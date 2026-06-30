# Python Agent Service 架构迁移前资产盘点

## 1. 背景与目标转变

当前智能仓储 AI 助手已经具备 Java Spring Boot 后端、Web AI 助手入口、`agent_session`、`AGENT_DELEGATION` token、Java `AgentGatewayService`、Java MCP Server `warehouse-mcp`，以及 6 个 L1 只读 MCP 工具：

- `resolve_products`
- `resolve_warehouses`
- `get_inventory_overview`
- `get_warehouse_status`
- `get_pallet_status`
- `get_assay_status`

当前实现的主要问题不是缺少更多工具，而是 Agent Runtime 仍偏规则式和搜索器式：Java 负责意图 JSON 规划、候选项处理、工具编排、局部上下文记忆和模板化回答，导致多轮对话、工具结果回填、流式输出、人类确认和长期记忆都很难继续扩展。

目标需要从：

```text
Java AgentGatewayService 内部规划和编排
```

转变为：

```text
Java 业务系统 + Python LangGraph Agent Runtime + Java MCP/Tool 能力层
```

迁移后的职责原则是：

- Java 继续负责业务、安全、权限、数据权限、事务、审计、MCP/Tool 能力和 preview/execute 安全闭环。
- Python FastAPI + LangGraph 专注负责真正的 Agent Runtime，包括多轮对话、messages 历史、state、tool loop、工具结果回填、流式输出、human-in-the-loop 和记忆策略。
- Python 不直接连接数据库，不复制 Java Mapper，不拼 SQL，不绕过 Java 权限，不直接调用写接口。

## 2. 最终推荐架构

```text
Web 前端
  -> Java Spring Boot
      - 登录
      - agent_session
      - 用户委托 token
      - 审计
      - 前端 API
  -> Python FastAPI Agent Service
      - LangGraph
      - messages
      - state
      - tool loop
      - streaming
      - human-in-the-loop
  -> Java MCP Server / Java Tool API
      - 仓储只读工具
      - 未来 preview/execute
  -> Java Service / Mapper / DB
```

推荐的调用链为：

```text
用户登录 Web/小程序
  -> Java 创建或复用 agent_session
  -> Java 校验当前用户与会话状态
  -> Java 调用 Python Agent Service
  -> Python 基于 LangGraph state 选择工具和生成回答
  -> Python 通过 Java 受控 Tool Gateway 或 HTTP/Streamable HTTP MCP 调用工具
  -> Java 使用当前 agentSessionId 和委托身份做最终鉴权、数据权限和审计
  -> Python 汇总工具结果并生成自然语言回答或候选选择事件
  -> Java 转发给前端
  -> 前端展示自然语言、业务卡片、追问建议和调试信息
```

普通用户看到的是业务回答和业务卡片；内部的 MCP Tool、tool schema、agentSession、delegationToken、audit log 和 errorCode 只保留在系统内部或管理员调试模式中。

## 3. 保留在 Java 的资产

### 登录与权限

保留在 Java。现有 Web/小程序登录、Spring Security、`LoginUser`、角色和权限判断已经是仓储系统的权限入口。Python 不应接触用户密码，也不应重新实现登录态。

### JWT

保留在 Java。`JwtUtils` 已支持普通用户 token 和 `AGENT_DELEGATION` token，包含 `tokenType`、`agentSessionId`、`aud=warehouse-mcp`、`scopes`、`authorities`、`iat`、`exp` 等关键 claims。token 签发、校验和吊销状态检查必须继续由 Java 控制。

### agent_session

保留在 Java。`AgentSessionController`、`AgentSessionService`、`AgentSessionServiceImpl`、`AgentSessionMapper` 和 `agent_session` 表负责会话创建、归属校验、状态、过期、撤销、最后使用时间和错误码更新。Python 可把 `agentSessionId` 作为 LangGraph `thread_id` 或映射键，但不能绕过 Java 会话状态。

### AgentDelegationTokenService 能力

