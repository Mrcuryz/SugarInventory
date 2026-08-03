# 智能仓储 RAG 语料与索引契约

状态：`RAG-02_CHUNK_AND_INDEX_CONTRACTS_VALIDATED`
日期：2026-07-29
目标：定义离线处理结果和运行时检索之间的稳定边界

## 1. 原则

- 原始材料、规范化文档、知识块和索引是四个不同层次。
- 运行时只读取知识块和索引。
- 所有文档和知识块必须具有稳定 ID、checksum 和来源定位。
- 数值、单位和控制点必须保留在同一个知识块内。
- 权限过滤必须在候选证据进入模型上下文前完成。
- 工具失败、索引缺失和无检索结果必须保持不同语义。
- 任何知识回答都必须能够回溯到具体文档和页码或步骤。

## 2. 目录契约

建议离线成品结构：

```text
corpus/
  corpus-manifest.json
  documents/
    <document-id>.document.json
  chunks/
    knowledge-chunks.jsonl
  index/
    index-manifest.json
    lexical/
    vector/
  qa/
    normalization-review-profile.json
    normalization-report.json
    quality-report.json
  evaluation/
    rag-evaluation-corpus.jsonl
    rag-evaluation-result.json
```

原始材料单独归档，不要求进入 Agent 运行时发布包。

`RAG-01A` 已在 `agent-service/app/rag/contracts.py` 实现 corpus、document、process step、chunk、index、QA 和 source inventory 的 Pydantic v2 基础契约。契约默认拒绝未知字段，时间必须带时区，checksum 必须为小写 64 位 SHA-256，`allowedRoles` 当前只接受 `ADMIN` 和 `SUPER_ADMIN`。后续切片可以增加向后兼容的可选字段；改变现有字段语义或删除字段必须提升 `schemaVersion`。

`RAG-01B` 在同一模块增加 DOCX extraction 中间契约，包括包信息、文本块、流程节点、
版面几何、控制参数、连接线、关系候选、提取指标和视觉核验状态。该层用于保存可审计的
OOXML 提取事实，不是最终 `DocumentContract`：任何 `visualValidation.status` 不是
`PASSED` 的 extraction 都不得直接进入规范化语料冻结或检索索引。

DOCX extraction 的确定性摘要不包含 `parsedAt`。同一 inventory、同一文件内容和同一
parser 版本必须生成相同的文档 extraction SHA-256 与 batch manifest SHA-256。

`RAG-01C` 增加 PDF package、页级 extraction、文本层状态、OCR 状态、归一化区域坐标、
页面渲染信息、提取指标、视觉核验和批报告契约。页面图像只保存在 Git 忽略的 QA
artifact 中；契约仅保存 release 内相对路径与 SHA-256。

PDF extraction 与 DOCX extraction 一样属于中间事实。以下任一条件存在时不得转成最终
`DocumentContract`：

- `visualValidation.status` 不是 `SUCCEEDED`；
- 需要 OCR 的页面状态不是 `SUCCEEDED`；
- 页面存在未复核的低置信度 OCR；
- 产品卡片、证书、地图或正文区域归属未确认；
- 重要企业事实与页面图像未完成人工复核。

人工视觉复核会改变 extraction 和 batch manifest SHA-256，因为核验状态、时间和说明也是
审计事实。确定性比较使用复核前 extraction；复核落盘使用预期 hash compare-and-set，
防止对已变化的页面或 extraction 追认。

## 3. Corpus Manifest

示例：

```json
{
  "schemaVersion": 1,
  "corpusId": "laibin-warehouse-knowledge",
  "corpusVersion": "laibin-rag-2026-07-29-v1",
  "status": "VALIDATED",
  "sourceBasis": "CLIENT_CONFIRMED_CURRENT",
  "builtAt": "2026-07-29T00:00:00+08:00",
  "timezone": "Asia/Shanghai",
  "allowedRoles": ["ADMIN", "SUPER_ADMIN"],
  "documentCount": 10,
  "chunkCount": 0,
  "inventorySha256": "",
  "reviewProfileSha256": "",
  "documentManifestSha256": "",
  "chunkManifestSha256": "",
  "lexicalIndexSha256": "",
  "vectorIndexSha256": "",
  "embedding": {
    "provider": "",
    "model": "",
    "dimension": 0,
    "normalization": ""
  },
  "parserVersions": {
    "docx": "",
    "pdf": "",
    "ocr": "",
    "normalizer": "",
    "chunker": ""
  }
}
```

