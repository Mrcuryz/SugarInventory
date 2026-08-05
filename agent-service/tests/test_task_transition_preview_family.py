from __future__ import annotations

from pathlib import Path
from typing import get_args

from app.goal_contracts import (
    GOAL_CONTRACTS,
    CoreFactTypeV1,
    CoreGoalTypeV1,
    registered_goal_for_plan,
)
from app.task_transition_previews import (
    TASK_TRANSITION_PREVIEW_BY_GOAL,
    TASK_TRANSITION_PREVIEW_BY_INTENT,
    TASK_TRANSITION_PREVIEW_BY_TRANSITION,
    TASK_TRANSITION_PREVIEW_DEFINITIONS,
)
from app.tool_arguments import LLM_TOOL_DESCRIPTIONS, TOOL_SCHEMAS


def test_task_transition_preview_family_has_one_closed_definition_per_business_flow() -> None:
    assert len(TASK_TRANSITION_PREVIEW_DEFINITIONS) == 3
    assert set(TASK_TRANSITION_PREVIEW_BY_GOAL) == {
        "FINISH_INBOUND_TASK_TRANSITION_PREVIEW",
        "FINISH_OUTBOUND_TASK_TRANSITION_PREVIEW",
        "TRANSFER_TASK_TRANSITION_PREVIEW",
    }
    assert set(TASK_TRANSITION_PREVIEW_BY_INTENT) == {
        "finish_inbound_task_transition_preview",
        "finish_outbound_task_transition_preview",
        "transfer_task_transition_preview",
    }
    assert set(TASK_TRANSITION_PREVIEW_BY_TRANSITION) == {
        "CONFIRM_FINISH_INBOUND",
        "CONFIRM_FINISH_OUTBOUND",
        "CONFIRM_TRANSFER",
    }


def test_task_transition_preview_family_matches_goal_and_tool_contracts() -> None:
    goal_types = set(get_args(CoreGoalTypeV1))
    fact_types = set(get_args(CoreFactTypeV1))
    schema_transitions = set(
        TOOL_SCHEMAS["preview_task_transition"]["properties"]["transition"]["enum"]
    )

    assert schema_transitions == set(TASK_TRANSITION_PREVIEW_BY_TRANSITION)
    for definition in TASK_TRANSITION_PREVIEW_DEFINITIONS:
        assert definition.goal_type in goal_types
        assert definition.goal_type in fact_types
        contract = GOAL_CONTRACTS[definition.goal_type]
        assert contract.ownerExpert == "logistics_expert"
        assert contract.requiredFactTypes == (definition.goal_type,)
        assert contract.evidenceTools == ("preview_task_transition",)
        assert registered_goal_for_plan(
            tool_name="preview_task_transition",
            arguments={
                "previewVersion": 1,
                "transition": definition.transition,
                "palletCodes": ["BT001TEST"],
            },
            intent=definition.intent_subtype,
        ) == definition.goal_type
        assert definition.transition in LLM_TOOL_DESCRIPTIONS["preview_task_transition"]


def test_unknown_task_transition_fails_closed_instead_of_falling_back_to_inbound() -> None:
    assert registered_goal_for_plan(
        tool_name="preview_task_transition",
        arguments={
            "previewVersion": 1,
            "transition": "CONFIRM_UNKNOWN",
            "palletCodes": ["BT001TEST"],
        },
        intent="unknown_task_transition_preview",
    ) is None


def test_task_transition_preview_family_is_registered_across_repository_layers() -> None:
    repository = Path(__file__).resolve().parents[2]
    readonly_goal_registry = (
        repository / "docs/agent/readonly-goal-contract-registry.yaml"
    ).read_text(encoding="utf-8")
    tool_capability_registry = (
        repository / "docs/agent/tool-capability-registry.yaml"
    ).read_text(encoding="utf-8")
    java_tool_configuration = (
        repository
        / "warehouse-mcp/src/main/java/com/Laibin/SugarInventory/mcp/config/ToolConfiguration.java"
    ).read_text(encoding="utf-8")
    java_preview_service = (
        repository
        / "src/main/java/com/Laibin/SugarInventory/service/impl/LogisticsAgentReadServiceImpl.java"
    ).read_text(encoding="utf-8")
    frontend_dialog = (
        repository / "webpage/src/components/agent/PalletTaskBatchDialog.vue"
    ).read_text(encoding="utf-8")

    for definition in TASK_TRANSITION_PREVIEW_DEFINITIONS:
        assert f"  {definition.goal_type}:" in readonly_goal_registry
        assert f"CURRENT_{definition.goal_type}" in tool_capability_registry
        assert definition.transition in java_tool_configuration
        assert definition.transition in java_preview_service
        assert definition.batch_action in frontend_dialog
