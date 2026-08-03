from __future__ import annotations

from dataclasses import dataclass, field
from datetime import datetime
from typing import Any


@dataclass
class EntityContextV1:
    internal_id: int | None
    display_label: str
    source: str
    metadata: dict[str, Any] = field(default_factory=dict)
    schema_version: str = "1.0"
    entity_type: str | None = None
    entity_ref: str | None = None
    canonical_name: str | None = None
    resolution_status: str = "UNIQUE"
    resolved_by: str | None = None
    resolved_at: datetime | None = None
    expires_at: datetime | None = None
    scope_hash: str | None = None
    attributes: dict[str, Any] = field(default_factory=dict)

    def __post_init__(self) -> None:
        if not self.attributes and self.metadata:
            self.attributes = dict(self.metadata)
        if self.resolved_by is None:
            self.resolved_by = self.source.upper()


# Backward-compatible import name. EntityContextV1 is the versioned evolution of
# the previous SelectedEntity state object, not a second parallel entity store.
SelectedEntity = EntityContextV1


@dataclass
class PendingClarification:
    kind: str
    intent: str
    prompt: str
    options: list[dict[str, Any]]
    interrupt_id: str
    resume_token_hash: str
    resume_token: str = field(repr=False)
    expires_at: datetime
    expert_agent: str = "main_agent"
    allowed_tools: tuple[str, ...] = ()
    business_domain: str | None = None
    continuation: dict[str, Any] | None = None
    user_id: int | None = None
    status: str = "PENDING"


@dataclass
class WarehouseAgentState:
    state_version: int = 0
    messages: list[dict[str, Any]] = field(default_factory=list)
    active_agent: str = "main_agent"
    last_agent_handoff: dict[str, Any] | None = None
    active_run: dict[str, Any] | None = None
    immutable_execution_context: dict[str, Any] | None = None
    orchestration_plan: dict[str, Any] | None = None
    orchestration_step_results: dict[str, dict[str, Any]] = field(default_factory=dict)
    selected_product: SelectedEntity | None = None
    selected_warehouse: SelectedEntity | None = None
    selected_production_order: SelectedEntity | None = None
    selected_boiling_batch: SelectedEntity | None = None
    selected_pallet: SelectedEntity | None = None
    active_goal_type: str | None = None
    fact_envelopes: list[dict[str, Any]] = field(default_factory=list)
    last_goal_completion: dict[str, Any] | None = None
    last_inventory_result: dict[str, Any] | None = None
    last_inventory_distribution: dict[str, Any] | None = None
    last_warehouse_result: dict[str, Any] | None = None
    last_assay_result: dict[str, Any] | None = None
    last_assay_records: dict[str, Any] | None = None
    last_inventory_quality: dict[str, Any] | None = None
    last_assay_report_detail: dict[str, Any] | None = None
    last_assay_abnormalities: dict[str, Any] | None = None
    last_products_without_recent_assay: dict[str, Any] | None = None
    last_assay_standard_coverage: dict[str, Any] | None = None
    last_qr_code_lifecycle: dict[str, Any] | None = None
    last_printed_not_inbound_codes: dict[str, Any] | None = None
    last_pallet_anomalies: dict[str, Any] | None = None
    last_pallet_flow_records: dict[str, Any] | None = None
    last_qr_batch_inbound_completion: dict[str, Any] | None = None
    last_pallet_result: dict[str, Any] | None = None
    last_pallet_tasks: dict[str, Any] | None = None
    last_pallet_task_filters: dict[str, Any] | None = None
    last_production_order_progress: dict[str, Any] | None = None
    last_production_material_trace: dict[str, Any] | None = None
    last_boiling_batch_trace: dict[str, Any] | None = None
    last_report_context: dict[str, Any] | None = None
    last_knowledge_result: dict[str, Any] | None = None
    last_auto_inbound_batches: dict[str, Any] | None = None
    last_orchestration: dict[str, Any] | None = None
    pending_clarification: PendingClarification | None = None
    interrupt_status: dict[str, str] = field(default_factory=dict)
    interrupt_client_results: dict[str, dict[str, dict[str, Any]]] = field(default_factory=dict)
    tool_results: list[dict[str, Any]] = field(default_factory=list)
