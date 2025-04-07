import request from '@/utils/request.js'

//查询
export const getInStock = (params)=>{
    return request.post('/in-stock/query', params)
}

export const getOutStock = (params)=>{
    return request.post('/out-stock/records', params)
}

//新增
export const addInStock = (params)=>{
    return request.post('/in-stock/add', params)
}

export const addOutStock = (params)=>{
    return request.post('/out-stock/out', params)
}

//查询半成品入库
export const getSemiProductList = (params)=>{
    return request.post('/semi-products/records', params)
}



