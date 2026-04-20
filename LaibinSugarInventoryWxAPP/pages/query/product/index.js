import { listScreenMeshes, pageProducts } from '../../../api/query';
import { requireLogin } from '../../../utils/auth';
import { showError } from '../../../utils/toast';

const STATUS_OPTIONS = ['全部', '半成品', '成品'];

function textOrDash(value) {
  return value === null || value === undefined || value === '' ? '-' : value;
}

function normalizeProduct(item, screenMeshMap = {}) {
  const screenMeshName = item.screenMeshName || screenMeshMap[item.screenMeshId] || '无筛网';
  return {
    ...item,
    statusLabel: item.status || item.productStatus || '-',
    statusType: (item.status || item.productStatus) === '成品' ? 'primary' : 'success',
    productTypeText: textOrDash(item.productType),
    packagingText: textOrDash(item.packagingMethod),
    weightText: item.weightPerPiece || item.weightPerPiece === 0 ? `${item.weightPerPiece} kg/件` : '-',
    piecesText: item.piecesPerPallet || item.piecesPerPallet === 0 ? `${item.piecesPerPallet} 件/板` : '-',
    stackText: item.canStack ? '可堆叠' : '不可堆叠',
    screenMeshName
  };
}

Page({
  data: {
    productName: '',
    statusOptions: STATUS_OPTIONS,
    statusIndex: 0,
    page: 1,
    size: 10,
    total: 0,
    loading: false,
    error: false,
    products: []
  },

  onLoad() {
    requireLogin();
    this.loadProducts();
  },

  onShow() {
    requireLogin();
  },

  onProductNameInput(e) {
    this.setData({ productName: e.detail.value });
  },

  onStatusChange(e) {
    this.setData({ statusIndex: Number(e.detail.value) });
  },

  resetSearch() {
    this.setData({ productName: '', statusIndex: 0, page: 1 });
    this.loadProducts();
  },

  search() {
    this.setData({ page: 1 });
    this.loadProducts();
  },

  async loadProducts() {
    this.setData({ loading: true, error: false });
    try {
      const selectedStatus = STATUS_OPTIONS[this.data.statusIndex];
      const [res, meshes] = await Promise.all([
        pageProducts({
          page: this.data.page,
          size: this.data.size,
          name: this.data.productName.trim() || undefined,
          status: selectedStatus === '全部' ? undefined : selectedStatus
        }),
        listScreenMeshes().catch(() => [])
      ]);
      const screenMeshMap = {};
      (Array.isArray(meshes) ? meshes : []).forEach(item => {
        screenMeshMap[item.id] = item.meshName;
      });
      this.setData({
        products: (res.records || []).map(item => normalizeProduct(item, screenMeshMap)),
        total: res.total || 0
      });
    } catch (error) {
      this.setData({ error: true });
      showError(error, '产品查询失败');
    } finally {
      this.setData({ loading: false });
    }
  },

  onPageChange(e) {
    this.setData({ page: e.detail.page });
    this.loadProducts();
  }
});
