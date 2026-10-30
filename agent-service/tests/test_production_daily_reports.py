from __future__ import annotations

import pytest
from app.agents import AgentHandoffRouter
from app.daily_report_records import adapt_daily_report_records, daily_report_card, daily_report_answer_error
from app.goal_contracts import GOAL_CONTRACTS, GoalCompletionEvaluator, build_fact_envelope
from app.state_models import WarehouseAgentState
from app.state_store import deserialize_state, serialize_state
from app.tools.client import MockToolClient, ToolGatewayError
from tests.test_llm_tool_loop import ScriptedLlmModel, main_delegate, call, final, runtime_for, chat_request

TOOL = "query_production_daily_reports"
GOAL = "PRODUCTION_DAILY_REPORT_RECORDS"


def saved_report(**overrides):
    return {
        "sourceLabel": "生产日报填报记录", "dateRangeLabel": "2026-07-21", "scopeLabel": "多晶车间",
        "isEmpty": False, "page": 1, "size": 20, "total": 21, "hasMore": True,
        "overview": [], "requestedDays": 1,
        "reports": [{"reportDate": "2026-07-21", "statusLabel": "草稿", "id": 44}],
        "records": [{"reportDate": "2026-07-21", "departmentName": "多晶车间", "sectionStatusLabel": "已提交",
                     "groupName": "半成品", "itemName": "黄冰糖", "itemKindLabel": "产品", "unit": "件",
                     "dailyActual": 0, "convertedTons": None, "monthQuantity": 123,
                     "monthTons": None, "yearTons": None, "productId": 99, "status": "PREPRINTED"}],
        "limitations": ["填报值不与订单产出相加。"], **overrides,
    }


def test_saved_report_adapter_keeps_zero_null_and_drops_internal_fields():
    data = adapt_daily_report_records(saved_report())
    assert data["records"][0]["dailyActual"] == 0
    assert data["records"][0]["convertedTons"] is None
    assert data["records"][0]["monthQuantity"] == 123
    card = daily_report_card(data)
    serialized = card.model_dump_json()
    assert "productId" not in serialized and "PREPRINTED" not in serialized
    assert card.cardType == "production_daily_records"
    assert data["returnedRecordCount"] == 1
    assert data["size"] == 20


def test_daily_report_contract_is_owned_by_production_only():
    assert GOAL_CONTRACTS[GOAL].ownerExpert == "production_expert"
    assert GOAL_CONTRACTS[GOAL].allowedTools == (TOOL,)
    assert TOOL not in AgentHandoffRouter().profile("analytics_expert").allowed_tools
    fact = build_fact_envelope(goal_type=GOAL, tool_name=TOOL, arguments={},
                               safe_data=adapt_daily_report_records(saved_report()), entity_contexts={})
    assert fact.status == "AVAILABLE"


def test_overview_uses_server_totals_without_deriving_from_detail_page():
    data = adapt_daily_report_records(saved_report(overview=[{
        "summaryType": "全厂产量", "label": "总产量", "quantity": 120, "unit": "件",
        "convertedTons": None, "quantityRows": 2, "recordCount": 3, "observedDays": 2,
        "metricCode": "FACTORY_TOTAL_OUTPUT", "internalId": 42,
    }], requestedDays=3))
    assert data["overview"][0]["quantity"] == 120
    assert data["records"][0]["dailyActual"] == 0
    assert data["overview"][0]["convertedTons"] is None
    serialized = daily_report_card(data).model_dump_json()
    assert "FACTORY_TOTAL_OUTPUT" not in serialized and "internalId" not in serialized
    assert "daily_records_overview" in serialized


def test_daily_report_card_stays_within_gateway_field_limit():
    data = adapt_daily_report_records(saved_report(
        reports=[{} for _ in range(31)], records=[{} for _ in range(50)],
        overview=[{} for _ in range(10)], limitations=["口径" for _ in range(20)]))
    assert len(daily_report_card(data).fields) == 100


