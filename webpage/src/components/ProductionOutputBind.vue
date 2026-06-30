<template>
  <div class="production-page">
    <el-card class="context-card">
      <div class="context-header">
        <div>
          <div class="section-title">{{ pageTitle }}</div>
          <div class="section-subtitle">{{ pageSubtitle }}</div>
        </div>
        <el-select
          v-model="form.orderId"
          filterable
          placeholder="搜索生产订单"
          style="width: 390px"
          @change="handleOrderChange"
        >
          <el-option
            v-for="item in orderOptions"
            :key="item.id"
            :label="orderOptionLabel(item)"
            :value="item.id"
          />
        </el-select>
      </div>

      <div class="context-grid">
        <div class="context-item"><span>订单号</span><strong>{{ selectedOrder?.orderNo || '暂无' }}</strong></div>
        <div class="context-item"><span>订单类型</span><strong>{{ orderTypeLabel(selectedOrder?.orderType) }}</strong></div>
        <div class="context-item"><span>生产日期</span><strong>{{ selectedOrder?.productionDate || '暂无' }}</strong></div>
        <div class="context-item"><span>状态</span><strong>{{ orderStatusLabel(selectedOrder?.status) }}</strong></div>
        <div class="context-item wide"><span>计划领用</span><strong>{{ compactText(selectedOrder?.plannedMaterialText) }}</strong></div>
        <div class="context-item wide"><span>计划产出</span><strong>{{ compactText(selectedOrder?.plannedOutputText) }}</strong></div>
      </div>
    </el-card>

    <el-card class="form-card">
      <div class="section-title">预打印订单码</div>
      <el-alert
        class="stage-alert"
        type="warning"
        :closable="false"
        show-icon
        title="现场必须按同一产品、同一批次顺序贴码。重打只会重打同一批标签，不会重新分配二维码，请避免重复纸质标签流入现场。"
      />
      <el-table :data="preprintRows" class="production-entry-table" border size="small" empty-text="暂无">
        <el-table-column label="产品" min-width="260">
          <template #default="{ row }">
            <el-select v-model="row.productId" filterable :placeholder="productPlaceholder" style="width: 100%" @change="syncRowQrCount(row)">
              <el-option v-for="item in availableProducts" :key="item.id" :label="item.productName" :value="item.id" />
            </el-select>
          </template>
        </el-table-column>
        <el-table-column label="计划板数" min-width="130" align="right" header-align="right">
          <template #default="{ row }"><el-input-number v-model="row.boardCount" :min="0" :controls="false" @change="syncRowQrCount(row)" /></template>
        </el-table-column>
        <el-table-column label="计划件数" min-width="130" align="right" header-align="right">
          <template #default="{ row }"><el-input-number v-model="row.pieceCount" :min="0" :controls="false" @change="syncRowQrCount(row)" /></template>
        </el-table-column>
        <el-table-column label="预分配码数" min-width="140" align="right" header-align="right">
          <template #default="{ row }"><el-input-number v-model="row.qrCount" :min="1" :controls="false" /></template>
        </el-table-column>
        <el-table-column label="操作" min-width="96" align="center">
          <template #default="{ $index }">
            <el-button type="danger" link @click="removePreprintRow($index)">移除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div class="preprint-actions">
        <el-button @click="addPreprintRow">添加产品</el-button>
        <el-button type="primary" :disabled="!form.orderId" :loading="reserveLoading" @click="reserveLabels">预分配订单码</el-button>
      </div>
      <el-table :data="labelBatches" border stripe size="small" empty-text="暂无预打印批次">
        <el-table-column prop="batchNo" label="批次" min-width="170" />
        <el-table-column prop="productName" label="产品" min-width="150" />
        <el-table-column label="数量" width="150">
          <template #default="{ row }">{{ row.usedCount || 0 }}已用 / {{ row.recycledCount || 0 }}回收 / {{ row.reservedCount || 0 }}预留</template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">{{ labelBatchStatusText(row.status) }}</template>
        </el-table-column>
        <el-table-column label="打印时间" width="160">
          <template #default="{ row }">{{ formatDateTime(row.printedAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="150">
          <template #default="{ row }">
            <el-button type="primary" link @click="showLabelBatchCodes(row)">查看</el-button>
            <el-button type="primary" link :loading="printLoadingId === row.id" :disabled="['CLOSED', 'CANCELED'].includes(row.status)" @click="printLabelBatch(row)">
              {{ row.printedAt ? '重打' : '打印' }}
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-card class="form-card">
      <div class="section-title">确认实际产出</div>
      <el-alert
        v-if="appendOutputNotice"
        class="stage-alert"
        type="warning"
        :closable="false"
        show-icon
        :title="appendOutputNotice"
      />
      <el-table :data="finishRows" class="production-entry-table" border size="small" empty-text="暂无">
        <el-table-column label="产品" min-width="260">
          <template #default="{ row }">
            <el-select v-model="row.productId" filterable :placeholder="productPlaceholder" style="width: 100%" @change="syncRowQrCount(row)">
              <el-option v-for="item in availableProducts" :key="item.id" :label="item.productName" :value="item.id" />
            </el-select>
          </template>
        </el-table-column>
        <el-table-column label="生产日期" min-width="170">
          <template #default="{ row }">
            <el-date-picker v-model="row.productionDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
          </template>
        </el-table-column>
        <el-table-column label="实际板数" min-width="130" align="right" header-align="right">
          <template #default="{ row }"><el-input-number v-model="row.boardCount" :min="0" :controls="false" @change="syncRowQrCount(row)" /></template>
        </el-table-column>
        <el-table-column label="实际件数" min-width="130" align="right" header-align="right">
          <template #default="{ row }"><el-input-number v-model="row.pieceCount" :min="0" :controls="false" @change="syncRowQrCount(row)" /></template>
        </el-table-column>
        <el-table-column label="预计核销" min-width="180">
          <template #default="{ row }">{{ rowSplitDescription(row) }}</template>
        </el-table-column>
        <el-table-column label="操作" min-width="96" align="center">
          <template #default="{ $index }">
            <el-button type="danger" link @click="removeFinishRow($index)">移除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div class="preprint-actions">
        <el-button @click="addFinishRow">新增核销项</el-button>
        <el-button type="primary" :disabled="!canFinishProduction" :loading="finishLoading" @click="submitProductionFinish">
          确认生产结束
        </el-button>
      </div>
      <div class="split-preview">
        <span>核销规则：按同一产品的批次创建时间和批次内序号依次使用</span>
        <span>剩余订单码：确认生产结束后自动回收</span>
      </div>
    </el-card>

    <el-card class="table-card">
      <div class="table-toolbar">
        <div>
          <div class="section-title">{{ outputTableTitle }}</div>
          <div class="section-subtitle">确认生产结束后，系统按标签顺序核销实际使用码，剩余订单码自动回收。</div>
        </div>
      </div>

      <el-table :data="outputs" border stripe row-key="id" v-loading="loading" empty-text="暂无">
        <el-table-column type="expand">
          <template #default="{ row }">
            <el-table :data="row.codes || []" border size="small" empty-text="暂无">
              <el-table-column prop="palletCode" label="二维码" min-width="145" />
              <el-table-column label="数量" width="100">
                <template #default="{ row: codeRow }">{{ codeQuantityText(codeRow) }}</template>
              </el-table-column>
              <el-table-column label="状态" width="110">
                <template #default="{ row: codeRow }">{{ outputCodeStatusText(codeRow.status) }}</template>
              </el-table-column>
              <el-table-column label="入库时间" width="160">
                <template #default="{ row: codeRow }">{{ formatDateTime(codeRow.inboundAt) }}</template>
              </el-table-column>
            </el-table>
          </template>
        </el-table-column>
        <el-table-column prop="productName" label="产出产品" min-width="170" show-overflow-tooltip />
        <el-table-column prop="productionDate" label="生产日期" width="120" />
        <el-table-column label="产出数量" width="130">
          <template #default="{ row }">{{ outputQuantityText(row) }}</template>
        </el-table-column>
        <el-table-column prop="requiredQrCount" label="需二维码" width="95" />
        <el-table-column label="用码情况" width="110">
          <template #default="{ row }">{{ row.boundQrCount || 0 }}/{{ row.requiredQrCount || 0 }}</template>
        </el-table-column>
        <el-table-column label="入库进度" width="110">
          <template #default="{ row }">{{ row.inboundQrCount || 0 }}/{{ row.boundQrCount || 0 }}</template>
        </el-table-column>
        <el-table-column label="状态" width="110">
          <template #default="{ row }">{{ outputStatusText(row.status) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="280" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link :disabled="!row.boundQrCount" @click="printOutputCodes(row)">打印</el-button>
            <el-button type="primary" link :disabled="!row.boundQrCount" @click="showOutputCodes(row)">展示</el-button>
            <el-button type="primary" link :disabled="!row.boundQrCount" @click="goProcessTasks(row)">去确认入库</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="codesDialogVisible" title="本次产出二维码" width="620px">
      <el-table :data="dialogCodes" border stripe empty-text="暂无">
        <el-table-column prop="palletCode" label="二维码" min-width="150" />
        <el-table-column label="数量" width="100">
          <template #default="{ row }">{{ codeQuantityText(row) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="80">
          <template #default="{ row }">
            <el-button type="primary" link :loading="previewLoadingCode === row.palletCode" @click="previewQr(row)">查看</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-dialog>

    <el-dialog v-model="labelCodesDialogVisible" title="预打印订单码" width="min(1080px, 92vw)">
      <div class="label-dialog-header">
        <div>
          <div class="label-dialog-title">{{ selectedLabelBatch?.batchNo || '暂无批次' }}</div>
          <div class="label-dialog-meta">{{ selectedLabelBatch?.productName || '暂无产品' }} · {{ labelBatchStatusText(selectedLabelBatch?.status) }}</div>
        </div>
        <div class="label-dialog-tip">现场按序号从小到大使用</div>
      </div>
      <el-table :data="labelDialogCodes" border stripe empty-text="暂无二维码">
        <el-table-column prop="sequenceNo" label="序号" width="72" align="center" />
        <el-table-column prop="palletCode" label="二维码" min-width="150" />
        <el-table-column prop="productName" label="产品" min-width="150" show-overflow-tooltip />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">{{ labelCodeStatusText(row.status) }}</template>
        </el-table-column>
        <el-table-column label="使用时间" width="160">
          <template #default="{ row }">{{ formatDateTime(row.usedAt) }}</template>
        </el-table-column>
        <el-table-column label="回收时间" width="160">
          <template #default="{ row }">{{ formatDateTime(row.recycledAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="88" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link :loading="previewLoadingCode === `label-${row.id}`" @click="previewLabelQr(row)">查看</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-dialog>

    <el-dialog v-model="previewDialogVisible" title="二维码查看" width="380px" destroy-on-close @closed="closePreviewDialog">
      <div class="preview-wrapper" v-loading="!!previewLoadingCode">
        <div class="preview-code">{{ previewCode || '暂无' }}</div>
        <el-image
          v-if="previewImageUrl"
          class="preview-image"
          :src="previewImageUrl"
          fit="contain"
          :preview-src-list="[previewImageUrl]"
        />
        <el-empty v-else description="暂无二维码" :image-size="96" />
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getProductList } from '@/api/product'
import {
  finishProductionOrder,
  getProductionLabelQrCode,
  getProductionOrderDetail,
  listProductionOrderOptions,
  printProductionLabelBatch,
  reserveProductionLabels
} from '@/api/production'
import { getPalletQrCode } from '@/api/palletCode'
import { formatDateTime } from '@/utils/dateTime'

