# 成品入库 S1 精确预览隔离验收（2026-08-09）

## 结论

- `FINISH_INBOUND_S1_EXACT_L2_PREVIEW_LOCAL_UAT_GO`
- `FINISH_INBOUND_EXECUTION_IMPLEMENTATION_NO_GO`
- `AGENT_L3_EXECUTION_NO_GO`
- `PRODUCTION_NOT_RELEASED`

S1 已形成独立的 `FINISH_INBOUND_EXECUTION_PREVIEW` GoalContract，并按统一架构经过“主模型理解 → `logistics_expert` 决策 → `preview_finish_inbound_execution` → 结果分析与卡片”。该工具风险等级为 L2，只生成受控精确预览和技术性不可变快照，不确认、不签发执行令牌、不调用业务写接口。

## 已实现边界

精确预览只接受以下 7 个用户字段：

- `code`
- `warehouseName`
- `entryDate`
- `side`
- `quantity`
- `unit`
- `remark`

不接收或展示 `taskId`、`warehouseId`、`rowNumber`、`layer`、`previewRef`、状态摘要或服务端快照。后端重新读取托盘、当前轮次 `FINISH_IN + PENDING` 任务、产品、筛网、目标库位及当前占用行；同一批次按既有分配顺序模拟并预留位置，库位已满或批内超配时整批预览返回冲突。只有 `READY` 结果才归档，归档绑定当前用户、Agent 会话、`task:view + task:confirm` 权限快照、规范化输入、实体状态、内容哈希和有效期。

## 自动化证据

- Java 全量测试：`mvn -q test` 通过。
- Java 定向边界测试：`AgentSessionServiceImplTest`、`HttpPythonAgentClientTest`、`FinishInboundExecutionPreviewServiceTest`、`FinishInboundL3ReadinessContractTest` 通过。
- Python 精确预览测试：4 项通过；此前 Python 全量为 473 项通过、5 项跳过。
- warehouse-mcp 全量测试通过。
- Web 构建及精确预览请求/卡片相关 Node 测试通过。

`FinishInboundExecutionPreviewServiceTest` 额外锁定：

- 目标库位已满时失败关闭；
- 同一批两个托盘争用最后一个位置时，第二项冲突，不能把同一位置分配两次；
- 服务端快照包含首次读取的左右侧、1/2 层占用事实。

## 真实隔离链路

使用 `scripts/start-isolated-agent-uat.ps1` 在 `38082 → 38091` 启动 Java/Python 隔离服务；使用 `scripts/verify-finish-inbound-s1.ps1` 连接 `127.0.0.1` 本地 MySQL。脚本应用 S1 迁移，创建临时 ADMIN、库位、托盘和 `FINISH_IN + PENDING` 任务，通过正式 Agent 会话消息发送前端同构的可见表单请求，完成后在 `finally` 清理并断言零残留。

最终结果：

```json
{
  "Result": "PASS",
  "DatabaseHost": "127.0.0.1",
  "Goal": "FINISH_INBOUND_EXECUTION_PREVIEW",
  "Tool": "preview_finish_inbound_execution",
  "CardType": "finish_inbound_execution_preview",
  "PreviewArchiveCount": 1,
  "BusinessWrites": 0,
  "ExecuteToolCalls": 0
}
```

数据库断言确认：托盘和任务仍为 `PENDING`；未创建库存、流转记录、库存移动事件；目标库位容量未变化；没有 `execute_*` 审计。普通可见回答和卡片未暴露预览引用、内部 ID、自动分配位置或 execute 名称。

## 验收中发现并修复的真实问题

1. 新增第 54 个 Java 工具后，Java 对 Python capability snapshot 的工具注册表哈希仍是旧值，健康门禁正确地失败关闭。已更新 54 工具哈希和合同测试。
2. 精确预览 Controller 已有 `task:view + task:confirm`，但 Agent 委托令牌的只读 POST 精确白名单遗漏新路径，真实工具调用被 403 拒绝。已补充唯一精确路径并加入“所有登记 Agent 只读 POST 路径”测试；没有放宽到任意 POST。
3. 隔离启动脚本补齐 Java/Python 对同一模型密钥的进程内变量映射，并为未使用的微信登录依赖生成进程内测试值；源 env 文件未被修改。

## 下一门禁

S1 通过不代表可以执行入库。S2 才处理受控确认、撤销、一次性 token、幂等请求、结果查询和原子审计状态机，并必须先用无业务副作用适配器验证。`execute_finish_inbound_task` 仍未登记、未实现、未启用。
