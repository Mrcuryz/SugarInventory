from __future__ import annotations

from typing import Annotated, Any, Literal

from pydantic import BaseModel, ConfigDict, Field, model_validator


EntityTypeV1 = Literal[
    "PRODUCT",
    "WAREHOUSE",
    "PRODUCTION_ORDER",
    "BOILING_BATCH",
    "PALLET",
    "PALLET_TASK",
    "ASSAY_RECORD",
]


class GoalEntityMentionV1(BaseModel):
    """A non-authoritative entity mention produced by the model."""

    model_config = ConfigDict(extra="forbid")

    entityType: EntityTypeV1
    mentionText: str = Field(min_length=1, max_length=100)
    referenceKind: Literal["EXPLICIT", "PRONOUN", "CONTEXT_REUSE"]


class GoalDraftV1(BaseModel):
    """Constrained semantic proposal; never an executable plan."""

    model_config = ConfigDict(extra="forbid")

    schemaVersion: Literal["1.0"] = "1.0"
    goalType: Literal[
        "CURRENT_PRODUCT_INVENTORY",
        "PRODUCT_INVENTORY_DISTRIBUTION",
        "WAREHOUSE_INVENTORY_DISTRIBUTION",
        "WAREHOUSE_INVENTORY_WITH_LATEST_ASSAY",
        "CURRENT_PENDING_TASKS",
        "OUT_OF_SLICE",
        "UNSUPPORTED",
        "UNCLEAR",
    ]
    requestedOutcome: str = Field(min_length=1, max_length=300)
    entityMentions: list[GoalEntityMentionV1] = Field(default_factory=list, max_length=6)
    contextReuse: list[EntityTypeV1] = Field(default_factory=list, max_length=6)
    missingEntities: list[EntityTypeV1] = Field(default_factory=list, max_length=6)
    dataNeed: Literal["NONE", "FRESH_READ", "REUSE_CONFIRMED_FACTS", "CLARIFICATION"]
    presentationPreference: Literal["DEFAULT", "SUMMARY", "DETAIL", "NEXT_PAGE"] = "DEFAULT"
    needsClarification: bool
    clarificationReason: Literal[
        "NONE",
        "MISSING_ENTITY",
        "AMBIGUOUS_ENTITY",
        "INTENT_CONFLICT",
        "UNSUPPORTED_SCOPE",
    ] = "NONE"
    confidence: float = Field(ge=0, le=1)


FactTypeV1 = Literal[
    "CURRENT_PRODUCT_INVENTORY",
    "PRODUCT_INVENTORY_DISTRIBUTION",
    "WAREHOUSE_INVENTORY_DISTRIBUTION",
    "PRODUCT_LATEST_ASSAY",
    "WAREHOUSE_STATUS",
    "CURRENT_PENDING_TASKS",
    "ENTITY_RESOLUTION",
]


class ResultReasoningDraftV1(BaseModel):
    """Model's semantic assessment; Runtime remains the completion authority."""

    model_config = ConfigDict(extra="forbid")

    schemaVersion: Literal["1.0"] = "1.0"
    proposedStatus: Literal[
        "COMPLETE",
        "PARTIAL",
        "NEEDS_CLARIFICATION",
        "UNSUPPORTED",
        "FAILED",
    ]
    answeredUserGoal: bool
    evidenceRefs: list[str] = Field(default_factory=list, max_length=20)
    missingFacts: list[FactTypeV1] = Field(default_factory=list, max_length=10)
    failedFacts: list[FactTypeV1] = Field(default_factory=list, max_length=10)
    contradictions: list[str] = Field(default_factory=list, max_length=10)
    limitations: list[str] = Field(default_factory=list, max_length=10)
    suggestedFollowupActions: list[
        Literal[
            "REUSE_ENTITY",
            "REPLACE_ENTITY",
            "VIEW_DETAILS",
            "NEXT_PAGE",
            "NARROW_SCOPE",
            "RETRY_FAILED_FACT",
            "RETURN_TO_SUMMARY",
            "UNDO_LAST_CONTEXT_CHANGE",
        ]
    ] = Field(default_factory=list, max_length=8)


LlmExpertAgentV1 = Literal[
    "inventory_expert",
    "warehouse_expert",
    "assay_expert",
    "pallet_expert",
    "production_expert",
    "logistics_expert",
    "master_data_expert",
    "administration_expert",
    "audit_expert",
]


