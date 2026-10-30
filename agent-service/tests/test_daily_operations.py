from copy import deepcopy

import pytest

from app.daily_operations import DailyOperationsScope, project_nonproduction, nonproduction_card
from app.orchestration import CompoundIntentPlanner, new_orchestration_state, validate_restored_plan
from app.schemas import MainAgentDecisionV1, ExpertLoopDecisionV1
from app.tools.client import MockToolClient, ToolGatewayError
from app.config import Settings
from app.model import OpenAICompatibleModelClient
from tests.test_llm_tool_loop import ScriptedLlmModel, call, final, runtime_for, chat_request
from tests.test_production_daily_reports import saved_report


DAY = "2026-07-21"
DAILY_QUERY = {"startDate": DAY, "endDate": DAY}
OPERATIONS_QUERY = {**DAILY_QUERY, "reportDefinitionId": "today_operations_overview_v1", "reportVersion": 1}


def operations():
    return {
        "reportRunId": "report_run_legacy123456", "reportDefinitionId": "today_operations_overview_v1", "reportVersion": 1,
        "dateRangeLabel": DAY, "dataAsOf": DAY + "T16:00:00+08:00",
        "operationsOverview": {
            "businessDate": DAY,
            "productionOutput": {"outputRecordCount": 9, "productionOrderCount": 3, "totalWeightKg": 9999, "totalPieces": 8888},
            "productionFlow": {"stableOutputWeightKg": 9999, "materialInputWeightKg": 12345},
            "assayQuality": {"assayRecordCount": 3, "passCount": 1, "failCount": 1, "noStandardCount": 1,
                             "multipleCandidatesCount": 0, "judgedRecordCount": 2, "passRatePercent": 50},
            "currentInventory": {"productCount": 3, "warehouseCount": 2, "palletCount": 7, "totalEquivalentPieces": 280,
                                 "totalStockText": "7 板", "totalWeightText": None},
            "todayPalletTasks": {"cohortTaskCount": 4, "completedTaskCount": 2, "inProgressTaskCount": 1, "canceledTaskCount": 1},
            "currentPendingTaskCount": 7,
        },
        "dataQuality": {"partial": False, "notes": []},
        "limitations": ["产出是订单登记值，9999 kg。"],
    }


def daily():
    return saved_report(scopeLabel="全部车间", total=1, hasMore=False,
                        overview=[{"summaryType": "全厂产量", "label": "总产量", "quantity": 200,
                                   "unit": "件", "convertedTons": 2, "recordCount": 1,
                                   "quantityRows": 1, "tonnageRows": 1, "observedDays": 1, "draftRows": 1}])


def recipe():
    return MainAgentDecisionV1(action="RUN_REGISTERED_RECIPE", recipeId="today_daily_operations",
                               semanticReason="CROSS_DOMAIN_RECIPE", confidence=.99)


def setup(decisions, results=None):
    model = ScriptedLlmModel([recipe()], decisions)
    tools = MockToolClient(results or {"query_production_daily_reports": daily(), "run_registered_report": operations()})
    runtime, store = runtime_for(model, tools, allowed_experts=("production_expert", "analytics_expert"))
    return runtime, store, model, tools


def successful_decisions():
    return [call("query_production_daily_reports", DAILY_QUERY), final("日报200件，2吨，包含已保存草稿。", "obs_1"),
            call("run_registered_report", OPERATIONS_QUERY), final("化验3条，当前库存7板，待处理7条。", "obs_1"),
            final("日报产量200件、2吨；化验3条，库存7板，待处理7条。", "daily_production", "nonproduction_operations")]


def test_recipe_runs_two_isolated_expert_loops_then_model_analysis():
    runtime, store, model, tools = setup(successful_decisions())
    state = store.get("agt_llm")
    state.last_daily_report_query = {"startDate": "2026-07-20", "endDate": "2026-07-20", "departmentName": "红糖车间", "page": 3}
    response = runtime.chat(chat_request("今天整体运营情况如何？"))
    assert response.error is None
    assert [item["toolName"] for item in tools.calls] == ["query_production_daily_reports", "run_registered_report"]
    assert [req.expertAgent for req in model.expert_requests] == ["production_expert", "production_expert", "analytics_expert", "analytics_expert", "main_agent"]
    assert list(model.expert_requests[0].toolSchemas) == ["query_production_daily_reports"]
    assert list(model.expert_requests[2].toolSchemas) == ["run_registered_report"]
    assert model.expert_requests[-1].toolSchemas == {}
    assert response.reviewTrace["recipeSummaryMode"] == "MODEL_ANALYSIS"
    assert response.reviewTrace["orchestration"]["toolCallCount"] == 2
    assert response.reviewTrace["orchestration"]["orchestrationStatus"] == "SUCCESS"
    assert [card.cardType for card in response.cards] == ["production_daily_records", "daily_operations_business_facts"]
    assert "9999" not in response.answer + str(response.cards)
    for req in (model.expert_requests[3], model.expert_requests[4]):
        assert "9999" not in str(req.observations) and "productionOutput" not in str(req.observations)
    assert state.last_report_context is None
    assert "departmentName" not in state.last_daily_report_query
    assert "reportRunId" not in str(response.cards)
    assert model.expert_requests[-1].toolCallCount == 2


