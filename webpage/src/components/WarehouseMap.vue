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
        <el-form-item label="二维码">
          <el-input v-model="searchForm.palletCodes" placeholder="多个用逗号/空格/顿号分隔" clearable style="width: 240px"/>
        </el-form-item>
        <el-form-item label="标准名称">
          <el-select v-model="searchForm.standardNames" placeholder="请选择" clearable style="width: 220px">
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
            <g v-for="location in renderedMapLocations" :key="location.id" class="map-location-group">
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
        <div v-if="selectedLocation" class="detail-content">
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
              <span>最近更新时间</span>
              <strong>{{ formatDateTime(firstProduct?.createdAt || firstProduct?.sampleDate || selectedLocation.updatedAt) }}</strong>
            </div>
          </div>

          <div class="detail-actions">
            <el-button type="primary" @click="openLocationDetailDialog">查看库位明细</el-button>
            <el-button @click="clearSelection">清空选择</el-button>
          </div>

          <div class="panel-operation-card compact">
            <div class="section-title">批量操作</div>
            <div class="operation-actions no-indent">
              <el-button type="primary" @click="openBatchDialog('OUT')">新增出库</el-button>
              <el-button type="warning" @click="openBatchDialog('TRANSFER')">调拨出库</el-button>
            </div>
          </div>

          <div class="product-list">
            <div class="section-title">产品明细</div>
            <el-empty v-if="!detailRecords.length" description="暂无产品明细" :image-size="80"/>
            <div v-for="item in detailRecords" :key="`${item.inventoryId}-${item.productId}-${item.sampleDate}-${item.productStatus}`" class="product-card">
              <div class="product-title">{{ item.productName || '-' }}</div>
              <div class="product-meta">
                <span>库存：{{ formatProductSummaryQuantity(item) }}</span>
                <span>最早生产日期：{{ item.sampleDate || '-' }}</span>
              </div>
            </div>
            <el-pagination
                v-if="detailTotal > detailPageSize"
                class="detail-pagination"
                small
                background
                layout="prev, pager, next"
                :current-page="detailPage"
                :page-size="detailPageSize"
                :total="detailTotal"
                @current-change="handleDetailPageChange"
            />
          </div>
        </div>
        <el-empty v-else description="点击左侧库位查看详情" :image-size="110"/>
      </el-card>
    </div>

    <el-dialog
        v-model="detailDialogVisible"
        :title="`${detailDialogLocation?.warehouseName || '库位'} 明细`"
        width="920px"
        class="location-detail-dialog"
        destroy-on-close
    >
      <div v-if="isSpecialLocation(detailDialogLocation)" class="location-unsupported">
        当前库位暂不支持平面图
      </div>
      <div v-else v-loading="detailDialogLoading" class="location-dialog-layout">
        <div class="location-layout-card">
          <div class="dialog-section-head">
            <div>
              <div class="section-title">库位详细平面图</div>
            </div>
            <div class="dialog-map-actions">
              <el-button
                  class="loose-piece-button"
                  :class="{active: loosePieceHighlightMode}"
                  circle
                  size="small"
                  aria-label="查看散件"
                  :title="loosePieceHighlightMode ? '取消散件高亮' : '查看散件'"
                  @click="toggleLoosePieceHighlight"
              >
                散
              </el-button>
              <el-radio-group v-model="detailActiveLayer" size="small">
                <el-radio-button v-for="layer in detailLayers" :key="layer" :value="layer">{{ layer }}层</el-radio-button>
              </el-radio-group>
            </div>
          </div>

          <div class="slot-vertical-map">
            <div v-for="side in detailSides" :key="side" class="slot-side">
              <div class="slot-side-title">{{ side }}侧</div>
              <div class="slot-row-list">
                <button
                    v-for="row in detailRows"
                    :key="`${side}-${detailActiveLayer}-${row}`"
                    class="slot-row-cell"
                    :class="[getSlotState(side, detailActiveLayer, row), {selected: isSelectedSlot(side, detailActiveLayer, row) || isLoosePieceSlotHighlighted(side, detailActiveLayer, row)}]"
                    type="button"
                    :title="getSlotTitle(side, detailActiveLayer, row)"
                    @click="handleSelectSlot(side, detailActiveLayer, row)"
                >
                  <span>{{ row }}排</span>
                  <strong>{{ getSlotStateLabel(side, detailActiveLayer, row) }}</strong>
                </button>
              </div>
            </div>
          </div>
        </div>

        <aside class="location-info-card">
          <div class="section-title">图例</div>
          <div class="slot-legend">
            <span><i class="slot-dot empty"/>空置</span>
            <span><i class="slot-dot occupied"/>有货未命中</span>
            <span><i class="slot-dot matched"/>有货且命中</span>
            <span><i class="slot-dot selected"/>当前选中</span>
          </div>

          <div v-if="selectedSlot" class="slot-detail-card">
            <div class="section-title">{{ selectedSlot.record ? '单板详情' : '空位置操作' }}</div>
            <template v-if="selectedSlot.record">
              <div class="slot-detail-row">
                <span>二维码</span>
                <el-link type="primary" @click="goPalletCode(selectedSlot.record.palletCode)">{{ selectedSlot.record.palletCode || '-' }}</el-link>
              </div>
              <div class="slot-detail-row"><span>产品</span><strong>{{ selectedSlot.record.productName || '-' }}</strong></div>
              <div class="slot-detail-row"><span>生产日期</span><strong>{{ selectedSlot.record.sampleDate || '-' }}</strong></div>
              <div class="slot-detail-row"><span>数量</span><strong>{{ formatInventoryQuantity(selectedSlot.record) }}</strong></div>
              <div class="slot-detail-row"><span>当前位置</span><strong>{{ formatInventoryPosition(selectedSlot.record) }}</strong></div>
              <div class="slot-action-row">
                <el-button size="small" @click="showAssay(selectedSlot.record)">查看化验</el-button>
                <el-button size="small" type="primary" @click="submitSingleOut(selectedSlot.record)">出库</el-button>
                <el-button size="small" type="warning" @click="prepareSingleTransfer(selectedSlot.record)">调拨</el-button>
              </div>
            </template>
            <template v-else>
              <div class="slot-detail-row"><span>当前位置</span><strong>{{ formatSlotPosition(selectedSlot) }}</strong></div>
              <div class="slot-detail-row"><span>当前状态</span><strong>空置</strong></div>
              <div class="slot-action-row">
                <el-button size="small" type="primary" @click="openSlotInboundDialog(selectedSlot)">入库</el-button>
              </div>
            </template>
          </div>

          <div v-else class="location-summary-list">
            <div>
              <span>库位</span>
              <strong>{{ detailDialogLocation?.warehouseName || '-' }}</strong>
            </div>
            <div>
              <span>状态</span>
              <strong>{{ getStatusLabel(detailDialogLocation?.statusKey) }}</strong>
            </div>
            <div>
              <span>有货位置</span>
              <strong>{{ occupiedPositionCount }}</strong>
            </div>
            <div>
              <span>命中位置</span>
              <strong>{{ matchedPositionCount }}</strong>
            </div>
          </div>

          <div class="dialog-products">
            <div class="section-title">库位内产品</div>
            <el-empty v-if="!detailAllPositions.length" description="暂无库存位置" :image-size="70"/>
            <div v-for="item in detailAllPositions.slice(0, 8)" :key="item.inventoryId || `${item.side}-${item.layer}-${item.rowNumber}-${item.productName}`" class="dialog-product-row">
              <div>{{ item.productName || '-' }}</div>
              <span>{{ formatInventoryPosition(item) }} · {{ item.sampleDate || '-' }} · {{ item.palletCode || '无二维码' }} · {{ formatInventoryQuantity(item) }}</span>
            </div>
          </div>
        </aside>
      </div>
    </el-dialog>

    <el-dialog v-model="taskResultVisible" title="任务创建结果" width="520px">
      <div class="task-result">
        <div class="batch-no">单据批次：{{ taskResult?.operationBatchNo || '-' }}</div>
        <div v-for="item in taskResultItems" :key="`${item.productStatus}-${item.taskType}-${item.bizScene}`" class="task-result-row">
          <span>{{ buildResultText(item) }}</span>
          <el-button link type="primary" @click="router.push(item.routePath)">去处理</el-button>
        </div>
      </div>
      <template #footer>
        <el-button @click="taskResultVisible = false">关闭</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="batchDialogVisible" :title="batchDialogTitle" width="520px">
      <el-form :model="batchForm" label-width="86px" size="small">
        <el-form-item label="操作类型">
          <strong>{{ batchDialogTitle }}</strong>
        </el-form-item>
        <el-form-item label="侧别">
          <el-radio-group v-model="batchForm.side">
            <el-radio-button value="左">左</el-radio-button>
            <el-radio-button value="右">右</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="数量">
          <el-input-number v-model="batchForm.quantity" :min="1" :max="batchInputMax" controls-position="right"/>
          <span class="operation-hint">当前侧可操作 {{ batchMaxQuantity }} 板</span>
        </el-form-item>
        <div v-if="batchForm.codes.length" class="single-code-hint dialog-hint">
          <span>已锁定单板：{{ batchForm.codes.join('、') }}</span>
          <el-button type="primary" link @click="batchForm.codes = []">改用前N板</el-button>
        </div>
        <template v-if="batchForm.operationType === 'TRANSFER'">
          <el-form-item label="目标库位">
            <el-select v-model="batchForm.targetWarehouseName" filterable placeholder="选择目标库位">
              <el-option
                  v-for="item in targetWarehouseOptions"
                  :key="item.warehouseId"
                  :label="item.warehouseName"
                  :value="item.warehouseName"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="目标侧">
            <el-radio-group v-model="batchForm.targetSide">
              <el-radio-button value="左">左</el-radio-button>
              <el-radio-button value="右">右</el-radio-button>
            </el-radio-group>
          </el-form-item>
        </template>
        <el-form-item label="备注">
          <el-input v-model="batchForm.remark" type="textarea" :rows="2" placeholder="可选"/>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="batchDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="batchSubmitting" @click="submitBatchOperation">创建任务</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="slotInboundDialogVisible" title="单板入库任务" width="560px" :before-close="closeSlotInboundDialog">
      <el-form ref="slotInboundFormRef" :model="slotInboundForm" :rules="slotInboundRules" label-width="110px">
        <el-form-item label="目标位置">
          <strong>{{ formatSlotPosition(slotInboundTarget) }}</strong>
        </el-form-item>
        <el-form-item label="二维码" prop="code">
          <el-input v-model="slotInboundForm.code" clearable placeholder="请输入二维码"/>
        </el-form-item>
        <el-form-item label="产品" prop="productId">
          <el-cascader
              v-model="slotInboundForm.productId"
              :options="productOptions"
              :props="productCascaderProps"
              clearable
              filterable
              placeholder="请选择产品"
              style="width: 100%"
              @change="handleSlotInboundProductChange"
          />
        </el-form-item>
        <el-form-item label="生产日期" prop="productionDate">
          <el-date-picker v-model="slotInboundForm.productionDate" value-format="YYYY-MM-DD" type="date" style="width: 100%"/>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="slotInboundForm.remark" type="textarea" :rows="2" placeholder="可选"/>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="closeSlotInboundDialog">取消</el-button>
        <el-button type="primary" :loading="slotInboundSubmitting" @click="submitSlotInbound">直接入库</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="assayDialogVisible" title="化验记录" width="560px">
      <div class="assay-detail" v-loading="assayLoading">
        <div><span>当前二维码</span><strong>{{ selectedSlot?.record?.palletCode || '-' }}</strong></div>
        <div><span>产品</span><strong>{{ assayDetail?.productName || selectedSlot?.record?.productName || '-' }}</strong></div>
        <div><span>检测日期</span><strong>{{ assayDetail?.sampleDate || selectedSlot?.record?.sampleDate || '-' }}</strong></div>
        <div><span>是否合格</span><strong>{{ assayDetail?.isQualified || '-' }}</strong></div>
        <div><span>标准名称</span><strong>{{ assayDetail?.qualifiedStandards || selectedSlot?.record?.standardNames || '-' }}</strong></div>
        <div><span>筛网</span><strong>{{ selectedSlot?.record?.meshName || '-' }}</strong></div>
        <div><span>色值</span><strong>{{ assayDetail?.colorValue ?? '-' }}</strong></div>
        <div><span>还原糖</span><strong>{{ assayDetail?.reducingSugar ?? '-' }}</strong></div>
        <div><span>干燥失重</span><strong>{{ assayDetail?.dryWeight ?? '-' }}</strong></div>
        <div><span>电导灰分</span><strong>{{ assayDetail?.conductivityAsh ?? '-' }}</strong></div>
        <div><span>蔗糖分</span><strong>{{ assayDetail?.sucrose ?? '-' }}</strong></div>
        <div><span>不溶杂质</span><strong>{{ assayDetail?.insolubleImpurity ?? '-' }}</strong></div>
        <div><span>pH</span><strong>{{ assayDetail?.phValue ?? '-' }}</strong></div>
        <div><span>化验员</span><strong>{{ assayDetail?.testerName || '-' }}</strong></div>
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
import {computed, onMounted, ref, watch} from 'vue'
import {ElMessage} from 'element-plus'
import {useRoute, useRouter} from 'vue-router'
import {getAllWarehouseCapacity, getMaxRowNum, getWarehouseById, getWarehouseInventoryPage, getWarehouseList} from '@/api/warehouseinfo'
import {createWarehouseMapSlotInbound, createWarehouseMapTasks, getPalletAssay} from '@/api/palletCode'
import {getStandard} from '@/api/standard'
import {getMesh} from '@/api/mesh'
import {getProductList} from '@/api/product'
import {formatDateTime} from '@/utils/dateTime'
import {buildProductCascaderOptions, productCascaderProps} from '@/utils/productCascader'

