from __future__ import annotations

import hashlib
import json
import os
import re
import shutil
import sqlite3
import time
import unicodedata
import uuid
from datetime import datetime
from pathlib import Path
from typing import Any, Iterable, Sequence
from zoneinfo import ZoneInfo

import numpy as np
from pydantic import ValidationError

from app.rag.config import TIMEZONE
from app.rag.contracts import (
    BuildStatus,
    ChunkContract,
    ChunkManifest,
    ChunkType,
    CorpusManifest,
    CorpusStatus,
    IndexComponentContract,
    IndexManifest,
    KnowledgeDomain,
    RetrievalHit,
    RetrievalMode,
    RetrievalResult,
    RetrievalStatus,
)
from app.rag.offline.chunker import _chunk_manifest_hash, _with_chunk_hash
from app.rag.offline.embedding_provider import EmbeddingProvider
from app.rag.offline.source_inventory import RagBuildError


INDEX_BUILDER_VERSION = "rag-hybrid-index/1.0.0"
LEXICAL_INDEX_VERSION = "sqlite-fts5-cjk12/1.0.0"
VECTOR_INDEX_VERSION = "numpy-float32-cosine/1.0.0"
RETRIEVAL_FUSION_VERSION = "rrf-control-aware/1.0.2"
RRF_K = 60
_CJK = re.compile(r"[\u3400-\u9fff]+")
_LATIN_NUMBER = re.compile(
    r"(?:ccp|cpp)\d+|(?:iso|fssc)\d+|\d+(?:\.\d+)?(?:°bx|℃|°c|mpa|mm|g|目|天|小时|分钟)?|[a-z]+(?:[._/-][a-z0-9]+)*",
    re.IGNORECASE,
)


def _canonical_hash(payload: Any) -> str:
    encoded = json.dumps(
        payload,
        ensure_ascii=False,
        sort_keys=True,
        separators=(",", ":"),
    ).encode("utf-8")
    return hashlib.sha256(encoded).hexdigest()


def _sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for block in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def _write_json(path: Path, payload: dict[str, Any]) -> None:
    path.write_text(
        json.dumps(payload, ensure_ascii=False, indent=2, sort_keys=True) + "\n",
        encoding="utf-8",
        newline="\n",
    )


def _write_jsonl(path: Path, records: Iterable[dict[str, Any]]) -> None:
    with path.open("w", encoding="utf-8", newline="\n") as handle:
        for record in records:
            handle.write(
                json.dumps(record, ensure_ascii=False, sort_keys=True, separators=(",", ":"))
                + "\n"
            )


def _load_model(path: Path, model_type: type[Any], code: str) -> Any:
    try:
        return model_type.model_validate_json(path.read_text(encoding="utf-8"))
    except (OSError, UnicodeError, ValidationError) as exc:
        raise RagBuildError(code, f"Invalid RAG artifact: {path.name}") from exc


