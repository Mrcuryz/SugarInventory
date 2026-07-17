# 智能仓储 AI 助手最终产品目标

适用项目：LaibinSugarInventory / 智能仓储
适用范围：Web AI 助手、Agent Gateway、MCP 工具编排、自然语言仓储操作体验
当前原则：本文描述最终产品体验和演进边界，不代表当前阶段已经全部实现。

## 1. 产品定位

智能仓储 AI 助手的最终目标不是展示 MCP 工具调用过程，而是提供接近用户日常使用网页 AI 对话的业务助手体验。

用户应当感觉自己在和“智能仓储助手”对话，而不是在和接口、工具、ID 或数据库字段对话。

最终体验应满足：

* 用户只需要说人话；
* 助手能理解上下文；
* 助手能主动追问；
* 助手能查询、分析、解释业务数据；
* 助手能用自然语言、候选按钮、业务卡片、追问建议和加载状态展示结果；
* 普通用户看不到 `toolName`、`productId`、`warehouseId`、raw JSON、`SUCCESS` 等内部字段；
* 管理员调试模式才显示工具调用、耗时、错误码和安全摘要；
* 查询和分析可以相对开放，但必须只读、受权限、可审计；
* 入库、出库、调拨等写操作必须遵循 `preview -> 用户确认 -> executionToken -> idempotencyKey -> execute`；
* 后端仍是最终权限判断者。

一句话：AI 助手应表现为业务助手，不是接口搜索器。

## 2. 分层边界

### 2.1 用户体验层

普通用户看到的是：

* 自然语言回答；
* 候选按钮；
* 结果卡片；
* 追问建议；
* 加载中的业务状态，例如“正在确认产品范围”“正在查询库存”“正在汇总库位分布”；
* 错误时的人话解释。

普通用户界面不得展示：

* `productId`；
* `warehouseId`；
* `toolName`；
* `SUCCESS`；
* raw JSON；
* error stack；
* Authorization Header；
* token、delegationToken、refresh token；
* 密码；
* 数据库连接信息；
* 内部服务器路径。

### 2.2 系统内部层

系统内部可以使用：

* MCP Tool；
* tool schema；
* agentSession；
* conversationContext；
* structured memory；
* tool result memory；
* delegationToken；
* audit log；
* errorCode；
* upstreamStatus。

这些内容用于安全执行、权限控制、调试、审计和问题排查，不直接暴露给普通用户。

### 2.3 调试模式

管理员调试模式可以查看：

* 工具名；
* 调用耗时；
* 结果状态；
* 错误码；
* 脱敏后的请求摘要；
* 脱敏后的响应摘要。

调试模式仍不得展示 token、Authorization Header、密码、完整异常堆栈或数据库连接串。

## 3. 产品原则

1. 用户和“助手”对话，不和接口、工具、ID 对话。
2. 普通用户界面默认隐藏 MCP 工具调用细节。
3. 助手必须支持连续上下文，例如“这些”“它”“刚才那个产品”“这个库位”。
4. 不确定时主动追问，不得猜产品 ID、库位 ID、托盘码或化验记录。
5. 查询/分析类能力可以使用受控只读数据访问层。
6. 不允许开放任意 SQL、任意 HTTP 代理、任意数据库访问。
7. 写操作必须强约束，不能让模型直接改业务数据。
8. 错误必须用业务语言解释，不得把权限错误、接口错误伪装成无数据。
9. token、Authorization、密码、内部异常、堆栈、数据库连接信息永不进入普通用户界面。
10. Agent Gateway 负责会话记忆、工具编排、权限上下文、流式事件和审计。
11. MCP Server 负责安全工具能力，不负责直接和用户对话。
12. 后端仍是最终权限判断者。

## 4. 体验目标

### 4.1 产品歧义消解

用户：

```text
帮我查黄冰糖当前库存
```

助手不应回答：

```text
resolve_products SUCCESS
```

助手应回答：

