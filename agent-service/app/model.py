from __future__ import annotations

import json
import socket
from dataclasses import dataclass, field
from datetime import date, timedelta
import re
from collections.abc import Iterator
from typing import Any, Literal, Protocol
from urllib.error import HTTPError, URLError
from urllib.request import Request, urlopen

from app.cancellation import RunCancelledError, current_cancellation_token
from app.config import Settings
from app.context import DomainContextPack
from app.graph.state import WarehouseAgentState


class ModelStreamError(Exception):
    def __init__(self, code: str, message: str = "模型流式输出失败。") -> None:
        self.code = code
        self.message = message
        super().__init__(code)


class ModelStreamTimeout(ModelStreamError):
    def __init__(self) -> None:
        super().__init__("MODEL_TIMEOUT", "模型响应超时，请稍后重试。")


@dataclass(frozen=True)
class ModelArgumentRequest:
    toolName: str
    userMessage: str
    messages: list[dict[str, Any]]
    state: WarehouseAgentState
    domainContext: list[DomainContextPack]
    toolSchema: dict[str, Any]


@dataclass(frozen=True)
class ModelArgumentDecision:
    toolName: str
    arguments: dict[str, Any] = field(default_factory=dict)
    confidenceNote: str | None = None


@dataclass(frozen=True)
class ModelPlanRequest:
    userMessage: str
    messages: list[dict[str, Any]]
    state: WarehouseAgentState
    domainContext: list[DomainContextPack]
    toolSchemas: dict[str, dict[str, Any]]


@dataclass(frozen=True)
class ModelPlanDecision:
    action: Literal["call_tool", "ask_user", "answer"]
    toolName: str | None = None
    arguments: dict[str, Any] = field(default_factory=dict)
    intent: str | None = None
    responseMode: str | None = None
    answer: str | None = None
    prompt: str | None = None
    suggestions: list[str] = field(default_factory=list)
    confidenceNote: str | None = None


class ModelClient(Protocol):
    def plan_next_action(self, request: ModelPlanRequest) -> ModelPlanDecision:
        ...

    def build_tool_arguments(self, request: ModelArgumentRequest) -> ModelArgumentDecision:
        ...

    def stream_answer_deltas(self, answer: str) -> Iterator[str]:
        ...


