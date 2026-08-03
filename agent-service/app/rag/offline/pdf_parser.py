from __future__ import annotations

import base64
import hashlib
import json
import os
import re
import shutil
import stat
import subprocess
import time
import unicodedata
import uuid
from dataclasses import dataclass
from datetime import datetime
from pathlib import Path, PurePosixPath
from typing import Any, Protocol
from urllib.parse import urlsplit
from zoneinfo import ZoneInfo

import httpx
from PIL import Image
from pydantic import BaseModel, ConfigDict, Field, ValidationError, model_validator
from pypdf import PdfReader
from pypdf.errors import PdfReadError

from app.rag.config import TIMEZONE
from app.rag.contracts import (
    BuildStatus,
    PdfBoundingBox,
    PdfDocumentExtractionSummary,
    PdfExtractionContract,
    PdfExtractionMetrics,
    PdfExtractionReport,
    PdfOcrRegion,
    PdfOcrStatus,
    PdfPackageInfo,
    PdfPageExtraction,
    PdfRegionType,
    PdfTextLayerStatus,
    PdfVisualValidation,
    SourceFileRecord,
    SourceInventoryManifest,
    SourceType,
    VisualValidationStatus,
)
from app.rag.offline.source_inventory import (
    RagBuildError,
    load_source_inventory,
    resolve_inventory_source_file,
    validate_output_root,
)


PARSER_VERSION = "pdf-render-ocr/1.0.0"
DEFAULT_RENDER_DPI = 250
MAX_PDF_BYTES = 512 * 1024 * 1024
MAX_PAGE_COUNT = 200
MAX_PAGE_POINTS = 20_000
MAX_RENDERED_PIXELS = 100_000_000
MAX_RENDERED_IMAGE_BYTES = 100 * 1024 * 1024
MAX_OCR_RESPONSE_BYTES = 10 * 1024 * 1024
MAX_OCR_IMAGE_BYTES = 50 * 1024 * 1024
LOW_OCR_CONFIDENCE = 0.85
TEXT_LAYER_PRESENT_CHARACTERS = 200


class PdfParseError(RagBuildError):
    pass


class OcrProviderError(RuntimeError):
    def __init__(self, code: str, message: str):
        super().__init__(message)
        self.code = code


class _OcrRegionResponse(BaseModel):
    model_config = ConfigDict(extra="forbid")

    regionType: PdfRegionType = PdfRegionType.UNKNOWN
    text: str = Field(min_length=1)
    confidence: float | None = Field(default=None, ge=0, le=1)
    boundingBox: PdfBoundingBox | None = None


class _OcrPageResponse(BaseModel):
    model_config = ConfigDict(extra="forbid")

    text: str = Field(min_length=1)
    confidence: float | None = Field(default=None, ge=0, le=1)
    regions: tuple[_OcrRegionResponse, ...] = ()

    @model_validator(mode="after")
    def validate_meaningful_text(self) -> _OcrPageResponse:
        if not _normalized_text(self.text):
            raise ValueError("OCR response text must contain visible characters")
        if any(not _normalized_text(region.text) for region in self.regions):
            raise ValueError("OCR region text must contain visible characters")
        return self


@dataclass(frozen=True)
class OcrProviderSettings:
    enabled: bool
    endpoint: str | None = None
    allowed_hosts: tuple[str, ...] = ()
    api_key: str | None = None
    model: str | None = None
    timeout_seconds: float = 60.0

    @classmethod
    def from_environment(cls) -> OcrProviderSettings:
        enabled = os.getenv("RAG_OCR_ENABLED", "").strip().casefold() in {
            "1",
            "true",
            "yes",
        }
        if not enabled:
            return cls(enabled=False)
        allowed_hosts = tuple(
            host.strip().casefold()
            for host in os.getenv("RAG_OCR_ALLOWED_HOSTS", "").split(",")
            if host.strip()
        )
        timeout_text = os.getenv("RAG_OCR_TIMEOUT_SECONDS", "60").strip()
        try:
            timeout_seconds = float(timeout_text)
        except ValueError as exc:
            raise PdfParseError(
                "OCR_CONFIG_INVALID",
                "RAG_OCR_TIMEOUT_SECONDS must be numeric.",
            ) from exc
        settings = cls(
            enabled=enabled,
            endpoint=os.getenv("RAG_OCR_ENDPOINT") or None,
            allowed_hosts=allowed_hosts,
            api_key=os.getenv("RAG_OCR_API_KEY") or None,
            model=os.getenv("RAG_OCR_MODEL") or None,
            timeout_seconds=timeout_seconds,
        )
        settings.validate()
        return settings

    def validate(self) -> None:
        if not self.enabled:
            return
        if not 1 <= self.timeout_seconds <= 300:
            raise PdfParseError(
                "OCR_CONFIG_INVALID",
                "OCR timeout must be between 1 and 300 seconds.",
            )
        if not self.endpoint or not self.api_key or not self.model:
            raise PdfParseError(
                "OCR_CONFIG_INCOMPLETE",
                "Enabled OCR requires endpoint, API key, and model.",
            )
        parsed = urlsplit(self.endpoint)
        if (
            parsed.scheme.casefold() != "https"
            or not parsed.hostname
            or parsed.username
            or parsed.password
            or parsed.fragment
            or parsed.query
        ):
            raise PdfParseError(
                "OCR_ENDPOINT_INVALID",
                "OCR endpoint must be a credential-free HTTPS URL without query or fragment.",
            )
        if not self.allowed_hosts or parsed.hostname.casefold() not in self.allowed_hosts:
            raise PdfParseError(
                "OCR_HOST_NOT_ALLOWED",
                "OCR endpoint host must be explicitly allowlisted.",
            )


