import request from '@/utils/request.js'

//查询
export const getInStock = (params) => {
    return request.post('/in-stock/query', params)
}

export const getOutStock = (params) => {
    return request.post('/out-stock/records', params)
}

//新增
export const addInStock = (params) => {
    return request.post('/in-stock/add', params)
}

export const addOutStock = (params) => {
    return request.post('/out-stock/out', params)
}

//查询半成品入库
export const getSemiProductList = (params) => {
    return request.post('/semi-products/records', params)
}
//新增半成品入库
export const addSemiProduct = (params) => {
    return request.post('/semi-products/add', params)
}
//查询是否化验
export const getCheck = (params) => {
    return request.post('/assay/exists', params)
}

export const addOutStack = (params) => {
    return request.post('/out-stock/stack-out', params)
}
export const addTransferOut = (params) => {
    return request.post('/out-stock/transferOut', params)
}
export const addSemiProductStack = (params) => {
    return request.post('/semi-products/stack-in', params)
}


