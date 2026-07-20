from __future__ import annotations

from datetime import date
from datetime import datetime, timedelta, timezone
from copy import deepcopy
import hashlib
import json
import re
import secrets
import time
from collections.abc import Iterator
from typing import Any

from app.cancellation import current_cancellation_token
from app.agents import MAIN_AGENT, AgentHandoffRouter, ExpertBoundaryError
from app.business_time import BusinessClock
from app.execution import (
    AgentExecutionContext,
    bind_execution_context,
    current_execution_context,
)
from app.goal_contracts import (
    GOAL_CONTRACTS,
    FactEnvelopeV1,
    GoalCompletionEvaluator,
    build_fact_envelope,
    registered_goal_for_plan,
)
from app.graph.state import (
    InMemoryCheckpointer,
    PendingClarification,
    SelectedEntity,
    StateCheckpointer,
    WarehouseAgentState,
)
from app.schemas import (
    AgentError,
    BusinessCard,
    ChatRequest,
    ChatResponse,
    ExpertLoopDecisionV1,
    MainAgentDecisionV1,
    ResumeRequest,
    SafeAssayMetric,
    SafeAssayReport,
    SafeInventoryDistributionGroup,
    SafeInventoryDistributionResult,
    SafeInventoryLocation,
    SafeInventoryResult,
    SafePalletTaskRecord,
    SafePalletTaskResult,
    SafeWarehouseResult,
    UserOption,
)
from app.model import (
    ExpertLoopRequest,
    MainAgentRouteRequest,
    ModelClient,
    ModelDecisionError,
    reset_model_decision_diagnostics,
    take_model_decision_diagnostics,
)
from app.tool_arguments import ToolArgumentBuilder
from app.tools.client import ALLOWED_TOOLS, AgentToolClient, ToolGatewayError
from app.orchestration import (
    CompoundIntentPlanner,
    MAX_ASSAY_PRODUCT_FANOUT,
    mark_step_finished,
    mark_step_started,
    validate_restored_plan,
)
from app.observability import MetricsRegistry
from app.policies import FastCompletionPolicy, NextActionPolicy, SafeFallbackPolicy
from app.progress import report_business_progress, report_tool_progress


class OrchestrationBudgetExceeded(RuntimeError):
    pass


