from __future__ import annotations

import json
from datetime import datetime, timedelta
from pathlib import Path
from types import SimpleNamespace

import pytest
from pydantic import ValidationError

from app.rag.contracts import (
    DocumentContract,
    DocumentReviewDecision,
    DocumentType,
    KnowledgeDomain,
    RelationshipReviewDecision,
)
from app.rag.offline.normalizer import (
    _control_points,
    _detail_text_by_step,
    _document_manifest_sha256,
    _extract_parameters,
    _with_document_hash,
    compute_review_profile_sha256,
)


FIXTURES = Path(__file__).parent / "fixtures"


def test_review_profile_hash_ignores_only_its_checksum_field() -> None:
    payload = {
        "corpusVersion": "laibin-rag-2026-07-29-v1",
        "decisions": [{"documentId": "doc-aaaaaaaaaaaaaaaaaaaaaaaa"}],
        "reviewProfileSha256": "0" * 64,
    }

    checksum = compute_review_profile_sha256(payload)
    payload["reviewProfileSha256"] = "f" * 64
    assert compute_review_profile_sha256(payload) == checksum

    payload["decisions"][0]["documentId"] = "doc-bbbbbbbbbbbbbbbbbbbbbbbb"
    assert compute_review_profile_sha256(payload) != checksum


def test_step_detail_mapping_uses_multi_paragraph_description_but_not_table() -> None:
    extraction = SimpleNamespace(
        blocks=(
            SimpleNamespace(
                blockType=SimpleNamespace(value="TEXT_BOX"),
                paragraphs=("2、糖度40-55°Bx", "3、温度60-90℃"),
            ),
            SimpleNamespace(
                blockType=SimpleNamespace(value="TABLE"),
                paragraphs=("1", "金属控制", "Φ1.5mm"),
            ),
        )
    )

    details = _detail_text_by_step(extraction, {"1", "2", "3"})

    assert details["1"] == []
    assert details["2"] == ["2、糖度40-55°Bx"]
    assert details["3"] == ["3、温度60-90℃"]


def test_parameter_and_control_point_normalization_preserves_reviewed_source() -> None:
    text = (
        "真空度0.06～0.09Mpa，温度60-90℃，汽压0.4-0.8Mpa；"
        "压力温度126～140°C；筛网6到8目；金属控制CCP3（CCP1），调味CPP1。"
    )

    parameters = _extract_parameters(text)
    controls = _control_points(text)

    assert [item.sourceText for item in parameters] == [
        "0.06～0.09Mpa",
        "60-90℃",
        "0.4-0.8Mpa",
        "126～140°C",
        "6到8目",
    ]
    assert parameters[0].name == "真空度"
    assert parameters[1].name == "温度"
    assert parameters[3].name == "压力温度（原文）"
    assert parameters[3].qualityFlags == ("PARAMETER_LABEL_POSSIBLE_TYPO",)
    assert [(item.type, item.label) for item in controls] == [
        ("CCP", "CCP3"),
        ("CCP", "CCP1"),
        ("CPP", "CPP1"),
    ]


def test_review_decision_rejects_two_relationship_approval_modes() -> None:
    with pytest.raises(ValidationError, match="mutually exclusive"):
        DocumentReviewDecision(
            documentId="doc-aaaaaaaaaaaaaaaaaaaaaaaa",
            sourceSha256="a" * 64,
            sourceExtractionSha256="b" * 64,
            displayTitle="样例",
            documentType=DocumentType.PROCESS_FLOW,
            knowledgeDomain=KnowledgeDomain.PROCESS,
            approveAllExtractedRelationships=True,
            approvedRelationships=(
                RelationshipReviewDecision(
                    predecessorNodeId="doc-a/node/001",
                    successorNodeId="doc-a/node/002",
                ),
            ),
        )


def test_document_hash_excludes_processing_timestamp() -> None:
    payload = json.loads((FIXTURES / "document-v1.json").read_text(encoding="utf-8"))
    document = DocumentContract.model_validate(payload)
    first = _with_document_hash(document)
    later = document.model_copy(
        update={
            "extraction": document.extraction.model_copy(
                update={
                    "processedAt": document.extraction.processedAt
                    + timedelta(hours=1)
                }
            )
        }
    )

    assert _with_document_hash(later).documentSha256 == first.documentSha256
    assert first.extraction.processedAt == datetime.fromisoformat(
        "2026-07-29T10:00:00+08:00"
    )

    second_payload = dict(payload)
    second_payload["documentId"] = "doc-bbbbbbbbbbbbbbbbbbbbbbbb"
    second_payload["sourceFileName"] = "另一样例.docx"
    second = _with_document_hash(DocumentContract.model_validate(second_payload))
    assert _document_manifest_sha256((first, second)) == _document_manifest_sha256(
        (second, first)
    )
