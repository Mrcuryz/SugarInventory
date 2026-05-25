import { getProductsByStatus } from '../../../api/product';
import { getAssayDetail } from '../../../api/query';
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
  confirmTransferTasks,
  getTaskList
} from '../../../api/task';
import {
  enrichPallet,
  enrichTask,
  FLOW_OPERATION,
  formatAssayStandard,
  formatDateTime,
  formatMetricRange,
  getAssayJudgeMeta,
  getAssayPrimaryStandard,
  getDictItem
} from '../../../utils/dict';
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

function formatDate(value) {
  return normalizeText(value).slice(0, 10);
}

function formatTime(value) {
  const text = formatDateTime(value);
  return text === '-' ? '-' : text.slice(11, 16);
}

function parseWeight(value) {
  if (value == null || value === '') return 0;
  const match = String(value).match(/\d+(\.\d+)?/);
  return match ? Number(match[0]) : 0;
}

function findProductConfig(pallet, productCatalog) {
  const byId = productCatalog.byId || {};
  const byName = productCatalog.byName || {};
  if (pallet.productId && byId[pallet.productId]) return byId[pallet.productId];
  return byName[`${pallet.productStatus || ''}:${pallet.productName || ''}`] || byName[`:${pallet.productName || ''}`] || null;
}

function calculatePalletQuantity(pallet, inventory, product) {
  if (!pallet || !pallet.productName) {
    return { quantityText: '-', weightText: '-' };
  }

  const piecesPerPallet = Number(product && (product.piecesPerPallet || product.pieces_per_pallet));
  const rawQuantity = Number(inventory && inventory.quantity);
  const isPieceUnit = inventory && (inventory.unit === true || inventory.unit === '1');
  let pieces = 0;
  let quantityText = '';

  if (isPieceUnit) {
    pieces = Number.isFinite(rawQuantity) && rawQuantity > 0 ? rawQuantity : 0;
    quantityText = pieces ? `${pieces} 件` : '缺少实际件数';
  } else if (Number.isFinite(piecesPerPallet) && piecesPerPallet > 0) {
    const boardCount = Number.isFinite(rawQuantity) && rawQuantity > 0 ? rawQuantity : 1;
    pieces = boardCount * piecesPerPallet;
    quantityText = `${boardCount} 板 / ${pieces} 件`;
  } else if (Number.isFinite(rawQuantity) && rawQuantity > 0) {
    quantityText = `${rawQuantity} 板`;
  } else {
    quantityText = '缺少每板件数配置';
  }

  const weightPerPiece = parseWeight(product && product.weightPerPiece);
  const weight = pieces && weightPerPiece ? pieces * weightPerPiece : 0;
  return {
    quantityText,
    weightText: weight ? `${Number(weight.toFixed(2))} kg` : '缺少每件重量配置'
  };
}

function buildBaseFields(pallet, inventory, productCatalog) {
  const product = findProductConfig(pallet, productCatalog);
  const quantity = calculatePalletQuantity(pallet, inventory, product);
  const fields = [
    { label: '产品', value: normalizeText(pallet.productName, '-') },
    { label: '产品状态', value: normalizeText(pallet.productStatus) },
    { label: '生产日期', value: formatDate(pallet.productionDate) },
    { label: '筛网', value: normalizeText(pallet.screenMeshName) },
    { label: '数量', value: quantity.quantityText },
    { label: '绑定时间', value: formatDateTime(pallet.createdAt) },
    { label: '重量', value: quantity.weightText }
  ];
  if (pallet.fixedModeEnabled || pallet.fixedProductName) {
    fields.splice(1, 0, { label: '当前固定产品', value: normalizeText(pallet.fixedProductName, '-') });
  }
  return fields;
}

