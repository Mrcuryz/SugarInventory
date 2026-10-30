import test from 'node:test'
import assert from 'node:assert/strict'
import { dailyReportRecords, reportedValue, cumulativeDisplay, overviewValueLabel } from './dailyReportRecordsPresentation.mjs'

test('daily report display preserves zero, unfilled values and stored units', () => {
  assert.equal(reportedValue(0, '件'), '0 件')
  assert.equal(reportedValue(null, '吨'), '未填报')
  assert.equal(reportedValue(undefined), '未填报')
  assert.equal(reportedValue(''), '未填报')
  assert.equal(reportedValue(12.5, '%'), '12.5 %')
  assert.equal(reportedValue(300, 'Kw.h'), '300 千瓦时')
})

test('metric cumulative values are not assigned invented ton units', () => {
  assert.equal(cumulativeDisplay({ itemKindLabel: '指标', unit: '%' }).unit, '')
  assert.equal(cumulativeDisplay({ itemKindLabel: '产品' }).unit, '吨')
})

test('overview keeps full-filter totals distinct from page rows and identifies partial values', () => {
  const item = { kind: 'daily_records_overview', quantity: 120, recordCount: 3, quantityRows: 2, tonnageRows: 1, observedDays: 2 }
  const result = dailyReportRecords({ fields: [item, { kind: 'daily_records_row', dailyActual: 0 }] })
  assert.equal(result.overview[0].quantity, 120)
  assert.equal(result.rows[0].dailyActual, 0)
  assert.equal(overviewValueLabel(item, 3), '已记录部分数量')
  assert.equal(overviewValueLabel(item, 3, true), '已记录部分折吨')
})

test('daily report records remain distinct from order report fields', () => {
  const result = dailyReportRecords({ fields: [
    { kind: 'daily_records_summary', sourceLabel: '生产日报填报记录', page: 2, total: 21 },
    { kind: 'daily_records_header', reportDate: '2026-09-08', statusLabel: '草稿' },
    { kind: 'daily_records_row', itemName: '白冰糖', dailyActual: 0, monthTons: 7 },
    { kind: 'daily_production_product', totalWeightKg: 999 }
  ] })
  assert.equal(result.rows.length, 1)
  assert.equal(result.rows[0].monthTons, 7)
  assert.equal(result.summary.page, 2)
  assert.equal(result.reports[0].statusLabel, '草稿')
  assert.deepEqual(dailyReportRecords(null).rows, [])
})
