from __future__ import annotations

import json
from typing import Iterator

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


def _event(event: str, data: dict) -> str:
    return f"event: {event}\ndata: {json.dumps(data, ensure_ascii=False)}\n\n"


