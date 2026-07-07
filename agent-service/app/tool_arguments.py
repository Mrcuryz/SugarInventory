from __future__ import annotations

from typing import Any
from collections.abc import Iterator
from datetime import date

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
    "get_inventory_distribution": {
        "type": "object",
        "additionalProperties": False,
        "required": ["productScope", "warehouseScope", "groupBy"],
        "properties": {
            "productScope": {
                "type": "object",
                "additionalProperties": False,
                "required": ["type"],
                "properties": {
                    "type": {"type": "string", "enum": ["SINGLE_PRODUCT", "EXACT_PRODUCT_NAME_GROUP", "PRODUCT_TYPE_GROUP", "ALL"]},
                    "productId": {"type": "integer", "minimum": 1},
                    "productName": {"type": "string", "minLength": 1, "maxLength": 100},
                    "productType": {"type": "string", "minLength": 1, "maxLength": 50},
                },
            },
            "warehouseScope": {
                "type": "object",
                "additionalProperties": False,
                "required": ["type"],
                "properties": {
                    "type": {"type": "string", "enum": ["ALL", "SINGLE_WAREHOUSE"]},
                    "warehouseId": {"type": "integer", "minimum": 1},
                },
            },
            "statusFilter": {
                "type": "object",
                "additionalProperties": False,
                "properties": {
                    "productStatuses": {"type": "array", "maxItems": 10, "items": {"enum": ["半成品", "成品"]}},
                    "warehouseStatuses": {"type": "array", "maxItems": 10, "items": {"enum": ["正常", "空置", "满仓", "维护", "临期预警"]}},
                    "palletStatuses": {"type": "array", "maxItems": 10, "items": {"enum": ["FREE", "PENDING", "INSTOCK", "INVALID", "ORDER_RESERVED"]}},
                    "assayStatus": {"enum": ["HAS_ASSAY", "MISSING_ASSAY", "PASS", "FAIL", "NO_STANDARD", "MULTIPLE_CANDIDATES"]},
                    "entryDateFrom": {"type": "string", "format": "date"},
                    "entryDateTo": {"type": "string", "format": "date"},
                },
            },
            "groupBy": {"type": "string", "enum": ["warehouse", "product", "warehouse_product"]},
            "limit": {"type": "integer", "minimum": 1, "maximum": 100, "default": 20},
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
        arguments = decision.arguments
        if tool_name == "get_inventory_distribution":
            allow_all = self._explicit_all_product_request(user_message)
            arguments = self._distribution_arguments(arguments, state, allow_all=allow_all)
        return self._validate(tool_name, arguments)

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
        arguments = decision.arguments
        if decision.toolName == "get_inventory_distribution":
            explicit_all = (
                isinstance(arguments.get("productScope"), dict)
                and arguments["productScope"].get("type") == "ALL"
                and self._explicit_all_product_request(user_message)
            )
            if state.selected_product is None and not explicit_all:
                return ModelPlanDecision(
                    action="ask_user",
                    prompt="你想查哪个产品？请先选择或输入一个明确的产品名称。",
                    suggestions=["例如：黄冰糖（袋）在哪些库位？"],
                    confidenceNote="distribution requires a confirmed selected product",
                )
            arguments = self._distribution_arguments(arguments, state, allow_all=explicit_all)
        return ModelPlanDecision(
            action=decision.action,
            toolName=decision.toolName,
            arguments=self._validate(decision.toolName, arguments),
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

    def distribution_arguments_for_state(
        self, arguments: dict[str, Any], state: WarehouseAgentState
    ) -> dict[str, Any]:
        return self._validate(
            "get_inventory_distribution",
            self._distribution_arguments(arguments, state),
        )

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
        if tool_name == "get_inventory_distribution":
            return self._validate_distribution(arguments)
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

    def _distribution_arguments(
        self, arguments: dict[str, Any], state: WarehouseAgentState, *, allow_all: bool = False
    ) -> dict[str, Any]:
        result = dict(arguments)
        requested_scope = arguments.get("productScope")
        if allow_all and isinstance(requested_scope, dict) and requested_scope.get("type") == "ALL":
            result["productScope"] = {"type": "ALL"}
        elif state.selected_product is not None:
            metadata = state.selected_product.metadata
            scope_type = str(metadata.get("scopeType") or "SINGLE_PRODUCT")
            if scope_type == "SINGLE_PRODUCT":
                if state.selected_product.internal_id is None:
                    raise ValueError("selected product has no validated productId")
                result["productScope"] = {
                    "type": scope_type,
                    "productId": state.selected_product.internal_id,
                }
            elif scope_type == "EXACT_PRODUCT_NAME_GROUP":
                result["productScope"] = {"type": scope_type, "productName": metadata.get("productName")}
            elif scope_type == "PRODUCT_TYPE_GROUP":
                result["productScope"] = {"type": scope_type, "productType": metadata.get("productType")}
            else:
                raise ValueError("selected product scope is unsupported")
        elif not isinstance(requested_scope, dict) or requested_scope.get("type") != "ALL":
            raise ValueError("distribution product scope requires resolver provenance")

        warehouse_scope = arguments.get("warehouseScope") or {"type": "ALL"}
        if isinstance(warehouse_scope, dict) and warehouse_scope.get("type") == "SINGLE_WAREHOUSE":
            requested_id = int(warehouse_scope.get("warehouseId") or 0)
            if state.selected_warehouse is None or state.selected_warehouse.internal_id != requested_id:
                raise ValueError("warehouseId does not match selected warehouse")
        result["warehouseScope"] = warehouse_scope
        result.setdefault("statusFilter", {})
        result.setdefault("groupBy", "warehouse")
        result.setdefault("limit", 20)
        return result

    def _validate_distribution(self, arguments: dict[str, Any]) -> dict[str, Any]:
        product_scope = arguments.get("productScope")
        if not isinstance(product_scope, dict):
            raise ValueError("productScope is required")
        scope_type = product_scope.get("type")
        safe_product_scope: dict[str, Any] = {"type": scope_type}
        if scope_type == "SINGLE_PRODUCT":
            product_id = int(product_scope.get("productId") or 0)
            if product_id <= 0:
                raise ValueError("productScope.productId must be positive")
            safe_product_scope["productId"] = product_id
        elif scope_type == "EXACT_PRODUCT_NAME_GROUP":
            safe_product_scope["productName"] = self._required_text(product_scope.get("productName"), 100)
        elif scope_type == "PRODUCT_TYPE_GROUP":
            safe_product_scope["productType"] = self._required_text(product_scope.get("productType"), 50)
        elif scope_type != "ALL":
            raise ValueError("unsupported productScope.type")

        warehouse_scope = arguments.get("warehouseScope")
        if not isinstance(warehouse_scope, dict):
            raise ValueError("warehouseScope is required")
        warehouse_type = warehouse_scope.get("type")
        safe_warehouse_scope: dict[str, Any] = {"type": warehouse_type}
        if warehouse_type == "SINGLE_WAREHOUSE":
            warehouse_id = int(warehouse_scope.get("warehouseId") or 0)
            if warehouse_id <= 0:
                raise ValueError("warehouseScope.warehouseId must be positive")
            safe_warehouse_scope["warehouseId"] = warehouse_id
        elif warehouse_type != "ALL":
            raise ValueError("unsupported warehouseScope.type")

        group_by = str(arguments.get("groupBy") or "")
        if group_by not in {"warehouse", "product", "warehouse_product"}:
            raise ValueError("unsupported groupBy")
        limit = int(arguments.get("limit", 20))
        if not 1 <= limit <= 100:
            raise ValueError("limit must be 1..100")
        safe_filter = self._validate_distribution_filter(arguments.get("statusFilter"))
        result = {
            "productScope": safe_product_scope,
            "warehouseScope": safe_warehouse_scope,
            "groupBy": group_by,
            "limit": limit,
        }
        if safe_filter:
            result["statusFilter"] = safe_filter
        return result

    def _validate_distribution_filter(self, value: Any) -> dict[str, Any]:
        if value is None:
            return {}
        if not isinstance(value, dict):
            raise ValueError("statusFilter must be an object")
        result: dict[str, Any] = {}
        for key, allowed in {
            "productStatuses": {"半成品", "成品"},
            "warehouseStatuses": {"正常", "空置", "满仓", "维护", "临期预警"},
            "palletStatuses": {"FREE", "PENDING", "INSTOCK", "INVALID", "ORDER_RESERVED"},
        }.items():
            values = value.get(key, [])
            if (
                not isinstance(values, list)
                or len(values) > 10
                or any(not isinstance(item, str) or item not in allowed for item in values)
            ):
                raise ValueError(f"unsupported {key}")
            if values:
                result[key] = values
        assay_status = value.get("assayStatus")
        if assay_status is not None:
            if assay_status not in {"HAS_ASSAY", "MISSING_ASSAY", "PASS", "FAIL", "NO_STANDARD", "MULTIPLE_CANDIDATES"}:
                raise ValueError("unsupported assayStatus")
            result["assayStatus"] = assay_status
        date_from = self._optional_date(value.get("entryDateFrom"))
        date_to = self._optional_date(value.get("entryDateTo"))
        if date_from and date_to and date_from > date_to:
            raise ValueError("entryDateFrom must not be after entryDateTo")
        if date_from:
            result["entryDateFrom"] = date_from
        if date_to:
            result["entryDateTo"] = date_to
        return result

    def _required_text(self, value: Any, max_length: int) -> str:
        text = str(value or "").strip()
        if not 1 <= len(text) <= max_length:
            raise ValueError("scope text is invalid")
        return text

    def _optional_date(self, value: Any) -> str | None:
        if value is None or value == "":
            return None
        text = str(value)
        try:
            parsed = date.fromisoformat(text)
        except ValueError as exc:
            raise ValueError("date filter must use YYYY-MM-DD")
        return parsed.isoformat()

    def _explicit_all_product_request(self, user_message: str) -> bool:
        return any(phrase in user_message for phrase in ["全部产品", "所有产品", "全产品", "全部品种", "所有品种"])
