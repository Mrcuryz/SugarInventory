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
流式重点是业务过程事件，不只是把最终回答拆成 `text_delta`。

每个事件必须使用统一 envelope，至少包含：

```json
{
  "eventId": "evt_001",
  "messageId": "msg_001",
  "agentSessionId": "agt_20260629_001",
  "type": "progress",
  "sequence": 3,
  "payload": {}
}
```

字段要求：

- `messageId`：一次用户问题对应的 assistant message 容器 ID。
- `eventId`：单个事件 ID，用于前端和审计去重。
- `sequence`：同一 `messageId` 内递增序号，用于乱序保护。
- `type`：事件类型。
- `payload`：业务载荷；普通 UI 不直接展示 envelope 内部字段。

事件序列示例：

```text
event: message_start
data: {"eventId":"evt_001","messageId":"msg_001","agentSessionId":"agt_20260629_001","type":"message_start","sequence":1,"payload":{}}

event: progress
data: {"eventId":"evt_002","messageId":"msg_001","agentSessionId":"agt_20260629_001","type":"progress","sequence":2,"payload":{"stage":"resolve_product","text":"正在确认产品范围..."}}

event: clarification
data: {"eventId":"evt_003","messageId":"msg_001","agentSessionId":"agt_20260629_001","type":"clarification","sequence":3,"payload":{"prompt":"“黄冰糖”有多个规格，请选择：","options":[...]}}

event: message_end
data: {"eventId":"evt_004","messageId":"msg_001","agentSessionId":"agt_20260629_001","type":"message_end","sequence":4,"payload":{"finishReason":"clarification_required"}}
```

如果流式过程中 Python、Java Tool Gateway 或 Java 代理中途失败，连接不能静默断开，必须尽量输出：

```text
event: error
data: {"eventId":"evt_010","messageId":"msg_001","agentSessionId":"agt_20260629_001","type":"error","sequence":10,"payload":{"message":"查询仓储数据超时，请稍后重试。","retryable":true}}

event: message_end
data: {"eventId":"evt_011","messageId":"msg_001","agentSessionId":"agt_20260629_001","type":"message_end","sequence":11,"payload":{"finishReason":"timeout"}}
```

`message_end.payload.finishReason` 取值：

- `completed`
- `clarification_required`
- `error`
- `cancelled`
- `timeout`
- `fallback`

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

建议 Java 对前端暴露 SSE，Python 内部输出统一事件。M1.3R-5 的重点是业务过程流，不是单纯文本分片。

### 10.1 事件 envelope

所有 Python -> Java -> Web 的事件必须使用统一 envelope：

```json
{
  "eventId": "evt_001",
  "messageId": "msg_001",
  "agentSessionId": "agt_20260629_001",
  "type": "progress",
  "sequence": 3,
  "payload": {}
}
```

协议要求：

- `messageId` 表示一次用户问题对应的 assistant message 容器。
- `eventId` 表示单个事件，用于前端去重和审计定位。
- `sequence` 在同一 `messageId` 内单调递增，用于前端排序和乱序保护。
- 前端按 `messageId + eventId` 去重，按 `sequence` 渲染。
- 普通 UI 可以不显示 envelope 字段，但日志和调试应保留。

### 10.2 事件分类

用户可见业务事件：

```text
message_start
progress
clarification
text_delta
card
error
message_end
```

管理员调试事件：

```text
tool_start
tool_end
debug
```

系统控制事件：

```text
heartbeat
cancelled
timeout
fallback
```

普通用户只看到业务事件。管理员调试事件只允许在 `client.debug=true` 且当前用户具备管理员角色时返回，并且仍必须脱敏。

### 10.3 普通用户事件

`message_start`：

```json
{
  "eventId": "evt_001",
  "messageId": "msg_001",
  "agentSessionId": "agt_20260629_001",
  "type": "message_start",
  "sequence": 1,
  "payload": {}
}
```

`progress`：

```json
{
  "eventId": "evt_002",
  "messageId": "msg_001",
  "agentSessionId": "agt_20260629_001",
  "type": "progress",
  "sequence": 2,
  "payload": {
    "stage": "resolve_product",
    "text": "正在确认产品范围..."
  }
}
```

`clarification`：

