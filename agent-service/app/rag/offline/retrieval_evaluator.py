from __future__ import annotations

import hashlib
import json
from datetime import datetime
from pathlib import Path
from typing import Any, Iterable
from zoneinfo import ZoneInfo

from pydantic import ValidationError

from app.rag.config import TIMEZONE
from app.rag.contracts import (
    BuildStatus,
    RetrievalEvaluationCase,
    RetrievalEvaluationCaseResult,
    RetrievalEvaluationReport,
    RetrievalStatus,
)
from app.rag.runtime.hybrid_retriever import HybridIndex
from app.rag.offline.source_inventory import RagBuildError


MINIMUM_FORMAL_CASES = 60
MAXIMUM_FORMAL_CASES = 100


def _canonical_hash(payload: Any) -> str:
    encoded = json.dumps(
        payload,
        ensure_ascii=False,
        sort_keys=True,
        separators=(",", ":"),
    ).encode("utf-8")
    return hashlib.sha256(encoded).hexdigest()


def load_evaluation_cases(
    path: Path,
    *,
    enforce_formal_size: bool = True,
) -> tuple[RetrievalEvaluationCase, ...]:
    cases = []
    try:
        with path.open("r", encoding="utf-8") as handle:
            for line_number, line in enumerate(handle, start=1):
                if not line.strip():
                    continue
                try:
                    cases.append(RetrievalEvaluationCase.model_validate_json(line))
                except ValidationError as exc:
                    raise RagBuildError(
                        "EVALUATION_CASE_INVALID",
                        f"Invalid evaluation case at JSONL line {line_number}.",
                    ) from exc
    except OSError as exc:
        raise RagBuildError("EVALUATION_SET_READ_FAILED", "Unable to read evaluation JSONL.") from exc
    result = tuple(cases)
    if enforce_formal_size and not MINIMUM_FORMAL_CASES <= len(result) <= MAXIMUM_FORMAL_CASES:
        raise RagBuildError(
            "EVALUATION_SET_SIZE_INVALID",
            f"Formal evaluation requires {MINIMUM_FORMAL_CASES}-{MAXIMUM_FORMAL_CASES} cases.",
        )
    case_ids = [case.caseId for case in result]
    if case_ids != sorted(case_ids):
        raise RagBuildError(
            "EVALUATION_CASE_ORDER_INVALID", "Evaluation cases must use sorted caseId order."
        )
    if len(case_ids) != len(set(case_ids)):
        raise RagBuildError("EVALUATION_CASE_DUPLICATE", "Evaluation case IDs must be unique.")
    return result


def evaluation_set_sha256(cases: Iterable[RetrievalEvaluationCase]) -> str:
    return _canonical_hash(
        {
            "schemaVersion": 1,
            "cases": [case.model_dump(mode="json") for case in cases],
        }
    )


def _percentile_95(values: list[float]) -> float:
    if not values:
        return 0.0
    ordered = sorted(values)
    index = max(0, min(len(ordered) - 1, int((len(ordered) - 1) * 0.95 + 0.999999)))
    return ordered[index]


def _ratio(numerator: int, denominator: int) -> float:
    return numerator / denominator if denominator else 1.0


