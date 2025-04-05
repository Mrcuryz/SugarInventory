import request from "../../utils/request";

Page({
  data: {
    userInfo: {},
    currentDate: "",
  },

  onLoad() {
    this.getUserInfo();
    this.setCurrentDate();
  },

  getUserInfo() {
    request("/api/user/info", "GET")
      .then((data) => {
        this.setData({ userInfo: data });
      })
      .catch(() => {
        wx.showToast({ title: "获取用户信息失败", icon: "none" });
      });
  },

  setCurrentDate() {
    const date = new Date();
    const formattedDate = `${date.getFullYear()}年${date.getMonth() + 1}月${date.getDate()}日`;
    this.setData({ currentDate: formattedDate });
  },

  goToPage(event) {
    const url = event.currentTarget.dataset.url;
    wx.navigateTo({ url });
  },
});
