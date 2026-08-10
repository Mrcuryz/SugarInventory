from __future__ import annotations

import re
from dataclasses import asdict, dataclass, field
from typing import Any, Literal

from app.graph.state import WarehouseAgentState


PROCESS_KNOWLEDGE_DOMAINS = ("PROCESS",)
ENTERPRISE_KNOWLEDGE_DOMAINS = (
    "COMPANY",
    "PRODUCT_MARKETING",
    "CERTIFICATION",
    "SALES",
)


def classify_knowledge_query(text: str) -> str | None:
    """Return one bounded static-knowledge subtype without claiming real-time facts."""

    normalized = "".join((text or "").split())
    if not normalized:
        return None
    if "批次" in normalized and any(word in normalized for word in ("合格", "化验", "质检")):
        return None
    if "生产订单" in normalized and any(
        word in normalized for word in ("领料", "消耗", "产出", "进度", "状态", "入库去向")
    ):
        return None
    if "煮糖批次" in normalized and any(
        word in normalized for word in ("流转", "关联订单", "用量", "状态")
    ):
        return None
    enterprise_markers = (
        "公司介绍",
        "企业介绍",
        "公司简介",
        "企业简介",
        "宣传册",
        "产品介绍",
        "产品宣传",
        "企业荣誉",
        "公司荣誉",
        "资质认证",
        "企业认证",
        "公司认证",
        "认证证书",
        "有哪些认证",
        "荣誉认证",
        "销售网络",
        "销售区域",
        "经销网络",
    )
    if any(marker in normalized for marker in enterprise_markers):
        return "knowledge_enterprise"
    if any(subject in normalized for subject in ("公司", "企业")) and any(
        marker in normalized for marker in ("成立", "创立", "始建")
    ):
        return "knowledge_enterprise"
    if "自然结晶" in normalized or (
        "结晶" in normalized
        and any(marker in normalized for marker in ("多久", "多长时间", "时间", "温度", "参数", "要求"))
    ):
        return "knowledge_process"
    if any(marker in normalized for marker in ("金属检测", "金属控制", "金属探测")) and any(
        marker in normalized for marker in ("限值", "阈值", "参数", "要求", "标准", "控制点")
    ):
        return "knowledge_process"
    process_markers = (
        "工艺流程",
        "生产工艺",
        "加工流程",
        "分装流程",
        "完整流程",
        "流程顺序",
        "工艺步骤",
        "生产步骤",
        "控制点",
        "关键控制",
        "CCP",
        "CPP",
        "原辅料",
        "原料",
        "材料",
        "原料要求",
        "使用什么设备",
        "所需设备",
        "流程",
        "规则",
        "术语",
    )
    if any(marker in normalized for marker in process_markers):
        return "knowledge_process"
    return None


def is_static_realtime_mixed_query(text: str) -> bool:
    if classify_knowledge_query(text) is None:
        return False
    normalized = "".join((text or "").split())
    realtime_markers = (
        "当前库存",
        "现在库存",
        "库存数量",
        "库存还有",
        "库存多少",
        "库位状态",
        "库位容量",
        "托盘状态",
        "托盘在哪",
        "化验结果",
        "化验了吗",
        "是否合格",
        "订单进度",
        "订单状态",
        "批次状态",
        "待处理任务",
        "入库单据",
        "出库单据",
    )
    return any(marker in normalized for marker in realtime_markers)


def knowledge_domains_for_subtype(subtype: str | None) -> tuple[str, ...]:
    if subtype == "knowledge_process":
        return PROCESS_KNOWLEDGE_DOMAINS
    if subtype == "knowledge_enterprise":
        return ENTERPRISE_KNOWLEDGE_DOMAINS
    return ()


