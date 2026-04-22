export const PALLET_STATUS = {
  FREE: { label: '空闲', type: 'success' },
  PENDING_IN: { label: '待入库', type: 'warning' },
  PENDING: { label: '待处理', type: 'warning' },
  INSTOCK: { label: '在库', type: 'primary' },
  CONSUMED: { label: '已消耗', type: 'info' },
  INVALID: { label: '作废', type: 'danger' }
};

export const TASK_TYPE = {
  SEMI_IN: { label: '半成品入库', type: 'success' },
  FINISH_IN: { label: '成品入库', type: 'primary' },
  IN: { label: '入库任务', type: 'primary' },
  OUT: { label: '出库任务', type: 'warning' },
  TRANSFER: { label: '调拨任务', type: 'info' }
};

export const BIZ_SCENE = {
  DIRECT_OUT: { label: '半成品出库', type: 'warning' },
  PREPARE_CONSUMED: { label: '转入备料池', type: 'primary' },
  FINISH_OUT: { label: '成品出库', type: 'danger' }
};

export const TASK_STATUS = {
  PENDING: { label: '待处理', type: 'warning' },
  CONFIRMED: { label: '已完成', type: 'success' },
  CANCELED: { label: '已取消', type: 'info' }
};

export const FLOW_OPERATION = {
  SEMI_BIND: { label: '半成品绑定', type: 'success' },
  ASSAY: { label: '化验', type: 'info' },
  SEMI_INSTOCK: { label: '半成品入库', type: 'success' },
  FINISH_BIND: { label: '成品绑定', type: 'primary' },
  FINISH_INSTOCK: { label: '成品入库', type: 'primary' },
  PREPARE_CONSUMED: { label: '转入备料池', type: 'warning' },
  TRANSFER: { label: '调拨', type: 'info' },
  CONSUMED: { label: '消耗', type: 'danger' },
  OUT: { label: '出库', type: 'danger' },
  CANCELED: { label: '取消', type: 'info' }
};

export const UNIT_OPTIONS = [
  { value: '0', label: '板' },
  { value: '1', label: '件' }
];

export const SIDE_OPTIONS = [
  { value: '左', label: '左侧' },
  { value: '右', label: '右侧' }
];

export function getDictItem(dict, value, fallbackType = 'info') {
  return dict[value] || { label: value || '-', type: fallbackType };
}

export function formatTaskType(task) {
  if (!task) return '-';
  if (task.taskType === 'OUT' && task.bizScene) {
    return getDictItem(BIZ_SCENE, task.bizScene).label;
  }
  return getDictItem(TASK_TYPE, task.taskType).label;
}

export function formatDateTime(value) {
  if (!value) return '-';
  return String(value).replace('T', ' ').slice(0, 16);
}

export function formatAssayStandard(value, emptyText = '无匹配标准') {
  if (!value) return '本次未关联标准';
  let parsed = value;
  if (typeof value === 'string') {
    try {
      parsed = JSON.parse(value);
    } catch (error) {
      parsed = value;
    }
  }
  if (Array.isArray(parsed)) {
    const items = parsed.filter(item => item && item !== '无');
    return items.length ? items.join('、') : emptyText;
  }
  if (String(parsed) === '无' || String(parsed) === '[]') return emptyText;
  return String(parsed);
}

export const ASSAY_JUDGE_MAP = {
  PASS: { label: '合格', type: 'success' },
  FAIL: { label: '不合格', type: 'danger' },
  NO_STANDARD: { label: '无标准', type: 'warning' },
  MULTIPLE_CANDIDATES: { label: '待确认', type: 'info' }
};

export function getAssayJudgeMeta(value, fallbackLabel = '未出结论') {
  if (!value) {
    return { label: fallbackLabel, type: 'info' };
  }
  return ASSAY_JUDGE_MAP[value] || { label: value, type: 'info' };
}

export function getCompareTypeLabel(value) {
  return {
    range: '区间',
    lte: '不高于',
    gte: '不低于'
  }[value] || value || '-';
}

export function formatMetricRange(item, emptyText = '未设置') {
  if (!item) return emptyText;
  if (item.compareType === 'lte') {
    return item.maxValue == null ? emptyText : `<= ${item.maxValue}${item.unit || ''}`;
  }
  if (item.compareType === 'gte') {
    return item.minValue == null ? emptyText : `>= ${item.minValue}${item.unit || ''}`;
  }
  const min = item.minValue == null ? '-' : item.minValue;
  const max = item.maxValue == null ? '-' : item.maxValue;
  return `${min} ~ ${max}${item.unit || ''}`;
}

export function getAssayPrimaryStandard(assay, emptyText = '当前未采用标准') {
  if (!assay) return emptyText;
  if (assay.appliedStandard && assay.appliedStandard.standardName) {
    return assay.appliedStandard.standardName;
  }
  if (assay.appliedStandardName) {
    return assay.appliedStandardName;
  }
  return formatAssayStandard(assay.qualifiedStandards, emptyText);
}

export function enrichTask(task) {
  const status = getDictItem(TASK_STATUS, task.taskStatus || task.status);
  const type = task.taskType === 'OUT' && task.bizScene
    ? getDictItem(BIZ_SCENE, task.bizScene)
    : getDictItem(TASK_TYPE, task.taskType);
  return {
    ...task,
    taskTypeLabel: type.label,
    taskTypeTagType: type.type,
    taskStatusLabel: status.label,
    taskStatusTagType: status.type,
    createdAtText: formatDateTime(task.createdAt)
  };
}

export function enrichPallet(info) {
  const status = getDictItem(PALLET_STATUS, info && info.status);
  return {
    ...(info || {}),
    statusLabel: status.label,
    statusTagType: status.type
  };
}

