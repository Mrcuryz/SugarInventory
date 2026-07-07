<script setup>
import { computed } from 'vue'
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
  sending: {
    type: Boolean,
    default: false
  }
})

const emit = defineEmits(['chooseOption', 'feedback'])

const cards = computed(() => visibleCards(props.item))
const canSendFeedback = computed(() => (
  props.item.role === 'assistant' &&
  props.item.messageId &&
  props.item.finishReason &&
  !props.item.needsUserSelection &&
  !props.item.feedbackSubmitted
))
const bubbleClasses = computed(() => ({
  user: props.item.role === 'user',
  'wide-card-bubble': hasDistributionCard(props.item),
  'is-error': props.item.finishReason === 'error',
  'is-cancelled': props.item.cancelled || props.item.finishReason === 'cancelled',
  'is-interrupt': props.item.needsUserSelection || ['clarification_required', 'interrupt_required'].includes(props.item.finishReason),
  'is-streaming': Boolean(props.item.progress && !props.item.finishReason)
}))
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
      />
    </div>

    <div v-if="item.options?.length" class="option-card-list">
      <AgentOptionCard
        v-for="option in item.options"
        :key="`${option.optionId || option.optionType}-${option.displayLabel}`"
        :option="option"
        :disabled="option.supported === false || item.optionSubmitting || item.selectionCompleted"
        @choose="emit('chooseOption', option)"
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

    <div v-if="canSendFeedback" class="feedback-actions" aria-label="回答反馈">
      <button
        type="button"
        :disabled="item.feedbackSubmitting"
        title="这次没有解决问题"
        @click="emit('feedback', { item, feedbackType: 'NO_TOOL' })"
      >
        没解决
      </button>
      <button
        type="button"
        :disabled="item.feedbackSubmitting"
        title="理解错了用户意图"
        @click="emit('feedback', { item, feedbackType: 'WRONG_INTENT' })"
      >
        答非所问
      </button>
      <button
        type="button"
        :disabled="item.feedbackSubmitting"
        title="数据或展示不正确"
        @click="emit('feedback', { item, feedbackType: 'DATA_WRONG' })"
      >
        数据不对
      </button>
      <button
        type="button"
        :disabled="item.feedbackSubmitting"
        title="卡片或移动端展示有问题"
        @click="emit('feedback', { item, feedbackType: 'CARD_BAD' })"
      >
        展示问题
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

.feedback-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 10px;
  padding-top: 9px;
  border-top: 1px solid #eef2f7;
}

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

.feedback-actions button:hover:not(:disabled) {
  border-color: #c7d7fe;
  color: var(--app-primary);
  background: #f7fbff;
}

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
