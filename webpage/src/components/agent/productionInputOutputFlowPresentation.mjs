const fieldsByKind = (card, kind) => (
  Array.isArray(card?.fields)
    ? card.fields.filter(field => field?.kind === kind)
    : []
)

const safeNumberText = (value) => {
  const parsed = Number(value)
  if (!Number.isFinite(parsed) || parsed < 0) return '0'
  return Number.isInteger(parsed) ? String(parsed) : String(parsed)
}

export const isProductionInputOutputFlowCard = (card) => (
  card?.cardType === 'production_input_output_flow_report'
)

export const productionFlowSummary = (card) => (
  fieldsByKind(card, 'production_input_output_flow_summary')[0] || null
)

export const productionFlowDailyPoints = (card) => (
  fieldsByKind(card, 'production_input_output_flow_daily')
)

export const productionFlowOrders = (card) => (
  fieldsByKind(card, 'production_input_output_flow_order')
)

export const productionFlowQualityNotes = (card) => (
  fieldsByKind(card, 'production_input_output_flow_quality_note')
    .map(field => String(field?.value || '').trim())
    .filter(Boolean)
)

export const productionFlowScopeNote = (card) => (
  String(
    fieldsByKind(card, 'production_input_output_flow_scope_note')[0]?.value || ''
  ).trim()
)

export const productionFlowWeightText = (value) => `${safeNumberText(value)} kg`

export const productionFlowCoverageText = (summary) => {
  const completed = Number(summary?.completedOrderCount || 0)
  const withInput = Number(summary?.completedOrdersWithInputCount || 0)
  const withOutput = Number(summary?.completedOrdersWithStableOutputCount || 0)
  if (!completed) return '当前范围没有已完成订单'
  return `已完成订单 ${completed} 个；有输入 ${withInput} 个，有稳定产出 ${withOutput} 个`
}
