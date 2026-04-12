<template>
  <div class="warehouse-map-page">
    <div class="page-header">
      <div>
        <div class="page-title">仓库平面图</div>
        <div class="page-subtitle">查看仓区分布、库位状态与占用情况，点击库位可在右侧查看详情。</div>
      </div>
    </div>

    <el-card class="search-card">
      <el-form :model="searchForm" inline>
        <el-form-item label="产品名称">
          <el-input v-model="searchForm.productName" placeholder="请输入产品名称" clearable style="width: 200px"/>
        </el-form-item>
        <el-form-item label="标准名称">
          <el-select v-model="searchForm.standardNames" placeholder="请选择" clearable multiple collapse-tags style="width: 220px">
            <el-option
                v-for="item in standards"
                :key="item.id || item.standardName"
                :label="item.label || item.standardName"
                :value="item.standardName"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="筛网名称">
          <el-select v-model="searchForm.screenMeshId" placeholder="请选择" clearable style="width: 200px">
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
              style="width: 320px"
          />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="searchForm.status" placeholder="全部状态" clearable style="width: 170px">
            <el-option v-for="item in statusOptions" :key="item.value" :label="item.label" :value="item.value"/>
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">查询</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card class="legend-card">
      <div class="legend-list">
        <div v-for="item in statusOptions" :key="item.value" class="legend-item">
          <span class="legend-dot" :class="item.value"/>
          <span>{{ item.label }}</span>
        </div>
        <div class="legend-item">
          <span class="legend-dot matched"/>
          <span>查询命中</span>
        </div>
      </div>
    </el-card>

    <div class="map-workspace">
      <el-card class="map-card" v-loading="loadingMap">
        <div class="map-toolbar">
          <div>
            <div class="section-title">仓区平面看板</div>
            <div class="section-subtitle">保留库区空间关系，颜色表示状态，蓝色描边表示当前查询命中的库位。</div>
          </div>
          <div class="map-summary">
            <span>库位 {{ summary.total }}</span>
            <span>正常 {{ summary.normal }}</span>
            <span>满仓 {{ summary.full }}</span>
            <span>预警 {{ summary.danger }}</span>
          </div>
        </div>

        <div class="map-canvas">
          <svg class="warehouse-map-svg" :viewBox="`0 0 ${viewBoxWidth} ${viewBoxHeight}`" role="img">
            <g class="map-grid">
              <line v-for="x in verticalGridLines" :key="`v-${x}`" :x1="x" y1="0" :x2="x" :y2="viewBoxHeight"/>
              <line v-for="y in horizontalGridLines" :key="`h-${y}`" x1="0" :y1="y" :x2="viewBoxWidth" :y2="y"/>
            </g>
            <g v-for="location in mapLocations" :key="location.id" class="map-location-group">
              <polygon
                  v-if="location.shape === 'polygon'"
                  class="map-location"
                  :class="[location.statusKey, {active: selectedLocation?.id === location.id, matched: isMatched(location), dimmed: isDimmed(location)}]"
                  :points="getPolygonPoints(location)"
                  @click="handleSelectLocation(location)"
              >
                <title>{{ getLocationTitle(location) }}</title>
              </polygon>
              <rect
                  v-else
                  class="map-location"
                  :class="[location.statusKey, {active: selectedLocation?.id === location.id, matched: isMatched(location), dimmed: isDimmed(location)}]"
                  :x="location.x"
                  :y="location.y"
                  :width="location.width"
                  :height="location.height"
                  rx="8"
                  ry="8"
                  @click="handleSelectLocation(location)"
              >
                <title>{{ getLocationTitle(location) }}</title>
              </rect>
              <text
                  class="map-location-label"
                  :class="{compact: location.width < 45}"
                  :x="location.x + location.width / 2"
                  :y="location.y + location.height / 2"
                  @click="handleSelectLocation(location)"
              >
                {{ location.label }}
              </text>
            </g>
          </svg>
        </div>
      </el-card>

      <el-card class="detail-panel" v-loading="detailLoading">
        <template v-if="selectedLocation">
          <div class="detail-head">
            <div>
              <div class="detail-title">{{ selectedLocation.warehouseName }}</div>
              <div class="detail-subtitle">库位编号：{{ selectedLocation.id }}</div>
            </div>
            <el-tag :type="getStatusTag(selectedLocation.statusKey)">{{ getStatusLabel(selectedLocation.statusKey) }}</el-tag>
          </div>

          <div class="capacity-box">
            <el-progress :percentage="normalizePercent(selectedLocation.capacityPercentage)" :stroke-width="10"/>
            <div class="capacity-text">
              <span>当前容量 {{ selectedLocation.curCapacity ?? '-' }}</span>
              <span>最大容量 {{ selectedLocation.maxCapacity ?? '-' }}</span>
            </div>
          </div>

          <div class="detail-grid">
            <div class="detail-item">
              <span>当前产品</span>
              <strong>{{ currentProducts || '-' }}</strong>
            </div>
            <div class="detail-item">
              <span>产品类型</span>
              <strong>{{ firstProduct?.productType || '-' }}</strong>
            </div>
            <div class="detail-item">
              <span>筛网</span>
              <strong>{{ firstProduct?.screenMeshName || '-' }}</strong>
            </div>
            <div class="detail-item">
              <span>最近更新时间</span>
              <strong>{{ formatDateTime(firstProduct?.updatedAt || firstProduct?.entryDate || selectedLocation.updatedAt) }}</strong>
            </div>
          </div>

          <div class="detail-actions">
            <el-button type="primary" @click="router.push('/warehouse')">查看库位明细</el-button>
            <el-button @click="clearSelection">清空选择</el-button>
          </div>

          <div class="product-list">
            <div class="section-title">产品明细</div>
            <el-empty v-if="!detailRecords.length" description="暂无产品明细" :image-size="80"/>
            <div v-for="item in detailRecords.slice(0, 5)" :key="`${item.productId}-${item.entryDate}`" class="product-card">
              <div class="product-title">{{ item.productName || '-' }}</div>
              <div class="product-meta">
                <span>入库日期：{{ item.entryDate || '-' }}</span>
                <span>数量：{{ item.totalQuantity ?? '-' }}</span>
                <span>件数：{{ item.totalPieces ?? '-' }}</span>
              </div>
            </div>
          </div>
        </template>
        <el-empty v-else description="点击左侧库位查看详情" :image-size="110"/>
      </el-card>
    </div>
  </div>
