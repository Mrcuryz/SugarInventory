from __future__ import annotations

from collections.abc import Callable
from contextvars import ContextVar, Token


ProgressReporter = Callable[[str, str], None]


_BUSINESS_PROGRESS_TEXT = {
    "routing": "正在判断由哪个业务模块处理。",
    "expert_planning": "正在确认查询条件和下一步。",
    "resolving_product": "正在确认产品范围。",
    "resolving_warehouse": "正在确认库位范围。",
    "inventory_overview": "正在查询库存总览。",
    "inventory_distribution": "正在汇总库存分布。",
    "inventory_query": "正在查询库存数据。",
    "warehouse_query": "正在查询库位数据。",
    "assay_query": "正在查询化验与质量数据。",
    "pallet_query": "正在查询托盘与二维码流转。",
    "production_query": "正在查询生产与领料数据。",
    "logistics_query": "正在查询任务与单据数据。",
    "master_data_query": "正在查询产品与主数据配置。",
    "administration_query": "正在查询员工与权限摘要。",
    "audit_query": "正在查询审计摘要。",
    "business_query": "正在查询业务数据。",
    "analyzing": "正在分析已返回的业务数据。",
}

_TOOL_PROGRESS_STAGE = {
    "resolve_products": "resolving_product",
    "resolve_warehouses": "resolving_warehouse",
    "get_inventory_overview": "inventory_overview",
    "get_inventory_distribution": "inventory_distribution",
    "query_inventory_ledger": "inventory_query",
    "query_prepare_pool_balance": "inventory_query",
    "get_warehouse_status": "warehouse_query",
    "query_warehouse_capacity_distribution": "warehouse_query",
    "query_warehouse_recent_operations": "warehouse_query",
    "query_warehouse_mixed_storage_facts": "warehouse_query",
    "get_assay_status": "assay_query",
    "query_assay_records": "assay_query",
    "get_assay_report_detail": "assay_query",
    "query_assay_abnormalities": "assay_query",
    "query_products_without_recent_assay": "assay_query",
    "query_assay_standard_coverage": "assay_query",
    "query_unqualified_inventory": "assay_query",
    "query_inventory_by_quality_standard": "assay_query",
    "query_inventory_by_assay_metrics": "assay_query",
    "query_assay_groups": "assay_query",
    "query_quality_standard_catalog": "assay_query",
    "get_quality_standard_detail": "assay_query",
    "query_product_standard_relations": "assay_query",
    "query_product_quality_configuration": "assay_query",
    "get_pallet_status": "pallet_query",
    "query_qr_code_lifecycle": "pallet_query",
    "query_printed_not_inbound_codes": "pallet_query",
    "query_pallet_anomalies": "pallet_query",
    "query_pallet_flow_records": "pallet_query",
    "query_qr_batch_inbound_completion": "pallet_query",
    "query_fixed_product_qr_pool": "pallet_query",
    "resolve_production_entities": "production_query",
    "query_boiling_batches": "production_query",
    "query_production_order_progress": "production_query",
    "query_boiling_batch_trace": "production_query",
    "query_material_pick_trace": "production_query",
    "query_production_label_completion": "production_query",
    "query_in_process_materials": "production_query",
    "query_material_candidates": "production_query",
    "query_pallet_tasks": "logistics_query",
    "query_stock_documents": "logistics_query",
    "query_auto_inbound_batches": "logistics_query",
    "get_auto_inbound_batch_detail": "logistics_query",
    "query_product_catalog": "master_data_query",
    "get_product_detail": "master_data_query",
    "query_screen_mesh_catalog": "master_data_query",
    "query_employee_roster": "administration_query",
    "query_roles": "administration_query",
    "get_role_permission_summary": "administration_query",
    "search_operation_logs": "audit_query",
    "query_agent_tool_audit": "audit_query",
    "query_agent_answer_reviews": "audit_query",
}

_CURRENT_REPORTER: ContextVar[ProgressReporter | None] = ContextVar(
    "agent_business_progress_reporter",
    default=None,
)


def set_business_progress_reporter(reporter: ProgressReporter | None) -> Token:
    return _CURRENT_REPORTER.set(reporter)


def reset_business_progress_reporter(token: Token) -> None:
    _CURRENT_REPORTER.reset(token)


def report_business_progress(stage: str) -> None:
    reporter = _CURRENT_REPORTER.get()
    text = _BUSINESS_PROGRESS_TEXT.get(stage)
    if reporter is not None and text is not None:
        reporter(stage, text)


def report_tool_progress(tool_name: str) -> None:
    report_business_progress(_TOOL_PROGRESS_STAGE.get(tool_name, "business_query"))


def registered_progress_tools() -> frozenset[str]:
    return frozenset(_TOOL_PROGRESS_STAGE)
