<template>
  <div class="production-page">
    <el-card class="search-card">
      <el-form :model="searchForm" inline>
        <el-form-item label="订单号">
          <el-input v-model="searchForm.orderNo" clearable placeholder="请输入订单号" style="width: 190px" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="searchForm.status" clearable placeholder="全部状态" style="width: 160px">
            <el-option v-for="item in activeStatusOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="生产日期">
          <el-date-picker
            v-model="searchForm.dateRange"
            type="daterange"
            value-format="YYYY-MM-DD"
            start-placeholder="开始日期"
            end-placeholder="结束日期"
            style="width: 260px"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch(1)">查询</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card class="table-card">
      <div class="table-toolbar">
        <div>
          <div class="section-title">生产订单</div>
          <div class="section-subtitle">按成品生产和半成品生产分流处理领用、产出贴码与入库进度。</div>
        </div>
        <el-button type="primary" @click="openCreateDialog">新建订单</el-button>
      </div>

      <el-tabs v-model="activeOrderType" @tab-change="handleTabChange">
        <el-tab-pane label="成品生产订单" name="FINISH">
          <el-table :data="rows" border stripe v-loading="loading" style="width: 100%" empty-text="暂无">
            <el-table-column prop="orderNo" label="订单号" min-width="140" />
            <el-table-column label="生产信息" width="132">
              <template #default="{ row }">
                <div>{{ row.productionDate }}</div>
                <el-tag size="small" :type="orderStatusType(row.status)">{{ orderStatusLabel(row.status) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="领用" min-width="150" show-overflow-tooltip>
              <template #default="{ row }">
                <div class="plan-cell">
                  <span class="plan-label">计划：</span>
                  <span class="plan-values">
                    <span v-for="item in planItems(row.plannedMaterialText)" :key="item" class="plan-value">{{ item }}</span>
                  </span>
                </div>
                <div class="cell-muted">已领：{{ row.actualMaterialCount || 0 }}项</div>
              </template>
            </el-table-column>
            <el-table-column label="产出入库" min-width="150" show-overflow-tooltip>
              <template #default="{ row }">
                <div class="plan-cell">
                  <span class="plan-label">计划：</span>
                  <span class="plan-values">
                    <span v-for="item in planItems(row.plannedOutputText)" :key="item" class="plan-value">{{ item }}</span>
                  </span>
                </div>
                <div class="cell-muted">产出：{{ row.outputCount || 0 }}项，{{ row.inboundProgress }}</div>
              </template>
            </el-table-column>
            <el-table-column prop="createdByName" label="创建人" width="90" />
            <el-table-column label="操作" width="245">
              <template #default="{ row }">
                <el-button v-if="showPrimaryAction(row)" type="primary" size="small" @click="handlePrimaryAction(row)">{{ primaryActionText(row) }}</el-button>
                <el-button type="primary" link @click="openDetail(row)">详情</el-button>
                <el-button v-if="!['ISSUED', 'COMPLETED', 'CANCELED'].includes(row.status)" type="primary" link @click="goMaterialPick(row)">领用</el-button>
                <el-button v-if="!['MATERIALED', 'OUTPUT_BINDING', 'COMPLETED', 'CANCELED'].includes(row.status)" type="primary" link @click="goOutputBind(row)">贴码</el-button>
                <el-button v-if="row.status === 'ISSUED'" type="danger" link @click="deleteOrder(row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>

        <el-tab-pane label="半成品生产订单" name="SEMI">
          <el-table :data="rows" border stripe v-loading="loading" style="width: 100%" empty-text="暂无">
            <el-table-column prop="orderNo" label="订单号" min-width="140" />
            <el-table-column label="生产信息" width="132">
              <template #default="{ row }">
                <div>{{ row.productionDate }}</div>
                <el-tag size="small" :type="orderStatusType(row.status)">{{ orderStatusLabel(row.status) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="产出入库" min-width="190" show-overflow-tooltip>
              <template #default="{ row }">
                <div class="plan-cell">
                  <span class="plan-label">计划：</span>
                  <span class="plan-values">
                    <span v-for="item in planItems(row.plannedOutputText)" :key="item" class="plan-value">{{ item }}</span>
                  </span>
                </div>
                <div class="cell-muted">产出：{{ row.outputCount || 0 }}项，已绑 {{ row.boundQrCount || 0 }} 码，{{ row.inboundProgress }}</div>
              </template>
            </el-table-column>
            <el-table-column prop="createdByName" label="创建人" width="90" />
            <el-table-column label="操作" width="210">
              <template #default="{ row }">
                <el-button v-if="showPrimaryAction(row)" type="primary" size="small" @click="handlePrimaryAction(row)">{{ primaryActionText(row) }}</el-button>
                <el-button type="primary" link @click="openDetail(row)">详情</el-button>
                <el-button v-if="!['ISSUED', 'OUTPUT_BINDING', 'COMPLETED', 'CANCELED'].includes(row.status)" type="primary" link @click="goOutputBind(row)">贴码</el-button>
                <el-button v-if="row.status === 'ISSUED'" type="danger" link @click="deleteOrder(row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>
      </el-tabs>

      <div class="pagination-wrapper">
        <el-pagination
          background
          layout="total, sizes, prev, pager, next"
          :total="total"
          :current-page="currentPage"
          :page-size="pageSize"
          :page-sizes="[10, 20, 50]"
          @size-change="handleSizeChange"
          @current-change="handleSearch"
        />
      </div>
    </el-card>

    <el-dialog v-model="createDialogVisible" title="新建生产订单" width="560px" destroy-on-close>
      <el-form :model="createForm" label-width="110px">
        <el-form-item label="订单类型" required>
          <el-radio-group v-model="createForm.orderType">
            <el-radio-button v-for="item in createTypeOptions" :key="item.value" :label="item.value">
              {{ item.label }}
            </el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="生产日期" required>
          <el-date-picker v-model="createForm.productionDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
        </el-form-item>
        <el-form-item v-if="createForm.orderType === 'FINISH'" label="计划半成品">
          <div class="plan-lines">
            <div v-for="(item, index) in createForm.plannedMaterialRows" :key="index" class="plan-line">
              <el-cascader v-model="item.productPath" :options="semiProductOptions" clearable filterable placeholder="选择半成品" />
              <el-input-number v-model="item.boardCount" :min="0" :controls="false" />
              <span>板</span>
              <el-input-number v-model="item.pieceCount" :min="0" :controls="false" />
              <span>件</span>
              <el-button text type="danger" @click="removePlanRow('plannedMaterialRows', index)">删除</el-button>
            </div>
            <el-button @click="addPlanRow('plannedMaterialRows')">添加计划领用</el-button>
          </div>
        </el-form-item>
        <el-form-item label="预计产量">
          <div class="plan-lines">
            <div v-for="(item, index) in createForm.plannedOutputRows" :key="index" class="plan-line">
              <el-cascader v-model="item.productPath" :options="outputProductOptions" clearable filterable :placeholder="createForm.orderType === 'SEMI' ? '选择半成品' : '选择成品'" />
              <el-input-number v-model="item.boardCount" :min="0" :controls="false" />
              <span>板</span>
              <el-input-number v-model="item.pieceCount" :min="0" :controls="false" />
              <span>件</span>
              <el-button text type="danger" @click="removePlanRow('plannedOutputRows', index)">删除</el-button>
            </div>
            <el-button @click="addPlanRow('plannedOutputRows')">添加计划产出</el-button>
          </div>
        </el-form-item>
        <el-form-item label="班组/备注">
          <el-input v-model="createForm.teamName" placeholder="班组、工序或负责人，可为空" />
        </el-form-item>
        <el-form-item label="订单备注">
          <el-input v-model="createForm.remark" type="textarea" :rows="2" placeholder="可为空" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="createLoading" @click="submitCreate">确认创建</el-button>
      </template>
    </el-dialog>

    <el-drawer v-model="detailVisible" :title="detailTitle" size="58%" destroy-on-close>
      <div v-loading="detailLoading" class="detail-drawer">
        <el-steps v-if="detail.baseInfo" :active="currentStepIndex" finish-status="success" align-center class="order-steps">
          <el-step v-for="item in detailSteps" :key="item" :title="item" />
        </el-steps>

        <div v-if="detail.baseInfo" class="info-panel">
          <div class="section-title">订单基础信息</div>
          <div class="info-grid">
            <div class="info-item"><span>订单号</span><strong>{{ detail.baseInfo.orderNo }}</strong></div>
            <div class="info-item"><span>订单类型</span><strong>{{ orderTypeLabel(detail.baseInfo.orderType) }}</strong></div>
            <div class="info-item"><span>状态</span><strong>{{ orderStatusLabel(detail.baseInfo.status) }}</strong></div>
            <div class="info-item"><span>生产日期</span><strong>{{ detail.baseInfo.productionDate }}</strong></div>
            <div class="info-item"><span>创建人</span><strong>{{ detail.baseInfo.createdByName || '暂无' }}</strong></div>
            <div class="info-item"><span>创建时间</span><strong>{{ formatDateTime(detail.baseInfo.createdAt) }}</strong></div>
            <div class="info-item wide"><span>备注</span><strong>{{ detail.baseInfo.remark || '暂无' }}</strong></div>
          </div>
        </div>

        <div class="detail-section">
          <div class="section-title">预打印批次</div>
          <el-table :data="detail.labelBatches || []" border stripe empty-text="暂无">
            <el-table-column prop="batchNo" label="批次号" min-width="170" show-overflow-tooltip />
            <el-table-column prop="productName" label="产品" min-width="150" show-overflow-tooltip />
            <el-table-column prop="reservedCount" label="预打印数量" width="105" />
            <el-table-column prop="usedCount" label="已使用" width="82" />
            <el-table-column prop="recycledCount" label="已回收" width="82" />
            <el-table-column label="待入库" width="82">
              <template #default="{ row }">{{ labelBatchPendingCount(row) }}</template>
            </el-table-column>
            <el-table-column label="已入库" width="82">
              <template #default="{ row }">{{ labelBatchInboundCount(row) }}</template>
            </el-table-column>
            <el-table-column label="批次状态" width="100">
              <template #default="{ row }">{{ labelBatchStatusText(row.status) }}</template>
            </el-table-column>
            <el-table-column label="打印时间" width="160">
              <template #default="{ row }">{{ formatDateTime(row.printedAt) }}</template>
            </el-table-column>
          </el-table>
        </div>

        <div v-if="detail.baseInfo?.orderType === 'FINISH'" class="detail-section compare-section">
          <div class="compare-panel">
            <div class="section-title">计划领用半成品</div>
            <div class="plan-summary">
              <span v-for="item in planItems(detail.baseInfo.plannedMaterialText)" :key="item" class="plan-summary-item">{{ item }}</span>
            </div>
          </div>
          <div class="compare-panel">
            <div class="section-title">实际领用半成品</div>
            <el-table :data="detail.materials || []" border stripe empty-text="暂无">
              <el-table-column prop="palletCode" label="二维码" min-width="130" />
              <el-table-column prop="productName" label="产品" min-width="140" show-overflow-tooltip />
              <el-table-column label="数量" width="100">
                <template #default="{ row }">{{ materialQuantityText(row) }}</template>
              </el-table-column>
              <el-table-column prop="warehouseName" label="来源库位" min-width="110" show-overflow-tooltip />
            </el-table>
          </div>
        </div>

        <div class="detail-section compare-section">
          <div class="compare-panel">
            <div class="section-title">计划产出</div>
            <div class="plan-summary">
              <span v-for="item in planItems(detail.baseInfo?.plannedOutputText)" :key="item" class="plan-summary-item">{{ item }}</span>
            </div>
          </div>
          <div class="compare-panel">
            <div class="section-title">{{ detail.baseInfo?.orderType === 'SEMI' ? '实际产出半成品' : '实际产出成品' }}</div>
            <el-table :data="detail.outputs || []" border stripe row-key="id" empty-text="暂无">
              <el-table-column type="expand">
                <template #default="{ row }">
                  <el-table :data="row.codes || []" border size="small" empty-text="暂无">
                    <el-table-column prop="palletCode" label="二维码" min-width="140" />
                    <el-table-column label="数量" width="100">
                      <template #default="{ row: codeRow }">{{ materialQuantityText(codeRow) }}</template>
                    </el-table-column>
                    <el-table-column label="状态" width="110">
                      <template #default="{ row: codeRow }">{{ outputCodeStatusText(codeRow.status) }}</template>
                    </el-table-column>
                  </el-table>
                </template>
              </el-table-column>
              <el-table-column prop="productName" label="产出产品" min-width="150" show-overflow-tooltip />
              <el-table-column label="产出数量" width="120">
                <template #default="{ row }">{{ outputQuantityText(row) }}</template>
              </el-table-column>
              <el-table-column prop="boundQrCount" label="已用码" width="82" />
              <el-table-column prop="inboundQrCount" label="已入库" width="82" />
            </el-table>
          </div>
        </div>
      </div>
    </el-drawer>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getProductList } from '@/api/product'
import {
  createProductionOrder,
  deleteProductionOrder,
  getProductionOrderDetail,
  pageProductionOrders
} from '@/api/production'
import { formatDateTime } from '@/utils/dateTime'

const router = useRouter()
const route = useRoute()

const createTypeOptions = [
  { value: 'FINISH', label: '成品生产订单' },
  { value: 'SEMI', label: '半成品生产订单' }
]

const orderStatusOptions = [
  { value: 'ISSUED', label: '已下发', type: 'primary', types: ['FINISH', 'SEMI'] },
  { value: 'MATERIALING', label: '领料中', type: 'warning', types: ['FINISH'] },
  { value: 'MATERIALED', label: '已领料', type: 'success', types: ['FINISH'] },
  { value: 'OUTPUT_BINDING', label: '产出中', type: 'warning', types: ['FINISH', 'SEMI'] },
  { value: 'PREPRINTED', label: '已预打印', type: 'warning', types: ['FINISH', 'SEMI'] },
  { value: 'WAIT_INBOUND', label: '待入库', type: 'primary', types: ['FINISH', 'SEMI'] },
  { value: 'PART_INBOUND', label: '部分入库', type: 'warning', types: ['FINISH', 'SEMI'] },
  { value: 'COMPLETED', label: '已完成', type: 'success', types: ['FINISH', 'SEMI'] },
  { value: 'CANCELED', label: '已取消', type: 'info', types: ['FINISH', 'SEMI'] }
]

const activeOrderType = ref('FINISH')
const searchForm = ref({
  orderNo: Array.isArray(route.query.orderNo) ? (route.query.orderNo[0] || '') : (route.query.orderNo || ''),
  status: '',
  dateRange: []
})

const createForm = ref({})
const rows = ref([])
const productList = ref([])
const total = ref(0)
const currentPage = ref(1)
const pageSize = ref(10)
const loading = ref(false)
const createLoading = ref(false)
const createDialogVisible = ref(false)
const detailVisible = ref(false)
const detailLoading = ref(false)
const detail = reactive({
  baseInfo: null,
  materials: [],
  outputs: [],
  outputCodes: [],
  labelBatches: []
})

const activeStatusOptions = computed(() => orderStatusOptions.filter(item => item.types.includes(activeOrderType.value)))
const semiProductOptions = computed(() => productCascaderOptions('半成品'))
const finishProductOptions = computed(() => productCascaderOptions('成品'))
const outputProductOptions = computed(() => createForm.value.orderType === 'SEMI' ? semiProductOptions.value : finishProductOptions.value)
const detailSteps = computed(() => detail.baseInfo?.orderType === 'SEMI'
  ? ['已下发', '产出中', '待入库', '已完成']
  : ['已下发', '已领料', '产出中', '待入库', '已完成'])
const currentStepIndex = computed(() => {
  const status = detail.baseInfo?.status
  if (detail.baseInfo?.orderType === 'SEMI') {
    return { ISSUED: 0, OUTPUT_BINDING: 1, WAIT_INBOUND: 2, PART_INBOUND: 2, COMPLETED: 3 }[status] ?? 0
  }
  return { ISSUED: 0, MATERIALING: 1, MATERIALED: 1, OUTPUT_BINDING: 2, WAIT_INBOUND: 3, PART_INBOUND: 3, COMPLETED: 4 }[status] ?? 0
})
const detailTitle = computed(() => {
  const typeText = orderTypeLabel(detail.baseInfo?.orderType)
  return detail.baseInfo ? `${typeText}详情` : '生产订单详情'
})

const today = () => {
  const date = new Date()
  return `${date.getFullYear()}-${`${date.getMonth() + 1}`.padStart(2, '0')}-${`${date.getDate()}`.padStart(2, '0')}`
}

const orderTypeLabel = value => value === 'SEMI' ? '半成品生产订单' : (value === 'FINISH' ? '成品生产订单' : '-')
const orderStatusLabel = value => orderStatusOptions.find(item => item.value === value)?.label || value || '-'
const orderStatusType = value => orderStatusOptions.find(item => item.value === value)?.type || ''
const compactText = value => (!value || value === '-' ? '暂无' : value)
const planItems = value => {
  const text = compactText(value)
  return text === '暂无' ? [text] : text.split(/[；;]+/).map(item => item.trim()).filter(Boolean)
}
const materialQuantityText = row => {
  const boards = row.unit === '0' ? Number(row.quantity || 0) : 0
  const pieces = Number(row.pieces || (row.unit === '1' ? row.quantity : 0) || 0)
  if (boards && pieces) return `${boards}板${pieces}件`
  if (boards) return `${boards}板`
  if (pieces) return `${pieces}件`
  return '暂无'
}
const outputQuantityText = row => {
  const boards = Number(row.boardCount || 0)
  const pieces = Number(row.pieceCount || 0)
  if (boards && pieces) return `${boards}板${pieces}件`
  if (boards) return `${boards}板`
  if (pieces) return `${pieces}件`
  return '暂无'
}
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
const labelBatchOutputCodes = batch => {
  const labelCodeIds = new Set((batch.codes || []).map(item => item.id))
  return (detail.outputCodes || []).filter(item => labelCodeIds.has(item.labelCodeId))
}
const labelBatchPendingCount = batch => labelBatchOutputCodes(batch)
  .filter(item => !['INSTOCK', 'CANCELED'].includes(item.status))
  .length
const labelBatchInboundCount = batch => labelBatchOutputCodes(batch)
  .filter(item => item.status === 'INSTOCK')
  .length

const primaryActionText = row => {
  if (row.orderType === 'FINISH' && row.status === 'ISSUED') return '去领用'
  if (['MATERIALED', 'OUTPUT_BINDING', 'PREPRINTED'].includes(row.status)) return '去贴码'
  if (row.orderType === 'SEMI' && ['ISSUED', 'OUTPUT_BINDING', 'PREPRINTED'].includes(row.status)) return '去贴码'
  if (['WAIT_INBOUND', 'PART_INBOUND'].includes(row.status)) return '查看入库'
  return '查看详情'
}
const showPrimaryAction = row => !['COMPLETED', 'CANCELED'].includes(row.status)

const productCascaderOptions = status => {
  const groups = new Map()
  productList.value
    .filter(item => item.status === status)
    .forEach(item => {
      const type = item.productType || '未分类'
      const children = groups.get(type) || []
      children.push({ value: item.id, label: item.productName })
      groups.set(type, children)
    })
  return [...groups.entries()].map(([type, children]) => ({ value: type, label: type, children }))
}

const createPlanRow = () => ({ productPath: [], boardCount: 0, pieceCount: 0 })

const addPlanRow = field => {
  createForm.value[field].push(createPlanRow())
}

const removePlanRow = (field, index) => {
  createForm.value[field].splice(index, 1)
}

const productNameById = id => productList.value.find(item => item.id === id)?.productName || '产品'

const planRowsToJson = rows => {
  const result = (rows || [])
    .map(item => {
      const productId = Array.isArray(item.productPath) ? item.productPath[item.productPath.length - 1] : undefined
      return {
        productId,
        productName: productId ? productNameById(productId) : '',
        boardCount: Number(item.boardCount || 0),
        pieceCount: Number(item.pieceCount || 0)
      }
    })
    .filter(item => item.productId && (item.boardCount > 0 || item.pieceCount > 0))
  return result.length ? result : null
}

const buildQuery = () => {
  const [startDate, endDate] = searchForm.value.dateRange || []
  return {
    orderNo: searchForm.value.orderNo || undefined,
    orderType: activeOrderType.value,
    status: searchForm.value.status || undefined,
    startDate,
    endDate,
    page: currentPage.value,
    size: pageSize.value
  }
}

const handleSearch = async (page = currentPage.value) => {
  currentPage.value = page
  loading.value = true
  try {
    const res = await pageProductionOrders(buildQuery())
    rows.value = res.data?.records || []
    total.value = res.data?.total || 0
  } finally {
    loading.value = false
  }
}

const handleTabChange = () => {
  searchForm.value.status = ''
  handleSearch(1)
}

const handleReset = () => {
  searchForm.value = { orderNo: '', status: '', dateRange: [] }
  handleSearch(1)
}

const handleSizeChange = size => {
  pageSize.value = size
  handleSearch(1)
}

const openCreateDialog = () => {
  createForm.value = {
    orderType: activeOrderType.value,
    productionDate: today(),
    plannedMaterialRows: [createPlanRow()],
    plannedOutputRows: [createPlanRow()],
    teamName: '',
    remark: ''
  }
  createDialogVisible.value = true
}

const submitCreate = async () => {
  if (!createForm.value.orderType || !createForm.value.productionDate) {
    ElMessage.warning('请选择订单类型和生产日期')
    return
  }
  createLoading.value = true
  try {
    await createProductionOrder({
      orderType: createForm.value.orderType,
      productionDate: createForm.value.productionDate,
      plannedMaterialJson: createForm.value.orderType === 'FINISH' ? planRowsToJson(createForm.value.plannedMaterialRows) : null,
      plannedOutputJson: planRowsToJson(createForm.value.plannedOutputRows),
      teamName: createForm.value.teamName || undefined,
      remark: createForm.value.remark || undefined
    })
    ElMessage.success('生产订单已创建')
    createDialogVisible.value = false
    activeOrderType.value = createForm.value.orderType
    await handleSearch(1)
  } finally {
    createLoading.value = false
  }
}

const deleteOrder = async row => {
  await ElMessageBox.confirm(`确认删除生产订单“${row.orderNo}”吗？`, '删除生产订单', {
    type: 'warning',
    confirmButtonText: '确认删除',
    cancelButtonText: '取消'
  })
  await deleteProductionOrder(row.id)
  ElMessage.success('生产订单已删除')
  await handleSearch(currentPage.value)
}

const openDetail = async row => {
  detailVisible.value = true
  detailLoading.value = true
  try {
    const res = await getProductionOrderDetail(row.id)
    Object.assign(detail, {
      baseInfo: res.data?.baseInfo || null,
      materials: res.data?.materials || [],
      outputs: res.data?.outputs || [],
      outputCodes: res.data?.outputCodes || [],
      labelBatches: res.data?.labelBatches || []
    })
  } finally {
    detailLoading.value = false
  }
}

const handlePrimaryAction = row => {
  const action = primaryActionText(row)
  if (action === '去领用') {
    goMaterialPick(row)
  } else if (action === '去贴码') {
    goOutputBind(row)
  } else if (action === '查看入库') {
    goInboundTasks(row)
  } else {
    openDetail(row)
  }
}

const goMaterialPick = row => {
  router.push({ path: '/production/material-pick', query: { orderId: row.id } })
}

const goOutputBind = row => {
  router.push({ path: '/production/output-bind', query: { orderId: row.id } })
}

const goInboundTasks = row => {
  router.push(row.orderType === 'SEMI' ? '/pallet-task/semi/in' : '/pallet-task/finish/in')
}

const loadProducts = async () => {
  const res = await getProductList({})
  productList.value = Array.isArray(res.data) ? res.data : []
}

onMounted(async () => {
  await loadProducts()
  await handleSearch(1)
})
</script>

<style scoped>
.production-page {
  padding: 0;
}

.search-card {
  margin-bottom: 16px;
}

.table-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 16px;
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
  margin-top: 18px;
}

.detail-drawer {
  padding-right: 8px;
}

.info-panel,
.detail-section {
  margin-bottom: 20px;
}

.info-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
  margin-top: 12px;
}

.info-item {
  min-width: 0;
  border: 1px solid var(--app-border-soft);
  border-radius: 6px;
  padding: 10px 12px;
  background: #fff;
}

.info-item.wide {
  grid-column: 1 / -1;
}

.info-item span {
  display: block;
  color: var(--app-text-tertiary);
  font-size: 12px;
  margin-bottom: 4px;
}

.info-item strong {
  display: block;
  color: var(--app-text);
  font-size: 14px;
  font-weight: 600;
  word-break: break-word;
}

.order-steps {
  margin-bottom: 22px;
}

.compare-section {
  display: grid;
  grid-template-columns: minmax(180px, 0.42fr) minmax(0, 1fr);
  gap: 12px;
}

.compare-panel {
  min-width: 0;
}

.plan-summary {
  min-height: 72px;
  margin-top: 10px;
  padding: 12px;
  border: 1px solid var(--app-border-soft);
  border-radius: 6px;
  background: #f7f8fb;
  color: var(--app-text);
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  font-weight: 600;
  line-height: 1.7;
  word-break: break-word;
}

.plan-summary-item {
  width: 100%;
  text-align: left;
}

.cell-muted {
  margin-top: 2px;
  color: var(--app-text-tertiary);
  font-size: 12px;
}

.plan-cell {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr);
  column-gap: 0;
  align-items: start;
}

.plan-label {
  white-space: nowrap;
}

.plan-values {
  min-width: 0;
  display: flex;
  flex-direction: column;
  align-items: flex-start;
}

.plan-value {
  width: 100%;
  text-align: left;
  word-break: break-word;
}

.plan-lines {
  width: 100%;
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.plan-line {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 86px 20px 86px 20px 50px;
  align-items: center;
  gap: 8px;
  width: 100%;
}

.plan-line :deep(.el-cascader),
.plan-line :deep(.el-input-number) {
  width: 100%;
}

@media (max-width: 1200px) {
  .compare-section {
    grid-template-columns: 1fr;
  }
}
</style>
