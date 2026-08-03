# RAG-02 知识块、索引与检索评测设计

状态：`COMPLETED / VALIDATED_NOT_ACTIVE`

日期：2026-08-01
适用 corpus：`laibin-rag-2026-07-29-v1`

## 1. 目标与边界

以 RAG-01D 正式冻结的 10 份 `VALIDATED` document 为唯一业务输入，生成：

- 稳定、自包含、可引用的知识块；
- SQLite FTS5 中文词法索引；
- 本地中文向量索引；
- 词法、向量和 RRF 混合检索工程实现；
- 60～100 条固定检索评测及机器可读结果。

本阶段不接入 Agent、Java 或 Web，不创建 `knowledge_expert`，不修改业务数据库，不设置
`current.json`，也不把 corpus 切换为 `ACTIVE`。RAG-02 通过后 corpus 仍为
`VALIDATED`，等待 RAG-03 运行时加载与权限验收。

## 2. 输入冻结

正式输入：

```text
agent-service/build/rag-r01d-release-2026-07-31/
  releases/laibin-rag-2026-07-29-v1/
```

构建前必须重算并验证：

| 输入 | SHA-256 |
| --- | --- |
| source inventory | `cefd802a6bbee06ee211c45b1638c57a7c5f03458a5db843ee0ec3649abfd574` |
| content review profile | `44da044e3669d1f8585be70f61708029421fb5a3e765c30c88aaafd8179e05ee` |
| document manifest | `4fcf6769da4d725e778fc0cc2193dd25b2822f1c11a4931415847b4d45fd7b2f` |

还必须逐份验证 `DocumentContract`、`documentSha256`、管理员角色、状态和 document 数。
任何不一致时 fail-closed，不生成部分 chunk 或索引。

## 3. Embedding 实施决定

第一版使用离线、本地 CPU provider：

| 项目 | 值 |
| --- | --- |
| provider | `fastembed`（本地文件、禁用联网） |
| library | `fastembed==0.8.0` |
| model | `BAAI/bge-small-zh-v1.5` |
| dimension | `512` |
| normalization | `L2` |
| runtime | ONNX Runtime CPU |
| model package SHA-256 | `bf023219b6029148fddf764d248808816c0ca1f107f058231bb1ae0fa526f83f` |
| model artifact stable SHA-256 | `6b919dcf92ce43b2640c4d27a806d14a632f0659c1cc283abd51058ffed842bc` |

选择依据：该模型在 FastEmbed 0.8.0 支持清单中明确登记为中文文本 embedding，使用
512 token 输入和 512 维向量；模型来源登记为 Qdrant 的 ONNX 发布包。构建环境已完成
两条中文文本的本地加载冒烟验证，输出向量 L2 范数为 1。

约束：

- 模型文件只放在 Git 忽略构建缓存，不提交仓库、不进入普通用户输出；
- 构建命令必须显式提供本地模型目录，不允许运行时自动联网下载；
- manifest 记录 provider、library、model、dimension、normalization 和模型目录稳定摘要；
- 模型、维度、归一化或模型文件摘要变化时必须整库重建；
- 文档向量只在离线构建生成，RAG-03 运行时只能生成 query embedding；
- 单测使用显式 fake provider，不能把伪向量写入正式 release。

参考：

- FastEmbed 官方仓库：<https://github.com/qdrant/fastembed>
- FastEmbed 官方文档：<https://qdrant.tech/documentation/fastembed/>
- BGE 中文模型卡：<https://huggingface.co/BAAI/bge-small-zh-v1.5>

## 4. 知识块规则

### 4.1 通用规则

- chunk 不跨 document、产品、流程版本、页码或步骤。
- `chunkId` 根据 document、类型和来源位置确定，不使用随机数或时间戳。
- `contentSha256` 不包含构建时间和 embedding 行号。
- 每个 chunk 必须有业务标题、完整正文、原文、知识域、角色、状态和 citation。
- 质量标记从 document、page、step 和 parameter 向 chunk 合并，不静默丢弃。
- 任何数值、上下界和单位必须位于同一 chunk。
- 相同 document/type/content 的完全重复 chunk 阻断构建。
- 所有 chunk 只允许 `ADMIN`、`SUPER_ADMIN`，状态为 `ACTIVE`。

### 4.2 工艺文档

每份工艺 document 生成：

1. 一个 `FLOW_OVERVIEW`：按已复核前后继顺序列出全部编号步骤；
2. 每个编号步骤一个 `PROCESS_STEP`：包含产品、步骤号、名称、原文、前后步骤、设备、
   参数和控制点；
