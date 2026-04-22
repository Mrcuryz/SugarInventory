export function normalizeScanCode(raw) {
  const value = (raw || '').trim();
  if (!value) return '';

  try {
    const url = new URL(value);
    const code = url.searchParams.get('code') || url.searchParams.get('palletCode');
    if (code) return code.trim().toUpperCase();
  } catch (e) {
    // Raw QR code is expected in most warehouse scans.
  }

  const match = value.match(/[A-Za-z0-9]{6,32}/);
  return (match ? match[0] : value).trim().toUpperCase();
}

export function validatePalletCode(code) {
  if (!code) return '二维码不能为空';
  if (!/^[A-Z0-9]{6,32}$/.test(code)) return '二维码格式不正确';
  return '';
}

export function scanCode() {
  return new Promise((resolve, reject) => {
    wx.scanCode({
      onlyFromCamera: false,
      scanType: ['qrCode', 'barCode'],
      success: (res) => {
        const code = normalizeScanCode(res.result);
        const error = validatePalletCode(code);
        if (error) {
          reject(new Error(error));
          return;
        }
        resolve(code);
      },
      fail: reject
    });
  });
}
