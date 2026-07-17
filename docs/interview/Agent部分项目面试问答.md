# 来宾糖厂仓储管理系统：Agent 部分面试问答

## 1. 一句话简介

Agent 部分是在现有仓储系统之上构建的智能仓储助手，让用户用自然语言查询库存、库位、托盘、化验和质量异常。系统通过 Java Agent Gateway、Python Agent Runtime 和只读 MCP Tools 编排业务能力，同时严格限制写操作，避免模型直接改库存。

面试时可以这样说：

> 我们没有把大模型直接接数据库，也没有把所有 REST 接口暴露给 Agent，而是设计了“自然语言理解 -> 意图规划 -> 受控工具调用 -> 安全结果适配 -> 业务化回答”的链路。第一阶段只开放 L0/L1/L2 能力，尤其是只读查询和预览，入库、出库、调拨等写操作必须未来走 preview、确认、executionToken、幂等和审计。

## 2. 为什么要做 Agent

仓储系统虽然有 Web 和小程序，但真实用户经常问的是自然语言问题：

- “黄冰糖现在还有多少？”
- “这些主要在哪些库位？”
- “2号库位现在满了吗？”
- “这个托盘现在是什么状态？”
- “最近 7 天有哪些化验异常？”
- “帮我看下哪些库存没有化验风险。”

传统页面需要用户知道入口、筛选条件和字段含义。Agent 的目标是把这些查询变成自然语言交互，同时保持权限、审计和业务安全。

## 3. 总体架构

```text
Web / 小程序
  -> Java 后端 Agent Session API
  -> Agent Gateway
  -> Python Agent Runtime
  -> Java Internal Agent Tool Gateway
  -> warehouse-mcp
  -> 仓储后端只读 API
  -> 安全结果适配
  -> 自然语言回答 / 卡片 / 候选项 / SSE 事件
```

核心组件：

- 前端助手 UI：负责会话、流式展示、候选选择、卡片渲染。
- Java Agent Gateway：负责登录用户绑定、Agent session、权限上下文、Java 到 Python 转发、SSE 代理、审计。
- Python Agent Runtime：负责意图规划、上下文状态、候选消歧、工具参数构造、结果总结。
- Internal Agent Tool Gateway：Java 内部工具网关，只允许调用白名单工具。
- warehouse-mcp：只读 MCP Server，封装安全仓储工具，不连接数据库。
- 后端业务系统：最终权限判断、业务规则和只读聚合接口。

## 4. 技术栈

Java 侧：

- Spring Boot 3
- Spring Security + JWT
- Agent Session / Delegation Token
- SSE 流式响应
- MCP STDIO Session Manager
- 工具调用审计

Python 侧：

- Python 3.11+
- FastAPI
- Pydantic 2
- Uvicorn
- LangGraph-compatible conversation state
- OpenAI-compatible 模型客户端接口
- pytest / httpx

MCP 侧：

- Spring AI MCP Server
- STDIO transport
- Java HTTP Client 调用仓储后端只读 API
- 白名单工具 Schema

前端：

- Vue 3
- fetch + ReadableStream 消费 POST SSE
- 候选卡片、进度事件、回答流式输出

## 5. 设计原则

### 不做任意 SQL / 任意 HTTP

Agent 不允许：

- 直接连接数据库。
- 执行任意 SQL。
- 调用任意 HTTP。
- 拼接任意业务写接口。
- 猜测产品 ID、库位 ID、库存 ID。
- 把权限错误或工具失败解释为空数据。

这样做的原因：

> 仓储系统是高一致性业务系统。大模型可以理解语言，但不能成为权限边界和数据一致性的最终责任方。所有数据访问必须经过后端权限和受控工具。

### 查询和写入分离

当前阶段允许：

- L0：帮助、规则、能力说明。
- L1：只读实时查询。
- L2：业务预览、dry-run、分析，不修改库存主数据。

当前阶段禁止：

