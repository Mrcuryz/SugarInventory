from __future__ import annotations

import json
from datetime import datetime
from pathlib import Path
from zoneinfo import ZoneInfo

import httpx
import pytest
from PIL import Image

from app.rag.contracts import BuildStatus, PdfOcrStatus, PdfTextLayerStatus
from app.rag.offline import pdf_parser, source_inventory
from app.rag.offline.cli import main
from app.rag.offline.pdf_parser import (
    HttpJsonOcrProvider,
    OcrProviderError,
    OcrProviderSettings,
    OcrResult,
    PdfParseError,
    parse_pdf,
    parse_pdf_release,
    record_pdf_visual_review,
)
from app.rag.offline.source_inventory import (
    build_source_inventory,
    write_inventory_release,
)
from tests.rag.pdf_fixture import write_pdf


NOW = datetime(2026, 7, 29, 12, 0, tzinfo=ZoneInfo("Asia/Shanghai"))
CORPUS_VERSION = "laibin-rag-2026-07-29-v1"


class FakeRenderer:
    name = "fake-pdftoppm"
    version = "fake-pdftoppm 1.0"

    def render_page(
        self,
        source: Path,
        page_number: int,
        output: Path,
        *,
        dpi: int,
    ) -> None:
        del source, dpi
        Image.new(
            "RGB",
            (300 + page_number, 400 + page_number),
            color=(page_number, 20, 30),
        ).save(output, format="PNG")


class FakeOcrProvider:
    name = "fixture-ocr"
    model = "fixture-v1"

    def recognize(
        self,
        *,
        image: bytes,
        document_id: str,
        page_number: int,
    ) -> OcrResult:
        del document_id
        response = pdf_parser._OcrRegionResponse(
            regionType="TITLE",
            text=f"第{page_number}页标题",
            confidence=0.98,
            boundingBox={"x": 0.1, "y": 0.1, "width": 0.8, "height": 0.1},
        )
        return OcrResult(
            text=f"第{page_number}页宣传内容",
            confidence=0.97,
            regions=(response,),
            request_bytes=len(image),
            response_bytes=128,
            duration_ms=5,
        )


class FailingOcrProvider:
    name = "fixture-ocr"
    model = "fixture-v1"

    def recognize(
        self,
        *,
        image: bytes,
        document_id: str,
        page_number: int,
    ) -> OcrResult:
        del image, document_id, page_number
        raise OcrProviderError("OCR_FIXTURE_FAILURE", "fixture failure")


def _record_for(source: Path, file_name: str):
    inventory = build_source_inventory(source, CORPUS_VERSION, generated_at=NOW)
    return next(item for item in inventory.files if item.sourceFileName == file_name)


def _release(
    tmp_path: Path,
    monkeypatch: pytest.MonkeyPatch,
) -> tuple[Path, Path]:
    source = tmp_path / "source"
    output = tmp_path / "ignored-artifacts"
    write_pdf(source / "宣传册.pdf")
    monkeypatch.setattr(source_inventory, "PROJECT_ROOT", tmp_path)
    monkeypatch.setattr(source_inventory, "is_git_ignored", lambda path: True)
    write_inventory_release(source, output, CORPUS_VERSION, generated_at=NOW)
    return source, output


def test_pdf_parser_renders_every_page_and_marks_missing_ocr(
    tmp_path: Path,
) -> None:
    source = tmp_path / "source"
    path = source / "宣传册.pdf"
    write_pdf(path)
    record = _record_for(source, path.name)
    render_directory = tmp_path / "render"

    extraction, audits = parse_pdf(
        path,
        record,
        CORPUS_VERSION,
        render_directory=render_directory,
        renderer=FakeRenderer(),
        parsed_at=NOW,
    )

    assert extraction.status == BuildStatus.REQUIRES_REVIEW
    assert extraction.package.pageCount == 2
    assert extraction.package.title == "RAG PDF 测试"
    assert extraction.metrics.renderedPageCount == 2
    assert extraction.metrics.emptyTextLayerPageCount == 2
    assert extraction.metrics.ocrNotConfiguredPageCount == 2
    assert [page.textLayerStatus for page in extraction.pages] == [
        PdfTextLayerStatus.EMPTY,
        PdfTextLayerStatus.EMPTY,
    ]
    assert all(page.ocrStatus == PdfOcrStatus.NOT_CONFIGURED for page in extraction.pages)
    assert all((render_directory / f"page-{number:03d}.png").is_file() for number in (1, 2))
    assert all(audit["status"] == "NOT_CONFIGURED" for audit in audits)
    assert str(source.resolve()) not in extraction.model_dump_json()


