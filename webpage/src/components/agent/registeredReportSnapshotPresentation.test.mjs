import assert from 'node:assert/strict'
import test from 'node:test'

import { registeredReportSnapshotCard } from './registeredReportSnapshotPresentation.mjs'

const base = reportDefinitionId => ({
  reportDefinitionId,
  reportRunId: 'report_run_0123456789abcdef',
  reportName: '测试报表',
  dateRangeLabel: '2026-07-01 至 2026-07-31',
  filtersApplied: { productScope: '黄冰糖（袋）', taskScope: '成品入库任务' },
  dataQuality: { partial: false, notes: [] },
  comparison: { comparisonLabel: '上一等长期间' }
})

test('restores every registered report snapshot into its existing business card type', () => {
  const reports = [
    {
      ...base('today_operations_overview_v1'),
      operationsOverview: {
        productionOutput: { outputRecordCount: 1, totalWeightKg: 1980 },
        assayQuality: { assayRecordCount: 3, judgedRecordCount: 2, passCount: 2 },
        productionFlow: { materialInputRecordCount: 2, materialInputWeightKg: 2000 },
        currentInventory: { productCount: 5, totalEquivalentPieces: 550 },
        todayPalletTasks: { cohortTaskCount: 4 },
        currentPendingTaskCount: 7
      }
    },
    { ...base('daily_production_overview_v1'), metrics: { outputRecordCount: 1, totalWeightKg: 100 } },
    {
      ...base('inventory_level_trend_v1'),
      inventoryTrendMetrics: { observationDayCount: 7, closingPieces: 790, netChangePieces: 240 },
      inventoryTrendDailySeries: [{ businessDate: '2026-07-20', totalPieces: 790 }]
    },
    { ...base('quality_assay_result_trend_v1'), qualityMetrics: { assayRecordCount: 2 } },
    { ...base('quality_metric_trend_v1'), metricTrendSummary: { metricName: 'pH', sampleCount: 2 } },
    { ...base('production_input_output_flow_v1'), productionFlowMetrics: { materialInputRecordCount: 1 } },
    { ...base('pallet_task_cycle_time_v1'), palletTaskCycleMetrics: { cohortTaskCount: 2 } }
  ]
  assert.deepEqual(
    reports.map(report => registeredReportSnapshotCard(report)?.cardType),
    [
      'today_operations_overview_report',
      'daily_production_report',
      'inventory_level_trend_report',
      'quality_assay_trend_report',
      'quality_metric_trend_report',
      'production_input_output_flow_report',
      'pallet_task_cycle_report'
    ]
  )
  reports.forEach(report => {
    const card = registeredReportSnapshotCard(report)
    assert.equal(card.fields[0].reportRunId, report.reportRunId)
    if (report.reportDefinitionId !== 'today_operations_overview_v1') {
      assert.deepEqual(card.fields[0].comparison, report.comparison)
    }
  })
  const todayCard = registeredReportSnapshotCard(reports[0])
  assert.equal(todayCard.fields.find(item => item.kind === 'today_operations_tasks').currentPendingTaskCount, 7)
})

test('does not invent a presentation for an unregistered report definition', () => {
  assert.equal(registeredReportSnapshotCard(base('unknown_report')), null)
})
