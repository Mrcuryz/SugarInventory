# Agent V1.1b-0 模型主导语义判断 Shadow 设计

状态：`IMPLEMENTED_DEFAULT_OFF / EXECUTION_UNCHANGED`  
日期：2026-07-15

## 1. 审计结论

现有实现并非真正由大模型规划业务任务。`OpenAICompatibleModelClient` 继承确定性的 `BasicModelClient.plan_next_action` 和参数构造，大部分 Router 分支在模型被调用前已经确定 intent、tool 和 arguments；真实模型主要生成直答文本并复述已审核答案。

这解释了系统为何在已覆盖表达上稳定，但遇到自然改写、指代、复合目标或结果缺口时像接口调用器。问题不是模型调用次数少，而是模型没有位于关键语义决策点。

## 2. 责任边界

| 决策 | 模型职责 | Runtime 最终职责 |
|---|---|---|
| 用户最终目标 | 生成受约束 GoalDraft | 匹配登记 GoalContract，拒绝未知目标 |
| 是否需要新数据 | 判断 NONE / FRESH_READ / REUSE / CLARIFICATION | 检查事实时效、权限和允许动作 |
| 实体与指代 | 识别提及和建议复用类型 | 解析/校验 opaque ref、用户、会话、scope、TTL |
| 工具适用性 | 后续可在专家白名单内排序候选 | 工具白名单、schema、参数和调用预算最终裁决 |
| 用户确认 | 可识别歧义并建议追问 | 强制 HITL、签发 token，模型不得免除确认 |
| 结果是否满足目标 | 后续生成 ResultReasoningDraft | 必需事实、错误分类和 limitations 硬门禁 |
| 最终回答 | 基于安全事实解释、区分事实与推断 | safe adapter 保证数值、单位、来源不可篡改 |

## 3. 当前最小链路

```text
用户消息 + 最近安全对话 + 已确认实体展示摘要
  -> 模型 GoalDraftV1
  -> 严格 Pydantic / JSON Schema 校验
  -> 仅保留脱敏 Shadow 摘要
  -> 当前 Intent Router / 登记配方照常执行
```

Shadow 摘要不记录 `requestedOutcome` 和实体原文，不记录工具名建议，不含内部 ID，也不进入工具参数。它只额外记录本次草案耗时。模型调用失败、超时、返回非法 JSON 或额外字段时标记为 `UNAVAILABLE`，原 Router 继续执行。

## 4. GoalDraftV1

模型可输出：

- 三个试点目标，或 `OUT_OF_SLICE / UNSUPPORTED / UNCLEAR`；
- 用户想得到的结果描述；
- 实体提及类型、是否为显式提及/指代/上下文复用；
- 缺少的实体类型；
- `NONE / FRESH_READ / REUSE_CONFIRMED_FACTS / CLARIFICATION`；
- 展示偏好、追问需要和置信度。

模型不能输出：toolName、expertName、步骤、SQL、表列、Join、权限范围、数据库 ID、entityRef、公式、HTML 或组件名。Schema 位于 `schemas/goal-draft-v1.schema.json`。

## 5. ResultReasoningDraftV1

本切片只冻结结构，不接入执行。它允许模型基于未来的安全 FactEnvelope 提议完成状态、缺失/失败事实、矛盾、限制和登记追问类型。它不能修改事实，也不能签发动作。Schema 位于 `schemas/result-reasoning-draft-v1.schema.json`。

## 6. Feature Flag 与安全退化

- `AGENT_GOAL_DRAFT_SHADOW_ENABLED=false` 为默认值；
- 关闭时不调用 GoalDraft 模型，行为与 V1 完全一致；
- 开启时模型草案的 `executionInfluence` 永远为 `false`；
- 当前消息和最近对话在发送前对常见 token、Authorization、密码和密钥模式脱敏；
- 只向模型提供已确认实体的展示标签，不提供内部 ID；
- 不新增、删除、隐藏或修改 47 个 MCP 工具；
- 不改变专家白名单、唯一登记配方、Java Gateway 权限或 HITL。

## 7. Shadow 评测门禁

至少用真实 UAT 和多轮样本分别统计：

1. goalType 正确率；
2. dataNeed 正确率，尤其是不必要工具调用；
3. 指代和上下文复用正确率；
4. 应追问问题的追问率；
5. 不支持/写操作识别率；
6. 当前 Router 与 GoalDraft 分歧样本的人工裁决；
7. 延迟、超时和非法结构率。

安全负向、跨实体误用、权限扩大、写操作和无依据结论必须 100% 受 Runtime 拦截。只有 GoalDraft 在真实样本上显著优于当前 Router，且模型失败可安全退化，才允许在 V1.1c 将它升级为 GoalContract 候选输入。不能因为平均准确率更高就移除现有 Runtime 校验。

## 8. 下一最小切片

完成 V1.1a 浏览器门禁后，冻结三个 GoalContract 与 EntityContextV1 的兼容字段；随后让模型 GoalDraft 只参与“候选合同排序”，Runtime 仍可拒绝或追问。结果反思和 GoalCompletionEvaluator 必须等安全 FactEnvelope 就绪后再接入，避免让模型阅读工具原始响应或自行宣布完成。
