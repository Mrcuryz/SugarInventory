# 五类登记报表统一跨期比较验收记录

日期：2026-07-31
范围：`run_registered_report` 的只读跨期比较，不含库存趋势、预测、订阅或任何业务写入

## 1. 验收结论

五个已登记报表已统一接入跨期比较：

1. `daily_production_overview_v1@1`
2. `quality_assay_result_trend_v1@1`
3. `quality_metric_trend_v1@1`
4. `production_input_output_flow_v1@1`
5. `pallet_task_cycle_time_v1@1`

实现继续遵守“主模型理解 → 分析专家决策 → `run_registered_report` → 安全事实适配 → 模型分析”的既定架构。没有新增报表专用工具、GoalContract 或模型自由计算路径。

## 2. 输入与预期流程

### 2.1 上一等长期间

用户输入示例：`最近 7 天产量与前 7 天相比如何？`

结构化工具参数：

```json
{
  "reportDefinitionId": "daily_production_overview_v1",
  "reportVersion": 1,
  "startDate": "2026-07-25",
  "endDate": "2026-07-31",
  "comparisonMode": "PREVIOUS_PERIOD"
}
```

后端确定性推导对比期为 `2026-07-18` 至 `2026-07-24`，并使用同一产品筛选和指标版本运行两次登记报表。用户只获得一个 `reportRunId`，卡片展示本期、对比期、变化量和可用的日均值。

### 2.2 自定义对比期间

用户输入示例：`把 7 月 25 日到 31 日与 7 月 1 日到 7 日对比。`

结构化工具参数：

```json
{
  "reportDefinitionId": "daily_production_overview_v1",
  "reportVersion": 1,
  "startDate": "2026-07-25",
  "endDate": "2026-07-31",
  "comparisonMode": "CUSTOM",
  "comparisonStartDate": "2026-07-01",
  "comparisonEndDate": "2026-07-07"
}
```

Runtime 保留两组日期的结构化归属，并拒绝缺少一端、开始晚于结束、超过报表上限或与本期重叠的范围。

## 3. 自动化覆盖

- Java：上一等长期间推导、不同天数日均、重叠拒绝、五类报表中央指标映射、同快照 XLSX `跨期比较` 工作表。
- warehouse-mcp：参数安全透传、比较响应保留、旧七参数 Java 调用兼容。
- Python Agent：schema 与严格参数校验、两组日期归属、防重叠、安全有符号变化适配、会话报表上下文和业务卡片保留。
- Web：五类 summary 使用同一比较 presenter；变化保持中性展示，天数不同时提示优先查看日均。

## 4. 边界

- 比较结果只表示登记事实差异，不代表改善、恶化或因果关系。
- 化验合格率和指标达标率仍使用各自登记定义，不由模型重算。
- 生产实际领料与稳定登记产出仍是两条独立序列，不因比较功能而生成产耗比、良率或损耗率。
- 托盘任务完成耗时与进行中等待仍分开，不生成 SLA 或员工绩效结论。
- 库存趋势仍受连续日终快照与守恒对账门禁阻断，本次没有绕过该门禁。
