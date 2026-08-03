from __future__ import annotations

import json
import math
import os
import time
from dataclasses import dataclass
from importlib import metadata
from io import BytesIO
from typing import Any, Callable

from PIL import Image

from app.rag.contracts import PdfBoundingBox, PdfRegionType
from app.rag.offline.pdf_parser import (
    MAX_OCR_IMAGE_BYTES,
    OcrProviderError,
    OcrResult,
    PdfParseError,
    _OcrRegionResponse,
    _normalized_text,
)


RAPIDOCR_PACKAGE_VERSION = "3.9.2"
ONNXRUNTIME_PACKAGE_VERSION = "1.23.2"
LOCAL_RAPIDOCR_MODEL = "rapidocr-3.9.2-ppocrv6-small-ch-onnx-cpu"


@dataclass(frozen=True)
class LocalRapidOcrSettings:
    model: str = LOCAL_RAPIDOCR_MODEL

    @classmethod
    def from_environment(cls) -> LocalRapidOcrSettings:
        model = os.getenv("RAG_OCR_MODEL", LOCAL_RAPIDOCR_MODEL).strip()
        settings = cls(model=model or LOCAL_RAPIDOCR_MODEL)
        settings.validate()
        return settings

    def validate(self) -> None:
        if self.model != LOCAL_RAPIDOCR_MODEL:
            raise PdfParseError(
                "OCR_LOCAL_MODEL_UNSUPPORTED",
                "The configured local OCR model is not installed.",
            )