@dataclass(frozen=True)
class OcrResult:
    text: str
    confidence: float | None
    regions: tuple[_OcrRegionResponse, ...]
    request_bytes: int
    response_bytes: int
    duration_ms: int


class OcrProvider(Protocol):
    @property
    def name(self) -> str: ...

    @property
    def model(self) -> str: ...

    def recognize(
        self,
        *,
        image: bytes,
        document_id: str,
        page_number: int,
    ) -> OcrResult: ...


class HttpJsonOcrProvider:
    def __init__(
        self,
        settings: OcrProviderSettings,
        *,
        client: httpx.Client | None = None,
    ):
        settings.validate()
        if not settings.enabled:
            raise PdfParseError("OCR_CONFIG_DISABLED", "OCR provider is not enabled.")
        self._settings = settings
        self._client = client or httpx.Client(
            follow_redirects=False,
            timeout=settings.timeout_seconds,
            trust_env=False,
        )

    @property
    def name(self) -> str:
        return "http-json"

    @property
    def model(self) -> str:
        return self._settings.model or ""

    def recognize(
        self,
        *,
        image: bytes,
        document_id: str,
        page_number: int,
    ) -> OcrResult:
        if len(image) > MAX_OCR_IMAGE_BYTES:
            raise OcrProviderError("OCR_IMAGE_TOO_LARGE", "Rendered page exceeds the OCR request limit.")
        payload = {
            "schemaVersion": 1,
            "model": self.model,
            "documentId": document_id,
            "pageNumber": page_number,
            "mimeType": "image/png",
            "imageBase64": base64.b64encode(image).decode("ascii"),
        }
        started = time.monotonic()
        try:
            with self._client.stream(
                "POST",
                self._settings.endpoint or "",
                headers={
                    "Authorization": f"Bearer {self._settings.api_key}",
                    "Content-Type": "application/json",
                },
                json=payload,
            ) as response:
                if response.is_redirect:
                    raise OcrProviderError(
                        "OCR_REDIRECT_FORBIDDEN",
                        "OCR provider redirects are forbidden.",
                    )
                response.raise_for_status()
                chunks = []
                response_bytes = 0
                for chunk in response.iter_bytes():
                    response_bytes += len(chunk)
                    if response_bytes > MAX_OCR_RESPONSE_BYTES:
                        raise OcrProviderError(
                            "OCR_RESPONSE_TOO_LARGE",
                            "OCR provider response exceeds the configured limit.",
                        )
                    chunks.append(chunk)
        except OcrProviderError:
            raise
        except httpx.TimeoutException as exc:
            raise OcrProviderError("OCR_TIMEOUT", "OCR provider request timed out.") from exc
        except httpx.HTTPError as exc:
            raise OcrProviderError("OCR_REQUEST_FAILED", "OCR provider request failed.") from exc
        try:
            parsed = _OcrPageResponse.model_validate_json(b"".join(chunks))
        except (ValidationError, ValueError) as exc:
            raise OcrProviderError(
                "OCR_RESPONSE_INVALID",
                "OCR provider response does not match the expected contract.",
            ) from exc
        return OcrResult(
            text=_normalized_text(parsed.text),
            confidence=parsed.confidence,
            regions=parsed.regions,
            request_bytes=len(image),
            response_bytes=response_bytes,
            duration_ms=max(0, round((time.monotonic() - started) * 1000)),
        )


class PdfRenderer:
    def __init__(self, executable: Path, *, timeout_seconds: float = 120.0):
        candidate = executable.expanduser().resolve(strict=True)
        if not candidate.is_file():
            raise PdfParseError("PDF_RENDERER_NOT_FOUND", "PDF renderer does not exist.")
        if os.name == "nt" and candidate.suffix.casefold() in {".cmd", ".bat"}:
            raise PdfParseError(
                "PDF_RENDERER_WRAPPER_FORBIDDEN",
                "Use the pdftoppm executable instead of a shell wrapper.",
            )
        if not 1 <= timeout_seconds <= 600:
            raise PdfParseError(
                "PDF_RENDERER_CONFIG_INVALID",
                "PDF renderer timeout must be between 1 and 600 seconds.",
            )
        self._executable = candidate
        self._timeout_seconds = timeout_seconds
        self._version = self._read_version()

    @property
    def name(self) -> str:
        return self._executable.name

    @property
    def version(self) -> str:
        return self._version

    def _read_version(self) -> str:
        try:
            result = subprocess.run(
                [str(self._executable), "-v"],
                check=False,
                capture_output=True,
                text=True,
                timeout=10,
            )
        except (OSError, subprocess.SubprocessError) as exc:
            raise PdfParseError(
                "PDF_RENDERER_UNAVAILABLE",
                "Unable to execute the configured PDF renderer.",
            ) from exc
        output = "\n".join(part for part in (result.stdout, result.stderr) if part)
        first_line = output.strip().splitlines()[0] if output.strip() else "unknown"
        return _safe_metadata(first_line, max_length=500) or "unknown"

    def render_page(
        self,
        source: Path,
        page_number: int,
        output: Path,
        *,
        dpi: int,
    ) -> None:
        prefix = output.with_suffix("")
        try:
            result = subprocess.run(
                [
                    str(self._executable),
                    "-f",
                    str(page_number),
                    "-l",
                    str(page_number),
                    "-r",
                    str(dpi),
                    "-png",
                    "-singlefile",
                    str(source),
                    str(prefix),
                ],
                check=False,
                capture_output=True,
                timeout=self._timeout_seconds,
            )
        except subprocess.TimeoutExpired as exc:
            raise PdfParseError("PDF_RENDER_TIMEOUT", "PDF page rendering timed out.") from exc
        except OSError as exc:
            raise PdfParseError(
                "PDF_RENDERER_UNAVAILABLE",
                "Unable to execute the configured PDF renderer.",
            ) from exc
        if result.returncode != 0 or not output.is_file():
            raise PdfParseError("PDF_RENDER_FAILED", "PDF page rendering failed.")


