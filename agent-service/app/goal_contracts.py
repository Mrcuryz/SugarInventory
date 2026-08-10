from __future__ import annotations

from datetime import datetime, timezone
import hashlib
import json
from typing import Any, Literal, get_args

from pydantic import BaseModel, ConfigDict, Field

from app.state_models import EntityContextV1
from app.task_transition_previews import TASK_TRANSITION_PREVIEW_BY_TRANSITION


CoreGoalTypeV1 = Literal[
    "CURRENT_PRODUCT_INVENTORY",
    "PRODUCT_INVENTORY_DISTRIBUTION",
    "WAREHOUSE_INVENTORY_DISTRIBUTION",
    "CURRENT_PENDING_TASKS",
    "FINISH_INBOUND_GUIDED_PREPARATION",
    "FINISH_INBOUND_PENDING_TASK_PREPARATION",
    "FINISH_INBOUND_FIXED_QR_PREPARATION",
    "FINISH_INBOUND_TASK_TRANSITION_PREVIEW",
    "FINISH_INBOUND_EXECUTION_PREVIEW",
    "FINISH_OUTBOUND_TASK_TRANSITION_PREVIEW",
    "TRANSFER_TASK_TRANSITION_PREVIEW",
    "BOILING_BATCH_TRACE",
    "PRODUCTION_ORDER_PROGRESS",
    "PRODUCTION_MATERIAL_TRACE",
    "PALLET_CURRENT_STATUS",
    "PALLET_FLOW_HISTORY",
    "PRODUCT_ASSAY_REPORT",
    "PRODUCT_ASSAY_HISTORY",
    "BOILING_BATCH_LIST",
    "PRODUCTION_OUTPUT_DESTINATIONS",
    "CURRENT_UNQUALIFIED_INVENTORY",
    "CURRENT_INVENTORY_MATCHING_STANDARD",
    "CURRENT_INVENTORY_MATCHING_METRIC",
    "CURRENT_INVENTORY_ASSAY_GAPS",
    "CURRENT_INVENTORY_LEDGER",
    "WAREHOUSE_STATUS",
    "WAREHOUSE_CAPACITY_DISTRIBUTION",
    "WAREHOUSE_RECENT_OPERATIONS",
    "WAREHOUSE_MIXED_STORAGE_FACTS",
    "ASSAY_ABNORMALITY_SUMMARY",
    "CURRENT_INVENTORY_STANDARD_GAPS",
    "PALLET_ANOMALY_SUMMARY",
    "PRINTED_NOT_INBOUND_QR_CODES",
    "QR_BATCH_INBOUND_COMPLETION",
    "PRODUCTION_LABEL_COMPLETION",
    "IN_PROCESS_MATERIALS",
    "STOCK_DOCUMENTS",
    "AUTO_INBOUND_BATCH_STATUS",
    "ASSAY_REFERENCE_DATA",
    "PRODUCT_QUALITY_CONFIGURATION",
    "FIXED_PRODUCT_QR_POOL",
    "MATERIAL_CANDIDATES",
    "PRODUCT_MASTER_DATA",
    "SCREEN_MESH_CATALOG",
    "EMPLOYEE_ROSTER",
    "ROLE_PERMISSION_CATALOG",
    "BUSINESS_OPERATION_LOGS",
    "AGENT_TOOL_AUDIT",
    "AGENT_ANSWER_REVIEWS",
    "DAILY_PRODUCTION_ANALYSIS",
    "QUALITY_ASSAY_TREND_ANALYSIS",
    "QUALITY_METRIC_TREND_ANALYSIS",
    "PRODUCTION_INPUT_OUTPUT_TREND",
    "PROCESS_EFFICIENCY_TREND",
    "INVENTORY_LEVEL_TREND_ANALYSIS",
    "PROCESS_KNOWLEDGE_QUERY",
    "ENTERPRISE_KNOWLEDGE_QUERY",
    "TODAY_OPERATIONS_OVERVIEW",
]
CoreFactTypeV1 = Literal[
    "CURRENT_PRODUCT_INVENTORY",
    "PRODUCT_INVENTORY_DISTRIBUTION",
    "WAREHOUSE_INVENTORY_DISTRIBUTION",
    "CURRENT_PENDING_TASKS",
    "FINISH_INBOUND_GUIDED_PREPARATION",
    "FINISH_INBOUND_PENDING_TASK_PREPARATION",
    "FINISH_INBOUND_FIXED_QR_PREPARATION",
    "FINISH_INBOUND_TASK_TRANSITION_PREVIEW",
    "FINISH_INBOUND_EXECUTION_PREVIEW",
    "FINISH_OUTBOUND_TASK_TRANSITION_PREVIEW",
    "TRANSFER_TASK_TRANSITION_PREVIEW",
    "BOILING_BATCH_TRACE",
    "PRODUCTION_ORDER_PROGRESS",
    "PRODUCTION_MATERIAL_TRACE",
    "PALLET_CURRENT_STATUS",
    "PALLET_FLOW_HISTORY",
    "PRODUCT_ASSAY_REPORT",
    "PRODUCT_ASSAY_HISTORY",
    "BOILING_BATCH_LIST",
    "PRODUCTION_OUTPUT_DESTINATIONS",
    "CURRENT_UNQUALIFIED_INVENTORY",
    "CURRENT_INVENTORY_MATCHING_STANDARD",
    "CURRENT_INVENTORY_MATCHING_METRIC",
    "CURRENT_INVENTORY_ASSAY_GAPS",
    "CURRENT_INVENTORY_LEDGER",
    "WAREHOUSE_STATUS",
    "WAREHOUSE_CAPACITY_DISTRIBUTION",
    "WAREHOUSE_RECENT_OPERATIONS",
    "WAREHOUSE_MIXED_STORAGE_FACTS",
    "ASSAY_ABNORMALITY_SUMMARY",
    "CURRENT_INVENTORY_STANDARD_GAPS",
    "PALLET_ANOMALY_SUMMARY",
    "PRINTED_NOT_INBOUND_QR_CODES",
    "QR_BATCH_INBOUND_COMPLETION",
    "PRODUCTION_LABEL_COMPLETION",
    "IN_PROCESS_MATERIALS",
    "STOCK_DOCUMENTS",
    "AUTO_INBOUND_BATCH_STATUS",
    "ASSAY_REFERENCE_DATA",
    "PRODUCT_QUALITY_CONFIGURATION",
    "FIXED_PRODUCT_QR_POOL",
    "MATERIAL_CANDIDATES",
    "PRODUCT_MASTER_DATA",
    "SCREEN_MESH_CATALOG",
    "EMPLOYEE_ROSTER",
    "ROLE_PERMISSION_CATALOG",
    "BUSINESS_OPERATION_LOGS",
    "AGENT_TOOL_AUDIT",
    "AGENT_ANSWER_REVIEWS",
    "DAILY_PRODUCTION_ANALYSIS",
    "QUALITY_ASSAY_TREND_ANALYSIS",
    "QUALITY_METRIC_TREND_ANALYSIS",
    "PRODUCTION_INPUT_OUTPUT_TREND",
    "PROCESS_EFFICIENCY_TREND",
    "INVENTORY_LEVEL_TREND_ANALYSIS",
    "PROCESS_KNOWLEDGE_QUERY",
    "ENTERPRISE_KNOWLEDGE_QUERY",
    "TODAY_OPERATIONS_OVERVIEW",
]
REGISTERED_CORE_GOAL_TYPES: tuple[str, ...] = get_args(CoreGoalTypeV1)
REGISTERED_CORE_FACT_TYPES: tuple[str, ...] = get_args(CoreFactTypeV1)
FactStatusV1 = Literal["AVAILABLE", "NO_DATA", "INVALID", "TOOL_ERROR"]
CompletionStatusV1 = Literal[
    "COMPLETE",
    "PARTIAL",
    "NEEDS_CLARIFICATION",
    "UNSUPPORTED",
    "FAILED",
]


class FactValidationShapeV1(BaseModel):
    """One accepted safe-result shape for a goal fact."""

    model_config = ConfigDict(extra="forbid", frozen=True)

    requiredFields: tuple[str, ...] = ()
    listFields: tuple[str, ...] = ()
    noDataWhenEmptyList: str | None = None
    noDataBooleanField: str | None = None
    noDataBooleanValue: bool = True


class FactValidationRuleV1(FactValidationShapeV1):
    """Declarative minimum evidence required before a tool result becomes a fact."""

    mode: Literal["STANDARD", "INVENTORY_DISTRIBUTION"] = "STANDARD"
    factLabel: str
    alternativeShapes: tuple[FactValidationShapeV1, ...] = ()


