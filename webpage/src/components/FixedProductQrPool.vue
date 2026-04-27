<template>
  <div class="operation-logs">
    <el-card class="search-card" style="max-width: 1400px">
      <el-form :model="searchForm" inline>
        <el-form-item label="产品">
          <el-select v-model="searchForm.productId" clearable filterable placeholder="请选择产品" style="width: 220px">
            <el-option
              v-for="item in productOptions"
              :key="item.id"
              :label="item.productName"
              :value="item.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="产品名称">
          <el-input v-model="searchForm.productName" clearable placeholder="请输入产品名称" style="width: 200px" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="searchForm.status" clearable placeholder="全部状态" style="width: 160px">
            <el-option
              v-for="item in palletStatusOptions"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-checkbox v-model="searchForm.freeOnly">只看可打印二维码</el-checkbox>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch(1)">查询</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card class="table-card" style="max-width: 1400px">
      <div class="table-toolbar">
        <div>
          <div class="section-title">固定产品二维码池</div>
          <div class="section-subtitle">先完成静态准备，再通过打印并启用正式进入本轮业务。</div>
        </div>
        <div class="toolbar-actions">
          <el-button @click="printerSettingsVisible = true">打印助手设置</el-button>
          <el-button type="primary" @click="bindDialogVisible = true">批量绑定产品</el-button>
          <el-button
            type="primary"
            plain
            :disabled="!selectedRows.length"
            :loading="activateLoading"
            @click="openActivateDialog"
          >
            打印并启用
          </el-button>
          <el-button
            type="primary"
            plain
            :disabled="!selectedRows.length"
            :loading="printLoading"
            @click="handleBatchPrint"
          >
            直接打印
          </el-button>
          <el-button
            plain
            :disabled="!selectedRows.length"
            :loading="pdfLoading"
            @click="handleBatchExportPdf"
          >
            导出 PDF
          </el-button>
        </div>
      </div>

      <el-table
        :data="resultList"
        style="width: 100%"
        stripe
        border
        v-loading="loading"
        @selection-change="handleSelectionChange"
      >
        <el-table-column type="selection" width="45" :selectable="canSelectRow" />
        <el-table-column prop="code" label="二维码内容" min-width="180" />
        <el-table-column prop="fixedProductName" label="产品名称" min-width="180" />
        <el-table-column label="当前状态" width="120">
          <template #default="{ row }">
            <el-tag :type="getDictType(PALLET_STATUS_MAP, row.status)">
              {{ getDictLabel(PALLET_STATUS_MAP, row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="是否可打印" width="120">
          <template #default="{ row }">
            <el-tag :type="row.allowPrint ? 'success' : 'info'">
              {{ row.allowPrint ? '可打印' : '不可打印' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="updatedAt" label="更新时间" width="180">
          <template #default="{ row }">{{ formatDateTime(row.updatedAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="100" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link @click="handlePreview(row)">预览</el-button>
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
          @current-change="handleSearch"
        />
      </div>
    </el-card>

    <el-dialog v-model="bindDialogVisible" title="批量绑定产品" width="420px" destroy-on-close>
      <el-form :model="bindForm" label-width="100px">
        <el-form-item label="产品" required>
          <el-select v-model="bindForm.productId" filterable placeholder="请选择产品" style="width: 100%">
            <el-option
              v-for="item in productOptions"
              :key="item.id"
              :label="item.productName"
              :value="item.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="绑定数量" required>
          <el-input-number v-model="bindForm.num" :min="1" :max="1000" :controls="false" style="width: 100%" />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="closeBindDialog">取消</el-button>
        <el-button type="primary" :loading="bindLoading" @click="submitBind">确认绑定</el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="activateDialogVisible"
      title="打印并启用"
      width="420px"
      destroy-on-close
      @closed="closeActivateDialog"
    >
      <el-form :model="activateForm" label-width="100px">
        <el-form-item label="产品名称">
          <div class="dialog-static-text">{{ activateForm.productName || '-' }}</div>
        </el-form-item>
        <el-form-item label="生产日期" required>
          <el-date-picker
            v-model="activateForm.productionDate"
            type="date"
            value-format="YYYY-MM-DD"
            placeholder="请选择生产日期"
            style="width: 100%"
          />
        </el-form-item>
      </el-form>
      <div class="dialog-tip">
        当前会先导出二维码标签，再按所选二维码创建对应入库任务，后续可继续进入任务中心、入库确认、调拨和出库链路。
      </div>

      <template #footer>
        <el-button @click="closeActivateDialog">取消</el-button>
        <el-button type="primary" :loading="activateLoading" @click="submitActivate">确认打印并启用</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="previewDialogVisible" title="二维码预览" width="380px" destroy-on-close @closed="closePreviewDialog">
      <div class="preview-wrapper" v-loading="previewLoading">
        <div class="preview-code">{{ previewCode || '-' }}</div>
        <el-image
          v-if="previewImageUrl"
          :src="previewImageUrl"
          fit="contain"
          class="preview-image"
          :preview-src-list="[previewImageUrl]"
        />
        <el-empty v-else-if="!previewLoading" description="暂无二维码" :image-size="96" />
      </div>
    </el-dialog>

    <LocalPrinterSettingsDialog v-model="printerSettingsVisible" />
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import LocalPrinterSettingsDialog from '@/components/LocalPrinterSettingsDialog.vue'
import { getProductList } from '@/api/product'
import { buildAssistantErrorMessage, printLocalLabels } from '@/api/localPrinter'
import {
  activateFixedProductQrCodes,
  batchDownloadFixedProductQrLabelPdf,
  bindFixedProductCodes,
  getPalletQrCode,
  pageFixedProductPool
} from '@/api/palletCode'
import { formatDateTime } from '@/utils/dateTime'
import { PALLET_STATUS_MAP, getDictLabel, getDictType } from '@/utils/palletCodeDict'

const createToday = () => {
  const now = new Date()
  const year = now.getFullYear()
  const month = `${now.getMonth() + 1}`.padStart(2, '0')
  const day = `${now.getDate()}`.padStart(2, '0')
  return `${year}-${month}-${day}`
}

const searchForm = ref({
  productId: undefined,
  productName: '',
  status: '',
  freeOnly: true
})

const bindForm = ref({
  productId: undefined,
  num: 100
})

const activateForm = ref({
  productId: undefined,
  productName: '',
  productionDate: createToday()
})

const loading = ref(false)
const bindLoading = ref(false)
const printLoading = ref(false)
const pdfLoading = ref(false)
const activateLoading = ref(false)
const previewLoading = ref(false)
const bindDialogVisible = ref(false)
const activateDialogVisible = ref(false)
const previewDialogVisible = ref(false)
const printerSettingsVisible = ref(false)
const previewCode = ref('')
const previewImageUrl = ref('')
const resultList = ref([])
const selectedRows = ref([])
const productList = ref([])
const currentPage = ref(1)
const pageSize = ref(10)
const total = ref(0)

const productOptions = computed(() => productList.value || [])
const palletStatusOptions = Object.entries(PALLET_STATUS_MAP).map(([value, item]) => ({
  value,
  label: item.label
}))

const buildQuery = () => ({
  productId: searchForm.value.productId,
  productName: searchForm.value.productName || undefined,
  status: searchForm.value.status || undefined,
  freeOnly: searchForm.value.freeOnly,
  page: currentPage.value,
  size: pageSize.value
})

const normalizeCode = code => String(code || '').trim().toUpperCase()

const buildLabelsByCodes = codes => {
  const rowMap = new Map(resultList.value.map(item => [normalizeCode(item.code), item]))
  return Array.from(new Set((codes || []).map(normalizeCode).filter(Boolean))).map(code => {
    const row = rowMap.get(code)
    return {
      code,
      title: row?.fixedProductName || activateForm.value.productName || ''
    }
  })
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

const revokePreviewUrl = () => {
  if (previewImageUrl.value) {
    URL.revokeObjectURL(previewImageUrl.value)
    previewImageUrl.value = ''
  }
}

const loadProducts = async () => {
  const res = await getProductList({})
  productList.value = Array.isArray(res.data) ? res.data : []
}

const handleSearch = async (page = currentPage.value) => {
  currentPage.value = page
  loading.value = true
  try {
    const res = await pageFixedProductPool(buildQuery())
    resultList.value = res.data?.records || []
    total.value = res.data?.total || 0
    selectedRows.value = []
  } finally {
    loading.value = false
  }
}

const handleReset = () => {
  searchForm.value = {
    productId: undefined,
    productName: '',
    status: '',
    freeOnly: true
  }
  handleSearch(1)
}

const handleSizeChange = size => {
  pageSize.value = size
  handleSearch(1)
}

const handleSelectionChange = rows => {
  selectedRows.value = rows
}

const canSelectRow = row => !!row.allowPrint

const closeBindDialog = () => {
  bindDialogVisible.value = false
  bindForm.value = {
    productId: undefined,
    num: 100
  }
}

const closeActivateDialog = () => {
  activateDialogVisible.value = false
  activateForm.value = {
    productId: undefined,
    productName: '',
    productionDate: createToday()
  }
}

const closePreviewDialog = () => {
  previewDialogVisible.value = false
  previewLoading.value = false
  previewCode.value = ''
  revokePreviewUrl()
}

const submitBind = async () => {
  if (!bindForm.value.productId) {
    ElMessage.warning('请选择产品')
    return
  }
  if (!bindForm.value.num || bindForm.value.num < 1) {
    ElMessage.warning('绑定数量必须大于 0')
    return
  }

  bindLoading.value = true
  try {
    const res = await bindFixedProductCodes(bindForm.value)
    ElMessage.success(`已绑定 ${res.data || bindForm.value.num} 个二维码`)
    closeBindDialog()
    await handleSearch(1)
  } finally {
    bindLoading.value = false
  }
}

const exportPdfByCodes = async codes => {
  if (!codes.length) {
    ElMessage.warning('请选择可打印的二维码')
    return
  }
  pdfLoading.value = true
  try {
    const response = await batchDownloadFixedProductQrLabelPdf(codes)
    downloadBlob(response.data, `fixed-product-qrcodes-${Date.now()}.pdf`)
    ElMessage.success(`已导出 ${codes.length} 个二维码标签`)
  } finally {
    pdfLoading.value = false
  }
}

const printCodes = async codes => {
  if (!codes.length) {
    ElMessage.warning('请选择可打印的二维码')
    return
  }
  printLoading.value = true
  try {
    const labels = buildLabelsByCodes(codes)
    const response = await printLocalLabels({
      template: 'fixed_product_qrcode',
      copies: 1,
      labels
    })
    const printedCount = response.data?.printedCount || labels.length
    ElMessage.success(`已提交 ${printedCount} 张标签到打印机`)
  } catch (error) {
    await exportPdfByCodes(codes)
    ElMessage.warning(`${buildAssistantErrorMessage(error)}，已回退为 PDF 导出`)
  } finally {
    printLoading.value = false
  }
}

const ensureSelectedSameProduct = () => {
  if (!selectedRows.value.length) {
    ElMessage.warning('请选择要启用的二维码')
    return null
  }
  const first = selectedRows.value[0]
  const inconsistent = selectedRows.value.some(item => item.fixedProductId !== first.fixedProductId)
  if (inconsistent) {
    ElMessage.warning('打印并启用时只能选择同一产品的二维码')
    return null
  }
  return first
}

const openActivateDialog = () => {
  const first = ensureSelectedSameProduct()
  if (!first) {
    return
  }
  activateForm.value = {
    productId: first.fixedProductId,
    productName: first.fixedProductName || '-',
    productionDate: createToday()
  }
  activateDialogVisible.value = true
}

const submitActivate = async () => {
  const first = ensureSelectedSameProduct()
  if (!first) {
    return
  }
  if (!activateForm.value.productionDate) {
    ElMessage.warning('请选择生产日期')
    return
  }

  activateLoading.value = true
  try {
    const labels = buildLabelsByCodes(selectedRows.value.map(item => item.code))
    const response = await activateFixedProductQrCodes({
      codes: selectedRows.value.map(item => item.code),
      productionDate: activateForm.value.productionDate
    })
    try {
      const printRes = await printLocalLabels({
        template: 'fixed_product_qrcode',
        copies: 1,
        labels
      })
      const printedCount = printRes.data?.printedCount || labels.length
      ElMessage.success(`已启用并提交 ${printedCount} 张标签到打印机`)
    } catch (error) {
      downloadBlob(response.data, `fixed-product-qrcodes-activated-${Date.now()}.pdf`)
      ElMessage.warning(`${buildAssistantErrorMessage(error)}，已完成启用并回退导出 PDF`)
    }
    closeActivateDialog()
    await handleSearch(1)
  } finally {
    activateLoading.value = false
  }
}

const handleBatchPrint = async () => {
  await printCodes(selectedRows.value.map(item => item.code))
}

const handleBatchExportPdf = async () => {
  await exportPdfByCodes(selectedRows.value.map(item => normalizeCode(item.code)))
}

const handlePreview = async row => {
  previewDialogVisible.value = true
  previewLoading.value = true
  previewCode.value = row.code
  revokePreviewUrl()
  try {
    const response = await getPalletQrCode(row.code)
    previewImageUrl.value = URL.createObjectURL(response.data)
  } catch (error) {
    previewDialogVisible.value = false
    ElMessage.error(error?.response?.data?.message || '二维码预览加载失败')
  } finally {
    previewLoading.value = false
  }
}

onMounted(async () => {
  await loadProducts()
  await handleSearch(1)
})

onBeforeUnmount(() => {
  revokePreviewUrl()
})
</script>

<style scoped>
.operation-logs {
  padding: 0;
}

.search-card {
  margin-bottom: 20px;
  background: var(--app-panel);
}

.table-card {
  background: var(--app-panel);
}

.table-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 16px;
}

.toolbar-actions {
  display: flex;
  gap: 12px;
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
  margin-top: 20px;
  display: flex;
  justify-content: flex-end;
}

.dialog-static-text {
  min-height: 32px;
  display: flex;
  align-items: center;
  color: var(--app-text);
  font-weight: 600;
}

.dialog-tip {
  padding: 8px 0 0;
  color: var(--app-text-tertiary);
  font-size: 13px;
  line-height: 1.6;
}

.preview-wrapper {
  min-height: 260px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 16px;
}

.preview-code {
  color: var(--app-text);
  font-size: 14px;
  font-weight: 600;
  word-break: break-all;
  text-align: center;
}

.preview-image {
  width: 240px;
  height: 240px;
}

:deep(.el-table) {
  --el-table-border-color: var(--app-border-soft);
}

:deep(.el-table__header th) {
  background-color: #f7f8fb;
  color: var(--app-text-secondary);
}

:deep(.el-table__body tr:hover > td) {
  background-color: var(--app-hover) !important;
}
</style>
