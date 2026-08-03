from __future__ import annotations

import json
import os
from pathlib import Path
import stat

import pytest

from app.rag.offline.cli import main
from app.rag.offline.publisher import publish_release, rollback_release, validate_release
from app.rag.offline.source_inventory import RagBuildError
from app.rag.runtime.corpus_loader import RagRuntimeConfiguration, load_current_corpus
from app.rag.runtime.hybrid_retriever import HybridIndex
from tests.rag.runtime_fixture import build_runtime_root
from tests.rag.test_index_builder import NOW


@pytest.fixture
def runtime_root(tmp_path: Path):
    root = tmp_path / "runtime"
    yield root
    if root.exists():
        for path in root.rglob("*"):
            try:
                path.chmod(0o700 if path.is_dir() else 0o600)
            except OSError:
                pass


def _candidate(
    tmp_path: Path,
    version: str,
    *,
    label: str | None = None,
) -> tuple[Path, object]:
    release, provider, _ = build_runtime_root(
        tmp_path / f"candidate-{label or version}", version
    )
    return release, provider


def _pointer_payload(root: Path) -> dict[str, object]:
    return json.loads((root / "current.json").read_text(encoding="utf-8"))


def test_validate_and_publish_release_then_runtime_loads_read_only_copy(
    tmp_path: Path, runtime_root: Path
) -> None:
    release, provider = _candidate(tmp_path, "test-corpus-v1")

    validated = validate_release(release, published_at=NOW)
    result = publish_release(
        release,
        runtime_root,
        published_by="pytest",
        published_at=NOW,
    )

    deployed = runtime_root / "releases" / "test-corpus-v1"
    loaded = load_current_corpus(
        RagRuntimeConfiguration(
            enabled=True,
            required=True,
            root=str(runtime_root),
            model_path=str(tmp_path / "model"),
        ),
        retriever_factory=HybridIndex,
        provider_factory=lambda _: provider,
    )
    assert validated.evaluationCaseCount == 60
    assert result.releaseCopied is True
    assert result.pointerChanged is True
    assert result.previousCorpusVersion is None
    assert loaded.pointer.corpusVersion == "test-corpus-v1"
    assert (deployed / "corpus-manifest.json").stat().st_mode & stat.S_IWUSR == 0
    assert (runtime_root / "current.json").stat().st_mode & stat.S_IROTH
    assert result.auditEvent is not None
    assert (runtime_root / result.auditEvent).is_file()


def test_failed_evaluation_never_copies_or_switches(
    tmp_path: Path, runtime_root: Path
) -> None:
    first, _ = _candidate(tmp_path, "test-corpus-v1")
    publish_release(first, runtime_root, published_by="pytest", published_at=NOW)
    before = (runtime_root / "current.json").read_bytes()
    second, _ = _candidate(tmp_path, "test-corpus-v2")
    report_path = second / "qa" / "retrieval-evaluation-report.json"
    report = json.loads(report_path.read_text(encoding="utf-8"))
    report["metrics"]["recallAt5"] = 0.1
    report_path.write_text(json.dumps(report, ensure_ascii=False), encoding="utf-8")

    with pytest.raises(RagBuildError) as error:
        publish_release(
            second,
            runtime_root,
            published_by="pytest",
            expected_current_version="test-corpus-v1",
            published_at=NOW,
        )

    assert error.value.code == "RAG_EVALUATION_GATE_FAILED"
    assert (runtime_root / "current.json").read_bytes() == before
    assert not (runtime_root / "releases" / "test-corpus-v2").exists()


def test_existing_version_is_never_overwritten(tmp_path: Path, runtime_root: Path) -> None:
    first, _ = _candidate(tmp_path, "test-corpus-v1")
    publish_release(first, runtime_root, published_by="pytest", published_at=NOW)
    before = (runtime_root / "current.json").read_bytes()
    conflicting, _ = _candidate(tmp_path, "test-corpus-v1", label="conflict")
    (conflicting / "qa" / "extra.json").write_text("{}\n", encoding="utf-8")

    with pytest.raises(RagBuildError) as error:
        publish_release(
            conflicting,
            runtime_root,
            published_by="pytest",
            expected_current_version="test-corpus-v1",
            published_at=NOW,
        )

    assert error.value.code == "PUBLISH_RELEASE_ALREADY_EXISTS"
    assert (runtime_root / "current.json").read_bytes() == before


def test_interrupted_pointer_switch_preserves_current_and_can_resume(
    tmp_path: Path, runtime_root: Path
) -> None:
    first, _ = _candidate(tmp_path, "test-corpus-v1")
    second, _ = _candidate(tmp_path, "test-corpus-v2")
    publish_release(first, runtime_root, published_by="pytest", published_at=NOW)
    before = (runtime_root / "current.json").read_bytes()

    with pytest.raises(RagBuildError) as error:
        publish_release(
            second,
            runtime_root,
            published_by="pytest",
            expected_current_version="test-corpus-v1",
            published_at=NOW,
            before_pointer_replace=lambda: (_ for _ in ()).throw(RuntimeError("interrupt")),
        )

    assert error.value.code == "PUBLISH_POINTER_SWITCH_FAILED"
    assert (runtime_root / "current.json").read_bytes() == before
    assert (runtime_root / "releases" / "test-corpus-v2").is_dir()

    resumed = publish_release(
        second,
        runtime_root,
        published_by="pytest",
        expected_current_version="test-corpus-v1",
        published_at=NOW,
    )
    assert resumed.releaseCopied is False
    assert _pointer_payload(runtime_root)["corpusVersion"] == "test-corpus-v2"


