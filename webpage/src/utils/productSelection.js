const normalizeSearchText = value => String(value ?? '')
  .trim()
  .toLocaleLowerCase('zh-CN')
  .replace(/\s+/g, '')

const productSearchText = product => normalizeSearchText([
  product?.productName,
  product?.productType,
  product?.productStatus || product?.status
].filter(Boolean).join(' '))

const productMatchScore = (product, normalizedQuery) => {
  const productName = normalizeSearchText(product?.productName)
  if (productName === normalizedQuery) return 0
  if (productName.startsWith(normalizedQuery)) return 1
  if (productName.includes(normalizedQuery)) return 2
  if (productSearchText(product).includes(normalizedQuery)) return 3
  return Number.POSITIVE_INFINITY
}

export const filterProductsByText = (products = [], query = '') => {
  const normalizedQuery = normalizeSearchText(query)
  if (!normalizedQuery) return []

  return (Array.isArray(products) ? products : [])
    .map((product, index) => ({
      product,
      index,
      score: productMatchScore(product, normalizedQuery)
    }))
    .filter(item => Number.isFinite(item.score))
    .sort((left, right) => {
      if (left.score !== right.score) return left.score - right.score
      const nameOrder = String(left.product?.productName || '')
        .localeCompare(String(right.product?.productName || ''), 'zh-CN')
      return nameOrder || left.index - right.index
    })
    .map(item => item.product)
}

export const buildProductCreateRoute = searchText => {
  const productName = String(searchText ?? '').trim().slice(0, 100)
  return {
    name: 'Product',
    query: {
      createProduct: '1',
      productName,
      source: 'production-daily-report'
    }
  }
}

export const productCreatePrefillFromQuery = query => {
  if (String(query?.createProduct || '') !== '1') return ''
  return String(query?.productName ?? '').trim().slice(0, 100)
}