def resolve_pdf_renderer(explicit_path: Path | None = None) -> PdfRenderer:
    candidate = explicit_path
    if candidate is None:
        configured = os.getenv("RAG_PDF_RENDERER_PATH")
        if configured:
            candidate = Path(configured)
    if candidate is None:
        discovered = shutil.which("pdftoppm")
        if discovered:
            candidate = Path(discovered)
    if candidate is None:
        raise PdfParseError(
            "PDF_RENDERER_NOT_FOUND",
            "pdftoppm was not found; configure RAG_PDF_RENDERER_PATH.",
        )
    return PdfRenderer(candidate)


def _normalized_text(value: str) -> str:
    normalized = unicodedata.normalize("NFC", value)
    normalized = normalized.replace("\u3000", " ")
    normalized = re.sub(r"[ \t]+", " ", normalized)
    normalized = re.sub(r" *\n *", "\n", normalized)
    return normalized.strip()


def _safe_metadata(value: Any, *, max_length: int = 1000) -> str | None:
    if value is None:
        return None
    text = _normalized_text(str(value))
    text = "".join(character for character in text if character >= " " or character in "\n\t")
    return text[:max_length] or None


def _text_layer_status(text: str) -> PdfTextLayerStatus:
    if not text:
        return PdfTextLayerStatus.EMPTY
    if len(text) < TEXT_LAYER_PRESENT_CHARACTERS:
        return PdfTextLayerStatus.SPARSE
    return PdfTextLayerStatus.PRESENT


def _sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    try:
        with path.open("rb") as source:
            while block := source.read(1024 * 1024):
                digest.update(block)
    except OSError as exc:
        raise PdfParseError("PDF_RENDER_READ_FAILED", "Unable to read a rendered PDF page.") from exc
    return digest.hexdigest()


def _stable_hash(payload: dict[str, Any]) -> str:
    canonical = json.dumps(
        payload,
        ensure_ascii=False,
        sort_keys=True,
        separators=(",", ":"),
    ).encode("utf-8")
    return hashlib.sha256(canonical).hexdigest()


def _pdf_extraction_stable_payload(
    extraction: PdfExtractionContract,
) -> dict[str, Any]:
    return extraction.model_dump(
        mode="json",
        exclude={"parsedAt", "extractionSha256"},
    )


def _read_pdf(path: Path) -> tuple[PdfReader, PdfPackageInfo]:
    try:
        size_bytes = path.stat().st_size
    except OSError as exc:
        raise PdfParseError("PDF_SOURCE_STAT_FAILED", "Unable to inspect the PDF source.") from exc
    if size_bytes <= 0 or size_bytes > MAX_PDF_BYTES:
        raise PdfParseError("PDF_SOURCE_SIZE_INVALID", "PDF source size is outside the allowed range.")
    try:
        reader = PdfReader(str(path), strict=True)
        if reader.is_encrypted:
            raise PdfParseError("PDF_ENCRYPTED", "Encrypted PDF files are not supported.")
        page_count = len(reader.pages)
        if not 1 <= page_count <= MAX_PAGE_COUNT:
            raise PdfParseError(
                "PDF_PAGE_COUNT_INVALID",
                "PDF page count is outside the allowed range.",
            )
        metadata = reader.metadata
    except PdfParseError:
        raise
    except (OSError, PdfReadError, ValueError) as exc:
        raise PdfParseError("PDF_READ_FAILED", "Unable to read the PDF structure.") from exc
    return (
        reader,
        PdfPackageInfo(
            pageCount=page_count,
            encrypted=False,
            title=_safe_metadata(getattr(metadata, "title", None)),
            author=_safe_metadata(getattr(metadata, "author", None)),
            creator=_safe_metadata(getattr(metadata, "creator", None)),
            producer=_safe_metadata(getattr(metadata, "producer", None)),
        ),
    )


def _page_geometry(page: Any) -> tuple[float, float, int]:
    try:
        width = float(page.mediabox.width)
        height = float(page.mediabox.height)
        rotation = int(page.rotation or 0) % 360
    except (TypeError, ValueError) as exc:
        raise PdfParseError("PDF_PAGE_GEOMETRY_INVALID", "PDF page geometry is invalid.") from exc
    if (
        width <= 0
        or height <= 0
        or width > MAX_PAGE_POINTS
        or height > MAX_PAGE_POINTS
    ):
        raise PdfParseError("PDF_PAGE_GEOMETRY_INVALID", "PDF page geometry is invalid.")
    return width, height, rotation


def _extract_page_text(page: Any) -> str:
    try:
        return _normalized_text(page.extract_text() or "")
    except (PdfReadError, ValueError, TypeError) as exc:
        raise PdfParseError("PDF_TEXT_EXTRACTION_FAILED", "Unable to inspect the PDF text layer.") from exc


