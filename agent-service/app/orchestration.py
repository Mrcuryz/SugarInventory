from __future__ import annotations

from dataclasses import dataclass
from datetime import datetime, timezone
import hashlib
import json
import re
import secrets
from typing import Any

from app.agents import AgentHandoffRouter


MAX_ORCHESTRATION_STEPS = 4
MAX_ORCHESTRATION_TOOL_CALLS = 12
MAX_ASSAY_PRODUCT_FANOUT = 5
PLAN_VERSION = 1
DATA_SCOPE_PRODUCT_LATEST_ASSAY = "PRODUCT_LATEST_ASSAY"
ORCHESTRATION_STATUSES = frozenset(
    {"SUCCESS", "PARTIAL_SUCCESS", "FAILED", "CANCELLED", "BUDGET_EXCEEDED", "UNSUPPORTED", "RUNNING"}
)


@dataclass(frozen=True)
class OrchestrationStep:
    step_id: str
    expert_agent: str
    business_domain: str
    tools: tuple[str, ...]
    depends_on: tuple[str, ...] = ()
    output_artifact: str | None = None
    fanout: bool = False

    def to_snapshot(self) -> dict[str, Any]:
        return {
            "step_id": self.step_id,
            "expert_agent": self.expert_agent,
            "business_domain": self.business_domain,
            "tools": list(self.tools),
            "depends_on": list(self.depends_on),
            "output_artifact": self.output_artifact,
            "fanout": self.fanout,
        }


@dataclass(frozen=True)
class CompoundExecutionPlan:
    plan_id: str
    recipe: str
    steps: tuple[OrchestrationStep, ...]
    max_tool_calls: int = MAX_ORCHESTRATION_TOOL_CALLS
    max_fanout: int = MAX_ASSAY_PRODUCT_FANOUT
    plan_version: int = PLAN_VERSION

    def validate(self, router: AgentHandoffRouter) -> None:
        if not self.steps or len(self.steps) > MAX_ORCHESTRATION_STEPS:
            raise ValueError("compound execution plan has an invalid step count")
        if self.max_tool_calls < 1 or self.max_tool_calls > MAX_ORCHESTRATION_TOOL_CALLS:
            raise ValueError("compound execution plan exceeds the tool-call budget")
        if self.max_fanout < 1 or self.max_fanout > MAX_ASSAY_PRODUCT_FANOUT:
            raise ValueError("compound execution plan exceeds the fan-out budget")

        known_steps: set[str] = set()
        worst_case_tool_calls = 0
        for step in self.steps:
            if not step.step_id or step.step_id in known_steps:
                raise ValueError("compound execution plan contains a duplicate step")
            if any(dependency not in known_steps for dependency in step.depends_on):
                raise ValueError("compound execution plan contains an invalid dependency")
            for tool_name in step.tools:
                router.authorize_tool(step.expert_agent, tool_name)
            worst_case_tool_calls += len(step.tools) * (self.max_fanout if step.fanout else 1)
            known_steps.add(step.step_id)
        if worst_case_tool_calls > self.max_tool_calls:
            raise ValueError("compound execution plan exceeds the worst-case tool-call budget")

    def to_snapshot(self) -> dict[str, Any]:
        return {
            "plan_id": self.plan_id,
            "recipe": self.recipe,
            "plan_version": self.plan_version,
            "execution_mode": "dependency_graph",
            "max_tool_calls": self.max_tool_calls,
            "max_fanout": self.max_fanout,
            "steps": [step.to_snapshot() for step in self.steps],
        }


