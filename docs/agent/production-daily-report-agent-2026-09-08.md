# 生产日报接入 Agent：实现与本地测试记录

> 后续已增强生产概况和完整范围汇总，默认产量问题不再要求先选数据来源。本文保留原明细接入基线；当前口径与结果见 [生产概况接入](production-daily-overview-2026-09-08.md)。

日期：2026-09-08。状态：代码实现、本地自动化及独立卡片展示检查通过；真实模型、实际数据库、完整登录对话及服务器部署尚未验收。本次未读写生产数据库，未部署、未提交 Git，也未修改库存业务数据。

后续进展：已完成真实模型与本地空数据接口/页面联调，并修复运营时间范围表达问题；有明细场景因本地日报为空仍待验收。此处保留第一批测试基线，详见 [真实联调记录](production-daily-report-live-acceptance-2026-09-08.md)。

## 1. 本批范围与架构

用户目标示例：“查看今天多晶车间的生产日报”“查看9月1日至7日黄冰糖的日报填报明细”“下一页”。

沿用现有链路：主模型识别目标 → production_expert 决定参数及工具 → query_production_daily_reports → 事实适配及 GoalContract 完成校验 → 模型分析与生产日报卡片。没有新增关键词直达查询分支，也没有新增写入工具。

- 新 GoalContract：PRODUCTION_DAILY_REPORT_RECORDS，仅允许并以 query_production_daily_reports 为完成证据。
- 当前总数：59 个目标、11 个专家、55 个 Java 工具（53 L1 + 2 L2），另有 1 个进程内知识工具。
- 分页上下文保存为 LAST_DAILY_REPORT_QUERY；追问由模型及专家继续决策，工具仍校验参数。
- 今日运营/生产分析继续读取订单登记产出，只增加来源边界说明；本批没有将日报量合并到运营报表。
- 单说“今天产量”且没有来源上下文时要求澄清；明确指定订单登记产出时不因出现“日报”二字改变来源。

## 2. 后端接口及真实字段

`POST /api/production/agent-read/daily-reports/query` 是查询接口，使用 readOnly 事务。Controller 和 Service 均要求 `production:daily-report:view`；仅有订单查看权限不足以调用。沿用已认证用户的 Agent 委托与内部工具网关，不接受模型传入用户身份。

| 参数 | 约束 |
| --- | --- |
| startDate / endDate | 必填 YYYY-MM-DD，包含首尾，最多31天；不静默默认日期 |
| departmentName | 可省略；多晶车间、红糖车间、单晶车间、全厂汇总 |
| itemQuery | 可省略；产品或指标名称的字面子串，最多100字符，不作为SQL通配表达式 |
| page / size | 默认1/20；page 1–10000，size 1–50 |

接口示例：

```json
{"startDate":"2026-09-08","endDate":"2026-09-08","departmentName":"多晶车间","itemQuery":"黄冰糖","page":1,"size":20}
```

数据库来源仅为现有 `production_daily_report`、`production_daily_report_section`、`production_daily_metric_value`、`production_daily_product_line`。产品名称、指标名称、分组、单位均取已保存快照/字段。返回 daily_actual、converted_tons、month_quantity、month_tons、year_tons 原值，不 SUM、不填零、不重新折吨；不读取生产订单产出表，不生成未保存的模板行。

返回包含 sourceLabel、dateRangeLabel、scopeLabel、isEmpty、page、size、total、hasMore、reports、records、limitations。reports 为日期范围内日报表头；records 按车间/名称过滤后分页。total 是匹配明细条数，不是日报份数。isEmpty 只表示范围内没有日报表头；空页、没有匹配产品都不等于没有日报或现场产量为0。

对用户分别呈现“整份日报：草稿/已提交”和车间状态。单日日期只显示一次。内部ID、状态枚举不进入卡片；未填报不等于0；指标和产品、小计与全厂汇总、不同单位不能直接累加。填报产率/能耗仅展示原值，不能据此推断损耗原因或预测。

本接口尚未回写到历史 `docs/openapi.json` 导出快照；后续启动完整后端时需重新导出并核对。当前以 Controller、DTO/VO 和本节为准。

## 3. 自动化测试输入、输出与判断

以下为受控测试，不是真实模型或实际生产记录。

