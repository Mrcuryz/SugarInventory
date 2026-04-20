import request from '../utils/request';

const BASE_URL = 'http://localhost:8080';

function postPublic(url, data) {
  return new Promise((resolve, reject) => {
    wx.request({
      url: BASE_URL + url,
      method: 'POST',
      data,
      header: {
        'Content-Type': 'application/json'
      },
      success: (res) => {
        if (res.statusCode === 200 && res.data && res.data.code === 200) {
          resolve(res.data.data);
          return;
        }
        reject(res.data || res);
      },
      fail: reject
    });
  });
}

export function loginByWechat(code) {
  return postPublic('/api/auth/wechat-login', { code });
}

export function bindPhone(params) {
  return postPublic('/api/auth/phone-bind', params);
}

export function bindManual(params) {
  return postPublic('/api/auth/manual-bind', params);
}

export function getProfile() {
  return request('/api/user/info', 'GET');
}

export function wxLoginCode() {
  return new Promise((resolve, reject) => {
    wx.login({
      success: (res) => {
        if (res.code) {
          resolve(res.code);
        } else {
          reject(new Error('微信登录凭证获取失败'));
        }
      },
      fail: reject
    });
  });
}

