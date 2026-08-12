# 库存日终快照可靠调度与运行证据实施记录

日期：2026-08-01
实施切片：`V2A-DATA-06-INVENTORY-HISTORY-RELIABLE-SCHEDULING`
结论：独立调度、可信采集窗口、运行证据和失败检测已落地；库存趋势仍保持阻断，等待真实连续窗口。

## 1. 为什么不能做普通“补跑”

日终快照表达北京时间自然日结束时的库存状态。若应用在零点停机，白天启动后直接读取当前库存补成昨日日终，会混入当天已经发生的库存变化，形成不可发现的假历史。

因此本切片明确区分：

- 可信采集：只在零点后的受控窗口内生成最近一个已关闭自然日的快照；
- 安全复用：同日日终快照已经完成时，后续任务只复用，不重复生成；
- 失败证据：错过窗口、零点后已有库存变化、互斥锁超时或数据库异常时，记录失败并返回非零退出码；
- 禁止回填：应用启动和 00:15 核验任务只能检查、对账和告警，不补造快照。

默认可信采集窗口为北京时间 00:00 至 00:10，可通过 `INVENTORY_HISTORY_MAX_CAPTURE_DELAY_MINUTES` 收紧。即使仍在窗口内，只要零点后已登记库存事件，也会拒绝生成前一日日终快照。

## 2. 双调度与并发边界

保留应用内 00:00 `@Scheduled` 任务作为冗余，同时新增可由操作系统独立启动的非 Web 一次性进程：

```text
00:00  Capture：快照 → 守恒对账 → 运行证据
00:15  Verify：检查快照 → 补做幂等对账 → 运行证据/退出码
```

两条路径通过 MySQL `GET_LOCK` 按业务日期互斥，并在锁内再次检查已完成快照。外部任务和 Web 应用同时触发时，只会有一个任务构建快照，另一个复用已完成版本。

快照事务先读取日终批次和零点后事件，再读取当前库存聚合；MySQL 一致性读保证同一事务视图。如果跨日业务事件导致守恒不一致，后续对账会阻断趋势门禁，不由系统静默修正。

## 3. 运行证据

迁移 `migrations/2026-08-01-add-inventory-history-job-evidence.sql` 新增 `inventory_history_job_run`，每次 Capture、Verify 和应用启动检查保存：

- 业务日期、任务模式和触发来源；
- 运行状态与执行实例；
- 使用的快照、是否复用；
- 对账批次和对账状态；
- 失败代码、用户可读失败摘要和起止时间。

证据表不修改库存、生产或化验事实。任务主事务失败时，失败证据使用独立事务保存。

拥有 `log:view` 的管理员可通过只读接口查看最近已关闭日期状态：

```http
GET /api/inventory-history/operations/status
```

接口将内部状态翻译为“日终快照已生成”“数据不足，暂无法对账”“暂不可开放库存趋势”等用户语言，不把数据库枚举直接暴露给页面。

## 4. 退出码与告警

| 退出码 | 含义 | 运维动作 |
|---:|---|---|
| `0` | 快照、任务和守恒对账均通过 | 正常归档 |
| `2` | 缺快照、错过窗口、锁超时或技术失败 | 立即告警，禁止人工白天回填 |
| `3` | 任务已执行，但守恒对账/数据质量门禁未通过 | 数据质量告警并查看对账问题 |

应用日志不是唯一证据。Windows 任务计划程序和 systemd 都能读取进程退出码，监控系统应对 2、3 建立不同等级告警。

## 5. 部署入口

### 5.1 Windows

先构建后端可执行包。可先用普通 PowerShell 做无写入预检，再以管理员 PowerShell 注册任务；脚本会优先使用 PowerShell 7，未安装时兼容系统自带 Windows PowerShell：

```powershell
mvn -q package -DskipTests
powershell.exe -NoProfile -File scripts/register-inventory-history-tasks.ps1 `
  -EnvFile D:\path\to\backend.env `
  -JarPath D:\path\to\SugarInventory-1.0-SNAPSHOT.jar `
  -PreflightOnly

# 仅这一步需要“以管理员身份运行”
powershell.exe -NoProfile -File scripts/register-inventory-history-tasks.ps1 `
  -EnvFile D:\path\to\backend.env `
  -JarPath D:\path\to\SugarInventory-1.0-SNAPSHOT.jar