function buildAssayMetrics(assay) {
  if (!assay) return [];
  const hasAppliedStandard = Boolean(
    assay.appliedStandardId
    || assay.appliedStandardName
    || (assay.appliedStandard && assay.appliedStandard.standardName)
    || (assay.standardSnapshot && assay.standardSnapshot.items && assay.standardSnapshot.items.length)
  );
  const failedMap = (assay.failedMetrics || []).reduce((result, item) => {
    result[item.metricCode] = item;
    return result;
  }, {});
  const snapshotItems = assay.standardSnapshot && assay.standardSnapshot.items && assay.standardSnapshot.items.length
    ? assay.standardSnapshot.items
    : [
      { metricCode: 'color_value', metricName: '色值', unit: ' IU' },
      { metricCode: 'reducing_sugar', metricName: '还原糖', unit: '' },
      { metricCode: 'dry_weight', metricName: '干燥失重', unit: '' },
      { metricCode: 'conductivity_ash', metricName: '电导灰分', unit: '' },
      { metricCode: 'sucrose', metricName: '蔗糖分', unit: ' g/100g' },
      { metricCode: 'insoluble_impurity', metricName: '不溶于水杂质', unit: '' },
      { metricCode: 'ph', metricName: 'pH', unit: '' }
    ];
  const valueFieldMap = {
    color_value: 'colorValue',
    reducing_sugar: 'reducingSugar',
    dry_weight_loss: 'dryWeight',
    dry_weight: 'dryWeight',
    conductivity_ash: 'conductivityAsh',
    sucrose: 'sucrose',
    insoluble_impurity: 'insolubleImpurity',
    ph: 'phValue',
    ph_value: 'phValue'
  };
  return snapshotItems.map(item => {
    const valueField = valueFieldMap[item.metricCode];
    const value = valueField ? assay[valueField] : null;
    const failed = failedMap[item.metricCode];
    const hasValue = value !== null && value !== undefined && value !== '';
    const hasStandardRange = item.minValue != null || item.maxValue != null;
    const unavailable = !hasValue || !hasAppliedStandard || !hasStandardRange;
    return {
      key: item.metricCode,
      label: item.metricName,
      valueText: value == null || value === '' ? '-' : `${value}${item.unit || ''}`,
      standardText: item.minValue != null || item.maxValue != null ? formatMetricRange(item) : (assay.appliedStandardText || assay.standardText || '未配置标准区间'),
      statusText: unavailable ? '暂无' : (failed ? '未达标' : '达标'),
      statusType: unavailable ? 'info' : (failed ? 'danger' : 'success'),
      reasonText: unavailable ? '' : (failed ? failed.reason : '')
    };
  });
}

function normalizePalletAssay(assay) {
  if (!assay) return null;
  return {
    ...assay,
    createdAtText: formatDateTime(assay.createdAt),
    standardText: getAssayPrimaryStandard(assay, formatAssayStandard(assay.qualifiedStandards, '当前未采用标准')),
    appliedStandardText: getAssayPrimaryStandard(assay, '当前未采用标准'),
    matchedStandardsText: (assay.matchedStandards || []).join('、') || '无',
    failedMetricCount: assay.failedMetricCount || 0
  };
}

function buildLocationDisplay(warehouseName, side, rowNumber, layer) {
  const hasAnyLocation = Boolean(warehouseName || side || rowNumber != null || layer != null);
  if (!hasAnyLocation) return '';
  return [
    warehouseName ? `库位${warehouseName}` : '库位未记录',
    side ? `${side}侧` : '侧未记录',
    rowNumber != null ? `第${rowNumber}排` : '排未记录',
    layer != null ? `${layer}层` : '层未记录'
  ].join(' · ');
}

function buildLocationRoute(key, action, text, emptyText, suffix = '') {
  return {
    key,
    action,
    emptyText,
    suffix,
    text: text || emptyText
  };
}

