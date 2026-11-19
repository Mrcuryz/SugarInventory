// Session-local review only: these objects are not a write payload or an authorization.
export const reviewBookKey = Symbol('productImportReviewBook')
export function actionFor(entry) {
  const d = entry.card.fields?.[0] || {}
  if (entry.choice === 'skip') return { label: '已忽略' }
  if (entry.choice === 'new' && (d.allowCreate || (d.allowManualCreate && entry.ack))) {
    return { label: '新增', target: d.sourceValues.productName }
  }
  const opt = Number.isInteger(entry.choice) && d.options?.[entry.choice]
  if (!opt || opt.blocked) return { label: d.statusLabel || '未选择' }
  return opt.changes?.length ? { label: '修改', target: opt.values.productName } : { label: '无须修改' }
}
export function reviewSummary(entries, total) {
  const rows = [...entries].sort((a, b) => a.rowNumber - b.rowNumber)
    .map(entry => ({ entry, ...actionFor(entry), conflicts: [] }))
  const groups = new Map()
  for (const row of rows.filter(r => r.entry.included && r.target)) {
    // Conservative name collision, not a claim that signed per-row references identify the same object.
    const key = row.target.normalize('NFC').replaceAll('（', '(').replaceAll('）', ')').trim().replace(/\s+/gu, ' ')
    const same = groups.get(key) || []
    same.push(row)
    groups.set(key, same)
  }
  for (const same of groups.values()) if (same.length > 1) {
    for (const row of same) row.conflicts = same.filter(r => r !== row).map(r => r.entry.rowNumber)
  }
  return { rows, pending: Math.max(0, total - rows.length),
    ready: rows.filter(r => r.target && r.entry.included && !r.conflicts.length).length }
}
