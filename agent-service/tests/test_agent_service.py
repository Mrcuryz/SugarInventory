from __future__ import annotations

import json
import threading
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from typing import Any

import pytest
from fastapi.testclient import TestClient

from app.config import Settings
from app.context import ContextBuilder
from app.graph.state import InMemoryCheckpointer
from app.main import create_app
from app.tool_arguments import ToolArgumentBuilder
from app.tools.client import JavaGatewayToolClient, MockToolClient, ToolGatewayError


def chat_payload(message: str, agent_session_id: str = "agt_test") -> dict[str, Any]:
    return {
        "agentSessionId": agent_session_id,
        "message": {"type": "user_message", "content": message},
        "client": {"traceId": "trace_001", "requestId": "req_001"},
    }




def test_default_gateway_timeout_is_15_seconds(monkeypatch: pytest.MonkeyPatch) -> None:
    monkeypatch.delenv("REQUEST_TIMEOUT_MS", raising=False)

    assert Settings.from_env().request_timeout_ms == 15000
def test_health() -> None:
    app = create_app(
        Settings(tool_mode="mock"),
        tool_client=MockToolClient(),
        checkpointer=InMemoryCheckpointer(),
    )
    response = TestClient(app).get("/internal/agent/health")

    assert response.status_code == 200
    body = response.json()
    assert body["status"] == "UP"
    assert body["dependencies"]["memory"] == "UP"


def test_resolve_products_ambiguous_returns_business_candidates_without_internal_fields() -> None:
    tool_client = MockToolClient(
        {
            "resolve_products": {
                "resolutionStatus": "AMBIGUOUS",
                "clarificationPrompt": "“黄冰糖”有多个规格，请选择查询范围。",
                "options": [
                    {
                        "optionType": "PRODUCT_TYPE_GROUP",
                        "displayLabel": "全部黄冰糖大类",
                        "supported": False,
                        "disabledReason": "当前暂不支持大类聚合",
                    },
                    {
                        "optionType": "SINGLE_PRODUCT",
                        "displayLabel": "黄冰糖（袋） (#84) 25.0kg/件 40件/板",
                        "productId": 84,
                        "weightPerPiece": 25.0,
                        "piecesPerPallet": 40,
                        "supported": True,
                    },
                ],
            }
        }
    )
    app = create_app(Settings(tool_mode="mock"), tool_client=tool_client, checkpointer=InMemoryCheckpointer())
    response = TestClient(app).post("/internal/agent/chat", json=chat_payload("帮我查黄冰糖当前库存"))

    assert response.status_code == 200
    body = response.json()
    assert body["needsUserSelection"] is True
    assert body["cards"][0]["options"][1]["displayLabel"] == "黄冰糖（袋） 25.0kg/件 40件/板"
    assert "productId" not in json.dumps(body, ensure_ascii=False)
    assert "toolName" not in json.dumps(body, ensure_ascii=False)
    assert "SUCCESS" not in json.dumps(body, ensure_ascii=False)


def test_candidate_selected_writes_state_and_queries_inventory() -> None:
    tool_client = MockToolClient(
        {
            "resolve_products": {
                "resolutionStatus": "AMBIGUOUS",
                "options": [
                    {"optionType": "SINGLE_PRODUCT", "displayLabel": "黄冰糖（袋）", "productId": 84}
                ],
            },
            "get_inventory_overview": {
                "displayStockInfo": "11板30件",
                "totalEquivalentPieces": 470,
                "totalWeight": 11750.0,
            },
        }
    )
    checkpointer = InMemoryCheckpointer()
    app = create_app(Settings(tool_mode="mock"), tool_client=tool_client, checkpointer=checkpointer)
    client = TestClient(app)

    client.post("/internal/agent/chat", json=chat_payload("查黄冰糖库存"))
    response = client.post(
        "/internal/agent/resume",
        json={
            "agentSessionId": "agt_test",
            "event": {
                "type": "candidate_selected",
                "selection": {"optionId": "opt_001", "displayLabel": "黄冰糖（袋）"},
            },
        },
    )

    assert response.status_code == 200
    body = response.json()
    assert body["answer"] == "黄冰糖（袋）当前库存为 11板30件，折合 470 件，总重量 11750.0kg。"
    state = checkpointer.get("agt_test")
    assert state.selected_product is not None
    assert state.selected_product.internal_id == 84
    assert tool_client.calls[-1]["toolName"] == "get_inventory_overview"
    assert tool_client.calls[-1]["arguments"] == {"productId": 84}
    assert "productId" not in json.dumps(body, ensure_ascii=False)


