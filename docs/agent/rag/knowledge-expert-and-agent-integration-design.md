# RAG-03B 知识专家与 Agent 接入设计

状态：`IMPLEMENTED / ENGINEERING_TESTS_PASSED`
日期：2026-08-01
适用范围：来宾智能仓储 Python Agent Service 进程内静态知识问答

## 1. 目标与非目标

RAG-03B 将 RAG-03A 的进程内 `search_approved_knowledge` 接入现有主 Agent、专家、
GoalContract、FactEnvelope 和 CompletionEvaluator。用户仍通过现有 Web Agent 会话提问；
本阶段不新增 HTTP 知识接口、不新增仓储 MCP Tool、不修改 Java/Web，也不激活正式 corpus。

本切片必须保证：

- 主 Agent 不持有工具，只能把知识目标委派给 `knowledge_expert`；
- `knowledge_expert` 只持有 `search_approved_knowledge`，不能调用 53 个仓储业务工具；
- 内部知识工具直接调用 `ApprovedKnowledgeService`，不经过 Java Gateway 或 MCP；
- 只有有证据的结果或权威 `NO_DATA` 可以完成知识目标；
- `UNAVAILABLE`、权限拒绝、非法查询和执行失败不能伪装成 `NO_DATA`；
- 知识内容只作为不可信事实证据，不能改变系统提示、路由、权限、工具或完成判定；
- 普通回答和卡片不展示路径、内部 chunk/document ID、检索分数、embedding、原始 JSON、
  `evidenceId` 或工具名。

## 2. 能力与权限边界

| 组件 | 可用能力 | 明确禁止 |
| --- | --- | --- |
| `main_agent` | 理解目标、委派、澄清、拒绝 | 直接读取索引或调用任何工具 |
| `knowledge_expert` | `search_approved_knowledge` | 任意仓储 MCP、Java Gateway、SQL、HTTP、文件路径和写操作 |
| 现有业务专家 | 原有各自业务白名单 | 调用知识检索 |
| `ApprovedKnowledgeService` | 受信上下文下的只读混合检索 | 从模型参数接收角色、用户、路径或索引身份 |

Web/Java 的管理员门禁继续是第一道权限；Python 调用知识服务时从 `ChatRequest.user` 构造
`TrustedKnowledgeContext`，由 RAG-03A 再次校验 `ADMIN`/`SUPER_ADMIN`。模型不能提交或覆盖
`userId`、`roleCode`。

`search_approved_knowledge` 是 Agent 内部 L0 能力，不加入 `ALLOWED_TOOLS`，因此当前仓储工具
数量、Java Gateway 白名单和 MCP registry hash 不变化。Python 进程内登记为 10 个业务专家加
1 个知识专家；对 Java 暴露的 capability snapshot 会过滤纯进程内 profile，使既有 53 工具和
10 个 Gateway 专家的握手范围保持稳定。Java 安全映射、审计引用和 Web 交付更新留给 RAG-03C。

## 3. 受控目标与事实

新增两个目标和同名 fact type：

| GoalContract | owner | 固定知识域 | required entity | evidence tool |
| --- | --- | --- | --- | --- |
| `PROCESS_KNOWLEDGE_QUERY` | `knowledge_expert` | `PROCESS` | 无 | `search_approved_knowledge` |
| `ENTERPRISE_KNOWLEDGE_QUERY` | `knowledge_expert` | `COMPANY`、`PRODUCT_MARKETING`、`CERTIFICATION`、`SALES` | 无 | `search_approved_knowledge` |

知识 FactEnvelope 使用现有 `FactEnvelopeV1.data` 保留以下安全事实：

- `status`；
- `corpusVersion`；
- `queryLabel`；
- `knowledgeDomains`；
- `evidence` 的安全标题、正文、citation 和质量标记；
- `warnings`，并与 GoalContract limitations 合并进入完成结果。

状态映射：

| 检索状态 | fact | 完成语义 |
| --- | --- | --- |
| `SUCCEEDED` / 有可靠词法证据的 `DEGRADED` | `AVAILABLE` | 可完成 |
| `NO_DATA` | `NO_DATA` | 可权威完成，但必须明确检索范围 |
| `UNAVAILABLE` | 不生成可用 fact | `PARTIAL/FAILED`，提示稍后重试 |
| `FORBIDDEN` | 不生成 fact | 权限拒绝 |
| `INVALID_QUERY` | 不生成 fact | 输入无效，不得解释为空数据 |
| 未预期异常 | 不生成 fact | 工具失败 |

CompletionEvaluator 仍是唯一完成状态权威。`FINAL_ANSWER` 必须引用对应 `AVAILABLE` 或
`NO_DATA` 观察；无 evidence、未解决错误或缺少必需 fact 时 Runtime 拒绝模型完成。