def test_projection_never_mutates_or_relabels_legacy_report():
    raw = operations()
    before = deepcopy(raw)
    result = project_nonproduction(raw, DAY)
    assert raw == before
    assert "9999" not in str(result)
    assert "reportRunId" not in str(result)
    assert result["assayQuality"]["multipleCandidatesCount"] == 0
    assert result["currentInventory"]["totalWeightText"] is None
    assert "reportRunId" not in nonproduction_card(result).model_dump_json()


@pytest.mark.parametrize("section", ["assayQuality", "currentInventory", "todayPalletTasks"])
def test_missing_section_is_not_zero(section):
    raw = operations()
    del raw["operationsOverview"][section]
    with pytest.raises(ValueError):
        project_nonproduction(raw, DAY)


@pytest.mark.parametrize("bad", [None, True, -1, "0", 1.5])
def test_missing_or_invalid_counts_are_rejected(bad):
    raw = operations()
    raw["operationsOverview"]["currentPendingTaskCount"] = bad
    with pytest.raises(ValueError):
        project_nonproduction(raw, DAY)


@pytest.mark.parametrize("bad", ["NaN", float("inf"), -1, 101, True])
def test_invalid_rate_rejected(bad):
    raw = operations()
    raw["operationsOverview"]["assayQuality"]["passRatePercent"] = bad
    with pytest.raises(ValueError):
        project_nonproduction(raw, DAY)


def test_unknown_pass_rate_is_not_zero_and_partial_is_preserved():
    raw = operations()
    raw["operationsOverview"]["assayQuality"]["passRatePercent"] = None
    raw["dataQuality"] = {"partial": True, "notes": ["生产产出：9999 kg", "化验：缺标准"]}
    result = project_nonproduction(raw, DAY)
    assert result["assayQuality"]["passRatePercent"] is None
    assert result["partial"] is True and result["notes"] == ["化验：缺标准"]


@pytest.mark.parametrize("patch", [{"startDate": "2026-07-20"}, {"itemQuery": "黄冰糖"}, {"page": 2}, {"departmentName": "多晶车间"}])
def test_recipe_cannot_silently_change_scope(patch):
    with pytest.raises(ValueError):
        DailyOperationsScope(DAY, "daily_production").validate_arguments("query_production_daily_reports", {**DAILY_QUERY, **patch})


@pytest.mark.parametrize("patch", [{"reportDefinitionId": "daily_production_overview_v1"}, {"reportVersion": True}, {"comparisonMode": "PREVIOUS_PERIOD"}, {"productQuery": "黄冰糖"}])
def test_operations_step_cannot_substitute_report(patch):
    with pytest.raises(ValueError):
        DailyOperationsScope(DAY, "nonproduction_operations").validate_arguments("run_registered_report", {**OPERATIONS_QUERY, **patch})


def test_permission_failure_does_not_fall_back_to_order_production():
    decisions = [call("query_production_daily_reports", DAILY_QUERY),
                 call("run_registered_report", OPERATIONS_QUERY), final("已取得库存和化验。", "obs_1"),
                 ExpertLoopDecisionV1(action="PARTIAL_ANSWER", answer="日报查询未完成，其他业务数据见卡片。",
                                      citedObservationIds=["daily_production", "nonproduction_operations"], statusReason="PARTIAL_DATA")]
    runtime, _, model, tools = setup(decisions, {"query_production_daily_reports": ToolGatewayError("UPSTREAM_PERMISSION_DENIED", "denied", retryable=False),
                                              "run_registered_report": operations()})
    response = runtime.chat(chat_request("今天整体运营情况如何？"))
    assert response.reviewTrace["orchestration"]["orchestrationStatus"] == "PARTIAL_SUCCESS"
    assert "未完成" in response.answer and "9999" not in str(response.cards)
    assert len(tools.calls) == 2
    assert model.expert_requests[-1].observations[0]["status"] == "TOOL_ERROR"