class WarehouseAgentRuntime:
    """Minimal LangGraph-compatible runtime for M1.3R."""

    RESUME_TOKEN_TTL = timedelta(minutes=10)
    ASSAY_METRICS = (
        ("color_value", "colorValue", "色值"),
        ("reducing_sugar", "reducingSugar", "还原糖分"),
        ("dry_weight_loss", "dryWeight", "干燥失重"),
        ("conductivity_ash", "conductivityAsh", "电导灰分"),
        ("sucrose", "sucrose", "蔗糖分"),
        ("insoluble_impurity", "insolubleImpurity", "不溶于水杂质"),
        ("ph", "phValue", "pH"),
    )

    def __init__(
        self,
        tool_client: AgentToolClient,
        checkpointer: StateCheckpointer | None = None,
        argument_builder: ToolArgumentBuilder | None = None,
        metrics: MetricsRegistry | None = None,
        model_client: ModelClient | None = None,
        planning_mode: str = "deterministic",
        llm_allowed_experts: tuple[str, ...] = (),
        llm_max_tool_calls: int = 3,
        llm_max_tool_retries: int = 1,
        agent_router: AgentHandoffRouter | None = None,
        fast_completion_policy: FastCompletionPolicy | None = None,
        safe_fallback_policy: SafeFallbackPolicy | None = None,
        next_action_policy: NextActionPolicy | None = None,
        business_clock: BusinessClock | None = None,
    ) -> None:
        self.tool_client = tool_client
        self.checkpointer = checkpointer or InMemoryCheckpointer()
        inherited_clock = getattr(argument_builder, "business_clock", None)
        self.business_clock = business_clock or inherited_clock or BusinessClock()
        self.argument_builder = argument_builder or ToolArgumentBuilder(
            business_clock=self.business_clock
        )
        self.metrics = metrics or MetricsRegistry()
        self.model_client = model_client
        self.planning_mode = planning_mode if planning_mode in {"deterministic", "llm"} else "deterministic"
        self.agent_router = agent_router or AgentHandoffRouter()
        known_experts = set(self.agent_router.profiles) - {MAIN_AGENT}
        self.llm_allowed_experts = frozenset(llm_allowed_experts) & known_experts
        self.llm_max_tool_calls = min(3, max(1, llm_max_tool_calls))
        self.llm_max_tool_retries = min(1, max(0, llm_max_tool_retries))
        self.fast_completion_policy = fast_completion_policy or FastCompletionPolicy.default()
        self.safe_fallback_policy = safe_fallback_policy or SafeFallbackPolicy.default()
        self.next_action_policy = next_action_policy or NextActionPolicy()

    def chat(self, request: ChatRequest) -> ChatResponse:
        started = time.monotonic()
        try:
            with self.checkpointer.session(request.agentSessionId) as state:
                return self._chat_locked(request, state)
        finally:
            self.metrics.observe(
                "agent_turn_duration",
                time.monotonic() - started,
                mode=self.planning_mode,
                operation="chat",
            )

    def _chat_locked(self, request: ChatRequest, state: WarehouseAgentState) -> ChatResponse:
        text = request.message.content.strip()
        self._raise_if_cancelled()
        state.messages.append({"role": "user", "content": text})

        try:
            response = self._handle_message(request, state, text)
        except ToolGatewayError as exc:
            self.metrics.increment("gateway_rejection_total", code=exc.code)
            response = self._tool_error_response(request.agentSessionId, exc)

        self._raise_if_cancelled()
        state.messages.append({"role": "assistant", "content": response.answer})
        return response

    def stream_answer_deltas(self, answer: str) -> Iterator[str]:
        yield from self.argument_builder.stream_answer_deltas(answer)

    def clear_session(self, agent_session_id: str) -> None:
        self.checkpointer.clear(agent_session_id)

    def resume(self, request: ResumeRequest) -> ChatResponse:
        started = time.monotonic()
        try:
            with self.checkpointer.session(request.agentSessionId) as state:
                context = self._pending_execution_context(state.pending_clarification)
                with bind_execution_context(context):
                    return self._resume_locked(request, state)
        finally:
            self.metrics.observe(
                "agent_turn_duration",
                time.monotonic() - started,
                mode=self.planning_mode,
                operation="resume",
            )

    def _resume_locked(self, request: ResumeRequest, state: WarehouseAgentState) -> ChatResponse:
        selection = request.event.selection
        interrupt_id = request.event.interruptId
        client_request_id = request.event.clientRequestId or request.client.requestId
        self._raise_if_cancelled()
        state.messages.append(
            {
                "role": "user",
                "event": "candidate_selected",
                "interruptId": interrupt_id,
                "optionId": selection.optionId,
            }
        )

        cached = self._cached_resume_response(state, interrupt_id, client_request_id)
        if cached is not None:
            return cached

        pending = state.pending_clarification
        if pending is None:
            response = self._terminal_interrupt_response(request.agentSessionId, state, interrupt_id)
            self._cache_resume_response(state, interrupt_id, client_request_id, response)
            self._raise_if_cancelled()
            self.checkpointer.save(request.agentSessionId, state)
            return response

        if interrupt_id and interrupt_id != pending.interrupt_id:
            response = self._terminal_interrupt_response(request.agentSessionId, state, interrupt_id)
            self._cache_resume_response(state, interrupt_id, client_request_id, response)
            self._raise_if_cancelled()
            self.checkpointer.save(request.agentSessionId, state)
            return response

        if pending.status != "PENDING":
            response = self._status_response(request.agentSessionId, pending.status)
            self._cache_resume_response(state, pending.interrupt_id, client_request_id, response)
            self._raise_if_cancelled()
            self.checkpointer.save(request.agentSessionId, state)
            return response

        if self._is_expired(pending):
            self.metrics.increment("hitl_expired_total")
            pending.status = "EXPIRED"
            state.interrupt_status[pending.interrupt_id] = pending.status
            state.pending_clarification = None
            response = ChatResponse(
                agentSessionId=request.agentSessionId,
                answer="这个确认已过期，请重新发起。",
            )
            self._cache_resume_response(state, pending.interrupt_id, client_request_id, response)
            self._raise_if_cancelled()
            self.checkpointer.save(request.agentSessionId, state)
            return response

        if not self._resume_token_matches(pending, request.resumeToken) or not self._user_matches(pending, request):
            response = ChatResponse(
                agentSessionId=request.agentSessionId,
                answer="这个选择已失效，请重新发起。",
            )
            self._cache_resume_response(state, pending.interrupt_id, client_request_id, response)
            self._raise_if_cancelled()
            self.checkpointer.save(request.agentSessionId, state)
            return response

        if request.event.action == "CANCEL":
            pending.status = "CANCELLED"
            state.interrupt_status[pending.interrupt_id] = pending.status
            state.pending_clarification = None
            response = ChatResponse(agentSessionId=request.agentSessionId, answer="这个任务已取消。")
            self._cache_resume_response(state, pending.interrupt_id, client_request_id, response)
            self._raise_if_cancelled()
            state.messages.append({"role": "assistant", "content": response.answer})
            self.checkpointer.save(request.agentSessionId, state)
            return response

        option = self._find_pending_option(pending, selection.optionId, selection.displayLabel)
        if option is None:
            response = ChatResponse(
                agentSessionId=request.agentSessionId,
                answer="没有找到你选择的候选项，请重新选择。",
                needsUserSelection=True,
                cards=[self._clarification_card(pending)],
            )
            self._raise_if_cancelled()
            self.checkpointer.save(request.agentSessionId, state)
            return response

        if option.get("supported") is False:
            response = ChatResponse(
                agentSessionId=request.agentSessionId,
                answer=f"{option.get('displayLabel', '该选项')}当前暂不支持直接查询。",
                suggestions=["请选择一个具体产品或库位。"],
            )
            self._raise_if_cancelled()
            self.checkpointer.save(request.agentSessionId, state)
            return response

        internal = option.get("_internal", {})
        intent = pending.intent
        pending.status = "RESUMED"
        self.metrics.increment("hitl_resumed_total")
        state.interrupt_status[pending.interrupt_id] = pending.status
        if pending.kind == "product":
            option_type = str(option.get("optionType") or "SINGLE_PRODUCT")
            product_id = self._int_value(internal, "productId")
            if option_type == "SINGLE_PRODUCT" and product_id is None:
                response = ChatResponse(
                    agentSessionId=request.agentSessionId,
                    answer="该产品候选缺少可校验的内部映射，请重新查询。",
                    needsUserSelection=True,
                )
                self._raise_if_cancelled()
                self.checkpointer.save(request.agentSessionId, state)
                return response
            self._raise_if_cancelled()
            state.selected_product = SelectedEntity(
                internal_id=product_id,
                display_label=self._safe_display_label(str(option.get("displayLabel") or "所选产品")),
                source="user_selection",
                metadata={
                    "scopeType": option_type,
                    "productName": internal.get("productName"),
                    "productType": internal.get("productType"),
                },
                entity_type="PRODUCT",
                entity_ref="CURRENT_PRODUCT",
                canonical_name=self._safe_text(internal.get("productName")),
                resolved_by="USER_SELECTION",
                resolved_at=datetime.now(timezone.utc),
            )
            state.pending_clarification = None
            if intent == "llm_tool_loop":
                response = self._resume_llm_tool_loop(request, state, pending, "PRODUCT")
            else:
                response = self._continue_product_intent(
                    request.agentSessionId,
                    state,
                    intent,
                    request.client.traceId,
                    request.client.requestId,
                    request.messageId,
                )
        elif pending.kind == "warehouse":
            warehouse_id = self._int_value(internal, "warehouseId")
            if warehouse_id is None:
                response = ChatResponse(
                    agentSessionId=request.agentSessionId,
                    answer="该库位候选缺少可校验的内部映射，请重新查询。",
                    needsUserSelection=True,
                )
                self._raise_if_cancelled()
                self.checkpointer.save(request.agentSessionId, state)
                return response
            self._raise_if_cancelled()
            state.selected_warehouse = SelectedEntity(
                internal_id=warehouse_id,
                display_label=self._warehouse_display_name(str(option.get("displayLabel") or "所选库位")),
                source="user_selection",
                entity_type="WAREHOUSE",
                entity_ref="CURRENT_WAREHOUSE",
                canonical_name=self._warehouse_display_name(str(option.get("displayLabel") or "所选库位")),
                resolved_by="USER_SELECTION",
                resolved_at=datetime.now(timezone.utc),
            )
            state.pending_clarification = None
            if intent == "llm_tool_loop":
                response = self._resume_llm_tool_loop(request, state, pending, "WAREHOUSE")
            elif intent == "compound_inventory_assay":
                response = self._answer_compound_inventory_assay_from_selected(
                    agent_session_id=request.agentSessionId,
                    state=state,
                    trace_id=request.client.traceId,
                    request_id=request.client.requestId,
                    message_id=None,
                    continuation=pending.continuation,
                )
            elif intent == "inventory_distribution":
                distribution_args = self.argument_builder.distribution_arguments_for_state(
                    {
                        "productScope": {"type": "ALL"},
                        "warehouseScope": {
                            "type": "SINGLE_WAREHOUSE",
                            "warehouseId": warehouse_id,
                        },
                        "groupBy": "product",
                        "limit": 20,
                    },
                    state,
                )
                response = self._answer_inventory_distribution(
                    request.agentSessionId,
                    state,
                    request.client.traceId,
                    request.client.requestId,
                    request.messageId,
                    distribution_args,
                )
            elif intent == "products_without_recent_assay":
                user_message = self._latest_user_message(state)
                arguments = self.argument_builder.products_without_recent_assay_arguments_for_state(
                    {
                        "productScope": {"type": "ALL"},
                        "warehouseScope": {"type": "SINGLE_WAREHOUSE", "warehouseId": warehouse_id},
                    },
                    state,
                    user_message,
                )
                response = self._answer_products_without_recent_assay(
                    request.agentSessionId,
                    state,
                    request.client.traceId,
                    request.client.requestId,
                    request.messageId,
                    arguments,
                )
            elif intent == "warehouse_recent_operations":
                response = self._answer_warehouse_recent_operations(
                    request, state, {"warehouseId": warehouse_id, "eventTypes": [], "limit": 20}
                )
            elif intent == "warehouse_mixed_storage_facts":
                response = self._answer_warehouse_mixed_storage_facts(
                    request, state, {"warehouseId": warehouse_id, "factType": "ANY", "limit": 20}
                )
            else:
                response = self._answer_warehouse_status(
                    request.agentSessionId,
                    state,
                    request.client.traceId,
                    request.client.requestId,
                    request.messageId,
                )
        elif pending.kind == "production_order":
            order_ref = self._safe_text(internal.get("orderRef"))
            if not order_ref or not order_ref.startswith("aer_"):
                response = ChatResponse(
                    agentSessionId=request.agentSessionId,
                    answer="该生产订单候选缺少有效的受控引用，请重新查询。",
                    needsUserSelection=True,
                )
                self._raise_if_cancelled()
                self.checkpointer.save(request.agentSessionId, state)
                return response
            state.selected_production_order = SelectedEntity(
                internal_id=None,
                display_label=self._safe_display_label(str(option.get("displayLabel") or "所选生产订单")),
                source="user_selection",
                metadata={"orderRef": order_ref},
                entity_type="PRODUCTION_ORDER",
                entity_ref="CURRENT_PRODUCTION_ORDER",
                canonical_name=self._safe_display_label(str(option.get("displayLabel") or "所选生产订单")),
                resolved_by="USER_SELECTION",
                resolved_at=datetime.now(timezone.utc),
            )
            state.pending_clarification = None
            response = self._answer_selected_production_order(
                request,
                state,
                pending.intent,
                order_ref,
            )
        else:
            response = ChatResponse(agentSessionId=request.agentSessionId, answer="已记录你的选择。")

        self._attach_goal_completion(response, state)
        self._cache_resume_response(state, pending.interrupt_id, client_request_id, response)
        self._raise_if_cancelled()
        state.messages.append({"role": "assistant", "content": response.answer})
        self.checkpointer.save(request.agentSessionId, state)
        return response

    def _handle_message(self, request: ChatRequest, state: WarehouseAgentState, text: str) -> ChatResponse:
        task_detail = self._task_detail_followup_response(request.agentSessionId, state, text)
        if task_detail is not None:
            self._attach_goal_completion(task_detail, state)
            return task_detail
        task_filter = self._task_filter_followup_response(request, state, text)
        if task_filter is not None:
            self._attach_goal_completion(task_filter, state)
            return task_filter
        if self.planning_mode == "llm":
            self._begin_registered_goal(state, None)
            return self._handle_message_llm(request, state, text)
        try:
            plan = self.argument_builder.plan(user_message=text, state=state)
        except ExpertBoundaryError:
            return ChatResponse(
                agentSessionId=request.agentSessionId,
                answer="当前请求无法在已分配的专家权限内安全完成，请换一种更明确的只读查询方式。",
                error=AgentError(
                    code="EXPERT_TOOL_NOT_ALLOWED",
                    message="当前请求无法在已分配的专家权限内安全完成。",
                    retryable=False,
                ),
            )
        goal_type = registered_goal_for_plan(
            tool_name=plan.toolName,
            arguments=plan.arguments,
            intent=plan.intent,
            response_mode=plan.responseMode,
        )
        self._begin_registered_goal(state, goal_type)
        handoff = plan.routeSnapshot.get("agent_handoff", {}) if plan.routeSnapshot else {}
        target_agent = str(handoff.get("target_agent") or "main_agent")
        run_id = f"run_{secrets.token_hex(8)}"
        handoff_id = f"handoff_{secrets.token_hex(8)}"
        state.active_agent = target_agent
        state.last_agent_handoff = dict(handoff)
        state.active_run = {
            "traceId": request.client.traceId,
            "sessionId": request.agentSessionId,
            "requestId": request.client.requestId,
            "runId": run_id,
            "handoffId": handoff_id,
            "planId": ((plan.routeSnapshot or {}).get("orchestrationState") or {}).get("planId"),
            "startedAt": datetime.now(timezone.utc).isoformat(),
        }
        self.metrics.increment(
            "router_domain_total",
            domain=(plan.routeSnapshot or {}).get("business_domain") or "unknown",
        )
        state.messages.append(
            {
                "role": "planner",
                "action": plan.action,
                "toolName": plan.toolName,
                "intent": plan.intent,
                "responseMode": plan.responseMode,
                "intentRouter": plan.routeSnapshot,
            }
        )
        execution_context = self._execution_context_from_handoff(handoff)
        with bind_execution_context(execution_context):
            response = self._execute_plan(request, state, plan)
        completion = self._attach_goal_completion(response, state)
        if completion is not None and plan.routeSnapshot is not None:
            plan.routeSnapshot["goalCompletion"] = completion
        response.reviewTrace = plan.routeSnapshot
        if request.client.debug and plan.routeSnapshot:
            response.debug = {"intentRouter": plan.routeSnapshot}
        return response

    def _begin_registered_goal(self, state: WarehouseAgentState, goal_type: str | None) -> None:
        state.active_goal_type = goal_type
        state.fact_envelopes = []
        state.last_goal_completion = None

    def _goal_entity_contexts(self, state: WarehouseAgentState) -> dict[str, SelectedEntity]:
        contexts: dict[str, SelectedEntity] = {}
        if state.selected_product is not None:
            state.selected_product.entity_type = state.selected_product.entity_type or "PRODUCT"
            state.selected_product.entity_ref = state.selected_product.entity_ref or "CURRENT_PRODUCT"
            if state.selected_product.canonical_name is None:
                state.selected_product.canonical_name = self._safe_text(
                    state.selected_product.metadata.get("productName")
                )
            contexts["PRODUCT"] = state.selected_product
        if state.selected_warehouse is not None:
            state.selected_warehouse.entity_type = state.selected_warehouse.entity_type or "WAREHOUSE"
            state.selected_warehouse.entity_ref = state.selected_warehouse.entity_ref or "CURRENT_WAREHOUSE"
            state.selected_warehouse.canonical_name = (
                state.selected_warehouse.canonical_name
                or self._warehouse_display_name(state.selected_warehouse.display_label)
            )
            contexts["WAREHOUSE"] = state.selected_warehouse
        return contexts

    def _record_registered_goal_fact(
        self,
        *,
        state: WarehouseAgentState,
        tool_name: str,
        arguments: dict[str, Any] | None,
        safe_data: dict[str, Any],
    ) -> None:
        goal_type = state.active_goal_type
        contract = GOAL_CONTRACTS.get(goal_type) if goal_type else None
        if contract is None or tool_name not in contract.allowedTools:
            goal_type = registered_goal_for_plan(
                tool_name=tool_name,
                arguments=arguments,
                intent=None,
            )
            contract = GOAL_CONTRACTS.get(goal_type) if goal_type else None
        if goal_type is None or contract is None or tool_name not in contract.allowedTools:
            return
        if state.active_goal_type != goal_type:
            state.active_goal_type = goal_type
            state.fact_envelopes = []
            state.last_goal_completion = None
        envelope = build_fact_envelope(
            goal_type=goal_type,
            tool_name=tool_name,
            arguments=arguments,
            safe_data=safe_data,
            entity_contexts=self._goal_entity_contexts(state),
        )
        state.fact_envelopes.append(envelope.model_dump(mode="json"))
        state.fact_envelopes[:] = state.fact_envelopes[-20:]

    def _evaluate_registered_goal(
        self,
        state: WarehouseAgentState,
        *,
        needs_clarification: bool = False,
        unsupported: bool = False,
    ) -> dict[str, Any] | None:
        goal_type = state.active_goal_type
        if goal_type not in GOAL_CONTRACTS:
            return None
        facts: list[FactEnvelopeV1] = []
        for value in state.fact_envelopes:
            try:
                facts.append(FactEnvelopeV1.model_validate(value))
            except ValueError:
                continue
        completion = GoalCompletionEvaluator().evaluate(
            goal_type=goal_type,
            entity_contexts=self._goal_entity_contexts(state),
            facts=facts,
            needs_clarification=needs_clarification,
            unsupported=unsupported,
        )
        snapshot = completion.model_dump(mode="json")
        state.last_goal_completion = snapshot
        return snapshot

    def _attach_goal_completion(
        self,
        response: ChatResponse,
        state: WarehouseAgentState,
    ) -> dict[str, Any] | None:
        unsupported_codes = {"UNSUPPORTED_SCOPE", "UNSUPPORTED_CAPABILITY"}
        completion = self._evaluate_registered_goal(
            state,
            needs_clarification=response.needsUserSelection,
            unsupported=bool(response.error and response.error.code in unsupported_codes),
        )
        if completion is None:
            return None
        trace = dict(response.reviewTrace or {})
        trace["goalCompletion"] = completion
        response.reviewTrace = trace
        return completion

    def _handle_message_llm(
        self,
        request: ChatRequest,
        state: WarehouseAgentState,
        text: str,
    ) -> ChatResponse:
        if self.model_client is None or not self.llm_allowed_experts:
            return self._llm_configuration_error(request.agentSessionId)
        trace: dict[str, Any] = {
            "planningMode": "llm",
            "executionInfluence": True,
        }
        fast_followup_expert = self._llm_context_followup_expert(state, text)
        decision: MainAgentDecisionV1 | None = None
        if fast_followup_expert is not None:
            decision = MainAgentDecisionV1(
                action="DELEGATE",
                expertAgent=fast_followup_expert,
                semanticReason="FOLLOWUP_QUERY",
                confidence=1.0,
            )
            trace["mainRouteOptimization"] = {
                "status": "REUSED_ACTIVE_EXPERT",
                "expertAgent": fast_followup_expert,
                "reason": "BOUNDED_CONTEXT_FOLLOWUP",
            }
            trace["mainDecision"] = self._safe_main_decision_trace(decision)
        registered_recipe_plan: Any | None = None
        for route_attempt in (range(2) if decision is None else ()):
            report_business_progress("routing")
            selected_context = self._llm_selected_context(state)
            registered_recipes = [CompoundIntentPlanner.WAREHOUSE_INVENTORY_ASSAY]
            if route_attempt == 1:
                registered_recipes = []
                selected_context["RUNTIME_ROUTE_CORRECTION"] = {
                    "rejectedAction": "RUN_REGISTERED_RECIPE",
                    "reason": "CURRENT_MESSAGE_DID_NOT_MATCH_REGISTERED_RECIPE",
                    "allowedAlternatives": ["DELEGATE", "ASK_CLARIFICATION", "UNSUPPORTED"],
                }
            reset_model_decision_diagnostics()
            try:
                decision = self.model_client.route_main_agent(
                    MainAgentRouteRequest(
                        userMessage=text,
                        messages=self._llm_safe_messages(state),
                        selectedContext=selected_context,
                        availableExperts=self._llm_available_experts(),
                        registeredRecipes=registered_recipes,
                    )
                )
            except ModelDecisionError as exc:
                self._append_llm_model_diagnostics(trace)
                return self._llm_model_failure(request.agentSessionId, trace, exc)
            self._append_llm_model_diagnostics(trace)
            if decision is None:
                return self._llm_model_error(
                    request.agentSessionId,
                    "主模型未返回可校验的路由决策。",
                    trace,
                )
            trace["mainDecision" if route_attempt == 0 else "mainDecisionCorrection"] = (
                self._safe_main_decision_trace(decision)
            )
            if decision.action != "RUN_REGISTERED_RECIPE":
                break
            matched_recipe = CompoundIntentPlanner(self.agent_router).plan(text)
            if matched_recipe is not None and matched_recipe.recipe == decision.recipeId:
                registered_recipe_plan = self.argument_builder.plan(user_message=text, state=state)
                break
            trace["recipeGuard"] = {
                "status": "REJECTED",
                "reason": "CURRENT_MESSAGE_DID_NOT_MATCH_REGISTERED_RECIPE",
                "correctionAttempted": route_attempt == 0,
            }
            if route_attempt == 1:
                return self._llm_boundary_rejection(
                    request.agentSessionId,
                    trace,
                    "模型连续提出了与当前问题不匹配的跨域配方。",
                )
        if decision is None:
            return self._llm_model_error(request.agentSessionId, "主模型没有生成路由决策。", trace)
        if decision.action == "DIRECT_ANSWER":
            if decision.semanticReason not in {"SMALLTALK", "CAPABILITY", "SECURITY_REFUSAL"}:
                recovery_expert = self._llm_business_recovery_expert(state, text)
                if recovery_expert is None:
                    return self._llm_boundary_rejection(
                        request.agentSessionId,
                        trace,
                        "实时业务问题不能由主模型直接回答，且当前无法安全确定应委派的业务专家。",
                    )
                trace["mainRouteGuard"] = {
                    "status": "RECOVERED_AS_DELEGATE",
                    "rejectedAction": "DIRECT_ANSWER",
                    "expertAgent": recovery_expert,
                    "reason": "REALTIME_BUSINESS_FACT_REQUIRES_EXPERT",
                }
                decision = MainAgentDecisionV1(
                    action="DELEGATE",
                    expertAgent=recovery_expert,
                    semanticReason=decision.semanticReason,
                    confidence=decision.confidence,
                )
            else:
                response = ChatResponse(
                    agentSessionId=request.agentSessionId,
                    answer=self._sanitize_llm_answer(decision.answer or ""),
                )
                response.reviewTrace = trace
                return response
        if decision.action == "UNSUPPORTED":
            response = ChatResponse(
                agentSessionId=request.agentSessionId,
                answer=self._sanitize_llm_answer(decision.answer or "当前能力暂不支持这个请求。"),
            )
            response.reviewTrace = trace
            return response
        if decision.action == "ASK_CLARIFICATION":
            response = ChatResponse(
                agentSessionId=request.agentSessionId,
                answer=self._sanitize_llm_answer(decision.clarificationPrompt or "请补充查询条件。"),
                needsUserSelection=True,
            )
            response.reviewTrace = trace
            return response
        if decision.action == "RUN_REGISTERED_RECIPE":
            if registered_recipe_plan is None:
                return self._llm_boundary_rejection(
                    request.agentSessionId,
                    trace,
                    "模型提出的跨域请求未通过现有登记配方匹配。",
                )
            return self._execute_llm_registered_recipe(
                request,
                state,
                decision,
                trace,
                registered_recipe_plan,
            )
        if decision.action != "DELEGATE" or decision.expertAgent is None:
            return self._llm_boundary_rejection(request.agentSessionId, trace, "主模型动作不在允许范围内。")
        if decision.expertAgent not in self.llm_allowed_experts:
            return self._llm_boundary_rejection(
                request.agentSessionId,
                trace,
                "该专家尚未进入本地 LLM Tool Loop 实验白名单。",
            )
        handoff = self.agent_router.handoff_for_agent(decision.expertAgent, mode="llm_delegate")
        state.active_agent = handoff.target_agent
        state.last_agent_handoff = handoff.to_snapshot()
        state.active_run = {
            "traceId": request.client.traceId,
            "sessionId": request.agentSessionId,
            "requestId": request.client.requestId,
            "runId": f"run_{secrets.token_hex(8)}",
            "handoffId": f"handoff_{secrets.token_hex(8)}",
            "startedAt": datetime.now(timezone.utc).isoformat(),
            "planningMode": "llm",
        }
        execution_context = AgentExecutionContext(
            agent_name=handoff.target_agent,
            allowed_tools=frozenset(handoff.allowed_tools),
            business_domain=handoff.business_domain,
            handoff_mode="llm_delegate",
            handoff_id=str(state.active_run["handoffId"]),
        )
        with bind_execution_context(execution_context):
            response = self._run_llm_expert_loop(
                request=request,
                state=state,
                user_message=text,
                expert_agent=handoff.target_agent,
                observations=[],
                tool_call_count=0,
                retry_count=0,
                trace=trace,
            )
        return response

    def _execute_llm_registered_recipe(
        self,
        request: ChatRequest,
        state: WarehouseAgentState,
        decision: MainAgentDecisionV1,
        trace: dict[str, Any],
        deterministic_plan: Any,
    ) -> ChatResponse:
        if decision.recipeId != CompoundIntentPlanner.WAREHOUSE_INVENTORY_ASSAY:
            return self._llm_boundary_rejection(request.agentSessionId, trace, "跨域配方未登记。")
        recipe = ((deterministic_plan.routeSnapshot or {}).get("orchestrationState") or {}).get("recipeId")
        if deterministic_plan.action != "orchestrate" or recipe != decision.recipeId:
            return self._llm_boundary_rejection(
                request.agentSessionId,
                trace,
                "模型提出的跨域请求未通过现有登记配方匹配。",
            )
        handoff = (deterministic_plan.routeSnapshot or {}).get("agent_handoff") or {}
        state.active_agent = MAIN_AGENT
        state.last_agent_handoff = dict(handoff)
        with bind_execution_context(self._execution_context_from_handoff(handoff)):
            response = self._execute_compound_plan(request, state, deterministic_plan)
        trace["registeredRecipe"] = decision.recipeId
        response.reviewTrace = trace
        if request.client.debug:
            response.debug = {"llmToolLoop": trace}
        return response

    def _run_llm_expert_loop(
        self,
        *,
        request: ChatRequest | ResumeRequest,
        state: WarehouseAgentState,
        user_message: str,
        expert_agent: str,
        observations: list[dict[str, Any]],
        tool_call_count: int,
        retry_count: int,
        trace: dict[str, Any],
    ) -> ChatResponse:
        profile = self.agent_router.profile(expert_agent)
        handoff = self.agent_router.handoff_for_agent(expert_agent, mode="llm_delegate")
        visible_schemas = self.argument_builder.llm_visible_tool_schemas(handoff)
        trace.setdefault("expertLoop", {"expertAgent": expert_agent, "events": []})
        loop_trace = trace["expertLoop"]
        planning_rejections = 0
        latest_cards: list[BusinessCard] = []

        while True:
            self._raise_if_cancelled()
            report_business_progress("expert_planning")
            reset_model_decision_diagnostics()
            try:
                decision = self.model_client.decide_expert_action(
                    ExpertLoopRequest(
                        userMessage=user_message,
                        messages=self._llm_safe_messages(state),
                        expertAgent=expert_agent,
                        expertInstructions=list(profile.instructions),
                        selectedContext=self._llm_selected_context(state),
                        toolSchemas=visible_schemas,
                        observations=self._llm_model_observations(observations),
                        toolCallCount=tool_call_count,
                        maxToolCalls=self.llm_max_tool_calls,
                    )
                ) if self.model_client is not None else None
            except ModelDecisionError as exc:
                self._append_llm_model_diagnostics(trace)
                fallback = self._llm_safe_expert_failure_fallback(
                    request=request,
                    state=state,
                    user_message=user_message,
                    expert_agent=expert_agent,
                    observations=observations,
                    tool_call_count=tool_call_count,
                    trace=trace,
                    error=exc,
                )
                if fallback is not None:
                    return self._finish_llm_response(fallback, trace, request)
                response = self._llm_model_failure(request.agentSessionId, trace, exc)
                return self._finish_llm_response(response, trace, request)
            self._append_llm_model_diagnostics(trace)
            if decision is None:
                response = self._llm_model_error(
                    request.agentSessionId,
                    "专家模型未返回可校验的动作。",
                    trace,
                )
                return self._finish_llm_response(response, trace, request)
            loop_trace["events"].append(self._safe_expert_decision_trace(decision))

            if decision.action == "ASK_CLARIFICATION":
                response = ChatResponse(
                    agentSessionId=request.agentSessionId,
                    answer=self._sanitize_llm_answer(decision.clarificationPrompt or "请补充查询条件。"),
                    needsUserSelection=True,
                )
                return self._finish_llm_response(response, trace, request)
            if decision.action == "UNSUPPORTED":
                response = ChatResponse(
                    agentSessionId=request.agentSessionId,
                    answer=self._sanitize_llm_answer(decision.answer or "当前专家无法完成这个请求。"),
                )
                return self._finish_llm_response(response, trace, request)
            if decision.action in {"FINAL_ANSWER", "PARTIAL_ANSWER"}:
                validation_error = self._validate_llm_completion(decision, observations, state)
                if validation_error:
                    return self._finish_llm_response(
                        self._llm_boundary_rejection(request.agentSessionId, trace, validation_error),
                        trace,
                        request,
                    )
                answer = self._sanitize_llm_answer(decision.answer or "")
                if decision.action == "PARTIAL_ANSWER" and not answer.startswith("部分"):
                    answer = "部分完成：" + answer
                completion = self._evaluate_registered_goal(state)
                if completion is not None:
                    trace["goalCompletion"] = completion
                response = ChatResponse(
                    agentSessionId=request.agentSessionId,
                    answer=answer,
                    cards=latest_cards,
                )
                return self._finish_llm_response(response, trace, request)
            if decision.action != "CALL_TOOL" or decision.toolName is None:
                return self._finish_llm_response(
                    self._llm_boundary_rejection(request.agentSessionId, trace, "专家模型动作不在允许范围内。"),
                    trace,
                    request,
                )
            if tool_call_count >= self.llm_max_tool_calls:
                return self._finish_llm_response(
                    self._llm_boundary_rejection(request.agentSessionId, trace, "本轮只读工具调用已达到上限。"),
                    trace,
                    request,
                )
            try:
                self.agent_router.authorize_tool(expert_agent, decision.toolName)
                arguments = self.argument_builder.validate_llm_arguments(
                    tool_name=decision.toolName,
                    arguments=decision.arguments,
                    state=state,
                    user_message=user_message,
                )
            except (ExpertBoundaryError, TypeError, ValueError):
                planning_rejections += 1
                if planning_rejections > 1:
                    return self._finish_llm_response(
                        self._llm_boundary_rejection(
                            request.agentSessionId,
                            trace,
                            "模型连续提出了无法通过工具边界或参数校验的动作。",
                        ),
                        trace,
                        request,
                    )
                observations.append(
                    {
                        "observationId": f"obs_{len(observations) + 1}",
                        "status": "PLAN_REJECTED",
                        "message": "Runtime 拒绝了工具或参数；请使用当前专家 schema 和受控实体引用修正一次。",
                    }
                )
                continue

            call_signature = self._llm_call_signature(decision.toolName, arguments)
            previous_error = self._latest_unresolved_error(observations, call_signature)
            if previous_error is not None:
                if not previous_error.get("retryable") or retry_count >= self.llm_max_tool_retries:
                    return self._finish_llm_response(
                        self._llm_boundary_rejection(request.agentSessionId, trace, "该工具错误不允许继续重试。"),
                        trace,
                        request,
                    )
                retry_count += 1
            tool_call_count += 1
            try:
                result = self._call_tool_values(
                    agent_session_id=request.agentSessionId,
                    tool_name=decision.toolName,
                    arguments=arguments,
                    trace_id=request.client.traceId,
                    request_id=request.client.requestId,
                    message_id=request.messageId,
                )
                self._raise_if_tool_error_payload(result)
            except ToolGatewayError as exc:
                if exc.code in {"UPSTREAM_UNAUTHORIZED", "SERVICE_AUTHENTICATION_FAILED"}:
                    status = "AUTHENTICATION_FAILED"
                elif exc.code in {"UPSTREAM_PERMISSION_DENIED", "AGENT_SCOPE_DENIED"}:
                    status = "PERMISSION_DENIED"
                else:
                    status = "TOOL_ERROR"
                observation = {
                    "observationId": f"obs_{len(observations) + 1}",
                    "status": status,
                    "tool": decision.toolName,
                    "callSignature": call_signature,
                    "retryable": bool(exc.retryable),
                    "message": self._safe_tool_error_message(exc.code),
                    "resolved": False,
                }
                observations.append(observation)
                loop_trace["events"].append(
                    {"action": "TOOL_RESULT", "toolName": decision.toolName, "status": status}
                )
                if status == "PERMISSION_DENIED":
                    response = ChatResponse(
                        agentSessionId=request.agentSessionId,
                        answer="当前用户没有执行该只读查询的权限。",
                        error=AgentError(code="PERMISSION_DENIED", message="当前查询被权限边界拒绝。", retryable=False),
                    )
                    return self._finish_llm_response(response, trace, request)
                if status == "AUTHENTICATION_FAILED":
                    response = ChatResponse(
                        agentSessionId=request.agentSessionId,
                        answer="当前查询链路认证失败，请重新登录；如果重登后仍失败，请检查本地 Agent 服务配置。",
                        error=AgentError(
                            code="UPSTREAM_AUTHENTICATION_FAILED",
                            message="当前查询链路认证失败。",
                            retryable=False,
                        ),
                    )
                    return self._finish_llm_response(response, trace, request)
                continue

            if previous_error is not None:
                previous_error["resolved"] = True
            report_business_progress("analyzing")
            pending = self._llm_resolver_interrupt(
                request=request,
                state=state,
                user_message=user_message,
                expert_agent=expert_agent,
                tool_name=decision.toolName,
                result=result,
                observations=observations,
                tool_call_count=tool_call_count,
                retry_count=retry_count,
                trace=trace,
            )
            if pending is not None:
                return self._finish_llm_response(pending, trace, request)
            observation, cards = self._llm_success_observation(
                state=state,
                tool_name=decision.toolName,
                arguments=arguments,
                result=result,
                observation_id=f"obs_{len(observations) + 1}",
                call_signature=call_signature,
            )
            observations.append(observation)
            latest_cards = cards or latest_cards
            self._record_tool_message(state, decision.toolName, observation)
            loop_trace["events"].append(
                {"action": "TOOL_RESULT", "toolName": decision.toolName, "status": observation["status"]}
            )
            fast_completion = self._llm_fast_complete_after_tool(
                request=request,
                state=state,
                user_message=user_message,
                tool_name=decision.toolName,
                observation=observation,
                cards=latest_cards,
                trace=trace,
            )
            if fast_completion is not None:
                return self._finish_llm_response(fast_completion, trace, request)

    def _llm_fast_complete_after_tool(
        self,
        *,
        request: ChatRequest | ResumeRequest,
        state: WarehouseAgentState,
        user_message: str,
        tool_name: str,
        observation: dict[str, Any],
        cards: list[BusinessCard],
        trace: dict[str, Any],
    ) -> ChatResponse | None:
        if not self.fast_completion_policy.allows(tool_name, user_message):
            return None
        data = observation.get("data")
        if not isinstance(data, dict):
            return None

        answer: str | None = None
        suggestions: list[str] = []
        try:
            if tool_name == "get_inventory_overview":
                result = SafeInventoryResult.model_validate(data)
                label = state.selected_product.display_label if state.selected_product else "所选产品"
                answer = self._format_inventory_answer(label, result)
                suggestions = self.next_action_policy.suggestions(tool_name, result.model_dump(exclude_none=True))
            elif tool_name == "get_inventory_distribution":
                result = SafeInventoryDistributionResult.model_validate(data)
                answer = self._format_inventory_distribution(result)
            elif tool_name == "get_warehouse_status" and state.selected_warehouse is not None:
                result = SafeWarehouseResult.model_validate(data)
                answer = self._format_warehouse_answer(state.selected_warehouse.display_label, result)
            elif tool_name == "get_assay_status":
                label = state.selected_product.display_label if state.selected_product else "所选产品"
                answer = self._format_assay_answer(label, data)
            elif tool_name == "query_assay_records":
                answer = self._format_assay_records_answer(data)
            elif tool_name == "get_assay_report_detail":
                answer = self._format_assay_report_detail_answer(data)
            elif tool_name == "query_pallet_tasks":
                result = SafePalletTaskResult.model_validate(data)
                answer = self._format_pallet_tasks_answer(result)
                suggestions = self.next_action_policy.suggestions(
                    tool_name,
                    result.model_dump(exclude_none=True),
                )
        except ValueError:
            return None
        if not answer:
            return None

        completion = self._evaluate_registered_goal(state)
        if completion is not None:
            trace["goalCompletion"] = completion
        loop_trace = trace.setdefault("expertLoop", {"events": []})
        loop_trace["completionMode"] = "SAFE_FACT_FORMATTER"
        loop_trace.setdefault("events", []).append(
            {
                "action": "SAFE_FINAL_ANSWER",
                "toolName": tool_name,
                "observationId": observation.get("observationId"),
            }
        )
        self.metrics.increment("llm_fast_completion_total", tool=tool_name)
        return ChatResponse(
            agentSessionId=request.agentSessionId,
            answer=answer,
            cards=cards,
            suggestions=suggestions,
        )

    def _llm_safe_expert_failure_fallback(
        self,
        *,
        request: ChatRequest | ResumeRequest,
        state: WarehouseAgentState,
        user_message: str,
        expert_agent: str,
        observations: list[dict[str, Any]],
        tool_call_count: int,
        trace: dict[str, Any],
        error: ModelDecisionError,
    ) -> ChatResponse | None:
        if not self.safe_fallback_policy.can_attempt(
            error_code=error.code,
            tool_call_count=tool_call_count,
            max_tool_calls=self.llm_max_tool_calls,
            observations=observations,
        ):
            return None
        try:
            plan = self.argument_builder.plan(user_message=user_message, state=state)
        except (ExpertBoundaryError, TypeError, ValueError):
            return None
        handoff = (plan.routeSnapshot or {}).get("agent_handoff") or {}
        if str(handoff.get("target_agent") or "") != expert_agent:
            return None
        if not self.safe_fallback_policy.allows_plan(plan.action, plan.toolName):
            return None
        if plan.action == "call_tool":
            try:
                self.agent_router.authorize_tool(expert_agent, str(plan.toolName))
            except ExpertBoundaryError:
                return None

        trace["expertModelFallback"] = {
            "status": "SAFE_DETERMINISTIC_RECOVERY",
            "reason": error.code,
            "action": plan.action,
            "toolName": plan.toolName,
        }
        self.metrics.increment(
            "llm_safe_fallback_total",
            expert=expert_agent,
            reason=error.code,
        )
        response = self._execute_plan(request, state, plan)
        completion = self._attach_goal_completion(response, state)
        if completion is not None:
            trace["goalCompletion"] = completion
        return response

    def _finish_llm_response(
        self,
        response: ChatResponse,
        trace: dict[str, Any],
        request: ChatRequest | ResumeRequest,
    ) -> ChatResponse:
        response.reviewTrace = trace
        if request.client.debug:
            response.debug = {"llmToolLoop": trace}
        return response

    def _resume_llm_tool_loop(
        self,
        request: ResumeRequest,
        state: WarehouseAgentState,
        pending: PendingClarification,
        entity_type: str,
    ) -> ChatResponse:
        continuation = pending.continuation if isinstance(pending.continuation, dict) else {}
        if continuation.get("planningMode") != "llm":
            return self._llm_configuration_error(request.agentSessionId)
        expert_agent = str(continuation.get("expertAgent") or "")
        context = current_execution_context()
        if (
            expert_agent not in self.llm_allowed_experts
            or context is None
            or context.agent_name != expert_agent
            or pending.expert_agent != expert_agent
        ):
            return self._llm_boundary_rejection(
                request.agentSessionId,
                dict(continuation.get("trace") or {}),
                "恢复时专家或权限上下文发生变化，请重新发起查询。",
            )
        observations = continuation.get("observations")
        safe_observations = list(observations) if isinstance(observations, list) else []
        selected: dict[str, Any] = {"entityType": entity_type}
        if entity_type == "PRODUCT" and state.selected_product is not None:
            selected.update(
                {
                    "stateRef": "CURRENT_PRODUCT",
                    "canonicalName": self._safe_text(state.selected_product.metadata.get("productName")),
                }
            )
        if entity_type == "WAREHOUSE" and state.selected_warehouse is not None:
            selected.update(
                {
                    "stateRef": "CURRENT_WAREHOUSE",
                    "canonicalName": self._safe_display_label(state.selected_warehouse.display_label),
                }
            )
        safe_observations.append(
            {
                "observationId": f"obs_{len(safe_observations) + 1}",
                "status": "AVAILABLE",
                "tool": "USER_SELECTION",
                "data": selected,
            }
        )
        trace = continuation.get("trace")
        safe_trace = dict(trace) if isinstance(trace, dict) else {"planningMode": "llm"}
        return self._run_llm_expert_loop(
            request=request,
            state=state,
            user_message=str(continuation.get("userMessage") or self._latest_user_message(state)),
            expert_agent=expert_agent,
            observations=safe_observations,
            tool_call_count=self._bounded_int(
                continuation.get("toolCallCount"), default=0, minimum=0, maximum=self.llm_max_tool_calls
            ),
            retry_count=self._bounded_int(
                continuation.get("retryCount"), default=0, minimum=0, maximum=self.llm_max_tool_retries
            ),
            trace=safe_trace,
        )

    def _llm_configuration_error(self, agent_session_id: str) -> ChatResponse:
        return ChatResponse(
            agentSessionId=agent_session_id,
            answer="实验性 LLM Agent Mode 未正确配置，本次没有执行任何业务查询。",
            error=AgentError(code="LLM_AGENT_MODE_MISCONFIGURED", message="LLM Agent Mode 未配置。", retryable=False),
        )

    def _llm_model_error(
        self,
        agent_session_id: str,
        message: str,
        trace: dict[str, Any] | None = None,
    ) -> ChatResponse:
        response = ChatResponse(
            agentSessionId=agent_session_id,
            answer="模型暂时无法生成可校验的只读计划，本次没有扩大查询范围或执行未授权工具。",
            error=AgentError(code="LLM_PLAN_INVALID", message=message, retryable=True),
        )
        response.reviewTrace = trace
        return response

    def _llm_model_failure(
        self,
        agent_session_id: str,
        trace: dict[str, Any],
        error: ModelDecisionError,
    ) -> ChatResponse:
        if error.code == "MODEL_TIMEOUT":
            answer = "模型规划本轮查询时响应超时，本次没有执行未授权工具。"
            public_code = "MODEL_TIMEOUT"
        elif error.code == "MODEL_ACTION_INVALID":
            answer = "模型两次都没有生成符合安全契约的动作，本次没有执行未授权工具。"
            public_code = "LLM_PLAN_INVALID"
        elif error.code == "MODEL_NOT_CONFIGURED":
            answer = "实验性 LLM Agent Mode 未正确配置，本次没有执行任何业务查询。"
            public_code = "LLM_AGENT_MODE_MISCONFIGURED"
        else:
            answer = "模型服务暂时无法完成本轮只读规划，本次没有执行未授权工具。"
            public_code = "MODEL_UPSTREAM_ERROR"
        response = ChatResponse(
            agentSessionId=agent_session_id,
            answer=answer,
            error=AgentError(code=public_code, message=error.message, retryable=error.retryable),
        )
        response.reviewTrace = trace
        return response

    def _append_llm_model_diagnostics(self, trace: dict[str, Any]) -> None:
        diagnostics = take_model_decision_diagnostics()
        if diagnostics:
            trace.setdefault("modelDecisions", []).extend(diagnostics)
            for diagnostic in diagnostics:
                phase = str(diagnostic.get("phase") or "UNKNOWN")
                outcome = str(diagnostic.get("outcome") or "UNKNOWN")
                self.metrics.increment("model_decision_total", phase=phase, outcome=outcome)
                latency_ms = diagnostic.get("latencyMs")
                if isinstance(latency_ms, (int, float)):
                    self.metrics.observe(
                        "model_decision_duration",
                        float(latency_ms) / 1000,
                        phase=phase,
                        outcome=outcome,
                    )

    def _llm_context_followup_expert(
        self,
        state: WarehouseAgentState,
        text: str,
    ) -> str | None:
        normalized = re.sub(r"\s+", "", text or "").strip()
        if not normalized or len(normalized) > 60:
            return None
        followup_prefixes = (
            "只看",
            "仅看",
            "只查",
            "仅查",
            "筛选",
            "其中",
            "这些",
            "这个",
            "该",
            "它",
            "那",
            "再看",
            "再查",
            "再展开",
            "展开",
            "换成",
            "改看",
        )
        active_expert = str(state.active_agent or "")
        handoff = state.last_agent_handoff if isinstance(state.last_agent_handoff, dict) else {}
        if (
            active_expert not in self.llm_allowed_experts
            or handoff.get("target_agent") != active_expert
            or handoff.get("mode") != "llm_delegate"
        ):
            return None
        if self._llm_followup_crosses_expert_boundary(active_expert, normalized):
            return None
        if not normalized.startswith(followup_prefixes):
            if not normalized.startswith(("查", "查询", "帮我查", "看看")):
                return None
            try:
                plan = self.argument_builder.plan(user_message=text, state=state)
            except (ExpertBoundaryError, TypeError, ValueError):
                return None
            route_handoff = (plan.routeSnapshot or {}).get("agent_handoff") or {}
            if str(route_handoff.get("target_agent") or "") != active_expert:
                return None
        return active_expert

    @staticmethod
    def _llm_followup_crosses_expert_boundary(active_expert: str, text: str) -> bool:
        if active_expert == "inventory_expert":
            return any(word in text for word in ("化验", "质检", "指标", "合格", "标准", "生产订单", "煮糖", "领料", "托盘流转"))
        if active_expert == "warehouse_expert":
            return text.startswith(("只看", "仅看", "筛选")) or any(
                word in text for word in ("化验", "指标", "产品库存", "托盘", "生产")
            )
        if active_expert == "assay_expert":
            return any(word in text for word in ("库存", "库位", "仓库", "托盘", "生产订单", "煮糖"))
        if active_expert == "pallet_expert":
            return any(word in text for word in ("产品库存", "库位容量", "化验趋势", "生产订单", "煮糖", "领料"))
        if active_expert == "logistics_expert":
            return any(
                word in text
                for word in ("库存总览", "库存分布", "库位容量", "化验", "质量标准", "生产订单进度", "煮糖", "领料")
            )
        return True

    def _llm_business_recovery_expert(
        self,
        state: WarehouseAgentState,
        text: str,
    ) -> str | None:
        try:
            deterministic_plan = self.argument_builder.plan(user_message=text, state=state)
        except (TypeError, ValueError):
            deterministic_plan = None
        if deterministic_plan is not None:
            handoff = (deterministic_plan.routeSnapshot or {}).get("agent_handoff") or {}
            target = str(handoff.get("target_agent") or "")
            if target in self.llm_allowed_experts:
                return target

        bounded_followup = self._llm_context_followup_expert(state, text)
        if bounded_followup is not None:
            return bounded_followup

        active_expert = str(state.active_agent or "")
        handoff = state.last_agent_handoff if isinstance(state.last_agent_handoff, dict) else {}
        if (
            active_expert in self.llm_allowed_experts
            and handoff.get("target_agent") == active_expert
            and handoff.get("mode") == "llm_delegate"
            and not self._llm_followup_crosses_expert_boundary(active_expert, text)
        ):
            return active_expert
        return None

    def _llm_boundary_rejection(
        self,
        agent_session_id: str,
        trace: dict[str, Any],
        message: str,
    ) -> ChatResponse:
        response = ChatResponse(
            agentSessionId=agent_session_id,
            answer=message,
            error=AgentError(code="LLM_RUNTIME_BOUNDARY_REJECTED", message=message, retryable=False),
        )
        response.reviewTrace = trace
        return response

    def _llm_available_experts(self) -> list[dict[str, Any]]:
        values = []
        for name in sorted(self.llm_allowed_experts):
            profile = self.agent_router.profile(name)
            values.append(
                {
                    "expertAgent": profile.name,
                    "businessName": profile.business_name,
                    "domains": sorted(profile.domains),
                    "scope": list(profile.instructions),
                }
            )
        return values

    def _llm_selected_context(self, state: WarehouseAgentState) -> dict[str, Any]:
        context: dict[str, Any] = {"BUSINESS_TIME": self.business_clock.context()}
        if state.selected_product is not None:
            canonical_name = self._safe_text(state.selected_product.metadata.get("productName"))
            context["PRODUCT"] = {
                "stateRef": "CURRENT_PRODUCT",
                "canonicalName": canonical_name,
                "scopeType": self._safe_text(state.selected_product.metadata.get("scopeType")) or "SINGLE_PRODUCT",
            }
        if state.selected_warehouse is not None:
            context["WAREHOUSE"] = {
                "stateRef": "CURRENT_WAREHOUSE",
                "canonicalName": self._safe_display_label(state.selected_warehouse.display_label),
            }
        if state.selected_production_order is not None:
            order_ref = self._safe_text(state.selected_production_order.metadata.get("orderRef"))
            context["PRODUCTION_ORDER"] = {
                "entityRef": order_ref,
                "displayCode": self._safe_display_label(state.selected_production_order.display_label),
            }
        if state.selected_pallet is not None:
            context["PALLET"] = {
                "stateRef": "CURRENT_PALLET",
                "displayLabel": self._safe_display_label(state.selected_pallet.display_label),
            }
        if state.last_assay_records:
            records = state.last_assay_records.get("records")
            first_record = records[0] if isinstance(records, list) and records and isinstance(records[0], dict) else None
            if first_record is not None and self._safe_text(first_record.get("recordRef")):
                context["LAST_ASSAY_RECORD"] = {
                    "stateRef": "CURRENT_ASSAY_REPORT",
                    "sampleDate": self._safe_text(first_record.get("sampleDate")),
                    "productLabel": self._safe_text(first_record.get("productLabel")),
                    "judgeLabel": self._safe_text(first_record.get("judgeLabel")),
                }
        if state.last_inventory_distribution:
            groups = state.last_inventory_distribution.get("groups")
            product_names: list[str] = []
            if isinstance(groups, list):
                for group in groups[:10]:
                    if not isinstance(group, dict):
                        continue
                    name = self._safe_text(group.get("canonicalProductName"))
                    if name and name not in product_names:
                        product_names.append(name)
            context["LAST_READ"] = {
                "factType": "WAREHOUSE_INVENTORY_DISTRIBUTION",
                "scopeLabel": self._safe_text(state.last_inventory_distribution.get("scopeLabel")),
                "canonicalProducts": product_names,
            }
        if state.last_pallet_tasks:
            records = state.last_pallet_tasks.get("records")
            safe_records: list[dict[str, Any]] = []
            if isinstance(records, list):
                for raw_record in records[:3]:
                    record = self._dict_value(raw_record)
                    safe_records.append(
                        {
                            key: record.get(key)
                            for key in (
                                "taskTypeLabel",
                                "taskStatusLabel",
                                "palletCode",
                                "productLabel",
                                "targetLocationLabel",
                            )
                            if record.get(key) is not None
                        }
                    )
            context["LAST_TASK_QUERY"] = {
                "factType": "CURRENT_PENDING_TASKS",
                "scopeLabel": self._safe_text(state.last_pallet_tasks.get("scopeLabel")),
                "total": state.last_pallet_tasks.get("total"),
                "filterLabels": list(state.last_pallet_tasks.get("filterLabels") or [])[:8],
                "records": safe_records,
            }
        return context

    def _llm_safe_messages(self, state: WarehouseAgentState) -> list[dict[str, str]]:
        safe: list[dict[str, str]] = []
        for message in state.messages[-16:]:
            role = message.get("role")
            content = message.get("content")
            if role not in {"user", "assistant"} or not isinstance(content, str):
                continue
            text = self._sanitize_llm_context_text(content)
            if text:
                safe.append({"role": str(role), "content": text[:1000]})
        return safe[-12:]

    def _llm_model_observations(self, observations: list[dict[str, Any]]) -> list[dict[str, Any]]:
        return [
            {key: value for key, value in observation.items() if key not in {"callSignature", "resolved"}}
            for observation in observations
        ]

    def _sanitize_llm_context_text(self, value: str) -> str:
        text = re.sub(
            r"(?i)(bearer\s+[a-z0-9._-]+|authorization\s*[:=]\s*[^,;\s]+|"
            r"eyJ[a-z0-9_-]+\.[a-z0-9_-]+\.[a-z0-9_-]+)",
            "[REDACTED]",
            value,
        )
        return text.replace("\x00", "").strip()

    def _safe_main_decision_trace(self, decision: MainAgentDecisionV1) -> dict[str, Any]:
        return {
            "action": decision.action,
            "expertAgent": decision.expertAgent,
            "recipeId": decision.recipeId,
            "semanticReason": decision.semanticReason,
            "confidence": decision.confidence,
        }

    def _safe_expert_decision_trace(self, decision: ExpertLoopDecisionV1) -> dict[str, Any]:
        return {
            "action": decision.action,
            "toolName": decision.toolName,
            "statusReason": decision.statusReason,
            "citedObservationCount": len(decision.citedObservationIds),
        }

    def _sanitize_llm_answer(self, answer: str) -> str:
        value = self._sanitize_llm_context_text(answer)[:2500]
        if re.search(
            r"(?i)(productId|warehouseId|assayId|toolCallId|recordRef|reportRef|raw\s*json|chain[- ]?of[- ]?thought|"
            r"reasoning_content|jdbc:|delegationToken|refresh[_ -]?token)",
            value,
        ):
            return "模型回答包含不应向用户展示的内部信息，本次回答已被 Runtime 拦截。"
        if any(tool_name in value for tool_name in ALLOWED_TOOLS):
            return "模型回答包含不应向用户展示的内部工具信息，本次回答已被 Runtime 拦截。"
        value = re.sub(r"\b[a-z_]+_expert\b", "业务专家", value)
        value = self._sanitize_assay_business_text(value)
        value = self._normalize_user_facing_layout(value)
        return value or "模型没有生成可安全展示的回答。"

    def _normalize_user_facing_layout(self, value: str) -> str:
        """Add only obvious list breaks without imposing a rigid answer template."""
        text = value.replace("\r\n", "\n").replace("\r", "\n")
        inline_markers = re.findall(r"(?:^|[ \t]|[。！？；])\d{1,2}[.、][ \t]+", text)
        if not inline_markers:
            return text
        text = re.sub(
            r"(?<=[。！？；])[ \t]*(?=\d{1,2}[.、][ \t]+)",
            "\n\n",
            text,
        )
        text = re.sub(r"[ \t]+(?=\d{1,2}[.、][ \t]+)", "\n", text)
        return text

    def _llm_call_signature(self, tool_name: str, arguments: dict[str, Any]) -> str:
        payload = json.dumps(
            {"tool": tool_name, "arguments": arguments},
            ensure_ascii=False,
            sort_keys=True,
            separators=(",", ":"),
        )
        return hashlib.sha256(payload.encode("utf-8")).hexdigest()

    def _latest_unresolved_error(
        self,
        observations: list[dict[str, Any]],
        call_signature: str,
    ) -> dict[str, Any] | None:
        for observation in reversed(observations):
            if (
                observation.get("status") == "TOOL_ERROR"
                and observation.get("callSignature") == call_signature
                and not observation.get("resolved")
            ):
                return observation
        return None

    def _validate_llm_completion(
        self,
        decision: ExpertLoopDecisionV1,
        observations: list[dict[str, Any]],
        state: WarehouseAgentState,
    ) -> str | None:
        by_id = {str(item.get("observationId")): item for item in observations if item.get("observationId")}
        if any(observation_id not in by_id for observation_id in decision.citedObservationIds):
            return "模型引用了不存在的工具观察，Runtime 未接受完成结论。"
        cited = [by_id[observation_id] for observation_id in decision.citedObservationIds]
        if not any(item.get("status") in {"AVAILABLE", "NO_DATA"} for item in cited):
            return "模型没有引用可用或明确无数据的权威观察，不能形成业务结论。"
        unresolved_errors = [
            item for item in observations
            if item.get("status") == "TOOL_ERROR" and not item.get("resolved")
        ]
        if unresolved_errors and decision.action == "FINAL_ANSWER":
            return "仍有工具错误未解决，目标只能部分完成。"
        if unresolved_errors and decision.action == "PARTIAL_ANSWER":
            answer = decision.answer or ""
            if decision.statusReason not in {"TOOL_FAILED", "PARTIAL_DATA"}:
                return "部分完成必须明确标记未解决的工具失败。"
            if not any(word in answer for word in ("失败", "暂时无法", "查询异常", "未完成")):
                return "部分回答必须明确说明工具失败，不能把失败表述成无数据。"
        completion = self._evaluate_registered_goal(state)
        if completion is not None and decision.action == "FINAL_ANSWER" and completion["status"] != "COMPLETE":
            return "登记目标的实体或必需事实尚未完成，模型不能自行宣布完成。"
        return None

    def _llm_resolver_interrupt(
        self,
        *,
        request: ChatRequest | ResumeRequest,
        state: WarehouseAgentState,
        user_message: str,
        expert_agent: str,
        tool_name: str,
        result: dict[str, Any],
        observations: list[dict[str, Any]],
        tool_call_count: int,
        retry_count: int,
        trace: dict[str, Any],
    ) -> ChatResponse | None:
        if tool_name not in {"resolve_products", "resolve_warehouses"}:
            return None
        status = str(result.get("resolutionStatus") or "").upper()
        if status == "UNIQUE":
            if tool_name == "resolve_products":
                entity = self._single_product_entity(result)
                if entity is not None:
                    state.selected_product = entity
            else:
                entity = self._single_warehouse_entity(result)
                if entity is not None:
                    state.selected_warehouse = entity
            return None
        if status != "AMBIGUOUS":
            return None
        ambiguous_observation = {
            "observationId": f"obs_{len(observations) + 1}",
            "status": "AMBIGUOUS",
            "tool": tool_name,
            "candidateCount": self._list_size(result.get("options") or result.get("candidates")),
        }
        observations.append(ambiguous_observation)
        if tool_name == "resolve_products":
            pending = self._product_clarification(result, "llm_tool_loop", request)
        else:
            pending = self._warehouse_clarification(result, request, intent="llm_tool_loop")
        pending.continuation = {
            "planningMode": "llm",
            "userMessage": user_message,
            "expertAgent": expert_agent,
            "observations": observations,
            "toolCallCount": tool_call_count,
            "retryCount": retry_count,
            "trace": trace,
        }
        state.pending_clarification = pending
        state.interrupt_status[pending.interrupt_id] = pending.status
        return ChatResponse(
            agentSessionId=request.agentSessionId,
            answer=pending.prompt,
            needsUserSelection=True,
            cards=[self._clarification_card(pending)],
        )

    def _llm_success_observation(
        self,
        *,
        state: WarehouseAgentState,
        tool_name: str,
        arguments: dict[str, Any],
        result: dict[str, Any],
        observation_id: str,
        call_signature: str,
    ) -> tuple[dict[str, Any], list[BusinessCard]]:
        cards: list[BusinessCard] = []
        safe_data: dict[str, Any]
        if tool_name == "get_inventory_overview":
            adapted = self._adapt_inventory_result(result)
            safe_data = adapted.model_dump(exclude_none=True)
            state.last_inventory_result = dict(safe_data)
        elif tool_name == "get_inventory_distribution":
            adapted_distribution = self._adapt_inventory_distribution(result, arguments, state)
            safe_data = adapted_distribution.model_dump(exclude_none=True)
            state.last_inventory_distribution = dict(safe_data)
            if adapted_distribution.groups:
                cards = [self._inventory_distribution_card(adapted_distribution)]
        elif tool_name == "get_warehouse_status":
            adapted_warehouse = self._adapt_warehouse_result(result)
            safe_data = adapted_warehouse.model_dump(exclude_none=True)
            state.last_warehouse_result = dict(safe_data)
        elif tool_name == "get_assay_status":
            raw_safe_value = self._llm_safe_tool_data(result)
            raw_safe = raw_safe_value if isinstance(raw_safe_value, dict) else {}
            state.last_assay_result = dict(raw_safe)
            label = state.selected_product.display_label if state.selected_product else "所选产品"
            report = self._adapt_assay_status(raw_safe, label)
            safe_data = report.model_dump(exclude_none=True)
            card = self._assay_report_card(report)
            cards = [card] if card is not None else []
        elif tool_name == "query_assay_records":
            raw_safe_value = self._llm_safe_tool_data(result)
            raw_safe = raw_safe_value if isinstance(raw_safe_value, dict) else {}
            state.last_assay_records = dict(raw_safe)
            safe_data = self._adapt_assay_records(raw_safe)
            card = self._assay_history_card(safe_data)
            cards = [card] if card is not None else []
        elif tool_name == "get_assay_report_detail":
            raw_safe_value = self._llm_safe_tool_data(result)
            raw_safe = raw_safe_value if isinstance(raw_safe_value, dict) else {}
            state.last_assay_report_detail = dict(raw_safe)
            report = self._adapt_assay_report_detail(raw_safe)
            safe_data = report.model_dump(exclude_none=True)
            card = self._assay_report_card(report)
            cards = [card] if card is not None else []
        elif tool_name == "query_pallet_tasks":
            adapted_tasks = self._adapt_pallet_tasks(result, arguments)
            safe_data = adapted_tasks.model_dump(exclude_none=True)
            state.last_pallet_tasks = dict(safe_data)
            state.last_pallet_task_filters = dict(arguments)
            if adapted_tasks.records:
                cards = [self._pallet_tasks_card(adapted_tasks)]
        else:
            safe_value = self._llm_safe_tool_data(result)
            safe_data = safe_value if isinstance(safe_value, dict) else {"value": safe_value}
            if tool_name == "get_pallet_status":
                state.last_pallet_result = dict(safe_data)
                self._remember_pallet(state, arguments.get("code"))
            elif tool_name == "query_pallet_flow_records":
                state.last_pallet_flow_records = dict(safe_data)
            safe_data = self._redact_control_refs(safe_data)
        self._record_registered_goal_fact(
            state=state,
            tool_name=tool_name,
            arguments=arguments,
            safe_data=safe_data,
        )
        status = self._llm_result_status(tool_name, safe_data)
        return (
            {
                "observationId": observation_id,
                "status": status,
                "tool": tool_name,
                "callSignature": call_signature,
                "data": safe_data,
            },
            cards,
        )

    def _llm_result_status(self, tool_name: str, data: dict[str, Any]) -> str:
        if tool_name in {"resolve_products", "resolve_warehouses"}:
            status = str(data.get("resolutionStatus") or "").upper()
            return "NO_DATA" if status in {"NOT_FOUND", "NO_MATCH"} else "AVAILABLE"
        if data.get("isEmpty") is True or data.get("needsAssay") is True:
            return "NO_DATA"
        collections = [data.get(key) for key in ("records", "groups", "items", "locations", "timeline")]
        numeric_total = next(
            (data.get(key) for key in ("total", "count", "totalGroups") if data.get(key) is not None),
            None,
        )
        if numeric_total == 0 and not any(isinstance(items, list) and items for items in collections):
            return "NO_DATA"
        return "AVAILABLE"

    def _llm_safe_tool_data(self, value: Any, *, depth: int = 0) -> Any:
        if depth > 5:
            return "[TRUNCATED]"
        if isinstance(value, dict):
            safe: dict[str, Any] = {}
            for key, item in list(value.items())[:80]:
                key_text = str(key)
                lowered = key_text.lower()
                if (
                    key_text == "id"
                    or key_text.endswith("Id")
                    or key_text.endswith("_id")
                    or any(secret in lowered for secret in ("authorization", "password", "secret", "token", "rawjson", "stacktrace"))
                ):
                    continue
                safe[key_text] = self._llm_safe_tool_data(item, depth=depth + 1)
            return safe
        if isinstance(value, list):
            return [self._llm_safe_tool_data(item, depth=depth + 1) for item in value[:50]]
        if isinstance(value, str):
            return self._sanitize_llm_context_text(value)[:500]
        if value is None or isinstance(value, (bool, int, float)):
            return value
        return str(value)[:200]

    def _redact_control_refs(self, value: Any) -> Any:
        if isinstance(value, list):
            return [self._redact_control_refs(item) for item in value]
        if not isinstance(value, dict):
            return value
        return {
            key: self._redact_control_refs(item)
            for key, item in value.items()
            if key not in {"recordRef", "reportRef"}
        }

    def _execute_plan(self, request: ChatRequest, state: WarehouseAgentState, plan: Any) -> ChatResponse:
        if plan.action == "orchestrate":
            return self._execute_compound_plan(request, state, plan)
        if plan.action == "ask_user":
            return ChatResponse(
                agentSessionId=request.agentSessionId,
                answer=plan.prompt or "请补充更明确的查询条件。",
                needsUserSelection=True,
                suggestions=plan.suggestions,
            )
        if plan.action == "answer":
            return ChatResponse(agentSessionId=request.agentSessionId, answer=plan.answer or "已处理。")
        if plan.action != "call_tool" or not plan.toolName:
            return ChatResponse(
                agentSessionId=request.agentSessionId,
                answer="当前我只能使用受控的只读仓储工具。你可以换成库存、库位、托盘或化验状态查询。",
                needsUserSelection=True,
            )

        if plan.toolName == "resolve_products":
            return self._resolve_product_and_continue(request, state, plan.arguments, plan.intent or "inventory")
        if plan.toolName == "resolve_warehouses":
            return self._resolve_warehouse_and_continue(request, state, plan.arguments, plan.intent or "warehouse_status")
        if plan.toolName == "get_inventory_overview":
            if state.selected_product is None:
                return ChatResponse(
                    agentSessionId=request.agentSessionId,
                    answer="你想查哪个产品？请先选择或输入一个明确的产品名称。",
                    needsUserSelection=True,
                )
            if plan.responseMode == "inventory_locations":
                return self._answer_inventory_locations(
                    request.agentSessionId,
                    state,
                    request.client.traceId,
                    request.client.requestId,
                    request.messageId,
                    plan.arguments,
                )
            return self._answer_inventory(
                request.agentSessionId,
                state,
                request.client.traceId,
                request.client.requestId,
                request.messageId,
                plan.arguments,
            )
        if plan.toolName == "get_inventory_distribution":
            if state.selected_product is None and plan.arguments.get("productScope", {}).get("type") != "ALL":
                return ChatResponse(
                    agentSessionId=request.agentSessionId,
                    answer="你想查哪个产品？请先选择或输入一个明确的产品名称。",
                    needsUserSelection=True,
                )
            return self._answer_inventory_distribution(
                request.agentSessionId,
                state,
                request.client.traceId,
                request.client.requestId,
                request.messageId,
                plan.arguments,
            )
        if plan.toolName == "get_warehouse_status":
            if state.selected_warehouse is None:
                return ChatResponse(
                    agentSessionId=request.agentSessionId,
                    answer="你想查哪个库位？请提供库位名称，例如 2号库位。",
                    needsUserSelection=True,
                )
            return self._answer_warehouse_status(
                request.agentSessionId,
                state,
                request.client.traceId,
                request.client.requestId,
                request.messageId,
                plan.arguments,
            )
        if plan.toolName == "get_assay_status":
            if "productId" in plan.arguments and state.selected_product is None and "assayId" not in plan.arguments:
                return ChatResponse(
                    agentSessionId=request.agentSessionId,
                    answer="你想查询哪个产品的化验？请提供明确产品名称或先选择产品。",
                    needsUserSelection=True,
                )
            result = self._call_tool(request, "get_assay_status", plan.arguments)
            self._raise_if_cancelled()
            state.last_assay_result = result
            self._record_tool_message(state, "get_assay_status", self._safe_tool_summary("get_assay_status", result))
            label = state.selected_product.display_label if state.selected_product else "该产品"
            report = self._adapt_assay_status(result, label)
            card = self._assay_report_card(report)
            return ChatResponse(
                agentSessionId=request.agentSessionId,
                answer=self._format_assay_answer(label, report.model_dump(exclude_none=True)),
                cards=[card] if card is not None else [],
            )
        if plan.toolName == "query_assay_records":
            result = self._call_tool(request, "query_assay_records", plan.arguments)
            self._raise_if_cancelled()
            self._raise_if_tool_error_payload(result)
            state.last_assay_records = result
            self._record_tool_message(state, "query_assay_records", self._safe_tool_summary("query_assay_records", result))
            safe_result = self._adapt_assay_records(result)
            card = self._assay_history_card(safe_result)
            return ChatResponse(
                agentSessionId=request.agentSessionId,
                answer=self._format_assay_records_answer(safe_result),
                cards=[card] if card is not None else [],
            )
        if plan.toolName == "get_assay_report_detail":
            result = self._call_tool(request, "get_assay_report_detail", plan.arguments)
            self._raise_if_cancelled()
            self._raise_if_tool_error_payload(result)
            state.last_assay_report_detail = result
            self._record_tool_message(state, "get_assay_report_detail", self._safe_tool_summary("get_assay_report_detail", result))
            report = self._adapt_assay_report_detail(result)
            card = self._assay_report_card(report)
            return ChatResponse(
                agentSessionId=request.agentSessionId,
                answer=self._format_assay_report_detail_answer(report.model_dump(exclude_none=True)),
                cards=[card] if card is not None else [],
            )
        if plan.toolName == "query_assay_abnormalities":
            result = self._call_tool(request, "query_assay_abnormalities", plan.arguments)
            self._raise_if_cancelled()
            self._raise_if_tool_error_payload(result)
            state.last_assay_abnormalities = result
            self._record_tool_message(state, "query_assay_abnormalities", self._safe_tool_summary("query_assay_abnormalities", result))
            return ChatResponse(agentSessionId=request.agentSessionId, answer=self._format_assay_abnormalities_answer(result))
        if plan.toolName == "query_products_without_recent_assay":
            return self._answer_products_without_recent_assay(
                request.agentSessionId,
                state,
                request.client.traceId,
                request.client.requestId,
                request.messageId,
                plan.arguments,
            )
        if plan.toolName == "query_assay_standard_coverage":
            result = self._call_tool(request, "query_assay_standard_coverage", plan.arguments)
            self._raise_if_cancelled()
            self._raise_if_tool_error_payload(result)
            state.last_assay_standard_coverage = result
            self._record_tool_message(
                state,
                "query_assay_standard_coverage",
                self._safe_tool_summary("query_assay_standard_coverage", result),
            )
            return ChatResponse(agentSessionId=request.agentSessionId, answer=self._format_assay_standard_coverage_answer(result))
        if plan.toolName in {
            "query_qr_code_lifecycle",
            "query_printed_not_inbound_codes",
            "query_pallet_anomalies",
            "query_pallet_flow_records",
            "query_qr_batch_inbound_completion",
        }:
            return self._answer_pallet_lifecycle_tool(request, state, plan.toolName, plan.arguments)
        if plan.toolName == "resolve_production_entities":
            resolution = self._call_tool(request, "resolve_production_entities", plan.arguments)
            self._raise_if_cancelled()
            self._raise_if_tool_error_payload(resolution)
            self._record_tool_message(
                state,
                "resolve_production_entities",
                self._safe_tool_summary("resolve_production_entities", resolution),
            )
            candidates = resolution.get("candidates") if isinstance(resolution, dict) else None
            candidates = candidates if isinstance(candidates, list) else []
            if not candidates:
                return ChatResponse(
                    agentSessionId=request.agentSessionId,
                    answer="未查询到匹配的生产订单，请检查订单号。",
                )
            if len(candidates) > 1 or bool(resolution.get("needsUserSelection")):
                pending = self._production_order_clarification(resolution, request, plan.responseMode or plan.intent or "production_order_progress")
                state.pending_clarification = pending
                state.interrupt_status[pending.interrupt_id] = pending.status
                return ChatResponse(
                    agentSessionId=request.agentSessionId,
                    answer=pending.prompt,
                    needsUserSelection=True,
                    cards=[self._clarification_card(pending)],
                )
            candidate = candidates[0] if isinstance(candidates[0], dict) else {}
            entity_ref = self._safe_text(candidate.get("entityRef"))
            entity_type = self._safe_text(candidate.get("entityType") or resolution.get("entityType"))
            if not entity_ref or not entity_ref.startswith("aer_"):
                return ChatResponse(
                    agentSessionId=request.agentSessionId,
                    answer="生产实体解析结果缺少有效的受控引用，本次未继续查询。",
                    needsUserSelection=True,
                )
            if entity_type == "PRODUCTION_ORDER":
                state.selected_production_order = SelectedEntity(
                    internal_id=None,
                    display_label=self._safe_display_label(str(candidate.get("displayCode") or candidate.get("displayLabel") or "该生产订单")),
                    source="resolver",
                    metadata={"orderRef": entity_ref},
                )
            detail_tool = (
                "query_boiling_batch_trace"
                if entity_type == "BOILING_BATCH"
                else "query_material_pick_trace"
                if plan.responseMode == "material_pick_trace"
                else "query_material_candidates"
                if plan.responseMode == "material_candidates"
                else "query_production_label_completion"
                if plan.responseMode == "production_label_completion"
                else "query_production_order_progress"
            )
            detail_arguments = {"batchRef": entity_ref} if entity_type == "BOILING_BATCH" else {"orderRef": entity_ref}
            result = self._call_tool(request, detail_tool, detail_arguments)
            self._raise_if_cancelled()
            self._raise_if_tool_error_payload(result)
            self._record_tool_message(
                state,
                detail_tool,
                self._safe_tool_summary(detail_tool, result),
            )
            return ChatResponse(
                agentSessionId=request.agentSessionId,
                answer=(self._format_boiling_batch_trace_answer(result)
                        if detail_tool == "query_boiling_batch_trace"
                        else self._format_material_pick_trace_answer(result)
                        if detail_tool == "query_material_pick_trace"
                        else self._format_material_candidates_answer(result)
                        if detail_tool == "query_material_candidates"
                        else self._format_production_label_completion_answer(result)
                        if detail_tool == "query_production_label_completion"
                        else self._format_production_order_progress_answer(result)),
            )
        if plan.toolName == "query_production_order_progress":
            result = self._call_tool(request, plan.toolName, plan.arguments)
            self._raise_if_cancelled()
            self._raise_if_tool_error_payload(result)
            self._record_tool_message(state, plan.toolName, self._safe_tool_summary(plan.toolName, result))
            return ChatResponse(
                agentSessionId=request.agentSessionId,
                answer=self._format_production_order_progress_answer(result),
            )
        if plan.toolName == "query_boiling_batch_trace":
            result = self._call_tool(request, plan.toolName, plan.arguments)
            self._raise_if_cancelled()
            self._raise_if_tool_error_payload(result)
            self._record_tool_message(state, plan.toolName, self._safe_tool_summary(plan.toolName, result))
            return ChatResponse(
                agentSessionId=request.agentSessionId,
                answer=self._format_boiling_batch_trace_answer(result),
            )
        if plan.toolName == "query_material_pick_trace":
            result = self._call_tool(request, plan.toolName, plan.arguments)
            self._raise_if_cancelled()
            self._raise_if_tool_error_payload(result)
            self._record_tool_message(state, plan.toolName, self._safe_tool_summary(plan.toolName, result))
            return ChatResponse(agentSessionId=request.agentSessionId, answer=self._format_material_pick_trace_answer(result))
        if plan.toolName == "query_production_label_completion":
            result = self._call_tool(request, plan.toolName, plan.arguments)
            self._raise_if_cancelled()
            self._raise_if_tool_error_payload(result)
            self._record_tool_message(state, plan.toolName, self._safe_tool_summary(plan.toolName, result))
            return ChatResponse(agentSessionId=request.agentSessionId, answer=self._format_production_label_completion_answer(result))
        if plan.toolName == "query_in_process_materials":
            result = self._call_tool(request, plan.toolName, plan.arguments)
            self._raise_if_cancelled()
            self._raise_if_tool_error_payload(result)
            self._record_tool_message(state, plan.toolName, self._safe_tool_summary(plan.toolName, result))
            return ChatResponse(agentSessionId=request.agentSessionId, answer=self._format_in_process_materials_answer(result))
        if plan.toolName == "query_material_candidates":
            result = self._call_tool(request, plan.toolName, plan.arguments)
            self._raise_if_cancelled(); self._raise_if_tool_error_payload(result)
            self._record_tool_message(state, plan.toolName, self._safe_tool_summary(plan.toolName, result))
            return ChatResponse(agentSessionId=request.agentSessionId, answer=self._format_material_candidates_answer(result))
        if plan.toolName == "query_pallet_tasks":
            result = self._call_tool(request, plan.toolName, plan.arguments)
            self._raise_if_cancelled(); self._raise_if_tool_error_payload(result)
            adapted = self._adapt_pallet_tasks(result, plan.arguments)
            safe_data = adapted.model_dump(exclude_none=True)
            state.last_pallet_tasks = dict(safe_data)
            state.last_pallet_task_filters = dict(plan.arguments)
            self._record_registered_goal_fact(
                state=state,
                tool_name=plan.toolName,
                arguments=plan.arguments,
                safe_data=safe_data,
            )
            self._record_tool_message(state, plan.toolName, self._safe_tool_summary(plan.toolName, result))
            cards = [self._pallet_tasks_card(adapted)] if adapted.records else []
            return ChatResponse(
                agentSessionId=request.agentSessionId,
                answer=self._format_pallet_tasks_answer(adapted),
                cards=cards,
                suggestions=self.next_action_policy.suggestions(plan.toolName, safe_data),
            )
        if plan.toolName == "query_stock_documents":
            result = self._call_tool(request, plan.toolName, plan.arguments)
            self._raise_if_cancelled(); self._raise_if_tool_error_payload(result)
            self._record_tool_message(state, plan.toolName, self._safe_tool_summary(plan.toolName, result))
            return ChatResponse(agentSessionId=request.agentSessionId, answer=self._format_stock_documents_answer(result))
        if plan.toolName == "query_auto_inbound_batches":
            result = self._call_tool(request, plan.toolName, plan.arguments)
            self._raise_if_cancelled(); self._raise_if_tool_error_payload(result)
            state.last_auto_inbound_batches = result
            self._record_tool_message(state, plan.toolName, self._safe_tool_summary(plan.toolName, result))
            return ChatResponse(agentSessionId=request.agentSessionId, answer=self._format_auto_inbound_batches_answer(result))
        if plan.toolName == "get_auto_inbound_batch_detail":
            result = self._call_tool(request, plan.toolName, plan.arguments)
            self._raise_if_cancelled(); self._raise_if_tool_error_payload(result)
            self._record_tool_message(state, plan.toolName, self._safe_tool_summary(plan.toolName, result))
            return ChatResponse(agentSessionId=request.agentSessionId, answer=self._format_auto_inbound_batch_detail_answer(result))
        if plan.toolName == "query_warehouse_capacity_distribution":
            result = self._call_tool(request, plan.toolName, plan.arguments)
            self._raise_if_cancelled(); self._raise_if_tool_error_payload(result)
            self._record_tool_message(state, plan.toolName, self._safe_tool_summary(plan.toolName, result))
            return ChatResponse(agentSessionId=request.agentSessionId, answer=self._format_warehouse_capacity_distribution_answer(result))
        if plan.toolName == "query_warehouse_recent_operations":
            return self._answer_warehouse_recent_operations(request, state, plan.arguments)
        if plan.toolName == "query_warehouse_mixed_storage_facts":
            return self._answer_warehouse_mixed_storage_facts(request, state, plan.arguments)
        if plan.toolName in {"query_product_catalog", "get_product_detail", "query_screen_mesh_catalog"}:
            result = self._call_tool(request, plan.toolName, plan.arguments)
            self._raise_if_cancelled(); self._raise_if_tool_error_payload(result)
            self._record_tool_message(state, plan.toolName, self._safe_tool_summary(plan.toolName, result))
            formatter = {"query_product_catalog": self._format_product_catalog_answer,
                         "get_product_detail": self._format_product_detail_answer,
                         "query_screen_mesh_catalog": self._format_screen_mesh_catalog_answer}[plan.toolName]
            return ChatResponse(agentSessionId=request.agentSessionId, answer=formatter(result))
        if plan.toolName in {"query_assay_groups", "query_quality_standard_catalog", "get_quality_standard_detail", "query_product_standard_relations"}:
            result = self._call_tool(request, plan.toolName, plan.arguments); self._raise_if_cancelled(); self._raise_if_tool_error_payload(result)
            self._record_tool_message(state, plan.toolName, self._safe_tool_summary(plan.toolName, result))
            formatter = {"query_assay_groups": self._format_assay_groups_answer,
                         "query_quality_standard_catalog": self._format_quality_standard_catalog_answer,
                         "get_quality_standard_detail": self._format_quality_standard_detail_answer,
                         "query_product_standard_relations": self._format_product_standard_relations_answer}[plan.toolName]
            return ChatResponse(agentSessionId=request.agentSessionId, answer=formatter(result))
        if plan.toolName in {"query_employee_roster", "query_roles", "get_role_permission_summary"}:
            result = self._call_tool(request, plan.toolName, plan.arguments); self._raise_if_cancelled(); self._raise_if_tool_error_payload(result)
            self._record_tool_message(state, plan.toolName, self._safe_tool_summary(plan.toolName, result))
            formatter = {"query_employee_roster": self._format_employee_roster_answer,
                         "query_roles": self._format_role_catalog_answer,
                         "get_role_permission_summary": self._format_role_permission_summary_answer}[plan.toolName]
            return ChatResponse(agentSessionId=request.agentSessionId, answer=formatter(result))
        if plan.toolName in {"search_operation_logs", "query_agent_tool_audit", "query_agent_answer_reviews"}:
            result = self._call_tool(request, plan.toolName, plan.arguments); self._raise_if_cancelled(); self._raise_if_tool_error_payload(result)
            self._record_tool_message(state, plan.toolName, self._safe_tool_summary(plan.toolName, result))
            formatter = {"search_operation_logs": self._format_operation_logs_answer,
                         "query_agent_tool_audit": self._format_agent_tool_audit_answer,
                         "query_agent_answer_reviews": self._format_agent_answer_reviews_answer}[plan.toolName]
            return ChatResponse(agentSessionId=request.agentSessionId, answer=formatter(result))
        if plan.toolName in {"query_inventory_ledger", "query_prepare_pool_balance"}:
            result = self._call_tool(request, plan.toolName, plan.arguments); self._raise_if_cancelled(); self._raise_if_tool_error_payload(result)
            self._record_tool_message(state, plan.toolName, self._safe_tool_summary(plan.toolName, result))
            formatter = {"query_inventory_ledger": self._format_inventory_ledger_answer,
                         "query_prepare_pool_balance": self._format_prepare_pool_balance_answer}[plan.toolName]
            return ChatResponse(agentSessionId=request.agentSessionId, answer=formatter(result))
        if plan.toolName == "query_fixed_product_qr_pool":
            result = self._call_tool(request, plan.toolName, plan.arguments); self._raise_if_cancelled(); self._raise_if_tool_error_payload(result)
            self._record_tool_message(state, plan.toolName, self._safe_tool_summary(plan.toolName, result))
            return ChatResponse(agentSessionId=request.agentSessionId, answer=self._format_fixed_product_qr_pool_answer(result))
        if plan.toolName == "get_pallet_status":
            result = self._call_tool(request, "get_pallet_status", plan.arguments)
            self._raise_if_cancelled()
            state.last_pallet_result = result
            self._remember_pallet(state, plan.arguments.get("code"))
            self._record_tool_message(state, "get_pallet_status", self._safe_tool_summary("get_pallet_status", result))
            return ChatResponse(agentSessionId=request.agentSessionId, answer=self._format_pallet_answer(result))

        return ChatResponse(
            agentSessionId=request.agentSessionId,
            answer="当前工具不在只读白名单内，无法执行。",
            needsUserSelection=True,
        )

    def _remember_pallet(self, state: WarehouseAgentState, value: Any) -> None:
        code = self._safe_text(value)
        if not code:
            return
        state.selected_pallet = SelectedEntity(
            internal_id=None,
            display_label=code,
            source="tool_result",
            metadata={"code": code},
            entity_type="PALLET",
            entity_ref="CURRENT_PALLET",
            canonical_name=code,
        )

    def _execute_compound_plan(
        self,
        request: ChatRequest,
        state: WarehouseAgentState,
        plan: Any,
    ) -> ChatResponse:
        snapshot = plan.routeSnapshot if isinstance(plan.routeSnapshot, dict) else {}
        orchestration = snapshot.get("orchestration") if isinstance(snapshot.get("orchestration"), dict) else {}
        orchestration_state = snapshot.get("orchestrationState") if isinstance(snapshot.get("orchestrationState"), dict) else {}
        recipe = str(orchestration_state.get("recipeId") or orchestration.get("recipe") or "")
        if recipe != CompoundIntentPlanner.WAREHOUSE_INVENTORY_ASSAY:
            return ChatResponse(
                agentSessionId=request.agentSessionId,
                answer="这个跨模块组合查询尚未登记为可执行配方，请拆分为单独的只读查询。",
                error=AgentError(
                    code="ORCHESTRATION_RECIPE_NOT_ALLOWED",
                    message="跨模块执行配方未登记。",
                    retryable=False,
                ),
            )
        warehouse_query = self._safe_text(plan.arguments.get("warehouseQuery"))
        if not warehouse_query:
            return ChatResponse(
                agentSessionId=request.agentSessionId,
                answer="请提供明确的库位名称，例如 1号库位。",
                needsUserSelection=True,
            )

        state.orchestration_plan = deepcopy(orchestration_state)
        state.orchestration_step_results = {}
        validate_restored_plan(state.orchestration_plan)
        mark_step_started(state.orchestration_plan, "inventory_scope")
        self.metrics.increment("orchestration_started_total", recipe=recipe)

        inventory_context = self._expert_execution_context("inventory_expert", "inventory", state, "inventory_scope")
        self._store_immutable_execution_context(state, inventory_context, "inventory_scope")
        with bind_execution_context(inventory_context):
            result = self._orchestration_tool_call(
                state=state,
                step_id="inventory_scope",
                agent_session_id=request.agentSessionId,
                tool_name="resolve_warehouses",
                arguments={"query": warehouse_query, "limit": 10},
                trace_id=request.client.traceId,
                request_id=request.client.requestId,
                message_id=request.messageId,
            )
            self._raise_if_cancelled()
            self._record_tool_message(
                state,
                "resolve_warehouses",
                self._safe_tool_summary("resolve_warehouses", result),
            )
            status = str(result.get("resolutionStatus") or "").upper()
            if status == "AMBIGUOUS":
                pending = self._warehouse_clarification(
                    result,
                    request,
                    "compound_inventory_assay",
                    continuation={
                        "recipeId": recipe,
                        "planId": orchestration_state.get("planId") or orchestration.get("plan_id"),
                        "planVersion": orchestration_state.get("planVersion"),
                        "planFingerprint": orchestration_state.get("planFingerprint"),
                    },
                )
                state.pending_clarification = pending
                state.interrupt_status[pending.interrupt_id] = pending.status
                return ChatResponse(
                    agentSessionId=request.agentSessionId,
                    answer=pending.prompt,
                    needsUserSelection=True,
                    cards=[self._clarification_card(pending)],
                )
            if status == "NOT_FOUND":
                return ChatResponse(
                    agentSessionId=request.agentSessionId,
                    answer="未找到匹配库位，请换一个库位名称。",
                )
            entity = self._single_warehouse_entity(result)
            if entity is None:
                return ChatResponse(
                    agentSessionId=request.agentSessionId,
                    answer="库位解析结果不完整，请换一个更准确的库位名称。",
                    needsUserSelection=True,
                )
            state.selected_warehouse = entity

        return self._answer_compound_inventory_assay_from_selected(
            agent_session_id=request.agentSessionId,
            state=state,
            trace_id=request.client.traceId,
            request_id=request.client.requestId,
            message_id=request.messageId,
            continuation={
                "recipeId": recipe,
                "planId": orchestration_state.get("planId") or orchestration.get("plan_id"),
                "planVersion": orchestration_state.get("planVersion"),
                "planFingerprint": orchestration_state.get("planFingerprint"),
            },
        )

    def _answer_compound_inventory_assay_from_selected(
        self,
        *,
        agent_session_id: str,
        state: WarehouseAgentState,
        trace_id: str | None,
        request_id: str | None,
        message_id: str | None,
        continuation: dict[str, Any] | None,
    ) -> ChatResponse:
        if state.selected_warehouse is None or state.selected_warehouse.internal_id is None:
            return ChatResponse(
                agentSessionId=agent_session_id,
                answer="跨模块查询需要先确认一个具体库位。",
                needsUserSelection=True,
            )
        recipe = str((continuation or {}).get("recipeId") or "")
        if recipe != CompoundIntentPlanner.WAREHOUSE_INVENTORY_ASSAY:
            return ChatResponse(
                agentSessionId=agent_session_id,
                answer="这个跨模块查询上下文已失效，请重新发起。",
                error=AgentError(
                    code="ORCHESTRATION_CONTEXT_INVALID",
                    message="跨模块执行上下文无效。",
                    retryable=False,
                ),
            )

        orchestration = state.orchestration_plan
        try:
            if not isinstance(orchestration, dict):
                raise ValueError("orchestration state is missing")
            validate_restored_plan(orchestration)
            if (continuation or {}).get("planId") != orchestration.get("planId"):
                raise ValueError("restored orchestration planId changed")
            if (continuation or {}).get("planVersion") != orchestration.get("planVersion"):
                raise ValueError("restored orchestration planVersion changed")
            if (continuation or {}).get("planFingerprint") != orchestration.get("planFingerprint"):
                raise ValueError("restored orchestration fingerprint changed")
        except ValueError:
            if isinstance(orchestration, dict):
                orchestration["orchestrationStatus"] = "UNSUPPORTED"
                orchestration["finishedAt"] = datetime.now(timezone.utc).isoformat()
            return ChatResponse(
                agentSessionId=agent_session_id,
                answer="该复合查询计划版本与当前安全注册表不一致，已拒绝恢复，本次没有继续执行工具。",
                error=AgentError(
                    code="ORCHESTRATION_PLAN_VERSION_MISMATCH",
                    message="复合查询计划版本不一致。",
                    retryable=False,
                ),
            )

        max_fanout = self._bounded_int(
            orchestration.get("maxProductFanOut"),
            default=MAX_ASSAY_PRODUCT_FANOUT,
            minimum=1,
            maximum=MAX_ASSAY_PRODUCT_FANOUT,
        )
        distribution_args = {
            "productScope": {"type": "ALL"},
            "warehouseScope": {
                "type": "SINGLE_WAREHOUSE",
                "warehouseId": state.selected_warehouse.internal_id,
            },
            "groupBy": "product",
            "limit": 20,
        }
        inventory_context = self._expert_execution_context("inventory_expert", "inventory", state, "inventory_scope")
        self._store_immutable_execution_context(state, inventory_context, "inventory_scope")
        with bind_execution_context(inventory_context):
            try:
                inventory_response = self._answer_inventory_distribution(
                    agent_session_id,
                    state,
                    trace_id,
                    request_id,
                    message_id,
                    distribution_args,
                    orchestration_step_id="inventory_scope",
                )
            except OrchestrationBudgetExceeded:
                return self._orchestration_budget_exceeded_response(agent_session_id, state)
        orchestration["inventoryAsOf"] = datetime.now(timezone.utc).isoformat()
        mark_step_finished(
            orchestration,
            "inventory_scope",
            status="SUCCESS",
            safe_summary={
                "productCount": (state.last_inventory_distribution or {}).get("productCount"),
                "palletCount": (state.last_inventory_distribution or {}).get("palletCount"),
            },
        )
        self._observe_orchestration_step(orchestration, "inventory_scope")

        distribution = state.last_inventory_distribution or {}
        raw_groups = distribution.get("groups") if isinstance(distribution.get("groups"), list) else []
        product_targets: list[dict[str, str]] = []
        seen_canonical_names: set[str] = set()
        seen_missing_labels: set[str] = set()
        assay_summaries: list[dict[str, str]] = []
        for raw_group in raw_groups:
            group = self._dict_value(raw_group)
            display_label = self._safe_text(group.get("productLabel") or group.get("groupLabel"))
            canonical_name = self._safe_text(group.get("canonicalProductName"))
            if not canonical_name:
                if display_label and display_label not in seen_missing_labels:
                    assay_summaries.append(
                        {
                            "product": display_label,
                            "status": "缺少受控产品规范名，未使用展示标签继续查询",
                            "errorCode": "ENTITY_CONTEXT_MISSING",
                        }
                    )
                    seen_missing_labels.add(display_label)
                continue
            if canonical_name in seen_canonical_names:
                continue
            product_targets.append(
                {
                    "canonicalName": canonical_name,
                    "displayLabel": display_label or canonical_name,
                }
            )
            seen_canonical_names.add(canonical_name)

        mark_step_started(orchestration, "latest_assay")
        assay_context = self._expert_execution_context("assay_expert", "assay", state, "latest_assay")
        self._store_immutable_execution_context(state, assay_context, "latest_assay")
        with bind_execution_context(assay_context):
            for product_target in product_targets[:max_fanout]:
                canonical_name = product_target["canonicalName"]
                product_label = product_target["displayLabel"]
                self._raise_if_cancelled()
                try:
                    resolution = self._orchestration_tool_call(
                        state=state,
                        step_id="latest_assay",
                        agent_session_id=agent_session_id,
                        tool_name="resolve_products",
                        arguments={"query": canonical_name, "limit": 10},
                        trace_id=trace_id,
                        request_id=request_id,
                        message_id=message_id,
                    )
                    self._record_tool_message(
                        state,
                        "resolve_products",
                        self._safe_tool_summary("resolve_products", resolution),
                    )
                    if str(resolution.get("resolutionStatus") or "").upper() != "UNIQUE":
                        assay_summaries.append(
                            {
                                "product": product_label,
                                "status": "产品范围无法唯一确认，未自动查询化验",
                                "errorCode": "ENTITY_AMBIGUOUS",
                            }
                        )
                        continue
                    entity = self._single_product_entity(resolution)
                    if entity is None or entity.internal_id is None:
                        assay_summaries.append(
                            {
                                "product": product_label,
                                "status": "缺少可校验的产品映射，未自动查询化验",
                                "errorCode": "ENTITY_AMBIGUOUS",
                            }
                        )
                        continue
                    assay_result = self._orchestration_tool_call(
                        state=state,
                        step_id="latest_assay",
                        agent_session_id=agent_session_id,
                        tool_name="query_assay_records",
                        arguments={
                            "productScope": {
                                "type": "SINGLE_PRODUCT",
                                "productId": entity.internal_id,
                            },
                            "judgeStatus": "ANY",
                            "sortBy": "sampleDate",
                            "sortDirection": "DESC",
                            "page": 1,
                            "size": 1,
                        },
                        trace_id=trace_id,
                        request_id=request_id,
                        message_id=message_id,
                    )
                    self._raise_if_tool_error_payload(assay_result)
                    self._record_tool_message(
                        state,
                        "query_assay_records",
                        self._safe_tool_summary("query_assay_records", assay_result),
                    )
                    assay_summaries.append(
                        self._latest_assay_summary(product_label, assay_result)
                    )
                except OrchestrationBudgetExceeded:
                    return self._orchestration_budget_exceeded_response(
                        agent_session_id,
                        state,
                        inventory_response.answer,
                        inventory_response.cards,
                    )
                except ToolGatewayError as exc:
                    assay_summaries.append(
                        {
                            "product": product_label,
                            "status": self._orchestration_error_text(exc.code),
                            "errorCode": self._orchestration_error_code(exc.code),
                        }
                    )

        omitted = max(0, len(product_targets) - max_fanout)
        partial = omitted or any(
            item.get("errorCode") not in {None, "NO_DATA"}
            for item in assay_summaries
        )
        orchestration["productFanOutCount"] = min(len(product_targets), max_fanout)
        orchestration["assayAsOf"] = datetime.now(timezone.utc).isoformat()
        result_counts: dict[str, int] = {}
        for item in assay_summaries:
            category = item.get("errorCode") or "SUCCESS"
            result_counts[category] = result_counts.get(category, 0) + 1
        mark_step_finished(
            orchestration,
            "latest_assay",
            status="PARTIAL_SUCCESS" if partial else "SUCCESS",
            safe_summary={
                "queriedProductCount": len(assay_summaries),
                "omittedProductCount": omitted,
                "resultCounts": result_counts,
            },
        )
        self._observe_orchestration_step(orchestration, "latest_assay")
        mark_step_started(orchestration, "present")
        mark_step_finished(orchestration, "present", status="SUCCESS")
        self._observe_orchestration_step(orchestration, "present")
        orchestration["orchestrationStatus"] = "PARTIAL_SUCCESS" if partial else "SUCCESS"
        orchestration["finishedAt"] = datetime.now(timezone.utc).isoformat()
        state.last_orchestration = deepcopy(orchestration)
        state.orchestration_step_results = deepcopy(orchestration.get("steps") or {})
        self.metrics.increment(
            "orchestration_partial_success_total" if partial else "orchestration_success_total",
            recipe=recipe,
        )
        self.metrics.observe("tool_call_count_per_plan", orchestration["toolCallCount"], recipe=recipe)
        self.metrics.observe("product_fan_out_count", orchestration["productFanOutCount"], recipe=recipe)
        answer = self._format_compound_inventory_assay_answer(
            inventory_response.answer,
            assay_summaries,
            omitted,
        )
        return ChatResponse(
            agentSessionId=agent_session_id,
            answer=answer,
            cards=inventory_response.cards,
        )

    def _latest_assay_summary(self, product_label: str, result: dict[str, Any]) -> dict[str, str]:
        records = result.get("records") if isinstance(result.get("records"), list) else []
        if not records:
            return {"product": product_label, "status": "未查询到化验记录", "errorCode": "NO_DATA"}
        record = self._dict_value(records[0])
        sample_date = self._safe_text(record.get("sampleDate"))
        judge = self._safe_text(record.get("judgeLabel") or record.get("judgeStatus")) or "结论未明"
        standard = self._safe_text(record.get("standardLabel"))
        parts = [judge]
        if sample_date:
            parts.insert(0, sample_date)
        if standard:
            parts.append(f"标准：{standard}")
        return {"product": product_label, "status": "，".join(parts)}

    def _format_compound_inventory_assay_answer(
        self,
        inventory_answer: str,
        assay_summaries: list[dict[str, str]],
        omitted: int,
    ) -> str:
        lines = [inventory_answer]
        if assay_summaries:
            lines.append("\n这些产品的最新化验记录如下：")
            for index, item in enumerate(assay_summaries, start=1):
                lines.append(f"{index}. {item['product']}：{item['status']}。")
        else:
            lines.append("\n当前库位没有可用于继续查询化验的产品范围。")
        if omitted:
            lines.append(f"另有 {omitted} 类产品因单次查询上限未逐项查询化验，可缩小范围后继续查询。")
        lines.append("说明：以上是按产品匹配的最新化验记录，不代表当前库存批次已经逐批对应并判定合格。")
        return "\n".join(lines)

    def _orchestration_tool_call(
        self,
        *,
        state: WarehouseAgentState,
        step_id: str,
        agent_session_id: str,
        tool_name: str,
        arguments: dict[str, Any],
        trace_id: str | None,
        request_id: str | None,
        message_id: str | None,
    ) -> dict[str, Any]:
        orchestration = state.orchestration_plan
        if not isinstance(orchestration, dict):
            raise ToolGatewayError("ORCHESTRATION_CONTEXT_INVALID", "复合查询上下文无效。")
        steps = orchestration.get("steps")
        step = steps.get(step_id) if isinstance(steps, dict) else None
        if not isinstance(step, dict) or tool_name not in (step.get("plannedTools") or []):
            raise ToolGatewayError("EXPERT_TOOL_NOT_ALLOWED", "当前复合步骤无权调用该工具。")
        call_count = int(orchestration.get("toolCallCount") or 0)
        max_calls = int(orchestration.get("maxToolCalls") or 0)
        if call_count >= max_calls:
            orchestration["orchestrationStatus"] = "BUDGET_EXCEEDED"
            orchestration["finishedAt"] = datetime.now(timezone.utc).isoformat()
            raise OrchestrationBudgetExceeded()
        orchestration["toolCallCount"] = call_count + 1
        orchestration["toolBudgetRemaining"] = max_calls - call_count - 1
        step.setdefault("executedTools", []).append(tool_name)
        return self._call_tool_values(
            agent_session_id=agent_session_id,
            tool_name=tool_name,
            arguments=arguments,
            trace_id=trace_id,
            request_id=request_id,
            message_id=message_id,
        )

    def _orchestration_budget_exceeded_response(
        self,
        agent_session_id: str,
        state: WarehouseAgentState,
        inventory_answer: str | None = None,
        cards: list[BusinessCard] | None = None,
    ) -> ChatResponse:
        orchestration = state.orchestration_plan or {}
        orchestration["orchestrationStatus"] = "BUDGET_EXCEEDED"
        orchestration["finishedAt"] = datetime.now(timezone.utc).isoformat()
        state.last_orchestration = deepcopy(orchestration)
        self.metrics.increment("orchestration_budget_exceeded_total", recipe=orchestration.get("recipeId"))
        prefix = f"{inventory_answer}\n" if inventory_answer else ""
        return ChatResponse(
            agentSessionId=agent_session_id,
            answer=prefix + "复合查询已达到只读工具调用预算，剩余步骤未执行。",
            cards=cards or [],
            error=AgentError(
                code="BUDGET_EXCEEDED",
                message="复合查询工具调用预算已用尽。",
                retryable=False,
            ),
        )

    def _orchestration_error_code(self, code: str) -> str:
        if code in {"UPSTREAM_TIMEOUT", "TOOL_TIMEOUT"}:
            return "TOOL_TIMEOUT"
        if code in {"UPSTREAM_PERMISSION_DENIED", "AGENT_SCOPE_DENIED"}:
            return "PERMISSION_DENIED"
        return "TOOL_ERROR"

    def _orchestration_error_text(self, code: str) -> str:
        category = self._orchestration_error_code(code)
        if category == "TOOL_TIMEOUT":
            return "化验服务查询超时"
        if category == "PERMISSION_DENIED":
            return "当前用户无权查询该产品化验"
        return "化验服务查询失败"

    def _expert_execution_context(
        self,
        agent_name: str,
        business_domain: str,
        state: WarehouseAgentState | None = None,
        step_id: str | None = None,
    ) -> AgentExecutionContext:
        handoff = self.argument_builder.handoff_for_agent(
            agent_name,
            business_domain=business_domain,
            mode="orchestration_step",
        )
        base = self._execution_context_from_handoff(handoff.to_snapshot())
        orchestration = state.orchestration_plan if state is not None else None
        active_run = state.active_run if state is not None else None
        return AgentExecutionContext(
            agent_name=base.agent_name,
            allowed_tools=base.allowed_tools,
            business_domain=base.business_domain,
            handoff_mode=base.handoff_mode,
            handoff_id=(active_run or {}).get("handoffId"),
            plan_id=(orchestration or {}).get("planId"),
            step_id=step_id,
        )

    def _store_immutable_execution_context(
        self,
        state: WarehouseAgentState,
        context: AgentExecutionContext,
        step_id: str,
    ) -> None:
        snapshot = {
            "agentName": context.agent_name,
            "allowedTools": sorted(context.allowed_tools),
            "businessDomain": context.business_domain,
            "handoffMode": context.handoff_mode,
            "handoffId": context.handoff_id,
            "planId": (state.orchestration_plan or {}).get("planId"),
            "recipeId": (state.orchestration_plan or {}).get("recipeId"),
            "planVersion": (state.orchestration_plan or {}).get("planVersion"),
            "stepId": step_id,
        }
        snapshot["contextFingerprint"] = hashlib.sha256(
            repr(sorted(snapshot.items())).encode("utf-8")
        ).hexdigest()
        state.immutable_execution_context = snapshot

    def _observe_orchestration_step(self, orchestration: dict[str, Any], step_id: str) -> None:
        step = (orchestration.get("steps") or {}).get(step_id) or {}
        try:
            started = datetime.fromisoformat(str(step["startedAt"]))
            finished = datetime.fromisoformat(str(step["finishedAt"]))
        except (KeyError, TypeError, ValueError):
            return
        self.metrics.observe(
            "orchestration_step_duration",
            max(0.0, (finished - started).total_seconds()),
            recipe=str(orchestration.get("recipeId") or "unknown"),
            step=step_id,
            status=str(step.get("status") or "UNKNOWN"),
        )

    def _bounded_int(self, value: Any, *, default: int, minimum: int, maximum: int) -> int:
        try:
            parsed = int(value)
        except (TypeError, ValueError):
            return default
        return min(maximum, max(minimum, parsed))

    def _resolve_product_and_continue(
        self, request: ChatRequest, state: WarehouseAgentState, arguments: dict[str, Any], intent: str
    ) -> ChatResponse:
        result = self._call_tool(request, "resolve_products", arguments)
        self._raise_if_cancelled()
        self._record_tool_message(state, "resolve_products", self._safe_tool_summary("resolve_products", result))
        status = str(result.get("resolutionStatus") or "").upper()
        if status == "AMBIGUOUS":
            pending = self._product_clarification(result, intent, request)
            self._raise_if_cancelled()
            state.pending_clarification = pending
            state.interrupt_status[pending.interrupt_id] = pending.status
            return ChatResponse(
                agentSessionId=request.agentSessionId,
                answer=pending.prompt,
                needsUserSelection=True,
                cards=[self._clarification_card(pending)],
            )
        if status == "NOT_FOUND":
            return ChatResponse(
                agentSessionId=request.agentSessionId,
                answer="未找到匹配产品，请换一个更准确的产品名称或编号。",
            )
        entity = self._single_product_entity(result)
        if entity is None:
            return ChatResponse(
                agentSessionId=request.agentSessionId,
                answer="产品解析结果不完整，请换一个更准确的产品名称。",
                needsUserSelection=True,
            )
        self._raise_if_cancelled()
        state.selected_product = entity
        return self._continue_product_intent(
            request.agentSessionId,
            state,
            intent,
            request.client.traceId,
            request.client.requestId,
            request.messageId,
        )

    def _continue_product_intent(
        self,
        agent_session_id: str,
        state: WarehouseAgentState,
        intent: str,
        trace_id: str | None,
        request_id: str | None,
        message_id: str | None,
    ) -> ChatResponse:
        if intent == "product_detail":
            if state.selected_product is None:
                return ChatResponse(agentSessionId=agent_session_id, answer="产品详情查询需要先确认产品。", needsUserSelection=True)
            product_name = self._safe_text(state.selected_product.metadata.get("productName"))
            if not product_name:
                product_name = state.selected_product.display_label
            result = self._call_tool_values(
                agent_session_id=agent_session_id,
                tool_name="get_product_detail",
                arguments={"productName": product_name},
                trace_id=trace_id,
                request_id=request_id,
                message_id=message_id,
            )
            self._raise_if_cancelled()
            self._raise_if_tool_error_payload(result)
            self._record_tool_message(state, "get_product_detail", self._safe_tool_summary("get_product_detail", result))
            return ChatResponse(agentSessionId=agent_session_id, answer=self._format_product_detail_answer(result))
        if intent == "assay":
            if state.selected_product is None or state.selected_product.internal_id is None:
                return ChatResponse(agentSessionId=agent_session_id, answer="化验查询需要选择一个具体产品规格。", needsUserSelection=True)
            result = self._call_tool_values(
                agent_session_id=agent_session_id,
                tool_name="get_assay_status",
                arguments={
                    "productId": state.selected_product.internal_id,
                    "productionDate": self.business_clock.today().isoformat(),
                },
                trace_id=trace_id,
                request_id=request_id,
                message_id=message_id,
            )
            self._raise_if_cancelled()
            state.last_assay_result = result
            self._record_tool_message(state, "get_assay_status", self._safe_tool_summary("get_assay_status", result))
            report = self._adapt_assay_status(result, state.selected_product.display_label)
            card = self._assay_report_card(report)
            return ChatResponse(
                agentSessionId=agent_session_id,
                answer=self._format_assay_answer(
                    state.selected_product.display_label,
                    report.model_dump(exclude_none=True),
                ),
                cards=[card] if card is not None else [],
            )
        if intent == "assay_records":
            if state.selected_product is None:
                return ChatResponse(agentSessionId=agent_session_id, answer="化验记录查询需要先确认产品范围。", needsUserSelection=True)
            user_message = self._latest_user_message(state)
            arguments = self.argument_builder.assay_records_arguments_for_state(
                {"productScope": {"type": "SINGLE_PRODUCT"}}, state, user_message
            )
            result = self._call_tool_values(
                agent_session_id=agent_session_id,
                tool_name="query_assay_records",
                arguments=arguments,
                trace_id=trace_id,
                request_id=request_id,
                message_id=message_id,
            )
            self._raise_if_cancelled()
            self._raise_if_tool_error_payload(result)
            state.last_assay_records = result
            self._record_tool_message(state, "query_assay_records", self._safe_tool_summary("query_assay_records", result))
            safe_result = self._adapt_assay_records(result)
            card = self._assay_history_card(safe_result)
            return ChatResponse(
                agentSessionId=agent_session_id,
                answer=self._format_assay_records_answer(safe_result),
                cards=[card] if card is not None else [],
            )
        if intent == "assay_abnormalities":
            if state.selected_product is None:
                return ChatResponse(agentSessionId=agent_session_id, answer="化验异常查询需要先确认产品范围。", needsUserSelection=True)
            user_message = self._latest_user_message(state)
            arguments = self.argument_builder.assay_abnormalities_arguments_for_state(
                {"productScope": {"type": "SINGLE_PRODUCT"}}, state, user_message
            )
            result = self._call_tool_values(
                agent_session_id=agent_session_id,
                tool_name="query_assay_abnormalities",
                arguments=arguments,
                trace_id=trace_id,
                request_id=request_id,
                message_id=message_id,
            )
            self._raise_if_cancelled()
            self._raise_if_tool_error_payload(result)
            state.last_assay_abnormalities = result
            self._record_tool_message(state, "query_assay_abnormalities", self._safe_tool_summary("query_assay_abnormalities", result))
            return ChatResponse(agentSessionId=agent_session_id, answer=self._format_assay_abnormalities_answer(result))
        if intent == "products_without_recent_assay":
            if state.selected_product is None:
                return ChatResponse(agentSessionId=agent_session_id, answer="缺化验查询需要先确认产品范围。", needsUserSelection=True)
            user_message = self._latest_user_message(state)
            arguments = self.argument_builder.products_without_recent_assay_arguments_for_state(
                {"productScope": {"type": "SINGLE_PRODUCT"}, "warehouseScope": {"type": "ALL"}},
                state,
                user_message,
            )
            return self._answer_products_without_recent_assay(
                agent_session_id,
                state,
                trace_id,
                request_id,
                message_id,
                arguments,
            )
        if intent == "assay_standard_coverage":
            if state.selected_product is None:
                return ChatResponse(agentSessionId=agent_session_id, answer="质量标准覆盖查询需要先确认产品范围。", needsUserSelection=True)
            user_message = self._latest_user_message(state)
            arguments = self.argument_builder.assay_standard_coverage_arguments_for_state(
                {"productScope": {"type": "SINGLE_PRODUCT"}, "coverageType": "PRODUCT_WITHOUT_STANDARD"},
                state,
                user_message,
            )
            result = self._call_tool_values(
                agent_session_id=agent_session_id,
                tool_name="query_assay_standard_coverage",
                arguments=arguments,
                trace_id=trace_id,
                request_id=request_id,
                message_id=message_id,
            )
            self._raise_if_cancelled()
            self._raise_if_tool_error_payload(result)
            state.last_assay_standard_coverage = result
            self._record_tool_message(
                state,
                "query_assay_standard_coverage",
                self._safe_tool_summary("query_assay_standard_coverage", result),
            )
            return ChatResponse(agentSessionId=agent_session_id, answer=self._format_assay_standard_coverage_answer(result))
        if intent in {"printed_not_inbound_codes", "pallet_anomalies", "pallet_flow_records"}:
            if state.selected_product is None:
                return ChatResponse(agentSessionId=agent_session_id, answer="请先确认产品范围，再查询托盘数据。", needsUserSelection=True)
            user_message = self._latest_user_message(state)
            argument_methods = {
                "printed_not_inbound_codes": self.argument_builder.printed_not_inbound_arguments_for_state,
                "pallet_anomalies": self.argument_builder.pallet_anomalies_arguments_for_state,
                "pallet_flow_records": self.argument_builder.pallet_flow_records_arguments_for_state,
            }
            tool_names = {
                "printed_not_inbound_codes": "query_printed_not_inbound_codes",
                "pallet_anomalies": "query_pallet_anomalies",
                "pallet_flow_records": "query_pallet_flow_records",
            }
            arguments = argument_methods[intent]({}, state, user_message)
            return self._answer_pallet_lifecycle_tool(
                agent_session_id,
                state,
                tool_names[intent],
                arguments,
                trace_id,
                request_id,
                message_id,
            )
        if intent == "inventory_distribution":
            return self._answer_inventory_distribution(agent_session_id, state, trace_id, request_id, message_id)
        if state.selected_product is None or state.selected_product.internal_id is None:
            return ChatResponse(agentSessionId=agent_session_id, answer="库存概览需要选择一个具体产品规格。", needsUserSelection=True)
        return self._answer_inventory(agent_session_id, state, trace_id, request_id, message_id)

    def _answer_inventory(
        self,
        agent_session_id: str,
        state: WarehouseAgentState,
        trace_id: str | None,
        request_id: str | None,
        message_id: str | None,
        arguments: dict[str, Any] | None = None,
    ) -> ChatResponse:
        tool_arguments = arguments or {"productId": state.selected_product.internal_id}
        result = self._call_tool_values(
            agent_session_id=agent_session_id,
            tool_name="get_inventory_overview",
            arguments=tool_arguments,
            trace_id=trace_id,
            request_id=request_id,
            message_id=message_id,
        )
        safe_result = self._adapt_inventory_result(result)
        self._raise_if_cancelled()
        state.last_inventory_result = safe_result.model_dump(exclude_none=True)
        self._record_registered_goal_fact(
            state=state,
            tool_name="get_inventory_overview",
            arguments=tool_arguments,
            safe_data=safe_result.model_dump(exclude_none=True),
        )
        state.tool_results.append({"kind": "inventory_overview", "summary": self._inventory_summary(safe_result)})
        self._record_tool_message(
            state,
            "get_inventory_overview",
            self._safe_tool_summary("get_inventory_overview", safe_result.model_dump(exclude_none=True)),
        )
        return ChatResponse(
            agentSessionId=agent_session_id,
            answer=self._format_inventory_answer(state.selected_product.display_label, safe_result),
        )

    def _answer_inventory_locations(
        self,
        agent_session_id: str,
        state: WarehouseAgentState,
        trace_id: str | None,
        request_id: str | None,
        message_id: str | None,
        arguments: dict[str, Any] | None = None,
    ) -> ChatResponse:
        safe_result = None
        if state.last_inventory_result:
            try:
                safe_result = SafeInventoryResult.model_validate(state.last_inventory_result)
            except ValueError:
                safe_result = None
        if safe_result is None:
            tool_arguments = arguments or {"productId": state.selected_product.internal_id}
            result = self._call_tool_values(
                agent_session_id=agent_session_id,
                tool_name="get_inventory_overview",
                arguments=tool_arguments,
                trace_id=trace_id,
                request_id=request_id,
                message_id=message_id,
            )
            safe_result = self._adapt_inventory_result(result)
            self._raise_if_cancelled()
            state.last_inventory_result = safe_result.model_dump(exclude_none=True)
            self._record_tool_message(
                state,
                "get_inventory_overview",
                self._safe_tool_summary("get_inventory_overview", safe_result.model_dump(exclude_none=True)),
            )

        self._record_registered_goal_fact(
            state=state,
            tool_name="get_inventory_overview",
            arguments=arguments,
            safe_data=safe_result.model_dump(exclude_none=True),
        )

        return ChatResponse(
            agentSessionId=agent_session_id,
            answer=self._format_inventory_locations(state.selected_product.display_label, safe_result),
        )

    def _answer_inventory_distribution(
        self,
        agent_session_id: str,
        state: WarehouseAgentState,
        trace_id: str | None,
        request_id: str | None,
        message_id: str | None,
        arguments: dict[str, Any] | None = None,
        orchestration_step_id: str | None = None,
    ) -> ChatResponse:
        if arguments is None:
            arguments = self.argument_builder.distribution_arguments_for_state({}, state)
        if orchestration_step_id:
            raw = self._orchestration_tool_call(
                state=state,
                step_id=orchestration_step_id,
                agent_session_id=agent_session_id,
                tool_name="get_inventory_distribution",
                arguments=arguments,
                trace_id=trace_id,
                request_id=request_id,
                message_id=message_id,
            )
        else:
            raw = self._call_tool_values(
                agent_session_id=agent_session_id,
                tool_name="get_inventory_distribution",
                arguments=arguments,
                trace_id=trace_id,
                request_id=request_id,
                message_id=message_id,
            )
        self._raise_if_tool_error_payload(raw)
        safe_result = self._adapt_inventory_distribution(raw, arguments, state)
        self._raise_if_cancelled()
        state.last_inventory_distribution = safe_result.model_dump(exclude_none=True)
        self._record_registered_goal_fact(
            state=state,
            tool_name="get_inventory_distribution",
            arguments=arguments,
            safe_data=safe_result.model_dump(exclude_none=True),
        )
        state.tool_results.append(
            {"kind": "inventory_distribution", "summary": self._distribution_summary(safe_result)}
        )
        self._record_tool_message(
            state,
            "get_inventory_distribution",
            self._safe_tool_summary(
                "get_inventory_distribution", safe_result.model_dump(exclude_none=True)
            ),
        )
        return ChatResponse(
            agentSessionId=agent_session_id,
            answer=self._format_inventory_distribution(safe_result),
            cards=[self._inventory_distribution_card(safe_result)] if safe_result.groups else [],
        )

    def _answer_products_without_recent_assay(
        self,
        agent_session_id: str,
        state: WarehouseAgentState,
        trace_id: str | None,
        request_id: str | None,
        message_id: str | None,
        arguments: dict[str, Any],
    ) -> ChatResponse:
        result = self._call_tool_values(
            agent_session_id=agent_session_id,
            tool_name="query_products_without_recent_assay",
            arguments=arguments,
            trace_id=trace_id,
            request_id=request_id,
            message_id=message_id,
        )
        self._raise_if_cancelled()
        self._raise_if_tool_error_payload(result)
        state.last_products_without_recent_assay = result
        self._record_tool_message(
            state,
            "query_products_without_recent_assay",
            self._safe_tool_summary("query_products_without_recent_assay", result),
        )
        return ChatResponse(
            agentSessionId=agent_session_id,
            answer=self._format_products_without_recent_assay_answer(result),
        )

    def _answer_pallet_lifecycle_tool(
        self,
        request_or_session: ChatRequest | str,
        state: WarehouseAgentState,
        tool_name: str,
        arguments: dict[str, Any],
        trace_id: str | None = None,
        request_id: str | None = None,
        message_id: str | None = None,
    ) -> ChatResponse:
        if isinstance(request_or_session, str):
            agent_session_id = request_or_session
        else:
            agent_session_id = request_or_session.agentSessionId
            trace_id = request_or_session.client.traceId
            request_id = request_or_session.client.requestId
            message_id = request_or_session.messageId
        result = self._call_tool_values(
            agent_session_id=agent_session_id,
            tool_name=tool_name,
            arguments=arguments,
            trace_id=trace_id,
            request_id=request_id,
            message_id=message_id,
        )
        self._raise_if_cancelled()
        self._raise_if_tool_error_payload(result)
        state_field = {
            "query_qr_code_lifecycle": "last_qr_code_lifecycle",
            "query_printed_not_inbound_codes": "last_printed_not_inbound_codes",
            "query_pallet_anomalies": "last_pallet_anomalies",
            "query_pallet_flow_records": "last_pallet_flow_records",
            "query_qr_batch_inbound_completion": "last_qr_batch_inbound_completion",
        }[tool_name]
        setattr(state, state_field, result)
        self._record_tool_message(state, tool_name, self._safe_tool_summary(tool_name, result))
        formatter = {
            "query_qr_code_lifecycle": self._format_qr_code_lifecycle_answer,
            "query_printed_not_inbound_codes": self._format_printed_not_inbound_answer,
            "query_pallet_anomalies": self._format_pallet_anomalies_answer,
            "query_pallet_flow_records": self._format_pallet_flow_records_answer,
            "query_qr_batch_inbound_completion": self._format_qr_batch_inbound_answer,
        }[tool_name]
        return ChatResponse(agentSessionId=agent_session_id, answer=formatter(result))

    def _resolve_warehouse_and_continue(
        self, request: ChatRequest, state: WarehouseAgentState, arguments: dict[str, Any], intent: str = "warehouse_status"
    ) -> ChatResponse:
        result = self._call_tool(request, "resolve_warehouses", arguments)
        self._raise_if_cancelled()
        self._record_tool_message(state, "resolve_warehouses", self._safe_tool_summary("resolve_warehouses", result))
        status = str(result.get("resolutionStatus") or "").upper()
        if status == "AMBIGUOUS":
            pending = self._warehouse_clarification(result, request, intent)
            self._raise_if_cancelled()
            state.pending_clarification = pending
            state.interrupt_status[pending.interrupt_id] = pending.status
            return ChatResponse(
                agentSessionId=request.agentSessionId,
                answer=pending.prompt,
                needsUserSelection=True,
                cards=[self._clarification_card(pending)],
            )
        if status == "NOT_FOUND":
            return ChatResponse(agentSessionId=request.agentSessionId, answer="未找到匹配库位，请换一个库位名称。")
        entity = self._single_warehouse_entity(result)
        if entity is None:
            return ChatResponse(
                agentSessionId=request.agentSessionId,
                answer="库位解析结果不完整，请换一个更准确的库位名称。",
                needsUserSelection=True,
            )
        self._raise_if_cancelled()
        state.selected_warehouse = entity
        if intent == "inventory_distribution":
            distribution_args = self.argument_builder.distribution_arguments_for_state(
                {
                    "productScope": {"type": "ALL"},
                    "warehouseScope": {
                        "type": "SINGLE_WAREHOUSE",
                        "warehouseId": entity.internal_id,
                    },
                    "groupBy": "product",
                    "limit": 20,
                },
                state,
            )
            return self._answer_inventory_distribution(
                request.agentSessionId,
                state,
                request.client.traceId,
                request.client.requestId,
                request.messageId,
                distribution_args,
            )
        if intent == "products_without_recent_assay":
            user_message = self._latest_user_message(state)
            arguments = self.argument_builder.products_without_recent_assay_arguments_for_state(
                {
                    "productScope": {"type": "ALL"},
                    "warehouseScope": {"type": "SINGLE_WAREHOUSE", "warehouseId": entity.internal_id},
                },
                state,
                user_message,
            )
            return self._answer_products_without_recent_assay(
                request.agentSessionId,
                state,
                request.client.traceId,
                request.client.requestId,
                request.messageId,
                arguments,
            )
        if intent == "warehouse_recent_operations":
            return self._answer_warehouse_recent_operations(
                request, state, {"warehouseId": entity.internal_id, "eventTypes": [], "limit": 20}
            )
        if intent == "warehouse_mixed_storage_facts":
            return self._answer_warehouse_mixed_storage_facts(
                request, state, {"warehouseId": entity.internal_id, "factType": "ANY", "limit": 20}
            )
        return self._answer_warehouse_status(
            request.agentSessionId,
            state,
            request.client.traceId,
            request.client.requestId,
            request.messageId,
        )

    def _answer_warehouse_recent_operations(
        self, request: ChatRequest, state: WarehouseAgentState, arguments: dict[str, Any]
    ) -> ChatResponse:
        result = self._call_tool(request, "query_warehouse_recent_operations", arguments)
        self._raise_if_cancelled(); self._raise_if_tool_error_payload(result)
        self._record_tool_message(state, "query_warehouse_recent_operations",
                                  self._safe_tool_summary("query_warehouse_recent_operations", result))
        return ChatResponse(agentSessionId=request.agentSessionId,
                            answer=self._format_warehouse_recent_operations_answer(result))

    def _answer_warehouse_mixed_storage_facts(
        self, request: ChatRequest, state: WarehouseAgentState, arguments: dict[str, Any]
    ) -> ChatResponse:
        result = self._call_tool(request, "query_warehouse_mixed_storage_facts", arguments)
        self._raise_if_cancelled(); self._raise_if_tool_error_payload(result)
        self._record_tool_message(state, "query_warehouse_mixed_storage_facts",
                                  self._safe_tool_summary("query_warehouse_mixed_storage_facts", result))
        return ChatResponse(agentSessionId=request.agentSessionId,
                            answer=self._format_warehouse_mixed_storage_facts_answer(result))

    def _answer_warehouse_status(
        self,
        agent_session_id: str,
        state: WarehouseAgentState,
        trace_id: str | None,
        request_id: str | None,
        message_id: str | None,
        arguments: dict[str, Any] | None = None,
    ) -> ChatResponse:
        tool_arguments = arguments or {"warehouseId": state.selected_warehouse.internal_id}
        result = self._call_tool_values(
            agent_session_id=agent_session_id,
            tool_name="get_warehouse_status",
            arguments=tool_arguments,
            trace_id=trace_id,
            request_id=request_id,
            message_id=message_id,
        )
        safe_result = self._adapt_warehouse_result(result)
        self._raise_if_cancelled()
        state.last_warehouse_result = safe_result.model_dump(exclude_none=True)
        self._record_tool_message(
            state,
            "get_warehouse_status",
            self._safe_tool_summary("get_warehouse_status", safe_result.model_dump(exclude_none=True)),
        )
        return ChatResponse(
            agentSessionId=agent_session_id,
            answer=self._format_warehouse_answer(state.selected_warehouse.display_label, safe_result),
        )

    def _call_tool(self, request: ChatRequest, tool_name: str, arguments: dict[str, Any]) -> dict[str, Any]:
        return self._call_tool_values(
            agent_session_id=request.agentSessionId,
            tool_name=tool_name,
            arguments=arguments,
            trace_id=request.client.traceId,
            request_id=request.client.requestId,
            message_id=request.messageId,
        )

    def _call_tool_values(
        self,
        *,
        agent_session_id: str,
        tool_name: str,
        arguments: dict[str, Any],
        trace_id: str | None,
        request_id: str | None,
        message_id: str | None,
    ) -> dict[str, Any]:
        self._raise_if_cancelled()
        context = current_execution_context()
        if context is None:
            raise ToolGatewayError(
                "AGENT_EXECUTION_CONTEXT_MISSING",
                "当前查询缺少可校验的专家执行上下文。",
            )
        try:
            if not context.authorizes(tool_name):
                raise ExpertBoundaryError("tool is outside the immutable execution boundary")
            self.argument_builder.authorize_tool(context.agent_name, tool_name)
        except ValueError as exc:
            self.metrics.increment(
                "expert_tool_not_allowed_total",
                expert=context.agent_name,
                tool=tool_name,
            )
            raise ToolGatewayError(
                "EXPERT_TOOL_NOT_ALLOWED",
                "当前专家 Agent 无权调用该工具。",
            ) from exc
        started = time.monotonic()
        try:
            report_tool_progress(tool_name)
            result = self.tool_client.call_tool(
                agent_session_id=agent_session_id,
                message_id=message_id,
                tool_name=tool_name,
                arguments=arguments,
                trace_id=trace_id,
                request_id=request_id,
                expert_agent=context.agent_name,
                business_domain=context.business_domain,
                handoff_mode=context.handoff_mode,
                handoff_id=context.handoff_id,
                plan_id=context.plan_id,
                step_id=context.step_id,
            )
        finally:
            self.metrics.observe(
                "tool_call_duration",
                time.monotonic() - started,
                expert=context.agent_name,
                tool=tool_name,
            )
        self._raise_if_cancelled()
        return result

    def _raise_if_cancelled(self) -> None:
        token = current_cancellation_token()
        if token is not None:
            token.raise_if_cancelled()

    def _build_arguments_or_empty(
        self, tool_name: str, text: str, state: WarehouseAgentState
    ) -> dict[str, Any]:
        try:
            return self.argument_builder.build(tool_name=tool_name, user_message=text, state=state)
        except ValueError:
            return {}

    def _product_clarification(
        self,
        result: dict[str, Any],
        intent: str,
        request: ChatRequest | ResumeRequest,
    ) -> PendingClarification:
        self.metrics.increment("hitl_created_total", kind="product")
        raw_options = result.get("options") or result.get("candidates") or []
        options = []
        for idx, option in enumerate(raw_options[:10], start=1):
            label = self._safe_display_label(
                str(option.get("displayLabel") or option.get("productName") or option.get("name") or "候选产品")
            )
            product_id = self._int_value(option, "productId") or self._int_value(option, "id")
            option_type = str(option.get("optionType") or "SINGLE_PRODUCT")
            group_supported = intent == "inventory_distribution" and (
                (option_type == "PRODUCT_TYPE_GROUP" and bool(option.get("productType")))
                or (option_type == "EXACT_PRODUCT_NAME_GROUP" and bool(option.get("productName")))
            )
            supported = product_id is not None if option_type == "SINGLE_PRODUCT" else group_supported
            options.append(
                {
                    "optionId": f"opt_{idx:03d}",
                    "optionType": option_type,
                    "displayLabel": label,
                    "description": self._product_description(option),
                    "supported": supported,
                    "disabledReason": None if supported else option.get("disabledReason") or "当前查询需要选择具体产品规格。",
                    "_internal": {
                        "productId": product_id,
                        "productName": option.get("productName"),
                        "productType": option.get("productType"),
                    },
                }
            )
        prompt = str(result.get("clarificationPrompt") or "该产品名称存在多个匹配项，请选择要查询的具体产品。")
        token = self._new_resume_token()
        return PendingClarification(
            kind="product",
            intent=intent,
            prompt=prompt,
            options=options,
            interrupt_id=self._new_interrupt_id(),
            resume_token_hash=self._hash_resume_token(token),
            resume_token=token,
            expires_at=self._expires_at(),
            **self._pending_agent_binding(),
            user_id=request.user.userId if request.user else None,
        )

    def _warehouse_clarification(
        self,
        result: dict[str, Any],
        request: ChatRequest | ResumeRequest,
        intent: str = "warehouse_status",
        continuation: dict[str, Any] | None = None,
    ) -> PendingClarification:
        self.metrics.increment("hitl_created_total", kind="warehouse")
        raw_options = result.get("options") or result.get("candidates") or []
        options = []
        for idx, option in enumerate(raw_options[:10], start=1):
            label = self._safe_display_label(
                str(option.get("displayLabel") or option.get("warehouseName") or option.get("name") or "候选库位")
            )
            warehouse_id = self._int_value(option, "warehouseId") or self._int_value(option, "id")
            options.append(
                {
                    "optionId": f"opt_{idx:03d}",
                    "optionType": str(option.get("optionType") or "SINGLE_WAREHOUSE"),
                    "displayLabel": label,
                    "description": option.get("matchReason"),
                    "supported": warehouse_id is not None,
                    "_internal": {"warehouseId": warehouse_id},
                }
            )
        prompt = str(result.get("clarificationPrompt") or "该库位名称存在多个匹配项，请选择要查询的库位。")
        token = self._new_resume_token()
        return PendingClarification(
            kind="warehouse",
            intent=intent,
            prompt=prompt,
            options=options,
            interrupt_id=self._new_interrupt_id(),
            resume_token_hash=self._hash_resume_token(token),
            resume_token=token,
            expires_at=self._expires_at(),
            **self._pending_agent_binding(),
            continuation=continuation,
            user_id=request.user.userId if request.user else None,
        )

    def _production_order_clarification(
        self,
        result: dict[str, Any],
        request: ChatRequest,
        intent: str,
    ) -> PendingClarification:
        self.metrics.increment("hitl_created_total", kind="production_order")
        raw_options = result.get("candidates") or []
        options = []
        for idx, option in enumerate(raw_options[:10], start=1):
            if not isinstance(option, dict):
                continue
            label = self._safe_display_label(
                str(option.get("displayCode") or option.get("displayLabel") or "候选生产订单")
            )
            order_ref = self._safe_text(option.get("entityRef"))
            options.append(
                {
                    "optionId": f"opt_{idx:03d}",
                    "optionType": "PRODUCTION_ORDER",
                    "displayLabel": label,
                    "description": self._safe_text(option.get("description")),
                    "supported": bool(order_ref and order_ref.startswith("aer_")),
                    "disabledReason": None if order_ref and order_ref.startswith("aer_") else "该候选缺少有效的受控引用。",
                    "_internal": {"orderRef": order_ref},
                }
            )
        token = self._new_resume_token()
        labels = [str(option["displayLabel"]) for option in options[:5]]
        prompt = "找到多个匹配的生产订单，请选择要继续查询的订单"
        if labels:
            prompt += "：" + "、".join(labels)
        return PendingClarification(
            kind="production_order",
            intent=intent,
            prompt=prompt + "。",
            options=options,
            interrupt_id=self._new_interrupt_id(),
            resume_token_hash=self._hash_resume_token(token),
            resume_token=token,
            expires_at=self._expires_at(),
            **self._pending_agent_binding(),
            user_id=request.user.userId if request.user else None,
        )

    def _answer_selected_production_order(
        self,
        request: ChatRequest,
        state: WarehouseAgentState,
        intent: str,
        order_ref: str,
    ) -> ChatResponse:
        tool_name = {
            "material_pick_trace": "query_material_pick_trace",
            "material_candidates": "query_material_candidates",
            "production_label_completion": "query_production_label_completion",
        }.get(intent, "query_production_order_progress")
        result = self._call_tool(request, tool_name, {"orderRef": order_ref})
        self._raise_if_cancelled()
        self._raise_if_tool_error_payload(result)
        self._record_tool_message(state, tool_name, self._safe_tool_summary(tool_name, result))
        answer = (
            self._format_material_pick_trace_answer(result)
            if tool_name == "query_material_pick_trace"
            else self._format_material_candidates_answer(result)
            if tool_name == "query_material_candidates"
            else self._format_production_label_completion_answer(result)
            if tool_name == "query_production_label_completion"
            else self._format_production_order_progress_answer(result)
        )
        return ChatResponse(agentSessionId=request.agentSessionId, answer=answer)

    def _clarification_card(self, pending: PendingClarification) -> BusinessCard:
        return BusinessCard(
            cardType="candidate_selection",
            title="请选择查询范围",
            prompt=pending.prompt,
            interruptId=pending.interrupt_id,
            interruptKind="CLARIFICATION",
            resumeToken=pending.resume_token,
            expiresAt=pending.expires_at.isoformat(),
            options=[
                UserOption(
                    optionId=str(option["optionId"]),
                    optionType=str(option["optionType"]),
                    displayLabel=str(option["displayLabel"]),
                    description=option.get("description"),
                    supported=bool(option.get("supported", True)),
                    disabledReason=option.get("disabledReason"),
                )
                for option in pending.options
            ],
        )

    def _find_pending_option(
        self, pending: PendingClarification, option_id: str | None, display_label: str | None
    ) -> dict[str, Any] | None:
        for option in pending.options:
            if option_id and option.get("optionId") == option_id:
                return option
            if display_label and option.get("displayLabel") == display_label:
                return option
        return None

    def _new_interrupt_id(self) -> str:
        return f"intr_{secrets.token_hex(12)}"

    def _new_resume_token(self) -> str:
        return f"resume_{secrets.token_urlsafe(32)}"

    def _hash_resume_token(self, token: str) -> str:
        return hashlib.sha256(token.encode("utf-8")).hexdigest()

    def _expires_at(self) -> datetime:
        return datetime.now(timezone.utc) + self.RESUME_TOKEN_TTL

    def _is_expired(self, pending: PendingClarification) -> bool:
        return datetime.now(timezone.utc) >= pending.expires_at

    def _resume_token_matches(self, pending: PendingClarification, resume_token: str | None) -> bool:
        if not resume_token:
            return False
        return secrets.compare_digest(self._hash_resume_token(resume_token), pending.resume_token_hash)

    def _user_matches(self, pending: PendingClarification, request: ResumeRequest) -> bool:
        request_user_id = request.user.userId if request.user else None
        return pending.user_id is None or request_user_id is None or pending.user_id == request_user_id

    def _cached_resume_response(
        self, state: WarehouseAgentState, interrupt_id: str | None, client_request_id: str | None
    ) -> ChatResponse | None:
        if not interrupt_id or not client_request_id:
            return None
        cached = state.interrupt_client_results.get(interrupt_id, {}).get(client_request_id)
        if cached is None:
            return None
        return ChatResponse.model_validate(cached)

    def _cache_resume_response(
        self,
        state: WarehouseAgentState,
        interrupt_id: str | None,
        client_request_id: str | None,
        response: ChatResponse,
    ) -> None:
        if not interrupt_id or not client_request_id:
            return
        state.interrupt_client_results.setdefault(interrupt_id, {})[client_request_id] = response.model_dump()

    def _terminal_interrupt_response(
        self, agent_session_id: str, state: WarehouseAgentState, interrupt_id: str | None
    ) -> ChatResponse:
        status = state.interrupt_status.get(interrupt_id or "")
        return self._status_response(agent_session_id, status)

    def _status_response(self, agent_session_id: str, status: str | None) -> ChatResponse:
        if status == "RESUMED":
            return ChatResponse(agentSessionId=agent_session_id, answer="这个选择已经处理过了。")
        if status == "EXPIRED":
            return ChatResponse(agentSessionId=agent_session_id, answer="这个确认已过期，请重新发起。")
        if status == "CANCELLED":
            return ChatResponse(agentSessionId=agent_session_id, answer="这个任务已取消。")
        if status == "REJECTED":
            return ChatResponse(agentSessionId=agent_session_id, answer="这个确认已被拒绝。")
        return ChatResponse(agentSessionId=agent_session_id, answer="当前没有等待选择的业务候选项，请重新描述要查询的内容。")

    def _single_product_entity(self, result: dict[str, Any]) -> SelectedEntity | None:
        candidate = self._first_candidate(result)
        product_id = (
            self._int_value(result, "productId")
            or self._int_value(candidate, "productId")
            or self._int_value(candidate, "id")
        )
        if product_id is None:
            return None
        label = self._safe_display_label(
            str(
                result.get("displayLabel")
                or candidate.get("displayLabel")
                or candidate.get("productName")
                or candidate.get("name")
                or "所选产品"
            )
        )
        return SelectedEntity(
            internal_id=product_id,
            display_label=label,
            source="resolver",
            metadata={
                "scopeType": "SINGLE_PRODUCT",
                "productName": candidate.get("productName"),
                "productType": candidate.get("productType"),
            },
            entity_type="PRODUCT",
            entity_ref="CURRENT_PRODUCT",
            canonical_name=self._safe_text(candidate.get("productName")),
            resolved_by="RESOLVER",
            resolved_at=datetime.now(timezone.utc),
        )

    def _single_warehouse_entity(self, result: dict[str, Any]) -> SelectedEntity | None:
        candidate = self._first_candidate(result)
        warehouse_id = (
            self._int_value(result, "warehouseId")
            or self._int_value(candidate, "warehouseId")
            or self._int_value(candidate, "id")
        )
        if warehouse_id is None:
            return None
        label = self._warehouse_display_name(
            str(
                result.get("displayLabel")
                or candidate.get("displayLabel")
                or candidate.get("warehouseName")
                or candidate.get("name")
                or "所选库位"
            )
        )
        return SelectedEntity(
            internal_id=warehouse_id,
            display_label=label,
            source="resolver",
            entity_type="WAREHOUSE",
            entity_ref="CURRENT_WAREHOUSE",
            canonical_name=label,
            resolved_by="RESOLVER",
            resolved_at=datetime.now(timezone.utc),
        )

    def _format_inventory_answer(self, label: str, result: SafeInventoryResult) -> str:
        if result.isEmpty:
            return f"未查询到 {label} 的库存记录。"
        stock = result.displayStockInfo
        if stock is None and result.normalizedPallets is not None and result.normalizedLoosePieces is not None:
            stock = f"{result.normalizedPallets}板{result.normalizedLoosePieces}件"
        if stock is None and result.totalEquivalentPieces is None and result.totalWeight is None:
            return f"{label}已查询到库存结果，但当前返回缺少可展示的库存数量字段。"

        parts = [f"{label}当前库存为 {stock}"] if stock is not None else [f"{label}当前库存已查询到"]
        if result.totalEquivalentPieces is not None:
            parts.append(f"折合 {result.totalEquivalentPieces} 件")
        if result.totalWeight is not None:
            parts.append(f"总重量 {result.totalWeight}kg")
        return "，".join(parts) + "。\n需要我帮你查库存分布吗？"

    def _format_inventory_locations(self, label: str, result: SafeInventoryResult) -> str:
        if not result.locations:
            return (
                f"我已确认你问的是{label}，但当前已接入的只读工具只能查询总库存，"
                "还不能准确返回按库位分布。需要新增“库存按库位分布”的只读分析工具后才能回答这个问题。"
            )

        items = []
        for index, location in enumerate(result.locations[:5], start=1):
            details = []
            if location.displayStockInfo:
                details.append(location.displayStockInfo)
            if location.totalEquivalentPieces is not None:
                details.append(f"折合 {location.totalEquivalentPieces} 件")
            if location.totalWeight is not None:
                details.append(f"总重量 {location.totalWeight}kg")
            suffix = f"：{'，'.join(details)}" if details else ""
            items.append(f"{index}. {self._warehouse_display_name(location.warehouseName)}{suffix}")
        return f"{label}目前主要存放在以下库位：\n" + "\n".join(items)

    def _format_inventory_distribution(self, result: SafeInventoryDistributionResult) -> str:
        if not result.groups:
            if result.filterSummary:
                return f"未查询到{result.scopeLabel}中符合{result.filterSummary}条件的当前在库库存分布。"
            return f"未查询到{result.scopeLabel}的当前在库库存分布。"
        if result.groupBy == "warehouse":
            heading = f"{result.scopeLabel}当前库存主要存放在以下库位："
        elif result.groupBy == "product":
            if self._is_warehouse_scope_distribution(result):
                return self._append_distribution_limitations(
                    self._format_warehouse_inventory_summary(result), result
                )
            heading = f"{result.scopeLabel}当前库存按产品分布如下："
        else:
            heading = f"{result.scopeLabel}当前库存按库位和产品分布如下："
        top_groups = "、".join(group.groupLabel for group in result.groups[:3])
        summary = (
            f"{heading}\n"
            f"共 {len(result.groups)} 个分组，合计 {result.totalStockText}"
            f"，涉及 {result.palletCount} 个托盘。"
        )
        if top_groups:
            summary += f"主要分组：{top_groups}。"
        risk_summary = self._distribution_risk_summary(result)
        if risk_summary:
            summary += "\n存在风险提示，详见卡片。"
        summary += "\n详细分布见下方卡片。"
        return self._append_distribution_limitations(summary, result)

    def _append_distribution_limitations(
        self,
        answer: str,
        result: SafeInventoryDistributionResult,
    ) -> str:
        limitations = [
            note for note in result.notes
            if "尚未由业务负责人确认" in note or "不作为权威结论" in note
        ]
        if not limitations:
            return answer
        return answer + "\n数据口径限制：" + "；".join(limitations)

    def _inventory_distribution_card(self, result: SafeInventoryDistributionResult) -> BusinessCard:
        fields = []
        for index, group in enumerate(result.groups):
            field = {
                "kind": "distribution_group",
                "label": f"{index + 1}. {group.groupLabel}",
                "value": group.stockText,
                "percentage": str(group.percentageText),
                "palletCount": f"{group.palletCount}个托盘",
            }
            if group.latestInboundTime:
                field["latestInboundTime"] = f"最近入库 {group.latestInboundTime}"
            if group.riskLabels:
                field["riskText"] = "；".join(group.riskLabels)
            if any("无化验" in risk for risk in group.riskLabels):
                product_name = self._distribution_group_product_name(group)
                if product_name:
                    field["actionKind"] = "create_assay"
                    field["actionLabel"] = "去补充"
                    field["actionProductName"] = product_name
                    if group.latestInboundTime:
                        field["actionSampleDate"] = str(group.latestInboundTime)
            fields.append(field)
        if not self._is_warehouse_scope_distribution(result):
            risk_summary = self._distribution_risk_summary(result)
            if risk_summary:
                fields.append({"kind": "risk_summary", "label": "风险提示", "value": risk_summary})
        return BusinessCard(
            cardType="inventory_distribution",
            title=f"{result.scopeLabel}库存分布",
            fields=fields,
        )

    def _is_warehouse_scope_distribution(self, result: SafeInventoryDistributionResult) -> bool:
        return result.groupBy == "product" and "库位" in self._safe_text(result.scopeLabel)

    def _format_warehouse_inventory_summary(self, result: SafeInventoryDistributionResult) -> str:
        label = self._safe_text(result.scopeLabel).replace("的全部产品", "").strip() or result.scopeLabel
        product_count = self._display_count(result.productCount, len(result.groups))
        pallet_count = self._display_count(result.palletCount, 0)
        names = self._top_distribution_names(result, limit=2)

        summary = f"{label}当前有 {product_count} 类产品，共 {pallet_count} 个托盘"
        if names:
            connector = "和" if len(names) == 2 else ""
            summary += f"，主要是{connector.join(names)}"
        summary += "。"

        risk_summary = self._warehouse_inventory_risk_sentence(result)
        if risk_summary:
            summary += risk_summary
        summary += "详情见下方卡片。"
        return summary

    def _top_distribution_names(self, result: SafeInventoryDistributionResult, limit: int) -> list[str]:
        names: list[str] = []
        seen: set[str] = set()
        for group in result.groups:
            name = self._distribution_group_product_name(group) or self._safe_text(group.groupLabel)
            if not name or name in seen:
                continue
            names.append(name)
            seen.add(name)
            if len(names) >= limit:
                break
        return names

    def _warehouse_inventory_risk_sentence(self, result: SafeInventoryDistributionResult) -> str:
        risk_labels = [risk for group in result.groups for risk in group.riskLabels]
        if not risk_labels:
            return ""
        if any("无化验" in risk for risk in risk_labels):
            return "其中存在无化验库存，出库前建议先核对化验状态。"
        return "其中存在库存风险提示，出库前建议先核对相关状态。"

    def _display_count(self, value: int | float | str | None, fallback: int) -> str:
        if value is None or value == "":
            return str(fallback)
        if isinstance(value, float) and value.is_integer():
            return str(int(value))
        return str(value)

    def _distribution_risk_summary(self, result: SafeInventoryDistributionResult) -> str | None:
        risk_counts: dict[str, int] = {}
        for group in result.groups:
            for risk in group.riskLabels:
                risk_counts[risk] = risk_counts.get(risk, 0) + 1
        if not risk_counts:
            return None
        return "；".join(f"{risk}（{count}项）" for risk, count in sorted(risk_counts.items()))

    def _distribution_group_product_name(self, group: SafeInventoryDistributionGroup) -> str | None:
        return self._safe_text(group.canonicalProductName)

    def _format_warehouse_answer(self, label: str, result: SafeWarehouseResult) -> str:
        display_name = self._warehouse_display_name(result.warehouseName or label)
        fields = []
        if result.status:
            fields.append(f"状态 {result.status}")
        if result.maxCapacity is not None:
            fields.append(f"最大容量 {result.maxCapacity}")
        if result.currentPallets is not None:
            fields.append(f"当前托盘数 {result.currentPallets}")
        if result.currentOccupancy is not None:
            fields.append(f"当前占用 {result.currentOccupancy}")
        if result.remainingCapacity is not None:
            fields.append(f"剩余容量 {result.remainingCapacity}")
        if result.occupancyRate is not None:
            fields.append(f"占用率 {self._format_rate(result.occupancyRate)}")
        if fields:
            return f"{display_name}当前状态：" + "，".join(fields) + "。"
        return f"{display_name}当前工具已返回库位状态，但缺少容量明细字段。"

    def _format_pallet_answer(self, result: dict[str, Any]) -> str:
        info = result.get("palletInfo") or result.get("baseInfo") or result
        info = info if isinstance(info, dict) else {}
        status = self._safe_text(info.get("status") or info.get("bindStatus")) or "状态未明确"
        location = result.get("inventory", {}).get("warehouseName") if isinstance(result.get("inventory"), dict) else None
        location = self._safe_text(location)
        if location:
            return f"该托盘当前状态为 {status}，当前位置在 {location}。"
        return f"该托盘当前状态为 {status}。"

    def _adapt_assay_status(self, result: dict[str, Any], label: str) -> SafeAssayReport:
        if "hasAssay" in result and "judgeLabel" in result and "standardLabel" in result:
            return SafeAssayReport.model_validate(result)

        assay = self._dict_value(result.get("assay"))
        product_label = self._safe_text(label)
        if not product_label or product_label in {"所选产品", "该产品"}:
            product_label = self._safe_text(result.get("productLabel") or assay.get("productName")) or "所选产品"
        sample_date = self._safe_text(result.get("sampleDate") or assay.get("sampleDate")) or None
        result_status = self._safe_text(result.get("status")) or ""
        if result.get("needsAssay") is True or result_status.upper() in {"NO_DATA", "NOT_FOUND"}:
            return SafeAssayReport(
                hasAssay=False,
                productLabel=product_label,
                sampleDate=sample_date,
                judgeLabel="未查询到化验记录",
                judgeExplanation="对应产品和生产日期暂无化验数据，当前不能判断合格或不合格。",
                standardLabel="—",
            )

        judge_status = self._safe_text(
            result.get("judgeResult") or result.get("result") or assay.get("judgeResult") or assay.get("isQualified")
        )
        judge_label = self._assay_status_label(judge_status)
        standard_label = self._assay_standard_label(result, assay)
        metrics = self._assay_metrics_from_status(result, assay, judge_status)
        explanation = self._assay_judge_explanation(judge_status, self._safe_text(assay.get("judgeMessage")))
        notes: list[str] = []
        if self._canonical_assay_status(judge_status) == "NO_STANDARD":
            notes.append("未配置适用标准不等同于化验不合格。")
        elif self._canonical_assay_status(judge_status) == "MULTIPLE_CANDIDATES":
            notes.append("匹配到多个候选标准，需要人工确认采用哪一个标准。")
        return SafeAssayReport(
            productLabel=product_label,
            sampleDate=sample_date,
            judgeLabel=judge_label,
            judgeExplanation=explanation or None,
            standardLabel=standard_label,
            metrics=metrics,
            notes=notes,
        )

    def _adapt_assay_report_detail(self, result: dict[str, Any]) -> SafeAssayReport:
        product_label = self._safe_text(result.get("productLabel")) or "所选产品"
        judge_status = self._safe_text(result.get("judgeStatus"))
        source_judge_label = self._safe_text(result.get("judgeLabel"))
        judge_label = self._assay_status_label(judge_status or source_judge_label or "")
        standard_label = self._safe_text(result.get("standardLabel"))
        if not standard_label:
            standard_label = "未配置适用标准" if judge_label in {"无标准", "暂无法判定"} else "未提供标准快照"
        metrics: list[SafeAssayMetric] = []
        for index, raw_metric in enumerate(result.get("metrics") or []):
            metric = self._dict_value(raw_metric)
            metric_name = self._safe_text(metric.get("metricName")) or f"指标{index + 1}"
            actual = self._safe_text(metric.get("actualValueText")) or "未填写"
            standard = self._safe_text(metric.get("standardRangeText")) or (
                "未配置适用标准" if standard_label == "未配置适用标准" else "未提供"
            )
            metric_result = self._safe_text(metric.get("resultLabel")) or "未判定"
            if metric_result in {"无标准", "NO_STANDARD"}:
                metric_result = "未判定"
            metrics.append(
                SafeAssayMetric(
                    metricName=metric_name,
                    actualValueText=actual,
                    standardRangeText=standard,
                    resultLabel=self._sanitize_assay_business_text(metric_result),
                    reason=self._safe_text(metric.get("reason")) or None,
                )
            )
        raw_notes = result.get("notes") if isinstance(result.get("notes"), list) else []
        canonical_status = self._canonical_assay_status(judge_status or source_judge_label)
        notes: list[str] = []
        for item in raw_notes:
            note = self._safe_text(item)
            if not note or re.search(r"(?i)(reportRef|内部化验\s*ID|受控.*引用)", note):
                continue
            if "无标准" in note and canonical_status != "NO_STANDARD":
                continue
            notes.append(self._sanitize_assay_business_text(note))
            if len(notes) == 3:
                break
        return SafeAssayReport(
            productLabel=product_label,
            sampleDate=self._safe_text(result.get("sampleDate")) or None,
            judgeLabel=self._sanitize_assay_business_text(judge_label),
            judgeExplanation=self._assay_judge_explanation(
                judge_status or judge_label,
                self._safe_text(result.get("judgeMessage")),
            ) or None,
            standardLabel=standard_label,
            metrics=metrics,
            notes=notes,
        )

    def _assay_metrics_from_status(
        self,
        result: dict[str, Any],
        assay: dict[str, Any],
        judge_status: str,
    ) -> list[SafeAssayMetric]:
        snapshot = self._dict_value(assay.get("standardSnapshot"))
        standard_items = snapshot.get("items") if isinstance(snapshot.get("items"), list) else []
        items_by_code = {
            self._normalize_assay_metric_code(
                self._safe_text(self._dict_value(item).get("metricCode"))
            ): self._dict_value(item)
            for item in standard_items
            if self._safe_text(self._dict_value(item).get("metricCode"))
        }
        raw_failed = result.get("failedMetrics") if isinstance(result.get("failedMetrics"), list) else assay.get("failedMetrics")
        failed_metrics = raw_failed if isinstance(raw_failed, list) else []
        failed_codes = {
            self._normalize_assay_metric_code(
                self._safe_text(self._dict_value(item).get("metricCode") or self._dict_value(item).get("metric"))
            )
            for item in failed_metrics
        }
        failed_by_code = {
            self._normalize_assay_metric_code(
                self._safe_text(self._dict_value(item).get("metricCode") or self._dict_value(item).get("metric"))
            ): self._dict_value(item)
            for item in failed_metrics
        }
        canonical_status = self._canonical_assay_status(judge_status)
        metrics: list[SafeAssayMetric] = []
        for metric_code, value_key, metric_name in self.ASSAY_METRICS:
            actual_value = assay.get(value_key)
            standard = items_by_code.get(metric_code)
            unit = (self._safe_text(standard.get("unit")) or "") if standard else ""
            actual_text = self._assay_scalar_text(actual_value, unit) or "未填写"
            standard_text = self._assay_standard_range_text(standard) if standard else "未配置适用标准"
            if actual_value is None or actual_value == "":
                metric_result = "未填写"
            elif metric_code in failed_codes:
                metric_result = "不合格"
            elif canonical_status == "NO_STANDARD" or standard is None:
                metric_result = "未判定"
            elif canonical_status == "MULTIPLE_CANDIDATES":
                metric_result = "待人工确认"
            else:
                metric_result = "合格"
            failed = failed_by_code.get(metric_code, {})
            metrics.append(
                SafeAssayMetric(
                    metricName=metric_name,
                    actualValueText=actual_text,
                    standardRangeText=standard_text or "未提供",
                    resultLabel=metric_result,
                    reason=self._safe_text(failed.get("reason")) or None,
                )
            )
        return metrics

    def _adapt_assay_records(self, result: dict[str, Any]) -> dict[str, Any]:
        records = result.get("records") if isinstance(result.get("records"), list) else []
        safe_records: list[dict[str, Any]] = []
        for raw_record in records[:50]:
            record = self._dict_value(raw_record)
            judge_label = self._safe_text(record.get("judgeLabel")) or self._assay_status_label(
                self._safe_text(record.get("judgeStatus"))
            )
            standard_label = self._safe_text(record.get("standardLabel"))
            if not standard_label and judge_label in {"无标准", "暂无法判定"}:
                standard_label = "未配置适用标准"
            safe_records.append(
                {
                    "productLabel": self._safe_text(record.get("productLabel")) or "产品未明",
                    "sampleDate": self._safe_text(record.get("sampleDate")) or "日期未明",
                    "judgeLabel": self._sanitize_assay_business_text(judge_label or "结论未明"),
                    "failedMetricText": self._safe_text(record.get("failedMetricText")),
                    "standardLabel": standard_label,
                    "testerLabel": self._safe_text(record.get("testerLabel")),
                }
            )
        safe_result = {
            "scopeLabel": self._safe_text(result.get("scopeLabel")),
            "dateRangeLabel": self._safe_text(result.get("dateRangeLabel")),
            "total": self._first_scalar([result], "total") or 0,
            "passCount": self._first_scalar([result], "passCount") or 0,
            "failedCount": self._first_scalar([result], "failedCount") or 0,
            "noStandardCount": self._first_scalar([result], "noStandardCount") or 0,
            "multipleCandidatesCount": self._first_scalar([result], "multipleCandidatesCount") or 0,
            "summaryText": self._sanitize_assay_business_text(self._safe_text(result.get("summaryText"))),
            "records": safe_records,
        }
        return safe_result

    def _assay_status_label(self, value: str) -> str:
        canonical = self._canonical_assay_status(value)
        labels = {
            "PASS": "合格",
            "FAIL": "不合格",
            "NO_STANDARD": "暂无法判定",
            "MULTIPLE_CANDIDATES": "待人工确认",
            "NO_ASSAY": "未查询到化验记录",
        }
        if canonical in labels:
            return labels[canonical]
        safe = self._sanitize_assay_business_text(self._safe_text(value))
        if safe and not re.fullmatch(r"[A-Z][A-Z0-9_]*", safe):
            return safe
        return "未知"

    def _canonical_assay_status(self, value: str) -> str:
        normalized = (self._safe_text(value) or "").upper()
        aliases = {
            "QUALIFIED": "PASS",
            "合格": "PASS",
            "UNQUALIFIED": "FAIL",
            "FAILED": "FAIL",
            "不合格": "FAIL",
            "无标准": "NO_STANDARD",
            "暂无法判定": "NO_STANDARD",
            "标准多候选": "MULTIPLE_CANDIDATES",
            "待人工确认": "MULTIPLE_CANDIDATES",
        }
        return aliases.get(normalized, normalized)

    def _assay_standard_label(self, result: dict[str, Any], assay: dict[str, Any]) -> str:
        applied = self._dict_value(result.get("appliedStandard") or assay.get("appliedStandard"))
        name = self._safe_text(
            assay.get("appliedStandardName") or applied.get("standardName") or applied.get("name")
        )
        version = assay.get("appliedStandardVersion")
        if version is None:
            version = applied.get("version")
        if not name:
            return "未配置适用标准"
        return f"{name} v{version}" if version not in {None, ""} else name

    def _assay_judge_explanation(self, status: str, fallback: str) -> str:
        canonical = self._canonical_assay_status(status)
        if canonical == "NO_STANDARD":
            return "当前产品未匹配到启用中的化验标准，因此不能判定合格或不合格。"
        if canonical == "MULTIPLE_CANDIDATES":
            return "当前匹配到多个候选标准，需要人工确认采用标准后再判定。"
        return self._sanitize_assay_business_text(fallback)

    def _normalize_assay_metric_code(self, value: str) -> str:
        aliases = {camel: code for code, camel, _ in self.ASSAY_METRICS}
        aliases.update(
            {
                "dry_weight": "dry_weight_loss",
                "ph_value": "ph",
            }
        )
        return aliases.get(value, value)

    def _assay_scalar_text(self, value: Any, unit: str | None = "") -> str:
        if value is None or value == "":
            return ""
        if isinstance(value, float) and value.is_integer():
            text = str(int(value))
        else:
            text = str(value)
        return text + (unit or "")

    def _assay_standard_range_text(self, standard: dict[str, Any]) -> str:
        unit = self._safe_text(standard.get("unit")) or ""
        minimum = self._assay_scalar_text(standard.get("minValue"))
        maximum = self._assay_scalar_text(standard.get("maxValue"))
        compare_type = self._safe_text(standard.get("compareType")).lower()
        if compare_type == "lte" and maximum:
            return f"≤ {maximum}{unit}"
        if compare_type == "lt" and maximum:
            return f"< {maximum}{unit}"
        if compare_type == "gte" and minimum:
            return f"≥ {minimum}{unit}"
        if compare_type == "gt" and minimum:
            return f"> {minimum}{unit}"
        if minimum and maximum:
            return f"{minimum} - {maximum}{unit}"
        return ""

    def _sanitize_assay_business_text(self, value: str) -> str:
        text = self._safe_text(value) or ""
        replacements = (
            ("MULTIPLE_CANDIDATES", "标准多候选"),
            ("NO_STANDARD", "无标准"),
            ("UNQUALIFIED", "不合格"),
            ("QUALIFIED", "合格"),
            ("FAILED", "不合格"),
            ("NO_ASSAY", "无化验记录"),
            ("FAIL", "不合格"),
            ("PASS", "合格"),
        )
        for raw, label in replacements:
            text = text.replace(raw, label)
        text = re.sub(r"(无标准|标准多候选|不合格|合格)[（(]\1[)）]", r"\1", text)
        return text

    def _assay_report_card(self, report: SafeAssayReport) -> BusinessCard | None:
        if not report.hasAssay:
            return None
        title_date = f" {report.sampleDate}" if report.sampleDate else ""
        fields: list[dict[str, Any]] = [
            {
                "kind": "assay_summary",
                "label": "判定结果",
                "value": report.judgeLabel,
                "productLabel": report.productLabel,
                "sampleDate": report.sampleDate or "日期未明",
                "judgeLabel": report.judgeLabel,
                "judgeExplanation": report.judgeExplanation or "",
                "standardLabel": report.standardLabel,
            }
        ]
        for metric in report.metrics:
            fields.append(
                {
                    "kind": "assay_metric",
                    "label": metric.metricName,
                    "value": metric.actualValueText,
                    "metricName": metric.metricName,
                    "actualValueText": metric.actualValueText,
                    "standardRangeText": metric.standardRangeText,
                    "resultLabel": metric.resultLabel,
                    "reason": metric.reason or "",
                }
            )
        for note in report.notes[:3]:
            fields.append({"kind": "assay_note", "label": "说明", "value": note})
        return BusinessCard(
            cardType="assay_report",
            title=f"{report.productLabel}{title_date} 化验报告",
            fields=fields,
        )

    def _assay_history_card(self, result: dict[str, Any]) -> BusinessCard | None:
        records = result.get("records") if isinstance(result.get("records"), list) else []
        if not records:
            return None
        fields: list[dict[str, Any]] = []
        for index, raw_record in enumerate(records[:20], start=1):
            record = self._dict_value(raw_record)
            fields.append(
                {
                    "kind": "assay_history_record",
                    "label": f"{index}. {self._safe_text(record.get('sampleDate')) or '日期未明'}",
                    "value": self._safe_text(record.get("judgeLabel")) or "结论未明",
                    "productLabel": self._safe_text(record.get("productLabel")) or "产品未明",
                    "sampleDate": self._safe_text(record.get("sampleDate")) or "日期未明",
                    "judgeLabel": self._safe_text(record.get("judgeLabel")) or "结论未明",
                    "standardLabel": self._safe_text(record.get("standardLabel")) or "未配置适用标准",
                    "failedMetricText": self._safe_text(record.get("failedMetricText")),
                    "testerLabel": self._safe_text(record.get("testerLabel")),
                }
            )
        scope = self._safe_text(result.get("scopeLabel")) or "所选范围"
        date_range = self._safe_text(result.get("dateRangeLabel")) or "历史"
        return BusinessCard(cardType="assay_history", title=f"{scope} · {date_range}化验", fields=fields)

    def _format_assay_answer(self, label: str, result: dict[str, Any]) -> str:
        report = self._adapt_assay_status(result, label)
        if not report.hasAssay:
            return f"{report.productLabel}没有查询到对应日期的化验记录，不能判断合格或不合格。"
        if report.judgeLabel == "暂无法判定":
            date_text = f" {report.sampleDate}" if report.sampleDate else ""
            return (
                f"已找到{report.productLabel}{date_text}的化验记录。当前未匹配到启用中的化验标准，"
                "实测数据已列在下方卡片中，但暂时不能判定合格或不合格。"
            )
        date_text = f" {report.sampleDate}" if report.sampleDate else ""
        return f"{report.productLabel}{date_text}的化验判定为{report.judgeLabel}。具体指标和采用标准见下方卡片。"

    def _format_assay_records_answer(self, result: dict[str, Any]) -> str:
        self._raise_if_tool_error_payload(result)
        result = self._adapt_assay_records(result)
        summary = self._safe_text(result.get("summaryText"))
        if not summary:
            scope = self._safe_text(result.get("scopeLabel")) or "所选范围"
            date_range = self._safe_text(result.get("dateRangeLabel")) or "指定日期范围"
            total = self._first_scalar([result], "total") or 0
            summary = f"{date_range}{scope}共有 {total} 条化验记录。"
        records = result.get("records") if isinstance(result.get("records"), list) else []
        if not records:
            return summary
        lines = [summary, ""]
        for index, raw_record in enumerate(records[:5], start=1):
            record = self._dict_value(raw_record)
            sample_date = self._safe_text(record.get("sampleDate")) or "日期未明"
            product = self._safe_text(record.get("productLabel")) or "产品未明"
            judge = self._safe_text(record.get("judgeLabel")) or "结论未明"
            failed = self._safe_text(record.get("failedMetricText"))
            standard = self._safe_text(record.get("standardLabel"))
            details = [sample_date, product, judge]
            if failed:
                details.append(f"异常指标：{failed}")
            if standard:
                details.append(f"标准：{standard}")
            lines.append(f"{index}. " + "，".join(details))
        remaining = len(records) - 5
        total = self._first_scalar([result], "total")
        if isinstance(total, (int, float)) and total > 5:
            remaining = int(total) - 5
        if remaining > 0:
            lines.append(f"还有 {remaining} 条，可继续分页查看。")
        return "\n".join(lines)

    def _format_assay_report_detail_answer(self, result: dict[str, Any]) -> str:
        self._raise_if_tool_error_payload(result)
        report = (
            SafeAssayReport.model_validate(result)
            if "hasAssay" in result and "judgeLabel" in result
            else self._adapt_assay_report_detail(result)
        )
        date_text = f" {report.sampleDate}" if report.sampleDate else ""
        lines = [
            f"{report.productLabel}{date_text}的化验判定为{report.judgeLabel}。具体指标和采用标准见下方卡片。"
        ]
        if report.judgeExplanation:
            lines.append(report.judgeExplanation)
        return "\n".join(lines)

    def _format_assay_abnormalities_answer(self, result: dict[str, Any]) -> str:
        self._raise_if_tool_error_payload(result)
        summary = self._safe_text(result.get("summaryText"))
        if not summary:
            scope = self._safe_text(result.get("scopeLabel")) or "所选范围"
            date_range = self._safe_text(result.get("dateRangeLabel")) or "指定日期范围"
            total = self._first_scalar([result], "total") or 0
            summary = f"{date_range}{scope}发现 {total} 条化验质量异常。"
        groups = result.get("groups") if isinstance(result.get("groups"), list) else []
        if not groups:
            return summary
        lines = [summary]
        for index, raw_group in enumerate(groups[:5], start=1):
            group = self._dict_value(raw_group)
            label = self._safe_text(group.get("groupLabel")) or "未命名分组"
            total = self._first_scalar([group], "total") or 0
            failed = self._first_scalar([group], "failedCount") or 0
            no_standard = self._first_scalar([group], "noStandardCount") or 0
            multiple = self._first_scalar([group], "multipleCandidatesCount") or 0
            latest = self._safe_text(group.get("latestSampleDate"))
            parts = [f"{label}：{total} 条"]
            if failed:
                parts.append(f"不合格 {failed} 条")
            if no_standard:
                parts.append(f"无标准 {no_standard} 条")
            if multiple:
                parts.append(f"标准多候选 {multiple} 条")
            if latest:
                parts.append(f"最近采样 {latest}")
            lines.append(f"{index}. " + "，".join(parts))
        remaining = len(groups) - 5
        if remaining > 0:
            lines.append(f"还有 {remaining} 个分组，可继续缩小范围查看。")
        return "\n".join(lines)

    def _format_products_without_recent_assay_answer(self, result: dict[str, Any]) -> str:
        self._raise_if_tool_error_payload(result)
        summary = self._safe_text(result.get("summaryText"))
        if not summary:
            scope = self._safe_text(result.get("scopeLabel")) or "当前在库产品"
            warehouse_scope = self._safe_text(result.get("warehouseScopeLabel")) or "全部库位"
            date_range = self._safe_text(result.get("dateRangeLabel")) or "指定日期范围"
            total_groups = self._first_scalar([result], "totalGroups") or 0
            summary = f"{date_range}{warehouse_scope}中{scope}共有 {total_groups} 个当前在库分组缺少有效化验。"
        groups = result.get("groups") if isinstance(result.get("groups"), list) else []
        if not groups:
            return summary
        lines = [summary]
        for index, raw_group in enumerate(groups[:5], start=1):
            group = self._dict_value(raw_group)
            label = self._safe_text(group.get("groupLabel")) or "未命名分组"
            stock = self._safe_text(group.get("stockText"))
            latest = self._safe_text(group.get("latestInboundTime"))
            risk_labels = group.get("riskLabels") if isinstance(group.get("riskLabels"), list) else []
            risks = "、".join([risk for value in risk_labels if (risk := self._safe_text(value))])
            parts = [label]
            if stock:
                parts.append(f"库存 {stock}")
            if latest:
                parts.append(f"最近入库 {latest}")
            if risks:
                parts.append(risks)
            lines.append(f"{index}. " + "，".join(parts))
        remaining = len(groups) - 5
        if remaining > 0:
            lines.append(f"还有 {remaining} 个分组，可继续缩小范围查看。")
        notes = result.get("notes") if isinstance(result.get("notes"), list) else []
        if any("无标准" in str(note) for note in notes):
            lines.append("注意：无标准表示已有化验但无法自动判定，不等同于无化验。")
        return "\n".join(lines)

    def _format_assay_standard_coverage_answer(self, result: dict[str, Any]) -> str:
        self._raise_if_tool_error_payload(result)
        summary = self._safe_text(result.get("summaryText"))
        if not summary:
            scope = self._safe_text(result.get("scopeLabel")) or "当前在库产品"
            total_groups = self._first_scalar([result], "totalGroups") or 0
            summary = f"{scope}共有 {total_groups} 个当前在库产品未绑定有效质量标准。"
        groups = result.get("groups") if isinstance(result.get("groups"), list) else []
        if not groups:
            return summary
        lines = [summary]
        for index, raw_group in enumerate(groups[:5], start=1):
            group = self._dict_value(raw_group)
            label = self._safe_text(group.get("groupLabel")) or self._safe_text(group.get("productLabel")) or "未命名产品"
            coverage = self._safe_text(group.get("coverageLabel"))
            stock = self._safe_text(group.get("affectedStockText"))
            risk_labels = group.get("riskLabels") if isinstance(group.get("riskLabels"), list) else []
            risks = "、".join([risk for value in risk_labels if (risk := self._safe_text(value))])
            parts = [label]
            if coverage:
                parts.append(coverage)
            if stock:
                parts.append(f"受影响库存 {stock}")
            if risks:
                parts.append(risks)
            lines.append(f"{index}. " + "，".join(parts))
        remaining = len(groups) - 5
        if remaining > 0:
            lines.append(f"还有 {remaining} 个分组，可继续缩小范围查看。")
        lines.append("注意：无标准表示无法自动判定化验合格性，不等同于化验不合格。")
        return "\n".join(lines)

    def _format_qr_code_lifecycle_answer(self, result: dict[str, Any]) -> str:
        code = self._safe_text(result.get("codeLabel")) or "该二维码"
        status = self._safe_text(result.get("currentStatusLabel")) or "状态未明"
        parts = [f"{code}当前状态为{status}"]
        product = self._safe_text(result.get("productLabel"))
        warehouse = self._safe_text(result.get("warehouseLabel"))
        quantity = self._safe_text(result.get("quantityText"))
        if product:
            parts.append(f"产品：{product}")
        if warehouse:
            parts.append(f"位置：{warehouse}")
        if quantity:
            parts.append(f"数量：{quantity}")
        lines = ["，".join(parts) + "。"]
        timeline = result.get("timeline") if isinstance(result.get("timeline"), list) else []
        for index, raw_event in enumerate(timeline[:5], start=1):
            event = self._dict_value(raw_event)
            time = self._safe_text(event.get("time")) or "时间未明"
            label = self._safe_text(event.get("eventLabel")) or "流转事件"
            target = self._safe_text(event.get("toWarehouseLabel"))
            lines.append(f"{index}. {time}：{label}" + (f"，到{target}" if target else ""))
        risks = result.get("riskLabels") if isinstance(result.get("riskLabels"), list) else []
        safe_risks = [self._safe_text(value) for value in risks if self._safe_text(value)]
        if safe_risks:
            lines.append("风险：" + "、".join(safe_risks))
        return "\n".join(lines)

    def _format_printed_not_inbound_answer(self, result: dict[str, Any]) -> str:
        self._raise_if_tool_error_payload(result)
        summary = self._safe_text(result.get("summaryText")) or "已查询到打印标签入库情况。"
        groups = result.get("groups") if isinstance(result.get("groups"), list) else []
        lines = [summary]
        for index, raw_group in enumerate(groups[:5], start=1):
            group = self._dict_value(raw_group)
            label = self._safe_text(group.get("groupLabel")) or "未命名分组"
            not_inbound = self._first_scalar([group], "notInboundCount") or 0
            rate = self._safe_text(group.get("completionRateText"))
            lines.append(f"{index}. {label}：{not_inbound} 个未完成入库" + (f"，完成率 {rate}" if rate else ""))
        return "\n".join(lines)

    def _format_pallet_anomalies_answer(self, result: dict[str, Any]) -> str:
        self._raise_if_tool_error_payload(result)
        summary = self._safe_text(result.get("summaryText")) or "已查询到托盘异常情况。"
        lines = [summary]
        groups = result.get("groups") if isinstance(result.get("groups"), list) else []
        for index, raw_group in enumerate(groups[:5], start=1):
            group = self._dict_value(raw_group)
            label = self._safe_text(group.get("groupLabel")) or "未命名异常"
            count = self._first_scalar([group], "count") or 0
            lines.append(f"{index}. {label}：{count} 项")
        notes = result.get("notes") if isinstance(result.get("notes"), list) else []
        safe_notes = [self._safe_text(value) for value in notes if self._safe_text(value)]
        if safe_notes:
            lines.append("说明：" + "；".join(safe_notes[:2]))
        return "\n".join(lines)

    def _format_pallet_flow_records_answer(self, result: dict[str, Any]) -> str:
        self._raise_if_tool_error_payload(result)
        summary = self._safe_text(result.get("summaryText")) or "已查询到托盘流转记录。"
        lines = [summary]
        records = result.get("records") if isinstance(result.get("records"), list) else []
        for index, raw_record in enumerate(records[:5], start=1):
            record = self._dict_value(raw_record)
            time = self._safe_text(record.get("time")) or "时间未明"
            label = self._safe_text(record.get("eventLabel")) or "流转事件"
            code = self._safe_text(record.get("codeLabel"))
            lines.append(f"{index}. {time}：{label}" + (f"，托盘 {code}" if code else ""))
        return "\n".join(lines)

    def _format_qr_batch_inbound_answer(self, result: dict[str, Any]) -> str:
        self._raise_if_tool_error_payload(result)
        summary = self._safe_text(result.get("summaryText")) or "已查询到二维码批次入库完成率。"
        examples = result.get("unfinishedExamples") if isinstance(result.get("unfinishedExamples"), list) else []
        safe_examples = [self._safe_text(value) for value in examples if self._safe_text(value)]
        if safe_examples:
            return summary + "\n尚未完成入库的示例：" + "、".join(safe_examples[:5])
        return summary

    def _inventory_summary(self, result: SafeInventoryResult) -> str:
        if result.displayStockInfo:
            return result.displayStockInfo
        if result.normalizedPallets is not None and result.normalizedLoosePieces is not None:
            return f"{result.normalizedPallets}板{result.normalizedLoosePieces}件"
        if result.totalEquivalentPieces is not None:
            return f"折合 {result.totalEquivalentPieces} 件"
        return "缺少可展示的库存数量字段"

    def _distribution_summary(self, result: SafeInventoryDistributionResult) -> str:
        return f"{result.totalStockText}，分布于 {result.warehouseCount} 个库位"

    def _adapt_inventory_distribution(
        self,
        raw: dict[str, Any],
        arguments: dict[str, Any] | None = None,
        state: WarehouseAgentState | None = None,
    ) -> SafeInventoryDistributionResult:
        root = raw if isinstance(raw, dict) else {}
        self._raise_if_tool_error_payload(root)
        groups = []
        for raw_group in root.get("groups") if isinstance(root.get("groups"), list) else []:
            group = self._dict_value(raw_group)
            warehouse_label = self._first_safe_text([group], "warehouseLabel")
            canonical_product_name = self._first_safe_text([group], "canonicalProductName")
            product_label = self._first_safe_text([group], "productLabel")
            group_label = self._first_safe_text([group], "groupLabel") or warehouse_label or product_label
            stock_text = self._first_safe_text([group], "stockText")
            percentage = self._first_safe_text([group], "percentageText")
            equivalent = self._first_scalar([group], "totalEquivalentPieces")
            pallet_count = self._first_scalar([group], "palletCount")
            if not group_label or not stock_text or percentage is None or equivalent is None or pallet_count is None:
                continue
            risk_labels = [
                text for value in group.get("riskLabels", [])
                if (text := self._safe_text(value)) is not None
            ] if isinstance(group.get("riskLabels"), list) else []
            groups.append(
                SafeInventoryDistributionGroup(
                    groupLabel=group_label,
                    warehouseLabel=warehouse_label,
                    canonicalProductName=canonical_product_name,
                    productLabel=product_label,
                    stockText=stock_text,
                    totalEquivalentPieces=equivalent,
                    palletCount=pallet_count,
                    warehouseCount=self._first_scalar([group], "warehouseCount") or 0,
                    productCount=self._first_scalar([group], "productCount") or 0,
                    percentageText=percentage,
                    latestInboundTime=self._first_safe_text([group], "latestInboundTime"),
                    riskLabels=risk_labels,
                )
            )
        fallback_scope_label = self._distribution_scope_label_from_arguments(arguments)
        warehouse_scope_label = self._distribution_warehouse_scope_label_from_arguments(arguments, state)
        product_label = self._first_safe_text([root], "productLabel")
        if product_label is None or (fallback_scope_label and product_label in {"所选产品", "该产品"}):
            product_label = fallback_scope_label or "所选产品"
        scope_label = self._first_safe_text([root], "scopeLabel")
        if scope_label is None or (fallback_scope_label and scope_label in {"所选产品", "该产品"}):
            scope_label = fallback_scope_label or product_label
        if warehouse_scope_label:
            scope_label = self._combine_distribution_scope_label(scope_label, warehouse_scope_label)
        group_by = self._first_safe_text([root], "groupBy")
        if group_by is None and isinstance(arguments, dict):
            group_by = self._safe_text(arguments.get("groupBy"))
        if group_by not in {"warehouse", "product", "warehouse_product"}:
            group_by = "warehouse"
        total_stock = self._first_safe_text([root], "totalStockText") or "0板0件"
        notes = [
            text for value in root.get("notes", [])
            if (text := self._safe_text(value)) is not None
        ] if isinstance(root.get("notes"), list) else []
        return SafeInventoryDistributionResult(
            scopeLabel=scope_label,
            productLabel=product_label,
            groupBy=group_by,
            totalStockText=total_stock,
            totalEquivalentPieces=self._first_scalar([root], "totalEquivalentPieces") or 0,
            totalWeightText=self._first_safe_text([root], "totalWeightText"),
            warehouseCount=self._first_scalar([root], "warehouseCount") or 0,
            productCount=self._first_scalar([root], "productCount") or 0,
            palletCount=self._first_scalar([root], "palletCount") or 0,
            groups=groups,
            notes=notes,
            filterSummary=self._distribution_filter_summary_from_arguments(arguments),
        )

    def _raise_if_tool_error_payload(self, raw: Any) -> None:
        if not isinstance(raw, dict):
            return
        error = raw.get("error")
        if isinstance(error, dict):
            code = str(error.get("code") or "MCP_TOOL_ERROR")
            raise ToolGatewayError(code, self._safe_tool_error_message(code), bool(error.get("retryable", False)))
        if raw.get("isError") is True:
            code = str(raw.get("code") or "MCP_TOOL_ERROR")
            raise ToolGatewayError(code, self._safe_tool_error_message(code), bool(raw.get("retryable", False)))

    def _safe_tool_error_message(self, code: str) -> str:
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

    def _distribution_scope_label_from_arguments(self, arguments: dict[str, Any] | None) -> str | None:
        if not isinstance(arguments, dict):
            return None
        product_scope = arguments.get("productScope")
        if not isinstance(product_scope, dict):
            return None
        scope_type = product_scope.get("type")
        if scope_type == "ALL":
            return "全部产品"
        if scope_type == "EXACT_PRODUCT_NAME_GROUP":
            product_name = self._safe_text(product_scope.get("productName"))
            return f"产品名称为“{product_name}”的全部规格" if product_name else None
        if scope_type == "PRODUCT_TYPE_GROUP":
            product_type = self._safe_text(product_scope.get("productType"))
            return f"全部{product_type}大类" if product_type else None
        return None

    def _distribution_warehouse_scope_label_from_arguments(
        self, arguments: dict[str, Any] | None, state: WarehouseAgentState | None
    ) -> str | None:
        if not isinstance(arguments, dict):
            return None
        warehouse_scope = arguments.get("warehouseScope")
        if not isinstance(warehouse_scope, dict) or warehouse_scope.get("type") != "SINGLE_WAREHOUSE":
            return None
        requested_id = self._int_value(warehouse_scope, "warehouseId")
        selected = state.selected_warehouse if state is not None else None
        if selected is not None and requested_id is not None and selected.internal_id == requested_id:
            return selected.display_label
        return "所选库位"

    def _combine_distribution_scope_label(self, scope_label: str, warehouse_label: str) -> str:
        if scope_label == "全部产品":
            return f"{warehouse_label}的全部产品"
        if warehouse_label in scope_label:
            return scope_label
        return f"{warehouse_label}内{scope_label}"

    def _distribution_filter_summary_from_arguments(self, arguments: dict[str, Any] | None) -> str | None:
        if not isinstance(arguments, dict):
            return None
        filter_args = arguments.get("statusFilter")
        if not isinstance(filter_args, dict):
            return None
        parts = []
        date_summary = self._distribution_date_filter_summary(
            self._safe_text(filter_args.get("entryDateFrom")),
            self._safe_text(filter_args.get("entryDateTo")),
        )
        if date_summary:
            parts.append(date_summary)
        assay_status = self._safe_text(filter_args.get("assayStatus"))
        assay_labels = {
            "PASS": "化验合格",
            "FAIL": "化验不合格",
            "NO_STANDARD": "无标准化验",
            "MULTIPLE_CANDIDATES": "化验标准多候选",
            "HAS_ASSAY": "有化验记录",
            "MISSING_ASSAY": "无化验",
        }
        if assay_status in assay_labels:
            parts.append(assay_labels[assay_status])
        product_statuses = self._safe_filter_values(filter_args.get("productStatuses"))
        if product_statuses:
            parts.append("产品状态为" + "、".join(product_statuses))
        warehouse_statuses = self._safe_filter_values(filter_args.get("warehouseStatuses"))
        if warehouse_statuses:
            parts.append("库位状态为" + "、".join(warehouse_statuses))
        pallet_statuses = self._safe_filter_values(filter_args.get("palletStatuses"))
        pallet_labels = {
            "INSTOCK": "托盘在库",
            "FREE": "托盘空闲",
            "PENDING": "托盘待处理",
            "INVALID": "托盘作废",
            "ORDER_RESERVED": "托盘已预留",
        }
        labeled_pallet_statuses = [pallet_labels.get(value, value) for value in pallet_statuses]
        if labeled_pallet_statuses:
            parts.append("托盘状态为" + "、".join(labeled_pallet_statuses))
        return "、".join(parts) if parts else None

    def _distribution_date_filter_summary(self, start: str | None, end: str | None) -> str | None:
        if not start and not end:
            return None
        try:
            start_date = date.fromisoformat(start) if start else None
            end_date = date.fromisoformat(end) if end else None
        except ValueError:
            start_date = None
            end_date = None
        if start_date and end_date:
            days = (end_date - start_date).days + 1
            if days > 0:
                return f"最近{days}天"
            return f"{start}至{end}"
        if start:
            return f"{start}之后"
        return f"{end}之前"

    def _safe_filter_values(self, values: Any) -> list[str]:
        if not isinstance(values, list):
            return []
        return [text for value in values if (text := self._safe_text(value)) is not None][:10]

    def _adapt_inventory_result(self, raw: dict[str, Any]) -> SafeInventoryResult:
        root = raw if isinstance(raw, dict) else {}
        summary = self._dict_value(root.get("summary"))
        inventory_summary = self._dict_value(root.get("inventorySummary"))
        records = root.get("records") if isinstance(root.get("records"), list) else []
        first_record = self._dict_value(records[0]) if records else {}
        sources = [summary, inventory_summary, root, first_record]

        display_stock = self._first_safe_text(sources, "displayStockInfo", "stockInfo")
        if display_stock is None and isinstance(root.get("inventorySummary"), str):
            display_stock = self._safe_text(root.get("inventorySummary"))

        total_records = self._first_scalar([summary, root], "totalRecords")
        quantity_present = any(
            self._first_scalar(sources, key) is not None
            for key in (
                "displayStockInfo",
                "stockInfo",
                "normalizedPallets",
                "normalizedLoosePieces",
                "totalEquivalentPieces",
                "totalWeight",
            )
        )
        locations = []
        for record in records:
            item = self._dict_value(record)
            warehouse_name = self._first_safe_text([item], "warehouseName", "warehouse", "location")
            if not warehouse_name:
                continue
            locations.append(
                SafeInventoryLocation(
                    warehouseName=warehouse_name,
                    displayStockInfo=self._first_safe_text([item], "displayStockInfo", "stockInfo"),
                    totalEquivalentPieces=self._first_scalar([item], "totalEquivalentPieces"),
                    totalWeight=self._first_scalar([item], "totalWeight", "weight"),
                )
            )

        resolved_product = self._dict_value(root.get("resolvedProduct"))
        return SafeInventoryResult(
            displayLabel=self._first_safe_text([resolved_product, root], "displayLabel", "productName", "name"),
            displayStockInfo=display_stock,
            normalizedPallets=self._first_scalar(sources, "normalizedPallets"),
            normalizedLoosePieces=self._first_scalar(sources, "normalizedLoosePieces"),
            totalEquivalentPieces=self._first_scalar(sources, "totalEquivalentPieces"),
            totalWeight=self._first_scalar(sources, "totalWeight"),
            isEmpty=total_records in (0, 0.0, "0") if total_records is not None else False,
            locations=locations,
        )

    def _adapt_warehouse_result(self, raw: dict[str, Any]) -> SafeWarehouseResult:
        root = raw if isinstance(raw, dict) else {}
        capacity = self._dict_value(root.get("capacity"))
        warehouse = self._dict_value(root.get("warehouse"))
        sources = [capacity, warehouse, root]
        maximum = self._first_scalar(sources, "max", "maxCapacity", "totalCapacity", "capacity")
        occupancy = self._first_scalar(
            sources, "used", "usedCapacity", "currentOccupancy", "curCapacity", "currentCapacity", "usedPallets"
        )
        pallets = self._first_scalar(sources, "currentPallets", "currentPalletCount")
        remaining = self._first_scalar(sources, "remaining", "remainingCapacity", "availableCapacity", "freeCapacity")
        if remaining is None:
            remaining = self._subtract_scalars(maximum, occupancy)

        return SafeWarehouseResult(
            warehouseName=self._first_safe_text(sources, "warehouseName", "name", "displayName"),
            status=self._first_safe_text(sources, "status", "warehouseStatus", "statusDescription"),
            maxCapacity=maximum,
            currentOccupancy=occupancy,
            currentPallets=pallets,
            occupancyRate=self._first_scalar(sources, "usageRate", "occupancyRate", "capacityPercentage"),
            remainingCapacity=remaining,
        )
    def _tool_error_response(self, agent_session_id: str, error: ToolGatewayError) -> ChatResponse:
        return ChatResponse(
            agentSessionId=agent_session_id,
            answer=error.message,
            error=AgentError(code=error.code, message=error.message, retryable=error.retryable),
        )

    def _record_tool_message(self, state: WarehouseAgentState, tool_name: str, summary: dict[str, Any]) -> None:
        context = current_execution_context()
        expert_agent = context.agent_name if context is not None else "unknown_expert"
        state.messages.append(
            {
                "role": "tool",
                "expertAgent": expert_agent,
                "toolName": tool_name,
                "content": summary,
            }
        )
        state.tool_results.append(
            {"kind": tool_name, "expertAgent": expert_agent, "summary": summary}
        )

    def _execution_context_from_handoff(self, handoff: dict[str, Any]) -> AgentExecutionContext:
        return AgentExecutionContext(
            agent_name=str(handoff.get("target_agent") or "main_agent"),
            allowed_tools=frozenset(str(tool) for tool in handoff.get("allowed_tools") or []),
            business_domain=(str(handoff["business_domain"]) if handoff.get("business_domain") else None),
            handoff_mode=str(handoff.get("mode") or "direct"),
        )

    def _pending_execution_context(
        self,
        pending: PendingClarification | None,
    ) -> AgentExecutionContext:
        if pending is None:
            return AgentExecutionContext(agent_name="main_agent", allowed_tools=frozenset())
        return AgentExecutionContext(
            agent_name=pending.expert_agent,
            allowed_tools=frozenset(pending.allowed_tools),
            business_domain=pending.business_domain,
            handoff_mode="resume",
        )

    def _pending_agent_binding(self) -> dict[str, Any]:
        context = current_execution_context()
        if context is None:
            raise ExpertBoundaryError("clarification requires an expert execution context")
        return {
            "expert_agent": context.agent_name,
            "allowed_tools": tuple(sorted(context.allowed_tools)),
            "business_domain": context.business_domain,
        }

    def _safe_tool_summary(self, tool_name: str, result: dict[str, Any]) -> dict[str, Any]:
        if tool_name == "resolve_production_entities":
            source = result if isinstance(result, dict) else {}
            return {
                "resolutionStatus": self._safe_text(source.get("resolutionStatus")),
                "entityType": self._safe_text(source.get("entityType")),
                "candidateCount": self._list_size(source.get("candidates")),
            }
        if tool_name == "query_production_order_progress":
            source = result if isinstance(result, dict) else {}
            return {
                "dataScope": self._safe_text(source.get("dataScope")),
                "orderNo": self._safe_text(source.get("orderNo")),
                "status": self._safe_text(source.get("status")),
                "materialRecordCount": self._first_scalar([source], "materialRecordCount"),
                "outputRecordCount": self._first_scalar([source], "outputRecordCount"),
                "inboundQrCount": self._first_scalar([source], "inboundQrCount"),
            }
        if tool_name == "query_boiling_batch_trace":
            source = result if isinstance(result, dict) else {}
            return {
                "dataScope": self._safe_text(source.get("dataScope")),
                "batchNo": self._safe_text(source.get("batchNo")),
                "status": self._safe_text(source.get("status")),
                "usageCount": self._first_scalar([source], "usageCount"),
                "nodeCount": self._first_scalar([source], "nodeCount"),
                "edgeCount": self._first_scalar([source], "edgeCount"),
            }
        if tool_name == "query_material_pick_trace":
            source = result if isinstance(result, dict) else {}
            return {
                "dataScope": self._safe_text(source.get("dataScope")),
                "orderNo": self._safe_text(source.get("orderNo")),
                "orderStatus": self._safe_text(source.get("orderStatus")),
                "materialRecordCount": self._first_scalar([source], "materialRecordCount"),
            }
        if tool_name == "query_production_label_completion":
            source = result if isinstance(result, dict) else {}
            return {
                "dataScope": self._safe_text(source.get("dataScope")),
                "orderNo": self._safe_text(source.get("orderNo")),
                "labelBatchCount": self._first_scalar([source], "labelBatchCount"),
                "requiredQrCount": self._first_scalar([source], "requiredQrCount"),
                "inboundQrCount": self._first_scalar([source], "inboundQrCount"),
            }
        if tool_name == "query_in_process_materials":
            source = result if isinstance(result, dict) else {}
            return {"dataScope": self._safe_text(source.get("dataScope")),
                    "total": self._first_scalar([source], "total"),
                    "recordCount": len(source.get("records") or [])}
        if tool_name == "query_material_candidates":
            source = result if isinstance(result, dict) else {}
            return {"dataScope": self._safe_text(source.get("dataScope")),
                    "total": self._first_scalar([source], "total"),
                    "recordCount": len(source.get("records") or [])}
        if tool_name == "query_pallet_tasks":
            source = result if isinstance(result, dict) else {}
            return {"dataScope": self._safe_text(source.get("dataScope")),
                    "total": self._first_scalar([source], "total"),
                    "recordCount": len(source.get("records") or [])}
        if tool_name == "query_stock_documents":
            source = result if isinstance(result, dict) else {}
            return {"dataScope": self._safe_text(source.get("dataScope")),
                    "documentType": self._safe_text(source.get("documentType")),
                    "total": self._first_scalar([source], "total"), "recordCount": len(source.get("records") or [])}
        if tool_name == "query_auto_inbound_batches":
            source = result if isinstance(result, dict) else {}
            return {"dataScope": self._safe_text(source.get("dataScope")),
                    "count": self._first_scalar([source], "count")}
        if tool_name == "get_auto_inbound_batch_detail":
            source = result if isinstance(result, dict) else {}
            return {"dataScope": self._safe_text(source.get("dataScope")),
                    "taskCount": self._first_scalar([source], "taskCount")}
        if tool_name == "query_warehouse_capacity_distribution":
            source = result if isinstance(result, dict) else {}
            return {"dataScope": self._safe_text(source.get("dataScope")),
                    "total": self._first_scalar([source], "total"),
                    "recordCount": len(source.get("records") or [])}
        if tool_name == "query_warehouse_recent_operations":
            source = result if isinstance(result, dict) else {}
            return {"dataScope": self._safe_text(source.get("dataScope")),
                    "count": self._first_scalar([source], "count")}
        if tool_name == "query_warehouse_mixed_storage_facts":
            source = result if isinstance(result, dict) else {}
            return {"dataScope": self._safe_text(source.get("dataScope")),
                    "count": self._first_scalar([source], "count")}
        if tool_name in {"query_product_catalog", "query_screen_mesh_catalog"}:
            source = result if isinstance(result, dict) else {}
            return {"dataScope": self._safe_text(source.get("dataScope")),
                    "total": self._first_scalar([source], "total"), "recordCount": len(source.get("records") or [])}
        if tool_name == "get_product_detail":
            source = result if isinstance(result, dict) else {}
            return {"dataScope": self._safe_text(source.get("dataScope")), "productName": self._safe_text(source.get("productName"))}
        if tool_name in {"query_assay_groups", "query_quality_standard_catalog", "query_product_standard_relations"}:
            source = result if isinstance(result, dict) else {}
            return {"dataScope": self._safe_text(source.get("dataScope")), "total": self._first_scalar([source], "total") or self._first_scalar([source], "count"), "recordCount": len(source.get("records") or [])}
        if tool_name in {"query_employee_roster", "query_roles"}:
            source = result if isinstance(result, dict) else {}
            return {"dataScope": self._safe_text(source.get("dataScope")), "total": self._first_scalar([source], "total"), "recordCount": len(source.get("records") or [])}
        if tool_name == "get_role_permission_summary":
            source = result if isinstance(result, dict) else {}
            return {"dataScope": self._safe_text(source.get("dataScope")), "roleCode": self._safe_text(source.get("roleCode")), "permissionCount": self._first_scalar([source], "permissionCount")}
        if tool_name in {"search_operation_logs", "query_agent_tool_audit", "query_agent_answer_reviews"}:
            source = result if isinstance(result, dict) else {}
            return {"dataScope": self._safe_text(source.get("dataScope")), "total": self._first_scalar([source], "total"), "recordCount": len(source.get("records") or [])}
        if tool_name in {"query_inventory_ledger", "query_prepare_pool_balance"}:
            source = result if isinstance(result, dict) else {}
            return {"dataScope": self._safe_text(source.get("dataScope")), "total": self._first_scalar([source], "total"), "recordCount": len(source.get("records") or [])}
        if tool_name == "query_fixed_product_qr_pool":
            source = result if isinstance(result, dict) else {}
            return {"dataScope": self._safe_text(source.get("dataScope")), "total": self._first_scalar([source], "total"), "recordCount": len(source.get("records") or [])}
        if tool_name == "get_quality_standard_detail":
            source = result if isinstance(result, dict) else {}
            return {"dataScope": self._safe_text(source.get("dataScope")), "standardCode": self._safe_text(source.get("standardCode")), "version": self._first_scalar([source], "version")}
        if tool_name == "resolve_products":
            return {
                "resolutionStatus": self._safe_text(result.get("resolutionStatus")),
                "candidateCount": self._list_size(result.get("options") or result.get("candidates")),
            }
        if tool_name == "resolve_warehouses":
            return {
                "resolutionStatus": self._safe_text(result.get("resolutionStatus")),
                "candidateCount": self._list_size(result.get("options") or result.get("candidates")),
            }
        if tool_name == "get_inventory_overview":
            source = result if isinstance(result, dict) else {}
            return {
                "displayStockInfo": self._safe_text(source.get("displayStockInfo")),
                "totalEquivalentPieces": self._first_scalar([source], "totalEquivalentPieces"),
                "totalWeight": self._first_scalar([source], "totalWeight"),
                "locationCount": self._list_size(source.get("locations") or source.get("records")),
            }
        if tool_name == "get_inventory_distribution":
            source = result if isinstance(result, dict) else {}
            return {
                "productLabel": self._safe_text(source.get("productLabel")),
                "totalStockText": self._safe_text(source.get("totalStockText")),
                "warehouseCount": self._first_scalar([source], "warehouseCount"),
                "groupCount": self._list_size(source.get("groups")),
            }
        if tool_name == "get_warehouse_status":
            source = result if isinstance(result, dict) else {}
            return {
                "warehouseName": self._safe_text(source.get("warehouseName")),
                "status": self._safe_text(source.get("status")),
                "remainingCapacity": self._first_scalar([source], "remainingCapacity"),
            }
        if tool_name == "get_assay_status":
            return {
                "judgeResult": self._safe_text(result.get("judgeResult") or result.get("result")),
                "needsAssay": bool(result.get("needsAssay", False)),
            }
        if tool_name == "query_assay_records":
            source = result if isinstance(result, dict) else {}
            return {
                "scopeLabel": self._safe_text(source.get("scopeLabel")),
                "dateRangeLabel": self._safe_text(source.get("dateRangeLabel")),
                "total": self._first_scalar([source], "total"),
                "latestSampleDate": self._safe_text(source.get("latestSampleDate")),
                "recordCount": self._list_size(source.get("records")),
            }
        if tool_name == "get_assay_report_detail":
            source = result if isinstance(result, dict) else {}
            return {
                "reportLabel": self._safe_text(source.get("reportLabel")),
                "judgeLabel": self._safe_text(source.get("judgeLabel")),
                "standardLabel": self._safe_text(source.get("standardLabel")),
                "riskLabels": source.get("riskLabels") if isinstance(source.get("riskLabels"), list) else [],
            }
        if tool_name == "query_assay_abnormalities":
            source = result if isinstance(result, dict) else {}
            return {
                "scopeLabel": self._safe_text(source.get("scopeLabel")),
                "dateRangeLabel": self._safe_text(source.get("dateRangeLabel")),
                "total": self._first_scalar([source], "total"),
                "failedCount": self._first_scalar([source], "failedCount"),
                "noStandardCount": self._first_scalar([source], "noStandardCount"),
                "groupCount": self._list_size(source.get("groups")),
            }
        if tool_name == "query_products_without_recent_assay":
            source = result if isinstance(result, dict) else {}
            return {
                "scopeLabel": self._safe_text(source.get("scopeLabel")),
                "warehouseScopeLabel": self._safe_text(source.get("warehouseScopeLabel")),
                "dateRangeLabel": self._safe_text(source.get("dateRangeLabel")),
                "totalGroups": self._first_scalar([source], "totalGroups"),
                "groupCount": self._list_size(source.get("groups")),
            }
        if tool_name == "query_assay_standard_coverage":
            source = result if isinstance(result, dict) else {}
            return {
                "scopeLabel": self._safe_text(source.get("scopeLabel")),
                "coverageType": self._safe_text(source.get("coverageType")),
                "totalGroups": self._first_scalar([source], "totalGroups"),
                "groupCount": self._list_size(source.get("groups")),
            }
        if tool_name == "query_qr_code_lifecycle":
            source = result if isinstance(result, dict) else {}
            return {
                "codeLabel": self._safe_text(source.get("codeLabel")),
                "currentStatusLabel": self._safe_text(source.get("currentStatusLabel")),
                "eventCount": self._list_size(source.get("timeline")),
                "riskCount": self._list_size(source.get("riskLabels")),
            }
        if tool_name in {"query_printed_not_inbound_codes", "query_pallet_anomalies", "query_pallet_flow_records", "query_qr_batch_inbound_completion"}:
            source = result if isinstance(result, dict) else {}
            return {
                "scopeLabel": self._safe_text(source.get("scopeLabel") or source.get("batchLabel")),
                "dateRangeLabel": self._safe_text(source.get("dateRangeLabel")),
                "total": self._first_scalar([source], "total", "printedCount", "notInboundCount"),
                "groupCount": self._list_size(source.get("groups") or source.get("records")),
            }
        if tool_name == "get_pallet_status":
            info = self._dict_value(result.get("palletInfo") or result.get("baseInfo") or result)
            return {"status": self._safe_text(info.get("status") or info.get("bindStatus"))}
        return {}

    def _format_production_order_progress_answer(self, result: dict[str, Any]) -> str:
        source = result if isinstance(result, dict) else {}
        order_no = self._safe_text(source.get("orderNo")) or "该生产订单"
        status = self._production_status_label(source.get("status"))
        production_date = self._safe_text(source.get("productionDate"))
        material_count = self._first_scalar([source], "materialRecordCount") or 0
        output_count = self._first_scalar([source], "outputRecordCount") or 0
        required = self._first_scalar([source], "requiredQrCount") or 0
        bound = self._first_scalar([source], "boundQrCount") or 0
        inbound = self._first_scalar([source], "inboundQrCount") or 0
        labels = self._first_scalar([source], "reservedLabelCount") or 0
        pieces = [f"生产订单 {order_no} 当前状态为 {status}"]
        if production_date:
            pieces.append(f"生产日期 {production_date}")
        pieces.append(f"已登记 {material_count} 条领料记录、{output_count} 条产出记录")
        pieces.append(f"标签预留 {labels} 个，二维码需求 {required} 个、已绑定 {bound} 个、已入库 {inbound} 个")
        pieces.append("数据范围仅为当前订单记录；不代表产出率、损耗率或质量放行结论。")
        return "；".join(pieces) + "。"

    def _format_boiling_batch_trace_answer(self, result: dict[str, Any]) -> str:
        source = result if isinstance(result, dict) else {}
        batch_no = self._safe_text(source.get("batchNo")) or "该煮糖批次"
        status = self._safe_text(source.get("status")) or "未知"
        product = self._safe_text(source.get("productName"))
        total_weight = self._first_scalar([source], "totalWeightKg")
        remaining_weight = self._first_scalar([source], "remainingWeightKg")
        usage_count = self._first_scalar([source], "usageCount") or 0
        node_count = self._first_scalar([source], "nodeCount") or 0
        edge_count = self._first_scalar([source], "edgeCount") or 0
        pieces = [f"煮糖批次 {batch_no} 当前状态为 {status}"]
        if product:
            pieces.append(f"产品为 {product}")
        if total_weight is not None:
            pieces.append(f"登记总重量 {total_weight} kg")
        if remaining_weight is not None:
            pieces.append(f"剩余重量 {remaining_weight} kg")
        pieces.append(f"系统已登记 {usage_count} 条使用记录、{node_count} 个追溯节点和 {edge_count} 条关系边")
        pieces.append("仅展示系统已有追溯证据；缺失的上下游关系不会由 Agent 推断。")
        return "；".join(pieces) + "。"

    def _format_material_pick_trace_answer(self, result: dict[str, Any]) -> str:
        source = result if isinstance(result, dict) else {}
        order_no = self._safe_text(source.get("orderNo")) or "该生产订单"
        status = self._production_status_label(source.get("orderStatus"))
        records = source.get("records") if isinstance(source.get("records"), list) else []
        lines = [f"生产订单 {order_no} 当前状态为 {status}，已登记 {len(records)} 条实际领料记录。"]
        for item in records[:5]:
            if not isinstance(item, dict):
                continue
            product = self._safe_text(item.get("productName")) or "未标明产品"
            pallet = self._safe_text(item.get("palletCode")) or "未标明托盘"
            warehouse = self._safe_text(item.get("warehouseName")) or "未标明库位"
            quantity = self._first_scalar([item], "totalWeight", "totalPieces", "quantity")
            quantity_text = f"，登记数量 {quantity}" if quantity is not None else ""
            lines.append(f"{product}，托盘 {pallet}，来源 {warehouse}{quantity_text}。")
        lines.append("这里只展示已登记实际领料，不计算计划差异、损耗或实际消耗率。")
        return "".join(lines)

    def _production_status_label(self, value: Any) -> str:
        status = self._safe_text(value)
        return {
            "PENDING": "待处理",
            "IN_PROGRESS": "进行中",
            "COMPLETED": "已完成",
            "CANCELLED": "已取消",
        }.get(status or "", status or "未知")

    def _format_production_label_completion_answer(self, result: dict[str, Any]) -> str:
        source = result if isinstance(result, dict) else {}
        order_no = self._safe_text(source.get("orderNo")) or "该生产订单"
        reserved = self._first_scalar([source], "reservedLabelCount") or 0
        used = self._first_scalar([source], "usedLabelCount") or 0
        recycled = self._first_scalar([source], "recycledLabelCount") or 0
        required = self._first_scalar([source], "requiredQrCount") or 0
        bound = self._first_scalar([source], "boundQrCount") or 0
        inbound = self._first_scalar([source], "inboundQrCount") or 0
        not_bound = self._first_scalar([source], "notBoundQrCount") or 0
        not_inbound = self._first_scalar([source], "notInboundQrCount") or 0
        return (
            f"生产订单 {order_no}：标签预留 {reserved} 个、已使用 {used} 个、已回收 {recycled} 个；"
            f"二维码需求 {required} 个、已绑定 {bound} 个、已入库 {inbound} 个，"
            f"尚未绑定 {not_bound} 个、尚未入库 {not_inbound} 个。"
            "打印时间只表示标签批次记录了打印，不等同于二维码已绑定或已入库。"
        )

    def _format_in_process_materials_answer(self, result: dict[str, Any]) -> str:
        source = result if isinstance(result, dict) else {}
        records = source.get("records") if isinstance(source.get("records"), list) else []
        total = self._first_scalar([source], "total") or 0
        if not records:
            return "当前没有查询到在制半成品领料记录。这表示当前筛选范围内没有已登记记录，不代表查询失败，也不能据此判断是否仍有可领物料或实时库存。"
        lines = [f"查询到 {total} 条在制半成品领料记录，下面展示本页的 {len(records)} 条。"]
        for item in records[:10]:
            if not isinstance(item, dict):
                continue
            lines.append(
                f"订单 {self._safe_text(item.get('orderNo')) or '未标明'}，"
                f"{self._safe_text(item.get('productName')) or '未标明产品'}，"
                f"托盘 {self._safe_text(item.get('palletCode')) or '未标明'}，"
                f"状态 {self._enum_label('material_status', item.get('materialStatus'))}。"
            )
        lines.append("在制记录不代表仍可再次领用、质量已放行、FIFO/FEFO 推荐或实时库存结余。")
        return "".join(lines)

    def _format_material_candidates_answer(self, result: dict[str, Any]) -> str:
        source = result if isinstance(result, dict) else {}
        records = source.get("records") if isinstance(source.get("records"), list) else []
        total = self._first_scalar([source], "total") or 0
        lines = [f"当前查询到 {total} 条半成品库存候选，本页返回 {len(records)} 条。"]
        for item in records[:10]:
            if isinstance(item, dict):
                lines.append(f"{self._safe_text(item.get('productName')) or '未标明产品'}，托盘 "
                             f"{self._safe_text(item.get('palletCode')) or '未标明'}，库位 "
                             f"{self._safe_text(item.get('warehouseName')) or '未标明'}。")
        lines.append("这些记录不代表已领用或已预留，返回顺序也不是 FIFO/FEFO 推荐或质量放行结论。")
        return "".join(lines)

    def _adapt_pallet_tasks(
        self,
        result: dict[str, Any],
        arguments: dict[str, Any] | None,
    ) -> SafePalletTaskResult:
        source = result if isinstance(result, dict) else {}
        raw_records = source.get("records") if isinstance(source.get("records"), list) else []
        records: list[SafePalletTaskRecord] = []
        for raw_record in raw_records[:50]:
            item = self._dict_value(raw_record)
            task_type = self._controlled_task_label("task_type", item.get("taskType"), "任务类型未标明")
            task_status = self._controlled_task_label("task_status", item.get("taskStatus"), "状态未标明")
            product_name = self._safe_text(item.get("productName"))
            product_type = self._safe_text(item.get("productType"))
            product_label = product_name or product_type or "产品未标明"

            warehouse = self._safe_text(item.get("targetWarehouseName"))
            side = self._controlled_task_label("warehouse_side", item.get("targetSide"), "")
            target_location = " ".join(value for value in (warehouse, side) if value) or None

            weight = self._safe_text(item.get("totalWeight"))
            if weight and not weight.lower().endswith("kg"):
                weight = f"{weight} kg"
            semi_item_count = self._safe_text(item.get("semiItemCount"))
            if semi_item_count:
                semi_item_count = f"{semi_item_count} 项半成品"

            production_order = self._safe_text(item.get("productionOrderNo"))
            production_order_status = None
            if item.get("productionOrderStatus") is not None:
                production_order_status = self._controlled_task_label(
                    "production_status",
                    item.get("productionOrderStatus"),
                    "订单状态未标明",
                )
            created_summary = self._task_actor_time_summary(
                item.get("createdBy"),
                item.get("createdAt"),
            )
            confirmation_summary = self._task_actor_time_summary(
                item.get("confirmedBy"),
                item.get("confirmedAt"),
            )
            records.append(
                SafePalletTaskRecord(
                    taskTypeLabel=task_type,
                    taskStatusLabel=task_status,
                    palletCode=self._safe_text(item.get("code")) or "托盘未标明",
                    productLabel=product_label,
                    businessSceneLabel=(
                        self._controlled_task_label("business_scene", item.get("bizScene"), "业务场景未标明")
                        if item.get("bizScene") is not None else None
                    ),
                    productStatusLabel=self._safe_text(item.get("productStatus")),
                    targetLocationLabel=target_location,
                    totalWeightText=weight,
                    productionDate=self._safe_text(item.get("productionDate")),
                    screenMeshLabel=self._safe_text(item.get("screenMeshName")),
                    semiItemCountText=semi_item_count,
                    operationBatchLabel=self._safe_text(item.get("operationBatchNo")),
                    productionOrderLabel=production_order,
                    productionOrderStatusLabel=production_order_status,
                    productionLabelBatchLabel=self._safe_text(item.get("productionLabelBatchNo")),
                    createdSummary=created_summary,
                    confirmationSummary=confirmation_summary,
                )
            )

        filters = self._pallet_task_filter_labels(arguments or {})
        total_value = self._first_scalar([source], "total")
        try:
            total = max(0, int(total_value)) if total_value is not None else len(records)
        except (TypeError, ValueError):
            total = len(records)
        try:
            page = max(1, int(self._first_scalar([source], "page") or (arguments or {}).get("page") or 1))
        except (TypeError, ValueError):
            page = 1
        try:
            size = max(1, int(self._first_scalar([source], "size") or (arguments or {}).get("size") or 20))
        except (TypeError, ValueError):
            size = 20

        status_label = self._controlled_task_label(
            "task_status",
            (arguments or {}).get("status"),
            "",
        )
        task_type_label = self._controlled_task_label(
            "task_type",
            (arguments or {}).get("taskType"),
            "",
        )
        if status_label or task_type_label:
            scope_label = f"当前{status_label}{task_type_label}任务"
        else:
            scope_label = "当前托盘任务"
        return SafePalletTaskResult(
            scopeLabel=scope_label,
            total=total,
            page=page,
            size=size,
            filterLabels=filters,
            records=records,
            limitations=["此次仅查询任务记录，未确认、取消或执行任何入库、出库和调拨。"],
        )

    def _pallet_task_filter_labels(self, arguments: dict[str, Any]) -> list[str]:
        labels: list[str] = []
        status = self._controlled_task_label("task_status", arguments.get("status"), "")
        task_type = self._controlled_task_label("task_type", arguments.get("taskType"), "")
        scene = self._controlled_task_label("business_scene", arguments.get("bizScene"), "")
        if status:
            labels.append(f"状态：{status}")
        if task_type:
            labels.append(f"类型：{task_type}")
        if scene:
            labels.append(f"业务场景：{scene}")
        for key, prefix in (
            ("productName", "产品"),
            ("productType", "产品大类"),
            ("productStatus", "产品状态"),
            ("targetWarehouseName", "目标库位"),
        ):
            value = self._safe_text(arguments.get(key))
            if value:
                labels.append(f"{prefix}：{value}")
        start = self._safe_text(arguments.get("productionDateStart"))
        end = self._safe_text(arguments.get("productionDateEnd"))
        if start or end:
            labels.append(f"生产日期：{start or '最早'} 至 {end or '当前'}")
        return labels

    def _controlled_task_label(self, category: str, value: Any, fallback: str) -> str:
        raw = self._safe_text(value)
        if not raw:
            return fallback
        labels = {
            "task_type": {
                "IN": "入库", "INBOUND": "入库", "OUT": "出库", "OUTBOUND": "出库",
                "TRANSFER": "调拨", "SEMI_IN": "半成品入库", "SEMI_OUT": "半成品出库",
                "FINISH_IN": "成品入库", "FINISH_OUT": "成品出库",
            },
            "task_status": {
                "PENDING": "待处理", "PROCESSING": "处理中", "COMPLETED": "已完成",
                "CONFIRMED": "已确认", "CANCELED": "已取消", "CANCELLED": "已取消",
                "FAILED": "处理失败",
            },
            "business_scene": {
                "DIRECT_OUT": "半成品直接出库",
                "PREPARE_CONSUMED": "历史生产占用",
                "FINISH_OUT": "成品出库",
            },
            "warehouse_side": {"LEFT": "左侧", "RIGHT": "右侧"},
            "production_status": {
                "PENDING": "待处理", "WAIT_MATERIAL": "待领料", "IN_PROGRESS": "进行中",
                "PRODUCING": "生产中", "WAIT_INBOUND": "待入库", "COMPLETED": "已完成",
                "CANCELED": "已取消", "CANCELLED": "已取消",
            },
        }
        return labels.get(category, {}).get(raw, fallback)

    def _task_actor_time_summary(self, actor: Any, timestamp: Any) -> str | None:
        actor_label = self._safe_text(actor)
        time_label = self._safe_text(timestamp)
        if time_label:
            time_label = time_label.replace("T", " ")[:16]
        if actor_label and time_label:
            return f"{actor_label} · {time_label}"
        return actor_label or time_label

    def _pallet_tasks_card(self, result: SafePalletTaskResult) -> BusinessCard:
        fields: list[dict[str, Any]] = []
        for index, record in enumerate(result.records, start=1):
            field = record.model_dump(exclude_none=True)
            field.update(
                {
                    "kind": "pallet_task",
                    "label": f"{index}. {record.taskTypeLabel}任务",
                    "value": record.palletCode,
                }
            )
            fields.append(field)
        return BusinessCard(
            cardType="pallet_tasks",
            title=f"{result.scopeLabel} · 共 {result.total} 条",
            fields=fields,
        )

    def _pallet_task_detail_card(self, record: SafePalletTaskRecord, index: int) -> BusinessCard:
        field = record.model_dump(exclude_none=True)
        field.update(
            {
                "kind": "pallet_task_detail",
                "label": f"第 {index} 条任务",
                "value": record.palletCode,
            }
        )
        return BusinessCard(
            cardType="pallet_task_detail",
            title=f"{record.taskTypeLabel}任务 · {record.palletCode}",
            fields=[field],
        )

    def _task_detail_followup_response(
        self,
        agent_session_id: str,
        state: WarehouseAgentState,
        text: str,
    ) -> ChatResponse | None:
        normalized = re.sub(r"\s+", "", text or "")
        if not any(word in normalized for word in ("详情", "详细", "展开")):
            return None
        if "任务" not in normalized and state.active_agent != "logistics_expert":
            return None
        if not isinstance(state.last_pallet_tasks, dict):
            return None
        try:
            result = SafePalletTaskResult.model_validate(state.last_pallet_tasks)
        except ValueError:
            return None
        if not result.records:
            return ChatResponse(
                agentSessionId=agent_session_id,
                answer="上一轮任务查询没有可展开的记录，请先查询任务列表。",
                suggestions=["查询当前待处理任务"],
            )

        match = re.search(r"第?([一二三四五六七八九十\d]+)(?:条|个)", normalized)
        if match:
            index = self._task_ordinal(match.group(1))
        elif len(result.records) == 1:
            index = 1
        else:
            return ChatResponse(
                agentSessionId=agent_session_id,
                answer=f"上一轮共展示 {len(result.records)} 条任务，请告诉我要看第几条。",
                needsUserSelection=True,
            )
        if index is None or index < 1 or index > len(result.records):
            return ChatResponse(
                agentSessionId=agent_session_id,
                answer=f"上一轮只展示了 {len(result.records)} 条任务，请选择这个范围内的序号。",
                needsUserSelection=True,
            )
        record = result.records[index - 1]
        target = f"，目标位置为{record.targetLocationLabel}" if record.targetLocationLabel else ""
        answer = (
            f"第 {index} 条是{record.taskTypeLabel}任务，托盘 {record.palletCode}，"
            f"产品为{record.productLabel}，当前状态为{record.taskStatusLabel}{target}。\n\n"
            "具体信息见下方卡片。此次仅查看已有任务记录，没有执行或变更任务。"
        )
        return ChatResponse(
            agentSessionId=agent_session_id,
            answer=answer,
            cards=[self._pallet_task_detail_card(record, index)],
            reviewTrace={
                "planningMode": "state_reuse",
                "executionInfluence": True,
                "stateReuse": {"source": "LAST_TASK_QUERY", "toolCalled": False},
            },
        )

    def _task_filter_followup_response(
        self,
        request: ChatRequest,
        state: WarehouseAgentState,
        text: str,
    ) -> ChatResponse | None:
        normalized = re.sub(r"\s+", "", text or "")
        if self.planning_mode != "llm" or state.active_agent != "logistics_expert":
            return None
        if not isinstance(state.last_pallet_task_filters, dict):
            return None
        prefixes = (
            "只看", "仅看", "只查", "仅查", "筛选", "其中", "这些",
            "再看", "再查", "换成", "改看", "全部任务", "所有任务", "清除筛选",
        )
        filter_words = (
            "任务", "入库", "出库", "调拨", "半成品", "成品",
            "待处理", "已确认", "已取消", "不限状态", "清除筛选",
        )
        if not normalized.startswith(prefixes) or not any(word in normalized for word in filter_words):
            return None

        arguments = self.argument_builder.validate_llm_arguments(
            tool_name="query_pallet_tasks",
            arguments={},
            state=state,
            user_message=text,
        )
        handoff = self.agent_router.handoff_for_agent("logistics_expert", mode="llm_delegate")
        self.agent_router.authorize_tool(handoff.target_agent, "query_pallet_tasks")
        state.last_agent_handoff = handoff.to_snapshot()
        state.active_run = {
            "traceId": request.client.traceId,
            "sessionId": request.agentSessionId,
            "requestId": request.client.requestId,
            "runId": f"run_{secrets.token_hex(8)}",
            "handoffId": f"handoff_{secrets.token_hex(8)}",
            "startedAt": datetime.now(timezone.utc).isoformat(),
            "planningMode": "llm_context_filter",
        }
        execution_context = AgentExecutionContext(
            agent_name=handoff.target_agent,
            allowed_tools=frozenset(handoff.allowed_tools),
            business_domain=handoff.business_domain,
            handoff_mode="llm_delegate",
            handoff_id=str(state.active_run["handoffId"]),
        )
        with bind_execution_context(execution_context):
            result = self._call_tool(request, "query_pallet_tasks", arguments)
        self._raise_if_cancelled()
        self._raise_if_tool_error_payload(result)
        adapted = self._adapt_pallet_tasks(result, arguments)
        safe_data = adapted.model_dump(exclude_none=True)
        state.last_pallet_tasks = dict(safe_data)
        state.last_pallet_task_filters = dict(arguments)
        self._record_registered_goal_fact(
            state=state,
            tool_name="query_pallet_tasks",
            arguments=arguments,
            safe_data=safe_data,
        )
        self._record_tool_message(
            state,
            "query_pallet_tasks",
            self._safe_tool_summary("query_pallet_tasks", result),
        )
        self.metrics.increment("llm_context_filter_total", tool="query_pallet_tasks")
        cards = [self._pallet_tasks_card(adapted)] if adapted.records else []
        return ChatResponse(
            agentSessionId=request.agentSessionId,
            answer=self._format_pallet_tasks_answer(adapted),
            cards=cards,
            suggestions=self.next_action_policy.suggestions("query_pallet_tasks", safe_data),
            reviewTrace={
                "planningMode": "llm",
                "executionInfluence": True,
                "mainRouteOptimization": {
                    "status": "BOUNDED_CONTEXT_FILTER",
                    "expertAgent": "logistics_expert",
                    "modelCallSkipped": True,
                },
            },
        )

    @staticmethod
    def _task_ordinal(value: str) -> int | None:
        if value.isdigit():
            return int(value)
        labels = {
            "一": 1, "二": 2, "三": 3, "四": 4, "五": 5,
            "六": 6, "七": 7, "八": 8, "九": 9, "十": 10,
        }
        return labels.get(value)

    def _format_pallet_tasks_answer(self, result: SafePalletTaskResult) -> str:
        filter_text = "；".join(result.filterLabels) if result.filterLabels else "无额外筛选"
        limitation = result.limitations[0] if result.limitations else "此次仅查询任务记录，没有执行或变更任务。"
        if not result.records:
            return (
                f"{result.scopeLabel}中没有查询到符合条件的记录。\n\n"
                f"筛选条件：{filter_text}。\n\n"
                f"{limitation}"
            )

        counts: dict[str, int] = {}
        for record in result.records:
            counts[record.taskTypeLabel] = counts.get(record.taskTypeLabel, 0) + 1
        composition = "、".join(f"{label} {count} 条" for label, count in counts.items())
        lines = [
            f"{result.scopeLabel}共有 {result.total} 条，本页展示 {len(result.records)} 条。",
            "",
            f"任务构成：{composition}。",
            f"筛选条件：{filter_text}。",
            "",
            "具体托盘、产品、目标库位和任务状态见下方卡片。",
            limitation,
        ]
        return "\n".join(lines)

    def _format_stock_documents_answer(self, result: dict[str, Any]) -> str:
        source = result if isinstance(result, dict) else {}
        records = source.get("records") if isinstance(source.get("records"), list) else []
        labels = {"INBOUND": "入库", "OUTBOUND": "出库", "SEMI_PRODUCT": "半成品"}
        label = labels.get(self._safe_text(source.get("documentType")), "库存")
        total = self._first_scalar([source], "total") or 0
        lines = [f"当前条件下共有 {total} 条{label}单据，本页返回 {len(records)} 条。"]
        for item in records[:10]:
            if isinstance(item, dict):
                lines.append(f"{self._safe_text(item.get('businessDate')) or '日期未标明'}，"
                             f"{self._safe_text(item.get('productName')) or '产品未标明'}，"
                             f"库位 {self._safe_text(item.get('warehouseName')) or '未标明'}，"
                             f"数量 {self._first_scalar([item], 'quantity') or 0}。")
        lines.append("每次只查询一种明确单据来源；本次未执行任何库存操作，也未重建系统缺失的历史事件。")
        return "".join(lines)

    def _format_auto_inbound_batches_answer(self, result: dict[str, Any]) -> str:
        source = result if isinstance(result, dict) else {}
        records = source.get("records") if isinstance(source.get("records"), list) else []
        if not records:
            return "当前用户没有查询到尚未过期的智能报数批次。该结果不同于批次查询服务失败。"
        lines = [f"当前用户共有 {len(records)} 个尚未过期的智能报数批次："]
        for index, item in enumerate(records[:20], 1):
            if isinstance(item, dict):
                lines.append(
                    f"{index}. {self._safe_text(item.get('displayName')) or '未命名批次'}，"
                    f"状态 {self._safe_text(item.get('status')) or '未知'}，"
                    f"任务数 {self._first_scalar([item], 'taskCount') or 0}。"
                )
        lines.append("如需详情，请说明查看第几个批次。以上仅是当前用户 Redis 中未过期的近期解析记录，本次未确认或执行入库。")
        return "".join(lines)

    def _format_auto_inbound_batch_detail_answer(self, result: dict[str, Any]) -> str:
        source = result if isinstance(result, dict) else {}
        tasks = source.get("tasks") if isinstance(source.get("tasks"), list) else []
        lines = [f"该智能报数批次包含 {len(tasks)} 项解析任务："]
        for index, item in enumerate(tasks[:20], 1):
            if isinstance(item, dict):
                lines.append(
                    f"{index}. {self._safe_text(item.get('productName')) or '产品未标明'}，"
                    f"库位 {self._safe_text(item.get('warehouseName')) or '未标明'}，"
                    f"状态 {self._safe_text(item.get('status')) or '未知'}，"
                    f"风险 {self._safe_text(item.get('riskLevel')) or '未知'}。"
                )
        lines.append("详情不含原始报数文本和内部 ID；解析结果不等同于入库确认、质量放行或库存事实，本次未执行任何写操作。")
        return "".join(lines)

    def _format_warehouse_capacity_distribution_answer(self, result: dict[str, Any]) -> str:
        source = result if isinstance(result, dict) else {}
        records = source.get("records") if isinstance(source.get("records"), list) else []
        summary = source.get("summary") if isinstance(source.get("summary"), dict) else {}
        if not records:
            return "当前没有库位符合“快满”条件（当前固定展示口径为占用率达到 80% 但尚未满仓）。这不代表系统中没有库位，也不代表容量查询失败；本次未执行任何库位分配或库存变更。"
        lines = [
            f"当前条件下共有 {self._first_scalar([source], 'total') or 0} 个库位；"
            f"当前容量 {self._first_scalar([summary], 'currentCapacity') or 0}，"
            f"最大容量 {self._first_scalar([summary], 'maximumCapacity') or 0}，"
            f"剩余容量 {self._first_scalar([summary], 'remainingCapacity') or 0}。"
        ]
        for item in records[:20]:
            if isinstance(item, dict):
                lines.append(
                    f"{self._safe_text(item.get('warehouseName')) or '库位未标明'}："
                    f"{self._first_scalar([item], 'currentCapacity') or 0}/"
                    f"{self._first_scalar([item], 'maximumCapacity') or 0}，"
                    f"剩余 {self._first_scalar([item], 'remainingCapacity') or 0}，"
                    f"占用率 {self._first_scalar([item], 'occupancyRate') or 0}%。"
                )
        lines.append("这里的“快满”采用固定展示口径：占用率达到 80% 但尚未满仓；它不是业务风险判定、库位分配或调拨建议。本次未执行任何库存或库位变更。")
        return "".join(lines)

    def _format_warehouse_recent_operations_answer(self, result: dict[str, Any]) -> str:
        source = result if isinstance(result, dict) else {}
        records = source.get("records") if isinstance(source.get("records"), list) else []
        if not records:
            return "当前条件下未查询到已登记的库位托盘流转事件；这不同于查询服务失败。"
        lines = [f"当前条件下查询到 {len(records)} 条已登记的库位托盘流转事件："]
        for item in records[:20]:
            if isinstance(item, dict):
                lines.append(
                    f"{self._safe_text(item.get('operationTime')) or '时间未标明'}，"
                    f"{self._safe_text(item.get('eventType')) or '其他'}，"
                    f"{self._safe_text(item.get('productName')) or '产品未标明'}，"
                    f"从 {self._safe_text(item.get('fromWarehouseName')) or '未标明'} "
                    f"到 {self._safe_text(item.get('toWarehouseName')) or '未标明'}。"
                )
        lines.append("以上只覆盖系统 pallet_flow_record 中已登记的事件，不是完整操作日志或历史事件账；本次未执行入库、出库、调拨或库存变更。")
        return "".join(lines)

    def _format_warehouse_mixed_storage_facts_answer(self, result: dict[str, Any]) -> str:
        source = result if isinstance(result, dict) else {}
        records = source.get("records") if isinstance(source.get("records"), list) else []
        if not records:
            return "当前条件下未查询到同库位多产品或多规格事实；这不同于查询服务失败。"
        lines = [f"当前查询到 {len(records)} 个存在多产品或多规格事实的库位："]
        for item in records[:20]:
            if isinstance(item, dict):
                products = item.get("productLabels") if isinstance(item.get("productLabels"), list) else []
                lines.append(
                    f"{self._safe_text(item.get('warehouseName')) or '库位未标明'}："
                    f"产品数 {self._first_scalar([item], 'productCount') or 0}，"
                    f"产品/筛网/状态组合数 {self._first_scalar([item], 'specificationCount') or 0}，"
                    f"产品包括 {'、'.join(str(value) for value in products[:10]) if products else '未标明'}。"
                )
        lines.append("以上仅是当前同库位多产品或多规格的客观事实；由于混放规则和阈值尚未确认，不得解释为违规、风险或调拨建议，本次未执行任何库存操作。")
        return "".join(lines)

    def _format_product_catalog_answer(self, result: dict[str, Any]) -> str:
        source = result if isinstance(result, dict) else {}; records = source.get("records") if isinstance(source.get("records"), list) else []
        if not records:
            return "当前筛选条件下没有查询到产品主数据。这表示目录中没有匹配项，不代表产品目录服务查询失败，也不代表库存中没有产品。"
        lines = [f"当前产品目录共有 {self._first_scalar([source], 'total') or 0} 项，本页返回 {len(records)} 项："]
        for item in records[:20]:
            if isinstance(item, dict):
                lines.append(f"{self._safe_text(item.get('productName')) or '名称未标明'}，"
                             f"{self._safe_text(item.get('productType')) or '品类未标明'}，"
                             f"{self._safe_text(item.get('productStatus')) or '状态未标明'}，"
                             f"包装 {self._safe_text(item.get('packagingMethod')) or '未配置'}，"
                             f"筛网 {self._safe_text(item.get('screenMeshName')) or '未配置'}。")
        lines.append("以上仅为当前产品主数据配置，不代表库存、质量合格或生产可用性，本次未修改任何配置。")
        return "".join(lines)

    def _format_product_detail_answer(self, result: dict[str, Any]) -> str:
        source = result if isinstance(result, dict) else {}
        return (f"{self._safe_text(source.get('productName')) or '该产品'}：品类 "
                f"{self._safe_text(source.get('productType')) or '未配置'}，状态 "
                f"{self._safe_text(source.get('productStatus')) or '未配置'}，包装 "
                f"{self._safe_text(source.get('packagingMethod')) or '未配置'}，筛网 "
                f"{self._safe_text(source.get('screenMeshName')) or '未配置'}；"
                f"{self._safe_text(source.get('conversionSummary')) or '换算配置不完整'}。"
                "该详情只复述当前主数据配置，不推导库存数量、质量合格或装载建议，本次未修改配置。")

    def _format_screen_mesh_catalog_answer(self, result: dict[str, Any]) -> str:
        source = result if isinstance(result, dict) else {}; records = source.get("records") if isinstance(source.get("records"), list) else []
        lines = [f"当前筛网目录共有 {self._first_scalar([source], 'total') or 0} 项，本页返回 {len(records)} 项："]
        for item in records[:20]:
            if isinstance(item, dict):
                lines.append(f"{self._safe_text(item.get('meshName')) or '名称未标明'}：{self._safe_text(item.get('description')) or '无说明'}。")
        lines.append("以上仅为当前筛网目录配置，不代表产品当前实际使用情况，本次未修改任何配置。")
        return "".join(lines)

    def _format_assay_groups_answer(self, result: dict[str, Any]) -> str:
        source = result if isinstance(result, dict) else {}; records = source.get("records") if isinstance(source.get("records"), list) else []
        lines = [f"当前化验组目录共有 {self._first_scalar([source], 'total') or 0} 项："]
        for item in records[:20]:
            if isinstance(item, dict): lines.append(f"{self._safe_text(item.get('groupName')) or '名称未标明'}，包含 {len(item.get('productNames') or [])} 个产品。")
        lines.append("化验组只表示当前产品分组配置，不是质量标准、合格结论或库存批次范围，本次未修改配置。")
        return "".join(lines)

    def _format_quality_standard_catalog_answer(self, result: dict[str, Any]) -> str:
        source = result if isinstance(result, dict) else {}; records = source.get("records") if isinstance(source.get("records"), list) else []
        lines = [f"当前质量标准目录共有 {self._first_scalar([source], 'total') or 0} 项："]
        for item in records[:20]:
            if isinstance(item, dict): lines.append(f"{self._safe_text(item.get('standardCode')) or '代码未标明'} {self._safe_text(item.get('standardName')) or '名称未标明'}，版本 {self._first_scalar([item], 'version') or 0}，状态为{self._enum_label('enabled_status', item.get('status'))}。")
        lines.append("目录只表示当前标准配置与版本状态，不证明某次化验实际采用该标准，本次未修改配置。")
        return "".join(lines)

    def _format_quality_standard_detail_answer(self, result: dict[str, Any]) -> str:
        source = result if isinstance(result, dict) else {}; metrics = source.get("metrics") if isinstance(source.get("metrics"), list) else []
        return (f"质量标准 {self._safe_text(source.get('standardCode')) or '代码未标明'} "
                f"{self._safe_text(source.get('standardName')) or '名称未标明'}，版本 {self._first_scalar([source], 'version') or 0}，"
                f"状态 {self._safe_text(source.get('status')) or '未标明'}，包含 {len(metrics)} 个指标。"
                "该详情是当前配置快照，不证明某份报告采用此版本；最终质量判定仍由确定性服务执行。")

    def _format_product_standard_relations_answer(self, result: dict[str, Any]) -> str:
        source = result if isinstance(result, dict) else {}; records = source.get("records") if isinstance(source.get("records"), list) else []
        lines = [f"{self._safe_text(source.get('productName')) or '该产品'}当前配置了 {len(records)} 条质量标准关系："]
        for item in records[:20]:
            if isinstance(item, dict): lines.append(f"{self._safe_text(item.get('standardCode')) or '代码未标明'} {self._safe_text(item.get('standardName')) or '名称未标明'}，版本 {self._first_scalar([item], 'standardVersion') or 0}，{'默认' if item.get('isDefault') else '非默认'}，{'启用' if item.get('enabled') else '停用'}。")
        lines.append("绑定关系不证明某次化验采用该标准或产品已合格，本次未修改任何绑定。")
        return "".join(lines)

    def _format_employee_roster_answer(self, result: dict[str, Any]) -> str:
        source = result if isinstance(result, dict) else {}; records = source.get("records") if isinstance(source.get("records"), list) else []
        lines = [f"当前员工名册共匹配 {self._first_scalar([source], 'total') or 0} 人："]
        for item in records[:20]:
            if isinstance(item, dict): lines.append(f"{self._safe_text(item.get('employeeId')) or '工号未标明'} {self._safe_text(item.get('name')) or '姓名未标明'}，{self._safe_text(item.get('department')) or '部门未标明'}，{self._safe_text(item.get('position')) or '职位未标明'}，状态 {self._safe_text(item.get('status')) or '未标明'}，手机号 {self._safe_text(item.get('maskedMobile')) or '未提供'}。")
        lines.append("手机号已脱敏；本结果不包含登录凭据、绑定信息或内部记录 ID，也不表示员工当前已登录或实际在岗。")
        return "".join(lines)

    def _format_role_catalog_answer(self, result: dict[str, Any]) -> str:
        source = result if isinstance(result, dict) else {}; records = source.get("records") if isinstance(source.get("records"), list) else []
        lines = [f"当前角色目录共匹配 {self._first_scalar([source], 'total') or 0} 项："]
        for item in records[:20]:
            if isinstance(item, dict): lines.append(f"{self._safe_text(item.get('roleCode')) or '编码未标明'} {self._safe_text(item.get('roleName')) or '名称未标明'}，状态 {self._safe_text(item.get('status')) or '未标明'}，权限 {self._first_scalar([item], 'permissionCount') or 0} 项，在职员工 {self._first_scalar([item], 'activeEmployeeCount') or 0} 人。")
        lines.append("目录仅反映当前角色配置；实际访问仍需按登录身份和 Java Gateway RBAC 校验。")
        return "".join(lines)

    def _format_role_permission_summary_answer(self, result: dict[str, Any]) -> str:
        source = result if isinstance(result, dict) else {}; permissions = source.get("permissions") if isinstance(source.get("permissions"), list) else []
        lines = [f"角色 {self._safe_text(source.get('roleCode')) or '编码未标明'} {self._safe_text(source.get('roleName')) or '名称未标明'} 当前配置 {len(permissions)} 项权限："]
        for item in permissions[:50]:
            if isinstance(item, dict): lines.append(f"[{self._safe_text(item.get('permissionGroup')) or 'OTHER'}] {self._safe_text(item.get('permissionName')) or self._safe_text(item.get('permissionCode')) or '权限未标明'}。")
        lines.append("这是角色配置摘要，不包含内部权限 ID、Agent 白名单或密钥；每次业务查询仍会重新执行 RBAC 鉴权。")
        return "".join(lines)

    def _format_operation_logs_answer(self, result: dict[str, Any]) -> str:
        source = result if isinstance(result, dict) else {}; records = source.get("records") if isinstance(source.get("records"), list) else []
        lines = [f"已登记业务操作日志共匹配 {self._first_scalar([source], 'total') or 0} 条："]
        for item in records[:20]:
            if isinstance(item, dict): lines.append(f"{self._safe_text(item.get('operationTime')) or '时间未标明'}，{self._safe_text(item.get('operator')) or '操作人未标明'}对 {self._safe_text(item.get('module')) or '模块未标明'}执行{self._enum_label('operation_type', item.get('operationType'))}；变更字段数 {len(item.get('changedFieldNames') or [])}。")
        lines.append("仅展示字段名称摘要，不包含修改前后值、请求正文、凭据或原始异常；未登记事件不能据此判定为从未发生。")
        return "".join(lines)

    def _format_agent_tool_audit_answer(self, result: dict[str, Any]) -> str:
        source = result if isinstance(result, dict) else {}; records = source.get("records") if isinstance(source.get("records"), list) else []
        lines = [f"Agent 工具调用审计共匹配 {self._first_scalar([source], 'total') or 0} 条："]
        for item in records[:20]:
            if isinstance(item, dict): lines.append(f"{self._safe_text(item.get('occurredAt')) or '时间未标明'}，能力 {self._safe_text(item.get('capability')) or '未标明'}，结果 {self._safe_text(item.get('resultCode')) or '未标明'}，错误类别 {self._safe_text(item.get('errorCode')) or '无'}，耗时 {self._first_scalar([item], 'durationMs') or 0} ms。")
        lines.append("不展示会话、用户、消息、调用内部 ID、参数、Prompt、模型上下文、密钥或原始堆栈。")
        return "".join(lines)

    def _format_agent_answer_reviews_answer(self, result: dict[str, Any]) -> str:
        source = result if isinstance(result, dict) else {}; records = source.get("records") if isinstance(source.get("records"), list) else []
        lines = [f"Agent 回答 Review 安全摘要共匹配 {self._first_scalar([source], 'total') or 0} 条："]
        for item in records[:20]:
            if isinstance(item, dict): lines.append(f"{self._safe_text(item.get('createdAt')) or '时间未标明'}，回答状态 {self._safe_text(item.get('answerStatus')) or '未标明'}，置信度 {self._safe_text(item.get('confidenceLevel')) or '未标明'}，失败域 {self._safe_text(item.get('failureDomain')) or '无'}，Review 状态 {self._safe_text(item.get('reviewStatus')) or '未标明'}。")
        lines.append("不展示用户原问题、助手原回答、工具名称、决策快照、证据正文或内部身份；Review 状态不是业务事实或自动处罚依据。")
        return "".join(lines)

    def _format_inventory_ledger_answer(self, result: dict[str, Any]) -> str:
        source = result if isinstance(result, dict) else {}; records = source.get("records") if isinstance(source.get("records"), list) else []
        lines = [f"当前库存台账共匹配 {self._first_scalar([source], 'total') or 0} 行（截至 {self._safe_text(source.get('inventoryAsOf')) or '查询时'}）："]
        for item in records[:20]:
            if isinstance(item, dict): lines.append(f"{self._safe_text(item.get('warehouseName')) or '库位未标明'}，{self._safe_text(item.get('productName')) or '产品未标明'}，位置 {self._safe_text(item.get('location')) or '未标明'}，{self._first_scalar([item], 'palletQuantity') or 0} 板 {self._first_scalar([item], 'pieces') or 0} 件，入库日期 {self._safe_text(item.get('entryDate')) or '未标明'}。")
        lines.append("这是当前库存行快照，不是完整历史流水；生产日期可能为空，也不证明当前库存批次质量合格，本次未修改库存。")
        return "".join(lines)

    def _format_prepare_pool_balance_answer(self, result: dict[str, Any]) -> str:
        source = result if isinstance(result, dict) else {}; records = source.get("records") if isinstance(source.get("records"), list) else []
        lines = [f"历史半成品备料池当前正余额共匹配 {self._first_scalar([source], 'total') or 0} 条："]
        for item in records[:20]:
            if isinstance(item, dict): lines.append(f"{self._safe_text(item.get('productName')) or '产品未标明'}，生产日期 {self._safe_text(item.get('productionDate')) or '未标明'}，入池 {self._first_scalar([item], 'inPieces') or 0} 件，已用 {self._first_scalar([item], 'consumedPieces') or 0} 件，剩余 {self._first_scalar([item], 'remainingPieces') or 0} 件。")
        lines.append("仅包含现存正余额，不是完整历史账；余额不等于已为订单保留、质量合格或可直接领用，本次未执行占用或领用。")
        return "".join(lines)

    def _format_fixed_product_qr_pool_answer(self, result: dict[str, Any]) -> str:
        source = result if isinstance(result, dict) else {}; records = source.get("records") if isinstance(source.get("records"), list) else []
        lines = [f"固定产品二维码池共匹配 {self._first_scalar([source], 'total') or 0} 个码："]
        for item in records[:20]:
            if isinstance(item, dict): lines.append(f"{self._safe_text(item.get('code')) or '二维码未标明'}，固定产品 {self._safe_text(item.get('fixedProductName')) or '未标明'}，状态为{self._enum_label('qr_status', item.get('status'))}，{'当前可打印' if item.get('allowPrint') else '当前不可打印'}。")
        lines.append("可打印只表示当前码池状态条件满足，不表示标签已打印、码已启用或已创建入库任务；本次未执行绑定、打印、启用、作废、恢复或库存操作。")
        return "".join(lines)

    def _enum_label(self, category: str, value: Any) -> str:
        raw = self._safe_text(value)
        labels = {
            "task_type": {
                "IN": "入库", "INBOUND": "入库", "OUT": "出库", "OUTBOUND": "出库",
                "TRANSFER": "调拨", "SEMI_IN": "半成品入库", "SEMI_OUT": "半成品出库",
                "FINISH_IN": "成品入库", "FINISH_OUT": "成品出库",
            },
            "task_status": {
                "PENDING": "待处理", "PROCESSING": "处理中", "COMPLETED": "已完成",
                "CONFIRMED": "已确认", "CANCELED": "已取消", "CANCELLED": "已取消", "FAILED": "处理失败",
            },
            "qr_status": {
                "FREE": "空闲、可使用", "BOUND": "已绑定", "ACTIVE": "已启用",
                "INSTOCK": "已入库", "OUTSTOCK": "已出库", "VOID": "已作废",
                "RECYCLED": "已回收", "PRINTED": "已打印",
            },
            "material_status": {
                "PENDING": "待处理", "PICKED": "已领用", "IN_PROCESS": "生产中",
                "CONSUMED": "已消耗", "COMPLETED": "已完成", "CANCELLED": "已取消",
            },
            "enabled_status": {
                "ENABLED": "已启用", "DISABLED": "已停用", "ACTIVE": "已启用", "INACTIVE": "已停用",
            },
            "operation_type": {
                "CREATE": "新增", "INSERT": "新增", "UPDATE": "修改", "DELETE": "删除",
                "IMPORT": "导入", "EXPORT": "导出", "LOGIN": "登录", "LOGOUT": "退出登录",
            },
        }
        return labels.get(category, {}).get(raw or "", raw or "未知")

    def _latest_user_message(self, state: WarehouseAgentState) -> str:
        for message in reversed(state.messages):
            if message.get("role") == "user":
                content = message.get("content")
                if isinstance(content, str):
                    return content
        return ""

    def _is_context_product_followup(self, text: str) -> bool:
        return self._has_context_reference(text) and "库存" in text

    def _is_inventory_location_followup(self, text: str) -> bool:
        return self._has_context_reference(text) and any(word in text for word in ["存放", "在哪", "哪些库位"])

    def _has_context_reference(self, text: str) -> bool:
        return any(word in text for word in ["这些", "它", "刚才", "这个", "该产品"])

    def _has_warehouse_semantics(self, text: str) -> bool:
        return any(word in text for word in ["库位", "仓库", "容量", "位置"])

    def _list_size(self, value: Any) -> int:
        return len(value) if isinstance(value, list) else 0

    def _first_candidate(self, result: dict[str, Any]) -> dict[str, Any]:
        candidates = result.get("candidates") or result.get("options") or []
        if isinstance(candidates, list) and candidates:
            first = candidates[0]
            return first if isinstance(first, dict) else {}
        return {}

    def _safe_display_label(self, label: str) -> str:
        cleaned = re.sub(r"\s*\(#\d+\)", "", label or "")
        cleaned = re.sub(r"(?<![A-Za-z0-9])#\d+", "", cleaned)
        return " ".join(cleaned.split()).strip() or "候选项"

    def _dict_value(self, value: Any) -> dict[str, Any]:
        return value if isinstance(value, dict) else {}

    def _first_safe_text(self, sources: list[dict[str, Any]], *keys: str) -> str | None:
        for source in sources:
            for key in keys:
                value = self._safe_text(source.get(key))
                if value is not None:
                    return value
        return None

    def _first_scalar(self, sources: list[dict[str, Any]], *keys: str) -> int | float | str | None:
        for source in sources:
            for key in keys:
                value = source.get(key)
                if isinstance(value, bool) or value is None or isinstance(value, (dict, list, tuple, set)):
                    continue
                if isinstance(value, (int, float)):
                    return value
                safe = self._safe_text(value)
                if safe is not None:
                    return safe
        return None

    def _safe_text(self, value: Any) -> str | None:
        if value is None or isinstance(value, (dict, list, tuple, set)):
            return None
        text = str(value).strip()
        if not text:
            return None
        if re.search(
            r"(?i)(authorization|bearer\s+|delegationtoken|refresh[_ -]?token|stack\s*trace|jdbc:|"
            r"(?:[a-z]:\\|/)(?:users|home|var|opt|srv|windows)(?:\\|/))",
            text,
        ):
            return None
        return text

    def _subtract_scalars(
        self, maximum: int | float | str | None, used: int | float | str | None
    ) -> int | float | None:
        if isinstance(maximum, bool) or isinstance(used, bool):
            return None
        try:
            remaining = float(maximum) - float(used)
        except (TypeError, ValueError):
            return None
        if remaining < 0:
            remaining = 0
        return int(remaining) if remaining.is_integer() else round(remaining, 4)

    def _format_rate(self, value: int | float | str) -> str:
        text = str(value).strip()
        return text if text.endswith("%") else f"{text}%"

    def _warehouse_display_name(self, value: str) -> str:
        safe = self._safe_display_label(value)
        return f"{safe}号库位" if safe.isdigit() else safe

    def _product_description(self, option: dict[str, Any]) -> str | None:
        pieces = option.get("piecesPerPallet")
        weight = option.get("weightPerPiece")
        details = []
        if weight is not None:
            details.append(f"{weight}kg/件")
        if pieces is not None:
            details.append(f"{pieces}件/板")
        return " ".join(details) if details else option.get("description")

    def _int_value(self, mapping: dict[str, Any], key: str) -> int | None:
        value = mapping.get(key) if isinstance(mapping, dict) else None
        if value is None:
            return None
        try:
            parsed = int(value)
        except (TypeError, ValueError):
            return None
        return parsed if parsed > 0 else None
