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


def test_production_compose_keeps_model_driven_controlled_planning_enabled() -> None:
    compose = (REPOSITORY / "deploy/simple/docker-compose.yml").read_text(
        encoding="utf-8"
    )
    env_example = (REPOSITORY / "deploy/simple/.env.example").read_text(
        encoding="utf-8"
    )

    assert "AGENT_PYTHON_MODEL_MODE: ${AGENT_PYTHON_MODEL_MODE:-openai_compatible}" in compose
    assert "AGENT_PLANNING_MODE: ${AGENT_PLANNING_MODE:-llm}" in compose
    assert "AGENT_PYTHON_MODEL_MODE=openai_compatible" in env_example
    assert "AGENT_PLANNING_MODE=llm" in env_example
    assert "master_data_expert" in env_example
    assert "AGENT_MODEL_API_KEY=" in env_example


def test_agent_image_keeps_a_valid_wheel_filename() -> None:
    dockerfile = (REPOSITORY / "deploy/simple/agent.Dockerfile").read_text(
        encoding="utf-8"
    )

    wheel_name = "warehouse_agent_service-0.2.0-py3-none-any.whl"
    assert f"/tmp/{wheel_name}" in dockerfile
    assert '"/tmp/agent-service.whl[run,rag-runtime]"' not in dockerfile


def test_linux_deployment_scripts_use_lf_line_endings() -> None:
    for name in ("apply-migrations.sh", "init-env.sh", "update.sh"):
        content = (REPOSITORY / "deploy/simple" / name).read_bytes()
        assert b"\r\n" not in content, f"{name} must use LF line endings"


def test_demo_web_login_password_is_memorable_and_not_randomized() -> None:
    env_example = (REPOSITORY / "deploy/simple/.env.example").read_text(
        encoding="utf-8"
    )
    init_script = (REPOSITORY / "deploy/simple/init-env.sh").read_text(
        encoding="utf-8"
    )

    assert "WEB_LOGIN_PASSWORD=lbsp88888888" in env_example.splitlines()
    assert "web_login_password=" not in init_script
    assert "WEB_LOGIN_PASSWORD=GENERATED_BY_INIT" not in env_example
