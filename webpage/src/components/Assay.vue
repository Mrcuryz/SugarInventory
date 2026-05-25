<template>
  <div class="operation-logs assay-page">
    <el-card class="search-card">
      <el-form :model="searchForm" inline>
        <el-form-item label="时间范围">
          <el-date-picker
            v-model="searchForm.dateRange"
            type="daterange"
            range-separator="至"
            start-placeholder="开始日期"
            end-placeholder="结束日期"
            style="width: 434px"
          />
        </el-form-item>
        <el-form-item label="化验产品名称">
          <el-input
            v-model="searchForm.productName"
            placeholder="请输入化验产品名称"
            clearable
            style="width: 180px"
          />
        </el-form-item>
        <el-form-item label="化验员">
          <el-input
            v-model="searchForm.testerName"
            placeholder="请输入化验员姓名"
            clearable
            style="width: 180px"
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
          <div class="section-title">化验管理</div>
          <div class="section-subtitle">列表只展示结论摘要，详细数据和历史版本统一在右侧查看。</div>
        </div>
        <el-button type="primary" @click="openCreateDialog">新增</el-button>
      </div>

      <el-table class="assay-table" :data="resultList" row-key="id" border stripe v-loading="loading">
        <el-table-column prop="productName" label="产品名称" min-width="150" show-overflow-tooltip />
        <el-table-column prop="sampleDate" label="采样日期" width="112" />
        <el-table-column prop="testerName" label="化验员" width="96" show-overflow-tooltip />
        <el-table-column label="是否合格" width="104">
          <template #default="{ row }">
            <el-tag :type="getQualificationTagType(row)">
              {{ getQualificationLabel(row) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="合格标准" min-width="150" show-overflow-tooltip>
          <template #default="{ row }">
            <span>{{ getStandardDisplayName(row) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="190" align="left" header-align="left">
          <template #default="{ row }">
            <div class="table-actions">
              <el-button type="primary" link @click="openDetailDrawer(row)">详细</el-button>
              <el-button type="primary" link @click="openHistoryDrawer(row)">历史</el-button>
              <el-dropdown trigger="click" @command="(command) => handleMoreCommand(command, row)">
                <el-button type="primary" link>
                  更多
                </el-button>
                <template #dropdown>
                  <el-dropdown-menu>
                    <el-dropdown-item command="edit">编辑</el-dropdown-item>
                    <el-dropdown-item command="copy">复制</el-dropdown-item>
                    <el-dropdown-item command="delete" divided>删除</el-dropdown-item>
                  </el-dropdown-menu>
                </template>
              </el-dropdown>
            </div>
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
          @current-change="handlePageChange"
        />
      </div>
    </el-card>

    <el-drawer
      v-model="drawerVisible"
      :title="drawerMode === 'history' ? '查看历史数据' : '查看详细数据'"
      size="70%"
      destroy-on-close
    >
      <div class="assay-drawer" v-loading="drawerLoading">
        <template v-if="drawerMode === 'history'">
          <div class="drawer-toolbar">
            <div class="drawer-toolbar-left">
              <el-select
                v-model="selectedHistoryId"
                placeholder="请选择版本"
                style="width: 240px"
                @change="handleHistoryVersionChange"
              >
                <el-option
                  v-for="item in historyOptions"
                  :key="item.id"
                  :label="`版本 ${item.version || 1}`"
                  :value="item.id"
                />
              </el-select>
            </div>
            <div class="version-summary" v-if="historyOptions.length">
              <span>共 {{ historyOptions.length }} 个版本</span>
              <span>当前查看：{{ historyRecord?.version || '-' }}</span>
              <span>最新版本：{{ latestHistoryVersion }}</span>
            </div>
          </div>
          <el-alert
            v-if="historyOptions.length <= 1"
            title="当前记录暂无历史版本，以下展示为当前版本数据。"
            type="info"
            :closable="false"
            class="drawer-alert"
          />
        </template>

        <template v-if="activeDrawerRecord">
          <div class="drawer-section">
            <div class="block-title">基础信息</div>
            <div class="info-grid">
              <div class="info-card">
                <span>产品名称</span>
                <strong>{{ activeDrawerRecord.productName || '-' }}</strong>
              </div>
              <div class="info-card">
                <span>采样日期</span>
                <strong>{{ activeDrawerRecord.sampleDate || '-' }}</strong>
              </div>
              <div class="info-card">
                <span>化验员</span>
                <strong>{{ activeDrawerRecord.testerName || '-' }}</strong>
              </div>
              <div class="info-card">
                <span>录入时间</span>
                <strong>{{ formatDateTime(activeDrawerRecord.createdAt) }}</strong>
              </div>
              <div class="info-card">
                <span>是否合格</span>
                <strong>{{ getQualificationLabel(activeDrawerRecord) }}</strong>
              </div>
              <div class="info-card">
                <span>合格标准</span>
                <strong>{{ getStandardDisplayName(activeDrawerRecord) }}</strong>
              </div>
            </div>
          </div>

          <div class="drawer-section">
            <div class="block-title">指标明细</div>
            <el-table :data="drawerMetricRows" border>
              <el-table-column prop="metricName" label="指标名称" min-width="140" />
              <el-table-column label="实测值" width="140">
                <template #default="{ row }">{{ formatMetricValue(row.actualValue) }}</template>
              </el-table-column>
              <el-table-column label="标准范围" min-width="180">
                <template #default="{ row }">{{ row.standardRange }}</template>
              </el-table-column>
              <el-table-column label="单位" width="120">
                <template #default="{ row }">{{ row.unit || '-' }}</template>
              </el-table-column>
              <el-table-column label="判定结果" width="140">
                <template #default="{ row }">
                  <el-tag :type="row.resultType">{{ row.resultLabel }}</el-tag>
                </template>
              </el-table-column>
            </el-table>
          </div>

          <div class="drawer-section">
            <div class="block-title">结论说明</div>
            <div class="conclusion-card">
              <template v-if="detailConclusion.status === 'PASS'">
                <div class="conclusion-title success-text">
                  本次化验符合：{{ detailConclusion.standardName }}
                </div>
                <div class="conclusion-text">{{ detailConclusion.message }}</div>
              </template>
              <template v-else-if="detailConclusion.status === 'FAIL'">
                <div class="conclusion-title danger-text">本次化验判定为不合格</div>
                <div class="conclusion-text">不合格指标：{{ detailConclusion.failedMetricNames }}</div>
                <div class="conclusion-text">原因：{{ detailConclusion.failedReasons }}</div>
              </template>
              <template v-else>
                <div class="conclusion-title warning-text">{{ detailConclusion.title }}</div>
                <div class="conclusion-text">{{ detailConclusion.message }}</div>
              </template>
            </div>
          </div>
        </template>

        <el-empty v-else description="暂无可展示的化验详情" />
      </div>
    </el-drawer>

    <el-dialog
      v-model="dialogVisible"
      :title="operationType"
      width="40%"
      :before-close="handleClose"
    >
      <el-form :model="submitForm" :rules="rule" label-width="auto">
        <el-form-item label="选择类型" prop="selectType" required v-if="operationType === '新增化验'">
          <el-radio-group v-model="submitForm.selectType" class="radio-group">
            <el-radio label="1" border class="radio-item">选择产品</el-radio>
            <el-radio label="2" border class="radio-item">选择批量化验组</el-radio>
          </el-radio-group>
        </el-form-item>

        <el-form-item
          label="化验产品名称"
          prop="productId"
          v-if="(operationType === '新增化验' && submitForm.selectType === '1') || operationType === '复制化验' || operationType === '修改化验'"
        >
          <el-cascader
            v-model="submitForm.productId"
            :options="productOptions"
            :props="cascaderProps"
            placeholder="请选择化验产品名称"
            style="width: 100%"
            clearable
          />
        </el-form-item>

        <el-form-item
          v-if="operationType === '新增化验' && submitForm.selectType === '2'"
          label="批量化验组"
          prop="relatedId"
          required
        >
          <el-select
            v-model="submitForm.relatedId"
            placeholder="请选择批量化验组"
            style="width: 100%"
            collapse-tags
            filterable
            clearable
          >
            <el-option
              v-for="standard in assayStandardList"
              :key="standard.id"
              :label="standard.standardName"
              :value="standard.id"
            />
          </el-select>
        </el-form-item>

        <el-form-item label="采样日期" prop="sampleDate">
          <el-date-picker
            v-model="submitForm.sampleDate"
            type="date"
            placeholder="请选择采样日期"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="色值" prop="colorValue">
          <el-input v-model="submitForm.colorValue" placeholder="请输入色值" style="width: 100%" />
        </el-form-item>
        <el-form-item label="还原糖分" prop="reducingSugar">
          <el-input v-model="submitForm.reducingSugar" placeholder="请输入还原糖分" style="width: 100%" />
        </el-form-item>
        <el-form-item label="干燥失重" prop="dryWeight">
          <el-input v-model="submitForm.dryWeight" placeholder="请输入干燥失重" style="width: 100%" />
        </el-form-item>
        <el-form-item label="电导灰分" prop="conductivityAsh">
          <el-input v-model="submitForm.conductivityAsh" placeholder="请输入电导灰分" style="width: 100%" />
        </el-form-item>
        <el-form-item label="蔗糖分" prop="sucrose">
          <el-input v-model="submitForm.sucrose" placeholder="请输入蔗糖分" style="width: 100%" />
        </el-form-item>
        <el-form-item label="不溶于水杂质" prop="insolubleImpurity">
          <el-input v-model="submitForm.insolubleImpurity" placeholder="请输入不溶于水杂质" style="width: 100%" />
        </el-form-item>
        <el-form-item label="pH值" prop="phValue">
          <el-input v-model="submitForm.phValue" placeholder="请输入pH值" style="width: 100%" />
        </el-form-item>
      </el-form>

      <template #footer>
        <div class="dialog-footer">
          <el-button type="primary" @click="operationType === '新增化验' || operationType === '复制化验' ? handleNew() : handleUpdate()">
            确定
          </el-button>
          <el-button @click="dialogVisible = false">取消</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import dayjs from 'dayjs'
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  addAssay,
  deleteAssay,
  getAssay,
  getAssayDetail,
  getSemiProduct,
  getStProduct,
  updateAssay
} from '@/api/assay'
import { getAssayGroup } from '@/api/assayGroup'

const route = useRoute()

const metricDefinitions = [
  { metricCode: 'color_value', metricName: '色值', field: 'colorValue' },
  { metricCode: 'reducing_sugar', metricName: '还原糖分', field: 'reducingSugar' },
  { metricCode: 'dry_weight_loss', metricName: '干燥失重', field: 'dryWeight' },
  { metricCode: 'conductivity_ash', metricName: '电导灰分', field: 'conductivityAsh' },
  { metricCode: 'sucrose', metricName: '蔗糖分', field: 'sucrose' },
  { metricCode: 'insoluble_impurity', metricName: '不溶于水杂质', field: 'insolubleImpurity' },
  { metricCode: 'ph', metricName: 'pH', field: 'phValue' }
]

const searchForm = ref({
  productName: getRouteQueryValue('productName'),
  dateRange: [],
  testerName: '',
  version: ''
})
const resultList = ref([])
const loading = ref(false)
const pageSize = ref(10)
const currentPage = ref(1)
const total = ref(0)

const drawerVisible = ref(false)
const drawerMode = ref('detail')
const drawerLoading = ref(false)
const detailRecord = ref(null)
const historyRecord = ref(null)
const historyOptions = ref([])
const selectedHistoryId = ref(undefined)

const assayStandardList = ref([])
const dialogVisible = ref(false)
const operationType = ref('')
const submitForm = ref(createSubmitForm())

const semiProductList = ref([])
const stProductList = ref([])

const cascaderProps = reactive({
  emitPath: false,
  label: 'label',
  value: 'value',
  children: 'children'
})

const rule = {
  productId: [{ required: true, message: '请选择化验产品名称', trigger: 'blur' }],
  sampleDate: [{ required: true, message: '请选择采样日期', trigger: 'blur' }],
  colorValue: [{ type: 'string', message: '色值必须为数字', trigger: 'blur', pattern: /^-?\d+(\.\d+)?$/ }],
  reducingSugar: [{ type: 'string', message: '还原糖分必须为数字', trigger: 'blur', pattern: /^-?\d+(\.\d+)?$/ }],
  dryWeight: [{ type: 'string', message: '干燥失重必须为数字', trigger: 'blur', pattern: /^-?\d+(\.\d+)?$/ }],
  conductivityAsh: [{ type: 'string', message: '电导灰分必须为数字', trigger: 'blur', pattern: /^-?\d+(\.\d+)?$/ }],
  sucrose: [{ type: 'string', message: '蔗糖分必须为数字', trigger: 'blur', pattern: /^-?\d+(\.\d+)?$/ }],
  insolubleImpurity: [{ type: 'string', message: '不溶于水杂质必须为数字', trigger: 'blur', pattern: /^-?\d+(\.\d+)?$/ }],
  phValue: [{ type: 'string', message: 'pH值必须为数字', trigger: 'blur', pattern: /^-?\d+(\.\d+)?$/ }]
}

const activeDrawerRecord = computed(() => (drawerMode.value === 'history' ? historyRecord.value : detailRecord.value))
const latestHistoryVersion = computed(() => historyOptions.value[0]?.version || '-')
const drawerMetricRows = computed(() => buildMetricRows(activeDrawerRecord.value))
const detailConclusion = computed(() => buildConclusion(activeDrawerRecord.value))

const productOptions = computed(() => {
  const combinedProducts = [
    ...stProductList.value.map(item => ({ ...item, category: '成品' })),
    ...semiProductList.value.map(item => ({ ...item, category: '半成品' }))
  ]

  const categoryMap = {}
  combinedProducts.forEach(product => {
    const categoryNode = categoryMap[product.category] || {
      value: product.category,
      label: product.category,
      children: {}
    }
    const typeNode = categoryNode.children[product.productType] || {
      value: product.productType,
      label: product.productType,
      children: []
    }

    typeNode.children.push({
      value: product.productId,
      label: product.productName
    })

    categoryNode.children[product.productType] = typeNode
    categoryMap[product.category] = categoryNode
  })

  return Object.values(categoryMap).map(category => ({
    ...category,
    children: Object.values(category.children)
  }))
})

function createSubmitForm() {
  return {
    productId: undefined,
    relatedId: undefined,
    selectType: '1',
    sampleDate: '',
    colorValue: '',
    reducingSugar: '',
    dryWeight: '',
    conductivityAsh: '',
    sucrose: '',
    insolubleImpurity: '',
    phValue: ''
  }
}

function getRouteQueryValue(key) {
  const value = route.query[key]
  if (Array.isArray(value)) return value[0] || ''
  return value || ''
}

function parseArrayField(value) {
  if (!value) {
    return []
  }
  if (Array.isArray(value)) {
    return value
  }
  if (typeof value === 'string') {
    try {
      const parsed = JSON.parse(value)
      return Array.isArray(parsed) ? parsed : []
    } catch {
      return value ? [value] : []
    }
  }
  return []
}

function parseObjectField(value) {
  if (!value) {
    return null
  }
  if (typeof value === 'object') {
    return value
  }
  if (typeof value === 'string') {
    try {
      return JSON.parse(value)
    } catch {
      return null
    }
  }
  return null
}

function normalizeRecord(record) {
  const matchedStandards = parseArrayField(record?.matchedStandards || record?.qualifiedStandards)
  const failedMetrics = parseArrayField(record?.failedMetrics || record?.failedMetricsJson)
  const standardSnapshot = parseObjectField(record?.standardSnapshot || record?.standardSnapshotJson)
  return {
    ...record,
    matchedStandards,
    failedMetrics,
    standardSnapshot,
    appliedStandardName: record?.appliedStandardName || record?.appliedStandard?.standardName || '',
    appliedStandardVersion: record?.appliedStandardVersion || record?.appliedStandard?.version || null
  }
}

function buildGroupKey(record) {
  return `${record.productId || record.productName || ''}-${record.sampleDate || ''}`
}

function processResultList(records = []) {
  const groupedMap = new Map()
  records.map(normalizeRecord).forEach(record => {
    const key = buildGroupKey(record)
    const list = groupedMap.get(key) || []
    list.push(record)
    groupedMap.set(key, list)
  })

  return Array.from(groupedMap.values()).map(group => {
    const sorted = [...group].sort((left, right) => Number(right.version || 1) - Number(left.version || 1))
    return {
      ...sorted[0],
      historyVersions: sorted.slice(1),
      historyCount: Math.max(sorted.length - 1, 0)
    }
  })
}

function formatDateTime(value) {
  if (!value) {
    return '-'
  }
  return String(value).replace('T', ' ').slice(0, 16)
}

function formatMetricValue(value) {
  if (value === null || value === undefined || value === '') {
    return '-'
  }
  return `${value}`
}

function buildStandardRange(item) {
  if (!item) {
    return '未配置'
  }
  const minValue = item.minValue
  const maxValue = item.maxValue

  if (minValue == null && maxValue == null) {
    return '未参与判定'
  }
  if (minValue != null && maxValue != null) {
    return `${minValue} ~ ${maxValue}`
  }
  if (minValue != null) {
    return `>= ${minValue}`
  }
  return `<= ${maxValue}`
}

function mergeAssayRecord(baseRecord, detailRecord) {
  const base = normalizeRecord(baseRecord || {})
  const detail = normalizeRecord(detailRecord || {})
  return normalizeRecord({
    ...base,
    ...detail,
    testerName: detail.testerName || base.testerName || '',
    productName: detail.productName || base.productName || '',
    sampleDate: detail.sampleDate || base.sampleDate || '',
    createdAt: detail.createdAt || base.createdAt || '',
    isQualified: detail.isQualified || base.isQualified || '',
    judgeResult: detail.judgeResult || base.judgeResult || '',
    judgeMessage: detail.judgeMessage || base.judgeMessage || ''
  })
}

function buildMetricRows(record) {
  if (!record) {
    return []
  }
  const snapshotItems = record.standardSnapshot?.items || []
  const itemMap = new Map(snapshotItems.map(item => [item.metricCode, item]))
  const failedMap = new Map((record.failedMetrics || []).map(item => [item.metricCode, item]))
  const hasStandard = !!record.standardSnapshot || !!record.appliedStandardName || (record.matchedStandards || []).length > 0

  return metricDefinitions.map(metric => {
    const standardItem = itemMap.get(metric.metricCode)
    const failedItem = failedMap.get(metric.metricCode)
    let resultLabel = '合格'
    let resultType = 'success'

    if (!hasStandard) {
      resultLabel = '无法判定'
      resultType = 'info'
    } else if (!standardItem || (standardItem.minValue == null && standardItem.maxValue == null)) {
      resultLabel = '不参与判定'
      resultType = 'info'
    } else if (failedItem) {
      resultLabel = '不合格'
      resultType = 'danger'
    }

    return {
      metricCode: metric.metricCode,
      metricName: metric.metricName,
      actualValue: record[metric.field],
      unit: standardItem?.unit || failedItem?.unit || '',
      standardRange: buildStandardRange(standardItem),
      resultLabel,
      resultType
    }
  })
}

function buildConclusion(record) {
  if (!record) {
    return {
      status: 'EMPTY',
      title: '暂无结论',
      message: '当前没有可展示的化验记录。'
    }
  }

  const standardName = getStandardDisplayName(record)
  if (record.judgeResult === 'PASS' || record.isQualified === '合格') {
    return {
      status: 'PASS',
      standardName,
      message: record.judgeMessage || '当前记录符合所采用标准。'
    }
  }

  if (record.judgeResult === 'FAIL' || record.isQualified === '不合格') {
    const failedMetrics = record.failedMetrics || []
    return {
      status: 'FAIL',
      failedMetricNames: failedMetrics.map(item => item.metricName).join('、') || '未识别',
      failedReasons: failedMetrics.map(item => item.reason).join('；') || record.judgeMessage || '存在指标未达标。'
    }
  }

  if (record.judgeResult === 'MULTIPLE_CANDIDATES') {
    return {
      status: 'MULTIPLE_CANDIDATES',
      title: '当前记录命中了多个候选标准',
      message: record.judgeMessage || '请结合详细信息人工确认采用的标准。'
    }
  }

  return {
    status: 'NO_STANDARD',
    title: '当前记录无法完成自动判定',
    message: record.judgeMessage || '未匹配到可用标准，请先配置标准后再查看判定结果。'
  }
}

function getStandardDisplayName(record) {
  if (!record) {
    return '-'
  }
  if (record.appliedStandardName) {
    return record.appliedStandardVersion
      ? `${record.appliedStandardName} v${record.appliedStandardVersion}`
      : record.appliedStandardName
  }
  if ((record.matchedStandards || []).length) {
    return record.matchedStandards.join('、')
  }
  return '未配置'
}

function getQualificationLabel(record) {
  if (record?.judgeResult === 'NO_STANDARD') {
    return '无标准'
  }
  if (record?.judgeResult === 'MULTIPLE_CANDIDATES') {
    return '待确认'
  }
  return record?.isQualified || '未知'
}

function getQualificationTagType(record) {
  if (record?.judgeResult === 'NO_STANDARD' || record?.judgeResult === 'MULTIPLE_CANDIDATES') {
    return 'info'
  }
  return record?.isQualified === '合格' ? 'success' : 'danger'
}

async function getAssayStandards(params = {}) {
  const res = await getAssayGroup({
    page: 1,
    size: 1000,
    ...params
  })
  assayStandardList.value = res.data?.records || []
}

async function handleSearch(page = currentPage.value) {
  currentPage.value = page
  const params = {
    page: currentPage.value,
    size: pageSize.value
  }

  if (searchForm.value.productName) {
    params.productName = searchForm.value.productName
  }
  if (searchForm.value.testerName) {
    params.testerName = searchForm.value.testerName
  }
  if (searchForm.value.version) {
    params.version = Number(searchForm.value.version)
  }
  if (searchForm.value.dateRange?.length === 2) {
    params.startDate = dayjs(searchForm.value.dateRange[0]).format('YYYY-MM-DD')
    params.endDate = dayjs(searchForm.value.dateRange[1]).format('YYYY-MM-DD')
  }

  loading.value = true
  try {
    const res = await getAssay(params)
    total.value = res.data?.total || 0
    resultList.value = processResultList(res.data?.records || [])
  } catch (error) {
    ElMessage.error(error?.msg || error?.message || '获取化验记录失败')
  } finally {
    loading.value = false
  }
}

function handleReset() {
  searchForm.value = {
    productName: '',
    dateRange: [],
    testerName: '',
    version: ''
  }
  handleSearch(1)
}

function applyRouteSearch() {
  const productName = getRouteQueryValue('productName')
  if (productName) {
    searchForm.value.productName = productName
  }
  const startDate = getRouteQueryValue('startDate')
  const endDate = getRouteQueryValue('endDate')
  if (startDate && endDate) {
    searchForm.value.dateRange = [startDate, endDate]
  }
}

function handleSizeChange(size) {
  pageSize.value = size
  handleSearch(1)
}

function handlePageChange(page) {
  handleSearch(page)
}

async function loadAssayDetail(id, fallbackRecord = null) {
  const res = await getAssayDetail(id)
  return mergeAssayRecord(fallbackRecord, res.data || {})
}

async function openDetailDrawer(row) {
  drawerMode.value = 'detail'
  drawerVisible.value = true
  drawerLoading.value = true
  detailRecord.value = null
  try {
    detailRecord.value = await loadAssayDetail(row.id, row)
  } catch (error) {
    ElMessage.error(error?.msg || error?.message || '获取化验详情失败')
  } finally {
    drawerLoading.value = false
  }
}

async function fetchHistoryOptions(row) {
  const res = await getAssay({
    page: 1,
    size: 100,
    productName: row.productName,
    startDate: row.sampleDate,
    endDate: row.sampleDate
  })
  const grouped = processResultList(res.data?.records || [])
  const currentGroup = grouped.find(item => buildGroupKey(item) === buildGroupKey(row))
  const versions = currentGroup
    ? [currentGroup, ...(currentGroup.historyVersions || [])]
    : [normalizeRecord(row)]
  historyOptions.value = versions.sort((left, right) => Number(right.version || 1) - Number(left.version || 1))
}

async function openHistoryDrawer(row) {
  drawerMode.value = 'history'
  drawerVisible.value = true
  drawerLoading.value = true
  historyRecord.value = null
  historyOptions.value = []
  selectedHistoryId.value = undefined
  try {
    await fetchHistoryOptions(row)
    const defaultRecord = historyOptions.value[1] || historyOptions.value[0]
    if (!defaultRecord?.id) {
      return
    }
    selectedHistoryId.value = defaultRecord.id
    historyRecord.value = await loadAssayDetail(defaultRecord.id, defaultRecord)
  } catch (error) {
    ElMessage.error(error?.msg || error?.message || '获取历史数据失败')
  } finally {
    drawerLoading.value = false
  }
}

async function handleHistoryVersionChange(id) {
  if (!id) {
    historyRecord.value = null
    return
  }
  drawerLoading.value = true
  try {
    const selectedRecord = historyOptions.value.find(item => item.id === id) || null
    historyRecord.value = await loadAssayDetail(id, selectedRecord)
  } catch (error) {
    ElMessage.error(error?.msg || error?.message || '切换历史版本失败')
  } finally {
    drawerLoading.value = false
  }
}

function openCreateDialog() {
  operationType.value = '新增化验'
  submitForm.value = createSubmitForm()
  dialogVisible.value = true
}

function findProductIdByName(productName) {
  if (!productName) return undefined
  const allProducts = [...stProductList.value, ...semiProductList.value]
  return allProducts.find(item => item.productName === productName)?.productId
}

function openCreateDialogFromRoute() {
  openCreateDialog()
  const productId = getRouteQueryValue('productId')
  const productName = getRouteQueryValue('productName')
  const sampleDate = getRouteQueryValue('sampleDate') || getRouteQueryValue('startDate')
  const matchedProductId = productId || findProductIdByName(productName)
  if (matchedProductId) {
    submitForm.value.productId = Number(matchedProductId)
  }
  if (sampleDate) {
    submitForm.value.sampleDate = sampleDate
  }
}

function handleClose() {
  submitForm.value = createSubmitForm()
  dialogVisible.value = false
}

async function handleNew() {
  if (submitForm.value.selectType === '1' && !submitForm.value.productId) {
    ElMessage.error('请选择化验产品名称')
    return
  }
  if (submitForm.value.selectType === '2' && !submitForm.value.relatedId) {
    ElMessage.error('请选择批量化验组')
    return
  }
  if (!submitForm.value.sampleDate) {
    ElMessage.error('请选择采样日期')
    return
  }

  const params = buildSubmitParams()
  const res = await addAssay([params])
  if (res.code === 200) {
    ElMessage.success('新增成功')
    dialogVisible.value = false
    submitForm.value = createSubmitForm()
    await handleSearch(currentPage.value)
    return
  }
  ElMessage.error(res.msg || '新增失败')
}

function handleEdit(row) {
  operationType.value = '修改化验'
  submitForm.value = {
    id: row.id,
    productId: row.productId,
    relatedId: undefined,
    selectType: '1',
    sampleDate: row.sampleDate,
    colorValue: row.colorValue,
    reducingSugar: row.reducingSugar,
    dryWeight: row.dryWeight,
    conductivityAsh: row.conductivityAsh,
    sucrose: row.sucrose,
    insolubleImpurity: row.insolubleImpurity,
    phValue: row.phValue
  }
  dialogVisible.value = true
}

async function handleUpdate() {
  if (!submitForm.value.sampleDate) {
    ElMessage.error('请选择采样日期')
    return
  }

  const params = buildSubmitParams()
  delete params.selectType
  delete params.relatedId
  const res = await updateAssay(submitForm.value.id, params)
  if (res.code === 200) {
    ElMessage.success('修改成功')
    dialogVisible.value = false
    submitForm.value = createSubmitForm()
    await handleSearch(currentPage.value)
    return
  }
  ElMessage.error(res.msg || '修改失败')
}

async function handleDelete(row) {
  try {
    await ElMessageBox.confirm('确认删除该化验记录吗？', '删除确认', {
      confirmButtonText: '确认',
      cancelButtonText: '取消',
      type: 'warning'
    })
  } catch {
    return
  }

  const res = await deleteAssay(row.id)
  if (res.code === 200) {
    ElMessage.success('删除成功')
    await handleSearch(currentPage.value)
    return
  }
  ElMessage.error(res.msg || '删除失败')
}

function handleCopy(row) {
  operationType.value = '复制化验'
  submitForm.value = {
    ...createSubmitForm(),
    sampleDate: row.sampleDate,
    colorValue: row.colorValue,
    reducingSugar: row.reducingSugar,
    dryWeight: row.dryWeight,
    conductivityAsh: row.conductivityAsh,
    sucrose: row.sucrose,
    insolubleImpurity: row.insolubleImpurity,
    phValue: row.phValue
  }
  dialogVisible.value = true
}

function handleMoreCommand(command, row) {
  if (command === 'edit') {
    handleEdit(row)
    return
  }
  if (command === 'copy') {
    handleCopy(row)
    return
  }
  if (command === 'delete') {
    handleDelete(row)
  }
}

function buildSubmitParams() {
  const params = {
    selectType: submitForm.value.selectType,
    relatedId: submitForm.value.relatedId
  }

  if (submitForm.value.productId) {
    params.productId = submitForm.value.productId
  }
  if (submitForm.value.sampleDate) {
    params.sampleDate = dayjs(submitForm.value.sampleDate).format('YYYY-MM-DD')
  }

  ;[
    'colorValue',
    'reducingSugar',
    'dryWeight',
    'conductivityAsh',
    'sucrose',
    'insolubleImpurity',
    'phValue'
  ].forEach(field => {
    if (submitForm.value[field] !== '' && submitForm.value[field] !== null && submitForm.value[field] !== undefined) {
      params[field] = submitForm.value[field]
    }
  })

  return params
}

async function loadSemiProducts() {
  const res = await getSemiProduct()
  semiProductList.value = res.data || []
}

async function loadFinishedProducts() {
  const res = await getStProduct()
  stProductList.value = res.data || []
}

onMounted(async () => {
  await Promise.all([
    getAssayStandards(),
    loadSemiProducts(),
    loadFinishedProducts()
  ])
  applyRouteSearch()
  await handleSearch(1)
  if (getRouteQueryValue('create') === '1') {
    openCreateDialogFromRoute()
  }
})

watch(
  () => [route.query.productName, route.query.productId, route.query.create, route.query.startDate, route.query.endDate, route.query.sampleDate],
  async () => {
    applyRouteSearch()
    await handleSearch(1)
    if (getRouteQueryValue('create') === '1') {
      openCreateDialogFromRoute()
    }
  }
)
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

.table-toolbar,
.drawer-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 16px;
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

.pagination-wrapper {
  display: flex;
  justify-content: flex-end;
  margin-top: 20px;
}

.table-actions {
  display: flex;
  justify-content: flex-start;
  align-items: center;
  gap: 12px;
  white-space: nowrap;
}

.table-actions :deep(.el-button) {
  margin-left: 0;
  padding: 0;
}

.assay-drawer {
  display: flex;
  flex-direction: column;
  gap: 20px;
  min-height: 100%;
}

.drawer-toolbar {
  margin-bottom: 0;
}

.drawer-toolbar-left {
  display: flex;
  align-items: center;
  gap: 12px;
}

.version-summary {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 10px 18px;
  color: var(--app-text-tertiary);
  font-size: 13px;
}

.drawer-alert {
  margin-top: -6px;
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

.info-card,
.conclusion-card {
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

.conclusion-title {
  font-size: 15px;
  font-weight: 700;
}

.conclusion-text {
  margin-top: 8px;
  color: var(--app-text-secondary);
  line-height: 1.7;
}

.success-text {
  color: var(--el-color-success);
}

.danger-text {
  color: var(--el-color-danger);
}

.warning-text {
  color: var(--el-color-warning);
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

:deep(.el-drawer__body) {
  padding-top: 8px;
}

@media (max-width: 1200px) {
  .info-grid {
    grid-template-columns: 1fr;
  }

  .drawer-toolbar {
    flex-direction: column;
    align-items: flex-start;
  }

  .version-summary {
    justify-content: flex-start;
  }
}
</style>