当前代码中委托 token 能力主要落在 `AgentSessionService.issueDelegationTokenForInternalUse(...)` 和 `JwtUtils.generateAgentDelegationToken(...)`，未必是独立类。该能力必须保留在 Java 内部。前端、LLM 和普通 MCP Tool 输出都不得接收 `delegationToken`。

### JwtAuthenticationFilter 对 AGENT_DELEGATION 的支持

保留在 Java。`JwtAuthenticationFilter` 每次处理 `AGENT_DELEGATION` token 时会调用 `AgentSessionService.validateDelegation(...)`，检查 audience、会话状态、过期、用户有效性、scope 和请求范围，并设置 `agentSessionId`、`agentUserId`、`agentToolName` 请求属性。`X-Agent-Tool-Name` 只能做审计辅助，不作为权限依据。

### Java Service / Mapper / POJO / DTO / VO

保留在 Java。库存、库位、产品、托盘、化验等核心业务规则和数据访问已经沉淀在 Java Service、Mapper、POJO、DTO 和 VO 中。Python 不应复制 Mapper 逻辑，也不应直接连库读取业务表。

### MCP Tool

保留在 Java。`warehouse-mcp` 已经把 6 个 L1 只读能力封装成安全工具，并具备 schema、错误结构、写接口黑名单和只读后端调用边界。未来 preview/execute 工具也应继续由 Java 业务系统定义和约束。

### WarehouseApiClient

保留在 Java MCP 模块。`WarehouseApiClient` 通过仓储后端 HTTP 接口取数，不直接访问数据库，并携带 `Authorization`、`X-Agent-Session-Id` 和 `X-Agent-Tool-Name` 等审计关联信息。它应继续作为 Java MCP Server 的后端访问层。

### 审计

保留在 Java。当前已有 Agent Tool 审计和后端 API 审计两层，记录 `userId`、`agentSessionId`、`toolName`、`upstreamPath`、`resultCode`、`errorCode`、`durationMs`、`requestSummary`、`responseSummary` 等字段。审计不得记录 token、Authorization Header、密码、数据库连接串或完整堆栈。

### 写操作 preview/execute

保留在 Java。入库、出库、调拨等写操作未来必须遵循 `preview -> 用户确认 -> executionToken -> idempotencyKey -> execute`。权限、幂等、事务和审计都应由 Java 最终执行，Python 只负责对话编排和 human-in-the-loop。

### 业务规则、数据权限和事务

保留在 Java。产品、库位、库存、托盘、化验、质量标准、出入库和移库规则都应继续由 Java 业务层判断。Python 不能绕过 Java 直接读库或写库，不能自己推导事务性业务行为。

## 4. 迁移到 Python 的资产

### LLM Agent Loop

当前位置：`AgentGatewayServiceImpl` + `AgentModelClient`。

迁移原因：当前 Java 实现本质是一次性 intent planner，不是真正的模型驱动 tool loop。LangGraph 更适合表达 plan、act、observe、respond、多轮 resume 和中断恢复。

### messages 历史

当前位置：`AgentConversationMemory` 的 `recentMessages`。

迁移原因：当前只保留少量 process-local 文本，不是标准 chat messages，也没有把工具结果、候选点击和用户选择统一纳入消息轨迹。Python 应维护完整的 `messages` 历史，并支持持久化 checkpointer。

### LangGraph state

当前位置：Java 中分散在 `AgentConversationMemory.ConversationState`、`AgentPlan`、`pageContext` 和局部变量。

迁移原因：当前状态结构不足以支持“这些”“它”“刚才那个产品”“这个库位”等连续指代。Python 应用 LangGraph state 管理 selected product、warehouse、pallet、assay、pending question、tool results 和 UI events。

### 短期记忆

当前位置：`AgentConversationMemory` 中的 last product、last warehouse、pending options、recent messages。

迁移原因：短期记忆需要和消息历史、工具结果、人类选择事件绑定，不能继续靠 Java if-else 逐条补规则。

### 长期记忆策略

