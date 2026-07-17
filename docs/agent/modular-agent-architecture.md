# 智能仓储模块化 Agent 架构

状态：第一版单专家路由和首个受控多专家配方已实现

适用范围：Python Agent Runtime、Agent Gateway、MCP 工具选择、模块提示词、模型策略和审计

## 1. 目标

当前 47 个 MCP 工具保持 L1 只读边界，但不作为一个扁平工具集合交给单一业务 Agent。运行时采用：

```text
用户消息
  -> 主 Agent：意图、上下文、权限边界、HITL
  -> Agent Handoff Router / 受控复合任务 Planner
  -> 一个模块专家，或按依赖图依次调用多个模块专家
  -> Java Internal Agent Gateway：最终鉴权与只读白名单
  -> 专家返回结构化安全结果
  -> 主 Agent：safe adapter、自然语言、卡片、SSE
```

该架构用于降低跨模块工具误选，并让模块提示词、工具权限、审计和模型策略可以独立演进。

## 2. 当前 Agent 划分

| Agent | 当前职责 | 当前工具范围 |
| --- | --- | --- |
| `main_agent` | 意图识别、会话上下文、HITL、安全边界、最终回答 | 无业务工具 |
| `inventory_expert` | 产品解析、库存概览、库存分布、当前台账和备料池余额 | 6 个 L1 工具 |
| `warehouse_expert` | 库位解析、容量、近期流转和混放事实 | 5 个 L1 工具 |
| `logistics_expert` | 托盘任务、库存单据和智能报数批次 | 4 个 L1 工具 |
| `pallet_expert` | 托盘状态、二维码生命周期、码池、异常、流转和批次完成率 | 9 个 L1 工具 |
| `production_expert` | 订单、煮糖、领料、标签和在制物料 | 7 个 L1 工具 |
| `assay_expert` | 运行时质量域专家：化验、化验组、标准与产品标准关系 | 12 个 L1 工具；对应蓝图 `quality_expert` |
| `master_data_expert` | 产品与筛网基础资料 | 4 个 L1 工具（含共享产品解析器） |
| `administration_expert` | 员工、角色和权限摘要 | 3 个 L1 工具 |
| `audit_expert` | 操作日志、Agent 工具审计与回答 Review | 3 个 L1 工具 |

resolver 是共享的受控实体确认能力，可以出现在多个专家白名单中；它仍不能作为最终业务答案，也不能让模型猜内部 ID。

## 3. 责任边界

### 主 Agent

- 读取用户消息、历史消息、structured state 和待确认状态。
- 生成可审计的 Intent Router 快照。
- 单模块请求只交给一个最小权限专家；已登记的复合请求生成有界依赖图。
- 负责普通用户可见的最终自然语言、卡片和 SSE。
- 不直接持有或调用业务 MCP 工具。
- 写操作、敏感凭据、无支持能力由主 Agent 直接安全说明，不委派执行。

### 专家 Agent

- 只接收自己的专业 context pack。
- 只接收自己的工具 schema，不接收其他模块工具 schema。
- 负责本模块的工具选择和结构化参数构造。
- 必须继续经过参数白名单、resolver / HITL 和 Java Gateway 鉴权。
- 只返回结构化安全结果，不直接决定普通 UI 最终展示。

### Java Gateway 与 MCP

- Java Internal Agent Gateway 仍是最终用户身份、scope、路径和工具白名单边界。
- Python 专家白名单是额外的最小权限层，不替代 Java 鉴权。
- `warehouse-mcp` 的 47 个现有只读工具由专家白名单隔离；原有 17 个工具的行为和 schema 不因本次改造改变。

## 4. Handoff 与审计

Intent Router 快照新增 `agent_handoff`：

```json
{
  "source_agent": "main_agent",
  "target_agent": "assay_expert",
  "business_domain": "assay",
  "mode": "delegate",
  "allowed_tools": ["resolve_products", "query_assay_records"],
  "model_policy_key": "assay",
  "model_parameters": {"temperature": 0.0}
}
```

实际快照中的 `allowed_tools` 是该专家完整白名单。工具结果消息同时记录执行专家，用于把意图、handoff、工具调用和最终回答关联起来。普通 UI 不展示这些内部字段。

工具调用采用双层校验：

1. 规划完成时，检查计划工具是否属于目标专家。
2. 调用 Java Gateway 前，按本次请求创建的不可变 `AgentExecutionContext` 再检查一次。

