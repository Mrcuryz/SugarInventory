import { createRouter, createWebHistory } from 'vue-router'

//导入组件
import MenuVue from '../components/Menu.vue'

//定义路由关系
const routes = [
    { path: '/login', component: () => import('@/components/Login.vue')},
    { path: '/', component: MenuVue, redirect:'/home', children: [
            { path: 'home', meta: { title: '首页' }, component: () => import('@/components/Home.vue')},
            { path: 'autoInbound', meta: { title: '自动入库' }, component: () => import('@/components/AutoInbound.vue')},
            { path: 'operationlogs', meta: { title: '操作日志' }, component: () => import('@/components/OperationLogs.vue')},
            { path: 'product', meta: { title: '产品管理' }, component: () => import('@/components/Product.vue')},
            { path: 'productStock', meta: { title: '产品库存' }, component: () => import('@/components/ProductStock.vue')},
            { path: 'warehouse', meta: { title: '库位管理' }, component: () => import('@/components/Warehouse.vue')},
            { path: 'screenMesh', meta: { title: '筛网管理' }, component: () => import('@/components/ScreenMesh.vue')},
            { path: 'assay', meta: { title: '化验管理 ' }, component: () => import('@/components/Assay.vue')},
            { path: 'assayGroup', meta: { title: '验收标准' }, component: () => import('@/components/AssayGroup.vue')},
            { path: 'standard', meta: { title: '质量标准管理' }, component: () => import('@/components/Standard.vue')},
            { path: 'stock', meta: { title: '出入库管理' }, component: () => import('@/components/Stock.vue')},
            { path: 'employee', meta: { title: '员工管理' }, component: () => import('@/components/Employee.vue')},
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
