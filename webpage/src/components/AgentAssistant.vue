<script setup>
import { computed, nextTick, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  cancelAgentMessage,
  createAgentSession,
  recordAgentMessageReview,
  revokeAgentSession,
  streamAgentInterruptResume,
  streamAgentMessage,
  submitAgentMessageReviewFeedback
} from '@/api/agent'
import { useAuthStore } from '@/stores/auth'
import AgentMessageBubble from '@/components/agent/AgentMessageBubble.vue'
import { cleanOptionLabel } from '@/components/agent/agentDisplay'

const visible = ref(false)
const router = useRouter()
const loadingSession = ref(false)
const sending = ref(false)
const cancelling = ref(false)
const session = ref(null)
const input = ref('')
const messages = ref([])
const scrollRef = ref(null)
const pendingSelection = ref(null)
const debugMode = ref(false)
const shadowCompareMode = import.meta.env.VITE_AGENT_SHADOW_COMPARE === 'true'
const authStore = useAuthStore()
let activeStreamController = null
let activeAssistantMessage = null

const sessionStatus = computed(() => session.value?.status || '未启动')
const sessionStatusLabel = computed(() => {
  const labels = {
    ACTIVE: '已连接',
    REVOKED: '已撤销',
    EXPIRED: '已过期',
    '未启动': '未启动'
  }
  return labels[sessionStatus.value] || sessionStatus.value
})
const sessionStatusClass = computed(() => ({
  active: sessionStatus.value === 'ACTIVE',
  inactive: sessionStatus.value !== 'ACTIVE'
}))
const isAdmin = computed(() => ['ADMIN', 'SUPER_ADMIN'].includes(session.value?.roleCode || authStore.roleCode))
const currentUserName = computed(() => session.value?.name || authStore.name || authStore.employeeId || '当前用户')
const assistantName = computed(() => '智能仓储助手')
const userAvatarText = computed(() => avatarText(currentUserName.value, '用'))

const avatarText = (name, fallback) => {
  const normalized = String(name || '').trim()
  if (!normalized) return fallback
  return normalized.slice(0, 1).toUpperCase()
}

const open = async () => {
  if (!session.value) {
    await startSession()
  }
  // Do not expose an apparently ready drawer before the replacement session
  // has cancelled any pending interrupts owned by the previous page instance.
  visible.value = true
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
  if (!text || (sending.value && !options.allowWhileSending)) return
  if (!session.value?.agentSessionId) {
    await startSession()
  }
  await markPreviousAssistantIfCorrection(text, options)
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
    finishReason: null,
    intentTrace: null
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
          debug: debugMode.value || shadowCompareMode,
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
    recordAssistantReview(text, assistantMessage, selectedOption)
  }
  return assistantMessage.finishReason
}

const handleComposerKeydown = (event) => {
  if (event.isComposing || event.key !== 'Enter' || event.shiftKey) return
  event.preventDefault()
  send()
}

const recordAssistantReview = async (userQuestion, assistantMessage, selectedOption) => {
  if (!session.value?.agentSessionId || assistantMessage.reviewRecorded) return
  if (!assistantMessage.messageId && !assistantMessage.content) return
  assistantMessage.reviewRecorded = true
  try {
    await recordAgentMessageReview(session.value.agentSessionId, {
      messageId: assistantMessage.messageId,
      userQuestion,
      assistantAnswerTextSafe: assistantMessage.content,
      assistantAnswerSummary: assistantMessage.content,
      pagePath: window.location.pathname,
      ...intentReviewPayload(assistantMessage.intentTrace),
      actualIntentSummary: formatIntentTrace(assistantMessage.intentTrace) || (selectedOption?.displayLabel
        ? `用户选择候选项后继续：${selectedOption.displayLabel}`
        : null),
      actualToolNames: extractToolNames(assistantMessage.toolCalls),
      hasCards: Boolean(assistantMessage.cards?.length),
      finishReason: assistantMessage.finishReason
    })
  } catch {
    assistantMessage.reviewRecorded = false
  }
}

const intentReviewPayload = (trace) => {
  if (!trace?.intent_type) return {}
  return {
    intent_type: safeReviewScalar(trace.intent_type),
    intent_subtype: safeReviewScalar(trace.intent_subtype),
    business_domain: safeReviewScalar(trace.business_domain),
    business_objects: safeBusinessObjects(trace.business_objects),
    missing_slots: safeReviewList(trace.missing_slots),
    support_status: safeReviewScalar(trace.support_status),
    next_action: safeReviewScalar(trace.next_action),
    planned_tools: safeReviewList(trace.planned_tools)
  }
}

