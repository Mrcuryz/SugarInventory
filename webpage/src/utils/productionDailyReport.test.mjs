import test from 'node:test'
import assert from 'node:assert/strict'

import {
  PRODUCTION_PRODUCT_UNIT,
  productionDailyCalendarRange,
  productionDailyDateState,
  reorderProductLines
} from './productionDailyReport.js'

test('生产日报产品单位固定为件', () => {
  assert.equal(PRODUCTION_PRODUCT_UNIT, '件')
})

test('拖拽排序后重建连续的显示顺序', () => {
  const rows = [
    { id: 1, displayOrder: 1 },
    { id: 2, displayOrder: 2 },
    { id: 3, displayOrder: 3 }
  ]

  assert.equal(reorderProductLines(rows, 0, 2), true)
  assert.deepEqual(rows.map(row => row.id), [2, 3, 1])
  assert.deepEqual(rows.map(row => row.displayOrder), [1, 2, 3])
})

test('无效拖拽不修改原顺序', () => {
  const rows = [{ id: 1, displayOrder: 1 }]

  assert.equal(reorderProductLines(rows, 0, 0), false)
  assert.equal(reorderProductLines(rows, 0, 2), false)
  assert.deepEqual(rows, [{ id: 1, displayOrder: 1 }])
})

test('日报日期状态按选中、未来、提交、草稿和未填写排序', () => {
  const today = '2026-08-27'
  assert.equal(productionDailyDateState({
    date: '2026-09-01', selectedDate: '2026-09-01', today, status: null
  }), 'selected')
  assert.equal(productionDailyDateState({
    date: '2026-09-01', selectedDate: '2026-08-20', today, status: null
  }), 'future')
  assert.equal(productionDailyDateState({
    date: '2026-08-26', selectedDate: '2026-08-20', today, status: 'SUBMITTED'
  }), 'submitted')
  assert.equal(productionDailyDateState({
    date: '2026-08-25', selectedDate: '2026-08-20', today, status: 'DRAFT'
  }), 'draft')
  assert.equal(productionDailyDateState({
    date: '2026-08-24', selectedDate: '2026-08-20', today, status: null
  }), 'unfilled')
})

test('日历状态查询覆盖目标月份前后相邻日期', () => {
  assert.deepEqual(productionDailyCalendarRange('2026-08-20'), {
    startDate: '2026-07-18',
    endDate: '2026-09-14'
  })
})
