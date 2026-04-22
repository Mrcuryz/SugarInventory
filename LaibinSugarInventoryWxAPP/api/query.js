import request from '../utils/request';

function compactParams(params = {}) {
  return Object.keys(params).reduce((result, key) => {
    const value = params[key];
    if (value !== undefined && value !== null && value !== '') {
      result[key] = value;
    }
    return result;
  }, {});
}

export function queryProductStock(productStatus, productName = '') {
  return request('/api/inventory/stock', 'GET', compactParams({ productStatus, productName }));
}

export function pageProductStock(params = {}) {
  return request('/api/inventory/stock/page', 'GET', compactParams(params));
}

export function pagePalletCodes(params = {}) {
  return request('/api/pallet-codes', 'POST', compactParams(params));
}

export function queryAssays(params = {}) {
  return request('/api/assay/query', 'POST', compactParams(params));
}

export function getAssayDetail(id) {
  return request(`/api/assay/${id}`, 'GET');
}

export function queryOperationLogs(params = {}) {
  return request('/api/logs/query', 'POST', compactParams(params));
}

export function queryWarehouses(params = {}) {
  return request('/api/inventory/query', 'GET', compactParams(params));
}

export function listScreenMeshes(params = {}) {
  return request('/api/screen-mesh/list', 'GET', compactParams(params));
}

export function pageProducts(params = {}) {
  return request('/api/products/product/page', 'GET', compactParams(params));
}

export function pageScreenMeshes(params = {}) {
  return request('/api/screen-mesh/page', 'GET', compactParams(params));
}
