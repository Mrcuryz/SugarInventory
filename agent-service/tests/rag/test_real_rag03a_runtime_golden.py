from __future__ import annotations

from datetime import datetime
import hashlib
import json
import math
import os
from pathlib import Path
import shutil
import time
from zoneinfo import ZoneInfo

import pytest

from app.observability import MetricsRegistry
from app.rag.contracts import IndexManifest, RetrievalEvaluationReport, RetrievalStatus
from app.rag.runtime.contracts import CurrentCorpusPointer, TrustedKnowledgeContext
from app.rag.runtime.corpus_loader import RagRuntimeConfiguration
from app.rag.runtime.knowledge_tool import build_approved_knowledge_service


SERVICE_ROOT = Path(__file__).resolve().parents[2]
LOCAL_GOLDEN_PATHS = {
    "LAIBIN_RAG_INDEXED_RELEASE": SERVICE_ROOT
    / "build"
    / "rag-r02-release-2026-08-01"
    / "releases"
    / "laibin-rag-2026-07-29-v1",
    "LAIBIN_RAG_MODEL_PATH": SERVICE_ROOT
    / "build"
    / "rag-models"
    / "fast-bge-small-zh-v1.5",
}


def _configured_path(name: str) -> Path:
    value = os.getenv(name)
    path = Path(value) if value else LOCAL_GOLDEN_PATHS[name]
    if not path.exists() and not value:
        pytest.skip(f"{name} is not configured")
    if not path.exists():
        pytest.fail(f"{name} does not exist")
    return path


def _sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for block in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def test_real_rag03a_loader_safe_search_and_performance(
    tmp_path: Path, record_property
) -> None:
    source_release = _configured_path("LAIBIN_RAG_INDEXED_RELEASE")
    model_path = _configured_path("LAIBIN_RAG_MODEL_PATH")
    corpus = json.loads((source_release / "corpus-manifest.json").read_text(encoding="utf-8"))
    corpus_version = corpus["corpusVersion"]
    runtime_root = tmp_path / "runtime"
    release = runtime_root / "releases" / corpus_version
    shutil.copytree(source_release, release)
    index_manifest = IndexManifest.model_validate_json(
        (release / "indexes" / "index-manifest.json").read_text(encoding="utf-8")
    )
    evaluation = RetrievalEvaluationReport.model_validate_json(
        (release / "qa" / "retrieval-evaluation-report.json").read_text(encoding="utf-8")
    )
    pointer = CurrentCorpusPointer(
        corpusVersion=corpus_version,
        releaseName=corpus_version,
        corpusManifestSha256=_sha256_file(release / "corpus-manifest.json"),
        indexManifestSha256=index_manifest.indexManifestSha256 or "",
        evaluationSetSha256=evaluation.evaluationSetSha256,
        evaluationReportSha256=_sha256_file(
            release / "qa" / "retrieval-evaluation-report.json"
        ),
        publishedAt=datetime.now(ZoneInfo("Asia/Shanghai")),
        publishedBy="rag03a-golden",
    )
    (runtime_root / "current.json").write_text(
        pointer.model_dump_json(indent=2) + "\n", encoding="utf-8", newline="\n"
    )
    service = build_approved_knowledge_service(
        RagRuntimeConfiguration(
            enabled=True,
            required=True,
            root=str(runtime_root),
            model_path=str(model_path),
            query_timeout_ms=3000,
            max_evidence=5,
        ),
        MetricsRegistry(),
    )
    queries = (
        {
            "query": "多晶体白冰糖自然结晶需要多久",
            "knowledgeDomains": ["PROCESS"],
            "productQueries": ["多晶体白冰糖"],
        },
        {
            "query": "白砂糖金属检测的控制参数",
            "knowledgeDomains": ["PROCESS"],
            "productQueries": ["白砂糖"],
        },
        {"query": "公司是哪一年成立的", "knowledgeDomains": ["COMPANY"]},
        {
            "query": "玫瑰花黑糖是什么产品",
            "knowledgeDomains": ["PRODUCT_MARKETING"],
            "productQueries": ["玫瑰花黑糖"],
        },
        {
            "query": "单晶体冰糖有哪些工艺步骤",
            "knowledgeDomains": ["PROCESS"],
            "productQueries": ["单晶体冰糖"],
        },
    )
    latencies: list[float] = []
    try:
        responses = []
        for index in range(20):
            started = time.perf_counter()
            response = service.search_approved_knowledge(
                queries[index % len(queries)],
                TrustedKnowledgeContext(
                    roleCode="SUPER_ADMIN" if index % 2 else "ADMIN",
                    userId=1,
                ),
            )
            latencies.append((time.perf_counter() - started) * 1000)
            responses.append(response)
        forbidden = service.search_approved_knowledge(
            queries[0], TrustedKnowledgeContext(roleCode="STAFF", userId=2)
        )
        no_data = service.search_approved_knowledge(
            {
                "query": "白砂糖的企业认证资料是什么",
                "knowledgeDomains": [
                    "COMPANY",
                    "PRODUCT_MARKETING",
                    "CERTIFICATION",
                    "SALES",
                ],
                "productQueries": ["白砂糖"],
            },
            TrustedKnowledgeContext(roleCode="ADMIN", userId=1),
        )
    finally:
        service.close()

    assert service.readiness.state.value == "READY"
    assert all(response.status == RetrievalStatus.SUCCEEDED for response in responses), [
        (response.queryLabel, response.status.value, response.warnings)
        for response in responses
        if response.status != RetrievalStatus.SUCCEEDED
    ]
    assert all(response.evidence for response in responses)
    assert forbidden.status == RetrievalStatus.FORBIDDEN
    assert forbidden.evidence == ()
    assert no_data.status == RetrievalStatus.NO_DATA
    assert no_data.evidence == ()
    serialized = "".join(response.model_dump_json() for response in responses)
    assert all(
        forbidden_field not in serialized
        for forbidden_field in ("chunkId", "documentId", "score", "embedding", str(release))
    )
    p95 = sorted(latencies)[math.ceil(len(latencies) * 0.95) - 1]
    record_property("rag03aSearchP95Milliseconds", round(p95, 3))
    assert p95 <= 3000.0
