import request from "../../utils/request";

Page({
  data: {
    code: "",
  },

  getPhoneNumber(e) {
    if (e.detail.errMsg === 'getPhoneNumber:ok') {
      // 用户同意授权，可以进行后续操作
      console.log('用户同意授权');
      // 这里可以进行手机号的获取和处理
    } else {
      // 用户拒绝授权
      console.log('用户拒绝授权');
      wx.showToast({
        title: '您需要授权才能使用此功能',
        icon: 'none'
      });
    }
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
      },
    });
  },

  onGetPhoneNumber(e) {
    if (e.detail.errMsg === "getPhoneNumber:ok") {
      const phoneCode = e.detail.code; // ✅ 正确获取微信加密手机号的 code
      console.log("getPhoneNumber detail:", e.detail);
      wx.login({
        success: (res) => {
          const loginCode = res.code;
          if (!loginCode || !phoneCode) {
            wx.showToast({ title: "code获取失败", icon: "none" });
            return;
          }
          console.log(loginCode)
          console.log(phoneCode)
  
          request("/api/auth/phone-bind", "POST", {
            code: loginCode,
            phoneCode: phoneCode
          })
            .then((data) => {
              wx.setStorageSync("token", data.token);
              setTimeout(() => {
                wx.showToast({ title: "绑定成功！", icon: "success" });
                wx.switchTab({ url: "/pages/home/home" });
              }, 1000);
            })
            .catch((err) => {
              wx.showToast({ title: err.msg || "绑定失败", icon: "none" });
              wx.showModal({
                title: "绑定失败",
                content: "是否通过工号绑定？",
                success: (res) => {
                  if (res.confirm) {
                    wx.navigateTo({ url: "/pages/bind-manual/bind-manual" });
                  }
                },
              });
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
            wx.navigateTo({ url: "/pages/bind-manual/bind-manual" });
          }
        },
      });
    }
  },  
});