`builtAt` 是构建时间，不是业务生效时间。

## 4. Document Contract

每份原始材料生成一个规范化文档。

```json
{
  "schemaVersion": 1,
  "documentId": "proc-single-crystal-yellow-ice-sugar-24-12",
  "displayTitle": "单晶黄冰糖工艺流程图24.12",
  "sourceInternalTitle": "咖啡调糖生产工艺流程",
  "sourceFileName": "单晶黄冰糖工艺流程图24.12.docx",
  "sourceType": "DOCX",
  "documentType": "PROCESS_FLOW",
  "knowledgeDomain": "PROCESS",
  "language": "zh-CN",
  "status": "ACTIVE",
  "sourceBasis": "CLIENT_CONFIRMED_CURRENT",
  "versionLabel": "24.12",
  "effectiveFrom": null,
  "effectiveTo": null,
  "owner": null,
  "reviewer": null,
  "approvalStatus": null,
  "allowedRoles": ["ADMIN", "SUPER_ADMIN"],
  "supersedesDocumentId": null,
  "supersededByDocumentId": null,
  "sourceSha256": "",
  "productFamilies": ["单晶黄冰糖"],
  "qualityFlags": ["TITLE_CONFLICT"],
  "pages": [],
  "process": {
    "steps": [],
    "branches": [],
    "controlPoints": []
  },
  "extraction": {
    "parserVersion": "",
    "processedAt": "",
    "status": "SUCCEEDED",
    "sourceExtractionSha256": "",
    "reviewProfileSha256": "",
    "warnings": []
  },
  "documentSha256": ""
}
```

### 4.1 文档类型

第一版允许：

- `PROCESS_FLOW`；
- `COMPANY_PROFILE`；
- `PRODUCT_BROCHURE`；
- `HONOR_AND_CERTIFICATION`；
- `SALES_NETWORK`。

### 4.2 知识域

第一版允许：

- `PROCESS`；
- `COMPANY`；
- `PRODUCT_MARKETING`；
- `CERTIFICATION`；
- `SALES`。

知识域用于路由、过滤和回答限制，不等同于数据库业务模块。

### 4.3 质量标记

第一版建议：

- `TITLE_CONFLICT`；
- `POSSIBLE_TYPO`；
- `CONTROL_POINT_LABEL_CONFLICT`；
- `POSSIBLE_SCOPE_AMBIGUITY`；
- `SIMILAR_DOCUMENT_WITH_DIFFERENT_LIMITS`；
- `OCR_LOW_CONFIDENCE`；
- `FLOW_RELATION_REQUIRES_REVIEW`；
- `PARAMETER_LABEL_POSSIBLE_TYPO`。

按甲方既定决策，未提供生效日期、owner、reviewer 和 approval 时对应字段保持 `null`，
不生成缺失标记。未来启用治理流程时可在不改变主契约的前提下增加校验。

### 4.4 Page Contract

页面除 `pageNumber`、`title`、`sourceText` 和 `normalizedText` 外，还包含：

- `sectionKind`：受控页面板块；
- `knowledgeDomains`：该页适用的一个或多个知识域；
- `productFamilies`：仅填写页面明确涉及的产品；
- `excludedSourceTexts`：经视觉复核后不进入规范化正文的 OCR 片段；
- `reviewNotes`：人工取舍理由；
- `qualityFlags`：页级质量标记。

`sourceText` 保留 OCR/抽取原文，`normalizedText` 保存经视觉对照确认后的检索正文；
两者不得互相覆盖。

## 5. Process Step Contract

