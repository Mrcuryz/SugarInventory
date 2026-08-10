# 成品入库 S2 控制面隔离验收（2026-08-10）

## 1. 结论

- `S2_CONFIRMATION_IDEMPOTENCY_AUDIT_LOCAL_UAT_GO`
- `BUSINESS_WRITES_0`
- `MCP_EXECUTE_TOOL_CALLS_0`
- `AGENT_L3_EXECUTION_NO_GO`
- `PRODUCTION_NOT_RELEASED`

本次只完成成品入库候选的确认、撤销、一次性凭据、幂等请求、结果重放和专用审计基础设施。没有接入既有成品入库领域事务，没有登记 `execute_finish_inbound_task`，也没有向模型开放任何写工具。

## 2. 安全边界

1. 确认 API 只接收服务端归档的 `previewRef`，不接收或覆盖仓库、日期、侧、数量、单位、备注及任何内部 ID。
2. 确认绑定当前用户、当前 Agent 会话、`task:view + task:confirm` 权限快照、预览内容哈希、业务状态摘要和短有效期。
3. `executionToken`、`idempotencyKey` 由服务端生成；数据库只保存 SHA-256，不保存明文凭据。
4. 消费前重新读取任务、托盘、产品、库位、生产关联和历史用料余额；摘要变化即使已有确认也会失效。
5. S2 消费端点由 `agent.finish-inbound.s2-noop-enabled=true` 显式启用，正常运行默认不注册该 Controller。
6. S2 适配器没有业务服务依赖，固定返回 `S2_NOOP_VALIDATED / businessWrites=0`。
7. 起点审计写入失败时不会进入适配器；成功审计、请求成功、确认消费和预览消费位于同一事务。适配器失败时令牌不消费，请求记为可重试。

## 3. 自动化证据

定向测试覆盖：

- 确认绑定用户、会话、权限、内容哈希和状态摘要；
- 重复确认重放同一服务端派生 token 与幂等键；
- 状态变化使确认失效；
- 跨用户、跨会话、错误 token、撤销和过期均在创建执行请求前拒绝；
- 已提交结果按相同幂等键重放，不再次进入适配器边界；
- 适配器失败不消费确认；
- 审计不可用时失败关闭；
- 数据库迁移只保存凭据哈希；
- S2 Controller 必须显式开关，MCP 白名单继续不存在任何 `execute_*`。

相关测试：

- `FinishInboundExecutionConfirmationServiceTest`
- `FinishInboundExecutionAttemptServiceTest`
- `FinishInboundExecutionCompletionServiceTest`
- `FinishInboundExecutionNoopOrchestratorTest`
- `FinishInboundExecutionS2ContractTest`

根项目 `mvn -q test` 全量通过，`git diff --check` 无空白错误。

## 4. 本地 MySQL + 正式 Agent 链路验收

使用 `scripts/start-isolated-agent-uat.ps1 -EnableFinishInboundS2Noop` 在本机启动 `38082 → 38091` 隔离服务；环境文件只在进程内加载。随后运行：

```powershell
pwsh -NoProfile -File scripts/verify-finish-inbound-s1.ps1 `
  -EnvFile "D:\Users\Mrcury\Desktop\laibin-shadow-model.env" `
  -BaseUrl "http://127.0.0.1:38082" `
  -LoginPassword "lbsp" `
  -VerifyS2ControlPlane
```

最终结果：

```json
{
  "Result": "PASS",
  "PreviewArchiveCount": 1,
  "BusinessWrites": 0,
  "ExecuteToolCalls": 0,
  "S2ControlPlane": "PASS",
  "S2ExecutionRef": "fie1_beb80c9b3f92410baeded22925e0e6b1",
  "S2AuditEvents": 3
}
```

真实链路逐项验证：

1. 正式 Agent 会话生成 S1 精确预览，预览归档为 `READY`；
2. 第一次受控确认签发 `fic1_ / fiet1_ / fii1_`；
3. 相同 `previewRef` 再次确认返回同一 token、同一幂等键并标记 `replayed=true`；
4. 首次 S2 消费得到 `SUCCEEDED / S2_NOOP_VALIDATED / businessWrites=0`；
5. 相同凭据重试返回同一 `executionRef`，`attempt_count` 仍为 1；
6. 确认和预览均变为 `CONSUMED`，token 与幂等键数据库字段均为 64 位哈希；
7. `USER_CONFIRMED → EXECUTION_ACCEPTED → EXECUTION_SUCCEEDED` 三条专用审计完整；
8. 托盘、任务、库存、流转、库存事件及库位容量均未改变；
9. 临时账号、会话、业务样本和 S1/S2 技术记录按外键顺序清理。

首次真实验收发现执行请求在写入非空 `status` 前先执行了 INSERT，MySQL 正确拒绝。修复为插入前一次性设置 `IN_PROGRESS`、`startedAt`、`updatedAt` 后，重新构建和验收通过。这个缺陷说明 S2 必须保留真实数据库验证，不能只依赖 Mockito。

最终复核还统一了并发路径锁顺序：消费受理和完成事务均按“确认 → 执行请求 → 预览/业务实体”推进，避免重试与完成分别反向持锁形成死锁；单元测试固定确认锁早于执行请求行锁。锁顺序修复后重新构建并完成上面的最终本地验收。

## 5. 尚未关闭的 L3 门禁

S2 通过不等于可以执行真实入库。以下内容继续留给 S3：

- 独立 `agent:finish-inbound:execute` 权限及真实角色分配；
- 受控 UI 的“确认执行/撤销”按钮，且普通用户界面不展示 token 或幂等键；
- 既有成品入库领域事务适配器；
- 业务写入、token 消费、成功结果和成功审计的同事务隔离数据库复验；
- 真实业务失败、连接中断、并发重复请求及结构化用户错误的 S3 验收；
- 独立评审后才决定是否登记并默认关闭 `execute_finish_inbound_task`。

因此当前只可声明 S2 控制面基础设施完成，L3 与生产放行仍为 `NO-GO`。
