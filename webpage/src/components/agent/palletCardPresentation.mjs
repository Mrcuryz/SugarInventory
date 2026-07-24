const PALLET_CARD_TYPES = new Set(['pallet_status', 'pallet_flow_history'])

const fieldsByKind = (card, kind) => (card?.fields || []).filter(field => field?.kind === kind)

export const isPalletCard = (card) => PALLET_CARD_TYPES.has(card?.cardType)

export const isPalletStatusCard = (card) => card?.cardType === 'pallet_status'

export const isPalletFlowCard = (card) => card?.cardType === 'pallet_flow_history'

export const palletStatusSummary = (card) => fieldsByKind(card, 'pallet_status_summary')[0] || null

export const palletFlowSummary = (card) => fieldsByKind(card, 'pallet_flow_summary')[0] || null

export const palletFlowEvents = (card) => fieldsByKind(card, 'pallet_flow_event')

export const palletRisks = (card) => fieldsByKind(card, 'pallet_risk')

export const palletStatusTone = (label) => {
  const value = String(label || '')
  if (value.includes('在库') || value.includes('空闲')) return 'success'
  if (value.includes('待') || value.includes('预留')) return 'pending'
  if (value.includes('作废') || value.includes('异常')) return 'danger'
  return 'muted'
}

export const palletSummaryEntries = (summary) => [
  ['产品', summary?.productLabel],
  ['当前位置', summary?.warehouseLabel],
  ['数量', summary?.quantityText],
  ['生产日期', summary?.productionDate],
  ['化验', summary?.assaySummary]
].filter(([, value]) => value !== undefined && value !== null && String(value).trim() !== '')

export const palletEventRoute = (event) => {
  const source = String(event?.fromWarehouseLabel || '').trim()
  const target = String(event?.toWarehouseLabel || '').trim()
  if (source && target) return `${source} → ${target}`
  if (target) return `到 ${target}`
  if (source) return `从 ${source}`
  return ''
}
