from __future__ import annotations

from datetime import date, datetime
from enum import Enum
from pathlib import PurePosixPath
from typing import Annotated, Any

from pydantic import (
    BaseModel,
    ConfigDict,
    Field,
    StringConstraints,
    field_validator,
    model_validator,
)

from app.rag.config import ALLOWED_ROLES, CORPUS_ID, SCHEMA_VERSION, SOURCE_BASIS, TIMEZONE


Sha256 = Annotated[str, StringConstraints(pattern=r"^[0-9a-f]{64}$")]
StableId = Annotated[str, StringConstraints(pattern=r"^[a-z0-9][a-z0-9._/-]{2,127}$")]
CorpusVersion = Annotated[
    str,
    StringConstraints(pattern=r"^[a-z0-9][a-z0-9._-]{2,79}$"),
]


class RagContract(BaseModel):
    model_config = ConfigDict(
        extra="forbid",
        frozen=True,
        str_strip_whitespace=True,
        validate_default=True,
    )


class SourceType(str, Enum):
    DOCX = "DOCX"
    PDF = "PDF"


class CorpusStatus(str, Enum):
    BUILDING = "BUILDING"
    VALIDATED = "VALIDATED"
    ACTIVE = "ACTIVE"
    FAILED = "FAILED"


class DocumentStatus(str, Enum):
    DRAFT = "DRAFT"
    ACTIVE = "ACTIVE"
    INACTIVE = "INACTIVE"


class DocumentType(str, Enum):
    PROCESS_FLOW = "PROCESS_FLOW"
    COMPANY_PROFILE = "COMPANY_PROFILE"
    PRODUCT_BROCHURE = "PRODUCT_BROCHURE"
    HONOR_AND_CERTIFICATION = "HONOR_AND_CERTIFICATION"
    SALES_NETWORK = "SALES_NETWORK"


class KnowledgeDomain(str, Enum):
    PROCESS = "PROCESS"
    COMPANY = "COMPANY"
    PRODUCT_MARKETING = "PRODUCT_MARKETING"
    CERTIFICATION = "CERTIFICATION"
    SALES = "SALES"


class PageSectionKind(str, Enum):
    COVER = "COVER"
    CATALOG = "CATALOG"
    COMPANY_PROFILE = "COMPANY_PROFILE"
    FACILITY = "FACILITY"
    COMPANY_HONOR = "COMPANY_HONOR"
    PRODUCT_INTRODUCTION = "PRODUCT_INTRODUCTION"
    GIFT_PRODUCT = "GIFT_PRODUCT"
    SALES_NETWORK = "SALES_NETWORK"
    CERTIFICATION = "CERTIFICATION"
    CLOSING = "CLOSING"
    PROCESS_FLOW = "PROCESS_FLOW"
    SOURCE_BLANK_PAGE = "SOURCE_BLANK_PAGE"


class ChunkType(str, Enum):
    FLOW_OVERVIEW = "FLOW_OVERVIEW"
    PROCESS_STEP = "PROCESS_STEP"
    CONTROL_POINT = "CONTROL_POINT"
    MATERIAL_BRANCH = "MATERIAL_BRANCH"
    COMPANY_SECTION = "COMPANY_SECTION"
    PRODUCT_SECTION = "PRODUCT_SECTION"
    CERTIFICATION_SECTION = "CERTIFICATION_SECTION"
    SALES_SECTION = "SALES_SECTION"


class BuildStatus(str, Enum):
    NOT_STARTED = "NOT_STARTED"
    SUCCEEDED = "SUCCEEDED"
    FAILED = "FAILED"
    REQUIRES_REVIEW = "REQUIRES_REVIEW"


class RetrievalMode(str, Enum):
    LEXICAL = "LEXICAL"
    VECTOR = "VECTOR"
    HYBRID = "HYBRID"


class RetrievalStatus(str, Enum):
    SUCCEEDED = "SUCCEEDED"
    NO_DATA = "NO_DATA"
    FORBIDDEN = "FORBIDDEN"
    UNAVAILABLE = "UNAVAILABLE"
    DEGRADED = "DEGRADED"
    INVALID_QUERY = "INVALID_QUERY"


class QualitySeverity(str, Enum):
    INFO = "INFO"
    WARNING = "WARNING"
    BLOCKING = "BLOCKING"


def _validate_allowed_roles(roles: tuple[str, ...]) -> tuple[str, ...]:
    normalized = tuple(role.strip().upper() for role in roles)
    if not normalized:
        raise ValueError("allowedRoles must not be empty")
    if len(normalized) != len(set(normalized)):
        raise ValueError("allowedRoles must not contain duplicates")
    unsupported = set(normalized).difference(ALLOWED_ROLES)
    if unsupported:
        raise ValueError("allowedRoles contains unsupported roles")
    return normalized


def _validate_aware_datetime(value: datetime) -> datetime:
    if value.tzinfo is None or value.utcoffset() is None:
        raise ValueError("datetime must include a timezone")
    return value


class SourceFileRecord(RagContract):
    documentId: StableId
    relativePath: str = Field(min_length=1, max_length=500)
    sourceFileName: str = Field(min_length=1, max_length=255)
    sourceType: SourceType
    sizeBytes: int = Field(ge=0)
    sourceSha256: Sha256

    @field_validator("relativePath")
    @classmethod
    def validate_relative_path(cls, value: str) -> str:
        normalized = value.replace("\\", "/")
        path = PurePosixPath(normalized)
        if path.is_absolute() or ".." in path.parts or "." in path.parts:
            raise ValueError("relativePath must stay within the source root")
        if normalized.startswith("/") or ":" in path.parts[0]:
            raise ValueError("relativePath must not be absolute")
        return path.as_posix()

    @model_validator(mode="after")
    def validate_file_name_and_type(self) -> SourceFileRecord:
        path = PurePosixPath(self.relativePath)
        if path.name != self.sourceFileName:
            raise ValueError("sourceFileName must match relativePath")
        expected_suffix = f".{self.sourceType.value.lower()}"
        if path.suffix.lower() != expected_suffix:
            raise ValueError("sourceType must match the file extension")
        return self


