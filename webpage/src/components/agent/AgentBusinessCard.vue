<script setup>
import { computed, ref } from 'vue'
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
</script>

<template>
  <div class="business-card" :class="{ 'distribution-card': isDistribution, 'assay-card': isAssay, 'task-card': isTask, 'production-card': isProduction, 'pallet-card': isPallet, 'inventory-quality-card': isInventoryQuality }">
    <div class="business-card-head" :class="{ 'task-card-head': isTask && !isTaskDetail }">
      <span class="business-card-icon">
        <el-icon><DataAnalysis /></el-icon>
      </span>
      <span class="business-card-title">
        {{ isTask && !isTaskDetail ? '任务状态' : (card.title || '查询结果') }}
      </span>
      <span v-if="isTask && !isTaskDetail && taskSummary.updated" class="task-updated-badge">已更新</span>
      <div v-if="isTask && !isTaskDetail" class="task-status-summary" aria-label="当前任务状态汇总">
        <span class="task-summary-total">共 {{ taskSummary.totalCount }} 条</span>
        <span v-if="taskSummary.pendingCount" class="task-summary-pending">待处理 {{ taskSummary.pendingCount }}</span>
        <span v-if="taskSummary.confirmedCount" class="task-summary-confirmed">已确认 {{ taskSummary.confirmedCount }}</span>
        <span v-if="taskSummary.otherCount" class="task-summary-other">其他 {{ taskSummary.otherCount }}</span>
      </div>
      <button
        v-if="isAssay || isTask || isProduction || isPallet || isInventoryQuality"
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
    <div v-if="isAssay" class="assay-card-body">
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
    <div v-else-if="card.fields?.length" class="business-card-fields">
      <div
        v-for="(field, fieldIndex) in card.fields"
        :key="`${field.label || field.name || fieldIndex}-${field.value || fieldIndex}`"
        class="business-card-field"
      >
        <span>{{ field.label || field.name }}</span>
        <strong>{{ field.value }}</strong>
      </div>
    </div>
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
