const prefix = 'assay-entry-recovery:v1:'
const valid = row => row && typeof row.sessionId === 'string' && /^[A-Za-z0-9-]{1,64}$/.test(row.sessionId)
  && /^aep1_[A-Za-z0-9_-]{43}$/.test(row.previewRef || '')

// Persist only opaque references, never measurements, credentials, user names or responses.
export const createAssayRecoveryJournal = (storage, changed = () => {}) => {
  const key = owner => {
    if (typeof owner !== 'string' || !owner.trim()) throw new Error('无法确认当前账号，请重新登录后再保存。')
    return prefix + encodeURIComponent(owner)
  }
  const legacy = owner => {
    const value = storage.getItem(key(owner))
    if (!value) return []
    const rows = JSON.parse(value)
    if (!Array.isArray(rows) || rows.some(row => !valid(row))) throw new Error('待核实记录无法读取，请联系管理员，暂勿重复录入。')
    return rows.map(({ sessionId, previewRef }) => ({ sessionId, previewRef }))
  }
  const entryKey = (owner, row) => `${key(owner)}:${row.sessionId}:${row.previewRef}`
  const read = owner => {
    const rows = legacy(owner)
    const start = key(owner) + ':'
    for (let i = 0; i < storage.length; i++) {
      const name = storage.key(i)
      if (!name?.startsWith(start)) continue
      const row = JSON.parse(storage.getItem(name))
      if (!valid(row) || name !== entryKey(owner, row)) throw new Error('待核实记录无法读取，请联系管理员，暂勿重复录入。')
      if (!rows.some(item => item.sessionId === row.sessionId && item.previewRef === row.previewRef)) rows.push({ sessionId: row.sessionId, previewRef: row.previewRef })
    }
    return rows
  }
  const write = (name, value) => {
    const serialized = JSON.stringify(value)
    storage.setItem(name, serialized)
    if (storage.getItem(name) !== serialized) throw new Error('无法保留待核实记录，请检查浏览器存储设置后再试。')
    changed()
  }
  return {
    read,
    remember: (owner, row) => {
      if (!valid(row)) throw new Error('确认引用无效，请重新预览。')
      const rows = read(owner)
      if (rows.some(item => item.previewRef === row.previewRef && item.sessionId === row.sessionId)) return
      if (rows.length >= 20) throw new Error('请先核实之前的化验保存结果。')
      // One key per operation avoids lost updates across tabs; never rewrite another pending reference.
      write(entryKey(owner, row), { sessionId: row.sessionId, previewRef: row.previewRef })
    },
    forget: (owner, row) => {
      if (!valid(row)) throw new Error('确认引用无效')
      storage.removeItem(entryKey(owner, row))
      const old = legacy(owner)
      if (old.some(item => item.previewRef === row.previewRef && item.sessionId === row.sessionId))
        write(key(owner), old.filter(item => item.previewRef !== row.previewRef || item.sessionId !== row.sessionId))
      changed()
    }
  }
}

export const browserAssayRecoveryJournal = () => createAssayRecoveryJournal(window.localStorage,
  () => window.dispatchEvent(new Event('assay-entry-recovery-changed')))

import { isSavedAssayResult } from './assayEntryResult.mjs'

export const isSettledAssayResult = result => result?.saved === true
  ? isSavedAssayResult(result)
  : result?.saved === false && ['已取消', '已失效', '已过期'].includes(result.statusLabel)
