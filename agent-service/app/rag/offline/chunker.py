from __future__ import annotations

import hashlib
import json
import re
import shutil
import uuid
from collections import Counter
from datetime import datetime
from pathlib import Path
from typing import Any, Iterable
from zoneinfo import ZoneInfo

from pydantic import ValidationError

from app.rag.config import TIMEZONE
from app.rag.contracts import (
    BuildStatus,
    ChunkContract,
    ChunkManifest,
    ChunkSummary,
    ChunkType,
    ChunkingReport,
    CitationContract,
    CorpusManifest,
    CorpusStatus,
    DocumentContract,
    KnowledgeDomain,
    PageContract,
    PageSectionKind,
    ParserVersions,
    ProcessStepContract,
)
from app.rag.offline.normalizer import _document_manifest_sha256, _with_document_hash
from app.rag.offline.source_inventory import (
    RagBuildError,
    load_source_inventory,
    validate_output_root,
)


CHUNKER_VERSION = "rag-chunker/1.0.0"
_CONTROL_TERM = re.compile(r"\b(?:CCP|CPP)\d+\b", re.IGNORECASE)
_PARAMETER_TERM = re.compile(
    r"(?:\d+(?:\.\d+)?\s*(?:[-~～—至到]\s*\d+(?:\.\d+)?)?\s*"
    r"(?:°Bx|℃|°C|MPa|Mpa|转/分|目|天|小时|分钟|mm|g))",
    re.IGNORECASE,
)


def _canonical_hash(payload: Any) -> str:
    encoded = json.dumps(
        payload,
        ensure_ascii=False,
        sort_keys=True,
        separators=(",", ":"),
    ).encode("utf-8")
    return hashlib.sha256(encoded).hexdigest()


def _write_json(path: Path, payload: dict[str, Any]) -> None:
    path.write_text(
        json.dumps(payload, ensure_ascii=False, indent=2, sort_keys=True) + "\n",
        encoding="utf-8",
        newline="\n",
    )


def _write_jsonl(path: Path, records: Iterable[dict[str, Any]]) -> None:
    with path.open("w", encoding="utf-8", newline="\n") as handle:
        for record in records:
            handle.write(
                json.dumps(record, ensure_ascii=False, sort_keys=True, separators=(",", ":"))
                + "\n"
            )


def _deduplicate(values: Iterable[str]) -> tuple[str, ...]:
    return tuple(dict.fromkeys(value.strip() for value in values if value.strip()))


def _load_json_model(path: Path, model_type: type[Any], code: str) -> Any:
    try:
        payload = json.loads(path.read_text(encoding="utf-8"))
        return model_type.model_validate(payload)
    except (OSError, UnicodeError, json.JSONDecodeError, ValidationError) as exc:
        raise RagBuildError(code, f"Invalid RAG artifact: {path.name}") from exc


