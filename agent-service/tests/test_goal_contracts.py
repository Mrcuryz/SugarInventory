from __future__ import annotations

from datetime import datetime, timezone
from pathlib import Path

from fastapi.testclient import TestClient
import pytest
import yaml

from app.config import Settings
from app.evaluation import CoreTaskRunV1, summarize_core_task_comparison
from app.goal_contracts import (
    GOAL_CONTRACTS,
    REGISTERED_CORE_FACT_TYPES,
    REGISTERED_CORE_GOAL_TYPES,
    GoalCompletionEvaluator,
    build_fact_envelope,
    registered_goal_for_plan,
)
from app.schemas import (
    GOAL_DRAFT_GOAL_TYPES,
    RESULT_REASONING_FACT_TYPES,
    GoalDraftV1,
    MainAgentDecisionV1,
    ResultReasoningDraftV1,
)
from app.main import create_app
from app.state_models import EntityContextV1, WarehouseAgentState
from app.state_store import InMemoryCheckpointer, deserialize_state, serialize_state
from app.tools.client import MockToolClient
from app.tools.client import ALLOWED_TOOLS


def test_priority_readonly_goal_contracts_are_registered() -> None:
    assert len(GOAL_CONTRACTS) == 43
    assert {
        "CURRENT_INVENTORY_LEDGER",
        "PREPARE_POOL_BALANCE",
        "WAREHOUSE_STATUS",
        "WAREHOUSE_CAPACITY_DISTRIBUTION",
        "WAREHOUSE_RECENT_OPERATIONS",
        "WAREHOUSE_MIXED_STORAGE_FACTS",
        "ASSAY_ABNORMALITY_SUMMARY",
        "CURRENT_INVENTORY_STANDARD_GAPS",
        "PALLET_ANOMALY_SUMMARY",
        "PRINTED_NOT_INBOUND_QR_CODES",
        "QR_BATCH_INBOUND_COMPLETION",
        "PRODUCTION_LABEL_COMPLETION",
        "IN_PROCESS_MATERIALS",
        "STOCK_DOCUMENTS",
        "AUTO_INBOUND_BATCH_STATUS",
    }.issubset(GOAL_CONTRACTS)
    assert all(
        contract.evidenceTools
        and set(contract.evidenceTools).issubset(contract.allowedTools)
        for contract in GOAL_CONTRACTS.values()
    )


def test_readonly_goal_registry_covers_every_allowed_tool_and_matches_current_contracts() -> None:
    registry_path = (
        Path(__file__).resolve().parents[2]
        / "docs"
        / "agent"
        / "readonly-goal-contract-registry.yaml"
    )
    registry = yaml.safe_load(registry_path.read_text(encoding="utf-8"))
    current = registry["current_goals"]
    planned = registry["planned_goals"]

    assert set(current) == set(GOAL_CONTRACTS)
    for goal_type, contract in GOAL_CONTRACTS.items():
        item = current[goal_type]
        assert item["owner_expert"] == contract.ownerExpert
        assert set(item["primary_tools"]) == set(contract.evidenceTools)
        assert set(item["primary_tools"] + item["supporting_tools"]) == set(contract.allowedTools)

    classified_tools = {
        tool
        for goals in (current, planned)
        for item in goals.values()
        for key in ("primary_tools", "supporting_tools")
        for tool in item[key]
    }
    assert classified_tools == set(ALLOWED_TOOLS)


def test_goal_contract_types_are_the_single_source_for_model_semantic_schemas() -> None:
    assert set(REGISTERED_CORE_GOAL_TYPES) == set(GOAL_CONTRACTS)
    assert set(REGISTERED_CORE_FACT_TYPES) == {
        fact_type
        for contract in GOAL_CONTRACTS.values()
        for fact_type in contract.requiredFactTypes
    }
    assert set(REGISTERED_CORE_GOAL_TYPES).issubset(GOAL_DRAFT_GOAL_TYPES)
    assert set(REGISTERED_CORE_FACT_TYPES).issubset(RESULT_REASONING_FACT_TYPES)