- L3：真实入库、出库、调拨、托盘确认。
- L4：删除、回滚、权限、配置、质量标准修改。

未来写操作必须走：

```text
resolver / 信息补全
  -> preview
  -> 用户确认
  -> executionToken
  -> idempotencyKey
  -> execute
  -> 审计
```

## 6. 当前 MCP 工具能力

当前白名单是只读工具，面向业务目标，不是一对一 REST 映射。

| 工具 | 业务目标 | 风险 |
| --- | --- | --- |
| `resolve_products` | 解析产品名称、规格或产品范围 | L1 |
| `resolve_warehouses` | 解析自然语言库位 | L1 |
| `get_inventory_overview` | 查询单产品库存概览 | L1 |
| `get_inventory_distribution` | 查询库存分布和聚合 | L1 |
| `get_warehouse_status` | 查询库位容量和状态 | L1 |
| `get_pallet_status` | 查询托盘当前状态和流转 | L1 |
| `get_assay_status` | 查询单产品/单日期化验状态 | L1 |
| `query_assay_records` | 查询化验记录范围和摘要 | L1 |
| `query_assay_abnormalities` | 查询化验质量异常聚合 | L1 |

重要边界：

- MCP Server 不保存用户密码。
- MCP Server 不返回 delegation token。
- MCP Server 不开放 login 工具。
- MCP Server 不连接数据库。
- MCP Server 不开放写操作。

## 7. 意图识别怎么做

项目不是简单用关键词硬编码所有分支，而是把模型规划和安全校验分开。

基本流程：

```text
用户消息 + 会话状态 + 领域上下文 + 工具能力说明
  -> ModelClient.plan_next_action
  -> 得到候选动作：回答 / 澄清 / 工具调用
  -> Runtime 校验工具名、参数和状态
  -> 调用只读工具
  -> 安全适配结果
  -> 生成用户可见回答
```

意图大类：

- 产品库存查询：先解析产品，再查库存概览。
- 产品分布查询：先解析产品，再查库存分布。
- 库位内容查询：先解析库位，再按库位查库存分布。
- 库位容量查询：先解析库位，再查库位状态。
- 托盘状态查询：有明确托盘码时查托盘。
- 化验状态查询：产品和日期明确后查化验。
- 化验记录范围查询：按产品范围、日期范围、判定状态查询。
- 质量异常查询：按异常类型和维度聚合。
- 写操作请求：当前识别后拒绝执行，并说明安全边界。

面试可回答：

> 模型负责“理解用户想做什么”，Runtime 负责“能不能做、参数是否合法、是否需要用户选择”。最终能调用的工具和参数都经过白名单和 Schema 校验，所以模型不会因为幻觉调用不存在的接口。

## 8. 知识库和业务上下文

项目里的“知识库”不是直接把所有文档塞给模型，而是拆成可控的业务上下文和工具能力说明。

主要来源：

- `docs/agent/runtime-playbook/*`：助手身份、术语、库存规则、产品/库位/托盘关系、化验状态、失败案例。
- `docs/agent/tool-capability-registry.yaml`：每个工具能回答什么、不能回答什么、什么时候用、反例是什么。
- `docs/mcp/mcp-tool-registry.md`：MCP 工具清单、风险等级、实现状态和禁止边界。
- 业务代码和只读聚合接口：库存、化验、库位、托盘事实以实时工具查询为准。

知识组织方式：

- 静态规则：如“不能猜产品 ID”“普通用户不展示 toolName”。
- 领域术语：如板、件、库位、托盘、化验、生产日期。
- 工具能力：每个工具的适用意图和禁用场景。
- 上下文包：根据用户问题动态注入产品、库位、托盘、化验等相关规则。

面试可回答：

> 我们没有把知识库当成万能 RAG，而是把它做成“运行时规则 + 工具能力 registry + 领域上下文包”。实时库存和化验事实必须查工具，静态文档只用于指导模型怎么问、怎么解释、什么时候拒绝。

## 9. 上下文管理

