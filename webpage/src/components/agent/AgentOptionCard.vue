<script setup>
import { computed } from 'vue'
import { cleanOptionLabel, optionCardClass, optionHint, optionTypeLabel } from './agentDisplay'

const props = defineProps({
  option: {
    type: Object,
    required: true
  },
  disabled: {
    type: Boolean,
    default: false
  }
})

const emit = defineEmits(['choose'])
const cardClasses = computed(() => ({
  ...optionCardClass(props.option),
  disabled: props.disabled || props.option.supported === false
}))
</script>

<template>
  <button
    class="option-card"
    :class="cardClasses"
    type="button"
    :disabled="disabled"
    @click="emit('choose', option)"
  >
    <span class="option-content">
      <span class="option-title">{{ cleanOptionLabel(option.displayLabel) }}</span>
      <span v-if="option.description" class="option-description">{{ option.description }}</span>
      <span class="option-note">{{ optionHint(option) }}</span>
    </span>
    <span class="option-meta">{{ optionTypeLabel(option.optionType) }}</span>
  </button>
</template>

<style scoped lang="scss">
.option-card {
  width: 100%;
  display: grid;
  grid-template-columns: 1fr auto;
  gap: 8px 12px;
  align-items: start;
  min-height: 56px;
  border: 1px solid #dfe7f4;
  border-radius: 10px;
  background: #ffffff;
  color: var(--app-text);
  padding: 10px 11px;
  text-align: left;
  cursor: pointer;
  box-shadow: 0 3px 10px rgba(29, 33, 41, 0.035);
  transition: border-color 0.15s ease, background-color 0.15s ease, box-shadow 0.15s ease, transform 0.15s ease;

  &:hover:not(.disabled) {
    border-color: #7aa2ff;
    background: #f7fbff;
    box-shadow: 0 8px 20px rgba(22, 93, 255, 0.09);
    transform: translateY(-1px);
  }

  &.disabled {
    cursor: not-allowed;
    color: #98a2b3;
    background: #f8fafc;
    box-shadow: none;
  }

  &.actionable {
    border-color: #d4def0;
  }
}

.option-content {
  min-width: 0;
  display: grid;
  gap: 4px;
}

.option-title {
  min-width: 0;
  color: var(--app-text);
  font-size: 13px;
  font-weight: 750;
  line-height: 19px;
  overflow-wrap: anywhere;
}

.option-description {
  color: #667085;
  font-size: 12px;
  line-height: 17px;
  overflow-wrap: anywhere;
}

.option-meta {
  display: inline-flex;
  align-items: center;
  min-height: 24px;
  padding: 2px 8px;
  border-radius: 999px;
  background: #f2f6ff;
  color: #4267b2;
  font-size: 12px;
  line-height: 16px;
  white-space: nowrap;
}

.option-note {
  color: #98a2b3;
  font-size: 12px;
  line-height: 17px;
}

@media (max-width: 520px) {
  .option-card {
    grid-template-columns: minmax(0, 1fr);
  }

  .option-meta {
    width: fit-content;
  }
}
</style>
