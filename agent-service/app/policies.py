from __future__ import annotations

from dataclasses import dataclass
from typing import Any


@dataclass(frozen=True)
class FastCompletionPolicy:
    blockers_by_tool: dict[str, tuple[str, ...]]

    @classmethod
    def default(cls) -> "FastCompletionPolicy":
        return cls(
            blockers_by_tool={
                "get_inventory_overview": (
                    "分布", "库位", "位置", "化验", "检验", "质量", "趋势", "对比", "同时", "并且", "以及"
                ),
                "get_inventory_distribution": ("化验", "检验", "质量", "趋势", "对比", "同时", "并且", "以及"),
                "get_warehouse_status": ("最近操作", "流转", "混放", "化验", "趋势", "对比", "同时", "并且", "以及"),
                "get_assay_status": ("历史", "趋势", "最近30天", "近30天", "对比", "同时", "并且", "以及"),
                "query_assay_records": ("趋势", "分析", "变化", "对比", "同时", "并且", "以及"),
                "get_assay_report_detail": ("趋势", "分析", "对比", "同时", "并且", "以及"),
                "query_unqualified_inventory": ("趋势", "分析", "原因", "对比", "同时", "并且", "以及"),
                "query_inventory_by_quality_standard": ("趋势", "分析", "原因", "对比", "同时", "并且", "以及"),
                "query_inventory_by_assay_metrics": ("趋势", "分析", "原因", "对比", "同时", "并且", "以及"),
                "query_pallet_tasks": ("趋势", "效率", "原因", "对比", "同时", "并且", "以及"),
                "query_fixed_product_qr_pool": ("趋势", "效率", "原因", "对比", "同时", "并且", "以及"),
                "query_production_order_progress": ("原料", "领料", "趋势", "效率", "原因", "对比", "同时", "并且", "以及"),
                "query_material_pick_trace": ("产出", "去向", "趋势", "效率", "原因", "对比", "同时", "并且", "以及"),
                "query_boiling_batch_trace": ("原料", "产出", "趋势", "效率", "原因", "对比", "同时", "并且", "以及"),
            }
        )

    def allows(self, tool_name: str, user_message: str) -> bool:
        text = "".join((user_message or "").split())
        blockers = self.blockers_by_tool.get(tool_name)
        return blockers is not None and not any(word in text for word in blockers)


@dataclass(frozen=True)
class SafeFallbackPolicy:
    recoverable_model_errors: frozenset[str]
    pre_fallback_observation_tools: frozenset[str]
    allowed_plan_tools: frozenset[str]

    @classmethod
    def default(cls) -> "SafeFallbackPolicy":
        return cls(
            recoverable_model_errors=frozenset({"MODEL_TIMEOUT", "MODEL_ACTION_INVALID"}),
            pre_fallback_observation_tools=frozenset(
                {"resolve_products", "resolve_warehouses", "resolve_production_entities", "USER_SELECTION"}
            ),
            allowed_plan_tools=frozenset(
                {
                    "resolve_products",
                    "resolve_warehouses",
                    "get_inventory_overview",
                    "get_inventory_distribution",
                    "get_warehouse_status",
                    "get_assay_status",
                    "query_unqualified_inventory",
                    "query_inventory_by_quality_standard",
                    "query_inventory_by_assay_metrics",
                    "resolve_production_entities",
                    "query_boiling_batches",
                    "query_production_order_progress",
                    "query_material_pick_trace",
                    "query_boiling_batch_trace",
                }
            ),
        )

    def can_attempt(
        self,
        *,
        error_code: str,
        tool_call_count: int,
        max_tool_calls: int,
        observations: list[dict[str, Any]],
    ) -> bool:
        if error_code not in self.recoverable_model_errors or tool_call_count >= max_tool_calls:
            return False
        observed_tools = {
            str(item.get("tool") or "")
            for item in observations
            if isinstance(item, dict) and item.get("status") in {"AVAILABLE", "NO_DATA"}
        }
        return not (observed_tools - self.pre_fallback_observation_tools)

    def allows_plan(self, action: str, tool_name: str | None) -> bool:
        if action == "ask_user":
            return True
        return action == "call_tool" and tool_name in self.allowed_plan_tools


@dataclass(frozen=True)
class NextActionPolicy:
    def suggestions(self, tool_name: str, safe_data: dict[str, Any]) -> list[str]:
        if tool_name == "get_inventory_overview" and safe_data.get("isEmpty") is False:
            return ["查询库存分布"]
        if tool_name == "query_pallet_tasks" and safe_data.get("records"):
            return ["只看入库任务", "只看出库任务", "查看第一条任务详情"]
        if tool_name == "preview_task_transition" and safe_data.get("canOpenBusinessDialog"):
            task_group = safe_data.get("taskGroupLabel")
            if task_group in {"成品入库", "成品出库", "调拨"}:
                return [f"打开{task_group}业务弹窗"]
            return ["打开业务弹窗"]
        if tool_name == "query_boiling_batch_trace" and safe_data.get("usages"):
            return ["查看关联生产订单"]
        if tool_name == "query_production_order_progress":
            return ["查看实际领料", "查看产出入库去向"]
        if tool_name == "query_material_pick_trace":
            return ["查看产出情况", "查看产出入库去向"]
        return []
