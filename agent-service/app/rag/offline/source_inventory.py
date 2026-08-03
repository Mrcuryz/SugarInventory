from __future__ import annotations

import hashlib
import json
import os
import shutil
import stat
import subprocess
import unicodedata
import uuid
from collections.abc import Iterable
from datetime import datetime
from pathlib import Path, PurePosixPath
from zoneinfo import ZoneInfo

from app.rag.config import (
    CORPUS_ID,
    PROJECT_ROOT,
    SOURCE_BASIS,
    SUPPORTED_SOURCE_SUFFIXES,
    TIMEZONE,
)
from app.rag.contracts import SourceFileRecord, SourceInventoryManifest, SourceType


BUILDER_VERSION = "rag-source-inventory/1.0.0"
HASH_CHUNK_SIZE = 1024 * 1024
RELEASE_DIRECTORIES = ("documents", "chunks", "index", "qa", "evaluation")


class RagBuildError(RuntimeError):
    def __init__(self, code: str, message: str):
        super().__init__(message)
        self.code = code


def _is_reparse_point(path: Path) -> bool:
    try:
        attributes = getattr(path.lstat(), "st_file_attributes", 0)
    except OSError as exc:
        raise RagBuildError("SOURCE_STAT_FAILED", "Unable to inspect a source entry.") from exc
    return bool(attributes & getattr(stat, "FILE_ATTRIBUTE_REPARSE_POINT", 0))


def _reject_link(path: Path, *, code: str, message: str) -> None:
    if path.is_symlink() or _is_reparse_point(path):
        raise RagBuildError(code, message)


def resolve_source_root(source_root: Path) -> Path:
    source = source_root.expanduser().absolute()
    if not source.exists():
        raise RagBuildError("SOURCE_NOT_FOUND", "The source directory does not exist.")
    _reject_link(
        source,
        code="SOURCE_LINK_FORBIDDEN",
        message="The source directory must not be a symbolic link or reparse point.",
    )
    if not source.is_dir():
        raise RagBuildError("SOURCE_NOT_DIRECTORY", "The source path must be a directory.")
    return source.resolve(strict=True)


def resolve_inventory_source_file(
    source_root: Path,
    record: SourceFileRecord,
) -> Path:
    root = resolve_source_root(source_root)
    current = root
    for component in PurePosixPath(record.relativePath).parts:
        current /= component
        if current.exists() or current.is_symlink():
            _reject_link(
                current,
                code="SOURCE_LINK_FORBIDDEN",
                message=f"Linked source entries are forbidden: {record.relativePath}",
            )
    try:
        resolved = current.resolve(strict=True)
        resolved.relative_to(root)
    except (OSError, ValueError) as exc:
        raise RagBuildError(
            "SOURCE_INVENTORY_PATH_INVALID",
            f"An inventoried source file is missing or escaped the source root: {record.relativePath}",
        ) from exc
    if not resolved.is_file():
        raise RagBuildError(
            "SOURCE_INVENTORY_PATH_INVALID",
            f"An inventoried source entry is not a regular file: {record.relativePath}",
        )
    actual = _build_file_record(root, resolved)
    if actual != record:
        raise RagBuildError(
            "SOURCE_INVENTORY_MISMATCH",
            f"An inventoried source file no longer matches its manifest: {record.relativePath}",
        )
    return resolved


def _walk_source(root: Path) -> tuple[list[Path], list[str]]:
    supported: list[Path] = []
    ignored: list[str] = []
    pending = [root]

    while pending:
        directory = pending.pop()
        try:
            entries = list(os.scandir(directory))
        except OSError as exc:
            raise RagBuildError("SOURCE_READ_FAILED", "Unable to read the source directory.") from exc

        for entry in entries:
            path = Path(entry.path)
            relative = _normalized_relative_path(root, path)
            if entry.is_symlink() or _is_reparse_point(path):
                raise RagBuildError(
                    "SOURCE_LINK_FORBIDDEN",
                    f"Linked source entries are forbidden: {relative}",
                )
            if entry.is_dir(follow_symlinks=False):
                pending.append(path)
                continue
            if not entry.is_file(follow_symlinks=False):
                raise RagBuildError(
                    "SOURCE_ENTRY_UNSUPPORTED",
                    f"Only regular source files are supported: {relative}",
                )
            if path.suffix.lower() in SUPPORTED_SOURCE_SUFFIXES:
                supported.append(path)
            else:
                ignored.append(relative)

    supported.sort(
        key=lambda path: (
            _normalized_relative_path(root, path).casefold(),
            _normalized_relative_path(root, path),
        )
    )
    ignored.sort(key=lambda item: (item.casefold(), item))
    return supported, ignored


def _normalized_relative_path(root: Path, path: Path) -> str:
    relative = path.relative_to(root).as_posix()
    return unicodedata.normalize("NFC", relative)