class CompoundIntentPlanner:
    """Builds allowlisted multi-expert plans for explicitly supported recipes."""

    WAREHOUSE_INVENTORY_ASSAY = "warehouse_inventory_latest_assay"

    def __init__(self, router: AgentHandoffRouter | None = None) -> None:
        self._router = router or AgentHandoffRouter()

    def plan(self, user_message: str) -> CompoundExecutionPlan | None:
        if not self._is_warehouse_inventory_assay(user_message):
            return None
        plan = CompoundExecutionPlan(
            plan_id=f"orch_{secrets.token_hex(8)}",
            recipe=self.WAREHOUSE_INVENTORY_ASSAY,
            steps=(
                OrchestrationStep(
                    step_id="inventory_scope",
                    expert_agent="inventory_expert",
                    business_domain="inventory",
                    tools=("resolve_warehouses", "get_inventory_distribution"),
                    output_artifact="inventory_product_scope",
                ),
                OrchestrationStep(
                    step_id="latest_assay",
                    expert_agent="assay_expert",
                    business_domain="assay",
                    tools=("resolve_products", "query_assay_records"),
                    depends_on=("inventory_scope",),
                    output_artifact="latest_product_assays",
                    fanout=True,
                ),
                OrchestrationStep(
                    step_id="present",
                    expert_agent="main_agent",
                    business_domain="assistant_experience",
                    tools=(),
                    depends_on=("inventory_scope", "latest_assay"),
                ),
            ),
        )
        plan.validate(self._router)
        return plan

    def warehouse_query(self, user_message: str) -> str | None:
        match = re.search(r"([0-9０-９]{1,4})\s*号\s*(?:库位|库|位)?", user_message)
        if not match:
            return None
        number = match.group(1).translate(str.maketrans("０１２３４５６７８９", "0123456789"))
        return f"{number}号库位"

    def is_unsupported_batch_qualification(self, user_message: str) -> bool:
        supported_inventory_quality = (
            any(scope in user_message for scope in ("库存中", "库存里", "库中", "在库产品", "哪些库存", "哪些产品"))
            and any(word in user_message for word in (
                "不合格", "符合", "满足", "达标", "色值", "还原糖", "干燥失重",
                "电导灰分", "蔗糖", "不溶于水杂质", "pH", "ph",
            ))
        )
        return (
            any(scope in user_message for scope in ("库存批次", "这批库存", "当前批次"))
            and any(word in user_message for word in ("合格", "化验", "质检"))
            and not supported_inventory_quality
        )

    def _is_warehouse_inventory_assay(self, user_message: str) -> bool:
        asks_two_results = "库存情况" in user_message or (
            any(reference in user_message for reference in ("它们", "这些", "上述"))
            and any(phrase in user_message for phrase in (
                "化验情况",
                "质检情况",
                "质量情况",
                "化验如何",
                "最新化验",
                "最近化验",
                "最新质检",
            ))
        )
        return (
            self.warehouse_query(user_message) is not None
            and "库存" in user_message
            and any(word in user_message for word in ("化验", "质检", "质量"))
            and asks_two_results
            and not any(word in user_message for word in ("缺化验", "未化验", "无化验"))
        )


def capability_snapshot(router: AgentHandoffRouter, allowed_tools: set[str]) -> dict[str, Any]:
    tool_names = sorted(allowed_tools)
    recipes = [
        {
            "recipeId": CompoundIntentPlanner.WAREHOUSE_INVENTORY_ASSAY,
            "planVersion": PLAN_VERSION,
            "experts": ["inventory_expert", "assay_expert", "main_agent"],
            "maxSteps": MAX_ORCHESTRATION_STEPS,
            "maxToolCalls": MAX_ORCHESTRATION_TOOL_CALLS,
            "maxProductFanOut": MAX_ASSAY_PRODUCT_FANOUT,
        }
    ]
    profiles = [
        {
            "name": profile.name,
            "domains": sorted(profile.domains),
            "allowedTools": sorted(profile.allowed_tools),
            "allowedToolCount": len(profile.allowed_tools),
        }
        for profile in sorted(router.profiles.values(), key=lambda item: item.name)
    ]
    return {
        "toolRegistryHash": _registry_hash(tool_names),
        "recipeRegistryHash": _registry_hash(recipes),
        "agentProfileRegistryHash": _registry_hash(profiles),
        "agentProfiles": profiles,
        "toolCount": len(tool_names),
        "recipeCount": len(recipes),
        "recipes": recipes,
    }


def _registry_hash(value: Any) -> str:
    canonical = json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(",", ":"))
    return hashlib.sha256(canonical.encode("utf-8")).hexdigest()


