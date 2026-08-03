<script setup>
import { computed, defineAsyncComponent, ref } from 'vue'
import { distributionRiskSummary, distributionRows, isDistributionCard } from './agentDisplay'
import {
  assayHistoryRecords,
  assayMetrics,
  assayNotes,
  assayResultTone,
  assaySummary,
  isAssayCard,
  isAssayHistoryCard,
  isAssayReportCard
} from './assayCardPresentation.mjs'
import {
  isTaskCard,
  isTaskDetailCard,
  taskCardStatusSummary,
  taskDetailEntries,
  taskGroupStatusSummary,
  taskGroups,
  taskStatusTone
} from './taskCardPresentation.mjs'
import {
  boilingBatchSummary,
  boilingBatchUsages,
  isBoilingBatchCard,
  isProductionCard,
  isProductionMaterialCard,
  isProductionOrderCard,
  productionMaterials,
  productionBoilingSources,
  productionOrderSummary,
  productionOutputs,
  productionStatusTone,
  productionSummaryEntries,
  productionTraceSteps,
  qrProgressText
} from './productionCardPresentation.mjs'
import {
  isPalletCard,
  isPalletFlowCard,
  isPalletStatusCard,
  palletEventRoute,
  palletFlowEvents,
  palletFlowSummary,
  palletRisks,
  palletStatusSummary,
  palletStatusTone,
  palletSummaryEntries
} from './palletCardPresentation.mjs'
import {
  inventoryQualityRecords,
  inventoryQualitySummary,
  inventoryQualityTone,
  isInventoryQualityCard
} from './inventoryQualityCardPresentation.mjs'
import {
  dailyProductionPoints,
  dailyProductionProducts,
  dailyProductionQualityNotes,
  dailyProductionSummary,
  isDailyProductionReportCard,
  productionQuantityText,
  qrProgressSummary,
  reportDataTimeText
} from './dailyProductionReportPresentation.mjs'
import {
  inventoryLevelTrendDailyPoints,
  inventoryLevelTrendNotes,
  inventoryLevelTrendProducts,
  inventoryLevelTrendScopeNote,
  inventoryLevelTrendSummary,
  inventoryTrendDirection,
  inventoryWeightText,
  isInventoryLevelTrendCard,
  signedInventoryChangeText
} from './inventoryLevelTrendPresentation.mjs'
import {
  isQualityAssayTrendCard,
  qualityAssayTrendNotes,
  qualityAssayTrendPoints,
  qualityAssayTrendProducts,
  qualityAssayTrendStandards,
  qualityAssayTrendSummary,
  qualityJudgementSummary,
  qualityPassRateText
} from './qualityAssayTrendPresentation.mjs'
import {
  isQualityMetricTrendCard,
  metricValueText,
  metricWithinRateText,
  qualityMetricTrendNotes,
  qualityMetricTrendPoints,
  qualityMetricTrendProducts,
  qualityMetricTrendStandards,
  qualityMetricTrendSummary
} from './qualityMetricTrendPresentation.mjs'
import {
  isProductionInputOutputFlowCard,
  productionFlowCoverageText,
  productionFlowDailyPoints,
  productionFlowOrders,
  productionFlowQualityNotes,
  productionFlowScopeNote,
  productionFlowSummary,
  productionFlowWeightText
} from './productionInputOutputFlowPresentation.mjs'
import {
  isPalletTaskCycleReportCard,
  palletTaskCycleDailyPoints,
  palletTaskCycleQualityNotes,
  palletTaskCycleScopeNote,
  palletTaskCycleSummary,
  palletTaskCycleTypes,
  palletTaskPendingItems,
  taskDurationText
} from './palletTaskCycleReportPresentation.mjs'
import {
  isTodayOperationsOverviewCard,
  todayOperationsOverviewNotes,
  todayOperationsOverviewScopeNote,
  todayOperationsOverviewSections,
  todayOperationsOverviewSummary
} from './todayOperationsOverviewPresentation.mjs'
import {
  comparisonChangeText,
  comparisonDailyAverageText,
  comparisonMetricValue,
  registeredReportComparison
} from './registeredReportComparisonPresentation.mjs'
import { isKnowledgeCard, knowledgeEvidence } from './knowledgeCardPresentation.mjs'

const RegisteredReportTrendChart = defineAsyncComponent(
  () => import('./RegisteredReportTrendChart.vue')
)

const props = defineProps({
  card: {
    type: Object,
    required: true
  }
})

const emit = defineEmits(['card-action'])
const isDistribution = computed(() => isDistributionCard(props.card))
const rows = computed(() => distributionRows(props.card))
const riskSummary = computed(() => distributionRiskSummary(props.card))
const isKnowledge = computed(() => isKnowledgeCard(props.card))
const knowledge = computed(() => knowledgeEvidence(props.card))
const isAssay = computed(() => isAssayCard(props.card))
const isAssayReport = computed(() => isAssayReportCard(props.card))
const isAssayHistory = computed(() => isAssayHistoryCard(props.card))
const reportSummary = computed(() => assaySummary(props.card))
const reportMetrics = computed(() => assayMetrics(props.card))
const reportNotes = computed(() => assayNotes(props.card))
const historyRecords = computed(() => assayHistoryRecords(props.card))
const isTask = computed(() => isTaskCard(props.card))
const isTaskDetail = computed(() => isTaskDetailCard(props.card))
const groupedTasks = computed(() => taskGroups(props.card))
const taskSummary = computed(() => taskCardStatusSummary(props.card))
const isProduction = computed(() => isProductionCard(props.card))
const isProductionOrder = computed(() => isProductionOrderCard(props.card))
const isProductionMaterial = computed(() => isProductionMaterialCard(props.card))
const isBoilingBatch = computed(() => isBoilingBatchCard(props.card))
const orderSummary = computed(() => productionOrderSummary(props.card))
const batchSummary = computed(() => boilingBatchSummary(props.card))
const outputRows = computed(() => productionOutputs(props.card))
const boilingSourceRows = computed(() => productionBoilingSources(props.card))
const materialRows = computed(() => productionMaterials(props.card))
const batchUsageRows = computed(() => boilingBatchUsages(props.card))
const traceSteps = computed(() => productionTraceSteps(props.card))
const isPallet = computed(() => isPalletCard(props.card))
const isPalletStatus = computed(() => isPalletStatusCard(props.card))
const isPalletFlow = computed(() => isPalletFlowCard(props.card))
const palletSummary = computed(() => palletStatusSummary(props.card))
const palletHistorySummary = computed(() => palletFlowSummary(props.card))
const palletEvents = computed(() => palletFlowEvents(props.card))
const palletRiskRows = computed(() => palletRisks(props.card))
const isInventoryQuality = computed(() => isInventoryQualityCard(props.card))
const qualitySummary = computed(() => inventoryQualitySummary(props.card))
const qualityRecords = computed(() => inventoryQualityRecords(props.card))
const isDailyProductionReport = computed(() => isDailyProductionReportCard(props.card))
const dailyReportSummary = computed(() => dailyProductionSummary(props.card))
const dailyReportProducts = computed(() => dailyProductionProducts(props.card))
const dailyReportPoints = computed(() => dailyProductionPoints(props.card))
const dailyReportQualityNotes = computed(() => dailyProductionQualityNotes(props.card))
const isInventoryLevelTrend = computed(() => isInventoryLevelTrendCard(props.card))
const inventoryTrendSummary = computed(() => inventoryLevelTrendSummary(props.card))
const inventoryTrendPoints = computed(() => inventoryLevelTrendDailyPoints(props.card))
const inventoryTrendProducts = computed(() => inventoryLevelTrendProducts(props.card))
const inventoryTrendQualityNotes = computed(() => inventoryLevelTrendNotes(props.card))
const inventoryTrendBoundary = computed(() => inventoryLevelTrendScopeNote(props.card))
const isQualityAssayTrend = computed(() => isQualityAssayTrendCard(props.card))
const qualityTrendSummary = computed(() => qualityAssayTrendSummary(props.card))
const qualityTrendProducts = computed(() => qualityAssayTrendProducts(props.card))
const qualityTrendStandards = computed(() => qualityAssayTrendStandards(props.card))
const qualityTrendPoints = computed(() => qualityAssayTrendPoints(props.card))
const qualityTrendNotes = computed(() => qualityAssayTrendNotes(props.card))
const isQualityMetricTrend = computed(() => isQualityMetricTrendCard(props.card))
const metricTrendSummary = computed(() => qualityMetricTrendSummary(props.card))
const metricTrendProducts = computed(() => qualityMetricTrendProducts(props.card))
const metricTrendStandards = computed(() => qualityMetricTrendStandards(props.card))
const metricTrendPoints = computed(() => qualityMetricTrendPoints(props.card))
const metricTrendNotes = computed(() => qualityMetricTrendNotes(props.card))
const isProductionInputOutputFlow = computed(() => isProductionInputOutputFlowCard(props.card))
const productionFlowReportSummary = computed(() => productionFlowSummary(props.card))
const productionFlowReportPoints = computed(() => productionFlowDailyPoints(props.card))
const productionFlowReportOrders = computed(() => productionFlowOrders(props.card))
const productionFlowReportNotes = computed(() => productionFlowQualityNotes(props.card))
const productionFlowReportScopeNote = computed(() => productionFlowScopeNote(props.card))
const isPalletTaskCycleReport = computed(() => isPalletTaskCycleReportCard(props.card))
const taskCycleReportSummary = computed(() => palletTaskCycleSummary(props.card))
const taskCycleReportPoints = computed(() => palletTaskCycleDailyPoints(props.card))
const taskCycleReportTypes = computed(() => palletTaskCycleTypes(props.card))
const taskCyclePendingRows = computed(() => palletTaskPendingItems(props.card))
const taskCycleReportNotes = computed(() => palletTaskCycleQualityNotes(props.card))
const taskCycleReportScopeNote = computed(() => palletTaskCycleScopeNote(props.card))
const isTodayOperationsOverview = computed(() => isTodayOperationsOverviewCard(props.card))
const todayOverviewSummary = computed(() => todayOperationsOverviewSummary(props.card))
const todayOverviewSections = computed(() => todayOperationsOverviewSections(props.card))
const todayOverviewNotes = computed(() => todayOperationsOverviewNotes(props.card))
const todayOverviewScopeNote = computed(() => todayOperationsOverviewScopeNote(props.card))
const isRegisteredReport = computed(() => (
  isTodayOperationsOverview.value
  || isDailyProductionReport.value
  || isInventoryLevelTrend.value
  || isQualityAssayTrend.value
  || isQualityMetricTrend.value
  || isProductionInputOutputFlow.value
  || isPalletTaskCycleReport.value
))
const reportComparison = computed(() => registeredReportComparison(props.card))
const reportRunId = computed(() => (
  todayOverviewSummary.value?.reportRunId
  || dailyReportSummary.value?.reportRunId
  || inventoryTrendSummary.value?.reportRunId
  || qualityTrendSummary.value?.reportRunId
  || metricTrendSummary.value?.reportRunId
  || productionFlowReportSummary.value?.reportRunId
  || taskCycleReportSummary.value?.reportRunId
  || ''
))
const expanded = ref(true)
const collapsedTaskGroups = ref(new Set(taskGroups(props.card).map(group => group.key)))
const selectedTaskKeys = ref(new Set())

const isTaskGroupExpanded = (group) => !collapsedTaskGroups.value.has(group.key)

const toggleTaskGroup = (group) => {
  const next = new Set(collapsedTaskGroups.value)
  if (next.has(group.key)) next.delete(group.key)
  else next.add(group.key)
  collapsedTaskGroups.value = next
}

const selectableGroupRecords = (group) => group.records.filter(record => record.selectable)

const selectedGroupRecords = (group) => group.records.filter(
  record => record.selectable && selectedTaskKeys.value.has(record.selectionKey)
)

const isTaskSelected = (record) => selectedTaskKeys.value.has(record.selectionKey)

const taskGroupSummary = (group) => taskGroupStatusSummary(group)

const toggleTaskSelection = (record, selected) => {
  if (!record.selectable) return
  const next = new Set(selectedTaskKeys.value)
  if (selected) next.add(record.selectionKey)
  else next.delete(record.selectionKey)
  selectedTaskKeys.value = next
}

const toggleTaskRow = (record) => {
  if (!record.selectable) return
  toggleTaskSelection(record, !isTaskSelected(record))
}

const isTaskGroupSelected = (group) => {
  const selectable = selectableGroupRecords(group)
  return selectable.length > 0 && selectable.every(record => isTaskSelected(record))
}

const isTaskGroupIndeterminate = (group) => {
  const selectable = selectableGroupRecords(group)
  const selectedCount = selectable.filter(record => isTaskSelected(record)).length
  return selectedCount > 0 && selectedCount < selectable.length
}

