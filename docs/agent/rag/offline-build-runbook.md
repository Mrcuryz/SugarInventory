# 智能仓储 RAG 离线构建手册

状态：`WORKTREE_ENGINEERING_VALIDATED / V1_ARTIFACT_VALIDATED / SOURCE_INTEGRATION_PENDING / ONLINE_DEPLOYMENT_UNVERIFIED`
日期：2026-08-02
适用范围：离线构建机，不适用于 Agent 运行时容器

## 1. 当前能力边界

`RAG-01A～01C` 已完成：

- 校验显式 source 和 output；
- 扫描 `.docx`、`.pdf` 普通文件；
- 生成稳定 `documentId`、文件大小和 SHA-256；
- 生成不含绝对源路径的 source inventory；
- 建立版本 release 目录骨架；
- 校验 inventory 契约和 checksum；
- 从 inventory 定位并再次校验 9 份 DOCX；
- 安全读取 OOXML、提取文本框/表格/控制点/参数/版面和连接线候选；
- 生成 DOCX extraction 与 batch QA 报告；
- 从 inventory 再次校验 1 份 PDF；
- 预检 PDF 元数据、页数、页面尺寸和现有文本层；
- 逐页渲染 250 DPI PNG，记录页级尺寸与 SHA-256；
- 提供默认关闭、HTTPS host allowlist 的 OCR provider 适配器和脱敏调用日志；
- 提供显式 `local-rapidocr` provider，在隔离环境中执行本地 CPU OCR；
- 使用 LibreOffice 26.2.5.2 将 9 份 DOCX 渲染为 14 页并绑定逐页视觉复核记录；
- 对 16 页真实宣传册完成逐页视觉核验并以 compare-and-set 落盘。

当前不执行：

- 文档规范化；
- 流程连接关系业务确认；
- OCR 低置信区域和重要宣传事实的内容级复核；
- 宣传册产品卡片等版面分区；
- chunk 生成；
- embedding；
- lexical/vector 索引；
- runtime 发布或 `current.json` 切换。

因此，`source-inventory.json` 是离线管线输入清单，不是可供 Agent 检索的知识库。

## 2. Python 与依赖

项目最低 Python 版本仍为 3.11，当前验证环境为 Python 3.12。

离线依赖位于 `agent-service/pyproject.toml` 的 `rag-build` optional extra：

| 依赖 | 版本范围 | 后续用途 |
| --- | --- | --- |
| `lxml` | `>=6.1.1,<7.0` | DOCX OOXML、文本框和关系 |
| `pypdf` | `>=6.14.2,<7.0` | PDF 元数据、页数和文本层预检 |
| `Pillow` | `>=12.3.0,<13.0` | PDF/OCR 页面图像预处理 |
| `httpx` | `>=0.28.1,<1.0` | 受控 OCR/embedding provider 适配器 |
| `rapidocr` | `==3.9.2` | 本地中文 OCR、区域和置信度 |
| `onnxruntime` | `==1.23.2` | RapidOCR CPU 推理后端 |

这些依赖不得加入普通 Agent runtime extra。LibreOffice 和 Poppler 属于离线构建机的系统依赖，也不得进入 Agent 运行时镜像。`RAG-01C` 已使用 Poppler 26.05.0 完成真实渲染验证。
当前隔离环境为 `agent-service/build/rag-build-venv`，不继承系统 site-packages；普通
Agent runtime 不需要安装 RapidOCR 或 ONNX Runtime。

开发环境安装示例：

```text
cd agent-service
python -m pip install -e ".[test,rag-build]"
```

`RAG-01A` 的 inventory 本身只使用标准库和项目已有的 Pydantic；未安装 DOCX/PDF/OCR 依赖时仍可运行 inventory。当前验证使用独立、Git 忽略的本地构建虚拟环境，不改变普通 Agent runtime 依赖。

## 3. 目录

本地构建：

```text
agent-service/build/rag/
  releases/
    <corpus-version>/
      source-inventory.json
      documents/
      chunks/
      index/
      qa/
      evaluation/
```

部署 artifact：

```text
deploy/simple/artifacts/rag/
```

这两个位置均已被项目 `.gitignore` 覆盖。CLI 仍会调用 `git check-ignore` 进行 fail-closed 校验，不能只依赖操作人员记忆。

原始材料不复制进上述目录，不提交 Git。

## 4. 生成真实材料清单

在 `agent-service` 目录执行：

