from datetime import datetime

import pytest

from app.assay_entry_grounding import AssayInputClarification, grounded_sample_date, validate_product_qualifiers
from app.assay_entry_preview import preview_answer_error
from app.business_time import BusinessClock, BEIJING_TIMEZONE
from app.schemas import ExpertLoopDecisionV1, MainAgentDecisionV1
from app.tools.client import MockToolClient
from tests.test_assay_entry_preview import INPUT, result
from tests.test_llm_tool_loop import ScriptedLlmModel, main_delegate, call, final, runtime_for, chat_request

CLOCK = BusinessClock(lambda: datetime(2026, 9, 29, 9, tzinfo=BEIJING_TIMEZONE))


def test_preview_does_not_embellish_sampling_date_with_time_of_day():
    assert preview_answer_error("采样日期：2026-09-28（昨天下午）。尚未保存。")
    assert preview_answer_error("采样日期为2026-09-28，尚未保存。") is None


@pytest.mark.parametrize("text", [
    "帮我记一下刚测的那批糖，色值十，酸碱度六点八，其他没测。",
    "今天测的黄冰糖，色值250", "这份样品今天测的，色值250", "昨天送样，今天出结果",
    "记录化验色值250", "昨天的化验录一下", "刚取的样", "采样日期好像是昨天",
    "今天或者昨天取的样", "上周取样", "样是今天测的",
])
def test_no_sampling_date_is_invented_from_measurement_or_uncertain_time(text):
    with pytest.raises(AssayInputClarification):
        grounded_sample_date([], text, "2026-09-29", CLOCK)


@pytest.mark.parametrize("text,expected", [
    ("昨天取的样，今天测的，色值二百五", "2026-09-28"),
    ("今天才出结果，样是前天取的", "2026-09-27"),
    ("给甲糖记一下，采样日期2026-09-23", "2026-09-23"),
    ("取样是9月28号，酸碱度六点八", "2026-09-28"),
    ("样品是昨天取的", "2026-09-28"),
])
def test_explicit_sampling_date_uses_business_clock(text, expected):
    assert grounded_sample_date([], text, expected, CLOCK) == expected


def test_short_date_answer_requires_sampling_question_and_ignores_assistant_guesses():
    question = [{"role": "assistant", "content": "这份样品是哪天取的？"}]
    assert grounded_sample_date(question, "昨天", "2026-09-28", CLOCK) == "2026-09-28"
    guessed = [{"role": "assistant", "content": "采样日期2026-09-29"}]
    with pytest.raises(AssayInputClarification):
        grounded_sample_date(guessed, "黄冰糖袋装", "2026-09-29", CLOCK)


def test_latest_correction_wins_without_overwriting_unchanged_fields():
    history = [{"role": "user", "content": "昨天取样，色值250，pH6.8"}]
    assert grounded_sample_date(history, "不对，日期改成前天", "2026-09-27", CLOCK) == "2026-09-27"
    assert grounded_sample_date(history, "色值改成260，日期不变", "2026-09-28", CLOCK) == "2026-09-28"
    with pytest.raises(AssayInputClarification):
        grounded_sample_date(history, "色值改成260", "2026-09-29", CLOCK)


def test_confirmed_answer_can_replace_earlier_uncertainty():
    history = [{"role": "user", "content": "采样日期好像是昨天"},
               {"role": "assistant", "content": "请确认具体哪天取样？"}]
    assert grounded_sample_date(history, "确认了，是昨天", "2026-09-28", CLOCK) == "2026-09-28"


@pytest.mark.parametrize("phrase,query", [("袋装那个", "黄冰糖（袋）"), ("箱装", "黄冰糖（箱）"), ("桶装的", "糖浆（桶）")])
def test_resolver_must_keep_packaging_qualifier(phrase, query):
    validate_product_qualifiers(query, phrase)
    with pytest.raises(ValueError):
        validate_product_qualifiers("黄冰糖", phrase)


def test_negated_old_packaging_does_not_block_corrected_resolution():
    validate_product_qualifiers("黄冰糖（箱）", "不是袋装，是箱装，刚才说错了")
    validate_product_qualifiers("黄冰糖（袋）", "不要箱装，选袋装")
    with pytest.raises(ValueError):
        validate_product_qualifiers("黄冰糖（箱）", "不要箱装，选袋装")


def test_model_hallucinated_date_is_rejected_and_expert_can_clarify():
    model = ScriptedLlmModel([main_delegate("assay_expert", "ASSAY_ENTRY_PREVIEW")], [
        call("resolve_products", {"query": "黄冰糖（袋）"}),
        call("preview_assay_entry", {"productRef": "CURRENT_PRODUCT", "sampleDate": "2026-09-29", "colorValue": 10}),
        ExpertLoopDecisionV1(action="ASK_CLARIFICATION", clarificationPrompt="这份样品是哪天取的？色值10已记下，其他没测的留空。", statusReason="MISSING_ENTITY"),
    ])
    tools = MockToolClient(responses={"resolve_products": {"resolutionStatus": "UNIQUE", "candidates": [{"productId": 84, "productName": "黄冰糖（袋）"}]}})
    runtime, _ = runtime_for(model, tools)
    response = runtime.chat(chat_request("刚测的黄冰糖袋装，色值十，其他没测，帮我记一下"))
    assert response.error is None and response.needsUserSelection
    assert "哪天取" in response.answer
    assert [c["toolName"] for c in tools.calls] == ["resolve_products"]


def test_request_to_save_repreviews_instead_of_pointing_to_old_card():
    args = {"productRef": "CURRENT_PRODUCT", **{k: v for k, v in INPUT.items() if k != "productId"}}
    model = ScriptedLlmModel([main_delegate("assay_expert", "ASSAY_ENTRY_PREVIEW")]*2, [
        call("resolve_products", {"query": "黄冰糖（袋）"}), call("preview_assay_entry", args),
        final("预览如下，尚未保存。", "obs_2"),
        final("尚未保存，请点击上方卡片核对并保存。", "obs_1"),
        call("preview_assay_entry", args), final("尚未保存，请核对这张新卡片后确认。", "obs_2"),
    ])
    tools = MockToolClient(responses={"resolve_products": {"resolutionStatus": "UNIQUE", "candidates": [{"productId": 84, "productName": "黄冰糖（袋）"}]},
                                   "preview_assay_entry": result()})
    runtime, _ = runtime_for(model, tools)
    first = runtime.chat(chat_request("记黄冰糖袋装化验，采样日期2026-09-23，色值268，pH0"))
    assert first.error is None
    response = runtime.chat(chat_request("就这样，帮我记上吧"))
    assert response.error is None, response.model_dump()
    assert response.cards[0].cardType == "assay_entry_preview"
    assert [c["toolName"] for c in tools.calls] == ["resolve_products", "preview_assay_entry", "preview_assay_entry"]


def test_model_understood_cancel_is_dialogue_control_not_a_new_preview():
    model = ScriptedLlmModel([MainAgentDecisionV1(action="DIRECT_ANSWER", semanticReason="SMALLTALK",
                            answer="好的，这次先不保存。", confidence=1.0)], [])
    tools = MockToolClient()
    runtime, _ = runtime_for(model, tools)
    response = runtime.chat(chat_request("算了，先别存。"))
    assert response.error is None and not response.cards
    assert response.answer == "好的，这次先不保存。"
    assert not tools.calls
