from __future__ import annotations

from pathlib import Path

from app.rag.contracts import KnowledgeDomain, RetrievalMode, RetrievalStatus
from app.rag.runtime.errors import RagRuntimeError
from app.rag.runtime.hybrid_retriever import HybridIndex
from tests.rag.runtime_fixture import build_runtime_root
from tests.rag.test_index_builder import FakeEmbeddingProvider


class FailingQueryProvider(FakeEmbeddingProvider):
    def embed_queries(self, texts: list[str]):
        raise RagRuntimeError("RAG_QUERY_EMBEDDING_FAILED", "synthetic query failure")


def test_runtime_hybrid_index_loads_and_filters_the_offline_index(tmp_path: Path) -> None:
    release, provider, _ = build_runtime_root(tmp_path)
    index = HybridIndex(release, provider)

    admin = index.search(
        "白砂糖金属检测 CCP2",
        role="ADMIN",
        knowledge_domains=(KnowledgeDomain.PROCESS,),
        product_families=("白砂糖",),
    )
    super_admin = index.search("红糖分装", role="SUPER_ADMIN")
    forbidden = index.search("白砂糖", role="STAFF")
    excluded = index.search("白砂糖", product_families=("不存在产品",))

    assert admin.status == RetrievalStatus.SUCCEEDED
    assert admin.hits[0].documentId == "doc-aaaaaaaaaaaaaaaaaaaaaaaa"
    assert super_admin.status == RetrievalStatus.SUCCEEDED
    assert forbidden.status == RetrievalStatus.FORBIDDEN
    assert forbidden.hits == ()
    assert excluded.status == RetrievalStatus.NO_DATA


def test_runtime_hybrid_index_distinguishes_embedding_degradation(tmp_path: Path) -> None:
    release, _, _ = build_runtime_root(tmp_path)
    failing = FailingQueryProvider()
    index = HybridIndex(release, failing)

    hybrid = index.search(
        "白砂糖金属检测 CCP2",
        mode=RetrievalMode.HYBRID,
        product_families=("白砂糖",),
    )
    vector = index.search("白砂糖", mode=RetrievalMode.VECTOR)

    assert hybrid.status == RetrievalStatus.DEGRADED
    assert hybrid.hits
    assert hybrid.reason == "RAG_QUERY_EMBEDDING_FAILED"
    assert vector.status == RetrievalStatus.UNAVAILABLE
    assert vector.hits == ()
