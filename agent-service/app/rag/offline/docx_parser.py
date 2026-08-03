from __future__ import annotations

import hashlib
import json
import os
import re
import shutil
import stat
import unicodedata
import uuid
import zipfile
from collections import Counter
from datetime import datetime
from decimal import Decimal, InvalidOperation
from pathlib import Path
from typing import Any
from zoneinfo import ZoneInfo

from lxml import etree

from app.rag.config import CORPUS_ID, TIMEZONE
from app.rag.contracts import (
    BuildStatus,
    CompatibilityBranch,
    DocxBlockType,
    DocxConnector,
    DocxDocumentExtractionSummary,
    DocxExtractionContract,
    DocxExtractionMetrics,
    DocxExtractionReport,
    DocxFlowNode,
    DocxPackageInfo,
    DocxRenderedPage,
    DocxRelationshipCandidate,
    DocxTextBlock,
    DocxVisualValidation,
    ExtractedParameter,
    FlowNodeKind,
    LayoutGeometry,
    RelationshipEvidence,
    SourceFileRecord,
    SourceInventoryManifest,
    SourceType,
    VisualValidationStatus,
)
from app.rag.offline.source_inventory import (
    RagBuildError,
    load_source_inventory,
    resolve_inventory_source_file,
    validate_output_root,
)


PARSER_VERSION = "docx-ooxml/1.0.0"
MAX_PACKAGE_ENTRIES = 5_000
MAX_TOTAL_UNCOMPRESSED_BYTES = 256 * 1024 * 1024
MAX_SINGLE_ENTRY_BYTES = 64 * 1024 * 1024
MAX_COMPRESSION_RATIO = 500
MAX_RELATIONSHIP_BYTES = 5 * 1024 * 1024
EMU_PER_POINT = 12_700

NS = {
    "a": "http://schemas.openxmlformats.org/drawingml/2006/main",
    "cp": "http://schemas.openxmlformats.org/package/2006/metadata/core-properties",
    "dc": "http://purl.org/dc/elements/1.1/",
    "mc": "http://schemas.openxmlformats.org/markup-compatibility/2006",
    "pr": "http://schemas.openxmlformats.org/package/2006/relationships",
    "v": "urn:schemas-microsoft-com:vml",
    "w": "http://schemas.openxmlformats.org/wordprocessingml/2006/main",
    "wp": "http://schemas.openxmlformats.org/drawingml/2006/wordprocessingDrawing",
}

W = f"{{{NS['w']}}}"
MC = f"{{{NS['mc']}}}"

CONTROL_POINT_PATTERN = re.compile(r"(?:CCP|CPP)\s*[-－]?\s*\d+", re.IGNORECASE)
STEP_PATTERN = re.compile(r"^\s*(\d+(?:[.．]\d+)?)\s*[、,，.．:：]?\s*(.*)$", re.DOTALL)
PARAMETER_PATTERN = re.compile(
    r"(?P<first>\d+(?:\.\d+)?)"
    r"\s*(?:(?P<separator>[-–—~～至到])\s*(?P<second>\d+(?:\.\d+)?))?"
    r"\s*(?P<unit>°\s*Bx|°\s*C|℃|MPa|Mpa|kPa|Pa|mm|cm|m|分钟|小时|天|秒|"
    r"转/分|转／分|目|次|G|g|kg|克|千克)",
    re.IGNORECASE,
)
GENERIC_TITLE_TEXT = frozenset({"流程图", "工艺流程图", "流程图工艺描述", "工艺描述"})


class DocxParseError(RagBuildError):
    pass


def _safe_xml(data: bytes, label: str) -> etree._Element:
    if b"<!DOCTYPE" in data.upper() or b"<!ENTITY" in data.upper():
        raise DocxParseError("DOCX_XML_UNSAFE", f"Unsafe XML declarations are forbidden in {label}.")
    try:
        parser = etree.XMLParser(
            resolve_entities=False,
            no_network=True,
            recover=False,
            huge_tree=False,
            remove_comments=False,
        )
        return etree.fromstring(data, parser=parser)
    except etree.XMLSyntaxError as exc:
        raise DocxParseError("DOCX_XML_INVALID", f"Invalid OOXML part: {label}.") from exc


def _validate_package_entry(info: zipfile.ZipInfo) -> None:
    name = info.filename
    normalized_name = name[:-1] if info.is_dir() else name
    parts = normalized_name.replace("\\", "/").split("/")
    if (
        not normalized_name
        or "\\" in name
        or normalized_name.startswith("/")
        or ":" in parts[0]
        or any(part in {"", ".", ".."} for part in parts)
    ):
        raise DocxParseError("DOCX_PACKAGE_PATH_INVALID", "The DOCX contains an unsafe package path.")
    if info.flag_bits & 0x1:
        raise DocxParseError("DOCX_PACKAGE_ENCRYPTED", "Encrypted DOCX package entries are not supported.")
    if info.file_size > MAX_SINGLE_ENTRY_BYTES:
        raise DocxParseError("DOCX_PACKAGE_ENTRY_TOO_LARGE", "A DOCX package entry is too large.")
    if (
        info.file_size > 1024 * 1024
        and info.compress_size > 0
        and info.file_size / info.compress_size > MAX_COMPRESSION_RATIO
    ):
        raise DocxParseError(
            "DOCX_PACKAGE_COMPRESSION_RATIO_EXCEEDED",
            "A DOCX package entry has an unsafe compression ratio.",
        )