def test_java_gateway_client_can_call_real_gateway_endpoint() -> None:
    server = FakeGatewayServer(
        response={
            "toolName": "get_inventory_overview",
            "toolCallId": "tool_001",
            "status": "SUCCESS",
            "result": {"displayStockInfo": "11板30件", "totalEquivalentPieces": 470},
            "error": None,
            "auditRef": "audit_001",
        }
    )
    server.start()
    try:
        client = JavaGatewayToolClient(
            Settings(
                tool_mode="java_gateway",
                java_tool_gateway_base_url=server.base_url,
                agent_internal_tool_service_key="test-key",
                request_timeout_ms=2000,
            )
        )
        result = client.call_tool(
            agent_session_id="agt_test",
            tool_name="get_inventory_overview",
            arguments={"productId": 84},
            trace_id="trace_001",
            request_id="req_001",
        )
    finally:
        server.stop()

    assert result["displayStockInfo"] == "11板30件"
    assert server.last_path == "/internal/agent/tools/get_inventory_overview"
    assert server.last_headers.get("X-Agent-Service-Key") == "test-key"
    assert server.last_body["agentSessionId"] == "agt_test"


def test_followup_uses_selected_product_context() -> None:
    tool_client = MockToolClient(
        {
            "resolve_products": {
                "resolutionStatus": "AMBIGUOUS",
                "options": [
                    {"optionType": "SINGLE_PRODUCT", "displayLabel": "黄冰糖（袋）", "productId": 84}
                ],
            },
            "get_inventory_overview": {
                "displayStockInfo": "11板30件",
                "totalEquivalentPieces": 470,
                "totalWeight": 11750.0,
            },
        }
    )
    app = create_app(Settings(tool_mode="mock"), tool_client=tool_client, checkpointer=InMemoryCheckpointer())
    client = TestClient(app)

    client.post("/internal/agent/chat", json=chat_payload("查黄冰糖库存"))
    client.post(
        "/internal/agent/resume",
        json={
            "agentSessionId": "agt_test",
            "event": {
                "type": "candidate_selected",
                "selection": {"optionId": "opt_001", "displayLabel": "黄冰糖（袋）"},
            },
        },
    )
    response = client.post("/internal/agent/chat", json=chat_payload("这些主要存放在哪些库位？"))

    assert response.status_code == 200
    body = response.json()
    assert body["needsUserSelection"] is False
    assert "还不能准确返回按库位分布" in body["answer"]
    assert "当前库存为" not in body["answer"]
    assert tool_client.calls[-1]["toolName"] == "get_inventory_overview"
    assert tool_client.calls[-1]["arguments"] == {"productId": 84}


def test_context_followup_without_state_requires_clarification() -> None:
    app = create_app(
        Settings(tool_mode="mock"),
        tool_client=MockToolClient(),
        checkpointer=InMemoryCheckpointer(),
    )
    response = TestClient(app).post("/internal/agent/chat", json=chat_payload("这些主要存放在哪些库位？"))

    assert response.status_code == 200
    body = response.json()
    assert body["needsUserSelection"] is True
    assert "哪个产品" in body["answer"]