class SourceInventoryManifest(RagContract):
    schemaVersion: int = Field(default=SCHEMA_VERSION, ge=1)
    corpusId: str = Field(default=CORPUS_ID, min_length=3, max_length=100)
    corpusVersion: CorpusVersion
    sourceBasis: str = Field(default=SOURCE_BASIS, min_length=3, max_length=100)
    generatedAt: datetime
    timezone: str = Field(default=TIMEZONE, min_length=1, max_length=100)
    builderVersion: str = Field(min_length=1, max_length=100)
    fileCount: int = Field(ge=0)
    totalBytes: int = Field(ge=0)
    sourceTypeCounts: dict[SourceType, int]
    files: tuple[SourceFileRecord, ...]
    ignoredFileCount: int = Field(default=0, ge=0)
    ignoredRelativePaths: tuple[str, ...] = ()
    inventorySha256: Sha256

    _generated_at_timezone = field_validator("generatedAt")(_validate_aware_datetime)

    @model_validator(mode="after")
    def validate_counts_and_order(self) -> SourceInventoryManifest:
        if self.fileCount != len(self.files):
            raise ValueError("fileCount does not match files")
        if self.totalBytes != sum(item.sizeBytes for item in self.files):
            raise ValueError("totalBytes does not match files")
        expected_counts = {
            source_type: sum(item.sourceType == source_type for item in self.files)
            for source_type in SourceType
        }
        actual_counts = {
            source_type: self.sourceTypeCounts.get(source_type, 0)
            for source_type in SourceType
        }
        if actual_counts != expected_counts:
            raise ValueError("sourceTypeCounts does not match files")
        if self.ignoredFileCount != len(self.ignoredRelativePaths):
            raise ValueError("ignoredFileCount does not match ignoredRelativePaths")
        paths = [item.relativePath for item in self.files]
        if paths != sorted(paths, key=lambda item: (item.casefold(), item)):
            raise ValueError("files must use deterministic relativePath order")
        if len(paths) != len(set(paths)):
            raise ValueError("files must not contain duplicate relativePath values")
        document_ids = [item.documentId for item in self.files]
        if len(document_ids) != len(set(document_ids)):
            raise ValueError("files must not contain duplicate documentId values")
        return self


class EmbeddingContract(RagContract):
    provider: str = ""
    providerVersion: str = ""
    model: str = ""
    dimension: int = Field(default=0, ge=0)
    normalization: str = ""
    maxTokens: int = Field(default=0, ge=0)
    modelArtifactSha256: Sha256 | None = None


class ParserVersions(RagContract):
    docx: str = ""
    pdf: str = ""
    ocr: str = ""
    normalizer: str = ""
    chunker: str = ""


class CorpusManifest(RagContract):
    schemaVersion: int = Field(default=SCHEMA_VERSION, ge=1)
    corpusId: str = Field(default=CORPUS_ID, min_length=3, max_length=100)
    corpusVersion: CorpusVersion
    status: CorpusStatus
    sourceBasis: str = Field(default=SOURCE_BASIS, min_length=3, max_length=100)
    builtAt: datetime
    timezone: str = Field(default=TIMEZONE, min_length=1, max_length=100)
    allowedRoles: tuple[str, ...] = ALLOWED_ROLES
    documentCount: int = Field(ge=0)
    chunkCount: int = Field(ge=0)
    documentManifestSha256: Sha256 | None = None
    inventorySha256: Sha256 | None = None
    reviewProfileSha256: Sha256 | None = None
    chunkManifestSha256: Sha256 | None = None
    lexicalIndexSha256: Sha256 | None = None
    vectorIndexSha256: Sha256 | None = None
    embedding: EmbeddingContract = Field(default_factory=EmbeddingContract)
    parserVersions: ParserVersions = Field(default_factory=ParserVersions)

    _built_at_timezone = field_validator("builtAt")(_validate_aware_datetime)
    _allowed_roles = field_validator("allowedRoles")(_validate_allowed_roles)


class ParameterContract(RagContract):
    name: str = Field(min_length=1, max_length=200)
    sourceText: str = Field(min_length=1)
    valueType: str = Field(min_length=1, max_length=50)
    minValue: float | None = None
    maxValue: float | None = None
    unit: str | None = Field(default=None, max_length=50)
    normalizationStatus: str = Field(min_length=1, max_length=50)
    qualityFlags: tuple[str, ...] = ()


class ControlPointContract(RagContract):
    type: str = Field(min_length=1, max_length=20)
    label: str = Field(min_length=1, max_length=50)
    sourceLabel: str = Field(min_length=1, max_length=50)


class ProcessStepContract(RagContract):
    stepId: StableId
    stepNo: str = Field(min_length=1, max_length=50)
    name: str = Field(min_length=1, max_length=300)
    sourceText: str = Field(min_length=1)
    normalizedText: str = Field(min_length=1)
    predecessorStepIds: tuple[StableId, ...] = ()
    successorStepIds: tuple[StableId, ...] = ()
    inputs: tuple[str, ...] = ()
    outputs: tuple[str, ...] = ()
    equipment: tuple[str, ...] = ()
    parameters: tuple[ParameterContract, ...] = ()
    controlPoint: ControlPointContract | None = None
    controlPoints: tuple[ControlPointContract, ...] = ()
    pageNumber: int | None = Field(default=None, ge=1)
    sourceRegion: dict[str, Any] | None = None
    qualityFlags: tuple[str, ...] = ()

    @model_validator(mode="after")
    def validate_control_point_compatibility(self) -> ProcessStepContract:
        if self.controlPoint and self.controlPoints and self.controlPoint not in self.controlPoints:
            raise ValueError("controlPoint must also be present in controlPoints")
        return self


