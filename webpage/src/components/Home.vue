<template>
  <div class="home-workbench">
    <el-card class="workbench-hero">
      <div>
        <div class="hero-eyebrow">仓储管理工作台</div>
        <div class="hero-title">欢迎回来，今天是 {{ formattedDate }} {{ dayOfWeek }}</div>
        <div class="hero-subtitle">聚合库位状态、库存概览和常用业务入口，快速进入托盘码、任务和化验流程。</div>
      </div>
      <div class="hero-time">{{ hours }}:{{ minutes }}:{{ seconds }}</div>
    </el-card>

    <div class="overview-grid">
      <el-card v-for="item in dashboardStats" :key="item.label" class="metric-card">
        <div class="metric-label">{{ item.label }}</div>
        <div class="metric-value">{{ item.value }}</div>
        <div class="metric-hint">{{ item.hint }}</div>
      </el-card>
    </div>

    <el-card class="quick-card">
      <div class="section-title">快捷入口</div>
      <div class="quick-grid">
        <div v-for="item in quickEntries" :key="item.path" class="quick-entry" @click="router.push(item.path)">
          <div class="quick-title">{{ item.title }}</div>
          <div class="quick-desc">{{ item.desc }}</div>
        </div>
      </div>
    </el-card>

    <el-card class="table-card" v-loading="loadingMap">
      <div class="table-toolbar">
        <div>
          <div class="section-title">库位占用概览</div>
          <div class="section-subtitle">按库位状态和容量查看当前仓储概况，点击库位进入仓库平面图定位。</div>
        </div>
      </div>
      <div class="status-overview">
        <div v-for="item in warehouseStatusOverview" :key="item.label" class="status-card">
          <el-tag :type="item.type">{{ item.label }}</el-tag>
          <span>{{ item.value }}</span>
        </div>
      </div>
      <el-table :data="warehouseOverviewRows" stripe height="360">
        <el-table-column prop="warehouseName" label="库位名称" min-width="140" show-overflow-tooltip/>
        <el-table-column prop="status" label="状态" width="110">
          <template #default="{ row }">
            <el-tag :type="getWarehouseStatusTag(row.status)">{{ row.status || '未知' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="curCapacity" label="当前容量" width="110"/>
        <el-table-column prop="maxCapacity" label="最大容量" width="110"/>
        <el-table-column prop="capacityPercentage" label="占用率" min-width="180">
          <template #default="{ row }">
            <el-progress :percentage="normalizePercent(row.capacityPercentage)" :stroke-width="8"/>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="110" fixed="right">
          <template #default="{ row }">
            <el-button size="small" type="primary" @click="goWarehouseMap(row)">查看</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-card v-if="selectedLocation" class="table-card location-detail-card">
      <div class="table-toolbar">
        <div>
          <div class="section-title">{{ selectedLocation.warehouseName }} 详情</div>
          <div class="section-subtitle">当前库位产品明细和可执行操作。</div>
        </div>
        <div class="table-toolbar-right">
          <el-tag :type="statusTagMap[selectedLocation.status]">{{ statusMap[selectedLocation.status] }}</el-tag>
          <el-button @click="selectedLocation = null">关闭</el-button>
        </div>
      </div>
      <div class="location-actions">
        <el-button type="primary" @click="visible = true;returnInStockFlag='0';operationTypeLabel = '新增入库';operationType='新增入库';disableBtn=false">新增入库</el-button>
        <el-button @click="visible = true;operationTypeLabel='新增出库'; operationType='新增出库';warehouseProductList=selectedLocationInfo">新增出库</el-button>
        <el-button v-if="selectedLocation.id < 1000" @click="visible = true;operationTypeLabel='调拨出库';operationType='新增出库';warehouseProductList=selectedLocationInfo">调拨出库</el-button>
      </div>
      <el-empty v-if="!selectedLocationInfo.length" description="暂无产品明细"/>
      <div v-else class="product-detail-grid">
        <div v-for="(item, index) in selectedLocationInfo" :key="index" class="product-detail-card">
          <div class="product-detail-title">{{ item.productName }}</div>
          <div class="product-detail-meta">
            <span>数量：{{ item.stockInfo }}</span>
            <span>重量：{{ item.totalWeight?.toFixed ? item.totalWeight.toFixed(2) : item.totalWeight || '-' }} kg</span>
            <span>入库日期：{{ item.entryDate || '-' }}</span>
          </div>
          <el-button text type="primary" @click="assayDialogVisible=true;getAssayInfo(item)">查看化验</el-button>
        </div>
      </div>
      <div class="location-layout-container" v-if="selectedLocation.status === 'filtered' && selectedLocation.id < 1000">
        <div class="columns-wrapper">
          <div class="column">
            <div class="rows-container">
              <div v-for="row in maxRowNum" :key="`left-${row}`" class="cell" :class="getCellClass('左', row)">{{ row }}</div>
            </div>
            <div class="column-title">左</div>
          </div>
          <div class="column">
            <div class="rows-container">
              <div v-for="row in maxRowNum" :key="`right-${row}`" class="cell" :class="getCellClass('右', row)">{{ row }}</div>
            </div>
            <div class="column-title">右</div>
          </div>
        </div>
      </div>
    </el-card>

    <el-dialog
        :title=operationTypeLabel
        v-model="visible"
        width="40%"
        :before-close="handleClose"
    >
      <el-form :model="submitForm" :rules="rule" label-width="auto" v-if="operationType === '新增入库'">
        <el-form-item label="产品状态">
          <el-radio-group
              v-model="firstLevelValues"
              @change="submitForm.productId = []"
          >
            <el-radio label="成品" value="成品" :disabled="selectedLocation.id >= 1000">成品</el-radio>
            <el-radio label="半成品" value="半成品">半成品</el-radio>
          </el-radio-group>
        </el-form-item>

        <!-- 级联选择器 - 动态禁用选项 -->
        <el-form-item label="产品名称" prop="productId">
          <el-cascader
              v-model="submitForm.productId"
              :options="productOptions.map(item => ({
        ...item,
        disabled: firstLevelValues ? item.label !== firstLevelValues : false
      }))"
              :props="{ expandTrigger: 'hover' }"
              placeholder="请选择产品名称"
              style="width: 100%"
              clearable
          />
        </el-form-item>
        <el-form-item label="仓库名称" prop="warehouseName">
          <span>{{ selectedLocation.warehouseName }}</span>
        </el-form-item>
        <el-form-item label="入库日期" prop="entryDate">
          <el-date-picker
              v-model="submitForm.entryDate"
              type="date"
              placeholder="入库日期"
              style="width: 88%"
          />
          <transition name="fade" mode="out-in">
            <el-tag type="danger" v-if="checkAssay === -1" key="unchecked">未化验</el-tag>
            <el-tag type="success" v-else-if="checkAssay === 1" key="checked">已化验</el-tag>
          </transition>
        </el-form-item>
        <el-form-item label="数量" prop="quantity">
          <div class="group-input">
            <el-input v-model="submitForm.quantity" clearable/>
            <el-select
                v-model="submitForm.unit"
                placeholder="单位"
                style="width: 120px"
            >
              <el-option label="板" value="0"/>
              <el-option label="件" value="1"/>
            </el-select>
          </div>
        </el-form-item>
        <el-form-item label="位置" prop="side" v-if="selectedLocation.id < 1000">
          <el-radio-group v-model="submitForm.side">
            <el-radio label="左" value="左">左</el-radio>
            <el-radio label="右" value="右">右</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="半成品信息" v-if="firstLevelValues === '成品'">
          <div v-for="(item, index) in submitForm.semiRecords" :key="index">
            <el-row :gutter="20">
              <el-col :span="20">
                <el-form-item
                    label="半成品名称"
                    :prop="`semiRecords.${index}.semiProductId`"
                    :rules="rules.semiProductId"
                >
                  <el-select
                      @change="handleSemiProductChange(item)"
                      v-model="item.semiProductId"
                      placeholder="选择半成品"
                      clearable
                  >
                    <el-option
                        v-for="semi in semiProductList"
                        :key="semi.productId"
                        :label="semi.productName"
                        :value="semi.productId"
                    />
                  </el-select>
                </el-form-item>
              </el-col>
              <el-col :span="20">
                <el-form-item
                    label="库位"
                    :prop="`semiRecords.${index}.warehouseId`"
                    :rules="rules.warehouse"
                >
                  <el-select
                      v-model="item.warehouseId"
                      placeholder="选择库位"
                      clearable
                  >
                    <el-option
                        v-for="semi in warehouseList"
                        :key="semi.warehouseName"
                        :label="semi.warehouseName"
                        :value="semi.warehouseId"
                    />
                  </el-select>
                </el-form-item>
              </el-col>
              <el-col :span="20">
                <el-form-item
                    label="数量"
                    :prop="`semiRecords.${index}.quantity`"
                    :rules="rules.quantity"
                >
                  <div class="group-input">
                    <el-input v-model="item.quantity" clearable/>
                    <el-select
                        v-model="item.unit"
                        placeholder="单位"
                        style="width: 120px"
                    >
                      <el-option label="板" value="0"/>
                      <el-option label="件" value="1"/>
                    </el-select>
                  </div>
                </el-form-item>
              </el-col>
              <el-col :span="20">
                <el-form-item
                    label="生产日期"
                    :prop="`semiRecords.${index}.productionDate`"
                    :rules="rules.productionDate"
                >
                  <el-date-picker
                      v-model="item.productionDate"
                      type="date"
                      placeholder="生产日期"
                      style="width: 100%"
                  />
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-button
                    type="danger"
                    :icon="Delete"
                    @click="removeSemi(index)"
                    circle
                />
                <el-switch
                    v-model="item.useAssay"
                    active-color="#13ce66"
                    inactive-color="#ff4949"
                    active-text="套用该半成品化验数据"
                    :active-value="true"
                    :inactive-value="false"
                    @change="handleUseAssay(index)"
                />
              </el-col>

            </el-row>
            <br>
          </div>
          <el-button
              type="primary"
              icon="el-icon-plus"
              @click="addSemi"
              style="margin-top: 10px"
              v-if="firstLevelValues === '成品'"
          >
            添加半成品
          </el-button>
        </el-form-item>
      </el-form>
      <el-form :model="submitForm" :rules="ruless" label-width="auto" v-if="operationType === '新增出库'">
        <el-form-item label="仓库名称" prop="warehouseName">
          <span>{{ selectedLocation.warehouseName }}</span>
        </el-form-item>
        <el-form-item
            label="产品名称"
            prop="productId"
            :rules="rules.productId"
        >
          <el-select
              v-model="submitForm.productId"
              placeholder="选择产品"
              clearable
          >
            <el-option
                v-for="semi in warehouseProductList"
                :key="semi.productId"
                :label="semi.productName +'('+ semi.entryDate + ')'"
                :value="semi.productId"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="出库数量" prop="quantity">
          <div class="group-input">
            <el-input v-model="submitForm.quantity" clearable/>
            <el-select
                v-model="submitForm.unit"
                placeholder="单位"
                style="width: 120px"
            >
              <el-option label="板" value="0"/>
              <el-option label="件" value="1"/>
            </el-select>
          </div>
        </el-form-item>
        <el-form-item label="出库位置" prop="side" v-if="selectedLocation.id < 1000">
          <el-radio-group v-model="submitForm.side">
            <el-radio label="左" value="左">左</el-radio>
            <el-radio label="右" value="右">右</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item v-if="operationTypeLabel === '调拨出库'"
                      label="入库仓库"
                      prop="inWarehouseName"
                      :rules="rules.warehouse"
        >
          <el-select
              v-model="submitForm.inWarehouseName"
              placeholder="选择入库仓库"
              clearable
          >
            <el-option
                v-for="semi in CapacityList"
                :key="semi.warehouseName"
                :label="semi.warehouseName"
                :value="semi.warehouseId"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="出库方式" prop="outType" v-if="selectedLocation.id < 1000">
          <el-radio-group v-model="submitForm.outType">
            <el-radio label="整板优先" value="0">整板优先</el-radio>
            <el-radio label="散件优先" value="1">散件优先</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <div slot="footer" class="dialog-footer">
        <el-button type="primary" @click="operationType === '新增入库' ? handleNew() : handleOut()">确定</el-button>
        <el-button @click="visible = false;handleClose()">取消</el-button>
      </div>
    </el-dialog>


    <el-dialog
        title="化验信息"
        v-model="assayDialogVisible"
        width="40%"
    >
      <div class="card-container">
        <el-card
            class="record-card"
            shadow="hover"
        >
          <div class="card-content">
            <el-row :gutter="20">
              <el-col :span="20">
                <div class="info-item">
                  <label>采样日期：</label>
                  <span>{{ assayInfo.sampleDate }}</span>
                </div>
                <div class="info-item">
                  <label>色值：</label>
                  <span>{{ assayInfo.colorValue }}</span>
                </div>
                <div class="info-item">
                  <label>还原糖：</label>
                  <span>{{ assayInfo.reducingSugar }}</span>
                </div>
                <div class="info-item">
                  <label>干重：</label>
                  <span>{{ assayInfo.dryWeight }}</span>
                </div>
                <div class="info-item">
                  <label>电导灰分：</label>
                  <span>{{ assayInfo.conductivityAsh }}</span>
                </div>
                <div class="info-item">
                  <label>蔗糖：</label>
                  <span>{{ assayInfo.sucrose }}</span>
                </div>
                <div class="info-item">
                  <label>不溶物：</label>
                  <span>{{ assayInfo.insolubleImpurity }}</span>
                </div>
                <div class="info-item">
                  <label>pH值：</label>
                  <span>{{ assayInfo.phValue }}</span>
                </div>
              </el-col>
            </el-row>

            <!-- 底部状态栏 -->
            <div class="status-bar">
              <el-tag
                  :span="12"
                  :type="assayInfo.isQualified === '合格' ? 'success' : 'danger'"
                  size="medium"
              >
                {{ assayInfo.isQualified }}
              </el-tag>
              <div class="meta-info">
                <span>检测人：{{ assayInfo.testerName }}</span>
              </div>
            </div>
          </div>
        </el-card>
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
import {ref, computed, onMounted, onUnmounted, reactive, onBeforeMount, watchEffect, watch} from 'vue'
import {Calendar, Delete, Timer} from '@element-plus/icons-vue'
import {useRouter} from 'vue-router'
import {throttle} from 'lodash-es'
import {
  getWarehouseInfo,
  queryWarehouseLedger,
  getWarehouseList,
  getWarehouseById,
  getMaxRowNum
} from '@/api/warehouseinfo'
import {ElMessage} from 'element-plus'
import {getStandard} from "@/api/standard";
import {getSemiProduct, getStProduct, getProductWarehouse, getAssay} from "@/api/assay";
import {
  addInStock,
  addOutStack,
  addOutStock,
  addSemiProduct,
  addSemiProductStack, addTransferOut,
  getCheck,
  getOutStock
} from "@/api/stock";
import {getMesh} from "@/api/mesh";
import dayjs from "dayjs";

