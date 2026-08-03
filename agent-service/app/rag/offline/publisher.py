from __future__ import annotations

from collections.abc import Callable
from dataclasses import dataclass
from datetime import datetime
import hashlib
import json
import os
from pathlib import Path
import shutil
import stat
import uuid
from zoneinfo import ZoneInfo

from pydantic import TypeAdapter, ValidationError

from app.rag.config import CORPUS_ID, SCHEMA_VERSION, TIMEZONE
from app.rag.contracts import (
    CorpusManifest,
    CorpusVersion,
    IndexManifest,
    RetrievalEvaluationReport,
)
from app.rag.offline.source_inventory import RagBuildError
from app.rag.runtime.contracts import CurrentCorpusPointer
from app.rag.runtime.corpus_loader import validate_release_artifacts
from app.rag.runtime.errors import RagRuntimeError


MAX_RELEASE_FILES = 10_000
MAX_RELEASE_BYTES = 2 * 1024 * 1024 * 1024
POINTER_MAX_BYTES = 64 * 1024


@dataclass(frozen=True)
class ValidatedRelease:
    release: Path
    pointer: CurrentCorpusPointer
    releaseSha256: str
    fileCount: int
    totalBytes: int
    documentCount: int
    chunkCount: int
    evaluationCaseCount: int


@dataclass(frozen=True)
class PublicationResult:
    action: str
    pointer: CurrentCorpusPointer
    previousCorpusVersion: str | None
    releaseSha256: str
    releaseCopied: bool
    pointerChanged: bool
    auditEvent: str | None


def _sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    try:
        with path.open("rb") as handle:
            for block in iter(lambda: handle.read(1024 * 1024), b""):
                digest.update(block)
    except OSError as exc:
        raise RagBuildError("PUBLISH_ARTIFACT_READ_FAILED", "Unable to read a release artifact.") from exc
    return digest.hexdigest()


def _is_reparse_stat(result: os.stat_result) -> bool:
    return bool(
        getattr(result, "st_file_attributes", 0)
        & getattr(stat, "FILE_ATTRIBUTE_REPARSE_POINT", 0)
    )


def _resolve_directory(path: Path, *, code: str, message: str) -> Path:
    candidate = path.expanduser().absolute()
    if not candidate.exists():
        raise RagBuildError(code, message)
    try:
        metadata = candidate.lstat()
    except OSError as exc:
        raise RagBuildError("PUBLISH_PATH_INSPECTION_FAILED", "Unable to inspect a publish path.") from exc
    if candidate.is_symlink() or _is_reparse_stat(metadata) or not candidate.is_dir():
        raise RagBuildError(code, message)
    try:
        return candidate.resolve(strict=True)
    except OSError as exc:
        raise RagBuildError("PUBLISH_PATH_INSPECTION_FAILED", "Unable to resolve a publish path.") from exc


def _scan_release_tree(root: Path) -> tuple[tuple[tuple[str, int, str], ...], int]:
    records: list[tuple[str, int, str]] = []
    total_bytes = 0
    pending = [root]
    while pending:
        directory = pending.pop()
        try:
            entries = tuple(os.scandir(directory))
        except OSError as exc:
            raise RagBuildError(
                "PUBLISH_RELEASE_SCAN_FAILED", "Unable to scan the release artifact."
            ) from exc
        for entry in entries:
            path = Path(entry.path)
            try:
                metadata = entry.stat(follow_symlinks=False)
            except OSError as exc:
                raise RagBuildError(
                    "PUBLISH_RELEASE_SCAN_FAILED", "Unable to inspect a release artifact."
                ) from exc
            if entry.is_symlink() or _is_reparse_stat(metadata):
                raise RagBuildError(
                    "PUBLISH_RELEASE_LINK_FORBIDDEN",
                    "Release artifacts must not contain links or reparse points.",
                )
            if entry.is_dir(follow_symlinks=False):
                pending.append(path)
                continue
            if not entry.is_file(follow_symlinks=False):
                raise RagBuildError(
                    "PUBLISH_RELEASE_ENTRY_INVALID",
                    "Release artifacts may contain only directories and regular files.",
                )
            try:
                relative = path.relative_to(root).as_posix()
            except ValueError as exc:
                raise RagBuildError(
                    "PUBLISH_RELEASE_PATH_ESCAPE", "A release artifact escaped its root."
                ) from exc
            total_bytes += metadata.st_size
            records.append((relative, metadata.st_size, _sha256_file(path)))
            if len(records) > MAX_RELEASE_FILES or total_bytes > MAX_RELEASE_BYTES:
                raise RagBuildError(
                    "PUBLISH_RELEASE_LIMIT_EXCEEDED",
                    "The release artifact exceeds its file or size limit.",
                )
    records.sort(key=lambda item: item[0])
    if not records:
        raise RagBuildError("PUBLISH_RELEASE_EMPTY", "The release artifact is empty.")
    return tuple(records), total_bytes


