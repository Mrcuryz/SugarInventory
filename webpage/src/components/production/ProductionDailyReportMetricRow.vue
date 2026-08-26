<script setup>
defineProps({
  metric: {
    type: Object,
    required: true
  },
  disabled: {
    type: Boolean,
    default: false
  }
})
</script>

<template>
  <div class="metric-row">
    <div class="report-cell report-cell--name">{{ metric.metricName }}</div>
    <div class="report-cell report-cell--unit">{{ metric.unit }}</div>
    <div class="report-cell"><el-input v-model="metric.dailyActual" :disabled="disabled" inputmode="decimal" /></div>
    <div class="report-cell"><el-input v-model="metric.convertedTons" :disabled="disabled" inputmode="decimal" /></div>
    <div class="report-cell"><el-input v-model="metric.monthQuantity" :disabled="disabled" inputmode="decimal" /></div>
    <div class="report-cell"><el-input v-model="metric.monthTons" :disabled="disabled" inputmode="decimal" /></div>
    <div class="report-cell"><el-input v-model="metric.yearTons" :disabled="disabled" inputmode="decimal" /></div>
    <div class="report-cell report-cell--remark"><el-input v-model="metric.remark" :disabled="disabled" maxlength="500" /></div>
    <div class="report-cell report-cell--operation">—</div>
  </div>
</template>

<style scoped>
.metric-row {
  display: contents;
}
</style>
