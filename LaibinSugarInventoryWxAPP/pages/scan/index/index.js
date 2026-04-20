import { getProductsByStatus } from '../../../api/product';
import { getPalletFlowCycles, getPalletFlowDetails, getPalletInventory, parseCode } from '../../../api/pallet';
import {
  bindPalletTask,
  cancelTasks,
  confirmFinishOutTasks,
  confirmPalletInBatch,
  confirmSemiOutTasks,
  confirmSemiPrepareTasks,
  confirmTransferTasks,
  createFinishOutTasks,
  createSemiOutTasks,
  createSemiPrepareTasks,
  createTransferTasks
} from '../../../api/task';
import { enrichPallet } from '../../../utils/dict';
import { requireLogin } from '../../../utils/auth';
import { normalizeQuantityByUnit, validateQuantity } from '../../../utils/quantity';
import { scanCode } from '../../../utils/scan';
import { addPoolItem, createPoolItem, removePoolItem, successfulCodes } from '../../../utils/taskPool';
import { getScanDefaults, pushRecentScan, setScanDefaults } from '../../../utils/storage';
import { confirm, showError, showToast } from '../../../utils/toast';

const MODES = [
  { key: 'in', label: '入库', desc: '扫码绑定托盘，创建待入库任务。' },
  { key: 'out', label: '出库', desc: '扫码在库托盘，创建出库任务。' },
  { key: 'prepare', label: '转入备料池', desc: '扫码半成品托盘，创建转入备料池任务。' },
  { key: 'transfer', label: '调拨', desc: '扫码在库托盘，指定目标库位创建调拨任务。' }
];

function today() {
  const date = new Date();
  const month = String(date.getMonth() + 1).padStart(2, '0');
  const day = String(date.getDate()).padStart(2, '0');
  return `${date.getFullYear()}-${month}-${day}`;
}

function vibrate() {
  if (wx.vibrateShort) {
    wx.vibrateShort();
  }
}

function emptySummary() {
  return {
    total: 0,
    success: 0,
    error: 0,
    duplicate: 0
  };
}

function isScanCancel(error) {
  const message = error && (error.errMsg || error.message || error.msg) || '';
  return String(message).toLowerCase().includes('cancel');
}

function getErrorMessage(error, fallback = '扫码失败') {
  return error && (error.msg || error.message || error.errMsg) || fallback;
}

