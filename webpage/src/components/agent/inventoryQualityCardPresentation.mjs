export const isInventoryQualityCard = (card) => card?.cardType === 'inventory_quality'

export const inventoryQualitySummary = (card) => (card?.fields || [])
  .find(field => field?.kind === 'inventory_quality_summary') || null

export const inventoryQualityRecords = (card) => (card?.fields || [])
  .filter(field => field?.kind === 'inventory_quality_record')

export const inventoryQualityTone = (label) => {
  const text = String(label || '')
  if (text.includes('不合格') || text.includes('未达标')) return 'danger'
  if (text.includes('合格') || text.includes('符合')) return 'success'
  return 'neutral'
}
