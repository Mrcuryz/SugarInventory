import { getProductsByStatus } from '../../../api/product';
import {
  getPalletAssay,
  getPalletFlowCycles,
  getPalletFlowDetails,
  getPalletInventory,
  parseCode
} from '../../../api/pallet';
import {
  cancelTasks,
  confirmFinishOutTasks,
  confirmPalletInBatch,
  confirmSemiOutTasks,
  confirmSemiPrepareTasks,
  confirmTransferTasks,
  getTaskList
} from '../../../api/task';
import { enrichPallet, enrichTask, FLOW_OPERATION, formatDateTime, getDictItem } from '../../../utils/dict';
import { requireLogin } from '../../../utils/auth';
import { normalizeQuantityByUnit, validateQuantity } from '../../../utils/quantity';
import { getScanDefaults, setScanDefaults } from '../../../utils/storage';
import { confirm, showError, showToast } from '../../../utils/toast';

const DETAIL_TABS = [
  { key: 'base', label: '基础信息' },
  { key: 'assay', label: '化验数据' },
  { key: 'flow', label: '流转记录' }
];

function today() {
  const date = new Date();
  const month = String(date.getMonth() + 1).padStart(2, '0');
  const day = String(date.getDate()).padStart(2, '0');
  return `${date.getFullYear()}-${month}-${day}`;
}

function normalizeText(value, fallback = '-') {
  if (value == null || value === '') return fallback;
  return String(value);
}

function formatUnitText(unit) {
  return unit === true || unit === '1' ? '件' : '板';
}

function formatQuantityText(quantity, unit) {
  if (quantity == null || quantity === '') return '-';
  return `${quantity} ${formatUnitText(unit)}`;
}

function formatLocationText(inventory) {
  if (!inventory || !inventory.warehouseName) return '当前无库位信息';
  const parts = [
    inventory.warehouseName,
    inventory.side ? `${inventory.side}侧` : '',
    inventory.rowNumber != null ? `${inventory.rowNumber}排` : '',
    inventory.layer != null ? `${inventory.layer}层` : ''
  ].filter(Boolean);
  return parts.join(' · ');
}

function buildBaseFields(pallet, inventory) {
  return [
    { label: '产品', value: normalizeText(pallet.productName, '未绑定产品') },
    { label: '产品状态', value: normalizeText(pallet.productStatus) },
    { label: '产品类型', value: normalizeText(pallet.productType) },
    { label: '生产日期', value: normalizeText(pallet.productionDate) },
    { label: '筛网', value: normalizeText(pallet.screenMeshName) },
    { label: '数量', value: formatQuantityText(inventory && inventory.quantity, inventory && inventory.unit) },
    { label: '绑定时间', value: formatDateTime(pallet.createdAt) },
    { label: '重量', value: '暂未提供' }
  ];
}

function buildAssayMetrics(assay) {
  if (!assay) return [];
  return [
    { key: 'colorValue', label: '色值', value: assay.colorValue, unit: ' IU' },
    { key: 'reducingSugar', label: '还原糖', value: assay.reducingSugar, unit: '' },
    { key: 'dryWeight', label: '干燥失重', value: assay.dryWeight, unit: '' },
    { key: 'conductivityAsh', label: '电导灰分', value: assay.conductivityAsh, unit: '' },
    { key: 'sucrose', label: '蔗糖分', value: assay.sucrose, unit: ' g/100g' },
    { key: 'insolubleImpurity', label: '不溶于水杂质', value: assay.insolubleImpurity, unit: '' },
    { key: 'phValue', label: 'pH', value: assay.phValue, unit: '' }
  ].map(item => ({
    ...item,
    valueText: item.value == null || item.value === '' ? '-' : `${item.value}${item.unit}`
  }));
}

