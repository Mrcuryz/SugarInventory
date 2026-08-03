from __future__ import annotations

import hashlib
import json
import os
import re
import shutil
import uuid
from datetime import datetime
from pathlib import Path, PurePosixPath
from typing import Any, Iterable
from zoneinfo import ZoneInfo

from pydantic import ValidationError

from app.rag.config import TIMEZONE
from app.rag.contracts import (
    BuildStatus,
    ControlPointContract,
    CorpusManifest,
    CorpusStatus,
    DocumentContract,
    DocumentReviewDecision,
    DocumentStatus,
    ExtractionContract,
    FlowNodeKind,
    NormalizationReport,
    NormalizationReviewProfile,
    NormalizedDocumentSummary,
    PageContract,
    PageSectionKind,
    ParameterContract,
    ParserVersions,
    ProcessContract,
    ProcessStepContract,
    QualityIssue,
    QualityReport,
    QualitySeverity,
    SourceFileRecord,
    SourceType,
    VisualValidationStatus,
)
from app.rag.offline.docx_parser import _load_docx_extraction
from app.rag.offline.pdf_parser import _load_pdf_extraction
from app.rag.offline.source_inventory import (
    RagBuildError,
    load_source_inventory,
    resolve_inventory_source_file,
    validate_output_root,
)


NORMALIZER_VERSION = "rag-normalizer/1.0.0"
_STEP_PREFIX = re.compile(
    r"^\s*(?P<step>\d+(?:\.\d+)?)(?:-\d+)?\s*[、:：.．]?\s*(?P<body>.*)$"
)
_PARAMETER_RANGE = re.compile(
    r"(?P<source>(?P<min>\d+(?:\.\d+)?)\s*(?:-|~|～|—|至|到)\s*"
    r"(?P<max>\d+(?:\.\d+)?)\s*(?P<unit>°Bx|℃|°C|MPa|Mpa|转/分|目|天|小时|分钟|mm|g|G))",
    re.IGNORECASE,
)
_PARAMETER_SINGLE = re.compile(
    r"(?P<source>(?:Φ|φ)?(?P<value>\d+(?:\.\d+)?)\s*"
    r"(?P<unit>°Bx|℃|°C|MPa|Mpa|转/分|目|天|小时|分钟|mm|g|G))",
    re.IGNORECASE,
)
_CONTROL_POINT = re.compile(r"(CCP\d+|CPP\d+)", re.IGNORECASE)


def _canonical_hash(payload: Any) -> str:
    canonical = json.dumps(
        payload,
        ensure_ascii=False,
        sort_keys=True,
        separators=(",", ":"),
    ).encode("utf-8")
    return hashlib.sha256(canonical).hexdigest()


def _sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def compute_review_profile_sha256(profile: dict[str, Any]) -> str:
    stable = dict(profile)
    stable.pop("reviewProfileSha256", None)
    return _canonical_hash(stable)


def _load_review_profile(path: Path) -> NormalizationReviewProfile:
    if not path.is_file() or path.stat().st_size > 10 * 1024 * 1024:
        raise RagBuildError(
            "NORMALIZATION_PROFILE_NOT_FOUND",
            "The normalization review profile is missing or too large.",
        )
    try:
        payload = json.loads(path.read_text(encoding="utf-8"))
        profile = NormalizationReviewProfile.model_validate(payload)
    except (OSError, UnicodeError, json.JSONDecodeError, ValidationError) as exc:
        raise RagBuildError(
            "NORMALIZATION_PROFILE_INVALID",
            "The normalization review profile is invalid.",
        ) from exc
    if compute_review_profile_sha256(payload) != profile.reviewProfileSha256:
        raise RagBuildError(
            "NORMALIZATION_PROFILE_CHECKSUM_MISMATCH",
            "The normalization review profile checksum is invalid.",
        )
    return profile


