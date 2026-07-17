from __future__ import annotations

from datetime import datetime, timezone
import hashlib
import json
from typing import Any, Literal

from pydantic import BaseModel, ConfigDict, Field

from app.state_models import EntityContextV1


CoreGoalTypeV1 = Literal[
    "CURRENT_PRODUCT_INVENTORY",
    "PRODUCT_INVENTORY_DISTRIBUTION",
    "WAREHOUSE_INVENTORY_DISTRIBUTION",
]
CoreFactTypeV1 = Literal[
    "CURRENT_PRODUCT_INVENTORY",
    "PRODUCT_INVENTORY_DISTRIBUTION",
    "WAREHOUSE_INVENTORY_DISTRIBUTION",
]
FactStatusV1 = Literal["AVAILABLE", "NO_DATA", "INVALID", "TOOL_ERROR"]
CompletionStatusV1 = Literal[
    "COMPLETE",
    "PARTIAL",
    "NEEDS_CLARIFICATION",
    "UNSUPPORTED",
    "FAILED",
]


class GoalContractV1(BaseModel):
    model_config = ConfigDict(extra="forbid", frozen=True)

    schemaVersion: Literal["1.0"] = "1.0"
    goalType: CoreGoalTypeV1
    requiredEntityTypes: tuple[Literal["PRODUCT", "WAREHOUSE"], ...]
    requiredFactTypes: tuple[CoreFactTypeV1, ...]
    allowedTools: tuple[str, ...]
    completionPolicy: Literal["ALL_REQUIRED_FACTS"] = "ALL_REQUIRED_FACTS"
    noDataCompletesGoal: bool = True
    limitations: tuple[str, ...] = ()


class FactEnvelopeV1(BaseModel):
    model_config = ConfigDict(extra="forbid")

    schemaVersion: Literal["1.0"] = "1.0"
    factRef: str = Field(pattern=r"^fact_[a-f0-9]{16}$")
    goalType: CoreGoalTypeV1
    factType: CoreFactTypeV1
    status: FactStatusV1
    entityRefs: list[str] = Field(default_factory=list, max_length=4)
    observedAt: datetime
    sourceTool: str
    scope: dict[str, str] = Field(default_factory=dict)
    data: dict[str, Any] = Field(default_factory=dict)
    limitations: list[str] = Field(default_factory=list, max_length=20)


class GoalCompletionV1(BaseModel):
    model_config = ConfigDict(extra="forbid")

    schemaVersion: Literal["1.0"] = "1.0"
    goalType: CoreGoalTypeV1
    status: CompletionStatusV1
    evidenceRefs: list[str] = Field(default_factory=list, max_length=20)
    missingEntityTypes: list[str] = Field(default_factory=list, max_length=4)
    missingFactTypes: list[str] = Field(default_factory=list, max_length=10)
    failedFactTypes: list[str] = Field(default_factory=list, max_length=10)
    limitations: list[str] = Field(default_factory=list, max_length=20)


GOAL_CONTRACTS: dict[CoreGoalTypeV1, GoalContractV1] = {
    "CURRENT_PRODUCT_INVENTORY": GoalContractV1(
        goalType="CURRENT_PRODUCT_INVENTORY",
        requiredEntityTypes=("PRODUCT",),
        requiredFactTypes=("CURRENT_PRODUCT_INVENTORY",),
        allowedTools=("resolve_products", "get_inventory_overview"),
    ),
    "PRODUCT_INVENTORY_DISTRIBUTION": GoalContractV1(
        goalType="PRODUCT_INVENTORY_DISTRIBUTION",
        requiredEntityTypes=("PRODUCT",),
        requiredFactTypes=("PRODUCT_INVENTORY_DISTRIBUTION",),
        allowedTools=("resolve_products", "get_inventory_distribution", "get_inventory_overview"),
        limitations=("库存分布只表达当前库存，不是历史流水。",),
    ),
    "WAREHOUSE_INVENTORY_DISTRIBUTION": GoalContractV1(
        goalType="WAREHOUSE_INVENTORY_DISTRIBUTION",
        requiredEntityTypes=("WAREHOUSE",),
        requiredFactTypes=("WAREHOUSE_INVENTORY_DISTRIBUTION",),
        allowedTools=("resolve_warehouses", "get_inventory_distribution"),
        limitations=("库位库存只表达当前库存，不自动包含批次化验结论。",),
    ),
}