```text
python -m app.rag.offline.cli inventory ^
  --source "D:\Users\Mrcury\Desktop\laibin RAG" ^
  --output build\rag ^
  --corpus-version laibin-rag-2026-07-29-v1
```

若命令执行环境对带空格路径的引号处理异常，可以把当前目录切换到材料根目录，并显式设置项目 Python path；source 使用 `.`。这只改变命令启动位置，不改变安全规则。

成功摘要示例：

```json
{
  "status": "SUCCEEDED",
  "corpusId": "laibin-warehouse-knowledge",
  "corpusVersion": "laibin-rag-2026-07-29-v1",
  "fileCount": 10,
  "sourceTypeCounts": {
    "DOCX": 9,
    "PDF": 1
  },
  "inventorySha256": "<sha256>",
  "artifact": "releases/laibin-rag-2026-07-29-v1/source-inventory.json"
}
```

摘要和清单都不输出 source 绝对路径。

## 5. 校验已有清单

```text
python -m app.rag.offline.cli validate-inventory ^
  --inventory build\rag\releases\laibin-rag-2026-07-29-v1\source-inventory.json
```

校验内容：

- JSON 和 Pydantic 契约；
- 未知字段；
- corpus version；
- 文件计数和类型计数；
- 文件总大小；
- 相对路径顺序和唯一性；
- `documentId` 唯一性；
- inventory SHA-256。

`inventorySha256` 覆盖 corpus 标识、版本、source basis、builder 版本、全部文件记录和忽略文件记录；不覆盖每次运行都会变化的 `generatedAt`。文件内容完整性由每条记录的 `sourceSha256` 保证。

该命令不会重新打开甲方 DOCX/PDF，不等同于重新处理材料。

## 6. 解析 DOCX

对已经生成 inventory 的 release 执行：

```text
python -m app.rag.offline.cli parse-docx ^
  --source "D:\Users\Mrcury\Desktop\laibin RAG" ^
  --output build\rag-r01b ^
  --corpus-version laibin-rag-2026-07-29-v1
```

命令只处理该 release 的 `source-inventory.json` 中登记的 DOCX，并在打开前复核记录和
实际文件的相对路径、文件名、类型、大小与 SHA-256。输出为：

```text
releases/<corpus-version>/
  documents/<document-id>.extraction.json
  qa/docx-extraction-report.json
```

结构解析后的真实材料结果：

```json
{
  "status": "REQUIRES_REVIEW",
  "documentCount": 9,
  "succeededCount": 9,
  "failedCount": 0,
  "visualValidationPendingCount": 9,
  "extractionManifestSha256":
    "c51f92b83ca623068eaae0ae2d22f6d0d63f14649bb88df02f51eadc62c98180"
}
```

两个独立输出目录得到相同 manifest SHA-256。随后使用 LibreOffice 26.2.5.2 完成
逐页渲染。Windows 必须使用控制台入口：

```text
C:\Program Files\LibreOffice\program\soffice.com --version
```

调用 `render_docx.py` 前，还要把 Poppler 的 `pdfinfo.exe`、`pdftoppm.exe` 目录加入当前
进程 `PATH`。技能包脚本在 Windows 上默认调用 `soffice` 时可能解析到 GUI 入口
`soffice.exe`；本次使用 Git 忽略目录中的构建启动适配器把调用固定到 `soffice.com`，
并为每次运行保留隔离的 LibreOffice profile。

渲染输出必须放在：

```text
releases/<corpus-version>/qa/docx-render/<document-id>/page-1.png
```

完成全部页面目检后，使用结构 extraction 的旧 SHA-256 逐份记录：

```text
python -m app.rag.offline.cli record-docx-visual-review ^
  --source "D:\Users\Mrcury\Desktop\laibin RAG" ^
  --output build\rag-r01b ^
  --corpus-version laibin-rag-2026-07-29-v1 ^
  --document-id <document-id> ^
  --expected-extraction-sha256 <reviewed-extraction-sha256> ^
  --renderer LibreOffice ^
  --renderer-version 26.2.5.2 ^
  --note all-rendered-pages-inspected
```

该命令重新校验 inventory 和 source SHA-256，读取连续的 `page-N.png`，验证非链接、
PNG 格式和尺寸，计算逐页 SHA-256 与空白页标记，并以 compare-and-set 更新 extraction
和批报告。

最终真实结果：

- 9/9 文档、14/14 页面渲染并目检通过；
- 5 个尾部空白第 2 页被识别并保留；
- 新旧目检渲染逐页 SHA-256 比对差异为 0；
- `visualValidationPendingCount == 0`；
- extraction manifest SHA-256 为
  `db9643abff1803b76e520cd9a3df81b96a8013f2a6ccdb92b017b556a5837857`。