第二层失败返回 `EXPERT_TOOL_NOT_ALLOWED`，不会访问 Java Gateway，也不会解释为空数据。
模型如果选择了当前专家范围外的工具会直接被拒绝，不再自动改派给另一个专家。

同一个 `agentSessionId` 的 chat / resume 会串行执行，避免并发请求覆盖结构化上下文。
HITL pending state 绑定创建它的专家、业务域和工具白名单，恢复时不读取可变的
`active_agent`。普通 SSE 不返回完整 Router/Handoff 快照；Python 通过 Java-only
`audit` 事件传递最小 handoff 信息，由 Java 过滤后写入服务端审计。

## 5. 受控多专家编排

多专家不是专家之间自由对话，也不是把工具并集交给主 Agent。主 Agent 只能选择代码中已登记的
`CompoundExecutionPlan` 配方；每个步骤声明目标专家、依赖、工具集合和结构化输出，执行前逐项验证专家白名单。

当前已登记配方：`warehouse_inventory_latest_assay`。

```text
inventory_scope（inventory_expert）
  resolve_warehouses -> get_inventory_distribution(groupBy=product)
    -> latest_assay（assay_expert）
       resolve_products -> query_assay_records(size=1)
         -> present（main_agent，无工具）
```

约束：

- 最多 4 个步骤、12 次工具调用、5 个产品扇出。
- 有依赖的步骤串行执行；当前配方不做无依赖并行。
- 每个步骤重新创建不可变 `AgentExecutionContext`，工具调用审计记录实际专家和 `orchestration_step`。
- 专家之间只传受控产品标签和 resolver 产生的内部映射，不把一个专家的自然语言回答作为另一个专家的指令。
- 单个产品化验失败时保留库存结果，并明确标记部分失败；不得把工具失败解释为无化验。
- 库位需要消歧时，pending state 保存复合配方上下文，HITL 恢复后继续原依赖图。
- 库存分布当前没有批次级化验关联键，因此只能表述为“各产品最新化验记录”，不得表述为“当前库存批次均合格”。

只有明确的“库位库存情况 + 这些产品的化验情况”请求进入该配方。缺化验、化验异常等既有单模块查询仍走原工具，避免语义冲突。

## 6. 模型与参数策略

每个专家 profile 已包含独立的 `model_policy_key` 和默认参数。`ToolArgumentBuilder` 支持通过 `expert_model_clients` 为不同专家注入不同 `ModelClient`。

当前生产装配仍默认让所有专家共享现有 `ModelClient`，因此本次不是多模型部署。后续接入模型配置中心时，可以按专家分别配置模型、base URL、temperature、超时和 token 上限，而不改变工具安全边界。

## 7. 当前实现映射

- `agent-service/app/agents.py`：专家注册表、handoff、context pack、工具授权。
- `agent-service/app/knowledge.py`：主 Agent 业务意图 Router。
- `agent-service/app/tool_arguments.py`：专家 schema 裁剪、模型客户端选择、参数校验。
- `agent-service/app/execution.py`：不可变的单次执行授权上下文。
- `agent-service/app/orchestration.py`：受控复合配方、依赖图校验和调用预算。
- `agent-service/app/runtime.py`：handoff、调用前二次授权、safe adapter 和最终回答。
- `agent-service/app/graph/state.py`：同会话锁、有限历史、HITL 专家绑定和结构化状态。
- `docs/agent/tool-capability-registry.yaml`：模块 Agent 与工具能力登记。

## 8. 当前限制与后续演进

- 当前专家是同一 Python 进程内的逻辑 Agent，不是独立服务或独立 LangGraph 子图。
- 单测使用内存状态存储；生产配置强制 RedisStateStore、TTL、CAS 和跨 worker SessionLock。上线仍需验证 Redis 认证、TLS/受控网络、持久化、备份和故障恢复。
- 当前只开放一个库存到化验的串行复合配方；尚未开放任意跨模块拆解或并行步骤。
- 产品化验关联仍是产品级最新记录；要判断实际库存批次，需要后端提供批次/生产日期到化验报告的可靠关联键和批量聚合能力。
- 主 Agent 的最终数据回答仍使用现有确定性 safe formatter；后续可增加只接收安全结果的独立 presenter model。
- 9 个业务专家均已有 L1 工具，但 `expert_tool_roadmap` 中非 `CURRENT` 的 v2/规划占位仍不能当作已实现能力。
- 后续新增模块时，必须先登记专家 profile 和最小工具集，再新增工具 schema、参数校验、safe adapter、测试与文档。
- 任何未来 L3/L4 写工具仍必须遵循 `preview -> 用户确认 -> executionToken -> idempotencyKey -> execute`，不能因模块化 Agent 绕过安全链路。

