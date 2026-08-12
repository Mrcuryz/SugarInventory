export const PRINTER_ASSISTANT_KEY_STORAGE = 'laibin-printer-assistant-key'

const normalizeKey = (value) => typeof value === 'string' ? value.trim() : ''

export const readPrinterAssistantKey = (storage = globalThis.sessionStorage) => {
  try {
    return normalizeKey(storage?.getItem(PRINTER_ASSISTANT_KEY_STORAGE))
  } catch (_) {
    return ''
  }
}

export const savePrinterAssistantKey = (value, storage = globalThis.sessionStorage) => {
  const normalized = normalizeKey(value)
  if (normalized.length < 24) {
    throw new Error('打印助手连接密钥至少需要 24 个字符')
  }
  storage?.setItem(PRINTER_ASSISTANT_KEY_STORAGE, normalized)
  return normalized
}

export const clearPrinterAssistantKey = (storage = globalThis.sessionStorage) => {
  try {
    storage?.removeItem(PRINTER_ASSISTANT_KEY_STORAGE)
  } catch (_) {
    // Session storage may be unavailable in restricted browser contexts.
  }
}
