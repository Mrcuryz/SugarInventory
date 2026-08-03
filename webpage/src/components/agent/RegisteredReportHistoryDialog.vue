<script setup>
import { computed, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { getRegisteredReportRun, pageRegisteredReportRuns } from '@/api/analyticsReport'
import { registeredReportSnapshotCard } from './registeredReportSnapshotPresentation.mjs'

const props = defineProps({
  modelValue: { type: Boolean, default: false }
})
const emit = defineEmits(['update:modelValue', 'opened'])

const loading = ref(false)
const openingId = ref('')
const records = ref([])
const total = ref(0)
const page = ref(1)
const size = 8
const reportDefinitionId = ref('')

const visible = computed({
  get: () => props.modelValue,
  set: value => emit('update:modelValue', value)
})

const reportTypes = Object.freeze([
  { value: '', label: '全部报表' },
  { value: 'today_operations_overview_v1', label: '今日运营概览' },
  { value: 'daily_production_overview_v1', label: '生产产量' },
  { value: 'quality_assay_result_trend_v1', label: '化验判定趋势' },
  { value: 'quality_metric_trend_v1', label: '单项指标趋势' },
  { value: 'production_input_output_flow_v1', label: '生产领料与登记产出' },
  { value: 'pallet_task_cycle_time_v1', label: '流程效率' },
  { value: 'inventory_level_trend_v1', label: '库存水平趋势' }
])

const formatDateTime = value => {
  const normalized = String(value || '').trim()
  return normalized
    ? normalized.replace('T', ' ').replace(/\.\d+$/, '')
    : '-'
}

const load = async () => {
  if (!visible.value) return
  loading.value = true
  try {
    const response = await pageRegisteredReportRuns({
      page: page.value,
      size,
      ...(reportDefinitionId.value ? { reportDefinitionId: reportDefinitionId.value } : {})
    })
    records.value = Array.isArray(response.data?.records) ? response.data.records : []
    total.value = Number(response.data?.total || 0)
  } catch {
    records.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

const changeType = () => {
  page.value = 1
  load()
}

const changePage = value => {
  page.value = value
  load()
}

const openSnapshot = async item => {
  if (!item?.reportRunId || openingId.value) return
  openingId.value = item.reportRunId
  try {
    const response = await getRegisteredReportRun(item.reportRunId)
    const card = registeredReportSnapshotCard(response.data)
    if (!card) {
      ElMessage.error('当前报表类型暂不支持重新打开')
      return
    }
    emit('opened', { item, report: response.data, card })
    visible.value = false
  } catch {
    // The shared request interceptor already renders the precise server message.
  } finally {
    openingId.value = ''
  }
}

watch(() => props.modelValue, value => {
  if (!value) return
  page.value = 1
  load()
})
</script>

<template>
  <el-dialog
    v-model="visible"
    title="历史报表"
    width="min(720px, calc(100vw - 24px))"
    append-to-body
    destroy-on-close
    class="registered-report-history-dialog"
  >
    <div class="history-toolbar">
      <el-select
        v-model="reportDefinitionId"
        aria-label="筛选报表类型"
        @change="changeType"
      >
        <el-option
          v-for="type in reportTypes"
          :key="type.value || 'all'"
          :label="type.label"
          :value="type.value"
        />
      </el-select>
      <span>仅显示本人生成、当前仍有权限且未过期的报表</span>
    </div>

    <el-alert
      title="历史报表是生成时的不可变快照，不会随当前业务数据自动变化。要查看最新结果，请重新向 AI 助手提问。"
      type="info"
      :closable="false"
      show-icon
    />

    <div v-loading="loading" class="history-list">
      <el-empty v-if="!loading && !records.length" description="暂无可重新打开的历史报表" />
      <article v-for="item in records" :key="item.reportRunId" class="history-item">
        <div class="history-item-main">
          <div class="history-item-title-row">
            <strong>{{ item.reportName }}</strong>
            <el-tag v-if="item.comparisonIncluded" size="small" type="primary">含对比</el-tag>
            <el-tag v-if="item.partialData" size="small" type="warning">数据不完整</el-tag>
          </div>
          <div class="history-item-range">{{ item.dateRangeLabel }}</div>
          <div class="history-item-scope">{{ item.scopeLabel }}</div>
          <div class="history-item-meta">
            生成于 {{ formatDateTime(item.generatedAt) }} · 保留至 {{ formatDateTime(item.expiresAt) }}
          </div>
        </div>
        <el-button
          type="primary"
          plain
          :loading="openingId === item.reportRunId"
          :disabled="Boolean(openingId)"
          @click="openSnapshot(item)"
        >
          打开快照
        </el-button>
      </article>
    </div>

    <el-pagination
      v-if="total > size"
      background
      layout="prev, pager, next"
      :current-page="page"
      :page-size="size"
      :total="total"
      @current-change="changePage"
    />
  </el-dialog>
</template>

<style scoped lang="scss">
.history-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
  color: #667085;
  font-size: 13px;

  .el-select {
    width: 190px;
    flex: none;
  }
}

.history-list {
  min-height: 180px;
  margin-top: 14px;
  display: grid;
  gap: 10px;
}

.history-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 14px;
  border: 1px solid #e4eaf3;
  border-radius: 10px;
  background: #f8faff;
}

.history-item-main {
  min-width: 0;
  display: grid;
  gap: 5px;
}

.history-item-title-row {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 7px;
  color: #1d2939;
}

.history-item-range {
  color: #344054;
  font-size: 14px;
}

.history-item-scope,
.history-item-meta {
  color: #667085;
  font-size: 12px;
}

.el-pagination {
  margin-top: 16px;
  justify-content: flex-end;
}

@media (max-width: 560px) {
  .history-toolbar,
  .history-item {
    align-items: stretch;
    flex-direction: column;
  }

  .history-toolbar .el-select {
    width: 100%;
  }
}
</style>
