from __future__ import annotations

from pathlib import Path

from pypdf import PdfWriter


def write_pdf(
    path: Path,
    *,
    page_count: int = 2,
    encrypted: bool = False,
) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    writer = PdfWriter()
    for index in range(page_count):
        writer.add_blank_page(width=595 + index, height=842 + index)
    writer.add_metadata(
        {
            "/Title": "RAG PDF 测试",
            "/Creator": "fixture",
            "/Producer": "pypdf",
        }
    )
    if encrypted:
        writer.encrypt("secret")
    with path.open("wb") as target:
        writer.write(target)