def _load_normalized_input(
    release_root: Path,
    corpus_version: str,
) -> tuple[CorpusManifest, tuple[DocumentContract, ...]]:
    manifest = _load_json_model(
        release_root / "corpus-manifest.json",
        CorpusManifest,
        "CHUNK_INPUT_MANIFEST_INVALID",
    )
    if manifest.corpusVersion != corpus_version or manifest.status != CorpusStatus.VALIDATED:
        raise RagBuildError(
            "CHUNK_INPUT_NOT_VALIDATED",
            "Chunking requires the matching VALIDATED normalization release.",
        )
    if not all(
        (
            manifest.inventorySha256,
            manifest.reviewProfileSha256,
            manifest.documentManifestSha256,
        )
    ):
        raise RagBuildError(
            "CHUNK_INPUT_HASH_MISSING",
            "The normalization release is missing a frozen input checksum.",
        )
    if manifest.chunkCount != 0 or manifest.chunkManifestSha256:
        raise RagBuildError(
            "CHUNK_INPUT_ALREADY_CHUNKED",
            "The input must be the immutable pre-chunk normalization release.",
        )
    inventory = load_source_inventory(release_root / "source-inventory.json")
    if inventory.inventorySha256 != manifest.inventorySha256:
        raise RagBuildError(
            "CHUNK_INPUT_INVENTORY_MISMATCH",
            "The source inventory does not match the corpus manifest.",
        )

    documents = []
    document_paths = sorted((release_root / "documents").glob("*.document.json"))
    for path in document_paths:
        document = _load_json_model(path, DocumentContract, "CHUNK_DOCUMENT_INVALID")
        if _with_document_hash(document).documentSha256 != document.documentSha256:
            raise RagBuildError(
                "CHUNK_DOCUMENT_CHECKSUM_MISMATCH",
                f"A normalized document checksum is invalid: {document.documentId}",
            )
        documents.append(document)
    result = tuple(sorted(documents, key=lambda item: item.documentId))
    if len(result) != manifest.documentCount:
        raise RagBuildError(
            "CHUNK_DOCUMENT_COUNT_MISMATCH",
            "The normalized document count does not match the corpus manifest.",
        )
    if _document_manifest_sha256(result) != manifest.documentManifestSha256:
        raise RagBuildError(
            "CHUNK_DOCUMENT_MANIFEST_MISMATCH",
            "The normalized document manifest checksum is invalid.",
        )
    return manifest, result


def _step_order(steps: tuple[ProcessStepContract, ...]) -> tuple[ProcessStepContract, ...]:
    by_id = {step.stepId: step for step in steps}
    indegree = {
        step.stepId: sum(predecessor in by_id for predecessor in step.predecessorStepIds)
        for step in steps
    }

    def sort_key(step_id: str) -> tuple[tuple[int, ...], str]:
        step_no = by_id[step_id].stepNo
        numeric = tuple(int(part) for part in re.findall(r"\d+", step_no)) or (10**9,)
        return numeric, step_id

    pending = sorted((step_id for step_id, degree in indegree.items() if degree == 0), key=sort_key)
    ordered: list[ProcessStepContract] = []
    while pending:
        step_id = pending.pop(0)
        ordered.append(by_id[step_id])
        for successor in by_id[step_id].successorStepIds:
            if successor not in indegree:
                continue
            indegree[successor] -= 1
            if indegree[successor] == 0:
                pending.append(successor)
                pending.sort(key=sort_key)
    if len(ordered) != len(steps):
        raise RagBuildError(
            "CHUNK_PROCESS_GRAPH_INVALID",
            "A normalized process graph contains a cycle or unresolved relationship.",
        )
    return tuple(ordered)


def _chunk_hash_payload(chunk: ChunkContract) -> dict[str, Any]:
    payload = chunk.model_dump(mode="json")
    payload.pop("contentSha256", None)
    payload.pop("embeddingRef", None)
    return payload


def _with_chunk_hash(chunk: ChunkContract) -> ChunkContract:
    return chunk.model_copy(update={"contentSha256": _canonical_hash(_chunk_hash_payload(chunk))})


def _base_chunk(
    document: DocumentContract,
    *,
    chunk_id: str,
    chunk_type: ChunkType,
    title: str,
    content: str,
    source_text: str,
    knowledge_domain: KnowledgeDomain | None = None,
    page_number: int | None = None,
    step_no: str | None = None,
    control_labels: tuple[str, ...] = (),
    product_families: tuple[str, ...] | None = None,
    keywords: Iterable[str] = (),
    quality_flags: Iterable[str] = (),
) -> ChunkContract:
    source_text = source_text.strip()
    if not source_text:
        raise RagBuildError("CHUNK_SOURCE_TEXT_EMPTY", f"Chunk has no source evidence: {chunk_id}")
    families = product_families if product_families is not None else document.productFamilies
    chunk = ChunkContract(
        chunkId=chunk_id,
        documentId=document.documentId,
        chunkType=chunk_type,
        knowledgeDomain=knowledge_domain or document.knowledgeDomain,
        title=title,
        content=content.strip(),
        sourceText=source_text,
        productFamilies=families,
        keywords=_deduplicate((*families, *keywords, *_CONTROL_TERM.findall(content))),
        pageNumber=page_number,
        stepNo=step_no,
        controlPointLabels=control_labels,
        qualityFlags=_deduplicate((*document.qualityFlags, *quality_flags)),
        status=document.status,
        allowedRoles=document.allowedRoles,
        citation=CitationContract(
            documentTitle=document.displayTitle,
            pageNumber=page_number,
            sectionLabel=title,
        ),
        sourceSha256=document.sourceSha256,
        contentSha256="0" * 64,
    )
    return _with_chunk_hash(chunk)


