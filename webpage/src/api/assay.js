import request from '@/utils/request.js'

//查询化验记录
export const getAssay = (params)=>{
    return request.post('/assay/query',params)
}
//新增化验记录（JSON数组）
export const addAssay = ([params])=>{
    return request.post('/assay/import',[params])
}
//更新化验记录
export const updateAssay = (id,params)=>{
    return request.post('/assay/'+id,params)
}
//查询成品记录
export const getStProduct = ()=>{
    return request.get('/products/finished-product-names')
}
//查询半成品记录
export const getSemiProduct = ()=>{
    return request.get('/products/semi-product-names')
}