from __future__ import annotations

import hmac
import logging
from pathlib import Path
import re

from fastapi import Depends, FastAPI, Header, HTTPException
from fastapi.responses import StreamingResponse

from app.config import Settings
from app.cancellation import AgentRunRegistry
from app.graph.state import InMemoryCheckpointer
from app.agents import AgentHandoffRouter
from app.orchestration import capability_snapshot
from app.session_lock import RedisSessionLock
from app.state_store import RedisStateStore, StateCheckpointer
from app.model import build_model_client
from app.observability import MetricsRegistry
from app.runtime import WarehouseAgentRuntime
from app.schemas import (
    CandidateSelectedMessage,
    CancelRequest,
    CancelResponse,
    ChatRequest,
    ChatResponse,
    HealthResponse,
    ResumeEvent,
    ResumeRequest,
)
from app.streaming import sse_for_request
from app.tool_arguments import ToolArgumentBuilder
from app.tools.client import ALLOWED_TOOLS, AgentToolClient, JavaGatewayToolClient, MockToolClient


logger = logging.getLogger(__name__)


def create_app(
    settings: Settings | None = None,
    tool_client: AgentToolClient | None = None,
    checkpointer: StateCheckpointer | None = None,
) -> FastAPI:
    settings = settings or Settings.from_env()
    if settings.deployment_environment == "production" and settings.planning_mode == "llm":
        raise RuntimeError("Experimental LLM planning mode is forbidden in production.")
    if settings.planning_mode == "llm" and settings.model_mode != "openai_compatible":
        raise RuntimeError("LLM planning mode requires an OpenAI-compatible model client.")
    tool_client = tool_client or _build_tool_client(settings)
    metrics = MetricsRegistry()
    checkpointer = checkpointer or _build_checkpointer(settings, metrics)
    model_client = build_model_client(settings)
    runtime = WarehouseAgentRuntime(
        tool_client=tool_client,
        checkpointer=checkpointer,
        argument_builder=ToolArgumentBuilder(
            model_client=model_client,
            goal_draft_shadow_enabled=settings.goal_draft_shadow_enabled,
        ),
        metrics=metrics,
        model_client=model_client,
        planning_mode=settings.planning_mode,
        llm_allowed_experts=settings.llm_allowed_experts,
        llm_max_tool_calls=settings.llm_max_tool_calls,
        llm_max_tool_retries=settings.llm_max_tool_retries,
    )
    run_registry = AgentRunRegistry()

    app = FastAPI(title="Warehouse Agent Service", version=settings.version)
    app.state.settings = settings
    app.state.runtime = runtime
    app.state.run_registry = run_registry
    app.state.metrics = metrics
    startup_capabilities = capability_snapshot(AgentHandoffRouter(), ALLOWED_TOOLS)
    logger.info(
        "Agent runtime ready runtimeVersion=%s protocolVersion=%s planningMode=%s codeSource=%s toolRegistryHash=%s recipeRegistryHash=%s agentProfileRegistryHash=%s",
        settings.version,
        settings.protocol_version,
        settings.planning_mode,
        Path(__file__).resolve(),
        startup_capabilities["toolRegistryHash"],
        startup_capabilities["recipeRegistryHash"],
        startup_capabilities["agentProfileRegistryHash"],
    )

    def authorize_java(
        x_agent_service_key: str | None = Header(default=None, alias="X-Agent-Service-Key"),
    ) -> None:
        if settings.tool_mode == "mock" and settings.allow_insecure_mock_auth:
            return
        expected = settings.python_service_key
        if not expected:
            raise HTTPException(status_code=503, detail="Agent service authentication is not configured.")
        if not x_agent_service_key or not hmac.compare_digest(x_agent_service_key, expected):
            raise HTTPException(status_code=401, detail="Agent service authentication failed.")

    @app.get(
        "/internal/agent/health",
        response_model=HealthResponse,
        dependencies=[Depends(authorize_java)],
    )
    def health() -> HealthResponse:
        gateway_state = "UP"
        if settings.tool_mode == "java_gateway" and (
            not settings.java_tool_gateway_base_url or not settings.agent_internal_tool_service_key
        ):
            gateway_state = "MISCONFIGURED"
        state_store = "UP"
        try:
            checkpointer.store.load("__healthcheck__")
        except Exception:
            state_store = "DOWN"
        return HealthResponse(
            status="UP" if state_store == "UP" else "DOWN",
            service=settings.service_name,
            version=settings.version,
            dependencies={
                "model": settings.model_mode.upper(),
                "toolGateway": gateway_state,
                "stateStore": state_store,
                "planningMode": settings.planning_mode.upper(),
            },
        )

    @app.get(
        "/internal/agent/capabilities",
        dependencies=[Depends(authorize_java)],
    )
    def capabilities() -> dict[str, object]:
        snapshot = capability_snapshot(AgentHandoffRouter(), ALLOWED_TOOLS)
        return {
            "runtimeVersion": settings.version,
            "protocolVersion": settings.protocol_version,
            "planningMode": settings.planning_mode,
            "llmAllowedExperts": list(settings.llm_allowed_experts) if settings.planning_mode == "llm" else [],
            **snapshot,
        }

    @app.get(
        "/internal/agent/metrics",
        dependencies=[Depends(authorize_java)],
    )
    def metrics_snapshot() -> dict[str, object]:
        return metrics.snapshot()

    @app.post(
        "/internal/agent/chat",
        response_model=ChatResponse,
        dependencies=[Depends(authorize_java)],
    )
    def chat(request: ChatRequest) -> ChatResponse:
        if isinstance(request.message, CandidateSelectedMessage):
            return runtime.resume(
                ResumeRequest(
                    agentSessionId=request.agentSessionId,
                    messageId=request.messageId,
                    resumeToken=request.message.resumeToken,
                    user=request.user,
                    event=ResumeEvent(
                        type="candidate_selected",
                        interruptId=request.message.interruptId,
                        action=request.message.action,
                        selection=request.message.selection,
                        clientRequestId=request.message.clientRequestId,
                    ),
                    client=request.client,
                )
            )
        return runtime.chat(request)

    @app.post(
        "/internal/agent/chat/stream",
        dependencies=[Depends(authorize_java)],
    )
    def chat_stream(request: ChatRequest) -> StreamingResponse:
        return StreamingResponse(
            sse_for_request(
                request,
                runtime.chat,
                runtime.resume,
                run_registry,
                None if settings.planning_mode == "llm" else runtime.stream_answer_deltas,
                settings.run_timeout_ms,
            ),
            media_type="text/event-stream",
        )

    @app.post(
        "/internal/agent/cancel",
        response_model=CancelResponse,
        dependencies=[Depends(authorize_java)],
    )
    def cancel(request: CancelRequest) -> CancelResponse:
        run_registry.cancel(request.agentSessionId, request.messageId)
        return CancelResponse(
            agentSessionId=request.agentSessionId,
            messageId=request.messageId,
            cancelled=True,
        )

    @app.post(
        "/internal/agent/resume",
        response_model=ChatResponse,
        dependencies=[Depends(authorize_java)],
    )
    def resume(request: ResumeRequest) -> ChatResponse:
        return runtime.resume(request)

    @app.delete(
        "/internal/agent/sessions/{agent_session_id}",
        dependencies=[Depends(authorize_java)],
    )
    def clear_session(agent_session_id: str) -> dict[str, str | bool]:
        if not re.fullmatch(r"[A-Za-z0-9_-]{1,64}", agent_session_id):
            raise HTTPException(status_code=422, detail="Invalid Agent session id.")
        runtime.clear_session(agent_session_id)
        return {"agentSessionId": agent_session_id, "cleared": True}

    return app


def _build_tool_client(settings: Settings) -> AgentToolClient:
    if settings.tool_mode == "mock":
        return MockToolClient()
    return JavaGatewayToolClient(settings)


def _build_checkpointer(settings: Settings, metrics: MetricsRegistry) -> StateCheckpointer:
    if settings.deployment_environment == "production" and settings.state_backend != "redis":
        raise RuntimeError("Production Agent runtime requires Redis state storage.")
    if settings.state_backend == "memory":
        return InMemoryCheckpointer(
            ttl_seconds=settings.state_ttl_seconds,
            wait_timeout_seconds=settings.session_lock_wait_ms / 1000,
        )
    try:
        import redis
    except ImportError as exc:
        raise RuntimeError("Redis state backend requires the redis package.") from exc
    client = redis.Redis.from_url(settings.redis_url, decode_responses=False)
    client.ping()
    return StateCheckpointer(
        RedisStateStore(client, ttl_seconds=settings.state_ttl_seconds),
        RedisSessionLock(
            client,
            wait_timeout_seconds=settings.session_lock_wait_ms / 1000,
            lease_ms=settings.session_lock_lease_ms,
            wait_observer=lambda seconds: metrics.observe("session_lock_wait_duration", seconds),
        ),
    )


app = create_app()
