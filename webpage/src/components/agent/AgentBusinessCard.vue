<script setup>
import { computed } from 'vue'
import { distributionRiskSummary, distributionRows, isDistributionCard } from './agentDisplay'

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
  <div class="business-card" :class="{ 'distribution-card': isDistribution }">
    <div class="business-card-head">
      <span class="business-card-icon">
        <el-icon><DataAnalysis /></el-icon>
      </span>
      <span class="business-card-title">{{ card.title || '查询结果' }}</span>
    </div>
    <div v-if="isDistribution" class="distribution-card-body">
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

@media (max-width: 520px) {
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
