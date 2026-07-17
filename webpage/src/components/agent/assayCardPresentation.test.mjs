import assert from 'node:assert/strict'
import test from 'node:test'

import {
  assayHistoryRecords,
  assayMetrics,
  assayResultTone,
  assaySummary,
  isAssayCard
} from './assayCardPresentation.mjs'

test('extracts the display-safe sections of an assay report card', () => {
  const card = {
    cardType: 'assay_report',
    fields: [
      { kind: 'assay_summary', judgeLabel: '暂无法判定' },
      { kind: 'assay_metric', metricName: '色值', actualValueText: '12' },
      { kind: 'assay_note', value: '未配置适用标准不等同于不合格。' }
    ]
  }

  assert.equal(isAssayCard(card), true)
  assert.equal(assaySummary(card).judgeLabel, '暂无法判定')
  assert.deepEqual(assayMetrics(card).map(item => item.metricName), ['色值'])
  assert.equal(assayResultTone('暂无法判定'), 'unknown')
})

test('keeps assay history records separate from report metrics', () => {
  const card = {
    cardType: 'assay_history',
    fields: [
      { kind: 'assay_history_record', sampleDate: '2026-07-14', judgeLabel: '无标准' }
    ]
  }

  assert.equal(isAssayCard(card), true)
  assert.equal(assayMetrics(card).length, 0)
  assert.equal(assayHistoryRecords(card)[0].sampleDate, '2026-07-14')
  assert.equal(assayResultTone('不合格'), 'fail')
})
