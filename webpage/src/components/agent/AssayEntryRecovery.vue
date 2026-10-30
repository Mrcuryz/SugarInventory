<script setup>
import { ref, watch, onMounted, onBeforeUnmount } from 'vue'
import { recoverAssayEntry } from '@/api/agent'
import { browserAssayRecoveryJournal, isSettledAssayResult } from './assayEntryRecovery.mjs'
import { previewAsCard } from './assayEntryPresentation.mjs'
import AssayEntryPreviewCard from './AssayEntryPreviewCard.vue'
const props = defineProps({ owner: String })
const emit = defineEmits(['pending'])
const rows = ref([])
const error = ref('')
const busy = ref(false)
const results = ref([])
const refresh = () => {
  try { rows.value = props.owner ? browserAssayRecoveryJournal().read(props.owner) : []; error.value = '' }
  catch (e) { error.value = e.message || '无法读取待核实记录，请勿重复录入。' }
  emit('pending', Boolean(error.value || rows.value.length))
}
watch(() => props.owner, () => { rows.value = []; results.value = []; refresh() }, { immediate: true })
onMounted(() => { window.addEventListener('storage', refresh); window.addEventListener('assay-entry-recovery-changed', refresh) })
onBeforeUnmount(() => { window.removeEventListener('storage', refresh); window.removeEventListener('assay-entry-recovery-changed', refresh) })
const recover = async row => {
  if (busy.value) return
  const owner = props.owner
  busy.value = true
  try {
    const result = (await recoverAssayEntry(row.sessionId, row.previewRef)).data
    if (!isSettledAssayResult(result)) throw new Error('结果仍未明确，请稍后重新核实，不要重复录入。')
    // Do not show a previous user's record if the account changed during the request.
    if (props.owner !== owner) return
    results.value.push(result)
    browserAssayRecoveryJournal().forget(owner, row)
    refresh()
  } catch (e) { if (props.owner === owner) error.value = e.msg || e.message || '核实失败，请恢复网络或联系管理员，暂勿重复录入。' }
  finally { busy.value = false }
}
</script>

<template>
  <section v-if="rows.length || error || results.length" class="assay-recovery" aria-label="化验保存结果核实">
    <p v-if="rows.length">有化验保存结果需要核实。已保存的会显示原记录，尚未保存的会取消本次操作，不会重新保存。</p>
    <p v-if="error" role="alert">{{ error }}</p>
    <el-button v-for="(row, index) in rows" :key="row.previewRef" :loading="busy" :disabled="busy" @click="recover(row)">
      {{ rows.length > 1 ? `核实第${index + 1}项并结束` : '核实结果并结束' }}
    </el-button>
    <template v-for="(result, index) in results" :key="index">
      <p>{{ result.saved ? '已找回保存成功的化验记录，没有重复保存。' : '本次操作已结束，没有保存化验。可以重新录入。' }}</p>
      <AssayEntryPreviewCard v-if="result.saved" :card="previewAsCard(result.details)" />
    </template>
  </section>
</template>

<style scoped>
.assay-recovery { padding: 12px; margin: 8px 0; border: 1px solid #cbd5e1; border-radius: 10px; background: #fff; font-size: 13px; line-height: 1.6; }
.assay-recovery p { margin: 0 0 10px; }
</style>
