import request from '@/utils/request.js'

export const listProductStandardRelations = (productId) => {
  return request.get(`/product-quality-standards/products/${productId}`)
}

export const bindProductStandardRelation = (params) => {
  return request.post('/product-quality-standards/bind', params)
}

export const updateProductStandardRelation = (id, params) => {
  return request.put(`/product-quality-standards/${id}`, params)
}

export const setDefaultProductStandardRelation = (id) => {
  return request.post(`/product-quality-standards/${id}/default`)
}

export const deleteProductStandardRelation = (id) => {
  return request.delete(`/product-quality-standards/${id}`)
}