Agent 必须支持连续对话，例如：

```text
用户：查黄冰糖（袋）库存
助手：当前库存为 ...
用户：这些主要在哪些库位？
助手：沿用刚才的黄冰糖（袋）查询库存分布
用户：它今天有化验吗？
助手：沿用刚才产品，按今天查询化验
```

当前上下文状态包括：

- 最近确认的产品或产品范围。
- 最近确认的库位。
- 最近查询的托盘。
- 最近查询的化验。
- pending clarification。
- 用户候选选择状态。
- 安全工具结果摘要。
- 最近几轮 message history。

关键做法：

- 前端候选选择只提交 `optionId`，不提交内部 `productId`。
- 后端 pending state 保存 `optionId` 到真实业务实体的映射。
- 用户选择后更新 structured state。
- 后续问题中的“它”“这些”“刚才那个产品”从 structured state 解析。
- 会话撤销后清理上下文。

面试可回答：

> 上下文不能只靠拼接历史文本，否则模型可能把候选项或 ID 记错。我们把会话记忆拆成 message history 和 structured state，真正可复用的业务对象必须来自 resolver 或用户选择，不接受模型自己编的 ID。

## 10. 消歧与 Human-in-the-loop

产品和库位经常有歧义。

示例：

```text
用户：查黄冰糖库存
```

系统不能直接猜测一个产品 ID，而是：

```text
resolve_products
  -> 返回 UNIQUE / AMBIGUOUS / NOT_FOUND
  -> AMBIGUOUS 时生成候选卡片
  -> 等待用户选择
  -> resume 后继续查询
```

消歧原则：

- `UNIQUE`：可以继续查询。
- `AMBIGUOUS`：必须让用户选择。
- `NOT_FOUND`：说明未找到，不编造。
- `NORMALIZED_NAME`：库位自然语言归一化后可继续查询，并说明匹配关系。

HITL 设计：

- interrupt / resume 模型统一候选选择和未来确认流程。
- `resumeToken` 短期有效、单次使用、绑定用户、会话、interrupt 和 action。
- 前端只展示业务标签，不暴露内部 ID。
- 当前消息以 `interrupt_required` 或 `clarification_required` 结束。

## 11. 安全结果适配

工具返回结果不能直接给模型或用户展示。

处理链路：

```text
raw_gateway_result
  -> safe_business_result
  -> answer / SSE event / card
```

适配规则：

- 普通用户不显示 `toolName`、`SUCCESS`、`productId`、`warehouseId`、raw JSON。
- 不返回 token、Authorization、内部堆栈、数据库路径。
- 库存回答优先用 `displayStockInfo`、标准化板件、等价总件数、重量。
- 库位回答只保留库位名称、状态、容量、占用率、剩余容量等安全字段。
- 化验回答只展示业务结果、异常指标和风险提示。
- SSE 事件也做递归脱敏。

面试可回答：

> 工具结果和用户回答之间有一层 safe adapter。它的作用是把内部字段、ID、raw JSON 和异常信息过滤掉，只保留业务可解释字段。这样即使上游工具返回了很多内部信息，普通用户和模型后续上下文也拿不到敏感数据。

## 12. Agent Gateway 设计

Java Gateway 的职责：

- 创建和校验 Agent session。
- 绑定当前登录用户。
- 管理会话撤销和过期。
- 将用户消息转发给 Python Runtime。
- 代理 Python SSE 流。
- 过滤非管理员 debug 事件。
- 记录工具调用和回答反馈审计。
- 注入用户委托身份给 MCP。

关键接口：

- `POST /api/agent/sessions`
- `POST /api/agent/sessions/{agentSessionId}/messages`
- `POST /api/agent/sessions/{agentSessionId}/messages/stream`
- `POST /api/agent/sessions/{agentSessionId}/interrupts/{interruptId}/resume`
- `POST /api/agent/sessions/{agentSessionId}/messages/{messageId}/cancel`

授权边界：

