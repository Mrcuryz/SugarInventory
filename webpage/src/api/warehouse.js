import request from '@/utils/request.js'

//查询库位信息
export const getWarehouse = (params) => {
    return request.get('/warehouse/query', {params: params})
}

//新增库位信息
export const addWarehouse = (params) => {
    return request.post('/warehouse/create', params)
}

//删除库位信息
export const removeWarehouse = (id) => {
    return request.delete('/warehouse/delete/' + id)
}

//修改库位信息
export const changeWarehouse = (params) => {
    return request.put('/warehouse/update', params)
}

//修改库位状态
export const changeWarehouseStatus = (id) => {
    return request.put('/warehouse/maintain/' + id)
}

export const getWarehouseLocationCodes = (warehouseIds) => {
    return request.post('/warehouse/location-codes/query', {warehouseIds})
}

export const downloadWarehouseLocationQrPng = (warehouseId) => {
    return request.get(`/warehouse/${warehouseId}/location-qrcode.png`, {responseType: 'blob'})
}

export const downloadWarehouseLocationLabelPdf = (warehouseId) => {
    return request.get(`/warehouse/${warehouseId}/location-qrcode-label.pdf`, {responseType: 'blob'})
}

export const batchDownloadWarehouseLocationLabelsPdf = (warehouseIds) => {
    return request.post('/warehouse/location-qrcode-labels/pdf', {warehouseIds}, {responseType: 'blob'})
}
