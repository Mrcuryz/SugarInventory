import request from '../utils/request';

export function parseCode(code) {
  return request('/api/pallet-codes/parse', 'GET', { code });
}

export function getPalletInventory(code) {
  return request(`/api/pallet-codes/${encodeURIComponent(code)}/inventory`, 'GET');
}

export function getPalletAssay(code) {
  return request(`/api/pallet-codes/${encodeURIComponent(code)}/assay`, 'GET');
}

export function getPalletFlowCycles(code, params = {}) {
  return request(`/api/pallet-codes/${encodeURIComponent(code)}/flows/cycles`, 'GET', params);
}

export function getPalletFlowDetails(code, cycleNo) {
  return request(`/api/pallet-codes/${encodeURIComponent(code)}/flows`, 'GET', { cycleNo });
}

export function pagePalletCodes(params = {}) {
  return request('/api/pallet-codes', 'POST', params);
}

