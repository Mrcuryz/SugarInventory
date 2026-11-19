from decimal import Decimal
import pytest
from app.product_import import compare_row, duplicate_rows, name_key, parse_values, validate_model_proposal


def catalog(**values):
    return [{"ref": "product_ref_a", "hasInventory": False, "values": {
        "productName": "黄冰糖（袋）", "weightPerPiece": 25, "piecesPerPallet": 40,
        "canStack": True, **values}}]


def test_parenthesis_only_is_not_a_rename():
    result = compare_row({"productName": " 黄冰糖(袋) "}, catalog())
    assert result["state"] == "UNCHANGED" and not result["changes"]


def test_blank_preserves_values_but_false_is_a_change():
    result = compare_row({"productName": "黄冰糖(袋)", "weightPerPiece": "", "canStack": "否"}, catalog())
    assert result["state"] == "UPDATE"
    assert result["changes"] == [{"field": "canStack", "before": True, "after": False}]


def test_decimal_equality_and_specification_change():
    assert compare_row({"productName": "黄冰糖(袋)", "weightPerPiece": "25.00"}, catalog())["state"] == "UNCHANGED"
    row = {"productName": "黄冰糖(袋)", "weightPerPiece": "14.5"}
    assert compare_row(row, catalog())["state"] == "NEEDS_CONFIRMATION"
    existing = catalog(); existing[0]["hasInventory"] = True
    assert compare_row(row, existing)["state"] == "BLOCKED_INVENTORY_SPECIFICATION"


def test_same_name_multiple_existing_products_requires_choice():
    existing = catalog() + [{**catalog()[0], "ref": "product_ref_b"}]
    assert compare_row({"productName": "黄冰糖(袋)"}, existing)["state"] == "NEEDS_CONFIRMATION"


@pytest.mark.parametrize("name", ["黄冰糖9.6(箱装)", "黄冰糖14.5(箱装)", "白冰糖(袋)", "黄冰糖(箱)"])
def test_other_specifications_and_colours_are_not_collapsed(name):
    assert compare_row({"productName": name}, catalog())["state"] == "MODEL_MATCH_REQUIRED"


def test_duplicate_source_rows_do_not_overwrite_each_other():
    assert duplicate_rows([{"productName": "黄冰糖（袋）"}, {"productName": "黄冰糖(袋)"}]) == [[0, 1]]
    assert name_key("A B") != name_key("AB")


@pytest.mark.parametrize("field,value", [("weightPerPiece", "NaN"), ("weightPerPiece", "Infinity"),
    ("weightPerPiece", "25公斤"), ("weightPerPiece", 0), ("piecesPerPallet", "1.5"),
    ("piecesPerPallet", True), ("piecesPerPallet", "2147483648"), ("canStack", "可能"),
    ("productType", "其他糖"), ("productId", 1)])
def test_invalid_values_are_not_guessed(field, value):
    with pytest.raises(ValueError): parse_values({"productName": "黄冰糖", field: value})


def test_valid_numbers_and_boolean():
    assert parse_values({"productName": "黄冰糖", "weightPerPiece": "14.50", "canStack": False}) == {
        "productName": "黄冰糖", "weightPerPiece": Decimal("14.50"), "canStack": False}


@pytest.mark.parametrize("decision,refs", [("MATCH", ["product_ref_a"]), ("NEW", []), ("UNSURE", [])])
def test_model_never_authorizes_an_operation(decision, refs):
    result = validate_model_proposal({"decision": decision, "candidateRefs": refs, "reason": "名称近似，需核对规格"}, {"product_ref_a"})
    assert not result["selectable"] and result["state"] == "NEEDS_CONFIRMATION"


@pytest.mark.parametrize("proposal", [
    {"decision": "MATCH", "candidateRefs": ["invented"], "reason": "相似"},
    {"decision": "MATCH", "candidateRefs": [], "reason": "相似"},
    {"decision": "NEW", "candidateRefs": ["product_ref_a"], "reason": "相似"},
    {"decision": "MATCH", "candidateRefs": ["product_ref_a"], "reason": "相似", "execute": True},
])
def test_model_cannot_invent_refs_or_execution(proposal):
    with pytest.raises(ValueError): validate_model_proposal(proposal, {"product_ref_a"})


def evidence(name="袋装黄冰糖", **extra):
    return {"source": {"rowNumber": 17, "values": {"productName": name, **extra}},
            "duplicateRows": [], "exactCount": 0, "allCandidatesShown": True, "candidates": catalog()}


def never(_):
    pytest.fail("不应请求模型")


def test_exact_duplicate_invalid_and_missing_inventory_do_not_request_model():
    from app.product_import import review_product_import_row as review
    data = evidence("黄冰糖（袋）")
    data["exactCount"] = 1
    assert review(data, never)["state"] == "UNCHANGED"
    data["candidates"][0]["hasInventory"] = None
    assert not review(data, never)["selectable"]
    data["duplicateRows"] = [3, 17]
    assert review(data, never)["rows"] == [3, 17]
    assert review(evidence(canStack="不清楚"), never)["state"] == "INVALID_ROW"


