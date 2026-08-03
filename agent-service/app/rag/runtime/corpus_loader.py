from __future__ import annotations

from dataclasses import dataclass
import hashlib
from pathlib import Path
import stat
from typing import Any, Callable, TYPE_CHECKING

from pydantic import ValidationError

from app.rag.config import ALLOWED_ROLES, CORPUS_ID, SCHEMA_VERSION
from app.rag.contracts import (
    BuildStatus,
    DocumentContract,
    DocumentStatus,
    RetrievalEvaluationReport,
)
from app.rag.runtime.contracts import CurrentCorpusPointer
from app.rag.runtime.errors import RagRuntimeError

if TYPE_CHECKING:
    from app.rag.runtime.hybrid_retriever import HybridIndex


POINTER_MAX_BYTES = 64 * 1024
EVALUATION_REPORT_MAX_BYTES = 8 * 1024 * 1024


@dataclass(frozen=True)
class RagRuntimeConfiguration:
    enabled: bool = False
    required: bool = False
    root: str = ""
    model_path: str = ""
    model_name: str = "BAAI/bge-small-zh-v1.5"
    model_threads: int = 2
    query_timeout_ms: int = 5000
    max_evidence: int = 5


@dataclass(frozen=True)
class LoadedRagRuntime:
    pointer: CurrentCorpusPointer
    retriever: HybridIndex


def _is_reparse_point(path: Path) -> bool:
    try:
        attributes = getattr(path.lstat(), "st_file_attributes", 0)
    except OSError as exc:
        raise RagRuntimeError("RAG_PATH_INSPECTION_FAILED", "Unable to inspect a RAG path.") from exc
    return bool(attributes & getattr(stat, "FILE_ATTRIBUTE_REPARSE_POINT", 0))


def _reject_link(path: Path) -> None:
    if path.is_symlink() or _is_reparse_point(path):
        raise RagRuntimeError(
            "RAG_LINK_FORBIDDEN", "Symbolic links and reparse points are forbidden in RAG artifacts."
        )


def _resolve_directory(path: Path, code: str) -> Path:
    candidate = path.expanduser().absolute()
    if not candidate.exists():
        raise RagRuntimeError(code, "A required RAG directory does not exist.")
    _reject_link(candidate)
    if not candidate.is_dir():
        raise RagRuntimeError(code, "A required RAG path is not a directory.")
    try:
        return candidate.resolve(strict=True)
    except OSError as exc:
        raise RagRuntimeError(code, "Unable to resolve a required RAG directory.") from exc


def _require_regular_file(path: Path) -> None:
    if not path.exists():
        raise RagRuntimeError("RAG_ARTIFACT_MISSING", "A required RAG artifact is missing.")
    _reject_link(path)
    if not path.is_file():
        raise RagRuntimeError("RAG_ARTIFACT_INVALID", "A required RAG artifact is not a file.")


def _sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    try:
        with path.open("rb") as handle:
            for block in iter(lambda: handle.read(1024 * 1024), b""):
                digest.update(block)
    except OSError as exc:
        raise RagRuntimeError("RAG_ARTIFACT_READ_FAILED", "Unable to read a RAG artifact.") from exc
    return digest.hexdigest()


def _load_pointer(root: Path) -> CurrentCorpusPointer:
    pointer_path = root / "current.json"
    _require_regular_file(pointer_path)
    try:
        if pointer_path.stat().st_size > POINTER_MAX_BYTES:
            raise RagRuntimeError("RAG_POINTER_TOO_LARGE", "The RAG pointer exceeds its size limit.")
        raw = pointer_path.read_bytes()
        pointer = CurrentCorpusPointer.model_validate_json(raw)
    except RagRuntimeError:
        raise
    except (OSError, UnicodeError, ValidationError) as exc:
        raise RagRuntimeError("RAG_POINTER_INVALID", "The RAG pointer is invalid.") from exc
    if pointer.schemaVersion != SCHEMA_VERSION or pointer.corpusId != CORPUS_ID:
        raise RagRuntimeError("RAG_POINTER_CONTRACT_MISMATCH", "The RAG pointer is unsupported.")
    return pointer


