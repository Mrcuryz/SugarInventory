from __future__ import annotations

from datetime import datetime, timedelta, timezone
import threading
import time
from typing import Any

import pytest
from fastapi.testclient import TestClient

from app.config import Settings
from app.main import create_app
from app.session_lock import RedisSessionLock
from app.state_models import PendingClarification, WarehouseAgentState
from app.state_store import (
    RedisStateStore,
    StateCheckpointer,
    StateVersionConflict,
    deserialize_state,
    serialize_state,
)
from app.tools.client import MockToolClient


class FakeRedis:
    def __init__(self) -> None:
        self._strings: dict[str, str] = {}
        self._hashes: dict[str, dict[str, str]] = {}
        self._expires: dict[str, float] = {}
        self._guard = threading.RLock()

    def ping(self) -> bool:
        return True

    def set(self, key: str, value: str, *, nx: bool = False, px: int | None = None) -> bool:
        with self._guard:
            self._purge(key)
            if nx and key in self._strings:
                return False
            self._strings[key] = value
            if px is not None:
                self._expires[key] = time.monotonic() + px / 1000
            return True

    def hgetall(self, key: str) -> dict[str, str]:
        with self._guard:
            self._purge(key)
            return dict(self._hashes.get(key, {}))

    def delete(self, key: str) -> int:
        with self._guard:
            existed = key in self._strings or key in self._hashes
            self._strings.pop(key, None)
            self._hashes.pop(key, None)
            self._expires.pop(key, None)
            return int(existed)

    def eval(self, script: str, key_count: int, key: str, *args: Any) -> int:
        assert key_count == 1
        with self._guard:
            self._purge(key)
            if "hset" in script:
                expected, payload, schema_version, ttl = args
                current = int(self._hashes.get(key, {}).get("version", "0"))
                if current != int(expected):
                    return -1
                next_version = current + 1
                self._hashes[key] = {
                    "version": str(next_version),
                    "payload": str(payload),
                    "schemaVersion": str(schema_version),
                }
                self._expires[key] = time.monotonic() + int(ttl)
                return next_version
            token = str(args[0])
            if self._strings.get(key) != token:
                return 0
            if "pexpire" in script:
                self._expires[key] = time.monotonic() + int(args[1]) / 1000
                return 1
            return self.delete(key)

    def expire_now(self, key: str) -> None:
        with self._guard:
            self._expires[key] = time.monotonic() - 1

    def _purge(self, key: str) -> None:
        expires_at = self._expires.get(key)
        if expires_at is not None and expires_at <= time.monotonic():
            self._strings.pop(key, None)
            self._hashes.pop(key, None)
            self._expires.pop(key, None)


def _checkpointer(redis: FakeRedis) -> StateCheckpointer:
    return StateCheckpointer(
        RedisStateStore(redis, ttl_seconds=60),
        RedisSessionLock(redis, wait_timeout_seconds=1, lease_ms=1000),
    )


def _chat_payload(message: str) -> dict[str, Any]:
    return {
        "agentSessionId": "agt_redis_restore",
        "message": {"type": "user_message", "content": message},
        "client": {"traceId": "trace_restore", "requestId": "req_restore"},
    }


def test_state_json_round_trip_preserves_hitl_and_immutable_plan_context() -> None:
    state = WarehouseAgentState(
        orchestration_plan={"recipeId": "warehouse_inventory_latest_assay", "planVersion": 1},
        immutable_execution_context={"agent": "inventory_expert", "allowedTools": ["resolve_warehouses"]},
        pending_clarification=PendingClarification(
            kind="warehouse",
            intent="compound_inventory_assay",
            prompt="请选择库位",
            options=[],
            interrupt_id="intr_restore",
            resume_token_hash="hash",
            resume_token="token",
            expires_at=datetime.now(timezone.utc) + timedelta(minutes=5),
            expert_agent="inventory_expert",
            allowed_tools=("resolve_warehouses", "get_inventory_distribution"),
            continuation={"recipeId": "warehouse_inventory_latest_assay", "planVersion": 1},
        ),
    )

    restored = deserialize_state(serialize_state(state))

    assert restored.orchestration_plan == state.orchestration_plan
    assert restored.immutable_execution_context == state.immutable_execution_context
    assert restored.pending_clarification is not None
    assert restored.pending_clarification.allowed_tools == state.pending_clarification.allowed_tools
    assert restored.pending_clarification.continuation == state.pending_clarification.continuation


