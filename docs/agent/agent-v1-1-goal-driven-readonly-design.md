# Agent V1.1 目标驱动只读对话与任务闭环设计

状态：`CORE3_CONTRACT_SLICE_IMPLEMENTED / FULL_REGRESSION_AND_FIELD_PILOT_PENDING`  
日期：2026-07-17  
边界：不建设语义查询 Runtime，不增加或变更 47 个 MCP 工具输入 schema，不增加自由 Planner、自由 DAG、写工具或跨域 Join。

## 1. 独立判断与范围收敛

“Agent 更像接口调用器”的判断基本成立，但根因不是工具数量本身，而是当前执行链把“工具调用成功”当成了“用户目标完成”，并且展示数据、执行实体、事实状态之间缺少稳定边界。

本阶段不应按提示词原样建设四套新平台。代码中已经存在：

- `SelectedEntity` 与 Redis/内存状态恢复；
- Java `AgentEntityRefCodec` 的用户、实体类型、权限摘要和 TTL 绑定；
- HITL 候选选择和恢复；
- 唯一登记的 `warehouse_inventory_latest_assay` 复合计划、步骤预算、扇出限制和计划指纹；
- Python safe adapter 和工具错误分类。

因此采用以下更小方案：

1. `EntityContextV1` 是现有 `SelectedEntity` 的版本化演进，不另建 Entity Graph。
2. `ReadTaskPlan` 复用现有登记复合计划和单目标执行入口，只增加三个登记目标的元数据和完成规则，不建设第二套 Planner。
3. `FactEnvelope` 只在 Python safe adapter 后生成；Java 与 MCP 保持业务 DTO，不分别维护一套事实转换器。
4. `GoalCompletionEvaluator` 是纯确定性函数，只消费登记合同和安全事实状态。
5. `FollowupAction` 优先复用现有 HITL/恢复签名机制；在证明不能满足之前，不新增通用动作平台。

这意味着 V1.1a 只修复已确认的实体交接和数据口径提示，不提前实现 V1.1b-d 抽象。

### 1.1 2026-07-15 模型职责修正

代码审计确认，`OpenAICompatibleModelClient` 在 V1.1 前没有覆盖
`plan_next_action` 和 `build_tool_arguments`，因此生产模型主要参与直答措辞与最终流式复述，主路径的意图、工具链和大量参数仍由确定性分支先行决定。用户感受到“接口调用器”有直接代码证据。

但本设计明确反对“除工具外全部由模型最终决定”。调整后的原则是：

```text
模型持续负责理解、语义判断、结果反思和自然对话；
Runtime 持续负责合同匹配、权限、工具白名单、参数校验、执行和完成硬门禁。
```

因此在 V1.1b 正式迁移前增加 `V1.1b-0`：受约束 `GoalDraftV1` Shadow Mode。模型先判断目标、实体提及、上下文复用、数据需要和追问需要，但不得输出工具名、专家名、执行步骤、权限范围、内部 ID、SQL 或 Join。Shadow 草案只做脱敏对照，不影响当前 Router 执行。

同时冻结 `ResultReasoningDraftV1` 结构，供后续模型判断“结果是否回答了用户目标”；Runtime 的 `GoalCompletionEvaluator` 仍是最终状态裁决者。模型建议 `COMPLETE` 而登记事实缺失时，结果必须是 `PARTIAL` 或 `FAILED`。

## 2. 当前根因与本阶段处理

| 根因 | 当前证据 | V1.1处理 |
|---|---|---|
| 展示标签进入执行链 | 复合配方把 `productLabel` 重新传给 `resolve_products` | V1.1a 阻断 |
| 实体状态字段语义不足 | `SelectedEntity` 仅有内部 ID、展示名和 metadata | V1.1b 最小演进 |
| 工具成功等于目标完成 | 复合步骤已有状态，但没有登记的完成策略 | V1.1c 增加确定性判断 |
| 追问依赖关键词和 last_* | 多轮上下文能工作，但目标范围不稳定 | V1.1c-d 只迁移三个目标 |
| 库存件数口径未签署 | distribution 使用加法公式，其他入口存在不同处理 | V1.1a 只加限制，不选公式 |
| 库存读取权限不一致 | overview/distribution/ledger/summary 权限注解不同且无统一行级范围 | 保留风险，追问不得扩大范围 |

## 3. V1.1a 最小改动

### 3.1 产品实体交接

库存分布组增加只读输出字段：

```json
{
  "canonicalProductName": "黄冰糖（袋）",
  "productLabel": "黄冰糖（袋） 40kg/件 25件/板"
}
```

约束：

