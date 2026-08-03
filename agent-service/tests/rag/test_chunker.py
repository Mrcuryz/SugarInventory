from __future__ import annotations

import json
from pathlib import Path

from app.rag.contracts import (
    ChunkType,
    ControlPointContract,
    DocumentContract,
    KnowledgeDomain,
    PageContract,
    PageSectionKind,
    ParameterContract,
    ProcessContract,
    ProcessStepContract,
)
from app.rag.offline.chunker import build_chunks


FIXTURES = Path(__file__).parent / "fixtures"


def _document() -> DocumentContract:
    payload = json.loads((FIXTURES / "document-v1.json").read_text(encoding="utf-8"))
    return DocumentContract.model_validate(payload)


def test_process_chunking_uses_graph_order_and_preserves_exact_control_evidence() -> None:
    control = ControlPointContract(type="CCP", label="CCP2", sourceLabel="CCP2")
    first = ProcessStepContract(
        stepId="doc-aaaaaaaaaaaaaaaaaaaaaaaa/step/1",
        stepNo="1",
        name="原料验收",
        sourceText="1 原料验收",
        normalizedText="原料验收",
        successorStepIds=("doc-aaaaaaaaaaaaaaaaaaaaaaaa/step/2",),
    )
    second = ProcessStepContract(
        stepId="doc-aaaaaaaaaaaaaaaaaaaaaaaa/step/2",
        stepNo="2",
        name="金属检测",
        sourceText="2 金属检测 CCP2，铁类Φ1.5mm",
        normalizedText="金属检测 CCP2，铁类Φ1.5mm",
        predecessorStepIds=("doc-aaaaaaaaaaaaaaaaaaaaaaaa/step/1",),
        equipment=("金属检测机",),
        parameters=(
            ParameterContract(
                name="铁类金属检测阈值",
                sourceText="Φ1.5mm",
                valueType="SINGLE",
                minValue=1.5,
                unit="mm",
                normalizationStatus="NORMALIZED",
            ),
        ),
        controlPoint=control,
        controlPoints=(control,),
    )
    document = _document().model_copy(
        update={
            "displayTitle": "白砂糖分装工艺",
            "productFamilies": ("白砂糖",),
            "process": ProcessContract(
                steps=(second, first),
                branches=(
                    {
                        "name": "包装材料验收",
                        "nodeKind": "PACKAGING_MATERIAL",
                        "sourceText": "包装材料验收",
                        "qualityFlags": [],
                    },
                ),
                controlPoints=(control,),
            ),
        }
    )

    chunks = build_chunks((document,))
    overview = next(item for item in chunks if item.chunkType == ChunkType.FLOW_OVERVIEW)
    control_chunk = next(
        item
        for item in chunks
        if item.chunkType == ChunkType.CONTROL_POINT and item.stepNo == "2"
    )

    assert "1 原料验收 → 2 金属检测" in overview.content
    assert control_chunk.chunkId.endswith("/control/2")
    assert "Φ1.5mm" in control_chunk.content
    assert control_chunk.sourceText == "2 金属检测 CCP2，铁类Φ1.5mm"
    assert control_chunk.controlPointLabels == ("CCP2",)
    assert control_chunk.embeddingRef is not None
    assert len(control_chunk.contentSha256) == 64
    assert any(item.chunkType == ChunkType.MATERIAL_BRANCH for item in chunks)


def test_gift_page_is_split_into_one_chunk_per_product_card() -> None:
    page = PageContract(
        pageNumber=12,
        title="糖罐子礼品糖",
        sectionKind=PageSectionKind.GIFT_PRODUCT,
        knowledgeDomains=(KnowledgeDomain.PRODUCT_MARKETING,),
        productFamilies=("玫瑰花黑糖", "梨汁冰糖", "姜汁红糖", "阿胶黑糖"),
        sourceText="视觉核对后的第十二页原始 OCR 文本",
        normalizedText=(
            "糖罐子礼品糖\n"
            "玫瑰花黑糖：宣传册描述为补营养、暖身体、舒情绪。\n"
            "梨汁冰糖：宣传册描述为温和补水、润喉适口。\n"
            "姜汁红糖：宣传册描述为温热驱寒、快速补能。\n"
            "阿胶黑糖：宣传册描述为温和养血、暖身补能。\n"
            "包装文案：来者上宾，天下来宾。"
        ),
    )
    document = _document().model_copy(
        update={
            "documentId": "doc-bbbbbbbbbbbbbbbbbbbbbbbb",
            "displayTitle": "企业宣传册",
            "pages": (page,),
            "process": ProcessContract(),
        }
    )

    chunks = build_chunks((document,))
    product_chunks = [item for item in chunks if item.title.endswith(("玫瑰花黑糖", "梨汁冰糖", "姜汁红糖", "阿胶黑糖"))]

    assert len(product_chunks) == 4
    assert all(item.chunkType == ChunkType.PRODUCT_SECTION for item in product_chunks)
    assert all(len(item.productFamilies) == 2 for item in product_chunks)
    assert len(chunks) == 5