```text
“黄冰糖”有多个规格，你想查哪一个？

1. 黄冰糖（袋）
2. 黄冰糖（箱）净
3. 黄冰糖14.5（箱装）
4. 全部黄冰糖大类
```

这些选项应展示为业务候选卡片或按钮。普通用户不应看到内部 ID。

### 4.2 选择后继续查询

用户选择：

```text
黄冰糖（袋）
```

助手：

```text
黄冰糖（袋）当前库存为 11板30件，折合 470 件，总重量 11750kg。
```

### 4.3 上下文追问

用户：

```text
这些主要存放在哪些库位？
```

助手应沿用上一次唯一产品上下文，不要求用户重新输入产品名：

```text
黄冰糖（袋）主要存放在以下库位：

1. 2号库位：……
2. 3号库位：……

需要我继续展开具体托盘明细吗？
```

### 4.4 化验追问

用户：

```text
它今天有化验吗？
```

助手应沿用刚才的产品上下文查询化验。除非上下文已失效或仍存在歧义，不得重新要求用户选择产品。

### 4.5 库位自然语言

用户：

```text
2号库位现在还有多少容量？
```

助手应把“2号库位”归一化为业务库位名称后查询容量，并用业务语言回答，不暴露 `warehouseId`。

## 5. 业务对话记忆

Agent 必须具备持续上下文，而不是只把最后一句用户输入扔给模型。

至少应记住：

* 最近一次选择的产品；
* 最近一次查询的库存；
* 最近一次查询的库位；
* 最近一次查询的托盘；
* 最近一次查询的化验；
* 当前待用户选择的问题；
* 最近几轮聊天记录；
* 关键工具结果的安全摘要。

技术组成建议：

* message history；
* structured memory；
* tool result memory；
* pending clarification state；
* selected business entity state。

必须能接住类似表达：

* 这些在哪些库？
* 它今天化验了吗？
* 刚才那个产品最近30天怎么样？
* 这个库位还有多少容量？
* 给我展开详情。
* 导出一下。
* 换成白冰糖看看。

如果当前能力无法完成，例如导出或最近 30 天分析尚无安全工具，助手应说明能力缺口并给出可做的部分，不得假装完成。

## 6. 不确定性管理

真实助手不应乱猜，应主动追问。

示例：

* 用户说“查黄冰糖库存”，若存在多个产品，助手应询问“你想查全部黄冰糖大类，还是某个具体规格？”
* 用户说“查最近一天化验”，若“最近一天”不清楚，助手可以问“你是指今天，还是最近有化验记录的一天？”
* 用户说“把它入库”，若未来进入写操作能力，助手必须先列出缺少的产品、数量、库位、生产日期等信息，并进入 preview 流程，不得直接写入。

歧义路径要求：

* `UNIQUE`：可以继续查询；
* `AMBIGUOUS`：展示候选并询问用户，不得猜 ID；
* `NOT_FOUND`：说明未找到并给出建议，不得编造结果；
* 权限错误、接口错误、超时：用业务语言说明失败，不得解释为空数据。

## 7. 查询与分析能力边界

查询/分析类能力可以比当前 6 个工具更开放，但必须通过受控只读数据访问层实现。

允许方向：

* 读取当前用户有权限的数据；
* 读取白名单业务实体和字段；
* 返回结构化业务数据，而不是数据库裸字段；
* 对大数据做分页、聚合或采样；
* 生成业务摘要、趋势、异常解释和追问建议；
* 每次数据访问都审计。

禁止方向：

* 任意 SQL；
* 任意 HTTP 代理；
* 任意数据库访问；
* 不受限制的全量数据导出；
* 绕过后端权限；
* 把原始敏感字段直接塞给模型或普通用户界面。

## 8. 写操作安全边界

入库、出库、调拨、托盘确认、二维码作废/恢复、化验导入/更新/删除、质量标准修改等写操作必须强约束。

最终写操作链路：

