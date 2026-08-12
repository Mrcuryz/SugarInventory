from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path
from typing import Sequence

from pydantic import ValidationError

from app.rag.config import CORPUS_ID, DEPLOY_ARTIFACT_ROOT
from app.rag.offline.source_inventory import (
    RagBuildError,
    load_source_inventory,
    write_inventory_release,
)


def _parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        prog="rag-offline",
        description="Build and validate versioned, offline RAG artifacts.",
    )
    subparsers = parser.add_subparsers(dest="command", required=True)

    inventory = subparsers.add_parser(
        "inventory",
        help="Create a read-only source inventory and release directory skeleton.",
    )
    inventory.add_argument("--source", type=Path, required=True)
    inventory.add_argument("--output", type=Path, required=True)
    inventory.add_argument("--corpus-version", required=True)

    parse_docx = subparsers.add_parser(
        "parse-docx",
        help="Parse inventoried DOCX files into controlled OOXML extraction artifacts.",
    )
    parse_docx.add_argument("--source", type=Path, required=True)
    parse_docx.add_argument("--output", type=Path, required=True)
    parse_docx.add_argument("--corpus-version", required=True)

    review_docx = subparsers.add_parser(
        "record-docx-visual-review",
        help="Record a compare-and-set visual review after every rendered DOCX page was inspected.",
    )
    review_docx.add_argument("--source", type=Path, required=True)
    review_docx.add_argument("--output", type=Path, required=True)
    review_docx.add_argument("--corpus-version", required=True)
    review_docx.add_argument("--document-id", required=True)
    review_docx.add_argument("--expected-extraction-sha256", required=True)
    review_docx.add_argument("--renderer", required=True)
    review_docx.add_argument("--renderer-version", required=True)
    review_docx.add_argument("--note", action="append", default=[])

    parse_pdf = subparsers.add_parser(
        "parse-pdf",
        help="Preflight, render, and optionally OCR inventoried PDF files.",
    )
    parse_pdf.add_argument("--source", type=Path, required=True)
    parse_pdf.add_argument("--output", type=Path, required=True)
    parse_pdf.add_argument("--corpus-version", required=True)
    parse_pdf.add_argument("--renderer", type=Path)
    parse_pdf.add_argument("--dpi", type=int, default=250)

    review_pdf = subparsers.add_parser(
        "record-pdf-visual-review",
        help="Record a compare-and-set visual review after every rendered page was inspected.",
    )
    review_pdf.add_argument("--source", type=Path, required=True)
    review_pdf.add_argument("--output", type=Path, required=True)
    review_pdf.add_argument("--corpus-version", required=True)
    review_pdf.add_argument("--document-id", required=True)
    review_pdf.add_argument("--expected-extraction-sha256", required=True)
    review_pdf.add_argument("--note", action="append", default=[])

    normalize = subparsers.add_parser(
        "normalize",
        help="Normalize reviewed DOCX/PDF extractions into frozen document contracts.",
    )
    normalize.add_argument("--source", type=Path, required=True)
    normalize.add_argument("--output", type=Path, required=True)
    normalize.add_argument("--corpus-version", required=True)
    normalize.add_argument("--review-profile", type=Path, required=True)

    chunk = subparsers.add_parser(
        "build-chunks",
        help="Build deterministic retrieval chunks from an immutable normalized release.",
    )
    chunk.add_argument("--input-release", type=Path, required=True)
    chunk.add_argument("--output", type=Path, required=True)
    chunk.add_argument("--corpus-version", required=True)

    index = subparsers.add_parser(
        "build-indexes",
        help="Build SQLite FTS5 and local FastEmbed vector indexes.",
    )
    index.add_argument("--release", type=Path, required=True)
    index.add_argument("--model-path", type=Path, required=True)
    index.add_argument("--model-name", default="BAAI/bge-small-zh-v1.5")
    index.add_argument("--threads", type=int, default=2)

    evaluate = subparsers.add_parser(
        "evaluate-index",
        help="Evaluate a frozen hybrid index against the fixed retrieval JSONL set.",
    )
    evaluate.add_argument("--release", type=Path, required=True)
    evaluate.add_argument("--model-path", type=Path, required=True)
    evaluate.add_argument("--cases", type=Path, required=True)
    evaluate.add_argument("--model-name", default="BAAI/bge-small-zh-v1.5")
    evaluate.add_argument("--threads", type=int, default=2)

    validate = subparsers.add_parser(
        "validate-inventory",
        help="Validate a source inventory contract and checksum.",
    )
    validate.add_argument("--inventory", type=Path, required=True)

    validate_release = subparsers.add_parser(
        "validate-release",
        help="Validate a complete immutable RAG release and all publication gates.",
    )
    validate_release.add_argument("--release", type=Path, required=True)

    validate_runtime = subparsers.add_parser(
        "validate-runtime",
        help="Validate the active pointer, immutable release, and local query model.",
    )
    validate_runtime.add_argument("--runtime-root", type=Path, required=True)
    validate_runtime.add_argument("--model-path", type=Path, required=True)
    validate_runtime.add_argument("--model-name", default="BAAI/bge-small-zh-v1.5")
    validate_runtime.add_argument("--threads", type=int, default=2)

    publish = subparsers.add_parser(
        "publish-release",
        help="Copy and atomically activate a validated immutable RAG release.",
    )
    publish.add_argument("--release", type=Path, required=True)
    publish.add_argument("--runtime-root", type=Path, default=DEPLOY_ARTIFACT_ROOT)
    publish.add_argument("--published-by", required=True)
    publish.add_argument("--expected-current-version")

    rollback = subparsers.add_parser(
        "rollback-release",
        help="Atomically switch to a previously published validated RAG release.",
    )
    rollback.add_argument("--runtime-root", type=Path, default=DEPLOY_ARTIFACT_ROOT)
    rollback.add_argument("--target-version", required=True)
    rollback.add_argument("--expected-current-version", required=True)
    rollback.add_argument("--published-by", required=True)
    return parser