class MainAgentDecisionV1(BaseModel):
    """LLM semantic routing proposal; Runtime remains the execution authority."""

    model_config = ConfigDict(extra="forbid")

    schemaVersion: Literal["1.0"] = "1.0"
    action: Literal[
        "DIRECT_ANSWER",
        "ASK_CLARIFICATION",
        "DELEGATE",
        "RUN_REGISTERED_RECIPE",
        "UNSUPPORTED",
    ]
    expertAgent: LlmExpertAgentV1 | None = None
    recipeId: Literal["warehouse_inventory_latest_assay"] | None = None
    answer: str | None = Field(default=None, max_length=1500)
    clarificationPrompt: str | None = Field(default=None, max_length=500)
    semanticReason: Literal[
        "SMALLTALK",
        "CAPABILITY",
        "READ_QUERY",
        "FOLLOWUP_QUERY",
        "CROSS_DOMAIN_RECIPE",
        "MISSING_INFORMATION",
        "UNSUPPORTED_CAPABILITY",
        "SECURITY_REFUSAL",
    ]
    confidence: float = Field(ge=0, le=1)

    @model_validator(mode="before")
    @classmethod
    def normalize_empty_optional_fields(cls, value: Any) -> Any:
        if not isinstance(value, dict):
            return value
        normalized = dict(value)
        for field_name in ("expertAgent", "recipeId", "answer", "clarificationPrompt"):
            field_value = normalized.get(field_name)
            if isinstance(field_value, str) and not field_value.strip():
                normalized[field_name] = None
        return normalized

    @model_validator(mode="after")
    def validate_action_fields(self) -> "MainAgentDecisionV1":
        if self.action == "DELEGATE" and self.expertAgent is None:
            raise ValueError("DELEGATE requires expertAgent")
        if self.action != "DELEGATE" and self.expertAgent is not None:
            raise ValueError("expertAgent is only allowed for DELEGATE")
        if self.action == "RUN_REGISTERED_RECIPE" and self.recipeId is None:
            raise ValueError("RUN_REGISTERED_RECIPE requires recipeId")
        if self.action != "RUN_REGISTERED_RECIPE" and self.recipeId is not None:
            raise ValueError("recipeId is only allowed for RUN_REGISTERED_RECIPE")
        if self.action in {"DIRECT_ANSWER", "UNSUPPORTED"} and not self.answer:
            raise ValueError("answer is required")
        if self.action not in {"DIRECT_ANSWER", "UNSUPPORTED"} and self.answer is not None:
            raise ValueError("answer is not allowed for this action")
        if self.action == "ASK_CLARIFICATION" and not self.clarificationPrompt:
            raise ValueError("ASK_CLARIFICATION requires clarificationPrompt")
        if self.action != "ASK_CLARIFICATION" and self.clarificationPrompt is not None:
            raise ValueError("clarificationPrompt is only allowed for ASK_CLARIFICATION")
        return self