def _parameter_lines(step: ProcessStepContract) -> tuple[str, ...]:
    return tuple(
        f"{parameter.name}：{parameter.sourceText}"
        for parameter in step.parameters
    )


def _process_chunks(document: DocumentContract) -> list[ChunkContract]:
    steps = _step_order(document.process.steps)
    if not steps:
        return []
    sequence = " → ".join(f"{step.stepNo} {step.name}" for step in steps)
    overview_source = "\n".join(step.sourceText for step in steps)
    chunks = [
        _base_chunk(
            document,
            chunk_id=f"{document.documentId}/flow",
            chunk_type=ChunkType.FLOW_OVERVIEW,
            title=f"{document.displayTitle}：流程总览",
            content=f"产品/工艺：{document.displayTitle}\n流程顺序：{sequence}",
            source_text=overview_source,
            keywords=("工艺流程", "流程顺序", *(step.name for step in steps)),
        )
    ]
    for step in steps:
        control_labels = _deduplicate(point.label for point in step.controlPoints)
        parameter_lines = _parameter_lines(step)
        equipment_line = "、".join(step.equipment)
        content_lines = [
            f"产品/工艺：{document.displayTitle}",
            f"步骤 {step.stepNo}：{step.name}",
            f"工艺说明：{step.normalizedText}",
        ]
        if equipment_line:
            content_lines.append(f"设备：{equipment_line}")
        if parameter_lines:
            content_lines.append("参数：" + "；".join(parameter_lines))
        if control_labels:
            content_lines.append("控制点：" + "、".join(control_labels))
        step_content = "\n".join(content_lines)
        common_keywords = (
            step.name,
            step.stepNo,
            *step.equipment,
            *(parameter.name for parameter in step.parameters),
            *(parameter.sourceText for parameter in step.parameters),
            *control_labels,
            *_PARAMETER_TERM.findall(step.sourceText),
        )
        chunks.append(
            _base_chunk(
                document,
                chunk_id=f"{document.documentId}/step/{step.stepNo}",
                chunk_type=ChunkType.PROCESS_STEP,
                title=f"{document.displayTitle}：步骤 {step.stepNo} {step.name}",
                content=step_content,
                source_text=step.sourceText,
                page_number=step.pageNumber,
                step_no=step.stepNo,
                control_labels=control_labels,
                keywords=common_keywords,
                quality_flags=step.qualityFlags,
            )
        )
        if step.parameters or control_labels or step.equipment:
            evidence = []
            if control_labels:
                evidence.append("控制点：" + "、".join(control_labels))
            if parameter_lines:
                evidence.append("参数：" + "；".join(parameter_lines))
            if equipment_line:
                evidence.append("设备：" + equipment_line)
            chunks.append(
                _base_chunk(
                    document,
                    chunk_id=f"{document.documentId}/control/{step.stepNo}",
                    chunk_type=ChunkType.CONTROL_POINT,
                    title=f"{document.displayTitle}：步骤 {step.stepNo} 控制参数",
                    content=(
                        f"产品/工艺：{document.displayTitle}\n"
                        f"步骤 {step.stepNo}：{step.name}\n"
                        f"工艺说明：{step.normalizedText}\n" + "\n".join(evidence)
                    ),
                    source_text=step.sourceText,
                    page_number=step.pageNumber,
                    step_no=step.stepNo,
                    control_labels=control_labels,
                    keywords=common_keywords,
                    quality_flags=step.qualityFlags,
                )
            )
    for ordinal, branch in enumerate(document.process.branches, start=1):
        name = str(branch.get("name") or branch.get("sourceText") or "物料分支").strip()
        source_text = str(branch.get("sourceText") or name).strip()
        node_kind = str(branch.get("nodeKind") or "MATERIAL")
        chunks.append(
            _base_chunk(
                document,
                chunk_id=f"{document.documentId}/branch/{ordinal:03d}",
                chunk_type=ChunkType.MATERIAL_BRANCH,
                title=f"{document.displayTitle}：物料分支 {name}",
                content=(
                    f"产品/工艺：{document.displayTitle}\n"
                    f"物料分支：{name}\n分支类型：{node_kind}"
                ),
                source_text=source_text,
                keywords=(name, node_kind),
                quality_flags=tuple(branch.get("qualityFlags") or ()),
            )
        )
    return chunks