class ProcessContract(RagContract):
    steps: tuple[ProcessStepContract, ...] = ()
    branches: tuple[dict[str, Any], ...] = ()
    controlPoints: tuple[ControlPointContract, ...] = ()


class PageContract(RagContract):
    pageNumber: int = Field(ge=1)
    title: str | None = Field(default=None, max_length=500)
    sourceText: str = ""
    normalizedText: str = ""
    sectionKind: PageSectionKind | None = None
    knowledgeDomains: tuple[KnowledgeDomain, ...] = ()
    productFamilies: tuple[str, ...] = ()
    excludedSourceTexts: tuple[str, ...] = ()
    reviewNotes: tuple[str, ...] = ()
    qualityFlags: tuple[str, ...] = ()


class ExtractionContract(RagContract):
    parserVersion: str = Field(min_length=1, max_length=100)
    processedAt: datetime
    status: BuildStatus
    sourceExtractionSha256: Sha256
    reviewProfileSha256: Sha256
    warnings: tuple[str, ...] = ()

    _processed_at_timezone = field_validator("processedAt")(_validate_aware_datetime)


class DocumentContract(RagContract):
    schemaVersion: int = Field(default=SCHEMA_VERSION, ge=1)
    documentId: StableId
    displayTitle: str = Field(min_length=1, max_length=500)
    sourceInternalTitle: str | None = Field(default=None, max_length=500)
    sourceFileName: str = Field(min_length=1, max_length=255)
    sourceType: SourceType
    documentType: DocumentType
    knowledgeDomain: KnowledgeDomain
    language: str = Field(default="zh-CN", min_length=2, max_length=20)
    status: DocumentStatus
    sourceBasis: str = Field(default=SOURCE_BASIS, min_length=3, max_length=100)
    versionLabel: str | None = Field(default=None, max_length=100)
    effectiveFrom: date | None = None
    effectiveTo: date | None = None
    owner: str | None = Field(default=None, max_length=200)
    reviewer: str | None = Field(default=None, max_length=200)
    approvalStatus: str | None = Field(default=None, max_length=100)
    allowedRoles: tuple[str, ...] = ALLOWED_ROLES
    supersedesDocumentId: StableId | None = None
    supersededByDocumentId: StableId | None = None
    sourceSha256: Sha256
    productFamilies: tuple[str, ...] = ()
    qualityFlags: tuple[str, ...] = ()
    pages: tuple[PageContract, ...] = ()
    process: ProcessContract = Field(default_factory=ProcessContract)
    extraction: ExtractionContract
    documentSha256: Sha256

    _allowed_roles = field_validator("allowedRoles")(_validate_allowed_roles)

    @model_validator(mode="after")
    def validate_effective_range(self) -> DocumentContract:
        if self.effectiveFrom and self.effectiveTo and self.effectiveTo < self.effectiveFrom:
            raise ValueError("effectiveTo must not be earlier than effectiveFrom")
        return self


class RelationshipReviewDecision(RagContract):
    predecessorNodeId: StableId
    successorNodeId: StableId


class TableStepMapping(RagContract):
    processName: str = Field(min_length=1, max_length=300)
    stepNo: str = Field(min_length=1, max_length=50)


class PdfPageReviewDecision(RagContract):
    pageNumber: int = Field(ge=1)
    title: str | None = Field(default=None, max_length=500)
    sectionKind: PageSectionKind
    knowledgeDomains: tuple[KnowledgeDomain, ...]
    productFamilies: tuple[str, ...] = ()
    normalizedText: str = ""
    excludedSourceTexts: tuple[str, ...] = ()
    reviewNotes: tuple[str, ...] = ()
    qualityFlags: tuple[str, ...] = ()


class DocumentReviewDecision(RagContract):
    documentId: StableId
    sourceSha256: Sha256
    sourceExtractionSha256: Sha256
    displayTitle: str = Field(min_length=1, max_length=500)
    sourceInternalTitle: str | None = Field(default=None, max_length=500)
    documentType: DocumentType
    knowledgeDomain: KnowledgeDomain
    versionLabel: str | None = Field(default=None, max_length=100)
    productFamilies: tuple[str, ...] = ()
    approveAllExtractedRelationships: bool = False
    approvedRelationships: tuple[RelationshipReviewDecision, ...] = ()
    tableStepMappings: tuple[TableStepMapping, ...] = ()
    pageReviews: tuple[PdfPageReviewDecision, ...] = ()
    qualityFlags: tuple[str, ...] = ()
    reviewNotes: tuple[str, ...] = ()

    @model_validator(mode="after")
    def validate_relationship_review_mode(self) -> DocumentReviewDecision:
        if self.approveAllExtractedRelationships and self.approvedRelationships:
            raise ValueError(
                "approveAllExtractedRelationships and approvedRelationships are mutually exclusive"
            )
        return self


class NormalizationReviewProfile(RagContract):
    schemaVersion: int = Field(default=SCHEMA_VERSION, ge=1)
    corpusId: str = Field(default=CORPUS_ID, min_length=3, max_length=100)
    corpusVersion: CorpusVersion
    profileVersion: str = Field(min_length=1, max_length=100)
    inventorySha256: Sha256
    decisions: tuple[DocumentReviewDecision, ...]
    reviewProfileSha256: Sha256

    @model_validator(mode="after")
    def validate_decision_ids(self) -> NormalizationReviewProfile:
        document_ids = [decision.documentId for decision in self.decisions]
        if document_ids != sorted(document_ids):
            raise ValueError("review decisions must use deterministic documentId order")
        if len(document_ids) != len(set(document_ids)):
            raise ValueError("review decisions must not contain duplicate documentId values")
        return self


