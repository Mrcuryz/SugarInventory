import dayjs from 'dayjs';
import { getProfile } from '../../../api/auth';
import { getFinishedProducts, getSemiProducts } from '../../../api/product';
import { createAssays, queryAssayGroups, queryAssays } from '../../../api/query';
import { requireLogin } from '../../../utils/auth';
import { formatDateTime, getAssayJudgeMeta, getAssayPrimaryStandard } from '../../../utils/dict';
import { showError } from '../../../utils/toast';

const JUDGE_OPTIONS = [
  { label: '全部', value: '' },
  { label: '合格', value: 'PASS' },
  { label: '不合格', value: 'FAIL' },
  { label: '无标准', value: 'NO_STANDARD' },
  { label: '待确认', value: 'MULTIPLE_CANDIDATES' }
];

const SELECT_TYPE_OPTIONS = [
  { label: '选择产品（单次录入）', value: '1' },
  { label: '批量化验组（批量录入）', value: '2' }
];

const METRIC_FIELDS = [
  { field: 'colorValue', label: '色值', unit: '' },
  { field: 'reducingSugar', label: '还原糖分', unit: '' },
  { field: 'dryWeight', label: '干燥失重', unit: '' },
  { field: 'conductivityAsh', label: '电导灰分', unit: '' },
  { field: 'sucrose', label: '蔗糖分', unit: '' },
  { field: 'insolubleImpurity', label: '不溶于水杂质', unit: '' },
  { field: 'phValue', label: 'pH值', unit: '' }
];

function createEmptyForm() {
  return {
    selectType: SELECT_TYPE_OPTIONS[0].value,
    selectTypeLabel: SELECT_TYPE_OPTIONS[0].label,
    productId: null,
    productName: '',
    relatedId: null,
    relatedName: '',
    sampleDate: '',
    colorValue: '',
    reducingSugar: '',
    dryWeight: '',
    conductivityAsh: '',
    sucrose: '',
    insolubleImpurity: '',
    phValue: ''
  };
}

function canCreateAssay(permissionCodes = []) {
  return permissionCodes.includes('assay:create') || permissionCodes.includes('quality:test');
}

function normalizeAssay(item) {
  const judge = getAssayJudgeMeta(item.judgeResult, item.isQualified || '未出结论');
  return {
    ...item,
    sampleDateText: item.sampleDate || '-',
    createdAtText: formatDateTime(item.createdAt),
    standardText: getAssayPrimaryStandard(item, '当前未采用标准'),
    judgeLabel: judge.label,
    judgeType: judge.type,
    failedMetricCount: item.failedMetricCount || 0
  };
}

function normalizeProduct(product) {
  return {
    id: product.productId,
    name: product.productName,
    type: product.productType || '未分类'
  };
}

function buildProductPickerState(semiProducts = [], finishedProducts = []) {
  return [
    {
      label: '成品',
      types: groupProductsByType((finishedProducts || []).map(normalizeProduct))
    },
    {
      label: '半成品',
      types: groupProductsByType((semiProducts || []).map(normalizeProduct))
    }
  ];
}

function groupProductsByType(products = []) {
  const typeMap = {};
  products.forEach(product => {
    if (!typeMap[product.type]) {
      typeMap[product.type] = [];
    }
    typeMap[product.type].push({
      id: product.id,
      name: product.name
    });
  });

  const types = Object.keys(typeMap);
  if (!types.length) {
    return [];
  }

  return types.map(type => ({
    label: type,
    products: typeMap[type]
  }));
}

function getPickerColumns(pickerState = [], indexes = [0, 0, 0]) {
  const categoryLabels = pickerState.map(item => item.label);
  const category = pickerState[indexes[0]] || pickerState[0] || { types: [] };
  const typeLabels = (category.types || []).map(item => item.label);
  const type = category.types?.[indexes[1]] || category.types?.[0] || { products: [] };
  const productLabels = (type.products || []).map(item => item.name);

  return [
    categoryLabels.length ? categoryLabels : ['暂无分类'],
    typeLabels.length ? typeLabels : ['暂无类型'],
    productLabels.length ? productLabels : ['暂无产品']
  ];
}

function getSafePickerIndexes(pickerState = [], indexes = [0, 0, 0]) {
  const categoryIndex = Math.min(indexes[0] || 0, Math.max((pickerState || []).length - 1, 0));
  const category = pickerState[categoryIndex] || { types: [] };
  const typeIndex = Math.min(indexes[1] || 0, Math.max((category.types || []).length - 1, 0));
  const type = category.types?.[typeIndex] || { products: [] };
  const productIndex = Math.min(indexes[2] || 0, Math.max((type.products || []).length - 1, 0));

  return [categoryIndex, typeIndex, productIndex];
}