当前位置：当前基本没有生产级长期记忆。

迁移原因：未来需要决定用户偏好、常用查询、历史任务和可审计摘要的存储策略。LangGraph 支持 thread-scoped state 和外部 store 的组合，更适合作为长期记忆入口。

### 工具选择

当前位置：`LlmAgentModelClient` 生成有限 intent JSON，`RuleBasedAgentModelClient` 通过关键词兜底，`AgentGatewayServiceImpl` 根据 intent switch 调工具。

迁移原因：当前模型只能选少数固定 intent，复杂查询会退化为搜索器。Python 应让模型基于工具描述、上下文和工具结果动态决定下一步，同时由 Java 工具边界限制权限和安全。

### 工具结果回填

当前位置：`AgentGatewayServiceImpl` 直接读取 MCP 返回并拼接回答。

迁移原因：工具结果没有作为 tool message 回填给模型，模型无法基于真实结果组织自然语言解释、追问和后续分析。Python 应把工具调用和结果作为标准消息或 LangGraph state 回填。

### 最终自然语言生成

当前位置：`AgentGatewayServiceImpl` 的 `formatInventoryAnswer`、`formatWarehouseAnswer`、`formatPalletAnswer`、`formatAssayAnswer` 等模板方法。

迁移原因：模板化回答容易暴露结构字段，也难以解释复杂业务结果。Python 应由模型基于安全摘要生成自然语言回答，Java 只保留必要的兜底文案。

### 流式输出

当前位置：当前 `POST /api/agent/sessions/{agentSessionId}/messages` 为非流式响应。

迁移原因：真实 AI 助手需要业务进度事件和最终回答流式展示，例如“正在确认产品范围”“正在查询库存”“找到多个规格”。Python 应生成业务事件流，Java 做安全代理和审计。

### human-in-the-loop

当前位置：候选项通过 `pageContext.selectedOption` 回传，未来 preview/execute 尚未实现。

迁移原因：候选选择、预览确认和执行授权都应成为 Agent state 中的一等事件。Python 适合用 LangGraph interrupt/resume 建模，人类确认结果再回到工具调用循环。

### intent planner 的替代方案

当前位置：`LlmAgentModelClient` 要求模型返回严格 JSON plan，`RuleBasedAgentModelClient` 兜底。

迁移原因：单轮 JSON planner 不足以表达多步工具调用、结果观察、追问和继续分析。应由 LangGraph 的节点、边、state 和工具消息替代简单 planner。

## 5. 保留为 fallback 的资产

### rule parser

`RuleBasedAgentModelClient` 可保留为开发、故障降级和极简只读查询 fallback，但不应继续扩展成完整中文 Agent。

### 简单 LLM intent planner

`LlmAgentModelClient` 可在 Python 服务不可用时作为临时 fallback，维持库存、库位、托盘、化验的基础查询，但不应承担长期 Agent Runtime。

### Java messages 非流式接口

`POST /api/agent/sessions/{agentSessionId}/messages` 应继续保留，供旧前端、测试和非流式降级使用。新增流式能力时，可让 Java 内部转发到 Python。

### STATIC_TOKEN 本地开发模式

`WAREHOUSE_API_TOKEN` / `STATIC_TOKEN` 仅用于 Codex 本地开发验收。生产应使用当前用户委托身份，不允许把静态全局 token 作为多用户生产授权模式。

### STDIO MCP 一用户一进程

`StdioMcpSessionManager` 和 `WAREHOUSE_DELEGATED_TOKEN` 环境变量注入可保留为过渡资产，用于本地和早期闭环。生产多用户并发不应长期依赖 STDIO 一用户一进程。

## 6. 应丢弃或停止扩展的资产

### 在 Java 中继续堆叠指代 if-else

不应继续在 `AgentGatewayServiceImpl` 中为“这些”“它”“刚才那个”“这个库位”增加更多 if-else。此类上下文解析应迁移到 Python messages + structured state。

### Java 模板化最终回答