const router = useRouter()
const quickEntries = [
  {title: '二维码管理', desc: '生成、查看和追溯二维码', path: '/pallet-code/list'},
  {title: '半成品入库任务', desc: '处理半成品入库确认', path: '/pallet-task/semi/in'},
  {title: '成品入库任务', desc: '绑定半成品并确认入库', path: '/pallet-task/finish/in'},
  {title: '半成品出库任务', desc: '普通出库与备料池转入', path: '/pallet-task/semi/out'},
  {title: '成品出库任务', desc: '创建并确认成品出库', path: '/pallet-task/finish/out'},
  {title: '调拨任务', desc: '创建与确认库位调拨', path: '/pallet-task/transfer'},
  {title: '仓库平面图', desc: '查看仓区分布与库位状态', path: '/warehouse-map'},
  {title: '化验管理', desc: '维护产品化验记录', path: '/assay'}
]

//查询仓库信息
let searchWarehouseForm = ref({
  productName: '',
  standardNames: '',
  screenMeshId: '',
  dateRange: [],
})
const warehouseProductList = ref([])
const loadingMap = ref(false)
const warehousesList = ref([])
const filteredInfo = ref([])
// 处理搜索
const handleSearch = async () => {
  await getAll()
  let params = {}
  if (searchWarehouseForm.value.productName) {
    params.productName = searchWarehouseForm.value.productName
  }
  if (searchWarehouseForm.value.standardNames) {
    params.standardNames = searchWarehouseForm.value.standardNames
  }
  if (searchWarehouseForm.value.screenMeshId) {
    params.screenMeshId = searchWarehouseForm.value.screenMeshId
  }
  if (searchWarehouseForm.value.dateRange.length === 2) {
    params.startDate = searchWarehouseForm.value.dateRange[0]
    params.endDate = searchWarehouseForm.value.dateRange[1]
  }
  if (searchWarehouseForm.value.productName === '' && !searchWarehouseForm.value.standardNames && searchWarehouseForm.value.screenMeshId === '' && searchWarehouseForm.value.dateRange.length === 0) {
    ElMessage.error('请至少输入一个查询条件')
    return
  }
  filteredInfo.value = params
  loadingMap.value = true
  let res = await getWarehouseList(params)
  if (res.code === 200) {
    warehousesList.value = res.data
    //查询后的仓库变蓝色
    warehousesList.value.forEach(warehouse => {
      //查找locations中id为item.warehouseId的对象，并更新其capacityPercentage和status属性
      const index = locations.value.findIndex(location => location.id === warehouse.warehouseId)
      if (index === -1) {
        return
      }
      locations.value[index].status = 'filtered'
    })
    loadingMap.value = false
  } else {
    console.error(res.msg)
  }
}
// 处理重置
const handleReset = () => {
  searchWarehouseForm.value = {
    productName: '',
    standardNames: '',
    screenMeshId: '',
    dateRange: [],
  }
  filteredInfo.value = []
  getAll()
}
const filterLocations = ref([])
const maxRowNum = ref(0)
// 样式计算函数
const getCellClass = (side, row) => {
  const hasGoods = filterLocations.value.some(
      loc => loc.side === side && loc.rowNumber === row
  )
  return hasGoods
      ? 'bg-blue-500 cursor-pointer'
      : 'bg-gray-200 cursor-not-allowed'
}
// 时间数据
const time = ref(new Date())
const canvas = ref({
  width: 0,
  height: 0
})
let animationFrame = null
// 粒子参数
const particles = {
  count: 100,
  maxRadius: 2,
  color: 'rgba(64, 158, 255, 0.8)',
  baseSpeed: 2,
  baseWidth: 1920, // 基准分辨率宽度
  marginRatio: 0.1 // 边距比例
}
// 时间计算属性
const hours = computed(() => time.value.getHours().toString().padStart(2, '0'))
const minutes = computed(() => time.value.getMinutes().toString().padStart(2, '0'))
const seconds = computed(() => time.value.getSeconds().toString().padStart(2, '0'))
const dayOfWeek = computed(() => ['周日', '周一', '周二', '周三', '周四', '周五', '周六'][time.value.getDay()])
const formattedDate = computed(() => {
  const d = time.value
  return `${d.getFullYear()}-${(d.getMonth() + 1).toString().padStart(2, '0')}-${d.getDate().toString().padStart(2, '0')}`
})
const assayDialogVisible = ref(false)
const assayInfo = ref({})
const getAssayInfo = async (product) => {
  if (!product || product.productId === '' || product.entryDate === '') {
    ElMessage.error('请选择产品')
    return
  }
  let params = {
    page: 1,
    size: 100,
    productId: product.productId,
    entryDate: product.entryDate,
    isQualified: '合格'
  }
  let res = await getAssay(params)
  assayInfo.value = res.data.records[0]
  assayDialogVisible.value = true
  console.log(assayInfo.value)
}

