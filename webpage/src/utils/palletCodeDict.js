export const PALLET_STATUS_MAP = {
  FREE: { label: '空闲', type: 'success' },
  PENDING: { label: '待入库', type: 'warning' },
  INSTOCK: { label: '在库', type: 'primary' },
  INVALID: { label: '作废', type: 'danger' }
}

export const TASK_TYPE_MAP = {
  SEMI_IN: { label: '半成品入库', type: 'success' },
  FINISH_IN: { label: '成品入库', type: 'primary' },
  OUT: { label: '出库', type: 'warning' },
  TRANSFER: { label: '调拨', type: 'info' }
}

export const BIZ_SCENE_MAP = {
  DIRECT_OUT: { label: '半成品普通出库', type: 'warning' },
  PREPARE_CONSUMED: { label: '转入备料池', type: 'primary' },
  FINISH_OUT: { label: '成品出库', type: 'danger' }
}

export const TASK_STATUS_MAP = {
  PENDING: { label: '待处理', type: 'warning' },
  CONFIRMED: { label: '已确认', type: 'success' },
  CANCELED: { label: '已取消', type: 'info' }
}

export const FLOW_OPERATION_MAP = {
  SEMI_BIND: { label: '半成品绑定', type: 'success' },
  ASSAY: { label: '化验', type: 'info' },
  SEMI_INSTOCK: { label: '半成品入库', type: 'success' },
  FINISH_BIND: { label: '成品绑定', type: 'primary' },
  FINISH_INSTOCK: { label: '成品入库', type: 'primary' },
  PREPARE_CONSUMED: { label: '转入备料池', type: 'warning' },
  TRANSFER: { label: '调拨', type: 'info' },
  CONSUMED: { label: '消耗', type: 'danger' },
  OUT: { label: '出库', type: 'danger' },
  INVALID: { label: '作废', type: 'danger' },
  RESTORED: { label: '恢复', type: 'warning' }
}

export const PRODUCT_TYPE_OPTIONS = [
  { value: '白冰糖', label: '白冰糖' },
  { value: '黄冰糖', label: '黄冰糖' }
]

export const PRODUCT_STATUS_OPTIONS = [
  { value: '半成品', label: '半成品' },
  { value: '成品', label: '成品' }
]

export const SIDE_OPTIONS = [
  { value: '左', label: '左' },
  { value: '右', label: '右' }
]

export const UNIT_OPTIONS = [
  { value: '0', label: '板' },
  { value: '1', label: '件' }
]

export const taskTypeOptions = Object.entries(TASK_TYPE_MAP).map(([value, item]) => ({
  value,
  label: item.label
}))

export const bizSceneOptions = Object.entries(BIZ_SCENE_MAP).map(([value, item]) => ({
  value,
  label: item.label
}))

export const taskStatusOptions = Object.entries(TASK_STATUS_MAP).map(([value, item]) => ({
  value,
  label: item.label
}))

export const getDictLabel = (map, value) => map[value]?.label || value || '-'

export const getDictType = (map, value) => map[value]?.type || ''
