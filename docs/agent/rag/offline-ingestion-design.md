# 智能仓储 RAG 离线加工与发布设计

状态：`NORMALIZATION_COMPLETED / CORPUS_VALIDATED`
日期：2026-07-31
风险边界：静态知识只读，不修改库存、业务数据或原始材料

## 1. 设计目标

把甲方提供的 9 份 Word 工艺流程图和 1 份企业宣传册一次性加工为可审计、可引用、可回滚的静态知识库。

运行时只执行权限过滤、查询向量化、检索和回答，不承担以下工作：

- 打开或解析 DOCX；
- 渲染或 OCR PDF；
- 猜测文档版本、生效日期或替代关系；
- 临时切分材料；
- 临时生成文档 embedding；
- 修改原始材料；
- 根据工艺文档判断当前库存或具体批次是否合格。

## 2. 当前材料特征

### 2.1 Word 工艺流程图

- 共 9 份。
- 均为单页纵向流程图。
- 主体由文本框、形状、连接线和少量表格组成。
- 文本主要存在于 OOXML 文本框中，不是普通连续段落。
- Word/WPS 兼容表示会让普通解析器抽出重复文本。
- 工艺信息包含步骤、分支、原辅料、回用物、设备、温度、压力、时间、糖度、筛网和 CCP/CPP 等控制点。

### 2.2 企业宣传册

- 共 16 页，文件约 99 MB。
- 由 CorelDRAW 导出。
- 绝大多数页面没有可用文本层。
- 内容包括企业介绍、厂区、荣誉、产品介绍、礼品、销售网络和认证体系。
- 必须通过逐页渲染、中文 OCR 和版面识别提取。

## 3. 已确认数据治理规则

### 3.1 当前有效性

甲方已说明全部材料为当前现行版本。因此第一版统一登记：

```yaml
status: ACTIVE
sourceBasis: CLIENT_CONFIRMED_CURRENT
```

### 3.2 生效日期

材料未提供生效日期时：

- `effectiveFrom` 为 `null`；
- `effectiveTo` 为 `null`；
- 不从文件名、文件修改时间或正文内容推断；
- 不阻塞知识库发布。

### 3.3 暂不建设的治理能力

以下信息当前允许为空：

- `owner`；
- `reviewer`；
- `approvalStatus`；
- `supersedesDocumentId`；
- `supersededByDocumentId`；
- 更细粒度角色和组织权限。

数据结构必须保留这些字段，第一版不建设填写界面、审批流或自动状态迁移。

### 3.4 文档疑点

文档中的标题不一致、疑似错别字、控制点编号冲突和相似流程差异不阻塞处理，但必须遵守：

- 文件名作为第一版 `displayTitle` 和主要产品范围线索；
- 文档内部标题保存为 `sourceInternalTitle`；
- 不一致时增加 `TITLE_CONFLICT`；
- 原文保存为 `sourceText`；
- 格式统一后的内容保存为 `normalizedText`；
- 不静默修改温度、压力、时间、限值、设备型号或 CCP/CPP；
- 相似文档不得仅因文本相似而合并；
- 回答涉及疑点时应说明“材料原文如此”，不得替甲方作业务裁决。

## 4. 总体流程

```text
原始材料暂存
  -> 文件清单与 SHA-256
  -> 文件安全和结构检查
  -> DOCX 解析 / PDF 渲染与 OCR
  -> 版面与流程关系重建
  -> 规范化文档
  -> 知识块生成
  -> 技术质量检查
  -> 关键词索引与向量索引
  -> 固定评测集验证
  -> 生成版本 manifest
  -> 发布新知识库版本
  -> Agent 启动校验并加载
```

离线处理程序和运行时检索程序必须分离。运行时发布包不得依赖 Word、LibreOffice、OCR 模型或 PDF 渲染工具。文档 embedding 离线生成；运行时的 query embedding 仅用于检索当前问题，不会重新处理材料。

## 5. 离线加工阶段

### 5.1 材料发现

输入为明确指定的材料根目录，不递归读取系统其他目录。

输出至少包括：

- 相对路径；
- 文件名；
- 扩展名；
- 文件大小；
- SHA-256；
- 稳定 `documentId`；
- inventory builder 版本；
- 处理时间、处理状态和错误分类。

不把本机绝对路径写入普通用户可见知识块。

`RAG-01A` 的规范清单刻意不写入文件修改时间。文件修改时间既不是业务生效时间，也会使同一内容在复制到新的构建暂存目录后产生不必要的清单差异。源文件内容使用 SHA-256 审计；业务生效日期仍只来自甲方明确提供的信息。

