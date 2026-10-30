"""Saved daily-report trend projection. Totals come from the backend, never sampled points."""
from __future__ import annotations

from datetime import date, timedelta
from decimal import Decimal, InvalidOperation
import math
from typing import Any

from app.schemas import BusinessCard

REPORT_ID = "production_daily_report_analysis_v1"
COUNTS = ("recordCount", "quantityRows", "tonnageRows", "draftRows")
SERIES_TEXT = ("summaryType", "departmentName", "label", "unit", "coverageLabel")


def _number(value: Any) -> float | None:
    if value is None:
        return None
    if isinstance(value, bool):
        raise ValueError("invalid daily report number")
    try:
        result = Decimal(str(value))
        if not result.is_finite():
            raise ValueError("non-finite daily report number")
        number = float(result)
        if not math.isfinite(number):
            raise ValueError("daily report number exceeds finite range")
        return number
    except InvalidOperation as exc:
        raise ValueError("invalid daily report number") from exc


def _count(value: Any) -> int:
    if isinstance(value, bool) or not isinstance(value, int) or value < 0:
        raise ValueError("invalid daily report count")
    return value


def _comparison(raw: Any, start: date, end: date, safe_text) -> dict[str, Any] | None:
    if raw is None:
        return None
    if not isinstance(raw, dict) or raw.get("comparisonMode") not in {"PREVIOUS_PERIOD", "CUSTOM"}:
        raise ValueError("invalid daily report comparison mode")
    first, last = (date.fromisoformat(raw[key]) for key in ("comparisonStartDate", "comparisonEndDate"))
    days = (end - start).days + 1
    if raw.get("currentStartDate") != start.isoformat() or raw.get("currentEndDate") != end.isoformat() \
            or (last - first).days + 1 != days or not (last < start or first > end) \
            or raw.get("currentPeriodDays") != days or raw.get("comparisonPeriodDays") != days \
            or raw.get("differentPeriodLengths") is not False:
        raise ValueError("invalid daily report comparison periods")
    if raw["comparisonMode"] == "PREVIOUS_PERIOD" and last != start - timedelta(days=1):
        raise ValueError("non-adjacent daily report previous period")
    metrics = raw.get("metrics")
    if not isinstance(metrics, list) or len(metrics) > 40:
        raise ValueError("invalid daily report comparison metrics")
    projected = []
    numbers = ("currentValue", "comparisonValue", "absoluteChange", "percentChange", "currentDailyAverage",
               "comparisonDailyAverage", "dailyAverageAbsoluteChange", "dailyAveragePercentChange")
    for index, metric in enumerate(metrics):
        if not isinstance(metric, dict) or not isinstance(metric.get("metricLabel"), str) or not metric["metricLabel"].strip():
            raise ValueError("invalid daily report comparison metric label")
        values = {key: _number(metric.get(key)) for key in numbers}
        if (values["comparisonValue"] is None or values["comparisonValue"] <= 0 or values["currentValue"] is None) \
                and (values["percentChange"] is not None or values["dailyAveragePercentChange"] is not None):
            raise ValueError("invalid daily report comparison rate")
        projected.append({"metricCode": f"daily_{index}", "metricLabel": safe_text(metric["metricLabel"]),
                          "unit": safe_text(metric.get("unit")), "note": safe_text(metric.get("note")),
                          "additive": metric.get("additive") is True, **values})
    label = lambda a, b: a.isoformat() if a == b else f"{a.isoformat()} 至 {b.isoformat()}"
    return {"comparisonMode": raw["comparisonMode"],
            "comparisonLabel": "上一等长期间" if raw["comparisonMode"] == "PREVIOUS_PERIOD" else "指定对比期间",
            "currentDateRangeLabel": label(start, end), "comparisonDateRangeLabel": label(first, last),
            "currentStartDate": start.isoformat(), "currentEndDate": end.isoformat(),
            "comparisonStartDate": first.isoformat(), "comparisonEndDate": last.isoformat(),
            "currentPeriodDays": days, "comparisonPeriodDays": days, "differentPeriodLengths": False,
            "comparisonDataAsOf": safe_text(raw.get("comparisonDataAsOf")), "metrics": projected,
            "notes": [safe_text(note) for note in raw.get("notes", [])[:10] if isinstance(note, str)]}


