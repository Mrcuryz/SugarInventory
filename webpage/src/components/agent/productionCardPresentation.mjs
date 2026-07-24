const PRODUCTION_CARD_TYPES = new Set([
  'production_order_progress',
  'production_material_trace',
  'boiling_batch_trace'
])

export const isProductionCard = (card) => PRODUCTION_CARD_TYPES.has(card?.cardType)

export const isProductionOrderCard = (card) => card?.cardType === 'production_order_progress'

export const isProductionMaterialCard = (card) => card?.cardType === 'production_material_trace'

export const isBoilingBatchCard = (card) => card?.cardType === 'boiling_batch_trace'

const fieldsByKind = (card, kind) => (card?.fields || []).filter(field => field?.kind === kind)

export const productionOrderSummary = (card) => fieldsByKind(card, 'production_order_summary')[0] || null

export const boilingBatchSummary = (card) => fieldsByKind(card, 'boiling_batch_summary')[0] || null

export const productionOutputs = (card) => fieldsByKind(card, 'production_output')

export const productionBoilingSources = (card) => fieldsByKind(card, 'production_boiling_source')

export const productionMaterials = (card) => fieldsByKind(card, 'production_material')

export const boilingBatchUsages = (card) => fieldsByKind(card, 'boiling_batch_usage')

export const productionTraceSteps = (card) => fieldsByKind(card, 'production_trace_step')

export const productionStatusTone = (label) => {
  const value = String(label || '')
  if (value.includes('完成') || value.includes('用完') || value.includes('入库') || value.includes('已消耗')) return 'success'
  if (value.includes('进行') || value.includes('生产') || value.includes('待') || value.includes('预')) return 'pending'
  if (value.includes('取消') || value.includes('失败')) return 'muted'
  return 'unknown'
}

export const productionSummaryEntries = (summary, kind = 'order') => {
  if (!summary) return []
  const entries = kind === 'batch'
    ? [
        ['产品', summary.productLabel],
        ['煮糖日期', summary.boilingDate],
        ['登记总重量', summary.totalWeightText],
        ['已消耗', summary.consumedWeightText],
        ['已预留', summary.reservedWeightText],
        ['剩余', summary.remainingWeightText]
      ]
    : [
        ['订单类型', summary.orderTypeLabel],
        ['生产日期', summary.productionDate],
        ['班组', summary.teamName],
        ['计划用料', summary.plannedMaterialText],
        ['计划产出', summary.plannedOutputText]
      ]
  return entries.filter(([, value]) => value !== undefined && value !== null && value !== '')
}

export const qrProgressText = (record) => {
  const required = Number(record?.requiredQrCount || 0)
  const bound = Number(record?.boundQrCount || 0)
  const inbound = Number(record?.inboundQrCount || 0)
  if (!required && !bound && !inbound) return ''
  return `二维码：需求 ${required}，已绑定 ${bound}，已入库 ${inbound}`
}
