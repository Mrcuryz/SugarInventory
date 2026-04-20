import {
  cancelTasks,
  confirmFinishOutTasks,
  confirmPalletInBatch,
  confirmSemiOutTasks,
  confirmSemiPrepareTasks,
  confirmTransferTasks,
  getTaskList
} from '../../../api/task';
import { getProductsByStatus } from '../../../api/product';
import { enrichTask } from '../../../utils/dict';
import { requireLogin } from '../../../utils/auth';
import { normalizeQuantityByUnit, validateQuantity } from '../../../utils/quantity';
import { getScanDefaults, setScanDefaults } from '../../../utils/storage';
import { confirm, showError, showToast } from '../../../utils/toast';

function today() {
  const date = new Date();
  const month = String(date.getMonth() + 1).padStart(2, '0');
  const day = String(date.getDate()).padStart(2, '0');
  return `${date.getFullYear()}-${month}-${day}`;
}

Page({
  data: {
    statusTabs: [
      { key: 'PENDING', label: '待处理' },
      { key: 'CONFIRMED', label: '已完成' },
      { key: 'CANCELED', label: '已取消' }
    ],
    typeTabs: [
      { key: '', label: '全部' },
      { key: 'IN', label: '入库' },
      { key: 'OUT', label: '出库' },
      { key: 'TRANSFER', label: '调拨' }
    ],
    status: 'PENDING',
    taskType: '',
    tasks: [],
    selectedCodes: [],
    pageNum: 1,
    pageSize: 20,
    total: 0,
    loading: false,
    confirmVisible: false,
    productCatalog: {},
    confirmForm: {
      warehouseName: '',
      side: '左',
      entryDate: '',
      quantity: '1',
      unit: '0',
      remark: ''
    },
    bottomActions: []
  },

  async onLoad() {
    const defaults = getScanDefaults();
    this.setData({
      'confirmForm.warehouseName': defaults.warehouseName || '',
      'confirmForm.side': defaults.side || '左',
      'confirmForm.entryDate': defaults.entryDate || today()
    });
    await this.loadProductCatalog();
  },

  async loadProductCatalog() {
    try {
      const [semi, finish] = await Promise.all([
        getProductsByStatus('半成品'),
        getProductsByStatus('成品')
      ]);
      const catalog = {};
      [...(semi || []), ...(finish || [])].forEach(item => {
        catalog[item.id || item.productId] = item;
      });
      this.setData({ productCatalog: catalog });
    } catch (error) {
      this.setData({ productCatalog: {} });
    }
  },

  onShow() {
    if (!requireLogin()) return;
    this.loadTasks();
  },

  async loadTasks() {
    this.setData({ loading: true });
    try {
      const params = {
        status: this.data.status,
        pageNum: this.data.pageNum,
        pageSize: this.data.pageSize
      };
      if (this.data.taskType) params.taskType = this.data.taskType;
      const res = await getTaskList(params);
      const selectedSet = new Set(this.data.selectedCodes);
      const tasks = ((res && res.records) || []).map(item => ({
        ...enrichTask(item),
        selected: selectedSet.has(item.code),
        canConfirm: item.taskStatus === 'PENDING',
        canCancel: item.taskStatus === 'PENDING'
      }));
      this.setData({
        tasks,
        total: res && res.total || tasks.length,
        loading: false
      });
      this.refreshActions();
    } catch (error) {
      this.setData({ tasks: [], total: 0, loading: false });
      showError(error, '任务列表加载失败');
    }
  },

  onStatusTap(e) {
    this.setData({ status: e.currentTarget.dataset.key, selectedCodes: [], pageNum: 1 });
    this.loadTasks();
  },

  onTypeTap(e) {
    this.setData({ taskType: e.currentTarget.dataset.key, selectedCodes: [], pageNum: 1 });
    this.loadTasks();
  },

  onSelectTask(e) {
    const code = e.detail.task.code;
    const selected = new Set(this.data.selectedCodes);
    if (selected.has(code)) {
      selected.delete(code);
    } else {
      selected.add(code);
    }
    const selectedCodes = Array.from(selected);
    this.setData({
      selectedCodes,
      tasks: this.data.tasks.map(item => ({ ...item, selected: selected.has(item.code) }))
    });
    this.refreshActions();
  },

  refreshActions() {
    const hasSelected = this.data.selectedCodes.length > 0;
    this.setData({
      bottomActions: [
        { key: 'refresh', label: '刷新', type: 'plain' },
        { key: 'cancel', label: `取消${hasSelected ? `(${this.data.selectedCodes.length})` : ''}`, type: 'danger', disabled: !hasSelected || this.data.status !== 'PENDING' },
        { key: 'confirm', label: `确认${hasSelected ? `(${this.data.selectedCodes.length})` : ''}`, type: 'primary', disabled: !hasSelected || this.data.status !== 'PENDING' }
      ]
    });
  },

  onBottomAction(e) {
    const key = e.detail.action.key;
    if (key === 'refresh') this.loadTasks();
    if (key === 'cancel') this.cancelSelected();
    if (key === 'confirm') this.confirmSelected();
  },

  onTapTask(e) {
    const code = e.detail.task.code;
    wx.navigateTo({ url: `/pages/pallet/detail/index?code=${encodeURIComponent(code)}` });
  },

  onConfirmTask(e) {
    this.setData({ selectedCodes: [e.detail.task.code] });
    this.confirmSelected();
  },

  onCancelTask(e) {
    this.setData({ selectedCodes: [e.detail.task.code] });
    this.cancelSelected();
  },

  getSelectedTasks() {
    const selected = new Set(this.data.selectedCodes);
    return this.data.tasks.filter(item => selected.has(item.code));
  },

  async cancelSelected() {
    const codes = this.data.selectedCodes;
    if (!codes.length) return;
    const ok = await confirm(`取消 ${codes.length} 个待处理任务？`, '取消任务');
    if (!ok) return;
    try {
      await cancelTasks(codes, '小程序任务中心取消');
      showToast('已取消', 'success');
      this.setData({ selectedCodes: [] });
      this.loadTasks();
    } catch (error) {
      showError(error, '取消失败');
    }
  },

  async confirmSelected() {
    const tasks = this.getSelectedTasks();
    if (!tasks.length) return;
    if (tasks.some(item => item.taskType === 'SEMI_IN' || item.taskType === 'FINISH_IN' || item.taskType === 'IN')) {
      this.setData({ confirmVisible: true });
      return;
    }
    const ok = await confirm(`确认处理 ${tasks.length} 个任务？`, '确认任务');
    if (!ok) return;
    try {
      const semiOut = tasks.filter(item => item.taskType === 'OUT' && item.bizScene === 'DIRECT_OUT').map(item => item.code);
      const semiPrepare = tasks.filter(item => item.taskType === 'OUT' && item.bizScene === 'PREPARE_CONSUMED').map(item => item.code);
      const finishOut = tasks.filter(item => item.taskType === 'OUT' && item.bizScene === 'FINISH_OUT').map(item => item.code);
      const transfer = tasks.filter(item => item.taskType === 'TRANSFER').map(item => item.code);
      if (semiOut.length) await confirmSemiOutTasks(semiOut);
      if (semiPrepare.length) await confirmSemiPrepareTasks(semiPrepare);
      if (finishOut.length) await confirmFinishOutTasks(finishOut);
      if (transfer.length) await confirmTransferTasks(transfer);
      showToast('确认完成', 'success');
      this.setData({ selectedCodes: [] });
      this.loadTasks();
    } catch (error) {
      showError(error, '确认失败');
    }
  },

  async submitConfirmIn() {
    const { warehouseName, side, entryDate, quantity, unit, remark } = this.data.confirmForm;
    if (!warehouseName) {
      showToast('请填写入库库位');
      return;
    }
    const selectedTasks = this.getSelectedTasks();
    const limits = selectedTasks
      .map(task => {
        const product = this.data.productCatalog[task.productId];
        const value = Number(product && product.piecesPerPallet);
        return Number.isFinite(value) && value > 0 ? value : 0;
      })
      .filter(Boolean);
    const fallbackLimit = limits.length ? Math.min(...limits) : 0;
    const quantityResult = validateQuantity({ unit, quantity, fallbackLimit });
    if (!quantityResult.valid) {
      showToast(quantityResult.message);
      return;
    }
    const items = selectedTasks.map(task => ({
      code: task.code,
      warehouseName,
      side,
      entryDate,
      quantity: quantityResult.value,
      unit,
      remark: remark || '小程序任务中心确认入库'
    }));
    try {
      await confirmPalletInBatch(items);
      setScanDefaults({
        ...getScanDefaults(),
        warehouseName,
        side,
        entryDate
      });
      showToast('入库确认完成', 'success');
      this.setData({ selectedCodes: [], confirmVisible: false });
      this.loadTasks();
    } catch (error) {
      showError(error, '入库确认失败');
    }
  },

  onConfirmInput(e) {
    const field = e.currentTarget.dataset.field;
    const value = field === 'quantity'
      ? normalizeQuantityByUnit(this.data.confirmForm.unit, e.detail.value)
      : e.detail.value;
    this.setData({ [`confirmForm.${field}`]: value });
  },

  onSideChange(e) {
    this.setData({ 'confirmForm.side': e.detail.value === '1' ? '右' : '左' });
  },

  onUnitChange(e) {
    const unit = e.detail.value === '1' ? '1' : '0';
    this.setData({
      'confirmForm.unit': unit,
      'confirmForm.quantity': normalizeQuantityByUnit(unit, this.data.confirmForm.quantity)
    });
  },

  closeConfirm() {
    this.setData({ confirmVisible: false });
  },

  noop() {
  }
});