def _read_docx_package(path: Path) -> tuple[etree._Element, DocxPackageInfo, tuple[str, ...]]:
    if not zipfile.is_zipfile(path):
        raise DocxParseError("DOCX_PACKAGE_INVALID", "The source file is not a valid DOCX package.")
    try:
        with zipfile.ZipFile(path) as package:
            infos = package.infolist()
            if not infos or len(infos) > MAX_PACKAGE_ENTRIES:
                raise DocxParseError(
                    "DOCX_PACKAGE_ENTRY_COUNT_INVALID",
                    "The DOCX package entry count is outside the allowed range.",
                )
            names = [info.filename for info in infos]
            if len(names) != len(set(names)):
                raise DocxParseError(
                    "DOCX_PACKAGE_DUPLICATE_ENTRY",
                    "The DOCX contains duplicate package entries.",
                )
            for info in infos:
                _validate_package_entry(info)
            total_uncompressed = sum(info.file_size for info in infos)
            if total_uncompressed > MAX_TOTAL_UNCOMPRESSED_BYTES:
                raise DocxParseError("DOCX_PACKAGE_TOO_LARGE", "The DOCX package is too large.")
            if "word/document.xml" not in names:
                raise DocxParseError(
                    "DOCX_DOCUMENT_XML_MISSING",
                    "The DOCX package does not contain word/document.xml.",
                )
            document = _safe_xml(package.read("word/document.xml"), "word/document.xml")
            external_relationships: list[str] = []
            for info in infos:
                if not info.filename.endswith(".rels"):
                    continue
                if info.file_size > MAX_RELATIONSHIP_BYTES:
                    raise DocxParseError(
                        "DOCX_RELATIONSHIPS_TOO_LARGE",
                        "A DOCX relationships part is too large.",
                    )
                relationships = _safe_xml(package.read(info.filename), info.filename)
                for relationship in relationships.xpath(
                    "//*[local-name()='Relationship'][@TargetMode='External']"
                ):
                    relationship_type = relationship.get("Type", "")
                    external_relationships.append(relationship_type.rsplit("/", 1)[-1] or "unknown")
    except (OSError, zipfile.BadZipFile, RuntimeError) as exc:
        if isinstance(exc, DocxParseError):
            raise
        raise DocxParseError("DOCX_PACKAGE_READ_FAILED", "Unable to read the DOCX package.") from exc

    section_count = int(document.xpath("count(//w:sectPr)", namespaces=NS)) or 1
    return (
        document,
        DocxPackageInfo(
            entryCount=len(infos),
            totalUncompressedBytes=total_uncompressed,
            externalRelationshipCount=len(external_relationships),
            sectionCount=section_count,
        ),
        tuple(sorted(external_relationships)),
    )


def _normalized_text(value: str) -> str:
    value = unicodedata.normalize("NFC", value)
    value = value.replace("\u3000", " ")
    value = re.sub(r"[ \t]+", " ", value)
    value = re.sub(r" *\n *", "\n", value)
    return value.strip()


def _extract_text(node: etree._Element, *, exclude_text_boxes: bool = False) -> str:
    parts: list[str] = []

    def visit(current: etree._Element) -> None:
        if exclude_text_boxes and current.tag == f"{W}txbxContent":
            return
        if current.tag == f"{W}t":
            parts.append(current.text or "")
            return
        if current.tag == f"{W}tab":
            parts.append("\t")
            return
        if current.tag in {f"{W}br", f"{W}cr"}:
            parts.append("\n")
            return
        for child in current:
            visit(child)

    visit(node)
    return _normalized_text("".join(parts))


def _text_box_paragraphs(node: etree._Element) -> tuple[str, ...]:
    values = []
    for paragraph in node.xpath("./w:p | ./w:tbl//w:p", namespaces=NS):
        text = _extract_text(paragraph)
        if text:
            values.append(text)
    return tuple(values)


def _int_or_none(value: str | None) -> int | None:
    if value is None or not str(value).strip():
        return None
    try:
        return int(str(value).strip())
    except ValueError:
        return None


def _vml_length_to_emu(value: str | None) -> int | None:
    if value is None:
        return None
    cleaned = value.strip().lower()
    match = re.fullmatch(r"(-?\d+(?:\.\d+)?)(pt|in|cm|mm)?", cleaned)
    if not match:
        return None
    number = Decimal(match.group(1))
    unit = match.group(2) or "pt"
    factor = {
        "pt": Decimal(EMU_PER_POINT),
        "in": Decimal(914_400),
        "cm": Decimal(360_000),
        "mm": Decimal(36_000),
    }[unit]
    return int(number * factor)


def _parse_vml_style(style: str | None) -> dict[str, str]:
    if not style:
        return {}
    result: dict[str, str] = {}
    for item in style.split(";"):
        if ":" not in item:
            continue
        key, value = item.split(":", 1)
        result[key.strip().lower()] = value.strip()
    return result


def _layout_geometry(node: etree._Element) -> LayoutGeometry | None:
    anchor = node.xpath("ancestor-or-self::wp:anchor[1]", namespaces=NS)
    if anchor:
        current = anchor[0]
        extent = current.xpath("wp:extent", namespaces=NS)
        horizontal = current.xpath("wp:positionH", namespaces=NS)
        vertical = current.xpath("wp:positionV", namespaces=NS)
        return LayoutGeometry(
            xEmu=_int_or_none(
                current.xpath("string(wp:positionH/wp:posOffset)", namespaces=NS)
            ),
            yEmu=_int_or_none(
                current.xpath("string(wp:positionV/wp:posOffset)", namespaces=NS)
            ),
            widthEmu=_int_or_none(extent[0].get("cx")) if extent else None,
            heightEmu=_int_or_none(extent[0].get("cy")) if extent else None,
            horizontalRelativeFrom=horizontal[0].get("relativeFrom") if horizontal else None,
            verticalRelativeFrom=vertical[0].get("relativeFrom") if vertical else None,
        )
    vml_shape = node.xpath(
        "ancestor-or-self::*[self::v:shape or self::v:rect][1]",
        namespaces=NS,
    )
    if not vml_shape:
        return None
    style = vml_shape[0].get("style")
    values = _parse_vml_style(style)
    return LayoutGeometry(
        xEmu=_vml_length_to_emu(values.get("margin-left") or values.get("left")),
        yEmu=_vml_length_to_emu(values.get("margin-top") or values.get("top")),
        widthEmu=_vml_length_to_emu(values.get("width")),
        heightEmu=_vml_length_to_emu(values.get("height")),
        rawStyle=style,
    )


