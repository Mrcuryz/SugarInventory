from __future__ import annotations

import json
import os
from datetime import datetime
from pathlib import Path
from zoneinfo import ZoneInfo

import pytest

from app.rag.contracts import SourceInventoryManifest, SourceType
from app.rag.offline import source_inventory
from app.rag.offline.cli import main
from app.rag.offline.source_inventory import (
    RagBuildError,
    build_source_inventory,
    load_source_inventory,
    validate_inventory_checksum,
    write_inventory_release,
)


NOW = datetime(2026, 7, 29, 10, 0, tzinfo=ZoneInfo("Asia/Shanghai"))
CORPUS_VERSION = "laibin-rag-2026-07-29-v1"


def _source_tree(root: Path) -> Path:
    source = root / "甲方材料"
    process = source / "工艺流程图"
    process.mkdir(parents=True)
    (process / "单晶黄冰糖工艺流程图24.12.docx").write_bytes(b"docx-fixture-a")
    (process / "多晶冰糖工艺流程图25.8.DOCX").write_bytes(b"docx-fixture-b")
    (source / "“来冰”企业宣传册.pdf").write_bytes(b"pdf-fixture")
    (source / "README.txt").write_text("不属于受支持材料", encoding="utf-8")
    return source


def test_inventory_is_deterministic_and_never_exposes_absolute_source_path(tmp_path: Path) -> None:
    source = _source_tree(tmp_path)

    first = build_source_inventory(source, CORPUS_VERSION, generated_at=NOW)
    second = build_source_inventory(source, CORPUS_VERSION, generated_at=NOW)

    assert first == second
    assert first.fileCount == 3
    assert first.sourceTypeCounts == {SourceType.DOCX: 2, SourceType.PDF: 1}
    assert first.ignoredRelativePaths == ("README.txt",)
    assert first.inventorySha256 == second.inventorySha256
    assert len({item.documentId for item in first.files}) == 3
    assert str(source.resolve()) not in first.model_dump_json()
    assert all("\\" not in item.relativePath for item in first.files)
    validate_inventory_checksum(first)


def test_inventory_document_id_depends_on_relative_path_not_source_root(tmp_path: Path) -> None:
    first_source = _source_tree(tmp_path / "first")
    second_source = _source_tree(tmp_path / "second")

    first = build_source_inventory(first_source, CORPUS_VERSION, generated_at=NOW)
    second = build_source_inventory(second_source, CORPUS_VERSION, generated_at=NOW)

    assert [(item.documentId, item.sourceSha256) for item in first.files] == [
        (item.documentId, item.sourceSha256) for item in second.files
    ]
    assert first.inventorySha256 == second.inventorySha256