const router = useRouter()
const route = useRoute()
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
  palletCodes: '',
  standardNames: '',
  screenMeshId: '',
  dateRange: [],
  status: ''
})
const standards = ref([])
const meshList = ref([])
const productList = ref([])
const productOptions = computed(() => buildProductCascaderOptions(productList.value))
const loadingMap = ref(false)
const detailLoading = ref(false)
const capacityList = ref([])
const matchedWarehouseIds = ref([])
const serverFilterApplied = ref(false)
const selectedLocation = ref(null)
const detailRecords = ref([])
const detailPage = ref(1)
const detailPageSize = ref(5)
const detailTotal = ref(0)
const detailDialogVisible = ref(false)
const detailDialogLoading = ref(false)
const detailDialogLocation = ref(null)
const detailAllPositions = ref([])
const detailMatchedPositions = ref([])
const detailMaxRows = ref(0)
const detailActiveLayer = ref(1)
const detailSides = ['左', '右']
const detailLayers = [1, 2]
const selectedSlot = ref(null)
const loosePieceHighlightMode = ref(false)
const batchDialogVisible = ref(false)
const batchSubmitting = ref(false)
const slotInboundDialogVisible = ref(false)
const slotInboundSubmitting = ref(false)
const slotInboundFormRef = ref(null)
const slotInboundTarget = ref(null)
const slotInboundForm = ref(defaultSlotInboundForm())
const taskResultVisible = ref(false)
const taskResult = ref(null)
const assayDialogVisible = ref(false)
const assayLoading = ref(false)
const assayDetail = ref(null)
function defaultSlotInboundForm() {
  return {
    code: '',
    productId: null,
    productStatus: '',
    productionDate: '',
    quantity: 1,
    unit: '0',
    remark: ''
  }
}

