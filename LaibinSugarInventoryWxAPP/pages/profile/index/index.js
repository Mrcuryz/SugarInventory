import { getProfile } from '../../../api/auth';
import { logout, requireLogin } from '../../../utils/auth';
import { confirm, showToast } from '../../../utils/toast';

function getBindMethodLabel(userInfo = {}) {
  if (userInfo.bindMethod === 'WECHAT') return '手机号绑定';
  if (userInfo.bindMethod === 'MANUAL') return '工号绑定';
  if (userInfo.mobile || userInfo.phone) return '手机号已登记';
  return '未绑定';
}

function buildAccountMeta(userInfo = {}) {
  const mobile = userInfo.mobile || userInfo.phone || '';

  return [
    { label: '角色', value: userInfo.roleName || userInfo.roleCode || '未分配' },
    { label: '手机号', value: mobile || '未绑定' },
    { label: '绑定方式', value: getBindMethodLabel(userInfo) },
    { label: '当前版本', value: 'v2.0' }
  ];
}

Page({
  data: {
    userInfo: {},
    accountMeta: buildAccountMeta(),
    menus: [
      { key: 'help', title: '帮助中心', desc: '查看登录、扫码、任务处理等操作说明。', icon: '/assets/icons-line/icon-help.svg' },
      { key: 'contact', title: '联系管理员', desc: '员工信息异常或权限问题，请联系系统管理员。', icon: '/assets/icons-line/icon-contact.svg' },
      { key: 'version', title: '版本信息', desc: 'v2.0：支持二维码、库存、化验和作业记录查询。', icon: '/assets/icons-line/icon-version.svg' }
    ]
  },

  onShow() {
    if (!requireLogin()) return;
    this.loadProfile();
  },

  async loadProfile() {
    try {
      const userInfo = await getProfile();
      this.setData({
        userInfo: userInfo || {},
        accountMeta: buildAccountMeta(userInfo || {})
      });
    } catch (error) {
      this.setData({
        userInfo: {},
        accountMeta: buildAccountMeta()
      });
    }
  },

  onMenuTap(e) {
    const key = e.currentTarget.dataset.key;
    if (key === 'help') {
      wx.navigateTo({ url: '/pages/help/index/index' });
      return;
    }
    if (key === 'contact') {
      showToast('请联系仓储系统管理员');
    }
  },

  async onLogout() {
    const ok = await confirm('退出当前账号后，需要重新登录。确认退出？', '退出登录');
    if (!ok) return;
    logout();
  }
});