def evaluate_retrieval(
    index: HybridIndex,
    cases: tuple[RetrievalEvaluationCase, ...],
    *,
    generated_at: datetime | None = None,
) -> RetrievalEvaluationReport:
    case_results = []
    success_target_count = 0
    success_match_count = 0
    category_target: dict[str, int] = {}
    category_match: dict[str, int] = {}
    category_evidence: dict[str, int] = {}
    category_forbidden_hits: dict[str, int] = {}
    latencies = []
    for case in cases:
        result = index.search(
            case.query,
            role=case.role,
            mode=case.mode,
            limit=case.topK,
        )
        latencies.append(result.elapsedMilliseconds)
        hits = result.hits[: case.requiredRank]
        hit_chunk_ids = tuple(hit.chunkId for hit in result.hits)
        hit_document_ids = tuple(hit.documentId for hit in result.hits)
        expected_chunks = set(case.expectedChunkIds)
        expected_documents = set(case.expectedDocumentIds)
        target_hits = [
            hit
            for hit in hits
            if (expected_chunks and hit.chunkId in expected_chunks)
            or (not expected_chunks and expected_documents and hit.documentId in expected_documents)
        ]
        target_matched = bool(target_hits) if case.expectedStatus == RetrievalStatus.SUCCEEDED else True
        evidence_matched = True
        if case.requiredEvidenceTerms:
            evidence_matched = any(
                all(
                    term.casefold()
                    in (
                        index.chunks[hit.chunkId].content
                        + "\n"
                        + index.chunks[hit.chunkId].sourceText
                    ).casefold()
                    for term in case.requiredEvidenceTerms
                )
                for hit in target_hits
            )
        forbidden_hit_count = sum(
            hit.documentId in set(case.forbiddenDocumentIds) for hit in hits
        )
        failures = []
        if result.status != case.expectedStatus:
            failures.append(
                f"status {result.status.value} != {case.expectedStatus.value}"
            )
        if not target_matched:
            failures.append(f"expected target missing from top {case.requiredRank}")
        if not evidence_matched:
            failures.append("required evidence terms missing from the matched target")
        if forbidden_hit_count:
            failures.append(f"{forbidden_hit_count} forbidden similar-document hit(s)")
        passed = not failures
        case_results.append(
            RetrievalEvaluationCaseResult(
                caseId=case.caseId,
                category=case.category,
                passed=passed,
                actualStatus=result.status,
                expectedStatus=case.expectedStatus,
                elapsedMilliseconds=result.elapsedMilliseconds,
                hitChunkIds=hit_chunk_ids,
                hitDocumentIds=hit_document_ids,
                targetMatched=target_matched,
                evidenceMatched=evidence_matched,
                forbiddenHitCount=forbidden_hit_count,
                failureReasons=tuple(failures),
            )
        )
        if case.expectedStatus == RetrievalStatus.SUCCEEDED:
            success_target_count += 1
            success_match_count += int(target_matched)
            category_target[case.category] = category_target.get(case.category, 0) + 1
            category_match[case.category] = category_match.get(case.category, 0) + int(
                target_matched
            )
            category_evidence[case.category] = category_evidence.get(case.category, 0) + int(
                evidence_matched
            )
        category_forbidden_hits[case.category] = (
            category_forbidden_hits.get(case.category, 0) + forbidden_hit_count
        )

    exact_categories = {"EXACT_PRODUCT", "CONTROL_POINT"}
    exact_total = sum(category_target.get(category, 0) for category in exact_categories)
    exact_matched = sum(category_match.get(category, 0) for category in exact_categories)
    numeric_total = category_target.get("NUMERIC", 0)
    access_results = [item for item in case_results if item.category == "ACCESS_BOUNDARY"]
    missing_source_count = sum(
        not chunk.sourceText.strip() for chunk in index.ordered_chunks
    )
    metrics: dict[str, float | int] = {
        "recallAt5": _ratio(success_match_count, success_target_count),
        "exactProductAndControlAccuracy": _ratio(exact_matched, exact_total),
        "numericTop3Accuracy": _ratio(category_match.get("NUMERIC", 0), numeric_total),
        "numericEvidenceAccuracy": _ratio(
            category_evidence.get("NUMERIC", 0), numeric_total
        ),
        "similarDocumentConfusionCount": sum(category_forbidden_hits.values()),
        "accessBoundaryAccuracy": _ratio(
            sum(item.passed for item in access_results), len(access_results)
        ),
        "missingSourceTextCount": missing_source_count,
        "p95LatencyMilliseconds": _percentile_95(latencies),
    }
    thresholds: dict[str, float | int] = {
        "recallAt5Minimum": 0.90,
        "exactProductAndControlAccuracyMinimum": 1.0,
        "numericTop3AccuracyMinimum": 1.0,
        "numericEvidenceAccuracyMinimum": 1.0,
        "similarDocumentConfusionCountMaximum": 0,
        "accessBoundaryAccuracyMinimum": 1.0,
        "missingSourceTextCountMaximum": 0,
        "p95LatencyMillisecondsMaximum": 300.0,
    }
    gates_passed = (
        metrics["recallAt5"] >= thresholds["recallAt5Minimum"]
        and metrics["exactProductAndControlAccuracy"]
        >= thresholds["exactProductAndControlAccuracyMinimum"]
        and metrics["numericTop3Accuracy"] >= thresholds["numericTop3AccuracyMinimum"]
        and metrics["numericEvidenceAccuracy"]
        >= thresholds["numericEvidenceAccuracyMinimum"]
        and metrics["similarDocumentConfusionCount"]
        <= thresholds["similarDocumentConfusionCountMaximum"]
        and metrics["accessBoundaryAccuracy"]
        >= thresholds["accessBoundaryAccuracyMinimum"]
        and metrics["missingSourceTextCount"] <= thresholds["missingSourceTextCountMaximum"]
        and metrics["p95LatencyMilliseconds"]
        <= thresholds["p95LatencyMillisecondsMaximum"]
    )
    failed_ids = tuple(item.caseId for item in case_results if not item.passed)
    effective_time = generated_at or datetime.now(ZoneInfo(TIMEZONE))
    if index.index_manifest.indexManifestSha256 is None:
        raise RagBuildError(
            "EVALUATION_INDEX_HASH_MISSING", "The index manifest is not frozen."
        )
    return RetrievalEvaluationReport(
        corpusVersion=index.corpus.corpusVersion,
        generatedAt=effective_time,
        status=BuildStatus.SUCCEEDED if gates_passed else BuildStatus.FAILED,
        evaluationSetSha256=evaluation_set_sha256(cases),
        indexManifestSha256=index.index_manifest.indexManifestSha256,
        caseCount=len(cases),
        passedCount=len(cases) - len(failed_ids),
        failedCount=len(failed_ids),
        metrics=metrics,
        thresholds=thresholds,
        failedCaseIds=failed_ids,
        cases=tuple(case_results),
    )


def write_evaluation_report(release_root: Path, report: RetrievalEvaluationReport) -> Path:
    target = release_root / "qa" / "retrieval-evaluation-report.json"
    if target.exists():
        raise RagBuildError(
            "EVALUATION_REPORT_ALREADY_EXISTS",
            "The retrieval evaluation report already exists and will not be overwritten.",
        )
    try:
        target.write_text(
            json.dumps(report.model_dump(mode="json"), ensure_ascii=False, indent=2, sort_keys=True)
            + "\n",
            encoding="utf-8",
            newline="\n",
        )
    except OSError as exc:
        raise RagBuildError("EVALUATION_REPORT_WRITE_FAILED", "Unable to write evaluation report.") from exc
    return target
