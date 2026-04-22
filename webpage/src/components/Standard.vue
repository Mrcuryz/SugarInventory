<template>
  <div class="operation-logs standard-page">
    <el-card class="search-card">
      <el-form :model="searchForm" inline>
        <el-form-item label="产品类型">
          <el-select v-model="searchForm.productType" clearable placeholder="全部产品类型" style="width: 180px">
            <el-option v-for="item in productTypes" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="标准名称">
          <el-input v-model="searchForm.standardName" clearable placeholder="请输入标准名称" style="width: 180px" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch(1)">查询</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <div class="standard-workspace">
      <el-card class="table-card">
        <div class="table-toolbar">
          <div>
            <div class="section-title">化验标准</div>
            <div class="section-subtitle">维护标准主信息、关联产品和固定 7 项指标。</div>
          </div>
          <el-button type="primary" @click="openCreate">新增标准</el-button>
        </div>

        <el-table :data="resultList" row-key="id" highlight-current-row v-loading="loading" @current-change="handleCurrentChange">
          <el-table-column prop="standardName" label="标准名称" min-width="180" />
          <el-table-column prop="productType" label="产品类型" width="110">
            <template #default="{ row }">
              <el-tag>{{ row.productType || '-' }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="关联产品" min-width="280" class-name="related-products-column">
            <template #default="{ row }">
              <div class="related-products-wrap">
                <el-tag
                  v-for="product in row.relatedProducts || []"
                  :key="`${row.id}-${product.productId}`"
                  size="small"
                  type="primary"
                  class="related-product-tag"
                >
                  {{ product.productName }}
                </el-tag>
                <span v-if="!(row.relatedProducts || []).length" class="empty-text">暂无关联产品</span>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="状态" width="110">
            <template #default="{ row }">
              <el-tag :type="row.status === 'ENABLED' ? 'success' : 'info'">{{ row.status === 'ENABLED' ? '启用' : '停用' }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="220" fixed="right">
            <template #default="{ row }">
              <el-button type="primary" link @click.stop="openRelationDrawer(row)">添加产品</el-button>
              <el-button type="primary" link @click.stop="openEdit(row)">编辑</el-button>
              <el-button type="danger" link @click.stop="handleDelete(row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>

        <div class="pagination-wrap">
          <el-pagination
            background
            layout="total, sizes, prev, pager, next"
            :total="pageState.total"
            :current-page="pageState.currentPage"
            :page-size="pageState.pageSize"
            :page-sizes="[10, 20, 50]"
            @current-change="handlePageChange"
            @size-change="handleSizeChange"
          />
        </div>
      </el-card>

      <el-card class="detail-card">
        <template v-if="currentStandard">
          <div class="detail-head">
            <div>
              <div class="detail-title">{{ currentStandard.standardName }}</div>
              <div class="detail-subtitle">{{ currentStandard.productType || '-' }}</div>
            </div>
            <el-tag :type="currentStandard.status === 'ENABLED' ? 'success' : 'info'">
              {{ currentStandard.status === 'ENABLED' ? '启用' : '停用' }}
            </el-tag>
          </div>

          <div class="meta-grid">
            <div class="meta-item">
              <span>标准编号（选填）</span>
              <strong>{{ currentStandard.standardCode || '未设置' }}</strong>
            </div>
            <div class="meta-item">
              <span>适用等级</span>
              <strong>{{ currentStandard.standardLevel || '未设置' }}</strong>
            </div>
            <div class="meta-item">
              <span>标准版本</span>
              <strong>{{ buildVersionLabel(currentStandard.version) }}</strong>
            </div>
            <div class="meta-item">
              <span>更新时间</span>
              <strong>{{ formatDateTime(currentStandard.updatedAt) }}</strong>
            </div>
          </div>

          <div class="block-title">指标明细</div>
          <el-table :data="detailMetricRows" size="small" border>
            <el-table-column prop="metricName" label="指标名称" min-width="140" />
            <el-table-column label="下限" width="110">
              <template #default="{ row }">{{ row.minValue ?? '无下限' }}</template>
            </el-table-column>
            <el-table-column label="上限" width="110">
              <template #default="{ row }">{{ row.maxValue ?? '无上限' }}</template>
            </el-table-column>
            <el-table-column prop="unit" label="单位" width="100">
              <template #default="{ row }">{{ row.unit || '-' }}</template>
            </el-table-column>
          </el-table>

          <div class="remark-box">
            <div class="remark-title">备注</div>
            <div class="remark-text">{{ currentStandard.remark || '暂无备注' }}</div>
          </div>
        </template>
        <el-empty v-else description="请选择左侧标准查看详情" />
      </el-card>
    </div>

    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="980px" destroy-on-close>
      <el-form :model="submitForm" label-width="110px">
        <div class="form-grid">
          <el-form-item label="标准名称" required>
            <el-input v-model="submitForm.standardName" placeholder="请输入标准名称" />
          </el-form-item>
          <el-form-item label="标准编号">
            <el-input v-model="submitForm.standardCode" placeholder="例如GB/T 35883-2018" />
          </el-form-item>
          <el-form-item label="产品类型" required>
            <el-select v-model="submitForm.productType" placeholder="请选择产品类型">
              <el-option v-for="item in productTypes" :key="item.value" :label="item.label" :value="item.value" />
            </el-select>
          </el-form-item>
          <el-form-item label="适用等级">
            <el-input v-model="submitForm.standardLevel" placeholder="例如 国家一级" />
          </el-form-item>
          <el-form-item label="标准版本">
            <div class="version-display">{{ buildVersionLabel(submitForm.version) }}</div>
          </el-form-item>
          <el-form-item label="状态">
            <el-select v-model="submitForm.status">
              <el-option label="启用" value="ENABLED" />
              <el-option label="停用" value="DISABLED" />
            </el-select>
          </el-form-item>
        </div>
        <el-form-item label="备注">
          <el-input v-model="submitForm.remark" type="textarea" :rows="2" placeholder="输入标准适用说明" />
        </el-form-item>

        <div class="items-toolbar">
          <div>
            <div class="block-title">指标明细</div>
            <div class="section-subtitle">固定 7 项指标</div>
          </div>
          <div class="items-toolbar-actions">
            <el-button @click="openCopyDialog">从已有标准复制</el-button>
          </div>
        </div>

        <el-table :data="submitForm.items" size="small" border>
          <el-table-column prop="metricName" label="指标名称" min-width="160" />
          <el-table-column label="下限" width="130">
            <template #default="{ row }">
              <el-input-number v-model="row.minValue" :controls="false" style="width: 100%" />
            </template>
          </el-table-column>
          <el-table-column label="上限" width="130">
            <template #default="{ row }">
              <el-input-number v-model="row.maxValue" :controls="false" style="width: 100%" />
            </template>
          </el-table-column>
          <el-table-column label="单位" width="120">
            <template #default="{ row }">
              <span>{{ row.unit || '-' }}</span>
            </template>
          </el-table-column>
        </el-table>
      </el-form>

      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitStandard">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="copyDialogVisible" title="从已有标准复制" width="640px" destroy-on-close>
      <div class="copy-dialog-body">
        <div class="copy-filter-row">
          <el-select v-model="copyFilter.productType" clearable placeholder="全部产品类型">
            <el-option v-for="item in productTypes" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
          <el-input v-model="copyFilter.keyword" clearable placeholder="请输入标准名称" />
        </div>
        <el-table :data="copyCandidates" height="320" highlight-current-row @current-change="handleCopySelection">
          <el-table-column prop="standardName" label="标准名称" min-width="180" />
          <el-table-column prop="productType" label="产品类型" width="110" />
          <el-table-column label="标准版本" width="100">
            <template #default="{ row }">{{ buildVersionLabel(row.version) }}</template>
          </el-table-column>
          <el-table-column label="状态" width="100">
            <template #default="{ row }">
              <el-tag :type="row.status === 'ENABLED' ? 'success' : 'info'">{{ row.status === 'ENABLED' ? '启用' : '停用' }}</el-tag>
            </template>
          </el-table-column>
        </el-table>
      </div>

      <template #footer>
        <el-button @click="copyDialogVisible = false">取消</el-button>
        <el-button type="primary" :disabled="!selectedCopyCandidate" @click="applyCopyCandidate">复制到当前表单</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="conflictDialogVisible" title="标准版本处理" width="520px" destroy-on-close>
      <div class="conflict-body">
        <div class="conflict-title">{{ conflictTitle }}</div>
        <div class="conflict-desc">
          {{ submitForm.standardName }} 标准当前已存在，最新版本为 {{ buildVersionLabel(conflictLatestVersion) }}。
        </div>
        <div class="conflict-actions-note">
          请选择后续处理方式：取消、覆盖现有版本，或设为最新版本。
        </div>
      </div>

      <template #footer>
        <el-button @click="conflictDialogVisible = false">取消</el-button>
        <el-button @click="handleConflictAction('overwrite')">覆盖</el-button>
        <el-button type="primary" @click="handleConflictAction('latest')">设为最新版本</el-button>
      </template>
    </el-dialog>

    <el-drawer v-model="relationDrawerVisible" size="720px" destroy-on-close>
      <template #header>
        <div>
          <div class="section-title">标准关联产品</div>
          <div class="section-subtitle">{{ activeStandard?.standardName || '-' }} / {{ activeStandard?.productType || '-' }}</div>
        </div>
      </template>

      <div class="relation-panel" v-loading="relationLoading">
        <div class="relation-summary" v-if="activeStandard">
          <div class="summary-item"><span>标准版本</span><strong>{{ buildVersionLabel(activeStandard.version) }}</strong></div>
          <div class="summary-item"><span>标准状态</span><strong>{{ activeStandard.status === 'ENABLED' ? '启用' : '停用' }}</strong></div>
          <div class="summary-item"><span>已关联产品</span><strong>{{ (activeStandard.relatedProducts || []).length }}</strong></div>
        </div>

        <el-card class="relation-form-card">
          <div class="form-card-head">
            <div>
              <div class="section-title">添加产品</div>
              <div class="section-subtitle">给当前标准补充产品关联，并可直接设置默认标准。</div>
            </div>
            <el-button type="primary" :loading="relationSubmitting" @click="submitRelation">保存关联</el-button>
          </div>
          <div class="relation-form-grid">
            <el-select v-model="relationForm.productId" filterable placeholder="选择产品">
              <el-option v-for="item in relationProductOptions" :key="item.id" :label="item.productName" :value="item.id" />
            </el-select>
            <el-input-number v-model="relationForm.priority" :min="1" :controls="false" placeholder="优先级" />
            <el-select v-model="relationForm.enabled" placeholder="状态">
              <el-option :value="true" label="启用" />
              <el-option :value="false" label="停用" />
            </el-select>
            <el-switch v-model="relationForm.isDefault" active-text="设为默认" />
          </div>
          <el-input v-model="relationForm.remark" placeholder="备注，例如：该产品默认执行此标准" />
        </el-card>

        <div class="block-title">当前关联产品</div>
        <div class="related-products-wrap relation-products-box">
          <el-tag
            v-for="product in activeStandard?.relatedProducts || []"
            :key="`relation-${product.productId}`"
            size="small"
            type="primary"
            class="related-product-tag"
          >
            {{ product.productName }}
          </el-tag>
          <span v-if="!(activeStandard?.relatedProducts || []).length" class="empty-text">暂无关联产品</span>
        </div>
      </div>
    </el-drawer>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getProductList } from '@/api/product'
import { bindProductStandardRelation } from '@/api/productQualityStandard'
import {
  addStandard,
  deleteStandard,
  forceDeleteStandard,
  getStandard,
  getStandardDetail,
  getStandardPage,
  updateStandard
} from '@/api/standard'

const MAX_STANDARD_VALUE = 2147483647

const productTypes = [
  { value: '白冰糖', label: '白冰糖' },
  { value: '黄冰糖', label: '黄冰糖' }
]

const metricLibrary = [
  { metricCode: 'color_value', metricName: '色值', unit: 'IU' },
  { metricCode: 'reducing_sugar', metricName: '还原糖分', unit: 'g/100g' },
  { metricCode: 'dry_weight_loss', metricName: '干燥失重', unit: 'g/100g' },
  { metricCode: 'conductivity_ash', metricName: '电导灰分', unit: 'g/100g' },
  { metricCode: 'sucrose', metricName: '蔗糖分', unit: 'g/100g' },
  { metricCode: 'insoluble_impurity', metricName: '不溶于水杂质', unit: 'mg/kg' },
  { metricCode: 'ph', metricName: 'pH', unit: '' }
]

const searchForm = ref({
  productType: '',
  standardName: ''
})
const resultList = ref([])
const loading = ref(false)
const pageState = ref({
  currentPage: 1,
  pageSize: 10,
  total: 0
})
const dialogVisible = ref(false)
const submitting = ref(false)
const currentStandard = ref(null)
const submitForm = ref(createStandardForm())
const copyDialogVisible = ref(false)
const copyFilter = ref({
  productType: '',
  keyword: ''
})
const copySourceList = ref([])
const selectedCopyCandidate = ref(null)
const conflictDialogVisible = ref(false)
const pendingPayload = ref(null)
const conflictList = ref([])
const relationDrawerVisible = ref(false)
const relationLoading = ref(false)
const relationSubmitting = ref(false)
const activeStandard = ref(null)
const productOptions = ref([])
const relationForm = ref(createRelationForm())

const dialogTitle = computed(() => (submitForm.value.id ? '编辑化验标准' : '新增化验标准'))
const detailMetricRows = computed(() => createFixedMetricRows(currentStandard.value?.items || []))
const copyCandidates = computed(() => {
  const keyword = copyFilter.value.keyword?.trim()
  return copySourceList.value.filter(item => {
    if (copyFilter.value.productType && item.productType !== copyFilter.value.productType) {
      return false
    }
    if (keyword && !item.standardName?.includes(keyword)) {
      return false
    }
    return true
  })
})
const conflictLatestVersion = computed(() => conflictList.value[0]?.version || 1)
const conflictTitle = computed(() => `${submitForm.value.standardName || '当前'}标准已存在`)
const relationProductOptions = computed(() => {
  const relatedIds = new Set((activeStandard.value?.relatedProducts || []).map(item => item.productId))
  return productOptions.value.filter(item => !relatedIds.has(item.id))
})

function createRelationForm() {
  return {
    productId: undefined,
    priority: 1,
    enabled: true,
    isDefault: false,
    remark: ''
  }
}

function createFixedMetricRows(items = []) {
  const itemMap = new Map((items || []).map(item => [item.metricCode, item]))
  return metricLibrary.map((metric, index) => {
    const current = itemMap.get(metric.metricCode) || {}
    return {
      id: current.id,
      metricCode: metric.metricCode,
      metricName: metric.metricName,
      minValue: current.minValue ?? null,
      maxValue: current.maxValue ?? null,
      unit: metric.unit,
      sortOrder: current.sortOrder ?? (index + 1) * 10
    }
  })
}

function createStandardForm(standard = null) {
  return {
    id: standard?.id,
    standardCode: standard?.standardCode || '',
    standardName: standard?.standardName || '',
    productType: standard?.productType || '',
    standardLevel: standard?.standardLevel || '',
    version: standard?.version || 1,
    status: standard?.status || 'ENABLED',
    remark: standard?.remark || '',
    originalStandardName: standard?.standardName || '',
    originalProductType: standard?.productType || '',
    items: createFixedMetricRows(standard?.items || [])
  }
}

function buildVersionLabel(version) {
  return `v${version || 1}`
}

function formatDateTime(value) {
  if (!value) return '-'
  return String(value).replace('T', ' ').slice(0, 16)
}

function sanitizeItems(items = []) {
  return createFixedMetricRows(items).map((item, index) => ({
    metricCode: item.metricCode,
    metricName: item.metricName,
    minValue: item.minValue ?? null,
    maxValue: item.maxValue ?? null,
    unit: item.unit || '',
    sortOrder: (index + 1) * 10
  }))
}

function getErrorMessage(error) {
  return error?.msg || error?.message || error?.response?.data?.msg || '操作失败'
}

function validateMetricValues(items) {
  for (const item of items) {
    const values = [item.minValue, item.maxValue].filter(value => value !== null && value !== undefined)
    if (values.some(value => Number(value) < 0)) {
      ElMessage.warning('输入值不能为负！')
      return false
    }
    if (values.some(value => Number(value) > MAX_STANDARD_VALUE)) {
      ElMessage.warning('输入值超过最大上限')
      return false
    }
    if (item.minValue != null && item.maxValue != null && Number(item.minValue) > Number(item.maxValue)) {
      ElMessage.warning(`${item.metricName} 下限不能大于上限`)
      return false
    }
  }
  return true
}

async function loadStandardDetail(id) {
  const res = await getStandardDetail(id)
  currentStandard.value = res.data || null
  if (activeStandard.value?.id === id) {
    activeStandard.value = res.data || null
  }
}

async function handleSearch(page = pageState.value.currentPage) {
  loading.value = true
  try {
    pageState.value.currentPage = page
    const res = await getStandardPage({
      productType: searchForm.value.productType || undefined,
      standardName: searchForm.value.standardName || undefined,
      page: pageState.value.currentPage,
      size: pageState.value.pageSize
    })
    resultList.value = res.data?.records || []
    pageState.value.total = res.data?.total || 0
    if (!resultList.value.length && pageState.value.total > 0 && pageState.value.currentPage > 1) {
      await handleSearch(pageState.value.currentPage - 1)
      return
    }
    if (resultList.value.length) {
      const target = currentStandard.value
        ? resultList.value.find(item => item.id === currentStandard.value.id) || resultList.value[0]
        : resultList.value[0]
      await loadStandardDetail(target.id)
    } else {
      currentStandard.value = null
    }
  } finally {
    loading.value = false
  }
}

function handleReset() {
  searchForm.value = { productType: '', standardName: '' }
  pageState.value.currentPage = 1
  handleSearch(1)
}

function handlePageChange(page) {
  handleSearch(page)
}

function handleSizeChange(size) {
  pageState.value.pageSize = size
  handleSearch(1)
}

async function handleCurrentChange(row) {
  if (!row?.id) {
    currentStandard.value = null
    return
  }
  await loadStandardDetail(row.id)
}

function openCreate() {
  submitForm.value = createStandardForm()
  dialogVisible.value = true
}

async function openEdit(row) {
  const detailRes = await getStandardDetail(row.id)
  submitForm.value = createStandardForm(detailRes.data)
  dialogVisible.value = true
}

async function openCopyDialog() {
  selectedCopyCandidate.value = null
  if (!copySourceList.value.length) {
    const res = await getStandard({ status: 'ENABLED' })
    copySourceList.value = res.data || []
  }
  copyDialogVisible.value = true
}

function handleCopySelection(row) {
  selectedCopyCandidate.value = row || null
}

async function applyCopyCandidate() {
  if (!selectedCopyCandidate.value?.id) return
  const res = await getStandardDetail(selectedCopyCandidate.value.id)
  submitForm.value.items = createFixedMetricRows(res.data?.items || [])
  copyDialogVisible.value = false
  ElMessage.success('已复制当前标准的 7 项指标，可继续微调')
}

async function loadSameNameStandards(payload) {
  const res = await getStandard({
    productType: payload.productType,
    standardName: payload.standardName
  })
  return (res.data || [])
    .filter(item => item.standardName === payload.standardName)
    .sort((left, right) => (right.version || 1) - (left.version || 1))
}

function shouldPromptConflict(sameNameStandards, payload) {
  const otherStandards = sameNameStandards.filter(item => item.id !== submitForm.value.id)
  if (!otherStandards.length) {
    return false
  }
  if (!submitForm.value.id) {
    return true
  }
  return submitForm.value.originalStandardName !== payload.standardName
    || submitForm.value.originalProductType !== payload.productType
}

function buildPayload(version = submitForm.value.version || 1) {
  return {
    standardCode: submitForm.value.standardCode || undefined,
    standardName: submitForm.value.standardName?.trim(),
    productType: submitForm.value.productType,
    standardLevel: submitForm.value.standardLevel || undefined,
    version,
    status: submitForm.value.status || 'ENABLED',
    remark: submitForm.value.remark || undefined,
    items: sanitizeItems(submitForm.value.items)
  }
}

async function persistStandard(mode, payload) {
  if (mode === 'overwrite') {
    const target = conflictList.value[0]
    if (target?.id) {
      await updateStandard(target.id, { ...payload, version: target.version || 1 })
      ElMessage.success('已覆盖现有标准版本')
      return
    }
  }
  if (mode === 'latest') {
    const nextVersion = (conflictList.value[0]?.version || 0) + 1
    await addStandard({ ...payload, version: nextVersion })
    ElMessage.success(`已新增 ${buildVersionLabel(nextVersion)}`)
    return
  }
  if (submitForm.value.id) {
    await updateStandard(submitForm.value.id, payload)
    ElMessage.success('标准已更新')
    return
  }
  await addStandard(payload)
  ElMessage.success('标准已新增')
}

async function handleConflictAction(mode) {
  if (!pendingPayload.value) return
  submitting.value = true
  try {
    await persistStandard(mode, pendingPayload.value)
    conflictDialogVisible.value = false
    dialogVisible.value = false
    await handleSearch(pageState.value.currentPage)
  } finally {
    submitting.value = false
  }
}

async function submitStandard() {
  const payload = buildPayload()
  if (!payload.standardName || !payload.productType) {
    ElMessage.warning('请先填写标准名称和产品类型')
    return
  }
  if (!validateMetricValues(payload.items)) {
    return
  }

  const sameNameStandards = await loadSameNameStandards(payload)
  if (shouldPromptConflict(sameNameStandards, payload)) {
    conflictList.value = sameNameStandards
    pendingPayload.value = payload
    conflictDialogVisible.value = true
    return
  }

  submitting.value = true
  try {
    await persistStandard('direct', payload)
    dialogVisible.value = false
    await handleSearch(pageState.value.currentPage)
  } finally {
    submitting.value = false
  }
}

async function handleDelete(row) {
  try {
    await ElMessageBox.confirm(`确认删除标准“${row.standardName}”吗？`, '删除标准', {
      confirmButtonText: '确认',
      cancelButtonText: '取消',
      type: 'warning'
    })
  } catch {
    return
  }

  try {
    await deleteStandard(row.id)
    ElMessage.success('标准已删除')
    await handleSearch(pageState.value.currentPage)
  } catch (error) {
    const message = getErrorMessage(error)
    if (!message.includes('当前标准正在被使用')) {
      return
    }

    try {
      await ElMessageBox.confirm('当前标准正在被使用！', '标准正在被使用', {
        confirmButtonText: '强制删除',
        cancelButtonText: '取消',
        type: 'warning'
      })
    } catch {
      return
    }

    await forceDeleteStandard(row.id)
    ElMessage.success('标准已强制删除')
    await handleSearch(pageState.value.currentPage)
  }
}

async function openRelationDrawer(row) {
  relationDrawerVisible.value = true
  relationForm.value = createRelationForm()
  relationLoading.value = true
  try {
    const detailRes = await getStandardDetail(row.id)
    activeStandard.value = detailRes.data || row
    await loadProductOptions(activeStandard.value?.productType)
  } finally {
    relationLoading.value = false
  }
}

async function loadProductOptions(productType) {
  const res = await getProductList({ type: productType || undefined })
  productOptions.value = res.data || []
}

async function submitRelation() {
  if (!activeStandard.value?.id || !relationForm.value.productId) {
    ElMessage.warning('请选择要绑定的产品')
    return
  }
  relationSubmitting.value = true
  try {
    await bindProductStandardRelation({
      productId: relationForm.value.productId,
      qualityStandardId: activeStandard.value.id,
      priority: relationForm.value.priority,
      enabled: relationForm.value.enabled,
      isDefault: relationForm.value.isDefault,
      remark: relationForm.value.remark || undefined
    })
    ElMessage.success('产品关联已保存')
    relationForm.value = createRelationForm()
    await loadProductOptions(activeStandard.value.productType)
    await loadStandardDetail(activeStandard.value.id)
    await handleSearch(pageState.value.currentPage)
  } finally {
    relationSubmitting.value = false
  }
}

onMounted(async () => {
  await handleSearch(1)
})
</script>

<style scoped>
.operation-logs {
  padding: 20px;
}

.search-card,
.table-card,
.detail-card,
.relation-form-card {
  background: var(--app-panel);
}

.search-card {
  margin-bottom: 20px;
}

.standard-workspace {
  display: grid;
  grid-template-columns: minmax(0, 1.6fr) minmax(360px, 1fr);
  gap: 20px;
}

.table-toolbar,
.detail-head,
.items-toolbar,
.form-card-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 16px;
}

