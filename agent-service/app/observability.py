from __future__ import annotations

from collections import defaultdict
from threading import RLock
from typing import Any


COUNTERS = (
    "router_domain_total",
    "orchestration_started_total",
    "orchestration_success_total",
    "orchestration_partial_success_total",
    "orchestration_failed_total",
    "orchestration_budget_exceeded_total",
    "hitl_created_total",
    "hitl_resumed_total",
    "hitl_expired_total",
    "expert_tool_not_allowed_total",
    "gateway_rejection_total",
    "model_token_usage",
    "model_decision_total",
    "llm_safe_fallback_total",
    "llm_fast_completion_total",
    "llm_context_filter_total",
)

HISTOGRAMS = (
    "orchestration_step_duration",
    "tool_call_duration",
    "tool_call_count_per_plan",
    "product_fan_out_count",
    "session_lock_wait_duration",
    "model_cost",
    "model_decision_duration",
    "agent_turn_duration",
)


class MetricsRegistry:
    def __init__(self) -> None:
        self._counters: dict[tuple[str, tuple[tuple[str, str], ...]], float] = defaultdict(float)
        self._histograms: dict[tuple[str, tuple[tuple[str, str], ...]], list[float]] = defaultdict(list)
        self._guard = RLock()

    def increment(self, name: str, value: float = 1, **labels: Any) -> None:
        if name not in COUNTERS:
            raise ValueError("metric counter is not registered")
        with self._guard:
            self._counters[(name, _labels(labels))] += value

    def observe(self, name: str, value: float, **labels: Any) -> None:
        if name not in HISTOGRAMS:
            raise ValueError("metric histogram is not registered")
        with self._guard:
            self._histograms[(name, _labels(labels))].append(float(value))

    def snapshot(self) -> dict[str, Any]:
        with self._guard:
            counters = {
                _metric_key(name, labels): value
                for (name, labels), value in self._counters.items()
            }
            histograms = {
                _metric_key(name, labels): {
                    "count": len(values),
                    "sum": sum(values),
                    "max": max(values) if values else 0,
                }
                for (name, labels), values in self._histograms.items()
            }
        for name in COUNTERS:
            counters.setdefault(name, 0)
        for name in HISTOGRAMS:
            histograms.setdefault(name, {"count": 0, "sum": 0, "max": 0})
        return {"counters": counters, "histograms": histograms}


def _labels(values: dict[str, Any]) -> tuple[tuple[str, str], ...]:
    return tuple(sorted((key, str(value)) for key, value in values.items() if value is not None))


def _metric_key(name: str, labels: tuple[tuple[str, str], ...]) -> str:
    if not labels:
        return name
    rendered = ",".join(f'{key}="{value}"' for key, value in labels)
    return f"{name}{{{rendered}}}"