批状态仍为 `REQUIRES_REVIEW`，因为流程关系、控制点冲突和标题冲突属于后续内容级复核，
不表示 DOCX 渲染环境失败。

## 7. 解析、渲染和复核 PDF

先准备具有同一 inventory 的新输出 release，再执行：

```text
python -m app.rag.offline.cli parse-pdf ^
  --source "D:\Users\Mrcury\Desktop\laibin RAG" ^
  --output build\rag-r01c ^
  --corpus-version laibin-rag-2026-07-29-v1 ^
  --renderer C:\path\to\pdftoppm.exe ^
  --dpi 250
```

Windows 必须提供 `pdftoppm.exe`，不接受 `.cmd` 或 `.bat` shell wrapper。也可通过
`RAG_PDF_RENDERER_PATH` 提供路径。当前输出：

```text
releases/<corpus-version>/
  documents/<document-id>.pdf-extraction.json
  qa/pdf-extraction-report.json
  qa/ocr-call-log.jsonl
  qa/pdf-render/<document-id>/page-001.png
  ...
```

OCR 默认关闭。本机构建首选本地 provider：

```text
RAG_OCR_ENABLED=true
RAG_OCR_PROVIDER=local-rapidocr
RAG_OCR_MODEL=rapidocr-3.9.2-ppocrv6-small-ch-onnx-cpu
```

必须从安装了 `rag-build` extra 的隔离环境启动命令。本地 provider 不需要 endpoint、
allowlist 或 API key，实际使用 RapidOCR 3.9.2、ONNX Runtime 1.23.2 和
`CPUExecutionProvider`，输入图像和识别结果均留在本机。

启用受控 HTTP JSON provider 时必须显式增加 `RAG_OCR_PROVIDER=http-json`，并配置：

```text
RAG_OCR_ENABLED=true
RAG_OCR_PROVIDER=http-json
RAG_OCR_ENDPOINT=https://ocr.example.com/v1/recognize
RAG_OCR_ALLOWED_HOSTS=ocr.example.com
RAG_OCR_API_KEY=<secret>
RAG_OCR_MODEL=<model>
RAG_OCR_TIMEOUT_SECONDS=60
```

endpoint host 必须在 allowlist 中；禁止 HTTP、URL 凭据、query、fragment 和 redirect。
API key 不得写入命令行、日志或 artifact。

完成全部页面目检后，使用解析摘要中的旧 extraction SHA-256 执行：

```text
python -m app.rag.offline.cli record-pdf-visual-review ^
  --source "D:\Users\Mrcury\Desktop\laibin RAG" ^
  --output build\rag-r01c ^
  --corpus-version laibin-rag-2026-07-29-v1 ^
  --document-id doc-94170623c8ba7c517d863337 ^
  --expected-extraction-sha256 <reviewed-extraction-sha256> ^
  --note all-pages-rendered-completely
```

该命令会再次验证 inventory、16 个页面相对路径、PNG hash 和像素尺寸。extraction 已变化、
页面被替换或复核已记录时拒绝执行。

首次无 OCR provider 的真实结果：

- 16/16 页渲染成功，均为 3347×1890；
- 12 页文本层为空，4 页稀疏，0 页可直接使用；
- 两次复核前 extraction manifest SHA-256 均为
  `48ae59ba2cdc8686c63a5c9f869448937ed7a268292473d59daf2629f02e8a19`；
- 两次构建的 16 个 PNG 逐字节一致；
- 视觉复核后 extraction manifest SHA-256 为
  `5e01a9aced4c4cc5239c937f7178bb394d9d1faac9b2788f469aef4559523a2e`；
- 视觉状态为 `SUCCEEDED`，OCR 待处理页数仍为 16，批状态为 `REQUIRES_REVIEW`。

2026-07-31 本地 OCR 重建结果：

- 16/16 页 `ocrStatus == SUCCEEDED`，`ocrPendingPageCount == 0`；
- 16/16 页视觉复核完成，`visualValidationPendingPageCount == 0`；
- 所有 OCR 区域包含文本、置信度和 `[0,1]` 归一化坐标；
- 第 15 页平均置信度约 `0.818`，保留 `OCR_LOW_CONFIDENCE`；低置信区域集中在证书
  缩略图，不能直接作为可信事实；
