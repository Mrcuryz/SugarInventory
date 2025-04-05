import request from "../../utils/request";

Page({
  data: {
    employeeId: "",
    namePart: "",
    code: "",
  },

  onInputEmployeeId(e) {
    this.setData({ employeeId: e.detail.value });
  },

  onInputNamePart(e) {
    this.setData({ namePart: e.detail.value });
  },

  onBindWork() {
    const { employeeId, namePart } = this.data;

    if (!employeeId || !namePart) {
      wx.showToast({ title: "请填写完整信息", icon: "none" });
      return;
    }

    wx.login({
      success: (res) => {
        if (res.code) {
          request("/api/auth/manual-bind", "POST", {
            employeeId,
            namePart,
            code: res.code,
          })
            .then((data) => {
              wx.setStorageSync("token", data.token);
              setTimeout(() => {
                wx.showToast({ title: "绑定成功！", icon: "success" });
              }, 1000); // 延迟 1 秒再跳转
              wx.switchTab({ url: "/pages/home/home" });
            })
            .catch((err) => {
              wx.showToast({ title: err.msg || "绑定失败", icon: "none" });
            });
        } else {
          wx.showToast({ title: "获取登录凭证失败", icon: "none" });
        }
      },
    });
  },
});
