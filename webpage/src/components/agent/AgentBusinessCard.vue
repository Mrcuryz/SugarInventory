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
  taskDetailEntries,
  taskGroups,
  taskStatusTone
} from './taskCardPresentation.mjs'

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
const expanded = ref(true)
const collapsedTaskGroups = ref(new Set())
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

const toggleTaskSelection = (record, selected) => {
  if (!record.selectable) return
  const next = new Set(selectedTaskKeys.value)
  if (selected) next.add(record.selectionKey)
  else next.delete(record.selectionKey)
  selectedTaskKeys.value = next
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
  <div class="business-card" :class="{ 'distribution-card': isDistribution, 'assay-card': isAssay, 'task-card': isTask }">
    <div class="business-card-head">
      <span class="business-card-icon">
        <el-icon><DataAnalysis /></el-icon>
      </span>
      <span class="business-card-title">{{ card.title || '查询结果' }}</span>
      <button
        v-if="isAssay || isTask"
        type="button"
        class="business-card-toggle"
        :aria-expanded="expanded"
        @click="expanded = !expanded"
      >
        {{ expanded ? '收起' : '展开' }}
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
            <div class="task-group-title">
              <strong>{{ group.label }}</strong>
              <span>{{ group.records.length }} 条</span>
            </div>
            <div class="task-group-actions">
              <el-checkbox
                v-if="selectableGroupRecords(group).length"
                :model-value="isTaskGroupSelected(group)"
                :indeterminate="isTaskGroupIndeterminate(group)"
                @change="toggleTaskGroupSelection(group, $event)"
              >
                全选
              </el-checkbox>
              <button type="button" class="task-group-toggle" @click="toggleTaskGroup(group)">
                {{ isTaskGroupExpanded(group) ? '收起' : '展开' }}
              </button>
            </div>
          </div>

          <div v-show="isTaskDetail || isTaskGroupExpanded(group)" class="task-group-records">
            <article
              v-for="(task, taskIndex) in group.records"
              :key="task.selectionKey"
              class="task-row"
            >
              <div class="task-row-head">
                <el-checkbox
                  v-if="!isTaskDetail && task.selectable"
                  class="task-row-checkbox"
                  :aria-label="`选择托盘 ${task.palletCode}`"
                  :model-value="isTaskSelected(task)"
                  @change="toggleTaskSelection(task, $event)"
                />
                <strong>{{ task.label || `${taskIndex + 1}. ${task.taskTypeLabel}任务` }}</strong>
                <span class="task-status" :class="`task-status-${taskStatusTone(task.taskStatusLabel)}`">
                  {{ task.taskStatusLabel }}
                </span>
              </div>
              <div class="task-product">{{ task.productLabel }}</div>
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
            <span>已选择 {{ selectedGroupRecords(group).length }} 条</span>
            <el-button
              type="primary"
              size="small"
              :disabled="!selectedGroupRecords(group).length"
              @click="openTaskBatch(group)"
            >
              批量处理
            </el-button>
          </div>
        </section>
      </div>
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
  margin-bottom: 10px;
}

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
  padding: 3px 6px;
  background: transparent;
  color: var(--app-primary);
  font-size: 12px;
  cursor: pointer;
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
  padding: 12px;
}

.task-card-body {
  display: grid;
}

.task-list {
  display: grid;
  gap: 8px;
  max-height: 380px;
  overflow-y: auto;
  padding-right: 3px;
}

.task-group {
  display: grid;
  gap: 8px;
  padding: 9px;
  border: 1px solid #dfe7f4;
  border-radius: 10px;
  background: #ffffff;
}

.task-group-detail {
  padding: 0;
  border: 0;
}

.task-group-head,
.task-group-actions,
.task-group-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
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

.task-group-actions {
  flex: none;
}

.task-group-toggle {
  padding: 0;
  border: 0;
  background: transparent;
  color: var(--app-primary);
  cursor: pointer;
  font-size: 12px;
}

.task-group-records {
  display: grid;
  gap: 8px;
}

.task-group-footer {
  padding-top: 8px;
  border-top: 1px solid #edf1f7;

  span {
    color: var(--app-text-tertiary);
    font-size: 12px;
  }
}

.task-row {
  display: grid;
  gap: 7px;
  padding: 10px;
  border: 1px solid #e7edf6;
  border-radius: 9px;
  background: #f8fafc;
  color: var(--app-text);
}

.task-row-head {
  display: flex;
  align-items: flex-start;
  gap: 10px;

  strong {
    flex: 1;
    min-width: 0;
    font-size: 13px;
    line-height: 1.4;
    overflow-wrap: anywhere;
  }
}

.task-row-checkbox {
  flex: none;
  margin-top: 1px;
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

.task-product {
  font-size: 13px;
  font-weight: 650;
  overflow-wrap: anywhere;
}

.task-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 5px 8px;
  color: var(--app-text-tertiary);
  font-size: 12px;

  span {
    padding: 2px 7px;
    border: 1px solid #edf1f7;
    border-radius: 999px;
    background: #ffffff;
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

@media (max-width: 520px) {
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
}
</style>
