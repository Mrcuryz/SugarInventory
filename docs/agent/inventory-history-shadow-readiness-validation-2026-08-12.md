# 库存历史 shadow 调度就绪性与生产门禁复核

日期：2026-08-12（Asia/Shanghai）

结论：`SCHEDULING_RUNTIME_READY / SHADOW_GATE_BLOCKED_0_OF_7 / PRODUCTION_TREND_NOT_RELEASED`

## 1. 复核目标

本轮不生成历史、不修改库存，也不把本地历史回放当成生产数据。目标是确认：

1. Windows 目标机能否真实运行独立 Capture/Verify；
2. shadow 库当前有多少可信快照和守恒对账；
3. 缺失时能否产生可监控的失败退出码与证据；
4. 管理员能否从真实浏览器看到准确、可读、不会误导发布的状态。

## 2. 真实数据结论

shadow 库截至验收时：

| 项目 | 结果 |
|---|---|
| `INITIAL_BASELINE` | 2026-08-12，1 个批次，319 个聚合维度 |
| `DAILY_CLOSE` | 0 |
| 每日守恒对账 | 0 |
| 门禁 | `BLOCKED`，0/7 |
| 最近已关闭业务日 | 2026-08-11 |
| 最近失败 | `MISSING_DAILY_CLOSE` |

启用基线建立于 00:12，不能冒充 2026-08-11 日终。系统没有执行白天回填，也没有用本地模拟推进正式门禁。

## 3. 发现并修复的部署缺口

真实目标机只有 Windows PowerShell 5.1，没有 `pwsh`。原脚本存在三个阻断：

- 注册脚本强制 `Get-Command pwsh`，因此无法创建计划任务；
- 执行器使用 Windows PowerShell 5.1 不支持的 `RandomNumberGenerator.GetBytes(int)` 和 `Convert.ToHexString`；
- ScriptBlock 调用时 `$PSScriptRoot` 可能为空，默认 jar/log 路径不能可靠解析。

现已实现：

- PowerShell 7 优先、Windows PowerShell 自动回退；
- `-PreflightOnly` 无写入预检；
- env 必需键、Java、runner、jar 与绝对路径检查；
- Windows PowerShell 5.1 可用的加密随机数实例 API；
- ScriptBlock/`-File` 两种调用方式的项目根目录定位；
- 纯 ASCII 脚本运行期文本，避免 Windows PowerShell 5.1 把无 BOM UTF-8 中文解析为非法 token。

本机预检真实返回 `READY`，并选择：

```text
C:\WINDOWS\System32\WindowsPowerShell\v1.0\powershell.exe
```

预检不创建或修改计划任务。当前机器仍没有 `LaibinInventoryHistory-Capture/Verify`；正式注册需管理员 PowerShell，这是目标环境运维动作，未在本轮静默执行。

## 4. 真实 Verify 与退出码

使用计划任务将采用的真实 `powershell.exe -File` 路径运行 Verify：

- Java 一次性进程正常启动并连接本地 shadow MySQL；
- 业务日期为 2026-08-11；
- 因缺少可信日终快照，失败代码为 `MISSING_DAILY_CLOSE`；
- Windows 子进程真实退出码为 2；
- `inventory_history_job_run` 新增 `EXTERNAL_VERIFY / FAILED` 证据；
- 没有新增 `DAILY_CLOSE`、对账结果或任何库存业务写入。

这证明退出码 2 可以作为技术缺口告警，且不会为了让任务变绿而补造历史。

## 5. 真实浏览器验收

使用最新构建、默认关闭 `INVENTORY_TREND_SIMULATION_ENABLED`，通过 Chromium 打开 Swagger 页面并以具备 `log:view` 的 shadow 管理员登录：

- 页面标题：冰糖工厂仓库管理 API；
- 登录 HTTP 200；
- `GET /api/inventory-history/operations/status` HTTP 200；
- 返回“缺少可信日终快照”“未执行”“执行失败”；
- 失败摘要为“缺少 2026-08-11 的可信日终库存快照”；
- 趋势门禁为“暂不可开放库存趋势”；
- 门禁摘要为“等待连续可信日终快照和每日守恒对账（当前 0/7 天）”；
- live OpenAPI 中正式库存趋势路径为 0。

Swagger 仅有不存在的 `/favicon.ico` 401 装饰资源错误；运维接口、登录和 OpenAPI 请求均成功，本轮没有为消除该装饰错误而放宽安全规则。

## 6. 自动化验证

通过：

```text
mvn.cmd -q -Dtest=InventoryHistorySchedulingScriptContractTest,
InventoryHistoryDailyJobServiceTest,
InventoryHistoryOperationsReadServiceTest,
InventoryHistoryOneShotRunnerTest test
```

覆盖 Windows PowerShell 回退、无写入预检、根目录定位、5.1 加密 API 兼容、退出码、失败证据和历史乱码摘要降级。

## 7. 仍未完成的生产条件

本轮不能声明库存趋势生产可用。后续必须按顺序完成：

1. 由目标环境管理员注册 Capture 00:00、Verify 00:15；
2. 把退出码 2/3 和运行证据接入现有监控；
3. 从安装日起自然积累连续 7 个可信 `DAILY_CLOSE`；
4. 每日产品全局与仓库维度守恒对账均为 `PASSED`；
5. 门禁变为 `ELIGIBLE` 后再做生产权限、空数据、范围、卡片和 XLSX 复验。

任何补造历史、直接修改门禁或用模拟数据冒充正式快照的做法都不在允许范围内。