def _release_hash(records: tuple[tuple[str, int, str], ...]) -> str:
    encoded = json.dumps(
        records,
        ensure_ascii=False,
        separators=(",", ":"),
    ).encode("utf-8")
    return hashlib.sha256(encoded).hexdigest()


def _load_contract(path: Path, model_type: type, code: str):
    try:
        return model_type.model_validate_json(path.read_bytes())
    except (OSError, UnicodeError, ValidationError) as exc:
        raise RagBuildError(code, "A required release contract is invalid.") from exc


def validate_release(
    release_root: Path,
    *,
    published_by: str = "release-validation",
    published_at: datetime | None = None,
    require_version_directory_name: bool = True,
) -> ValidatedRelease:
    release = _resolve_directory(
        release_root,
        code="PUBLISH_RELEASE_NOT_FOUND",
        message="The release directory does not exist or is invalid.",
    )
    records, total_bytes = _scan_release_tree(release)
    corpus = _load_contract(
        release / "corpus-manifest.json", CorpusManifest, "PUBLISH_CORPUS_MANIFEST_INVALID"
    )
    index = _load_contract(
        release / "indexes" / "index-manifest.json",
        IndexManifest,
        "PUBLISH_INDEX_MANIFEST_INVALID",
    )
    report = _load_contract(
        release / "qa" / "retrieval-evaluation-report.json",
        RetrievalEvaluationReport,
        "PUBLISH_EVALUATION_REPORT_INVALID",
    )
    if require_version_directory_name and release.name != corpus.corpusVersion:
        raise RagBuildError(
            "PUBLISH_RELEASE_NAME_MISMATCH",
            "The release directory name must match its corpus version.",
        )
    effective_time = published_at or datetime.now(ZoneInfo(TIMEZONE))
    try:
        pointer = CurrentCorpusPointer(
            corpusVersion=corpus.corpusVersion,
            releaseName=corpus.corpusVersion,
            corpusManifestSha256=_sha256_file(release / "corpus-manifest.json"),
            indexManifestSha256=index.indexManifestSha256 or "",
            evaluationSetSha256=report.evaluationSetSha256,
            evaluationReportSha256=_sha256_file(
                release / "qa" / "retrieval-evaluation-report.json"
            ),
            publishedAt=effective_time,
            publishedBy=published_by,
        )
    except ValidationError as exc:
        raise RagBuildError("PUBLISH_POINTER_CONTRACT_INVALID", "The release pointer is invalid.") from exc
    try:
        retriever = validate_release_artifacts(release, pointer)
    except RagRuntimeError as exc:
        raise RagBuildError(exc.code, str(exc)) from exc
    return ValidatedRelease(
        release=release,
        pointer=pointer,
        releaseSha256=_release_hash(records),
        fileCount=len(records),
        totalBytes=total_bytes,
        documentCount=retriever.corpus.documentCount,
        chunkCount=retriever.corpus.chunkCount,
        evaluationCaseCount=report.caseCount,
    )