`formatInventoryAnswer` 等模板可作为 fallback，但不应继续作为主路径。主路径应由模型基于工具结果和业务上下文生成自然语言回答。

### 只把 selectedOption 放进 pageContext

候选卡片点击不能只作为一次 `pageContext.selectedOption` 处理。它应进入 conversation messages 或 structured event，成为后续上下文的一部分。

### 普通 UI 展示内部字段

普通用户界面不得展示 `toolName`、`SUCCESS`、`productId`、`warehouseId`、raw JSON、错误堆栈、token 或 Authorization。管理员调试模式可以查看脱敏后的 toolCalls、耗时和错误码。

### Python 直接连数据库

禁止。Python 只能通过 Java 受控 Tool Gateway 或 MCP 获取业务数据。

### Python 复制 Java Mapper

禁止。Mapper、事务和业务查询规则属于 Java 业务系统，复制会造成权限和规则分叉。

### 任意 SQL / 任意 HTTP 工具

禁止。查询分析能力可以开放得更灵活，但必须是白名单业务实体、字段、范围、分页、聚合和审计，不允许任意 SQL 或任意 HTTP 代理。

### 多用户生产继续依赖 STDIO 全局或一用户一进程模式

STDIO 一用户一进程是过渡方案。生产多用户应迁移到 HTTP/Streamable HTTP MCP 或受控 Tool Gateway，以支持请求级身份注入、连接复用、隔离和监控。

## 7. Java 与 Python 接口边界

### Java 对前端保持的 API

```text
POST /api/agent/sessions
POST /api/agent/sessions/{agentSessionId}/messages
POST /api/agent/sessions/{agentSessionId}/messages/stream
DELETE /api/agent/sessions/{agentSessionId}
```

边界说明：

- 前端仍只和 Java 交互。
- Java 继续校验当前登录用户、agentSession 归属、状态和过期。
- 前端响应不得包含 `delegationToken`、用户原始 token 或 Authorization Header。
- `/messages` 可作为非流式 fallback，`/messages/stream` 用于 SSE 或 WebSocket 事件流。

### Java 调 Python 内部接口建议

```text
POST /internal/agent/chat
POST /internal/agent/chat/stream
POST /internal/agent/resume
GET /internal/agent/health
```

建议请求只包含必要安全上下文：

```text
agentSessionId
userSummary: userId, name, roleCode, permissionCodes
scopes
message 或 user event
pageContext
conversation metadata
```

Java 调 Python 应使用内网服务鉴权，例如 mTLS、内部网关签名或服务间 token。该鉴权不同于用户登录 token。

### Python 调 Java 工具短期可选接口

```text
POST /internal/agent/tools/{toolName}
```

短期可以由 Java 提供受控内部 Tool API，复用现有 MCP 工具封装或 Java Service 只读能力。该接口必须：

- 只允许白名单工具名；
- 只允许当前 6 个 L1 只读工具；
- 通过 agentSessionId 绑定当前用户；
- 由 Java 注入或校验委托身份；
- 记录 Agent Tool 审计；
- 不允许任意 SQL、任意 HTTP 或写接口。

M1.3R-1b 已选择方案 C 作为过渡实现：Java Internal Tool Gateway 复用现有 `McpSessionManager` 和 `warehouse-mcp` 六工具，STDIO MCP 进程仍由 Java 按 `agentSessionId` 管理。Python 后续只调用 Internal Tool Gateway，不管理进程、不接触用户 token、delegationToken 或 Authorization。

接口使用独立 `X-Agent-Service-Key` 做内网服务间鉴权，配置来自 `AGENT_INTERNAL_TOOL_SERVICE_KEY`，为空时默认拒绝。请求参数和响应均有限制并脱敏；Java 会校验会话存在、未撤销、未过期、用户仍有效且 scope 包含 `mcp:warehouse:read`，后端业务接口继续承担最终权限和数据权限判断。