def test_goal_draft_and_result_reasoning_accept_every_registered_contract() -> None:
    for goal_type in REGISTERED_CORE_GOAL_TYPES:
        draft = GoalDraftV1(
            goalType=goal_type,
            requestedOutcome="验证登记目标可以进入模型语义合同",
            dataNeed="FRESH_READ",
            needsClarification=False,
            confidence=1,
        )
        assert draft.goalType == goal_type

    for fact_type in REGISTERED_CORE_FACT_TYPES:
        reasoning = ResultReasoningDraftV1(
            proposedStatus="PARTIAL",
            answeredUserGoal=False,
            missingFacts=[fact_type],
        )
        assert reasoning.missingFacts == [fact_type]


def test_priority_goal_fact_shapes_are_declarative_and_accept_safe_results() -> None:
    cases = {
        "CURRENT_INVENTORY_LEDGER": (
            "query_inventory_ledger",
            {"dataScope": "CURRENT", "total": 1, "records": [{"productName": "黄冰糖"}]},
        ),
        "PREPARE_POOL_BALANCE": (
            "query_prepare_pool_balance",
            {"dataScope": "CURRENT", "total": 1, "records": [{"productName": "白冰糖"}]},
        ),
        "WAREHOUSE_STATUS": (
            "get_warehouse_status",
            {"warehouseName": "2号库位", "remainingCapacity": 10},
        ),
        "WAREHOUSE_CAPACITY_DISTRIBUTION": (
            "query_warehouse_capacity_distribution",
            {"dataScope": "CURRENT", "total": 1, "summary": {}, "records": [{}]},
        ),
        "WAREHOUSE_RECENT_OPERATIONS": (
            "query_warehouse_recent_operations",
            {"dataScope": "RECORDED", "count": 1, "records": [{}]},
        ),
        "WAREHOUSE_MIXED_STORAGE_FACTS": (
            "query_warehouse_mixed_storage_facts",
            {"dataScope": "CURRENT", "count": 1, "records": [{}]},
        ),
        "ASSAY_ABNORMALITY_SUMMARY": (
            "query_assay_abnormalities",
            {
                "scopeLabel": "全部产品",
                "dateRangeLabel": "最近30天",
                "groupBy": "product",
                "total": 1,
                "groups": [{}],
            },
        ),
        "CURRENT_INVENTORY_STANDARD_GAPS": (
            "query_assay_standard_coverage",
            {
                "scopeLabel": "全部产品",
                "coverageType": "PRODUCT_WITHOUT_STANDARD",
                "totalGroups": 1,
                "groups": [{}],
            },
        ),
        "PALLET_ANOMALY_SUMMARY": (
            "query_pallet_anomalies",
            {"scopeLabel": "全部托盘", "dateRangeLabel": "最近30天", "total": 1, "groups": [{}]},
        ),
        "PRINTED_NOT_INBOUND_QR_CODES": (
            "query_printed_not_inbound_codes",
            {
                "scopeLabel": "全部产品",
                "dateRangeLabel": "最近30天",
                "printedCount": 2,
                "inboundCount": 1,
                "notInboundCount": 1,
                "groups": [{}],
            },
        ),
        "QR_BATCH_INBOUND_COMPLETION": (
            "query_qr_batch_inbound_completion",
            {
                "batchLabel": "LB-001",
                "printedCount": 2,
                "inboundCount": 1,
                "notInboundCount": 1,
                "unfinishedExamples": ["P001"],
            },
        ),
        "PRODUCTION_LABEL_COMPLETION": (
            "query_production_label_completion",
            {
                "orderNo": "PO-001",
                "labelBatchCount": 1,
                "requiredQrCount": 10,
                "boundQrCount": 8,
                "inboundQrCount": 6,
                "batches": [{}],
            },
        ),
        "IN_PROCESS_MATERIALS": (
            "query_in_process_materials",
            {"dataScope": "CURRENT", "total": 1, "records": [{}]},
        ),
        "STOCK_DOCUMENTS": (
            "query_stock_documents",
            {"dataScope": "RECORDED", "documentType": "OUTBOUND", "total": 1, "records": [{}]},
        ),
        "AUTO_INBOUND_BATCH_STATUS": (
            "get_auto_inbound_batch_detail",
            {"dataScope": "CURRENT", "taskCount": 1, "tasks": [{}]},
        ),
    }

    for goal_type, (tool_name, safe_data) in cases.items():
        fact = build_fact_envelope(
            goal_type=goal_type,
            tool_name=tool_name,
            arguments={},
            safe_data=safe_data,
            entity_contexts={},
        )
        assert fact.status == "AVAILABLE", goal_type


