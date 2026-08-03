from __future__ import annotations

import os

from app.rag.offline.pdf_parser import (
    HttpJsonOcrProvider,
    OcrProvider,
    OcrProviderSettings,
    PdfParseError,
)


def _ocr_enabled() -> bool:
    return os.getenv("RAG_OCR_ENABLED", "").strip().casefold() in {
        "1",
        "true",
        "yes",
    }


def resolve_ocr_provider_from_environment() -> OcrProvider | None:
    if not _ocr_enabled():
        return None
    provider_name = (
        os.getenv("RAG_OCR_PROVIDER", "http-json").strip().casefold()
        or "http-json"
    )
    if provider_name == "http-json":
        return HttpJsonOcrProvider(OcrProviderSettings.from_environment())
    if provider_name == "local-rapidocr":
        from app.rag.offline.local_ocr import (
            LocalRapidOcrProvider,
            LocalRapidOcrSettings,
        )

        return LocalRapidOcrProvider(LocalRapidOcrSettings.from_environment())
    raise PdfParseError(
        "OCR_PROVIDER_INVALID",
        "RAG_OCR_PROVIDER must be http-json or local-rapidocr.",
    )