def test_wrong_date_raw_evidence_is_not_rendered_as_success():
    raw = operations()
    raw["operationsOverview"]["businessDate"] = "2026-07-20"
    runtime, _, _, _ = setup(successful_decisions()[:3], {"query_production_daily_reports": daily(), "run_registered_report": raw})
    response = runtime.chat(chat_request("今天整体运营情况如何？"))
    assert response.reviewTrace["orchestration"]["orchestrationStatus"] == "PARTIAL_SUCCESS"
    assert response.reviewTrace["orchestration"]["toolCallCount"] == 2
    assert "不完整或日期不匹配" in str(response.cards)


def test_historical_request_never_executes_today_recipe():
    runtime, _, _, tools = setup([])
    response = runtime.chat(chat_request("昨天整体运营情况如何？"))
    assert response.needsUserSelection and not tools.calls


def test_summary_without_both_source_citations_falls_back_to_cards():
    decisions = successful_decisions()
    decisions[-1] = final("全部完成。", "nonproduction_operations")
    runtime, _, _, _ = setup(decisions)
    response = runtime.chat(chat_request("今天整体运营情况如何？"))
    assert response.reviewTrace["recipeSummaryMode"] == "SAFE_CARD_FALLBACK"
    assert "分项查询结果" in response.answer and len(response.cards) == 2


def test_model_may_describe_complete_empty_daily_evidence_as_partial_overview():
    decisions = successful_decisions()
    decisions[-1] = ExpertLoopDecisionV1(action="PARTIAL_ANSWER", answer="今天暂无已保存日报，化验和库存情况见卡片。",
        citedObservationIds=["daily_production", "nonproduction_operations"], statusReason="PARTIAL_DATA")
    empty = saved_report(isEmpty=True, records=[], reports=[], overview=[], total=0, hasMore=False)
    runtime, _, _, _ = setup(decisions, {"query_production_daily_reports": empty, "run_registered_report": operations()})
    response = runtime.chat(chat_request("今天整体运营情况如何？"))
    assert response.reviewTrace["recipeSummaryMode"] == "MODEL_ANALYSIS"
    assert response.debug["recipeSummaryAction"] == "PARTIAL_ANSWER"
    assert response.debug["recipeSummaryCitationsComplete"] is True


def test_recipe_identity_and_step_tools_are_protected():
    plan = CompoundIntentPlanner().daily_operations_plan()
    snapshot = new_orchestration_state(plan)
    validate_restored_plan(snapshot)
    assert snapshot["steps"]["daily_production"]["allowedTools"] == ["query_production_daily_reports"]
    assert snapshot["dataScope"] == "DAILY_REPORT_AND_CURRENT_OPERATIONS"
    snapshot["steps"]["daily_production"]["plannedTools"] = ["run_registered_report"]
    with pytest.raises(ValueError):
        validate_restored_plan(snapshot)


def test_both_permission_failures_are_failed_not_partial_success():
    denied = ToolGatewayError("UPSTREAM_PERMISSION_DENIED", "denied", retryable=False)
    runtime, _, _, tools = setup([call("query_production_daily_reports", DAILY_QUERY), call("run_registered_report", OPERATIONS_QUERY)],
                                {"query_production_daily_reports": denied, "run_registered_report": denied})
    response = runtime.chat(chat_request("今天整体运营情况如何？"))
    assert response.error.code == "UPSTREAM_PERMISSION_DENIED"
    assert response.reviewTrace["orchestration"]["orchestrationStatus"] == "FAILED"
    assert len(tools.calls) == 2


def test_empty_daily_is_not_replaced_by_nonempty_orders():
    empty = saved_report(isEmpty=True, records=[], reports=[], overview=[], total=0, hasMore=False)
    decisions = successful_decisions()
    decisions[1] = final("今天没有已保存的生产日报，不能据此判断实际产量为0。", "obs_1")
    decisions[-1] = final("今天没有已保存日报，不能据此判断实际产量为0；化验3条，当前库存7板。", "daily_production", "nonproduction_operations")
    runtime, _, model, tools = setup(decisions, {"query_production_daily_reports": empty, "run_registered_report": operations()})
    response = runtime.chat(chat_request("今天整体运营情况如何？"))
    assert model.expert_requests[-1].observations[0]["status"] == "NO_DATA"
    assert "9999" not in str(model.expert_requests[-1].observations)
    assert "没有已保存日报" in response.answer
    assert len(tools.calls) == 2 and response.error is None


