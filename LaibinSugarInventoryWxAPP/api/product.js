import request from '../utils/request';

export function getSemiProducts() {
  return request('/api/products/semi-products', 'GET');
}

export function getFinishedProducts() {
  return request('/api/products/finished-products', 'GET');
}

export function getProductsByStatus(status) {
  return request('/api/products/product', 'GET', { status });
}
