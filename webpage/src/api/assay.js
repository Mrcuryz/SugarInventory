import request from '@/utils/request.js'

export const getAssay = (params) => {
  return request.post('/assay/query', params)
}

export const getAssayDetail = (id) => {
  return request.get('/assay/' + id)
}

export const getAssayByProductDate = (params) => {
  return request.get('/assay/by-product-date', { params })
}

export const addAssay = ([params]) => {
  return request.post('/assay/import', [params])
}

export const updateAssay = (id, params) => {
  return request.post('/assay/' + id, params)
}

export const getStProduct = () => {
  return request.get('/products/finished-product-names')
}

export const getSemiProduct = () => {
  return request.get('/products/semi-product-names')
}

export const getProductWarehouse = (id) => {
  return request.get('/products/getProductWarehouse/' + id)
}

export const deleteAssay = (id) => {
  return request.delete('/assay/' + id)
}