def _shape_metadata(node: etree._Element) -> tuple[str | None, str | None]:
    anchor = node.xpath("ancestor::wp:anchor[1]", namespaces=NS)
    if anchor:
        doc_properties = anchor[0].xpath("wp:docPr", namespaces=NS)
        if doc_properties:
            return doc_properties[0].get("id"), doc_properties[0].get("name")
    vml_shape = node.xpath(
        "ancestor::*[self::v:shape or self::v:rect][1]",
        namespaces=NS,
    )
    if vml_shape:
        shape_id = vml_shape[0].get("id")
        return shape_id, shape_id
    return None, None


def _control_point_labels(text: str) -> tuple[str, ...]:
    values: list[str] = []
    for match in CONTROL_POINT_PATTERN.finditer(text):
        label = re.sub(r"\s+", "", match.group(0)).upper().replace("－", "-")
        if label not in values:
            values.append(label)
    return tuple(values)


def _extracted_parameters(text: str) -> tuple[ExtractedParameter, ...]:
    parameters: list[ExtractedParameter] = []
    seen: set[tuple[str, int]] = set()
    for match in PARAMETER_PATTERN.finditer(text):
        source_text = match.group(0)
        key = (source_text, match.start())
        if key in seen:
            continue
        seen.add(key)
        start = max(0, match.start() - 60)
        end = min(len(text), match.end() + 60)
        context = _normalized_text(text[start:end]).replace("\n", " ")
        parameters.append(
            ExtractedParameter(
                sourceText=source_text,
                valueType="RANGE" if match.group("second") else "SINGLE",
                minValue=match.group("first"),
                maxValue=match.group("second"),
                unit=re.sub(r"\s+", "", match.group("unit")),
                contextText=context,
            )
        )
    return tuple(parameters)


def _compatibility_branch(node: etree._Element) -> CompatibilityBranch:
    if node.xpath("ancestor::mc:Choice", namespaces=NS):
        return CompatibilityBranch.CHOICE
    return CompatibilityBranch.DIRECT


def _document_order(root: etree._Element) -> dict[etree._Element, int]:
    return {node: index for index, node in enumerate(root.iter())}


def _build_blocks(document_id: str, root: etree._Element) -> tuple[DocxTextBlock, ...]:
    order = _document_order(root)
    candidates: list[tuple[int, dict[str, Any]]] = []

    for paragraph in root.xpath(
        "//w:body//w:p[not(ancestor::w:txbxContent) and not(ancestor::w:tbl)]",
        namespaces=NS,
    ):
        text = _extract_text(paragraph, exclude_text_boxes=True)
        if text:
            candidates.append(
                (
                    order[paragraph],
                    {
                        "blockType": DocxBlockType.PARAGRAPH,
                        "sourceBranch": CompatibilityBranch.DIRECT,
                        "text": text,
                        "paragraphs": (text,),
                    },
                )
            )

    for table in root.xpath("//w:body//w:tbl[not(ancestor::w:txbxContent)]", namespaces=NS):
        rows: list[tuple[str, ...]] = []
        for row in table.xpath("./w:tr", namespaces=NS):
            cells = []
            for cell in row.xpath("./w:tc", namespaces=NS):
                cell_text = "\n".join(
                    value
                    for paragraph in cell.xpath(".//w:p", namespaces=NS)
                    if (value := _extract_text(paragraph, exclude_text_boxes=True))
                )
                cells.append(cell_text)
            if any(cells):
                rows.append(tuple(cells))
        if rows:
            text = "\n".join(" | ".join(cell for cell in row) for row in rows)
            candidates.append(
                (
                    order[table],
                    {
                        "blockType": DocxBlockType.TABLE,
                        "sourceBranch": CompatibilityBranch.DIRECT,
                        "text": text,
                        "paragraphs": tuple(cell for row in rows for cell in row if cell),
                        "tableRows": tuple(rows),
                    },
                )
            )

    selected_text_boxes = root.xpath(
        "//w:txbxContent[not(ancestor::mc:Fallback)]",
        namespaces=NS,
    )
    for text_box in selected_text_boxes:
        paragraphs = _text_box_paragraphs(text_box)
        text = "\n".join(paragraphs) if paragraphs else _extract_text(text_box)
        if not text:
            continue
        shape_id, shape_name = _shape_metadata(text_box)
        candidates.append(
            (
                order[text_box],
                {
                    "blockType": DocxBlockType.TEXT_BOX,
                    "sourceBranch": _compatibility_branch(text_box),
                    "text": text,
                    "paragraphs": paragraphs,
                    "shapeId": shape_id,
                    "shapeName": shape_name,
                    "geometry": _layout_geometry(text_box),
                },
            )
        )

    candidates.sort(key=lambda item: item[0])
    duplicate_texts = {
        text
        for text, count in Counter(
            _normalized_text(item[1]["text"]).casefold()
            for item in candidates
            if item[1]["blockType"] == DocxBlockType.TEXT_BOX
        ).items()
        if count > 1
    }
    blocks = []
    for sequence, (_, candidate) in enumerate(candidates, start=1):
        text = candidate["text"]
        quality_flags = []
        if (
            candidate["blockType"] == DocxBlockType.TEXT_BOX
            and _normalized_text(text).casefold() in duplicate_texts
        ):
            quality_flags.append("DUPLICATE_SELECTED_TEXT")
        blocks.append(
            DocxTextBlock(
                blockId=f"{document_id}/block/{sequence:03d}",
                sequence=sequence,
                controlPointLabels=_control_point_labels(text),
                parameters=_extracted_parameters(text),
                qualityFlags=tuple(quality_flags),
                **candidate,
            )
        )
    return tuple(blocks)


