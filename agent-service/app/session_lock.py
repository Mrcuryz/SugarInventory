from __future__ import annotations

from collections.abc import Callable, Iterator
from contextlib import contextmanager
import secrets
from threading import Event, RLock, Thread
import time
from typing import Any, Protocol


class SessionLockTimeout(TimeoutError):
    pass


class SessionLock(Protocol):
    @contextmanager
    def hold(self, session_id: str) -> Iterator[None]:
        ...


class LocalSessionLock:
    def __init__(self, *, wait_timeout_seconds: float = 5.0) -> None:
        self._wait_timeout_seconds = wait_timeout_seconds
        self._locks: dict[str, RLock] = {}
        self._guard = RLock()

    @contextmanager
    def hold(self, session_id: str) -> Iterator[None]:
        with self._guard:
            lock = self._locks.setdefault(session_id, RLock())
        acquired = lock.acquire(timeout=self._wait_timeout_seconds)
        if not acquired:
            raise SessionLockTimeout("session lock wait timeout")
        try:
            yield
        finally:
            lock.release()


class RedisSessionLock:
    _RENEW_SCRIPT = """
if redis.call('get', KEYS[1]) == ARGV[1] then
  return redis.call('pexpire', KEYS[1], ARGV[2])
end
return 0
"""
    _RELEASE_SCRIPT = """
if redis.call('get', KEYS[1]) == ARGV[1] then
  return redis.call('del', KEYS[1])
end
return 0
"""

    def __init__(
        self,
        client: Any,
        *,
        namespace: str = "warehouse-agent",
        wait_timeout_seconds: float = 5.0,
        lease_ms: int = 30000,
        poll_interval_seconds: float = 0.05,
        wait_observer: Callable[[float], None] | None = None,
    ) -> None:
        if lease_ms < 1000:
            raise ValueError("redis session lock lease must be at least 1000ms")
        self._client = client
        self._namespace = namespace
        self._wait_timeout_seconds = wait_timeout_seconds
        self._lease_ms = lease_ms
        self._poll_interval_seconds = poll_interval_seconds
        self._wait_observer = wait_observer

    @contextmanager
    def hold(self, session_id: str) -> Iterator[None]:
        key = f"{self._namespace}:lock:{session_id}"
        token = secrets.token_urlsafe(24)
        started = time.monotonic()
        while not self._client.set(key, token, nx=True, px=self._lease_ms):
            waited = time.monotonic() - started
            if waited >= self._wait_timeout_seconds:
                if self._wait_observer:
                    self._wait_observer(waited)
                raise SessionLockTimeout("redis session lock wait timeout")
            time.sleep(self._poll_interval_seconds)
        waited = time.monotonic() - started
        if self._wait_observer:
            self._wait_observer(waited)

        stop = Event()
        renewer = Thread(
            target=self._renew_until_stopped,
            args=(key, token, stop),
            name="agent-session-lock-renewer",
            daemon=True,
        )
        renewer.start()
        try:
            yield
        finally:
            stop.set()
            renewer.join(timeout=max(1.0, self._lease_ms / 1000))
            self._client.eval(self._RELEASE_SCRIPT, 1, key, token)

    def _renew_until_stopped(self, key: str, token: str, stop: Event) -> None:
        interval = max(0.25, self._lease_ms / 3000)
        while not stop.wait(interval):
            renewed = self._client.eval(self._RENEW_SCRIPT, 1, key, token, self._lease_ms)
            if not renewed:
                return