```json
{
  "stepId": "step-09",
  "stepNo": "9",
  "name": "金属探测",
  "sourceText": "9、金属探测（CCP1）",
  "normalizedText": "9、金属探测（CCP1）",
  "predecessorStepIds": ["step-08"],
  "successorStepIds": ["step-10"],
  "inputs": [],
  "outputs": [],
  "equipment": ["金属探测器"],
  "parameters": [],
  "controlPoint": {
    "type": "CCP",
    "label": "CCP1",
    "sourceLabel": "CCP1"
  },
  "controlPoints": [
    {
      "type": "CCP",
      "label": "CCP1",
      "sourceLabel": "CCP1"
    }
  ],
  "pageNumber": 1,
  "sourceRegion": null,
  "qualityFlags": []
}
```

`controlPoint` 为兼容单标签消费者保留；`controlPoints` 是权威多值字段。源文同一步骤出现
多个标签时，`controlPoint` 为 `null`，所有原标签写入 `controlPoints`，并保留冲突质量项。

### 5.1 Parameter Contract

```json
{
  "name": "浓缩温度",
  "sourceText": "112-120℃",
  "valueType": "RANGE",
  "minValue": 112,
  "maxValue": 120,
  "unit": "℃",
  "normalizationStatus": "EXACT",
  "qualityFlags": []
}
```

要求：

- `sourceText` 必须保留。
- 不把 `Mpa` 自动改成 `MPa` 后覆盖原文。
- 数值解析失败时保留原文并标记，不允许模型补全。
- 范围上下界和单位必须进入同一知识块。

## 6. Chunk Contract

每行一个 JSON 对象。

```json
{
  "schemaVersion": 1,
  "chunkId": "proc-single-crystal-yellow-ice-sugar-24-12/step/09",
  "documentId": "proc-single-crystal-yellow-ice-sugar-24-12",
  "chunkType": "PROCESS_STEP",
  "knowledgeDomain": "PROCESS",
  "title": "单晶黄冰糖：步骤9 搅拌上色",
  "content": "单晶黄冰糖工艺的步骤9为搅拌上色……",
  "sourceText": "9.搅拌染色（CPP1）",
  "productFamilies": ["单晶黄冰糖"],
  "keywords": ["单晶黄冰糖", "搅拌", "上色", "CPP1"],
  "pageNumber": 1,
  "stepNo": "9",
  "controlPointLabels": ["CPP1"],
  "qualityFlags": [],
  "status": "ACTIVE",
  "allowedRoles": ["ADMIN", "SUPER_ADMIN"],
  "citation": {
    "documentTitle": "单晶黄冰糖工艺流程图24.12",
    "pageNumber": 1,
    "sectionLabel": "步骤9"
  },
  "sourceSha256": "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
  "contentSha256": "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb",
  "embeddingRef": "vector/row/42"
}
```

正式物理文件为 `chunks/chunks.jsonl`。`embeddingRef` 在最终 chunk 中必须为
`vector/row/<n>`；`contentSha256` 排除构建时间和向量行号，因此相同审核输入与切块版本
可得到相同内容摘要。

### 6.1 Chunk 类型

- `FLOW_OVERVIEW`：完整流程步骤概览；
- `PROCESS_STEP`：单个步骤及其前后关系；
- `CONTROL_POINT`：CCP/CPP、设备、限值和操作频率；
- `MATERIAL_BRANCH`：原料、辅料、包装材料、副产物和回用；
- `COMPANY_SECTION`：企业介绍和厂区能力；
- `PRODUCT_SECTION`：宣传册产品介绍；
- `CERTIFICATION_SECTION`：荣誉与认证；
- `SALES_SECTION`：销售网络。

### 6.2 切分规则

- 不使用固定字数机械切分流程图。
- 每个工艺步骤默认独立成块。
- 过短步骤可以与描述合并，但不得跨越产品或流程版本。
- 控制点和参数可以形成独立块，同时保留所属步骤。
- `FLOW_OVERVIEW` 应包含有序步骤名称，但不重复所有长描述。
- 宣传册按页面业务板块切分，不把不同产品卡片合并。
- 知识块必须自包含产品名称、文档名称或明确上下文。
- 不把数值与单位拆到不同知识块。
- 不把标题冲突、OCR 低置信度等质量标记从知识块中丢失。

## 7. 索引契约

`chunks/chunk-manifest.json` 固定记录 document manifest 摘要、chunker 版本、分类计数、
每个 chunk 的内容摘要和向量行引用。`indexes/index-manifest.json` 固定记录：

