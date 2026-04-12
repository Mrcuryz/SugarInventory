import {createRouter, createWebHistory} from 'vue-router'

//导入组件
import MenuVue from '../components/Menu.vue'

//定义路由关系
const routes = [
    {path: '/login', name: 'Login', meta: {hiddenTab: true}, component: () => import('@/components/Login.vue')},
    {
        path: '/', component: MenuVue, redirect: '/home', children: [
            {path: 'home', name: 'Home', meta: {title: '首页', keepAlive: true}, component: () => import('@/components/Home.vue')},
            {path: 'autoInbound', name: 'AutoInbound', meta: {title: '自动入库', keepAlive: true}, component: () => import('@/components/AutoInbound.vue')},
            {
                path: 'operationlogs',
                name: 'OperationLogs',
                meta: {title: '操作日志', keepAlive: true},
                component: () => import('@/components/OperationLogs.vue')
            },
            {path: 'product', name: 'Product', meta: {title: '产品管理', keepAlive: true}, component: () => import('@/components/Product.vue')},
            {path: 'productStock', name: 'ProductStock', meta: {title: '产品库存', keepAlive: true}, component: () => import('@/components/ProductStock.vue')},
            {path: 'warehouse', name: 'Warehouse', meta: {title: '库位管理', keepAlive: true}, component: () => import('@/components/Warehouse.vue')},
            {path: 'warehouse-map', name: 'WarehouseMap', meta: {title: '仓库平面图', keepAlive: true}, component: () => import('@/components/WarehouseMap.vue')},
            {path: 'pallet-code/list', name: 'PalletCodeList', meta: {title: '托盘码管理', keepAlive: true}, component: () => import('@/components/PalletCodeList.vue')},
            {path: 'pallet-code/task-center', redirect: '/pallet-task/overview'},
            {path: 'pallet-task/overview', name: 'PalletTaskOverview', meta: {title: '任务中心', keepAlive: true}, component: () => import('@/components/PalletTaskOverview.vue')},
            {path: 'pallet-task/semi/in', name: 'SemiInTaskPage', meta: {title: '半成品入库任务', keepAlive: true}, component: () => import('@/components/SemiInTaskPage.vue')},
            {path: 'pallet-task/semi/out', name: 'SemiOutTaskPage', meta: {title: '半成品出库任务', keepAlive: true}, component: () => import('@/components/SemiOutTaskPage.vue')},
            {path: 'pallet-task/finish/in', name: 'FinishInTaskPage', meta: {title: '成品入库任务', keepAlive: true}, component: () => import('@/components/FinishInTaskPage.vue')},
            {path: 'pallet-task/finish/out', name: 'FinishOutTaskPage', meta: {title: '成品出库任务', keepAlive: true}, component: () => import('@/components/FinishOutTaskPage.vue')},
            {path: 'pallet-task/transfer', name: 'TransferTaskPage', meta: {title: '调拨任务', keepAlive: true}, component: () => import('@/components/TransferTaskPage.vue')},
            {path: 'screenMesh', name: 'ScreenMesh', meta: {title: '筛网管理'}, component: () => import('@/components/ScreenMesh.vue')},
            {path: 'assay', name: 'Assay', meta: {title: '化验管理', keepAlive: true}, component: () => import('@/components/Assay.vue')},
            {path: 'assayGroup', name: 'AssayGroup', meta: {title: '验收标准', keepAlive: true}, component: () => import('@/components/AssayGroup.vue')},
            {path: 'standard', name: 'Standard', meta: {title: '质量标准管理', keepAlive: true}, component: () => import('@/components/Standard.vue')},
            {path: 'stock', name: 'Stock', meta: {title: '出入库管理', keepAlive: true}, component: () => import('@/components/Stock.vue')},
            {path: 'employee', name: 'Employee', meta: {title: '员工管理'}, component: () => import('@/components/Employee.vue')},
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