function buildFlowLocation(flow) {
  const fromText = buildLocationDisplay(flow.fromWarehouseName, flow.fromSide, flow.fromRowNumber, flow.fromLayer);
  const toText = buildLocationDisplay(flow.toWarehouseName, flow.toSide, flow.toRowNumber, flow.toLayer);

  if (flow.operationType === 'CANCELED') {
    return {
      hasLocation: false,
      lines: [],
      routes: [],
      summary: '任务已取消'
    };
  }

  if (flow.operationType === 'SEMI_BIND' || flow.operationType === 'FINISH_BIND') {
    return {
      hasLocation: false,
      lines: [],
      routes: [],
      summary: '绑定关系事件'
    };
  }

  if (flow.operationType === 'ASSAY' || flow.operationType === 'INVALID') {
    return {
      hasLocation: false,
      lines: [],
      routes: [],
      summary: '无位置变化'
    };
  }

  if (flow.operationType === 'PREPARE_CONSUMED') {
    const route = buildLocationRoute('from', '历史生产占用', fromText, '原位置未记录', '');
    return {
      hasLocation: true,
      mode: 'single',
      routes: [route],
      lines: [`${route.action} ${route.text} ${route.suffix}`],
      summary: '业务阶段变化'
    };
  }
  if (flow.operationType === 'CONSUMED') {
    return {
      hasLocation: false,
      lines: [],
      routes: [],
      summary: '业务阶段变化'
    };
  }

  if (flow.operationType === 'TRANSFER') {
    const fromRoute = buildLocationRoute('from', '原位置', fromText, '原位置未记录');
    const toRoute = buildLocationRoute('to', '目的位置', toText, '目的位置未记录');
    return {
      hasLocation: true,
      mode: 'route',
      routes: [fromRoute, toRoute],
      lines: [`${fromRoute.text} 调拨至 ${toRoute.text}`],
      summary: '库内调拨'
    };
  }

  if (flow.operationType === 'OUT') {
    const route = buildLocationRoute('from', '出库', fromText, '原位置未记录', '');
    return {
      hasLocation: true,
      mode: 'single',
      routes: [route],
      lines: [`${route.action} ${route.text} ${route.suffix}`],
      summary: '出库位置'
    };
  }

  if (flow.operationType === 'SEMI_INSTOCK' || flow.operationType === 'FINISH_INSTOCK') {
    const route = buildLocationRoute('to', '存入', toText, '目的位置未记录');
    return {
      hasLocation: true,
      mode: 'single',
      routes: [route],
      lines: [`${route.action} ${route.text}`],
      summary: '入库位置'
    };
  }

  return {
    hasLocation: false,
    lines: [],
    routes: [],
    summary: '无位置变化'
  };
}

function getFlowIconSrc(operationType) {
  const map = {
    SEMI_BIND: '/assets/icons-line/flow-link.svg',
    FINISH_BIND: '/assets/icons-line/flow-link.svg',
    SEMI_INSTOCK: '/assets/icons-line/flow-in.svg',
    FINISH_INSTOCK: '/assets/icons-line/flow-in.svg',
    OUT: '/assets/icons-line/flow-out.svg',
    TRANSFER: '/assets/icons-line/flow-transfer.svg',
    PREPARE_CONSUMED: '/assets/icons-line/flow-prepare.svg',
    CONSUMED: '/assets/icons-line/flow-consume.svg',
    CANCELED: '/assets/icons-line/flow-cancel.svg',
    INVALID: '/assets/icons-line/flow-invalid.svg',
    ASSAY: '/assets/icons-line/flow-assay.svg'
  };
  return map[operationType] || '/assets/icons-line/icon-task.svg';
}

function normalizeOperationLabel(operationType, operationName, fallbackLabel) {
  if (operationType === 'TRANSFER') return '调拨';
  if (!operationName) return fallbackLabel;
  return String(operationName)
    .replace(/托盘调拨/g, '调拨')
    .replace(/托盘码/g, '二维码')
    .replace(/托盘/g, '');
}

function buildFlowGroups(rows, palletCode) {
  const sorted = [...(rows || [])].sort((a, b) => {
    const at = new Date(a.operationTime || 0).getTime();
    const bt = new Date(b.operationTime || 0).getTime();
    if (at !== bt) return at - bt;
    return (a.id || 0) - (b.id || 0);
  });
  const groups = [];
  sorted.forEach((item, index) => {
    const operation = getDictItem(FLOW_OPERATION, item.operationType);
    const date = formatDate(item.operationTime);
    const location = buildFlowLocation(item);
    const normalized = {
      ...item,
      operationLabel: normalizeOperationLabel(item.operationType, item.operationName, operation.label),
      operationTypeTag: operation.type,
      operationDateText: date,
      operationTimeText: formatTime(item.operationTime),
      locationText: location.lines.join('；'),
      locationLines: location.lines,
      locationRoutes: location.routes || [],
      locationMode: location.mode || 'single',
      hasLocation: location.hasLocation,
      locationSummary: location.summary,
      flowExtras: location.mode === 'route' ? [
        palletCode ? `二维码 ${palletCode}` : ''
      ].filter(Boolean) : [],
      remarkText: normalizeText(item.remark, ''),
      iconSrc: getFlowIconSrc(item.operationType),
      isLast: index === sorted.length - 1
    };
    let group = groups[groups.length - 1];
    if (!group || group.date !== date) {
      group = { date, items: [] };
      groups.push(group);
    }
    group.items.push(normalized);
  });
  return groups;
}

