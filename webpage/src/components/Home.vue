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
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { Calendar, Timer } from '@element-plus/icons-vue'
import { throttle } from 'lodash-es'

// 时间数据
const time = ref(new Date())
const canvas = ref(null)
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
})
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

</style>