def _normalize_text(value: str) -> str:
    lines = []
    for raw_line in value.replace("\r\n", "\n").replace("\r", "\n").split("\n"):
        line = re.sub(r"[\t\u00a0 ]+", " ", raw_line).strip()
        if line and (not lines or line != lines[-1]):
            lines.append(line)
    return "\n".join(lines)


def _verify_relative_artifact(
    release_root: Path,
    relative_path: str,
    expected_sha256: str,
) -> None:
    relative = PurePosixPath(relative_path)
    if relative.is_absolute() or ".." in relative.parts or not relative.parts:
        raise RagBuildError(
            "NORMALIZATION_RENDER_ARTIFACT_INVALID",
            "A reviewed render artifact path is invalid.",
        )
    artifact = release_root.joinpath(*relative.parts)
    if not artifact.is_file():
        raise RagBuildError(
            "NORMALIZATION_RENDER_ARTIFACT_MISSING",
            "A reviewed render artifact is missing.",
        )
    try:
        artifact.resolve(strict=True).relative_to(release_root.resolve(strict=True))
    except (OSError, ValueError) as exc:
        raise RagBuildError(
            "NORMALIZATION_RENDER_ARTIFACT_INVALID",
            "A reviewed render artifact escaped the release root.",
        ) from exc
    if _sha256_file(artifact) != expected_sha256:
        raise RagBuildError(
            "NORMALIZATION_RENDER_ARTIFACT_CHANGED",
            "A reviewed render artifact changed after visual review.",
        )


def _detail_text_by_step(extraction: Any, step_numbers: set[str]) -> dict[str, list[str]]:
    details: dict[str, list[str]] = {step_no: [] for step_no in step_numbers}
    for block in extraction.blocks:
        paragraphs = list(block.paragraphs)
        if len(paragraphs) <= 1 or block.blockType.value == "TABLE":
            continue
        current_step: str | None = None
        for paragraph in paragraphs:
            for line in paragraph.splitlines():
                cleaned = _normalize_text(line)
                if not cleaned or cleaned == "工艺描述":
                    continue
                match = _STEP_PREFIX.match(cleaned)
                if match and match.group("step") in step_numbers:
                    current_step = match.group("step")
                if current_step and cleaned not in details[current_step]:
                    details[current_step].append(cleaned)
    return details


def _append_table_evidence(
    extraction: Any,
    decision: DocumentReviewDecision,
    details: dict[str, list[str]],
) -> dict[str, list[str]]:
    mappings = {item.processName: item.stepNo for item in decision.tableStepMappings}
    matched: set[str] = set()
    equipment: dict[str, list[str]] = {step_no: [] for step_no in details}
    for block in extraction.blocks:
        for row in block.tableRows:
            cells = tuple(_normalize_text(cell) for cell in row if _normalize_text(cell))
            if not cells:
                continue
            for process_name, step_no in mappings.items():
                if process_name in cells:
                    evidence = "；".join(cells)
                    if evidence not in details[step_no]:
                        details[step_no].append(evidence)
                    process_index = cells.index(process_name)
                    if process_index + 1 < len(cells):
                        raw_equipment = cells[process_index + 1]
                        if raw_equipment != "/":
                            for item in raw_equipment.splitlines():
                                normalized = _normalize_text(item)
                                if normalized and normalized not in equipment[step_no]:
                                    equipment[step_no].append(normalized)
                    matched.add(process_name)
    missing = set(mappings).difference(matched)
    if missing:
        raise RagBuildError(
            "NORMALIZATION_TABLE_MAPPING_UNRESOLVED",
            "A reviewed table-to-step mapping no longer matches the extraction.",
        )
    return equipment


