<template>
  <div class="production-page">
    <el-card class="context-card">
      <div class="context-header">
        <div>
          <div class="section-title">半成品领用</div>
          <div class="section-subtitle">从待选库存加入本订单清单，确认后库存移出并释放原二维码。</div>
        </div>
        <el-select
          v-model="form.orderId"
          filterable
          placeholder="搜索成品生产订单"
          style="width: 360px"
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
        <div class="context-item"><span>订单号</span><strong>{{ orderContext?.orderNo || '暂无' }}</strong></div>
        <div class="context-item"><span>订单类型</span><strong>{{ orderTypeLabel(orderContext?.orderType) }}</strong></div>
        <div class="context-item"><span>生产日期</span><strong>{{ orderContext?.productionDate || '暂无' }}</strong></div>
        <div class="context-item"><span>状态</span><strong>{{ orderStatusLabel(orderContext?.status) }}</strong></div>
        <div class="context-item wide"><span>计划领用</span><strong>{{ compactText(orderContext?.plannedMaterialText) }}</strong></div>
        <div class="context-item wide"><span>已领用汇总</span><strong>{{ pickedSummary }}</strong></div>
      </div>
      <el-alert
        v-if="supplementPickNotice"
        class="status-alert"
        type="warning"
        :closable="false"
        show-icon
        :title="supplementPickNotice"
      />
    </el-card>

    <div class="pick-layout">
      <el-card class="pick-panel">
        <div class="panel-header">
          <div class="section-title">待选半成品库存</div>
        </div>

        <el-form :model="query" class="filter-form" label-width="76px">
          <el-form-item label="半成品">
            <el-select v-model="query.productId" clearable filterable placeholder="全部半成品">
              <el-option v-for="item in semiProducts" :key="item.id" :label="item.productName" :value="item.id" />
            </el-select>
          </el-form-item>
          <el-form-item label="生产日期">
            <el-date-picker v-model="query.productionDate" type="date" value-format="YYYY-MM-DD" placeholder="可为空" />
          </el-form-item>
          <el-form-item label="二维码">
            <el-input v-model="query.palletCode" clearable placeholder="二维码" />
          </el-form-item>
          <el-form-item label="库位">
            <el-select v-model="query.warehouseId" clearable filterable placeholder="全部库位">
              <el-option v-for="item in warehouseOptions" :key="item.warehouseId" :label="item.warehouseName" :value="item.warehouseId" />
            </el-select>
          </el-form-item>
          <el-form-item>
            <el-button type="primary" :disabled="!form.orderId" @click="handleSearch(1)">查询</el-button>
            <el-button @click="handleReset">重置</el-button>
          </el-form-item>
        </el-form>

        <el-table
          :data="candidateRows"
          border
          stripe
          v-loading="loading"
          height="420"
          empty-text="暂无"
          @selection-change="rows => selectedRows = rows"
        >
          <el-table-column type="selection" width="42" />
          <el-table-column prop="palletCode" label="二维码" min-width="135" />
          <el-table-column prop="productName" label="产品" min-width="145" show-overflow-tooltip />
          <el-table-column prop="productionDate" label="日期" width="105" />
          <el-table-column label="数量" width="100">
            <template #default="{ row }">{{ quantityText(row) }}</template>
          </el-table-column>
          <el-table-column prop="warehouseName" label="库位" min-width="115" show-overflow-tooltip />
        </el-table>

        <div class="candidate-actions">
          <el-button type="primary" :disabled="!selectedRows.length" @click="addSelectedRows">加入</el-button>
        </div>

        <div class="pagination-wrapper">
          <el-pagination
            small
            background
            layout="total, prev, pager, next"
            :total="total"
            :current-page="currentPage"
            :page-size="pageSize"
            @current-change="handleSearch"
          />
        </div>
      </el-card>

      <el-card class="pick-panel">
        <div class="panel-header">
          <div>
            <div class="section-title">本订单领用清单</div>
            <div class="section-subtitle">{{ pendingRows.length }} 项待提交，{{ pickedRows.length }} 项已领用</div>
          </div>
        </div>

        <div class="sub-table-header">
          <span>已领用</span>
          <el-tag size="small" type="success">{{ pickedRows.length }} 项</el-tag>
        </div>
        <el-table :data="pickedRows" border stripe height="238" empty-text="暂无" v-loading="detailLoading">
          <el-table-column prop="palletCode" label="二维码" min-width="135" />
          <el-table-column prop="productName" label="产品" min-width="145" show-overflow-tooltip />
          <el-table-column label="数量" width="100">
            <template #default="{ row }">{{ quantityText(row) }}</template>
          </el-table-column>
          <el-table-column prop="warehouseName" label="库位" min-width="110" show-overflow-tooltip />
        </el-table>

        <div class="sub-table-header pending-header">
          <span>待确认领用</span>
          <el-tag size="small" type="warning">{{ pendingRows.length }} 项</el-tag>
        </div>
        <el-table :data="pendingRows" border stripe height="238" empty-text="暂无">
          <el-table-column prop="palletCode" label="二维码" min-width="135" />
          <el-table-column prop="productName" label="产品" min-width="145" show-overflow-tooltip />
          <el-table-column label="数量" width="100">
            <template #default="{ row }">{{ quantityText(row) }}</template>
          </el-table-column>
          <el-table-column prop="warehouseName" label="库位" min-width="110" show-overflow-tooltip />
          <el-table-column label="操作" width="80">
            <template #default="{ row }">
              <el-button type="danger" link @click="removePending(row)">移除</el-button>
            </template>
          </el-table-column>
        </el-table>
        <div class="pending-actions">
          <el-button type="primary" :disabled="!pendingRows.length" :loading="pickLoading" @click="confirmPick">
            {{ confirmPickButtonText }}
          </el-button>
        </div>
      </el-card>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getProductList } from '@/api/product'