</template>

<script setup>
import {computed, onMounted, ref} from 'vue'
import {useRouter} from 'vue-router'
import {ElMessage} from 'element-plus'
import {getAllWarehouseCapacity, getWarehouseInfo, getWarehouseList} from '@/api/warehouseinfo'
import {getStandard} from '@/api/standard'
import {getMesh} from '@/api/mesh'
import {formatDateTime} from '@/utils/dateTime'

const router = useRouter()
const viewBoxWidth = 1200
const viewBoxHeight = 800
const verticalGridLines = [150, 300, 450, 600, 750, 900, 1050]
const horizontalGridLines = [100, 200, 300, 400, 500, 600, 700]

const statusOptions = [
  {label: '正常', value: 'normal'},
  {label: '空置', value: 'empty'},
  {label: '临期预警', value: 'danger'},
  {label: '满仓', value: 'full'},
  {label: '维护中', value: 'maintenance'}
]

const searchForm = ref({
  productName: '',
  standardNames: [],
  screenMeshId: '',
  dateRange: [],
  status: ''
})
const standards = ref([])
const meshList = ref([])
const loadingMap = ref(false)
const detailLoading = ref(false)
const capacityList = ref([])
const matchedWarehouseIds = ref([])
const serverFilterApplied = ref(false)
const selectedLocation = ref(null)
const detailRecords = ref([])

const createLocation = (id, x, y, width, height, extra = {}) => ({
  id,
  x,
  y,
  width,
  height,
  statusKey: 'default',
  ...extra
})