const batchForm = ref({
  operationType: 'OUT',
  side: '左',
  quantity: 1,
  codes: [],
  targetWarehouseName: '',
  targetSide: '左',
  remark: ''
})
const slotInboundRules = {
  code: [{required: true, message: '请输入二维码', trigger: 'blur'}],
  productId: [{required: true, message: '请选择产品', trigger: 'change'}],
  productionDate: [{required: true, message: '请选择生产日期', trigger: 'change'}]
}
const generalLocationIds = new Set([...Array.from({length: 75}, (_, index) => index + 1), 1008, 1009])

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
const renderedMapLocations = computed(() => {
  if (!selectedLocation.value) return mapLocations.value
  return [...mapLocations.value].sort((left, right) => {
    if (left.id === selectedLocation.value.id) return 1
    if (right.id === selectedLocation.value.id) return -1
    return 0
  })
})

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
  return names.join('、')
})
const formatInventoryPosition = (item) => {
  if (!item) return '-'
  return [item.side ? `${item.side}侧` : '', item.layer ? `${item.layer}层` : '', item.rowNumber ? `${item.rowNumber}排` : '']
      .filter(Boolean)
      .join(' ') || '-'
}
const formatInventoryQuantity = (item) => {
  if (!item) return '-'
  const quantity = Number(item.quantity || 0)
  const pieces = Number(item.pieces || 0)
  if (pieces > 0) return `${pieces}件，占1板位`
  if (quantity > 0) return `${quantity}板`
  return '0'
}
const formatProductSummaryQuantity = (item) => {
  if (!item) return '-'
  const quantity = Number(item.quantity || 0)
  const pieces = Number(item.pieces || 0)
  if (quantity > 0 && pieces > 0) return `${quantity}板${pieces}件`
  if (quantity > 0) return `${quantity}板`
  if (pieces > 0) return `${pieces}件`
  return '0'
}
const detailMaxRowsSafe = computed(() => Math.max(detailMaxRows.value, 1))
const detailRows = computed(() => Array.from({length: detailMaxRowsSafe.value}, (_, index) => index + 1))
const occupiedPositionSet = computed(() => buildPositionSet(detailAllPositions.value))
const matchedPositionSet = computed(() => buildPositionSet(detailMatchedPositions.value))
const loosePiecePositionSet = computed(() => buildPositionSet(detailAllPositions.value.filter(isLoosePieceInventory)))
const occupiedPositionCount = computed(() => occupiedPositionSet.value.size)
const matchedPositionCount = computed(() => matchedPositionSet.value.size)
const loosePiecePositionCount = computed(() => loosePiecePositionSet.value.size)
const targetWarehouseOptions = computed(() => capacityList.value
    .filter(item => item.warehouseId !== selectedLocation.value?.id)
    .slice()
    .sort((left, right) => Number(left.warehouseId) - Number(right.warehouseId)))
