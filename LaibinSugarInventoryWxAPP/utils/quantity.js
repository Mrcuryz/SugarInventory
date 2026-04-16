export function sanitizePositiveInteger(value) {
  const raw = String(value == null ? '' : value).replace(/[^\d]/g, '');
  if (!raw) return '';
  return String(Math.max(1, parseInt(raw, 10)));
}

export function normalizeQuantityByUnit(unit, value) {
  if (unit === '0') return '1';
  return sanitizePositiveInteger(value);
}

export function getPiecesLimit(product) {
  const value = Number(product && (product.piecesPerPallet || product.pieces_per_pallet));
  return Number.isFinite(value) && value > 0 ? value : 0;
}

export function validateQuantity({ unit, quantity, product, fallbackLimit = 0 }) {
  if (unit === '0') {
    return { valid: true, value: 1 };
  }

  const numeric = parseInt(quantity, 10);
  if (!Number.isInteger(numeric) || numeric <= 0) {
    return { valid: false, message: '件数必须是正整数' };
  }

  const limit = getPiecesLimit(product) || fallbackLimit;
  if (limit > 0 && numeric > limit) {
    return { valid: false, message: `件数不能超过每板件数 ${limit}` };
  }

  return { valid: true, value: numeric };
}
