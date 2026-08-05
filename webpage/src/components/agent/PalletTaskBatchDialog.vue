<script setup>
import { computed, ref, watch } from 'vue'
import dayjs from 'dayjs'
import { ElMessage } from 'element-plus'
import { getProductList } from '@/api/product'
import { getWarehouse } from '@/api/warehouse'
import {
  confirmFinishOutTasks,
  confirmPalletInBatch,
  confirmSemiOutTasks,
  confirmTransferTasks,
  pagePalletTasks
} from '@/api/palletCode'
import {
  BIZ_SCENE_MAP,
  SIDE_OPTIONS,
  TASK_STATUS_MAP,
  UNIT_OPTIONS,
  getDictLabel
} from '@/utils/palletCodeDict'

const props = defineProps({
  modelValue: {
    type: Boolean,
    default: false
  },
  batchAction: {
    type: String,
    default: ''
  },
  taskGroupLabel: {
    type: String,
    default: ''
  },
  palletCodes: {
    type: Array,
    default: () => []
  }
})

const emit = defineEmits(['update:modelValue', 'completed'])

const visible = computed({
  get: () => props.modelValue,
  set: value => emit('update:modelValue', value)
})
const loading = ref(false)
const submitting = ref(false)
const rows = ref([])
const warehouseList = ref([])
const productList = ref([])
const remark = ref('')

const operations = {
  semiOutConfirm: {
    title: '批量确认半成品出库',
    api: confirmSemiOutTasks,
    successMessage: '批量确认半成品出库成功',
    matches: row => row.taskType === 'OUT' && row.bizScene === 'DIRECT_OUT' && row.taskStatus === 'PENDING'
  },
  finishOutConfirm: {
    title: '批量确认成品出库',
    api: confirmFinishOutTasks,
    successMessage: '批量确认成品出库成功',
    matches: row => row.taskType === 'OUT' && row.bizScene === 'FINISH_OUT' && row.taskStatus === 'PENDING'
  },
  transferConfirm: {
    title: '批量确认调拨',
    api: confirmTransferTasks,
    successMessage: '批量确认调拨成功',
    matches: row => row.taskType === 'TRANSFER' && row.taskStatus === 'PENDING'
  }
}

const isInbound = computed(() => props.batchAction === 'confirmIn')
const isTransfer = computed(() => props.batchAction === 'transferConfirm')
const operation = computed(() => operations[props.batchAction])
const dialogTitle = computed(() => {
  if (isInbound.value) return `批量确认${props.taskGroupLabel || '入库'}`
  return operation.value?.title || '批量处理任务'
})
const productMap = computed(() => {
  const result = {}
  productList.value.forEach(product => {
    result[product.id] = product
  })
  return result
})

const normalizeCodes = codes => [...new Set(
  (codes || []).map(code => String(code || '').trim().toUpperCase()).filter(Boolean)
)]

const isProductionOrderOutputTask = row => Boolean(row?.productionOutputCodeId)
const productionOutputUnit = row => String(row?.productionOutputUnit || '0') === '1' ? '1' : '0'
const productionOutputQuantity = row => {
  if (!isProductionOrderOutputTask(row)) return null
  if (productionOutputUnit(row) === '1') {
    return Number(row.productionOutputPieces || row.productionOutputQuantity || 0) || 1
  }
  return Number(row.productionOutputQuantity || 1) || 1
}

const getPiecesLimitByProductId = productId => {
  const product = productMap.value[productId]
  const value = Number(product?.piecesPerPallet ?? product?.pieces_per_pallet)
  return Number.isFinite(value) && value > 0 ? value : null
}

const canEditQuantity = row => row.unit === '1' && !row.quantityLocked
const getQuantityMax = row => {
  if (row.unit === '0') return 1
  const limit = getPiecesLimitByProductId(row.productId)
  return limit ? Math.max(limit - 1, 1) : undefined
}

const normalizeQuantityByUnit = row => {
  if (row.quantityLocked) {
    return
  }
  if (row.unit === '0') {
    row.quantity = 1
    return
  }
  if (!row.quantity || row.quantity < 1) row.quantity = 1
  const limit = getPiecesLimitByProductId(row.productId)
  if (limit && row.quantity >= limit) row.quantity = Math.max(limit - 1, 1)
}