class LocalRapidOcrProvider:
    def __init__(
        self,
        settings: LocalRapidOcrSettings,
        *,
        engine: Callable[[Any], Any] | None = None,
    ):
        settings.validate()
        self._settings = settings
        self._engine = engine or self._create_engine()

    @property
    def name(self) -> str:
        return "local-rapidocr"

    @property
    def model(self) -> str:
        return self._settings.model

    @staticmethod
    def _create_engine() -> Callable[[Any], Any]:
        try:
            rapidocr_version = metadata.version("rapidocr")
            onnxruntime_version = metadata.version("onnxruntime")
        except metadata.PackageNotFoundError as exc:
            raise PdfParseError(
                "OCR_LOCAL_DEPENDENCY_MISSING",
                "Install the rag-build optional dependencies before using local OCR.",
            ) from exc
        if rapidocr_version != RAPIDOCR_PACKAGE_VERSION:
            raise PdfParseError(
                "OCR_LOCAL_VERSION_MISMATCH",
                "The installed RapidOCR version does not match the approved build version.",
            )
        if onnxruntime_version != ONNXRUNTIME_PACKAGE_VERSION:
            raise PdfParseError(
                "OCR_LOCAL_VERSION_MISMATCH",
                "The installed ONNX Runtime version does not match the approved build version.",
            )
        try:
            import onnxruntime
            from rapidocr import RapidOCR
        except (ImportError, OSError) as exc:
            raise PdfParseError(
                "OCR_LOCAL_DEPENDENCY_UNAVAILABLE",
                "The local OCR dependencies could not be loaded.",
            ) from exc
        if "CPUExecutionProvider" not in onnxruntime.get_available_providers():
            raise PdfParseError(
                "OCR_LOCAL_CPU_PROVIDER_UNAVAILABLE",
                "ONNX Runtime CPU execution provider is unavailable.",
            )
        try:
            return RapidOCR(params={"Global.log_level": "warning"})
        except Exception as exc:
            raise PdfParseError(
                "OCR_LOCAL_INITIALIZATION_FAILED",
                "RapidOCR could not initialize the approved local model.",
            ) from exc

    @staticmethod
    def _decode_png(image: bytes) -> tuple[Any, int, int]:
        try:
            import numpy

            with Image.open(BytesIO(image)) as source:
                if source.format != "PNG":
                    raise OcrProviderError(
                        "OCR_IMAGE_FORMAT_INVALID",
                        "Local OCR accepts rendered PNG pages only.",
                    )
                width, height = source.size
                if width < 1 or height < 1:
                    raise OcrProviderError(
                        "OCR_IMAGE_DIMENSIONS_INVALID",
                        "Rendered OCR page dimensions are invalid.",
                    )
                rgb = source.convert("RGB")
                bgr = numpy.ascontiguousarray(numpy.asarray(rgb)[:, :, ::-1])
                return bgr, width, height
        except OcrProviderError:
            raise
        except (ImportError, OSError, ValueError, Image.DecompressionBombError) as exc:
            raise OcrProviderError(
                "OCR_IMAGE_INVALID",
                "Local OCR could not decode the rendered page.",
            ) from exc

    @staticmethod
    def _region(
        *,
        text: str,
        confidence: float,
        box: Any,
        width: int,
        height: int,
    ) -> _OcrRegionResponse:
        try:
            points = tuple((float(point[0]), float(point[1])) for point in box)
        except (TypeError, ValueError, IndexError) as exc:
            raise OcrProviderError(
                "OCR_RESPONSE_INVALID",
                "Local OCR returned an invalid text region.",
            ) from exc
        if len(points) < 4 or any(
            not math.isfinite(value) for point in points for value in point
        ):
            raise OcrProviderError(
                "OCR_RESPONSE_INVALID",
                "Local OCR returned an invalid text region.",
            )
        left = max(0.0, min(point[0] for point in points))
        top = max(0.0, min(point[1] for point in points))
        right = min(float(width), max(point[0] for point in points))
        bottom = min(float(height), max(point[1] for point in points))
        if right <= left or bottom <= top:
            raise OcrProviderError(
                "OCR_RESPONSE_INVALID",
                "Local OCR returned an empty text region.",
            )
        return _OcrRegionResponse(
            regionType=PdfRegionType.BODY,
            text=text,
            confidence=confidence,
            boundingBox=PdfBoundingBox(
                x=left / width,
                y=top / height,
                width=(right - left) / width,
                height=(bottom - top) / height,
            ),
        )

    def recognize(
        self,
        *,
        image: bytes,
        document_id: str,
        page_number: int,
    ) -> OcrResult:
        del document_id, page_number
        if not image or len(image) > MAX_OCR_IMAGE_BYTES:
            raise OcrProviderError(
                "OCR_IMAGE_TOO_LARGE",
                "Rendered page is empty or exceeds the OCR request limit.",
            )
        decoded, width, height = self._decode_png(image)
        started = time.monotonic()
        try:
            output = self._engine(decoded)
        except Exception as exc:
            raise OcrProviderError(
                "OCR_LOCAL_INFERENCE_FAILED",
                "Local OCR inference failed.",
            ) from exc
        boxes = getattr(output, "boxes", None)
        texts = getattr(output, "txts", None)
        scores = getattr(output, "scores", None)
        if boxes is None or texts is None or scores is None:
            raise OcrProviderError(
                "OCR_NO_TEXT_DETECTED",
                "Local OCR did not detect usable text.",
            )
        try:
            item_count = len(texts)
        except TypeError as exc:
            raise OcrProviderError(
                "OCR_RESPONSE_INVALID",
                "Local OCR returned an invalid result.",
            ) from exc
        if item_count == 0:
            raise OcrProviderError(
                "OCR_NO_TEXT_DETECTED",
                "Local OCR did not detect usable text.",
            )
        if len(boxes) != item_count or len(scores) != item_count:
            raise OcrProviderError(
                "OCR_RESPONSE_INVALID",
                "Local OCR result arrays have different lengths.",
            )

        regions = []
        weights = []
        weighted_scores = []
        for raw_text, raw_score, box in zip(texts, scores, boxes, strict=True):
            text = _normalized_text(str(raw_text))
            try:
                confidence = float(raw_score)
            except (TypeError, ValueError) as exc:
                raise OcrProviderError(
                    "OCR_RESPONSE_INVALID",
                    "Local OCR returned an invalid confidence.",
                ) from exc
            if not text or not math.isfinite(confidence) or not 0 <= confidence <= 1:
                raise OcrProviderError(
                    "OCR_RESPONSE_INVALID",
                    "Local OCR returned blank text or an invalid confidence.",
                )
            regions.append(
                self._region(
                    text=text,
                    confidence=confidence,
                    box=box,
                    width=width,
                    height=height,
                )
            )
            weight = max(1, len(text))
            weights.append(weight)
            weighted_scores.append(confidence * weight)

        page_text = "\n".join(region.text for region in regions)
        response_bytes = len(
            json.dumps(
                {
                    "text": page_text,
                    "regions": [
                        region.model_dump(mode="json", exclude_none=True)
                        for region in regions
                    ],
                },
                ensure_ascii=False,
                separators=(",", ":"),
            ).encode("utf-8")
        )
        return OcrResult(
            text=page_text,
            confidence=sum(weighted_scores) / sum(weights),
            regions=tuple(regions),
            request_bytes=len(image),
            response_bytes=response_bytes,
            duration_ms=max(0, round((time.monotonic() - started) * 1000)),
        )