def test_cross_version_rollback_is_atomic_and_preserves_both_releases(
    tmp_path: Path, runtime_root: Path
) -> None:
    first, _ = _candidate(tmp_path, "test-corpus-v1")
    second, _ = _candidate(tmp_path, "test-corpus-v2")
    publish_release(first, runtime_root, published_by="pytest", published_at=NOW)
    publish_release(
        second,
        runtime_root,
        published_by="pytest",
        expected_current_version="test-corpus-v1",
        published_at=NOW,
    )

    result = rollback_release(
        runtime_root,
        target_version="test-corpus-v1",
        expected_current_version="test-corpus-v2",
        published_by="pytest",
        published_at=NOW,
    )

    assert result.action == "ROLLBACK"
    assert result.previousCorpusVersion == "test-corpus-v2"
    assert _pointer_payload(runtime_root)["corpusVersion"] == "test-corpus-v1"
    assert (runtime_root / "releases" / "test-corpus-v1").is_dir()
    assert (runtime_root / "releases" / "test-corpus-v2").is_dir()


def test_idempotent_publish_does_not_rewrite_pointer_or_audit(
    tmp_path: Path, runtime_root: Path
) -> None:
    first, _ = _candidate(tmp_path, "test-corpus-v1")
    initial = publish_release(first, runtime_root, published_by="pytest", published_at=NOW)
    before = (runtime_root / "current.json").read_bytes()
    audit_before = tuple((runtime_root / "audit").glob("*.json"))

    repeated = publish_release(
        first,
        runtime_root,
        published_by="pytest-second-call",
        published_at=NOW,
    )

    assert initial.pointerChanged is True
    assert repeated.pointerChanged is False
    assert repeated.auditEvent is None
    assert (runtime_root / "current.json").read_bytes() == before
    assert tuple((runtime_root / "audit").glob("*.json")) == audit_before


def test_audit_commit_failure_reports_pointer_already_switched_and_keeps_prepared_event(
    tmp_path: Path,
    runtime_root: Path,
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    first, _ = _candidate(tmp_path, "test-corpus-v1")
    original_replace = os.replace

    def replace_with_audit_failure(source, target):
        if str(source).endswith(".prepared"):
            raise OSError("audit rename failed")
        return original_replace(source, target)

    monkeypatch.setattr(os, "replace", replace_with_audit_failure)

    with pytest.raises(RagBuildError) as error:
        publish_release(first, runtime_root, published_by="pytest", published_at=NOW)

    assert error.value.code == "PUBLISH_AUDIT_COMMIT_FAILED"
    assert _pointer_payload(runtime_root)["corpusVersion"] == "test-corpus-v1"
    assert len(tuple((runtime_root / "audit").glob("*.prepared"))) == 1


def test_expected_current_and_lock_fail_closed(tmp_path: Path, runtime_root: Path) -> None:
    first, _ = _candidate(tmp_path, "test-corpus-v1")
    publish_release(first, runtime_root, published_by="pytest", published_at=NOW)
    second, _ = _candidate(tmp_path, "test-corpus-v2")

    with pytest.raises(RagBuildError) as mismatch:
        publish_release(second, runtime_root, published_by="pytest", published_at=NOW)
    assert mismatch.value.code == "PUBLISH_EXPECTED_CURRENT_REQUIRED"

    (runtime_root / ".publish.lock").write_text("held", encoding="utf-8")
    with pytest.raises(RagBuildError) as locked:
        publish_release(
            second,
            runtime_root,
            published_by="pytest",
            expected_current_version="test-corpus-v1",
            published_at=NOW,
        )
    assert locked.value.code == "PUBLISH_LOCKED"


def test_rollback_rejects_target_path_traversal(
    tmp_path: Path, runtime_root: Path
) -> None:
    first, _ = _candidate(tmp_path, "test-corpus-v1")
    publish_release(first, runtime_root, published_by="pytest", published_at=NOW)

    with pytest.raises(RagBuildError) as error:
        rollback_release(
            runtime_root,
            target_version="../candidate-test-corpus-v1",
            expected_current_version="test-corpus-v1",
            published_by="pytest",
            published_at=NOW,
        )

    assert error.value.code == "ROLLBACK_TARGET_INVALID"


def test_validate_release_cli_outputs_safe_summary(tmp_path: Path, capsys) -> None:
    release, _ = _candidate(tmp_path, "test-corpus-v1")

    code = main(["validate-release", "--release", str(release)])
    output = capsys.readouterr().out
    payload = json.loads(output)

    assert code == 0
    assert payload["status"] == "SUCCEEDED"
    assert payload["corpusVersion"] == "test-corpus-v1"
    assert str(tmp_path) not in output
