import test from 'node:test'
import assert from 'node:assert/strict'
import { registeredReportTrendChart } from './registeredReportTrendChart.mjs'

const card = (cardType, fields) => ({ cardType, fields })

test('builds inventory level chart without recalculating report facts', () => {
  const chart = registeredReportTrendChart(card('inventory_level_trend_report', [
    { kind: 'inventory_level_trend_daily', businessDate: '2026-07-14', totalPieces: 550, totalWeightKg: 13750 },
    { kind: 'inventory_level_trend_daily', businessDate: '2026-07-20', totalPieces: 790, totalWeightKg: 19750 }
  ]))

  assert.deepEqual(chart.categories, ['2026-07-14', '2026-07-20'])
  assert.deepEqual(chart.series[0].data, [550, 790])
  assert.deepEqual(chart.series[1].data, [13750, 19750])
  assert.equal(chart.series[1].axisIndex, 1)
})

test('keeps missing quality rates as gaps instead of displaying zero', () => {
  const chart = registeredReportTrendChart(card('quality_assay_trend_report', [
    { kind: 'quality_assay_trend_point', periodLabel: '7月', passRatePercent: null, assayRecordCount: 2 },
    { kind: 'quality_assay_trend_point', periodLabel: '8月', passRatePercent: 75, assayRecordCount: 4 }
  ]))

  assert.deepEqual(chart.series[0].data, [null, 75])
  assert.deepEqual(chart.series[1].data, [2, 4])
  assert.equal(chart.axes[0].max, 100)
})

test('uses the registered metric unit and only converts display duration units', () => {
  const metric = registeredReportTrendChart(card('quality_metric_trend_report', [
    { kind: 'quality_metric_trend_summary', metricName: 'pH', unit: '' },
    { kind: 'quality_metric_trend_point', periodLabel: '7月', averageValue: 7.1, medianValue: 7.2 },
    { kind: 'quality_metric_trend_point', periodLabel: '8月', averageValue: 7.3, medianValue: 7.3 }
  ]))
  const duration = registeredReportTrendChart(card('pallet_task_cycle_report', [
    { kind: 'pallet_task_cycle_daily', businessDate: '2026-07-30', medianDurationSeconds: 1800, p90DurationSeconds: 7200 },
    { kind: 'pallet_task_cycle_daily', businessDate: '2026-07-31', medianDurationSeconds: 3600, p90DurationSeconds: 10800 }
  ]))

  assert.equal(metric.title, 'pH趋势')
  assert.deepEqual(metric.series[0].data, [7.1, 7.3])
  assert.deepEqual(duration.series[0].data, [0.5, 1])
  assert.deepEqual(duration.series[1].data, [2, 3])
})

test('does not draw a misleading trend for one point or an unknown card', () => {
  assert.equal(registeredReportTrendChart(card('daily_production_report', [
    { kind: 'daily_production_point', businessDate: '2026-08-01', totalWeightKg: 1000 }
  ])), null)
  assert.equal(registeredReportTrendChart(card('unknown_report', [])), null)
})
