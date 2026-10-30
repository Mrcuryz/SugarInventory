# 日报趋势 B1：后端实现与验证

日期：2026-09-23。结论：**后端切片通过专项测试，日报趋势整体验收尚未完成，不可据此宣称已上线。**

用户顺序保持：完成日报趋势并验收，然后再推进化验录入。生产汇总来源仍为车间保存的日报，不开发预测，不修改日报填写、库存或订单业务，不迁移数据库结构。

## 本批实现

- 新定义 `production_daily_report_analysis_v1`，版本1，名称“生产日报分析”。旧 `daily_production_overview_v1` 仍表示订单登记产出，未替换其口径。
- 新增 `ProductionDailyAggregateMapper/Service`，现有日报概况和新趋势共用同一事实投影；删除旧概况重复聚合SQL。查询结果不是从分页明细加总。
- 数据仅来自现有四张日报表。全厂指标与多晶产量使用白名单；产品分类按车间、分类、状态、单位及名称快照分组。不将产品不同环节、全厂和车间量相加。
- 原日报查询的 `itemQuery` 仍可匹配产品或指标名称。新报表的 `productQuery` 只匹配产品名称，筛选产品时不混入全厂指标。
- 独立返回 `productionDailyReport`，包含 `sourceLabel/requestedDays/truncated/series`。系列包含完整日期的 `points`，合计及数量/折吨独立覆盖信息；旧订单的 `metrics/dailySeries` 不被借用。
- 缺日、已保存空值和明确的0分别保留；数量、折吨的有效行数和覆盖天数分别统计。能耗只保留原单位，不拿通用折吨字段做能耗换算。
- 最多31天，不接受未来日期；车间名称受现有目录约束。先选前10个系列，再查询这些系列的完整日期数据，避免日期被结果行上限截断；超出系列上限显式标记。
- 复用现有 ReportRun 归档、所有者校验、内容哈希、有效期与审计。XLSX读取同一快照，新增“日报汇总”“日报按日趋势”，说明和限制随文件保留。能耗折吨写“不适用”，缺值保持空白，0保持数值0。
- 运行、历史列表、历史读取使用 `production:daily-report:view`；导出还需 `production:daily-report:export`。不要求订单查看权限，不给现有账号增加权限。

本批比较参数明确拒绝，不能悄悄忽略或套用旧订单比较公式。

## 接口合同（后端已实现，Agent尚未接入）

沿用 `POST /api/analytics/agent-read/reports/run`：

```json
{
  "reportDefinitionId": "production_daily_report_analysis_v1",
  "reportVersion": 1,
  "startDate": "2026-09-05",
  "endDate": "2026-09-07",
  "departmentName": "多晶车间",
  "productQuery": "黄冰糖"
}
```

后两项可省略。`departmentName`仅新日报定义支持，其他定义收到该字段会拒绝，避免静默忽略。返回外壳仍为 `Result<RegisteredReportRunVO>`。

历史与导出沿用 `/reports`、`/reports/{reportRunId}`、`/reports/{reportRunId}/export.xlsx`。运行会保存报表快照及审计，但不修改库存、订单或日报事实。

每个系列提供：`summaryType/departmentName/label/unit/tonnageApplicable`，`quantity/convertedTons`，`recordCount/quantityRows/tonnageRows/observedDays/tonnageObservedDays/draftRows/coverageLabel`。`seriesKey`是内部稳定分组键，不用于界面文案。每个日点有业务日期、数量、折吨、三类行数和未提交行数。

## 验证输入、输出与判断

以下为确定性后端用例，不冒充自然语言或浏览器验收。