## 9. 验收要求

- 主 Agent 工具集为空。
- 专家工具并集等于 Java/Python 当前只读白名单，不包含 `preview_*`、`execute_*` 或任意 SQL/HTTP 工具。
- 库存、库位、化验、托盘问题分别路由到对应专家。
- 专家模型只能收到本专家工具 schema。
- 跨模块工具调用在 Java Gateway 前被拒绝。
- 已登记复合配方按依赖顺序切换不可变专家上下文，主 Agent 始终没有业务工具。
- 复合配方遵守工具调用和扇出上限，部分失败不丢弃已成功的库存结果。
- 同会话并发请求不会交叉覆盖专家授权或结构化状态。
- 会话撤销会清理 Java 与 Python 两侧会话状态。
- 普通 SSE 不包含完整 `reviewTrace` / handoff 快照，服务端仍可审计目标专家。
- resolver 消歧、HITL resume、safe adapter、SSE 和现有 47 个工具回归测试保持通过。
# 生产化状态与受控编排补充

当前仅登记 `warehouse_inventory_latest_assay` 一个复合配方。执行顺序固定为 inventory_expert → assay_expert → main_agent；主 Agent 不持有业务工具，模型不能生成任意 DAG，也不能让专家自由对话。完整机器可读定义见 `orchestration-recipe-registry.yaml`。

会话、运行、不可变执行上下文、handoff、HITL、编排计划和步骤结果统一通过 StateStore 保存。单测使用 InMemoryStateStore；生产强制 RedisStateStore，并以状态版本和 CAS 防止覆盖。SessionLock 在生产使用带租约、续租和 owner token 的 Redis 锁。恢复时重新校验 recipeId、planVersion、专家顺序、白名单、扇出上限和工具预算，任何漂移均拒绝。

复合结果的数据语义固定为 `PRODUCT_LATEST_ASSAY`，不支持推断当前库存批次均合格。数据范围、时间点、部分失败及限制由确定性 formatter 输出。

## Agent v1 模块重新审计说明

2026-07-13 按前端菜单、API 和后端 Controller 重新审计后识别出的任务/单据/智能报数、产品与筛网主数据、员工/RBAC、生产和审计缺口，现已由 Agent v1 的 47 个 L1 工具覆盖。

当前 v1 专家域为：inventory、warehouse、logistics、pallet、production、quality（运行时 ID 为 `assay_expert`）、master_data、administration、audit，外加无业务工具的 main_agent。47 个工具的运行时可用性仍以 Tool Capability Registry、Java/Python/MCP 白名单、启动握手和验收测试共同为准。详细矩阵见 `docs/mcp-analysis/agent-v1-module-expert-reaudit.md`。

## 设计态长期扩展

在不改变当前 47 个只读工具和唯一运行时复合配方的前提下，后续架构预留：

- 跨专家候选配方 Registry：只允许登记步骤、固定专家和固定工具，不允许模型自由 DAG 或专家自由互聊；
- 事实/快照/指标语义层：历史趋势必须以历史快照或完整事件账为依据；
- 版本化规则引擎：Wiki 负责解释，Rule Engine 负责确定性业务判定；
- 化验文件导入暂存批次：强类型文件安全、映射、行级校验、preview/HITL/execute；
- Channel Integration Gateway：工作群先开放群安全查询、报表、通知和 Web 跳转，不在群内执行 L3/L4；
- `analytics_expert`：只访问登记报表和数据集；
- `planning_expert`：只访问登记数据、预测服务、规则引擎和求解器，生成 advisory scenarios，不持有业务 execute 工具；
- 模型治理、异步 Job、主动通知以及未来 organization/plant/warehouseArea scope。

设计蓝图和机器可读占位分别见：

- `docs/mcp-analysis/agent-expert-tool-blueprint.md`
- `docs/agent/expert-tool-roadmap-registry.yaml`
- `docs/agent/cross-expert-recipe-roadmap-registry.yaml`
- `docs/agent/data-semantic-roadmap-registry.yaml`
- `docs/agent/business-rule-roadmap-registry.yaml`

这些文件全部为 `DESIGN_ONLY`，不进入当前 Python 专家白名单、Java Gateway 白名单、warehouse-mcp 注册或运行时配方 Registry。
