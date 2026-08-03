const fieldsByKind = (card, kind) => (
  Array.isArray(card?.fields)
    ? card.fields.filter(field => field?.kind === kind)
    : []
)

const nonNegativeNumber = (value) => {
  const parsed = Number(value)
  return Number.isFinite(parsed) && parsed >= 0 ? parsed : 0
}

export const isQualityAssayTrendCard = (card) => (
  card?.cardType === 'quality_assay_trend_report'
)

export const qualityAssayTrendSummary = (card) => (
  fieldsByKind(card, 'quality_assay_trend_summary')[0] || null
)

export const qualityAssayTrendProducts = (card) => (
  fieldsByKind(card, 'quality_assay_trend_product')
)

export const qualityAssayTrendStandards = (card) => (
  fieldsByKind(card, 'quality_assay_trend_standard')
)

export const qualityAssayTrendPoints = (card) => (
  fieldsByKind(card, 'quality_assay_trend_point')
    .filter(point => nonNegativeNumber(point?.assayRecordCount) > 0)
)

export const qualityAssayTrendNotes = (card) => (
  fieldsByKind(card, 'quality_assay_trend_quality_note')
    .map(field => String(field?.value || '').trim())
    .filter(Boolean)
)

export const qualityPassRateText = (record) => {
  const raw = record?.passRatePercent
  if (raw === null || raw === undefined || raw === '') return '无可计算样本'
  const parsed = Number(raw)
  return Number.isFinite(parsed) && parsed >= 0 && parsed <= 100
    ? `${parsed}%`
    : '无可计算样本'
}

export const qualityJudgementSummary = (record) => {
  const parts = [
    `合格 ${nonNegativeNumber(record?.passCount)} 条`,
    `不合格 ${nonNegativeNumber(record?.failCount)} 条`
  ]
  const noStandard = nonNegativeNumber(record?.noStandardCount)
  const multiple = nonNegativeNumber(record?.multipleCandidatesCount)
  if (noStandard) parts.push(`无标准 ${noStandard} 条`)
  if (multiple) parts.push(`标准多候选 ${multiple} 条`)
  return parts.join(' · ')
}
