<script setup>
import { computed, inject, ref, watch } from 'vue'
import { actionFor, reviewBookKey } from './productImportSelection.mjs'
import { PRODUCT_FIELDS } from './productImportFlow.mjs'

const props = defineProps({ card: { type: Object, required: true } })
const data = computed(() => props.card.fields?.[0] || {})
const book = inject(reviewBookKey, null)
const entry = computed(() => book?.forCard(props.card))
const localChoice = ref(null), localAck = ref(false)
const choice = computed({ get: () => book ? entry.value?.choice ?? null : localChoice.value, set: v => {
  if (entry.value) { entry.value.choice = v; entry.value.included = !!actionFor(entry.value).target }
  else if (!book) localChoice.value = v
} })
const checkedCatalog = computed({ get: () => book ? !!entry.value?.ack : localAck.value, set: v => {
  if (entry.value) entry.value.ack = v
  else if (!book) localAck.value = v
} })
watch(() => props.card, () => { choice.value = null; checkedCatalog.value = false })
watch(checkedCatalog, checked => { if (!checked && choice.value === 'new' && !data.value.allowCreate) choice.value = null })
const label = key => PRODUCT_FIELDS.find(f => f.value === key)?.label || '未识别字段'
const display = value => value == null || value === '' ? '未填写' : value === true ? '是' : value === false ? '否' : value
const description = values => PRODUCT_FIELDS.filter(f => f.value !== 'productName' && values?.[f.value] != null)
  .map(f => f.label + '：' + display(values[f.value])).join(' · ')
</script>
<template>
  <section class="import-review">
    <strong>{{ data.statusLabel }}</strong>
    <div v-if="data.sourceValues"><b>表格产品：{{ data.sourceValues.productName || '未填写' }}</b>
      <small>{{ description(data.sourceValues) }}</small></div>
    <p v-if="data.message">{{ data.message }}</p>
    <p v-if="data.reason">匹配依据：{{ data.reason }}</p>
    <p v-if="data.duplicateRows?.length">请核对第 {{ data.duplicateRows.join('、') }} 行。</p>
    <p v-if="data.partial">仅展示相近候选，不能据此判断为新产品。</p>
    <fieldset :disabled="!!book && !entry">
      <legend>本行如何处理</legend>
      <label v-for="(item, index) in data.options" :key="item.ref" :class="{ blocked: item.blocked }">
        <input v-model="choice" type="radio" :value="index" :disabled="item.blocked" />
        <span><b>匹配已有产品：{{ item.values.productName }}</b>
          <small>{{ description(item.values) }}</small>
          <small>{{ item.suggested ? '建议核对 · ' : '' }}{{ item.label }}</small>
          <small v-for="d in item.changes" :key="d.field">{{ label(d.field) }}：{{ display(d.before) }} → {{ display(d.after) }}</small>
        </span>
      </label>
      <label v-if="data.allowManualCreate"><input v-model="checkedCatalog" type="checkbox" />我已核对产品目录中的其他名称，确认没有同款</label>
      <label v-if="data.allowCreate || data.allowManualCreate"><input v-model="choice" type="radio" value="new" :disabled="!data.allowCreate && !checkedCatalog" />作为新产品</label>
      <small v-if="data.missingFields?.length && !data.partial && !data.options?.some(o => o.label === '没有差异' && o.suggested)">如需新增，表格还需填写：{{ data.missingFields.map(label).join('、') }}。</small>
      <label><input v-model="choice" type="radio" value="skip" />忽略此行</label>
    </fieldset>
    <p role="status">{{ choice === null ? '尚未选择' : choice === 'skip' ? '已选择忽略此行' : choice === 'new' ? '已选择作为新产品' : '已选择匹配：' + data.options[choice]?.values.productName }} · 仅本页核对，未保存产品</p>
  </section>
</template>
<style scoped>
.import-review { display: grid; gap: .65rem; color: #283950; overflow-wrap: anywhere; }
.import-review p { margin: 0; }
.import-review small { display: block; line-height: 1.5; color: #56677b; }
.import-review fieldset { display: grid; min-width: 0; gap: .4rem; border: 1px solid #d9e1ed; border-radius: 8px; padding: .6rem; }
.import-review label { display: flex; align-items: baseline; gap: .4rem; cursor: pointer; padding: .3rem; }
.import-review .blocked { background: #faf6f1; cursor: not-allowed; }
.import-review p[role=status] { color: #425e82; font-size: .85rem; }
</style>
