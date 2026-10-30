<script setup>
import { computed, reactive, ref, watch, onBeforeUnmount } from 'vue'
import { createAssayEntryPreview, createAssayGroupEntryPreview, confirmAssayEntry, executeAssayEntry, getAssayEntryResult, cancelAssayEntry } from '@/api/agent'
import AssayEntryPreviewCard from './AssayEntryPreviewCard.vue'
import { createAssayEntryFlow } from './assayEntryFlow.mjs'
import { assayEntryInput, assayEntryControl, previewAsCard } from './assayEntryPresentation.mjs'
import { browserAssayRecoveryJournal } from './assayEntryRecovery.mjs'
const props = defineProps({ modelValue: Boolean, agentSessionId: String, card: Object, recoveryOwner: String })
const emit = defineEmits(['update:modelValue', 'completed'])
const state = reactive({ phase: 'idle', preview: null, result: null, message: '' })
let flow = null
let opening = 0
let recoveryIdentity = null
let recoveryContext = null
const busy = computed(() => ['loading', 'saving', 'checking', 'cancelling'].includes(state.phase))
const clock = ref(Date.now())
const timer = setInterval(() => { clock.value = Date.now() }, 1000)
onBeforeUnmount(() => clearInterval(timer))
const expired = computed(() => state.preview?.expiresAt && Date.parse(`${state.preview.expiresAt}+08:00`) <= clock.value)
const displayCard = computed(() => previewAsCard({ ...(state.result?.details || state.preview?.preview || {}), saveOutcomeUnknown: state.phase === 'uncertain' }))
watch(() => props.modelValue, async opened => {
  if (!opened) return
  const epoch = ++opening
  const sessionId = props.agentSessionId
  const owner = props.recoveryOwner
  recoveryContext = { owner, sessionId }
  recoveryIdentity = null
  const card = props.card
  const control = assayEntryControl(card)
  const input = assayEntryInput(card)
  if (!sessionId || !control || !input) { Object.assign(state, { phase: 'invalid', preview: null, result: null, message: '预览信息不完整，请重新查询。' }); return }
  const unwrap = promise => promise.then(response => response.data)
  flow = createAssayEntryFlow({
    preview: value => unwrap(value.groupRef ? createAssayGroupEntryPreview(sessionId, value) : createAssayEntryPreview(sessionId, value)),
    confirm: ref => unwrap(confirmAssayEntry(sessionId, ref)),
    execute: (ref, credentials) => unwrap(executeAssayEntry(sessionId, ref, credentials)),
    result: ref => unwrap(getAssayEntryResult(sessionId, ref)),
    cancel: ref => unwrap(cancelAssayEntry(sessionId, ref))
  }, next => {
    if (epoch !== opening) return
    Object.assign(state, next)
    if (next.preview) control.preview = next.preview
    control.uncertain = next.phase === 'uncertain'
    if (next.phase === 'saved') {
      try { if (recoveryIdentity) browserAssayRecoveryJournal().forget(owner, recoveryIdentity) } catch { /* Keep reference for later recovery. */ }
      emit('completed', { card, result: next.result })
    }
    if (next.phase === 'invalid') control.invalidated = true
  })
  await flow.load(input, control.preview)
})
const close = async () => {
  if (busy.value) return
  if (flow && !await flow.cancel()) return
  if (state.message === '已取消' && recoveryIdentity) {
    try { browserAssayRecoveryJournal().forget(recoveryContext.owner, recoveryIdentity) } catch { /* Keep reference for later recovery. */ }
  }
  emit('update:modelValue', false)
}
const save = async () => {
  if (expired.value || busy.value || state.phase !== 'ready') return
  try {
    recoveryIdentity = { sessionId: recoveryContext.sessionId, previewRef: state.preview.previewRef }
    browserAssayRecoveryJournal().remember(recoveryContext.owner, recoveryIdentity)
  } catch (e) { state.message = e.message || '无法保留待核实记录，本次没有提交保存。'; return }
  await flow?.save()
}
</script>

<template>
  <el-dialog :model-value="modelValue" title="核对化验记录" width="min(620px, 94vw)" top="6vh" class="assay-entry-confirm-dialog" append-to-body
    :close-on-click-modal="false" :close-on-press-escape="false" :show-close="!busy" :before-close="close">
    <div v-loading="busy">
      <el-alert v-if="state.message" :title="state.message" :type="state.phase === 'saved' ? 'success' : 'warning'" :closable="false" show-icon class="assay-notice" />
      <AssayEntryPreviewCard v-if="state.preview" :card="displayCard" />
      <p v-if="state.phase === 'ready'" class="assay-confirm-note">{{ state.preview?.preview?.entryKind === 'GROUP_V1' ? `确认后将为以上全部 ${state.preview.preview.productCount} 个产品各新增一版化验记录；任一产品失败，整组不保存。` : '确认后将新增一版化验记录，不会覆盖已有记录。' }}请核对产品、采样日期及检测值。</p>
      <p v-if="expired && state.phase !== 'saved'" class="assay-confirm-note">本次预览已过期，请关闭后重新查询。</p>
    </div>
    <template #footer>
      <el-button :disabled="busy" @click="close">{{ state.phase === 'saved' ? '完成' : '取消' }}</el-button>
      <el-button v-if="state.phase === 'uncertain'" type="primary" @click="flow.check()">核实保存结果</el-button>
      <el-button v-else-if="state.phase !== 'saved' && state.phase !== 'invalid'" type="primary" :loading="busy"
        :disabled="busy || state.phase !== 'ready' || expired" @click="save">确认保存</el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
:global(.assay-entry-confirm-dialog .el-dialog__body) { max-height: calc(88vh - 150px); overflow-y: auto; }
.assay-notice { margin-bottom: 14px; }
.assay-confirm-note { margin: 14px 0 0; color: #64748b; font-size: 13px; line-height: 1.7; }
</style>