const baseLayout = [
  createLocation(1007, 20, 600, 800, 200, {name: '二楼库房'}),
  createLocation(1011, 860, 600, 100, 80, {name: '红糖库'}),
  createLocation(1000, 320, 50, 700, 150, {
    name: '包装间库',
    shape: 'polygon',
    points: [[0, 0], [700, 0], [700, 70], [450, 70], [450, 150], [0, 150]]
  }),
  createLocation(1010, 320, 210, 450, 45, {name: '怡宝击破组 包装一楼'}),
  createLocation(1008, 800, 430, 80, 130, {name: '电梯口'}),
  createLocation(1009, 1080, 145, 100, 40, {name: '办公室门口'}),
  ...[1001, 1002, 1003, 1004, 1005, 1006].map((id, index) =>
      createLocation(id, 780 + index * 40, 130, 30, 130, {name: `烘房${index + 1}`})
  ),
  ...[
    [1, 992], [2, 956], [3, 926], [4, 896], [5, 860], [6, 830], [7, 800], [8, 758],
    [9, 728], [10, 698], [11, 662], [12, 632], [13, 602], [14, 566], [15, 536],
    [16, 506], [17, 470], [18, 440], [19, 410], [20, 374], [21, 314], [22, 272],
    [23, 242], [24, 212], [25, 176]
  ].map(([id, x]) => createLocation(id, x, 270, 30, 130)),
  ...[
    [26, 238], [27, 208], [28, 178], [29, 142], [30, 82]
  ].map(([id, y]) => createLocation(id, 176, y, 130, id === 30 ? 60 : 30)),
  ...[
    [31, 82], [32, 112], [33, 142], [34, 178], [35, 208], [36, 238],
    [37, 274], [38, 304], [39, 334], [40, 370]
  ].map(([id, y]) => createLocation(id, 20, y, 130, 30)),
  ...[
    [41, 20], [42, 50], [43, 80], [44, 116], [45, 146], [46, 176], [47, 212],
    [48, 242], [49, 272], [50, 314], [51, 374], [52, 410], [53, 440], [54, 470],
    [55, 506], [56, 536], [57, 566], [58, 602], [59, 632], [60, 662], [61, 698],
    [62, 728], [63, 758], [64, 896], [65, 926], [66, 956], [67, 992]
  ].map(([id, x]) => createLocation(id, x, 430, 30, 130)),
  ...[
    [68, 500], [69, 450], [70, 390], [71, 340], [72, 290], [73, 240], [74, 190], [75, 90]
  ].map(([id, y]) => createLocation(id, 1080, y, 100, 50))
]

const capacityMap = computed(() => new Map(capacityList.value.map(item => [item.warehouseId, item])))
const matchedSet = computed(() => new Set(matchedWarehouseIds.value))
const mapLocations = computed(() => baseLayout.map(location => {
  const capacity = capacityMap.value.get(location.id)
  const statusKey = capacity ? getWarehouseStatusKey(capacity.status) : location.statusKey
  return {
    ...location,
    ...capacity,
    warehouseName: capacity?.warehouseName || location.name || `${location.id}号库位`,
    statusKey,
    label: location.name || capacity?.warehouseName || String(location.id)
  }
}))

const summary = computed(() => {
  const initial = {total: mapLocations.value.length, normal: 0, full: 0, danger: 0}
  mapLocations.value.forEach(item => {
    if (item.statusKey === 'normal') initial.normal += 1
    if (item.statusKey === 'full') initial.full += 1
    if (item.statusKey === 'danger') initial.danger += 1
  })
  return initial
})
const firstProduct = computed(() => detailRecords.value[0] || null)
const currentProducts = computed(() => {
  const names = [...new Set(detailRecords.value.map(item => item.productName).filter(Boolean))]
  if (!names.length) return ''
  return names.slice(0, 3).join('、') + (names.length > 3 ? ` 等${names.length}种` : '')
})

const getWarehouseStatusKey = (status) => {
  if (status === '正常') return 'normal'
  if (status === '空置') return 'empty'
  if (status === '临期预警') return 'danger'
  if (status === '满仓') return 'full'
  if (status === '维护' || status === '维护中') return 'maintenance'
  return 'default'
}
const getStatusLabel = (statusKey) => statusOptions.find(item => item.value === statusKey)?.label || '未知'
const getStatusTag = (statusKey) => {
  const tagMap = {
    normal: 'success',
    empty: 'info',
    danger: 'warning',
    full: 'danger',
    maintenance: 'info',
    default: 'info'
  }
  return tagMap[statusKey] || 'info'
}
const normalizePercent = (value) => {
  const number = Number(value)
  if (Number.isNaN(number)) return 0
  if (number <= 1) return Math.round(number * 100)
  return Math.min(Math.round(number), 100)
}
const getPolygonPoints = (location) => location.points
    .map(point => `${location.x + point[0]},${location.y + point[1]}`)
    .join(' ')
