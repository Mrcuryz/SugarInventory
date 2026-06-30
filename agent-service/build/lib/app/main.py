from __future__ import annotations

from fastapi import FastAPI
from fastapi.responses import StreamingResponse

from app.config import Settings
from app.graph.state import InMemoryCheckpointer
from app.runtime import WarehouseAgentRuntime
from app.schemas import ChatRequest, ChatResponse, HealthResponse, ResumeRequest
from app.streaming import sse_for_response
from app.tools.client import AgentToolClient, JavaGatewayToolClient, MockToolClient


def create_app(
    settings: Settings | None = None,
    tool_client: AgentToolClient | None = None,
    checkpointer: InMemoryCheckpointer | None = None,
) -> FastAPI:
    settings = settings or Settings.from_env()
    tool_client = tool_client or _build_tool_client(settings)
    runtime = WarehouseAgentRuntime(tool_client=tool_client, checkpointer=checkpointer)

    app = FastAPI(title="Warehouse Agent Service", version=settings.version)
    app.state.settings = settings
    app.state.runtime = runtime

    @app.get("/internal/agent/health", response_model=HealthResponse)
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
                "model": "BASIC_RUNTIME",
                "toolGateway": gateway_state,
                "memory": "UP",
            },
        )

    @app.post("/internal/agent/chat", response_model=ChatResponse)
    def chat(request: ChatRequest) -> ChatResponse:
        return runtime.chat(request)

    @app.post("/internal/agent/chat/stream")
    def chat_stream(request: ChatRequest) -> StreamingResponse:
        response = runtime.chat(request)
        return StreamingResponse(sse_for_response(response), media_type="text/event-stream")

    @app.post("/internal/agent/resume", response_model=ChatResponse)
    def resume(request: ResumeRequest) -> ChatResponse:
        return runtime.resume(request)

    return app


def _build_tool_client(settings: Settings) -> AgentToolClient:
    if settings.tool_mode == "mock":
        return MockToolClient()
    return JavaGatewayToolClient(settings)


app = create_app()


