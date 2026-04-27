const ASSISTANT_BASE_URL = 'http://127.0.0.1:9527'
const DEFAULT_TIMEOUT = 5000

const requestAssistant = async (path, options = {}) => {
  const controller = new AbortController()
  const timeout = options.timeout ?? DEFAULT_TIMEOUT
  const timer = window.setTimeout(() => controller.abort(), timeout)

  try {
    const response = await fetch(`${ASSISTANT_BASE_URL}${path}`, {
      method: options.method || 'GET',
      headers: options.body ? {'Content-Type': 'application/json'} : undefined,
      body: options.body ? JSON.stringify(options.body) : undefined,
      signal: controller.signal
    })

    const rawText = await response.text()
    const payload = rawText ? JSON.parse(rawText) : null
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

export const getPrinterAssistantHealth = () => requestAssistant('/health', {timeout: 2500})

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
