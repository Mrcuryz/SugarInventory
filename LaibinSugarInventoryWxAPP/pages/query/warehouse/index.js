import { queryWarehouses } from '../../../api/query';
import { requireLogin } from '../../../utils/auth';
import { setScanDefaults, getScanDefaults } from '../../../utils/storage';
import { showError, showToast } from '../../../utils/toast';

const STATUS_OPTIONS = ['全部', '正常', '空置', '临期预警', '满仓', '维护中'];

function getStatusType(status) {
  if (status === '正常') return 'success';
  if (status === '空置') return 'info';
  if (status === '临期预警') return 'warning';
  if (status === '满仓') return 'danger';
  if (status === '维护中') return 'info';
  return 'primary';
}

function normalizeWarehouse(item) {
  const max = Number(item.maxCapacity || 0);
  const current = Number(item.curCapacity || item.currentCapacity || 0);
  const percent = max > 0 ? Math.min(100, Math.round((current / max) * 100)) : Number(item.capacityPercentage || 0);
  const status = item.status || (current <= 0 ? '空置' : (percent >= 100 ? '满仓' : (percent >= 80 ? '临期预警' : '正常')));
  return {
    ...item,
    warehouseId: item.warehouseId || item.id,
    warehouseName: item.warehouseName || item.name || '-',
    status,
    statusType: getStatusType(status),
    currentText: `${current}/${max || '-'}`,
    percent,
    progressStyle: `width:${Math.min(100, Math.max(0, percent))}%`,
    hasSpace: max <= 0 ? false : current < max,
    palletText: `${item.currentPalletCount || 0} 板`,
    productText: `${item.currentProductCount || 0} 种产品`
  };
}

Page({
  data: {
    warehouseName: '',
    statusOptions: STATUS_OPTIONS,
    statusIndex: 0,
    onlyHasSpace: false,
    loading: false,
    error: false,
    page: 1,
    size: 10,
    total: 0,
    warehouses: []
  },

  onLoad() {
    requireLogin();
    this.loadWarehouses();
  },

  onShow() {
    requireLogin();
  },

  onNameInput(e) {
    this.setData({ warehouseName: e.detail.value });
  },

  onStatusChange(e) {
    this.setData({ statusIndex: Number(e.detail.value) });
  },

  onOnlyHasSpaceChange(e) {
    this.setData({ onlyHasSpace: e.detail.value });
  },

  resetSearch() {
    this.setData({ warehouseName: '', statusIndex: 0, onlyHasSpace: false, page: 1, warehouses: [] });
    this.loadWarehouses();
  },

  search() {
    this.setData({ page: 1, warehouses: [] });
    this.loadWarehouses();
  },

  async loadWarehouses() {
    this.setData({ loading: true, error: false });
    try {
      const selectedStatus = STATUS_OPTIONS[this.data.statusIndex];
      const res = await queryWarehouses({
        page: this.data.page,
        size: this.data.size,
        warehouseName: this.data.warehouseName.trim() || undefined,
        status: selectedStatus === '全部' ? undefined : selectedStatus,
        hasSpace: this.data.onlyHasSpace || undefined
      });
      const warehouses = (res.records || []).map(normalizeWarehouse);
      this.setData({
        warehouses,
        total: res.total || 0
      });
    } catch (error) {
      this.setData({ error: true });
      showError(error, '库位查询失败');
    } finally {
      this.setData({ loading: false });
    }
  },

  onPageChange(e) {
    this.setData({ page: e.detail.page });
    this.loadWarehouses();
  },

  specifyInbound(e) {
    const index = e.currentTarget.dataset.index;
    const warehouse = this.data.warehouses[index];
    if (!warehouse) return;
    if (!warehouse.hasSpace) {
      showToast('当前库位没有可用容量');
      return;
    }
    wx.showActionSheet({
      itemList: ['左侧', '右侧'],
      success: res => {
        const side = res.tapIndex === 1 ? '右' : '左';
        setScanDefaults({
          ...getScanDefaults(),
          warehouseName: warehouse.warehouseName,
          side
        });
        wx.setStorageSync('preferredScanMode', 'in');
        showToast('已指定库位，正在进入扫码入库', 'success');
        wx.switchTab({ url: '/pages/scan/index/index' });
      }
    });
  }
});
