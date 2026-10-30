import json
import pytest

from app.assay_entry_preview import METRICS, adapt_preview, preview_card, validate_preview_arguments, preview_answer_error
from app.graph.state import SelectedEntity, WarehouseAgentState
from app.tool_arguments import ToolArgumentBuilder
from app.tools.client import MockToolClient, ToolGatewayError
from app.schemas import ExpertLoopDecisionV1, ResumeRequest
from tests.test_llm_tool_loop import ScriptedLlmModel, main_delegate, call, final, runtime_for, chat_request

INPUT = {"productId": 84, "sampleDate": "2026-09-23", "colorValue": 268, "phValue": 0}


def result():
    return {"previewOnly": True, "productId": 84, "productName": "黄冰糖（袋）",
            "sampleDate": "2026-09-23", "existingVersion": None, "proposedVersion": 1,
            "judgementLabel": "无标准", "warnings": ["本次未保存"], "failedMetrics": [],
            "metrics": [{"field": key, "name": name, "value": INPUT.get(key),
                         "provided": INPUT.get(key) is not None, "unit": None}
                        for key, name in METRICS.items()]}


@pytest.mark.parametrize("changes", [
    {"productId": True}, {"productId": 0}, {"productId": "84"}, {"sampleDate": "2026-09-31"},
    {"sampleDate": "20260923"}, {"sampleDate": None}, {"phValue": "0"}, {"colorValue": True},
    {"colorValue": float("nan")}, {"phValue": float("inf")}, {"selectType": 2},
    {"colorValue": None, "phValue": None},
])
def test_invalid_input_rejected(changes):
    with pytest.raises(ValueError):
        validate_preview_arguments({**INPUT, **changes})


def test_precision_is_not_changed_by_agent():
    source = {**INPUT, "colorValue": 268.001, "dryWeight": None}
    assert validate_preview_arguments(source) == source


def test_preview_has_only_user_facing_values_and_missing_is_not_zero():
    data = adapt_preview({**result(), "executionToken": "secret"}, INPUT)
    assert data["metrics"][-1]["value"] == 0
    assert data["metrics"][2]["value"] is None
    assert not data["metrics"][2]["provided"]
    dumped = preview_card(data).model_dump_json()
    assert 'productId' not in dumped and 'executionToken' not in dumped
    assert 'colorValue' not in dumped


def test_confirmation_guidance_does_not_confuse_human_save_with_model_write():
    available = {"confirmationAvailable": True}
    assert preview_answer_error("尚未保存，当前没有可用的保存操作", available)
    assert preview_answer_error("尚未保存，请点击核对并保存按钮，由你确认。", available) is None
    assert preview_answer_error("尚未保存，当前尚不支持保存", {"confirmationAvailable": False}) is None
    assert preview_answer_error("尚未保存，如需入库请点击核对并保存", available)
    assert preview_answer_error("尚未保存，这是化验记录，不是产品入库。", available) is None


def test_human_action_input_is_separate_from_model_facts():
    data = adapt_preview(result(), INPUT)
    card = preview_card(data, INPUT, "aer_payload.signature")
    assert card.fields[1] == {"kind": "assay_entry_action", "entryInput": {"productRef": "aer_payload.signature", "values": {k: v for k, v in INPUT.items() if k != "productId"}}}
    assert 'productId' not in json.dumps(data)
    assert 'entryInput' not in json.dumps(data)
    assert len(preview_card(data).fields) == 1


@pytest.mark.parametrize("changes", [{"productId": 85}, {"sampleDate": "2026-09-24"},
                                     {"previewOnly": False}, {"metrics": []}, {"judgementLabel": "PASS"}])
def test_mismatched_preview_rejected(changes):
    with pytest.raises(ValueError):
        adapt_preview({**result(), **changes}, INPUT)


def test_changed_measurement_cannot_be_shown_as_exact_preview():
    value = result()
    value["metrics"][0]["value"] = 269
    with pytest.raises(ValueError):
        adapt_preview(value, INPUT)


