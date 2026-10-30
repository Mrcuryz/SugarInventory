import { isSavedAssayResult } from './assayEntryResult.mjs'
const metrics = ['colorValue', 'reducingSugar', 'dryWeight', 'conductivityAsh', 'sucrose', 'insolubleImpurity', 'phValue']
export const assayEntrySummary = card => card?.fields?.find(row => row.kind === 'assay_entry_preview_summary') || {}
export const assayEntryControl = card => card?.fields?.find(row => row.kind === 'assay_entry_action') || null
export const assayEntryInput = card => {
  const source = assayEntryControl(card)?.entryInput
  if (!source || Boolean(source.productRef) === Boolean(source.groupRef)) return null
  const referenceField = source.groupRef ? 'groupRef' : 'productRef'
  if (!/^aer_[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+$/.test(source[referenceField] || '') || !/^\d{4}-\d{2}-\d{2}$/.test(source.values?.sampleDate || '')) return null
  const input = { sampleDate: source.values.sampleDate }
  let provided = false
  for (const key of metrics) {
    const value = source.values[key]
    if (value == null) continue
    if (typeof value !== 'number' || !Number.isFinite(value)) return null
    input[key] = value
    provided = true
  }
  return provided ? { [referenceField]: source[referenceField], values: input } : null
}
export const hasAssayEntryPermission = codes => Array.isArray(codes) && codes.includes('assay:view') && (codes.includes('assay:create') || codes.includes('quality:test'))
export const assayEntryAction = (card, allowed) => {
  const control = assayEntryControl(card)
  if (!allowed || !control || control.result?.saved || control.invalidated || !assayEntryInput(card)) return null
  return { actionKind: 'open_assay_entry_confirmation', card }
}
export const applyAssayEntryResult = (card, result) => {
  const control = assayEntryControl(card)
  if (!control || !isSavedAssayResult(result, assayEntrySummary(card))) return false
  control.result = result
  const summary = assayEntrySummary(card)
  Object.assign(summary, result.details || {}, { saved: true, recordVersion: result.version, savedAt: result.savedAt })
  return true
}
export const previewAsCard = data => ({ cardType: 'assay_entry_preview', fields: [{ kind: 'assay_entry_preview_summary', ...data }] })

export const invalidateAssayEntryCards = async (messages, api) => {
  for (const message of messages) for (const card of message.cards || []) {
    const control = assayEntryControl(card)
    if (!control || control.invalidated || control.result?.saved) continue
    if (control.preview?.previewRef) {
      const result = await api.result(control.preview.previewRef)
      if (result?.saved) { if (!applyAssayEntryResult(card, result)) throw new Error('保存结果不完整'); continue }
      if (!['已取消', '已失效', '已过期'].includes(result?.statusLabel)) await api.cancel(control.preview.previewRef)
    }
    control.invalidated = true
  }
}