class GoalContractV1(BaseModel):
    model_config = ConfigDict(extra="forbid", frozen=True)

    schemaVersion: Literal["1.0"] = "1.0"
    goalType: CoreGoalTypeV1
    ownerExpert: Literal[
        "inventory_expert",
        "warehouse_expert",
        "logistics_expert",
        "production_expert",
        "pallet_expert",
        "assay_expert",
        "master_data_expert",
        "administration_expert",
        "audit_expert",
        "analytics_expert",
        "knowledge_expert",
    ]
    requiredEntityTypes: tuple[Literal["PRODUCT", "WAREHOUSE", "PALLET", "PALLET_TASK", "PRODUCTION_ORDER", "BOILING_BATCH"], ...]
    requiredFactTypes: tuple[CoreFactTypeV1, ...]
    allowedTools: tuple[str, ...]
    evidenceTools: tuple[str, ...]
    factValidation: FactValidationRuleV1
    scopeEntityBindings: tuple[Literal["PRODUCT_SCOPE", "WAREHOUSE_SCOPE"], ...] = ()
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
        ownerExpert="inventory_expert",
        requiredEntityTypes=("PRODUCT",),
        requiredFactTypes=("CURRENT_PRODUCT_INVENTORY",),
        allowedTools=("resolve_products", "get_inventory_overview"),
        evidenceTools=("get_inventory_overview",),
        factValidation=FactValidationRuleV1(
            factLabel="库存总览",
            requiredFields=("isEmpty",),
            noDataBooleanField="isEmpty",
        ),
    ),
    "PRODUCT_INVENTORY_DISTRIBUTION": GoalContractV1(
        goalType="PRODUCT_INVENTORY_DISTRIBUTION",
        ownerExpert="inventory_expert",
        requiredEntityTypes=("PRODUCT",),
        requiredFactTypes=("PRODUCT_INVENTORY_DISTRIBUTION",),
        allowedTools=("resolve_products", "get_inventory_distribution", "get_inventory_overview"),
        evidenceTools=("get_inventory_distribution", "get_inventory_overview"),
        factValidation=FactValidationRuleV1(
            mode="INVENTORY_DISTRIBUTION",
            factLabel="库存分布",
        ),
        scopeEntityBindings=("PRODUCT_SCOPE",),
        limitations=("库存分布只表达当前库存，不是历史流水。",),
    ),
    "WAREHOUSE_INVENTORY_DISTRIBUTION": GoalContractV1(
        goalType="WAREHOUSE_INVENTORY_DISTRIBUTION",
        ownerExpert="inventory_expert",
        requiredEntityTypes=("WAREHOUSE",),
        requiredFactTypes=("WAREHOUSE_INVENTORY_DISTRIBUTION",),
        allowedTools=("resolve_warehouses", "get_inventory_distribution"),
        evidenceTools=("get_inventory_distribution",),
        factValidation=FactValidationRuleV1(
            mode="INVENTORY_DISTRIBUTION",
            factLabel="库存分布",
        ),
        scopeEntityBindings=("WAREHOUSE_SCOPE",),
        limitations=("库位库存只表达当前库存，不自动包含批次化验结论。",),
    ),
    "CURRENT_PENDING_TASKS": GoalContractV1(
        goalType="CURRENT_PENDING_TASKS",
        ownerExpert="logistics_expert",
        requiredEntityTypes=(),
        requiredFactTypes=("CURRENT_PENDING_TASKS",),
        allowedTools=("query_pallet_tasks",),
        evidenceTools=("query_pallet_tasks",),
        factValidation=FactValidationRuleV1(
            factLabel="待处理任务",
            requiredFields=("scopeLabel", "total", "page", "size", "records"),
            listFields=("records",),
            noDataWhenEmptyList="records",
        ),
        limitations=("任务查询只展示当前任务记录，不代表任务已执行。",),
    ),
    "FINISH_INBOUND_GUIDED_PREPARATION": GoalContractV1(
        goalType="FINISH_INBOUND_GUIDED_PREPARATION",
        ownerExpert="logistics_expert",
        requiredEntityTypes=("PRODUCT", "WAREHOUSE"),
        requiredFactTypes=("FINISH_INBOUND_GUIDED_PREPARATION",),
        allowedTools=("resolve_products", "resolve_warehouses"),
        evidenceTools=("resolve_products", "resolve_warehouses"),
        factValidation=FactValidationRuleV1(
            factLabel="成品入库业务模式确认",
            requiredFields=("operationMode",),
        ),
        limitations=(
            "本目标只确认具体产品、目标库位以及本次是处理已有待入库任务还是使用空闲固定二维码新建任务。",
            "两种模式使用不同的 GoalContract 和后续预览，任何一条路径都不得静默回退到另一种二维码来源。",
        ),
    ),
    "FINISH_INBOUND_PENDING_TASK_PREPARATION": GoalContractV1(
        goalType="FINISH_INBOUND_PENDING_TASK_PREPARATION",
        ownerExpert="logistics_expert",
        requiredEntityTypes=("PRODUCT", "WAREHOUSE"),
        requiredFactTypes=("FINISH_INBOUND_PENDING_TASK_PREPARATION",),
        allowedTools=("resolve_products", "resolve_warehouses", "query_pallet_tasks"),
        evidenceTools=("query_pallet_tasks",),
        factValidation=FactValidationRuleV1(
            factLabel="已有成品入库任务二维码选择准备",
            requiredFields=("scopeLabel", "total", "page", "size", "records"),
            listFields=("records",),
            noDataWhenEmptyList="records",
        ),
        limitations=(
            "只查询已绑定且当前存在 FINISH_IN 待处理任务的二维码，不创建新任务、不执行入库。",
            "若数量不足，应明确告知已有任务不足，不得改用空闲固定二维码补足。",
        ),
    ),
    "FINISH_INBOUND_FIXED_QR_PREPARATION": GoalContractV1(
        goalType="FINISH_INBOUND_FIXED_QR_PREPARATION",
        ownerExpert="logistics_expert",
        requiredEntityTypes=("PRODUCT", "WAREHOUSE"),
        requiredFactTypes=("FINISH_INBOUND_FIXED_QR_PREPARATION",),
        allowedTools=("resolve_products", "resolve_warehouses", "query_fixed_product_qr_pool"),
        evidenceTools=("query_fixed_product_qr_pool",),
        factValidation=FactValidationRuleV1(
            factLabel="空闲固定产品二维码选择准备",
            requiredFields=("dataScope", "total", "page", "size", "records"),
            listFields=("records",),
            noDataWhenEmptyList="records",
        ),
        limitations=(
            "只查询与已确认具体产品精确匹配、固定产品模式已启用且当前为空闲状态的二维码。",
            "选择二维码本身不会创建任务或入库；后续必须先通过独立确认创建待入库任务，任务真实存在后才可进入既有任务资格与精确入库预览。",
        ),
    ),
    "FINISH_INBOUND_TASK_TRANSITION_PREVIEW": GoalContractV1(
        goalType="FINISH_INBOUND_TASK_TRANSITION_PREVIEW",
        ownerExpert="logistics_expert",
        requiredEntityTypes=(),
        requiredFactTypes=("FINISH_INBOUND_TASK_TRANSITION_PREVIEW",),
        allowedTools=("query_pallet_tasks", "preview_task_transition"),
        evidenceTools=("preview_task_transition",),
        factValidation=FactValidationRuleV1(
            factLabel="成品入库任务处理预览",
            requiredFields=(
                "previewVersion", "previewStatusLabel", "canOpenBusinessDialog",
                "requestedTaskCount", "eligibleTaskCount", "tasks", "blockingIssues",
            ),
            listFields=("tasks", "blockingIssues"),
        ),
        limitations=(
            "预览只重查当前成品入库待处理任务并生成短期摘要，不执行入库或任务确认。",
            "previewRef 不是 executionToken，最终提交仍在既有业务弹窗中完成。",
        ),
    ),
    "FINISH_INBOUND_EXECUTION_PREVIEW": GoalContractV1(
        goalType="FINISH_INBOUND_EXECUTION_PREVIEW",
        ownerExpert="logistics_expert",
        requiredEntityTypes=(),
        requiredFactTypes=("FINISH_INBOUND_EXECUTION_PREVIEW",),
        allowedTools=("preview_finish_inbound_execution",),
        evidenceTools=("preview_finish_inbound_execution",),
        factValidation=FactValidationRuleV1(
            factLabel="成品入库精确执行预览",
            requiredFields=(
                "previewVersion", "previewStatusLabel", "readyForUserConfirmation",
                "requestedItemCount", "eligibleItemCount", "items", "blockingIssues",
            ),
            listFields=("items", "blockingIssues"),
        ),
        limitations=(
            "精确预览只绑定用户已填写的成品入库表单，不执行入库、任务确认或库存变更。",
            "预览引用和状态摘要不会展示给普通用户，也不是 executionToken。",
        ),
    ),
    "FINISH_OUTBOUND_TASK_TRANSITION_PREVIEW": GoalContractV1(
        goalType="FINISH_OUTBOUND_TASK_TRANSITION_PREVIEW",
        ownerExpert="logistics_expert",
        requiredEntityTypes=(),
        requiredFactTypes=("FINISH_OUTBOUND_TASK_TRANSITION_PREVIEW",),
        allowedTools=("query_pallet_tasks", "preview_task_transition"),
        evidenceTools=("preview_task_transition",),
        factValidation=FactValidationRuleV1(
            factLabel="成品出库任务处理预览",
            requiredFields=(
                "previewVersion", "previewStatusLabel", "canOpenBusinessDialog",
                "requestedTaskCount", "eligibleTaskCount", "tasks", "blockingIssues",
            ),
            listFields=("tasks", "blockingIssues"),
        ),
        limitations=(
            "预览只重查当前成品出库待处理任务、托盘在库状态和当前库存，不执行出库或任务确认。",
            "最终提交仍在既有业务弹窗中完成；预览结果不能替代提交时的再次校验。",
        ),
    ),
    "TRANSFER_TASK_TRANSITION_PREVIEW": GoalContractV1(
        goalType="TRANSFER_TASK_TRANSITION_PREVIEW",
        ownerExpert="logistics_expert",
        requiredEntityTypes=(),
        requiredFactTypes=("TRANSFER_TASK_TRANSITION_PREVIEW",),
        allowedTools=("query_pallet_tasks", "preview_task_transition"),
        evidenceTools=("preview_task_transition",),
        factValidation=FactValidationRuleV1(
            factLabel="调拨任务处理预览",
            requiredFields=(
                "previewVersion", "previewStatusLabel", "canOpenBusinessDialog",
                "requestedTaskCount", "eligibleTaskCount", "tasks", "blockingIssues",
            ),
            listFields=("tasks", "blockingIssues"),
        ),
        limitations=(
            "预览只重查当前调拨待处理任务、托盘库存和目标库位容量，不执行调拨或任务确认。",
            "最终目标位置以既有业务弹窗提交时的重新分配和校验为准。",
        ),
    ),
    "BOILING_BATCH_TRACE": GoalContractV1(
        goalType="BOILING_BATCH_TRACE",
        ownerExpert="production_expert",
        requiredEntityTypes=("BOILING_BATCH",),
        requiredFactTypes=("BOILING_BATCH_TRACE",),
        allowedTools=("resolve_production_entities", "query_boiling_batch_trace"),
        evidenceTools=("query_boiling_batch_trace",),
        factValidation=FactValidationRuleV1(
            factLabel="煮糖批次",
            requiredFields=("batchNo", "productLabel", "statusLabel", "usages"),
            listFields=("usages",),
        ),
        limitations=("只展示系统已经登记的煮糖批次追溯关系。",),
    ),
    "PRODUCTION_ORDER_PROGRESS": GoalContractV1(
        goalType="PRODUCTION_ORDER_PROGRESS",
        ownerExpert="production_expert",
        requiredEntityTypes=("PRODUCTION_ORDER",),
        requiredFactTypes=("PRODUCTION_ORDER_PROGRESS",),
        allowedTools=("resolve_production_entities", "query_production_order_progress"),
        evidenceTools=("query_production_order_progress",),
        factValidation=FactValidationRuleV1(
            factLabel="生产订单进度",
            requiredFields=("orderNo", "statusLabel", "materialRecordCount", "outputRecordCount", "outputs"),
            listFields=("outputs",),
        ),
        limitations=("订单进度不代表质量放行，也不自动计算产出率或损耗率。",),
    ),
    "PRODUCTION_MATERIAL_TRACE": GoalContractV1(
        goalType="PRODUCTION_MATERIAL_TRACE",
        ownerExpert="production_expert",
        requiredEntityTypes=("PRODUCTION_ORDER",),
        requiredFactTypes=("PRODUCTION_MATERIAL_TRACE",),
        allowedTools=("resolve_production_entities", "query_material_pick_trace"),
        evidenceTools=("query_material_pick_trace",),
        factValidation=FactValidationRuleV1(
            factLabel="生产领料",
            requiredFields=("orderNo", "statusLabel", "materialRecordCount", "records"),
            listFields=("records",),
            noDataWhenEmptyList="records",
        ),
        limitations=("记录表示确认领用时已完成库存扣减，但不等于计划差异、收率或损耗率分析。",),
    ),
    "PALLET_CURRENT_STATUS": GoalContractV1(
        goalType="PALLET_CURRENT_STATUS",
        ownerExpert="pallet_expert",
        requiredEntityTypes=("PALLET",),
        requiredFactTypes=("PALLET_CURRENT_STATUS",),
        allowedTools=("get_pallet_status", "query_qr_code_lifecycle"),
        evidenceTools=("get_pallet_status", "query_qr_code_lifecycle"),
        factValidation=FactValidationRuleV1(
            factLabel="托盘现状",
            requiredFields=("codeLabel", "currentStatusLabel"),
        ),
        limitations=("托盘现状只反映系统当前登记状态和关联库存。",),
    ),
    "PALLET_FLOW_HISTORY": GoalContractV1(
        goalType="PALLET_FLOW_HISTORY",
        ownerExpert="pallet_expert",
        requiredEntityTypes=("PALLET",),
        requiredFactTypes=("PALLET_FLOW_HISTORY",),
        allowedTools=("query_qr_code_lifecycle", "query_pallet_flow_records"),
        evidenceTools=("query_qr_code_lifecycle", "query_pallet_flow_records"),
        factValidation=FactValidationRuleV1(
            factLabel="托盘流转",
            requiredFields=("scopeLabel", "total", "records"),
            listFields=("records",),
            noDataWhenEmptyList="records",
        ),
        limitations=("流转历史只包含系统已登记的托盘事件。",),
    ),
    "PRODUCT_ASSAY_REPORT": GoalContractV1(
        goalType="PRODUCT_ASSAY_REPORT",
        ownerExpert="assay_expert",
        requiredEntityTypes=(),
        requiredFactTypes=("PRODUCT_ASSAY_REPORT",),
        allowedTools=("resolve_products", "get_assay_status", "get_assay_report_detail"),
        evidenceTools=("get_assay_status", "get_assay_report_detail"),
        factValidation=FactValidationRuleV1(
            factLabel="化验报告",
            requiredFields=("hasAssay", "productLabel", "judgeLabel", "standardLabel", "metrics"),
            listFields=("metrics",),
            noDataBooleanField="hasAssay",
            noDataBooleanValue=False,
        ),
        limitations=("无适用标准表示暂时无法自动判定，不等同于不合格。",),
    ),
    "PRODUCT_ASSAY_HISTORY": GoalContractV1(
        goalType="PRODUCT_ASSAY_HISTORY",
        ownerExpert="assay_expert",
        requiredEntityTypes=(),
        requiredFactTypes=("PRODUCT_ASSAY_HISTORY",),
        allowedTools=("resolve_products", "query_assay_records"),
        evidenceTools=("query_assay_records",),
        factValidation=FactValidationRuleV1(
            factLabel="历史化验",
            requiredFields=("scopeLabel", "total", "records"),
            listFields=("records",),
            noDataWhenEmptyList="records",
        ),
        scopeEntityBindings=("PRODUCT_SCOPE",),
        limitations=("历史化验记录列表不等同于趋势分析。",),
    ),
    "BOILING_BATCH_LIST": GoalContractV1(
        goalType="BOILING_BATCH_LIST",
        ownerExpert="production_expert",
        requiredEntityTypes=(),
        requiredFactTypes=("BOILING_BATCH_LIST",),
        allowedTools=("query_boiling_batches",),
        evidenceTools=("query_boiling_batches",),
        factValidation=FactValidationRuleV1(
            factLabel="煮糖批次列表",
            requiredFields=("total", "candidates"),
            listFields=("candidates",),
            noDataWhenEmptyList="candidates",
        ),
        limitations=("批次列表只包含系统已登记且符合当前查询范围的煮糖批次。",),
    ),
    "PRODUCTION_OUTPUT_DESTINATIONS": GoalContractV1(
        goalType="PRODUCTION_OUTPUT_DESTINATIONS",
        ownerExpert="production_expert",
        requiredEntityTypes=("PRODUCTION_ORDER",),
        requiredFactTypes=("PRODUCTION_OUTPUT_DESTINATIONS",),
        allowedTools=("resolve_production_entities", "query_production_order_progress"),
        evidenceTools=("query_production_order_progress",),
        factValidation=FactValidationRuleV1(
            factLabel="生产产出去向",
            requiredFields=("orderNo", "outputRecordCount", "outputs"),
            listFields=("outputs",),
            noDataWhenEmptyList="outputs",
        ),
        limitations=("只有已形成库存、已有入库时间或状态明确为已入库的记录才属于实际入库去向。",),
    ),
    "CURRENT_UNQUALIFIED_INVENTORY": GoalContractV1(
        goalType="CURRENT_UNQUALIFIED_INVENTORY",
        ownerExpert="assay_expert",
        requiredEntityTypes=(),
        requiredFactTypes=("CURRENT_UNQUALIFIED_INVENTORY",),
        allowedTools=("resolve_products", "resolve_warehouses", "query_unqualified_inventory"),
        evidenceTools=("query_unqualified_inventory",),
        factValidation=FactValidationRuleV1(
            factLabel="库存质量筛选",
            requiredFields=("queryLabel", "totalGroups", "records"),
            listFields=("records",),
            noDataWhenEmptyList="records",
        ),
        scopeEntityBindings=("PRODUCT_SCOPE", "WAREHOUSE_SCOPE"),
        limitations=("无化验、无适用标准和标准多候选均不等同于不合格。",),
    ),
    "CURRENT_INVENTORY_MATCHING_STANDARD": GoalContractV1(
        goalType="CURRENT_INVENTORY_MATCHING_STANDARD",
        ownerExpert="assay_expert",
        requiredEntityTypes=(),
        requiredFactTypes=("CURRENT_INVENTORY_MATCHING_STANDARD",),
        allowedTools=(
            "resolve_products",
            "resolve_warehouses",
            "query_quality_standard_catalog",
            "query_inventory_by_quality_standard",
        ),
        evidenceTools=("query_inventory_by_quality_standard",),
        factValidation=FactValidationRuleV1(
            factLabel="库存质量筛选",
            requiredFields=("queryLabel", "totalGroups", "records"),
            listFields=("records",),
            noDataWhenEmptyList="records",
        ),
        scopeEntityBindings=("PRODUCT_SCOPE", "WAREHOUSE_SCOPE"),
        limitations=("满足指定标准不表示产品当前绑定了该标准，也不表示历史化验采用了该标准。",),
    ),
    "CURRENT_INVENTORY_MATCHING_METRIC": GoalContractV1(
        goalType="CURRENT_INVENTORY_MATCHING_METRIC",
        ownerExpert="assay_expert",
        requiredEntityTypes=(),
        requiredFactTypes=("CURRENT_INVENTORY_MATCHING_METRIC",),
        allowedTools=("resolve_products", "resolve_warehouses", "query_inventory_by_assay_metrics"),
        evidenceTools=("query_inventory_by_assay_metrics",),
        factValidation=FactValidationRuleV1(
            factLabel="库存质量筛选",
            requiredFields=("queryLabel", "totalGroups", "records"),
            listFields=("records",),
            noDataWhenEmptyList="records",
        ),
        scopeEntityBindings=("PRODUCT_SCOPE", "WAREHOUSE_SCOPE"),
        limitations=("单个原始指标命中不等同于满足完整质量标准。",),
    ),
    "CURRENT_INVENTORY_ASSAY_GAPS": GoalContractV1(
        goalType="CURRENT_INVENTORY_ASSAY_GAPS",
        ownerExpert="assay_expert",
        requiredEntityTypes=(),
        requiredFactTypes=("CURRENT_INVENTORY_ASSAY_GAPS",),
        allowedTools=("resolve_products", "resolve_warehouses", "query_products_without_recent_assay"),
        evidenceTools=("query_products_without_recent_assay",),
        factValidation=FactValidationRuleV1(
            factLabel="库存缺化验",
            requiredFields=("scopeLabel", "totalGroups", "groups"),
            listFields=("groups",),
            noDataWhenEmptyList="groups",
        ),
        scopeEntityBindings=("PRODUCT_SCOPE", "WAREHOUSE_SCOPE"),
        limitations=("无标准表示已有化验但无法判定，不等同于缺少化验。",),
    ),
    "CURRENT_INVENTORY_LEDGER": GoalContractV1(
        goalType="CURRENT_INVENTORY_LEDGER",
        ownerExpert="inventory_expert",
        requiredEntityTypes=(),
        requiredFactTypes=("CURRENT_INVENTORY_LEDGER",),
        allowedTools=("resolve_products", "resolve_warehouses", "query_inventory_ledger"),
        evidenceTools=("query_inventory_ledger",),
        factValidation=FactValidationRuleV1(
            factLabel="当前库存台账",
            requiredFields=("dataScope", "total", "records"),
            listFields=("records",),
            noDataWhenEmptyList="records",
        ),
        limitations=("库存台账是当前快照，不是历史库存流水，也不自动证明质量合格。",),
    ),
    "WAREHOUSE_STATUS": GoalContractV1(
        goalType="WAREHOUSE_STATUS",
        ownerExpert="warehouse_expert",
        requiredEntityTypes=("WAREHOUSE",),
        requiredFactTypes=("WAREHOUSE_STATUS",),
        allowedTools=("resolve_warehouses", "get_warehouse_status"),
        evidenceTools=("get_warehouse_status",),
        factValidation=FactValidationRuleV1(
            factLabel="库位状态",
            requiredFields=("warehouseName",),
        ),
        limitations=("库位状态只反映当前登记容量和占用情况，不是历史容量趋势。",),
    ),
    "WAREHOUSE_CAPACITY_DISTRIBUTION": GoalContractV1(
        goalType="WAREHOUSE_CAPACITY_DISTRIBUTION",
        ownerExpert="warehouse_expert",
        requiredEntityTypes=(),
        requiredFactTypes=("WAREHOUSE_CAPACITY_DISTRIBUTION",),
        allowedTools=("resolve_warehouses", "query_warehouse_capacity_distribution"),
        evidenceTools=("query_warehouse_capacity_distribution",),
        factValidation=FactValidationRuleV1(
            factLabel="库位容量分布",
            requiredFields=("dataScope", "total", "summary", "records"),
            listFields=("records",),
            noDataWhenEmptyList="records",
        ),
        limitations=("容量分布是当前登记事实，不等于拥堵风险预测。",),
    ),
    "WAREHOUSE_RECENT_OPERATIONS": GoalContractV1(
        goalType="WAREHOUSE_RECENT_OPERATIONS",
        ownerExpert="warehouse_expert",
        requiredEntityTypes=(),
        requiredFactTypes=("WAREHOUSE_RECENT_OPERATIONS",),
        allowedTools=("resolve_warehouses", "query_warehouse_recent_operations"),
        evidenceTools=("query_warehouse_recent_operations",),
        factValidation=FactValidationRuleV1(
            factLabel="库位近期操作",
            requiredFields=("dataScope", "count", "records"),
            listFields=("records",),
            noDataWhenEmptyList="records",
        ),
        limitations=("近期操作只包含已登记的托盘流转事件，不是完整审计日志。",),
    ),
    "WAREHOUSE_MIXED_STORAGE_FACTS": GoalContractV1(
        goalType="WAREHOUSE_MIXED_STORAGE_FACTS",
        ownerExpert="warehouse_expert",
        requiredEntityTypes=(),
        requiredFactTypes=("WAREHOUSE_MIXED_STORAGE_FACTS",),
        allowedTools=("resolve_warehouses", "query_warehouse_mixed_storage_facts"),
        evidenceTools=("query_warehouse_mixed_storage_facts",),
        factValidation=FactValidationRuleV1(
            factLabel="库位混放事实",
            requiredFields=("dataScope", "count", "records"),
            listFields=("records",),
            noDataWhenEmptyList="records",
        ),
        limitations=("混放事实只描述系统当前登记组合，不自动判定违规。",),
    ),
    "ASSAY_ABNORMALITY_SUMMARY": GoalContractV1(
        goalType="ASSAY_ABNORMALITY_SUMMARY",
        ownerExpert="assay_expert",
        requiredEntityTypes=(),
        requiredFactTypes=("ASSAY_ABNORMALITY_SUMMARY",),
        allowedTools=("resolve_products", "query_assay_abnormalities"),
        evidenceTools=("query_assay_abnormalities",),
        factValidation=FactValidationRuleV1(
            factLabel="化验异常汇总",
            requiredFields=("scopeLabel", "dateRangeLabel", "groupBy", "total", "groups"),
            listFields=("groups",),
            noDataWhenEmptyList="groups",
        ),
        limitations=("无标准和标准多候选表示无法自动判定，不等同于化验不合格。",),
    ),
    "CURRENT_INVENTORY_STANDARD_GAPS": GoalContractV1(
        goalType="CURRENT_INVENTORY_STANDARD_GAPS",
        ownerExpert="assay_expert",
        requiredEntityTypes=(),
        requiredFactTypes=("CURRENT_INVENTORY_STANDARD_GAPS",),
        allowedTools=("resolve_products", "query_assay_standard_coverage"),
        evidenceTools=("query_assay_standard_coverage",),
        factValidation=FactValidationRuleV1(
            factLabel="库存标准缺口",
            requiredFields=("scopeLabel", "coverageType", "totalGroups", "groups"),
            listFields=("groups",),
            noDataWhenEmptyList="groups",
        ),
        limitations=("缺少有效标准不等同于化验不合格，也不等同于产品不可使用。",),
    ),
    "PALLET_ANOMALY_SUMMARY": GoalContractV1(
        goalType="PALLET_ANOMALY_SUMMARY",
        ownerExpert="pallet_expert",
        requiredEntityTypes=(),
        requiredFactTypes=("PALLET_ANOMALY_SUMMARY",),
        allowedTools=("resolve_products", "resolve_warehouses", "query_pallet_anomalies"),
        evidenceTools=("query_pallet_anomalies",),
        factValidation=FactValidationRuleV1(
            factLabel="托盘异常汇总",
            requiredFields=("scopeLabel", "dateRangeLabel", "total", "groups"),
            listFields=("groups",),
            noDataWhenEmptyList="groups",
        ),
        limitations=("异常汇总只反映已登记事实，不自动推断异常原因或责任。",),
    ),
    "PRINTED_NOT_INBOUND_QR_CODES": GoalContractV1(
        goalType="PRINTED_NOT_INBOUND_QR_CODES",
        ownerExpert="pallet_expert",
        requiredEntityTypes=(),
        requiredFactTypes=("PRINTED_NOT_INBOUND_QR_CODES",),
        allowedTools=("resolve_products", "query_printed_not_inbound_codes"),
        evidenceTools=("query_printed_not_inbound_codes",),
        factValidation=FactValidationRuleV1(
            factLabel="已打印未入库二维码",
            requiredFields=("scopeLabel", "dateRangeLabel", "printedCount", "inboundCount", "notInboundCount", "groups"),
            listFields=("groups",),
            noDataWhenEmptyList="groups",
        ),
        limitations=("已打印不等于已绑定，已绑定也不等于已入库。",),
    ),
    "QR_BATCH_INBOUND_COMPLETION": GoalContractV1(
        goalType="QR_BATCH_INBOUND_COMPLETION",
        ownerExpert="pallet_expert",
        requiredEntityTypes=(),
        requiredFactTypes=("QR_BATCH_INBOUND_COMPLETION",),
        allowedTools=("query_qr_batch_inbound_completion",),
        evidenceTools=("query_qr_batch_inbound_completion",),
        factValidation=FactValidationRuleV1(
            factLabel="二维码批次入库完成情况",
            requiredFields=("batchLabel", "printedCount", "inboundCount", "notInboundCount", "unfinishedExamples"),
            listFields=("unfinishedExamples",),
        ),
        limitations=("入库完成情况按系统已确认的库存关联口径计算。",),
    ),
    "PRODUCTION_LABEL_COMPLETION": GoalContractV1(
        goalType="PRODUCTION_LABEL_COMPLETION",
        ownerExpert="production_expert",
        requiredEntityTypes=("PRODUCTION_ORDER",),
        requiredFactTypes=("PRODUCTION_LABEL_COMPLETION",),
        allowedTools=("resolve_production_entities", "query_production_label_completion"),
        evidenceTools=("query_production_label_completion",),
        factValidation=FactValidationRuleV1(
            factLabel="生产标签完成情况",
            requiredFields=(
                "orderNo",
                "labelBatchCount",
                "requiredQrCount",
                "boundQrCount",
                "inboundQrCount",
                "batches",
            ),
            listFields=("batches",),
        ),
        limitations=("打印、绑定和入库是不同阶段，不能相互替代。",),
    ),
    "IN_PROCESS_MATERIALS": GoalContractV1(
        goalType="IN_PROCESS_MATERIALS",
        ownerExpert="production_expert",
        requiredEntityTypes=(),
        requiredFactTypes=("IN_PROCESS_MATERIALS",),
        allowedTools=("resolve_products", "query_in_process_materials"),
        evidenceTools=("query_in_process_materials",),
        factValidation=FactValidationRuleV1(
            factLabel="已确认领用并扣减库存、订单未完成的在制半成品",
            requiredFields=("dataScope", "total", "records"),
            listFields=("records",),
            noDataWhenEmptyList="records",
        ),
        limitations=("在制记录不代表仍可再次领用、质量已放行或实时库存结余。",),
    ),
    "STOCK_DOCUMENTS": GoalContractV1(
        goalType="STOCK_DOCUMENTS",
        ownerExpert="logistics_expert",
        requiredEntityTypes=(),
        requiredFactTypes=("STOCK_DOCUMENTS",),
        allowedTools=("resolve_products", "resolve_warehouses", "query_stock_documents"),
        evidenceTools=("query_stock_documents",),
        factValidation=FactValidationRuleV1(
            factLabel="库存业务单据",
            requiredFields=("dataScope", "documentType", "total", "records"),
            listFields=("records",),
            noDataWhenEmptyList="records",
        ),
        limitations=("每次查询只对应一种明确的单据来源，不自动合并不同业务口径。",),
    ),
    "AUTO_INBOUND_BATCH_STATUS": GoalContractV1(
        goalType="AUTO_INBOUND_BATCH_STATUS",
        ownerExpert="logistics_expert",
        requiredEntityTypes=(),
        requiredFactTypes=("AUTO_INBOUND_BATCH_STATUS",),
        allowedTools=("query_auto_inbound_batches", "get_auto_inbound_batch_detail"),
        evidenceTools=("query_auto_inbound_batches", "get_auto_inbound_batch_detail"),
        factValidation=FactValidationRuleV1(
            factLabel="自动报数入库批次",
            requiredFields=("dataScope", "count", "records"),
            listFields=("records",),
            noDataWhenEmptyList="records",
            alternativeShapes=(
                FactValidationShapeV1(
                    requiredFields=("dataScope", "taskCount", "tasks"),
                    listFields=("tasks",),
                    noDataWhenEmptyList="tasks",
                ),
            ),
        ),
        limitations=("批次和任务详情只供核对，不代表已确认或执行入库。",),
    ),
    "ASSAY_REFERENCE_DATA": GoalContractV1(
        goalType="ASSAY_REFERENCE_DATA",
        ownerExpert="assay_expert",
        requiredEntityTypes=(),
        requiredFactTypes=("ASSAY_REFERENCE_DATA",),
        allowedTools=(
            "resolve_products",
            "query_assay_groups",
            "query_quality_standard_catalog",
            "get_quality_standard_detail",
            "query_product_standard_relations",
        ),
        evidenceTools=(
            "query_assay_groups",
            "query_quality_standard_catalog",
            "get_quality_standard_detail",
            "query_product_standard_relations",
        ),
        factValidation=FactValidationRuleV1(
            factLabel="化验基础资料",
            requiredFields=("dataScope", "total", "records"),
            listFields=("records",),
            noDataWhenEmptyList="records",
            alternativeShapes=(
                FactValidationShapeV1(
                    requiredFields=("dataScope", "standardCode", "standardName", "metrics"),
                    listFields=("metrics",),
                ),
                FactValidationShapeV1(
                    requiredFields=("dataScope", "productName", "count", "records"),
                    listFields=("records",),
                    noDataWhenEmptyList="records",
                ),
            ),
        ),
        limitations=("基础资料只描述系统当前配置，不等同于某批产品的化验判定。",),
    ),
    "PRODUCT_QUALITY_CONFIGURATION": GoalContractV1(
        goalType="PRODUCT_QUALITY_CONFIGURATION",
        ownerExpert="assay_expert",
        requiredEntityTypes=("PRODUCT",),
        requiredFactTypes=("PRODUCT_QUALITY_CONFIGURATION",),
        allowedTools=(
            "resolve_products",
            "query_product_quality_configuration",
        ),
        evidenceTools=("query_product_quality_configuration",),
        factValidation=FactValidationRuleV1(
            factLabel="具体产品当前适用质量标准和所属批量化验组",
            requiredFields=("dataScope", "productName", "standards", "assayGroups"),
            listFields=("standards", "assayGroups"),
        ),
        limitations=(
            "适用质量标准是当前产品配置，不证明某次化验采用该标准或产品已经合格。",
            "所属批量化验组是批量化验产品分组，不是质量标准或库存批次范围。",
        ),
    ),
    "FIXED_PRODUCT_QR_POOL": GoalContractV1(
        goalType="FIXED_PRODUCT_QR_POOL",
        ownerExpert="pallet_expert",
        requiredEntityTypes=(),
        requiredFactTypes=("FIXED_PRODUCT_QR_POOL",),
        allowedTools=("resolve_products", "query_fixed_product_qr_pool"),
        evidenceTools=("query_fixed_product_qr_pool",),
        factValidation=FactValidationRuleV1(
            factLabel="固定产品二维码池",
            requiredFields=("dataScope", "total", "records"),
            listFields=("records",),
            noDataWhenEmptyList="records",
        ),
        limitations=("二维码池记录不等于二维码已绑定托盘、已使用或已入库。",),
    ),
    "MATERIAL_CANDIDATES": GoalContractV1(
        goalType="MATERIAL_CANDIDATES",
        ownerExpert="production_expert",
        requiredEntityTypes=("PRODUCTION_ORDER",),
        requiredFactTypes=("MATERIAL_CANDIDATES",),
        allowedTools=("resolve_production_entities", "query_material_candidates"),
        evidenceTools=("query_material_candidates",),
        factValidation=FactValidationRuleV1(
            factLabel="生产领料候选",
            requiredFields=("dataScope", "total", "records"),
            listFields=("records",),
            noDataWhenEmptyList="records",
        ),
        limitations=("候选库存不是系统推荐，也不代表已领料、已预留或符合 FIFO/FEFO。",),
    ),
    "PRODUCT_MASTER_DATA": GoalContractV1(
        goalType="PRODUCT_MASTER_DATA",
        ownerExpert="master_data_expert",
        requiredEntityTypes=(),
        requiredFactTypes=("PRODUCT_MASTER_DATA",),
        allowedTools=("resolve_products", "query_product_catalog", "get_product_detail"),
        evidenceTools=("query_product_catalog", "get_product_detail"),
        factValidation=FactValidationRuleV1(
            factLabel="产品主数据",
            requiredFields=("dataScope", "total", "records"),
            listFields=("records",),
            noDataWhenEmptyList="records",
            alternativeShapes=(
                FactValidationShapeV1(
                    requiredFields=("dataScope", "productName"),
                ),
            ),
        ),
        limitations=("产品主数据只描述当前配置，不代表库存、化验或可生产状态。",),
    ),
    "SCREEN_MESH_CATALOG": GoalContractV1(
        goalType="SCREEN_MESH_CATALOG",
        ownerExpert="master_data_expert",
        requiredEntityTypes=(),
        requiredFactTypes=("SCREEN_MESH_CATALOG",),
        allowedTools=("query_screen_mesh_catalog",),
        evidenceTools=("query_screen_mesh_catalog",),
        factValidation=FactValidationRuleV1(
            factLabel="筛网目录",
            requiredFields=("dataScope", "total", "records"),
            listFields=("records",),
            noDataWhenEmptyList="records",
        ),
        limitations=("筛网目录是主数据配置，不等于某产品当前使用的筛网。",),
    ),
    "EMPLOYEE_ROSTER": GoalContractV1(
        goalType="EMPLOYEE_ROSTER",
        ownerExpert="administration_expert",
        requiredEntityTypes=(),
        requiredFactTypes=("EMPLOYEE_ROSTER",),
        allowedTools=("query_employee_roster",),
        evidenceTools=("query_employee_roster",),
        factValidation=FactValidationRuleV1(
            factLabel="员工名录",
            requiredFields=("dataScope", "total", "records"),
            listFields=("records",),
            noDataWhenEmptyList="records",
        ),
        limitations=("员工名录只返回授权范围内的业务字段，不包含认证凭据。",),
    ),
    "ROLE_PERMISSION_CATALOG": GoalContractV1(
        goalType="ROLE_PERMISSION_CATALOG",
        ownerExpert="administration_expert",
        requiredEntityTypes=(),
        requiredFactTypes=("ROLE_PERMISSION_CATALOG",),
        allowedTools=("query_roles", "get_role_permission_summary"),
        evidenceTools=("query_roles", "get_role_permission_summary"),
        factValidation=FactValidationRuleV1(
            factLabel="角色权限目录",
            requiredFields=("dataScope", "total", "records"),
            listFields=("records",),
            noDataWhenEmptyList="records",
            alternativeShapes=(
                FactValidationShapeV1(
                    requiredFields=("dataScope", "roleName", "permissionCount", "permissions"),
                    listFields=("permissions",),
                ),
            ),
        ),
        limitations=("角色权限只描述当前授权配置，不代表某次操作已经获得批准。",),
    ),
    "BUSINESS_OPERATION_LOGS": GoalContractV1(
        goalType="BUSINESS_OPERATION_LOGS",
        ownerExpert="audit_expert",
        requiredEntityTypes=(),
        requiredFactTypes=("BUSINESS_OPERATION_LOGS",),
        allowedTools=("search_operation_logs",),
        evidenceTools=("search_operation_logs",),
        factValidation=FactValidationRuleV1(
            factLabel="业务操作日志",
            requiredFields=("dataScope", "total", "records"),
            listFields=("records",),
            noDataWhenEmptyList="records",
        ),
        limitations=("操作日志只证明系统已登记事件，不自动判定责任或违规。",),
    ),
    "AGENT_TOOL_AUDIT": GoalContractV1(
        goalType="AGENT_TOOL_AUDIT",
        ownerExpert="audit_expert",
        requiredEntityTypes=(),
        requiredFactTypes=("AGENT_TOOL_AUDIT",),
        allowedTools=("query_agent_tool_audit",),
        evidenceTools=("query_agent_tool_audit",),
        factValidation=FactValidationRuleV1(
            factLabel="Agent 工具审计",
            requiredFields=("dataScope", "total", "records"),
            listFields=("records",),
            noDataWhenEmptyList="records",
        ),
        limitations=("工具审计记录调用事实，不自动说明最终回答正确。",),
    ),
    "AGENT_ANSWER_REVIEWS": GoalContractV1(
        goalType="AGENT_ANSWER_REVIEWS",
        ownerExpert="audit_expert",
        requiredEntityTypes=(),
        requiredFactTypes=("AGENT_ANSWER_REVIEWS",),
        allowedTools=("query_agent_answer_reviews",),
        evidenceTools=("query_agent_answer_reviews",),
        factValidation=FactValidationRuleV1(
            factLabel="Agent 回答复核",
            requiredFields=("dataScope", "total", "records"),
            listFields=("records",),
            noDataWhenEmptyList="records",
        ),
        limitations=("回答复核记录是治理依据，不替代业务数据源本身。",),
    ),
    "DAILY_PRODUCTION_ANALYSIS": GoalContractV1(
        goalType="DAILY_PRODUCTION_ANALYSIS",
        ownerExpert="analytics_expert",
        requiredEntityTypes=(),
        requiredFactTypes=("DAILY_PRODUCTION_ANALYSIS",),
        allowedTools=("run_registered_report",),
        evidenceTools=("run_registered_report",),
        factValidation=FactValidationRuleV1(
            factLabel="按生产日期登记的实际产出分析",
            requiredFields=(
                "dataScope",
                "reportDefinitionId",
                "reportVersion",
                "isEmpty",
                "metrics",
                "dailySeries",
                "productBreakdowns",
                "dataQuality",
            ),
            listFields=("dailySeries", "productBreakdowns"),
            noDataBooleanField="isEmpty",
        ),
        limitations=(
            "只统计生产订单中已登记且未取消的产出记录。",
            "标签、二维码绑定和入库进度不计入产量。",
            "未登记的计划达成率、班次对比、良率和预测不得生成。",
        ),
    ),
    "TODAY_OPERATIONS_OVERVIEW": GoalContractV1(
        goalType="TODAY_OPERATIONS_OVERVIEW",
        ownerExpert="analytics_expert",
        requiredEntityTypes=(),
        requiredFactTypes=("TODAY_OPERATIONS_OVERVIEW",),
        allowedTools=("run_registered_report",),
        evidenceTools=("run_registered_report",),
        factValidation=FactValidationRuleV1(
            factLabel="北京时间今日的已登记运营事实概览",
            requiredFields=(
                "dataScope",
                "reportDefinitionId",
                "reportVersion",
                "isEmpty",
                "operationsOverview",
                "dataQuality",
            ),
            noDataBooleanField="isEmpty",
        ),
        limitations=(
            "只支持北京时间今天；当前库存和当前待处理任务是生成时快照，不是历史值。",
            "确认领用与稳定登记产出采用独立业务日期，只能并列展示，不能直接相除。",
            "当前库存不表示今天的库存变化；库存变化必须使用通过发布门禁的库存趋势报表。",
            "不得生成计划达成率、良率、收率、损耗率、SLA 逾期、预测或因果结论。",
        ),
    ),
    "QUALITY_ASSAY_TREND_ANALYSIS": GoalContractV1(
        goalType="QUALITY_ASSAY_TREND_ANALYSIS",
        ownerExpert="analytics_expert",
        requiredEntityTypes=(),
        requiredFactTypes=("QUALITY_ASSAY_TREND_ANALYSIS",),
        allowedTools=("run_registered_report",),
        evidenceTools=("run_registered_report",),
        factValidation=FactValidationRuleV1(
            factLabel="按生产日期登记的化验判定趋势",
            requiredFields=(
                "dataScope",
                "reportDefinitionId",
                "reportVersion",
                "isEmpty",
                "qualityMetrics",
                "qualitySeries",
                "qualityProductBreakdowns",
                "standardBreakdowns",
                "dataQuality",
            ),
            listFields=(
                "qualitySeries",
                "qualityProductBreakdowns",
                "standardBreakdowns",
            ),
            noDataBooleanField="isEmpty",
        ),
        limitations=(
            "合格率只使用合格与不合格记录作为分母。",
            "无标准、标准多候选和缺少判定必须单独展示。",
            "只统计已有化验记录，不能反推历史批次是否缺少化验。",
            "不得用当前标准重算历史判定或输出因果结论。",
        ),
    ),
    "QUALITY_METRIC_TREND_ANALYSIS": GoalContractV1(
        goalType="QUALITY_METRIC_TREND_ANALYSIS",
        ownerExpert="analytics_expert",
        requiredEntityTypes=(),
        requiredFactTypes=("QUALITY_METRIC_TREND_ANALYSIS",),
        allowedTools=("run_registered_report",),
        evidenceTools=("run_registered_report",),
        factValidation=FactValidationRuleV1(
            factLabel="按生产日期登记的单项化验指标趋势",
            requiredFields=(
                "dataScope",
                "reportDefinitionId",
                "reportVersion",
                "isEmpty",
                "metricTrendSummary",
                "metricSeries",
                "metricProductBreakdowns",
                "metricStandardBreakdowns",
                "dataQuality",
            ),
            listFields=(
                "metricSeries",
                "metricProductBreakdowns",
                "metricStandardBreakdowns",
            ),
            noDataBooleanField="isEmpty",
        ),
        limitations=(
            "原始统计包含所有有实测值的样本，指标达标率只统计历史标准快照中可比较的样本。",
            "无历史标准或指标单位不可比的样本不得进入达标率分母。",
            "样本过少或时间点稀疏时不得输出改善、恶化或因果结论。",
            "不得用当前标准重算历史样本。",
        ),
    ),
    "PRODUCTION_INPUT_OUTPUT_TREND": GoalContractV1(
        goalType="PRODUCTION_INPUT_OUTPUT_TREND",
        ownerExpert="analytics_expert",
        requiredEntityTypes=(),
        requiredFactTypes=("PRODUCTION_INPUT_OUTPUT_TREND",),
        allowedTools=("run_registered_report",),
        evidenceTools=("run_registered_report",),
        factValidation=FactValidationRuleV1(
            factLabel="生产领料与稳定登记产出的独立趋势及订单归属",
            requiredFields=(
                "dataScope",
                "reportDefinitionId",
                "reportVersion",
                "isEmpty",
                "productionFlowMetrics",
                "productionFlowDailySeries",
                "productionFlowOrderBreakdowns",
                "dataQuality",
            ),
            listFields=(
                "productionFlowDailySeries",
                "productionFlowOrderBreakdowns",
            ),
            noDataBooleanField="isEmpty",
        ),
        limitations=(
            "成品订单半成品领用在确认时同步扣减仓库库存，可作为已确认生产投入。",
            "每日领料按领料时间统计，稳定登记产出按生产日期统计，两条序列不能直接相除。",
            "半成品订单的输入来自最终确认的煮糖批次领用，不代表真实投料时间。",
            "领用确认时间与产出生产日期不是同一时间口径，且没有返工、报废和损耗事实，不得生成产耗比、收率、损耗率或因果结论。",
        ),
    ),
    "PROCESS_EFFICIENCY_TREND": GoalContractV1(
        goalType="PROCESS_EFFICIENCY_TREND",
        ownerExpert="analytics_expert",
        requiredEntityTypes=(),
        requiredFactTypes=("PROCESS_EFFICIENCY_TREND",),
        allowedTools=("run_registered_report",),
        evidenceTools=("run_registered_report",),
        factValidation=FactValidationRuleV1(
            factLabel="系统已登记托盘任务处理耗时趋势",
            requiredFields=(
                "dataScope",
                "reportDefinitionId",
                "reportVersion",
                "isEmpty",
                "palletTaskCycleMetrics",
                "palletTaskCycleDailySeries",
                "palletTaskCycleTypeBreakdowns",
                "palletTaskPendingItems",
                "dataQuality",
            ),
            listFields=(
                "palletTaskCycleDailySeries",
                "palletTaskCycleTypeBreakdowns",
                "palletTaskPendingItems",
            ),
            noDataBooleanField="isEmpty",
        ),
        limitations=(
            "统计队列按任务创建日期形成，完成耗时与进行中等待时长必须分开呈现。",
            "取消任务没有独立取消时间，只能统计取消数量，不能计算取消耗时。",
            "当前没有登记 SLA，不得把等待时长称为逾期。",
            "结果只代表系统已登记托盘任务，不代表现场全部流程、人员绩效或因果结论。",
        ),
    ),
    "INVENTORY_LEVEL_TREND_ANALYSIS": GoalContractV1(
        goalType="INVENTORY_LEVEL_TREND_ANALYSIS",
        ownerExpert="analytics_expert",
        requiredEntityTypes=(),
        requiredFactTypes=("INVENTORY_LEVEL_TREND_ANALYSIS",),
        allowedTools=("run_registered_report",),
        evidenceTools=("run_registered_report",),
        factValidation=FactValidationRuleV1(
            factLabel="按产品汇总的库存水平变化趋势",
            requiredFields=(
                "dataScope",
                "reportDefinitionId",
                "reportVersion",
                "isEmpty",
                "inventoryTrendMetrics",
                "inventoryTrendDailySeries",
                "inventoryTrendProductBreakdowns",
                "dataQuality",
            ),
            listFields=(
                "inventoryTrendDailySeries",
                "inventoryTrendProductBreakdowns",
            ),
            noDataBooleanField="isEmpty",
        ),
        limitations=(
            "正式库存趋势只允许使用已通过守恒对账的连续日终快照。",
            "本地历史回放必须明确标记为模拟，不得作为生产发布证据。",
            "库存水平变化不自动解释原因，也不得据此预测未来库存。",
        ),
    ),
    "PROCESS_KNOWLEDGE_QUERY": GoalContractV1(
        goalType="PROCESS_KNOWLEDGE_QUERY",
        ownerExpert="knowledge_expert",
        requiredEntityTypes=(),
        requiredFactTypes=("PROCESS_KNOWLEDGE_QUERY",),
        allowedTools=("search_approved_knowledge",),
        evidenceTools=("search_approved_knowledge",),
        factValidation=FactValidationRuleV1(
            factLabel="现行工艺知识",
            requiredFields=(
                "status",
                "corpusVersion",
                "queryLabel",
                "knowledgeDomains",
                "evidence",
            ),
            listFields=("knowledgeDomains", "evidence"),
            noDataWhenEmptyList="evidence",
        ),
        limitations=(
            "知识材料是静态现行资料，不代表实时库存、批次质量或生产订单状态。",
            "回答只能依据返回证据，不能用模型记忆补充材料外事实。",
        ),
    ),
    "ENTERPRISE_KNOWLEDGE_QUERY": GoalContractV1(
        goalType="ENTERPRISE_KNOWLEDGE_QUERY",
        ownerExpert="knowledge_expert",
        requiredEntityTypes=(),
        requiredFactTypes=("ENTERPRISE_KNOWLEDGE_QUERY",),
        allowedTools=("search_approved_knowledge",),
        evidenceTools=("search_approved_knowledge",),
        factValidation=FactValidationRuleV1(
            factLabel="现行企业资料知识",
            requiredFields=(
                "status",
                "corpusVersion",
                "queryLabel",
                "knowledgeDomains",
                "evidence",
            ),
            listFields=("knowledgeDomains", "evidence"),
            noDataWhenEmptyList="evidence",
        ),
        limitations=(
            "企业、产品宣传、认证和销售网络回答只表示当前知识库材料记载。",
            "回答只能依据返回证据，不能用模型记忆补充材料外事实。",
        ),
    ),
}

