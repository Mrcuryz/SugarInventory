import { createRouter, createWebHistory } from 'vue-router'
import { useTokenStore } from '@/stores/token'
import { useAuthStore } from '@/stores/auth'
import { findFirstAccessiblePath } from '@/utils/navigation'

import MenuVue from '../components/Menu.vue'

const routes = [
  { path: '/login', name: 'Login', meta: { hiddenTab: true }, component: () => import('@/components/LoginModern.vue') },
  {
    path: '/',
    component: MenuVue,
    redirect: '/home',
    children: [
      { path: 'home', name: 'Home', meta: { title: '首页', keepAlive: true, permCode: 'dashboard:view' }, component: () => import('@/components/Home.vue') },
      {
        path: 'operationlogs',
        name: 'OperationLogs',
        meta: { title: '操作日志', keepAlive: true, permCode: 'log:view' },
        component: () => import('@/components/OperationLogs.vue')
      },
      { path: 'product', name: 'Product', meta: { title: '产品管理', keepAlive: true, permCode: 'product:view' }, component: () => import('@/components/Product.vue') },
      { path: 'productStock', name: 'ProductStock', meta: { title: '库存汇总', keepAlive: true, permCode: 'inventory:view' }, component: () => import('@/components/ProductStock.vue') },
      { path: 'auto-inbound', name: 'AutoInbound', meta: { title: '智能报数处理', keepAlive: true, permCode: 'inventory:view' }, component: () => import('@/components/AutoInbound.vue') },
      { path: 'warehouse', name: 'Warehouse', meta: { title: '库位管理', keepAlive: true, permCode: 'warehouse:view' }, component: () => import('@/components/WareHouse.vue') },
      { path: 'warehouse-map', name: 'WarehouseMap', meta: { title: '仓库平面图', keepAlive: true, permCode: 'warehouse_map:view' }, component: () => import('@/components/WarehouseMap.vue') },
      { path: 'pallet-code/list', name: 'PalletCodeList', meta: { title: '二维码管理', keepAlive: true, permCode: 'qrcode:view' }, component: () => import('@/components/PalletCodeList.vue') },
      { path: 'pallet-code/fixed-product', name: 'FixedProductQrPool', meta: { title: '固定产品二维码池', keepAlive: true, permCode: 'qrcode:pool_view' }, component: () => import('@/components/FixedProductQrPool.vue') },
      { path: 'pallet-code/task-center', redirect: '/pallet-task/overview' },
      { path: 'pallet-task/overview', name: 'PalletTaskOverview', meta: { title: '任务中心', keepAlive: true, permCode: 'task:view' }, component: () => import('@/components/PalletTaskOverview.vue') },
      { path: 'pallet-task/semi/in', name: 'SemiInTaskPage', meta: { title: '半成品入库任务', keepAlive: true, permCode: 'task:view' }, component: () => import('@/components/SemiInTaskPage.vue') },
      { path: 'pallet-task/semi/out', name: 'SemiOutTaskPage', meta: { title: '半成品出库任务', keepAlive: true, permCode: 'task:view' }, component: () => import('@/components/SemiOutTaskPage.vue') },
      { path: 'pallet-task/finish/in', name: 'FinishInTaskPage', meta: { title: '成品入库任务', keepAlive: true, permCode: 'task:view' }, component: () => import('@/components/FinishInTaskPage.vue') },
      { path: 'pallet-task/finish/out', name: 'FinishOutTaskPage', meta: { title: '成品出库任务', keepAlive: true, permCode: 'task:view' }, component: () => import('@/components/FinishOutTaskPage.vue') },
      { path: 'pallet-task/transfer', name: 'TransferTaskPage', meta: { title: '调拨任务', keepAlive: true, permCode: 'task:view' }, component: () => import('@/components/TransferTaskPage.vue') },
      { path: 'production/boiling-batches', name: 'ProductionBoilingBatches', meta: { title: '煮糖批次', keepAlive: true, permCode: 'production:boiling:view' }, component: () => import('@/components/ProductionBoilingBatches.vue') },
      { path: 'production/orders', name: 'ProductionOrders', meta: { title: '生产订单', keepAlive: true, permCode: 'production:order:view' }, component: () => import('@/components/ProductionOrders.vue') },
      { path: 'production/material-pick', name: 'ProductionMaterialPick', meta: { title: '半成品领用', keepAlive: true, permCode: 'production:material:view' }, component: () => import('@/components/ProductionMaterialPick.vue') },
      { path: 'production/output-bind', name: 'ProductionOutputBind', meta: { title: '产出贴码', keepAlive: true, permCode: 'production:output:view' }, component: () => import('@/components/ProductionOutputBind.vue') },
      { path: 'screenMesh', name: 'ScreenMesh', meta: { title: '筛网管理', permCode: 'screen_mesh:view' }, component: () => import('@/components/ScreenMesh.vue') },
      { path: 'assay', name: 'Assay', meta: { title: '化验管理', keepAlive: true, permCode: 'assay:view' }, component: () => import('@/components/Assay.vue') },
      { path: 'assayGroup', name: 'AssayGroup', meta: { title: '批量化验组', keepAlive: true, permCode: 'assay_group:view' }, component: () => import('@/components/AssayGroup.vue') },
      { path: 'standard', name: 'Standard', meta: { title: '化验标准管理', keepAlive: true, permCode: 'quality_standard:view' }, component: () => import('@/components/Standard.vue') },
      { path: 'stock', redirect: '/stock/semi' },
      {
        path: 'stock/semi',
        name: 'SemiLedgerCenter',
        meta: { title: '半成品单据', keepAlive: true, permCode: 'document:view' },
        component: () => import('@/components/Stock.vue'),
        props: {
          productStatusFilter: '半成品',
          pageTitle: '半成品单据',
          pageDescription: '仅展示半成品相关的入库、出库和调拨台账；进入生产请通过生产订单领用。'
        }
      },
      {
        path: 'stock/finish',
        name: 'FinishLedgerCenter',
        meta: { title: '成品单据', keepAlive: true, permCode: 'document:view' },
        component: () => import('@/components/Stock.vue'),
        props: {
          productStatusFilter: '成品',
          pageTitle: '成品单据',
          pageDescription: '仅展示成品相关的入库、出库和调拨台账。'
        }
      },
      { path: 'employee', name: 'Employee', meta: { title: '用户管理', permCode: 'rbac:user:view' }, component: () => import('@/components/Employee.vue') },
      { path: 'role', name: 'Role', meta: { title: '角色管理', permCode: 'rbac:role:view' }, component: () => import('@/components/RoleManagement.vue') }
    ]
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

router.beforeEach(async to => {
  const tokenStore = useTokenStore()
  const authStore = useAuthStore()

  if (to.path === '/login') {
    if (!tokenStore.token) return true
    try {
      await authStore.ensureLoaded()
      return findFirstAccessiblePath(authStore.permissionCodes)
    } catch (error) {
      tokenStore.removeToken()
      authStore.clearAuth()
      return true
    }
  }

  if (!tokenStore.token) {
    authStore.clearAuth()
    return '/login'
  }

  try {
    await authStore.ensureLoaded()
  } catch (error) {
    tokenStore.removeToken()
    authStore.clearAuth()
    return '/login'
  }

  const permCode = to.meta?.permCode
  if (permCode && !authStore.hasPermission(permCode)) {
    return findFirstAccessiblePath(authStore.permissionCodes)
  }

  return true
})

export default router