const safeReviewScalar = (value, maxLength = 120) => {
  const text = String(value || '').trim()
  if (!text || /(authorization|bearer\s+|token|password|secret|stacktrace|jdbc:|chain[-_ ]?of[-_ ]?thought)/i.test(text)) {
    return null
  }
  return text.slice(0, maxLength)
}

const safeReviewList = (values = []) => {
  if (!Array.isArray(values)) return []
  return Array.from(new Set(values.map(item => safeReviewScalar(item, 100)).filter(Boolean))).slice(0, 20)
}

const safeBusinessObjects = (objects) => {
  if (!objects || typeof objects !== 'object' || Array.isArray(objects)) return {}
  return Object.entries(objects).reduce((result, [key, value]) => {
    const safeKey = safeReviewScalar(key, 80)
    if (!safeKey || safeKey.toLowerCase().endsWith('id') || /(authorization|token|password|secret|stacktrace)/i.test(safeKey)) {
      return result
    }
    if (Array.isArray(value)) {
      const list = safeReviewList(value)
      if (list.length) result[safeKey] = list
      return result
    }
    const safeValue = safeReviewScalar(value, 120)
    if (safeValue) result[safeKey] = safeValue
    return result
  }, {})
}

const extractToolNames = (toolCalls = []) => {
  const names = new Set()
  toolCalls.forEach(call => {
    if (call?.toolName) names.add(call.toolName)
  })
  return Array.from(names)
}

const formatIntentTrace = (trace) => {
  if (!trace?.intent_type) return null
  const parts = [
    `intent=${trace.intent_type}`,
    trace.intent_subtype ? `subtype=${trace.intent_subtype}` : null,
    trace.business_domain ? `domain=${trace.business_domain}` : null,
    trace.support_status ? `support=${trace.support_status}` : null,
    trace.next_action ? `next=${trace.next_action}` : null
  ].filter(Boolean)
  if (trace.missing_slots?.length) {
    parts.push(`missing=${trace.missing_slots.join('|')}`)
  }
  if (trace.planned_tools?.length) {
    parts.push(`planned=${trace.planned_tools.join('|')}`)
  }
  return parts.join('; ')
}

const correctionPatterns = [
  '不是',
  '不对',
  '你理解错了',
  '我问的是',
  '重新',
  '不是这个',
  '怎么没有',
  '为什么没查到'
]

const markPreviousAssistantIfCorrection = async (text, options = {}) => {
  if (options.showUserMessage === false || !session.value?.agentSessionId) return
  if (!correctionPatterns.some(pattern => text.includes(pattern))) return
  const previousAssistant = [...messages.value]
    .reverse()
    .find(item => item.role === 'assistant' && item.messageId && item.finishReason && !item.correctionAutoReported)
  if (!previousAssistant) return
  previousAssistant.correctionAutoReported = true
  try {
    await submitAgentMessageReviewFeedback(session.value.agentSessionId, previousAssistant.messageId, {
      feedbackType: 'USER_CORRECTION',
      feedbackNote: `用户下一轮纠正：${text}`,
      expectedIntentSummary: text
    })
  } catch {
    previousAssistant.correctionAutoReported = false
  }
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
      if (payload.intentRouter) {
        message.intentTrace = payload.intentRouter
      }
      message.toolCalls.push(payload)
      break
    case 'cancelled':
      message.cancelled = true
      message.content = payload.message || '已取消本次生成。'
      message.progress = ''
      break
    case 'message_end':
      message.finishReason = payload.finishReason || 'completed'
      if (payload.reviewTrace?.intent_type) {
        message.intentTrace = payload.reviewTrace
      } else if (payload.intentRouter?.intent_type) {
        message.intentTrace = payload.intentRouter
      }
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

const chooseOption = async (option, sourceMessage) => {
  if (!option?.displayLabel || option.supported === false) return
  if (!sourceMessage?.interruptId || !sourceMessage?.resumeToken || sourceMessage.optionSubmitting || sourceMessage.selectionCompleted) {
    return
  }
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
    sourceMessage.optionSubmitting = true
    sourceMessage.needsUserSelection = false
    sourceMessage.progress = '正在继续查询……'
  }
  pendingSelection.value = selectedOption
  const finishReason = await send({
    message: '用户选择了候选项',
    selectedOption,
    showUserMessage: false,
    allowWhileSending: true
  })
  if (sourceMessage) {
    sourceMessage.optionSubmitting = false
    sourceMessage.selectionCompleted = finishReason === 'completed'
    sourceMessage.needsUserSelection = finishReason !== 'completed'
    sourceMessage.progress = finishReason === 'completed' ? '已处理' : ''
  }
}