const toggleTaskGroupSelection = (group, selected) => {
  const next = new Set(selectedTaskKeys.value)
  selectableGroupRecords(group).forEach(record => {
    if (selected) next.add(record.selectionKey)
    else next.delete(record.selectionKey)
  })
  selectedTaskKeys.value = next
}

const openTaskBatch = (group) => {
  const selected = selectedGroupRecords(group)
  if (!group.batchAction || !selected.length) return
  emit('card-action', {
    actionKind: 'open_task_batch',
    batchAction: group.batchAction,
    taskGroupLabel: group.label,
    palletCodes: selected.map(record => record.palletCode).filter(Boolean)
  })
}

const inferActionProductName = (field) => {
  const source = field?.actionProductName || field?.productName || field?.label || field?.name || ''
  return String(source)
    .replace(/^\s*\d+\.\s*/, '')
    .replace(/\s+\d+(?:\.\d+)?\s*kg\/件.*$/i, '')
    .replace(/\s+\d+\s*件\/板.*$/i, '')
    .trim()
}

const openCreateAssay = (field) => {
  if (field?.actionKind !== 'create_assay') return
  emit('card-action', {
    actionKind: field.actionKind,
    productName: inferActionProductName(field),
    sampleDate: field.actionSampleDate || ''
  })
}

const exportRegisteredReport = () => {
  if (!reportRunId.value) return
  emit('card-action', {
    actionKind: 'export_report_run',
    reportRunId: reportRunId.value
  })
}
</script>

