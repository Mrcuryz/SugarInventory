from __future__ import annotations

import hashlib
import importlib.metadata
from pathlib import Path
import stat
from typing import Sequence

import numpy as np

from app.rag.contracts import EmbeddingContract
from app.rag.runtime.errors import RagRuntimeError


DEFAULT_MODEL_NAME = "BAAI/bge-small-zh-v1.5"
DEFAULT_DIMENSION = 512
DEFAULT_MAX_TOKENS = 512


def _is_link_or_reparse_point(path: Path) -> bool:
    try:
        attributes = getattr(path.lstat(), "st_file_attributes", 0)
    except OSError as exc:
        raise RagRuntimeError(
            "RAG_EMBEDDING_MODEL_READ_FAILED", "Unable to inspect the embedding model."
        ) from exc
    return path.is_symlink() or bool(
        attributes & getattr(stat, "FILE_ATTRIBUTE_REPARSE_POINT", 0)
    )


def hash_model_directory(model_path: Path) -> str:
    candidate = model_path.expanduser().absolute()
    try:
        if not candidate.exists() or _is_link_or_reparse_point(candidate):
            raise RagRuntimeError(
                "RAG_EMBEDDING_MODEL_PATH_INVALID",
                "The approved local embedding model path is invalid.",
            )
        root = candidate.resolve(strict=True)
    except RagRuntimeError:
        raise
    except OSError as exc:
        raise RagRuntimeError(
            "RAG_EMBEDDING_MODEL_PATH_INVALID",
            "The approved local embedding model path is invalid.",
        ) from exc
    if not root.is_dir() or _is_link_or_reparse_point(root):
        raise RagRuntimeError(
            "RAG_EMBEDDING_MODEL_PATH_INVALID",
            "The approved local embedding model path is invalid.",
        )
    entries = sorted(root.rglob("*"))
    if any(_is_link_or_reparse_point(entry) for entry in entries):
        raise RagRuntimeError(
            "RAG_EMBEDDING_MODEL_LINK_FORBIDDEN",
            "Linked entries are forbidden inside the embedding model.",
        )
    files = [path for path in entries if path.is_file()]
    if not files:
        raise RagRuntimeError("RAG_EMBEDDING_MODEL_EMPTY", "The embedding model is empty.")
    digest = hashlib.sha256()
    try:
        for path in files:
            relative = path.relative_to(root).as_posix().encode("utf-8")
            digest.update(len(relative).to_bytes(4, "big"))
            digest.update(relative)
            file_digest = hashlib.sha256()
            with path.open("rb") as handle:
                for block in iter(lambda: handle.read(1024 * 1024), b""):
                    file_digest.update(block)
            digest.update(file_digest.digest())
    except OSError as exc:
        raise RagRuntimeError(
            "RAG_EMBEDDING_MODEL_READ_FAILED", "Unable to read the embedding model."
        ) from exc
    return digest.hexdigest()


def _validate_matrix(matrix: np.ndarray, count: int, dimension: int) -> np.ndarray:
    result = np.asarray(matrix, dtype=np.float32)
    if result.shape != (count, dimension):
        raise RagRuntimeError(
            "RAG_QUERY_EMBEDDING_DIMENSION_MISMATCH",
            "Query embedding shape does not match the approved index.",
        )
    if not np.isfinite(result).all():
        raise RagRuntimeError(
            "RAG_QUERY_EMBEDDING_NON_FINITE", "Query embedding contains invalid values."
        )
    norms = np.linalg.norm(result, axis=1, keepdims=True)
    if (norms <= 0).any():
        raise RagRuntimeError(
            "RAG_QUERY_EMBEDDING_ZERO_VECTOR", "Query embedding contains a zero vector."
        )
    return np.ascontiguousarray(result / norms, dtype=np.float32)


class FastEmbedQueryProvider:
    """Local, query-only provider; document vectors are never generated at runtime."""

    def __init__(
        self,
        model_path: Path,
        *,
        model_name: str = DEFAULT_MODEL_NAME,
        threads: int = 2,
    ) -> None:
        candidate = model_path.expanduser().absolute()
        try:
            if not candidate.exists() or _is_link_or_reparse_point(candidate):
                raise RagRuntimeError(
                    "RAG_EMBEDDING_MODEL_PATH_INVALID",
                    "The approved local embedding model path is invalid.",
                )
            path = candidate.resolve(strict=True)
        except RagRuntimeError:
            raise
        except OSError as exc:
            raise RagRuntimeError(
                "RAG_EMBEDDING_MODEL_PATH_INVALID",
                "The approved local embedding model path is invalid.",
            ) from exc
        required = {"model_optimized.onnx", "tokenizer.json", "config.json"}
        try:
            available = {item.name for item in path.iterdir() if item.is_file()}
        except OSError as exc:
            raise RagRuntimeError(
                "RAG_EMBEDDING_MODEL_READ_FAILED", "Unable to inspect the embedding model."
            ) from exc
        if (
            not path.is_dir()
            or _is_link_or_reparse_point(path)
            or not required.issubset(available)
        ):
            raise RagRuntimeError(
                "RAG_EMBEDDING_MODEL_INCOMPLETE",
                "The approved local embedding model directory is incomplete.",
            )
        try:
            from fastembed import TextEmbedding
        except ImportError as exc:
            raise RagRuntimeError(
                "RAG_EMBEDDING_PROVIDER_UNAVAILABLE",
                "The runtime embedding provider is unavailable.",
            ) from exc
        try:
            self._model = TextEmbedding(
                model_name=model_name,
                specific_model_path=str(path),
                threads=threads,
                local_files_only=True,
            )
            provider_version = importlib.metadata.version("fastembed")
            model_sha256 = hash_model_directory(path)
        except RagRuntimeError:
            raise
        except Exception as exc:
            raise RagRuntimeError(
                "RAG_EMBEDDING_MODEL_LOAD_FAILED",
                "Unable to load the approved local embedding model.",
            ) from exc
        self._contract = EmbeddingContract(
            provider="fastembed",
            providerVersion=provider_version,
            model=model_name,
            dimension=DEFAULT_DIMENSION,
            normalization="L2",
            maxTokens=DEFAULT_MAX_TOKENS,
            modelArtifactSha256=model_sha256,
        )

    @property
    def contract(self) -> EmbeddingContract:
        return self._contract

    def embed_queries(self, texts: Sequence[str]) -> np.ndarray:
        if not texts:
            return np.empty((0, self.contract.dimension), dtype=np.float32)
        try:
            vectors = np.stack(tuple(self._model.query_embed(list(texts))))
            return _validate_matrix(vectors, len(texts), self.contract.dimension)
        except RagRuntimeError:
            raise
        except Exception as exc:
            raise RagRuntimeError(
                "RAG_QUERY_EMBEDDING_FAILED", "Unable to embed the knowledge query."
            ) from exc
