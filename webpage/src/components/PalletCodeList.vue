<template>
  <div class="operation-logs">
    <el-card class="search-card" style="max-width: 1400px">
      <el-form :model="searchForm" inline>
        <el-form-item label="二维码">
          <el-input v-model="searchForm.code" clearable placeholder="请输入二维码" style="width: 180px"/>
        </el-form-item>
        <el-form-item label="当前状态">
          <el-select v-model="searchForm.status" clearable placeholder="请选择" style="width: 150px">
            <el-option v-for="item in palletStatusOptions" :key="item.value" :label="item.label" :value="item.value"/>
          </el-select>
        </el-form-item>
        <el-form-item label="产品名称">
          <el-input v-model="searchForm.productName" clearable placeholder="请输入产品名称" style="width: 180px"/>
        </el-form-item>
        <el-form-item label="产品类型">
          <el-select v-model="searchForm.productType" clearable placeholder="请选择" style="width: 150px">
            <el-option v-for="item in PRODUCT_TYPE_OPTIONS" :key="item.value" :label="item.label" :value="item.value"/>
          </el-select>
        </el-form-item>
        <el-form-item label="产品状态">
          <el-select v-model="searchForm.productStatus" clearable placeholder="请选择" style="width: 150px">
            <el-option v-for="item in PRODUCT_STATUS_OPTIONS" :key="item.value" :label="item.label" :value="item.value"/>
          </el-select>
        </el-form-item>
      </el-form>
      <el-form :model="searchForm" inline>
        <el-form-item label="生产日期">
          <el-date-picker
              v-model="searchForm.productionDateRange"
              type="daterange"
              value-format="YYYY-MM-DD"
              range-separator="至"
              start-placeholder="开始日期"
              end-placeholder="结束日期"
              style="width: 260px"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">查询</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card class="table-card" style="max-width: 1400px">
      <div class="table-toolbar">
        <div class="table-toolbar-left">
          <el-button v-if="canPrint" @click="printerSettingsVisible = true">打印助手设置</el-button>
          <el-button v-if="canGenerate" type="primary" @click="openGenerateDialog">生成二维码</el-button>
          <el-button v-if="canInvalidate" type="danger" :disabled="!selectedRows.length" @click="handleBatchInvalid">批量作废</el-button>
          <el-button
              v-if="canPrint"
              type="primary"
              plain
              :loading="batchPrintLoading"
              :disabled="!selectedRows.length || batchPrintLoading"
              @click="handleBatchDirectPrint()"
          >
            批量直接打印
          </el-button>
          <el-button
              v-if="canPrint"
              type="primary"
              plain
              :loading="batchPdfLoading"
              :disabled="!selectedRows.length || batchPdfLoading"
              @click="handleBatchDownloadPdf()"
          >
            批量导出标签 PDF
          </el-button>
        </div>
      </div>
      <el-table
          :data="resultList"
          style="width: 100%"
          height="560"
          stripe
          v-loading="loading"
          @selection-change="handleSelectionChange"
      >
        <el-table-column type="selection" width="45"/>
        <el-table-column prop="code" label="二维码" width="130" fixed="left" show-overflow-tooltip/>
        <el-table-column prop="status" label="二维码状态" width="100">
          <template #default="{ row }">
            <el-tag :type="getDictType(PALLET_STATUS_MAP, row.status)">
              {{ getDictLabel(PALLET_STATUS_MAP, row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="productName" label="产品名称" min-width="140" show-overflow-tooltip/>
        <el-table-column prop="productType" label="产品类型" width="90" show-overflow-tooltip/>
        <el-table-column prop="productStatus" label="产品状态" width="100" show-overflow-tooltip/>
        <el-table-column prop="productionDate" label="生产日期" width="120" show-overflow-tooltip>
          <template #default="{ row }">
            <span class="no-ellipsis-cell" :title="row.productionDate || '-'">{{ row.productionDate || '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="screenMeshName" label="筛网" width="100" show-overflow-tooltip/>
        <el-table-column prop="createdAt" label="创建时间" width="170" show-overflow-tooltip>
          <template #default="{ row }">
            <span :title="formatDateTime(row.createdAt)">{{ formatDateTime(row.createdAt) }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="updatedAt" label="更新时间" width="170" show-overflow-tooltip>
          <template #default="{ row }">
            <span :title="formatDateTime(row.updatedAt)">{{ formatDateTime(row.updatedAt) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="290" fixed="right" align="left" header-align="left">
          <template #default="{ row }">
            <div class="row-actions">
              <el-button type="primary" link @click="openQrDialog(row)">二维码</el-button>
              <el-dropdown v-if="canPrint" trigger="click" @command="command => handleQrAction(row, command)">
                <el-button type="primary" link :loading="qrDownloadLoading">
                  下载
                </el-button>
                <template #dropdown>
                  <el-dropdown-menu>
                    <el-dropdown-item command="print">直接打印标签</el-dropdown-item>
                    <el-dropdown-item command="pdf">下载标签 PDF</el-dropdown-item>
                    <el-dropdown-item command="png">下载 PNG</el-dropdown-item>
                    <el-dropdown-item command="svg">下载 SVG</el-dropdown-item>
                  </el-dropdown-menu>
                </template>
              </el-dropdown>
              <el-button v-if="canShowTaskAction(row)" type="primary" link @click="goTaskCenter(row)">任务</el-button>
              <el-button v-if="canShowAssayAction(row)" type="primary" link @click="openAssayDialog(row)">化验</el-button>
              <el-button v-if="canShowLocationAction(row)" type="primary" link @click="goWarehouseMap(row)">位置</el-button>
              <el-button v-if="canShowFlowAction(row)" type="primary" link @click="openFlowDrawer(row)">流转</el-button>
              <el-button v-if="canInvalidate && canShowInvalidAction(row)" type="danger" link @click="handleInvalid(row)">作废</el-button>
              <el-button v-if="canInvalidate && canShowRestoreInvalidAction(row)" type="warning" link @click="handleRestoreInvalid(row)">取消作废</el-button>
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
            @size-change="handleSizeChange"
            @current-change="handleCurrentChange"
        />
      </div>
    </el-card>

    <el-dialog title="生成二维码" v-model="generateDialogVisible" width="420px" :before-close="closeGenerateDialog">
      <el-form :model="generateForm" label-width="100px">
        <el-form-item label="生成数量" required>
          <el-input-number v-model="generateForm.count" :min="1" :max="100"/>
        </el-form-item>
      </el-form>
      <div v-if="generatedCodes.length" class="code-result">
        <el-tag v-for="code in generatedCodes" :key="code" class="code-tag">{{ code }}</el-tag>
        <el-button
            v-if="canPrint"
            type="primary"
            plain
            size="small"
            :loading="generatedPdfLoading"
            :disabled="generatedPdfLoading"
            @click="handleBatchDownloadPdf(generatedCodes, true)"
        >
          导出本次生成标签 PDF
        </el-button>
      </div>
      <template #footer>
        <el-button @click="closeGenerateDialog">关闭</el-button>
        <el-button type="primary" @click="submitGenerate">生成</el-button>
      </template>
    </el-dialog>

    <el-dialog title="二维码" v-model="qrDialogVisible" width="360px" :before-close="closeQrDialog">
      <div class="qr-wrapper">
        <div class="qr-code">{{ currentCode }}</div>
        <el-image v-if="qrImageUrl" :src="qrImageUrl" fit="contain" class="qr-image"/>
        <div v-if="canPrint" class="qr-download-actions">
          <el-button type="success" plain :loading="qrPrintLoading" @click="handleDirectPrintByCodes([currentCode])">
            直接打印标签
          </el-button>
          <el-button type="primary" :loading="qrDownloadLoading" @click="handleQrDownload(currentCode, 'pdf')">
            下载标签 PDF
          </el-button>
          <el-button :loading="qrDownloadLoading" @click="handleQrDownload(currentCode, 'png')">下载 PNG</el-button>
          <el-button :loading="qrDownloadLoading" @click="handleQrDownload(currentCode, 'svg')">下载 SVG</el-button>
        </div>
      </div>
    </el-dialog>

    <el-dialog title="化验数据" v-model="assayDialogVisible" width="560px">
      <el-descriptions v-if="assayInfo" :column="1" border>
        <el-descriptions-item label="产品名称">{{ assayInfo.productName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="采样日期">{{ assayInfo.sampleDate || '-' }}</el-descriptions-item>
        <el-descriptions-item label="色值">{{ assayInfo.colorValue || '-' }}</el-descriptions-item>
        <el-descriptions-item label="还原糖分">{{ assayInfo.reducingSugar || '-' }}</el-descriptions-item>
        <el-descriptions-item label="干燥失重">{{ assayInfo.dryWeight || '-' }}</el-descriptions-item>
        <el-descriptions-item label="电导灰分">{{ assayInfo.conductivityAsh || '-' }}</el-descriptions-item>
        <el-descriptions-item label="蔗糖分">{{ assayInfo.sucrose || '-' }}</el-descriptions-item>
        <el-descriptions-item label="不溶于水杂质">{{ assayInfo.insolubleImpurity || '-' }}</el-descriptions-item>
        <el-descriptions-item label="pH值">{{ assayInfo.phValue || '-' }}</el-descriptions-item>
        <el-descriptions-item label="化验员">{{ assayInfo.testerName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="是否合格">{{ assayInfo.isQualified || '-' }}</el-descriptions-item>
      </el-descriptions>
      <el-empty v-else description="暂无化验数据"/>
    </el-dialog>

    <el-dialog title="新增化验" v-model="assayCreateDialogVisible" width="560px">
      <el-form :model="assayCreateForm" label-width="110px">
        <el-form-item label="化验产品">
          <el-cascader
              v-model="assayCreateForm.productId"
              :options="productOptions"
              :props="productCascaderProps"
              clearable
              filterable
              placeholder="请选择化验产品"
              style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="采样日期">
          <el-date-picker v-model="assayCreateForm.sampleDate" value-format="YYYY-MM-DD" type="date" style="width: 100%"/>
        </el-form-item>
        <el-form-item label="色值"><el-input v-model="assayCreateForm.colorValue" /></el-form-item>
        <el-form-item label="还原糖分"><el-input v-model="assayCreateForm.reducingSugar" /></el-form-item>
        <el-form-item label="干燥失重"><el-input v-model="assayCreateForm.dryWeight" /></el-form-item>
        <el-form-item label="电导灰分"><el-input v-model="assayCreateForm.conductivityAsh" /></el-form-item>
        <el-form-item label="蔗糖分"><el-input v-model="assayCreateForm.sucrose" /></el-form-item>
        <el-form-item label="不溶于水杂质"><el-input v-model="assayCreateForm.insolubleImpurity" /></el-form-item>
        <el-form-item label="pH值"><el-input v-model="assayCreateForm.phValue" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="assayCreateDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="assayCreateSubmitting" @click="submitAssayCreate">提交</el-button>
      </template>
    </el-dialog>

    <el-dialog title="库存位置" v-model="inventoryDialogVisible" width="520px">
      <el-descriptions v-if="inventoryInfo" :column="1" border>
        <el-descriptions-item label="仓库">{{ inventoryInfo.warehouseName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="侧别">{{ inventoryInfo.side || '-' }}</el-descriptions-item>
        <el-descriptions-item label="排">{{ inventoryInfo.rowNumber || '-' }}</el-descriptions-item>
        <el-descriptions-item label="层">{{ inventoryInfo.layer || '-' }}</el-descriptions-item>
        <el-descriptions-item label="数量">{{ formatInventoryOccupancy(inventoryInfo) }}</el-descriptions-item>
        <el-descriptions-item label="入库时间">{{ formatDateTime(inventoryInfo.inStockTime) }}</el-descriptions-item>
      </el-descriptions>
      <el-empty v-else description="暂无库存位置"/>
    </el-dialog>

    <el-drawer v-model="flowDrawerVisible" title="流转记录" size="70%" :before-close="closeFlowDrawer">
      <div class="flow-drawer">
        <div class="cycle-panel">
          <div class="panel-title">{{ currentCode }} 的循环轮次</div>
          <div class="cycle-list" v-loading="cycleLoading">
            <div class="cycle-list-header">
              <span>轮次</span>
              <span>产品</span>
              <span>状态</span>
              <span>记录数</span>
            </div>
            <button
                v-for="row in cycleList"
                :key="row.cycleNo"
                class="cycle-item"
                :class="{ active: selectedCycle?.cycleNo === row.cycleNo }"
                type="button"
                @click="selectCycle(row)"
            >
              <span class="cycle-no">{{ displayCycleNo(row.cycleNo) }}{{ row.isCurrentCycle ? '*' : '' }}</span>
              <span class="cycle-product" :title="row.productName || '-'">{{ row.productName || '-' }}</span>
              <span class="cycle-status">{{ row.productStatus || '-' }}</span>
              <span class="cycle-count">{{ row.flowCount ?? 0 }}</span>
            </button>
            <el-empty v-if="!cycleLoading && !cycleList.length" description="暂无轮次" :image-size="72"/>
          </div>
          <div class="pagination-wrapper">
            <el-pagination
                small
                background
                layout="prev, pager, next"
                :total="cycleTotal"
                :current-page="cyclePageNum"
                :page-size="cyclePageSize"
                @current-change="handleCyclePageChange"
            />
          </div>
        </div>
        <div class="flow-panel">
          <div class="panel-title">
            第 {{ displayCycleNo(selectedCycle?.cycleNo) }} 轮明细
            <el-button v-if="canDeleteFlow" type="danger" size="small" :disabled="!selectedFlowIds.length" @click="handleDeleteFlows">批量删除历史记录</el-button>
          </div>
          <el-table
              :data="flowList"
              border
              stripe
              height="610"
              v-loading="flowLoading"
              @selection-change="handleFlowSelectionChange"
          >
            <el-table-column type="selection" width="45" :selectable="canSelectFlow"/>
            <el-table-column prop="operationTime" label="时间" width="180">
              <template #default="{ row }">{{ formatDateTime(row.operationTime) }}</template>
            </el-table-column>
            <el-table-column prop="operationType" label="动作" width="120">
              <template #default="{ row }">
                <el-tag :type="getDictType(FLOW_OPERATION_MAP, row.operationType)">
                  {{ row.operationName || getDictLabel(FLOW_OPERATION_MAP, row.operationType) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="operatorName" label="操作人" width="90"/>
            <el-table-column prop="productName" label="产品" min-width="110" show-overflow-tooltip/>
            <el-table-column prop="productStatus" label="产品状态" width="90"/>
            <el-table-column label="位置" width="76">
              <template #default="{ row }">
                <el-button type="primary" link @click="openLocationDialog(row)">查看</el-button>
              </template>
            </el-table-column>
            <el-table-column prop="remark" label="备注" min-width="180" show-overflow-tooltip/>
          </el-table>
        </div>
      </div>
    </el-drawer>

    <el-dialog title="流转位置" v-model="locationDialogVisible" width="460px">
      <el-descriptions :column="1" border>
        <el-descriptions-item label="原位置">{{ formatLocation(currentFlowLocation, 'from', '无') }}</el-descriptions-item>
        <el-descriptions-item label="目标位置">{{ formatLocation(currentFlowLocation, 'to', '无') }}</el-descriptions-item>
      </el-descriptions>
    </el-dialog>

    <LocalPrinterSettingsDialog v-model="printerSettingsVisible" />
  </div>
</template>

<script setup>
import {computed, onBeforeUnmount, onMounted, ref, watch} from 'vue'
import {useRoute, useRouter} from 'vue-router'
import dayjs from 'dayjs'
import {ElMessage, ElMessageBox} from 'element-plus'
import LocalPrinterSettingsDialog from '@/components/LocalPrinterSettingsDialog.vue'
import {getProductList} from '@/api/product'
import {addAssay} from '@/api/assay'
import {buildAssistantErrorMessage, printLocalLabels} from '@/api/localPrinter'
import {formatDateTime} from '@/utils/dateTime'
import {buildProductCascaderOptions, productCascaderProps} from '@/utils/productCascader'
import {
  batchDownloadPalletQrLabelPdf,
  deletePalletFlows,
  downloadPalletQrLabelPdf,
  downloadPalletQrPng,
  downloadPalletQrSvg,
  generatePalletCodes,
  getPalletAssay,
  getPalletInventory,
  getPalletQrCode,
  invalidatePalletCodes,
  listPalletFlowsByCycle,
  pagePalletCodes,
  pagePalletFlowCycles,
  restoreInvalidPalletCodes
} from '@/api/palletCode'
import {
  FLOW_OPERATION_MAP,
  PALLET_STATUS_MAP,
  PRODUCT_STATUS_OPTIONS,
  PRODUCT_TYPE_OPTIONS,
  getDictLabel,
  getDictType
} from '@/utils/palletCodeDict'
import {useAuthStore} from '@/stores/auth'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const canGenerate = computed(() => authStore.hasPermission('qrcode:generate'))
const canPrint = computed(() => authStore.hasPermission('qrcode:print'))
const canInvalidate = computed(() => authStore.hasPermission('qrcode:invalidate'))
const canDeleteFlow = computed(() => authStore.hasPermission('qrcode:flow_delete'))
const searchForm = ref({
  code: '',
  status: '',
  productName: '',
  productType: '',
  productStatus: '',
  productionDateRange: []
})
const resultList = ref([])
const selectedRows = ref([])
const loading = ref(false)
const qrDownloadLoading = ref(false)
const batchPdfLoading = ref(false)
const batchPrintLoading = ref(false)
const generatedPdfLoading = ref(false)
const qrPrintLoading = ref(false)
const currentPage = ref(1)
const pageSize = ref(10)
const total = ref(0)
const printerSettingsVisible = ref(false)

const productList = ref([])
const productOptions = computed(() => buildProductCascaderOptions(productList.value))
const palletStatusOptions = Object.entries(PALLET_STATUS_MAP).map(([value, item]) => ({
  value,
  label: item.label
}))
const currentCode = ref('')

const generateDialogVisible = ref(false)
const generateForm = ref({count: 20})
const generatedCodes = ref([])

const qrDialogVisible = ref(false)
const qrImageUrl = ref('')
const assayDialogVisible = ref(false)
const assayInfo = ref(null)
const assayCreateDialogVisible = ref(false)
const assayCreateSubmitting = ref(false)
const assayCreateForm = ref(defaultAssayCreateForm())
const inventoryDialogVisible = ref(false)
const inventoryInfo = ref(null)

const flowDrawerVisible = ref(false)
const cycleLoading = ref(false)
const cycleList = ref([])
const cycleTotal = ref(0)
const cyclePageNum = ref(1)
const cyclePageSize = ref(5)
const selectedCycle = ref(null)
const flowLoading = ref(false)
const flowList = ref([])
const selectedFlowIds = ref([])
const locationDialogVisible = ref(false)
const currentFlowLocation = ref(null)

function defaultAssayCreateForm() {
  return {
    code: '',
    productId: null,
    sampleDate: dayjs().format('YYYY-MM-DD'),
    colorValue: '',
    reducingSugar: '',
    dryWeight: '',
    conductivityAsh: '',
    sucrose: '',
    insolubleImpurity: '',
    phValue: ''
  }
}

const isFreeRow = (row) => row.status === 'FREE'
const isPendingRow = (row) => row.status === 'PENDING'
const isInstockRow = (row) => row.status === 'INSTOCK'
const isInvalidRow = (row) => row.status === 'INVALID'
const canShowTaskAction = (row) => isPendingRow(row)
const canShowAssayAction = (row) => isPendingRow(row) || isInstockRow(row)
const canShowLocationAction = (row) => isInstockRow(row)
const canShowFlowAction = (row) => isFreeRow(row) || isPendingRow(row) || isInstockRow(row)
const canShowInvalidAction = (row) => isFreeRow(row)
const canShowRestoreInvalidAction = (row) => isInvalidRow(row)

const buildQuery = () => {
  const params = {
    pageNum: currentPage.value,
    pageSize: pageSize.value
  }
  Object.entries(searchForm.value).forEach(([key, value]) => {
    if (key !== 'productionDateRange' && value !== '' && value != null) {
      params[key] = value
    }
  })
  if (searchForm.value.productionDateRange?.length === 2) {
    params.productionDateStart = searchForm.value.productionDateRange[0]
    params.productionDateEnd = searchForm.value.productionDateRange[1]
  }
  return params
}

const normalizeCode = (code) => String(code || '').trim().toUpperCase()

const buildPrintLabelsByCodes = (codes) => {
  const rowMap = new Map(resultList.value.map(item => [normalizeCode(item.code), item]))
  return Array.from(new Set((codes || []).map(normalizeCode).filter(Boolean))).map(code => {
    const row = rowMap.get(code)
    return {
      code,
      title: row?.productName || ''
    }
  })
}

const handleSearch = async () => {
  loading.value = true
  try {
    const res = await pagePalletCodes(buildQuery())
    resultList.value = res.data?.records || []
    total.value = res.data?.total || 0
  } finally {
    loading.value = false
  }
}

const handleReset = () => {
  searchForm.value = {
    code: '',
    status: '',
    productName: '',
    productType: '',
    productStatus: '',
    productionDateRange: []
  }
  currentPage.value = 1
  handleSearch()
}

const handleSizeChange = (size) => {
  pageSize.value = size
  currentPage.value = 1
  handleSearch()
}

const handleCurrentChange = (page) => {
  currentPage.value = page
  handleSearch()
}

const handleSelectionChange = (rows) => {
  selectedRows.value = rows
}

const openGenerateDialog = () => {
  generatedCodes.value = []
  generateForm.value = {count: 20}
  generateDialogVisible.value = true
}

const closeGenerateDialog = () => {
  generateDialogVisible.value = false
  generatedCodes.value = []
}

const submitGenerate = async () => {
  const res = await generatePalletCodes(generateForm.value)
  generatedCodes.value = res.data || []
  ElMessage.success('生成成功')
  await handleSearch()
}

const openQrDialog = async (row) => {
  currentCode.value = row.code
  revokeQrImage()
  const res = await getPalletQrCode(row.code)
  qrImageUrl.value = URL.createObjectURL(res.data)
  qrDialogVisible.value = true
}

const closeQrDialog = () => {
  qrDialogVisible.value = false
  revokeQrImage()
}

const revokeQrImage = () => {
  if (qrImageUrl.value) {
    URL.revokeObjectURL(qrImageUrl.value)
    qrImageUrl.value = ''
  }
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

const handleQrDownload = async (code, format) => {
  if (!code) {
    ElMessage.warning('二维码不能为空')
    return
  }
  qrDownloadLoading.value = true
  try {
    const safeCode = String(code).trim().toUpperCase()
    let response
    let filename
    if (format === 'png') {
      response = await downloadPalletQrPng(safeCode)
      filename = `${safeCode}.png`
    } else if (format === 'svg') {
      response = await downloadPalletQrSvg(safeCode)
      filename = `${safeCode}.svg`
    } else {
      response = await downloadPalletQrLabelPdf(safeCode)
      filename = `${safeCode}.pdf`
    }
    downloadBlob(response.data, filename)
    ElMessage.success('下载已开始')
  } catch (error) {
    ElMessage.error('二维码下载失败')
  } finally {
    qrDownloadLoading.value = false
  }
}

const handleQrAction = async (row, command) => {
  if (command === 'print') {
    await handleDirectPrintByCodes([row.code], true)
    return
  }
  await handleQrDownload(row.code, command)
}

const handleBatchDownloadPdf = async (codes = selectedRows.value.map(row => row.code), fromGenerated = false) => {
  const exportCodes = Array.from(new Set((codes || []).filter(Boolean).map(code => String(code).trim().toUpperCase())))
  if (!exportCodes.length) {
    ElMessage.warning('请选择二维码')
    return
  }
  if (fromGenerated) {
    generatedPdfLoading.value = true
  } else {
    batchPdfLoading.value = true
  }
  try {
    const response = await batchDownloadPalletQrLabelPdf(exportCodes)
    downloadBlob(response.data, `pallet-labels-batch-${dayjs().format('YYYY-MM-DD')}.pdf`)
    ElMessage.success(`已导出 ${exportCodes.length} 个二维码标签`)
  } catch (error) {
    ElMessage.error('批量导出标签 PDF 失败')
  } finally {
    if (fromGenerated) {
      generatedPdfLoading.value = false
    } else {
      batchPdfLoading.value = false
    }
  }
}

const handleDirectPrintByCodes = async (codes = selectedRows.value.map(row => row.code), single = false) => {
  const labels = buildPrintLabelsByCodes(codes)
  if (!labels.length) {
    ElMessage.warning('请选择二维码')
    return
  }

  if (single) {
    qrPrintLoading.value = true
  } else {
    batchPrintLoading.value = true
  }

  try {
    const response = await printLocalLabels({
      template: 'fixed_product_qrcode',
      copies: 1,
      labels
    })
    const printedCount = response.data?.printedCount || labels.length
    ElMessage.success(`已提交 ${printedCount} 张标签到打印机`)
  } catch (error) {
    await handleBatchDownloadPdf(labels.map(item => item.code))
    ElMessage.warning(`${buildAssistantErrorMessage(error)}，已回退为 PDF 导出`)
  } finally {
    if (single) {
      qrPrintLoading.value = false
    } else {
      batchPrintLoading.value = false
    }
  }
}

const handleBatchDirectPrint = async () => {
  await handleDirectPrintByCodes()
}

const openAssayDialog = async (row) => {
  currentCode.value = row.code
  const res = await getPalletAssay(row.code)
  if (res.data?.id) {
    assayInfo.value = res.data || null
    assayDialogVisible.value = true
    return
  }
  const action = await ElMessageBox.confirm(
      `当前二维码 ${row.code} 暂无化验记录，是否现在新增？`,
      '新增化验',
      {
        confirmButtonText: '新增化验',
        cancelButtonText: '取消',
        type: 'warning'
      }
  ).catch(() => null)
  if (!action) {
    return
  }
  prepareAssayCreate(row)
}

const handleInvalid = async (row) => {
  await invalidateByCodes([row.code])
}

const handleRestoreInvalid = async (row) => {
  await restoreInvalidByCodes([row.code])
}

const handleBatchInvalid = async () => {
  await invalidateByCodes(selectedRows.value.map(row => row.code))
}

const invalidateByCodes = async (codes) => {
  if (!codes.length) {
    ElMessage.warning('请选择二维码')
    return
  }
  await ElMessageBox.confirm(`确认作废 ${codes.length} 个二维码吗？`, '提示', {type: 'warning'})
  await invalidatePalletCodes({codes, remark: 'Web 管理端作废'})
  ElMessage.success('作废成功')
  await handleSearch()
}

const restoreInvalidByCodes = async (codes) => {
  if (!codes.length) {
    ElMessage.warning('请选择二维码')
    return
  }
  await ElMessageBox.confirm(`确认恢复 ${codes.length} 个作废二维码吗？`, '提示', {type: 'warning'})
  await restoreInvalidPalletCodes({codes, remark: 'Web 管理端取消作废'})
  ElMessage.success('取消作废成功')
  await handleSearch()
}

const prepareAssayCreate = (row) => {
  assayCreateForm.value = {
    ...defaultAssayCreateForm(),
    code: row.code,
    productId: row.productId || null,
    sampleDate: row.productionDate || dayjs().format('YYYY-MM-DD')
  }
  assayCreateDialogVisible.value = true
}

const submitAssayCreate = async () => {
  if (!assayCreateForm.value.productId) {
    ElMessage.warning('请选择化验产品')
    return
  }
  if (!assayCreateForm.value.sampleDate) {
    ElMessage.warning('请选择采样日期')
    return
  }
  assayCreateSubmitting.value = true
  try {
    const payload = {
      selectType: '1',
      productId: assayCreateForm.value.productId,
      sampleDate: assayCreateForm.value.sampleDate
    }
    ;['colorValue', 'reducingSugar', 'dryWeight', 'conductivityAsh', 'sucrose', 'insolubleImpurity', 'phValue']
        .forEach((field) => {
          if (assayCreateForm.value[field] !== '' && assayCreateForm.value[field] != null) {
            payload[field] = assayCreateForm.value[field]
          }
        })
    await addAssay([payload])
    assayCreateDialogVisible.value = false
    ElMessage.success('新增化验成功')
    await openAssayDialog({code: assayCreateForm.value.code, productId: assayCreateForm.value.productId, productionDate: assayCreateForm.value.sampleDate})
    await handleSearch()
  } finally {
    assayCreateSubmitting.value = false
  }
}

const goTaskCenter = (row) => {
  if (!row.code) {
    ElMessage.warning('当前二维码为空，无法定位任务')
    return
  }
  const path = row.productStatus === '半成品' ? '/pallet-task/semi/in' : '/pallet-task/finish/in'
  router.push({
    path,
    query: {
      code: row.code,
      status: 'PENDING',
      productNameExact: row.productName || ''
    }
  })
}

const goWarehouseMap = async (row) => {
  try {
    const res = await getPalletInventory(row.code)
    router.push({
      path: '/warehouse-map',
      query: {
        palletCode: row.code,
        warehouseName: res.data?.warehouseName || ''
      }
    })
  } catch (error) {
    ElMessage.error(error?.message || '跳转仓库平面图失败')
  }
}

const openFlowDrawer = async (row) => {
  currentCode.value = row.code
  cyclePageNum.value = 1
  selectedCycle.value = null
  flowList.value = []
  selectedFlowIds.value = []
  flowDrawerVisible.value = true
  await loadFlowCycles()
}

const closeFlowDrawer = () => {
  flowDrawerVisible.value = false
  cycleList.value = []
  flowList.value = []
  selectedCycle.value = null
  selectedFlowIds.value = []
  currentFlowLocation.value = null
  locationDialogVisible.value = false
}

const loadFlowCycles = async () => {
  cycleLoading.value = true
  try {
    const res = await pagePalletFlowCycles(currentCode.value, {
      pageNum: cyclePageNum.value,
      pageSize: cyclePageSize.value
    })
    cycleList.value = res.data?.records || []
    cycleTotal.value = res.data?.total || 0
    if (cycleList.value.length) {
      await selectCycle(cycleList.value[0])
    }
  } finally {
    cycleLoading.value = false
  }
}

const handleCyclePageChange = async (page) => {
  cyclePageNum.value = page
  await loadFlowCycles()
}

const selectCycle = async (row) => {
  selectedCycle.value = row
  selectedFlowIds.value = []
  flowLoading.value = true
  try {
    const res = await listPalletFlowsByCycle(currentCode.value, row.cycleNo)
    flowList.value = res.data || []
  } finally {
    flowLoading.value = false
  }
}

const handleFlowSelectionChange = (rows) => {
  selectedFlowIds.value = rows.map(row => row.id)
}

const displayCycleNo = (cycleNo) => {
  if (cycleNo == null) {
    return '-'
  }
  const value = Number(cycleNo)
  return value <= 0 ? 1 : value
}

const canSelectFlow = (row) => {
  if (selectedCycle.value?.isCurrentCycle) {
    return false
  }
  if (!row.operationTime) {
    return false
  }
  return dayjs(row.operationTime).isBefore(dayjs().subtract(180, 'day'))
}

const handleDeleteFlows = async () => {
  if (!selectedFlowIds.value.length) {
    ElMessage.warning('请选择可删除的历史记录')
    return
  }
  await ElMessageBox.confirm(`确认删除 ${selectedFlowIds.value.length} 条历史记录吗？`, '提示', {type: 'warning'})
  await deletePalletFlows({ids: selectedFlowIds.value})
  ElMessage.success('删除成功')
  await selectCycle(selectedCycle.value)
  await loadFlowCycles()
}

const openLocationDialog = (row) => {
  currentFlowLocation.value = row
  locationDialogVisible.value = true
}

const formatLocation = (row, prefix, emptyText = '-') => {
  if (!row) {
    return emptyText
  }
  const warehouse = row[`${prefix}WarehouseName`]
  if (!warehouse) {
    return emptyText
  }
  const side = row[`${prefix}Side`] || '-'
  const rowNumber = row[`${prefix}RowNumber`] ?? '-'
  const layer = row[`${prefix}Layer`] ?? '-'
  return `${warehouse} ${side}侧 ${rowNumber}排 ${layer}层`
}

const loadProducts = async () => {
  const res = await getProductList({})
  productList.value = Array.isArray(res.data) ? res.data : (res.data?.records || [])
}

onMounted(async () => {
  if (route.query.code) {
    searchForm.value.code = Array.isArray(route.query.code) ? route.query.code[0] : route.query.code
  }
  await loadProducts()
  await handleSearch()
})

watch(() => route.query.code, (code) => {
  if (!code) return
  searchForm.value.code = Array.isArray(code) ? code[0] : code
  currentPage.value = 1
  handleSearch()
})

onBeforeUnmount(() => {
  revokeQrImage()
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

.el-form--inline .el-form-item {
  margin-right: 24px;
}

.pagination-wrapper {
  margin-top: 20px;
  display: flex;
  justify-content: flex-end;
}

.code-result {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 16px;
}

.code-tag {
  margin-right: 4px;
}

.no-ellipsis-cell {
  display: inline-block;
  min-width: max-content;
}

.row-actions {
  display: flex;
  justify-content: flex-start;
  align-items: center;
  gap: 10px;
  flex-wrap: nowrap;
  white-space: nowrap;
}

.row-actions :deep(.el-button) {
  margin-left: 0;
  padding: 0;
}

.qr-wrapper {
  text-align: center;
}

.qr-code {
  margin-bottom: 12px;
  font-weight: 600;
}

.qr-image {
  width: 256px;
  height: 256px;
}

.qr-download-actions {
  display: flex;
  justify-content: center;
  gap: 8px;
  flex-wrap: wrap;
  margin-top: 16px;
}

.flow-drawer {
  display: grid;
  grid-template-columns: 300px minmax(0, 1fr);
  gap: 16px;
}

.flow-panel,
.cycle-panel {
  min-width: 0;
  padding: 14px;
  border: 1px solid var(--app-border-soft);
  border-radius: var(--app-radius);
  background: var(--app-panel);
  box-shadow: var(--app-shadow-soft);
}

.panel-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
  color: var(--app-text);
  font-weight: 650;
}

.cycle-list {
  min-height: 560px;
}

.cycle-list-header,
.cycle-item {
  display: grid;
  grid-template-columns: 48px minmax(0, 1fr) 48px 42px;
  align-items: center;
  gap: 8px;
}

.cycle-list-header {
  height: 34px;
  padding: 0 10px;
  border-radius: 6px;
  background: #f7f8fb;
  color: var(--app-text-tertiary);
  font-size: 12px;
  font-weight: 600;
}

.cycle-item {
  width: 100%;
  min-width: 0;
  height: 42px;
  margin-top: 6px;
  padding: 0 10px;
  border: 1px solid transparent;
  border-radius: 7px;
  background: transparent;
  color: var(--app-text-secondary);
  cursor: pointer;
  text-align: left;
  transition: background-color 0.16s ease, border-color 0.16s ease, color 0.16s ease;
}

.cycle-item:hover {
  background: var(--app-hover);
  color: var(--app-text);
}

.cycle-item.active {
  border-color: #cfe0ff;
  background: var(--app-primary-light);
  color: var(--app-primary);
  font-weight: 650;
}

.cycle-no,
.cycle-status,
.cycle-count {
  white-space: nowrap;
}

.cycle-product {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.cycle-status,
.cycle-count {
  text-align: center;
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
const formatInventoryOccupancy = (inventory) => {
  if (!inventory) return '-'
  const quantity = Number(inventory.quantity || 0)
  const pieces = Number(inventory.pieces || 0)
  if (pieces > 0) {
    return `${pieces}件，占1板位`
  }
  if (quantity > 0) {
    return `${quantity}板`
  }
  return '0'
}