def test_legacy_no_matching_standard_phrase_does_not_override_backend_failure():
    source = {**result(), "judgementLabel": "不合格", "judgementMessage": "未命中任何标准，已按默认标准/优先级选择参考标准",
              "appliedStandardName": "黄冰糖", "failedMetrics": [{"name": "干燥失重", "reason": "缺少检测值"}]}
    data = adapt_preview(source, INPUT)
    assert data["judgementLabel"] == "不合格"
    assert data["appliedStandardName"] == "黄冰糖"
    assert "缺少检测值属于未通过原因" in data["judgementNote"]
    assert "judgementMessage" not in data


def test_tool_requires_locked_assay_goal_and_specific_resolved_product():
    builder = ToolArgumentBuilder()
    state = WarehouseAgentState()
    args = {"productRef": "CURRENT_PRODUCT", "sampleDate": "2026-09-23", "colorValue": 268}
    def validate():
        return builder.validate_llm_arguments(tool_name="preview_assay_entry", arguments=args,
                                               state=state, user_message="录入化验，采样日期2026-09-23")
    with pytest.raises(ValueError):
        validate()
    state.active_goal_type = "ASSAY_ENTRY_PREVIEW"
    state.selected_product = SelectedEntity(internal_id=84, display_label="黄冰糖（袋）", source="resolver",
                                           metadata={"scopeType": "SINGLE_PRODUCT"})
    assert validate()["productId"] == 84
    state.selected_product.metadata["scopeType"] = "PRODUCT_TYPE_GROUP"
    with pytest.raises(ValueError):
        validate()


def test_missing_values_are_not_invented_as_failure_reasons():
    source = {**result(), "judgementLabel": "不合格", "failedMetrics": [{"name": "色值", "reason": "超出上限 5.00"}]}
    data = adapt_preview(source, INPUT)
    assert "缺少检测值" not in data["judgementNote"]
    assert data["failedMetrics"] == [{"name": "色值", "value": None, "unit": None, "reason": "超出上限 5.00"}]


@pytest.mark.parametrize("message", [
    "录入黄冰糖（袋）的化验，采样日期2026-09-23，色值268，pH值0",
    "录入黄冰糖（袋）的化验记录，采样日期2026-09-23，色值268，pH值0",
])
def test_main_expert_tool_analysis_chain_builds_preview_card(message):
    model = ScriptedLlmModel(
        [main_delegate("assay_expert", "ASSAY_ENTRY_PREVIEW")],
        [call("resolve_products", {"query": "黄冰糖（袋）"}),
         call("preview_assay_entry", {"productRef": "CURRENT_PRODUCT", "sampleDate": "2026-09-23", "colorValue": 268, "phValue": 0}),
         final("已生成化验预览，色值268，pH值0，其他项目未填写；当前无标准，尚未保存。", "obs_2")],
    )
    tools = MockToolClient(responses={
        "resolve_products": {"resolutionStatus": "UNIQUE", "candidates": [{"productId": 84, "productName": "黄冰糖（袋）"}]},
        "preview_assay_entry": {**result(), "confirmationAvailable": True, "productRef": "aer_payload.signature"},
    })
    runtime, _ = runtime_for(model, tools)
    response = runtime.chat(chat_request(message))
    assert response.error is None, response.model_dump()
    assert response.cards[0].cardType == "assay_entry_preview"
    assert "未保存" in response.answer
    assert len(model.main_requests) == 1
    assert len(model.expert_requests) >= 3
    assert "productId" not in json.dumps(model.expert_requests[-1].observations)
    assert response.cards[0].fields[1]["kind"] == "assay_entry_action"
    assert "entryInput" not in json.dumps(model.expert_requests[-1].observations)


def test_answer_must_not_claim_save():
    assert preview_answer_error("化验录入成功")
    assert preview_answer_error("色值268")
    assert preview_answer_error("仅预览，尚未保存") is None
    preview = {"failedMetrics": [{"name": "干燥失重", "reason": "缺少检测值"}]}
    assert preview_answer_error("缺测项未参与判定，尚未保存", preview)
    assert preview_answer_error("其余未填项因缺少检测值未作判定，尚未保存", preview)
    assert preview_answer_error("预判不合格，其中干燥失重缺少检测值，尚未保存", preview) is None