def _inspect_rendered_image(path: Path) -> tuple[int, int]:
    try:
        if path.stat().st_size > MAX_RENDERED_IMAGE_BYTES:
            raise PdfParseError(
                "PDF_RENDER_OUTPUT_TOO_LARGE",
                "Rendered PDF page exceeds the artifact size limit.",
            )
        with Image.open(path) as image:
            image.verify()
        with Image.open(path) as image:
            width, height = image.size
            if width * height > MAX_RENDERED_PIXELS:
                raise PdfParseError(
                    "PDF_RENDER_DIMENSIONS_INVALID",
                    "Rendered PDF page exceeds the pixel limit.",
                )
            return width, height
    except PdfParseError:
        raise
    except (OSError, Image.DecompressionBombError) as exc:
        raise PdfParseError(
            "PDF_RENDER_OUTPUT_INVALID",
            "Rendered PDF page is not a valid bounded image.",
        ) from exc


def _ocr_regions(
    document_id: str,
    page_number: int,
    result: OcrResult,
) -> tuple[PdfOcrRegion, ...]:
    regions = []
    for sequence, region in enumerate(result.regions, start=1):
        flags = (
            ("OCR_LOW_CONFIDENCE",)
            if region.confidence is not None and region.confidence < LOW_OCR_CONFIDENCE
            else ()
        )
        regions.append(
            PdfOcrRegion(
                regionId=f"{document_id}/page/{page_number:03d}/region/{sequence:03d}",
                regionType=region.regionType,
                sourceText=_normalized_text(region.text),
                confidence=region.confidence,
                boundingBox=region.boundingBox,
                qualityFlags=flags,
            )
        )
    return tuple(regions)


def _ocr_audit(
    *,
    document_id: str,
    page_number: int,
    provider: OcrProvider | None,
    status: PdfOcrStatus,
    result: OcrResult | None = None,
    error_code: str | None = None,
) -> dict[str, Any]:
    return {
        "schemaVersion": 1,
        "documentId": document_id,
        "pageNumber": page_number,
        "provider": provider.name if provider else "not-configured",
        "model": provider.model if provider else "",
        "status": status.value,
        "durationMs": result.duration_ms if result else 0,
        "requestBytes": result.request_bytes if result else 0,
        "responseBytes": result.response_bytes if result else 0,
        "errorCode": error_code,
    }