def _flow_node_kind(text: str, step_no: str | None) -> FlowNodeKind:
    compact = re.sub(r"\s+", "", text)
    if len(text) > 220:
        return FlowNodeKind.PROCESS_DESCRIPTION
    if step_no:
        return FlowNodeKind.PROCESS_STEP
    if "包装材料" in compact or "内包材料" in compact or "瓦楞纸箱" in compact:
        return FlowNodeKind.PACKAGING_MATERIAL
    if "贮存" in compact or compact == "储存":
        return FlowNodeKind.STORAGE
    if "糖蜜" in compact or "回用" in compact:
        return FlowNodeKind.BYPRODUCT
    if any(keyword in compact for keyword in ("原料", "辅料", "白开水", "晶种")):
        return FlowNodeKind.MATERIAL
    return FlowNodeKind.OTHER


def _build_flow_nodes(blocks: tuple[DocxTextBlock, ...]) -> tuple[DocxFlowNode, ...]:
    nodes = []
    for block in blocks:
        if block.blockType != DocxBlockType.TEXT_BOX:
            continue
        text = block.text.replace("\n", " ")
        step_match = STEP_PATTERN.match(text)
        step_no = None
        name = text
        if len(text) <= 220 and step_match:
            step_no = step_match.group(1).replace("．", ".")
            name = step_match.group(2).strip() or text
        kind = _flow_node_kind(text, step_no)
        if kind == FlowNodeKind.PROCESS_DESCRIPTION:
            name = "工艺描述"
            step_no = None
        flags = list(block.qualityFlags)
        if len(set(block.controlPointLabels)) > 1:
            flags.append("CONTROL_POINT_LABEL_CONFLICT")
        sequence = len(nodes) + 1
        nodes.append(
            DocxFlowNode(
                nodeId=f"{block.blockId.rsplit('/', 2)[0]}/node/{sequence:03d}",
                blockId=block.blockId,
                nodeKind=kind,
                stepNo=step_no,
                name=name[:500],
                sourceText=block.text,
                controlPointLabels=block.controlPointLabels,
                parameters=block.parameters,
                geometry=block.geometry,
                qualityFlags=tuple(dict.fromkeys(flags)),
            )
        )
    return tuple(nodes)


def _modern_connectors(document_id: str, root: etree._Element) -> list[DocxConnector]:
    connectors = []
    anchors = root.xpath("//wp:anchor[not(ancestor::mc:Fallback)]", namespaces=NS)
    for anchor in anchors:
        preset = anchor.xpath("string(.//a:prstGeom/@prst)", namespaces=NS)
        if not preset or not (preset == "line" or "connector" in preset.casefold()):
            continue
        doc_properties = anchor.xpath("wp:docPr", namespaces=NS)
        start_arrow = anchor.xpath("string(.//a:headEnd/@type)", namespaces=NS) or None
        end_arrow = anchor.xpath("string(.//a:tailEnd/@type)", namespaces=NS) or None
        sequence = len(connectors) + 1
        connectors.append(
            DocxConnector(
                connectorId=f"{document_id}/connector/{sequence:03d}",
                sequence=sequence,
                presetGeometry=preset,
                shapeId=doc_properties[0].get("id") if doc_properties else None,
                shapeName=doc_properties[0].get("name") if doc_properties else None,
                geometry=_layout_geometry(anchor),
                startArrow=start_arrow,
                endArrow=end_arrow,
            )
        )
    return connectors


def _legacy_connectors(
    document_id: str,
    root: etree._Element,
    *,
    sequence_offset: int,
) -> list[DocxConnector]:
    connectors = []
    lines = root.xpath("//v:line[not(ancestor::mc:Fallback)]", namespaces=NS)
    for line in lines:
        sequence = sequence_offset + len(connectors) + 1
        stroke = line.xpath("./v:stroke", namespaces=NS)
        connectors.append(
            DocxConnector(
                connectorId=f"{document_id}/connector/{sequence:03d}",
                sequence=sequence,
                presetGeometry="line",
                shapeId=line.get("id"),
                shapeName=line.get("id"),
                geometry=LayoutGeometry(rawStyle=line.get("style")),
                rawFrom=line.get("from"),
                rawTo=line.get("to"),
                startArrow=stroke[0].get("startarrow") if stroke else None,
                endArrow=stroke[0].get("endarrow") if stroke else None,
            )
        )
    return connectors


def _build_connectors(document_id: str, root: etree._Element) -> tuple[DocxConnector, ...]:
    modern = _modern_connectors(document_id, root)
    legacy = _legacy_connectors(document_id, root, sequence_offset=len(modern))
    return tuple(modern + legacy)


def _step_decimal(step_no: str) -> Decimal | None:
    try:
        return Decimal(step_no)
    except InvalidOperation:
        return None


def _relationship_candidates(
    document_id: str,
    nodes: tuple[DocxFlowNode, ...],
) -> tuple[DocxRelationshipCandidate, ...]:
    numbered = [
        (value, node)
        for node in nodes
        if node.stepNo and (value := _step_decimal(node.stepNo)) is not None
    ]
    numbered.sort(key=lambda item: (item[0], item[1].nodeId))
    candidates = []
    for (_, predecessor), (_, successor) in zip(numbered, numbered[1:]):
        if predecessor.stepNo == successor.stepNo:
            continue
        sequence = len(candidates) + 1
        candidates.append(
            DocxRelationshipCandidate(
                candidateId=f"{document_id}/relation/{sequence:03d}",
                predecessorNodeId=predecessor.nodeId,
                successorNodeId=successor.nodeId,
                evidence=RelationshipEvidence.STEP_NUMBER_SEQUENCE,
            )
        )
    return tuple(candidates)


