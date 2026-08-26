<script setup>
import { computed, onMounted, ref } from 'vue'
import { Delete, Rank } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  confirmProductionDailyReportImport,
  exportProductionDailyReport,
  getProductionDailyReport,
  importProductionDailyReport,
  listProductionDailyReportProducts,
  pageProductionDailyReports,
  saveProductionDailyReportHeader,
  saveProductionDailyReportSection,
  submitProductionDailyReport,
  submitProductionDailyReportSection
} from '@/api/production'
import ProductionDailyReportMetricRow from '@/components/production/ProductionDailyReportMetricRow.vue'
import { useAuthStore } from '@/stores/auth'
import { formatDateTime } from '@/utils/dateTime'
import { buildProductCascaderOptions, productCascaderProps } from '@/utils/productCascader'
import {
  PRODUCTION_PRODUCT_UNIT,
  productionDailyCalendarRange,
  productionDailyDateState,
  reorderProductLines
} from '@/utils/productionDailyReport'

const authStore = useAuthStore()
const loading = ref(false)
const savingHeader = ref(false)
const savingSection = ref(false)
const submitting = ref(false)
const exporting = ref(false)
const importing = ref(false)
const savingImport = ref(false)
const importPending = ref(false)
const productOptions = ref([])
const calendarStatuses = ref({})
const reportDate = ref(formatDate(new Date()))
const report = ref(createEmptyReport(reportDate.value))
const activeSectionCode = ref('POLYCRYSTAL')
const historyVisible = ref(false)
const historyLoading = ref(false)
const historyRows = ref([])
const historyTotal = ref(0)
const historyPage = ref(1)
const historySize = ref(10)
const productDrag = ref(createEmptyProductDrag())
let productRowSequence = 0
let calendarLoadSequence = 0

const calendarShortcuts = [{ text: '今日', value: () => new Date() }]

const canEdit = computed(() => authStore.hasPermission('production:daily-report:edit'))
const canExport = computed(() => authStore.hasPermission('production:daily-report:export'))
const activeSection = computed(() => {
  return report.value.sections?.find(item => item.departmentCode === activeSectionCode.value)
    || report.value.sections?.[0]
})
const productMap = computed(() => new Map(productOptions.value.map(item => [item.id, item])))

onMounted(async () => {
  await Promise.all([
    loadProductOptions(),
    loadReport(),
    loadCalendarStatuses(parseDate(reportDate.value))
  ])
})

function createEmptyReport(date) {
  return {
    reportDate: date,
    preparedDate: '',
    preparedByName: '',
    status: 'DRAFT',
    version: 0,
    sections: []
  }
}

async function loadReport() {
  loading.value = true
  try {
    const response = await getProductionDailyReport(reportDate.value)
    applyReport(response.data)
  } finally {
    loading.value = false
  }
}

async function loadProductOptions() {
  const response = await listProductionDailyReportProducts()
  productOptions.value = response.data || []
}

function applyReport(payload = {}, options = {}) {
  report.value = {
    ...createEmptyReport(reportDate.value),
    ...payload,
    preparedDate: payload.preparedDate || '',
    preparedByName: payload.preparedByName || '',
    sections: payload.sections || []
  }
  report.value.sections.forEach(section => {
    section.groups = section.groups || []
    section.groups.forEach(group => {
      group.metricValues = group.metricValues || []
      group.productLines = group.productLines || []
      group.productLines.forEach(row => {
        row.unit = PRODUCTION_PRODUCT_UNIT
        row._rowKey = productRowKey(row)
      })
      if (group.productGroup && !group.productLines.length) {
        group.productLines.push(blankProductLine(group, 1))
      }
    })
  })
  if (!report.value.sections.some(item => item.departmentCode === activeSectionCode.value)) {
    activeSectionCode.value = report.value.sections[0]?.departmentCode || 'POLYCRYSTAL'
  }
  if (options.syncCalendarStatus !== false && payload.id && payload.reportDate) {
    calendarStatuses.value = {
      ...calendarStatuses.value,
      [payload.reportDate]: payload.status
    }
  }
}

async function loadCalendarStatuses(anchor) {
  const requestSequence = ++calendarLoadSequence
  const range = productionDailyCalendarRange(anchor)
  const response = await pageProductionDailyReports({
    page: 1,
    size: 100,
    startDate: range.startDate,
    endDate: range.endDate
  })
  if (requestSequence !== calendarLoadSequence) return
  const loaded = {}
  for (const item of response.data?.records || []) {
    loaded[item.reportDate] = item.status
  }
  calendarStatuses.value = { ...calendarStatuses.value, ...loaded }
}