def adapt_daily_report_analysis(source: dict[str, Any], safe_text=str) -> dict[str, Any]:
    if source.get("reportDefinitionId") != REPORT_ID or source.get("reportVersion") != 1 \
            or source.get("dataScope") != "SAVED_PRODUCTION_DAILY_REPORT":
        raise ValueError("unsupported daily report result")
    start, end = (date.fromisoformat(source[key]) for key in ("startDate", "endDate"))
    days = (end - start).days + 1
    raw = source.get("productionDailyReport")
    if not 1 <= days <= 31 or not isinstance(raw, dict) or raw.get("requestedDays") != days:
        raise ValueError("invalid daily report range")
    rows = raw.get("series")
    if not isinstance(rows, list) or len(rows) > 10:
        raise ValueError("invalid daily report series")
    series = []
    for row in rows:
        item = {key: safe_text(row.get(key)) for key in SERIES_TEXT}
        item.update({key: _count(row.get(key)) for key in (*COUNTS, "observedDays", "tonnageObservedDays")})
        if not isinstance(row.get("tonnageApplicable"), bool):
            raise ValueError("missing tonnage applicability")
        item["tonnageApplicable"] = row["tonnageApplicable"]
        item["quantity"] = _number(row.get("quantity"))
        item["convertedTons"] = _number(row.get("convertedTons")) if item["tonnageApplicable"] else None
        points = row.get("points")
        if not isinstance(points, list) or len(points) != days:
            raise ValueError("incomplete daily report date series")
        item["points"] = []
        for index, point in enumerate(points):
            expected = (start + timedelta(days=index)).isoformat()
            if point.get("businessDate") != expected:
                raise ValueError("unordered daily report dates")
            item["points"].append({
                "businessDate": expected,
                "quantity": _number(point.get("quantity")),
                "convertedTons": _number(point.get("convertedTons")) if item["tonnageApplicable"] else None,
                **{key: _count(point.get(key)) for key in COUNTS},
            })
        series.append(item)
    filters = source.get("filtersApplied") or {}
    quality = source.get("dataQuality") or {}
    result = {key: safe_text(source.get(key)) for key in (
        "dataScope", "reportRunId", "reportDefinitionId", "reportName", "startDate", "endDate",
        "dateRangeLabel", "dataAsOf", "metricDefinitionVersion")}
    comparison = _comparison(source.get("comparison"), start, end, safe_text)
    result.update({
        "reportVersion": 1, "isEmpty": not series and not (comparison and comparison["metrics"]),
        "currentPeriodEmpty": not series, "comparison": comparison,
        "productScopeLabel": safe_text(filters.get("productScope")),
        "departmentScopeLabel": safe_text(filters.get("departmentScope")),
        "productionDailyReport": {"sourceLabel": "生产日报", "requestedDays": days,
                                  "truncated": raw.get("truncated") is True, "series": series},
        "dataQuality": {"partial": quality.get("partial") is True,
                        "notes": [safe_text(note) for note in quality.get("notes", [])[:30] if isinstance(note, str)]},
        "limitations": [safe_text(note) for note in source.get("limitations", [])[:10] if isinstance(note, str)],
    })
    return result


def daily_report_analysis_card(data: dict[str, Any]) -> BusinessCard:
    aggregate = data["productionDailyReport"]
    return BusinessCard(cardType="production_daily_report_analysis", title="生产日报分析", fields=[
        {"kind": "daily_analysis_summary", **{key: data.get(key) for key in (
            "reportRunId", "dateRangeLabel", "dataAsOf", "productScopeLabel", "departmentScopeLabel", "isEmpty", "currentPeriodEmpty", "comparison")},
         "sourceLabel": "生产日报", "requestedDays": aggregate["requestedDays"], "truncated": aggregate["truncated"]},
        *[{"kind": "daily_analysis_series", **row} for row in aggregate["series"]],
        *[{"kind": "daily_analysis_note", "value": note} for note in
          dict.fromkeys([*data["dataQuality"]["notes"], *data["limitations"]])],
    ])
