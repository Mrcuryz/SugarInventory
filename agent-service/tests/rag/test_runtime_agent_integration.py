from __future__ import annotations

from pathlib import Path
import subprocess
import sys

import pytest
from fastapi.testclient import TestClient

from app.config import Settings
from app.graph.state import InMemoryCheckpointer
from app.main import create_app
from app.rag.runtime.errors import RagRuntimeError
from app.tools.client import MockToolClient


def test_settings_load_bounded_rag_configuration(monkeypatch: pytest.MonkeyPatch) -> None:
    monkeypatch.setenv("AGENT_RAG_ENABLED", "true")
    monkeypatch.setenv("AGENT_RAG_REQUIRED", "true")
    monkeypatch.setenv("AGENT_RAG_ROOT", " /app/rag ")
    monkeypatch.setenv("AGENT_RAG_MODEL_PATH", " /app/model ")
    monkeypatch.setenv("AGENT_RAG_MODEL_THREADS", "99")
    monkeypatch.setenv("AGENT_RAG_QUERY_TIMEOUT_MS", "1")
    monkeypatch.setenv("AGENT_RAG_MAX_EVIDENCE", "99")

    settings = Settings.from_env()

    assert settings.rag_enabled is True
    assert settings.rag_required is True
    assert settings.rag_root == "/app/rag"
    assert settings.rag_model_path == "/app/model"
    assert settings.rag_model_threads == 16
    assert settings.rag_query_timeout_ms == 100
    assert settings.rag_max_evidence == 10


def test_default_app_reports_rag_disabled_without_changing_health() -> None:
    app = create_app(
        Settings(tool_mode="mock"),
        tool_client=MockToolClient(),
        checkpointer=InMemoryCheckpointer(),
    )
    with TestClient(app) as client:
        health = client.get("/internal/agent/health")
        capabilities = client.get("/internal/agent/capabilities")

    assert health.status_code == 200
    assert health.json()["status"] == "UP"
    assert health.json()["dependencies"]["rag"] == "DISABLED"
    assert capabilities.json()["rag"] == {
        "enabled": False,
        "required": False,
        "state": "DISABLED",
        "corpusVersion": None,
    }


def test_app_rejects_required_rag_when_disabled() -> None:
    with pytest.raises(RagRuntimeError) as error:
        create_app(
            Settings(tool_mode="mock", rag_required=True),
            tool_client=MockToolClient(),
            checkpointer=InMemoryCheckpointer(),
        )

    assert error.value.code == "RAG_REQUIRED_WHILE_DISABLED"


def test_optional_invalid_rag_is_unavailable_without_taking_agent_down(tmp_path: Path) -> None:
    app = create_app(
        Settings(
            tool_mode="mock",
            rag_enabled=True,
            rag_root=str(tmp_path),
            rag_model_path=str(tmp_path / "model"),
        ),
        tool_client=MockToolClient(),
        checkpointer=InMemoryCheckpointer(),
    )
    with TestClient(app) as client:
        response = client.get("/internal/agent/health")

    assert response.status_code == 200
    assert response.json()["status"] == "UP"
    assert response.json()["dependencies"]["rag"] == "UNAVAILABLE"


def test_importing_disabled_agent_does_not_import_rag_runtime_dependencies() -> None:
    service_root = Path(__file__).resolve().parents[2]
    result = subprocess.run(
        [
            sys.executable,
            "-c",
            (
                "import sys; import app.main; "
                "assert 'numpy' not in sys.modules; "
                "assert 'fastembed' not in sys.modules"
            ),
        ],
        cwd=service_root,
        capture_output=True,
        text=True,
        timeout=30,
        check=False,
    )

    assert result.returncode == 0, result.stderr
