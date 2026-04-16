export const TOKEN_KEY = 'token';
export const ROLE_KEY = 'role';
export const RECENT_SCANS_KEY = 'recentScans';
export const SCAN_DEFAULTS_KEY = 'scanDefaults';

export function getToken() {
  return wx.getStorageSync(TOKEN_KEY) || '';
}

export function setAuth(data = {}) {
  if (data.token) wx.setStorageSync(TOKEN_KEY, data.token);
  if (data.roleCode) wx.setStorageSync(ROLE_KEY, data.roleCode);
}

export function clearAuth() {
  wx.removeStorageSync(TOKEN_KEY);
  wx.removeStorageSync(ROLE_KEY);
}

export function pushRecentScan(scan) {
  const list = wx.getStorageSync(RECENT_SCANS_KEY) || [];
  const next = [
    { ...scan, scannedAt: new Date().toISOString() },
    ...list.filter(item => item.code !== scan.code)
  ].slice(0, 10);
  wx.setStorageSync(RECENT_SCANS_KEY, next);
  return next;
}

export function getRecentScans(limit = 3) {
  const list = wx.getStorageSync(RECENT_SCANS_KEY) || [];
  return list.slice(0, limit);
}

export function getScanDefaults() {
  return wx.getStorageSync(SCAN_DEFAULTS_KEY) || {};
}

export function setScanDefaults(defaults) {
  wx.setStorageSync(SCAN_DEFAULTS_KEY, defaults || {});
}