```json
{
  "eventId": "evt_003",
  "messageId": "msg_001",
  "agentSessionId": "agt_20260629_001",
  "type": "clarification",
  "sequence": 3,
  "payload": {
    "prompt": "“黄冰糖”有多个规格，请选择：",
    "options": [
      {
        "optionId": "opt_001",
        "displayLabel": "黄冰糖（袋）",
        "description": "25.0kg/件，40件/板",
        "supported": true
      }
    ]
  }
}
```

`text_delta`：

```json
{
  "eventId": "evt_004",
  "messageId": "msg_001",
  "agentSessionId": "agt_20260629_001",
  "type": "text_delta",
  "sequence": 4,
  "payload": {
    "text": "黄冰糖（袋）当前库存为 "
  }
}
```

`card`：

```json
{
  "eventId": "evt_005",
  "messageId": "msg_001",
  "agentSessionId": "agt_20260629_001",
  "type": "card",
  "sequence": 5,
  "payload": {
    "cardType": "inventory_summary",
    "title": "黄冰糖（袋）库存",
    "fields": [
      {"label": "当前库存", "value": "11板30件"},
      {"label": "折合件数", "value": "470件"},
      {"label": "总重量", "value": "11750kg"}
    ]
  }
}
```

`error`：

```json
{
  "eventId": "evt_006",
  "messageId": "msg_001",
  "agentSessionId": "agt_20260629_001",
  "type": "error",
  "sequence": 6,
  "payload": {
    "message": "查询仓储数据超时，请稍后重试。",
    "retryable": true
  }
}
```

`message_end`：

```json
{
  "eventId": "evt_007",
  "messageId": "msg_001",
  "agentSessionId": "agt_20260629_001",
  "type": "message_end",
  "sequence": 7,
  "payload": {
    "finishReason": "completed"
  }
}
```

`message_end.payload.finishReason` 取值：

- `completed`：正常完成。
- `clarification_required`：已发出候选或追问，等待用户选择或补充。
- `error`：业务错误或不可恢复错误。
- `cancelled`：用户取消或客户端断开。
- `timeout`：工具、Python 或 Java 代理超时。
- `fallback`：尚未进入 Python 会话前的安全降级。

当输出 `clarification` 时，不能把它当作普通完成；必须以 `message_end.finishReason=clarification_required` 结束当前流，为 M1.3R-6 的 interrupt/resume 预留语义。

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

### 10.4 管理员调试事件

管理员调试模式可以看到脱敏后的 `tool_start`、`tool_end`、`debug`，用于定位耗时、错误码和工具路径。

示例：

```json
{
  "eventId": "evt_010",
  "messageId": "msg_001",
  "agentSessionId": "agt_20260629_001",
  "type": "tool_end",
  "sequence": 10,
  "payload": {
    "tool": "resolve_products",
    "status": "SUCCESS",
    "durationMs": 120,
    "resultSummary": "AMBIGUOUS, 8 candidates"
  }
}
```

调试事件仍不得包含 token、Authorization Header、密码、delegationToken、完整异常堆栈、数据库连接串、原始 tool payload 或未脱敏内部 ID。

### 10.5 系统控制事件

`heartbeat` 用于长耗时查询期间保持连接活性，不写入长期 messages。

`cancelled` 用于用户取消或客户端断开。当前阶段可以先做到：

- Java 检测客户端断开；
- Java 停止继续转发；
- Java 记录 `CANCELLED` 或 `CLIENT_DISCONNECTED` 审计；
- Python 真正取消执行可留到后续增强。

预留取消接口：

```text
POST /api/agent/sessions/{agentSessionId}/messages/{messageId}/cancel
```

`timeout` 用于 Java/Python/Tool Gateway 任一环节超时。流中超时必须输出 `error` 和 `message_end.finishReason=timeout`，不能直接断开连接。

`fallback` 只允许在尚未进入 Python runtime 的简单首轮只读查询中使用。只要同一个 `agentSessionId` 已经由 Python 处理过，后续不得 fallback 到 legacy Java AgentGateway，避免 Python state 与 Java legacy memory 分叉。

### 10.6 流式 state 写入边界

- 用户消息：收到请求后立即写入 state/messages。
- tool result：工具成功返回并经过 safe adapter 后写入 state/messages。
- assistant final answer：`message_end` 前写入 state/messages。
- progress：默认不写长期 messages，可写 event log 或审计摘要。
- clarification：必须写入 `pendingClarification`，否则前端看到候选后点击会接不上上下文。

### 10.7 前端渲染约束

前端不要把每条 `progress` 渲染成独立聊天气泡。一个用户问题对应一个 assistant message 容器：

