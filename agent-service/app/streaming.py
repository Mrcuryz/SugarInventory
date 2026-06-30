from __future__ import annotations

import json
import re
from typing import Any, Iterator

from app.schemas import ChatResponse


def sse_for_response(response: ChatResponse) -> Iterator[str]:
    yield _event("message_start", {"agentSessionId": response.agentSessionId})
    if response.needsUserSelection:
        yield _event("progress", {"stage": "clarification", "text": "需要确认查询范围。"})
        for card in response.cards:
            if card.cardType == "candidate_selection":
                yield _event(
                    "clarification",
                    {
                        "prompt": card.prompt or response.answer,
                        "options": [option.model_dump(exclude_none=True) for option in card.options],
                    },
                )
    elif response.error:
        yield _event("error", {"message": response.error.message, "retryable": response.error.retryable})
    else:
        yield _event("progress", {"stage": "answer", "text": "正在整理查询结果。"})
        yield _event("text_delta", {"text": response.answer})
    yield _event("message_end", {"status": "error" if response.error else "completed"})


_BLOCKED_KEYS = {
    "authorization",
    "delegationtoken",
    "password",
    "productid",
    "refreshtoken",
    "stacktrace",
    "token",
    "toolname",
    "warehouseid",
}


def _event(event: str, data: dict) -> str:
    safe_data = _sanitize_event_value(data)
    return f"event: {event}\ndata: {json.dumps(safe_data, ensure_ascii=False)}\n\n"


def _sanitize_event_value(value: Any) -> Any:
    if isinstance(value, dict):
        return {
            key: _sanitize_event_value(item)
            for key, item in value.items()
            if key.replace("_", "").lower() not in _BLOCKED_KEYS
        }
    if isinstance(value, list):
        return [_sanitize_event_value(item) for item in value]
    if isinstance(value, str):
        return re.sub(
            r"(?i)(bearer\s+[a-z0-9._-]+|authorization\s*[:=]\s*[^,;\s]+|"
            r"eyJ[a-z0-9_-]+\.[a-z0-9_-]+\.[a-z0-9_-]+)",
            "[REDACTED]",
            value,
        )
    return value