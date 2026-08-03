import test from 'node:test'
import assert from 'node:assert/strict'

import {
  comparisonChangeText,
  comparisonDailyAverageText,
  registeredReportComparison
} from './registeredReportComparisonPresentation.mjs'

test('extracts one common comparison from every registered report summary shape', () => {
  for (const kind of [
    'daily_production_summary',
    'quality_assay_trend_summary',
    'quality_metric_trend_summary',
    'production_input_output_flow_summary',
    'pallet_task_cycle_summary'
  ]) {
    const result = registeredReportComparison({
      fields: [{
        kind,
        comparison: {
          comparisonLabel: '上一等长期间',
          currentDateRangeLabel: '2026-07-01 至 2026-07-07',
          comparisonDateRangeLabel: '2026-06-24 至 2026-06-30',
          metrics: [{ metricLabel: '产出重量', currentValue: '120', comparisonValue: '100' }]
        }
      }]
    })
    assert.equal(result.label, '上一等长期间')
    assert.equal(result.metrics.length, 1)
  }
})

test('renders neutral signed changes and daily averages without good-or-bad semantics', () => {
  const metric = {
    additive: true,
    unit: 'kg',
    absoluteChange: '-20',
    percentChange: '-16.67',
    currentDailyAverage: '40',
    comparisonDailyAverage: '50',
    dailyAverageAbsoluteChange: '-10'
  }
  assert.equal(comparisonChangeText(metric), '变化 -20 kg（-16.67%）')
  assert.equal(
    comparisonDailyAverageText(metric),
    '日均 40 kg/天，对比期 50 kg/天，变化 -10 kg/天'
  )
})
