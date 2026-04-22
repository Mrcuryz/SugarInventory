import { bindPhone, wxLoginCode } from '../../../api/auth';
import { setAuth } from '../../../utils/storage';
import { showToast } from '../../../utils/toast';

Page({
  data: {
    loading: false
  },

  async onGetPhoneNumber(e) {
    if (e.detail.errMsg !== 'getPhoneNumber:ok') {
      wx.showModal({
        title: '授权未完成',
        content: '如果当前微信手机号无法使用，可以改用工号绑定完成身份校验。',
        confirmText: '工号绑定',
        success: (res) => {
          if (res.confirm) wx.redirectTo({ url: '/pages/auth/bind-manual/index' });
        }
      });
      return;
    }

    if (this.data.loading) return;
    this.setData({ loading: true });

    try {
      const code = await wxLoginCode();
      const data = await bindPhone({ code, phoneCode: e.detail.code });
      setAuth(data);
      showToast('绑定成功', 'success');
      wx.switchTab({ url: '/pages/workbench/index/index' });
    } catch (error) {
      wx.showModal({
        title: '手机号绑定失败',
        content: (error && error.msg) || '手机号未匹配到员工信息，可改用工号绑定。',
        confirmText: '工号绑定',
        success: (res) => {
          if (res.confirm) wx.redirectTo({ url: '/pages/auth/bind-manual/index' });
        }
      });
    } finally {
      this.setData({ loading: false });
    }
  },

  goManual() {
    wx.redirectTo({ url: '/pages/auth/bind-manual/index' });
  }
});
