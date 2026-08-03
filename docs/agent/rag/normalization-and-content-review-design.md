# RAG-01D 规范化与内容复核设计

状态：`IMPLEMENTED_AND_VALIDATED`

日期：2026-07-31

## 1. 目标

将同一 corpus version 下已完成 checksum、DOCX 视觉核验和 PDF OCR/视觉核验的
10 份 extraction，转换为可追溯、可评测、但尚未切分和建索引的规范化 document。

本切片不实施 chunk、embedding、检索、Agent runtime 加载或发布切换。

## 2. 输入门禁

normalizer 只接受一个完整 release，且必须同时满足：

1. `source-inventory.json` 契约和 inventory SHA-256 有效。
2. inventory 中 9 份 DOCX 均存在且仅存在一份对应 extraction。
3. DOCX extraction 自身 checksum 有效，`visualValidation.status == SUCCEEDED`，且
   逐页渲染件的路径、尺寸和 SHA-256 仍一致。
4. inventory 中 1 份 PDF 存在且仅存在一份对应 extraction。
5. PDF extraction 自身 checksum 有效，16/16 页 OCR 为 `SUCCEEDED`，视觉核验
   为 `SUCCEEDED`，渲染件未变化。
6. extraction 的 `sourceSha256`、`documentId`、`sourceFileName` 与 inventory 逐项一致。
7. 显式内容复核 profile 与 corpus version、inventory SHA-256、每份 source SHA-256
   和 extraction SHA-256 绑定。

任一条不满足时 fail-closed，不生成部分规范化语料。

## 3. 内容复核 profile

内容判断不写死在 normalizer 代码中，而是使用版本化、可审计的 JSON profile。
profile 至少包含：

- corpus version 和 inventory SHA-256；
- 每份文档的 source/extraction SHA-256；
- `displayTitle`、`sourceInternalTitle`、`versionLabel` 和 `productFamilies`；
- DOCX 流程关系候选的核验结论；
- 标题、控制点和适用范围疑点的保留/阻断级别；
- PDF 每页的业务主题、知识域、页标题和区域类型；
- OCR 低置信区域的保留、排除或阻断结论；
- 每个决定的简短理由，但不写入本机路径、人员姓名或未提供的审批信息。

profile 必须通过 Pydantic 契约校验、使用 UTF-8 无 BOM，并计算稳定
SHA-256。normalizer 输出必须记录 profile SHA-256。

## 4. DOCX 规范化规则

### 4.1 标题和适用范围

- `displayTitle` 使用去扩展名的甲方文件名。
- 页内标题单独保存为 `sourceInternalTitle`，不覆盖文件名。
- 不一致时保留 `TITLE_CONFLICT`，不推测冲突原因。
- `productFamilies` 只能来自文件名或页内明确标题；泛化的“糖分装”
  不自动扩展为具体产品。
- 文件名末尾数字可作为 `versionLabel`，但不视为生效日期。

### 4.2 流程步骤

- 只使用 extraction 中有明确 `stepNo` 的流程节点生成 `ProcessStepContract`。
- `sourceText`、数值、范围、单位和 CCP/CPP 原标签完整保留。
- `normalizedText` 只执行 Unicode、空白、列表和单位周边空格的确定性整理，
  不改写业务措辞。
- 前后继只使用 profile 已确认的关系；未确认关系不写入。
- 连接线、步骤号和页面位置不一致时保留阻断质量项，不由算法补全。

## 5. PDF 规范化规则

- 企业宣传册作为一份 `PRODUCT_BROCHURE` document，不拆成多份伪造原文档。
- 每页保留页码、OCR 原文、规范化文本、质量标记和受控页面板块。
- 页面板块只能使用白名单类型：企业、产品宣传、荣誉认证、销售网络、封面/
  目录或装饰。
- OCR 区域默认只是 `BODY`；只有根据渲染页确认后才可改为
  `TITLE`、`PRODUCT_CARD`、`CERTIFICATE`、`MAP` 或 `DECORATION`。
- 装饰文字和不可读证书缩略图可以排除出 `normalizedText`，但必须保留原
  OCR 和排除理由。
- 企业名称、地址、联系方式、资质、产品名称和宣传数字必须与渲染页完成
  人工对照后才可进入规范化文本。
