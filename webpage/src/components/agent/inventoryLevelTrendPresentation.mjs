const fieldsByKind = (card, kind) => (
  Array.isArray(card?.fields)
    ? card.fields.filter(field => field?.kind === kind)
    : []
)

const number = (value) => {
  const parsed = Number(value)
  return Number.isFinite(parsed) ? parsed : 0
}

export const isInventoryLevelTrendCard = (card) => (
  card?.cardType === 'inventory_level_trend_report'
)

export const inventoryLevelTrendSummary = (card) => (
  fieldsByKind(card, 'inventory_level_trend_summary')[0] || null
)

export const inventoryLevelTrendDailyPoints = (card) => (
  fieldsByKind(card, 'inventory_level_trend_daily')
)

export const inventoryLevelTrendProducts = (card) => (
  fieldsByKind(card, 'inventory_level_trend_product')
)

export const inventoryLevelTrendNotes = (card) => (
  fieldsByKind(card, 'inventory_level_trend_quality_note')
    .map(field => String(field?.value || '').trim())
    .filter(Boolean)
)

export const inventoryLevelTrendScopeNote = (card) => (
  String(fieldsByKind(card, 'inventory_level_trend_scope_note')[0]?.value || '').trim()
)

export const signedInventoryChangeText = (value, unit = '件') => {
  const parsed = number(value)
  const prefix = parsed > 0 ? '+' : ''
  return `${prefix}${parsed} ${unit}`
}

export const inventoryWeightText = (value) => `${number(value)} kg`

export const inventoryTrendDirection = (value) => {
  const parsed = number(value)
  if (parsed > 0) return 'increase'
  if (parsed < 0) return 'decrease'
  return 'unchanged'
}
