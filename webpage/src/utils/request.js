//定制请求的实例
//导入axios  npm install axios
import axios from 'axios';

import {ElMessage} from 'element-plus'
import {isJsonParseErrorMessage, resolveResponseMessage} from './requestError.mjs'
//定义一个变量,记录公共的前缀  ,  baseURL
// const baseURL = 'http://localhost:8080/api';
const baseURL = '/api';
const instance = axios.create({baseURL})

import {useTokenStore} from '@/stores/token.js'

const redirectToLogin = () => {
    try {
        localStorage.removeItem('token')
        localStorage.removeItem('auth')
    } catch (error) {
        // ignore storage cleanup errors
    }
    if (window.location.pathname !== '/login') {
        window.location.replace('/login')
    }
}

//添加请求拦截器
instance.interceptors.request.use(
    (config) => {
        //请求前的回调
        //添加token
        const tokenStore = useTokenStore();
        //判断有没有token
        if (tokenStore.token) {
            let Bearer = 'Bearer '
            config.headers.Authorization = Bearer + tokenStore.token
        }
        return config;
    },
    (err) => {
        if (err?.response?.status === 401) {
            ElMessage.error('认证失败,请重新登录')
            redirectToLogin()
        }
        //请求错误的回调
        return Promise.reject(err)
    }
)
//添加响应拦截器
instance.interceptors.response.use(
    result => {
        if (result.config.responseType === 'blob') {
            return result;
        }
        //判断业务状态码
        if (result.data.code === 200) {
            return result.data;
        }
        //操作失败
        //JSON parse error
        const responseMessage = resolveResponseMessage(result.data)
        if (result.data?.code === 500 && isJsonParseErrorMessage(responseMessage)) {
            ElMessage.error('请检查输入参数是否正确')
            return Promise.reject({...result.data, msg: '请检查输入参数是否正确'})
        }
        ElMessage.error(responseMessage)
        //异步操作的状态转换为失败
        const errorPayload = result.data && typeof result.data === 'object' ? result.data : {}
        return Promise.reject({...errorPayload, msg: responseMessage})

    },
    err => {
        //判断响应状态码,如果为401,则证明未登录,提示请登录,并跳转到登录页面
        if (err?.response?.status === 401) {
            const tokenStore = useTokenStore()
            tokenStore.removeToken()
            ElMessage.error(err.response.data || '登录状态已失效，请重新登录')
            redirectToLogin()
        } else if (err?.response?.status) {
            const data = err.response.data
            const message = resolveResponseMessage(data, `请求失败：${err.response.status}`)
            ElMessage.error(message)
        }
        return Promise.reject(err);//异步的状态转化成失败的状态
    }
)

export default instance;
