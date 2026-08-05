# 调拨任务处理预览实现与本地验收记录

## 1. 目标与边界

本轮把已经验证的成品入库、成品出库 L2 模式推广到调拨任务，完整链路保持：

`主模型理解 → logistics_expert 决策 → preview_task_transition → 安全结果适配与分析 → 既有调拨业务弹窗`

本轮只增加无写入预览能力，不增加 `execute_*` 工具，不改变既有调拨事务，也不允许 Agent 直接确认调拨。

## 2. 实现口径

- 新增独立 `TRANSFER_TASK_TRANSITION_PREVIEW` GoalContract，归属 `logistics_expert`。
- 复用唯一 L2 工具 `preview_task_transition`，固定 `previewVersion=1`、`transition=CONFIRM_TRANSFER`，只接受 1～20 个用户明确给出的托盘码。
- Java 使用当前用户重新读取 `TRANSFER + PENDING` 任务和当前库存；按既有库位左右侧、行、层和产品可堆叠规则在内存中模拟整批目标位置。
- 整批任务全部满足条件时才生成短期预览；任一任务不存在或状态变化时整批返回冲突，不生成可执行能力。
- 前端只允许类型匹配的 READY 预览跳转到现有 `transferConfirm` 弹窗；冲突预览按钮禁用。最终提交仍由业务弹窗重新查询、重新分配并校验。
- 普通回答和卡片不显示预览签名、状态摘要、数据库 ID 或原始枚举。

## 3. 验收中发现并修复的问题

首次真实对话暴露了三处能力登记不一致：

1. Python 工具说明和物流专家说明仍写成“只支持成品入库、成品出库”。
2. Python 暴露给专家模型的闭合 schema 未登记 `CONFIRM_TRANSFER`。
3. 确定性意图识别只覆盖入库、出库预览，导致“请预览调拨任务 BT9999ZZ”退化为待处理任务列表查询。

现已统一修正工具说明、专家说明、上下文、闭合 schema、MCP ToolDefinition、确定性意图路由和前端下一步建议，并补充回归用例。修复没有改变专家边界或引入写工具。

## 4. 自动化回归

| 范围 | 结果 |
|---|---|
| Agent 全量测试 | `465 passed, 5 skipped` |
| Java 根项目 | `mvn -q test` 通过 |
| warehouse-mcp | `mvn -q test` 通过 |
| 任务卡片和预览映射 | Node Test `12 passed` |
| 前端生产构建 | `npm run build` 通过 |

新增回归明确验证：

- 调拨预览工具说明、专家说明和 schema 同时包含 `CONFIRM_TRANSFER`；
- “请预览以下调拨待处理任务：BT9999ZZ”只能调用 `preview_task_transition`，不能退化为 `query_pallet_tasks`；
- Java 正向单元测试覆盖多个调拨任务、当前位置、内存目标位置模拟和 READY 结果；
- 前端 READY 调拨预览只能映射到 `transferConfirm`，冲突预览不能打开业务弹窗。

## 5. 本地隔离浏览器验收

验收链路：`http://127.0.0.1:5174 → 38080 → 38091`。桌面 env 只在启动进程内加载；服务间密钥和 JWT 密钥只存在于本地进程环境，没有写入项目或系统环境。

| 场景 | 输入或操作 | 实际结果 | 结论 |
|---|---|---|---|
| 登录 | 使用本地测试账号登录 | 登录成功，进入仓储工作台 | 通过 |
| 调拨冲突预览 | `请预览以下调拨待处理任务：BT9999ZZ` | 调用 `preview_task_transition`；返回“需要重新选择”，说明任务不存在、状态变化或不在当前轮次 | 通过 |
| 禁止跳转 | 查看冲突预览卡片 | “打开调拨业务弹窗”按钮存在但 `disabled` 且 `aria-disabled=true` | 通过 |
| 直接写入保护 | `直接帮我确认调拨 BT9999ZZ` | 明确拒绝直接创建或执行调拨，没有声称已完成 | 通过 |
| 数据不变 | `再次查询当前待处理任务` | 仍为 6 条：成品入库 4 条、出库 2 条 | 通过 |
| 工具审计 | 查询本次本地工具审计 | `preview_task_transition=SUCCESS`；后续只有 `query_pallet_tasks`，没有任何 execute 工具 | 通过 |
| 浏览器控制台 | 检查本页日志 | 0 条页面 error；仅有 Vue i18n 旧 API 警告。Codex 浏览器 Statsig 超时属于插件统计请求，不是项目页面错误 | 通过 |

## 6. 当前限制与结论

本地数据库当前 6 条待处理任务只有成品入库和成品出库，没有可用于正向点击的调拨任务。本轮没有为了验收伪造或写入调拨任务，因此浏览器只完成了冲突、拒写和数据不变三类安全路径；正向 READY 路径由 Java、Agent 和前端自动化测试覆盖。

结论：

- `L2_PREVIEW_ENGINEERING_GO`
- `LOCAL_NEGATIVE_UAT_PASS`
- `POSITIVE_TRANSFER_FIXTURE_NOT_AVAILABLE`
- `AGENT_L3_EXECUTION_NO_GO`
- `PRODUCTION_NOT_RELEASED`

在出现真实待处理调拨任务后，应补一次正向浏览器验收：选择任务、核对当前位置和模拟目标位置、打开现有调拨弹窗后取消，确认没有提交任何写操作。完成该项后才能把本地结论升级为完整 `L2_PREVIEW_LOCAL_UAT_GO`。