3. 有参数、设备或 CCP/CPP 的步骤增加一个 `CONTROL_POINT`，保证精确术语和限值召回；
4. 每个未编号 material/storage/byproduct 分支生成一个 `MATERIAL_BRANCH`。

`PROCESS_STEP` 和 `CONTROL_POINT` 可以共享事实，但 chunk 类型与用途不同；不得生成两条
类型、标题和正文均相同的重复块。

### 4.3 宣传册

- 封面、目录、企业简介、设施和结束页生成 `COMPANY_SECTION`；
- 产品介绍生成 `PRODUCT_SECTION`；
- 荣誉和体系认证生成 `CERTIFICATION_SECTION`；
- 销售网络生成 `SALES_SECTION`；
- 第 12、13 页礼品糖必须按产品卡片拆分，不把四个产品合并成一个检索块；
- 第 3 页按已复核段落拆分企业、认证、产品、销售和联系信息；
- 第 15 页低置信证书编号仍不得进入 chunk；
- 空白页和没有 `normalizedText` 的页面不生成知识块。

## 5. 中文词法索引

使用 Python 标准库 SQLite FTS5，不引入独立检索服务。

### 5.1 Token 规则

离线为 title/content/keywords 生成独立检索 token：

- 中文连续文本生成单字和相邻双字 token；
- 产品名、文档标题、步骤名、参数名和受控业务词保留完整 token；
- 英文、数字、型号、`CCP1`、`CPP1`、`112-120℃`、`0.06～0.09Mpa` 等精确表达保留；
- 常见全角/半角标点和英文字母大小写只在 token 层规范化，不改写展示正文；
- query 使用同一 tokenizer，不接受任意 FTS 表达式。

### 5.2 SQLite 内容

- 普通 metadata 表保存 chunk、citation、角色、状态、知识域、产品和质量标记；
- FTS5 表只保存受控 token 列和不可检索的 `chunkId`；
- 固定插入顺序、page size、schema version 和 tokenizer 配置；
- 建库后关闭 journal、执行 `VACUUM`，计算文件 SHA-256；
- 不写绝对路径、构建时间、embedding 或原始 OCR 页面。

## 6. 向量索引

- 按 `chunkId` 排序后批量生成文档 embedding；
- 输出 L2 归一化 `float32` NumPy 二维数组；
- 行映射 JSONL 只包含行号、chunkId 和 content SHA-256；
- 每行向量维度必须为 512，范数误差不超过 `1e-4`，禁止 NaN/Inf；
- `vectors.npy`、row map 和模型摘要共同生成 vector index SHA-256；
- chunk 中 `embeddingRef` 使用 `vector/row/<n>`，不暴露文件系统路径。

当前语料规模使用 NumPy 矩阵点积，不引入向量数据库或 ANN。

## 7. 检索与融合

离线评测器实现三种可独立测试的模式：

- `LEXICAL`：FTS5 BM25，分数转为越大越优；
- `VECTOR`：query embedding 与文档矩阵点积；
- `HYBRID`：两路 top 20 使用 RRF，`k=60`。

过滤顺序：

```text
受信角色 + ACTIVE 状态
  -> knowledgeDomain
  -> productFamilies
  -> lexical/vector 候选
  -> RRF
  -> 精确产品、步骤、CCP/CPP 和完整短语加权
  -> 最终 topK（默认5，最大10）
```

非 `ADMIN`/`SUPER_ADMIN` 直接返回 `FORBIDDEN`，不得先检索再删除。索引/provider 损坏
返回 `UNAVAILABLE`；向量不可用但词法索引有效时只能显式 `DEGRADED`，不能当作
`NO_DATA`。

## 8. 固定评测

评测集使用 UTF-8 JSONL，固定 60～100 条，至少覆盖：

- 精确流程和步骤顺序；
- 温度、时间、压力、糖度、质量和金属限值；
- 产品、设备、CCP/CPP；
- 红糖/赤砂糖、单晶体/单晶黄冰糖等相似文档区分；
- 文件名/内部标题冲突；
- 宣传册企业、产品、荣誉、销售、认证和联系方式；
- 材料未覆盖与实时业务边界；
- ADMIN/SUPER_ADMIN 与非管理员权限。

检索案例必须声明 expected document、可选 expected chunk type、required facts、禁止文档/
事实和 topK。实时边界案例记录为后续 RAG-03 路由评测，不用静态索引伪造实时答案。

