import { clearAuth, getToken } from './storage';

export function isLoggedIn() {
  return Boolean(getToken());
}

export function requireLogin() {
  if (isLoggedIn()) return true;
  wx.reLaunch({ url: '/pages/auth/login/index' });
  return false;
}

export function logout() {
  clearAuth();
  wx.reLaunch({ url: '/pages/auth/login/index' });
}

