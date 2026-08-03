import test from 'node:test'
import assert from 'node:assert/strict'

import {
  isTodayOperationsOverviewCard,
  todayOperationsOverviewNotes,
  todayOperationsOverviewScopeNote,
  todayOperationsOverviewSections,
  todayOperationsOverviewSummary
} from './todayOperationsOverviewPresentation.mjs'

const card = {
  cardType: 'today_operations_overview_report',
  fields: [
    { kind: 'today_operations_overview_summary', reportRunId: 'rr_today', value: '今日概览' },
    {
      kind: 'today_operations_production', label: '今日稳定登记产出', value: '1980 kg',
      outputRecordCount: 1, productionOrderCount: 1, totalPieces: 80
    },
    {
      kind: 'today_operations_quality', label: '今日已登记化验', value: '3 条',
      judgedRecordCount: 2, noStandardCount: 1, multipleCandidatesCount: 0
    },
    {
      kind: 'today_operations_flow', label: '今日确认领用与稳定产出', value: '领用 2000 kg · 产出 1980 kg',
      materialInputRecordCount: 2, stableOutputRecordCount: 1
    },
    {
      kind: 'today_operations_inventory', label: '当前库存水平', value: '13 板 30 件',
      productCount: 5, warehouseCount: 3, palletCount: 12, totalWeightText: '13750 kg'
    },
    {
      kind: 'today_operations_tasks', label: '托盘任务', value: '今日创建 4 条 · 当前待处理 7 条',
      completedTaskCount: 2, inProgressTaskCount: 1, canceledTaskCount: 1
    },
    { kind: 'today_operations_quality_note', value: '化验：1 条记录无适用标准。' },
    { kind: 'today_operations_scope_note', value: '今日事实与当前快照分开解释。' }
  ]
}

test('builds readable today overview sections without backend enums', () => {
  assert.equal(isTodayOperationsOverviewCard(card), true)
  assert.equal(todayOperationsOverviewSummary(card).reportRunId, 'rr_today')
  const sections = todayOperationsOverviewSections(card)
  assert.deepEqual(sections.map(item => item.key), [
    'production', 'quality', 'flow', 'inventory', 'tasks'
  ])
  assert.ok(sections.find(item => item.key === 'quality').details.includes('无适用标准 1 条'))
  assert.ok(sections.find(item => item.key === 'flow').details.some(item => item.includes('不直接相除')))
  assert.equal(todayOperationsOverviewNotes(card).length, 1)
  assert.equal(todayOperationsOverviewScopeNote(card).value, '今日事实与当前快照分开解释。')
})

test('ignores malformed cards safely', () => {
  assert.equal(isTodayOperationsOverviewCard({}), false)
  assert.deepEqual(todayOperationsOverviewSections({ fields: [null, 'bad'] }), [])
  assert.equal(todayOperationsOverviewSummary(null), null)
})
