from __future__ import annotations

import json
import re
import uuid
from collections.abc import Callable
from typing import Any, Iterator

from app.cancellation import (
    AgentRunRegistry,
    RunCancelledError,
    reset_current_cancellation_token,
    set_current_cancellation_token,
)
from app.model import ModelStreamError, ModelStreamTimeout
from app.schemas import CandidateSelectedMessage, ChatRequest, ChatResponse, ResumeEvent, ResumeRequest


EventPayload = dict[str, Any]
RuntimeHandler = Callable[[ChatRequest], ChatResponse]
ResumeHandler = Callable[[ResumeRequest], ChatResponse]
AnswerDeltaStreamer = Callable[[str], Iterator[str]]


def sse_for_request(
    request: ChatRequest,
    chat_handler: RuntimeHandler,
    resume_handler: ResumeHandler,
    run_registry: AgentRunRegistry | None = None,
    answer_delta_streamer: AnswerDeltaStreamer | None = None,
    run_timeout_ms: int | None = None,
) -> Iterator[str]:
    builder = StreamEventBuilder(request.agentSessionId, request.messageId)
    terminal_context = (
        {"interruptId": request.message.interruptId}
        if isinstance(request.message, CandidateSelectedMessage)
        else None
    )
    run_token = run_registry.start(builder.agent_session_id, builder.message_id, run_timeout_ms) if run_registry else None
    context_token = set_current_cancellation_token(run_token)
    try:
        if _is_cancelled(run_registry, builder):
            yield from _cancelled_events(builder, terminal_context)
            return
        if run_token is not None:
            run_token.raise_if_cancelled()
        yield _event(builder.next("message_start", {"role": "assistant"}))
        yield _event(builder.next("progress", {"stage": "understanding", "text": _progress_text(request)}))
        try:
            set_current_cancellation_token(run_token)
            if run_token is not None:
                run_token.raise_if_cancelled()
            if isinstance(request.message, CandidateSelectedMessage):
                response = resume_handler(
                    ResumeRequest(
                        agentSessionId=request.agentSessionId,
                        messageId=request.messageId,
                        resumeToken=request.message.resumeToken,
                        user=request.user,
                        event=ResumeEvent(
                            type="candidate_selected",
                            interruptId=request.message.interruptId,
                            action=request.message.action,
                            selection=request.message.selection,
                            clientRequestId=request.message.clientRequestId,
                        ),
                        client=request.client,
                    )
                )
            else:
                response = chat_handler(request)
            if run_token is not None:
                run_token.raise_if_cancelled()
            response_events = events_for_response(
                response,
                builder,
                include_start=False,
                answer_delta_streamer=answer_delta_streamer,
                terminal_context=terminal_context,
            )
            while True:
                set_current_cancellation_token(run_token)
                try:
                    yield next(response_events)
                except StopIteration:
                    break
        except RunCancelledError as exc:
            if exc.reason == "PYTHON_TIMEOUT":
                yield _event(
                    builder.next(
                        "error",
                        {"message": "AI 助手处理超时，请稍后重试。", "retryable": True, "category": "PYTHON_TIMEOUT"},
                    )
                )
                yield _event(builder.next("message_end", _finish_payload("timeout", terminal_context)))
            else:
                yield from _cancelled_events(builder, terminal_context)
        except ModelStreamTimeout as exc:
            yield _event(
                builder.next(
                    "error",
                    {"message": exc.message, "retryable": True, "category": "MODEL_TIMEOUT"},
                )
            )
            yield _event(builder.next("message_end", _finish_payload("timeout", terminal_context)))
        except ModelStreamError as exc:
            yield _event(
                builder.next(
                    "error",
                    {"message": exc.message, "retryable": True, "category": "UPSTREAM_ERROR"},
                )
            )
            yield _event(builder.next("message_end", _finish_payload("error", terminal_context)))
        except TimeoutError:
            yield _event(
                builder.next(
                    "error",
                    {"message": "查询仓储数据超时，请稍后重试。", "retryable": True, "category": "TOOL_TIMEOUT"},
                )
            )
            yield _event(builder.next("message_end", _finish_payload("timeout", terminal_context)))
        except Exception:
            yield _event(
                builder.next(
                    "error",
                    {"message": "AI 助手暂时不可用，请稍后重试。", "retryable": True, "category": "UPSTREAM_ERROR"},
                )
            )
            yield _event(builder.next("message_end", _finish_payload("error", terminal_context)))
    finally:
        reset_current_cancellation_token(context_token)
        if run_registry is not None:
            run_registry.finish(builder.agent_session_id, builder.message_id)


def sse_for_response(response: ChatResponse) -> Iterator[str]:
    yield from events_for_response(response, StreamEventBuilder(response.agentSessionId), include_start=True)