const isMatched = (location) => matchedSet.value.has(location.id)
const hasSearchCriteria = computed(() => Boolean(
    searchForm.value.productName ||
    searchForm.value.standardNames.length ||
    searchForm.value.screenMeshId ||
    searchForm.value.dateRange.length ||
    searchForm.value.status
))
const isDimmed = (location) => {
  if (!hasSearchCriteria.value) return false
  if (searchForm.value.status && location.statusKey !== searchForm.value.status) return true
  return serverFilterApplied.value && !isMatched(location)
}
const getLocationTitle = (location) => `${location.warehouseName} / ${getStatusLabel(location.statusKey)} / 占用率 ${normalizePercent(location.capacityPercentage)}%`

const fetchCapacity = async () => {
  loadingMap.value = true
  const res = await getAllWarehouseCapacity()
  if (res.code === 200) {
    capacityList.value = res.data || []
  } else {
    ElMessage.error(res.msg || '获取库位容量失败')
  }
  loadingMap.value = false
}
const fetchOptions = async () => {
  const [standardRes, meshRes] = await Promise.all([getStandard({}), getMesh({})])
  if (standardRes.code === 200) {
    standards.value = standardRes.data || []
  }
  if (meshRes.code === 200) {
    meshList.value = meshRes.data || []
  }
}
const handleSearch = async () => {
  await fetchCapacity()
  matchedWarehouseIds.value = []
  serverFilterApplied.value = false
  const params = {}
  if (searchForm.value.productName) params.productName = searchForm.value.productName
  if (searchForm.value.standardNames.length) params.standardNames = searchForm.value.standardNames
  if (searchForm.value.screenMeshId) params.screenMeshId = searchForm.value.screenMeshId
  if (searchForm.value.dateRange.length === 2) {
    params.startTime = searchForm.value.dateRange[0]
    params.endTime = searchForm.value.dateRange[1]
  }
  if (Object.keys(params).length) {
    serverFilterApplied.value = true
    loadingMap.value = true
    const res = await getWarehouseList(params)
    if (res.code === 200) {
      matchedWarehouseIds.value = (res.data || []).map(item => item.warehouseId)
    } else {
      ElMessage.error(res.msg || '查询库位失败')
    }
    loadingMap.value = false
  }
}
const handleReset = async () => {
  searchForm.value = {
    productName: '',
    standardNames: [],
    screenMeshId: '',
    dateRange: [],
    status: ''
  }
  matchedWarehouseIds.value = []
  serverFilterApplied.value = false
  await fetchCapacity()
}
const handleSelectLocation = async (location) => {
  selectedLocation.value = {...location}
  detailRecords.value = []
  detailLoading.value = true
  const res = await getWarehouseInfo({
    warehouseId: location.id,
    page: 1,
    size: 1000
  })
  if (res.code === 200) {
    detailRecords.value = res.data?.records || []
  } else {
    ElMessage.error(res.msg || '获取库位明细失败')
  }
  detailLoading.value = false
}
const clearSelection = () => {
  selectedLocation.value = null
  detailRecords.value = []
}

onMounted(async () => {
  await Promise.all([fetchCapacity(), fetchOptions()])
})
</script>

<style scoped>
.warehouse-map-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.legend-card :deep(.el-card__body) {
  padding: 12px 16px;
}

.legend-list {
  display: flex;
  flex-wrap: wrap;
  gap: 12px 18px;
  align-items: center;
}

.legend-item {
  display: inline-flex;
  gap: 8px;
  align-items: center;
  color: var(--app-text-secondary);
  font-size: 13px;
}

.legend-dot {
  width: 10px;
  height: 10px;
  border-radius: 50%;
  border: 1px solid transparent;
}

