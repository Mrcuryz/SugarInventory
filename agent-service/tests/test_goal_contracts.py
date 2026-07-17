from __future__ import annotations

from datetime import datetime, timezone

from fastapi.testclient import TestClient

from app.config import Settings
from app.evaluation import CoreTaskRunV1, summarize_core_task_comparison
from app.goal_contracts import (
    GOAL_CONTRACTS,
    GoalCompletionEvaluator,
    build_fact_envelope,
    registered_goal_for_plan,
)
from app.main import create_app
from app.state_models import EntityContextV1, WarehouseAgentState
from app.state_store import InMemoryCheckpointer, deserialize_state, serialize_state
from app.tools.client import MockToolClient


def test_only_three_core_goal_contracts_are_registered() -> None:
    assert set(GOAL_CONTRACTS) == {
        "CURRENT_PRODUCT_INVENTORY",
        "PRODUCT_INVENTORY_DISTRIBUTION",
        "WAREHOUSE_INVENTORY_DISTRIBUTION",
    }


def test_goal_mapping_distinguishes_product_and_warehouse_distribution() -> None:
    assert registered_goal_for_plan(
        tool_name="get_inventory_distribution",
        arguments={
            "productScope": {"type": "SINGLE_PRODUCT", "productId": 84},
            "warehouseScope": {"type": "ALL"},
            "groupBy": "warehouse",
        },
        intent="inventory_distribution",
    ) == "PRODUCT_INVENTORY_DISTRIBUTION"
    assert registered_goal_for_plan(
        tool_name="get_inventory_distribution",
        arguments={
            "productScope": {"type": "ALL"},
            "warehouseScope": {"type": "SINGLE_WAREHOUSE", "warehouseId": 8},
            "groupBy": "product",
        },
        intent="inventory_distribution",
    ) == "WAREHOUSE_INVENTORY_DISTRIBUTION"


def test_no_data_is_a_complete_authoritative_inventory_answer() -> None:
    product = EntityContextV1(
        internal_id=84,
        display_label="黄冰糖（袋）",
        source="resolver",
        entity_type="PRODUCT",
        entity_ref="CURRENT_PRODUCT",
        canonical_name="黄冰糖（袋）",
        resolved_at=datetime.now(timezone.utc),
    )
    fact = build_fact_envelope(
        goal_type="CURRENT_PRODUCT_INVENTORY",
        tool_name="get_inventory_overview",
        arguments={"productId": 84},
        safe_data={"isEmpty": True, "locations": []},
        entity_contexts={"PRODUCT": product},
    )

    completion = GoalCompletionEvaluator().evaluate(
        goal_type="CURRENT_PRODUCT_INVENTORY",
        entity_contexts={"PRODUCT": product},
        facts=[fact],
    )

    assert fact.status == "NO_DATA"
    assert completion.status == "COMPLETE"
    assert completion.evidenceRefs == [fact.factRef]


def test_missing_required_entity_needs_clarification() -> None:
    completion = GoalCompletionEvaluator().evaluate(
        goal_type="WAREHOUSE_INVENTORY_DISTRIBUTION",
        entity_contexts={},
        facts=[],
    )

    assert completion.status == "NEEDS_CLARIFICATION"
    assert completion.missingEntityTypes == ["WAREHOUSE"]


def test_entity_context_and_goal_state_round_trip() -> None:
    state = WarehouseAgentState(
        selected_product=EntityContextV1(
            internal_id=84,
            display_label="黄冰糖（袋）",
            source="resolver",
            entity_type="PRODUCT",
            entity_ref="CURRENT_PRODUCT",
            canonical_name="黄冰糖（袋）",
            resolved_by="RESOLVER",
            resolved_at=datetime.now(timezone.utc),
        ),
        active_goal_type="CURRENT_PRODUCT_INVENTORY",
        fact_envelopes=[{"schemaVersion": "1.0"}],
        last_goal_completion={"status": "PARTIAL"},
    )

    restored = deserialize_state(serialize_state(state))

    assert restored.selected_product is not None
    assert restored.selected_product.schema_version == "1.0"
    assert restored.selected_product.entity_type == "PRODUCT"
    assert restored.selected_product.entity_ref == "CURRENT_PRODUCT"
    assert restored.selected_product.canonical_name == "黄冰糖（袋）"
    assert restored.active_goal_type == "CURRENT_PRODUCT_INVENTORY"
    assert restored.fact_envelopes == [{"schemaVersion": "1.0"}]