def test_model_expert_tool_analysis_chain_normalizes_today_and_remembers_page():
    model = ScriptedLlmModel(
        [main_delegate("production_expert", GOAL), main_delegate("production_expert", GOAL)],
        [call(TOOL, {"startDate": "2020-01-01", "endDate": "2020-01-01", "departmentName": "多晶车间"}),
         final("日报是草稿，黄冰糖当日填报0件，折吨未填报；还有下一页。", "obs_1"),
         call(TOOL, {"startDate": "2026-07-21", "endDate": "2026-07-21", "departmentName": "多晶车间", "page": 2}),
         final("这是相同日期与车间的第2页日报填报明细。", "obs_1")])
    tools = MockToolClient({TOOL: saved_report()})
    runtime, store = runtime_for(model, tools, allowed_experts=("production_expert",))
    response = runtime.chat(chat_request("查看今天多晶车间的生产日报"))
    assert response.error is None
    assert tools.calls[0]["arguments"]["startDate"] == "2026-07-21"
    assert len(model.main_requests) == 1 and len(model.expert_requests) == 2
    assert response.cards[0].cardType == "production_daily_records"
    assert response.reviewTrace["goalCompletion"]["status"] == "COMPLETE"
    assert "PREPRINTED" not in response.model_dump_json()
    runtime.chat(chat_request("下一页"))
    assert model.main_requests[-1].selectedContext["LAST_DAILY_REPORT_QUERY"]["page"] == 1
    assert tools.calls[-1]["arguments"]["page"] == 2
    restored = deserialize_state(serialize_state(store.get("agt_llm")))
    assert restored.last_daily_report_query["page"] == 2


@pytest.mark.parametrize("patch", [
    {"startDate": "2026-01-01", "endDate": "2026-02-01"},
    {"startDate": "2026-07-22", "endDate": "2026-07-21"},
    {"departmentName": "不存在的车间"}, {"page": 0}, {"size": 51}, {"page": True},
])
def test_daily_report_query_rejects_invalid_range_or_filters(patch):
    runtime, _ = runtime_for(ScriptedLlmModel([], []), MockToolClient(), allowed_experts=("production_expert",))
    with pytest.raises(ValueError):
        runtime.argument_builder.validate_llm_arguments(
            tool_name=TOOL, arguments={"startDate": "2026-07-21", "endDate": "2026-07-21", **patch},
            state=WarehouseAgentState(), user_message="按给定条件查询日报")


def test_header_only_report_is_not_misrepresented_as_missing_or_zero_output():
    runtime, _ = runtime_for(ScriptedLlmModel([], []), MockToolClient())
    data = adapt_daily_report_records(saved_report(records=[], total=0))
    assert runtime._llm_result_status(TOOL, data) == "AVAILABLE"
    assert runtime._llm_result_status(TOOL, {**data, "isEmpty": True, "reports": []}) == "NO_DATA"
    assert "不代表现场产量为0" in runtime._no_data_answer(TOOL)


def test_overview_does_not_expose_scoped_timestamp_as_system_freshness():
    runtime, _ = runtime_for(ScriptedLlmModel([], []), MockToolClient())
    data = runtime._adapt_today_operations_overview_report({
        "latestRecordAt": "2026-05-12T14:53:43", "dataAsOf": "2026-09-08T04:59:41",
    })
    assert "latestRecordAt" not in data
    assert "2026-05-12" not in str(data)
    assert "不提供全系统最新业务记录时间" in data["recordTimeScopeLabel"]
    assert data["dataAsOf"] == "2026-09-08T04:59:41"


