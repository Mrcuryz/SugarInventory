from __future__ import annotations

import json
from typing import Any

import pytest
from fastapi.testclient import TestClient

from app.agents import AgentHandoffRouter
from app.config import Settings
from app.graph.state import InMemoryCheckpointer, SelectedEntity
from app.model import BasicModelClient, ExpertLoopRequest, MainAgentRouteRequest
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
            llm_allowed_experts=("inventory_expert", "warehouse_expert", "assay_expert"),
            llm_max_tool_calls=3,
            llm_max_tool_retries=1,
        ),
        store,
    )


def test_llm_planning_mode_is_disabled_by_default(monkeypatch: pytest.MonkeyPatch) -> None:
    monkeypatch.delenv("AGENT_PLANNING_MODE", raising=False)

    assert Settings.from_env().planning_mode == "deterministic"


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


def test_main_model_cannot_directly_answer_realtime_business_facts() -> None:
    model = ScriptedLlmModel(
        [
            MainAgentDecisionV1(
                action="DIRECT_ANSWER",
                answer="黄冰糖当前有100件。",
                semanticReason="READ_QUERY",
                confidence=0.99,
            )
        ],
        [],
    )
    runtime, _ = runtime_for(model, MockToolClient())

    response = runtime.chat(chat_request("黄冰糖还有多少库存"))

    assert response.error is not None
    assert response.error.code == "LLM_RUNTIME_BOUNDARY_REJECTED"
    assert "100件" not in response.answer


def test_llm_loop_resolves_product_then_uses_runtime_bound_product() -> None:
    model = ScriptedLlmModel(
        [main_delegate("inventory_expert")],
        [
            call("resolve_products", {"query": "黄冰糖（袋）", "limit": 10}),
            call("get_inventory_overview", {"productRef": "CURRENT_PRODUCT"}),
            final("黄冰糖（袋）当前库存为13板30件，折合550件。", "obs_1", "obs_2"),
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

    assert response.answer.startswith("黄冰糖（袋）当前库存")
    assert [item["toolName"] for item in tools.calls] == ["resolve_products", "get_inventory_overview"]
    assert tools.calls[1]["arguments"] == {"productId": 84}
    assert tools.calls[1]["expertAgent"] == "inventory_expert"
    second_request = model.expert_requests[1]
    assert second_request.selectedContext["PRODUCT"]["canonicalName"] == "黄冰糖（袋）"
    assert "84" not in json.dumps(second_request.selectedContext, ensure_ascii=False)
    assert "25kg/件" not in json.dumps(second_request.selectedContext, ensure_ascii=False)


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
            final("黄冰糖（袋）当前库存为13板30件。", "obs_1", "obs_2"),
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
    assert "黄冰糖（袋）当前库存为13板30件" in response.text
    assert model.stream_call_count == 0
