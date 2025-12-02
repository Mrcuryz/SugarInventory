import request from '@/utils/request.js'

//查询筛网
export const getMesh = (params) => {
    return request.get('/screen-mesh/list', {params: params})
}

//新增筛网
export const addMesh = (params) => {
    return request.post('/screen-mesh/add', params)
}
//删除筛网
export const deleteMesh = (id) => {
    return request.delete('/screen-mesh/delete/' + id)
}
//修改筛网
export const updateMesh = (params) => {
    return request.put('/screen-mesh/update', params)
}