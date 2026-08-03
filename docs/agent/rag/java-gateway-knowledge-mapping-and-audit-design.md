# RAG-03C Java Gateway 知识映射与审计设计

状态：`ENGINEERING_TESTS_PASSED`
日期：2026-08-01
适用范围：Python Agent Service 到 Java Agent Gateway 的知识回答、卡片、SSE 和审计链路

## 1. 目标与边界

本切片让 RAG-03B 已生成的知识回答安全通过 Java Gateway，并使非流式和流式调用都能追溯
知识库版本。它不新增 HTTP 知识接口、Java/MCP 工具或业务数据库写入能力，也不启用正式
`current.json`。

核心边界：

- Java 只从当前 `LoginUser` 和已绑定 `AgentSession` 构造 Python 用户上下文，不读取前端提交的
  `roleCode`、`userId`、权限或 scope；
- Python 的 `search_approved_knowledge` 继续是进程内 L0 能力，不加入 Java Gateway/MCP 的
  53 工具白名单；
- 普通回答只允许业务文本与安全来源，知识卡片使用显式白名单，不使用通用“未知字段透传”；
- 审计只记录专家、目标、知识域、检索状态、corpus version、证据数量、错误类别和耗时；
- 审计、日志和响应均不得记录查询原文、evidence 正文、文件路径、内部 ID、检索分数、
  embedding、Prompt、token、Authorization 或原始 JSON。

## 2. 受信请求上下文

Java `RuntimeRoutingAgentGatewayService.buildPythonRequest` 只允许以下来源：

| Python 字段 | 受信来源 | 前端是否可覆盖 |
| --- | --- | --- |
| `user.userId` | `LoginUser.user.id` | 否 |
| `user.name` | `LoginUser.user.name` | 否 |
| `user.roleCode` | `LoginUser.user.roleCode` | 否 |
| `user.permissionCodes` | Spring Security authorities | 否 |
| `agentSessionId` | Java 已校验的本人有效会话 | 否 |
| `client.requestId/traceId` | Java 生成 | 否 |

`pageContext` 仍只允许页面路径、路由名、页面标题和受控 HITL 结构，任何伪造身份字段必须被丢弃。
Controller、Service 和 Python 检索层继续分别执行管理员门禁，Java 不把前端隐藏入口当作授权。

## 3. Python 到 Java 的审计摘要

Python `reviewTrace.knowledgeAudit` 固定为展示不可见的安全摘要：

```json
{
  "targetAgent": "knowledge_expert",
  "goalType": "PROCESS_KNOWLEDGE_QUERY",
  "status": "SUCCEEDED",
  "corpusVersion": "laibin-rag-2026-07-29-v1",
  "knowledgeDomains": ["PROCESS"],
  "evidenceCount": 2,
  "degraded": false
}
```

失败时 `corpusVersion` 可以为空；`status` 必须保持 `UNAVAILABLE`、`FORBIDDEN`、
`INVALID_QUERY` 或 `ERROR`，不得改写为 `NO_DATA`。该摘要不得包含 evidence、query、路径、
内部引用或工具参数。

非流式响应通过 `PythonAgentChatResponseDTO.reviewTrace` 读取；流式响应继续使用内部 `audit` SSE
事件，Java 消费后不向 Web 转发。两条链路必须生成同一语义的审计记录。

## 4. 知识卡片显式白名单

仅当 `cardType=knowledge_evidence` 时启用以下白名单：

| 层级 | 允许字段 | 约束 |
| --- | --- | --- |
| card | `cardType`、`title`、`fields` | `cardType` 固定；不接收 options、prompt 或未知字段 |
| field | `label`、`value` | label 只允许“内容”“来源”；最多 2 项 |
| 内容 value | 文本 | 最长 800 字；不得含内部控制词、路径、凭据或堆栈 |
| 来源 value | 文本 | 最长 500 字；只表示文档标题、页码和章节 |

Java 必须重建新对象，不能把 Python Map 原样返回。若新增 `documentId`、`chunkId`、
`evidenceId`、`score`、`path`、`embedding` 或其他未知键，非流式映射直接丢弃；流式映射也只
输出同一白名单结果。现有非知识业务卡片继续沿用当前通用安全适配器，本切片不改变其协议。

## 5. 审计模型

复用现有 `agent_tool_audit_log`，不新增迁移：

