import { getProductionOrderDetail, pickProductionMaterials } from '../../../api/production';
import { getPalletInventory, parseCode } from '../../../api/pallet';
import { requireLogin } from '../../../utils/auth';
import { scanCode } from '../../../utils/scan';
import { confirm, showError, showToast } from '../../../utils/toast';
import { formatDateTime } from '../../../utils/dict';

const STATUS_MAP = {
  ISSUED: { label: '已下发', type: 'primary' },
  MATERIALING: { label: '领料中', type: 'warning' },
  MATERIALED: { label: '已领料', type: 'success' },
  OUTPUT_BINDING: { label: '产出中', type: 'warning' },
  PREPRINTED: { label: '已预打印', type: 'warning' },
  WAIT_INBOUND: { label: '待入库', type: 'primary' },
  PART_INBOUND: { label: '部分入库', type: 'warning' },
  COMPLETED: { label: '已完成', type: 'success' },
  CANCELED: { label: '已取消', type: 'info' }
};

function statusMeta(status) {
  return STATUS_MAP[status] || { label: status || '-', type: 'info' };
}

function quantityText(row) {
  if (!row) return '暂无';
  const isPieceUnit = row.unit === true || row.unit === 1 || row.unit === '1';
  const boards = isPieceUnit ? Number(row.boardCount || 0) : Number(row.quantity || row.boardCount || 0);
  const pieces = Number(row.pieces || row.pieceCount || (isPieceUnit ? row.quantity : 0) || 0);
  if (boards && pieces) return `${boards}板${pieces}件`;
  if (boards) return `${boards}板`;
  if (pieces) return `${pieces}件`;
  return '暂无';
}

function outputQuantityText(row) {
  const boards = Number(row && row.boardCount || 0);
  const pieces = Number(row && row.pieceCount || 0);
  if (boards && pieces) return `${boards}板${pieces}件`;
  if (boards) return `${boards}板`;
  if (pieces) return `${pieces}件`;
  return '暂无';
}

function outputCodeQuantityText(row) {
  const boards = row && row.unit === '0' ? Number(row.quantity || 0) : 0;
  const pieces = Number(row && (row.pieces || (row.unit === '1' ? row.quantity : 0)) || 0);
  if (boards && pieces) return `${boards}板${pieces}件`;
  if (boards) return `${boards}板`;
  if (pieces) return `${pieces}件`;
  return '暂无';
}

function compact(value) {
  return value && value !== '-' ? value : '暂无';
}

function locationText(inventory) {
  if (!inventory) return '暂无库位';
  return [
    inventory.warehouseName,
    inventory.side ? `${inventory.side}侧` : '',
    inventory.rowNumber ? `${inventory.rowNumber}排` : '',
    inventory.layer ? `${inventory.layer}层` : ''
  ].filter(Boolean).join(' · ') || '暂无库位';
}

function decorate(detail) {
  const base = detail.baseInfo || {};
  const status = statusMeta(base.status);
  const materials = (detail.materials || []).map(item => ({
    ...item,
    quantityText: quantityText(item),
    pickedAtText: formatDateTime(item.pickedAt),
    locationText: [item.warehouseName, item.side ? `${item.side}侧` : '', item.rowNumber ? `${item.rowNumber}排` : '', item.layer ? `${item.layer}层` : ''].filter(Boolean).join(' · ') || '-'
  }));
  const outputs = (detail.outputs || []).map(item => ({
    ...item,
    quantityText: outputQuantityText(item),
    codes: (item.codes || []).map(code => ({
      ...code,
      quantityText: outputCodeQuantityText(code)
    }))
  }));
  const labelBatches = (detail.labelBatches || []).map(item => ({
    ...item,
    printedAtText: formatDateTime(item.printedAt),
    statusLabel: {
      RESERVED: '已预留',
      PRINTED: '已打印',
      CLOSED: '已失效',
      CANCELED: '已取消'
    }[item.status] || item.status || '-'
  }));
  return {
    baseInfo: {
      ...base,
      orderTypeText: base.orderType === 'SEMI' ? '半成品生产' : '成品生产',
      statusLabel: status.label,
      statusType: status.type,
      plannedMaterialText: compact(base.plannedMaterialText),
      plannedOutputText: compact(base.plannedOutputText)
    },
    materials,
    outputs,
    labelBatches
  };
}

Page({
  data: {
    id: '',
    loading: false,
    picking: false,
    detail: null,
    pendingMaterial: null
  },

  onLoad(options) {
    requireLogin();
    this.setData({ id: options.id || '' });
    this.loadDetail();
  },

  onShow() {
    requireLogin();
  },

  async loadDetail() {
    if (!this.data.id) return;
    this.setData({ loading: true });
    try {
      const res = await getProductionOrderDetail(this.data.id);
      this.setData({ detail: decorate(res), pendingMaterial: null });
    } catch (error) {
      showError(error, '生产订单详情加载失败');
    } finally {
      this.setData({ loading: false });
    }
  },

  async scanMaterial() {
    const order = this.data.detail && this.data.detail.baseInfo;
    if (!order || order.orderType !== 'FINISH') {
      showToast('只有成品生产订单需要领用半成品');
      return;
    }
    if (['COMPLETED', 'CANCELED'].includes(order.status)) {
      showToast('当前订单不能继续领用');
      return;
    }
    this.setData({ picking: true, pendingMaterial: null });
    try {
      const code = String(await scanCode() || '').trim().toUpperCase();
      if (!code) {
        showToast('二维码为空');
        return;
      }
      const pallet = await parseCode(code);
      let inventory = null;
      try {
        inventory = await getPalletInventory(code);
      } catch (error) {
        inventory = null;
      }
      this.setData({
        pendingMaterial: {
          code,
          productName: pallet.productName || '-',
          productStatus: pallet.productStatus || '-',
          productionDate: pallet.productionDate || '-',
          status: pallet.status || '-',
          quantityText: quantityText(inventory || pallet),
          locationText: locationText(inventory)
        }
      });
    } catch (error) {
      showError(error, '扫码识别失败');
    } finally {
      this.setData({ picking: false });
    }
  },

  clearPendingMaterial() {
    this.setData({ pendingMaterial: null });
  },

  async confirmPickMaterial() {
    const pending = this.data.pendingMaterial;
    if (!pending || !pending.code) return;
    const ok = await confirm(
      '确认后，该半成品将从库存移出，并关联到当前生产订单。原二维码将释放为可复用状态，此操作不能直接撤销。',
      '确认领用半成品'
    );
    if (!ok) return;
    this.setData({ picking: true });
    try {
      await pickProductionMaterials(this.data.id, {
        palletCodes: [pending.code],
        remark: '小程序扫码领用半成品'
      });
      showToast('领用成功', 'success');
      await this.loadDetail();
    } catch (error) {
      showError(error, '确认领用失败');
    } finally {
      this.setData({ picking: false });
    }
  },

  goTasks() {
    const order = this.data.detail && this.data.detail.baseInfo;
    if (!order) return;
    const path = order.orderType === 'SEMI'
      ? '/pages/tasks/index/index'
      : '/pages/tasks/index/index';
    wx.switchTab({ url: path });
  }
});
