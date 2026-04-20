import { getProfile } from '../../../api/auth';
import { logout, requireLogin } from '../../../utils/auth';
import { confirm, showToast } from '../../../utils/toast';

function buildAccountMeta(userInfo = {}) {
  return [
    { label: '角色', value: userInfo.roleCode || '未分配' },
    { label: '手机号', value: userInfo.phone || '未绑定' },
    { label: '绑定方式', value: userInfo.phone ? '手机号绑定' : '工号绑定' },
    { label: '当前版本', value: 'P1 查询版' }
  ];
}

Page({
  data: {
    userInfo: {},
    accountMeta: buildAccountMeta(),
    menus: [
      { key: 'help', title: '帮助中心', desc: '查看登录、扫码、任务处理说明。', icon: '/assets/icons-line/icon-help.svg' },
      { key: 'contact', title: '联系管理员', desc: '员工信息异常、权限问题可联系管理员。', icon: '/assets/icons-line/icon-contact.svg' },
      { key: 'version', title: '版本信息', desc: 'P1 查询版：托盘、库存、化验和作业记录查询。', icon: '/assets/icons-line/icon-version.svg' }
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
      return;
    }
    if (key === 'version') {
      showToast('当前为 P1 查询版');
    }
  },

  async onLogout() {
    const ok = await confirm('退出当前账号后，需要重新登录。确认退出？', '退出登录');
    if (!ok) return;
    logout();
  }
});
