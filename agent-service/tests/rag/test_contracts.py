from __future__ import annotations

import json
from datetime import datetime
from pathlib import Path
from zoneinfo import ZoneInfo

import pytest
from pydantic import ValidationError

from app.rag.contracts import (
    BuildStatus,
    CorpusManifest,
    CorpusStatus,
    DocumentContract,
    EmbeddingContract,
    IndexComponentContract,
    IndexManifest,
    ParserVersions,
    QualityIssue,
    QualityReport,
    QualitySeverity,
)


FIXTURES = Path(__file__).parent / "fixtures"
NOW = datetime(2026, 7, 29, 10, 0, tzinfo=ZoneInfo("Asia/Shanghai"))


def test_document_fixture_validates_and_serializes_as_utf8_json() -> None:
    payload = json.loads((FIXTURES / "document-v1.json").read_text(encoding="utf-8"))

    document = DocumentContract.model_validate(payload)
    serialized = document.model_dump_json()

    assert document.displayTitle == "样例工艺流程"
    assert "\\u6837" not in serialized
    assert DocumentContract.model_validate_json(serialized) == document


def test_contracts_forbid_unknown_fields_and_non_admin_roles() -> None:
    payload = json.loads((FIXTURES / "document-v1.json").read_text(encoding="utf-8"))
    payload["unexpected"] = True

    with pytest.raises(ValidationError, match="Extra inputs are not permitted"):
        DocumentContract.model_validate(payload)

    payload.pop("unexpected")
    payload["allowedRoles"] = ["ADMIN", "STAFF"]
    with pytest.raises(ValidationError, match="unsupported roles"):
        DocumentContract.model_validate(payload)


def test_corpus_index_and_quality_contracts_validate_cross_field_counts() -> None:
    corpus = CorpusManifest(
        corpusVersion="laibin-rag-2026-07-29-v1",
        status=CorpusStatus.BUILDING,
        builtAt=NOW,
        documentCount=10,
        chunkCount=0,
        embedding=EmbeddingContract(),
        parserVersions=ParserVersions(),
    )
    component = IndexComponentContract(
        status=BuildStatus.NOT_STARTED,
        implementation="not-built",
        version="1",
    )
    index = IndexManifest(
        corpusVersion=corpus.corpusVersion,
        builtAt=NOW,
        documentCount=10,
        chunkCount=0,
        lexical=component,
        vector=component,
    )
    issue = QualityIssue(
        code="MISSING_OWNER",
        severity=QualitySeverity.WARNING,
        message="当前未提供文档负责人，按既定决策保留扩展字段。",
    )
    report = QualityReport(
        corpusVersion=corpus.corpusVersion,
        generatedAt=NOW,
        status=BuildStatus.REQUIRES_REVIEW,
        documentCount=10,
        blockingIssueCount=0,
        warningIssueCount=1,
        issues=(issue,),
    )

    assert index.documentCount == report.documentCount == corpus.documentCount
    assert report.warningIssueCount == 1

    with pytest.raises(ValidationError, match="quality issue counts"):
        report.model_copy(update={"warningIssueCount": 0}).model_validate(
            report.model_copy(update={"warningIssueCount": 0}).model_dump()
        )


def test_contract_datetimes_must_include_timezone() -> None:
    with pytest.raises(ValidationError, match="timezone"):
        CorpusManifest(
            corpusVersion="laibin-rag-2026-07-29-v1",
            status=CorpusStatus.BUILDING,
            builtAt=datetime(2026, 7, 29, 10, 0),
            documentCount=0,
            chunkCount=0,
        )
