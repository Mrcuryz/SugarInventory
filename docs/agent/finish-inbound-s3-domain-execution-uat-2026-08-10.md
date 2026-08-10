# 成品入库 S3 真实领域执行隔离验收

日期：2026-08-10  
范围：仅 `FINISH_IN + PENDING` 成品入库；本地隔离数据库  
结论：S3 工程实现和本地 UAT 通过，功能开关默认关闭；L3 总体仍为 `NO_GO`

## 1. 本次实现

- 新增独立权限 `agent:finish-inbound:execute`，迁移只登记权限，不自动分配给任何角色；
- 新增默认关闭的 `agent.finish-inbound.s3-execute-enabled` 受控用户接口；
- 受控界面先按当前用户、Agent 会话和托盘范围读取服务端不可变精确预览，再展示最终库位、日期、侧和数量；
- 用户点击“确认入库”后，服务端内部完成确认、一次性凭据签发和执行，凭据不进入模型、业务卡片或浏览器可见文本；
- 真实领域适配器只从服务端 `normalized_input_json` 重建请求，并复用既有 `PalletCodeService.confirmFinishedTaskInBatch` 事务，不复制库存、库位、流转或生产同步规则；
- 业务写入、成功审计、执行结果、确认消费和预览消费在同一事务中提交；
- 相同确认和幂等键在成功后只读取已提交结果，不再次调用领域事务；
- `execute_finish_inbound_task` 仍未注册为 MCP Tool，模型仍没有业务写入能力。

## 2. 自动化验证

已通过：

- 根项目全量 `mvn -q test`；
- Web 受控动作单测和 `npm run build`；
- 默认关闭、独立权限、无默认角色授权和无 MCP execute 的契约测试；
- 规范化输入只能重建允许字段，`rowNumber`、`layer` 和内部 ID 不进入执行 DTO；
- 已提交结果重放不再次调用领域适配器；
- 成功路径按“实时状态重检 → 既有领域事务 → 专用审计 → 请求/确认/预览提交”执行。

## 3. 本地隔离数据库 UAT

启动方式：

```powershell
pwsh -NoProfile -File scripts/start-isolated-agent-uat.ps1 `
  -EnvFile <local-env-file> `
  -JavaPort 38082 `
  -PythonPort 38091 `
  -EnableFinishInboundS3Execute
```

验收方式：

```powershell
pwsh -NoProfile -File scripts/verify-finish-inbound-s1.ps1 `
  -EnvFile <local-env-file> `
  -BaseUrl http://127.0.0.1:38082 `
  -LoginPassword <local-test-password> `
  -VerifyS3DomainExecution
```

最终结果：

```json
{
  "Result": "PASS",
  "PreviewArchiveCount": 1,
  "BusinessWrites": 1,
  "ExecuteToolCalls": 0,
  "S3DomainExecution": "PASS",
  "S3AuditEvents": 3,
  "S3CommittedReplay": true
}
```

实际验证的数据库事实：

- 托盘由待处理变为已入库；
- 对应任务由待处理变为已确认；
- 生成且仅生成 1 条当前库存；
- 生成且仅生成 1 条成品入库流转记录；
- 生成库存变动事件，目标库位容量同步增加；
- 控制面确认状态为已消费，权限快照包含独立执行权限；
- 执行请求 `attempt_count=1`，第二次请求读取同一已提交结果；
- 专用审计包含用户确认、执行受理和执行成功 3 个事件；
- Agent 工具审计中 `execute_%` 调用仍为 0；
- 临时账号、托盘、任务、库存、流转、仓库、控制面记录和临时角色授权均已清理。

## 4. 尚未放行的原因

S3 证明了单一成品入库候选可以安全复用既有领域事务，但不等于生产放行。S4 仍需单独完成：

- 审计写入故障注入下的真实事务回滚；
- 确认后、提交前业务状态变化的真实数据库失败路径；
- 独立权限应分配给哪些正式角色的职责评审；
- 受控界面真实浏览器验收和发布回滚演练；
- 是否注册 `execute_finish_inbound_task` 的独立安全评审。

在 S4 明确通过前，生产开关保持关闭，不扩展到半成品入库、出库、调拨、生产写入或主动触发。