function inferBusinessStage(pallet, flows) {
  const sorted = [...(flows || [])].sort((a, b) => new Date(a.operationTime || 0).getTime() - new Date(b.operationTime || 0).getTime());
  const hasPrepare = sorted.some(item => item.operationType === 'PREPARE_CONSUMED');
  const hasConsumed = sorted.some(item => item.operationType === 'CONSUMED') || pallet.status === 'CONSUMED';
  const isSemi = pallet.productStatus === '半成品';
  if (hasConsumed) return { key: 'consumed', label: '已消耗', type: 'info', readonly: true };
  if (isSemi && pallet.status === 'INSTOCK' && hasPrepare) return { key: 'prepare', label: '历史生产占用中', type: 'warning', readonly: true };
  if (pallet.status === 'FREE') return { key: 'free', label: '空闲 / 未绑定', type: 'success', readonly: false };
  if (pallet.status === 'PENDING' || pallet.status === 'PENDING_IN') return { key: 'pending', label: '待入库', type: 'warning', readonly: false };
  if (pallet.status === 'INSTOCK') return { key: 'instock', label: '在库', type: 'primary', readonly: false };
  if (pallet.status === 'INVALID') return { key: 'invalid', label: '作废', type: 'danger', readonly: true };
  return { key: 'unknown', label: pallet.statusLabel || pallet.status || '未知状态', type: pallet.statusTagType || 'info', readonly: false };
}

