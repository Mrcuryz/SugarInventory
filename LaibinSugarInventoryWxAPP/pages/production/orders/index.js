import { pageProductionOrders } from '../../../api/production';
import { requireLogin } from '../../../utils/auth';
import { showError } from '../../../utils/toast';

const STATUS_TABS = [
  { key: '', label: '全部' },
  { key: 'ISSUED', label: '待领料' },
  { key: 'MATERIALED', label: '已领料' },
  { key: 'PREPRINTED', label: '已预打印' },
  { key: 'WAIT_INBOUND', label: '待入库' },
  { key: 'PART_INBOUND', label: '部分入库' },
  { key: 'COMPLETED', label: '已完成' }
];

const STATUS_MAP = {
  ISSUED: { label: '待领料', type: 'primary' },
  MATERIALING: { label: '领料中', type: 'warning' },
  MATERIALED: { label: '已领料', type: 'success' },
  OUTPUT_BINDING: { label: '产出中', type: 'warning' },
  PREPRINTED: { label: '已预打印', type: 'warning' },
  WAIT_INBOUND: { label: '待入库', type: 'primary' },
  PART_INBOUND: { label: '部分入库', type: 'warning' },
  COMPLETED: { label: '已完成', type: 'success' },
  CANCELED: { label: '已取消', type: 'info' }
};

function today() {
  const date = new Date();
  const month = String(date.getMonth() + 1).padStart(2, '0');
  const day = String(date.getDate()).padStart(2, '0');
  return `${date.getFullYear()}-${month}-${day}`;
}

function compact(value) {
  return value && value !== '-' ? value : '暂无';
}

function decorateOrder(item) {
  const status = STATUS_MAP[item.status] || { label: item.status || '-', type: 'info' };
  return {
    ...item,
    orderTypeText: item.orderType === 'SEMI' ? '半成品生产' : '成品生产',
    statusLabel: status.label,
    statusType: status.type,
    plannedMaterialText: compact(item.plannedMaterialText),
    plannedOutputText: compact(item.plannedOutputText),
    actualMaterialText: compact(item.actualMaterialText || (item.actualMaterialCount ? `${item.actualMaterialCount}项` : '暂无')),
    outputText: compact(item.outputText || (item.outputCount ? `${item.outputCount}项` : '暂无')),
    inboundProgressText: item.inboundProgress || `${item.inboundQrCount || 0}/${item.requiredQrCount || 0}`
  };
}

Page({
  data: {
    statusTabs: STATUS_TABS,
    status: '',
    orderNo: '',
    todayOnly: true,
    page: 1,
    size: 20,
    total: 0,
    loading: false,
    orders: []
  },

  onLoad() {
    requireLogin();
    this.loadOrders(true);
  },

  onShow() {
    requireLogin();
  },

  onOrderNoInput(e) {
    this.setData({ orderNo: e.detail.value });
  },

  onStatusTap(e) {
    this.setData({ status: e.currentTarget.dataset.key || '', page: 1 });
    this.loadOrders(true);
  },

  toggleToday() {
    this.setData({ todayOnly: !this.data.todayOnly, page: 1 });
    this.loadOrders(true);
  },

  search() {
    this.setData({ page: 1 });
    this.loadOrders(true);
  },

  resetSearch() {
    this.setData({ orderNo: '', status: '', todayOnly: true, page: 1 });
    this.loadOrders(true);
  },

  async loadOrders(reset = false) {
    this.setData({ loading: true });
    try {
      const currentPage = reset ? 1 : this.data.page;
      const date = this.data.todayOnly ? today() : '';
      const res = await pageProductionOrders({
        page: currentPage,
        size: this.data.size,
        orderNo: this.data.orderNo.trim() || undefined,
        status: this.data.status || undefined,
        startDate: date || undefined,
        endDate: date || undefined
      });
      const records = (res.records || []).map(decorateOrder);
      this.setData({
        orders: reset ? records : this.data.orders.concat(records),
        total: res.total || 0,
        page: currentPage
      });
    } catch (error) {
      showError(error, '生产订单加载失败');
      if (reset) this.setData({ orders: [], total: 0 });
    } finally {
      this.setData({ loading: false });
    }
  },

  onReachBottom() {
    if (this.data.loading || this.data.orders.length >= this.data.total) return;
    this.setData({ page: this.data.page + 1 });
    this.loadOrders(false);
  },

  openOrder(e) {
    const id = e.currentTarget.dataset.id;
    if (!id) return;
    wx.navigateTo({ url: `/pages/production/detail/index?id=${id}` });
  }
});
