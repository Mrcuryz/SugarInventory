from __future__ import annotations

import json
from typing import get_args

import pytest

from app.agents import AgentHandoffRouter
from app.goal_contracts import (
    GOAL_CONTRACTS,
    CoreFactTypeV1,
    CoreGoalTypeV1,
    registered_goal_for_plan,
)
from app.graph.state import InMemoryCheckpointer
from app.runtime import WarehouseAgentRuntime
from app.tool_arguments import TOOL_SCHEMAS, ToolArgumentBuilder
from app.tools.client import MockToolClient


TOOL_NAME = "preview_finish_inbound_execution"
GOAL_TYPE = "FINISH_INBOUND_EXECUTION_PREVIEW"


def test_exact_preview_has_one_closed_goal_expert_and_tool_contract() -> None:
    schema = TOOL_SCHEMAS[TOOL_NAME]
    item_schema = schema["properties"]["items"]["items"]

    assert GOAL_TYPE in set(get_args(CoreGoalTypeV1))
    assert GOAL_TYPE in set(get_args(CoreFactTypeV1))
    contract = GOAL_CONTRACTS[GOAL_TYPE]
    assert contract.ownerExpert == "logistics_expert"
    assert contract.requiredFactTypes == (GOAL_TYPE,)
    assert contract.evidenceTools == (TOOL_NAME,)
    assert TOOL_NAME in AgentHandoffRouter().profile("logistics_expert").allowed_tools
    assert schema["additionalProperties"] is False
    assert schema["properties"]["items"]["maxItems"] == 20
    assert item_schema["additionalProperties"] is False
    assert set(item_schema["properties"]) == {
        "code",
        "warehouseName",
        "entryDate",
        "side",
        "quantity",
        "unit",
        "remark",
    }
    assert not {
        "taskId",
        "palletCodeId",
        "productId",
        "warehouseId",
        "rowNumber",
        "layer",
    } & set(item_schema["properties"])


def test_visible_form_payload_routes_through_main_agent_and_logistics_expert() -> None:
    message = """请为已填写的成品入库表单生成精确执行预览（只预览，不执行）。
```json
{"previewVersion":1,"items":[{"code":"BT0019N1","warehouseName":"1号库位","entryDate":"2026-08-09","side":"左","quantity":1,"unit":"0","remark":"验收预览"}]}
```"""

    decision = ToolArgumentBuilder().plan(
        user_message=message,
        state=InMemoryCheckpointer().get("agt-finish-inbound-preview"),
    )

    assert decision.action == "call_tool"
    assert decision.toolName == TOOL_NAME
    assert decision.intent == "finish_inbound_execution_preview"
    assert decision.arguments == {
        "previewVersion": 1,
        "items": [
            {
                "code": "BT0019N1",
                "warehouseName": "1号库位",
                "entryDate": "2026-08-09",
                "side": "左",
                "quantity": 1,
                "unit": "0",
                "remark": "验收预览",
            }
        ],
    }
    assert decision.routeSnapshot["agent_handoff"]["target_agent"] == "logistics_expert"
    assert registered_goal_for_plan(
        tool_name=decision.toolName,
        arguments=decision.arguments,
        intent=decision.intent,
    ) == GOAL_TYPE


def test_argument_validation_rejects_internal_ids_and_layout_coordinates() -> None:
    builder = ToolArgumentBuilder()

    with pytest.raises(ValueError, match="unsupported finish inbound preview field"):
        builder._validate(
            TOOL_NAME,
            {
                "previewVersion": 1,
                "items": [
                    {
                        "code": "BT0019N1",
                        "warehouseName": "1号库位",
                        "rowNumber": 1,
                        "layer": 1,
                        "taskId": 21,
                    }
                ],
            },
        )


def test_safe_adapter_and_card_never_expose_preview_refs_or_server_state() -> None:
    runtime = WarehouseAgentRuntime(
        tool_client=MockToolClient(),
        checkpointer=InMemoryCheckpointer(),
    )
    adapted = runtime._adapt_finish_inbound_execution_preview(
        {
            "dataScope": "FINISH_INBOUND_EXECUTION_PREVIEW",
            "previewVersion": 1,
            "previewStatus": "READY",
            "previewRef": "fip1_internal-reference",
            "stateDigest": "a" * 64,
            "previewedAt": "2026-08-09T09:00:00",
            "expiresAt": "2026-08-09T09:05:00",
            "readyForUserConfirmation": True,
            "requestedItemCount": 1,
            "eligibleItemCount": 1,
            "items": [
                {
                    "palletCode": "BT0019N1",
                    "productName": "黄冰糖（袋）",
                    "warehouseName": "1号库位",
                    "entryDate": "2026-08-09",
                    "side": "左",
                    "quantity": 1,
                    "unitLabel": "板",
                    "taskId": 21,
                    "warehouseId": 41,
                    "rowNumber": 1,
                    "layer": 1,
                }
            ],
            "serverSnapshot": {"task": {"id": 21}},
            "blockingIssues": [],
            "warnings": [],
            "limitations": ["本预览不执行入库。"],
        }
    )
    card = runtime._finish_inbound_execution_preview_card(adapted)
    serialized = json.dumps(
        {
            "safe": adapted.model_dump(exclude_none=True),
            "card": card.model_dump(exclude_none=True),
        },
        ensure_ascii=False,
    )

    assert card.cardType == "finish_inbound_execution_preview"
    assert "BT0019N1" in serialized
    assert "1号库位" in serialized
    assert "fip1_internal-reference" not in serialized
    assert "stateDigest" not in serialized
    assert "serverSnapshot" not in serialized
    assert "taskId" not in serialized
    assert "warehouseId" not in serialized
    assert "rowNumber" not in serialized
    assert "layer" not in serialized