<template>
  <div class="business-card" :class="{ 'knowledge-card': isKnowledge, 'distribution-card': isDistribution, 'assay-card': isAssay, 'task-card': isTask, 'production-card': isProduction, 'today-operations-overview-card': isTodayOperationsOverview, 'daily-production-report-card': isDailyProductionReport, 'inventory-level-trend-card': isInventoryLevelTrend, 'quality-assay-trend-card': isQualityAssayTrend, 'quality-metric-trend-card': isQualityMetricTrend, 'production-input-output-flow-card': isProductionInputOutputFlow, 'pallet-task-cycle-report-card': isPalletTaskCycleReport, 'pallet-card': isPallet, 'inventory-quality-card': isInventoryQuality }">
    <div class="business-card-head" :class="{ 'task-card-head': isTask && !isTaskDetail }">
      <span class="business-card-icon">
        <el-icon><DataAnalysis /></el-icon>
      </span>
      <span class="business-card-title">
        {{ isKnowledge ? (knowledge?.title || '现行资料引用') : (isTask && !isTaskDetail ? '任务状态' : (card.title || '查询结果')) }}
      </span>
      <span v-if="isTask && !isTaskDetail && taskSummary.updated" class="task-updated-badge">已更新</span>
      <div v-if="isTask && !isTaskDetail" class="task-status-summary" aria-label="当前任务状态汇总">
        <span class="task-summary-total">共 {{ taskSummary.totalCount }} 条</span>
        <span v-if="taskSummary.pendingCount" class="task-summary-pending">待处理 {{ taskSummary.pendingCount }}</span>
        <span v-if="taskSummary.confirmedCount" class="task-summary-confirmed">已确认 {{ taskSummary.confirmedCount }}</span>
        <span v-if="taskSummary.otherCount" class="task-summary-other">其他 {{ taskSummary.otherCount }}</span>
      </div>
      <button
        v-if="isRegisteredReport && reportRunId"
        type="button"
        class="business-card-export"
        aria-label="导出这份报表"
        title="导出 XLSX"
        @click.stop="exportRegisteredReport"
      >
        导出
      </button>
      <button
        v-if="isAssay || isTask || isProduction || isTodayOperationsOverview || isDailyProductionReport || isInventoryLevelTrend || isQualityAssayTrend || isQualityMetricTrend || isProductionInputOutputFlow || isPalletTaskCycleReport || isPallet || isInventoryQuality"
        type="button"
        class="business-card-toggle"
        :aria-expanded="expanded"
        :aria-label="expanded ? '收起结果卡片' : '展开结果卡片'"
        :title="expanded ? '收起' : '展开'"
        @click="expanded = !expanded"
      >
        <el-icon><ArrowUp v-if="expanded" /><ArrowDown v-else /></el-icon>
      </button>
    </div>
    <div v-if="isTodayOperationsOverview" class="today-operations-body">
      <template v-if="expanded">
        <div v-if="todayOverviewSummary" class="today-operations-summary">
          <strong>{{ todayOverviewSummary.value }}</strong>
          <span>{{ todayOverviewSummary.dateRangeLabel }}</span>
        </div>
        <div class="today-operations-grid">
          <section
            v-for="section in todayOverviewSections"
            :key="section.key"
            class="today-operations-section"
            :class="`today-operations-section-${section.key}`"
          >
            <span>{{ section.label }}</span>
            <strong>{{ section.value }}</strong>
            <ul>
              <li v-for="detail in section.details" :key="detail">{{ detail }}</li>
            </ul>
          </section>
        </div>
        <div v-if="todayOverviewNotes.length" class="today-operations-notes">
          <div v-for="(note, index) in todayOverviewNotes" :key="`${note.value}-${index}`">
            {{ note.value }}
          </div>
        </div>
        <div v-if="todayOverviewScopeNote" class="today-operations-scope">
          <strong>{{ todayOverviewScopeNote.label }}</strong>
          <span>{{ todayOverviewScopeNote.value }}</span>
        </div>
      </template>
    </div>
    <div v-else-if="isAssay" class="assay-card-body">
      <template v-if="expanded">
        <div v-if="isAssayReport && reportSummary" class="assay-summary">
          <div class="assay-summary-main">
            <span>{{ reportSummary.sampleDate }}</span>
            <strong :class="`assay-result-${assayResultTone(reportSummary.judgeLabel)}`">
              {{ reportSummary.judgeLabel }}
            </strong>
          </div>
          <div class="assay-summary-standard">
            <span>采用标准</span>
            <strong>{{ reportSummary.standardLabel }}</strong>
          </div>
          <p v-if="reportSummary.judgeExplanation">{{ reportSummary.judgeExplanation }}</p>
        </div>

        <div v-if="isAssayReport && reportMetrics.length" class="assay-metric-list">
          <div class="assay-metric-head" aria-hidden="true">
            <span>指标</span><span>实测值</span><span>对应标准</span><span>结果</span>
          </div>
          <div
            v-for="(metric, metricIndex) in reportMetrics"
            :key="`${metric.metricName || metricIndex}-${metric.actualValueText || metricIndex}`"
            class="assay-metric-row"
          >
            <strong>{{ metric.metricName }}</strong>
            <span data-label="实测值">{{ metric.actualValueText }}</span>
            <span data-label="对应标准">{{ metric.standardRangeText }}</span>
            <span
              data-label="结果"
              class="assay-result"
              :class="`assay-result-${assayResultTone(metric.resultLabel)}`"
            >
              {{ metric.resultLabel }}
            </span>
            <small v-if="metric.reason">{{ metric.reason }}</small>
          </div>
        </div>

        <div v-if="isAssayHistory" class="assay-history-list">
          <div
            v-for="(record, recordIndex) in historyRecords"
            :key="`${record.sampleDate || recordIndex}-${record.productLabel || recordIndex}`"
            class="assay-history-row"
          >
            <div class="assay-history-main">
              <strong>{{ record.sampleDate }}</strong>
              <span :class="`assay-result-${assayResultTone(record.judgeLabel)}`">{{ record.judgeLabel }}</span>
            </div>
            <div>{{ record.productLabel }}</div>
            <div class="assay-history-meta">
              <span>标准：{{ record.standardLabel }}</span>
              <span v-if="record.failedMetricText">异常指标：{{ record.failedMetricText }}</span>
              <span v-if="record.testerLabel">化验员：{{ record.testerLabel }}</span>
            </div>
          </div>
        </div>

        <div v-if="reportNotes.length" class="assay-notes">
          <div v-for="(note, noteIndex) in reportNotes" :key="`${note.value}-${noteIndex}`">
            {{ note.value }}
          </div>
        </div>
      </template>
    </div>
    <div v-else-if="isTask" class="task-card-body">
      <div v-if="expanded" class="task-list">
        <section
          v-for="group in groupedTasks"
          :key="group.key"
          class="task-group"
          :class="{ 'task-group-detail': isTaskDetail }"
        >
          <div v-if="!isTaskDetail" class="task-group-head">
            <button
              type="button"
              class="task-group-disclosure"
              :aria-expanded="isTaskGroupExpanded(group)"
              :aria-label="`${isTaskGroupExpanded(group) ? '收起' : '展开'}${group.label}`"
              @click="toggleTaskGroup(group)"
            >
              <span class="task-group-title">
                <strong>{{ group.label }}</strong>
                <span>{{ taskGroupSummary(group).totalCount }} 条</span>
              </span>
              <span class="task-group-statuses">
                <span v-if="taskGroupSummary(group).pendingCount">待处理 {{ taskGroupSummary(group).pendingCount }}</span>
                <span v-if="taskGroupSummary(group).confirmedCount">已确认 {{ taskGroupSummary(group).confirmedCount }}</span>
              </span>
              <el-icon><ArrowUp v-if="isTaskGroupExpanded(group)" /><ArrowDown v-else /></el-icon>
            </button>
            <div v-if="selectableGroupRecords(group).length" class="task-group-actions" @click.stop>
              <el-checkbox
                :model-value="isTaskGroupSelected(group)"
                :indeterminate="isTaskGroupIndeterminate(group)"
                @change="toggleTaskGroupSelection(group, $event)"
              >
                全选可处理项（{{ taskGroupSummary(group).selectableCount }}）
              </el-checkbox>
            </div>
          </div>

          <div v-show="isTaskDetail || isTaskGroupExpanded(group)" class="task-group-records">
            <article
              v-for="(task, taskIndex) in group.records"
              :key="task.selectionKey"
              class="task-row"
              :class="{
                'is-selectable': task.selectable,
                'is-selected': isTaskSelected(task),
                'is-completed': !task.selectable && taskStatusTone(task.taskStatusLabel) === 'success'
              }"
              @click="toggleTaskRow(task)"
            >
              <div class="task-row-head">
                <el-checkbox
                  v-if="!isTaskDetail && task.selectable"
                  class="task-row-checkbox"
                  :aria-label="`选择托盘 ${task.palletCode}`"
                  :model-value="isTaskSelected(task)"
                  @click.stop
                  @change="toggleTaskSelection(task, $event)"
                />
                <div class="task-row-copy">
                  <strong>{{ task.label || `${taskIndex + 1}. ${task.taskTypeLabel}任务` }}</strong>
                  <span>{{ task.productLabel }}</span>
                </div>
                <span class="task-status" :class="`task-status-${taskStatusTone(task.taskStatusLabel)}`">
                  {{ task.taskStatusLabel }}
                </span>
              </div>
              <div class="task-meta">
                <span>托盘：{{ task.palletCode }}</span>
                <span v-if="task.targetLocationLabel">目标：{{ task.targetLocationLabel }}</span>
                <span v-if="task.productionDate">生产日期：{{ task.productionDate }}</span>
                <span v-if="task.totalWeightText">重量：{{ task.totalWeightText }}</span>
              </div>
              <dl v-if="isTaskDetail" class="task-detail-grid">
                <template v-for="([label, value], detailIndex) in taskDetailEntries(task)" :key="`${label}-${detailIndex}`">
                  <dt>{{ label }}</dt>
                  <dd>{{ value }}</dd>
                </template>
              </dl>
            </article>
          </div>

          <div
            v-if="!isTaskDetail && group.batchAction && selectableGroupRecords(group).length"
            class="task-group-footer"
          >
            <span>已选择 {{ selectedGroupRecords(group).length }} / {{ selectableGroupRecords(group).length }} 条</span>
            <el-button
              type="primary"
              size="small"
              :disabled="!selectedGroupRecords(group).length"
              @click="openTaskBatch(group)"
            >
              {{ selectedGroupRecords(group).length ? `处理所选 ${selectedGroupRecords(group).length} 条` : '选择任务后处理' }}
            </el-button>
          </div>
        </section>
      </div>
    </div>
    <div v-else-if="isDailyProductionReport" class="daily-production-report-body">
      <template v-if="expanded">
        <section v-if="dailyReportSummary" class="daily-report-summary">
          <div class="daily-report-scope">
            <div>
              <span>统计日期</span>
              <strong>{{ dailyReportSummary.dateRangeLabel || dailyReportSummary.label }}</strong>
            </div>
            <div>
              <span>产品范围</span>
              <strong>{{ dailyReportSummary.productScopeLabel || '全部产品' }}</strong>
            </div>
          </div>

          <div v-if="dailyReportSummary.isEmpty" class="daily-report-empty">
            当前范围内没有已登记且未取消的生产产出记录。
          </div>
          <div v-else class="daily-report-kpis">
            <div class="daily-report-kpi daily-report-kpi-primary">
              <span>登记产出重量</span>
              <strong>{{ dailyReportSummary.totalWeightKg || 0 }} kg</strong>
            </div>
            <div class="daily-report-kpi">
              <span>生产订单</span>
              <strong>{{ dailyReportSummary.productionOrderCount || 0 }} 个</strong>
            </div>
            <div class="daily-report-kpi">
              <span>产出记录</span>
              <strong>{{ dailyReportSummary.outputRecordCount || 0 }} 条</strong>
            </div>
            <div class="daily-report-kpi">
              <span>板件数量</span>
              <strong>{{ productionQuantityText(dailyReportSummary) }}</strong>
            </div>
          </div>

          <p v-if="qrProgressSummary(dailyReportSummary)" class="daily-report-qr-note">
            {{ qrProgressSummary(dailyReportSummary) }}
          </p>
          <p v-if="reportDataTimeText(dailyReportSummary)" class="daily-report-data-time">
            数据更新至 {{ reportDataTimeText(dailyReportSummary) }}
          </p>
        </section>

        <RegisteredReportTrendChart :card="card" />

        <section v-if="dailyReportProducts.length" class="daily-report-section">
          <h4>按产品构成</h4>
          <article
            v-for="(product, productIndex) in dailyReportProducts"
            :key="`${product.productName || product.label}-${productIndex}`"
            class="daily-report-row"
          >
            <div class="daily-report-row-head">
              <strong>{{ product.productName || product.label || '产品未标明' }}</strong>
              <span>{{ product.totalWeightKg || 0 }} kg</span>
            </div>
            <div class="daily-report-row-meta">
              <span>{{ product.productionOrderCount || 0 }} 个订单</span>
              <span>{{ product.outputRecordCount || 0 }} 条产出</span>
              <span>{{ productionQuantityText(product) }}</span>
            </div>
          </article>
        </section>

        <section v-if="dailyReportPoints.length > 1" class="daily-report-section">
          <h4>每日趋势明细</h4>
          <div class="daily-report-point-list">
            <div
              v-for="(point, pointIndex) in dailyReportPoints"
              :key="`${point.businessDate || point.label}-${pointIndex}`"
              class="daily-report-point"
            >
              <span>{{ point.businessDate || point.label }}</span>
              <strong>{{ point.totalWeightKg || 0 }} kg</strong>
              <small>{{ point.productionOrderCount || 0 }} 个订单 · {{ productionQuantityText(point) }}</small>
            </div>
          </div>
        </section>

        <section v-if="dailyReportQualityNotes.length" class="daily-report-quality">
          <strong>数据完整性提示</strong>
          <ul>
            <li v-for="(note, noteIndex) in dailyReportQualityNotes" :key="`${note}-${noteIndex}`">
              {{ note }}
            </li>
          </ul>
        </section>

        <p class="daily-report-boundary">
          本卡片统计已登记且未取消的生产产出；二维码绑定和入库进度单独展示，不等同于实际产量。
        </p>
      </template>
    </div>
    <div v-else-if="isInventoryLevelTrend" class="daily-production-report-body inventory-level-trend-body">
      <template v-if="expanded">
        <section v-if="inventoryTrendSummary" class="daily-report-summary">
          <div
            class="inventory-trend-source-banner"
            :class="{ 'inventory-trend-source-simulation': inventoryTrendSummary.simulationData }"
          >
            <strong>{{ inventoryTrendSummary.dataSourceLabel || '库存趋势数据' }}</strong>
            <span v-if="inventoryTrendSummary.simulationData">仅用于本地验收，不是正式日终快照</span>
            <span v-else>连续日终快照已通过库存守恒对账</span>
          </div>

          <div class="daily-report-scope">
            <div>
              <span>统计日期</span>
              <strong>{{ inventoryTrendSummary.dateRangeLabel || inventoryTrendSummary.label }}</strong>
            </div>
            <div>
              <span>产品范围</span>
              <strong>{{ inventoryTrendSummary.productScopeLabel || '全部产品' }}</strong>
            </div>
          </div>

          <div v-if="inventoryTrendSummary.isEmpty" class="daily-report-empty">
            当前范围内没有可用于库存趋势展示的数据。
          </div>
          <div v-else class="daily-report-kpis">
            <div class="daily-report-kpi">
              <span>期初库存</span>
              <strong>{{ inventoryTrendSummary.openingPieces || 0 }} 件</strong>
              <small>{{ inventoryWeightText(inventoryTrendSummary.openingWeightKg) }}</small>
            </div>
            <div class="daily-report-kpi daily-report-kpi-primary">
              <span>期末库存</span>
              <strong>{{ inventoryTrendSummary.closingPieces || 0 }} 件</strong>
              <small>{{ inventoryWeightText(inventoryTrendSummary.closingWeightKg) }}</small>
            </div>
            <div
              class="daily-report-kpi inventory-trend-change-kpi"
              :class="`inventory-trend-${inventoryTrendDirection(inventoryTrendSummary.netChangePieces)}`"
            >
              <span>净变化</span>
              <strong>{{ signedInventoryChangeText(inventoryTrendSummary.netChangePieces) }}</strong>
              <small>{{ signedInventoryChangeText(inventoryTrendSummary.netChangeWeightKg, 'kg') }}</small>
            </div>
            <div class="daily-report-kpi">
              <span>观察天数</span>
              <strong>{{ inventoryTrendSummary.observationDayCount || 0 }} 天</strong>
              <small>
                增 {{ inventoryTrendSummary.increaseDayCount || 0 }} ·
                减 {{ inventoryTrendSummary.decreaseDayCount || 0 }} ·
                持平 {{ inventoryTrendSummary.unchangedDayCount || 0 }}
              </small>
            </div>
          </div>

          <p v-if="reportDataTimeText(inventoryTrendSummary)" class="daily-report-data-time">
            数据计算于 {{ reportDataTimeText(inventoryTrendSummary) }}
          </p>
        </section>

        <RegisteredReportTrendChart :card="card" />

        <section v-if="inventoryTrendPoints.length" class="daily-report-section">
          <h4>每日库存水平</h4>
          <article
            v-for="(point, pointIndex) in inventoryTrendPoints"
            :key="`${point.businessDate || point.label}-${pointIndex}`"
            class="daily-report-row inventory-trend-daily-row"
          >
            <div class="daily-report-row-head">
              <strong>{{ point.businessDate || point.label }}</strong>
              <span>{{ point.totalPieces || 0 }} 件</span>
            </div>
            <div class="daily-report-row-meta">
              <span>{{ inventoryWeightText(point.totalWeightKg) }}</span>
              <span :class="`inventory-trend-${inventoryTrendDirection(point.pieceChange)}`">
                较前一日 {{ signedInventoryChangeText(point.pieceChange) }}
              </span>
              <span v-if="point.movementRecordCount">{{ point.movementRecordCount }} 条登记流水</span>
            </div>
          </article>
        </section>

        <section v-if="inventoryTrendProducts.length" class="daily-report-section">
          <h4>按产品变化</h4>
          <article
            v-for="(product, productIndex) in inventoryTrendProducts"
            :key="`${product.productName || product.label}-${productIndex}`"
            class="daily-report-row"
          >
            <div class="daily-report-row-head">
              <strong>{{ product.productName || product.label || '产品未标明' }}</strong>
              <span :class="`inventory-trend-${inventoryTrendDirection(product.netChangePieces)}`">
                {{ signedInventoryChangeText(product.netChangePieces) }}
              </span>
            </div>
            <div class="daily-report-row-meta">
              <span>期初 {{ product.openingPieces || 0 }} 件</span>
              <span>期末 {{ product.closingPieces || 0 }} 件</span>
              <span>{{ signedInventoryChangeText(product.netChangeWeightKg, 'kg') }}</span>
            </div>
          </article>
        </section>

        <section v-if="inventoryTrendQualityNotes.length" class="daily-report-quality">
          <strong>{{ inventoryTrendSummary?.simulationData ? '模拟数据说明' : '数据完整性提示' }}</strong>
          <ul>
            <li v-for="(note, noteIndex) in inventoryTrendQualityNotes" :key="`${note}-${noteIndex}`">
              {{ note }}
            </li>
          </ul>
        </section>

        <p class="daily-report-boundary inventory-trend-boundary">
          {{ inventoryTrendBoundary || '库存趋势表示已登记库存水平变化，不自动解释原因，也不等同于需求预测。' }}
        </p>
      </template>
    </div>
    <div v-else-if="isProductionInputOutputFlow" class="daily-production-report-body production-flow-report-body">
      <template v-if="expanded">
        <section v-if="productionFlowReportSummary" class="daily-report-summary">
          <div class="daily-report-scope">
            <div>
              <span>统计日期</span>
              <strong>{{ productionFlowReportSummary.dateRangeLabel || productionFlowReportSummary.label }}</strong>
            </div>
            <div>
              <span>产品归属范围</span>
              <strong>{{ productionFlowReportSummary.productScopeLabel || '全部产品' }}</strong>
            </div>
          </div>

          <div v-if="productionFlowReportSummary.isEmpty" class="daily-report-empty">
            当前范围内没有实际领料、稳定登记产出或生产订单记录。
          </div>
          <div v-else class="production-flow-kpis">
            <article class="production-flow-kpi production-flow-kpi-input">
              <span>实际领料（按领料时间）</span>
              <strong>{{ productionFlowWeightText(productionFlowReportSummary.materialInputWeightKg) }}</strong>
              <small>
                {{ productionFlowReportSummary.materialInputRecordCount || 0 }} 条记录
                · {{ productionFlowReportSummary.materialInputOrderCount || 0 }} 个订单
              </small>
            </article>
            <article class="production-flow-kpi production-flow-kpi-output">
              <span>稳定登记产出（按生产日期）</span>
              <strong>{{ productionFlowWeightText(productionFlowReportSummary.stableOutputWeightKg) }}</strong>
              <small>
                {{ productionFlowReportSummary.stableOutputRecordCount || 0 }} 条记录
                · {{ productionFlowReportSummary.stableOutputOrderCount || 0 }} 个订单
              </small>
            </article>
          </div>

          <p class="production-flow-coverage">
            {{ productionFlowCoverageText(productionFlowReportSummary) }}
          </p>
          <p v-if="reportDataTimeText(productionFlowReportSummary)" class="daily-report-data-time">
            数据更新至 {{ reportDataTimeText(productionFlowReportSummary) }}
          </p>
        </section>

        <RegisteredReportTrendChart :card="card" />

        <section v-if="productionFlowReportPoints.length" class="daily-report-section">
          <h4>有记录日期的两条独立序列</h4>
          <div class="production-flow-daily-head" aria-hidden="true">
            <span>日期</span>
            <span>实际领料</span>
            <span>稳定登记产出</span>
          </div>
          <article
            v-for="(point, pointIndex) in productionFlowReportPoints"
            :key="`${point.businessDate || point.label}-${pointIndex}`"
            class="production-flow-daily-row"
          >
            <strong>{{ point.businessDate || point.label }}</strong>
            <div>
              <span>按领料时间</span>
              <b>{{ productionFlowWeightText(point.materialInputWeightKg) }}</b>
              <small>{{ point.materialInputRecordCount || 0 }} 条</small>
            </div>
            <div>
              <span>按生产日期</span>
              <b>{{ productionFlowWeightText(point.stableOutputWeightKg) }}</b>
              <small>{{ point.stableOutputRecordCount || 0 }} 条</small>
            </div>
          </article>
        </section>

        <section v-if="productionFlowReportOrders.length" class="daily-report-section">
          <h4>生产订单归属事实</h4>
          <article
            v-for="(order, orderIndex) in productionFlowReportOrders"
            :key="`${order.orderNo || order.label}-${orderIndex}`"
            class="daily-report-row production-flow-order-row"
          >
            <div class="daily-report-row-head">
              <strong>{{ order.orderNo || order.label || '订单未标明' }}</strong>
              <span>{{ order.completenessLabel || order.value || '完整性未标明' }}</span>
            </div>
            <div class="daily-report-row-meta">
              <span>{{ order.orderTypeLabel || '订单类型未标明' }}</span>
              <span>{{ order.orderStatusLabel || '订单状态未标明' }}</span>
              <span>生产日期 {{ order.productionDate || '未标明' }}</span>
            </div>
            <div class="production-flow-order-sources">
              <div>
                <span>{{ order.inputSourceLabel || '订单输入' }}</span>
                <strong>{{ productionFlowWeightText(order.inputWeightKg) }}</strong>
                <small>{{ order.inputRecordCount || 0 }} 条记录</small>
              </div>
              <div>
                <span>稳定登记产出</span>
                <strong>{{ productionFlowWeightText(order.stableOutputWeightKg) }}</strong>
                <small>{{ order.stableOutputRecordCount || 0 }} 条记录</small>
              </div>
            </div>
            <p v-if="order.outputProductNames">产出产品：{{ order.outputProductNames }}</p>
          </article>
        </section>

        <section v-if="productionFlowReportNotes.length" class="daily-report-quality">
          <strong>数据完整性提示</strong>
          <ul>
            <li v-for="(note, noteIndex) in productionFlowReportNotes" :key="`${note}-${noteIndex}`">
              {{ note }}
            </li>
          </ul>
        </section>

        <p class="daily-report-boundary production-flow-boundary">
          {{ productionFlowReportScopeNote || '领料与稳定登记产出是两条独立序列，不计算产耗比、收率或损耗率。' }}
        </p>
      </template>
    </div>
    <div v-else-if="isPalletTaskCycleReport" class="daily-production-report-body pallet-task-cycle-report-body">
      <template v-if="expanded">
        <section v-if="taskCycleReportSummary" class="daily-report-summary">
          <div class="daily-report-scope">
            <div>
              <span>任务创建日期</span>
              <strong>{{ taskCycleReportSummary.dateRangeLabel || taskCycleReportSummary.label }}</strong>
            </div>
            <div>
              <span>任务类型</span>
              <strong>{{ taskCycleReportSummary.taskScopeLabel || '全部托盘任务' }}</strong>
            </div>
            <div>
              <span>产品范围</span>
              <strong>{{ taskCycleReportSummary.productScopeLabel || '全部产品' }}</strong>
            </div>
          </div>

          <div v-if="taskCycleReportSummary.isEmpty" class="daily-report-empty">
            当前范围内没有系统已登记的托盘任务。
          </div>
          <div v-else class="daily-report-kpis">
            <div class="daily-report-kpi daily-report-kpi-primary">
              <span>任务总数</span>
              <strong>{{ taskCycleReportSummary.cohortTaskCount || 0 }} 条</strong>
            </div>
            <div class="daily-report-kpi">
              <span>已完成</span>
              <strong>{{ taskCycleReportSummary.completedTaskCount || 0 }} 条</strong>
            </div>
            <div class="daily-report-kpi">
              <span>进行中</span>
              <strong>{{ taskCycleReportSummary.inProgressTaskCount || 0 }} 条</strong>
            </div>
            <div class="daily-report-kpi">
              <span>已取消</span>
              <strong>{{ taskCycleReportSummary.canceledTaskCount || 0 }} 条</strong>
            </div>
          </div>

          <div v-if="!taskCycleReportSummary.isEmpty" class="task-cycle-duration-grid">
            <div>
              <span>完成耗时中位数</span>
              <strong>{{ taskDurationText(taskCycleReportSummary.medianDurationSeconds) }}</strong>
            </div>
            <div>
              <span>完成耗时 P90</span>
              <strong>{{ taskDurationText(taskCycleReportSummary.p90DurationSeconds) }}</strong>
            </div>
            <div>
              <span>最长进行中等待</span>
              <strong>{{ taskDurationText(taskCycleReportSummary.maximumWaitingSeconds) }}</strong>
            </div>
          </div>
          <p v-if="reportDataTimeText(taskCycleReportSummary)" class="daily-report-data-time">
            数据更新至 {{ reportDataTimeText(taskCycleReportSummary) }}
          </p>
        </section>

        <RegisteredReportTrendChart :card="card" />

        <section v-if="taskCycleReportTypes.length" class="daily-report-section">
          <h4>按任务类型</h4>
          <article
            v-for="(item, itemIndex) in taskCycleReportTypes"
            :key="`${item.taskTypeLabel || item.label}-${itemIndex}`"
            class="daily-report-row"
          >
            <div class="daily-report-row-head">
              <strong>{{ item.taskTypeLabel || item.label }}</strong>
              <span>{{ item.taskCount || 0 }} 条</span>
            </div>
            <div class="daily-report-row-meta">
              <span>完成 {{ item.completedTaskCount || 0 }}</span>
              <span>进行中 {{ item.inProgressTaskCount || 0 }}</span>
              <span>取消 {{ item.canceledTaskCount || 0 }}</span>
            </div>
            <div class="task-cycle-duration-inline">
              <span>完成耗时中位数 {{ taskDurationText(item.medianDurationSeconds) }}</span>
              <span>P90 {{ taskDurationText(item.p90DurationSeconds) }}</span>
            </div>
          </article>
        </section>

        <section v-if="taskCycleReportPoints.length" class="daily-report-section">
          <h4>有任务日期</h4>
          <article
            v-for="(point, pointIndex) in taskCycleReportPoints"
            :key="`${point.businessDate || point.label}-${pointIndex}`"
            class="daily-report-row"
          >
            <div class="daily-report-row-head">
              <strong>{{ point.businessDate || point.label }}</strong>
              <span>{{ point.taskCount || 0 }} 条</span>
            </div>
            <div class="daily-report-row-meta">
              <span>完成 {{ point.completedTaskCount || 0 }}</span>
              <span>进行中 {{ point.inProgressTaskCount || 0 }}</span>
              <span>取消 {{ point.canceledTaskCount || 0 }}</span>
            </div>
            <p>完成耗时中位数：{{ taskDurationText(point.medianDurationSeconds) }}</p>
          </article>
        </section>

        <section v-if="taskCyclePendingRows.length" class="daily-report-section">
          <h4>等待时间较长的进行中任务</h4>
          <article
            v-for="(item, itemIndex) in taskCyclePendingRows"
            :key="`${item.palletCode || item.label}-${itemIndex}`"
            class="daily-report-row task-cycle-pending-row"
          >
            <div class="daily-report-row-head">
              <strong>{{ item.palletCode || item.label }}</strong>
              <span>已等待 {{ taskDurationText(item.waitingSeconds) }}</span>
            </div>
            <div class="daily-report-row-meta">
              <span>{{ item.taskTypeLabel }}</span>
              <span>{{ item.productName }}</span>
            </div>
            <p>创建于 {{ String(item.createdAt || '未标明').replace('T', ' ').slice(0, 16) }} · {{ item.targetWarehouseName || '尚未登记目标库位' }}</p>
          </article>
        </section>

        <section v-if="taskCycleReportNotes.length" class="daily-report-quality">
          <strong>数据完整性提示</strong>
          <ul>
            <li v-for="(note, noteIndex) in taskCycleReportNotes" :key="`${note}-${noteIndex}`">
              {{ note }}
            </li>
          </ul>
        </section>

        <p class="daily-report-boundary production-flow-boundary">
          {{ taskCycleReportScopeNote || '这里只统计系统已登记托盘任务；完成耗时与进行中等待分开，不代表 SLA、员工绩效或现场全部流程。' }}
        </p>
      </template>
    </div>
    <div v-else-if="isQualityAssayTrend" class="daily-production-report-body quality-assay-trend-body">
      <template v-if="expanded">
        <section v-if="qualityTrendSummary" class="daily-report-summary">
          <div class="daily-report-scope">
            <div>
              <span>统计日期</span>
              <strong>{{ qualityTrendSummary.dateRangeLabel || qualityTrendSummary.label }}</strong>
            </div>
            <div>
              <span>产品范围</span>
              <strong>{{ qualityTrendSummary.productScopeLabel || '全部产品' }}</strong>
            </div>
          </div>

          <div v-if="qualityTrendSummary.isEmpty" class="daily-report-empty">
            当前范围内没有已登记的化验记录。
          </div>
          <div v-else class="daily-report-kpis">
            <div class="daily-report-kpi daily-report-kpi-primary">
              <span>化验记录</span>
              <strong>{{ qualityTrendSummary.assayRecordCount || 0 }} 条</strong>
            </div>
            <div class="daily-report-kpi">
              <span>明确判定合格率</span>
              <strong>{{ qualityPassRateText(qualityTrendSummary) }}</strong>
            </div>
            <div class="daily-report-kpi">
              <span>合格 / 不合格</span>
              <strong>{{ qualityTrendSummary.passCount || 0 }} / {{ qualityTrendSummary.failCount || 0 }}</strong>
            </div>
            <div class="daily-report-kpi">
              <span>无标准 / 多候选</span>
              <strong>{{ qualityTrendSummary.noStandardCount || 0 }} / {{ qualityTrendSummary.multipleCandidatesCount || 0 }}</strong>
            </div>
          </div>

          <p v-if="reportDataTimeText(qualityTrendSummary)" class="daily-report-data-time">
            数据更新至 {{ reportDataTimeText(qualityTrendSummary) }}
          </p>
        </section>

        <RegisteredReportTrendChart :card="card" />

        <section v-if="qualityTrendProducts.length" class="daily-report-section">
          <h4>按产品分布</h4>
          <article
            v-for="(product, productIndex) in qualityTrendProducts"
            :key="`${product.productName || product.label}-${productIndex}`"
            class="daily-report-row"
          >
            <div class="daily-report-row-head">
              <strong>{{ product.productName || product.label || '产品未标明' }}</strong>
              <span>{{ qualityPassRateText(product) }}</span>
            </div>
            <div class="daily-report-row-meta">
              <span>共 {{ product.assayRecordCount || 0 }} 条</span>
              <span>{{ qualityJudgementSummary(product) }}</span>
            </div>
          </article>
        </section>

        <section v-if="qualityTrendStandards.length" class="daily-report-section">
          <h4>采用标准版本</h4>
          <article
            v-for="(standard, standardIndex) in qualityTrendStandards"
            :key="`${standard.standardLabel || standard.label}-${standardIndex}`"
            class="daily-report-row"
          >
            <div class="daily-report-row-head">
              <strong>{{ standard.standardLabel || standard.label || '标准未标明' }}</strong>
              <span>{{ qualityPassRateText(standard) }}</span>
            </div>
            <div class="daily-report-row-meta">
              <span>共 {{ standard.assayRecordCount || 0 }} 条</span>
              <span>合格 {{ standard.passCount || 0 }} 条 · 不合格 {{ standard.failCount || 0 }} 条</span>
            </div>
          </article>
        </section>

        <section v-if="qualityTrendPoints.length > 1" class="daily-report-section">
          <h4>有记录的{{ qualityTrendSummary?.seriesGranularity === '月' ? '月份' : '日期' }}</h4>
          <div class="daily-report-point-list">
            <div
              v-for="(point, pointIndex) in qualityTrendPoints"
              :key="`${point.periodLabel || point.label}-${pointIndex}`"
              class="daily-report-point"
            >
              <span>{{ point.periodLabel || point.label }}</span>
              <strong>{{ qualityPassRateText(point) }}</strong>
              <small>{{ qualityJudgementSummary(point) }}</small>
            </div>
          </div>
        </section>

        <section v-if="qualityTrendNotes.length" class="daily-report-quality">
          <strong>数据完整性提示</strong>
          <ul>
            <li v-for="(note, noteIndex) in qualityTrendNotes" :key="`${note}-${noteIndex}`">
              {{ note }}
            </li>
          </ul>
        </section>

        <p class="daily-report-boundary">
          合格率只统计明确合格和不合格记录；无标准、标准多候选不进入分母。本卡片不推断历史缺化验批次。
        </p>
      </template>
    </div>
    <div v-else-if="isQualityMetricTrend" class="daily-production-report-body quality-metric-trend-body">
      <template v-if="expanded">
        <section v-if="metricTrendSummary" class="daily-report-summary">
          <div class="daily-report-scope">
            <div>
              <span>指标与日期</span>
              <strong>{{ metricTrendSummary.metricName }} · {{ metricTrendSummary.dateRangeLabel }}</strong>
            </div>
            <div>
              <span>产品范围</span>
              <strong>{{ metricTrendSummary.productScopeLabel || '全部产品' }}</strong>
            </div>
          </div>

          <div v-if="metricTrendSummary.isEmpty" class="daily-report-empty">
            当前范围内没有该指标的实测数据。
          </div>
          <div v-else class="daily-report-kpis">
            <div class="daily-report-kpi daily-report-kpi-primary">
              <span>实测样本</span>
              <strong>{{ metricTrendSummary.sampleCount || 0 }} 个</strong>
            </div>
            <div class="daily-report-kpi">
              <span>均值 / 中位数</span>
              <strong>{{ metricValueText(metricTrendSummary.averageValue, metricTrendSummary.unit) }} / {{ metricValueText(metricTrendSummary.medianValue, metricTrendSummary.unit) }}</strong>
            </div>
            <div class="daily-report-kpi">
              <span>最小值 / 最大值</span>
              <strong>{{ metricValueText(metricTrendSummary.minimumValue, metricTrendSummary.unit) }} / {{ metricValueText(metricTrendSummary.maximumValue, metricTrendSummary.unit) }}</strong>
            </div>
            <div class="daily-report-kpi">
              <span>历史标准下达标率</span>
              <strong>{{ metricWithinRateText(metricTrendSummary) }}</strong>
            </div>
          </div>

          <p v-if="!metricTrendSummary.isEmpty" class="daily-report-qr-note">
            P10 / P90：{{ metricValueText(metricTrendSummary.p10Value, metricTrendSummary.unit) }} / {{ metricValueText(metricTrendSummary.p90Value, metricTrendSummary.unit) }}
          </p>
          <p v-if="reportDataTimeText(metricTrendSummary)" class="daily-report-data-time">
            数据更新至 {{ reportDataTimeText(metricTrendSummary) }}
          </p>
        </section>

        <RegisteredReportTrendChart :card="card" />

        <section v-if="metricTrendProducts.length" class="daily-report-section">
          <h4>按产品统计</h4>
          <article
            v-for="(product, productIndex) in metricTrendProducts"
            :key="`${product.productName || product.label}-${productIndex}`"
            class="daily-report-row"
          >
            <div class="daily-report-row-head">
              <strong>{{ product.productName || product.label || '产品未标明' }}</strong>
              <span>{{ product.sampleCount || 0 }} 个样本</span>
            </div>
            <div class="daily-report-row-meta">
              <span>均值 {{ metricValueText(product.averageValue, metricTrendSummary?.unit) }}</span>
              <span>中位数 {{ metricValueText(product.medianValue, metricTrendSummary?.unit) }}</span>
              <span>范围 {{ metricValueText(product.minimumValue, metricTrendSummary?.unit) }} – {{ metricValueText(product.maximumValue, metricTrendSummary?.unit) }}</span>
            </div>
          </article>
        </section>

        <section v-if="metricTrendStandards.length" class="daily-report-section">
          <h4>历史采用标准</h4>
          <article
            v-for="(standard, standardIndex) in metricTrendStandards"
            :key="`${standard.standardLabel || standard.label}-${standardIndex}`"
            class="daily-report-row"
          >
            <div class="daily-report-row-head">
              <strong>{{ standard.standardLabel || standard.label || '历史标准' }}</strong>
              <span>{{ metricWithinRateText(standard) }}</span>
            </div>
            <div class="daily-report-row-meta">
              <span>标准范围 {{ standard.rangeLabel || '-' }}{{ standard.unit ? ` ${standard.unit}` : '' }}</span>
              <span>可比较 {{ standard.sampleCount || 0 }} 个</span>
              <span>达标 {{ standard.withinStandardCount || 0 }} · 超出 {{ standard.outOfStandardCount || 0 }}</span>
            </div>
          </article>
        </section>

        <section v-if="metricTrendPoints.length" class="daily-report-section">
          <h4>有样本的{{ metricTrendSummary?.seriesGranularity === '月' ? '月份' : '日期' }}</h4>
          <div class="daily-report-point-list">
            <div
              v-for="(point, pointIndex) in metricTrendPoints"
              :key="`${point.periodLabel || point.label}-${pointIndex}`"
              class="daily-report-point"
            >
              <span>{{ point.periodLabel || point.label }}</span>
              <strong>{{ metricValueText(point.averageValue, metricTrendSummary?.unit) }}</strong>
              <small>{{ point.sampleCount || 0 }} 个样本 · 中位数 {{ metricValueText(point.medianValue, metricTrendSummary?.unit) }}</small>
            </div>
          </div>
        </section>

        <section v-if="metricTrendNotes.length" class="daily-report-quality">
          <strong>数据完整性提示</strong>
          <ul>
            <li v-for="(note, noteIndex) in metricTrendNotes" :key="`${note}-${noteIndex}`">
              {{ note }}
            </li>
          </ul>
        </section>

        <p class="daily-report-boundary">
          原始统计包含所有有实测值的样本；达标率只比较历史标准中范围和单位可比的样本。样本较少时不判断改善、恶化或原因。
        </p>
      </template>
    </div>
    <div v-else-if="isProduction" class="production-card-body">
      <template v-if="expanded">
        <section v-if="isProductionOrder && orderSummary" class="production-summary">
          <div class="production-summary-head">
            <strong>{{ orderSummary.orderNo }}</strong>
            <span :class="`production-status-${productionStatusTone(orderSummary.statusLabel)}`">
              {{ orderSummary.statusLabel }}
            </span>
          </div>
          <dl class="production-summary-grid">
            <template
              v-for="([label, value], summaryIndex) in productionSummaryEntries(orderSummary)"
              :key="`${label}-${summaryIndex}`"
            >
              <dt>{{ label }}</dt>
              <dd>{{ value }}</dd>
            </template>
          </dl>
          <div class="production-progress-tags">
            <span>领料 {{ orderSummary.materialRecordCount || 0 }} 条</span>
            <span>产出 {{ orderSummary.outputRecordCount || 0 }} 条</span>
            <span v-if="qrProgressText(orderSummary)">{{ qrProgressText(orderSummary) }}</span>
          </div>
        </section>

        <section v-if="isBoilingBatch && batchSummary" class="production-summary">
          <div class="production-summary-head">
            <strong>{{ batchSummary.batchNo }}</strong>
            <span :class="`production-status-${productionStatusTone(batchSummary.statusLabel)}`">
              {{ batchSummary.statusLabel }}
            </span>
          </div>
          <dl class="production-summary-grid">
            <template
              v-for="([label, value], summaryIndex) in productionSummaryEntries(batchSummary, 'batch')"
              :key="`${label}-${summaryIndex}`"
            >
              <dt>{{ label }}</dt>
              <dd>{{ value }}</dd>
            </template>
          </dl>
        </section>

        <section v-if="isProductionOrder && boilingSourceRows.length" class="production-section">
          <h4>煮糖批次投入与预留</h4>
          <article
            v-for="(source, sourceIndex) in boilingSourceRows"
            :key="`${source.batchNo}-${sourceIndex}`"
            class="production-row"
          >
            <div class="production-row-head">
              <strong>{{ source.label || `煮糖批次 ${source.batchNo}` }}</strong>
              <span :class="`production-status-${productionStatusTone(source.statusLabel)}`">
                {{ source.statusLabel }}
              </span>
            </div>
            <div class="production-row-primary">{{ source.quantityText }}</div>
            <p v-if="source.statusLabel?.includes('预')" class="production-stage-note">
              当前为预留阶段，不能按实际领料或实际消耗计算。
            </p>
          </article>
        </section>

        <section v-if="outputRows.length" class="production-section">
          <h4>产出与实际入库去向</h4>
          <article v-for="(output, outputIndex) in outputRows" :key="`${output.productLabel}-${outputIndex}`" class="production-row">
            <div class="production-row-head">
              <strong>{{ output.label || output.productLabel }}</strong>
              <span :class="`production-status-${productionStatusTone(output.statusLabel)}`">{{ output.statusLabel }}</span>
            </div>
            <div class="production-row-primary">{{ output.quantityText }}</div>
            <div v-if="qrProgressText(output)" class="production-row-meta">{{ qrProgressText(output) }}</div>
            <div v-if="output.inboundDestinations?.length" class="production-destinations">
              <div
                v-for="(destination, destinationIndex) in output.inboundDestinations"
                :key="`${destination.warehouseName}-${destinationIndex}`"
                class="production-destination"
              >
                <div><strong>{{ destination.warehouseName }}</strong><span>已入库 {{ destination.inboundCodeCount }} 个码</span></div>
                <small v-if="destination.palletCodes?.length">托盘：{{ destination.palletCodes.join('、') }}</small>
              </div>
            </div>
            <p v-else class="production-empty-note">暂未确认实际入库去向</p>
          </article>
        </section>

        <section v-if="isProductionMaterial" class="production-section">
          <h4>实际领料记录</h4>
          <p v-if="!materialRows.length" class="production-empty-note">暂未查询到已登记的实际领料记录</p>
          <article v-for="(material, materialIndex) in materialRows" :key="`${material.palletCode}-${materialIndex}`" class="production-row">
            <div class="production-row-head">
              <strong>{{ material.label || material.productLabel }}</strong>
              <span :class="`production-status-${productionStatusTone(material.statusLabel)}`">{{ material.statusLabel }}</span>
            </div>
            <div class="production-row-primary">{{ material.quantityText }}</div>
            <div class="production-row-meta">
              <span>来源托盘：{{ material.palletCode }}</span>
              <span>来源库位：{{ material.sourceLocation }}</span>
              <span v-if="material.productionDate">生产日期：{{ material.productionDate }}</span>
              <span v-if="material.pickedSummary">领料：{{ material.pickedSummary }}</span>
            </div>
          </article>
        </section>

        <section v-if="batchUsageRows.length" class="production-section">
          <h4>关联生产订单</h4>
          <article v-for="(usage, usageIndex) in batchUsageRows" :key="`${usage.orderNo}-${usageIndex}`" class="production-row">
            <div class="production-row-head">
              <strong>{{ usage.label || `生产订单 ${usage.orderNo}` }}</strong>
              <span :class="`production-status-${productionStatusTone(usage.orderStatusLabel)}`">{{ usage.orderStatusLabel }}</span>
            </div>
            <div class="production-row-primary">本批次用量：{{ usage.quantityText }}</div>
            <div class="production-row-meta">
              <span>{{ usage.orderTypeLabel }}</span>
              <span>使用状态：{{ usage.statusLabel }}</span>
            </div>
          </article>
        </section>

        <section v-if="traceSteps.length" class="production-section production-trace">
          <h4>流转摘要</h4>
          <ol>
            <li v-for="(step, stepIndex) in traceSteps" :key="`${step.value}-${stepIndex}`">{{ step.value }}</li>
          </ol>
        </section>
      </template>
    </div>
    <div v-else-if="isPallet" class="pallet-card-body">
      <template v-if="expanded">
        <section v-if="isPalletStatus && palletSummary" class="pallet-summary">
          <div class="pallet-summary-head">
            <strong>{{ palletSummary.codeLabel }}</strong>
            <span :class="`pallet-status-${palletStatusTone(palletSummary.currentStatusLabel)}`">
              {{ palletSummary.currentStatusLabel }}
            </span>
          </div>
          <dl class="pallet-summary-grid">
            <template
              v-for="([label, value], summaryIndex) in palletSummaryEntries(palletSummary)"
              :key="`${label}-${summaryIndex}`"
            >
              <dt>{{ label }}</dt>
              <dd>{{ value }}</dd>
            </template>
          </dl>
        </section>

        <section v-if="isPalletFlow && palletHistorySummary" class="pallet-flow-summary">
          <strong>{{ palletHistorySummary.scopeLabel }}</strong>
          <span>共 {{ palletHistorySummary.total || 0 }} 条</span>
          <small v-if="palletHistorySummary.dateRangeLabel">{{ palletHistorySummary.dateRangeLabel }}</small>
        </section>

        <section v-if="palletEvents.length" class="pallet-timeline">
          <h4>{{ isPalletFlow ? '历史流转' : '最近流转' }}</h4>
          <ol>
            <li v-for="(event, eventIndex) in palletEvents" :key="`${event.time || eventIndex}-${event.eventLabel}`">
              <span class="pallet-timeline-dot" aria-hidden="true" />
              <div class="pallet-timeline-content">
                <div class="pallet-timeline-head">
                  <strong>{{ event.eventLabel }}</strong>
                  <time v-if="event.time">{{ event.time }}</time>
                </div>
                <div v-if="palletEventRoute(event)" class="pallet-timeline-route">{{ palletEventRoute(event) }}</div>
                <div class="pallet-timeline-meta">
                  <span v-if="event.productLabel">{{ event.productLabel }}</span>
                  <span v-if="event.operatorLabel">操作人：{{ event.operatorLabel }}</span>
                  <span v-if="event.cycleNo">第 {{ event.cycleNo }} 次流转</span>
                </div>
              </div>
            </li>
          </ol>
        </section>
        <p v-else-if="isPalletFlow" class="pallet-empty-note">当前范围内没有已登记的流转记录。</p>

        <section v-if="palletRiskRows.length" class="pallet-risk-list">
          <div v-for="(risk, riskIndex) in palletRiskRows" :key="`${risk.value}-${riskIndex}`">
            {{ risk.value }}
          </div>
        </section>
      </template>
    </div>
    <div v-else-if="isInventoryQuality" class="inventory-quality-body">
      <template v-if="expanded">
        <section v-if="qualitySummary" class="inventory-quality-summary">
          <strong>{{ qualitySummary.value }}</strong>
          <span>折合 {{ qualitySummary.totalEquivalentPieces || 0 }} 件</span>
          <span>总重量 {{ qualitySummary.totalWeightText || '0 kg' }}</span>
        </section>
        <section class="inventory-quality-list">
          <article
            v-for="(record, recordIndex) in qualityRecords"
            :key="`${record.productLabel}-${record.productionDate}-${record.warehouseLabel}-${recordIndex}`"
            class="inventory-quality-row"
          >
            <div class="inventory-quality-row-head">
              <strong>{{ record.label || record.productLabel }}</strong>
              <span :class="`inventory-quality-${inventoryQualityTone(record.value || record.judgeLabel)}`">
                {{ record.value || record.judgeLabel }}
              </span>
            </div>
            <div class="inventory-quality-meta">
              <span>生产日期：{{ record.productionDate }}</span>
              <span>库位：{{ record.warehouseLabel }}</span>
              <span>{{ record.stockText }}</span>
              <span v-if="record.totalWeightText">{{ record.totalWeightText }}</span>
            </div>
            <div v-if="record.standardLabel || record.failedMetricText || record.metricValueText" class="inventory-quality-detail">
              <span v-if="record.standardLabel">标准：{{ record.standardLabel }}</span>
              <span v-if="record.failedMetricText">未达标指标：{{ record.failedMetricText }}</span>
              <span v-if="record.metricValueText">{{ record.metricLabel }}：{{ record.metricValueText }}</span>
            </div>
          </article>
        </section>
      </template>
    </div>
    <div v-else-if="isDistribution" class="distribution-card-body">
      <div class="distribution-list">
        <div
          v-for="(field, fieldIndex) in rows"
          :key="`${field.label || fieldIndex}-${field.value || fieldIndex}`"
          class="distribution-row"
        >
          <div class="distribution-main">
            <span class="distribution-label">{{ field.label || field.name }}</span>
            <strong>{{ field.value }}</strong>
          </div>
          <div class="distribution-meta">
            <span v-if="field.percentage">{{ field.percentage }}</span>
            <span v-if="field.palletCount">{{ field.palletCount }}</span>
            <span v-if="field.latestInboundTime">{{ field.latestInboundTime }}</span>
          </div>
          <div v-if="field.riskText || field.actionKind" class="distribution-row-risk">
            <span v-if="field.riskText">{{ field.riskText }}</span>
            <el-button
              v-if="field.actionKind === 'create_assay'"
              size="small"
              type="warning"
              plain
              @click.stop="openCreateAssay(field)"
            >
              {{ field.actionLabel || '去补充' }}
            </el-button>
          </div>
        </div>
      </div>
      <div v-if="riskSummary" class="distribution-risk">
        {{ riskSummary }}
      </div>
    </div>
    <section v-else-if="isKnowledge && knowledge" class="knowledge-card-body" data-testid="knowledge-citation">
      <p class="knowledge-evidence-content">{{ knowledge.content }}</p>
      <div class="knowledge-citation-source">
        <span>现行资料来源</span>
        <strong>{{ knowledge.documentTitle }}</strong>
        <small v-if="knowledge.locationLabel">{{ knowledge.locationLabel }}</small>
      </div>
      <p class="knowledge-boundary-note">静态现行资料，不代表实时库存、批次质量或生产进度。</p>
    </section>
    <div v-else-if="!isKnowledge && card.fields?.length" class="business-card-fields">
      <div
        v-for="(field, fieldIndex) in card.fields"
        :key="`${field.label || field.name || fieldIndex}-${field.value || fieldIndex}`"
        class="business-card-field"
      >
        <span>{{ field.label || field.name }}</span>
        <strong>{{ field.value }}</strong>
      </div>
    </div>
    <section v-if="isRegisteredReport && expanded && reportComparison" class="registered-report-comparison">
      <div class="registered-report-comparison-head">
        <strong>{{ reportComparison.label }}</strong>
        <span>本期 {{ reportComparison.currentDateRangeLabel }} · 对比期 {{ reportComparison.comparisonDateRangeLabel }}</span>
      </div>
      <article
        v-for="metric in reportComparison.metrics"
        :key="metric.metricCode || metric.metricLabel"
        class="registered-report-comparison-row"
      >
        <strong>{{ metric.metricLabel }}</strong>
        <div>
          <span>本期 {{ comparisonMetricValue(metric.currentValue, metric.unit) }}</span>
          <span>对比期 {{ comparisonMetricValue(metric.comparisonValue, metric.unit) }}</span>
        </div>
        <small>{{ comparisonChangeText(metric) }}</small>
        <small v-if="comparisonDailyAverageText(metric)">{{ comparisonDailyAverageText(metric) }}</small>
        <small v-if="metric.note">{{ metric.note }}</small>
      </article>
      <p v-if="reportComparison.differentPeriodLengths">两期天数不同，可累加指标请优先查看日均变化。</p>
    </section>
  </div>