import { getAllWarehouseCapacity } from '@/api/warehouseinfo'
import {
  getProductionOrderDetail,
  listProductionOrderOptions,
  pageMaterialCandidates,
  pickProductionMaterials
} from '@/api/production'

const route = useRoute()

const form = ref({ orderId: undefined })
const query = ref({ productId: undefined, productionDate: '', palletCode: '', warehouseId: undefined })
const orderOptions = ref([])
const productList = ref([])
const warehouseOptions = ref([])
const candidateRows = ref([])
const pickedRows = ref([])
const pendingRows = ref([])
const selectedRows = ref([])
const orderDetail = ref(null)
const loading = ref(false)
const detailLoading = ref(false)
const pickLoading = ref(false)
const total = ref(0)
const currentPage = ref(1)
const pageSize = ref(10)

const semiProducts = computed(() => productList.value.filter(item => item.status === '半成品'))
const orderContext = computed(() => orderDetail.value?.baseInfo || orderOptions.value.find(item => item.id === form.value.orderId))
const pickedSummary = computed(() => summarizeRows(pickedRows.value))
const isSupplementPick = computed(() => ['MATERIALED', 'OUTPUT_BINDING', 'WAIT_INBOUND', 'PART_INBOUND'].includes(orderContext.value?.status))
const supplementPickNotice = computed(() => isSupplementPick.value ? '当前订单已领料，可继续补充领用半成品。' : '')
const confirmPickButtonText = computed(() => isSupplementPick.value ? '确认补充领用' : '确认领用')

const orderTypeLabel = value => value === 'FINISH' ? '成品生产订单' : (value === 'SEMI' ? '半成品生产订单' : '暂无')
const orderStatusLabel = value => ({
  ISSUED: '已下发',
  MATERIALING: '领料中',
  MATERIALED: '已领料',
  OUTPUT_BINDING: '产出中',
  WAIT_INBOUND: '待入库',
  PART_INBOUND: '部分入库',
  COMPLETED: '已完成',
  CANCELED: '已取消'
}[value] || value || '暂无')
const compactText = value => (!value || value === '-' ? '暂无' : value)
const orderOptionLabel = item => `${item.orderNo} / ${orderTypeLabel(item.orderType)} / ${item.productionDate} / ${orderStatusLabel(item.status)}`
const quantityText = row => {
  if (row.quantityText && !row.unit) return row.quantityText
  const boards = row.unit === '0' ? Number(row.quantity || 0) : 0
  const pieces = Number(row.pieces || (row.unit === '1' ? row.quantity : 0) || 0)
  if (boards && pieces) return `${boards}板${pieces}件`
  if (boards) return `${boards}板`
  if (pieces) return `${pieces}件`
  return row.quantityText || '暂无'
}

const summarizeRows = rows => {
  if (!rows.length) return '暂无'
  const totals = new Map()
  rows.forEach(row => {
    const key = row.productName || '产品'
    const current = totals.get(key) || { boards: 0, pieces: 0 }
    if (row.unit === '0') current.boards += Number(row.quantity || 0)
    else current.pieces += Number(row.pieces || row.quantity || 0)
    totals.set(key, current)
  })
  return [...totals.entries()].map(([name, value]) => {
    const boardText = value.boards ? `${value.boards}板` : ''
    const pieceText = value.pieces ? `${value.pieces}件` : ''
    return `${name} ${boardText}${pieceText}`.trim()
  }).join('；')
}

const loadOrders = async () => {
  const res = await listProductionOrderOptions({ orderType: 'FINISH' })
  orderOptions.value = res.data || []
}

const loadProducts = async () => {
  const res = await getProductList({})
  productList.value = Array.isArray(res.data) ? res.data : []
}

