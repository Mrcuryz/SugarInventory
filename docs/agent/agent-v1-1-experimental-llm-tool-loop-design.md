# Agent V1.1 Experimental LLM Tool Loop

状态：`LOCAL_UAT_ONLY`

默认模式：`AGENT_PLANNING_MODE=deterministic`

实验模式：`AGENT_PLANNING_MODE=llm`

## 1. 独立判断

方向总体正确。当前主要体验缺口不是“用了确定性工具”，而是模型没有稳定经历：

```text
理解目标 → 提出行动 → 观察安全结果 → 修正或继续 → 形成有依据的回答
```

但以下判断必须纠正：

1. 模型可以提出“是否完成”，不能成为完成状态的唯一权威。Runtime 仍必须校验工具调用预算、证据引用、未解决工具错误、权限拒绝和参数边界。
2. 模型可以理解 Runtime 状态，不能把展示标签、历史消息或工具返回文字变成权威执行参数。
3. “llm 模式”不能等于一次开放全部 9 个专家。当前真实 UAT 集中在库存、库位和化验；先验证这三个专家能否产生净收益。
4. DeepSeek 的真实延迟可能成为首要产品风险。Shadow 单次模型调用约 5～10 秒；包含 resolver 的一次完整任务可能需要主模型一次、专家模型三次，若无优化可能达到 20～40 秒。准确率提升但延迟不可接受时，本实验仍应判定 NO-GO。
5. 现有产品、库位工具尚未统一接收外部 `entityRef`。首个实验使用仅存在于 Runtime 会话状态中的 `CURRENT_PRODUCT` / `CURRENT_WAREHOUSE` 绑定，由 Runtime 注入现有工具所需内部参数。它不是一个新的通用 EntityContext 平台，也不向模型暴露数据库 ID。

## 2. 实验运行路径

```text
用户消息 + 脱敏会话历史 + 少量权威状态
  → 主模型：直接回答 / 澄清 / 单专家 / 登记配方 / 不支持
  → Runtime 校验实验专家白名单和配方登记
  → 专家模型：一次提出一个工具动作
  → Runtime 校验专家白名单、模型可见 schema、受控状态引用、查询范围和调用预算
  → Java Gateway / MCP / Java Service 最终鉴权并执行
  → Runtime 将安全结果转换为有状态的 observation
  → 专家模型：继续调用 / 澄清 / 完整回答 / 部分回答 / 不支持
  → Runtime 校验证据引用、错误状态和完成边界
  → 最终回答
```

跨专家请求不进入自由循环。当前只有 `warehouse_inventory_latest_assay` 可进入现有登记配方；模型提出配方后，Runtime 必须再次用现有确定性配方匹配器验证。

## 3. 第一版边界

| 项目 | 第一版规则 |
| --- | --- |
| 环境 | 仅 development/test/UAT；production 启用时启动失败 |
| 默认路径 | deterministic，不改变现有 Router 基线 |
| 实验专家 | inventory、warehouse、assay |
| 单轮专家数 | 1 |
| 工具调用 | 最多 3 次，包含 resolver 和重试 |
| 工具重试 | 最多 1 次；仅同一参数、明确 retryable 的失败可重试 |
| 参数纠正 | 模型参数或越权动作最多允许 1 次无执行纠正 |
| 工具类型 | 现有 L1 只读工具；不新增或修改 MCP schema |
| SQL / HTTP / 写操作 | 禁止 |
| 多候选 resolver | 强制进入现有 HITL，并绑定原专家、用户、会话、TTL 和工具白名单 |
| 跨专家 | 仅现有登记配方 |
| 工具错误 | `TOOL_ERROR` / `PERMISSION_DENIED` / `NO_DATA` 分离 |
| 完成判断 | 模型提出，Runtime 校验后才接受 |

现有 47 个工具、9 个专家登记、Java Gateway 权限、MCP 能力握手和 deterministic 路径保持不变。实验只把三个专家的现有工具集合提供给模型。

## 4. 最小协议

本实验不继续扩建 GoalContract、FactEnvelope 或通用 FollowupAction 平台，只保留两个严格模型输出：

### 4.1 主模型动作

```text
DIRECT_ANSWER
ASK_CLARIFICATION
DELEGATE
RUN_REGISTERED_RECIPE
UNSUPPORTED
```

主模型不能填写工具名、工具参数、步骤、权限范围、SQL、Join 或内部 ID。

### 4.2 专家循环动作

