from __future__ import annotations

from datetime import datetime
from enum import Enum

from pydantic import Field, field_validator, model_validator

from app.rag.config import CORPUS_ID, SCHEMA_VERSION
from app.rag.contracts import (
    CorpusVersion,
    KnowledgeDomain,
    RagContract,
    RetrievalStatus,
    Sha256,
)


KNOWLEDGE_TOOL_NAME = "search_approved_knowledge"
INTERNAL_KNOWLEDGE_TOOLS = frozenset({KNOWLEDGE_TOOL_NAME})


class RagReadinessState(str, Enum):
    DISABLED = "DISABLED"
    READY = "READY"
    UNAVAILABLE = "UNAVAILABLE"


class CurrentCorpusPointer(RagContract):
    schemaVersion: int = Field(default=SCHEMA_VERSION, ge=1)
    corpusId: str = Field(default=CORPUS_ID, min_length=3, max_length=100)
    corpusVersion: CorpusVersion
    releaseName: CorpusVersion
    corpusManifestSha256: Sha256
    indexManifestSha256: Sha256
    evaluationSetSha256: Sha256
    evaluationReportSha256: Sha256
    publishedAt: datetime
    publishedBy: str = Field(min_length=1, max_length=100)

    @field_validator("publishedAt")
    @classmethod
    def validate_published_at(cls, value: datetime) -> datetime:
        if value.tzinfo is None or value.utcoffset() is None:
            raise ValueError("publishedAt must include timezone information")
        return value

    @model_validator(mode="after")
    def validate_release_identity(self) -> CurrentCorpusPointer:
        if self.releaseName != self.corpusVersion:
            raise ValueError("releaseName must match corpusVersion")
        return self


class RagReadiness(RagContract):
    enabled: bool
    required: bool
    state: RagReadinessState
    corpusVersion: CorpusVersion | None = None
    reasonCode: str | None = Field(default=None, pattern=r"^[A-Z][A-Z0-9_]{2,79}$")


class KnowledgeSearchRequest(RagContract):
    query: str = Field(min_length=1, max_length=500)
    knowledgeDomains: tuple[KnowledgeDomain, ...] = Field(default=(), max_length=5)
    productQueries: tuple[str, ...] = Field(default=(), max_length=5)
    limit: int = Field(default=5, ge=1, le=10)

    @field_validator("knowledgeDomains")
    @classmethod
    def validate_domains(
        cls, values: tuple[KnowledgeDomain, ...]
    ) -> tuple[KnowledgeDomain, ...]:
        if len(values) != len(set(values)):
            raise ValueError("knowledgeDomains must not contain duplicates")
        return values

    @field_validator("productQueries")
    @classmethod
    def validate_products(cls, values: tuple[str, ...]) -> tuple[str, ...]:
        normalized = tuple(value.strip() for value in values)
        if any(not value or len(value) > 100 for value in normalized):
            raise ValueError("productQueries entries must contain 1 to 100 characters")
        if len({value.casefold() for value in normalized}) != len(normalized):
            raise ValueError("productQueries must not contain duplicates")
        return normalized


class TrustedKnowledgeContext(RagContract):
    roleCode: str = Field(min_length=1, max_length=50)
    userId: int = Field(ge=1)


class KnowledgeCitation(RagContract):
    documentTitle: str = Field(min_length=1, max_length=500)
    pageNumber: int | None = Field(default=None, ge=1)
    sectionLabel: str | None = Field(default=None, max_length=200)


class KnowledgeEvidence(RagContract):
    evidenceId: str = Field(pattern=r"^ev_[0-9a-f]{16}$")
    title: str = Field(min_length=1, max_length=500)
    content: str = Field(min_length=1, max_length=800)
    citation: KnowledgeCitation
    qualityFlags: tuple[str, ...] = ()


class KnowledgeSearchResponse(RagContract):
    status: RetrievalStatus
    corpusVersion: CorpusVersion | None = None
    queryLabel: str = Field(default="", max_length=500)
    knowledgeDomains: tuple[KnowledgeDomain, ...] = ()
    evidence: tuple[KnowledgeEvidence, ...] = Field(default=(), max_length=10)
    warnings: tuple[str, ...] = ()