def _ensure_runtime_root(runtime_root: Path) -> Path:
    root = runtime_root.expanduser().absolute()
    if root == Path(root.anchor):
        raise RagBuildError(
            "PUBLISH_RUNTIME_ROOT_INVALID", "A filesystem root cannot be used as the runtime root."
        )
    parent = _resolve_directory(
        root.parent,
        code="PUBLISH_RUNTIME_PARENT_INVALID",
        message="The runtime root parent does not exist or is invalid.",
    )
    if root.exists():
        root = _resolve_directory(
            root,
            code="PUBLISH_RUNTIME_ROOT_INVALID",
            message="The runtime root is invalid.",
        )
    else:
        try:
            root.mkdir()
        except OSError as exc:
            raise RagBuildError(
                "PUBLISH_RUNTIME_ROOT_CREATE_FAILED", "Unable to create the runtime root."
            ) from exc
        root = root.resolve(strict=True)
    try:
        root.relative_to(parent)
    except ValueError as exc:
        raise RagBuildError("PUBLISH_RUNTIME_PATH_ESCAPE", "The runtime root escaped its parent.") from exc
    for name in ("releases", "audit"):
        target = root / name
        if target.exists():
            _resolve_directory(
                target,
                code="PUBLISH_RUNTIME_ROOT_INVALID",
                message="A runtime artifact directory is invalid.",
            )
        else:
            try:
                target.mkdir()
            except OSError as exc:
                raise RagBuildError(
                    "PUBLISH_RUNTIME_ROOT_CREATE_FAILED",
                    "Unable to create a runtime artifact directory.",
                ) from exc
    return root


def _paths_overlap(first: Path, second: Path) -> bool:
    try:
        first.relative_to(second)
        return True
    except ValueError:
        pass
    try:
        second.relative_to(first)
        return True
    except ValueError:
        return False


def _load_current_pointer(root: Path) -> CurrentCorpusPointer | None:
    path = root / "current.json"
    if not path.exists():
        return None
    try:
        metadata = path.lstat()
        if path.is_symlink() or _is_reparse_stat(metadata) or not path.is_file():
            raise RagBuildError("PUBLISH_CURRENT_POINTER_INVALID", "The current pointer is invalid.")
        if metadata.st_size > POINTER_MAX_BYTES:
            raise RagBuildError("PUBLISH_CURRENT_POINTER_INVALID", "The current pointer is invalid.")
        pointer = CurrentCorpusPointer.model_validate_json(path.read_bytes())
    except RagBuildError:
        raise
    except (OSError, UnicodeError, ValidationError) as exc:
        raise RagBuildError("PUBLISH_CURRENT_POINTER_INVALID", "The current pointer is invalid.") from exc
    if pointer.schemaVersion != SCHEMA_VERSION or pointer.corpusId != CORPUS_ID:
        raise RagBuildError("PUBLISH_CURRENT_POINTER_INVALID", "The current pointer is unsupported.")
    return pointer


def _write_all(descriptor: int, content: bytes) -> None:
    remaining = memoryview(content)
    while remaining:
        written = os.write(descriptor, remaining)
        if written <= 0:
            raise OSError("publish transaction write made no progress")
        remaining = remaining[written:]


