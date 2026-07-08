from __future__ import annotations

from datetime import date
from datetime import datetime, timedelta, timezone
import hashlib
import re
import secrets
from collections.abc import Iterator
from typing import Any

from app.cancellation import current_cancellation_token
from app.graph.state import (
    InMemoryCheckpointer,
    PendingClarification,
    SelectedEntity,
    WarehouseAgentState,
)
from app.schemas import (
    AgentError,
    BusinessCard,
    ChatRequest,
    ChatResponse,
    ResumeRequest,
    SafeInventoryDistributionGroup,
    SafeInventoryDistributionResult,
    SafeInventoryLocation,
    SafeInventoryResult,
    SafeWarehouseResult,
    UserOption,
)
from app.tool_arguments import ToolArgumentBuilder
from app.tools.client import AgentToolClient, ToolGatewayError


class WarehouseAgentRuntime:
    """Minimal LangGraph-compatible runtime for M1.3R."""

    RESUME_TOKEN_TTL = timedelta(minutes=10)

    def __init__(
        self,
        tool_client: AgentToolClient,
        checkpointer: InMemoryCheckpointer | None = None,
        argument_builder: ToolArgumentBuilder | None = None,
    ) -> None:
        self.tool_client = tool_client
        self.checkpointer = checkpointer or InMemoryCheckpointer()
        self.argument_builder = argument_builder or ToolArgumentBuilder()

    def chat(self, request: ChatRequest) -> ChatResponse:
        state = self.checkpointer.get(request.agentSessionId)
        text = request.message.content.strip()
        self._raise_if_cancelled()
        state.messages.append({"role": "user", "content": text})

        try:
            response = self._handle_message(request, state, text)
        except ToolGatewayError as exc:
            response = self._tool_error_response(request.agentSessionId, exc)

        self._raise_if_cancelled()
        state.messages.append({"role": "assistant", "content": response.answer})
        self.checkpointer.save(request.agentSessionId, state)
        return response

    def stream_answer_deltas(self, answer: str) -> Iterator[str]:
        yield from self.argument_builder.stream_answer_deltas(answer)

    def resume(self, request: ResumeRequest) -> ChatResponse:
        state = self.checkpointer.get(request.agentSessionId)
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
            )
            state.pending_clarification = None
            response = self._continue_product_intent(
                request.agentSessionId,
                state,
                intent,
                request.client.traceId,
                request.client.requestId,
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
            )
            state.pending_clarification = None
            if intent == "inventory_distribution":
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
                    distribution_args,
                )
            else:
                response = self._answer_warehouse_status(
                    request.agentSessionId,
                    state,
                    request.client.traceId,
                    request.client.requestId,
                )
        else:
            response = ChatResponse(agentSessionId=request.agentSessionId, answer="已记录你的选择。")

        self._cache_resume_response(state, pending.interrupt_id, client_request_id, response)
        self._raise_if_cancelled()
        state.messages.append({"role": "assistant", "content": response.answer})
        self.checkpointer.save(request.agentSessionId, state)
        return response

    def _handle_message(self, request: ChatRequest, state: WarehouseAgentState, text: str) -> ChatResponse:
        try:
            plan = self.argument_builder.plan(user_message=text, state=state)
        except ValueError:
            return ChatResponse(
                agentSessionId=request.agentSessionId,
                answer="当前我只能使用受控的只读仓储工具。请补充要查询的产品、库位、托盘码或生产日期。",
                needsUserSelection=True,
            )
        state.messages.append(
            {
                "role": "planner",
                "action": plan.action,
                "toolName": plan.toolName,
                "intent": plan.intent,
                "responseMode": plan.responseMode,
            }
        )
        return self._execute_plan(request, state, plan)

    def _execute_plan(self, request: ChatRequest, state: WarehouseAgentState, plan: Any) -> ChatResponse:
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
                answer="当前我只能使用受控的只读仓储工具。请补充要查询的产品、库位、托盘码或生产日期。",
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
                    plan.arguments,
                )
            return self._answer_inventory(
                request.agentSessionId,
                state,
                request.client.traceId,
                request.client.requestId,
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
            return ChatResponse(agentSessionId=request.agentSessionId, answer=self._format_assay_answer(label, result))
        if plan.toolName == "get_pallet_status":
            result = self._call_tool(request, "get_pallet_status", plan.arguments)
            self._raise_if_cancelled()
            state.last_pallet_result = result
            self._record_tool_message(state, "get_pallet_status", self._safe_tool_summary("get_pallet_status", result))
            return ChatResponse(agentSessionId=request.agentSessionId, answer=self._format_pallet_answer(result))

        return ChatResponse(
            agentSessionId=request.agentSessionId,
            answer="当前工具不在只读白名单内，无法执行。",
            needsUserSelection=True,
        )

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
        )

    def _continue_product_intent(
        self,
        agent_session_id: str,
        state: WarehouseAgentState,
        intent: str,
        trace_id: str | None,
        request_id: str | None,
    ) -> ChatResponse:
        if intent == "assay":
            if state.selected_product is None or state.selected_product.internal_id is None:
                return ChatResponse(agentSessionId=agent_session_id, answer="化验查询需要选择一个具体产品规格。", needsUserSelection=True)
            result = self._call_tool_values(
                agent_session_id=agent_session_id,
                tool_name="get_assay_status",
                arguments={"productId": state.selected_product.internal_id, "productionDate": date.today().isoformat()},
                trace_id=trace_id,
                request_id=request_id,
            )
            self._raise_if_cancelled()
            state.last_assay_result = result
            self._record_tool_message(state, "get_assay_status", self._safe_tool_summary("get_assay_status", result))
            return ChatResponse(
                agentSessionId=agent_session_id,
                answer=self._format_assay_answer(state.selected_product.display_label, result),
            )
        if intent == "inventory_distribution":
            return self._answer_inventory_distribution(agent_session_id, state, trace_id, request_id)
        if state.selected_product is None or state.selected_product.internal_id is None:
            return ChatResponse(agentSessionId=agent_session_id, answer="库存概览需要选择一个具体产品规格。", needsUserSelection=True)
        return self._answer_inventory(agent_session_id, state, trace_id, request_id)

    def _answer_inventory(
        self,
        agent_session_id: str,
        state: WarehouseAgentState,
        trace_id: str | None,
        request_id: str | None,
        arguments: dict[str, Any] | None = None,
    ) -> ChatResponse:
        tool_arguments = arguments or {"productId": state.selected_product.internal_id}
        result = self._call_tool_values(
            agent_session_id=agent_session_id,
            tool_name="get_inventory_overview",
            arguments=tool_arguments,
            trace_id=trace_id,
            request_id=request_id,
        )
        safe_result = self._adapt_inventory_result(result)
        self._raise_if_cancelled()
        state.last_inventory_result = safe_result.model_dump(exclude_none=True)
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
            )
            safe_result = self._adapt_inventory_result(result)
            self._raise_if_cancelled()
            state.last_inventory_result = safe_result.model_dump(exclude_none=True)
            self._record_tool_message(
                state,
                "get_inventory_overview",
                self._safe_tool_summary("get_inventory_overview", safe_result.model_dump(exclude_none=True)),
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
        arguments: dict[str, Any] | None = None,
    ) -> ChatResponse:
        if arguments is None:
            arguments = self.argument_builder.distribution_arguments_for_state({}, state)
        raw = self._call_tool_values(
            agent_session_id=agent_session_id,
            tool_name="get_inventory_distribution",
            arguments=arguments,
            trace_id=trace_id,
            request_id=request_id,
        )
        self._raise_if_tool_error_payload(raw)
        safe_result = self._adapt_inventory_distribution(raw, arguments, state)
        self._raise_if_cancelled()
        state.last_inventory_distribution = safe_result.model_dump(exclude_none=True)
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
                distribution_args,
            )
        return self._answer_warehouse_status(
            request.agentSessionId,
            state,
            request.client.traceId,
            request.client.requestId,
        )

    def _answer_warehouse_status(
        self,
        agent_session_id: str,
        state: WarehouseAgentState,
        trace_id: str | None,
        request_id: str | None,
        arguments: dict[str, Any] | None = None,
    ) -> ChatResponse:
        tool_arguments = arguments or {"warehouseId": state.selected_warehouse.internal_id}
        result = self._call_tool_values(
            agent_session_id=agent_session_id,
            tool_name="get_warehouse_status",
            arguments=tool_arguments,
            trace_id=trace_id,
            request_id=request_id,
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
        )

    def _call_tool_values(
        self,
        *,
        agent_session_id: str,
        tool_name: str,
        arguments: dict[str, Any],
        trace_id: str | None,
        request_id: str | None,
    ) -> dict[str, Any]:
        self._raise_if_cancelled()
        result = self.tool_client.call_tool(
            agent_session_id=agent_session_id,
            tool_name=tool_name,
            arguments=arguments,
            trace_id=trace_id,
            request_id=request_id,
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

    def _product_clarification(self, result: dict[str, Any], intent: str, request: ChatRequest) -> PendingClarification:
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
            user_id=request.user.userId if request.user else None,
        )

    def _warehouse_clarification(self, result: dict[str, Any], request: ChatRequest, intent: str = "warehouse_status") -> PendingClarification:
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
            user_id=request.user.userId if request.user else None,
        )

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
            metadata={"scopeType": "SINGLE_PRODUCT"},
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
        return SelectedEntity(internal_id=warehouse_id, display_label=label, source="resolver")

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
        return "，".join(parts) + "。"

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
        return summary

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
        risk_summary = self._distribution_risk_summary(result)
        if risk_summary:
            fields.append({"kind": "risk_summary", "label": "风险提示", "value": risk_summary})
        return BusinessCard(
            cardType="inventory_distribution",
            title=f"{result.scopeLabel}库存分布",
            fields=fields,
        )

    def _distribution_risk_summary(self, result: SafeInventoryDistributionResult) -> str | None:
        risk_counts: dict[str, int] = {}
        for group in result.groups:
            for risk in group.riskLabels:
                risk_counts[risk] = risk_counts.get(risk, 0) + 1
        if not risk_counts:
            return None
        return "；".join(f"{risk}（{count}项）" for risk, count in sorted(risk_counts.items()))

    def _distribution_group_product_name(self, group: SafeInventoryDistributionGroup) -> str | None:
        source = group.productLabel or group.groupLabel
        text = self._safe_text(source)
        if not text:
            return None
        text = re.split(r"\s+\d+(?:\.\d+)?kg/件\b", text, maxsplit=1)[0].strip()
        text = re.split(r"\s+\d+件/板\b", text, maxsplit=1)[0].strip()
        text = re.sub(r"^\d+\.\s*", "", text).strip()
        return text or None

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

    def _format_assay_answer(self, label: str, result: dict[str, Any]) -> str:
        if result.get("needsAssay") is True:
            return f"{label}没有查询到对应日期的化验记录，不能判断合格或不合格。"
        judge = self._safe_text(result.get("judgeResult") or result.get("result"))
        if judge:
            return f"{label}的化验结果为 {judge}。"
        return f"{label}的化验状态已查询到，但返回字段不足以判断合格或不合格。"

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
        state.messages.append(
            {
                "role": "tool",
                "toolName": tool_name,
                "content": summary,
            }
        )
        state.tool_results.append({"kind": tool_name, "summary": summary})

    def _safe_tool_summary(self, tool_name: str, result: dict[str, Any]) -> dict[str, Any]:
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
        if tool_name == "get_pallet_status":
            info = self._dict_value(result.get("palletInfo") or result.get("baseInfo") or result)
            return {"status": self._safe_text(info.get("status") or info.get("bindStatus"))}
        return {}

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
