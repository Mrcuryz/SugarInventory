import request from '@/utils/request.js'

//根据仓库ID获取仓库信息
export const getWarehouseInfo = (params)=>{
    return request.post('/inventory/summary', params)
}

//获得所有仓库信息
export const getAllWarehouseCapacity = ()=>{
    return request.get('/inventory/warehouses')
}