<template>
  <div class="dashboard-container">
    <el-card class="clock-card">
      <!-- 粒子背景画布 -->
      <canvas ref="canvas" class="particle-canvas"></canvas>

      <!-- 时钟主体 -->
      <div class="clock-wrapper">
        <!-- 数字时钟 -->
        <div class="digital-clock">
          <span class="time-number">{{ hours }}</span>
          <span class="time-colon">:</span>
          <span class="time-number">{{ minutes }}</span>
          <span class="time-colon">:</span>
          <span class="time-number">{{ seconds }}</span>
        </div>

        <!-- 日期信息 -->
        <div class="date-info">
          <el-icon><calendar /></el-icon>
          <span>{{ formattedDate }}</span>
          <el-icon><timer /></el-icon>
          <span>{{ dayOfWeek }}</span>
        </div>
      </div>
    </el-card>
  </div>
  <el-card class="search-card">
  <el-form :model="searchWarehouseForm" inline>
    <el-form-item label="产品名称">
      <el-input
          v-model="searchWarehouseForm.productName"
          placeholder="请输入产品名称"
          clearable
          style="width: 200px"
      >
      </el-input>
    </el-form-item>
    <el-form-item label="标准名称">
      <el-input-tag
          v-model="searchWarehouseForm.standardNames"
          placeholder="请输入标准名称后回车"
          clearable
          style="width: 420px"
      />
    </el-form-item>
    <el-form-item label="筛网ID">
      <el-input
          v-model="searchWarehouseForm.screenMeshId"
          placeholder="请输入筛网ID"
          clearable
          style="width: 300px"
      />
    </el-form-item>
    <el-form-item label="时间范围">
      <el-date-picker
          v-model="searchWarehouseForm.dateRange"
          type="daterange"
          range-separator="至"
          start-placeholder="开始日期"
          end-placeholder="结束日期"
          style="width: 400px"
      />
    </el-form-item>
    <el-form-item>
      <el-button type="primary" @click="handleSearch">查询</el-button>
      <el-button @click="handleReset">重置</el-button>
    </el-form-item>
  </el-form>
  </el-card>
  <el-container class="h-screen">
    <!-- 左侧库位图 -->
    <el-main
        class="p-4 bg-gray-50 transition-all duration-300"
        :style="{ flex: `0 0 ${selectedLocation ? 'calc(100% - 360px)' : '100%'}`}"
        v-loading="loadingMap"
    >
      <div class="border rounded-lg bg-white p-4 h-full">
        <svg
            :viewBox="`0 0 ${viewBoxWidth} ${viewBoxHeight}`"
            class="warehouse-map w-full h-full"
            @click.self.stop="handleCanvasClick"
        >
          <g v-for="location in locations" :key="location.id">
            <rect
                :x="location.x"
                :y="location.y"
                :width="location.width"
                :height="location.height"
                :class="[
                  'location',
                  location.status,
                  { 'selected': location.id === selectedLocation?.id,
                    'disabled': location.status === 'default'
                    //透明度根据capacity计算
                  }
                ]"
                :style="getLocationStyle(location)"
                @click="location.status !== 'default' && handleSelectLocation(location)"
            />
            <text
                :x="location.x + location.width/2"
                :y="location.y + location.height/2"
                class="location-label"
            >
              {{ location.id }}
            </text>
          </g>
        </svg>
      </div>
    </el-main>

    <!-- 右侧信息面板 -->
    <transition name="slide-fade">
      <el-aside
          v-if="selectedLocation"
          key="aside"
          width="360px"
          class="border-l p-4 bg-white h-full"
      >
      <div>
        <h2 class="text-lg font-bold mb-4">{{ selectedLocation.warehouseName }} 详情</h2>
        <el-descriptions :column="1" border>
          <el-descriptions-item label="状态">
            <el-tag :type="statusTagMap[selectedLocation.status]">
              {{ statusMap[selectedLocation.status] }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="库名">
            {{ selectedLocation.warehouseName }}
          </el-descriptions-item>
          <el-descriptions-item label="产品名称">
            {{ selectedLocation.productName }}
          </el-descriptions-item>
          <el-descriptions-item label="数量">
            {{ selectedLocation.totalQuantity }}板
          </el-descriptions-item>
          <el-descriptions-item label="重量">
            {{ selectedLocation.totalWeight }}kg
          </el-descriptions-item>
          <el-descriptions-item label="入库日期">
            {{ selectedLocation.entryDate }}
          </el-descriptions-item>
        </el-descriptions>
      </div>
        <!-- 修改后的模板 -->
        <div class="location-layout-container" v-if="selectedLocation.status === 'filtered'">
          <!-- 添加flex横向布局容器 -->
          <div class="columns-wrapper">
            <!-- LEFT列 -->
            <div class="column">
              <div class="rows-container">
                <div
                    v-for="row in maxRowNum"
                    :key="`left-${row}`"
                    class="cell"
                    :class="getCellClass('LEFT', row)"
                >
                  {{ row }}
                </div>
              </div>
              <div class="column-title">LEFT</div>
            </div>

            <!-- RIGHT列 -->
            <div class="column">
              <div class="rows-container">
                <div
                    v-for="row in maxRowNum"
                    :key="`right-${row}`"
                    class="cell"
                    :class="getCellClass('RIGHT', row)"
                >
                  {{ row }}
                </div>
              </div>
              <div class="column-title">RIGHT</div>
            </div>
          </div>
        </div>
    </el-aside>
    </transition>
  </el-container>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted, reactive , onBeforeMount, watchEffect } from 'vue'
