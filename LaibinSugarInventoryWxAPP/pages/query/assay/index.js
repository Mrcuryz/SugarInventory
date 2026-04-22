import { queryAssays } from '../../../api/query';
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
  }
});