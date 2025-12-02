const BASE_URL = "https://ccgl.site";
// const BASE_URL = "http://localhost:8080";
// const BASE_URL = "https://cscgood.mynatapp.cc";

function request(url, method, data = {}) {
  return new Promise((resolve, reject) => {
    wx.request({
      url: BASE_URL + url,
      method: method,
      data: data,
      header: {
        "Authorization": "Bearer " + wx.getStorageSync("token"),
        "Content-Type": "application/json"
      },
      success: res => {
        if (res.statusCode === 200) {
          const result = res.data;
          console.log("res:", res)
          if (result.code === 200) {
            resolve(result.data); // 解析 `data`，否则 `login.js` 里 `data.token` 会报错
          } else if (result.code === 403) { // 权限不足
            wx.showModal({
              title: "权限不足！",
              icon: "none",
              duration: 2000
            });
            reject(result);
          } else if (result.code === 401) { // 认证失败，可能是 token 过期
            wx.removeStorageSync('token');
            wx.redirectTo({ url: '/pages/login/index' });
            wx.showToast({
              title: "登录过期，请重新登录",
              icon: "none",
              duration: 2000
            });

            setTimeout(() => {
              wx.clearStorageSync(); // 清除缓存中的 token
              wx.reLaunch({
                url: "/pages/login/login"
              }); // 重新跳转到登录页面
            }, 1500);

            reject(result);
          } else {
            // wx.showToast({
            //   title: result.msg || "请求失败",
            //   icon: "none"
            // });
            wx.showModal({
              title: '提示',
              content: result.msg || "请求失败",
              showCancel: false,
              confirmText: '知道了'
            })
            reject(result);
          }
        } else {
          wx.showToast({
            title: "请求错误: " + res.statusCode,
            icon: "none"
          });
          reject(res);
        }
      },
      fail: err => {
        console.log("err:", err)
        wx.showToast({
          title: "网络错误，请稍后重试",
          icon: "none"
        });
        reject(err);
      }
    });
  });
}

export default request;