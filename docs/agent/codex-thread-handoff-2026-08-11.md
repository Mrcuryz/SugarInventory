# 冰糖仓储管理 Agent 项目交接文档

更新时间：2026-08-11（Asia/Shanghai）  
适用对象：接手本仓库的 Codex 任务、开发者、测试与架构评审人员  
当前分支：`dev`  
文档性质：工作区事实快照，不代表生产发布批准

## 0. 接手者先读

新任务开始前，按顺序阅读：

1. 仓库根目录 `AGENTS.md`；
2. 本文；
3. [AI 助手产品目标](ai-assistant-product-goal.md)；
4. [MCP 工具登记表](../mcp/mcp-tool-registry.md)；
5. 与本次任务对应的 Goal、工具或报表登记文件。

任何 Agent、MCP、自然语言交互或前端助手改动，都不能跳过第 3、4 项。

### 0.1 事实来源优先级

发生冲突时，按以下顺序判断：

1. 当前 Controller、Service、Mapper、事务与权限代码；
2. 当前数据库迁移、表字段、真实可达写入路径；
3. Python Runtime、Java Gateway、MCP 的运行时登记与合同测试；
4. `docs/agent/*.yaml`、`docs/mcp/mcp-tool-registry.md`；
5. OpenAPI、历史设计文档和历史验收记录。

历史文档只能证明当时的状态。不要用历史结论覆盖当前代码，也不要把设计占位当作已实现能力。

### 0.2 当前工作区非常脏，禁止清理式接手

截至本文生成时：

- `HEAD`：`cfb0bb5`（`fix(tasks): close finish inbound s0 uat`）；
- 工作区约有 194 条 modified/untracked 状态；
- 其中包含尚未提交的 Agent、MCP、Web、数据库迁移、成品入库 S1～S3 与最新连续中断修复；
- `.playwright-cli/`、`.playwright-mcp/`、`output/` 多为验收产物，不能未经筛选直接提交；
- 不得执行 `git reset --hard`、`git checkout -- .`、批量删除或覆盖当前用户改动。

新任务第一条命令应是：

```powershell
git status --short
```

若 Git 因 Windows 运行账户不同报告 `dubious ownership`，只在单条命令中使用：

```powershell
git -c safe.directory=D:/Laibin/LaibinSugarInventory status --short
```

不要为了方便擅自修改用户的全局 Git 配置。

## 1. 项目背景与产品目标

这是一个冰糖仓储管理系统上的智能业务助手。现有业务系统覆盖库存、库位、托盘二维码、出入库任务、生产订单、煮糖批次、化验质量、主数据、权限与审计。

早期 Agent 主要由关键词 Router 提取意图、调用固定接口，LLM 只在最后润色，因此更像“自然语言接口调用器”。当前方向已调整为：

```text
主模型理解用户目标
  -> 委派最小权限专家
  -> 专家模型决定追问或调用哪个已登记工具
  -> Runtime / Java Gateway 做确定性安全校验
  -> MCP 调用现有业务能力
  -> 形成受控事实并校验目标是否完成
  -> 模型分析事实，返回自然语言与业务卡片
```

这比“主 Agent 识别意图 -> 专家调用工具 -> 模型润色”多了两个不可省略的部分：

- 调用前的合同、实体、权限和参数校验；
- 调用后的事实封装与完成度裁决。

模型负责理解、决策和分析，但没有权力绕过业务规则、权限或证据宣布任务完成。

### 1.1 三阶段产品目标

| 阶段 | 目标 | 当前判断 |
| --- | --- | --- |
| 1. 只读查询 | 用户用自然语言完成实时业务查询与多轮追问 | 本地/UAT 已封板；未等同于正式生产发布 |
| 2. 报表分析 | 确定性指标、趋势、对比、快照、历史重开与导出 | 七类报表工程和本地验收已完成；部分生产数据门禁仍待真实环境积累 |
| 3. 写入操作 | preview、确认、幂等、事务、审计后执行真实业务变更 | 总体 `NO-GO`；只有首个成品入库候选完成 S0～S3 工程切片，默认关闭且没有 MCP execute 工具 |

长期还需要支持主动触发、定时任务、外部工作群和复杂长流程，但这些不应在 L3 安全链路封板前提前开放。

## 2. 仓库与运行组件

### 2.1 主要目录

| 目录 | 职责 |
| --- | --- |
| `src/` | Java/Spring Boot 主业务系统、Agent Gateway、Agent 只读聚合接口、报表与受控写候选 |
| `agent-service/` | Python/FastAPI Agent Runtime、主/专家模型循环、状态、合同、RAG、SSE |
| `warehouse-mcp/` | Java STDIO MCP Server，向 Agent 暴露闭合业务工具 |
| `webpage/` | Vue 3 Web，AI 助手、卡片、HITL 选择、报表、受控业务弹窗 |
| `LaibinSugarInventoryWxAPP/` | 小程序客户端；当前 Agent 主要验收在 Web |
| `migrations/` | 增量数据库迁移，包括 Agent、报表、快照和成品入库控制面 |
| `docs/agent/` | 产品目标、架构、Goal/工具/报表登记、验收与发布门禁 |
| `docs/mcp/` | MCP 权威登记、风险等级和工具边界 |
| `scripts/` | 隔离服务启动、报表/快照、S0～S4 验收脚本 |

