const CONSUMED_SELECTION_FINISH_REASONS = new Set([
  'completed',
  'clarification_required',
  'interrupt_required'
])

export const isSelectionResumeConsumed = finishReason => (
  CONSUMED_SELECTION_FINISH_REASONS.has(String(finishReason || ''))
)