def _load_chunks(release_root: Path) -> tuple[CorpusManifest, ChunkManifest, tuple[ChunkContract, ...]]:
    corpus = _load_model(
        release_root / "corpus-manifest.json", CorpusManifest, "INDEX_CORPUS_MANIFEST_INVALID"
    )
    manifest = _load_model(
        release_root / "chunks" / "chunk-manifest.json",
        ChunkManifest,
        "INDEX_CHUNK_MANIFEST_INVALID",
    )
    if corpus.status != CorpusStatus.VALIDATED or corpus.chunkCount <= 0:
        raise RagBuildError("INDEX_CORPUS_NOT_READY", "A VALIDATED chunk release is required.")
    if corpus.corpusVersion != manifest.corpusVersion:
        raise RagBuildError("INDEX_VERSION_MISMATCH", "Corpus and chunk versions do not match.")
    if corpus.chunkManifestSha256 != manifest.chunkManifestSha256:
        raise RagBuildError("INDEX_CHUNK_HASH_MISMATCH", "Chunk manifest checksum does not match corpus.")
    if _chunk_manifest_hash(manifest) != manifest.chunkManifestSha256:
        raise RagBuildError("INDEX_CHUNK_HASH_INVALID", "Chunk manifest checksum is invalid.")
    chunks = []
    try:
        with (release_root / "chunks" / "chunks.jsonl").open("r", encoding="utf-8") as handle:
            for line_number, line in enumerate(handle, start=1):
                if not line.strip():
                    continue
                try:
                    chunk = ChunkContract.model_validate_json(line)
                except ValidationError as exc:
                    raise RagBuildError(
                        "INDEX_CHUNK_INVALID", f"Invalid chunk at JSONL line {line_number}."
                    ) from exc
                if _with_chunk_hash(chunk).contentSha256 != chunk.contentSha256:
                    raise RagBuildError(
                        "INDEX_CHUNK_CHECKSUM_MISMATCH",
                        f"Chunk checksum is invalid: {chunk.chunkId}",
                    )
                chunks.append(chunk)
    except OSError as exc:
        raise RagBuildError("INDEX_CHUNK_READ_FAILED", "Unable to read chunks.jsonl.") from exc
    result = tuple(sorted(chunks, key=lambda item: item.chunkId))
    summaries = {item.chunkId: item for item in manifest.chunks}
    if len(result) != manifest.chunkCount or len(summaries) != len(result):
        raise RagBuildError("INDEX_CHUNK_COUNT_MISMATCH", "Chunk counts are inconsistent.")
    for index, chunk in enumerate(result):
        summary = summaries.get(chunk.chunkId)
        if (
            summary is None
            or summary.contentSha256 != chunk.contentSha256
            or summary.embeddingRef != f"vector/row/{index}"
            or chunk.embeddingRef != summary.embeddingRef
        ):
            raise RagBuildError(
                "INDEX_CHUNK_MANIFEST_MISMATCH",
                f"Chunk does not match its manifest: {chunk.chunkId}",
            )
    return corpus, manifest, result


def _normalize(value: str) -> str:
    return unicodedata.normalize("NFKC", value).lower().strip()


def _base_tokens(value: str) -> list[str]:
    normalized = _normalize(value)
    tokens: list[str] = []
    for match in _CJK.finditer(normalized):
        span = match.group()
        tokens.extend(span)
        tokens.extend(span[index : index + 2] for index in range(len(span) - 1))
        if len(span) <= 24:
            tokens.append(span)
    tokens.extend(match.group().lower() for match in _LATIN_NUMBER.finditer(normalized))
    return tokens


def lexical_tokens(value: str, controlled_terms: Sequence[str] = ()) -> tuple[str, ...]:
    normalized = _normalize(value)
    tokens = _base_tokens(normalized)
    for term in controlled_terms:
        normalized_term = _normalize(term)
        if normalized_term and normalized_term in normalized:
            tokens.append(normalized_term.replace(" ", ""))
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


