from __future__ import annotations

import hashlib
import json
import os
from pathlib import Path

import pytest

from app.rag.contracts import (
    BuildStatus,
    ChunkContract,
    ChunkManifest,
    CorpusManifest,
    CorpusStatus,
    IndexManifest,
    RetrievalEvaluationReport,
)
from app.rag.offline.chunker import _chunk_manifest_hash, _with_chunk_hash
from app.rag.offline.index_builder import _index_manifest_hash


FIXTURES = Path(__file__).parent / "fixtures"


def _release_root() -> Path:
    configured = os.getenv("LAIBIN_RAG_INDEXED_RELEASE")
    if not configured:
        pytest.skip("LAIBIN_RAG_INDEXED_RELEASE is not configured")
    root = Path(configured)
    if not root.is_dir():
        pytest.fail("LAIBIN_RAG_INDEXED_RELEASE does not exist")
    return root


def _load_json(path: Path) -> dict[str, object]:
    return json.loads(path.read_text(encoding="utf-8"))


def _sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for block in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def test_real_rag02_release_matches_frozen_chunk_index_and_evaluation_golden() -> None:
    root = _release_root()
    golden = _load_json(FIXTURES / "rag02-golden-v1.json")
    corpus = CorpusManifest.model_validate(_load_json(root / "corpus-manifest.json"))
    chunk_manifest = ChunkManifest.model_validate(
        _load_json(root / "chunks" / "chunk-manifest.json")
    )
    index_manifest = IndexManifest.model_validate(
        _load_json(root / "indexes" / "index-manifest.json")
    )
    evaluation = RetrievalEvaluationReport.model_validate(
        _load_json(root / "qa" / "retrieval-evaluation-report.json")
    )
    chunks = tuple(
        ChunkContract.model_validate_json(line)
        for line in (root / "chunks" / "chunks.jsonl").read_text(encoding="utf-8").splitlines()
        if line.strip()
    )

    assert corpus.status == CorpusStatus.VALIDATED
    assert corpus.corpusVersion == golden["corpusVersion"]
    assert corpus.inventorySha256 == golden["inventorySha256"]
    assert corpus.reviewProfileSha256 == golden["reviewProfileSha256"]
    assert corpus.documentManifestSha256 == golden["documentManifestSha256"]
    assert corpus.chunkManifestSha256 == golden["chunkManifestSha256"]
    assert corpus.lexicalIndexSha256 == golden["lexicalIndexSha256"]
    assert corpus.vectorIndexSha256 == golden["vectorIndexSha256"]
    assert corpus.documentCount == golden["documentCount"]
    assert corpus.chunkCount == golden["chunkCount"]
    assert corpus.allowedRoles == ("ADMIN", "SUPER_ADMIN")

    assert _chunk_manifest_hash(chunk_manifest) == chunk_manifest.chunkManifestSha256
    assert chunk_manifest.chunkManifestSha256 == golden["chunkManifestSha256"]
    assert {
        key.value: value for key, value in chunk_manifest.chunkTypeCounts.items()
    } == golden["chunkTypeCounts"]
    assert len(chunks) == chunk_manifest.chunkCount
    assert [chunk.chunkId for chunk in chunks] == sorted(chunk.chunkId for chunk in chunks)
    assert all(_with_chunk_hash(chunk).contentSha256 == chunk.contentSha256 for chunk in chunks)
    assert all(chunk.embeddingRef == f"vector/row/{row}" for row, chunk in enumerate(chunks))
    assert all(chunk.sourceText.strip() for chunk in chunks)

    assert _index_manifest_hash(index_manifest) == index_manifest.indexManifestSha256
    assert index_manifest.indexManifestSha256 == golden["indexManifestSha256"]
    assert index_manifest.embedding.provider == "fastembed"
    assert index_manifest.embedding.providerVersion == "0.8.0"
    assert index_manifest.embedding.model == "BAAI/bge-small-zh-v1.5"
    assert index_manifest.embedding.dimension == 512
    assert index_manifest.embedding.modelArtifactSha256 == golden["modelArtifactSha256"]
    assert index_manifest.retrieval["version"] == "rrf-control-aware/1.0.2"
    assert _sha256_file(root / "indexes" / "lexical" / "chunks.sqlite3") == golden[
        "lexicalIndexSha256"
    ]

    assert evaluation.status == BuildStatus.SUCCEEDED
    assert evaluation.caseCount == golden["evaluationCaseCount"]
    assert evaluation.failedCount == 0
    assert evaluation.evaluationSetSha256 == golden["evaluationSetSha256"]
    assert evaluation.indexManifestSha256 == golden["indexManifestSha256"]
    assert evaluation.metrics["recallAt5"] == 1.0
    assert evaluation.metrics["exactProductAndControlAccuracy"] == 1.0
    assert evaluation.metrics["numericTop3Accuracy"] == 1.0
    assert evaluation.metrics["numericEvidenceAccuracy"] == 1.0
    assert evaluation.metrics["similarDocumentConfusionCount"] == 0
    assert evaluation.metrics["accessBoundaryAccuracy"] == 1.0
    assert evaluation.metrics["missingSourceTextCount"] == 0
    assert evaluation.metrics["p95LatencyMilliseconds"] <= 300.0

    by_id = {chunk.chunkId: chunk for chunk in chunks}
    white_control = by_id["doc-161898e85c006f9f13db2f01/control/6"]
    assert white_control.controlPointLabels == ("CCP3", "CCP1")
    assert all(term in white_control.content for term in ("SA-990", "Φ1.5mm", "Φ2.5mm"))
    assert by_id["doc-94170623c8ba7c517d863337/page/012/01"].productFamilies == (
        "玫瑰花黑糖",
        "礼品糖",
    )