class NormalizedDocumentSummary(RagContract):
    documentId: StableId
    sourceFileName: str = Field(min_length=1, max_length=255)
    sourceExtractionSha256: Sha256
    documentSha256: Sha256
    pageCount: int = Field(ge=1)
    processStepCount: int = Field(ge=0)
    qualityFlags: tuple[str, ...] = ()


class NormalizationReport(RagContract):
    schemaVersion: int = Field(default=SCHEMA_VERSION, ge=1)
    corpusId: str = Field(default=CORPUS_ID, min_length=3, max_length=100)
    corpusVersion: CorpusVersion
    generatedAt: datetime
    status: BuildStatus
    inventorySha256: Sha256
    reviewProfileSha256: Sha256
    expectedDocumentCount: int = Field(ge=0)
    succeededCount: int = Field(ge=0)
    failedCount: int = Field(ge=0)
    documents: tuple[NormalizedDocumentSummary, ...]
    documentManifestSha256: Sha256

    _generated_at_timezone = field_validator("generatedAt")(_validate_aware_datetime)

    @model_validator(mode="after")
    def validate_normalization_counts(self) -> NormalizationReport:
        if self.expectedDocumentCount != len(self.documents):
            raise ValueError("expectedDocumentCount does not match documents")
        if self.succeededCount + self.failedCount != len(self.documents):
            raise ValueError("succeededCount and failedCount do not match documents")
        return self


class CitationContract(RagContract):
    documentTitle: str = Field(min_length=1, max_length=500)
    pageNumber: int | None = Field(default=None, ge=1)
    sectionLabel: str | None = Field(default=None, max_length=200)


class ChunkContract(RagContract):
    schemaVersion: int = Field(default=SCHEMA_VERSION, ge=1)
    chunkId: StableId
    documentId: StableId
    chunkType: ChunkType
    knowledgeDomain: KnowledgeDomain
    title: str = Field(min_length=1, max_length=500)
    content: str = Field(min_length=1)
    sourceText: str = Field(min_length=1)
    productFamilies: tuple[str, ...] = ()
    keywords: tuple[str, ...] = ()
    pageNumber: int | None = Field(default=None, ge=1)
    stepNo: str | None = Field(default=None, max_length=50)
    controlPointLabels: tuple[str, ...] = ()
    qualityFlags: tuple[str, ...] = ()
    status: DocumentStatus
    allowedRoles: tuple[str, ...] = ALLOWED_ROLES
    citation: CitationContract
    sourceSha256: Sha256
    contentSha256: Sha256
    embeddingRef: str | None = Field(default=None, max_length=300)

    _allowed_roles = field_validator("allowedRoles")(_validate_allowed_roles)


class ChunkSummary(RagContract):
    chunkId: StableId
    documentId: StableId
    chunkType: ChunkType
    contentSha256: Sha256
    embeddingRef: str = Field(min_length=1, max_length=300)


class ChunkManifest(RagContract):
    schemaVersion: int = Field(default=SCHEMA_VERSION, ge=1)
    corpusId: str = Field(default=CORPUS_ID, min_length=3, max_length=100)
    corpusVersion: CorpusVersion
    generatedAt: datetime
    chunkerVersion: str = Field(min_length=1, max_length=100)
    documentManifestSha256: Sha256
    documentCount: int = Field(ge=0)
    chunkCount: int = Field(ge=0)
    chunkTypeCounts: dict[ChunkType, int]
    chunks: tuple[ChunkSummary, ...]
    chunkManifestSha256: Sha256

    _generated_at_timezone = field_validator("generatedAt")(_validate_aware_datetime)

    @model_validator(mode="after")
    def validate_chunk_counts(self) -> ChunkManifest:
        if self.chunkCount != len(self.chunks):
            raise ValueError("chunkCount does not match chunks")
        expected = {
            chunk_type: sum(item.chunkType == chunk_type for item in self.chunks)
            for chunk_type in ChunkType
        }
        actual = {
            chunk_type: self.chunkTypeCounts.get(chunk_type, 0)
            for chunk_type in ChunkType
        }
        if actual != expected:
            raise ValueError("chunkTypeCounts does not match chunks")
        chunk_ids = [item.chunkId for item in self.chunks]
        if chunk_ids != sorted(chunk_ids):
            raise ValueError("chunks must use deterministic chunkId order")
        if len(chunk_ids) != len(set(chunk_ids)):
            raise ValueError("chunks must not contain duplicate chunkId values")
        return self


class ChunkingReport(RagContract):
    schemaVersion: int = Field(default=SCHEMA_VERSION, ge=1)
    corpusId: str = Field(default=CORPUS_ID, min_length=3, max_length=100)
    corpusVersion: CorpusVersion
    generatedAt: datetime
    status: BuildStatus
    documentManifestSha256: Sha256
    documentCount: int = Field(ge=0)
    chunkCount: int = Field(ge=0)
    duplicateContentCount: int = Field(default=0, ge=0)
    emptySourceTextCount: int = Field(default=0, ge=0)
    chunkManifestSha256: Sha256

    _generated_at_timezone = field_validator("generatedAt")(_validate_aware_datetime)


class IndexComponentContract(RagContract):
    status: BuildStatus
    implementation: str = Field(min_length=1, max_length=100)
    version: str = Field(min_length=1, max_length=100)
    sha256: Sha256 | None = None
    itemCount: int = Field(default=0, ge=0)
    metadata: dict[str, Any] = Field(default_factory=dict)


