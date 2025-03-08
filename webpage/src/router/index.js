import { createRouter, createWebHistory } from 'vue-router'

//导入组件
import MenuVue from '../components/Menu.vue'

//定义路由关系
const routes = [
    { path: '/login', component: () => import('@/components/Login.vue')},
    { path: '/', component: MenuVue, redirect:'/home', children: [
            { path: 'home', meta: { title: '首页' }, component: () => import('@/components/Home.vue')},
            { path: 'operationlogs', meta: { title: '操作日志' }, component: () => import('@/components/OperationLogs.vue')}
        ]
    }

]

//创建路由器
const router = createRouter({
    history: createWebHistory(),
    routes: routes
})

//导出路由
export default router
