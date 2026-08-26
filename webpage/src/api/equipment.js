import request from '@/utils/request.js'

export const queryEquipmentAssets = data => request.post('/equipment/assets/query', data)
export const getEquipmentAsset = id => request.get(`/equipment/assets/${id}`)
export const getEquipmentAssetOptions = keyword => request.get('/equipment/assets/options', { params: { keyword } })
export const createEquipmentAsset = data => request.post('/equipment/assets', data)
export const updateEquipmentAsset = (id, data) => request.put(`/equipment/assets/${id}`, data)
export const deleteEquipmentAsset = id => request.delete(`/equipment/assets/${id}`)
export const exportEquipmentAssets = data => request.post('/equipment/assets/export', data, { responseType: 'blob' })

export const queryEquipmentRepairs = data => request.post('/equipment/repairs/query', data)
export const getEquipmentRepair = id => request.get(`/equipment/repairs/${id}`)
export const createEquipmentRepair = data => request.post('/equipment/repairs', data)
export const updateEquipmentRepair = (id, data) => request.put(`/equipment/repairs/${id}`, data)
export const deleteEquipmentRepair = id => request.delete(`/equipment/repairs/${id}`)
export const exportEquipmentRepairs = data => request.post('/equipment/repairs/export', data, { responseType: 'blob' })

export const queryEquipmentBasicData = (resource, data) => request.post(`/equipment/${resource}/query`, data)
export const getEquipmentBasicOptions = resource => request.get(`/equipment/${resource}/options`)
export const createEquipmentBasicData = (resource, data) => request.post(`/equipment/${resource}`, data)
export const updateEquipmentBasicData = (resource, id, data) => request.put(`/equipment/${resource}/${id}`, data)
export const setEquipmentBasicEnabled = (resource, id, enabled) => request.put(`/equipment/${resource}/${id}/enabled`, { enabled })
export const deleteEquipmentBasicData = (resource, id) => request.delete(`/equipment/${resource}/${id}`)
