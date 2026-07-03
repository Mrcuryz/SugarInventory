<script setup>
import { computed, nextTick, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  cancelAgentMessage,
  createAgentSession,
  revokeAgentSession,
  streamAgentInterruptResume,
  streamAgentMessage
} from '@/api/agent'
import { useAuthStore } from '@/stores/auth'

const visible = ref(false)
const loadingSession = ref(false)
const sending = ref(false)
const cancelling = ref(false)
const session = ref(null)
const input = ref('')
const messages = ref([])
const scrollRef = ref(null)
const pendingSelection = ref(null)
const debugMode = ref(false)
const authStore = useAuthStore()
let activeStreamController = null
let activeAssistantMessage = null

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
  const assistantMessage = reactive({
    role: 'assistant',
    content: '',
    progress: '正在连接 Agent……',
    options: [],
    cards: [],
    needsUserSelection: false,
    toolCalls: [],
    eventIds: new Set(),
    lastSequence: 0,
    interruptId: null,
    interruptKind: null,
    resumeToken: null,
    expiresAt: null,
    finishReason: null
  })
  activeAssistantMessage = assistantMessage
  messages.value.push(assistantMessage)
  await scrollToBottom()
  try {
    activeStreamController = new AbortController()
    const onEvent = (event) => {
      applyStreamEvent(assistantMessage, event)
      scrollToBottom()
    }
    if (selectedOption?.interruptId) {
      await streamAgentInterruptResume(session.value.agentSessionId, selectedOption.interruptId, {
        resumeToken: selectedOption.resumeToken,
        action: selectedOption.action || 'SELECT_OPTION',
        clientRequestId: selectedOption.clientRequestId,
        selection: {
          optionId: selectedOption.optionId
        }
      }, onEvent, {
        signal: activeStreamController.signal
      })
    } else {
      await streamAgentMessage(session.value.agentSessionId, {
        message: text,
        pageContext: {
          path: window.location.pathname,
          debug: debugMode.value,
          selectedOption,
          recentMessages: messages.value
            .filter(item => item.content)
            .slice(-8)
            .map(item => ({
              role: item.role,
              content: item.content
            }))
        }
      }, onEvent, {
        signal: activeStreamController.signal
      })
    }
  } catch (error) {
    if (error.name === 'AbortError') {
      assistantMessage.progress = ''
      assistantMessage.content = assistantMessage.content || '已取消本次生成。'
      assistantMessage.finishReason = 'cancelled'
    } else {
      assistantMessage.progress = ''
      assistantMessage.content = '请求失败，未将错误解释为空数据。'
      assistantMessage.finishReason = 'error'
    }
  } finally {
    pendingSelection.value = null
    sending.value = false
    activeStreamController = null
    activeAssistantMessage = null
    await scrollToBottom()
  }
  return assistantMessage.finishReason
}

const applyStreamEvent = (message, event) => {
  if (!event?.type || message.eventIds.has(event.eventId)) return
  if (message.cancelled && !['cancelled', 'message_end'].includes(event.type)) return
  if (event.messageId) message.messageId = event.messageId
  if (Number.isFinite(event.sequence) && event.sequence < message.lastSequence) return
  message.eventIds.add(event.eventId)
  message.lastSequence = Math.max(message.lastSequence, event.sequence || 0)
  const payload = event.payload || {}
  switch (event.type) {
    case 'message_start':
      message.progress = '正在处理……'
      break
    case 'progress':
    case 'heartbeat':
      message.progress = payload.text || payload.message || message.progress
      break
    case 'clarification':
      message.content = payload.prompt || message.content
      message.options = payload.options || []
      message.interruptId = payload.interruptId || null
      message.interruptKind = payload.interruptKind || 'CLARIFICATION'
      message.resumeToken = payload.resumeToken || null
      message.expiresAt = payload.expiresAt || null
      message.needsUserSelection = true
      message.progress = '等待你选择'
      break
    case 'card':
      message.cards.push(payload)
      break
    case 'text_delta':
      message.content += payload.text || ''
      break
    case 'error':
      message.content = payload.message || '请求失败，未将错误解释为空数据。'
      message.progress = ''
      break
    case 'fallback':
      message.progress = payload.message || '已切换到基础查询模式。'
      break
    case 'tool_start':
    case 'tool_end':
    case 'debug':
      message.toolCalls.push(payload)
      break
    case 'cancelled':
      message.cancelled = true
      message.content = payload.message || '已取消本次生成。'
      message.progress = ''
      break
    case 'message_end':
      message.finishReason = payload.finishReason || 'completed'
      message.interruptId = payload.interruptId || message.interruptId
      message.interruptKind = payload.interruptKind || message.interruptKind
      if (message.finishReason === 'completed') {
        message.progress = ''
      }
      if (message.finishReason === 'clarification_required' || message.finishReason === 'interrupt_required') {
        const hasActionableOptions = (message.options || []).some(option => option.supported !== false)
        if (message.interruptId && hasActionableOptions) {
          message.needsUserSelection = true
          message.progress = '等待你选择'
        } else {
          message.needsUserSelection = false
          message.progress = ''
        }
      }
      break
    default:
      break
  }
}

