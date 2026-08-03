from __future__ import annotations

import hashlib
import json
from pathlib import Path
import re
import sqlite3
import time
from typing import Any, Protocol, Sequence
import unicodedata

import numpy as np
from pydantic import ValidationError

from app.rag.config import ALLOWED_ROLES, CORPUS_ID, SCHEMA_VERSION
from app.rag.contracts import (
    BuildStatus,
    ChunkContract,
    ChunkManifest,
    ChunkType,
    CorpusManifest,
    CorpusStatus,
    EmbeddingContract,
    IndexManifest,
    KnowledgeDomain,
    RetrievalHit,
    RetrievalMode,
    RetrievalResult,
    RetrievalStatus,
)
from app.rag.runtime.errors import RagRuntimeError


LEXICAL_INDEX_VERSION = "sqlite-fts5-cjk12/1.0.0"
VECTOR_INDEX_VERSION = "numpy-float32-cosine/1.0.0"
RETRIEVAL_FUSION_VERSION = "rrf-control-aware/1.0.2"
RRF_K = 60
_CJK = re.compile(r"[\u3400-\u4dbf\u4e00-\u9fff]+")
_ASCII_TOKEN = re.compile(r"[a-z0-9]+(?:[._/-][a-z0-9]+)*", re.I)


class QueryEmbeddingProvider(Protocol):
    @property
    def contract(self) -> EmbeddingContract: ...

    def embed_queries(self, texts: Sequence[str]) -> np.ndarray: ...


def _canonical_hash(payload: Any) -> str:
    encoded = json.dumps(
        payload,
        ensure_ascii=False,
        sort_keys=True,
        separators=(",", ":"),
    ).encode("utf-8")
    return hashlib.sha256(encoded).hexdigest()


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    try:
        with path.open("rb") as handle:
            for block in iter(lambda: handle.read(1024 * 1024), b""):
                digest.update(block)
    except OSError as exc:
        raise RagRuntimeError("RAG_ARTIFACT_READ_FAILED", "Unable to read a RAG artifact.") from exc
    return digest.hexdigest()


def _load_model(path: Path, model_type: type[Any], code: str) -> Any:
    try:
        return model_type.model_validate_json(path.read_text(encoding="utf-8"))
    except (OSError, UnicodeError, ValidationError) as exc:
        raise RagRuntimeError(code, "A required RAG artifact is invalid.") from exc


def _chunk_hash(chunk: ChunkContract) -> str:
    payload = chunk.model_dump(mode="json")
    payload.pop("contentSha256", None)
    payload.pop("embeddingRef", None)
    return _canonical_hash(payload)


def _chunk_manifest_hash(manifest: ChunkManifest) -> str:
    payload = manifest.model_dump(mode="json")
    payload.pop("generatedAt")
    payload.pop("chunkManifestSha256")
    return _canonical_hash(payload)


def _index_manifest_hash(manifest: IndexManifest) -> str:
    payload = manifest.model_dump(mode="json")
    payload.pop("builtAt")
    payload.pop("indexManifestSha256")
    return _canonical_hash(payload)


def _normalize(value: str) -> str:
    return unicodedata.normalize("NFKC", value).lower().strip()


def _base_tokens(value: str) -> list[str]:
    normalized = _normalize(value)
    tokens: list[str] = []
    for match in _CJK.finditer(normalized):
        span = match.group()
        tokens.extend(span)
        tokens.extend(span[index : index + 2] for index in range(len(span) - 1))
    tokens.extend(match.group() for match in _ASCII_TOKEN.finditer(normalized))
    return tokens


def lexical_tokens(value: str, controlled_terms: Sequence[str] = ()) -> tuple[str, ...]:
    normalized = _normalize(value)
    tokens = _base_tokens(normalized)
    tokens.extend(
        _normalize(term)
        for term in controlled_terms
        if _normalize(term) and _normalize(term) in normalized
    )
    return tuple(dict.fromkeys(token for token in tokens if token))


def _chunk_controlled_terms(chunk: ChunkContract) -> tuple[str, ...]:
    return tuple(
        dict.fromkeys(
            (
                *chunk.productFamilies,
                *chunk.keywords,
                *chunk.controlPointLabels,
                chunk.title,
            )
        )
    )


