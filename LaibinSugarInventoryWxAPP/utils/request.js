const BASE_URL = "http://localhost:8080";
// const BASE_URL = "http://localhost:8080";
// const BASE_URL = "https://cscgood.mynatapp.cc";

function sanitizeData(data = {}) {
  if (!data || typeof data !== 'object' || Array.isArray(data)) return data;
  return Object.keys(data).reduce((result, key) => {
    const value = data[key];
    if (value !== undefined && value !== null && value !== '') {
      result[key] = value;
    }
    return result;
  }, {});
}

function request(url, method, data = {}) {
  return new Promise((resolve, reject) => {
    const token = wx.getStorageSync("token");
    wx.request({
      url: BASE_URL + url,
      method: method,
      data: method === 'GET' ? sanitizeData(data) : data,
      header: {
        "Authorization": token ? "Bearer " + token : "",
        "Content-Type": "application/json"
      },
      success: res => {
        if (res.statusCode === 200) {
          const result = res.data;
          if (result.code === 200) {
            resolve(result.data);
          } else if (result.code === 403) {
            wx.showModal({
              title: "权限不足！",
              content: result.msg || "当前账号没有执行该操作的权限",
              showCancel: false
            });
            reject(result);
          } else if (result.code === 401) {
            wx.removeStorageSync('token');
            wx.showToast({
              title: "登录过期，请重新登录",
              icon: "none",
              duration: 2000
            });

            setTimeout(() => {
              wx.clearStorageSync();
              wx.reLaunch({
                url: "/pages/auth/login/index"
              });
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
