import { queryAssays } from '../../../api/query';
import { requireLogin } from '../../../utils/auth';
import { formatAssayStandard, formatDateTime } from '../../../utils/dict';
import { showError } from '../../../utils/toast';

const QUALIFIED_OPTIONS = ['全部', '合格', '不合格', '无标准'];

function normalizeAssay(item) {
  const qualified = item.isQualified || '无标准';
  return {
    ...item,
    sampleDateText: item.sampleDate || '-',
    createdAtText: formatDateTime(item.createdAt),
    standardText: formatAssayStandard(item.qualifiedStandards),
    qualifiedLabel: qualified,
    qualifiedType: qualified === '合格' ? 'success' : (qualified === '不合格' ? 'danger' : 'info')
  };
}

Page({
  data: {
    productName: '',
    testerName: '',
    startDate: '',
    endDate: '',
    qualifiedOptions: QUALIFIED_OPTIONS,
    qualifiedIndex: 0,
    page: 1,
    size: 10,
    total: 0,
    loading: false,
    error: false,
    records: []
  },

  onLoad(options = {}) {
    requireLogin();
    if (options.productName) {
      this.setData({ productName: decodeURIComponent(options.productName) });
    }
    this.search();
  },

  onShow() {
    requireLogin();
  },

  onInput(e) {
    this.setData({ [e.currentTarget.dataset.field]: e.detail.value });
  },

  onDateChange(e) {
    this.setData({ [e.currentTarget.dataset.field]: e.detail.value });
  },

  onQualifiedChange(e) {
    this.setData({ qualifiedIndex: Number(e.detail.value) });
  },

  resetSearch() {
    this.setData({
      productName: '',
      testerName: '',
      startDate: '',
      endDate: '',
      qualifiedIndex: 0,
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
      const qualified = QUALIFIED_OPTIONS[this.data.qualifiedIndex];
      const res = await queryAssays({
        page: this.data.page,
        size: this.data.size,
        productName: this.data.productName.trim() || undefined,
        testerName: this.data.testerName.trim() || undefined,
        startDate: this.data.startDate || undefined,
        endDate: this.data.endDate || undefined,
        isQualified: qualified === '全部' ? undefined : qualified
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
    if (!item) return;
    wx.setStorageSync('p1AssayDetail', item);
    wx.navigateTo({ url: `/pages/query/assay-detail/index?id=${item.id}` });
  }
});
