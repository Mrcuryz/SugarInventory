from __future__ import annotations

from collections.abc import Iterator
from contextlib import contextmanager
from contextvars import ContextVar
from dataclasses import dataclass


@dataclass(frozen=True)
class AgentExecutionContext:
    """Immutable least-privilege boundary for one chat or resume execution."""

    agent_name: str
    allowed_tools: frozenset[str]
    business_domain: str | None = None
    handoff_mode: str = "direct"
    handoff_id: str | None = None
    plan_id: str | None = None
    step_id: str | None = None

    def authorizes(self, tool_name: str) -> bool:
        return tool_name in self.allowed_tools


_CURRENT_EXECUTION_CONTEXT: ContextVar[AgentExecutionContext | None] = ContextVar(
    "agent_execution_context",
    default=None,
)


def current_execution_context() -> AgentExecutionContext | None:
    return _CURRENT_EXECUTION_CONTEXT.get()


@contextmanager
def bind_execution_context(context: AgentExecutionContext) -> Iterator[AgentExecutionContext]:
    token = _CURRENT_EXECUTION_CONTEXT.set(context)
    try:
        yield context
    finally:
        _CURRENT_EXECUTION_CONTEXT.reset(token)
