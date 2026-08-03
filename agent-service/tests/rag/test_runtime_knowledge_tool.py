from __future__ import annotations

from pathlib import Path
import time

import pytest

from app.observability import MetricsRegistry
from app.rag.contracts import RetrievalMode, RetrievalResult, RetrievalStatus
from app.rag.runtime.contracts import (
    RagReadiness,
    RagReadinessState,
    TrustedKnowledgeContext,
)
from app.rag.runtime.corpus_loader import RagRuntimeConfiguration
from app.rag.runtime.errors import RagRuntimeError
from app.rag.runtime.hybrid_retriever import HybridIndex
from app.rag.runtime.knowledge_tool import (
    ApprovedKnowledgeService,
    build_approved_knowledge_service,
)
from tests.rag.runtime_fixture import build_runtime_root
from tests.rag.test_index_builder import FakeEmbeddingProvider


class FailingQueryProvider(FakeEmbeddingProvider):
    def embed_queries(self, texts: list[str]):
        raise RagRuntimeError("RAG_QUERY_EMBEDDING_FAILED", "synthetic query failure")


class SlowRetriever:
    corpus_version = "test-corpus-v1"
    allowed_roles = ("ADMIN", "SUPER_ADMIN")
    known_product_families = ("白砂糖",)
    chunks = {}

    def search(self, query: str, **_: object) -> RetrievalResult:
        time.sleep(0.1)
        return RetrievalResult(
            status=RetrievalStatus.NO_DATA,
            mode=RetrievalMode.HYBRID,
            query=query,
            elapsedMilliseconds=100,
        )

    def has_reliable_lexical_evidence(self, *args: object) -> bool:
        return False


def ready_service(
    index,
    *,
    timeout_ms: int = 1000,
    max_evidence: int = 5,
) -> tuple[ApprovedKnowledgeService, MetricsRegistry]:
    metrics = MetricsRegistry()
    service = ApprovedKnowledgeService(
        readiness=RagReadiness(
            enabled=True,
            required=False,
            state=RagReadinessState.READY,
            corpusVersion="test-corpus-v1",
        ),
        retriever=index,
        metrics=metrics,
        timeout_ms=timeout_ms,
        max_evidence=max_evidence,
    )
    return service, metrics


def test_internal_knowledge_search_returns_only_safe_evidence(tmp_path: Path) -> None:
    release, provider, _ = build_runtime_root(tmp_path)
    index = HybridIndex(release, provider)
    service, metrics = ready_service(index)
    try:
        admin = service.search_approved_knowledge(
            {
                "query": "白砂糖金属检测 CCP2",
                "knowledgeDomains": ["PROCESS"],
                "productQueries": ["白砂糖"],
                "limit": 5,
            },
            TrustedKnowledgeContext(roleCode="ADMIN", userId=1),
        )
        super_admin = service.search_approved_knowledge(
            {"query": "红糖分装", "productQueries": ["红糖"]},
            TrustedKnowledgeContext(roleCode="SUPER_ADMIN", userId=2),
        )
    finally:
        service.close()

    serialized = admin.model_dump_json()
    assert admin.status == RetrievalStatus.SUCCEEDED
    assert admin.evidence
    assert super_admin.status == RetrievalStatus.SUCCEEDED
    assert "chunkId" not in serialized
    assert "documentId" not in serialized
    assert "score" not in serialized
    assert "embedding" not in serialized.lower()
    assert str(release) not in serialized
    assert all(item.evidenceId.startswith("ev_") for item in admin.evidence)
    snapshot = metrics.snapshot()
    assert any(key.startswith("knowledge_search_total") for key in snapshot["counters"])
    assert any(key.startswith("knowledge_search_duration") for key in snapshot["histograms"])