def _parameter_name(context: str, unit: str) -> str:
    if "糖度" in context or unit.lower() == "°bx":
        return "糖度"
    if unit in {"℃", "°C"}:
        return "压力温度（原文）" if "压力温度" in context else "温度"
    if unit in {"天", "小时", "分钟"}:
        return "时间"
    if "真空" in context:
        return "真空度"
    if "汽压" in context or "压力" in context or unit.lower() == "mpa":
        return "压力"
    if "转" in unit or "转速" in context:
        return "转速"
    if "筛" in context or unit == "目":
        return "筛目"
    if "铁类" in context and "非铁" not in context:
        return "铁类金属检测阈值"
    if "非铁" in context:
        return "非铁类金属检测阈值"
    if "不锈钢" in context:
        return "不锈钢检测阈值"
    if unit.lower() == "mm":
        return "金属检测阈值"
    if "温" in context:
        return "温度"
    if unit.lower() == "g":
        return "质量"
    return "原文参数"


def _extract_parameters(source_text: str) -> tuple[ParameterContract, ...]:
    matches: list[tuple[int, int, str, float, float | None, str, str]] = []
    occupied: list[tuple[int, int]] = []
    for match in _PARAMETER_RANGE.finditer(source_text):
        occupied.append(match.span())
        matches.append(
            (
                match.start(),
                match.end(),
                match.group("source"),
                float(match.group("min")),
                float(match.group("max")),
                match.group("unit"),
                source_text[max(0, match.start() - 24) : match.start()],
            )
        )
    for match in _PARAMETER_SINGLE.finditer(source_text):
        if any(start <= match.start() < end for start, end in occupied):
            continue
        matches.append(
            (
                match.start(),
                match.end(),
                match.group("source"),
                float(match.group("value")),
                None,
                match.group("unit"),
                source_text[max(0, match.start() - 24) : match.start()],
            )
        )
    parameters = []
    seen: set[tuple[str, str]] = set()
    for _, _, source, minimum, maximum, raw_unit, context in sorted(matches):
        unit = {"°C": "℃", "Mpa": "MPa", "G": "g"}.get(raw_unit, raw_unit)
        key = (source, context)
        if key in seen:
            continue
        seen.add(key)
        parameters.append(
            ParameterContract(
                name=_parameter_name(context, raw_unit),
                sourceText=source,
                valueType="RANGE" if maximum is not None else "SINGLE",
                minValue=minimum,
                maxValue=maximum,
                unit=unit,
                normalizationStatus="NORMALIZED_FROM_REVIEWED_SOURCE",
                qualityFlags=(
                    ("PARAMETER_LABEL_POSSIBLE_TYPO",)
                    if raw_unit in {"℃", "°C"} and "压力温度" in context
                    else ()
                ),
            )
        )
    return tuple(parameters)


def _control_points(source_text: str) -> tuple[ControlPointContract, ...]:
    labels = tuple(dict.fromkeys(match.upper() for match in _CONTROL_POINT.findall(source_text)))
    return tuple(
        ControlPointContract(type=label[:3], label=label, sourceLabel=label)
        for label in labels
    )


