<template>
  <div class="inventory-center">
    <div class="page-header">
      <div>
        <div class="page-title">库存总览中心</div>
          <div class="page-subtitle">按产品、二维码、生产中半成品三个维度查看库存与生产占用，并联动二维码、仓库平面图、化验和流转记录。</div>
      </div>
    </div>

    <el-card class="search-card">
      <el-form :model="searchForm" inline>
        <el-form-item label="产品名称">
          <el-input v-model="searchForm.productName" clearable placeholder="请输入产品名称" style="width: 190px"/>
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
        <el-form-item label="筛网">
          <el-select v-model="searchForm.screenMeshId" clearable placeholder="请选择" style="width: 160px">
            <el-option v-for="item in meshList" :key="item.id" :label="item.meshName" :value="item.id"/>
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
              style="width: 280px"
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
          <div class="section-title">{{ activeTabTitle }}</div>
          <div class="section-subtitle">{{ activeTabDescription }}</div>
        </div>
        <div class="table-toolbar-right">
          <el-button @click="handleExport">导出当前表格</el-button>
          <el-button @click="router.push('/warehouse-map')">仓库平面图</el-button>
          <el-button @click="router.push('/pallet-code/list')">二维码管理</el-button>
        </div>
      </div>

      <el-tabs v-model="activeTab" class="inventory-tabs" @tab-change="handleTabChange">
        <el-tab-pane label="产品汇总库存" name="product"/>
        <el-tab-pane label="二维码库存" name="pallet"/>
        <el-tab-pane label="生产中半成品" name="inProcess"/>
      </el-tabs>

      <el-table
          v-if="activeTab === 'product'"
          :data="pagedProductRows"
          height="560"
          stripe
          v-loading="loading"
      >
        <el-table-column prop="productName" label="产品名称" min-width="160" fixed="left" show-overflow-tooltip/>
        <el-table-column prop="productType" label="产品类型" width="110" show-overflow-tooltip/>
        <el-table-column prop="productStatus" label="产品状态" width="110">
          <template #default="{ row }">
            <el-tag :type="row.productStatus === '成品' ? 'primary' : 'success'">{{ row.productStatus || '-' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="totalQuantity" label="总板数" width="110"/>
        <el-table-column prop="totalPieces" label="件数" width="110"/>
        <el-table-column prop="totalWeight" label="总重量" width="120">
          <template #default="{ row }">{{ formatWeight(row.totalWeight) }}</template>
        </el-table-column>
        <el-table-column prop="palletCount" label="涉及二维码数" width="120"/>
        <el-table-column prop="warehouseCount" label="涉及库位数" width="120"/>
        <el-table-column label="操作" width="230" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="switchToPallet(row)">看二维码</el-button>
            <el-button link type="primary" @click="router.push('/warehouse-map')">平面图</el-button>
            <el-button link type="primary" @click="router.push({path: '/assay', query: {productName: row.productName}})">化验</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-table
          v-else-if="activeTab === 'pallet'"
          :data="palletRows"
          height="560"
          stripe
          v-loading="loading"
      >
        <el-table-column prop="code" label="二维码" width="140" fixed="left" show-overflow-tooltip/>
        <el-table-column prop="productName" label="产品名称" min-width="150" show-overflow-tooltip/>
        <el-table-column prop="productStatus" label="产品状态" width="100"/>
        <el-table-column prop="productionDate" label="生产日期" width="120"/>
        <el-table-column label="所在库位" min-width="180" show-overflow-tooltip>
          <template #default="{ row }">
            {{ formatInventoryLocation(row.inventoryInfo) }}
          </template>
        </el-table-column>
        <el-table-column prop="screenMeshName" label="筛网" width="100" show-overflow-tooltip/>
        <el-table-column label="化验状态" width="110">
          <template #default="{ row }">
            <el-tag :type="row.assayId ? 'success' : 'info'">{{ row.assayId ? '已关联' : '未关联' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="updatedAt" label="最近更新时间" width="170">
          <template #default="{ row }">{{ formatDateTime(row.updatedAt || row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="310" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openPallet(row)">二维码</el-button>
            <el-button link type="primary" @click="openWarehouseMap(row)">平面图</el-button>
            <el-button link type="primary" @click="openAssay(row)">化验</el-button>
            <el-button link type="primary" @click="openFlowDrawer(row)">流转</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-table
          v-else
          :data="inProcessRows"
          height="560"
          stripe
          v-loading="loading"
      >
        <el-table-column prop="productName" label="半成品名称" min-width="170" fixed="left" show-overflow-tooltip/>
        <el-table-column prop="orderNo" label="生产订单" width="150" show-overflow-tooltip/>
        <el-table-column prop="productionDate" label="生产日期" width="120"/>
        <el-table-column label="领用数量" width="130">
          <template #default="{ row }">{{ formatMaterialQuantity(row) }}</template>
        </el-table-column>
        <el-table-column label="来源库位" min-width="180" show-overflow-tooltip>
          <template #default="{ row }">{{ formatMaterialLocation(row) }}</template>
        </el-table-column>
        <el-table-column label="领用时间" width="170">
          <template #default="{ row }">{{ formatDateTime(row.pickedAt) }}</template>
        </el-table-column>
        <el-table-column prop="pickedByName" label="领用人" width="100"/>
        <el-table-column label="订单状态" width="110">
          <template #default="{ row }">
            <el-tag>{{ orderStatusLabel(row.orderStatus) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="110">
          <template #default="{ row }">
            <el-tag type="warning">{{ row.status === 'PICKED' ? '生产中' : row.status || '-' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="180" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openProductionOrder(row)">生产订单</el-button>
            <el-button link type="primary" @click="openAssay(row)">化验</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-wrapper">
        <el-pagination
            background
            layout="total, sizes, prev, pager, next"
            :total="activeTotal"
            :current-page="currentPage"
            :page-size="pageSize"
            @size-change="handleSizeChange"
            @current-change="handleCurrentChange"
        />
      </div>
    </el-card>

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
      <el-empty v-else description="暂无化验记录"/>
    </el-dialog>

    <el-drawer v-model="flowDrawerVisible" title="二维码流转记录" size="70%" :before-close="closeFlowDrawer">
      <div class="flow-drawer">
        <div class="cycle-panel">
          <div class="panel-title">{{ currentCode }} 的循环轮次</div>
          <div class="cycle-list" v-loading="cycleLoading">
            <div class="cycle-list-header">
              <span>轮次</span>
              <span>产品</span>
              <span>状态</span>
              <span>flow</span>
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
          <div class="panel-title">第 {{ displayCycleNo(selectedCycle?.cycleNo) }} 轮明细</div>
          <el-table :data="flowList" height="610" stripe v-loading="flowLoading">
            <el-table-column prop="operationTime" label="时间" width="170">
              <template #default="{ row }">{{ formatDateTime(row.operationTime) }}</template>
            </el-table-column>
            <el-table-column prop="operationName" label="动作" min-width="140" show-overflow-tooltip/>
            <el-table-column prop="operatorName" label="操作人" width="100"/>
            <el-table-column prop="productName" label="产品" min-width="140" show-overflow-tooltip/>
            <el-table-column prop="productStatus" label="产品状态" width="100"/>
            <el-table-column prop="remark" label="备注" min-width="160" show-overflow-tooltip/>
          </el-table>
        </div>
      </div>
    </el-drawer>
  </div>
</template>

<script setup>
import {computed, onMounted, ref} from 'vue'
import {useRouter} from 'vue-router'
import {ElMessage} from 'element-plus'
import * as XLSX from 'xlsx'
import {
  getPalletAssay,
  getPalletInventory,
  listPalletFlowsByCycle,
  pagePalletCodes,
  pagePalletFlowCycles
} from '@/api/palletCode'
import {getProductStock} from '@/api/warehouseinfo'
import {pageProductionInProcessMaterials} from '@/api/production'
import {getProductList} from '@/api/product'
import {getMesh} from '@/api/mesh'
import {getAssayByProductDate} from '@/api/assay'
import {formatDateTime} from '@/utils/dateTime'
import {
  PRODUCT_STATUS_OPTIONS,
  PRODUCT_TYPE_OPTIONS
} from '@/utils/palletCodeDict'

const router = useRouter()
const activeTab = ref('product')
const loading = ref(false)
const currentPage = ref(1)
const pageSize = ref(10)
const productRows = ref([])
const productTotal = ref(0)
const palletRows = ref([])
const palletTotal = ref(0)
const inProcessRows = ref([])
const inProcessTotal = ref(0)
const productList = ref([])
const meshList = ref([])
const inventoryCache = ref({})
const assayDialogVisible = ref(false)
const assayInfo = ref(null)
const flowDrawerVisible = ref(false)
const currentCode = ref('')
const cycleLoading = ref(false)
const cycleList = ref([])
const cycleTotal = ref(0)
const cyclePageNum = ref(1)
const cyclePageSize = ref(5)
const selectedCycle = ref(null)
const flowLoading = ref(false)
const flowList = ref([])

const searchForm = ref(defaultSearchForm())

const productMap = computed(() => new Map(productList.value.map(item => [item.id, item])))
const productByName = computed(() => new Map(productList.value.map(item => [item.productName, item])))
const meshMap = computed(() => new Map(meshList.value.map(item => [item.id, item.meshName])))
const activeTotal = computed(() => {
  if (activeTab.value === 'product') return productTotal.value
  if (activeTab.value === 'pallet') return palletTotal.value
  return inProcessTotal.value
})
const pagedProductRows = computed(() => {
  const start = (currentPage.value - 1) * pageSize.value
  return productRows.value.slice(start, start + pageSize.value)
})
const activeTabTitle = computed(() => {
  const map = {
    product: '产品汇总库存',
    pallet: '二维码库存',
    inProcess: '生产中半成品'
  }
  return map[activeTab.value]
})
const activeTabDescription = computed(() => {
  const map = {
    product: '按产品聚合当前库存，展示总板数、总件数、重量以及涉及二维码和库位。',
    pallet: '按二维码查看当前在库占位，并可联动二维码详情、库位、化验和流转。',
    inProcess: '展示已被生产订单领用、仍处于生产过程中的半成品材料记录。'
  }
  return map[activeTab.value]
})

function defaultSearchForm() {
  return {
    productName: '',
    productType: '',
    productStatus: '',
    screenMeshId: '',
    dateRange: []
  }
}

const loadOptions = async () => {
  const [productRes, meshRes] = await Promise.all([getProductList({}), getMesh({})])
  productList.value = Array.isArray(productRes.data) ? productRes.data : (productRes.data?.records || [])
  meshList.value = meshRes.code === 200 ? (meshRes.data || []) : []
}

const handleSearch = async () => {
  if (activeTab.value === 'product') {
    await loadProductSummary()
  } else if (activeTab.value === 'pallet') {
    await loadPalletInventory()
  } else {
    await loadInProcessMaterials()
  }
}

const handleReset = () => {
  searchForm.value = defaultSearchForm()
  currentPage.value = 1
  handleSearch()
}

const handleTabChange = () => {
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

const loadProductSummary = async () => {
  loading.value = true
  try {
    const statuses = searchForm.value.productStatus ? [searchForm.value.productStatus] : ['成品', '半成品']
    const stockResponses = await Promise.all(statuses.map(status => getProductStock({
      productStatus: status,
      productName: searchForm.value.productName
    })))
    const rawRows = stockResponses.flatMap((res, index) => (res.data || []).map(row => ({
      ...row,
      productStatus: statuses[index]
    })))
    const filteredRows = rawRows.filter(matchSummaryFilters)
    const palletCountMap = await loadPalletCountMap()
    const grouped = new Map()
    filteredRows.forEach(row => {
      const product = productMap.value.get(row.productId) || productByName.value.get(row.productName) || {}
      const key = row.productId || row.productName
      if (!grouped.has(key)) {
        grouped.set(key, {
          productId: row.productId,
          productName: row.productName,
          productType: product.productType || '-',
          productStatus: product.status || row.productStatus,
          totalQuantity: 0,
          totalPieces: 0,
          totalWeight: 0,
          warehouseIds: new Set(),
          warehouseCount: 0,
          palletCount: 0
        })
      }
      const target = grouped.get(key)
      target.totalQuantity += Number(row.totalQuantity || 0)
      target.totalPieces += Number(row.totalPieces || 0)
      target.totalWeight += Number(row.totalWeight || 0)
      if (row.warehouseId) target.warehouseIds.add(row.warehouseId)
      target.warehouseCount = Math.max(target.warehouseCount, Number(row.warehouseCount || 0))
    })
    productRows.value = Array.from(grouped.values()).map(row => ({
      ...row,
      palletCount: palletCountMap.get(row.productId || row.productName) || estimatePalletCount(row),
      warehouseCount: row.warehouseIds.size || row.warehouseCount || 0
    }))
    productTotal.value = productRows.value.length
  } finally {
    loading.value = false
  }
}

const loadPalletCountMap = async () => {
  const params = buildPalletQuery({pageNum: 1, pageSize: 1000})
  const res = await pagePalletCodes(params)
  const map = new Map()
  ;(res.data?.records || []).forEach(row => {
    const key = row.productId || row.productName
    map.set(key, (map.get(key) || 0) + 1)
  })
  return map
}

const loadPalletInventory = async () => {
  loading.value = true
  try {
    const res = await pagePalletCodes(buildPalletQuery({pageNum: currentPage.value, pageSize: pageSize.value}))
    const rows = (res.data?.records || []).filter(matchScreenMeshFilter)
    await enrichPalletLocations(rows)
    palletRows.value = rows.filter(row => row.inventoryInfo)
    palletTotal.value = searchForm.value.screenMeshId ? palletRows.value.length : (res.data?.total || 0)
  } finally {
    loading.value = false
  }
}

const loadInProcessMaterials = async () => {
  if (searchForm.value.productStatus && searchForm.value.productStatus !== '半成品') {
    inProcessRows.value = []
    inProcessTotal.value = 0
    return
  }
  loading.value = true
  try {
    const res = await pageProductionInProcessMaterials({
      page: currentPage.value,
      size: pageSize.value,
      productName: searchForm.value.productName || undefined,
      productType: searchForm.value.productType || undefined,
      screenMeshId: searchForm.value.screenMeshId || undefined,
      productionDateStart: searchForm.value.dateRange?.[0],
      productionDateEnd: searchForm.value.dateRange?.[1]
    })
    inProcessRows.value = res.data?.records || []
    inProcessTotal.value = res.data?.total || 0
  } finally {
    loading.value = false
  }
}

const buildPalletQuery = (pageParams) => ({
  ...pageParams,
  status: 'INSTOCK',
  inventoryOnly: true,
  productName: searchForm.value.productName || undefined,
  productType: searchForm.value.productType || undefined,
  productStatus: searchForm.value.productStatus || undefined,
  productionDateStart: searchForm.value.dateRange?.[0],
  productionDateEnd: searchForm.value.dateRange?.[1]
})

const matchSummaryFilters = (row) => {
  const product = productMap.value.get(row.productId) || productByName.value.get(row.productName) || {}
  if (searchForm.value.productType && product.productType !== searchForm.value.productType) return false
  if (searchForm.value.screenMeshId && product.screenMeshId !== searchForm.value.screenMeshId) return false
  if (!searchForm.value.dateRange?.length) return true
  const [start, end] = searchForm.value.dateRange
  return (!start || row.entryDate >= start) && (!end || row.entryDate <= end)
}

const matchScreenMeshFilter = (row) => {
  if (!searchForm.value.screenMeshId) return true
  return row.screenMeshId === searchForm.value.screenMeshId || row.screenMeshName === meshMap.value.get(searchForm.value.screenMeshId)
}

const enrichPalletLocations = async (rows) => {
  await Promise.all(rows.map(async row => {
    if (!inventoryCache.value[row.code]) {
      try {
        const res = await getPalletInventory(row.code)
        inventoryCache.value[row.code] = res.data || null
      } catch {
        inventoryCache.value[row.code] = null
      }
    }
    row.inventoryInfo = inventoryCache.value[row.code]
  }))
}

const estimatePalletCount = (row) => row.totalQuantity + (row.totalPieces > 0 ? 1 : 0)
const formatNumber = (value) => {
  const number = Number(value || 0)
  return Number.isInteger(number) ? number : number.toFixed(2)
}
const formatWeight = (value) => `${formatNumber(value)} kg`
const formatInventoryLocation = (info) => {
  if (!info) return '-'
  const position = [info.side && `${info.side}侧`, info.rowNumber != null && `${info.rowNumber}排`, info.layer != null && `${info.layer}层`]
      .filter(Boolean)
      .join(' ')
  return `${info.warehouseName || '-'}${position ? ` ${position}` : ''}`
}
const formatMaterialLocation = (row) => {
  const position = [row.side && `${row.side}侧`, row.rowNumber != null && `${row.rowNumber}排`, row.layer != null && `${row.layer}层`]
      .filter(Boolean)
      .join(' ')
  return `${row.warehouseName || '-'}${position ? ` ${position}` : ''}`
}
const formatMaterialQuantity = (row) => {
  if (row.unit === '1') {
    return `${row.quantity || row.pieces || 0}件`
  }
  return `${row.quantity || 0}板`
}
const orderStatusLabel = (status) => {
  const map = {
    ISSUED: '已下发',
    MATERIALING: '领料中',
    MATERIALED: '已领料',
    OUTPUT_BINDING: '产出中',
    WAIT_INBOUND: '待入库',
    PART_INBOUND: '部分入库',
    COMPLETED: '已完成',
    CANCELED: '已取消'
  }
  return map[status] || status || '-'
}

const handleExport = () => {
  const rows = buildExportRows()
  if (!rows.length) {
    ElMessage.warning('当前表格暂无可导出数据')
    return
  }
  const worksheet = XLSX.utils.json_to_sheet(rows)
  const workbook = XLSX.utils.book_new()
  XLSX.utils.book_append_sheet(workbook, worksheet, activeTabTitle.value)
  const dateText = new Date().toISOString().slice(0, 10)
  XLSX.writeFile(workbook, `${activeTabTitle.value}_${dateText}.xlsx`)
}

const buildExportRows = () => {
  if (activeTab.value === 'product') {
    return productRows.value.map(row => ({
      产品名称: row.productName || '',
      产品类型: row.productType || '',
      产品状态: row.productStatus || '',
      总板数: row.totalQuantity || 0,
      件数: row.totalPieces || 0,
      总重量: formatWeight(row.totalWeight),
      涉及二维码数: row.palletCount || 0,
      涉及库位数: row.warehouseCount || 0
    }))
  }
  if (activeTab.value === 'pallet') {
    return palletRows.value.map(row => ({
      二维码: row.code || '',
      产品名称: row.productName || '',
      产品状态: row.productStatus || '',
      生产日期: row.productionDate || '',
      所在库位: formatInventoryLocation(row.inventoryInfo),
      筛网: row.screenMeshName || '',
      化验状态: row.assayId ? '已关联' : '未关联',
      最近更新时间: formatDateTime(row.updatedAt || row.createdAt)
    }))
  }
  return inProcessRows.value.map(row => ({
    生产订单: row.orderNo || '',
    半成品产品名称: row.productName || '',
    生产日期: row.productionDate || '',
    领用数量: formatMaterialQuantity(row),
    来源库位: formatMaterialLocation(row),
    领用时间: formatDateTime(row.pickedAt),
    领用人: row.pickedByName || '',
    订单状态: orderStatusLabel(row.orderStatus),
    状态: row.status === 'PICKED' ? '生产中' : row.status || ''
  }))
}

const switchToPallet = (row) => {
  searchForm.value.productName = row.productName
  searchForm.value.productType = row.productType === '-' ? '' : row.productType
  searchForm.value.productStatus = row.productStatus
  activeTab.value = 'pallet'
  currentPage.value = 1
  loadPalletInventory()
}

const openPallet = (row) => {
  router.push({path: '/pallet-code/list', query: {code: row.code}})
}

const openWarehouseMap = (row) => {
  const warehouseId = row.inventoryInfo?.warehouseId
  router.push({path: '/warehouse-map', query: warehouseId ? {warehouseId} : {}})
}

const openProductionOrder = (row) => {
  router.push({
    path: '/production/orders',
    query: {
      orderNo: row.orderNo
    }
  })
}

const openAssay = async (row) => {
  if (!row.code) {
    if (!row.productId || !row.productionDate) {
      assayInfo.value = null
      assayDialogVisible.value = true
      return
    }
    const res = await getAssayByProductDate({
      productId: row.productId,
      productionDate: row.productionDate
    })
    assayInfo.value = res.data || null
    assayDialogVisible.value = true
    return
  }
  const res = await getPalletAssay(row.code)
  assayInfo.value = res.data || null
  assayDialogVisible.value = true
}

const formatBoardPiece = (pieces, piecesPerPallet) => {
  const total = Number(pieces || 0)
  const perPallet = Number(piecesPerPallet || 0)
  if (perPallet <= 0) return `${total}件`
  const boards = Math.floor(total / perPallet)
  const rest = total % perPallet
  return `${boards}板${rest}件`
}

const openFlowDrawer = async (row) => {
  currentCode.value = row.code
  cyclePageNum.value = 1
  selectedCycle.value = null
  flowList.value = []
  flowDrawerVisible.value = true
  await loadFlowCycles()
}

const closeFlowDrawer = () => {
  flowDrawerVisible.value = false
  cycleList.value = []
  flowList.value = []
  selectedCycle.value = null
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
  flowLoading.value = true
  try {
    const res = await listPalletFlowsByCycle(currentCode.value, row.cycleNo)
    flowList.value = res.data || []
  } finally {
    flowLoading.value = false
  }
}

const displayCycleNo = (cycleNo) => {
  if (cycleNo == null) return '-'
  const value = Number(cycleNo)
  return value <= 0 ? 1 : value
}

onMounted(async () => {
  await loadOptions()
  await handleSearch()
})
</script>

<style scoped>
.inventory-center {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.inventory-tabs {
  margin-bottom: 12px;
}

.flow-drawer {
  display: grid;
  grid-template-columns: 310px minmax(0, 1fr);
  gap: 16px;
}

.cycle-panel,
.flow-panel {
  min-width: 0;
}

.panel-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  min-height: 32px;
  margin-bottom: 10px;
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
</style>
