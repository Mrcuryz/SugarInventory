from __future__ import annotations

from io import BytesIO
from types import SimpleNamespace

import pytest
from PIL import Image

from app.rag.offline.local_ocr import (
    LOCAL_RAPIDOCR_MODEL,
    LocalRapidOcrProvider,
    LocalRapidOcrSettings,
)
from app.rag.offline.ocr_provider import resolve_ocr_provider_from_environment
from app.rag.offline.pdf_parser import OcrProviderError, PdfParseError


def _png_bytes(width: int = 200, height: int = 100) -> bytes:
    output = BytesIO()
    Image.new("RGB", (width, height), color="white").save(output, format="PNG")
    return output.getvalue()


def test_local_rapidocr_maps_text_confidence_and_normalized_regions() -> None:
    def engine(image):
        assert image.shape == (100, 200, 3)
        return SimpleNamespace(
            boxes=(
                ((20, 10), (120, 10), (120, 30), (20, 30)),
                ((10, 50), (190, 50), (190, 90), (10, 90)),
            ),
            txts=("来宾糖业", "仓储管理"),
            scores=(0.98, 0.88),
        )

    provider = LocalRapidOcrProvider(
        LocalRapidOcrSettings(),
        engine=engine,
    )
    image = _png_bytes()
    result = provider.recognize(
        image=image,
        document_id="doc-example",
        page_number=1,
    )

    assert provider.name == "local-rapidocr"
    assert provider.model == LOCAL_RAPIDOCR_MODEL
    assert result.text == "来宾糖业\n仓储管理"
    assert result.confidence == pytest.approx((0.98 * 4 + 0.88 * 4) / 8)
    assert result.regions[0].regionType.value == "BODY"
    assert result.regions[0].boundingBox.model_dump() == {
        "x": 0.1,
        "y": 0.1,
        "width": 0.5,
        "height": 0.2,
    }
    assert result.request_bytes == len(image)
    assert result.response_bytes > 0
    assert result.duration_ms >= 0


@pytest.mark.parametrize(
    "output",
    [
        SimpleNamespace(boxes=(), txts=(), scores=()),
        SimpleNamespace(
            boxes=(((0, 0), (10, 0), (10, 10), (0, 10)),),
            txts=("文本", "多余"),
            scores=(0.9,),
        ),
        SimpleNamespace(
            boxes=(((0, 0), (10, 0), (10, 10), (0, 10)),),
            txts=("文本",),
            scores=(1.1,),
        ),
    ],
)
def test_local_rapidocr_rejects_empty_or_malformed_results(output) -> None:
    provider = LocalRapidOcrProvider(
        LocalRapidOcrSettings(),
        engine=lambda image: output,
    )

    with pytest.raises(OcrProviderError) as error:
        provider.recognize(
            image=_png_bytes(),
            document_id="doc-example",
            page_number=1,
        )

    assert error.value.code in {"OCR_NO_TEXT_DETECTED", "OCR_RESPONSE_INVALID"}


def test_local_rapidocr_rejects_non_png_input() -> None:
    provider = LocalRapidOcrProvider(
        LocalRapidOcrSettings(),
        engine=lambda image: None,
    )

    with pytest.raises(OcrProviderError) as error:
        provider.recognize(
            image=b"not-a-png",
            document_id="doc-example",
            page_number=1,
        )

    assert error.value.code == "OCR_IMAGE_INVALID"


def test_local_rapidocr_model_is_fail_closed(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    monkeypatch.setenv("RAG_OCR_MODEL", "unapproved-model")

    with pytest.raises(PdfParseError) as error:
        LocalRapidOcrSettings.from_environment()

    assert error.value.code == "OCR_LOCAL_MODEL_UNSUPPORTED"


def test_ocr_provider_selection_is_disabled_by_default(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    monkeypatch.delenv("RAG_OCR_ENABLED", raising=False)
    monkeypatch.setenv("RAG_OCR_PROVIDER", "invalid-but-disabled")

    assert resolve_ocr_provider_from_environment() is None


def test_ocr_provider_selection_rejects_unknown_enabled_provider(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    monkeypatch.setenv("RAG_OCR_ENABLED", "true")
    monkeypatch.setenv("RAG_OCR_PROVIDER", "unknown")

    with pytest.raises(PdfParseError) as error:
        resolve_ocr_provider_from_environment()

    assert error.value.code == "OCR_PROVIDER_INVALID"
