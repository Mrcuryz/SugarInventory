import request from '@/utils/request.js'

//查询产品列表
export const getProductList = (params) => {
    return request.get('/products/product', {params: params})
}

//新增产品
export const addProduct = (params) => {
    return request.post('/products', params)
}

//删除
export const removeProduct = (id) => {
    return request.delete('/products/' + id)
}

//修改
export const changeProduct = (params) => {
    return request.put('/products', params)
}