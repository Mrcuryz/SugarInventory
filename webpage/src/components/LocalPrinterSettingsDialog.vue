<template>
  <el-dialog v-model="dialogVisible" title="打印助手设置" width="560px" destroy-on-close>
    <div class="assistant-status">
      <el-alert
        :type="assistantOnline ? 'success' : 'warning'"
        :closable="false"
        :title="assistantOnline ? '本机标签打印助手在线' : '未检测到本机标签打印助手'"
        :description="assistantOnline ? '已连接 127.0.0.1:9527，可直接把标签提交到本地打印机。' : '请先启动 PrinterAssistantApplication 或其桌面版程序；未启动时页面仍可回退导出 PDF。'"
      />
    </div>

    <div class="dialog-body" v-loading="loading">
      <el-descriptions :column="1" border>
        <el-descriptions-item label="服务地址">127.0.0.1:9527</el-descriptions-item>
        <el-descriptions-item label="系统默认打印机">{{ config.systemDefaultPrinterName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="当前默认打印机">{{ config.defaultPrinterName || '未设置，打印时使用系统默认打印机' }}</el-descriptions-item>
        <el-descriptions-item label="已识别打印机">{{ printerOptions.length ? `${printerOptions.length} 台` : '0 台' }}</el-descriptions-item>
      </el-descriptions>

      <el-form label-width="110px" class="printer-form">
        <el-form-item label="默认打印机">
          <el-select
            v-model="selectedPrinterName"
            clearable
            filterable
            placeholder="不设置时使用系统默认打印机"
            style="width: 100%"
            :disabled="!assistantOnline"
          >
            <el-option
              v-for="item in printerOptions"
              :key="item"
              :label="item"
              :value="item"
            />
          </el-select>
        </el-form-item>
      </el-form>
    </div>

    <template #footer>
      <el-button @click="loadState">刷新</el-button>
      <el-button :disabled="!assistantOnline" :loading="testLoading" @click="handleTestPrint">测试打印</el-button>
      <el-button type="primary" :disabled="!assistantOnline" :loading="saveLoading" @click="handleSave">
        保存默认打印机
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import {computed, reactive, ref, watch} from 'vue'
import {ElMessage} from 'element-plus'
import {
  buildAssistantErrorMessage,
  getLocalPrinterConfig,
  getLocalPrinters,
  getPrinterAssistantHealth,
  printTestLabel,
  saveDefaultLocalPrinter
} from '@/api/localPrinter'

const props = defineProps({
  modelValue: {
    type: Boolean,
    default: false
  }
})

const emit = defineEmits(['update:modelValue'])

const dialogVisible = computed({
  get: () => props.modelValue,
  set: (value) => emit('update:modelValue', value)
})

const loading = ref(false)
const saveLoading = ref(false)
const testLoading = ref(false)
const assistantOnline = ref(false)
const printerOptions = ref([])
const selectedPrinterName = ref('')
const config = reactive({
  defaultPrinterName: '',
  systemDefaultPrinterName: ''
})

const resetState = () => {
  assistantOnline.value = false
  printerOptions.value = []
  selectedPrinterName.value = ''
  config.defaultPrinterName = ''
  config.systemDefaultPrinterName = ''
}

const loadState = async () => {
  loading.value = true
  try {
    await getPrinterAssistantHealth()
    assistantOnline.value = true
    const [printersRes, configRes] = await Promise.all([
      getLocalPrinters(),
      getLocalPrinterConfig()
    ])
    printerOptions.value = Array.isArray(printersRes.data) ? printersRes.data : []
    config.defaultPrinterName = configRes.data?.defaultPrinterName || ''
    config.systemDefaultPrinterName = configRes.data?.systemDefaultPrinterName || ''
    selectedPrinterName.value = config.defaultPrinterName || ''
  } catch (error) {
    resetState()
    ElMessage.warning(buildAssistantErrorMessage(error))
  } finally {
    loading.value = false
  }
}

const handleSave = async () => {
  saveLoading.value = true
  try {
    const res = await saveDefaultLocalPrinter(selectedPrinterName.value || null)
    config.defaultPrinterName = res.data?.defaultPrinterName || ''
    config.systemDefaultPrinterName = res.data?.systemDefaultPrinterName || ''
    selectedPrinterName.value = config.defaultPrinterName || ''
    ElMessage.success(config.defaultPrinterName ? '默认打印机已保存' : '已改为使用系统默认打印机')
  } catch (error) {
    ElMessage.error(buildAssistantErrorMessage(error))
  } finally {
    saveLoading.value = false
  }
}

const handleTestPrint = async () => {
  testLoading.value = true
  try {
    const res = await printTestLabel({
      printerName: selectedPrinterName.value || null
    })
    ElMessage.success(res.message || '测试标签已提交到打印机')
  } catch (error) {
    ElMessage.error(buildAssistantErrorMessage(error))
  } finally {
    testLoading.value = false
  }
}

watch(() => props.modelValue, (value) => {
  if (value) {
    loadState()
  }
})
</script>

<style scoped>
.assistant-status {
  margin-bottom: 16px;
}

.dialog-body {
  min-height: 180px;
}

.printer-form {
  margin-top: 16px;
}
</style>