class IndexManifest(RagContract):
    schemaVersion: int = Field(default=SCHEMA_VERSION, ge=1)
    corpusId: str = Field(default=CORPUS_ID, min_length=3, max_length=100)
    corpusVersion: CorpusVersion
    builtAt: datetime
    documentCount: int = Field(ge=0)
    chunkCount: int = Field(ge=0)
    documentManifestSha256: Sha256 | None = None
    chunkManifestSha256: Sha256 | None = None
    embedding: EmbeddingContract = Field(default_factory=EmbeddingContract)
    retrieval: dict[str, Any] = Field(default_factory=dict)
    lexical: IndexComponentContract
    vector: IndexComponentContract
    indexManifestSha256: Sha256 | None = None

    _built_at_timezone = field_validator("builtAt")(_validate_aware_datetime)


class RetrievalHit(RagContract):
    rank: int = Field(ge=1)
    chunkId: StableId
    documentId: StableId
    score: float
    citation: CitationContract


class RetrievalResult(RagContract):
    status: RetrievalStatus
    mode: RetrievalMode
    query: str = Field(max_length=1000)
    elapsedMilliseconds: float = Field(ge=0)
    hits: tuple[RetrievalHit, ...] = ()
    reason: str | None = Field(default=None, max_length=500)


class RetrievalEvaluationCase(RagContract):
    schemaVersion: int = Field(default=SCHEMA_VERSION, ge=1)
    caseId: StableId
    category: str = Field(pattern=r"^[A-Z][A-Z0-9_]{2,49}$")
    query: str = Field(min_length=1, max_length=1000)
    mode: RetrievalMode = RetrievalMode.HYBRID
    role: str = Field(default="ADMIN", min_length=1, max_length=50)
    expectedStatus: RetrievalStatus = RetrievalStatus.SUCCEEDED
    expectedDocumentIds: tuple[StableId, ...] = ()
    expectedChunkIds: tuple[StableId, ...] = ()
    forbiddenDocumentIds: tuple[StableId, ...] = ()
    requiredEvidenceTerms: tuple[str, ...] = ()
    topK: int = Field(default=5, ge=1, le=10)
    requiredRank: int = Field(default=5, ge=1, le=10)

    @model_validator(mode="after")
    def validate_expected_target(self) -> RetrievalEvaluationCase:
        if (
            self.expectedStatus == RetrievalStatus.SUCCEEDED
            and not self.expectedDocumentIds
            and not self.expectedChunkIds
        ):
            raise ValueError("successful evaluation cases require an expected target")
        if self.requiredRank > self.topK:
            raise ValueError("requiredRank must not exceed topK")
        return self


class RetrievalEvaluationCaseResult(RagContract):
    caseId: StableId
    category: str = Field(pattern=r"^[A-Z][A-Z0-9_]{2,49}$")
    passed: bool
    actualStatus: RetrievalStatus
    expectedStatus: RetrievalStatus
    elapsedMilliseconds: float = Field(ge=0)
    hitChunkIds: tuple[StableId, ...] = ()
    hitDocumentIds: tuple[StableId, ...] = ()
    targetMatched: bool
    evidenceMatched: bool
    forbiddenHitCount: int = Field(default=0, ge=0)
    failureReasons: tuple[str, ...] = ()


class RetrievalEvaluationReport(RagContract):
    schemaVersion: int = Field(default=SCHEMA_VERSION, ge=1)
    corpusId: str = Field(default=CORPUS_ID, min_length=3, max_length=100)
    corpusVersion: CorpusVersion
    generatedAt: datetime
    status: BuildStatus
    evaluationSetSha256: Sha256
    indexManifestSha256: Sha256
    caseCount: int = Field(ge=0)
    passedCount: int = Field(ge=0)
    failedCount: int = Field(ge=0)
    metrics: dict[str, float | int]
    thresholds: dict[str, float | int]
    failedCaseIds: tuple[StableId, ...] = ()
    cases: tuple[RetrievalEvaluationCaseResult, ...]

    _generated_at_timezone = field_validator("generatedAt")(_validate_aware_datetime)

    @model_validator(mode="after")
    def validate_evaluation_counts(self) -> RetrievalEvaluationReport:
        if self.caseCount != len(self.cases):
            raise ValueError("caseCount does not match cases")
        if self.passedCount + self.failedCount != self.caseCount:
            raise ValueError("passedCount and failedCount do not match caseCount")
        if self.failedCount != len(self.failedCaseIds):
            raise ValueError("failedCount does not match failedCaseIds")
        return self


class QualityIssue(RagContract):
    code: str = Field(pattern=r"^[A-Z][A-Z0-9_]{2,79}$")
    severity: QualitySeverity
    message: str = Field(min_length=1, max_length=1000)
    documentId: StableId | None = None
    relativePath: str | None = Field(default=None, max_length=500)
    pageNumber: int | None = Field(default=None, ge=1)
    stepNo: str | None = Field(default=None, max_length=50)


class QualityReport(RagContract):
    schemaVersion: int = Field(default=SCHEMA_VERSION, ge=1)
    corpusVersion: CorpusVersion
    generatedAt: datetime
    status: BuildStatus
    documentCount: int = Field(ge=0)
    blockingIssueCount: int = Field(ge=0)
    warningIssueCount: int = Field(ge=0)
    issues: tuple[QualityIssue, ...] = ()

    _generated_at_timezone = field_validator("generatedAt")(_validate_aware_datetime)

    @model_validator(mode="after")
    def validate_issue_counts(self) -> QualityReport:
        blocking = sum(issue.severity == QualitySeverity.BLOCKING for issue in self.issues)
        warnings = sum(issue.severity == QualitySeverity.WARNING for issue in self.issues)
        if self.blockingIssueCount != blocking or self.warningIssueCount != warnings:
            raise ValueError("quality issue counts do not match issues")
        return self