### 2.2 当前调用链

```text
Vue Web
  -> Java /api/agent Gateway（JWT 登录用户、会话、权限、SSE、审计）
  -> Python /internal/agent Runtime（主模型、专家、Goal、上下文、HITL）
  -> Java /internal/agent/tools/{toolName}（二次白名单与用户权限）
  -> warehouse-mcp（STDIO MCP）
  -> Java 现有业务 Controller / Service / DB
```

RAG 是例外：`knowledge_expert` 只调用 Python 进程内的 `search_approved_knowledge`，不进入 Java/MCP 的 54 工具白名单。

### 2.3 关键代码入口

| 文件 | 接手时重点关注 |
| --- | --- |
| `src/main/java/.../agent/controller/AgentSessionController.java` | 公共 Agent 会话、消息、SSE、取消、恢复和反馈接口 |
| `src/main/java/.../agent/gateway/RuntimeRoutingAgentGatewayService.java` | Java/Python 流代理、interrupt 生命周期、事件一致性与审计 |
| `src/main/java/.../agent/python/HttpPythonAgentClient.java` | Java 到 Python Runtime 的协议和能力握手 |
| `src/main/java/.../agent/internal/controller/InternalAgentToolController.java` | 内部工具调用入口 |
| `src/main/java/.../agent/internal/service/impl/McpInternalAgentToolGatewayService.java` | 54 工具白名单、专家白名单、上游路径、权限和 MCP 调用 |
| `src/main/java/.../agent/mcp/StdioMcpSessionManager.java` | MCP 进程生命周期、工具清单握手和调用 |
| `agent-service/app/runtime.py` | 主/专家循环、Goal、HITL、事实、完成裁决和卡片适配 |
| `agent-service/app/model.py` | 主模型/专家模型结构化契约与提示词 |
| `agent-service/app/agents.py` | 主 Agent 和专家 Profile、工具所有权 |
| `agent-service/app/goal_contracts.py` | 58 个 GoalContract 的运行时定义 |
| `agent-service/app/streaming.py` | SSE 事件、超时、取消、增量输出与终止原因 |
| `warehouse-mcp/src/main/java/.../mcp/tool/WarehouseTools.java` | MCP Tool 暴露层 |
| `warehouse-mcp/src/main/java/.../mcp/service/WarehouseReadService.java` | MCP 到 Java API 的安全只读适配 |
| `webpage/src/components/AgentAssistant.vue` | 会话入口、流式消息、候选恢复和业务弹窗编排 |
| `webpage/src/components/agent/AgentBusinessCard.vue` | 业务卡片、报表、任务/二维码选择与状态呈现 |

## 3. Agent 核心设计

### 3.1 统一执行范式

在 `AGENT_PLANNING_MODE=llm` 下，所有专家必须遵守同一模式：

1. 主模型基于当前消息和受控会话上下文理解目标；
2. Runtime 将目标绑定到一个已登记 `GoalContract`；
3. 主 Agent 只委派一个最小权限专家，主 Agent 自身没有业务工具；
4. 专家模型在自己的说明、Context Pack 与工具 schema 中决定追问、调用或结束；
5. Runtime 校验专家身份、`allowedTools`、参数 schema、实体引用、次数、日期和状态；
6. Java Gateway 再校验登录用户、会话、delegation scope、专家工具白名单与业务权限；
7. 工具结果转换为 `FactEnvelope` 和安全业务卡片；
8. `CompletionEvaluator` 检查所需实体和事实是否齐全；
9. 模型只根据安全事实做结果分析，不得补造数据库中不存在的事实。

关键词、确定性 Router 和状态机只能用于：

- schema、权限、日期、候选和安全校验；
- 已明确的候选选择恢复与卡片组装；
- deterministic 测试模式或受控降级。

在 LLM 模式下，不能用关键词 Router 抢先回答普通业务问题，也不能替专家选择业务工具。

### 3.2 四个关键合同对象

| 对象 | 作用 | 主要代码 |
| --- | --- | --- |
| `GoalContract` | 定义用户目标、负责专家、所需实体、允许工具和必需事实 | `agent-service/app/goal_contracts.py` |
| `EntityContext` | 保存产品、库位、托盘、生产订单、煮糖批次等受控上下文 | `agent-service/app/state_models.py`、`context.py` |
| `FactEnvelope` | 把工具结果封装成带来源、状态、范围和限制的事实 | `agent-service/app/goal_contracts.py` |
| `CompletionEvaluator` | 防止只解析实体或只调用支撑工具就宣布目标完成 | `agent-service/app/goal_contracts.py`、`runtime.py` |

`allowedTools` 是目标内可使用的最小集合；`evidenceTools` 才能形成目标完成证据。resolver、目录发现和详情下钻通常只是支撑能力。

### 3.3 当前 Agent/专家

