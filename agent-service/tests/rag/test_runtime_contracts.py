from __future__ import annotations

from datetime import datetime
from pathlib import Path
from zoneinfo import ZoneInfo

import pytest
from pydantic import ValidationError

from app.rag.runtime.contracts import CurrentCorpusPointer, KnowledgeSearchRequest


NOW = datetime(2026, 8, 1, 8, 0, tzinfo=ZoneInfo("Asia/Shanghai"))


def test_rag03a_sources_and_design_are_utf8_without_bom() -> None:
    repository_root = Path(__file__).resolve().parents[3]
    paths = (
        *(repository_root / "agent-service" / "app" / "rag" / "runtime").glob("*.py"),
        repository_root / "agent-service" / "README.md",
        repository_root / "docs" / "agent" / "rag" / "README.md",
        repository_root / "docs" / "agent" / "rag" / "implementation-plan.md",
        repository_root / "docs" / "agent" / "rag" / "development-log.md",
        repository_root / "docs" / "agent" / "rag" / "runtime-loader-and-internal-search-design.md",
    )
    for path in paths:
        raw = path.read_bytes()
        assert not raw.startswith(b"\xef\xbb\xbf"), path
        decoded = raw.decode("utf-8", errors="strict")
        assert "�" not in decoded, path


def test_current_pointer_requires_same_safe_release_and_corpus_version() -> None:
    with pytest.raises(ValidationError, match="releaseName must match corpusVersion"):
        CurrentCorpusPointer(
            corpusVersion="test-corpus-v1",
            releaseName="other-corpus-v1",
            corpusManifestSha256="a" * 64,
            indexManifestSha256="b" * 64,
            evaluationSetSha256="c" * 64,
            evaluationReportSha256="d" * 64,
            publishedAt=NOW,
            publishedBy="pytest",
        )


def test_current_pointer_rejects_untrusted_path_fields_and_naive_time() -> None:
    payload = {
        "corpusVersion": "test-corpus-v1",
        "releaseName": "test-corpus-v1",
        "corpusManifestSha256": "a" * 64,
        "indexManifestSha256": "b" * 64,
        "evaluationSetSha256": "c" * 64,
        "evaluationReportSha256": "d" * 64,
        "publishedAt": "2026-08-01T08:00:00",
        "publishedBy": "pytest",
        "path": "D:/secret",
    }

    with pytest.raises(ValidationError):
        CurrentCorpusPointer.model_validate(payload)


@pytest.mark.parametrize(
    "payload",
    [
        {"query": ""},
        {"query": "x" * 501},
        {"query": "白砂糖", "knowledgeDomains": ["PROCESS", "PROCESS"]},
        {"query": "白砂糖", "productQueries": ["白砂糖", "白砂糖"]},
        {"query": "白砂糖", "productQueries": ["x" * 101]},
        {"query": "白砂糖", "limit": 11},
        {"query": "白砂糖", "roleCode": "ADMIN"},
        {"query": "白砂糖", "path": "indexes/vector"},
        {"query": "白砂糖", "documentId": "doc-secret"},
    ],
)
def test_knowledge_search_request_rejects_invalid_or_untrusted_fields(payload: dict) -> None:
    with pytest.raises(ValidationError):
        KnowledgeSearchRequest.model_validate(payload)


def test_knowledge_search_request_accepts_only_controlled_filters() -> None:
    request = KnowledgeSearchRequest.model_validate(
        {
            "query": " 多晶冰糖自然结晶需要多久 ",
            "knowledgeDomains": ["PROCESS"],
            "productQueries": ["多晶冰糖"],
            "limit": 5,
        }
    )

    assert request.query == "多晶冰糖自然结晶需要多久"
    assert request.knowledgeDomains[0].value == "PROCESS"
    assert request.productQueries == ("多晶冰糖",)
