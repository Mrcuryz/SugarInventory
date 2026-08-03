from __future__ import annotations

from collections import defaultdict
from contextvars import ContextVar
from math import ceil
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
    "knowledge_search_total",
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
    "stream_first_progress_duration",
    "knowledge_search_duration",
)


_TURN_TOOL_DURATIONS: ContextVar[tuple[float, ...]] = ContextVar(
    "turn_tool_durations",
    default=(),
)


def reset_turn_diagnostics() -> None:
    """Start a request-local diagnostic scope without retaining business data."""

    _TURN_TOOL_DURATIONS.set(())


def record_turn_tool_duration(duration_seconds: float) -> None:
    duration = max(0.0, float(duration_seconds))
    _TURN_TOOL_DURATIONS.set((*_TURN_TOOL_DURATIONS.get(), duration))


def take_turn_tool_durations() -> list[float]:
    values = list(_TURN_TOOL_DURATIONS.get())
    _TURN_TOOL_DURATIONS.set(())
    return values


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
                    "p50": _percentile(values, 0.50),
                    "p95": _percentile(values, 0.95),
                }
                for (name, labels), values in self._histograms.items()
            }
        for name in COUNTERS:
            counters.setdefault(name, 0)
        for name in HISTOGRAMS:
            histograms.setdefault(name, {"count": 0, "sum": 0, "max": 0, "p50": 0, "p95": 0})
        return {"counters": counters, "histograms": histograms}


def _percentile(values: list[float], percentile: float) -> float:
    if not values:
        return 0
    ordered = sorted(values)
    rank = max(1, ceil(percentile * len(ordered)))
    return ordered[rank - 1]


def _labels(values: dict[str, Any]) -> tuple[tuple[str, str], ...]:
    return tuple(sorted((key, str(value)) for key, value in values.items() if value is not None))


def _metric_key(name: str, labels: tuple[tuple[str, str], ...]) -> str:
    if not labels:
        return name
    rendered = ",".join(f'{key}="{value}"' for key, value in labels)
    return f"{name}{{{rendered}}}"