def test_401_403_timeout_are_not_interpreted_as_empty_data() -> None:
    for error in [
        ToolGatewayError("UPSTREAM_UNAUTHORIZED", "当前查询认证失败。"),
        ToolGatewayError("UPSTREAM_PERMISSION_DENIED", "当前用户没有执行该只读查询的权限。"),
        ToolGatewayError("UPSTREAM_TIMEOUT", "查询仓储数据超时。", True),
    ]:
        app = create_app(
            Settings(tool_mode="mock"),
            tool_client=MockToolClient({"resolve_products": error}),
            checkpointer=InMemoryCheckpointer(),
        )
        response = TestClient(app).post("/internal/agent/chat", json=chat_payload("查黄冰糖库存"))

        assert response.status_code == 200
        body = response.json()
        assert body["error"]["code"] == error.code
        assert "没有库存" not in body["answer"]
        assert "无数据" not in body["answer"]


def test_normal_response_hides_sensitive_and_internal_fields() -> None:
    tool_client = MockToolClient(
        {
            "resolve_products": {
                "resolutionStatus": "UNIQUE",
                "candidates": [{"productId": 84, "displayLabel": "黄冰糖（袋）"}],
            },
            "get_inventory_overview": {
                "displayStockInfo": "11板30件",
                "totalEquivalentPieces": 470,
                "totalWeight": 11750.0,
                "Authorization": "Bearer secret",
                "token": "hidden",
                "stackTrace": "java.lang.Exception at Server.java:1",
            },
        }
    )
    app = create_app(Settings(tool_mode="mock"), tool_client=tool_client, checkpointer=InMemoryCheckpointer())
    response = TestClient(app).post("/internal/agent/chat", json=chat_payload("查黄冰糖库存"))

    body_text = json.dumps(response.json(), ensure_ascii=False)
    assert "Authorization" not in body_text
    assert "Bearer" not in body_text
    assert "token" not in body_text
    assert "toolName" not in body_text
    assert "SUCCESS" not in body_text
    assert "productId" not in body_text
    assert "warehouseId" not in body_text
    assert "stackTrace" not in body_text


def test_stream_endpoint_returns_basic_sse() -> None:
    tool_client = MockToolClient(
        {
            "resolve_products": {
                "resolutionStatus": "NOT_FOUND",
            }
        }
    )
    app = create_app(Settings(tool_mode="mock"), tool_client=tool_client, checkpointer=InMemoryCheckpointer())
    response = TestClient(app).post("/internal/agent/chat/stream", json=chat_payload("查黄冰糖库存"))

    assert response.status_code == 200
    text = response.text
    assert "event: message_start" in text
    assert "event: text_delta" in text
    assert "event: message_end" in text
    assert "SUCCESS" not in text
    assert "toolName" not in text
    assert "productId" not in text
    assert "warehouseId" not in text




def test_context_builder_adds_warehouse_domain_pack() -> None:
    packs = ContextBuilder().build("2号库位现在还有多少容量？", InMemoryCheckpointer().get("agt_test"))

    assert [pack.name for pack in packs] == ["warehouse_naming"]
    instructions = "\n".join(packs[0].instructions)
    assert "warehouseName" in instructions
    assert "resolve_warehouses" in instructions
    assert "不要猜 warehouseId" in instructions


def test_tool_argument_builder_generates_warehouse_query_phrase() -> None:
    state = InMemoryCheckpointer().get("agt_test")
    arguments = ToolArgumentBuilder().build(
        tool_name="resolve_warehouses",
        user_message="2号库位现在还有多少容量？",
        state=state,
    )

    assert arguments == {"query": "2号库位", "limit": 10}