def test_supporting_tool_cannot_masquerade_as_goal_evidence() -> None:
    with pytest.raises(ValueError, match="cannot provide evidence"):
        build_fact_envelope(
            goal_type="CURRENT_INVENTORY_LEDGER",
            tool_name="resolve_products",
            arguments={"query": "黄冰糖"},
            safe_data={"resolutionStatus": "UNIQUE"},
            entity_contexts={},
        )


def test_remaining_readonly_goal_fact_shapes_accept_safe_results() -> None:
    cases = {
        "ASSAY_REFERENCE_DATA": (
            "get_quality_standard_detail",
            {
                "dataScope": "CURRENT",
                "standardCode": "STD-001",
                "standardName": "黄冰糖 v1",
                "metrics": [{}],
            },
        ),
        "PRODUCT_QUALITY_CONFIGURATION": (
            "query_product_quality_configuration",
            {
                "dataScope": "CURRENT_PRODUCT_QUALITY_CONFIGURATION",
                "productName": "黄冰糖（袋）",
                "standards": [{}],
                "assayGroups": [{}],
            },
        ),
        "FIXED_PRODUCT_QR_POOL": (
            "query_fixed_product_qr_pool",
            {"dataScope": "CURRENT", "total": 1, "records": [{}]},
        ),
        "MATERIAL_CANDIDATES": (
            "query_material_candidates",
            {"dataScope": "CURRENT", "total": 1, "records": [{}]},
        ),
        "PRODUCT_MASTER_DATA": (
            "get_product_detail",
            {"dataScope": "CURRENT", "productName": "黄冰糖（袋）"},
        ),
        "SCREEN_MESH_CATALOG": (
            "query_screen_mesh_catalog",
            {"dataScope": "CURRENT", "total": 1, "records": [{}]},
        ),
        "EMPLOYEE_ROSTER": (
            "query_employee_roster",
            {"dataScope": "AUTHORIZED", "total": 1, "records": [{}]},
        ),
        "ROLE_PERMISSION_CATALOG": (
            "get_role_permission_summary",
            {
                "dataScope": "CURRENT",
                "roleName": "管理员",
                "permissionCount": 1,
                "permissions": [{}],
            },
        ),
        "BUSINESS_OPERATION_LOGS": (
            "search_operation_logs",
            {"dataScope": "AUDIT", "total": 1, "records": [{}]},
        ),
        "AGENT_TOOL_AUDIT": (
            "query_agent_tool_audit",
            {"dataScope": "AUDIT", "total": 1, "records": [{}]},
        ),
        "AGENT_ANSWER_REVIEWS": (
            "query_agent_answer_reviews",
            {"dataScope": "AUDIT", "total": 1, "records": [{}]},
        ),
    }

    for goal_type, (tool_name, safe_data) in cases.items():
        fact = build_fact_envelope(
            goal_type=goal_type,
            tool_name=tool_name,
            arguments={},
            safe_data=safe_data,
            entity_contexts={},
        )
        assert fact.status == "AVAILABLE", goal_type


def test_main_agent_can_bind_registered_goal_to_expert_without_selecting_tool() -> None:
    decision = MainAgentDecisionV1(
        action="DELEGATE",
        expertAgent="production_expert",
        goalType="PRODUCTION_ORDER_PROGRESS",
        semanticReason="READ_QUERY",
        confidence=1,
    )

    assert GOAL_CONTRACTS[decision.goalType].ownerExpert == decision.expertAgent


