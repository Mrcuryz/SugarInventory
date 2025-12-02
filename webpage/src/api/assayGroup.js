import request from '@/utils/request.js'

//查询化验记录
export const getAssayGroup = (params) => {
    return request.post('/assayGroup/query', params)
}
//新增化验记录（JSON数组）
export const addAssayGroup = (params) => {
    return request.post('/assayGroup/add', params)
}
//更新化验记录
export const updateAssayGroup = (id, params) => {
    return request.post('/assayGroup/' + id, params)
}
//删除化验记录
export const deleteAssayGroup = (id) => {
    return request.delete('/assayGroup/' + id)
}