@pytest.mark.parametrize("answer", ["尚未保存，不代表已保存", "未保存，请不要视为录入成功",
                                   "尚未保存，不能说已成功录入", "未录入成功，尚未保存"])
def test_negative_save_disclaimer_is_not_a_success_claim(answer):
    assert preview_answer_error(answer) is None


@pytest.mark.parametrize("answer", ["尚未保存，但已保存", "已完成录入，未保存", "已保存，不能说未保存",
                                   "尚未保存但已保存", "不是未保存而是已保存"])
def test_contradictory_success_claim_still_rejected(answer):
    assert preview_answer_error(answer)


def test_rejected_model_prose_keeps_only_verified_preview_card():
    model = ScriptedLlmModel(
        [main_delegate("assay_expert", "ASSAY_ENTRY_PREVIEW")],
        [call("resolve_products", {"query": "黄冰糖（袋）"}),
         call("preview_assay_entry", {"productRef": "CURRENT_PRODUCT", "sampleDate": "2026-09-23", "colorValue": 268, "phValue": 0}),
         final("已保存", "obs_2"), final("录入成功", "obs_2")],
    )
    tools = MockToolClient(responses={
        "resolve_products": {"resolutionStatus": "UNIQUE", "candidates": [{"productId": 84, "productName": "黄冰糖（袋）"}]},
        "preview_assay_entry": result(),
    })
    runtime, _ = runtime_for(model, tools)
    response = runtime.chat(chat_request("录入黄冰糖（袋）化验，采样日期2026-09-23，色值268，pH0"))
    assert response.error is None
    assert "尚未保存" in response.answer
    assert "录入成功" not in response.answer and "已保存" not in response.answer
    assert response.cards[0].cardType == "assay_entry_preview"
    assert response.reviewTrace["expertLoop"]["completionMode"] == "SAFE_ASSAY_PREVIEW_CARD_FALLBACK"


@pytest.mark.parametrize("code,phrase", [
    ("ASSAY_ENTRY_INPUT_INVALID", "两位小数"),
    ("ASSAY_ENTRY_VERSION_CONFLICT", "版本存在冲突"),
    ("ASSAY_ENTRY_JUDGEMENT_UNAVAILABLE", "无法取得化验预判"),
])
def test_preview_business_error_is_actionable_without_retry_or_raw_details(code, phrase):
    model = ScriptedLlmModel(
        [main_delegate("assay_expert", "ASSAY_ENTRY_PREVIEW")],
        [call("resolve_products", {"query": "黄冰糖（袋）"}),
         call("preview_assay_entry", {"productRef": "CURRENT_PRODUCT", "sampleDate": "2026-09-23", "reducingSugar": 0.123})],
    )
    tools = MockToolClient(responses={
        "resolve_products": {"resolutionStatus": "UNIQUE", "candidates": [{"productId": 84, "productName": "黄冰糖（袋）"}]},
        "preview_assay_entry": {"error": {"code": code, "message": "private SQL detail", "retryable": False}},
    })
    runtime, _ = runtime_for(model, tools)
    response = runtime.chat(chat_request("录入黄冰糖（袋）化验，采样日期2026-09-23，还原糖0.123"))
    assert phrase in response.answer
    assert "未保存" in response.answer
    assert "private" not in response.answer
    assert response.error is None
    assert not response.cards
    assert [item["toolName"] for item in tools.calls] == ["resolve_products", "preview_assay_entry"]


