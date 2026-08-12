<template>
  <el-dialog v-model="dialogVisible" title="打印助手设置" width="560px" destroy-on-close>
    <div class="assistant-status">
      <el-alert
        :type="assistantOnline && assistantAuthorized ? 'success' : 'warning'"
        :closable="false"
        :title="assistantStatusTitle"
        :description="assistantStatusDescription"
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
        <el-form-item label="连接密钥">
          <el-input
            v-model="accessKeyInput"
            type="password"
            show-password
            autocomplete="off"
            placeholder="从本机标签打印助手复制"
          />
        </el-form-item>
        <el-form-item label="默认打印机">
          <el-select
            v-model="selectedPrinterName"
            clearable
            filterable
            placeholder="不设置时使用系统默认打印机"
            style="width: 100%"
            :disabled="!assistantAuthorized"
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
      <el-button :disabled="!assistantOnline" :loading="connectLoading" @click="handleConnect">连接助手</el-button>
      <el-button :disabled="!assistantAuthorized" :loading="testLoading" @click="handleTestPrint">测试打印</el-button>
      <el-button type="primary" :disabled="!assistantAuthorized" :loading="saveLoading" @click="handleSave">
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
  getPrinterAssistantAccessKey,
  getPrinterAssistantHealth,
  printTestLabel,
  setPrinterAssistantAccessKey,
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
const connectLoading = ref(false)
const assistantOnline = ref(false)
const assistantAuthorized = ref(false)
const accessKeyInput = ref('')
const printerOptions = ref([])
const selectedPrinterName = ref('')
const config = reactive({
  defaultPrinterName: '',
  systemDefaultPrinterName: ''
})

const assistantStatusTitle = computed(() => {
  if (!assistantOnline.value) return '未检测到本机标签打印助手'
  if (!assistantAuthorized.value) return '已检测到打印助手，请输入连接密钥'
  return '本机标签打印助手已安全连接'
})

const assistantStatusDescription = computed(() => {
  if (!assistantOnline.value) return '请先启动本机桌面版程序；未启动时页面仍可回退导出 PDF。'
  if (!assistantAuthorized.value) return '请在桌面版标签打印助手中复制 Web 连接密钥；密钥只保存在当前浏览器会话。'
  return '已认证连接 127.0.0.1:9527，可把标签提交到本地打印机。'
})

const resetProtectedState = () => {
  assistantAuthorized.value = false
  printerOptions.value = []
  selectedPrinterName.value = ''
  config.defaultPrinterName = ''
  config.systemDefaultPrinterName = ''
}

const loadProtectedState = async () => {
  const [printersRes, configRes] = await Promise.all([
    getLocalPrinters(),
    getLocalPrinterConfig()
  ])
  printerOptions.value = Array.isArray(printersRes.data) ? printersRes.data : []
  config.defaultPrinterName = configRes.data?.defaultPrinterName || ''
  config.systemDefaultPrinterName = configRes.data?.systemDefaultPrinterName || ''
  selectedPrinterName.value = config.defaultPrinterName || ''
  assistantAuthorized.value = true
}

const loadState = async () => {
  loading.value = true
  assistantOnline.value = false
  resetProtectedState()
  try {
    await getPrinterAssistantHealth()
    assistantOnline.value = true
    accessKeyInput.value = getPrinterAssistantAccessKey()
    resetProtectedState()
    if (accessKeyInput.value) {
      await loadProtectedState()
    }
  } catch (error) {
    resetProtectedState()
    ElMessage.warning(buildAssistantErrorMessage(error))
  } finally {
    loading.value = false
  }
}

const handleConnect = async () => {
  connectLoading.value = true
  try {
    setPrinterAssistantAccessKey(accessKeyInput.value)
    await loadProtectedState()
    ElMessage.success('打印助手已安全连接')
  } catch (error) {
    resetProtectedState()
    ElMessage.error(buildAssistantErrorMessage(error))
  } finally {
    connectLoading.value = false
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
