from __future__ import annotations

import json
import os
from pathlib import Path
import stat

import pytest

from app.observability import MetricsRegistry
from app.rag.contracts import RetrievalStatus
from app.rag.offline.publisher import publish_release, validate_release
from app.rag.offline.source_inventory import RagBuildError
from app.rag.runtime.contracts import TrustedKnowledgeContext
from app.rag.runtime.corpus_loader import RagRuntimeConfiguration
from app.rag.runtime.knowledge_tool import build_approved_knowledge_service


SERVICE_ROOT = Path(__file__).resolve().parents[2]
PROJECT_ROOT = SERVICE_ROOT.parent
DEFAULT_ACTIVE_ROOT = PROJECT_ROOT / "deploy" / "simple" / "artifacts" / "rag"
DEFAULT_MODEL_PATH = (
    SERVICE_ROOT / "build" / "rag-models" / "fast-bge-small-zh-v1.5"
)


def _configured_path(name: str, fallback: Path) -> Path:
    configured = os.getenv(name)
    path = Path(configured) if configured else fallback
    if not path.exists() and not configured:
        pytest.skip(f"{name} is not configured")
    if not path.exists():
        pytest.fail(f"{name} does not exist")
    return path


def _make_writable(root: Path) -> None:
    if not root.exists():
        return
    for path in root.rglob("*"):
        try:
            path.chmod(0o700 if path.is_dir() else 0o600)
        except OSError:
            pass


def test_real_rag05_active_release_is_audited_read_only_and_searchable() -> None:
    active_root = _configured_path("LAIBIN_RAG_ACTIVE_ROOT", DEFAULT_ACTIVE_ROOT)
    model_path = _configured_path("LAIBIN_RAG_MODEL_PATH", DEFAULT_MODEL_PATH)
    pointer = json.loads((active_root / "current.json").read_text(encoding="utf-8"))
    release = active_root / "releases" / pointer["releaseName"]
    validated = validate_release(release)
    audit_events = tuple(sorted((active_root / "audit").glob("*.json")))

    assert pointer["corpusVersion"] == "laibin-rag-2026-07-29-v1"
    assert pointer["publishedBy"]
    assert validated.documentCount == 10
    assert validated.chunkCount == 196
    assert validated.evaluationCaseCount == 83
    assert validated.releaseSha256 == (
        "4a7525328af286d8ca7702ddc52c3f857244745bf0db080a84d426c6392456f6"
    )
    assert audit_events
    audit = json.loads(audit_events[-1].read_text(encoding="utf-8"))
    assert audit["status"] == "COMMITTED"
    assert audit["action"] == "PUBLISH"
    assert audit["toCorpusVersion"] == pointer["corpusVersion"]
    assert audit["corpusManifestSha256"] == pointer["corpusManifestSha256"]
    assert not tuple(active_root.glob(".current.*.tmp"))
    assert not tuple((active_root / "audit").glob("*.prepared"))
    assert not (active_root / ".publish.lock").exists()
    assert (release / "corpus-manifest.json").stat().st_mode & stat.S_IWUSR == 0

    service = build_approved_knowledge_service(
        RagRuntimeConfiguration(
            enabled=True,
            required=True,
            root=str(active_root),
            model_path=str(model_path),
            query_timeout_ms=5000,
            max_evidence=5,
        ),
        MetricsRegistry(),
    )
    try:
        response = service.search_approved_knowledge(
            {
                "query": "白砂糖生产工艺中金属检测 CCP2 的控制参数是什么",
                "knowledgeDomains": ["PROCESS"],
                "productQueries": ["白砂糖"],
            },
            TrustedKnowledgeContext(roleCode="ADMIN", userId=1),
        )
        forbidden = service.search_approved_knowledge(
            {"query": "白砂糖生产工艺", "knowledgeDomains": ["PROCESS"]},
            TrustedKnowledgeContext(roleCode="STAFF", userId=2),
        )
    finally:
        service.close()

    assert service.readiness.state.value == "READY"
    assert service.readiness.corpusVersion == pointer["corpusVersion"]
    assert response.status == RetrievalStatus.SUCCEEDED
    assert response.corpusVersion == pointer["corpusVersion"]
    assert response.evidence
    assert forbidden.status == RetrievalStatus.FORBIDDEN
    assert forbidden.evidence == ()
    serialized = response.model_dump_json()
    assert str(active_root) not in serialized
    assert all(field not in serialized for field in ("chunkId", "documentId", "embedding"))


def test_real_rag05_interruption_before_pointer_replace_is_resumable(
    tmp_path: Path,
) -> None:
    active_root = _configured_path("LAIBIN_RAG_ACTIVE_ROOT", DEFAULT_ACTIVE_ROOT)
    pointer = json.loads((active_root / "current.json").read_text(encoding="utf-8"))
    source_release = active_root / "releases" / pointer["releaseName"]
    runtime_root = tmp_path / "interrupted-runtime"

    try:
        with pytest.raises(RagBuildError) as error:
            publish_release(
                source_release,
                runtime_root,
                published_by="rag05-interruption-test",
                before_pointer_replace=lambda: (_ for _ in ()).throw(
                    RuntimeError("simulated interruption")
                ),
            )

        assert error.value.code == "PUBLISH_POINTER_SWITCH_FAILED"
        assert not (runtime_root / "current.json").exists()
        assert (
            runtime_root / "releases" / "laibin-rag-2026-07-29-v1"
        ).is_dir()
        assert not tuple(runtime_root.glob(".current.*.tmp"))
        assert not tuple((runtime_root / "audit").glob("*.prepared"))

        resumed = publish_release(
            source_release,
            runtime_root,
            published_by="rag05-interruption-test",
        )
        resumed_pointer = json.loads(
            (runtime_root / "current.json").read_text(encoding="utf-8")
        )
        assert resumed.releaseCopied is False
        assert resumed.pointerChanged is True
        assert resumed_pointer["corpusVersion"] == "laibin-rag-2026-07-29-v1"
    finally:
        _make_writable(runtime_root)