def test_inventory_rejects_source_changes_during_hashing(
    tmp_path: Path,
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    source = _source_tree(tmp_path)
    target = source / "“来冰”企业宣传册.pdf"
    original_hash = source_inventory._sha256_file

    def mutate_after_hash(path: Path) -> str:
        digest = original_hash(path)
        if path == target:
            path.write_bytes(path.read_bytes() + b"-changed")
        return digest

    monkeypatch.setattr(source_inventory, "_sha256_file", mutate_after_hash)

    with pytest.raises(RagBuildError, match="changed while it was being inventoried"):
        build_source_inventory(source, CORPUS_VERSION, generated_at=NOW)


def test_inventory_rejects_linked_entries_without_following_them(tmp_path: Path) -> None:
    source = _source_tree(tmp_path)
    outside = tmp_path / "outside.docx"
    outside.write_bytes(b"must-not-be-read")
    link = source / "linked.docx"
    try:
        os.symlink(outside, link)
    except OSError:
        pytest.skip("symbolic links are not available in this test environment")

    with pytest.raises(RagBuildError, match="Linked source entries"):
        build_source_inventory(source, CORPUS_VERSION, generated_at=NOW)


def test_inventory_rejects_windows_reparse_entries(
    tmp_path: Path,
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    source = _source_tree(tmp_path)
    suspicious = source / "linked.docx"
    suspicious.write_bytes(b"must-not-be-read")
    original_check = source_inventory._is_reparse_point
    monkeypatch.setattr(
        source_inventory,
        "_is_reparse_point",
        lambda path: path == suspicious or original_check(path),
    )

    with pytest.raises(RagBuildError, match="Linked source entries"):
        build_source_inventory(source, CORPUS_VERSION, generated_at=NOW)


def test_release_writer_requires_git_ignored_output_and_does_not_overwrite(
    tmp_path: Path,
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    source = _source_tree(tmp_path)
    output = source_inventory.PROJECT_ROOT / "agent-service" / "build" / "rag-test"
    monkeypatch.setattr(source_inventory, "is_git_ignored", lambda path: True)
    monkeypatch.setattr(source_inventory, "PROJECT_ROOT", tmp_path)
    output = tmp_path / "ignored-artifacts"
    before = {
        path.relative_to(source): (path.stat().st_size, path.read_bytes())
        for path in source.rglob("*")
        if path.is_file()
    }

    manifest, inventory_path = write_inventory_release(
        source,
        output,
        CORPUS_VERSION,
        generated_at=NOW,
    )

    assert inventory_path.is_file()
    assert inventory_path.read_bytes()[:3] != b"\xef\xbb\xbf"
    assert SourceInventoryManifest.model_validate_json(
        inventory_path.read_text(encoding="utf-8")
    ) == manifest
    assert {
        child.name
        for child in inventory_path.parent.iterdir()
        if child.is_dir()
    } == {"documents", "chunks", "index", "qa", "evaluation"}
    after = {
        path.relative_to(source): (path.stat().st_size, path.read_bytes())
        for path in source.rglob("*")
        if path.is_file()
    }
    assert after == before

    with pytest.raises(RagBuildError, match="will not be overwritten"):
        write_inventory_release(source, output, CORPUS_VERSION, generated_at=NOW)


def test_output_inside_source_is_rejected_before_writing(
    tmp_path: Path,
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    source = _source_tree(tmp_path)
    monkeypatch.setattr(source_inventory, "is_git_ignored", lambda path: True)
    monkeypatch.setattr(source_inventory, "PROJECT_ROOT", tmp_path)

    with pytest.raises(RagBuildError, match="must not be inside"):
        write_inventory_release(
            source,
            source / "generated",
            CORPUS_VERSION,
            generated_at=NOW,
        )
    assert not (source / "generated").exists()


def test_output_must_be_git_ignored_and_must_not_use_reparse_components(
    tmp_path: Path,
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    source = _source_tree(tmp_path)
    output = tmp_path / "artifacts"
    monkeypatch.setattr(source_inventory, "PROJECT_ROOT", tmp_path)
    monkeypatch.setattr(source_inventory, "is_git_ignored", lambda path: False)

    with pytest.raises(RagBuildError, match="explicitly ignored by Git"):
        write_inventory_release(source, output, CORPUS_VERSION, generated_at=NOW)

    output.mkdir()
    original_check = source_inventory._is_reparse_point
    monkeypatch.setattr(source_inventory, "is_git_ignored", lambda path: True)
    monkeypatch.setattr(
        source_inventory,
        "_is_reparse_point",
        lambda path: path == output or original_check(path),
    )
    with pytest.raises(RagBuildError, match="must not contain"):
        write_inventory_release(source, output, CORPUS_VERSION, generated_at=NOW)


def test_cli_rejects_corpus_version_path_traversal_before_creating_release(
    tmp_path: Path,
    monkeypatch: pytest.MonkeyPatch,
    capsys: pytest.CaptureFixture[str],
) -> None:
    source = _source_tree(tmp_path)
    output = tmp_path / "ignored-artifacts"
    monkeypatch.setattr(source_inventory, "PROJECT_ROOT", tmp_path)
    monkeypatch.setattr(source_inventory, "is_git_ignored", lambda path: True)

    assert main(
        [
            "inventory",
            "--source",
            str(source),
            "--output",
            str(output),
            "--corpus-version",
            "../escape",
        ]
    ) == 2
    failure = json.loads(capsys.readouterr().err)
    assert failure["status"] == "FAILED"
    assert failure["errorCode"] == "CONTRACT_INVALID"
    assert not output.exists()


def test_cli_inventory_and_validate_return_safe_structured_summary(
    tmp_path: Path,
    monkeypatch: pytest.MonkeyPatch,
    capsys: pytest.CaptureFixture[str],
) -> None:
    source = _source_tree(tmp_path)
    output = tmp_path / "ignored-artifacts"
    monkeypatch.setattr(source_inventory, "is_git_ignored", lambda path: True)
    monkeypatch.setattr(source_inventory, "PROJECT_ROOT", tmp_path)

    assert main(
        [
            "inventory",
            "--source",
            str(source),
            "--output",
            str(output),
            "--corpus-version",
            CORPUS_VERSION,
        ]
    ) == 0
    summary = json.loads(capsys.readouterr().out)
    assert summary["status"] == "SUCCEEDED"
    assert summary["fileCount"] == 3
    assert summary["artifact"] == f"releases/{CORPUS_VERSION}/source-inventory.json"
    assert str(source) not in json.dumps(summary, ensure_ascii=False)

    inventory_path = output / summary["artifact"]
    assert main(["validate-inventory", "--inventory", str(inventory_path)]) == 0
    validation = json.loads(capsys.readouterr().out)
    assert validation["status"] == "SUCCEEDED"
    assert load_source_inventory(inventory_path).inventorySha256 == validation["inventorySha256"]