const route = useRoute()
const router = useRouter()

const form = ref({ orderId: undefined })
const orderOptions = ref([])
const productList = ref([])
const detail = ref(null)
const outputs = ref([])
const loading = ref(false)
const reserveLoading = ref(false)
const finishLoading = ref(false)
const printLoadingId = ref(null)
const preprintRows = ref([])
const finishRows = ref([])
const codesDialogVisible = ref(false)
const dialogCodes = ref([])
const labelCodesDialogVisible = ref(false)
const selectedLabelBatch = ref(null)
const labelDialogCodes = ref([])
const previewDialogVisible = ref(false)
const previewLoadingCode = ref('')
const previewCode = ref('')
const previewImageUrl = ref('')

const today = () => {
  const date = new Date()
  return `${date.getFullYear()}-${`${date.getMonth() + 1}`.padStart(2, '0')}-${`${date.getDate()}`.padStart(2, '0')}`
}

const selectedOrder = computed(() => detail.value?.baseInfo || orderOptions.value.find(item => item.id === form.value.orderId))
const labelBatches = computed(() => detail.value?.labelBatches || [])
const isSemiOrder = computed(() => selectedOrder.value?.orderType === 'SEMI')
const pageTitle = computed(() => isSemiOrder.value ? '产出半成品贴码' : '产出成品贴码')
const outputTableTitle = computed(() => isSemiOrder.value ? '实际产出半成品' : '实际产出成品')
const pageSubtitle = computed(() => isSemiOrder.value
  ? '半成品生产订单只允许选择半成品；确认生产结束后生成半成品入库任务。'
  : '成品生产订单只允许选择成品；确认生产结束后生成成品入库任务。')