def _build_docx_document(
    record: SourceFileRecord,
    extraction: Any,
    decision: DocumentReviewDecision,
    review_profile_sha256: str,
    processed_at: datetime,
) -> DocumentContract:
    numbered_nodes = tuple(node for node in extraction.flowNodes if node.stepNo)
    step_numbers = {node.stepNo for node in numbered_nodes}
    details = _detail_text_by_step(extraction, step_numbers)
    equipment_by_step = _append_table_evidence(extraction, decision, details)

    candidates = {
        (item.predecessorNodeId, item.successorNodeId)
        for item in extraction.relationshipCandidates
    }
    relationships = (
        candidates
        if decision.approveAllExtractedRelationships
        else {
            (item.predecessorNodeId, item.successorNodeId)
            for item in decision.approvedRelationships
        }
    )
    if not relationships.issubset(candidates):
        raise RagBuildError(
            "NORMALIZATION_RELATIONSHIP_INVALID",
            "A reviewed relationship is no longer present in the extraction.",
        )
    node_by_id = {node.nodeId: node for node in numbered_nodes}
    if any(left not in node_by_id or right not in node_by_id for left, right in relationships):
        raise RagBuildError(
            "NORMALIZATION_RELATIONSHIP_NODE_INVALID",
            "A reviewed relationship references a non-step node.",
        )

    steps = []
    all_control_points: list[ControlPointContract] = []
    for node in numbered_nodes:
        source_parts = [node.sourceText, *details[node.stepNo]]
        source_text = _normalize_text("\n".join(dict.fromkeys(source_parts)))
        controls = _control_points(source_text)
        all_control_points.extend(item for item in controls if item not in all_control_points)
        step_flags = tuple(
            flag
            for flag in decision.qualityFlags
            if flag == "CONTROL_POINT_LABEL_CONFLICT" and len(controls) > 1
        )
        steps.append(
            ProcessStepContract(
                stepId=node.nodeId,
                stepNo=node.stepNo,
                name=node.name,
                sourceText=source_text,
                normalizedText=source_text,
                predecessorStepIds=tuple(
                    left for left, right in sorted(relationships) if right == node.nodeId
                ),
                successorStepIds=tuple(
                    right for left, right in sorted(relationships) if left == node.nodeId
                ),
                equipment=tuple(equipment_by_step[node.stepNo]),
                parameters=_extract_parameters(source_text),
                controlPoint=controls[0] if len(controls) == 1 else None,
                controlPoints=controls,
                pageNumber=1,
                sourceRegion={"blockId": node.blockId, "nodeId": node.nodeId},
                qualityFlags=step_flags,
            )
        )

    branches = tuple(
        {
            "nodeId": node.nodeId,
            "name": node.name,
            "sourceText": node.sourceText,
            "nodeKind": node.nodeKind.value,
            "qualityFlags": list(node.qualityFlags),
        }
        for node in extraction.flowNodes
        if not node.stepNo and node.nodeKind != FlowNodeKind.PROCESS_DESCRIPTION
    )
    pages = []
    overview = "\n".join(f"{step.stepNo} {step.name}" for step in steps)
    for rendered_page in extraction.visualValidation.renderedPages:
        pages.append(
            PageContract(
                pageNumber=rendered_page.pageNumber,
                title=decision.displayTitle if rendered_page.pageNumber == 1 else None,
                sourceText=overview if rendered_page.pageNumber == 1 else "",
                normalizedText=overview if rendered_page.pageNumber == 1 else "",
                sectionKind=(
                    PageSectionKind.PROCESS_FLOW
                    if not rendered_page.blank
                    else PageSectionKind.SOURCE_BLANK_PAGE
                ),
                knowledgeDomains=(decision.knowledgeDomain,),
                productFamilies=decision.productFamilies,
                reviewNotes=("该页经 LibreOffice 渲染后人工逐页复核。",),
                qualityFlags=("SOURCE_BLANK_PAGE",) if rendered_page.blank else (),
            )
        )

    document = DocumentContract(
        documentId=record.documentId,
        displayTitle=decision.displayTitle,
        sourceInternalTitle=decision.sourceInternalTitle,
        sourceFileName=record.sourceFileName,
        sourceType=record.sourceType,
        documentType=decision.documentType,
        knowledgeDomain=decision.knowledgeDomain,
        status=DocumentStatus.ACTIVE,
        versionLabel=decision.versionLabel,
        sourceSha256=record.sourceSha256,
        productFamilies=decision.productFamilies,
        qualityFlags=decision.qualityFlags,
        pages=tuple(pages),
        process=ProcessContract(
            steps=tuple(steps),
            branches=branches,
            controlPoints=tuple(all_control_points),
        ),
        extraction=ExtractionContract(
            parserVersion=extraction.parserVersion,
            processedAt=processed_at,
            status=BuildStatus.SUCCEEDED,
            sourceExtractionSha256=extraction.extractionSha256,
            reviewProfileSha256=review_profile_sha256,
            warnings=decision.qualityFlags,
        ),
        documentSha256="0" * 64,
    )
    return _with_document_hash(document)