当前共有主 Agent、10 个 Java-backed 业务专家和 1 个 Python 进程内知识专家：

| Agent | 主要职责 | 核心边界 |
| --- | --- | --- |
| `main_agent` | 目标理解、上下文、HITL、安全边界、最终反馈 | 无业务工具 |
| `inventory_expert` | 产品库存总览、分布、台账 | 不执行库存变更 |
| `warehouse_expert` | 库位解析、容量、状态、近期操作、混放事实 | 不把自然语言数字当内部 ID |
| `logistics_expert` | 待处理任务、单据、自动入库批次、L2 预览、入库引导 | 查询/预览不等于执行 |
| `pallet_expert` | 二维码、托盘状态、生命周期、流转、异常 | 不确认/取消任务，不出入库 |
| `production_expert` | 生产订单、煮糖批次、领料、产出、标签、在制品 | 不创建/修改订单，不领料 |
| `assay_expert` | 化验、标准、当前库存质量筛选 | 无标准不等于不合格 |
| `master_data_expert` | 产品、筛网主数据 | 不创建/修改基础资料 |
| `administration_expert` | 员工、角色、权限摘要 | 不修改账号或权限 |
| `audit_expert` | 操作日志、工具审计、回答复核 | 不暴露原问题、Prompt、内部 ID、凭据 |
| `analytics_expert` | 运行版本化登记报表并分析安全事实 | 不自由拼接指标，不让模型计算权威指标 |
| `knowledge_expert` | 已发布现行资料的静态知识检索 | Python L0；不能回答实时业务事实 |

权威实现见 `agent-service/app/agents.py` 和 `docs/agent/tool-capability-registry.yaml`。

### 3.4 用户可见处理过程

界面展示的是受控业务阶段，例如：

```text
理解用户需求
判断业务领域并选择专家
专家确认查询条件并决定工具
调用业务数据工具
分析工具返回结果
确认查询范围
根据用户选择继续处理
```

它不是模型原始思维链。禁止展示 Prompt、chain-of-thought、raw JSON、内部 ID、token、鉴权头和堆栈。

SSE 主要事件包括 `message_start`、`progress`、`clarification`、`text_delta`、`card`、`error`、`message_end`；管理员调试事件与普通用户事件分层。

## 4. 当前能力盘点

### 4.1 数量基线

截至 2026-08-11 当前代码/登记为：

- 58 个已接入 Runtime 的 GoalContract；
- `planned_goals` 为 0；
- 54 个 Java/MCP 无业务写入工具：52 个 L1 只读/登记报表工具、2 个 L2 预览工具；
- 1 个 Python 进程内 L0 知识检索能力；
- 7 个版本化登记报表定义；
- 没有已登记的 MCP `execute_*` 工具。

精确清单以以下文件为准：

- [GoalContract 登记](readonly-goal-contract-registry.yaml)
- [工具能力登记](tool-capability-registry.yaml)
- [MCP 工具登记](../mcp/mcp-tool-registry.md)
- [报表定义登记](report-definition-registry.yaml)

已知文档债务：`readonly-goal-contract-registry.yaml` 的原则段仍残留“1 个 L2 预览工具”的旧表述；实际当前为 2 个，计数与 Runtime/MCP 登记为 54。后续应统一该文字，但不得因此改变运行时能力。

### 4.2 第一阶段：只读查询

结论：本地/UAT 范围已封板。

证据：

- 43 个第一阶段用户目标各执行 3 次主模型稳定性重放，129/129 正确委派；
- 25 项页面补充验收完成；
- ADMIN、QC、STAFF 角色关键旅程完成；
- 库存、库位、任务、托盘、生产、化验、主数据、员工/RBAC、审计均进入统一合同；
- 端到端常见耗时仍约 10～23 秒，工具本身通常不是主要耗时，模型结构化决策是主要长尾。

“封板”只表示本地/UAT 基线完成，不表示已经做完生产部署、监控、容量和正式发布演练。

### 4.3 第二阶段：报表分析

当前 7 个报表定义：

1. `today_operations_overview_v1@1`：今日运营概览；
2. `daily_production_overview_v1@1`：生产登记产出日报；
3. `inventory_level_trend_v1@1`：库存水平趋势；
4. `quality_assay_result_trend_v1@1`：化验判定趋势；
5. `quality_metric_trend_v1@1`：单项化验指标趋势；
6. `production_input_output_flow_v1@1`：生产确认领用—稳定登记产出趋势；
7. `pallet_task_cycle_time_v1@1`：已登记托盘任务处理耗时。

已实现：

- Java/SQL 确定性聚合，模型不计算权威指标；
- 除“今日运营概览”外的六类报表支持统一跨期比较；
- 不可变 `ReportRun` 快照；
- 当前用户历史列表、跨会话原快照重开；
- 读取/导出时复核所有者、当前权限、30 天有效期与 SHA-256；
- 同一快照 XLSX 导出，不重新运行报表；
- 到期清理和独立清理审计；
- 详细事实保留在卡片、快照和导出中，提交模型前做确定性压缩。

仍有限制：