- 顶部：当前进度短句；
- 中间：候选卡片或业务卡片；
- 底部：最终回答文本；
- 完成后：进度可折叠，只保留最终答案和卡片。

前端必须按 `messageId + eventId` 去重，按 `sequence` 排序或丢弃重复旧事件。

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

Python 不可用时，Java 可以 fallback，但 fallback 必须收敛，不应继续扩展为完整 Agent Runtime，也不得造成 Python state 与 Java legacy memory 分叉。

允许 Java 旧 `AgentGatewayService` fallback 的场景：

- 当前 `agentSessionId` 尚未进入 Python runtime；
- Python `/internal/agent/health` 不可用，或首次 Python chat/stream 建立前失败；
- 用户请求属于当前 6 个只读工具能覆盖的明确、简单、首轮只读查询；
- fallback 仍能遵守 resolver `UNIQUE` / `AMBIGUOUS` / `NOT_FOUND` 规则；
- fallback 后该 `agentSessionId` 固定走 legacy，不能再混入 Python state。

应提示“AI 助手暂不可用”的场景：

- 同一个 `agentSessionId` 已经由 Python runtime 处理过任意消息；
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

### M1.3R-5：流式交互首版（已完成）

目标：实现 Java 代理的业务过程流式输出，让用户看到“正在确认产品范围、正在查询库存、正在汇总结果、需要选择”等业务状态，而不是只把最终回答拆成文本片段。

当前实现状态：

- Python `/internal/agent/chat/stream` 已输出统一 event envelope，并在调用 runtime 前先发送 `message_start` 和业务 `progress`；
- Python clarification 流以 `message_end.payload.finishReason=clarification_required` 结束，工具超时以 `error + message_end.finishReason=timeout` 收口；
- Java 已新增 `POST /api/agent/sessions/{agentSessionId}/messages/stream`，通过 `HttpPythonAgentClient.stream` 代理 Python SSE；
- Java 代理层已做事件类型白名单、普通用户 debug 事件过滤、递归二次脱敏、流中失败补 `error + message_end`；
- Java stream fallback 遵守同一会话状态边界：已进入 Python runtime 的 `agentSessionId` 不再 fallback 到 legacy；
- 前端 `AgentAssistant` 已改为 `fetch + ReadableStream` 消费 POST SSE，一个用户问题对应一个 assistant message 容器；
- 前端 progress 只更新当前助手消息顶部状态，clarification/card/text_delta/error 分区渲染，并支持前端取消连接；
- 后端 cancel endpoint、完整审计分类和真实浏览器验收进入 M1.3R-5.1。

#### M1.3R-5a：流式事件协议固化

目标：

- 固化统一 event envelope：`eventId`、`messageId`、`agentSessionId`、`type`、`sequence`、`payload`；
- 固化 `message_end.payload.finishReason`：`completed`、`clarification_required`、`error`、`cancelled`、`timeout`、`fallback`；
- 区分用户可见业务事件、管理员调试事件和系统控制事件；
- 定义 `heartbeat`、`cancelled`、`timeout`、`fallback` 控制事件。

验收：

- 前端可按 `messageId + eventId` 去重，并按 `sequence` 排序；
- clarification 结束时必须返回 `finishReason=clarification_required`；
- 协议禁止 chain-of-thought、raw MCP frame、token、Authorization、内部 ID 和堆栈进入普通 UI。

#### M1.3R-5b：Python chat/stream 完善

目标：

- Python `/internal/agent/chat/stream` 输出业务 `progress`、`clarification`、`card`、`text_delta`、`error` 和 `message_end`；
- 中途 tool timeout 或 gateway 错误时输出 `error + message_end`，不能静默断流；
- clarification 必须写入 `pendingClarification`；
- tool result 成功并 safe adapter 后写入 messages/state；
- progress 默认只进 event log 或审计摘要，不写长期 messages。

验收：

- “帮我查黄冰糖当前库存”能输出确认产品范围、候选卡片和 `clarification_required`；
- 工具超时输出业务错误和 `finishReason=timeout`；
- Python 事件不包含 token、Authorization、内部 ID、raw JSON 或堆栈。

#### M1.3R-5c：Java SSE 代理

目标：