def _title_candidates(blocks: tuple[DocxTextBlock, ...]) -> tuple[str, ...]:
    values = []
    for block in blocks:
        if block.blockType != DocxBlockType.PARAGRAPH:
            continue
        compact = re.sub(r"\s+", "", block.text)
        if not compact or len(compact) > 100 or compact.startswith("注"):
            continue
        if block.text not in values:
            values.append(block.text)
    return tuple(values)


def _title_signature(value: str) -> set[str]:
    normalized = unicodedata.normalize("NFKC", value).casefold()
    normalized = re.sub(r"\.[a-z0-9]+$", "", normalized)
    normalized = re.sub(r"\d+(?:\.\d+)*", "", normalized)
    for token in ("工艺流程图", "流程图", "工艺描述", "分装", "生产", "制品"):
        normalized = normalized.replace(token, "")
    return {
        character
        for character in normalized
        if "\u4e00" <= character <= "\u9fff"
    }


def _has_title_conflict(source_file_name: str, candidates: tuple[str, ...]) -> bool:
    usable = [
        candidate
        for candidate in candidates
        if re.sub(r"\s+", "", candidate) not in GENERIC_TITLE_TEXT
    ]
    if not usable:
        return False
    source_signature = _title_signature(Path(source_file_name).stem)
    if not source_signature:
        return False
    scores = []
    for candidate in usable:
        candidate_signature = _title_signature(candidate)
        union = source_signature | candidate_signature
        scores.append(len(source_signature & candidate_signature) / len(union) if union else 1.0)
    return max(scores, default=1.0) < 0.2


def _stable_hash(payload: dict[str, Any]) -> str:
    canonical = json.dumps(
        payload,
        ensure_ascii=False,
        sort_keys=True,
        separators=(",", ":"),
    ).encode("utf-8")
    return hashlib.sha256(canonical).hexdigest()


def _docx_extraction_stable_payload(
    extraction: DocxExtractionContract,
) -> dict[str, Any]:
    payload = {
        "schemaVersion": extraction.schemaVersion,
        "corpusId": extraction.corpusId,
        "corpusVersion": extraction.corpusVersion,
        "documentId": extraction.documentId,
        "sourceFileName": extraction.sourceFileName,
        "sourceSha256": extraction.sourceSha256,
        "parserVersion": extraction.parserVersion,
        "titleCandidates": list(extraction.titleCandidates),
        "package": extraction.package.model_dump(mode="json"),
        "blocks": [
            block.model_dump(mode="json") for block in extraction.blocks
        ],
        "flowNodes": [
            node.model_dump(mode="json") for node in extraction.flowNodes
        ],
        "connectors": [
            connector.model_dump(mode="json")
            for connector in extraction.connectors
        ],
        "relationshipCandidates": [
            candidate.model_dump(mode="json")
            for candidate in extraction.relationshipCandidates
        ],
        "qualityFlags": list(extraction.qualityFlags),
        "metrics": extraction.metrics.model_dump(mode="json"),
    }
    if extraction.visualValidation.status != VisualValidationStatus.NOT_PERFORMED:
        payload["visualValidation"] = extraction.visualValidation.model_dump(
            mode="json"
        )
    return payload


def _sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        while chunk := stream.read(1024 * 1024):
            digest.update(chunk)
    return digest.hexdigest()


def parse_docx(
    path: Path,
    record: SourceFileRecord,
    corpus_version: str,
    *,
    parsed_at: datetime | None = None,
) -> DocxExtractionContract:
    if record.sourceType != SourceType.DOCX:
        raise DocxParseError("DOCX_SOURCE_TYPE_INVALID", "Only inventoried DOCX files may be parsed.")
    root, package_info, external_relationships = _read_docx_package(path)
    blocks = _build_blocks(record.documentId, root)
    nodes = _build_flow_nodes(blocks)
    connectors = _build_connectors(record.documentId, root)
    relationships = _relationship_candidates(record.documentId, nodes)
    titles = _title_candidates(blocks)

    raw_text_box_count = int(root.xpath("count(//w:txbxContent)", namespaces=NS))
    selected_text_box_count = sum(
        block.blockType == DocxBlockType.TEXT_BOX for block in blocks
    )
    fallback_count = int(
        root.xpath("count(//mc:Fallback//w:txbxContent)", namespaces=NS)
    )
    flags: list[str] = []
    if fallback_count:
        flags.append("COMPATIBILITY_FALLBACK_DEDUPLICATED")
    if not root.xpath("//wp:anchor", namespaces=NS) and root.xpath("//v:textbox", namespaces=NS):
        flags.append("LEGACY_VML_ONLY")
    if external_relationships:
        flags.append("EXTERNAL_RELATIONSHIP_IGNORED")
    if any("DUPLICATE_SELECTED_TEXT" in block.qualityFlags for block in blocks):
        flags.append("DUPLICATE_SELECTED_TEXT")
    if any("CONTROL_POINT_LABEL_CONFLICT" in node.qualityFlags for node in nodes):
        flags.append("CONTROL_POINT_LABEL_CONFLICT")
    if _has_title_conflict(record.sourceFileName, titles):
        flags.append("TITLE_CONFLICT")
    if connectors:
        flags.append("FLOW_RELATION_REQUIRES_REVIEW")
    if not nodes:
        flags.append("NO_FLOW_NODES")
    flags.append("VISUAL_REVIEW_PENDING")

    metrics = DocxExtractionMetrics(
        rawTextBoxCount=raw_text_box_count,
        selectedTextBoxCount=selected_text_box_count,
        fallbackDuplicateCount=fallback_count,
        paragraphBlockCount=sum(
            block.blockType == DocxBlockType.PARAGRAPH for block in blocks
        ),
        tableBlockCount=sum(block.blockType == DocxBlockType.TABLE for block in blocks),
        connectorCount=len(connectors),
        flowNodeCount=len(nodes),
        controlPointCount=sum(len(node.controlPointLabels) for node in nodes),
        parameterCount=sum(len(block.parameters) for block in blocks),
    )
    stable_payload = {
        "schemaVersion": 1,
        "corpusId": CORPUS_ID,
        "corpusVersion": corpus_version,
        "documentId": record.documentId,
        "sourceFileName": record.sourceFileName,
        "sourceSha256": record.sourceSha256,
        "parserVersion": PARSER_VERSION,
        "titleCandidates": list(titles),
        "package": package_info.model_dump(mode="json"),
        "blocks": [block.model_dump(mode="json") for block in blocks],
        "flowNodes": [node.model_dump(mode="json") for node in nodes],
        "connectors": [connector.model_dump(mode="json") for connector in connectors],
        "relationshipCandidates": [
            candidate.model_dump(mode="json") for candidate in relationships
        ],
        "qualityFlags": flags,
        "metrics": metrics.model_dump(mode="json"),
    }
    return DocxExtractionContract(
        corpusVersion=corpus_version,
        documentId=record.documentId,
        sourceFileName=record.sourceFileName,
        sourceSha256=record.sourceSha256,
        parserVersion=PARSER_VERSION,
        parsedAt=parsed_at or datetime.now(ZoneInfo(TIMEZONE)),
        status=BuildStatus.REQUIRES_REVIEW,
        titleCandidates=titles,
        package=package_info,
        blocks=blocks,
        flowNodes=nodes,
        connectors=connectors,
        relationshipCandidates=relationships,
        qualityFlags=tuple(flags),
        metrics=metrics,
        extractionSha256=_stable_hash(stable_payload),
    )