function handleCalendarVisible(visible) {
  if (visible) loadCalendarStatuses(parseDate(reportDate.value))
}

function handleCalendarPanelChange(value) {
  const anchor = value instanceof Date
    ? value
    : (value?.toDate ? value.toDate() : parseDate(reportDate.value))
  loadCalendarStatuses(anchor)
}

function dateCellKey(cell) {
  if (cell?.dayjs?.format) return cell.dayjs.format('YYYY-MM-DD')
  if (cell?.date instanceof Date) return formatDate(cell.date)
  return ''
}

function dateCellClasses(cell) {
  const date = dateCellKey(cell)
  const state = productionDailyDateState({
    date,
    selectedDate: reportDate.value,
    today: formatDate(new Date()),
    status: calendarStatuses.value[date]
  })
  return [`is-${state}`, { 'is-adjacent-month': ['prev-month', 'next-month'].includes(cell?.type) }]
}

function blankProductLine(group, displayOrder) {
  return {
    id: null,
    categoryCode: group.groupCode,
    productId: null,
    productName: '',
    importedProductName: '',
    importError: '',
    productStatus: '',
    productType: '',
    unit: PRODUCTION_PRODUCT_UNIT,
    dailyActual: '',
    convertedTons: '',
    monthQuantity: '',
    monthTons: '',
    yearTons: '',
    remark: '',
    displayOrder,
    _rowKey: `new-${++productRowSequence}`
  }
}

function productRowKey(row) {
  if (row._rowKey) return row._rowKey
  return row.id ? `saved-${row.id}` : `new-${++productRowSequence}`
}

function groupProductOptions(group) {
  const statuses = new Set(group.allowedProductStatuses || [])
  return buildProductCascaderOptions(productOptions.value.filter(item => statuses.has(item.productStatus)))
}

function handleProductChange(row) {
  const product = productMap.value.get(row.productId)
  if (!product) {
    row.productName = ''
    row.productStatus = ''
    row.productType = ''
    row.unit = PRODUCTION_PRODUCT_UNIT
    row.importError = row.importedProductName
      ? '未找到匹配产品'
      : (hasProductRowContent(row) ? '请选择产品' : '')
    return
  }
  row.productName = product.productName
  row.productStatus = product.productStatus
  row.productType = product.productType
  row.unit = PRODUCTION_PRODUCT_UNIT
  row.importError = ''
}

function addProduct(group) {
  group.productLines.push(blankProductLine(group, group.productLines.length + 1))
}

function removeProduct(group, index) {
  group.productLines.splice(index, 1)
  if (!group.productLines.length) {
    group.productLines.push(blankProductLine(group, 1))
  }
  refreshProductOrders(group)
}

function createEmptyProductDrag() {
  return { groupCode: '', fromIndex: -1, overIndex: -1 }
}

function startProductDrag(event, group, index) {
  if (!canEdit.value) {
    event.preventDefault()
    return
  }
  productDrag.value = { groupCode: group.groupCode, fromIndex: index, overIndex: index }
  if (event.dataTransfer) {
    event.dataTransfer.effectAllowed = 'move'
    event.dataTransfer.setData('text/plain', `${group.groupCode}:${index}`)
  }
}

function handleProductDragOver(event, group, index) {
  if (!canEdit.value || productDrag.value.groupCode !== group.groupCode) return
  event.preventDefault()
  if (event.dataTransfer) event.dataTransfer.dropEffect = 'move'
  productDrag.value.overIndex = index
}

function dropProduct(group, index) {
  if (productDrag.value.groupCode === group.groupCode) {
    reorderProductLines(group.productLines, productDrag.value.fromIndex, index)
  }
  endProductDrag()
}

function endProductDrag() {
  productDrag.value = createEmptyProductDrag()
}

function isProductDragging(group, index) {
  return productDrag.value.groupCode === group.groupCode && productDrag.value.fromIndex === index
}

function isProductDragOver(group, index) {
  return productDrag.value.groupCode === group.groupCode
    && productDrag.value.fromIndex !== index
    && productDrag.value.overIndex === index
}

function refreshProductOrders(group) {
  group.productLines.forEach((row, index) => {
    row.displayOrder = index + 1
  })
}

async function saveHeader() {
  savingHeader.value = true
  try {
    const response = await saveProductionDailyReportHeader(reportDate.value, {
      version: report.value.version || 0,
      preparedDate: emptyToNull(report.value.preparedDate),
      preparedByName: emptyToNull(report.value.preparedByName)
    })
    applyReport(response.data)
    ElMessage.success('保存成功')
  } catch (error) {
    await handleConflict(error)
  } finally {
    savingHeader.value = false
  }
}

