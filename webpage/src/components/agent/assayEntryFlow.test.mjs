import test from 'node:test'
import assert from 'node:assert/strict'
import { createAssayEntryFlow } from './assayEntryFlow.mjs'
import { assayEntryInput, assayEntryAction, applyAssayEntryResult, invalidateAssayEntryCards, hasAssayEntryPermission } from './assayEntryPresentation.mjs'
const saved = { saved: true, assayId: 12, version: 1, details: { productName: '黄冰糖', saved: true } }
const preview = { previewRef: 'test-ref', preview: { productName: '黄冰糖' } }
const credentials = { executionToken: 'token', idempotencyKey: 'key', extra: 'must not be sent' }
const card = () => ({ fields: [{ kind: 'assay_entry_preview_summary' }, { kind: 'assay_entry_action', entryInput: { productRef: 'aer_payload.signature', values: { sampleDate: '2026-09-24', colorValue: 0, phValue: null } } }] })
const defaults = () => ({ preview: async () => preview, confirm: async () => credentials, execute: async () => saved, result: async () => saved, cancel: async () => {} })

test('input preserves zero and missing; unknown business properties are not forwarded', () => {
  const value = card(); value.fields[1].entryInput.values.selectType = 2
  assert.deepEqual(assayEntryInput(value), { productRef: 'aer_payload.signature', values: { sampleDate: '2026-09-24', colorValue: 0 } })
  value.fields[1].entryInput.productRef = '8'; assert.equal(assayEntryInput(value), null)
})
test('permission requires view and either creation authority', () => {
  assert.equal(hasAssayEntryPermission(['assay:view']), false)
  assert.equal(hasAssayEntryPermission(['assay:create']), false)
  assert.equal(hasAssayEntryPermission(['assay:view', 'quality:test']), true)
})
test('save submits only credentials and duplicate clicks do not send twice', async () => {
  let count = 0
  const flow = createAssayEntryFlow({ ...defaults(), execute: async (ref, sent) => { count++; assert.equal(ref, preview.previewRef); assert.deepEqual(sent, { executionToken: 'token', idempotencyKey: 'key' }); return saved } })
  await flow.load({}); await Promise.all([flow.save(), flow.save()]); await flow.save()
  assert.equal(count, 1); assert.equal(flow.state.phase, 'saved')
})
test('timeout after committed execution recovers the same saved result without retry', async () => {
  let count = 0; let checks = 0
  const flow = createAssayEntryFlow({ ...defaults(), execute: async () => { count++; throw new Error('timeout') }, result: async () => { checks++; return saved } })
  await flow.load({}); await flow.save()
  assert.equal(flow.state.phase, 'saved'); assert.equal(count, 1); assert.equal(checks, 1)
})
test('unknown save blocks resubmission and cancellation until result is verified', async () => {
  let count = 0; let online = false
  const flow = createAssayEntryFlow({ ...defaults(), execute: async () => { count++; throw new Error('timeout') }, result: async () => { if (!online) throw new Error('offline'); return saved } })
  await flow.load({}); await flow.save(); await flow.save()
  assert.equal(flow.state.phase, 'uncertain'); assert.equal(count, 1); assert.equal(await flow.cancel(), false)
  online = true; await flow.check(); assert.equal(flow.state.phase, 'saved'); assert.equal(count, 1)
})
test('definitive state-change rejection requires fresh preview', async () => {
  const flow = createAssayEntryFlow({ ...defaults(), execute: async () => { throw { code: 1055, msg: '状态已变化' } } })
  await flow.load({}); await flow.save(); assert.equal(flow.state.phase, 'invalid'); assert.equal(flow.state.message, '状态已变化')
})
test('reopening reuses existing archive and reads result instead of creating another', async () => {
  let previews = 0
  const flow = createAssayEntryFlow({ ...defaults(), preview: async () => { previews++; return preview } })
  await flow.load({}, preview); assert.equal(previews, 0); assert.equal(flow.state.phase, 'saved')
})
test('cancel invalidates exact preview without executing', async () => {
  let canceled = 0
  const flow = createAssayEntryFlow({ ...defaults(), execute: async () => assert.fail('must not save'), cancel: async ref => { assert.equal(ref, preview.previewRef); canceled++ } })
  await flow.load({}); assert.equal(await flow.cancel(), true); assert.equal(canceled, 1); assert.equal(flow.state.phase, 'invalid')
})
test('saved card cannot be selected again', () => {
  const value = card(); assert.ok(assayEntryAction(value, true))
  assert.equal(applyAssayEntryResult(value, saved), true); assert.equal(assayEntryAction(value, true), null)
})
test('next message cancels active archives; uncertain result blocks continuation', async () => {
  const value = card(); value.fields[1].preview = preview
  let canceled = 0
  await assert.rejects(invalidateAssayEntryCards([{ cards: [value] }], { result: async () => { throw new Error('offline') } }))
  assert.equal(value.fields[1].invalidated, undefined)
  await invalidateAssayEntryCards([{ cards: [value] }], { result: async () => ({ saved: false, statusLabel: '尚未保存' }), cancel: async () => { canceled++ } })
  assert.equal(canceled, 1); assert.equal(value.fields[1].invalidated, true)
})
test('next message preserves a saved result found by readback', async () => {
  const value = card(); value.fields[1].preview = preview
  await invalidateAssayEntryCards([{ cards: [value] }], { result: async () => saved, cancel: async () => assert.fail('must not cancel a saved result') })
  assert.equal(value.fields[0].saved, true)
})