- 前端只有普通 JWT。
- Agent 和 MCP 不拿用户登录 token。
- 后端内部签发短期 delegated identity。
- STDIO 过渡方案是一用户一 MCP 进程。
- 生产多用户更推荐 HTTP / Streamable HTTP MCP，请求级注入身份。

## 13. 流式输出设计

流式输出不是简单把最终答案拆成字，而是业务事件流。

事件类型：

- 用户可见：`message_start`、`progress`、`clarification`、`text_delta`、`card`、`error`、`message_end`
- 管理员调试：`tool_start`、`tool_end`、`debug`
- 系统控制：`heartbeat`、`cancelled`、`timeout`、`fallback`

事件 Envelope：

```json
{
  "eventId": "evt_001",
  "messageId": "msg_001",
  "agentSessionId": "agt_xxx",
  "type": "progress",
  "sequence": 3,
  "payload": {}
}
```

设计重点：

- 一个用户问题对应一个 assistant message 容器。
- progress 不独立变成多条聊天气泡。
- clarification 以 `finishReason=clarification_required` 暂停。
- 用户取消后抑制迟到工具结果。
- 工具超时要发业务错误和 `message_end`，不要直接断流。
- 普通模式过滤 debug 事件。

## 14. 工具调用审计和回答复核

审计目标：

- 谁在什么会话里问了什么。
- Agent 调用了哪些受控工具。
- 工具调用是否成功、超时、取消或被安全过滤。
- 回答是否被用户反馈有问题。

项目中包含：

- Agent tool audit log。
- Agent API audit filter。
- Agent message review。
- Evidence correlation。
- Answer review plan。

面试可回答：

> Agent 系统必须可追责。我们不仅记录用户消息，还记录工具调用摘要、结果分类、耗时和回答反馈。尤其在仓储系统里，工具失败不能被解释为空数据；审计能帮助定位是权限问题、工具超时、上游接口错误，还是模型解释不准确。

## 15. 主要难点与解决方案

### 难点 1：不能让模型直接操作库存

问题：

- 大模型可能幻觉参数。
- 写库存是高风险操作。
- 出入库会影响多表一致性。

解决：

- 当前只开放 L1 只读工具。
- 写操作统一拒绝或说明未来 preview/execute 边界。
- MCP 不开放任意 SQL、任意 HTTP、任意写接口。
- 未来执行必须有 preview、确认、executionToken、幂等和审计。

### 难点 2：产品和库位有自然语言歧义

问题：

- “黄冰糖”可能是大类、产品名组或具体规格。
- “2号库位”不等于 `warehouseId=2`，数据库里可能是库位名称 `2`。

解决：

- 先用 resolver 解析。
- resolver 返回候选项和消歧提示。
- 模型不得自行选择 ID。
- 库位支持自然语言归一化，但归一化数字只按名称查，不当作内部 ID。

### 难点 3：上下文指代容易错

问题：

- 用户会说“这些”“它”“刚才那个”。
- 如果只拼历史文本，模型可能引用错对象。

解决：

- 引入 structured state。
- selectedProduct、selectedWarehouse 等必须来自 resolver 或用户选择。
- 工具结果只保存安全摘要。
- pending clarification 明确记录当前等待用户选择的问题。

### 难点 4：工具返回信息和用户回答不是一回事

问题：

- 工具结果有内部 ID、raw 字段、错误码。
- 普通用户要看业务答案。

解决：

- safe adapter 白名单字段。
- 普通模式隐藏工具名、ID、raw JSON。
- 管理员调试模式只显示脱敏摘要。
- 错误按业务语言解释。

### 难点 5：流式交互和取消处理

问题：

- 工具调用可能超时。
- 用户可能取消。
- 后端结果可能迟到。

解决：

- 使用稳定 SSE envelope。
- 事件按 `messageId + eventId` 去重，按 sequence 渲染。
- 取消后 suppress 后续工具结果和模型 delta。
- 结果分类区分 completed、timeout、cancelled、tool_error 等。

