<template>
  <div class="timeline-group" :class="{ 'timeline-group--expanded': expanded, 'timeline-group--single': !canExpand }">
    <button type="button" class="timeline-group-summary" :disabled="!canExpand" @click="toggleExpanded">
      <span class="timeline-group-dot"></span>
      <span class="timeline-group-time">{{ formatDateTime(group.occurredAt) || '-' }}</span>
      <span class="timeline-group-action">{{ group.actionType || '-' }}</span>
      <span class="timeline-group-doc" :title="group.documentNo">{{ group.documentNo || '-' }}</span>
      <span class="timeline-group-product" :title="group.productSummary">{{ group.productSummary || '-' }}</span>
      <span class="timeline-group-qty" :title="group.quantitySummary">{{ group.quantitySummary || '-' }}</span>
      <span class="timeline-group-warehouse" :title="group.warehouseSummary">{{ group.warehouseSummary || '-' }}</span>
      <span class="timeline-group-related" :title="group.summary">{{ group.summary || '-' }}</span>
      <span class="timeline-group-status"><el-tag size="small" :type="statusType(group.status)">{{ group.status || '-' }}</el-tag></span>
      <span class="timeline-group-count">
        <el-tag v-if="canExpand" size="small" type="info">共 {{ group.count }} 条</el-tag>
        <span v-else class="timeline-group-count-single">单条</span>
      </span>
      <el-icon v-if="canExpand" class="timeline-group-arrow"><ArrowDown /></el-icon>
      <span v-else class="timeline-group-arrow-placeholder"></span>
    </button>

    <div v-if="canExpand && expanded" class="timeline-group-children">
      <div class="timeline-group-child-head">
        <span>具体单号/二维码</span>
        <span>产品/物料</span>
        <span>数量</span>
        <span>库位</span>
        <span>关联对象</span>
        <span>状态</span>
      </div>
      <div v-for="record in group.children" :key="record.key" class="timeline-group-child-row">
        <span :title="record.documentNo">{{ record.documentNo || '-' }}</span>
        <span :title="record.productName">{{ record.productName || '-' }}</span>
        <span>{{ record.quantityText || '-' }}</span>
        <span>{{ record.warehouseName || '-' }}</span>
        <span :title="record.relatedObject">{{ record.relatedObject || '-' }}</span>
        <el-tag size="small" :type="statusType(record.status)">{{ record.status || '-' }}</el-tag>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, ref } from 'vue'
import { ArrowDown } from '@element-plus/icons-vue'
import { formatDateTime } from '@/utils/dateTime'

const props = defineProps({
  group: {
    type: Object,
    required: true
  }
})

const expanded = ref(false)
const canExpand = computed(() => Number(props.group?.count || 0) > 1)

const toggleExpanded = () => {
  if (!canExpand.value) return
  expanded.value = !expanded.value
}

const statusType = status => {
  const value = String(status || '')
  if (value.includes('入库') || value.includes('领用') || value.includes('完成') || value.includes('消耗')) return 'success'
  if (value.includes('待') || value.includes('预占')) return 'warning'
  return 'info'
}
</script>

<style scoped>
.timeline-group {
  position: relative;
  min-width: 1160px;
  border-bottom: 1px solid #e8edf4;
  background: #f8faff;
}
.timeline-group:last-child {
  border-bottom: 0;
}
.timeline-group-summary {
  width: 100%;
  min-height: 52px;
  display: grid;
  grid-template-columns: 132px 126px minmax(140px, 1.1fr) minmax(120px, 1fr) minmax(150px, 1.2fr) 92px minmax(132px, 1fr) 88px 78px 24px;
  align-items: center;
  gap: 10px;
  padding: 8px 10px 8px 28px;
  border: 0;
  background: transparent;
  color: #34445c;
  font: inherit;
  font-size: 12px;
  text-align: left;
  cursor: pointer;
}
.timeline-group-summary:hover {
  background: #f1f6ff;
}
.timeline-group-summary:disabled {
  cursor: default;
}
.timeline-group-summary:disabled:hover {
  background: transparent;
}
.timeline-group-dot {
  position: absolute;
  left: 8px;
  top: 21px;
  width: 10px;
  height: 10px;
  border: 2px solid #5c8ee8;
  border-radius: 50%;
  background: #fff;
}
.timeline-group::before {
  content: '';
  position: absolute;
  left: 12px;
  top: 0;
  bottom: 0;
  width: 1px;
  background: #d9e2ee;
}
.timeline-group-action {
  color: #263a57;
  font-weight: 600;
}
.timeline-group-time,
.timeline-group-warehouse,
.timeline-group-related,
.timeline-group-count-single {
  color: #697991;
}
.timeline-group-doc,
.timeline-group-product,
.timeline-group-qty,
.timeline-group-related {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.timeline-group-status,
.timeline-group-count {
  min-width: 0;
}
.timeline-group-arrow,
.timeline-group-arrow-placeholder {
  color: #718096;
  transition: transform 0.18s ease;
}
.timeline-group-arrow-placeholder {
  width: 16px;
  height: 16px;
}
.timeline-group--expanded .timeline-group-arrow {
  transform: rotate(180deg);
}
.timeline-group--single {
  background: #fbfcff;
}
.timeline-group-children {
  margin: 0 16px 12px 30px;
  border: 1px solid #e4e9f1;
  border-left: 3px solid #cbd9ef;
  border-radius: 4px;
  background: #fff;
}
.timeline-group-child-head,
.timeline-group-child-row {
  display: grid;
  grid-template-columns: minmax(160px, 1.2fr) minmax(130px, 1fr) 110px 100px minmax(160px, 1.2fr) 90px;
  align-items: center;
  gap: 12px;
  min-height: 38px;
  padding: 7px 12px;
  font-size: 12px;
}
.timeline-group-child-head {
  min-height: 34px;
  background: #f6f8fb;
  color: #7a8799;
  font-size: 11px;
}
.timeline-group-child-row + .timeline-group-child-row {
  border-top: 1px solid #edf1f6;
}
.timeline-group-child-row > span {
  overflow: hidden;
  color: #45566e;
  text-overflow: ellipsis;
  white-space: nowrap;
}
</style>