from __future__ import annotations

import json
import os
from pathlib import Path

import pytest

from app.rag.contracts import DocxBlockType
from app.rag.offline.docx_parser import parse_docx
from app.rag.offline.source_inventory import build_source_inventory


FIXTURE = Path(__file__).parent / "fixtures" / "docx-golden-v1.json"


def test_real_client_docx_corpus_matches_frozen_structural_golden() -> None:
    source_value = os.getenv("LAIBIN_RAG_SOURCE_DIR")
    if not source_value:
        pytest.skip("LAIBIN_RAG_SOURCE_DIR is not configured")
    source = Path(source_value).resolve()
    golden = json.loads(FIXTURE.read_text(encoding="utf-8"))
    inventory = build_source_inventory(source, golden["corpusVersion"])
    records = {
        record.sourceFileName: record
        for record in inventory.files
        if record.sourceType.value == "DOCX"
    }

    assert set(records) == set(golden["documents"])
    for file_name, expected in golden["documents"].items():
        record = records[file_name]
        extraction = parse_docx(
            source / Path(record.relativePath),
            record,
            golden["corpusVersion"],
        )
        assert extraction.metrics.selectedTextBoxCount == expected["selectedTextBoxCount"]
        assert extraction.metrics.fallbackDuplicateCount == expected["fallbackDuplicateCount"]
        assert extraction.metrics.connectorCount == expected["connectorCount"]

        extracted_texts = {
            block.text.replace("\n", " ")
            for block in extraction.blocks
            if block.blockType == DocxBlockType.TEXT_BOX
        }
        assert set(expected["requiredTexts"]).issubset(extracted_texts)

        control_points = {
            label
            for node in extraction.flowNodes
            for label in node.controlPointLabels
        }
        assert set(expected["requiredControlPoints"]).issubset(control_points)

        parameters = {
            parameter.sourceText
            for block in extraction.blocks
            for parameter in block.parameters
        }
        assert set(expected["requiredParameters"]).issubset(parameters)
        assert set(expected.get("requiredQualityFlags", [])).issubset(
            extraction.qualityFlags
        )
        assert set(expected.get("forbiddenQualityFlags", [])).isdisjoint(
            extraction.qualityFlags
        )