当前实现：

- CLI：`python -m app.rag.offline.cli inventory`；
- 代码：`agent-service/app/rag/offline/source_inventory.py`；
- 清单：`<output>/releases/<corpus-version>/source-inventory.json`；
- 清单不含绝对源路径；
- 只扫描显式 source 根目录内的 `.docx` 和 `.pdf` 普通文件；
- 不支持的普通文件只登记相对路径并忽略，不进入材料计数；
- source 或子项为符号链接、junction 或 reparse point 时 fail-closed；
- source 与 output 重叠、output 位于项目外或 output 未被 Git 忽略时拒绝执行；
- 已存在的同版本 release 不覆盖。

### 5.2 DOCX 处理

DOCX 处理顺序：

1. 读取 ZIP/OOXML 包结构。
2. 提取正文、文本框、表格、页眉页脚和关系文件。
3. 识别 `AlternateContent` 等兼容结构。
4. 使用文本内容、对象关系和版面位置去除兼容层重复。
5. 获取流程节点、连接关系、分支、回用和旁路。
6. 提取步骤说明和结构化控制参数。
7. 渲染页面进行视觉核对。
8. 生成规范化流程对象和用于检索的派生文本。

不得直接使用 XML 出现顺序作为流程顺序。流程顺序优先来自步骤编号、连接关系和页面布局的共同结果。

`RAG-01B` 已实现 DOCX 结构解析：

- CLI：`python -m app.rag.offline.cli parse-docx`；
- 解析前按 inventory 的相对路径、文件名、类型、大小和 SHA-256 再次校验输入；
- 对 ZIP 条目数量、解压后大小、单条目大小、压缩比、重复路径、目录逃逸和加密条目执行 fail-closed 检查；
- XML 解析禁用 DTD、实体解析和网络访问，外部关系仅计数并忽略；
- 对 `mc:AlternateContent` 优先读取 `Choice`，跳过同一对象的 VML `Fallback`，同时兼容纯 VML 文档；
- 提取普通段落、表格、文本框、版面坐标、控制点标签、参数原文、单位和连接线几何信息；
- 连接线缺少可靠端点映射时不猜测，仅按明确步骤号形成待视觉核验的顺序候选；
- extraction 和 QA 报告不包含材料目录的绝对路径。

真实材料工程结果：

- 9/9 DOCX 生成 extraction，失败数为 0；
- 8 份文档使用 DrawingML `Choice` 与 VML `Fallback` 兼容表示，1 份为纯 VML；
- 两个独立输出目录的 extraction manifest SHA-256 均为
  `c51f92b83ca623068eaae0ae2d22f6d0d63f14649bb88df02f51eadc62c98180`；
- 全部文档因流程连接关系尚未完成渲染对照而保持 `REQUIRES_REVIEW`；
- 已识别并保留内部标题冲突、控制点标签冲突、纯 VML 和流程关系待核验等质量标记。

2026-07-31 已恢复 DOCX 渲染环境：

- 安装并验证 LibreOffice 26.2.5.2，Windows 无头入口固定为 `soffice.com`；
- 9/9 文档渲染成功，共 14 页，14/14 页完成人工视觉检查；
- 5 份文档的尾部空白第 2 页被识别并保留；
- 新构建页面与已目检页面逐页 SHA-256 比对差异为 0；
- extraction 保存 renderer/version、页数、像素尺寸、空白页和逐页 SHA-256；
- `visualValidationPendingCount == 0`，新的 extraction manifest SHA-256 为
  `db9643abff1803b76e520cd9a3df81b96a8013f2a6ccdb92b017b556a5837857`。

流程连接关系、控制点标签冲突和页内标题冲突继续保留到规范化阶段；视觉核验通过不代表
这些业务含义已经自动裁决。

### 5.3 PDF 处理

PDF 处理顺序：

1. 读取页数、文件元数据和现有文本层。
2. 每页渲染为 200～300 DPI 图像。
3. 执行简体中文 OCR。
4. 执行标题、正文、产品卡片、证书和地图等版面分区。
5. 保留页码和区域坐标，建立引用定位。
6. 对 OCR 低置信度文本执行人工技术复核。
7. 生成宣传册规范化文档和知识块。

宣传册内容只能进入企业与产品宣传知识域，不能成为工艺参数、质量判定、库存状态或生产执行的权威来源。

`RAG-01C` 已实现 PDF 预检、渲染和 OCR 适配边界：