def parse_pdf(
    path: Path,
    record: SourceFileRecord,
    corpus_version: str,
    *,
    render_directory: Path,
    renderer: PdfRenderer,
    ocr_provider: OcrProvider | None = None,
    parsed_at: datetime | None = None,
    render_dpi: int = DEFAULT_RENDER_DPI,
) -> tuple[PdfExtractionContract, tuple[dict[str, Any], ...]]:
    if record.sourceType != SourceType.PDF:
        raise PdfParseError("PDF_SOURCE_TYPE_INVALID", "Only inventoried PDF files may be parsed.")
    if not 72 <= render_dpi <= 600:
        raise PdfParseError("PDF_RENDER_DPI_INVALID", "PDF render DPI must be between 72 and 600.")
    if render_directory.exists():
        raise PdfParseError(
            "PDF_RENDER_OUTPUT_EXISTS",
            "PDF render output already exists and will not be overwritten.",
        )
    render_directory.mkdir(parents=True)
    reader, package = _read_pdf(path)
    pages = []
    audits = []
    for page_number, page in enumerate(reader.pages, start=1):
        width_points, height_points, rotation = _page_geometry(page)
        extracted_text = _extract_page_text(page)
        text_status = _text_layer_status(extracted_text)
        rendered_path = render_directory / f"page-{page_number:03d}.png"
        renderer.render_page(path, page_number, rendered_path, dpi=render_dpi)
        pixel_width, pixel_height = _inspect_rendered_image(rendered_path)
        rendered_sha256 = _sha256_file(rendered_path)
        page_flags = []
        if text_status == PdfTextLayerStatus.EMPTY:
            page_flags.append("PDF_TEXT_LAYER_EMPTY")
        elif text_status == PdfTextLayerStatus.SPARSE:
            page_flags.append("PDF_TEXT_LAYER_SPARSE")

        ocr_status = PdfOcrStatus.NOT_REQUIRED
        ocr_text = ""
        ocr_confidence = None
        regions: tuple[PdfOcrRegion, ...] = ()
        if text_status != PdfTextLayerStatus.PRESENT:
            if ocr_provider is None:
                ocr_status = PdfOcrStatus.NOT_CONFIGURED
                page_flags.append("OCR_PROVIDER_NOT_CONFIGURED")
                audits.append(
                    _ocr_audit(
                        document_id=record.documentId,
                        page_number=page_number,
                        provider=None,
                        status=ocr_status,
                    )
                )
            else:
                try:
                    image_bytes = rendered_path.read_bytes()
                    ocr_result = ocr_provider.recognize(
                        image=image_bytes,
                        document_id=record.documentId,
                        page_number=page_number,
                    )
                    ocr_text = _normalized_text(ocr_result.text)
                    ocr_confidence = ocr_result.confidence
                    regions = _ocr_regions(record.documentId, page_number, ocr_result)
                    ocr_status = PdfOcrStatus.SUCCEEDED
                    if (
                        ocr_confidence is not None
                        and ocr_confidence < LOW_OCR_CONFIDENCE
                    ):
                        page_flags.append("OCR_LOW_CONFIDENCE")
                    audits.append(
                        _ocr_audit(
                            document_id=record.documentId,
                            page_number=page_number,
                            provider=ocr_provider,
                            status=ocr_status,
                            result=ocr_result,
                        )
                    )
                except (
                    OcrProviderError,
                    OSError,
                    ValidationError,
                    ValueError,
                ) as exc:
                    ocr_status = PdfOcrStatus.FAILED
                    page_flags.append("OCR_FAILED")
                    error_code = (
                        exc.code
                        if isinstance(exc, OcrProviderError)
                        else (
                            "OCR_IMAGE_READ_FAILED"
                            if isinstance(exc, OSError)
                            else "OCR_RESULT_INVALID"
                        )
                    )
                    audits.append(
                        _ocr_audit(
                            document_id=record.documentId,
                            page_number=page_number,
                            provider=ocr_provider,
                            status=ocr_status,
                            error_code=error_code,
                        )
                    )

        pages.append(
            PdfPageExtraction(
                pageNumber=page_number,
                widthPoints=width_points,
                heightPoints=height_points,
                rotationDegrees=rotation,
                textLayerStatus=text_status,
                extractedText=extracted_text,
                extractedCharacterCount=len(extracted_text),
                renderedArtifact=(
                    Path("qa")
                    / "pdf-render"
                    / record.documentId
                    / rendered_path.name
                ).as_posix(),
                renderedSha256=rendered_sha256,
                renderDpi=render_dpi,
                pixelWidth=pixel_width,
                pixelHeight=pixel_height,
                ocrStatus=ocr_status,
                ocrText=ocr_text,
                ocrAverageConfidence=ocr_confidence,
                regions=regions,
                qualityFlags=tuple(page_flags),
            )
        )

    metrics = PdfExtractionMetrics(
        renderedPageCount=len(pages),
        emptyTextLayerPageCount=sum(
            page.textLayerStatus == PdfTextLayerStatus.EMPTY for page in pages
        ),
        sparseTextLayerPageCount=sum(
            page.textLayerStatus == PdfTextLayerStatus.SPARSE for page in pages
        ),
        presentTextLayerPageCount=sum(
            page.textLayerStatus == PdfTextLayerStatus.PRESENT for page in pages
        ),
        ocrSucceededPageCount=sum(
            page.ocrStatus == PdfOcrStatus.SUCCEEDED for page in pages
        ),
        ocrFailedPageCount=sum(
            page.ocrStatus == PdfOcrStatus.FAILED for page in pages
        ),
        ocrNotConfiguredPageCount=sum(
            page.ocrStatus == PdfOcrStatus.NOT_CONFIGURED for page in pages
        ),
        lowConfidencePageCount=sum(
            "OCR_LOW_CONFIDENCE" in page.qualityFlags for page in pages
        ),
    )
    quality_flags = []
    if metrics.emptyTextLayerPageCount:
        quality_flags.append("PDF_TEXT_LAYER_EMPTY")
    if metrics.sparseTextLayerPageCount:
        quality_flags.append("PDF_TEXT_LAYER_SPARSE")
    if metrics.ocrNotConfiguredPageCount:
        quality_flags.append("OCR_PROVIDER_NOT_CONFIGURED")
    if metrics.ocrFailedPageCount:
        quality_flags.append("OCR_FAILED")
    if metrics.lowConfidencePageCount:
        quality_flags.append("OCR_LOW_CONFIDENCE")
    quality_flags.append("VISUAL_REVIEW_PENDING")
    visual_validation = PdfVisualValidation(
        status=VisualValidationStatus.NOT_PERFORMED,
        renderer=renderer.name,
        rendererVersion=renderer.version,
        renderedPageCount=len(pages),
        notes=("Every rendered page requires visual review before corpus normalization.",),
    )
    extraction_without_hash = PdfExtractionContract(
        corpusVersion=corpus_version,
        documentId=record.documentId,
        sourceFileName=record.sourceFileName,
        sourceSha256=record.sourceSha256,
        parserVersion=PARSER_VERSION,
        parsedAt=parsed_at or datetime.now(ZoneInfo(TIMEZONE)),
        status=BuildStatus.REQUIRES_REVIEW,
        package=package,
        pages=tuple(pages),
        qualityFlags=tuple(quality_flags),
        metrics=metrics,
        visualValidation=visual_validation,
        extractionSha256="0" * 64,
    )
    extraction = extraction_without_hash.model_copy(
        update={
            "extractionSha256": _stable_hash(
                _pdf_extraction_stable_payload(extraction_without_hash)
            )
        }
    )
    return extraction, tuple(audits)


def _ensure_safe_artifact_directory(path: Path, root: Path) -> None:
    root_absolute = root.absolute()
    try:
        relative = path.absolute().relative_to(root_absolute)
    except (OSError, ValueError) as exc:
        raise PdfParseError("PDF_OUTPUT_DIRECTORY_INVALID", "PDF artifact directory is invalid.") from exc
    current = root_absolute
    for component in relative.parts:
        current /= component
        if not current.exists() and not current.is_symlink():
            raise PdfParseError(
                "PDF_OUTPUT_DIRECTORY_INVALID",
                "PDF artifact directory is invalid.",
            )
        attributes = getattr(current.lstat(), "st_file_attributes", 0)
        if current.is_symlink() or attributes & getattr(
            stat,
            "FILE_ATTRIBUTE_REPARSE_POINT",
            0,
        ):
            raise PdfParseError(
                "PDF_OUTPUT_LINK_FORBIDDEN",
                "PDF artifact directories must not be links or reparse points.",
            )
    try:
        path.resolve(strict=True).relative_to(root.resolve(strict=True))
    except (OSError, ValueError) as exc:
        raise PdfParseError("PDF_OUTPUT_DIRECTORY_INVALID", "PDF artifact directory is invalid.") from exc