class DocxBlockType(str, Enum):
    PARAGRAPH = "PARAGRAPH"
    TABLE = "TABLE"
    TEXT_BOX = "TEXT_BOX"


class CompatibilityBranch(str, Enum):
    DIRECT = "DIRECT"
    CHOICE = "CHOICE"


class FlowNodeKind(str, Enum):
    PROCESS_STEP = "PROCESS_STEP"
    MATERIAL = "MATERIAL"
    PACKAGING_MATERIAL = "PACKAGING_MATERIAL"
    STORAGE = "STORAGE"
    BYPRODUCT = "BYPRODUCT"
    PROCESS_DESCRIPTION = "PROCESS_DESCRIPTION"
    OTHER = "OTHER"


class RelationshipEvidence(str, Enum):
    STEP_NUMBER_SEQUENCE = "STEP_NUMBER_SEQUENCE"


class VisualValidationStatus(str, Enum):
    NOT_PERFORMED = "NOT_PERFORMED"
    UNAVAILABLE = "UNAVAILABLE"
    SUCCEEDED = "SUCCEEDED"
    FAILED = "FAILED"


class LayoutGeometry(RagContract):
    xEmu: int | None = None
    yEmu: int | None = None
    widthEmu: int | None = Field(default=None, ge=0)
    heightEmu: int | None = Field(default=None, ge=0)
    horizontalRelativeFrom: str | None = Field(default=None, max_length=50)
    verticalRelativeFrom: str | None = Field(default=None, max_length=50)
    rawStyle: str | None = Field(default=None, max_length=2000)


class ExtractedParameter(RagContract):
    sourceText: str = Field(min_length=1, max_length=200)
    valueType: str = Field(pattern=r"^(SINGLE|RANGE)$")
    minValue: str = Field(min_length=1, max_length=100)
    maxValue: str | None = Field(default=None, max_length=100)
    unit: str = Field(min_length=1, max_length=50)
    contextText: str = Field(min_length=1, max_length=500)


class DocxTextBlock(RagContract):
    blockId: StableId
    blockType: DocxBlockType
    sequence: int = Field(ge=1)
    sourceBranch: CompatibilityBranch
    text: str = Field(min_length=1)
    paragraphs: tuple[str, ...] = ()
    tableRows: tuple[tuple[str, ...], ...] = ()
    shapeId: str | None = Field(default=None, max_length=200)
    shapeName: str | None = Field(default=None, max_length=500)
    geometry: LayoutGeometry | None = None
    controlPointLabels: tuple[str, ...] = ()
    parameters: tuple[ExtractedParameter, ...] = ()
    qualityFlags: tuple[str, ...] = ()


class DocxFlowNode(RagContract):
    nodeId: StableId
    blockId: StableId
    nodeKind: FlowNodeKind
    stepNo: str | None = Field(default=None, max_length=50)
    name: str = Field(min_length=1, max_length=500)
    sourceText: str = Field(min_length=1)
    controlPointLabels: tuple[str, ...] = ()
    parameters: tuple[ExtractedParameter, ...] = ()
    geometry: LayoutGeometry | None = None
    qualityFlags: tuple[str, ...] = ()


class DocxConnector(RagContract):
    connectorId: StableId
    sequence: int = Field(ge=1)
    presetGeometry: str = Field(min_length=1, max_length=100)
    shapeId: str | None = Field(default=None, max_length=200)
    shapeName: str | None = Field(default=None, max_length=500)
    geometry: LayoutGeometry | None = None
    rawFrom: str | None = Field(default=None, max_length=100)
    rawTo: str | None = Field(default=None, max_length=100)
    startArrow: str | None = Field(default=None, max_length=100)
    endArrow: str | None = Field(default=None, max_length=100)
    fromNodeId: StableId | None = None
    toNodeId: StableId | None = None
    qualityFlags: tuple[str, ...] = ("FLOW_RELATION_REQUIRES_REVIEW",)


class DocxRelationshipCandidate(RagContract):
    candidateId: StableId
    predecessorNodeId: StableId
    successorNodeId: StableId
    evidence: RelationshipEvidence
    requiresVisualReview: bool = True
    qualityFlags: tuple[str, ...] = ("FLOW_RELATION_REQUIRES_REVIEW",)


class DocxPackageInfo(RagContract):
    entryCount: int = Field(ge=1)
    totalUncompressedBytes: int = Field(ge=1)
    externalRelationshipCount: int = Field(ge=0)
    sectionCount: int = Field(ge=1)


class DocxExtractionMetrics(RagContract):
    rawTextBoxCount: int = Field(ge=0)
    selectedTextBoxCount: int = Field(ge=0)
    fallbackDuplicateCount: int = Field(ge=0)
    paragraphBlockCount: int = Field(ge=0)
    tableBlockCount: int = Field(ge=0)
    connectorCount: int = Field(ge=0)
    flowNodeCount: int = Field(ge=0)
    controlPointCount: int = Field(ge=0)
    parameterCount: int = Field(ge=0)


class DocxRenderedPage(RagContract):
    pageNumber: int = Field(ge=1)
    renderedArtifact: str = Field(min_length=1, max_length=500)
    renderedSha256: Sha256
    pixelWidth: int = Field(ge=1)
    pixelHeight: int = Field(ge=1)
    blank: bool = False


