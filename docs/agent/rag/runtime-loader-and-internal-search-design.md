# RAG-03A 运行时加载器与内部知识检索设计

状态：`IMPLEMENTED / ENGINEERING_TESTS_PASSED`
基线日期：2026-08-01
适用范围：来宾智能仓储 Python Agent Service 的静态知识只读检索

## 1. 目标与边界

RAG-03A 把 RAG-02 已验证的 corpus release 变成可由后续 `knowledge_expert`
调用的进程内只读能力。本切片交付加载、校验、检索、安全输出、健康状态和审计基础，
不交付对话路由、知识专家、Java 映射、Web 引用卡片或生产版本切换。

本阶段保持以下边界：

- `AGENT_RAG_ENABLED=false` 是默认值，现有 Agent 行为和依赖不受影响；
- 运行时不读取原始 DOCX/PDF，不执行 OCR、规范化、切块或文档 embedding；
- 运行时仅对用户查询生成 query embedding；
- 不新增任意文件读取、任意路径、任意 SQL、任意 HTTP 或仓储 MCP Tool；
- 不建立对外知识检索 HTTP API，能力先通过进程内对象提供给 RAG-03B；
- corpus 仍为 `VALIDATED`，不在本切片创建生产 `current.json` 或切换为 `ACTIVE`；
- 本切片的真实 release 验证在测试临时目录中生成候选 pointer，不改变正式 release。

## 2. 模块结构

```text
agent-service/app/rag/
  runtime/
    __init__.py
    contracts.py
    corpus_loader.py
    hybrid_retriever.py
    knowledge_tool.py
```

职责划分：

| 模块 | 职责 |
| --- | --- |
| `contracts.py` | `current.json`、readiness、内部工具输入和安全输出契约 |
| `corpus_loader.py` | pointer、路径、schema、hash、lineage、评测结果和 provider 契约校验 |
| `hybrid_retriever.py` | 只读 FTS5、query embedding、NumPy 向量与 RRF 融合 |
| `knowledge_tool.py` | 受信角色门禁、输入过滤、超时、审计指标和安全 evidence 映射 |

`runtime` 不导入 DOCX、PDF、OCR、normalizer 或 chunker。RAG-02 的离线评测可以复用
同一检索内核，避免离线评测与运行时算法漂移；离线构建函数不得被运行时调用。

## 3. 配置

| 环境变量 | 默认值 | 约束 |
| --- | --- | --- |
| `AGENT_RAG_ENABLED` | `false` | 关闭时不加载 RAG artifact 或 runtime optional dependency |
| `AGENT_RAG_REQUIRED` | `false` | 为 `true` 时必须同时启用；加载失败阻止进程就绪 |
| `AGENT_RAG_ROOT` | 空 | 启用时必填；只允许从该根目录读取 `current.json` 和 `releases/` |
| `AGENT_RAG_MODEL_PATH` | 空 | 启用 hybrid 查询时必填；必须是显式本地模型目录 |
| `AGENT_RAG_MODEL_NAME` | `BAAI/bge-small-zh-v1.5` | 必须与 index manifest 完全一致 |
| `AGENT_RAG_MODEL_THREADS` | `2` | 1～16 |
| `AGENT_RAG_QUERY_TIMEOUT_MS` | `5000` | 100～30000；超时结果必须为 `UNAVAILABLE` |
| `AGENT_RAG_MAX_EVIDENCE` | `5` | 1～10；调用方 `limit` 不能突破此上限 |

运行时依赖单独放入 `rag-runtime` optional extra，只包含检索所需的
`fastembed`、`numpy` 和 `onnxruntime`。RAG 关闭时普通安装不应因缺少这些依赖而失败；
生产启用前再让 Agent 镜像安装 `[run,rag-runtime]`。

## 4. `current.json` 契约