def _ensure_safe_artifact_directory(path: Path, root: Path) -> None:
    root_absolute = root.absolute()
    try:
        relative = path.absolute().relative_to(root_absolute)
    except (OSError, ValueError) as exc:
        raise DocxParseError(
            "DOCX_OUTPUT_DIRECTORY_INVALID",
            "The DOCX artifact directory is invalid.",
        ) from exc
    current = root_absolute
    for component in relative.parts:
        current /= component
        if not current.exists() and not current.is_symlink():
            raise DocxParseError(
                "DOCX_OUTPUT_DIRECTORY_INVALID",
                "The DOCX artifact directory is invalid.",
            )
        attributes = getattr(current.lstat(), "st_file_attributes", 0)
        if current.is_symlink() or attributes & getattr(
            stat,
            "FILE_ATTRIBUTE_REPARSE_POINT",
            0,
        ):
            raise DocxParseError(
                "DOCX_OUTPUT_LINK_FORBIDDEN",
                "DOCX artifact directories must not be links or reparse points.",
            )
    try:
        path.resolve(strict=True).relative_to(root.resolve(strict=True))
    except (OSError, ValueError) as exc:
        raise DocxParseError(
            "DOCX_OUTPUT_DIRECTORY_INVALID",
            "The DOCX artifact directory is invalid.",
        ) from exc


def _write_json(path: Path, payload: dict[str, Any]) -> None:
    path.write_text(
        json.dumps(payload, ensure_ascii=False, indent=2, sort_keys=True) + "\n",
        encoding="utf-8",
        newline="\n",
    )


def _batch_manifest_sha256(extractions: tuple[DocxExtractionContract, ...]) -> str:
    return _stable_hash(
        {
            "schemaVersion": 1,
            "parserVersion": PARSER_VERSION,
            "documents": [
                {
                    "documentId": item.documentId,
                    "sourceSha256": item.sourceSha256,
                    "extractionSha256": item.extractionSha256,
                }
                for item in extractions
            ],
        }
    )


def parse_docx_release(
    source_root: Path,
    output_root: Path,
    corpus_version: str,
    *,
    parsed_at: datetime | None = None,
) -> tuple[tuple[DocxExtractionContract, ...], DocxExtractionReport, Path]:
    source, output = validate_output_root(source_root, output_root)
    if not re.fullmatch(r"[a-z0-9][a-z0-9._-]{2,79}", corpus_version):
        raise DocxParseError(
            "DOCX_CORPUS_VERSION_INVALID",
            "The corpus version is invalid.",
        )
    release_root = output / "releases" / corpus_version
    inventory_path = release_root / "source-inventory.json"
    inventory: SourceInventoryManifest = load_source_inventory(inventory_path)
    if inventory.corpusVersion != corpus_version:
        raise DocxParseError(
            "DOCX_CORPUS_VERSION_MISMATCH",
            "The requested corpus version does not match the source inventory.",
        )
    records = tuple(
        record
        for record in inventory.files
        if record.sourceType == SourceType.DOCX
    )
    if not records:
        raise DocxParseError("DOCX_SOURCE_EMPTY", "The source inventory contains no DOCX files.")

    documents_root = release_root / "documents"
    qa_root = release_root / "qa"
    _ensure_safe_artifact_directory(documents_root, output)
    _ensure_safe_artifact_directory(qa_root, output)
    report_path = qa_root / "docx-extraction-report.json"
    targets = tuple(
        documents_root / f"{record.documentId}.extraction.json"
        for record in records
    )
    if report_path.exists() or any(target.exists() for target in targets):
        raise DocxParseError(
            "DOCX_EXTRACTION_ALREADY_EXISTS",
            "DOCX extraction artifacts already exist and will not be overwritten.",
        )

    effective_parsed_at = parsed_at or datetime.now(ZoneInfo(TIMEZONE))
    extractions = tuple(
        parse_docx(
            resolve_inventory_source_file(source, record),
            record,
            corpus_version,
            parsed_at=effective_parsed_at,
        )
        for record in records
    )
    summaries = tuple(
        DocxDocumentExtractionSummary(
            documentId=item.documentId,
            sourceFileName=item.sourceFileName,
            status=item.status,
            extractionSha256=item.extractionSha256,
            selectedTextBoxCount=item.metrics.selectedTextBoxCount,
            connectorCount=item.metrics.connectorCount,
            flowNodeCount=item.metrics.flowNodeCount,
            qualityFlags=item.qualityFlags,
        )
        for item in extractions
    )
    report = DocxExtractionReport(
        corpusVersion=corpus_version,
        generatedAt=effective_parsed_at,
        status=BuildStatus.REQUIRES_REVIEW,
        expectedDocxCount=len(extractions),
        succeededCount=len(extractions),
        failedCount=0,
        visualValidationPendingCount=sum(
            item.visualValidation.status.value != "SUCCEEDED"
            for item in extractions
        ),
        documents=summaries,
        extractionManifestSha256=_batch_manifest_sha256(extractions),
    )

    staging_root = release_root / f".docx-staging-{uuid.uuid4().hex}"
    try:
        staging_documents = staging_root / "documents"
        staging_qa = staging_root / "qa"
        staging_documents.mkdir(parents=True)
        staging_qa.mkdir()
        for item in extractions:
            _write_json(
                staging_documents / f"{item.documentId}.extraction.json",
                item.model_dump(mode="json"),
            )
        _write_json(
            staging_qa / report_path.name,
            report.model_dump(mode="json"),
        )
        for staged in staging_documents.iterdir():
            os.rename(staged, documents_root / staged.name)
        os.rename(staging_qa / report_path.name, report_path)
    except OSError as exc:
        raise DocxParseError(
            "DOCX_OUTPUT_WRITE_FAILED",
            "Unable to write DOCX extraction artifacts.",
        ) from exc
    finally:
        if staging_root.exists():
            shutil.rmtree(staging_root, ignore_errors=True)
    return extractions, report, report_path