function getSelectedProduct(pickerState = [], indexes = [0, 0, 0]) {
  const safeIndexes = getSafePickerIndexes(pickerState, indexes);
  const category = pickerState[safeIndexes[0]];
  const type = category?.types?.[safeIndexes[1]];
  return type?.products?.[safeIndexes[2]] || null;
}

function sanitizeMetricValue(value) {
  if (value === null || value === undefined) {
    return '';
  }
  const normalized = String(value)
    .replace(/[^\d.]/g, '')
    .replace(/^\./g, '')
    .replace(/\.{2,}/g, '.')
    .replace(/^(\d+\.\d*?)\..*$/, '$1');
  return normalized;
}

Page({
  data: {
    productName: '',
    testerName: '',
    startDate: '',
    endDate: '',
    judgeOptions: JUDGE_OPTIONS,
    judgeIndex: 0,
    page: 1,
    size: 10,
    total: 0,
    loading: false,
    error: false,
    records: [],
    permissionCodes: [],
    canCreate: false,
    showCreateModal: false,
    createSubmitting: false,
    selectTypeOptions: SELECT_TYPE_OPTIONS,
    form: createEmptyForm(),
    assayGroupOptions: [],
    assayGroupIndex: -1,
    productPickerColumns: [[], [], []],
    productPickerIndexes: [0, 0, 0]
  },

  onLoad(options = {}) {
    requireLogin();
    this.productPickerState = [];
    if (options.productName) {
      this.setData({ productName: decodeURIComponent(options.productName) });
    }
    if (options.startDate || options.endDate) {
      this.setData({
        startDate: options.startDate ? decodeURIComponent(options.startDate) : '',
        endDate: options.endDate ? decodeURIComponent(options.endDate) : ''
      });
    }
    this.initPage();
  },

  onShow() {
    requireLogin();
  },

  async initPage() {
    await Promise.all([
      this.loadCreateDependencies(),
      this.loadAssays()
    ]);
  },

  async loadCreateDependencies() {
    try {
      const [userInfo, semiProducts, finishedProducts, assayGroups] = await Promise.all([
        getProfile().catch(() => ({})),
        getSemiProducts().catch(() => []),
        getFinishedProducts().catch(() => []),
        queryAssayGroups({ page: 1, size: 1000 }).catch(() => ({ records: [] }))
      ]);

      const permissionCodes = Array.isArray(userInfo && userInfo.permissionCodes)
        ? userInfo.permissionCodes
        : [];

      this.productPickerState = buildProductPickerState(semiProducts, finishedProducts);
      const pickerIndexes = getSafePickerIndexes(this.productPickerState, [0, 0, 0]);
      const productPickerColumns = getPickerColumns(this.productPickerState, pickerIndexes);

      this.setData({
        permissionCodes,
        canCreate: canCreateAssay(permissionCodes),
        assayGroupOptions: assayGroups.records || [],
        productPickerIndexes: pickerIndexes,
        productPickerColumns
      });
    } catch (error) {
      showError(error, '初始化化验新增数据失败');
    }
  },

  onInput(e) {
    this.setData({ [e.currentTarget.dataset.field]: e.detail.value });
  },

  onDateChange(e) {
    this.setData({ [e.currentTarget.dataset.field]: e.detail.value });
  },

  onJudgeChange(e) {
    this.setData({ judgeIndex: Number(e.detail.value) });
  },

  resetSearch() {
    this.setData({
      productName: '',
      testerName: '',
      startDate: '',
      endDate: '',
      judgeIndex: 0,
      page: 1
    });
    this.loadAssays();
  },

  search() {
    this.setData({ page: 1, records: [] });
    this.loadAssays();
  },

  async loadAssays() {
    this.setData({ loading: true, error: false });
    try {
      const selectedJudge = JUDGE_OPTIONS[this.data.judgeIndex];
      const res = await queryAssays({
        page: this.data.page,
        size: this.data.size,
        productName: this.data.productName.trim() || undefined,
        testerName: this.data.testerName.trim() || undefined,
        startDate: this.data.startDate || undefined,
        endDate: this.data.endDate || undefined,
        judgeResult: selectedJudge.value || undefined
      });
      this.setData({
        records: (res.records || []).map(normalizeAssay),
        total: res.total || 0
      });
    } catch (error) {
      this.setData({ error: true });
      showError(error, '化验查询失败');
    } finally {
      this.setData({ loading: false });
    }
  },

  onPageChange(e) {
    this.setData({ page: e.detail.page });
    this.loadAssays();
  },

  openDetail(e) {
    const index = e.currentTarget.dataset.index;
    const item = this.data.records[index];
    if (!item || !item.id) return;
    wx.navigateTo({ url: `/pages/query/assay-detail/index?id=${item.id}` });
  },

  openCreateModal() {
    if (!this.data.canCreate) {
      wx.showToast({
        title: '当前账号没有新增化验权限',
        icon: 'none'
      });
      return;
    }

    const pickerIndexes = getSafePickerIndexes(this.productPickerState, [0, 0, 0]);
    const productPickerColumns = getPickerColumns(this.productPickerState, pickerIndexes);
    this.setData({
      showCreateModal: true,
      assayGroupIndex: -1,
      productPickerIndexes: pickerIndexes,
      productPickerColumns,
      form: createEmptyForm()
    });
  },

  closeCreateModal() {
    if (this.data.createSubmitting) {
      return;
    }
    this.setData({
      showCreateModal: false,
      assayGroupIndex: -1,
      form: createEmptyForm()
    });
  },

  noop() {},

  onSelectTypeChange(e) {
    const index = Number(e.detail.value);
    const option = SELECT_TYPE_OPTIONS[index] || SELECT_TYPE_OPTIONS[0];
    const nextForm = {
      ...this.data.form,
      selectType: option.value,
      selectTypeLabel: option.label,
      productId: null,
      productName: '',
      relatedId: null,
      relatedName: ''
    };
    this.setData({
      form: nextForm,
      assayGroupIndex: -1
    });
  },

  onProductPickerColumnChange(e) {
    const indexes = [...this.data.productPickerIndexes];
    indexes[e.detail.column] = Number(e.detail.value);

    if (e.detail.column === 0) {
      indexes[1] = 0;
      indexes[2] = 0;
    } else if (e.detail.column === 1) {
      indexes[2] = 0;
    }

    const safeIndexes = getSafePickerIndexes(this.productPickerState, indexes);
    this.setData({
      productPickerIndexes: safeIndexes,
      productPickerColumns: getPickerColumns(this.productPickerState, safeIndexes)
    });
  },

  onProductPickerChange(e) {
    const pickerIndexes = (e.detail.value || []).map(item => Number(item));
    const safeIndexes = getSafePickerIndexes(this.productPickerState, pickerIndexes);
    const product = getSelectedProduct(this.productPickerState, safeIndexes);
    if (!product) {
      wx.showToast({
        title: '当前分类下暂无可选产品',
        icon: 'none'
      });
      return;
    }

    this.setData({
      productPickerIndexes: safeIndexes,
      productPickerColumns: getPickerColumns(this.productPickerState, safeIndexes),
      'form.productId': product.id,
      'form.productName': product.name
    });
  },

  onAssayGroupChange(e) {
    const index = Number(e.detail.value);
    const group = this.data.assayGroupOptions[index];
    if (!group) {
      return;
    }
    this.setData({
      assayGroupIndex: index,
      'form.relatedId': group.id,
      'form.relatedName': group.standardName
    });
  },

  onFormDateChange(e) {
    this.setData({ 'form.sampleDate': e.detail.value });
  },

  onMetricInput(e) {
    const field = e.currentTarget.dataset.field;
    const value = sanitizeMetricValue(e.detail.value);
    this.setData({
      [`form.${field}`]: value
    });
  },

  validateForm() {
    const { form } = this.data;
    if (form.selectType === '1' && !form.productId) {
      wx.showToast({ title: '请选择化验产品名称', icon: 'none' });
      return false;
    }
    if (form.selectType === '2' && !form.relatedId) {
      wx.showToast({ title: '请选择批量化验组', icon: 'none' });
      return false;
    }
    if (!form.sampleDate) {
      wx.showToast({ title: '请选择采样日期', icon: 'none' });
      return false;
    }
    return true;
  },

  buildCreatePayload() {
    const { form } = this.data;
    const payload = {
      selectType: form.selectType,
      sampleDate: dayjs(form.sampleDate).format('YYYY-MM-DD')
    };

    if (form.selectType === '1' && form.productId) {
      payload.productId = form.productId;
    }
    if (form.selectType === '2' && form.relatedId) {
      payload.relatedId = form.relatedId;
    }

    METRIC_FIELDS.forEach(metric => {
      if (form[metric.field] !== '') {
        payload[metric.field] = form[metric.field];
      }
    });

    return payload;
  },

  async submitCreateForm() {
    if (!this.validateForm()) {
      return;
    }

    this.setData({ createSubmitting: true });
    try {
      await createAssays([this.buildCreatePayload()]);
      wx.showToast({
        title: '新增成功',
        icon: 'success'
      });
      this.setData({
        showCreateModal: false,
        createSubmitting: false,
        assayGroupIndex: -1,
        form: createEmptyForm(),
        page: 1
      });
      this.loadAssays();
    } catch (error) {
      this.setData({ createSubmitting: false });
      showError(error, '新增化验失败');
    }
  }
});