</template>

<style scoped lang="scss">
.business-card {
  box-sizing: border-box;
  max-width: 100%;
  border: 1px solid #dfe7f4;
  border-radius: 10px;
  padding: 11px 12px;
  background: linear-gradient(180deg, #ffffff 0%, #fbfdff 100%);
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.9);
}

.business-card-head {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
  margin-bottom: 10px;
}

.inventory-quality-summary {
  display: flex;
  flex-wrap: wrap;
  gap: 8px 12px;
  margin-bottom: 10px;
  padding: 10px;
  border-radius: 8px;
  background: #f5f8ff;
  color: #667085;
}

.inventory-quality-summary strong {
  width: 100%;
  color: #1f2937;
}

.inventory-quality-list {
  display: grid;
  gap: 8px;
}

.inventory-quality-row {
  padding: 10px;
  border: 1px solid #e5ebf5;
  border-radius: 8px;
  background: #fff;
}

.inventory-quality-row-head,
.inventory-quality-meta,
.inventory-quality-detail {
  display: flex;
  flex-wrap: wrap;
  gap: 6px 10px;
}

.inventory-quality-row-head {
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
}

.inventory-quality-meta,
.inventory-quality-detail {
  color: #667085;
  font-size: 12px;
  line-height: 1.6;
}