// 粒子系统类
class Particle {
  constructor(canvasWidth, canvasHeight) {
    const margin = canvasWidth * particles.marginRatio
    this.x = margin + Math.random() * (canvasWidth - 2 * margin)
    this.y = Math.random() * canvasHeight
    this.radius = Math.random() * particles.maxRadius
    this.baseSpeed = particles.baseSpeed
    this.normalizeSpeed(canvasWidth)
  }

  normalizeSpeed(canvasWidth) {
    const sizeRatio = canvasWidth / particles.baseWidth
    this.speedX = ((Math.random() - 0.5) * this.baseSpeed * sizeRatio)
    this.speedY = ((Math.random() - 0.5) * this.baseSpeed * sizeRatio)
  }

  update(canvasWidth, canvasHeight) {
    this.x += this.speedX
    this.y += this.speedY

    // 循环边界处理
    if (this.x < 0) this.x += canvasWidth
    if (this.x > canvasWidth) this.x -= canvasWidth
    if (this.y < 0) this.y += canvasHeight
    if (this.y > canvasHeight) this.y -= canvasHeight
  }

  draw(ctx) {
    ctx.beginPath()
    ctx.arc(this.x, this.y, this.radius, 0, Math.PI * 2)
    ctx.fillStyle = particles.color
    ctx.fill()
  }
}

// 粒子系统管理
let particlesArray = []
const initParticles = () => {
  if (!canvas.value) return

  const ctx = canvas.value.getContext('2d')
  const initProcess = () => {
    // 初始化粒子
    particlesArray = Array.from({length: particles.count}, () =>
        new Particle(canvas.value.width, canvas.value.height))
  }

  // 尺寸校验
  const validateCanvasSize = () => {
    if (!canvas.value || canvas.value.offsetWidth === 0) {
      setTimeout(validateCanvasSize, 50)
      return false
    }
    return true
  }

  // 调整尺寸处理
  const handleResize = throttle(() => {
    if (!validateCanvasSize()) return

    const oldWidth = canvas.value.width
    const oldHeight = canvas.value.height
    canvas.value.width = canvas.value.offsetWidth
    canvas.value.height = canvas.value.offsetHeight

    // 保持粒子相对位置
    particlesArray.forEach(p => {
      p.x *= canvas.value.width / oldWidth
      p.y *= canvas.value.height / oldHeight
      p.normalizeSpeed(canvas.value.width)
    })
  }, 200)

  // 动画循环
  const animate = () => {
    if (!canvas.value) return
    ctx.clearRect(0, 0, canvas.value.width, canvas.value.height)
    particlesArray.forEach(p => {
      p.update(canvas.value.width, canvas.value.height)
      p.draw(ctx)
    })
    animationFrame = requestAnimationFrame(animate)
  }

  // 初始化流程
  if (validateCanvasSize()) {
    canvas.value.width = canvas.value.offsetWidth
    canvas.value.height = canvas.value.offsetHeight
    initProcess()
    animate()
  }

  // 事件监听
  window.addEventListener('resize', handleResize)
  onUnmounted(() => {
    window.removeEventListener('resize', handleResize)
    cancelAnimationFrame(animationFrame)
  })
}

// 组件生命周期
onMounted(() => {
  setInterval(() => time.value = new Date(), 1000)
  getAll()
})

