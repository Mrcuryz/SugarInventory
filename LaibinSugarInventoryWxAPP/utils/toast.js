export function showToast(title, icon = 'none') {
  wx.showToast({
    title,
    icon,
    duration: 1800
  });
}

export function showError(error, fallback = '操作失败') {
  const title = error && (error.msg || error.message || error.errMsg) || fallback;
  showToast(title, 'none');
}

export function confirm(content, title = '确认操作') {
  return new Promise((resolve) => {
    wx.showModal({
      title,
      content,
      confirmText: '确认',
      cancelText: '取消',
      success: (res) => resolve(Boolean(res.confirm)),
      fail: () => resolve(false)
    });
  });
}

