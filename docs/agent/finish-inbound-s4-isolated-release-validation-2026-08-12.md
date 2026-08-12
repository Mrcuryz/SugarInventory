# 成品入库 S4 隔离发布验证

日期：2026-08-12

结论：`ISOLATED_UAT_PASS / RELEASE_NO_GO / DEFAULT_OFF / ZERO_ROLE_ASSIGNMENTS / MCP_EXECUTE_UNREGISTERED`

## 1. 范围与决策

本轮只在用户明确授权的本机 shadow 数据库执行成品入库 S4 写入、故障注入和恢复验收。验证范围是现有 `FINISH_IN + PENDING` 单场景、默认关闭的受控 Web 入口及 S2 服务端凭据控制面；没有连接生产数据库，没有增加或注册 `execute_finish_inbound_task` MCP Tool，也没有扩展到半成品、出库、调拨或主动触发。

隔离技术验收通过不等于生产放行。仓储、信息化、安全或甲方尚未指定该专用权限的正式责任角色，因此本轮发布决策保持 `NO_GO`：`agent:finish-inbound:execute` 继续零角色分配，S2/S3 开关默认关闭，MCP execute 继续未注册。

## 2. 验收中修复的启动器漂移

真实启动前发现并修复三项只影响隔离验收编排的漂移：

- `start-isolated-agent-uat.ps1` 和两个验收脚本在 UTF-8 ScriptBlock 调用时不能依赖 `$PSScriptRoot`，现支持从项目根目录安全解析；
- 仓库 `.venv` 已残缺，启动器现要求不存在仓库 Python 时显式传入 `PythonExecutable/PythonPath`，不静默使用未知解释器；
- 新安全启动门禁要求至少 12 位 Web 登录密码，启动器现显式接收并进程内设置同一验收密码。

上述配置没有写回用户指定的 env 文件。临时 Python 运行依赖只修复在既有本机临时测试依赖目录中。

## 3. 真实 MySQL 故障与状态矩阵

### 3.1 审计故障整事务回滚

通过一次性 MySQL Trigger 仅在写入 `EXECUTION_SUCCEEDED` 审计时制造数据库错误。第一次受控执行返回稳定可重试提示，并确认：

- 托盘和任务仍为待处理；
- `inventory`、`pallet_flow_record`、`stock_movement_event` 和库容增量全部为零；
- 执行请求为 `FAILED_RETRYABLE / attempt_count=1`；
- 确认和预览保持可重试；
- 删除 Trigger 后使用同一确认重试成功，最终 `attempt_count=2`，审计链完整；
- MCP `execute_%` 调用为零。

### 3.2 确认后状态变化

在确认落库后、执行请求创建前制造延迟，并发把任务改为取消。执行稳定返回“任务、托盘、库位或生产关联已经变化，请重新预览”，随后确认：

- 执行请求为 `FAILED_FINAL / attempt_count=1`；
- 确认和预览均为 `INVALIDATED`；
- 托盘、库存、流转、库存事件和库容未发生非预期写入；
- 拒绝审计链完整。

### 3.3 凭据级恢复矩阵

使用双一次性 ADMIN 账号和 S2 零写入消费端点验证服务端凭据：跨用户复用、确认撤销、强制过期和会话撤销均在执行请求创建前拒绝。撤销临时执行权限后，旧 JWT 的下一次 S3 请求也因服务端实时加载权限而返回 403。整个矩阵 `execution request=0`、业务写入为零，token 与幂等键不写入脚本输出。

## 4. 真实 Chromium 受控执行与断线恢复

浏览器使用一次性权限账号、托盘、任务和库位完成：

1. 生成真实精确 L2 预览；
2. 卡片完整展示托盘、产品、目标库位、日期、侧、数量和备注；
3. 打开“核对并确认成品入库”，再次读取服务端不可变预览；
4. 二次弹窗明确提示会修改库存和任务状态；
5. 浏览器在服务端成功提交后人为中断响应，页面显示网络失败但保持可重试；
6. 数据库反查已是 `SUCCEEDED / attempt_count=1`，任务、库存、流转、事件和库容事实完整；
7. 移除断线模拟后在同一弹窗重试，页面显示已完成，数据库仍为 `attempt_count=1`、三条成功审计和一次业务写入；
8. 页面及响应未显示 execution token、幂等键或内部主键。

本轮浏览器唯一新增 console error 是刻意模拟断线产生的 `net::ERR_FAILED`；登录前 `/api/user/info` 401 和 vue-i18n 弃用警告属于既有行为，业务正常链路没有应用异常。

## 5. 回滚验证与清理

所有一次性账号、权限、托盘、任务、库存、流转、事件、库位、Agent 会话、预览、确认、请求、审计和数据库 Trigger 已清理。反向查询结果为：执行权限角色关联 0、S4 Trigger 0、S4 账号 0、S4 库位 0。

随后以默认配置重新启动同一构建，真实 Chromium 打开 Swagger UI 并读取 live `/v3/api-docs`：207 个当前启用路径、server `/`、S2 noop 路径 0、S3 路径 0、命名 execute 路径 0；Swagger 当前页 console 0 error/warning。这证明运维回退只需关闭开关并撤销专用权限，不需要删除代码或修改数据库结构。

## 6. 未解除的生产阻断

- 没有正式角色责任人和最小授权范围的业务决策；
- 没有目标生产环境的备份、迁移、变更窗口、监控和审批记录；
- 没有独立安全评审允许把 Web 候选升级为 MCP execute Tool；
- 当前第一版 MCP 仍只允许 L0/L1/L2 实现计划。

因此下一步不是自动打开开关，而是由业务与安全责任人完成正式角色归属和单独生产发布审批；在此之前继续保持默认关闭和零分配。