const batchMaxQuantity = computed(() => detailAllPositions.value.filter(item => item.side === batchForm.value.side && item.palletCode).length)
const batchInputMax = computed(() => Math.max(batchMaxQuantity.value, 1))
const batchDialogTitle = computed(() => {
  if (batchForm.value.operationType === 'TRANSFER') return '调拨出库'
  return '新增出库'
})
const taskResultItems = computed(() => taskResult.value?.items || [])

const buildFilterParams = () => {
  const params = {}
  if (searchForm.value.productName) params.productName = searchForm.value.productName
  const palletCodes = normalizePalletCodes(searchForm.value.palletCodes)
  if (palletCodes) params.palletCodes = palletCodes
  if (searchForm.value.standardNames) params.standardNames = searchForm.value.standardNames
  if (searchForm.value.screenMeshId) params.screenMeshId = searchForm.value.screenMeshId
  if (searchForm.value.dateRange.length === 2) {
    params.startDate = searchForm.value.dateRange[0]
    params.endDate = searchForm.value.dateRange[1]
  }
  return params
}
const normalizePalletCodes = (value) => (value || '').trim().replace(/[,，、\s]+/g, ',').replace(/^,+|,+$/g, '')
const buildPositionKey = (side, layer, rowNumber) => `${side || ''}-${layer || 1}-${rowNumber || ''}`
const buildPositionSet = (records) => new Set((records || [])
    .filter(item => item.side && item.rowNumber)
    .map(item => buildPositionKey(item.side, item.layer || 1, item.rowNumber))
)
const isLoosePieceInventory = (item) => Number(item?.pieces || 0) > 0
const getPositionRecord = (records, side, layer, rowNumber) => (records || [])
    .find(item => buildPositionKey(item.side, item.layer || 1, item.rowNumber) === buildPositionKey(side, layer, rowNumber))
const getSlotState = (side, layer, rowNumber) => {
  const key = buildPositionKey(side, layer, rowNumber)
  if (matchedPositionSet.value.has(key)) return 'matched'
  if (occupiedPositionSet.value.has(key)) return 'occupied'
  return 'empty'
}
const isSelectedSlot = (side, layer, rowNumber) => {
  if (!selectedSlot.value) return false
  return selectedSlot.value.side === side
      && selectedSlot.value.layer === layer
      && selectedSlot.value.rowNumber === rowNumber
}
const isLoosePieceSlotHighlighted = (side, layer, rowNumber) => loosePieceHighlightMode.value
    && loosePiecePositionSet.value.has(buildPositionKey(side, layer, rowNumber))