- `inventory_level_trend_v1` 的生产门禁需要目标环境连续 7 天可信日终快照和守恒对账；
- 本地历史回放只用于工程模拟，必须标注“模拟”，不能推进生产门禁；
- 当前数据库没有计划顺延、返工、报废、损耗、SLA 等事实，不得编造这些指标；
- 不支持预测、因果归因、良率、收率、损耗率、计划达成率，除非未来先证明真实字段和写入链路；
- 当前正式导出是 XLSX；PDF/CSV、订阅、历史刷新尚未完成。

### 4.4 RAG/知识能力

当前正式本地 artifact 根目录存在 `current.json`，指向 `laibin-rag-2026-07-29-v1`。历史记录证明该版本已发布到本地 artifact、通过完整校验，并在隔离/本地 UAT 中达到 `rag=READY`。

但运行态是进程级状态：电脑重启或服务重启后，必须重新检查 `/internal/agent/health` 与 capabilities，不能仅凭 `current.json` 宣称当前进程在线或生产已发布。

边界：

- 只检索已审核现行资料；
- 普通业务工具白名单和 registry hash 不包含知识工具；
- 静态知识与实时业务没有登记组合配方时，要求拆分问题；
- 引用卡片只展示安全业务内容与来源，不展示路径、分数或内部审计字段；
- 当前结论仍是本地 UAT，不是生产发布。

### 4.5 第三阶段：写入操作

总体结论：`AGENT_L3_EXECUTION_NO_GO / PRODUCTION_NOT_RELEASED`。

首个候选严格限定为 `FINISH_IN + PENDING` 成品入库确认：

| 切片 | 状态 | 说明 |
| --- | --- | --- |
| S0 | 本地 MySQL UAT 通过 | 批量上限、去重、稳定锁顺序、确认/取消并发、整批回滚 |
| S1 | 本地 Agent 链路通过 | 精确 L2 `preview_finish_inbound_execution`，无业务写入 |
| S2 | 本地零写入控制面通过 | 确认/撤销、一次性 token、幂等、状态重检、专用审计 |
| S3 | 本地隔离真实写入通过，默认关闭 | 受控 Web UI 复用现有成品入库领域事务；重放不二次写入 |
| S4 | 未封板 | 正式角色、故障注入、完整浏览器发布验收、回滚与发布决策 |

关键事实：

- `execute_finish_inbound_task` 没有注册、没有实现为 MCP Tool、没有启用；
- S3 是默认关闭的 Web 控制面候选，不是模型可调用的写工具；
- 专用权限 `agent:finish-inbound:execute` 默认不分配任何正式角色；
- 正常启动不要传 `-EnableFinishInboundS3Execute`；
- 出库、调拨、半成品入库和主动触发不在首个 L3 候选范围。

权威门禁见 [成品入库 L3 Gate](finish-inbound-l3-gate.yaml)。该文件部分细项仍有 S3 前后的文字漂移，生产决策前应结合 S3 UAT 和当前代码做一次机器可读门禁归一化；在归一化前总体状态始终按 `NO_GO`。

## 5. 当前高价值业务旅程

优先围绕用户目标验收，不要按 REST 接口逐个造测试：

1. 产品库存总览 -> 库位分布 -> 化验详情 -> 异常/缺化验；
2. 库位库存 -> 筛产品 -> 产品化验 -> 异常；
3. 煮糖批次 -> 关联生产订单 -> 确认领料与稳定产出 -> 入库去向；
4. 托盘现状 -> 当前流转 -> 完整历史流转；
5. 产品某日化验 -> 指标与标准 -> 历史趋势；
6. 当前待处理任务 -> 按类型分组 -> 多选 -> L2 预览 -> 跳转原业务弹窗；
7. 今日产量/质量/库存/任务概览 -> 登记报表 -> 历史重开 -> 同快照 XLSX；
8. “3号库位入库2板黄冰糖” -> 具体规格 -> 库位 -> 二维码来源 -> 恰好选择 2 个实物对象 -> 受控任务/预览链路。

第 8 条必须区分：

- 已绑定且处于待入库状态的现有任务；
- 空闲固定产品二维码，用于显式创建新的待入库任务。

两条路径业务含义不同，数量不足时不得静默互补。固定码启用只创建待入库任务并生成标签，不写库存；实际入库仍需后续预览和明确确认。

## 6. 已确认业务口径

后续开发必须以这些口径为前提，除非当前数据库/代码已经发生可证实变化。

### 6.1 化验与库存质量

- `assay.sample_date` 在业务上就是生产日期；
- 当前以“产品 ID + 生产日期”作为批次键；
- 库存与化验查询按产品 + 日期找到最新版本化验，不依赖 `inventory.assay_id`；
- 产品可以先入库、后出化验结果；
- “不合格”指存在任一实测指标不满足任何适用标准；
- “无适用标准”“多标准候选”“无化验”和“明确不合格”必须分开；
- 无标准不能展示为不合格；
- “库存中满足某指标”同时支持完整化验标准匹配和原始指标数值条件。

### 6.2 生产事实

