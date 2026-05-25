<template>
  <div class="operation-logs">
    <el-card class="search-card" style="max-width: 1200px">
      <div class="page-title">任务中心</div>
      <div class="page-subtitle">按业务场景进入对应任务页面，避免不同操作混在同一张表里。</div>
      <div class="entry-grid">
        <el-card v-for="item in entries" :key="item.path" shadow="hover" class="entry-card" @click="router.push(item.path)">
          <div class="entry-title">{{ item.title }}</div>
          <div class="entry-desc">{{ item.desc }}</div>
          <el-button type="primary" size="small">进入</el-button>
        </el-card>
      </div>
    </el-card>
  </div>
</template>

<script setup>
import {useRouter} from 'vue-router'

const router = useRouter()
const entries = [
  {title: '半成品入库任务', desc: '确认半成品二维码入库、取消待处理任务', path: '/pallet-task/semi/in'},
  {title: '半成品出库任务', desc: '确认半成品普通出库；进入生产走生产订单领用', path: '/pallet-task/semi/out'},
  {title: '成品入库任务', desc: '确认成品二维码入库；追溯关系走生产订单', path: '/pallet-task/finish/in'},
  {title: '成品出库任务', desc: '创建并确认成品二维码出库', path: '/pallet-task/finish/out'},
  {title: '调拨任务', desc: '创建并确认调拨', path: '/pallet-task/transfer'}
]
</script>

<style scoped>
.operation-logs {
  padding: 20px;
}

.search-card {
  margin-bottom: 20px;
  background: var(--app-panel);
}

.page-title {
  font-size: 18px;
  font-weight: 600;
  color: #303133;
}

.page-subtitle {
  margin-top: 6px;
  color: #909399;
  font-size: 13px;
}

.entry-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
  gap: 16px;
  margin-top: 18px;
}

.entry-card {
  cursor: pointer;
  border-radius: 8px;
}

.entry-title {
  margin-bottom: 8px;
  font-weight: 600;
}

.entry-desc {
  min-height: 40px;
  margin-bottom: 14px;
  color: #606266;
  font-size: 13px;
}
</style>
