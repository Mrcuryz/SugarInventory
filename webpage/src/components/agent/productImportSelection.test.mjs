import test from 'node:test'
import assert from 'node:assert/strict'
import { actionFor, reviewSummary } from './productImportSelection.mjs'

function entry(rowNumber, name, extra = {}) {
  return { rowNumber, included: true, choice: 0, card: { fields: [{ sourceValues: { productName: '另一种叫法' },
    options: [{ values: { productName: name }, changes: [{ field: 'canStack' }], ...extra }] }] } }
}
test('同名目标按整张表而非当前页提示冲突；取消一项后恢复', () => {
  const a = entry(88, '黄冰糖(袋)'), b = entry(3, '黄冰糖（袋）')
  const summary = reviewSummary([a, b], 100)
  assert.equal(summary.pending, 98)
  assert.equal(summary.ready, 0)
  assert.deepEqual(summary.rows.map(r => r.conflicts), [[88], [3]])
  a.included = false
  assert.equal(reviewSummary([a, b], 100).ready, 1)
})
test('无差异、已有库存不可改、未作选择和跳过都不算可应用', () => {
  const rows = [entry(2, 'A', { changes: [] }), entry(3, 'B', { blocked: true }), entry(4, 'C'), entry(5, 'D')]
  rows[2].choice = 'skip'; rows[3].choice = null
  assert.equal(reviewSummary(rows, 4).ready, 0)
})
test('不完整候选范围需要用户另外确认才有新增意向', () => {
  const r = entry(2, 'A')
  r.choice = 'new'; r.card.fields[0].allowManualCreate = true
  assert.equal(actionFor(r).target, undefined)
  r.ack = true
  assert.equal(actionFor(r).label, '新增')
})
