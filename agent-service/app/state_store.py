from __future__ import annotations

from collections.abc import Iterator
from contextlib import contextmanager
from dataclasses import asdict, dataclass
from datetime import datetime, timezone
import json
from threading import RLock
import time
from typing import Any, Protocol

from app.session_lock import LocalSessionLock, SessionLock
from app.state_models import PendingClarification, SelectedEntity, WarehouseAgentState


STATE_SCHEMA_VERSION = 1


class StateVersionConflict(RuntimeError):
    pass


@dataclass(frozen=True)
class StateRecord:
    state: WarehouseAgentState
    version: int


class StateStore(Protocol):
    def load(self, session_id: str) -> StateRecord | None:
        ...

    def save(self, session_id: str, state: WarehouseAgentState, expected_version: int) -> int:
        ...

    def delete(self, session_id: str) -> None:
        ...

    def purge_expired(self) -> int:
        ...


class InMemoryStateStore:
    def __init__(self, *, ttl_seconds: int = 3600) -> None:
        self._ttl_seconds = ttl_seconds
        self._records: dict[str, tuple[WarehouseAgentState, int, float]] = {}
        self._guard = RLock()

    def load(self, session_id: str) -> StateRecord | None:
        with self._guard:
            record = self._records.get(session_id)
            if record is None:
                return None
            state, version, expires_at = record
            if expires_at <= time.monotonic():
                self._records.pop(session_id, None)
                return None
            state.state_version = version
            return StateRecord(state=state, version=version)

    def save(self, session_id: str, state: WarehouseAgentState, expected_version: int) -> int:
        with self._guard:
            current = self._records.get(session_id)
            current_version = current[1] if current else 0
            if current_version != expected_version:
                raise StateVersionConflict("state version changed during update")
            new_version = current_version + 1
            state.state_version = new_version
            self._records[session_id] = (
                state,
                new_version,
                time.monotonic() + self._ttl_seconds,
            )
            return new_version

    def delete(self, session_id: str) -> None:
        with self._guard:
            self._records.pop(session_id, None)

    def purge_expired(self) -> int:
        now = time.monotonic()
        with self._guard:
            expired = [key for key, value in self._records.items() if value[2] <= now]
            for key in expired:
                self._records.pop(key, None)
            return len(expired)


class RedisStateStore:
    _SAVE_SCRIPT = """
local current = redis.call('hget', KEYS[1], 'version')
local currentVersion = tonumber(current or '0')
if currentVersion ~= tonumber(ARGV[1]) then
  return -1
end
local nextVersion = currentVersion + 1
redis.call('hset', KEYS[1], 'version', nextVersion, 'payload', ARGV[2], 'schemaVersion', ARGV[3])
redis.call('expire', KEYS[1], ARGV[4])
return nextVersion
"""

    def __init__(
        self,
        client: Any,
        *,
        ttl_seconds: int = 3600,
        namespace: str = "warehouse-agent",
    ) -> None:
        if ttl_seconds < 60:
            raise ValueError("state TTL must be at least 60 seconds")
        self._client = client
        self._ttl_seconds = ttl_seconds
        self._namespace = namespace

    def load(self, session_id: str) -> StateRecord | None:
        raw = self._client.hgetall(self._key(session_id))
        if not raw:
            return None
        decoded = {_decode(key): _decode(value) for key, value in raw.items()}
        if int(decoded.get("schemaVersion", "0")) != STATE_SCHEMA_VERSION:
            raise StateVersionConflict("stored state schema version is unsupported")
        version = int(decoded["version"])
        state = deserialize_state(decoded["payload"])
        state.state_version = version
        return StateRecord(state=state, version=version)

    def save(self, session_id: str, state: WarehouseAgentState, expected_version: int) -> int:
        payload = serialize_state(state)
        result = int(
            self._client.eval(
                self._SAVE_SCRIPT,
                1,
                self._key(session_id),
                expected_version,
                payload,
                STATE_SCHEMA_VERSION,
                self._ttl_seconds,
            )
        )
        if result < 0:
            raise StateVersionConflict("state version changed during Redis update")
        state.state_version = result
        return result

    def delete(self, session_id: str) -> None:
        self._client.delete(self._key(session_id))

    def purge_expired(self) -> int:
        return 0

    def _key(self, session_id: str) -> str:
        return f"{self._namespace}:state:{session_id}"


