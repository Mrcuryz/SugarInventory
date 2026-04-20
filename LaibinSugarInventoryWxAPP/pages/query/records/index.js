import { queryOperationLogs } from '../../../api/query';
import { requireLogin } from '../../../utils/auth';
import { formatDateTime } from '../../../utils/dict';
import { showError } from '../../../utils/toast';

const OBJECT_OPTIONS = [
  { label: '全部对象', value: '' },
  { label: '产品', value: '产品' },
  { label: '库位', value: '库位' },
  { label: '筛网', value: '筛网' },
  { label: '化验', value: '化验数据' },
  { label: '检验标准', value: '检验标准' },
  { label: '化验验收标准', value: '化验验收标准' },
  { label: '化验验收标准数据', value: '化验验收标准数据' },
  { label: '员工名册', value: '员工名册' }
];

const TYPE_OPTIONS = [
  { label: '全部类型', value: '' },
  { label: '新增', value: 'INSERT' },
  { label: '修改', value: 'UPDATE' },
  { label: '删除', value: 'DELETE' }
];

const TYPE_META = {
  INSERT: { label: '新增', type: 'success', icon: '/assets/icons-line/flow-in.svg' },
  UPDATE: { label: '修改', type: 'primary', icon: '/assets/icons-line/icon-task.svg' },
  DELETE: { label: '删除', type: 'danger', icon: '/assets/icons-line/flow-cancel.svg' }
};

function objectLabel(value) {
  const item = OBJECT_OPTIONS.find(option => option.value === value);
  return item ? item.label : (value || '业务对象');
}

const FIELD_LABELS = {
  productName: '产品名称',
  productType: '产品类型',
  status: '状态',
  packagingMethod: '包装方式',
  weightPerPiece: '单件重量',
  piecesPerPallet: '每板件数',
  screenMeshId: '筛网',
  warehouseName: '库位名称',
  maxRows: '最大行数',
  maxCapacity: '最大容量',
  curCapacity: '当前容量',
  meshName: '筛网名称',
  description: '描述',
  standardName: '标准名称',
  productId: '产品',
  sampleDate: '采样日期',
  isQualified: '结论',
  version: '版本'
};

function parseJson(value) {
  if (!value) return {};
  if (typeof value === 'object') return value;
  try {
    return JSON.parse(value);
  } catch (error) {
    return {};
  }
}

function compactText(value) {
  if (value === null || value === undefined || value === '') return '';
  if (typeof value === 'object') return '';
  return String(value);
}

function pickName(data) {
  return compactText(
    data.productName ||
    data.warehouseName ||
    data.meshName ||
    data.standardName ||
    data.name ||
    data.id
  );
}

function changedFieldText(data) {
  const keys = Object.keys(data || {}).filter(key => !['id', 'createdAt', 'updatedAt'].includes(key));
  if (!keys.length) return '';
  return keys.slice(0, 4).map(key => FIELD_LABELS[key] || key).join('、');
}

function normalizeDate(value) {
  return value ? `${value} 00:00:00` : undefined;
}

function normalizeEndDate(value) {
  return value ? `${value} 23:59:59` : undefined;
}

function dateGroup(value) {
  const text = formatDateTime(value);
  return text === '-' ? '未知日期' : text.slice(0, 10);
}

function normalizeLog(item) {
  const meta = TYPE_META[item.operationType] || { label: '操作', type: 'info', icon: '/assets/icons-line/icon-task.svg' };
  const targetLabel = objectLabel(item.tableName);
  const changed = parseJson(item.changedFields);
  const oldData = parseJson(item.oldData);
  return {
    ...item,
    objectLabel: targetLabel,
    operationName: `${meta.label}${targetLabel}`,
    operationTypeLabel: meta.label,
    operationTypeTag: meta.type,
    icon: meta.icon,
    operationTimeText: formatDateTime(item.operationTime),
    operationDate: dateGroup(item.operationTime),
    operatorText: item.operator || '未记录',
    summary: buildSummary(item.operationType, targetLabel, changed, oldData)
  };
}

function buildSummary(operationType, targetLabel, changed, oldData) {
  const name = pickName(changed) || pickName(oldData);
  const suffix = name ? `：${name}` : '';
  if (operationType === 'INSERT') {
    return `新增${targetLabel}${suffix}`;
  }
  if (operationType === 'DELETE') {
    return `删除${targetLabel}${suffix}`;
  }
  if (operationType === 'UPDATE') {
    const fields = changedFieldText(changed);
    return fields ? `修改${targetLabel}${suffix}，变更 ${fields}` : `修改${targetLabel}${suffix}`;
  }
  return `${targetLabel}发生一次操作${suffix}`;
}

function groupRecords(records) {
  const groups = [];
  records.forEach(record => {
    let group = groups.find(item => item.date === record.operationDate);
    if (!group) {
      group = { date: record.operationDate, records: [] };
      groups.push(group);
    }
    group.records.push(record);
  });
  return groups;
}

Page({
  data: {
    operator: '',
    objectOptions: OBJECT_OPTIONS.map(item => item.label),
    objectIndex: 0,
    typeOptions: TYPE_OPTIONS.map(item => item.label),
    typeIndex: 0,
    startDate: '',
    endDate: '',
    page: 1,
    size: 10,
    total: 0,
    loading: false,
    error: false,
    records: [],
    groups: []
  },

  onLoad() {
    requireLogin();
    this.search();
  },

  onShow() {
    requireLogin();
  },

  onInput(e) {
    this.setData({ [e.currentTarget.dataset.field]: e.detail.value });
  },

  onObjectChange(e) {
    this.setData({ objectIndex: Number(e.detail.value) });
  },

  onTypeChange(e) {
    this.setData({ typeIndex: Number(e.detail.value) });
  },

  onDateChange(e) {
    this.setData({ [e.currentTarget.dataset.field]: e.detail.value });
  },

  resetSearch() {
    this.setData({
      operator: '',
      objectIndex: 0,
      typeIndex: 0,
      startDate: '',
      endDate: ''
    });
    this.search();
  },

  search() {
    this.setData({ page: 1, records: [], groups: [] });
    this.loadRecords();
  },

  async loadRecords() {
    this.setData({ loading: true, error: false });
    try {
      const object = OBJECT_OPTIONS[this.data.objectIndex];
      const type = TYPE_OPTIONS[this.data.typeIndex];
      const res = await queryOperationLogs({
        page: this.data.page,
        size: this.data.size,
        operator: this.data.operator.trim() || undefined,
        tableName: object && object.value ? object.value : undefined,
        operationType: type && type.value ? type.value : undefined,
        startTime: normalizeDate(this.data.startDate),
        endTime: normalizeEndDate(this.data.endDate)
      });
      const next = (res.records || []).map(normalizeLog);
      this.setData({
        records: next,
        groups: groupRecords(next),
        total: res.total || 0
      });
    } catch (error) {
      this.setData({ error: true });
      showError(error, '作业记录查询失败');
    } finally {
      this.setData({ loading: false });
    }
  },

  onPageChange(e) {
    this.setData({ page: e.detail.page });
    this.loadRecords();
  }
});
