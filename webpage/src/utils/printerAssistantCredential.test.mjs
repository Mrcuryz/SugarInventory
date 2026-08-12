import assert from 'node:assert/strict'
import test from 'node:test'

import {
  clearPrinterAssistantKey,
  PRINTER_ASSISTANT_KEY_STORAGE,
  readPrinterAssistantKey,
  savePrinterAssistantKey
} from './printerAssistantCredential.mjs'

const createStorage = () => {
  const values = new Map()
  return {
    getItem: (key) => values.get(key) ?? null,
    setItem: (key, value) => values.set(key, value),
    removeItem: (key) => values.delete(key),
    values
  }
}

test('printer assistant key stays in supplied session storage and is normalized', () => {
  const storage = createStorage()
  const key = '  printer-assistant-uat-key-123456  '

  assert.equal(savePrinterAssistantKey(key, storage), key.trim())
  assert.equal(readPrinterAssistantKey(storage), key.trim())
  assert.equal(storage.values.get(PRINTER_ASSISTANT_KEY_STORAGE), key.trim())
})

test('short keys are rejected and clearing removes the session credential', () => {
  const storage = createStorage()

  assert.throws(() => savePrinterAssistantKey('too-short', storage), /至少需要 24 个字符/)
  savePrinterAssistantKey('printer-assistant-uat-key-123456', storage)
  clearPrinterAssistantKey(storage)
  assert.equal(readPrinterAssistantKey(storage), '')
})