def _page_chunk_type(page: PageContract) -> tuple[ChunkType, KnowledgeDomain]:
    if page.sectionKind in {PageSectionKind.PRODUCT_INTRODUCTION, PageSectionKind.GIFT_PRODUCT}:
        return ChunkType.PRODUCT_SECTION, KnowledgeDomain.PRODUCT_MARKETING
    if page.sectionKind in {PageSectionKind.CERTIFICATION, PageSectionKind.COMPANY_HONOR}:
        return ChunkType.CERTIFICATION_SECTION, KnowledgeDomain.CERTIFICATION
    if page.sectionKind == PageSectionKind.SALES_NETWORK:
        return ChunkType.SALES_SECTION, KnowledgeDomain.SALES
    return ChunkType.COMPANY_SECTION, KnowledgeDomain.COMPANY


def _profile_segments(page: PageContract) -> tuple[tuple[str, str, KnowledgeDomain], ...]:
    segments: list[tuple[str, str, KnowledgeDomain]] = []
    for line in (item.strip() for item in page.normalizedText.splitlines() if item.strip()):
        if "主要生产" in line and "销售网络" in line:
            product_text, sales_text = line.split("宣传册称销售网络", maxsplit=1)
            segments.append(("主要产品", product_text.rstrip("。") + "。", KnowledgeDomain.PRODUCT_MARKETING))
            segments.append(("销售与合作陈述", "宣传册称销售网络" + sales_text, KnowledgeDomain.SALES))
        elif any(term in line for term in ("ISO9001", "ISO22000", "HACCP", "FSSC22000")):
            segments.append(("体系认证与建设陈述", line, KnowledgeDomain.CERTIFICATION))
        elif "销售网络" in line or "合作" in line:
            segments.append(("销售与合作陈述", line, KnowledgeDomain.SALES))
        else:
            segments.append((page.title or "企业简介", line, KnowledgeDomain.COMPANY))
    return tuple(segments)


def _gift_segments(page: PageContract) -> tuple[tuple[str, str, tuple[str, ...]], ...]:
    segments: list[tuple[str, str, tuple[str, ...]]] = []
    for line in (item.strip() for item in page.normalizedText.splitlines() if item.strip()):
        if "：" in line:
            product = line.split("：", maxsplit=1)[0].strip()
            segments.append((product, line, (product, "礼品糖")))
        elif line.startswith("包装文案"):
            segments.append(("礼品糖包装文案", line, ("礼品糖",)))
    return tuple(segments)


