from __future__ import annotations


class RagRuntimeError(RuntimeError):
    """Fail-closed RAG runtime error with a response-safe reason code."""

    def __init__(self, code: str, message: str):
        super().__init__(message)
        self.code = code
