const list = value => (Array.isArray(value) ? value.filter(item => item && typeof item === 'object') : [])
const object = value => (value && typeof value === 'object' && !Array.isArray(value) ? value : {})
const text = value => String(value ?? '').trim()
const count = value => {
  const parsed = Number(value)
  return Number.isFinite(parsed) && parsed >= 0 ? parsed : 0
}

const scope = (report, key, fallback = '') => text(object(report?.filtersApplied)[key]) || fallback
const qualityNotes = report => {
  const notes = object(report?.dataQuality).notes
  return Array.isArray(notes) ? notes.map(text).filter(Boolean) : []
}
const title = (report, prefix = text(report?.reportName) || '业务报表') => (
  `${prefix} · ${text(report?.dateRangeLabel) || '未标注日期范围'}`
)

const todayOperationsCard = report => {
  const overview = object(report.operationsOverview)
  const production = object(overview.productionOutput)
  const quality = object(overview.assayQuality)
  const flow = object(overview.productionFlow)
  const inventory = object(overview.currentInventory)
  const tasks = object(overview.todayPalletTasks)
  const currentPendingTaskCount = count(overview.currentPendingTaskCount)
  const fields = [{
    kind: 'today_operations_overview_summary',
    reportRunId: text(report.reportRunId),
    label: text(report.dateRangeLabel),
    value: `产出 ${text(production.totalWeightKg) || '0'} kg · 化验 ${count(quality.assayRecordCount)} 条 · 当前待处理 ${currentPendingTaskCount} 条`,
    dateRangeLabel: text(report.dateRangeLabel),
    dataAsOf: report.dataAsOf,
    latestRecordAt: report.latestRecordAt,
    isEmpty: count(production.outputRecordCount) === 0
      && count(quality.assayRecordCount) === 0
      && count(flow.materialInputRecordCount) === 0
      && count(flow.stableOutputRecordCount) === 0
      && count(inventory.productCount) === 0
      && count(inventory.palletCount) === 0
      && count(inventory.totalEquivalentPieces) === 0
      && count(tasks.cohortTaskCount) === 0
      && currentPendingTaskCount === 0
  }, {
    kind: 'today_operations_production',
    label: '今日稳定登记产出',
    value: `${text(production.totalWeightKg) || '0'} kg · ${count(production.outputRecordCount)} 条记录`,
    ...production
  }, {
    kind: 'today_operations_quality',
    label: '今日已登记化验',
    value: `${count(quality.assayRecordCount)} 条 · 合格 ${count(quality.passCount)} 条 · 不合格 ${count(quality.failCount)} 条`,
    ...quality
  }, {
    kind: 'today_operations_flow',
    label: '今日确认领用与稳定产出',
    value: `领用 ${text(flow.materialInputWeightKg) || '0'} kg · 产出 ${text(flow.stableOutputWeightKg) || '0'} kg`,
    ...flow
  }, {
    kind: 'today_operations_inventory',
    label: '当前库存水平',
    value: text(inventory.totalStockText) || `${count(inventory.totalEquivalentPieces)} 件`,
    ...inventory
  }, {
    kind: 'today_operations_tasks',
    label: '托盘任务',
    value: `今日创建 ${count(tasks.cohortTaskCount)} 条 · 当前待处理 ${currentPendingTaskCount} 条`,
    currentPendingTaskCount,
    ...tasks
  }]
  qualityNotes(report).forEach(note => fields.push({
    kind: 'today_operations_quality_note', label: '数据完整性提示', value: text(note)
  }))
  fields.push({
    kind: 'today_operations_scope_note',
    label: '口径边界',
    value: '今日事实按各自登记业务日期统计；当前库存和当前待处理任务是生成时快照。领用与产出不直接相除，本概览不计算计划达成率、收率、损耗、SLA 或预测。'
  })
  return { cardType: 'today_operations_overview_report', title: title(report), fields }
}

