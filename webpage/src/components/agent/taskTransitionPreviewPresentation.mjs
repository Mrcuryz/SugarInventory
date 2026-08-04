export const isTaskTransitionPreviewCard = card => card?.cardType === 'task_transition_preview'

export const taskTransitionPreviewSummary = card => (card?.fields || []).find(
  field => field?.kind === 'task_transition_preview_summary'
) || null

export const taskTransitionPreviewTasks = card => (card?.fields || []).filter(
  field => field?.kind === 'task_transition_preview_task'
)

export const taskTransitionPreviewDialogAction = card => {
  const summary = taskTransitionPreviewSummary(card)
  const supportedActions = {
    confirmIn: '成品入库',
    finishOutConfirm: '成品出库'
  }
  const expectedGroupLabel = supportedActions[summary?.batchAction]
  const palletCodes = [...new Set((summary?.palletCodes || []).map(
    code => String(code || '').trim().toUpperCase()
  ).filter(Boolean))]
  if (
    !summary?.canOpenBusinessDialog
    || !expectedGroupLabel
    || summary?.taskGroupLabel !== expectedGroupLabel
    || !palletCodes.length
  ) {
    return null
  }
  return {
    actionKind: 'open_task_batch',
    batchAction: summary.batchAction,
    taskGroupLabel: expectedGroupLabel,
    palletCodes
  }
}