if set(GOAL_CONTRACTS) != set(REGISTERED_CORE_GOAL_TYPES):
    raise RuntimeError("CoreGoalTypeV1 and GOAL_CONTRACTS must define the same registered goals")
for _contract in GOAL_CONTRACTS.values():
    if not _contract.evidenceTools or not set(_contract.evidenceTools).issubset(_contract.allowedTools):
        raise RuntimeError(f"{_contract.goalType} evidenceTools must be a non-empty subset of allowedTools")


DIRECT_PRIMARY_TOOL_GOALS: dict[str, CoreGoalTypeV1] = {
    "query_inventory_ledger": "CURRENT_INVENTORY_LEDGER",
    "get_warehouse_status": "WAREHOUSE_STATUS",
    "query_warehouse_capacity_distribution": "WAREHOUSE_CAPACITY_DISTRIBUTION",
    "query_warehouse_recent_operations": "WAREHOUSE_RECENT_OPERATIONS",
    "query_warehouse_mixed_storage_facts": "WAREHOUSE_MIXED_STORAGE_FACTS",
    "query_assay_abnormalities": "ASSAY_ABNORMALITY_SUMMARY",
    "query_assay_standard_coverage": "CURRENT_INVENTORY_STANDARD_GAPS",
    "query_pallet_anomalies": "PALLET_ANOMALY_SUMMARY",
    "query_printed_not_inbound_codes": "PRINTED_NOT_INBOUND_QR_CODES",
    "query_qr_batch_inbound_completion": "QR_BATCH_INBOUND_COMPLETION",
    "query_production_label_completion": "PRODUCTION_LABEL_COMPLETION",
    "query_in_process_materials": "IN_PROCESS_MATERIALS",
    "query_stock_documents": "STOCK_DOCUMENTS",
    "query_auto_inbound_batches": "AUTO_INBOUND_BATCH_STATUS",
    "get_auto_inbound_batch_detail": "AUTO_INBOUND_BATCH_STATUS",
    "query_assay_groups": "ASSAY_REFERENCE_DATA",
    "query_quality_standard_catalog": "ASSAY_REFERENCE_DATA",
    "get_quality_standard_detail": "ASSAY_REFERENCE_DATA",
    "query_product_standard_relations": "ASSAY_REFERENCE_DATA",
    "query_product_quality_configuration": "PRODUCT_QUALITY_CONFIGURATION",
    "query_fixed_product_qr_pool": "FIXED_PRODUCT_QR_POOL",
    "query_material_candidates": "MATERIAL_CANDIDATES",
    "query_product_catalog": "PRODUCT_MASTER_DATA",
    "get_product_detail": "PRODUCT_MASTER_DATA",
    "query_screen_mesh_catalog": "SCREEN_MESH_CATALOG",
    "query_employee_roster": "EMPLOYEE_ROSTER",
    "query_roles": "ROLE_PERMISSION_CATALOG",
    "get_role_permission_summary": "ROLE_PERMISSION_CATALOG",
    "search_operation_logs": "BUSINESS_OPERATION_LOGS",
    "query_agent_tool_audit": "AGENT_TOOL_AUDIT",
    "query_agent_answer_reviews": "AGENT_ANSWER_REVIEWS",
    "run_registered_report": "DAILY_PRODUCTION_ANALYSIS",
}