class _PublishLock:
    def __init__(self, root: Path, published_by: str):
        self.path = root / ".publish.lock"
        self.published_by = published_by
        self.fd: int | None = None

    def __enter__(self) -> None:
        try:
            self.fd = os.open(self.path, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
            payload = json.dumps(
                {"schemaVersion": SCHEMA_VERSION, "publishedBy": self.published_by},
                ensure_ascii=False,
                sort_keys=True,
            ).encode("utf-8")
            _write_all(self.fd, payload)
            os.fsync(self.fd)
        except FileExistsError as exc:
            raise RagBuildError(
                "PUBLISH_LOCKED", "Another publish or rollback operation holds the runtime lock."
            ) from exc
        except OSError as exc:
            if self.fd is not None:
                os.close(self.fd)
                self.fd = None
            try:
                self.path.unlink(missing_ok=True)
            except OSError:
                pass
            raise RagBuildError("PUBLISH_LOCK_FAILED", "Unable to acquire the runtime lock.") from exc

    def __exit__(self, exc_type, exc, traceback) -> None:
        if self.fd is not None:
            os.close(self.fd)
            self.fd = None
        try:
            self.path.unlink(missing_ok=True)
        except OSError as cleanup_error:
            if exc is None:
                raise RagBuildError("PUBLISH_LOCK_RELEASE_FAILED", "Unable to release the runtime lock.") from cleanup_error


def _check_expected_current(
    current: CurrentCorpusPointer | None,
    expected_current_version: str | None,
) -> None:
    if current is None:
        if expected_current_version is not None:
            raise RagBuildError(
                "PUBLISH_CURRENT_VERSION_MISMATCH", "The expected current corpus version does not match."
            )
        return
    if expected_current_version is None:
        raise RagBuildError(
            "PUBLISH_EXPECTED_CURRENT_REQUIRED",
            "An expected current corpus version is required for this switch.",
        )
    if current.corpusVersion != expected_current_version:
        raise RagBuildError(
            "PUBLISH_CURRENT_VERSION_MISMATCH", "The expected current corpus version does not match."
        )


def _write_new_file(path: Path, content: bytes, code: str, *, mode: int = 0o600) -> None:
    descriptor: int | None = None
    try:
        descriptor = os.open(path, os.O_WRONLY | os.O_CREAT | os.O_EXCL, mode)
        _write_all(descriptor, content)
        os.fsync(descriptor)
    except OSError as exc:
        raise RagBuildError(code, "Unable to write a publish transaction artifact.") from exc
    finally:
        if descriptor is not None:
            os.close(descriptor)


def _pointer_bytes(pointer: CurrentCorpusPointer) -> bytes:
    return (pointer.model_dump_json(indent=2) + "\n").encode("utf-8")


def _audit_bytes(
    *,
    event_id: str,
    action: str,
    current: CurrentCorpusPointer | None,
    target: ValidatedRelease,
    release_copied: bool,
) -> bytes:
    payload = {
        "schemaVersion": SCHEMA_VERSION,
        "eventId": event_id,
        "action": action,
        "status": "COMMITTED",
        "fromCorpusVersion": current.corpusVersion if current else None,
        "toCorpusVersion": target.pointer.corpusVersion,
        "corpusManifestSha256": target.pointer.corpusManifestSha256,
        "indexManifestSha256": target.pointer.indexManifestSha256,
        "evaluationSetSha256": target.pointer.evaluationSetSha256,
        "evaluationReportSha256": target.pointer.evaluationReportSha256,
        "releaseSha256": target.releaseSha256,
        "releaseCopied": release_copied,
        "occurredAt": target.pointer.publishedAt.isoformat(),
        "publishedBy": target.pointer.publishedBy,
    }
    return (json.dumps(payload, ensure_ascii=False, indent=2, sort_keys=True) + "\n").encode(
        "utf-8"
    )


def _atomic_switch(
    root: Path,
    *,
    action: str,
    current: CurrentCorpusPointer | None,
    target: ValidatedRelease,
    release_copied: bool,
    before_replace: Callable[[], None] | None,
) -> str:
    transaction = uuid.uuid4().hex
    pointer_temp = root / f".current.{transaction}.tmp"
    audit_temp = root / "audit" / f".{transaction}.prepared"
    audit_name = f"{target.pointer.publishedAt.strftime('%Y%m%dT%H%M%S%z')}-{action.lower()}-{transaction}.json"
    audit_final = root / "audit" / audit_name
    pointer_switched = False
    try:
        _write_new_file(
            pointer_temp,
            _pointer_bytes(target.pointer),
            "PUBLISH_POINTER_WRITE_FAILED",
            mode=0o644,
        )
        _write_new_file(
            audit_temp,
            _audit_bytes(
                event_id=f"ragpub_{transaction}",
                action=action,
                current=current,
                target=target,
                release_copied=release_copied,
            ),
            "PUBLISH_AUDIT_WRITE_FAILED",
        )
        if before_replace is not None:
            before_replace()
        os.replace(pointer_temp, root / "current.json")
        pointer_switched = True
        try:
            os.replace(audit_temp, audit_final)
        except OSError as exc:
            raise RagBuildError(
                "PUBLISH_AUDIT_COMMIT_FAILED",
                "The pointer switched, but the prepared audit event could not be committed; inspect current.json.",
            ) from exc
    except RagBuildError:
        raise
    except Exception as exc:
        if pointer_switched:
            raise RagBuildError(
                "PUBLISH_POST_SWITCH_FAILED",
                "The pointer switched, but publication finalization failed; inspect current.json.",
            ) from exc
        raise RagBuildError(
            "PUBLISH_POINTER_SWITCH_FAILED", "The current pointer was not switched."
        ) from exc
    finally:
        for temporary in (pointer_temp,):
            try:
                temporary.unlink(missing_ok=True)
            except OSError:
                pass
        if not pointer_switched:
            try:
                audit_temp.unlink(missing_ok=True)
            except OSError:
                pass
    return f"audit/{audit_name}"


def _freeze_release(root: Path) -> None:
    try:
        paths = tuple(root.rglob("*"))
        for path in paths:
            if path.is_file():
                path.chmod(0o444)
        for path in sorted((item for item in paths if item.is_dir()), key=lambda item: len(item.parts), reverse=True):
            path.chmod(0o555)
        root.chmod(0o555)
    except OSError as exc:
        raise RagBuildError("PUBLISH_RELEASE_FREEZE_FAILED", "Unable to make the release read-only.") from exc


def _remove_staging(staging: Path) -> None:
    if not staging.exists():
        return
    try:
        paths = tuple(staging.rglob("*"))
        for path in paths:
            path.chmod(0o700 if path.is_dir() else 0o600)
        staging.chmod(0o700)
        shutil.rmtree(staging)
    except OSError:
        # A generated staging directory is never treated as a release or pointer.
        # Leaving it in place is safer than broadening cleanup beyond the exact path.
        pass


def publish_release(
    release_root: Path,
    runtime_root: Path,
    *,
    published_by: str,
    expected_current_version: str | None = None,
    published_at: datetime | None = None,
    before_pointer_replace: Callable[[], None] | None = None,
) -> PublicationResult:
    source = validate_release(
        release_root,
        published_by=published_by,
        published_at=published_at,
    )
    root = _ensure_runtime_root(runtime_root)
    if _paths_overlap(source.release, root):
        raise RagBuildError(
            "PUBLISH_SOURCE_RUNTIME_OVERLAP", "The source release and runtime root must be separate."
        )
    with _PublishLock(root, published_by):
        current = _load_current_pointer(root)
        if current is not None and current.corpusVersion == source.pointer.corpusVersion:
            destination = root / "releases" / source.pointer.releaseName
            deployed = validate_release(
                destination,
                published_by=published_by,
                published_at=published_at,
            )
            if deployed.releaseSha256 != source.releaseSha256:
                raise RagBuildError(
                    "PUBLISH_RELEASE_ALREADY_EXISTS",
                    "A different immutable release already uses this corpus version.",
                )
            if (
                current.corpusManifestSha256 != deployed.pointer.corpusManifestSha256
                or current.indexManifestSha256 != deployed.pointer.indexManifestSha256
                or current.evaluationSetSha256 != deployed.pointer.evaluationSetSha256
                or current.evaluationReportSha256 != deployed.pointer.evaluationReportSha256
            ):
                raise RagBuildError(
                    "PUBLISH_CURRENT_POINTER_INVALID", "The current pointer does not match its release."
                )
            return PublicationResult(
                action="PUBLISH",
                pointer=current,
                previousCorpusVersion=current.corpusVersion,
                releaseSha256=deployed.releaseSha256,
                releaseCopied=False,
                pointerChanged=False,
                auditEvent=None,
            )
        _check_expected_current(current, expected_current_version)
        destination = root / "releases" / source.pointer.releaseName
        release_copied = False
        if destination.exists():
            deployed = validate_release(
                destination,
                published_by=published_by,
                published_at=published_at,
            )
            if deployed.releaseSha256 != source.releaseSha256:
                raise RagBuildError(
                    "PUBLISH_RELEASE_ALREADY_EXISTS",
                    "A different immutable release already uses this corpus version.",
                )
            _freeze_release(destination)
        else:
            staging = root / "releases" / f".{source.pointer.releaseName}.staging-{uuid.uuid4().hex}"
            try:
                shutil.copytree(source.release, staging, symlinks=True)
                deployed = validate_release(
                    staging,
                    published_by=published_by,
                    published_at=published_at,
                    require_version_directory_name=False,
                )
                if deployed.releaseSha256 != source.releaseSha256:
                    raise RagBuildError(
                        "PUBLISH_RELEASE_COPY_MISMATCH",
                        "The copied release does not match the validated source.",
                    )
                staging.replace(destination)
                _freeze_release(destination)
                deployed = validate_release(
                    destination,
                    published_by=published_by,
                    published_at=published_at,
                )
                release_copied = True
            except RagBuildError:
                raise
            except OSError as exc:
                raise RagBuildError(
                    "PUBLISH_RELEASE_COPY_FAILED", "Unable to copy the release artifact."
                ) from exc
            finally:
                _remove_staging(staging)
        audit_event = _atomic_switch(
            root,
            action="PUBLISH",
            current=current,
            target=deployed,
            release_copied=release_copied,
            before_replace=before_pointer_replace,
        )
        return PublicationResult(
            action="PUBLISH",
            pointer=deployed.pointer,
            previousCorpusVersion=current.corpusVersion if current else None,
            releaseSha256=deployed.releaseSha256,
            releaseCopied=release_copied,
            pointerChanged=True,
            auditEvent=audit_event,
        )


def rollback_release(
    runtime_root: Path,
    *,
    target_version: str,
    expected_current_version: str,
    published_by: str,
    published_at: datetime | None = None,
    before_pointer_replace: Callable[[], None] | None = None,
) -> PublicationResult:
    try:
        normalized_target = TypeAdapter(CorpusVersion).validate_python(target_version)
    except ValidationError as exc:
        raise RagBuildError("ROLLBACK_TARGET_INVALID", "The rollback target version is invalid.") from exc
    root = _ensure_runtime_root(runtime_root)
    with _PublishLock(root, published_by):
        current = _load_current_pointer(root)
        _check_expected_current(current, expected_current_version)
        if current is None:
            raise RagBuildError("PUBLISH_CURRENT_POINTER_MISSING", "No current corpus is active.")
        if normalized_target == current.corpusVersion:
            raise RagBuildError("ROLLBACK_TARGET_IS_CURRENT", "The rollback target is already active.")
        target = validate_release(
            root / "releases" / normalized_target,
            published_by=published_by,
            published_at=published_at,
        )
        if target.pointer.corpusVersion != normalized_target:
            raise RagBuildError("ROLLBACK_TARGET_MISMATCH", "The rollback target version is invalid.")
        audit_event = _atomic_switch(
            root,
            action="ROLLBACK",
            current=current,
            target=target,
            release_copied=False,
            before_replace=before_pointer_replace,
        )
        return PublicationResult(
            action="ROLLBACK",
            pointer=target.pointer,
            previousCorpusVersion=current.corpusVersion,
            releaseSha256=target.releaseSha256,
            releaseCopied=False,
            pointerChanged=True,
            auditEvent=audit_event,
        )