def _sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    try:
        _reject_link(
            path,
            code="SOURCE_LINK_FORBIDDEN",
            message="Linked source entries are forbidden.",
        )
        with path.open("rb") as source:
            while block := source.read(HASH_CHUNK_SIZE):
                digest.update(block)
    except OSError as exc:
        raise RagBuildError("SOURCE_HASH_FAILED", "Unable to hash a source file.") from exc
    return digest.hexdigest()


def _stable_document_id(relative_path: str) -> str:
    normalized = unicodedata.normalize("NFC", relative_path).casefold()
    digest = hashlib.sha256(f"document-id-v1\0{normalized}".encode("utf-8")).hexdigest()
    return f"doc-{digest[:24]}"


def _source_type(path: Path) -> SourceType:
    try:
        return SourceType(path.suffix.lstrip(".").upper())
    except ValueError as exc:
        raise RagBuildError("SOURCE_TYPE_UNSUPPORTED", "Unsupported source file type.") from exc


def _build_file_record(root: Path, path: Path) -> SourceFileRecord:
    relative_path = _normalized_relative_path(root, path)
    try:
        before = path.stat()
    except OSError as exc:
        raise RagBuildError("SOURCE_STAT_FAILED", "Unable to inspect a source file.") from exc
    source_sha256 = _sha256_file(path)
    try:
        after = path.stat()
    except OSError as exc:
        raise RagBuildError("SOURCE_STAT_FAILED", "Unable to inspect a source file.") from exc
    if (
        before.st_size != after.st_size
        or before.st_mtime_ns != after.st_mtime_ns
        or getattr(before, "st_ino", None) != getattr(after, "st_ino", None)
    ):
        raise RagBuildError(
            "SOURCE_CHANGED_DURING_SCAN",
            f"A source file changed while it was being inventoried: {relative_path}",
        )
    return SourceFileRecord(
        documentId=_stable_document_id(relative_path),
        relativePath=relative_path,
        sourceFileName=relative_path.rsplit("/", 1)[-1],
        sourceType=_source_type(path),
        sizeBytes=before.st_size,
        sourceSha256=source_sha256,
    )


def _canonical_inventory_payload(
    *,
    corpus_id: str,
    corpus_version: str,
    source_basis: str,
    files: Iterable[SourceFileRecord],
    ignored_paths: Iterable[str],
) -> dict[str, object]:
    records = tuple(files)
    ignored = tuple(ignored_paths)
    counts = {
        source_type.value: sum(record.sourceType == source_type for record in records)
        for source_type in SourceType
    }
    return {
        "schemaVersion": 1,
        "corpusId": corpus_id,
        "corpusVersion": corpus_version,
        "sourceBasis": source_basis,
        "builderVersion": BUILDER_VERSION,
        "fileCount": len(records),
        "totalBytes": sum(record.sizeBytes for record in records),
        "sourceTypeCounts": counts,
        "files": [record.model_dump(mode="json") for record in records],
        "ignoredFileCount": len(ignored),
        "ignoredRelativePaths": list(ignored),
    }


def compute_inventory_sha256(
    *,
    corpus_id: str,
    corpus_version: str,
    source_basis: str,
    files: Iterable[SourceFileRecord],
    ignored_paths: Iterable[str],
) -> str:
    payload = _canonical_inventory_payload(
        corpus_id=corpus_id,
        corpus_version=corpus_version,
        source_basis=source_basis,
        files=files,
        ignored_paths=ignored_paths,
    )
    canonical = json.dumps(
        payload,
        ensure_ascii=False,
        sort_keys=True,
        separators=(",", ":"),
    ).encode("utf-8")
    return hashlib.sha256(canonical).hexdigest()


def build_source_inventory(
    source_root: Path,
    corpus_version: str,
    *,
    generated_at: datetime | None = None,
    corpus_id: str = CORPUS_ID,
    source_basis: str = SOURCE_BASIS,
) -> SourceInventoryManifest:
    root = resolve_source_root(source_root)
    paths, ignored_paths = _walk_source(root)
    files = tuple(_build_file_record(root, path) for path in paths)
    inventory_sha256 = compute_inventory_sha256(
        corpus_id=corpus_id,
        corpus_version=corpus_version,
        source_basis=source_basis,
        files=files,
        ignored_paths=ignored_paths,
    )
    return SourceInventoryManifest(
        corpusId=corpus_id,
        corpusVersion=corpus_version,
        sourceBasis=source_basis,
        generatedAt=generated_at or datetime.now(ZoneInfo(TIMEZONE)),
        timezone=TIMEZONE,
        builderVersion=BUILDER_VERSION,
        fileCount=len(files),
        totalBytes=sum(item.sizeBytes for item in files),
        sourceTypeCounts={
            source_type: sum(item.sourceType == source_type for item in files)
            for source_type in SourceType
        },
        files=files,
        ignoredFileCount=len(ignored_paths),
        ignoredRelativePaths=tuple(ignored_paths),
        inventorySha256=inventory_sha256,
    )


