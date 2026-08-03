import assert from 'node:assert/strict'
import test from 'node:test'
import {
  isQualityMetricTrendCard,
  metricValueText,
  metricWithinRateText,
  qualityMetricTrendNotes,
  qualityMetricTrendPoints,
  qualityMetricTrendProducts,
  qualityMetricTrendStandards,
  qualityMetricTrendSummary
} from './qualityMetricTrendPresentation.mjs'

const card = {
  cardType: 'quality_metric_trend_report',
  fields: [
    { kind: 'quality_metric_trend_summary', metricName: 'pH', sampleCount: 3, comparableStandardCount: 1, withinStandardRatePercent: 100 },
    { kind: 'quality_metric_trend_product', productName: '黄冰糖（袋）' },
    { kind: 'quality_metric_trend_standard', standardLabel: '黄冰糖 v1' },
    { kind: 'quality_metric_trend_point', periodLabel: '2026-04', sampleCount: 1 },
    { kind: 'quality_metric_trend_point', periodLabel: '2026-05', sampleCount: 0 },
    { kind: 'quality_metric_trend_point', periodLabel: '2026-07', sampleCount: 2 },
    { kind: 'quality_metric_trend_quality_note', value: '2 个样本无可比较历史标准' }
  ]
}

test('extracts metric trend sections and hides empty periods', () => {
  assert.equal(isQualityMetricTrendCard(card), true)
  assert.equal(qualityMetricTrendSummary(card)?.metricName, 'pH')
  assert.equal(qualityMetricTrendProducts(card).length, 1)
  assert.equal(qualityMetricTrendStandards(card).length, 1)
  assert.deepEqual(
    qualityMetricTrendPoints(card).map(point => point.periodLabel),
    ['2026-04', '2026-07']
  )
  assert.deepEqual(
    qualityMetricTrendNotes(card),
    ['2 个样本无可比较历史标准']
  )
})

test('formats metric values and controlled comparable-rate language', () => {
  assert.equal(metricValueText('7.1', ''), '7.1')
  assert.equal(metricValueText('1.3', 'g/100g'), '1.3 g/100g')
  assert.equal(metricValueText(null, 'g/100g'), '-')
  assert.equal(metricWithinRateText({ comparableStandardCount: 1, withinStandardRatePercent: 100 }), '100%')
  assert.equal(metricWithinRateText({ comparableStandardCount: 0, withinStandardRatePercent: null }), '无可比较标准')
})
