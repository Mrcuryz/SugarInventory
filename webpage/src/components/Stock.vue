<template>
  <div class="ledger-center">
    <div class="page-header">
      <div>
        <div class="page-title">{{ resolvedPageTitle }}</div>
        <div class="page-subtitle">{{ resolvedPageDescription }}</div>
      </div>
    </div>

    <el-card class="search-card">
      <el-form :model="searchForm" class="ledger-search-form" label-width="80px">
        <el-form-item label="产品名称">
          <el-input v-model="searchForm.productName" clearable placeholder="请输入产品名称"/>
        </el-form-item>
        <el-form-item label="仓库">
          <el-input v-model="searchForm.warehouseName" clearable placeholder="请输入仓库"/>
        </el-form-item>
        <el-form-item label="操作人">
          <el-input v-model="searchForm.operatorName" clearable placeholder="请输入操作人"/>
        </el-form-item>
        <el-form-item label="单号">
          <el-input v-model="searchForm.documentNo" clearable placeholder="请输入单号"/>
        </el-form-item>
      </el-form>
      <el-form :model="searchForm" class="ledger-search-form" label-width="80px">
        <el-form-item label="单据状态">
          <el-select v-model="searchForm.status" clearable placeholder="请选择">
            <el-option v-for="item in currentStatusOptions" :key="item.value" :label="item.label" :value="item.value"/>
          </el-select>
        </el-form-item>
        <el-form-item label="时间范围">
          <el-date-picker
              v-model="searchForm.dateRange"
              type="daterange"
              value-format="YYYY-MM-DD"
              range-separator="至"
              start-placeholder="开始日期"
              end-placeholder="结束日期"
          />
        </el-form-item>
        <el-form-item class="search-actions">
          <el-button type="primary" @click="handleSearch">查询</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card class="table-card">
      <div class="table-toolbar">
        <div class="table-toolbar-main">
          <el-tabs v-model="activeTab" class="ledger-tabs" @tab-change="handleTabChange">
            <el-tab-pane v-for="tab in ledgerTabs" :key="tab.value" :label="tab.label" :name="tab.value"/>
          </el-tabs>
          <div class="action-groups">
            <el-button @click="exportLedger">导出当前台账</el-button>
          </div>
        </div>
      </div>

      <el-table :data="ledgerRows" style="width: 100%" height="560" stripe v-loading="loading">
        <el-table-column prop="documentNo" label="单号" min-width="150" fixed="left" show-overflow-tooltip/>
        <el-table-column prop="documentTypeName" label="单据类型" width="120">
          <template #default="{ row }">
            <el-tag :type="getDocumentTagType(row.documentType)">{{ row.documentTypeName }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="operator" label="操作人" min-width="100" show-overflow-tooltip/>
        <el-table-column prop="createdAt" label="创建时间" width="170">
          <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column prop="warehouseName" label="仓库" min-width="130" show-overflow-tooltip/>
        <el-table-column prop="statusName" label="当前状态" width="110">
          <template #default="{ row }">
            <el-tag :type="getStatusTagType(row.status)">{{ row.statusName }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="productName" label="产品名称" min-width="160" show-overflow-tooltip/>
        <el-table-column prop="quantityText" label="数量" width="120" show-overflow-tooltip/>
        <el-table-column prop="totalWeight" label="重量(kg)" width="120" show-overflow-tooltip/>
        <el-table-column label="联动" width="220" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDetail(row)">看单</el-button>
            <el-button link type="primary" @click="openTaskCenter(row)">任务</el-button>
            <el-button link type="primary" @click="openAssay(row)">化验</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-wrapper">
        <el-pagination
            background
            layout="total, sizes, prev, pager, next"
            :total="total"
            :current-page="currentPage"
            :page-size="pageSize"
            @size-change="handleSizeChange"
            @current-change="handleCurrentChange"
        />
      </div>
    </el-card>

    <el-drawer v-model="detailVisible" title="单据详情" size="1200px" class="ledger-drawer">
      <template v-if="currentDocument">
        <el-descriptions class="document-head" :column="2" border>
          <el-descriptions-item label="单号">{{ currentDocument.documentNo }}</el-descriptions-item>
          <el-descriptions-item label="单据类型">{{ currentDocument.documentTypeName }}</el-descriptions-item>
          <el-descriptions-item label="操作人">{{ currentDocument.operator || '-' }}</el-descriptions-item>
          <el-descriptions-item label="创建时间">{{ formatDateTime(currentDocument.createdAt) }}</el-descriptions-item>
          <el-descriptions-item label="仓库">{{ currentDocument.warehouseName || '-' }}</el-descriptions-item>
          <el-descriptions-item label="当前状态">
            <el-tag :type="getStatusTagType(currentDocument.status)">{{ currentDocument.statusName }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="关联化验">
            <el-button v-if="currentDocument.assayId || currentDocument.productName" link type="primary" @click="openAssay(currentDocument)">查看化验</el-button>
            <span v-else>-</span>
          </el-descriptions-item>
        </el-descriptions>

        <div class="drawer-section-title">单明细</div>
        <el-table :data="currentDocument.details" border>
          <el-table-column prop="palletCode" label="二维码" min-width="140" show-overflow-tooltip>
            <template #default="{ row }">
              <el-button v-if="row.palletCode" link type="primary" @click="openPallet(row.palletCode)">{{ row.palletCode }}</el-button>
              <span v-else>-</span>
            </template>
          </el-table-column>
          <el-table-column prop="productName" label="产品名称" min-width="150" show-overflow-tooltip/>
          <el-table-column prop="quantityText" label="数量" width="100"/>
          <el-table-column prop="unitName" label="单位" width="80"/>
          <el-table-column prop="fromLocation" label="原位置" min-width="130" show-overflow-tooltip/>
          <el-table-column prop="toLocation" label="目标位置" min-width="130" show-overflow-tooltip/>
          <el-table-column prop="taskId" label="关联任务ID" width="120">
            <template #default="{ row }">{{ row.taskId || '-' }}</template>
          </el-table-column>
          <el-table-column prop="statusName" label="执行状态" width="110">
            <template #default="{ row }">
              <el-tag :type="getStatusTagType(row.status)">{{ row.statusName }}</el-tag>
            </template>
          </el-table-column>
        </el-table>

        <div class="drawer-actions">
          <el-button @click="openTaskCenter(currentDocument)">关联任务</el-button>
        </div>
      </template>
    </el-drawer>
  </div>
</template>

<script setup>
import {computed, onMounted, ref, watch} from 'vue'
import {ElMessage, ElMessageBox} from 'element-plus'
import {useRouter} from 'vue-router'
import * as XLSX from 'xlsx'
import {pagePalletTasks} from '@/api/palletCode'
import {getProductList} from '@/api/product'
import {getAssay} from '@/api/assay'
import {formatDateTime} from '@/utils/dateTime'

const props = defineProps({
  productStatusFilter: {
    type: String,
    default: ''
  },
  pageTitle: {
    type: String,
    default: '单据中心'
  },
  pageDescription: {
    type: String,
    default: '集中查询入库单、出库单和调拨单，用于查单、看单、追溯和导出。'
  }
})

const router = useRouter()

const baseLedgerTabs = [
  {value: 'IN', label: '入库单', documentNoPrefix: 'IN', documentTypeName: '入库单'},
  {value: 'OUT', label: '出库单', documentNoPrefix: 'OUT', documentTypeName: '出库单'},
  {value: 'TRANSFER', label: '调拨单', documentNoPrefix: 'TR', documentTypeName: '调拨单'}
]

const taskStatusOptions = [
  {label: '待处理', value: 'PENDING'},
  {label: '已确认', value: 'CONFIRMED'},
  {label: '已取消', value: 'CANCELED'}
]

const activeTab = ref('IN')
const searchForm = ref(defaultSearchForm())
const ledgerRows = ref([])
const loading = ref(false)
const currentPage = ref(1)
const pageSize = ref(10)
const total = ref(0)
const detailVisible = ref(false)
const currentDocument = ref(null)
const productSpecMap = ref(new Map())

const resolvedPageTitle = computed(() => props.pageTitle)
const resolvedPageDescription = computed(() => props.pageDescription)
const effectiveProductStatus = computed(() => props.productStatusFilter || '')
const ledgerTabs = computed(() => baseLedgerTabs)
const currentTabConfig = computed(() => ledgerTabs.value.find(tab => tab.value === activeTab.value) || ledgerTabs.value[0])
const currentStatusOptions = computed(() => taskStatusOptions)

watch(effectiveProductStatus, () => {
  if (!ledgerTabs.value.some(tab => tab.value === activeTab.value)) {
    activeTab.value = ledgerTabs.value[0]?.value || 'IN'
  }
  currentPage.value = 1
  searchForm.value.status = ''
  handleSearch()
})

function defaultSearchForm() {
  return {
    productName: '',
    warehouseName: '',
    operatorName: '',
    documentNo: '',
    status: '',
    dateRange: []
  }
}

function buildTransferQuery() {
  const params = {
    taskType: 'TRANSFER',
    pageNum: currentPage.value,
    pageSize: pageSize.value
  }
  if (searchForm.value.productName) params.productName = searchForm.value.productName
  if (searchForm.value.warehouseName) params.targetWarehouseName = searchForm.value.warehouseName
  if (searchForm.value.status) params.status = searchForm.value.status
  if (effectiveProductStatus.value) params.productStatus = effectiveProductStatus.value
  if (searchForm.value.dateRange?.length === 2) {
    params.productionDateStart = searchForm.value.dateRange[0]
    params.productionDateEnd = searchForm.value.dateRange[1]
  }
  return params
}

function buildInboundQuery() {
  const params = {
    taskType: 'IN',
    pageNum: currentPage.value,
    pageSize: pageSize.value
  }
  if (searchForm.value.productName) params.productName = searchForm.value.productName
  if (searchForm.value.warehouseName) params.targetWarehouseName = searchForm.value.warehouseName
  if (searchForm.value.status) params.status = searchForm.value.status
  if (effectiveProductStatus.value) params.productStatus = effectiveProductStatus.value
  if (searchForm.value.dateRange?.length === 2) {
    params.productionDateStart = searchForm.value.dateRange[0]
    params.productionDateEnd = searchForm.value.dateRange[1]
  }
  return params
}

function buildOutTaskQuery() {
  const params = {
    taskType: 'OUT',
    pageNum: currentPage.value,
    pageSize: pageSize.value
  }
  if (searchForm.value.productName) params.productName = searchForm.value.productName
  if (searchForm.value.warehouseName) params.targetWarehouseName = searchForm.value.warehouseName
  if (searchForm.value.status) params.status = searchForm.value.status
  if (effectiveProductStatus.value) params.productStatus = effectiveProductStatus.value
  if (effectiveProductStatus.value === '半成品') params.bizScene = 'DIRECT_OUT'
  if (effectiveProductStatus.value === '成品') params.bizScene = 'FINISH_OUT'
  if (!effectiveProductStatus.value) params.bizScene = 'FINISH_OUT'
  if (searchForm.value.dateRange?.length === 2) {
    params.productionDateStart = searchForm.value.dateRange[0]
    params.productionDateEnd = searchForm.value.dateRange[1]
  }
  return params
}

function buildPrepareTaskQuery() {
  const params = {
    taskType: 'OUT',
    bizScene: 'PREPARE_CONSUMED',
    productStatus: '半成品',
    pageNum: currentPage.value,
    pageSize: pageSize.value
  }
  if (searchForm.value.productName) params.productName = searchForm.value.productName
  if (searchForm.value.warehouseName) params.targetWarehouseName = searchForm.value.warehouseName
  if (searchForm.value.status) params.status = searchForm.value.status
  if (searchForm.value.dateRange?.length === 2) {
    params.productionDateStart = searchForm.value.dateRange[0]
    params.productionDateEnd = searchForm.value.dateRange[1]
  }
  return params
}

async function handleSearch() {
  loading.value = true
  try {
    await ensureProductSpecs()
    let res
    if (activeTab.value === 'IN') {
      res = await pagePalletTasks(buildInboundQuery())
      const rows = normalizeRows(res.data?.records || [], activeTab.value)
      ledgerRows.value = applyClientFilters(rows)
      total.value = hasClientOnlyFilters() ? ledgerRows.value.length : (res.data?.total || 0)
    } else if (activeTab.value === 'OUT') {
      res = await pagePalletTasks(buildOutTaskQuery())
      const rows = normalizeRows(res.data?.records || [], activeTab.value)
      ledgerRows.value = applyClientFilters(rows)
      total.value = hasClientOnlyFilters() ? ledgerRows.value.length : (res.data?.total || 0)
    } else if (activeTab.value === 'PREPARE') {
      res = await pagePalletTasks(buildPrepareTaskQuery())
      const rows = normalizeRows(res.data?.records || [], activeTab.value)
      ledgerRows.value = applyClientFilters(rows)
      total.value = hasClientOnlyFilters() ? ledgerRows.value.length : (res.data?.total || 0)
    } else {
      res = await pagePalletTasks(buildTransferQuery())
      const rows = normalizeRows(res.data?.records || [], activeTab.value)
      ledgerRows.value = applyClientFilters(rows)
      total.value = hasClientOnlyFilters() ? ledgerRows.value.length : (res.data?.total || 0)
    }
  } catch (error) {
    ElMessage.error(error?.message || '查询单据失败')
  } finally {
    loading.value = false
  }
}

function normalizeRows(rows, type) {
  if (type === 'IN') {
    return rows.map(normalizeInboundTask)
  }
  if (type === 'OUT') {
    return rows.map(normalizeOutTask)
  }
  if (type === 'PREPARE') {
    return rows.map(normalizePrepareTask)
  }
  return rows.map(normalizeTransferTask)
}

function normalizeInboundTask(row) {
  const documentNo = createDocumentNo('IN', row, row.operationBatchNo || row.taskId || row.createdAt || row.code)
  const status = row.taskStatus || 'PENDING'
  const detail = {
    palletCode: row.code,
    productName: row.productName,
    quantity: 1,
    quantityText: '1',
    unitName: '板',
    fromLocation: '无',
    toLocation: row.targetWarehouseName || '已确认入库位置',
    taskId: row.taskId,
    status,
    statusName: getTaskStatusName(status)
  }
  return {
    raw: row,
    documentNo,
    documentType: 'IN',
    documentTypeName: '入库单',
    sourcePage: inferSourcePage(row, 'IN'),
    operator: row.confirmedBy || row.createdBy,
    createdAt: row.confirmedAt || row.createdAt,
    warehouseName: row.targetWarehouseName || '',
    warehouseId: row.targetWarehouseId,
    status,
    statusName: getTaskStatusName(status),
    code: row.code,
    taskId: row.taskId,
    productId: row.productId,
    productName: row.productName,
    quantityText: '1板',
    totalWeight: formatWeight(resolveTaskWeight(row)),
    assayId: row.assayId,
    taskRoute: row.taskType === 'FINISH_IN' ? '/pallet-task/finish/in' : '/pallet-task/semi/in',
    details: [detail]
  }
}

function normalizeOutTask(row) {
  const documentNo = createDocumentNo('OUT', row, row.operationBatchNo || row.taskId || row.createdAt || row.code)
  const status = row.taskStatus || 'PENDING'
  const detail = {
    palletCode: row.code,
    productName: row.productName,
    quantity: 1,
    quantityText: '1板',
    unitName: '板',
    fromLocation: row.targetWarehouseName || '出库前库存位置',
    toLocation: '无',
    taskId: row.taskId,
    status,
    statusName: getTaskStatusName(status)
  }
  return {
    raw: row,
    documentNo,
    documentType: 'OUT',
    documentTypeName: '出库单',
    sourcePage: inferSourcePage(row, 'OUT'),
    operator: row.confirmedBy || row.createdBy,
    createdAt: row.confirmedAt || row.createdAt,
    warehouseName: row.targetWarehouseName || '',
    warehouseId: row.targetWarehouseId,
    status,
    statusName: getTaskStatusName(status),
    code: row.code,
    taskId: row.taskId,
    productId: row.productId,
    productName: row.productName,
    quantityText: '1板',
    totalWeight: formatWeight(resolveTaskWeight(row)),
    assayId: row.assayId,
    taskRoute: row.productStatus === '半成品' ? '/pallet-task/semi/out' : '/pallet-task/finish/out',
    details: [detail]
  }
}

function normalizePrepareTask(row) {
  const documentNo = createDocumentNo('PREP', row, row.operationBatchNo || row.taskId || row.createdAt || row.code)
  const status = row.taskStatus || 'PENDING'
  const detail = {
    palletCode: row.code,
    productName: row.productName,
    quantity: 1,
    quantityText: '1板',
    unitName: '板',
    fromLocation: row.targetWarehouseName || '原库存位置',
    toLocation: '历史生产占用',
    taskId: row.taskId,
    status,
    statusName: getTaskStatusName(status)
  }
  return {
    raw: row,
    documentNo,
    documentType: 'PREPARE',
    documentTypeName: '历史生产占用单',
    sourcePage: inferSourcePage(row, 'PREPARE'),
    operator: row.confirmedBy || row.createdBy,
    createdAt: row.confirmedAt || row.createdAt,
    warehouseName: row.targetWarehouseName || '',
    warehouseId: row.targetWarehouseId,
    status,
    statusName: getTaskStatusName(status),
    code: row.code,
    taskId: row.taskId,
    productId: row.productId,
    productName: row.productName,
    quantityText: '1板',
    totalWeight: formatWeight(resolveTaskWeight(row)),
    assayId: row.assayId,
    taskRoute: '/pallet-task/semi/out',
    details: [detail]
  }
}

function normalizeTransferTask(row) {
  const documentNo = createDocumentNo('TR', row, row.taskId || row.createdAt || row.code)
  const status = row.taskStatus || 'PENDING'
  const detail = {
    palletCode: row.code,
    productName: row.productName,
    quantity: 1,
    quantityText: '1板',
    unitName: '板',
    fromLocation: '当前库存位置',
    toLocation: formatLocation(row.targetWarehouseName, row.targetSide),
    taskId: row.taskId,
    status,
    statusName: getTaskStatusName(status)
  }
  return {
    raw: row,
    documentNo,
    documentType: 'TRANSFER',
    documentTypeName: '调拨单',
    sourcePage: '任务中心',
    operator: row.confirmedBy || row.createdBy,
    createdAt: row.confirmedAt || row.createdAt,
    warehouseName: row.targetWarehouseName,
    warehouseId: row.targetWarehouseId,
    status,
    statusName: getTaskStatusName(status),
    code: row.code,
    taskId: row.taskId,
    productId: row.productId,
    productName: row.productName,
    quantityText: '1板',
    totalWeight: formatWeight(resolveTaskWeight(row)),
    assayId: row.assayId,
    taskRoute: '/pallet-task/transfer',
    details: [detail]
  }
}

function applyClientFilters(rows) {
  return rows.filter(row => {
    if (searchForm.value.documentNo && !row.documentNo.includes(searchForm.value.documentNo)) return false
    if (searchForm.value.status && row.status !== searchForm.value.status) return false
    if (searchForm.value.operatorName && !String(row.operator || '').includes(searchForm.value.operatorName)) return false
    if (searchForm.value.warehouseName && !String(row.warehouseName || '').includes(searchForm.value.warehouseName)) return false
    return true
  })
}

function hasClientOnlyFilters() {
  return Boolean(
      searchForm.value.documentNo
      || searchForm.value.operatorName
      || searchForm.value.warehouseName
  )
}

function inferSourcePage(row, type) {
  const text = `${row?.sourcePage || ''}${row?.remark || ''}${row?.operationSource || ''}`
  if (text.includes('仓库平面图')) return '仓库平面图'
  if (row?.operationBatchNo && String(row.operationBatchNo).startsWith('WM')) return '仓库平面图'
  if (text.includes('自动入库')) return '自动入库'
  if (type === 'IN') return '任务中心'
  if (type === 'OUT' || type === 'PREPARE') return '任务中心'
  return '任务中心'
}

function createDocumentNo(prefix, row, id) {
  if (id) return `${prefix}-${String(id).replace(/\s+/g, '').replace(/[:/]/g, '')}`
  const seed = [row?.productName, row?.warehouseName, row?.targetWarehouseName, row?.entryDate, row?.outDate]
      .filter(Boolean)
      .join('-')
      .replace(/\s+/g, '')
  return `${prefix}-${seed || 'UNTRACKED'}`
}

function formatLocation(warehouseName, side) {
  return [warehouseName, side].filter(Boolean).join(' / ') || '无'
}

function formatWeight(value) {
  if (value === null || value === undefined || value === '') return '-'
  const number = Number(value)
  if (!Number.isFinite(number)) return String(value)
  return Number.isInteger(number) ? String(number) : number.toFixed(2).replace(/\.?0+$/, '')
}

async function ensureProductSpecs() {
  if (productSpecMap.value.size) return
  try {
    const res = await getProductList({})
    const rows = Array.isArray(res.data) ? res.data : []
    const map = new Map()
    rows.forEach(item => {
      if (item.id != null) map.set(`id:${item.id}`, item)
      if (item.productName) map.set(`name:${item.productName}`, item)
    })
    productSpecMap.value = map
  } catch (error) {
    productSpecMap.value = new Map()
  }
}

function resolveProductSpec(row) {
  return productSpecMap.value.get(`id:${row.productId}`) || productSpecMap.value.get(`name:${row.productName}`) || null
}

function resolveTaskWeight(row) {
  if (row.totalWeight !== null && row.totalWeight !== undefined && row.totalWeight !== '') return row.totalWeight
  const product = resolveProductSpec(row)
  if (!product) return ''
  const weightPerPiece = Number(product.weightPerPiece || 0)
  const piecesPerPallet = Number(product.piecesPerPallet || 0)
  if (!weightPerPiece || !piecesPerPallet) return ''
  const unit = String(row.productionOutputUnit ?? '')
  if (row.productionOutputCodeId) {
    if (unit === '1') {
      const pieces = Number(row.productionOutputPieces || row.productionOutputQuantity || 0)
      return pieces ? pieces * weightPerPiece : ''
    }
    const boards = Number(row.productionOutputQuantity || 1)
    return boards * piecesPerPallet * weightPerPiece
  }
  return piecesPerPallet * weightPerPiece
}

function getTaskStatusName(status) {
  const map = {
    PENDING: '待处理',
    CONFIRMED: '已确认',
    CANCELED: '已取消',
    COMPLETED: '已完成'
  }
  return map[status] || status || '-'
}

function getStatusTagType(status) {
  if (status === 'CONFIRMED' || status === 'COMPLETED') return 'success'
  if (status === 'PENDING') return 'warning'
  if (status === 'CANCELED') return 'info'
  return 'info'
}

function getDocumentTagType(type) {
  if (type === 'IN') return 'success'
  if (type === 'OUT') return 'warning'
  if (type === 'TRANSFER') return 'primary'
  if (type === 'PREPARE') return 'primary'
  return 'info'
}

function handleTabChange() {
  currentPage.value = 1
  searchForm.value.status = ''
  handleSearch()
}

function handleReset() {
  searchForm.value = defaultSearchForm()
  currentPage.value = 1
  handleSearch()
}

function handleSizeChange(size) {
  pageSize.value = size
  currentPage.value = 1
  handleSearch()
}

function handleCurrentChange(page) {
  currentPage.value = page
  handleSearch()
}

function openDetail(row) {
  currentDocument.value = row
  detailVisible.value = true
}

function openPallet(code) {
  if (!code) {
    ElMessage.info('当前明细没有二维码')
    return
  }
  router.push({path: '/pallet-code/list', query: {code}})
}

function openTaskCenter(row) {
  const path = row.taskRoute || getTaskRoute(row)
  const code = resolveTaskCode(row)
  const query = {}
  if (code) query.code = code
  if (row.status) query.status = row.status
  if (row.productName) query.productNameExact = row.productName
  router.push({path, query})
}

function getTaskRoute(row) {
  if (row.documentType === 'TRANSFER') return '/pallet-task/transfer'
  if (row.documentType === 'PREPARE') return '/pallet-task/semi/out'
  if (row.documentType === 'OUT') return '/pallet-task/finish/out'
  return '/pallet-task/overview'
}

function openWarehouseMap(row) {
  const warehouseId = row.warehouseId || row.raw?.warehouseId || row.raw?.targetWarehouseId
  router.push({path: '/warehouse-map', query: warehouseId ? {warehouseId} : {}})
}

function resolveTaskCode(row) {
  return row.code || row.raw?.code || row.details?.[0]?.palletCode || ''
}

async function hasRelatedAssay(row) {
  if (row.assayId) return true
  if (!row.productName) return false
  const assayDate = resolveAssayDate(row)
  const res = await getAssay({
    page: 1,
    size: 1,
    productName: row.productName,
    ...(assayDate ? {startDate: assayDate, endDate: assayDate} : {})
  })
  return (res.data?.records || []).length > 0
}

function resolveAssayDate(row) {
  const value = row.productionDate || row.raw?.productionDate || row.raw?.entryDate || row.raw?.outDate || ''
  return value ? String(value).slice(0, 10) : ''
}

async function openAssay(row) {
  let hasAssay = false
  try {
    hasAssay = await hasRelatedAssay(row)
  } catch (error) {
    ElMessage.error(error?.msg || error?.message || '查询化验记录失败')
    return
  }

  if (!hasAssay) {
    try {
      await ElMessageBox.confirm('当前暂无相关化验记录', '提示', {
        confirmButtonText: '去新增',
        cancelButtonText: '取消',
        type: 'info'
      })
    } catch {
      return
    }
    router.push({
      path: '/assay',
      query: {
        create: '1',
        productName: row.productName || '',
        productId: row.productId || row.raw?.productId || '',
        sampleDate: resolveAssayDate(row)
      }
    })
    return
  }

  router.push({
    path: '/assay',
    query: {
      productName: row.productName || '',
      assayId: row.assayId || '',
      startDate: resolveAssayDate(row),
      endDate: resolveAssayDate(row)
    }
  })
}

function exportLedger() {
  if (!ledgerRows.value.length) {
    ElMessage.warning('当前没有可导出的单据')
    return
  }
  const data = ledgerRows.value.map(row => ({
    单号: row.documentNo,
    单据类型: row.documentTypeName,
    操作人: row.operator,
    创建时间: formatDateTime(row.createdAt),
    仓库: row.warehouseName,
    当前状态: row.statusName,
    产品名称: row.productName,
    数量: row.quantityText,
    重量kg: row.totalWeight
  }))
  const ws = XLSX.utils.json_to_sheet(data)
  const wb = XLSX.utils.book_new()
  XLSX.utils.book_append_sheet(wb, ws, currentTabConfig.value.label)
  XLSX.writeFile(wb, `${currentTabConfig.value.label}_${Date.now()}.xlsx`)
  ElMessage.success('导出成功')
}

onMounted(() => {
  handleSearch()
})
</script>

<style scoped>
.ledger-center {
  padding: 20px;
}

.search-card,
.table-card {
  max-width: 1500px;
  margin-bottom: 18px;
  background: var(--app-panel);
}

.ledger-search-form {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(240px, 1fr));
  gap: 14px 18px;
  align-items: center;
  margin-bottom: 15px;
}

.ledger-search-form :deep(.el-form-item) {
  margin: 0;
}

.ledger-search-form :deep(.el-input),
.ledger-search-form :deep(.el-select),
.ledger-search-form :deep(.el-date-editor) {
  width: 100%;
}

.search-actions {
  justify-content: flex-end;
}

.table-toolbar-main {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.ledger-tabs {
  flex: 1;
}

.ledger-tip {
  margin-bottom: 14px;
}

.pagination-wrapper {
  margin-top: 18px;
  display: flex;
  justify-content: flex-end;
}

.document-head {
  margin-bottom: 18px;
}

.drawer-section-title {
  margin: 18px 0 12px;
  font-size: 15px;
  font-weight: 600;
  color: var(--app-text-primary);
}

.drawer-actions {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  margin-top: 18px;
}

:deep(.el-table) {
  --el-table-border-color: var(--app-border-soft);
  --el-table-header-bg-color: #f7f8fb;
  --el-table-row-hover-bg-color: var(--app-hover);
}

:deep(.el-table__header th) {
  background-color: #f7f8fb !important;
  color: var(--app-text-secondary);
}

:deep(.el-table__body tr:hover > td) {
  background-color: var(--app-hover) !important;
}

:deep(.ledger-drawer .el-drawer__body) {
  padding: 18px 22px;
}
</style>
