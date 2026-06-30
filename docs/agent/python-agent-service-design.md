# Python Agent Service 详细设计

适用阶段：M1.3R-1a
适用范围：Python FastAPI Agent Service、Java Agent Gateway 转发、LangGraph Runtime、只读工具调用、流式事件和 human-in-the-loop 预留。

本文只描述设计，不代表已经实现 Python 服务代码。

## 1. 设计目标

Python Agent Service 的定位是专门负责 LangGraph Agent Runtime，不替代 Java 业务后端，也不替代 Java MCP/Tool 能力层。

Python 负责：

- messages 历史；
- LangGraph state；
- tool loop；
- 工具结果回填；
- 自然语言回答生成；
- 流式输出；
- human-in-the-loop；
- 候选选择、追问和上下文指代；
- 短期记忆和未来长期记忆策略。

Python 不负责：

- 登录；
- 用户权限；
- JWT 签发或校验；
- `agent_session` 最终状态判断；
- 数据权限；
- 数据库连接；
- Mapper；
- 事务；
- 写操作执行；
- 任意 SQL；
- 任意 HTTP 代理。

设计边界：

- Python 不直接连接数据库。
- Python 不复制 Java Mapper。
- Python 不绕过 Java 权限。
- Python 不接触用户密码、用户原始 token、refresh token 或 `delegationToken`。
- Java 仍是最终权限判断者。
- MCP/Tool 能力继续保留在 Java。

## 2. 总体架构

```text
Web 前端
  -> Java Spring Boot
      - 登录
      - agent_session
      - 鉴权
      - 审计
      - 前端 API
  -> Python FastAPI Agent Service
      - LangGraph
      - messages
      - state
      - tool loop
      - streaming
      - human-in-the-loop
  -> Java Tool Gateway / Java MCP Server
      - 6 个只读工具
      - 未来 preview/execute
  -> Java Service / Mapper / DB
```

目标链路：

```text
用户在 Web/小程序已登录
  -> 前端调用 Java /api/agent/sessions
  -> Java 创建或复用 agent_session
  -> 前端调用 Java /api/agent/sessions/{agentSessionId}/messages 或 /messages/stream
  -> Java 校验当前用户拥有该 agentSessionId
  -> Java 向 Python 传递安全上下文和用户消息
  -> Python 用 LangGraph 维护 messages/state 并选择工具
  -> Python 调 Java Tool Gateway 或 Java MCP Server
  -> Java 根据 agentSessionId 做最终权限、数据权限和审计
  -> Python 将工具结果写回 messages/state
  -> Python 生成自然语言回答、候选卡片或流式事件
  -> Java 转发给前端
```

普通用户界面展示自然语言、业务候选卡片、业务结果卡片、追问建议和业务进度。内部 tool name、raw JSON、产品 ID、库位 ID、token 和堆栈只允许留在系统内部或管理员调试模式的脱敏摘要中。

## 3. Python 服务目录结构

建议后续新增独立模块：

```text
agent-service/
  pyproject.toml
  README.md
  app/
    main.py
    config.py
    schemas.py
    graph/
      state.py
      nodes.py
      graph.py
      memory.py
    tools/
      client.py
      warehouse_tools.py
      schemas.py
    streaming/
      events.py
      sse.py
    security/
      auth.py
    observability/
      logging.py
      tracing.py
    tests/
```

职责建议：

- `main.py`：FastAPI 应用入口、路由注册、生命周期管理。
- `config.py`：读取 Java 内部服务地址、模型配置、超时、日志配置，不读取仓储用户 token。
- `schemas.py`：内部 chat、stream、resume、error 和 health schema。
- `graph/state.py`：定义 `WarehouseAgentState`。
- `graph/nodes.py`：定义模型节点、工具选择节点、工具调用节点、回答生成节点、澄清节点。
- `graph/graph.py`：组装 LangGraph。
- `graph/memory.py`：封装短期 checkpointer，未来扩展 Redis/Postgres。
- `tools/client.py`：调用 Java Tool Gateway 或 MCP Client 的适配层。
- `tools/warehouse_tools.py`：当前 6 个只读工具的 Python 侧描述和输入输出映射。
- `tools/schemas.py`：工具输入输出安全 schema，不包含数据库裸字段。
- `streaming/events.py`：统一 SSE 事件类型和 payload。
- `streaming/sse.py`：把 LangGraph 事件转换为前端可消费 SSE。
- `security/auth.py`：Java 调 Python 的内部服务鉴权。
- `observability/logging.py`：结构化日志、脱敏策略。
- `observability/tracing.py`：traceId、requestId、toolCallId 串联。
- `tests/`：接口 schema、state、工具调用、streaming、fallback 和安全边界测试。