```text
用户自然语言
  -> resolver / 信息补全
  -> preview
  -> 用户确认
  -> 后端签发 executionToken
  -> idempotencyKey
  -> execute
  -> 审计
  -> 业务结果回答
```

禁止：

* 模型直接改业务数据；
* 跳过 preview；
* 跳过用户确认；
* 跳过 executionToken；
* 跳过幂等键；
* 跳过权限校验；
* 让 Agent 临时拼接任意写接口请求。

## 9. 流式输出目标

流式输出应提升真实感，但展示的是业务进度，不是模型原始思考链，也不是单纯把最终回答拆成文本分片。

允许展示：

```text
正在确认产品范围……
找到多个黄冰糖规格，需要你选择。
```

或：

```text
正在查询库存……
正在汇总库位分布……
黄冰糖（袋）当前主要存放在……
```

禁止展示：

* chain-of-thought；
* 原始 MCP 协议消息；
* raw JSON；
* token；
* Authorization Header；
* 内部堆栈。

流式事件应至少区分三类：

* 用户可见业务事件：`message_start`、`progress`、`clarification`、`text_delta`、`card`、`error`、`message_end`；
* 管理员调试事件：`tool_start`、`tool_end`、`debug`；
* 系统控制事件：`heartbeat`、`cancelled`、`timeout`、`fallback`。

每个事件协议内部应具备 `messageId`、`eventId`、`sequence`、`type` 和 `payload`，用于前端去重、排序和审计定位。普通用户界面不直接展示这些协议字段。

当助手需要用户选择，例如“黄冰糖”存在多个规格时，流式输出应以 `message_end.finishReason=clarification_required` 结束，表示当前消息暂停等待用户输入，而不是普通完成。

流式中途失败时不得直接断开连接。已经发送 `message_start` 或 `progress` 后，如果工具调用超时或上游失败，应尽量继续发送业务 `error` 事件，再发送 `message_end`，并标记 `finishReason=timeout` 或 `error`。

前端渲染应以一个用户问题对应一个 assistant message 容器。`progress` 应作为容器内当前进度短句或可折叠处理过程，不应堆成多条独立聊天气泡。

## 10. 组件职责

### 10.1 Agent Gateway

Agent Gateway 负责：

* 绑定当前登录用户；
* 会话记忆；
* 工具编排；
* 权限上下文；
* 流式业务事件；
* 消歧状态；
* 错误解释；
* Agent Tool 审计；
* 面向前端返回自然语言、业务卡片和追问建议。

Agent Gateway 内部采用模块化 Agent 编排：主 Agent 负责意图、上下文、HITL、安全边界和最终回答，通过 Router 把受支持任务交给库存、库位、物流、二维码/托盘、生产、质量、主数据、员工/RBAC 和审计最小权限专家。主 Agent 不直接持有业务工具；专家只看到本模块 context pack 和工具 schema。当前 9 个业务专家均已有 L1 只读工具，但 v2/规划占位仍只能说明能力缺口，不得把设计条目当作已实现能力。

模块化 Agent 是 Python 侧的附加最小权限层，不能替代 Java Gateway 的用户身份、scope 和工具白名单校验。未来可以为不同专家配置不同模型与参数，但模型替换不得改变工具权限、参数校验、safe adapter 或写操作安全链路。详细设计见 `docs/agent/modular-agent-architecture.md`。

### 10.2 MCP Server

MCP Server 负责：

* 暴露安全、受控、可审计的工具能力；
* 调用后端只读或未来受控业务接口；
* 统一错误结构；
* 脱敏日志；
* 不直接和用户对话；
* 不保存用户密码；
* 不开放任意 SQL、任意 HTTP 代理或直接数据库访问。

### 10.3 后端业务系统

后端业务系统负责：

* 最终权限判断；
* token 与用户身份校验；
* agent_session 状态校验；
* 数据权限；
* 业务规则；
* 事务；
* 审计；
* 写操作 preview/execute 安全闭环。

