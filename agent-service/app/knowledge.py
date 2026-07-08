from __future__ import annotations

import re
from dataclasses import asdict, dataclass, field
from typing import Any, Literal

from app.graph.state import WarehouseAgentState


IntentType = Literal[
    "smalltalk",
    "capability",
    "business_faq",
    "data_query",
    "report_analysis",
    "write_operation",
    "unsupported",
    "ambiguous",
    "unrelated",
    "feedback",
]

SupportStatus = Literal["supported", "partially_supported", "unsupported", "ambiguous"]
NextAction = Literal[
    "answer_directly",
    "ask_clarification",
    "call_tool",
    "explain_unsupported",
    "create_preview_placeholder",
]


@dataclass(frozen=True)
class BusinessObjects:
    product: str | None = None
    warehouse: str | None = None
    pallet: str | None = None
    batch: str | None = None
    date_range: str | None = None
    inspection_status: str | None = None


@dataclass(frozen=True)
class IntentRoute:
    intent_type: IntentType
    intent_subtype: str | None = None
    business_domain: str | None = None
    business_objects: BusinessObjects = field(default_factory=BusinessObjects)
    missing_slots: list[str] = field(default_factory=list)
    support_status: SupportStatus = "unsupported"
    next_action: NextAction = "explain_unsupported"
    planned_tools: list[str] = field(default_factory=list)
    answer: str | None = None
    clarification_prompt: str | None = None
    suggestions: list[str] = field(default_factory=list)

    def to_snapshot(self) -> dict[str, Any]:
        data = asdict(self)
        data.pop("answer", None)
        data.pop("clarification_prompt", None)
        data.pop("suggestions", None)
        return data


@dataclass(frozen=True)
class ToolCapability:
    name: str
    business_name: str
    can_answer: list[str]
    cannot_answer: list[str]
    use_when: list[str]
    do_not_use_when: list[str]
    required_slots: list[str]
    optional_slots: list[str]
    resolver_for: list[str] = field(default_factory=list)
    default_behavior: str = ""
    safe_result_summary: list[str] = field(default_factory=list)
    unsupported_alternatives: list[str] = field(default_factory=list)


