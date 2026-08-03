const REPORT_SERIES = Object.freeze({
  daily_production_report: {
    title: '登记产出重量趋势',
    pointKind: 'daily_production_point',
    categoryKey: 'businessDate',
    axes: [{ unit: 'kg' }],
    series: [
      { name: '登记产出重量', valueKey: 'totalWeightKg', unit: 'kg', color: '#2563eb' }
    ]
  },
  inventory_level_trend_report: {
    title: '库存水平趋势',
    pointKind: 'inventory_level_trend_daily',
    categoryKey: 'businessDate',
    axes: [{ unit: '件' }, { unit: 'kg' }],
    series: [
      { name: '库存件数', valueKey: 'totalPieces', unit: '件', color: '#2563eb' },
      { name: '库存重量', valueKey: 'totalWeightKg', unit: 'kg', color: '#14b8a6', axisIndex: 1 }
    ]
  },
  quality_assay_trend_report: {
    title: '化验判定趋势',
    pointKind: 'quality_assay_trend_point',
    categoryKey: 'periodLabel',
    axes: [{ unit: '%', min: 0, max: 100 }, { unit: '条' }],
    series: [
      { name: '明确判定合格率', valueKey: 'passRatePercent', unit: '%', color: '#16a34a' },
      { name: '化验记录', valueKey: 'assayRecordCount', unit: '条', color: '#93c5fd', axisIndex: 1, type: 'bar' }
    ]
  },
  quality_metric_trend_report: {
    title: '化验指标趋势',
    pointKind: 'quality_metric_trend_point',
    categoryKey: 'periodLabel',
    unitFromSummary: true,
    axes: [{ unit: '' }],
    series: [
      { name: '均值', valueKey: 'averageValue', unit: '', color: '#7c3aed' },
      { name: '中位数', valueKey: 'medianValue', unit: '', color: '#f59e0b' }
    ]
  },
  production_input_output_flow_report: {
    title: '领料与登记产出趋势',
    pointKind: 'production_input_output_flow_daily',
    categoryKey: 'businessDate',
    axes: [{ unit: 'kg' }],
    series: [
      { name: '实际领料', valueKey: 'materialInputWeightKg', unit: 'kg', color: '#f59e0b' },
      { name: '稳定登记产出', valueKey: 'stableOutputWeightKg', unit: 'kg', color: '#2563eb' }
    ]
  },
  pallet_task_cycle_report: {
    title: '托盘任务完成耗时趋势',
    pointKind: 'pallet_task_cycle_daily',
    categoryKey: 'businessDate',
    axes: [{ unit: '小时' }],
    series: [
      { name: '完成耗时中位数', valueKey: 'medianDurationSeconds', unit: '小时', color: '#2563eb', divisor: 3600 },
      { name: '完成耗时 P90', valueKey: 'p90DurationSeconds', unit: '小时', color: '#ef4444', divisor: 3600 }
    ]
  }
})

const fieldsByKind = (card, kind) => (
  Array.isArray(card?.fields)
    ? card.fields.filter(field => field?.kind === kind)
    : []
)

const numberOrNull = (value, divisor = 1) => {
  if (value === null || value === undefined || value === '') return null
  const parsed = Number(value)
  if (!Number.isFinite(parsed)) return null
  const scaled = parsed / divisor
  return Number.isInteger(scaled) ? scaled : Number(scaled.toFixed(2))
}

const displayLabel = (point, key) => String(point?.[key] || point?.label || '').trim()

const chartAriaLabel = (title, categories, series) => {
  const names = series.map(item => item.name).join('、')
  return `${title}，共 ${categories.length} 个时间点，展示${names}。具体数值可在图表下方明细中查看。`
}

export const registeredReportTrendChart = (card) => {
  const definition = REPORT_SERIES[card?.cardType]
  if (!definition) return null

  const points = fieldsByKind(card, definition.pointKind)
  if (points.length < 2) return null

  const summary = Array.isArray(card?.fields)
    ? card.fields.find(field => String(field?.kind || '').endsWith('_summary'))
    : null
  const inheritedUnit = definition.unitFromSummary
    ? String(summary?.unit || '').trim()
    : ''
  const categories = points.map(point => displayLabel(point, definition.categoryKey))
  const axes = definition.axes.map(axis => ({
    ...axis,
    unit: inheritedUnit || axis.unit
  }))
  const series = definition.series.map(item => ({
    name: item.name,
    type: item.type || 'line',
    axisIndex: item.axisIndex || 0,
    unit: inheritedUnit || item.unit,
    color: item.color,
    data: points.map(point => numberOrNull(point?.[item.valueKey], item.divisor || 1))
  }))

  if (!series.some(item => item.data.some(value => value !== null))) return null

  const title = definition.unitFromSummary && summary?.metricName
    ? `${summary.metricName}趋势`
    : definition.title
  return {
    title,
    categories,
    axes,
    series,
    ariaLabel: chartAriaLabel(title, categories, series)
  }
}
