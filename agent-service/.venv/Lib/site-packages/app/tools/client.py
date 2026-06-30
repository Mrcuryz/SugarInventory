from __future__ import annotations

import json
import socket
import uuid
from dataclasses import dataclass
from typing import Any, Protocol
from urllib.error import HTTPError, URLError
from urllib.request import Request, urlopen

from app.config import Settings


ALLOWED_TOOLS = {
    "resolve_products",
    "resolve_warehouses",
    "get_inventory_overview",
    "get_warehouse_status",
    "get_pallet_status",
    "get_assay_status",
}


@dataclass
class ToolGatewayError(Exception):
    code: str
    message: str
    retryable: bool = False
    upstream_status: int | None = None


class AgentToolClient(Protocol):
    def call_tool(
        self,
        *,
        agent_session_id: str,
        tool_name: str,
        arguments: dict[str, Any],
        trace_id: str | None = None,
        request_id: str | None = None,
    ) -> dict[str, Any]:
        ...


class JavaGatewayToolClient:
    def __init__(self, settings: Settings) -> None:
        self._base_url = settings.java_tool_gateway_base_url.rstrip("/")
        self._service_key = settings.agent_internal_tool_service_key
        self._timeout = settings.request_timeout_ms / 1000

    def call_tool(
        self,
        *,
        agent_session_id: str,
        tool_name: str,
        arguments: dict[str, Any],
        trace_id: str | None = None,
        request_id: str | None = None,
    ) -> dict[str, Any]:
        if tool_name not in ALLOWED_TOOLS:
            raise ToolGatewayError("TOOL_NOT_ALLOWED", "当前工具不允许被 Agent 调用。")
        if not self._service_key:
            raise ToolGatewayError("SERVICE_AUTHENTICATION_FAILED", "内部工具服务认证未配置。")

        payload = {
            "agentSessionId": agent_session_id,
            "toolCallId": f"py_{uuid.uuid4().hex[:16]}",
            "arguments": arguments,
            "client": {"traceId": trace_id, "requestId": request_id},
        }
        body = json.dumps(payload, ensure_ascii=False).encode("utf-8")
        request = Request(
            f"{self._base_url}/internal/agent/tools/{tool_name}",
            data=body,
            method="POST",
            headers={
                "Content-Type": "application/json; charset=utf-8",
                "X-Agent-Service-Key": self._service_key,
            },
        )

        try:
            with urlopen(request, timeout=self._timeout) as response:  # noqa: S310 - fixed internal gateway URL only
                status_code = int(response.status)
                response_body = response.read().decode("utf-8")
        except HTTPError as exc:
            status_code = int(exc.code)
            if status_code in (401, 403):
                code = "UPSTREAM_UNAUTHORIZED" if status_code == 401 else "UPSTREAM_PERMISSION_DENIED"
                raise ToolGatewayError(code, "当前用户无法完成该只读查询。", False, status_code) from exc
            if status_code >= 500:
                raise ToolGatewayError("UPSTREAM_SERVER_ERROR", "仓储后端暂时无法完成查询。", True, status_code) from exc
            raise ToolGatewayError("UPSTREAM_BAD_REQUEST", "仓储后端拒绝了查询请求。", False, status_code) from exc
        except socket.timeout as exc:
            raise ToolGatewayError("UPSTREAM_TIMEOUT", "仓储工具调用超时。", True) from exc
        except URLError as exc:
            reason = getattr(exc, "reason", None)
            if isinstance(reason, socket.timeout):
                raise ToolGatewayError("UPSTREAM_TIMEOUT", "仓储工具调用超时。", True) from exc
            raise ToolGatewayError("UPSTREAM_UNAVAILABLE", "仓储工具暂时不可用。", True) from exc

        if status_code in (401, 403):
            code = "UPSTREAM_UNAUTHORIZED" if status_code == 401 else "UPSTREAM_PERMISSION_DENIED"
            raise ToolGatewayError(code, "当前用户无法完成该只读查询。", False, status_code)
        if status_code >= 500:
            raise ToolGatewayError("UPSTREAM_SERVER_ERROR", "仓储后端暂时无法完成查询。", True, status_code)
        if status_code >= 400:
            raise ToolGatewayError("UPSTREAM_BAD_REQUEST", "仓储后端拒绝了查询请求。", False, status_code)

        try:
            envelope = json.loads(response_body)
        except json.JSONDecodeError as exc:
            raise ToolGatewayError("UPSTREAM_BAD_RESPONSE", "仓储工具返回格式异常。", True) from exc

        if envelope.get("status") == "ERROR":
            error = envelope.get("error") or {}
            raise ToolGatewayError(
                code=str(error.get("code") or "TOOL_ERROR"),
                message=_safe_error_message(str(error.get("code") or "TOOL_ERROR")),
                retryable=bool(error.get("retryable", False)),
                upstream_status=error.get("upstreamStatus"),
            )
        return envelope.get("result") or {}


class MockToolClient:
    """Test-only tool client. Runtime main path remains java_gateway."""

    def __init__(self, responses: dict[str, Any] | None = None) -> None:
        self.responses = responses or {}
        self.calls: list[dict[str, Any]] = []

    def call_tool(
        self,
        *,
        agent_session_id: str,
        tool_name: str,
        arguments: dict[str, Any],
        trace_id: str | None = None,
        request_id: str | None = None,
    ) -> dict[str, Any]:
        if tool_name not in ALLOWED_TOOLS:
            raise ToolGatewayError("TOOL_NOT_ALLOWED", "当前工具不允许被 Agent 调用。")
        self.calls.append(
            {
                "agentSessionId": agent_session_id,
                "toolName": tool_name,
                "arguments": arguments,
                "traceId": trace_id,
                "requestId": request_id,
            }
        )
        response = self.responses.get(tool_name, {})
        if isinstance(response, ToolGatewayError):
            raise response
        if callable(response):
            return response(arguments)
        return response


def _safe_error_message(code: str) -> str:
    if code in {"UPSTREAM_UNAUTHORIZED", "SERVICE_AUTHENTICATION_FAILED"}:
        return "当前查询认证失败。"
    if code in {"UPSTREAM_PERMISSION_DENIED", "AGENT_SCOPE_DENIED"}:
        return "当前用户没有执行该只读查询的权限。"
    if code == "UPSTREAM_TIMEOUT":
        return "查询仓储数据超时。"
    if code == "UPSTREAM_NOT_FOUND":
        return "未找到符合条件的业务数据。"
    if code == "UPSTREAM_SERVER_ERROR":
        return "仓储后端暂时无法完成查询。"
    return "只读仓储工具调用失败。"