RESOLVER_INTENT_GOALS: dict[tuple[str, str], CoreGoalTypeV1] = {
    ("resolve_products", "inventory_ledger"): "CURRENT_INVENTORY_LEDGER",
    ("resolve_products", "assay_abnormalities"): "ASSAY_ABNORMALITY_SUMMARY",
    ("resolve_products", "assay_standard_coverage"): "CURRENT_INVENTORY_STANDARD_GAPS",
    ("resolve_products", "pallet_anomalies"): "PALLET_ANOMALY_SUMMARY",
    ("resolve_products", "printed_not_inbound_codes"): "PRINTED_NOT_INBOUND_QR_CODES",
    ("resolve_products", "in_process_materials"): "IN_PROCESS_MATERIALS",
    ("resolve_products", "stock_documents"): "STOCK_DOCUMENTS",
    ("resolve_products", "assay_reference_data"): "ASSAY_REFERENCE_DATA",
    ("resolve_products", "product_quality_configuration"): "PRODUCT_QUALITY_CONFIGURATION",
    ("resolve_products", "fixed_product_qr_pool"): "FIXED_PRODUCT_QR_POOL",
    ("resolve_products", "product_detail"): "PRODUCT_MASTER_DATA",
    ("resolve_warehouses", "warehouse_status"): "WAREHOUSE_STATUS",
    ("resolve_warehouses", "warehouse_recent_operations"): "WAREHOUSE_RECENT_OPERATIONS",
    ("resolve_warehouses", "warehouse_mixed_storage_facts"): "WAREHOUSE_MIXED_STORAGE_FACTS",
    ("resolve_warehouses", "warehouse_capacity_distribution"): "WAREHOUSE_CAPACITY_DISTRIBUTION",
    ("resolve_warehouses", "pallet_anomalies"): "PALLET_ANOMALY_SUMMARY",
    ("resolve_warehouses", "stock_documents"): "STOCK_DOCUMENTS",
    ("resolve_production_entities", "production_label_completion"): "PRODUCTION_LABEL_COMPLETION",
    ("resolve_production_entities", "material_candidates"): "MATERIAL_CANDIDATES",
}