- 新增或规划 `POST /api/agent/sessions/{agentSessionId}/messages/stream`；
- 预留 `POST /api/agent/sessions/{agentSessionId}/messages/{messageId}/cancel`；
- Java 校验登录用户、session 归属、状态和 scope；
- Java 调用 Python `/internal/agent/chat/stream` 并注入 `AGENT_PYTHON_SERVICE_KEY`；
- Java 对所有事件做二次脱敏和 schema 校验；
- Java 处理中途失败，补发安全 `error + message_end`；
- Java 检测客户端断开，停止转发并记录 `CLIENT_DISCONNECTED`。

验收：

- Python 返回非法 event 时，Java 拒绝或转换为安全 error；
- Python event 中包含 `productId`、`warehouseId`、token 或 Authorization 时，Java 二次脱敏；
- 同一 `agentSessionId` 已走 Python 后不得 fallback 到 legacy；
- 审计记录 messageId、finishReason、duration、fallbackUsed 和错误码。

#### M1.3R-5d：前端 AgentAssistant 流式渲染

目标：

- 一个用户问题对应一个 assistant message 容器；
- 容器顶部显示当前进度短句；
- 容器中部展示候选卡片或业务卡片；
- 容器底部展示最终回答；
- 完成后可折叠进度，只保留最终答案和卡片；
- 普通模式隐藏 debug/tool 事件，管理员 debug 模式显示脱敏摘要。

验收：

- progress 不堆成多条独立聊天气泡；
- 前端按 `messageId + eventId` 去重，按 `sequence` 渲染；
- 候选选择后能继续同一会话上下文。

#### M1.3R-5e：端到端验收

场景：

- 黄冰糖候选；
- 选择黄冰糖（袋）后库存查询；
- “它今天有没有化验？”沿用 Python state；
- “2号库位现在还有多少容量？”；
- Python 断开；
- 工具超时；
- 普通 UI 脱敏；
- 管理员 debug 模式脱敏可见；
- 前端断开 SSE，Java 停止转发并记录。

验收：

- 普通 UI 只显示业务事件；
- 管理员调试模式显示脱敏 tool 摘要；
- 流式中断有 `error + message_end`；
- 已进入 Python 的会话不 fallback 到 legacy。

### M1.3R-5.1：流式链路加固

目标：补齐流式审计、Java 端到端 SSE 测试、后端 cancel endpoint、客户端断开处理、真实浏览器验收，以及上游失败/超时/正常结束分类。

当前实现状态：

- Java 已实现 `POST /api/agent/sessions/{agentSessionId}/messages/{messageId}/cancel`，校验当前用户与会话归属；
- Java 为流请求预分配 `messageId` 并传给 Python，主动取消后停止转发、发送 `cancelled + message_end` 并通知 Python；
- Python 已实现 `POST /internal/agent/cancel` 和进程内取消登记，运行返回后会抑制后续卡片、文本和工具结果事件；
- 审计已区分正常完成、主动取消、被动断开、Python 超时/错误、工具超时/错误、安全过滤和禁止降级；
- 已增加 Python 取消/分类测试，以及 Java HTTP cancel、主动取消审计、工具超时和安全过滤测试；
- 真实浏览器已验证正常回答、候选暂停与恢复、连续消息隔离、主动取消后按钮恢复、迟到结果抑制、自动滚动、刷新重置和 390px 移动端布局；
- 浏览器验收期间修复了 Spring Security 异步二次分派被拒绝、Vue 流消息对象未稳定触发增量渲染，以及 420px 固定抽屉超出移动端视口的问题；
- Python 当前基础 runtime 仍是同步调用，第一阶段取消只能保证结果不再转发，真正中断模型执行留到 M1.3R-5.2 的原生增量运行时。

取消链路：

- 前端调用 `POST /api/agent/sessions/{agentSessionId}/messages/{messageId}/cancel`；
- Java 校验当前用户和 session/message 归属后，通知 Python `POST /internal/agent/cancel`；
- message 状态标记为 cancelled，Java 停止转发后续事件；
- Python 第一阶段即使不能中断正在运行的模型，也必须阻止后续 tool result 或最终回答写回同一个 assistant message；
- 用户主动取消记录 `CLIENT_CANCELLED`，网络或页面导致的被动断开记录 `CLIENT_DISCONNECTED`。

审计结果包括：`COMPLETED`、`CLIENT_DISCONNECTED`、`CLIENT_CANCELLED`、`PYTHON_TIMEOUT`、`PYTHON_ERROR`、`TOOL_TIMEOUT`、`TOOL_ERROR`、`SECURITY_FILTERED`、`FALLBACK_BLOCKED`。

