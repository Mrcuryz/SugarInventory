<template>
  <div class="location-code-page">
    <section class="page-hero">
      <div>
        <div class="hero-eyebrow">二维码中心</div>
        <h1>库位码打印</h1>
        <p>每个库位对应一个固定二维码。打印后张贴在库位醒目位置，现场扫码即可确认作业库位。</p>
      </div>
      <div class="hero-stats">
        <div class="stat-item">
          <strong>{{ warehouses.length }}</strong>
          <span>全部库位</span>
        </div>
        <div class="stat-divider" />
        <div class="stat-item">
          <strong>{{ normalCount }}</strong>
          <span>正常使用</span>
        </div>
        <div class="stat-divider" />
        <div class="stat-item selected">
          <strong>{{ selectedRows.length }}</strong>
          <span>已选择</span>
        </div>
      </div>
    </section>

    <el-card class="filter-card" shadow="never">
      <el-form :model="searchForm" inline @submit.prevent="loadWarehouses">
        <el-form-item label="库位名称">
          <el-input
            v-model="searchForm.name"
            clearable
            placeholder="输入名称查找库位"
            style="width: 220px"
            @keyup.enter="loadWarehouses"
          />
        </el-form-item>
        <el-form-item label="使用状态">
          <el-select v-model="searchForm.status" clearable placeholder="全部状态" style="width: 150px">
            <el-option v-for="item in statusOptions" :key="item" :label="item" :value="item" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="loading" @click="loadWarehouses">查询</el-button>
          <el-button @click="resetSearch">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card class="table-card" shadow="never">
      <div class="table-toolbar">
        <div>
          <div class="toolbar-title">选择需要打印的库位</div>
          <div class="toolbar-note">支持批量打印；未连接打印助手时自动提供 PDF 文件。</div>
        </div>
        <div class="toolbar-actions">
          <el-button @click="printerSettingsVisible = true">打印助手设置</el-button>
          <el-button
            type="primary"
            plain
            :disabled="!selectedRows.length"
            :loading="batchPdfLoading"
            @click="downloadSelectedPdf"
          >
            导出标签 PDF
          </el-button>
          <el-button
            type="primary"
            :disabled="!selectedRows.length"
            :loading="batchPrintLoading"
            @click="printSelected"
          >
            直接打印{{ selectedRows.length ? `（${selectedRows.length}）` : '' }}
          </el-button>
        </div>
      </div>

      <el-table
        ref="tableRef"
        v-loading="loading"
        :data="filteredWarehouses"
        row-key="id"
        stripe
        empty-text="暂无库位"
        @selection-change="selectedRows = $event"
      >
        <el-table-column type="selection" width="52" reserve-selection />
        <el-table-column prop="warehouseName" label="库位名称" min-width="180">
          <template #default="{ row }">
            <div class="warehouse-name-cell">
              <span class="location-mark"><el-icon><Location /></el-icon></span>
              <div>
                <strong>{{ row.warehouseName || '-' }}</strong>
                <small>唯一库位码</small>
              </div>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="120">
          <template #default="{ row }">
            <el-tag :type="statusType(row.status)" effect="light">{{ row.status || '未知' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="当前容量" min-width="210">
          <template #default="{ row }">
            <div class="capacity-cell">
              <span>{{ capacityText(row) }}</span>
              <el-progress
                v-if="capacityPercent(row) !== null"
                :percentage="capacityPercent(row)"
                :stroke-width="6"
                :show-text="false"
                :status="capacityPercent(row) >= 90 ? 'exception' : undefined"
              />
            </div>
          </template>
        </el-table-column>
        <el-table-column label="标签" width="120">
          <template #default="{ row }">
            <span class="ready-label"><span class="ready-dot" />可生成</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="250" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link :loading="previewLoadingId === row.id" @click="previewCode(row)">
              预览
            </el-button>
            <el-button type="primary" link :loading="printLoadingId === row.id" @click="printOne(row)">
              直接打印
            </el-button>
            <el-dropdown trigger="click" @command="command => handleDownload(row, command)">
              <el-button type="primary" link>下载</el-button>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item command="pdf">标签 PDF</el-dropdown-item>
                  <el-dropdown-item command="png">二维码 PNG</el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="previewVisible" title="库位码预览" width="420px" destroy-on-close @closed="clearPreview">
      <div class="preview-panel">
        <div class="preview-location">{{ previewWarehouse?.warehouseName || '-' }}</div>
        <el-image v-if="previewImageUrl" :src="previewImageUrl" fit="contain" class="preview-image" />
        <div class="preview-tip">打印后请张贴在库位入口或便于扫码的位置</div>
      </div>
      <template #footer>
        <el-button @click="previewVisible = false">关闭</el-button>
        <el-button type="primary" :loading="printLoadingId === previewWarehouse?.id" @click="printOne(previewWarehouse)">
          直接打印
        </el-button>
      </template>
    </el-dialog>

    <LocalPrinterSettingsDialog v-model="printerSettingsVisible" />
  </div>
</template>

<script setup>
import {computed, onBeforeUnmount, onMounted, reactive, ref} from 'vue'
import {ElMessage} from 'element-plus'
import {Location} from '@element-plus/icons-vue'
import LocalPrinterSettingsDialog from '@/components/LocalPrinterSettingsDialog.vue'
import {
  batchDownloadWarehouseLocationLabelsPdf,
  downloadWarehouseLocationLabelPdf,
  downloadWarehouseLocationQrPng,
  getWarehouse,
  getWarehouseLocationCodes
} from '@/api/warehouse'
import {buildAssistantErrorMessage, printLocalLabels} from '@/api/localPrinter'
import {
  buildWarehouseLocationLabels,
  capacityText,
  selectedWarehouseIds
} from '@/utils/warehouseLocationLabels.mjs'

const searchForm = reactive({name: '', status: ''})
const warehouses = ref([])
const selectedRows = ref([])
const loading = ref(false)
const batchPrintLoading = ref(false)
const batchPdfLoading = ref(false)
const printLoadingId = ref(null)
const previewLoadingId = ref(null)
const printerSettingsVisible = ref(false)
const previewVisible = ref(false)
const previewWarehouse = ref(null)
const previewImageUrl = ref('')
const tableRef = ref(null)

const statusOptions = computed(() => [...new Set(warehouses.value.map(item => item.status).filter(Boolean))])
const filteredWarehouses = computed(() => searchForm.status
  ? warehouses.value.filter(item => item.status === searchForm.status)
  : warehouses.value)
const normalCount = computed(() => warehouses.value.filter(item => item.status === '正常').length)

const statusType = status => {
  if (status === '正常') return 'success'
  if (status === '维修') return 'warning'
  if (status === '已停用' || status === '停用') return 'danger'
  return 'info'
}

const capacityPercent = warehouse => {
  const current = Number(warehouse?.curCapacity)
  const maximum = Number(warehouse?.maxCapacity)
  if (!Number.isFinite(current) || !Number.isFinite(maximum) || maximum <= 0) return null
  return Math.min(100, Math.max(0, Math.round(current / maximum * 100)))
}

const loadWarehouses = async () => {
  loading.value = true
  try {
    const response = await getWarehouse({name: searchForm.name.trim() || undefined})
    warehouses.value = Array.isArray(response.data) ? response.data : []
    selectedRows.value = []
    tableRef.value?.clearSelection()
  } catch (error) {
    warehouses.value = []
  } finally {
    loading.value = false
  }
}

const resetSearch = async () => {
  searchForm.name = ''
  searchForm.status = ''
  await loadWarehouses()
}

const downloadBlob = (blob, filename) => {
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = filename
  document.body.appendChild(link)
  link.click()
  link.remove()
  URL.revokeObjectURL(url)
}

const labelsForRows = async rows => {
  const warehouseIds = selectedWarehouseIds(rows)
  if (!warehouseIds.length) throw new Error('请先选择库位')
  const response = await getWarehouseLocationCodes(warehouseIds)
  const labels = buildWarehouseLocationLabels(response.data)
  if (labels.length !== warehouseIds.length) throw new Error('部分库位码生成失败，请刷新后重试')
  return {warehouseIds, labels}
}

const downloadPdfByRows = async (rows, showSuccess = true) => {
  const warehouseIds = selectedWarehouseIds(rows)
  if (!warehouseIds.length) {
    ElMessage.warning('请先选择库位')
    return false
  }
  const response = warehouseIds.length === 1
    ? await downloadWarehouseLocationLabelPdf(warehouseIds[0])
    : await batchDownloadWarehouseLocationLabelsPdf(warehouseIds)
  downloadBlob(response.data, `库位码标签-${new Date().toISOString().slice(0, 10)}.pdf`)
  if (showSuccess) ElMessage.success(`已导出 ${warehouseIds.length} 张库位标签`)
  return true
}

const directPrint = async (rows, single = false) => {
  if (single) printLoadingId.value = rows[0]?.id
  else batchPrintLoading.value = true
  try {
    const {labels} = await labelsForRows(rows)
    const response = await printLocalLabels({
      template: 'warehouse_location_qrcode',
      copies: 1,
      labels
    })
    ElMessage.success(`已提交 ${response.data?.printedCount || labels.length} 张库位标签到打印机`)
  } catch (error) {
    try {
      const downloaded = await downloadPdfByRows(rows, false)
      if (downloaded) ElMessage.warning(`${buildAssistantErrorMessage(error)}，已改为导出 PDF`)
    } catch (downloadError) {
      ElMessage.error(error?.message || '库位标签打印失败')
    }
  } finally {
    if (single) printLoadingId.value = null
    else batchPrintLoading.value = false
  }
}

const printSelected = () => directPrint(selectedRows.value)
const printOne = row => row && directPrint([row], true)

const downloadSelectedPdf = async () => {
  batchPdfLoading.value = true
  try {
    await downloadPdfByRows(selectedRows.value)
  } catch (error) {
    ElMessage.error('库位标签导出失败')
  } finally {
    batchPdfLoading.value = false
  }
}

const handleDownload = async (row, command) => {
  try {
    const response = command === 'png'
      ? await downloadWarehouseLocationQrPng(row.id)
      : await downloadWarehouseLocationLabelPdf(row.id)
    downloadBlob(response.data, `${row.warehouseName || `库位-${row.id}`}.${command}`)
    ElMessage.success(command === 'png' ? '二维码图片已下载' : '库位标签已下载')
  } catch (error) {
    ElMessage.error('文件下载失败')
  }
}

const clearPreview = () => {
  if (previewImageUrl.value) URL.revokeObjectURL(previewImageUrl.value)
  previewImageUrl.value = ''
  previewWarehouse.value = null
}

const previewCode = async row => {
  previewLoadingId.value = row.id
  try {
    if (previewImageUrl.value) URL.revokeObjectURL(previewImageUrl.value)
    const response = await downloadWarehouseLocationQrPng(row.id)
    previewImageUrl.value = URL.createObjectURL(response.data)
    previewWarehouse.value = row
    previewVisible.value = true
  } catch (error) {
    ElMessage.error('库位码预览失败')
  } finally {
    previewLoadingId.value = null
  }
}

onMounted(loadWarehouses)
onBeforeUnmount(clearPreview)
</script>

<style scoped>
.location-code-page {
  width: min(1400px, 100%);
  margin: 0 auto;
}

.page-hero {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 28px;
  padding: 28px 32px;
  margin-bottom: 16px;
  overflow: hidden;
  color: #fff;
  background: linear-gradient(130deg, #155bd7 0%, #2d82f2 58%, #20bdb0 120%);
  border-radius: 16px;
  box-shadow: 0 12px 30px rgba(35, 106, 214, 0.18);
}

.hero-eyebrow {
  margin-bottom: 8px;
  font-size: 12px;
  letter-spacing: 0.18em;
  opacity: 0.78;
}

.page-hero h1 {
  margin: 0;
  font-size: 28px;
}

.page-hero p {
  max-width: 680px;
  margin: 10px 0 0;
  font-size: 14px;
  line-height: 1.7;
  opacity: 0.9;
}

.hero-stats {
  display: flex;
  align-items: center;
  flex: 0 0 auto;
  padding: 16px 20px;
  background: rgba(255, 255, 255, 0.13);
  border: 1px solid rgba(255, 255, 255, 0.2);
  border-radius: 14px;
  backdrop-filter: blur(8px);
}

.stat-item {
  min-width: 74px;
  text-align: center;
}

.stat-item strong,
.stat-item span {
  display: block;
}

.stat-item strong {
  font-size: 24px;
}

.stat-item span {
  margin-top: 3px;
  font-size: 11px;
  opacity: 0.78;
}

.stat-item.selected strong {
  color: #bffbf1;
}

.stat-divider {
  width: 1px;
  height: 34px;
  margin: 0 12px;
  background: rgba(255, 255, 255, 0.2);
}

.filter-card,
.table-card {
  border: 1px solid #e6edf7;
  border-radius: 14px;
}

.filter-card {
  margin-bottom: 16px;
}

.filter-card :deep(.el-card__body) {
  padding-bottom: 2px;
}

.table-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
  margin-bottom: 18px;
}

.toolbar-title {
  color: #1f2937;
  font-size: 16px;
  font-weight: 700;
}

.toolbar-note {
  margin-top: 5px;
  color: #8492a6;
  font-size: 12px;
}

.toolbar-actions {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 8px;
}

.warehouse-name-cell {
  display: flex;
  align-items: center;
  gap: 12px;
}

.warehouse-name-cell strong,
.warehouse-name-cell small {
  display: block;
}

.warehouse-name-cell strong {
  color: #1f2937;
  font-size: 14px;
}

.warehouse-name-cell small {
  margin-top: 3px;
  color: #9aa6b5;
  font-size: 11px;
}

.location-mark {
  display: grid;
  width: 36px;
  height: 36px;
  color: #2477e8;
  background: #edf5ff;
  border-radius: 10px;
  place-items: center;
}

.capacity-cell {
  width: 160px;
  color: #526174;
  font-size: 13px;
}

.capacity-cell :deep(.el-progress) {
  margin-top: 7px;
}

.ready-label {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  color: #399b76;
  font-size: 13px;
}

.ready-dot {
  width: 7px;
  height: 7px;
  background: #35bd89;
  border-radius: 50%;
  box-shadow: 0 0 0 4px rgba(53, 189, 137, 0.12);
}

.preview-panel {
  padding: 8px 16px 4px;
  text-align: center;
}

.preview-location {
  margin-bottom: 14px;
  color: #1f2937;
  font-size: 20px;
  font-weight: 700;
}

.preview-image {
  width: 260px;
  height: 260px;
  padding: 12px;
  background: #fff;
  border: 1px solid #e6edf7;
  border-radius: 14px;
}

.preview-tip {
  margin-top: 14px;
  color: #8492a6;
  font-size: 12px;
}

@media (max-width: 900px) {
  .page-hero,
  .table-toolbar {
    align-items: flex-start;
    flex-direction: column;
  }

  .hero-stats {
    width: 100%;
    box-sizing: border-box;
    justify-content: center;
  }

  .toolbar-actions {
    justify-content: flex-start;
  }
}
</style>
