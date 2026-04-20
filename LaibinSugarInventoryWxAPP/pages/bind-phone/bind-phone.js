const BASE_URL = "http://localhost:8080";
// const BASE_URL = "http://124.220.1.37:8080";
// const BASE_URL = "http://cscgood.mynatapp.cc";
Page({
  data: {
    code: "",
  },

  onLoad() {
    this.getCode();
  },

  getCode() {
    wx.login({
      success: (res) => {
        if (res.code) {
          this.setData({ code: res.code });
        } else {
          wx.showToast({ title: "获取登录凭证失败", icon: "none" });
        }
      }
    });
  },

  onGetPhoneNumber(e) {
    if (e.detail.errMsg === "getPhoneNumber:ok") {
      const phoneCode = e.detail.code;

      wx.login({
        success: (res) => {
          const loginCode = res.code;
          if (!loginCode || !phoneCode) {
            wx.showToast({ title: "code获取失败", icon: "none" });
            return;
          }

          wx.request({
            url: BASE_URL + "/api/auth/phone-bind",
            method: "POST",
            data: {
              code: loginCode,
              phoneCode: phoneCode
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
                wx.showModal({
                  title: "绑定失败",
                  content: "是否通过工号绑定？",
                  success: (res) => {
                    if (res.confirm) {
                      wx.navigateTo({ url: "/pages/auth/bind-manual/index" });
                    }
                  }
                });
              }
            },
            fail: (err) => {
              console.error("绑定失败：", err);
              wx.showToast({ title: "网络错误，请稍后重试", icon: "none" });
            }
          });
        }
      });
    } else {
      wx.showToast({ title: "授权失败", icon: "none" });
      wx.showModal({
        title: "授权失败",
        content: "您需要授权才能使用此功能。通过工号完成验证？",
        success: (res) => {
          if (res.confirm) {
            wx.navigateTo({ url: "/pages/auth/bind-manual/index" });
          }
        }
      });
    }
  }
});