def explicit_knowledge_product_queries(text: str) -> tuple[str, ...]:
    """Extract one explicit product label without inventing or resolving an ID."""

    normalized = "".join((text or "").split())
    match = re.match(
        r"^(.{1,50}?)(?:的)?(?:完整)?(?:工艺流程|生产工艺|自然结晶|金属检测|金属控制|金属探测|企业认证|公司认证)",
        normalized,
    )
    if match is None:
        return ()
    candidate = match.group(1).strip("，。？！?；;、的")
    if not candidate or candidate in {"公司", "企业", "产品", "这个", "该产品"}:
        return ()
    return (candidate[:100],)


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
        resolver_for=[
            "get_inventory_overview",
            "get_inventory_distribution",
            "get_assay_status",
            "query_assay_records",
            "get_assay_report_detail",
            "query_assay_abnormalities",
            "query_products_without_recent_assay",
            "query_assay_standard_coverage",
            "query_printed_not_inbound_codes",
            "query_pallet_anomalies",
            "query_pallet_flow_records",
            "query_qr_batch_inbound_completion",
        ],
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
        resolver_for=["get_warehouse_status", "get_inventory_distribution", "query_products_without_recent_assay", "query_pallet_anomalies", "query_pallet_flow_records"],
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
    "query_unqualified_inventory": ToolCapability(
        name="query_unqualified_inventory",
        business_name="当前不合格库存查询",
        can_answer=["当前仍在库且批次最新化验明确不合格的产品、生产日期、库位、数量和失败指标"],
        cannot_answer=["不能把无化验或无标准库存算作不合格", "不能使用历史化验异常替代当前库存事实"],
        use_when=["用户问库存中有哪些不合格产品、不合格库存在哪里或哪些指标失败"],
        do_not_use_when=["用户只问历史化验异常", "用户问缺化验或无标准库存"],
        required_slots=["product_scope", "warehouse_scope"],
        optional_slots=["limit"],
        default_behavior="基于产品+生产日期关联最新版本化验；全局问题可使用 ALL 产品和 ALL 库位。",
        safe_result_summary=["queryLabel", "totalGroups", "totalEquivalentPieces", "records"],
    ),
    "query_inventory_by_quality_standard": ToolCapability(
        name="query_inventory_by_quality_standard",
        business_name="按化验标准筛选当前库存",
        can_answer=["当前库存批次最新化验是否逐项满足指定标准，并展示产品、日期、库位和数量"],
        cannot_answer=["不能让模型猜 standardCode", "不能把缺少指标值的批次判为符合标准"],
        use_when=["用户问哪些库存符合或满足某个化验标准"],
        do_not_use_when=["用户只问标准定义", "用户问某个原始指标条件"],
        required_slots=["product_scope", "warehouse_scope", "standard_code"],
        optional_slots=["standard_version", "limit"],
        default_behavior="standardCode 来自质量标准目录；未指定版本时由后端选择该编码最新版本。",
        safe_result_summary=["queryLabel", "totalGroups", "records"],
    ),
    "query_inventory_by_assay_metrics": ToolCapability(
        name="query_inventory_by_assay_metrics",
        business_name="按原始化验指标筛选当前库存",
        can_answer=["当前库存批次最新化验的一个受控原始指标满足数值条件的产品、日期、库位和数量"],
        cannot_answer=["不能接受任意字段、任意表达式或 SQL", "不能把无该指标值的库存计入结果"],
        use_when=["用户问库存中某指标大于、小于、等于或介于某数值的产品"],
        do_not_use_when=["用户问符合完整标准", "用户只问历史化验记录"],
        required_slots=["product_scope", "warehouse_scope", "metric_condition"],
        optional_slots=["limit"],
        default_behavior="只允许 7 个化验指标和 GT/GTE/LT/LTE/EQ/BETWEEN。",
        safe_result_summary=["queryLabel", "totalGroups", "records"],
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
    "query_assay_records": ToolCapability(
        name="query_assay_records",
        business_name="化验记录范围查询",
        can_answer=["按产品范围、采样日期范围和判定状态查询化验记录列表与摘要"],
        cannot_answer=["不能导入、更新、删除化验", "不能导出报表", "不能修改质量标准"],
        use_when=["用户问最近N天化验记录、今天有哪些化验记录、范围内不合格或无标准化验记录"],
        do_not_use_when=["用户只问单产品某一天有没有化验"],
        required_slots=["product_scope"],
        optional_slots=["date_range", "judge_status", "page", "size"],
        safe_result_summary=["scopeLabel", "dateRangeLabel", "total", "latestSampleDate"],
        unsupported_alternatives=["报告详情使用 get_assay_report_detail，导出需要后续工具"],
    ),
    "get_assay_report_detail": ToolCapability(
        name="get_assay_report_detail",
        business_name="化验报告详情查询",
        can_answer=["查看上一轮化验记录中的单条报告指标、判定、标准和异常原因"],
        cannot_answer=["不能根据用户手写内部 assayId 查询", "不能导入、更新、删除化验或修改指标"],
        use_when=["用户问刚才那条化验详情、这份报告哪些指标不合格、无标准原因"],
        do_not_use_when=["没有上一轮 recordRef 或用户只是问范围化验记录列表"],
        required_slots=["report_ref"],
        optional_slots=["include_metrics", "include_standard_snapshot"],
        default_behavior="reportRef 必须来自 query_assay_records 返回或 HITL 选择，不能由模型编造。",
        safe_result_summary=["reportLabel", "judgeLabel", "standardLabel", "riskLabels"],
        unsupported_alternatives=["没有上下文时先用 query_assay_records 查询并让用户选择报告"],
    ),
    "query_assay_abnormalities": ToolCapability(
        name="query_assay_abnormalities",
        business_name="化验质量异常聚合查询",
        can_answer=["按产品范围、采样日期范围和异常类型统计不合格、无标准、标准多候选化验"],
        cannot_answer=["不能统计无化验产品", "不能导出报表", "不能修改化验或质量标准"],
        use_when=["用户问最近化验异常、质量异常、不合格统计、无标准记录、指标越界"],
        do_not_use_when=["用户只问普通化验记录列表", "用户问无化验产品"],
        required_slots=["product_scope"],
        optional_slots=["date_range", "abnormal_types", "group_by", "limit"],
        safe_result_summary=["scopeLabel", "dateRangeLabel", "total", "groupCount"],
        unsupported_alternatives=["无化验风险需要 query_products_without_recent_assay 或库存风险查询"],
    ),
    "query_products_without_recent_assay": ToolCapability(
        name="query_products_without_recent_assay",
        business_name="缺化验在库产品查询",
        can_answer=["当前在库产品或库位分组在指定日期范围内没有有效化验记录"],
        cannot_answer=["不能把无标准当作无化验", "不能导入、更新或创建化验"],
        use_when=["用户问无化验、未化验、缺化验、没有化验的在库产品或库位"],
        do_not_use_when=["用户问不合格或无标准化验记录"],
        required_slots=["product_scope", "warehouse_scope"],
        optional_slots=["date_range", "group_by", "limit"],
        safe_result_summary=["scopeLabel", "warehouseScopeLabel", "dateRangeLabel", "totalGroups", "groupCount"],
        unsupported_alternatives=["不合格和无标准统计使用 query_assay_abnormalities"],
    ),
    "query_assay_standard_coverage": ToolCapability(
        name="query_assay_standard_coverage",
        business_name="质量标准覆盖查询",
        can_answer=["当前在库产品中哪些产品没有绑定有效质量标准"],
        cannot_answer=["不能修改质量标准", "不能回答标准最近未使用的历史口径", "不能把无标准回答成不合格"],
        use_when=["用户问哪些产品没有质量标准、未绑定标准、标准覆盖缺口"],
        do_not_use_when=["用户问无化验或缺化验", "用户问化验记录里的无标准统计"],
        required_slots=["product_scope"],
        optional_slots=["date_range", "coverage_type", "limit"],
        safe_result_summary=["scopeLabel", "coverageType", "totalGroups", "groupCount"],
        unsupported_alternatives=["化验记录无标准统计使用 query_assay_abnormalities"],
    ),
    "query_qr_code_lifecycle": ToolCapability(
        name="query_qr_code_lifecycle",
        business_name="二维码托盘生命周期查询",
        can_answer=["明确二维码或托盘码从打印、绑定、入库、流转到当前状态的只读时间线"],
        cannot_answer=["不能作废、恢复、确认托盘任务或修改库存"],
        use_when=["用户提供明确二维码或托盘码并询问经历、环节、生命周期"],
        do_not_use_when=["用户只问当前状态且不需要时间线"],
        required_slots=["pallet"],
        optional_slots=["include_inventory", "include_assay", "include_flows", "include_print_info"],
        safe_result_summary=["codeLabel", "currentStatusLabel", "eventCount", "riskCount"],
        unsupported_alternatives=["请用户提供明确二维码或托盘码"],
    ),
    "query_printed_not_inbound_codes": ToolCapability(
        name="query_printed_not_inbound_codes",
        business_name="已打印未入库二维码查询",
        can_answer=["已打印标签批次、订单或产品范围中尚未完成入库的二维码统计"],
        cannot_answer=["不能打印、回收、核销或修改二维码"],
        use_when=["用户问打印了但没入库、未入库码或打印批次完成情况"],
        do_not_use_when=["用户只问一个二维码当前状态"],
        required_slots=[],
        optional_slots=["product_scope", "order_no", "batch_no", "date_range", "group_by", "limit"],
        safe_result_summary=["printedCount", "inboundCount", "notInboundCount", "groupCount"],
    ),
    "query_pallet_anomalies": ToolCapability(
        name="query_pallet_anomalies",
        business_name="托盘生命周期异常查询",
        can_answer=["托盘状态、库存、流转和产品绑定之间的受控异常统计"],
        cannot_answer=["当前没有独立扫描日志，不能断言作废码被扫描"],
        use_when=["用户问托盘异常、重复入库、无入库出库或状态库存不一致"],
        do_not_use_when=["用户只问普通托盘状态"],
        required_slots=[],
        optional_slots=["product_scope", "warehouse_id", "date_range", "anomaly_types", "limit"],
        safe_result_summary=["total", "groupCount", "notes"],
    ),
    "query_pallet_flow_records": ToolCapability(
        name="query_pallet_flow_records",
        business_name="托盘流转记录查询",
        can_answer=["按二维码、产品、库位、时间和事件类型分页查询托盘流转"],
        cannot_answer=["不能删除流转记录或修改托盘状态"],
        use_when=["用户问托盘最近流转、进出记录或事件时间线"],
        do_not_use_when=["用户只问当前状态"],
        required_slots=[],
        optional_slots=["code", "product_scope", "warehouse_id", "date_range", "event_types", "page", "size"],
        safe_result_summary=["scopeLabel", "dateRangeLabel", "total", "recordCount"],
    ),
    "query_qr_batch_inbound_completion": ToolCapability(
        name="query_qr_batch_inbound_completion",
        business_name="二维码批次入库完成率查询",
        can_answer=["标签批次、生产订单或产品范围的二维码入库完成率和未完成示例"],
        cannot_answer=["不能核销标签、创建入库任务或执行入库"],
        use_when=["用户问二维码批次或生产订单入库完成率"],
        do_not_use_when=["用户没有提供批次、订单、产品或日期范围"],
        required_slots=["batch_or_order_or_product_or_date"],
        optional_slots=["include_unfinished_examples", "limit"],
        safe_result_summary=["batchLabel", "printedCount", "inboundCount", "notInboundCount"],
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
        if self._is_finish_inbound_execution_preview(normalized):
            return IntentRoute(
                intent_type="data_query",
                intent_subtype="finish_inbound_execution_preview",
                business_domain="logistics",
                business_objects=self._business_objects(normalized),
                support_status="supported",
                next_action="call_tool",
                planned_tools=["preview_finish_inbound_execution"],
                answer="我会按已填写的成品入库字段生成精确预览，本轮不会确认任务或修改库存。",
            )
        transfer_preview_codes = self._transfer_preview_codes(normalized)
        if transfer_preview_codes:
            return IntentRoute(
                intent_type="data_query",
                intent_subtype="transfer_task_transition_preview",
                business_domain="logistics",
                business_objects=self._business_objects(normalized),
                support_status="supported",
                next_action="call_tool",
                planned_tools=["preview_task_transition"],
                answer="我会重新核对这些调拨待处理任务、托盘库存和目标库位容量，本轮不会修改业务数据。",
            )
        outbound_preview_codes = self._finish_outbound_preview_codes(normalized)
        if outbound_preview_codes:
            return IntentRoute(
                intent_type="data_query",
                intent_subtype="finish_outbound_task_transition_preview",
                business_domain="logistics",
                business_objects=self._business_objects(normalized),
                support_status="supported",
                next_action="call_tool",
                planned_tools=["preview_task_transition"],
                answer="我会重新核对这些成品出库待处理任务、托盘在库状态和当前库存，本轮不会修改业务数据。",
            )
        preview_codes = self._finish_inbound_preview_codes(normalized)
        if preview_codes:
            return IntentRoute(
                intent_type="data_query",
                intent_subtype="finish_inbound_task_transition_preview",
                business_domain="logistics",
                business_objects=self._business_objects(normalized),
                support_status="supported",
                next_action="call_tool",
                planned_tools=["preview_task_transition"],
                answer="我会重新核对这些成品入库待处理任务并生成短期预览，本轮不会修改业务数据。",
            )
        if self._is_task_processing_preview(normalized):
            return IntentRoute(
                intent_type="data_query",
                intent_subtype="pallet_task_processing_preview",
                business_domain="logistics",
                business_objects=self._business_objects(normalized),
                support_status="supported",
                next_action="call_tool",
                planned_tools=["query_pallet_tasks"],
                answer=(
                    "我会先查询仍处于待处理状态且类型匹配的任务。"
                    "你可以在结果卡片中选择任务并打开现有业务处理弹窗；"
                    "本轮查询不会确认、取消或执行任何任务。"
                ),
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
        knowledge_subtype = classify_knowledge_query(normalized)
        if knowledge_subtype is not None and is_static_realtime_mixed_query(normalized):
            return IntentRoute(
                intent_type="ambiguous",
                intent_subtype="knowledge_realtime_mixed",
                business_domain="multi_domain",
                support_status="ambiguous",
                next_action="ask_clarification",
                clarification_prompt=(
                    "这个问题同时包含现行资料知识和实时业务数据。当前没有登记这类组合配方，"
                    "请拆成两个问题，例如先问工艺流程，再单独查询当前库存。"
                ),
            )
        if knowledge_subtype is not None:
            return IntentRoute(
                intent_type="data_query",
                intent_subtype=knowledge_subtype,
                business_domain="knowledge",
                business_objects=self._business_objects(normalized),
                support_status="supported",
                next_action="call_tool",
                planned_tools=["search_approved_knowledge"],
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
            supported_qr_lifecycle = self._asks_qr_code_lifecycle(normalized)
            supported_printed_not_inbound = self._asks_printed_not_inbound(normalized)
            supported_pallet_anomalies = self._asks_pallet_anomalies(normalized)
            supported_pallet_flow_records = self._asks_pallet_flow_records(normalized)
            supported_qr_batch_completion = self._asks_qr_batch_completion(normalized)
            supported_distribution = self._is_supported_distribution_analysis(normalized)
            supported_assay_records = self._is_supported_assay_records_query(normalized)
            supported_assay_abnormalities = self._is_supported_assay_abnormalities_query(normalized)
            supported_missing_assay = self._asks_products_without_recent_assay(normalized)
            supported_standard_coverage = self._asks_assay_standard_coverage(normalized)
            return IntentRoute(
                intent_type="report_analysis",
                intent_subtype=self._unsupported_capability_subtype(normalized)
                if not (supported_qr_lifecycle or supported_printed_not_inbound or supported_pallet_anomalies or supported_pallet_flow_records or supported_qr_batch_completion or supported_distribution or supported_assay_records or supported_assay_abnormalities or supported_missing_assay or supported_standard_coverage)
                else (
                    "qr_code_lifecycle"
                    if supported_qr_lifecycle
                    else "printed_not_inbound_codes"
                    if supported_printed_not_inbound
                    else "pallet_anomalies"
                    if supported_pallet_anomalies
                    else "pallet_flow_records"
                    if supported_pallet_flow_records
                    else "qr_batch_inbound_completion"
                    if supported_qr_batch_completion
                    else (
                    "assay_standard_coverage"
                    if supported_standard_coverage
                    else "products_without_recent_assay"
                    if supported_missing_assay
                    else ("assay_abnormalities" if supported_assay_abnormalities else ("assay_records" if supported_assay_records else "readonly_analysis"))
                    )
                ),
                business_domain=self._business_domain(normalized),
                business_objects=self._business_objects(normalized),
                support_status="partially_supported" if (supported_qr_lifecycle or supported_printed_not_inbound or supported_pallet_anomalies or supported_pallet_flow_records or supported_qr_batch_completion or supported_distribution or supported_assay_records or supported_assay_abnormalities or supported_missing_assay or supported_standard_coverage) else "unsupported",
                next_action="call_tool" if (supported_qr_lifecycle or supported_printed_not_inbound or supported_pallet_anomalies or supported_pallet_flow_records or supported_qr_batch_completion or supported_distribution or supported_assay_records or supported_assay_abnormalities or supported_missing_assay or supported_standard_coverage) else "explain_unsupported",
                planned_tools=["query_qr_code_lifecycle"]
                if supported_qr_lifecycle
                else ["query_printed_not_inbound_codes"]
                if supported_printed_not_inbound
                else ["query_pallet_anomalies"]
                if supported_pallet_anomalies
                else ["query_pallet_flow_records"]
                if supported_pallet_flow_records
                else ["query_qr_batch_inbound_completion"]
                if supported_qr_batch_completion
                else ["query_assay_standard_coverage"]
                if supported_standard_coverage
                else ["query_products_without_recent_assay"]
                if supported_missing_assay
                else (["query_assay_abnormalities"] if supported_assay_abnormalities else (["query_assay_records"] if supported_assay_records else (["get_inventory_distribution"] if supported_distribution else []))),
                answer=None if (supported_qr_lifecycle or supported_printed_not_inbound or supported_pallet_anomalies or supported_pallet_flow_records or supported_qr_batch_completion or supported_distribution or supported_assay_records or supported_assay_abnormalities or supported_missing_assay or supported_standard_coverage) else self._unsupported_capability_answer(normalized),
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
        if any(phrase in text for phrase in ["固定产品二维码池", "固定产品码池", "固定二维码池"]):
            return IntentRoute(intent_type="data_query", intent_subtype="fixed_product_qr_pool", business_domain="pallet",
                               business_objects=objects, support_status="supported", next_action="call_tool", planned_tools=["query_fixed_product_qr_pool"])
        if any(phrase in text for phrase in ["备料池余额", "半成品备料余额", "历史备料余额"]):
            return IntentRoute(intent_type="data_query", intent_subtype="in_process_materials", business_domain="production",
                               business_objects=objects, support_status="supported", next_action="call_tool", planned_tools=["query_in_process_materials"])
        if any(phrase in text for phrase in ["库存台账", "库存明细台账", "当前库存行"]):
            return IntentRoute(intent_type="data_query", intent_subtype="inventory_ledger", business_domain="inventory",
                               business_objects=objects, support_status="supported", next_action="call_tool", planned_tools=["query_inventory_ledger"])
        if any(phrase in text for phrase in ["Agent工具审计", "Agent 工具审计", "工具调用审计"]):
            return IntentRoute(intent_type="data_query", intent_subtype="agent_tool_audit", business_domain="audit",
                               business_objects=objects, support_status="supported", next_action="call_tool", planned_tools=["query_agent_tool_audit"])
        if any(phrase in text for phrase in ["回答复核", "回答Review", "回答 Review", "Agent回答审查", "Agent 回答审查"]):
            return IntentRoute(intent_type="data_query", intent_subtype="agent_answer_reviews", business_domain="audit",
                               business_objects=objects, support_status="supported", next_action="call_tool", planned_tools=["query_agent_answer_reviews"])
        if any(phrase in text for phrase in ["操作日志", "业务日志", "变更日志"]):
            return IntentRoute(intent_type="data_query", intent_subtype="operation_logs", business_domain="audit",
                               business_objects=objects, support_status="supported", next_action="call_tool", planned_tools=["search_operation_logs"])
        if any(phrase in text for phrase in ["员工名录", "员工名册", "员工列表", "查询员工"]):
            return IntentRoute(intent_type="data_query", intent_subtype="employee_roster",
                               business_domain="administration", business_objects=objects, support_status="supported",
                               next_action="call_tool", planned_tools=["query_employee_roster"])
        if any(phrase in text for phrase in ["角色权限摘要", "角色权限详情", "角色有哪些权限"]) or ("角色" in text and "权限摘要" in text):
            return IntentRoute(intent_type="data_query", intent_subtype="role_permission_summary",
                               business_domain="administration", business_objects=objects, support_status="supported",
                               next_action="call_tool", planned_tools=["get_role_permission_summary"])
        if any(phrase in text for phrase in ["角色目录", "角色列表", "查询角色", "有哪些角色"]):
            return IntentRoute(intent_type="data_query", intent_subtype="role_catalog",
                               business_domain="administration", business_objects=objects, support_status="supported",
                               next_action="call_tool", planned_tools=["query_roles"])
        if any(word in text for word in ["筛网目录", "筛网配置", "有哪些筛网"]):
            return IntentRoute(intent_type="data_query", intent_subtype="screen_mesh_catalog",
                               business_domain="master_data", business_objects=objects, support_status="supported",
                               next_action="call_tool", planned_tools=["query_screen_mesh_catalog"])
        if (
            "化验组" in text
            and any(word in text for word in ["化验标准", "质量标准", "标准"])
            and any(word in text for word in ["适用", "所属", "关联", "配置", "查询", "查看"])
        ):
            return IntentRoute(intent_type="data_query", intent_subtype="product_quality_configuration",
                               business_domain="quality", business_objects=objects, support_status="supported",
                               next_action="call_tool", planned_tools=["resolve_products"])
        if any(word in text for word in ["化验组目录", "化验分组", "化验组配置"]):
            return IntentRoute(intent_type="data_query", intent_subtype="assay_groups", business_domain="quality",
                               business_objects=objects, support_status="supported", next_action="call_tool", planned_tools=["query_assay_groups"])
        if any(word in text for word in ["质量标准目录", "化验标准目录", "有哪些质量标准"]):
            return IntentRoute(intent_type="data_query", intent_subtype="quality_standard_catalog", business_domain="quality",
                               business_objects=objects, support_status="supported", next_action="call_tool", planned_tools=["query_quality_standard_catalog"])
        if any(word in text for word in ["质量标准详情", "化验标准详情"]):
            return IntentRoute(intent_type="data_query", intent_subtype="quality_standard_detail", business_domain="quality",
                               business_objects=objects, support_status="supported", next_action="call_tool", planned_tools=["get_quality_standard_detail"])
        if any(word in text for word in ["绑定哪些标准", "产品标准关系", "产品标准绑定"]):
            return IntentRoute(intent_type="data_query", intent_subtype="product_standard_relations", business_domain="quality",
                               business_objects=objects, support_status="supported", next_action="call_tool", planned_tools=["query_product_standard_relations"])
        if any(word in text for word in ["产品目录", "产品主数据", "产品配置列表"]) or any(
            phrase in text for phrase in ["查询所有成品产品", "查询全部成品产品", "查询所有半成品产品", "查询全部半成品产品"]
        ):
            return IntentRoute(intent_type="data_query", intent_subtype="product_catalog",
                               business_domain="master_data", business_objects=objects, support_status="supported",
                               next_action="call_tool", planned_tools=["query_product_catalog"])
        if "产品详情" in text or ("产品配置" in text and objects.product is not None):
            return IntentRoute(intent_type="data_query", intent_subtype="product_detail",
                               business_domain="master_data", business_objects=objects, support_status="supported",
                               next_action="call_tool", planned_tools=["get_product_detail"])
        if any(word in text for word in ["智能报数批次", "自动入库批次", "报数历史"]):
            detail = any(word in text for word in ["批次详情", "查看该批次", "第一个", "第二个", "第三个"])
            return IntentRoute(
                intent_type="data_query",
                intent_subtype="auto_inbound_batch_detail" if detail else "auto_inbound_batches",
                business_domain="logistics", business_objects=objects,
                support_status="supported", next_action="call_tool",
                planned_tools=["get_auto_inbound_batch_detail" if detail else "query_auto_inbound_batches"],
            )
        if "任务" in text and any(word in text for word in ["查", "查询", "哪些", "列表", "待处理", "情况"]):
            return IntentRoute(
                intent_type="data_query", intent_subtype="pallet_tasks", business_domain="logistics",
                business_objects=objects, support_status="supported", next_action="call_tool",
                planned_tools=["query_pallet_tasks"],
            )
        if any(word in text for word in ["单据", "入库记录", "出库记录", "半成品记录"]):
            return IntentRoute(
                intent_type="data_query", intent_subtype="stock_documents", business_domain="logistics",
                business_objects=objects, support_status="supported", next_action="call_tool",
                planned_tools=["query_stock_documents"],
            )
        if any(word in text for word in ["在制物料", "在制半成品", "生产中半成品"]):
            return IntentRoute(
                intent_type="data_query", intent_subtype="in_process_materials",
                business_domain="production", business_objects=objects,
                support_status="supported", next_action="call_tool",
                planned_tools=["query_in_process_materials"],
            )
        if "生产订单" in text or (state.selected_production_order is not None and any(
            phrase in text for phrase in ["这个订单", "该订单", "刚才的订单", "刚才那个订单"]
        )):
            material_candidates = any(word in text for word in ["领料候选", "物料候选", "可领用半成品", "候选半成品"])
            material_trace = not material_candidates and any(word in text for word in ["领料追溯", "实际领料", "用料记录", "领过哪些物料", "领了哪些物料"])
            label_completion = any(word in text for word in ["标签", "贴码", "绑定码", "二维码完成"])
            subtype = "material_candidates" if material_candidates else "material_pick_trace" if material_trace else "production_label_completion" if label_completion else "production_order_progress"
            return IntentRoute(
                intent_type="data_query",
                intent_subtype=subtype,
                business_domain="production",
                business_objects=objects,
                support_status="supported",
                next_action="call_tool",
                planned_tools=[
                    "resolve_production_entities",
                    "query_material_candidates" if material_candidates
                    else "query_material_pick_trace" if material_trace
                    else "query_production_label_completion" if label_completion
                    else "query_production_order_progress",
                ],
            )
        if "煮糖批次" in text or ("煮糖" in text and "批次" in text):
            return IntentRoute(
                intent_type="data_query",
                intent_subtype="boiling_batch_trace",
                business_domain="production",
                business_objects=objects,
                support_status="supported",
                next_action="call_tool",
                planned_tools=["resolve_production_entities", "query_boiling_batch_trace"],
            )
        if "库存" in text and objects.product is None and objects.warehouse is None and state.selected_product is None:
            return self._ambiguous_inventory(objects)
        if self._asks_qr_code_lifecycle(text):
            return IntentRoute(intent_type="data_query", intent_subtype="qr_code_lifecycle", business_domain="pallet", business_objects=objects, support_status="supported", next_action="call_tool", planned_tools=["query_qr_code_lifecycle"] if objects.pallet else [], missing_slots=[] if objects.pallet else ["pallet"])
        if self._asks_printed_not_inbound(text):
            return IntentRoute(intent_type="data_query", intent_subtype="printed_not_inbound_codes", business_domain="pallet", business_objects=objects, support_status="supported", next_action="call_tool", planned_tools=["query_printed_not_inbound_codes"])
        if self._asks_pallet_anomalies(text):
            return IntentRoute(intent_type="data_query", intent_subtype="pallet_anomalies", business_domain="pallet", business_objects=objects, support_status="supported", next_action="call_tool", planned_tools=["query_pallet_anomalies"])
        if self._asks_pallet_flow_records(text):
            return IntentRoute(intent_type="data_query", intent_subtype="pallet_flow_records", business_domain="pallet", business_objects=objects, support_status="supported", next_action="call_tool", planned_tools=["query_pallet_flow_records"])
        if self._asks_qr_batch_completion(text):
            return IntentRoute(intent_type="data_query", intent_subtype="qr_batch_inbound_completion", business_domain="pallet", business_objects=objects, support_status="supported", next_action="call_tool", planned_tools=["query_qr_batch_inbound_completion"])
        if self._asks_products_without_recent_assay(text):
            return IntentRoute(
                intent_type="data_query",
                intent_subtype="products_without_recent_assay",
                business_domain="assay",
                business_objects=objects,
                support_status="supported",
                next_action="call_tool",
                planned_tools=["query_products_without_recent_assay"]
                if objects.product in {None, "全部产品"} and objects.warehouse is None
                else (["resolve_warehouses", "query_products_without_recent_assay"] if objects.warehouse else ["resolve_products", "query_products_without_recent_assay"]),
            )
        if self._asks_assay_standard_coverage(text):
            return IntentRoute(
                intent_type="data_query",
                intent_subtype="assay_standard_coverage",
                business_domain="assay",
                business_objects=objects,
                support_status="supported",
                next_action="call_tool",
                planned_tools=["query_assay_standard_coverage"]
                if self._assay_standard_coverage_all_scope_allowed(text) or objects.product in {None, "全部产品"}
                else ["resolve_products", "query_assay_standard_coverage"],
            )
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
        if any(word in text for word in ["最近操作", "近期操作", "最近发生了什么", "近期流转", "最近发生过哪些流转", "最近有哪些流转"]):
            return IntentRoute(
                intent_type="data_query", intent_subtype="warehouse_recent_operations",
                business_domain="warehouse", business_objects=objects,
                support_status="supported", next_action="call_tool",
                planned_tools=["resolve_warehouses", "query_warehouse_recent_operations"]
                if objects.warehouse is not None else ["query_warehouse_recent_operations"],
            )
        if any(word in text for word in ["混放事实", "混放情况", "多个产品", "多种产品", "多个规格", "多种规格"]):
            return IntentRoute(
                intent_type="data_query", intent_subtype="warehouse_mixed_storage_facts",
                business_domain="warehouse", business_objects=objects,
                support_status="supported", next_action="call_tool",
                planned_tools=["resolve_warehouses", "query_warehouse_mixed_storage_facts"]
                if objects.warehouse is not None else ["query_warehouse_mixed_storage_facts"],
            )
        if (
            state.selected_warehouse is not None
            and self._has_context_reference(text)
            and any(word in text for word in ["还有多少容量", "还能放多少板", "容量", "情况", "状态"])
        ):
            return IntentRoute(
                intent_type="data_query", intent_subtype="warehouse_status",
                business_domain="warehouse", business_objects=objects,
                support_status="supported", next_action="call_tool",
                planned_tools=["get_warehouse_status"],
            )
        if objects.warehouse is None and any(word in text for word in ["容量分布", "哪些库位空置", "哪些库位快满", "哪些库位已满", "还有多少容量", "还能放多少板"]):
            return IntentRoute(
                intent_type="data_query", intent_subtype="warehouse_capacity_distribution",
                business_domain="warehouse", business_objects=objects,
                support_status="supported", next_action="call_tool",
                planned_tools=["query_warehouse_capacity_distribution"],
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
        if self._asks_assay_report_detail(text):
            return IntentRoute(
                intent_type="data_query",
                intent_subtype="assay_report_detail",
                business_domain="assay",
                business_objects=objects,
                support_status="supported" if (state.last_assay_records or state.last_inventory_quality) else "ambiguous",
                next_action="call_tool" if (state.last_assay_records or state.last_inventory_quality) else "ask_clarification",
                planned_tools=["get_assay_report_detail"] if (state.last_assay_records or state.last_inventory_quality) else ["query_assay_records"],
                clarification_prompt="请先查询或选择一条化验记录，再查看报告详情。",
                suggestions=["例如：黄冰糖最近一次化验记录", "今天有哪些化验记录"],
            )
        if self._asks_unqualified_inventory(text):
            return IntentRoute(
                intent_type="data_query",
                intent_subtype="unqualified_inventory",
                business_domain="quality",
                business_objects=objects,
                support_status="supported",
                next_action="call_tool",
                planned_tools=["query_unqualified_inventory"]
                if objects.product in {None, "全部产品"} or state.selected_product is not None
                else ["resolve_products", "query_unqualified_inventory"],
            )
        if self._asks_inventory_by_quality_standard(text):
            return IntentRoute(
                intent_type="data_query",
                intent_subtype="inventory_by_quality_standard",
                business_domain="quality",
                business_objects=objects,
                support_status="supported",
                next_action="call_tool",
                planned_tools=["query_quality_standard_catalog", "query_inventory_by_quality_standard"],
            )
        if self._asks_inventory_by_assay_metric(text):
            return IntentRoute(
                intent_type="data_query",
                intent_subtype="inventory_by_assay_metric",
                business_domain="quality",
                business_objects=objects,
                support_status="supported",
                next_action="call_tool",
                planned_tools=["query_inventory_by_assay_metrics"]
                if objects.product in {None, "全部产品"} or state.selected_product is not None
                else ["resolve_products", "query_inventory_by_assay_metrics"],
            )
        if self._asks_assay_abnormalities(text):
            return IntentRoute(
                intent_type="data_query",
                intent_subtype="assay_abnormalities",
                business_domain="assay",
                business_objects=objects,
                support_status="supported",
                next_action="call_tool",
                planned_tools=["query_assay_abnormalities"] if objects.product in {None, "全部产品"} else ["resolve_products", "query_assay_abnormalities"],
            )
        if self._asks_assay_records(text):
            return IntentRoute(
                intent_type="data_query",
                intent_subtype="assay_records",
                business_domain="assay",
                business_objects=objects,
                support_status="supported",
                next_action="call_tool",
                planned_tools=["query_assay_records"] if objects.product in {None, "全部产品"} else ["resolve_products", "query_assay_records"],
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
        if "煮糖" in text:
            return "production"
        return None

    def _planned_read_tools(self, text: str, state: WarehouseAgentState) -> list[str]:
        if self._asks_unqualified_inventory(text):
            if state.selected_product is not None or self._explicit_all_product_request(text):
                return ["query_unqualified_inventory"]
            return ["resolve_products", "query_unqualified_inventory"]
        if self._asks_inventory_by_quality_standard(text):
            return ["query_quality_standard_catalog", "query_inventory_by_quality_standard"]
        if self._asks_inventory_by_assay_metric(text):
            if state.selected_product is not None or self._explicit_all_product_request(text):
                return ["query_inventory_by_assay_metrics"]
            return ["resolve_products", "query_inventory_by_assay_metrics"]
        if "化验" in text:
            if self._asks_assay_standard_coverage(text):
                if state.selected_product is not None or self._assay_standard_coverage_all_scope_allowed(text):
                    return ["query_assay_standard_coverage"]
                return ["resolve_products", "query_assay_standard_coverage"]
            if self._asks_products_without_recent_assay(text):
                if self._extract_warehouse(text):
                    return ["resolve_warehouses", "query_products_without_recent_assay"]
                if state.selected_product is not None or self._products_without_recent_assay_all_scope_allowed(text):
                    return ["query_products_without_recent_assay"]
                return ["resolve_products", "query_products_without_recent_assay"]
            if self._asks_assay_abnormalities(text):
                if state.selected_product is not None or self._explicit_all_product_request(text):
                    return ["query_assay_abnormalities"]
                return ["resolve_products", "query_assay_abnormalities"]
            if self._asks_assay_records(text):
                if state.selected_product is not None or self._explicit_all_product_request(text):
                    return ["query_assay_records"]
                return ["resolve_products", "query_assay_records"]
            if state.selected_product is not None and self._has_context_reference(text):
                return ["get_assay_status"]
            return ["resolve_products", "get_assay_status"]
        if self._asks_qr_code_lifecycle(text) or self._asks_printed_not_inbound(text) or self._asks_pallet_anomalies(text) or self._asks_pallet_flow_records(text) or self._asks_qr_batch_completion(text):
            if self._asks_qr_code_lifecycle(text):
                return ["query_qr_code_lifecycle"] if self._extract_pallet(text) else []
            if self._asks_printed_not_inbound(text):
                return ["query_printed_not_inbound_codes"]
            if self._asks_pallet_anomalies(text):
                return ["query_pallet_anomalies"]
            if self._asks_pallet_flow_records(text):
                return ["query_pallet_flow_records"]
            return ["query_qr_batch_inbound_completion"]
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
            tools.extend(["query_qr_code_lifecycle", "query_pallet_flow_records", "query_pallet_anomalies"])
        return list(dict.fromkeys(tools))

    def _query_subtype(self, text: str) -> str:
        if self._asks_qr_code_lifecycle(text):
            return "qr_code_lifecycle"
        if self._asks_printed_not_inbound(text):
            return "printed_not_inbound_codes"
        if self._asks_pallet_anomalies(text):
            return "pallet_anomalies"
        if self._asks_pallet_flow_records(text):
            return "pallet_flow_records"
        if self._asks_qr_batch_completion(text):
            return "qr_batch_inbound_completion"
        if "化验" in text:
            if self._asks_assay_report_detail(text):
                return "assay_report_detail"
            if self._asks_assay_standard_coverage(text):
                return "assay_standard_coverage"
            if self._asks_products_without_recent_assay(text):
                return "products_without_recent_assay"
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
        cleaned = re.sub(r"(?:最近|近)\s*\d{1,3}\s*天", "", cleaned)
        for word in [
            "帮我",
            "查询",
            "查一下",
            "查",
            "当前",
            "现在",
            "库存",
            "化验",
            "质检",
            "质量",
            "记录",
            "无化验",
            "未化验",
            "缺化验",
            "没有化验",
            "没有",
            "在库产品",
            "在库",
            "产品",
            "有哪些",
            "哪些",
            "最近一次",
            "最近",
            "今天",
            "昨天",
            "合格",
            "不合格",
            "状态",
            "情况",
            "分布",
            "主要",
            "在哪些库位",
            "在哪",
            "有什么",
            "有啥",
            "码",
            "哪些码",
            "打印了但没入库",
            "打印但未入库",
            "已打印未入库",
            "未入库码",
            "打印批次还有多少没入库",
            "哪些码没入库",
            "打印",
            "入库",
            "还",
            "但",
            "了",
            "托盘异常",
            "二维码异常",
            "状态与库存不一致",
            "重复入库",
            "无入库出库",
            "产品绑定不一致",
            "作废码被扫描",
            "流转记录",
            "托盘最近有哪些流转",
            "托盘进出记录",
            "二维码流转",
            "托盘流转",
            "入库完成率",
            "二维码批次",
            "打印批次完成",
            "标签批次入库",
            "订单打印的托盘码",
        ]:
            cleaned = cleaned.replace(word, "")
        cleaned = cleaned.strip(" ，。？！?；;、")
        # 中文查询中产品名后常带结构助词，例如“黄冰糖的化验记录”。
        # 这些助词不属于产品名称，否则 resolver 会查询“黄冰糖的”。
        cleaned = re.sub(r"(?:的|相关)$", "", cleaned).strip()
        if not cleaned or cleaned in {"二维", "二维码"} or any(word in cleaned for word in ["库位", "托盘", "二维码"]):
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
            "我现在主要能帮你做只读业务查询，比如查库存、看某个库位里有什么、查托盘状态、"
            "查产品或批次的化验情况，以及查询明确生产订单的当前进度。暂时不能直接替你入库、出库、调拨或修改基础资料，"
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
        if any(phrase in text for phrase in ["查询固定产品二维码池", "查看固定产品二维码池", "固定产品码池查询", "固定二维码池查询"]):
            return False
        if any(phrase in text for phrase in ["备料池余额", "半成品备料余额", "历史备料余额", "库存台账", "库存明细台账", "当前库存行"]):
            return False
        if any(phrase in text for phrase in ["操作日志", "业务日志", "变更日志", "Agent工具审计", "Agent 工具审计", "工具调用审计", "回答复核", "回答Review", "回答 Review", "Agent回答审查", "Agent 回答审查"]):
            return False
        if any(phrase in text for phrase in ["员工名录", "员工名册", "员工列表", "查询员工", "角色目录", "角色列表", "查询角色", "有哪些角色", "角色权限摘要", "角色权限详情", "角色有哪些权限"]) or ("角色" in text and "权限摘要" in text):
            return False
        if any(phrase in text for phrase in ["质量标准目录", "化验标准目录", "有哪些质量标准", "质量标准详情", "化验标准详情", "绑定哪些标准", "产品标准关系", "产品标准绑定"]):
            return False
        if self._asks_assay_standard_coverage(text):
            return False
        if "任务" in text and any(word in text for word in ["查", "查询", "哪些", "列表", "待处理", "情况"]):
            return False
        if any(word in text for word in ["单据", "入库记录", "出库记录", "半成品记录"]):
            return False
        if (
            "生产订单" in text
            and any(word in text for word in ["查", "查询", "情况", "进度", "完成"])
            and any(word in text for word in ["标签", "贴码", "绑定码", "二维码"])
        ):
            return False
        if self._asks_qr_code_lifecycle(text) or self._asks_printed_not_inbound(text) or self._asks_pallet_anomalies(text) or self._asks_pallet_flow_records(text) or self._asks_qr_batch_completion(text):
            return False
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

    def _is_task_processing_preview(self, text: str) -> bool:
        if "任务" not in text or "取消" in text:
            return False
        if not any(word in text for word in ("处理", "确认", "办理")):
            return False
        return any(word in text for word in ("待处理", "待确认", "入库", "出库", "调拨"))

    def _finish_inbound_preview_codes(self, text: str) -> list[str]:
        if "预览" not in text or "成品入库" not in text:
            return []
        values = re.findall(
            r"(?<![A-Za-z0-9-])([A-Za-z]{2,}[A-Za-z0-9-]*\d[A-Za-z0-9-]*)(?![A-Za-z0-9-])",
            text,
        )
        result: list[str] = []
        for value in values:
            code = value.upper()
            if code not in result:
                result.append(code)
        return result[:20]

    def _is_finish_inbound_execution_preview(self, text: str) -> bool:
        return (
            "成品入库" in text
            and "预览" in text
            and any(marker in text for marker in ("精确", "表单", "执行预览"))
            and any(marker in text for marker in ("库位", "warehouseName"))
        )

    def _finish_outbound_preview_codes(self, text: str) -> list[str]:
        if "预览" not in text or "成品出库" not in text:
            return []
        values = re.findall(
            r"(?<![A-Za-z0-9-])([A-Za-z]{2,}[A-Za-z0-9-]*\d[A-Za-z0-9-]*)(?![A-Za-z0-9-])",
            text,
        )
        result: list[str] = []
        for value in values:
            code = value.upper()
            if code not in result:
                result.append(code)
        return result[:20]

    def _transfer_preview_codes(self, text: str) -> list[str]:
        if "预览" not in text or not any(word in text for word in ("调拨", "移库")):
            return []
        values = re.findall(
            r"(?<![A-Za-z0-9-])([A-Za-z]{2,}[A-Za-z0-9-]*\d[A-Za-z0-9-]*)(?![A-Za-z0-9-])",
            text,
        )
        result: list[str] = []
        for value in values:
            code = value.upper()
            if code not in result:
                result.append(code)
        return result[:20]

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
        return self._explicit_all_product_request(text) and any(word in text for word in ["库存", "不合格", "分类", "统计"])

    def _is_supported_assay_records_query(self, text: str) -> bool:
        return self._asks_assay_records(text)

    def _is_supported_assay_abnormalities_query(self, text: str) -> bool:
        return self._asks_assay_abnormalities(text)

    def _products_without_recent_assay_all_scope_allowed(self, text: str) -> bool:
        return self._explicit_all_product_request(text) or any(
            word in text for word in ["哪些产品", "在库产品", "库存", "库位", "全部", "所有"]
        )

    def _assay_standard_coverage_all_scope_allowed(self, text: str) -> bool:
        return self._explicit_all_product_request(text) or any(
            word in text for word in ["哪些产品", "在库产品", "当前在库", "全部", "所有", "未绑定标准", "没有质量标准"]
        )

    def _is_unsupported_business_capability(self, text: str) -> bool:
        return False

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

    def _asks_assay_records(self, text: str) -> bool:
        if "库存" in text:
            return False
        if self._asks_assay_report_detail(text):
            return False
        if any(word in text for word in ["趋势", "导出", "报表", "异常"]) or ("分析" in text and "记录" not in text):
            return False
        if "化验" not in text:
            return False
        return "记录" in text or "最近一次" in text or "有哪些化验" in text or "哪些化验" in text

    def _asks_assay_report_detail(self, text: str) -> bool:
        has_assay_context = any(word in text for word in ["化验", "报告", "指标", "标准"])
        has_detail_word = any(word in text for word in ["详情", "明细", "哪些指标", "异常原因", "不合格原因", "这份报告", "这条记录", "刚才那条"])
        return has_assay_context and has_detail_word

    def _asks_assay_abnormalities(self, text: str) -> bool:
        # 当前库存质量属于库存批次事实；带日期/分组的库存查询继续由
        # get_inventory_distribution 处理，其余走专用 inventory quality 工具。
        # 不得把“库存不合格”误路由为历史化验异常记录查询。
        if "库存" in text or "在库" in text:
            return False
        if "无化验" in text or "未化验" in text or "缺化验" in text:
            return False
        if self._asks_assay_standard_coverage(text):
            return False
        if any(word in text for word in ["趋势", "导出", "报表"]):
            return False
        no_standard_records = "化验" in text and "记录" in text and any(word in text for word in ["无标准", "没有标准", "未匹配标准"])
        if "记录" in text and not no_standard_records and not any(word in text for word in ["统计", "汇总", "哪些产品"]):
            return False
        has_assay = any(word in text for word in ["化验", "质检", "质量"])
        if not has_assay:
            return False
        return any(word in text for word in ["异常", "不合格", "未通过", "无标准", "没有标准", "未匹配标准", "越界", "哪些产品", "统计", "汇总"])

    def _asks_unqualified_inventory(self, text: str) -> bool:
        has_inventory = any(word in text for word in ["库存", "在库", "库里", "库中"])
        explicit_failed = any(word in text for word in ["不合格", "未通过", "判定失败"])
        asks_grouped_distribution = self._asks_distribution(text)
        has_date_scope = bool(re.search(
            r"(?:最近|近)\s*\d+\s*天|今天|昨天|本周|上周|本月|上月|\d{4}[-年/]\d{1,2}",
            text,
        ))
        # 专用工具回答“当前有哪些明确不合格库存”。用户另带日期或聚合维度时，
        # 必须继续走支持这些条件的库存分布工具，不能静默丢掉约束。
        return has_inventory and explicit_failed and not asks_grouped_distribution and not has_date_scope

    def _asks_inventory_by_quality_standard(self, text: str) -> bool:
        has_inventory = any(word in text for word in ["库存", "在库", "库里", "库中"])
        has_standard = any(word in text for word in ["质量标准", "化验标准", "标准"])
        has_match = any(word in text for word in ["符合", "满足", "达到", "达标"])
        return has_inventory and has_standard and has_match

    def _asks_inventory_by_assay_metric(self, text: str) -> bool:
        has_inventory = any(word in text for word in ["库存", "在库", "库里", "库中"])
        metrics = ["色值", "还原糖", "干燥失重", "电导灰分", "蔗糖", "不溶于水杂质", "pH", "ph"]
        has_metric = any(metric in text for metric in metrics)
        has_condition = any(word in text for word in ["大于", "高于", "不少于", "不低于", "小于", "低于", "不超过", "等于", "介于", "之间", ">", "<", "≥", "≤"])
        return has_inventory and has_metric and has_condition

    def _asks_products_without_recent_assay(self, text: str) -> bool:
        if any(phrase in text for phrase in ["有没有化验", "是否有化验", "有化验吗"]):
            return False
        return any(word in text for word in ["无化验", "未化验", "缺化验", "没有化验"])

    def _asks_assay_standard_coverage(self, text: str) -> bool:
        if any(word in text for word in ["无化验", "未化验", "缺化验", "没有化验"]):
            return False
        # “化验记录没有标准”描述已有化验的判定异常，不是产品主数据标准覆盖缺口。
        if "化验" in text and "记录" in text and any(word in text for word in ["无标准", "没有标准", "未匹配标准"]):
            return False
        has_standard = any(word in text for word in ["质量标准", "化验标准", "标准覆盖", "绑定标准", "有效标准", "无标准"])
        has_gap = any(word in text for word in ["没有", "未绑定", "缺", "缺少", "哪些产品", "覆盖"])
        return has_standard and has_gap

    def _asks_qr_code_lifecycle(self, text: str) -> bool:
        has_code = self._extract_pallet(text) is not None
        lifecycle_words = any(word in text for word in ["生命周期", "经历哪些环节", "经历了哪些", "流转过程", "从打印到入库", "从生成到"])
        return has_code and lifecycle_words

    def _asks_printed_not_inbound(self, text: str) -> bool:
        return any(phrase in text for phrase in ["打印了但没入库", "打印了但还没有入库", "打印但未入库", "已打印未入库", "未入库码", "打印批次还有多少没入库", "哪些码没入库", "哪些二维码打印了但还没有入库"])

    def _asks_pallet_anomalies(self, text: str) -> bool:
        return any(phrase in text for phrase in ["托盘异常", "二维码异常", "状态与库存不一致", "重复入库", "无入库出库", "产品绑定不一致", "作废码被扫描"])

    def _asks_pallet_flow_records(self, text: str) -> bool:
        return any(phrase in text for phrase in ["流转记录", "托盘最近有哪些流转", "托盘进出记录", "二维码流转", "托盘流转"])

    def _asks_qr_batch_completion(self, text: str) -> bool:
        return any(phrase in text for phrase in ["入库完成率", "二维码批次", "打印批次完成", "标签批次入库", "订单打印的托盘码"])

    def _is_data_query(self, text: str) -> bool:
        return ("角色" in text and "权限摘要" in text) or "生产订单" in text or "煮糖批次" in text or any(word in text for word in ["在制物料", "在制半成品", "生产中半成品", "查", "查询", "库存", "剩", "库位", "仓库", "容量", "托盘", "二维码", "化验", "质量", "合格", "不合格", "有什么", "有啥", "缺化验", "无化验", "未化验", "筛网", "产品目录", "产品主数据", "产品详情", "员工名录", "员工名册", "员工列表", "角色目录", "角色列表", "角色权限", "订单", "流转", "操作日志", "业务日志", "工具审计", "调用审计", "回答复核", "回答审查"]) or any(
            detector(text)
            for detector in [
                self._asks_qr_code_lifecycle,
                self._asks_printed_not_inbound,
                self._asks_pallet_anomalies,
                self._asks_pallet_flow_records,
                self._asks_qr_batch_completion,
            ]
        )

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