def _load_docx_extraction(path: Path) -> DocxExtractionContract:
    if not path.is_file() or path.stat().st_size > 50 * 1024 * 1024:
        raise DocxParseError(
            "DOCX_EXTRACTION_NOT_FOUND",
            "DOCX extraction artifact is missing or too large.",
        )
    try:
        extraction = DocxExtractionContract.model_validate_json(
            path.read_text(encoding="utf-8")
        )
    except (OSError, UnicodeError, ValueError) as exc:
        raise DocxParseError(
            "DOCX_EXTRACTION_INVALID",
            "DOCX extraction artifact is invalid.",
        ) from exc
    actual_sha256 = _stable_hash(
        _docx_extraction_stable_payload(extraction)
    )
    if actual_sha256 != extraction.extractionSha256:
        raise DocxParseError(
            "DOCX_EXTRACTION_CHECKSUM_MISMATCH",
            "DOCX extraction checksum is invalid.",
        )
    return extraction


def _inspect_docx_rendered_page(
    path: Path,
) -> tuple[int, int, bool]:
    try:
        from PIL import Image, ImageChops

        if path.stat().st_size > 100 * 1024 * 1024:
            raise DocxParseError(
                "DOCX_RENDER_ARTIFACT_TOO_LARGE",
                "Rendered DOCX page exceeds the artifact size limit.",
            )
        with Image.open(path) as image:
            image.verify()
        with Image.open(path) as image:
            rgb = image.convert("RGB")
            width, height = rgb.size
            blank = (
                ImageChops.difference(
                    rgb,
                    Image.new("RGB", rgb.size, color="white"),
                ).getbbox()
                is None
            )
        return width, height, blank
    except DocxParseError:
        raise
    except (OSError, ValueError) as exc:
        raise DocxParseError(
            "DOCX_RENDER_ARTIFACT_INVALID",
            "Rendered DOCX page is not a valid bounded image.",
        ) from exc