| 用例 | 输入/流程 | 输出或断言 | 判断 |
| --- | --- | --- | --- |
| 日报主链路 | 固定业务时间2026-07-21；“查看今天多晶车间的生产日报”；脚本模型依次委派、调用、分析 | 错误模型日期2020-01-01被业务时钟规范为2026-07-21；得到日报卡片及目标完成事实 | 通过，脚本模型 |
| 分页追问 | 上述会话继续“下一页” | 专家获得原日期、车间与分页上下文；调用page=2；会话序列化保留查询 | 通过，工具响应为mock；真实跨页数据待联调 |
| 原值与状态 | 草稿日报、已提交车间；dailyActual=0，convertedTons=null，monthQuantity=123.45 | 保留0、null、累计原值；单日标题2026-09-08；isEmpty序列化字段正确 | 通过 |
| 内部字段隔离 | mock增加productId及PREPRINTED | 卡片无产品ID或内部枚举 | 通过 |
| 范围和分页 | 超31天、日期倒置、未知车间、page=0/true、size=51 | 参数拒绝，不执行查询 | 通过 |
| 查询安全 | 名称包含SQL样式文本 | MyBatis生成绑定参数、稳定排序及分页，无SUM/COALESCE/订单产出表 | 通过，仅SQL绑定检查，未实库执行 |
| 权限 | 仅production:order:view调用日报Service | Spring方法安全拒绝，Mapper未调用 | 通过 |
| 缺失日报与空页 | 无表头；另测有表头而明细空 | 前者NO_DATA且不推定产量0；后者AVAILABLE，不伪报“没有日报” | 通过 |
| MCP映射 | mock HTTP返回日报分页响应 | 新工具只调用专用只读路径；0和null及分页信息保留 | 通过 |
| 展示 | 下述独立fixture加载真实Vue卡片；点击累计值；390px/1280px视口 | 原单位、草稿/提交分层、未填报、分页提示和累计展开可读 | 通过，非完整助手页面 |

测试代码：

- `src/test/java/com/Laibin/SugarInventory/production/service/ProductionDailyReportAgentReadServiceTest.java`（新增5项）。
- `warehouse-mcp/src/test/java/com/Laibin/SugarInventory/mcp/WarehouseToolsTest.java` 及 `WarehouseMcpApplicationTest.java`。
- `agent-service/tests/test_production_daily_reports.py`（新增10项，含参数化）。
- `webpage/src/components/agent/dailyReportRecordsPresentation.test.mjs`（新增2项）。

最终回归：Python全量514通过、5跳过；Java定向59个测试类共264通过；warehouse-mcp 73通过；Web Agent 74通过。合计925通过、5跳过，新增用例已计入各套件，不重复累加。Java不是全项目全量测试。

Python wheel重建通过，源码与打包运行时一致性测试通过。Web生产构建通过；保留已有Sass弃用、bundle体积警告，不是本批新增功能失败。

执行方式：

```text
agent-service: .\.venv\Scripts\python.exe -m pytest -q
根目录: mvn -q -Dtest=*Agent*Test,*RegisteredReport*Test,FinishInbound*Test,ProductionDailyReport*Test,HttpPythonAgentClientTest test
根目录: mvn -q -f warehouse-mcp/pom.xml test
webpage: node --test src/components/agent/*.test.mjs
webpage: npm run build
```

## 4. 浏览器证据

使用Playwright技能加载独立测试页 `webpage/tests/fixtures/daily-report-records.html`，明确标识“展示验收数据，不是生产业务记录”。测试页不发业务API请求，未进入正式构建入口。验收期间修正了fixture引用不同Vue运行时造成的样式缺失，最终截图使用与业务组件相同的运行时。

- [桌面：累计值展开](../../output/playwright/production-daily-records-desktop.png)
- [窄屏：390px卡片](../../output/playwright/production-daily-records-mobile.png)

截图位于本地output目录，可能被Git忽略；交接/发布验收材料时需连同截图另行归档。

## 5. 发布边界与下一步

本次工具注册hash变化，Java、MCP、Python、Web需要作为匹配版本发布，不可只替换其中一个组件。新toolRegistryHash：`32290bc2fadf3f0b9a096426352e3c4b8b5fddfaaa9c222320325f60e4c18191`；profileHash：`2e330afea7cb537019d215134ad09eb8909d8e1ead20c38eee5f8d755808781e`。原配方hash未变。没有增加新的日报权限或数据库迁移。

下一步先在本地隔离服务进行真实模型及现有数据库只读验收：

1. 日报有保存数据的日期，与Web逐行核对产品/指标、单位、0/空值、草稿/提交状态及分页；必须包含实际SQL执行。
2. 今天/昨天/最近30天、单日、多车间、产品名称模糊查询、下一页、改变查询范围、无数据、权限不足。
3. “今天整体运营情况如何”“今天产量”“看生产日报”“看订单登记产出”对比口径，确认不错误路由、不相加、不将字段缺失解释为0。
4. 更新现有59个目标的场景矩阵，继续库存/化验/生产/RAG、报表导出和异常恢复回归；保留每项输入、输出、截图、判断及版本。
5. 其后完善现有成品入库：二维码来源区分、只到库位、缺信息追问、强确认、并发与幂等、超时后结果恢复、任务/卡片/库存一致性。

设备管理只读及其他写入保持后续独立范围，不把本批完成等同于所有Agent能力封板。