async function saveSection() {
  const section = activeSection.value
  if (!section) return
  savingSection.value = true
  try {
    const response = await saveProductionDailyReportSection(reportDate.value, section.departmentCode, {
      version: section.version || 0,
      metricValues: section.groups.flatMap(group => group.metricValues || []).map(metricPayload),
      productLines: section.groups
        .filter(group => group.productGroup)
        .flatMap(group => (group.productLines || [])
          .filter(hasProductRowContent)
          .map((row, index) => productPayload(group, row, index)))
    })
    applyReport(response.data)
    ElMessage.success('保存成功')
  } catch (error) {
    await handleConflict(error)
  } finally {
    savingSection.value = false
  }
}

function metricPayload(metric) {
  return {
    metricCode: metric.metricCode,
    dailyActual: decimalOrNull(metric.dailyActual),
    convertedTons: decimalOrNull(metric.convertedTons),
    monthQuantity: decimalOrNull(metric.monthQuantity),
    monthTons: decimalOrNull(metric.monthTons),
    yearTons: decimalOrNull(metric.yearTons),
    remark: emptyToNull(metric.remark)
  }
}

function productPayload(group, row, index) {
  return {
    id: row.id || null,
    categoryCode: group.groupCode,
    productId: row.productId || null,
    unit: PRODUCTION_PRODUCT_UNIT,
    dailyActual: decimalOrNull(row.dailyActual),
    convertedTons: decimalOrNull(row.convertedTons),
    monthQuantity: decimalOrNull(row.monthQuantity),
    monthTons: decimalOrNull(row.monthTons),
    yearTons: decimalOrNull(row.yearTons),
    remark: emptyToNull(row.remark),
    displayOrder: index + 1
  }
}

function hasProductRowContent(row) {
  return Boolean(row.productId
    || decimalOrNull(row.dailyActual) !== null
    || decimalOrNull(row.convertedTons) !== null
    || decimalOrNull(row.monthQuantity) !== null
    || decimalOrNull(row.monthTons) !== null
    || decimalOrNull(row.yearTons) !== null
    || emptyToNull(row.remark))
}

async function submitSection() {
  const section = activeSection.value
  if (!section) return
  submitting.value = true
  try {
    const response = await submitProductionDailyReportSection(reportDate.value, section.departmentCode)
    applyReport(response.data)
    ElMessage.success('提交成功')
  } finally {
    submitting.value = false
  }
}

async function submitAll() {
  submitting.value = true
  try {
    const response = await submitProductionDailyReport(reportDate.value)
    applyReport(response.data)
    ElMessage.success('提交成功')
  } finally {
    submitting.value = false
  }
}

async function exportReport() {
  exporting.value = true
  try {
    const response = await exportProductionDailyReport(reportDate.value)
    const url = URL.createObjectURL(response.data)
    const link = document.createElement('a')
    link.href = url
    link.download = `来冰公司${reportDate.value.replaceAll('-', '')}生产日报表.xlsx`
    document.body.appendChild(link)
    link.click()
    link.remove()
    URL.revokeObjectURL(url)
  } finally {
    exporting.value = false
  }
}

function beforeImport(file) {
  if (!/\.(xlsx|xls)$/i.test(file?.name || '')) {
    ElMessage.error('仅支持 .xlsx 或 .xls 文件')
    return false
  }
  if (file.size > 10 * 1024 * 1024) {
    ElMessage.error('Excel 文件不能超过 10MB')
    return false
  }
  return true
}

async function importReportFile({ file }) {
  const confirmed = await ElMessageBox.confirm(
    '导入后将按 Excel 中的日报日期覆盖整张日报，是否继续？',
    '导入生产日报',
    {
      confirmButtonText: '确认导入',
      cancelButtonText: '取消',
      type: 'warning'
    }
  ).catch(() => false)
  if (!confirmed) return

  importing.value = true
  try {
    const formData = new FormData()
    formData.append('file', file)
    const response = await importProductionDailyReport(formData)
    reportDate.value = response.data.report.reportDate
    applyReport(response.data.report, { syncCalendarStatus: response.data.saved })
    importPending.value = !response.data.saved
    if (response.data.saved) {
      ElMessage.success('导入成功')
    } else {
      const firstUnmatched = findFirstUnmatchedProduct()
      if (firstUnmatched) activeSectionCode.value = firstUnmatched.sectionCode
      ElMessage.warning(`有 ${response.data.unmatchedCount} 个产品未找到匹配，请在标红行选择产品`)
    }
  } catch (error) {
    // 请求层已经展示具体业务错误，避免上传控件再次输出未处理异常。
  } finally {
    importing.value = false
  }
}

