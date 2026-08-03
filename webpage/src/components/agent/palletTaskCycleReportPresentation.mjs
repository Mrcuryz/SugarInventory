const fieldsByKind = (card, kind) => (
  Array.isArray(card?.fields)
    ? card.fields.filter(field => field?.kind === kind)
    : []
)

export const isPalletTaskCycleReportCard = (card) => (
  card?.cardType === 'pallet_task_cycle_report'
)

export const palletTaskCycleSummary = (card) => (
  fieldsByKind(card, 'pallet_task_cycle_summary')[0] || null
)

export const palletTaskCycleDailyPoints = (card) => (
  fieldsByKind(card, 'pallet_task_cycle_daily')
)

export const palletTaskCycleTypes = (card) => (
  fieldsByKind(card, 'pallet_task_cycle_type')
)

export const palletTaskPendingItems = (card) => (
  fieldsByKind(card, 'pallet_task_cycle_pending')
)

export const palletTaskCycleQualityNotes = (card) => (
  fieldsByKind(card, 'pallet_task_cycle_quality_note')
    .map(field => String(field?.value || '').trim())
    .filter(Boolean)
)

export const palletTaskCycleScopeNote = (card) => (
  String(fieldsByKind(card, 'pallet_task_cycle_scope_note')[0]?.value || '').trim()
)

export const taskDurationText = (value) => {
  if (value === null || value === undefined || value === '') return '暂无有效样本'
  const seconds = Number(value)
  if (!Number.isFinite(seconds) || seconds < 0) return '暂无有效样本'
  if (seconds < 60) return `${Math.round(seconds)} 秒`
  if (seconds < 3600) return `${(seconds / 60).toFixed(seconds < 600 ? 1 : 0)} 分钟`
  if (seconds < 86400) return `${(seconds / 3600).toFixed(seconds < 36000 ? 1 : 0)} 小时`
  return `${(seconds / 86400).toFixed(1)} 天`
}
