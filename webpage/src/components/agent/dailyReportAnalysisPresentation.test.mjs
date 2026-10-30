import test from 'node:test'
import assert from 'node:assert/strict'
import { dailyAnalysisChart, dailyAnalysisValue, dailyAnalysisSeries } from './dailyReportAnalysisPresentation.mjs'
import { registeredReportSnapshotCard } from './registeredReportSnapshotPresentation.mjs'
import { registeredReportComparison } from './registeredReportComparisonPresentation.mjs'

test('null and zero stay distinct; energy cannot chart tons', () => {
  const series = { unit: 'Kw.h', tonnageApplicable: false, points: [{ businessDate: '2026-07-01', quantity: null }, { businessDate: '2026-07-02', quantity: 0 }] }
  assert.deepEqual(dailyAnalysisChart(series).series[0].data, [null, 0])
  assert.equal(dailyAnalysisChart(series, 'convertedTons'), null)
  assert.equal(dailyAnalysisValue(null), '未填报')
  assert.equal(dailyAnalysisValue(0, '件'), '0 件')
})

test('history retains all comparison metrics including previous-only records and blank rates', () => {
  const comparison = { comparisonLabel: '上一等长期间', currentDateRangeLabel: '2026-07-01', comparisonDateRangeLabel: '2026-06-30',
    metrics: Array.from({ length: 40 }, (_, i) => ({ metricLabel: `项目${i}`, currentValue: null, comparisonValue: 20, percentChange: null, internalId: 42 })),
    notes: ['缺日不补零'] }
  const card = registeredReportSnapshotCard({ reportDefinitionId: 'production_daily_report_analysis_v1',
    productionDailyReport: { series: [] }, comparison })
  assert.equal(card.fields[0].isEmpty, false)
  assert.equal(card.fields[0].currentPeriodEmpty, true)
  assert.equal(registeredReportComparison(card).metrics.length, 40)
  assert.equal(registeredReportComparison(card).metrics[39].percentChange, null)
  assert.equal('internalId' in registeredReportComparison(card).metrics[39], false)
  comparison.metrics = []
  const empty = registeredReportSnapshotCard({ reportDefinitionId: 'production_daily_report_analysis_v1',
    productionDailyReport: { series: [] }, comparison })
  assert.ok(registeredReportComparison(empty))
  assert.equal(registeredReportComparison(empty).metrics.length, 0)
})

test('history preserves all dates and backend totals without exposing internal fields', () => {
  const report = { reportDefinitionId: 'production_daily_report_analysis_v1', filtersApplied: {}, productionDailyReport: { series: [
    { label: '产量', quantity: 100, convertedTons: null, tonnageApplicable: true, internalId: 9, points: Array.from({ length: 31 }, (_, i) => ({ businessDate: `2026-07-${String(i + 1).padStart(2, '0')}`, quantity: i === 0 ? null : 0 })) }
  ] } }
  const card = registeredReportSnapshotCard(report)
  const series = dailyAnalysisSeries(card)[0]
  assert.equal(card.cardType, 'production_daily_report_analysis')
  assert.equal(series.quantity, 100)
  assert.equal(series.points.length, 31)
  assert.equal(dailyAnalysisChart(series, 'convertedTons').axes[0].unit, '吨')
  assert.equal('internalId' in series, false)
})