function assayResultMeta(result) {
  return getAssayJudgeMeta(result, '未出结论');
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
    assayDetailId: '',
    assayResolveMessage: '',
    assayMetrics: [],
    assayResult: { label: '未出结论', type: 'info' },
    baseFields: [],
    flows: [],
    flowGroups: [],
    pendingTasks: [],
    actions: [],
    summary: {},
    readonlyStageNotice: '',
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
      const byName = {};
      [
        ...((semi || []).map(item => ({ ...item, productStatus: item.productStatus || '半成品' }))),
        ...((finish || []).map(item => ({ ...item, productStatus: item.productStatus || '成品' })))
      ].forEach(item => {
        productCatalog[item.id || item.productId] = item;
        byName[`${item.productStatus || ''}:${item.productName || ''}`] = item;
        byName[`:${item.productName || ''}`] = item;
      });
      this.setData({ productCatalog: { byId: productCatalog, byName } });
    } catch (error) {
      this.setData({ productCatalog: {} });
    }
  },

  async loadDetail() {
    if (!this.data.code) {
      showToast('缺少二维码编号');
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
      const rawAssay = assayRes.status === 'fulfilled' ? assayRes.value : null;
      let assay = null;
      if (rawAssay && rawAssay.id) {
        try {
          const detailAssay = await getAssayDetail(rawAssay.id);
          assay = normalizePalletAssay({
            ...detailAssay,
            resolveSource: rawAssay.resolveSource,
            resolveStatus: rawAssay.resolveStatus,
            resolveMessage: rawAssay.resolveMessage,
            autoBound: rawAssay.autoBound,
            multipleCandidates: rawAssay.multipleCandidates,
            candidateCount: rawAssay.candidateCount
          });
        } catch (error) {
          assay = normalizePalletAssay(rawAssay);
        }
      } else if (rawAssay) {
        assay = normalizePalletAssay(rawAssay);
      }
      const pendingTasks = tasksRes.status === 'fulfilled'
        ? ((tasksRes.value && tasksRes.value.records) || [])
          .filter(item => item.bizScene !== 'PREPARE_CONSUMED')
          .map(item => ({
            ...enrichTask(item),
            canConfirm: item.taskStatus === 'PENDING',
            canCancel: item.taskStatus === 'PENDING'
          }))
        : [];

      let flows = [];
      let flowGroups = [];
      const cycle = cyclesRes.status === 'fulfilled' && cyclesRes.value && cyclesRes.value.records && cyclesRes.value.records[0];
      if (cycle) {
        try {
          const detailRows = await getPalletFlowDetails(code, cycle.cycleNo);
          flowGroups = buildFlowGroups(detailRows || [], code);
          flows = flowGroups.reduce((list, group) => list.concat(group.items), []);
        } catch (error) {
          flows = [];
          flowGroups = [];
        }
      }

      const businessStage = inferBusinessStage(pallet, flows);
      const displayPallet = {
        ...pallet,
        statusLabel: businessStage.label,
        statusTagType: businessStage.type
      };
      const actions = this.buildActions(displayPallet, pendingTasks, businessStage);

      this.setData({
        pallet: displayPallet,
        inventory,
        assay,
        assayDetailId: assay && assay.id ? assay.id : '',
        assayResolveMessage: rawAssay && rawAssay.resolveMessage ? rawAssay.resolveMessage : '',
        assayMetrics: buildAssayMetrics(assay),
        assayResult: assayResultMeta(assay && assay.judgeResult),
        baseFields: buildBaseFields(displayPallet, inventory, this.data.productCatalog),
        pendingTasks,
        flows,
        flowGroups,
        actions,
        readonlyStageNotice: businessStage.readonly ? (businessStage.key === 'prepare'
          ? '当前二维码已处于历史生产占用状态，已离开仓库主库存。'
          : '当前二维码处于只读阶段，不再执行普通现场任务。') : '',
        summary: this.buildSummary(displayPallet, inventory, pendingTasks, actions, businessStage),
        confirmTargets: [],
        confirmTargetCount: 0,
        loading: false
      });
    } catch (error) {
      this.setData({ loading: false });
      showError(error, '二维码详情加载失败');
    }
  },

  buildSummary(pallet, inventory, pendingTasks, actions, businessStage) {
    const noLocationHint = businessStage.key === 'prepare'
      ? '已处于历史生产占用'
      : businessStage.key === 'consumed'
        ? '已被成品生产消耗'
        : businessStage.key === 'free'
          ? '未入库，暂无库位信息'
          : '可能处于未入库、已出库、历史生产占用或已释放状态';
    return {
      locationText: formatLocationText(inventory),
      locationHint: inventory && inventory.inStockTime
        ? `入库时间 ${formatDateTime(inventory.inStockTime)}`
        : noLocationHint,
      taskText: businessStage.readonly ? businessStage.label : (pendingTasks.length ? `待处理 ${pendingTasks.length} 条任务` : '当前无待处理任务'),
      actionText: businessStage.key === 'prepare' ? '已离开仓库主库存' : (actions[0] ? actions[0].label : '查看二维码信息')
    };
  },

  buildActions(pallet, pendingTasks, businessStage) {
    if (businessStage.readonly) {
      return;
    }

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
    if (key === 'processPending') this.processPendingTasks(this.data.pendingTasks);
    if (key === 'cancelPending') this.cancelPendingTasks(this.data.pendingTasks);
  },

  goScanMode(mode) {
    wx.setStorageSync('preferredScanMode', mode);
    wx.setStorageSync('pendingScanCodeAction', {
      mode,
      code: this.data.code,
      from: 'pallet-detail',
      createdAt: Date.now()
    });
    wx.switchTab({ url: '/pages/scan/index/index' });
  },

  refresh() {
    this.loadDetail();
  },

  onTapTask() {
    wx.switchTab({ url: '/pages/tasks/index/index' });
  },

  openAssayDetail() {
    if (!this.data.assayDetailId) return;
    wx.navigateTo({ url: `/pages/query/assay-detail/index?id=${this.data.assayDetailId}` });
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
      const finishOut = taskList.filter(item => item.taskType === 'OUT' && item.bizScene === 'FINISH_OUT').map(item => item.code);
      const transfer = taskList.filter(item => item.taskType === 'TRANSFER').map(item => item.code);
      if (semiOut.length) await confirmSemiOutTasks(semiOut);
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
      await cancelTasks(codes, '小程序二维码详情取消任务');
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
        const product = this.data.productCatalog.byId && this.data.productCatalog.byId[task.productId];
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
      remark: remark || '小程序二维码详情确认入库'
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
  },

  noop() {
  }
});
