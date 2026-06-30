from __future__ import annotations

from typing import Any

from app.context import ContextBuilder
from app.graph.state import WarehouseAgentState
from app.model import BasicModelClient, ModelArgumentRequest, ModelClient
from app.tools.client import ALLOWED_TOOLS


TOOL_SCHEMAS: dict[str, dict[str, Any]] = {
    "resolve_products": {
        "type": "object",
        "additionalProperties": False,
        "required": ["query"],
        "properties": {
            "query": {"type": "string", "minLength": 1, "maxLength": 100},
            "limit": {"type": "integer", "minimum": 1, "maximum": 100, "default": 10},
        },
    },
    "resolve_warehouses": {
        "type": "object",
        "additionalProperties": False,
        "required": ["query"],
        "properties": {
            "query": {"type": "string", "minLength": 1, "maxLength": 100},
            "limit": {"type": "integer", "minimum": 1, "maximum": 100, "default": 10},
        },
    },
    "get_pallet_status": {
        "type": "object",
        "additionalProperties": False,
        "required": ["code"],
        "properties": {
            "code": {"type": "string", "minLength": 1, "maxLength": 100},
            "includeInventory": {"type": "boolean", "default": True},
            "includeAssay": {"type": "boolean", "default": True},
            "includeFlows": {"type": "boolean", "default": True},
            "flowLimit": {"type": "integer", "minimum": 1, "maximum": 100, "default": 20},
        },
    },
}


class ToolArgumentBuilder:
    def __init__(
        self,
        model_client: ModelClient | None = None,
        context_builder: ContextBuilder | None = None,
    ) -> None:
        self._model_client = model_client or BasicModelClient()
        self._context_builder = context_builder or ContextBuilder()

    def build(self, *, tool_name: str, user_message: str, state: WarehouseAgentState) -> dict[str, Any]:
        if tool_name not in ALLOWED_TOOLS:
            raise ValueError("tool is not allowed")
        schema = TOOL_SCHEMAS.get(tool_name, {"type": "object", "properties": {}})
        decision = self._model_client.build_tool_arguments(
            ModelArgumentRequest(
                toolName=tool_name,
                userMessage=user_message,
                messages=state.messages,
                state=state,
                domainContext=self._context_builder.build(user_message, state),
                toolSchema=schema,
            )
        )
        return self._validate(tool_name, decision.arguments)

    def context_packs(self, user_message: str, state: WarehouseAgentState) -> list[str]:
        return [pack.name for pack in self._context_builder.build(user_message, state)]

    def _validate(self, tool_name: str, arguments: dict[str, Any]) -> dict[str, Any]:
        if tool_name in {"resolve_products", "resolve_warehouses"}:
            query = str(arguments.get("query") or "").strip()
            if not 1 <= len(query) <= 100:
                raise ValueError("query must be 1..100 characters")
            limit = int(arguments.get("limit", 10))
            if not 1 <= limit <= 100:
                raise ValueError("limit must be 1..100")
            return {"query": query, "limit": limit}
        if tool_name == "get_pallet_status":
            code = str(arguments.get("code") or "").strip()
            if not 1 <= len(code) <= 100:
                raise ValueError("code must be 1..100 characters")
            return {
                "code": code,
                "includeInventory": bool(arguments.get("includeInventory", True)),
                "includeAssay": bool(arguments.get("includeAssay", True)),
                "includeFlows": bool(arguments.get("includeFlows", True)),
                "flowLimit": int(arguments.get("flowLimit", 20)),
            }
        return dict(arguments)
