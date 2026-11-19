"""Pure product-import comparison foundation; not registered as a tool or write authority.

Workbook/candidate content is data, never instructions. Catalog references must be
provided by the backend. A model proposal never constitutes human confirmation.
"""
from __future__ import annotations

from decimal import Decimal, InvalidOperation
import re
from typing import Any
import unicodedata

FIELDS = frozenset({"productName", "productType", "status", "packagingMethod",
                    "weightPerPiece", "piecesPerPallet", "canStack", "screenMeshName"})
SPEC_FIELDS = frozenset({"weightPerPiece", "piecesPerPallet"})
FIELD_LABELS = {"productType": "产品类型", "status": "产品状态", "packagingMethod": "包装方式",
                "weightPerPiece": "单件重量", "piecesPerPallet": "每板件数", "canStack": "是否可堆叠",
                "screenMeshName": "筛网名称"}


def name_key(value: str) -> str:
    # Do not remove sizes, packaging, colours, grades or inner spaces.
    value = unicodedata.normalize("NFC", value).translate(str.maketrans("（）\u3000", "() "))
    return re.sub(r"\s+", " ", value).strip()


def parse_values(raw: dict[str, Any]) -> dict[str, Any]:
    if set(raw) - FIELDS:
        raise ValueError("存在未确认的产品字段")
    values: dict[str, Any] = {}
    for key, value in raw.items():
        if value is None or isinstance(value, str) and not value.strip():
            continue
        if key in SPEC_FIELDS:
            try:
                if isinstance(value, bool):
                    raise ValueError()
                number = Decimal(str(value))
                if not number.is_finite() or number < (Decimal("0.1") if key == "weightPerPiece" else 1):
                    raise ValueError()
                if key == "piecesPerPallet" and (number != number.to_integral_value() or number > 2147483647):
                    raise ValueError()
                values[key] = number if key == "weightPerPiece" else int(number)
            except (InvalidOperation, ValueError, OverflowError):
                raise ValueError("请核对单件重量或每板件数，不能推测单位或取整") from None
        elif key == "canStack":
            if type(value) is bool:
                values[key] = value
            elif value in ("是", "否"):
                values[key] = value == "是"
            else:
                raise ValueError("是否可堆叠请填写是或否")
        else:
            if not isinstance(value, str) or len(value) > 2000:
                raise ValueError("产品字段格式不正确")
            values[key] = value.strip()
    if not values.get("productName"):
        raise ValueError("请提供产品名称")
    for field, allowed in {"productType": {"黄冰糖", "白冰糖"}, "status": {"成品", "半成品"},
                           "packagingMethod": {"袋", "箱", "罐"}}.items():
        if field in values and values[field] not in allowed:
            raise ValueError("请确认产品类型、状态或包装方式，不能自动改成其他值")
    return values


def compare_row(raw: dict[str, Any], catalog: list[dict[str, Any]]) -> dict[str, Any]:
    values = parse_values(raw)
    matches = [item for item in catalog if name_key(item["values"]["productName"]) == name_key(values["productName"])]
    if not matches:
        return {"state": "MODEL_MATCH_REQUIRED", "changes": [], "selectable": False}
    if len(matches) != 1:
        return {"state": "NEEDS_CONFIRMATION", "candidateRefs": [x["ref"] for x in matches],
                "changes": [], "selectable": False}
    target = matches[0]
    changes = []
    for field, after in values.items():
        before = target["values"].get(field)
        if field == "productName":
            continue  # Typography-only variations must not cause a rename.
        if field == "weightPerPiece" and before is not None:
            before = Decimal(str(before))
        if before != after:
            changes.append({"field": field, "before": before, "after": after})
    spec_change = any(x["field"] in SPEC_FIELDS for x in changes)
    identity_change = any(x["field"] in {"productType", "status", "packagingMethod", "screenMeshName"} for x in changes)
    if spec_change and target.get("hasInventory") is True:
        state = "BLOCKED_INVENTORY_SPECIFICATION"
    elif spec_change or identity_change:
        state = "NEEDS_CONFIRMATION"
    else:
        state = "UPDATE" if changes else "UNCHANGED"
    return {"state": state, "targetRef": target["ref"], "changes": changes,
            "selectable": state == "UPDATE"}


def duplicate_rows(rows: list[dict[str, Any]]) -> list[list[int]]:
    """Report all same-name rows for review rather than silently keeping the last."""
    groups: dict[str, list[int]] = {}
    for position, row in enumerate(rows):
        key = name_key(parse_values(row)["productName"])
        groups.setdefault(key, []).append(position)
    return [positions for positions in groups.values() if len(positions) > 1]