## 11. 路线图

### M1.3.1：上下文指代修复

目标：

* 让助手支持“这些”“它”“刚才那个”“这个库位”“这条化验”等指代。
* 让候选卡片选择成为同一会话上下文，而不是新的用户消息。

允许做什么：

* 增强 Agent Gateway 会话记忆；
* 记录最近产品、库存、库位、托盘、化验和待选择项；
* 增加只读查询场景的上下文测试；
* 普通 UI 隐藏内部字段；
* 管理员调试模式查看安全摘要。

禁止做什么：

* 新增写工具；
* 新增任意 SQL 或任意 HTTP 代理；
* 暴露 token、Authorization Header 或内部 ID 给普通用户；
* 让模型猜产品 ID、库位 ID。

验收标准：

* “帮我查黄冰糖当前库存”展示业务候选卡片；
* 选择“黄冰糖（袋）”后回答库存；
* 追问“这些主要存放在哪些库位？”沿用产品上下文；
* 追问“它今天有没有化验？”沿用产品上下文；
* “2号库位现在还有多少容量？”能归一化并回答；
* 普通 UI 不显示 toolName、SUCCESS、productId、warehouseId；
* 会话撤销后上下文被清理。

### M1.3R-5：流式交互首版（已完成）

目标：

* 前端支持业务进度事件和最终回答流式展示；
* 用户在等待期间看到“正在确认产品”“正在查询库存”“正在分析结果”等业务状态。
* 流式协议支持 `messageId`、`eventId`、`sequence`、`finishReason`、`heartbeat`、`cancelled`、`timeout` 和 `fallback`。

允许做什么：

* 增加 Agent Gateway 流式事件协议；
* 增加前端流式渲染；
* 区分普通事件和管理员调试事件；
* 一个用户问题渲染为一个 assistant message 容器；
* 预留取消生成接口；
* 保持当前只读工具范围。

禁止做什么：

* 展示模型原始 chain-of-thought；
* 展示 MCP 原始协议帧；
* 为流式输出暴露 token 或 raw JSON；
* 新增写能力。

验收标准：

* 用户能看到业务进度；
* 最终回答可逐段或逐字输出；
* clarification 以 `finishReason=clarification_required` 暂停等待用户；
* 流中错误以 `error + message_end` 结束；
* 前端按事件 ID 去重并按 sequence 渲染；
* 普通模式不显示工具细节；
* 管理员调试模式可查看安全摘要；
* 权限错误和上游错误仍用业务语言解释。

### M1.3R-5.1：流式链路加固

目标：补齐流式审计、Java 端到端 SSE 测试、后端取消接口、客户端断开处理、真实浏览器验收和结果分类。

验收标准：

* 用户主动取消与客户端意外断开分别记录；
* 取消后不再向原 assistant message 写入工具结果或最终回答；
* 审计至少区分 `COMPLETED`、`CLIENT_DISCONNECTED`、`CLIENT_CANCELLED`、`PYTHON_TIMEOUT`、`PYTHON_ERROR`、`TOOL_TIMEOUT`、`TOOL_ERROR`、`SECURITY_FILTERED`、`FALLBACK_BLOCKED`；
* 长回答、候选等待、取消恢复、移动端卡片、连续消息和刷新状态通过真实浏览器验收。

### M1.3R-5.2：模型原生增量输出

目标：输出 LLM token delta，同时保留业务 `progress`、`clarification` 和 `card` 事件。

禁止展示 chain-of-thought、内部推理、raw MCP frame 或敏感工具数据。

### M1.3R-6：Human-in-the-loop 统一模型

目标：统一 clarification resume、preview 确认、execute 前确认，以及用户拒绝、修改和重新预览。

当前阶段只建设统一状态与协议模型，不开放入库、出库、调拨等执行能力。

阶段拆分：

