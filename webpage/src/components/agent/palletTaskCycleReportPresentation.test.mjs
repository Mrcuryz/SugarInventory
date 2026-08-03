import assert from 'node:assert/strict'
import test from 'node:test'

import {
  isPalletTaskCycleReportCard,
  palletTaskCycleDailyPoints,
  palletTaskCycleQualityNotes,
  palletTaskCycleScopeNote,
  palletTaskCycleSummary,
  palletTaskCycleTypes,
  palletTaskPendingItems,
  taskDurationText
} from './palletTaskCycleReportPresentation.mjs'

const card = {
  cardType: 'pallet_task_cycle_report',
  fields: [
    { kind: 'pallet_task_cycle_summary', cohortTaskCount: 28 },
    { kind: 'pallet_task_cycle_daily', businessDate: '2026-06-30' },
    { kind: 'pallet_task_cycle_type', taskTypeLabel: '成品入库任务' },
    { kind: 'pallet_task_cycle_pending', palletCode: 'BT001' },
    { kind: 'pallet_task_cycle_quality_note', value: '24 条任务没有操作批次号' },
    { kind: 'pallet_task_cycle_scope_note', value: '不代表员工绩效。' }
  ]
}

test('recognizes and separates pallet task cycle fields', () => {
  assert.equal(isPalletTaskCycleReportCard(card), true)
  assert.equal(isPalletTaskCycleReportCard({ cardType: 'daily_production_report' }), false)
  assert.equal(palletTaskCycleSummary(card)?.cohortTaskCount, 28)
  assert.equal(palletTaskCycleDailyPoints(card).length, 1)
  assert.equal(palletTaskCycleTypes(card).length, 1)
  assert.equal(palletTaskPendingItems(card).length, 1)
  assert.deepEqual(palletTaskCycleQualityNotes(card), ['24 条任务没有操作批次号'])
  assert.equal(palletTaskCycleScopeNote(card), '不代表员工绩效。')
})

test('formats task duration without converting missing samples to zero', () => {
  assert.equal(taskDurationText(null), '暂无有效样本')
  assert.equal(taskDurationText(48), '48 秒')
  assert.equal(taskDurationText(180), '3.0 分钟')
  assert.equal(taskDurationText(7200), '2.0 小时')
  assert.equal(taskDurationText(172800), '2.0 天')
  assert.equal(taskDurationText(-1), '暂无有效样本')
})