- `production_order_material.status=PICKED` 且有 `picked_at`，表示用户已确认领用，并在同一事务完成库存扣减；
- `CONSUMED/RETURNED` 只是历史表设计预留，没有当前可达业务入口；
- `semi_prepare_pool*` 与 `production_consumption_record` 属于已弃用旧流程，不能进入当前 Agent、MCP 或报表；
- “备料池”旧说法应转换为查询已确认领用、库存已扣减且订单未完成的在制半成品；
- 稳定产出以 `production_order_output` 的稳定状态与生产日期为准，不能用标签数、二维码数或入库数代替产量；
- 生产领用与稳定产出是两条时间语义不同的序列，不得直接相除得到产耗比或收率。

### 6.3 库位、托盘与任务

- Agent 不得猜产品 ID、库位 ID、任务 ID 或库存 ID；
- 用户说“20”可能是 20 号库位，必须经 resolver，不直接当内部 ID；
- 任务目标库位不等于实际入库去向；只有已有库存/入库事实才能证明实际去向；
- `PREPRINTED`、`USED_UP`、`RESERVED`、`NO_STANDARD`、`FAIL` 等内部枚举必须转换为用户语言；
- 任务卡片按类型分组、多选和跳转既有弹窗属于 L2 UI handoff，不表示 Agent 已执行写操作；
- 上线会清空旧数据，因此历史非托盘库存不是正式迁移阻塞项，但当前查询仍应保持安全兼容。

### 6.4 时间

- 相对日期使用受信 `Asia/Shanghai` 业务时间转换；
- “今天、昨天、本周、上周、最近 30 天”等必须在调用工具前确定成明确日期范围；
- 不需要额外开放一个由模型自由调用的“系统时间 MCP 工具”，业务时间由 Runtime/Java 受控上下文提供。

## 7. 重要接口索引

这里只列 Agent 开发常用入口。全部工具到上游接口映射以 `McpInternalAgentToolGatewayService.UPSTREAM_PATHS` 与 MCP 登记表为准。

### 7.1 登录与 Agent 会话

| Method | Path | 作用 |
| --- | --- | --- |
| POST | `/api/auth/web-login` | Web 登录 |
| POST | `/api/agent/sessions` | 创建 Agent 会话 |
| GET | `/api/agent/sessions/current` | 当前会话 |
| POST | `/api/agent/sessions/{sessionId}/messages` | 非流式消息 |
| POST | `/api/agent/sessions/{sessionId}/messages/stream` | SSE 消息 |
| POST | `/api/agent/sessions/{sessionId}/interrupts/{interruptId}/resume` | 非流式恢复候选选择 |
| POST | `/api/agent/sessions/{sessionId}/interrupts/{interruptId}/resume/stream` | SSE 恢复候选选择 |
| POST | `/api/agent/sessions/{sessionId}/messages/{messageId}/cancel` | 取消运行 |
| POST | `/api/agent/sessions/{sessionId}/message-reviews` | 建立回答复核 |
| POST | `/api/agent/sessions/{sessionId}/message-reviews/{messageId}/feedback` | 用户反馈 |
| DELETE | `/api/agent/sessions/{sessionId}` | 结束/撤销会话 |

### 7.2 Python Runtime 内部接口

均要求 Java/Python 内部服务认证：

- `GET /internal/agent/health`
- `GET /internal/agent/capabilities`
- `GET /internal/agent/metrics`
- `POST /internal/agent/chat`
- `POST /internal/agent/chat/stream`
- `POST /internal/agent/resume`
- `POST /internal/agent/cancel`
- `DELETE /internal/agent/sessions/{sessionId}`

### 7.3 Java 内部工具网关

```text
POST /internal/agent/tools/{toolName}
```

这里是 Python 到 Java/MCP 的唯一受控工具入口之一，负责服务密钥、用户、会话、delegation、专家白名单、参数长度、工具白名单和审计。不得新增任意 HTTP/SQL 代理绕过它。

### 7.4 主要业务聚合接口族

| 领域 | 主要路径 |
| --- | --- |
| 库存 | `/api/inventory/distribution`、`/api/inventory/agent-read/ledger/query`、`/api/inventory/agent-read/quality/query` |
| 库位 | `/api/warehouse/agent-read/capacity-distribution/query`、`recent-operations/query`、`mixed-storage-facts/query` |
| 化验 | `/api/assay/records/query`、`report-detail/query`、`abnormalities/query`、`standard-coverage/query` |
| 质量目录 | `/api/quality/agent-read/**` |
| 托盘/二维码 | `/api/pallet-codes/lifecycle/query`、`flow-records/query`、`anomalies/query`、`batch-inbound-completion/query` |
| 生产 | `/api/production/agent-read/entities/resolve`、`boiling-batches/query`、`orders/progress/query`、`orders/material-pick-trace/query` |
| 物流 | `/api/logistics/agent-read/pallet-tasks/query`、`stock-documents/query`、`auto-inbound/**` |
| 主数据 | `/api/master-data/agent-read/**` |
| 员工/RBAC | `/api/administration/agent-read/**` |
| 审计 | `/api/audit/agent-read/**` |

### 7.5 报表

