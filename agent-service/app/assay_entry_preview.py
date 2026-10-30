"""Selected product/group assay preview input and presentation; no write operation."""
from __future__ import annotations

from datetime import date
from decimal import Decimal, InvalidOperation
from typing import Any
import re
from app.schemas import BusinessCard

METRICS = {
    "colorValue": "色值", "reducingSugar": "还原糖分", "dryWeight": "干燥失重",
    "conductivityAsh": "电导灰分", "sucrose": "蔗糖分",
    "insolubleImpurity": "不溶于水杂质", "phValue": "pH值",
}
PREVIEW_ERRORS = {
    "ASSAY_ENTRY_INPUT_INVALID": "化验输入未通过校验。请核对采样日期和数值范围；检测值最多保留两位小数，且至少填写一项实测值。本次未保存，请修正后重新预览。",
    "ASSAY_ENTRY_VERSION_CONFLICT": "该产品的已有化验版本存在冲突，请先核对已有记录后重新预览。本次未保存。",
    "ASSAY_ENTRY_JUDGEMENT_UNAVAILABLE": "暂时无法取得化验预判结果，请稍后重新预览。本次未保存。",
}
SCHEMA = {
    "type": "object", "additionalProperties": False, "required": ["sampleDate"],
    "properties": {
        "productId": {"type": "integer", "minimum": 1},
        "groupRef": {"type": "string", "maxLength": 500},
        "sampleDate": {"type": "string", "format": "date"},
        **{key: {"type": ["number", "null"]} for key in METRICS},
    },
}


def validate_preview_arguments(arguments: dict[str, Any]) -> dict[str, Any]:
    if set(arguments) - set(SCHEMA["properties"]):
        raise ValueError("仅支持一个产品或化验组、采样日期和七项实测指标")
    if ("productId" in arguments) == ("groupRef" in arguments):
        raise ValueError("请选择一个具体产品或化验组，不能同时选择")
    product_id = arguments.get("productId")
    if "productId" in arguments and (type(product_id) is not int or product_id <= 0):
        raise ValueError("请选择具体产品")
    group_ref = arguments.get("groupRef")
    if "groupRef" in arguments and not valid_group_ref(group_ref):
        raise ValueError("请重新选择化验组")
    sample_date = arguments.get("sampleDate")
    if not isinstance(sample_date, str) or len(sample_date) != 10:
        raise ValueError("请提供明确的采样日期")
    parsed = date.fromisoformat(sample_date)
    if parsed.year < 1000 or parsed.isoformat() != sample_date:
        raise ValueError("采样日期无效")
    provided = False
    for field in METRICS:
        value = arguments.get(field)
        if value is None:
            continue
        if type(value) not in (int, float, Decimal):
            raise ValueError(f"{METRICS[field]}必须是数值")
        try:
            number = Decimal(str(value))
        except InvalidOperation as exc:
            raise ValueError("检测数值无效") from exc
        if not number.is_finite():
            raise ValueError("检测数值必须是有限数值")
        provided = True
    if not provided:
        raise ValueError("请至少提供一项实测值，未检测项可以留空")
    # Preserve precision and zeros; backend alone decides storage bounds and scientific rules.
    return dict(arguments)


def valid_group_ref(value: Any) -> bool:
    return isinstance(value, str) and len(value) <= 500 and re.fullmatch(r"aer_[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+", value) is not None


