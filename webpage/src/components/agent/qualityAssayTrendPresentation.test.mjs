import test from 'node:test'
import assert from 'node:assert/strict'

import {
  isQualityAssayTrendCard,
  qualityAssayTrendNotes,
  qualityAssayTrendPoints,
  qualityAssayTrendProducts,
  qualityAssayTrendStandards,
  qualityAssayTrendSummary,
  qualityJudgementSummary,
  qualityPassRateText
} from './qualityAssayTrendPresentation.mjs'

const card = {
  cardType: 'quality_assay_trend_report',
  fields: [
    {
      kind: 'quality_assay_trend_summary',
      assayRecordCount: 3,
      passCount: 1,
      failCount: 1,
      noStandardCount: 1,
      multipleCandidatesCount: 0,
      passRatePercent: '50'
    },
    { kind: 'quality_assay_trend_product', productName: '黄冰糖（袋）' },
    { kind: 'quality_assay_trend_standard', standardLabel: '黄冰糖 v1' },
    { kind: 'quality_assay_trend_point', periodLabel: '2026-07-16', assayRecordCount: 0 },
    { kind: 'quality_assay_trend_point', periodLabel: '2026-07-17', assayRecordCount: 3 },
    { kind: 'quality_assay_trend_quality_note', value: '1 条记录晚录' }
  ]
}

test('recognizes and separates controlled quality trend fields', () => {
  assert.equal(isQualityAssayTrendCard(card), true)
  assert.equal(qualityAssayTrendSummary(card)?.assayRecordCount, 3)
  assert.equal(qualityAssayTrendProducts(card).length, 1)
  assert.equal(qualityAssayTrendStandards(card).length, 1)
  assert.equal(qualityAssayTrendPoints(card).length, 1)
  assert.deepEqual(qualityAssayTrendNotes(card), ['1 条记录晚录'])
})

test('formats controlled pass-rate denominator and judgement counts', () => {
  assert.equal(qualityPassRateText(card.fields[0]), '50%')
  assert.equal(qualityPassRateText({ passRatePercent: null }), '无可计算样本')
  assert.equal(
    qualityJudgementSummary(card.fields[0]),
    '合格 1 条 · 不合格 1 条 · 无标准 1 条'
  )
})