## 4. Java 调 Python 的内部接口

前端不直接调用 Python。所有用户入口仍由 Java 暴露，Java 校验当前登录用户、`agent_session` 归属和状态后，才调用 Python 内部接口。

### 4.1 `POST /internal/agent/chat`

用途：非流式对话。用于兼容当前 `/api/agent/sessions/{agentSessionId}/messages`，也用于 Python 流式能力不可用时的降级。

请求 schema 草案：

```json
{
  "agentSessionId": "agt_20260629_001",
  "user": {
    "userId": 1,
    "name": "张三",
    "roleCode": "warehouse_user",
    "permissionCodes": ["inventory:view"]
  },
  "scopes": ["mcp:warehouse:read"],
  "message": {
    "type": "user_message",
    "content": "这些主要在哪些库位？"
  },
  "pageContext": {
    "route": "/productStock"
  },
  "client": {
    "traceId": "trace-001",
    "requestId": "req-001",
    "debug": false
  }
}
```

响应 schema 草案：

```json
{
  "agentSessionId": "agt_20260629_001",
  "answer": "黄冰糖（袋）主要存放在 2 号库位和 3 号库位。",
  "needsUserSelection": false,
  "cards": [],
  "suggestions": ["需要我继续展开具体托盘明细吗？"],
  "debug": null,
  "error": null
}
```

### 4.2 `POST /internal/agent/chat/stream`

用途：流式对话。Java 将 Python 事件转换为前端 SSE 或 WebSocket 事件。

请求 schema 与 `/internal/agent/chat` 基本一致。响应为 SSE 事件流，不返回一次性 JSON。

事件序列示例：

```text
event: message_start
data: {"messageId":"msg-001"}

event: progress
data: {"stage":"resolve_product","text":"正在确认产品范围..."}

event: clarification
data: {"prompt":"“黄冰糖”有多个规格，请选择：","options":[...]}

event: message_end
data: {"resultCode":"NEEDS_USER_SELECTION"}
```

### 4.3 `POST /internal/agent/resume`

用途：human-in-the-loop 恢复。用于候选选择、未来 preview 确认、未来 execute 前确认或用户修改操作。

请求 schema 草案：

```json
{
  "agentSessionId": "agt_20260629_001",
  "resumeToken": "resume_001",
  "event": {
    "type": "candidate_selected",
    "selection": {
      "optionId": "opt_001",
      "optionType": "SINGLE_PRODUCT",
      "displayLabel": "黄冰糖（袋）"
    }
  },
  "client": {
    "traceId": "trace-002",
    "requestId": "req-002",
    "debug": false
  }
}
```

响应可以是非流式 JSON，也可以复用 `/internal/agent/chat/stream` 的事件格式。M1.3R-1c 首版可先实现非流式真实只读链路。

### 4.4 `GET /internal/agent/health`

用途：Java 健康检查和 fallback 判断。

响应 schema 草案：

```json
{
  "status": "UP",
  "service": "warehouse-agent-service",
  "version": "0.1.0",
  "dependencies": {
    "model": "UP",
    "toolGateway": "UP",
    "memory": "UP"
  }
}
```

健康检查不得返回密钥、token、模型 API key、内部连接串或异常堆栈。

## 5. 请求上下文设计

Java 传给 Python 的上下文示例：

```json
{
  "agentSessionId": "agt_20260629_001",
  "user": {
    "userId": 1,
    "name": "张三",
    "roleCode": "warehouse_user",
    "permissionCodes": []
  },
  "scopes": ["mcp:warehouse:read"],
  "message": "这些主要在哪些库位？",
  "pageContext": {},
  "client": {
    "traceId": "trace-001",
    "requestId": "req-001"
  }
}
```

可进入模型的字段：

- 用户自然语言消息；
- 安全的页面上下文，例如当前页面业务模块、筛选条件摘要；
- 用户角色名称或权限摘要，但不应暴露过细内部权限实现；
- 当前会话的安全业务记忆，例如“最近选择的产品是黄冰糖（袋）”；
- 工具返回的脱敏业务摘要。

