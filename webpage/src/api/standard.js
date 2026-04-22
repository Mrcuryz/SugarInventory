import request from '@/utils/request.js'

export const getStandard = (params) => {
  return request.get('/quality-standards/list', { params })
}

export const getStandardPage = (params) => {
  return request.get('/quality-standards/list/page', { params })
}

export const getStandardDetail = (id) => {
  return request.get('/quality-standards/' + id)
}

export const addStandard = (params) => {
  return request.post('/quality-standards/add', params)
}

export const deleteStandard = (id) => {
  return request.delete('/quality-standards/delete/' + id)
}

export const forceDeleteStandard = (id) => {
  return request.delete('/quality-standards/delete/' + id + '/force')
}

export const updateStandard = (id, params) => {
  return request.put('/quality-standards/update/' + id, params)
}