| Method | Path | 作用 |
| --- | --- | --- |
| POST | `/api/analytics/agent-read/reports/run` | 运行登记报表并持久化快照 |
| GET | `/api/analytics/agent-read/reports` | 当前用户历史快照分页 |
| GET | `/api/analytics/agent-read/reports/{reportRunId}` | 重开原始快照 |
| GET | `/api/analytics/agent-read/reports/{reportRunId}/export.xlsx` | 导出同快照 XLSX |

Agent 侧只通过 `run_registered_report` 运行报表，不能让模型拼 SQL 或自行计算核心指标。

### 7.6 L2 任务/入库预览

- `POST /api/logistics/agent-read/pallet-tasks/transition/preview`
- `POST /api/logistics/agent-read/pallet-tasks/finish-inbound/execution/preview`

前者是任务资格与 UI handoff，后者才是带最终表单字段的精确成品入库 L2 快照；两者都不执行写入。

### 7.7 默认关闭的成品入库控制面候选

- `POST /api/agent/sessions/{sessionId}/finish-inbound-execution/confirmations`
- `POST /api/agent/sessions/{sessionId}/finish-inbound-execution/confirmations/{confirmationRef}/revoke`
- `POST /api/agent/sessions/{sessionId}/finish-inbound-execution/s3/pending-preview`
- `POST /api/agent/sessions/{sessionId}/finish-inbound-execution/s3/previews/{previewRef}/confirm-and-execute`
- `POST /api/agent/sessions/{sessionId}/finish-inbound-execution/s3/confirmations/{confirmationRef}/execute`

S3 Controller 只有显式 feature flag 才注册。不要在常规开发服务中启用，也不要把这些接口注册成 MCP 工具。

固定二维码创建待入库任务复用现有：

```text
POST /api/pallet-codes/fixed-product/activate/pdf
```

该动作要求显式 Web 确认和 `qrcode:activate`，只创建任务/标签，不直接写库存。

## 8. 安全与权限底线

### 8.1 身份与最小权限

- Java `LoginUser`/JWT 是用户身份唯一权威来源；
- 前端或 Python 传来的用户字段不能覆盖 Java 登录身份；
- 主 Agent 工具集必须保持空；
- 专家工具在 Python 与 Java 各有一层白名单，并通过 registry hash 握手；
- MCP 首次绑定还要核对完整工具清单，漂移时 fail-closed；
- 工具需要同时满足用户业务权限和 Agent delegation scope。

### 8.2 写操作协议

未来任何 L3 必须同时具备：

1. 精确、不可变、短期 preview；
2. 用户在受控 UI 明确确认；
3. 服务端签发且不暴露给模型的 execution token；
4. 服务端幂等键与请求哈希；
5. 执行前实体、状态、规则与权限重检；
6. 单事务业务写入与审计；
7. 并发、断线重试、失败回滚和审计故障注入；
8. 独立最小权限、默认关闭和可回滚发布。

严禁任意 SQL、任意 HTTP、模型直接拼业务 DTO、跳过 preview、跳过确认或用自然语言“确认”替代受控按钮。

## 9. 最近一次成品入库引导修复

场景：“3号库位入库2板黄冰糖”。

最近修复了两个连续追问问题：

1. 用户选择具体产品后，Runtime 不再为组装必需的二维码来源选择额外等待一次没有业务必要的专家决策；
2. 产品中断被消费并生成新的“二维码来源”中断时，Java Gateway 现在分别维护旧/新中断：旧记录变为 `RESUMED / COMPLETED`，新记录保持 `PENDING`。

真实 HTTP/SSE 复验结果：

- 产品候选选择后正确解析 3 号库位；
- 返回“处理已有待入库任务”和“使用空闲固定二维码新建任务”；
- `message_end.finishReason=interrupt_required`；
- 没有通用错误；
- 重复提交旧产品选择只返回“这个选择已经处理过了。”；
- 执行审计为 0，没有创建任务或修改库存。

对应测试：

- `agent-service/tests/test_llm_tool_loop.py`
- `agent-service/tests/test_agent_service.py`
- `src/test/java/.../RuntimeRoutingAgentGatewayServiceTest.java`
- `webpage/src/components/agent/agentSelectionState.test.mjs`

完整记录见 [成品入库引导 UAT](finish-inbound-guided-scene-uat-2026-08-10.md)。固定二维码创建弹窗、副作用和精确预览的完整浏览器旅程仍需按该文档未勾选项继续验收。

## 10. 最近回归与证据

最近已确认：

- Python 全量历史基线：`479 passed, 5 skipped`；
- 最近连续中断修复后相关 Python 定向测试通过；
- Java `RuntimeRoutingAgentGatewayServiceTest` 定向通过；
- Java Gateway/Session/Python Client 组合定向通过；
- 根项目 `mvn -q test` 全量通过；
- `warehouse-mcp` 全量在本批改动中通过；
- Web Agent 测试历史基线 69 项通过；
- Web `npm run build` 通过，只有既有 Sass/大包警告；
- `git diff --check` 通过；
- 相关中文源码与文档未发现乱码，使用 UTF-8 无 BOM。