```

执行器为 `scripts/run-inventory-history-job.ps1`。任务计划程序默认创建：

- `LaibinInventoryHistory-Capture`：每日 00:00；
- `LaibinInventoryHistory-Verify`：每日 00:15。

`StartWhenAvailable` 只保证错过任务会留下失败证据；它不会绕过可信窗口补建快照。

### 5.2 systemd

部署以下单元并启用两个 timer：

- `laibin-inventory-history-capture.service/.timer`；
- `laibin-inventory-history-verify.service/.timer`。

```bash
systemctl enable --now laibin-inventory-history-capture.timer
systemctl enable --now laibin-inventory-history-verify.timer
```

两个 timer 均使用 `Asia/Shanghai`。`Persistent=true` 使错过执行能够被发现；迟到 Capture 会安全失败，而不是回填假历史。

## 6. 验收结果

- 新迁移在本地数据库执行成功，证据表存在且表注释正确；
- 新增可信窗口、零点后事件拒绝、数据库锁释放、任务证据、退出码和用户语言状态测试；
- 已完成快照即使标记为 `COMPLETED`，复用和 Verify 前仍会重新校验 `dataAsOf` 与生成前的零点后事件；
- Java 定向测试通过；
- Java 根项目全量测试通过；
- PowerShell 两个脚本完成静态语法解析；
- 当前可执行包完成真实 `Verify` 冒烟：
  - 2026-07-31 日终快照保持 1 份，没有重复生成；
  - 该快照实际生成于 2026-08-01 00:00:00.032738，落在可信窗口内；
  - 最近两次核验均保存为 `SUCCEEDED`，快照为复用；
  - 对账状态为 `INSUFFICIENT_DATA`；
  - 子进程真实退出码为 `3`；
  - 发布门禁仍为 `BLOCKED`、连续通过 `0/7` 天。
- 最新 Java、Python Runtime 和前端重新启动后，Python 鉴权健康检查为 `UP`；
- 使用本地管理员测试账号调用 `GET /api/inventory-history/operations/status` 返回：日终快照已生成、数据不足暂无法对账、任务执行完成、库存趋势暂不可开放、连续窗口 0/7。

这次结果证明了“任务运行成功”和“数据足够发布趋势”是两个独立事实。系统没有为了让测试变绿而修改门禁或补造历史。

## 7. 当前边界与下一门禁

本切片关闭了“Web 应用整夜停机就必然错过调度且没有证据”的实现缺口，但还不能宣布库存趋势完成：

1. Windows/systemd 配置已提供，目标部署环境仍需实际安装并接入现有告警平台；
2. 需要从安装日起积累连续 7 个自然日真实快照和 `PASSED` 对账；
3. 期间任何退出码 2/3 都必须处置，不能人工篡改门禁；
4. 门禁变为 `ELIGIBLE` 后仍需评审库存趋势的范围、权限、空数据、卡片和 XLSX；
5. 在生产模式通过门禁前，不允许 `inventory_level_trend_v1` 返回正式库存趋势；本地/UAT 可在显式模拟开关下运行历史回放，但不得推进或伪装通过生产门禁。

本地尝试注册 Windows 计划任务时，当前非管理员进程被系统拒绝；检查确认没有留下半注册任务。需要在管理员 PowerShell 中执行第 5.1 节命令。不能改成“仅用户登录时运行”来规避管理员权限，否则无法证明无人值守跨零点可靠性。

## 8. 本地历史回放与生产门禁的关系

本地开发机经常关机，无法靠进程持续运行自然积累连续 7 天快照。为避免工程验收被等待时间阻断，2026-08-01 增加了隔离的本地/UAT 历史回放：以当前库存为锚点，按现有入库、半成品入库和出库登记时间反向还原产品总库存，并先校验件数、重量守恒。

该回放只验证 GoalContract、专家决策、报表运行、业务卡片、历史重开和 XLSX 导出链路。它不重建历史库位分布，不证明登记时间等于现场发生时间，不生成正式日终快照，也不会修改 `INVENTORY_LEVEL_TREND` 的连续通过天数。生产环境仍必须满足本文件定义的独立调度、可信窗口、每日守恒对账和连续 7 天门禁。

## 9. 2026-08-12 shadow 环境复核

对当前 shadow 库与 Windows 目标机重新审查后确认：

- shadow 库只有 2026-08-12 建立的 `INITIAL_BASELINE`，没有 `DAILY_CLOSE` 或守恒对账；
- 2026-08-11 缺少可信快照，门禁保持 `BLOCKED`、0/7；没有执行白天回填；
- 目标机没有 PowerShell 7，原注册脚本会因强制查找 `pwsh` 而无法注册；执行器还使用了 Windows PowerShell 5.1 不支持的新版静态加密 API；
- 注册脚本现已支持 PowerShell 7 → Windows PowerShell 回退、绝对路径归一化、env 必需键与 Java/Jar 校验，以及不创建任务的 `-PreflightOnly`；
- 执行器现已兼容 Windows PowerShell 5.1，并可在 ScriptBlock 或 `-File` 调用方式下安全定位项目根目录；
- 真实 `-File` Verify 已连接 shadow 库并以 `MISSING_DAILY_CLOSE` 返回退出码 2，新增失败证据但没有库存、快照或对账写入；
- 真实 Chromium 登录后只读运维接口明确显示“缺少可信日终快照”“未执行”“执行失败”“暂不可开放库存趋势”和 0/7；OpenAPI 没有正式库存趋势路径；
- 早期迁移种子在该 shadow 库中留下全问号门禁原因，读模型现仅对此类空白/全问号历史值生成基于真实 0/7 计数的确定性中文摘要，不修改历史事实。

完整证据见 `inventory-history-shadow-readiness-validation-2026-08-12.md`。本次关闭的是“目标机脚本不可运行和状态不可读”的实现缺口，不是生产趋势门禁：管理员安装 Capture/Verify、接入告警并自然积累连续 7 个 `PASSED` 日仍为必需条件。