class BasicModelClient:
    """Deterministic local model substitute for M1.3R-1d.

    The production path can replace this with a real LLM client. This class
    intentionally receives messages, state, domain context, and tool schema so
    runtime code does not grow domain-specific extraction branches.
    """

    def plan_next_action(self, request: ModelPlanRequest) -> ModelPlanDecision:
        text = request.userMessage
        state = request.state

        if self._is_sensitive_credential_request(text):
            return ModelPlanDecision(
                action="answer",
                answer="我不能提供登录凭据、访问密钥或其他敏感凭据。",
                confidenceNote="security refusal for credential exfiltration request",
            )

        if self._is_warehouse_inventory_distribution_query(text):
            arguments = self.build_tool_arguments(
                ModelArgumentRequest(
                    toolName="resolve_warehouses",
                    userMessage=text,
                    messages=request.messages,
                    state=state,
                    domainContext=request.domainContext,
                    toolSchema=request.toolSchemas["resolve_warehouses"],
                )
            ).arguments
            if arguments.get("query"):
                return ModelPlanDecision(
                    action="call_tool",
                    toolName="resolve_warehouses",
                    arguments=arguments,
                    intent="inventory_distribution",
                    responseMode="inventory_distribution",
                    confidenceNote="warehouse inventory query requires warehouse resolver before scoped distribution",
                )
            return ModelPlanDecision(
                action="ask_user",
                prompt="你想查哪个库位的库存？请提供库位名称，例如 8号库位。",
                confidenceNote="warehouse inventory query lacks resolvable warehouse phrase",
            )

        if self._is_inventory_distribution_query(text):
            distribution_arguments = self._distribution_arguments(text, state)
            if distribution_arguments["productScope"]["type"] == "ALL":
                return ModelPlanDecision(
                    action="call_tool",
                    toolName="get_inventory_distribution",
                    arguments=distribution_arguments,
                    intent="inventory_distribution",
                    responseMode="inventory_distribution",
                    confidenceNote="explicit all-product distribution uses controlled aggregate scope",
                )
            if state.selected_product is None:
                if self._has_context_reference(text):
                    return ModelPlanDecision(
                        action="ask_user",
                        prompt="你想查哪个产品？请先选择或输入一个明确的产品名称。",
                        suggestions=["例如：黄冰糖（袋）在哪些库位？"],
                        confidenceNote="product location follow-up without selected product",
                    )
                arguments = self.build_tool_arguments(
                    ModelArgumentRequest(
                        toolName="resolve_products",
                        userMessage=text,
                        messages=request.messages,
                        state=state,
                        domainContext=request.domainContext,
                        toolSchema=request.toolSchemas["resolve_products"],
                    )
                ).arguments
                return ModelPlanDecision(
                    action="call_tool",
                    toolName="resolve_products",
                    arguments=arguments,
                    intent="inventory_distribution",
                    confidenceNote="distribution query requires product resolution",
                )
            return ModelPlanDecision(
                action="call_tool",
                toolName="get_inventory_distribution",
                arguments=distribution_arguments,
                intent="inventory_distribution",
                responseMode="inventory_distribution",
                confidenceNote="follow-up uses selected product from structured state",
            )

        if self._has_warehouse_semantics(text):
            if self._has_context_reference(text) and state.selected_warehouse is not None:
                return ModelPlanDecision(
                    action="call_tool",
                    toolName="get_warehouse_status",
                    arguments={"warehouseId": state.selected_warehouse.internal_id},
                    intent="warehouse_status",
                    responseMode="warehouse_status",
                    confidenceNote="warehouse follow-up uses selected warehouse from structured state",
                )
            try:
                arguments = self.build_tool_arguments(
                    ModelArgumentRequest(
                        toolName="resolve_warehouses",
                        userMessage=text,
                        messages=request.messages,
                        state=state,
                        domainContext=request.domainContext,
                        toolSchema=request.toolSchemas["resolve_warehouses"],
                    )
                ).arguments
            except (KeyError, ValueError):
                arguments = {}
            if arguments.get("query"):
                return ModelPlanDecision(
                    action="call_tool",
                    toolName="resolve_warehouses",
                    arguments=arguments,
                    intent="warehouse_status",
                    responseMode="warehouse_status",
                    confidenceNote="warehouse resolver selected from domain context and tool schema",
                )
            return ModelPlanDecision(
                action="ask_user",
                prompt="你想查哪个库位？请提供库位名称，例如 2号库位。",
                confidenceNote="warehouse intent lacks resolvable warehouse phrase",
            )

        if "托盘" in text:
            try:
                arguments = self.build_tool_arguments(
                    ModelArgumentRequest(
                        toolName="get_pallet_status",
                        userMessage=text,
                        messages=request.messages,
                        state=state,
                        domainContext=request.domainContext,
                        toolSchema=request.toolSchemas["get_pallet_status"],
                    )
                ).arguments
            except (KeyError, ValueError):
                arguments = {}
            if arguments.get("code"):
                return ModelPlanDecision(
                    action="call_tool",
                    toolName="get_pallet_status",
                    arguments=arguments,
                    intent="pallet_status",
                    responseMode="pallet_status",
                )
            return ModelPlanDecision(action="ask_user", prompt="请提供要查询的托盘码。")

        if "化验" in text:
            if state.selected_product is not None and state.selected_product.internal_id is not None and self._has_context_reference(text):
                return ModelPlanDecision(
                    action="call_tool",
                    toolName="get_assay_status",
                    arguments={
                        "productId": state.selected_product.internal_id,
                        "productionDate": date.today().isoformat(),
                    },
                    intent="assay",
                    responseMode="assay_status",
                    confidenceNote="assay follow-up uses selected product from structured state",
                )
            product_args = self._product_arguments(request)
            if product_args.get("query"):
                return ModelPlanDecision(
                    action="call_tool",
                    toolName="resolve_products",
                    arguments=product_args,
                    intent="assay",
                    responseMode="assay_status",
                    confidenceNote="assay intent needs product resolver",
                )
            return ModelPlanDecision(
                action="ask_user",
                prompt="你想查询哪个产品的化验？请提供明确产品名称或先选择产品。",
            )

        if "库存" in text:
            product_args = self._product_arguments(request)
            if state.selected_product is not None and state.selected_product.internal_id is not None and (
                self._has_context_reference(text) or not product_args.get("query")
            ):
                return ModelPlanDecision(
                    action="call_tool",
                    toolName="get_inventory_overview",
                    arguments={"productId": state.selected_product.internal_id},
                    intent="inventory",
                    responseMode="inventory_overview",
                    confidenceNote="inventory query uses selected product from structured state",
                )
            if product_args.get("query"):
                return ModelPlanDecision(
                    action="call_tool",
                    toolName="resolve_products",
                    arguments=product_args,
                    intent="inventory",
                    responseMode="inventory_overview",
                    confidenceNote="inventory intent needs product resolver",
                )
            return ModelPlanDecision(
                action="ask_user",
                prompt="你想查哪个产品的库存？请先输入明确产品名称。",
            )

        return ModelPlanDecision(
            action="ask_user",
            prompt="当前我只支持库存、库位、托盘和化验的只读查询。请补充要查询的产品、库位、托盘码或生产日期。",
            confidenceNote="no supported read-only tool matched",
        )

    def build_tool_arguments(self, request: ModelArgumentRequest) -> ModelArgumentDecision:
        if request.toolName == "resolve_warehouses":
            return ModelArgumentDecision(
                toolName=request.toolName,
                arguments={
                    "query": self._extract_warehouse_phrase(request.userMessage),
                    "limit": self._limit_from_schema(request.toolSchema),
                },
                confidenceNote="warehouse query phrase extracted with warehouse domain context",
            )
        if request.toolName == "resolve_products":
            return ModelArgumentDecision(
                toolName=request.toolName,
                arguments={
                    "query": self._extract_product_phrase(request.userMessage),
                    "limit": self._limit_from_schema(request.toolSchema),
                },
                confidenceNote="product query phrase extracted from user message",
            )
        if request.toolName == "get_pallet_status":
            return ModelArgumentDecision(
                toolName=request.toolName,
                arguments={"code": self._extract_after_terms(request.userMessage, ["托盘码", "托盘"])},
            )
        return ModelArgumentDecision(toolName=request.toolName, arguments={})

    def stream_answer_deltas(self, answer: str) -> Iterator[str]:
        if answer:
            yield answer

    def _product_arguments(self, request: ModelPlanRequest) -> dict[str, Any]:
        try:
            return self.build_tool_arguments(
                ModelArgumentRequest(
                    toolName="resolve_products",
                    userMessage=request.userMessage,
                    messages=request.messages,
                    state=request.state,
                    domainContext=request.domainContext,
                    toolSchema=request.toolSchemas["resolve_products"],
                )
            ).arguments
        except (KeyError, ValueError):
            return {}

    def _extract_warehouse_phrase(self, text: str) -> str:
        warehouse_match = re.search(r"([0-9０-９]{1,4})\s*号\s*库位?", text)
        if warehouse_match:
            number = warehouse_match.group(1).translate(str.maketrans("０１２３４５６７８９", "0123456789"))
            return f"{number}号库位"
        cleaned = self._strip_common_words(
            text,
            [
                "帮我",
                "查询",
                "查一下",
                "查",
                "当前",
                "现在",
                "还有多少",
                "多少",
                "容量",
                "库存",
                "状态",
                "情况",
                "库位",
                "的",
                "是什么",
                "怎么样",
            ],
        )
        return self._trim_phrase(cleaned)

    def _extract_product_phrase(self, text: str) -> str:
        cleaned = self._strip_common_words(
            text,
            [
                "帮我",
                "查询",
                "查一下",
                "查",
                "当前",
                "现在",
                "库存",
                "情况",
                "有没有",
                "化验",
                "合不合格",
                "主要存放在",
                "存放在哪",
                "哪些库位",
                "哪些库",
                "库存分布",
                "按产品",
                "按品种",
                "按库位",
                "分类",
                "统计",
                "全部产品",
                "所有产品",
                "全部品种",
                "所有品种",
                "全产品",
                "最近",
                "近",
                "天",
                "不合格",
                "未通过",
                "检测失败",
                "无化验",
                "未化验",
                "无标准",
            ],
        )
        return self._trim_phrase(cleaned).removesuffix("的").strip()

    def _extract_after_terms(self, text: str, terms: list[str]) -> str:
        for term in terms:
            if term in text:
                return self._trim_phrase(text.split(term, 1)[-1])
        return self._trim_phrase(text)

    def _strip_common_words(self, text: str, words: list[str]) -> str:
        cleaned = text
        for word in sorted(words, key=len, reverse=True):
            cleaned = cleaned.replace(word, "")
        return cleaned

    def _trim_phrase(self, text: str) -> str:
        return text.strip(" \t\r\n，。？?！!：:；;、")[:100]

    def _limit_from_schema(self, schema: dict[str, Any]) -> int:
        properties = schema.get("properties") if isinstance(schema, dict) else {}
        limit_schema = properties.get("limit") if isinstance(properties, dict) else {}
        default = limit_schema.get("default") if isinstance(limit_schema, dict) else None
        if isinstance(default, int) and 1 <= default <= 100:
            return default
        return 10

    def _is_inventory_location_followup(self, text: str) -> bool:
        return self._has_context_reference(text) and any(word in text for word in ["存放", "在哪", "哪些库位"])

    def _is_warehouse_inventory_distribution_query(self, text: str) -> bool:
        return bool(re.search(r"[0-9０-９]{1,4}\s*号\s*库位?", text)) and "库存" in text

    def _is_inventory_distribution_query(self, text: str) -> bool:
        if any(word in text for word in ["存放在哪", "主要在哪", "哪些库位", "哪些库", "库存分布", "分布情况", "按产品分布", "按库位分布"]):
            return True
        if "库存" in text and any(
            phrase in text
            for phrase in [
                "按产品分类",
                "按品种分类",
                "按产品统计",
                "按品种统计",
                "按库位分类",
                "按库位统计",
                "按库位和产品分类",
                "按产品和库位分类",
            ]
        ):
            return True
        return self._explicit_all_product_request(text) and any(
            word in text for word in ["不合格", "未通过", "检测失败", "无化验", "未化验", "无标准", "分类", "统计"]
        )

    def _distribution_arguments(self, text: str, state: WarehouseAgentState) -> dict[str, Any]:
        if self._explicit_all_product_request(text):
            product_scope: dict[str, Any] = {"type": "ALL"}
        elif state.selected_product is not None:
            scope_type = str(state.selected_product.metadata.get("scopeType") or "SINGLE_PRODUCT")
            if scope_type == "EXACT_PRODUCT_NAME_GROUP":
                product_scope = {
                    "type": scope_type,
                    "productName": state.selected_product.metadata.get("productName"),
                }
            elif scope_type == "PRODUCT_TYPE_GROUP":
                product_scope = {
                    "type": scope_type,
                    "productType": state.selected_product.metadata.get("productType"),
                }
            else:
                product_scope = {"type": "SINGLE_PRODUCT", "productId": state.selected_product.internal_id}
        else:
            product_scope = {"type": "UNRESOLVED"}

        warehouse_scope: dict[str, Any] = {"type": "ALL"}
        if state.selected_warehouse is not None and self._has_context_reference(text) and any(
            word in text for word in ["这个库位", "该库位", "这个仓库", "该仓库"]
        ):
            warehouse_scope = {
                "type": "SINGLE_WAREHOUSE",
                "warehouseId": state.selected_warehouse.internal_id,
            }
        return {
            "productScope": product_scope,
            "warehouseScope": warehouse_scope,
            "statusFilter": self._distribution_filters(text),
            "groupBy": self._distribution_grouping(text),
            "limit": 20,
        }

    def _distribution_grouping(self, text: str) -> str:
        if any(phrase in text for phrase in ["产品和库位", "库位和产品", "各产品各库位", "产品库位明细", "按库位和产品分类", "按产品和库位分类"]):
            return "warehouse_product"
        if any(phrase in text for phrase in ["按产品", "各产品", "按品种", "各品种"]):
            return "product"
        return "warehouse"

    def _distribution_filters(self, text: str) -> dict[str, Any]:
        result: dict[str, Any] = {}
        if "半成品" in text:
            result["productStatuses"] = ["半成品"]
        elif "成品" in text:
            result["productStatuses"] = ["成品"]
        warehouse_statuses = [status for status in ["正常", "空置", "满仓", "维护", "临期预警"] if status in text]
        if warehouse_statuses:
            result["warehouseStatuses"] = warehouse_statuses
        pallet_status_map = {
            "空闲托盘": "FREE",
            "待入库托盘": "PENDING",
            "在库托盘": "INSTOCK",
            "作废托盘": "INVALID",
            "订单预留托盘": "ORDER_RESERVED",
        }
        pallet_statuses = [value for label, value in pallet_status_map.items() if label in text]
        if pallet_statuses:
            result["palletStatuses"] = pallet_statuses
        assay_status_map = [
            ("没有化验", "MISSING_ASSAY"),
            ("无化验", "MISSING_ASSAY"),
            ("未化验", "MISSING_ASSAY"),
            ("有化验", "HAS_ASSAY"),
            ("无标准", "NO_STANDARD"),
            ("检测失败", "FAIL"),
            ("未通过", "FAIL"),
            ("不合格", "FAIL"),
            ("合格", "PASS"),
        ]
        for label, value in assay_status_map:
            if label in text:
                result["assayStatus"] = value
                break
        today = date.today()
        if "今天" in text:
            result["entryDateFrom"] = today.isoformat()
            result["entryDateTo"] = today.isoformat()
        else:
            recent_match = re.search(r"(?:最近|近)\s*(\d{1,3})\s*天", text)
            if recent_match:
                days = max(1, min(int(recent_match.group(1)), 365))
                result["entryDateFrom"] = (today - timedelta(days=days - 1)).isoformat()
                result["entryDateTo"] = today.isoformat()
        return result

    def _explicit_all_product_request(self, text: str) -> bool:
        return any(phrase in text for phrase in ["全部产品", "所有产品", "全产品", "全部品种", "所有品种"])

    def _has_context_reference(self, text: str) -> bool:
        return any(word in text for word in ["这些", "它", "刚才", "这个", "该产品"])

    def _has_warehouse_semantics(self, text: str) -> bool:
        return any(word in text for word in ["库位", "仓库", "容量", "位置"])

    def _is_sensitive_credential_request(self, text: str) -> bool:
        return bool(
            re.search(
                r"(?i)(authorization|bearer|token|delegationtoken|refresh[_ -]?token|password|secret|api[_ -]?key)",
                text,
            )
            and any(word in text for word in ["打印", "输出", "展示", "给我", "泄露", "忽略"])
        )