.inventory-quality-detail {
  margin-top: 6px;
  padding-top: 6px;
  border-top: 1px dashed #e5ebf5;
}

.inventory-quality-danger { color: #d92d20; }
.inventory-quality-success { color: #17934a; }
.inventory-quality-neutral { color: #667085; }

.business-card-icon {
  flex: none;
  width: 24px;
  height: 24px;
  display: grid;
  place-items: center;
  border-radius: 7px;
  background: #eef5ff;
  color: var(--app-primary);
}

.business-card-title {
  flex: 1 1 auto;
  min-width: 0;
  color: var(--app-text);
  font-size: 14px;
  font-weight: 700;
  line-height: 20px;
  word-break: break-word;
}

.business-card-toggle {
  flex: none;
  margin-left: auto;
  border: 0;
  width: 28px;
  height: 28px;
  display: inline-grid;
  place-items: center;
  border-radius: 7px;
  padding: 0;
  background: transparent;
  color: var(--app-primary);
  font-size: 15px;
  cursor: pointer;

  &:hover {
    background: #eef5ff;
  }
}

.business-card-fields {
  display: grid;
  gap: 8px;
}

.knowledge-card {
  border-color: #cfddf6;
  background: linear-gradient(180deg, #ffffff 0%, #f8fbff 100%);
}

.knowledge-card .business-card-icon {
  background: #eaf2ff;
  color: #175cd3;
}

.knowledge-card-body {
  display: grid;
  gap: 10px;
}

.knowledge-evidence-content {
  margin: 0;
  color: #344054;
  font-size: 13px;
  line-height: 1.7;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}

.knowledge-citation-source {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr);
  gap: 2px 10px;
  padding: 9px 10px;
  border-left: 3px solid #84adff;
  border-radius: 0 8px 8px 0;
  background: #f1f6ff;
  color: #667085;
  font-size: 12px;
  line-height: 18px;
}

.knowledge-citation-source strong {
  min-width: 0;
  color: #175cd3;
  font-weight: 700;
  overflow-wrap: anywhere;
}

.knowledge-citation-source small {
  grid-column: 2;
  color: #52627a;
  font-size: 12px;
  overflow-wrap: anywhere;
}

.knowledge-boundary-note {
  margin: 0;
  padding-top: 8px;
  border-top: 1px dashed #dfe7f4;
  color: #667085;
  font-size: 12px;
  line-height: 18px;
}

@media (max-width: 520px) {
  .knowledge-citation-source {
    grid-template-columns: minmax(0, 1fr);
  }

  .knowledge-citation-source small {
    grid-column: 1;
  }
}

.business-card-field {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  color: var(--app-text-tertiary);
  font-size: 13px;

  strong {
    color: var(--app-text);
    font-weight: 600;
    text-align: right;
    overflow-wrap: anywhere;
  }
}

.distribution-card {
  padding: 12px;
}

.distribution-card-body {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.distribution-list {
  display: grid;
  gap: 7px;
  max-height: 380px;
  overflow-y: auto;
  padding-right: 4px;
}

.distribution-row {
  display: grid;
  grid-template-columns: minmax(0, 1fr);
  gap: 6px;
  padding: 9px 10px;
  border: 1px solid #edf1f7;
  border-radius: 8px;
  background: #f8fafc;
}

.distribution-main {
  display: flex;
  flex-wrap: wrap;
  justify-content: space-between;
  gap: 8px;
  align-items: baseline;

  strong {
    color: var(--app-text);
    font-size: 13px;
    font-weight: 750;
    overflow-wrap: anywhere;
    text-align: right;
  }
}

.distribution-label {
  min-width: 0;
  color: var(--app-text);
  font-weight: 600;
  overflow-wrap: anywhere;
}

.distribution-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 5px 8px;
  color: var(--app-text-tertiary);
  font-size: 12px;
  line-height: 1.35;

  span {
    display: inline-flex;
    align-items: center;
    min-height: 20px;
    padding: 2px 7px;
    border-radius: 999px;
    background: #ffffff;
    border: 1px solid #edf1f7;
  }
}

.distribution-risk {
  padding: 9px 10px;
  border: 1px solid #fed7aa;
  border-radius: 8px;
  background: #fff7ed;
  color: #9a3412;
  font-size: 12px;
  line-height: 1.45;
  overflow-wrap: anywhere;
}

.distribution-row-risk {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding: 7px 8px;
  border-radius: 7px;
  background: #fff7ed;
  color: #9a3412;
  font-size: 12px;
  line-height: 1.35;
  overflow-wrap: anywhere;

  span {
    min-width: 0;
  }

  .el-button {
    flex: none;
  }
}

.assay-card {
  padding: 12px;
}

.task-card {
  min-width: 0;
  padding: 12px 12px 10px;
}

.task-card-body {
  box-sizing: border-box;
  max-width: 100%;
  min-width: 0;
  display: grid;
}

.task-card-head {
  flex-wrap: wrap;
  row-gap: 7px;
}

.task-card-head .business-card-title {
  flex: 0 1 auto;
}

.task-card-head .business-card-toggle {
  margin-left: 0;
}

.task-updated-badge {
  flex: none;
  padding: 2px 7px;
  border-radius: 999px;
  background: #eef5ff;
  color: #175cd3;
  font-size: 11px;
  font-weight: 650;
  line-height: 18px;
}

.task-status-summary {
  box-sizing: border-box;
  flex: 1 1 auto;
  min-width: 0;
  display: flex;
  justify-content: flex-end;
  flex-wrap: wrap;
  gap: 5px;

  span {
    display: inline-flex;
    align-items: center;
    min-height: 22px;
    padding: 2px 8px;
    border-radius: 999px;
    font-size: 11px;
    font-weight: 650;
    line-height: 16px;
  }
}

.task-summary-total,
.task-summary-other {
  background: #f1f5f9;
  color: #64748b;
}

.task-summary-pending {
  background: #fff7ed;
  color: #b45309;
}

.task-summary-confirmed {
  background: #f0fdf4;
  color: #15803d;
}

.task-list {
  box-sizing: border-box;
  max-width: 100%;
  display: grid;
  gap: 0;
  min-width: 0;
}

.task-group {
  box-sizing: border-box;
  max-width: 100%;
  display: grid;
  gap: 9px;
  min-width: 0;
  padding: 12px 0;
  border-top: 1px solid #e8edf5;

  &:first-child {
    padding-top: 2px;
    border-top: 0;
  }
}

.task-group-detail {
  padding: 0;
  border: 0;
}

.task-group-head,
.task-group-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

.task-group-head {
  box-sizing: border-box;
  max-width: 100%;
  min-width: 0;
}

.task-group-disclosure {
  box-sizing: border-box;
  max-width: 100%;
  flex: 1 1 auto;
  min-width: 0;
  display: flex;
  align-items: center;
  gap: 10px;
  min-height: 34px;
  padding: 5px 6px;
  border: 0;
  border-radius: 7px;
  background: transparent;
  color: inherit;
  font: inherit;
  text-align: left;
  cursor: pointer;

  &:hover {
    background: #f7f9fc;
  }

  > .el-icon {
    flex: none;
    color: #667085;
    font-size: 14px;
  }
}

.task-group-title {
  display: flex;
  align-items: baseline;
  gap: 7px;
  min-width: 0;

  strong {
    color: var(--app-text);
    font-size: 13px;
  }

  span {
    color: var(--app-text-tertiary);
    font-size: 11px;
  }
}

.task-group-statuses {
  flex: 1 1 auto;
  min-width: 0;
  display: flex;
  flex-wrap: wrap;
  gap: 4px 9px;
  color: #667085;
  font-size: 11px;
  line-height: 18px;
}

.task-group-actions {
  flex: none;
  min-height: 32px;
  display: flex;
  align-items: center;
  padding: 0 6px;
  border-left: 1px solid #edf1f7;

  :deep(.el-checkbox) {
    height: 32px;
    margin-right: 0;
  }

  :deep(.el-checkbox__label) {
    padding-left: 7px;
    color: #475467;
    font-size: 12px;
  }

  :deep(.el-checkbox__inner) {
    width: 17px;
    height: 17px;
    border-color: #b8c4d6;
  }

  :deep(.el-checkbox__input.is-checked .el-checkbox__inner),
  :deep(.el-checkbox__input.is-indeterminate .el-checkbox__inner) {
    border-color: var(--app-primary);
    background: var(--app-primary);
  }
}

.task-group-records {
  box-sizing: border-box;
  max-width: 100%;
  display: grid;
  gap: 7px;
  min-width: 0;
}

.task-group-footer {
  min-height: 42px;
  padding: 8px 2px 0;
  border-top: 1px solid #edf1f7;

  span {
    color: var(--app-text-tertiary);
    font-size: 12px;
  }

  .el-button {
    min-width: 116px;
  }
}

.task-row {
  box-sizing: border-box;
  max-width: 100%;
  display: grid;
  gap: 6px;
  min-width: 0;
  padding: 9px 10px;
  border: 1px solid #e7edf6;
  border-radius: 9px;
  background: #fafbfd;
  color: var(--app-text);
  transition: border-color 0.15s ease, background-color 0.15s ease, box-shadow 0.15s ease;
}

.task-row.is-selectable {
  cursor: pointer;

  &:hover {
    border-color: #bfd2ff;
    background: #f7faff;
  }

  &:focus-visible {
    outline: 0;
    border-color: #7aa2ff;
    box-shadow: 0 0 0 3px rgba(22, 93, 255, 0.10);
  }
}

.task-row.is-selected {
  border-color: #7aa2ff;
  background: #f1f6ff;
  box-shadow: inset 3px 0 0 var(--app-primary);
}

.task-row.is-completed {
  border-color: #e1ebe5;
  background: #fafcfb;
}

.task-row-head {
  display: flex;
  align-items: center;
  gap: 9px;
  min-width: 0;
}

.task-row-copy {
  flex: 1 1 auto;
  min-width: 0;
  display: flex;
  align-items: baseline;
  flex-wrap: wrap;
  gap: 3px 10px;

  strong {
    min-width: 0;
    font-size: 13px;
    line-height: 1.4;
    overflow-wrap: anywhere;
  }

  span {
    min-width: 0;
    color: #475467;
    font-size: 12px;
    font-weight: 600;
    overflow-wrap: anywhere;
  }
}

.task-row-checkbox {
  flex: none;

  :deep(.el-checkbox__inner) {
    width: 17px;
    height: 17px;
    border-color: #b8c4d6;
  }

  :deep(.el-checkbox__input.is-checked .el-checkbox__inner) {
    border-color: var(--app-primary);
    background: var(--app-primary);
  }
}

.task-status {
  flex: none;
  padding: 2px 7px;
  border-radius: 999px;
  font-size: 11px;
  font-weight: 650;
}

.task-status-pending {
  background: #fff7ed;
  color: #b45309;
}

.task-status-success {
  background: #f0fdf4;
  color: #15803d;
}

.task-status-muted,
.task-status-unknown {
  background: #f1f5f9;
  color: #64748b;
}

.task-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 3px 0;
  padding-left: 26px;
  color: var(--app-text-tertiary);
  font-size: 11px;
  line-height: 18px;

  span {
    display: inline-flex;
    align-items: center;
  }

  span + span::before {
    content: '';
    width: 3px;
    height: 3px;
    margin: 0 8px;
    border-radius: 50%;
    background: #c4ccd8;
  }
}

.task-detail-grid {
  display: grid;
  grid-template-columns: minmax(76px, auto) minmax(0, 1fr);
  gap: 6px 10px;
  margin: 2px 0 0;
  padding-top: 9px;
  border-top: 1px solid #e7edf6;
  font-size: 12px;

  dt {
    color: var(--app-text-tertiary);
  }

  dd {
    margin: 0;
    color: var(--app-text);
    overflow-wrap: anywhere;
  }
}

.assay-card-body {
  display: grid;
  gap: 10px;
}

.assay-summary {
  display: grid;
  gap: 8px;
  padding: 10px;
  border-radius: 8px;
  background: #f8fafc;
}

.assay-summary-main,
.assay-summary-standard,
.assay-history-main {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.assay-summary-standard {
  color: var(--app-text-tertiary);
  font-size: 12px;

  strong {
    color: var(--app-text);
    text-align: right;
  }
}

.assay-summary p {
  margin: 0;
  color: #9a3412;
  font-size: 12px;
  line-height: 1.5;
}

.assay-metric-list {
  display: grid;
  gap: 1px;
  overflow: hidden;
  border: 1px solid #edf1f7;
  border-radius: 8px;
  background: #edf1f7;
}

.assay-metric-head,
.assay-metric-row {
  display: grid;
  grid-template-columns: minmax(80px, 1fr) minmax(64px, 0.8fr) minmax(110px, 1.2fr) minmax(64px, 0.8fr);
  gap: 8px;
  align-items: center;
  padding: 8px 9px;
}

.assay-metric-head {
  background: #f1f5f9;
  color: var(--app-text-tertiary);
  font-size: 11px;
}

.assay-metric-row {
  background: #ffffff;
  color: var(--app-text);
  font-size: 12px;

  small {
    grid-column: 1 / -1;
    color: #b91c1c;
  }
}

.assay-result {
  font-weight: 650;
}

.assay-result-pass {
  color: #15803d;
}

.assay-result-fail {
  color: #b91c1c;
}

.assay-result-review {
  color: #b45309;
}

.assay-result-unknown {
  color: #64748b;
}

.assay-history-list {
  display: grid;
  gap: 7px;
  max-height: 360px;
  overflow-y: auto;
}

.assay-history-row {
  display: grid;
  gap: 5px;
  padding: 9px 10px;
  border: 1px solid #edf1f7;
  border-radius: 8px;
  background: #f8fafc;
  color: var(--app-text);
  font-size: 12px;
}

.assay-history-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 5px 10px;
  color: var(--app-text-tertiary);
}

.assay-notes {
  display: grid;
  gap: 3px;
  padding: 8px 9px;
  border-radius: 8px;
  background: #fff7ed;
  color: #9a3412;
  font-size: 12px;
  line-height: 1.45;
}

.today-operations-overview-card {
  min-width: 0;
  padding: 12px;
}

.today-operations-body {
  display: grid;
  min-width: 0;
  gap: 10px;
}

.today-operations-summary {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 8px;
  padding: 9px 10px;
  border: 1px solid #cfe0ff;
  border-radius: 9px;
  background: #f3f7ff;

  strong {
    color: #1d4ed8;
    font-size: 13px;
    line-height: 1.45;
  }

  span {
    flex: none;
    color: #667085;
    font-size: 11px;
  }
}

.today-operations-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px;
}

.today-operations-section {
  display: grid;
  min-width: 0;
  gap: 5px;
  padding: 9px 10px;
  border: 1px solid #e7edf6;
  border-radius: 9px;
  background: #fafbfd;

  > span {
    color: #667085;
    font-size: 11px;
  }

  > strong {
    color: #1d2939;
    font-size: 13px;
    line-height: 1.4;
    overflow-wrap: anywhere;
  }

  ul {
    display: grid;
    gap: 2px;
    margin: 0;
    padding-left: 16px;
    color: #667085;
    font-size: 10px;
    line-height: 1.45;
  }
}

.today-operations-section-flow,
.today-operations-section-tasks {
  grid-column: 1 / -1;
}

.today-operations-notes,
.today-operations-scope {
  padding: 8px 9px;
  border-radius: 8px;
  font-size: 11px;
  line-height: 1.5;
}

.today-operations-notes {
  display: grid;
  gap: 3px;
  background: #fff7ed;
  color: #9a3412;
}

.today-operations-scope {
  display: grid;
  gap: 3px;
  background: #f7f9fc;
  color: #667085;

  strong {
    color: #344054;
  }
}

@media (max-width: 520px) {
  .today-operations-grid {
    grid-template-columns: 1fr;
  }

  .today-operations-section-flow,
  .today-operations-section-tasks {
    grid-column: auto;
  }
}

.daily-production-report-card {
  min-width: 0;
  padding: 12px;
}

.registered-report-comparison {
  display: grid;
  gap: 7px;
  margin-top: 10px;
  padding-top: 10px;
  border-top: 1px solid #e7edf6;
}

.registered-report-comparison-head,
.registered-report-comparison-row {
  display: grid;
  gap: 4px;
}

.registered-report-comparison-head span,
.registered-report-comparison-row small,
.registered-report-comparison > p {
  margin: 0;
  color: var(--app-text-tertiary);
  font-size: 11px;
  line-height: 1.5;
}

.registered-report-comparison-row {
  padding: 8px 9px;
  border: 1px solid #e7edf6;
  border-radius: 8px;
  background: #f8fafc;
}

.registered-report-comparison-row > div {
  display: flex;
  flex-wrap: wrap;
  gap: 5px 12px;
  color: #475467;
  font-size: 11px;
}

.daily-production-report-body {
  display: grid;
  min-width: 0;
  gap: 10px;
}

.daily-report-summary {
  display: grid;
  gap: 9px;
}

.daily-report-scope,
.daily-report-kpis {
  display: grid;
  gap: 7px;
}

.daily-report-scope {
  grid-template-columns: repeat(2, minmax(0, 1fr));

  > div {
    display: grid;
    gap: 3px;
    padding: 8px 9px;
    border-radius: 8px;
    background: #f7f9fc;
  }

  span {
    color: var(--app-text-tertiary);
    font-size: 11px;
  }

  strong {
    color: var(--app-text);
    font-size: 12px;
    overflow-wrap: anywhere;
  }
}

.daily-report-kpis {
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.daily-report-kpi {
  display: grid;
  gap: 4px;
  padding: 9px 10px;
  border: 1px solid #e7edf6;
  border-radius: 9px;
  background: #ffffff;

  span {
    color: var(--app-text-tertiary);
    font-size: 11px;
  }

  strong {
    color: #344054;
    font-size: 14px;
    overflow-wrap: anywhere;
  }
}

.daily-report-kpi-primary {
  border-color: #cfe0ff;
  background: #f3f7ff;

  strong {
    color: var(--app-primary);
  }
}

.daily-report-empty,
.daily-report-qr-note,
.daily-report-data-time,
.daily-report-boundary {
  margin: 0;
  border-radius: 8px;
  padding: 8px 9px;
  font-size: 11px;
  line-height: 1.5;
}

.business-card-export {
  flex: none;
  border: 0;
  background: transparent;
  color: #2563eb;
  font-size: 13px;
  line-height: 1;
  cursor: pointer;
}

.business-card-export:hover {
  color: #1d4ed8;
}

.daily-report-empty {
  background: #f5f7fa;
  color: #667085;
}

.daily-report-qr-note {
  background: #eef5ff;
  color: #315da8;
}

.daily-report-data-time,
.daily-report-boundary {
  padding: 0;
  background: transparent;
  color: var(--app-text-tertiary);
}

.daily-report-section {
  display: grid;
  gap: 7px;

  h4 {
    margin: 2px 0 0;
    color: var(--app-text);
    font-size: 12px;
  }
}

.daily-report-row {
  display: grid;
  gap: 6px;
  padding: 9px 10px;
  border: 1px solid #e7edf6;
  border-radius: 9px;
  background: #fafbfd;
}

.daily-report-row-head,
.daily-report-row-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 6px 10px;
}

.daily-report-row-head {
  align-items: center;
  justify-content: space-between;
  color: var(--app-text);
  font-size: 12px;

  strong {
    min-width: 0;
    overflow-wrap: anywhere;
  }
}

.daily-report-row-meta {
  color: var(--app-text-tertiary);
  font-size: 11px;
}

.daily-report-point-list {
  display: grid;
  gap: 5px;
  max-height: 260px;
  overflow-y: auto;
}

.daily-report-point {
  display: grid;
  grid-template-columns: minmax(90px, auto) minmax(0, 1fr);
  gap: 3px 10px;
  padding: 8px 9px;
  border-radius: 8px;
  background: #f7f9fc;
  font-size: 11px;

  strong {
    color: #344054;
    text-align: right;
  }

  small {
    grid-column: 1 / -1;
    color: var(--app-text-tertiary);
  }
}

.daily-report-quality {
  padding: 8px 9px;
  border-radius: 8px;
  background: #fff7ed;
  color: #9a3412;
  font-size: 11px;
  line-height: 1.5;

  ul {
    margin: 4px 0 0;
    padding-left: 18px;
  }
}

.inventory-level-trend-card {
  min-width: 0;
  padding: 12px;
}

.inventory-trend-source-banner {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 4px 10px;
  padding: 8px 9px;
  border: 1px solid #bbf7d0;
  border-radius: 8px;
  background: #f0fdf4;
  color: #166534;
  font-size: 11px;
}

.inventory-trend-source-simulation {
  border-color: #fed7aa;
  background: #fff7ed;
  color: #9a3412;
}

.daily-report-kpi small {
  color: var(--app-text-tertiary);
  font-size: 10px;
}

.inventory-trend-increase {
  color: #067647 !important;
}

.inventory-trend-decrease {
  color: #b42318 !important;
}

.inventory-trend-unchanged {
  color: var(--app-text-tertiary) !important;
}

.inventory-trend-daily-row {
  border-left: 3px solid #cfe0ff;
}

.inventory-trend-boundary {
  border-top: 1px dashed #e4e7ec;
  padding-top: 8px;
}

.production-input-output-flow-card {
  min-width: 0;
  padding: 12px;
}

.production-flow-kpis {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 7px;
}

.production-flow-kpi {
  display: grid;
  gap: 4px;
  padding: 10px;
  border: 1px solid #e1e8f2;
  border-radius: 9px;

  span,
  small {
    color: var(--app-text-tertiary);
    font-size: 11px;
  }

  strong {
    color: #344054;
    font-size: 15px;
    overflow-wrap: anywhere;
  }
}

.production-flow-kpi-input {
  border-color: #cfe0ff;
  background: #f3f7ff;

  strong {
    color: #2458c6;
  }
}

.production-flow-kpi-output {
  border-color: #ccebd8;
  background: #f2fbf5;

  strong {
    color: #157347;
  }
}

.production-flow-coverage {
  margin: 0;
  padding: 8px 9px;
  border-radius: 8px;
  background: #f7f9fc;
  color: #475467;
  font-size: 11px;
  line-height: 1.5;
}

.production-flow-daily-head,
.production-flow-daily-row {
  display: grid;
  grid-template-columns: minmax(82px, .85fr) repeat(2, minmax(0, 1fr));
  gap: 6px;
}

.production-flow-daily-head {
  padding: 0 8px;
  color: var(--app-text-tertiary);
  font-size: 10px;
}

.production-flow-daily-row {
  align-items: center;
  padding: 8px 9px;
  border-radius: 8px;
  background: #f7f9fc;
  font-size: 11px;

  > strong {
    color: var(--app-text);
    overflow-wrap: anywhere;
  }

  > div {
    display: grid;
    gap: 2px;
    min-width: 0;

    span,
    small {
      color: var(--app-text-tertiary);
      font-size: 10px;
    }

    b {
      color: #344054;
      overflow-wrap: anywhere;
    }
  }
}

.production-flow-order-row > p {
  margin: 0;
  color: #475467;
  font-size: 11px;
}

.production-flow-order-sources {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 6px;

  > div {
    display: grid;
    gap: 2px;
    padding: 7px 8px;
    border-radius: 7px;
    background: #ffffff;

    span,
    small {
      color: var(--app-text-tertiary);
      font-size: 10px;
    }

    strong {
      color: #344054;
      font-size: 12px;
      overflow-wrap: anywhere;
    }
  }
}

.production-flow-boundary {
  padding: 8px 9px;
  border: 1px solid #f4c77d;
  background: #fff8eb;
  color: #9a5b13;
}

.pallet-task-cycle-report-card {
  min-width: 0;
  padding: 12px;
}

.task-cycle-duration-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 6px;

  > div {
    display: grid;
    gap: 3px;
    padding: 8px;
    border: 1px solid #dbe7fb;
    border-radius: 8px;
    background: #f5f8ff;
  }

  span {
    color: var(--app-text-tertiary);
    font-size: 10px;
  }

  strong {
    color: #2458c6;
    font-size: 12px;
    overflow-wrap: anywhere;
  }
}