@pytest.mark.parametrize("patch", [
    {"startDate": "2026-07-20", "endDate": "2026-07-20"},
    {"departmentName": "红糖车间"}, {"itemQuery": "白冰糖"}, {"size": 50},
    {"departmentName": None, "itemQuery": None},
])
def test_scope_change_resets_carried_page_without_changing_model_scope(patch):
    runtime, _ = runtime_for(ScriptedLlmModel([], []), MockToolClient())
    old = {"startDate": "2026-07-21", "endDate": "2026-07-21", "departmentName": "多晶车间",
           "itemQuery": "黄冰糖", "page": 3, "size": 20}
    state = WarehouseAgentState(last_daily_report_query=old)
    result = runtime.argument_builder.validate_llm_arguments(
        tool_name=TOOL, arguments={**old, **patch}, state=state, user_message="按新的范围查询")
    assert result["page"] == 1
    for key, value in patch.items():
        assert result.get(key) == value
    assert state.last_daily_report_query == old


@pytest.mark.parametrize("message", ["改查昨天第3页", "改查昨天第三页"])
def test_explicit_page_request_is_preserved_when_date_changes(message):
    runtime, _ = runtime_for(ScriptedLlmModel([], []), MockToolClient())
    old = {"startDate": "2026-07-21", "endDate": "2026-07-21", "page": 3, "size": 20}
    result = runtime.argument_builder.validate_llm_arguments(
        tool_name=TOOL, arguments=old, state=WarehouseAgentState(last_daily_report_query=old),
        user_message=message)
    assert result["startDate"] == result["endDate"] == "2026-07-20"
    assert result["page"] == 3


def test_generic_scope_values_do_not_become_literal_database_filters():
    runtime, _ = runtime_for(ScriptedLlmModel([], []), MockToolClient())
    result = runtime.argument_builder.validate_llm_arguments(
        tool_name=TOOL, arguments={"startDate": "2026-07-21", "endDate": "2026-07-21",
                                  "departmentName": "全部车间", "itemQuery": "全部产品"},
        state=WarehouseAgentState(), user_message="看全部车间的全部产品")
    assert "departmentName" not in result and "itemQuery" not in result


def test_factory_summary_and_real_product_filter_are_not_cleared_as_generic():
    runtime, _ = runtime_for(ScriptedLlmModel([], []), MockToolClient())
    result = runtime.argument_builder.validate_llm_arguments(
        tool_name=TOOL, arguments={"startDate": "2026-07-21", "endDate": "2026-07-21",
                                  "departmentName": "全厂汇总", "itemQuery": "总产量"},
        state=WarehouseAgentState(), user_message="看全厂汇总的总产量")
    assert result["departmentName"] == "全厂汇总" and result["itemQuery"] == "总产量"


def test_date_followup_uses_model_decision_then_normalizes_stale_page():
    old = {"startDate": "2026-07-21", "endDate": "2026-07-21", "departmentName": "多晶车间", "page": 3}
    model = ScriptedLlmModel([main_delegate("production_expert", GOAL)],
                            [call(TOOL, old), final("根据生产日报，这是昨天多晶车间的生产情况。", "obs_1")])
    runtime, store = runtime_for(model, MockToolClient({TOOL: saved_report()}), allowed_experts=("production_expert",))
    store.get("agt_llm").last_daily_report_query = {**old, "size": 20}
    response = runtime.chat(chat_request("改成昨天"))
    assert response.error is None
    assert len(model.main_requests) == 1 and len(model.expert_requests) == 2
    assert store.get("agt_llm").last_daily_report_query["startDate"] == "2026-07-20"
    assert store.get("agt_llm").last_daily_report_query["page"] == 1


