import assert from 'node:assert/strict'
import test from 'node:test'

import {
  isProductionInputOutputFlowCard,
  productionFlowCoverageText,
  productionFlowDailyPoints,
  productionFlowOrders,
  productionFlowQualityNotes,
  productionFlowScopeNote,
  productionFlowSummary,
  productionFlowWeightText
} from './productionInputOutputFlowPresentation.mjs'

const card = {
  cardType: 'production_input_output_flow_report',
  fields: [
    {
      kind: 'production_input_output_flow_summary',
      materialInputWeightKg: '1656.8',
      stableOutputWeightKg: '1983.8',
      completedOrderCount: 2,
      completedOrdersWithInputCount: 1,
      completedOrdersWithStableOutputCount: 2
    },
    {
      kind: 'production_input_output_flow_daily',
      businessDate: '2026-06-30'
    },
    {
      kind: 'production_input_output_flow_order',
      orderNo: 'PO202606300001'
    },
    {
      kind: 'production_input_output_flow_quality_note',
      value: '1 个已完成订单缺少输入记录'
    },
    {
      kind: 'production_input_output_flow_scope_note',
      value: '两条序列不能直接相除。'
    }
  ]
}

test('recognizes and separates production input-output flow fields', () => {
  assert.equal(isProductionInputOutputFlowCard(card), true)
  assert.equal(isProductionInputOutputFlowCard({ cardType: 'daily_production_report' }), false)
  assert.equal(productionFlowSummary(card)?.materialInputWeightKg, '1656.8')
  assert.equal(productionFlowDailyPoints(card).length, 1)
  assert.equal(productionFlowOrders(card).length, 1)
  assert.deepEqual(
    productionFlowQualityNotes(card),
    ['1 个已完成订单缺少输入记录']
  )
  assert.equal(productionFlowScopeNote(card), '两条序列不能直接相除。')
})

test('formats weights and order coverage without creating ratios', () => {
  assert.equal(productionFlowWeightText('1656.8'), '1656.8 kg')
  assert.equal(productionFlowWeightText(-1), '0 kg')
  assert.equal(
    productionFlowCoverageText(productionFlowSummary(card)),
    '已完成订单 2 个；有输入 1 个，有稳定产出 2 个'
  )
  assert.equal(
    productionFlowCoverageText({ completedOrderCount: 0 }),
    '当前范围没有已完成订单'
  )
})
