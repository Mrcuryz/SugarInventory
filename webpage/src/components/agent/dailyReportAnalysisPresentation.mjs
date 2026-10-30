const list = value => Array.isArray(value) ? value : []
const numeric = value => value === null || value === undefined || value === '' ? null
  : Number.isFinite(Number(value)) ? Number(value) : null

export const dailyAnalysisSummary = card => list(card?.fields).find(row => row.kind === 'daily_analysis_summary') || {}
export const dailyAnalysisSeries = card => list(card?.fields).filter(row => row.kind === 'daily_analysis_series')
export const dailyAnalysisNotes = card => list(card?.fields).filter(row => row.kind === 'daily_analysis_note').map(row => row.value)
export const dailyAnalysisValue = (value, unit = '') => numeric(value) === null ? '未填报' : `${numeric(value)}${unit ? ` ${unit}` : ''}`

export const dailyAnalysisChart = (series, metric = 'quantity') => {
  if (!series || !['quantity', 'convertedTons'].includes(metric)) return null
  if (metric === 'convertedTons' && !series.tonnageApplicable) return null
  const points = list(series.points)
  if (!points.length) return null
  const unit = metric === 'convertedTons' ? '吨' : series.unit || ''
  const name = metric === 'convertedTons' ? '折吨' : '数量'
  return {
    title: '按日趋势', ariaLabel: `${series.label}按日${name}，缺失日期保留空值，不补零。`,
    categories: points.map(point => point.businessDate), axes: [{ unit }],
    series: [{ name, type: 'line', axisIndex: 0, unit, color: '#2563eb', data: points.map(point => numeric(point[metric])) }]
  }
}

export const dailyAnalysisSnapshotCard = report => {
  const aggregate = report.productionDailyReport || {}
  const series = list(aggregate.series)
  const textKeys = ['summaryType', 'departmentName', 'label', 'unit', 'coverageLabel']
  const countKeys = ['recordCount', 'quantityRows', 'tonnageRows', 'draftRows', 'observedDays', 'tonnageObservedDays']
  const rawComparison = report.comparison
  const comparison = rawComparison && Array.isArray(rawComparison.metrics) ? {
    ...Object.fromEntries(['comparisonMode', 'comparisonLabel', 'currentDateRangeLabel', 'comparisonDateRangeLabel',
      'currentPeriodDays', 'comparisonPeriodDays', 'differentPeriodLengths', 'comparisonDataAsOf']
      .map(key => [key, rawComparison[key]])),
    metrics: rawComparison.metrics.map((row, index) => ({ metricCode: `daily_${index}`,
      ...Object.fromEntries(['metricLabel', 'unit', 'additive', 'note'].map(key => [key, row[key]])),
      ...Object.fromEntries(['currentValue', 'comparisonValue', 'absoluteChange', 'percentChange', 'currentDailyAverage',
        'comparisonDailyAverage', 'dailyAverageAbsoluteChange', 'dailyAveragePercentChange'].map(key => [key, numeric(row[key])])) })),
    notes: list(rawComparison.notes).filter(value => typeof value === 'string')
  } : null
  return { cardType: 'production_daily_report_analysis', title: '生产日报分析', fields: [
    { kind: 'daily_analysis_summary', reportRunId: report.reportRunId, dateRangeLabel: report.dateRangeLabel,
      dataAsOf: report.dataAsOf, sourceLabel: '生产日报', requestedDays: aggregate.requestedDays,
      productScopeLabel: report.filtersApplied?.productScope, departmentScopeLabel: report.filtersApplied?.departmentScope,
      isEmpty: !series.length && !list(report.comparison?.metrics).length,
      currentPeriodEmpty: !series.length, comparison, truncated: aggregate.truncated === true },
    ...series.map(row => ({ kind: 'daily_analysis_series',
      ...Object.fromEntries([...textKeys, ...countKeys].map(key => [key, row[key]])),
      quantity: numeric(row.quantity), convertedTons: row.tonnageApplicable ? numeric(row.convertedTons) : null,
      tonnageApplicable: row.tonnageApplicable === true,
      points: list(row.points).map(point => ({ businessDate: point.businessDate, quantity: numeric(point.quantity),
        convertedTons: row.tonnageApplicable ? numeric(point.convertedTons) : null,
        ...Object.fromEntries(countKeys.slice(0, 4).map(key => [key, point[key]])) })) })),
    ...[...new Set([...list(report.dataQuality?.notes), ...list(report.limitations)])]
      .map(value => ({ kind: 'daily_analysis_note', value }))
  ] }
}
