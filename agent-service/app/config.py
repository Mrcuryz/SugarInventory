from __future__ import annotations

import os
from dataclasses import dataclass
from typing import Literal


ToolMode = Literal["java_gateway", "mock"]
ModelMode = Literal["basic", "openai_compatible"]


@dataclass(frozen=True)
class Settings:
    tool_mode: ToolMode = "java_gateway"
    model_mode: ModelMode = "basic"
    java_tool_gateway_base_url: str = "http://localhost:8080"
    agent_internal_tool_service_key: str = ""
    python_service_key: str = ""
    request_timeout_ms: int = 15000
    model_base_url: str = ""
    model_api_key: str = ""
    model_name: str = ""
    model_timeout_ms: int = 30000
    service_name: str = "warehouse-agent-service"
    version: str = "0.1.0"

    @staticmethod
    def from_env() -> "Settings":
        raw_mode = os.getenv("AGENT_TOOL_MODE", "java_gateway").strip().lower()
        if raw_mode not in {"java_gateway", "mock"}:
            raw_mode = "java_gateway"
        raw_model_mode = os.getenv("AGENT_MODEL_MODE", "basic").strip().lower()
        if raw_model_mode in {"openai", "openai-compatible", "openai_compatible"}:
            raw_model_mode = "openai_compatible"
        if raw_model_mode not in {"basic", "openai_compatible"}:
            raw_model_mode = "basic"
        return Settings(
            tool_mode=raw_mode,  # type: ignore[arg-type]
            model_mode=raw_model_mode,  # type: ignore[arg-type]
            java_tool_gateway_base_url=os.getenv(
                "JAVA_TOOL_GATEWAY_BASE_URL", "http://localhost:8080"
            ).rstrip("/"),
            agent_internal_tool_service_key=os.getenv("AGENT_INTERNAL_TOOL_SERVICE_KEY", ""),
            python_service_key=os.getenv("AGENT_PYTHON_SERVICE_KEY", ""),
            request_timeout_ms=_int_env("REQUEST_TIMEOUT_MS", 15000),
            model_base_url=os.getenv("AGENT_MODEL_BASE_URL", "").rstrip("/"),
            model_api_key=os.getenv("AGENT_MODEL_API_KEY", ""),
            model_name=os.getenv("AGENT_MODEL_NAME", ""),
            model_timeout_ms=_int_env("AGENT_MODEL_TIMEOUT_MS", 30000),
        )


def _int_env(name: str, fallback: int) -> int:
    value = os.getenv(name)
    if value is None:
        return fallback
    try:
        return max(100, int(value))
    except ValueError:
        return fallback
