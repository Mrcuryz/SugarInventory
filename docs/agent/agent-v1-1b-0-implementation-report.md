# Agent V1.1b-0 实施报告：模型目标草案 Shadow Mode

日期：2026-07-15  
结论：`SHADOW_GO / PRIMARY_ROUTER_NO_GO`  
默认状态：`AGENT_GOAL_DRAFT_SHADOW_ENABLED=false`

## 1. 修改范围

- 新增受约束 `GoalDraftV1` 与尚未接入执行的 `ResultReasoningDraftV1` Pydantic 契约；
- 提交与运行时模型完全一致的 JSON Schema，并用测试阻止 schema 漂移；
- `OpenAICompatibleModelClient` 新增目标草案调用；
- `ToolArgumentBuilder` 可在默认关闭的 Shadow Mode 中调用模型，并把脱敏摘要放入内部 review trace；
- 增加 Shadow 输入脱敏、实体上下文最小化和模型失败安全退化；
- 更新 V1.1 设计、Agent Runtime 说明和环境变量样例。

没有新增 MCP 工具，没有修改 47 个工具 schema，没有改变 Router、专家白名单、登记配方、Java Gateway、HITL、前端卡片或数据库访问。

## 2. 修复的真实用户问题

本切片不宣称已经修复某个用户可见问题。它修复的是架构验证缺口：此前真实模型没有参与主路径意图和目标理解，却缺少可与现有 Router 同题对照的安全机制。

Shadow Mode 使后续可以用真实浏览器/UAT 问题判断：失败究竟来自模型语义理解、Router、实体上下文还是工具能力，而不是继续凭印象增加关键词。

## 3. 新增契约及版本

- `GoalDraftV1` / `schemaVersion=1.0`：只描述目标、实体提及、上下文复用、缺失实体、数据需要、展示偏好、追问需要和置信度；
- `ResultReasoningDraftV1` / `schemaVersion=1.0`：只冻结模型结果反思结构，本切片不调用；
- 两者都 `additionalProperties=false`，模型提交 `toolName` 等执行字段会被拒绝。

## 4. 目标完成规则

本切片没有实现或改变 `GoalCompletionEvaluator`。`GoalDraftV1` 不是完成判断，也不是执行计划。当前目标完成行为保持 V1/V1.1a 现状。

后续采用双层规则：模型可提出 `ResultReasoningDraftV1.proposedStatus`；Runtime 依据 GoalContract、必需事实、错误分类和 limitations 最终裁决。两者冲突时以 Runtime 为准。

## 5. 是否改变现有工具行为

不改变。Feature Flag 关闭时没有新增模型调用。开启时模型草案的 `executionInfluence=false`，即使模型故意给出错误目标，当前 Router 和工具执行仍保持原结果。

Shadow audit 摘要不保存目标自然语言和实体原文，只保留目标类型、实体类型/引用种类、数据需要、追问判断、置信度和耗时；不保存内部 ID、工具建议或权限范围。

## 6. 新增和回归测试

- Shadow Flag 默认关闭与显式开启；
- 关闭时不调用目标草案模型；
- 模型草案与 Router 冲突时不改变工具执行；
- Authorization/Bearer 等上下文发送前脱敏；
- 已确认实体只提供展示标签，不提供内部 ID；
- Pydantic 与提交 JSON Schema 完全一致；
- GoalDraft 拒绝额外执行字段；
- OpenAI-compatible 模型合法 JSON 解析与非法额外字段拒绝；
- 应用装配层开启 Shadow 后仍执行原 Router 工具。

门禁结果：

| 门禁 | 结果 |
|---|---|
| Python Agent 全量 | 164 passed |
| Java 全量 | 203 passed，0 failure/error |
| warehouse-mcp 全量 | 50 passed，0 failure/error；有既有 Surefire fork 退出提示但进程码为 0 |
| 前端生产构建 | 通过；仅既有 Sass deprecated 与 chunk size 警告 |
| 工具/专家/配方基线 | 测试继续断言 `toolCount=47`、`recipeCount=1` 和 9 个专家白名单 |
| diff whitespace | 通过；仅工作区既有 LF/CRLF 提示 |
| UTF-8/BOM/乱码 | 本切片文件未发现 BOM、替换字符或常见乱码特征 |

## 7. 未闭环风险

1. 尚无足够真实 UAT 样本证明模型 GoalDraft 优于 Router；不能升级主路径。
2. V1.1a 第二次真实浏览器和脱敏执行轨迹门禁仍未正式记录为完成。
3. 当前 Shadow 只覆盖三个试点 goalType；其他 44 个工具相关问题会归为 `OUT_OF_SLICE`，不能据此评价完整 Agent。
4. `ResultReasoningDraftV1` 尚未接收 FactEnvelope，也没有 GoalCompletionEvaluator；系统仍未形成完整的“工具后反思”。
5. EntityContextV1、三个 GoalContract 和五个 FactEnvelope 尚未冻结，不应提前让 GoalDraft 驱动执行。
6. 启用 Shadow 会增加一次模型调用及延迟；需要在真实模型上统计可用率、结构失败率和延迟，不能默认成本可接受。

## 8. 是否建议进入下一切片

建议进入受控 Shadow UAT 准备，不建议进入“模型替换 Router”或 V1.1c 主路径迁移。

下一步先完成：

1. 关闭生产开关，仅在本地/UAT 开启；
2. 用真实浏览器问题记录 Router 与 GoalDraft 的脱敏分歧；
3. 人工裁决不少于三个试点目标的单轮、指代、复合和不支持样本；
4. 补齐 V1.1a 浏览器执行轨迹证据；
5. 冻结 EntityContextV1 兼容字段和三个 GoalContract 后，重新决定是否允许 GoalDraft 参与候选合同排序。

## 9. 批判性修正

- “模型参与更多”不等于“模型拥有更多最终权限”。本切片只扩大语义参与，不扩大执行权限。
- 不把 GoalDraft 设计成另一种自由 Planner；它没有工具、步骤或权限字段。
- 不在此时同时实现 FactEnvelope、ReadTaskPlan、FollowupAction 和前端元数据；这会在缺少真实评测前形成新平台。
- 不再把现有 `ModelClient` 接口存在视为真实模型已经参与规划；必须以具体实现是否覆盖语义方法为准。