const toggleLoosePieceHighlight = () => {
  if (!loosePiecePositionCount.value) {
    ElMessage.info('当前库位暂无散件板位')
    loosePieceHighlightMode.value = false
    return
  }
  loosePieceHighlightMode.value = !loosePieceHighlightMode.value
}
const getSlotStateLabel = (side, layer, rowNumber) => {
  const state = getSlotState(side, layer, rowNumber)
  if (state === 'matched') return '命中'
  if (state === 'occupied') return '有货'
  return '空置'
}
const getSlotTitle = (side, layer, rowNumber) => {
  const matchedRecord = getPositionRecord(detailMatchedPositions.value, side, layer, rowNumber)
  const occupiedRecord = getPositionRecord(detailAllPositions.value, side, layer, rowNumber)
  const record = matchedRecord || occupiedRecord
  const stateText = matchedRecord ? '有货且命中' : occupiedRecord ? '有货未命中' : '空置'
  if (!record) return `${side}侧 ${layer}层 ${rowNumber}排 / ${stateText}`
  return `${side}侧 ${layer}层 ${rowNumber}排 / ${stateText} / ${record.productName || '-'} / ${record.palletCode || '无二维码'} / ${record.sampleDate || '-'}`
}
const handleSelectSlot = (side, layer, rowNumber) => {
  selectedSlot.value = {
    side,
    layer,
    rowNumber,
    record: getPositionRecord(detailAllPositions.value, side, layer, rowNumber)
  }
}
const formatSlotPosition = (slot) => slot ? `${slot.layer}层 ${slot.side}侧 ${slot.rowNumber}排` : '-'
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
const isRegularLocation = (location) => Boolean(location && generalLocationIds.has(Number(location.id)))
const isSpecialLocation = (location) => Boolean(location && !isRegularLocation(location))
const hasSearchCriteria = computed(() => Boolean(
    searchForm.value.productName ||
    normalizePalletCodes(searchForm.value.palletCodes) ||
    searchForm.value.standardNames ||
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
  try {
    const res = await getAllWarehouseCapacity()
    if (res.code === 200) {
      capacityList.value = res.data || []
    } else {
      ElMessage.error(res.msg || '获取库位容量失败')
    }
  } catch (error) {
    ElMessage.error(error?.message || '获取库位容量失败')
  } finally {
    loadingMap.value = false
  }
}
const fetchOptions = async () => {
  try {
    const [standardRes, meshRes, productRes] = await Promise.all([getStandard({}), getMesh({}), getProductList({})])
    if (standardRes.code === 200) {
      standards.value = standardRes.data || []
    }
    if (meshRes.code === 200) {
      meshList.value = meshRes.data || []
    }
    productList.value = Array.isArray(productRes.data) ? productRes.data : (productRes.data?.records || [])
  } catch (error) {
    ElMessage.error(error?.message || '获取筛选项失败')
  }
}
const handleSearch = async () => {
  try {
    await fetchCapacity()
    matchedWarehouseIds.value = []
    serverFilterApplied.value = false
    const params = buildFilterParams()
    if (Object.keys(params).length) {
      serverFilterApplied.value = true
      loadingMap.value = true
      const res = await getWarehouseList(params)
      if (res.code === 200) {
        matchedWarehouseIds.value = (res.data || []).map(item => item.warehouseId)
      } else {
        ElMessage.error(res.msg || '查询库位失败')
      }
    }
  } catch (error) {
    ElMessage.error(error?.message || '查询库位失败')
  } finally {
    loadingMap.value = false
  }
}
const handleReset = async () => {
  searchForm.value = {
    productName: '',
    palletCodes: '',
    standardNames: '',
    screenMeshId: '',
    dateRange: [],
    status: ''
  }
  matchedWarehouseIds.value = []
  serverFilterApplied.value = false
  detailMatchedPositions.value = []
  await fetchCapacity()
}

const resolveRouteQueryValue = (value) => Array.isArray(value) ? (value[0] || '') : (value || '')

const applyRouteQueryFilters = async () => {
  const palletCode = resolveRouteQueryValue(route.query.palletCode || route.query.code)
  if (!palletCode) {
    return
  }
  searchForm.value.palletCodes = palletCode
  await handleSearch()
}

const handleSelectLocation = async (location) => {
  selectedLocation.value = {...location}
  detailPage.value = 1
  detailRecords.value = []
  detailTotal.value = 0
  await Promise.all([
    fetchLocationProductPage(location.id),
    fetchLocationOperationPositions(location.id)
  ])
}
const fetchLocationOperationPositions = async (warehouseId) => {
  const res = await getWarehouseById(warehouseId, {})
  if (res.code === 200) {
    detailAllPositions.value = res.data || []
  }
}
const fetchLocationProductPage = async (warehouseId) => {
  detailLoading.value = true
  try {
    const res = await getWarehouseInventoryPage(warehouseId, {
      page: detailPage.value,
      size: detailPageSize.value
    })
    if (res.code === 200) {
      detailRecords.value = res.data?.records || []
      detailTotal.value = res.data?.total || 0
    } else {
      ElMessage.error(res.msg || '获取库位明细失败')
    }
  } catch (error) {
    ElMessage.error(error?.message || '获取库位明细失败')
  } finally {
    detailLoading.value = false
  }
}
const handleDetailPageChange = async (page) => {
  if (!selectedLocation.value) return
  detailPage.value = page
  await fetchLocationProductPage(selectedLocation.value.id)
}
const clearSelection = () => {
  selectedLocation.value = null
  detailRecords.value = []
  detailPage.value = 1
  detailTotal.value = 0
  detailDialogVisible.value = false
  detailDialogLocation.value = null
  detailAllPositions.value = []
  detailMatchedPositions.value = []
  selectedSlot.value = null
  loosePieceHighlightMode.value = false
}
const fetchDetailPositions = async (location) => {
  const params = buildFilterParams()
  const hasServerFilters = Object.keys(params).length > 0
  const [allRes, matchedRes, warehouseRes] = await Promise.all([
    getWarehouseById(location.id, {}),
    hasServerFilters ? getWarehouseById(location.id, params) : Promise.resolve({code: 200, data: []}),
    getMaxRowNum(location.id)
  ])
  if (allRes.code !== 200) {
    throw new Error(allRes.msg || '获取库位位置失败')
  }
  if (matchedRes.code !== 200) {
    throw new Error(matchedRes.msg || '获取命中位置失败')
  }
  detailAllPositions.value = allRes.data || []
  detailMatchedPositions.value = matchedRes.data || []
  selectedSlot.value = null
  loosePieceHighlightMode.value = false
  detailMaxRows.value = warehouseRes?.data?.maxRows
      || Math.max(...detailAllPositions.value.map(item => item.rowNumber || 0), 1)
}
const openLocationDetailDialog = async () => {
  if (!selectedLocation.value) return
  detailDialogLocation.value = {...selectedLocation.value}
  detailDialogVisible.value = true
  detailActiveLayer.value = 1
  if (isSpecialLocation(selectedLocation.value)) {
    detailDialogLoading.value = false
    detailAllPositions.value = []
    detailMatchedPositions.value = []
    selectedSlot.value = null
    loosePieceHighlightMode.value = false
    detailMaxRows.value = 0
    return
  }
  detailDialogLoading.value = true
  detailAllPositions.value = []
  detailMatchedPositions.value = []
  selectedSlot.value = null
  loosePieceHighlightMode.value = false
  detailMaxRows.value = 0
  try {
    await fetchDetailPositions(selectedLocation.value)
  } catch (error) {
    ElMessage.error(error?.message || '获取库位明细失败')
  } finally {
    detailDialogLoading.value = false
  }
}

watch(() => batchForm.value.side, () => {
  if (batchForm.value.quantity > batchMaxQuantity.value) {
    batchForm.value.quantity = Math.max(batchMaxQuantity.value, 1)
  }
})

const openBatchDialog = (operationType) => {
  if (!selectedLocation.value) {
    ElMessage.warning('请先选择库位')
    return
  }
  if (operationType === 'PREPARE') {
    ElMessage.warning('半成品进入生产请在生产订单中领用')
    return
  }
  batchForm.value.operationType = operationType
  batchForm.value.codes = []
  batchForm.value.quantity = Math.min(Math.max(batchMaxQuantity.value, 1), batchForm.value.quantity || 1)
  if (operationType !== 'TRANSFER') {
    batchForm.value.targetWarehouseName = ''
    batchForm.value.targetSide = '左'
  }
  batchDialogVisible.value = true
}

const submitBatchOperation = async () => {
  if (!selectedLocation.value) {
    ElMessage.warning('请先选择库位')
    return
  }
  const hasExplicitCodes = batchForm.value.codes.length > 0
  if (!hasExplicitCodes && batchMaxQuantity.value <= 0) {
    ElMessage.warning('当前侧没有可操作板位')
    return
  }
  if (!hasExplicitCodes && batchForm.value.quantity > batchMaxQuantity.value) {
    ElMessage.warning('操作数量不能超过当前侧可操作板位数')
    return
  }
  if (batchForm.value.operationType === 'TRANSFER' && !batchForm.value.targetWarehouseName) {
    ElMessage.warning('请选择目标库位')
    return
  }
  batchSubmitting.value = true
  try {
    const res = await createWarehouseMapTasks({
      operationType: batchForm.value.operationType,
      warehouseId: selectedLocation.value.id,
      side: batchForm.value.side,
      quantity: batchForm.value.quantity,
      codes: hasExplicitCodes ? batchForm.value.codes : [],
      targetWarehouseName: batchForm.value.targetWarehouseName,
      targetSide: batchForm.value.targetSide,
      remark: batchForm.value.remark
    })
    if (res.code === 200) {
      taskResult.value = res.data
      taskResultVisible.value = true
      batchDialogVisible.value = false
      await handleSelectLocation(selectedLocation.value)
      await fetchCapacity()
      batchForm.value.codes = []
    } else {
      ElMessage.error(res.msg || '创建任务失败')
    }
  } catch (error) {
    ElMessage.error(error?.message || '创建任务失败')
  } finally {
    batchSubmitting.value = false
  }
}
const submitSingleOut = async (record) => {
  if (!record?.palletCode) {
    ElMessage.warning('当前格子缺少二维码')
    return
  }
  batchSubmitting.value = true
  try {
    const res = await createWarehouseMapTasks({
      operationType: 'OUT',
      warehouseId: detailDialogLocation.value.id,
      side: record.side,
      quantity: 1,
      codes: [record.palletCode],
      remark: `格子级出库：${formatInventoryPosition(record)}`
    })
    if (res.code === 200) {
      taskResult.value = res.data
      taskResultVisible.value = true
    } else {
      ElMessage.error(res.msg || '创建出库任务失败')
    }
  } catch (error) {
    ElMessage.error(error?.message || '创建出库任务失败')
  } finally {
    batchSubmitting.value = false
  }
}
const openSlotInboundDialog = (slot) => {
  if (!slot || slot.record) return
  slotInboundTarget.value = {...slot}
  slotInboundForm.value = defaultSlotInboundForm()
  slotInboundForm.value.remark = `平面图单板入库目标位置：${formatSlotPosition(slot)}`
  slotInboundDialogVisible.value = true
}
const closeSlotInboundDialog = () => {
  slotInboundDialogVisible.value = false
  slotInboundFormRef.value?.resetFields()
  slotInboundForm.value = defaultSlotInboundForm()
  slotInboundTarget.value = null
}
const handleSlotInboundProductChange = (productId) => {
  const product = productList.value.find(item => item.id === productId)
  slotInboundForm.value.productStatus = product?.status || product?.productStatus || ''
}
const submitSlotInbound = async () => {
  await slotInboundFormRef.value?.validate()
  if (!detailDialogLocation.value || !slotInboundTarget.value) {
    ElMessage.warning('缺少目标库位信息')
    return
  }
  slotInboundSubmitting.value = true
  try {
    const res = await createWarehouseMapSlotInbound({
      ...slotInboundForm.value,
      warehouseName: detailDialogLocation.value.warehouseName,
      side: slotInboundTarget.value.side,
      rowNumber: slotInboundTarget.value.rowNumber,
      layer: slotInboundTarget.value.layer
    })
    if (res.code !== 200) {
      ElMessage.error(res.msg || '单板入库失败')
      return
    }
    ElMessage.success('入库成功')
    closeSlotInboundDialog()
    await fetchDetailPositions(detailDialogLocation.value)
    if (selectedLocation.value) {
      await handleSelectLocation(selectedLocation.value)
    }
    await fetchCapacity()
  } catch (error) {
    ElMessage.error(error?.message || '单板入库失败')
  } finally {
    slotInboundSubmitting.value = false
  }
}
const prepareSingleTransfer = (record) => {
  if (!record) return
  batchForm.value.operationType = 'TRANSFER'
  batchForm.value.side = record.side || '左'
  batchForm.value.quantity = 1
  batchForm.value.codes = record.palletCode ? [record.palletCode] : []
  batchForm.value.remark = `板级调拨：${formatInventoryPosition(record)}，二维码${record.palletCode || ''}`
  batchDialogVisible.value = true
}
const isSemiProductSlot = (record) => record?.productStatus === '半成品'
const openUnsupportedInbound = (actionName, slot) => {
  const position = slot ? `，目标位置：${formatSlotPosition(slot)}` : ''
  ElMessage.warning(`${actionName}需要先扫码确认二维码和产品信息${position}，当前页面仅预留入口，暂不直接创建空位入库任务`)
}
const goPalletCode = (code) => {
  if (!code) return
  router.push({path: '/pallet-code/list', query: {code}})
}
const showAssay = async (record) => {
  assayDetail.value = null
  assayDialogVisible.value = true
  if (!record?.palletCode) return
  assayLoading.value = true
  try {
    const res = await getPalletAssay(record.palletCode)
    if (res.code === 200) {
      assayDetail.value = res.data
    } else {
      ElMessage.error(res.msg || '获取化验记录失败')
    }
  } catch (error) {
    ElMessage.error(error?.message || '获取化验记录失败')
  } finally {
    assayLoading.value = false
  }
}
const buildResultText = (item) => {
  const typeName = item.taskType === 'TRANSFER' ? '调拨任务' : (item.taskType === 'PREPARE' ? '历史生产占用任务' : '出库任务')
  return `创建了 ${item.count} 条${item.productStatus}${typeName}`
}

const selectWarehouseFromRoute = async () => {
  const warehouseId = Number(route.query.warehouseId)
  const warehouseName = resolveRouteQueryValue(route.query.warehouseName)
  let target = null
  if (warehouseId) {
    target = mapLocations.value.find(item => item.id === warehouseId) || null
  }
  if (!target && warehouseName) {
    target = mapLocations.value.find(item => item.warehouseName === warehouseName) || null
  }
  if (target) {
    await handleSelectLocation(target)
  }
}

onMounted(async () => {
  await Promise.all([fetchCapacity(), fetchOptions()])
  await applyRouteQueryFilters()
  await selectWarehouseFromRoute()
})

watch(() => [route.query.warehouseId, route.query.warehouseName], () => {
  selectWarehouseFromRoute()
})

watch(() => [route.query.palletCode, route.query.code], async ([palletCode, code]) => {
  const value = resolveRouteQueryValue(palletCode || code)
  if (!value) {
    return
  }
  searchForm.value.palletCodes = value
  await handleSearch()
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
  align-items: flex-start;
  gap: 12px;
  padding: 10px 0;
  border-bottom: 1px solid var(--app-border-light);
}

.detail-item span {
  flex: 0 0 84px;
  color: var(--app-text-tertiary);
  font-size: 12px;
  line-height: 1.6;
}

.detail-item strong {
  min-width: 0;
  flex: 1;
  color: var(--app-text);
  font-size: 13px;
  font-weight: 650;
  line-height: 1.65;
  overflow: visible;
  text-align: right;
  text-overflow: initial;
  white-space: normal;
  word-break: break-word;
}

.detail-item:first-child strong {
  text-align: left;
}

.detail-item:nth-child(4) strong {
  min-width: 150px;
  margin-right: 4px;
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
  line-height: 1.5;
  overflow: visible;
  text-overflow: initial;
  white-space: normal;
  word-break: break-word;
}

.product-meta {
  display: flex;
  flex-direction: column;
  gap: 4px;
  margin-top: 8px;
  color: var(--app-text-secondary);
  font-size: 12px;
}

.detail-pagination {
  justify-content: center;
  margin-top: 14px;
}

.location-dialog-layout {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 260px;
  gap: 16px;
  min-height: 360px;
}

.location-unsupported {
  display: grid;
  min-height: 240px;
  place-items: center;
  border: 1px dashed #cbd5e1;
  border-radius: 14px;
  background: #f8fafc;
  color: var(--app-text-secondary);
  font-size: 15px;
  font-weight: 650;
}

.location-layout-card,
.location-info-card {
  min-width: 0;
  padding: 14px;
  border: 1px solid var(--app-border-light);
  border-radius: 12px;
  background: #ffffff;
}

.dialog-section-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  margin-bottom: 12px;
}

.dialog-map-actions {
  display: inline-flex;
  align-items: center;
  gap: 10px;
}

.loose-piece-button {
  flex: none;
  border-color: #f97316;
  color: #c2410c;
  font-weight: 800;
}

.loose-piece-button.active {
  border-color: #ea580c;
  background: #ea580c;
  color: #ffffff;
}

.slot-vertical-map {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px;
}

.slot-side {
  min-width: 0;
  padding: 12px;
  border: 1px solid #e2e8f0;
  border-radius: 12px;
  background: #f8fafc;
}

.slot-side-title {
  margin-bottom: 10px;
  color: var(--app-text);
  font-weight: 700;
}

.slot-row-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
  max-height: 430px;
  overflow-y: auto;
  padding-right: 4px;
}