def test_shared_tool_budget_is_not_reset_between_experts():
    runtime, _, model, tools = setup(successful_decisions())
    runtime.llm_max_tool_calls = 1
    response = runtime.chat(chat_request("今天整体运营情况如何？"))
    assert len(tools.calls) == 1
    assert response.reviewTrace["orchestration"]["orchestrationStatus"] == "PARTIAL_SUCCESS"
    assert response.reviewTrace["orchestration"]["toolBudgetRemaining"] == 0
    assert model.expert_requests[2].toolCallCount == 1


def test_authentication_failure_stops_recipe_before_next_expert():
    runtime, _, _, tools = setup([call("query_production_daily_reports", DAILY_QUERY)],
        {"query_production_daily_reports": ToolGatewayError("UPSTREAM_UNAUTHORIZED", "expired", retryable=False)})
    response = runtime.chat(chat_request("今天整体运营情况如何？"))
    assert response.error.code == "UPSTREAM_AUTHENTICATION_FAILED"
    assert len(tools.calls) == 1
    assert response.reviewTrace["orchestration"]["orchestrationStatus"] == "FAILED"


def test_daily_evidence_from_another_date_is_rejected():
    raw = daily()
    raw["reports"][0]["reportDate"] = "2026-07-20"
    with pytest.raises(ValueError):
        DailyOperationsScope(DAY, "daily_production").validate_daily_evidence(raw)


def test_context_keeps_sources_and_does_not_publish_composite_report_id():
    runtime, store, _, _ = setup(successful_decisions())
    runtime.chat(chat_request("今天整体运营情况如何？"))
    context = runtime._llm_selected_context(store.get("agt_llm"))
    assert context["LAST_OPERATIONS_COMPOSITION"]["productionSource"] == "已保存生产日报"
    assert context["LAST_OPERATIONS_COMPOSITION"]["archivedReport"] is False
    assert "REPORT" not in context


def test_composition_analysis_prompt_does_not_reuse_detail_pagination_rules(monkeypatch):
    runtime, _, model, _ = setup(successful_decisions())
    runtime.chat(chat_request("今天整体运营情况如何？"))
    client = OpenAICompatibleModelClient(Settings())
    captured = {}
    def capture(**kwargs):
        captured.update(kwargs)
        return final("日报200件。", "daily_production", "nonproduction_operations")
    monkeypatch.setattr(client, "_structured_decision", capture)
    client.decide_expert_action(model.expert_requests[-1])
    assert "Do not mention pagination" in captured["system_prompt"]
    assert "not a failed or unfinished query" in captured["system_prompt"]
    assert "not 码垛任务" in captured["system_prompt"]
    assert "describe the returned query scope and page" not in captured["system_prompt"]


def test_regular_analysis_does_not_translate_qr_count_into_full_boards(monkeypatch):
    runtime, _, model, _ = setup(successful_decisions())
    runtime.chat(chat_request("今天整体运营情况如何？"))
    client = OpenAICompatibleModelClient(Settings())
    captured = {}
    def capture(**kwargs):
        captured.update(kwargs)
        return final("当前库存见卡片。", "obs_1")
    monkeypatch.setattr(client, "_structured_decision", capture)
    client.decide_expert_action(model.expert_requests[3])
    assert "not full boards (整板)" in captured["system_prompt"]


def test_legacy_operations_adapter_preserves_unknown_pass_rate():
    runtime, _, _, _ = setup([])
    raw = operations()
    raw["operationsOverview"]["assayQuality"]["passRatePercent"] = None
    data = runtime._adapt_today_operations_overview_report(raw)
    assert data["operationsOverview"]["assayQuality"]["passRatePercent"] is None


def test_failed_composition_statuses_are_displayed_in_user_language():
    runtime, _, _, _ = setup([])
    answer = runtime._sanitize_llm_answer("出现 TOOL_ERROR，原因为 PERMISSION_DENIED。")
    assert answer == "出现 查询失败，原因为 权限不足。"


def test_composition_export_is_a_scope_clarification_not_another_live_report(monkeypatch):
    runtime, _, model, _ = setup(successful_decisions())
    runtime.chat(chat_request("今天整体运营情况如何？"))
    client = OpenAICompatibleModelClient(Settings())
    captured = {}
    def capture(**kwargs):
        captured.update(kwargs)
        return recipe()
    monkeypatch.setattr(client, "_structured_decision", capture)
    client.route_main_agent(model.main_requests[0])
    assert "应选择 ASK_CLARIFICATION" in captured["system_prompt"]
    assert "不得重跑旧订单运营报表冒充原结果" in captured["system_prompt"]
