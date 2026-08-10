import assert from 'node:assert/strict'
import test from 'node:test'

import {
  isTaskTransitionPreviewCard,
  taskTransitionPreviewDialogAction,
  taskTransitionPreviewSummary,
  taskTransitionPreviewTasks
} from './taskTransitionPreviewPresentation.mjs'

const readyCard = {
  cardType: 'task_transition_preview',
  fields: [
    {
      kind: 'task_transition_preview_summary',
      canOpenBusinessDialog: true,
      batchAction: 'confirmIn',
      taskGroupLabel: '成品入库',
      palletCodes: ['bt0019n1', 'BT0019N1']
    },
    { kind: 'task_transition_preview_task', palletCode: 'BT0019N1' }
  ]
}

test('ready task transition preview opens only the existing finished-inbound dialog', () => {
  assert.equal(isTaskTransitionPreviewCard(readyCard), true)
  assert.equal(taskTransitionPreviewSummary(readyCard)?.canOpenBusinessDialog, true)
  assert.equal(taskTransitionPreviewTasks(readyCard).length, 1)
  assert.deepEqual(taskTransitionPreviewDialogAction(readyCard), {
    actionKind: 'open_task_batch',
    batchAction: 'confirmIn',
    taskGroupLabel: '成品入库',
    palletCodes: ['BT0019N1']
  })
})

test('conflicted preview cannot open a business dialog', () => {
  assert.equal(taskTransitionPreviewDialogAction({
    ...readyCard,
    fields: [{ ...readyCard.fields[0], canOpenBusinessDialog: false }]
  }), null)
})

test('ready finished-outbound preview opens only the matching existing dialog', () => {
  const outboundCard = {
    ...readyCard,
    fields: [{
      ...readyCard.fields[0],
      batchAction: 'finishOutConfirm',
      taskGroupLabel: '成品出库',
      palletCodes: ['bt00135d']
    }]
  }

  assert.deepEqual(taskTransitionPreviewDialogAction(outboundCard), {
    actionKind: 'open_task_batch',
    batchAction: 'finishOutConfirm',
    taskGroupLabel: '成品出库',
    palletCodes: ['BT00135D']
  })
})

test('ready transfer preview opens only the matching existing transfer dialog', () => {
  const transferCard = {
    ...readyCard,
    fields: [{
      ...readyCard.fields[0],
      batchAction: 'transferConfirm',
      taskGroupLabel: '调拨',
      palletCodes: ['bt0016lc']
    }]
  }

  assert.deepEqual(taskTransitionPreviewDialogAction(transferCard), {
    actionKind: 'open_task_batch',
    batchAction: 'transferConfirm',
    taskGroupLabel: '调拨',
    palletCodes: ['BT0016LC']
  })
})

test('preview cannot redirect to a mismatched or unknown business dialog', () => {
  assert.equal(taskTransitionPreviewDialogAction({
    ...readyCard,
    fields: [{ ...readyCard.fields[0], batchAction: 'finishOutConfirm', taskGroupLabel: '成品入库' }]
  }), null)
  assert.equal(taskTransitionPreviewDialogAction({
    ...readyCard,
    fields: [{ ...readyCard.fields[0], batchAction: 'deleteTask', taskGroupLabel: '成品出库' }]
  }), null)
})

test('guided finish-inbound preview carries only validated form defaults', () => {
  const guidedCard = {
    ...readyCard,
    fields: [{
      ...readyCard.fields[0],
      palletCodes: ['BT001A', 'BT001B'],
      requestedPalletCount: 2,
      defaultWarehouseName: '3号库位',
      defaultSide: '左',
      guidedProductLabel: '黄冰糖（袋）25kg/件 40件/板'
    }]
  }
  assert.deepEqual(taskTransitionPreviewDialogAction(guidedCard), {
    actionKind: 'open_task_batch',
    batchAction: 'confirmIn',
    taskGroupLabel: '成品入库',
    palletCodes: ['BT001A', 'BT001B'],
    defaultWarehouseName: '3号库位',
    defaultSide: '左',
    requestedPalletCount: 2,
    guidedProductLabel: '黄冰糖（袋）25kg/件 40件/板'
  })
  assert.equal(taskTransitionPreviewDialogAction({
    ...guidedCard,
    fields: [{ ...guidedCard.fields[0], requestedPalletCount: 3 }]
  }), null)
})
