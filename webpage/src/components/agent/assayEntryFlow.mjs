// API-independent state machine; uncertain writes are checked before a user may retry.
import { isSavedAssayResult } from './assayEntryResult.mjs'
export const createAssayEntryFlow = (api, onChange = () => {}) => {
  const state = { phase: 'idle', preview: null, result: null, message: '', credentials: null }
  let busy = false
  const change = (values) => { Object.assign(state, values); onChange({ ...state }) }
  const run = async action => {
    if (busy) return
    busy = true
    try { return await action() } finally { busy = false }
  }
  const terminal = result => {
    if (result?.saved === true) {
      if (!isSavedAssayResult(result, state.preview?.preview)) throw new Error('保存结果不完整，请核实后再操作')
      change({ phase: 'saved', result, message: '化验记录已保存。', credentials: null }); return true
    }
    if (['已取消', '已失效', '已过期'].includes(result?.statusLabel)) {
      change({ phase: 'invalid', result, message: `${result.statusLabel}，请重新生成预览。`, credentials: null }); return true
    }
    return false
  }
  const check = async () => {
    change({ phase: 'checking', message: '正在核实保存结果…' })
    try {
      const result = await api.result(state.preview.previewRef)
      if (!terminal(result)) change({ phase: 'ready', message: '尚未保存，可重新核对后确认。' })
    } catch {
      change({ phase: 'uncertain', message: '暂时无法核实是否保存，请恢复网络后点击“核实保存结果”，不要重复录入。' })
    }
  }
  return {
    state,
    load: (input, existingPreview = null) => run(async () => {
      change({ phase: 'loading', message: '', result: null, credentials: null })
      if (existingPreview?.previewRef) { change({ preview: existingPreview }); await check(); return }
      try {
        const preview = await api.preview(input)
        if (!preview?.previewRef || !preview?.preview) throw new Error('预览内容不完整')
        change({ preview, phase: 'ready' })
      } catch (error) { change({ phase: 'invalid', message: error?.msg || error?.message || '无法取得预览，请重新查询后再试。' }) }
    }),
    save: () => run(async () => {
      if (state.phase !== 'ready' || !state.preview?.previewRef) return
      change({ phase: 'saving', message: '' })
      try {
        const ref = state.preview.previewRef
        const confirmation = await api.confirm(ref)
        if (!confirmation?.executionToken || !confirmation?.idempotencyKey) throw new Error('确认结果不完整')
        // No business fields or server display payload are resubmitted to execute.
        const credentials = { executionToken: confirmation.executionToken, idempotencyKey: confirmation.idempotencyKey }
        change({ credentials })
        const result = await api.execute(ref, credentials)
        if (!terminal(result)) throw new Error('需要核实保存结果')
      } catch (error) {
        if ([1054, 1055, 1056, 1057, 1058, 1059, 403, 401].includes(Number(error?.code))) {
          change({ phase: 'invalid', credentials: null, message: error.msg || error.message || '预览或权限已变化，请重新查询后再试。' })
        } else { await check() }
      }
    }),
    check: () => run(check),
    cancel: () => run(async () => {
      if (!state.preview?.previewRef || ['saved', 'invalid'].includes(state.phase)) return true
      if (state.phase === 'uncertain') { await check(); if (state.phase === 'uncertain') return false }
      if (state.phase === 'saved') return true
      change({ phase: 'cancelling' })
      try { await api.cancel(state.preview.previewRef); change({ phase: 'invalid', credentials: null, message: '已取消' }); return true }
      catch { await check(); return state.phase === 'saved' || state.phase === 'invalid' }
    })
  }
}
