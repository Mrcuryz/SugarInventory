import assert from 'node:assert/strict'
import test from 'node:test'

import {
  inventoryLevelTrendDailyPoints,
  inventoryLevelTrendNotes,
  inventoryLevelTrendProducts,
  inventoryLevelTrendScopeNote,
  inventoryLevelTrendSummary,
  inventoryTrendDirection,
  inventoryWeightText,
  isInventoryLevelTrendCard,
  signedInventoryChangeText
} from './inventoryLevelTrendPresentation.mjs'

const card = {
  cardType: 'inventory_level_trend_report',
  fields: [
    { kind: 'inventory_level_trend_summary', closingPieces: 790, netChangePieces: 240 },
    { kind: 'inventory_level_trend_daily', businessDate: '2026-07-20', totalPieces: 790 },
    { kind: 'inventory_level_trend_product', productName: '黄冰糖（袋）', netChangePieces: 240 },
    { kind: 'inventory_level_trend_quality_note', value: '本地历史回放模拟' },
    { kind: 'inventory_level_trend_scope_note', value: '正式上线使用可信日终快照。' }
  ]
}

test('reads inventory trend card sections', () => {
  assert.equal(isInventoryLevelTrendCard(card), true)
  assert.equal(inventoryLevelTrendSummary(card)?.closingPieces, 790)
  assert.equal(inventoryLevelTrendDailyPoints(card).length, 1)
  assert.equal(inventoryLevelTrendProducts(card).length, 1)
  assert.deepEqual(inventoryLevelTrendNotes(card), ['本地历史回放模拟'])
  assert.equal(inventoryLevelTrendScopeNote(card), '正式上线使用可信日终快照。')
})

test('formats signed changes and trend direction', () => {
  assert.equal(signedInventoryChangeText(240), '+240 件')
  assert.equal(signedInventoryChangeText(-5), '-5 件')
  assert.equal(inventoryWeightText('6000.5'), '6000.5 kg')
  assert.equal(inventoryTrendDirection(1), 'increase')
  assert.equal(inventoryTrendDirection(-1), 'decrease')
  assert.equal(inventoryTrendDirection(0), 'unchanged')
})