TOOL_CAPABILITY_REGISTRY: dict[str, ToolCapability] = {
    "resolve_products": ToolCapability(
        name="resolve_products",
        business_name="产品解析",
        can_answer=["把用户输入的产品名称解析为候选产品或产品范围"],
        cannot_answer=["不能直接回答库存数量", "不能替用户选择产品 ID"],
        use_when=["用户提供了产品名称但未唯一确认产品或范围"],
        do_not_use_when=["用户只是问助手身份、能力或无关闲聊"],
        required_slots=["product_query"],
        optional_slots=["product_type", "product_status"],
        resolver_for=["get_inventory_overview", "get_inventory_distribution", "get_assay_status"],
        default_behavior="唯一匹配后才能继续主查询；多候选时必须追问。",
        safe_result_summary=["resolutionStatus", "candidateCount"],
        unsupported_alternatives=["请用户选择具体产品或受支持的产品范围"],
    ),
    "resolve_warehouses": ToolCapability(
        name="resolve_warehouses",
        business_name="库位解析",
        can_answer=["把 8号库位、库位8 等自然语言库位表达解析为候选库位"],
        cannot_answer=["不能直接回答库位库存或容量", "不能把数字当 warehouseId 使用"],
        use_when=["用户提到库位名称且后续需要库位状态或该库位库存"],
        do_not_use_when=["用户没有库位语义，或只是问能力边界"],
        required_slots=["warehouse_query"],
        optional_slots=["only_available"],
        resolver_for=["get_warehouse_status", "get_inventory_distribution"],
        default_behavior="解析成功后必须继续调用主查询工具，不能只解析后结束。",
        safe_result_summary=["resolutionStatus", "candidateCount"],
        unsupported_alternatives=["请用户换一个库位名称"],
    ),
    "get_inventory_overview": ToolCapability(
        name="get_inventory_overview",
        business_name="单产品库存概览",
        can_answer=["某个已确认产品当前库存数量、总件数、重量和存放摘要"],
        cannot_answer=["不能查询全部产品", "不能按库位聚合", "不能执行出入库"],
        use_when=["产品已经唯一确认，用户问该产品库存"],
        do_not_use_when=["产品名称有歧义", "用户问某库位有什么库存"],
        required_slots=["product"],
        optional_slots=[],
        default_behavior="只使用已确认 productId；普通回答使用板件、总件数和重量口径。",
        safe_result_summary=["displayStockInfo", "totalEquivalentPieces", "totalWeight"],
        unsupported_alternatives=["库存分布改用 get_inventory_distribution"],
    ),
    "get_inventory_distribution": ToolCapability(
        name="get_inventory_distribution",
        business_name="库存分布查询",
        can_answer=["产品在哪些库位", "某库位有什么库存", "按产品或库位聚合的只读库存分布"],
        cannot_answer=["不能下钻任意明细", "不能导出报表", "不能修改库存"],
        use_when=["用户问库存分布、库位内容、全部产品受控聚合"],
        do_not_use_when=["没有确认产品范围且不是明确全部产品或已解析库位范围"],
        required_slots=["product_scope", "warehouse_scope", "group_by"],
        optional_slots=["status_filter", "limit"],
        default_behavior="例如“8号库位有什么”应先 resolve_warehouses，再以 ALL 产品范围和该库位调用本工具。",
        safe_result_summary=["scopeLabel", "totalStockText", "warehouseCount", "groupCount"],
        unsupported_alternatives=["可先查询单产品库存或单库位容量"],
    ),
    "get_warehouse_status": ToolCapability(
        name="get_warehouse_status",
        business_name="库位状态查询",
        can_answer=["已确认库位的容量、占用、状态"],
        cannot_answer=["不能列出该库位所有产品库存分布"],
        use_when=["用户问库位容量、状态、是否满仓"],
        do_not_use_when=["用户问“有什么库存、哪些产品”"],
        required_slots=["warehouse"],
        optional_slots=[],
        default_behavior="必须由 resolve_warehouses 或用户选择确认库位。",
        safe_result_summary=["warehouseName", "status", "remainingCapacity"],
    ),
    "get_pallet_status": ToolCapability(
        name="get_pallet_status",
        business_name="托盘状态查询",
        can_answer=["明确托盘码的当前状态、库存、化验和流转摘要"],
        cannot_answer=["不能作废、恢复、确认任务或出库托盘"],
        use_when=["用户提供明确托盘码或扫码结果"],
        do_not_use_when=["没有托盘码"],
        required_slots=["pallet"],
        optional_slots=["include_inventory", "include_assay", "include_flows"],
        safe_result_summary=["status"],
        unsupported_alternatives=["请用户提供托盘码"],
    ),
    "get_assay_status": ToolCapability(
        name="get_assay_status",
        business_name="单产品/单日期化验查询",
        can_answer=["已确认产品指定日期或化验记录的判定状态"],
        cannot_answer=["不能批量趋势分析", "不能导入、更新、删除化验或修改指标"],
        use_when=["用户问某产品今天或某日期是否有化验、是否合格"],
        do_not_use_when=["用户要求修改质量标准或写入化验"],
        required_slots=["product_or_assay"],
        optional_slots=["production_date"],
        safe_result_summary=["judgeResult", "needsAssay"],
        unsupported_alternatives=["可查询单日化验状态"],
    ),
}


