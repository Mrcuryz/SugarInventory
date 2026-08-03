from __future__ import annotations

import json
from datetime import datetime
from pathlib import Path
from zoneinfo import ZoneInfo

import pytest
from PIL import Image

from app.rag.contracts import (
    BuildStatus,
    DocxBlockType,
    FlowNodeKind,
    VisualValidationStatus,
)
from app.rag.offline import source_inventory
from app.rag.offline.cli import main
from app.rag.offline.docx_parser import (
    DocxParseError,
    parse_docx,
    parse_docx_release,
    record_docx_visual_review,
)
from app.rag.offline.source_inventory import (
    build_source_inventory,
    write_inventory_release,
)
from tests.rag.docx_fixture import write_legacy_docx, write_modern_docx


NOW = datetime(2026, 7, 29, 12, 0, tzinfo=ZoneInfo("Asia/Shanghai"))
CORPUS_VERSION = "laibin-rag-2026-07-29-v1"


def _record_for(source: Path, file_name: str):
    inventory = build_source_inventory(source, CORPUS_VERSION, generated_at=NOW)
    return next(item for item in inventory.files if item.sourceFileName == file_name)


def _release(
    tmp_path: Path,
    monkeypatch: pytest.MonkeyPatch,
    *,
    modern_kwargs: dict[str, object] | None = None,
) -> tuple[Path, Path]:
    source = tmp_path / "source"
    output = tmp_path / "ignored-artifacts"
    write_modern_docx(source / "现代流程.docx", **(modern_kwargs or {}))
    write_legacy_docx(source / "旧版流程.docx")
    monkeypatch.setattr(source_inventory, "PROJECT_ROOT", tmp_path)
    monkeypatch.setattr(source_inventory, "is_git_ignored", lambda path: True)
    write_inventory_release(source, output, CORPUS_VERSION, generated_at=NOW)
    return source, output


def test_modern_parser_selects_choice_once_and_preserves_business_values(tmp_path: Path) -> None:
    source = tmp_path / "source"
    path = source / "现代流程.docx"
    write_modern_docx(path, external_relationship=True)
    record = _record_for(source, path.name)

    extraction = parse_docx(path, record, CORPUS_VERSION, parsed_at=NOW)

    assert extraction.status == BuildStatus.REQUIRES_REVIEW
    assert extraction.metrics.rawTextBoxCount == 4
    assert extraction.metrics.selectedTextBoxCount == 2
    assert extraction.metrics.fallbackDuplicateCount == 2
    assert extraction.metrics.connectorCount == 1
    assert extraction.metrics.flowNodeCount == 2
    assert extraction.metrics.controlPointCount == 1
    assert extraction.titleCandidates == ("样例工艺流程图",)
    text_blocks = [
        block for block in extraction.blocks if block.blockType == DocxBlockType.TEXT_BOX
    ]
    assert [block.text for block in text_blocks] == [
        "1、原料验收（CCP1）",
        "2、浓缩，温度112-120℃，时间30-60分钟。",
    ]
    assert {parameter.sourceText for block in extraction.blocks for parameter in block.parameters} >= {
        "112-120℃",
        "30-60分钟",
        "0.4-0.8Mpa",
    }
    assert extraction.flowNodes[0].controlPointLabels == ("CCP1",)
    assert extraction.relationshipCandidates[0].predecessorNodeId == extraction.flowNodes[0].nodeId
    assert extraction.relationshipCandidates[0].successorNodeId == extraction.flowNodes[1].nodeId
    assert extraction.connectors[0].fromNodeId is None
    assert "EXTERNAL_RELATIONSHIP_IGNORED" in extraction.qualityFlags
    assert "FLOW_RELATION_REQUIRES_REVIEW" in extraction.qualityFlags
    assert "VISUAL_REVIEW_PENDING" in extraction.qualityFlags
    assert str(source.resolve()) not in extraction.model_dump_json()


