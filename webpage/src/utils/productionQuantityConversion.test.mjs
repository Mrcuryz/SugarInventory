import test from 'node:test'
import assert from 'node:assert/strict'
import {
  convertWeightToBoardPieces,
  normalizeSourceUsageQuantity,
  sourceUsageWeightKg,
  totalSourceWeightKg
} from './productionQuantityConversion.mjs'

const batch = { id: 1, kgPerBucket: 11 }

test('桶数按每桶重量折算为千克', () => {
  assert.equal(sourceUsageWeightKg({ batchId: 1, usageUnit: 'BUCKET', usageQuantity: 30 }, [batch]), 330)
})

test('千克来源保持原重量并可汇总多个批次', () => {
  const total = totalSourceWeightKg([
    { batchId: 1, usageUnit: 'BUCKET', usageQuantity: 2 },
    { batchId: 1, usageUnit: 'KG', usageQuantity: 18 }
  ], [batch])
  assert.equal(total, 40)
})

test('重量按单件重量和每板件数拆成板件', () => {
  assert.deepEqual(convertWeightToBoardPieces(1980, 40, 25), {
    boardCount: 1,
    pieceCount: 24,
    totalPieces: 49,
    unusedWeightKg: 20
  })
})

test('产品规格不完整时不生成错误预计产量', () => {
  assert.deepEqual(convertWeightToBoardPieces(330, 0, 25), {
    boardCount: 0,
    pieceCount: 0,
    totalPieces: 0,
    unusedWeightKg: 330
  })
})

test('桶数只允许正整数并按可用整桶数限制', () => {
  assert.deepEqual(normalizeSourceUsageQuantity(50000.8, 'BUCKET', 152.6), {
    value: 152,
    exceeded: true,
    rounded: true,
    limit: 152
  })
})

test('千克数量允许小数但不能超过可用重量', () => {
  assert.deepEqual(normalizeSourceUsageQuantity(2000.4567, 'KG', 1656.8), {
    value: 1656.8,
    exceeded: true,
    rounded: false,
    limit: 1656.8
  })
})