def events_for_response(
    response: ChatResponse,
    builder: "StreamEventBuilder",
    include_start: bool,
    answer_delta_streamer: AnswerDeltaStreamer | None = None,
    terminal_context: EventPayload | None = None,
) -> Iterator[str]:
    terminal_payload = _terminal_context(response, terminal_context)
    if include_start:
        yield _event(builder.next("message_start", {"role": "assistant"}))
    if response.reviewTrace:
        yield _event(builder.next("audit", {"intentRouter": response.reviewTrace}))
    interrupt_card = next(
        (
            card
            for card in response.cards
            if card.cardType == "candidate_selection" and card.interruptId and card.resumeToken
        ),
        None,
    )
    if response.needsUserSelection and interrupt_card is not None:
        yield _event(builder.next("progress", {"stage": "clarification", "text": "需要确认查询范围。"}))
        for card in response.cards:
            if card is interrupt_card:
                yield _event(
                    builder.next(
                        "clarification",
                        {
                            "prompt": card.prompt or response.answer,
                            "interruptId": card.interruptId,
                            "interruptKind": card.interruptKind or "CLARIFICATION",
                            "resumeToken": card.resumeToken,
                            "expiresAt": card.expiresAt,
                            "options": [option.model_dump(exclude_none=True) for option in card.options],
                        },
                    )
                )
            else:
                yield _event(builder.next("card", card.model_dump(exclude_none=True)))
        yield _event(
            builder.next(
                "message_end",
                {
                    "finishReason": "interrupt_required",
                    "interruptId": interrupt_card.interruptId,
                    "interruptKind": interrupt_card.interruptKind or "CLARIFICATION",
                    **terminal_payload,
                },
            )
        )
    elif response.error:
        finish_reason = (
            "timeout"
            if response.error.code in {"UPSTREAM_TIMEOUT", "PYTHON_AGENT_TIMEOUT", "MODEL_TIMEOUT"}
            else "error"
        )
        if response.error.code == "MODEL_TIMEOUT":
            category = "MODEL_TIMEOUT"
        elif response.error.code in {"LLM_PLAN_INVALID", "MODEL_UPSTREAM_ERROR"}:
            category = "MODEL_ERROR"
        elif response.error.code == "UPSTREAM_AUTHENTICATION_FAILED":
            category = "AUTHENTICATION_ERROR"
        elif response.error.code == "PERMISSION_DENIED":
            category = "PERMISSION_DENIED"
        else:
            category = "TOOL_TIMEOUT" if finish_reason == "timeout" else "TOOL_ERROR"
        yield _event(
            builder.next(
                "error",
                {
                    "message": response.error.message,
                    "retryable": response.error.retryable,
                    "category": category,
                },
            )
        )
        yield _event(builder.next("message_end", _finish_payload(finish_reason, terminal_payload)))
    else:
        if response.debug:
            yield _event(builder.next("debug", response.debug))
        yield _event(builder.next("progress", {"stage": "answer", "text": "正在整理查询结果。"}))
        for card in response.cards:
            if card.cardType == "candidate_selection":
                continue
            yield _event(builder.next("card", card.model_dump(exclude_none=True)))
        if response.answer:
            for delta in _answer_deltas(response.answer, answer_delta_streamer):
                yield _event(builder.next("text_delta", {"text": delta}))
        yield _event(builder.next("message_end", _finish_payload("completed", terminal_payload)))


class StreamEventBuilder:
    def __init__(self, agent_session_id: str, message_id: str | None = None) -> None:
        self.agent_session_id = agent_session_id
        self.message_id = message_id or f"msg_{uuid.uuid4().hex[:16]}"
        self.sequence = 0

    def next(self, event_type: str, payload: EventPayload) -> dict[str, Any]:
        self.sequence += 1
        return {
            "eventId": f"evt_{self.sequence:06d}",
            "messageId": self.message_id,
            "agentSessionId": self.agent_session_id,
            "type": event_type,
            "sequence": self.sequence,
            "payload": payload,
        }


def _is_cancelled(registry: AgentRunRegistry | None, builder: StreamEventBuilder) -> bool:
    return registry is not None and registry.is_cancelled(builder.agent_session_id, builder.message_id)


def _cancelled_events(
    builder: StreamEventBuilder,
    terminal_context: EventPayload | None = None,
) -> Iterator[str]:
    yield _event(builder.next("cancelled", {"message": "已取消本次生成。"}))
    yield _event(builder.next("message_end", _finish_payload("cancelled", terminal_context)))


def _finish_payload(finish_reason: str, terminal_context: EventPayload | None) -> EventPayload:
    return {"finishReason": finish_reason, **(terminal_context or {})}


def _terminal_context(response: ChatResponse, terminal_context: EventPayload | None) -> EventPayload:
    payload: EventPayload = dict(terminal_context or {})
    if response.debug and response.reviewTrace:
        payload["reviewTrace"] = response.reviewTrace
    return payload


def _answer_deltas(answer: str, streamer: AnswerDeltaStreamer | None) -> Iterator[str]:
    if streamer is None:
        yield answer
        return
    for delta in streamer(answer):
        if not delta:
            continue
        yield delta


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


def _event(event: dict[str, Any]) -> str:
    safe_data = _sanitize_event_value(event)
    return f"event: {safe_data['type']}\ndata: {json.dumps(safe_data, ensure_ascii=False)}\n\n"


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


def _progress_text(request: ChatRequest) -> str:
    if isinstance(request.message, CandidateSelectedMessage):
        return "正在根据你的选择继续查询。"
    text = request.message.content
    if any(keyword in text for keyword in ("化验", "检验", "质量")):
        return "正在确认产品和化验日期。"
    if any(keyword in text for keyword in ("库位", "仓库", "容量")):
        return "正在确认库位范围。"
    if any(keyword in text for keyword in ("托盘", "码")):
        return "正在确认托盘范围。"
    if any(keyword in text for keyword in ("库存", "剩余", "还有", "查")):
        return "正在确认产品范围。"
    return "正在理解问题并准备查询。"