.legend-dot.normal { background: #dcfce7; border-color: #86efac; }
.legend-dot.empty { background: #f1f5f9; border-color: #cbd5e1; }
.legend-dot.danger { background: #fff7ed; border-color: #fed7aa; }
.legend-dot.full { background: #fee2e2; border-color: #fca5a5; }
.legend-dot.maintenance { background: #e5e7eb; border-color: #9ca3af; }
.legend-dot.matched { background: #dbeafe; border-color: #60a5fa; }

.map-workspace {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 360px;
  gap: 16px;
  align-items: start;
}

.map-card,
.detail-panel {
  min-width: 0;
}

.map-toolbar,
.detail-head {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  align-items: flex-start;
  margin-bottom: 14px;
}

.map-summary {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 8px;
  color: var(--app-text-secondary);
  font-size: 12px;
}

.map-summary span {
  padding: 5px 9px;
  border-radius: 999px;
  background: #f7f8fb;
  border: 1px solid var(--app-border-light);
}

.map-canvas {
  overflow: auto;
  border-radius: 10px;
  border: 1px solid var(--app-border-light);
  background:
      linear-gradient(135deg, rgba(255, 255, 255, 0.92), rgba(248, 250, 252, 0.92)),
      radial-gradient(circle at top left, rgba(64, 128, 255, 0.08), transparent 28%);
  padding: 18px;
}

.warehouse-map-svg {
  width: 100%;
  min-width: 920px;
  height: auto;
  display: block;
}

.map-grid line {
  stroke: rgba(148, 163, 184, 0.12);
  stroke-width: 1;
}

.map-location {
  cursor: pointer;
  stroke: rgba(100, 116, 139, 0.34);
  stroke-width: 1.2;
  transition: fill 0.16s ease, stroke 0.16s ease, filter 0.16s ease, opacity 0.16s ease;
}

.map-location.default { fill: #ffffff; }
.map-location.normal { fill: #dcfce7; stroke: #86efac; }
.map-location.empty { fill: #f1f5f9; stroke: #cbd5e1; }
.map-location.danger { fill: #fff7ed; stroke: #fed7aa; }
.map-location.full { fill: #fee2e2; stroke: #fca5a5; }
.map-location.maintenance { fill: #e5e7eb; stroke: #9ca3af; }

.map-location:hover {
  filter: drop-shadow(0 6px 12px rgba(15, 23, 42, 0.12));
  stroke: var(--app-primary);
}

.map-location.active {
  stroke: var(--app-primary);
  stroke-width: 3;
  filter: drop-shadow(0 8px 18px rgba(64, 128, 255, 0.22));
}

.map-location.matched {
  stroke: #2563eb;
  stroke-width: 2.2;
}

.map-location.dimmed {
  opacity: 0.28;
}

.map-location-label {
  pointer-events: none;
  text-anchor: middle;
  dominant-baseline: central;
  fill: #334155;
  font-size: 13px;
  font-weight: 650;
}

.map-location-label.compact {
  font-size: 11px;
}

.detail-title {
  color: var(--app-text);
  font-size: 18px;
  font-weight: 700;
}

.detail-subtitle {
  margin-top: 4px;
  color: var(--app-text-tertiary);
  font-size: 12px;
}

.capacity-box {
  padding: 14px;
  border-radius: 10px;
  background: #f8fafc;
  border: 1px solid var(--app-border-light);
}

.capacity-text {
  display: flex;
  justify-content: space-between;
  margin-top: 10px;
  color: var(--app-text-secondary);
  font-size: 12px;
}

.detail-grid {
  display: grid;
  grid-template-columns: 1fr;
  gap: 10px;
  margin-top: 14px;
}

.detail-item {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  padding: 10px 0;
  border-bottom: 1px solid var(--app-border-light);
}

.detail-item span {
  flex: none;
  color: var(--app-text-tertiary);
  font-size: 12px;
}

.detail-item strong {
  min-width: 0;
  color: var(--app-text);
  font-size: 13px;
  font-weight: 650;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.detail-actions {
  display: flex;
  gap: 8px;
  margin-top: 16px;
}

.product-list {
  margin-top: 18px;
}

.product-card {
  margin-top: 10px;
  padding: 12px;
  border-radius: 8px;
  background: #ffffff;
  border: 1px solid var(--app-border-light);
}

.product-title {
  color: var(--app-text);
  font-weight: 650;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.product-meta {
  display: flex;
  flex-direction: column;
  gap: 4px;
  margin-top: 8px;
  color: var(--app-text-secondary);
  font-size: 12px;
}

@media (max-width: 1180px) {
  .map-workspace {
    grid-template-columns: 1fr;
  }

  .detail-panel {
    order: 2;
  }
}
</style>
