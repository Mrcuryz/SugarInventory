const positiveInteger = value => Number.isSafeInteger(value) && value > 0

// A saved group is complete only when every distinct product has its own actual record/version.
export const isSavedAssayResult = (result, expected = null) => {
  if (result?.saved !== true) return false
  const details = result.details
  if (expected?.entryKind === 'GROUP_V1' && (details?.entryKind !== 'GROUP_V1'
      || expected.productCount !== details.productCount || expected.products?.length !== details.products?.length)) return false
  if (details?.entryKind == null) return positiveInteger(result.assayId) && positiveInteger(result.version)
  if (details.entryKind !== 'GROUP_V1' || result.assayId != null || result.version != null
      || details.saved !== true || details.previewOnly !== false || !positiveInteger(details.productCount)
      || !Array.isArray(details.products) || details.products.length !== details.productCount) return false
  const products = new Set(), records = new Set()
  return details.products.every((row, index) => {
    const target = expected?.products?.[index]
    if (!positiveInteger(row?.productId) || !positiveInteger(row.assayId) || !positiveInteger(row.recordVersion)
        || row.recordVersion !== row.proposedVersion || row.saved !== true || row.previewOnly !== false
        || products.has(row.productId) || records.has(row.assayId)
        || (target?.productId != null && target.productId !== row.productId)
        || (target?.proposedVersion != null && target.proposedVersion !== row.recordVersion)) return false
    products.add(row.productId); records.add(row.assayId)
    return true
  })
}
