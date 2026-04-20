import { parseCode } from '../../../api/pallet';
import { requireLogin } from '../../../utils/auth';
import { scanCode } from '../../../utils/scan';
import { showError, showToast } from '../../../utils/toast';

const RECENT_KEY = 'recentPalletQueries';

const MODULES = [
  { key: 'inventory', title: '库存查询', desc: '查看产品库存摘要与托盘明细', icon: '/assets/icons-line/icon-location.svg', url: '/pages/query/inventory/index' },
  { key: 'assay', title: '化验查询', desc: '查看化验记录、结论与标准匹配', icon: '/assets/icons-line/flow-assay.svg', url: '/pages/query/assay/index' },
  { key: 'record', title: '作业记录', desc: '按时间和类型追溯现场动作', icon: '/assets/icons-line/icon-task.svg', url: '/pages/query/records/index' },
  { key: 'product', title: '产品查询', desc: '查看产品基础信息与包装参数', icon: '/assets/icons-line/icon-notice.svg', url: '/pages/query/product/index' },
  { key: 'warehouse', title: '库位查询', desc: '查看库位状态并指定库位入库', icon: '/assets/icons-line/icon-location.svg', url: '/pages/query/warehouse/index' },
  { key: 'mesh', title: '筛网查询', desc: '查看筛网描述与关联产品', icon: '/assets/icons-line/icon-version.svg', url: '/pages/query/screen-mesh/index' }
];

function isNotFoundError(error) {
  const message = String(error && (error.msg || error.message || error.errMsg) || '').toLowerCase();
  return message.includes('不存在')
    || message.includes('未找到')
    || message.includes('无对应')
    || message.includes('not found');
}

function isScanCancel(error) {
  const message = String(error && (error.errMsg || error.message || error.msg) || '').toLowerCase();
  return message.includes('cancel');
}

Page({
  data: {
    code: '',
    queryState: 'idle',
    querying: false,
    recentQueries: [],
    modules: MODULES,
    scenarioTips: [
      '托盘查询：现场单码核对、问题复核、补查托盘状态。',
      '库位查询：查找空位后可直接进入指定库位入库。',
      '化验查询：查看系统返回的结论、指标和标准说明。',
      '作业记录：追溯最近入库、出库、调拨、化验关联动作。'
    ]
  },

  onShow() {
    requireLogin();
    this.loadRecentQueries();
  },

  loadRecentQueries() {
    this.setData({ recentQueries: wx.getStorageSync(RECENT_KEY) || [] });
  },

  saveRecentQuery(code) {
    const now = new Date();
    const timeText = `${now.getMonth() + 1}-${now.getDate()} ${String(now.getHours()).padStart(2, '0')}:${String(now.getMinutes()).padStart(2, '0')}`;
    const next = [
      { code, timeText },
      ...(wx.getStorageSync(RECENT_KEY) || []).filter(item => item.code !== code)
    ].slice(0, 5);
    wx.setStorageSync(RECENT_KEY, next);
    this.setData({ recentQueries: next });
  },

  onInput(e) {
    this.setData({
      code: e.detail.value.trim().toUpperCase(),
      queryState: 'idle'
    });
  },

  async onScan() {
    try {
      const code = await scanCode();
      this.setData({ code });
      await this.submitQuery();
    } catch (error) {
      if (isScanCancel(error)) return;
      this.setData({ queryState: 'error' });
      showError(error, '扫码失败');
    }
  },

  clearCode() {
    this.setData({
      code: '',
      queryState: 'idle',
      querying: false
    });
  },

  async submitQuery() {
    const code = (this.data.code || '').trim().toUpperCase();
    if (this.data.querying) return;
    if (!code) {
      showToast('请输入托盘码');
      return;
    }
    this.setData({
      code,
      querying: true,
      queryState: 'loading'
    });
    try {
      await parseCode(code);
      this.setData({
        querying: false,
        queryState: 'success'
      });
      this.saveRecentQuery(code);
      wx.navigateTo({ url: `/pages/pallet/detail/index?code=${encodeURIComponent(code)}` });
    } catch (error) {
      const queryState = isNotFoundError(error) ? 'empty' : 'error';
      this.setData({
        querying: false,
        queryState
      });
      showError(error, queryState === 'empty' ? '未找到对应托盘' : '查询失败');
    }
  },

  openModule(e) {
    const url = e.currentTarget.dataset.url;
    if (!url) {
      showToast('入口暂未配置');
      return;
    }
    wx.navigateTo({ url });
  },

  openRecent(e) {
    const code = e.currentTarget.dataset.code;
    if (!code) return;
    wx.navigateTo({ url: `/pages/pallet/detail/index?code=${encodeURIComponent(code)}` });
  }
});