只能用于系统内部的字段：

- `agentSessionId`；
- `userId`；
- `permissionCodes` 原始列表；
- `traceId`；
- `requestId`；
- `toolCallId`；
- 内部错误码；
- 审计引用。

禁止传给 Python 或模型的字段：

- `delegationToken`；
- Authorization Header；
- 用户原始登录 token；
- refresh token；
- 密码；
- 数据库连接串；
- 完整异常堆栈；
- 服务器本地路径。

如果 Python 需要向模型提供用户身份上下文，应使用安全摘要，例如：

```text
当前用户是仓储系统已登录用户，具备库存、库位、托盘和化验的只读查询权限。
```

## 6. LangGraph state 设计

建议定义 `WarehouseAgentState`，至少包含：

```text
messages
agentSessionId
user
scopes
pageContext
selectedProduct
selectedWarehouse
lastInventoryResult
lastAssayResult
lastPalletResult
pendingClarification
toolCallHistory
auditRefs
```

字段草案：

```json
{
  "messages": [],
  "agentSessionId": "agt_20260629_001",
  "user": {
    "userId": 1,
    "name": "张三",
    "roleCode": "warehouse_user"
  },
  "scopes": ["mcp:warehouse:read"],
  "pageContext": {},
  "selectedProduct": {
    "productId": 84,
    "displayLabel": "黄冰糖（袋）",
    "source": "user_selection",
    "selectedAt": "2026-06-29T10:00:00+08:00"
  },
  "selectedWarehouse": {
    "warehouseId": 2,
    "displayLabel": "2号库位",
    "matchType": "NORMALIZED_NAME"
  },
  "lastInventoryResult": {
    "productLabel": "黄冰糖（袋）",
    "displayStockInfo": "11板30件",
    "totalEquivalentPieces": 470,
    "totalWeight": 11750.0,
    "summaryForModel": "黄冰糖（袋）当前库存 11板30件，折合 470 件，总重量 11750kg。"
  },
  "lastAssayResult": null,
  "lastPalletResult": null,
  "pendingClarification": null,
  "toolCallHistory": [],
  "auditRefs": []
}
```

设计规则：

- `messages` 用于自然语言上下文，包含用户消息、助手消息、工具消息和用户选择事件。
- structured state 用于稳定工具调用，避免模型在后续轮次猜产品 ID、库位 ID 或托盘码。
- 内部 ID 可以存 state，用于后续工具调用，但不得进入普通用户 UI。
- 工具结果应存安全摘要，不保存无限量明细。
- `agentSessionId` 可以作为 LangGraph `thread_id` 的默认映射键。
- M1.3R 第一阶段只要求同一会话短期记忆，生产化再接 Redis/Postgres checkpointer。

## 7. 候选卡片与用户选择

### 7.1 resolver AMBIGUOUS 到 clarification event

当 `resolve_products` 或 `resolve_warehouses` 返回 `AMBIGUOUS` 时，Python 不得继续调用下游详情工具，也不得自行选择 ID。

Python 应生成：

```json
{
  "type": "clarification",
  "prompt": "“黄冰糖”有多个规格，请选择查询范围：",
  "options": [
    {
      "optionId": "opt-001",
      "optionType": "SINGLE_PRODUCT",
      "displayLabel": "黄冰糖（袋）",
      "supported": true
    },
    {
      "optionId": "opt-002",
      "optionType": "PRODUCT_TYPE_GROUP",
      "displayLabel": "全部黄冰糖大类",
      "supported": false,
      "disabledReason": "当前库存查询暂不支持产品大类聚合"
    }
  ]
}
```

普通 UI 展示 `displayLabel`、业务描述、是否可选和不可选原因，不展示 `productId`、`warehouseId` 或 toolName。

### 7.2 前端点击候选卡片

前端点击候选后仍先发给 Java，不直接发给 Python。Java 继续校验当前登录用户和 `agentSessionId`。

建议 Java 转发给 Python 的事件：

```json
{
  "type": "candidate_selected",
  "optionId": "opt-001",
  "optionType": "SINGLE_PRODUCT",
  "displayLabel": "黄冰糖（袋）"
}
```

前端不得把候选卡片展示文本伪装成新的自然语言消息，例如不应发送“黄冰糖（袋） 25.0kg/件 40件/板 当前库存”作为新 user message。

### 7.3 Python 写入 messages 和 structured state