def _page_segments(
    page: PageContract,
) -> tuple[tuple[str, str, ChunkType, KnowledgeDomain, tuple[str, ...]], ...]:
    default_type, default_domain = _page_chunk_type(page)
    if page.pageNumber == 3 and page.sectionKind == PageSectionKind.COMPANY_PROFILE:
        return tuple(
            (title, text, _page_chunk_type(page)[0] if domain == KnowledgeDomain.COMPANY else {
                KnowledgeDomain.PRODUCT_MARKETING: ChunkType.PRODUCT_SECTION,
                KnowledgeDomain.CERTIFICATION: ChunkType.CERTIFICATION_SECTION,
                KnowledgeDomain.SALES: ChunkType.SALES_SECTION,
            }[domain], domain, page.productFamilies if domain == KnowledgeDomain.PRODUCT_MARKETING else ())
            for title, text, domain in _profile_segments(page)
        )
    if page.sectionKind == PageSectionKind.GIFT_PRODUCT:
        return tuple(
            (title, text, ChunkType.PRODUCT_SECTION, KnowledgeDomain.PRODUCT_MARKETING, families)
            for title, text, families in _gift_segments(page)
        )
    return ((page.title or f"第 {page.pageNumber} 页", page.normalizedText, default_type, default_domain, page.productFamilies),)


def _pdf_chunks(document: DocumentContract) -> list[ChunkContract]:
    chunks: list[ChunkContract] = []
    for page in sorted(document.pages, key=lambda item: item.pageNumber):
        if page.sectionKind == PageSectionKind.SOURCE_BLANK_PAGE or not page.normalizedText.strip():
            continue
        for ordinal, (title, text, chunk_type, domain, families) in enumerate(_page_segments(page), start=1):
            if not text.strip():
                continue
            chunks.append(
                _base_chunk(
                    document,
                    chunk_id=f"{document.documentId}/page/{page.pageNumber:03d}/{ordinal:02d}",
                    chunk_type=chunk_type,
                    title=f"{document.displayTitle}：{title}",
                    content=(
                        f"文档：{document.displayTitle}\n"
                        f"页码：{page.pageNumber}\n"
                        f"主题：{title}\n{text.strip()}"
                    ),
                    source_text=page.sourceText,
                    knowledge_domain=domain,
                    page_number=page.pageNumber,
                    product_families=families,
                    keywords=(title, *(families or ())),
                    quality_flags=page.qualityFlags,
                )
            )
    return chunks


def build_chunks(documents: tuple[DocumentContract, ...]) -> tuple[ChunkContract, ...]:
    chunks = [
        chunk
        for document in documents
        for chunk in (_process_chunks(document) if document.process.steps else _pdf_chunks(document))
    ]
    chunks.sort(key=lambda item: item.chunkId)
    ids = [chunk.chunkId for chunk in chunks]
    if len(ids) != len(set(ids)):
        raise RagBuildError("CHUNK_ID_DUPLICATE", "The chunk builder generated duplicate IDs.")
    fingerprints = [
        (chunk.documentId, chunk.chunkType, chunk.title, chunk.content)
        for chunk in chunks
    ]
    if len(fingerprints) != len(set(fingerprints)):
        raise RagBuildError(
            "CHUNK_CONTENT_DUPLICATE",
            "The chunk builder generated duplicate content inside a document.",
        )
    return tuple(
        chunk.model_copy(update={"embeddingRef": f"vector/row/{index}"})
        for index, chunk in enumerate(chunks)
    )


def _chunk_manifest_hash(manifest: ChunkManifest) -> str:
    payload = manifest.model_dump(mode="json")
    payload.pop("generatedAt")
    payload.pop("chunkManifestSha256")
    return _canonical_hash(payload)