def registered_goal_for_plan(
    *,
    tool_name: str | None,
    arguments: dict[str, Any] | None,
    intent: str | None,
    response_mode: str | None = None,
) -> CoreGoalTypeV1 | None:
    arguments = arguments or {}
    if tool_name == "resolve_products":
        if intent == "inventory_distribution":
            return "PRODUCT_INVENTORY_DISTRIBUTION"
        if intent in {"inventory", "inventory_overview"}:
            return "CURRENT_PRODUCT_INVENTORY"
    if tool_name == "resolve_warehouses" and intent == "inventory_distribution":
        return "WAREHOUSE_INVENTORY_DISTRIBUTION"
    if tool_name == "get_inventory_overview":
        if response_mode == "inventory_locations":
            return "PRODUCT_INVENTORY_DISTRIBUTION"
        return "CURRENT_PRODUCT_INVENTORY"
    if tool_name == "get_inventory_distribution":
        warehouse_scope = _mapping(arguments.get("warehouseScope"))
        product_scope = _mapping(arguments.get("productScope"))
        if warehouse_scope.get("type") == "SINGLE_WAREHOUSE":
            return "WAREHOUSE_INVENTORY_DISTRIBUTION"
        if product_scope.get("type") in {
            "SINGLE_PRODUCT",
            "EXACT_PRODUCT_NAME_GROUP",
            "PRODUCT_TYPE_GROUP",
        }:
            return "PRODUCT_INVENTORY_DISTRIBUTION"
    return None


def build_fact_envelope(
    *,
    goal_type: CoreGoalTypeV1,
    tool_name: str,
    arguments: dict[str, Any] | None,
    safe_data: dict[str, Any],
    entity_contexts: dict[str, EntityContextV1],
) -> FactEnvelopeV1:
    contract = GOAL_CONTRACTS[goal_type]
    if tool_name not in contract.allowedTools:
        raise ValueError(f"tool {tool_name} is not allowed for goal {goal_type}")
    fact_type: CoreFactTypeV1 = goal_type
    status, validation_limitations = _fact_status(goal_type, safe_data)
    notes = safe_data.get("notes")
    limitations = [str(item)[:300] for item in notes] if isinstance(notes, list) else []
    limitations.extend(validation_limitations)
    entity_refs = [
        context.entity_ref
        for entity_type, context in entity_contexts.items()
        if entity_type in contract.requiredEntityTypes and context.entity_ref
    ]
    scope = _safe_scope(arguments or {})
    fingerprint_input = {
        "goalType": goal_type,
        "factType": fact_type,
        "sourceTool": tool_name,
        "entityRefs": entity_refs,
        "scope": scope,
        "data": safe_data,
    }
    digest = hashlib.sha256(
        json.dumps(fingerprint_input, ensure_ascii=False, sort_keys=True, default=str).encode("utf-8")
    ).hexdigest()[:16]
    return FactEnvelopeV1(
        factRef=f"fact_{digest}",
        goalType=goal_type,
        factType=fact_type,
        status=status,
        entityRefs=entity_refs,
        observedAt=datetime.now(timezone.utc),
        sourceTool=tool_name,
        scope=scope,
        data=dict(safe_data),
        limitations=_unique(limitations),
    )


