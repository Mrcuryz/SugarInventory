export const cleanOptionLabel = (label) => String(label || '')
  .replace(/\s*[(（]#\d+[)）]\s*/g, ' ')
  .replace(/\s+/g, ' ')
  .trim()

export const optionTypeLabel = (optionType) => {
  const labels = {
    PRODUCT_TYPE_GROUP: '产品大类',
    EXACT_PRODUCT_NAME_GROUP: '产品名称组',
    SINGLE_PRODUCT: '具体产品',
    SINGLE_WAREHOUSE: '具体库位'
  }
  return labels[optionType] || '候选项'
}

export const selectedOptionKindLabel = (optionType) => {
  const labels = {
    SINGLE_PRODUCT: '产品',
    EXACT_PRODUCT_NAME_GROUP: '产品',
    PRODUCT_TYPE_GROUP: '产品',
    SINGLE_WAREHOUSE: '库位',
    PRODUCTION_ORDER: '生产订单'
  }
  return labels[optionType] || '选项'
}

export const optionHint = (option) => option.supported === false
  ? '暂不支持直接查询'
  : '点击选择并继续查询'

export const optionCardClass = (option) => ({
  disabled: option.supported === false,
  actionable: option.supported !== false
})

export const visibleCards = (item) => (item?.cards || []).filter(card => card?.cardType !== 'candidate_selection')
export const isDistributionCard = (card) => card?.cardType === 'inventory_distribution'
export const hasDistributionCard = (item) => visibleCards(item).some(card => isDistributionCard(card))
export const hasWideBusinessCard = (item) => visibleCards(item)
  .some(card => [
    'inventory_distribution',
    'assay_report',
    'assay_history',
    'pallet_tasks',
    'pallet_task_detail'
  ].includes(card?.cardType))
export const distributionRows = (card) => (card?.fields || []).filter(field => field?.kind !== 'risk_summary')
export const distributionRiskSummary = (card) => (card?.fields || []).find(field => field?.kind === 'risk_summary')?.value