def build_chunk_release(
    input_release: Path,
    output_root: Path,
    corpus_version: str,
    *,
    generated_at: datetime | None = None,
) -> tuple[tuple[ChunkContract, ...], ChunkManifest, ChunkingReport, CorpusManifest]:
    source, output = validate_output_root(input_release, output_root)
    input_manifest, documents = _load_normalized_input(source, corpus_version)
    release_root = output / "releases" / corpus_version
    if release_root.exists():
        raise RagBuildError(
            "CHUNK_RELEASE_ALREADY_EXISTS",
            "The chunk release already exists and will not be overwritten.",
        )
    effective_time = generated_at or datetime.now(ZoneInfo(TIMEZONE))
    chunks = build_chunks(documents)
    summaries = tuple(
        ChunkSummary(
            chunkId=chunk.chunkId,
            documentId=chunk.documentId,
            chunkType=chunk.chunkType,
            contentSha256=chunk.contentSha256,
            embeddingRef=chunk.embeddingRef or "",
        )
        for chunk in chunks
    )
    counts = Counter(chunk.chunkType for chunk in chunks)
    draft_manifest = ChunkManifest(
        corpusVersion=corpus_version,
        generatedAt=effective_time,
        chunkerVersion=CHUNKER_VERSION,
        documentManifestSha256=input_manifest.documentManifestSha256,
        documentCount=len(documents),
        chunkCount=len(chunks),
        chunkTypeCounts={chunk_type: counts[chunk_type] for chunk_type in ChunkType},
        chunks=summaries,
        chunkManifestSha256="0" * 64,
    )
    chunk_manifest = draft_manifest.model_copy(
        update={"chunkManifestSha256": _chunk_manifest_hash(draft_manifest)}
    )
    report = ChunkingReport(
        corpusVersion=corpus_version,
        generatedAt=effective_time,
        status=BuildStatus.SUCCEEDED,
        documentManifestSha256=input_manifest.documentManifestSha256,
        documentCount=len(documents),
        chunkCount=len(chunks),
        duplicateContentCount=0,
        emptySourceTextCount=0,
        chunkManifestSha256=chunk_manifest.chunkManifestSha256,
    )
    output_manifest = input_manifest.model_copy(
        update={
            "builtAt": effective_time,
            "chunkCount": len(chunks),
            "chunkManifestSha256": chunk_manifest.chunkManifestSha256,
            "parserVersions": ParserVersions(
                **{
                    **input_manifest.parserVersions.model_dump(),
                    "chunker": CHUNKER_VERSION,
                }
            ),
        }
    )

    releases_root = output / "releases"
    staging_root = releases_root / f".{corpus_version}.staging-{uuid.uuid4().hex}"
    try:
        (staging_root / "documents").mkdir(parents=True)
        (staging_root / "chunks").mkdir()
        (staging_root / "indexes").mkdir()
        (staging_root / "qa").mkdir()
        shutil.copy2(source / "source-inventory.json", staging_root / "source-inventory.json")
        for path in sorted((source / "documents").glob("*.document.json")):
            shutil.copy2(path, staging_root / "documents" / path.name)
        for name in (
            "normalization-report.json",
            "quality-report.json",
            "normalization-review-profile.json",
        ):
            shutil.copy2(source / "qa" / name, staging_root / "qa" / name)
        _write_jsonl(
            staging_root / "chunks" / "chunks.jsonl",
            (chunk.model_dump(mode="json") for chunk in chunks),
        )
        _write_json(
            staging_root / "chunks" / "chunk-manifest.json",
            chunk_manifest.model_dump(mode="json"),
        )
        _write_json(
            staging_root / "qa" / "chunking-report.json",
            report.model_dump(mode="json"),
        )
        _write_json(staging_root / "corpus-manifest.json", output_manifest.model_dump(mode="json"))
        releases_root.mkdir(parents=True, exist_ok=True)
        staging_root.replace(release_root)
    except OSError as exc:
        raise RagBuildError("CHUNK_OUTPUT_WRITE_FAILED", "Unable to write chunk artifacts.") from exc
    finally:
        if staging_root.exists():
            shutil.rmtree(staging_root, ignore_errors=True)
    return chunks, chunk_manifest, report, output_manifest
