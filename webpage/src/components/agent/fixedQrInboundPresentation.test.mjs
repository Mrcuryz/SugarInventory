import assert from 'node:assert/strict'
import test from 'node:test'
import {
  applyFixedQrTaskCreation,
  fixedQrCreatedTaskPreviewRequest,
  fixedQrInboundCandidates,
  fixedQrInboundSummary,
  fixedQrTaskCreationAction,
  isFixedQrInboundSelectionCard
} from './fixedQrInboundPresentation.mjs'

const card = {
  cardType: 'fixed_qr_inbound_selection',
  fields: [
    {
      kind: 'fixed_qr_inbound_selection_summary',
      requestedPalletCount: 2,
      availablePalletCount: 3,
      productLabel: '黄冰糖（袋）',
      warehouseName: '3号库位',
      defaultSide: '右',
      canSelectRequestedCount: true
    },
    { kind: 'fixed_qr_inbound_candidate', code: 'qr001', fixedProductName: '黄冰糖（袋）', statusLabel: '空闲', selectable: true },
    { kind: 'fixed_qr_inbound_candidate', code: 'QR002', fixedProductName: '黄冰糖（袋）', statusLabel: '空闲', selectable: true },
    { kind: 'fixed_qr_inbound_candidate', code: 'QR003', fixedProductName: '黄冰糖（袋）', statusLabel: '空闲', selectable: true }
  ]
}

test('reads the fixed QR inbound selection without exposing raw status', () => {
  assert.equal(isFixedQrInboundSelectionCard(card), true)
  assert.deepEqual(fixedQrInboundSummary(card), {
    requestedPalletCount: 2,
    availablePalletCount: 3,
    productLabel: '黄冰糖（袋）',
    warehouseName: '3号库位',
    defaultSide: '右',
    operationModeLabel: '使用空闲固定二维码新建任务',
    canSelectRequestedCount: true,
    creationCompleted: false
  })
  assert.deepEqual(fixedQrInboundCandidates(card).map(item => item.code), ['QR001', 'QR002', 'QR003'])
})

test('marks fixed QR candidates unavailable after their tasks are created', () => {
  const result = applyFixedQrTaskCreation(card, ['QR001', 'QR002'])
  assert.equal(result.changed, true)
  assert.equal(fixedQrInboundSummary(result.card).creationCompleted, true)
  assert.deepEqual(
    fixedQrInboundCandidates(result.card).map(item => [item.code, item.statusLabel, item.selectable]),
    [
      ['QR001', '已创建待入库任务', false],
      ['QR002', '已创建待入库任务', false],
      ['QR003', '空闲', true]
    ]
  )
})

test('creates a task-creation action only for the exact requested QR count', () => {
  assert.deepEqual(fixedQrTaskCreationAction(card, ['QR001', 'QR002']), {
    actionKind: 'open_fixed_qr_task_creation',
    palletCodes: ['QR001', 'QR002'],
    productLabel: '黄冰糖（袋）',
    defaultWarehouseName: '3号库位',
    defaultSide: '右',
    requestedPalletCount: 2
  })
  assert.equal(fixedQrTaskCreationAction(card, ['QR001']), null)
  assert.equal(fixedQrTaskCreationAction(card, ['QR001', 'QR002', 'QR003']), null)
})

test('re-enters the controlled task preview after fixed QR tasks are created', () => {
  assert.deepEqual(fixedQrCreatedTaskPreviewRequest({
    palletCodes: [' qr001 ', 'QR002', 'QR001'],
    warehouseName: '3号库位'
  }), {
    message: '请预览以下成品入库待处理任务：QR001、QR002。本次目标入库库位：3号库位'
  })
  assert.equal(fixedQrCreatedTaskPreviewRequest({ palletCodes: [] }), null)
})