def _resolve_release(root: Path, pointer: CurrentCorpusPointer) -> Path:
    releases_root = _resolve_directory(root / "releases", "RAG_RELEASES_ROOT_MISSING")
    release = _resolve_directory(releases_root / pointer.releaseName, "RAG_RELEASE_MISSING")
    try:
        release.relative_to(releases_root)
    except ValueError as exc:
        raise RagRuntimeError("RAG_RELEASE_PATH_ESCAPE", "The RAG release path is outside its root.") from exc
    return _validate_release_layout(release)


def _validate_release_layout(release_root: Path) -> Path:
    release = _resolve_directory(release_root, "RAG_RELEASE_MISSING")
    for relative in (
        "documents",
        "chunks",
        "indexes",
        "indexes/lexical",
        "indexes/vector",
        "qa",
    ):
        directory = _resolve_directory(release / relative, "RAG_ARTIFACT_DIRECTORY_MISSING")
        try:
            directory.relative_to(release)
        except ValueError as exc:
            raise RagRuntimeError(
                "RAG_RELEASE_PATH_ESCAPE", "A RAG artifact directory escaped the release root."
            ) from exc
    required_files = (
        "corpus-manifest.json",
        "chunks/chunk-manifest.json",
        "chunks/chunks.jsonl",
        "indexes/index-manifest.json",
        "indexes/lexical/chunks.sqlite3",
        "indexes/vector/embeddings.npy",
        "indexes/vector/row-map.jsonl",
        "qa/retrieval-evaluation-report.json",
    )
    for relative in required_files:
        _require_regular_file(release / relative)
    return release


def _validate_documents(release: Path, expected_count: int) -> set[str]:
    document_paths = tuple(sorted((release / "documents").glob("*.document.json")))
    if len(document_paths) != expected_count:
        raise RagRuntimeError(
            "RAG_DOCUMENT_COUNT_MISMATCH", "The RAG document count is inconsistent."
        )
    document_ids: set[str] = set()
    for path in document_paths:
        _require_regular_file(path)
        try:
            document = DocumentContract.model_validate_json(path.read_bytes())
        except (OSError, UnicodeError, ValidationError) as exc:
            raise RagRuntimeError(
                "RAG_DOCUMENT_INVALID", "An approved RAG document is invalid."
            ) from exc
        if (
            document.status != DocumentStatus.ACTIVE
            or set(document.allowedRoles) != set(ALLOWED_ROLES)
            or document.documentId in document_ids
        ):
            raise RagRuntimeError(
                "RAG_DOCUMENT_CONTRACT_MISMATCH",
                "An approved RAG document contract is inconsistent.",
            )
        document_ids.add(document.documentId)
    return document_ids


def _load_evaluation_report(path: Path) -> RetrievalEvaluationReport:
    try:
        if path.stat().st_size > EVALUATION_REPORT_MAX_BYTES:
            raise RagRuntimeError(
                "RAG_EVALUATION_REPORT_TOO_LARGE", "The evaluation report exceeds its size limit."
            )
        return RetrievalEvaluationReport.model_validate_json(path.read_bytes())
    except RagRuntimeError:
        raise
    except (OSError, UnicodeError, ValidationError) as exc:
        raise RagRuntimeError(
            "RAG_EVALUATION_REPORT_INVALID", "The retrieval evaluation report is invalid."
        ) from exc


def _validate_evaluation(
    report: RetrievalEvaluationReport,
    pointer: CurrentCorpusPointer,
    index_manifest_sha256: str,
) -> None:
    if (
        report.schemaVersion != SCHEMA_VERSION
        or report.corpusId != CORPUS_ID
        or report.corpusVersion != pointer.corpusVersion
        or report.status != BuildStatus.SUCCEEDED
        or report.caseCount < 60
        or report.passedCount != report.caseCount
        or report.failedCount != 0
        or report.failedCaseIds
        or report.indexManifestSha256 != index_manifest_sha256
        or report.indexManifestSha256 != pointer.indexManifestSha256
        or report.evaluationSetSha256 != pointer.evaluationSetSha256
    ):
        raise RagRuntimeError(
            "RAG_EVALUATION_GATE_FAILED", "The approved retrieval evaluation gate is not satisfied."
        )
    for threshold_name, threshold_value in report.thresholds.items():
        if threshold_name.endswith("Minimum"):
            metric_name = threshold_name[: -len("Minimum")]
            metric_value = report.metrics.get(metric_name)
            if metric_value is None or metric_value < threshold_value:
                raise RagRuntimeError(
                    "RAG_EVALUATION_GATE_FAILED",
                    "The approved retrieval evaluation gate is not satisfied.",
                )
        elif threshold_name.endswith("Maximum"):
            metric_name = threshold_name[: -len("Maximum")]
            metric_value = report.metrics.get(metric_name)
            if metric_value is None or metric_value > threshold_value:
                raise RagRuntimeError(
                    "RAG_EVALUATION_GATE_FAILED",
                    "The approved retrieval evaluation gate is not satisfied.",
                )
        else:
            raise RagRuntimeError(
                "RAG_EVALUATION_THRESHOLD_INVALID",
                "The retrieval evaluation threshold contract is invalid.",
            )