class GoalCompletionEvaluator:
    def evaluate(
        self,
        *,
        goal_type: CoreGoalTypeV1,
        entity_contexts: dict[str, EntityContextV1],
        facts: list[FactEnvelopeV1],
        needs_clarification: bool = False,
        unsupported: bool = False,
    ) -> GoalCompletionV1:
        contract = GOAL_CONTRACTS[goal_type]
        if unsupported:
            return GoalCompletionV1(goalType=goal_type, status="UNSUPPORTED")

        missing_entities = [
            entity_type
            for entity_type in contract.requiredEntityTypes
            if not _resolved_entity(entity_contexts.get(entity_type))
        ]
        if needs_clarification or missing_entities:
            return GoalCompletionV1(
                goalType=goal_type,
                status="NEEDS_CLARIFICATION",
                missingEntityTypes=missing_entities,
                limitations=list(contract.limitations),
            )

        relevant = [fact for fact in facts if fact.goalType == goal_type]
        latest_by_type: dict[str, FactEnvelopeV1] = {}
        for fact in relevant:
            current = latest_by_type.get(fact.factType)
            if current is None or fact.observedAt >= current.observedAt:
                latest_by_type[fact.factType] = fact

        missing_facts = [fact for fact in contract.requiredFactTypes if fact not in latest_by_type]
        failed_facts = [
            fact_type
            for fact_type, fact in latest_by_type.items()
            if fact.status in {"INVALID", "TOOL_ERROR"}
        ]
        evidence = [
            fact.factRef
            for fact in latest_by_type.values()
            if fact.status in {"AVAILABLE", "NO_DATA"}
        ]
        limitations = list(contract.limitations)
        for fact in latest_by_type.values():
            limitations.extend(fact.limitations)

        if failed_facts and not evidence:
            status: CompletionStatusV1 = "FAILED"
        elif failed_facts or missing_facts:
            status = "PARTIAL"
        else:
            status = "COMPLETE"
        return GoalCompletionV1(
            goalType=goal_type,
            status=status,
            evidenceRefs=evidence,
            missingFactTypes=missing_facts,
            failedFactTypes=failed_facts,
            limitations=_unique(limitations),
        )


def _fact_status(
    goal_type: CoreGoalTypeV1,
    data: dict[str, Any],
) -> tuple[FactStatusV1, list[str]]:
    if goal_type == "CURRENT_PRODUCT_INVENTORY":
        if "isEmpty" not in data:
            return "INVALID", ["库存总览事实缺少 isEmpty 字段。"]
        return ("NO_DATA" if data.get("isEmpty") is True else "AVAILABLE"), []

    if "groupBy" in data and isinstance(data.get("groups"), list):
        required = {"scopeLabel", "totalEquivalentPieces", "groupBy", "groups"}
        missing = sorted(required.difference(data))
        if missing:
            return "INVALID", [f"库存分布事实缺少字段：{','.join(missing)}。"]
        groups = data.get("groups") or []
        return ("NO_DATA" if not groups else "AVAILABLE"), []

    locations = data.get("locations")
    if goal_type == "PRODUCT_INVENTORY_DISTRIBUTION" and isinstance(locations, list) and locations:
        return "AVAILABLE", []
    return "INVALID", ["当前结果不足以证明库存分布目标已经完成。"]


def _safe_scope(arguments: dict[str, Any]) -> dict[str, str]:
    scope: dict[str, str] = {}
    for source_key, target_key in (
        ("productScope", "productScopeType"),
        ("warehouseScope", "warehouseScopeType"),
    ):
        scope_type = _mapping(arguments.get(source_key)).get("type")
        if isinstance(scope_type, str):
            scope[target_key] = scope_type
    group_by = arguments.get("groupBy")
    if isinstance(group_by, str):
        scope["groupBy"] = group_by
    return scope


def _resolved_entity(context: EntityContextV1 | None) -> bool:
    return bool(
        context
        and context.resolution_status == "UNIQUE"
        and context.entity_ref
        and (context.internal_id is not None or context.canonical_name)
    )


def _mapping(value: Any) -> dict[str, Any]:
    return value if isinstance(value, dict) else {}


def _unique(values: list[str]) -> list[str]:
    return list(dict.fromkeys(value for value in values if value))[:20]
