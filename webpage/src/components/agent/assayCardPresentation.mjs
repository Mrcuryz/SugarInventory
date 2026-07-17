export const isAssayReportCard = (card) => card?.cardType === 'assay_report'
export const isAssayHistoryCard = (card) => card?.cardType === 'assay_history'
export const isAssayCard = (card) => isAssayReportCard(card) || isAssayHistoryCard(card)

export const assaySummary = (card) => (card?.fields || [])
  .find(field => field?.kind === 'assay_summary') || null

export const assayMetrics = (card) => (card?.fields || [])
  .filter(field => field?.kind === 'assay_metric')

export const assayNotes = (card) => (card?.fields || [])
  .filter(field => field?.kind === 'assay_note')

export const assayHistoryRecords = (card) => (card?.fields || [])
  .filter(field => field?.kind === 'assay_history_record')

export const assayResultTone = (label) => {
  const text = String(label || '')
  if (text === '合格') return 'pass'
  if (text === '不合格') return 'fail'
  if (text.includes('待人工') || text.includes('多候选')) return 'review'
  return 'unknown'
}
