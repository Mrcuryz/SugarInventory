export const isFinishInboundExecutionPreviewCard = card => card?.cardType === 'finish_inbound_execution_preview'

export const finishInboundExecutionPreviewSummary = card => {
  if (!isFinishInboundExecutionPreviewCard(card)) return null
  return (card.fields || []).find(field => field?.kind === 'finish_inbound_execution_preview_summary') || null
}

export const finishInboundExecutionPreviewItems = card => {
  if (!isFinishInboundExecutionPreviewCard(card)) return []
  return (card.fields || []).filter(field => field?.kind === 'finish_inbound_execution_preview_item')
}

export const finishInboundExecutionControlAction = (card, enabled) => {
  if (!enabled) return null
  const summary = finishInboundExecutionPreviewSummary(card)
  if (!summary?.readyForUserConfirmation) return null
  const palletCodes = [...new Set(finishInboundExecutionPreviewItems(card)
    .map(item => String(item?.palletCode || '').trim().toUpperCase())
    .filter(Boolean))]
  if (!palletCodes.length || palletCodes.length > 20) return null
  return {
    actionKind: 'open_finish_inbound_execution_control',
    palletCodes
  }
}

export const applyFinishInboundExecutionCompletion = (card, palletCodes = [], completedAt = '') => {
  if (!isFinishInboundExecutionPreviewCard(card)) return { card, changed: false }
  const completed = new Set((palletCodes || [])
    .map(code => String(code || '').trim().toUpperCase())
    .filter(Boolean))
  if (!completed.size) return { card, changed: false }

  let changed = false
  const fields = (card.fields || []).map(field => {
    if (field?.kind !== 'finish_inbound_execution_preview_item') return field
    const palletCode = String(field.palletCode || '').trim().toUpperCase()
    if (!completed.has(palletCode)) return field
    changed = true
    return {
      ...field,
      executionCompleted: true,
      executionStatusLabel: '已入库'
    }
  })
  if (!changed) return { card, changed: false }

  return {
    changed: true,
    card: {
      ...card,
      fields: fields.map(field => field?.kind === 'finish_inbound_execution_preview_summary'
        ? {
            ...field,
            readyForUserConfirmation: false,
            previewStatusLabel: '已完成',
            executionCompleted: true,
            completedAt: completedAt || field.completedAt || ''
          }
        : field)
    }
  }
}