部署前需执行 `migrations/2026-06-29-add-internal-agent-tool-gateway-audit.sql`，为 Agent Tool 审计增加 `tool_call_id` 关联字段。

### 中长期目标

```text
Python Agent Service -> HTTP/Streamable HTTP MCP -> Java MCP Server
```

中长期应收敛到 MCP 协议或受控 Tool Gateway，避免 Python 和 Java 两套工具定义长期分叉。

## 8. 认证与安全边界

- 前端不接触 `delegationToken`。
- LLM 不接触 `delegationToken`。
- Python 不接触用户密码、refresh token 或用户原始登录 token。
- Python 只接收必要的 `agentSessionId`、用户安全摘要、scope 和 `pageContext`。
- Java 调 Python 必须使用内网服务鉴权，不允许公网匿名调用内部 Agent API。
- Python 调 Java 工具必须携带 `agentSessionId`，或由 Java 代理层根据 agentSessionId 注入用户委托身份。
- 权限最终由 Java 后端判断，包括用户是否有效、session 是否撤销或过期、scope 是否覆盖请求、数据权限是否允许。
- Python 不得绕过 Java 权限直接读库或写库。
- 工具返回给 Python 的数据应是结构化业务数据和安全摘要，不是数据库裸字段或内部异常。
- 审计、日志和前端响应不得记录 token、Authorization Header、密码、refresh token、数据库连接串、完整堆栈或服务器内部路径。

## 9. 工具调用策略

### 方案 A：Python 调 Java internal Tool API

优点：

- 快速落地，可以先验证真实 Agent 体验。
- 复用 Java 登录态、agent_session、权限、审计和现有 Service/MCP 封装。
- 不依赖 MCP transport 立即改造。
- 更容易让 Java 在工具调用前后统一做安全检查和审计。

缺点：

- 可能与 MCP 工具定义存在重复。
- Python tool schema 与 Java MCP schema 需要同步。
- 后续仍需要收敛到统一协议或统一 Tool Gateway。

### 方案 B：Python 作为 MCP Client 调 Java MCP Server

优点：

- 协议统一，工具边界清晰。
- Python 不需要理解 Java 内部接口细节。
- 更适合长期生产和跨 Agent 复用。
- MCP 工具 schema、错误结构和权限边界可统一治理。

缺点：

- 当前 STDIO 一用户一进程不适合生产多用户并发。
- 需要 HTTP/Streamable HTTP MCP 改造。
- 请求级用户委托身份注入、连接管理、超时和审计链路需要重新设计。

### 阶段性建议

```text
短期 M1.3R：Python 调 Java internal Tool API 或复用现有 MCP 封装，先验证真实 Agent 体验。
中期 M1.4/M5：迁移到 HTTP/Streamable HTTP MCP。
长期：Python 只通过 MCP 或受控 Tool Gateway 调 Java 工具。
```

短期不应为了协议完美而继续在 Java 中堆叠 Agent Runtime；但也不能让 Python 绕过 Java 安全边界。

## 10. 分阶段路线

### M1.3R-0：资产盘点与设计文档

目标：完成当前 Java Agent、MCP、认证、会话、审计和前端助手资产盘点，明确 Java/Python 边界。

允许做什么：新增和更新设计文档，读取代码和文档。

禁止做什么：修改 Java 业务逻辑、前端组件逻辑、新增 Python 服务代码、新增 MCP 工具或写接口。

验收标准：形成 `python-agent-migration-inventory.md`，明确保留、迁移、fallback、丢弃资产和阶段路线。

### M1.3R-1a：Python Agent Service 详细设计

目标：定义 FastAPI、LangGraph state、messages、工具循环、流式事件、候选选择、鉴权和 fallback 边界。

允许做什么：新增和更新设计文档。

禁止做什么：新增 Python 服务代码、MCP 工具或写能力。

验收标准：`python-agent-service-design.md` 能直接指导后续实现。

### M1.3R-1b：Java Internal Agent Tool Gateway