// 仓库数据
// 仓库尺寸
const viewBoxWidth = ref(1200)
const viewBoxHeight = ref(800)
// 当前选中库位
const selectedLocation = ref(null)
// 状态映射配置
const statusMap = reactive({
  normal: '正常',
  empty: '空置',
  full: '满仓',
  danger: '临期预警',
  maintenance: '维护',
  filtered: '选中',
  default: '默认',
})
const statusTagMap = reactive({
  normal: 'success',
  empty: 'primary',
  full: 'danger',
  danger: 'warning',
  maintenance: 'info',
  default: 'info',
  filtered: 'primary',
})
// 需要垂直排列的ID列表
const verticalIds = ['办公室门', '仓库入口', '特殊区域']

// 将多边形点数组转换为SVG需要的字符串格式
const getPolygonPoints = (location) => {
  return location.points.map(point => {
    // 将相对坐标转换为绝对坐标
    const absX = location.x + point[0];
    const absY = location.y + point[1];
    return `${absX},${absY}`;
  }).join(' ');
};
// 在 script 中添加
// const getTextPosition = (location) => {
//   if (verticalTextIds.value.includes(location.id)) {
//     return {
//       x: location.x + location.width/2 - 8, // 向左微调
//       y: location.y + location.height/2 + 5
//     }
//   }
//   return {
//     x: location.x + location.width/2,
//     y: location.y + location.height/2
//   }
// }
// script setup 部分
const verticalTextIds = ref([
  '办公室门',
  '消防通道',
  '应急出口'
  // 添加其他需要垂直排列的ID
])
// 库位数据（示例）
const locations = ref([
  {
    id: 1007,
    name: '二楼库房',
    x: 20,
    y: 600,
    width: 800,
    height: 200,
    status: 'default'
  },
  {
    id: 1011,
    name: '红糖库',
    x: 860,
    y: 600,
    width: 100,
    height: 80,
    status: 'default'
  },
  {
    id: 1000,
    name: '包装间库',
    x: 320,
    y: 50,
    points: [ // 定义不规则图形的各个顶点坐标
      [0, 0],
      [700, 0],
      [700, 70],
      [450, 70],
      [450, 150],
      [0, 150]
    ],
    status: 'default',
    shape: 'polygon', // 标记为多边形
    width: 500,
    height: 210,
  },
  {
    id: 1010,
    name: '怡宝击破组 包装一楼',
    x: 320,
    y: 210,
    width: 450,
    height: 45,
    status: 'default'
  },
  {
    id: 1008,
    name: '电梯口',
    x: 800,
    y: 430,
    width: 80,
    height: 130,
    status: 'default'
  },
  {
    id: 1009,
    name: '办公室门口',
    x: 1080,
    y: 145,
    width: 100,
    height: 40,
    status: 'default'
  },
  {
    id: 1001,
    name: '烘房1',
    x: 780,
    y: 130,
    width: 30,
    height: 130,
    status: 'default'
  },
  {
    id: 1002,
    name: '烘房2',
    x: 820,
    y: 130,
    width: 30,
    height: 130,
    status: 'default'
  },
  {
    id: 1003,
    name: '烘房3',
    x: 860,
    y: 130,
    width: 30,
    height: 130,
    status: 'default'
  },
  {
    id: 1004,
    name: '烘房4',
    x: 900,
    y: 130,
    width: 30,
    height: 130,
    status: 'default'
  },
  {
    id: 1005,
    name: '烘房5',
    x: 940,
    y: 130,
    width: 30,
    height: 130,
    status: 'default'
  },
  {
    id: 1006,
    name: '烘房6',
    x: 980,
    y: 130,
    width: 30,
    height: 130,
    status: 'default'
  },
  {
    id: 1,
    x: 992,
    y: 270,
    width: 30,
    height: 130,
    status: 'default'
  },
  {
    id: 2,
    x: 956,
    y: 270,
    width: 30,
    height: 130,
    status: 'default'
  },
  {
    id: 3,
    x: 926,
    y: 270,
    width: 30,
    height: 130,
    status: 'default'
  },
  {
    id: 4,
    x: 896,
    y: 270,
    width: 30,
    height: 130,
    status: 'default'
  },
  {
    id: 5,
    x: 860,
    y: 270,
    width: 30,
    height: 130,
    status: 'default'
  },
  {
    id: 6,
    x: 830,
    y: 270,
    width: 30,
    height: 130,
    status: 'default'
  },
  {
    id: 7,
    x: 800,
    y: 270,
    width: 30,
    height: 130,
    status: 'default'
  },
  {
    id: 8,
    x: 758,
    y: 270,
    width: 30,
    height: 130,
    status: 'default'
  },
  {
    id: 9,
    x: 728,
    y: 270,
    width: 30,
    height: 130,
    status: 'default'
  },
  {
    id: 10,
    x: 698,
    y: 270,
    width: 30,
    height: 130,
    status: 'default'
  },
  {
    id: 11,
    x: 662,
    y: 270,
    width: 30,
    height: 130,
    status: 'default'
  },
  {
    id: 12,
    x: 632,
    y: 270,
    width: 30,
    height: 130,
    status: 'default'
  },
  {
    id: 13,
    x: 602,
    y: 270,
    width: 30,
    height: 130,
    status: 'default'
  },
  {
    id: 14,
    x: 566,
    y: 270,
    width: 30,
    height: 130,
    status: 'default'
  },
  {
    id: 15,
    x: 536,
    y: 270,
    width: 30,
    height: 130,
    status: 'default'
  },
  {
    id: 16,
    x: 506,
    y: 270,
    width: 30,
    height: 130,
    status: 'default'
  },
  {
    id: 17,
    x: 470,
    y: 270,
    width: 30,
    height: 130,
    status: 'default'
  },
  {
    id: 18,
    x: 440,
    y: 270,
    width: 30,
    height: 130,
    status: 'default'
  },
  {
    id: 19,
    x: 410,
    y: 270,
    width: 30,
    height: 130,
    status: 'default'
  },
  {
    id: 20,
    x: 374,
    y: 270,
    width: 30,
    height: 130,
    status: 'default'
  },
  {
    id: 21,
    x: 314,
    y: 270,
    width: 30,
    height: 130,
    status: 'default'
  },
  {
    id: 22,
    x: 272,
    y: 270,
    width: 30,
    height: 130,
    status: 'default'
  },
  {
    id: 23,
    x: 242,
    y: 270,
    width: 30,
    height: 130,
    status: 'default'
  },
  {
    id: 24,
    x: 212,
    y: 270,
    width: 30,
    height: 130,
    status: 'default'
  },
  {
    id: 25,
    x: 176,
    y: 270,
    width: 30,
    height: 130,
    status: 'default'
  },
  {
    id: 26,
    x: 176,
    y: 238,
    width: 130,
    height: 30,
    status: 'default'
  },
  {
    id: 27,
    x: 176,
    y: 208,
    width: 130,
    height: 30,
    status: 'default'
  },
  {
    id: 28,
    x: 176,
    y: 178,
    width: 130,
    height: 30,
    status: 'default'
  },
  {
    id: 29,
    x: 176,
    y: 142,
    width: 130,
    height: 30,
    status: 'default'
  },
  {
    id: 30,
    x: 176,
    y: 82,
    width: 130,
    height: 60,
    status: 'default'
  },
  {
    id: 31,
    x: 20,
    y: 82,
    width: 130,
    height: 30,
    status: 'default'
  },
  {
    id: 32,
    x: 20,
    y: 112,
    width: 130,
    height: 30,
    status: 'default'
  },
  {
    id: 33,
    x: 20,
    y: 142,
    width: 130,
    height: 30,
    status: 'default'
  },
  {
    id: 34,
    x: 20,
    y: 178,
    width: 130,
    height: 30,
    status: 'default'
  },
  {
    id: 35,
    x: 20,
    y: 208,
    width: 130,
    height: 30,
    status: 'default'
  },
  {
    id: 36,
    x: 20,
    y: 238,
    width: 130,
    height: 30,
    status: 'default'
  },
  {
    id: 37,
    x: 20,
    y: 274,
    width: 130,
    height: 30,
    status: 'default'
  },
  {
    id: 38,
    x: 20,
    y: 304,
    width: 130,
    height: 30,
    status: 'default'
  },
  {
    id: 39,
    x: 20,
    y: 334,
    width: 130,
    height: 30,
    status: 'default'
  },
  {
    id: 40,
    x: 20,
    y: 370,
    width: 130,
    height: 30,
    status: 'default'
  },
  {
    id: 41,
    x: 20,
    y: 430,
    width: 30,
    height: 130,
    status: 'default'
  },
  {
    id: 42,
    x: 50,
    y: 430,
    width: 30,
    height: 130,
    status: 'default'
  },
  {
    id: 43,
    x: 80,
    y: 430,
    width: 30,
    height: 130,
    status: 'default'
  },
  {
    id: 44,
    x: 116,
    y: 430,
    width: 30,
    height: 130,
    status: 'default'
  },
  {
    id: 45,
    x: 146,
    y: 430,
    width: 30,
    height: 130,
    status: 'default'
  },
  {
    id: 46,
    x: 176,
    y: 430,
    width: 30,
    height: 130,
    status: 'default'
  },
  {
    id: 47,
    x: 212,
    y: 430,
    width: 30,
    height: 130,
    status: 'default'
  },
  {
    id: 48,
    x: 242,
    y: 430,
    width: 30,
    height: 130,
    status: 'default'
  },
  {
    id: 49,
    x: 272,
    y: 430,
    width: 30,
    height: 130,
    status: 'default'
  },
  {
    id: 50,
    x: 314,
    y: 430,
    width: 30,
    height: 130,
    status: 'default'
  },
  {
    id: 51,
    x: 374,
    y: 430,
    width: 30,
    height: 130,
    status: 'default'
  },
  {
    id: 52,
    x: 410,
    y: 430,
    width: 30,
    height: 130,
    status: 'default'
  },
  {
    id: 53,
    x: 440,
    y: 430,
    width: 30,
    height: 130,
    status: 'default'
  },
  {
    id: 54,
    x: 470,
    y: 430,
    width: 30,
    height: 130,
    status: 'default'
  },
  {
    id: 55,
    x: 506,
    y: 430,
    width: 30,
    height: 130,
    status: 'default'
  },
  {
    id: 56,
    x: 536,
    y: 430,
    width: 30,
    height: 130,
    status: 'default'
  },
  {
    id: 57,
    x: 566,
    y: 430,
    width: 30,
    height: 130,
    status: 'default'
  },
  {
    id: 58,
    x: 602,
    y: 430,
    width: 30,
    height: 130,
    status: 'default'
  },
  {
    id: 59,
    x: 632,
    y: 430,
    width: 30,
    height: 130,
    status: 'default'
  },
  {
    id: 60,
    x: 662,
    y: 430,
    width: 30,
    height: 130,
    status: 'default'
  },
  {
    id: 61,
    x: 698,
    y: 430,
    width: 30,
    height: 130,
    status: 'default'
  },
  {
    id: 62,
    x: 728,
    y: 430,
    width: 30,
    height: 130,
    status: 'default'
  },
  {
    id: 63,
    x: 758,
    y: 430,
    width: 30,
    height: 130,
    status: 'default'
  },
  {
    id: 64,
    x: 896,
    y: 430,
    width: 30,
    height: 130,
    status: 'default'
  },
  {
    id: 65,
    x: 926,
    y: 430,
    width: 30,
    height: 130,
    status: 'default'
  },
  {
    id: 66,
    x: 956,
    y: 430,
    width: 30,
    height: 130,
    status: 'default'
  },
  {
    id: 67,
    x: 992,
    y: 430,
    width: 30,
    height: 130,
    status: 'default'
  },
  {
    id: 68,
    x: 1080,
    y: 500,
    width: 100,
    height: 50,
    status: 'default'
  },
  {
    id: 69,
    x: 1080,
    y: 450,
    width: 100,
    height: 50,
    status: 'default'
  },
  {
    id: 70,
    x: 1080,
    y: 390,
    width: 100,
    height: 50,
    status: 'default'
  },
  {
    id: 71,
    x: 1080,
    y: 340,
    width: 100,
    height: 50,
    status: 'default'
  },
  {
    id: 72,
    x: 1080,
    y: 290,
    width: 100,
    height: 50,
    status: 'default'
  },
  {
    id: 73,
    x: 1080,
    y: 240,
    width: 100,
    height: 50,
    status: 'default'
  },
  {
    id: 74,
    x: 1080,
    y: 190,
    width: 100,
    height: 50,
    status: 'default'
  },
  {
    id: 75,
    x: 1080,
    y: 90,
    width: 100,
    height: 50,
    status: 'default'
  }
  // {
  //   id: '多晶冰糖包装间',
  //   x:312,
  //   y: 28,
  //   width: 710,
  //   height: 240,
  //   status: "default"
  // }
  // ,
  // {
  //   id:101,
  //   x:312,
  //   y: 600,
  //   width: 710,
  //   height: 240,
  //   status: "default",
  // }
])
// 加载状态
const loading = ref(false)
//单个库位信息
const selectedLocationInfo = ref([])

