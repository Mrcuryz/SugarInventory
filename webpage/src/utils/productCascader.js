export const productCascaderProps = {
  emitPath: false,
  label: 'label',
  value: 'value',
  children: 'children',
  expandTrigger: 'hover'
}

export const buildProductCascaderOptions = (products = []) => {
  const categoryMap = {}
  products.forEach(product => {
    const category = product.status || product.category || product.productStatus
    const productId = product.id ?? product.productId
    if (!category || !product.productType || !productId) {
      return
    }
    const categoryNode = categoryMap[category] || {
      value: category,
      label: category,
      children: {}
    }
    const typeNode = categoryNode.children[product.productType] || {
      value: product.productType,
      label: product.productType,
      children: []
    }
    typeNode.children.push({
      value: productId,
      label: product.productName
    })
    categoryNode.children[product.productType] = typeNode
    categoryMap[category] = categoryNode
  })
  return Object.values(categoryMap).map(category => ({
    value: category.value,
    label: category.label,
    children: Object.values(category.children)
  }))
}