def test_text_layer_requires_enough_content_to_avoid_false_usable_status() -> None:
    assert pdf_parser._text_layer_status("") == PdfTextLayerStatus.EMPTY
    assert pdf_parser._text_layer_status("目录" * 40) == PdfTextLayerStatus.SPARSE
    assert pdf_parser._text_layer_status("企业介绍内容" * 40) == PdfTextLayerStatus.PRESENT


def test_pdf_parser_preserves_page_regions_and_sanitized_audit(
    tmp_path: Path,
) -> None:
    source = tmp_path / "source"
    path = source / "宣传册.pdf"
    write_pdf(path, page_count=1)
    record = _record_for(source, path.name)

    extraction, audits = parse_pdf(
        path,
        record,
        CORPUS_VERSION,
        render_directory=tmp_path / "render",
        renderer=FakeRenderer(),
        ocr_provider=FakeOcrProvider(),
        parsed_at=NOW,
    )

    page = extraction.pages[0]
    assert page.ocrStatus == PdfOcrStatus.SUCCEEDED
    assert page.ocrText == "第1页宣传内容"
    assert page.regions[0].regionType.value == "TITLE"
    assert page.regions[0].boundingBox.x == 0.1
    assert audits[0] == {
        "schemaVersion": 1,
        "documentId": record.documentId,
        "pageNumber": 1,
        "provider": "fixture-ocr",
        "model": "fixture-v1",
        "status": "SUCCEEDED",
        "durationMs": 5,
        "requestBytes": audits[0]["requestBytes"],
        "responseBytes": 128,
        "errorCode": None,
    }
    assert audits[0]["requestBytes"] > 0
    assert str(path.resolve()) not in json.dumps(audits, ensure_ascii=False)


def test_pdf_parser_records_ocr_failure_without_losing_render(
    tmp_path: Path,
) -> None:
    source = tmp_path / "source"
    path = source / "宣传册.pdf"
    write_pdf(path, page_count=1)
    record = _record_for(source, path.name)

    extraction, audits = parse_pdf(
        path,
        record,
        CORPUS_VERSION,
        render_directory=tmp_path / "render",
        renderer=FakeRenderer(),
        ocr_provider=FailingOcrProvider(),
        parsed_at=NOW,
    )

    assert extraction.pages[0].ocrStatus == PdfOcrStatus.FAILED
    assert "OCR_FAILED" in extraction.qualityFlags
    assert audits[0]["errorCode"] == "OCR_FIXTURE_FAILURE"


@pytest.mark.parametrize(
    ("settings", "error_code"),
    [
        (
            OcrProviderSettings(
                enabled=True,
                endpoint="http://ocr.example.test/v1",
                allowed_hosts=("ocr.example.test",),
                api_key="secret",
                model="ocr-v1",
            ),
            "OCR_ENDPOINT_INVALID",
        ),
        (
            OcrProviderSettings(
                enabled=True,
                endpoint="https://ocr.example.test/v1",
                allowed_hosts=("other.example.test",),
                api_key="secret",
                model="ocr-v1",
            ),
            "OCR_HOST_NOT_ALLOWED",
        ),
    ],
)
def test_ocr_settings_fail_closed(
    settings: OcrProviderSettings,
    error_code: str,
) -> None:
    with pytest.raises(PdfParseError) as error:
        settings.validate()
    assert error.value.code == error_code