* M1.3R-6a：协议与状态机，定义 `HitlInterrupt`、`ResumeAction`、`resumeToken`、状态机、`finishReason` 和审计码；
* M1.3R-6b：clarification 迁移，候选选择改成统一 interrupt/resume，前端只提交 opaque `optionId`；
* M1.3R-6c：Java resume API 与审计，Java 至少记录 interrupt 最小元数据并处理重复提交；
* M1.3R-6d：前端统一卡片，preview / execute confirmation 只占位，不触发写操作；
* M1.3R-6e：验收与文档，覆盖正常选择、重复点击、过期点击、刷新状态、不暴露内部字段和不触发写操作。

`resumeToken` 是前端可见的一次性能力凭证，必须短期有效、单次使用、绑定 `agentSessionId`、`interruptId`、`userId`、action 和 `optionId` / `previewId`，不可预测，不写普通日志，过期不可恢复。

候选项前端只展示 `optionId`、`displayLabel`、`description` 和 `supported`，不得暴露 `productId`、`warehouseId`、`inventoryId`。真实业务实体映射保存在后端 pending interrupt state。

产生人类介入时，当前消息必须以 `message_end.finishReason=interrupt_required` 暂停，而不是普通 `completed`。

### M1.4：受控只读数据分析层

目标：

* 让助手能查询和分析更多只读业务数据，例如库存分布、化验时间范围、化验报告详情、趋势分析。

允许做什么：

* 新增受控只读聚合接口；
* 新增白名单实体和字段；
* 支持分页、聚合、采样和摘要；
* 记录每次数据访问审计；
* 返回结构化业务数据和自然语言解释。

禁止做什么：

* 任意 SQL；
* 任意 HTTP 代理；
* 直接数据库访问；
* 无上限全量返回；
* 绕过当前用户权限；
* 把数据库裸字段直接暴露给普通用户。

验收标准：

* 可回答“最近30天黄冰糖化验趋势怎么样”等只读分析问题；
* 大数据查询有分页、聚合或采样；
* 每次数据访问可审计；
* 工具失败不被解释为空数据。

### M1.5：报表与导出

目标：

* 支持安全导出库存、化验或分析报表。

允许做什么：

* 基于已审计的查询或分析结果生成报表；
* 支持文件过期、下载权限和导出审计；
* 支持 Excel/PDF 等格式；
* 普通用户只看到业务文件名、摘要和下载入口。

禁止做什么：

* 无权限导出；
* 导出超出当前用户权限范围的数据；
* 导出 token、内部异常或数据库连接信息；
* 直接把任意 SQL 查询结果导出。

验收标准：

* 报表来源可追溯；
* 导出人、范围、时间、文件类型可审计；
* 下载链接有权限和过期控制；
* 失败时返回业务错误解释。

### M2：预览类业务操作

目标：

* 支持入库、出库、调拨等写操作前的 dry-run 预览，不修改业务数据。

允许做什么：

* 新增 preview 工具或后端聚合预览接口；
* 校验产品、库位、容量、化验、库存不足、冲突；
* 返回 blockingIssues、warnings、candidateResolutions 和 canExecute；
* 引导用户补齐缺失信息。

禁止做什么：

* 写库存；
* 创建真实业务单；
* 改变托盘状态；
* 分配真实二维码；
* 跳过用户确认。

验收标准：

* 预览不会修改任何业务表；
* 风险、缺失项和阻塞原因清晰；
* 用户确认前不能执行；
* 预览结果可审计。

### M3：执行安全层

目标：

* 为未来执行类工具建立安全基础。

允许做什么：

* executionToken；
* idempotencyKey；
* preview 与 execute 内容绑定；
* 强权限；
* 强确认；
* 操作审计；
* 执行前重新校验。

禁止做什么：

* 未经 preview 签发执行 token；
* 未经用户确认执行；
* 无幂等键执行；
* 让模型直接拼接写接口；
* 执行内容和预览内容不一致。

验收标准：

* 撤销、过期、重复请求、内容篡改都被拒绝；
* 审计可关联用户、会话、预览、执行和结果；
* 执行前重新校验业务状态。