def _write_json(path: Path, payload: dict[str, Any]) -> None:
    path.write_text(
        json.dumps(payload, ensure_ascii=False, indent=2, sort_keys=True) + "\n",
        encoding="utf-8",
        newline="\n",
    )


def _write_audit_jsonl(path: Path, audits: tuple[dict[str, Any], ...]) -> None:
    content = "".join(
        json.dumps(item, ensure_ascii=False, sort_keys=True, separators=(",", ":")) + "\n"
        for item in audits
    )
    path.write_text(content, encoding="utf-8", newline="\n")


def _batch_manifest_sha256(extractions: tuple[PdfExtractionContract, ...]) -> str:
    return _stable_hash(
        {
            "schemaVersion": 1,
            "parserVersion": PARSER_VERSION,
            "documents": [
                {
                    "documentId": item.documentId,
                    "sourceSha256": item.sourceSha256,
                    "extractionSha256": item.extractionSha256,
                }
                for item in extractions
            ],
        }
    )


def parse_pdf_release(
    source_root: Path,
    output_root: Path,
    corpus_version: str,
    *,
    renderer: PdfRenderer | None = None,
    ocr_provider: OcrProvider | None = None,
    parsed_at: datetime | None = None,
    render_dpi: int = DEFAULT_RENDER_DPI,
) -> tuple[tuple[PdfExtractionContract, ...], PdfExtractionReport, Path]:
    source, output = validate_output_root(source_root, output_root)
    if not re.fullmatch(r"[a-z0-9][a-z0-9._-]{2,79}", corpus_version):
        raise PdfParseError("PDF_CORPUS_VERSION_INVALID", "The corpus version is invalid.")
    release_root = output / "releases" / corpus_version
    inventory: SourceInventoryManifest = load_source_inventory(
        release_root / "source-inventory.json"
    )
    if inventory.corpusVersion != corpus_version:
        raise PdfParseError(
            "PDF_CORPUS_VERSION_MISMATCH",
            "The requested corpus version does not match the source inventory.",
        )
    records = tuple(
        record for record in inventory.files if record.sourceType == SourceType.PDF
    )
    if not records:
        raise PdfParseError("PDF_SOURCE_EMPTY", "The source inventory contains no PDF files.")

    documents_root = release_root / "documents"
    qa_root = release_root / "qa"
    _ensure_safe_artifact_directory(documents_root, output)
    _ensure_safe_artifact_directory(qa_root, output)
    render_root = qa_root / "pdf-render"
    if render_root.exists():
        raise PdfParseError(
            "PDF_EXTRACTION_ALREADY_EXISTS",
            "PDF extraction artifacts already exist and will not be overwritten.",
        )
    report_path = qa_root / "pdf-extraction-report.json"
    audit_path = qa_root / "ocr-call-log.jsonl"
    targets = tuple(
        documents_root / f"{record.documentId}.pdf-extraction.json"
        for record in records
    )
    if report_path.exists() or audit_path.exists() or any(target.exists() for target in targets):
        raise PdfParseError(
            "PDF_EXTRACTION_ALREADY_EXISTS",
            "PDF extraction artifacts already exist and will not be overwritten.",
        )

    effective_renderer = renderer or resolve_pdf_renderer()
    effective_parsed_at = parsed_at or datetime.now(ZoneInfo(TIMEZONE))
    staging_root = release_root / f".pdf-staging-{uuid.uuid4().hex}"
    staged_render_root = staging_root / "qa" / "pdf-render"
    staged_documents_root = staging_root / "documents"
    staged_qa_root = staging_root / "qa"
    try:
        staged_documents_root.mkdir(parents=True)
        staged_render_root.mkdir(parents=True)
        extractions_list = []
        all_audits = []
        for record in records:
            extraction, audits = parse_pdf(
                resolve_inventory_source_file(source, record),
                record,
                corpus_version,
                render_directory=staged_render_root / record.documentId,
                renderer=effective_renderer,
                ocr_provider=ocr_provider,
                parsed_at=effective_parsed_at,
                render_dpi=render_dpi,
            )
            extractions_list.append(extraction)
            all_audits.extend(audits)
        extractions = tuple(extractions_list)
        summaries = tuple(
            PdfDocumentExtractionSummary(
                documentId=item.documentId,
                sourceFileName=item.sourceFileName,
                status=item.status,
                extractionSha256=item.extractionSha256,
                pageCount=item.package.pageCount,
                renderedPageCount=item.metrics.renderedPageCount,
                ocrSucceededPageCount=item.metrics.ocrSucceededPageCount,
                qualityFlags=item.qualityFlags,
            )
            for item in extractions
        )
        report = PdfExtractionReport(
            corpusVersion=corpus_version,
            generatedAt=effective_parsed_at,
            status=BuildStatus.REQUIRES_REVIEW,
            expectedPdfCount=len(extractions),
            succeededCount=len(extractions),
            failedCount=0,
            renderedPageCount=sum(
                item.metrics.renderedPageCount for item in extractions
            ),
            ocrPendingPageCount=sum(
                item.metrics.ocrNotConfiguredPageCount
                + item.metrics.ocrFailedPageCount
                for item in extractions
            ),
            visualValidationPendingPageCount=sum(
                item.visualValidation.renderedPageCount
                - item.visualValidation.reviewedPageCount
                for item in extractions
            ),
            documents=summaries,
            extractionManifestSha256=_batch_manifest_sha256(extractions),
        )
        for item in extractions:
            _write_json(
                staged_documents_root / f"{item.documentId}.pdf-extraction.json",
                item.model_dump(mode="json"),
            )
        _write_json(staged_qa_root / report_path.name, report.model_dump(mode="json"))
        _write_audit_jsonl(staged_qa_root / audit_path.name, tuple(all_audits))
        published_paths: list[Path] = []
        os.rename(staged_render_root, render_root)
        published_paths.append(render_root)
        for staged in staged_documents_root.iterdir():
            target = documents_root / staged.name
            os.rename(staged, target)
            published_paths.append(target)
        os.rename(staged_qa_root / report_path.name, report_path)
        published_paths.append(report_path)
        os.rename(staged_qa_root / audit_path.name, audit_path)
        published_paths.append(audit_path)
    except PdfParseError:
        raise
    except OSError as exc:
        for published in reversed(locals().get("published_paths", [])):
            if published.is_dir():
                shutil.rmtree(published, ignore_errors=True)
            else:
                try:
                    published.unlink(missing_ok=True)
                except OSError:
                    pass
        raise PdfParseError(
            "PDF_OUTPUT_WRITE_FAILED",
            "Unable to write PDF extraction artifacts.",
        ) from exc
    finally:
        if staging_root.exists():
            shutil.rmtree(staging_root, ignore_errors=True)
    return extractions, report, report_path