function findFirstUnmatchedProduct() {
  for (const section of report.value.sections || []) {
    for (const group of section.groups || []) {
      if (!group.productGroup) continue
      const row = (group.productLines || []).find(item => {
        return item.importError || (hasProductRowContent(item) && !item.productId)
      })
      if (row) return { sectionCode: section.departmentCode, group, row }
    }
  }
  return null
}

async function saveImportedReport() {
  const unresolved = findFirstUnmatchedProduct()
  if (unresolved) {
    activeSectionCode.value = unresolved.sectionCode
    ElMessage.error('请先为标红行选择产品')
    return
  }

  savingImport.value = true
  try {
    const response = await confirmProductionDailyReportImport({
      reportDate: reportDate.value,
      version: report.value.version || 0,
      preparedDate: emptyToNull(report.value.preparedDate),
      preparedByName: emptyToNull(report.value.preparedByName),
      sections: (report.value.sections || []).map(section => ({
        departmentCode: section.departmentCode,
        version: section.version || 0,
        metricValues: (section.groups || [])
          .flatMap(group => group.metricValues || [])
          .map(metricPayload),
        productLines: (section.groups || [])
          .filter(group => group.productGroup)
          .flatMap(group => (group.productLines || [])
            .filter(hasProductRowContent)
            .map((row, index) => productPayload(group, row, index)))
      }))
    })
    applyReport(response.data)
    importPending.value = false
    ElMessage.success('导入成功')
  } catch (error) {
    await handleConflict(error)
  } finally {
    savingImport.value = false
  }
}

async function cancelImport() {
  importPending.value = false
  await loadReport()
}

async function openHistory() {
  historyVisible.value = true
  historyPage.value = 1
  await loadHistory()
}

async function loadHistory() {
  historyLoading.value = true
  try {
    const response = await pageProductionDailyReports({
      page: historyPage.value,
      size: historySize.value
    })
    historyRows.value = response.data?.records || []
    historyTotal.value = response.data?.total || 0
  } finally {
    historyLoading.value = false
  }
}

async function chooseHistory(row) {
  historyVisible.value = false
  reportDate.value = row.reportDate
  await loadReport()
}

async function shiftDate(offset) {
  const date = parseDate(reportDate.value)
  date.setDate(date.getDate() + offset)
  reportDate.value = formatDate(date)
  await loadReport()
}

async function handleDateChange() {
  if (reportDate.value) await loadReport()
}

async function handleConflict(error) {
  if (Number(error?.code) !== 1032) return
  const reload = await ElMessageBox.confirm('日报已被更新，是否加载最新数据？', '数据已更新', {
    confirmButtonText: '加载最新数据',
    cancelButtonText: '取消',
    type: 'warning'
  }).catch(() => false)
  if (reload) await loadReport()
}

function sectionStatusType(status) {
  return status === 'SUBMITTED' ? 'success' : 'warning'
}

function statusText(status) {
  return status === 'SUBMITTED' ? '已提交' : '草稿'
}

function sectionActionText(section) {
  return section?.departmentCode === 'FACTORY_SUMMARY' ? '提交汇总' : '提交本车间'
}

function decimalOrNull(value) {
  if (value === null || value === undefined || String(value).trim() === '') return null
  return String(value).trim()
}

function emptyToNull(value) {
  if (value === null || value === undefined || String(value).trim() === '') return null
  return String(value).trim()
}

function formatDate(date) {
  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  return `${year}-${month}-${day}`
}

function parseDate(value) {
  const [year, month, day] = value.split('-').map(Number)
  return new Date(year, month - 1, day, 12, 0, 0)
}
</script>

