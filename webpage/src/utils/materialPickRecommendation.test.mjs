import assert from 'node:assert/strict'
import {
  buildMaterialPickRecommendation,
  materialPickRecommendationIds
} from './materialPickRecommendation.mjs'

const full = id => ({ palletCodeId: `F${id}`, pieces: 0, quantityText: '1板' })
const loose = (id, pieces) => ({ palletCodeId: `L${id}`, pieces, quantityText: `${pieces}件` })

{
  const result = buildMaterialPickRecommendation({
    requiredBoardCount: 4,
    requiredPieceCount: 17,
    piecesPerPallet: 20,
    candidates: [full(1), full(2), full(3), full(4), loose(1, 19), loose(2, 16)]
  })
  assert.equal(result.matchType, 'NEAREST_PIECE_OVER')
  assert.deepEqual(materialPickRecommendationIds(result), ['F1', 'F2', 'F3', 'F4', 'L1'])
  assert.equal(result.expectedReturnPieces, 2)
}

{
  const result = buildMaterialPickRecommendation({
    requiredPieceCount: 16,
    piecesPerPallet: 20,
    candidates: [loose(1, 19), loose(2, 16)]
  })
  assert.equal(result.matchType, 'EXACT_PIECE_COMBO')
  assert.deepEqual(materialPickRecommendationIds(result), ['L2'])
  assert.equal(result.expectedReturnPieces, 0)
}

{
  const result = buildMaterialPickRecommendation({
    requiredPieceCount: 19,
    piecesPerPallet: 20,
    candidates: [loose(1, 10), loose(2, 9)]
  })
  assert.equal(result.matchType, 'EXACT_PIECE_COMBO')
  assert.deepEqual(materialPickRecommendationIds(result), ['L1', 'L2'])
  assert.equal(result.expectedReturnPieces, 0)
}

{
  const result = buildMaterialPickRecommendation({
    requiredPieceCount: 19,
    piecesPerPallet: 20,
    candidates: [loose(1, 10), full(1)]
  })
  assert.equal(result.matchType, 'PIECE_PLUS_FULL_PALLET')
  assert.deepEqual(materialPickRecommendationIds(result), ['L1', 'F1'])
  assert.equal(result.expectedReturnPieces, 11)
}

{
  const result = buildMaterialPickRecommendation({
    requiredBoardCount: 4,
    piecesPerPallet: 20,
    candidates: [full(1), full(2), full(3), full(4), loose(1, 19)]
  })
  assert.equal(result.matchType, 'FULL_ONLY')
  assert.deepEqual(materialPickRecommendationIds(result), ['F1', 'F2', 'F3', 'F4'])
  assert.equal(result.expectedReturnPieces, 0)
}

{
  const result = buildMaterialPickRecommendation({
    requiredBoardCount: 4,
    piecesPerPallet: 20,
    candidates: [full(1), full(2), full(3), loose(1, 19)]
  })
  assert.equal(result.matchType, 'INSUFFICIENT')
  assert.equal(result.selectable, false)
}

console.log('materialPickRecommendation tests passed')
