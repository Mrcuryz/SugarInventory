import test from 'node:test'
import assert from 'node:assert/strict'
import { optionHint, optionCardClass } from './agentDisplay.js'

test('candidate hint works for both query and entry, without implying a write', () => {
  assert.equal(optionHint({ supported: true }), '点击选择并继续')
  assert.equal(optionHint({ supported: false }), '此选项暂不可用')
  assert.deepEqual(optionCardClass({ supported: false }), { disabled: true, actionable: false })
})
