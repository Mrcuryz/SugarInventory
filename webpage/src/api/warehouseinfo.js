import request from '@/utils/request.js'

//根据仓库ID获取仓库信息
export const getWarehouseInfo = (params) => {
    return request.post('/inventory/summary', params)
}

//获得所有仓库信息
export const getAllWarehouseCapacity = () => {
    return request.get('/inventory/warehouses')
}

//获取符合条件的仓库列表
export const getWarehouseList = (params) => {
    return request.post('/inventory/qualified-warehouses', params)
}

//根据id获取单个仓库
export const getWarehouseById = (id, params) => {
    return request.post('/inventory/qualified-inventory/' + id, params)
}

export const getWarehouseInventoryPage = (id, params) => {
    return request.post('/inventory/qualified-inventory/' + id + '/page', params)
}

//查询产品库存
export const getProductStock = (params) => {
    return request.get('/inventory/stock', {params: params})
}

//根据id查询仓库最大行数
export const getMaxRowNum = (id) => {
    return request.get('/warehouse/' + id)
}