def _build_pdf_document(
    record: SourceFileRecord,
    extraction: Any,
    decision: DocumentReviewDecision,
    review_profile_sha256: str,
    processed_at: datetime,
) -> DocumentContract:
    if len(decision.pageReviews) != len(extraction.pages):
        raise RagBuildError(
            "NORMALIZATION_PAGE_REVIEW_INCOMPLETE",
            "Every PDF page must have a review decision.",
        )
    reviews = {item.pageNumber: item for item in decision.pageReviews}
    if sorted(reviews) != list(range(1, len(extraction.pages) + 1)):
        raise RagBuildError(
            "NORMALIZATION_PAGE_REVIEW_INCOMPLETE",
            "PDF page review decisions must be complete and consecutive.",
        )
    pages = []
    for source_page in extraction.pages:
        review = reviews[source_page.pageNumber]
        source_text = source_page.ocrText or source_page.extractedText
        pages.append(
            PageContract(
                pageNumber=source_page.pageNumber,
                title=review.title,
                sourceText=source_text,
                normalizedText=review.normalizedText,
                sectionKind=review.sectionKind,
                knowledgeDomains=review.knowledgeDomains,
                productFamilies=review.productFamilies,
                excludedSourceTexts=review.excludedSourceTexts,
                reviewNotes=review.reviewNotes,
                qualityFlags=review.qualityFlags,
            )
        )
    document = DocumentContract(
        documentId=record.documentId,
        displayTitle=decision.displayTitle,
        sourceInternalTitle=decision.sourceInternalTitle,
        sourceFileName=record.sourceFileName,
        sourceType=record.sourceType,
        documentType=decision.documentType,
        knowledgeDomain=decision.knowledgeDomain,
        status=DocumentStatus.ACTIVE,
        versionLabel=decision.versionLabel,
        sourceSha256=record.sourceSha256,
        productFamilies=decision.productFamilies,
        qualityFlags=decision.qualityFlags,
        pages=tuple(pages),
        extraction=ExtractionContract(
            parserVersion=extraction.parserVersion,
            processedAt=processed_at,
            status=BuildStatus.SUCCEEDED,
            sourceExtractionSha256=extraction.extractionSha256,
            reviewProfileSha256=review_profile_sha256,
            warnings=decision.qualityFlags,
        ),
        documentSha256="0" * 64,
    )
    return _with_document_hash(document)


def _with_document_hash(document: DocumentContract) -> DocumentContract:
    payload = document.model_dump(mode="json")
    payload.pop("documentSha256")
    payload["extraction"].pop("processedAt")
    return document.model_copy(update={"documentSha256": _canonical_hash(payload)})


def _document_manifest_sha256(documents: Iterable[DocumentContract]) -> str:
    ordered_documents = sorted(documents, key=lambda item: item.documentId)
    return _canonical_hash(
        {
            "schemaVersion": 1,
            "normalizerVersion": NORMALIZER_VERSION,
            "documents": [
                {
                    "documentId": document.documentId,
                    "sourceSha256": document.sourceSha256,
                    "sourceExtractionSha256": document.extraction.sourceExtractionSha256,
                    "documentSha256": document.documentSha256,
                }
                for document in ordered_documents
            ],
        }
    )


def _write_json(path: Path, payload: dict[str, Any]) -> None:
    path.write_text(
        json.dumps(payload, ensure_ascii=False, indent=2, sort_keys=True) + "\n",
        encoding="utf-8",
        newline="\n",
    )


def _quality_issues(documents: tuple[DocumentContract, ...]) -> tuple[QualityIssue, ...]:
    messages = {
        "TITLE_CONFLICT": "文件名与文档内部标题不一致；已按复核配置保留两者。",
        "POSSIBLE_SCOPE_AMBIGUITY": "源材料的产品适用范围不明确；未进行推断扩展。",
        "CONTROL_POINT_LABEL_CONFLICT": "同一步骤出现多个 CCP 标签；已原样保留且未代为裁决。",
        "POSSIBLE_TYPO": "源材料包含疑似错字或重复；原文保留，未静默改写。",
        "OCR_LOW_CONFIDENCE": "局部 OCR 置信度较低；低置信证书缩略图内容未作为知识事实。",
    }
    return tuple(
        QualityIssue(
            code=flag,
            severity=QualitySeverity.WARNING,
            message=messages.get(flag, "已复核并保留的源材料质量提示。"),
            documentId=document.documentId,
        )
        for document in documents
        for flag in document.qualityFlags
    )


