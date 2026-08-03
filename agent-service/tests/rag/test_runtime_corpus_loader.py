from __future__ import annotations

import json
from pathlib import Path

import pytest

from app.rag.runtime.corpus_loader import RagRuntimeConfiguration, load_current_corpus
from app.rag.runtime.errors import RagRuntimeError
from app.rag.runtime.hybrid_retriever import HybridIndex
from tests.rag.runtime_fixture import build_runtime_root, sha256_file


def configuration(root: Path) -> RagRuntimeConfiguration:
    return RagRuntimeConfiguration(
        enabled=True,
        root=str(root),
        model_path=str(root / "test-model"),
    )


def test_loader_uses_only_current_pointer_and_binds_validated_release(tmp_path: Path) -> None:
    _, provider, pointer = build_runtime_root(tmp_path)

    loaded = load_current_corpus(
        configuration(tmp_path),
        retriever_factory=HybridIndex,
        provider_factory=lambda _: provider,
    )

    assert loaded.pointer == pointer
    assert loaded.retriever.corpus_version == "test-corpus-v1"
    assert loaded.retriever.provider is provider


@pytest.mark.parametrize(
    ("prepare", "expected_code"),
    [
        (lambda root: None, "RAG_ARTIFACT_MISSING"),
        (
            lambda root: (root / "current.json").write_text(
                "{invalid", encoding="utf-8"
            ),
            "RAG_POINTER_INVALID",
        ),
    ],
)
def test_loader_fails_closed_for_missing_or_invalid_pointer(
    tmp_path: Path, prepare, expected_code: str
) -> None:
    prepare(tmp_path)

    with pytest.raises(RagRuntimeError) as error:
        load_current_corpus(configuration(tmp_path))

    assert error.value.code == expected_code


def test_loader_rejects_pointer_hash_mismatch_before_provider_load(tmp_path: Path) -> None:
    _, provider, _ = build_runtime_root(tmp_path)
    payload = json.loads((tmp_path / "current.json").read_text(encoding="utf-8"))
    payload["corpusManifestSha256"] = "0" * 64
    (tmp_path / "current.json").write_text(
        json.dumps(payload, ensure_ascii=False), encoding="utf-8"
    )
    provider_called = False

    def provider_factory(_):
        nonlocal provider_called
        provider_called = True
        return provider

    with pytest.raises(RagRuntimeError) as error:
        load_current_corpus(
            configuration(tmp_path),
            retriever_factory=HybridIndex,
            provider_factory=provider_factory,
        )

    assert error.value.code == "RAG_CORPUS_MANIFEST_HASH_INVALID"
    assert provider_called is False


def test_loader_rejects_failed_evaluation_gate(tmp_path: Path) -> None:
    release, provider, _ = build_runtime_root(tmp_path)
    report_path = release / "qa" / "retrieval-evaluation-report.json"
    payload = json.loads(report_path.read_text(encoding="utf-8"))
    payload["metrics"]["recallAt5"] = 0.5
    report_path.write_text(json.dumps(payload, ensure_ascii=False), encoding="utf-8")
    pointer_path = tmp_path / "current.json"
    pointer = json.loads(pointer_path.read_text(encoding="utf-8"))
    pointer["evaluationReportSha256"] = sha256_file(report_path)
    pointer_path.write_text(json.dumps(pointer, ensure_ascii=False), encoding="utf-8")

    with pytest.raises(RagRuntimeError) as error:
        load_current_corpus(
            configuration(tmp_path),
            retriever_factory=HybridIndex,
            provider_factory=lambda _: provider,
        )

    assert error.value.code == "RAG_EVALUATION_GATE_FAILED"


def test_loader_rejects_evaluation_report_hash_mismatch(tmp_path: Path) -> None:
    release, provider, _ = build_runtime_root(tmp_path)
    report_path = release / "qa" / "retrieval-evaluation-report.json"
    report_path.write_text(report_path.read_text(encoding="utf-8") + "\n", encoding="utf-8")

    with pytest.raises(RagRuntimeError) as error:
        load_current_corpus(
            configuration(tmp_path),
            retriever_factory=HybridIndex,
            provider_factory=lambda _: provider,
        )

    assert error.value.code == "RAG_EVALUATION_REPORT_HASH_INVALID"


def test_loader_rejects_missing_normalized_document(tmp_path: Path) -> None:
    release, provider, _ = build_runtime_root(tmp_path)
    document_path = next((release / "documents").glob("*.document.json"))
    document_path.unlink()

    with pytest.raises(RagRuntimeError) as error:
        load_current_corpus(
            configuration(tmp_path),
            retriever_factory=HybridIndex,
            provider_factory=lambda _: provider,
        )

    assert error.value.code == "RAG_DOCUMENT_COUNT_MISMATCH"


def test_loader_rejects_required_while_disabled() -> None:
    with pytest.raises(RagRuntimeError) as error:
        load_current_corpus(RagRuntimeConfiguration(enabled=False, required=True))

    assert error.value.code == "RAG_REQUIRED_WHILE_DISABLED"
