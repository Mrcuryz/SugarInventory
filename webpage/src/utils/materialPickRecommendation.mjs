const numberValue = value => Number(value || 0)

const candidatePieces = candidate => numberValue(candidate?.pieces)

const candidateId = candidate => candidate?.palletCodeId || candidate?.id || candidate?.palletCode

const candidateLabel = candidate => candidate?.quantityText || (candidatePieces(candidate) > 0 ? `${candidatePieces(candidate)}件` : '整板')

const uniqueById = candidates => {
  const seen = new Set()
  return candidates.filter(candidate => {
    const id = candidateId(candidate)
    if (!id || seen.has(id)) return false
    seen.add(id)
    return true
  })
}

const findPieceCombo = (pieceCandidates, requiredPieces) => {
  if (requiredPieces <= 0) {
    return { type: 'NONE', candidates: [], pieces: 0 }
  }
  const states = new Map([[0, []]])
  pieceCandidates.forEach(candidate => {
    const pieces = candidatePieces(candidate)
    if (pieces <= 0) return
    const snapshot = Array.from(states.entries())
    snapshot.forEach(([sum, combo]) => {
      const nextSum = sum + pieces
      const nextCombo = [...combo, candidate]
      const current = states.get(nextSum)
      if (!current || nextCombo.length < current.length) {
        states.set(nextSum, nextCombo)
      }
    })
  })

  if (states.has(requiredPieces)) {
    return { type: 'EXACT_PIECE_COMBO', candidates: states.get(requiredPieces), pieces: requiredPieces }
  }

  let bestSum = Infinity
  let bestCombo = []
  states.forEach((combo, sum) => {
    if (sum <= requiredPieces) return
    if (sum < bestSum || (sum === bestSum && combo.length < bestCombo.length)) {
      bestSum = sum
      bestCombo = combo
    }
  })
  if (bestCombo.length) {
    return { type: 'NEAREST_PIECE_OVER', candidates: bestCombo, pieces: bestSum }
  }

  const allPieces = pieceCandidates.reduce((sum, candidate) => sum + candidatePieces(candidate), 0)
  return { type: 'SHORTAGE', candidates: pieceCandidates, pieces: allPieces }
}

export const buildMaterialPickRecommendation = ({
  requiredBoardCount = 0,
  requiredPieceCount = 0,
  piecesPerPallet = 0,
  candidates = []
} = {}) => {
  const boards = Math.max(0, numberValue(requiredBoardCount))
  const pieces = Math.max(0, numberValue(requiredPieceCount))
  const perPallet = Math.max(0, numberValue(piecesPerPallet))
  const uniqueCandidates = uniqueById(candidates)
  const fullCandidates = uniqueCandidates.filter(candidate => candidatePieces(candidate) <= 0)
  const pieceCandidates = uniqueCandidates.filter(candidate => candidatePieces(candidate) > 0)

  const requiredTotalPieces = perPallet > 0 ? boards * perPallet + pieces : pieces
  const base = {
    requiredBoardCount: boards,
    requiredPieceCount: pieces,
    selectedFullPalletCodes: [],
    selectedPiecePalletCodes: [],
    extraFullPalletCodes: [],
    pickedTotalPieces: 0,
    requiredTotalPieces,
    expectedReturnPieces: 0,
    matchType: 'INSUFFICIENT',
    selectable: false,
    reason: ''
  }

  if (boards <= 0 && pieces <= 0) {
    return { ...base, matchType: 'INSUFFICIENT', reason: '未识别到有效用料数量' }
  }
  if (perPallet <= 0) {
    return { ...base, matchType: 'INSUFFICIENT', reason: '产品未配置每板件数，无法计算散件推荐' }
  }
  if (fullCandidates.length < boards) {
    return {
      ...base,
      selectedFullPalletCodes: fullCandidates,
      pickedTotalPieces: fullCandidates.length * perPallet,
      reason: `整板库存不足：需要 ${boards} 个整板码，当前只有 ${fullCandidates.length} 个。`
    }
  }

  const selectedFull = fullCandidates.slice(0, boards)
  const remainingFull = fullCandidates.slice(boards)

  if (pieces <= 0) {
    return {
      ...base,
      selectedFullPalletCodes: selectedFull,
      pickedTotalPieces: selectedFull.length * perPallet,
      matchType: 'FULL_ONLY',
      selectable: true
    }
  }

  const pieceCombo = findPieceCombo(pieceCandidates, pieces)
  if (pieceCombo.type === 'EXACT_PIECE_COMBO' || pieceCombo.type === 'NEAREST_PIECE_OVER') {
    const pickedTotalPieces = selectedFull.length * perPallet + pieceCombo.pieces
    return {
      ...base,
      selectedFullPalletCodes: selectedFull,
      selectedPiecePalletCodes: pieceCombo.candidates,
      pickedTotalPieces,
      expectedReturnPieces: Math.max(0, pickedTotalPieces - requiredTotalPieces),
      matchType: pieceCombo.type,
      selectable: true
    }
  }

  const shortagePieces = Math.max(0, pieces - pieceCombo.pieces)
  const extraFullCount = Math.ceil(shortagePieces / perPallet)
  if (remainingFull.length < extraFullCount) {
    const pickedTotalPieces = selectedFull.length * perPallet + pieceCombo.pieces
    return {
      ...base,
      selectedFullPalletCodes: selectedFull,
      selectedPiecePalletCodes: pieceCombo.candidates,
      pickedTotalPieces,
      expectedReturnPieces: 0,
      reason: `散件库存不足且整板补充不足：还需 ${extraFullCount} 个整板码，当前可补 ${remainingFull.length} 个。`
    }
  }

  const extraFull = remainingFull.slice(0, extraFullCount)
  const pickedTotalPieces = selectedFull.length * perPallet + pieceCombo.pieces + extraFull.length * perPallet
  return {
    ...base,
    selectedFullPalletCodes: selectedFull,
    selectedPiecePalletCodes: pieceCombo.candidates,
    extraFullPalletCodes: extraFull,
    pickedTotalPieces,
    expectedReturnPieces: Math.max(0, pickedTotalPieces - requiredTotalPieces),
    matchType: 'PIECE_PLUS_FULL_PALLET',
    selectable: true
  }
}

export const materialPickRecommendationIds = recommendation => [
  ...(recommendation?.selectedFullPalletCodes || []),
  ...(recommendation?.selectedPiecePalletCodes || []),
  ...(recommendation?.extraFullPalletCodes || [])
].map(candidateId).filter(Boolean)

export const materialPickRecommendationPickText = recommendation => {
  if (!recommendation) return '暂无推荐'
  const parts = []
  const fullCount = recommendation.selectedFullPalletCodes?.length || 0
  const pieceParts = (recommendation.selectedPiecePalletCodes || []).map(candidateLabel)
  const extraFullCount = recommendation.extraFullPalletCodes?.length || 0
  if (fullCount) parts.push(`整板 ${fullCount} 个`)
  if (pieceParts.length) parts.push(`散件 ${pieceParts.join(' + ')}`)
  if (extraFullCount) parts.push(`补整板 ${extraFullCount} 个`)
  return parts.length ? parts.join('，') : '暂无可推荐二维码'
}

export const materialPickRecommendationReturnText = recommendation => {
  const pieces = numberValue(recommendation?.expectedReturnPieces)
  return pieces > 0 ? `${pieces}件，需重新贴码入库` : '无'
}
