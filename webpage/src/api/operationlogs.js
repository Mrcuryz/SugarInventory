import request from '@/utils/request.js'

//提供调用登录接口的函数
export const getOperationLogs = (params) => {
    return request.post('/logs/query', params)
}