def validate_inventory_checksum(manifest: SourceInventoryManifest) -> None:
    actual = compute_inventory_sha256(
        corpus_id=manifest.corpusId,
        corpus_version=manifest.corpusVersion,
        source_basis=manifest.sourceBasis,
        files=manifest.files,
        ignored_paths=manifest.ignoredRelativePaths,
    )
    if actual != manifest.inventorySha256:
        raise RagBuildError("INVENTORY_CHECKSUM_MISMATCH", "The source inventory checksum is invalid.")


def load_source_inventory(path: Path) -> SourceInventoryManifest:
    if not path.is_file():
        raise RagBuildError("INVENTORY_NOT_FOUND", "The source inventory file does not exist.")
    if path.stat().st_size > 10 * 1024 * 1024:
        raise RagBuildError("INVENTORY_TOO_LARGE", "The source inventory file is too large.")
    try:
        payload = json.loads(path.read_text(encoding="utf-8"))
        manifest = SourceInventoryManifest.model_validate(payload)
    except (OSError, UnicodeError, json.JSONDecodeError, ValueError) as exc:
        raise RagBuildError("INVENTORY_INVALID", "The source inventory file is invalid.") from exc
    validate_inventory_checksum(manifest)
    return manifest


def is_git_ignored(path: Path, project_root: Path = PROJECT_ROOT) -> bool:
    try:
        relative = path.relative_to(project_root).as_posix()
    except ValueError:
        return False
    try:
        result = subprocess.run(
            [
                "git",
                "-c",
                f"safe.directory={project_root.as_posix()}",
                "check-ignore",
                "--quiet",
                "--",
                relative,
            ],
            cwd=project_root,
            check=False,
            capture_output=True,
            text=True,
            timeout=5,
        )
    except (OSError, subprocess.SubprocessError):
        return False
    return result.returncode == 0


def validate_output_root(source_root: Path, output_root: Path) -> tuple[Path, Path]:
    source = resolve_source_root(source_root)
    raw_output = output_root.expanduser().absolute()
    project_root = PROJECT_ROOT.absolute()
    try:
        relative_output = raw_output.relative_to(project_root)
    except ValueError as exc:
        raise RagBuildError(
            "OUTPUT_OUTSIDE_PROJECT",
            "The output directory must be inside the project artifact area.",
        ) from exc
    current = project_root
    for component in relative_output.parts:
        current /= component
        if current.exists() or current.is_symlink():
            _reject_link(
                current,
                code="OUTPUT_LINK_FORBIDDEN",
                message="The output path must not contain a symbolic link or reparse point.",
            )
    output = raw_output.resolve(strict=False)
    try:
        output.relative_to(source)
    except ValueError:
        pass
    else:
        raise RagBuildError("OUTPUT_OVERLAPS_SOURCE", "The output directory must not be inside the source directory.")
    try:
        source.relative_to(output)
    except ValueError:
        pass
    else:
        raise RagBuildError("OUTPUT_OVERLAPS_SOURCE", "The source directory must not be inside the output directory.")
    try:
        output.relative_to(project_root)
    except ValueError as exc:
        raise RagBuildError(
            "OUTPUT_OUTSIDE_PROJECT",
            "The output directory must be inside the project artifact area.",
        ) from exc
    if not is_git_ignored(output):
        raise RagBuildError(
            "OUTPUT_NOT_GIT_IGNORED",
            "The output directory must be explicitly ignored by Git.",
        )
    return source, output


def write_inventory_release(
    source_root: Path,
    output_root: Path,
    corpus_version: str,
    *,
    generated_at: datetime | None = None,
) -> tuple[SourceInventoryManifest, Path]:
    source, output = validate_output_root(source_root, output_root)
    manifest = build_source_inventory(source, corpus_version, generated_at=generated_at)
    releases_root = output / "releases"
    release_root = releases_root / manifest.corpusVersion
    if release_root.exists():
        raise RagBuildError(
            "RELEASE_ALREADY_EXISTS",
            "The corpus release already exists and will not be overwritten.",
        )

    releases_root.mkdir(parents=True, exist_ok=True)
    staging_root = releases_root / f".{manifest.corpusVersion}.staging-{uuid.uuid4().hex}"
    try:
        staging_root.mkdir()
        for directory_name in RELEASE_DIRECTORIES:
            (staging_root / directory_name).mkdir()
        inventory_path = staging_root / "source-inventory.json"
        inventory_path.write_text(
            json.dumps(
                manifest.model_dump(mode="json"),
                ensure_ascii=False,
                indent=2,
                sort_keys=True,
            )
            + "\n",
            encoding="utf-8",
            newline="\n",
        )
        staging_root.replace(release_root)
    except OSError as exc:
        if staging_root.exists():
            shutil.rmtree(staging_root)
        raise RagBuildError("OUTPUT_WRITE_FAILED", "Unable to write the RAG artifact release.") from exc
    except Exception:
        if staging_root.exists():
            shutil.rmtree(staging_root)
        raise
    return manifest, release_root / "source-inventory.json"
