<script setup>
import { computed, nextTick, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { createAgentSession, revokeAgentSession, sendAgentMessage } from '@/api/agent'
import { useAuthStore } from '@/stores/auth'

const visible = ref(false)
const loadingSession = ref(false)
const sending = ref(false)
const session = ref(null)
const input = ref('')
const messages = ref([])
const scrollRef = ref(null)
const pendingSelection = ref(null)
const debugMode = ref(false)
const authStore = useAuthStore()

const sessionStatus = computed(() => session.value?.status || '未启动')
const isAdmin = computed(() => ['ADMIN', 'SUPER_ADMIN'].includes(session.value?.roleCode || authStore.roleCode))

const open = async () => {
  visible.value = true
  if (!session.value) {
    await startSession()
  }
}

const startSession = async () => {
  loadingSession.value = true
  try {
    const result = await createAgentSession({
      clientType: 'WEB',
      requestedScopes: ['mcp:warehouse:read'],
      mcpTransport: 'STDIO'
    })
    session.value = result.data
    messages.value.push({
      role: 'assistant',
      content: 'AI 助手已连接当前登录用户。你可以直接问库存、库位、托盘和化验状态。'
    })
  } finally {
    loadingSession.value = false
  }
}

const send = async (options = {}) => {
  const text = (options.message || input.value).trim()
  if (!text || sending.value) return
  if (!session.value?.agentSessionId) {
    await startSession()
  }
  const selectedOption = options.selectedOption || null
  if (options.showUserMessage !== false) {
    messages.value.push({ role: 'user', content: text })
  }
  input.value = ''
  sending.value = true
  await scrollToBottom()
  try {
    const result = await sendAgentMessage(session.value.agentSessionId, {
      message: text,
      pageContext: {
        path: window.location.pathname,
        selectedOption,
        recentMessages: messages.value.slice(-8).map(item => ({
          role: item.role,
          content: item.content
        }))
      }
    })
    const data = result.data
    messages.value.push({
      role: 'assistant',
      content: data.answer,
      options: data.options || [],
      needsUserSelection: data.needsUserSelection,
      toolCalls: data.toolCalls || []
    })
    if (data.session) {
      session.value = data.session
    }
  } catch (error) {
    messages.value.push({ role: 'assistant', content: '请求失败，未将错误解释为空数据。' })
  } finally {
    pendingSelection.value = null
    sending.value = false
    await scrollToBottom()
  }
}

const cleanOptionLabel = (label) => String(label || '')
  .replace(/\s*[(（]#\d+[)）]\s*/g, ' ')
  .replace(/\s+/g, ' ')
  .trim()

const chooseOption = async (option) => {
  if (!option?.displayLabel || option.supported === false) return
  const visibleLabel = cleanOptionLabel(option.displayLabel)
  const selectedOption = {
    optionType: option.optionType,
    displayLabel: visibleLabel,
    rawDisplayLabel: option.displayLabel,
    productId: option.productId,
    warehouseId: option.warehouseId
  }
  pendingSelection.value = selectedOption
  await send({
    message: '用户选择了候选项',
    selectedOption,
    showUserMessage: false
  })
}

const optionTypeLabel = (optionType) => {
  const labels = {
    PRODUCT_TYPE_GROUP: '产品大类',
    EXACT_PRODUCT_NAME_GROUP: '产品名称组',
    SINGLE_PRODUCT: '具体产品',
    SINGLE_WAREHOUSE: '具体库位'
  }
  return labels[optionType] || '候选项'
}

const handleBeforeClose = async (done) => {
  const closed = await closeSession()
  if (closed && done) done()
}

const closeSession = async () => {
  if (!session.value?.agentSessionId) {
    visible.value = false
    return true
  }
  const confirmed = await ElMessageBox.confirm('关闭后将撤销当前 Agent 会话，是否继续？', '关闭 AI 助手', {
    type: 'warning',
    confirmButtonText: '关闭',
    cancelButtonText: '取消'
  }).catch(() => false)
  if (!confirmed) return false
  try {
    await revokeAgentSession(session.value.agentSessionId, { revokedReason: 'USER_CLOSED_ASSISTANT' })
    ElMessage.success('Agent 会话已撤销')
  } finally {
    session.value = null
    pendingSelection.value = null
    messages.value = []
    debugMode.value = false
    visible.value = false
  }
  return true
}

const scrollToBottom = async () => {
  await nextTick()
  const el = scrollRef.value?.wrapRef
  if (el) {
    el.scrollTop = el.scrollHeight
  }
}

defineExpose({ open })
</script>

<template>
  <el-drawer
    v-model="visible"
    title="AI 助手"
    direction="rtl"
    size="420px"
    :before-close="handleBeforeClose"
    class="agent-assistant-drawer"
  >
    <div class="assistant-shell" v-loading="loadingSession">
      <div class="session-bar">
        <span>会话：{{ sessionStatus }}</span>
        <span v-if="session?.expiresAt">到期：{{ session.expiresAt }}</span>
        <el-button v-if="isAdmin" text size="small" class="debug-toggle" @click="debugMode = !debugMode">
          {{ debugMode ? '隐藏调试' : '调试' }}
        </el-button>
      </div>

      <el-scrollbar ref="scrollRef" class="message-list">
        <div
          v-for="(item, index) in messages"
          :key="index"
          class="message-row"
          :class="item.role"
        >
          <div class="message-bubble">
            <div class="message-text">{{ item.content }}</div>
            <div v-if="item.options?.length" class="option-card-list">
              <button
                v-for="option in item.options"
                :key="`${option.optionType}-${option.productId || option.warehouseId || option.displayLabel}`"
                class="option-card"
                :class="{ disabled: option.supported === false }"
                type="button"
                :disabled="option.supported === false || sending"
                @click="chooseOption(option)"
              >
                <span class="option-title">{{ cleanOptionLabel(option.displayLabel) }}</span>
                <span class="option-meta">{{ optionTypeLabel(option.optionType) }}</span>
                <span v-if="option.supported === false" class="option-note">暂不支持直接查询</span>
              </button>
            </div>
            <div v-if="debugMode && item.toolCalls?.length" class="tool-summary">
              <div
                v-for="call in item.toolCalls"
                :key="`${call.toolName}-${call.durationMs}-${call.resultCode}`"
                class="tool-summary-line"
              >
                <span>{{ call.toolName }}</span>
                <span>{{ call.resultCode }}</span>
                <span v-if="call.errorCode">{{ call.errorCode }}</span>
              </div>
            </div>
          </div>
        </div>
      </el-scrollbar>

      <div class="composer">
        <el-input
          v-model="input"
          type="textarea"
          :rows="3"
          maxlength="500"
          show-word-limit
          placeholder="例如：查黄冰糖（袋）库存"
          @keydown.ctrl.enter.prevent="send"
        />
        <div class="composer-actions">
          <el-button @click="closeSession">关闭</el-button>
          <el-button type="primary" :loading="sending" @click="send">发送</el-button>
        </div>
      </div>
    </div>
  </el-drawer>
</template>

<style scoped lang="scss">
.assistant-shell {
  height: 100%;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.session-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  color: var(--app-text-tertiary);
  font-size: 12px;
}

.debug-toggle {
  margin-left: auto;
}

.message-list {
  flex: 1;
  min-height: 0;
  border: 1px solid var(--app-border-soft);
  border-radius: 8px;
  background: #fafafa;
  padding: 12px;
}

.message-row {
  display: flex;
  margin-bottom: 12px;

  &.user {
    justify-content: flex-end;
  }

  &.assistant {
    justify-content: flex-start;
  }
}

.message-bubble {
  max-width: 88%;
  padding: 10px 12px;
  border-radius: 8px;
  background: #ffffff;
  color: var(--app-text);
  line-height: 1.55;
  box-shadow: 0 2px 8px rgba(29, 33, 41, 0.06);
}

.message-row.user .message-bubble {
  background: var(--app-primary-light);
  color: var(--app-primary);
}

.message-text {
  white-space: pre-wrap;
  word-break: break-word;
}

.option-card-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin-top: 10px;
}

.option-card {
  width: 100%;
  display: grid;
  grid-template-columns: 1fr auto;
  gap: 4px 10px;
  align-items: center;
  border: 1px solid var(--app-border-soft);
  border-radius: 8px;
  background: #ffffff;
  color: var(--app-text);
  padding: 9px 10px;
  text-align: left;
  cursor: pointer;
  transition: border-color 0.15s ease, background-color 0.15s ease;

  &:hover:not(.disabled) {
    border-color: var(--app-primary);
    background: var(--app-primary-light);
  }

  &.disabled {
    cursor: not-allowed;
    color: var(--app-text-tertiary);
    background: #f7f8fa;
  }
}

.option-title {
  min-width: 0;
  font-weight: 600;
  overflow-wrap: anywhere;
}

.option-meta {
  color: var(--app-text-tertiary);
  font-size: 12px;
}

.option-note {
  grid-column: 1 / -1;
  color: var(--app-text-tertiary);
  font-size: 12px;
}

.tool-summary {
  margin-top: 10px;
  padding-top: 8px;
  border-top: 1px dashed var(--app-border-soft);
  color: var(--app-text-tertiary);
  font-size: 12px;
}

.tool-summary-line {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.composer {
  flex: 0 0 auto;
}

.composer-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  margin-top: 10px;
}
</style>


