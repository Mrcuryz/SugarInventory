import assert from 'node:assert/strict'
import test from 'node:test'

import {
  applyTaskBatchCompletion,
  isConfirmedTaskRecord,
  isTaskCard,
  isTaskDetailCard,
  isTaskListCard,
  isPendingTaskRecord,
  taskDetailEntries,
  taskCardStatusSummary,
  taskGroupStatusSummary,
  taskGroups,
  taskRecords,
  taskSelectionRequirement,
  taskStatusTone
} from './taskCardPresentation.mjs'

test('recognizes task list and detail cards', () => {
  assert.equal(isTaskCard({ cardType: 'pallet_tasks' }), true)
  assert.equal(isTaskListCard({ cardType: 'pallet_tasks' }), true)
  assert.equal(isTaskDetailCard({ cardType: 'pallet_task_detail' }), true)
  assert.equal(isTaskCard({ cardType: 'assay_report' }), false)
})

test('keeps only controlled task rows and maps status tones', () => {
  const card = {
    fields: [
      { kind: 'pallet_task', palletCode: 'P001' },
      { kind: 'risk_summary', value: 'ignored' }
    ]
  }
  assert.deepEqual(taskRecords(card), [{ kind: 'pallet_task', palletCode: 'P001' }])
  assert.equal(taskStatusTone('待处理'), 'pending')
  assert.equal(taskStatusTone('已确认'), 'success')
  assert.equal(taskStatusTone('已取消'), 'muted')
  assert.equal(isConfirmedTaskRecord({ taskStatusLabel: '已确认' }), true)
})

test('detail entries omit empty values and never invent fields', () => {
  assert.deepEqual(
    taskDetailEntries({
      targetLocationLabel: '2号库位 左侧',
      productionDate: '2026-07-20',
      operationBatchLabel: ''
    }),
    [
      ['目标位置', '2号库位 左侧'],
      ['生产日期', '2026-07-20']
    ]
  )
})

test('groups task cards by actionable business type', () => {
  const groups = taskGroups({
    fields: [
      {
        kind: 'pallet_task',
        palletCode: 'P-IN',
        taskTypeLabel: '成品入库',
        taskStatusLabel: '待处理'
      },
      {
        kind: 'pallet_task',
        palletCode: 'P-OUT-1',
        taskTypeLabel: '出库',
        productStatusLabel: '成品',
        businessSceneLabel: '成品出库',
        taskStatusLabel: '待处理'
      },
      {
        kind: 'pallet_task',
        palletCode: 'P-OUT-2',
        taskTypeLabel: '出库',
        productStatusLabel: '半成品',
        businessSceneLabel: '半成品直接出库',
        taskStatusLabel: '已确认'
      }
    ]
  })

  assert.deepEqual(groups.map(group => [group.label, group.batchAction, group.records.length]), [
    ['成品入库', 'confirmIn', 1],
    ['成品出库', 'finishOutConfirm', 1],
    ['半成品出库', 'semiOutConfirm', 1]
  ])
  assert.equal(groups[0].records[0].selectable, true)
  assert.equal(groups[2].records[0].selectable, false)
})

test('summarizes current task state while retaining the original result rows', () => {
  const card = {
    cardType: 'pallet_tasks',
    title: '任务状态（已更新）· 待处理 8 条，已确认 2 条',
    fields: [
      ...Array.from({ length: 8 }, (_, index) => ({
        kind: 'pallet_task',
        palletCode: `P-PENDING-${index}`,
        taskTypeLabel: '成品入库',
        taskStatusLabel: '待处理'
      })),
      ...Array.from({ length: 2 }, (_, index) => ({
        kind: 'pallet_task',
        palletCode: `P-CONFIRMED-${index}`,
        taskTypeLabel: '成品入库',
        taskStatusLabel: '已确认'
      }))
    ]
  }

  assert.deepEqual(taskCardStatusSummary(card), {
    totalCount: 10,
    pendingCount: 8,
    confirmedCount: 2,
    otherCount: 0,
    updated: true
  })
  const group = taskGroups(card)[0]
  assert.deepEqual(taskGroupStatusSummary(group), {
    totalCount: 10,
    pendingCount: 8,
    confirmedCount: 2,
    otherCount: 0,
    selectableCount: 8
  })
  assert.equal(group.records.at(-1).taskStatusLabel, '已确认')
})

test('does not offer processing for legacy or unknown task groups', () => {
  const groups = taskGroups({
    fields: [
      {
        kind: 'pallet_task',
        palletCode: 'P-LEGACY',
        taskTypeLabel: '出库',
        businessSceneLabel: '历史生产占用',
        taskStatusLabel: '待处理'
      }
    ]
  })

  assert.equal(groups[0].label, '历史生产占用')
  assert.equal(groups[0].batchAction, '')
  assert.equal(groups[0].records[0].selectable, false)
  assert.equal(isPendingTaskRecord(groups[0].records[0]), true)
})

test('marks completed batch rows confirmed and removes them from selectable tasks', () => {
  const original = {
    cardType: 'pallet_tasks',
    title: '当前待处理任务 · 共 3 条',
    fields: [
      { kind: 'pallet_task', palletCode: 'P001', taskTypeLabel: '成品入库', taskStatusLabel: '待处理' },
      { kind: 'pallet_task', palletCode: 'P002', taskTypeLabel: '成品入库', taskStatusLabel: '待处理' },
      { kind: 'pallet_task', palletCode: 'P003', taskTypeLabel: '成品出库', taskStatusLabel: '待处理' }
    ]
  }

  const result = applyTaskBatchCompletion(original, ['p001', 'P002'])
  assert.equal(result.changedCount, 2)
  assert.equal(result.card.title, '任务状态（已更新）· 待处理 1 条，已确认 2 条')
  assert.deepEqual(result.card.fields.map(field => field.taskStatusLabel), ['已确认', '已确认', '待处理'])
  assert.deepEqual(taskGroups(result.card).map(group => group.records.map(record => record.selectable)), [
    [false, false],
    [true]
  ])
  assert.equal(original.fields[0].taskStatusLabel, '待处理')
})

test('reads a bounded guided finish-inbound QR selection requirement', () => {
  assert.deepEqual(taskSelectionRequirement({
    fields: [{
      kind: 'finish_inbound_guided_selection',
      requestedPalletCount: 2,
      availablePalletCount: 3,
      productLabel: '黄冰糖（袋）25kg/件 40件/板',
      warehouseName: '3号库位',
      defaultSide: '左',
      taskGroupKey: 'finish_in',
      canSelectRequestedCount: true
    }]
  }), {
    requestedPalletCount: 2,
    availablePalletCount: 3,
    productLabel: '黄冰糖（袋）25kg/件 40件/板',
    warehouseName: '3号库位',
    defaultSide: '左',
    taskGroupKey: 'finish_in',
    canSelectRequestedCount: true
  })
  assert.equal(taskSelectionRequirement({ fields: [{
    kind: 'finish_inbound_guided_selection',
    requestedPalletCount: 21
  }] }), null)
})