不要把历史数字当作修改后的自动通过。接手者每次改动后至少运行定向测试，并按风险决定是否全量回归。

主要验收资料：

- [项目阶段进度](agent-v1-progress.md)
- [只读/报表/RAG/写入整体评估](agent-holistic-acceptance-and-write-readiness-2026-08-04.md)
- [任务预览能力族封板](task-transition-preview-family-closure-and-l3-gate-2026-08-05.md)
- [S0 隔离数据库 UAT](finish-inbound-s0-isolated-db-uat-2026-08-06.md)
- [S1 精确预览 UAT](finish-inbound-s1-exact-preview-uat-2026-08-09.md)
- [S2 控制面 UAT](finish-inbound-s2-control-plane-uat-2026-08-10.md)
- [S3 受控领域执行 UAT](finish-inbound-s3-domain-execution-uat-2026-08-10.md)
- [RAG 当前本地切换](rag/rag-current-local-switch-validation-2026-08-04.md)

## 11. 本地启动与验收

### 11.1 构建

```powershell
mvn -q package -DskipTests
mvn -q -f warehouse-mcp/pom.xml package -DskipTests
```

Python 依赖使用仓库已有 `agent-service/.venv`。不要把模型密钥、数据库密码、JWT 或服务密钥写进仓库。

### 11.2 启动隔离 Java + Python

在用户明确授权读取本地 env 文件后：

```powershell
pwsh -NoProfile -File scripts/start-isolated-agent-uat.ps1 `
  -EnvFile "<user-authorized-env-file>" `
  -JavaPort 38082 `
  -PythonPort 38091
```

脚本只在进程内加载配置并生成临时安全材料。常规启动不要添加：

```text
-EnableFinishInboundS2Noop
-EnableFinishInboundS3Execute
```

### 11.3 启动 Web

让 Vite 将 `/api` 代理到隔离 Java：

```powershell
cd webpage
$env:VITE_API_PROXY_TARGET = "http://127.0.0.1:38082/"
npm run dev -- --port 5174
```

当前生成本文时，本机监听状态为：

- Web `5174`；
- Java `38082`；
- Python `38091`。

这些进程是临时状态，电脑重启后会消失。新任务必须重新核对端口和日志，不能依据本文假定在线。

隔离脚本日志位于 `output/s1-uat/`，属于本地产物，不应盲目提交。

### 11.4 常用测试

```powershell
# Java
mvn -q test

# MCP
mvn -q -f warehouse-mcp/pom.xml test

# Python
Push-Location agent-service
.\.venv\Scripts\python.exe -m pytest tests -q
Pop-Location