```json
{
  "schemaVersion": 1,
  "corpusId": "laibin-warehouse-knowledge",
  "corpusVersion": "laibin-rag-2026-07-29-v1",
  "releaseName": "laibin-rag-2026-07-29-v1",
  "corpusManifestSha256": "<sha256>",
  "indexManifestSha256": "<sha256>",
  "evaluationSetSha256": "<sha256>",
  "evaluationReportSha256": "<sha256>",
  "publishedAt": "2026-08-01T00:00:00+08:00",
  "publishedBy": "<受控发布者标识>"
}
```

约束：

- pointer 最大 64 KiB，必须是 UTF-8 JSON，未知字段拒绝；
- `releaseName` 必须是单个安全目录名，并与 `corpusVersion` 相同；
- release 必须解析到 `<root>/releases/<releaseName>` 内；
- root、pointer、`releases` 和 release 链路中的符号链接或 reparse point 拒绝；
- pointer 只保存相对 release 名，不保存本机绝对路径；
- 启用模式不存在绕过 pointer 的 `load arbitrary release` 生产入口；
- pointer 原子切换和正式发布由 RAG-05 实现。

## 5. 启动加载与 fail-closed

Agent Service 创建应用时只加载一次，不热加载。顺序如下：

1. 校验 RAG 配置组合；
2. 安全读取 `current.json`；
3. 校验 release 目录和必需 artifact；
4. 校验 corpus、chunk 和 index schema/version/lineage；
5. 重算 corpus manifest 文件、index manifest、评测报告、FTS5、向量矩阵和 row map 摘要；
6. 实际读取 `documents/` 并校验文档数、schema、状态、角色和 chunk 引用，再校验
   chunk 数、向量维度与行映射；
7. 校验 retrieval evaluation 为 `SUCCEEDED`、失败数为 0、index/evaluation hash 一致，
   且冻结指标达到 Gate C；
8. 从显式本地目录加载 query embedding provider，并要求 provider contract 与 index
   manifest 完全一致；
9. 构造不可变运行时快照，readiness 进入 `READY`。

接受 corpus 状态 `VALIDATED` 或 `ACTIVE`：`VALIDATED` 用于 RAG-03A/03B/03C 工程验收，
生产启用和正式 `ACTIVE` 切换仍由 RAG-05 的发布门禁控制。`BUILDING`、`FAILED` 和未知状态
均拒绝。

加载状态：

| 状态 | 含义 |
| --- | --- |
| `DISABLED` | RAG 显式关闭，未触碰 artifact |
| `READY` | pointer、release、评测和 provider 全部通过 |
| `UNAVAILABLE` | optional 模式启用，但配置、artifact 或 provider 无法加载 |

`AGENT_RAG_REQUIRED=true` 且不能达到 `READY` 时，应用创建直接失败，错误仅暴露稳定原因码，
不得把路径、堆栈、hash 或 provider 凭据写入对外响应。optional 模式保留现有 Agent 能力，
但知识检索返回 `UNAVAILABLE`，不能伪装为 `NO_DATA`。

## 6. 内部工具契约

模型可控输入只有：

```json
{
  "query": "多晶冰糖自然结晶需要多久",
  "knowledgeDomains": ["PROCESS"],
  "productQueries": ["多晶冰糖"],
  "limit": 5
}
```

输入约束：

- `query` 去除首尾空白后 1～500 字符；
- `knowledgeDomains` 仅允许契约枚举，最多 5 个且不重复；
- `productQueries` 最多 5 个，每项去除空白后 1～100 字符且不重复；
- `limit` 为 1～10，同时受 `AGENT_RAG_MAX_EVIDENCE` 限制；
- `roleCode`、`userId` 和 corpus version 由受信运行时单独注入；
- 输入契约禁止 role、path、index、SQL、filter expression、documentId 和 chunkId。

`productQueries` 只解析 corpus 已知 product family 的大小写无关精确名称。任何未知或歧义
产品返回 `INVALID_QUERY`，不得猜测产品 ID 或把过滤条件静默丢弃。