def adapt_group_preview(source: dict[str, Any], arguments: dict[str, Any]) -> dict[str, Any]:
    validate_preview_arguments(arguments)
    products = source.get("products")
    count = source.get("productCount")
    if ("groupRef" not in arguments or source.get("groupRef") != arguments["groupRef"]
            or source.get("previewOnly") is not True or source.get("error")
            or source.get("sampleDate") != arguments.get("sampleDate")
            or not isinstance(source.get("groupName"), str) or not source["groupName"].strip()
            or type(count) is not int or not 1 <= count <= 200
            or not isinstance(products, list) or len(products) != count):
        raise ValueError("化验组预览范围或产品明细不完整")
    seen = set()
    adapted = []
    for product in products:
        if not isinstance(product, dict):
            raise ValueError("化验组产品明细无效")
        product_id = product.get("productId")
        if (type(product_id) is not int or product_id <= 0 or product_id in seen
                or type(product.get("proposedVersion")) is not int or product["proposedVersion"] <= 0):
            raise ValueError("化验组产品重复或版本无效")
        seen.add(product_id)
        values = {key: value for key, value in arguments.items() if key != "groupRef"}
        values["productId"] = product_id
        child = adapt_preview(product, values)
        # Only the parent group can be confirmed; nested products have no separate save action.
        child.pop("confirmationAvailable", None)
        child.pop("confirmationGuidance", None)
        adapted.append(child)
    available = source.get("confirmationAvailable") is True
    return {"entryKind": "GROUP_V1", "previewOnly": True, "confirmationAvailable": available,
            "groupName": source["groupName"], "sampleDate": arguments["sampleDate"],
            "productCount": count, "products": adapted,
            "warnings": ["本次仅预览，尚未保存化验记录。", "同一组检测值将用于组内所有产品，各产品分别按其适用标准预判。"],
            "confirmationGuidance": "当前尚未保存；页面已支持人工保存，用户可点击卡片的“核对并保存”，核对整组全部产品后确认。模型不能自动保存，不等于系统没有保存能力；不要说当前没有保存能力。" if available else "当前仅提供预览，尚未开放人工保存入口。"}


def adapt_preview(source: dict[str, Any], arguments: dict[str, Any]) -> dict[str, Any]:
    if (source.get("previewOnly") is not True or source.get("error")
            or source.get("productId") != arguments.get("productId")
            or source.get("sampleDate") != arguments.get("sampleDate")):
        raise ValueError("化验预览对象不一致，请重新预览")
    rows = source.get("metrics")
    if not isinstance(rows, list) or len(rows) != len(METRICS):
        raise ValueError("化验预览指标不完整")
    by_field = {row.get("field"): row for row in rows if isinstance(row, dict)}
    if set(by_field) != set(METRICS):
        raise ValueError("化验预览指标不一致")
    metrics = []
    for field, name in METRICS.items():
        row = by_field[field]
        expected = arguments.get(field)
        provided = expected is not None
        actual = row.get("value")
        if row.get("provided") is not provided or ((actual is None) != (expected is None)):
            raise ValueError("化验预览缺测标记与输入不一致")
        if provided and Decimal(str(actual)) != Decimal(str(expected)):
            raise ValueError("化验预览数值与输入不一致，未接受自动舍入")
        metrics.append({"name": name, "value": actual, "provided": provided, "unit": row.get("unit")})
    if source.get("judgementLabel") not in {"合格", "不合格", "无标准", "合格（多个匹配标准）"}:
        raise ValueError("化验预判结果不可用")
    result = {key: source.get(key) for key in (
        "productName", "weightPerPiece", "piecesPerPallet", "sampleDate", "previewedAt",
        "existingVersion", "proposedVersion", "judgementLabel",
        "appliedStandardName", "appliedStandardVersion",
    )}
    result.update(previewOnly=True, confirmationAvailable=source.get("confirmationAvailable") is True, metrics=metrics,
                  failedMetrics=[{key: row.get(key) for key in ("name", "value", "unit", "reason")}
                                 for row in source.get("failedMetrics", []) if isinstance(row, dict)],
                  warnings=["本次仅预览，尚未保存化验记录。" if "确认凭据" in note else note
                            for note in source.get("warnings", []) if isinstance(note, str)])
    # The legacy phrase "未命中任何标准" means no standard passed, not no configured
    # standard. Give the model the authoritative label/reasons without that ambiguity.
    result["judgementNote"] = (
        "后端已按所列参考标准完成预判；卡片中的全部未通过项目均为判定依据。"
        if source.get("judgementLabel") == "不合格" else "判定与参考标准以本次预览结果为准。"
    )
    if any("缺少检测值" in str(row.get("reason", "")) for row in result["failedMetrics"]):
        result["judgementNote"] += "缺少检测值属于未通过原因，并非排除该项。"
    result["confirmationGuidance"] = ("当前尚未保存；用户可点击卡片的“核对并保存”，核对服务端预览后确认保存化验记录。这不是产品入库，不改变库存；不要把保存化验称作入库。模型本身不执行保存。"
                                      if result["confirmationAvailable"] else "当前仅提供预览，尚未开放人工保存入口。")
    return result