<template>
  <div class="daily-report-page" v-loading="loading">
    <el-card class="daily-report-header">
      <div class="page-toolbar">
        <div class="page-title">生产日报</div>
        <div class="page-actions">
          <el-button :disabled="importPending" @click="openHistory">历史日报</el-button>
          <el-upload
            v-if="canEdit"
            :show-file-list="false"
            :before-upload="beforeImport"
            :http-request="importReportFile"
            :disabled="importPending"
            accept=".xlsx,.xls"
          >
            <el-button :loading="importing" :disabled="importPending">导入 Excel</el-button>
          </el-upload>
          <el-button v-if="canExport" :loading="exporting" :disabled="importPending" @click="exportReport">导出 Excel</el-button>
          <el-button v-if="canEdit && !importPending" :loading="savingHeader" @click="saveHeader">保存</el-button>
          <el-button v-if="canEdit && !importPending" type="primary" :loading="submitting" @click="submitAll">提交整张日报</el-button>
          <el-button v-if="canEdit && importPending" :disabled="savingImport" @click="cancelImport">取消导入</el-button>
          <el-button v-if="canEdit && importPending" type="primary" :loading="savingImport" @click="saveImportedReport">保存导入</el-button>
        </div>
      </div>

      <div class="header-fields">
        <div class="header-field header-field--date">
          <label>日报日期</label>
          <div class="date-control">
            <el-button :disabled="importPending" @click="shiftDate(-1)">‹</el-button>
            <el-date-picker
              v-model="reportDate"
              type="date"
              value-format="YYYY-MM-DD"
              :clearable="false"
              :disabled="importPending"
              :shortcuts="calendarShortcuts"
              popper-class="production-daily-date-popper"
              @visible-change="handleCalendarVisible"
              @panel-change="handleCalendarPanelChange"
              @change="handleDateChange"
            >
              <template #default="cell">
                <span
                  class="daily-date-cell"
                  :class="dateCellClasses(cell)"
                  :data-date="dateCellKey(cell)"
                >{{ cell.text }}</span>
              </template>
            </el-date-picker>
            <el-button :disabled="importPending" @click="shiftDate(1)">›</el-button>
          </div>
        </div>
        <div class="header-field">
          <label>制表日期</label>
          <el-date-picker
            v-model="report.preparedDate"
            type="date"
            value-format="YYYY-MM-DD"
            :disabled="!canEdit"
            clearable
          />
        </div>
        <div class="header-field">
          <label>制表人</label>
          <el-input v-model="report.preparedByName" :disabled="!canEdit" maxlength="100" />
        </div>
        <div class="header-field header-field--status">
          <label>当前状态</label>
          <div class="status-line">
            <el-tag :type="sectionStatusType(report.status)">{{ statusText(report.status) }}</el-tag>
            <span v-if="report.updatedAt">{{ formatDateTime(report.updatedAt) }}</span>
          </div>
        </div>
      </div>
    </el-card>

    <div class="section-tabs">
      <button
        v-for="section in report.sections"
        :key="section.departmentCode"
        class="section-tab"
        :class="{ active: section.departmentCode === activeSectionCode }"
        @click="activeSectionCode = section.departmentCode"
      >
        <strong>{{ section.departmentName }}</strong>
        <el-tag size="small" :type="sectionStatusType(section.status)">{{ statusText(section.status) }}</el-tag>
      </button>
    </div>

    <el-card v-if="activeSection" class="section-card">
      <div class="section-toolbar">
        <div>
          <div class="section-title">{{ activeSection.departmentName }}</div>
          <div v-if="activeSection.updatedAt" class="section-updated">
            {{ activeSection.updatedByName || '-' }}&nbsp;&nbsp;{{ formatDateTime(activeSection.updatedAt) }}
          </div>
        </div>
        <div v-if="canEdit && !importPending" class="section-actions">
          <el-button :loading="savingSection" @click="saveSection">保存草稿</el-button>
          <el-button type="primary" :loading="submitting" @click="submitSection">
            {{ sectionActionText(activeSection) }}
          </el-button>
        </div>
      </div>

      <div v-for="group in activeSection.groups" :key="group.groupCode" class="report-group">
        <div class="group-toolbar">
          <strong>{{ group.groupName }}</strong>
          <el-button v-if="group.productGroup && canEdit" size="small" @click="addProduct(group)">新增产品</el-button>
        </div>

        <div class="report-grid">
          <div class="report-head">项目</div>
          <div class="report-head">单位</div>
          <div class="report-head">当日实绩</div>
          <div class="report-head">折成吨</div>
          <div class="report-head">月累计(件)</div>
          <div class="report-head">月累计(吨)</div>
          <div class="report-head">年累计(吨)</div>
          <div class="report-head">备注</div>
          <div class="report-head">操作</div>

          <template v-if="group.productGroup">
            <ProductionDailyReportMetricRow
              v-for="metric in group.metricValues.filter(item => item.position === 'BEFORE_PRODUCT')"
              :key="metric.metricCode"
              :metric="metric"
              :disabled="!canEdit"
            />

            <div
              v-for="(row, index) in group.productLines"
              :key="productRowKey(row)"
              class="product-row"
              :class="{
                'is-dragging': isProductDragging(group, index),
                'is-drag-over': isProductDragOver(group, index)
              }"
              @dragover="handleProductDragOver($event, group, index)"
              @drop.prevent="dropProduct(group, index)"
            >
              <div class="report-cell report-cell--product">
                <div class="product-selector" :class="{ 'has-import-error': row.importError }">
                  <el-cascader
                    v-model="row.productId"
                    :options="groupProductOptions(group)"
                    :props="productCascaderProps"
                    :disabled="!canEdit"
                    filterable
                    clearable
                    :placeholder="row.importedProductName || '请选择产品'"
                    @change="handleProductChange(row)"
                  />
                  <div v-if="row.importError" class="product-import-error">{{ row.importError }}</div>
                </div>
              </div>
              <div class="report-cell report-cell--unit fixed-product-unit">{{ PRODUCTION_PRODUCT_UNIT }}</div>
              <div class="report-cell"><el-input v-model="row.dailyActual" :disabled="!canEdit" inputmode="decimal" /></div>
              <div class="report-cell"><el-input v-model="row.convertedTons" :disabled="!canEdit" inputmode="decimal" /></div>
              <div class="report-cell"><el-input v-model="row.monthQuantity" :disabled="!canEdit" inputmode="decimal" /></div>
              <div class="report-cell"><el-input v-model="row.monthTons" :disabled="!canEdit" inputmode="decimal" /></div>
              <div class="report-cell"><el-input v-model="row.yearTons" :disabled="!canEdit" inputmode="decimal" /></div>
              <div class="report-cell report-cell--remark"><el-input v-model="row.remark" :disabled="!canEdit" maxlength="500" /></div>
              <div class="report-cell report-cell--operation">
                <span
                  class="product-drag-handle"
                  :class="{ 'is-disabled': !canEdit }"
                  :draggable="canEdit"
                  title="按住拖动排序"
                  @dragstart="startProductDrag($event, group, index)"
                  @dragend="endProductDrag"
                >
                  <el-icon><Rank /></el-icon>
                </span>
                <el-button link type="danger" :icon="Delete" :disabled="!canEdit" @click="removeProduct(group, index)" />
              </div>
            </div>

            <ProductionDailyReportMetricRow
              v-for="metric in group.metricValues.filter(item => item.position !== 'BEFORE_PRODUCT')"
              :key="metric.metricCode"
              :metric="metric"
              :disabled="!canEdit"
            />
          </template>

          <template v-else>
            <ProductionDailyReportMetricRow
              v-for="metric in group.metricValues"
              :key="metric.metricCode"
              :metric="metric"
              :disabled="!canEdit"
            />
          </template>
        </div>
      </div>
    </el-card>

    <el-dialog v-model="historyVisible" title="历史日报" width="760px">
      <el-table v-loading="historyLoading" :data="historyRows" border stripe @row-dblclick="chooseHistory">
        <el-table-column prop="reportDate" label="日报日期" width="120" />
        <el-table-column prop="preparedDate" label="制表日期" width="120" />
        <el-table-column prop="preparedByName" label="制表人" min-width="120" />
        <el-table-column label="状态" width="90">
          <template #default="{ row }"><el-tag :type="sectionStatusType(row.status)">{{ statusText(row.status) }}</el-tag></template>
        </el-table-column>
        <el-table-column label="最后修改" min-width="170">
          <template #default="{ row }">{{ formatDateTime(row.updatedAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="80">
          <template #default="{ row }"><el-button link type="primary" @click="chooseHistory(row)">查看</el-button></template>
        </el-table-column>
      </el-table>
      <div class="history-pagination">
        <el-pagination
          v-model:current-page="historyPage"
          :page-size="historySize"
          :total="historyTotal"
          layout="total, prev, pager, next"
          @current-change="loadHistory"
        />
      </div>
    </el-dialog>
  </div>