class ExpertLoopDecisionV1(BaseModel):
    """One bounded expert-loop proposal; it never authorizes its own tool call."""

    model_config = ConfigDict(extra="forbid")

    schemaVersion: Literal["1.0"] = "1.0"
    action: Literal[
        "CALL_TOOL",
        "ASK_CLARIFICATION",
        "FINAL_ANSWER",
        "PARTIAL_ANSWER",
        "UNSUPPORTED",
    ]
    toolName: str | None = Field(default=None, min_length=1, max_length=100)
    arguments: dict[str, Any] = Field(default_factory=dict)
    answer: str | None = Field(default=None, max_length=2500)
    clarificationPrompt: str | None = Field(default=None, max_length=500)
    citedObservationIds: list[str] = Field(default_factory=list, max_length=10)
    statusReason: Literal[
        "NEED_FRESH_DATA",
        "NEED_MORE_DATA",
        "MISSING_ENTITY",
        "AMBIGUOUS_ENTITY",
        "ENOUGH_DATA",
        "PARTIAL_DATA",
        "TOOL_FAILED",
        "NO_DATA",
        "UNSUPPORTED_CAPABILITY",
    ]

    @model_validator(mode="before")
    @classmethod
    def normalize_empty_optional_fields(cls, value: Any) -> Any:
        if not isinstance(value, dict):
            return value
        normalized = dict(value)
        for field_name in ("toolName", "answer", "clarificationPrompt"):
            field_value = normalized.get(field_name)
            if isinstance(field_value, str) and not field_value.strip():
                normalized[field_name] = None
        if normalized.get("arguments") is None:
            normalized["arguments"] = {}
        if normalized.get("citedObservationIds") is None:
            normalized["citedObservationIds"] = []
        return normalized

    @model_validator(mode="after")
    def validate_action_fields(self) -> "ExpertLoopDecisionV1":
        if self.action == "CALL_TOOL":
            if self.toolName is None:
                raise ValueError("CALL_TOOL requires toolName")
            if self.answer is not None or self.clarificationPrompt is not None:
                raise ValueError("CALL_TOOL cannot contain answer or clarificationPrompt")
        elif self.toolName is not None or self.arguments:
            raise ValueError("toolName and arguments are only allowed for CALL_TOOL")
        if self.action in {"FINAL_ANSWER", "PARTIAL_ANSWER", "UNSUPPORTED"} and not self.answer:
            raise ValueError("answer is required")
        if self.action not in {"FINAL_ANSWER", "PARTIAL_ANSWER", "UNSUPPORTED"} and self.answer is not None:
            raise ValueError("answer is not allowed for this action")
        if self.action == "ASK_CLARIFICATION" and not self.clarificationPrompt:
            raise ValueError("ASK_CLARIFICATION requires clarificationPrompt")
        if self.action != "ASK_CLARIFICATION" and self.clarificationPrompt is not None:
            raise ValueError("clarificationPrompt is only allowed for ASK_CLARIFICATION")
        if self.action in {"FINAL_ANSWER", "PARTIAL_ANSWER"} and not self.citedObservationIds:
            raise ValueError("grounded answers require citedObservationIds")
        if self.action in {"ASK_CLARIFICATION", "UNSUPPORTED"} and self.citedObservationIds:
            raise ValueError("citedObservationIds are not allowed for this action")
        return self


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
    messageId: str | None = Field(default=None, min_length=1, max_length=64)
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
    fields: list[dict[str, Any]] = Field(default_factory=list)


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
    canonicalProductName: str | None = None
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
    filterSummary: str | None = None


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


class SafeAssayMetric(BaseModel):
    """One allowlisted assay metric prepared for user-facing presentation."""

    model_config = ConfigDict(extra="forbid")

    metricName: str
    actualValueText: str
    standardRangeText: str
    resultLabel: str
    reason: str | None = None


class SafeAssayReport(BaseModel):
    """Display-safe assay report; raw enums, IDs and control refs are excluded."""

    model_config = ConfigDict(extra="forbid")

    hasAssay: bool = True
    productLabel: str
    sampleDate: str | None = None
    judgeLabel: str
    judgeExplanation: str | None = None
    standardLabel: str
    metrics: list[SafeAssayMetric] = Field(default_factory=list)
    notes: list[str] = Field(default_factory=list)


class SafePalletTaskRecord(BaseModel):
    """One display-safe pallet task; raw status enums and database IDs are excluded."""

    model_config = ConfigDict(extra="forbid")

    taskTypeLabel: str
    taskStatusLabel: str
    palletCode: str
    productLabel: str
    businessSceneLabel: str | None = None
    productStatusLabel: str | None = None
    targetLocationLabel: str | None = None
    totalWeightText: str | None = None
    productionDate: str | None = None
    screenMeshLabel: str | None = None
    semiItemCountText: str | None = None
    operationBatchLabel: str | None = None
    productionOrderLabel: str | None = None
    productionOrderStatusLabel: str | None = None
    productionLabelBatchLabel: str | None = None
    createdSummary: str | None = None
    confirmationSummary: str | None = None


class SafePalletTaskResult(BaseModel):
    """Display-safe task page and its user-facing filter summary."""

    model_config = ConfigDict(extra="forbid")

    scopeLabel: str
    total: int
    page: int
    size: int
    filterLabels: list[str] = Field(default_factory=list)
    records: list[SafePalletTaskRecord] = Field(default_factory=list)
    limitations: list[str] = Field(default_factory=list)


class ChatResponse(BaseModel):
    model_config = ConfigDict(extra="forbid")

    agentSessionId: str
    answer: str
    needsUserSelection: bool = False
    cards: list[BusinessCard] = Field(default_factory=list)
    suggestions: list[str] = Field(default_factory=list)
    reviewTrace: dict[str, Any] | None = None
    debug: dict[str, Any] | None = None
    error: AgentError | None = None


class HealthResponse(BaseModel):
    model_config = ConfigDict(extra="forbid")

    status: str
    service: str
    version: str
    dependencies: dict[str, str]