def test_quality_goal_all_scope_completes_without_forcing_product_entity() -> None:
    fact = build_fact_envelope(
        goal_type="CURRENT_UNQUALIFIED_INVENTORY",
        tool_name="query_unqualified_inventory",
        arguments={
            "productScope": {"type": "ALL"},
            "warehouseScope": {"type": "ALL"},
        },
        safe_data={
            "queryLabel": "当前不合格库存",
            "totalGroups": 0,
            "records": [],
        },
        entity_contexts={},
    )

    completion = GoalCompletionEvaluator().evaluate(
        goal_type="CURRENT_UNQUALIFIED_INVENTORY",
        entity_contexts={},
        facts=[fact],
    )

    assert fact.status == "NO_DATA"
    assert completion.status == "COMPLETE"


def test_quality_goal_rejects_single_product_scope_without_matching_entity() -> None:
    fact = build_fact_envelope(
        goal_type="CURRENT_INVENTORY_MATCHING_METRIC",
        tool_name="query_inventory_by_assay_metrics",
        arguments={
            "productScope": {"type": "SINGLE_PRODUCT", "productId": 84},
            "warehouseScope": {"type": "ALL"},
            "metricCondition": {"metricCode": "sucrose", "operator": "GTE", "value": 99.7},
        },
        safe_data={
            "queryLabel": "蔗糖分不低于99.7的当前库存",
            "totalGroups": 0,
            "records": [],
        },
        entity_contexts={},
    )

    completion = GoalCompletionEvaluator().evaluate(
        goal_type="CURRENT_INVENTORY_MATCHING_METRIC",
        entity_contexts={},
        facts=[fact],
    )

    assert fact.status == "INVALID"
    assert completion.status == "FAILED"
    assert "单产品查询范围缺少已确认产品实体。" in fact.limitations
    assert fact.scope["metricCondition.metricCode"] == "sucrose"
    assert fact.scope["metricCondition.value"] == "99.7"


def test_assay_history_and_boiling_list_no_data_are_authoritative_completion() -> None:
    assay_fact = build_fact_envelope(
        goal_type="PRODUCT_ASSAY_HISTORY",
        tool_name="query_assay_records",
        arguments={
            "productScope": {"type": "ALL"},
            "dateRange": {"type": "LAST_DAYS", "days": 30},
        },
        safe_data={"scopeLabel": "全部产品", "total": 0, "records": []},
        entity_contexts={},
    )
    batch_fact = build_fact_envelope(
        goal_type="BOILING_BATCH_LIST",
        tool_name="query_boiling_batches",
        arguments={"startDate": "2026-06-22", "endDate": "2026-07-21"},
        safe_data={"total": 0, "candidates": []},
        entity_contexts={},
    )

    assert assay_fact.status == "NO_DATA"
    assert batch_fact.status == "NO_DATA"
    assert GoalCompletionEvaluator().evaluate(
        goal_type="PRODUCT_ASSAY_HISTORY",
        entity_contexts={},
        facts=[assay_fact],
    ).status == "COMPLETE"
    assert GoalCompletionEvaluator().evaluate(
        goal_type="BOILING_BATCH_LIST",
        entity_contexts={},
        facts=[batch_fact],
    ).status == "COMPLETE"


def test_production_goal_mapping_distinguishes_batch_order_and_material_trace() -> None:
    assert registered_goal_for_plan(
        tool_name="resolve_production_entities",
        arguments={"entityType": "BOILING_BATCH", "query": "BT-001"},
        intent="boiling_batch_trace",
    ) == "BOILING_BATCH_TRACE"
    assert registered_goal_for_plan(
        tool_name="query_production_order_progress",
        arguments={"orderRef": "aer_order"},
        intent="production_order_progress",
    ) == "PRODUCTION_ORDER_PROGRESS"
    assert registered_goal_for_plan(
        tool_name="query_material_pick_trace",
        arguments={"orderRef": "aer_order"},
        intent="material_pick_trace",
    ) == "PRODUCTION_MATERIAL_TRACE"


