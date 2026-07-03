from __future__ import annotations

from contextvars import ContextVar
from threading import Event, Lock
from time import monotonic


class RunCancelledError(Exception):
    def __init__(self, reason: str = "CLIENT_CANCELLED") -> None:
        self.reason = reason
        super().__init__(reason)


class CancellationToken:
    def __init__(self, agent_session_id: str, message_id: str, timeout_ms: int | None = None) -> None:
        self.agent_session_id = agent_session_id
        self.message_id = message_id
        self._cancelled = Event()
        self._reason = "CLIENT_CANCELLED"
        self._deadline = monotonic() + timeout_ms / 1000 if timeout_ms else None

    def cancel(self, reason: str = "CLIENT_CANCELLED") -> None:
        self._reason = reason
        self._cancelled.set()

    @property
    def reason(self) -> str:
        if self._deadline is not None and monotonic() > self._deadline and not self._cancelled.is_set():
            return "PYTHON_TIMEOUT"
        return self._reason

    def is_cancelled(self) -> bool:
        return self._cancelled.is_set() or (
            self._deadline is not None and monotonic() > self._deadline
        )

    def raise_if_cancelled(self) -> None:
        if self.is_cancelled():
            raise RunCancelledError(self.reason)


_CURRENT_TOKEN: ContextVar[CancellationToken | None] = ContextVar("agent_cancellation_token", default=None)


def current_cancellation_token() -> CancellationToken | None:
    return _CURRENT_TOKEN.get()


def set_current_cancellation_token(token: CancellationToken | None):
    return _CURRENT_TOKEN.set(token)


def reset_current_cancellation_token(context_token) -> None:
    _CURRENT_TOKEN.set(None)


class AgentRunRegistry:
    def __init__(self) -> None:
        self._active: dict[tuple[str, str], CancellationToken] = {}
        self._pending_cancelled: set[tuple[str, str]] = set()
        self._lock = Lock()

    def start(self, agent_session_id: str, message_id: str, timeout_ms: int | None = None) -> CancellationToken:
        key = (agent_session_id, message_id)
        token = CancellationToken(agent_session_id, message_id, timeout_ms)
        with self._lock:
            if key in self._pending_cancelled:
                token.cancel("CLIENT_CANCELLED")
            self._active[key] = token
        return token

    def cancel(self, agent_session_id: str, message_id: str) -> None:
        key = (agent_session_id, message_id)
        with self._lock:
            token = self._active.get(key)
            if token is not None:
                token.cancel("CLIENT_CANCELLED")
            else:
                self._pending_cancelled.add(key)

    def is_cancelled(self, agent_session_id: str, message_id: str) -> bool:
        key = (agent_session_id, message_id)
        with self._lock:
            token = self._active.get(key)
            return token.is_cancelled() if token else key in self._pending_cancelled

    def finish(self, agent_session_id: str, message_id: str) -> None:
        key = (agent_session_id, message_id)
        with self._lock:
            self._active.pop(key, None)
            self._pending_cancelled.discard(key)


class CancellationRegistry(AgentRunRegistry):
    """Backward-compatible name kept for older tests/imports."""

    def __init__(self) -> None:
        super().__init__()

    def clear(self, agent_session_id: str, message_id: str) -> None:
        self.finish(agent_session_id, message_id)
