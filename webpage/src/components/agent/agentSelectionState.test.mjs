import test from 'node:test'
import assert from 'node:assert/strict'
import { isSelectionResumeConsumed } from './agentSelectionState.mjs'

test('keeps an earlier choice consumed when the resume produces the next clarification', () => {
  assert.equal(isSelectionResumeConsumed('completed'), true)
  assert.equal(isSelectionResumeConsumed('interrupt_required'), true)
  assert.equal(isSelectionResumeConsumed('clarification_required'), true)
})

test('restores an earlier choice only when the resume did not complete safely', () => {
  assert.equal(isSelectionResumeConsumed('error'), false)
  assert.equal(isSelectionResumeConsumed('timeout'), false)
  assert.equal(isSelectionResumeConsumed('cancelled'), false)
  assert.equal(isSelectionResumeConsumed(null), false)
})