def _load_pdf_extraction(path: Path) -> PdfExtractionContract:
    if not path.is_file() or path.stat().st_size > 50 * 1024 * 1024:
        raise PdfParseError(
            "PDF_EXTRACTION_NOT_FOUND",
            "PDF extraction artifact is missing or too large.",
        )
    try:
        extraction = PdfExtractionContract.model_validate_json(
            path.read_text(encoding="utf-8")
        )
    except (OSError, UnicodeError, ValidationError, ValueError) as exc:
        raise PdfParseError(
            "PDF_EXTRACTION_INVALID",
            "PDF extraction artifact is invalid.",
        ) from exc
    actual_sha256 = _stable_hash(_pdf_extraction_stable_payload(extraction))
    if actual_sha256 != extraction.extractionSha256:
        raise PdfParseError(
            "PDF_EXTRACTION_CHECKSUM_MISMATCH",
            "PDF extraction checksum is invalid.",
        )
    return extraction


def _resolve_rendered_artifact(
    release_root: Path,
    page: PdfPageExtraction,
    document_id: str,
) -> Path:
    relative = PurePosixPath(page.renderedArtifact)
    expected_parent = PurePosixPath("qa") / "pdf-render" / document_id
    if (
        relative.is_absolute()
        or ".." in relative.parts
        or relative.parent != expected_parent
        or relative.name != f"page-{page.pageNumber:03d}.png"
    ):
        raise PdfParseError(
            "PDF_RENDER_ARTIFACT_PATH_INVALID",
            "A rendered page artifact path is invalid.",
        )
    artifact = release_root.joinpath(*relative.parts)
    if not artifact.is_file():
        raise PdfParseError(
            "PDF_RENDER_ARTIFACT_MISSING",
            "A rendered page artifact is missing.",
        )
    attributes = getattr(artifact.lstat(), "st_file_attributes", 0)
    if artifact.is_symlink() or attributes & getattr(
        stat,
        "FILE_ATTRIBUTE_REPARSE_POINT",
        0,
    ):
        raise PdfParseError(
            "PDF_OUTPUT_LINK_FORBIDDEN",
            "Rendered page artifacts must not be links or reparse points.",
        )
    try:
        artifact.resolve(strict=True).relative_to(release_root.resolve(strict=True))
    except (OSError, ValueError) as exc:
        raise PdfParseError(
            "PDF_RENDER_ARTIFACT_PATH_INVALID",
            "A rendered page artifact escaped the release root.",
        ) from exc
    return artifact