const productPlaceholder = computed(() => isSemiOrder.value ? '请选择半成品' : '请选择成品')
const appendOutputNotice = computed(() => ['WAIT_INBOUND', 'PART_INBOUND'].includes(selectedOrder.value?.status)
  ? '当前订单已进入待入库阶段，实际产出已核销；如需变更需走异常处理。'
  : '')
const canFinishProduction = computed(() => !!form.value.orderId
  && labelBatches.value.some(batch => ['RESERVED', 'PRINTED'].includes(batch.status))
  && normalizedFinishItems().length > 0)
const availableProducts = computed(() => {
  const expectedStatus = isSemiOrder.value ? '半成品' : '成品'
  return productList.value.filter(item => item.status === expectedStatus)
})
const calculateSplit = row => {
  const product = productList.value.find(item => item.id === row?.productId)
  const perPallet = Number(product?.piecesPerPallet || 0)
  const boards = Number(row?.boardCount || 0)
  const pieces = Number(row?.pieceCount || 0)
  if (!perPallet) return { finalBoardCount: 0, finalPieces: 0, requiredQrCount: 0 }
  const finalBoardCount = boards + Math.floor(pieces / perPallet)
  const finalPieces = pieces % perPallet
  return {
    finalBoardCount,
    finalPieces,
    requiredQrCount: finalBoardCount + (finalPieces > 0 ? 1 : 0)
  }
}
const rowSplitDescription = row => {
  const split = calculateSplit(row)
  if (!split.requiredQrCount) return '暂无'
  const boardText = `${split.finalBoardCount}个整板码`
  const pieceText = split.finalPieces ? `，1个${split.finalPieces}件散件码` : ''
  return `${boardText}${pieceText}`
}