- extraction SHA-256 为
  `af605269166418bcc4fef13a0caa582a9edcbf4598e27b48a77d0a312e11ec2e`；
- extraction manifest SHA-256 为
  `d0665370d34977f03eb9fa7c3bad3dc2bc79f507d810426214be3d1acc8f2717`。

`REQUIRES_REVIEW` 仍是正确状态：本地 OCR 已完成，但产品卡片版面分区、重要企业事实
对照和低置信区域取舍要在规范化阶段执行。

## 8. 规范化和冻结 document

DOCX 与 PDF 的 extraction 必须位于同一 inventory 对应的 release，且视觉复核均已成功。
人工内容决定保存在版本化 profile 中：

```text
docs/agent/rag/profiles/laibin-rag-2026-07-29-v1.normalization-review.json
```

执行：

```text
python -m app.rag.offline.cli normalize ^
  --source "D:\Users\Mrcury\Desktop\laibin RAG" ^
  --output build\rag-r01d-release-2026-07-31 ^
  --corpus-version laibin-rag-2026-07-29-v1 ^
  --review-profile ..\docs\agent\rag\profiles\laibin-rag-2026-07-29-v1.normalization-review.json
```

命令在写入前重新验证 inventory、source/extraction/profile SHA-256、DOCX/PDF 视觉状态和
所有页面 PNG SHA-256。任何一项不一致时 fail-closed；已经存在规范化产物时拒绝覆盖。

正式输出：

```text
releases/<corpus-version>/
  corpus-manifest.json
  documents/<document-id>.document.json
  qa/normalization-review-profile.json
  qa/normalization-report.json
  qa/quality-report.json
```

2026-07-31 正式结果：10 份 document、30 页、108 个步骤、32 个参数、14 个
CCP/CPP、0 个 blocking issue，corpus 状态为 `VALIDATED`。详见
`normalization-content-review-report.md`。

## 9. 构建知识块、索引和固定检索评测

前置条件：RAG-01D release 为 `VALIDATED`，三个冻结摘要与第 8 节一致；本地模型目录
包含 `model_optimized.onnx`、`tokenizer.json` 和 `config.json`。所有命令在
`agent-service` 目录执行。

先从冻结输入生成新的、不可覆盖的隔离 release：

```text
python -m app.rag.offline.cli build-chunks ^
  --input-release build\rag-r01d-release-2026-07-31\releases\laibin-rag-2026-07-29-v1 ^
  --output build\rag-r02-release-2026-08-01 ^
  --corpus-version laibin-rag-2026-07-29-v1
```

再使用带 `fastembed==0.8.0` 的离线构建环境生成两个索引：

```text
python -m app.rag.offline.cli build-indexes ^
  --release build\rag-r02-release-2026-08-01\releases\laibin-rag-2026-07-29-v1 ^
  --model-path build\rag-models\fast-bge-small-zh-v1.5 ^
  --threads 2
```

最后运行 60～100 条固定 JSONL 评测；失败返回非零退出码且报告仍会保留失败详情：

```text
python -m app.rag.offline.cli evaluate-index ^
  --release build\rag-r02-release-2026-08-01\releases\laibin-rag-2026-07-29-v1 ^
  --model-path build\rag-models\fast-bge-small-zh-v1.5 ^
  --cases tests\rag\evaluation\laibin-rag-v1-retrieval.jsonl ^
  --threads 2
```

正式结果为 196 个 chunk 和 83/83 评测通过。输出位于 `chunks/`、`indexes/` 和
`qa/retrieval-evaluation-report.json`；corpus 仍保持 `VALIDATED`，不得手工设为
`ACTIVE`。完整摘要见 `chunking-index-and-evaluation-design.md`。

## 10. 安全规则

CLI 必须满足：

1. `--source` 和 `--output` 均显式提供。
2. source 必须是存在的普通目录。
3. source 根目录和扫描到的任何子项不得是符号链接、junction 或 reparse point。
4. 只读取 source 根目录内的普通文件，不读取系统其他目录。
5. output 不得与 source 相同，也不得互相包含。
6. output 必须位于项目目录内并被 Git 忽略。
7. corpus version 只能包含小写字母、数字、点、下划线和连字符，不能形成路径穿越。
8. 已存在的 release 不覆盖；更新材料必须使用新版本或先按明确流程处理未发布的失败构建。
9. inventory 不写入绝对 source 路径、凭据、原始二进制或文件内容。
10. 所有 JSON 使用 UTF-8 无 BOM。
11. PDF renderer 必须是显式普通可执行文件，不通过 shell wrapper 拼接命令。
12. OCR endpoint 必须是显式 HTTPS allowlist host，禁止跳转和环境代理继承。
13. OCR 请求不发送本机路径；调用日志不保存凭据、endpoint、图片、OCR 文本或响应正文。
14. 页面 PNG 只进入 Git 忽略 artifact，不进入运行时发布包。
15. 视觉复核必须验证页面 hash 和尺寸，并使用预期 extraction hash 防止并发追认。

