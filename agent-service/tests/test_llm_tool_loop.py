from __future__ import annotations

import json
from typing import Any

import pytest
from fastapi.testclient import TestClient

from app.agents import AgentHandoffRouter
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


def main_delegate(expert: str) -> MainAgentDecisionV1:
    return MainAgentDecisionV1(
        action="DELEGATE",
        expertAgent=expert,
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
            "message": {"type": "user_message", "content": message},
            "client": {"traceId": "trace_001", "requestId": "req_001", "debug": True},
        }
    )


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
    builder = ToolArgumentBuilder(model_client=model)
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


def test_pallet_expert_remains_outside_default_llm_pilot_scope() -> None:
    assert "pallet_expert" not in Settings().llm_allowed_experts
    assert "logistics_expert" in Settings().llm_allowed_experts


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
                "palletInfo": {"code": pallet_code, "status": "在库"},
            },
            "query_pallet_flow_records": {
                "scopeLabel": f"托盘 {pallet_code}",
                "total": 1,
                "records": [
                    {
                        "time": "2026-07-09 10:00:00",
                        "eventLabel": "入库",
                        "codeLabel": pallet_code,
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
