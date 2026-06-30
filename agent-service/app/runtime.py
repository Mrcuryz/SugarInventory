from __future__ import annotations

from datetime import date
import re
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
    SafeInventoryLocation,
    SafeInventoryResult,
    SafeWarehouseResult,
    UserOption,
)
from app.tool_arguments import ToolArgumentBuilder
from app.tools.client import AgentToolClient, ToolGatewayError


class WarehouseAgentRuntime:
    """Minimal LangGraph-compatible runtime for M1.3R."""

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
                display_label=self._safe_display_label(str(option.get("displayLabel") or "所选产品")),
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
                display_label=self._safe_display_label(str(option.get("displayLabel") or "所选库位")),
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
        if self._is_inventory_location_followup(text):
            if state.selected_product is None:
                return ChatResponse(
                    agentSessionId=request.agentSessionId,
                    answer="你想查哪个产品？请先选择或输入一个明确的产品名称。",
                    needsUserSelection=True,
                    suggestions=["例如：黄冰糖（袋）库存。"],
                )
            return self._answer_inventory_locations(
                request.agentSessionId,
                state,
                request.client.traceId,
                request.client.requestId,
            )

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

        if self._has_warehouse_semantics(text):
            arguments = self._build_arguments_or_empty("resolve_warehouses", text, state)
            if arguments.get("query"):
                return self._resolve_warehouse_and_continue(request, state, arguments)
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
            arguments = self._build_arguments_or_empty("get_pallet_status", text, state)
            if not arguments.get("code"):
                return ChatResponse(
                    agentSessionId=request.agentSessionId,
                    answer="请提供要查询的托盘码。",
                    needsUserSelection=True,
                )
            result = self._call_tool(request, "get_pallet_status", arguments)
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
            arguments = self._build_arguments_or_empty("resolve_products", text, state)
            if not arguments.get("query"):
                return ChatResponse(
                    agentSessionId=request.agentSessionId,
                    answer="你想查询哪个产品的化验？请提供明确产品名称或先选择产品。",
                    needsUserSelection=True,
                )
            return self._resolve_product_and_continue(request, state, arguments, "assay")

        if "库存" in text:
            arguments = self._build_arguments_or_empty("resolve_products", text, state)
            if not arguments.get("query") and state.selected_product is not None:
                return self._answer_inventory(
                    request.agentSessionId,
                    state,
                    request.client.traceId,
                    request.client.requestId,
                )
            if not arguments.get("query"):
                return ChatResponse(
                    agentSessionId=request.agentSessionId,
                    answer="你想查哪个产品的库存？请先输入明确产品名称。",
                    needsUserSelection=True,
                )
            return self._resolve_product_and_continue(request, state, arguments, "inventory")

        return ChatResponse(
            agentSessionId=request.agentSessionId,
            answer="当前我只支持库存、库位、托盘和化验的只读查询。请补充要查询的产品、库位、托盘码或生产日期。",
            needsUserSelection=True,
        )

    def _resolve_product_and_continue(
        self, request: ChatRequest, state: WarehouseAgentState, arguments: dict[str, Any], intent: str
    ) -> ChatResponse:
        result = self._call_tool(request, "resolve_products", arguments)
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
        safe_result = self._adapt_inventory_result(result)
        state.last_inventory_result = safe_result.model_dump(exclude_none=True)
        state.tool_results.append({"kind": "inventory_overview", "summary": self._inventory_summary(safe_result)})
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
    ) -> ChatResponse:
        safe_result = None
        if state.last_inventory_result:
            try:
                safe_result = SafeInventoryResult.model_validate(state.last_inventory_result)
            except ValueError:
                safe_result = None
        if safe_result is None:
            result = self.tool_client.call_tool(
                agent_session_id=agent_session_id,
                tool_name="get_inventory_overview",
                arguments={"productId": state.selected_product.internal_id},
                trace_id=trace_id,
                request_id=request_id,
            )
            safe_result = self._adapt_inventory_result(result)
            state.last_inventory_result = safe_result.model_dump(exclude_none=True)

        return ChatResponse(
            agentSessionId=agent_session_id,
            answer=self._format_inventory_locations(state.selected_product.display_label, safe_result),
        )

    def _resolve_warehouse_and_continue(
        self, request: ChatRequest, state: WarehouseAgentState, arguments: dict[str, Any]
    ) -> ChatResponse:
        result = self._call_tool(request, "resolve_warehouses", arguments)
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
        safe_result = self._adapt_warehouse_result(result)
        state.last_warehouse_result = safe_result.model_dump(exclude_none=True)
        return ChatResponse(
            agentSessionId=agent_session_id,
            answer=self._format_warehouse_answer(state.selected_warehouse.display_label, safe_result),
        )

    def _call_tool(self, request: ChatRequest, tool_name: str, arguments: dict[str, Any]) -> dict[str, Any]:
        return self.tool_client.call_tool(
            agent_session_id=request.agentSessionId,
            tool_name=tool_name,
            arguments=arguments,
            trace_id=request.client.traceId,
            request_id=request.client.requestId,
        )

    def _build_arguments_or_empty(
        self, tool_name: str, text: str, state: WarehouseAgentState
    ) -> dict[str, Any]:
        try:
            return self.argument_builder.build(tool_name=tool_name, user_message=text, state=state)
        except ValueError:
            return {}

    def _product_clarification(self, result: dict[str, Any], intent: str) -> PendingClarification:
        raw_options = result.get("options") or result.get("candidates") or []
        options = []
        for idx, option in enumerate(raw_options[:10], start=1):
            label = self._safe_display_label(
                str(option.get("displayLabel") or option.get("productName") or option.get("name") or "候选产品")
            )
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
        return SelectedEntity(internal_id=product_id, display_label=label, source="resolver")

    def _single_warehouse_entity(self, result: dict[str, Any]) -> SelectedEntity | None:
        candidate = self._first_candidate(result)
        warehouse_id = (
            self._int_value(result, "warehouseId")
            or self._int_value(candidate, "warehouseId")
            or self._int_value(candidate, "id")
        )
        if warehouse_id is None:
            return None
        label = self._safe_display_label(
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

    def _is_context_product_followup(self, text: str) -> bool:
        return self._has_context_reference(text) and "库存" in text

    def _is_inventory_location_followup(self, text: str) -> bool:
        return self._has_context_reference(text) and any(word in text for word in ["存放", "在哪", "哪些库位"])

    def _has_context_reference(self, text: str) -> bool:
        return any(word in text for word in ["这些", "它", "刚才", "这个", "该产品"])

    def _has_warehouse_semantics(self, text: str) -> bool:
        return any(word in text for word in ["库位", "仓库", "容量", "位置"])

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