## 11. 故障分类

| 错误码 | 含义 | 处理 |
| --- | --- | --- |
| `SOURCE_NOT_FOUND` | source 不存在 | 检查显式目录 |
| `SOURCE_NOT_DIRECTORY` | source 不是目录 | 指向材料根目录 |
| `SOURCE_LINK_FORBIDDEN` | source 或子项是链接/reparse point | 使用真实、独立的材料目录 |
| `SOURCE_READ_FAILED` | 无法读取目录 | 检查离线构建机权限 |
| `SOURCE_STAT_FAILED` | 无法检查文件类型 | 检查文件占用和权限 |
| `SOURCE_ENTRY_UNSUPPORTED` | 出现非普通文件 | 移出设备文件或异常条目 |
| `SOURCE_HASH_FAILED` | 文件读取或 hash 失败 | 检查文件完整性和占用 |
| `SOURCE_CHANGED_DURING_SCAN` | hash 期间源文件发生变化 | 停止编辑或同步后从零重跑 |
| `OUTPUT_LINK_FORBIDDEN` | output 是链接/reparse point | 使用真实 artifact 目录 |
| `OUTPUT_OVERLAPS_SOURCE` | source/output 重叠 | 分离原始材料和构建产物 |
| `OUTPUT_OUTSIDE_PROJECT` | output 位于项目外 | 使用已登记 artifact 目录 |
| `OUTPUT_NOT_GIT_IGNORED` | output 未被 Git 忽略 | 修正目录或 `.gitignore` 后重试 |
| `OUTPUT_WRITE_FAILED` | artifact 无法写入或原子落盘 | 检查磁盘、权限和文件占用 |
| `RELEASE_ALREADY_EXISTS` | 同版本 release 已存在 | 不覆盖；核实版本策略 |
| `INVENTORY_NOT_FOUND` | inventory 不存在 | 检查相对 artifact 路径 |
| `INVENTORY_TOO_LARGE` | inventory 异常过大 | 检查是否误指向原始文件 |
| `INVENTORY_INVALID` | JSON 或契约无效 | 重新生成，不手工猜测修补 |
| `INVENTORY_CHECKSUM_MISMATCH` | inventory 内容被改变 | 废弃该 artifact 并重新构建 |
| `DOCX_SOURCE_MISMATCH` | DOCX 与 inventory 记录不一致 | 停止构建，重新生成完整 inventory |
| `DOCX_PACKAGE_INVALID` | 文件不是有效或完整的 OOXML 包 | 核对源文件，不尝试自动修复 |
| `DOCX_ZIP_LIMIT_EXCEEDED` | ZIP 数量、体积或压缩比超限 | 隔离文件并人工检查 |
| `DOCX_ZIP_ENTRY_UNSAFE` | ZIP 条目重复、加密或路径不安全 | 拒绝解析，核验材料来源 |
| `DOCX_XML_INVALID` | XML 不完整或不符合安全解析要求 | 核验原始文件 |
| `DOCX_PARSE_FAILED` | 文档结构解析失败 | 保存失败原因并终止该批构建 |
| `DOCX_VISUAL_REVIEW_REQUIRED` | 结构解析成功但视觉关系未核验 | 在可用 Office/LibreOffice 环境完成逐页对照 |
| `DOCX_EXTRACTION_COMPARE_AND_SET_FAILED` | 人工目检后 extraction 已变化 | 重新检查最新渲染和 extraction |
| `DOCX_RENDER_PAGE_SEQUENCE_INVALID` | DOCX 渲染页缺失、重复或不连续 | 废弃本次复核并重新渲染 |
| `DOCX_RENDER_ARTIFACT_INVALID` | 页面 PNG 损坏或无法安全读取 | 重新渲染并检查 LibreOffice/Poppler |
| `PDF_ENCRYPTED` | PDF 已加密 | 获取甲方可审计的未加密现行材料 |
| `PDF_READ_FAILED` | PDF 结构无法严格读取 | 核验原始文件，不自动修复 |
| `PDF_PAGE_COUNT_INVALID` | PDF 页数异常 | 核对是否误选材料 |
| `PDF_RENDERER_NOT_FOUND` | 未配置可用 `pdftoppm` | 安装 Poppler 并提供显式可执行文件 |
| `PDF_RENDER_FAILED` | 单页渲染失败 | 检查 Poppler、文件完整性和错误页 |
| `PDF_RENDER_TIMEOUT` | 单页渲染超时 | 检查构建机资源，不跳过该页 |
| `PDF_RENDER_OUTPUT_INVALID` | PNG 损坏或超过限制 | 废弃该构建并检查 PDF/renderer |
| `OCR_CONFIG_INCOMPLETE` | OCR 已启用但配置不完整 | 补齐 endpoint、allowlist、key、model |
| `OCR_PROVIDER_INVALID` | provider 名称未登记 | 使用 `local-rapidocr` 或 `http-json` |
| `OCR_LOCAL_DEPENDENCY_MISSING` / `OCR_LOCAL_VERSION_MISMATCH` | 本地 OCR 依赖缺失或版本不符 | 在隔离环境重装固定版本 `rag-build` extra |
| `OCR_LOCAL_INFERENCE_FAILED` | RapidOCR 初始化或推理失败 | 保持待处理并检查模型、内存与运行环境 |
| `OCR_ENDPOINT_INVALID` | OCR endpoint 不符合 HTTPS 规则 | 使用受控 HTTPS endpoint |
| `OCR_HOST_NOT_ALLOWED` | OCR host 未列入白名单 | 审核后显式加入 allowlist |
| `OCR_REQUEST_FAILED` | provider 调用失败 | 保持页面待处理并检查 provider |
| `OCR_RESPONSE_INVALID` | provider 响应不符合契约 | 修复适配器或 provider，不猜测文本 |
| `PDF_EXTRACTION_COMPARE_AND_SET_FAILED` | 复核期间 extraction 已变化 | 重新检查最新页面和 extraction |
| `PDF_RENDER_ARTIFACT_CHECKSUM_MISMATCH` | 复核期间 PNG 已变化 | 废弃复核结论并重新渲染检查 |
| `NORMALIZATION_PROFILE_CHECKSUM_MISMATCH` | 内容复核配置被改动 | 重新复核并更新 profile SHA-256 |
| `NORMALIZATION_INVENTORY_CHANGED` / `NORMALIZATION_SOURCE_CHANGED` | 复核后材料或 inventory 已变化 | 对完整材料集重新处理和复核 |
| `NORMALIZATION_EXTRACTION_CHANGED` | 复核后 extraction 已变化 | 对最新 extraction 重新执行内容复核 |
| `NORMALIZATION_VISUAL_REVIEW_INCOMPLETE` | DOCX/PDF 视觉门禁未完成 | 完成全部页面复核后再规范化 |
| `NORMALIZATION_RENDER_ARTIFACT_CHANGED` | 已审核页面 PNG 发生变化 | 废弃旧结论并重新渲染检查 |
| `NORMALIZATION_REVIEW_INCOMPLETE` | 不是每份材料都有唯一复核决定 | 补全版本化 profile，不生成部分语料 |
| `NORMALIZATION_ARTIFACT_ALREADY_EXISTS` | 目标已有规范化产物 | 使用新的隔离构建目录，不覆盖旧产物 |
| `CHUNK_INPUT_NOT_VALIDATED` / `CHUNK_DOCUMENT_MANIFEST_MISMATCH` | 输入不是匹配的冻结规范化发布 | 停止构建并核对 RAG-01D 三个摘要 |
| `CHUNK_CONTENT_DUPLICATE` / `CHUNK_SOURCE_TEXT_EMPTY` | 切块重复或失去源证据 | 修正规则并使用新隔离目录整库重建 |
| `EMBEDDING_MODEL_INCOMPLETE` / `EMBEDDING_MODEL_LOAD_FAILED` | 本地模型不完整或无法加载 | 核对模型目录、摘要和构建依赖 |
| `INDEX_CHUNK_MANIFEST_MISMATCH` | chunk JSONL 与 manifest 不一致 | 废弃该候选发布，不局部修补 |
| `INDEX_ARTIFACT_ALREADY_EXISTS` | 目标已有索引 | 不覆盖；使用新的隔离候选或新 corpus version |
| `EVALUATION_SET_SIZE_INVALID` | 正式评测不在 60～100 条范围 | 补齐或收敛固定评测集后重跑 |
| `EVALUATION_REPORT_ALREADY_EXISTS` | 目标已有评测报告 | 不覆盖；废弃失败候选并从冻结输入重建 |
| `PUBLISH_RELEASE_*` / `RAG_EVALUATION_GATE_FAILED` | 发布包缺失、复制不一致、含链接或评测门禁失败 | 不切换；废弃损坏副本并核对冻结候选 |
| `PUBLISH_RELEASE_ALREADY_EXISTS` | 同版本正式 release 已存在但内容不同 | 禁止覆盖；使用新 corpus version |
| `PUBLISH_EXPECTED_CURRENT_REQUIRED` / `PUBLISH_CURRENT_VERSION_MISMATCH` | 更新未声明当前版本或 compare-and-set 不匹配 | 重新读取 `current.json` 并确认发布窗口 |
| `PUBLISH_LOCKED` | 另一发布/回滚持有锁，或上次异常留下待确认锁 | 确认没有发布进程后再由运维清理，不自动绕过 |
| `PUBLISH_POINTER_SWITCH_FAILED` | 指针替换前中断 | 旧 pointer 保持有效；验证已复制 release 后可用相同命令续跑 |
| `PUBLISH_AUDIT_COMMIT_FAILED` / `PUBLISH_POST_SWITCH_FAILED` | pointer 已替换但收尾失败 | 以 `current.json` 和 readiness 为准，停止自动重试并人工核对 |
| `ROLLBACK_TARGET_INVALID` / `ROLLBACK_TARGET_IS_CURRENT` | 回滚版本名非法或目标就是当前版本 | 选择正式 `releases/` 中不同的已验证版本 |
| `CONTRACT_INVALID` | CLI 参数或契约不符合规则 | 根据验证错误修正输入 |