- `canonicalProductName` 来自 Java 查询结果的权威产品名称字段，不由模型或 Python 从标签推断。
- `productLabel` 与 `groupLabel` 仅展示。
- 当前 `resolve_products` 不接受实体引用，因此复合配方可使用 `canonicalProductName`；解析不唯一时保持局部失败，不自动选择。
- 字段缺失时不得回退到正则截取 `productLabel`，而是产生 `ENTITY_CONTEXT_MISSING` 局部结果。
- 前端“补充化验”展示动作也不得从 `productLabel` 解析产品名；没有规范名时不生成该动作。
- 本切片不改变 MCP 工具名、数量、输入 schema、专家白名单和权限。

`entityRef` 仍是后续优先方向，但当前库存分布走普通 `/api/inventory/distribution`，没有安全注入会话绑定实体引用的权威位置。为了 V1.1a 强行扩展 Controller、RBAC 和引用编解码器，成本和回归面大于收益。因此先使用确定性规范名并保留“可能不唯一则不执行”的安全退化。

### 3.2 库存散件公式

经 2026-07-15 业务确认，当前库存等价件数公式冻结为：

```text
CASE WHEN pieces > 0
     THEN pieces
     ELSE quantity * product.pieces_per_pallet
END
```

一条现行库存记录对应一个二维码板位。`quantity=1 && pieces=0` 表示满板；`quantity=1 && 0<pieces<pieces_per_pallet` 表示不足满板但仍占用一个完整板位，实际件数只取 `pieces`。用户同时确认：产品存在当前库存时不得修改 `pieces_per_pallet` 或 `weight_per_piece`，不同规格必须新建产品。

V1.1a 据此：

- 修正 inventory distribution、无近期化验产品、质量标准覆盖三个当前库存聚合入口，避免部分板重复叠加整板；
- 与已经采用相同口径的库存 summary 视图对齐；
- 移除“口径未确认”的固定限制文案；
- 产品更新同时做 Service 前置校验和 Mapper 原子 `NOT EXISTS inventory` 条件；
- `quantity > 1 && pieces = 0` 仅作为历史多板记录兼容，不再认定为现行合法写入状态。

仍未闭环的是当前部署数据画像：如果存在 `quantity<=0`、`pieces>=pieces_per_pallet` 或其他非法历史状态，应由数据质量检查明确报告，不能由模型或查询公式静默修正。

### 3.3 权限边界

| 入口 | Controller 权限 | Service/Mapper 数据范围 | Agent 边界 | V1.1结论 |
|---|---|---|---|---|
| overview `/api/inventory/stock/page` | 认证用户（全局默认鉴权） | 未发现仓库行级注入 | `mcp:warehouse:read` + inventory 专家白名单 | 范围未统一确认 |
| distribution `/api/inventory/distribution` | `isAuthenticated()` | 查询条件可限定仓库，但不是用户数据权限 | 同上 | 范围未统一确认 |
| ledger | `inventory:view` | 未证明统一仓库范围注入 | 同上 | 权限更严格 |
| summary `/api/inventory/summary` | `record:query` | 未证明统一仓库范围注入 | 非复合配方主入口 | 权限不同 |
| 库存到化验复合配方 | 两个专家白名单 + Java Gateway 最终鉴权 | 继承各工具实际范围 | 最大扇出 5、登记配方 | 不得扩大起始库位范围 |

本阶段不调整 RBAC。统一契约在确认前保持草案：

```yaml
capability: inventory_read
requiredPermissions: [TO_BE_CONFIRMED]
deploymentScopeMode: OTHER
scopeInjectionOwner: JAVA_BACKEND
scopeSource: TO_BE_CONFIRMED
agentPlanMaySpecifyScope: false
permissionDeniedCode: UPSTREAM_PERMISSION_DENIED
status: BLOCKED
```

在契约确认前，V1.1 追问只能保持或缩小已执行范围，不能从单库位、单产品自动扩大到全部库存。

## 4. V1.1b 契约冻结方案

V1.1b 只有在 V1.1a 回归和浏览器复测通过后进入。

### 4.1 EntityContextV1

首版覆盖 `PRODUCT`、`WAREHOUSE`、`PRODUCTION_ORDER`、`BOILING_BATCH`、`PALLET`、`ASSAY_RECORD`。实现上扩展 `SelectedEntity`，不增加图结构。

最小字段：`schemaVersion`、`entityType`、`entityRef`、`canonicalName`、`displayLabel`、`resolutionStatus`、`resolvedBy`、`resolvedAt`、`expiresAt`、`scopeHash`、`source` 和受控 `attributes`。

兼容规则：旧状态缺字段时只允许展示或重新解析；不得凭旧 `display_label` 构造执行参数。多个同类型实体不能靠“最后一个”自动选择。

### 4.2 三个 GoalContract

仅登记：