- CLI：`python -m app.rag.offline.cli parse-pdf`；
- 使用 pypdf 严格读取页数、元数据、页面尺寸和现有文本层，拒绝加密、异常大小和异常页数；
- 使用显式 `pdftoppm` 可执行文件逐页渲染 250 DPI PNG，并限制超时、文件大小和像素数量；
- 每页保存页码、PDF 点尺寸、旋转、像素尺寸、文本层状态、PNG 相对路径和 SHA-256；
- OCR provider 默认关闭；可显式选择本地 `local-rapidocr`，也可在后续获得批准时选择
  受控 HTTPS `http-json` provider；
- 本地 provider 使用 RapidOCR 3.9.2、ONNX Runtime 1.23.2、PP-OCRv6 中文小模型和
  CPU execution provider，不需要 endpoint 或 API key；
- HTTP provider 必须使用无凭据的 HTTPS endpoint、显式 host allowlist、API key、
  模型和超时；
- provider 请求只发送文档 ID、页码和页面图像字节，不发送本机路径；
- 禁止 HTTP 跳转，限制请求图像和响应大小，响应严格校验文本、置信度、区域类型和归一化坐标；
- 调用日志只保存 provider 名、模型、页号、耗时、字节数、状态和错误码，不保存凭据、
  endpoint、页面文本或绝对路径；
- `record-pdf-visual-review` 以预期 extraction SHA-256 执行 compare-and-set，在再次验证
  16 个页面文件的路径、hash、尺寸后记录视觉复核。

真实宣传册结果：

- 16/16 页面以 250 DPI 成功渲染，均为 3347×1890；
- 逐页视觉检查确认方向正确、页面完整，无黑页、裁切、缺字形或渲染破损；
- 文本层分类为 12 页 `EMPTY`、4 页 `SPARSE`、0 页 `PRESENT`；
- 4 个稀疏页仅含少量或重复文本，均不能替代页面 OCR；
- 两次独立构建的复核前 extraction manifest SHA-256 均为
  `48ae59ba2cdc8686c63a5c9f869448937ed7a268292473d59daf2629f02e8a19`，
  16 个 PNG 逐字节一致；
- 视觉复核后的 extraction manifest SHA-256 为
  `5e01a9aced4c4cc5239c937f7178bb394d9d1faac9b2788f469aef4559523a2e`；
- 2026-07-31 从完整 inventory 使用本地 provider 重建，16/16 页 OCR 成功，
  `ocrPendingPageCount == 0`；
- 第 15 页平均置信度约为 `0.818`，保留 `OCR_LOW_CONFIDENCE`；低置信区域集中在证书
  缩略图，不自动提升为可信事实；
- 本地 OCR 构建的 extraction manifest SHA-256 为
  `d0665370d34977f03eb9fa7c3bad3dc2bc79f507d810426214be3d1acc8f2717`。

页面可成功渲染且 OCR 成功，仍不代表宣传内容已形成可检索语料。产品卡片版面分区、
低置信区域取舍和重要企业事实复核完成前，宣传册不得进入知识块或索引。

### 5.4 规范化

允许的规范化：

- Unicode 规范化；
- 全角和半角空格统一；
- 连续空白折叠；
- 明确的段落和列表恢复；
- 单位旁多余空格清理；
- 重复兼容文本消除；
- 页眉页脚和纯装饰文字标记。

禁止的规范化：

- 自动改写业务数值；
- 自动修正疑似错别字；
- 自动转换 CCP 与 CPP；
- 自动推断设备型号；
- 自动合并相似工艺；
- 用模型补写文档没有提供的步骤。

## 6. 技术质量检查

每次生成知识库前必须输出质量报告，至少检查：

- 输入文件是否全部处理；
- SHA-256 是否稳定；
- DOCX 是否存在未解析文本框；
- PDF 是否存在 OCR 空页；
- 是否出现大量异常乱码；
- 数字、单位和范围符号是否完整；
- 文档标题与内部标题是否冲突；
- 是否存在重复知识块；
- 每个知识块是否具有可用来源；
- 引用页码是否存在；
- 管理员权限元数据是否完整；
- 索引中的文档数和 manifest 是否一致。

质量标记不一定阻止发布。阻止条件由 `blocking` 字段明确表达。

建议第一版阻止发布的情况：

- 输入文件无法读取；
- 文档没有生成任何可检索内容；
- 关键数值与原文不一致；
- 知识块缺少文档引用；
- 索引 hash 与 manifest 不一致；
- 非管理员角色能够检索知识。

## 7. 知识库版本与发布

### 7.1 版本命名

建议使用：

```text
laibin-rag-YYYY-MM-DD-vN
```