```text
CALL_TOOL
ASK_CLARIFICATION
FINAL_ANSWER
PARTIAL_ANSWER
UNSUPPORTED
```

`FINAL_ANSWER` 和 `PARTIAL_ANSWER` 必须引用本轮实际 `observationId`。Runtime 不接受不存在的引用，也不接受只引用 `TOOL_ERROR` 的业务结论。

Observation 是本轮内部协议，不是新的企业级事实平台。它只保存：

```text
observationId
status
tool（仅内部审计）
安全 data
重试状态
```

## 5. 模型和 Runtime 的责任

| 模型负责 | Runtime / Java 负责 |
| --- | --- |
| 理解用户当前目标和指代 | 会话归属、权限和数据范围 |
| 判断是否需要实时读取 | 禁止模型直接回答实时事实 |
| 选择一个专家 | 专家实验白名单和不可变执行上下文 |
| 在专家集合内提出工具 | Python、Java、MCP 三层工具白名单 |
| 构造模型可见参数 | schema 校验、内部引用注入、范围限制 |
| 根据观察决定继续或追问 | 3 次调用、1 次重试、1 次参数纠正上限 |
| 提出完整或部分回答 | 错误/无数据/权限拒绝分类和完成门禁 |
| 解释有依据的结果 | Java 最终业务规则、审计和安全适配 |

## 6. 实体交接

模型可见参数中的：

```json
{"productRef": "CURRENT_PRODUCT"}
```

由 Runtime 转换为当前会话中 resolver/HITL 已确认的产品。模型生成 `productId`、`warehouseId` 或其他数据库 ID 会被拒绝。

`displayLabel` 不进入模型权威状态。Runtime 只提供 `canonicalName` 和状态引用。若模型在用户没有输入完整规格标签时，把已有展示标签重新提交给产品 resolver，Runtime 拒绝该参数并只允许一次纠正。

现有库存到化验登记配方的代码路径已经要求 `canonicalProductName`，缺失时停止下游查询，不会退回 `displayLabel`。真实浏览器仍需在最新 Java/MCP 构建上复测；源码通过不等于部署闭环。

## 7. 主要风险

### P0 / P1

- 模型把工具错误描述为无数据；Runtime 已阻止只引用错误 observation 的完成结论。
- 明确新库位查询错误继承旧产品过滤；必须加入包含多产品固定数据的 UAT，不能继续用恰好只有一个产品的数据证明正确。
- 工具安全结果仍缺统一输出 schema；本实验只对三个专家做递归敏感字段过滤和库存专用适配，不据此开放全部专家。
- 模型最终文字仍可能夸大业务含义；必须统计无依据结论率，库存批次合格、历史趋势等表述为硬负向用例。

### 产品风险

- 多模型往返延迟可能抵消全部“智能感”收益。
- 结构化 JSON 不稳定会导致可校验计划失败。
- 每次都先调用主模型可能浪费延迟；只有真实评测证明后，才讨论会话内专家粘滞或小模型路由，不能先优化掉主模型参与。

## 8. 实验验收

首轮真实 UAT 至少覆盖：

1. `查询黄冰糖（袋）的库存`：resolver 后继续查库存。
2. `具体存放在哪些库位`：复用产品并切换到分布查询。
3. `1号库位有哪些库存`：显式库位范围替换旧产品过滤。
4. `只看黄冰糖`：在当前库位目标内缩小产品范围。
5. `换成2号库位`：替换库位，保留或清除其他过滤必须符合用户表达。
6. `它最新化验怎么样`：新一轮切到 assay 专家并复用已确认产品；没有“最新”能力时应改用登记的记录查询，不能把指定日期状态冒充最新。
7. resolver 多候选：进入 HITL，恢复后继续原专家循环。
8. 工具超时：允许一次受控重试，不得回答无数据。
9. 模型生成原始 ID、越权工具、SQL、HTTP 或写操作：100% 被拒绝。
10. 库存到化验：仍只走登记配方和结构化规范名。

核心门禁：

- 安全、权限、跨专家、原始 ID 和工具错误误判：100%。
- 目标完成率显著高于 deterministic 基线。
- 平均不必要工具调用数不高于约定阈值。
- P95 总延迟达到 UAT 可接受值；未约定前不进入生产讨论。
- deterministic 全量回归、47 工具数量和专家白名单不变。

在真实 DeepSeek UAT 结果出来前，本设计结论是 `CONDITIONAL_GO_FOR_LOCAL_UAT`，生产仍为 `NO_GO`。
