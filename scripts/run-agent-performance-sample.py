from __future__ import annotations

import argparse
import hashlib
import json
import math
import os
import sys
import time
from datetime import datetime, timezone
from pathlib import Path
from typing import Any
from urllib.error import HTTPError, URLError
from urllib.parse import quote
from urllib.request import Request, urlopen


PERFORMANCE_FIELDS = (
    "totalDurationMs",
    "mainRouteMs",
    "mainRouteDecisionCount",
    "mainRouteInputBytes",
    "expertInitialMs",
    "expertInitialDecisionCount",
    "expertInitialInputBytes",
    "expertResultAnalysisMs",
    "expertResultAnalysisDecisionCount",
    "expertResultAnalysisInputBytes",
    "toolDurationMs",
    "modelDecisionCount",
    "toolCallCount",
)

PUBLIC_FAILURE_MARKERS = {
    "AI 助手暂时不可用": "AGENT_UNAVAILABLE",
    "Agent 服务暂不可用": "AGENT_SERVICE_UNAVAILABLE",
    "模型规划本轮查询时响应超时": "MODEL_TIMEOUT",
    "模型结构化决策响应超时": "MODEL_TIMEOUT",
    "模型暂时无法生成可校验": "MODEL_PLAN_INVALID",
    "模型两次都没有生成符合安全契约": "MODEL_PLAN_INVALID",
    "实验性 LLM Agent Mode 未正确配置": "MODEL_MISCONFIGURED",
    "当前查询链路认证失败": "AUTHENTICATION_FAILED",
    "当前用户没有执行该只读查询的权限": "PERMISSION_DENIED",
}


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Run the fixed four-domain Agent latency corpus without persisting credentials or answers."
    )
    parser.add_argument(
        "--base-url",
        default="http://127.0.0.1:8080",
        help="Java backend base URL.",
    )
    parser.add_argument(
        "--corpus",
        default="docs/agent/evaluation/agent-performance-corpus-v1.json",
    )
    parser.add_argument("--output", help="Result JSON path; required unless --validate-only is used.")
    parser.add_argument("--repeats", type=int, default=4)
    parser.add_argument(
        "--case-ids",
        nargs="*",
        help="Optional case IDs for a smoke run; omitted means the full corpus.",
    )
    parser.add_argument("--timeout-seconds", type=float, default=110.0)
    parser.add_argument("--validate-only", action="store_true")
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    corpus_path = Path(args.corpus)
    corpus = json.loads(corpus_path.read_text(encoding="utf-8"))
    cases = validate_corpus(corpus)
    if args.case_ids:
        requested = set(args.case_ids)
        known = {case["caseId"] for case in cases}
        unknown = requested - known
        if unknown:
            raise ValueError(f"unknown case IDs: {', '.join(sorted(unknown))}")
        cases = [case for case in cases if case["caseId"] in requested]
    if args.repeats < 1 or args.repeats > 20:
        raise ValueError("--repeats must be between 1 and 20")
    if args.validate_only:
        print(f"validated {len(cases)} cases across {len(corpus['domains'])} domains")
        return 0
    if not args.output:
        raise ValueError("--output is required for a real sample run")

    username = os.environ.get("AGENT_BENCHMARK_USERNAME", "")
    password = os.environ.get("AGENT_BENCHMARK_PASSWORD", "")
    if not username or not password:
        raise ValueError(
            "AGENT_BENCHMARK_USERNAME and AGENT_BENCHMARK_PASSWORD are required; credentials are never written"
        )

    base_url = args.base_url.rstrip("/")
    token = login(base_url, username, password, args.timeout_seconds)
    started_at = datetime.now(timezone.utc)
    samples: list[dict[str, Any]] = []
    total_samples = len(cases) * args.repeats
    sample_number = 0
    for repeat in range(1, args.repeats + 1):
        for case in cases:
            sample_number += 1
            sample = run_case(
                base_url=base_url,
                token=token,
                case=case,
                repeat=repeat,
                timeout_seconds=args.timeout_seconds,
            )
            samples.append(sample)
            print(
                f"[{sample_number}/{total_samples}] {case['caseId']} repeat={repeat} "
                f"success={str(sample['success']).lower()} elapsedMs={sample['elapsedMs']}",
                flush=True,
            )

    ended_at = datetime.now(timezone.utc)
    result = {
        "schemaVersion": "1.0",
        "corpus": {
            "path": corpus_path.as_posix(),
            "sha256": hashlib.sha256(corpus_path.read_bytes()).hexdigest(),
            "caseCount": len(cases),
            "domainCount": len(corpus["domains"]),
            "repeats": args.repeats,
        },
        "environment": {
            "baseUrl": base_url,
            "measurementMode": corpus.get("measurementMode"),
            "credentialsPersisted": False,
            "answersPersisted": False,
        },
        "startedAt": started_at.isoformat(),
        "endedAt": ended_at.isoformat(),
        "sampleCount": len(samples),
        "successCount": sum(1 for sample in samples if sample["success"]),
        "summary": summarize(samples),
        "samples": samples,
    }
    output_path = Path(args.output)
    output_path.parent.mkdir(parents=True, exist_ok=True)
    output_path.write_text(
        json.dumps(result, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )
    print(f"result={output_path.as_posix()}", flush=True)
    return 0 if result["successCount"] == result["sampleCount"] else 2


def validate_corpus(corpus: dict[str, Any]) -> list[dict[str, str]]:
    domains = corpus.get("domains")
    if corpus.get("schemaVersion") != "1.0" or not isinstance(domains, list) or not domains:
        raise ValueError("invalid performance corpus")
    cases: list[dict[str, str]] = []
    seen: set[str] = set()
    for domain in domains:
        domain_name = str(domain.get("domain") or "")
        domain_cases = domain.get("cases")
        if not domain_name or not isinstance(domain_cases, list) or not domain_cases:
            raise ValueError("each domain must contain cases")
        for case in domain_cases:
            case_id = str(case.get("caseId") or "")
            message = str(case.get("message") or "").strip()
            if not case_id or case_id in seen or not message:
                raise ValueError("case IDs must be unique and messages must be non-empty")
            seen.add(case_id)
            cases.append({"caseId": case_id, "domain": domain_name, "message": message})
    return cases


def login(base_url: str, username: str, password: str, timeout_seconds: float) -> str:
    payload = request_json(
        "POST",
        f"{base_url}/api/auth/web-login",
        {"name": username, "password": password},
        None,
        timeout_seconds,
    )
    data = require_success(payload, "login")
    token = data.get("token") if isinstance(data, dict) else None
    if not isinstance(token, str) or not token:
        raise RuntimeError("login response did not contain a token")
    return token


def run_case(
    *,
    base_url: str,
    token: str,
    case: dict[str, str],
    repeat: int,
    timeout_seconds: float,
) -> dict[str, Any]:
    session_id: str | None = None
    started = time.monotonic()
    try:
        session_payload = request_json(
            "POST",
            f"{base_url}/api/agent/sessions",
            {
                "clientType": "PERFORMANCE_SAMPLE",
                "requestedScopes": ["mcp:warehouse:read"],
                "mcpTransport": "STDIO",
            },
            token,
            timeout_seconds,
        )
        session = require_success(session_payload, "create session")
        session_id = str(session.get("agentSessionId") or "")
        if not session_id:
            raise RuntimeError("session response did not contain agentSessionId")

        message_started = time.monotonic()
        response_payload = request_json(
            "POST",
            f"{base_url}/api/agent/sessions/{quote(session_id)}/messages",
            {
                "message": case["message"],
                "pageContext": {
                    "path": "/assistant/performance-sample",
                    "debug": True,
                },
            },
            token,
            timeout_seconds,
        )
        elapsed_ms = max(0, round((time.monotonic() - message_started) * 1000))
        response = require_success(response_payload, "send message")
        debug = response.get("debug") if isinstance(response.get("debug"), dict) else {}
        cards = response.get("cards") if isinstance(response.get("cards"), list) else []
        answer = response.get("answer") if isinstance(response.get("answer"), str) else ""
        failure_marker = next(
            (code for marker, code in PUBLIC_FAILURE_MARKERS.items() if marker in answer),
            None,
        )
        sample: dict[str, Any] = {
            "caseId": case["caseId"],
            "domain": case["domain"],
            "repeat": repeat,
            "success": bool(answer.strip()) and failure_marker is None,
            "elapsedMs": elapsed_ms,
            "needsUserSelection": bool(response.get("needsUserSelection")),
            "cardTypes": sorted(
                {
                    str(card.get("cardType"))
                    for card in cards
                    if isinstance(card, dict) and card.get("cardType")
                }
            ),
            "errorType": failure_marker,
        }
        for field in PERFORMANCE_FIELDS:
            sample[field] = safe_int(debug.get(field))
        return sample
    except (HTTPError, URLError, TimeoutError, RuntimeError, ValueError) as exc:
        return {
            "caseId": case["caseId"],
            "domain": case["domain"],
            "repeat": repeat,
            "success": False,
            "elapsedMs": max(0, round((time.monotonic() - started) * 1000)),
            "needsUserSelection": False,
            "cardTypes": [],
            "errorType": safe_error_type(exc),
            **{field: None for field in PERFORMANCE_FIELDS},
        }
    finally:
        if session_id:
            try:
                request_json(
                    "DELETE",
                    f"{base_url}/api/agent/sessions/{quote(session_id)}",
                    {"revokedReason": "PERFORMANCE_SAMPLE_COMPLETED"},
                    token,
                    min(timeout_seconds, 20.0),
                )
            except Exception:
                pass


def request_json(
    method: str,
    url: str,
    payload: dict[str, Any] | None,
    token: str | None,
    timeout_seconds: float,
) -> dict[str, Any]:
    body = json.dumps(payload or {}, ensure_ascii=False).encode("utf-8")
    headers = {"Content-Type": "application/json", "Accept": "application/json"}
    if token:
        headers["Authorization"] = f"Bearer {token}"
    request = Request(url, data=body, headers=headers, method=method)
    with urlopen(request, timeout=timeout_seconds) as response:
        return json.loads(response.read().decode("utf-8"))


def require_success(payload: dict[str, Any], operation: str) -> Any:
    if payload.get("code") != 200:
        raise RuntimeError(f"{operation} failed with business code {payload.get('code')}")
    return payload.get("data")


def safe_int(value: Any) -> int | None:
    if isinstance(value, bool):
        return int(value)
    if isinstance(value, (int, float)):
        return max(0, round(float(value)))
    if isinstance(value, str) and value.strip().isdigit():
        return int(value.strip())
    return None


def safe_error_type(exc: Exception) -> str:
    if isinstance(exc, HTTPError):
        return f"HTTP_{exc.code}"
    if isinstance(exc, URLError):
        return "NETWORK_ERROR"
    if isinstance(exc, TimeoutError):
        return "TIMEOUT"
    return type(exc).__name__[:80]


def summarize(samples: list[dict[str, Any]]) -> dict[str, Any]:
    groups: dict[str, list[dict[str, Any]]] = {"all": samples}
    for sample in samples:
        groups.setdefault(sample["domain"], []).append(sample)
    return {name: summarize_group(values) for name, values in groups.items()}


def summarize_group(samples: list[dict[str, Any]]) -> dict[str, Any]:
    result: dict[str, Any] = {
        "sampleCount": len(samples),
        "successCount": sum(1 for sample in samples if sample["success"]),
        "selectionCount": sum(1 for sample in samples if sample["needsUserSelection"]),
    }
    for field in ("elapsedMs", *PERFORMANCE_FIELDS):
        values = [sample[field] for sample in samples if isinstance(sample.get(field), int)]
        result[field] = distribution(values)
    return result


def distribution(values: list[int]) -> dict[str, int | float]:
    if not values:
        return {"count": 0, "min": 0, "max": 0, "mean": 0, "p50": 0, "p95": 0}
    ordered = sorted(values)
    return {
        "count": len(values),
        "min": ordered[0],
        "max": ordered[-1],
        "mean": round(sum(ordered) / len(ordered), 2),
        "p50": nearest_rank(ordered, 0.50),
        "p95": nearest_rank(ordered, 0.95),
    }


def nearest_rank(ordered: list[int], percentile: float) -> int:
    rank = max(1, math.ceil(percentile * len(ordered)))
    return ordered[rank - 1]


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except KeyboardInterrupt:
        print("cancelled", file=sys.stderr)
        raise SystemExit(130)