def record_docx_visual_review(
    source_root: Path,
    output_root: Path,
    corpus_version: str,
    document_id: str,
    expected_extraction_sha256: str,
    *,
    renderer: str,
    renderer_version: str,
    reviewed_at: datetime | None = None,
    notes: tuple[str, ...] = (),
) -> tuple[DocxExtractionContract, DocxExtractionReport]:
    source, output = validate_output_root(source_root, output_root)
    if not re.fullmatch(r"[a-z0-9][a-z0-9._-]{2,79}", corpus_version):
        raise DocxParseError(
            "DOCX_CORPUS_VERSION_INVALID",
            "The corpus version is invalid.",
        )
    if not re.fullmatch(r"[a-z0-9][a-z0-9._/-]{2,127}", document_id):
        raise DocxParseError(
            "DOCX_DOCUMENT_ID_INVALID",
            "The DOCX document ID is invalid.",
        )
    if not re.fullmatch(r"[0-9a-f]{64}", expected_extraction_sha256):
        raise DocxParseError(
            "DOCX_EXTRACTION_CHECKSUM_INVALID",
            "Expected DOCX extraction checksum is invalid.",
        )
    if (
        not renderer
        or len(renderer) > 100
        or not renderer_version
        or len(renderer_version) > 500
        or re.search(
            r"(?:[A-Za-z]:[\\/]|\\\\|/(?:home|Users|tmp)/)",
            renderer + renderer_version,
        )
    ):
        raise DocxParseError(
            "DOCX_RENDERER_METADATA_INVALID",
            "DOCX renderer metadata is invalid.",
        )

    release_root = output / "releases" / corpus_version
    inventory = load_source_inventory(release_root / "source-inventory.json")
    records = tuple(
        record
        for record in inventory.files
        if record.sourceType == SourceType.DOCX
        and record.documentId == document_id
    )
    if len(records) != 1:
        raise DocxParseError(
            "DOCX_DOCUMENT_NOT_IN_INVENTORY",
            "The DOCX document is not uniquely present in the source inventory.",
        )
    resolve_inventory_source_file(source, records[0])

    extraction_path = (
        release_root / "documents" / f"{document_id}.extraction.json"
    )
    report_path = release_root / "qa" / "docx-extraction-report.json"
    extraction = _load_docx_extraction(extraction_path)
    if extraction.extractionSha256 != expected_extraction_sha256:
        raise DocxParseError(
            "DOCX_EXTRACTION_COMPARE_AND_SET_FAILED",
            "DOCX extraction changed after it was reviewed.",
        )
    if (
        extraction.visualValidation.status
        == VisualValidationStatus.SUCCEEDED
    ):
        raise DocxParseError(
            "DOCX_VISUAL_REVIEW_ALREADY_RECORDED",
            "DOCX visual review was already recorded.",
        )

    render_root = (
        release_root / "qa" / "docx-render" / document_id
    )
    _ensure_safe_artifact_directory(render_root, output)
    candidates: list[tuple[int, Path]] = []
    for path in render_root.iterdir():
        if not path.is_file():
            continue
        match = re.fullmatch(r"page-(\d+)\.png", path.name)
        if match:
            candidates.append((int(match.group(1)), path))
    candidates.sort(key=lambda item: item[0])
    if (
        not candidates
        or len(candidates) > 200
        or [page for page, _ in candidates]
        != list(range(1, len(candidates) + 1))
    ):
        raise DocxParseError(
            "DOCX_RENDER_PAGE_SEQUENCE_INVALID",
            "Rendered DOCX pages must be consecutive and non-empty.",
        )

    rendered_pages = []
    for page_number, artifact in candidates:
        attributes = getattr(
            artifact.lstat(),
            "st_file_attributes",
            0,
        )
        if artifact.is_symlink() or attributes & getattr(
            stat,
            "FILE_ATTRIBUTE_REPARSE_POINT",
            0,
        ):
            raise DocxParseError(
                "DOCX_OUTPUT_LINK_FORBIDDEN",
                "Rendered DOCX pages must not be links or reparse points.",
            )
        width, height, blank = _inspect_docx_rendered_page(artifact)
        rendered_pages.append(
            DocxRenderedPage(
                pageNumber=page_number,
                renderedArtifact=(
                    Path("qa")
                    / "docx-render"
                    / document_id
                    / artifact.name
                ).as_posix(),
                renderedSha256=_sha256_file(artifact),
                pixelWidth=width,
                pixelHeight=height,
                blank=blank,
            )
        )

    effective_reviewed_at = reviewed_at or datetime.now(ZoneInfo(TIMEZONE))
    review_notes = notes or (
        "All rendered pages were checked for clipping, overlap, glyph errors, table layout, and blank output.",
    )
    if any(
        not note
        or len(note) > 500
        or re.search(
            r"(?:[A-Za-z]:[\\/]|\\\\|/(?:home|Users|tmp)/)",
            note,
        )
        for note in review_notes
    ):
        raise DocxParseError(
            "DOCX_VISUAL_REVIEW_NOTE_INVALID",
            "DOCX visual review notes must be short and must not contain local paths.",
        )
    visual_validation = DocxVisualValidation(
        status=VisualValidationStatus.SUCCEEDED,
        renderer=renderer,
        rendererVersion=renderer_version,
        renderedPageCount=len(rendered_pages),
        reviewedPageCount=len(rendered_pages),
        renderedPages=tuple(rendered_pages),
        reviewedAt=effective_reviewed_at,
        notes=review_notes,
    )
    flags = tuple(
        flag
        for flag in extraction.qualityFlags
        if flag != "VISUAL_REVIEW_PENDING"
    ) + ("VISUAL_REVIEW_PASSED",)
    updated_without_hash = extraction.model_copy(
        update={
            "qualityFlags": flags,
            "visualValidation": visual_validation,
            "extractionSha256": "0" * 64,
        }
    )
    updated = updated_without_hash.model_copy(
        update={
            "extractionSha256": _stable_hash(
                _docx_extraction_stable_payload(updated_without_hash)
            )
        }
    )

    try:
        report = DocxExtractionReport.model_validate_json(
            report_path.read_text(encoding="utf-8")
        )
    except (OSError, UnicodeError, ValueError) as exc:
        raise DocxParseError(
            "DOCX_EXTRACTION_REPORT_INVALID",
            "DOCX extraction report is invalid.",
        ) from exc
    summaries = tuple(
        DocxDocumentExtractionSummary(
            documentId=updated.documentId,
            sourceFileName=updated.sourceFileName,
            status=updated.status,
            extractionSha256=updated.extractionSha256,
            selectedTextBoxCount=updated.metrics.selectedTextBoxCount,
            connectorCount=updated.metrics.connectorCount,
            flowNodeCount=updated.metrics.flowNodeCount,
            qualityFlags=updated.qualityFlags,
        )
        if summary.documentId == document_id
        else summary
        for summary in report.documents
    )
    all_extractions = tuple(
        updated
        if summary.documentId == document_id
        else _load_docx_extraction(
            release_root
            / "documents"
            / f"{summary.documentId}.extraction.json"
        )
        for summary in summaries
    )
    updated_report = report.model_copy(
        update={
            "documents": summaries,
            "visualValidationPendingCount": sum(
                item.visualValidation.status
                != VisualValidationStatus.SUCCEEDED
                for item in all_extractions
            ),
            "extractionManifestSha256": _batch_manifest_sha256(
                all_extractions
            ),
        }
    )

    staging_root = (
        release_root / f".docx-review-staging-{uuid.uuid4().hex}"
    )
    try:
        staging_root.mkdir()
        staged_extraction = staging_root / extraction_path.name
        staged_report = staging_root / report_path.name
        _write_json(
            staged_extraction,
            updated.model_dump(mode="json"),
        )
        _write_json(
            staged_report,
            updated_report.model_dump(mode="json"),
        )
        os.replace(staged_extraction, extraction_path)
        os.replace(staged_report, report_path)
    except OSError as exc:
        raise DocxParseError(
            "DOCX_VISUAL_REVIEW_WRITE_FAILED",
            "Unable to record DOCX visual review.",
        ) from exc
    finally:
        if staging_root.exists():
            shutil.rmtree(staging_root, ignore_errors=True)
    return updated, updated_report