</template>

<style scoped>
.daily-report-page {
  min-width: 0;
}

.daily-report-header,
.section-card {
  border: 1px solid var(--app-border-soft);
  border-radius: var(--app-radius);
  box-shadow: var(--app-shadow-soft);
}

.daily-report-header {
  margin-bottom: 12px;
}

.daily-report-header :deep(.el-card__body),
.section-card :deep(.el-card__body) {
  padding: 16px 18px;
}

.page-toolbar,
.section-toolbar,
.group-toolbar,
.page-actions,
.section-actions,
.status-line {
  display: flex;
  align-items: center;
}

.page-toolbar,
.section-toolbar,
.group-toolbar {
  justify-content: space-between;
  gap: 12px;
}

.page-toolbar {
  padding-bottom: 14px;
  border-bottom: 1px solid var(--app-border-soft);
}

.page-title {
  font-size: 19px;
  font-weight: 700;
}

.page-actions,
.section-actions {
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 8px;
}

.page-actions :deep(.el-button + .el-button),
.section-actions :deep(.el-button + .el-button) {
  margin-left: 0;
}

.header-fields {
  display: grid;
  grid-template-columns: minmax(270px, 1.35fr) minmax(180px, .8fr) minmax(180px, .8fr) minmax(155px, .65fr);
  gap: 12px;
  padding-top: 13px;
}