def test_pallet_goal_mapping_distinguishes_current_status_and_history() -> None:
    assert registered_goal_for_plan(
        tool_name="get_pallet_status",
        arguments={"code": "BT000YGI"},
        intent="pallet_status",
    ) == "PALLET_CURRENT_STATUS"
    assert registered_goal_for_plan(
        tool_name="query_qr_code_lifecycle",
        arguments={"code": "BT000YGI"},
        intent="qr_code_lifecycle",
    ) == "PALLET_CURRENT_STATUS"
    assert registered_goal_for_plan(
        tool_name="query_pallet_flow_records",
        arguments={"code": "BT000YGI"},
        intent="pallet_flow_records",
    ) == "PALLET_FLOW_HISTORY"


def test_pallet_history_no_data_completes_with_resolved_pallet() -> None:
    pallet = EntityContextV1(
        internal_id=None,
        display_label="BT000YGI",
        source="user",
        entity_type="PALLET",
        entity_ref="CURRENT_PALLET",
        canonical_name="BT000YGI",
        resolved_at=datetime.now(timezone.utc),
    )
    fact = build_fact_envelope(
        goal_type="PALLET_FLOW_HISTORY",
        tool_name="query_pallet_flow_records",
        arguments={"code": "BT000YGI", "page": 1, "size": 20},
        safe_data={
            "scopeLabel": "托盘 BT000YGI",
            "total": 0,
            "summaryText": "没有已登记流转记录。",
            "records": [],
        },
        entity_contexts={"PALLET": pallet},
    )
    completion = GoalCompletionEvaluator().evaluate(
        goal_type="PALLET_FLOW_HISTORY",
        entity_contexts={"PALLET": pallet},
        facts=[fact],
    )

    assert fact.status == "NO_DATA"
    assert completion.status == "COMPLETE"


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


def test_pending_task_goal_mapping_and_no_data_completion() -> None:
    assert registered_goal_for_plan(
        tool_name="query_pallet_tasks",
        arguments={"status": "PENDING", "page": 1, "size": 20},
        intent="pallet_tasks",
    ) == "CURRENT_PENDING_TASKS"
    fact = build_fact_envelope(
        goal_type="CURRENT_PENDING_TASKS",
        tool_name="query_pallet_tasks",
        arguments={"status": "PENDING", "page": 1, "size": 20},
        safe_data={
            "scopeLabel": "当前待处理任务",
            "total": 0,
            "page": 1,
            "size": 20,
            "records": [],
        },
        entity_contexts={},
    )

    completion = GoalCompletionEvaluator().evaluate(
        goal_type="CURRENT_PENDING_TASKS",
        entity_contexts={},
        facts=[fact],
    )

    assert fact.status == "NO_DATA"
    assert completion.status == "COMPLETE"


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


def test_product_quality_configuration_requires_confirmed_product_and_completes_with_fact() -> None:
    evaluator = GoalCompletionEvaluator()

    missing_product = evaluator.evaluate(
        goal_type="PRODUCT_QUALITY_CONFIGURATION",
        entity_contexts={},
        facts=[],
    )

    assert missing_product.status == "NEEDS_CLARIFICATION"
    assert missing_product.missingEntityTypes == ["PRODUCT"]

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
        goal_type="PRODUCT_QUALITY_CONFIGURATION",
        tool_name="query_product_quality_configuration",
        arguments={"productId": 84},
        safe_data={
            "dataScope": "CURRENT_PRODUCT_QUALITY_CONFIGURATION",
            "productName": "黄冰糖（袋）",
            "standards": [{"standardName": "黄冰糖 v1"}],
            "assayGroups": [{"groupName": "成品糖批量化验组"}],
        },
        entity_contexts={"PRODUCT": product},
    )

    completion = evaluator.evaluate(
        goal_type="PRODUCT_QUALITY_CONFIGURATION",
        entity_contexts={"PRODUCT": product},
        facts=[fact],
    )

    assert fact.status == "AVAILABLE"
    assert completion.status == "COMPLETE"
    assert completion.evidenceRefs == [fact.factRef]


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