def test_legacy_vml_parser_extracts_geometry_and_connector(tmp_path: Path) -> None:
    source = tmp_path / "source"
    path = source / "旧版流程.docx"
    write_legacy_docx(path)
    record = _record_for(source, path.name)

    extraction = parse_docx(path, record, CORPUS_VERSION, parsed_at=NOW)

    assert extraction.metrics.selectedTextBoxCount == 2
    assert extraction.metrics.fallbackDuplicateCount == 0
    assert extraction.metrics.connectorCount == 1
    assert "LEGACY_VML_ONLY" in extraction.qualityFlags
    assert extraction.flowNodes[0].geometry.xEmu == 10 * 12700
    assert extraction.flowNodes[1].nodeKind == FlowNodeKind.PROCESS_STEP
    assert extraction.connectors[0].rawFrom == "10pt,35pt"
    assert extraction.connectors[0].endArrow == "block"


@pytest.mark.parametrize(
    ("modern_kwargs", "error_code"),
    [
        ({"unsafe_entry": "../outside.xml"}, "DOCX_PACKAGE_PATH_INVALID"),
        (
            {"high_ratio_entry": True},
            "DOCX_PACKAGE_COMPRESSION_RATIO_EXCEEDED",
        ),
    ],
)
def test_parser_rejects_unsafe_docx_packages(
    tmp_path: Path,
    modern_kwargs: dict[str, object],
    error_code: str,
) -> None:
    source = tmp_path / "source"
    path = source / "unsafe.docx"
    write_modern_docx(path, **modern_kwargs)
    record = _record_for(source, path.name)

    with pytest.raises(DocxParseError) as error:
        parse_docx(path, record, CORPUS_VERSION, parsed_at=NOW)
    assert error.value.code == error_code


