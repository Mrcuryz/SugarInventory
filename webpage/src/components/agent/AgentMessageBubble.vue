<script setup>
import { computed, ref } from 'vue'
import AgentBusinessCard from './AgentBusinessCard.vue'
import AgentOptionCard from './AgentOptionCard.vue'
import { hasDistributionCard, visibleCards } from './agentDisplay'

const props = defineProps({
  item: {
    type: Object,
    required: true
  },
  debugMode: {
    type: Boolean,
    default: false
  },
  shadowCompareMode: {
    type: Boolean,
    default: false
  },
  sending: {
    type: Boolean,
    default: false
  }
})

const emit = defineEmits(['choose-option', 'feedback', 'card-action'])

const feedbackExpanded = ref(false)
const cards = computed(() => visibleCards(props.item))
const shadowComparison = computed(() => {
  if (!props.shadowCompareMode || props.item.role !== 'assistant') return null
  const trace = props.item.intentTrace
  const shadow = trace?.goalDraftShadow
  if (!trace?.intent_type || !shadow) return null
  const draft = shadow.draft || {}
  return {
    routerGoal: trace.intent_subtype || trace.intent_type,
    routerAction: trace.next_action || 'unknown',
    modelStatus: shadow.status || 'UNAVAILABLE',
    modelGoal: draft.goalType || '未生成',
    dataNeed: draft.dataNeed || '未知',
    clarification: draft.needsClarification === true ? '需要' : '不需要',
    contextReuse: Array.isArray(draft.contextReuse) && draft.contextReuse.length
      ? draft.contextReuse.join('、')
      : '无',
    confidence: typeof draft.confidence === 'number' ? `${Math.round(draft.confidence * 100)}%` : '未知',
    latency: Number.isFinite(shadow.latencyMs) ? `${shadow.latencyMs}ms` : '未知'
  }
})
const canSendFeedback = computed(() => (
  props.item.role === 'assistant' &&
  props.item.messageId &&
  props.item.finishReason &&
  !props.item.needsUserSelection &&
  !props.item.feedbackSubmitted
))
const showFeedbackTrigger = computed(() => canSendFeedback.value && !props.debugMode && !feedbackExpanded.value)
const showFeedbackOptions = computed(() => canSendFeedback.value && (props.debugMode || feedbackExpanded.value))
const bubbleClasses = computed(() => ({
  user: props.item.role === 'user',
  'wide-card-bubble': hasDistributionCard(props.item),
  'is-error': props.item.finishReason === 'error',
  'is-cancelled': props.item.cancelled || props.item.finishReason === 'cancelled',
  'is-interrupt': props.item.needsUserSelection || ['clarification_required', 'interrupt_required'].includes(props.item.finishReason),
  'is-streaming': Boolean(props.item.progress && !props.item.finishReason)
}))

const sendFeedback = (feedbackType) => {
  feedbackExpanded.value = false
  emit('feedback', { item: props.item, feedbackType })
}
</script>

<template>
  <div class="message-bubble" :class="bubbleClasses">
    <div v-if="item.progress" class="message-progress">
      <span class="progress-pulse" />
      <span>{{ item.progress }}</span>
    </div>
    <div v-if="item.content" class="message-text">{{ item.content }}</div>

    <div v-if="cards.length" class="business-card-list">
      <AgentBusinessCard
        v-for="(card, cardIndex) in cards"
        :key="`${card.cardType || 'card'}-${card.title || cardIndex}`"
        :card="card"
        @card-action="emit('card-action', $event)"
      />
    </div>

    <div v-if="item.options?.length" class="option-card-list">
      <AgentOptionCard
        v-for="option in item.options"
        :key="`${option.optionId || option.optionType}-${option.displayLabel}`"
        :option="option"
        :disabled="option.supported === false || item.optionSubmitting || item.selectionCompleted"
        @choose="emit('choose-option', option)"
      />
    </div>

    <div v-if="debugMode && item.toolCalls?.length" class="tool-summary">
      <div class="tool-summary-title">调试摘要</div>
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

    <div v-if="shadowComparison" class="shadow-comparison">
      <div class="shadow-comparison-title">Router / 模型 Shadow 对比</div>
      <div class="shadow-comparison-grid">
        <span>Router 目标</span><strong>{{ shadowComparison.routerGoal }}</strong>
        <span>Router 动作</span><strong>{{ shadowComparison.routerAction }}</strong>
        <span>模型状态</span><strong>{{ shadowComparison.modelStatus }}</strong>
        <span>模型目标</span><strong>{{ shadowComparison.modelGoal }}</strong>
        <span>数据需要</span><strong>{{ shadowComparison.dataNeed }}</strong>
        <span>模型追问</span><strong>{{ shadowComparison.clarification }}</strong>
        <span>上下文复用</span><strong>{{ shadowComparison.contextReuse }}</strong>
        <span>置信度 / 耗时</span><strong>{{ shadowComparison.confidence }} / {{ shadowComparison.latency }}</strong>
      </div>
      <div class="shadow-comparison-note">仅用于 UAT 对照；实际执行仍由 Router 与 Runtime 边界控制。</div>
    </div>

    <div v-if="showFeedbackTrigger" class="feedback-entry">
      <button
        type="button"
        :disabled="item.feedbackSubmitting"
        title="展开回答反馈"
        @click="feedbackExpanded = true"
      >
        反馈
      </button>
    </div>

    <div v-if="showFeedbackOptions" class="feedback-actions" aria-label="回答反馈">
      <button
        type="button"
        :disabled="item.feedbackSubmitting"
        title="这次没有解决问题"
        @click="sendFeedback('NO_TOOL')"
      >
        没解决
      </button>
      <button
        type="button"
        :disabled="item.feedbackSubmitting"
        title="理解错了用户意图"
        @click="sendFeedback('WRONG_INTENT')"
      >
        答非所问
      </button>
      <button
        type="button"
        :disabled="item.feedbackSubmitting"
        title="数据或展示不正确"
        @click="sendFeedback('DATA_WRONG')"
      >
        数据不对
      </button>
      <button
        type="button"
        :disabled="item.feedbackSubmitting"
        title="卡片或移动端展示有问题"
        @click="sendFeedback('CARD_BAD')"
      >
        展示问题
      </button>
      <button
        type="button"
        :disabled="item.feedbackSubmitting"
        title="填写其他反馈内容"
        @click="sendFeedback('OTHER')"
      >
        其他
      </button>
    </div>
    <div v-else-if="item.feedbackSubmitted" class="feedback-recorded">已记录反馈</div>
  </div>
