from __future__ import annotations

from pathlib import Path


REPOSITORY = Path(__file__).resolve().parents[3]


def test_packaging_cleanup_preserves_rag_recovery_sources() -> None:
    script = (REPOSITORY / "scripts/prepare-simple-deployment.ps1").read_text(
        encoding="utf-8"
    )

    assert "'agent-service\\build'," not in script
    assert "agent-service\\build\\lib" in script
    assert "agent-service\\build\\bdist.win-amd64" in script
    assert "build/rag and build/rag-models" in script


def test_production_update_fails_closed_when_enabled_rag_is_incomplete() -> None:
    script = (REPOSITORY / "deploy/simple/update.sh").read_text(encoding="utf-8")

    assert 'rag_enabled="$(sed -n' in script
    assert 'rag_required" = "true"' in script
    assert "artifacts/rag/current.json" in script
    assert "artifacts/rag-model/model_optimized.onnx" in script
    assert "python -m app.rag.offline.cli validate-runtime" in script
    assert "--runtime-root /app/rag" in script
    assert "--model-path /app/rag-model" in script


def test_runtime_rag_artifacts_remain_read_only_mounts() -> None:
    compose = (REPOSITORY / "deploy/simple/docker-compose.yml").read_text(
        encoding="utf-8"
    )

    assert "./artifacts/rag:/app/rag:ro" in compose
    assert "./artifacts/rag-model:/app/rag-model:ro" in compose