class HybridIndex:
    """Validated, read-only view of one immutable RAG release."""

    def __init__(self, release_root: Path, provider: QueryEmbeddingProvider | None = None) -> None:
        self.release_root = release_root.expanduser().resolve(strict=True)
        self.provider: QueryEmbeddingProvider | None = None
        self.corpus, self.chunk_manifest, chunks = self._load_chunks()
        self.chunks = {chunk.chunkId: chunk for chunk in chunks}
        self.ordered_chunks = chunks
        self.index_manifest = _load_model(
            self.release_root / "indexes" / "index-manifest.json",
            IndexManifest,
            "RAG_INDEX_MANIFEST_INVALID",
        )
        self._validate_index_manifest()
        self.lexical_path = self.release_root / "indexes" / "lexical" / "chunks.sqlite3"
        self._validate_lexical_index()
        self.matrix = self._load_vector_index()
        self.controlled_terms = tuple(
            sorted(
                {
                    term
                    for chunk in chunks
                    for term in _chunk_controlled_terms(chunk)
                    if 1 < len(term) <= 80
                },
                key=lambda item: (-len(item), item),
            )
        )
        self.known_product_families = tuple(
            sorted({family for chunk in chunks for family in chunk.productFamilies})
        )
        if provider is not None:
            self.bind_provider(provider)

    @property
    def corpus_version(self) -> str:
        return self.corpus.corpusVersion

    @property
    def allowed_roles(self) -> tuple[str, ...]:
        return self.corpus.allowedRoles

    def bind_provider(self, provider: QueryEmbeddingProvider) -> None:
        if provider.contract != self.index_manifest.embedding:
            raise RagRuntimeError(
                "RAG_EMBEDDING_CONTRACT_MISMATCH",
                "The query embedding provider does not match the approved index.",
            )
        self.provider = provider

    def _load_chunks(self) -> tuple[CorpusManifest, ChunkManifest, tuple[ChunkContract, ...]]:
        corpus = _load_model(
            self.release_root / "corpus-manifest.json",
            CorpusManifest,
            "RAG_CORPUS_MANIFEST_INVALID",
        )
        manifest = _load_model(
            self.release_root / "chunks" / "chunk-manifest.json",
            ChunkManifest,
            "RAG_CHUNK_MANIFEST_INVALID",
        )
        if corpus.schemaVersion != SCHEMA_VERSION or manifest.schemaVersion != SCHEMA_VERSION:
            raise RagRuntimeError("RAG_SCHEMA_UNSUPPORTED", "The RAG schema version is unsupported.")
        if corpus.corpusId != CORPUS_ID or manifest.corpusId != CORPUS_ID:
            raise RagRuntimeError("RAG_CORPUS_ID_MISMATCH", "The RAG corpus identity is invalid.")
        if corpus.status not in {CorpusStatus.VALIDATED, CorpusStatus.ACTIVE} or corpus.chunkCount <= 0:
            raise RagRuntimeError("RAG_CORPUS_NOT_READY", "The RAG corpus is not validated.")
        if set(corpus.allowedRoles) != set(ALLOWED_ROLES):
            raise RagRuntimeError("RAG_ROLE_CONTRACT_MISMATCH", "The RAG role contract is invalid.")
        if (
            corpus.corpusVersion != manifest.corpusVersion
            or corpus.documentCount != manifest.documentCount
            or corpus.chunkCount != manifest.chunkCount
            or corpus.chunkManifestSha256 != manifest.chunkManifestSha256
        ):
            raise RagRuntimeError("RAG_CHUNK_LINEAGE_INVALID", "Corpus and chunk lineage do not match.")
        if _chunk_manifest_hash(manifest) != manifest.chunkManifestSha256:
            raise RagRuntimeError("RAG_CHUNK_MANIFEST_HASH_INVALID", "Chunk manifest checksum is invalid.")
        chunks: list[ChunkContract] = []
        try:
            with (self.release_root / "chunks" / "chunks.jsonl").open(
                "r", encoding="utf-8"
            ) as handle:
                for line in handle:
                    if not line.strip():
                        continue
                    chunk = ChunkContract.model_validate_json(line)
                    if _chunk_hash(chunk) != chunk.contentSha256:
                        raise RagRuntimeError(
                            "RAG_CHUNK_HASH_INVALID", "A RAG chunk checksum is invalid."
                        )
                    if set(chunk.allowedRoles) != set(ALLOWED_ROLES):
                        raise RagRuntimeError(
                            "RAG_CHUNK_ROLE_MISMATCH", "A RAG chunk role contract is invalid."
                        )
                    if chunk.status.value != "ACTIVE":
                        raise RagRuntimeError(
                            "RAG_CHUNK_STATUS_INVALID", "A RAG chunk is not active."
                        )
                    chunks.append(chunk)
        except RagRuntimeError:
            raise
        except (OSError, UnicodeError, ValidationError) as exc:
            raise RagRuntimeError("RAG_CHUNK_DATA_INVALID", "The RAG chunk data is invalid.") from exc
        ordered = tuple(sorted(chunks, key=lambda item: item.chunkId))
        summaries = {item.chunkId: item for item in manifest.chunks}
        if len(ordered) != manifest.chunkCount or len(summaries) != len(ordered):
            raise RagRuntimeError("RAG_CHUNK_COUNT_MISMATCH", "RAG chunk counts are inconsistent.")
        for row, chunk in enumerate(ordered):
            summary = summaries.get(chunk.chunkId)
            if (
                summary is None
                or summary.documentId != chunk.documentId
                or summary.contentSha256 != chunk.contentSha256
                or summary.embeddingRef != f"vector/row/{row}"
                or chunk.embeddingRef != summary.embeddingRef
            ):
                raise RagRuntimeError(
                    "RAG_CHUNK_MANIFEST_MISMATCH", "RAG chunks do not match the chunk manifest."
                )
        return corpus, manifest, ordered

    def _validate_index_manifest(self) -> None:
        manifest = self.index_manifest
        if manifest.schemaVersion != SCHEMA_VERSION or manifest.corpusId != CORPUS_ID:
            raise RagRuntimeError("RAG_INDEX_SCHEMA_INVALID", "The RAG index schema is invalid.")
        if _index_manifest_hash(manifest) != manifest.indexManifestSha256:
            raise RagRuntimeError("RAG_INDEX_MANIFEST_HASH_INVALID", "Index manifest checksum is invalid.")
        if (
            manifest.corpusVersion != self.corpus.corpusVersion
            or manifest.documentManifestSha256 != self.corpus.documentManifestSha256
            or manifest.chunkManifestSha256 != self.chunk_manifest.chunkManifestSha256
            or manifest.documentCount != self.corpus.documentCount
            or manifest.chunkCount != len(self.ordered_chunks)
        ):
            raise RagRuntimeError("RAG_INDEX_LINEAGE_INVALID", "The RAG index lineage is invalid.")
        if (
            manifest.lexical.status != BuildStatus.SUCCEEDED
            or manifest.vector.status != BuildStatus.SUCCEEDED
            or manifest.lexical.itemCount != len(self.ordered_chunks)
            or manifest.vector.itemCount != len(self.ordered_chunks)
            or manifest.lexical.version != LEXICAL_INDEX_VERSION
            or manifest.vector.version != VECTOR_INDEX_VERSION
            or manifest.retrieval.get("version") != RETRIEVAL_FUSION_VERSION
            or manifest.retrieval.get("rrfK") != RRF_K
            or manifest.retrieval.get("accessGate") != "corpus.allowedRoles-before-search"
        ):
            raise RagRuntimeError("RAG_INDEX_CONTRACT_MISMATCH", "The RAG index contract is unsupported.")

    def _validate_lexical_index(self) -> None:
        expected = self.index_manifest.lexical.sha256
        if (
            expected is None
            or self.corpus.lexicalIndexSha256 != expected
            or sha256_file(self.lexical_path) != expected
        ):
            raise RagRuntimeError("RAG_LEXICAL_HASH_INVALID", "Lexical index checksum is invalid.")

    def _load_vector_index(self) -> np.ndarray:
        matrix_path = self.release_root / "indexes" / "vector" / "embeddings.npy"
        row_map_path = self.release_root / "indexes" / "vector" / "row-map.jsonl"
        metadata = self.index_manifest.vector.metadata
        matrix_sha = sha256_file(matrix_path)
        row_map_sha = sha256_file(row_map_path)
        vector_sha = _canonical_hash(
            {"embeddingsSha256": matrix_sha, "rowMapSha256": row_map_sha}
        )
        if (
            matrix_sha != metadata.get("embeddingsSha256")
            or row_map_sha != metadata.get("rowMapSha256")
            or vector_sha != self.index_manifest.vector.sha256
            or vector_sha != self.corpus.vectorIndexSha256
        ):
            raise RagRuntimeError("RAG_VECTOR_HASH_INVALID", "Vector index checksum is invalid.")
        try:
            matrix = np.load(matrix_path, mmap_mode="r", allow_pickle=False)
        except (OSError, ValueError) as exc:
            raise RagRuntimeError("RAG_VECTOR_DATA_INVALID", "Vector data is invalid.") from exc
        expected_shape = (len(self.ordered_chunks), self.index_manifest.embedding.dimension)
        if matrix.shape != expected_shape or matrix.dtype != np.float32:
            raise RagRuntimeError("RAG_VECTOR_SHAPE_INVALID", "Vector shape or type is invalid.")
        try:
            row_map = tuple(
                json.loads(line)
                for line in row_map_path.read_text(encoding="utf-8").splitlines()
                if line.strip()
            )
        except (OSError, UnicodeError, json.JSONDecodeError) as exc:
            raise RagRuntimeError("RAG_ROW_MAP_INVALID", "The vector row map is invalid.") from exc
        if len(row_map) != len(self.ordered_chunks) or any(
            item
            != {
                "row": row,
                "chunkId": chunk.chunkId,
                "documentId": chunk.documentId,
                "contentSha256": chunk.contentSha256,
            }
            for row, (item, chunk) in enumerate(
                zip(row_map, self.ordered_chunks, strict=True)
            )
        ):
            raise RagRuntimeError("RAG_ROW_MAP_MISMATCH", "The vector row map is inconsistent.")
        return matrix

    @staticmethod
    def _allowed(chunk: ChunkContract, role: str) -> bool:
        return role.upper() in chunk.allowedRoles

    @staticmethod
    def _matches_filters(
        chunk: ChunkContract,
        knowledge_domains: tuple[KnowledgeDomain, ...],
        product_families: tuple[str, ...],
    ) -> bool:
        return (
            not knowledge_domains or chunk.knowledgeDomain in knowledge_domains
        ) and (
            not product_families
            or any(family in chunk.productFamilies for family in product_families)
        )

    def _candidate_ids(
        self,
        role: str,
        knowledge_domains: tuple[KnowledgeDomain, ...],
        product_families: tuple[str, ...],
    ) -> set[str]:
        return {
            chunk.chunkId
            for chunk in self.ordered_chunks
            if self._allowed(chunk, role)
            and self._matches_filters(chunk, knowledge_domains, product_families)
        }

    def _lexical_rank(self, query: str, candidates: set[str], limit: int) -> list[str]:
        tokens = lexical_tokens(query, self.controlled_terms)
        if not tokens or not candidates:
            return []
        expression = " OR ".join(
            f'"{token.replace(chr(34), chr(34) * 2)}"' for token in tokens
        )
        uri = f"file:{self.lexical_path.as_posix()}?mode=ro&immutable=1"
        try:
            connection = sqlite3.connect(uri, uri=True)
            try:
                rows = connection.execute(
                    "SELECT chunk_id, bm25(chunk_fts, 8.0, 1.0, 5.0) AS score "
                    "FROM chunk_fts WHERE chunk_fts MATCH ? ORDER BY score, chunk_id LIMIT ?",
                    (expression, max(limit * 8, 50)),
                ).fetchall()
            finally:
                connection.close()
        except sqlite3.Error as exc:
            raise RagRuntimeError("RAG_LEXICAL_QUERY_FAILED", "Lexical search failed.") from exc
        return [chunk_id for chunk_id, _ in rows if chunk_id in candidates][:limit]

    def _vector_rank(self, query: str, candidates: set[str], limit: int) -> list[str]:
        if self.provider is None or not candidates:
            return []
        try:
            vector = self.provider.embed_queries([query])[0]
            scores = np.asarray(self.matrix @ vector, dtype=np.float32)
        except RagRuntimeError:
            raise
        except Exception as exc:
            raise RagRuntimeError("RAG_QUERY_EMBEDDING_FAILED", "Query embedding failed.") from exc
        order = np.argsort(-scores, kind="stable")
        return [
            self.ordered_chunks[int(row)].chunkId
            for row in order
            if self.ordered_chunks[int(row)].chunkId in candidates
        ][:limit]

    @staticmethod
    def _exact_match(query: str, chunk: ChunkContract) -> bool:
        compact_query = re.sub(r"\s+", "", _normalize(query))
        if not compact_query:
            return False
        fields = (chunk.title, chunk.content, *chunk.keywords)
        return any(
            compact_query in re.sub(r"\s+", "", _normalize(field))
            for field in fields
        )

    @staticmethod
    def _evidence_type_boost(query: str, chunk: ChunkContract) -> float:
        normalized = _normalize(query)
        control_terms = {term.upper() for term in re.findall(r"(?:CCP|CPP)\d+", query, re.I)}
        control_intent = bool(control_terms) or any(
            term in normalized
            for term in ("控制点", "控制参数", "金属探测器", "金属控制", "检测阈值")
        )
        if (
            control_intent
            and chunk.chunkType == ChunkType.CONTROL_POINT
            and (
                not control_terms
                or control_terms.intersection(label.upper() for label in chunk.controlPointLabels)
            )
        ):
            return 0.02
        if chunk.chunkType == ChunkType.FLOW_OVERVIEW and any(
            term in normalized for term in ("完整流程", "流程顺序")
        ):
            return 0.02
        return 0.0

    def has_reliable_lexical_evidence(
        self, query: str, hit_ids: Sequence[str], product_families: Sequence[str]
    ) -> bool:
        normalized_query = _normalize(query)
        for hit_id in hit_ids:
            chunk = self.chunks[hit_id]
            if self._exact_match(query, chunk):
                return True
            if product_families and all(
                _normalize(family) in _normalize(" ".join(chunk.productFamilies))
                for family in product_families
            ):
                return True
            if any(
                len(term) >= 3
                and _normalize(term) in normalized_query
                and _normalize(term) in _normalize(f"{chunk.title} {chunk.content}")
                for term in (*chunk.keywords, *chunk.controlPointLabels)
            ):
                return True
        return False

    def search(
        self,
        query: str,
        *,
        role: str = "ADMIN",
        mode: RetrievalMode = RetrievalMode.HYBRID,
        limit: int = 5,
        knowledge_domains: tuple[KnowledgeDomain, ...] = (),
        product_families: tuple[str, ...] = (),
        knowledge_domain: KnowledgeDomain | None = None,
        product_family: str | None = None,
    ) -> RetrievalResult:
        started = time.perf_counter()
        cleaned = query.strip()
        if knowledge_domain is not None and not knowledge_domains:
            knowledge_domains = (knowledge_domain,)
        if product_family is not None and not product_families:
            product_families = (product_family,)
        if not cleaned:
            return RetrievalResult(
                status=RetrievalStatus.INVALID_QUERY,
                mode=mode,
                query="",
                elapsedMilliseconds=(time.perf_counter() - started) * 1000,
                reason="RAG_QUERY_EMPTY",
            )
        normalized_role = role.strip().upper()
        if normalized_role not in self.corpus.allowedRoles:
            return RetrievalResult(
                status=RetrievalStatus.FORBIDDEN,
                mode=mode,
                query=cleaned,
                elapsedMilliseconds=(time.perf_counter() - started) * 1000,
                reason="RAG_ROLE_FORBIDDEN",
            )
        candidates = self._candidate_ids(
            normalized_role, knowledge_domains, product_families
        )
        lexical = self._lexical_rank(cleaned, candidates, 20) if mode != RetrievalMode.VECTOR else []
        vector_available = self.provider is not None
        vector_failed = False
        vector: list[str] = []
        if mode != RetrievalMode.LEXICAL and vector_available:
            try:
                vector = self._vector_rank(cleaned, candidates, 20)
            except RagRuntimeError:
                vector_failed = True
                vector_available = False
        if mode == RetrievalMode.VECTOR and not vector_available:
            return RetrievalResult(
                status=RetrievalStatus.UNAVAILABLE,
                mode=mode,
                query=cleaned,
                elapsedMilliseconds=(time.perf_counter() - started) * 1000,
                reason=(
                    "RAG_QUERY_EMBEDDING_FAILED"
                    if vector_failed
                    else "RAG_EMBEDDING_PROVIDER_UNAVAILABLE"
                ),
            )
        scores: dict[str, float] = {}
        for ranking in (lexical, vector):
            for rank, chunk_id in enumerate(ranking, start=1):
                scores[chunk_id] = scores.get(chunk_id, 0.0) + 1.0 / (RRF_K + rank)
        for chunk_id in tuple(scores):
            if self._exact_match(cleaned, self.chunks[chunk_id]):
                scores[chunk_id] += 0.05
            scores[chunk_id] += self._evidence_type_boost(cleaned, self.chunks[chunk_id])
        ranked = sorted(scores, key=lambda chunk_id: (-scores[chunk_id], chunk_id))[
            : max(1, min(limit, 10))
        ]
        hits = tuple(
            RetrievalHit(
                rank=rank,
                chunkId=chunk_id,
                documentId=self.chunks[chunk_id].documentId,
                score=scores[chunk_id],
                citation=self.chunks[chunk_id].citation,
            )
            for rank, chunk_id in enumerate(ranked, start=1)
        )
        status = RetrievalStatus.SUCCEEDED if hits else RetrievalStatus.NO_DATA
        reason = None
        if mode == RetrievalMode.HYBRID and not vector_available:
            status = RetrievalStatus.DEGRADED if hits else RetrievalStatus.UNAVAILABLE
            reason = (
                "RAG_QUERY_EMBEDDING_FAILED"
                if vector_failed
                else "RAG_EMBEDDING_PROVIDER_UNAVAILABLE"
            )
        return RetrievalResult(
            status=status,
            mode=mode,
            query=cleaned,
            elapsedMilliseconds=(time.perf_counter() - started) * 1000,
            hits=hits,
            reason=reason,
        )