def validate_model_proposal(proposal: dict[str, Any], candidate_refs: set[str]) -> dict[str, Any]:
    """Closed model output contract; proposals cannot introduce DB IDs or field updates."""
    if not isinstance(proposal, dict) or set(proposal) != {"decision", "candidateRefs", "reason"}:
        raise ValueError("产品匹配建议格式不正确")
    refs = proposal["candidateRefs"]
    reason = proposal["reason"]
    decision = proposal["decision"]
    if not isinstance(refs, list) or len(refs) > 10 or any(not isinstance(x, str) for x in refs):
        raise ValueError("产品候选不正确")
    if len(set(refs)) != len(refs) or not set(refs) <= candidate_refs:
        raise ValueError("匹配结果包含未提供的产品")
    if not isinstance(reason, str) or not reason.strip() or len(reason) > 500:
        raise ValueError("请提供简短的匹配依据")
    if not isinstance(decision, str) or decision not in {"MATCH", "NEW", "UNSURE"}:
        raise ValueError("产品匹配判断不正确")
    if decision == "MATCH" and len(refs) != 1 or decision == "NEW" and refs:
        raise ValueError("产品匹配判断与候选不一致")
    return {"state": "NEEDS_CONFIRMATION", "proposal": {"decision": decision,
            "candidateRefs": list(refs), "reason": reason}, "selectable": False}


def review_product_import_row(evidence, propose):
    """Analyse one server-issued row. No write calls; uncertainty never becomes an update."""
    source, candidates = evidence["source"], evidence["candidates"]
    base = {"rowNumber": source["rowNumber"], "sourceValues": source["values"],
            "candidates": candidates, "allCandidatesShown": evidence["allCandidatesShown"],
            "changes": [], "selectable": False}
    try:
        values = parse_values(source["values"])
    except ValueError as exc:
        return {**base, "state": "INVALID_ROW", "message": str(exc)}
    if evidence["duplicateRows"]:
        return {**base, "state": "DUPLICATE_SOURCE", "rows": evidence["duplicateRows"],
                "message": "这些表格行的产品名称相同，请核对后再导入。"}
    name = name_key(values["productName"])
    exact = [c for c in candidates if name_key(c["values"]["productName"]) == name]
    if (type(evidence["allCandidatesShown"]) is not bool or type(evidence["exactCount"]) is not int
            or evidence["exactCount"] != len(exact)
            or any(type(c.get("hasInventory")) is not bool for c in candidates)
            or len({c["ref"] for c in candidates}) != len(candidates)):
        return {**base, "state": "NEEDS_CONFIRMATION", "message": "产品目录信息不完整，请重新核对。"}
    if exact:
        return {**base, **compare_row(source["values"], exact)}
    missing = sorted({"productType", "status"} - values.keys())
    if not candidates:
        if evidence["allCandidatesShown"]:
            return {**base, "state": "CREATE" if not missing else "INCOMPLETE_CREATE",
                    "selectable": not missing, "missingFields": missing}
        return {**base, "state": "NEEDS_CONFIRMATION", "message": "候选范围不完整，无法判断是否为新产品。"}
    # Never send workbook metadata, signed references or inventory flags to the model.
    options = {f"C{i}": item for i, item in enumerate(candidates, 1)}
    payload = {"sourceValues": source["values"], "allCandidatesShown": evidence["allCandidatesShown"],
               "candidates": [{"key": key, "values": item["values"]} for key, item in options.items()]}
    proposal = propose(payload)
    if proposal is None:
        return {**base, "state": "NEEDS_CONFIRMATION", "message": "暂时无法判断是否为同一产品，请人工核对。"}
    checked = validate_model_proposal(proposal, set(options))["proposal"]
    chosen = [options[key] for key in checked["candidateRefs"]]
    if checked["decision"] == "NEW" and not evidence["allCandidatesShown"]:
        return {**base, "state": "NEEDS_CONFIRMATION", "message": "已展示的候选未发现匹配，但未覆盖全部产品。"}
    result = {**base, "state": "NEEDS_CONFIRMATION", "proposal": {
        **checked, "candidateRefs": [item["ref"] for item in chosen]}}
    if checked["decision"] == "MATCH":
        # Compare supplied fields against the proposed target, keeping its canonical name.
        target = chosen[0]
        diff = compare_row({**source["values"], "productName": target["values"]["productName"]}, [target])
        result.update(changes=diff["changes"], targetComparison=diff["state"])
        if diff["state"] == "BLOCKED_INVENTORY_SPECIFICATION":
            result["message"] = "该候选已有库存，不能修改单件重量或每板件数。"
    elif checked["decision"] == "NEW":
        result["missingFields"] = missing
    return result


def import_row_context(context):
    attachment, row = context.get("productImportAttachment"), context.get("productImportRow")
    if (isinstance(attachment, str) and re.fullmatch(r"[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}", attachment)
            and type(row) is int and 2 <= row <= 501):
        return {"attachment": attachment, "rowNumber": row}
    return None


