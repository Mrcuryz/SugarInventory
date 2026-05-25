<template>
  <div class="warehouse-ledger-page app-page">
    <div class="page-heading">
      <div>
        <h2>库位管理</h2>
        <p>用表格方式精确查看库位容量、占用状态和最近操作，现场空间关系请进入仓库平面图。</p>
      </div>
      <el-button type="primary" plain @click="router.push('/warehouse-map')">打开仓库平面图</el-button>
    </div>

    <div class="ledger-stats">
      <div v-for="item in summaryCards" :key="item.key" class="ledger-stat-card">
        <div class="stat-meta">
          <span class="stat-dot" :class="`is-${item.key}`"></span>
          <span>{{ item.label }}</span>
        </div>
        <strong>{{ item.value }}</strong>
      </div>
    </div>

    <el-card class="app-filter-card" shadow="never">
      <el-form :model="searchForm" class="ledger-filter-form" label-width="92px">
        <div class="filter-grid">
          <el-form-item label="库位名称">
            <el-input v-model="searchForm.warehouseName" clearable placeholder="请输入库位名称"/>
          </el-form-item>
          <el-form-item label="库位状态">
            <el-select v-model="searchForm.status" clearable placeholder="全部状态">
              <el-option v-for="item in statusOptions" :key="item.value" :label="item.label" :value="item.value"/>
            </el-select>
          </el-form-item>
          <el-form-item label="排序方式">
            <el-select v-model="searchForm.sortKey" placeholder="默认顺序">
              <el-option v-for="item in sortOptions" :key="item.value" :label="item.label" :value="item.value"/>
            </el-select>
          </el-form-item>
          <el-form-item label="时间类型">
            <el-select v-model="searchForm.timeType" placeholder="请选择">
              <el-option label="创建时间" value="created"/>
              <el-option label="最近修改时间" value="updated"/>
            </el-select>
          </el-form-item>
          <el-form-item label="时间范围" class="date-item">
            <el-date-picker
                v-model="searchForm.dateRange"
                type="daterange"
                value-format="YYYY-MM-DD"
                range-separator="至"
                start-placeholder="开始日期"
                end-placeholder="结束日期"
            />
          </el-form-item>
        </div>
        <div class="filter-actions">
          <el-button type="primary" @click="handleSearch">查询</el-button>
          <el-button @click="handleReset">重置</el-button>
        </div>
      </el-form>
    </el-card>

    <el-card class="app-table-card ledger-table-card" shadow="never">
      <div class="table-toolbar">
        <div>
          <div class="table-title">库位列表</div>
          <div class="table-subtitle">共 {{ total }} 个库位，支持跳转平面图定位与查看最近 10 条流转操作。</div>
        </div>
      </div>

      <el-table
          :data="warehouseRows"
          style="width: 100%"
          stripe
          v-loading="loading"
          row-key="warehouseId"
          class="ledger-table"
      >
        <el-table-column prop="warehouseName" label="库位名称" min-width="140" show-overflow-tooltip/>
        <el-table-column prop="status" label="库位状态" width="110">
          <template #default="{ row }">
            <el-tag :type="getStatusTag(row.status)" effect="light">{{ displayStatus(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="curCapacity" label="当前容量" width="100"/>
        <el-table-column prop="maxCapacity" label="最大容量" width="100"/>
        <el-table-column label="占用率" min-width="150">
          <template #default="{ row }">
            <div class="usage-cell">
              <el-progress :percentage="normalizePercent(row.capacityPercentage)" :stroke-width="8" :show-text="false"/>
              <span>{{ normalizePercent(row.capacityPercentage) }}%</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="maxRows" label="最大行数" width="100"/>
        <el-table-column prop="currentPalletCount" label="当前板位数" width="120"/>
        <el-table-column prop="currentProductCount" label="当前产品数" width="120"/>
        <el-table-column prop="createdAt" label="创建时间" min-width="170" show-overflow-tooltip>
          <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column prop="updatedAt" label="最近修改时间" min-width="170" show-overflow-tooltip>
          <template #default="{ row }">{{ formatDateTime(row.updatedAt || row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="240" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openEditDialog(row)">编辑</el-button>
            <el-button link type="primary" @click="goWarehouseMap(row)">平面图定位</el-button>
            <el-button link type="primary" @click="openRecentOperations(row)">最近操作</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-wrapper">
        <el-pagination
            v-model:current-page="page.page"
            v-model:page-size="page.size"
            :page-sizes="[10, 20, 50, 100]"
            layout="total, sizes, prev, pager, next, jumper"
            :total="total"
            @size-change="fetchWarehouseLedger"
            @current-change="fetchWarehouseLedger"
        />
      </div>
    </el-card>

    <el-drawer v-model="recentDrawerVisible" :title="recentDrawerTitle" size="420px" class="recent-operation-drawer">
      <div v-if="recentLoading" class="drawer-loading">加载中...</div>
      <el-empty v-else-if="!recentOperations.length" description="暂无最近操作记录"/>
      <div v-else class="operation-list">
        <div v-for="(item, index) in recentOperations" :key="`${item.operationTime}-${index}`" class="operation-item">
          <div class="operation-time">{{ formatDateTime(item.operationTime) }}</div>
          <div class="operation-main">
            <el-tag size="small" type="info" effect="light">{{ item.operationName || item.operationType || '操作' }}</el-tag>
            <span>{{ item.operatorName || '系统' }}</span>
          </div>
          <div class="operation-object">
            {{ buildOperationObject(item) }}
          </div>
          <div class="operation-summary">
            {{ buildOperationSummary(item) }}
          </div>
        </div>
      </div>
    </el-drawer>

    <el-dialog
        v-model="editDialogVisible"
        title="编辑库位"
        width="460px"
        class="warehouse-edit-dialog"
        @closed="resetEditForm"
    >
      <el-form :model="editForm" label-width="96px" class="warehouse-edit-form">
        <el-form-item label="库位名称" required>
          <el-input v-model="editForm.warehouseName" clearable placeholder="请输入库位名称"/>
        </el-form-item>
        <el-form-item label="最大行数" required>
          <el-input-number v-model="editForm.maxRows" :min="1" :step="1" :precision="0" style="width: 100%"/>
        </el-form-item>
        <el-form-item label="库位状态">
          <div class="maintenance-switch-row">
            <el-switch
                v-model="editForm.maintenance"
                active-text="维护"
                inactive-text="正常"
            />
            <span>容量、状态仍由库存变化自动计算。</span>
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="editSubmitting" @click="submitEdit">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import {computed, onMounted, ref} from 'vue'
import {useRouter} from 'vue-router'
import {ElMessage} from 'element-plus'
import {changeWarehouse, changeWarehouseStatus} from '@/api/warehouse'
import {
  getAllWarehouseCapacity,
  getWarehouseRecentOperations,
  queryWarehouseLedger
} from '@/api/warehouseinfo'
import {formatDateTime} from '@/utils/dateTime'

const router = useRouter()
const loading = ref(false)
const recentLoading = ref(false)
const warehouseRows = ref([])
const allWarehouses = ref([])
const total = ref(0)
const recentDrawerVisible = ref(false)
const recentDrawerTitle = ref('最近操作')
const recentOperations = ref([])
const editDialogVisible = ref(false)
const editSubmitting = ref(false)
const editInitialMaintenance = ref(false)
const editForm = ref({
  id: null,
  warehouseName: '',
  maxRows: 1,
  maintenance: false
})

const page = ref({
  page: 1,
  size: 10
})

const searchForm = ref({
  warehouseName: '',
  status: '',
  sortKey: 'default',
  timeType: 'updated',
  dateRange: []
})

const statusOptions = [
  {label: '正常', value: '正常'},
  {label: '空置', value: '空置'},
  {label: '临期预警', value: '临期预警'},
  {label: '满仓', value: '满仓'},
  {label: '维护中', value: '维护'}
]

const sortOptions = [
  {label: '默认顺序', value: 'default'},
  {label: '名称拼音升序', value: 'namePinyin:asc'},
  {label: '名称拼音降序', value: 'namePinyin:desc'},
  {label: '创建时间升序', value: 'createdAt:asc'},
  {label: '创建时间降序', value: 'createdAt:desc'},
  {label: '最近修改时间升序', value: 'updatedAt:asc'},
  {label: '最近修改时间降序', value: 'updatedAt:desc'}
]

const statusCount = computed(() => {
  const seed = {
    total: allWarehouses.value.length,
    normal: 0,
    empty: 0,
    warning: 0,
    full: 0,
    maintenance: 0
  }
  allWarehouses.value.forEach(item => {
    if (item.status === '正常') seed.normal += 1
    if (item.status === '空置') seed.empty += 1
    if (item.status === '临期预警') seed.warning += 1
    if (item.status === '满仓') seed.full += 1
    if (item.status === '维护') seed.maintenance += 1
  })
  return seed
})

const summaryCards = computed(() => [
  {key: 'total', label: '库位总数', value: statusCount.value.total},
  {key: 'normal', label: '正常库位', value: statusCount.value.normal},
  {key: 'empty', label: '空置库位', value: statusCount.value.empty},
  {key: 'warning', label: '临期预警', value: statusCount.value.warning},
  {key: 'full', label: '满仓', value: statusCount.value.full},
  {key: 'maintenance', label: '维护中', value: statusCount.value.maintenance}
])

const normalizePercent = (value) => {
  const numberValue = Number(value || 0)
  if (Number.isNaN(numberValue)) return 0
  return Math.max(0, Math.min(100, Math.round(numberValue)))
}

const displayStatus = (status) => status === '维护' ? '维护中' : (status || '未知')

const getStatusTag = (status) => {
  const map = {
    正常: 'success',
    空置: 'info',
    临期预警: 'warning',
    满仓: 'danger',
    维护: 'warning'
  }
  return map[status] || 'info'
}

const buildLedgerParams = () => {
  const params = {
    page: page.value.page,
    size: page.value.size
  }
  if (searchForm.value.warehouseName) params.warehouseName = searchForm.value.warehouseName
  if (searchForm.value.status) params.status = searchForm.value.status
  if (searchForm.value.sortKey && searchForm.value.sortKey !== 'default') {
    const [sortField, sortOrder] = searchForm.value.sortKey.split(':')
    params.sortField = sortField
    params.sortOrder = sortOrder
  }
  const [start, end] = searchForm.value.dateRange || []
  if (start && end) {
    const prefix = searchForm.value.timeType === 'created' ? 'created' : 'updated'
    params[`${prefix}Start`] = `${start} 00:00:00`
    params[`${prefix}End`] = `${end} 23:59:59`
  }
  return params
}

const fetchSummary = async () => {
  const res = await getAllWarehouseCapacity()
  if (res.code === 200) {
    allWarehouses.value = Array.isArray(res.data) ? res.data : []
  } else {
    ElMessage.error(res.msg || '获取库位概况失败')
  }
}

const fetchWarehouseLedger = async () => {
  loading.value = true
  try {
    const res = await queryWarehouseLedger(buildLedgerParams())
    if (res.code === 200) {
      warehouseRows.value = res.data?.records || []
      total.value = res.data?.total || 0
    } else {
      ElMessage.error(res.msg || '获取库位列表失败')
    }
  } finally {
    loading.value = false
  }
}

const handleSearch = () => {
  page.value.page = 1
  fetchWarehouseLedger()
}

const handleReset = () => {
  searchForm.value = {
    warehouseName: '',
    status: '',
    sortKey: 'default',
    timeType: 'updated',
    dateRange: []
  }
  page.value.page = 1
  fetchWarehouseLedger()
}

const goWarehouseMap = (row) => {
  router.push({
    path: '/warehouse-map',
    query: {warehouseId: row.warehouseId}
  })
}

const openEditDialog = (row) => {
  editForm.value = {
    id: row.warehouseId,
    warehouseName: row.warehouseName || '',
    maxRows: Number(row.maxRows || 1),
    maintenance: row.status === '维护'
  }
  editInitialMaintenance.value = row.status === '维护'
  editDialogVisible.value = true
}

const resetEditForm = () => {
  editForm.value = {
    id: null,
    warehouseName: '',
    maxRows: 1,
    maintenance: false
  }
  editInitialMaintenance.value = false
}

const submitEdit = async () => {
  if (!editForm.value.warehouseName?.trim()) {
    ElMessage.warning('请输入库位名称')
    return
  }
  if (!editForm.value.maxRows || editForm.value.maxRows < 1) {
    ElMessage.warning('请输入正确的最大行数')
    return
  }
  editSubmitting.value = true
  try {
    const updateRes = await changeWarehouse({
      id: editForm.value.id,
      warehouseName: editForm.value.warehouseName.trim(),
      maxRows: editForm.value.maxRows
    })
    if (updateRes.code !== 200) {
      ElMessage.error(updateRes.msg || '库位信息更新失败')
      return
    }
    if (editForm.value.maintenance !== editInitialMaintenance.value) {
      const statusRes = await changeWarehouseStatus(editForm.value.id)
      if (statusRes.code !== 200) {
        ElMessage.error(statusRes.msg || '库位状态更新失败')
        return
      }
    }
    ElMessage.success('库位已更新')
    editDialogVisible.value = false
    await Promise.all([fetchSummary(), fetchWarehouseLedger()])
  } finally {
    editSubmitting.value = false
  }
}

const openRecentOperations = async (row) => {
  recentDrawerTitle.value = `${row.warehouseName} 最近操作`
  recentDrawerVisible.value = true
  recentLoading.value = true
  recentOperations.value = []
  try {
    const res = await getWarehouseRecentOperations(row.warehouseId, {limit: 10})
    if (res.code === 200) {
      recentOperations.value = Array.isArray(res.data) ? res.data : []
    } else {
      ElMessage.error(res.msg || '获取最近操作失败')
    }
  } finally {
    recentLoading.value = false
  }
}

const buildOperationObject = (item) => {
  const parts = []
  if (item.palletCode) parts.push(`二维码 ${item.palletCode}`)
  if (item.productName) parts.push(item.productName)
  return parts.join('，') || '无关联对象'
}

const buildOperationSummary = (item) => {
  if (item.remark) return item.remark
  const from = item.fromWarehouseName || '无'
  const to = item.toWarehouseName || '无'
  return `位置：${from} -> ${to}`
}

onMounted(() => {
  fetchSummary()
  fetchWarehouseLedger()
})
</script>

<style scoped>
.warehouse-ledger-page {
  padding: 20px;
}

.page-heading {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 16px;
  padding: 18px 20px;
  border: 1px solid var(--app-border-soft, #edf0f5);
  border-radius: 10px;
  background: linear-gradient(135deg, #ffffff 0%, #f7faff 100%);
  box-shadow: 0 8px 24px rgba(29, 33, 41, 0.05);
}

.page-heading h2 {
  margin: 0 0 6px;
  font-size: 22px;
  color: var(--app-text);
}

.page-heading p {
  margin: 0;
  color: var(--app-text-secondary);
  font-size: 13px;
}

.ledger-stats {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(150px, 1fr));
  gap: 12px;
  margin-bottom: 16px;
}

.ledger-stat-card {
  position: relative;
  overflow: hidden;
  background: linear-gradient(180deg, #ffffff 0%, #fbfcff 100%);
  border: 1px solid var(--app-border-soft, #edf0f5);
  border-radius: 10px;
  padding: 16px;
  box-shadow: 0 6px 18px rgba(29, 33, 41, 0.05);
}

.ledger-stat-card::before {
  content: "";
  position: absolute;
  inset: 0 auto 0 0;
  width: 3px;
  background: var(--app-primary, #165dff);
  opacity: 0.72;
}

.stat-meta {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 10px;
  color: var(--app-text-secondary);
  font-size: 13px;
}

.stat-dot {
  width: 8px;
  height: 8px;
  border-radius: 999px;
  background: #94a3b8;
}

.stat-dot.is-normal { background: #22c55e; }
.stat-dot.is-empty { background: #94a3b8; }
.stat-dot.is-warning { background: #f59e0b; }
.stat-dot.is-full { background: #ef4444; }
.stat-dot.is-maintenance { background: #64748b; }
.stat-dot.is-total { background: var(--app-primary); }

.ledger-stat-card strong {
  display: block;
  font-size: 24px;
  line-height: 1;
  color: var(--app-text);
}

.app-filter-card,
.app-table-card {
  margin-bottom: 16px;
  border: 1px solid var(--app-border-soft);
  border-radius: var(--app-radius-lg);
  background: var(--app-panel);
}

.ledger-filter-form {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  flex-wrap: wrap;
}

.filter-grid {
  flex: 1;
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
  gap: 12px 16px;
  min-width: 0;
}

.ledger-filter-form :deep(.el-form-item) {
  margin: 0;
}

.ledger-filter-form :deep(.el-input),
.ledger-filter-form :deep(.el-select),
.ledger-filter-form :deep(.el-date-editor) {
  width: 100%;
}

.date-item {
  grid-column: span 2;
}

.filter-actions {
  display: flex;
  align-items: flex-end;
  gap: 8px;
}

.table-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 14px;
}

.table-title {
  font-size: 16px;
  font-weight: 600;
  color: var(--app-text);
}

.table-subtitle {
  margin-top: 4px;
  font-size: 12px;
  color: var(--app-text-secondary);
}

.usage-cell {
  display: grid;
  grid-template-columns: 1fr auto;
  gap: 8px;
  align-items: center;
}

.usage-cell span {
  color: var(--app-text-secondary);
  font-size: 12px;
}

.pagination-wrapper {
  display: flex;
  justify-content: flex-end;
  padding-top: 16px;
}

.drawer-loading {
  padding: 32px;
  color: var(--app-text-secondary);
  text-align: center;
}

.operation-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.operation-item {
  padding: 14px;
  border: 1px solid var(--app-border-soft);
  border-radius: var(--app-radius);
  background: #f8fafc;
}

.operation-time {
  color: var(--app-text-secondary);
  font-size: 12px;
  margin-bottom: 8px;
}

.operation-main {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
  color: var(--app-text);
  font-size: 13px;
}

.operation-object {
  font-weight: 600;
  color: var(--app-text);
  margin-bottom: 6px;
}

.operation-summary {
  color: var(--app-text-secondary);
  line-height: 1.6;
}

.warehouse-edit-form {
  padding-top: 8px;
}

.maintenance-switch-row {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.maintenance-switch-row span {
  color: var(--app-text-tertiary);
  font-size: 12px;
  line-height: 1.5;
}

:deep(.warehouse-edit-dialog .el-dialog__header) {
  padding: 18px 20px 12px;
  border-bottom: 1px solid var(--app-border-soft, #edf0f5);
}

:deep(.warehouse-edit-dialog .el-dialog__body) {
  padding: 18px 22px 6px;
}

:deep(.warehouse-edit-dialog .el-dialog__footer) {
  padding: 14px 20px 18px;
  border-top: 1px solid var(--app-border-soft, #edf0f5);
}

:deep(.maintenance-switch-row .el-switch__core) {
  border-color: var(--app-border, #e5e6eb);
}

:deep(.ledger-table .cell) {
  white-space: nowrap;
}

:deep(.el-table) {
  --el-table-border-color: var(--app-border-soft);
  --el-table-header-bg-color: #f7f8fb;
  --el-table-row-hover-bg-color: var(--app-hover);
}

:deep(.el-table__header th) {
  background-color: #f7f8fb;
  color: var(--app-text-secondary);
  font-weight: 600;
}

:deep(.el-table__body tr:hover > td) {
  background-color: var(--app-hover) !important;
}

@media (max-width: 900px) {
  .page-heading,
  .ledger-filter-form {
    flex-direction: column;
  }

  .date-item {
    grid-column: span 1;
  }

  .filter-actions {
    align-self: flex-start;
  }
}
</style>
