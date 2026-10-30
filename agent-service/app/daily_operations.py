"""Source boundaries for the fixed daily-report / operations composition.

This module does not call tools or alter the legacy registered report. Only
validated observations may cross from an expert step to the final summary.
"""
from __future__ import annotations

from copy import deepcopy
from dataclasses import dataclass
from datetime import date
from decimal import Decimal, InvalidOperation
from typing import Any

from app.schemas import BusinessCard


DAILY_OPERATIONS_RECIPE = "today_daily_operations"
DAILY_TOOL = "query_production_daily_reports"
OPERATIONS_TOOL = "run_registered_report"
OPERATIONS_REPORT = "today_operations_overview_v1"


@dataclass(frozen=True)
class DailyOperationsScope:
    """A server-bound step scope, never supplied by tool output or chat history."""

    business_date: str
    step_id: str

    def __post_init__(self) -> None:
        if date.fromisoformat(self.business_date).isoformat() != self.business_date:
            raise ValueError("business date must be canonical")
        if self.step_id not in {"daily_production", "nonproduction_operations"}:
            raise ValueError("unknown daily operations step")

    @property
    def tool(self) -> str:
        return DAILY_TOOL if self.step_id == "daily_production" else OPERATIONS_TOOL

    @property
    def goal(self) -> str:
        return "PRODUCTION_DAILY_REPORT_RECORDS" if self.step_id == "daily_production" else "TODAY_OPERATIONS_OVERVIEW"

    def context(self) -> dict[str, Any]:
        return {
            "recipeId": DAILY_OPERATIONS_RECIPE, "stepId": self.step_id,
            "businessDate": self.business_date, "scope": "全部车间、全部产品，不继承旧筛选",
            "tool": self.tool,
            "reportDefinitionId": OPERATIONS_REPORT if self.tool == OPERATIONS_TOOL else None,
            "instruction": (
                "只处理本步骤。生产数据仅取已保存日报（包括草稿），使用完整范围概况，不加总分页明细。"
                if self.tool == DAILY_TOOL else
                "只处理化验、当前库存和任务。旧运营报表的订单产出及领用不参与本次生产概况。"
            ),
        }

    def validate_arguments(self, tool: str, arguments: dict[str, Any]) -> None:
        if tool != self.tool:
            raise ValueError("tool does not belong to the registered step")
        if any(arguments.get(key) != self.business_date for key in ("startDate", "endDate")):
            raise ValueError("daily operations requires the pinned business date")
        allowed = {"startDate", "endDate"}
        if tool == DAILY_TOOL:
            allowed |= {"page", "size"}
            if arguments.get("page", 1) != 1:
                raise ValueError("overview must start at the first detail page")
        else:
            allowed |= {"reportDefinitionId", "reportVersion"}
            if arguments.get("reportDefinitionId") != OPERATIONS_REPORT or type(arguments.get("reportVersion")) is not int or arguments["reportVersion"] != 1:
                raise ValueError("legacy operations report identity changed")
        if set(arguments) - allowed:
            raise ValueError("filtered or comparison queries are outside this recipe")

    def validate_daily_evidence(self, source: dict[str, Any]) -> None:
        if self.step_id != "daily_production":
            return
        if source.get("dateRangeLabel") != self.business_date or type(source.get("requestedDays")) is not int or source["requestedDays"] != 1:
            raise ValueError("daily evidence range does not match this turn")
        for key in ("reports", "records", "overview"):
            if not isinstance(source.get(key), list):
                raise ValueError("missing daily evidence")
        for item in source["reports"] + source["records"]:
            if not isinstance(item, dict) or item.get("reportDate") != self.business_date:
                raise ValueError("daily evidence contains another business date")


COUNT_FIELDS = {
    "assayQuality": ("assayRecordCount", "passCount", "failCount", "noStandardCount", "multipleCandidatesCount", "judgedRecordCount"),
    "currentInventory": ("productCount", "warehouseCount", "palletCount", "totalEquivalentPieces"),
    "todayPalletTasks": ("cohortTaskCount", "completedTaskCount", "inProgressTaskCount", "canceledTaskCount"),
}


def _count(value: Any) -> int:
    if type(value) is not int or value < 0:
        raise ValueError("missing or invalid count is not a zero")
    return value