### M4：执行类工具

目标：

* 在 M3 安全层基础上开放有限的入库、出库、调拨执行能力。

允许做什么：

* `execute_inbound_plan`；
* `execute_outbound_plan`；
* `execute_transfer_plan`；
* 返回业务单号、任务号和执行摘要。

禁止做什么：

* 开放通用写接口；
* 开放任意 SQL；
* 开放任意 HTTP 代理；
* 跳过 preview、确认、executionToken 或 idempotencyKey；
* 修改权限、质量标准等高风险配置。

验收标准：

* 执行只能基于有效预览；
* 幂等生效；
* 并发冲突可处理；
* 审计完整；
* 普通用户看到业务结果，不看到内部调用细节。

### M5：生产化与多用户能力

目标：

* 支持生产多用户并发、稳定运行、观测、权限隔离和持续运营。

允许做什么：

* HTTP/Streamable HTTP MCP；
* 请求级用户委托身份注入；
* 多用户会话隔离；
* 流式事件标准化；
* 可观测指标；
* unmet intent 统计；
* 运维告警；
* 更细粒度 scope。

禁止做什么：

* 多用户共用全局 token；
* STDIO 全局进程承载生产多用户；
* 把开发 STATIC_TOKEN 当生产授权；
* 记录敏感信息到日志或审计。

验收标准：

* 多用户并发隔离；
* 用户撤销会话后后续调用失败；
* token 不进入前端、LLM、MCP Tool 输出或普通日志；
* 审计和监控可定位问题；
* 生产部署文档完整。

## 12. 当前阶段提醒

当前已实现的 47 个只读 MCP 工具是基础能力，不是最终用户体验本身。后续迭代优先级应从“增加工具数量”转向“形成真实助手感”和上线安全闭环：

1. 上下文稳定；
2. 消歧自然；
3. 普通 UI 隐藏内部细节；
4. 错误解释可信；
5. 业务结果可读；
6. 查询分析受控开放；
7. 写操作强约束。

## 13. Agent v1、v2 与后续规划能力的调整路线

本节为设计路线，不修改当前 47 个只读工具、Python/Java/MCP 白名单或当前唯一受控复合配方。

1. 先完成 Agent v1 日常查询覆盖，使 inventory、warehouse、logistics、pallet、production、quality、master_data、administration、audit 专家均有真实只读能力和固定数据测试。
2. 再建设 inventory、stock movement、production、material、output、assay、pallet lifecycle、warehouse capacity 等事实/快照数据，以及版本化指标 Registry、数据质量和 Rule Engine。
3. 在数据语义稳定后建设 v2-A：登记报表、受控跨域分析、化验文件暂存预览，以及入库/出库/调拨 dry-run。
4. 完成 executionToken、幂等、实体/规则版本重检、审批、事务、HITL 和完整审计后，才允许逐项进入 v2-B execute。
5. 工作群先开放群安全查询、登记报表、提醒和 Web 跳转确认，不在群聊内执行 L3/L4。
6. 历史数据、指标、规则和模型评测稳定后，再建设只生成 advisory scenarios 的 planning_expert、预测、优化和仿真。
7. 当前只预留 organizationScope、plantScope、warehouseAreaScope；多组织真正启用前必须完成 scope 隔离和跨 scope 测试。

长期设计继续禁止任意 SQL、任意 HTTP、万能管理工具、模型自由 DAG、专家自由互聊、直接修改库存，以及任何绕过 preview/HITL 的写入。详细设计和机器可读占位见：

- `docs/mcp-analysis/agent-expert-tool-blueprint.md`
- `docs/agent/expert-tool-roadmap-registry.yaml`
- `docs/agent/cross-expert-recipe-roadmap-registry.yaml`
- `docs/agent/data-semantic-roadmap-registry.yaml`
- `docs/agent/business-rule-roadmap-registry.yaml`