Python 收到用户选择事件后，应同时写入：

- `messages`：记录一条 human selection event，例如“用户选择了候选项：黄冰糖（袋）”。
- `selectedProduct` 或 `selectedWarehouse`：保存已校验的内部 ID 和业务展示名。
- `pendingClarification`：清空或标记 resolved。

这样下一轮用户问：

```text
这些主要在哪些库位？
```

模型可以从 `messages` 和 state 中知道“这些”指向刚才用户选择的“黄冰糖（袋）”。工具调用时用 state 中已验证的 `productId`，普通回答时只显示“黄冰糖（袋）”。

## 8. 工具调用策略

### 短期方案：Python 调 Java internal Tool API

M1.3R-1b 已实现 Java 受控内部工具入口：

```text
POST /internal/agent/tools/{toolName}
```

允许工具仅限当前 6 个只读工具：

- `resolve_products`
- `resolve_warehouses`
- `get_inventory_overview`
- `get_warehouse_status`
- `get_pallet_status`
- `get_assay_status`

请求草案：

```json
{
  "agentSessionId": "agt_20260629_001",
  "toolCallId": "tool-001",
  "arguments": {
    "query": "黄冰糖",
    "limit": 10
  },
  "client": {
    "traceId": "trace-001",
    "requestId": "req-001"
  }
}
```

Java Tool Gateway 必须：

- 白名单校验工具名；
- 校验 `agentSessionId`；
- 做最终权限和数据权限判断；
- 调用 Java MCP 封装或 Java 业务只读接口；
- 记录 Agent Tool 审计和后端 API 审计；
- 返回结构化业务数据或统一错误；
- 拒绝任意 SQL、任意 HTTP、直接数据库访问和写接口。

当前 M1.3R-1b 采用方案 C：Java Internal Tool Gateway 复用 `McpSessionManager`，由 Java 按 `agentSessionId` 管理独立 STDIO MCP 进程并调用现有 6 个工具。Python 不管理 MCP 进程，也不接触委托 token。选择该方案是为了复用已有 resolver、schema、统一错误和只读黑名单，避免在主后端复制第二套工具规则。

内部接口额外使用 `X-Agent-Service-Key` 做服务间鉴权，密钥来自 `AGENT_INTERNAL_TOOL_SERVICE_KEY`；配置为空时默认拒绝。该密钥只用于 Java 与未来 Python 服务之间的内网认证，不是用户 token，也不得进入模型消息、工具参数、响应或审计摘要。

Gateway 请求参数限制为 `agentSessionId`、`toolCallId`、`arguments`、`traceId` 和 `requestId`。Java 根据会话恢复当前用户，检查会话状态、用户有效性和 `mcp:warehouse:read` scope，再由后端接口执行最终业务权限与数据权限判断。`arguments` 默认最大 16 KiB，可通过 `AGENT_INTERNAL_TOOL_MAX_ARGUMENTS_BYTES` 调整。

部署 Gateway 前必须执行 `migrations/2026-06-29-add-internal-agent-tool-gateway-audit.sql`，为 `agent_tool_audit_log` 增加 `tool_call_id`。审计写入失败时 Gateway 不返回本次工具结果，而返回安全的 `AUDIT_UNAVAILABLE`。

响应固定为 `toolName`、`toolCallId`、`status`、`result`、`error` 和 `auditRef`。错误使用安全通用消息；响应和审计摘要会过滤 token、Authorization、密码、数据库连接串、堆栈和内部绝对路径。

### 中长期方案：Python 作为 MCP Client

中长期目标：

```text
Python Agent Service -> HTTP/Streamable HTTP MCP -> Java MCP Server
```

优势：

- 工具协议统一；
- schema、错误结构和工具边界集中治理；
- 更适合多 Agent 和多用户并发；
- 请求级用户委托身份更清晰。

前置：

- Java MCP Server 支持 HTTP/Streamable HTTP；
- 支持请求级 `agentSessionId` 和用户委托身份注入；
- 支持超时、取消、审计和限流；
- 替代 STDIO 一用户一进程过渡模式。

无论短期还是长期，都必须遵守：

- Python 不调用任意 SQL；
- Python 不调用任意 HTTP；
- Python 不直接访问数据库；
- Python 不调用写接口；
- Java 仍然做最终权限判断和审计。

## 9. Tool message 回填

主路径应从 Java 模板回答迁移为模型基于工具结果生成自然语言回答。

