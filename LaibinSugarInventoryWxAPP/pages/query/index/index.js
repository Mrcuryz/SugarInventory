import { parseCode } from '../../../api/pallet';
import { requireLogin } from '../../../utils/auth';
import { scanCode } from '../../../utils/scan';
import { showError, showToast } from '../../../utils/toast';

function isNotFoundError(error) {
  const message = String(error && (error.msg || error.message || error.errMsg) || '').toLowerCase();
  return message.includes('不存在')
    || message.includes('未找到')
    || message.includes('无对应')
    || message.includes('not found');
}

Page({
  data: {
    code: '',
    queryState: 'idle',
    querying: false,
    quickTips: [
      '支持手动输入托盘码，也支持直接扫码。',
      '查到托盘后会进入详情页查看状态、位置与可执行操作。'
    ],
    modules: [
      { key: 'inventory', title: '库存查询', desc: '第二阶段补充移动端库存摘要查询能力。' },
      { key: 'assay', title: '化验查询', desc: '第二阶段补充化验记录列表与详情。' },
      { key: 'record', title: '作业记录', desc: '第二阶段补充入库、出库、调拨记录中心。' }
    ]
  },

  onShow() {
    requireLogin();
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

  showComingSoon(e) {
    showToast(`${e.currentTarget.dataset.title}将在第二阶段补充`);
  }
});