.items-toolbar-actions {
  display: flex;
  gap: 8px;
}

.section-title,
.detail-title,
.block-title {
  color: var(--app-text);
  font-size: 16px;
  font-weight: 700;
}

.section-subtitle,
.detail-subtitle {
  margin-top: 4px;
  color: var(--app-text-tertiary);
  font-size: 13px;
}

.meta-grid,
.form-grid,
.relation-summary {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.meta-grid {
  margin-bottom: 18px;
}

.meta-item,
.remark-box,
.copy-dialog-body,
.conflict-body,
.summary-item,
.relation-products-box {
  padding: 14px;
  border: 1px solid var(--app-border-soft);
  border-radius: var(--app-radius);
  background: #fbfcff;
}

.meta-item span,
.remark-title,
.empty-text,
.summary-item span {
  display: block;
  color: var(--app-text-tertiary);
  font-size: 12px;
}

.meta-item strong,
.remark-text,
.version-display,
.summary-item strong {
  display: block;
  margin-top: 6px;
  color: var(--app-text);
  font-size: 14px;
  line-height: 1.5;
}

.version-display {
  margin-top: 0;
}

.remark-box {
  margin-top: 18px;
}

.copy-filter-row,
.relation-form-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
  margin-bottom: 12px;
}

.relation-panel {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.relation-form-grid {
  grid-template-columns: 2fr 1fr 1fr 1fr;
}

.conflict-title {
  color: var(--app-text);
  font-size: 16px;
  font-weight: 700;
}

.conflict-desc,
.conflict-actions-note {
  margin-top: 8px;
  color: var(--app-text-secondary);
  line-height: 1.6;
}

.pagination-wrap {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}

:deep(.el-input),
:deep(.el-select),
:deep(.el-input-number) {
  width: 100%;
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

:deep(.related-products-column .cell) {
  white-space: normal;
  overflow: visible;
  text-overflow: initial;
}

.related-products-wrap {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px;
  padding: 2px 0;
  line-height: 1.6;
}

.related-product-tag {
  max-width: 100%;
  height: auto;
  min-height: 24px;
  white-space: normal;
  word-break: break-all;
  line-height: 1.4;
  padding: 3px 8px;
}
</style>