推荐工具调用循环：

```text
用户消息
  -> 模型决定调用工具
  -> Python 调 Java tool
  -> Java 返回结构化工具结果
  -> Python 将工具结果写入 messages/state
  -> 模型基于工具结果生成最终回答
```

工具消息建议结构：

```json
{
  "role": "tool",
  "name": "get_inventory_overview",
  "toolCallId": "tool-002",
  "content": {
    "businessSummary": "黄冰糖（袋）当前库存 11板30件，折合 470 件，总重量 11750kg。",
    "safeData": {
      "productLabel": "黄冰糖（袋）",
      "displayStockInfo": "11板30件",
      "totalEquivalentPieces": 470,
      "totalWeight": 11750.0
    }
  }
}
```

规则：

- `safeData` 只包含业务安全字段和必要摘要。
- 内部 ID 可以存 state，不应进入普通用户消息内容。
- 模型看到的是安全业务摘要，不是数据库裸字段。
- Java 当前模板化回答降级为 fallback，只在 Python 不可用或模型失败时使用。
- 工具失败不能被解释为空数据，必须转为业务语言错误或可重试提示。

## 10. 流式输出协议

建议 Java 对前端暴露 SSE 或 WebSocket，Python 内部输出统一事件。事件类型：

```text
message_start
progress
clarification
tool_start
tool_end
text_delta
card
error
message_end
```

### 10.1 普通用户事件

`message_start`：

```json
{
  "messageId": "msg-001"
}
```

`progress`：

```json
{
  "stage": "resolve_product",
  "text": "正在确认产品范围..."
}
```

`clarification`：

```json
{
  "prompt": "“黄冰糖”有多个规格，请选择：",
  "options": [
    {
      "optionId": "opt-001",
      "displayLabel": "黄冰糖（袋）",
      "description": "25.0kg/件，40件/板",
      "supported": true
    }
  ]
}
```

`text_delta`：

```json
{
  "text": "黄冰糖（袋）当前库存为 "
}
```

`card`：

```json
{
  "cardType": "inventory_summary",
  "title": "黄冰糖（袋）库存",
  "fields": [
    {"label": "当前库存", "value": "11板30件"},
    {"label": "折合件数", "value": "470件"},
    {"label": "总重量", "value": "11750kg"}
  ]
}
```

`error`：

```json
{
  "message": "查询库存时遇到权限或服务问题，请稍后重试或联系管理员。",
  "retryable": true
}
```

普通用户不得看到：

- toolName；
- raw JSON；
- `productId`；
- `warehouseId`；
- token；
- Authorization Header；
- 后端异常堆栈；
- 数据库连接串；
- 内部服务器路径。

### 10.2 管理员调试事件

管理员调试模式可以额外接收脱敏事件，例如：

```json
{
  "debugType": "tool_call",
  "toolName": "get_inventory_overview",
  "durationMs": 180,
  "resultCode": "SUCCESS",
  "errorCode": null,
  "requestSummary": "productLabel=黄冰糖（袋）",
  "responseSummary": "displayStockInfo=11板30件,totalEquivalentPieces=470"
}
```

调试事件仍不得包含 token、Authorization Header、密码、完整堆栈或数据库连接串。

## 11. Human-in-the-loop 预留

M1.3R 阶段只实现候选选择，不实现写操作。但 state 和接口必须为未来 preview/execute 预留 interrupt/resume。

需要支持的 interrupt 类型：

- 候选项选择；
- 未来 preview 确认；
- 未来 execute 前确认；
- 用户拒绝操作；
- 用户修改参数；
- 用户要求重新预览。

候选选择流程：

```text
resolver 返回 AMBIGUOUS
  -> Python 产生 clarification interrupt
  -> Java 转为前端候选卡片
  -> 用户点击候选
  -> Java 调 /internal/agent/resume
  -> Python 写入 messages/state
  -> 继续 tool loop
```

未来写操作流程预留：

```text
preview
  -> 用户确认
  -> executionToken
  -> idempotencyKey
  -> execute
```

本阶段不得实现入库、出库、调拨、托盘确认、二维码作废/恢复、化验导入/更新/删除或质量标准修改。

## 12. 记忆策略

### 同一会话短期记忆

M1.3R 第一阶段只实现同一 `agentSessionId` 内的短期记忆。建议使用 LangGraph checkpointer，`agentSessionId` 映射为 `thread_id`。

