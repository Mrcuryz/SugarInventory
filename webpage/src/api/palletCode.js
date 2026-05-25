import request from '@/utils/request.js'

export const pagePalletCodes = (params) => request.post('/pallet-codes', params)

export const generatePalletCodes = (params) => request.post('/pallet-codes/generate', params)

export const bindFixedProductCodes = (params) => request.post('/pallet-codes/fixed-product/bind', params)

export const pageFixedProductPool = (params) => request.get('/pallet-codes/fixed-product/pool', {params})

export const batchDownloadFixedProductQrLabelPdf = (codes) => {
  return request.post('/pallet-codes/fixed-product/qrcode-labels/pdf', {codes}, {responseType: 'blob'})
}

export const activateFixedProductQrCodes = (params) => {
  return request.post('/pallet-codes/fixed-product/activate/pdf', params, {responseType: 'blob'})
}

export const getPalletQrCode = (code) => {
  return request.get(`/pallet-codes/${encodeURIComponent(code)}/qrcode`, {responseType: 'blob'})
}

export const downloadPalletQrPng = (code) => {
  return request.get(`/pallet-codes/${encodeURIComponent(code)}/qrcode.png`, {responseType: 'blob'})
}

export const downloadPalletQrSvg = (code) => {
  return request.get(`/pallet-codes/${encodeURIComponent(code)}/qrcode.svg`, {responseType: 'blob'})
}

export const downloadPalletQrLabelPdf = (code) => {
  return request.get(`/pallet-codes/${encodeURIComponent(code)}/qrcode-label.pdf`, {responseType: 'blob'})
}

export const batchDownloadPalletQrLabelPdf = (codes) => {
  return request.post('/pallet-codes/qrcode-labels/pdf', {codes}, {responseType: 'blob'})
}

export const invalidatePalletCodes = (params) => request.post('/pallet-codes/invalid', params)

export const restoreInvalidPalletCodes = (params) => request.post('/pallet-codes/invalid/restore', params)

export const parsePalletCode = (code) => request.get('/pallet-codes/parse', {params: {code}})

export const getPalletAssay = (code) => request.get(`/pallet-codes/${encodeURIComponent(code)}/assay`)

export const getPalletInventory = (code) => request.get(`/pallet-codes/${encodeURIComponent(code)}/inventory`)

export const bindPalletTask = (params) => request.post('/pallet-codes/bind', params)

export const pagePalletTasks = (params) => request.post('/pallet-codes/tasks/list', params)

export const bindSemiItemsToTask = () => Promise.reject(new Error('成品与半成品追溯请通过生产订单关联'))

export const confirmPalletInBatch = (params) => request.post('/pallet-codes/tasks/confirm', params)

export const cancelPalletTasks = (params) => request.post('/pallet-codes/tasks/cancel', params)

export const createSemiOutTasks = (params) => request.post('/pallet-codes/semi/out/create', params)

export const confirmSemiOutTasks = (params) => request.post('/pallet-codes/semi/out/confirm', params)

export const createSemiPrepareTasks = () => Promise.reject(new Error('半成品进入生产请通过生产订单领用'))

export const confirmSemiPrepareTasks = () => Promise.reject(new Error('历史生产占用任务仅保留查询，不再允许确认'))

export const createFinishOutTasks = (params) => request.post('/pallet-codes/finish/out/create', params)

export const confirmFinishOutTasks = (params) => request.post('/pallet-codes/finish/out/confirm', params)

export const createTransferTasks = (params) => request.post('/pallet-codes/transfer/create', params)

export const confirmTransferTasks = (params) => request.post('/pallet-codes/transfer/confirm', params)

export const createWarehouseMapTasks = (params) => request.post('/pallet-codes/warehouse-map/tasks/create', params)

export const createWarehouseMapSlotInbound = (params) => request.post('/pallet-codes/warehouse-map/slot/inbound', params)

export const pagePalletFlowCycles = (code, params) => {
  return request.get(`/pallet-codes/${encodeURIComponent(code)}/flows/cycles`, {params})
}

export const listPalletFlowsByCycle = (code, cycleNo) => {
  return request.get(`/pallet-codes/${encodeURIComponent(code)}/flows`, {params: {cycleNo}})
}

export const deletePalletFlows = (params) => request.post('/pallet-codes/flows/delete', params)
