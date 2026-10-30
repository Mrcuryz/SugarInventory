import test from 'node:test'
import assert from 'node:assert/strict'
import { isSavedAssayResult } from './assayEntryResult.mjs'
import { isSettledAssayResult } from './assayEntryRecovery.mjs'
import { assayEntryInput, applyAssayEntryResult, assayEntryAction } from './assayEntryPresentation.mjs'
import { createAssayEntryFlow } from './assayEntryFlow.mjs'

const savedGroup = () => ({ saved: true, assayId: null, version: null, details: {
  entryKind: 'GROUP_V1', groupName: '测试组', productCount: 2, saved: true, previewOnly: false,
  products: [
    { productId: 2, productName: '甲产品', assayId: 101, recordVersion: 1, proposedVersion: 1, saved: true, previewOnly: false },
    { productId: 3, productName: '乙产品', assayId: 102, recordVersion: 4, proposedVersion: 4, saved: true, previewOnly: false }
  ]
} })
const card = () => ({ fields: [{ kind: 'assay_entry_preview_summary', ...savedGroup().details, saved: false },
  { kind: 'assay_entry_action', entryInput: { groupRef: 'aer_payload.signature', groupId: 99, productIds: [9],
    values: { sampleDate: '2026-09-24', colorValue: 0, phValue: null, productId: 999 } } }] })

test('group input sends only signed group reference and explicit measured values', () => {
  const value = card()
  assert.deepEqual(assayEntryInput(value), { groupRef: 'aer_payload.signature', values: { sampleDate: '2026-09-24', colorValue: 0 } })
  value.fields[1].entryInput.productRef = 'aer_other.signature'
  assert.equal(assayEntryInput(value), null)
})
test('complete saved group updates the whole card and cannot be saved again', () => {
  const value = card(); const result = savedGroup()
  assert.equal(isSavedAssayResult(result), true); assert.equal(isSettledAssayResult(result), true)
  assert.ok(assayEntryAction(value, true)); assert.equal(applyAssayEntryResult(value, result), true)
  assert.equal(value.fields[0].products.length, 2); assert.equal(value.fields[0].products[1].recordVersion, 4)
  assert.equal(assayEntryAction(value, true), null)
})
test('missing, duplicate, unversioned or partially saved members cannot settle recovery', () => {
  const mutations = [r => r.details.products.pop(), r => { r.details.products[1].assayId = 101 },
    r => { r.details.products[1].productId = 2 }, r => { r.details.products[1].recordVersion = 0 },
    r => { r.details.products[1].saved = false }, r => { r.details.products[1].recordVersion = 5 },
    r => { r.assayId = 101 }, r => { r.details.entryKind = 'UNKNOWN' }]
  for (const mutate of mutations) {
    const value = savedGroup(); mutate(value)
    assert.equal(isSavedAssayResult(value), false); assert.equal(isSettledAssayResult(value), false)
    assert.equal(applyAssayEntryResult(card(), value), false)
  }
})
test('a subset or single-product success cannot replace a complete group preview', async () => {
  for (const partial of [{ saved: true, assayId: 101, version: 1 },
    { ...savedGroup(), details: { ...savedGroup().details, productCount: 1, products: savedGroup().details.products.slice(0, 1) } }]) {
    const flow = createAssayEntryFlow({ preview: async () => ({ previewRef: 'ref', preview: savedGroup().details }),
      confirm: async () => ({ executionToken: 'token', idempotencyKey: 'key' }), execute: async () => partial, result: async () => partial })
    await flow.load({}); await flow.save()
    assert.equal(flow.state.phase, 'uncertain')
  }
})
test('group timeout restores all original records without another execution', async () => {
  let saves = 0
  const expected = savedGroup()
  const flow = createAssayEntryFlow({ preview: async () => ({ previewRef: 'ref', preview: expected.details }),
    confirm: async () => ({ executionToken: 'token', idempotencyKey: 'key' }),
    execute: async () => { saves++; throw new Error('timeout') }, result: async () => expected })
  await flow.load({}); await flow.save(); await flow.save()
  assert.equal(saves, 1); assert.equal(flow.state.phase, 'saved'); assert.equal(flow.state.result.details.products.length, 2)
})
