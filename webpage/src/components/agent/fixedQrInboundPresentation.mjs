const SUMMARY_KIND = 'fixed_qr_inbound_selection_summary'
const CANDIDATE_KIND = 'fixed_qr_inbound_candidate'

export const isFixedQrInboundSelectionCard = card => card?.cardType === 'fixed_qr_inbound_selection'

export const fixedQrInboundSummary = card => {
  if (!isFixedQrInboundSelectionCard(card)) return null
  const field = (card.fields || []).find(item => item?.kind === SUMMARY_KIND)
  const requestedPalletCount = Number(field?.requestedPalletCount || 0)
  if (!field || !Number.isInteger(requestedPalletCount) || requestedPalletCount < 1 || requestedPalletCount > 20) {
    return null
  }
  return {
    requestedPalletCount,
    availablePalletCount: Math.max(0, Number(field.availablePalletCount || 0)),
    productLabel: String(field.productLabel || '所选产品'),
    warehouseName: String(field.warehouseName || '所选库位'),
    defaultSide: ['左', '右'].includes(field.defaultSide) ? field.defaultSide : '左',
    operationModeLabel: String(field.operationModeLabel || '使用空闲固定二维码新建任务'),
    canSelectRequestedCount: field.canSelectRequestedCount === true,
    creationCompleted: field.creationCompleted === true
  }
}

export const applyFixedQrTaskCreation = (card, createdCodes) => {
  if (!isFixedQrInboundSelectionCard(card)) return { card, changed: false }
  const codeSet = new Set((createdCodes || []).map(code => String(code || '').trim().toUpperCase()).filter(Boolean))
  if (!codeSet.size) return { card, changed: false }
  let changed = false
  const fields = (card.fields || []).map(field => {
    if (field?.kind !== CANDIDATE_KIND || !codeSet.has(String(field.code || '').trim().toUpperCase())) return field
    changed = true
    return { ...field, statusLabel: '已创建待入库任务', selectable: false }
  })
  if (!changed) return { card, changed: false }
  return {
    changed: true,
    card: {
      ...card,
      fields: fields.map(field => field?.kind === SUMMARY_KIND
        ? { ...field, creationCompleted: true, canSelectRequestedCount: false }
        : field)
    }
  }
}

export const fixedQrInboundCandidates = card => {
  if (!isFixedQrInboundSelectionCard(card)) return []
  return (card.fields || [])
    .filter(item => item?.kind === CANDIDATE_KIND)
    .map(item => ({
      code: String(item.code || '').trim().toUpperCase(),
      productLabel: String(item.fixedProductName || item.value || '产品未标明'),
      statusLabel: String(item.statusLabel || '状态未标明'),
      selectable: item.selectable === true,
      updatedAt: String(item.updatedAt || '')
    }))
    .filter(item => item.code)
}

export const fixedQrTaskCreationAction = (card, selectedCodes) => {
  const summary = fixedQrInboundSummary(card)
  if (!summary || !summary.canSelectRequestedCount) return null
  const candidates = new Map(fixedQrInboundCandidates(card).map(item => [item.code, item]))
  const codes = [...new Set((selectedCodes || [])
    .map(code => String(code || '').trim().toUpperCase())
    .filter(code => candidates.get(code)?.selectable))]
  if (codes.length !== summary.requestedPalletCount) return null
  return {
    actionKind: 'open_fixed_qr_task_creation',
    palletCodes: codes,
    productLabel: summary.productLabel,
    defaultWarehouseName: summary.warehouseName,
    defaultSide: summary.defaultSide,
    requestedPalletCount: summary.requestedPalletCount
  }
}

export const fixedQrCreatedTaskPreviewRequest = ({ palletCodes, warehouseName = '' } = {}) => {
  const codes = [...new Set((palletCodes || [])
    .map(code => String(code || '').trim().toUpperCase())
    .filter(Boolean))]
  if (!codes.length || codes.length > 20) return null
  const target = String(warehouseName || '').trim()
  return {
    message: `请预览以下成品入库待处理任务：${codes.join('、')}${target ? `。本次目标入库库位：${target}` : ''}`
  }
}
