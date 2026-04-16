export function createPoolItem(params) {
  return {
    id: `${Date.now()}_${Math.random().toString(16).slice(2)}`,
    code: params.code,
    mode: params.mode,
    status: params.status || 'success',
    message: params.message || '',
    task: params.task || null,
    pallet: params.pallet || null,
    createdAt: new Date().toISOString()
  };
}

export function hasCode(pool, code) {
  return pool.some(item => item.code === code);
}

export function addPoolItem(pool, item) {
  if (hasCode(pool, item.code)) {
    return { duplicated: true, pool };
  }
  return { duplicated: false, pool: [item, ...pool] };
}

export function removePoolItem(pool, id) {
  return pool.filter(item => item.id !== id);
}

export function successfulCodes(pool) {
  return pool.filter(item => item.status === 'success').map(item => item.code);
}

export function countByStatus(pool) {
  return pool.reduce((acc, item) => {
    acc[item.status] = (acc[item.status] || 0) + 1;
    return acc;
  }, {});
}