def _create_lexical_index(path: Path, chunks: tuple[ChunkContract, ...]) -> None:
    connection = sqlite3.connect(path)
    try:
        connection.execute("PRAGMA page_size=4096")
        connection.execute("PRAGMA journal_mode=DELETE")
        connection.execute("PRAGMA synchronous=FULL")
        connection.execute("PRAGMA application_id=1279346482")
        connection.execute("PRAGMA user_version=1")
        connection.executescript(
            """
            CREATE TABLE metadata (key TEXT PRIMARY KEY, value TEXT NOT NULL) WITHOUT ROWID;
            CREATE TABLE chunks (
                chunk_id TEXT PRIMARY KEY,
                document_id TEXT NOT NULL,
                chunk_type TEXT NOT NULL,
                knowledge_domain TEXT NOT NULL,
                product_families_json TEXT NOT NULL,
                allowed_roles_json TEXT NOT NULL
            ) WITHOUT ROWID;
            CREATE VIRTUAL TABLE chunk_fts USING fts5(
                chunk_id UNINDEXED,
                title_tokens,
                content_tokens,
                keyword_tokens,
                tokenize='unicode61 remove_diacritics 0'
            );
            """
        )
        connection.executemany(
            "INSERT INTO metadata(key, value) VALUES (?, ?)",
            (
                ("builderVersion", INDEX_BUILDER_VERSION),
                ("implementation", "SQLite FTS5 with explicit CJK unigram/bigram tokens"),
                ("schemaVersion", "1"),
            ),
        )
        for rowid, chunk in enumerate(chunks, start=1):
            terms = _chunk_controlled_terms(chunk)
            connection.execute(
                "INSERT INTO chunks VALUES (?, ?, ?, ?, ?, ?)",
                (
                    chunk.chunkId,
                    chunk.documentId,
                    chunk.chunkType.value,
                    chunk.knowledgeDomain.value,
                    json.dumps(chunk.productFamilies, ensure_ascii=False, separators=(",", ":")),
                    json.dumps(chunk.allowedRoles, ensure_ascii=False, separators=(",", ":")),
                ),
            )
            connection.execute(
                "INSERT INTO chunk_fts(rowid, chunk_id, title_tokens, content_tokens, keyword_tokens) "
                "VALUES (?, ?, ?, ?, ?)",
                (
                    rowid,
                    chunk.chunkId,
                    " ".join(lexical_tokens(chunk.title, terms)),
                    " ".join(lexical_tokens(chunk.content, terms)),
                    " ".join(
                        lexical_tokens(" ".join((*chunk.productFamilies, *chunk.keywords)), terms)
                    ),
                ),
            )
        connection.commit()
        connection.execute("VACUUM")
        connection.execute("PRAGMA optimize")
    finally:
        connection.close()


def _index_manifest_hash(manifest: IndexManifest) -> str:
    payload = manifest.model_dump(mode="json")
    payload.pop("builtAt")
    payload.pop("indexManifestSha256")
    return _canonical_hash(payload)


