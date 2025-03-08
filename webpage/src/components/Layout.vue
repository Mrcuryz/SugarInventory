<script setup>
defineProps({
  title: String,
  value: [String, Number],
  icon: String,
  color: String
})
</script>
<template>
  <el-card class="stat-card">
    <div class="card-content">
      <div class="icon-wrapper" :style="{ background: color + '10' }">
        <el-icon :color="color">
          <component :is="icon" />
        </el-icon>
      </div>
      <div class="card-info">
        <div class="title">{{ title }}</div>
        <div class="value" :style="{ color: color }">{{ value }}</div>
      </div>
    </div>
  </el-card>
</template>

<style scoped>
.stat-card {
  border: none;

  :deep(.el-card__body) {
    padding: 20px;
  }

  .card-content {
    display: flex;
    align-items: center;

    .icon-wrapper {
      width: 48px;
      height: 48px;
      border-radius: 8px;
      display: flex;
      align-items: center;
      justify-content: center;
      margin-right: 16px;

      .el-icon {
        font-size: 24px;
      }
    }

    .card-info {
      .title {
        color: #909399;
        margin-bottom: 8px;
      }

      .value {
        font-size: 24px;
        font-weight: bold;
      }
    }
  }
}
</style>