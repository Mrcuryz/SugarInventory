<script setup>
import { computed } from 'vue'
import { assayEntryAction, assayEntryControl, previewAsCard } from './assayEntryPresentation.mjs'
const props = defineProps({ card: { type: Object, required: true }, canSave: Boolean, nested: Boolean })
const emit = defineEmits(['card-action'])
const preview = computed(() => props.card.fields?.find(row => row.kind === 'assay_entry_preview_summary') || {})
const control = computed(() => assayEntryControl(props.card))
const action = computed(() => assayEntryAction(props.card, props.canSave))
const outcomeUnknown = computed(() => preview.value.saveOutcomeUnknown || control.value?.uncertain)
const isGroup = computed(() => preview.value.entryKind === 'GROUP_V1')
const invalidated = computed(() => preview.value.invalidated || control.value?.invalidated)
</script>

<template>
  <section class="assay-entry-preview">
    <div class="preview-heading"><strong>{{ isGroup ? preview.groupName : preview.productName }}</strong><span :class="{ saved: preview.saved }">{{ preview.saved ? '已保存' : outcomeUnknown ? '待核实' : invalidated ? '已失效' : '未保存' }}</span></div>
    <template v-if="isGroup">
      <p class="group-summary">采样日期：{{ preview.sampleDate }} · 共 {{ preview.productCount }} 个产品</p>
      <div class="group-products">
        <AssayEntryPreviewCard v-for="(product, index) in preview.products || []" :key="product.productId || index"
          nested :card="previewAsCard({ ...product, entryKind: undefined, saveOutcomeUnknown: outcomeUnknown, invalidated })" />
      </div>
    </template>
    <template v-else>
    <dl>
      <div><dt>采样日期</dt><dd>{{ preview.sampleDate }}</dd></div>
      <div v-if="preview.existingVersion"><dt>已有记录</dt><dd>第 {{ preview.existingVersion }} 版</dd></div>
      <div><dt>{{ preview.saved ? '记录版本' : '拟新增版本' }}</dt><dd>第 {{ preview.saved ? preview.recordVersion : preview.proposedVersion }} 版</dd></div>
      <div><dt>{{ preview.saved ? '判定结果' : '预判结果' }}</dt><dd>{{ preview.judgementLabel }}</dd></div>
      <div v-if="preview.appliedStandardName"><dt>参考标准</dt><dd>{{ preview.appliedStandardName }} · 第 {{ preview.appliedStandardVersion }} 版</dd></div>
    </dl>
    <div class="metric-list">
      <div v-for="metric in preview.metrics || []" :key="metric.name" class="metric-row">
        <span>{{ metric.name }}</span>
        <strong v-if="metric.provided">{{ metric.value }} <small>{{ metric.unit || '' }}</small></strong>
        <span v-else class="missing">未填写</span>
      </div>
    </div>
    <ul v-if="preview.failedMetrics?.length" class="preview-notes">
      <li v-for="(item, index) in preview.failedMetrics" :key="index">{{ item.name }}：{{ item.reason }}</li>
    </ul>
    </template>
    <p v-if="!nested" class="preview-footnote">{{ preview.saved ? '化验记录已保存，可在化验查询中查看。' : outcomeUnknown ? '保存结果尚未核实，请勿重复录入。' : invalidated ? '这份预览已失效，请在对话中重新生成。' : '请核对以上信息。可在对话中补充或修改，本次尚未保存。' }}</p>
    <el-button v-if="action" type="primary" class="confirm-entry-button" @click="emit('card-action', action)">{{ control?.uncertain ? '核实保存结果' : '核对并保存' }}</el-button>
  </section>
</template>

<style scoped>
.assay-entry-preview { padding: 14px; border: 1px solid #e2e8f0; border-radius: 12px; background: #f8fafc; color: #334155; }
.preview-heading { display: flex; gap: 12px; justify-content: space-between; align-items: center; margin-bottom: 14px; }
.preview-heading > span { color: #a16207; background: #fef3c7; padding: 3px 8px; border-radius: 6px; white-space: nowrap; font-size: 12px; }
.preview-heading > span.saved { color: #15803d; background: #dcfce7; }
.confirm-entry-button { margin-top: 12px; }
.group-summary { margin: 0 0 12px; font-size: 13px; color: #64748b; }
.group-products { display: grid; gap: 12px; }
dl { margin: 0 0 12px; font-size: 13px; }
dl > div, .metric-row { display: flex; gap: 12px; justify-content: space-between; padding: 6px 0; }
dt, .missing { color: #64748b; }
dd { margin: 0; text-align: right; }
.metric-list { border-top: 1px solid #e2e8f0; border-bottom: 1px solid #e2e8f0; padding: 6px 0; font-size: 13px; }
small { font-weight: 400; color: #64748b; }
.preview-notes, .preview-footnote { font-size: 12px; line-height: 1.7; margin: 12px 0 0; }
.preview-notes { padding-left: 18px; color: #9a3412; }
.preview-footnote { color: #64748b; }
</style>