构建/契约错误返回退出码 `2`；评测正常执行但门槛未通过返回 `3`；成功返回 `0`。

## 12. 测试

切片测试：

```text
cd agent-service
python -m pytest -q tests/rag
```

Agent Service 全量回归：

```text
cd agent-service
python -m pytest -q
```

真实 DOCX golden 回归需显式提供材料目录，普通 CI 不依赖工作区外文件：

```text
set LAIBIN_RAG_SOURCE_DIR=D:\Users\Mrcury\Desktop\laibin RAG
python -m pytest -q tests/rag/test_real_docx_golden.py
```

真实 PDF golden 还需显式提供 Poppler 可执行文件：

```text
set LAIBIN_RAG_SOURCE_DIR=D:\Users\Mrcury\Desktop\laibin RAG
set LAIBIN_RAG_PDF_RENDERER=C:\path\to\pdftoppm.exe
python -m pytest -q tests/rag/test_real_pdf_golden.py
```

该测试会重新渲染全部 16 页，耗时明显高于普通 fixture 测试。普通 CI 没有外部材料或
renderer 时显式跳过。

真实规范化发布 golden：

```text
set LAIBIN_RAG_NORMALIZED_RELEASE=D:\Laibin\LaibinSugarInventory\agent-service\build\rag-r01d-release-2026-07-31\releases\laibin-rag-2026-07-29-v1
python -m pytest -q tests/rag/test_real_normalization_golden.py
```