def validate_release_artifacts(
    release_root: Path,
    pointer: CurrentCorpusPointer,
    *,
    retriever_factory: Callable[[Path], Any] | None = None,
) -> Any:
    """Validate one immutable release without loading a query embedding provider."""

    release = _validate_release_layout(release_root)
    corpus_manifest_path = release / "corpus-manifest.json"
    if _sha256_file(corpus_manifest_path) != pointer.corpusManifestSha256:
        raise RagRuntimeError(
            "RAG_CORPUS_MANIFEST_HASH_INVALID", "The corpus manifest checksum is invalid."
        )
    if retriever_factory is None:
        from app.rag.runtime.hybrid_retriever import HybridIndex

        retriever_factory = HybridIndex
    retriever = retriever_factory(release)
    if (
        retriever.corpus.corpusVersion != pointer.corpusVersion
        or retriever.index_manifest.indexManifestSha256 != pointer.indexManifestSha256
    ):
        raise RagRuntimeError("RAG_POINTER_LINEAGE_INVALID", "The RAG pointer lineage is invalid.")
    document_ids = _validate_documents(release, retriever.corpus.documentCount)
    if {chunk.documentId for chunk in retriever.ordered_chunks} != document_ids:
        raise RagRuntimeError(
            "RAG_DOCUMENT_CHUNK_LINEAGE_INVALID",
            "RAG document and chunk lineage is inconsistent.",
        )
    report_path = release / "qa" / "retrieval-evaluation-report.json"
    if _sha256_file(report_path) != pointer.evaluationReportSha256:
        raise RagRuntimeError(
            "RAG_EVALUATION_REPORT_HASH_INVALID",
            "The retrieval evaluation report checksum is invalid.",
        )
    report = _load_evaluation_report(report_path)
    _validate_evaluation(report, pointer, retriever.index_manifest.indexManifestSha256 or "")
    return retriever


def load_current_corpus(
    configuration: RagRuntimeConfiguration,
    *,
    retriever_factory: Callable[[Path], Any] | None = None,
    provider_factory: Callable[[RagRuntimeConfiguration], Any] | None = None,
) -> LoadedRagRuntime:
    if configuration.required and not configuration.enabled:
        raise RagRuntimeError("RAG_REQUIRED_WHILE_DISABLED", "Required RAG cannot be disabled.")
    if not configuration.enabled:
        raise RagRuntimeError("RAG_DISABLED", "RAG is disabled.")
    if not configuration.root.strip():
        raise RagRuntimeError("RAG_ROOT_NOT_CONFIGURED", "The RAG root is not configured.")
    if not configuration.model_path.strip():
        raise RagRuntimeError(
            "RAG_EMBEDDING_MODEL_NOT_CONFIGURED", "The query embedding model is not configured."
        )
    root = _resolve_directory(Path(configuration.root), "RAG_ROOT_MISSING")
    pointer = _load_pointer(root)
    release = _resolve_release(root, pointer)
    retriever = validate_release_artifacts(
        release,
        pointer,
        retriever_factory=retriever_factory,
    )
    if provider_factory is None:
        from app.rag.runtime.local_embedding import FastEmbedQueryProvider

        provider = FastEmbedQueryProvider(
            Path(configuration.model_path),
            model_name=configuration.model_name,
            threads=configuration.model_threads,
        )
    else:
        provider = provider_factory(configuration)
    retriever.bind_provider(provider)
    return LoadedRagRuntime(pointer=pointer, retriever=retriever)