.slot-row-cell {
  display: flex;
  align-items: center;
  justify-content: space-between;
  min-height: 38px;
  width: 100%;
  padding: 0 12px;
  border: 1px solid #d6dde8;
  border-radius: 8px;
  background: #f1f5f9;
  color: #475569;
  cursor: default;
  font-size: 12px;
  font-weight: 650;
  transition: border-color 0.15s ease, box-shadow 0.15s ease, transform 0.15s ease;
}

.slot-row-cell span {
  color: inherit;
}

.slot-row-cell strong {
  font-size: 12px;
  font-weight: 700;
}

.slot-row-cell.empty {
  border-color: #d6dde8;
  background: #f1f5f9;
  color: #94a3b8;
}

.slot-row-cell.occupied {
  border-color: #fed7aa;
  background: #fff7ed;
  color: #9a3412;
}

.slot-row-cell.matched {
  border-color: #60a5fa;
  background: #dbeafe;
  color: #1d4ed8;
  box-shadow: 0 0 0 2px rgba(96, 165, 250, 0.18);
}

.slot-row-cell:hover {
  transform: translateY(-1px);
}

.slot-legend {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin-top: 10px;
  color: var(--app-text-secondary);
  font-size: 13px;
}

.slot-legend span {
  display: inline-flex;
  align-items: center;
  gap: 8px;
}

