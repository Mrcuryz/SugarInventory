from __future__ import annotations

from concurrent.futures import ThreadPoolExecutor, TimeoutError as FutureTimeoutError
import hashlib
import logging
from threading import BoundedSemaphore
import time
from typing import Any, Mapping, Protocol, Sequence
import unicodedata

from pydantic import ValidationError

from app.observability import MetricsRegistry
from app.rag.config import ALLOWED_ROLES
from app.rag.contracts import (
    ChunkContract,
    KnowledgeDomain,
    RetrievalMode,
    RetrievalResult,
    RetrievalStatus,
)
from app.rag.runtime.contracts import (
    KnowledgeCitation,
    KnowledgeEvidence,
    KnowledgeSearchRequest,
    KnowledgeSearchResponse,
    RagReadiness,
    RagReadinessState,
    TrustedKnowledgeContext,
)
from app.rag.runtime.corpus_loader import (
    LoadedRagRuntime,
    RagRuntimeConfiguration,
    load_current_corpus,
)
from app.rag.runtime.errors import RagRuntimeError


logger = logging.getLogger(__name__)
MAX_EVIDENCE_CONTENT = 800
MAX_TOTAL_EVIDENCE_CONTENT = 6000


class KnowledgeRetriever(Protocol):
    corpus_version: str
    allowed_roles: tuple[str, ...]
    known_product_families: tuple[str, ...]
    chunks: dict[str, ChunkContract]

    def search(
        self,
        query: str,
        *,
        role: str,
        mode: RetrievalMode,
        limit: int,
        knowledge_domains: tuple[KnowledgeDomain, ...],
        product_families: tuple[str, ...],
    ) -> RetrievalResult: ...

    def has_reliable_lexical_evidence(
        self, query: str, hit_ids: Sequence[str], product_families: Sequence[str]
    ) -> bool: ...


def _normalize_label(value: str) -> str:
    return unicodedata.normalize("NFKC", value).strip().casefold()


