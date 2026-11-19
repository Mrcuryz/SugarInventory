<script setup>
import { computed, inject, onBeforeUnmount, reactive, ref, watch } from 'vue'
import { reviewBookKey, reviewSummary } from './productImportSelection.mjs'
import { reviewNextRows } from './productImportBatch.mjs'
import ProductImportReviewCard from './ProductImportReviewCard.vue'
import { Document, UploadFilled, Refresh, Delete } from '@element-plus/icons-vue'
import { productImportApi } from '@/api/productImport'
import { createProductImportFlow, importBusy, mappingError, PRODUCT_FIELDS } from './productImportFlow.mjs'

const props = defineProps({ modelValue: Boolean, agentSessionId: String, available: Boolean, matchingEnabled: Boolean })
const emit = defineEmits(['update:modelValue', 'attachments-changed', 'review-row', 'removed'])
const state = reactive({ phase: 'idle', file: null, sheets: [], sheet: '', mapping: {}, attachments: [],
  selectedRef: '', source: null, error: '', recoveryRequired: false })
let flow
const fileInput = ref(null)
const clock = ref(Date.now())
const timer = setInterval(() => { clock.value = Date.now() }, 1000)
onBeforeUnmount(() => { clearInterval(timer); flow?.dispose() })
const batchRunning = ref(false), pauseAfterRow = ref(false), batchMessage = ref('')
const expanded = ref(null)
const busy = computed(() => batchRunning.value || importBusy(state.phase))
const sheet = computed(() => state.sheets.find(item => item.name === state.sheet))
const mappingProblem = computed(() => mappingError(sheet.value, state.mapping))
const selected = computed(() => state.attachments.find(item => item.reference === state.selectedRef))
const expired = item => Number(item?.expiresAt || 0) <= clock.value
const book = inject(reviewBookKey, null)
const summary = computed(() => reviewSummary((book?.entries.value || []).filter(e =>
  e.reference === selected.value?.reference && !expired(e)), selected.value?.rowCount || 0))
watch(() => [props.modelValue, props.agentSessionId, state.selectedRef], () => {
  expanded.value = null; batchMessage.value = ''
  if (batchRunning.value) pauseAfterRow.value = true
})
const runBatch = async limit => {
  if (busy.value || !summary.value.pending || !book?.reviewBatchRow || !selected.value) return
  const receipt = selected.value, sessionId = props.agentSessionId
  const active = () => props.modelValue && props.available && props.matchingEnabled &&
    props.agentSessionId === sessionId && selected.value?.reference === receipt.reference && receipt.expiresAt > Date.now()
  if (!active()) return
  batchRunning.value = true; pauseAfterRow.value = false; batchMessage.value = '正在读取表格行号…'
  const planned = Math.min(limit, summary.value.pending)
  try {
    const result = await reviewNextRows({ receipt, limit: planned, active,
      read: productImportApi(sessionId).read, shouldPause: () => pauseAfterRow.value,
      has: rowNumber => book.entries.value.some(e => e.sessionId === sessionId && e.reference === receipt.reference && e.rowNumber === rowNumber),
      review: rowNumber => book.reviewBatchRow({ sessionId, reference: receipt.reference, expiresAt: receipt.expiresAt, rowNumber }),
      progress: ({ completed, rowNumber }) => { batchMessage.value = `正在核对第 ${rowNumber} 行（本次 ${completed + 1}/${planned}）` }
    })
    if (active()) batchMessage.value = `${result.paused ? '已暂停' : '本次核对结束'}，核对了 ${result.completed} 行。`
  } catch (e) {
    if (active()) batchMessage.value = e?.message || '读取失败，请继续重试。'
  } finally { batchRunning.value = false }
}
const toggleAll = checked => { for (const row of summary.value.rows) row.entry.included = !!row.target && checked }
const removeSheet = async reference => { if (await flow?.remove(reference)) emit('removed', reference) }
const columns = computed(() => PRODUCT_FIELDS.filter(field => state.source?.rows?.some(row =>
  Object.prototype.hasOwnProperty.call(row.values || {}, field.value))))
