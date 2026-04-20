const BASE_URL = "http://localhost:8080";
// const BASE_URL = "http://124.220.1.37:8080";
// const BASE_URL = "http://cscgood.mynatapp.cc";

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
          wx.request({
            url: BASE_URL + "/api/auth/manual-bind",
            method: "POST",
            data: {
              employeeId,
              namePart,
              code: res.code
            },
            header: {
              "Content-Type": "application/json"
            },
            success: (res) => {
              if (res.statusCode === 200 && res.data.code === 200) {
                const data = res.data.data;
                wx.setStorageSync("token", data.token);
                wx.setStorageSync("role", data.roleCode);

                setTimeout(() => {
                  wx.showToast({ title: "绑定成功！", icon: "success" });
                  wx.switchTab({ url: "/pages/workbench/index/index" });
                }, 1000);
              } else {
                wx.showToast({ title: res.data.msg || "绑定失败", icon: "none" });
              }
            },
            fail: (err) => {
              console.error("绑定请求失败：", err);
              wx.showToast({ title: "网络错误", icon: "none" });
            }
          });
        } else {
          wx.showToast({ title: "获取登录凭证失败", icon: "none" });
        }
      }
    });
  }
});
