from __future__ import annotations

import json
import os
from collections import Counter
from datetime import datetime
from pathlib import Path
from zoneinfo import ZoneInfo

import pytest

from app.rag.contracts import PdfTextLayerStatus, SourceType
from app.rag.offline.pdf_parser import PdfRenderer, parse_pdf
from app.rag.offline.source_inventory import (
    build_source_inventory,
    resolve_inventory_source_file,
)


CORPUS_VERSION = "laibin-rag-2026-07-29-v1"
NOW = datetime(2026, 7, 29, 12, 0, tzinfo=ZoneInfo("Asia/Shanghai"))


def test_real_pdf_matches_structural_golden(tmp_path: Path) -> None:
    source_text = os.getenv("LAIBIN_RAG_SOURCE_DIR")
    renderer_text = os.getenv("LAIBIN_RAG_PDF_RENDERER")
    if not source_text or not renderer_text:
        pytest.skip(
            "Set LAIBIN_RAG_SOURCE_DIR and LAIBIN_RAG_PDF_RENDERER "
            "to run the real PDF golden."
        )

    golden = json.loads(
        (
            Path(__file__).parent
            / "fixtures"
            / "pdf-golden-v1.json"
        ).read_text(encoding="utf-8")
    )
    source = Path(source_text)
    inventory = build_source_inventory(source, CORPUS_VERSION, generated_at=NOW)
    records = [
        record
        for record in inventory.files
        if record.sourceType == SourceType.PDF
    ]
    assert len(records) == 1
    record = records[0]
    assert record.documentId == golden["documentId"]
    assert record.sourceFileName == golden["sourceFileName"]
    assert record.sourceSha256 == golden["sourceSha256"]

    extraction, _ = parse_pdf(
        resolve_inventory_source_file(source, record),
        record,
        CORPUS_VERSION,
        render_directory=tmp_path / "render",
        renderer=PdfRenderer(Path(renderer_text)),
        parsed_at=NOW,
    )

    assert extraction.package.pageCount == golden["pageCount"]
    assert extraction.package.title == golden["packageMetadata"]["title"]
    assert extraction.package.creator == golden["packageMetadata"]["creator"]
    assert extraction.package.producer == golden["packageMetadata"]["producer"]
    assert {
        page.pixelWidth for page in extraction.pages
    } == {golden["pixelWidth"]}
    assert {
        page.pixelHeight for page in extraction.pages
    } == {golden["pixelHeight"]}
    counts = Counter(page.textLayerStatus.value for page in extraction.pages)
    assert {
        status.value: counts[status.value]
        for status in PdfTextLayerStatus
    } == golden["textLayerStatusCounts"]
    assert [
        page.extractedCharacterCount for page in extraction.pages
    ] == golden["extractedCharacterCounts"]
    assert extraction.metrics.ocrNotConfiguredPageCount == golden["pageCount"]
    assert "OCR_PROVIDER_NOT_CONFIGURED" in extraction.qualityFlags
