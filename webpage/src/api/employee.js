import request from '@/utils/request.js'

//查询员工列表
export const getEmployeeList = (params)=>{
    return request.post('/employee/query',params)
}

//删除员工
export const deleteEmployee = (params)=>{
    return request.delete('/employee/clearResigned',params)
}
//修改员工
export const updateEmployee = (params)=>{
    return request.put('/employee/update',params)
}