const positiveInteger = value => {
  const parsed = Number(value)
  return Number.isInteger(parsed) && parsed > 0 ? parsed : null
}

export const selectedWarehouseIds = rows => [...new Set(
  (rows || [])
    .map(row => positiveInteger(row?.id ?? row?.warehouseId))
    .filter(Boolean)
)]

export const buildWarehouseLocationLabels = locations => (locations || [])
  .map(location => ({
    code: String(location?.code || '').trim(),
    title: String(location?.warehouseName || '').trim()
  }))
  .filter(label => label.code && label.title)

export const capacityText = warehouse => {
  const current = Number(warehouse?.curCapacity)
  const maximum = Number(warehouse?.maxCapacity)
  const safeCurrent = Number.isFinite(current) && current >= 0 ? current : 0
  if (!Number.isFinite(maximum) || maximum <= 0) {
    return `${safeCurrent} 板`
  }
  return `${safeCurrent} / ${maximum} 板`
}