.header-field {
  min-width: 0;
}

.header-field label {
  display: block;
  margin-bottom: 6px;
  color: var(--app-text-secondary);
  font-size: 12px;
  font-weight: 500;
}

.header-field :deep(.el-date-editor),
.header-field :deep(.el-input) {
  width: 100%;
}

.date-control {
  display: grid;
  grid-template-columns: 32px minmax(135px, 1fr) 32px;
  gap: 5px;
}

.daily-date-cell {
  width: 28px;
  height: 28px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  transition: color .15s ease, background-color .15s ease, box-shadow .15s ease;
}

.daily-date-cell.is-unfilled {
  color: #7c8798;
  background: #eef0f3;
}

.daily-date-cell.is-draft {
  color: #946200;
  background: #fff0bd;
}

.daily-date-cell.is-submitted {
  color: #237a42;
  background: #dff3e5;
}

.daily-date-cell.is-selected {
  color: #fff;
  background: var(--app-primary);
  box-shadow: 0 3px 8px rgba(22, 93, 255, .25);
}

.daily-date-cell.is-adjacent-month {
  opacity: .55;
}

:global(.production-daily-date-popper .el-picker-panel__sidebar) {
  top: auto;
  right: 8px;
  bottom: 2px;
  left: auto;
  z-index: 2;
  width: 48px;
  height: 26px;
  padding: 0;
  overflow: visible;
  border-right: 0;
  background: transparent;
}

:global(.production-daily-date-popper .el-picker-panel__body) {
  margin-left: 0;
  position: relative;
  padding-bottom: 30px;
}

:global(.production-daily-date-popper .el-picker-panel__content) {
  width: auto;
}

:global(.production-daily-date-popper .el-picker-panel__body::before) {
  content: '';
  width: 8px;
  height: 8px;
  position: absolute;
  left: 18px;
  bottom: 10px;
  z-index: 1;
  border-radius: 50%;
  background: #e8c55b;
  box-shadow: 71px 0 #84c99a, 153px 0 #aeb6c2, 235px 0 var(--app-primary);
}

:global(.production-daily-date-popper .el-picker-panel__body::after) {
  content: '草稿 已提交 未填写 当前选中';
  position: absolute;
  left: 0;
  right: 0;
  bottom: 0;
  z-index: 0;
  box-sizing: border-box;
  height: 30px;
  padding: 5px 60px 0 30px;
  border-top: 1px solid var(--app-border-soft);
  color: var(--app-text-tertiary);
  font-size: 11px;
  line-height: 18px;
  word-spacing: 45px;
  white-space: nowrap;
}

:global(.production-daily-date-popper .el-picker-panel__shortcut) {
  width: 100%;
  height: 26px;
  padding: 4px 8px;
  border-radius: 4px;
  color: var(--app-text-tertiary);
  font-size: 12px;
  font-weight: 400;
  line-height: 18px;
  text-align: center;
  transition: color 0.18s ease, background-color 0.18s ease;
}

:global(.production-daily-date-popper .el-picker-panel__shortcut:hover),
:global(.production-daily-date-popper .el-picker-panel__shortcut:focus-visible) {
  color: var(--app-primary);
  background: var(--app-primary-light);
}

.date-control :deep(.el-button) {
  margin: 0;
  padding: 6px;
}

.status-line {
  min-height: 32px;
  gap: 7px;
}

.status-line span:last-child {
  color: var(--app-text-tertiary);
  font-size: 11px;
}

.section-tabs {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 10px;
  margin-bottom: 12px;
}

.section-tab {
  min-width: 0;
  min-height: 54px;
  padding: 10px 13px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  color: var(--app-text-secondary);
  background: #fff;
  border: 1px solid var(--app-border-soft);
  border-radius: var(--app-radius);
  box-shadow: var(--app-shadow-soft);
  text-align: left;
}

.section-tab.active {
  color: var(--app-primary);
  border-color: #9ebcff;
  box-shadow: 0 5px 18px rgba(22, 93, 255, .09);
}

.section-toolbar {
  margin: -2px 0 12px;
  padding-bottom: 12px;
  border-bottom: 1px solid var(--app-border-soft);
}

.section-title {
  font-size: 16px;
  font-weight: 700;
}

.section-updated {
  margin-top: 3px;
  color: var(--app-text-tertiary);
  font-size: 11px;
}

.report-group {
  margin-top: 12px;
  overflow: hidden;
  border: 1px solid var(--app-border-soft);
  border-radius: 7px;
}

