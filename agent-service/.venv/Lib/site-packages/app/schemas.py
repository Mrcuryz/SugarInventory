from __future__ import annotations

from typing import Any, Literal

from pydantic import BaseModel, ConfigDict, Field


class ClientContext(BaseModel):
    model_config = ConfigDict(extra="forbid")

    traceId: str | None = Field(default=None, max_length=100)
    requestId: str | None = Field(default=None, max_length=100)
    debug: bool = False


class ChatMessage(BaseModel):
    model_config = ConfigDict(extra="forbid")

    type: Literal["user_message"] = "user_message"
    content: str = Field(min_length=1, max_length=1000)


class UserSummary(BaseModel):
    model_config = ConfigDict(extra="allow")

    userId: int | None = None
    name: str | None = Field(default=None, max_length=100)
    roleCode: str | None = Field(default=None, max_length=100)
    permissionCodes: list[str] = Field(default_factory=list)


class ChatRequest(BaseModel):
    model_config = ConfigDict(extra="forbid")

    agentSessionId: str = Field(min_length=1, max_length=64)
    user: UserSummary | None = None
    scopes: list[str] = Field(default_factory=list)
    message: ChatMessage
    pageContext: dict[str, Any] = Field(default_factory=dict)
    client: ClientContext = Field(default_factory=ClientContext)


class CandidateSelection(BaseModel):
    model_config = ConfigDict(extra="forbid")

    optionId: str | None = Field(default=None, max_length=100)
    optionType: str | None = Field(default=None, max_length=100)
    displayLabel: str | None = Field(default=None, max_length=200)


class ResumeEvent(BaseModel):
    model_config = ConfigDict(extra="forbid")

    type: Literal["candidate_selected"]
    selection: CandidateSelection


class ResumeRequest(BaseModel):
    model_config = ConfigDict(extra="forbid")

    agentSessionId: str = Field(min_length=1, max_length=64)
    resumeToken: str | None = Field(default=None, max_length=100)
    event: ResumeEvent
    client: ClientContext = Field(default_factory=ClientContext)


class UserOption(BaseModel):
    model_config = ConfigDict(extra="forbid")

    optionId: str
    optionType: str
    displayLabel: str
    description: str | None = None
    supported: bool = True
    disabledReason: str | None = None


class BusinessCard(BaseModel):
    model_config = ConfigDict(extra="forbid")

    cardType: str
    title: str
    prompt: str | None = None
    options: list[UserOption] = Field(default_factory=list)
    fields: list[dict[str, str]] = Field(default_factory=list)


class AgentError(BaseModel):
    model_config = ConfigDict(extra="forbid")

    code: str
    message: str
    retryable: bool = False


class ChatResponse(BaseModel):
    model_config = ConfigDict(extra="forbid")

    agentSessionId: str
    answer: str
    needsUserSelection: bool = False
    cards: list[BusinessCard] = Field(default_factory=list)
    suggestions: list[str] = Field(default_factory=list)
    debug: dict[str, Any] | None = None
    error: AgentError | None = None


class HealthResponse(BaseModel):
    model_config = ConfigDict(extra="forbid")

    status: str
    service: str
    version: str
    dependencies: dict[str, str]