- 第 15 页的证书缩略图低置信区域不作为可独立检索的证书事实，
  保留 `OCR_LOW_CONFIDENCE` 和技术复核说明。

## 6. 质量分类

阻止语料冻结：

- 缺失任一 inventory 文件或 extraction；
- checksum、页数、渲染件或 profile 绑定不一致；
- DOCX 视觉核验或 PDF OCR/视觉核验未完成；
- 一份文档没有任何可用正文/流程步骤；
- 数值、单位、控制点或页码在规范化前后不一致；
- 未完成的流程关系、PDF 板块归属或重要事实对照。

保留为非阻塞 warning：

- `TITLE_CONFLICT`、已记录原文的 `CONTROL_POINT_LABEL_CONFLICT`；
- 纯 VML、兼容层去重等解析实现信息；
- 已明确排除不可读缩略图文字的 `OCR_LOW_CONFIDENCE`。

按甲方既定决策，材料未提供生效日期、owner、reviewer 或 approval 信息时字段保持
`null`，不生成缺失告警，也不阻塞处理；契约仅预留后续扩展空间。

warning 必须进入 document 和 `quality-report.json`，不得静默丢弃。

## 7. 输出契约

```text
releases/<corpus-version>/
  corpus-manifest.json
  documents/<document-id>.document.json
  qa/normalization-review-profile.json
  qa/normalization-report.json
  qa/quality-report.json
```

- 规范化 document 使用 `DocumentContract`，并记录稳定 `documentSha256`。
- manifest 记录 document 数、document manifest SHA-256、parser/normalizer 版本和
  profile SHA-256。
- `blockingIssueCount == 0` 时 corpus 可为 `VALIDATED`；本切片不设为
  `ACTIVE`，不执行发布切换。
- 任一阻断项存在时命令 fail-closed，不发布部分 document、manifest 或 QA 结果，
  且不得进入 chunk/index 构建。

## 8. 确定性与安全

- 不使用 LLM 自动改写原文、补流程、命名产品或修正 OCR。
- 相同 inventory、extraction、profile 和 normalizer 版本必须产生相同
  document SHA-256 和 document manifest SHA-256；时间戳不进入稳定摘要。
- 输出不包含绝对路径、页面图像、原始二进制、凭据或未白名单的角色。
- normalizer 只在 Git 忽略的 release 内写临时文件，原子更新，不覆盖
  已存在的规范化产物。
- 对更新材料必须从完整 inventory 和新 profile 重建，不在运行时增量修补。

## 9. 验收门槛

- 10/10 inventory 文件有且仅有一份规范化 document。
- 每份 document 的 source/document checksum 可重算。
- DOCX 所有有编号步骤保留原文、页码、参数和控制点。
- PDF 16/16 页均有受控主题、页标题、来源文本和复核结论。
- 关键数值与单位前后一致率 100%，引用页码有效率 100%。
- 标题冲突、范围疑点和 OCR 低置信不被消失。
- 自动化契约、路径、checksum、不可覆盖、数值保真和真实材料 golden 通过。

## 10. 实施结果

2026-07-31 已按本设计完成正式发布：

```text
agent-service/build/rag-r01d-release-2026-07-31/
  releases/laibin-rag-2026-07-29-v1/
```

- 10/10 document，30 页，108 个有编号流程步骤；
- 32 个结构化参数、14 个 CCP/CPP 标记、8 个表格设备字段；
- 5 个源文档尾随空白页显式保留为 `SOURCE_BLANK_PAGE`；
- PDF 16/16 页均有受控板块、知识域、规范化正文和复核说明；
- `blockingIssueCount == 0`，8 个已复核 warning 全部保留；
- corpus 状态为 `VALIDATED`，尚未生成 chunk/index，也未设为 `ACTIVE`。

稳定摘要：

- inventory SHA-256：
  `cefd802a6bbee06ee211c45b1638c57a7c5f03458a5db843ee0ec3649abfd574`
- review profile SHA-256：
  `44da044e3669d1f8585be70f61708029421fb5a3e765c30c88aaafd8179e05ee`
- document manifest SHA-256：
  `4fcf6769da4d725e778fc0cc2193dd25b2822f1c11a4931415847b4d45fd7b2f`

逐文档结论和保留质量项见
[normalization-content-review-report.md](normalization-content-review-report.md)。
