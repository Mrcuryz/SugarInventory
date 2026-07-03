from __future__ import annotations

from typing import Any
from collections.abc import Iterator

from app.context import ContextBuilder
from app.graph.state import WarehouseAgentState
from app.model import BasicModelClient, ModelArgumentRequest, ModelClient, ModelPlanDecision, ModelPlanRequest
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
    "get_inventory_overview": {
        "type": "object",
        "additionalProperties": False,
        "required": ["productId"],
        "properties": {
            "productId": {"type": "integer", "minimum": 1},
        },
    },
    "get_warehouse_status": {
        "type": "object",
        "additionalProperties": False,
        "required": ["warehouseId"],
        "properties": {
            "warehouseId": {"type": "integer", "minimum": 1},
        },
    },
    "get_assay_status": {
        "type": "object",
        "additionalProperties": False,
        "properties": {
            "productId": {"type": "integer", "minimum": 1},
            "productionDate": {"type": "string", "maxLength": 20},
            "assayId": {"type": "integer", "minimum": 1},
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

    def plan(self, *, user_message: str, state: WarehouseAgentState) -> ModelPlanDecision:
        decision = self._model_client.plan_next_action(
            ModelPlanRequest(
                userMessage=user_message,
                messages=state.messages,
                state=state,
                domainContext=self._context_builder.build(user_message, state),
                toolSchemas=TOOL_SCHEMAS,
            )
        )
        if decision.action != "call_tool":
            return decision
        if not decision.toolName or decision.toolName not in ALLOWED_TOOLS:
            raise ValueError("planned tool is not allowed")
        return ModelPlanDecision(
            action=decision.action,
            toolName=decision.toolName,
            arguments=self._validate(decision.toolName, decision.arguments),
            intent=decision.intent,
            responseMode=decision.responseMode,
            answer=decision.answer,
            prompt=decision.prompt,
            suggestions=decision.suggestions,
            confidenceNote=decision.confidenceNote,
        )

    def context_packs(self, user_message: str, state: WarehouseAgentState) -> list[str]:
        return [pack.name for pack in self._context_builder.build(user_message, state)]

    def stream_answer_deltas(self, answer: str) -> Iterator[str]:
        return self._model_client.stream_answer_deltas(answer)

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
            flow_limit = int(arguments.get("flowLimit", 20))
            if not 1 <= flow_limit <= 100:
                raise ValueError("flowLimit must be 1..100")
            return {
                "code": code,
                "includeInventory": bool(arguments.get("includeInventory", True)),
                "includeAssay": bool(arguments.get("includeAssay", True)),
                "includeFlows": bool(arguments.get("includeFlows", True)),
                "flowLimit": flow_limit,
            }
        if tool_name == "get_inventory_overview":
            product_id = int(arguments.get("productId") or 0)
            if product_id <= 0:
                raise ValueError("productId must be positive")
            return {"productId": product_id}
        if tool_name == "get_warehouse_status":
            warehouse_id = int(arguments.get("warehouseId") or 0)
            if warehouse_id <= 0:
                raise ValueError("warehouseId must be positive")
            return {"warehouseId": warehouse_id}
        if tool_name == "get_assay_status":
            result: dict[str, Any] = {}
            if arguments.get("assayId") is not None:
                assay_id = int(arguments.get("assayId") or 0)
                if assay_id <= 0:
                    raise ValueError("assayId must be positive")
                result["assayId"] = assay_id
            if arguments.get("productId") is not None:
                product_id = int(arguments.get("productId") or 0)
                if product_id <= 0:
                    raise ValueError("productId must be positive")
                result["productId"] = product_id
            production_date = str(arguments.get("productionDate") or "").strip()
            if production_date:
                result["productionDate"] = production_date[:20]
            if "assayId" not in result and "productId" not in result:
                raise ValueError("get_assay_status requires assayId or productId")
            return result
        return dict(arguments)