def normalize_release(
    source_root: Path,
    output_root: Path,
    corpus_version: str,
    review_profile_path: Path,
    *,
    processed_at: datetime | None = None,
) -> tuple[tuple[DocumentContract, ...], NormalizationReport, CorpusManifest]:
    source, output = validate_output_root(source_root, output_root)
    release_root = output / "releases" / corpus_version
    inventory = load_source_inventory(release_root / "source-inventory.json")
    profile = _load_review_profile(review_profile_path)
    if profile.corpusVersion != corpus_version or profile.corpusId != inventory.corpusId:
        raise RagBuildError(
            "NORMALIZATION_PROFILE_VERSION_MISMATCH",
            "The review profile does not match the release.",
        )
    if profile.inventorySha256 != inventory.inventorySha256:
        raise RagBuildError(
            "NORMALIZATION_INVENTORY_CHANGED",
            "The source inventory changed after content review.",
        )
    decisions = {decision.documentId: decision for decision in profile.decisions}
    inventory_ids = {record.documentId for record in inventory.files}
    if set(decisions) != inventory_ids:
        raise RagBuildError(
            "NORMALIZATION_REVIEW_INCOMPLETE",
            "Every inventoried document must have exactly one review decision.",
        )

    documents_root = release_root / "documents"
    qa_root = release_root / "qa"
    targets = tuple(documents_root / f"{record.documentId}.document.json" for record in inventory.files)
    fixed_targets = (
        qa_root / "normalization-report.json",
        qa_root / "quality-report.json",
        qa_root / "normalization-review-profile.json",
        release_root / "corpus-manifest.json",
    )
    if any(path.exists() for path in (*targets, *fixed_targets)):
        raise RagBuildError(
            "NORMALIZATION_ARTIFACT_ALREADY_EXISTS",
            "Normalization artifacts already exist and will not be overwritten.",
        )

    effective_time = processed_at or datetime.now(ZoneInfo(TIMEZONE))
    documents_list = []
    docx_versions: set[str] = set()
    pdf_versions: set[str] = set()
    for record in inventory.files:
        resolve_inventory_source_file(source, record)
        decision = decisions[record.documentId]
        if decision.sourceSha256 != record.sourceSha256:
            raise RagBuildError(
                "NORMALIZATION_SOURCE_CHANGED",
                "A reviewed source file changed after review.",
            )
        if record.sourceType == SourceType.DOCX:
            extraction = _load_docx_extraction(
                documents_root / f"{record.documentId}.extraction.json"
            )
            docx_versions.add(extraction.parserVersion)
            if extraction.visualValidation.status != VisualValidationStatus.SUCCEEDED:
                raise RagBuildError(
                    "NORMALIZATION_VISUAL_REVIEW_INCOMPLETE",
                    "DOCX visual review must succeed before normalization.",
                )
            for page in extraction.visualValidation.renderedPages:
                _verify_relative_artifact(release_root, page.renderedArtifact, page.renderedSha256)
            builder = _build_docx_document
        else:
            extraction = _load_pdf_extraction(
                documents_root / f"{record.documentId}.pdf-extraction.json"
            )
            pdf_versions.add(extraction.parserVersion)
            if extraction.visualValidation.status != VisualValidationStatus.SUCCEEDED:
                raise RagBuildError(
                    "NORMALIZATION_VISUAL_REVIEW_INCOMPLETE",
                    "PDF visual review must succeed before normalization.",
                )
            for page in extraction.pages:
                _verify_relative_artifact(release_root, page.renderedArtifact, page.renderedSha256)
            builder = _build_pdf_document
        if extraction.sourceSha256 != record.sourceSha256:
            raise RagBuildError(
                "NORMALIZATION_EXTRACTION_SOURCE_MISMATCH",
                "An extraction does not match its inventoried source file.",
            )
        if extraction.extractionSha256 != decision.sourceExtractionSha256:
            raise RagBuildError(
                "NORMALIZATION_EXTRACTION_CHANGED",
                "An extraction changed after content review.",
            )
        documents_list.append(
            builder(record, extraction, decision, profile.reviewProfileSha256, effective_time)
        )

    documents = tuple(documents_list)
    document_manifest_sha256 = _document_manifest_sha256(documents)
    summaries = tuple(
        NormalizedDocumentSummary(
            documentId=document.documentId,
            sourceFileName=document.sourceFileName,
            sourceExtractionSha256=document.extraction.sourceExtractionSha256,
            documentSha256=document.documentSha256,
            pageCount=len(document.pages),
            processStepCount=len(document.process.steps),
            qualityFlags=document.qualityFlags,
        )
        for document in documents
    )
    report = NormalizationReport(
        corpusVersion=corpus_version,
        generatedAt=effective_time,
        status=BuildStatus.SUCCEEDED,
        inventorySha256=inventory.inventorySha256,
        reviewProfileSha256=profile.reviewProfileSha256,
        expectedDocumentCount=len(documents),
        succeededCount=len(documents),
        failedCount=0,
        documents=summaries,
        documentManifestSha256=document_manifest_sha256,
    )
    issues = _quality_issues(documents)
    quality_report = QualityReport(
        corpusVersion=corpus_version,
        generatedAt=effective_time,
        status=BuildStatus.SUCCEEDED,
        documentCount=len(documents),
        blockingIssueCount=0,
        warningIssueCount=len(issues),
        issues=issues,
    )
    manifest = CorpusManifest(
        corpusVersion=corpus_version,
        status=CorpusStatus.VALIDATED,
        builtAt=effective_time,
        documentCount=len(documents),
        chunkCount=0,
        inventorySha256=inventory.inventorySha256,
        reviewProfileSha256=profile.reviewProfileSha256,
        documentManifestSha256=document_manifest_sha256,
        parserVersions=ParserVersions(
            docx=",".join(sorted(docx_versions)),
            pdf=",".join(sorted(pdf_versions)),
            normalizer=NORMALIZER_VERSION,
        ),
    )

    staging_root = release_root / f".normalization-staging-{uuid.uuid4().hex}"
    try:
        staging_documents = staging_root / "documents"
        staging_qa = staging_root / "qa"
        staging_documents.mkdir(parents=True)
        staging_qa.mkdir()
        for document in documents:
            _write_json(
                staging_documents / f"{document.documentId}.document.json",
                document.model_dump(mode="json"),
            )
        _write_json(staging_qa / "normalization-report.json", report.model_dump(mode="json"))
        _write_json(staging_qa / "quality-report.json", quality_report.model_dump(mode="json"))
        _write_json(
            staging_qa / "normalization-review-profile.json",
            profile.model_dump(mode="json"),
        )
        _write_json(staging_root / "corpus-manifest.json", manifest.model_dump(mode="json"))
        for staged in staging_documents.iterdir():
            os.rename(staged, documents_root / staged.name)
        for staged in staging_qa.iterdir():
            os.rename(staged, qa_root / staged.name)
        os.rename(staging_root / "corpus-manifest.json", release_root / "corpus-manifest.json")
    except OSError as exc:
        raise RagBuildError(
            "NORMALIZATION_OUTPUT_WRITE_FAILED",
            "Unable to write normalization artifacts.",
        ) from exc
    finally:
        if staging_root.exists():
            shutil.rmtree(staging_root, ignore_errors=True)
    return documents, report, manifest