### 难点 6：Java、Python、MCP 三层协作

问题：

- Java 是原业务系统和权限边界。
- Python 更适合 Agent Runtime。
- MCP 是工具协议层。

解决：

- Java 仍作为前端入口和权限入口。
- Python 不连数据库，只调 Java Internal Tool Gateway。
- MCP 不和用户对话，只提供安全工具能力。
- 服务间使用内部 service key，不把用户 token 传给模型或前端。

## 16. 项目亮点

- 把 Agent 做成业务助手，而不是接口搜索器。
- 工具按业务目标设计，不把 REST API 一对一暴露。
- 只读、预览、执行、管理按风险等级分层。
- resolver + HITL 避免模型猜产品/库位 ID。
- structured state 支持“它”“这些”等上下文指代。
- safe adapter 防止内部 ID、token、raw JSON 泄露。
- Java Gateway 保留后端权限边界，Python 只做 Agent Runtime。
- MCP Server 不连数据库，不开放写能力。
- SSE 事件区分业务进度、卡片、最终回答、错误和调试事件。
- 写操作设计了未来 preview -> confirmation -> executionToken -> idempotencyKey -> execute 的安全闭环。

## 17. 高频面试问答

### Q1：这个 Agent 和普通 ChatGPT 套壳有什么区别？

普通套壳只把用户问题发给模型，而这个 Agent 接入了仓储系统的实时数据和权限体系。它会根据意图调用受控 MCP 工具查询库存、库位、托盘和化验，回答来自真实后端数据。同时它有 resolver、上下文、审计、SSE、安全脱敏和写操作边界，不允许模型直接操作数据库。

### Q2：为什么不用 RAG 直接查文档回答？

库存、库位、托盘和化验都是实时业务数据，不能靠文档回答。RAG 或静态知识只用于解释术语和工具规则，事实数据必须通过后端只读工具查询。否则用户问“现在库存多少”时，文档内容一定会过期。

### Q3：为什么要引入 MCP？

MCP 给 Agent 和业务系统之间提供了标准工具边界。我们可以把仓储能力封装为结构化工具，定义输入输出和风险等级，并通过白名单限制模型只能调用安全工具。它比让模型直接调用 REST API 更容易做权限、安全、审计和工具演进。

### Q4：MCP 工具为什么不直接连接数据库？

数据库不是 Agent 的安全边界。直接连库会绕过后端权限、业务规则和审计，也容易变成任意 SQL。项目里 MCP 只调用后端受控只读接口，最终权限判断仍由 Java 后端完成。

### Q5：Agent 如何处理“黄冰糖”这种歧义？

先调用 `resolve_products`。如果唯一匹配，就继续查询；如果多候选，就返回候选卡片让用户选；如果没找到，就说明未找到。模型不能自己选一个产品 ID，也不能把产品大类、产品名组和具体规格混为一谈。

### Q6：上下文管理怎么实现？

除了聊天历史，还维护 structured state，比如最近确认的产品、库位、托盘、化验和 pending clarification。后续用户说“它”“这些”“刚才那个产品”时，Runtime 会从 structured state 找上下文，而不是让模型凭历史文本猜。

### Q7：如果用户要求“帮我出库”，Agent 怎么办？

当前阶段不会执行出库。它会说明当前只支持只读查询和部分预览设计，不支持直接写库存。未来要支持，也必须先做出库预览，展示将扣哪些库存和风险，用户确认后由后端签发 executionToken，再带幂等键执行。

### Q8：如何防止敏感信息泄露？

有多层防护：前端不接收 delegated token；Python 不接收用户登录 token；MCP 不返回 token；safe adapter 过滤内部 ID、raw JSON、堆栈和 Authorization；Java SSE 代理还会做二次递归脱敏。普通用户界面不显示工具名和内部字段。

### Q9：为什么 Java 和 Python 分开？