const dailyProductionCard = report => {
  const metrics = object(report.metrics)
  const summary = {
    kind: 'daily_production_summary',
    reportRunId: text(report.reportRunId),
    label: text(report.dateRangeLabel),
    value: `${text(metrics.totalWeightKg) || '0'} kg`,
    dateRangeLabel: text(report.dateRangeLabel),
    productScopeLabel: scope(report, 'productScope', '全部产品'),
    dataAsOf: report.dataAsOf,
    latestRecordAt: report.latestRecordAt,
    isEmpty: count(metrics.outputRecordCount) === 0,
    comparison: report.comparison || null,
    ...metrics
  }
  const fields = [summary]
  list(report.productBreakdowns).forEach(product => fields.push({
    kind: 'daily_production_product',
    label: text(product.productName),
    value: `${text(product.totalWeightKg) || '0'} kg`,
    ...product
  }))
  if (list(report.dailySeries).length > 1) {
    list(report.dailySeries).forEach(point => fields.push({
      kind: 'daily_production_point',
      label: text(point.businessDate),
      value: `${text(point.totalWeightKg) || '0'} kg`,
      ...point
    }))
  }
  qualityNotes(report).forEach(note => fields.push({
    kind: 'daily_production_quality_note',
    label: '数据完整性提示',
    value: text(note)
  }))
  return { cardType: 'daily_production_report', title: title(report), fields }
}

const inventoryLevelTrendCard = report => {
  const metrics = object(report.inventoryTrendMetrics)
  const dataQuality = object(report.dataQuality)
  const simulation = Boolean(dataQuality.simulationData)
  const signed = value => {
    const parsed = Number(value)
    if (!Number.isFinite(parsed)) return '0'
    return parsed > 0 ? `+${parsed}` : String(parsed)
  }
  const fields = [{
    kind: 'inventory_level_trend_summary',
    reportRunId: text(report.reportRunId),
    label: text(report.dateRangeLabel),
    value: `期末 ${count(metrics.closingPieces)} 件 · 净变化 ${signed(metrics.netChangePieces)} 件`,
    dateRangeLabel: text(report.dateRangeLabel),
    productScopeLabel: scope(report, 'productScope', '全部产品'),
    dataSourceLabel: scope(
      report,
      'dataSource',
      simulation ? '本地历史回放模拟' : '可信日终快照'
    ),
    simulationData: simulation,
    dataAsOf: report.dataAsOf,
    latestRecordAt: report.latestRecordAt,
    isEmpty: count(metrics.observationDayCount) === 0,
    comparison: report.comparison || null,
    ...metrics
  }]
  list(report.inventoryTrendDailySeries).forEach(item => fields.push({
    kind: 'inventory_level_trend_daily',
    label: text(item.businessDate),
    value: `${count(item.totalPieces)} 件`,
    ...item
  }))
  list(report.inventoryTrendProductBreakdowns).forEach(item => fields.push({
    kind: 'inventory_level_trend_product',
    label: text(item.productName),
    value: `期末 ${count(item.closingPieces)} 件 · 净变化 ${signed(item.netChangePieces)} 件`,
    ...item
  }))
  qualityNotes(report).forEach(note => fields.push({
    kind: 'inventory_level_trend_quality_note',
    label: simulation ? '数据来源说明' : '数据完整性提示',
    value: text(note)
  }))
  fields.push({
    kind: 'inventory_level_trend_scope_note',
    label: '口径边界',
    value: simulation
      ? '当前为本地历史回放模拟，仅用于验收报表流程；正式上线仍以连续通过守恒对账的真实日终快照为准。'
      : '库存趋势表示已登记库存水平变化，不自动解释原因，也不等同于需求预测。'
  })
  return { cardType: 'inventory_level_trend_report', title: title(report), fields }
}