- document/chunk manifest SHA-256；
- embedding provider、provider 版本、模型、维度、归一化、最大 token 和模型目录摘要；
- lexical/vector 物理实现、版本、文件摘要和条目数；
- 融合算法版本、RRF 参数、候选数、证据类型加权和检索前角色门禁；
- 排除构建时间后的 `indexManifestSha256`。

RAG-02 正式实现为 SQLite FTS5 中文单字/双字与受控词索引、NumPy L2 `float32`
向量矩阵，以及 `rrf-control-aware/1.0.2` 融合。

### 7.1 混合检索

第一版同时建设：

- 关键词/倒排索引；
- 中文语义向量索引；
- 元数据过滤；
- 结果融合；
- 可选的小规模重排。

关键词检索负责：

- 产品名称；
- 工艺步骤；
- CCP/CPP；
- 设备型号；
- 温度、压力、时间和限值；
- 精确术语。

向量检索负责：

- 自然语言改写；
- 同义表达；
- 用户未使用原文术语时的召回。

不得只依赖向量相似度回答精确数值问题。

文档知识块 embedding 在离线构建阶段完成。运行时只为当前用户问题生成 query embedding，不重新解析材料、不执行 OCR，也不补生成文档向量。

### 7.2 检索流程

```text
用户问题
  -> 目标分类
  -> 管理员权限检查
  -> 文档状态、角色和元数据候选过滤
  -> knowledgeDomain 过滤
  -> lexical topK
  -> vector topK
  -> 结果融合
  -> 精确短语与证据类型加权
  -> 最大 3～5 个安全证据块
  -> FactEnvelope
  -> 回答和引用
```

### 7.3 权限过滤

权限和状态过滤必须在证据进入模型上下文前完成：

```text
status == ACTIVE
AND currentRole IN allowedRoles
```

未来增加组织范围时，也必须在召回阶段过滤，不能只在回答阶段删除。

## 8. 检索输入输出契约

逻辑能力：

```text
search_approved_knowledge
```

建议输入：

```json
{
  "query": "多晶冰糖自然结晶需要多久",
  "knowledgeDomains": ["PROCESS"],
  "productQueries": ["多晶冰糖"],
  "limit": 5
}
```

约束：

- `query` 长度必须受限。
- `knowledgeDomains` 只能使用白名单。
- `limit` 第一版最大 10，默认 5。
- 当前角色由受信运行时注入，模型不能提交或修改。
- 不接受文件路径、SQL、任意过滤表达式或任意索引名称。

建议输出：

```json
{
  "status": "SUCCEEDED",
  "corpusVersion": "laibin-rag-2026-07-29-v1",
  "queryLabel": "多晶冰糖自然结晶时间",
  "evidence": [
    {
      "title": "多晶冰糖：自然结晶",
      "content": "将浓缩糖液放入有结晶架的冰糖桶，在室温下自然结晶7天。",
      "citation": {
        "documentTitle": "多晶冰糖工艺流程图25.8",
        "pageNumber": 1,
        "sectionLabel": "步骤4"
      },
      "qualityFlags": []
    }
  ],
  "warnings": []
}
```

普通模型观察中不得包含：

- 向量 ID；
- embedding；
- 绝对文件路径；
- 原始检索分数；
- 索引物理目录；
- OCR 中间文件；
- 未经白名单允许的元数据。

## 9. 引用契约

每条最终知识结论至少关联一条证据。

引用展示至少包含：

- 文档业务标题；
- 页码；
- 有步骤时显示步骤号或章节名；
- corpus version 保留在审计中，普通用户界面可不直接展示。

引用不得仅显示：

- `chunkId`；
- `documentId`；
- 文件系统路径；
- SHA-256；
- 向量数据库主键。

无证据时不得生成确定性知识答案。应返回“当前材料中没有找到明确依据”。

## 10. 版本兼容

Agent 启动时必须校验 `schemaVersion`。不支持的 schema 应阻止加载。

知识库升级时：

- 允许新增可选字段；
- 删除或改变字段语义必须提升 schemaVersion；
- embedding 模型或维度变化必须整库重建；
- chunker 规则变化必须整库重建并执行完整评测；
- 旧版本保留到新版本完成 UAT 和回滚演练后再决定归档。
