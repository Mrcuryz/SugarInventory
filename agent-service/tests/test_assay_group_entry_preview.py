import json
import pytest

from app.assay_entry_preview import adapt_group_preview, validate_preview_arguments, preview_answer_error
from app.graph.state import SelectedEntity, WarehouseAgentState
from app.schemas import ResumeRequest, ExpertLoopDecisionV1
from app.state_store import serialize_state, deserialize_state
from app.tool_arguments import ToolArgumentBuilder
from app.tools.client import MockToolClient
from tests.test_assay_entry_preview import result
from tests.test_llm_tool_loop import ScriptedLlmModel, main_delegate, call, final, runtime_for, chat_request

REF = "aer_selected-group.signature"
INPUT = {"groupRef": REF, "sampleDate": "2026-09-23", "colorValue": 268, "phValue": 0}
MODEL_INPUT = {**INPUT, "groupRef": "CURRENT_ASSAY_GROUP"}


def group_result(count=2):
    return {"previewOnly": True, "confirmationAvailable": True, "groupId": 9, "groupRef": REF,
            "groupName": "冰糖组", "sampleDate": "2026-09-23", "productCount": count,
            "products": [{**result(), "productId": index + 84, "productName": f"产品{index + 1}",
                          "proposedVersion": index + 1} for index in range(count)], "warnings": []}


def catalog():
    return {"dataScope": "CURRENT_ASSAY_GROUP_CATALOG", "total": 2, "page": 1, "size": 20,
            "records": [{"groupName": "冰糖组", "groupRef": REF, "productNames": ["产品1", "产品2"]},
                        {"groupName": "其他组", "groupRef": "aer_other.signature", "productNames": ["产品3"]}]}


def select(runtime, store):
    pending = store.get("agt_llm").pending_clarification
    return runtime.resume(ResumeRequest.model_validate({
        "agentSessionId": "agt_llm", "messageId": "msg_group_resume", "resumeToken": pending.resume_token,
        "event": {"type": "candidate_selected", "interruptId": pending.interrupt_id,
                  "action": "SELECT_OPTION", "selection": {"optionId": pending.options[0]["optionId"]},
                  "clientRequestId": "assay-group-select-1"},
        "client": {"traceId": "trace_group", "requestId": "req_group", "debug": True},
    }))


@pytest.mark.parametrize("changes", [{"groupRef": "9"}, {"groupRef": 9}, {"groupRef": None},
                                     {"groupId": 9}, {"productId": 84}, {"phValue": "0"}])
def test_invalid_or_mixed_identity_is_rejected(changes):
    with pytest.raises(ValueError):
        validate_preview_arguments({**INPUT, **changes})


def test_group_is_bound_only_to_selected_runtime_reference_and_active_goal():
    builder = ToolArgumentBuilder()
    state = WarehouseAgentState(active_goal_type="ASSAY_ENTRY_PREVIEW")
    build = lambda args: builder.validate_llm_arguments(tool_name="preview_assay_entry", arguments=args, state=state, user_message="录入冰糖组化验，采样日期2026-09-23")
    with pytest.raises(ValueError):
        build(MODEL_INPUT)
    state.selected_assay_group = SelectedEntity(None, "冰糖组", "user_selection", {"groupRef": REF}, entity_type="ASSAY_GROUP", entity_ref="CURRENT_ASSAY_GROUP")
    assert build(MODEL_INPUT) == INPUT
    with pytest.raises(ValueError):
        build(INPUT)  # Even a correctly shaped signed value must not come from the model.
    state.active_goal_type = "ASSAY_REFERENCE_DATA"
    with pytest.raises(ValueError):
        build(MODEL_INPUT)


@pytest.mark.parametrize("variant", ["ref", "count", "duplicate", "date", "measurement", "missingMetric", "version", "write"])
def test_group_preview_rejects_any_partial_or_changed_member(variant):
    data = group_result()
    if variant == "ref": data["groupRef"] = "aer_other.signature"
    if variant == "count": data["productCount"] = 3
    if variant == "duplicate": data["products"][1]["productId"] = 84
    if variant == "date": data["products"][1]["sampleDate"] = "2026-09-22"
    if variant == "measurement": data["products"][1]["metrics"][0]["value"] = 269
    if variant == "missingMetric": data["products"][1]["metrics"].pop()
    if variant == "version": data["products"][1]["proposedVersion"] = 0
    if variant == "write": data["products"][1]["previewOnly"] = False
    with pytest.raises(ValueError):
        adapt_group_preview(data, INPUT)


