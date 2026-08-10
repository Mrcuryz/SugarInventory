<script setup>
import { computed, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  confirmAndExecuteFinishInbound,
  getPendingFinishInboundExecutionPreview
} from '@/api/agent'
import { formatDateTime } from '@/utils/dateTime'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  agentSessionId: { type: String, default: '' },
  palletCodes: { type: Array, default: () => [] }
})

const emit = defineEmits(['update:modelValue', 'completed'])
const visible = computed({
  get: () => props.modelValue,
  set: value => emit('update:modelValue', value)
})
const loading = ref(false)
const submitting = ref(false)
const pending = ref(null)

const loadPending = async () => {
  if (!props.agentSessionId || !props.palletCodes.length) {
    ElMessage.warning('当前预览缺少可确认的任务范围')
    visible.value = false
    return
  }
  loading.value = true
  try {
    const response = await getPendingFinishInboundExecutionPreview(
      props.agentSessionId,
      props.palletCodes
    )
    pending.value = response.data
  } catch (error) {
    ElMessage.error(error?.message || '安全预览已失效，请重新生成')
    visible.value = false
  } finally {
    loading.value = false
  }
}

const execute = async () => {
  if (!pending.value?.previewRef || submitting.value) return
  const accepted = await ElMessageBox.confirm(
    `确认将 ${pending.value.items?.length || 0} 个托盘按上方内容正式入库？该操作会修改库存和任务状态。`,
    '确认执行成品入库',
    {
      type: 'warning',
      confirmButtonText: '确认入库',
      cancelButtonText: '取消'
    }
  ).catch(() => false)
  if (!accepted) return
  submitting.value = true
  try {
    const response = await confirmAndExecuteFinishInbound(
      props.agentSessionId,
      pending.value.previewRef
    )
    const result = response.data || {}
    ElMessage.success(result.replayed ? '入库已完成，本次返回原提交结果' : '成品入库已完成')
    emit('completed', {
      palletCodes: result.palletCodes || props.palletCodes,
      completedAt: result.completedAt || ''
    })
    visible.value = false
  } catch (error) {
    ElMessage.error(error?.message || '本次成品入库未完成，请按提示重新预览')
  } finally {
    submitting.value = false
  }
}

const reset = () => {
  pending.value = null
  loading.value = false
  submitting.value = false
}

watch(() => props.modelValue, opened => {
  if (opened) loadPending()
})
</script>

<template>
  <el-dialog
    v-model="visible"
    title="核对并确认成品入库"
    width="min(900px, 94vw)"
    append-to-body
    destroy-on-close
    :close-on-click-modal="false"
    @closed="reset"
  >
    <div v-loading="loading" class="finish-inbound-confirm-dialog">
      <el-alert
        title="以下内容来自服务端不可变预览；点击确认时还会重新核对任务、托盘、库位和生产关联。"
        type="warning"
        :closable="false"
        show-icon
      />
      <template v-if="pending">
        <div class="confirm-summary">
          <strong>{{ pending.statusLabel }}</strong>
          <span>有效至 {{ formatDateTime(pending.expiresAt) }}</span>
        </div>
        <el-table :data="pending.items || []" border max-height="440">
          <el-table-column prop="palletCode" label="托盘码" min-width="140" />
          <el-table-column prop="productName" label="产品" min-width="150" />
          <el-table-column prop="warehouseName" label="目标库位" min-width="120" />
          <el-table-column prop="entryDate" label="入库日期" width="120" />
          <el-table-column prop="side" label="侧" width="70" />
          <el-table-column label="数量" width="110">
            <template #default="{ row }">{{ row.quantity }} {{ row.unitLabel }}</template>
          </el-table-column>
          <el-table-column prop="remark" label="备注" min-width="130">
            <template #default="{ row }">{{ row.remark || '-' }}</template>
          </el-table-column>
        </el-table>
      </template>
    </div>
    <template #footer>
      <el-button :disabled="submitting" @click="visible = false">取消</el-button>
      <el-button
        type="danger"
        :loading="submitting"
        :disabled="loading || !pending"
        @click="execute"
      >
        确认入库
      </el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.finish-inbound-confirm-dialog {
  min-height: 160px;
}

.finish-inbound-confirm-dialog .el-alert {
  margin-bottom: 14px;
}

.confirm-summary {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
  color: var(--app-text-secondary);
}

.confirm-summary strong {
  color: #b42318;
}
</style>
