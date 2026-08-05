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
| 确认绑定 | `PARTIAL` | `READY` 预览已持久化不可变快照，并绑定操作者、Agent 会话、最小权限快照、实体引用、状态摘要、规范化请求哈希和 5 分钟有效期；但尚无用户确认记录、独立 `confirmationRef` 或一次性消费协议 |
| 幂等与并发 | `NO-GO` | 尚无 execute 幂等表、幂等键生命周期或执行时实体版本冲突协议 |
| 事务与失败语义 | `NO-GO` | 既有业务接口各自有事务，但尚未定义 Agent execute 与业务写入、状态变化、审计的统一事务边界 |
| 细粒度执行权限 | `NO-GO` | L2 预览校验 `task:view + task:confirm`；尚未建立独立 execute 权限和职责分离 |
| 持久化 HITL | `PARTIAL` | 服务重启后可按归属和会话恢复、验真有效期内的 L2 预览；过期会拒绝继续，但尚无显式确认、撤销、消费状态迁移和清理策略 |
| 完整执行审计 | `NO-GO` | 已有工具调用审计；尚无“预览—确认—执行—业务结果—失败原因”完整写入审计 |
| 负向与恢复验收 | `PARTIAL` | 已覆盖未知类型、失效任务、权限、直接写入拒绝、数据不变，以及预览服务重建恢复、跨用户、跨会话、权限不足、过期和快照篡改拒绝；未覆盖 execute 重复点击、断网重试、事务回滚和审计失败 |
| 真实角色 UAT | `PARTIAL` | 入库、出库已有本地浏览器证据；调拨缺真实待处理样本的正向浏览器验收，生产环境角色验收未进行 |

只要任一 `NO-GO` 存在，就不能新增或开放 `execute_task_transition`。L2 预览通过不能被解释为 L3 已经“只差接接口”。

## 5. 下一步实施顺序

1. 出现真实待处理调拨任务时补一次正向本地浏览器验收：预览、核对当前位置与模拟目标位置、打开弹窗后取消、复核任务和库存未变化。
2. 在已完成的不可变预览快照、操作者、Agent 会话、最小权限快照、实体引用、过期和规范化哈希基础上，只针对首个候选场景设计显式用户确认、撤销和一次性消费协议；设计阶段仍不执行写入。
3. 设计持久化幂等记录和 execute 审计模型，明确确认消费、既有业务写入与审计的统一事务边界，并补重复确认、并发状态变化、断网重试、事务回滚和审计失败测试。
4. 单独评审首个 L3 场景。建议优先评估证据最完整、已有正向浏览器 UAT 的成品入库，不把三个 transition 一次性全部开放。
5. 只有单场景门禁全部通过后，才实现与开放该场景的最小 execute；其他场景继续保持 L2。

上述顺序以现有数据库字段、权限、业务接口和事务为准，不引入系统中不存在的审批、库存规则或业务状态。

不可变预览归档的实现与边界见 `docs/agent/task-transition-preview-persistence-2026-08-05.md`。

## 6. 首个 L3 候选专项审计补充

首个候选已按第 5 节顺序收窄为成品入库确认，但专项代码审计后仍为 `NO-GO`：

- 现有 `preview_task_transition` 只绑定 transition 和托盘码，不能绑定最终仓库、日期、侧、数量、单位和备注；
- 现有人工批量确认入口没有 20 条上限、去重和固定锁顺序；
- 确认路径锁定托盘，取消路径当前未采用相同锁策略，确认/取消竞争必须先在既有业务层整改；
- 现有工具审计不是可与业务写入一起提交的执行审计，也没有 confirmation、execution token 或 idempotency 生命周期；
- 当前只有人工业务权限 `task:confirm`，没有独立 Agent 成品入库执行权限。

因此下一工程切片从既有业务并发与批量边界加固开始，不直接新增 execute。精确协议、门禁和测试矩阵见 `finish-inbound-l3-readiness-design-2026-08-05.md` 与 `finish-inbound-l3-gate.yaml`。

### 2026-08-06 S0 更新

既有人工成品入库服务已完成 S0 加固：批量入口限制最多 20 条；整批在任何业务写入前完成托盘码规范化、不同标签同托盘去重和实体解析；确认与取消统一按托盘主键排序并使用显式行锁；取消路径同时使用任务 `FOR UPDATE` 当前读；标签关联在预解析后变化时失败关闭。新增单元合同覆盖上述边界、整批事务注解边界和“锁后托盘已确认则不得再取消旧快照任务”的回归场景。

本地 MySQL 专用样本已完成第二项失败整批回滚和五轮确认/取消竞争复验，并同时覆盖确认胜出、取消胜出两种合法终态；测试数据完成零残留清理。S0 业务加固升级为 `GO`，但没有开放 Agent 写入。精确执行预览、确认、token、幂等、独立权限和原子执行审计仍是 `NO-GO`，因此 L3 总结论不变。证据见 `finish-inbound-s0-isolated-db-uat-2026-08-06.md`。