const cancelStream = async () => {
  if (!activeStreamController) return
  cancelling.value = true
  if (activeAssistantMessage) {
    activeAssistantMessage.cancelled = true
    activeAssistantMessage.progress = '正在取消……'
  }
  try {
    if (session.value?.agentSessionId && activeAssistantMessage?.messageId) {
      await cancelAgentMessage(session.value.agentSessionId, activeAssistantMessage.messageId)
    }
  } catch {
    // The local abort still prevents stale stream events from reaching the UI.
  } finally {
    activeStreamController.abort()
    cancelling.value = false
  }
}

const cleanOptionLabel = (label) => String(label || '')
  .replace(/\s*[(（]#\d+[)）]\s*/g, ' ')
  .replace(/\s+/g, ' ')
  .trim()

const chooseOption = async (option, sourceMessage) => {
  if (!option?.displayLabel || option.supported === false) return
  const clientRequestId = window.crypto?.randomUUID
    ? window.crypto.randomUUID()
    : `resume_${Date.now()}_${Math.random().toString(16).slice(2)}`
  const selectedOption = {
    optionId: option.optionId,
    optionType: option.optionType,
    displayLabel: cleanOptionLabel(option.displayLabel),
    rawDisplayLabel: option.displayLabel,
    interruptId: sourceMessage?.interruptId,
    resumeToken: sourceMessage?.resumeToken,
    action: 'SELECT_OPTION',
    clientRequestId
  }
  if (sourceMessage) {
    sourceMessage.needsUserSelection = false
    sourceMessage.progress = '正在继续查询……'
  }
  pendingSelection.value = selectedOption
  const finishReason = await send({
    message: '用户选择了候选项',
    selectedOption,
    showUserMessage: false
  })
  if (sourceMessage) {
    sourceMessage.progress = finishReason === 'completed' ? '已处理' : ''
  }
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

const visibleCards = (item) => (item.cards || []).filter(card => card?.cardType !== 'candidate_selection')

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
    size="min(420px, 100vw)"
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
            <div v-if="item.progress" class="message-progress">{{ item.progress }}</div>
            <div class="message-text">{{ item.content }}</div>
            <div v-if="visibleCards(item).length" class="business-card-list">
              <div
                v-for="(card, cardIndex) in visibleCards(item)"
                :key="`${card.cardType || 'card'}-${card.title || cardIndex}`"
                class="business-card"
              >
                <div class="business-card-title">{{ card.title || '查询结果' }}</div>
                <div v-if="card.fields?.length" class="business-card-fields">
                  <div
                    v-for="(field, fieldIndex) in card.fields"
                    :key="`${field.label || field.name || fieldIndex}-${field.value || fieldIndex}`"
                    class="business-card-field"
                  >
                    <span>{{ field.label || field.name }}</span>
                    <strong>{{ field.value }}</strong>
                  </div>
                </div>
              </div>
            </div>
            <div v-if="item.options?.length" class="option-card-list">
              <button
                v-for="option in item.options"
                :key="`${option.optionId || option.optionType}-${option.displayLabel}`"
                class="option-card"
                :class="{ disabled: option.supported === false }"
                type="button"
                :disabled="option.supported === false || sending || !item.needsUserSelection"
                @click="chooseOption(option, item)"
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
          <el-button v-if="sending" :loading="cancelling" @click="cancelStream">取消</el-button>
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

.message-progress {
  margin-bottom: 6px;
  color: var(--app-text-tertiary);
  font-size: 12px;
  line-height: 1.4;
}

.business-card-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin-top: 10px;
}

.business-card {
  border: 1px solid var(--app-border-soft);
  border-radius: 8px;
  padding: 9px 10px;
  background: #fbfcfe;
}

.business-card-title {
  font-weight: 600;
  margin-bottom: 8px;
}

.business-card-fields {
  display: grid;
  gap: 6px;
}

.business-card-field {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  color: var(--app-text-tertiary);
  font-size: 13px;

  strong {
    color: var(--app-text);
    font-weight: 600;
    text-align: right;
    overflow-wrap: anywhere;
  }
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


