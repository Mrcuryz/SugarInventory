from __future__ import annotations

import hashlib
from pathlib import Path

from app.rag.contracts import (
    BuildStatus,
    ChunkContract,
    ChunkManifest,
    ChunkSummary,
    CorpusManifest,
    DocumentStatus,
    RetrievalEvaluationCaseResult,
    RetrievalEvaluationReport,
    RetrievalStatus,
)
from app.rag.offline.index_builder import build_indexes
from app.rag.offline.chunker import _chunk_manifest_hash, _with_chunk_hash
from app.rag.runtime.contracts import CurrentCorpusPointer
from tests.rag.test_index_builder import FakeEmbeddingProvider, NOW, _document, _write_release


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for block in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def build_runtime_root(
    root: Path,
    corpus_version: str = "test-corpus-v1",
) -> tuple[Path, FakeEmbeddingProvider, CurrentCorpusPointer]:
    release = root / "releases" / corpus_version
    _write_release(release, corpus_version)
    documents = (
        _document("doc-aaaaaaaaaaaaaaaaaaaaaaaa", "白砂糖分装工艺", "白砂糖").model_copy(
            update={"status": DocumentStatus.ACTIVE}
        ),
        _document("doc-bbbbbbbbbbbbbbbbbbbbbbbb", "红糖分装工艺", "红糖").model_copy(
            update={"status": DocumentStatus.ACTIVE}
        ),
    )
    documents_root = release / "documents"
    documents_root.mkdir()
    for document in documents:
        (documents_root / f"{document.documentId}.document.json").write_text(
            document.model_dump_json(indent=2) + "\n", encoding="utf-8", newline="\n"
        )
    chunk_path = release / "chunks" / "chunks.jsonl"
    chunks = tuple(
        _with_chunk_hash(
            ChunkContract.model_validate_json(line).model_copy(
                update={"status": DocumentStatus.ACTIVE}
            )
        )
        for line in chunk_path.read_text(encoding="utf-8").splitlines()
        if line.strip()
    )
    chunk_path.write_text(
        "".join(chunk.model_dump_json() + "\n" for chunk in chunks),
        encoding="utf-8",
        newline="\n",
    )
    manifest_path = release / "chunks" / "chunk-manifest.json"
    manifest = ChunkManifest.model_validate_json(manifest_path.read_text(encoding="utf-8"))
    manifest = manifest.model_copy(
        update={
            "chunks": tuple(
                ChunkSummary(
                    chunkId=chunk.chunkId,
                    documentId=chunk.documentId,
                    chunkType=chunk.chunkType,
                    contentSha256=chunk.contentSha256,
                    embeddingRef=chunk.embeddingRef or "",
                )
                for chunk in chunks
            ),
            "chunkManifestSha256": "0" * 64,
        }
    )
    manifest = manifest.model_copy(
        update={"chunkManifestSha256": _chunk_manifest_hash(manifest)}
    )
    manifest_path.write_text(
        manifest.model_dump_json(indent=2) + "\n", encoding="utf-8", newline="\n"
    )
    corpus_path = release / "corpus-manifest.json"
    corpus = CorpusManifest.model_validate_json(corpus_path.read_text(encoding="utf-8"))
    corpus_path.write_text(
        corpus.model_copy(
            update={"chunkManifestSha256": manifest.chunkManifestSha256}
        ).model_dump_json(indent=2)
        + "\n",
        encoding="utf-8",
        newline="\n",
    )
    provider = FakeEmbeddingProvider()
    index_manifest, _ = build_indexes(release, provider, built_at=NOW)
    cases = tuple(
        RetrievalEvaluationCaseResult(
            caseId=f"case-{index:03d}",
            category="RUNTIME_GOLDEN",
            passed=True,
            actualStatus=RetrievalStatus.SUCCEEDED,
            expectedStatus=RetrievalStatus.SUCCEEDED,
            elapsedMilliseconds=1.0,
            targetMatched=True,
            evidenceMatched=True,
        )
        for index in range(60)
    )
    evaluation_set_sha256 = "e" * 64
    report = RetrievalEvaluationReport(
        corpusVersion=corpus_version,
        generatedAt=NOW,
        status=BuildStatus.SUCCEEDED,
        evaluationSetSha256=evaluation_set_sha256,
        indexManifestSha256=index_manifest.indexManifestSha256 or "",
        caseCount=len(cases),
        passedCount=len(cases),
        failedCount=0,
        metrics={
            "recallAt5": 1.0,
            "p95LatencyMilliseconds": 10.0,
            "accessBoundaryAccuracy": 1.0,
        },
        thresholds={
            "recallAt5Minimum": 0.9,
            "p95LatencyMillisecondsMaximum": 300.0,
            "accessBoundaryAccuracyMinimum": 1.0,
        },
        cases=cases,
    )
    qa = release / "qa"
    qa.mkdir()
    (qa / "retrieval-evaluation-report.json").write_text(
        report.model_dump_json(indent=2) + "\n", encoding="utf-8", newline="\n"
    )
    pointer = CurrentCorpusPointer(
        corpusVersion=corpus_version,
        releaseName=corpus_version,
        corpusManifestSha256=sha256_file(release / "corpus-manifest.json"),
        indexManifestSha256=index_manifest.indexManifestSha256 or "",
        evaluationSetSha256=evaluation_set_sha256,
        evaluationReportSha256=sha256_file(qa / "retrieval-evaluation-report.json"),
        publishedAt=NOW,
        publishedBy="pytest",
    )
    (root / "current.json").write_text(
        pointer.model_dump_json(indent=2) + "\n", encoding="utf-8", newline="\n"
    )
    return release, provider, pointer
