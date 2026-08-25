export const permissionGroupCatalog = [
  {
    key: 'dashboard',
    title: '首页',
    permissions: ['system:access', 'dashboard:view']
  },
  {
    key: 'product',
    title: '产品管理',
    permissions: ['product:view', 'product:create', 'product:update', 'product:delete']
  },
  {
    key: 'warehouse',
    title: '库位与仓库',
    permissions: ['warehouse:view', 'warehouse_map:view', 'warehouse:create', 'warehouse:update', 'warehouse:delete', 'warehouse:status']
  },
  {
    key: 'qrcode',
    title: '二维码管理',
    permissions: ['qrcode:view', 'qrcode:generate', 'qrcode:print', 'qrcode:bind_fixed_product', 'qrcode:activate', 'qrcode:pool_view', 'qrcode:invalidate', 'qrcode:flow_delete']
  },
  {
    key: 'inventory_operation',
    title: '传统库存作业',
    permissions: ['inventory:inbound', 'inventory:outbound', 'inventory:transfer']
  },
  {
    key: 'task',
    title: '任务中心',
    permissions: ['task:view', 'task:create', 'task:confirm', 'task:cancel']
  },
  {
    key: 'production',
    title: '生产管理',
    permissions: [
      'production:boiling:view',
      'production:boiling:create',
      'production:boiling:update',
      'production:boiling:cancel',
      'production:order:view',
      'production:order:create',
      'production:order:update',
      'production:order:cancel',
      'production:material:view',
      'production:material:pick',
      'production:output:view',
      'production:output:create',
      'production:output:bindQr',
      'production:output:print'
    ]
  },
  {
    key: 'assay',
    title: '化验管理',
    permissions: ['assay:view', 'assay:create', 'assay:update', 'assay:delete', 'assay:query', 'assay:copy', 'quality:test']
  },
  {
    key: 'screen_mesh',
    title: '筛网管理',
    permissions: ['screen_mesh:view', 'screen_mesh:create', 'screen_mesh:update', 'screen_mesh:delete']
  },
  {
    key: 'assay_group',
    title: '批量化验组',
    permissions: ['assay_group:view', 'assay_group:create', 'assay_group:update', 'assay_group:delete']
  },
  {
    key: 'quality_standard',
    title: '化验标准管理',
    permissions: ['quality_standard:view', 'quality_standard:create', 'quality_standard:update', 'quality_standard:delete', 'quality_standard:bind_product']
  },
  {
    key: 'document',
    title: '单据与记录',
    permissions: ['inventory:view', 'document:view', 'record:view', 'record:export', 'record:query', 'log:view']
  },
  {
    key: 'agent_access',
    title: 'AI 助手',
    permissions: ['agent:use', 'agent:finish-inbound:execute']
  },
  {
    key: 'agent_review',
    title: 'AI 审查',
    permissions: ['agent:review:view', 'agent:review:update']
  },
  {
    key: 'employee',
    title: '员工管理',
    permissions: ['employee:view', 'employee:create', 'employee:update', 'employee:delete', 'user:create', 'user:update', 'user:delete']
  },
  {
    key: 'rbac_user',
    title: '用户管理',
    permissions: ['rbac:user:view', 'rbac:user:update_role', 'rbac:user:enable_disable', 'rbac:user:reset_password']
  },
  {
    key: 'rbac_role',
    title: '角色管理',
    permissions: ['rbac:role:view', 'rbac:role:create', 'rbac:role:update', 'rbac:role:delete', 'rbac:role:assign_permission']
  }
]

