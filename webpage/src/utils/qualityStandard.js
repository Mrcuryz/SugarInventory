export const PRODUCT_TYPE_OPTIONS = [
  { value: '白冰糖', label: '白冰糖' },
  { value: '黄冰糖', label: '黄冰糖' }
]

export const QUALITY_STANDARD_STATUS_OPTIONS = [
  { value: 'ENABLED', label: '启用中' },
  { value: 'DISABLED', label: '已停用' }
]

export const FIXED_METRIC_LIBRARY = [
  { metricCode: 'color_value', metricName: '色值', unit: 'IU' },
  { metricCode: 'reducing_sugar', metricName: '还原糖分', unit: 'g/100g' },
  { metricCode: 'dry_weight_loss', metricName: '干燥失重', unit: 'g/100g' },
  { metricCode: 'conductivity_ash', metricName: '电导灰分', unit: 'g/100g' },
  { metricCode: 'sucrose', metricName: '蔗糖分', unit: 'g/100g' },
  { metricCode: 'insoluble_impurity', metricName: '不溶于水杂质', unit: 'mg/kg' },
  { metricCode: 'ph', metricName: 'pH', unit: '' }
]

export function createFixedMetricRows(items = []) {
  const itemMap = new Map((items || []).map(item => [item.metricCode, item]))
  return FIXED_METRIC_LIBRARY.map((preset, index) => {
    const current = itemMap.get(preset.metricCode) || {}
    return {
      id: current.id,
      metricCode: preset.metricCode,
      metricName: preset.metricName,
      minValue: current.minValue ?? null,
      maxValue: current.maxValue ?? null,
      unit: preset.unit,
      sortOrder: current.sortOrder ?? (index + 1) * 10,
      remark: current.remark || ''
    }
  })
}

export function formatStandardRange(row) {
  if (!row) return '-'
  const hasMin = row.minValue !== null && row.minValue !== undefined && row.minValue !== ''
  const hasMax = row.maxValue !== null && row.maxValue !== undefined && row.maxValue !== ''
  if (!hasMin && !hasMax) return '不参与判定'
  if (hasMin && hasMax) return `${row.minValue} ~ ${row.maxValue}`
  if (hasMin) return `>= ${row.minValue}`
  return `<= ${row.maxValue}`
}

export function buildStandardVersionLabel(version) {
  return `v${version || 1}`
}

export function standardStatusLabel(status) {
  return status === 'DISABLED' ? '已停用' : '启用中'
}

export function standardStatusTagType(status) {
  return status === 'DISABLED' ? 'info' : 'success'
}
