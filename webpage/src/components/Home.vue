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
  <el-container class="h-screen">
    <!-- 左侧库位图 -->
    <el-main class="p-4 bg-gray-50">
      <div class="border rounded-lg bg-white p-4">
        <svg
            :viewBox="`0 0 ${viewBoxWidth} ${viewBoxHeight}`"
            class="warehouse-map"
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
    <el-aside width="360px" class="border-l p-4 bg-white">
      <div v-if="selectedLocation">
        <h2 class="text-lg font-bold mb-4">{{ selectedLocation.id }} 详情</h2>
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
            {{ selectedLocation.totalQuantity }}
          </el-descriptions-item>
          <el-descriptions-item label="重量">
            {{ selectedLocation.totalWeight }}
          </el-descriptions-item>
          <el-descriptions-item label="入库日期">
            {{ selectedLocation.entryDate }}
          </el-descriptions-item>
        </el-descriptions>
      </div>
      <el-empty v-else description="请点击库位查看详情" />
    </el-aside>
  </el-container>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted, reactive , onBeforeMount} from 'vue'
import { Calendar, Timer } from '@element-plus/icons-vue'
import { throttle } from 'lodash-es'
import { getWarehouseInfo, getAllWarehouseCapacity } from '@/api/warehouseinfo'
import { ElMessage } from 'element-plus'

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
const selectedLocation = ref([])

// 状态映射配置
const statusMap = reactive({
  normal: '正常',
  empty: '空置',
  full: '满仓',
  danger: '临期预警',
  maintenance: '维护',
  default: '默认',
})

const statusTagMap = reactive({
  normal: 'success',
  empty: 'warning',
  full: 'danger',
  danger: 'danger',
  maintenance: 'warning',
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
  let params = {
    warehouseId: location.id,
    page:1,
    size:1000
  }
  let result = await getWarehouseInfo(params)
  console.log(result.data.records[0])
  if (result.code === 200) {
    selectedLocation.value.warehouseName = result.data.records[0].warehouseName
    selectedLocation.value.productName = result.data.records[0].productName
    selectedLocation.value.totalQuantity = result.data.records[0].totalQuantity
    selectedLocation.value.totalWeight = result.data.records[0].totalWeight
    selectedLocation.value.entryDate = result.data.records[0].entryDate
    selectedLocation.value.firstEntryDate = result.data.records[0].firstEntryDate
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
  default: '#e2e8f0'
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
// onBeforeMount(() => {
//   getAll()
//   setInterval(() => {
//     getAll()
//   }, 10000)
// })
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


.warehouse-map {
  width: 100%;
  height: calc(100vh - 120px);
  background-color: #f8fafc;
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


.location-label {
  font-size: 12px;
  text-anchor: middle;
  dominant-baseline: central;
  fill: #475569;
  font-family: 'Helvetica Neue', sans-serif;
  pointer-events: none;
}
</style>