class DocxVisualValidation(RagContract):
    status: VisualValidationStatus = VisualValidationStatus.NOT_PERFORMED
    renderer: str | None = Field(default=None, max_length=100)
    rendererVersion: str | None = Field(default=None, max_length=500)
    renderedPageCount: int | None = Field(default=None, ge=1)
    reviewedPageCount: int = Field(default=0, ge=0)
    renderedPages: tuple[DocxRenderedPage, ...] = ()
    reviewedAt: datetime | None = None
    notes: tuple[str, ...] = ()

    @field_validator("reviewedAt")
    @classmethod
    def validate_reviewed_at(cls, value: datetime | None) -> datetime | None:
        return _validate_aware_datetime(value) if value is not None else None

    @model_validator(mode="after")
    def validate_success_metadata(self) -> DocxVisualValidation:
        if self.status == VisualValidationStatus.SUCCEEDED:
            if (
                not self.renderer
                or not self.rendererVersion
                or not self.renderedPageCount
                or not self.reviewedAt
            ):
                raise ValueError(
                    "successful visual validation requires renderer, version, page count, and reviewedAt"
                )
            if self.reviewedPageCount != self.renderedPageCount:
                raise ValueError("successful visual validation requires every rendered page to be reviewed")
            if len(self.renderedPages) != self.renderedPageCount:
                raise ValueError("rendered page metadata does not match renderedPageCount")
            if [page.pageNumber for page in self.renderedPages] != list(
                range(1, self.renderedPageCount + 1)
            ):
                raise ValueError("rendered page numbers must be consecutive")
        return self


class DocxExtractionContract(RagContract):
    schemaVersion: int = Field(default=SCHEMA_VERSION, ge=1)
    corpusId: str = Field(default=CORPUS_ID, min_length=3, max_length=100)
    corpusVersion: CorpusVersion
    documentId: StableId
    sourceFileName: str = Field(min_length=1, max_length=255)
    sourceSha256: Sha256
    parserVersion: str = Field(min_length=1, max_length=100)
    parsedAt: datetime
    status: BuildStatus
    titleCandidates: tuple[str, ...] = ()
    package: DocxPackageInfo
    blocks: tuple[DocxTextBlock, ...]
    flowNodes: tuple[DocxFlowNode, ...]
    connectors: tuple[DocxConnector, ...]
    relationshipCandidates: tuple[DocxRelationshipCandidate, ...]
    qualityFlags: tuple[str, ...]
    metrics: DocxExtractionMetrics
    visualValidation: DocxVisualValidation = Field(default_factory=DocxVisualValidation)
    extractionSha256: Sha256

    _parsed_at_timezone = field_validator("parsedAt")(_validate_aware_datetime)


class DocxDocumentExtractionSummary(RagContract):
    documentId: StableId
    sourceFileName: str = Field(min_length=1, max_length=255)
    status: BuildStatus
    extractionSha256: Sha256
    selectedTextBoxCount: int = Field(ge=0)
    connectorCount: int = Field(ge=0)
    flowNodeCount: int = Field(ge=0)
    qualityFlags: tuple[str, ...] = ()


class DocxExtractionReport(RagContract):
    schemaVersion: int = Field(default=SCHEMA_VERSION, ge=1)
    corpusId: str = Field(default=CORPUS_ID, min_length=3, max_length=100)
    corpusVersion: CorpusVersion
    generatedAt: datetime
    status: BuildStatus
    expectedDocxCount: int = Field(ge=0)
    succeededCount: int = Field(ge=0)
    failedCount: int = Field(ge=0)
    visualValidationPendingCount: int = Field(ge=0)
    documents: tuple[DocxDocumentExtractionSummary, ...]
    extractionManifestSha256: Sha256

    _generated_at_timezone = field_validator("generatedAt")(_validate_aware_datetime)

    @model_validator(mode="after")
    def validate_document_counts(self) -> DocxExtractionReport:
        if self.expectedDocxCount != len(self.documents):
            raise ValueError("expectedDocxCount does not match documents")
        if self.succeededCount + self.failedCount != len(self.documents):
            raise ValueError("succeededCount and failedCount do not match documents")
        return self


class PdfTextLayerStatus(str, Enum):
    EMPTY = "EMPTY"
    SPARSE = "SPARSE"
    PRESENT = "PRESENT"


class PdfOcrStatus(str, Enum):
    NOT_CONFIGURED = "NOT_CONFIGURED"
    NOT_REQUIRED = "NOT_REQUIRED"
    SUCCEEDED = "SUCCEEDED"
    FAILED = "FAILED"


class PdfRegionType(str, Enum):
    UNKNOWN = "UNKNOWN"
    TITLE = "TITLE"
    BODY = "BODY"
    PRODUCT_CARD = "PRODUCT_CARD"
    CERTIFICATE = "CERTIFICATE"
    MAP = "MAP"
    DECORATION = "DECORATION"


class PdfBoundingBox(RagContract):
    x: float = Field(ge=0, le=1)
    y: float = Field(ge=0, le=1)
    width: float = Field(gt=0, le=1)
    height: float = Field(gt=0, le=1)

    @model_validator(mode="after")
    def validate_bounds(self) -> PdfBoundingBox:
        if self.x + self.width > 1.000001 or self.y + self.height > 1.000001:
            raise ValueError("PDF bounding box must stay within the page")
        return self


class PdfOcrRegion(RagContract):
    regionId: StableId
    regionType: PdfRegionType = PdfRegionType.UNKNOWN
    sourceText: str = Field(min_length=1)
    confidence: float | None = Field(default=None, ge=0, le=1)
    boundingBox: PdfBoundingBox | None = None
    qualityFlags: tuple[str, ...] = ()