def record_pdf_visual_review(
    source_root: Path,
    output_root: Path,
    corpus_version: str,
    document_id: str,
    expected_extraction_sha256: str,
    *,
    reviewed_at: datetime | None = None,
    notes: tuple[str, ...] = (),
) -> tuple[PdfExtractionContract, PdfExtractionReport]:
    source, output = validate_output_root(source_root, output_root)
    if not re.fullmatch(r"[a-z0-9][a-z0-9._-]{2,79}", corpus_version):
        raise PdfParseError("PDF_CORPUS_VERSION_INVALID", "The corpus version is invalid.")
    if not re.fullmatch(r"[a-z0-9][a-z0-9._/-]{2,127}", document_id):
        raise PdfParseError("PDF_DOCUMENT_ID_INVALID", "The PDF document ID is invalid.")
    if not re.fullmatch(r"[0-9a-f]{64}", expected_extraction_sha256):
        raise PdfParseError(
            "PDF_EXTRACTION_CHECKSUM_INVALID",
            "Expected PDF extraction checksum is invalid.",
        )
    release_root = output / "releases" / corpus_version
    inventory = load_source_inventory(release_root / "source-inventory.json")
    records = tuple(
        record
        for record in inventory.files
        if record.sourceType == SourceType.PDF and record.documentId == document_id
    )
    if len(records) != 1:
        raise PdfParseError(
            "PDF_DOCUMENT_NOT_IN_INVENTORY",
            "The PDF document is not uniquely present in the source inventory.",
        )
    resolve_inventory_source_file(source, records[0])

    extraction_path = (
        release_root / "documents" / f"{document_id}.pdf-extraction.json"
    )
    report_path = release_root / "qa" / "pdf-extraction-report.json"
    extraction = _load_pdf_extraction(extraction_path)
    if extraction.extractionSha256 != expected_extraction_sha256:
        raise PdfParseError(
            "PDF_EXTRACTION_COMPARE_AND_SET_FAILED",
            "PDF extraction changed after it was reviewed.",
        )
    if extraction.visualValidation.status == VisualValidationStatus.SUCCEEDED:
        raise PdfParseError(
            "PDF_VISUAL_REVIEW_ALREADY_RECORDED",
            "PDF visual review was already recorded.",
        )
    if extraction.metrics.renderedPageCount != extraction.package.pageCount:
        raise PdfParseError(
            "PDF_RENDER_PAGE_COUNT_MISMATCH",
            "Rendered page count does not match the PDF package.",
        )
    for page in extraction.pages:
        artifact = _resolve_rendered_artifact(
            release_root,
            page,
            document_id,
        )
        if _sha256_file(artifact) != page.renderedSha256:
            raise PdfParseError(
                "PDF_RENDER_ARTIFACT_CHECKSUM_MISMATCH",
                "A rendered page changed after extraction.",
            )
        width, height = _inspect_rendered_image(artifact)
        if (width, height) != (page.pixelWidth, page.pixelHeight):
            raise PdfParseError(
                "PDF_RENDER_ARTIFACT_DIMENSION_MISMATCH",
                "A rendered page dimension changed after extraction.",
            )

    effective_reviewed_at = reviewed_at or datetime.now(ZoneInfo(TIMEZONE))
    review_notes = notes or (
        "All pages were checked for orientation, clipping, blank output, glyph errors, and continuity.",
    )
    if any(
        len(note) > 500
        or re.search(r"(?:[A-Za-z]:[\\/]|\\\\|/(?:home|Users|tmp)/)", note)
        for note in review_notes
    ):
        raise PdfParseError(
            "PDF_VISUAL_REVIEW_NOTE_INVALID",
            "PDF visual review notes must be short and must not contain local paths.",
        )
    visual_validation = PdfVisualValidation(
        status=VisualValidationStatus.SUCCEEDED,
        renderer=extraction.visualValidation.renderer,
        rendererVersion=extraction.visualValidation.rendererVersion,
        renderedPageCount=extraction.package.pageCount,
        reviewedPageCount=extraction.package.pageCount,
        reviewedAt=effective_reviewed_at,
        notes=review_notes,
    )
    flags = tuple(
        flag
        for flag in extraction.qualityFlags
        if flag != "VISUAL_REVIEW_PENDING"
    ) + ("VISUAL_REVIEW_PASSED",)
    updated_without_hash = extraction.model_copy(
        update={
            "qualityFlags": flags,
            "visualValidation": visual_validation,
            "extractionSha256": "0" * 64,
        }
    )
    updated = updated_without_hash.model_copy(
        update={
            "extractionSha256": _stable_hash(
                _pdf_extraction_stable_payload(updated_without_hash)
            )
        }
    )

    try:
        report = PdfExtractionReport.model_validate_json(
            report_path.read_text(encoding="utf-8")
        )
    except (OSError, UnicodeError, ValidationError, ValueError) as exc:
        raise PdfParseError(
            "PDF_EXTRACTION_REPORT_INVALID",
            "PDF extraction report is invalid.",
        ) from exc
    summaries = tuple(
        PdfDocumentExtractionSummary(
            documentId=updated.documentId,
            sourceFileName=updated.sourceFileName,
            status=updated.status,
            extractionSha256=updated.extractionSha256,
            pageCount=updated.package.pageCount,
            renderedPageCount=updated.metrics.renderedPageCount,
            ocrSucceededPageCount=updated.metrics.ocrSucceededPageCount,
            qualityFlags=updated.qualityFlags,
        )
        if summary.documentId == document_id
        else summary
        for summary in report.documents
    )
    all_extractions = tuple(
        updated
        if summary.documentId == document_id
        else _load_pdf_extraction(
            release_root
            / "documents"
            / f"{summary.documentId}.pdf-extraction.json"
        )
        for summary in summaries
    )
    updated_report = report.model_copy(
        update={
            "documents": summaries,
            "visualValidationPendingPageCount": sum(
                item.visualValidation.renderedPageCount
                - item.visualValidation.reviewedPageCount
                for item in all_extractions
            ),
            "extractionManifestSha256": _batch_manifest_sha256(all_extractions),
        }
    )

    staging_root = release_root / f".pdf-review-staging-{uuid.uuid4().hex}"
    try:
        staging_root.mkdir()
        staged_extraction = staging_root / extraction_path.name
        staged_report = staging_root / report_path.name
        _write_json(staged_extraction, updated.model_dump(mode="json"))
        _write_json(staged_report, updated_report.model_dump(mode="json"))
        os.replace(staged_extraction, extraction_path)
        os.replace(staged_report, report_path)
    except OSError as exc:
        raise PdfParseError(
            "PDF_VISUAL_REVIEW_WRITE_FAILED",
            "Unable to record PDF visual review.",
        ) from exc
    finally:
        if staging_root.exists():
            shutil.rmtree(staging_root, ignore_errors=True)
    return updated, updated_report
