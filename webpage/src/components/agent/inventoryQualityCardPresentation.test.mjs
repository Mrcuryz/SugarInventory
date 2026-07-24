import assert from 'node:assert/strict'
import test from 'node:test'

import {
  inventoryQualityRecords,
  inventoryQualitySummary,
  inventoryQualityTone,
  isInventoryQualityCard
} from './inventoryQualityCardPresentation.mjs'

const card = {
  cardType: 'inventory_quality',
  fields: [
    { kind: 'inventory_quality_summary', totalGroups: 2 },
    { kind: 'inventory_quality_record', productLabel: '黄冰糖（袋）', judgeLabel: '不合格' }
  ]
}

test('recognizes inventory quality cards and separates summary from records', () => {
  assert.equal(isInventoryQualityCard(card), true)
  assert.equal(inventoryQualitySummary(card).totalGroups, 2)
  assert.equal(inventoryQualityRecords(card).length, 1)
})

test('maps user-facing quality labels to visual tones', () => {
  assert.equal(inventoryQualityTone('不合格'), 'danger')
  assert.equal(inventoryQualityTone('符合黄冰糖 v1'), 'success')
  assert.equal(inventoryQualityTone('符合查询条件'), 'success')
  assert.equal(inventoryQualityTone('暂无法判定'), 'neutral')
})