def new_orchestration_state(plan: CompoundExecutionPlan) -> dict[str, Any]:
    now = _now()
    steps = {
        step.step_id: {
            "stepId": step.step_id,
            "expert": step.expert_agent,
            "allowedTools": sorted(
                AgentHandoffRouter().profile(step.expert_agent).allowed_tools
            ),
            "plannedTools": list(step.tools),
            "executedTools": [],
            "status": "PENDING",
            "startedAt": None,
            "finishedAt": None,
            "errorCode": None,
            "safeResultSummary": None,
        }
        for step in plan.steps
    }
    return {
        "recipeId": plan.recipe,
        "planId": plan.plan_id,
        "planVersion": plan.plan_version,
        "executionMode": "DEPENDENCY_GRAPH",
        "currentStep": plan.steps[0].step_id,
        "completedSteps": [],
        "pendingSteps": [step.step_id for step in plan.steps],
        "failedSteps": [],
        "toolCallCount": 0,
        "toolBudgetRemaining": plan.max_tool_calls,
        "productFanOutCount": 0,
        "startedAt": now,
        "finishedAt": None,
        "orchestrationStatus": "RUNNING",
        "dataScope": DATA_SCOPE_PRODUCT_LATEST_ASSAY,
        "inventoryAsOf": None,
        "assayAsOf": None,
        "batchQualificationSupported": False,
        "limitations": [
            "化验结果仅表示各产品最新化验记录。",
            "当前不支持把产品最新化验解释为库存批次逐批合格。",
        ],
        "maxSteps": MAX_ORCHESTRATION_STEPS,
        "maxToolCalls": plan.max_tool_calls,
        "maxProductFanOut": plan.max_fanout,
        "stepOrder": [step.step_id for step in plan.steps],
        "steps": steps,
        "planFingerprint": plan_fingerprint(plan),
    }


def plan_fingerprint(plan: CompoundExecutionPlan) -> str:
    identity = {
        "recipeId": plan.recipe,
        "planVersion": plan.plan_version,
        "executionMode": "DEPENDENCY_GRAPH",
        "maxSteps": MAX_ORCHESTRATION_STEPS,
        "maxToolCalls": plan.max_tool_calls,
        "maxProductFanOut": plan.max_fanout,
        "steps": [
            {
                "stepId": step.step_id,
                "expert": step.expert_agent,
                "tools": list(step.tools),
                "dependsOn": list(step.depends_on),
            }
            for step in plan.steps
        ],
    }
    return _registry_hash(identity)


def mark_step_started(state: dict[str, Any], step_id: str) -> None:
    step = _step(state, step_id)
    step["status"] = "RUNNING"
    step["startedAt"] = step.get("startedAt") or _now()
    state["currentStep"] = step_id


def mark_step_finished(
    state: dict[str, Any],
    step_id: str,
    *,
    status: str,
    safe_summary: dict[str, Any] | None = None,
    error_code: str | None = None,
) -> None:
    step = _step(state, step_id)
    step["status"] = status
    step["finishedAt"] = _now()
    step["safeResultSummary"] = safe_summary
    step["errorCode"] = error_code
    pending = state.get("pendingSteps", [])
    if step_id in pending:
        pending.remove(step_id)
    target = state["failedSteps"] if status == "FAILED" else state["completedSteps"]
    if step_id not in target:
        target.append(step_id)


def validate_restored_plan(state: dict[str, Any]) -> None:
    planner = CompoundIntentPlanner()
    plan = planner.plan("当前1号库库存情况如何？它们的化验情况如何？")
    if plan is None:
        raise ValueError("registered orchestration recipe is unavailable")
    expected = new_orchestration_state(plan)
    immutable_fields = (
        "recipeId",
        "planVersion",
        "executionMode",
        "maxSteps",
        "maxToolCalls",
        "maxProductFanOut",
        "stepOrder",
        "planFingerprint",
    )
    for field in immutable_fields:
        if state.get(field) != expected.get(field):
            raise ValueError(f"restored orchestration {field} does not match registry")
    for step_id in expected["stepOrder"]:
        actual_step = (state.get("steps") or {}).get(step_id) or {}
        expected_step = expected["steps"][step_id]
        for field in ("stepId", "expert", "allowedTools", "plannedTools"):
            if actual_step.get(field) != expected_step.get(field):
                raise ValueError(f"restored orchestration step {field} changed")


def _step(state: dict[str, Any], step_id: str) -> dict[str, Any]:
    steps = state.get("steps")
    if not isinstance(steps, dict) or step_id not in steps:
        raise ValueError("orchestration step is not registered")
    return steps[step_id]


def _now() -> str:
    return datetime.now(timezone.utc).isoformat()