// 处理库位点击
const handleSelectLocation = async (location) => {
  ElMessage.success(`已选中 ${location.id}号仓库`)
  selectedLocation.value = null
  selectedLocationInfo.value = []
  selectedLocation.value = location
  loading.value = true
  let isFiltered = false
  if (location.status === 'filtered') {
    isFiltered = true
  }
  let params = {
    warehouseId: location.id,
    page: 1,
    size: 1000
  }
  let result = await getWarehouseInfo(params)
  if (result.code === 200) {
    console.log(result.value)
    selectedLocation.value.warehouseId = location.id
    selectedLocation.value.warehouseName = CapacityList.value.find(item => item.warehouseId === location.id)?.warehouseName || location.warehouseName || location.name || location.id
    if (result.data.records[0].productName) {
      selectedLocation.value.productName = result.data.records[0].productName
    }
    if (result.data.records[0].totalQuantity) {
      selectedLocation.value.totalQuantity = result.data.records[0].totalQuantity
    }
    if (result.data.records[0].totalPieces) {
      selectedLocation.value.totalPieces = result.data.records[0].totalPieces
    }
    if (result.data.records[0].totalWeight) {
      selectedLocation.value.totalWeight = result.data.records[0].totalWeight
    }
    if (result.data.records[0].entryDate) {
      selectedLocation.value.entryDate = result.data.records[0].entryDate
    }
    if (result.data.records[0].firstEntryDate) {
      selectedLocation.value.firstEntryDate = result.data.records[0].firstEntryDate
    }
    selectedLocationInfo.value = result.data.records
  }
  if (isFiltered) {
    selectedLocation.value.status = 'filtered'
    let res = await getWarehouseById(location.id, filteredInfo.value)
    if (res.code === 200) {
      filterLocations.value = res.data
      // 获取最大行数
      const result = await getMaxRowNum(selectedLocation.value.warehouseId)
      if (result.code === 200) {
        maxRowNum.value = result.data.maxRows
      }
    }
  }
  loading.value = false
  //颜色变深
  const locationElement = document.querySelector(`.location[data-id="${location.id}"]`)
  if (locationElement) {
    locationElement.classList.add('selected')
    setTimeout(() => {
      locationElement.classList.remove('selected')
    }, 1000)
  }
}
// 状态颜色映射（不带透明度）
const statusColorMap = reactive({
  normal: '#006918',
  empty: '#b6b6b6',
  danger: '#fde047',
  full: '#ff3838',
  maintenance: '#727272',
  default: '#ffffff'
})
// HEX转RGBA函数
const hexToRgba = (hex, alpha) => {
  const r = parseInt(hex.slice(1, 3), 16)
  const g = parseInt(hex.slice(3, 5), 16)
  const b = parseInt(hex.slice(5, 7), 16)
  return `rgba(${r}, ${g}, ${b}, ${alpha})`
}
// 获取库位样式
const getLocationStyle = (location) => {
  if (location.status === 'normal') {
    const baseColor = statusColorMap[location.status]
    const capacity = location.capacityPercentage ?? 1 // 默认100%
    const alpha = Math.min(Math.max(1 - capacity, 0.3), 0.7)
    return {
      fill: hexToRgba(baseColor, alpha.toFixed(2))
    }
  }
}
let CapacityList = ref([]);
const warehouseOverviewRows = computed(() => CapacityList.value.slice(0, 12))
const warehouseStatusOverview = computed(() => {
  const summary = {
    normal: {label: '正常', value: 0, type: 'success'},
    empty: {label: '空置', value: 0, type: 'info'},
    danger: {label: '临期预警', value: 0, type: 'warning'},
    full: {label: '满仓', value: 0, type: 'danger'},
    maintenance: {label: '维护', value: 0, type: 'info'}
  }
  CapacityList.value.forEach(item => {
    const key = getWarehouseStatusKey(item.status)
    if (summary[key]) summary[key].value += 1
  })
  return Object.values(summary)
})
const dashboardStats = computed(() => {
  const total = CapacityList.value.length
  const warning = CapacityList.value.filter(item => item.status === '临期预警').length
  const full = CapacityList.value.filter(item => item.status === '满仓').length
  const normal = CapacityList.value.filter(item => item.status === '正常').length
  return [
    {label: '库位总数', value: total, hint: '当前纳入统计的库位'},
    {label: '正常库位', value: normal, hint: '正在正常使用中'},
    {label: '临期预警', value: warning, hint: '需要优先关注'},
    {label: '满仓库位', value: full, hint: '容量已达到上限'}
  ]
})
const getWarehouseStatusKey = (status) => {
  if (status === '正常') return 'normal'
  if (status === '空置') return 'empty'
  if (status === '临期预警') return 'danger'
  if (status === '满仓') return 'full'
  if (status === '维护') return 'maintenance'
  return 'default'
}
const getWarehouseStatusTag = (status) => statusTagMap[getWarehouseStatusKey(status)] || 'info'
const normalizePercent = (value) => {
  const number = Number(value || 0)
  if (Number.isNaN(number)) return 0
  return Math.max(0, Math.min(100, Math.round(number)))
}

