# 托盘任务 L2 预览能力族封板与 L3 准入评估（2026-08-05）

## 1. 结论

成品入库、成品出库、调拨三条任务处理流程已经形成一个受控的 L2 预览能力族：

`主模型理解 → logistics_expert 决策 → preview_task_transition → 安全事实适配与结果分析 → 类型匹配的既有业务弹窗`

三条流程继续保持独立 GoalContract 和独立业务校验，只共享协议版本、工具入口、闭合参数结构和展示映射。这里的“能力族”不是万能任务转换工具，也不允许模型自造 transition。

当前结论：

- `L2_PREVIEW_FAMILY_ENGINEERING_GO`
- `FINISH_INBOUND_LOCAL_UAT_GO`
- `FINISH_OUTBOUND_LOCAL_UAT_GO`
- `TRANSFER_NEGATIVE_UAT_PASS`
- `TRANSFER_POSITIVE_FIXTURE_REQUIRED`
- `AGENT_L3_EXECUTION_NO_GO`
- `PRODUCTION_NOT_RELEASED`

## 2. 能力族登记

| GoalContract | 固定 transition | 前端受控动作 | 当前本地证据 |
|---|---|---|---|
| `FINISH_INBOUND_TASK_TRANSITION_PREVIEW` | `CONFIRM_FINISH_INBOUND` | `confirmIn` | 正向、冲突、拒写、弹窗取消、数据不变均通过 |
| `FINISH_OUTBOUND_TASK_TRANSITION_PREVIEW` | `CONFIRM_FINISH_OUTBOUND` | `finishOutConfirm` | 正向、冲突、拒写、弹窗取消、数据不变均通过 |
| `TRANSFER_TASK_TRANSITION_PREVIEW` | `CONFIRM_TRANSFER` | `transferConfirm` | 自动化正向通过；浏览器冲突、拒写和数据不变通过；本地无待处理调拨样本 |

当前共有 54 个 GoalContract：原 51 个只读、报表和知识目标，加上上述 3 个 L2 预览目标。Java 仓储工具仍为 53 个，其中 52 个 L1 只读/登记报表工具、1 个 L2 预览工具；没有 L3/L4 execute 工具。

## 3. 本轮防漂移收口

Python 新增任务预览能力族的单一受控定义，统一维护：

- GoalContract 名称；
- 意图子类型；
- 后端 transition；
- 用户可读业务分组；
- 前端既有弹窗动作；
- 用户可读转换名称。

工具 schema、确定性预览规划、参数校验、GoalContract 归属和安全卡片映射均复用该定义。三条业务的真实校验仍保留在 Java 业务服务中，不把入库、出库和调拨合并成同一业务实现。

新增跨层一致性回归，要求三条定义同时存在于：

- Python GoalContract、FactType、工具 schema 和模型工具说明；
- Java MCP ToolDefinition 与预览业务服务；
- 机器可读 GoalContract/工具能力登记；
- 前端既有业务弹窗动作白名单。

未知 transition 不再回退为成品入库目标，而是返回未登记目标并由运行时失败关闭。

## 4. L3 十项硬门禁复核

| 门禁 | 当前状态 | 证据或缺口 |
|---|---|---|
| 可复现基线 | `GO` | 当前代码、登记、构建和自动化测试可复现；生产制品和回滚演练不在本地结论内 |
| preview/execute 分离 | `GO` | 只有 `preview_task_transition`，全部 execute 仍不在白名单 |
| 确认绑定 | `NO-GO` | 当前 `previewRef/stateDigest` 只服务 L2 短期摘要，未形成服务端持久化、一次性、绑定操作者与权限快照的 `confirmationRef` |
| 幂等与并发 | `NO-GO` | 尚无 execute 幂等表、幂等键生命周期或执行时实体版本冲突协议 |
| 事务与失败语义 | `NO-GO` | 既有业务接口各自有事务，但尚未定义 Agent execute 与业务写入、状态变化、审计的统一事务边界 |
| 细粒度执行权限 | `NO-GO` | L2 预览校验 `task:view + task:confirm`；尚未建立独立 execute 权限和职责分离 |
| 持久化 HITL | `NO-GO` | 当前会话状态不保证服务重启后恢复待确认动作，也没有过期、撤销和归属持久化 |
| 完整执行审计 | `NO-GO` | 已有工具调用审计；尚无“预览—确认—执行—业务结果—失败原因”完整写入审计 |
| 负向与恢复验收 | `PARTIAL` | 已覆盖未知类型、失效任务、权限、直接写入拒绝和数据不变；未覆盖 execute 重复点击、断网重试、重启恢复、事务回滚和审计失败 |
| 真实角色 UAT | `PARTIAL` | 入库、出库已有本地浏览器证据；调拨缺真实待处理样本的正向浏览器验收，生产环境角色验收未进行 |

只要任一 `NO-GO` 存在，就不能新增或开放 `execute_task_transition`。L2 预览通过不能被解释为 L3 已经“只差接接口”。

## 5. 下一步实施顺序

1. 出现真实待处理调拨任务时补一次正向本地浏览器验收：预览、核对当前位置与模拟目标位置、打开弹窗后取消、复核任务和库存未变化。
2. 只针对首个候选场景设计持久化确认协议，包括不可变预览快照、操作者、权限快照、实体版本、过期、撤销、一次性消费和规范化哈希；仍不执行写入。
3. 设计持久化幂等记录和 execute 审计模型，明确与既有业务事务的边界，并补服务重启、重复确认、并发状态变化和审计失败测试。
4. 单独评审首个 L3 场景。建议优先评估证据最完整、已有正向浏览器 UAT 的成品入库，不把三个 transition 一次性全部开放。
5. 只有单场景门禁全部通过后，才实现与开放该场景的最小 execute；其他场景继续保持 L2。

上述顺序以现有数据库字段、权限、业务接口和事务为准，不引入系统中不存在的审批、库存规则或业务状态。