const orderTypeLabel = value => value === 'SEMI' ? '半成品生产订单' : (value === 'FINISH' ? '成品生产订单' : '暂无')
const compactText = value => (!value || value === '-' ? '暂无' : value)
const orderStatusLabel = value => ({
  ISSUED: '已下发',
  MATERIALING: '领料中',
  MATERIALED: '已领料',
  OUTPUT_BINDING: '产出中',
  PREPRINTED: '已预打印',
  WAIT_INBOUND: '待入库',
  PART_INBOUND: '部分入库',
  COMPLETED: '已完成',
  CANCELED: '已取消'
}[value] || value || '暂无')
const orderOptionLabel = item => `${item.orderNo} / ${orderTypeLabel(item.orderType)} / ${item.productionDate} / ${orderStatusLabel(item.status)}`
const outputQuantityText = row => {
  const boards = Number(row.boardCount || 0)
  const pieces = Number(row.pieceCount || 0)
  if (boards && pieces) return `${boards}板${pieces}件`
  if (boards) return `${boards}板`
  if (pieces) return `${pieces}件`
  return '暂无'
}
const codeQuantityText = row => {
  const boards = row.unit === '0' ? Number(row.quantity || 0) : 0
  const pieces = Number(row.pieces || (row.unit === '1' ? row.quantity : 0) || 0)
  if (boards && pieces) return `${boards}板${pieces}件`
  if (boards) return `${boards}板`
  if (pieces) return `${pieces}件`
  return '暂无'
}
const outputStatusText = status => ({
  DRAFT: '待用码',
  BOUND: '已用码',
  PART_INBOUND: '部分入库',
  INSTOCK: '已入库',
  CANCELED: '已取消'
}[status] || status || '暂无')
const outputCodeStatusText = status => ({
  BOUND: '已用码',
  PENDING_INBOUND: '待入库',
  INSTOCK: '已入库',
  CANCELED: '已取消'
}[status] || status || '暂无')
const labelBatchStatusText = status => ({
  RESERVED: '已预留',
  PRINTED: '已打印',
  CLOSED: '已失效',
  CANCELED: '已取消'
}[status] || status || '暂无')
const labelCodeStatusText = status => ({
  RESERVED: '已预留',
  USED: '已使用',
  RECYCLED: '已回收',
  CANCELED: '已取消'
}[status] || status || '暂无')

