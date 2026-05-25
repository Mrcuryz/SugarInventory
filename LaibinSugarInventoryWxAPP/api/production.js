import request from '../utils/request';

export function pageProductionOrders(params = {}) {
  return request('/api/production/orders', 'GET', params);
}

export function getProductionOrderDetail(id) {
  return request(`/api/production/orders/${id}`, 'GET');
}

export function pickProductionMaterials(orderId, params = {}) {
  return request(`/api/production/orders/${orderId}/materials/pick`, 'POST', params);
}