def test_warehouse_capacity_uses_model_arguments_and_resolver() -> None:
    tool_client = MockToolClient(
        {
            "resolve_warehouses": {
                "resolutionStatus": "UNIQUE",
                "candidates": [{"warehouseId": 2, "displayLabel": "2号库位", "matchType": "NORMALIZED_NAME"}],
            },
            "get_warehouse_status": {
                "warehouseName": "2",
                "capacity": 100,
                "usedCapacity": 40,
                "availableCapacity": 60,
                "occupancyRate": "40%",
            },
        }
    )
    app = create_app(Settings(tool_mode="mock"), tool_client=tool_client, checkpointer=InMemoryCheckpointer())
    response = TestClient(app).post("/internal/agent/chat", json=chat_payload("2号库位现在还有多少容量？"))

    assert response.status_code == 200
    assert tool_client.calls[0]["toolName"] == "resolve_warehouses"
    assert tool_client.calls[0]["arguments"] == {"query": "2号库位", "limit": 10}
    assert tool_client.calls[1]["toolName"] == "get_warehouse_status"
    assert tool_client.calls[1]["arguments"] == {"warehouseId": 2}
    body = response.json()
    assert "剩余容量 60" in body["answer"]
    body_text = json.dumps(body, ensure_ascii=False)
    assert "toolName" not in body_text
    assert "SUCCESS" not in body_text
    assert "warehouseId" not in body_text

class FakeGatewayServer:
    def __init__(self, response: dict[str, Any], status_code: int = 200) -> None:
        self.response = response
        self.status_code = status_code
        self.last_path: str | None = None
        self.last_headers: dict[str, str] = {}
        self.last_body: dict[str, Any] = {}
        self._server: ThreadingHTTPServer | None = None
        self._thread: threading.Thread | None = None
        self.base_url = ""

    def start(self) -> None:
        owner = self

        class Handler(BaseHTTPRequestHandler):
            def do_POST(self) -> None:  # noqa: N802
                length = int(self.headers.get("Content-Length", "0"))
                body = self.rfile.read(length)
                owner.last_path = self.path
                owner.last_headers = {key: value for key, value in self.headers.items()}
                owner.last_body = json.loads(body.decode("utf-8"))
                payload = json.dumps(owner.response, ensure_ascii=False).encode("utf-8")
                self.send_response(owner.status_code)
                self.send_header("Content-Type", "application/json; charset=utf-8")
                self.send_header("Content-Length", str(len(payload)))
                self.end_headers()
                self.wfile.write(payload)

            def log_message(self, format: str, *args: Any) -> None:  # noqa: A002
                return

        self._server = ThreadingHTTPServer(("127.0.0.1", 0), Handler)
        self.base_url = f"http://127.0.0.1:{self._server.server_port}"
        self._thread = threading.Thread(target=self._server.serve_forever, daemon=True)
        self._thread.start()

    def stop(self) -> None:
        if self._server:
            self._server.shutdown()
            self._server.server_close()
        if self._thread:
            self._thread.join(timeout=2)

def test_warehouse_real_nested_capacity_is_safely_formatted() -> None:
    tool_client = MockToolClient(
        {
            "resolve_warehouses": {
                "resolutionStatus": "UNIQUE",
                "candidates": [{"warehouseId": 2, "displayLabel": "2", "matchType": "NORMALIZED_NAME"}],
            },
            "get_warehouse_status": {
                "warehouse": {"warehouseId": 2, "warehouseName": "2"},
                "capacity": {
                    "warehouseId": 2,
                    "warehouseName": "2",
                    "status": "正常",
                    "curCapacity": 3,
                    "maxCapacity": 120,
                    "capacityPercentage": 2.5,
                    "currentPalletCount": 3,
                },
            },
        }
    )
    app = create_app(Settings(tool_mode="mock"), tool_client=tool_client, checkpointer=InMemoryCheckpointer())
    response = TestClient(app).post("/internal/agent/chat", json=chat_payload("2号库位现在还有多少容量？"))

    body = response.json()
    assert "2号库位" in body["answer"]
    assert "最大容量 120" in body["answer"]
    assert "当前占用 3" in body["answer"]
    assert "当前托盘数 3" in body["answer"]
    assert "剩余容量 117" in body["answer"]
    assert "占用率 2.5%" in body["answer"]
    body_text = json.dumps(body, ensure_ascii=False)
    assert "warehouseId" not in body_text
    assert "{'" not in body_text
    assert '"capacity"' not in body_text