- `tool_name=knowledge_search`：表示进程内知识检索审计能力，不是 MCP Tool；
- `arguments_summary`：只含 `targetAgent`、`goalType`、`knowledgeDomains`；
- `request_summary`：固定 `source=approved_corpus`，不记录用户问题；
- `response_summary`：只含 `status`、`corpusVersion`、`evidenceCount`、`degraded`；
- `result_code`：`SUCCEEDED/DEGRADED/NO_DATA` 映射成功，其余映射失败；
- `error_code`：只接受安全错误枚举；
- `duration_ms`：使用本次 Python 问答链路耗时。

每次知识请求还保留现有 `agent_runtime` 和 `agent_handoff` 审计。Java 日志只记录固定错误码，
不得把响应、evidence 或异常堆栈写入日志。

## 6. 错误与降级映射

| Python 状态/错误 | Java 用户回答 | 审计结果 |
| --- | --- | --- |
| `SUCCEEDED` | 知识回答和引用卡片 | 成功 |
| `DEGRADED` | 回答保留降级说明 | 成功，`degraded=true` |
| `NO_DATA` | 明确“本次知识库范围无证据” | 成功，不伪装系统失败 |
| `RAG_UNAVAILABLE` | 知识库暂不可用，实时查询不受影响 | 失败，可重试 |
| `UPSTREAM_PERMISSION_DENIED` | 当前用户无知识查询权限 | 失败 |
| `UPSTREAM_BAD_REQUEST` | 知识查询条件无效 | 失败 |
| 未识别错误 | Agent 服务暂不可用 | 失败 |

非流式和 SSE 的业务措辞必须一致；错误响应不得回退旧 Agent，也不得自动改查实时业务工具。

## 7. 验收矩阵

- 身份：前端伪造 `roleCode/userId/permissionCodes` 不影响 Python 请求；
- 卡片：非流式和 SSE 的知识卡片一致，只含固定字段；
- 脱敏：路径、内部 ID、score、token、原始 JSON 和 evidence 正文不进入审计；
- 审计：成功、降级、无数据和不可用均能定位 corpus version 或明确版本未知；
- handoff：非流式和流式均记录 `knowledge_expert`，不记录内部工具参数；
- 错误：`NO_DATA`、不可用、权限拒绝和非法查询保持不同语义；
- 回归：Java Agent、Python Agent 全量测试通过，53 个 Gateway/MCP 工具 hash 不变化。

## 8. 不在本切片内

- Web 专用引用组件和真实浏览器 UAT；
- 正式 corpus 发布、`current.json` 切换和回滚；
- 新增数据库表、MCP Tool、Java 知识查询 Controller 或任意业务写操作；
- 面向非管理员的细粒度知识权限。

## 9. 实施结果

2026-08-01 已完成以下实现：

- Python 在确定性和 LLM 知识目标结束时生成固定 `reviewTrace.knowledgeAudit`，失败状态不伪装
  成无数据，摘要不含查询或 evidence；
- Java 非流式 DTO 接收 `reviewTrace`，只解析受控专家、Goal、状态、知识域、版本和数量；
- Java 对 `knowledge_evidence` 非流式卡片和 SSE card 使用同一显式白名单重建对象，允许字段
  的文本值仍会拒绝绝对路径和内部 document/chunk/evidence 标识；
- 内部 `audit` SSE 不向 Web 转发，即使完整 trace 含内部工具诊断，也只提取白名单审计元数据；
- 复用 `agent_tool_audit_log` 记录 `agent_runtime`、`agent_handoff` 和 `knowledge_search`，未新增
  MCP Tool、数据库迁移或业务写入；
- `RAG_UNAVAILABLE` 和 `UPSTREAM_BAD_REQUEST` 使用知识查询专用安全提示，其他实时业务能力不被
  伪装为已失败。
- RAG-04 现场发现长知识回答触发 DTO 校验时，全局异常处理会返回方法签名和 rejected value；
  现已增加参数校验固定消息，并把所有其他非业务异常改为仅服务端记录详细堆栈、客户端只返回
  “服务异常，请稍后重试”。

验证结果：

- Java 定向测试：`34 passed`；
- Java 全量测试：`304 passed`，0 failure、0 error、0 skipped；
- Python 全量测试：`433 passed, 5 skipped`；
- 正式 corpus 保持 `VALIDATED`，未创建或发布 `current.json`。
