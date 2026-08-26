import {
  Box,
  Document,
  Finished,
  Filter,
  Goods,
  Histogram,
  House,
  List,
  Location,
  PieChart,
  Promotion,
  ShoppingCart,
  Switch,
  Tickets,
  Tools,
  User
} from '@element-plus/icons-vue'

export const menuList = [
  { path: '/home', title: '首页', icon: House, permCode: 'dashboard:view' },
  { path: '/operationlogs', title: '操作日志', icon: Document, permCode: 'log:view' },
  { path: '/production/daily-reports', title: '生产日报', icon: Document, permCode: 'production:daily-report:view' },
  { path: '/agent-review', title: 'AI Review Lite', icon: Document, permCode: 'agent:review:view' },
  { path: '/product', title: '产品管理', icon: Histogram, permCode: 'product:view' },
  {
    path: '/warehouse-management',
    title: '仓库管理',
    icon: Box,
    children: [
      { path: '/productStock', title: '库存汇总', icon: ShoppingCart, permCode: 'inventory:view' },
      { path: '/auto-inbound', title: '智能报数处理', icon: Promotion, permCode: 'inventory:view' },
      { path: '/warehouse', title: '库位管理', icon: Box, permCode: 'warehouse:view' },
      { path: '/warehouse-map', title: '仓库平面图', icon: Location, permCode: 'warehouse_map:view' }
    ]
  },
  {
    path: '/qrcode-center',
    title: '二维码中心',
    icon: Tickets,
    children: [
      { path: '/pallet-code/list', title: '二维码管理', icon: Tickets, permCode: 'qrcode:view' },
      { path: '/pallet-code/fixed-product', title: '固定产品二维码池', icon: Tickets, permCode: 'qrcode:pool_view' }
    ]
  },
  {
    path: '/pallet-task',
    title: '任务中心',
    icon: List,
    permCode: 'task:view',
    children: [
      {
        path: '/pallet-task/semi',
        title: '半成品',
        icon: Box,
        children: [
          { path: '/pallet-task/semi/in', title: '入库任务', icon: Tickets },
          { path: '/pallet-task/semi/out', title: '出库任务', icon: Promotion }
        ]
      },
      {
        path: '/pallet-task/finish',
        title: '成品',
        icon: Finished,
        children: [
          { path: '/pallet-task/finish/in', title: '成品入库任务', icon: Tickets },
          { path: '/pallet-task/finish/out', title: '成品出库任务', icon: ShoppingCart }
        ]
      },
      { path: '/pallet-task/transfer', title: '调拨任务', icon: Switch, permCode: 'task:view' }
    ]
  },
  {
    path: '/production',
    title: '生产管理',
    icon: Goods,
    permCode: 'production:order:view',
    children: [
      { path: '/production/boiling-batches', title: '煮糖批次', icon: Goods, permCode: 'production:boiling:view' },
      { path: '/production/orders', title: '生产订单', icon: List, permCode: 'production:order:view' },
      { path: '/production/material-pick', title: '半成品领用', icon: Box, permCode: 'production:material:view' },
      { path: '/production/output-bind', title: '产出贴码', icon: Tickets, permCode: 'production:output:view' }
    ]
  },
  {
    path: '/equipment',
    title: '设备管理',
    icon: Tools,
    children: [
      { path: '/equipment/assets', title: '设备台账', icon: List, permCode: 'equipment:asset:view' },
      { path: '/equipment/repairs', title: '修理记录', icon: Document, permCode: 'equipment:repair:view' },
      { path: '/equipment/basic-data', title: '基础资料', icon: Tools, permCode: 'equipment:config:view' }
    ]
  },
  { path: '/screenMesh', title: '筛网管理', icon: Filter, permCode: 'screen_mesh:view' },
  {
    path: '/assay-center',
    title: '化验中心',
    icon: PieChart,
    children: [
      { path: '/assay', title: '化验管理', icon: PieChart, permCode: 'assay:view' },
      { path: '/assayGroup', title: '批量化验组', icon: Document, permCode: 'assay_group:view' },
      { path: '/standard', title: '化验标准管理', icon: Finished, permCode: 'quality_standard:view' }
    ]
  },
  {
    path: '/stock',
    title: '单据中心',
    icon: Switch,
    permCode: 'document:view',
    children: [
      { path: '/stock/semi', title: '半成品单据', icon: Box, permCode: 'document:view' },
      { path: '/stock/finish', title: '成品单据', icon: Finished, permCode: 'document:view' }
    ]
  },
  {
    path: '/staff-management',
    title: '员工管理',
    icon: User,
    children: [
      { path: '/employee', title: '用户管理', icon: User, permCode: 'rbac:user:view' },
      { path: '/role', title: '角色管理', icon: User, permCode: 'rbac:role:view' }
    ]
  }
]

const flattenMenu = (items, result = []) => {
  items.forEach(item => {
    result.push(item)
    if (item.children?.length) {
      flattenMenu(item.children, result)
    }
  })
  return result
}

const menuByPath = new Map(flattenMenu(menuList).map(item => [item.path, item]))

export const getMenuIconByPath = path => menuByPath.get(path)?.icon || Document

export const filterMenuByPermissions = (items, permissionCodes = [], roleCode = '') => {
  const codeSet = new Set(permissionCodes || [])
  const isAdmin = ['ADMIN', 'SUPER_ADMIN'].includes(roleCode)
  const canVisit = item => !item.permCode || isAdmin || codeSet.has(item.permCode)

  return (items || [])
    .map(item => {
      const next = { ...item }
      if (item.children?.length) {
        next.children = filterMenuByPermissions(item.children, permissionCodes, roleCode)
      }
      return next
    })
    .filter(item => {
      if (item.children?.length) {
        return canVisit(item) || item.children.length > 0
      }
      if (Array.isArray(item.children)) {
        return false
      }
      return canVisit(item)
    })
}

export const findFirstAccessiblePath = (permissionCodes, roleCode = '') => {
  const visibleMenus = filterMenuByPermissions(menuList, permissionCodes, roleCode)
  const flat = flattenMenu(visibleMenus)
  const firstLeaf = flat.find(item => !item.children?.length)
  return firstLeaf?.path || '/login'
}