def test_warehouse_missing_capacity_fields_uses_safe_message() -> None:
    tool_client = MockToolClient(
        {
            "resolve_warehouses": {
                "resolutionStatus": "UNIQUE",
                "candidates": [{"warehouseId": 2, "displayLabel": "2"}],
            },
            "get_warehouse_status": {"warehouse": {"warehouseId": 2, "warehouseName": "2"}},
        }
    )
    app = create_app(Settings(tool_mode="mock"), tool_client=tool_client, checkpointer=InMemoryCheckpointer())
    response = TestClient(app).post("/internal/agent/chat", json=chat_payload("2号库位现在怎么样？"))

    assert "缺少容量明细字段" in response.json()["answer"]
    assert "None" not in response.json()["answer"]


def test_inventory_real_nested_summary_is_safely_formatted() -> None:
    tool_client = MockToolClient(
        {
            "resolve_products": {
                "resolutionStatus": "UNIQUE",
                "candidates": [{"productId": 84, "displayLabel": "黄冰糖（袋）"}],
            },
            "get_inventory_overview": {
                "resolvedProduct": {"productId": 84, "productName": "黄冰糖（袋）"},
                "summary": {
                    "totalRecords": 1,
                    "rawFullPallets": 10,
                    "rawLoosePieces": 70,
                    "normalizedPallets": 11,
                    "normalizedLoosePieces": 30,
                    "totalEquivalentPieces": 470,
                    "totalWeight": 11750.0,
                    "displayStockInfo": "11板30件",
                },
                "records": [{"productId": 84, "warehouseId": 2}],
            },
        }
    )
    app = create_app(Settings(tool_mode="mock"), tool_client=tool_client, checkpointer=InMemoryCheckpointer())
    response = TestClient(app).post("/internal/agent/chat", json=chat_payload("查黄冰糖（袋）库存"))

    body = response.json()
    assert "11板30件" in body["answer"]
    assert "折合 470 件" in body["answer"]
    assert "总重量 11750.0kg" in body["answer"]
    body_text = json.dumps(body, ensure_ascii=False)
    assert "productId" not in body_text
    assert "warehouseId" not in body_text
    assert "rawFullPallets" not in body_text


def test_inventory_missing_quantity_fields_uses_explicit_message() -> None:
    tool_client = MockToolClient(
        {
            "resolve_products": {
                "resolutionStatus": "UNIQUE",
                "candidates": [{"productId": 84, "displayLabel": "黄冰糖（袋）"}],
            },
            "get_inventory_overview": {
                "summary": {"totalRecords": 1},
                "records": [{"productId": 84}],
            },
        }
    )
    app = create_app(Settings(tool_mode="mock"), tool_client=tool_client, checkpointer=InMemoryCheckpointer())
    response = TestClient(app).post("/internal/agent/chat", json=chat_payload("查黄冰糖（袋）库存"))

    answer = response.json()["answer"]
    assert "缺少可展示的库存数量字段" in answer
    assert "库存结果已返回" not in answer


def test_inventory_location_followup_uses_safe_distribution_when_available() -> None:
    tool_client = MockToolClient(
        {
            "resolve_products": {
                "resolutionStatus": "UNIQUE",
                "candidates": [{"productId": 84, "displayLabel": "黄冰糖（袋）"}],
            },
            "get_inventory_overview": {
                "summary": {"totalRecords": 1, "displayStockInfo": "11板30件"},
                "records": [
                    {
                        "warehouseId": 2,
                        "warehouseName": "2",
                        "displayStockInfo": "11板30件",
                        "totalEquivalentPieces": 470,
                    }
                ],
            },
        }
    )
    app = create_app(Settings(tool_mode="mock"), tool_client=tool_client, checkpointer=InMemoryCheckpointer())
    client = TestClient(app)
    client.post("/internal/agent/chat", json=chat_payload("查黄冰糖（袋）库存"))
    response = client.post("/internal/agent/chat", json=chat_payload("这些主要存放在哪些库位？"))

    body = response.json()
    assert "主要存放在以下库位" in body["answer"]
    assert "2号库位" in body["answer"]
    assert "warehouseId" not in json.dumps(body, ensure_ascii=False)