watch(availableProducts, list => {
  finishRows.value.forEach(row => {
    if (row.productId && !list.some(item => item.id === row.productId)) {
      row.productId = undefined
    }
  })
  preprintRows.value.forEach(row => {
    if (row.productId && !list.some(item => item.id === row.productId)) {
      row.productId = undefined
    }
  })
})

const loadOrders = async () => {
  const res = await listProductionOrderOptions({})
  orderOptions.value = res.data || []
}

const loadProducts = async () => {
  const res = await getProductList({})
  productList.value = Array.isArray(res.data) ? res.data : []
}

const loadDetail = async () => {
  if (!form.value.orderId) return
  loading.value = true
  try {
    const res = await getProductionOrderDetail(form.value.orderId)
    detail.value = res.data || null
    outputs.value = res.data?.outputs || []
    resetPreprintRowsFromPlan()
  } finally {
    loading.value = false
  }
}

const handleOrderChange = async () => {
  await loadDetail()
}

const normalizePlanItems = value => Array.isArray(value) ? value : []

const addPreprintRow = () => {
  preprintRows.value.push({ productId: undefined, boardCount: 0, pieceCount: 0, qrCount: 1 })
}

const removePreprintRow = index => {
  preprintRows.value.splice(index, 1)
}

const addFinishRow = () => {
  finishRows.value.push({
    productId: undefined,
    productionDate: selectedOrder.value?.productionDate || today(),
    boardCount: 0,
    pieceCount: 0
  })
}

const removeFinishRow = index => {
  finishRows.value.splice(index, 1)
  if (!finishRows.value.length) addFinishRow()
}

const syncRowQrCount = row => {
  if (!row) return
  const split = calculateSplit(row)
  if ('qrCount' in row) {
    row.qrCount = Math.max(1, split.requiredQrCount || Number(row.qrCount || 1))
  }
}

const normalizedFinishItems = () => finishRows.value
  .filter(item => item.productId && (Number(item.boardCount || 0) > 0 || Number(item.pieceCount || 0) > 0))
  .map(item => ({
    productId: item.productId,
    productionDate: item.productionDate || selectedOrder.value?.productionDate || today(),
    boardCount: Number(item.boardCount || 0),
    pieceCount: Number(item.pieceCount || 0)
  }))

const resetPreprintRowsFromPlan = () => {
  const plan = normalizePlanItems(detail.value?.baseInfo?.plannedOutputJson)
  preprintRows.value = plan
    .map(item => ({
      productId: item.productId,
      boardCount: Number(item.boardCount || 0),
      pieceCount: Number(item.pieceCount || 0),
      qrCount: Math.max(1, calculateSplit(item).requiredQrCount || Number(item.boardCount || 0) + (Number(item.pieceCount || 0) > 0 ? 1 : 0))
    }))
    .filter(item => item.productId)
  if (!preprintRows.value.length) {
    addPreprintRow()
  }
  finishRows.value = plan
    .map(item => ({
      productId: item.productId,
      productionDate: selectedOrder.value?.productionDate || today(),
      boardCount: Number(item.boardCount || 0),
      pieceCount: Number(item.pieceCount || 0)
    }))
    .filter(item => item.productId)
  if (!finishRows.value.length) {
    addFinishRow()
  }
}

