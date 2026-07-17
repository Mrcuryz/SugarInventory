from __future__ import annotations

from collections import defaultdict
from statistics import mean, median
from typing import Literal

from pydantic import BaseModel, ConfigDict, Field


class CoreTaskRunV1(BaseModel):
    model_config = ConfigDict(extra="forbid")

    schemaVersion: Literal["1.0"] = "1.0"
    participantKey: str = Field(min_length=1, max_length=80)
    participantRole: Literal["WAREHOUSE_OPERATOR", "QUALITY_INSPECTOR", "PRODUCTION_SUPERVISOR"]
    taskId: str = Field(pattern=r"^T(0[1-9]|10)$")
    routePath: Literal["DETERMINISTIC", "LLM"]
    completionStatus: Literal[
        "COMPLETE",
        "PARTIAL",
        "NEEDS_CLARIFICATION",
        "UNSUPPORTED",
        "FAILED",
    ]
    factFingerprint: str | None = Field(default=None, max_length=128)
    userTurns: int = Field(ge=1, le=30)
    latencyMs: int = Field(ge=0, le=600_000)
    routedGoalType: str | None = Field(default=None, max_length=100)
    expectedGoalType: str | None = Field(default=None, max_length=100)
    errorCode: str | None = Field(default=None, max_length=100)


def summarize_core_task_comparison(runs: list[CoreTaskRunV1]) -> dict[str, object]:
    by_path: dict[str, list[CoreTaskRunV1]] = defaultdict(list)
    by_pair: dict[tuple[str, str], dict[str, CoreTaskRunV1]] = defaultdict(dict)
    for run in runs:
        by_path[run.routePath].append(run)
        by_pair[(run.participantKey, run.taskId)][run.routePath] = run

    path_metrics: dict[str, dict[str, float | int]] = {}
    for path in ("DETERMINISTIC", "LLM"):
        values = by_path.get(path, [])
        complete = [run for run in values if run.completionStatus == "COMPLETE"]
        correct_route = [
            run
            for run in values
            if run.expectedGoalType and run.routedGoalType == run.expectedGoalType
        ]
        path_metrics[path] = {
            "runCount": len(values),
            "completionRate": _ratio(len(complete), len(values)),
            "goalRouteAccuracy": _ratio(len(correct_route), len(values)),
            "averageUserTurns": round(mean(run.userTurns for run in values), 3) if values else 0.0,
            "medianLatencyMs": round(median(run.latencyMs for run in values), 3) if values else 0.0,
            "p95LatencyMs": _percentile([run.latencyMs for run in values], 0.95),
        }

    paired = [pair for pair in by_pair.values() if set(pair) == {"DETERMINISTIC", "LLM"}]
    comparable_facts = [
        pair
        for pair in paired
        if pair["DETERMINISTIC"].factFingerprint and pair["LLM"].factFingerprint
    ]
    consistent_facts = [
        pair
        for pair in comparable_facts
        if pair["DETERMINISTIC"].factFingerprint == pair["LLM"].factFingerprint
    ]
    return {
        "schemaVersion": "1.0",
        "pathMetrics": path_metrics,
        "pairedRunCount": len(paired),
        "factComparablePairCount": len(comparable_facts),
        "factConsistencyRate": _ratio(len(consistent_facts), len(comparable_facts)),
    }


def _ratio(numerator: int, denominator: int) -> float:
    return round(numerator / denominator, 4) if denominator else 0.0


def _percentile(values: list[int], percentile: float) -> float:
    if not values:
        return 0.0
    ordered = sorted(values)
    index = max(0, min(len(ordered) - 1, round((len(ordered) - 1) * percentile)))
    return float(ordered[index])