def test_release_parser_verifies_inventory_and_never_overwrites(
    tmp_path: Path,
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    source, output = _release(tmp_path, monkeypatch)

    extractions, report, report_path = parse_docx_release(
        source,
        output,
        CORPUS_VERSION,
        parsed_at=NOW,
    )

    assert len(extractions) == 2
    assert report.succeededCount == 2
    assert report.failedCount == 0
    assert report.visualValidationPendingCount == 2
    assert report_path.is_file()
    assert report_path.read_bytes()[:3] != b"\xef\xbb\xbf"
    assert len(list((report_path.parent.parent / "documents").glob("*.extraction.json"))) == 2
    assert str(source.resolve()) not in report_path.read_text(encoding="utf-8")

    with pytest.raises(DocxParseError) as error:
        parse_docx_release(source, output, CORPUS_VERSION, parsed_at=NOW)
    assert error.value.code == "DOCX_EXTRACTION_ALREADY_EXISTS"


def test_release_parser_rejects_source_changed_after_inventory(
    tmp_path: Path,
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    source, output = _release(tmp_path, monkeypatch)
    target = source / "现代流程.docx"
    target.write_bytes(target.read_bytes() + b"changed")

    with pytest.raises(source_inventory.RagBuildError) as error:
        parse_docx_release(source, output, CORPUS_VERSION, parsed_at=NOW)
    assert error.value.code == "SOURCE_INVENTORY_MISMATCH"
    assert not list((output / "releases" / CORPUS_VERSION / "documents").glob("*.json"))


def test_record_docx_visual_review_binds_rendered_pages_and_uses_compare_and_set(
    tmp_path: Path,
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    source, output = _release(tmp_path, monkeypatch)
    extractions, _, _ = parse_docx_release(
        source,
        output,
        CORPUS_VERSION,
        parsed_at=NOW,
    )
    extraction = next(
        item for item in extractions if item.sourceFileName == "现代流程.docx"
    )
    render_root = (
        output
        / "releases"
        / CORPUS_VERSION
        / "qa"
        / "docx-render"
        / extraction.documentId
    )
    render_root.mkdir(parents=True)
    first_page = Image.new("RGB", (80, 60), "white")
    first_page.putpixel((10, 10), (0, 0, 0))
    first_page.save(render_root / "page-1.png")
    Image.new("RGB", (80, 60), "white").save(render_root / "page-2.png")

    updated, report = record_docx_visual_review(
        source,
        output,
        CORPUS_VERSION,
        extraction.documentId,
        extraction.extractionSha256,
        renderer="LibreOffice",
        renderer_version="26.2.5.2",
        reviewed_at=NOW,
        notes=("Both rendered pages were inspected.",),
    )

    assert updated.visualValidation.status == VisualValidationStatus.SUCCEEDED
    assert updated.visualValidation.reviewedPageCount == 2
    assert [page.blank for page in updated.visualValidation.renderedPages] == [
        False,
        True,
    ]
    assert all(
        len(page.renderedSha256) == 64
        for page in updated.visualValidation.renderedPages
    )
    assert "VISUAL_REVIEW_PENDING" not in updated.qualityFlags
    assert "VISUAL_REVIEW_PASSED" in updated.qualityFlags
    assert report.visualValidationPendingCount == 1

    with pytest.raises(DocxParseError) as error:
        record_docx_visual_review(
            source,
            output,
            CORPUS_VERSION,
            extraction.documentId,
            extraction.extractionSha256,
            renderer="LibreOffice",
            renderer_version="26.2.5.2",
            reviewed_at=NOW,
        )
    assert error.value.code == "DOCX_EXTRACTION_COMPARE_AND_SET_FAILED"


def test_parse_docx_cli_returns_safe_review_pending_summary(
    tmp_path: Path,
    monkeypatch: pytest.MonkeyPatch,
    capsys: pytest.CaptureFixture[str],
) -> None:
    source, output = _release(tmp_path, monkeypatch)

    assert main(
        [
            "parse-docx",
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
    assert summary["documentCount"] == 2
    assert summary["visualValidationPendingCount"] == 2
    assert summary["artifact"].endswith("/qa/docx-extraction-report.json")
    assert str(source.resolve()) not in json.dumps(summary, ensure_ascii=False)


def test_record_docx_visual_review_cli_returns_safe_summary(
    tmp_path: Path,
    monkeypatch: pytest.MonkeyPatch,
    capsys: pytest.CaptureFixture[str],
) -> None:
    source, output = _release(tmp_path, monkeypatch)
    extractions, _, _ = parse_docx_release(
        source,
        output,
        CORPUS_VERSION,
        parsed_at=NOW,
    )
    extraction = next(
        item for item in extractions if item.sourceFileName == "旧版流程.docx"
    )
    render_root = (
        output
        / "releases"
        / CORPUS_VERSION
        / "qa"
        / "docx-render"
        / extraction.documentId
    )
    render_root.mkdir(parents=True)
    page = Image.new("RGB", (64, 64), "white")
    page.putpixel((5, 5), (0, 0, 0))
    page.save(render_root / "page-1.png")

    assert main(
        [
            "record-docx-visual-review",
            "--source",
            str(source),
            "--output",
            str(output),
            "--corpus-version",
            CORPUS_VERSION,
            "--document-id",
            extraction.documentId,
            "--expected-extraction-sha256",
            extraction.extractionSha256,
            "--renderer",
            "LibreOffice",
            "--renderer-version",
            "26.2.5.2",
            "--note",
            "The rendered page was inspected.",
        ]
    ) == 0
    summary = json.loads(capsys.readouterr().out)

    assert summary["visualValidationStatus"] == "SUCCEEDED"
    assert summary["reviewedPageCount"] == 1
    assert summary["visualValidationPendingCount"] == 1
    assert len(summary["extractionSha256"]) == 64
    assert str(source.resolve()) not in json.dumps(summary, ensure_ascii=False)


def test_parse_docx_cli_rejects_version_path_traversal(
    tmp_path: Path,
    monkeypatch: pytest.MonkeyPatch,
    capsys: pytest.CaptureFixture[str],
) -> None:
    source, output = _release(tmp_path, monkeypatch)

    assert main(
        [
            "parse-docx",
            "--source",
            str(source),
            "--output",
            str(output),
            "--corpus-version",
            "../escape",
        ]
    ) == 2
    failure = json.loads(capsys.readouterr().err)
    assert failure["errorCode"] == "DOCX_CORPUS_VERSION_INVALID"