const goWarehouseMap = (row) => {
  router.push({
    path: '/warehouse-map',
    query: {warehouseId: row.warehouseId}
  })
}

const getAll = async () => {
  const result = await queryWarehouseLedger({page: 1, size: 500})

  if (result.code === 200) {
    CapacityList.value = result.data?.records || []
    CapacityList.value.forEach(item => {
      //查找locations中id为item.warehouseId的对象，并更新其capacityPercentage和status属性
      const index = locations.value.findIndex(location => location.id === item.warehouseId)
      if (index === -1) {
        return
      }
      locations.value[index].capacityPercentage = item.capacityPercentage
      if (item.status === '正常') {
        locations.value[index].status = 'normal'
      } else if (item.status === '空置') {
        locations.value[index].status = 'empty'
      } else if (item.status === '临期预警') {
        locations.value[index].status = 'danger'
      } else if (item.status === '满仓') {
        locations.value[index].status = 'full'
      } else if (item.status === '维护') {
        locations.value[index].status = 'maintenance'
      } else {
        locations.value[index].status = 'default'
      }
    })
  }
}
const handleCanvasClick = () => {
  console.log('canvas clicked')
  if (selectedLocation.value) {
    selectedLocation.value = null
  }
}
const standards = ref([])
const getStandardList = async () => {
  let params = {}
  let res = await getStandard(params)
  if (res.code === 200) {
    standards.value = res.data
  } else {
    ElMessage.error('获取标准列表失败')
  }
}
getStandardList()

// 新增
const handleClose = () => {
  visible.value = false
  submitForm.value = {
    productId: '',
    warehouseName: '',
    quantity: '',
    side: '',
    screenMeshId: '',
    outType: '0',
    unit: '0',
    semiRecords: []
  }
  firstLevelValues.value = '成品'
  checkAssay.value = 0
}
const operationType = ref('')
const returnInStockFlag = ref('0')
const operationTypeLabel = ref('')
const submitForm = ref({
  unit: "0",
  outType: '0',
  productId: '',
  warehouseName: '',
  inWarehouseName: '',
  quantity: '',
  side: '',
  screenMeshId: '',
  entryDate: '',
  semiRecords: []
})
const visible = ref(false)
// 验证规则
const rule = {
  productId: [{required: true, message: '请输入产品名称', trigger: 'blur'}],
  warehouseName: [{required: true, message: '请输入仓库名称', trigger: 'blur'}],
  quantity: [{required: true, message: '请输入数量', trigger: 'blur'}],
  side: [{required: true, message: '请选择位置', trigger: 'blur'}],
  entryDate: [{required: true, message: '请选择入库日期', trigger: 'blur'}]
}
const rules = reactive({
  productId: {required: true, message: '请选择产品'},
  warehouseName: {required: true, message: '请输入仓库名称'},
  quantity: {required: true, message: '请输入数量'},
  semiProductId: {required: true, message: '请选择半成品'},
  warehouse: {required: true, message: '请选择库位'},
  productionDate: {type: 'date', required: true, message: '请选择生产日期'},
})
const ruless = reactive({
  quantity: {required: true, message: '请输入数量'},
  side: [{required: true, message: '请选择位置', trigger: 'blur'}]
})
const addSemi = () => {
  if (!submitForm.value.semiRecords) {
    submitForm.value.semiRecords = [] // 初始化数组
  }
  submitForm.value.semiRecords.push({
    semiProductId: null,
    quantity: null,
    outType: '0',
    unit: '0'
  })
}
const removeSemi = (index) => {
  submitForm.value.semiRecords.splice(index, 1)
}
// 自动查询逻辑
const checkAssay = ref(0)
const handleAutoQuery = async () => {
  let len = submitForm.value.productId.length
  let params = {
    productId: submitForm.value.productId[len - 1],
    entryDate: dayjs(submitForm.value.entryDate).format('YYYY-MM-DD')
  }
  let res = await getCheck(params)
  if (res.data === true) {
    checkAssay.value = 1
  } else if (res.data === false) {
    checkAssay.value = -1
  }
  console.log(checkAssay.value)
}
// 监听产品ID和入库日期的变化
watch(
    [() => submitForm.value.productId, () => submitForm.value.entryDate],
    ([productId, entryDate]) => {
      // 当两者都有值时触发查询
      if (productId?.length > 0 && entryDate) {
        handleAutoQuery()
      }
    },
    {deep: true}
)

