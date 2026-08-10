<script setup>
import { computed, reactive, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { activateFixedProductQrCodes } from '@/api/palletCode'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  palletCodes: { type: Array, default: () => [] },
  productLabel: { type: String, default: '' },
  defaultWarehouseName: { type: String, default: '' },
  defaultSide: { type: String, default: '左' }
})

const emit = defineEmits(['update:modelValue', 'created'])

const localDate = () => {
  const now = new Date()
  const year = now.getFullYear()
  const month = String(now.getMonth() + 1).padStart(2, '0')
  const day = String(now.getDate()).padStart(2, '0')
  return `${year}-${month}-${day}`
}

const form = reactive({ productionDate: localDate(), submitting: false })
const visible = computed({
  get: () => props.modelValue,
  set: value => emit('update:modelValue', value)
})
const normalizedCodes = computed(() => [...new Set((props.palletCodes || [])
  .map(code => String(code || '').trim().toUpperCase())
  .filter(Boolean))])

watch(() => props.modelValue, value => {
  if (value) form.productionDate = localDate()
})

const downloadPdf = blob => {
  if (!(blob instanceof Blob)) return
  const url = URL.createObjectURL(blob)
  const anchor = document.createElement('a')
  anchor.href = url
  anchor.download = `fixed-product-qrcodes-activated-${Date.now()}.pdf`
  document.body.appendChild(anchor)
  anchor.click()
  anchor.remove()
  URL.revokeObjectURL(url)
}

const submit = async () => {
  if (!form.productionDate) {
    ElMessage.warning('请选择生产日期')
    return
  }
  if (!normalizedCodes.value.length || normalizedCodes.value.length > 20) {
    ElMessage.warning('固定二维码范围无效，请重新选择')
    return
  }
  await ElMessageBox.confirm(
    `确认使用所选 ${normalizedCodes.value.length} 个空闲固定二维码创建成品入库待处理任务？本步骤不会写入库存。`,
    '确认创建待入库任务',
    { type: 'warning', confirmButtonText: '创建任务', cancelButtonText: '取消' }
  )
  form.submitting = true
  try {
    const response = await activateFixedProductQrCodes({
      codes: normalizedCodes.value,
      productionDate: form.productionDate
    })
    downloadPdf(response?.data)
    ElMessage.success(`已创建 ${normalizedCodes.value.length} 条待入库任务，正在重新核对任务资格`)
    visible.value = false
    emit('created', {
      palletCodes: normalizedCodes.value,
      productLabel: props.productLabel,
      productionDate: form.productionDate,
      defaultWarehouseName: props.defaultWarehouseName,
      defaultSide: props.defaultSide
    })
  } finally {
    form.submitting = false
  }
}
</script>

<template>
  <el-dialog v-model="visible" title="创建固定二维码待入库任务" width="620px" append-to-body destroy-on-close>
    <el-alert
      type="warning"
      :closable="false"
      show-icon
      title="这是新建任务，不是处理已有待入库任务"
    >
      <template #default>
        确认后会把所选空闲固定二维码启用为成品入库待处理任务，并生成标签 PDF；此步骤不会确认入库或写入库存。
      </template>
    </el-alert>

    <el-descriptions :column="1" border class="creation-summary">
      <el-descriptions-item label="产品">{{ productLabel || '所选产品' }}</el-descriptions-item>
      <el-descriptions-item label="二维码数量">{{ normalizedCodes.length }} 个</el-descriptions-item>
      <el-descriptions-item label="二维码">{{ normalizedCodes.join('、') }}</el-descriptions-item>
      <el-descriptions-item label="后续目标库位">{{ defaultWarehouseName || '稍后确认' }}</el-descriptions-item>
    </el-descriptions>

    <el-form label-width="90px" class="creation-form">
      <el-form-item label="生产日期" required>
        <el-date-picker
          v-model="form.productionDate"
          type="date"
          value-format="YYYY-MM-DD"
          placeholder="选择生产日期"
          style="width: 100%"
        />
      </el-form-item>
    </el-form>

    <template #footer>
      <el-button :disabled="form.submitting" @click="visible = false">取消</el-button>
      <el-button type="primary" :loading="form.submitting" @click="submit">创建待入库任务</el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.creation-summary,
.creation-form {
  margin-top: 18px;
}
</style>
