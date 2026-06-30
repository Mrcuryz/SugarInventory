const numberValue = value => {
  const number = Number(value)
  return Number.isFinite(number) ? number : 0
}

export const sourceUsageWeightKg = (source, batches = []) => {
  const batch = source?.selectedBatch || batches.find(item => item.id === source?.batchId)
  const quantity = Math.max(0, numberValue(source?.usageQuantity))
  if (!batch || quantity <= 0) return 0
  if (source?.usageUnit === 'KG') return quantity
  return quantity * Math.max(0, numberValue(batch.kgPerBucket))
}

export const totalSourceWeightKg = (sources = [], batches = []) => sources
  .reduce((total, source) => total + sourceUsageWeightKg(source, batches), 0)
export const normalizeSourceUsageQuantity = (value, unit, availableQuantity) => {
  const original = Math.max(0, numberValue(value))
  const available = Math.max(0, numberValue(availableQuantity))
  const normalized = unit === 'BUCKET'
    ? Math.floor(original)
    : Math.round(original * 1000) / 1000
  const limit = unit === 'BUCKET' ? Math.floor(available) : available
  return {
    value: Math.min(normalized, limit),
    exceeded: normalized > limit,
    rounded: unit === 'BUCKET' && normalized !== original,
    limit
  }
}

export const convertWeightToBoardPieces = (weightKg, weightPerPiece, piecesPerPallet) => {
  const safeWeight = Math.max(0, numberValue(weightKg))
  const pieceWeight = Math.max(0, numberValue(weightPerPiece))
  const perPallet = Math.floor(Math.max(0, numberValue(piecesPerPallet)))
  if (!safeWeight || !pieceWeight || !perPallet) {
    return { boardCount: 0, pieceCount: 0, totalPieces: 0, unusedWeightKg: safeWeight }
  }

  const totalPieces = Math.floor((safeWeight + Number.EPSILON) / pieceWeight)
  return {
    boardCount: Math.floor(totalPieces / perPallet),
    pieceCount: totalPieces % perPallet,
    totalPieces,
    unusedWeightKg: Math.max(0, safeWeight - totalPieces * pieceWeight)
  }
}