const toInboundRow = row => {
  const quantityLocked = isProductionOrderOutputTask(row)
  return {
    code: row.code,
    productId: row.productId,
    productName: row.productName,
    warehouseName: row.targetWarehouseName || '',
    entryDate: row.productionDate || dayjs().format('YYYY-MM-DD'),
    side: row.targetSide || '左',
    quantity: quantityLocked ? productionOutputQuantity(row) : 1,
    unit: quantityLocked ? productionOutputUnit(row) : '0',
    quantityLocked,
    productionOutputCodeId: row.productionOutputCodeId || null,
    remark: ''
  }
}

const validateInboundRows = () => {
  for (const row of rows.value) {
    if (!row.warehouseName) {
      ElMessage.warning(`请为托盘 ${row.code} 选择入库库位`)
      return false
    }
    if (row.quantityLocked) {
      continue
    }
    if (row.unit === '0') {
      row.quantity = 1
      continue
    }
    const limit = getPiecesLimitByProductId(row.productId)
    if (!limit) {
      ElMessage.error(`托盘 ${row.code} 未找到每板件数配置，不能按件提交`)
      return false
    }
    if (!row.quantity || row.quantity < 1 || row.quantity >= limit) {
      ElMessage.error(`托盘 ${row.code} 的件数应大于 0 且少于每板件数 ${limit}`)
      return false
    }
  }
  return true
}

const reset = () => {
  rows.value = []
  warehouseList.value = []
  productList.value = []
  remark.value = ''
  loading.value = false
  submitting.value = false
}

const close = () => {
  visible.value = false
}

const prepareDialog = async () => {
  const codes = normalizeCodes(props.palletCodes)
  if (!codes.length) {
    ElMessage.warning('请先选择需要处理的任务')
    close()
    return
  }
  if (!isInbound.value && !operation.value) {
    ElMessage.warning('当前任务类型暂不支持批量处理')
    close()
    return
  }

  loading.value = true
  try {
    const requests = [pagePalletTasks({
      codes: codes.join(','),
      status: 'PENDING',
      pageNum: 1,
      pageSize: Math.min(Math.max(codes.length, 10), 100)
    })]
    if (isInbound.value) {
      requests.push(getWarehouse({}), getProductList({}))
    }
    const [taskResponse, warehouseResponse, productResponse] = await Promise.all(requests)
    const currentRows = taskResponse.data?.records || []
    const codeSet = new Set(codes)
    const exactRows = currentRows.filter(row => codeSet.has(String(row.code || '').toUpperCase()))
    const validRows = isInbound.value
      ? exactRows.filter(row => ['SEMI_IN', 'FINISH_IN'].includes(row.taskType) && row.taskStatus === 'PENDING')
      : exactRows.filter(row => operation.value.matches(row))

    if (validRows.length !== codes.length) {
      ElMessage.warning(`有 ${codes.length - validRows.length} 条任务状态或类型已变化，已从本次处理中移除`)
    }
    if (!validRows.length) {
      ElMessage.warning('所选任务已无法处理，请重新查询任务状态')
      close()
      return
    }

    if (isInbound.value) {
      warehouseList.value = Array.isArray(warehouseResponse.data)
        ? warehouseResponse.data
        : (warehouseResponse.data?.records || [])
      productList.value = Array.isArray(productResponse.data)
        ? productResponse.data
        : (productResponse.data?.records || [])
      rows.value = validRows.map(toInboundRow)
    } else {
      rows.value = validRows
    }
  } catch (error) {
    ElMessage.error(error?.message || '任务处理弹窗加载失败，请稍后重试')
    close()
  } finally {
    loading.value = false
  }
}

const submit = async () => {
  if (!rows.value.length || submitting.value) return
  submitting.value = true
  try {
    if (isInbound.value) {
      if (!validateInboundRows()) return
      await confirmPalletInBatch({
        items: rows.value.map(row => ({
          code: row.code,
          warehouseName: row.warehouseName,
          entryDate: row.entryDate,
          side: row.side,
          quantity: row.quantity,
          unit: row.unit,
          remark: row.remark
        }))
      })
      ElMessage.success('批量确认入库成功')
    } else {
      await operation.value.api({
        codes: rows.value.map(row => row.code),
        remark: remark.value
      })
      ElMessage.success(operation.value.successMessage)
    }
    emit('completed', {
      batchAction: props.batchAction,
      palletCodes: rows.value.map(row => row.code)
    })
    close()
  } finally {
    submitting.value = false
  }
}

watch(() => props.modelValue, opened => {
  if (opened) prepareDialog()
})
</script>