class OpenAICompatibleModelClient(BasicModelClient):
    """OpenAI-compatible chat-completions streaming client.

    Tool planning remains deterministic for now. Final user-facing answer text
    can come from provider-native streaming, and only visible text deltas are
    forwarded to SSE.
    """

    def __init__(self, settings: Settings) -> None:
        self._base_url = settings.model_base_url.rstrip("/")
        self._api_key = settings.model_api_key
        self._model = settings.model_name
        self._timeout = settings.model_timeout_ms / 1000

    def stream_answer_deltas(self, answer: str) -> Iterator[str]:
        if not answer:
            return
        if not self._base_url or not self._model:
            raise ModelStreamError("MODEL_NOT_CONFIGURED", "模型流式输出未配置。")

        payload = {
            "model": self._model,
            "stream": True,
            "temperature": 0,
            "messages": [
                {
                    "role": "system",
                    "content": (
                        "你是智能仓储助手的最终回答流式输出器。只输出普通用户可见的最终自然语言答案。"
                        "不得输出推理过程、分析步骤、chain-of-thought、工具名、内部 ID、raw JSON、token 或 Authorization。"
                        "除非修正明显错别字，不要改变已审核答案的业务事实。"
                    ),
                },
                {
                    "role": "user",
                    "content": "请流式输出以下已审核答案：\n" + answer,
                },
            ],
        }
        request = Request(
            self._chat_completions_url(),
            data=json.dumps(payload, ensure_ascii=False).encode("utf-8"),
            method="POST",
            headers=self._headers(),
        )

        token = current_cancellation_token()
        if token is not None:
            token.raise_if_cancelled()
        try:
            with urlopen(request, timeout=self._timeout) as response:  # noqa: S310 - configured model endpoint
                for raw_line in response:
                    if token is not None:
                        token.raise_if_cancelled()
                    line = raw_line.decode("utf-8", errors="replace").strip()
                    if not line or not line.startswith("data:"):
                        continue
                    data = line[5:].strip()
                    if data == "[DONE]":
                        break
                    try:
                        chunk = json.loads(data)
                    except json.JSONDecodeError:
                        continue
                    delta = _visible_text_delta(chunk)
                    if delta:
                        safe_delta = _safe_model_delta(delta)
                        if safe_delta:
                            yield safe_delta
        except RunCancelledError:
            raise
        except (TimeoutError, socket.timeout) as exc:
            raise ModelStreamTimeout() from exc
        except HTTPError as exc:
            if int(exc.code) == 408 or int(exc.code) >= 500:
                raise ModelStreamError("MODEL_UPSTREAM_ERROR", "模型服务暂时不可用。") from exc
            raise ModelStreamError("MODEL_BAD_REQUEST", "模型请求被拒绝。") from exc
        except URLError as exc:
            reason = getattr(exc, "reason", None)
            if isinstance(reason, socket.timeout):
                raise ModelStreamTimeout() from exc
            raise ModelStreamError("MODEL_UPSTREAM_ERROR", "模型服务暂时不可用。") from exc

    def _headers(self) -> dict[str, str]:
        headers = {"Content-Type": "application/json; charset=utf-8"}
        if self._api_key:
            headers["Authorization"] = f"Bearer {self._api_key}"
        return headers

    def _chat_completions_url(self) -> str:
        if self._base_url.endswith("/chat/completions"):
            return self._base_url
        return f"{self._base_url}/chat/completions"