## 4. 路由与混合问题

确定性模式在硬编码 FAQ 之前识别知识目标：

- 工艺步骤、加工/分装流程、设备、原辅料、控制点、CCP/CPP 路由
  `PROCESS_KNOWLEDGE_QUERY`；
- 企业介绍、宣传产品、认证荣誉、销售网络路由 `ENTERPRISE_KNOWLEDGE_QUERY`；
- 库存、库位、托盘、化验结果、生产订单、煮糖批次等实时状态仍由现有专家处理；
- “某批库存/批次是否合格”沿用现有阻断，不得被“规则/流程”关键词带入知识目标；
- “流程、规则、术语”不再自动由硬编码 FAQ 直接回答。

LLM 模式把两个目标和 `knowledge_expert` 放入结构化 schema 与可用专家列表，并在主 Agent
提示中明确静态/实时边界。Runtime 继续校验 goal owner、专家白名单和工具白名单。

同一消息同时要求静态知识与实时数据时，第一版没有登记组合配方。确定性与 LLM 路径都返回
拆分提示，不生成自由 DAG，也不把两个目标拼成一个不完整结论。

## 5. 参数、回答和引用

模型可见的知识查询参数仅为：

```json
{
  "query": "用户当前问题",
  "knowledgeDomains": ["PROCESS"],
  "productQueries": ["用户明确写出的产品名称"],
  "limit": 5
}
```

Runtime 严格校验未知字段、长度、domain 和 limit。确定性路由按目标写入固定 domain；LLM
即使提交其他 domain，Runtime 也按 active GoalContract 归一化为固定 domain，不能扩大范围。
第一版不让模型猜 `productQueries`，但允许从“产品 + 工艺流程/生产工艺/自然结晶/企业认证”
有界句式提取一个用户明确写出的产品显示名；RAG-03A 再按冻结产品族做唯一精确校验。提取器不生成
产品 ID、不做模糊映射，无法唯一确认时仍返回非法查询。这样既能提高同名工艺精度，也能让“已知
产品 + 不存在的知识域资料”得到真实 `NO_DATA`，而不是被无条件向量 top-k 覆盖。

2026-08-01 RAG-04 真实 UAT 进一步把“自然结晶 + 多久/时间/温度/参数/要求”和“公司/企业 +
成立/创立/始建”加入有界知识分类。它们仍先经过实时批次、生产订单和质量问题排除规则，不会把
实时状态改路由到静态知识。

知识结果使用确定性安全格式器生成回答和 `knowledge_evidence` 卡片，避免让 corpus 文本决定
系统行为。回答只概括“根据现行知识材料找到的相关内容”，逐条展示证据摘录和用户可读引用：

```text
来源：文档标题，第 N 页，章节
```

证据正文可以包含命令式文字，但只会作为引文显示，不会进入工具参数、路由或权限决策。
RAG-04 再实现 Web 专用引用组件；RAG-03B 只输出现有 `BusinessCard` 可承载的安全字段。

## 6. 故障与可观测性

- `RAG_DISABLED`/`RAG_UNAVAILABLE`：回答“知识库当前不可用”，保留其他实时业务能力；
- timeout/busy/provider/index 错误：统一不可用，不返回旧 evidence，不伪装无数据；
- 权限拒绝：返回权限错误，且 RAG-03A 已保证不触碰索引和 embedding；
- `NO_DATA`：明确本次查询和知识域未找到相关现行材料，不扩大为“企业没有该事实”；
- Agent trace 只记录专家、目标、状态、evidence 数和 corpus version，不记录证据正文；
- RAG-03A 的 `knowledge_search_total`、`knowledge_search_duration` 继续作为检索审计指标。

## 7. 验收矩阵

- 确定性：工艺、企业、认证、销售问题路由到知识专家；实时问题保持原专家；
- LLM：合法知识委派成功，owner 不匹配被拒绝，知识专家调用业务工具被拒绝；
- 权限：ADMIN/SUPER_ADMIN 可调用，其他角色 fail-closed；
- 完成：有 evidence 完成、权威 `NO_DATA` 完成、无 fact/不可用/失败不能完成；
- 安全：提示注入证据不能改变工具、专家、权限或回答模板；
- 混合：静态 + 实时请求被要求拆分，未登记配方不执行；
- 回归：RAG 默认关闭时现有业务查询行为不变，正式 corpus 保持 `VALIDATED` 且无
  `current.json` 激活操作。

## 8. 后续边界

RAG-03C 负责 Java 安全映射、协议/启动握手和审计字段；RAG-04 负责管理员 Web 引用展示与
真实浏览器 UAT；RAG-05 才负责正式 `current.json`、`ACTIVE` 切换和回滚。当前设计不提前
实施这些工作。