门槛：

- Recall@5 ≥ 90%；
- 精确产品/控制点 Recall@5 = 100%；
- 精确数值正确文档 Top3 = 100%；
- 关键数值和单位事实准确率 = 100%；
- 相似文档禁止项命中数 = 0；
- 非管理员证据数 = 0；
- 无来源 chunk = 0；
- lexical/vector/hybrid 本地阶段分别记录 p50/p95，hybrid p95 ≤ 300 ms。

评测失败时保存失败案例并保持 corpus `VALIDATED` 但 `evaluation.status == FAILED`；
不得进入 RAG-03。

## 9. 输出与不可覆盖

RAG-01D 输入 release 保持只读。RAG-02 在新的 Git 忽略目录生成独立 release：

```text
releases/<corpus-version>/
  source-inventory.json
  corpus-manifest.json
  documents/<document-id>.document.json
  chunks/chunks.jsonl
  chunks/chunk-manifest.json
  indexes/index-manifest.json
  indexes/lexical/chunks.sqlite3
  indexes/vector/embeddings.npy
  indexes/vector/row-map.jsonl
  qa/normalization-review-profile.json
  qa/normalization-report.json
  qa/quality-report.json
  qa/chunking-report.json
  qa/retrieval-evaluation-report.json
```

固定评测集作为代码审查和回归资产保存在
`agent-service/tests/rag/evaluation/laibin-rag-v1-retrieval.jsonl`，不复制进正式 release；
release 通过 `evaluationSetSha256` 绑定该评测集。

- 不复制 DOCX/PDF extraction、OCR 日志或页面 PNG 到索引 release；
- 每个阶段使用新 staging 目录和原子发布；
- 不覆盖 RAG-01D release；
- chunk/index/evaluation 目标存在时拒绝再次写入；
- corpus manifest 只在预期前一阶段摘要匹配时原子更新。

## 10. 验收门槛

- RAG-01D 三个冻结摘要重算通过；
- 10/10 document 生成完整、有来源、无重复 chunk；
- chunk、SQLite、NumPy、row map 和 index manifest 摘要可重算；
- 模型目录摘要、维度和归一化与 manifest 一致；
- 固定评测达到第 8 节全部门槛；
- 正式真实 release golden 通过；
- RAG 与 Agent Service 全量回归通过；
- 文档和追加式开发日志完成；
- UTF-8 无 BOM、乱码扫描和 `git diff --check` 通过。

## 11. 实施结果

唯一正式离线发布：

```text
agent-service/build/rag-r02-release-2026-08-01/
  releases/laibin-rag-2026-07-29-v1/
```

构建结果：

| 项目 | 结果 |
| --- | --- |
| document | 10 |
| chunk | 196 |
| `FLOW_OVERVIEW` / `PROCESS_STEP` / `CONTROL_POINT` / `MATERIAL_BRANCH` | 9 / 108 / 26 / 23 |
| `COMPANY_SECTION` / `PRODUCT_SECTION` / `CERTIFICATION_SECTION` / `SALES_SECTION` | 9 / 16 / 3 / 2 |
| duplicate / empty source | 0 / 0 |
| chunk manifest SHA-256 | `fff89ef6e272dff99ff1bf94fe5688186ae1847b517f83d7ab90537758337893` |
| lexical index SHA-256 | `22a74909d0e5eb7afbc75f3dc3815e4352bc02efd55f1665a47cab389a47c13d` |
| vector index SHA-256 | `80f5562500bf0a12cac4d88e5886e7c3601a8ae544f8c74df45f657db673ec32` |
| index manifest SHA-256 | `2a36b5a8d15f129453d5041562b1b56bc19dfab49a795b82e4da7a105b94fd83` |
| fusion | `rrf-control-aware/1.0.2` |

固定评测共 83 条，83/83 通过：Recall@5、精确产品/控制点、数值 Top3、数值证据和
权限边界均为 100%，相似文档禁止项命中数和无来源 chunk 均为 0；本次正式构建的
hybrid P95 为 8.71 ms。评测集 SHA-256 为
`658dca252ecc1dff0ef7a24a61c08e62d0b6a5d960b286ff69c6fb0c0c1bc16d`。

RAG-02 没有接入 Agent runtime，也没有建立 `current.json` 或切换 `ACTIVE`；下一阶段从
RAG-03A 的只读 runtime loader 和内部检索契约开始。