短期记忆内容：

- 最近消息；
- 最近选择的产品；
- 最近查询的库存；
- 最近查询的库位；
- 最近查询的托盘；
- 最近查询的化验；
- 当前待用户选择的问题；
- 最近工具结果摘要。

### structured state

structured state 用于稳定工具调用，例如 `selectedProduct.productId`、`selectedWarehouse.warehouseId`、`pendingClarification.optionId`。这些 ID 可以存内部 state，但不得出现在普通 UI。

### tool result summary

工具结果应同时保存：

- 给模型的安全摘要；
- 给 UI 的业务卡片数据；
- 给审计关联的 `toolCallId` / `auditRefs`。

不保存无限量原始明细，不把数据库裸字段长期塞进模型上下文。

### 长期记忆候选方向

长期记忆暂不在 M1.3R 实现，只预留方向：

- 用户常用查询偏好；
- 常用产品或库位的非敏感标签；
- 未覆盖意图统计；
- 跨会话的安全业务摘要。

第一阶段不保存敏感业务数据长期快照，不保存 token，不保存原始 Authorization，不保存完整工具 raw response。

## 13. 鉴权与安全

必须遵守：

- Java 调 Python 使用内网服务鉴权。
- Python 不接触用户密码。
- Python 不接触 `delegationToken`。
- Python 不接触用户原始 token。
- Python 调 Java 工具必须携带 `agentSessionId`、`traceId` 和 `requestId`。
- Java 根据 `agentSessionId` 做最终权限和数据权限判断。
- 工具结果给模型前必须脱敏。
- 日志不得记录 token、Authorization、密码、堆栈、数据库连接串。

建议补充机制：

- Java 到 Python 使用服务间签名、mTLS 或内部网关 token。
- Python 到 Java Tool Gateway 使用服务间鉴权加 `agentSessionId`。
- Java 对每次工具调用重新检查 `agent_session` 状态，确保未撤销、未过期、用户仍有效。
- 所有内部接口加超时、限流和请求体大小限制。
- 所有日志走结构化脱敏器。

错误处理要求：

- 401/403 不得解释为空数据。
- timeout 不得解释为空数据。
- 5xx 不得解释为空数据。
- resolver `AMBIGUOUS` 必须追问，不得猜 ID。
- resolver `NOT_FOUND` 必须说明未找到，不得编造业务对象。

## 14. Fallback 策略

Python 不可用时，Java 可以 fallback，但 fallback 必须收敛，不应继续扩展为完整 Agent Runtime。

允许 Java 旧 `AgentGatewayService` fallback 的场景：

- Python `/internal/agent/health` 不可用；
- Python chat 超时；
- 模型服务短暂不可用；
- 用户请求属于当前 6 个只读工具能覆盖的简单查询；
- fallback 仍能遵守 resolver `UNIQUE` / `AMBIGUOUS` / `NOT_FOUND` 规则。

应提示“AI 助手暂不可用”的场景：

- 需要多步上下文推理；
- 需要工具结果回填后自然语言分析；
- 需要流式输出；
- 需要 human-in-the-loop resume；
- 需要未来 preview/execute；
- fallback 无法安全判断用户意图；
- fallback 可能导致猜 productId、warehouseId、托盘码或化验记录。

fallback UI 要求：

- 普通用户仍不展示 toolName、SUCCESS、内部 ID 或 raw JSON。
- 错误必须用业务语言解释。
- 管理员调试模式可展示 fallback 原因和脱敏错误码。

## 15. 分阶段实现计划

### M1.3R-1a：详细设计文档

目标：完成本文档，明确 Python 服务定位、接口、state、工具调用、流式事件、候选选择、鉴权和 fallback。

允许：新增/更新 Markdown 文档。

禁止：新增 Python 代码、修改 Java 业务逻辑、修改前端组件逻辑、新增 MCP 工具。

验收：本文能支撑后续 Python 骨架实现。

### M1.3R-1b：Java Internal Agent Tool Gateway

目标：在 Java 后端实现 `POST /internal/agent/tools/{toolName}`，为 Python 提供真实、受控、可审计的只读工具入口。

允许：仅调用当前 6 个 L1 只读工具；Java 管理 STDIO MCP 进程；按 `agentSessionId` 恢复用户身份；使用独立服务间鉴权；记录 `toolCallId` 和 Agent Tool 审计。