目标：先实现 `POST /internal/agent/tools/{toolName}`，让后续 Python 可以访问真实 Java 只读工具，而不直接管理 STDIO MCP。

允许做什么：白名单调用现有 6 个 L1 工具；Java 按 agentSession 恢复用户和 scope；使用内网服务鉴权；记录 Tool 审计。

禁止做什么：新增 Python 服务、MCP 工具、preview、execute、SQL、任意 HTTP、直接数据库访问或业务写接口。

验收标准：六工具白名单、会话校验、用户有效性、scope、参数限额、错误脱敏和审计测试通过，禁止工具不会到达 MCP。

### M1.3R-1c：Python Agent Service 接入真实 Gateway

目标：新增 FastAPI + LangGraph 服务，主路径直接调用 M1.3R-1b 的真实只读 Gateway；mock 只作为单元测试夹具。

允许做什么：健康检查、chat、state、messages、tool loop，以及当前 6 个只读工具调用。

禁止做什么：Python 接触 token、Authorization、数据库、Mapper 或业务写接口。

验收标准：Python 可处理多轮消息，以 `agentSessionId` 映射 thread_id，并通过真实工具结果生成回答。

### M1.3R-2：真实只读链路验收

目标：完成候选消歧、tool result 回填、统一错误、超时和 Java/Python 审计关联。

允许做什么：通过 Java Internal Tool Gateway 调用现有 6 个只读工具。

禁止做什么：新增 preview、execute、SQL、HTTP 代理、数据库访问或写接口调用。

验收标准：库存、库位、托盘、化验真实可用；权限和审计仍由 Java 控制；错误不被解释为空数据。

### M1.3R-3：Java 转发 Agent 请求到 Python（已实现）

目标：Java 保持原前端 API，但内部可配置调用 Python Agent Service。

已实现：

- `agent.runtime.mode` 支持 `legacy` 和 `python`，默认 `legacy`；
- Java 通过 `POST /internal/agent/chat` 转发普通消息和 `candidate_selected` 事件；
- Java 通过 `GET /internal/agent/health` 判断 Python Runtime 和 Tool Gateway 是否可用；
- Java -> Python 使用 `AGENT_PYTHON_SERVICE_KEY`，Python -> Java Tool Gateway 使用独立的 `AGENT_INTERNAL_TOOL_SERVICE_KEY`；
- 转发内容只含 `agentSessionId`、用户安全摘要、scope、消息事件、白名单 page context、traceId 和 requestId；
- Python cards/options/suggestions 映射回现有前端响应，Java 再做一次普通响应脱敏；
- Java 记录 runtimeMode、实际路径、requestId、traceId、resultCode、errorCode、durationMs 和 fallbackUsed；
- 前端 API 仍为 `POST /api/agent/sessions/{agentSessionId}/messages`。

Fallback 边界：

- 只允许尚未在 Python 建立上下文的首次、明确、简单只读查询降级；
- 一旦 Python 已处理该会话，后续超时或不可用不得切到另一套记忆；
- 候选选择、指代追问和其他复杂上下文请求不得 fallback；
- 首次降级后的会话固定走 legacy，避免 Java/Python 两套状态分叉；
- 不可安全降级时返回“AI 助手暂时不可用，请稍后重试。”

禁止：前端直接调用 Python；转发用户 token、delegationToken、Authorization、refresh token、密码或数据库连接信息；新增写能力。

验收标准：前端调用路径不变；普通消息和候选事件可进入 Python；候选与回答不暴露内部 ID 或工具字段；Python 不可用时严格按上述策略降级。

### M1.3R-4：上下文与工具回填

目标：实现 messages history、tool result as messages、candidate selection as event/message。

允许做什么：把候选卡片点击、用户选择、工具调用结果和最终回答统一写入 LangGraph state。

禁止做什么：继续依赖 Java if-else 修补“这些/它/刚才那个”。

验收标准：用户选择“黄冰糖（袋）”后，追问“这些主要在哪些库位”“它今天有没有化验”不再要求重新输入产品。

