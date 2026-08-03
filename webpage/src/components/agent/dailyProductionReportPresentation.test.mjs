import assert from 'node:assert/strict'
import test from 'node:test'

import {
  dailyProductionPoints,
  dailyProductionProducts,
  dailyProductionQualityNotes,
  dailyProductionSummary,
  isDailyProductionReportCard,
  productionQuantityText,
  qrProgressSummary,
  reportDataTimeText
} from './dailyProductionReportPresentation.mjs'

const card = {
  cardType: 'daily_production_report',
  fields: [
    {
      kind: 'daily_production_summary',
      totalWeightKg: '2500',
      totalBoardCount: 3,
      loosePieceCount: 5,
      requiredQrCount: 120,
      boundQrCount: 100,
      inboundQrCount: 80,
      dataAsOf: '2026-07-27T15:30:00+08:00'
    },
    { kind: 'daily_production_product', productName: '黄冰糖（袋）' },
    { kind: 'daily_production_point', businessDate: '2026-07-27' },
    { kind: 'daily_production_quality_note', value: '1 条记录缺少重量' }
  ]
}

test('recognizes and separates daily production report fields', () => {
  assert.equal(isDailyProductionReportCard(card), true)
  assert.equal(isDailyProductionReportCard({ cardType: 'production_order_progress' }), false)
  assert.equal(dailyProductionSummary(card)?.totalWeightKg, '2500')
  assert.equal(dailyProductionProducts(card).length, 1)
  assert.equal(dailyProductionPoints(card).length, 1)
  assert.deepEqual(dailyProductionQualityNotes(card), ['1 条记录缺少重量'])
})

test('formats production quantity, qr progress and report timestamp for users', () => {
  const summary = dailyProductionSummary(card)
  assert.equal(productionQuantityText(summary), '3 板 5 件')
  assert.equal(
    qrProgressSummary(summary),
    '二维码进度：应生成 120 个，已绑定 100 个，已入库 80 个'
  )
  assert.equal(reportDataTimeText(summary), '2026-07-27 15:30:00')
  assert.equal(
    reportDataTimeText({ dataAsOf: '2026-07-29T17:37:24.971001' }),
    '2026-07-29 17:37:24'
  )
  assert.equal(productionQuantityText({ totalPieces: 40 }), '折合 40 件')
  assert.equal(productionQuantityText({ outputRecordCount: 0 }), '0 板 0 件')
  assert.equal(productionQuantityText({ outputRecordCount: 1 }), '未登记板件数量')
})
