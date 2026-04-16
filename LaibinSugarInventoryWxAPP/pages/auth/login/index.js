import { loginByWechat, wxLoginCode } from '../../../api/auth';
import { isLoggedIn } from '../../../utils/auth';
import { setAuth } from '../../../utils/storage';
import { showError, showToast } from '../../../utils/toast';

Page({
  data: {
    loading: false
  },

  onLoad() {
    if (isLoggedIn()) {
      wx.switchTab({ url: '/pages/workbench/index/index' });
    }
  },

  async onLogin() {
    if (this.data.loading) return;
    this.setData({ loading: true });

    try {
      const code = await wxLoginCode();
      const data = await loginByWechat(code);
      setAuth(data);
      showToast('登录成功', 'success');
      wx.switchTab({ url: '/pages/workbench/index/index' });
    } catch (error) {
      const msg = error && (error.msg || error.message || '');
      if (msg.includes('用户不存在')) {
        wx.showModal({
          title: '首次登录需要绑定',
          content: '请先通过手机号或工号完成员工身份绑定。',
          confirmText: '手机号绑定',
          cancelText: '工号绑定',
          success: (res) => {
            wx.navigateTo({
              url: res.confirm ? '/pages/auth/bind-phone/index' : '/pages/auth/bind-manual/index'
            });
          }
        });
      } else {
        showError(error, '登录失败');
      }
    } finally {
      this.setData({ loading: false });
    }
  },

  goBindPhone() {
    wx.navigateTo({ url: '/pages/auth/bind-phone/index' });
  },

  goBindManual() {
    wx.navigateTo({ url: '/pages/auth/bind-manual/index' });
  }
});