### M1.3R-5：流式输出

目标：实现 SSE 或 WebSocket，普通用户显示业务进度和最终回答。

允许做什么：增加 `/messages/stream`，输出业务事件，如确认产品、查询库存、汇总结果、需要选择、最终回答。

禁止做什么：显示模型原始 chain-of-thought、token、内部错误堆栈或未脱敏 tool payload。

验收标准：前端能流式展示业务进度和回答；管理员调试模式可查看脱敏 toolCalls。

### M1.3R-6：human-in-the-loop 预留

目标：为未来 preview/execute 的人工确认设计 interrupt/resume。

允许做什么：设计确认事件、resume 接口、pending action state 和审计字段。

禁止做什么：实际执行入库、出库、调拨等写操作。

验收标准：只读候选选择和未来写操作确认都能用统一的人类介入模型表达。

### M1.4：受控只读数据分析层

目标：新增库存分布、化验时间范围、报告详情、趋势分析等只读分析能力。

允许做什么：新增白名单业务实体和字段的受控只读分析接口，支持分页、聚合、采样和审计。

禁止做什么：任意 SQL、任意 HTTP、任意数据库访问、无限量返回。

验收标准：Agent 能回答“主要在哪些库位”“最近 30 天化验趋势”“某类产品库存分布”等分析问题，仍只读且可审计。

### M5：生产化

目标：多用户生产可用、可监控、可回滚。

允许做什么：HTTP/Streamable HTTP MCP、多用户隔离、持久化 memory、监控告警、限流、超时、重试和观测体系。

禁止做什么：生产继续依赖全局 token、STDIO 全局进程或不可审计工具调用。

验收标准：多用户并发稳定，身份隔离正确，撤销会话立即失效，审计链路完整，异常可观测。

## 11. 风险与待确认问题

- Python 服务部署与运维：需要决定独立部署、同机部署、容器化、健康检查、日志和发布回滚方式。
- Java/Python DTO 对齐：需要定义稳定的 request、response、tool result、error 和 streaming event schema。
- 流式代理复杂度：Java 代理 SSE/WebSocket 到前端时，需要处理断连、超时、重试和审计落点。
- `agentSessionId` 与 LangGraph `thread_id` 映射：需要确认是一对一映射，还是增加独立 `threadId`。
- memory 存储：短期可用内存或 Redis，生产应评估 Redis、Postgres、文件或专用 checkpointer。
- Python 服务如何鉴权：Java 调 Python 的内部服务鉴权需要明确机制，不能裸露内网接口。
- 工具调用超时与重试：需要定义每个工具的 timeout、retryable 错误、用户可见解释和审计错误码。
- 审计如何串联 Java 与 Python：需要统一 `agentSessionId`、traceId、toolCallId、durationMs 和 resultCode。
- 如何回退到 rule 模式：需要明确 Python 不可用时哪些场景允许 Java fallback，哪些应直接提示暂不可用。
- 如何避免模型看到敏感字段：Java 给 Python 的用户摘要、工具结果和错误消息必须先脱敏。
- MCP transport 收敛时间：短期 internal Tool API 快，长期 HTTP/Streamable HTTP MCP 更一致，需要阶段切换计划。
- 多用户隔离：STDIO 一用户一进程过渡方案在资源消耗、进程回收和 token 注入上存在生产风险。

## 12. 本轮结论

结论明确：

```text
MCP/Tool 继续保留 Java；
Python 专门处理 Agent Runtime；
Java AgentGatewayService 当前规则式规划能力不应继续扩展为完整 Agent 框架；
下一步应进入 M1.3R-4/M1.3R-5，完善持久化上下文、候选恢复和 Java 到前端的流式代理。
```

M1.3R-3 已完成 Java 非流式转发。建议下一阶段进入 M1.3R-4/M1.3R-5：把 in-memory checkpointer 替换为可恢复存储，并实现 Java SSE 代理和前端业务事件渲染；Java 规则路径只保留为受限 fallback。
