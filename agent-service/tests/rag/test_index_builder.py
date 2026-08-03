from __future__ import annotations

import json
import shutil
from datetime import datetime
from pathlib import Path
from zoneinfo import ZoneInfo

import numpy as np
import pytest

from app.rag.contracts import (
    ChunkManifest,
    ChunkSummary,
    ChunkType,
    ControlPointContract,
    CorpusManifest,
    CorpusStatus,
    DocumentContract,
    EmbeddingContract,
    ProcessContract,
    ProcessStepContract,
    RetrievalMode,
    RetrievalEvaluationCase,
    RetrievalStatus,
)
from app.rag.offline.chunker import CHUNKER_VERSION, _chunk_manifest_hash, build_chunks
from app.rag.offline.index_builder import HybridIndex, build_indexes, lexical_tokens
from app.rag.offline.retrieval_evaluator import evaluate_retrieval, evaluation_set_sha256
from app.rag.offline.source_inventory import RagBuildError


FIXTURES = Path(__file__).parent / "fixtures"
NOW = datetime(2026, 8, 1, 10, 0, tzinfo=ZoneInfo("Asia/Shanghai"))


class FakeEmbeddingProvider:
    contract = EmbeddingContract(
        provider="test",
        providerVersion="1",
        model="term-features",
        dimension=5,
        normalization="L2",
        maxTokens=512,
        modelArtifactSha256="e" * 64,
    )

    @staticmethod
    def _matrix(texts: list[str] | tuple[str, ...]) -> np.ndarray:
        rows = []
        for text in texts:
            row = np.asarray(
                [
                    1.0,
                    float(text.count("白砂糖")),
                    float(text.count("红糖")),
                    float(text.upper().count("CCP2")),
                    float(text.count("金属检测")),
                ],
                dtype=np.float32,
            )
            rows.append(row / np.linalg.norm(row))
        return np.stack(rows)

    def embed_documents(self, texts: list[str]) -> np.ndarray:
        return self._matrix(texts)

    def embed_queries(self, texts: list[str]) -> np.ndarray:
        return self._matrix(texts)


def _document(document_id: str, title: str, product: str) -> DocumentContract:
    payload = json.loads((FIXTURES / "document-v1.json").read_text(encoding="utf-8"))
    base = DocumentContract.model_validate(payload)
    control = ControlPointContract(type="CCP", label="CCP2", sourceLabel="CCP2")
    step = ProcessStepContract(
        stepId=f"{document_id}/step/1",
        stepNo="1",
        name="金属检测",
        sourceText=f"{product}金属检测 CCP2",
        normalizedText=f"{product}金属检测 CCP2",
        controlPoint=control,
        controlPoints=(control,),
    )
    return base.model_copy(
        update={
            "documentId": document_id,
            "displayTitle": title,
            "productFamilies": (product,),
            "process": ProcessContract(steps=(step,)),
        }
    )


def _write_release(root: Path, corpus_version: str = "test-corpus-v1") -> tuple:
    chunks = build_chunks(
        (
            _document("doc-aaaaaaaaaaaaaaaaaaaaaaaa", "白砂糖分装工艺", "白砂糖"),
            _document("doc-bbbbbbbbbbbbbbbbbbbbbbbb", "红糖分装工艺", "红糖"),
        )
    )
    summaries = tuple(
        ChunkSummary(
            chunkId=chunk.chunkId,
            documentId=chunk.documentId,
            chunkType=chunk.chunkType,
            contentSha256=chunk.contentSha256,
            embeddingRef=chunk.embeddingRef or "",
        )
        for chunk in chunks
    )
    draft = ChunkManifest(
        corpusVersion=corpus_version,
        generatedAt=NOW,
        chunkerVersion=CHUNKER_VERSION,
        documentManifestSha256="a" * 64,
        documentCount=2,
        chunkCount=len(chunks),
        chunkTypeCounts={
            chunk_type: sum(item.chunkType == chunk_type for item in chunks)
            for chunk_type in ChunkType
        },
        chunks=summaries,
        chunkManifestSha256="0" * 64,
    )
    manifest = draft.model_copy(update={"chunkManifestSha256": _chunk_manifest_hash(draft)})
    corpus = CorpusManifest(
        corpusVersion=corpus_version,
        status=CorpusStatus.VALIDATED,
        builtAt=NOW,
        documentCount=2,
        chunkCount=len(chunks),
        documentManifestSha256="a" * 64,
        inventorySha256="b" * 64,
        reviewProfileSha256="c" * 64,
        chunkManifestSha256=manifest.chunkManifestSha256,
    )
    (root / "chunks").mkdir(parents=True)
    (root / "indexes").mkdir()
    (root / "chunks" / "chunks.jsonl").write_text(
        "".join(
            json.dumps(chunk.model_dump(mode="json"), ensure_ascii=False, sort_keys=True)
            + "\n"
            for chunk in chunks
        ),
        encoding="utf-8",
        newline="\n",
    )
    (root / "chunks" / "chunk-manifest.json").write_text(
        manifest.model_dump_json(indent=2) + "\n", encoding="utf-8", newline="\n"
    )
    (root / "corpus-manifest.json").write_text(
        corpus.model_dump_json(indent=2) + "\n", encoding="utf-8", newline="\n"
    )
    return chunks