def registered_goal_for_plan(
    *,
    tool_name: str | None,
    arguments: dict[str, Any] | None,
    intent: str | None,
    response_mode: str | None = None,
) -> CoreGoalTypeV1 | None:
    arguments = arguments or {}
    if tool_name == "search_approved_knowledge":
        if intent == "knowledge_process" or response_mode == "knowledge_process":
            return "PROCESS_KNOWLEDGE_QUERY"
        if intent == "knowledge_enterprise" or response_mode == "knowledge_enterprise":
            return "ENTERPRISE_KNOWLEDGE_QUERY"
        domains = {
            str(value.value if hasattr(value, "value") else value).upper()
            for value in (arguments.get("knowledgeDomains") or [])
        }
        if domains == {"PROCESS"}:
            return "PROCESS_KNOWLEDGE_QUERY"
        if domains and domains.issubset(
            {"COMPANY", "PRODUCT_MARKETING", "CERTIFICATION", "SALES"}
        ):
            return "ENTERPRISE_KNOWLEDGE_QUERY"
    resolver_goal = RESOLVER_INTENT_GOALS.get((str(tool_name or ""), str(intent or "")))
    if resolver_goal is not None:
        return resolver_goal
    if tool_name == "resolve_products":
        if intent == "inventory_distribution":
            return "PRODUCT_INVENTORY_DISTRIBUTION"
        if intent in {"inventory", "inventory_overview"}:
            return "CURRENT_PRODUCT_INVENTORY"
        if intent == "assay_status":
            return "PRODUCT_ASSAY_REPORT"
        if intent == "assay_records":
            return "PRODUCT_ASSAY_HISTORY"
        if intent == "products_without_recent_assay":
            return "CURRENT_INVENTORY_ASSAY_GAPS"
    if tool_name == "resolve_warehouses" and intent == "inventory_distribution":
        return "WAREHOUSE_INVENTORY_DISTRIBUTION"
    if tool_name == "resolve_warehouses" and intent == "products_without_recent_assay":
        return "CURRENT_INVENTORY_ASSAY_GAPS"
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
    if tool_name == "query_pallet_tasks" and str(arguments.get("status") or "").upper() == "PENDING":
        return "CURRENT_PENDING_TASKS"
    if tool_name == "preview_task_transition":
        definition = TASK_TRANSITION_PREVIEW_BY_TRANSITION.get(str(arguments.get("transition") or ""))
        return definition.goal_type if definition is not None else None
    if tool_name == "preview_finish_inbound_execution":
        return "FINISH_INBOUND_EXECUTION_PREVIEW"
    if tool_name == "resolve_production_entities":
        entity_type = str(arguments.get("entityType") or "").upper()
        if entity_type == "BOILING_BATCH":
            return "BOILING_BATCH_TRACE"
        if intent == "material_pick_trace" or response_mode == "material_pick_trace":
            return "PRODUCTION_MATERIAL_TRACE"
        if intent == "production_output_destinations" or response_mode == "production_output_destinations":
            return "PRODUCTION_OUTPUT_DESTINATIONS"
        if entity_type == "PRODUCTION_ORDER":
            return "PRODUCTION_ORDER_PROGRESS"
    if tool_name == "query_boiling_batch_trace":
        return "BOILING_BATCH_TRACE"
    if tool_name == "query_production_order_progress":
        if intent == "production_output_destinations" or response_mode == "production_output_destinations":
            return "PRODUCTION_OUTPUT_DESTINATIONS"
        return "PRODUCTION_ORDER_PROGRESS"
    if tool_name == "query_material_pick_trace":
        return "PRODUCTION_MATERIAL_TRACE"
    if tool_name == "get_pallet_status":
        return "PALLET_CURRENT_STATUS"
    if tool_name == "query_qr_code_lifecycle":
        return "PALLET_CURRENT_STATUS"
    if tool_name == "query_pallet_flow_records":
        return "PALLET_FLOW_HISTORY"
    if tool_name == "get_assay_status" or tool_name == "get_assay_report_detail":
        return "PRODUCT_ASSAY_REPORT"
    if tool_name == "query_assay_records":
        return "PRODUCT_ASSAY_HISTORY"
    if tool_name == "query_boiling_batches":
        return "BOILING_BATCH_LIST"
    if tool_name == "query_unqualified_inventory":
        return "CURRENT_UNQUALIFIED_INVENTORY"
    if tool_name == "query_inventory_by_quality_standard":
        return "CURRENT_INVENTORY_MATCHING_STANDARD"
    if tool_name == "query_inventory_by_assay_metrics":
        return "CURRENT_INVENTORY_MATCHING_METRIC"
    if tool_name == "query_products_without_recent_assay":
        return "CURRENT_INVENTORY_ASSAY_GAPS"
    if tool_name == "run_registered_report":
        if arguments.get("reportDefinitionId") == "today_operations_overview_v1":
            return "TODAY_OPERATIONS_OVERVIEW"
        if arguments.get("reportDefinitionId") == "inventory_level_trend_v1":
            return "INVENTORY_LEVEL_TREND_ANALYSIS"
        if arguments.get("reportDefinitionId") == "pallet_task_cycle_time_v1":
            return "PROCESS_EFFICIENCY_TREND"
        if arguments.get("reportDefinitionId") == "production_input_output_flow_v1":
            return "PRODUCTION_INPUT_OUTPUT_TREND"
        if arguments.get("reportDefinitionId") == "quality_metric_trend_v1":
            return "QUALITY_METRIC_TREND_ANALYSIS"
        if arguments.get("reportDefinitionId") == "quality_assay_result_trend_v1":
            return "QUALITY_ASSAY_TREND_ANALYSIS"
        if arguments.get("reportDefinitionId") == "daily_production_overview_v1":
            return "DAILY_PRODUCTION_ANALYSIS"
    direct_goal = DIRECT_PRIMARY_TOOL_GOALS.get(str(tool_name or ""))
    if direct_goal is not None:
        return direct_goal
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
    if tool_name not in contract.evidenceTools:
        raise ValueError(f"tool {tool_name} cannot provide evidence for goal {goal_type}")
    fact_type: CoreFactTypeV1 = goal_type
    status, validation_limitations = _fact_status(goal_type, safe_data)
    scope_limitations = _scope_entity_limitations(
        contract=contract,
        arguments=arguments or {},
        entity_contexts=entity_contexts,
    )
    if scope_limitations:
        status = "INVALID"
        validation_limitations.extend(scope_limitations)
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
    rule = GOAL_CONTRACTS[goal_type].factValidation
    if goal_type in {"PROCESS_KNOWLEDGE_QUERY", "ENTERPRISE_KNOWLEDGE_QUERY"}:
        missing = sorted(
            {"status", "corpusVersion", "queryLabel", "knowledgeDomains", "evidence"}.difference(data)
        )
        if missing or not isinstance(data.get("knowledgeDomains"), list) or not isinstance(data.get("evidence"), list):
            return "INVALID", [f"{rule.factLabel}事实缺少字段：{','.join(missing)}。"]
        retrieval_status = str(data.get("status") or "").upper()
        evidence = data.get("evidence") or []
        if retrieval_status == "NO_DATA" and not evidence:
            return "NO_DATA", []
        if retrieval_status in {"SUCCEEDED", "DEGRADED"} and evidence:
            return "AVAILABLE", []
        return "INVALID", ["知识检索状态与证据不一致，不能形成完成事实。"]
    if rule.mode == "STANDARD":
        shapes = [
            FactValidationShapeV1(
                requiredFields=rule.requiredFields,
                listFields=rule.listFields,
                noDataWhenEmptyList=rule.noDataWhenEmptyList,
                noDataBooleanField=rule.noDataBooleanField,
                noDataBooleanValue=rule.noDataBooleanValue,
            ),
            *rule.alternativeShapes,
        ]
        shape_failures: list[list[str]] = []
        for shape in shapes:
            missing = sorted(set(shape.requiredFields).difference(data))
            invalid_lists = [field for field in shape.listFields if not isinstance(data.get(field), list)]
            if missing or invalid_lists:
                shape_failures.append(_unique([*missing, *invalid_lists]))
                continue
            if (
                shape.noDataBooleanField is not None
                and data.get(shape.noDataBooleanField) is shape.noDataBooleanValue
            ):
                return "NO_DATA", []
            if shape.noDataWhenEmptyList is not None and not data.get(shape.noDataWhenEmptyList):
                return "NO_DATA", []
            return "AVAILABLE", []
        detail = " 或 ".join(",".join(fields) for fields in shape_failures)
        return "INVALID", [f"{rule.factLabel}事实缺少字段：{detail}。"]

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
    for key in ("status", "taskType", "productStatus"):
        value = arguments.get(key)
        if isinstance(value, str):
            scope[key] = value
    date_range = _mapping(arguments.get("dateRange"))
    for key in ("type", "date", "days", "from", "to"):
        value = date_range.get(key)
        if value is not None:
            scope[f"dateRange.{key}"] = str(value)
    for key in (
        "startDate",
        "endDate",
        "standardCode",
        "standardVersion",
        "reportDefinitionId",
        "reportVersion",
        "productQuery",
    ):
        value = arguments.get(key)
        if value is not None:
            scope[key] = str(value)
    metric_condition = _mapping(arguments.get("metricCondition"))
    for key in ("metricCode", "operator", "value", "minValue", "maxValue"):
        value = metric_condition.get(key)
        if value is not None:
            scope[f"metricCondition.{key}"] = str(value)
    return scope


