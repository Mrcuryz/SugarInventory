from __future__ import annotations

import hmac

from fastapi import Depends, FastAPI, Header, HTTPException
from fastapi.responses import StreamingResponse

from app.config import Settings
from app.cancellation import AgentRunRegistry
from app.graph.state import InMemoryCheckpointer
from app.model import build_model_client
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
from app.tools.client import AgentToolClient, JavaGatewayToolClient, MockToolClient


def create_app(
    settings: Settings | None = None,
    tool_client: AgentToolClient | None = None,
    checkpointer: InMemoryCheckpointer | None = None,
) -> FastAPI:
    settings = settings or Settings.from_env()
    tool_client = tool_client or _build_tool_client(settings)
    runtime = WarehouseAgentRuntime(
        tool_client=tool_client,
        checkpointer=checkpointer,
        argument_builder=ToolArgumentBuilder(model_client=build_model_client(settings)),
    )
    run_registry = AgentRunRegistry()

    app = FastAPI(title="Warehouse Agent Service", version=settings.version)
    app.state.settings = settings
    app.state.runtime = runtime
    app.state.run_registry = run_registry

    def authorize_java(
        x_agent_service_key: str | None = Header(default=None, alias="X-Agent-Service-Key"),
    ) -> None:
        if settings.tool_mode == "mock":
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
        return HealthResponse(
            status="UP",
            service=settings.service_name,
            version=settings.version,
            dependencies={
                "model": settings.model_mode.upper(),
                "toolGateway": gateway_state,
                "memory": "UP",
            },
        )

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
                runtime.stream_answer_deltas,
                settings.request_timeout_ms,
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

    return app


def _build_tool_client(settings: Settings) -> AgentToolClient:
    if settings.tool_mode == "mock":
        return MockToolClient()
    return JavaGatewayToolClient(settings)


app = create_app()
