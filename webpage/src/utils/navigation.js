import {
  Box,
  Document,
  Finished,
  Filter,
  Histogram,
  House,
  List,
  Location,
  PieChart,
  Promotion,
  ShoppingCart,
  Switch,
  Tickets,
  User
} from '@element-plus/icons-vue'

export const menuList = [
  {path: '/home', title: '首页', icon: House},
//  {path: '/autoInbound', title: '自动入库', icon: Document},
  {path: '/operationlogs', title: '操作日志', icon: Document},
  {path: '/product', title: '产品管理', icon: Histogram},
  {path: '/productStock', title: '库存汇总', icon: ShoppingCart},
  {path: '/warehouse', title: '库位管理', icon: Box},
  {path: '/warehouse-map', title: '仓库平面图', icon: Location},
  {path: '/pallet-code/list', title: '托盘码管理', icon: Tickets},
  {
    path: '/pallet-task',
    title: '任务中心',
    icon: List,
    children: [
      {
        path: '/pallet-task/semi',
        title: '半成品',
        icon: Box,
        children: [
          {path: '/pallet-task/semi/in', title: '入库任务', icon: Tickets},
          {path: '/pallet-task/semi/out', title: '出库任务', icon: Promotion}
        ]
      },
      {
        path: '/pallet-task/finish',
        title: '成品',
        icon: Finished,
        children: [
          {path: '/pallet-task/finish/in', title: '成品入库任务', icon: Tickets},
          {path: '/pallet-task/finish/out', title: '成品出库任务', icon: ShoppingCart}
        ]
      },
      {path: '/pallet-task/transfer', title: '调拨任务', icon: Switch}
    ]
  },
  {path: '/screenMesh', title: '筛网管理', icon: Filter},
  {path: '/assay', title: '化验管理', icon: PieChart},
  {path: '/assayGroup', title: '验收标准', icon: Document},
  {path: '/standard', title: '化验标准管理', icon: Finished},
  {
    path: '/stock',
    title: '单据中心',
    icon: Switch,
    children: [
      {path: '/stock/semi', title: '半成品单据', icon: Box},
      {path: '/stock/finish', title: '成品单据', icon: Finished}
    ]
  },
  {path: '/employee', title: '员工管理', icon: User}
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

export const getMenuIconByPath = (path) => menuByPath.get(path)?.icon || Document