该测试重算全部 document 和 document manifest 摘要，并核对管理员角色、关键步骤参数、
控制点冲突、PDF 联系方式和低置信证书排除规则。

真实 RAG-02 发布 golden：

```text
set LAIBIN_RAG_INDEXED_RELEASE=D:\Laibin\LaibinSugarInventory\agent-service\build\rag-r02-release-2026-08-01\releases\laibin-rag-2026-07-29-v1
python -m pytest -q tests/rag/test_real_rag02_golden.py
```

该测试重算 chunk/index manifest，核对 196 个 chunk 的来源和行映射、词法索引文件摘要、
模型与融合版本、83 条正式评测摘要以及白砂糖控制点和礼品糖拆分 golden。

真实材料检查还应核对：

- `fileCount == 10`；
- `sourceTypeCounts == {"DOCX": 9, "PDF": 1}`；
- 清单中不存在 `D:\Users\...` 等绝对路径；
- source inventory 两次计算的文件 hash 和稳定 `documentId` 一致；
- 原始材料数量、大小和内容 hash 未被构建命令改变。

## 13. RAG-05 发布与回滚

详细协议和首次上线验收口径见 `release-switch-and-rollback-design.md`。以下命令均从
`agent-service` 目录执行，输出不包含物理路径。

### 13.1 正式候选预检