def test_lexical_tokens_include_cjk_unigrams_bigrams_and_exact_controls() -> None:
    tokens = lexical_tokens("白砂糖金属检测 CCP2 1.5mm", ("白砂糖", "金属检测"))

    assert "白" in tokens
    assert "白砂" in tokens
    assert "白砂糖" in tokens
    assert "ccp2" in tokens
    assert "1.5mm" in tokens


def test_hybrid_index_build_search_and_role_gate(tmp_path: Path) -> None:
    release = tmp_path / "release"
    _write_release(release)
    provider = FakeEmbeddingProvider()

    manifest, corpus = build_indexes(release, provider, built_at=NOW)
    index = HybridIndex(release, provider)
    result = index.search("白砂糖金属检测 CCP2", mode=RetrievalMode.HYBRID)
    super_admin = index.search("白砂糖金属检测 CCP2", role="SUPER_ADMIN")
    forbidden = index.search("白砂糖", role="USER")
    control_chunk = index.chunks["doc-aaaaaaaaaaaaaaaaaaaaaaaa/control/1"]

    assert manifest.lexical.status.value == "SUCCEEDED"
    assert manifest.vector.status.value == "SUCCEEDED"
    assert corpus.lexicalIndexSha256 == manifest.lexical.sha256
    assert control_chunk.controlPointLabels == ("CCP2",)
    assert index._evidence_type_boost("白砂糖金属检测 CCP2", control_chunk) == 0.02
    assert index._evidence_type_boost("白砂糖 SA-990 金属探测器", control_chunk) == 0.02
    assert result.status == RetrievalStatus.SUCCEEDED
    assert super_admin.status == RetrievalStatus.SUCCEEDED
    assert result.hits[0].documentId == "doc-aaaaaaaaaaaaaaaaaaaaaaaa"
    assert result.hits[0].chunkId.endswith("/control/1"), [
        (hit.chunkId, hit.score) for hit in result.hits
    ]
    assert forbidden.status == RetrievalStatus.FORBIDDEN
    assert forbidden.hits == ()


def test_index_hashes_are_repeatable_for_the_same_chunks(tmp_path: Path) -> None:
    first = tmp_path / "first"
    second = tmp_path / "second"
    _write_release(first)
    shutil.copytree(first, second)
    provider = FakeEmbeddingProvider()

    first_manifest, _ = build_indexes(first, provider, built_at=NOW)
    second_manifest, _ = build_indexes(second, provider, built_at=NOW)

    assert first_manifest.lexical.sha256 == second_manifest.lexical.sha256
    assert first_manifest.vector.sha256 == second_manifest.vector.sha256
    assert first_manifest.indexManifestSha256 == second_manifest.indexManifestSha256


def test_search_rejects_a_query_provider_that_differs_from_the_index(tmp_path: Path) -> None:
    release = tmp_path / "release"
    _write_release(release)
    provider = FakeEmbeddingProvider()
    build_indexes(release, provider, built_at=NOW)
    mismatched = FakeEmbeddingProvider()
    mismatched.contract = provider.contract.model_copy(update={"model": "different-model"})

    with pytest.raises(RagBuildError, match="query embedding provider"):
        HybridIndex(release, mismatched)


def test_retrieval_evaluation_covers_admin_and_pre_search_access_gate(tmp_path: Path) -> None:
    release = tmp_path / "release"
    _write_release(release)
    provider = FakeEmbeddingProvider()
    build_indexes(release, provider, built_at=NOW)
    index = HybridIndex(release, provider)
    cases = (
        RetrievalEvaluationCase(
            caseId="eval-test-001",
            category="EXACT_PRODUCT",
            query="白砂糖分装工艺",
            expectedDocumentIds=("doc-aaaaaaaaaaaaaaaaaaaaaaaa",),
        ),
        RetrievalEvaluationCase(
            caseId="eval-test-002",
            category="ACCESS_BOUNDARY",
            query="白砂糖分装工艺",
            role="USER",
            expectedStatus=RetrievalStatus.FORBIDDEN,
        ),
        RetrievalEvaluationCase(
            caseId="eval-test-003",
            category="ACCESS_BOUNDARY",
            query="白砂糖分装工艺",
            role="SUPER_ADMIN",
            expectedDocumentIds=("doc-aaaaaaaaaaaaaaaaaaaaaaaa",),
        ),
    )

    report = evaluate_retrieval(index, cases, generated_at=NOW)

    assert report.status.value == "SUCCEEDED"
    assert report.passedCount == 3
    assert report.failedCount == 0
    assert report.metrics["accessBoundaryAccuracy"] == 1.0
    assert report.evaluationSetSha256 == evaluation_set_sha256(cases)