真实浏览器验收结果：长回答保持自动滚动到底部；clarification 卡片出现后停止等待；取消后发送按钮恢复且迟到结果不写回；390px 视口抽屉和卡片均不溢出；连续消息不串流；刷新后关闭临时抽屉并保留可重新进入的助手入口。

### M1.3R-5.2：模型原生增量输出

目标：接入 LLM token delta，并继续保留 `progress`、`clarification`、`card`、`error` 和 `message_end` 等结构化事件。

边界：不得展示 chain-of-thought；取消、超时、脱敏和审计规则继续沿用 M1.3R-5.1。

当前实现：Python 已提供 `AgentRunRegistry` 和 `CancellationToken`，每条流式消息按 `messageId` 注册 active run；runtime 在模型调用前、模型流期间、工具调用前后、工具结果写 state 前、最终回答输出前检查取消信号。`AGENT_MODEL_MODE=basic` 只输出已审核答案作为兼容 delta，不伪装 token；`AGENT_MODEL_MODE=openai_compatible` 调用配置的 OpenAI-compatible `/chat/completions` SSE，`text_delta` 来自模型实时 `delta.content` 或 `response.output_text.delta`。reasoning、chain-of-thought、tool calls、raw JSON、token 和内部字段均被忽略或过滤。模型超时、工具超时、模型/工具取消和上游错误通过 `error.category` 分类，Java runtime audit 映射为 `MODEL_TIMEOUT`、`TOOL_TIMEOUT`、`MODEL_CANCELLED`、`TOOL_CANCELLED`、`UPSTREAM_ERROR` 或 `CLIENT_CANCELLED`。

### M1.3R-6：Human-in-the-loop 统一模型

目标：统一 clarification resume、preview 确认、execute 前确认，以及用户拒绝、修改和重新预览的 interrupt/resume 事件模型。

边界：本阶段只建设统一状态与协议模型，并把现有 clarification 迁移到该协议。preview / execute confirmation 只做协议、状态、审计和 UI 占位，不接入入库、出库、调拨等真实写工具。

阶段拆分：

- M1.3R-6a：协议与状态机。定义 `HitlInterrupt`、`ResumeAction`、`status`、`finishReason`、audit code、`resumeToken` 能力凭证规则和 action/kind 允许矩阵。
- M1.3R-6b：clarification 迁移。现有候选选择改成 interrupt；前端只提交 opaque `optionId`；后端用 `interruptId + optionId` 恢复已验证业务实体；兼容旧 `candidate_selected`。
- M1.3R-6c：Java resume API 与审计。新增统一 resume 入口，Java 校验 user/session/status 并记录 interrupt 最小元数据，转发 Python `/internal/agent/resume`。
- M1.3R-6d：前端统一卡片。clarification 卡片走统一 interrupt；preview / execute confirmation 只占位；展示处理中、已处理、已过期、已取消等用户可理解状态。
- M1.3R-6e：验收与文档。覆盖正常选择、重复点击、过期点击、刷新恢复或失效提示、不暴露内部字段、不触发写操作。

`resumeToken` 按一次性能力凭证处理：

- 短期有效、单次使用、不可预测；
- 绑定 `agentSessionId`、`interruptId`、`userId`、action 类型和 `optionId` / `previewId`；
- 不写普通日志，不进入前端可见调试信息；
- 过期后不可恢复，必须重新发起用户请求；
- 同一个 `clientRequestId` 重放时返回同一次 resume 结果；不同 `clientRequestId` 命中已 `RESUMED` interrupt 时返回已处理或已失效，不得重复调用工具或重复写 state。

前端候选项必须使用 opaque id：

```json
{
  "optionId": "opt_003",
  "displayLabel": "黄冰糖（袋）",
  "description": "25.0kg/件 40件/板",
  "supported": true
}
```

前端 resume 只提交：

```json
{
  "action": "SELECT_OPTION",
  "selection": {
    "optionId": "opt_003"
  },
  "clientRequestId": "..."
}
```

不得把 `productId`、`warehouseId`、`inventoryId` 等内部 ID 放入普通前端事件。内部映射保存在 pending interrupt state 中：`interruptId + optionId -> 已验证的业务实体`。

状态机固定为：

```text
PENDING
  -> RESUMED
  -> REJECTED
  -> MODIFIED
  -> EXPIRED
  -> CANCELLED
```