def test_role_gate_runs_before_payload_validation_or_index_query(tmp_path: Path) -> None:
    release, provider, _ = build_runtime_root(tmp_path)
    index = HybridIndex(release, provider)
    original_search = index.search
    calls = 0

    def counted_search(*args, **kwargs):
        nonlocal calls
        calls += 1
        return original_search(*args, **kwargs)

    index.search = counted_search  # type: ignore[method-assign]
    service, _ = ready_service(index)
    try:
        response = service.search_approved_knowledge(
            {"path": "D:/secret", "roleCode": "ADMIN"},
            TrustedKnowledgeContext(roleCode="STAFF", userId=3),
        )
    finally:
        service.close()

    assert response.status == RetrievalStatus.FORBIDDEN
    assert response.corpusVersion is None
    assert response.evidence == ()
    assert calls == 0


def test_invalid_and_unknown_product_queries_do_not_search(tmp_path: Path) -> None:
    release, provider, _ = build_runtime_root(tmp_path)
    index = HybridIndex(release, provider)
    service, _ = ready_service(index)
    try:
        injected = service.search_approved_knowledge(
            {"query": "白砂糖", "roleCode": "ADMIN"},
            TrustedKnowledgeContext(roleCode="ADMIN", userId=1),
        )
        unknown = service.search_approved_knowledge(
            {"query": "白砂糖", "productQueries": ["未知产品"]},
            TrustedKnowledgeContext(roleCode="ADMIN", userId=1),
        )
    finally:
        service.close()

    assert injected.status == RetrievalStatus.INVALID_QUERY
    assert unknown.status == RetrievalStatus.INVALID_QUERY
    assert unknown.evidence == ()


def test_embedding_failure_is_explicitly_degraded_when_lexical_evidence_is_reliable(
    tmp_path: Path,
) -> None:
    release, _, _ = build_runtime_root(tmp_path)
    index = HybridIndex(release, FailingQueryProvider())
    service, _ = ready_service(index)
    try:
        response = service.search_approved_knowledge(
            {
                "query": "白砂糖金属检测 CCP2",
                "knowledgeDomains": ["PROCESS"],
                "productQueries": ["白砂糖"],
            },
            TrustedKnowledgeContext(roleCode="ADMIN", userId=1),
        )
    finally:
        service.close()

    assert response.status == RetrievalStatus.DEGRADED
    assert response.evidence
    assert "RAG_QUERY_EMBEDDING_DEGRADED" in response.warnings


def test_search_timeout_discards_evidence_and_returns_unavailable() -> None:
    service, _ = ready_service(SlowRetriever(), timeout_ms=20)
    try:
        started = time.perf_counter()
        response = service.search_approved_knowledge(
            {"query": "白砂糖"},
            TrustedKnowledgeContext(roleCode="ADMIN", userId=1),
        )
        elapsed = time.perf_counter() - started
        time.sleep(0.11)
    finally:
        service.close()

    assert elapsed < 0.08
    assert response.status == RetrievalStatus.UNAVAILABLE
    assert response.evidence == ()
    assert "RAG_QUERY_TIMEOUT_OR_BUSY" in response.warnings


def test_disabled_and_optional_invalid_runtime_do_not_break_existing_agent(tmp_path: Path) -> None:
    metrics = MetricsRegistry()
    disabled = build_approved_knowledge_service(RagRuntimeConfiguration(), metrics)
    optional = build_approved_knowledge_service(
        RagRuntimeConfiguration(
            enabled=True,
            root=str(tmp_path),
            model_path=str(tmp_path / "model"),
        ),
        metrics,
    )
    try:
        assert disabled.readiness.state == RagReadinessState.DISABLED
        assert optional.readiness.state == RagReadinessState.UNAVAILABLE
    finally:
        disabled.close()
        optional.close()


def test_required_invalid_runtime_fails_closed(tmp_path: Path) -> None:
    with pytest.raises(RagRuntimeError):
        build_approved_knowledge_service(
            RagRuntimeConfiguration(
                enabled=True,
                required=True,
                root=str(tmp_path),
                model_path=str(tmp_path / "model"),
            ),
            MetricsRegistry(),
        )