禁止：新增 MCP 工具、Python 服务代码、preview、execute、SQL、任意 HTTP 代理、直接数据库访问或业务写接口。

验收：白名单、会话状态、用户有效性、只读 scope、参数大小、统一错误、脱敏和审计测试通过；非白名单工具在绑定 MCP 会话前被拒绝。

### M1.3R-1c：Python Agent Service + 真实只读 Gateway

目标：新增 `agent-service/`，实现 FastAPI、LangGraph state、messages、tool loop 和 health，并直接接入 M1.3R-1b 的真实 Java Gateway，不以纯 mock 作为主验收路径。

允许：开发时保留极小 mock 作为单元测试夹具；运行时调用 Java Internal Tool Gateway 的 6 个只读工具。

禁止：Python 接触用户 token、delegationToken、Authorization、密码、数据库或 Java Mapper；禁止写操作。

验收：Python 以 `agentSessionId` 映射 thread_id，能通过真实只读数据完成 resolver、库存、库位、托盘和化验调用循环。


### M1.3R-1d：Domain Context Pack 与 ToolArgumentBuilder

目标：修正自然语言库位查询参数生成方式，同时保留 Java Gateway 超时配置修复。

允许：

- 将 Java Gateway 默认请求超时从 5 秒提高到 15 秒，并继续允许通过 `REQUEST_TIMEOUT_MS` 覆盖；
- 新增 `ContextBuilder`，每轮按用户消息和 state 构造小型 domain context pack；
- 当消息包含“库位 / 仓库 / 容量 / 存放 / 位置”等语义时，向模型上下文加入库位命名规则；
- 新增 `ToolArgumentBuilder` 和 `ModelClient` 抽象，由模型或 mock model 基于 messages、state、domain context 和 tool schema 生成工具参数；
- 库位查询应生成 `resolve_warehouses(query="2号库位")` 这类片段参数，再交给 Java resolver 归一化到数据库中的 `warehouseName="2"`。

禁止：

- 不新增 warehouse 专用正则作为 Runtime 主路径；
- 不猜 `warehouseId`；
- 不新增 MCP 工具；
- 不新增 preview、execute、SQL、任意 HTTP 代理、直接数据库访问或写接口调用。

验收：

- “2号库位现在还有多少容量？” 会先调用 `resolve_warehouses`，入参 `query` 为 `2号库位` 而不是整句；
- `ContextBuilder` 在库位语义消息中提供库位命名规则；
- 普通响应仍不暴露 `toolName`、`SUCCESS`、`productId`、`warehouseId`、token、Authorization 或堆栈；
- 既有 6 个只读工具范围不变。
### M1.3R-2：真实只读链路与 Agent Loop 验收

目标：完善 Python 对 6 个真实工具的错误处理、候选消歧、tool message 回填、超时和审计关联。

允许：调用 Java Internal Tool Gateway；补齐 Java/Python DTO 契约测试和端到端只读验收。

禁止：新增 preview、execute、SQL、HTTP 代理、数据库访问或写能力。

验收：权限和审计仍由 Java 控制；401/403/超时不解释为空数据；候选歧义不猜内部 ID。

### M1.3R-3：Java 转发 Agent 请求到 Python（已实现）

目标：Java 保持原前端 API，内部按配置调用 Python。

实现：

- Java `agent.runtime.mode=legacy|python`，默认 `legacy`；
- `PythonAgentClient` 调用 `GET /internal/agent/health` 和 `POST /internal/agent/chat`；
- `AGENT_PYTHON_SERVICE_KEY` 只作为 Java -> Python 的 `X-Agent-Service-Key` 请求头；
- `user_message` 与 `candidate_selected` 都通过 `/internal/agent/chat` 进入同一 Python conversation state；
- Java 只转发必要用户摘要、scope、白名单 page context、traceId 和 requestId；
- Java 将 Python cards/options/suggestions 映射为现有前端 VO，并再次删除内部 ID、工具字段和敏感文本；
- runtime 审计复用现有 Agent Tool 审计存储，不新增数据库结构。

Fallback：

- 首次明确简单只读问题可降级，降级后该 session 固定 legacy；
- 已由 Python 处理的会话、候选选择、上下文追问不得降级；
- 不可安全降级时返回统一业务错误，不返回 Python 错误正文或堆栈。

禁止：前端直连 Python；转发用户 token、delegationToken、Authorization、密码；新增写能力。

