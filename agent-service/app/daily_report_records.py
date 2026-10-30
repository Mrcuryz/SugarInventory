"""Display allowlist for saved daily-report values; no derived production metrics."""
from __future__ import annotations

from typing import Any
import re
from app.schemas import BusinessCard

ROW_FIELDS = ("reportDate", "departmentName", "sectionStatusLabel", "groupName", "itemName",
              "itemKindLabel", "unit", "dailyActual", "convertedTons", "monthQuantity", "monthTons", "yearTons")
SUMMARY_FIELDS = ("sourceLabel", "dateRangeLabel", "scopeLabel", "isEmpty", "page", "size", "total", "hasMore", "returnedRecordCount", "overviewTruncated", "requestedDays")
OVERVIEW_FIELDS = ("summaryType", "departmentName", "label", "unit", "quantity", "convertedTons",
                   "recordCount", "quantityRows", "tonnageRows", "observedDays", "draftRows", "coverageLabel")


def daily_report_answer_error(answer: str) -> str | None:
    """Reject known contradictions of saved-draft and daily aggregation policies."""
    aggregation_clauses = [clause for sentence in re.split(r"[。；;\n]", answer or "")
                           if not re.search(r"无数据|没有数据|未填报|缺少数据|空数据", sentence)
                           for clause in re.split(r"[，,]", sentence)]
    for clause in aggregation_clauses:
        # Monthly/yearly cumulative snapshots must not be summed again. That
        # restriction does not apply to daily actuals in a requested range.
        if re.search(r"月|年|累计值|累计字段|无数据|没有数据|未填报|缺少数据|空数据", clause):
            continue
        if re.search(r"并非|不是|不代表|不意味着|不等于", clause):
            continue
        if re.search(r"(?:不能|不可|不应|不允许|不支持|不做|不进行|不作|不)\s*跨日(?:累计|累加|合计|汇总|求和)", clause):
            return "日报当日实绩及已保存当日折吨可按查询日期范围合计；不能重复相加的是月/年累计快照。请依据本轮工具事实修正，不要泛化为禁止跨日合计；无数据仍不得补0。"
    for clause in re.split(r"[。；;\n]", answer or ""):
        # Do not reject a sentence which explicitly negates the wrong exclusion claim.
        if re.search(r"(?:并非|不是|不代表|不意味着|不等于)\s*(?:不会|不被|不|未|没有)?(?:计入|纳入|统计)", clause):
            continue
        excludes_draft = re.search(
            r"(?:草稿|未提交)[^。；;\n]{0,35}?(?:不会|不能|不应|不予|不被|不|未|没有)(?:被)?(?:计入|纳入|统计)", clause
        )
        submitted_only = re.search(
            r"(?:仅|只)(?:有)?(?:统计|计入|纳入)?已提交|已提交[^。；;\n]{0,15}才(?:会|能)?(?:被)?(?:计入|纳入|统计)", clause
        )
        if excludes_draft or submitted_only:
            return "生产日报查询包括已保存草稿，未提交不等于不计入。请依据本轮日报事实修正答复，不要套用订单产出排除草稿的规则。"
    return None


def adapt_daily_report_records(source: dict[str, Any]) -> dict[str, Any]:
    result = {key: source[key] for key in SUMMARY_FIELDS if key in source}
    for key, allowed, limit in (("reports", ("reportDate", "statusLabel"), 31),
                                ("records", ROW_FIELDS, 50), ("overview", OVERVIEW_FIELDS, 10)):
        if isinstance(source.get(key), list):
            result[key] = [{field: row.get(field) for field in allowed}
                           for row in source[key][:limit] if isinstance(row, dict)]
    result["limitations"] = [
        # Older backend wording grouped daily tonnage with cumulative snapshots.
        # Clarify the existing calculation, without changing or deriving any fact.
        "空值表示未填报，不等于0；当日折吨使用已保存值，概况可按日期范围合计；月/年累计仅展示原值，不再累加。"
        if value == "空值表示未填报，不等于0；折吨及月/年累计均为已保存填报值，不重新换算或累加。" else value
        for value in source.get("limitations", [])[:7] if isinstance(value, str)
    ]
    result["returnedRecordCount"] = len(result.get("records", []))
    result["limitations"].append("每页上限不等于本页实际条数；指标累计值只展示保存原值，不自动解释为吨或百分比。")
    return result


def daily_report_card(data: dict[str, Any]) -> BusinessCard:
    return BusinessCard(
        cardType="production_daily_records",
        title=f"生产日报 · {data.get('dateRangeLabel') or ''}",
        fields=[{"kind": "daily_records_summary", **{key: data.get(key) for key in SUMMARY_FIELDS}},
                *[{"kind": "daily_records_overview", **row} for row in data.get("overview", [])],
                *[{"kind": "daily_records_header", **row} for row in data.get("reports", [])],
                *[{"kind": "daily_records_row", **row} for row in data.get("records", [])],
                *[{"kind": "daily_records_note", "value": note} for note in data.get("limitations", [])]],
    )
