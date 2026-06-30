from __future__ import annotations

from dataclasses import dataclass, field
from typing import Any


@dataclass
class SelectedEntity:
    internal_id: int
    display_label: str
    source: str
    metadata: dict[str, Any] = field(default_factory=dict)


@dataclass
class PendingClarification:
    kind: str
    intent: str
    prompt: str
    options: list[dict[str, Any]]


@dataclass
class WarehouseAgentState:
    messages: list[dict[str, Any]] = field(default_factory=list)
    selected_product: SelectedEntity | None = None
    selected_warehouse: SelectedEntity | None = None
    last_inventory_result: dict[str, Any] | None = None
    last_warehouse_result: dict[str, Any] | None = None
    last_assay_result: dict[str, Any] | None = None
    last_pallet_result: dict[str, Any] | None = None
    pending_clarification: PendingClarification | None = None
    tool_results: list[dict[str, Any]] = field(default_factory=list)


class InMemoryCheckpointer:
    """Small LangGraph-compatible thread state store for M1.3R-1c."""

    def __init__(self) -> None:
        self._states: dict[str, WarehouseAgentState] = {}

    def get(self, thread_id: str) -> WarehouseAgentState:
        if thread_id not in self._states:
            self._states[thread_id] = WarehouseAgentState()
        return self._states[thread_id]

    def save(self, thread_id: str, state: WarehouseAgentState) -> None:
        self._states[thread_id] = state

    def clear(self, thread_id: str) -> None:
        self._states.pop(thread_id, None)