验收：前端 API 不变；服务间鉴权生效；普通响应不暴露内部字段；fallback 不造成两套 conversation state 混用。

### M1.3R-4：messages + tool result 回填 + 候选选择入历史

目标：候选卡片点击、工具结果和最终回答都进入 LangGraph messages/state。

验收：用户选择“黄冰糖（袋）”后，追问“这些主要在哪些库位？”和“它今天有没有化验？”无需重新选择。

### M1.3R-5：流式输出

目标：实现业务进度事件、候选事件、卡片事件和最终回答流式展示。

验收：普通 UI 只显示业务事件，管理员调试模式显示脱敏 tool 摘要。

### M1.3R-6：human-in-the-loop 预留

目标：为未来 preview/execute 建立 interrupt/resume 事件模型。

验收：候选选择使用统一 resume 模型；写操作仍未开放。

### M1.4：受控只读数据分析层

目标：支持库存分布、化验时间范围、报告详情、趋势分析等只读分析。

验收：有白名单实体和字段、分页/聚合/采样、审计和权限校验。

### M5：HTTP/Streamable HTTP MCP 与生产化

目标：迁移到 HTTP/Streamable HTTP MCP，多用户生产可用。

验收：请求级身份注入、多用户隔离、持久化 memory、监控告警、审计完整。

## 16. 验收标准

本文档必须能支撑后续回答和实现：

- Python 服务怎么启动：通过未来 `agent-service/README.md` 和 FastAPI 应用入口启动，本阶段定义目录与 health 接口。
- Java 怎么调用 Python：通过 `/internal/agent/chat`、`/internal/agent/chat/stream`、`/internal/agent/resume`、`/internal/agent/health`。
- Python 怎么维护上下文：通过 LangGraph `messages`、`WarehouseAgentState`、`agentSessionId` 到 `thread_id` 映射和短期 checkpointer。
- Python 怎么调用 Java 工具：短期调用 `POST /internal/agent/tools/{toolName}`，中长期作为 MCP Client 调 Java HTTP/Streamable HTTP MCP。
- 工具结果怎么回填给模型：以 tool message 和 structured state 写回，再由模型生成自然语言回答。
- 流式事件怎么发给前端：Python 产生 `message_start`、`progress`、`clarification`、`tool_start`、`tool_end`、`text_delta`、`card`、`error`、`message_end`，Java 转发给前端。
- 候选选择怎么进入上下文：前端点击候选后经 Java 转为 `candidate_selected` event，Python 写入 messages 和 structured state。
- 权限和审计在哪里做：最终权限、数据权限、`agent_session` 状态和工具/API 审计都在 Java；Python 只携带 `agentSessionId`、traceId 和 requestId。
- Python 不可用怎么 fallback：简单只读查询可走 Java 旧 Gateway；复杂多轮、流式、human-in-the-loop 和不确定场景应提示暂不可用。

本设计不新增 MCP 业务工具，不新增 login 工具，不新增 preview/execute，不开放 SQL、任意 HTTP 代理、直接数据库访问或写接口调用。

## 17. M1.3R-3 本地联调配置

Java：

```properties
agent.runtime.mode=python
agent.runtime.python-base-url=http://localhost:8091
agent.runtime.python-timeout-ms=30000
agent.runtime.python-service-key=${AGENT_PYTHON_SERVICE_KEY}
agent.runtime.fallback-enabled=true
```

Python：

```text
AGENT_TOOL_MODE=java_gateway
JAVA_TOOL_GATEWAY_BASE_URL=http://localhost:8080
AGENT_INTERNAL_TOOL_SERVICE_KEY=<Python 调 Java Gateway 的服务间 key>
AGENT_PYTHON_SERVICE_KEY=<Java 调 Python 的服务间 key>
REQUEST_TIMEOUT_MS=20000
```

两个 key 可以在本地使用相同开发值，但配置含义不同，生产应独立轮换。前端、LLM、普通响应和审计摘要均不得接触它们。

启动顺序：

1. 启动 Java 后端及 Internal Tool Gateway 依赖；
2. 启动 Python Agent Service；
3. 将 Java runtime mode 切换为 `python` 后重启 Java；
4. 启动 Web 前端，通过原 `/api/agent/sessions/{agentSessionId}/messages` 验收。

当前非流式链路已经接通。`/internal/agent/chat/stream` 尚未由 Java 代理给前端，留待 M1.3R-5。