Java 是原业务系统，负责权限、会话、业务接口和审计；Python 更适合做 Agent Runtime、模型适配、上下文管理和工具规划。分开后各自职责清晰：Java 保持安全边界，Python 做智能编排，MCP 做工具协议层。

### Q10：你们怎么测试 Agent？

Java 侧有 Agent session、JWT delegation、Internal Tool Gateway 等测试；Python 侧有 pytest 覆盖 Runtime、工具参数构造、上下文和安全输出；MCP 侧有工具回调和只读服务测试；前端还有 Agent HITL 相关 Playwright 测试。关键不是只测模型回答，而是测工具选择、消歧、脱敏、取消、超时和上下文续问。

### Q11：如果工具调用失败怎么办？

工具失败不能解释为空数据。系统会根据错误类型返回业务可理解的错误，比如权限不足、工具超时、上游服务不可用或参数不明确。SSE 中也会发送 `error` 和 `message_end`，而不是直接断开。

### Q12：Agent 的知识库怎么更新？

静态规则主要在 runtime playbook 和 tool capability registry 中维护；MCP 工具清单和风险等级在 MCP registry 中维护。新增工具时需要更新工具 schema、能力说明、安全适配、测试和文档，不能只改模型提示词。

### Q13：为什么普通用户界面不展示 toolName？

普通用户关心的是业务答案，不是内部工具调用。展示 toolName、SUCCESS、raw JSON 会让体验变成接口调试器，也可能泄露内部实现。管理员调试模式可以看脱敏工具摘要，用于排查问题。

### Q14：你怎么定义 Agent 项目的边界？

Agent 是业务助手，不是管理员脚本执行器。它可以帮助用户查数据、解释数据、提示风险、做受控预览；但不能绕过权限、直接改库存、执行 SQL、调用任意接口或替用户做高风险决策。

### Q15：这个 Agent 项目最大的难点是什么？

最大难点是把大模型的不确定性放进一个高一致性的业务系统里。解决思路是分层：模型负责理解和表达，Runtime 负责状态和工具规划，MCP 负责安全工具，Java 后端负责最终权限和业务规则，写操作再通过 preview/confirm/execute 安全闭环约束。

## 18. 面试讲述模板

一分钟版本：

> Agent 部分是在仓储系统上做的智能业务助手。用户可以用自然语言问库存、库位、托盘和化验，系统通过 Java Agent Gateway、Python Runtime 和只读 MCP 工具查询真实后端数据。我们重点做了安全边界：不让模型直接连数据库或调用任意接口，产品和库位必须 resolver 消歧，工具结果经过 safe adapter 脱敏，普通用户看不到内部 ID、工具名和 raw JSON。当前只开放只读查询，写操作未来必须走预览、确认、执行 token、幂等和审计。

三分钟版本：

> 这个 Agent 项目的目标不是做一个 ChatGPT 套壳，而是让仓储用户能用自然语言查实时业务数据。整体架构是 Web 调 Java Agent Gateway，Java 负责登录用户、Agent session、权限和 SSE 代理；Python Runtime 负责意图规划、上下文管理、候选消歧和模型适配；Python 只能调用 Java Internal Tool Gateway，再由 MCP Server 调后端只读 API。  
> 当前工具包括产品解析、库位解析、库存概览、库存分布、库位状态、托盘状态、化验状态、化验记录和化验异常查询。工具按风险等级管理，第一阶段只开放只读 L1 能力，不开放入库、出库、调拨等写操作。  
> 技术难点主要有三个：第一，产品和库位有歧义，所以必须 resolver + HITL，不能让模型猜 ID；第二，用户会连续追问“它”“这些”，所以需要 structured state，而不是只拼历史文本；第三，工具返回有内部字段，所以必须用 safe adapter 过滤成业务回答。  
> 整体亮点是把大模型能力放进可控工程边界里：模型负责理解语言，后端负责权限和事实，MCP 负责安全工具，未来写操作也必须通过 preview、用户确认、executionToken、幂等键和审计来完成。