// 调拨出库
const transferOut = async () => {
  let params = {
    productId: submitForm.value.productId,
    warehouseId: selectedLocation.value.warehouseId,
    inWarehouseName: submitForm.value.inWarehouseName,
    side: submitForm.value.side,
    unit: submitForm.value.unit,
    outType: submitForm.value.outType,
    quantity: submitForm.value.quantity
  }
  let res = await addTransferOut(params)
  if (res.code === 200) {
    ElMessage.success('调拨成功')
    await getAll()
    visible.value = false
  } else {
    ElMessage.error(res.msg)
  }
}
const handleOut = async () => {
  if (submitForm.value.quantity === '') {
    ElMessage.error('请输入数量')
  }
  if (!submitForm.value.outType || submitForm.value.outType === '') {
    ElMessage.error('请选择出库方式')
  }
  if (submitForm.value.side === '' && selectedLocation.value.warehouseId < 1000) {
    ElMessage.error('请选择位置')
  }
  if (operationTypeLabel.value === '调拨出库') {
    await transferOut()
    return
  }

  let res
  if (selectedLocation.value.warehouseId >= 1000 && selectedLocation.value.warehouseId < 2000) {
    let params = {
      productId: submitForm.value.productId,
      warehouseId: selectedLocation.value.warehouseId,
      quantity: submitForm.value.quantity,
      outType: submitForm.value.outType,
      unit: submitForm.value.unit
    }
    res = await addOutStack(params)
  } else {
    let params = {
      productId: submitForm.value.productId,
      warehouseId: selectedLocation.value.warehouseId,
      side: submitForm.value.side,
      unit: submitForm.value.unit,
      outType: submitForm.value.outType,
      quantity: submitForm.value.quantity
    }
    res = await addOutStock(params)
  }
  if (res.code === 200) {
    ElMessage.success('新增成功')
    await getAll()
    visible.value = false
  } else {
    ElMessage.error(res.msg)
  }
}

const handleNew = async () => {
  // 校验表单
  if (submitForm.value.productId === '') {
    ElMessage.error('请选择产品名称')
    return
  }
  if (submitForm.value.quantity === '') {
    ElMessage.error('请输入数量')
    return
  }
  if (!submitForm.value.unit || submitForm.value.unit === '') {
    ElMessage.error('请选择入库数量单位')
    return
  }
  if (submitForm.value.side === '' && selectedLocation.value.warehouseId < 1000) {
    ElMessage.error('请选择位置')
    return
  }
  if (submitForm.value.entryDate === '') {
    ElMessage.error('请选择入库日期')
    return
  }
  // if (submitForm.value.semiRecords.length === 0) {
  //   ElMessage.error('请添加半成品信息')
  //   return
  // }
  //在semiProductList中查找输入的半成品信息
  for (let i = 0; i < submitForm.value.semiRecords.length; i++) {
    let semiProduct = semiProductList.value.find(item => item.productId === submitForm.value.semiRecords[i].semiProductId)
    if (!semiProduct) {
      ElMessage.error('半成品信息有误')
      return
    } else {
      submitForm.value.semiRecords[i].productName = semiProduct.productName
      submitForm.value.semiRecords[i].productionDate = dayjs(submitForm.value.semiRecords[i].productionDate).format('YYYY-MM-DD')
    }
  }
  let len = submitForm.value.productId.length
  let preProductId = submitForm.value.productId;
  submitForm.value.productId = submitForm.value.productId[len - 1]
  if (!submitForm.value.productId || submitForm.value.productId === '') {
    ElMessage.error('请选择产品')
    return
  }
  submitForm.value.entryDate = dayjs(submitForm.value.entryDate).format('YYYY-MM-DD')
  submitForm.value.semiProductRecords = JSON.stringify(submitForm.value.semiRecords)
  submitForm.value.warehouseName = selectedLocation.value.warehouseName
  submitForm.value.returnInStockFlag = returnInStockFlag.value
  if (firstLevelValues.value === '半成品') {
    let res
    if (selectedLocation.value.warehouseId >= 1000 && selectedLocation.value.warehouseId < 2000) {
      let params = {
        productId: submitForm.value.productId,
        warehouseName: submitForm.value.warehouseName,
        quantity: submitForm.value.quantity,
        unit: submitForm.value.unit,
        screenMeshId: submitForm.value.screenMeshId,
        returnInStockFlag: returnInStockFlag.value,
        entryDate: submitForm.value.entryDate
      }
      res = await addSemiProductStack(params)
    } else {
      let params = {
        productId: submitForm.value.productId,
        warehouseName: submitForm.value.warehouseName,
        quantity: submitForm.value.quantity,
        side: submitForm.value.side,
        unit: submitForm.value.unit,
        screenMeshId: submitForm.value.screenMeshId,
        returnInStockFlag: returnInStockFlag.value,
        entryDate: submitForm.value.entryDate
      }
      res = await addSemiProduct(params)
    }
    submitForm.value.productId = preProductId
    if (res.code === 200) {
      ElMessage.success('新增成功')
      await getAll()
      visible.value = false
      checkAssay.value = 0
    } else {
      ElMessage.error(res.msg)
    }
  } else {
    //如果有两个半成品记录的useAssay为true，则报错
    let hasTwoUseAssay = 0
    for (let i = 0; i < submitForm.value.semiRecords.length; i++) {
      if (submitForm.value.semiRecords[i].useAssay) {
        hasTwoUseAssay++
      }
    }
    if (hasTwoUseAssay > 1) {
      ElMessage.error('只能套用一个半成品的化验数据')
      return
    }
    let reqParams = JSON.parse(JSON.stringify(submitForm.value));
    submitForm.value.productId = preProductId
    let res = await addInStock(reqParams)
    if (res.code === 200) {
      ElMessage.success('新增成功')
      await getAll()
      visible.value = false
      checkAssay.value = 0
      submitForm.value = {
        productId: '',
        warehouseName: '',
        quantity: '',
        unit: '0',
        side: '',
        meshName: ''
      }
    } else {
      ElMessage.error(res.msg)
    }
  }
}

const firstLevelValues = ref('半成品')
// 级联组件配置（保持不变）
const cascaderProps = reactive({
  emitPath: false,
  label: 'label',
  value: 'value',
  children: 'children'
})

// 合并处理成品和半成品数据
const productOptions = computed(() => {
  // 合并数据并添加分类标记
  const combinedProducts = [
    ...StProductList.value.map(p => ({...p, category: '成品'})),
    ...semiProductList.value.map(p => ({...p, category: '半成品'}))
  ]

  const categoryMap = {}

  combinedProducts.forEach(product => {
    // 第一级：成品/半成品
    const categoryNode = categoryMap[product.category] || {
      value: product.category,
      label: product.category,
      children: {}
    }

    // 第二级：产品类型
    const typeNode = categoryNode.children[product.productType] || {
      value: product.productType,
      label: product.productType,
      children: []
    }

    // 第三级：具体产品
    const productNode = {
      value: product.productId,
      label: product.productName
    }

    // 更新数据结构
    if (!categoryMap[product.category]) {
      categoryMap[product.category] = categoryNode
    }
    if (!categoryNode.children[product.productType]) {
      categoryNode.children[product.productType] = typeNode
    }
    typeNode.children.push(productNode)
  })

  // 转换数据结构为数组格式
  return Object.values(categoryMap).map(category => ({
    ...category,
    children: Object.values(category.children).map(type => ({
      ...type,
      children: type.children
    }))
  }))
})
const handleUseAssay = (index) => {
  submitForm.value.semiRecords.forEach((item, i) => {
    if (i !== index) {
      item.useAssay = false
    }
  })
}
const semiProductList = ref([])
const warehouseList = ref([])
const SemiProduct = async () => {
  let res = await getSemiProduct()
  semiProductList.value = res.data
}
const handleSemiProductChange = (item) => {
  item.warehouseName = ''
  item.warehouseId = null
  if (!item.semiProductId || item.semiProductId === '') {
    warehouseList.value = []
    return
  }
  getProductWarehouseData(item.semiProductId)
}
const getProductWarehouseData = async (productId) => {
  let res = await getProductWarehouse(productId)
  warehouseList.value = res.data

}
const StProductList = ref([])
const StProduct = async () => {
  let res = await getStProduct()
  StProductList.value = res.data
}
const meshList = ref([])
const getMeshList = async () => {
  let res = await getMesh()
  if (res.code === 200) {
    meshList.value = res.data
  } else {
    ElMessage.error(res.msg)
  }
}
onMounted(async () => {
  await SemiProduct()
  await StProduct()
  await getMeshList()
})
</script>

