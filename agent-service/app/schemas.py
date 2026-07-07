from __future__ import annotations

from typing import Annotated, Any, Literal

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


class CandidateSelection(BaseModel):
    model_config = ConfigDict(extra="forbid")

    optionId: str | None = Field(default=None, max_length=100)
    optionType: str | None = Field(default=None, max_length=100)
    displayLabel: str | None = Field(default=None, max_length=200)


class CandidateSelectedMessage(BaseModel):
    model_config = ConfigDict(extra="forbid")

    type: Literal["candidate_selected"]
    selection: CandidateSelection
    interruptId: str | None = Field(default=None, max_length=100)
    resumeToken: str | None = Field(default=None, max_length=200)
    action: Literal["SELECT_OPTION", "CANCEL"] = "SELECT_OPTION"
    clientRequestId: str | None = Field(default=None, max_length=100)


AgentMessage = Annotated[ChatMessage | CandidateSelectedMessage, Field(discriminator="type")]


class UserSummary(BaseModel):
    model_config = ConfigDict(extra="allow")

    userId: int | None = None
    name: str | None = Field(default=None, max_length=100)
    roleCode: str | None = Field(default=None, max_length=100)
    permissionCodes: list[str] = Field(default_factory=list)


class ChatRequest(BaseModel):
    model_config = ConfigDict(extra="forbid")

    agentSessionId: str = Field(min_length=1, max_length=64)
    messageId: str | None = Field(default=None, min_length=1, max_length=64)
    user: UserSummary | None = None
    scopes: list[str] = Field(default_factory=list)
    message: AgentMessage
    pageContext: dict[str, Any] = Field(default_factory=dict)
    client: ClientContext = Field(default_factory=ClientContext)


class ResumeEvent(BaseModel):
    model_config = ConfigDict(extra="forbid")

    type: Literal["candidate_selected"] = "candidate_selected"
    interruptId: str | None = Field(default=None, max_length=100)
    action: Literal["SELECT_OPTION", "CANCEL"] = "SELECT_OPTION"
    selection: CandidateSelection
    clientRequestId: str | None = Field(default=None, max_length=100)


class ResumeRequest(BaseModel):
    model_config = ConfigDict(extra="forbid")

    agentSessionId: str = Field(min_length=1, max_length=64)
    resumeToken: str | None = Field(default=None, max_length=200)
    user: UserSummary | None = None
    event: ResumeEvent
    client: ClientContext = Field(default_factory=ClientContext)


class CancelRequest(BaseModel):
    model_config = ConfigDict(extra="forbid")

    agentSessionId: str = Field(min_length=1, max_length=64)
    messageId: str = Field(min_length=1, max_length=64)


class CancelResponse(BaseModel):
    model_config = ConfigDict(extra="forbid")

    agentSessionId: str
    messageId: str
    cancelled: bool


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
    interruptId: str | None = None
    interruptKind: str | None = None
    resumeToken: str | None = None
    expiresAt: str | None = None
    options: list[UserOption] = Field(default_factory=list)
    fields: list[dict[str, str]] = Field(default_factory=list)


class AgentError(BaseModel):
    model_config = ConfigDict(extra="forbid")

    code: str
    message: str
    retryable: bool = False


class SafeInventoryLocation(BaseModel):
    """Allowlisted inventory distribution data safe for answer generation."""

    model_config = ConfigDict(extra="forbid")

    warehouseName: str
    displayStockInfo: str | None = None
    totalEquivalentPieces: int | float | str | None = None
    totalWeight: int | float | str | None = None


class SafeInventoryResult(BaseModel):
    """Allowlisted inventory data; raw gateway objects must not reach formatters."""

    model_config = ConfigDict(extra="forbid")

    displayLabel: str | None = None
    displayStockInfo: str | None = None
    normalizedPallets: int | float | str | None = None
    normalizedLoosePieces: int | float | str | None = None
    totalEquivalentPieces: int | float | str | None = None
    totalWeight: int | float | str | None = None
    isEmpty: bool = False
    locations: list[SafeInventoryLocation] = Field(default_factory=list)


class SafeInventoryDistributionGroup(BaseModel):
    model_config = ConfigDict(extra="forbid")

    groupLabel: str
    warehouseLabel: str | None = None
    productLabel: str | None = None
    stockText: str
    totalEquivalentPieces: int | float | str
    palletCount: int | float | str
    warehouseCount: int | float | str = 0
    productCount: int | float | str = 0
    percentageText: str
    latestInboundTime: str | None = None
    riskLabels: list[str] = Field(default_factory=list)


class SafeInventoryDistributionResult(BaseModel):
    """Allowlisted distribution result; internal IDs and raw rows are excluded."""

    model_config = ConfigDict(extra="forbid")

    scopeLabel: str
    productLabel: str
    groupBy: Literal["warehouse", "product", "warehouse_product"]
    totalStockText: str
    totalEquivalentPieces: int | float | str
    totalWeightText: str | None = None
    warehouseCount: int | float | str
    productCount: int | float | str
    palletCount: int | float | str
    groups: list[SafeInventoryDistributionGroup] = Field(default_factory=list)
    notes: list[str] = Field(default_factory=list)


class SafeWarehouseResult(BaseModel):
    """Allowlisted warehouse data; internal IDs and raw nested objects are excluded."""

    model_config = ConfigDict(extra="forbid")

    warehouseName: str | None = None
    status: str | None = None
    maxCapacity: int | float | str | None = None
    currentOccupancy: int | float | str | None = None
    currentPallets: int | float | str | None = None
    occupancyRate: int | float | str | None = None
    remainingCapacity: int | float | str | None = None


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
