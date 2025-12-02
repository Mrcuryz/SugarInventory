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