def test_runtime_attaches_product_inventory_goal_completion() -> None:
    tool_client = MockToolClient(
        {
            "resolve_products": {
                "resolutionStatus": "UNIQUE",
                "candidates": [
                    {
                        "productId": 84,
                        "productName": "黄冰糖（袋）",
                        "displayLabel": "黄冰糖（袋）",
                    }
                ],
            },
            "get_inventory_overview": {
                "displayStockInfo": "11板30件",
                "totalEquivalentPieces": 470,
                "totalWeight": 11750,
            },
        }
    )
    checkpointer = InMemoryCheckpointer()
    client = TestClient(
        create_app(Settings(tool_mode="mock"), tool_client=tool_client, checkpointer=checkpointer)
    )

    response = client.post("/internal/agent/chat", json=_chat_payload("查黄冰糖（袋）库存"))
    state = checkpointer.get("agt_goal_contract")

    assert response.status_code == 200
    assert state.active_goal_type == "CURRENT_PRODUCT_INVENTORY"
    assert state.selected_product is not None
    assert state.selected_product.entity_type == "PRODUCT"
    assert state.selected_product.canonical_name == "黄冰糖（袋）"
    assert state.last_goal_completion is not None
    assert state.last_goal_completion["status"] == "COMPLETE"
    assert len(state.fact_envelopes) == 1
    assert state.fact_envelopes[0]["factType"] == "CURRENT_PRODUCT_INVENTORY"


def test_runtime_attaches_warehouse_inventory_goal_completion() -> None:
    tool_client = MockToolClient(
        {
            "resolve_warehouses": {
                "resolutionStatus": "UNIQUE",
                "candidates": [{"warehouseId": 8, "displayLabel": "8号库位"}],
            },
            "get_inventory_distribution": {
                "scopeLabel": "8号库位的全部产品",
                "productLabel": "全部产品",
                "groupBy": "product",
                "totalStockText": "3板",
                "totalEquivalentPieces": 120,
                "warehouseCount": 1,
                "productCount": 1,
                "palletCount": 3,
                "groups": [
                    {
                        "groupLabel": "黄冰糖（袋）",
                        "canonicalProductName": "黄冰糖（袋）",
                        "productLabel": "黄冰糖（袋）",
                        "stockText": "3板",
                        "totalEquivalentPieces": 120,
                        "palletCount": 3,
                        "warehouseCount": 1,
                        "productCount": 1,
                        "percentageText": "100.0%",
                    }
                ],
            },
        }
    )
    checkpointer = InMemoryCheckpointer()
    client = TestClient(
        create_app(Settings(tool_mode="mock"), tool_client=tool_client, checkpointer=checkpointer)
    )

    response = client.post("/internal/agent/chat", json=_chat_payload("帮我查8号库位的库存情况"))
    state = checkpointer.get("agt_goal_contract")

    assert response.status_code == 200
    assert state.active_goal_type == "WAREHOUSE_INVENTORY_DISTRIBUTION"
    assert state.last_goal_completion is not None
    assert state.last_goal_completion["status"] == "COMPLETE"
    assert state.fact_envelopes[0]["factType"] == "WAREHOUSE_INVENTORY_DISTRIBUTION"


def test_router_llm_comparison_summary_uses_paired_fact_fingerprints() -> None:
    runs = [
        CoreTaskRunV1(
            participantKey="warehouse-01",
            participantRole="WAREHOUSE_OPERATOR",
            taskId="T01",
            routePath="DETERMINISTIC",
            completionStatus="COMPLETE",
            factFingerprint="fact-a",
            userTurns=2,
            latencyMs=100,
            routedGoalType="CURRENT_PRODUCT_INVENTORY",
            expectedGoalType="CURRENT_PRODUCT_INVENTORY",
        ),
        CoreTaskRunV1(
            participantKey="warehouse-01",
            participantRole="WAREHOUSE_OPERATOR",
            taskId="T01",
            routePath="LLM",
            completionStatus="COMPLETE",
            factFingerprint="fact-a",
            userTurns=1,
            latencyMs=300,
            routedGoalType="CURRENT_PRODUCT_INVENTORY",
            expectedGoalType="CURRENT_PRODUCT_INVENTORY",
        ),
    ]

    summary = summarize_core_task_comparison(runs)

    assert summary["pairedRunCount"] == 1
    assert summary["factConsistencyRate"] == 1.0
    assert summary["pathMetrics"]["DETERMINISTIC"]["completionRate"] == 1.0
    assert summary["pathMetrics"]["LLM"]["medianLatencyMs"] == 300.0


def _chat_payload(content: str) -> dict[str, object]:
    return {
        "agentSessionId": "agt_goal_contract",
        "messageId": "msg_goal_contract",
        "message": {"type": "user_message", "content": content},
        "client": {"traceId": "trace_goal_contract", "requestId": "req_goal_contract", "debug": True},
    }
