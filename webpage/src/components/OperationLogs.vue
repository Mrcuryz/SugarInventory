<template>
  <div class="operation-logs">
    <el-card class="search-card">
      <el-form :model="searchForm" inline>
        <el-form-item label="时间范围">
          <el-date-picker
            v-model="searchForm.dateRange"
            type="daterange"
            range-separator="至"
            start-placeholder="开始日期"
            end-placeholder="结束日期"
            style="width: 400px"
          />
        </el-form-item>
        <el-form-item label="操作类型">
          <el-select
            v-model="searchForm.operationType"
            placeholder="请选择"
            clearable
            style="width: 200px"
          >
            <el-option
              v-for="item in operationTypes"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="操作业务">
          <el-input
            v-model="searchForm.tableName"
            placeholder="请输入操作业务"
            clearable
            style="width: 300px"
          />
        </el-form-item>
        <el-form-item label="操作人员">
          <el-input
            v-model="searchForm.operator"
            placeholder="请输入操作人姓名"
            clearable
            style="width: 220px"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">查询</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card class="table-card">
      <div class="table-toolbar">
        <div>
          <div class="section-title">操作日志</div>
          <div class="section-subtitle">主表只看索引信息，具体变更内容放到右侧详情抽屉。</div>
        </div>
        <el-button @click="exportExcel">导出 Excel</el-button>
      </div>

      <el-table
        :data="logs"
        stripe
        border
        v-loading="loading"
      >
        <el-table-column prop="operationTimeText" label="时间" width="180" sortable />
        <el-table-column prop="tableName" label="操作业务" min-width="180" />
        <el-table-column prop="operationType" label="操作类型" width="120">
          <template #default="{ row }">
            <el-tag :type="getTagType(row.operationType)">
              {{ getOperationTypeLabel(row.operationType) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="operator" label="操作人" width="140" />
        <el-table-column label="操作" width="120" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link @click="openDetail(row)">查看详情</el-button>
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
          :page-sizes="[10, 20, 50]"
          @size-change="handleSizeChange"
          @current-change="handleCurrentChange"
        />
      </div>
    </el-card>

    <el-drawer
      v-model="detailVisible"
      title="日志详情"
      size="70%"
      destroy-on-close
    >
      <div class="log-drawer" v-if="detailLog">
        <div class="drawer-section">
          <div class="block-title">基础信息</div>
          <div class="info-grid">
            <div class="info-card">
              <span>时间</span>
              <strong>{{ detailLog.operationTimeText }}</strong>
            </div>
            <div class="info-card">
              <span>操作业务</span>
              <strong>{{ detailLog.tableName || '-' }}</strong>
            </div>
            <div class="info-card">
              <span>操作类型</span>
              <strong>{{ getOperationTypeLabel(detailLog.operationType) }}</strong>
            </div>
            <div class="info-card">
              <span>操作人</span>
              <strong>{{ detailLog.operator || '-' }}</strong>
            </div>
          </div>
        </div>

        <div class="drawer-section">
          <div class="block-title">{{ detailTitle }}</div>

          <template v-if="detailMode === 'UPDATE'">
            <el-table :data="detailRows" border>
              <el-table-column prop="fieldLabel" label="字段" min-width="180" />
              <el-table-column prop="oldValue" label="修改前" min-width="220" />
              <el-table-column prop="newValue" label="修改后" min-width="220" />
            </el-table>
          </template>

          <template v-else-if="detailMode === 'INSERT'">
            <el-table :data="detailRows" border>
              <el-table-column prop="fieldLabel" label="字段" min-width="180" />
              <el-table-column prop="newValue" label="操作内容" min-width="320" />
            </el-table>
          </template>

          <template v-else-if="detailMode === 'DELETE'">
            <el-table :data="detailRows" border>
              <el-table-column prop="fieldLabel" label="字段" min-width="180" />
              <el-table-column prop="oldValue" label="原数据" min-width="320" />
            </el-table>
          </template>

          <el-empty v-else description="当前日志没有可展示的详细内容" />
        </div>
      </div>
    </el-drawer>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import * as XLSX from 'xlsx'
import { useI18n } from 'vue-i18n'
import { getOperationLogs } from '@/api/operationlogs'

const { t, te } = useI18n()

const operationTypes = [
  { value: 'INSERT', label: '新增' },
  { value: 'UPDATE', label: '修改' },
  { value: 'DELETE', label: '删除' }
]

const searchForm = ref({
  tableName: '',
  operationType: '',
  operator: '',
  dateRange: []
})

const logs = ref([])
const loading = ref(false)
const pageSize = ref(10)
const currentPage = ref(1)
const total = ref(0)
const detailVisible = ref(false)
const detailLog = ref(null)

const detailMode = computed(() => detailLog.value?.operationType || '')
const detailTitle = computed(() => {
  if (detailMode.value === 'INSERT') return '操作内容'
  if (detailMode.value === 'DELETE') return '原数据'
  if (detailMode.value === 'UPDATE') return '字段变更'
  return '详情'
})

const detailRows = computed(() => buildDetailRows(detailLog.value))

function parseJsonField(value) {
  if (value === null || value === undefined || value === '') {
    return {}
  }

  let current = value
  try {
    while (typeof current === 'string') {
      current = JSON.parse(current)
    }
  } catch (error) {
    return {}
  }

  if (current && typeof current === 'object' && !Array.isArray(current)) {
    return current
  }
  return {}
}

function formatDateTime(value) {
  if (!value) return '-'
  return String(value).replace('T', ' ').replace('Z', ' ').trim()
}

function formatValue(value) {
  if (value === null || value === undefined || value === '') return '-'
  if (value === 'ADMIN') return '管理员'
  if (value === 'QC') return '化验员'
  if (value === 'STAFF') return '员工'
  if (value === true) return '是'
  if (value === false) return '否'
  if (Array.isArray(value)) {
    return value.length ? value.map(item => formatValue(item)).join('、') : '-'
  }
  if (typeof value === 'object') {
    return JSON.stringify(value)
  }
  return String(value)
}

function getFieldLabel(key) {
  return te(`fields.${key}`) ? t(`fields.${key}`) : key
}

function normalizeLog(log) {
  const changedFields = parseJsonField(log.changedFields)
  const oldData = parseJsonField(log.oldData)
  delete changedFields.id
  delete oldData.id

  return {
    ...log,
    operationTimeText: formatDateTime(log.operationTime),
    changedFields,
    oldData
  }
}

function buildDetailRows(log) {
  if (!log) return []

  if (log.operationType === 'INSERT') {
    return Object.entries(log.changedFields || {}).map(([key, value]) => ({
      key,
      fieldLabel: getFieldLabel(key),
      newValue: formatValue(value)
    }))
  }

  if (log.operationType === 'DELETE') {
    return Object.entries(log.oldData || {}).map(([key, value]) => ({
      key,
      fieldLabel: getFieldLabel(key),
      oldValue: formatValue(value)
    }))
  }

  if (log.operationType === 'UPDATE') {
    return Object.entries(log.changedFields || {}).map(([key, value]) => ({
      key,
      fieldLabel: getFieldLabel(key),
      oldValue: formatValue(log.oldData?.[key]),
      newValue: formatValue(value)
    }))
  }

  return []
}

function getOperationTypeLabel(type) {
  return operationTypes.find(item => item.value === type)?.label || type || '-'
}

function getTagType(type) {
  return {
    INSERT: 'success',
    UPDATE: 'warning',
    DELETE: 'danger'
  }[type] || 'info'
}

function buildQueryParams() {
  const params = {
    page: currentPage.value,
    size: pageSize.value
  }

  if (searchForm.value.tableName) {
    params.tableName = searchForm.value.tableName
  }
  if (searchForm.value.operationType) {
    params.operationType = searchForm.value.operationType
  }
  if (searchForm.value.operator) {
    params.operator = searchForm.value.operator
  }
  if (Array.isArray(searchForm.value.dateRange) && searchForm.value.dateRange.length === 2) {
    params.startTime = searchForm.value.dateRange[0]
    params.endTime = searchForm.value.dateRange[1]
  }

  return params
}

async function fetchLogs() {
  loading.value = true
  try {
    const res = await getOperationLogs(buildQueryParams())
    if (res.code !== 200) {
      ElMessage.error(res.msg || '获取操作日志失败')
      return
    }

    total.value = res.data.total || 0
    logs.value = (res.data.records || []).map(normalizeLog)
  } catch (error) {
    ElMessage.error(error?.message || '获取操作日志失败')
  } finally {
    loading.value = false
  }
}

async function handleSearch() {
  currentPage.value = 1
  await fetchLogs()
}

function handleReset() {
  searchForm.value = {
    tableName: '',
    operationType: '',
    operator: '',
    dateRange: []
  }
  currentPage.value = 1
  fetchLogs()
}

function handleSizeChange(size) {
  pageSize.value = size
  currentPage.value = 1
  fetchLogs()
}

function handleCurrentChange(page) {
  currentPage.value = page
  fetchLogs()
}

function openDetail(row) {
  detailLog.value = row
  detailVisible.value = true
}

async function exportExcel() {
  loading.value = true
  try {
    const res = await getOperationLogs({
      ...buildQueryParams(),
      page: 1,
      size: total.value || pageSize.value
    })

    if (res.code !== 200) {
      ElMessage.error(res.msg || '导出失败')
      return
    }

    const excelData = (res.data.records || []).map(normalizeLog).map(log => {
      const rows = buildDetailRows(log)
      const detailText = rows.map(row => {
        if (log.operationType === 'UPDATE') {
          return `${row.fieldLabel}: ${row.oldValue} -> ${row.newValue}`
        }
        if (log.operationType === 'INSERT') {
          return `${row.fieldLabel}: ${row.newValue}`
        }
        return `${row.fieldLabel}: ${row.oldValue}`
      }).join('\n')

      return {
        时间: log.operationTimeText,
        操作业务: log.tableName,
        操作类型: getOperationTypeLabel(log.operationType),
        操作人: log.operator,
        详情: detailText || '无'
      }
    })

    const worksheet = XLSX.utils.json_to_sheet(excelData)
    const workbook = XLSX.utils.book_new()
    XLSX.utils.book_append_sheet(workbook, worksheet, '操作日志')
    XLSX.writeFile(workbook, `操作日志_${new Date().toISOString().slice(0, 10)}.xlsx`)
    ElMessage.success('导出成功')
  } catch (error) {
    ElMessage.error(`导出失败: ${error.message}`)
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  fetchLogs()
})
</script>

<style scoped>
.operation-logs {
  padding: 20px;
}

.search-card,
.table-card {
  background: var(--app-panel);
}

.search-card {
  margin-bottom: 20px;
}

.section-title,
.block-title {
  color: var(--app-text);
  font-size: 16px;
  font-weight: 700;
}

.section-subtitle {
  margin-top: 4px;
  color: var(--app-text-tertiary);
  font-size: 13px;
}

.log-drawer {
  display: flex;
  flex-direction: column;
  gap: 20px;
  min-height: 100%;
}

.drawer-section {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.info-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.info-card {
  padding: 14px 16px;
  border: 1px solid var(--app-border-soft);
  border-radius: var(--app-radius);
  background: #fbfcff;
}

.info-card span {
  display: block;
  color: var(--app-text-tertiary);
  font-size: 12px;
}

.info-card strong {
  display: block;
  margin-top: 6px;
  color: var(--app-text);
  font-size: 14px;
  line-height: 1.5;
}

.pagination-wrapper {
  margin-top: 20px;
  display: flex;
  justify-content: flex-end;
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

@media (max-width: 1200px) {
  .info-grid {
    grid-template-columns: 1fr;
  }
}
</style>