.slot-dot {
  display: inline-block;
  width: 10px;
  height: 10px;
  border: 1px solid transparent;
  border-radius: 50%;
}

.slot-dot.empty {
  border-color: #d6dde8;
  background: #f1f5f9;
}

.slot-dot.occupied {
  border-color: #fed7aa;
  background: #fff7ed;
}

.slot-dot.matched {
  border-color: #60a5fa;
  background: #dbeafe;
}

.slot-dot.selected {
  border-color: #2563eb;
  background: #2563eb;
}

.location-summary-list {
  display: grid;
  gap: 10px;
  margin-top: 18px;
}

.location-summary-list div {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  padding-bottom: 8px;
  border-bottom: 1px solid var(--app-border-light);
}

.location-summary-list span {
  color: var(--app-text-tertiary);
  font-size: 12px;
}

.location-summary-list strong {
  color: var(--app-text);
  font-size: 13px;
  text-align: right;
  word-break: break-word;
}

.dialog-products {
  margin-top: 18px;
}

.dialog-product-row {
  padding: 10px 0;
  border-bottom: 1px solid var(--app-border-light);
}

.dialog-product-row div {
  color: var(--app-text);
  font-weight: 650;
  line-height: 1.45;
  word-break: break-word;
}

.dialog-product-row span {
  display: block;
  margin-top: 4px;
  color: var(--app-text-secondary);
  font-size: 12px;
  line-height: 1.45;
}