const reserveLabels = async () => {
  const items = preprintRows.value
    .filter(item => item.productId && Number(item.qrCount || 0) > 0)
    .map(item => ({
      productId: item.productId,
      boardCount: Number(item.boardCount || 0),
      pieceCount: Number(item.pieceCount || 0),
      qrCount: Number(item.qrCount || 0)
    }))
  if (!items.length) {
    ElMessage.warning('请至少填写一项预打印产品和码数')
    return
  }
  reserveLoading.value = true
  try {
    await reserveProductionLabels(form.value.orderId, { items, remark: '生产订单预打印' })
    ElMessage.success('订单码已预分配')
    await loadDetail()
  } finally {
    reserveLoading.value = false
  }
}

const downloadBlob = (blob, filename) => {
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = filename
  document.body.appendChild(link)
  link.click()
  document.body.removeChild(link)
  URL.revokeObjectURL(url)
}

const printLabelBatch = async row => {
  if (row.printedAt) {
    await ElMessageBox.confirm('本次操作只会重打同一批标签，不会重新分配二维码。请确认旧纸质标签不会与新标签同时流入现场。', '重打标签风险提示', {
      type: 'warning',
      confirmButtonText: '确认重打',
      cancelButtonText: '取消'
    })
  }
  printLoadingId.value = row.id
  try {
    const response = await printProductionLabelBatch(row.id)
    downloadBlob(response.data, `production-label-${row.batchNo || row.id}.pdf`)
    ElMessage.success(row.printedAt ? '标签已重打' : '标签已生成')
    await loadDetail()
  } finally {
    printLoadingId.value = null
  }
}

const showLabelBatchCodes = row => {
  selectedLabelBatch.value = row
  labelDialogCodes.value = [...(row.codes || [])].sort((a, b) => Number(a.sequenceNo || 0) - Number(b.sequenceNo || 0))
  labelCodesDialogVisible.value = true
}

const submitProductionFinish = async () => {
  await ElMessageBox.confirm(
    '确认生产结束后，系统将按同一产品标签的批次和顺序核销前 N 张为已使用，剩余订单码自动回收，旧打印批次失效。是否继续？',
    '确认生产结束',
    { type: 'warning', confirmButtonText: '确认结束', cancelButtonText: '取消' }
  )
  finishLoading.value = true
  try {
    const res = await finishProductionOrder(form.value.orderId, {
      items: normalizedFinishItems(),
      remark: '确认生产结束'
    })
    detail.value = res.data || null
    outputs.value = res.data?.outputs || []
    ElMessage.success('生产结束已确认，未使用订单码已回收')
  } finally {
    finishLoading.value = false
  }
}

const outputCodes = row => (row.codes || []).map(item => item.palletCode).filter(Boolean)

const printOutputCodes = row => {
  const codes = outputCodes(row)
  if (!codes.length) {
    ElMessage.warning('当前产出还没有已使用订单码')
    return
  }
  router.push({
    path: '/pallet-code/fixed-product',
    query: {
      codes: codes.join(','),
      freeOnly: 'false'
    }
  })
}

const showOutputCodes = row => {
  dialogCodes.value = row.codes || []
  codesDialogVisible.value = true
}

const goProcessTasks = row => {
  const codes = outputCodes(row)
  if (!codes.length) {
    ElMessage.warning('当前产出还没有已使用订单码')
    return
  }
  router.push({
    path: isSemiOrder.value ? '/pallet-task/semi/in' : '/pallet-task/finish/in',
    query: {
      codes: codes.join(',')
    }
  })
}

const revokePreviewUrl = () => {
  if (previewImageUrl.value) {
    URL.revokeObjectURL(previewImageUrl.value)
    previewImageUrl.value = ''
  }
}

