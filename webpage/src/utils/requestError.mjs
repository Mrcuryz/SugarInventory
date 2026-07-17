export const resolveResponseMessage = (data, fallback = '服务异常，请稍后重试') => {
  const value = typeof data === 'string' ? data : data?.msg ?? data?.message
  return typeof value === 'string' && value.trim() ? value : fallback
}

export const isJsonParseErrorMessage = message =>
  typeof message === 'string' && message.includes('JSON parse error')
