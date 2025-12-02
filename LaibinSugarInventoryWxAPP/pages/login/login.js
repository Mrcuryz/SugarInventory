import request from "../../utils/request";
const BASE_URL = "https://ccgl.site";
// const BASE_URL = "http://localhost:8080";
// const BASE_URL = "http://cscgood.mynatapp.cc";

Page({
  onLogin() {
    wx.login({
      success: res => {
        if (res.code) {
          console.log("微信登录 code:", res.code);

          // 不使用封装的 request.js，避免带旧 token
          wx.request({
            url: BASE_URL + "/api/auth/wechat-login",
            method: "POST",
            data: { code: res.code },
            header: {
              "Content-Type": "application/json"
            },
            success: res => {
              if (res.statusCode === 200 && res.data.code === 200) {
                const data = res.data.data;
                console.log("登录返回数据:", data);

                wx.setStorageSync("token", data.token);
                wx.setStorageSync("role", data.roleCode);

                if (data.roleCode != null) {
                  setTimeout(() => {
                    wx.showToast({ title: "登录成功！", icon: "success" });
                  }, 1000);
                  wx.switchTab({ url: "/pages/home/home" });
                }
              } else if (res.data.code === 500 && res.data.msg === "用户不存在") {
                wx.showModal({
                  title: "首次登录请先绑定！",
                  content: "是否授权使用手机号验证？",
                  success: (res) => {
                    if (res.confirm) {
                      wx.navigateTo({ url: "/pages/bind-phone/bind-phone" });
                    }
                  }
                });
              } else {
                wx.showToast({ title: res.data.msg || "登录失败", icon: "none" });
              }
            },
            fail: err => {
              console.error("登录失败:", err);
              wx.showToast({ title: "网络错误，请稍后重试", icon: "none" });
            }
          });
        } else {
          wx.showToast({ title: "微信登录失败", icon: "none" });
        }
      }
    });
  }
});