| 场景/输入 | 实际输出或检查 | 判断 |
|---|---|---|
| 9月5—7日；5日只有日报表头；6、7日全厂产量100、200件，保存折吨1、2吨 | 合计300件、3吨；数量/折吨覆盖2/3天；5日值为null、记录行数0 | PASS，未把缺日补零 |
| 多晶产量6日0、7日空值 | 合计0，数量覆盖1/3天；6日0，7日null但记录行数1 | PASS，0、空值、缺行区分 |
| 全厂电量120、180 Kw.h；通用折吨列故意置999 | 合计300 Kw.h，折吨null，覆盖文案不提折吨 | PASS，未产生能耗折吨 |
| 黄冰糖：直接成品20件及空值、各日2板；半成品各日10件；另有白冰糖各日5件 | 黄冰糖筛选得到3个独立系列；直接成品“件”合计20，不夹带白冰糖或全厂总量 | PASS，环节、单位、产品筛选分离 |
| 产品筛选“总产量”与原明细名称筛选“总产量” | 新报表无产品匹配；原概况名称筛选有总产量系列 | PASS，两种参数语义未混淆 |
| 同一MySQL会话重复查询概况、翻页 | 合计与首次查询一致；300未变成600/900 | PASS，修复缓存对象重复累加 |
| 查询不存在车间 / 超过31天 / 未来日期 / 非日报定义传车间 / 新日报传比较参数 | 在读取事实前拒绝，或非日报分发前拒绝 | PASS，无静默忽略 |
| 11个候选系列、31天 | 保留10个系列，每系列31个日点，truncated=true | PASS，系列限额不截断日期 |
| 只有日报查看权限 / 只有订单查看权限 | 前者可运行新日报、不能运行订单报表；后者不能运行新日报 | PASS，方法安全代理实际执行 |
| 查看有权、导出无权；撤销查看权限 | 查看可用、导出拒绝且不写导出成功审计；无查看权限不能读取快照 | PASS |
| 保存快照后，将原内存对象的数量改为999 | 历史与导出仍为原0；XLSX空值为空、0为数值0，日期正常显示 | PASS，未重新查询或篡改旧快照 |

测试：Java日报/登记报表专项81项全部通过（包括1项真实MySQL集成测试）；Python日期、日报、Goal登记回归112项通过；Web既有日报展示逻辑4项通过。Java日志中 `audit failed` 是既有测试故意构造的审计失败路径，以JUnit结果而非日志单词判断通过。

证据：`output/daily-trend-backend-20260923/summary.json` 及同目录JUnit XML。重复执行入口：

```powershell
pwsh -NoProfile -File scripts/verify-daily-report-trend-backend.ps1
```

脚本只接受本地MySQL配置，凭据仅在子进程环境中使用并恢复原值。MySQL用例要求9月5—7日无现有日报，否则中止；不会覆盖。测试新增数据在同一事务内，成功或断言失败均强制回滚，成功后另开连接确认标记日报为0。未修改现有账号、产品、库存或线上数据库。

### 本轮发现的问题

初次真实MySQL运行发现聚合修改了MyBatis一级缓存中的系列对象，导致同会话重复调用累加。已改为复制维度后生成独立结果，并新增单元与MySQL回归。导出测试还促使日报日期显式转成日期字符串，避免受ObjectMapper日期数组设置影响。较早失败测试不计入通过证据，最终证据来自修复后重跑。

## 尚未完成与下一步

1. B2：增加 `PRODUCTION_DAILY_REPORT_ANALYSIS` GoalContract，复用 `analytics_expert` 和 `run_registered_report`；同步 Python/MCP 参数枚举、领域验证、结果压缩及 Java卡片过滤。当前仍是59个Goal，**模型尚不能调用本定义**。
2. B2：Web日报趋势卡、空日断点、独立数量/折吨与单位、历史筛选/加载、导出按钮权限；补真实HTTP、真实模型和浏览器截图验收。
3. C：前期及等长自定义比较，覆盖不同、前期0/全空的比较限制；保持日报与订单来源明确。
4. 更新完整 `docs/openapi.json`（当前静态文件尚未包含本次DTO新增字段），再做跨端与最小权限验收。本页是当前增量接口合同，不冒充完整OpenAPI已同步。
5. 日报趋势整体通过后，再进入化验录入设计与实现。当前未部署、未提交、未推送，未启动/重启用户服务。

本批没有新界面截图：尚未实现的图表不可用旧页面截图代替验收。上一批真实模型和浏览器结果只证明上一批，不作为本批新趋势验收证据。
