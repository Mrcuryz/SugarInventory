from __future__ import annotations

import json
import os
from pathlib import Path

import pytest

from app.rag.contracts import CorpusManifest, DocumentContract, QualityReport
from app.rag.offline.normalizer import _document_manifest_sha256, _with_document_hash


FIXTURES = Path(__file__).parent / "fixtures"


def _release_root() -> Path:
    configured = os.getenv("LAIBIN_RAG_NORMALIZED_RELEASE")
    if not configured:
        pytest.skip("LAIBIN_RAG_NORMALIZED_RELEASE is not configured")
    root = Path(configured)
    if not root.is_dir():
        pytest.fail("LAIBIN_RAG_NORMALIZED_RELEASE does not exist")
    return root


def _load_json(path: Path) -> dict[str, object]:
    return json.loads(path.read_text(encoding="utf-8"))


def test_real_normalized_release_matches_reviewed_golden_baseline() -> None:
    root = _release_root()
    golden = _load_json(FIXTURES / "normalization-golden-v1.json")
    manifest = CorpusManifest.model_validate(_load_json(root / "corpus-manifest.json"))
    quality = QualityReport.model_validate(_load_json(root / "qa" / "quality-report.json"))
    documents = tuple(
        DocumentContract.model_validate(_load_json(path))
        for path in sorted((root / "documents").glob("*.document.json"))
    )

    assert manifest.corpusVersion == golden["corpusVersion"]
    assert manifest.inventorySha256 == golden["inventorySha256"]
    assert manifest.reviewProfileSha256 == golden["reviewProfileSha256"]
    assert manifest.documentManifestSha256 == golden["documentManifestSha256"]
    assert len(documents) == golden["documentCount"]
    assert sum(len(document.pages) for document in documents) == golden["pageCount"]
    assert sum(
        page.sectionKind and page.sectionKind.value == "SOURCE_BLANK_PAGE"
        for document in documents
        for page in document.pages
    ) == golden["sourceBlankPageCount"]
    assert sum(len(document.process.steps) for document in documents) == golden["processStepCount"]
    assert sum(
        len(step.parameters)
        for document in documents
        for step in document.process.steps
    ) == golden["parameterCount"]
    assert sum(
        len(step.controlPoints)
        for document in documents
        for step in document.process.steps
    ) == golden["controlPointCount"]
    assert sum(
        len(step.equipment)
        for document in documents
        for step in document.process.steps
    ) == golden["equipmentValueCount"]
    assert quality.blockingIssueCount == golden["blockingIssueCount"]
    assert quality.warningIssueCount == golden["warningIssueCount"]

    assert all(_with_document_hash(document).documentSha256 == document.documentSha256 for document in documents)
    assert _document_manifest_sha256(documents) == manifest.documentManifestSha256
    assert all(document.allowedRoles == ("ADMIN", "SUPER_ADMIN") for document in documents)

    by_id = {document.documentId: document for document in documents}
    white_sugar_step = next(
        step
        for step in by_id["doc-161898e85c006f9f13db2f01"].process.steps
        if step.stepNo == "6"
    )
    assert [item.label for item in white_sugar_step.controlPoints] == ["CCP3", "CCP1"]
    assert white_sugar_step.equipment == ("金属探测器", "SA-990")
    assert [item.sourceText for item in white_sugar_step.parameters] == [
        "Φ1.5mm",
        "Φ2.0mm",
        "Φ2.5mm",
    ]

    brochure = by_id["doc-94170623c8ba7c517d863337"]
    page_three = brochure.pages[2]
    page_fifteen = brochure.pages[14]
    assert "0772-3269058（销售）" in page_three.normalizedText
    assert "OCR_LOW_CONFIDENCE" in page_fifteen.qualityFlags
    assert "证书编号" not in page_fifteen.normalizedText
    assert "FSSC22000" in page_fifteen.normalizedText
