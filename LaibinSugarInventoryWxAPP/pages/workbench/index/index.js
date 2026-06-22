import { getProfile } from '../../../api/auth';
import { getTaskList } from '../../../api/task';
import { enrichTask } from '../../../utils/dict';
import { requireLogin } from '../../../utils/auth';
import { getRecentScans } from '../../../utils/storage';

function formatRecentTime(value) {
  return String(value || '').replace('T', ' ').slice(0, 16);
}

Page({
  data: {
    userInfo: {},
    currentDate: '',
    pendingCount: 0,
    recentTasks: [],
    recentScans: [],
    tips: [
      '同一批连续扫码会自动拦截重复二维码。',
      '任务创建后请及时在任务池或任务中心确认。'
    ],
    actions: [
      { key: 'in', title: '扫码入库', desc: '扫码确认二维码并创建入库任务', mode: 'in', icon: '/assets/icons-v2/action-in.png', primary: true },
      { key: 'out', title: '扫码出库', desc: '按二维码创建出库任务', mode: 'out', icon: '/assets/icons-v2/action-out.png' },
      { key: 'production', title: '生产作业', desc: '查看订单并扫码领用半成品', route: 'production', icon: '/assets/icons-v2/action-query.png' },
      { key: 'transfer', title: '扫码调拨', desc: '指定目标库位创建调拨任务', mode: 'transfer', icon: '/assets/icons-v2/action-out.png' },
      { key: 'query', title: '单码查询', desc: '手输或扫码查看二维码详情', route: 'query', icon: '/assets/icons-v2/action-query.png' }
    ]
  },

  onShow() {
    if (!requireLogin()) return;
    this.setCurrentDate();
    this.loadProfile();
    this.loadTasks();
    this.setData({
      recentScans: getRecentScans(3).map(item => ({
        ...item,
        scannedAtText: formatRecentTime(item.scannedAt)
      }))
    });
  },

  setCurrentDate() {
    const date = new Date();
    const text = `${date.getFullYear()}年${date.getMonth() + 1}月${date.getDate()}日`;
    this.setData({ currentDate: text });
  },

  async loadProfile() {
    try {
      const userInfo = await getProfile();
      this.setData({ userInfo: userInfo || {} });
    } catch (e) {
      this.setData({ userInfo: {} });
    }
  },

  async loadTasks() {
    try {
      const res = await getTaskList({ status: 'PENDING', pageNum: 1, pageSize: 3 });
      const records = (res && res.records || []).map(enrichTask);
      this.setData({
        recentTasks: records,
        pendingCount: res && res.total || records.length
      });
    } catch (e) {
      this.setData({ recentTasks: [], pendingCount: 0 });
    }
  },

  goScan(e) {
    const mode = e.currentTarget.dataset.mode;
    const route = e.currentTarget.dataset.route;
    if (route === 'query') {
      wx.switchTab({ url: '/pages/query/index/index' });
      return;
    }
    if (route === 'production') {
      wx.navigateTo({ url: '/pages/production/orders/index' });
      return;
    }
    wx.setStorageSync('preferredScanMode', mode);
    wx.switchTab({ url: '/pages/scan/index/index' });
  },

  goTasks() {
    wx.switchTab({ url: '/pages/tasks/index/index' });
  },

  goPallet(e) {
    const code = e.currentTarget.dataset.code;
    if (!code) return;
    wx.navigateTo({ url: `/pages/pallet/detail/index?code=${encodeURIComponent(code)}` });
  }
});