@pytest.mark.parametrize("code", ["UPSTREAM_PERMISSION_DENIED", "AGENT_SCOPE_DENIED"])
def test_preview_permission_error_explains_no_save_without_retry(code):
    model = ScriptedLlmModel(
        [main_delegate("assay_expert", "ASSAY_ENTRY_PREVIEW")],
        [call("resolve_products", {"query": "黄冰糖（袋）"}),
         call("preview_assay_entry", {"productRef": "CURRENT_PRODUCT", "sampleDate": "2026-09-23", "colorValue": 20})],
    )
    tools = MockToolClient(responses={
        "resolve_products": {"resolutionStatus": "UNIQUE", "candidates": [{"productId": 84, "productName": "黄冰糖（袋）"}]},
        "preview_assay_entry": ToolGatewayError(code, "private permission detail", retryable=False),
    })
    runtime, _ = runtime_for(model, tools)
    response = runtime.chat(chat_request("录入黄冰糖（袋）化验，采样日期2026-09-23，色值20"))
    assert response.error.code == "PERMISSION_DENIED"
    assert response.answer == response.error.message
    assert "没有化验录入权限" in response.answer and "本次未保存" in response.answer
    assert "private" not in response.answer and "查询" not in response.answer
    assert not response.error.retryable and not response.cards
    assert [item["toolName"] for item in tools.calls] == ["resolve_products", "preview_assay_entry"]


def test_product_selection_resumes_preview_without_repeating_resolution():
    model = ScriptedLlmModel(
        [main_delegate("assay_expert", "ASSAY_ENTRY_PREVIEW")],
        [call("resolve_products", {"query": "黄冰糖"}),
         call("preview_assay_entry", {"productRef": "CURRENT_PRODUCT", "sampleDate": "2026-09-23", "colorValue": 268, "phValue": 0}),
         final("黄冰糖（袋）的化验预览如下，尚未保存。", "obs_3")],
    )
    tools = MockToolClient(responses={
        "resolve_products": {"resolutionStatus": "AMBIGUOUS", "options": [
            {"productId": 84, "productName": "黄冰糖（袋）", "optionType": "SINGLE_PRODUCT"},
            {"productId": 85, "productName": "黄冰糖（箱）", "optionType": "SINGLE_PRODUCT"},
            {"productType": "黄冰糖", "displayLabel": "全部黄冰糖", "optionType": "PRODUCT_TYPE_GROUP"},
        ]}, "preview_assay_entry": result(),
    })
    runtime, store = runtime_for(model, tools)
    first = runtime.chat(chat_request("录入黄冰糖化验，采样日期2026-09-23，色值268，pH值0"))
    assert first.needsUserSelection
    pending = store.get("agt_llm").pending_clarification
    assert len(pending.options) == 2
    assert all(option["supported"] for option in pending.options)
    assert first.cards[0].title == "请选择本次化验的产品"
    resumed = runtime.resume(ResumeRequest.model_validate({
        "agentSessionId": "agt_llm", "messageId": "msg_002", "resumeToken": pending.resume_token,
        "event": {"type": "candidate_selected", "interruptId": pending.interrupt_id,
                  "action": "SELECT_OPTION", "selection": {"optionId": pending.options[0]["optionId"]},
                  "clientRequestId": "assay-preview-select-1"},
        "client": {"traceId": "trace_002", "requestId": "req_002", "debug": True},
    }))
    assert resumed.error is None, resumed.model_dump()
    assert not resumed.needsUserSelection
    assert resumed.cards[0].cardType == "assay_entry_preview"
    assert [item["toolName"] for item in tools.calls] == ["resolve_products", "preview_assay_entry"]


def test_missing_sampling_date_is_clarified_without_preview_call():
    model = ScriptedLlmModel(
        [main_delegate("assay_expert", "ASSAY_ENTRY_PREVIEW")],
        [call("resolve_products", {"query": "黄冰糖（袋）"}),
         ExpertLoopDecisionV1(action="ASK_CLARIFICATION", clarificationPrompt="这次化验的采样日期是哪一天？",
                              statusReason="MISSING_ENTITY")],
    )
    tools = MockToolClient(responses={"resolve_products": {"resolutionStatus": "UNIQUE",
            "candidates": [{"productId": 84, "productName": "黄冰糖（袋）"}]}})
    runtime, _ = runtime_for(model, tools)
    response = runtime.chat(chat_request("录入黄冰糖（袋）的化验，色值268"))
    assert "采样日期" in response.answer
    assert [item["toolName"] for item in tools.calls] == ["resolve_products"]
