import request from '../utils/request';

export function getTaskList(params = {}) {
  return request('/api/pallet-codes/tasks/list', 'POST', params);
}

export function bindPalletTask(params) {
  return request('/api/pallet-codes/bind', 'POST', params);
}

export function bindSemiItemsToTask(params) {
  return Promise.reject(new Error('成品与半成品追溯请通过生产订单关联'));
}

export function confirmPalletInBatch(items) {
  return request('/api/pallet-codes/tasks/confirm', 'POST', { items });
}

export function cancelTasks(codes, remark = '小程序取消任务') {
  return request('/api/pallet-codes/tasks/cancel', 'POST', { codes, remark });
}

export function createSemiOutTasks(codes, remark = '小程序创建半成品出库任务') {
  return request('/api/pallet-codes/semi/out/create', 'POST', { codes, remark });
}

export function confirmSemiOutTasks(codes, remark = '小程序确认半成品出库') {
  return request('/api/pallet-codes/semi/out/confirm', 'POST', { codes, remark });
}

export function createSemiPrepareTasks(codes, remark = '小程序创建历史生产占用任务') {
  return Promise.reject(new Error('半成品进入生产请在管理端通过生产订单领用'));
}

export function confirmSemiPrepareTasks(codes, remark = '小程序确认历史生产占用') {
  return Promise.reject(new Error('历史生产占用任务仅保留查询，不再允许确认'));
}

// 旧版接口保留兼容，不作为新版成品消耗入口；新版通过生产订单关联追溯。
export function confirmSemiConsume(codes, remark = '小程序确认消耗') {
  return Promise.reject(new Error('历史生产占用任务仅保留查询，不再允许消耗确认'));
}

export function createFinishOutTasks(codes, remark = '小程序创建成品出库任务') {
  return request('/api/pallet-codes/finish/out/create', 'POST', { codes, remark });
}

export function confirmFinishOutTasks(codes, remark = '小程序确认成品出库') {
  return request('/api/pallet-codes/finish/out/confirm', 'POST', { codes, remark });
}

export function createTransferTasks(items) {
  return request('/api/pallet-codes/transfer/create', 'POST', { items });
}

export function confirmTransferTasks(codes, remark = '小程序确认调拨') {
  return request('/api/pallet-codes/transfer/confirm', 'POST', { codes, remark });
}
