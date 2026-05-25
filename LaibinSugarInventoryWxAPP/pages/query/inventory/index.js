import { pageProductStock, pagePalletCodes, pageProductionInProcessMaterials } from '../../../api/query';
import { getPalletInventory } from '../../../api/pallet';
import { requireLogin } from '../../../utils/auth';
import { getDictItem, PALLET_STATUS, formatDateTime } from '../../../utils/dict';
import { showError, showToast } from '../../../utils/toast';

const STATUS_OPTIONS = ['全部', '半成品', '成品'];

function formatWeight(value) {
  if (value === null || value === undefined || value === '') return '-';
  return `${Number(value).toFixed(2)} kg`;
}

function normalizeLocation(inventory) {
  if (!inventory) return '暂无位置';
  const parts = [
    inventory.warehouseName,
    inventory.side ? `${inventory.side}侧` : '',
    inventory.rowNumber ? `第${inventory.rowNumber}排` : '',
    inventory.layer ? `${inventory.layer}层` : ''
  ].filter(Boolean);
  return parts.length ? parts.join(' · ') : '暂无位置';
}

Page({
  data: {
    activeTab: 'stock',
    tabs: [
      { key: 'stock', label: '产品库存' },
      { key: 'prepare', label: '生产中半成品' }
    ],
    productName: '',
    statusOptions: STATUS_OPTIONS,
    statusIndex: 0,
    page: 1,
    size: 10,
    total: 0,
    loading: false,
    error: false,
    stockList: [],
    detailMode: false,
    currentStock: null,
    palletPage: 1,
    palletSize: 10,
    palletTotal: 0,
    palletLoading: false,
    palletList: [],
    preparePage: 1,
    prepareSize: 10,
    prepareTotal: 0,
    prepareLoading: false,
    prepareError: false,
    prepareList: []
  },

  onLoad() {
    requireLogin();
    this.loadStock();
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

  onTabTap(e) {
    const activeTab = e.currentTarget.dataset.key;
    if (!activeTab || activeTab === this.data.activeTab) return;
    this.setData({
      activeTab,
      detailMode: false,
      currentStock: null,
      palletList: []
    });
    if (activeTab === 'prepare' && !this.data.prepareList.length) {
      this.loadPreparePool();
    }
  },

  resetSearch() {
    this.setData({
      productName: '',
      statusIndex: 0,
      page: 1,
      preparePage: 1,
      detailMode: false,
      currentStock: null,
      palletList: [],
      prepareList: []
    });
    if (this.data.activeTab === 'prepare') {
      this.loadPreparePool();
    } else {
      this.loadStock();
    }
  },

  search() {
    this.setData({ page: 1, preparePage: 1, detailMode: false, currentStock: null, palletList: [], prepareList: [] });
    if (this.data.activeTab === 'prepare') {
      this.loadPreparePool();
    } else {
      this.loadStock();
    }
  },

  async loadStock() {
    this.setData({ loading: true, error: false, detailMode: false, currentStock: null, palletList: [] });
    try {
      const productName = this.data.productName.trim();
      const selected = STATUS_OPTIONS[this.data.statusIndex];
      const res = await pageProductStock({
        page: this.data.page,
        size: this.data.size,
        productStatus: selected === '全部' ? undefined : selected,
        productName: productName || undefined
      });
      const stockList = (res.records || []).map(item => ({
        ...item,
        productStatus: item.productStatus || selected,
        totalQuantityText: `${item.totalQuantity || 0} 板`,
        totalPiecesText: `${item.totalPieces || 0} 件`,
        totalWeightText: formatWeight(item.totalWeight),
        warehouseCountText: `${item.warehouseCount || 0} 个库位`,
        stockInfoText: item.stockInfo || `${item.totalQuantity || 0}板 ${item.totalPieces || 0}件`
      }));
      this.setData({
        stockList,
        total: res.total || 0
      });
    } catch (error) {
      this.setData({ error: true });
      showError(error, '库存查询失败');
    } finally {
      this.setData({ loading: false });
    }
  },

  async loadPreparePool() {
    this.setData({ prepareLoading: true, prepareError: false });
    try {
      const productName = this.data.productName.trim();
      const res = await pageProductionInProcessMaterials({
        page: this.data.preparePage,
        size: this.data.prepareSize,
        productName: productName || undefined
      });
      const prepareList = (res.records || []).map(item => ({
        ...item,
        productNameText: item.productName || '-',
        orderNoText: item.orderNo || '-',
        productionDateText: item.productionDate || '-',
        quantityText: item.unit === '1' ? `${item.quantity || item.pieces || 0}件` : `${item.quantity || 0}板`,
        sourceLocationText: [item.warehouseName, item.side ? `${item.side}侧` : '', item.rowNumber ? `第${item.rowNumber}排` : '', item.layer ? `${item.layer}层` : ''].filter(Boolean).join(' · ') || '-',
        pickedAtText: formatDateTime(item.pickedAt),
        pickedByText: item.pickedByName || '-',
        statusLabel: item.status === 'PICKED' ? '生产中' : item.status || '-',
        statusType: 'warning'
      }));
      this.setData({
        prepareList,
        prepareTotal: res.total || 0
      });
    } catch (error) {
      this.setData({ prepareError: true });
      showError(error, '生产中半成品查询失败');
    } finally {
      this.setData({ prepareLoading: false });
    }
  },

  async openPallets(e) {
    const index = e.currentTarget.dataset.index;
    const item = this.data.stockList[index];
    if (!item) return;
    this.setData({ detailMode: true, currentStock: item, palletPage: 1, palletList: [] });
    this.loadPallets();
  },

  async loadPallets() {
    const item = this.data.currentStock;
    if (!item) return;
    this.setData({ palletLoading: true, palletList: [] });
    try {
      const res = await pagePalletCodes({
        pageNum: this.data.palletPage,
        pageSize: this.data.palletSize,
        productName: item.productName,
        productStatus: item.productStatus === '全部' ? undefined : item.productStatus,
        status: 'INSTOCK',
        inventoryOnly: true
      });
      const records = res.records || [];
      const palletList = await Promise.all(records.map(async pallet => {
        let locationText = '暂无位置';
        try {
          locationText = normalizeLocation(await getPalletInventory(pallet.code));
        } catch (error) {
          locationText = '暂无位置';
        }
        const status = getDictItem(PALLET_STATUS, pallet.status);
        return {
          ...pallet,
          statusLabel: status.label,
          statusType: status.type,
          locationText,
          updatedAtText: formatDateTime(pallet.updatedAt),
          assayText: pallet.assayId ? '有关联化验' : '暂无化验'
        };
      }));
      this.setData({
        palletList,
        palletTotal: res.total || 0
      });
    } catch (error) {
      showError(error, '二维码明细查询失败');
    } finally {
      this.setData({ palletLoading: false });
    }
  },

  onPageChange(e) {
    this.setData({ page: e.detail.page });
    this.loadStock();
  },

  onPreparePageChange(e) {
    this.setData({ preparePage: e.detail.page });
    this.loadPreparePool();
  },

  onPalletPageChange(e) {
    this.setData({ palletPage: e.detail.page });
    this.loadPallets();
  },

  backToSummary() {
    this.setData({ detailMode: false, currentStock: null, palletList: [] });
  },

  openPalletDetail(e) {
    const code = e.currentTarget.dataset.code;
    if (!code) return;
    wx.navigateTo({ url: `/pages/pallet/detail/index?code=${encodeURIComponent(code)}` });
  },

  openAssay(e) {
    const index = e.currentTarget.dataset.index;
    const item = this.data.stockList[index];
    const productName = item && item.productName ? item.productName : '';
    wx.navigateTo({ url: `/pages/query/assay/index?productName=${encodeURIComponent(productName)}` });
  },

  openPrepareAssay(e) {
    const index = e.currentTarget.dataset.index;
    const item = this.data.prepareList[index];
    const productName = item && item.productName ? item.productName : '';
    const date = item && item.productionDate ? item.productionDate : '';
    const query = [`productName=${encodeURIComponent(productName)}`];
    if (date) {
      query.push(`startDate=${encodeURIComponent(date)}`);
      query.push(`endDate=${encodeURIComponent(date)}`);
    }
    wx.navigateTo({ url: `/pages/query/assay/index?${query.join('&')}` });
  },

  showLocationHint() {
    showToast('库位空间图请在后台管理端查看');
  }
});