const qualityAssayCard = report => {
  const metrics = object(report.qualityMetrics)
  const fields = [{
    kind: 'quality_assay_trend_summary',
    reportRunId: text(report.reportRunId),
    label: text(report.dateRangeLabel),
    value: `${count(metrics.assayRecordCount)} 条化验`,
    dateRangeLabel: text(report.dateRangeLabel),
    productScopeLabel: scope(report, 'productScope', '全部产品'),
    dataAsOf: report.dataAsOf,
    latestRecordAt: report.latestRecordAt,
    seriesGranularity: text(report.seriesGranularity),
    isEmpty: count(metrics.assayRecordCount) === 0,
    comparison: report.comparison || null,
    ...metrics
  }]
  list(report.qualityProductBreakdowns).forEach(item => fields.push({
    kind: 'quality_assay_trend_product', label: text(item.productName),
    value: `${count(item.assayRecordCount)} 条`, ...item
  }))
  list(report.standardBreakdowns).forEach(item => fields.push({
    kind: 'quality_assay_trend_standard', label: text(item.standardLabel),
    value: `${count(item.assayRecordCount)} 条`, ...item
  }))
  list(report.qualitySeries).forEach(item => fields.push({
    kind: 'quality_assay_trend_point', label: text(item.periodLabel),
    value: `${count(item.assayRecordCount)} 条`, ...item
  }))
  qualityNotes(report).forEach(note => fields.push({
    kind: 'quality_assay_trend_quality_note', label: '数据完整性提示', value: text(note)
  }))
  return { cardType: 'quality_assay_trend_report', title: title(report), fields }
}

const qualityMetricCard = report => {
  const metrics = object(report.metricTrendSummary)
  const unit = text(metrics.unit)
  const fields = [{
    kind: 'quality_metric_trend_summary',
    reportRunId: text(report.reportRunId),
    label: text(metrics.metricName),
    value: `${count(metrics.sampleCount)} 个样本`,
    dateRangeLabel: text(report.dateRangeLabel),
    productScopeLabel: scope(report, 'productScope', '全部产品'),
    dataAsOf: report.dataAsOf,
    latestRecordAt: report.latestRecordAt,
    seriesGranularity: text(report.seriesGranularity),
    isEmpty: count(metrics.sampleCount) === 0,
    comparison: report.comparison || null,
    ...metrics
  }]
  list(report.metricProductBreakdowns).forEach(item => fields.push({
    kind: 'quality_metric_trend_product', label: text(item.productName),
    value: `均值 ${text(item.averageValue) || '-'}${unit ? ` ${unit}` : ''}`, ...item
  }))
  list(report.metricStandardBreakdowns).forEach(item => fields.push({
    kind: 'quality_metric_trend_standard', label: text(item.standardLabel),
    value: text(item.rangeLabel), ...item
  }))
  list(report.metricSeries).filter(item => count(item.sampleCount) > 0).forEach(item => fields.push({
    kind: 'quality_metric_trend_point', label: text(item.periodLabel),
    value: `均值 ${text(item.averageValue) || '-'}${unit ? ` ${unit}` : ''}`, ...item
  }))
  qualityNotes(report).forEach(note => fields.push({
    kind: 'quality_metric_trend_quality_note', label: '数据完整性提示', value: text(note)
  }))
  return {
    cardType: 'quality_metric_trend_report',
    title: title(report, `${text(metrics.metricName) || '单项化验指标'}趋势`),
    fields
  }
}