const remaining = item => expired(item) ? '已过期' : `${Math.ceil((item.expiresAt - clock.value) / 60000)}分钟后过期`
watch(() => [props.modelValue, props.agentSessionId, props.available], async ([opened, sessionId, available]) => {
  flow?.dispose()
  flow = null
  if (!opened || !sessionId || !available) {
    if (opened) emit('update:modelValue', false)
    return
  }
  flow = createProductImportFlow(productImportApi(sessionId), next => {
    Object.assign(state, next)
    emit('attachments-changed', next.attachments)
  })
  Object.assign(state, flow.state())
  await flow.refresh()
}, { immediate: true })
const pickFile = () => { if (!busy.value && !state.recoveryRequired) fileInput.value?.click() }
const fileChanged = async event => {
  const file = event.target.files?.[0]
  event.target.value = '' // Allow choosing the same corrected file again.
  if (file) await flow?.inspect(file)
}
const close = () => { if (!busy.value) emit('update:modelValue', false) }
</script>

<template>
  <el-dialog :model-value="modelValue" title="产品表格" width="min(860px, 96vw)" top="5vh"
    class="product-import-dialog" append-to-body :close-on-click-modal="false" :close-on-press-escape="!busy"
    :show-close="!busy" :before-close="close">
    <div class="import-body" :aria-busy="busy">
      <el-alert :title="matchingEnabled ? '可与产品目录核对并勾选差异。本次核对不保存产品。' : '当前仅查看表格内容，尚未开放产品对比。'" type="info" :closable="false" show-icon />
      <el-alert v-if="state.error" :title="state.error" type="warning" :closable="false" show-icon role="alert" />

      <section class="import-section">
        <div class="section-heading">
          <h3>选择表格</h3><span>Excel · 每次最多500行</span>
        </div>
        <input ref="fileInput" class="native-file-input" type="file" accept=".xls,.xlsx" aria-label="选择产品Excel文件" :disabled="busy || state.recoveryRequired" @change="fileChanged" />
        <button class="file-picker" type="button" :disabled="busy || state.recoveryRequired" @click="pickFile">
          <el-icon class="upload-icon"><UploadFilled /></el-icon>
          <span>{{ state.file?.name || '选择Excel文件' }}</span>
          <small>{{ state.phase === 'inspecting' ? '正在读取工作表…' : '支持 .xls、.xlsx，文件不超过10MB' }}</small>
        </button>
        <template v-if="state.file && state.sheets.length">
          <div class="sheet-selector">
            <label for="product-import-sheet">工作表</label>
            <el-select id="product-import-sheet" aria-label="工作表" :model-value="state.sheet" :disabled="busy"
              placeholder="请选择工作表" @update:model-value="flow.chooseSheet">
              <el-option v-for="item in state.sheets" :key="item.name" :value="item.name" :disabled="item.hidden"
                :label="item.hidden ? `${item.name}（已隐藏）` : item.name" />
            </el-select>
          </div>
          <div v-if="sheet" class="mapping-section">
            <h4>确认每列对应的内容</h4>
            <div class="mapping-grid">
              <label v-for="column in sheet.columns" :key="column.index" class="mapping-item">
                <span>{{ column.title || `第${column.index + 1}列（无列名）` }}</span>
                <el-select :aria-label="`第${column.index + 1}列对应内容`" :model-value="state.mapping[column.index] || ''"
                  :disabled="busy" placeholder="请选择对应内容" @update:model-value="value => flow.setMapping(column.index, value)">
                  <el-option v-for="field in PRODUCT_FIELDS" :key="field.value" :value="field.value" :label="field.label" />
                </el-select>
              </label>
            </div>
            <p v-if="mappingProblem" class="mapping-warning" role="status">{{ mappingProblem }}</p>
            <p class="subtle-note">空白单元格会保留为空，不按0处理，也不用于清空现有产品信息。</p>
          </div>
          <div class="file-actions">
            <el-button :disabled="busy" @click="flow.clearFile()">取消选择</el-button>
            <el-button type="primary" :disabled="busy || !!mappingProblem || state.recoveryRequired" :loading="state.phase === 'uploading'" @click="flow.upload()">上传并核对</el-button>
          </div>
        </template>
      </section>

      <section class="import-section">
        <div class="section-heading">
          <h3>本次会话的表格<span class="count-badge">{{ state.attachments.length }}</span></h3>
          <el-button link type="primary" :icon="Refresh" :disabled="busy" @click="flow.refresh()">刷新列表</el-button>
        </div>
        <p v-if="!state.attachments.length" class="empty-note">{{ state.phase === 'refreshing' ? '正在读取表格列表…' : '还没有上传表格' }}</p>
        <div v-for="item in state.attachments" :key="item.reference" class="attachment-row" :class="{ selected: item.reference === state.selectedRef }">
          <el-icon class="document-icon"><Document /></el-icon>
          <button type="button" class="attachment-details" :disabled="busy || expired(item)" @click="flow.read(item.reference)">
            <strong>{{ item.fileName }}</strong><span>{{ item.sheet }} · {{ item.rowCount }}行 · {{ remaining(item) }}</span>
          </button>
          <el-button link type="primary" :disabled="busy || expired(item)" @click="flow.read(item.reference)">查看</el-button>
          <el-button link type="danger" :icon="Delete" :disabled="busy || expired(item)" :aria-label="`移除${item.fileName}`" @click="removeSheet(item.reference)">移除</el-button>
        </div>
      </section>

      <section v-if="selected" class="import-section source-section">
        <div class="section-heading"><h3>表格原始内容</h3></div>
        <el-alert v-if="expired(selected)" title="这份表格已过期，请刷新列表后重新上传。" type="warning" :closable="false" />
        <template v-else-if="state.source">
          <section v-if="matchingEnabled && book" class="review-summary">
            <h4>整表核对汇总</h4>
            <p>共 {{ selected.rowCount }} 行 · 未核对 {{ summary.pending }} 行 · 已选且无冲突 {{ summary.ready }} 项</p>
            <p class="subtle-note">暂停后可以继续，已核对的行会跳过。刷新网页或结束会话会清除本次核对和选择。</p>
            <div v-if="book.reviewBatchRow" class="batch-actions">
              <template v-if="!batchRunning && summary.pending">
                <el-button type="primary" size="small" :disabled="busy" @click="runBatch(5)">核对接下来 {{ Math.min(summary.pending, 5) }} 行</el-button>
                <el-button v-if="summary.pending > 5" size="small" :disabled="busy" @click="runBatch(summary.pending)">核对全部剩余 {{ summary.pending }} 行</el-button>
              </template>
              <el-button v-if="batchRunning" size="small" :disabled="pauseAfterRow" @click="pauseAfterRow = true">{{ pauseAfterRow ? '正在等待本行结束…' : '暂停核对' }}</el-button>
              <span role="status" aria-live="polite">{{ batchMessage }}</span>
            </div>
            <template v-if="summary.rows.length">
              <label><input type="checkbox" :checked="summary.rows.some(r => r.target) && summary.rows.every(r => !r.target || r.entry.included)"
                :disabled="!summary.rows.some(r => r.target)" @change="toggleAll($event.target.checked)"> 全选已确定的新增和修改</label>
              <div v-for="r in summary.rows" :key="r.entry.rowNumber">
                <label><input type="checkbox" v-model="r.entry.included" :disabled="!r.target">
                  第 {{ r.entry.rowNumber }} 行 · {{ r.entry.card.fields[0].sourceValues?.productName }} · {{ r.label }}{{ r.target ? '：' + r.target : '' }}</label>
                <small v-if="r.conflicts.length" role="alert">与第 {{ r.conflicts.join('、') }} 行的目标同名，请取消一项或重新核对。</small>
                <el-button link type="primary" :aria-expanded="expanded === r.entry.rowNumber"
                  @click="expanded = expanded === r.entry.rowNumber ? null : r.entry.rowNumber">{{ expanded === r.entry.rowNumber ? '收起详情' : '查看差异与选择' }}</el-button>
                <ProductImportReviewCard v-if="expanded === r.entry.rowNumber" :card="r.entry.card" />
              </div>
            </template>
          </section>
          <el-table :data="state.source.rows" size="small" stripe border max-height="300" empty-text="这一页没有记录">
            <el-table-column prop="rowNumber" label="表格行号" width="85" fixed />
            <el-table-column v-if="matchingEnabled" label="操作" width="84" fixed="right">
              <template #default="{ row }"><el-button link type="primary" :disabled="busy || expired(selected)"
                @click="emit('review-row', { reference: selected.reference, expiresAt: selected.expiresAt, rowNumber: row.rowNumber, sessionId: agentSessionId })">核对差异</el-button></template>
            </el-table-column>
            <el-table-column v-for="column in columns" :key="column.value" :prop="`values.${column.value}`" :label="column.label" min-width="140">
              <template #default="{ row }"><span>{{ row.values?.[column.value] ?? '—' }}</span></template>
            </el-table-column>
          </el-table>
          <el-pagination layout="total, prev, pager, next" :total="state.source.attachment.rowCount" :page-size="50"
            :current-page="state.source.page" :disabled="busy" @current-change="page => flow.read(state.selectedRef, page)" />
        </template>
        <p v-else class="empty-note">{{ state.phase === 'loading' ? '正在读取表格内容…' : '内容未加载，请点击“查看”重试' }}</p>
      </section>
    </div>
    <template #footer><el-button :disabled="busy" @click="close">关闭</el-button></template>
  </el-dialog>