.task-cycle-duration-inline {
  display: flex;
  flex-wrap: wrap;
  gap: 5px 12px;
  color: #475467;
  font-size: 11px;
}

.task-cycle-pending-row {
  border-color: #f0d3a4;
  background: #fffbf3;

  > p {
    margin: 0;
    color: #7a5a27;
    font-size: 10px;
    line-height: 1.45;
  }
}

@media (max-width: 520px) {
  .task-cycle-duration-grid {
    grid-template-columns: 1fr;
  }
}

.production-card {
  min-width: 0;
  padding: 12px;
}

.production-card-body {
  box-sizing: border-box;
  max-width: 100%;
  min-width: 0;
  display: grid;
  gap: 10px;
}

.production-summary {
  display: grid;
  gap: 9px;
  padding: 10px;
  border: 1px solid #e7edf6;
  border-radius: 9px;
  background: #f8fafc;
}

.production-summary-head,
.production-row-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  min-width: 0;

  strong {
    min-width: 0;
    color: var(--app-text);
    overflow-wrap: anywhere;
  }

  > span {
    flex: none;
    padding: 2px 7px;
    border-radius: 999px;
    font-size: 11px;
    font-weight: 650;
  }
}

.production-summary-grid {
  display: grid;
  grid-template-columns: minmax(72px, auto) minmax(0, 1fr);
  gap: 5px 10px;
  margin: 0;
  font-size: 12px;

  dt {
    color: var(--app-text-tertiary);
  }

  dd {
    margin: 0;
    color: var(--app-text);
    text-align: right;
    overflow-wrap: anywhere;
  }
}