<style scoped>
.home-workbench {
  display: grid;
  gap: 14px;
}

.workbench-hero {
  border: 1px solid var(--app-border-soft);
  border-radius: var(--app-radius);
  background:
      linear-gradient(135deg, rgba(22, 93, 255, 0.08) 0%, rgba(255, 255, 255, 0.94) 48%),
      var(--app-panel);
  box-shadow: var(--app-shadow-soft);
}

.workbench-hero :deep(.el-card__body) {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 18px;
}

.hero-eyebrow,
.section-subtitle,
.metric-hint,
.quick-desc {
  color: var(--app-text-tertiary);
  font-size: 13px;
}

.hero-title {
  margin-top: 6px;
  color: var(--app-text);
  font-size: 22px;
  font-weight: 700;
}

.hero-subtitle {
  margin-top: 8px;
  color: var(--app-text-secondary);
}

.hero-time {
  flex: 0 0 auto;
  padding: 10px 16px;
  border: 1px solid var(--app-border-soft);
  border-radius: var(--app-radius);
  background: #fff;
  color: var(--app-primary);
  font-size: 24px;
  font-weight: 700;
}

.overview-grid,
.quick-grid,
.product-detail-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
  gap: 12px;
}

.metric-card,
.quick-card,
.product-detail-card {
  border: 1px solid var(--app-border-soft);
  border-radius: var(--app-radius);
  background: var(--app-panel);
  box-shadow: var(--app-shadow-soft);
}

.metric-card :deep(.el-card__body) {
  padding: 16px;
}

.metric-label {
  color: var(--app-text-secondary);
  font-size: 13px;
}

.metric-value {
  margin-top: 8px;
  color: var(--app-text);
  font-size: 28px;
  font-weight: 700;
}

.section-title {
  color: var(--app-text);
  font-size: 16px;
  font-weight: 650;
}

.quick-grid {
  margin-top: 14px;
}

.quick-entry {
  min-height: 76px;
  padding: 14px;
  border: 1px solid var(--app-border-soft);
  border-radius: var(--app-radius);
  background: #fbfcff;
  cursor: pointer;
  transition: transform 0.16s ease, box-shadow 0.16s ease, border-color 0.16s ease;
}

.quick-entry:hover {
  border-color: #cfe0ff;
  box-shadow: var(--app-shadow-soft);
  transform: translateY(-1px);
}

.quick-title,
.product-detail-title {
  color: var(--app-text);
  font-weight: 650;
}

.quick-desc {
  margin-top: 6px;
}

.status-overview {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(130px, 1fr));
  gap: 10px;
  margin-bottom: 14px;
}

.status-card {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 12px;
  border: 1px solid var(--app-border-soft);
  border-radius: var(--app-radius);
  background: #fbfcff;
}

.status-card span {
  color: var(--app-text);
  font-weight: 700;
}

.location-detail-card {
  margin-bottom: 18px;
}

.location-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 14px;
}

.product-detail-card {
  padding: 14px;
}

.product-detail-meta {
  display: grid;
  gap: 6px;
  margin: 8px 0 10px;
  color: var(--app-text-secondary);
  font-size: 13px;
}

.clock-card {
  position: relative;
  overflow: hidden;
  border: none;
  background: rgba(0, 0, 0, 0.8);
  height: 200px;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: transform 0.3s, box-shadow 0.3s;
}

.clock-card:hover {
  transform: translateY(-5px);
  box-shadow: 0 10px 20px rgba(64, 158, 255, 0.3);
}

.particle-canvas {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
}

.clock-wrapper {
  position: relative;
  z-index: 1;
  text-align: center;
}

.digital-clock {
  font-family: 'DS-Digital', monospace;
  font-size: 4.5rem;
  color: #fff;
  text-shadow: 0 0 10px rgba(64, 158, 255, 0.8);
  margin-bottom: 1rem;
}

.time-number {
  background: linear-gradient(45deg, #409eff, #00ffff);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  display: inline-block;
  padding: 0 0.2em;
}

.time-colon {
  animation: blink 1s infinite;
}

@keyframes blink {
  0%, 100% {
    opacity: 1;
  }
  50% {
    opacity: 0.3;
  }
}

.date-info {
  display: flex;
  gap: 1rem;
  align-items: center;
  justify-content: center;
  color: #a0d8ff;
  font-size: 1.2rem;
}

.date-info .el-icon {
  font-size: 1.4rem;
  color: #409eff;
}

/* 添加过渡动画 */
.slide-fade-enter-active {
  transition: all 0.3s ease-out;
}

.slide-fade-leave-active {
  transition: all 0.3s cubic-bezier(1, 0.5, 0.8, 1);
}

.slide-fade-enter-from,
.slide-fade-leave-to {
  transform: translateX(20px);
  opacity: 0;
}

/* 确保容器布局正确 */
.el-container {
  display: flex;
  flex-wrap: nowrap;
}

.el-main {
  transition: flex 0.3s ease-in-out;
}

.warehouse-map {
  min-height: 500px; /* 根据实际需要调整 */
}

.location {
  stroke: #cbd5e1;
  stroke-width: 1;
  cursor: pointer;
  transition: filter 0.2s ease,
  stroke-width 0.2s ease;
}

/* 选中状态 */
.location.selected {
  filter: brightness(0.8);
  stroke: #6366f1;
  stroke-width: 2px;
}

/* 鼠标悬停 */
.location:hover {
  filter: brightness(0.95);
  stroke-width: 2;
}

/* 新增禁用状态样式 */
.location.disabled {
  fill: #e2e8f0 !important;
  cursor: not-allowed;
  pointer-events: none; /* 禁用所有鼠标事件 */
}

.location.normal {
  fill: #4bff4e;
}

.location.empty {
  fill: #c2c2c2;
}

.location.danger {
  fill: #fef08a;
}

.location.full {
  fill: #ff5353;
}

.location.maintenance {
  fill: #727272;
}

.location.default {
  fill: #ffffff;
}

.location.filtered {
  fill: #3693ff;
}

.location.info {
  fill: #ffffff;
}


.location-label {
  font-size: 12px;
  text-anchor: middle;
  dominant-baseline: central;
  fill: #475569;
  font-family: 'Helvetica Neue', sans-serif;
  pointer-events: none;
}

/* 关键样式 */
.location-layout-container {
  width: 100%;
  min-width: 300px;
  padding: 12px 0;
}

.columns-wrapper {
  display: flex; /* 核心布局 */
  justify-content: center;
  gap: 40px; /* 列间距 */
  flex-wrap: nowrap; /* 禁止换行 */
}

.column {
  display: flex;
  flex-direction: column;
  align-items: center;
}

.rows-container {
  display: flex;
  flex-direction: column; /* 行号从下往上 */
  gap: 4px;
}

.cell {
  width: 36px;
  height: 36px;
  background: #e5e7eb;
  border-radius: 4px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  color: white;
  transition: all 0.2s;
}

.cell.bg-blue-500 {
  background: #3b82f6;
}

.column-title {
  margin-top: 8px;
  font-size: 12px;
  color: #666;
}

/* 垂直文字样式 */
.vertical-text {
  writing-mode: tb;
  glyph-orientation-vertical: 0;
  letter-spacing: 0.2em;
  /* 微调垂直位置 */
  transform: translateY(8px);
}

/* 水平文字样式 */
.location-label {
  font-size: 14px;
  fill: #666;
  pointer-events: none;
}
</style>