</template>

<style scoped>
.import-body { display: grid; grid-template-columns: minmax(0, 1fr); min-width: 0; gap: 16px; }
.import-section { min-width: 0; padding: 18px; border: 1px solid #e6ebf2; border-radius: 12px; background: white; }
.review-summary { margin-bottom: 14px; padding: 12px; background: #f6f9ff; border-radius: 8px; max-height: 260px; overflow: auto; }
.review-summary h4, .review-summary p { margin: 0 0 8px; }
.batch-actions { display: flex; flex-wrap: wrap; align-items: center; gap: 6px; margin-bottom: 8px; }
.batch-actions .el-button { margin: 0; }
.review-summary small { display: block; padding-left: 22px; color: #975000; }
.section-heading { display: flex; align-items: center; justify-content: space-between; gap: 12px; margin-bottom: 14px; }
.section-heading h3 { margin: 0; color: #20324d; font-size: 15px; }
.section-heading > span, .subtle-note, .empty-note { color: #748197; font-size: 12px; }
.count-badge { margin-left: 8px; padding: 2px 7px; border-radius: 20px; color: #3562be; background: #edf3ff; font-size: 12px; }
.native-file-input { position: absolute; width: 1px; height: 1px; opacity: 0; overflow: hidden; }
.file-picker { width: 100%; display: grid; justify-items: center; gap: 8px; padding: 22px 14px; border: 1px dashed #b9cef0; border-radius: 10px; background: #f7faff; color: #2c4f87; cursor: pointer; font: inherit; overflow-wrap: anywhere; }
.file-picker:hover:not(:disabled) { background: #eff5ff; border-color: #477ce5; }
.file-picker:disabled { cursor: default; opacity: .65; }
.file-picker small { font-size: 12px; color: #748197; }
.upload-icon { font-size: 29px; color: #4c80de; }
.sheet-selector { display: flex; align-items: center; gap: 14px; margin: 16px 0; }
.sheet-selector > label { flex: 0 0 48px; color: #4c5b70; font-size: 13px; }
.sheet-selector .el-select { flex: 1; }
.mapping-section h4 { font-size: 13px; font-weight: 500; color: #4c5b70; margin: 18px 0 12px; }
.mapping-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 12px 18px; }
.mapping-item { display: grid; gap: 6px; font-size: 12px; color: #68778d; overflow-wrap: anywhere; }
.mapping-warning { color: #a76513; font-size: 12px; }
.file-actions { display: flex; justify-content: flex-end; margin-top: 16px; }
.attachment-row { display: flex; align-items: center; gap: 12px; padding: 12px; border: 1px solid #e7edf5; border-radius: 8px; margin-top: 8px; }
.attachment-row.selected { background: #f6f9ff; border-color: #b9cef0; }
.document-icon { color: #4b7edb; font-size: 22px; flex-shrink: 0; }
.attachment-details { display: grid; gap: 5px; flex: 1; min-width: 0; text-align: left; border: 0; background: none; font: inherit; cursor: pointer; }
.attachment-details strong { color: #263b58; font-size: 13px; overflow-wrap: anywhere; }
.attachment-details span { color: #7b899c; font-size: 12px; }
.attachment-details:disabled { cursor: default; }
.source-section .el-pagination { margin-top: 14px; justify-content: flex-end; }
:global(.product-import-dialog .el-dialog__body) { max-height: 73vh; overflow: auto; padding-top: 8px; }
@media (max-width: 600px) {
  .import-section { padding: 12px; }
  .mapping-grid { grid-template-columns: 1fr; }
  .attachment-row { flex-wrap: wrap; gap: 8px; }
  .attachment-details { flex-basis: calc(100% - 40px); }
  .section-heading { align-items: flex-start; flex-wrap: wrap; }
}
</style>