安全输出：

```json
{
  "status": "SUCCEEDED",
  "corpusVersion": "laibin-rag-2026-07-29-v1",
  "queryLabel": "多晶冰糖自然结晶需要多久",
  "knowledgeDomains": ["PROCESS"],
  "evidence": [
    {
      "evidenceId": "ev_...",
      "title": "多晶冰糖：自然结晶",
      "content": "……",
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

输出不得包含物理路径、`chunkId`、`documentId`、向量行号、原始分数、embedding、索引结构、
原始 JSON 或堆栈。`evidenceId` 是 corpus version 与 chunk 内容摘要派生的不透明引用标识，
不复用内部 ID。

## 7. 检索与故障语义

检索顺序：角色门禁 → 元数据候选过滤 → lexical top 20 → vector top 20 → RRF →
精确短语和控制点加权 → 最终 evidence 裁剪。

| 状态 | 运行时语义 |
| --- | --- |
| `SUCCEEDED` | 检索正常并找到证据 |
| `NO_DATA` | 检索链路正常，但受控范围内没有证据 |
| `DEGRADED` | query embedding 失败，但 lexical 仍有精确证据；必须有 warning |
| `UNAVAILABLE` | runtime 未就绪、超时、纯向量失败或降级时无可靠证据 |
| `FORBIDDEN` | 受信角色不是 `ADMIN`/`SUPER_ADMIN`，不执行索引查询 |
| `INVALID_QUERY` | 输入或产品过滤不符合契约，不执行检索 |

query embedding 调用异常被收敛为稳定错误码。hybrid 模式可以 lexical-only 降级；
纯 vector 模式不得降级为 `NO_DATA`。超时结果丢弃 evidence 并返回 `UNAVAILABLE`。

单条 evidence 内容最多 800 字符，总 evidence 内容最多 6000 字符；截断优先发生在句子或
换行边界，不能只留下不完整的数值和单位。第一版不增加 reranker。

## 8. 权限、审计与可观测性

- 角色门禁在任何 FTS5 或 query embedding 操作之前执行；
- corpus、chunk 和调用角色必须共同允许；第一版唯一允许角色为 `ADMIN`、`SUPER_ADMIN`；
- 审计日志只记录状态、角色、corpus version、query 长度、过滤数量、evidence 数和耗时；
- 不记录用户原始问题、evidence 正文、路径、内部 ID、hash、embedding 或凭据；
- 新增计数 `knowledge_search_total{status,role}`；
- 新增耗时 `knowledge_search_duration{status}`，单位毫秒；
- health dependency 只返回 `DISABLED`、`READY` 或 `UNAVAILABLE`；
- capabilities 可返回 enabled、required、state 和 corpusVersion，不返回路径或摘要。

## 9. 测试与完成门槛

自动化至少覆盖：

- RAG 关闭时不导入/加载 runtime optional dependency，现有 Agent 测试不变；
- required/disabled、缺失 root、缺失/损坏 pointer、路径逃逸和 reparse point；
- corpus/index/evaluation hash、schema、lineage、数量、角色、模型和维度不一致；
- ADMIN、SUPER_ADMIN 成功，普通角色在索引调用前 `FORBIDDEN`；
- 输入长度、枚举、产品过滤和 limit；
- lexical/vector/hybrid、`NO_DATA`、provider 降级、不可用和超时；
- 对输出做字段递归检查，确认无路径、内部 ID、分数和 embedding；
- 正式 RAG-02 release 的候选 pointer 加载、黄金问题和 P95 性能验证；
- Agent Service 全量回归。

完成标准沿用 `implementation-plan.md` 的 RAG-03A 门槛。本切片通过后只标记
`ENGINEERING_TESTS_PASSED`；它不代表 RAG 已对用户开放，也不代表 corpus 已发布为 ACTIVE。