def build_indexes(
    release_root: Path,
    provider: EmbeddingProvider,
    *,
    built_at: datetime | None = None,
) -> tuple[IndexManifest, CorpusManifest]:
    release = release_root.expanduser().resolve(strict=True)
    corpus, chunk_manifest, chunks = _load_chunks(release)
    indexes_root = release / "indexes"
    if any(
        path.exists()
        for path in (
            indexes_root / "lexical",
            indexes_root / "vector",
            indexes_root / "index-manifest.json",
        )
    ):
        raise RagBuildError(
            "INDEX_ARTIFACT_ALREADY_EXISTS",
            "Index artifacts already exist and will not be overwritten.",
        )
    effective_time = built_at or datetime.now(ZoneInfo(TIMEZONE))
    staging = indexes_root / f".staging-{uuid.uuid4().hex}"
    try:
        lexical_root = staging / "lexical"
        vector_root = staging / "vector"
        lexical_root.mkdir(parents=True)
        vector_root.mkdir()
        lexical_path = lexical_root / "chunks.sqlite3"
        _create_lexical_index(lexical_path, chunks)
        lexical_sha = _sha256_file(lexical_path)

        texts = [chunk.content for chunk in chunks]
        matrix = np.asarray(provider.embed_documents(texts), dtype=np.float32)
        expected_shape = (len(chunks), provider.contract.dimension)
        if matrix.shape != expected_shape or not np.isfinite(matrix).all():
            raise RagBuildError(
                "VECTOR_INDEX_SHAPE_INVALID",
                f"Vector matrix shape must be {expected_shape}, received {matrix.shape}.",
            )
        norms = np.linalg.norm(matrix, axis=1)
        if not np.allclose(norms, 1.0, atol=1e-4):
            raise RagBuildError("VECTOR_INDEX_NOT_NORMALIZED", "Vector rows must be L2 normalized.")
        matrix_path = vector_root / "embeddings.npy"
        np.save(matrix_path, np.ascontiguousarray(matrix, dtype=np.float32), allow_pickle=False)
        row_map_path = vector_root / "row-map.jsonl"
        _write_jsonl(
            row_map_path,
            (
                {
                    "row": row,
                    "chunkId": chunk.chunkId,
                    "documentId": chunk.documentId,
                    "contentSha256": chunk.contentSha256,
                }
                for row, chunk in enumerate(chunks)
            ),
        )
        matrix_sha = _sha256_file(matrix_path)
        row_map_sha = _sha256_file(row_map_path)
        vector_sha = _canonical_hash(
            {"embeddingsSha256": matrix_sha, "rowMapSha256": row_map_sha}
        )
        draft = IndexManifest(
            corpusVersion=corpus.corpusVersion,
            builtAt=effective_time,
            documentCount=corpus.documentCount,
            chunkCount=len(chunks),
            documentManifestSha256=corpus.documentManifestSha256,
            chunkManifestSha256=chunk_manifest.chunkManifestSha256,
            embedding=provider.contract,
            retrieval={
                "implementation": "reciprocal rank fusion with evidence-type boosts",
                "version": RETRIEVAL_FUSION_VERSION,
                "rrfK": RRF_K,
                "lexicalCandidates": 20,
                "vectorCandidates": 20,
                "exactPhraseBoost": 0.05,
                "controlPointTypeBoost": 0.02,
                "flowOverviewTypeBoost": 0.02,
                "accessGate": "corpus.allowedRoles-before-search",
            },
            lexical=IndexComponentContract(
                status=BuildStatus.SUCCEEDED,
                implementation="SQLite FTS5 CJK unigram/bigram",
                version=LEXICAL_INDEX_VERSION,
                sha256=lexical_sha,
                itemCount=len(chunks),
                metadata={"artifact": "lexical/chunks.sqlite3"},
            ),
            vector=IndexComponentContract(
                status=BuildStatus.SUCCEEDED,
                implementation="NumPy float32 cosine matrix",
                version=VECTOR_INDEX_VERSION,
                sha256=vector_sha,
                itemCount=len(chunks),
                metadata={
                    "embeddingsArtifact": "vector/embeddings.npy",
                    "embeddingsSha256": matrix_sha,
                    "rowMapArtifact": "vector/row-map.jsonl",
                    "rowMapSha256": row_map_sha,
                },
            ),
            indexManifestSha256="0" * 64,
        )
        manifest = draft.model_copy(update={"indexManifestSha256": _index_manifest_hash(draft)})
        _write_json(staging / "index-manifest.json", manifest.model_dump(mode="json"))
        output_corpus = corpus.model_copy(
            update={
                "builtAt": effective_time,
                "lexicalIndexSha256": lexical_sha,
                "vectorIndexSha256": vector_sha,
                "embedding": provider.contract,
            }
        )
        _write_json(staging / "corpus-manifest.json", output_corpus.model_dump(mode="json"))
        (staging / "lexical").replace(indexes_root / "lexical")
        (staging / "vector").replace(indexes_root / "vector")
        (staging / "index-manifest.json").replace(indexes_root / "index-manifest.json")
        os.replace(staging / "corpus-manifest.json", release / "corpus-manifest.json")
    except OSError as exc:
        raise RagBuildError("INDEX_OUTPUT_WRITE_FAILED", "Unable to write index artifacts.") from exc
    finally:
        if staging.exists():
            shutil.rmtree(staging, ignore_errors=True)
    return manifest, output_corpus