class IntentRouter:
    """Business-level intent router for M1.4R-K0/K1.

    The router produces an auditable decision snapshot. It does not record
    hidden reasoning and it never creates write-capable plans.
    """

    def route(self, text: str, state: WarehouseAgentState) -> IntentRoute:
        normalized = text.strip()
        if not normalized:
            return self._ambiguous_inventory()

        if self._is_sensitive_credential_request(normalized):
            return IntentRoute(
                intent_type="unsupported",
                intent_subtype="sensitive_credential_request",
                business_domain="security",
                support_status="unsupported",
                next_action="answer_directly",
                answer="我不能提供登录凭据、访问密钥或其他敏感凭据。",
            )
        if self._is_feedback(normalized):
            return IntentRoute(
                intent_type="feedback",
                intent_subtype="answer_feedback",
                business_domain="assistant_experience",
                support_status="supported",
                next_action="answer_directly",
                answer="收到反馈。我会把这轮回答作为问题记录下来；你也可以直接补充正确的产品、库位、托盘码或化验日期，我再按只读查询重新确认。",
            )
        if self._is_smalltalk(normalized):
            return IntentRoute(
                intent_type="smalltalk",
                intent_subtype="greeting",
                business_domain="assistant_experience",
                support_status="supported",
                next_action="answer_directly",
                answer="你好，我是智能仓储助手，可以帮你做库存、库位、托盘和化验状态的只读查询。",
            )
        if self._is_capability(normalized):
            return IntentRoute(
                intent_type="capability",
                intent_subtype="assistant_identity"
                if any(phrase in normalized for phrase in ["你是什么", "你是谁"])
                else "capability_scope",
                business_domain="assistant_experience",
                support_status="supported",
                next_action="answer_directly",
                answer=self._capability_answer(),
            )
        if self._is_write_operation(normalized):
            return IntentRoute(
                intent_type="write_operation",
                intent_subtype=self._write_subtype(normalized),
                business_domain=self._business_domain(normalized),
                business_objects=self._business_objects(normalized),
                support_status="partially_supported" if self._has_queryable_context(normalized, state) else "unsupported",
                next_action="explain_unsupported",
                planned_tools=self._alternative_read_tools(normalized, state) if self._has_queryable_context(normalized, state) else [],
                answer=self._write_operation_answer(normalized),
            )
        if self._is_unrelated(normalized):
            return IntentRoute(
                intent_type="unrelated",
                intent_subtype="outside_warehouse",
                business_domain=None,
                support_status="unsupported",
                next_action="explain_unsupported",
                answer="这个问题不属于当前智能仓储助手能力范围。我可以继续帮你查库存、库位、托盘或化验状态。",
            )
        if self._is_business_faq(normalized):
            return IntentRoute(
                intent_type="business_faq",
                intent_subtype="term_or_rule",
                business_domain=self._business_domain(normalized),
                business_objects=self._business_objects(normalized),
                support_status="supported",
                next_action="answer_directly",
                answer=self._faq_answer(normalized),
            )
        if self._is_report_analysis(normalized):
            supported_distribution = self._is_supported_distribution_analysis(normalized)
            return IntentRoute(
                intent_type="report_analysis",
                intent_subtype=self._unsupported_capability_subtype(normalized)
                if not supported_distribution
                else "readonly_analysis",
                business_domain=self._business_domain(normalized),
                business_objects=self._business_objects(normalized),
                support_status="partially_supported" if supported_distribution else "unsupported",
                next_action="call_tool" if supported_distribution else "explain_unsupported",
                planned_tools=["get_inventory_distribution"] if supported_distribution else [],
                answer=None if supported_distribution else self._unsupported_capability_answer(normalized),
            )
        if self._is_unsupported_business_capability(normalized):
            return IntentRoute(
                intent_type="unsupported",
                intent_subtype=self._unsupported_capability_subtype(normalized),
                business_domain=self._business_domain(normalized),
                business_objects=self._business_objects(normalized),
                support_status="unsupported",
                next_action="explain_unsupported",
                planned_tools=[],
                answer=self._unsupported_capability_answer(normalized),
            )
        if self._is_data_query(normalized):
            return self._data_query_route(normalized, state)
        return IntentRoute(
            intent_type="unrelated",
            intent_subtype="no_supported_business_intent",
            support_status="unsupported",
            next_action="explain_unsupported",
            answer="我目前只处理智能仓储相关问题。你可以问库存、库位、托盘码或化验状态。",
        )

    def _data_query_route(self, text: str, state: WarehouseAgentState) -> IntentRoute:
        objects = self._business_objects(text)
        domain = self._business_domain(text)
        if "库存" in text and objects.product is None and objects.warehouse is None and state.selected_product is None:
            return self._ambiguous_inventory(objects)
        if objects.warehouse is not None and self._asks_warehouse_contents(text):
            return IntentRoute(
                intent_type="data_query",
                intent_subtype="warehouse_inventory_contents",
                business_domain="inventory",
                business_objects=objects,
                support_status="supported",
                next_action="call_tool",
                planned_tools=["resolve_warehouses", "get_inventory_distribution"],
            )
        if objects.warehouse is not None and domain == "warehouse":
            return IntentRoute(
                intent_type="data_query",
                intent_subtype="warehouse_status",
                business_domain="warehouse",
                business_objects=objects,
                support_status="supported",
                next_action="call_tool",
                planned_tools=["resolve_warehouses", "get_warehouse_status"],
            )
        if "托盘" in text and objects.pallet is None:
            return IntentRoute(
                intent_type="data_query",
                intent_subtype="pallet_status",
                business_domain="pallet",
                business_objects=objects,
                missing_slots=["pallet"],
                support_status="ambiguous",
                next_action="ask_clarification",
                clarification_prompt="请提供要查询的托盘码，例如 P202606130001。",
            )
        if "化验" in text and objects.product is None and state.selected_product is None:
            return IntentRoute(
                intent_type="data_query",
                intent_subtype="assay_status",
                business_domain="assay",
                business_objects=objects,
                missing_slots=["product"],
                support_status="ambiguous",
                next_action="ask_clarification",
                clarification_prompt="你想查询哪个产品的化验？例如：黄冰糖（袋）今天有没有化验。",
            )
        planned = self._planned_read_tools(text, state)
        return IntentRoute(
            intent_type="data_query",
            intent_subtype=self._query_subtype(text),
            business_domain=domain,
            business_objects=objects,
            support_status="supported",
            next_action="call_tool",
            planned_tools=planned,
        )

    def _ambiguous_inventory(self, objects: BusinessObjects | None = None) -> IntentRoute:
        return IntentRoute(
            intent_type="ambiguous",
            intent_subtype="inventory_query_missing_scope",
            business_domain="inventory",
            business_objects=objects or BusinessObjects(),
            missing_slots=["product_or_warehouse"],
            support_status="ambiguous",
            next_action="ask_clarification",
            clarification_prompt="你想按产品查，还是按库位查？例如：黄冰糖（袋）库存，或 1号库位有什么。",
            suggestions=["按产品查", "按库位查", "查托盘", "查化验"],
        )

    def _business_objects(self, text: str) -> BusinessObjects:
        return BusinessObjects(
            product=self._extract_product(text),
            warehouse=self._extract_warehouse(text),
            pallet=self._extract_pallet(text),
            batch=self._extract_batch(text),
            date_range=self._extract_date_range(text),
            inspection_status=self._extract_inspection_status(text),
        )

    def _business_domain(self, text: str) -> str | None:
        if self._is_write_operation(text):
            return self._write_subtype(text)
        if any(word in text for word in ["化验", "质检", "合格", "不合格", "指标"]):
            return "assay"
        if any(word in text for word in ["托盘", "二维码", "托盘码"]):
            return "pallet"
        if any(word in text for word in ["库位", "仓库", "容量", "满仓", "位置"]):
            return "warehouse"
        if any(word in text for word in ["库存", "入库", "出库", "调拨", "产品"]):
            return "inventory"
        if "生产订单" in text:
            return "production"
        return None

    def _planned_read_tools(self, text: str, state: WarehouseAgentState) -> list[str]:
        if "化验" in text:
            if state.selected_product is not None and self._has_context_reference(text):
                return ["get_assay_status"]
            return ["resolve_products", "get_assay_status"]
        if "托盘" in text:
            return ["get_pallet_status"]
        if self._asks_distribution(text):
            if state.selected_product is not None or self._explicit_all_product_request(text):
                return ["get_inventory_distribution"]
            return ["resolve_products", "get_inventory_distribution"]
        if "库存" in text:
            if state.selected_product is not None and self._has_context_reference(text):
                return ["get_inventory_overview"]
            return ["resolve_products", "get_inventory_overview"]
        return []

    def _alternative_read_tools(self, text: str, state: WarehouseAgentState) -> list[str]:
        tools: list[str] = []
        if state.selected_product is not None or self._extract_product(text):
            tools.extend(["resolve_products", "get_inventory_overview", "get_assay_status"])
        if self._extract_warehouse(text):
            tools.extend(["resolve_warehouses", "get_warehouse_status", "get_inventory_distribution"])
        if self._extract_pallet(text):
            tools.append("get_pallet_status")
        return list(dict.fromkeys(tools))

    def _query_subtype(self, text: str) -> str:
        if "化验" in text:
            return "assay_status"
        if "托盘" in text:
            return "pallet_status"
        if self._asks_distribution(text):
            return "inventory_distribution"
        return "inventory_overview"

    def _extract_warehouse(self, text: str) -> str | None:
        match = re.search(r"([0-9０-９]{1,4})\s*号\s*(?:库位|库|位)?", text)
        if match:
            number = match.group(1).translate(str.maketrans("０１２３４５６７８９", "0123456789"))
            return f"{number}号库位"
        match = re.search(r"库位\s*([0-9０-９]{1,4})", text)
        if match:
            number = match.group(1).translate(str.maketrans("０１２３４５６７８９", "0123456789"))
            return f"{number}号库位"
        return None

    def _extract_product(self, text: str) -> str | None:
        if self._explicit_all_product_request(text):
            return "全部产品"
        cleaned = text
        for word in [
            "帮我",
            "查询",
            "查一下",
            "查",
            "当前",
            "现在",
            "库存",
            "化验",
            "状态",
            "情况",
            "分布",
            "主要",
            "在哪些库位",
            "在哪",
            "有什么",
            "有啥",
        ]:
            cleaned = cleaned.replace(word, "")
        cleaned = cleaned.strip(" ，。？！?；;、")
        if not cleaned or any(word in cleaned for word in ["库位", "托盘", "二维码"]):
            return None
        return cleaned[:100]

    def _extract_pallet(self, text: str) -> str | None:
        match = re.search(r"(?:托盘码|托盘|二维码)\s*[:：]?\s*([A-Za-z0-9_-]{6,64})", text)
        return match.group(1) if match else None

    def _extract_batch(self, text: str) -> str | None:
        match = re.search(r"(?:批次|批号)\s*[:：]?\s*([A-Za-z0-9_-]{3,64})", text)
        return match.group(1) if match else None

    def _extract_date_range(self, text: str) -> str | None:
        if "今天" in text:
            return "today"
        if "昨天" in text:
            return "yesterday"
        match = re.search(r"(?:最近|近)\s*(\d{1,3})\s*天", text)
        return f"last_{match.group(1)}_days" if match else None

    def _extract_inspection_status(self, text: str) -> str | None:
        if "不合格" in text or "未通过" in text or "检测失败" in text:
            return "FAIL"
        if "无化验" in text or "未化验" in text:
            return "MISSING_ASSAY"
        if "无标准" in text:
            return "NO_STANDARD"
        if "合格" in text:
            return "PASS"
        return None

    def _faq_answer(self, text: str) -> str:
        if "化验" in text:
            return "化验状态用于说明产品在指定日期或记录下的质量判定。当前助手只支持查询单产品、单日期或明确化验记录的只读结果，不能写入或修改化验。"
        if "托盘" in text:
            return "托盘用于承载库存和流转记录。你提供明确托盘码后，我可以查询托盘当前状态、库存、化验和流转摘要。"
        if "库位" in text:
            return "库位是库存存放位置。用户说 8号库位、库位8、8号库时，系统会先解析成受控库位，再查询容量或库存分布。"
        return "当前知识层先覆盖系统能力边界、工具能力、意图识别、反问策略和不支持策略；实时业务事实仍必须通过只读工具查询。"

    def _capability_answer(self) -> str:
        return (
            "我现在主要能帮你做仓储只读查询，比如查库存、看某个库位里有什么、查托盘状态、"
            "查产品或批次的化验情况。暂时不能直接替你入库、出库、调拨或修改基础资料，"
            "但可以先帮你查清楚相关库存、库位和化验状态。"
        )

    def _unsupported_capability_answer(self, text: str) -> str:
        if "生产订单" in text:
            return (
                "生产订单查询当前还没有接入对应的只读工具，我不能假装已经查到订单进度或明细。"
                "你可以先按产品、库位或托盘查询库存位置、库存数量和化验风险；"
                "后续接入生产订单追踪工具后，再按订单查看领料、产出和入库进度。"
            )
        if any(word in text for word in ["导出", "报表", "excel", "pdf"]):
            return (
                "当前还没有接入安全的报表导出工具，我不能直接生成或假装已经导出文件。"
                "你可以先让我按产品、库位或托盘查询库存分布和化验风险；"
                "后续接入报表工具后，再基于已审计的查询结果生成导出文件。"
            )
        if self._is_assay_analysis_request(text):
            if "趋势" in text:
                return (
                    "这个属于化验趋势分析，当前还没有对应只读分析工具。"
                    "我可以先帮你查某个产品、库位或托盘的库存和化验风险提示；"
                    "后续接入化验分析后，再按时间范围查看异常和趋势。"
                )
            return (
                "我现在还不能直接汇总最近的化验异常，因为化验异常分析工具还没接入。"
                "你可以先按产品、库位或托盘查询库存和无化验风险；"
                "后续接入化验分析后，就可以按时间范围查看异常和趋势。"
            )
        return (
            "当前还没有接入这类业务分析工具，不能安全完成这个请求。"
            "我可以先帮你做受控的库存、库位、托盘或化验状态只读查询。"
        )

    def _write_operation_answer(self, text: str) -> str:
        subtype = self._write_subtype(text)
        operation = {
            "outbound": "出库操作",
            "inbound": "入库操作",
            "transfer": "调拨操作",
            "assay_write": "化验或质量指标修改",
            "reference_data_write": "基础资料修改",
            "production_order_write": "生产订单操作",
        }.get(subtype, "业务写操作")
        check_name = {
            "outbound": "出库前检查",
            "inbound": "入库前核对",
            "transfer": "调拨前核对",
        }.get(subtype, "操作前核对")
        return (
            f"我现在不能直接替你创建或执行{operation}，也不会假装已经完成。"
            "你可以先告诉我产品、托盘码或库位，我可以帮你查库存是否充足、货在哪个库位、"
            f"有没有化验异常，给你做{check_name}。"
        )

    def _is_smalltalk(self, text: str) -> bool:
        return text in {"你好", "您好", "hello", "hi", "嗨"} or text.startswith(("你好，", "您好，"))

    def _is_capability(self, text: str) -> bool:
        return any(
            phrase in text
            for phrase in [
                "你是什么",
                "你是谁",
                "你能干嘛",
                "你现在能干嘛",
                "你能做什么",
                "你现在能做什么",
                "你可以做什么",
                "你支持什么",
                "你有哪些功能",
                "你有什么功能",
                "你有什么能力",
                "你能帮我什么",
                "这个助手有什么用",
            ]
        )

    def _is_feedback(self, text: str) -> bool:
        return any(phrase in text for phrase in ["数据不对", "没解决", "答非所问", "展示问题", "你理解错了", "不对"])

    def _is_write_operation(self, text: str) -> bool:
        if any(phrase in text for phrase in ["导出", "报表"]):
            return False
        return any(
            phrase in text
            for phrase in [
                "帮我出库",
                "出库",
                "入库",
                "调拨",
                "移库",
                "改库存",
                "调整库存",
                "新增产品",
                "修改产品",
                "修改指标",
                "质量标准",
                "写入化验",
                "导入化验",
                "删除",
                "作废",
                "恢复二维码",
                "创建生产订单",
                "修改生产订单",
            ]
        ) or "能出库吗" in text

    def _write_subtype(self, text: str) -> str:
        if "出库" in text:
            return "outbound"
        if "入库" in text:
            return "inbound"
        if "调拨" in text or "移库" in text:
            return "transfer"
        if "化验" in text or "指标" in text or "质量标准" in text:
            return "assay_write"
        if "产品" in text:
            return "reference_data_write"
        if "生产订单" in text:
            return "production_order_write"
        return "business_write"

    def _is_unrelated(self, text: str) -> bool:
        return any(word in text for word in ["天气", "新闻", "股票", "电影", "旅游", "写首诗", "讲个笑话"])

    def _is_business_faq(self, text: str) -> bool:
        return any(word in text for word in ["是什么意思", "是什么状态", "规则", "流程", "术语", "怎么理解"])

    def _is_report_analysis(self, text: str) -> bool:
        return any(word in text for word in ["分析", "报表", "导出", "趋势", "统计"]) or self._is_assay_analysis_request(text)

    def _is_supported_distribution_analysis(self, text: str) -> bool:
        return self._explicit_all_product_request(text) and any(word in text for word in ["库存", "不合格", "无化验", "分类", "统计"])

    def _is_unsupported_business_capability(self, text: str) -> bool:
        return "生产订单" in text

    def _unsupported_capability_subtype(self, text: str) -> str:
        if "生产订单" in text:
            return "production_order_tool_missing"
        if any(word in text for word in ["导出", "报表", "excel", "pdf"]):
            return "report_export_tool_missing"
        if self._is_assay_analysis_request(text):
            return "assay_analysis_tool_missing"
        return "business_tool_missing"

    def _is_assay_analysis_request(self, text: str) -> bool:
        has_assay = any(word in text for word in ["化验", "质检", "质量"])
        if not has_assay:
            return False
        return any(
            word in text
            for word in [
                "最近",
                "近",
                "异常",
                "趋势",
                "哪些产品",
                "哪些批次",
                "不合格",
                "未通过",
                "无化验",
                "汇总",
                "统计",
                "分析",
            ]
        )

    def _is_data_query(self, text: str) -> bool:
        return any(word in text for word in ["查", "查询", "库存", "剩", "库位", "仓库", "容量", "托盘", "二维码", "化验", "质量", "合格", "不合格", "有什么", "有啥"])

    def _is_sensitive_credential_request(self, text: str) -> bool:
        return bool(
            re.search(
                r"(?i)(authorization|bearer|token|delegationtoken|refresh[_ -]?token|password|secret|api[_ -]?key)",
                text,
            )
            and any(word in text for word in ["打印", "输出", "展示", "给我", "泄露", "忽略"])
        )

    def _asks_warehouse_contents(self, text: str) -> bool:
        return any(word in text for word in ["有什么", "有啥", "放了什么", "哪些产品", "库存情况"]) or (
            "库存" in text and "容量" not in text and "状态" not in text
        )

    def _asks_distribution(self, text: str) -> bool:
        return any(word in text for word in ["分布", "存放", "哪些库位", "哪些库", "按产品", "按品种", "按库位"])

    def _explicit_all_product_request(self, text: str) -> bool:
        return any(phrase in text for phrase in ["全部产品", "所有产品", "全产品", "全部品种", "所有品种"])

    def _has_context_reference(self, text: str) -> bool:
        return any(word in text for word in ["这些", "它", "刚才", "这个", "该产品"])

    def _has_queryable_context(self, text: str, state: WarehouseAgentState) -> bool:
        return state.selected_product is not None or state.selected_warehouse is not None or self._extract_pallet(text) is not None