const submitMessageFeedback = async ({ item, feedbackType }) => {
  if (!session.value?.agentSessionId || !item?.messageId || item.feedbackSubmitting) return
  const normalizedType = feedbackType || 'OTHER'
  let feedbackNote = null
  if (normalizedType === 'OTHER') {
    const result = await ElMessageBox.prompt('请描述这次回答的问题，便于后续复盘。', '其他反馈', {
      confirmButtonText: '提交',
      cancelButtonText: '取消',
      inputType: 'textarea',
      inputPlaceholder: '例如：遗漏了上一轮选择的产品，或回答没有覆盖我关心的范围。',
      inputValidator: (value) => {
        const text = String(value || '').trim()
        if (!text) return '请填写反馈内容'
        if (text.length > 1000) return '反馈内容不能超过 1000 字'
        return true
      }
    }).catch(() => null)
    if (!result) return
    feedbackNote = String(result.value || '').trim()
    if (!feedbackNote) return
  }
  item.feedbackSubmitting = true
  try {
    await submitAgentMessageReviewFeedback(session.value.agentSessionId, item.messageId, {
      feedbackType: normalizedType,
      ...(feedbackNote ? { feedbackNote } : {})
    })
    item.feedbackSubmitted = true
    ElMessage.success('已记录反馈')
  } catch {
    ElMessage.error('反馈记录失败')
  } finally {
    item.feedbackSubmitting = false
  }
}

const messageStateClass = (item) => ({
  'is-error': item.finishReason === 'error',
  'is-cancelled': item.cancelled || item.finishReason === 'cancelled',
  'is-interrupt': item.needsUserSelection || ['clarification_required', 'interrupt_required'].includes(item.finishReason),
  'is-streaming': Boolean(item.progress && !item.finishReason)
})

const suspendAssistant = () => {
  visible.value = false
}

const handleBeforeClose = (done) => {
  if (done) {
    done()
    return
  }
  suspendAssistant()
}

