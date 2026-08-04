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