.group-toolbar {
  min-height: 42px;
  padding: 7px 11px;
  background: #fbfcff;
  border-bottom: 1px solid var(--app-border-soft);
}

.report-grid {
  --report-grid-columns: minmax(130px, 1.45fr) 58px repeat(5, minmax(68px, .72fr)) minmax(94px, 1fr) 68px;
  display: grid;
  grid-template-columns: var(--report-grid-columns);
  align-items: stretch;
  min-width: 0;
  overflow: hidden;
}

.product-row {
  grid-column: 1 / -1;
  display: grid;
  grid-template-columns: var(--report-grid-columns);
  min-width: 0;
  transition: opacity .15s ease;
}

.product-row.is-dragging {
  opacity: .45;
}

.product-row.is-drag-over > .report-cell {
  background: #eef4ff;
  box-shadow: inset 0 2px 0 var(--app-primary);
}

.report-head,
.daily-report-page :deep(.report-cell) {
  min-width: 0;
  min-height: 38px;
  padding: 5px;
  display: flex;
  align-items: center;
  border-right: 1px solid var(--app-border-soft);
  border-bottom: 1px solid var(--app-border-soft);
}

.report-head:nth-child(9),
.daily-report-page :deep(.report-cell--operation) {
  border-right: 0;
}

.report-head {
  min-height: 34px;
  justify-content: center;
  color: #657183;
  background: #f7f9fc;
  font-size: 11px;
  font-weight: 600;
  text-align: center;
}

.report-head:first-child {
  justify-content: flex-start;
  padding-left: 10px;
}

.daily-report-page :deep(.report-cell--name) {
  padding-left: 10px;
  font-weight: 500;
}

.daily-report-page :deep(.report-cell--unit),
.daily-report-page :deep(.report-cell--operation) {
  justify-content: center;
}

.daily-report-page :deep(.report-cell--product) {
  align-items: flex-start;
}

.product-selector {
  width: 100%;
  min-width: 0;
}

.product-selector.has-import-error :deep(.el-input__wrapper),
.product-selector.has-import-error :deep(.el-select__wrapper) {
  box-shadow: 0 0 0 1px var(--el-color-danger) inset;
}

.product-import-error {
  margin-top: 3px;
  color: var(--el-color-danger);
  font-size: 11px;
  line-height: 1.2;
}

.daily-report-page :deep(.report-cell--operation) {
  padding-inline: 1px;
  white-space: nowrap;
}

.daily-report-page :deep(.report-cell--operation .el-button) {
  margin: 0;
  padding: 3px;
}

.fixed-product-unit {
  color: var(--app-text-secondary);
  font-size: 12px;
  font-weight: 600;
}

.product-drag-handle {
  width: 24px;
  height: 26px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  color: #7b8798;
  border-radius: 5px;
  cursor: grab;
  user-select: none;
  transition: color .15s ease, background-color .15s ease;
}

.product-drag-handle:hover {
  color: var(--app-primary);
  background: #eef4ff;
}

.product-drag-handle:active {
  cursor: grabbing;
}

.product-drag-handle.is-disabled {
  color: #c0c4cc;
  cursor: not-allowed;
  background: transparent;
}

.daily-report-page :deep(.report-cell .el-input),
.daily-report-page :deep(.report-cell .el-cascader) {
  width: 100%;
  min-width: 0;
}

.daily-report-page :deep(.report-cell .el-input__wrapper),
.daily-report-page :deep(.report-cell .el-select__wrapper) {
  min-height: 28px;
  padding: 1px 7px;
  border-radius: 5px;
}

.daily-report-page :deep(.report-cell input) {
  text-align: right;
  font-size: 12px;
}

.daily-report-page :deep(.report-cell--product input),
.daily-report-page :deep(.report-cell--remark input) {
  text-align: left;
}

.history-pagination {
  display: flex;
  justify-content: flex-end;
  padding-top: 14px;
}

@media (max-width: 1200px) {
  .header-fields {
    grid-template-columns: 1fr 1fr;
  }

  .report-grid {
    --report-grid-columns: minmax(115px, 1.3fr) 50px repeat(5, minmax(62px, .7fr)) minmax(84px, .9fr) 64px;
  }
}

@media (max-width: 900px) {
  .page-toolbar,
  .section-toolbar {
    align-items: flex-start;
    flex-direction: column;
  }

  .page-actions,
  .section-actions {
    justify-content: flex-start;
  }

  .section-tabs {
    grid-template-columns: 1fr 1fr;
  }

  .report-grid {
    grid-template-columns: minmax(100px, 1.3fr) 48px repeat(5, minmax(50px, .7fr)) minmax(70px, .9fr) 72px;
  }
}
</style>