def test_disabled_ocr_ignores_unrelated_provider_values(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    monkeypatch.setenv("RAG_OCR_ENABLED", "false")
    monkeypatch.setenv("RAG_OCR_TIMEOUT_SECONDS", "not-a-number")
    monkeypatch.setenv("RAG_OCR_ENDPOINT", "http://unsafe.invalid")

    settings = OcrProviderSettings.from_environment()

    assert settings == OcrProviderSettings(enabled=False)


def test_http_ocr_provider_uses_contract_and_never_sends_local_path() -> None:
    def handler(request: httpx.Request) -> httpx.Response:
        payload = json.loads(request.content)
        assert payload["documentId"] == "doc-example"
        assert payload["pageNumber"] == 3
        assert payload["mimeType"] == "image/png"
        assert "path" not in json.dumps(payload).casefold()
        assert request.headers["Authorization"] == "Bearer secret"
        return httpx.Response(
            200,
            json={
                "text": "识别结果",
                "confidence": 0.96,
                "regions": [],
            },
        )

    settings = OcrProviderSettings(
        enabled=True,
        endpoint="https://ocr.example.test/v1/recognize",
        allowed_hosts=("ocr.example.test",),
        api_key="secret",
        model="ocr-v1",
    )
    client = httpx.Client(transport=httpx.MockTransport(handler))
    provider = HttpJsonOcrProvider(settings, client=client)

    result = provider.recognize(
        image=b"png-bytes",
        document_id="doc-example",
        page_number=3,
    )

    assert result.text == "识别结果"
    assert result.confidence == 0.96


def test_http_ocr_provider_rejects_blank_or_out_of_bounds_response() -> None:
    client = httpx.Client(
        transport=httpx.MockTransport(
            lambda request: httpx.Response(
                200,
                json={
                    "text": " ",
                    "regions": [
                        {
                            "text": "区域",
                            "boundingBox": {
                                "x": 0.9,
                                "y": 0.1,
                                "width": 0.2,
                                "height": 0.2,
                            },
                        }
                    ],
                },
            )
        )
    )
    provider = HttpJsonOcrProvider(
        OcrProviderSettings(
            enabled=True,
            endpoint="https://ocr.example.test/v1/recognize",
            allowed_hosts=("ocr.example.test",),
            api_key="secret",
            model="ocr-v1",
        ),
        client=client,
    )

    with pytest.raises(OcrProviderError) as error:
        provider.recognize(
            image=b"png-bytes",
            document_id="doc-example",
            page_number=1,
        )
    assert error.value.code == "OCR_RESPONSE_INVALID"


def test_pdf_parser_rejects_encrypted_pdf(tmp_path: Path) -> None:
    source = tmp_path / "source"
    path = source / "encrypted.pdf"
    write_pdf(path, page_count=1, encrypted=True)
    record = _record_for(source, path.name)

    with pytest.raises(PdfParseError) as error:
        parse_pdf(
            path,
            record,
            CORPUS_VERSION,
            render_directory=tmp_path / "render",
            renderer=FakeRenderer(),
            parsed_at=NOW,
        )
    assert error.value.code == "PDF_ENCRYPTED"


def test_pdf_release_writes_bounded_artifacts_and_never_overwrites(
    tmp_path: Path,
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    source, output = _release(tmp_path, monkeypatch)

    extractions, report, report_path = parse_pdf_release(
        source,
        output,
        CORPUS_VERSION,
        renderer=FakeRenderer(),
        parsed_at=NOW,
    )

    assert len(extractions) == 1
    assert report.renderedPageCount == 2
    assert report.ocrPendingPageCount == 2
    assert report.visualValidationPendingPageCount == 2
    assert report_path.is_file()
    assert report_path.read_bytes()[:3] != b"\xef\xbb\xbf"
    assert len(list((report_path.parent / "pdf-render").rglob("*.png"))) == 2
    assert str(source.resolve()) not in report_path.read_text(encoding="utf-8")

    with pytest.raises(PdfParseError) as error:
        parse_pdf_release(
            source,
            output,
            CORPUS_VERSION,
            renderer=FakeRenderer(),
            parsed_at=NOW,
        )
    assert error.value.code == "PDF_EXTRACTION_ALREADY_EXISTS"


def test_pdf_release_rejects_source_changed_after_inventory(
    tmp_path: Path,
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    source, output = _release(tmp_path, monkeypatch)
    target = source / "宣传册.pdf"
    target.write_bytes(target.read_bytes() + b"changed")

    with pytest.raises(source_inventory.RagBuildError) as error:
        parse_pdf_release(
            source,
            output,
            CORPUS_VERSION,
            renderer=FakeRenderer(),
            parsed_at=NOW,
        )
    assert error.value.code == "SOURCE_INVENTORY_MISMATCH"


def test_pdf_visual_review_verifies_every_render_and_updates_manifest(
    tmp_path: Path,
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    source, output = _release(tmp_path, monkeypatch)
    extractions, report, _ = parse_pdf_release(
        source,
        output,
        CORPUS_VERSION,
        renderer=FakeRenderer(),
        parsed_at=NOW,
    )
    original = extractions[0]

    updated, updated_report = record_pdf_visual_review(
        source,
        output,
        CORPUS_VERSION,
        original.documentId,
        original.extractionSha256,
        reviewed_at=NOW,
        notes=("Reviewed all fixture pages.",),
    )

    assert updated.visualValidation.status.value == "SUCCEEDED"
    assert updated.visualValidation.reviewedPageCount == 2
    assert "VISUAL_REVIEW_PENDING" not in updated.qualityFlags
    assert "VISUAL_REVIEW_PASSED" in updated.qualityFlags
    assert updated.extractionSha256 != original.extractionSha256
    assert updated_report.visualValidationPendingPageCount == 0
    assert updated_report.extractionManifestSha256 != report.extractionManifestSha256

    with pytest.raises(PdfParseError) as error:
        record_pdf_visual_review(
            source,
            output,
            CORPUS_VERSION,
            original.documentId,
            original.extractionSha256,
            reviewed_at=NOW,
        )
    assert error.value.code == "PDF_EXTRACTION_COMPARE_AND_SET_FAILED"


def test_pdf_visual_review_rejects_changed_rendered_page(
    tmp_path: Path,
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    source, output = _release(tmp_path, monkeypatch)
    extractions, _, _ = parse_pdf_release(
        source,
        output,
        CORPUS_VERSION,
        renderer=FakeRenderer(),
        parsed_at=NOW,
    )
    extraction = extractions[0]
    rendered_page = (
        output
        / "releases"
        / CORPUS_VERSION
        / "qa"
        / "pdf-render"
        / extraction.documentId
        / "page-001.png"
    )
    rendered_page.write_bytes(rendered_page.read_bytes() + b"changed")

    with pytest.raises(PdfParseError) as error:
        record_pdf_visual_review(
            source,
            output,
            CORPUS_VERSION,
            extraction.documentId,
            extraction.extractionSha256,
            reviewed_at=NOW,
        )
    assert error.value.code == "PDF_RENDER_ARTIFACT_CHECKSUM_MISMATCH"


def test_pdf_visual_review_rejects_local_path_in_note(
    tmp_path: Path,
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    source, output = _release(tmp_path, monkeypatch)
    extractions, _, _ = parse_pdf_release(
        source,
        output,
        CORPUS_VERSION,
        renderer=FakeRenderer(),
        parsed_at=NOW,
    )
    extraction = extractions[0]

    with pytest.raises(PdfParseError) as error:
        record_pdf_visual_review(
            source,
            output,
            CORPUS_VERSION,
            extraction.documentId,
            extraction.extractionSha256,
            reviewed_at=NOW,
            notes=("Reviewed at D:\\private\\page.png",),
        )
    assert error.value.code == "PDF_VISUAL_REVIEW_NOTE_INVALID"


def test_parse_pdf_cli_returns_safe_pending_summary(
    tmp_path: Path,
    monkeypatch: pytest.MonkeyPatch,
    capsys: pytest.CaptureFixture[str],
) -> None:
    source, output = _release(tmp_path, monkeypatch)
    monkeypatch.setattr(pdf_parser, "resolve_pdf_renderer", lambda path=None: FakeRenderer())

    assert main(
        [
            "parse-pdf",
            "--source",
            str(source),
            "--output",
            str(output),
            "--corpus-version",
            CORPUS_VERSION,
        ]
    ) == 0
    summary = json.loads(capsys.readouterr().out)

    assert summary["status"] == "REQUIRES_REVIEW"
    assert summary["renderedPageCount"] == 2
    assert summary["ocrPendingPageCount"] == 2
    assert summary["visualValidationPendingPageCount"] == 2
    assert str(source.resolve()) not in json.dumps(summary, ensure_ascii=False)
