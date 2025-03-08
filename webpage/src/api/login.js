import request from '@/utils/request.js'

//提供调用登录接口的函数
export const login = (loginData)=>{
    return request.post('/auth/web-login',loginData)
}