def _scope_entity_limitations(
    *,
    contract: GoalContractV1,
    arguments: dict[str, Any],
    entity_contexts: dict[str, EntityContextV1],
) -> list[str]:
    limitations: list[str] = []
    if "PRODUCT_SCOPE" in contract.scopeEntityBindings:
        product_scope = _mapping(arguments.get("productScope"))
        if product_scope.get("type") == "SINGLE_PRODUCT":
            context = entity_contexts.get("PRODUCT")
            product_id = product_scope.get("productId")
            if not _resolved_entity(context):
                limitations.append("单产品查询范围缺少已确认产品实体。")
            elif product_id is not None and context.internal_id != product_id:
                limitations.append("单产品查询范围与当前已确认产品不一致。")
    if "WAREHOUSE_SCOPE" in contract.scopeEntityBindings:
        warehouse_scope = _mapping(arguments.get("warehouseScope"))
        if warehouse_scope.get("type") == "SINGLE_WAREHOUSE":
            context = entity_contexts.get("WAREHOUSE")
            warehouse_id = warehouse_scope.get("warehouseId")
            if not _resolved_entity(context):
                limitations.append("单库位查询范围缺少已确认库位实体。")
            elif warehouse_id is not None and context.internal_id != warehouse_id:
                limitations.append("单库位查询范围与当前已确认库位不一致。")
    return limitations


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