def test_group_preserves_zero_null_and_each_independent_judgement():
    data = group_result()
    data["products"][1].update(judgementLabel="不合格", failedMetrics=[{"name": "干燥失重", "reason": "缺少检测值"}])
    adapted = adapt_group_preview(data, INPUT)
    assert adapted["products"][0]["metrics"][-1]["value"] == 0
    assert adapted["products"][0]["metrics"][2]["value"] is None
    assert adapted["products"][1]["judgementLabel"] == "不合格"
    assert preview_answer_error("未保存，未填项不参与判定", adapted)
    assert "groupRef" not in json.dumps(adapted) and "productId" not in json.dumps(adapted)


def test_group_guidance_names_the_available_human_save_entry():
    adapted = adapt_group_preview(group_result(), INPUT)
    assert "核对并保存" in adapted["confirmationGuidance"]
    assert "页面已支持人工保存" in adapted["confirmationGuidance"]
    assert all("confirmationGuidance" not in item for item in adapted["products"])
    assert preview_answer_error("本次结果全部为预览，尚未保存；当前没有保存能力，模型不会代为保存。", adapted)
    assert preview_answer_error("尚未保存，模型没有保存能力；可点击核对并保存，由你确认。", adapted) is None
    disabled = group_result()
    disabled["confirmationAvailable"] = False
    assert "尚未开放人工保存入口" in adapt_group_preview(disabled, INPUT)["confirmationGuidance"]


def test_group_inaccurate_save_availability_is_reanalysed_without_repeating_tools():
    model = ScriptedLlmModel([main_delegate("assay_expert", "ASSAY_ENTRY_PREVIEW")], [
        call("query_assay_groups", {"groupName": "冰糖"}), call("preview_assay_entry", MODEL_INPUT),
        final("尚未保存；当前没有保存能力，模型不会代为保存。", "obs_3"),
        final("尚未保存，可点击核对并保存，由你核对整组后确认。", "obs_3")])
    tools = MockToolClient(responses={"query_assay_groups": catalog(), "preview_assay_entry": group_result()})
    runtime, store = runtime_for(model, tools)
    runtime.chat(chat_request("录入冰糖组化验，采样日期2026-09-23，色值268，pH值0"))
    response = select(runtime, store)
    assert response.error is None, response.model_dump()
    assert "核对并保存" in response.answer and "当前没有保存能力" not in response.answer
    assert [item["toolName"] for item in tools.calls] == ["query_assay_groups", "preview_assay_entry"]


@pytest.mark.parametrize("count", [2, 51])
def test_model_selects_real_group_then_resumes_same_expert_with_complete_card(count):
    model = ScriptedLlmModel([main_delegate("assay_expert", "ASSAY_ENTRY_PREVIEW")], [
        call("query_assay_groups", {"groupName": "冰糖"}),
        call("preview_assay_entry", MODEL_INPUT), final("整组化验预览如下，尚未保存。", "obs_3")])
    tools = MockToolClient(responses={"query_assay_groups": catalog(), "preview_assay_entry": group_result(count)})
    runtime, store = runtime_for(model, tools)
    first = runtime.chat(chat_request("录入冰糖组化验，采样日期2026-09-23，色值268，pH值0"))
    assert first.error is None, first.model_dump()
    assert first.needsUserSelection
    assert REF not in first.model_dump_json()
    assert store.get("agt_llm").pending_clarification.kind == "assay_group"
    response = select(runtime, store)
    assert response.error is None, response.model_dump()
    assert not response.needsUserSelection
    card = response.cards[0]
    assert card.cardType == "assay_entry_preview"
    assert card.fields[0]["entryKind"] == "GROUP_V1"
    assert len(card.fields[0]["products"]) == count
    assert card.fields[1]["entryInput"]["groupRef"] == REF
    assert "productId" not in card.model_dump_json()
    assert REF not in json.dumps(model.expert_requests[-1].observations)
    assert len(model.expert_requests[-1].observations[-1]["data"]["products"]) == count
    assert [item["toolName"] for item in tools.calls] == ["query_assay_groups", "preview_assay_entry"]
    assert tools.calls[-1]["arguments"] == INPUT
    assert store.get("agt_llm").last_goal_completion["status"] == "COMPLETE"


