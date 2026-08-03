from __future__ import annotations

import json
from datetime import datetime, timezone
from typing import Any

import pytest
from fastapi.testclient import TestClient

from app.agents import AgentHandoffRouter
from app.business_time import BusinessClock
from app.config import Settings
from app.graph.state import InMemoryCheckpointer, SelectedEntity
from app.model import BasicModelClient, ExpertLoopRequest, MainAgentRouteRequest, ModelDecisionError
from app.runtime import WarehouseAgentRuntime
from app.schemas import (
    ChatRequest,
    ExpertLoopDecisionV1,
    MainAgentDecisionV1,
    ResumeRequest,
)
from app.tool_arguments import ToolArgumentBuilder
from app.tools.client import MockToolClient, ToolGatewayError


class ScriptedLlmModel(BasicModelClient):
    def __init__(
        self,
        main_decisions: list[MainAgentDecisionV1],
        expert_decisions: list[ExpertLoopDecisionV1],
    ) -> None:
        self.main_decisions = list(main_decisions)
        self.expert_decisions = list(expert_decisions)
        self.main_requests: list[MainAgentRouteRequest] = []
        self.expert_requests: list[ExpertLoopRequest] = []
        self.stream_call_count = 0

    def route_main_agent(self, request: MainAgentRouteRequest) -> MainAgentDecisionV1 | None:
        self.main_requests.append(request)
        return self.main_decisions.pop(0) if self.main_decisions else None

    def decide_expert_action(self, request: ExpertLoopRequest) -> ExpertLoopDecisionV1 | None:
        self.expert_requests.append(request)
        return self.expert_decisions.pop(0) if self.expert_decisions else None

    def stream_answer_deltas(self, answer: str):
        self.stream_call_count += 1
        yield answer


def main_delegate(expert: str, goal_type: str | None = None) -> MainAgentDecisionV1:
    return MainAgentDecisionV1(
        action="DELEGATE",
        expertAgent=expert,
        goalType=goal_type,
        semanticReason="FOLLOWUP_QUERY",
        confidence=0.95,
    )


def main_recipe() -> MainAgentDecisionV1:
    return MainAgentDecisionV1(
        action="RUN_REGISTERED_RECIPE",
        recipeId="warehouse_inventory_latest_assay",
        semanticReason="CROSS_DOMAIN_RECIPE",
        confidence=0.95,
    )


def call(tool: str, arguments: dict[str, Any]) -> ExpertLoopDecisionV1:
    return ExpertLoopDecisionV1(
        action="CALL_TOOL",
        toolName=tool,
        arguments=arguments,
        statusReason="NEED_FRESH_DATA",
    )


def final(answer: str, *observation_ids: str) -> ExpertLoopDecisionV1:
    return ExpertLoopDecisionV1(
        action="FINAL_ANSWER",
        answer=answer,
        citedObservationIds=list(observation_ids),
        statusReason="ENOUGH_DATA",
    )


def chat_request(message: str, session_id: str = "agt_llm") -> ChatRequest:
    return ChatRequest.model_validate(
        {
            "agentSessionId": session_id,
            "messageId": "msg_001",
            "user": {"userId": 7, "name": "测试管理员", "roleCode": "ADMIN"},
            "message": {"type": "user_message", "content": message},
            "client": {"traceId": "trace_001", "requestId": "req_001", "debug": True},
        }
    )


def fixed_test_clock() -> BusinessClock:
    return BusinessClock(lambda: datetime(2026, 7, 21, 4, 0, tzinfo=timezone.utc))


def runtime_for(
    model: ScriptedLlmModel,
    tool_client: MockToolClient,
    checkpointer: InMemoryCheckpointer | None = None,
    allowed_experts: tuple[str, ...] = (
        "inventory_expert",
        "warehouse_expert",
        "assay_expert",
        "logistics_expert",
    ),
) -> tuple[WarehouseAgentRuntime, InMemoryCheckpointer]:
    store = checkpointer or InMemoryCheckpointer()
    builder = ToolArgumentBuilder(model_client=model, business_clock=fixed_test_clock())
    return (
        WarehouseAgentRuntime(
            tool_client=tool_client,
            checkpointer=store,
            argument_builder=builder,
            model_client=model,
            planning_mode="llm",
            llm_allowed_experts=allowed_experts,
            llm_max_tool_calls=3,
            llm_max_tool_retries=1,
        ),
        store,
    )


def test_llm_planning_mode_is_disabled_by_default(monkeypatch: pytest.MonkeyPatch) -> None:
    monkeypatch.delenv("AGENT_PLANNING_MODE", raising=False)

    assert Settings.from_env().planning_mode == "deterministic"


