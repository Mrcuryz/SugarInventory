import request from '@/utils/request'

const path = sessionId => `/agent/sessions/${encodeURIComponent(sessionId)}/product-import`
const uploadBody = (file, sheet, mapping) => {
  const body = new FormData()
  body.append('file', file)
  if (sheet) body.append('sheet', sheet)
  if (mapping) body.append('mapping', JSON.stringify(mapping))
  return body
}
export const getProductImportCapabilities = sessionId => request.get(`${path(sessionId)}/capabilities`)
export const productImportApi = sessionId => ({
  inspect: file => request.post(`${path(sessionId)}/inspect`, uploadBody(file), { timeout: 30000 }).then(r => r.data),
  upload: (file, sheet, mapping) => request.post(`${path(sessionId)}/drafts`, uploadBody(file, sheet, mapping), { timeout: 30000 }).then(r => r.data),
  list: () => request.get(`${path(sessionId)}/drafts`, { timeout: 15000 }).then(r => r.data),
  read: (ref, page) => request.get(`${path(sessionId)}/drafts/${encodeURIComponent(ref)}`, { params: { page, pageSize: 50 }, timeout: 15000 }).then(r => r.data),
  remove: ref => request.delete(`${path(sessionId)}/drafts/${encodeURIComponent(ref)}`, { timeout: 15000 }).then(r => r.data)
})