const closeSession = async () => {
  if (!session.value?.agentSessionId) {
    visible.value = false
    return true
  }
  const confirmed = await ElMessageBox.confirm('结束后将撤销当前 Agent 会话，并清理本次对话上下文，是否继续？', '结束 AI 助手会话', {
    type: 'warning',
    confirmButtonText: '结束会话',
    cancelButtonText: '取消'
  }).catch(() => false)
  if (!confirmed) return false
  try {
    await revokeAgentSession(session.value.agentSessionId, { revokedReason: 'USER_ENDED_ASSISTANT_SESSION' })
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
const handleCardAction = async (action) => {
  if (action?.actionKind !== 'create_assay') return
  await router.push({
    path: '/assay',
    query: {
      create: '1',
      productName: action.productName || '',
      sampleDate: action.sampleDate || ''
    }
  })
  suspendAssistant()
}
</script>

<template>
  <el-drawer
    v-model="visible"
    title="AI 助手"
    aria-label="AI 助手"
    direction="rtl"
    size="min(480px, 100vw)"
    :before-close="handleBeforeClose"
    class="agent-assistant-drawer"
  >
    <template #header>
      <div class="assistant-header">
        <div class="assistant-mark">
          <el-icon><ChatDotRound /></el-icon>
        </div>
        <div class="assistant-heading">
          <div class="assistant-title-row">
            <div class="assistant-title">AI 助手</div>
            <span class="header-status" :class="sessionStatusClass">
              <span class="status-dot" />
              会话{{ sessionStatusLabel }}
            </span>
          </div>
          <div class="assistant-subtitle">
            <span>库存、库位、托盘和化验查询</span>
          </div>
        </div>
      </div>
    </template>

    <div class="assistant-shell" v-loading="loadingSession">
      <el-scrollbar ref="scrollRef" class="message-list">
        <div
          v-for="(item, index) in messages"
          :key="index"
          class="message-row"
          :class="[item.role, messageStateClass(item)]"
        >
          <div v-if="item.role === 'assistant'" class="assistant-avatar">
            <el-icon><Service /></el-icon>
          </div>
          <div class="message-stack">
            <div class="message-speaker" :class="item.role">
              <template v-if="item.role === 'assistant'">
                <span class="speaker-name">{{ assistantName }}</span>
              </template>
              <template v-else>
                <span class="speaker-name">{{ currentUserName }}</span>
              </template>
            </div>
            <AgentMessageBubble
              :item="item"
              :debug-mode="debugMode"
              :shadow-compare-mode="shadowCompareMode"
              :sending="sending"
              @choose-option="chooseOption($event, item)"
              @feedback="submitMessageFeedback"
              @card-action="handleCardAction"
            />
          </div>
          <div v-if="item.role === 'user'" class="user-avatar">
            {{ userAvatarText }}
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
          placeholder="例如：查黄冰糖（袋）库存，或问这些主要放在哪些库位"
          @keydown="handleComposerKeydown"
        />
        <div class="composer-actions">
          <button type="button" class="plain-action" @click="suspendAssistant">收起</button>
          <button type="button" class="plain-action danger-action" @click="closeSession">结束会话</button>
          <button v-if="sending" type="button" class="plain-action" :disabled="cancelling" @click="cancelStream">
            {{ cancelling ? '取消中' : '取消' }}
          </button>
          <button type="button" class="send-action" :disabled="sending || !input.trim()" @click="send">
            <span>{{ sending ? '发送中' : '发送' }}</span>
            <el-icon><Promotion /></el-icon>
          </button>
        </div>
      </div>
    </div>
  </el-drawer>
</template>

<style scoped lang="scss">
:deep(.agent-assistant-drawer) {
  --assistant-border: #e6eaf2;
  --assistant-border-strong: #d8e1ef;
  --assistant-surface: #f5f7fb;
  --assistant-soft-blue: #eef5ff;
  --assistant-blue: #165dff;
}

:deep(.agent-assistant-drawer .el-drawer__header) {
  margin: 0;
  padding: 18px 20px 16px;
  border-bottom: 1px solid var(--assistant-border);
  background: #ffffff;
}

:deep(.agent-assistant-drawer .el-drawer__close-btn) {
  width: 32px;
  height: 32px;
  border-radius: 8px;
  color: #667085;
  transition: background-color 0.16s ease, color 0.16s ease;

  &:hover {
    background: #f2f4f7;
    color: #1d2129;
  }
}

:deep(.agent-assistant-drawer .el-drawer__body) {
  padding: 0;
  background: var(--assistant-surface);
}

.assistant-header {
  display: flex;
  align-items: center;
  gap: 12px;
  min-width: 0;
  width: 100%;
  padding-right: 34px;
}

.assistant-mark {
  flex: none;
  width: 38px;
  height: 38px;
  display: grid;
  place-items: center;
  border: 1px solid #d7e4ff;
  border-radius: 10px;
  background: linear-gradient(180deg, #f6f9ff 0%, #eaf2ff 100%);
  color: var(--app-primary);
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.9);
}

.assistant-heading {
  flex: 1 1 auto;
  min-width: 0;
  display: grid;
  gap: 3px;
}

.assistant-title-row {
  min-width: 0;
  display: flex;
  align-items: center;
  gap: 9px;
}

.assistant-title {
  flex: none;
  color: var(--app-text);
  font-size: 17px;
  font-weight: 700;
  line-height: 22px;
}

.header-status {
  min-width: 0;
  display: inline-flex;
  align-items: center;
  gap: 5px;
  color: #475467;
  font-size: 12px;
  font-weight: 650;
  line-height: 18px;
  white-space: nowrap;
}

.status-dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: #a6afbf;
  box-shadow: 0 0 0 3px rgba(166, 175, 191, 0.13);
}

.header-status.active .status-dot {
  background: #12b76a;
  box-shadow: 0 0 0 3px rgba(18, 183, 106, 0.14);
}

.assistant-subtitle {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
  color: var(--app-text-tertiary);
  font-size: 12px;
  line-height: 16px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.assistant-subtitle > span:first-child {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
}

.assistant-shell {
  height: 100%;
  display: flex;
  flex-direction: column;
  background: var(--assistant-surface);
}

.message-list {
  flex: 1;
  min-height: 0;
  background:
    linear-gradient(180deg, rgba(255, 255, 255, 0.64) 0%, rgba(245, 247, 251, 0.96) 36%),
    var(--assistant-surface);
}

.message-list :deep(.el-scrollbar__view) {
  min-height: 100%;
  padding: 16px 18px 18px;
}

.message-row {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  margin-bottom: 14px;

  &.user {
    justify-content: flex-end;
  }

  &.assistant {
    justify-content: flex-start;
  }
}

.assistant-avatar {
  flex: 0 0 auto;
  width: 32px;
  height: 32px;
  display: grid;
  place-items: center;
  margin-top: 20px;
  border: 1px solid #dbe7ff;
  border-radius: 9px;
  background: #ffffff;
  color: var(--app-primary);
  box-shadow: 0 4px 12px rgba(22, 93, 255, 0.08);
}

.user-avatar {
  flex: 0 0 auto;
  width: 32px;
  height: 32px;
  display: grid;
  place-items: center;
  margin-top: 20px;
  border-radius: 9px;
  background: linear-gradient(180deg, #2f73ff 0%, #165dff 100%);
  color: #ffffff;
  font-size: 13px;
  font-weight: 750;
  box-shadow: 0 7px 16px rgba(22, 93, 255, 0.18);
}

.message-stack {
  min-width: 0;
  max-width: calc(100% - 42px);
  display: flex;
  flex-direction: column;
}

.message-row.user .message-stack {
  max-width: calc(86% - 42px);
  align-items: flex-end;
}

.message-speaker {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  max-width: 100%;
  min-height: 18px;
  margin-bottom: 5px;
  color: #667085;
  font-size: 12px;
  line-height: 18px;
}

.message-speaker.user {
  justify-content: flex-end;
}

.speaker-name {
  min-width: 0;
  color: #344054;
  font-weight: 700;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.composer {
  flex: 0 0 auto;
  padding: 14px 18px 16px;
  border-top: 1px solid var(--assistant-border);
  background: #ffffff;
  box-shadow: 0 -8px 22px rgba(29, 33, 41, 0.04);
}

.composer :deep(.el-textarea__inner) {
  min-height: 82px !important;
  padding: 11px 12px 24px;
  border: 1px solid #dfe7f4;
  border-radius: 10px;
  background: #fbfcff;
  box-shadow: none;
  color: var(--app-text);
  line-height: 1.5;
  resize: none;

  &:focus {
    border-color: #8fb0ff;
    box-shadow: 0 0 0 3px rgba(22, 93, 255, 0.08);
  }
}

.composer :deep(.el-input__count) {
  right: 10px;
  bottom: 5px;
  background: transparent;
  color: #98a2b3;
}

.composer-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  margin-top: 11px;
}

.plain-action,
.send-action {
  height: 34px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  border-radius: 8px;
  padding: 0 13px;
  font: inherit;
  font-size: 13px;
  font-weight: 650;
  cursor: pointer;
  transition: background-color 0.15s ease, border-color 0.15s ease, color 0.15s ease, box-shadow 0.15s ease;
}

.plain-action {
  border: 1px solid #dfe7f4;
  background: #ffffff;
  color: #475467;

  &:hover:not(:disabled) {
    border-color: #c7d7fe;
    background: #f7fbff;
    color: var(--app-primary);
  }
}

.danger-action {
  color: #b42318;

  &:hover:not(:disabled) {
    border-color: #fecdca;
    background: #fffbfa;
    color: #b42318;
  }
}

.send-action {
  min-width: 82px;
  border: 1px solid var(--app-primary);
  background: linear-gradient(180deg, #2f73ff 0%, #165dff 100%);
  color: #ffffff;
  box-shadow: 0 8px 18px rgba(22, 93, 255, 0.20);

  &:hover:not(:disabled) {
    box-shadow: 0 10px 22px rgba(22, 93, 255, 0.26);
  }
}

.plain-action:disabled,
.send-action:disabled {
  cursor: not-allowed;
  opacity: 0.62;
  box-shadow: none;
}

@media (max-width: 520px) {
  :deep(.agent-assistant-drawer .el-drawer__header) {
    padding: 15px 14px 13px;
  }

  .assistant-mark {
    width: 34px;
    height: 34px;
  }

  .assistant-header {
    gap: 10px;
    padding-right: 30px;
  }

  .assistant-title-row {
    gap: 7px;
  }

  .message-list :deep(.el-scrollbar__view) {
    padding: 12px 12px 14px;
  }

  .assistant-avatar,
  .user-avatar {
    width: 28px;
    height: 28px;
    margin-top: 20px;
    border-radius: 8px;
    font-size: 12px;
  }

  .message-stack {
    max-width: calc(100% - 38px);
  }

  .message-row.user .message-stack {
    max-width: calc(100% - 38px);
  }

  .composer {
    padding: 12px;
  }
}
</style>