@pytest.mark.parametrize("code", ["UPSTREAM_PERMISSION_DENIED", "UPSTREAM_TIMEOUT", "UPSTREAM_SERVER_ERROR"])
def test_failed_daily_query_is_not_empty_data_and_retry_uses_fresh_tool_result(code):
    query = {"startDate": "2026-07-21", "endDate": "2026-07-21"}
    model = ScriptedLlmModel([main_delegate("production_expert", GOAL), main_delegate("production_expert", GOAL)],
                            [call(TOOL, query), final("没有查询到生产数据。", "obs_1"),
                             final("没有查询到生产数据。", "obs_1")])
    tools = MockToolClient({TOOL: ToolGatewayError(code, "test failure", retryable=False)})
    runtime, store = runtime_for(model, tools, allowed_experts=("production_expert",))
    response = runtime.chat(chat_request("今天产量如何"))
    assert response.error is not None
    assert "没有查询到生产数据" not in response.answer
    assert not store.get("agt_llm").last_daily_report_query
    # Discard unconsumed scripted answers: the next request has a fresh tool observation.
    model.expert_decisions = [call(TOOL, query), final("根据生产日报，黄冰糖已记录0件。", "obs_1")]
    tools.responses[TOOL] = saved_report()
    retry = runtime.chat(chat_request("重试今天的生产查询"))
    assert retry.error is None
    assert len(tools.calls) == 2
    assert store.get("agt_llm").last_daily_report_query["startDate"] == "2026-07-21"


def test_daily_tonnage_limit_does_not_contradict_server_period_aggregate():
    data = adapt_daily_report_records(saved_report(limitations=[
        "空值表示未填报，不等于0；折吨及月/年累计均为已保存填报值，不重新换算或累加。",
        "草稿已包含。",
    ]))
    assert "概况可按日期范围合计" in data["limitations"][0]
    assert "月/年累计仅展示原值，不再累加" in data["limitations"][0]
    assert data["limitations"][1] == "草稿已包含。"


def test_scope_change_does_not_sanitize_invalid_page_into_valid_request():
    runtime, _ = runtime_for(ScriptedLlmModel([], []), MockToolClient())
    state = WarehouseAgentState(last_daily_report_query={"startDate": "2026-07-20", "page": 2})
    with pytest.raises(ValueError):
        runtime.argument_builder.validate_llm_arguments(
            tool_name=TOOL, arguments={"startDate": "2026-07-21", "endDate": "2026-07-21", "page": 0},
            state=state, user_message="今天产量")


@pytest.mark.parametrize("answer", [
    "如有车间已填报但未提交，也不会被计入。", "草稿不计入统计。",
    "未提交行未计入合计。", "草稿没有纳入统计。", "未提交行没有被计入。",
    "仅统计已提交的日报。", "已提交的日报才会纳入统计。",
])
def test_daily_policy_rejects_claim_that_saved_drafts_are_excluded(answer):
    assert daily_report_answer_error(answer) is not None


@pytest.mark.parametrize("answer", [
    "草稿已计入，但尚未提交。", "未提交不会影响已保存数据的统计。",
    "未提交并非不计入，已保存的数据已包含。", "根据生产日报，总产量300件，含草稿数据。",
    "未提交不等于未计入，草稿已纳入统计。",
])
def test_daily_policy_allows_correct_draft_descriptions(answer):
    assert daily_report_answer_error(answer) is None


@pytest.mark.parametrize("answer", [
    "统计依据为车间填报的生产日报（含未提交草稿），不与订单登记产出相加，也不做跨日累计。",
    "当日产量不能跨日合计。", "日报不支持跨日汇总。", "当日折吨不进行跨日累加。",
])
def test_daily_policy_rejects_blanket_cross_day_aggregation_ban(answer):
    assert "月/年累计快照" in daily_report_answer_error(answer)


@pytest.mark.parametrize("answer", [
    "当日实绩按日期范围合计，月/年累计值不做跨日累计。",
    "当日折吨可合计；月累计不跨日累加。",
    "没有数据，不能跨日合计。", "因缺少数据不能跨日合计。",
    "这不代表不能跨日合计。", "月累计不应跨日求和。",
])
def test_daily_policy_preserves_snapshot_and_missing_data_boundaries(answer):
    assert daily_report_answer_error(answer) is None