首版建议：

```text
laibin-rag-2026-07-29-v1
```

版本日期表示知识库构建日期，不表示原始业务材料生效日期。

### 7.2 整库重建

收到更新材料后：

1. 复制或指定新的完整材料集合。
2. 重新计算全部文件 checksum。
3. 重新解析全部材料。
4. 重新生成全部知识块。
5. 重新生成全部检索索引。
6. 执行完整固定评测集。
7. 生成新的 corpus version。
8. 验证通过后原子切换。

第一版不做目录监听、热更新、单文件在线重建或运行时增量 embedding。

### 7.3 原子切换和回滚

新版本发布前不得覆盖当前版本。

推荐结构：

```text
knowledge-releases/
  laibin-rag-2026-07-29-v1/
  laibin-rag-2026-09-12-v2/
  current -> laibin-rag-2026-09-12-v2
```

Windows 部署可以使用配置项或版本文件替代符号链接。

运行时启动时校验：

- corpus version；
- manifest hash；
- document count；
- chunk count；
- embedding dimension；
- lexical index version；
- allowed role；
- schema version。

任一关键项不一致时 fail-closed，不自动回退到未验证的新索引。运维可以显式切回上一版本。

## 8. 运行时检索边界

### 8.1 第一版实现形态

当前语料规模较小，第一版优先采用 Agent Service 内部只读检索组件，不单独建设大型知识服务。

对模型暴露的逻辑能力统一命名为：

```text
search_approved_knowledge
```

该名称表达稳定的能力契约，不要求第一版必须成为独立网络 MCP Tool。未来需要多系统共享、独立扩缩容或细粒度权限时，可以保持输入输出契约不变，将实现迁移为 L0 MCP Tool 或独立知识服务。

### 8.2 专家边界

规划新增：

```text
knowledge_expert
```

约束：

- 只接收静态知识类目标。
- 只拥有 `search_approved_knowledge`。
- 不拥有库存、库位、托盘、化验、生产、权限或审计业务工具。
- 不执行任意文件读取。
- 不接收本机文件路径。
- 不读取原始 PDF/DOCX。
- 返回展示安全的知识证据，不直接决定业务执行。

### 8.3 静态知识与实时事实

静态知识问题：

- 工艺流程；
- 工艺步骤说明；
- 材料中记录的设备和参数；
- 企业介绍；
- 产品宣传；
- 销售网络和认证介绍。

实时事实问题继续使用现有工具：

- 当前库存；
- 当前库位；
- 当前托盘；
- 当前化验；
- 当前生产订单或批次状态；
- 当前角色权限；
- 当前操作日志。

“某批次是否合格”必须使用化验和质量事实，不能使用工艺文档替代。

混合目标第一版不得由模型自由组合。需要时应增加固定 GoalContract 和已登记配方。

## 9. 权限与安全

第一版 `allowedRoles` 固定为：

```json
["ADMIN", "SUPER_ADMIN"]
```

权限必须至少执行两次：

1. Java Agent 会话或消息入口确认当前用户为 `ADMIN` 或 `SUPER_ADMIN`。
2. Python 检索执行前再次检查受信角色上下文。

禁止仅通过前端菜单隐藏实现权限控制。

未来预留：

- `agent:knowledge:read`；
- `knowledgeDomain`；
- `organizationScope`；
- `plantScope`；
- `allowedRoles`；
- 文档级和知识块级 ACL。

普通回答和普通审计不得包含：

- 本机绝对路径；
- 原始文件二进制；
- embedding；
- 内部向量 ID；
- 原始检索分数；
- OCR 中间图像；
- 模型 Prompt；
- chain-of-thought。

## 10. 开发切片建议

| 切片 | 内容 | 完成条件 |
| --- | --- | --- |
| `RAG-00` | 设计、语料契约、日志和评测基线 | 文档评审完成 |
| `RAG-SEC-01` | Agent 管理员权限门禁 | Web、Java 和 Python 权限回归通过 |
| `RAG-01` | DOCX/PDF 离线解析与质量报告 | 10 份材料均生成规范化结果 |
| `RAG-02` | 知识块和离线索引 | 固定检索评测通过 |
| `RAG-03` | `knowledge_expert` 和只读检索能力 | 自动化权限、路由和引用测试通过 |
| `RAG-04` | Web 管理员问答展示与审计 | 真实管理员浏览器验收通过 |
| `RAG-05` | 整库更新、原子切换和回滚演练 | 新旧版本切换与回滚验证通过 |

每个切片开始和结束时都必须更新 `development-log.md`。