class StateCheckpointer:
    MAX_MESSAGES = 80
    MAX_TOOL_RESULTS = 40
    MAX_INTERRUPT_RESULTS = 100

    def __init__(self, store: StateStore, session_lock: SessionLock) -> None:
        self.store = store
        self.session_lock = session_lock

    def get(self, thread_id: str) -> WarehouseAgentState:
        record = self.store.load(thread_id)
        if record is not None:
            return record.state
        state = WarehouseAgentState()
        self.store.save(thread_id, state, 0)
        return state

    def save(self, thread_id: str, state: WarehouseAgentState) -> None:
        self._prune(state)
        self.store.save(thread_id, state, state.state_version)

    @contextmanager
    def session(self, thread_id: str) -> Iterator[WarehouseAgentState]:
        with self.session_lock.hold(thread_id):
            state = self.get(thread_id)
            yield state
            self.save(thread_id, state)

    def clear(self, thread_id: str) -> None:
        with self.session_lock.hold(thread_id):
            self.store.delete(thread_id)

    def _prune(self, state: WarehouseAgentState) -> None:
        if len(state.messages) > self.MAX_MESSAGES:
            state.messages[:] = state.messages[-self.MAX_MESSAGES :]
        if len(state.tool_results) > self.MAX_TOOL_RESULTS:
            state.tool_results[:] = state.tool_results[-self.MAX_TOOL_RESULTS :]
        self._prune_mapping(state.interrupt_status, self.MAX_INTERRUPT_RESULTS)
        self._prune_mapping(state.interrupt_client_results, self.MAX_INTERRUPT_RESULTS)

    def _prune_mapping(self, values: dict[str, Any], limit: int) -> None:
        overflow = len(values) - limit
        for key in list(values)[: max(0, overflow)]:
            values.pop(key, None)


class InMemoryCheckpointer(StateCheckpointer):
    def __init__(self, *, ttl_seconds: int = 3600, wait_timeout_seconds: float = 5.0) -> None:
        super().__init__(
            InMemoryStateStore(ttl_seconds=ttl_seconds),
            LocalSessionLock(wait_timeout_seconds=wait_timeout_seconds),
        )


def serialize_state(state: WarehouseAgentState) -> str:
    return json.dumps(
        {"schemaVersion": STATE_SCHEMA_VERSION, "state": _json_value(asdict(state))},
        ensure_ascii=False,
        separators=(",", ":"),
        sort_keys=True,
    )


def deserialize_state(payload: str) -> WarehouseAgentState:
    envelope = json.loads(payload)
    if envelope.get("schemaVersion") != STATE_SCHEMA_VERSION:
        raise StateVersionConflict("state payload schema version is unsupported")
    values = dict(envelope.get("state") or {})
    selected_product = _selected_entity(values.pop("selected_product", None))
    selected_warehouse = _selected_entity(values.pop("selected_warehouse", None))
    selected_production_order = _selected_entity(values.pop("selected_production_order", None))
    pending = _pending(values.pop("pending_clarification", None))
    state = WarehouseAgentState()
    for key, value in values.items():
        if hasattr(state, key):
            setattr(state, key, value)
    state.selected_product = selected_product
    state.selected_warehouse = selected_warehouse
    state.selected_production_order = selected_production_order
    state.pending_clarification = pending
    return state


def _selected_entity(value: Any) -> SelectedEntity | None:
    if not isinstance(value, dict):
        return None
    return SelectedEntity(
        internal_id=value.get("internal_id"),
        display_label=str(value.get("display_label") or ""),
        source=str(value.get("source") or "restored"),
        metadata=dict(value.get("metadata") or {}),
        schema_version=str(value.get("schema_version") or "1.0"),
        entity_type=value.get("entity_type"),
        entity_ref=value.get("entity_ref"),
        canonical_name=value.get("canonical_name"),
        resolution_status=str(value.get("resolution_status") or "UNIQUE"),
        resolved_by=value.get("resolved_by"),
        resolved_at=_optional_datetime(value.get("resolved_at")),
        expires_at=_optional_datetime(value.get("expires_at")),
        scope_hash=value.get("scope_hash"),
        attributes=dict(value.get("attributes") or value.get("metadata") or {}),
    )


def _optional_datetime(value: Any) -> datetime | None:
    if value in (None, ""):
        return None
    return datetime.fromisoformat(str(value))


def _pending(value: Any) -> PendingClarification | None:
    if not isinstance(value, dict):
        return None
    return PendingClarification(
        kind=str(value["kind"]),
        intent=str(value["intent"]),
        prompt=str(value["prompt"]),
        options=list(value.get("options") or []),
        interrupt_id=str(value["interrupt_id"]),
        resume_token_hash=str(value["resume_token_hash"]),
        resume_token=str(value["resume_token"]),
        expires_at=datetime.fromisoformat(str(value["expires_at"])),
        expert_agent=str(value.get("expert_agent") or "main_agent"),
        allowed_tools=tuple(value.get("allowed_tools") or ()),
        business_domain=value.get("business_domain"),
        continuation=dict(value["continuation"]) if isinstance(value.get("continuation"), dict) else None,
        user_id=value.get("user_id"),
        status=str(value.get("status") or "PENDING"),
    )


def _json_value(value: Any) -> Any:
    if isinstance(value, datetime):
        return value.astimezone(timezone.utc).isoformat()
    if isinstance(value, dict):
        return {str(key): _json_value(item) for key, item in value.items()}
    if isinstance(value, (list, tuple)):
        return [_json_value(item) for item in value]
    return value


def _decode(value: Any) -> str:
    return value.decode("utf-8") if isinstance(value, bytes) else str(value)
