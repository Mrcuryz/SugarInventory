from __future__ import annotations

from datetime import date
from typing import Any

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
    UserOption,
)
from app.tools.client import AgentToolClient, ToolGatewayError


class WarehouseAgentRuntime:
    """Minimal LangGraph-compatible runtime for M1.3R-1c."""

    def __init__(self, tool_client: AgentToolClient, checkpointer: InMemoryCheckpointer | None = None) -> None:
        self.tool_client = tool_client
        self.checkpointer = checkpointer or InMemoryCheckpointer()

    def chat(self, request: ChatRequest) -> ChatResponse:
        state = self.checkpointer.get(request.agentSessionId)
        text = request.message.content.strip()
        state.messages.append({"role": "user", "content": text})

        try:
            response = self._handle_message(request, state, text)
        except ToolGatewayError as exc:
            response = self._tool_error_response(request.agentSessionId, exc)

        state.messages.append({"role": "assistant", "content": response.answer})
        self.checkpointer.save(request.agentSessionId, state)
        return response

    def resume(self, request: ResumeRequest) -> ChatResponse:
        state = self.checkpointer.get(request.agentSessionId)
        selection = request.event.selection
        state.messages.append(
            {
                "role": "user",
                "event": "candidate_selected",
                "displayLabel": selection.displayLabel,
            }
        )

        if state.pending_clarification is None:
            response = ChatResponse(
                agentSessionId=request.agentSessionId,
                answer="当前没有等待选择的业务候选项，请重新描述要查询的内容。",
                needsUserSelection=True,
            )
            self.checkpointer.save(request.agentSessionId, state)
            return response

        option = self._find_pending_option(state.pending_clarification, selection.optionId, selection.displayLabel)
        if option is None:
            response = ChatResponse(
                agentSessionId=request.agentSessionId,
                answer="没有找到你选择的候选项，请重新选择。",
                needsUserSelection=True,
                cards=[self._clarification_card(state.pending_clarification)],
            )
            self.checkpointer.save(request.agentSessionId, state)
            return response

        if option.get("supported") is False:
            response = ChatResponse(
                agentSessionId=request.agentSessionId,
                answer=f"{option.get('displayLabel', '该选项')}当前暂不支持直接查询。",
                suggestions=["请选择一个具体产品或库位。"],
            )
            self.checkpointer.save(request.agentSessionId, state)
            return response

        internal = option.get("_internal", {})
        intent = state.pending_clarification.intent
        if state.pending_clarification.kind == "product":
            product_id = self._int_value(internal, "productId")
            if product_id is None:
                response = ChatResponse(
                    agentSessionId=request.agentSessionId,
                    answer="该产品候选缺少可校验的内部映射，请重新查询。",
                    needsUserSelection=True,
                )
                self.checkpointer.save(request.agentSessionId, state)
                return response
            state.selected_product = SelectedEntity(
                internal_id=product_id,
                display_label=str(option.get("displayLabel") or "所选产品"),
                source="user_selection",
            )
            state.pending_clarification = None
            response = self._continue_product_intent(
                request.agentSessionId,
                state,
                intent,
                request.client.traceId,
                request.client.requestId,
            )
        elif state.pending_clarification.kind == "warehouse":
            warehouse_id = self._int_value(internal, "warehouseId")
            if warehouse_id is None:
                response = ChatResponse(
                    agentSessionId=request.agentSessionId,
                    answer="该库位候选缺少可校验的内部映射，请重新查询。",
                    needsUserSelection=True,
                )
                self.checkpointer.save(request.agentSessionId, state)
                return response
            state.selected_warehouse = SelectedEntity(
                internal_id=warehouse_id,
                display_label=str(option.get("displayLabel") or "所选库位"),
                source="user_selection",
            )
            state.pending_clarification = None
            response = self._answer_warehouse_status(
                request.agentSessionId,
                state,
                request.client.traceId,
                request.client.requestId,
            )
        else:
            response = ChatResponse(agentSessionId=request.agentSessionId, answer="已记录你的选择。")

        state.messages.append({"role": "assistant", "content": response.answer})
        self.checkpointer.save(request.agentSessionId, state)
        return response

    def _handle_message(self, request: ChatRequest, state: WarehouseAgentState, text: str) -> ChatResponse:
        if self._is_context_product_followup(text):
            if state.selected_product is None:
                return ChatResponse(
                    agentSessionId=request.agentSessionId,
                    answer="你想查哪个产品？请先选择或输入一个明确的产品名称。",
                    needsUserSelection=True,
                    suggestions=["例如：黄冰糖（袋）库存。"],
                )
            return self._answer_inventory(
                request.agentSessionId,
                state,
                request.client.traceId,
                request.client.requestId,
            )

        if "库位" in text or "容量" in text:
            query = self._extract_warehouse_query(text)
            if query:
                return self._resolve_warehouse_and_continue(request, state, query)
            if state.selected_warehouse is not None:
                return self._answer_warehouse_status(
                    request.agentSessionId,
                    state,
                    request.client.traceId,
                    request.client.requestId,
                )
            return ChatResponse(
                agentSessionId=request.agentSessionId,
                answer="你想查哪个库位？请提供库位名称，例如 2号库位。",
                needsUserSelection=True,
            )

        if "托盘" in text:
            code = self._extract_after_keywords(text, ["托盘码", "托盘"])
            if not code:
                return ChatResponse(
                    agentSessionId=request.agentSessionId,
                    answer="请提供要查询的托盘码。",
                    needsUserSelection=True,
                )
            result = self._call_tool(request, "get_pallet_status", {"code": code})
            state.last_pallet_result = result
            return ChatResponse(agentSessionId=request.agentSessionId, answer=self._format_pallet_answer(result))

        if "化验" in text:
            if state.selected_product is not None and self._has_context_reference(text):
                result = self._call_tool(
                    request,
                    "get_assay_status",
                    {"productId": state.selected_product.internal_id, "productionDate": date.today().isoformat()},
                )
                state.last_assay_result = result
                return ChatResponse(
                    agentSessionId=request.agentSessionId,
                    answer=self._format_assay_answer(state.selected_product.display_label, result),
                )
            query = self._extract_product_query(text)
            if not query:
                return ChatResponse(
                    agentSessionId=request.agentSessionId,
                    answer="你想查询哪个产品的化验？请提供明确产品名称或先选择产品。",
                    needsUserSelection=True,
                )
            return self._resolve_product_and_continue(request, state, query, "assay")

        if "库存" in text:
            query = self._extract_product_query(text)
            if not query and state.selected_product is not None:
                return self._answer_inventory(
                    request.agentSessionId,
                    state,
                    request.client.traceId,
                    request.client.requestId,
                )
            if not query:
                return ChatResponse(
                    agentSessionId=request.agentSessionId,
                    answer="你想查哪个产品的库存？请先输入明确产品名称。",
                    needsUserSelection=True,
                )
            return self._resolve_product_and_continue(request, state, query, "inventory")

        return ChatResponse(
            agentSessionId=request.agentSessionId,
            answer="当前我只支持库存、库位、托盘和化验的只读查询。请补充要查询的产品、库位、托盘码或生产日期。",
            needsUserSelection=True,
        )

    def _resolve_product_and_continue(
        self, request: ChatRequest, state: WarehouseAgentState, query: str, intent: str
    ) -> ChatResponse:
        result = self._call_tool(request, "resolve_products", {"query": query, "limit": 10})
        status = str(result.get("resolutionStatus") or "").upper()
        if status == "AMBIGUOUS":
            pending = self._product_clarification(result, intent)
            state.pending_clarification = pending
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
            result = self.tool_client.call_tool(
                agent_session_id=agent_session_id,
                tool_name="get_assay_status",
                arguments={"productId": state.selected_product.internal_id, "productionDate": date.today().isoformat()},
                trace_id=trace_id,
                request_id=request_id,
            )
            state.last_assay_result = result
            return ChatResponse(
                agentSessionId=agent_session_id,
                answer=self._format_assay_answer(state.selected_product.display_label, result),
            )
        return self._answer_inventory(agent_session_id, state, trace_id, request_id)

    def _answer_inventory(
        self,
        agent_session_id: str,
        state: WarehouseAgentState,
        trace_id: str | None,
        request_id: str | None,
    ) -> ChatResponse:
        result = self.tool_client.call_tool(
            agent_session_id=agent_session_id,
            tool_name="get_inventory_overview",
            arguments={"productId": state.selected_product.internal_id},
            trace_id=trace_id,
            request_id=request_id,
        )
        state.last_inventory_result = result
        state.tool_results.append({"name": "get_inventory_overview", "summary": self._inventory_summary(result)})
        return ChatResponse(
            agentSessionId=agent_session_id,
            answer=self._format_inventory_answer(state.selected_product.display_label, result),
        )

    def _resolve_warehouse_and_continue(
        self, request: ChatRequest, state: WarehouseAgentState, query: str
    ) -> ChatResponse:
        result = self._call_tool(request, "resolve_warehouses", {"query": query, "limit": 10})
        status = str(result.get("resolutionStatus") or "").upper()
        if status == "AMBIGUOUS":
            pending = self._warehouse_clarification(result)
            state.pending_clarification = pending
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
        state.selected_warehouse = entity
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
    ) -> ChatResponse:
        result = self.tool_client.call_tool(
            agent_session_id=agent_session_id,
            tool_name="get_warehouse_status",
            arguments={"warehouseId": state.selected_warehouse.internal_id},
            trace_id=trace_id,
            request_id=request_id,
        )
        state.last_warehouse_result = result
        return ChatResponse(
            agentSessionId=agent_session_id,
            answer=self._format_warehouse_answer(state.selected_warehouse.display_label, result),
        )

    def _call_tool(self, request: ChatRequest, tool_name: str, arguments: dict[str, Any]) -> dict[str, Any]:
        return self.tool_client.call_tool(
            agent_session_id=request.agentSessionId,
            tool_name=tool_name,
            arguments=arguments,
            trace_id=request.client.traceId,
            request_id=request.client.requestId,
        )

    def _product_clarification(self, result: dict[str, Any], intent: str) -> PendingClarification:
        raw_options = result.get("options") or result.get("candidates") or []
        options = []
        for idx, option in enumerate(raw_options[:10], start=1):
            label = str(option.get("displayLabel") or option.get("productName") or option.get("name") or "候选产品")
            product_id = self._int_value(option, "productId") or self._int_value(option, "id")
            options.append(
                {
                    "optionId": f"opt_{idx:03d}",
                    "optionType": str(option.get("optionType") or "SINGLE_PRODUCT"),
                    "displayLabel": label,
                    "description": self._product_description(option),
                    "supported": bool(option.get("supported", product_id is not None)),
                    "disabledReason": option.get("disabledReason"),
                    "_internal": {"productId": product_id},
                }
            )
        prompt = str(result.get("clarificationPrompt") or "该产品名称存在多个匹配项，请选择要查询的具体产品。")
        return PendingClarification(kind="product", intent=intent, prompt=prompt, options=options)

    def _warehouse_clarification(self, result: dict[str, Any]) -> PendingClarification:
        raw_options = result.get("options") or result.get("candidates") or []
        options = []
        for idx, option in enumerate(raw_options[:10], start=1):
            label = str(option.get("displayLabel") or option.get("warehouseName") or option.get("name") or "候选库位")
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
        return PendingClarification(kind="warehouse", intent="warehouse_status", prompt=prompt, options=options)

    def _clarification_card(self, pending: PendingClarification) -> BusinessCard:
        return BusinessCard(
            cardType="candidate_selection",
            title="请选择查询范围",
            prompt=pending.prompt,
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

    def _single_product_entity(self, result: dict[str, Any]) -> SelectedEntity | None:
        candidate = self._first_candidate(result)
        product_id = self._int_value(result, "productId") or self._int_value(candidate, "productId") or self._int_value(candidate, "id")
        if product_id is None:
            return None
        label = str(
            result.get("displayLabel")
            or candidate.get("displayLabel")
            or candidate.get("productName")
            or candidate.get("name")
            or "所选产品"
        )
        return SelectedEntity(internal_id=product_id, display_label=label, source="resolver")

    def _single_warehouse_entity(self, result: dict[str, Any]) -> SelectedEntity | None:
        candidate = self._first_candidate(result)
        warehouse_id = self._int_value(result, "warehouseId") or self._int_value(candidate, "warehouseId") or self._int_value(candidate, "id")
        if warehouse_id is None:
            return None
        label = str(
            result.get("displayLabel")
            or candidate.get("displayLabel")
            or candidate.get("warehouseName")
            or candidate.get("name")
            or "所选库位"
        )
        return SelectedEntity(internal_id=warehouse_id, display_label=label, source="resolver")

    def _format_inventory_answer(self, label: str, result: dict[str, Any]) -> str:
        stock = result.get("displayStockInfo") or self._inventory_summary(result)
        pieces = result.get("totalEquivalentPieces")
        weight = result.get("totalWeight")
        parts = [f"{label}当前库存为 {stock}"]
        if pieces is not None:
            parts.append(f"折合 {pieces} 件")
        if weight is not None:
            parts.append(f"总重量 {weight}kg")
        return "，".join(parts) + "。"

    def _format_warehouse_answer(self, label: str, result: dict[str, Any]) -> str:
        fields = []
        for key, name in [
            ("warehouseName", "库位"),
            ("capacity", "容量"),
            ("usedCapacity", "已用容量"),
            ("availableCapacity", "剩余容量"),
            ("occupancyRate", "占用率"),
        ]:
            if result.get(key) is not None:
                fields.append(f"{name} {result[key]}")
        if fields:
            return f"{label}当前状态：" + "，".join(fields) + "。"
        return f"{label}的库位状态已查询到，但返回字段不足以生成更详细摘要。"

    def _format_pallet_answer(self, result: dict[str, Any]) -> str:
        info = result.get("palletInfo") or result.get("baseInfo") or result
        status = info.get("status") or info.get("bindStatus") or "状态未明确"
        location = result.get("inventory", {}).get("warehouseName") if isinstance(result.get("inventory"), dict) else None
        if location:
            return f"该托盘当前状态为 {status}，当前位置在 {location}。"
        return f"该托盘当前状态为 {status}。"

    def _format_assay_answer(self, label: str, result: dict[str, Any]) -> str:
        if result.get("needsAssay") is True:
            return f"{label}没有查询到对应日期的化验记录，不能判断合格或不合格。"
        judge = result.get("judgeResult") or result.get("result")
        if judge:
            return f"{label}的化验结果为 {judge}。"
        return f"{label}的化验状态已查询到，但返回字段不足以判断合格或不合格。"

    def _inventory_summary(self, result: dict[str, Any]) -> str:
        if result.get("displayStockInfo"):
            return str(result["displayStockInfo"])
        pallets = result.get("normalizedPallets")
        loose = result.get("normalizedLoosePieces")
        if pallets is not None and loose is not None:
            return f"{pallets}板{loose}件"
        return "库存结果已返回"

    def _tool_error_response(self, agent_session_id: str, error: ToolGatewayError) -> ChatResponse:
        return ChatResponse(
            agentSessionId=agent_session_id,
            answer=error.message,
            error=AgentError(code=error.code, message=error.message, retryable=error.retryable),
        )

    def _extract_product_query(self, text: str) -> str:
        query = text
        for word in ["帮我", "查询", "查一下", "查", "当前", "现在", "库存", "情况", "有没有", "化验", "合不合格"]:
            query = query.replace(word, "")
        return query.strip(" ，。？?")[:100]

    def _extract_warehouse_query(self, text: str) -> str:
        stripped = text.strip(" ，。？?")
        if "库位" in stripped:
            return stripped[:100]
        return ""

    def _extract_after_keywords(self, text: str, keywords: list[str]) -> str:
        for keyword in keywords:
            if keyword in text:
                return text.split(keyword, 1)[-1].strip(" ：:，。？?")[:100]
        return ""

    def _is_context_product_followup(self, text: str) -> bool:
        return self._has_context_reference(text) and any(word in text for word in ["存放", "库存", "库位", "在哪"])

    def _has_context_reference(self, text: str) -> bool:
        return any(word in text for word in ["这些", "它", "刚才", "这个", "该产品"])

    def _first_candidate(self, result: dict[str, Any]) -> dict[str, Any]:
        candidates = result.get("candidates") or result.get("options") or []
        if isinstance(candidates, list) and candidates:
            first = candidates[0]
            return first if isinstance(first, dict) else {}
        return {}

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