const loadWarehouses = async () => {
  const res = await getAllWarehouseCapacity()
  warehouseOptions.value = Array.isArray(res.data) ? res.data : []
}

const loadPicked = async () => {
  if (!form.value.orderId) {
    pickedRows.value = []
    orderDetail.value = null
    return
  }
  detailLoading.value = true
  try {
    const res = await getProductionOrderDetail(form.value.orderId)
    orderDetail.value = res.data || null
    pickedRows.value = res.data?.materials || []
  } finally {
    detailLoading.value = false
  }
}

const buildQuery = () => ({
  productId: query.value.productId,
  productionDate: query.value.productionDate || undefined,
  palletCode: query.value.palletCode || undefined,
  warehouseId: query.value.warehouseId,
  page: currentPage.value,
  size: pageSize.value
})

const handleSearch = async (page = currentPage.value) => {
  if (!form.value.orderId) {
    ElMessage.warning('请先选择成品生产订单')
    return
  }
  currentPage.value = page
  loading.value = true
  try {
    const res = await pageMaterialCandidates(form.value.orderId, buildQuery())
    const pendingIds = new Set(pendingRows.value.map(item => item.palletCodeId))
    candidateRows.value = (res.data?.records || []).filter(item => !pendingIds.has(item.palletCodeId))
    total.value = res.data?.total || 0
    selectedRows.value = []
  } finally {
    loading.value = false
  }
}

const handleReset = () => {
  query.value = { productId: undefined, productionDate: '', palletCode: '', warehouseId: undefined }
  if (form.value.orderId) handleSearch(1)
}

const handleOrderChange = async () => {
  pendingRows.value = []
  await loadPicked()
  await handleSearch(1)
}

const addSelectedRows = () => {
  const existingIds = new Set(pendingRows.value.map(item => item.palletCodeId))
  const additions = selectedRows.value.filter(item => !existingIds.has(item.palletCodeId))
  pendingRows.value = [...pendingRows.value, ...additions]
  const addedIds = new Set(additions.map(item => item.palletCodeId))
  candidateRows.value = candidateRows.value.filter(item => !addedIds.has(item.palletCodeId))
  selectedRows.value = []
}

const removePending = row => {
  pendingRows.value = pendingRows.value.filter(item => item.palletCodeId !== row.palletCodeId)
  handleSearch(currentPage.value)
}

const confirmPick = async () => {
  if (!form.value.orderId || !pendingRows.value.length) return
  const confirmed = await ElMessageBox.confirm(
    '确认后，所选半成品将从仓库库存中移出并关联到生产订单，原二维码会释放为可复用状态。当前系统不支持撤销本次领料，请确认信息无误后再继续。',
    isSupplementPick.value ? '确认补充领用' : '确认生产订单领用',
    {
      type: 'warning',
      confirmButtonText: confirmPickButtonText.value,
      cancelButtonText: '取消'
    }
  ).catch(() => false)
  if (!confirmed) return
  pickLoading.value = true
  try {
    await pickProductionMaterials(form.value.orderId, {
      palletCodeIds: pendingRows.value.map(item => item.palletCodeId),
      remark: '生产订单领用'
    })
    ElMessage.success(isSupplementPick.value ? '补充领用成功' : '领用成功')
    pendingRows.value = []
    await loadPicked()
    await handleSearch(1)
  } finally {
    pickLoading.value = false
  }
}

onMounted(async () => {
  await Promise.all([loadOrders(), loadProducts(), loadWarehouses()])
  if (route.query.orderId) {
    form.value.orderId = Number(route.query.orderId)
    await handleOrderChange()
  }
})
</script>

<style scoped>
.production-page {
  padding: 0;
}

.context-card,
.pick-panel {
  margin-bottom: 16px;
}

.context-header,
.panel-header {
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

.pick-layout {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 1fr);
  gap: 16px;
}

.filter-form {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  column-gap: 12px;
}

.filter-form :deep(.el-select),
.filter-form :deep(.el-date-editor),
.filter-form :deep(.el-input) {
  width: 100%;
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

.pagination-wrapper {
  display: flex;
  justify-content: flex-end;
  margin-top: 14px;
}

.candidate-actions {
  display: flex;
  justify-content: flex-end;
  margin-top: 12px;
}

.status-alert {
  margin-top: 12px;
}

.sub-table-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin: 6px 0 8px;
  color: var(--app-text);
  font-size: 14px;
  font-weight: 700;
}

.pending-header {
  margin-top: 16px;
}

.pending-actions {
  display: flex;
  justify-content: flex-end;
  margin-top: 12px;
}

@media (max-width: 1200px) {
  .pick-layout,
  .context-grid {
    grid-template-columns: 1fr;
  }

  .context-item.wide {
    grid-column: auto;
  }
}
</style>
