import test from 'node:test'
import assert from 'node:assert/strict'

import {
  buildProductCreateRoute,
  filterProductsByText,
  productCreatePrefillFromQuery
} from './productSelection.js'

const products = [
  { id: 1, productName: '黄冰糖（袋）', productType: '黄冰糖', productStatus: '成品' },
  { id: 2, productName: '小粒黄冰糖', productType: '黄冰糖', productStatus: '半成品' },
  { id: 3, productName: '白冰糖（箱）', productType: '白冰糖', productStatus: '成品' }
]

test('产品文本搜索忽略空格并优先返回名称前缀匹配', () => {
  assert.deepEqual(
    filterProductsByText(products, '黄 冰 糖').map(item => item.id),
    [1, 2]
  )
})

test('产品文本搜索兼容类型和状态关键词且空查询不铺开结果', () => {
  assert.deepEqual(filterProductsByText(products, '白冰糖成品').map(item => item.id), [3])
  assert.deepEqual(filterProductsByText(products, '  '), [])
})

test('快捷新增路由携带清理后的产品名称和来源', () => {
  assert.deepEqual(buildProductCreateRoute('  新规格黄冰糖  '), {
    name: 'Product',
    query: {
      createProduct: '1',
      productName: '新规格黄冰糖',
      source: 'production-daily-report'
    }
  })
})

test('产品管理页只接受明确的快捷新增参数并限制预填长度', () => {
  assert.equal(productCreatePrefillFromQuery({ productName: '黄冰糖' }), '')
  assert.equal(productCreatePrefillFromQuery({ createProduct: '1', productName: '  黄冰糖（箱） ' }), '黄冰糖（箱）')
  assert.equal(productCreatePrefillFromQuery({ createProduct: '1', productName: '糖'.repeat(120) }).length, 100)
})
