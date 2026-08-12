import {
  clearPrinterAssistantKey,
  readPrinterAssistantKey,
  savePrinterAssistantKey
} from '@/utils/printerAssistantCredential.mjs'

const ASSISTANT_BASE_URL = 'http://127.0.0.1:9527'
const DEFAULT_TIMEOUT = 5000
const ACCESS_KEY_HEADER = 'X-Laibin-Printer-Key'

const requestAssistant = async (path, options = {}) => {
  const controller = new AbortController()
  const timeout = options.timeout ?? DEFAULT_TIMEOUT
  const timer = window.setTimeout(() => controller.abort(), timeout)

  try {
    const accessKey = options.auth === false ? '' : readPrinterAssistantKey()
    if (options.auth !== false && !accessKey) {
      throw new Error('请先在打印助手设置中输入本机连接密钥')
    }
    const response = await fetch(`${ASSISTANT_BASE_URL}${path}`, {
      method: options.method || 'GET',
      headers: {
        ...(options.body ? {'Content-Type': 'application/json'} : {}),
        ...(accessKey ? {[ACCESS_KEY_HEADER]: accessKey} : {})
      },
      body: options.body ? JSON.stringify(options.body) : undefined,
      signal: controller.signal
    })

    const rawText = await response.text()
    const payload = rawText ? JSON.parse(rawText) : null
    if (response.status === 401) {
      throw new Error('打印助手连接密钥无效，请从本机助手重新复制')
    }
    if (!response.ok || payload?.success === false) {
      const error = new Error(payload?.message || `标签打印助手请求失败（${response.status}）`)
      error.status = response.status
      error.payload = payload
      throw error
    }
    return payload
  } catch (error) {
    if (error.name === 'AbortError') {
      throw new Error('连接标签打印助手超时，请确认本地服务已启动')
    }
    if (error instanceof TypeError) {
      throw new Error('未检测到标签打印助手，请先在本机启动后重试')
    }
    if (error instanceof SyntaxError) {
      throw new Error('标签打印助手返回了无法识别的数据')
    }
    throw error
  } finally {
    window.clearTimeout(timer)
  }
}

export const getPrinterAssistantHealth = () => requestAssistant('/health', {timeout: 2500, auth: false})

export const getPrinterAssistantAccessKey = () => readPrinterAssistantKey()

export const setPrinterAssistantAccessKey = (accessKey) => savePrinterAssistantKey(accessKey)

export const removePrinterAssistantAccessKey = () => clearPrinterAssistantKey()

export const getLocalPrinters = () => requestAssistant('/printers')

export const getLocalPrinterConfig = () => requestAssistant('/config')

export const saveDefaultLocalPrinter = (printerName) => requestAssistant('/config/default-printer', {
  method: 'POST',
  body: {printerName}
})

export const printTestLabel = (params = {}) => requestAssistant('/print/test', {
  method: 'POST',
  body: params
})

export const printLocalLabels = (params) => requestAssistant('/print', {
  method: 'POST',
  body: params,
  timeout: 15000
})

export const buildAssistantErrorMessage = (error) => {
  if (!error) {
    return '标签打印助手调用失败'
  }
  return error.message || error.payload?.message || '标签打印助手调用失败'
}
