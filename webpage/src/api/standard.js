import request from '@/utils/request.js'

//查询标准列表
export const getStandard = (params)=>{
    return request.get('/quality-standards/list', {params: params})
}
//新增标准
export const addStandard = (params)=>{
    return request.post('/quality-standards/add', params)
}
//删除标准
export const deleteStandard = (id)=>{
    return request.delete('/quality-standards/delete/'+id)
}
//修改标准
export const updateStandard = (id,params)=>{
    return request.put('/quality-standards/update/'+id, params)
}