```text
.\.venv\Scripts\python.exe -m app.rag.offline.cli validate-release ^
  --release build/rag-r02-release-2026-08-01/releases/laibin-rag-2026-07-29-v1
```

2026-08-02 首次预检结果：10 份 document、196 个 chunk、83 条评测、23 个发布文件，全部
门禁通过。整个 release 的发布摘要为
`4a7525328af286d8ca7702ddc52c3f857244745bf0db080a84d426c6392456f6`。

### 13.2 首次发布

首次正式目录没有 `current.json`，不传 expected current version：

```text
.\.venv\Scripts\python.exe -m app.rag.offline.cli publish-release ^
  --release build/rag-r02-release-2026-08-01/releases/laibin-rag-2026-07-29-v1 ^
  --runtime-root ../deploy/simple/artifacts/rag ^
  --published-by <operator-id>
```

命令先复制到 staging、复检、原子改名并设置只读，最后原子替换 pointer。若 release 已完整复制但
pointer 替换前中断，以同一源 release 重跑会核对全树摘要并续跑激活，不覆盖已有目录。

### 13.3 后续版本发布

```text
.\.venv\Scripts\python.exe -m app.rag.offline.cli publish-release ^
  --release <new-complete-release> ^
  --runtime-root ../deploy/simple/artifacts/rag ^
  --published-by <operator-id> ^
  --expected-current-version <current-version>
```

`--expected-current-version` 是 compare-and-set 门禁。值缺失或与当前 pointer 不同均不切换。

### 13.4 回滚

```text
.\.venv\Scripts\python.exe -m app.rag.offline.cli rollback-release ^
  --runtime-root ../deploy/simple/artifacts/rag ^
  --target-version <previous-validated-version> ^
  --expected-current-version <current-failed-version> ^
  --published-by <operator-id>
```

回滚目标必须已经位于正式 `releases/`，并重新通过完整 artifact 和评测校验。回滚只切 pointer，
不删除或修改任何版本。

### 13.5 运行配置

Docker Compose 已配置：

```text
./artifacts/rag:/app/rag:ro
./artifacts/rag-model:/app/rag-model:ro
```

把经核验的本地 embedding 模型放入 `deploy/simple/artifacts/rag-model`，首次发布和新实例验证前
保持 `AGENT_RAG_ENABLED=false`。激活后设置：

```text
AGENT_RAG_ENABLED=true
AGENT_RAG_REQUIRED=true
AGENT_RAG_ROOT=/app/rag
AGENT_RAG_MODEL_PATH=/app/rag-model
AGENT_RAG_MODEL_NAME=BAAI/bge-small-zh-v1.5
```

指针切换不会使旧进程热加载。必须重启 Agent Service，并确认 health 中 RAG 为 `READY`、corpus
version 等于目标版本后，才将发布窗口标记成功。

### 13.6 2026-08-02 首次激活结果

- `publish-release` 返回 `SUCCEEDED`，`pointerChanged=true`、`releaseCopied=true`；
- 正式 `current.json` 激活 `laibin-rag-2026-07-29-v1`，发布审计状态为 `COMMITTED`；
- 正式复制后的 release 再次通过 10 份 document、196 个 chunk、83 条评测和全树摘要校验；
- 28091 独立 Agent 实例从正式根目录加载，health 为 `UP`、RAG 为 `READY`；
- 正式根目录同版本回滚返回 `ROLLBACK_TARGET_IS_CURRENT`，指针摘要和审计数量保持不变，无锁、
  pointer 临时文件或 prepared 审计残留；
- 真实候选的替换前中断/续跑测试及双有效 fixture 的 v2→v1 原子回滚测试通过；
- 当前只有一套甲方材料和一个真实 corpus version。测试中的 `test-corpus-v2` 只是与
  `test-corpus-v1` 内容相同、版本标识不同的协议测试 release，不得解释为业务材料版本。

完整发布哈希、测试结果和部署限制见 `rag05-release-record-2026-08-02.md`。

### 13.7 2026-08-03 当前状态

- 正式 v1 artifact、pointer 和审计再次校验通过；
- 28091 隔离验证实例已停止，当前 8091 无法使用已知验收密钥认证，在线 RAG 状态未验证；
- 真实 `deploy/simple/.env` 和部署模型目录尚不存在；
- RAG 源码、测试和文档尚未纳入 Git；
- 在完成源码集成、部署配置和当前实例验收前，不得把“历史首次激活通过”写成“当前已上线”。

完整复核见 `rag-current-state-audit-2026-08-03.md`。