def test_selected_group_missing_date_asks_without_preview_or_repeat_catalog():
    model = ScriptedLlmModel([main_delegate("assay_expert", "ASSAY_ENTRY_PREVIEW")], [
        call("query_assay_groups", {"groupName": "冰糖"}),
        ExpertLoopDecisionV1(action="ASK_CLARIFICATION", clarificationPrompt="这次采样日期是哪一天？", statusReason="MISSING_ENTITY")])
    tools = MockToolClient(responses={"query_assay_groups": catalog()})
    runtime, store = runtime_for(model, tools)
    runtime.chat(chat_request("录入冰糖组化验，色值268"))
    response = select(runtime, store)
    assert "采样日期" in response.answer
    assert [item["toolName"] for item in tools.calls] == ["query_assay_groups"]


def test_followup_changes_measurement_without_reselecting_group_or_losing_zero():
    revised = {**MODEL_INPUT, "colorValue": 270}
    model = ScriptedLlmModel([main_delegate("assay_expert", "ASSAY_ENTRY_PREVIEW"),
                             main_delegate("assay_expert", "ASSAY_ENTRY_PREVIEW")], [
        call("query_assay_groups", {"groupName": "冰糖"}), call("preview_assay_entry", MODEL_INPUT),
        final("整组预览如下，尚未保存。", "obs_3"), call("preview_assay_entry", revised),
        final("色值已改为270，pH仍为0，重新预览如下，尚未保存。", "obs_1")])
    def preview(arguments):
        data = group_result()
        for product in data["products"]:
            product["metrics"][0]["value"] = arguments["colorValue"]
        return data
    tools = MockToolClient(responses={"query_assay_groups": catalog(), "preview_assay_entry": preview})
    runtime, store = runtime_for(model, tools)
    runtime.chat(chat_request("录入冰糖组化验，采样日期2026-09-23，色值268，pH0"))
    select(runtime, store)
    response = runtime.chat(chat_request("色值改成270，其他不变"))
    assert response.error is None, response.model_dump()
    assert [item["toolName"] for item in tools.calls] == ["query_assay_groups", "preview_assay_entry", "preview_assay_entry"]
    assert tools.calls[-1]["arguments"] == {**INPUT, "colorValue": 270}
    assert response.cards[0].fields[1]["entryInput"]["values"]["phValue"] == 0


def test_group_directory_query_does_not_turn_into_entry_selection():
    model = ScriptedLlmModel([main_delegate("assay_expert", "ASSAY_REFERENCE_DATA")], [
        call("query_assay_groups", {}), final("已登记冰糖组和其他组。", "obs_1")])
    tools = MockToolClient(responses={"query_assay_groups": catalog()})
    runtime, store = runtime_for(model, tools)
    response = runtime.chat(chat_request("有哪些化验组？"))
    assert response.error is None, response.model_dump()
    assert not response.needsUserSelection
    assert store.get("agt_llm").pending_clarification is None
    assert REF not in json.dumps(model.expert_requests[-1].observations)
    assert [item["toolName"] for item in tools.calls] == ["query_assay_groups"]


def test_group_reference_state_survives_restart_without_leaking_into_model_context():
    state = WarehouseAgentState()
    state.selected_assay_group = SelectedEntity(None, "冰糖组", "user_selection", {"groupRef": REF}, entity_type="ASSAY_GROUP", entity_ref="CURRENT_ASSAY_GROUP")
    restored = deserialize_state(serialize_state(state))
    assert restored.selected_assay_group.metadata["groupRef"] == REF
    runtime, _ = runtime_for(ScriptedLlmModel([], []), MockToolClient())
    assert "groupRef" not in runtime._llm_safe_tool_data({"records": [{"groupRef": REF}]})["records"][0]
    assert runtime._llm_selection_rediscovery_error("query_assay_groups", {}, [
        {"tool": "USER_SELECTION", "status": "AVAILABLE", "data": {"entityType": "ASSAY_GROUP"}}])
