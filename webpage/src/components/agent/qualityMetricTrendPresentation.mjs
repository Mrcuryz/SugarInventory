const fieldsByKind = (card, kind) => (
  Array.isArray(card?.fields)
    ? card.fields.filter(field => field?.kind === kind)
    : []
)

const nonNegativeNumber = (value) => {
  const parsed = Number(value)
  return Number.isFinite(parsed) && parsed >= 0 ? parsed : 0
}

export const isQualityMetricTrendCard = (card) => (
  card?.cardType === 'quality_metric_trend_report'
)

export const qualityMetricTrendSummary = (card) => (
  fieldsByKind(card, 'quality_metric_trend_summary')[0] || null
)

export const qualityMetricTrendProducts = (card) => (
  fieldsByKind(card, 'quality_metric_trend_product')
)

export const qualityMetricTrendStandards = (card) => (
  fieldsByKind(card, 'quality_metric_trend_standard')
)

export const qualityMetricTrendPoints = (card) => (
  fieldsByKind(card, 'quality_metric_trend_point')
    .filter(point => nonNegativeNumber(point?.sampleCount) > 0)
)

export const qualityMetricTrendNotes = (card) => (
  fieldsByKind(card, 'quality_metric_trend_quality_note')
    .map(field => String(field?.value || '').trim())
    .filter(Boolean)
)

export const metricValueText = (value, unit = '') => {
  if (value === null || value === undefined || value === '') return '-'
  const parsed = Number(value)
  if (!Number.isFinite(parsed)) return '-'
  const suffix = String(unit || '').trim()
  return `${parsed}${suffix ? ` ${suffix}` : ''}`
}

export const metricWithinRateText = (record) => {
  const comparableCount = record?.comparableStandardCount ?? record?.sampleCount
  if (nonNegativeNumber(comparableCount) === 0) {
    return '无可比较标准'
  }
  const parsed = Number(record?.withinStandardRatePercent)
  return Number.isFinite(parsed) && parsed >= 0 && parsed <= 100
    ? `${parsed}%`
    : '无可计算样本'
}
