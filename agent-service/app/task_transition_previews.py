from __future__ import annotations

from dataclasses import dataclass


@dataclass(frozen=True, slots=True)
class TaskTransitionPreviewDefinition:
    """One explicitly supported no-write task transition preview."""

    goal_type: str
    intent_subtype: str
    transition: str
    task_group_label: str
    batch_action: str
    transition_label: str


TASK_TRANSITION_PREVIEW_DEFINITIONS: tuple[TaskTransitionPreviewDefinition, ...] = (
    TaskTransitionPreviewDefinition(
        goal_type="FINISH_INBOUND_TASK_TRANSITION_PREVIEW",
        intent_subtype="finish_inbound_task_transition_preview",
        transition="CONFIRM_FINISH_INBOUND",
        task_group_label="成品入库",
        batch_action="confirmIn",
        transition_label="确认成品入库",
    ),
    TaskTransitionPreviewDefinition(
        goal_type="FINISH_OUTBOUND_TASK_TRANSITION_PREVIEW",
        intent_subtype="finish_outbound_task_transition_preview",
        transition="CONFIRM_FINISH_OUTBOUND",
        task_group_label="成品出库",
        batch_action="finishOutConfirm",
        transition_label="确认成品出库",
    ),
    TaskTransitionPreviewDefinition(
        goal_type="TRANSFER_TASK_TRANSITION_PREVIEW",
        intent_subtype="transfer_task_transition_preview",
        transition="CONFIRM_TRANSFER",
        task_group_label="调拨",
        batch_action="transferConfirm",
        transition_label="确认调拨",
    ),
)


TASK_TRANSITION_PREVIEW_BY_GOAL = {
    definition.goal_type: definition
    for definition in TASK_TRANSITION_PREVIEW_DEFINITIONS
}
TASK_TRANSITION_PREVIEW_BY_INTENT = {
    definition.intent_subtype: definition
    for definition in TASK_TRANSITION_PREVIEW_DEFINITIONS
}
TASK_TRANSITION_PREVIEW_BY_TRANSITION = {
    definition.transition: definition
    for definition in TASK_TRANSITION_PREVIEW_DEFINITIONS
}
TASK_TRANSITION_PREVIEW_TRANSITIONS = tuple(TASK_TRANSITION_PREVIEW_BY_TRANSITION)


if not (
    len(TASK_TRANSITION_PREVIEW_DEFINITIONS)
    == len(TASK_TRANSITION_PREVIEW_BY_GOAL)
    == len(TASK_TRANSITION_PREVIEW_BY_INTENT)
    == len(TASK_TRANSITION_PREVIEW_BY_TRANSITION)
):
    raise RuntimeError("task transition preview definitions must be unique")