Page({
  data: {
    modes: MODES,
    currentMode: 'in',
    currentModeInfo: MODES[0],
    pool: [],
    lastFeedback: null,
    sessionSummary: emptySummary(),
    scanning: false,
    productCatalog: {
      '半成品': [],
      '成品': []
    },
    products: [],
    productNames: [],
    bindVisible: false,
    pendingPallet: null,
    bindForm: {
      productStatus: '',
      productIndex: -1,
      productionDate: '',
      quantity: '1',
      unit: '0',
      remark: ''
    },
    transferVisible: false,
    transferForm: {
      code: '',
      targetWarehouseName: '',
      targetSide: '左',
      remark: ''
    },
    confirmVisible: false,
    confirmForm: {
      warehouseName: '',
      side: '左',
      entryDate: '',
      quantity: '1',
      unit: '0',
      remark: ''
    },
    bottomActions: [],
    activePoolItemId: ''
  },

  async onLoad() {
    this.resetDateDefaults();
    await this.loadProductCatalog();
    this.refreshActions();
  },

  onShow() {
    if (!requireLogin()) return;
    this.resetDateDefaults();
    const preferred = wx.getStorageSync('preferredScanMode');
    if (preferred) {
      wx.removeStorageSync('preferredScanMode');
      this.setMode(preferred);
    }
  },

  resetDateDefaults() {
    const defaults = getScanDefaults();
    this.setData({
      'bindForm.productionDate': defaults.productionDate || today(),
      'bindForm.quantity': '1',
      'confirmForm.entryDate': defaults.entryDate || today(),
      'confirmForm.warehouseName': defaults.warehouseName || '',
      'confirmForm.side': defaults.side || '左',
      'confirmForm.quantity': '1',
      'transferForm.targetWarehouseName': defaults.targetWarehouseName || '',
      'transferForm.targetSide': defaults.targetSide || '左'
    });
  },

  async loadProductCatalog() {
    try {
      const [semi, finish] = await Promise.all([
        getProductsByStatus('半成品'),
        getProductsByStatus('成品')
      ]);
      this.setData({
        productCatalog: {
          '半成品': Array.isArray(semi) ? semi : [],
          '成品': Array.isArray(finish) ? finish : []
        }
      });
    } catch (error) {
      this.setData({
        productCatalog: {
          '半成品': [],
          '成品': []
        }
      });
    }
  },

  setMode(mode) {
    const info = MODES.find(item => item.key === mode) || MODES[0];
    this.setData({
      currentMode: info.key,
      currentModeInfo: info
    });
    this.refreshActions();
  },

  onModeTap(e) {
    this.setMode(e.currentTarget.dataset.mode);
  },

  updateSummary(delta = {}) {
    const sessionSummary = { ...this.data.sessionSummary };
    Object.keys(delta).forEach(key => {
      sessionSummary[key] = Math.max(0, (sessionSummary[key] || 0) + delta[key]);
    });
    this.setData({ sessionSummary });
  },

  createFeedback(type, text) {
    this.setData({
      lastFeedback: { type, text }
    });
  },

  markSessionError(text) {
    this.setData({
      lastFeedback: { type: 'danger', text }
    });
    this.updateSummary({ error: 1 });
  },

  async validatePalletForMode(pallet) {
    const mode = this.data.currentMode;
    const code = pallet.code;
    const tasksRes = await getTaskList({ code, status: 'PENDING', pageNum: 1, pageSize: 1 });
    const hasPending = Boolean(tasksRes && tasksRes.records && tasksRes.records.length);
    if (hasPending) {
      return { valid: false, message: '该码已有待处理任务' };
    }

    if (pallet.status === 'INVALID') {
      return { valid: false, message: '该码已作废，不能继续操作' };
    }

    if (mode === 'in') {
      if (pallet.status === 'FREE') return { valid: true };
      if (pallet.status === 'INSTOCK') return { valid: false, message: '该码已入库' };
      return { valid: false, message: '该码当前不可用于入库' };
    }

    if (pallet.status !== 'INSTOCK') {
      return { valid: false, message: '该码当前不在库，不能执行该操作' };
    }

    try {
      await getPalletInventory(code);
    } catch (error) {
      return { valid: false, message: '该码当前没有普通库位信息，不能执行该操作' };
    }

    const activePrepare = pallet.productStatus === '半成品'
      ? await this.isActivePreparePallet(code)
      : false;
    if (activePrepare) {
      return { valid: false, message: mode === 'prepare' ? '该码已转入备料池' : '该码已转入备料池，不属于普通可操作库存' };
    }

    if (mode === 'prepare') {
      if (pallet.productStatus !== '半成品') {
        return { valid: false, message: '该码不是可转入备料池的半成品' };
      }
      return { valid: true };
    }

    if (mode === 'out' || mode === 'transfer') {
      return { valid: true };
    }

    return { valid: false, message: '当前模式不支持该托盘' };
  },

  async isActivePreparePallet(code) {
    try {
      const cycles = await getPalletFlowCycles(code, { pageNum: 1, pageSize: 1 });
      const cycle = cycles && cycles.records && cycles.records[0];
      if (!cycle) return false;
      const flows = await getPalletFlowDetails(code, cycle.cycleNo);
      const hasPrepare = (flows || []).some(item => item.operationType === 'PREPARE_CONSUMED');
      const hasConsumed = (flows || []).some(item => item.operationType === 'CONSUMED');
      return hasPrepare && !hasConsumed;
    } catch (error) {
      return false;
    }
  },

  async onScan() {
    if (this.data.scanning) return;
    this.setData({ scanning: true });
    let currentCode = '';
    try {
      const code = await scanCode();
      currentCode = code;
      this.updateSummary({ total: 1 });
      if (this.data.pool.some(item => item.code === code)) {
        this.updateSummary({ duplicate: 1 });
        this.createFeedback('warning', `${code} 已在本次作业中，无需重复扫码`);
        showToast('本次作业已扫描该托盘');
        vibrate();
        return;
      }

      const pallet = enrichPallet(await parseCode(code));
      const validation = await this.validatePalletForMode(pallet);
      if (!validation.valid) {
        this.updateSummary({ error: 1 });
        this.createFeedback('danger', validation.message);
        showToast(validation.message);
        return;
      }

      pushRecentScan({ code, mode: this.data.currentMode, modeLabel: this.data.currentModeInfo.label });

      if (this.data.currentMode === 'in') {
        this.openBindModal(pallet);
        this.createFeedback('info', `${code} 已识别，请补充入库信息`);
        return;
      }

      if (this.data.currentMode === 'transfer') {
        this.openTransferModal(pallet);
        this.createFeedback('info', `${code} 已识别，请填写目标库位`);
        return;
      }

      if (this.data.currentMode === 'prepare') {
        await createSemiPrepareTasks([code]);
        this.addSuccessToPool({
          code,
          pallet,
          taskTypeLabel: '转入备料池',
          taskStatusLabel: '待处理',
          message: '备料任务已创建',
          actionMode: 'prepare'
        });
        vibrate();
        return;
      }

      await this.createOutTask(code, pallet);
      vibrate();
    } catch (error) {
      if (isScanCancel(error)) return;
      const message = getErrorMessage(error);
      this.updateSummary({ error: 1 });
      this.createFeedback('danger', message);
      showError(error, '扫码失败');
    } finally {
      this.setData({ scanning: false });
    }
  },

  openBindModal(pallet) {
    this.setData({
      pendingPallet: pallet,
      bindVisible: true,
      products: [],
      productNames: [],
      bindForm: {
        productStatus: '',
        productIndex: -1,
        productionDate: this.data.bindForm.productionDate || today(),
        quantity: '1',
        unit: '0',
        remark: ''
      }
    });
  },

  openTransferModal(pallet) {
    this.setData({
      pendingPallet: pallet,
      transferVisible: true,
      'transferForm.code': pallet.code,
      'transferForm.remark': ''
    });
  },

  syncProductsByStatus(status) {
    const products = this.data.productCatalog[status] || [];
    const productIndex = products.length === 1 ? 0 : -1;
    this.setData({
      products,
      productNames: products.map(item => `${item.productName}${item.productType ? ` / ${item.productType}` : ''}`),
      'bindForm.productStatus': status,
      'bindForm.productIndex': productIndex
    });
  },

  async createOutTask(code, pallet) {
    const isSemi = pallet.productStatus === '半成品';
    if (isSemi) {
      await createSemiOutTasks([code]);
    } else {
      await createFinishOutTasks([code]);
    }
    this.addSuccessToPool({
      code,
      pallet,
      taskTypeLabel: isSemi ? '半成品出库' : '成品出库',
      taskStatusLabel: '待处理',
      message: '出库任务已创建',
      actionMode: 'out'
    });
  },

  addFailureToPool(payload) {
    const item = createPoolItem({
      code: payload.code,
      mode: this.data.currentMode,
      status: 'error',
      message: payload.message,
      pallet: payload.pallet || {},
      task: null
    });
    item.productName = payload.pallet && payload.pallet.productName;
    item.productStatus = payload.pallet && payload.pallet.productStatus;
    item.taskTypeLabel = payload.taskTypeLabel || this.data.currentModeInfo.label;
    item.taskStatusLabel = '创建失败';
    item.taskStatusTagType = 'danger';
    item.canConfirm = false;
    item.canCancel = false;
    item.canRemove = true;
    item.canRetry = Boolean(payload.canRetry);
    item.retryMode = payload.retryMode || this.data.currentMode;
    item.targetWarehouseName = payload.targetWarehouseName || '';
    item.targetSide = payload.targetSide || '';
    item.remark = payload.remark || '';
    const result = addPoolItem(this.data.pool, item);
    this.setData({
      pool: result.pool,
      activePoolItemId: `pool-item-${item.id}`
    });
    this.refreshActions();
  },

  addSuccessToPool(task) {
    const item = createPoolItem({
      code: task.code,
      mode: this.data.currentMode,
      status: 'success',
      message: task.message,
      pallet: task.pallet,
      task: task.task
    });
    item.productName = task.pallet && task.pallet.productName;
    item.productStatus = task.pallet && task.pallet.productStatus;
    item.taskTypeLabel = task.taskTypeLabel;
    item.taskStatusLabel = task.taskStatusLabel;
    item.taskStatusTagType = 'warning';
    item.targetWarehouseName = task.targetWarehouseName || '';
    item.canConfirm = false;
    item.canCancel = false;
    item.canRemove = true;
    item.canRetry = false;
    item.actionMode = task.actionMode || this.data.currentMode;
    const result = addPoolItem(this.data.pool, item);
    if (result.duplicated) {
      this.updateSummary({ duplicate: 1 });
      this.createFeedback('warning', `${task.code} 已在本次作业中`);
      showToast('本次作业已扫描该托盘');
      return;
    }
    this.setData({
      pool: result.pool,
      activePoolItemId: `pool-item-${item.id}`
    });
    this.updateSummary({ success: 1 });
    this.createFeedback('success', `${task.code} ${task.message}`);
    this.refreshActions();
    showToast(task.message, 'success');
  },

  refreshActions() {
    const counts = {
      success: this.data.pool.filter(item => item.status === 'success').length
    };
    this.setData({
      bottomActions: [
        { key: 'clear', label: '清空本次池', type: 'plain', disabled: !this.data.pool.length },
        { key: 'cancel', label: `取消 ${counts.success}`, type: 'danger', disabled: !counts.success },
        { key: 'confirm', label: `确认 ${counts.success}`, type: 'primary', disabled: !counts.success }
      ]
    });
  },

  onBottomAction(e) {
    const key = e.detail.action.key;
    if (key === 'clear') this.clearPool();
    if (key === 'cancel') this.cancelPool();
    if (key === 'confirm') this.confirmPool();
  },

  removePoolItem(e) {
    const id = e.detail.task.id;
    this.setData({
      pool: removePoolItem(this.data.pool, id),
      activePoolItemId: ''
    });
    this.refreshActions();
  },

  async onRetryTask(e) {
    const task = e.detail.task;
    if (!task || !task.code) return;
    const originalPool = this.data.pool;
    try {
      const pallet = task.pallet || enrichPallet(await parseCode(task.code));
      const pool = removePoolItem(originalPool, task.id);
      this.setData({ pool });
      if (task.retryMode === 'out') {
        const isSemi = pallet.productStatus === '半成品';
        if (isSemi) {
          await createSemiOutTasks([task.code]);
        } else {
          await createFinishOutTasks([task.code]);
        }
        this.addSuccessToPool({
          code: task.code,
          pallet,
          taskTypeLabel: isSemi ? '半成品出库' : '成品出库',
          taskStatusLabel: '待处理',
          message: '出库任务已创建',
          actionMode: 'out'
        });
      } else if (task.retryMode === 'prepare') {
        await createSemiPrepareTasks([task.code]);
        this.addSuccessToPool({
          code: task.code,
          pallet,
          taskTypeLabel: '转入备料池',
          taskStatusLabel: '待处理',
          message: '备料任务已创建',
          actionMode: 'prepare'
        });
      } else if (task.retryMode === 'transfer') {
        await createTransferTasks([{
          code: task.code,
          targetWarehouseName: task.targetWarehouseName,
          targetSide: task.targetSide || '左',
          remark: task.remark || '小程序重试创建调拨任务'
        }]);
        this.addSuccessToPool({
          code: task.code,
          pallet,
          taskTypeLabel: '调拨任务',
          taskStatusLabel: '待处理',
          targetWarehouseName: task.targetWarehouseName,
          message: '调拨任务已创建',
          actionMode: 'transfer'
        });
      }
      this.refreshActions();
    } catch (error) {
      this.setData({ pool: originalPool });
      this.refreshActions();
      showError(error, '重试失败');
    }
  },

  async clearPool() {
    if (!this.data.pool.length) return;
    const ok = await confirm('只清空本地任务池，不会取消已创建的后端任务。确认继续？', '清空本次池');
    if (!ok) return;
    this.setData({
      pool: [],
      sessionSummary: emptySummary(),
      lastFeedback: null,
      activePoolItemId: ''
    });
    this.refreshActions();
  },

  async cancelPool() {
    const codes = successfulCodes(this.data.pool);
    if (!codes.length) return;
    const ok = await confirm(`将取消 ${codes.length} 条已创建的待处理任务，确认继续？`, '批量取消');
    if (!ok) return;
    try {
      await cancelTasks(codes, '小程序扫码池批量取消');
      showToast('已取消', 'success');
      this.setData({ pool: [], activePoolItemId: '' });
      this.refreshActions();
    } catch (error) {
      showError(error, '取消失败');
    }
  },

  async confirmPool() {
    const codes = successfulCodes(this.data.pool);
    if (!codes.length) return;
    if (this.data.currentMode === 'in') {
      this.setData({ confirmVisible: true });
      return;
    }

    const ok = await confirm(`将确认 ${codes.length} 条待处理任务，确认继续？`, '批量确认');
    if (!ok) return;
    try {
      if (this.data.currentMode === 'transfer') {
        await confirmTransferTasks(codes);
      } else if (this.data.currentMode === 'prepare') {
        await confirmSemiPrepareTasks(codes);
      } else {
        const semiCodes = this.data.pool.filter(item => item.status === 'success' && item.productStatus === '半成品').map(item => item.code);
        const finishCodes = this.data.pool.filter(item => item.status === 'success' && item.productStatus !== '半成品').map(item => item.code);
        if (semiCodes.length) await confirmSemiOutTasks(semiCodes);
        if (finishCodes.length) await confirmFinishOutTasks(finishCodes);
      }
      showToast('确认完成', 'success');
      this.setData({ pool: [], activePoolItemId: '' });
      this.refreshActions();
    } catch (error) {
      showError(error, '确认失败');
    }
  },

  async submitBind() {
    const { bindForm, pendingPallet } = this.data;
    if (!pendingPallet) return;
    if (!bindForm.productStatus) {
      showToast('请先选择产品状态');
      return;
    }
    const product = this.data.products[bindForm.productIndex];
    if (!product) {
      showToast('请先选择产品');
      return;
    }

    const quantityResult = validateQuantity({
      unit: bindForm.unit,
      quantity: bindForm.quantity,
      product
    });
    if (!quantityResult.valid) {
      showToast(quantityResult.message);
      return;
    }

    const payload = {
      code: pendingPallet.code,
      productId: product.id || product.productId,
      productStatus: bindForm.productStatus,
      productionDate: bindForm.productionDate,
      quantity: quantityResult.value,
      unit: bindForm.unit,
      remark: bindForm.remark || '小程序扫码绑定'
    };

    try {
      const task = await bindPalletTask(payload);
      setScanDefaults({
        ...getScanDefaults(),
        productionDate: payload.productionDate
      });
      this.addSuccessToPool({
        code: payload.code,
        pallet: enrichPallet({
          ...pendingPallet,
          productName: product.productName,
          productStatus: payload.productStatus
        }),
        task,
        taskTypeLabel: payload.productStatus === '成品' ? '成品入库' : '半成品入库',
        taskStatusLabel: '待处理',
        message: '入库任务已创建',
        actionMode: 'in'
      });
      vibrate();
      this.closeBind();
    } catch (error) {
      this.markSessionError((error && (error.msg || error.message)) || '创建入库任务失败');
      showError(error, '创建入库任务失败');
    }
  },

  async submitTransfer() {
    const { code, targetWarehouseName, targetSide, remark } = this.data.transferForm;
    if (!targetWarehouseName) {
      showToast('请填写目标库位');
      return;
    }
    try {
      await createTransferTasks([{ code, targetWarehouseName, targetSide, remark: remark || '小程序创建调拨任务' }]);
      setScanDefaults({
        ...getScanDefaults(),
        targetWarehouseName,
        targetSide
      });
      this.addSuccessToPool({
        code,
        pallet: this.data.pendingPallet,
        taskTypeLabel: '调拨任务',
        taskStatusLabel: '待处理',
        targetWarehouseName,
        message: '调拨任务已创建',
        actionMode: 'transfer'
      });
      vibrate();
      this.closeTransfer();
    } catch (error) {
      this.markSessionError((error && (error.msg || error.message)) || '调拨任务创建失败');
      showError(error, '创建调拨任务失败');
    }
  },

  async submitConfirmIn() {
    const { warehouseName, side, entryDate, quantity, unit, remark } = this.data.confirmForm;
    if (!warehouseName) {
      showToast('请填写入库库位');
      return;
    }
    const quantityResult = validateQuantity({
      unit,
      quantity,
      fallbackLimit: 0
    });
    if (!quantityResult.valid) {
      showToast(quantityResult.message);
      return;
    }
    const items = successfulCodes(this.data.pool).map(code => ({
      code,
      warehouseName,
      side,
      entryDate,
      quantity: quantityResult.value,
      unit,
      remark: remark || '小程序扫码池确认入库'
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
      this.setData({ pool: [], confirmVisible: false, activePoolItemId: '' });
      this.refreshActions();
    } catch (error) {
      showError(error, '入库确认失败');
    }
  },

  onBindInput(e) {
    const field = e.currentTarget.dataset.field;
    const value = field === 'quantity'
      ? normalizeQuantityByUnit(this.data.bindForm.unit, e.detail.value)
      : e.detail.value;
    this.setData({ [`bindForm.${field}`]: value });
  },

  onTransferInput(e) {
    this.setData({ [`transferForm.${e.currentTarget.dataset.field}`]: e.detail.value });
  },

  onConfirmInput(e) {
    const field = e.currentTarget.dataset.field;
    const value = field === 'quantity'
      ? normalizeQuantityByUnit(this.data.confirmForm.unit, e.detail.value)
      : e.detail.value;
    this.setData({ [`confirmForm.${field}`]: value });
  },

  onBindProductStatusChange(e) {
    const status = e.detail.value === '1' ? '成品' : '半成品';
    this.syncProductsByStatus(status);
  },

  onProductChange(e) {
    this.setData({ 'bindForm.productIndex': Number(e.detail.value) });
  },

  onUnitChange(e) {
    const target = e.currentTarget.dataset.target;
    const unit = e.detail.value === '1' ? '1' : '0';
    const quantityKey = `${target}.quantity`;
    const unitKey = `${target}.unit`;
    const currentQuantity = target === 'bindForm' ? this.data.bindForm.quantity : this.data.confirmForm.quantity;
    this.setData({
      [unitKey]: unit,
      [quantityKey]: normalizeQuantityByUnit(unit, currentQuantity)
    });
  },

  onSideChange(e) {
    const target = e.currentTarget.dataset.target;
    const side = e.detail.value === '1' ? '右' : '左';
    if (target === 'transferForm') {
      this.setData({ 'transferForm.targetSide': side });
      return;
    }
    this.setData({ [`${target}.side`]: side });
  },

  closeBind() {
    this.setData({
      bindVisible: false,
      pendingPallet: null,
      products: [],
      productNames: []
    });
  },

  closeTransfer() {
    this.setData({ transferVisible: false, pendingPallet: null, 'transferForm.code': '' });
  },

  closeConfirm() {
    this.setData({ confirmVisible: false });
  },

  openWarehouseQuery() {
    wx.navigateTo({ url: '/pages/query/warehouse/index' });
  },

  clearInboundTarget() {
    const defaults = { ...getScanDefaults() };
    delete defaults.warehouseName;
    delete defaults.side;
    setScanDefaults(defaults);
    this.setData({
      'confirmForm.warehouseName': '',
      'confirmForm.side': '\u5de6'
    });
    showToast('\u5df2\u6e05\u7a7a\u6307\u5b9a\u5e93\u4f4d');
  },
  noop() {
  }
});
