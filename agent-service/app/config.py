from __future__ import annotations

import os
from dataclasses import dataclass
from typing import Literal


ToolMode = Literal["java_gateway", "mock"]
ModelMode = Literal["basic", "openai_compatible"]
StateBackend = Literal["memory", "redis"]
PlanningMode = Literal["deterministic", "llm"]


@dataclass(frozen=True)
class Settings:
    tool_mode: ToolMode = "java_gateway"
    model_mode: ModelMode = "basic"
    java_tool_gateway_base_url: str = "http://localhost:8080"
    agent_internal_tool_service_key: str = ""
    python_service_key: str = ""
    request_timeout_ms: int = 15000
    run_timeout_ms: int = 20000
    model_base_url: str = ""
    model_api_key: str = ""
    model_name: str = ""
    model_timeout_ms: int = 30000
    goal_draft_shadow_enabled: bool = False
    planning_mode: PlanningMode = "deterministic"
    llm_allowed_experts: tuple[str, ...] = (
        "inventory_expert",
        "warehouse_expert",
        "assay_expert",
        "logistics_expert",
    )
    llm_max_tool_calls: int = 3
    llm_max_tool_retries: int = 1
    allow_insecure_mock_auth: bool = True
    deployment_environment: str = "test"
    state_backend: StateBackend = "memory"
    redis_url: str = "redis://127.0.0.1:6379/0"
    state_ttl_seconds: int = 3600
    session_lock_wait_ms: int = 5000
    session_lock_lease_ms: int = 30000
    service_name: str = "warehouse-agent-service"
    version: str = "0.2.0"
    protocol_version: str = "1.0"

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
        planning_mode = os.getenv("AGENT_PLANNING_MODE", "deterministic").strip().lower()
        if planning_mode not in {"deterministic", "llm"}:
            planning_mode = "deterministic"
        deployment_environment = os.getenv("AGENT_ENV", "development").strip().lower()
        default_state_backend = "redis" if deployment_environment == "production" else "memory"
        state_backend = os.getenv("AGENT_STATE_BACKEND", default_state_backend).strip().lower()
        if state_backend not in {"memory", "redis"}:
            state_backend = default_state_backend
        return Settings(
            tool_mode=raw_mode,  # type: ignore[arg-type]
            model_mode=raw_model_mode,  # type: ignore[arg-type]
            java_tool_gateway_base_url=os.getenv(
                "JAVA_TOOL_GATEWAY_BASE_URL", "http://localhost:8080"
            ).rstrip("/"),
            agent_internal_tool_service_key=os.getenv("AGENT_INTERNAL_TOOL_SERVICE_KEY", ""),
            python_service_key=os.getenv("AGENT_PYTHON_SERVICE_KEY", ""),
            request_timeout_ms=_int_env("REQUEST_TIMEOUT_MS", 15000),
            run_timeout_ms=_int_env(
                "AGENT_RUN_TIMEOUT_MS",
                90000 if planning_mode == "llm" else 20000,
            ),
            model_base_url=os.getenv("AGENT_MODEL_BASE_URL", "").rstrip("/"),
            model_api_key=os.getenv("AGENT_MODEL_API_KEY", ""),
            model_name=os.getenv("AGENT_MODEL_NAME", ""),
            model_timeout_ms=_int_env("AGENT_MODEL_TIMEOUT_MS", 30000),
            goal_draft_shadow_enabled=_bool_env("AGENT_GOAL_DRAFT_SHADOW_ENABLED", False),
            planning_mode=planning_mode,  # type: ignore[arg-type]
            llm_allowed_experts=_csv_env(
                "AGENT_LLM_ALLOWED_EXPERTS",
                ("inventory_expert", "warehouse_expert", "assay_expert", "logistics_expert"),
            ),
            llm_max_tool_calls=_bounded_int_env("AGENT_LLM_MAX_TOOL_CALLS", 3, 1, 3),
            llm_max_tool_retries=_bounded_int_env("AGENT_LLM_MAX_TOOL_RETRIES", 1, 0, 1),
            allow_insecure_mock_auth=_bool_env("AGENT_ALLOW_INSECURE_MOCK_AUTH", False),
            deployment_environment=deployment_environment,
            state_backend=state_backend,  # type: ignore[arg-type]
            redis_url=os.getenv("AGENT_REDIS_URL", "redis://127.0.0.1:6379/0"),
            state_ttl_seconds=_int_env("AGENT_STATE_TTL_SECONDS", 3600),
            session_lock_wait_ms=_int_env("AGENT_SESSION_LOCK_WAIT_MS", 5000),
            session_lock_lease_ms=_int_env("AGENT_SESSION_LOCK_LEASE_MS", 30000),
        )


def _int_env(name: str, fallback: int) -> int:
    value = os.getenv(name)
    if value is None:
        return fallback
    try:
        return max(100, int(value))
    except ValueError:
        return fallback


def _bool_env(name: str, fallback: bool) -> bool:
    value = os.getenv(name)
    if value is None:
        return fallback
    normalized = value.strip().lower()
    if normalized in {"1", "true", "yes", "on"}:
        return True
    if normalized in {"0", "false", "no", "off"}:
        return False
    return fallback


def _csv_env(name: str, fallback: tuple[str, ...]) -> tuple[str, ...]:
    value = os.getenv(name)
    if value is None:
        return fallback
    items = tuple(dict.fromkeys(item.strip() for item in value.split(",") if item.strip()))
    return items or fallback


def _bounded_int_env(name: str, fallback: int, minimum: int, maximum: int) -> int:
    value = os.getenv(name)
    if value is None:
        return fallback
    try:
        return min(maximum, max(minimum, int(value)))
    except ValueError:
        return fallback
