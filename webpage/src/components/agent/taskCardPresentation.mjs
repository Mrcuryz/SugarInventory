const TASK_CARD_TYPES = new Set(['pallet_tasks', 'pallet_task_detail'])

export const isTaskCard = (card) => TASK_CARD_TYPES.has(card?.cardType)

export const isTaskListCard = (card) => card?.cardType === 'pallet_tasks'

export const isTaskDetailCard = (card) => card?.cardType === 'pallet_task_detail'

export const taskRecords = (card) => (card?.fields || []).filter(field =>
  ['pallet_task', 'pallet_task_detail'].includes(field?.kind)
)

const taskBatchDescriptor = (record) => {
  const taskType = String(record?.taskTypeLabel || '')
  const businessScene = String(record?.businessSceneLabel || '')
  const productStatus = String(record?.productStatusLabel || '')

  if (taskType.includes('半成品入库')) {
    return { key: 'semi_in', label: '半成品入库', batchAction: 'confirmIn' }
  }
  if (taskType.includes('成品入库')) {
    return { key: 'finish_in', label: '成品入库', batchAction: 'confirmIn' }
  }
  if (taskType === '入库') {
    return productStatus.includes('半成品')
      ? { key: 'semi_in', label: '半成品入库', batchAction: 'confirmIn' }
      : { key: 'finish_in', label: '成品入库', batchAction: 'confirmIn' }
  }
  if (taskType.includes('调拨')) {
    return { key: 'transfer', label: '调拨', batchAction: 'transferConfirm' }
  }
  if (taskType.includes('出库') || taskType === '出库') {
    if (businessScene.includes('历史生产占用')) {
      return { key: 'legacy_prepare', label: '历史生产占用', batchAction: '' }
    }
    if (businessScene.includes('半成品') || productStatus.includes('半成品') || taskType.includes('半成品')) {
      return { key: 'semi_out', label: '半成品出库', batchAction: 'semiOutConfirm' }
    }
    if (businessScene.includes('成品') || productStatus.includes('成品') || taskType.includes('成品')) {
      return { key: 'finish_out', label: '成品出库', batchAction: 'finishOutConfirm' }
    }
    return { key: 'other_out', label: '其他出库', batchAction: '' }
  }
  return {
    key: `other_${taskType || 'unknown'}`,
    label: taskType || '其他任务',
    batchAction: ''
  }
}

export const isPendingTaskRecord = (record) => String(record?.taskStatusLabel || '').includes('待处理')

export const isConfirmedTaskRecord = (record) => {
  const status = String(record?.taskStatusLabel || '')
  return status.includes('已确认') || status.includes('已完成')
}

export const taskGroups = (card) => {
  const groups = new Map()
  taskRecords(card).forEach((record, index) => {
    const descriptor = taskBatchDescriptor(record)
    if (!groups.has(descriptor.key)) {
      groups.set(descriptor.key, { ...descriptor, records: [] })
    }
    groups.get(descriptor.key).records.push({
      ...record,
      selectionKey: `${descriptor.key}:${record?.palletCode || 'unknown'}:${index}`,
      selectable: Boolean(descriptor.batchAction) && isPendingTaskRecord(record)
    })
  })
  groups.forEach(group => {
    group.records.sort((left, right) => Number(right.selectable) - Number(left.selectable))
  })
  return [...groups.values()]
}

const statusSummary = (records) => {
  const source = Array.isArray(records) ? records : []
  const pendingCount = source.filter(isPendingTaskRecord).length
  const confirmedCount = source.filter(isConfirmedTaskRecord).length
  return {
    totalCount: source.length,
    pendingCount,
    confirmedCount,
    otherCount: Math.max(0, source.length - pendingCount - confirmedCount)
  }
}

export const taskCardStatusSummary = (card) => ({
  ...statusSummary(taskRecords(card)),
  updated: String(card?.title || '').includes('已更新')
})

export const taskGroupStatusSummary = (group) => ({
  ...statusSummary(group?.records),
  selectableCount: (group?.records || []).filter(record => record.selectable).length
})

export const applyTaskBatchCompletion = (card, palletCodes) => {
  if (!isTaskCard(card)) return { card, changedCount: 0 }
  const codeSet = new Set((palletCodes || []).map(code => String(code || '').trim().toUpperCase()).filter(Boolean))
  if (!codeSet.size) return { card, changedCount: 0 }

  let changedCount = 0
  const fields = (card.fields || []).map(field => {
    const isTaskRow = ['pallet_task', 'pallet_task_detail'].includes(field?.kind)
    const palletCode = String(field?.palletCode || '').trim().toUpperCase()
    if (!isTaskRow || !codeSet.has(palletCode) || !isPendingTaskRecord(field)) return field
    changedCount += 1
    return { ...field, taskStatusLabel: '已确认' }
  })
  if (!changedCount) return { card, changedCount: 0 }

  if (!isTaskListCard(card)) {
    return { card: { ...card, fields }, changedCount }
  }

  const taskFields = fields.filter(field => field?.kind === 'pallet_task')
  const pendingCount = taskFields.filter(isPendingTaskRecord).length
  const confirmedCount = taskFields.filter(field => String(field?.taskStatusLabel || '').includes('已确认')).length
  const otherCount = Math.max(0, taskFields.length - pendingCount - confirmedCount)
  const summary = [`待处理 ${pendingCount} 条`, `已确认 ${confirmedCount} 条`]
  if (otherCount) summary.push(`其他状态 ${otherCount} 条`)
  return {
    card: {
      ...card,
      title: `任务状态（已更新）· ${summary.join('，')}`,
      fields
    },
    changedCount
  }
}

export const taskStatusTone = (label) => {
  const value = String(label || '')
  if (value.includes('待处理') || value.includes('处理中') || value.includes('待入库')) return 'pending'
  if (value.includes('确认') || value.includes('完成')) return 'success'
  if (value.includes('失败') || value.includes('取消')) return 'muted'
  return 'unknown'
}

export const taskDetailEntries = (record) => [
  ['业务场景', record?.businessSceneLabel],
  ['产品状态', record?.productStatusLabel],
  ['目标位置', record?.targetLocationLabel],
  ['总重量', record?.totalWeightText],
  ['生产日期', record?.productionDate],
  ['筛网', record?.screenMeshLabel],
  ['半成品明细', record?.semiItemCountText],
  ['操作批次', record?.operationBatchLabel],
  ['生产订单', record?.productionOrderLabel],
  ['订单状态', record?.productionOrderStatusLabel],
  ['标签批次', record?.productionLabelBatchLabel],
  ['创建信息', record?.createdSummary],
  ['确认信息', record?.confirmationSummary]
].filter(([, value]) => value)
