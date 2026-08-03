const REPORT_SUMMARY_KINDS = new Set([
  'daily_production_summary',
  'quality_assay_trend_summary',
  'quality_metric_trend_summary',
  'production_input_output_flow_summary',
  'pallet_task_cycle_summary'
])

const safeText = value => (typeof value === 'string' ? value.trim() : '')

export const registeredReportComparison = (card) => {
  const fields = Array.isArray(card?.fields) ? card.fields : []
  const summary = fields.find(field => REPORT_SUMMARY_KINDS.has(field?.kind))
  const comparison = summary?.comparison
  if (!comparison || !Array.isArray(comparison.metrics) || !comparison.metrics.length) return null
  return {
    label: safeText(comparison.comparisonLabel) || '跨期比较',
    currentDateRangeLabel: safeText(comparison.currentDateRangeLabel),
    comparisonDateRangeLabel: safeText(comparison.comparisonDateRangeLabel),
    differentPeriodLengths: Boolean(comparison.differentPeriodLengths),
    metrics: comparison.metrics.filter(metric => metric && safeText(metric.metricLabel)),
    notes: Array.isArray(comparison.notes) ? comparison.notes.filter(safeText) : []
  }
}

export const comparisonMetricValue = (value, unit) => {
  if (value === null || value === undefined || value === '') return '-'
  const suffix = safeText(unit)
  return `${value}${suffix ? ` ${suffix}` : ''}`
}

export const comparisonChangeText = (metric) => {
  if (!metric || metric.absoluteChange === null || metric.absoluteChange === undefined) return '无法计算变化'
  const unit = safeText(metric.unit)
  const absolute = `${metric.absoluteChange}${unit ? ` ${unit}` : ''}`
  const percent = metric.percentChange === null || metric.percentChange === undefined
    ? ''
    : `（${metric.percentChange}%）`
  return `变化 ${absolute}${percent}`
}

export const comparisonDailyAverageText = (metric) => {
  if (!metric?.additive || metric.currentDailyAverage === null || metric.currentDailyAverage === undefined) return ''
  const unit = safeText(metric.unit)
  const suffix = unit ? ` ${unit}/天` : '/天'
  const change = metric.dailyAverageAbsoluteChange === null || metric.dailyAverageAbsoluteChange === undefined
    ? ''
    : `，变化 ${metric.dailyAverageAbsoluteChange}${suffix}`
  return `日均 ${metric.currentDailyAverage}${suffix}，对比期 ${metric.comparisonDailyAverage ?? '-'}${suffix}${change}`
}
