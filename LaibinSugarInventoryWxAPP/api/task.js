import request from '../utils/request';

export function getTaskList(params = {}) {
  return request('/api/pallet-codes/tasks/list', 'POST', params);
}

export function bindPalletTask(params) {
  return request('/api/pallet-codes/bind', 'POST', params);
}

export function bindSemiItemsToTask(params) {
  return request('/api/pallet-codes/tasks/semi-bind', 'POST', params);
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

export function createSemiPrepareTasks(codes, remark = '小程序创建备料任务') {
  return request('/api/pallet-codes/semi/prepare/create', 'POST', { codes, remark });
}

export function confirmSemiPrepareTasks(codes, remark = '小程序确认备料') {
  return request('/api/pallet-codes/semi/prepare/confirm', 'POST', { codes, remark });
}

export function confirmSemiConsume(codes, remark = '小程序确认消耗') {
  return request('/api/pallet-codes/semi/consume/confirm', 'POST', { codes, remark });
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