def build_model_client(settings: Settings) -> ModelClient:
    if settings.model_mode == "openai_compatible":
        return OpenAICompatibleModelClient(settings)
    return BasicModelClient()


def _visible_text_delta(chunk: dict[str, Any]) -> str | None:
    if chunk.get("type") == "response.output_text.delta":
        delta = chunk.get("delta")
        return delta if isinstance(delta, str) else None

    choices = chunk.get("choices")
    if isinstance(choices, list):
        parts = []
        for choice in choices:
            if not isinstance(choice, dict):
                continue
            delta = choice.get("delta")
            if isinstance(delta, dict):
                content = delta.get("content")
                if isinstance(content, str):
                    parts.append(content)
            message = choice.get("message")
            if isinstance(message, dict):
                content = message.get("content")
                if isinstance(content, str):
                    parts.append(content)
        return "".join(parts) if parts else None
    return None


def _safe_model_delta(delta: str) -> str:
    if re.search(
        r"(?i)(authorization|bearer\s+|delegationtoken|refresh[_ -]?token|stack\s*trace|jdbc:|"
        r"chain[- ]?of[- ]?thought|reasoning_content|reasoning|tool_calls|"
        r"(?:[a-z]:\\|/)(?:users|home|var|opt|srv|windows)(?:\\|/))",
        delta,
    ):
        return ""
    return re.sub(
        r"(?i)(bearer\s+[a-z0-9._-]+|authorization\s*[:=]\s*[^,;\s]+|"
        r"eyJ[a-z0-9_-]+\.[a-z0-9_-]+\.[a-z0-9_-]+)",
        "[REDACTED]",
        delta,
    )