</template>

<style scoped lang="scss">
.message-bubble {
  position: relative;
  width: fit-content;
  max-width: 100%;
  padding: 12px 14px;
  border: 1px solid var(--assistant-border);
  border-radius: 10px;
  background: #ffffff;
  color: var(--app-text);
  line-height: 1.62;
  box-shadow: 0 8px 22px rgba(29, 33, 41, 0.06);
}

.message-bubble.wide-card-bubble {
  width: 100%;
}

.message-bubble.user {
  border-color: #d7e4ff;
  background: linear-gradient(180deg, #eff6ff 0%, #e8f1ff 100%);
  color: #175cd3;
  box-shadow: 0 8px 20px rgba(22, 93, 255, 0.08);
}

.message-bubble.is-interrupt {
  border-color: #c7d7fe;
  box-shadow: 0 10px 24px rgba(22, 93, 255, 0.09);
}

.message-bubble.is-error {
  border-color: #ffd6d6;
  background: #fffafa;
}

.message-bubble.is-cancelled {
  color: #667085;
  background: #f8fafc;
}

.message-text {
  white-space: pre-wrap;
  word-break: break-word;
  font-size: 14px;
}

.message-progress {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  max-width: 100%;
  margin-bottom: 8px;
  padding: 4px 8px;
  border-radius: 999px;
  background: #f2f6ff;
  color: #4267b2;
  font-size: 12px;
  line-height: 1.4;
  font-weight: 600;
}

.progress-pulse {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--app-primary);
  box-shadow: 0 0 0 4px rgba(22, 93, 255, 0.1);
}

.business-card-list,
.option-card-list {
  display: flex;
  flex-direction: column;
  margin-top: 12px;
}

.business-card-list {
  gap: 10px;
}

.option-card-list {
  gap: 9px;
}

.tool-summary {
  display: grid;
  gap: 6px;
  margin-top: 12px;
  padding: 10px;
  border: 1px dashed #cbd5e1;
  border-radius: 8px;
  background: #f8fafc;
  color: #667085;
  font-size: 12px;
}

.tool-summary-title {
  color: #344054;
  font-weight: 700;
}

.tool-summary-line {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.shadow-comparison {
  display: grid;
  gap: 8px;
  margin-top: 12px;
  padding: 10px;
  border: 1px solid #b8d1ff;
  border-radius: 8px;
  background: #f5f8ff;
  color: #475467;
  font-size: 12px;
}

.shadow-comparison-title {
  color: #175cd3;
  font-weight: 700;
}

.shadow-comparison-grid {
  display: grid;
  grid-template-columns: minmax(82px, auto) minmax(0, 1fr);
  gap: 5px 10px;
}

.shadow-comparison-grid strong {
  min-width: 0;
  color: #344054;
  overflow-wrap: anywhere;
}

.shadow-comparison-note {
  padding-top: 7px;
  border-top: 1px solid #dce7ff;
  color: #667085;
}

.feedback-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 10px;
  padding-top: 9px;
  border-top: 1px solid #eef2f7;
}

.feedback-entry {
  display: flex;
  justify-content: flex-start;
  margin-top: 10px;
  padding-top: 9px;
  border-top: 1px solid #eef2f7;
}

.feedback-entry button,
.feedback-actions button {
  height: 26px;
  padding: 0 8px;
  border: 1px solid #dfe7f4;
  border-radius: 7px;
  background: #ffffff;
  color: #667085;
  font: inherit;
  font-size: 12px;
  line-height: 24px;
  cursor: pointer;
}

.feedback-entry button:hover:not(:disabled),
.feedback-actions button:hover:not(:disabled) {
  border-color: #c7d7fe;
  color: var(--app-primary);
  background: #f7fbff;
}

.feedback-entry button:disabled,
.feedback-actions button:disabled {
  cursor: not-allowed;
  opacity: 0.6;
}

.feedback-recorded {
  margin-top: 9px;
  color: #667085;
  font-size: 12px;
}

@media (max-width: 520px) {
  .message-bubble {
    border-radius: 9px;
    padding: 11px 12px;
  }

  .message-bubble.user {
    max-width: 88%;
  }
}
</style>