def test_expert_corrects_aggregation_policy_using_same_observation():
    model = ScriptedLlmModel([main_delegate("production_expert", GOAL)], [
        call(TOOL, {"startDate": "2026-07-21", "endDate": "2026-07-21"}),
        final("日报不做跨日累计。", "obs_1"),
        final("当日实绩可按查询范围合计；月/年累计值不重复相加。", "obs_1"),
    ])
    tools = MockToolClient({TOOL: saved_report()})
    runtime, _ = runtime_for(model, tools, allowed_experts=("production_expert",))
    response = runtime.chat(chat_request("今天产量如何？"))
    assert response.error is None
    assert "当日实绩可按查询范围合计" in response.answer
    assert len(tools.calls) == 1 and len(model.expert_requests) == 3
    assert model.expert_requests[-1].observations[-1]["status"] == "PLAN_REJECTED"


def test_expert_corrects_draft_policy_without_requerying_or_changing_architecture():
    model = ScriptedLlmModel([main_delegate("production_expert", GOAL)], [
        call(TOOL, {"startDate": "2026-07-21", "endDate": "2026-07-21"}),
        final("草稿不会被计入。", "obs_1"),
        final("根据生产日报，已保存的草稿也包含在查询中。", "obs_1"),
    ])
    tools = MockToolClient({TOOL: saved_report()})
    runtime, _ = runtime_for(model, tools, allowed_experts=("production_expert",))
    response = runtime.chat(chat_request("今天产量如何？"))
    assert response.error is None
    assert "草稿也包含" in response.answer
    assert len(tools.calls) == 1 and len(model.expert_requests) == 3
    assert model.expert_requests[-1].observations[-1]["status"] == "PLAN_REJECTED"


def test_daily_goal_requires_each_requested_query_not_just_one_fact_type():
    fact = build_fact_envelope(goal_type=GOAL, tool_name=TOOL,
        arguments={"startDate": "2026-07-21", "endDate": "2026-07-21", "itemQuery": "电"},
        safe_data=saved_report(), entity_contexts={})
    completion = GoalCompletionEvaluator().evaluate(goal_type=GOAL, entity_contexts={}, facts=[fact],
        required_query_items=["电", "结晶率"])
    assert completion.status == "PARTIAL"
    assert completion.missingQueries == ["结晶率"]


def test_two_daily_queries_keep_tools_open_until_covered_and_keep_both_cards():
    main = main_delegate("production_expert", GOAL).model_copy(update={"dailyReportItems": ["电", "结晶率"]})
    query = {"startDate": "2026-07-21", "endDate": "2026-07-21"}
    model = ScriptedLlmModel([main], [call(TOOL, {**query, "itemQuery": "电"}),
        call(TOOL, {**query, "itemQuery": "结晶率"}), final("根据生产日报，耗电与结晶率分别如下。", "obs_1", "obs_2")])
    tools = MockToolClient({TOOL: saved_report()})
    runtime, store = runtime_for(model, tools, allowed_experts=("production_expert",))
    response = runtime.chat(chat_request("今天耗电和结晶率如何？"))
    assert response.error is None
    assert len(tools.calls) == 2 and len(response.cards) == 2
    assert model.expert_requests[1].registeredGoalComplete is False
    assert TOOL in model.expert_requests[1].toolSchemas
    assert model.expert_requests[2].registeredGoalComplete is True
    assert not model.expert_requests[2].toolSchemas
    assert response.reviewTrace["goalCompletion"]["status"] == "COMPLETE"
    restored = deserialize_state(serialize_state(store.get("agt_llm")))
    assert restored.goal_query_items == ["电", "结晶率"]
    assert restored.last_daily_report_query is None
    assert len(restored.last_daily_report_queries) == 2
    runtime._begin_registered_goal(restored, "CURRENT_PENDING_TASKS")
    assert restored.goal_query_items == []


