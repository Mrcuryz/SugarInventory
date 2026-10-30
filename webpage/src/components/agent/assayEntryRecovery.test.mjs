import test from 'node:test'
import assert from 'node:assert/strict'
import { createAssayRecoveryJournal, isSettledAssayResult } from './assayEntryRecovery.mjs'
const row = { sessionId: '12345678-abcd', previewRef: 'aep1_' + 'a'.repeat(43) }
const memory = () => {
  const data = new Map()
  return { data, get length() { return data.size }, key: i => [...data.keys()][i], getItem: key => data.get(key) || null, setItem: (key, value) => data.set(key, value), removeItem: key => data.delete(key) }
}
test('journal survives a page instance and stores only opaque references', () => {
  const storage = memory(); const first = createAssayRecoveryJournal(storage)
  first.remember('qc-01', { ...row, executionToken: 'secret', colorValue: 99, productName: 'private' })
  first.remember('qc-01', row)
  assert.deepEqual(createAssayRecoveryJournal(storage).read('qc-01'), [row])
  assert.doesNotMatch([...storage.data.values()][0], /secret|colorValue|productName/)
})
test('different accounts never inherit one another references', () => {
  const journal = createAssayRecoveryJournal(memory()); journal.remember('qc-01', row)
  assert.deepEqual(journal.read('qc-02'), [])
  journal.forget('qc-02', row); assert.equal(journal.read('qc-01').length, 1)
  journal.forget('qc-01', row); assert.deepEqual(journal.read('qc-01'), [])
})
test('blocked or corrupt storage fails closed rather than dropping an unknown result', () => {
  assert.throws(() => createAssayRecoveryJournal({ getItem: () => null, setItem: () => { throw new Error('blocked') } }).remember('qc-01', row))
  assert.throws(() => createAssayRecoveryJournal({ getItem: () => 'not-json' }).read('qc-01'))
  assert.throws(() => createAssayRecoveryJournal({ getItem: () => '[{}]' }).read('qc-01'))
  assert.throws(() => createAssayRecoveryJournal(memory()).remember('', row))
})
test('only a terminal backend outcome can settle a recovery reference', () => {
  assert.equal(isSettledAssayResult({ saved: true, assayId: 8, version: 1 }), true)
  assert.equal(isSettledAssayResult({ saved: true }), false)
  assert.equal(isSettledAssayResult({ saved: false, statusLabel: '尚未保存' }), false)
  assert.equal(isSettledAssayResult({ saved: false, statusLabel: '已取消' }), true)
})
test('two tabs retain independent pending operations when one settles', () => {
  const storage = memory(); const one = createAssayRecoveryJournal(storage); const two = createAssayRecoveryJournal(storage)
  const second = { ...row, previewRef: 'aep1_' + 'b'.repeat(43) }
  one.remember('qc-01', row); two.remember('qc-01', second)
  one.forget('qc-01', row)
  assert.deepEqual(two.read('qc-01'), [second])
  assert.equal(storage.length, 1)
})
