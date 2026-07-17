from __future__ import annotations

from app.state_models import SelectedEntity, WarehouseAgentState
from app.state_store import deserialize_state, serialize_state


def test_selected_pallet_survives_state_json_round_trip() -> None:
    state = WarehouseAgentState(
        selected_pallet=SelectedEntity(
            internal_id=None,
            display_label="P202607090001",
            source="tool_result",
            metadata={"code": "P202607090001"},
            entity_type="PALLET",
            entity_ref="CURRENT_PALLET",
            canonical_name="P202607090001",
        )
    )

    restored = deserialize_state(serialize_state(state))

    assert restored.selected_pallet is not None
    assert restored.selected_pallet.entity_ref == "CURRENT_PALLET"
    assert restored.selected_pallet.metadata["code"] == "P202607090001"