class HybridIndex:
    def __init__(self, release_root: Path, provider: EmbeddingProvider | None = None) -> None:
        self.release_root = release_root.expanduser().resolve(strict=True)
        self.provider = provider
        self.corpus, self.chunk_manifest, chunks = _load_chunks(self.release_root)
        self.chunks = {chunk.chunkId: chunk for chunk in chunks}
        self.ordered_chunks = chunks
        self.index_manifest = _load_model(
            self.release_root / "indexes" / "index-manifest.json",
            IndexManifest,
            "SEARCH_INDEX_MANIFEST_INVALID",
        )
        if _index_manifest_hash(self.index_manifest) != self.index_manifest.indexManifestSha256:
            raise RagBuildError("SEARCH_INDEX_MANIFEST_HASH_INVALID", "Index manifest checksum is invalid.")
        if (
            self.index_manifest.corpusVersion != self.corpus.corpusVersion
            or self.index_manifest.documentManifestSha256 != self.corpus.documentManifestSha256
            or self.index_manifest.chunkManifestSha256 != self.chunk_manifest.chunkManifestSha256
            or self.index_manifest.chunkCount != len(chunks)
        ):
            raise RagBuildError(
                "SEARCH_INDEX_LINEAGE_INVALID",
                "The index manifest does not match the loaded corpus and chunks.",
            )
        if provider is not None and provider.contract != self.index_manifest.embedding:
            raise RagBuildError(
                "SEARCH_EMBEDDING_CONTRACT_MISMATCH",
                "The query embedding provider does not match the indexed model contract.",
            )
        lexical_path = self.release_root / "indexes" / "lexical" / "chunks.sqlite3"
        if (
            self.corpus.lexicalIndexSha256 != self.index_manifest.lexical.sha256
            or _sha256_file(lexical_path) != self.index_manifest.lexical.sha256
        ):
            raise RagBuildError("SEARCH_LEXICAL_HASH_INVALID", "Lexical index checksum is invalid.")
        self.lexical_path = lexical_path
        matrix_path = self.release_root / "indexes" / "vector" / "embeddings.npy"
        row_map_path = self.release_root / "indexes" / "vector" / "row-map.jsonl"
        vector_metadata = self.index_manifest.vector.metadata
        matrix_sha = _sha256_file(matrix_path)
        row_map_sha = _sha256_file(row_map_path)
        vector_sha = _canonical_hash(
            {"embeddingsSha256": matrix_sha, "rowMapSha256": row_map_sha}
        )
        if (
            matrix_sha != vector_metadata.get("embeddingsSha256")
            or row_map_sha != vector_metadata.get("rowMapSha256")
            or vector_sha != self.index_manifest.vector.sha256
            or vector_sha != self.corpus.vectorIndexSha256
        ):
            raise RagBuildError("SEARCH_VECTOR_HASH_INVALID", "Vector index checksum is invalid.")
        self.matrix = np.load(matrix_path, mmap_mode="r", allow_pickle=False)
        if self.matrix.shape != (len(chunks), self.index_manifest.embedding.dimension):
            raise RagBuildError(
                "SEARCH_VECTOR_SHAPE_INVALID",
                "The vector matrix shape does not match the index manifest.",
            )
        try:
            row_map = tuple(
                json.loads(line)
                for line in row_map_path.read_text(encoding="utf-8").splitlines()
                if line.strip()
            )
        except (OSError, UnicodeError, json.JSONDecodeError) as exc:
            raise RagBuildError("SEARCH_ROW_MAP_INVALID", "The vector row map is invalid.") from exc
        if len(row_map) != len(chunks) or any(
            item != {
                "row": row,
                "chunkId": chunk.chunkId,
                "documentId": chunk.documentId,
                "contentSha256": chunk.contentSha256,
            }
            for row, (item, chunk) in enumerate(zip(row_map, chunks, strict=True))
        ):
            raise RagBuildError(
                "SEARCH_ROW_MAP_MISMATCH",
                "The vector row map does not match the ordered chunks.",
            )
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

    @staticmethod
    def _allowed(chunk: ChunkContract, role: str) -> bool:
        return role.upper() in chunk.allowedRoles

    @staticmethod
    def _matches_filters(
        chunk: ChunkContract,
        knowledge_domain: KnowledgeDomain | None,
        product_family: str | None,
    ) -> bool:
        return (
            (knowledge_domain is None or chunk.knowledgeDomain == knowledge_domain)
            and (product_family is None or product_family in chunk.productFamilies)
        )

    def _candidate_ids(
        self,
        role: str,
        knowledge_domain: KnowledgeDomain | None,
        product_family: str | None,
    ) -> set[str]:
        return {
            chunk.chunkId
            for chunk in self.ordered_chunks
            if self._allowed(chunk, role)
            and self._matches_filters(chunk, knowledge_domain, product_family)
        }

    def _lexical_rank(self, query: str, candidates: set[str], limit: int) -> list[str]:
        tokens = lexical_tokens(query, self.controlled_terms)
        if not tokens:
            return []
        expression = " OR ".join(f'"{token.replace(chr(34), chr(34) * 2)}"' for token in tokens)
        connection = sqlite3.connect(f"file:{self.lexical_path.as_posix()}?mode=ro", uri=True)
        try:
            rows = connection.execute(
                "SELECT chunk_id, bm25(chunk_fts, 8.0, 1.0, 5.0) AS score "
                "FROM chunk_fts WHERE chunk_fts MATCH ? ORDER BY score, chunk_id LIMIT ?",
                (expression, max(limit * 8, 50)),
            ).fetchall()
        finally:
            connection.close()
        return [chunk_id for chunk_id, _ in rows if chunk_id in candidates][:limit]

    def _vector_rank(self, query: str, candidates: set[str], limit: int) -> list[str]:
        if self.provider is None:
            return []
        vector = self.provider.embed_queries([query])[0]
        scores = np.asarray(self.matrix @ vector, dtype=np.float32)
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
        if (
            chunk.chunkType == ChunkType.FLOW_OVERVIEW
            and any(term in normalized for term in ("完整流程", "流程顺序"))
        ):
            return 0.02
        return 0.0

    def search(
        self,
        query: str,
        *,
        role: str = "ADMIN",
        mode: RetrievalMode = RetrievalMode.HYBRID,
        limit: int = 5,
        knowledge_domain: KnowledgeDomain | None = None,
        product_family: str | None = None,
    ) -> RetrievalResult:
        started = time.perf_counter()
        cleaned = query.strip()
        if not cleaned:
            return RetrievalResult(
                status=RetrievalStatus.INVALID_QUERY,
                mode=mode,
                query="",
                elapsedMilliseconds=(time.perf_counter() - started) * 1000,
                reason="query must not be blank",
            )
        if role.upper() not in self.corpus.allowedRoles:
            return RetrievalResult(
                status=RetrievalStatus.FORBIDDEN,
                mode=mode,
                query=cleaned,
                elapsedMilliseconds=(time.perf_counter() - started) * 1000,
                reason="the current corpus is restricted to administrators",
            )
        candidates = self._candidate_ids(role, knowledge_domain, product_family)
        lexical = self._lexical_rank(cleaned, candidates, 20) if mode != RetrievalMode.VECTOR else []
        vector_available = self.provider is not None
        vector = (
            self._vector_rank(cleaned, candidates, 20)
            if mode != RetrievalMode.LEXICAL and vector_available
            else []
        )
        if mode == RetrievalMode.VECTOR and not vector_available:
            return RetrievalResult(
                status=RetrievalStatus.UNAVAILABLE,
                mode=mode,
                query=cleaned,
                elapsedMilliseconds=(time.perf_counter() - started) * 1000,
                reason="vector embedding provider is unavailable",
            )
        scores: dict[str, float] = {}
        for ranking in (lexical, vector):
            for rank, chunk_id in enumerate(ranking, start=1):
                scores[chunk_id] = scores.get(chunk_id, 0.0) + 1.0 / (RRF_K + rank)
        for chunk_id in tuple(scores):
            if self._exact_match(cleaned, self.chunks[chunk_id]):
                scores[chunk_id] += 0.05
            scores[chunk_id] += self._evidence_type_boost(cleaned, self.chunks[chunk_id])
        ranked = sorted(scores, key=lambda chunk_id: (-scores[chunk_id], chunk_id))[: max(1, min(limit, 10))]
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
            reason = "vector embedding provider is unavailable; lexical results only"
        return RetrievalResult(
            status=status,
            mode=mode,
            query=cleaned,
            elapsedMilliseconds=(time.perf_counter() - started) * 1000,
            hits=hits,
            reason=reason,
        )
