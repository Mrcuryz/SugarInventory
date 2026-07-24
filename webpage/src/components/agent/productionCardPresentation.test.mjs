import assert from 'node:assert/strict'
import test from 'node:test'

import {
  boilingBatchSummary,
  boilingBatchUsages,
  isBoilingBatchCard,
  isProductionCard,
  isProductionMaterialCard,
  isProductionOrderCard,
  productionBoilingSources,
  productionMaterials,
  productionOrderSummary,
  productionOutputs,
  productionStatusTone,
  productionSummaryEntries,
  productionTraceSteps,
  qrProgressText
} from './productionCardPresentation.mjs'

test('recognizes the three controlled production card types', () => {
  assert.equal(isProductionCard({ cardType: 'production_order_progress' }), true)
  assert.equal(isProductionCard({ cardType: 'production_material_trace' }), true)
  assert.equal(isProductionCard({ cardType: 'boiling_batch_trace' }), true)
  assert.equal(isProductionCard({ cardType: 'pallet_tasks' }), false)
  assert.equal(isProductionOrderCard({ cardType: 'production_order_progress' }), true)
  assert.equal(isProductionMaterialCard({ cardType: 'production_material_trace' }), true)
  assert.equal(isBoilingBatchCard({ cardType: 'boiling_batch_trace' }), true)
})

test('extracts only allowlisted production fields by kind', () => {
  const card = {
    fields: [
      { kind: 'production_order_summary', orderNo: 'PO-001', statusLabel: '生产中' },
      { kind: 'production_output', productLabel: '黄冰糖（袋）' },
      { kind: 'production_boiling_source', batchNo: 'BT-001', statusLabel: '已预留' },
      { kind: 'production_material', productLabel: '白砂糖' },
      { kind: 'boiling_batch_summary', batchNo: 'BT-001' },
      { kind: 'boiling_batch_usage', orderNo: 'PO-001' },
      { kind: 'production_trace_step', value: '2026-07-20｜确认入库' },
      { kind: 'unexpected_internal_row', orderRef: 'must-not-render' }
    ]
  }

  assert.equal(productionOrderSummary(card).orderNo, 'PO-001')
  assert.equal(boilingBatchSummary(card).batchNo, 'BT-001')
  assert.equal(productionOutputs(card).length, 1)
  assert.equal(productionBoilingSources(card).length, 1)
  assert.equal(productionMaterials(card).length, 1)
  assert.equal(boilingBatchUsages(card).length, 1)
  assert.equal(productionTraceSteps(card).length, 1)
})

test('formats status, summary and QR progress without inventing missing values', () => {
  assert.equal(productionStatusTone('已完成'), 'success')
  assert.equal(productionStatusTone('生产中'), 'pending')
  assert.equal(productionStatusTone('已预打印'), 'pending')
  assert.equal(productionStatusTone('已用完'), 'success')
  assert.equal(productionStatusTone('已取消'), 'muted')
  assert.deepEqual(
    productionSummaryEntries({ orderTypeLabel: '成品生产', productionDate: '2026-07-20' }),
    [['订单类型', '成品生产'], ['生产日期', '2026-07-20']]
  )
  assert.equal(qrProgressText({ requiredQrCount: 10, boundQrCount: 8, inboundQrCount: 6 }),
    '二维码：需求 10，已绑定 8，已入库 6')
  assert.equal(qrProgressText({}), '')
})