非法转换必须拒绝：`RESUMED` 不能再次 resume，`REJECTED` 不能 approve，`EXPIRED` 不能 select option，`CANCELLED` 不能恢复。用户可见文案使用“这个选择已经处理过了”“这个确认已过期，请重新发起”“这个任务已取消”，不得返回内部状态机错误。

`message_end.finishReason` 固定枚举：`completed`、`interrupt_required`、`cancelled`、`timeout`、`error`、`rejected`、`expired`。产生 interrupt 时事件序列为：

```text
message_start
progress
clarification / hitl_interrupt
message_end { finishReason: "interrupt_required", interruptId, interruptKind }
```

action 与 interrupt kind 的允许矩阵：

```text
CLARIFICATION:
  SELECT_OPTION / CANCEL

PREVIEW_CONFIRMATION:
  APPROVE / REJECT / MODIFY / REQUEST_REPREVIEW / CANCEL

EXECUTE_CONFIRMATION:
  APPROVE / REJECT / CANCEL
```

resume 需要支持流式继续。建议提供 `POST /api/agent/sessions/{agentSessionId}/interrupts/{interruptId}/resume/stream`，或让 resume 默认返回 SSE；短期如果保留非流式，协议中仍需明确后续 resume stream 能力，避免普通消息流式而候选选择后非流式的体验断层。

pending interrupt 分层：Python 保存运行态、option 映射和 pending interrupt 细节；Java 至少记录 interrupt 审计和最小元数据，包括 `messageId`、`interruptId`、`userId`、`agentSessionId`、`kind`、`status`、`expiresAt`。M1.3R-6 可以不做完整持久化，但不能只有 Python 内存知道 interrupt 的存在，否则刷新、重启和排查时无法区分过期、取消、重启丢失或非法请求。

当前实现：clarification 已迁移到统一 interrupt/resume 基础模型。Python 生成 `interruptId`、一次性 `resumeToken`、`expiresAt` 和 opaque `optionId`，并在 resume 时校验 token、状态、过期时间和 `clientRequestId` 幂等。Java 暴露 `/interrupts/{interruptId}/resume` 与 `/interrupts/{interruptId}/resume/stream`，前端候选点击走 stream resume。Java 新增 `agent_interrupt_state` 最小元数据表，只保存 `interruptId`、session/user/message、kind、status、expiresAt、action、opaque option/preview id、clientRequestId 和终态结果，不保存 `resumeToken` 明文。

验收：现有黄冰糖候选选择体验不退化；clarification 事件携带 `interruptId`、`resumeToken`、`expiresAt`；用户选择通过统一 resume 入口恢复；重复点击不会重复执行；过期、取消、拒绝都有明确状态和审计；前端不展示内部 ID、toolName、raw JSON、chain-of-thought；不新增任何真实写操作。

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
- 流式事件怎么发给前端：Python 产生统一 envelope 事件，包含 `message_start`、`progress`、`clarification`、`text_delta`、`card`、`error`、`message_end`、`heartbeat`、`cancelled`、`timeout`、`fallback`，管理员 debug 模式才允许脱敏 `tool_start`、`tool_end`、`debug`，Java 校验、二次脱敏后转发给前端。
- 候选选择怎么进入上下文：前端点击候选后经 Java 转为 `candidate_selected` event，Python 写入 messages 和 structured state。
- 权限和审计在哪里做：最终权限、数据权限、`agent_session` 状态和工具/API 审计都在 Java；Python 只携带 `agentSessionId`、traceId 和 requestId。
- Python 不可用怎么 fallback：仅未进入 Python runtime 的首次简单只读查询可走 Java 旧 Gateway；同一 `agentSessionId` 一旦进入 Python，后续不得 fallback 到 legacy，复杂多轮、流式、human-in-the-loop 和不确定场景应提示暂不可用。

本设计不新增 MCP 业务工具，不新增 login 工具，不新增 preview/execute，不开放 SQL、任意 HTTP 代理、直接数据库访问或写接口调用。

## 17. M1.3R 本地联调配置

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
5. M1.3R-5 流式验收时，Java 对前端暴露 `/api/agent/sessions/{agentSessionId}/messages/stream`，内部代理 Python `/internal/agent/chat/stream`。

当前非流式、M1.3R-5 首版流式、M1.3R-5.1 链路加固和 M1.3R-5.2 原生模型 token streaming 边界均已接通。后续可继续增强模型规划/最终回答生成质量，或进入 M1.3R-6 Human-in-the-loop 统一模型。