def test_python_specific_model_mode_precedes_legacy_shared_name(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    monkeypatch.setenv("AGENT_PYTHON_MODEL_MODE", "openai_compatible")
    monkeypatch.setenv("AGENT_MODEL_MODE", "rule")

    assert Settings.from_env().model_mode == "openai_compatible"


def test_legacy_model_mode_remains_supported_for_process_local_environments(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    monkeypatch.delenv("AGENT_PYTHON_MODEL_MODE", raising=False)
    monkeypatch.setenv("AGENT_MODEL_MODE", "openai-compatible")

    assert Settings.from_env().model_mode == "openai_compatible"


def test_openai_compatible_model_has_project_defaults(monkeypatch: pytest.MonkeyPatch) -> None:
    monkeypatch.delenv("AGENT_MODEL_BASE_URL", raising=False)
    monkeypatch.delenv("AGENT_MODEL_NAME", raising=False)

    settings = Settings.from_env()

    assert settings.model_base_url == "https://api.deepseek.com"
    assert settings.model_name == "deepseek-v4-flash"


def test_stage_specific_models_default_to_the_base_model(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    monkeypatch.setenv("AGENT_MODEL_NAME", "base-test-model")
    monkeypatch.delenv("AGENT_MAIN_ROUTE_MODEL_NAME", raising=False)
    monkeypatch.delenv("AGENT_EXPERT_INITIAL_MODEL_NAME", raising=False)
    monkeypatch.delenv("AGENT_EXPERT_RESULT_MODEL_NAME", raising=False)

    settings = Settings.from_env()

    assert settings.main_route_model_name == "base-test-model"
    assert settings.expert_initial_model_name == "base-test-model"
    assert settings.expert_result_model_name == "base-test-model"


def test_stage_specific_models_can_be_overridden_independently(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    monkeypatch.setenv("AGENT_MODEL_NAME", "base-test-model")
    monkeypatch.setenv("AGENT_MAIN_ROUTE_MODEL_NAME", "main-route-test-model")
    monkeypatch.setenv("AGENT_EXPERT_INITIAL_MODEL_NAME", "expert-initial-test-model")
    monkeypatch.setenv("AGENT_EXPERT_RESULT_MODEL_NAME", "expert-result-test-model")

    settings = Settings.from_env()

    assert settings.model_name == "base-test-model"
    assert settings.main_route_model_name == "main-route-test-model"
    assert settings.expert_initial_model_name == "expert-initial-test-model"
    assert settings.expert_result_model_name == "expert-result-test-model"


def test_pallet_expert_is_enabled_after_read_only_lifecycle_rollout() -> None:
    assert "pallet_expert" in Settings().llm_allowed_experts
    assert "logistics_expert" in Settings().llm_allowed_experts
    assert "analytics_expert" in Settings().llm_allowed_experts


def test_llm_limits_are_hard_bounded(monkeypatch: pytest.MonkeyPatch) -> None:
    monkeypatch.setenv("AGENT_LLM_MAX_TOOL_CALLS", "99")
    monkeypatch.setenv("AGENT_LLM_MAX_TOOL_RETRIES", "9")

    settings = Settings.from_env()

    assert settings.llm_max_tool_calls == 3
    assert settings.llm_max_tool_retries == 1


def test_llm_run_timeout_is_separate_from_single_tool_timeout(monkeypatch: pytest.MonkeyPatch) -> None:
    monkeypatch.setenv("AGENT_PLANNING_MODE", "llm")
    monkeypatch.setenv("REQUEST_TIMEOUT_MS", "15000")
    monkeypatch.delenv("AGENT_RUN_TIMEOUT_MS", raising=False)

    settings = Settings.from_env()

    assert settings.request_timeout_ms == 15000
    assert settings.run_timeout_ms == 90000


def test_expert_action_accepts_empty_optional_values_and_non_authorizing_call_citations() -> None:
    decision = ExpertLoopDecisionV1.model_validate(
        {
            "action": "CALL_TOOL",
            "toolName": "get_inventory_overview",
            "arguments": {"productRef": "CURRENT_PRODUCT"},
            "answer": " ",
            "clarificationPrompt": "",
            "citedObservationIds": ["obs_1"],
            "statusReason": "NEED_MORE_DATA",
        }
    )

    assert decision.answer is None
    assert decision.clarificationPrompt is None
    assert decision.citedObservationIds == ["obs_1"]


def test_production_environment_rejects_experimental_llm_mode() -> None:
    from app.main import create_app

    with pytest.raises(RuntimeError, match="forbidden in production"):
        create_app(
            Settings(
                tool_mode="mock",
                model_mode="openai_compatible",
                planning_mode="llm",
                deployment_environment="production",
            ),
            tool_client=MockToolClient(),
            checkpointer=InMemoryCheckpointer(),
        )


def test_model_visible_schema_replaces_database_ids_with_state_refs() -> None:
    builder = ToolArgumentBuilder()
    handoff = AgentHandoffRouter().handoff_for_agent("inventory_expert")

    schemas = builder.llm_visible_tool_schemas(handoff)
    serialized = json.dumps(schemas, ensure_ascii=False)

    assert "productId" not in serialized
    assert "warehouseId" not in serialized
    assert "CURRENT_PRODUCT" in serialized
    assert "CURRENT_WAREHOUSE" in serialized


def test_product_quality_configuration_uses_controlled_current_product_ref() -> None:
    builder = ToolArgumentBuilder()
    handoff = AgentHandoffRouter().handoff_for_agent("assay_expert")
    schema = builder.llm_visible_tool_schemas(handoff)[
        "query_product_quality_configuration"
    ]

    assert schema["required"] == ["productRef"]
    assert schema["properties"]["productRef"]["const"] == "CURRENT_PRODUCT"
    assert "productId" not in schema["properties"]

    state = InMemoryCheckpointer().get("agt_product_quality_configuration")
    state.selected_product = SelectedEntity(
        84,
        "黄冰糖（袋）",
        "resolver",
        {"productName": "黄冰糖（袋）"},
    )

    validated = builder.validate_llm_arguments(
        tool_name="query_product_quality_configuration",
        arguments={"productRef": "CURRENT_PRODUCT"},
        state=state,
        user_message="查询黄冰糖适用的化验标准和化验组",
    )

    assert validated == {"productId": 84}


def test_llm_product_quality_configuration_resolves_product_then_aggregates_relations() -> None:
    model = ScriptedLlmModel(
        [main_delegate("assay_expert", "PRODUCT_QUALITY_CONFIGURATION")],
        [
            call("resolve_products", {"query": "黄冰糖", "limit": 10}),
            call(
                "query_product_quality_configuration",
                {"productRef": "CURRENT_PRODUCT"},
            ),
            final(
                "黄冰糖（袋）当前适用质量标准为黄冰糖 v1；所属批量化验组为成品糖批量化验组。",
                "obs_2",
            ),
        ],
    )
    tools = MockToolClient(
        {
            "resolve_products": {
                "resolutionStatus": "UNIQUE",
                "candidates": [
                    {
                        "productId": 84,
                        "productName": "黄冰糖（袋）",
                        "displayLabel": "黄冰糖（袋） 25kg/件 40件/板",
                    }
                ],
            },
            "query_product_quality_configuration": {
                "dataScope": "CURRENT_PRODUCT_QUALITY_CONFIGURATION",
                "productName": "黄冰糖（袋）",
                "productType": "冰糖",
                "productStatus": "成品",
                "standardCount": 1,
                "standards": [
                    {
                        "standardCode": "YBT",
                        "standardName": "黄冰糖 v1",
                        "version": 1,
                        "defaultStandard": True,
                        "enabled": True,
                    }
                ],
                "assayGroupCount": 1,
                "assayGroups": [
                    {"groupName": "成品糖批量化验组", "remark": "生产日批量化验"}
                ],
                "limitations": [
                    "配置关系不代表某次化验采用该标准，也不代表产品合格。"
                ],
            },
        }
    )
    runtime, store = runtime_for(model, tools)

    response = runtime.chat(
        chat_request("查询黄冰糖适用的化验标准和化验组")
    )

    assert response.error is None
    assert response.needsUserSelection is False
    assert [item["toolName"] for item in tools.calls] == [
        "resolve_products",
        "query_product_quality_configuration",
    ]
    assert tools.calls[1]["arguments"] == {"productId": 84}
    assert "黄冰糖 v1" in response.answer
    assert "成品糖批量化验组" in response.answer
    assert "产品 ID" not in response.answer
    state = store.get("agt_llm")
    assert state.active_goal_type == "PRODUCT_QUALITY_CONFIGURATION"
    assert state.last_goal_completion["status"] == "COMPLETE"


def test_model_visible_schema_explains_global_scope_and_flat_argument_tools() -> None:
    builder = ToolArgumentBuilder()
    inventory = builder.llm_visible_tool_schemas(
        AgentHandoffRouter().handoff_for_agent("inventory_expert")
    )
    warehouse = builder.llm_visible_tool_schemas(
        AgentHandoffRouter().handoff_for_agent("warehouse_expert")
    )
    assay = builder.llm_visible_tool_schemas(
        AgentHandoffRouter().handoff_for_agent("assay_expert")
    )
    logistics = builder.llm_visible_tool_schemas(
        AgentHandoffRouter().handoff_for_agent("logistics_expert")
    )
    audit = builder.llm_visible_tool_schemas(
        AgentHandoffRouter().handoff_for_agent("audit_expert")
    )

    assert "扁平字段" in inventory["query_inventory_ledger"]["description"]
    assert "query_prepare_pool_balance" not in inventory
    assert "省略 warehouseRef" in warehouse["query_warehouse_recent_operations"]["description"]
    assert "全部仓库" in warehouse["query_warehouse_mixed_storage_facts"]["description"]
    assert "productScope=ALL" in assay["query_products_without_recent_assay"]["description"]
    assert "PRODUCT_WITHOUT_STANDARD" in assay["query_assay_standard_coverage"]["description"]
    assert "权威无数据结果" in logistics["query_auto_inbound_batches"]["description"]
    assert "reviewStatus=OPEN" in audit["query_agent_answer_reviews"]["description"]


def test_agent_answer_review_filters_normalize_pending_alias_and_reject_unknown_enum() -> None:
    builder = ToolArgumentBuilder()
    state = InMemoryCheckpointer().get("agt_agent_review_filters")

    schema = builder.llm_visible_tool_schemas(
        AgentHandoffRouter().handoff_for_agent("audit_expert")
    )["query_agent_answer_reviews"]
    assert schema["properties"]["reviewStatus"]["enum"] == [
        "OPEN",
        "TRIAGED",
        "FIXED",
        "WONT_FIX",
    ]
    assert "待复核" in schema["properties"]["reviewStatus"]["description"]

    validated = builder.validate_llm_arguments(
        tool_name="query_agent_answer_reviews",
        arguments={"reviewStatus": "PENDING", "page": 1, "size": 20},
        state=state,
        user_message="查询待复核的 Agent 回答",
    )
    assert validated["reviewStatus"] == "OPEN"

    with pytest.raises(ValueError, match="unsupported reviewStatus"):
        builder.validate_llm_arguments(
            tool_name="query_agent_answer_reviews",
            arguments={"reviewStatus": "WAITING_V2", "page": 1, "size": 20},
            state=state,
            user_message="查询待复核的 Agent 回答",
        )


def test_active_goal_is_exposed_to_expert_as_controlled_contract() -> None:
    model = ScriptedLlmModel([], [])
    runtime, store = runtime_for(model, MockToolClient())
    state = store.get("agt_llm")
    state.active_goal_type = "IN_PROCESS_MATERIALS"

    active_goal = runtime._llm_selected_context(state)["ACTIVE_GOAL"]

    assert active_goal["businessResult"] == "已确认领用并扣减库存、订单未完成的在制半成品"
    assert active_goal["ownerExpert"] == "production_expert"
    assert active_goal["requiredEntityTypes"] == []
    assert active_goal["allowedTools"] == ["resolve_products", "query_in_process_materials"]
    assert active_goal["evidenceTools"] == ["query_in_process_materials"]


def test_scope_only_followup_reuses_active_goal_expert() -> None:
    model = ScriptedLlmModel([], [])
    runtime, store = runtime_for(model, MockToolClient())
    state = store.get("agt_llm")
    handoff = runtime.agent_router.handoff_for_agent("assay_expert", mode="llm_delegate")
    state.active_agent = "assay_expert"
    state.last_agent_handoff = handoff.to_snapshot()
    state.active_goal_type = "CURRENT_INVENTORY_ASSAY_GAPS"

    assert runtime._llm_context_followup_expert(state, "最近30天") == "assay_expert"

    warehouse_handoff = runtime.agent_router.handoff_for_agent("warehouse_expert", mode="llm_delegate")
    state.active_agent = "warehouse_expert"
    state.last_agent_handoff = warehouse_handoff.to_snapshot()
    state.active_goal_type = "WAREHOUSE_MIXED_STORAGE_FACTS"

    assert runtime._llm_context_followup_expert(state, "全部仓库") == "warehouse_expert"


def test_recent_days_followup_keeps_active_goal_all_product_scope() -> None:
    builder = ToolArgumentBuilder(business_clock=fixed_test_clock())
    state = InMemoryCheckpointer().get("agt_assay_gap_followup")
    arguments = {
        "productScope": {"type": "ALL"},
        "warehouseScope": {"type": "ALL"},
        "population": "CURRENT_INVENTORY",
        "dateRange": {"type": "LAST_DAYS", "days": 30},
        "groupBy": "product",
        "limit": 50,
    }

    with pytest.raises(ValueError, match="ALL product scope"):
        builder.validate_llm_arguments(
            tool_name="query_products_without_recent_assay",
            arguments=arguments,
            state=state,
            user_message="最近30天",
        )

    state.active_goal_type = "CURRENT_INVENTORY_ASSAY_GAPS"
    validated = builder.validate_llm_arguments(
        tool_name="query_products_without_recent_assay",
        arguments=arguments,
        state=state,
        user_message="最近30天",
    )

    assert validated["productScope"] == {"type": "ALL"}
    assert validated["warehouseScope"] == {"type": "ALL"}
    assert validated["dateRange"] == {"type": "LAST_DAYS", "days": 30}

    normalized_defaults = builder.validate_llm_arguments(
        tool_name="query_products_without_recent_assay",
        arguments={"dateRange": {"type": "LAST_DAYS", "days": 30}},
        state=state,
        user_message="最近30天",
    )

    assert normalized_defaults["productScope"] == {"type": "ALL"}
    assert normalized_defaults["warehouseScope"] == {"type": "ALL"}
    assert normalized_defaults["population"] == "CURRENT_INVENTORY"


def test_current_inventory_without_applicable_standard_allows_global_scope() -> None:
    builder = ToolArgumentBuilder(business_clock=fixed_test_clock())
    state = InMemoryCheckpointer().get("agt_standard_gap")

    validated = builder.validate_llm_arguments(
        tool_name="query_assay_standard_coverage",
        arguments={
            "productScope": {"type": "ALL"},
            "coverageType": "PRODUCT_WITHOUT_STANDARD",
            "limit": 50,
        },
        state=state,
        user_message="哪些当前库存没有适用的化验标准？",
    )

    assert validated["productScope"] == {"type": "ALL"}
    assert validated["coverageType"] == "PRODUCT_WITHOUT_STANDARD"


def test_model_visible_schema_uses_controlled_assay_and_pallet_refs() -> None:
    builder = ToolArgumentBuilder()
    assay_schemas = builder.llm_visible_tool_schemas(
        AgentHandoffRouter().handoff_for_agent("assay_expert")
    )
    pallet_schemas = builder.llm_visible_tool_schemas(
        AgentHandoffRouter().handoff_for_agent("pallet_expert")
    )

    assert assay_schemas["get_assay_report_detail"]["properties"]["reportRef"]["const"] == (
        "CURRENT_ASSAY_REPORT"
    )
    pallet_status = pallet_schemas["get_pallet_status"]
    assert pallet_status["properties"]["palletRef"]["const"] == "CURRENT_PALLET"
    assert "code" not in pallet_status.get("required", [])
    assert pallet_status["oneOf"] == [
        {"required": ["code"]},
        {"required": ["palletRef"]},
    ]


def test_model_visible_schema_uses_controlled_production_refs() -> None:
    schemas = ToolArgumentBuilder().llm_visible_tool_schemas(
        AgentHandoffRouter().handoff_for_agent("production_expert")
    )

    assert schemas["query_boiling_batch_trace"]["properties"]["batchRef"]["const"] == (
        "CURRENT_BOILING_BATCH"
    )
    assert schemas["query_production_order_progress"]["properties"]["orderRef"]["const"] == (
        "CURRENT_PRODUCTION_ORDER"
    )
    assert schemas["query_material_pick_trace"]["properties"]["orderRef"]["const"] == (
        "CURRENT_PRODUCTION_ORDER"
    )


def test_analytics_expert_only_runs_registered_report_with_fixed_definition() -> None:
    router = AgentHandoffRouter()
    handoff = router.handoff_for_agent("analytics_expert")
    profile = router.profile("analytics_expert")
    schemas = ToolArgumentBuilder().llm_visible_tool_schemas(handoff)

    assert handoff.allowed_tools == ("run_registered_report",)
    assert any("31 天" in instruction for instruction in profile.instructions)
    assert any("下方卡片" in instruction for instruction in profile.instructions)
    assert any("逐字引用" in instruction for instruction in profile.instructions)
    assert any("折算后的总件数" in instruction for instruction in profile.instructions)
    assert set(schemas) == {"run_registered_report"}
    assert schemas["run_registered_report"]["properties"]["reportDefinitionId"]["enum"] == [
        "daily_production_overview_v1",
        "quality_assay_result_trend_v1",
        "quality_metric_trend_v1",
        "production_input_output_flow_v1",
        "pallet_task_cycle_time_v1",
        "inventory_level_trend_v1",
        "today_operations_overview_v1",
    ]
    assert "ph" in schemas["run_registered_report"]["properties"]["metricKey"]["enum"]
    assert schemas["run_registered_report"]["properties"]["reportVersion"]["const"] == 1


def test_registered_report_arguments_reject_unknown_definition_and_overlong_range() -> None:
    builder = ToolArgumentBuilder(business_clock=fixed_test_clock())
    state = InMemoryCheckpointer().get("agt_registered_report_arguments")

    with pytest.raises(ValueError, match="unsupported reportDefinitionId"):
        builder.validate_llm_arguments(
            tool_name="run_registered_report",
            arguments={
                "reportDefinitionId": "arbitrary_report",
                "reportVersion": 1,
                "startDate": "2026-07-21",
                "endDate": "2026-07-21",
            },
            state=state,
            user_message="今天产量如何",
        )

    with pytest.raises(ValueError, match="31 days"):
        builder.validate_llm_arguments(
            tool_name="run_registered_report",
            arguments={
                "reportDefinitionId": "daily_production_overview_v1",
                "reportVersion": 1,
                "startDate": "2026-06-01",
                "endDate": "2026-07-21",
            },
            state=state,
            user_message="最近两个月产量如何",
        )

    quality_arguments = builder.validate_llm_arguments(
        tool_name="run_registered_report",
        arguments={
            "reportDefinitionId": "quality_assay_result_trend_v1",
            "reportVersion": 1,
            "startDate": "2025-07-22",
            "endDate": "2026-07-21",
            "productQuery": "黄冰糖",
        },
        state=state,
        user_message="最近一年黄冰糖质量趋势",
    )
    assert quality_arguments["reportDefinitionId"] == "quality_assay_result_trend_v1"
    assert quality_arguments["productQuery"] == "黄冰糖"

    metric_arguments = builder.validate_llm_arguments(
        tool_name="run_registered_report",
        arguments={
            "reportDefinitionId": "quality_metric_trend_v1",
            "reportVersion": 1,
            "startDate": "2026-01-23",
            "endDate": "2026-07-21",
            "productQuery": "黄冰糖（袋）",
            "metricKey": "ph",
        },
        state=state,
        user_message="最近180天黄冰糖（袋）的pH趋势",
    )
    assert metric_arguments["metricKey"] == "ph"

    flow_arguments = builder.validate_llm_arguments(
        tool_name="run_registered_report",
        arguments={
            "reportDefinitionId": "production_input_output_flow_v1",
            "reportVersion": 1,
            "startDate": "2026-01-23",
            "endDate": "2026-07-21",
            "productQuery": "黄冰糖（袋）",
        },
        state=state,
        user_message="最近180天黄冰糖（袋）的领料和登记产出趋势",
    )
    assert flow_arguments["reportDefinitionId"] == "production_input_output_flow_v1"
    assert flow_arguments["productQuery"] == "黄冰糖（袋）"

    today_arguments = builder.validate_llm_arguments(
        tool_name="run_registered_report",
        arguments={
            "reportDefinitionId": "today_operations_overview_v1",
            "reportVersion": 1,
            "startDate": "2026-07-21",
            "endDate": "2026-07-21",
        },
        state=state,
        user_message="今天整体运营情况如何",
    )
    assert today_arguments == {
        "reportDefinitionId": "today_operations_overview_v1",
        "reportVersion": 1,
        "startDate": "2026-07-21",
        "endDate": "2026-07-21",
    }

    with pytest.raises(ValueError, match="Beijing today"):
        builder.validate_llm_arguments(
            tool_name="run_registered_report",
            arguments={
                "reportDefinitionId": "today_operations_overview_v1",
                "reportVersion": 1,
                "startDate": "2026-07-20",
                "endDate": "2026-07-20",
            },
            state=state,
            user_message="昨天整体运营情况如何",
        )

    with pytest.raises(ValueError, match="does not support filters"):
        builder.validate_llm_arguments(
            tool_name="run_registered_report",
            arguments={
                "reportDefinitionId": "today_operations_overview_v1",
                "reportVersion": 1,
                "startDate": "2026-07-21",
                "endDate": "2026-07-21",
                "productQuery": "黄冰糖",
            },
            state=state,
            user_message="今天黄冰糖运营情况如何",
        )

    with pytest.raises(ValueError, match="metricKey is required"):
        builder.validate_llm_arguments(
            tool_name="run_registered_report",
            arguments={
                "reportDefinitionId": "quality_metric_trend_v1",
                "reportVersion": 1,
                "startDate": "2026-01-23",
                "endDate": "2026-07-21",
            },
            state=state,
            user_message="最近180天的指标趋势",
        )

    with pytest.raises(ValueError, match="366 days"):
        builder.validate_llm_arguments(
            tool_name="run_registered_report",
            arguments={
                "reportDefinitionId": "quality_assay_result_trend_v1",
                "reportVersion": 1,
                "startDate": "2025-07-01",
                "endDate": "2026-07-21",
            },
            state=state,
            user_message="查询超过一年的化验趋势",
        )

    with pytest.raises(ValueError, match="366 days"):
        builder.validate_llm_arguments(
            tool_name="run_registered_report",
            arguments={
                "reportDefinitionId": "production_input_output_flow_v1",
                "reportVersion": 1,
                "startDate": "2025-07-01",
                "endDate": "2026-07-21",
            },
            state=state,
            user_message="查询超过一年的生产领料产出趋势",
        )


def test_registered_report_arguments_validate_deterministic_comparison_periods() -> None:
    builder = ToolArgumentBuilder(business_clock=fixed_test_clock())
    state = InMemoryCheckpointer().get("agt_registered_report_comparison")
    base = {
        "reportDefinitionId": "daily_production_overview_v1",
        "reportVersion": 1,
        "startDate": "2026-07-15",
        "endDate": "2026-07-21",
    }

    previous = builder.validate_llm_arguments(
        tool_name="run_registered_report",
        arguments={**base, "comparisonMode": "PREVIOUS_PERIOD"},
        state=state,
        user_message="和上周比，最近 7 天产量如何",
    )
    assert previous["comparisonMode"] == "PREVIOUS_PERIOD"
    assert "comparisonStartDate" not in previous

    custom = builder.validate_llm_arguments(
        tool_name="run_registered_report",
        arguments={
            **base,
            "comparisonMode": "CUSTOM",
            "comparisonStartDate": "2026-07-01",
            "comparisonEndDate": "2026-07-07",
        },
        state=state,
        user_message="最近 7 天和 7 月 1 日到 7 日对比",
    )
    assert custom["comparisonStartDate"] == "2026-07-01"

    with pytest.raises(ValueError, match="must not overlap"):
        builder.validate_llm_arguments(
            tool_name="run_registered_report",
            arguments={
                **base,
                "comparisonMode": "CUSTOM",
                "comparisonStartDate": "2026-07-14",
                "comparisonEndDate": "2026-07-16",
            },
            state=state,
            user_message="对比这两个重叠区间",
        )


def test_llm_daily_production_report_uses_analytics_expert_and_returns_report_card() -> None:
    model = ScriptedLlmModel(
        [main_delegate("analytics_expert", "DAILY_PRODUCTION_ANALYSIS")],
        [
            call(
                "run_registered_report",
                {
                    "reportDefinitionId": "daily_production_overview_v1",
                    "reportVersion": 1,
                    "startDate": "2026-07-21",
                    "endDate": "2026-07-21",
                },
            ),
            final(
                "今天已登记产出 2500 kg，涉及 2 个生产订单、3 条产出记录。",
                "obs_1",
            ),
        ],
    )
    tools = MockToolClient(
        {
            "run_registered_report": {
                "reportRunId": "rr_opaque",
                "reportDefinitionId": "daily_production_overview_v1",
                "reportVersion": 1,
                "reportName": "生产登记产出日报",
                "dateRangeLabel": "2026-07-21",
                "filtersApplied": {"productScope": "全部产品"},
                "dataAsOf": "2026-07-21T12:00:00+08:00",
                "latestRecordAt": "2026-07-21T11:30:00",
                "isEmpty": False,
                "metrics": {
                    "outputRecordCount": 3,
                    "productionOrderCount": 2,
                    "totalWeightKg": 2500,
                    "totalBoardCount": 3,
                    "loosePieceCount": 5,
                    "totalPieces": 125,
                    "requiredQrCount": 120,
                    "boundQrCount": 100,
                    "inboundQrCount": 80,
                },
                "dailySeries": [
                    {
                        "businessDate": "2026-07-21",
                        "outputRecordCount": 3,
                        "productionOrderCount": 2,
                        "totalWeightKg": 2500,
                        "totalBoardCount": 3,
                        "loosePieceCount": 5,
                        "totalPieces": 125,
                    }
                ],
                "productBreakdowns": [
                    {
                        "productName": "黄冰糖（袋）",
                        "outputRecordCount": 3,
                        "productionOrderCount": 2,
                        "totalWeightKg": 2500,
                        "totalBoardCount": 3,
                        "loosePieceCount": 5,
                        "totalPieces": 125,
                    }
                ],
                "comparison": {
                    "comparisonMode": "PREVIOUS_PERIOD",
                    "comparisonLabel": "上一等长期间",
                    "currentDateRangeLabel": "2026-07-21",
                    "comparisonDateRangeLabel": "2026-07-20",
                    "currentPeriodDays": 1,
                    "comparisonPeriodDays": 1,
                    "differentPeriodLengths": False,
                    "metrics": [
                        {
                            "metricCode": "totalWeightKg",
                            "metricLabel": "已登记产出重量",
                            "unit": "kg",
                            "additive": True,
                            "currentValue": 2500,
                            "comparisonValue": 3000,
                            "absoluteChange": -500,
                            "percentChange": -16.6667,
                            "currentDailyAverage": 2500,
                            "comparisonDailyAverage": 3000,
                            "dailyAverageAbsoluteChange": -500,
                            "dailyAveragePercentChange": -16.6667,
                        }
                    ],
                    "notes": ["变化只描述登记数值差异，不代表改善或恶化。"],
                },
                "dataQuality": {
                    "partial": False,
                    "rowsMissingWeight": 0,
                    "rowsMissingPieceConversion": 0,
                    "rowsMissingProductName": 0,
                    "notes": [],
                },
                "limitations": [
                    "只统计生产订单中已登记且未取消的产出记录。",
                    "二维码绑定和入库进度不计入产量。",
                ],
            }
        }
    )
    runtime, store = runtime_for(
        model,
        tools,
        allowed_experts=("analytics_expert",),
    )

    response = runtime.chat(chat_request("今天产量如何？"))

    assert response.error is None
    assert response.answer == "今天已登记产出 2500 kg，涉及 2 个生产订单、3 条产出记录。"
    assert [item["toolName"] for item in tools.calls] == ["run_registered_report"]
    assert tools.calls[0]["expertAgent"] == "analytics_expert"
    assert tools.calls[0]["arguments"] == {
        "reportDefinitionId": "daily_production_overview_v1",
        "reportVersion": 1,
        "startDate": "2026-07-21",
        "endDate": "2026-07-21",
    }
    assert [card.cardType for card in response.cards] == ["daily_production_report"]
    assert response.cards[0].fields[0]["comparison"]["metrics"][0]["absoluteChange"] == "-500"
    assert store.get("agt_llm").last_goal_completion["status"] == "COMPLETE"
    assert store.get("agt_llm").last_report_context["reportName"] == "生产登记产出日报"
    assert store.get("agt_llm").last_report_context["comparison"]["comparisonMode"] == "PREVIOUS_PERIOD"
    assert "daily_production_overview_v1" not in json.dumps(
        model.expert_requests[-1].observations,
        ensure_ascii=False,
    )


def test_llm_today_operations_overview_uses_registered_facts_and_safe_card() -> None:
    model = ScriptedLlmModel(
        [main_delegate("analytics_expert", "TODAY_OPERATIONS_OVERVIEW")],
        [
            call(
                "run_registered_report",
                {
                    "reportDefinitionId": "today_operations_overview_v1",
                    "reportVersion": 1,
                    "startDate": "2026-07-21",
                    "endDate": "2026-07-21",
                },
            ),
            final(
                "今天已登记产出 1980 kg，共有 3 条化验记录；当前待处理任务 7 条。各模块口径见卡片。",
                "obs_1",
            ),
        ],
    )
    tools = MockToolClient(
        {
            "run_registered_report": {
                "reportRunId": "report_run_today123456",
                "reportDefinitionId": "today_operations_overview_v1",
                "reportVersion": 1,
                "reportName": "今日运营概览",
                "dateRangeLabel": "2026-07-21",
                "dataAsOf": "2026-07-21T16:30:00+08:00",
                "latestRecordAt": "2026-07-21T16:15:00",
                "operationsOverview": {
                    "businessDate": "2026-07-21",
                    "productionOutput": {
                        "outputRecordCount": 1,
                        "productionOrderCount": 1,
                        "totalWeightKg": 1980,
                        "totalPieces": 80,
                    },
                    "assayQuality": {
                        "assayRecordCount": 3,
                        "judgedRecordCount": 2,
                        "passCount": 2,
                        "failCount": 0,
                        "noStandardCount": 1,
                        "multipleCandidatesCount": 0,
                        "passRatePercent": 100,
                    },
                    "productionFlow": {
                        "materialInputRecordCount": 2,
                        "materialInputOrderCount": 1,
                        "materialInputWeightKg": 2000,
                        "stableOutputRecordCount": 1,
                        "stableOutputOrderCount": 1,
                        "stableOutputWeightKg": 1980,
                    },
                    "currentInventory": {
                        "productCount": 5,
                        "warehouseCount": 3,
                        "palletCount": 12,
                        "totalEquivalentPieces": 550,
                        "totalStockText": "13 板 30 件",
                        "totalWeightText": "13750 kg",
                    },
                    "todayPalletTasks": {
                        "cohortTaskCount": 4,
                        "completedTaskCount": 2,
                        "inProgressTaskCount": 1,
                        "canceledTaskCount": 1,
                    },
                    "currentPendingTaskCount": 7,
                },
                "dataQuality": {"partial": False, "notes": []},
                "limitations": [
                    "今日事实与当前快照分开解释。",
                    "领用与产出不能直接相除。",
                ],
            }
        }
    )
    runtime, store = runtime_for(
        model,
        tools,
        allowed_experts=("analytics_expert",),
    )

    response = runtime.chat(chat_request("今天整体运营情况如何？"))

    assert response.error is None
    assert [item["toolName"] for item in tools.calls] == ["run_registered_report"]
    assert tools.calls[0]["arguments"]["reportDefinitionId"] == (
        "today_operations_overview_v1"
    )
    assert [card.cardType for card in response.cards] == [
        "today_operations_overview_report"
    ]
    fields = response.cards[0].fields
    assert fields[0]["kind"] == "today_operations_overview_summary"
    assert next(
        item for item in fields if item["kind"] == "today_operations_quality"
    )["judgedRecordCount"] == 2
    assert next(
        item for item in fields if item["kind"] == "today_operations_tasks"
    )["currentPendingTaskCount"] == 7
    assert store.get("agt_llm").last_goal_completion["status"] == "COMPLETE"


def test_llm_production_input_output_flow_uses_independent_series_and_safe_card() -> None:
    model = ScriptedLlmModel(
        [main_delegate("analytics_expert", "PRODUCTION_INPUT_OUTPUT_TREND")],
        [
            call(
                "run_registered_report",
                {
                    "reportDefinitionId": "production_input_output_flow_v1",
                    "reportVersion": 1,
                    "startDate": "2026-06-01",
                    "endDate": "2026-07-21",
                    "productQuery": "黄冰糖（袋）",
                },
            ),
            final(
                "领料和稳定登记产出分别见卡片。两者采用不同业务日期，不能直接计算产耗比。",
                "obs_1",
            ),
        ],
    )
    tools = MockToolClient(
        {
            "run_registered_report": {
                "reportRunId": "report_run_0123456789abcdef",
                "reportDefinitionId": "production_input_output_flow_v1",
                "reportVersion": 1,
                "reportName": "生产领料—登记产出趋势",
                "dateRangeLabel": "2026-06-01 至 2026-07-21",
                "filtersApplied": {
                    "productScope": "稳定登记产出产品名称包含“黄冰糖（袋）”"
                },
                "dataAsOf": "2026-07-21T12:00:00+08:00",
                "latestRecordAt": "2026-06-30T15:34:00",
                "seriesGranularity": "DAY",
                "productionFlowMetrics": {
                    "materialInputRecordCount": 2,
                    "materialInputOrderCount": 1,
                    "materialInputPalletCount": 2,
                    "materialInputBoardCount": 2,
                    "materialInputLoosePieceCount": 0,
                    "materialInputTotalPieces": 80,
                    "materialInputWeightKg": 1656.8,
                    "stableOutputRecordCount": 3,
                    "stableOutputOrderCount": 2,
                    "stableOutputBoardCount": 3,
                    "stableOutputLoosePieceCount": 0,
                    "stableOutputTotalPieces": 120,
                    "stableOutputWeightKg": 1983.8,
                    "cohortOrderCount": 2,
                    "completedOrderCount": 2,
                    "completedOrdersWithInputCount": 1,
                    "completedOrdersMissingInputCount": 1,
                    "completedOrdersWithStableOutputCount": 2,
                    "completedOrdersMissingOutputCount": 0,
                    "ordersWithInputCount": 1,
                    "ordersMissingInputCount": 1,
                    "ordersWithStableOutputCount": 2,
                    "ordersMissingOutputCount": 0,
                    "cohortMaterialInputWeightKg": 1656.8,
                    "cohortBoilingInputWeightKg": 0,
                    "cohortStableOutputWeightKg": 1983.8,
                },
                "productionFlowDailySeries": [
                    {
                        "businessDate": "2026-06-29",
                        "materialInputRecordCount": 2,
                        "materialInputOrderCount": 1,
                        "materialInputPalletCount": 2,
                        "materialInputTotalPieces": 80,
                        "materialInputWeightKg": 1656.8,
                        "stableOutputRecordCount": 0,
                        "stableOutputOrderCount": 0,
                        "stableOutputTotalPieces": 0,
                        "stableOutputWeightKg": 0,
                    },
                    {
                        "businessDate": "2026-06-30",
                        "materialInputRecordCount": 0,
                        "materialInputOrderCount": 0,
                        "materialInputPalletCount": 0,
                        "materialInputTotalPieces": 0,
                        "materialInputWeightKg": 0,
                        "stableOutputRecordCount": 3,
                        "stableOutputOrderCount": 2,
                        "stableOutputTotalPieces": 120,
                        "stableOutputWeightKg": 1983.8,
                    },
                ],
                "productionFlowOrderBreakdowns": [
                    {
                        "orderNo": "PO202606300001",
                        "orderTypeLabel": "成品生产",
                        "orderStatusLabel": "已完成",
                        "productionDate": "2026-06-30",
                        "inputSourceLabel": "实际领料记录",
                        "inputRecordCount": 2,
                        "inputPalletCount": 2,
                        "inputTotalPieces": 80,
                        "inputWeightKg": 1656.8,
                        "stableOutputRecordCount": 1,
                        "stableOutputTotalPieces": 40,
                        "stableOutputWeightKg": 327,
                        "outputProductNames": "黄冰糖（袋）",
                        "completenessLabel": "输入和稳定产出均已登记",
                    },
                    {
                        "orderNo": "PO202606300002",
                        "orderTypeLabel": "成品生产",
                        "orderStatusLabel": "已完成",
                        "productionDate": "2026-06-30",
                        "inputSourceLabel": "实际领料记录",
                        "inputRecordCount": 0,
                        "inputPalletCount": 0,
                        "inputTotalPieces": 0,
                        "inputWeightKg": 0,
                        "stableOutputRecordCount": 2,
                        "stableOutputTotalPieces": 80,
                        "stableOutputWeightKg": 1656.8,
                        "outputProductNames": "黄冰糖（袋）",
                        "completenessLabel": "缺少输入登记",
                    },
                ],
                "dataQuality": {
                    "partial": True,
                    "completedOrderCount": 2,
                    "completedOrdersWithInputCount": 1,
                    "completedOrdersMissingInputCount": 1,
                    "completedOrdersWithStableOutputCount": 2,
                    "completedOrdersMissingOutputCount": 0,
                    "unattributedOrderCount": 0,
                    "orderBreakdownTruncated": False,
                    "notes": ["1 个已完成订单缺少输入登记。"],
                },
                "limitations": [
                    "确认领用时已完成库存扣减，但领用确认时间与产出生产日期不能直接相除。",
                    "每日领料按领料发生时间统计，每日产出按生产日期统计。",
                ],
            }
        }
    )
    runtime, store = runtime_for(
        model,
        tools,
        allowed_experts=("analytics_expert",),
    )

    response = runtime.chat(
        chat_request("最近180天黄冰糖（袋）的生产领料和登记产出趋势")
    )

    assert response.error is None
    assert tools.calls[0]["arguments"]["reportDefinitionId"] == (
        "production_input_output_flow_v1"
    )
    assert [card.cardType for card in response.cards] == [
        "production_input_output_flow_report"
    ]
    assert response.cards[0].fields[0]["reportRunId"] == (
        "report_run_0123456789abcdef"
    )
    assert any(
        field["kind"] == "production_input_output_flow_scope_note"
        and "不计算产耗比" in field["value"]
        for field in response.cards[0].fields
    )
    assert all(
        "FINISH" not in json.dumps(field, ensure_ascii=False)
        and "COMPLETED" not in json.dumps(field, ensure_ascii=False)
        for field in response.cards[0].fields
    )
    assert store.get("agt_llm").last_goal_completion["goalType"] == (
        "PRODUCTION_INPUT_OUTPUT_TREND"
    )
    assert store.get("agt_llm").last_goal_completion["status"] == "COMPLETE"


def test_llm_inventory_level_trend_preserves_simulation_boundary_and_card() -> None:
    model = ScriptedLlmModel(
        [main_delegate("analytics_expert", "INVENTORY_LEVEL_TREND_ANALYSIS")],
        [
            call(
                "run_registered_report",
                {
                    "reportDefinitionId": "inventory_level_trend_v1",
                    "reportVersion": 1,
                    "startDate": "2026-07-14",
                    "endDate": "2026-07-20",
                    "productQuery": "黄冰糖（袋）",
                },
            ),
            final(
                "这 7 天黄冰糖（袋）库存由 550 件增至 790 件，净增加 240 件。当前结果是本地历史回放模拟，不能作为正式日终快照。",
                "obs_1",
            ),
        ],
    )
    tools = MockToolClient(
        {
            "run_registered_report": {
                "reportRunId": "report_run_inventory_uat",
                "reportDefinitionId": "inventory_level_trend_v1",
                "reportVersion": 1,
                "reportName": "库存水平趋势（历史回放模拟）",
                "dateRangeLabel": "2026-07-14 至 2026-07-20",
                "filtersApplied": {
                    "productScope": "产品名称包含“黄冰糖（袋）”",
                    "dataSource": "本地历史回放模拟",
                },
                "dataAsOf": "2026-08-01T12:00:00+08:00",
                "latestRecordAt": "2026-07-20T16:00:00",
                "inventoryTrendMetrics": {
                    "observationDayCount": 7,
                    "openingPieces": 550,
                    "closingPieces": 790,
                    "netChangePieces": 240,
                    "openingWeightKg": 13750,
                    "closingWeightKg": 19750,
                    "netChangeWeightKg": 6000,
                    "increaseDayCount": 1,
                    "decreaseDayCount": 0,
                    "unchangedDayCount": 5,
                },
                "inventoryTrendDailySeries": [
                    {
                        "businessDate": "2026-07-14",
                        "totalPieces": 550,
                        "totalWeightKg": 13750,
                        "pieceChange": 0,
                        "weightChangeKg": 0,
                        "movementRecordCount": 0,
                    },
                    {
                        "businessDate": "2026-07-20",
                        "totalPieces": 790,
                        "totalWeightKg": 19750,
                        "pieceChange": 240,
                        "weightChangeKg": 6000,
                        "movementRecordCount": 6,
                    },
                ],
                "inventoryTrendProductBreakdowns": [
                    {
                        "productName": "黄冰糖（袋）",
                        "openingPieces": 550,
                        "closingPieces": 790,
                        "netChangePieces": 240,
                        "openingWeightKg": 13750,
                        "closingWeightKg": 19750,
                        "netChangeWeightKg": 6000,
                    }
                ],
                "dataQuality": {
                    "partial": True,
                    "simulationData": True,
                    "replayMovementRecordCount": 6,
                    "replayAnchorReconciled": True,
                    "trustedSnapshotDayCount": 0,
                    "requiredSnapshotDayCount": 7,
                    "notes": ["当前为本地历史回放模拟。"],
                },
                "limitations": ["正式环境仍需连续 7 天真实日终快照。"],
            }
        }
    )
    runtime, store = runtime_for(
        model,
        tools,
        allowed_experts=("analytics_expert",),
    )

    response = runtime.chat(
        chat_request("2026年7月14日至20日黄冰糖（袋）的库存变化趋势如何")
    )

    assert response.error is None
    assert tools.calls[0]["arguments"]["reportDefinitionId"] == (
        "inventory_level_trend_v1"
    )
    assert [card.cardType for card in response.cards] == [
        "inventory_level_trend_report"
    ]
    summary = response.cards[0].fields[0]
    assert summary["simulationData"] is True
    assert summary["openingPieces"] == 550
    assert summary["closingPieces"] == 790
    assert any(
        field["kind"] == "inventory_level_trend_scope_note"
        and "正式上线" in field["value"]
        for field in response.cards[0].fields
    )
    assert store.get("agt_llm").last_goal_completion["goalType"] == (
        "INVENTORY_LEVEL_TREND_ANALYSIS"
    )
    assert store.get("agt_llm").last_goal_completion["status"] == "COMPLETE"


def test_llm_pallet_task_cycle_separates_duration_waiting_and_scope() -> None:
    model = ScriptedLlmModel(
        [main_delegate("analytics_expert", "PROCESS_EFFICIENCY_TREND")],
        [
            call(
                "run_registered_report",
                {
                    "reportDefinitionId": "pallet_task_cycle_time_v1",
                    "reportVersion": 1,
                    "startDate": "2026-05-01",
                    "endDate": "2026-07-31",
                    "taskType": "FINISH_IN",
                },
            ),
            final(
                "最近90天共登记28条成品入库任务，其中21条已完成、4条仍在等待、3条已取消。完成耗时与等待时长口径不同，详情见卡片。",
                "obs_1",
            ),
        ],
    )
    tools = MockToolClient(
        {
            "run_registered_report": {
                "reportRunId": "report_run_0123456789abcdef",
                "reportDefinitionId": "pallet_task_cycle_time_v1",
                "reportVersion": 1,
                "reportName": "托盘任务处理耗时趋势",
                "dateRangeLabel": "2026-05-01 至 2026-07-31",
                "filtersApplied": {
                    "productScope": "全部产品",
                    "taskScope": "成品入库任务",
                },
                "dataAsOf": "2026-07-31T18:00:00+08:00",
                "latestRecordAt": "2026-07-20T16:00:00",
                "palletTaskCycleMetrics": {
                    "cohortTaskCount": 28,
                    "completedTaskCount": 21,
                    "inProgressTaskCount": 4,
                    "canceledTaskCount": 3,
                    "invalidTaskCount": 0,
                    "averageDurationSeconds": 243780,
                    "medianDurationSeconds": 48,
                    "p90DurationSeconds": 5097116,
                    "maximumDurationSeconds": 5108516,
                    "medianWaitingSeconds": 6712056,
                    "p90WaitingSeconds": 7351380,
                    "maximumWaitingSeconds": 7351380,
                },
                "palletTaskCycleDailySeries": [
                    {
                        "businessDate": "2026-06-30",
                        "taskCount": 4,
                        "completedTaskCount": 2,
                        "inProgressTaskCount": 1,
                        "canceledTaskCount": 1,
                        "medianDurationSeconds": 48,
                    }
                ],
                "palletTaskCycleTypeBreakdowns": [
                    {
                        "taskTypeLabel": "成品入库任务",
                        "taskCount": 28,
                        "completedTaskCount": 21,
                        "inProgressTaskCount": 4,
                        "canceledTaskCount": 3,
                        "medianDurationSeconds": 48,
                    }
                ],
                "palletTaskPendingItems": [
                    {
                        "palletCode": "BT001",
                        "taskTypeLabel": "成品入库任务",
                        "productName": "黄冰糖（袋）",
                        "createdAt": "2026-05-01T10:00:00",
                        "waitingSeconds": 7351380,
                        "targetWarehouseName": "尚未登记目标库位",
                    }
                ],
                "dataQuality": {
                    "partial": True,
                    "tasksWithoutOperationBatchCount": 24,
                    "notes": ["24 条任务没有操作批次号，不能计算批量作业耗时。"],
                },
                "limitations": [
                    "当前没有登记 SLA，只能描述已等待时长，不能称为逾期。",
                    "本报表不代表现场全部操作或员工绩效。",
                ],
            }
        }
    )
    runtime, store = runtime_for(
        model,
        tools,
        allowed_experts=("analytics_expert",),
    )

    response = runtime.chat(chat_request("最近90天成品入库任务处理耗时如何"))

    assert response.error is None
    assert tools.calls[0]["arguments"]["taskType"] == "FINISH_IN"
    assert [card.cardType for card in response.cards] == [
        "pallet_task_cycle_report"
    ]
    assert any(
        field["kind"] == "pallet_task_cycle_pending"
        and field["palletCode"] == "BT001"
        for field in response.cards[0].fields
    )
    assert any(
        field["kind"] == "pallet_task_cycle_scope_note"
        and "员工绩效" in field["value"]
        for field in response.cards[0].fields
    )
    assert store.get("agt_llm").last_goal_completion["goalType"] == (
        "PROCESS_EFFICIENCY_TREND"
    )
    assert store.get("agt_llm").last_goal_completion["status"] == "COMPLETE"


def test_registered_report_model_observation_is_compact_but_card_keeps_full_rows() -> None:
    runtime, _ = runtime_for(
        ScriptedLlmModel([], []),
        MockToolClient({}),
        allowed_experts=("analytics_expert",),
    )
    daily_rows = [
        {
            "businessDate": f"2026-07-{index:02d}",
            "materialInputRecordCount": 1,
            "materialInputWeightKg": str(index),
            "stableOutputRecordCount": 1,
            "stableOutputWeightKg": str(index + 100),
        }
        for index in range(1, 21)
    ]
    order_rows = [
        {
            "orderNo": f"PO{index:04d}",
            "completenessLabel": "输入和稳定产出均已登记",
        }
        for index in range(1, 21)
    ]
    report_data = {
        "reportName": "生产领料—登记产出趋势",
        "dateRangeLabel": "2026-07-01 至 2026-07-20",
        "productScopeLabel": "全部产品",
        "productionFlowMetrics": {
            "materialInputRecordCount": 20,
            "materialInputWeightKg": "210",
            "stableOutputRecordCount": 20,
            "stableOutputWeightKg": "2210",
        },
        "productionFlowDailySeries": daily_rows,
        "productionFlowOrderBreakdowns": order_rows,
        "dataQuality": {"partial": False, "notes": []},
        "limitations": ["确认领用时已完成库存扣减，但领用确认时间与产出生产日期不能直接相除。"],
    }

    compact_data = runtime._compact_registered_report_model_data(report_data)
    model_observation = runtime._llm_model_observations(
        [
            {
                "observationId": "obs_1",
                "status": "AVAILABLE",
                "tool": "run_registered_report",
                "data": compact_data,
                "callSignature": "hidden",
            }
        ]
    )[0]

    assert model_observation["data"]["productionFlowDailySeriesTotalCount"] == 20
    assert len(model_observation["data"]["productionFlowDailySeries"]) == 6
    assert model_observation["data"]["productionFlowDailySeries"][0]["businessDate"] == (
        "2026-07-01"
    )
    assert model_observation["data"]["productionFlowDailySeries"][-1]["businessDate"] == (
        "2026-07-20"
    )
    assert model_observation["data"]["productionFlowOrderBreakdownsTotalCount"] == 20
    assert len(model_observation["data"]["productionFlowOrderBreakdowns"]) == 6
    assert model_observation["data"]["productionFlowMetrics"]["materialInputRecordCount"] == 20
    assert "完整受控明细保留在卡片和报表导出中" in (
        model_observation["data"]["modelObservationNote"]
    )
    assert "callSignature" not in model_observation

    sparse_daily_rows = [
        {
            "businessDate": f"2026-{(index // 28) + 1:02d}-{(index % 28) + 1:02d}",
            "materialInputRecordCount": 1 if index == 100 else 0,
            "stableOutputRecordCount": 0,
        }
        for index in range(180)
    ]
    sparse_compact = runtime._compact_registered_report_model_data(
        {
            **report_data,
            "productionFlowDailySeries": sparse_daily_rows,
        }
    )
    assert sparse_compact["productionFlowDailySeriesTotalCount"] == 180
    assert sparse_compact["productionFlowDailySeries"] == [sparse_daily_rows[100]]

    card = runtime._production_input_output_flow_card(report_data)
    assert sum(
        field["kind"] == "production_input_output_flow_daily"
        for field in card.fields
    ) == 20
    assert sum(
        field["kind"] == "production_input_output_flow_order"
        for field in card.fields
    ) == 20


def test_llm_daily_production_report_explains_overlong_range_without_generic_error() -> None:
    model = ScriptedLlmModel(
        [main_delegate("analytics_expert", "DAILY_PRODUCTION_ANALYSIS")],
        [
            call(
                "run_registered_report",
                {
                    "reportDefinitionId": "daily_production_overview_v1",
                    "reportVersion": 1,
                    "startDate": "2026-05-31",
                    "endDate": "2026-07-29",
                },
            )
        ],
    )
    runtime, _ = runtime_for(
        model,
        MockToolClient({}),
        allowed_experts=("analytics_expert",),
    )

    response = runtime.chat(chat_request("最近60天的产量"))

    assert response.error is None
    assert response.cards == []
    assert "单次最多查询 31 天" in response.answer
    assert "不会擅自缩短" in response.answer


def test_llm_quality_assay_trend_uses_registered_report_and_returns_quality_card() -> None:
    model = ScriptedLlmModel(
        [main_delegate("analytics_expert", "QUALITY_ASSAY_TREND_ANALYSIS")],
        [
            call(
                "run_registered_report",
                {
                    "reportDefinitionId": "quality_assay_result_trend_v1",
                    "reportVersion": 1,
                    "startDate": "2026-07-01",
                    "endDate": "2026-07-21",
                    "productQuery": "黄冰糖",
                },
            ),
            final(
                "最近21天共3条化验，其中1条合格、1条不合格，另有1条无标准。",
                "obs_1",
            ),
        ],
    )
    tools = MockToolClient(
        {
            "run_registered_report": {
                "reportRunId": "rr_quality_opaque",
                "reportDefinitionId": "quality_assay_result_trend_v1",
                "reportVersion": 1,
                "reportName": "化验判定趋势",
                "dateRangeLabel": "2026-07-01 至 2026-07-21",
                "filtersApplied": {"productScope": "产品名称包含“黄冰糖”"},
                "dataAsOf": "2026-07-21T12:00:00+08:00",
                "latestRecordAt": "2026-07-17T16:15:00",
                "seriesGranularity": "DAY",
                "qualityMetrics": {
                    "assayRecordCount": 3,
                    "judgedRecordCount": 2,
                    "passCount": 1,
                    "failCount": 1,
                    "noStandardCount": 1,
                    "multipleCandidatesCount": 0,
                    "passRatePercent": 50.0,
                    "distinctProductCount": 1,
                    "distinctStandardVersionCount": 1,
                },
                "qualitySeries": [
                    {
                        "periodLabel": "2026-07-17",
                        "periodStart": "2026-07-17",
                        "periodEnd": "2026-07-17",
                        "assayRecordCount": 3,
                        "judgedRecordCount": 2,
                        "passCount": 1,
                        "failCount": 1,
                        "noStandardCount": 1,
                        "multipleCandidatesCount": 0,
                        "passRatePercent": 50.0,
                    }
                ],
                "qualityProductBreakdowns": [
                    {
                        "productName": "黄冰糖（袋）",
                        "assayRecordCount": 3,
                        "judgedRecordCount": 2,
                        "passCount": 1,
                        "failCount": 1,
                        "noStandardCount": 1,
                        "multipleCandidatesCount": 0,
                        "passRatePercent": 50.0,
                    }
                ],
                "standardBreakdowns": [
                    {
                        "standardLabel": "黄冰糖 v1",
                        "assayRecordCount": 2,
                        "judgedRecordCount": 2,
                        "passCount": 1,
                        "failCount": 1,
                        "passRatePercent": 50.0,
                    }
                ],
                "dataQuality": {
                    "partial": False,
                    "rowsMissingJudgeResult": 0,
                    "rowsMissingStandardVersion": 0,
                    "rowsMissingProductName": 0,
                    "lateRecordedCount": 1,
                    "notes": ["1 条化验记录晚于生产日期录入；趋势仍按生产日期统计。"],
                },
                "limitations": [
                    "合格率固定为合格数除以合格数与不合格数之和。",
                    "本报表只统计已有化验记录。",
                ],
            }
        }
    )
    runtime, store = runtime_for(
        model,
        tools,
        allowed_experts=("analytics_expert",),
    )

    response = runtime.chat(chat_request("最近21天黄冰糖质量趋势如何？"))

    assert response.error is None
    assert [item["toolName"] for item in tools.calls] == ["run_registered_report"]
    assert tools.calls[0]["arguments"]["reportDefinitionId"] == (
        "quality_assay_result_trend_v1"
    )
    assert [card.cardType for card in response.cards] == [
        "quality_assay_trend_report"
    ]
    assert store.get("agt_llm").last_goal_completion["goalType"] == (
        "QUALITY_ASSAY_TREND_ANALYSIS"
    )
    assert store.get("agt_llm").last_goal_completion["status"] == "COMPLETE"


def test_llm_quality_metric_trend_uses_historical_standard_and_returns_metric_card() -> None:
    model = ScriptedLlmModel(
        [main_delegate("analytics_expert", "QUALITY_METRIC_TREND_ANALYSIS")],
        [
            call(
                "run_registered_report",
                {
                    "reportDefinitionId": "quality_metric_trend_v1",
                    "reportVersion": 1,
                    "startDate": "2026-01-23",
                    "endDate": "2026-07-21",
                    "productQuery": "黄冰糖（袋）",
                    "metricKey": "ph",
                },
            ),
            final(
                "最近180天的pH样本均处于标准范围，达标率100%。",
                "obs_1",
            ),
        ],
    )
    tools = MockToolClient(
        {
            "run_registered_report": {
                "reportRunId": "report_run_0123456789abcdef",
                "reportDefinitionId": "quality_metric_trend_v1",
                "reportVersion": 1,
                "reportName": "单项化验指标趋势",
                "dateRangeLabel": "2026-01-23 至 2026-07-21",
                "filtersApplied": {"productScope": "产品名称包含“黄冰糖（袋）”"},
                "dataAsOf": "2026-07-21T12:00:00+08:00",
                "latestRecordAt": "2026-07-17T16:15:00",
                "seriesGranularity": "MONTH",
                "metricTrendSummary": {
                    "metricKey": "ph",
                    "metricName": "pH",
                    "unit": "",
                    "assayRecordCount": 3,
                    "sampleCount": 3,
                    "missingValueCount": 0,
                    "comparableStandardCount": 1,
                    "withinStandardCount": 1,
                    "outOfStandardCount": 0,
                    "withoutComparableStandardCount": 2,
                    "withinStandardRatePercent": 100.0,
                    "averageValue": 5.067,
                    "medianValue": 7.0,
                    "minimumValue": 1.0,
                    "maximumValue": 7.2,
                    "p10Value": 2.2,
                    "p90Value": 7.16,
                },
                "metricSeries": [
                    {
                        "periodLabel": "2026-04",
                        "periodStart": "2026-04-01",
                        "periodEnd": "2026-04-30",
                        "sampleCount": 1,
                        "averageValue": 1.0,
                        "medianValue": 1.0,
                        "minimumValue": 1.0,
                        "maximumValue": 1.0,
                    },
                    {
                        "periodLabel": "2026-07",
                        "periodStart": "2026-07-01",
                        "periodEnd": "2026-07-31",
                        "sampleCount": 2,
                        "averageValue": 7.1,
                        "medianValue": 7.1,
                        "minimumValue": 7.0,
                        "maximumValue": 7.2,
                    },
                ],
                "metricProductBreakdowns": [
                    {
                        "productName": "黄冰糖（袋）",
                        "sampleCount": 3,
                        "averageValue": 5.067,
                        "medianValue": 7.0,
                        "minimumValue": 1.0,
                        "maximumValue": 7.2,
                    }
                ],
                "metricStandardBreakdowns": [
                    {
                        "standardLabel": "黄冰糖 v1",
                        "rangeLabel": "6 - 9",
                        "unit": "",
                        "sampleCount": 1,
                        "withinStandardCount": 1,
                        "outOfStandardCount": 0,
                        "withinStandardRatePercent": 100.0,
                    }
                ],
                "dataQuality": {
                    "partial": False,
                    "rowsMissingMetricValue": 0,
                    "rowsWithoutComparableMetricStandard": 2,
                    "rowsWithUnexpectedMetricUnit": 0,
                    "notes": ["2 个实测样本没有可比较的历史指标标准。"],
                },
                "limitations": ["样本较少时不输出趋势方向。"],
            }
        }
    )
    runtime, store = runtime_for(
        model,
        tools,
        allowed_experts=("analytics_expert",),
    )

    response = runtime.chat(chat_request("最近180天黄冰糖（袋）的pH趋势如何？"))

    assert response.error is None
    assert tools.calls[0]["arguments"]["metricKey"] == "ph"
    assert [card.cardType for card in response.cards] == [
        "quality_metric_trend_report"
    ]
    assert response.cards[0].fields[0]["reportRunId"] == "report_run_0123456789abcdef"
    assert response.cards[0].fields[0]["unit"] == ""
    assert "2 个样本无可比较的历史标准" in response.answer
    assert "不参与达标率计算" in response.answer
    assert [field["label"] for field in response.cards[0].fields if field["kind"] == "quality_metric_trend_point"] == [
        "2026-04",
        "2026-07",
    ]
    assert store.get("agt_llm").last_goal_completion["goalType"] == (
        "QUALITY_METRIC_TREND_ANALYSIS"
    )
    assert store.get("agt_llm").last_goal_completion["status"] == "COMPLETE"


def test_llm_boiling_batch_range_uses_main_and_production_expert_before_tool() -> None:
    model = ScriptedLlmModel(
        [main_delegate("production_expert", "BOILING_BATCH_LIST")],
        [
            call(
                "query_boiling_batches",
                {
                    "productQuery": "ALL",
                    "startDate": "2026-06-21",
                    "endDate": "2026-07-21",
                    "status": "AVAILABLE",
                    "limit": 10,
                },
            )
        ],
    )
    tools = MockToolClient(
        {
            "query_boiling_batches": {
                "dataScope": "BOILING_BATCH_LIST",
                "scopeLabel": "全部产品",
                "dateRangeLabel": "2026-06-21 至 2026-07-21",
                "total": 2,
                "candidates": [
                    {
                        "entityRef": "aer_batch_1",
                        "entityType": "BOILING_BATCH",
                        "displayCode": "20260630-01",
                        "summary": "白冰糖；煮糖1组；1983.8 kg",
                    },
                    {
                        "entityRef": "aer_batch_2",
                        "entityType": "BOILING_BATCH",
                        "displayCode": "ZT202606300001",
                        "summary": "1980 kg",
                    },
                ],
            }
        }
    )
    runtime, store = runtime_for(
        model,
        tools,
        allowed_experts=("production_expert",),
    )

    response = runtime.chat(chat_request("查最近一个月的煮糖批次"))

    assert response.error is None
    assert response.needsUserSelection is True
    assert "共查询到 2 个全部产品煮糖批次" in response.answer
    assert len(model.main_requests) == 1
    assert len(model.expert_requests) == 1
    assert model.expert_requests[0].expertAgent == "production_expert"
    assert "query_boiling_batches" in model.expert_requests[0].toolSchemas
    assert [item["toolName"] for item in tools.calls] == ["query_boiling_batches"]
    assert tools.calls[0]["expertAgent"] == "production_expert"
    assert tools.calls[0]["arguments"] == {
        "startDate": "2026-06-22",
        "endDate": "2026-07-21",
        "limit": 10,
    }
    assert response.reviewTrace["planningMode"] == "llm"
    assert response.reviewTrace["mainDecision"]["expertAgent"] == "production_expert"
    assert response.reviewTrace["mainDecision"]["goalType"] == "BOILING_BATCH_LIST"
    assert response.reviewTrace["expertLoop"]["events"][0]["action"] == "CALL_TOOL"
    assert store.get("agt_llm").last_goal_completion["status"] == "COMPLETE"


def test_llm_boiling_batch_scope_keeps_explicit_status_filter() -> None:
    arguments = ToolArgumentBuilder(business_clock=fixed_test_clock()).validate_llm_arguments(
        tool_name="query_boiling_batches",
        arguments={
            "productQuery": "全部产品",
            "startDate": "2026-06-22",
            "endDate": "2026-07-21",
            "status": "USED_UP",
            "limit": 10,
        },
        state=InMemoryCheckpointer().get("agt_batch_status"),
        user_message="查询最近30天已用完的全部产品煮糖批次",
    )

    assert arguments == {
        "startDate": "2026-06-22",
        "endDate": "2026-07-21",
        "status": "USED_UP",
        "limit": 10,
    }


def test_main_goal_binding_distinguishes_output_destination_from_order_progress() -> None:
    model = ScriptedLlmModel(
        [main_delegate("production_expert", "PRODUCTION_OUTPUT_DESTINATIONS")],
        [
            call(
                "resolve_production_entities",
                {"entityType": "PRODUCTION_ORDER", "query": "PO202606300001", "limit": 10},
            ),
            call("query_production_order_progress", {"orderRef": "CURRENT_PRODUCTION_ORDER"}),
        ],
    )
    tools = MockToolClient(
        {
            "resolve_production_entities": {
                "resolutionStatus": "EXACT",
                "needsUserSelection": False,
                "entityType": "PRODUCTION_ORDER",
                "candidates": [
                    {
                        "entityRef": "aer_order_1",
                        "entityType": "PRODUCTION_ORDER",
                        "displayCode": "PO202606300001",
                    }
                ],
            },
            "query_production_order_progress": {
                "orderNo": "PO202606300001",
                "orderType": "FINISHED_PRODUCT",
                "status": "COMPLETED",
                "materialRecordCount": 1,
                "outputRecordCount": 1,
                "inboundQrCount": 1,
                "outputs": [
                    {
                        "productName": "黄冰糖（袋）",
                        "quantityText": "1板40件",
                        "status": "COMPLETED",
                        "requiredQrCount": 1,
                        "boundQrCount": 1,
                        "inboundQrCount": 1,
                        "inboundDestinations": [
                            {
                                "warehouseName": "1号库位",
                                "inboundCodeCount": 1,
                                "palletCodes": ["BT0001"],
                            }
                        ],
                    }
                ],
            },
        }
    )
    runtime, store = runtime_for(
        model,
        tools,
        allowed_experts=("production_expert",),
    )

    response = runtime.chat(chat_request("生产订单PO202606300001的产出入库去向"))
    state = store.get("agt_llm")

    assert response.error is None
    assert state.active_goal_type == "PRODUCTION_OUTPUT_DESTINATIONS"
    assert state.last_goal_completion["status"] == "COMPLETE"
    assert state.fact_envelopes[-1]["factType"] == "PRODUCTION_OUTPUT_DESTINATIONS"
    assert "1号库位" in json.dumps(response.model_dump(), ensure_ascii=False)


def test_main_goal_owner_mismatch_gets_one_bounded_route_correction() -> None:
    model = ScriptedLlmModel(
        [
            main_delegate("warehouse_expert", "SCREEN_MESH_CATALOG"),
            main_delegate("master_data_expert", "SCREEN_MESH_CATALOG"),
        ],
        [
            call("query_screen_mesh_catalog", {"page": 1, "size": 50}),
            final("当前共有 8 个筛网规格。", "obs_1"),
        ],
    )
    tools = MockToolClient(
        {
            "query_screen_mesh_catalog": {
                "dataScope": "SCREEN_MESH_CATALOG",
                "total": 8,
                "records": [{"mesh": "4-15"}],
            }
        }
    )
    runtime, _ = runtime_for(
        model,
        tools,
        allowed_experts=("warehouse_expert", "master_data_expert"),
    )

    response = runtime.chat(chat_request("查询当前筛网目录"))

    assert response.error is None
    assert response.answer == "当前共有 8 个筛网规格。"
    assert len(model.main_requests) == 2
    correction = model.main_requests[1].selectedContext["RUNTIME_ROUTE_CORRECTION"]
    assert correction["reason"] == "GOAL_OWNER_MISMATCH"
    assert correction["expectedExpertAgent"] == "master_data_expert"
    assert response.reviewTrace["mainDecisionCorrection"]["expertAgent"] == "master_data_expert"
    assert tools.calls[0]["expertAgent"] == "master_data_expert"


def test_llm_boiling_batch_scope_uses_explicit_all_products_over_model_filter() -> None:
    arguments = ToolArgumentBuilder(business_clock=fixed_test_clock()).validate_llm_arguments(
        tool_name="query_boiling_batches",
        arguments={
            "productQuery": "白冰糖",
            "startDate": "2026-06-22",
            "endDate": "2026-07-21",
            "limit": 10,
        },
        state=InMemoryCheckpointer().get("agt_batch_all_products"),
        user_message="查询最近30天全部产品的煮糖批次",
    )

    assert arguments == {
        "startDate": "2026-06-22",
        "endDate": "2026-07-21",
        "limit": 10,
    }


def test_priority_warehouse_goal_uses_model_tool_loop_and_runtime_completion() -> None:
    model = ScriptedLlmModel(
        [main_delegate("warehouse_expert", "WAREHOUSE_CAPACITY_DISTRIBUTION")],
        [
            call(
                "query_warehouse_capacity_distribution",
                {
                    "warehouseScope": {"type": "ALL"},
                    "onlyAvailable": True,
                    "page": 1,
                    "size": 20,
                },
            ),
            final("当前有 1 个仍有可用容量的库位。", "obs_1"),
        ],
    )
    tools = MockToolClient(
        {
            "query_warehouse_capacity_distribution": {
                "dataScope": "CURRENT_WAREHOUSE_CAPACITY_FACTS",
                "total": 1,
                "page": 1,
                "size": 20,
                "summary": {
                    "currentCapacity": 9,
                    "maximumCapacity": 10,
                    "remainingCapacity": 1,
                },
                "records": [
                    {
                        "warehouseName": "2号库位",
                        "currentCapacity": 9,
                        "maximumCapacity": 10,
                        "remainingCapacity": 1,
                    }
                ],
            }
        }
    )
    runtime, store = runtime_for(model, tools)

    response = runtime.chat(chat_request("哪些库位目前还有可用容量"))
    state = store.get("agt_llm")

    assert response.error is None
    assert tools.calls[0]["expertAgent"] == "warehouse_expert"
    assert state.active_goal_type == "WAREHOUSE_CAPACITY_DISTRIBUTION"
    assert state.fact_envelopes[-1]["factType"] == "WAREHOUSE_CAPACITY_DISTRIBUTION"
    assert response.reviewTrace["goalCompletion"]["status"] == "COMPLETE"


def test_llm_boiling_batch_selection_rejects_list_rediscovery_and_continues_to_trace() -> None:
    model = ScriptedLlmModel(
        [main_delegate("production_expert")],
        [
            call(
                "query_boiling_batches",
                {"startDate": "2026-06-22", "endDate": "2026-07-21", "limit": 10},
            ),
            call(
                "query_boiling_batches",
                {"startDate": "2026-06-22", "endDate": "2026-07-21", "limit": 10},
            ),
            call("query_boiling_batch_trace", {"batchRef": "CURRENT_BOILING_BATCH"}),
        ],
    )
    tools = MockToolClient(
        {
            "query_boiling_batches": {
                "dataScope": "BOILING_BATCH_LIST",
                "scopeLabel": "全部产品",
                "dateRangeLabel": "2026-06-22 至 2026-07-21",
                "total": 1,
                "candidates": [
                    {
                        "entityRef": "aer_batch_1",
                        "entityType": "BOILING_BATCH",
                        "displayCode": "20260630-01",
                        "summary": "白冰糖；煮糖1组；1983.8 kg",
                    }
                ],
            },
            "query_boiling_batch_trace": {
                "dataScope": "REGISTERED_BOILING_BATCH_TRACE",
                "batchNo": "20260630-01",
                "status": "USED_UP",
                "productName": "白冰糖",
                "totalWeightKg": 1983.8,
                "consumedWeightKg": 1656.8,
                "remainingWeightKg": 0,
                "usages": [
                    {
                        "orderNo": "PO202606300002",
                        "orderStatus": "COMPLETED",
                        "weightKg": 1656.8,
                        "status": "CONSUMED",
                    }
                ],
            },
        }
    )
    runtime, store = runtime_for(
        model,
        tools,
        allowed_experts=("production_expert",),
    )

    interrupted = runtime.chat(chat_request("查最近一个月的煮糖批次"))
    pending = store.get("agt_llm").pending_clarification
    assert interrupted.needsUserSelection is True
    assert pending is not None

    response = runtime.resume(
        ResumeRequest.model_validate(
            {
                "agentSessionId": "agt_llm",
                "messageId": "msg_002",
                "resumeToken": pending.resume_token,
                "event": {
                    "type": "candidate_selected",
                    "interruptId": pending.interrupt_id,
                    "action": "SELECT_OPTION",
                    "selection": {"optionId": pending.options[0]["optionId"]},
                    "clientRequestId": "resume_batch_001",
                },
                "client": {"traceId": "trace_002", "requestId": "req_002", "debug": True},
            }
        )
    )

    assert response.error is None
    assert response.needsUserSelection is False
    assert "煮糖批次 20260630-01" in response.answer
    assert "缺失的上下游关系不会由 Agent 推断" not in response.answer
    assert [item["toolName"] for item in tools.calls] == [
        "query_boiling_batches",
        "query_boiling_batch_trace",
    ]
    assert tools.calls[1]["arguments"] == {"batchRef": "aer_batch_1"}
    assert "aer_batch_1" not in repr(model.expert_requests)
    assert "CURRENT_BOILING_BATCH" in repr(model.expert_requests)
    assert "已从候选列表选择煮糖批次 20260630-01" in model.expert_requests[-1].userMessage
    assert "不要再次询问用户要查看什么" in model.expert_requests[-1].userMessage
    assert any(
        event.get("status") == "SELECTION_ALREADY_COMPLETED"
        for event in response.reviewTrace["expertLoop"]["events"]
    )


def test_llm_production_expert_requires_selection_for_multiple_related_orders() -> None:
    model = ScriptedLlmModel(
        [main_delegate("production_expert")],
        [
            call("query_production_order_progress", {"orderRef": "CURRENT_PRODUCTION_ORDER"})
        ],
    )

    def resolve_order(arguments: dict[str, Any]) -> dict[str, Any]:
        order_no = str(arguments["query"])
        return {
            "resolutionStatus": "EXACT",
            "needsUserSelection": False,
            "entityType": "PRODUCTION_ORDER",
            "candidates": [
                {
                    "entityRef": f"aer_{order_no}",
                    "entityType": "PRODUCTION_ORDER",
                    "displayCode": order_no,
                    "summary": "SEMI；-" if order_no.endswith("001") else "SEMI；黄中冰 1板16件",
                }
            ],
        }

    tools = MockToolClient({"resolve_production_entities": resolve_order})
    runtime, store = runtime_for(
        model,
        tools,
        allowed_experts=("production_expert",),
    )
    store.get("agt_llm").last_boiling_batch_trace = {
        "batchNo": "20260630-01",
        "usages": [
            {"orderNo": "PO202606300002"},
            {"orderNo": "PO202606300001"},
        ],
    }

    response = runtime.chat(chat_request("原料消耗及产出"))

    assert response.error is None
    assert response.needsUserSelection is True
    assert response.cards[0].cardType == "candidate_selection"
    assert [option.displayLabel for option in response.cards[0].options] == [
        "PO202606300002",
        "PO202606300001",
    ]
    assert all(option.optionType == "PRODUCTION_ORDER" for option in response.cards[0].options)
    assert [option.description for option in response.cards[0].options] == [
        "半成品生产；黄中冰 1板16件",
        "半成品生产；暂无产出摘要",
    ]
    assert [item["toolName"] for item in tools.calls] == [
        "resolve_production_entities",
        "resolve_production_entities",
    ]


def test_llm_production_expert_returns_all_linked_orders_when_user_explicitly_requests_both() -> None:
    model = ScriptedLlmModel(
        [main_delegate("production_expert"), main_delegate("production_expert")],
        [
            call("query_production_order_progress", {"orderRef": "CURRENT_PRODUCTION_ORDER"}),
            call("query_production_order_progress", {"orderRef": "CURRENT_PRODUCTION_ORDER"}),
        ],
    )

    def resolve_order(arguments: dict[str, Any]) -> dict[str, Any]:
        order_no = str(arguments["query"])
        return {
            "resolutionStatus": "EXACT",
            "needsUserSelection": False,
            "entityType": "PRODUCTION_ORDER",
            "candidates": [
                {
                    "entityRef": f"aer_{order_no}",
                    "entityType": "PRODUCTION_ORDER",
                    "displayCode": order_no,
                    "summary": "SEMI；暂无产出摘要",
                }
            ],
        }

    def order_progress(arguments: dict[str, Any]) -> dict[str, Any]:
        order_no = str(arguments["orderRef"]).removeprefix("aer_")
        return {
            "orderNo": order_no,
            "orderType": "SEMI",
            "status": "PREPRINTED" if order_no.endswith("1") else "COMPLETED",
            "materialRecordCount": 0,
            "outputRecordCount": 0,
            "boilingSources": [
                {
                    "batchNo": "20260630-01",
                    "usageUnit": "KG",
                    "usageQuantity": 327 if order_no.endswith("1") else 1656.8,
                    "weightKg": 327 if order_no.endswith("1") else 1656.8,
                    "status": "RESERVED" if order_no.endswith("1") else "CONSUMED",
                }
            ],
            "outputs": [],
        }

    tools = MockToolClient(
        {
            "resolve_production_entities": resolve_order,
            "query_production_order_progress": order_progress,
        }
    )
    runtime, store = runtime_for(
        model,
        tools,
        allowed_experts=("production_expert",),
    )
    store.get("agt_llm").last_boiling_batch_trace = {
        "batchNo": "20260630-01",
        "usages": [
            {"orderNo": "PO202606300002"},
            {"orderNo": "PO202606300001"},
        ],
    }

    response = runtime.chat(chat_request("这两个关联生产订单的具体情况都给我看看"))

    assert response.error is None
    assert response.needsUserSelection is False
    assert [card.cardType for card in response.cards] == [
        "production_order_progress",
        "production_order_progress",
    ]
    assert [item["toolName"] for item in tools.calls] == [
        "resolve_production_entities",
        "query_production_order_progress",
        "resolve_production_entities",
        "query_production_order_progress",
    ]
    serialized = json.dumps(response.model_dump(exclude_none=True), ensure_ascii=False)
    assert "PREPRINTED" not in serialized
    assert "RESERVED" not in serialized
    assert response.reviewTrace["expertLoop"]["events"][-1]["status"] == (
        "ALL_LINKED_PRODUCTION_ORDERS"
    )

    state = store.get("agt_llm")
    state.selected_production_order = SelectedEntity(
        None,
        "PO202606300002",
        "tool_result",
        {"orderRef": "aer_PO202606300002"},
        entity_type="PRODUCTION_ORDER",
        entity_ref="CURRENT_PRODUCTION_ORDER",
        canonical_name="PO202606300002",
    )
    second = runtime.chat(chat_request("只看第二个订单"))

    assert second.error is None
    assert second.needsUserSelection is False
    assert second.cards[0].title == "生产订单 PO202606300001"
    assert tools.calls[-2]["arguments"]["query"] == "PO202606300001"
    assert tools.calls[-1]["arguments"]["orderRef"] == "aer_PO202606300001"


def test_llm_answer_translates_production_fields_and_enums_at_display_boundary() -> None:
    runtime, _ = runtime_for(
        ScriptedLlmModel([], []),
        MockToolClient(),
        allowed_experts=("production_expert",),
    )

    answer = runtime._sanitize_llm_answer(
        "orderStatus=PREPRINTED，materialRecordCount=0；煮糖批次状态为USED_UP，使用状态为RESERVED。"
    )

    assert "orderStatus" not in answer
    assert "materialRecordCount" not in answer
    assert "PREPRINTED" not in answer
    assert "USED_UP" not in answer
    assert "RESERVED" not in answer
    assert "生产订单状态=已预打印" in answer
    assert "实际领料记录数=0" in answer
    assert "煮糖批次状态为已用完" in answer
    assert runtime._production_date_scope_text("2026-06-30 至 2026-06-30") == "2026-06-30 当天"

    formatted = runtime._sanitize_llm_answer(
        "### 生产订单情况\n- **原料消耗**：暂无。\n- **产出**：`1板16件`。"
    )
    assert formatted == "生产订单情况\n• 原料消耗：暂无。\n• 产出：1板16件。"


def test_llm_answer_hides_internal_observation_citations() -> None:
    runtime, _ = runtime_for(ScriptedLlmModel([], []), MockToolClient())

    answer = runtime._sanitize_llm_answer(
        "管理员角色共有 12 项权限（数据来源：obs_1）。详情依据 obs_2、obs_3。"
    )

    assert "obs_" not in answer
    assert "数据来源" not in answer
    assert "管理员角色共有 12 项权限" in answer


def test_llm_assay_pilot_keeps_report_ref_runtime_controlled_across_three_turns() -> None:
    report_ref = "assay_report_testref"
    model = ScriptedLlmModel(
        [main_delegate("assay_expert")],
        [
            call("resolve_products", {"query": "黄冰糖（袋）", "limit": 10}),
            call(
                "get_assay_status",
                {"productRef": "CURRENT_PRODUCT", "productionDate": "2026-07-09"},
            ),
            call(
                "query_assay_records",
                {
                    "productScope": {
                        "type": "SINGLE_PRODUCT",
                        "productRef": "CURRENT_PRODUCT",
                    },
                    "dateRange": {"type": "LAST_DAYS", "days": 30},
                    "judgeStatus": "ANY",
                    "sortBy": "sampleDate",
                    "sortDirection": "DESC",
                    "page": 1,
                    "size": 20,
                },
            ),
            call(
                "get_assay_report_detail",
                {
                    "reportRef": "CURRENT_ASSAY_REPORT",
                    "includeMetrics": True,
                    "includeStandardSnapshot": True,
                },
            ),
        ],
    )
    tools = MockToolClient(
        {
            "resolve_products": {
                "resolutionStatus": "UNIQUE",
                "candidates": [
                    {
                        "productId": 84,
                        "productName": "黄冰糖（袋）",
                        "displayLabel": "黄冰糖（袋） 25kg/件 40件/板",
                    }
                ],
            },
            "get_assay_status": {
                "judgeResult": "NO_STANDARD",
                "assay": {
                    "productName": "黄冰糖（袋）",
                    "sampleDate": "2026-07-09",
                    "judgeResult": "NO_STANDARD",
                    "colorValue": 12,
                    "reducingSugar": 3,
                    "dryWeight": 4,
                    "conductivityAsh": 5,
                    "sucrose": 75,
                    "insolubleImpurity": 12,
                    "phValue": 7,
                },
            },
            "query_assay_records": {
                "scopeLabel": "黄冰糖（袋）",
                "dateRangeLabel": "最近30天",
                "total": 1,
                "records": [
                    {
                        "recordRef": report_ref,
                        "productLabel": "黄冰糖（袋）",
                        "sampleDate": "2026-07-09",
                        "judgeLabel": "不合格",
                    }
                ],
            },
            "get_assay_report_detail": {
                "reportRef": report_ref,
                "productLabel": "黄冰糖（袋）",
                "sampleDate": "2026-07-09",
                "judgeLabel": "不合格",
                "metrics": [
                    {
                        "metricName": "色值",
                        "actualValueText": "120",
                        "standardRangeText": "不大于100",
                        "resultLabel": "不合格",
                    }
                ],
            },
        }
    )
    runtime, store = runtime_for(model, tools)

    first = runtime.chat(chat_request("查询黄冰糖（袋）2026-07-09的化验情况"))
    second = runtime.chat(chat_request("再看最近30天历史化验"))
    third = runtime.chat(chat_request("再展开第一条具体指标"))

    assert first.error is None
    assert second.error is None
    assert third.error is None
    assert len(model.main_requests) == 1
    assert [item["toolName"] for item in tools.calls] == [
        "resolve_products",
        "get_assay_status",
        "query_assay_records",
        "get_assay_report_detail",
    ]
    assert tools.calls[-1]["arguments"]["reportRef"] == report_ref
    assert store.get("agt_llm").last_assay_records["records"][0]["recordRef"] == report_ref
    model_context = repr(model.expert_requests)
    assert "CURRENT_ASSAY_REPORT" in model_context
    assert report_ref not in model_context
    assert report_ref not in third.answer
    assert "NO_STANDARD" not in first.answer
    assert first.cards[0].cardType == "assay_report"
    assert second.cards[0].cardType == "assay_history"
    assert third.cards[0].cardType == "assay_report"


def test_llm_pallet_pilot_reuses_current_pallet_without_guessing_code() -> None:
    pallet_code = "P202607090001"
    model = ScriptedLlmModel(
        [main_delegate("pallet_expert")],
        [
            call("get_pallet_status", {"code": pallet_code}),
            final("该托盘当前在库。", "obs_1"),
            call(
                "query_pallet_flow_records",
                {"palletRef": "CURRENT_PALLET", "page": 1, "size": 20},
            ),
            final("该托盘最近一次登记流转为入库。", "obs_1"),
        ],
    )
    tools = MockToolClient(
        {
            "get_pallet_status": {
                "palletInfo": {
                    "code": pallet_code,
                    "status": "INSTOCK",
                    "productName": "黄中冰",
                    "productionDate": "2026-07-09",
                },
                "inventory": {"warehouseName": "20号库位", "quantity": 1, "unit": False},
            },
            "query_pallet_flow_records": {
                "scopeLabel": f"托盘 {pallet_code}",
                "total": 1,
                "records": [
                    {
                        "time": "2026-07-09 10:00:00",
                        "eventLabel": "入库",
                        "codeLabel": pallet_code,
                        "cycleNo": 4,
                    }
                ],
            },
        }
    )
    runtime, store = runtime_for(
        model,
        tools,
        allowed_experts=(
            "inventory_expert",
            "warehouse_expert",
            "assay_expert",
            "pallet_expert",
        ),
    )

    first = runtime.chat(chat_request(f"查询托盘 {pallet_code} 现状"))
    second = runtime.chat(chat_request("再看它的历史流转"))

    assert first.error is None
    assert second.error is None
    assert len(model.main_requests) == 1
    assert [item["toolName"] for item in tools.calls] == [
        "get_pallet_status",
        "query_pallet_flow_records",
    ]
    assert tools.calls[1]["arguments"]["code"] == pallet_code
    assert store.get("agt_llm").selected_pallet is not None
    assert store.get("agt_llm").selected_pallet.metadata["code"] == pallet_code
    assert first.cards[0].cardType == "pallet_status"
    assert first.cards[0].fields[0]["currentStatusLabel"] == "在库"
    assert first.cards[0].fields[0]["warehouseLabel"] == "20号库位"
    assert second.cards[0].cardType == "pallet_flow_history"
    observed_record = model.expert_requests[-1].observations[-1]["data"]["records"][0]
    assert "cycleNo" not in observed_record
    assert observed_record["flowSequenceLabel"] == "第 4 次流转"
    assert store.get("agt_llm").last_goal_completion["goalType"] == "PALLET_FLOW_HISTORY"
    assert store.get("agt_llm").last_goal_completion["status"] == "COMPLETE"


def test_llm_pallet_recent_no_data_can_use_lifecycle_as_history_evidence() -> None:
    pallet_code = "BT000YGI"
    model = ScriptedLlmModel(
        [main_delegate("pallet_expert")],
        [
            call("get_pallet_status", {"code": pallet_code}),
            final("该托盘当前为空闲。", "obs_1"),
            call(
                "query_pallet_flow_records",
                {
                    "palletRef": "CURRENT_PALLET",
                    "dateRange": {"type": "LAST_DAYS", "days": 7},
                    "page": 1,
                    "size": 20,
                },
            ),
            call(
                "query_qr_code_lifecycle",
                {"palletRef": "CURRENT_PALLET", "includeFlows": True, "flowLimit": 100},
            ),
            final("最近7天没有新流转；最近一次已登记事件是生产订单领用。", "obs_1", "obs_2"),
        ],
    )
    tools = MockToolClient(
        {
            "get_pallet_status": {
                "palletInfo": {"code": pallet_code, "status": "FREE", "productName": "黄中冰"},
            },
            "query_pallet_flow_records": {
                "scopeLabel": f"托盘 {pallet_code}",
                "dateRangeLabel": "最近7天",
                "total": 0,
                "summaryText": "最近7天没有已登记流转记录。",
                "records": [],
            },
            "query_qr_code_lifecycle": {
                "palletInfo": {"code": pallet_code, "status": "FREE", "productName": "黄中冰"},
                "timeline": [
                    {
                        "time": "2026-06-30 15:32:29",
                        "eventLabel": "生产订单领用",
                        "productLabel": "黄中冰",
                    }
                ],
            },
        }
    )
    runtime, store = runtime_for(
        model,
        tools,
        allowed_experts=(
            "inventory_expert",
            "warehouse_expert",
            "assay_expert",
            "pallet_expert",
        ),
    )

    first = runtime.chat(chat_request(f"查询托盘 {pallet_code} 现状"))
    second = runtime.chat(chat_request("它最近怎么流转的"))

    assert first.error is None
    assert second.error is None
    assert second.answer == "最近7天没有新流转；最近一次已登记事件是生产订单领用。"
    assert [item["toolName"] for item in tools.calls] == [
        "get_pallet_status",
        "query_pallet_flow_records",
        "query_qr_code_lifecycle",
    ]
    assert [card.cardType for card in second.cards] == ["pallet_flow_history"]
    assert second.cards[0].fields[0]["dateRangeLabel"] == "完整历史"
    assert store.get("agt_llm").last_goal_completion["goalType"] == "PALLET_FLOW_HISTORY"
    assert store.get("agt_llm").last_goal_completion["status"] == "COMPLETE"


def test_llm_direct_pallet_history_query_registers_the_explicit_pallet() -> None:
    pallet_code = "BT000YGI"
    model = ScriptedLlmModel(
        [main_delegate("pallet_expert")],
        [
            call(
                "query_qr_code_lifecycle",
                {"code": pallet_code, "includeFlows": True, "flowLimit": 50},
            ),
            call(
                "query_pallet_flow_records",
                {
                    "code": pallet_code,
                    "dateRange": {
                        "type": "RANGE",
                        "from": "2020-01-01",
                        "to": "2026-07-21",
                    },
                    "page": 1,
                    "size": 20,
                },
            ),
            final(
                "该托盘自2020-01-01至2026-07-21共有 1 条已登记历史流转。",
                "obs_2",
            ),
        ],
    )
    tools = MockToolClient(
        {
            "query_pallet_flow_records": {
                "scopeLabel": f"托盘 {pallet_code}",
                "dateRangeLabel": "2020-01-01至2026-07-21",
                "total": 1,
                "records": [
                    {
                        "time": "2026-06-30 15:32:29",
                        "eventLabel": "生产订单领用",
                        "codeLabel": pallet_code,
                    }
                ],
            }
        }
    )
    runtime, store = runtime_for(
        model,
        tools,
        allowed_experts=(
            "inventory_expert",
            "warehouse_expert",
            "assay_expert",
            "pallet_expert",
        ),
    )

    response = runtime.chat(chat_request(f"查询托盘 {pallet_code} 完整历史流转"))

    assert response.error is None
    assert response.answer == "该托盘完整历史范围内共有 1 条已登记历史流转。"
    assert response.cards[0].cardType == "pallet_flow_history"
    assert response.cards[0].fields[0]["dateRangeLabel"] == "完整历史"
    assert [item["toolName"] for item in tools.calls] == ["query_pallet_flow_records"]
    assert tools.calls[0]["arguments"]["dateRange"] == {
        "type": "RANGE",
        "from": "2000-01-01",
        "to": "2026-07-21",
    }
    assert any(
        event.get("status") == "INTENT_TOOL_MISMATCH"
        for event in response.reviewTrace["expertLoop"]["events"]
    )
    assert store.get("agt_llm").selected_pallet is not None
    assert store.get("agt_llm").selected_pallet.metadata["code"] == pallet_code
    assert store.get("agt_llm").last_goal_completion["status"] == "COMPLETE"


def test_llm_pallet_not_found_is_a_user_facing_no_data_answer() -> None:
    pallet_code = "BT999999"
    model = ScriptedLlmModel(
        [main_delegate("pallet_expert")],
        [
            call("get_pallet_status", {"code": pallet_code}),
            final(f"未找到托盘 {pallet_code}，请核对托盘码后重试。", "obs_1"),
        ],
    )
    tools = MockToolClient(
        {
            "get_pallet_status": ToolGatewayError(
                "UPSTREAM_NOT_FOUND",
                "not found",
                retryable=False,
            )
        }
    )
    runtime, _ = runtime_for(
        model,
        tools,
        allowed_experts=(
            "inventory_expert",
            "warehouse_expert",
            "assay_expert",
            "pallet_expert",
        ),
    )

    response = runtime.chat(chat_request(f"查询托盘 {pallet_code} 现状"))

    assert response.error is None
    assert response.answer == f"未找到托盘 {pallet_code}，请核对托盘码后重试。"
    observation = model.expert_requests[-1].observations[-1]
    assert observation["status"] == "NO_DATA"
    assert observation["message"] == "未找到符合条件的业务数据。"


def test_llm_invalid_pallet_code_asks_user_to_check_input() -> None:
    pallet_code = "BT999999"
    model = ScriptedLlmModel(
        [main_delegate("pallet_expert")],
        [
            call("get_pallet_status", {"code": pallet_code}),
            final(f"托盘码 {pallet_code} 的格式或校验位不正确，请核对后重试。", "obs_1"),
        ],
    )
    tools = MockToolClient(
        {
            "get_pallet_status": ToolGatewayError(
                "UPSTREAM_BAD_REQUEST",
                "invalid pallet code",
                retryable=False,
            )
        }
    )
    runtime, _ = runtime_for(
        model,
        tools,
        allowed_experts=(
            "inventory_expert",
            "warehouse_expert",
            "assay_expert",
            "pallet_expert",
        ),
    )

    response = runtime.chat(chat_request(f"查询托盘 {pallet_code} 现状"))

    assert response.error is None
    assert response.answer == f"托盘码 {pallet_code} 的格式或校验位不正确，请核对后重试。"
    observation = model.expert_requests[-1].observations[-1]
    assert observation["status"] == "INVALID_INPUT"
    assert observation["message"] == "查询条件不符合当前业务要求，请核对后重试。"


def test_llm_does_not_repeat_the_same_invalid_pallet_tool_call() -> None:
    pallet_code = "BT999999"
    model = ScriptedLlmModel(
        [main_delegate("pallet_expert")],
        [
            call("get_pallet_status", {"code": pallet_code}),
            call("get_pallet_status", {"code": pallet_code}),
        ],
    )
    tools = MockToolClient(
        {
            "get_pallet_status": ToolGatewayError(
                "UPSTREAM_BAD_REQUEST",
                "invalid pallet code",
                retryable=False,
            )
        }
    )
    runtime, _ = runtime_for(
        model,
        tools,
        allowed_experts=(
            "inventory_expert",
            "warehouse_expert",
            "assay_expert",
            "pallet_expert",
        ),
    )

    response = runtime.chat(chat_request(f"查询托盘 {pallet_code} 现状"))

    assert response.error is None
    assert response.needsUserSelection is True
    assert response.answer == "托盘码不符合系统规则，请核对完整托盘码后重试。"
    assert len(tools.calls) == 1


def test_llm_does_not_repeat_the_same_successful_audit_query() -> None:
    arguments = {
        "reviewStatus": "OPEN",
        "answerStatus": "LOW_CONFIDENCE",
        "failureDomain": "PLANNER",
        "page": 1,
        "size": 10,
    }
    model = ScriptedLlmModel(
        [main_delegate("audit_expert", "AGENT_ANSWER_REVIEWS")],
        [
            call("query_agent_answer_reviews", arguments),
            call("query_agent_answer_reviews", arguments),
            final("已查到 72 条需求理解或决策阶段失败且待复核的回答记录。", "obs_1"),
        ],
    )
    tools = MockToolClient(
        {
            "query_agent_answer_reviews": {
                "dataScope": "AGENT_ANSWER_REVIEW_SAFE_SUMMARY",
                "total": 72,
                "page": 1,
                "size": 10,
                "records": [
                    {
                        "answerStatus": "LOW_CONFIDENCE",
                        "confidenceLevel": "LOW",
                        "failureDomain": "PLANNER",
                        "reviewStatus": "OPEN",
                    }
                ],
            }
        }
    )
    runtime, _ = runtime_for(
        model,
        tools,
        allowed_experts=("audit_expert",),
    )

    response = runtime.chat(
        chat_request("只看需求理解或决策阶段失败的待复核回答")
    )

    assert response.error is None
    assert len(tools.calls) == 1
    assert tools.calls[0]["arguments"] == arguments
    assert any(
        observation.get("status") == "PLAN_REJECTED"
        and "不得重复调用工具" in str(observation.get("message"))
        for observation in model.expert_requests[-1].observations
    )
    assert "72 条" in response.answer


def test_llm_agent_tool_audit_observation_uses_only_user_facing_labels() -> None:
    model = ScriptedLlmModel(
        [main_delegate("audit_expert", "AGENT_TOOL_AUDIT")],
        [
            call("query_agent_tool_audit", {"page": 1, "size": 20}),
            final("今天查到 1 条工具调用审计：角色目录查询成功。", "obs_1"),
        ],
    )
    tools = MockToolClient(
        {
            "query_agent_tool_audit": {
                "dataScope": "RECORDED_AGENT_TOOL_AUDIT",
                "total": 1,
                "records": [
                    {
                        "capability": "query_roles",
                        "resultCode": "SUCCESS",
                        "errorCode": None,
                        "durationMs": 8,
                        "occurredAt": "2026-07-21T10:00:00",
                    }
                ],
            }
        }
    )
    runtime, _ = runtime_for(
        model,
        tools,
        allowed_experts=("audit_expert",),
    )

    response = runtime.chat(chat_request("查询今天的 Agent 工具调用审计"))

    assert response.error is None
    assert response.answer == "今天查到 1 条工具调用审计：角色目录查询成功。"
    observation = model.expert_requests[1].observations[0]["data"]
    assert observation["dataScope"] == "已登记的 Agent 工具调用审计"
    assert observation["records"] == [
        {
            "capabilityLabel": "角色目录查询",
            "resultLabel": "成功",
            "errorLabel": "无",
            "durationMs": 8,
            "occurredAt": "2026-07-21T10:00:00",
        }
    ]
    assert "query_roles" not in str(observation)
    assert "SUCCESS" not in str(observation)


def test_pallet_answer_sanitizes_internal_cycle_fields() -> None:
    runtime, _ = runtime_for(ScriptedLlmModel([], []), MockToolClient())

    answer = runtime._sanitize_llm_answer(
        "最近一次是 cycleNo 4，完整过程为 cycle 1→2→3→4。"
    )

    assert answer == "最近一次是 第 4 次流转，完整过程为 第 1、2、3、4 次流转。"
    assert "cycle" not in answer


def test_llm_rejects_pallet_code_not_in_user_message_or_controlled_state() -> None:
    builder = ToolArgumentBuilder()
    state = InMemoryCheckpointer().get("agt_unbound_pallet")

    with pytest.raises(ValueError, match="neither user-provided nor bound"):
        builder.validate_llm_arguments(
            tool_name="get_pallet_status",
            arguments={"code": "P_FORGED_BY_MODEL"},
            state=state,
            user_message="查询这个托盘现状",
        )


def test_llm_requires_current_pallet_ref_when_code_is_only_in_prior_state() -> None:
    builder = ToolArgumentBuilder()
    state = InMemoryCheckpointer().get("agt_prior_pallet")
    state.selected_pallet = SelectedEntity(
        None,
        "P202607090001",
        "tool_result",
        {"code": "P202607090001"},
        entity_type="PALLET",
        entity_ref="CURRENT_PALLET",
        canonical_name="P202607090001",
    )

    with pytest.raises(ValueError, match="neither user-provided nor bound"):
        builder.validate_llm_arguments(
            tool_name="query_pallet_flow_records",
            arguments={"code": "P202607090001", "page": 1, "size": 20},
            state=state,
            user_message="再看它的历史流转",
        )

    validated = builder.validate_llm_arguments(
        tool_name="query_pallet_flow_records",
        arguments={"palletRef": "CURRENT_PALLET", "page": 1, "size": 20},
        state=state,
        user_message="再看它的历史流转",
    )
    assert validated["code"] == "P202607090001"


def test_main_model_realtime_answer_is_discarded_and_recovered_to_expert() -> None:
    model = ScriptedLlmModel(
        [
            MainAgentDecisionV1(
                action="DIRECT_ANSWER",
                answer="黄冰糖当前有100件。",
                semanticReason="READ_QUERY",
                confidence=0.99,
            )
        ],
        [call("resolve_products", {"query": "黄冰糖", "limit": 10})],
    )
    tools = MockToolClient(
        {
            "resolve_products": {
                "resolutionStatus": "AMBIGUOUS",
                "needsUserSelection": True,
                "candidates": [
                    {
                        "productId": 84,
                        "productName": "黄冰糖（袋）",
                        "displayLabel": "黄冰糖（袋） 25kg/件 40件/板",
                    },
                    {
                        "productId": 85,
                        "productName": "黄冰糖（箱）",
                        "displayLabel": "黄冰糖（箱） 20kg/件 30件/板",
                    },
                ],
            }
        }
    )
    runtime, _ = runtime_for(model, tools)

    response = runtime.chat(chat_request("黄冰糖还有多少库存"))

    assert response.error is None
    assert response.needsUserSelection is True
    assert "100件" not in response.answer
    assert response.reviewTrace["mainRouteGuard"] == {
        "status": "RECOVERED_AS_DELEGATE",
        "rejectedAction": "DIRECT_ANSWER",
        "expertAgent": "inventory_expert",
        "reason": "REALTIME_BUSINESS_FACT_REQUIRES_EXPERT",
    }
    assert tools.calls[0]["toolName"] == "resolve_products"


def test_bounded_inventory_followup_reuses_active_expert_without_main_model_call() -> None:
    model = ScriptedLlmModel(
        [],
        [call("resolve_products", {"query": "黄冰糖", "limit": 10})],
    )
    tools = MockToolClient(
        {
            "resolve_products": {
                "resolutionStatus": "AMBIGUOUS",
                "needsUserSelection": True,
                "candidates": [
                    {
                        "productId": 84,
                        "productName": "黄冰糖（袋）",
                        "displayLabel": "黄冰糖（袋） 25kg/件 40件/板",
                    },
                    {
                        "productId": 85,
                        "productName": "黄冰糖（箱）",
                        "displayLabel": "黄冰糖（箱） 20kg/件 30件/板",
                    },
                ],
            }
        }
    )
    runtime, store = runtime_for(model, tools)
    state = store.get("agt_llm")
    state.active_agent = "inventory_expert"
    state.last_agent_handoff = AgentHandoffRouter().handoff_for_agent(
        "inventory_expert",
        mode="llm_delegate",
    ).to_snapshot()
    state.selected_warehouse = SelectedEntity(2, "2号库位", "resolver")

    response = runtime.chat(chat_request("只看黄冰糖"))

    assert response.error is None
    assert response.needsUserSelection is True
    assert model.main_requests == []
    assert model.expert_requests[0].expertAgent == "inventory_expert"
    assert response.reviewTrace["mainRouteOptimization"] == {
        "status": "REUSED_ACTIVE_EXPERT",
        "expertAgent": "inventory_expert",
        "reason": "BOUNDED_CONTEXT_FOLLOWUP",
    }
    turn_metric = runtime.metrics.snapshot()["histograms"][
        'agent_turn_duration{mode="llm",operation="chat"}'
    ]
    assert turn_metric["count"] == 1


def test_context_bound_inventory_query_reuses_expert_and_fast_formats_result() -> None:
    model = ScriptedLlmModel(
        [],
        [call("get_inventory_overview", {"productRef": "CURRENT_PRODUCT"})],
    )
    tools = MockToolClient(
        {
            "get_inventory_overview": {
                "displayStockInfo": "13板30件",
                "totalEquivalentPieces": 550,
                "totalWeight": 13750,
            }
        }
    )
    runtime, store = runtime_for(model, tools)
    state = store.get("agt_llm")
    state.active_agent = "inventory_expert"
    state.last_agent_handoff = AgentHandoffRouter().handoff_for_agent(
        "inventory_expert",
        mode="llm_delegate",
    ).to_snapshot()
    state.selected_product = SelectedEntity(
        84,
        "黄冰糖小颗粒（袋） 25kg/件 40件/板",
        "user_selection",
        {"productName": "黄冰糖小颗粒（袋）"},
    )

    response = runtime.chat(chat_request("查询黄冰糖当前库存总览"))

    assert response.error is None
    assert model.main_requests == []
    assert len(model.expert_requests) == 1
    assert "13板30件" in response.answer
    assert "需要我帮你查库存分布吗" in response.answer
    assert response.reviewTrace["expertLoop"]["completionMode"] == "SAFE_FACT_FORMATTER"
    assert runtime.metrics.snapshot()["counters"][
        'llm_fast_completion_total{tool="get_inventory_overview"}'
    ] == 1
    assert response.debug is not None
    assert response.debug["performanceSchemaVersion"] == "1.0"
    assert response.debug["totalDurationMs"] >= response.debug["toolDurationMs"]
    assert response.debug["toolCallCount"] == 1
    assert response.debug["modelDecisionCount"] == 0
    assert response.debug["mainRouteDecisionCount"] == 0
    assert response.debug["expertInitialDecisionCount"] == 0
    assert response.debug["expertResultAnalysisDecisionCount"] == 0


def test_model_diagnostics_are_tagged_with_the_runtime_decision_stage(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    runtime, _ = runtime_for(ScriptedLlmModel([], []), MockToolClient())
    trace: dict[str, Any] = {}
    monkeypatch.setattr(
        "app.runtime.take_model_decision_diagnostics",
        lambda: [
            {
                "phase": "EXPERT_ACTION",
                "attempt": 1,
                "latencyMs": 123,
                "outcome": "SUCCESS",
                "httpStatus": 200,
                "validationPaths": [],
            }
        ],
    )

    runtime._append_llm_model_diagnostics(trace, decision_stage="expert_initial")

    assert trace["modelDecisions"] == [
        {
            "phase": "EXPERT_ACTION",
            "attempt": 1,
            "latencyMs": 123,
            "outcome": "SUCCESS",
            "httpStatus": 200,
            "validationPaths": [],
            "decisionStage": "expert_initial",
        }
    ]


def test_llm_loop_resolves_product_then_uses_runtime_bound_product() -> None:
    model = ScriptedLlmModel(
        [main_delegate("inventory_expert")],
        [
            call("resolve_products", {"query": "黄冰糖（袋）", "limit": 10}),
            call("get_inventory_overview", {"productRef": "CURRENT_PRODUCT"}),
        ],
    )
    tools = MockToolClient(
        {
            "resolve_products": {
                "resolutionStatus": "UNIQUE",
                "candidates": [
                    {
                        "productId": 84,
                        "productName": "黄冰糖（袋）",
                        "displayLabel": "黄冰糖（袋） 25kg/件 40件/板",
                    }
                ],
            },
            "get_inventory_overview": {
                "displayStockInfo": "13板30件",
                "totalEquivalentPieces": 550,
                "totalWeight": 13750,
            },
        }
    )
    runtime, _ = runtime_for(model, tools)

    response = runtime.chat(chat_request("查询黄冰糖（袋）的库存"))

    assert "黄冰糖（袋）" in response.answer
    assert "当前库存为 13板30件" in response.answer
    assert "需要我帮你查库存分布吗" in response.answer
    assert [item["toolName"] for item in tools.calls] == ["resolve_products", "get_inventory_overview"]
    assert tools.calls[1]["arguments"] == {"productId": 84}
    assert tools.calls[1]["expertAgent"] == "inventory_expert"
    assert [request.decisionStage for request in model.expert_requests] == [
        "INITIAL",
        "RESULT_ANALYSIS",
    ]
    second_request = model.expert_requests[1]
    assert second_request.selectedContext["PRODUCT"]["canonicalName"] == "黄冰糖（袋）"
    assert "84" not in json.dumps(second_request.selectedContext, ensure_ascii=False)
    assert "25kg/件" not in json.dumps(second_request.selectedContext, ensure_ascii=False)


def test_expert_timeout_safely_falls_back_to_registered_inventory_query() -> None:
    class TimeoutExpertModel(ScriptedLlmModel):
        def decide_expert_action(self, request: ExpertLoopRequest) -> ExpertLoopDecisionV1 | None:
            self.expert_requests.append(request)
            raise ModelDecisionError("MODEL_TIMEOUT", "模型结构化决策响应超时。", retryable=True)

    model = TimeoutExpertModel([main_delegate("inventory_expert")], [])
    tools = MockToolClient(
        {
            "get_inventory_overview": {
                "displayStockInfo": "13板30件",
                "totalEquivalentPieces": 550,
            }
        }
    )
    runtime, store = runtime_for(model, tools)
    store.get("agt_llm").selected_product = SelectedEntity(
        84,
        "黄冰糖（袋）",
        "user_selection",
        {"productName": "黄冰糖（袋）"},
    )

    response = runtime.chat(chat_request("查这个产品库存"))

    assert response.error is None
    assert "13板30件" in response.answer
    assert response.reviewTrace["expertModelFallback"] == {
        "status": "SAFE_DETERMINISTIC_RECOVERY",
        "reason": "MODEL_TIMEOUT",
        "action": "call_tool",
        "toolName": "get_inventory_overview",
    }
    assert tools.calls[0]["toolName"] == "get_inventory_overview"
    assert tools.calls[0]["expertAgent"] == "inventory_expert"


def test_explicit_warehouse_query_may_drop_old_product_filter_without_expanding_permissions() -> None:
    model = ScriptedLlmModel(
        [main_delegate("inventory_expert")],
        [
            call("resolve_warehouses", {"query": "1号库位", "limit": 10}),
            call(
                "get_inventory_distribution",
                {
                    "productScope": {"type": "ALL"},
                    "warehouseScope": {"type": "SINGLE_WAREHOUSE", "warehouseRef": "CURRENT_WAREHOUSE"},
                    "groupBy": "product",
                    "filters": {},
                    "limit": 20,
                },
            ),
            final("1号库位当前有两类产品。", "obs_1", "obs_2"),
        ],
    )
    tools = MockToolClient(
        {
            "resolve_warehouses": {
                "resolutionStatus": "UNIQUE",
                "candidates": [{"warehouseId": 1, "warehouseName": "1", "displayLabel": "1号库位"}],
            },
            "get_inventory_distribution": {
                "scopeLabel": "1号库位的全部产品",
                "productLabel": "全部产品",
                "groupBy": "product",
                "totalStockText": "3板0件",
                "totalEquivalentPieces": 90,
                "warehouseCount": 1,
                "productCount": 2,
                "palletCount": 3,
                "groups": [],
            },
        }
    )
    runtime, store = runtime_for(model, tools)
    state = store.get("agt_llm")
    state.selected_product = SelectedEntity(
        84,
        "旧产品 25kg/件 40件/板",
        "resolver",
        {"productName": "旧产品", "scopeType": "SINGLE_PRODUCT"},
    )

    runtime.chat(chat_request("1号库位有哪些库存"))

    distribution = tools.calls[1]["arguments"]
    assert distribution["productScope"] == {"type": "ALL"}
    assert distribution["warehouseScope"] == {"type": "SINGLE_WAREHOUSE", "warehouseId": 1}


def test_display_label_cannot_be_reused_as_resolver_input_when_user_did_not_type_it() -> None:
    builder = ToolArgumentBuilder()
    state = InMemoryCheckpointer().get("agt_display_label")
    state.selected_product = SelectedEntity(
        84,
        "黄冰糖（袋） 25kg/件 40件/板",
        "resolver",
        {"productName": "黄冰糖（袋）", "scopeType": "SINGLE_PRODUCT"},
    )

    with pytest.raises(ValueError, match="displayLabel"):
        builder.validate_llm_arguments(
            tool_name="resolve_products",
            arguments={"query": "黄冰糖（袋） 25kg/件 40件/板", "limit": 10},
            state=state,
            user_message="它最新化验怎么样",
        )


def test_mismatched_recipe_proposal_for_product_filter_gets_one_safe_reroute() -> None:
    model = ScriptedLlmModel(
        [main_recipe(), main_delegate("inventory_expert")],
        [
            call("resolve_products", {"query": "黄冰糖", "limit": 10}),
            call(
                "get_inventory_distribution",
                {
                    "productScope": {"type": "SINGLE_PRODUCT", "productRef": "CURRENT_PRODUCT"},
                    "warehouseScope": {
                        "type": "SINGLE_WAREHOUSE",
                        "warehouseRef": "CURRENT_WAREHOUSE",
                    },
                    "groupBy": "product",
                    "filters": {},
                    "limit": 20,
                },
            ),
            final("1号库位当前只显示黄冰糖。", "obs_1", "obs_2"),
        ],
    )
    tools = MockToolClient(
        {
            "resolve_products": {
                "resolutionStatus": "UNIQUE",
                "candidates": [{"productId": 84, "productName": "黄冰糖（袋）"}],
            },
            "get_inventory_distribution": {
                "scopeLabel": "1号库位内黄冰糖（袋）",
                "productLabel": "黄冰糖（袋）",
                "groupBy": "product",
                "totalStockText": "1板0件",
                "totalEquivalentPieces": 40,
                "warehouseCount": 1,
                "productCount": 1,
                "palletCount": 1,
                "groups": [],
            },
        }
    )
    runtime, store = runtime_for(model, tools)
    store.get("agt_llm").selected_warehouse = SelectedEntity(1, "1号库位", "resolver")

    response = runtime.chat(chat_request("只看黄冰糖"))

    assert response.error is None
    assert len(model.main_requests) == 2
    assert model.main_requests[1].registeredRecipes == []
    correction = model.main_requests[1].selectedContext["RUNTIME_ROUTE_CORRECTION"]
    assert correction["reason"] == "CURRENT_MESSAGE_DID_NOT_MATCH_REGISTERED_RECIPE"
    assert [item["toolName"] for item in tools.calls] == [
        "resolve_products",
        "get_inventory_distribution",
    ]
    assert tools.calls[1]["arguments"]["warehouseScope"] == {
        "type": "SINGLE_WAREHOUSE",
        "warehouseId": 1,
    }


def test_mismatched_recipe_proposal_for_assay_followup_gets_one_safe_reroute() -> None:
    model = ScriptedLlmModel(
        [main_recipe(), main_delegate("assay_expert")],
        [
            call("get_assay_status", {"productRef": "CURRENT_PRODUCT"}),
            final("黄冰糖（袋）没有查询到对应日期的化验记录。", "obs_1"),
        ],
    )
    tools = MockToolClient(
        {"get_assay_status": {"status": "NO_DATA", "productLabel": "黄冰糖（袋）"}}
    )
    runtime, store = runtime_for(model, tools)
    store.get("agt_llm").selected_product = SelectedEntity(
        84,
        "黄冰糖（袋）",
        "resolver",
        {"productName": "黄冰糖（袋）", "scopeType": "SINGLE_PRODUCT"},
    )

    response = runtime.chat(chat_request("它最新化验怎么样"))

    assert response.error is None
    assert len(model.main_requests) == 2
    assert model.main_requests[1].registeredRecipes == []
    assert [item["toolName"] for item in tools.calls] == ["get_assay_status"]
    assert tools.calls[0]["arguments"] == {"productId": 84}


def test_matching_compound_request_keeps_registered_recipe_without_reroute() -> None:
    model = ScriptedLlmModel([main_recipe()], [])
    tools = MockToolClient(
        {
            "resolve_warehouses": {
                "resolutionStatus": "UNIQUE",
                "candidates": [{"warehouseId": 1, "displayLabel": "1号库位"}],
            },
            "get_inventory_distribution": {
                "scopeLabel": "1号库位的全部产品",
                "productLabel": "全部产品",
                "groupBy": "product",
                "totalStockText": "1板0件",
                "totalEquivalentPieces": 40,
                "warehouseCount": 1,
                "productCount": 1,
                "palletCount": 1,
                "groups": [
                    {
                        "groupLabel": "黄冰糖（袋） 25kg/件 40件/板",
                        "canonicalProductName": "黄冰糖（袋）",
                        "productLabel": "黄冰糖（袋） 25kg/件 40件/板",
                        "stockText": "1板0件",
                        "totalEquivalentPieces": 40,
                        "palletCount": 1,
                        "warehouseCount": 1,
                        "productCount": 1,
                        "percentageText": "100.0%",
                        "riskLabels": [],
                    }
                ],
            },
            "resolve_products": {
                "resolutionStatus": "UNIQUE",
                "candidates": [{"productId": 84, "productName": "黄冰糖（袋）"}],
            },
            "query_assay_records": {"total": 0, "records": []},
        }
    )
    runtime, _ = runtime_for(model, tools)

    response = runtime.chat(chat_request("1号库位库存及这些产品的最新化验"))

    assert response.error is None
    assert len(model.main_requests) == 1
    assert [item["toolName"] for item in tools.calls] == [
        "resolve_warehouses",
        "get_inventory_distribution",
        "resolve_products",
        "query_assay_records",
    ]
    assert tools.calls[2]["arguments"]["query"] == "黄冰糖（袋）"
    assert "25kg/件" not in tools.calls[2]["arguments"]["query"]


def test_model_generated_raw_id_is_rejected_then_one_correction_is_allowed() -> None:
    model = ScriptedLlmModel(
        [main_delegate("inventory_expert")],
        [
            call("get_inventory_overview", {"productId": 84}),
            call("get_inventory_overview", {"productRef": "CURRENT_PRODUCT"}),
            final("当前库存已查询。", "obs_2"),
        ],
    )
    tools = MockToolClient({"get_inventory_overview": {"displayStockInfo": "1板0件"}})
    runtime, store = runtime_for(model, tools)
    store.get("agt_llm").selected_product = SelectedEntity(
        84,
        "黄冰糖（袋）",
        "resolver",
        {"productName": "黄冰糖（袋）", "scopeType": "SINGLE_PRODUCT"},
    )

    response = runtime.chat(chat_request("它还有多少库存"))

    assert response.error is None
    assert len(tools.calls) == 1
    assert tools.calls[0]["arguments"] == {"productId": 84}
    assert model.expert_requests[1].observations[0]["status"] == "PLAN_REJECTED"


def test_tool_error_cannot_be_cited_as_no_data_completion() -> None:
    model = ScriptedLlmModel(
        [main_delegate("inventory_expert")],
        [
            call("get_inventory_overview", {"productRef": "CURRENT_PRODUCT"}),
            final("没有查询到库存。", "obs_1"),
            final("仍然没有查询到库存。", "obs_1"),
        ],
    )
    tools = MockToolClient(
        {"get_inventory_overview": ToolGatewayError("UPSTREAM_TIMEOUT", "timeout", retryable=False)}
    )
    runtime, store = runtime_for(model, tools)
    store.get("agt_llm").selected_product = SelectedEntity(
        84,
        "黄冰糖（袋）",
        "resolver",
        {"productName": "黄冰糖（袋）", "scopeType": "SINGLE_PRODUCT"},
    )

    response = runtime.chat(chat_request("它还有多少库存"))

    assert response.error is not None
    assert response.error.code == "LLM_RUNTIME_BOUNDARY_REJECTED"
    assert "没有查询到库存" not in response.answer


def test_completion_validation_allows_one_model_correction() -> None:
    model = ScriptedLlmModel(
        [main_delegate("logistics_expert", "AUTO_INBOUND_BATCH_STATUS")],
        [
            call("query_auto_inbound_batches", {"limit": 20}),
            final("最近没有自动报数入库批次。", "obs_999"),
            final("最近没有自动报数入库批次。", "obs_1"),
        ],
    )
    tools = MockToolClient(
        {
            "query_auto_inbound_batches": {
                "dataScope": "CURRENT_USER_RECENT_AUTO_INBOUND_BATCHES",
                "count": 0,
                "records": [],
            }
        }
    )
    runtime, _ = runtime_for(model, tools)

    response = runtime.chat(chat_request("查询最近的自动报数入库批次"))

    assert response.error is None
    assert response.answer == "最近没有自动报数入库批次。"
    assert len(model.expert_requests) == 3
    assert model.expert_requests[2].observations[-1]["status"] == "PLAN_REJECTED"
    assert {
        "action": "PLAN_REJECTED",
        "status": "COMPLETION_VALIDATION_FAILED",
    } in response.reviewTrace["expertLoop"]["events"]


def test_authoritative_no_data_uses_safe_fallback_after_two_missing_citations() -> None:
    model = ScriptedLlmModel(
        [main_delegate("logistics_expert", "AUTO_INBOUND_BATCH_STATUS")],
        [
            call("query_auto_inbound_batches", {"limit": 20}),
            final("最近没有自动报数入库批次。", "obs_2"),
            final("最近没有自动报数入库批次。", "obs_2"),
        ],
    )
    tools = MockToolClient(
        {
            "query_auto_inbound_batches": {
                "dataScope": "CURRENT_USER_RECENT_AUTO_INBOUND_BATCHES",
                "count": 0,
                "records": [],
            }
        }
    )
    runtime, _ = runtime_for(model, tools)

    response = runtime.chat(chat_request("查询最近的自动报数入库批次"))

    assert response.error is None
    assert response.answer == "当前用户没有查询到尚未过期的智能报数批次。该结果不同于批次查询服务失败。"
    assert response.reviewTrace["expertLoop"]["completionMode"] == "SAFE_NO_DATA_FALLBACK"
    assert {
        "action": "FINAL_ANSWER",
        "status": "AUTHORITATIVE_NO_DATA_FALLBACK",
    } in response.reviewTrace["expertLoop"]["events"]


def test_authoritative_no_data_uses_safe_fallback_when_model_answer_leaks_tool_name() -> None:
    model = ScriptedLlmModel(
        [main_delegate("administration_expert", "EMPLOYEE_ROSTER")],
        [
            call("query_employee_roster", {"roleName": "仓管员", "page": 1, "size": 50}),
            final("query_employee_roster 没有返回员工。", "obs_1"),
        ],
    )
    tools = MockToolClient(
        {
            "query_employee_roster": {
                "dataScope": "EMPLOYEE_ROSTER",
                "total": 0,
                "records": [],
            }
        }
    )
    runtime, _ = runtime_for(
        model,
        tools,
        allowed_experts=("administration_expert",),
    )

    response = runtime.chat(chat_request("查询当前仓管员名单"))

    assert response.error is None
    assert response.answer == "当前没有查询到符合条件的员工记录。"
    assert response.reviewTrace["expertLoop"]["completionMode"] == "SAFE_NO_DATA_FALLBACK"
    assert {
        "action": "FINAL_ANSWER",
        "status": "UNSAFE_ANSWER_NO_DATA_FALLBACK",
    } in response.reviewTrace["expertLoop"]["events"]


def test_authentication_failure_is_not_reported_as_business_permission_denial() -> None:
    model = ScriptedLlmModel(
        [main_delegate("inventory_expert")],
        [call("get_inventory_overview", {"productRef": "CURRENT_PRODUCT"})],
    )
    tools = MockToolClient(
        {
            "get_inventory_overview": ToolGatewayError(
                "UPSTREAM_UNAUTHORIZED",
                "authentication failed",
                retryable=False,
            )
        }
    )
    runtime, store = runtime_for(model, tools)
    store.get("agt_llm").selected_product = SelectedEntity(
        84,
        "黄冰糖（袋）",
        "resolver",
        {"productName": "黄冰糖（袋）", "scopeType": "SINGLE_PRODUCT"},
    )

    response = runtime.chat(chat_request("它还有多少库存"))

    assert response.error is not None
    assert response.error.code == "UPSTREAM_AUTHENTICATION_FAILED"
    assert "认证失败" in response.answer
    assert "没有执行该只读查询的权限" not in response.answer


def test_ambiguous_resolver_interrupt_resumes_same_expert_loop() -> None:
    model = ScriptedLlmModel(
        [main_delegate("inventory_expert")],
        [
            call("resolve_products", {"query": "黄冰糖", "limit": 10}),
            call("get_inventory_overview", {"productRef": "CURRENT_PRODUCT"}),
            final("所选产品当前库存为1板。", "obs_2", "obs_3"),
        ],
    )
    tools = MockToolClient(
        {
            "resolve_products": {
                "resolutionStatus": "AMBIGUOUS",
                "needsUserSelection": True,
                "candidates": [
                    {
                        "productId": 84,
                        "productName": "黄冰糖（袋）",
                        "displayLabel": "黄冰糖（袋） 25kg/件 40件/板",
                    },
                    {
                        "productId": 85,
                        "productName": "黄冰糖（箱）",
                        "displayLabel": "黄冰糖（箱） 20kg/件 30件/板",
                    },
                ],
            },
            "get_inventory_overview": {"displayStockInfo": "1板0件", "totalEquivalentPieces": 40},
        }
    )
    runtime, store = runtime_for(model, tools)

    interrupted = runtime.chat(chat_request("查询黄冰糖库存"))
    pending = store.get("agt_llm").pending_clarification
    assert interrupted.needsUserSelection is True
    assert pending is not None

    resumed = runtime.resume(
        ResumeRequest.model_validate(
            {
                "agentSessionId": "agt_llm",
                "messageId": "msg_002",
                "resumeToken": pending.resume_token,
                "event": {
                    "type": "candidate_selected",
                    "interruptId": pending.interrupt_id,
                    "action": "SELECT_OPTION",
                    "selection": {"optionId": "opt_001"},
                    "clientRequestId": "resume_001",
                },
                "client": {"traceId": "trace_002", "requestId": "req_002", "debug": True},
            }
        )
    )

    assert resumed.error is None
    assert "1板" in resumed.answer
    assert tools.calls[-1]["arguments"] == {"productId": 84}
    assert tools.calls[-1]["expertAgent"] == "inventory_expert"


def test_expert_cannot_call_another_experts_tool() -> None:
    model = ScriptedLlmModel(
        [main_delegate("inventory_expert")],
        [
            call("get_assay_status", {"productRef": "CURRENT_PRODUCT"}),
            call("get_warehouse_status", {"warehouseRef": "CURRENT_WAREHOUSE"}),
        ],
    )
    runtime, store = runtime_for(model, MockToolClient())
    state = store.get("agt_llm")
    state.selected_product = SelectedEntity(84, "黄冰糖（袋）", "resolver", {"productName": "黄冰糖（袋）"})
    state.selected_warehouse = SelectedEntity(1, "1号库位", "resolver")

    response = runtime.chat(chat_request("查一下"))

    assert response.error is not None
    assert response.error.code == "LLM_RUNTIME_BOUNDARY_REJECTED"
    assert "连续提出" in response.answer


def test_runtime_stops_fourth_tool_call_even_when_model_keeps_requesting() -> None:
    model = ScriptedLlmModel(
        [main_delegate("inventory_expert")],
        [
            call("query_inventory_ledger", {"page": 1, "size": 20}),
            call("query_inventory_ledger", {"page": 2, "size": 20}),
            call("query_inventory_ledger", {"page": 3, "size": 20}),
            call("query_inventory_ledger", {"page": 4, "size": 20}),
        ],
    )
    tools = MockToolClient({"query_inventory_ledger": {"total": 100, "records": []}})
    runtime, _ = runtime_for(model, tools)

    response = runtime.chat(chat_request("分页查看库存台账"))

    assert response.error is not None
    assert response.error.code == "LLM_RUNTIME_BOUNDARY_REJECTED"
    assert len(tools.calls) == 3
    assert "达到上限" in response.answer


def test_llm_stream_uses_expert_final_answer_without_second_model_call(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    from app.main import create_app

    model = ScriptedLlmModel(
        [main_delegate("inventory_expert")],
        [
            call("resolve_products", {"query": "黄冰糖（袋）", "limit": 10}),
            call("get_inventory_overview", {"productRef": "CURRENT_PRODUCT"}),
        ],
    )
    tools = MockToolClient(
        {
            "resolve_products": {
                "resolutionStatus": "UNIQUE",
                "candidates": [{"productId": 84, "productName": "黄冰糖（袋）"}],
            },
            "get_inventory_overview": {
                "displayStockInfo": "13板30件",
                "totalEquivalentPieces": 550,
            },
        }
    )
    monkeypatch.setattr("app.main.build_model_client", lambda settings: model)
    app = create_app(
        Settings(
            tool_mode="mock",
            model_mode="openai_compatible",
            planning_mode="llm",
            deployment_environment="test",
            allow_insecure_mock_auth=True,
        ),
        tool_client=tools,
        checkpointer=InMemoryCheckpointer(),
    )

    response = TestClient(app).post(
        "/internal/agent/chat/stream",
        json=chat_request("查询黄冰糖（袋）的库存").model_dump(mode="json"),
    )

    assert response.status_code == 200
    assert "黄冰糖（袋）当前库存为 13板30件" in response.text
    assert "需要我帮你查库存分布吗" in response.text
    assert len(model.expert_requests) == 2
    assert model.stream_call_count == 0


def test_llm_pending_task_journey_keeps_filters_and_reuses_safe_detail_without_tool_call() -> None:
    model = ScriptedLlmModel(
        [main_delegate("logistics_expert")],
        [
            call("query_pallet_tasks", {"status": "PENDING", "page": 1, "size": 20}),
            call("query_pallet_tasks", {"taskType": "OUT", "page": 1, "size": 20}),
        ],
    )
    tools = MockToolClient(
        {
            "query_pallet_tasks": {
                "dataScope": "CURRENT_PALLET_TASKS",
                "total": 1,
                "page": 1,
                "size": 20,
                "records": [
                    {
                        "id": 987,
                        "taskType": "FINISH_IN",
                        "taskStatus": "PENDING",
                        "code": "BT00161C",
                        "operationBatchNo": "OP-20260720-01",
                        "targetWarehouseName": "2号库位",
                        "targetSide": "LEFT",
                        "productName": "黄冰糖（袋） 25kg/件 40件/板",
                        "productStatus": "成品",
                        "totalWeight": 1000,
                        "productionDate": "2026-07-20",
                        "productionOrderNo": "PO-001",
                        "productionOrderStatus": "WAIT_INBOUND",
                        "createdBy": "仓管员",
                        "createdAt": "2026-07-20T09:30:00",
                    }
                ],
            }
        }
    )
    runtime, store = runtime_for(model, tools)

    first = runtime.chat(chat_request("查询当前待处理任务"))
    second = runtime.chat(chat_request("只看出库任务"))
    detail = runtime.chat(chat_request("查看第一条任务详情"))

    assert first.error is None
    assert first.cards[0].cardType == "pallet_tasks"
    assert "PENDING" not in first.answer
    assert "FINISH_IN" not in json.dumps(first.cards[0].model_dump(), ensure_ascii=False)
    assert second.error is None
    assert tools.calls[1]["arguments"]["status"] == "PENDING"
    assert tools.calls[1]["arguments"]["taskType"] == "OUT"
    assert second.reviewTrace["mainRouteOptimization"]["modelCallSkipped"] is True
    assert detail.cards[0].cardType == "pallet_task_detail"
    assert "2号库位 左侧" in detail.answer
    assert detail.reviewTrace["stateReuse"]["toolCalled"] is False
    assert len(tools.calls) == 2
    assert len(model.expert_requests) == 1
    assert runtime.metrics.snapshot()["counters"][
        'llm_context_filter_total{tool="query_pallet_tasks"}'
    ] == 1
    state = store.get("agt_llm")
    assert state.active_goal_type == "CURRENT_PENDING_TASKS"
    assert state.last_goal_completion is not None
    assert state.last_goal_completion["status"] == "COMPLETE"


def test_llm_unqualified_inventory_uses_latest_assay_tool_card_and_controlled_detail_followup() -> None:
    report_ref = "assay_report_inventory_quality_001"
    model = ScriptedLlmModel(
        [
            main_delegate("assay_expert", "CURRENT_UNQUALIFIED_INVENTORY"),
            main_delegate("assay_expert", "PRODUCT_ASSAY_REPORT"),
        ],
        [
            call(
                "query_unqualified_inventory",
                {
                    "productScope": {"type": "ALL"},
                    "warehouseScope": {"type": "ALL"},
                    "limit": 50,
                },
            ),
            call("get_assay_report_detail", {"reportRef": "CURRENT_ASSAY_REPORT"}),
        ],
    )
    tools = MockToolClient(
        {
            "query_unqualified_inventory": {
                "queryType": "JUDGE_STATUS",
                "queryLabel": "当前库存中化验明确不合格的产品",
                "totalGroups": 1,
                "totalEquivalentPieces": 40,
                "totalWeightText": "1000 kg",
                "truncated": False,
                "records": [
                    {
                        "productLabel": "黄冰糖（袋） 25kg/件 40件/板",
                        "productionDate": "2026-07-17",
                        "warehouseLabel": "1号库位",
                        "stockText": "1板40件",
                        "totalWeightText": "1000 kg",
                        "palletCount": 1,
                        "judgeStatus": "FAIL",
                        "judgeLabel": "不合格",
                        "standardLabel": "黄冰糖 v1",
                        "failedMetricText": "色值",
                        "reportRef": report_ref,
                    }
                ],
                "notes": ["无化验和无适用标准的库存不计入不合格。"],
            },
            "get_assay_report_detail": {
                "reportRef": report_ref,
                "reportLabel": "黄冰糖（袋） 2026-07-17 化验报告",
                "productLabel": "黄冰糖（袋）",
                "sampleDate": "2026-07-17",
                "judgeStatus": "FAILED",
                "judgeLabel": "不合格",
                "standardLabel": "黄冰糖 v1",
                "metrics": [
                    {
                        "metricCode": "color_value",
                        "metricName": "色值",
                        "actualValueText": "180 IU",
                        "standardRangeText": "≥ 200 IU",
                        "resultLabel": "不合格",
                    }
                ],
            },
        }
    )
    runtime, store = runtime_for(model, tools)

    first = runtime.chat(chat_request("库存中有哪些不合格产品"))
    detail = runtime.chat(chat_request("第一条的详细化验数据"))

    assert first.error is None
    assert first.reviewTrace["goalCompletion"]["status"] == "COMPLETE"
    assert first.cards[0].cardType == "inventory_quality"
    assert "1 个当前库存批次" in first.answer
    assert "无标准" not in first.answer
    assert "FAIL" not in json.dumps(first.model_dump(), ensure_ascii=False)
    assert report_ref not in json.dumps(first.model_dump(), ensure_ascii=False)
    assert store.get("agt_llm").last_inventory_quality["records"][0]["reportRef"] == report_ref
    assert len(model.expert_requests) == 2, len(model.expert_requests)
    assert len(tools.calls) == 2, tools.calls
    assert detail.error is None, {
        "detail": detail.model_dump(),
        "expertRequests": len(model.expert_requests),
        "toolCalls": tools.calls,
    }
    assert detail.needsUserSelection is False, detail.model_dump()
    assert detail.cards[0].cardType == "assay_report"
    assert tools.calls[1]["arguments"]["reportRef"] == report_ref
    assert report_ref not in repr(model.expert_requests)


def test_llm_inventory_standard_query_can_use_catalog_then_controlled_quality_tool() -> None:
    model = ScriptedLlmModel(
        [main_delegate("assay_expert")],
        [
            call(
                "query_quality_standard_catalog",
                {"standardName": "黄冰糖", "status": "ENABLED", "page": 1, "size": 20},
            ),
            call(
                "query_inventory_by_quality_standard",
                {
                    "productScope": {"type": "ALL"},
                    "warehouseScope": {"type": "ALL"},
                    "standardCode": "QS-YBT",
                    "standardVersion": 2,
                    "limit": 50,
                },
            ),
        ],
    )
    tools = MockToolClient(
        {
            "query_quality_standard_catalog": {
                "total": 1,
                "records": [
                    {
                        "standardCode": "QS-YBT",
                        "standardName": "黄冰糖标准",
                        "version": 2,
                        "status": "ENABLED",
                    }
                ],
            },
            "query_inventory_by_quality_standard": {
                "queryType": "STANDARD",
                "queryLabel": "符合黄冰糖标准 v2的当前库存",
                "totalGroups": 1,
                "totalEquivalentPieces": 40,
                "totalWeightText": "1000 kg",
                "records": [
                    {
                        "productLabel": "黄冰糖（袋）",
                        "productionDate": "2026-07-17",
                        "warehouseLabel": "1号库位",
                        "stockText": "1板40件",
                        "judgeLabel": "符合该标准",
                        "standardLabel": "黄冰糖标准 v2",
                    }
                ],
            },
        }
    )
    runtime, _ = runtime_for(model, tools)

    response = runtime.chat(chat_request("库存中哪些产品符合黄冰糖标准"))

    assert response.error is None
    assert response.cards[0].cardType == "inventory_quality"
    assert [item["toolName"] for item in tools.calls] == [
        "query_quality_standard_catalog",
        "query_inventory_by_quality_standard",
    ]
    assert tools.calls[1]["arguments"]["standardCode"] == "QS-YBT"
    assert tools.calls[1]["arguments"]["standardVersion"] == 2


def test_llm_inventory_metric_query_keeps_closed_metric_and_numeric_operator() -> None:
    model = ScriptedLlmModel(
        [main_delegate("assay_expert")],
        [
            call(
                "query_inventory_by_assay_metrics",
                {
                    "productScope": {"type": "ALL"},
                    "warehouseScope": {"type": "ALL"},
                    "metricCondition": {
                        "metricCode": "sucrose",
                        "operator": "GTE",
                        "value": 99.7,
                    },
                    "limit": 50,
                },
            )
        ],
    )
    tools = MockToolClient(
        {
            "query_inventory_by_assay_metrics": {
                "queryType": "METRIC",
                "queryLabel": "蔗糖分不低于 99.7 的当前库存",
                "totalGroups": 1,
                "totalEquivalentPieces": 40,
                "totalWeightText": "1000 kg",
                "records": [
                    {
                        "productLabel": "黄冰糖（袋）",
                        "productionDate": "2026-07-17",
                        "warehouseLabel": "1号库位",
                        "stockText": "1板40件",
                        "metricLabel": "蔗糖分",
                        "metricValueText": "99.8 g/100g",
                    }
                ],
            }
        }
    )
    runtime, _ = runtime_for(model, tools)

    response = runtime.chat(chat_request("库存中哪些产品蔗糖分不低于99.7"))

    assert response.error is None
    assert response.cards[0].cardType == "inventory_quality"
    assert tools.calls[0]["arguments"]["metricCondition"] == {
        "metricCode": "sucrose",
        "operator": "GTE",
        "value": 99.7,
    }