function buildFlowLocation(flow) {
  const from = [
    flow.fromWarehouseName,
    flow.fromSide ? `${flow.fromSide}侧` : '',
    flow.fromRowNumber != null ? `${flow.fromRowNumber}排` : '',
    flow.fromLayer != null ? `${flow.fromLayer}层` : ''
  ].filter(Boolean).join(' · ');
  const to = [
    flow.toWarehouseName,
    flow.toSide ? `${flow.toSide}侧` : '',
    flow.toRowNumber != null ? `${flow.toRowNumber}排` : '',
    flow.toLayer != null ? `${flow.toLayer}层` : ''
  ].filter(Boolean).join(' · ');
  if (from && to) return `${from} → ${to}`;
  return to || from || '未记录位置变化';
}

function assayResultMeta(result) {
  const value = normalizeText(result, '未出结论');
  const passed = value.includes('合格') && !value.includes('不合格');
  const failed = value.includes('不合格');
  return {
    label: value,
    type: passed ? 'success' : failed ? 'danger' : 'info'
  };
}

Page({
  data: {
    code: '',
    loading: true,
    tabs: DETAIL_TABS,
    activeTab: 'base',
    pallet: {},
    inventory: null,
    assay: null,
    assayMetrics: [],
    assayResult: { label: '未出结论', type: 'info' },
    baseFields: [],
    flows: [],
    pendingTasks: [],
    actions: [],
    summary: {},
    confirmVisible: false,
    confirmTargets: [],
    confirmTargetCount: 0,
    productCatalog: {},
    confirmForm: {
      warehouseName: '',
      side: '左',
      entryDate: '',
      quantity: '1',
      unit: '0',
      remark: ''
    }
  },

  async onLoad(options) {
    this.setData({ code: decodeURIComponent(options.code || '') });
    this.resetConfirmDefaults();
    await this.loadProductCatalog();
    if (this.data.code) this.loadDetail();
  },

  onShow() {
    requireLogin();
  },

  resetConfirmDefaults() {
    const defaults = getScanDefaults();
    this.setData({
      'confirmForm.warehouseName': defaults.warehouseName || '',
      'confirmForm.side': defaults.side || '左',
      'confirmForm.entryDate': defaults.entryDate || today(),
      'confirmForm.quantity': '1',
      'confirmForm.unit': '0'
    });
  },

  async loadProductCatalog() {
    try {
      const [semi, finish] = await Promise.all([
        getProductsByStatus('半成品'),
        getProductsByStatus('成品')
      ]);
      const productCatalog = {};
      [...(semi || []), ...(finish || [])].forEach(item => {
        productCatalog[item.id || item.productId] = item;
      });
      this.setData({ productCatalog });
    } catch (error) {
      this.setData({ productCatalog: {} });
    }
  },

  async loadDetail() {
    if (!this.data.code) {
      showToast('缺少托盘码');
      return;
    }
    this.setData({ loading: true });
    try {
      const code = this.data.code;
      const [palletRes, inventoryRes, assayRes, tasksRes, cyclesRes] = await Promise.allSettled([
        parseCode(code),
        getPalletInventory(code),
        getPalletAssay(code),
        getTaskList({ code, status: 'PENDING', pageNum: 1, pageSize: 5 }),
        getPalletFlowCycles(code, { pageNum: 1, pageSize: 1 })
      ]);

      const pallet = palletRes.status === 'fulfilled' ? enrichPallet(palletRes.value) : {};
      const inventory = inventoryRes.status === 'fulfilled' && inventoryRes.value
        ? {
          ...inventoryRes.value,
          inStockTimeText: formatDateTime(inventoryRes.value.inStockTime)
        }
        : null;
      const assay = assayRes.status === 'fulfilled' && assayRes.value
        ? {
          ...assayRes.value,
          createdAtText: formatDateTime(assayRes.value.createdAt)
        }
        : null;
      const pendingTasks = tasksRes.status === 'fulfilled'
        ? ((tasksRes.value && tasksRes.value.records) || []).map(item => ({
          ...enrichTask(item),
          canConfirm: item.taskStatus === 'PENDING',
          canCancel: item.taskStatus === 'PENDING'
        }))
        : [];

      let flows = [];
      const cycle = cyclesRes.status === 'fulfilled' && cyclesRes.value && cyclesRes.value.records && cyclesRes.value.records[0];
      if (cycle) {
        try {
          const detailRows = await getPalletFlowDetails(code, cycle.cycleNo);
          flows = (detailRows || []).slice(-10).reverse().map((item, index, list) => {
            const operation = getDictItem(FLOW_OPERATION, item.operationType);
            return {
              ...item,
              operationLabel: item.operationName || operation.label,
              operationTypeTag: operation.type,
              operationTimeText: formatDateTime(item.operationTime),
              locationText: buildFlowLocation(item),
              remarkText: normalizeText(item.remark, ''),
              isLast: index === list.length - 1
            };
          });
        } catch (error) {
          flows = [];
        }
      }

      const actions = this.buildActions(pallet, pendingTasks);

      this.setData({
        pallet,
        inventory,
        assay,
        assayMetrics: buildAssayMetrics(assay),
        assayResult: assayResultMeta(assay && assay.isQualified),
        baseFields: buildBaseFields(pallet, inventory),
        pendingTasks,
        flows,
        actions,
        summary: this.buildSummary(pallet, inventory, pendingTasks, actions),
        confirmTargets: [],
        confirmTargetCount: 0,
        loading: false
      });
    } catch (error) {
      this.setData({ loading: false });
      showError(error, '托盘详情加载失败');
    }
  },

  buildSummary(pallet, inventory, pendingTasks, actions) {
    return {
      locationText: formatLocationText(inventory),
      locationHint: inventory && inventory.inStockTime
        ? `入库时间 ${formatDateTime(inventory.inStockTime)}`
        : '可能处于未入库或已出库状态',
      taskText: pendingTasks.length ? `待处理 ${pendingTasks.length} 条任务` : '当前无待处理任务',
      actionText: actions[0] ? actions[0].label : '查看托盘信息'
    };
  },

  buildActions(pallet, pendingTasks) {
    if (pendingTasks.length) {
      return [
        { key: 'processPending', label: pendingTasks.length > 1 ? `处理任务(${pendingTasks.length})` : '处理任务', type: 'primary' },
        { key: 'cancelPending', label: '取消任务', type: 'danger' }
      ];
    }

    if (pallet.status === 'FREE') {
      return [
        { key: 'createIn', label: '绑定入库', type: 'primary' },
        { key: 'viewFlows', label: '查看流转', type: 'plain' }
      ];
    }

    if (pallet.status === 'INSTOCK') {
      const actions = [
        { key: 'createOut', label: '创建出库', type: 'primary' },
        { key: 'createTransfer', label: '创建调拨', type: 'plain' }
      ];
      if (pallet.productStatus === '半成品') {
        actions.push({ key: 'createPrepare', label: '转入备料池', type: 'plain' });
      }
      return actions;
    }

    if (pallet.status === 'INVALID') {
      return [
        { key: 'viewFlows', label: '查看流转', type: 'plain' },
        { key: 'scan', label: '继续扫码', type: 'plain' }
      ];
    }

    return [
      { key: 'scan', label: '继续扫码', type: 'plain' },
      { key: 'tasks', label: '任务中心', type: 'primary' }
    ];
  },

  onTabTap(e) {
    this.setData({ activeTab: e.currentTarget.dataset.key });
  },

  onAction(e) {
    const key = e.detail.action.key;
    if (key === 'scan') wx.switchTab({ url: '/pages/scan/index/index' });
    if (key === 'tasks') wx.switchTab({ url: '/pages/tasks/index/index' });
    if (key === 'viewFlows') this.setData({ activeTab: 'flow' });
    if (key === 'createIn') this.goScanMode('in');
    if (key === 'createOut') this.goScanMode('out');
    if (key === 'createTransfer') this.goScanMode('transfer');
    if (key === 'createPrepare') this.goScanMode('prepare');
    if (key === 'processPending') this.processPendingTasks(this.data.pendingTasks);
    if (key === 'cancelPending') this.cancelPendingTasks(this.data.pendingTasks);
  },

  goScanMode(mode) {
    wx.setStorageSync('preferredScanMode', mode);
    wx.switchTab({ url: '/pages/scan/index/index' });
  },

  refresh() {
    this.loadDetail();
  },

  onTapTask() {
    wx.switchTab({ url: '/pages/tasks/index/index' });
  },

  onConfirmTask(e) {
    this.processPendingTasks([e.detail.task]);
  },

  onCancelTask(e) {
    this.cancelPendingTasks([e.detail.task]);
  },

  async processPendingTasks(tasks) {
    const taskList = Array.isArray(tasks) ? tasks.filter(Boolean) : [];
    if (!taskList.length) return;
    if (taskList.some(item => item.taskType === 'SEMI_IN' || item.taskType === 'FINISH_IN' || item.taskType === 'IN')) {
      this.setData({
        confirmVisible: true,
        confirmTargets: taskList,
        confirmTargetCount: taskList.length
      });
      return;
    }
    const ok = await confirm(`确认处理 ${taskList.length} 条待处理任务？`, '处理任务');
    if (!ok) return;
    try {
      const semiOut = taskList.filter(item => item.taskType === 'OUT' && item.bizScene === 'DIRECT_OUT').map(item => item.code);
      const semiPrepare = taskList.filter(item => item.taskType === 'OUT' && item.bizScene === 'PREPARE_CONSUMED').map(item => item.code);
      const finishOut = taskList.filter(item => item.taskType === 'OUT' && item.bizScene === 'FINISH_OUT').map(item => item.code);
      const transfer = taskList.filter(item => item.taskType === 'TRANSFER').map(item => item.code);
      if (semiOut.length) await confirmSemiOutTasks(semiOut);
      if (semiPrepare.length) await confirmSemiPrepareTasks(semiPrepare);
      if (finishOut.length) await confirmFinishOutTasks(finishOut);
      if (transfer.length) await confirmTransferTasks(transfer);
      showToast('任务已处理', 'success');
      this.loadDetail();
    } catch (error) {
      showError(error, '任务处理失败');
    }
  },

  async cancelPendingTasks(tasks) {
    const taskList = Array.isArray(tasks) ? tasks.filter(Boolean) : [];
    const codes = taskList.map(item => item.code);
    if (!codes.length) return;
    const ok = await confirm(`取消 ${codes.length} 条待处理任务？`, '取消任务');
    if (!ok) return;
    try {
      await cancelTasks(codes, '小程序托盘详情取消任务');
      showToast('已取消', 'success');
      this.loadDetail();
    } catch (error) {
      showError(error, '取消失败');
    }
  },

  getPendingTasksForConfirm() {
    const source = this.data.confirmTargets.length ? this.data.confirmTargets : this.data.pendingTasks;
    return source.filter(item =>
      item.taskType === 'SEMI_IN' || item.taskType === 'FINISH_IN' || item.taskType === 'IN'
    );
  },

  async submitConfirmIn() {
    const { warehouseName, side, entryDate, quantity, unit, remark } = this.data.confirmForm;
    if (!warehouseName) {
      showToast('请填写入库库位');
      return;
    }

    const selectedTasks = this.getPendingTasksForConfirm();
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
      remark: remark || '小程序托盘详情确认入库'
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
      this.setData({ confirmVisible: false, confirmTargets: [], confirmTargetCount: 0 });
      this.loadDetail();
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
    this.setData({ confirmVisible: false, confirmTargets: [], confirmTargetCount: 0 });
  }
});