def _optional_number(value: Any) -> Any:
    if value is None:
        return None
    if isinstance(value, bool) or not isinstance(value, (int, float, str)):
        raise ValueError("invalid metric")
    try:
        number = Decimal(str(value))
    except InvalidOperation as exc:
        raise ValueError("invalid metric") from exc
    if not number.is_finite() or not 0 <= number <= 100:
        raise ValueError("invalid percentage")
    return value


def project_nonproduction(source: dict[str, Any], business_date: str) -> dict[str, Any]:
    """Project the RAW report, before legacy adapters can default absent counts.

    No reportRunId is propagated: this is not the archived legacy report and
    must not offer its export button. The original report remains untouched.
    """
    if source.get("reportDefinitionId") != OPERATIONS_REPORT or type(source.get("reportVersion")) is not int or source["reportVersion"] != 1:
        raise ValueError("unexpected operations report")
    overview = source.get("operationsOverview")
    if not isinstance(overview, dict) or overview.get("businessDate") != business_date:
        raise ValueError("operations evidence date does not match this turn")
    result: dict[str, Any] = {
        "sourceLabel": "化验、库存及任务业务记录", "businessDate": business_date,
        "dataAsOf": source.get("dataAsOf") if isinstance(source.get("dataAsOf"), str) else None,
    }
    for section, fields in COUNT_FIELDS.items():
        raw = overview.get(section)
        if not isinstance(raw, dict):
            raise ValueError("missing operations section")
        result[section] = {key: _count(raw.get(key)) for key in fields}
    result["assayQuality"]["passRatePercent"] = _optional_number(overview["assayQuality"].get("passRatePercent"))
    for key in ("totalStockText", "totalWeightText"):
        value = overview["currentInventory"].get(key)
        result["currentInventory"][key] = value[:200] if isinstance(value, str) else None
    result["currentPendingTaskCount"] = _count(overview.get("currentPendingTaskCount"))
    quality = source.get("dataQuality")
    if not isinstance(quality, dict) or type(quality.get("partial")) is not bool:
        raise ValueError("missing data quality status")
    # Parent partial status is deliberately preserved conservatively, even when
    # it originates in an excluded order section; never imply complete evidence.
    result["partial"] = quality["partial"]
    notes = quality.get("notes")
    if not isinstance(notes, list):
        raise ValueError("missing quality notes")
    result["notes"] = [value[:500] for value in notes[:20] if isinstance(value, str) and value.startswith(("化验", "托盘任务"))]
    result["limitations"] = [
        "化验只表示今日已登记化验，不代表全部库存批次的质量。",
        "库存和待处理任务是查询时的当前状态，不是今日库存增减。",
        "今日创建任务与当前待处理任务不是同一范围。",
        "这是分项查询结果，不是新的已归档报表；不提供组合导出。",
    ]
    if result["partial"]:
        result["limitations"].append("原运营数据存在完整性提示，当前结果不能视为完整的全厂业务记录。")
    return deepcopy(result)


def nonproduction_card(data: dict[str, Any]) -> BusinessCard:
    quality, inventory, tasks = (data[key] for key in COUNT_FIELDS)
    return BusinessCard(
        cardType="daily_operations_business_facts", title=f"库存、化验与任务 · {data['businessDate']}",
        fields=[
            {"label": "今日化验", "value": f"{quality['assayRecordCount']} 条；合格 {quality['passCount']} 条，不合格 {quality['failCount']} 条"},
            {"label": "质量判定范围", "value": f"已判定 {quality['judgedRecordCount']} 条；无标准 {quality['noStandardCount']} 条，多标准候选 {quality['multipleCandidatesCount']} 条"},
            {"label": "当前库存", "value": inventory["totalStockText"] or f"{inventory['totalEquivalentPieces']} 件"},
            {"label": "今日创建任务", "value": f"{tasks['cohortTaskCount']} 条；已完成 {tasks['completedTaskCount']} 条，处理中 {tasks['inProgressTaskCount']} 条，已取消 {tasks['canceledTaskCount']} 条"},
            {"label": "当前待处理", "value": f"{data['currentPendingTaskCount']} 条"},
            *[{"label": "数据提示", "value": note} for note in data["notes"]],
            *[{"label": "统计范围", "value": note} for note in data["limitations"]],
        ],
    )
