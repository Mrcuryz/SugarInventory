from __future__ import annotations

from dataclasses import dataclass, field
from typing import Any, Protocol

from app.context import DomainContextPack
from app.graph.state import WarehouseAgentState


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


class ModelClient(Protocol):
    def build_tool_arguments(self, request: ModelArgumentRequest) -> ModelArgumentDecision:
        ...


class BasicModelClient:
    """Deterministic local model substitute for M1.3R-1d.

    The production path can replace this with a real LLM client. This class
    intentionally receives messages, state, domain context, and tool schema so
    runtime code does not grow domain-specific extraction branches.
    """

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

    def _extract_warehouse_phrase(self, text: str) -> str:
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
                "状态",
                "情况",
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
            ],
        )
        return self._trim_phrase(cleaned)

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