def test_redis_state_store_uses_versioned_atomic_updates_and_ttl() -> None:
    redis = FakeRedis()
    store = RedisStateStore(redis, ttl_seconds=60)
    first = WarehouseAgentState(messages=[{"role": "user", "content": "first"}])
    assert store.save("agt_version", first, 0) == 1
    stale = store.load("agt_version")
    current = store.load("agt_version")
    assert stale is not None and current is not None
    current.state.messages.append({"role": "assistant", "content": "second"})
    assert store.save("agt_version", current.state, current.version) == 2

    with pytest.raises(StateVersionConflict):
        store.save("agt_version", stale.state, stale.version)

    redis.expire_now("warehouse-agent:state:agt_version")
    assert store.load("agt_version") is None


def test_two_runtime_instances_resume_same_persisted_hitl_plan_without_permission_drift() -> None:
    redis = FakeRedis()
    first_tools = MockToolClient(
        {
            "resolve_warehouses": {
                "resolutionStatus": "AMBIGUOUS",
                "options": [{"warehouseId": 1, "displayLabel": "1号库位"}],
            }
        }
    )
    first_app = create_app(
        Settings(tool_mode="mock"),
        tool_client=first_tools,
        checkpointer=_checkpointer(redis),
    )
    first = TestClient(first_app).post(
        "/internal/agent/chat",
        json=_chat_payload("当前1号库库存情况如何？它们的化验情况如何？"),
    )
    assert first.json()["needsUserSelection"] is True

    second_tools = MockToolClient(
        {
            "get_inventory_distribution": {
                "scopeLabel": "1号库位的全部产品",
                "productLabel": "全部产品",
                "groupBy": "product",
                "totalStockText": "1板",
                "totalEquivalentPieces": 40,
                "warehouseCount": 1,
                "productCount": 1,
                "palletCount": 1,
                "groups": [{
                    "groupLabel": "黄冰糖（袋）",
                    "canonicalProductName": "黄冰糖（袋）",
                    "productLabel": "黄冰糖（袋）",
                    "stockText": "1板",
                    "totalEquivalentPieces": 40,
                    "palletCount": 1,
                    "warehouseCount": 1,
                    "productCount": 1,
                    "percentageText": "100.0%",
                    "riskLabels": [],
                }],
            },
            "resolve_products": {
                "resolutionStatus": "UNIQUE",
                "candidates": [{"productId": 84, "displayLabel": "黄冰糖（袋）"}],
            },
            "query_assay_records": {"total": 0, "records": []},
        }
    )
    second_checkpointer = _checkpointer(redis)
    pending = second_checkpointer.get("agt_redis_restore").pending_clarification
    assert pending is not None
    original_plan_id = pending.continuation["planId"] if pending.continuation else None
    second_app = create_app(
        Settings(tool_mode="mock"),
        tool_client=second_tools,
        checkpointer=second_checkpointer,
    )
    resumed = TestClient(second_app).post(
        "/internal/agent/resume",
        json={
            "agentSessionId": "agt_redis_restore",
            "resumeToken": pending.resume_token,
            "event": {
                "type": "candidate_selected",
                "interruptId": pending.interrupt_id,
                "action": "SELECT_OPTION",
                "selection": {"optionId": "opt_001"},
                "clientRequestId": "resume_other_instance",
            },
        },
    )

    assert resumed.status_code == 200
    assert [call["expertAgent"] for call in second_tools.calls] == [
        "inventory_expert",
        "assay_expert",
        "assay_expert",
    ]
    restored_state = second_checkpointer.get("agt_redis_restore")
    assert restored_state.last_orchestration is not None
    assert restored_state.last_orchestration["planId"] == original_plan_id
    assert restored_state.last_orchestration["recipeId"] == "warehouse_inventory_latest_assay"


def test_redis_session_lock_serializes_instances_and_releases_after_exception() -> None:
    redis = FakeRedis()
    first = RedisSessionLock(redis, wait_timeout_seconds=1, lease_ms=1000)
    second = RedisSessionLock(redis, wait_timeout_seconds=1, lease_ms=1000)
    active = 0
    overlaps: list[int] = []
    guard = threading.Lock()

    def worker(lock: RedisSessionLock) -> None:
        nonlocal active
        with lock.hold("agt_lock"):
            with guard:
                active += 1
                overlaps.append(active)
            time.sleep(0.05)
            with guard:
                active -= 1

    threads = [threading.Thread(target=worker, args=(lock,)) for lock in (first, second)]
    for thread in threads:
        thread.start()
    for thread in threads:
        thread.join(timeout=2)

    assert max(overlaps) == 1
    with pytest.raises(RuntimeError):
        with first.hold("agt_exception"):
            raise RuntimeError("boom")
    with second.hold("agt_exception"):
        pass


def test_redis_session_lock_recovers_after_stale_lease_expires() -> None:
    redis = FakeRedis()
    redis.set("warehouse-agent:lock:agt_crashed", "dead-owner", nx=True, px=30)
    time.sleep(0.05)

    with RedisSessionLock(redis, wait_timeout_seconds=1, lease_ms=1000).hold("agt_crashed"):
        pass