const productionFlowCard = report => {
  const metrics = object(report.productionFlowMetrics)
  const fields = [{
    kind: 'production_input_output_flow_summary',
    reportRunId: text(report.reportRunId),
    label: text(report.dateRangeLabel),
    value: `领料 ${text(metrics.materialInputWeightKg) || '0'} kg · 稳定登记产出 ${text(metrics.stableOutputWeightKg) || '0'} kg`,
    dateRangeLabel: text(report.dateRangeLabel),
    productScopeLabel: scope(report, 'productScope', '全部产品'),
    dataAsOf: report.dataAsOf,
    latestRecordAt: report.latestRecordAt,
    seriesGranularity: text(report.seriesGranularity),
    isEmpty: count(metrics.materialInputRecordCount) === 0 && count(metrics.stableOutputRecordCount) === 0,
    comparison: report.comparison || null,
    ...metrics
  }]
  list(report.productionFlowDailySeries)
    .filter(item => count(item.materialInputRecordCount) > 0 || count(item.stableOutputRecordCount) > 0)
    .forEach(item => fields.push({
      kind: 'production_input_output_flow_daily', label: text(item.businessDate),
      value: `领料 ${text(item.materialInputWeightKg) || '0'} kg · 产出 ${text(item.stableOutputWeightKg) || '0'} kg`, ...item
    }))
  list(report.productionFlowOrderBreakdowns).forEach(item => fields.push({
    kind: 'production_input_output_flow_order', label: text(item.orderNo),
    value: text(item.completenessLabel), ...item
  }))
  qualityNotes(report).forEach(note => fields.push({
    kind: 'production_input_output_flow_quality_note', label: '数据完整性提示', value: text(note)
  }))
  fields.push({
    kind: 'production_input_output_flow_scope_note', label: '口径边界',
    value: '领料与稳定登记产出是两条独立序列，不计算产耗比、收率或损耗率。'
  })
  return { cardType: 'production_input_output_flow_report', title: title(report), fields }
}

const palletTaskCard = report => {
  const metrics = object(report.palletTaskCycleMetrics)
  const fields = [{
    kind: 'pallet_task_cycle_summary',
    reportRunId: text(report.reportRunId),
    label: text(report.dateRangeLabel),
    value: `任务 ${count(metrics.cohortTaskCount)} 条 · 已完成 ${count(metrics.completedTaskCount)} 条`,
    dateRangeLabel: text(report.dateRangeLabel),
    productScopeLabel: scope(report, 'productScope', '全部产品'),
    taskScopeLabel: scope(report, 'taskScope', '全部任务'),
    dataAsOf: report.dataAsOf,
    latestRecordAt: report.latestRecordAt,
    isEmpty: count(metrics.cohortTaskCount) === 0,
    comparison: report.comparison || null,
    ...metrics
  }]
  list(report.palletTaskCycleDailySeries).filter(item => count(item.taskCount) > 0).forEach(item => fields.push({
    kind: 'pallet_task_cycle_daily', label: text(item.businessDate), value: `${count(item.taskCount)} 条任务`, ...item
  }))
  list(report.palletTaskCycleTypeBreakdowns).forEach(item => fields.push({
    kind: 'pallet_task_cycle_type', label: text(item.taskTypeLabel), value: `${count(item.taskCount)} 条任务`, ...item
  }))
  list(report.palletTaskPendingItems).forEach(item => fields.push({
    kind: 'pallet_task_cycle_pending', label: text(item.palletCode), value: text(item.taskTypeLabel), ...item
  }))
  qualityNotes(report).forEach(note => fields.push({
    kind: 'pallet_task_cycle_quality_note', label: '数据完整性提示', value: text(note)
  }))
  fields.push({
    kind: 'pallet_task_cycle_scope_note', label: '口径边界',
    value: '这里只统计系统已登记托盘任务；完成耗时与进行中等待分开，不代表 SLA、员工绩效或现场全部流程。'
  })
  return { cardType: 'pallet_task_cycle_report', title: title(report), fields }
}

const builders = {
  today_operations_overview_v1: todayOperationsCard,
  daily_production_overview_v1: dailyProductionCard,
  inventory_level_trend_v1: inventoryLevelTrendCard,
  quality_assay_result_trend_v1: qualityAssayCard,
  quality_metric_trend_v1: qualityMetricCard,
  production_input_output_flow_v1: productionFlowCard,
  pallet_task_cycle_time_v1: palletTaskCard
}

export const registeredReportSnapshotCard = report => {
  const normalized = object(report)
  const builder = builders[text(normalized.reportDefinitionId)]
  return builder ? builder(normalized) : null
}
