from __future__ import annotations

import zipfile
from pathlib import Path


CONTENT_TYPES = """<?xml version="1.0" encoding="UTF-8"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
  <Default Extension="xml" ContentType="application/xml"/>
  <Override PartName="/word/document.xml"
    ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>
</Types>
"""

PACKAGE_RELS = """<?xml version="1.0" encoding="UTF-8"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1"
    Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument"
    Target="word/document.xml"/>
</Relationships>
"""


def _paragraph(text: str) -> str:
    return f"<w:p><w:r><w:t>{text}</w:t></w:r></w:p>"


def _modern_text_box(shape_id: int, text: str, x: int, y: int) -> str:
    return f"""
    <w:p><w:r>
      <mc:AlternateContent>
        <mc:Choice Requires="wps">
          <w:drawing>
            <wp:anchor>
              <wp:positionH relativeFrom="page"><wp:posOffset>{x}</wp:posOffset></wp:positionH>
              <wp:positionV relativeFrom="page"><wp:posOffset>{y}</wp:posOffset></wp:positionV>
              <wp:extent cx="1200000" cy="300000"/>
              <wp:docPr id="{shape_id}" name="Text Box {shape_id}"/>
              <a:prstGeom prst="rect"/>
              <wps:wsp><wps:txbx><w:txbxContent>{_paragraph(text)}</w:txbxContent></wps:txbx></wps:wsp>
            </wp:anchor>
          </w:drawing>
        </mc:Choice>
        <mc:Fallback>
          <w:pict>
            <v:shape id="legacy-{shape_id}"
              style="margin-left:{x / 12700}pt;margin-top:{y / 12700}pt;width:94pt;height:24pt">
              <v:textbox><w:txbxContent>{_paragraph(text)}</w:txbxContent></v:textbox>
            </v:shape>
          </w:pict>
        </mc:Fallback>
      </mc:AlternateContent>
    </w:r></w:p>
    """


def _modern_connector(shape_id: int) -> str:
    return f"""
    <w:p><w:r>
      <mc:AlternateContent>
        <mc:Choice Requires="wps">
          <w:drawing>
            <wp:anchor>
              <wp:positionH relativeFrom="page"><wp:posOffset>100000</wp:posOffset></wp:positionH>
              <wp:positionV relativeFrom="page"><wp:posOffset>500000</wp:posOffset></wp:positionV>
              <wp:extent cx="0" cy="400000"/>
              <wp:docPr id="{shape_id}" name="Connector {shape_id}"/>
              <a:prstGeom prst="line"/>
              <a:ln><a:tailEnd type="triangle"/></a:ln>
            </wp:anchor>
          </w:drawing>
        </mc:Choice>
        <mc:Fallback>
          <w:pict>
            <v:line id="legacy-line-{shape_id}" from="0,0" to="0,40pt">
              <v:stroke endarrow="block"/>
            </v:line>
          </w:pict>
        </mc:Fallback>
      </mc:AlternateContent>
    </w:r></w:p>
    """


def write_modern_docx(
    path: Path,
    *,
    external_relationship: bool = False,
    unsafe_entry: str | None = None,
    high_ratio_entry: bool = False,
) -> None:
    document = f"""<?xml version="1.0" encoding="UTF-8"?>
    <w:document
      xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main"
      xmlns:mc="http://schemas.openxmlformats.org/markup-compatibility/2006"
      xmlns:v="urn:schemas-microsoft-com:vml"
      xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"
      xmlns:wp="http://schemas.openxmlformats.org/drawingml/2006/wordprocessingDrawing"
      xmlns:wps="http://schemas.microsoft.com/office/word/2010/wordprocessingShape"
      mc:Ignorable="wps">
      <w:body>
        {_paragraph("样例工艺流程图")}
        {_modern_text_box(1, "1、原料验收（CCP1）", 100000, 100000)}
        {_modern_connector(2)}
        {_modern_text_box(3, "2、浓缩，温度112-120℃，时间30-60分钟。", 100000, 900000)}
        <w:tbl>
          <w:tr><w:tc>{_paragraph("关键参数")}</w:tc><w:tc>{_paragraph("压力0.4-0.8Mpa")}</w:tc></w:tr>
        </w:tbl>
        <w:sectPr/>
      </w:body>
    </w:document>
    """
    relationships = """<?xml version="1.0" encoding="UTF-8"?>
    <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
    """
    if external_relationship:
        relationships += """
      <Relationship Id="rId9"
        Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/hyperlink"
        Target="https://example.invalid/" TargetMode="External"/>
        """
    relationships += "</Relationships>"

    path.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(path, "w", compression=zipfile.ZIP_DEFLATED) as package:
        package.writestr("[Content_Types].xml", CONTENT_TYPES)
        package.writestr("_rels/.rels", PACKAGE_RELS)
        package.writestr("word/document.xml", document)
        package.writestr("word/_rels/document.xml.rels", relationships)
        if unsafe_entry:
            package.writestr(unsafe_entry, "unsafe")
        if high_ratio_entry:
            package.writestr("word/high-ratio.bin", b"\0" * (2 * 1024 * 1024))


def write_legacy_docx(path: Path) -> None:
    document = f"""<?xml version="1.0" encoding="UTF-8"?>
    <w:document
      xmlns:v="urn:schemas-microsoft-com:vml"
      xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
      <w:body>
        {_paragraph("旧版工艺流程图")}
        <w:p><w:r><w:pict>
          <v:shape id="legacy-step-1"
            style="margin-left:10pt;margin-top:10pt;width:100pt;height:25pt">
            <v:textbox><w:txbxContent>{_paragraph("1、原料验收")}</w:txbxContent></v:textbox>
          </v:shape>
        </w:pict></w:r></w:p>
        <w:p><w:r><w:pict>
          <v:line id="legacy-connector" from="10pt,35pt" to="10pt,80pt">
            <v:stroke endarrow="block"/>
          </v:line>
        </w:pict></w:r></w:p>
        <w:p><w:r><w:pict>
          <v:shape id="legacy-step-2"
            style="margin-left:10pt;margin-top:80pt;width:100pt;height:25pt">
            <v:textbox><w:txbxContent>{_paragraph("2、入库贮存")}</w:txbxContent></v:textbox>
          </v:shape>
        </w:pict></w:r></w:p>
        <w:sectPr/>
      </w:body>
    </w:document>
    """
    path.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(path, "w", compression=zipfile.ZIP_DEFLATED) as package:
        package.writestr("[Content_Types].xml", CONTENT_TYPES)
        package.writestr("_rels/.rels", PACKAGE_RELS)
        package.writestr("word/document.xml", document)
        package.writestr(
            "word/_rels/document.xml.rels",
            '<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"/>',
        )