def _truncate_evidence(value: str, limit: int) -> str:
    cleaned = value.strip()
    if len(cleaned) <= limit:
        return cleaned
    if limit <= 1:
        return cleaned[:limit]
    prefix = cleaned[: limit - 1]
    boundary = max(prefix.rfind(mark) for mark in ("。", "；", "！", "？", "\n"))
    if boundary >= max(1, limit // 2):
        return prefix[: boundary + 1]
    return prefix.rstrip() + "…"


class ApprovedKnowledgeService:
    """Trusted, process-local implementation of search_approved_knowledge."""

    def __init__(
        self,
        *,
        readiness: RagReadiness,
        retriever: KnowledgeRetriever | None,
        metrics: MetricsRegistry,
        timeout_ms: int,
        max_evidence: int,
    ) -> None:
        self.readiness = readiness
        self._retriever = retriever
        self._metrics = metrics
        self._timeout_ms = timeout_ms
        self._max_evidence = max_evidence
        self._executor = (
            ThreadPoolExecutor(max_workers=1, thread_name_prefix="rag-query")
            if retriever is not None
            else None
        )
        self._search_slot = BoundedSemaphore(1)

    def close(self) -> None:
        if self._executor is not None:
            self._executor.shutdown(wait=False, cancel_futures=True)

    def capability_snapshot(self) -> dict[str, object]:
        return {
            "enabled": self.readiness.enabled,
            "required": self.readiness.required,
            "state": self.readiness.state.value,
            "corpusVersion": self.readiness.corpusVersion,
        }

    def search_approved_knowledge(
        self,
        payload: KnowledgeSearchRequest | Mapping[str, Any],
        context: TrustedKnowledgeContext,
    ) -> KnowledgeSearchResponse:
        started = time.perf_counter()
        role = context.roleCode.strip().upper()
        if role not in ALLOWED_ROLES:
            return self._finish(
                KnowledgeSearchResponse(
                    status=RetrievalStatus.FORBIDDEN,
                    warnings=("RAG_ROLE_FORBIDDEN",),
                ),
                role,
                started,
                query_length=0,
                domain_count=0,
                product_count=0,
            )
        try:
            request = (
                payload
                if isinstance(payload, KnowledgeSearchRequest)
                else KnowledgeSearchRequest.model_validate(payload)
            )
        except ValidationError:
            return self._finish(
                KnowledgeSearchResponse(
                    status=RetrievalStatus.INVALID_QUERY,
                    warnings=("RAG_QUERY_INVALID",),
                ),
                role,
                started,
                query_length=0,
                domain_count=0,
                product_count=0,
            )
        if self.readiness.state != RagReadinessState.READY or self._retriever is None:
            return self._finish(
                KnowledgeSearchResponse(
                    status=RetrievalStatus.UNAVAILABLE,
                    queryLabel=request.query,
                    knowledgeDomains=request.knowledgeDomains,
                    warnings=("RAG_UNAVAILABLE",),
                ),
                role,
                started,
                request=request,
            )
        if role not in self._retriever.allowed_roles:
            return self._finish(
                KnowledgeSearchResponse(
                    status=RetrievalStatus.FORBIDDEN,
                    warnings=("RAG_ROLE_FORBIDDEN",),
                ),
                role,
                started,
                request=request,
            )
        product_families = self._resolve_products(request.productQueries)
        if product_families is None:
            return self._finish(
                KnowledgeSearchResponse(
                    status=RetrievalStatus.INVALID_QUERY,
                    corpusVersion=self._retriever.corpus_version,
                    queryLabel=request.query,
                    knowledgeDomains=request.knowledgeDomains,
                    warnings=("RAG_PRODUCT_QUERY_UNKNOWN",),
                ),
                role,
                started,
                request=request,
            )
        effective_limit = min(request.limit, self._max_evidence)
        warnings: tuple[str, ...] = (
            ("RAG_EVIDENCE_LIMIT_REDUCED",) if effective_limit < request.limit else ()
        )
        result = self._execute_search(
            request,
            role,
            product_families,
            effective_limit,
        )
        if result is None:
            return self._finish(
                KnowledgeSearchResponse(
                    status=RetrievalStatus.UNAVAILABLE,
                    corpusVersion=self._retriever.corpus_version,
                    queryLabel=request.query,
                    knowledgeDomains=request.knowledgeDomains,
                    warnings=(*warnings, "RAG_QUERY_TIMEOUT_OR_BUSY"),
                ),
                role,
                started,
                request=request,
            )
        if result.status == RetrievalStatus.DEGRADED and not self._retriever.has_reliable_lexical_evidence(
            request.query,
            tuple(hit.chunkId for hit in result.hits),
            product_families,
        ):
            result = result.model_copy(
                update={
                    "status": RetrievalStatus.UNAVAILABLE,
                    "hits": (),
                    "reason": "RAG_DEGRADED_EVIDENCE_INSUFFICIENT",
                }
            )
        if result.status not in {
            RetrievalStatus.SUCCEEDED,
            RetrievalStatus.NO_DATA,
            RetrievalStatus.DEGRADED,
        }:
            warning = result.reason if result.reason and result.reason.startswith("RAG_") else "RAG_UNAVAILABLE"
            return self._finish(
                KnowledgeSearchResponse(
                    status=result.status,
                    corpusVersion=self._retriever.corpus_version,
                    queryLabel=request.query,
                    knowledgeDomains=request.knowledgeDomains,
                    warnings=(*warnings, warning),
                ),
                role,
                started,
                request=request,
            )
        evidence = self._safe_evidence(result, effective_limit)
        if result.status == RetrievalStatus.DEGRADED:
            warnings = (*warnings, "RAG_QUERY_EMBEDDING_DEGRADED")
        return self._finish(
            KnowledgeSearchResponse(
                status=result.status,
                corpusVersion=self._retriever.corpus_version,
                queryLabel=request.query,
                knowledgeDomains=request.knowledgeDomains,
                evidence=evidence,
                warnings=warnings,
            ),
            role,
            started,
            request=request,
        )

    def _resolve_products(self, queries: Sequence[str]) -> tuple[str, ...] | None:
        assert self._retriever is not None
        lookup: dict[str, list[str]] = {}
        for family in self._retriever.known_product_families:
            lookup.setdefault(_normalize_label(family), []).append(family)
        resolved: list[str] = []
        for query in queries:
            matches = lookup.get(_normalize_label(query), [])
            if len(matches) != 1:
                return None
            if matches[0] not in resolved:
                resolved.append(matches[0])
        return tuple(resolved)

    def _execute_search(
        self,
        request: KnowledgeSearchRequest,
        role: str,
        product_families: tuple[str, ...],
        limit: int,
    ) -> RetrievalResult | None:
        assert self._retriever is not None
        assert self._executor is not None
        if not self._search_slot.acquire(blocking=False):
            return None

        def invoke() -> RetrievalResult:
            try:
                return self._retriever.search(
                    request.query,
                    role=role,
                    mode=RetrievalMode.HYBRID,
                    limit=limit,
                    knowledge_domains=request.knowledgeDomains,
                    product_families=product_families,
                )
            finally:
                self._search_slot.release()

        future = self._executor.submit(invoke)
        try:
            return future.result(timeout=self._timeout_ms / 1000)
        except FutureTimeoutError:
            future.cancel()
            return None
        except Exception:
            logger.error("Knowledge search failed reasonCode=RAG_QUERY_EXECUTION_FAILED")
            return RetrievalResult(
                status=RetrievalStatus.UNAVAILABLE,
                mode=RetrievalMode.HYBRID,
                query=request.query,
                elapsedMilliseconds=0,
                reason="RAG_QUERY_EXECUTION_FAILED",
            )

    def _safe_evidence(
        self, result: RetrievalResult, limit: int
    ) -> tuple[KnowledgeEvidence, ...]:
        assert self._retriever is not None
        evidence: list[KnowledgeEvidence] = []
        remaining = MAX_TOTAL_EVIDENCE_CONTENT
        for hit in result.hits[:limit]:
            chunk = self._retriever.chunks.get(hit.chunkId)
            if chunk is None or remaining <= 0:
                continue
            content_limit = min(MAX_EVIDENCE_CONTENT, remaining)
            content = _truncate_evidence(chunk.content, content_limit)
            if not content:
                continue
            evidence_id = hashlib.sha256(
                f"{self._retriever.corpus_version}\0{chunk.contentSha256}".encode("utf-8")
            ).hexdigest()[:16]
            evidence.append(
                KnowledgeEvidence(
                    evidenceId=f"ev_{evidence_id}",
                    title=chunk.title,
                    content=content,
                    citation=KnowledgeCitation(
                        documentTitle=chunk.citation.documentTitle,
                        pageNumber=chunk.citation.pageNumber,
                        sectionLabel=chunk.citation.sectionLabel,
                    ),
                    qualityFlags=tuple(flag[:100] for flag in chunk.qualityFlags[:20]),
                )
            )
            remaining -= len(content)
        return tuple(evidence)

    def _finish(
        self,
        response: KnowledgeSearchResponse,
        role: str,
        started: float,
        *,
        request: KnowledgeSearchRequest | None = None,
        query_length: int | None = None,
        domain_count: int | None = None,
        product_count: int | None = None,
    ) -> KnowledgeSearchResponse:
        elapsed_ms = (time.perf_counter() - started) * 1000
        role_label = role if role in ALLOWED_ROLES else "OTHER"
        self._metrics.increment(
            "knowledge_search_total", status=response.status.value, role=role_label
        )
        self._metrics.observe(
            "knowledge_search_duration", elapsed_ms, status=response.status.value
        )
        logger.info(
            "Knowledge search completed status=%s role=%s corpusVersion=%s queryLength=%s domainCount=%s productCount=%s evidenceCount=%s elapsedMilliseconds=%.3f",
            response.status.value,
            role_label,
            response.corpusVersion or "NONE",
            len(request.query) if request is not None else (query_length or 0),
            len(request.knowledgeDomains) if request is not None else (domain_count or 0),
            len(request.productQueries) if request is not None else (product_count or 0),
            len(response.evidence),
            elapsed_ms,
        )
        return response


def build_approved_knowledge_service(
    configuration: RagRuntimeConfiguration,
    metrics: MetricsRegistry,
) -> ApprovedKnowledgeService:
    if configuration.required and not configuration.enabled:
        raise RagRuntimeError(
            "RAG_REQUIRED_WHILE_DISABLED", "Required RAG cannot be disabled."
        )
    if not configuration.enabled:
        return ApprovedKnowledgeService(
            readiness=RagReadiness(
                enabled=False,
                required=configuration.required,
                state=RagReadinessState.DISABLED,
            ),
            retriever=None,
            metrics=metrics,
            timeout_ms=configuration.query_timeout_ms,
            max_evidence=configuration.max_evidence,
        )
    try:
        loaded: LoadedRagRuntime = load_current_corpus(configuration)
    except RagRuntimeError as exc:
        logger.error(
            "RAG runtime load failed reasonCode=%s required=%s", exc.code, configuration.required
        )
        if configuration.required:
            raise
        return ApprovedKnowledgeService(
            readiness=RagReadiness(
                enabled=True,
                required=False,
                state=RagReadinessState.UNAVAILABLE,
                reasonCode=exc.code,
            ),
            retriever=None,
            metrics=metrics,
            timeout_ms=configuration.query_timeout_ms,
            max_evidence=configuration.max_evidence,
        )
    return ApprovedKnowledgeService(
        readiness=RagReadiness(
            enabled=True,
            required=configuration.required,
            state=RagReadinessState.READY,
            corpusVersion=loaded.pointer.corpusVersion,
        ),
        retriever=loaded.retriever,
        metrics=metrics,
        timeout_ms=configuration.query_timeout_ms,
        max_evidence=configuration.max_evidence,
    )