class PdfPageExtraction(RagContract):
    pageNumber: int = Field(ge=1)
    widthPoints: float = Field(gt=0)
    heightPoints: float = Field(gt=0)
    rotationDegrees: int
    textLayerStatus: PdfTextLayerStatus
    extractedText: str = ""
    extractedCharacterCount: int = Field(ge=0)
    renderedArtifact: str = Field(min_length=1, max_length=500)
    renderedSha256: Sha256
    renderDpi: int = Field(ge=72, le=600)
    pixelWidth: int = Field(ge=1)
    pixelHeight: int = Field(ge=1)
    ocrStatus: PdfOcrStatus
    ocrText: str = ""
    ocrAverageConfidence: float | None = Field(default=None, ge=0, le=1)
    regions: tuple[PdfOcrRegion, ...] = ()
    qualityFlags: tuple[str, ...] = ()

    @model_validator(mode="after")
    def validate_text_and_ocr(self) -> PdfPageExtraction:
        if self.extractedCharacterCount != len(self.extractedText):
            raise ValueError("extractedCharacterCount does not match extractedText")
        if self.ocrStatus == PdfOcrStatus.SUCCEEDED and not self.ocrText:
            raise ValueError("successful OCR must include ocrText")
        return self


class PdfPackageInfo(RagContract):
    pageCount: int = Field(ge=1)
    encrypted: bool = False
    title: str | None = Field(default=None, max_length=1000)
    author: str | None = Field(default=None, max_length=1000)
    creator: str | None = Field(default=None, max_length=1000)
    producer: str | None = Field(default=None, max_length=1000)


class PdfExtractionMetrics(RagContract):
    renderedPageCount: int = Field(ge=0)
    emptyTextLayerPageCount: int = Field(ge=0)
    sparseTextLayerPageCount: int = Field(ge=0)
    presentTextLayerPageCount: int = Field(ge=0)
    ocrSucceededPageCount: int = Field(ge=0)
    ocrFailedPageCount: int = Field(ge=0)
    ocrNotConfiguredPageCount: int = Field(ge=0)
    lowConfidencePageCount: int = Field(ge=0)


class PdfVisualValidation(RagContract):
    status: VisualValidationStatus = VisualValidationStatus.NOT_PERFORMED
    renderer: str = Field(min_length=1, max_length=200)
    rendererVersion: str = Field(min_length=1, max_length=500)
    renderedPageCount: int = Field(ge=0)
    reviewedPageCount: int = Field(default=0, ge=0)
    reviewedAt: datetime | None = None
    notes: tuple[str, ...] = ()

    @field_validator("reviewedAt")
    @classmethod
    def validate_pdf_reviewed_at(cls, value: datetime | None) -> datetime | None:
        return _validate_aware_datetime(value) if value is not None else None

    @model_validator(mode="after")
    def validate_review_counts(self) -> PdfVisualValidation:
        if self.reviewedPageCount > self.renderedPageCount:
            raise ValueError("reviewedPageCount exceeds renderedPageCount")
        if self.status == VisualValidationStatus.SUCCEEDED:
            if (
                self.renderedPageCount == 0
                or self.reviewedPageCount != self.renderedPageCount
                or self.reviewedAt is None
            ):
                raise ValueError("successful PDF visual validation requires every page to be reviewed")
        return self


class PdfExtractionContract(RagContract):
    schemaVersion: int = Field(default=SCHEMA_VERSION, ge=1)
    corpusId: str = Field(default=CORPUS_ID, min_length=3, max_length=100)
    corpusVersion: CorpusVersion
    documentId: StableId
    sourceFileName: str = Field(min_length=1, max_length=255)
    sourceSha256: Sha256
    parserVersion: str = Field(min_length=1, max_length=100)
    parsedAt: datetime
    status: BuildStatus
    package: PdfPackageInfo
    pages: tuple[PdfPageExtraction, ...]
    qualityFlags: tuple[str, ...]
    metrics: PdfExtractionMetrics
    visualValidation: PdfVisualValidation
    extractionSha256: Sha256

    _parsed_at_timezone = field_validator("parsedAt")(_validate_aware_datetime)

    @model_validator(mode="after")
    def validate_pdf_pages(self) -> PdfExtractionContract:
        if self.package.pageCount != len(self.pages):
            raise ValueError("PDF package pageCount does not match pages")
        expected_numbers = list(range(1, len(self.pages) + 1))
        if [page.pageNumber for page in self.pages] != expected_numbers:
            raise ValueError("PDF pages must be complete and ordered")
        return self


class PdfDocumentExtractionSummary(RagContract):
    documentId: StableId
    sourceFileName: str = Field(min_length=1, max_length=255)
    status: BuildStatus
    extractionSha256: Sha256
    pageCount: int = Field(ge=1)
    renderedPageCount: int = Field(ge=0)
    ocrSucceededPageCount: int = Field(ge=0)
    qualityFlags: tuple[str, ...] = ()


class PdfExtractionReport(RagContract):
    schemaVersion: int = Field(default=SCHEMA_VERSION, ge=1)
    corpusId: str = Field(default=CORPUS_ID, min_length=3, max_length=100)
    corpusVersion: CorpusVersion
    generatedAt: datetime
    status: BuildStatus
    expectedPdfCount: int = Field(ge=0)
    succeededCount: int = Field(ge=0)
    failedCount: int = Field(ge=0)
    renderedPageCount: int = Field(ge=0)
    ocrPendingPageCount: int = Field(ge=0)
    visualValidationPendingPageCount: int = Field(ge=0)
    documents: tuple[PdfDocumentExtractionSummary, ...]
    extractionManifestSha256: Sha256

    _generated_at_timezone = field_validator("generatedAt")(_validate_aware_datetime)

    @model_validator(mode="after")
    def validate_pdf_document_counts(self) -> PdfExtractionReport:
        if self.expectedPdfCount != len(self.documents):
            raise ValueError("expectedPdfCount does not match documents")
        if self.succeededCount + self.failedCount != len(self.documents):
            raise ValueError("succeededCount and failedCount do not match documents")
        return self