# Web 生产构建
Push-Location webpage
npm run build
Pop-Location
```

Web 的 `.mjs` 组件测试当前多用 Node test runner，可根据相关测试文件定向执行。浏览器验收应从用户话术和多轮旅程出发，不要只验证接口返回 200。

### 11.5 环境与凭据规则

- 不在文档、日志或提交中记录账号密码、token、服务 key、数据库密码或 env 内容；
- 读取工作区外 env 文件必须有当前用户明确授权；
- 只在进程内加载，不修改用户 env 文件；
- 本地数据库操作仍要限定到已授权的隔离验收范围；
- 验收临时数据、临时权限和会话必须清理，并记录副作用清单。

## 12. 已知问题、风险与文档债务

1. **工作区未形成新的可复现 Git 基线。** 8 月 6 日后的大量 Agent/报表/L2/L3 候选改动仍在脏工作区，其他任务不能假设远端包含这些实现。
2. **Goal/MCP 文档有少量陈述漂移。** 当前权威数量是 58 Goal、54 Java/MCP 工具、2 个 L2 预览；应在封板提交前统一所有旧数字。
3. **L3 gate 个别细项仍残留 S3 前状态。** 总体 `NO_GO` 正确，但 S3 已完成项与 S4 待办应重新归一化。
4. **成品入库引导的固定码完整浏览器旅程未全部打勾。** 特别是创建任务弹窗、创建后卡片禁用、资格预览、精确预览和最终取消/无库存写入证据。
5. **模型结构化响应有长尾。** 常见 10～23 秒可以接受，但偶发 30 秒级超时仍应用 P50/P95、模型轮次、工具次数分解，不要把工具调用误判为唯一瓶颈。
6. **库存趋势生产门禁未完成。** 本地回放不能代替连续 7 天真实日终快照和对账。
7. **RAG artifact 在线不等于进程在线。** 每次重启后检查 health/capabilities；本地 UAT 不等于生产发布。
8. **业务指标禁止幻觉扩展。** 当前没有返工、报废、损耗、SLA、计划顺延等事实；不要要求产品经理补不存在的规则后再实现，也不要模型自行推导。
9. **历史接口仍包含旧流程。** 例如半成品备料池相关接口/迁移可能仍在仓库，但 Agent、MCP 和报表已明确弃用，不能因为代码存在就重新暴露。

## 13. 推荐后续计划

### P0：先建立安全可接手基线

1. 完成固定二维码引导剩余浏览器验收并补录真实输入、输出、卡片状态和数据库副作用；
2. 复跑相关 Python、Java、MCP、Web 定向与必要全量测试；
3. 统一 58/54/52+2、S3/S4 等文档数字和机器可读 gate；
4. 清理“应提交源码/迁移/文档”与“本地输出/Playwright 快照”的边界；
5. 经用户确认后形成一次可复现 commit/push 基线。

### P1：完成报表分析的生产准备

1. 在真实目标环境积累库存连续 7 天可信日终快照和守恒对账；
2. 建立报表运行、导出、清理、数据质量与耗时监控；
3. 用仓管、质检、生产主管按真实问题做七类报表验收，记录口径理解、完成率、事实一致性、轮次和延迟；
4. 只在数据库出现真实字段和可达写入链路后，评估新指标；
5. 再评估 PDF/CSV、订阅和主动通知，不提前做模型预测。

### P2：首个 L3 候选 S4

1. 明确哪些正式角色获得 `agent:finish-inbound:execute`，默认仍为 0；
2. 在隔离数据库做真实领域写入 + 审计故障注入，确认失败关闭和整批回滚；
3. 完成完整浏览器发布验收、断线恢复、重复点击、过期/撤销/跨用户/状态变化；
4. 演练 feature flag 关闭、权限撤销和版本回滚；
5. 单独做发布评审。未通过前不注册 MCP execute；通过也只评估成品入库，不自动推广到出库/调拨。

### P3：之后再扩展

- 按同样安全模板逐项评估出库、调拨和其他写操作；
- 建立主动触发/定时任务时，先做事件来源、去重、执行窗口、失败恢复、审批与通知，不直接让模型定时写业务数据；
- 外部工作群首版只开放安全查询、登记报表、提醒与 Web 跳转确认，不在群聊内执行 L3/L4。

## 14. 新 Codex 任务接手清单

复制到新任务开头执行：

- [ ] 阅读 `AGENTS.md`、本文、产品目标、MCP 登记；
- [ ] 执行 `git status --short`，声明会保留当前脏工作区；
- [ ] 明确本任务属于 L0/L1/L2/L3/L4 哪一级；
- [ ] 找到对应 GoalContract、负责专家、工具登记和上游接口；
- [ ] 以代码和数据库真实字段确认口径，不根据旧接口名猜业务；
- [ ] 检查 Python 与 Java 专家白名单、工具清单和 registry hash 是否同步；
- [ ] 修改源码时同时补测试和/或文档；
- [ ] 先跑定向测试，再按风险跑全量；
- [ ] 检查 `git diff`、`git diff --check`、UTF-8 无 BOM 和中文乱码；
- [ ] 浏览器验收从真实用户目标、多轮选择和异常恢复出发；
- [ ] 涉及写操作时确认 preview/确认/token/幂等/事务/审计/权限/回滚全部存在；
- [ ] 最终说明“已实现、已自动化验证、已真实页面验证、仅设计、未发布”分别是什么。

## 15. 核心文档索引

### 产品与架构

- [AI 助手最终产品目标](ai-assistant-product-goal.md)
- [模块化 Agent 架构](modular-agent-architecture.md)
- [Agent 专家工具蓝图](../mcp-analysis/agent-expert-tool-blueprint.md)
- [MCP 工具登记表](../mcp/mcp-tool-registry.md)

### 机器可读登记

- [GoalContract Registry](readonly-goal-contract-registry.yaml)
- [Tool Capability Registry](tool-capability-registry.yaml)
- [Orchestration Recipe Registry](orchestration-recipe-registry.yaml)
- [Report Definition Registry](report-definition-registry.yaml)
- [Finish Inbound L3 Gate](finish-inbound-l3-gate.yaml)

### 分阶段方案与验收

- [Agent v1 进度](agent-v1-progress.md)
- [高价值分析报表计划](high-value-analytics-report-plan.md)
- [整体功能验收与写入准入](agent-holistic-acceptance-and-write-readiness-2026-08-04.md)
- [任务预览能力族封板](task-transition-preview-family-closure-and-l3-gate-2026-08-05.md)
- [成品入库 L3 设计](finish-inbound-l3-readiness-design-2026-08-05.md)
- [成品入库自然语言引导 UAT](finish-inbound-guided-scene-uat-2026-08-10.md)
- [RAG 文档入口](rag/README.md)

## 16. 最后提醒

本项目的核心价值不是把 54 个工具都暴露给模型，而是让用户用业务语言稳定完成目标，同时保持事实、权限和执行边界可验证。

接手时最危险的三种做法是：

1. 为了“更智能”绕过 GoalContract、专家白名单或 CompletionEvaluator；
2. 根据数据库中不存在的概念设计报表、预测或写操作；
3. 把本地工程/UAT 通过写成生产可用，或把默认关闭的 Web 候选写成 Agent 已有 L3 能力。

任何新能力都应继续复用：

```text
模型理解 -> 专家决策 -> 受控工具 -> 安全事实 -> 完成裁决 -> 用户反馈
```

而不是退回关键词 Router，也不是把确定性安全层交给模型。