.panel-operation-card {
  margin-top: 16px;
  padding: 12px;
  border: 1px solid var(--app-border-light);
  border-radius: 10px;
  background: #f8fafc;
}

.panel-operation-card.compact {
  padding: 12px 12px 14px;
}

.operation-hint {
  margin-left: 8px;
  color: var(--app-text-secondary);
  font-size: 12px;
}

.single-code-hint {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  margin: -4px 0 12px 76px;
  padding: 8px 10px;
  border: 1px solid #bfdbfe;
  border-radius: 8px;
  background: #eff6ff;
  color: #1d4ed8;
  font-size: 12px;
  font-weight: 650;
}

.single-code-hint.dialog-hint {
  margin-left: 86px;
}

.operation-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  padding-left: 76px;
}

.operation-actions.no-indent {
  padding-left: 0;
}

.slot-row-cell.selected {
  border-color: #1d4ed8;
  background: #eff6ff;
  color: #1d4ed8;
  box-shadow: 0 0 0 3px rgba(37, 99, 235, 0.22);
}

.slot-detail-card {
  margin-top: 18px;
  padding: 12px;
  border: 1px solid var(--app-border-light);
  border-radius: 12px;
  background: #f8fafc;
}

.slot-detail-row {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  padding: 8px 0;
  border-bottom: 1px solid var(--app-border-light);
}

.slot-detail-row span {
  flex: none;
  color: var(--app-text-tertiary);
  font-size: 12px;
}

.slot-detail-row strong {
  min-width: 0;
  color: var(--app-text);
  font-size: 13px;
  text-align: right;
  word-break: break-word;
}

.slot-action-row {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 12px;
}

.task-result {
  display: grid;
  gap: 10px;
}

.batch-no {
  padding: 10px 12px;
  border-radius: 8px;
  background: #f1f5f9;
  color: var(--app-text);
  font-weight: 650;
}

.task-result-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 10px 0;
  border-bottom: 1px solid var(--app-border-light);
}

.assay-detail {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px 16px;
}

.assay-detail div {
  display: grid;
  gap: 4px;
}

.assay-detail span {
  color: var(--app-text-tertiary);
  font-size: 12px;
}

.assay-detail strong {
  color: var(--app-text);
  font-size: 13px;
  word-break: break-word;
}

@media (max-width: 1180px) {
  .map-workspace {
    grid-template-columns: 1fr;
  }

  .detail-panel {
    order: 2;
  }
}

@media (max-width: 900px) {
  .location-dialog-layout {
    grid-template-columns: 1fr;
  }

  .slot-vertical-map {
    grid-template-columns: 1fr;
  }
}
</style>