def _print_json(payload: dict[str, object], *, stream: object | None = None) -> None:
    print(
        json.dumps(payload, ensure_ascii=False, sort_keys=True),
        file=stream or sys.stdout,
    )


def main(argv: Sequence[str] | None = None) -> int:
    args = _parser().parse_args(argv)
    try:
        if args.command == "validate-runtime":
            from app.rag.runtime.corpus_loader import (
                RagRuntimeConfiguration,
                load_current_corpus,
            )

            loaded = load_current_corpus(
                RagRuntimeConfiguration(
                    enabled=True,
                    required=True,
                    root=str(args.runtime_root),
                    model_path=str(args.model_path),
                    model_name=args.model_name,
                    model_threads=args.threads,
                )
            )
            _print_json(
                {
                    "status": "SUCCEEDED",
                    "corpusId": loaded.pointer.corpusId,
                    "corpusVersion": loaded.pointer.corpusVersion,
                    "releaseName": loaded.pointer.releaseName,
                    "documentCount": loaded.retriever.corpus.documentCount,
                    "chunkCount": loaded.retriever.corpus.chunkCount,
                    "allowedRoles": list(loaded.retriever.corpus.allowedRoles),
                }
            )
            return 0

        if args.command == "validate-release":
            from app.rag.offline.publisher import validate_release

            validated = validate_release(args.release)
            _print_json(
                {
                    "status": "SUCCEEDED",
                    "corpusId": validated.pointer.corpusId,
                    "corpusVersion": validated.pointer.corpusVersion,
                    "documentCount": validated.documentCount,
                    "chunkCount": validated.chunkCount,
                    "evaluationCaseCount": validated.evaluationCaseCount,
                    "fileCount": validated.fileCount,
                    "totalBytes": validated.totalBytes,
                    "releaseSha256": validated.releaseSha256,
                    "corpusManifestSha256": validated.pointer.corpusManifestSha256,
                    "indexManifestSha256": validated.pointer.indexManifestSha256,
                    "evaluationReportSha256": validated.pointer.evaluationReportSha256,
                }
            )
            return 0

        if args.command == "publish-release":
            from app.rag.offline.publisher import publish_release

            result = publish_release(
                args.release,
                args.runtime_root,
                published_by=args.published_by,
                expected_current_version=args.expected_current_version,
            )
            _print_json(
                {
                    "status": "SUCCEEDED",
                    "action": result.action,
                    "corpusId": result.pointer.corpusId,
                    "corpusVersion": result.pointer.corpusVersion,
                    "previousCorpusVersion": result.previousCorpusVersion,
                    "releaseSha256": result.releaseSha256,
                    "releaseCopied": result.releaseCopied,
                    "pointerChanged": result.pointerChanged,
                    "auditEvent": result.auditEvent,
                }
            )
            return 0

        if args.command == "rollback-release":
            from app.rag.offline.publisher import rollback_release

            result = rollback_release(
                args.runtime_root,
                target_version=args.target_version,
                expected_current_version=args.expected_current_version,
                published_by=args.published_by,
            )
            _print_json(
                {
                    "status": "SUCCEEDED",
                    "action": result.action,
                    "corpusId": result.pointer.corpusId,
                    "corpusVersion": result.pointer.corpusVersion,
                    "previousCorpusVersion": result.previousCorpusVersion,
                    "releaseSha256": result.releaseSha256,
                    "releaseCopied": result.releaseCopied,
                    "pointerChanged": result.pointerChanged,
                    "auditEvent": result.auditEvent,
                }
            )
            return 0

        if args.command == "inventory":
            manifest, inventory_path = write_inventory_release(
                args.source,
                args.output,
                args.corpus_version,
            )
            _print_json(
                {
                    "status": "SUCCEEDED",
                    "corpusId": CORPUS_ID,
                    "corpusVersion": manifest.corpusVersion,
                    "fileCount": manifest.fileCount,
                    "sourceTypeCounts": {
                        key.value: value
                        for key, value in manifest.sourceTypeCounts.items()
                    },
                    "inventorySha256": manifest.inventorySha256,
                    "artifact": (
                        Path("releases")
                        / manifest.corpusVersion
                        / inventory_path.name
                    ).as_posix(),
                }
            )
            return 0

        if args.command == "parse-docx":
            from app.rag.offline.docx_parser import parse_docx_release

            extractions, report, _ = parse_docx_release(
                args.source,
                args.output,
                args.corpus_version,
            )
            _print_json(
                {
                    "status": report.status.value,
                    "corpusId": report.corpusId,
                    "corpusVersion": report.corpusVersion,
                    "documentCount": len(extractions),
                    "succeededCount": report.succeededCount,
                    "failedCount": report.failedCount,
                    "visualValidationPendingCount": report.visualValidationPendingCount,
                    "extractionManifestSha256": report.extractionManifestSha256,
                    "artifact": (
                        Path("releases")
                        / report.corpusVersion
                        / "qa"
                        / "docx-extraction-report.json"
                    ).as_posix(),
                }
            )
            return 0

        if args.command == "record-docx-visual-review":
            from app.rag.offline.docx_parser import (
                record_docx_visual_review,
            )

            extraction, report = record_docx_visual_review(
                args.source,
                args.output,
                args.corpus_version,
                args.document_id,
                args.expected_extraction_sha256,
                renderer=args.renderer,
                renderer_version=args.renderer_version,
                notes=tuple(args.note),
            )
            _print_json(
                {
                    "status": report.status.value,
                    "corpusId": report.corpusId,
                    "corpusVersion": report.corpusVersion,
                    "documentId": extraction.documentId,
                    "visualValidationStatus": (
                        extraction.visualValidation.status.value
                    ),
                    "reviewedPageCount": (
                        extraction.visualValidation.reviewedPageCount
                    ),
                    "extractionSha256": extraction.extractionSha256,
                    "extractionManifestSha256": (
                        report.extractionManifestSha256
                    ),
                    "visualValidationPendingCount": (
                        report.visualValidationPendingCount
                    ),
                }
            )
            return 0

        if args.command == "parse-pdf":
            from app.rag.offline.ocr_provider import (
                resolve_ocr_provider_from_environment,
            )
            from app.rag.offline.pdf_parser import parse_pdf_release, resolve_pdf_renderer

            ocr_provider = resolve_ocr_provider_from_environment()
            extractions, report, _ = parse_pdf_release(
                args.source,
                args.output,
                args.corpus_version,
                renderer=resolve_pdf_renderer(args.renderer),
                ocr_provider=ocr_provider,
                render_dpi=args.dpi,
            )
            _print_json(
                {
                    "status": report.status.value,
                    "corpusId": report.corpusId,
                    "corpusVersion": report.corpusVersion,
                    "documentCount": len(extractions),
                    "succeededCount": report.succeededCount,
                    "failedCount": report.failedCount,
                    "renderedPageCount": report.renderedPageCount,
                    "ocrPendingPageCount": report.ocrPendingPageCount,
                    "visualValidationPendingPageCount": (
                        report.visualValidationPendingPageCount
                    ),
                    "extractionManifestSha256": report.extractionManifestSha256,
                    "artifact": (
                        Path("releases")
                        / report.corpusVersion
                        / "qa"
                        / "pdf-extraction-report.json"
                    ).as_posix(),
                }
            )
            return 0

        if args.command == "record-pdf-visual-review":
            from app.rag.offline.pdf_parser import record_pdf_visual_review

            extraction, report = record_pdf_visual_review(
                args.source,
                args.output,
                args.corpus_version,
                args.document_id,
                args.expected_extraction_sha256,
                notes=tuple(args.note),
            )
            _print_json(
                {
                    "status": report.status.value,
                    "corpusId": report.corpusId,
                    "corpusVersion": report.corpusVersion,
                    "documentId": extraction.documentId,
                    "visualValidationStatus": (
                        extraction.visualValidation.status.value
                    ),
                    "reviewedPageCount": (
                        extraction.visualValidation.reviewedPageCount
                    ),
                    "extractionSha256": extraction.extractionSha256,
                    "extractionManifestSha256": (
                        report.extractionManifestSha256
                    ),
                    "ocrPendingPageCount": report.ocrPendingPageCount,
                }
            )
            return 0

        if args.command == "normalize":
            from app.rag.offline.normalizer import normalize_release

            documents, report, manifest = normalize_release(
                args.source,
                args.output,
                args.corpus_version,
                args.review_profile,
            )
            _print_json(
                {
                    "status": report.status.value,
                    "corpusId": report.corpusId,
                    "corpusVersion": report.corpusVersion,
                    "documentCount": len(documents),
                    "documentManifestSha256": report.documentManifestSha256,
                    "inventorySha256": report.inventorySha256,
                    "reviewProfileSha256": report.reviewProfileSha256,
                    "corpusStatus": manifest.status.value,
                    "artifact": (
                        Path("releases")
                        / report.corpusVersion
                        / "qa"
                        / "normalization-report.json"
                    ).as_posix(),
                }
            )
            return 0

        if args.command == "build-chunks":
            from app.rag.offline.chunker import build_chunk_release

            chunks, chunk_manifest, report, manifest = build_chunk_release(
                args.input_release,
                args.output,
                args.corpus_version,
            )
            _print_json(
                {
                    "status": report.status.value,
                    "corpusId": report.corpusId,
                    "corpusVersion": report.corpusVersion,
                    "documentCount": report.documentCount,
                    "chunkCount": len(chunks),
                    "chunkManifestSha256": chunk_manifest.chunkManifestSha256,
                    "corpusStatus": manifest.status.value,
                    "artifact": (
                        Path("releases")
                        / report.corpusVersion
                        / "chunks"
                        / "chunk-manifest.json"
                    ).as_posix(),
                }
            )
            return 0

        if args.command == "build-indexes":
            from app.rag.offline.embedding_provider import FastEmbedLocalProvider
            from app.rag.offline.index_builder import build_indexes

            provider = FastEmbedLocalProvider(
                args.model_path,
                model_name=args.model_name,
                threads=args.threads,
            )
            index_manifest, manifest = build_indexes(args.release, provider)
            _print_json(
                {
                    "status": "SUCCEEDED",
                    "corpusId": index_manifest.corpusId,
                    "corpusVersion": index_manifest.corpusVersion,
                    "documentCount": index_manifest.documentCount,
                    "chunkCount": index_manifest.chunkCount,
                    "lexicalIndexSha256": index_manifest.lexical.sha256,
                    "vectorIndexSha256": index_manifest.vector.sha256,
                    "embedding": index_manifest.embedding.model_dump(mode="json"),
                    "corpusStatus": manifest.status.value,
                    "artifact": "indexes/index-manifest.json",
                }
            )
            return 0

        if args.command == "evaluate-index":
            from app.rag.offline.embedding_provider import FastEmbedLocalProvider
            from app.rag.runtime.hybrid_retriever import HybridIndex
            from app.rag.offline.retrieval_evaluator import (
                evaluate_retrieval,
                load_evaluation_cases,
                write_evaluation_report,
            )

            provider = FastEmbedLocalProvider(
                args.model_path,
                model_name=args.model_name,
                threads=args.threads,
            )
            index = HybridIndex(args.release, provider)
            cases = load_evaluation_cases(args.cases)
            report = evaluate_retrieval(index, cases)
            artifact = write_evaluation_report(args.release, report)
            _print_json(
                {
                    "status": report.status.value,
                    "corpusId": report.corpusId,
                    "corpusVersion": report.corpusVersion,
                    "caseCount": report.caseCount,
                    "passedCount": report.passedCount,
                    "failedCount": report.failedCount,
                    "metrics": report.metrics,
                    "evaluationSetSha256": report.evaluationSetSha256,
                    "artifact": artifact.relative_to(args.release).as_posix(),
                }
            )
            return 0 if report.status.value == "SUCCEEDED" else 3

        manifest = load_source_inventory(args.inventory)
        _print_json(
            {
                "status": "SUCCEEDED",
                "corpusId": manifest.corpusId,
                "corpusVersion": manifest.corpusVersion,
                "fileCount": manifest.fileCount,
                "inventorySha256": manifest.inventorySha256,
            }
        )
        return 0
    except (RagBuildError, ValidationError) as exc:
        code = exc.code if isinstance(exc, RagBuildError) else "CONTRACT_INVALID"
        _print_json(
            {
                "status": "FAILED",
                "errorCode": code,
                "message": str(exc),
            },
            stream=sys.stderr,
        )
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
