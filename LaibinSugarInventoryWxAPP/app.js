// app.js
App({
  onLaunch() {
    const role = wx.getStorageSync("role") || '';
    this.globalData.role = role;
  },
  globalData: {
    userInfo: null,
    role: ''
  }
})