.production-progress-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 5px;

  span {
    padding: 3px 7px;
    border: 1px solid #e5ebf4;
    border-radius: 999px;
    background: #ffffff;
    color: #475467;
    font-size: 11px;
    line-height: 16px;
  }
}

.production-section {
  display: grid;
  gap: 7px;

  h4 {
    margin: 2px 0 0;
    color: var(--app-text);
    font-size: 12px;
    font-weight: 700;
  }
}

.production-row {
  min-width: 0;
  display: grid;
  gap: 6px;
  padding: 9px 10px;
  border: 1px solid #e7edf6;
  border-radius: 9px;
  background: #fafbfd;
  color: var(--app-text);
  font-size: 12px;
}

.production-row-primary {
  color: #344054;
  font-weight: 650;
}

.production-row-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 4px 10px;
  color: var(--app-text-tertiary);
  font-size: 11px;
  line-height: 17px;
}

.production-destinations {
  display: grid;
  gap: 5px;
}

.production-destination {
  display: grid;
  gap: 3px;
  padding: 7px 8px;
  border-radius: 7px;
  background: #eefbf3;
  color: #166534;

  div {
    display: flex;
    justify-content: space-between;
    gap: 8px;
  }

  small {
    color: #4b7659;
    overflow-wrap: anywhere;
  }
}

