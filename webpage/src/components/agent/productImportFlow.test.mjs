import test from 'node:test'
import assert from 'node:assert/strict'
import { canImportProducts, fileError, mappingError, createProductImportFlow } from './productImportFlow.mjs'

const file = { name: '产品.xlsx', size: 1024 }
const sheet = { name: '产品', hidden: false, columns: [{ index: 0, title: '名称', suggestedField: null },
  { index: 1, title: '单件重量（kg）', suggestedField: 'weightPerPiece' }] }
const receipt = { reference: 'r1', fileName: file.name, sheet: sheet.name, rowCount: 2, expiresAt: Date.now() + 1800000 }
const source = { attachment: receipt, page: 1, pageSize: 50, rows: [{ rowNumber: 2, values: { productName: '黄冰糖（袋）', weightPerPiece: '0' } }] }
const setup = overrides => {
  const calls = []
  const api = { list: async () => [], inspect: async () => [sheet], upload: async (...args) => { calls.push(args); return receipt },
    read: async () => source, remove: async () => true, ...overrides }
  const flow = createProductImportFlow(api)
  return { flow, calls }
}
const prepare = async flow => { await flow.inspect(file); flow.setMapping(0, 'productName') }

test('permission requires product view and either create or update', () => {
  assert.equal(canImportProducts(['product:view','product:create']), true)
  assert.equal(canImportProducts(['product:view','product:update']), true)
  for (const codes of [[],['product:view'],['product:create'],['product:update']]) assert.equal(canImportProducts(codes),false)
})
test('file checks extension, empty content and size without reading it locally', () => {
  assert.equal(fileError(file),'')
  assert.equal(fileError({name:'产品.XLS',size:1}),'')
  for (const bad of [null,{name:'a.xlsm',size:1},{name:'a.xlsx',size:0},{name:'a.xlsx',size:10*1024*1024+1}]) assert.ok(fileError(bad))
})
test('unknown headers require explicit mapping and duplicates cannot submit', () => {
  assert.match(mappingError(sheet,{1:'weightPerPiece'}),/名称/)
  assert.equal(mappingError(sheet,{0:'productName',1:'weightPerPiece'}),'')
  assert.match(mappingError(sheet,{0:'productName',1:'productName'}),/不能对应多列/)
  assert.ok(mappingError({...sheet,hidden:true},{0:'productName'}))
  assert.ok(mappingError(sheet,{0:'productId',1:'weightPerPiece'}))
})
test('multi-sheet file does not silently choose first visible sheet', async () => {
  const { flow }=setup({inspect:async()=>[sheet,{name:'内部',hidden:true,columns:[]}]})
  await flow.inspect(file)
  assert.equal(flow.state().sheet,'')
  flow.chooseSheet('内部'); assert.equal(flow.state().sheet,'')
  flow.chooseSheet('产品'); assert.equal(flow.state().sheet,'产品')
})
test('upload uses only explicit column mapping and displays original rows without a product save', async () => {
  const { flow, calls }=setup()
  await flow.inspect(file)
  assert.equal(await flow.upload(),false); assert.equal(calls.length,0)
  flow.setMapping(0,'productName'); assert.equal(await flow.upload(),true)
  assert.deepEqual(calls[0],[file,'产品',{0:'productName',1:'weightPerPiece'}])
  assert.equal(flow.state().source.rows[0].values.weightPerPiece,'0')
  assert.equal(flow.state().selectedRef,'r1'); assert.equal(flow.state().file,null)
})
test('double clicking during upload creates only one attachment', async () => {
  let release, requests=0
  const pending=new Promise(resolve=>{release=resolve})
  const {flow}=setup({upload:async()=>{requests++; return pending}})
  await prepare(flow)
  const first=flow.upload(); assert.equal(await flow.upload(),false)
  release(receipt); await first; assert.equal(requests,1)
})
test('lost upload response requires list recovery before retry and clears pending file after recovery', async () => {
  let requests=0
  const {flow}=setup({upload:async()=>{requests++; throw new Error('timeout')},list:async()=>[receipt]})
  await prepare(flow); assert.equal(await flow.upload(),false)
  assert.equal(flow.state().recoveryRequired,true)
  assert.equal(await flow.upload(),false); assert.equal(requests,1)
  await flow.refresh(); assert.equal(flow.state().attachments.length,1)
  assert.equal(flow.state().file,null); assert.equal(flow.state().recoveryRequired,false)
  await flow.read('r1'); assert.equal(flow.state().source,source)
})
test('read failure preserves successful upload receipt and never resubmits file', async () => {
  const {flow,calls}=setup({read:async()=>{throw new Error('network')}})
  await prepare(flow); await flow.upload()
  assert.equal(flow.state().attachments[0],receipt); assert.equal(flow.state().file,null)
  await flow.read('r1'); assert.equal(calls.length,1)
})
test('closing or changing session discards late asynchronous responses', async () => {
  let release, changed=0
  const api={list:()=>new Promise(resolve=>{release=resolve})}
  const flow=createProductImportFlow(api,()=>changed++)
  const pending=flow.refresh(); flow.dispose(); const before=changed
  release([receipt]); await pending
  assert.equal(changed,before); assert.deepEqual(flow.state().attachments,[])
})
test('refresh removes expired source and removal clears selected content', async () => {
  const {flow}=setup()
  await prepare(flow); await flow.upload(); await flow.refresh()
  assert.equal(flow.state().source,null); assert.equal(flow.state().selectedRef,'')
  await prepare(flow); await flow.upload(); await flow.remove('r1')
  assert.deepEqual(flow.state().attachments,[]); assert.equal(flow.state().source,null)
})
test('rejected replacement file cannot leave the old file ready for accidental upload', async () => {
  const {flow,calls}=setup(); await prepare(flow)
  await flow.inspect({name:'bad.xlsm',size:100})
  assert.equal(flow.state().file,null); await flow.upload(); assert.equal(calls.length,0)
})