def test_only_empty_complete_catalog_can_offer_create_without_model():
    from app.product_import import review_product_import_row as review
    data = {**evidence(), "candidates": []}
    assert review(data, never)["missingFields"] == ["productType", "status"]
    data["source"]["values"].update(productType="黄冰糖", status="成品")
    assert review(data, never)["state"] == "CREATE"
    data["allCandidatesShown"] = False
    assert not review(data, never)["selectable"]


def test_alias_match_passes_only_local_candidate_keys_to_structured_model(monkeypatch):
    import io
    import json
    from app.config import Settings
    from app.model import OpenAICompatibleModelClient
    from app.product_import import review_product_import_row
    data = evidence(weightPerPiece="15")
    data["candidates"][0]["hasInventory"] = True
    requests = []
    def respond(request, timeout):
        payload = json.loads(request.data)
        requests.append(payload)
        assert payload["model"] == "expert-test"
        assert "product_ref_a" not in str(payload) and "hasInventory" not in str(payload)
        # First response invents a target; only the repaired, validated key can survive.
        decision = {"decision": "MATCH", "candidateRefs": ["C1" if len(requests) == 2 else "C99"],
                    "reason": "名称近似，但单件重量不一致，需人工核对。"}
        return io.BytesIO(json.dumps({"choices": [{"message": {"content": json.dumps(decision)}}]}).encode())
    monkeypatch.setattr("app.model.urlopen", respond)
    client = OpenAICompatibleModelClient(Settings(model_name="default", expert_result_model_name="expert-test",
                                                  model_base_url="http://localhost"))
    result = review_product_import_row(data, client.propose_product_match)
    assert len(requests) == 2
    assert result["proposal"]["candidateRefs"] == ["product_ref_a"]
    assert result["changes"] == [{"field":"weightPerPiece", "before":Decimal("25"), "after":Decimal("15")}]
    assert not result["selectable"] and result["targetComparison"] == "BLOCKED_INVENTORY_SPECIFICATION"


def test_incomplete_search_or_model_outage_never_offer_new():
    from app.config import Settings
    from app.model import OpenAICompatibleModelClient
    from app.product_import import review_product_import_row
    data = {**evidence(), "allCandidatesShown": False}
    result = review_product_import_row(data, lambda _: {"decision":"NEW","candidateRefs":[],"reason":"未见同款"})
    assert not result["selectable"] and "proposal" not in result
    client = OpenAICompatibleModelClient(Settings())
    assert review_product_import_row(data, client.propose_product_match)["state"] == "NEEDS_CONFIRMATION"


@pytest.mark.parametrize("value", [None, [], {"decision": [], "candidateRefs": [], "reason": "未知"}])
def test_malformed_json_values_raise_repairable_validation_errors(value):
    with pytest.raises(ValueError):
        validate_model_proposal(value, {"C1"})


def test_uncertain_refs_are_not_suggestions_and_manual_creation_is_not_automatic():
    from app.product_import import import_row_card
    data = {**evidence(productType="黄冰糖", status="成品"), "allCandidatesShown": False}
    summary, card = import_row_card(data, {"rowNumber": 17}, lambda _: {
        "decision": "UNSURE", "candidateRefs": ["C1"], "reason": "未确定为同款"})
    d = card.fields[0]
    assert d["allowManualCreate"] and not d["allowCreate"] and not d["options"][0]["suggested"]
    assert "comparedWith" not in summary and "product_ref_a" not in str(summary)
    del data["source"]["values"]["status"]
    assert not import_row_card(data, {"rowNumber": 17}, lambda _: None)[1].fields[0]["allowManualCreate"]


def test_alias_observation_carries_actual_comparison_even_without_changes():
    from app.product_import import import_row_card
    summary, _ = import_row_card(evidence(weightPerPiece="25"), {"rowNumber": 17}, lambda _: {
        "decision": "MATCH", "candidateRefs": ["C1"], "reason": "包装写法不同"})
    assert summary["comparedWith"] == "黄冰糖（袋）" and summary["comparisonResult"] == "没有差异"
    assert summary["comparedFields"] == ["单件重量"] and "product_ref_a" not in str(summary)


def test_excel_weight_difference_survives_browser_stream_and_finishes():
    import json
    from app.schemas import ChatResponse
    from app.streaming import sse_for_response
    from app.product_import import import_row_card
    data = evidence(name="黄冰糖(袋)", weightPerPiece="24.125")
    data["exactCount"] = 1
    data["candidates"][0]["hasInventory"] = True
    _, card = import_row_card(data, {"rowNumber": 17}, never)
    events = [json.loads(s.split("data: ", 1)[1]) for s in sse_for_response(
        ChatResponse(agentSessionId="local-review", answer="不允许更改规格。", cards=[card]))]
    option = next(e["payload"] for e in events if e["type"] == "card")["fields"][0]["options"][0]
    assert option["blocked"] and Decimal(option["changes"][0]["after"]) == Decimal("24.125")
    assert events[-1]["payload"]["finishReason"] == "completed"
