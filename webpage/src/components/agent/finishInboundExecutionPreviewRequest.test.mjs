import assert from 'node:assert/strict'
import test from 'node:test'

import {
  createFinishInboundExecutionPreviewRequest,
  finishInboundExecutionPreviewAllowedItemKeys
} from './finishInboundExecutionPreviewRequest.mjs'

test('builds a visible closed payload without internal ids or layout coordinates', () => {
  const request = createFinishInboundExecutionPreviewRequest({
    previewVersion: 1,
    items: [{
      code: 'bt0019n1',
      warehouseName: '1号库位',
      entryDate: '2026-08-09',
      side: '左',
      quantity: 1,
      unit: '0',
      remark: '验收预览',
      taskId: 21,
      productId: 31,
      warehouseId: 41,
      rowNumber: 1,
      layer: 1
    }]
  })

  assert.ok(request)
  assert.deepEqual(Object.keys(request.payload.items[0]).sort(), [...finishInboundExecutionPreviewAllowedItemKeys].sort())
  assert.equal(request.payload.items[0].code, 'BT0019N1')
  assert.match(request.message, /只预览，不执行/)
  assert.match(request.message, /```json/)
  assert.doesNotMatch(request.message, /taskId|productId|warehouseId|rowNumber|layer/)
})

test('fails closed for incomplete or oversized form data', () => {
  assert.equal(createFinishInboundExecutionPreviewRequest({ previewVersion: 2, items: [{}] }), null)
  assert.equal(createFinishInboundExecutionPreviewRequest({ previewVersion: 1, items: [] }), null)
  assert.equal(createFinishInboundExecutionPreviewRequest({
    previewVersion: 1,
    items: Array.from({ length: 21 }, (_, index) => ({
      code: `BT${index}`,
      warehouseName: '1号库位'
    }))
  }), null)
})