1. `CURRENT_PRODUCT_INVENTORY`
2. `PRODUCT_INVENTORY_DISTRIBUTION`
3. `WAREHOUSE_INVENTORY_DISTRIBUTION`

合同是静态注册表，包含版本、所需实体、所需事实、允许配方、完成策略、局部失败策略、限制和可用追问类型。GoalDraft 不得携带工具名、专家名或步骤。

原计划中的 `WAREHOUSE_INVENTORY_WITH_LATEST_ASSAY` 不进入前三合同：批次键虽已确认，但当前库存质量查询尚未统一到最新批次化验事实模型。它继续作为受限旅程/旧复合配方存在，不得宣称批次质量完成。

### 4.3 五个 FactEnvelope schema

完整 GoalContract 只登记三个事实：`CURRENT_PRODUCT_INVENTORY`、`PRODUCT_INVENTORY_DISTRIBUTION`、`WAREHOUSE_INVENTORY_DISTRIBUTION`。`PRODUCT_LATEST_ASSAY`、`WAREHOUSE_STATUS`、`ENTITY_RESOLUTION` 仍可作为现有模型/工具语义类型，但本轮不接完整完成合同。

机器可读 JSON Schema 是唯一结构权威；Python safe adapter 是唯一事实转换责任。Java/MCP 输出仍是业务 DTO，避免跨三层重复转换。

## 5. V1.1c 执行与完成判断

- 当前单工具路径和 `CompoundExecutionPlan` 被适配为三个目标的确定性计划，不允许模型填步骤。
- 计划限制继续使用现有最大步骤、调用数、扇出、不可变上下文和计划指纹。
- `GoalCompletionEvaluator` 输出 `COMPLETE`、`PARTIAL`、`NEEDS_CLARIFICATION`、`UNSUPPORTED`、`FAILED`，以及缺失/失败/不支持事实、限制和服务端允许动作。
- 工具返回成功但事实 schema 不完整，不能 `COMPLETE`。
- 化验 `NO_DATA` 与 `TOOL_ERROR` 分开；库存成功、单产品化验失败必须为 `PARTIAL`。
- Feature Flag 关闭时完整回退到 V1 路径。

2026-07-17 最小实现已落在 `agent-service/app/goal_contracts.py`：

- `EntityContextV1` 兼容演进现有 `SelectedEntity`；
- Python safe adapter 后生成 `FactEnvelopeV1`；
- 三个静态 `GoalContractV1`；
- 确定性 `GoalCompletionEvaluator`；
- 确定性 Router 的完成状态进入 review trace；LLM 最终回答不能越过合同完成门禁。

这不表示现场验证已经完成。真实角色对比方案见 `docs/agent/evaluation/core-task-router-llm-pilot-v1.md`。

## 6. V1.1d 受控追问与前端增量

- 只实现登记动作，不允许模型生成可点击按钮。
- 优先将现有 resume token 扩展为 `actionRef` 载荷；若单次/幂等语义无法兼容，再引入独立 codec。
- 前端只给现有卡片增加版本化外层元数据，不替换卡片。
- Shadow Mode 只记录 GoalDraft 与当前 Router 的脱敏差异，不改变执行。

## 7. 切片门禁

### V1.1a 进入 V1.1b 主路径迁移

- 带规格后缀的真实格式标签从未作为 resolver 参数；
- 缺少规范名时安全局部失败，不解析展示标签；
- 多产品扇出仍受现有上限限制；
- 库存公式未被擅自修改，限制在 Agent 答案可见；
- 47 个工具、专家白名单和握手一致；
- Python、Java、warehouse-mcp 全量测试、前端构建和真实浏览器复测通过。

`V1.1b-0` Shadow Mode 不改变执行，可在浏览器门禁完成前合入但默认关闭；任何让模型草案影响 Router、工具或参数的改动仍受上述门禁限制。

### 后续切片停止条件

如果三个目标的多轮 UAT 不能显著降低无谓工具调用或用户轮次，停止 V1.1c-d，不继续抽象。若权限范围不能确定性复用，所有扩大范围的追问保持禁用。若事实 schema 只是原工具响应的机械包裹且不改善完成判断，则不推广到更多工具。

## 8. V1.1a 文件级计划

1. Java DTO/Service：新增 `canonicalProductName`，加入未确认件数口径限制；不改 SQL。
2. MCP 输出模型：透传新增只读响应字段；不改工具输入 schema。
3. Python schema/safe adapter：允许规范名；复合配方和卡片动作只使用规范名。
4. 测试：Java 权威来源、SQL 差异特征、MCP 透传、Python 带规格后缀/缺字段/多产品扇出/限制展示。
5. 验证：三层全量测试、前端生产构建、握手与工具数量、编码与 diff、真实浏览器复测。