@pytest.mark.parametrize("patch", [{"startDate": "2026-07-20"}, {"departmentName": "多晶车间"}, {"page": 2}, {"size": 50}, {"itemQuery": "电"}, {"itemQuery": ""}])
def test_multi_daily_coverage_does_not_accept_wrong_or_duplicate_scope(patch):
    query = {"startDate": "2026-07-21", "endDate": "2026-07-21", "page": 1, "size": 20}
    facts = [build_fact_envelope(goal_type=GOAL, tool_name=TOOL, arguments={**query, "itemQuery": item, **extra},
        safe_data=saved_report(), entity_contexts={}) for item, extra in [("电", {}), ("结晶率", patch)]]
    result = GoalCompletionEvaluator().evaluate(goal_type=GOAL, entity_contexts={}, facts=facts, required_query_items=["电", "结晶率"])
    assert result.status == "PARTIAL" and "结晶率" in result.missingQueries


def test_multi_daily_no_data_is_scoped_and_latest_failure_does_not_hide_other_evidence():
    query = {"startDate": "2026-07-21", "endDate": "2026-07-21"}
    facts = [build_fact_envelope(goal_type=GOAL, tool_name=TOOL, arguments={**query, "itemQuery": item},
        safe_data=saved_report(isEmpty=True, records=[], overview=[]), entity_contexts={}) for item in ["电", "结晶率"]]
    evaluator = GoalCompletionEvaluator()
    assert evaluator.evaluate(goal_type=GOAL, entity_contexts={}, facts=facts[:1], required_query_items=["电", "结晶率"]).status == "PARTIAL"
    complete = evaluator.evaluate(goal_type=GOAL, entity_contexts={}, facts=facts, required_query_items=["电", "结晶率"])
    assert complete.status == "COMPLETE" and len(complete.evidenceRefs) == 2
    facts[-1] = facts[-1].model_copy(update={"status": "TOOL_ERROR"})
    partial = evaluator.evaluate(goal_type=GOAL, entity_contexts={}, facts=facts, required_query_items=["电", "结晶率"])
    assert partial.status == "PARTIAL" and len(partial.evidenceRefs) == 1


@pytest.mark.parametrize("action", ["FINAL_ANSWER", "PARTIAL_ANSWER"])
def test_multi_daily_cannot_finish_on_first_empty_query(action):
    main = main_delegate("production_expert", GOAL).model_copy(update={"dailyReportItems": ["电", "结晶率"]})
    query = {"startDate": "2026-07-21", "endDate": "2026-07-21"}
    early = final("根据生产日报，耗电没有记录。", "obs_1").model_copy(update={"action": action})
    model = ScriptedLlmModel([main], [call(TOOL, {**query, "itemQuery": "电"}), early,
        call(TOOL, {**query, "itemQuery": "结晶率"}), final("根据生产日报，这两项都没有记录。", "obs_1", "obs_3")])
    tools = MockToolClient({TOOL: saved_report(isEmpty=True, records=[], overview=[])})
    runtime, _ = runtime_for(model, tools, allowed_experts=("production_expert",))
    response = runtime.chat(chat_request("今天耗电和结晶率如何？"))
    assert response.error is None and len(tools.calls) == 2
    assert model.expert_requests[2].observations[-1]["status"] == "PLAN_REJECTED"


@pytest.mark.parametrize("items", [["电"] * 2, [" "], ["电", "水", "率", "产量"]])
def test_main_daily_requirements_are_bounded_distinct_and_nonblank(items):
    from app.schemas import MainAgentDecisionV1
    with pytest.raises(ValueError):
        MainAgentDecisionV1.model_validate({**main_delegate("production_expert", GOAL).model_dump(), "dailyReportItems": items})


def test_main_daily_requirements_cannot_expand_other_goal():
    from app.schemas import MainAgentDecisionV1
    with pytest.raises(ValueError):
        MainAgentDecisionV1.model_validate({**main_delegate("inventory_expert", "CURRENT_PRODUCT_INVENTORY").model_dump(), "dailyReportItems": ["电"]})