def import_row_card(evidence, binding, propose):
    from app.schemas import BusinessCard
    labels = {"UNCHANGED": "没有差异", "UPDATE": "可修改", "CREATE": "可新增",
              "NEEDS_CONFIRMATION": "需要核对", "INVALID_ROW": "表格值无效",
              "DUPLICATE_SOURCE": "表内重名", "INCOMPLETE_CREATE": "新增资料不全",
              "BLOCKED_INVENTORY_SPECIFICATION": "已有库存，不能更改规格"}
    row = binding["rowNumber"]
    try:
        if evidence["source"]["rowNumber"] != row:
            raise ValueError("来源行不一致")
        checked = review_product_import_row(evidence, propose)
        raw, candidates = checked["sourceValues"], checked["candidates"]
        name = raw.get("productName") or "未填写名称"
        status = labels[checked["state"]]
        detail = {"sourceValues": raw, "statusLabel": status, "message": checked.get("message"),
                  "duplicateRows": checked.get("rows", []), "changes": checked["changes"],
                  "options": [], "allowCreate": False, "missingFields": checked.get("missingFields", [])}
        if checked["state"] not in {"INVALID_ROW", "DUPLICATE_SOURCE"}:
            exact = [item for item in candidates if name_key(item["values"]["productName"]) == name_key(name)]
            if (type(evidence["exactCount"]) is not int or evidence["exactCount"] != len(exact)
                    or type(evidence["allCandidatesShown"]) is not bool or len(candidates) > 10
                    or len({x["ref"] for x in candidates}) != len(candidates)
                    or any(not x["ref"] or type(x["hasInventory"]) is not bool for x in candidates)):
                raise ValueError("候选信息不完整")
            detail["missingFields"] = sorted({"productType", "status"} - parse_values(raw).keys())
            detail["allowCreate"] = not exact and evidence["allCandidatesShown"] and not detail["missingFields"]
            detail["partial"] = not evidence["allCandidatesShown"]
            # The Java reader counts exact names across the whole catalog before limiting candidates.
            # Manual intent is separate from allowCreate, which requires complete candidate coverage.
            detail["allowManualCreate"] = detail["partial"] and not exact and not detail["missingFields"]
            proposal = checked.get("proposal") or {}
            detail["reason"] = proposal.get("reason")
            for item in exact or candidates:
                diff = compare_row({**raw, "productName": item["values"]["productName"]}, [item])
                detail["options"].append({"values": item["values"], "ref": item["ref"],
                    "suggested": (proposal.get("decision") == "MATCH" and item["ref"] in proposal["candidateRefs"])
                                 or checked.get("targetRef") == item["ref"],
                    "changes": diff["changes"], "label": labels[diff["state"]],
                    "blocked": diff["state"] == "BLOCKED_INVENTORY_SPECIFICATION"})
    except (TypeError, KeyError, ValueError):
        name, status = "所选产品", "未完成核对"
        detail = {"statusLabel": status, "message": "本行信息不完整，请重新打开表格核对。",
                  "options": [], "allowCreate": False}
    summary = {"rowNumber": row, "productName": name, "reviewStatus": status, "saved": False}
    if detail.get("message"):
        summary["notice"] = detail["message"]
    if detail.get("duplicateRows"):
        summary["duplicateRows"] = detail["duplicateRows"]
        summary["nextStep"] = "请先核对列出的重复行，保留正确的一行，再重新导入；无需补填其他字段。"
    elif detail.get("sourceValues"):
        summary["candidateScope"] = "仅列出相近产品" if detail.get("partial") else "全部候选"
        matched = next((o for o in detail["options"] if o["suggested"]), None)
        if matched:
            summary["comparedWith"] = matched["values"]["productName"]
            summary["comparedFields"] = [v for k, v in FIELD_LABELS.items() if k in detail["sourceValues"]
                                         and detail["sourceValues"][k] not in (None, "")]
            summary["comparisonResult"] = matched["label"]
            summary["nextStep"] = ("请勿应用这项规格变更。" if matched["blocked"] else
                                   "若是同一产品，请在卡片中核对；只比较表格已填写的字段。")
        elif detail.get("partial"):
            summary["nextStep"] = "没有确定匹配；请核对卡片候选。不能仅凭候选列表认定为新品。"
    if detail.get("changes"):
        summary["differences"] = [{k: str(v) if isinstance(v, Decimal) else v for k, v in d.items()}
                                  for d in detail["changes"]]
    # Signed candidate references belong only in the user-facing card, never in the model observation.
    return summary, BusinessCard(cardType="product_import_review", title=f"第{row}行 · {name}", fields=[detail])