def test_sse_sanitizes_real_nested_results_and_sensitive_values() -> None:
    tool_client = MockToolClient(
        {
            "resolve_warehouses": {
                "resolutionStatus": "UNIQUE",
                "candidates": [{"warehouseId": 2, "displayLabel": "2"}],
            },
            "get_warehouse_status": {
                "warehouseId": 2,
                "Authorization": "Bearer secret-value",
                "token": "secret-value",
                "stackTrace": "java.lang.Exception at C:\\server\\Internal.java:1",
                "capacity": {
                    "warehouseId": 2,
                    "warehouseName": "2",
                    "maxCapacity": 120,
                    "curCapacity": 3,
                },
            },
        }
    )
    app = create_app(Settings(tool_mode="mock"), tool_client=tool_client, checkpointer=InMemoryCheckpointer())
    response = TestClient(app).post("/internal/agent/chat/stream", json=chat_payload("2号库位容量"))

    text = response.text
    for forbidden in [
        "warehouseId",
        "productId",
        "Authorization",
        "Bearer",
        "secret-value",
        "token",
        "stackTrace",
        "toolName",
        "SUCCESS",
    ]:
        assert forbidden not in text


def test_java_service_key_is_required_for_real_gateway_mode() -> None:
    settings = Settings(
        tool_mode="java_gateway",
        java_tool_gateway_base_url="http://localhost:8080",
        agent_internal_tool_service_key="tool-key",
        python_service_key="python-key",
    )
    app = create_app(settings, tool_client=MockToolClient(), checkpointer=InMemoryCheckpointer())
    client = TestClient(app)

    missing = client.get("/internal/agent/health")
    invalid = client.get(
        "/internal/agent/health",
        headers={"X-Agent-Service-Key": "wrong-key"},
    )
    valid = client.get(
        "/internal/agent/health",
        headers={"X-Agent-Service-Key": "python-key"},
    )

    assert missing.status_code == 401
    assert invalid.status_code == 401
    assert valid.status_code == 200
    text = valid.text + missing.text + invalid.text
    assert "python-key" not in text
    assert "tool-key" not in text


def test_candidate_selected_can_use_chat_endpoint_as_same_conversation_event() -> None:
    tool_client = MockToolClient(
        {
            "resolve_products": {
                "resolutionStatus": "AMBIGUOUS",
                "options": [
                    {
                        "optionType": "SINGLE_PRODUCT",
                        "displayLabel": "黄冰糖（袋）",
                        "productId": 84,
                    }
                ],
            },
            "get_inventory_overview": {
                "displayStockInfo": "11板30件",
                "totalEquivalentPieces": 470,
                "totalWeight": 11750.0,
            },
        }
    )
    checkpointer = InMemoryCheckpointer()
    app = create_app(Settings(tool_mode="mock"), tool_client=tool_client, checkpointer=checkpointer)
    client = TestClient(app)
    client.post("/internal/agent/chat", json=chat_payload("查黄冰糖库存"))

    response = client.post(
        "/internal/agent/chat",
        json={
            "agentSessionId": "agt_test",
            "message": {
                "type": "candidate_selected",
                "selection": {
                    "optionType": "SINGLE_PRODUCT",
                    "displayLabel": "黄冰糖（袋）",
                },
            },
            "client": {"traceId": "trace_002", "requestId": "req_002"},
        },
    )

    assert response.status_code == 200
    body = response.json()
    assert body["answer"] == "黄冰糖（袋）当前库存为 11板30件，折合 470 件，总重量 11750.0kg。"
    assert checkpointer.get("agt_test").selected_product is not None
    body_text = json.dumps(body, ensure_ascii=False)
    assert "productId" not in body_text
    assert "toolName" not in body_text
    assert "SUCCESS" not in body_text
