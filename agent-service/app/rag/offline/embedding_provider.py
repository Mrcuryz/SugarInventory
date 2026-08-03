from __future__ import annotations

import hashlib
import importlib.metadata
from pathlib import Path
from typing import Protocol, Sequence

import numpy as np

from app.rag.contracts import EmbeddingContract
from app.rag.offline.source_inventory import RagBuildError


DEFAULT_MODEL_NAME = "BAAI/bge-small-zh-v1.5"
DEFAULT_DIMENSION = 512
DEFAULT_MAX_TOKENS = 512


class EmbeddingProvider(Protocol):
    @property
    def contract(self) -> EmbeddingContract: ...

    def embed_documents(self, texts: Sequence[str]) -> np.ndarray: ...

    def embed_queries(self, texts: Sequence[str]) -> np.ndarray: ...


def hash_model_directory(model_path: Path) -> str:
    root = model_path.expanduser().resolve(strict=True)
    if not root.is_dir() or root.is_symlink():
        raise RagBuildError(
            "EMBEDDING_MODEL_PATH_INVALID",
            "The local embedding model path must be a regular directory.",
        )
    files = sorted(path for path in root.rglob("*") if path.is_file())
    if not files:
        raise RagBuildError("EMBEDDING_MODEL_EMPTY", "The local embedding model is empty.")
    digest = hashlib.sha256()
    for path in files:
        if path.is_symlink():
            raise RagBuildError(
                "EMBEDDING_MODEL_LINK_FORBIDDEN",
                "Linked files are forbidden inside the local embedding model.",
            )
        relative = path.relative_to(root).as_posix().encode("utf-8")
        digest.update(len(relative).to_bytes(4, "big"))
        digest.update(relative)
        file_digest = hashlib.sha256()
        with path.open("rb") as handle:
            for block in iter(lambda: handle.read(1024 * 1024), b""):
                file_digest.update(block)
        digest.update(file_digest.digest())
    return digest.hexdigest()


def _validate_matrix(matrix: np.ndarray, count: int, dimension: int) -> np.ndarray:
    result = np.asarray(matrix, dtype=np.float32)
    if result.shape != (count, dimension):
        raise RagBuildError(
            "EMBEDDING_DIMENSION_MISMATCH",
            f"Embedding shape {result.shape} does not match ({count}, {dimension}).",
        )
    if not np.isfinite(result).all():
        raise RagBuildError("EMBEDDING_NON_FINITE", "Embedding output contains non-finite values.")
    norms = np.linalg.norm(result, axis=1, keepdims=True)
    if (norms <= 0).any():
        raise RagBuildError("EMBEDDING_ZERO_VECTOR", "Embedding output contains a zero vector.")
    return np.ascontiguousarray(result / norms, dtype=np.float32)


class FastEmbedLocalProvider:
    def __init__(
        self,
        model_path: Path,
        *,
        model_name: str = DEFAULT_MODEL_NAME,
        threads: int = 2,
    ) -> None:
        path = model_path.expanduser().resolve(strict=True)
        required = {"model_optimized.onnx", "tokenizer.json", "config.json"}
        if not required.issubset(item.name for item in path.iterdir() if item.is_file()):
            raise RagBuildError(
                "EMBEDDING_MODEL_INCOMPLETE",
                "The local FastEmbed model directory is incomplete.",
            )
        try:
            from fastembed import TextEmbedding
        except ImportError as exc:
            raise RagBuildError(
                "EMBEDDING_PROVIDER_UNAVAILABLE",
                "fastembed is required to build the vector index.",
            ) from exc
        try:
            self._model = TextEmbedding(
                model_name=model_name,
                specific_model_path=str(path),
                threads=threads,
                local_files_only=True,
            )
        except Exception as exc:
            raise RagBuildError(
                "EMBEDDING_MODEL_LOAD_FAILED",
                "Unable to load the approved local embedding model.",
            ) from exc
        self._contract = EmbeddingContract(
            provider="fastembed",
            providerVersion=importlib.metadata.version("fastembed"),
            model=model_name,
            dimension=DEFAULT_DIMENSION,
            normalization="L2",
            maxTokens=DEFAULT_MAX_TOKENS,
            modelArtifactSha256=hash_model_directory(path),
        )

    @property
    def contract(self) -> EmbeddingContract:
        return self._contract

    def embed_documents(self, texts: Sequence[str]) -> np.ndarray:
        if not texts:
            return np.empty((0, self.contract.dimension), dtype=np.float32)
        vectors = np.stack(tuple(self._model.passage_embed(list(texts))))
        return _validate_matrix(vectors, len(texts), self.contract.dimension)

    def embed_queries(self, texts: Sequence[str]) -> np.ndarray:
        if not texts:
            return np.empty((0, self.contract.dimension), dtype=np.float32)
        vectors = np.stack(tuple(self._model.query_embed(list(texts))))
        return _validate_matrix(vectors, len(texts), self.contract.dimension)