<template>
  <el-dialog
    v-model="visible"
    :title="dialogTitle"
    :width="isInbound ? 'min(1180px, 96vw)' : 'min(760px, 94vw)'"
    append-to-body
    destroy-on-close
    :close-on-click-modal="false"
    @closed="reset"
  >
    <div v-loading="loading" class="agent-task-batch-dialog">
      <el-alert
        title="已按托盘码重新核对当前任务；只有仍处于待处理状态且类型一致的任务会出现在这里。"
        type="info"
        :closable="false"
        show-icon
      />

      <el-table v-if="isInbound" :data="rows" border max-height="520">
        <el-table-column prop="code" label="二维码" width="150" fixed="left"/>
        <el-table-column prop="productName" label="产品" min-width="150"/>
        <el-table-column label="入库库位" min-width="170">
          <template #default="{ row }">
            <el-select v-model="row.warehouseName" filterable allow-create default-first-option placeholder="选择库位" style="width: 100%">
              <el-option v-for="item in warehouseList" :key="item.id" :label="item.warehouseName" :value="item.warehouseName"/>
            </el-select>
          </template>
        </el-table-column>
        <el-table-column label="入库日期" width="155">
          <template #default="{ row }">
            <el-date-picker v-model="row.entryDate" value-format="YYYY-MM-DD" type="date" style="width: 100%"/>
          </template>
        </el-table-column>
        <el-table-column label="侧" width="95">
          <template #default="{ row }">
            <el-select v-model="row.side">
              <el-option v-for="item in SIDE_OPTIONS" :key="item.value" :label="item.label" :value="item.value"/>
            </el-select>
          </template>
        </el-table-column>
        <el-table-column label="单位" width="95">
          <template #default="{ row }">
            <el-select v-model="row.unit" :disabled="row.quantityLocked" @change="normalizeQuantityByUnit(row)">
              <el-option v-for="item in UNIT_OPTIONS" :key="item.value" :label="item.label" :value="item.value"/>
            </el-select>
          </template>
        </el-table-column>
        <el-table-column label="数量" width="125">
          <template #default="{ row }">
            <el-input-number
              v-model="row.quantity"
              :min="1"
              :max="getQuantityMax(row)"
              :disabled="row.quantityLocked || !canEditQuantity(row)"
              controls-position="right"
              style="width: 100%"
              @change="normalizeQuantityByUnit(row)"
            />
            <div v-if="row.quantityLocked" class="table-cell-tip">按订单核销数量</div>
          </template>
        </el-table-column>
        <el-table-column label="每板上限" width="95">
          <template #default="{ row }">{{ getPiecesLimitByProductId(row.productId) || '-' }}</template>
        </el-table-column>
        <el-table-column label="备注" min-width="160">
          <template #default="{ row }"><el-input v-model="row.remark" placeholder="备注"/></template>
        </el-table-column>
      </el-table>

      <template v-else>
        <el-table :data="rows" border max-height="360">
          <el-table-column prop="code" label="二维码" width="160"/>
          <el-table-column prop="productName" label="产品" min-width="150"/>
          <el-table-column v-if="!isTransfer" prop="bizScene" label="业务场景" width="150">
            <template #default="{ row }">{{ getDictLabel(BIZ_SCENE_MAP, row.bizScene) }}</template>
          </el-table-column>
          <el-table-column v-if="isTransfer" prop="targetWarehouseName" label="目标库位" min-width="150"/>
          <el-table-column v-if="isTransfer" prop="targetSide" label="目标侧" width="100"/>
          <el-table-column prop="taskStatus" label="任务状态" width="120">
            <template #default="{ row }">{{ getDictLabel(TASK_STATUS_MAP, row.taskStatus) }}</template>
          </el-table-column>
        </el-table>
        <el-form label-width="80px" class="batch-remark-form">
          <el-form-item label="备注"><el-input v-model="remark" type="textarea" :rows="2"/></el-form-item>
        </el-form>
      </template>
    </div>

    <template #footer>
      <el-button :disabled="submitting" @click="close">取消</el-button>
      <el-button type="primary" :loading="submitting" :disabled="loading || !rows.length" @click="submit">提交</el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.agent-task-batch-dialog {
  min-height: 140px;
}

.agent-task-batch-dialog .el-alert {
  margin-bottom: 14px;
}

.batch-remark-form {
  margin-top: 16px;
}

.table-cell-tip {
  margin-top: 3px;
  color: var(--app-text-tertiary);
  font-size: 11px;
}
</style>