import { Calendar, Timer } from '@element-plus/icons-vue'
import { throttle } from 'lodash-es'
import { getWarehouseInfo, getAllWarehouseCapacity, getWarehouseList, getWarehouseById, getMaxRowNum } from '@/api/warehouseinfo'
import { ElMessage } from 'element-plus'
//查询仓库信息
let searchWarehouseForm = ref({
  productName: '',
  standardNames: [],
  screenMeshId: '',
  dateRange: [],
})
const loadingMap = ref(false)
const warehousesList = ref([])
const filteredInfo = ref([])
// 处理搜索
const handleSearch = async () => {
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
    params.startTime = searchWarehouseForm.value.dateRange[0]
    params.endTime = searchWarehouseForm.value.dateRange[1]
  }
  if (searchWarehouseForm.value.productName === '' && searchWarehouseForm.value.standardNames.length === 0 && searchWarehouseForm.value.screenMeshId === '' && searchWarehouseForm.value.dateRange.length === 0) {
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
      if(index === -1){
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
    standardNames: [],
    screenMeshId: '',
    dateRange: [],
  }
  filteredInfo.value = []
  getAll()
}
// 过滤库位处理
// const filterLocations =ref([
//   {
//     x: 150,
//     y: 20,
//     width: 200,
//     height: 510,
//     status: 'info'
//   },
//   {
//     id: 10,
//     x: 160,
//     y: 30,
//     width: 40,
//     height: 40,
//     status: 'default'
//   },
//   {
//     id: 11,
//     x: 160,
//     y: 80,
//     width: 40,
//     height: 40,
//     status: 'default'
//   },
//   {
//     id: 12,
//     x: 160,
//     y: 130,
//     width: 40,
//     height: 40,
//     status: 'default'
//   },
//   {
//     id: 13,
//     x: 160,
//     y: 180,
//     width: 40,
//     height: 40,
//     status: 'default'
//   },
//   {
//     id: 14,
//     x: 160,
//     y: 230,
//     width: 40,
//     height: 40,
//     status: 'default'
//   },
//   {
//     id: 15,
//     x: 160,
//     y: 280,
//     width: 40,
//     height: 40,
//     status: 'default'
//   },
//   {
//     id: 16,
//     x: 160,
//     y: 330,
//     width: 40,
//     height: 40,
//     status: 'default'
//   },
//   {
//     id: 17,
//     x: 160,
//     y: 380,
//     width: 40,
//     height: 40,
//     status: 'default'
//   },
//   {
//     id: 18,
//     x: 160,
//     y: 430,
//     width: 40,
//     height: 40,
//     status: 'default'
//   },
//   {
//     id: 19,
//     x: 160,
//     y: 480,
//     width: 40,
//     height: 40,
//     status: 'default'
//   },
//   {
//     id: 20,
//     x: 300,
//     y: 30,
//     width: 40,
//     height: 40,
//     status: 'default'
//   },
//   {
//     id: 21,
//     x: 300,
//     y: 80,
//     width: 40,
//     height: 40,
//     status: 'default'
//   },
//   {
//     id: 22,
//     x: 300,
//     y: 130,
//     width: 40,
//     height: 40,
//     status: 'default'
//   },
//   {
//     id: 23,
//     x: 300,
//     y: 180,
//     width: 40,
//     height: 40,
//     status: 'default'
//   },
//   {
//     id: 24,
//     x: 300,
//     y: 230,
//     width: 40,
//     height: 40,
//     status: 'default'
//   },
//   {
//     id: 25,
//     x: 300,
//     y: 280,
//     width: 40,
//     height: 40,
//     status: 'default'
//   },
//   {
//     id: 26,
//     x: 300,
//     y: 330,
//     width: 40,
//     height: 40,
//     status: 'default'
//   },
//   {
//     id: 27,
//     x: 300,
//     y: 380,
//     width: 40,
//     height: 40,
//     status: 'default'
//   },
//   {
//     id: 28,
//     x: 300,
//     y: 430,
//     width: 40,
//     height: 40,
//     status: 'default'
//   },
//   {
//     id: 29,
//     x: 300,
//     y: 480,
//     width: 40,
//     height: 40,
//     status: 'default'
//   }
// ])
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
const dayOfWeek = computed(() => ['周日','周一','周二','周三','周四','周五','周六'][time.value.getDay()])
const formattedDate = computed(() => {
  const d = time.value
  return `${d.getFullYear()}-${(d.getMonth()+1).toString().padStart(2,'0')}-${d.getDate().toString().padStart(2,'0')}`
})
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
    if(this.x < 0) this.x += canvasWidth
    if(this.x > canvasWidth) this.x -= canvasWidth
    if(this.y < 0) this.y += canvasHeight
    if(this.y > canvasHeight) this.y -= canvasHeight
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
    particlesArray = Array.from({ length: particles.count }, () =>
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
  setTimeout(initParticles, 100) // 延迟初始化确保容器渲染
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
})
// 库位数据（示例）
const locations = ref([
  {
    id: 1,
    x:992,
    y: 270,
    width: 30,
    height: 130,
    status: 'default'
  },
  {
    id: 2,
    x:956,
    y: 270,
    width: 30,
    height: 130,
    status: 'default'
  },
  {
    id: 3,
    x:926,
    y: 270,
    width: 30,
    height: 130,
    status: 'default'
  },
  {
    id: 4,
    x:896,
    y: 270,
    width: 30,
    height: 130,
    status: 'default'
  },
  {
    id: 5,
    x:860,
    y: 270,
    width: 30,
    height: 130,
    status: 'default'
  },
  {
    id: 6,
    x:830,
    y: 270,
    width: 30,
    height: 130,
    status: 'default'
  },
  {
    id: 7,
    x:800,
    y: 270,
    width: 30,
    height: 130,
    status: 'default'
  },
  {
    id: 8,
    x:758,
    y: 270,
    width: 30,
    height: 130,
    status: 'default'
  },
  {
    id: 9,
    x:728,
    y: 270,
    width: 30,
    height: 130,
    status: 'default'
  },
  {
    id: 10,
    x:698,
    y: 270,
    width: 30,
    height: 130,
    status: 'default'
  },
  {
    id: 11,
    x:662,
    y: 270,
    width: 30,
    height: 130,
    status: 'default'
  },
  {
    id: 12,
    x:632,
    y: 270,
    width: 30,
    height: 130,
    status: 'default'
  },
  {
    id: 13,
    x:602,
    y: 270,
    width: 30,
    height: 130,
    status: 'default'
  },
  {
    id: 14,
    x:566,
    y: 270,
    width: 30,
    height: 130,
    status: 'default'
  },
  {
    id: 15,
    x:536,
    y: 270,
    width: 30,
    height: 130,
    status: 'default'
  },
  {
    id: 16,
    x:506,
    y: 270,
    width: 30,
    height: 130,
    status: 'default'
  },
  {
    id: 17,
    x:470,
    y: 270,
    width: 30,
    height: 130,
    status: 'default'
  },
  {
    id: 18,
    x:440,
    y: 270,
    width: 30,
    height: 130,
    status: 'default'
  },
  {
    id: 19,
    x:410,
    y: 270,
    width: 30,
    height: 130,
    status: 'default'
  },
  {
    id: 20,
    x:374,
    y: 270,
    width: 30,
    height: 130,
    status: 'default'
  },
  {
    id: 21,
    x:314,
    y: 270,
    width: 30,
    height: 130,
    status: 'default'
  },
  {
    id: 22,
    x:272,
    y: 270,
    width: 30,
    height: 130,
    status: 'default'
  },
  {
    id: 23,
    x:242,
    y: 270,
    width: 30,
    height: 130,
    status: 'default'
  },
  {
    id: 24,
    x:212,
    y: 270,
    width: 30,
    height: 130,
    status: 'default'
  },
  {
    id: 25,
    x:176,
    y: 270,
    width: 30,
    height: 130,
    status: 'default'
  },
  {
    id: 26,
    x:176,
    y: 238,
    width: 130,
    height: 30,
    status: 'default'
  },
  {
    id: 27,
    x:176,
    y: 208,
    width: 130,
    height: 30,
    status: 'default'
  },
  {
    id: 28,
    x:176,
    y: 178,
    width: 130,
    height: 30,
    status: 'default'
  },
  {
    id: 29,
    x:176,
    y: 142,
    width: 130,
    height: 30,
    status: 'default'
  },
  {
    id: 30,
    x:176,
    y: 82,
    width: 130,
    height: 60,
    status: 'default'
  },
  {
    id: 31,
    x:20,
    y: 82,
    width: 130,
    height: 30,
    status: 'default'
  },
  {
    id: 32,
    x:20,
    y: 112,
    width: 130,
    height: 30,
    status: 'default'
  },
  {
    id: 33,
    x:20,
    y: 142,
    width: 130,
    height: 30,
    status: 'default'
  },
  {
    id: 34,
    x:20,
    y: 178,
    width: 130,
    height: 30,
    status: 'default'
  },
  {
    id: 35,
    x:20,
    y: 208,
    width: 130,
    height: 30,
    status: 'default'
  },
  {
    id: 36,
    x:20,
    y: 238,
    width: 130,
    height: 30,
    status: 'default'
  },
  {
    id: 37,
    x:20,
    y: 274,
    width: 130,
    height: 30,
    status: 'default'
  },
  {
    id: 38,
    x:20,
    y: 304,
    width: 130,
    height: 30,
    status: 'default'
  },
  {
    id: 39,
    x:20,
    y: 334,
    width: 130,
    height: 30,
    status: 'default'
  },
  {
    id: 40,
    x:20,
    y: 370,
    width: 130,
    height: 30,
    status: 'default'
  },
  {
    id: 41,
    x:20,
    y: 430,
    width: 30,
    height: 130,
    status: 'default'
  },
  {
    id: 42,
    x:50,
    y: 430,
    width: 30,
    height: 130,
    status: 'default'
  },
  {
    id: 43,
    x:80,
    y: 430,
    width: 30,
    height: 130,
    status: 'default'
  },
  {
    id: 44,
    x:116,
    y: 430,
    width: 30,
    height: 130,
    status: 'default'
  },
  {
    id: 45,
    x:146,
    y: 430,
    width: 30,
    height: 130,
    status: 'default'
  },
  {
    id: 46,
    x:176,
    y: 430,
    width: 30,
    height: 130,
    status: 'default'
  },
  {
    id: 47,
    x:212,
    y: 430,
    width: 30,
    height: 130,
    status: 'default'
  },
  {
    id: 48,
    x:242,
    y: 430,
    width: 30,
    height: 130,
    status: 'default'
  },
  {
    id: 49,
    x:272,
    y: 430,
    width: 30,
    height: 130,
    status: 'default'
  },
  {
    id: 50,
    x:314,
    y: 430,
    width: 30,
    height: 130,
    status: 'default'
  },
  {
    id: 51,
    x:374,
    y: 430,
    width: 30,
    height: 130,
    status: 'default'
  },
  {
    id: 52,
    x:410,
    y: 430,
    width: 30,
    height: 130,
    status: 'default'
  },
  {
    id: 53,
    x:440,
    y: 430,
    width: 30,
    height: 130,
    status: 'default'
  },
  {
    id: 54,
    x:470,
    y: 430,
    width: 30,
    height: 130,
    status: 'default'
  },
  {
    id: 55,
    x:506,
    y: 430,
    width: 30,
    height: 130,
    status: 'default'
  },
  {
    id: 56,
    x:536,
    y: 430,
    width: 30,
    height: 130,
    status: 'default'
  },
  {
    id: 57,
    x:566,
    y: 430,
    width: 30,
    height: 130,
    status: 'default'
  },
  {
    id: 58,
    x:602,
    y: 430,
    width: 30,
    height: 130,
    status: 'default'
  },
  {
    id: 59,
    x:632,
    y: 430,
    width: 30,
    height: 130,
    status: 'default'
  },
  {
    id: 60,
    x:662,
    y: 430,
    width: 30,
    height: 130,
    status: 'default'
  },
  {
    id: 61,
    x:698,
    y: 430,
    width: 30,
    height: 130,
    status: 'default'
  },
  {
    id: 62,
    x:728,
    y: 430,
    width: 30,
    height: 130,
    status: 'default'
  },
  {
    id: 63,
    x:758,
    y: 430,
    width: 30,
    height: 130,
    status: 'default'
  },
  {
    id: 64,
    x:896,
    y: 430,
    width: 30,
    height: 130,
    status: 'default'
  },
  {
    id: 65,
    x:926,
    y: 430,
    width: 30,
    height: 130,
    status: 'default'
  },
  {
    id: 66,
    x:956,
    y: 430,
    width: 30,
    height: 130,
    status: 'default'
  },
  {
    id: 67,
    x:992,
    y: 430,
    width: 30,
    height: 130,
    status: 'default'
  },
  {
    id: '多晶冰糖包装间',
    x:312,
    y: 28,
    width: 710,
    height: 240,
    status: "default",
  },
  {
    id: 101,
    x:1100,
    y: 430,
    width: 30,
    height: 130,
    status: 'default'
  }
])
// 加载状态
const loading = ref(false)
// 处理库位点击
const handleSelectLocation = async (location) => {
  ElMessage.success(`已选中 ${location.id}号仓库`)
  selectedLocation.value = location
  loading.value = true
  let isFiltered = false
  if (location.status === 'filtered') {
    isFiltered = true
  }
  let params = {
    warehouseId: location.id,
    page:1,
    size:1000
  }
  let result = await getWarehouseInfo(params)
  if (result.code === 200) {
    selectedLocation.value.warehouseId = result.data.records[0].warehouseId
    selectedLocation.value.warehouseName = result.data.records[0].warehouseName
    selectedLocation.value.productName = result.data.records[0].productName
    selectedLocation.value.totalQuantity = result.data.records[0].totalQuantity
    selectedLocation.value.totalWeight = result.data.records[0].totalWeight
    selectedLocation.value.entryDate = result.data.records[0].entryDate
    selectedLocation.value.firstEntryDate = result.data.records[0].firstEntryDate
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
  locationElement.classList.add('selected')
  setTimeout(() => {
    locationElement.classList.remove('selected')
  }, 1000)
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
  if(location.status === 'normal') {
    const baseColor = statusColorMap[location.status]
    const capacity = location.capacityPercentage ?? 1 // 默认100%
    const alpha = Math.min(Math.max(1 - capacity, 0.3), 0.7)
    return {
      fill: hexToRgba(baseColor, alpha.toFixed(2))
    }
  }
}
let CapacityList=ref([]);
const getAll = async () => {
  let result =  await getAllWarehouseCapacity();
  if (result.code === 200) {
    CapacityList.value = result.data
    CapacityList.value.forEach(item => {
        //查找locations中id为item.warehouseId的对象，并更新其capacityPercentage和status属性
        const index = locations.value.findIndex(location => location.id === item.warehouseId)
        if(index === -1){
          return
        }
        locations.value[index].capacityPercentage = item.capacityPercentage
        if(item.status === '正常'){
          locations.value[index].status = 'normal'
        }else if(item.status === '空置'){
          locations.value[index].status = 'empty'
        }else if(item.status === '临期预警'){
          locations.value[index].status = 'danger'
        }else if(item.status === '满仓'){
          locations.value[index].status = 'full'
        }else if(item.status === '维护'){
          locations.value[index].status = 'maintenance'
        }else{
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
</script>

<style scoped>
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
  0%, 100% { opacity: 1; }
  50% { opacity: 0.3; }
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
  transition:
      filter 0.2s ease,
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

.location.normal { fill: #4bff4e; }
.location.empty { fill: #c2c2c2; }
.location.danger { fill: #fef08a; }
.location.full { fill: #ff5353; }
.location.maintenance { fill: #727272; }
.location.default { fill: #ffffff; }
.location.filtered { fill: #3693ff; }
.location.info { fill: #ffffff; }


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
  display: flex;          /* 核心布局 */
  justify-content: center;
  gap: 40px;              /* 列间距 */
  flex-wrap: nowrap;      /* 禁止换行 */
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
</style>