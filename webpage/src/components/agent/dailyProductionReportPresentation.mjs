const fieldsByKind = (card, kind) => (
  Array.isArray(card?.fields)
    ? card.fields.filter(field => field?.kind === kind)
    : []
)

const nonNegativeNumber = (value) => {
  const parsed = Number(value)
  return Number.isFinite(parsed) && parsed >= 0 ? parsed : 0
}

export const isDailyProductionReportCard = (card) => (
  card?.cardType === 'daily_production_report'
)

export const dailyProductionSummary = (card) => (
  fieldsByKind(card, 'daily_production_summary')[0] || null
)

export const dailyProductionProducts = (card) => (
  fieldsByKind(card, 'daily_production_product')
)

export const dailyProductionPoints = (card) => (
  fieldsByKind(card, 'daily_production_point')
)

export const dailyProductionQualityNotes = (card) => (
  fieldsByKind(card, 'daily_production_quality_note')
    .map(field => String(field?.value || '').trim())
    .filter(Boolean)
)

export const productionQuantityText = (record) => {
  const boards = nonNegativeNumber(record?.totalBoardCount)
  const loosePieces = nonNegativeNumber(record?.loosePieceCount)
  const totalPieces = nonNegativeNumber(record?.totalPieces)
  const outputRecordCount = nonNegativeNumber(record?.outputRecordCount)
  const parts = []
  if (boards) parts.push(`${boards} 板`)
  if (loosePieces) parts.push(`${loosePieces} 件`)
  if (!parts.length && totalPieces) parts.push(`折合 ${totalPieces} 件`)
  if (!parts.length && outputRecordCount === 0) return '0 板 0 件'
  return parts.join(' ') || '未登记板件数量'
}

export const qrProgressSummary = (summary) => {
  const required = nonNegativeNumber(summary?.requiredQrCount)
  const bound = nonNegativeNumber(summary?.boundQrCount)
  const inbound = nonNegativeNumber(summary?.inboundQrCount)
  if (!required && !bound && !inbound) return ''
  return `二维码进度：应生成 ${required} 个，已绑定 ${bound} 个，已入库 ${inbound} 个`
}

export const reportDataTimeText = (summary) => {
  const value = String(summary?.dataAsOf || '').trim()
  if (!value) return ''
  return value
    .replace('T', ' ')
    .replace(/(?:[+-]\d{2}:\d{2}|Z)$/, '')
    .replace(/\.\d+$/, '')
}
