import request from '@/utils/request.js'

export const pageProductionOrders = params => request.get('/production/orders', {params})

export const createProductionOrder = params => request.post('/production/orders', params)

export const getProductionOrderDetail = id => request.get(`/production/orders/${id}`)

export const deleteProductionOrder = id => request.delete(`/production/orders/${id}`)

export const cancelProductionOrder = id => request.post(`/production/orders/${id}/cancel`, {})

export const getProductionOrderTrace = id => request.get(`/production/orders/${id}/trace`)

export const listProductionOrderOptions = params => request.get('/production/orders/options', {params})

export const pageMaterialCandidates = (orderId, params) => {
  return request.get(`/production/orders/${orderId}/material-candidates`, {params})
}

export const pageProductionInProcessMaterials = params => {
  return request.get('/production/orders/materials/in-process', {params})
}

export const pickProductionMaterials = (orderId, params) => {
  return request.post(`/production/orders/${orderId}/materials/pick`, params)
}

export const finishProductionMaterials = orderId => {
  return request.post(`/production/orders/${orderId}/materials/finish`, {})
}

export const addProductionOutput = (orderId, params) => {
  return request.post(`/production/orders/${orderId}/outputs`, params)
}

export const updateProductionOutput = (outputId, params) => {
  return request.put(`/production/orders/outputs/${outputId}`, params)
}

export const deleteProductionOutput = outputId => {
  return request.delete(`/production/orders/outputs/${outputId}`)
}

export const bindProductionOutputQrs = (outputId, params) => {
  return request.post(`/production/orders/outputs/${outputId}/bind-fixed-qrs`, params)
}

export const markProductionOutputPrinted = outputId => {
  return request.post(`/production/orders/outputs/${outputId}/print-codes`, {})
}

export const reserveProductionLabels = (orderId, params) => {
  return request.post(`/production/orders/${orderId}/label-batches`, params)
}

export const listProductionLabelBatches = orderId => {
  return request.get(`/production/orders/${orderId}/label-batches`)
}

export const printProductionLabelBatch = batchId => {
  return request.post(`/production/orders/label-batches/${batchId}/print`, {}, {responseType: 'blob'})
}
export const getProductionLabelQrCode = labelCodeId => {
  return request.get(`/production/orders/label-codes/${labelCodeId}/qrcode`, {responseType: 'blob'})
}

export const finishProductionOrder = (orderId, params) => {
  return request.post(`/production/orders/${orderId}/production-finish`, params)
}

export const pageBoilingBatches = params => request.get('/production/boiling-batches', {params})

export const getBoilingBatchDetail = id => request.get(`/production/boiling-batches/${id}`)

export const createBoilingBatch = params => request.post('/production/boiling-batches', params)

export const updateBoilingBatch = (id, params) => request.put(`/production/boiling-batches/${id}`, params)

export const cancelBoilingBatch = id => request.post(`/production/boiling-batches/${id}/cancel`, {})

export const getBoilingBatchTrace = id => request.get(`/production/boiling-batches/${id}/trace`)

export const getBoilingBatchTraceGraph = id => request.get(`/production/boiling-batches/${id}/graph`)

export const getProductionDailyReport = reportDate => {
  return request.get(`/production/daily-reports/${reportDate}`)
}

export const pageProductionDailyReports = params => {
  return request.get('/production/daily-reports', { params })
}

export const listProductionDailyReportProducts = params => {
  return request.get('/production/daily-reports/product-options', { params })
}

export const saveProductionDailyReportHeader = (reportDate, params) => {
  return request.put(`/production/daily-reports/${reportDate}/header`, params)
}

export const saveProductionDailyReportSection = (reportDate, departmentCode, params) => {
  return request.put(`/production/daily-reports/${reportDate}/sections/${departmentCode}`, params)
}

export const submitProductionDailyReportSection = (reportDate, departmentCode) => {
  return request.post(`/production/daily-reports/${reportDate}/sections/${departmentCode}/submit`, {})
}

export const submitProductionDailyReport = reportDate => {
  return request.post(`/production/daily-reports/${reportDate}/submit`, {})
}

export const exportProductionDailyReport = reportDate => {
  return request.get(`/production/daily-reports/${reportDate}/export`, { responseType: 'blob' })
}

export const importProductionDailyReport = formData => {
  return request.post('/production/daily-reports/import', formData)
}

export const confirmProductionDailyReportImport = params => {
  return request.post('/production/daily-reports/import/confirm', params)
}
