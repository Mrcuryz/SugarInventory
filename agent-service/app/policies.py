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
                {"resolve_products", "resolve_warehouses", "USER_SELECTION"}
            ),
            allowed_plan_tools=frozenset(
                {
                    "resolve_products",
                    "resolve_warehouses",
                    "get_inventory_overview",
                    "get_inventory_distribution",
                    "get_warehouse_status",
                    "get_assay_status",
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
        return []
