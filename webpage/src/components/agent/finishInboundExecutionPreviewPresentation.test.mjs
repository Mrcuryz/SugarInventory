import assert from 'node:assert/strict'
import test from 'node:test'

import {
  applyFinishInboundExecutionCompletion,
  finishInboundExecutionControlAction,
  finishInboundExecutionPreviewItems,
  finishInboundExecutionPreviewSummary,
  isFinishInboundExecutionPreviewCard
} from './finishInboundExecutionPreviewPresentation.mjs'

test('selects only the controlled preview summary and item fields', () => {
  const summary = { kind: 'finish_inbound_execution_preview_summary', previewStatusLabel: '可确认' }
  const item = { kind: 'finish_inbound_execution_preview_item', palletCode: 'BT0019N1' }
  const card = {
    cardType: 'finish_inbound_execution_preview',
    fields: [summary, item, { kind: 'internal_state', taskId: 21 }]
  }

  assert.equal(isFinishInboundExecutionPreviewCard(card), true)
  assert.equal(finishInboundExecutionPreviewSummary(card), summary)
  assert.deepEqual(finishInboundExecutionPreviewItems(card), [item])
  assert.equal(finishInboundExecutionPreviewSummary({ cardType: 'other' }), null)
  assert.deepEqual(finishInboundExecutionPreviewItems({ cardType: 'other' }), [])
})

test('opens controlled execution only for a ready card and an explicitly authorized UI', () => {
  const card = {
    cardType: 'finish_inbound_execution_preview',
    fields: [
      {
        kind: 'finish_inbound_execution_preview_summary',
        readyForUserConfirmation: true
      },
      { kind: 'finish_inbound_execution_preview_item', palletCode: 'bt0014lu' },
      { kind: 'finish_inbound_execution_preview_item', palletCode: 'BT0014LU' }
    ]
  }

  assert.equal(finishInboundExecutionControlAction(card, false), null)
  assert.deepEqual(finishInboundExecutionControlAction(card, true), {
    actionKind: 'open_finish_inbound_execution_control',
    palletCodes: ['BT0014LU']
  })
  assert.equal(finishInboundExecutionControlAction({
    ...card,
    fields: [{ ...card.fields[0], readyForUserConfirmation: false }]
  }, true), null)
})

test('marks a completed preview as final and removes the controlled action', () => {
  const card = {
    cardType: 'finish_inbound_execution_preview',
    fields: [
      {
        kind: 'finish_inbound_execution_preview_summary',
        previewStatusLabel: '可核对',
        readyForUserConfirmation: true
      },
      {
        kind: 'finish_inbound_execution_preview_item',
        palletCode: 'BT001OTM',
        quantityText: '1 板'
      }
    ]
  }

  const result = applyFinishInboundExecutionCompletion(
    card,
    ['bt001otm'],
    '2026-08-10T18:49:00'
  )

  assert.equal(result.changed, true)
  assert.equal(finishInboundExecutionPreviewSummary(result.card).previewStatusLabel, '已完成')
  assert.equal(finishInboundExecutionPreviewSummary(result.card).readyForUserConfirmation, false)
  assert.equal(finishInboundExecutionPreviewItems(result.card)[0].executionStatusLabel, '已入库')
  assert.equal(finishInboundExecutionControlAction(result.card, true), null)
})
