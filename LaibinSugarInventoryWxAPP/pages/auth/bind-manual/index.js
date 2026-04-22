import { bindManual, wxLoginCode } from '../../../api/auth';
import { setAuth } from '../../../utils/storage';
import { showError, showToast } from '../../../utils/toast';

Page({
  data: {
    employeeId: '',
    namePart: '',
    loading: false
  },

  onInput(e) {
    const field = e.currentTarget.dataset.field;
    this.setData({ [field]: e.detail.value });
  },

  async onBind() {
    const { employeeId, namePart } = this.data;
    if (!employeeId || !namePart) {
      showToast('请填写工号和姓名');
      return;
    }
    if (this.data.loading) return;
    this.setData({ loading: true });

    try {
      const code = await wxLoginCode();
      const data = await bindManual({ employeeId, namePart, code });
      setAuth(data);
      showToast('绑定成功', 'success');
      wx.switchTab({ url: '/pages/workbench/index/index' });
    } catch (error) {
      showError(error, '工号绑定失败');
    } finally {
      this.setData({ loading: false });
    }
  }
});
