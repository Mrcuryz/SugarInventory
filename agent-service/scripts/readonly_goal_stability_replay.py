from __future__ import annotations

import argparse
import json
import math
import os
from pathlib import Path
from statistics import median
from time import monotonic
from typing import Any

from app.config import Settings
from app.goal_contracts import GOAL_CONTRACTS
from app.model import (
    MainAgentRouteRequest,
    ModelDecisionError,
    OpenAICompatibleModelClient,
    take_model_decision_diagnostics,
)


ROOT = Path(__file__).resolve().parents[2]
DEFAULT_CORPUS = ROOT / "docs" / "agent" / "evaluation" / "readonly-goal-stability-corpus-v1.json"
DEFAULT_OUTPUT = ROOT / "docs" / "agent" / "evaluation" / "readonly-goal-stability-result-latest.json"

EXPERTS = [
    {"expertAgent": "inventory_expert", "businessName": "库存专家", "domains": ["inventory"]},
    {"expertAgent": "warehouse_expert", "businessName": "库位专家", "domains": ["warehouse"]},
    {"expertAgent": "assay_expert", "businessName": "化验质量专家", "domains": ["assay", "quality"]},
    {"expertAgent": "pallet_expert", "businessName": "托盘专家", "domains": ["pallet", "qrcode"]},
    {"expertAgent": "production_expert", "businessName": "生产专家", "domains": ["production"]},
    {"expertAgent": "logistics_expert", "businessName": "物流任务专家", "domains": ["logistics"]},
    {"expertAgent": "master_data_expert", "businessName": "基础资料专家", "domains": ["master_data"]},
    {"expertAgent": "administration_expert", "businessName": "后台管理专家", "domains": ["administration"]},
    {"expertAgent": "audit_expert", "businessName": "审计专家", "domains": ["audit"]},
]


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Replay all registered read-only goals through the live main model.")
    parser.add_argument("--corpus", type=Path, default=DEFAULT_CORPUS)
    parser.add_argument("--output", type=Path, default=DEFAULT_OUTPUT)
    parser.add_argument("--repeats", type=int, default=3)
    parser.add_argument("--only", action="append", default=[], help="Run only the given case ID; repeatable.")
    parser.add_argument("--fresh", action="store_true", help="Discard an existing result file.")
    return parser.parse_args()


def percentile(values: list[int], ratio: float) -> int:
    if not values:
        return 0
    ordered = sorted(values)
    index = max(0, min(len(ordered) - 1, math.ceil(len(ordered) * ratio) - 1))
    return ordered[index]


def load_existing(path: Path, fresh: bool) -> dict[str, Any]:
    if fresh or not path.exists():
        return {"schemaVersion": "1.0", "runs": []}
    value = json.loads(path.read_text(encoding="utf-8"))
    if not isinstance(value, dict) or not isinstance(value.get("runs"), list):
        raise ValueError(f"Invalid replay result file: {path}")
    return value


def write_result(path: Path, document: dict[str, Any]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary = path.with_suffix(path.suffix + ".tmp")
    temporary.write_text(json.dumps(document, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    temporary.replace(path)


def summarize(document: dict[str, Any], expected_total: int) -> dict[str, Any]:
    runs = document["runs"]
    successes = [run for run in runs if run.get("passed")]
    latencies = [int(run.get("latencyMs") or 0) for run in runs]
    return {
        "expectedRuns": expected_total,
        "completedRuns": len(runs),
        "passedRuns": len(successes),
        "failedRuns": len(runs) - len(successes),
        "passRate": round(len(successes) / len(runs), 4) if runs else 0,
        "latencyP50Ms": round(median(latencies)) if latencies else 0,
        "latencyP95Ms": percentile(latencies, 0.95),
        "latencyMaxMs": max(latencies, default=0),
    }


def main() -> int:
    args = parse_args()
    if args.repeats < 1 or args.repeats > 10:
        raise ValueError("--repeats must be between 1 and 10")

    corpus = json.loads(args.corpus.read_text(encoding="utf-8"))
    cases = corpus["cases"]
    if args.only:
        wanted = set(args.only)
        cases = [case for case in cases if case["id"] in wanted]
        unknown = wanted - {case["id"] for case in cases}
        if unknown:
            raise ValueError(f"Unknown case IDs: {sorted(unknown)}")

    settings = Settings.from_env()
    if settings.model_mode != "openai_compatible" or not settings.model_api_key:
        raise RuntimeError(
            "Set AGENT_PYTHON_MODEL_MODE=openai_compatible and AGENT_MODEL_API_KEY before live replay."
        )

    document = load_existing(args.output, args.fresh)
    document.update(
        {
            "schemaVersion": "1.0",
            "corpusVersion": corpus["version"],
            "businessDate": corpus["businessDate"],
            "modelMode": settings.model_mode,
            "modelName": settings.model_name,
            "repeats": args.repeats,
        }
    )
    completed_keys = {(run["caseId"], int(run["repeat"])) for run in document["runs"]}
    model = OpenAICompatibleModelClient(settings)
    expected_total = len(cases) * args.repeats

    for case in cases:
        expected_goal = case["goalType"]
        expected_expert = GOAL_CONTRACTS[expected_goal].ownerExpert
        for repeat in range(1, args.repeats + 1):
            key = (case["id"], repeat)
            if key in completed_keys:
                continue
            started = monotonic()
            decision = None
            error_code = None
            try:
                decision = model.route_main_agent(
                    MainAgentRouteRequest(
                        userMessage=case["input"],
                        messages=[],
                        selectedContext={
                            "BUSINESS_TIME": {
                                "timezone": "Asia/Shanghai",
                                "currentDate": corpus["businessDate"],
                            }
                        },
                        availableExperts=EXPERTS,
                        registeredRecipes=["warehouse_inventory_latest_assay"],
                    )
                )
            except ModelDecisionError as exc:
                error_code = exc.code
            diagnostics = take_model_decision_diagnostics()
            latency_ms = round((monotonic() - started) * 1000)
            actual_action = decision.action if decision is not None else None
            actual_goal = decision.goalType if decision is not None else None
            actual_expert = decision.expertAgent if decision is not None else None
            passed = (
                actual_action == "DELEGATE"
                and actual_goal == expected_goal
                and actual_expert == expected_expert
            )
            run = {
                "caseId": case["id"],
                "repeat": repeat,
                "input": case["input"],
                "expectedGoalType": expected_goal,
                "expectedExpertAgent": expected_expert,
                "actualAction": actual_action,
                "actualGoalType": actual_goal,
                "actualExpertAgent": actual_expert,
                "passed": passed,
                "latencyMs": latency_ms,
                "errorCode": error_code,
                "diagnostics": diagnostics,
            }
            document["runs"].append(run)
            document["summary"] = summarize(document, expected_total)
            write_result(args.output, document)
            status = "PASS" if passed else "FAIL"
            print(
                f"{len(document['runs']):03d}/{expected_total:03d} {status} "
                f"{case['id']}#{repeat} expected={expected_goal}/{expected_expert} "
                f"actual={actual_goal}/{actual_expert} latencyMs={latency_ms} error={error_code or '-'}",
                flush=True,
            )

    document["summary"] = summarize(document, expected_total)
    write_result(args.output, document)
    print(json.dumps(document["summary"], ensure_ascii=False), flush=True)
    return 0 if document["summary"]["failedRuns"] == 0 and document["summary"]["completedRuns"] == expected_total else 1


if __name__ == "__main__":
    raise SystemExit(main())