const previewQr = async row => {
  const code = row.palletCode
  if (!code) {
    ElMessage.warning('缺少二维码')
    return
  }
  previewDialogVisible.value = true
  previewLoadingCode.value = code
  previewCode.value = code
  revokePreviewUrl()
  try {
    const res = await getPalletQrCode(code)
    previewImageUrl.value = URL.createObjectURL(res.data)
  } catch (error) {
    previewDialogVisible.value = false
    ElMessage.error(error?.response?.data?.message || '二维码图片加载失败')
  } finally {
    previewLoadingCode.value = ''
  }
}
const previewLabelQr = async row => {
  if (!row?.id) {
    ElMessage.warning('缺少预打印订单码信息')
    return
  }
  const loadingKey = `label-${row.id}`
  previewDialogVisible.value = true
  previewLoadingCode.value = loadingKey
  previewCode.value = row.palletCode || `序号 ${row.sequenceNo}`
  revokePreviewUrl()
  try {
    const res = await getProductionLabelQrCode(row.id)
    previewImageUrl.value = URL.createObjectURL(res.data)
  } catch (error) {
    previewDialogVisible.value = false
    ElMessage.error(error?.response?.data?.message || '订单标签二维码加载失败')
  } finally {
    previewLoadingCode.value = ''
  }
}

const closePreviewDialog = () => {
  previewLoadingCode.value = ''
  previewCode.value = ''
  revokePreviewUrl()
}

onMounted(async () => {
  await Promise.all([loadOrders(), loadProducts()])
  if (route.query.orderId) {
    form.value.orderId = Number(route.query.orderId)
    await handleOrderChange()
  }
})

onBeforeUnmount(() => {
  revokePreviewUrl()
})
</script>

<style scoped>
.production-page {
  padding: 0;
}

.context-card,
.form-card,
.table-card {
  margin-bottom: 16px;
}

.context-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 14px;
}

.context-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 10px;
}

.context-item {
  min-width: 0;
  border: 1px solid var(--app-border-soft);
  border-radius: 6px;
  padding: 10px 12px;
  background: #fff;
}

.context-item.wide {
  grid-column: span 2;
}

.context-item span {
  display: block;
  color: var(--app-text-tertiary);
  font-size: 12px;
  margin-bottom: 4px;
}

.context-item strong {
  color: var(--app-text);
  font-size: 14px;
  word-break: break-word;
}

.section-title {
  color: var(--app-text);
  font-size: 16px;
  font-weight: 700;
}

.section-subtitle {
  margin-top: 4px;
  color: var(--app-text-tertiary);
  font-size: 13px;
}

.output-form {
  margin-top: 14px;
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  column-gap: 12px;
}

.stage-alert {
  margin-top: 12px;
}

.production-entry-table {
  width: 100%;
  margin-top: 12px;
}

.production-entry-table :deep(.el-table__cell) {
  vertical-align: middle;
}

.production-entry-table :deep(.cell) {
  min-width: 0;
  line-height: 1.4;
}

.production-entry-table :deep(.el-select),
.production-entry-table :deep(.el-date-editor),
.production-entry-table :deep(.el-input),
.production-entry-table :deep(.el-input-number) {
  width: 100%;
  min-width: 0;
}

.production-entry-table :deep(.el-input-number .el-input__inner) {
  text-align: right;
}

.production-entry-table :deep(.el-input__wrapper) {
  min-width: 0;
}

.production-entry-table :deep(.el-button) {
  white-space: nowrap;
}

.preprint-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin: 12px 0 16px;
}

.output-form :deep(.el-select),
.output-form :deep(.el-date-editor),
.output-form :deep(.el-input),
.output-form :deep(.el-input-number) {
  width: 100%;
}

.split-preview {
  display: flex;
  flex-wrap: wrap;
  gap: 18px;
  color: var(--app-text-secondary);
  font-size: 13px;
}

.table-toolbar {
  margin-bottom: 16px;
}

.preview-wrapper {
  min-height: 260px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 12px;
}

.label-dialog-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 12px;
}

.label-dialog-title {
  color: var(--app-text);
  font-size: 15px;
  font-weight: 700;
}

.label-dialog-meta,
.label-dialog-tip {
  color: var(--app-text-secondary);
  font-size: 13px;
}

.label-dialog-tip {
  white-space: nowrap;
}

.preview-code {
  color: var(--app-text);
  font-size: 14px;
  font-weight: 600;
  word-break: break-all;
}

.preview-image {
  width: 260px;
  height: 260px;
}

@media (max-width: 1200px) {
  .context-grid,
  .output-form {
    grid-template-columns: 1fr;
  }

  .context-item.wide {
    grid-column: auto;
  }
}
</style>