def preview_card(data: dict[str, Any], arguments: dict[str, Any] | None = None, product_ref: str | None = None, *, group_ref: str | None = None) -> BusinessCard:
    fields = [{"kind": "assay_entry_preview_summary", **data}]
    reference = group_ref if group_ref is not None else product_ref
    if arguments is not None and valid_group_ref(reference):
        # UI-only input from the validated tool call, never from model-written card JSON.
        # The human dialog requests a fresh backend preview before any confirmation.
        values = validate_preview_arguments(arguments)
        key = "groupRef" if group_ref is not None else "productRef"
        values.pop("productId", None)
        values.pop("groupRef", None)
        fields.append({"kind": "assay_entry_action", "entryInput": {key: reference, "values": values}})
    return BusinessCard(cardType="assay_entry_preview", title="化验录入预览",
                        fields=fields)


def preview_answer_error(answer: str, preview: dict[str, Any] | None = None) -> str | None:
    if re.search(r"(?:采样|取样|样品|\d{4}-\d{2}-\d{2})[^。；\n]{0,32}(?:上午|下午|凌晨|早上|晚上|中午|\d{1,2}[:：]\d{2})", answer):
        return "预览只记录采样日期，不记录采样时刻。请只说明卡片中的年月日，不添加上午、下午或具体时刻；简洁概述，不重复卡片全部指标。"
    if any(phrase in answer for phrase in ("如需入库", "确认入库", "完成入库", "入库成功", "保存入库")):
        return "本次是化验记录预览与人工保存，不是产品入库。请删除入库操作指引，改为核对并保存化验记录，并说明尚未保存。"
    if preview and preview.get("confirmationAvailable") and any(phrase in answer for phrase in (
        "没有可用的保存", "尚不支持保存", "不支持保存", "具备保存能力后", "没有保存操作",
        "当前没有保存能力", "系统没有保存能力", "尚未开放人工保存入口", "只能预览，不能保存",
    )):
        return "页面已支持人工确认保存。请说明当前尚未保存，可点击卡片的核对并保存按钮；不要说系统不支持保存。"
    failed = (preview or {}).get("failedMetrics", []) + [row for product in (preview or {}).get("products", []) for row in product.get("failedMetrics", [])]
    if any("缺少检测值" in str(row.get("reason", "")) for row in failed):
        if any(phrase in answer for phrase in ("未参与判定", "不参与判定", "无法判定", "不影响判定", "未作判定", "未做判定", "不作判定", "无法参与判定", "不自动判定", "不视为合格或不合格")):
            return "后端将缺少检测值列为未通过原因，不能说未参与或无法判定。请忠实说明预判结果及缺测提示。"
    for match in re.finditer(r"已(?:经)?(?:成功|完成)?(?:保存|录入|写入|提交)|(?:保存|录入|写入|提交)成功", answer):
        clause = re.split(r"[，。；;！？\n]|但是|但|而是|实际上|其实", answer[:match.start()])[-1]
        # Quoting a success claim to explicitly deny it is not claiming a save.
        if clause.endswith("未") or re.search(r"(?:不能|不得|不要|不应|并非|不是|不代表|不表示|不等于)[^，。；;！？\n]{0,8}$", clause):
            continue
        return "本轮只有只读预览，未保存任何化验。请删除录入成功或已保存的表述，并明确说明未保存。"
    if not any(marker in answer for marker in ("未保存", "未录入", "未写入", "没有保存")):
        return "请明确说明本次化验仅预览、尚未保存。"
    return None