.production-empty-note {
  margin: 0;
  padding: 7px 8px;
  border-radius: 7px;
  background: #f1f5f9;
  color: #64748b;
  font-size: 11px;
}

.production-stage-note {
  margin: 0;
  color: #9a6700;
  font-size: 11px;
  line-height: 1.45;
}

.production-trace ol {
  display: grid;
  gap: 6px;
  margin: 0;
  padding: 9px 10px 9px 29px;
  border: 1px solid #e7edf6;
  border-radius: 9px;
  background: #fafbfd;
  color: #475467;
  font-size: 11px;
  line-height: 1.45;
}

.production-status-success {
  background: #f0fdf4;
  color: #15803d;
}

.production-status-pending {
  background: #fff7ed;
  color: #b45309;
}

.production-status-muted,
.production-status-unknown {
  background: #f1f5f9;
  color: #64748b;
}

.pallet-card {
  min-width: 0;
  padding: 12px;
}

.pallet-card-body {
  display: grid;
  min-width: 0;
  gap: 10px;
}

.pallet-summary,
.pallet-flow-summary {
  display: grid;
  gap: 8px;
  padding: 10px;
  border: 1px solid #e7edf6;
  border-radius: 9px;
  background: #f8fafc;
}

.pallet-summary-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;

  strong {
    min-width: 0;
    overflow-wrap: anywhere;
  }

  span {
    flex: none;
    padding: 2px 7px;
    border-radius: 999px;
    font-size: 11px;
    font-weight: 650;
  }
}

.pallet-summary-grid {
  display: grid;
  grid-template-columns: minmax(72px, auto) minmax(0, 1fr);
  gap: 5px 10px;
  margin: 0;
  font-size: 12px;

  dt {
    color: var(--app-text-tertiary);
  }

  dd {
    margin: 0;
    color: var(--app-text);
    text-align: right;
    overflow-wrap: anywhere;
  }
}

.pallet-flow-summary {
  grid-template-columns: minmax(0, 1fr) auto;
  align-items: center;

  span {
    color: var(--app-primary);
    font-size: 12px;
    font-weight: 650;
  }

  small {
    grid-column: 1 / -1;
    color: var(--app-text-tertiary);
  }
}

.pallet-timeline {
  display: grid;
  gap: 7px;

  h4 {
    margin: 2px 0 0;
    color: var(--app-text);
    font-size: 12px;
  }

  ol {
    display: grid;
    gap: 0;
    margin: 0;
    padding: 0;
    list-style: none;
  }

  li {
    position: relative;
    display: grid;
    grid-template-columns: 14px minmax(0, 1fr);
    gap: 7px;
    padding: 0 0 11px;
  }

  li:not(:last-child)::before {
    content: '';
    position: absolute;
    top: 12px;
    bottom: 0;
    left: 5px;
    width: 1px;
    background: #d8e2f1;
  }
}

.pallet-timeline-dot {
  position: relative;
  z-index: 1;
  width: 9px;
  height: 9px;
  margin-top: 4px;
  border: 2px solid #fff;
  border-radius: 50%;
  background: var(--app-primary);
  box-shadow: 0 0 0 1px #a9c4fb;
}

.pallet-timeline-content {
  display: grid;
  gap: 3px;
  min-width: 0;
  padding: 8px 9px;
  border: 1px solid #e7edf6;
  border-radius: 8px;
  background: #fafbfd;
  font-size: 12px;
}

.pallet-timeline-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 8px;

  time {
    flex: none;
    color: var(--app-text-tertiary);
    font-size: 11px;
  }
}

.pallet-timeline-route {
  color: #344054;
  font-weight: 600;
}

.pallet-timeline-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 3px 9px;
  color: var(--app-text-tertiary);
  font-size: 11px;
}

.pallet-risk-list {
  display: grid;
  gap: 4px;
  padding: 8px 9px;
  border: 1px solid #fed7aa;
  border-radius: 8px;
  background: #fff7ed;
  color: #9a3412;
  font-size: 12px;
}

.pallet-empty-note {
  margin: 0;
  padding: 8px 9px;
  border-radius: 8px;
  background: #f1f5f9;
  color: #64748b;
  font-size: 12px;
}

.pallet-status-success {
  background: #f0fdf4;
  color: #15803d;
}

.pallet-status-pending {
  background: #fff7ed;
  color: #b45309;
}

.pallet-status-danger {
  background: #fef2f2;
  color: #b42318;
}

.pallet-status-muted {
  background: #f1f5f9;
  color: #64748b;
}

@media (max-width: 520px) {
  .task-card-head .business-card-toggle {
    margin-left: auto;
  }

  .task-status-summary {
    order: 5;
    flex: 1 0 100%;
    justify-content: flex-start;
    padding-left: 32px;
  }

  .task-group-head {
    align-items: stretch;
    flex-direction: column;
    gap: 3px;
  }

  .task-group-actions {
    justify-content: flex-start;
    padding: 0 6px;
    border-left: 0;
  }

  .task-group-statuses {
    justify-content: flex-end;
  }

  .task-row-copy {
    display: grid;
    gap: 2px;
  }

  .task-meta {
    padding-left: 26px;
  }

  .task-row.is-completed .task-meta,
  .task-group-detail .task-meta {
    padding-left: 0;
  }

  .task-group-footer {
    align-items: flex-start;
    flex-direction: column;

    .el-button {
      width: 100%;
    }
  }

  .assay-metric-head {
    display: none;
  }

  .assay-metric-row {
    grid-template-columns: minmax(0, 1fr) auto;

    > span::before {
      content: attr(data-label) '：';
      color: var(--app-text-tertiary);
      font-weight: 400;
    }

    > span {
      grid-column: 1 / -1;
    }
  }

  .distribution-main {
    display: grid;
    grid-template-columns: minmax(0, 1fr);

    strong {
      text-align: left;
    }
  }

  .distribution-row-risk {
    align-items: flex-start;
    flex-direction: column;
  }

  .production-summary-grid {
    grid-template-columns: minmax(0, 1fr);
    gap: 2px;

    dd {
      margin-bottom: 4px;
      text-align: left;
    }
  }

  .production-destination div {
    align-items: flex-start;
    flex-direction: column;
    gap: 2px;
  }

  .pallet-summary-grid {
    grid-template-columns: minmax(0, 1fr);
    gap: 2px;

    dd {
      margin-bottom: 4px;
      text-align: left;
    }
  }

  .pallet-timeline-head {
    display: grid;
    gap: 2px;
  }
}
</style>