const permissionMeta = {
  'system:access': '系统访问',
  'dashboard:view': '首页查看',
  'product:view': '产品查看',
  'product:create': '产品新增',
  'product:update': '产品编辑',
  'product:delete': '产品删除',
  'warehouse:view': '库位查看',
  'warehouse_map:view': '仓库平面图查看',
  'warehouse:create': '库位新增',
  'warehouse:update': '库位编辑',
  'warehouse:delete': '库位删除',
  'warehouse:status': '库位状态维护',
  'qrcode:view': '二维码查看',
  'qrcode:generate': '二维码生成',
  'qrcode:print': '二维码打印',
  'qrcode:bind_fixed_product': '绑定固定产品',
  'qrcode:activate': '打印并启用',
  'qrcode:pool_view': '固定产品二维码池查看',
  'qrcode:invalidate': '二维码作废与恢复',
  'qrcode:flow_delete': '二维码历史流转删除',
  'task:view': '任务中心查看',
  'task:create': '任务创建',
  'task:confirm': '任务确认',
  'task:cancel': '任务取消',
  'inventory:inbound': '传统库存入库',
  'inventory:outbound': '传统库存出库',
  'inventory:transfer': '传统库存调拨',
  'production:boiling:view': '煮糖批次查看',
  'production:boiling:create': '煮糖批次创建',
  'production:boiling:update': '煮糖批次编辑',
  'production:boiling:cancel': '煮糖批次作废',
  'production:order:view': '生产订单查看',
  'production:order:create': '生产订单创建',
  'production:order:update': '生产订单更新',
  'production:order:cancel': '生产订单取消',
  'production:material:view': '半成品领用查看',
  'production:material:pick': '半成品领用',
  'production:output:view': '产出贴码查看',
  'production:output:create': '添加产出',
  'production:output:bindQr': '历史产出分配二维码',
  'production:output:print': '产出二维码打印',
  'screen_mesh:view': '筛网查看',
  'screen_mesh:create': '筛网新增',
  'screen_mesh:update': '筛网编辑',
  'screen_mesh:delete': '筛网删除',
  'assay:view': '化验查看',
  'assay:create': '化验新增',
  'assay:update': '化验编辑',
  'assay:delete': '化验删除',
  'assay:query': '化验查询',
  'assay:copy': '化验复制',
  'quality:test': '化验录入兼容权限',
  'assay_group:view': '批量化验组查看',
  'assay_group:create': '批量化验组新增',
  'assay_group:update': '批量化验组编辑',
  'assay_group:delete': '批量化验组删除',
  'quality_standard:view': '化验标准查看',
  'quality_standard:create': '化验标准新增',
  'quality_standard:update': '化验标准编辑',
  'quality_standard:delete': '化验标准删除',
  'quality_standard:bind_product': '绑定产品',
  'inventory:view': '库存查看',
  'document:view': '单据中心查看',
  'record:view': '记录查看',
  'record:export': '记录导出',
  'record:query': '库存/记录查询',
  'log:view': '操作日志查看',
  'agent:use': 'AI 助手使用',
  'agent:finish-inbound:execute': 'AI 成品入库执行',
  'agent:review:view': 'AI Review 查看',
  'agent:review:update': 'AI Review 标记',
  'employee:view': '员工查看',
  'employee:create': '员工新增',
  'employee:update': '员工编辑',
  'employee:delete': '员工删除',
  'user:create': '员工导入/新增',
  'user:update': '员工编辑',
  'user:delete': '员工删除',
  'rbac:user:view': '用户管理查看',
  'rbac:user:update_role': '分配角色',
  'rbac:user:enable_disable': '用户启停',
  'rbac:user:reset_password': '重置凭证',
  'rbac:role:view': '角色管理查看',
  'rbac:role:create': '角色新增',
  'rbac:role:update': '角色编辑',
  'rbac:role:delete': '角色删除',
  'rbac:role:assign_permission': '分配权限'
}

export function buildPermissionGroups(permissionList = []) {
  const permissionMap = new Map(permissionList.map(item => [item.permCode, item]))
  const used = new Set()

  const groups = permissionGroupCatalog
    .map(group => {
      const items = group.permissions
        .map(code => {
          const current = permissionMap.get(code)
          if (!current) return null
          used.add(code)
          return {
            ...current,
            label: permissionMeta[code] || current.permName || code
          }
        })
        .filter(Boolean)
      return { ...group, items }
    })
    .filter(group => group.items.length)

  const extraItems = permissionList
    .filter(item => !used.has(item.permCode))
    .map(item => ({
      ...item,
      label: permissionMeta[item.permCode] || item.permName || item.permCode
    }))

  if (extraItems.length) {
    groups.push({
      key: 'other',
      title: '其他权限',
      items: extraItems
    })
  }

  return groups
}
