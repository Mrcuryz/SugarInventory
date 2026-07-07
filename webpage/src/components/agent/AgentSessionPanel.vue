<script setup>
import { computed } from 'vue'

const props = defineProps({
  status: {
    type: String,
    default: '未启动'
  },
  expiresAt: {
    type: String,
    default: ''
  },
  isAdmin: {
    type: Boolean,
    default: false
  },
  debugMode: {
    type: Boolean,
    default: false
  },
  assistantName: {
    type: String,
    default: '智能仓储助手'
  },
  modelDisplayName: {
    type: String,
    default: '模型未配置'
  },
  modelState: {
    type: String,
    default: 'missing'
  },
  userName: {
    type: String,
    default: '当前用户'
  },
  userRole: {
    type: String,
    default: '登录用户'
  },
  userAvatarText: {
    type: String,
    default: '用'
  }
})

const emit = defineEmits(['update:debugMode'])

const sessionStatusLabel = computed(() => {
  const labels = {
    ACTIVE: '已连接',
    REVOKED: '已撤销',
    EXPIRED: '已过期',
    '未启动': '未启动'
  }
  return labels[props.status] || props.status
})

const sessionStatusClass = computed(() => ({
  active: props.status === 'ACTIVE',
  inactive: props.status !== 'ACTIVE'
}))
</script>

<template>
  <div class="session-panel">
    <div class="identity-strip" aria-label="AI 助手会话身份">
      <div class="identity-item assistant">
        <div class="identity-avatar">
          <el-icon><Service /></el-icon>
        </div>
        <div class="identity-copy">
          <span class="identity-label">助手</span>
          <strong>{{ assistantName }}</strong>
          <span class="identity-meta model" :class="modelState">{{ modelDisplayName }}</span>
        </div>
      </div>
      <div class="identity-item user">
        <div class="identity-avatar user-avatar">{{ userAvatarText }}</div>
        <div class="identity-copy">
          <span class="identity-label">当前登录</span>
          <strong>{{ userName }}</strong>
          <span class="identity-meta">{{ userRole }}</span>
        </div>
      </div>
    </div>
    <div class="session-meta">
      <div class="session-main">
        <span class="session-dot" :class="sessionStatusClass" />
        <span>会话{{ sessionStatusLabel }}</span>
      </div>
      <span v-if="expiresAt" class="session-expire">到期 {{ expiresAt }}</span>
      <button
        v-if="isAdmin"
        type="button"
        class="debug-toggle"
        :class="{ active: debugMode }"
        @click="emit('update:debugMode', !debugMode)"
      >
        {{ debugMode ? '隐藏调试' : '调试' }}
      </button>
    </div>
  </div>
</template>

<style scoped lang="scss">
.session-panel {
  flex: 0 0 auto;
  display: grid;
  gap: 10px;
  padding: 12px 18px 10px;
  border-bottom: 1px solid var(--assistant-border);
  background: linear-gradient(180deg, #ffffff 0%, #f8faff 100%);
  color: #667085;
  font-size: 12px;
}

.identity-strip {
  display: grid;
  grid-template-columns: minmax(0, 1.12fr) minmax(0, 0.88fr);
  gap: 10px;
}

.identity-item {
  min-width: 0;
  display: flex;
  align-items: center;
  gap: 9px;
  padding: 9px 10px;
  border: 1px solid #e3eaf7;
  border-radius: 8px;
  background: #ffffff;
  box-shadow: 0 4px 12px rgba(29, 33, 41, 0.04);
}

.identity-item.assistant {
  border-color: #d7e4ff;
  background: linear-gradient(180deg, #ffffff 0%, #f5f8ff 100%);
}

.identity-avatar {
  flex: none;
  width: 34px;
  height: 34px;
  display: grid;
  place-items: center;
  border: 1px solid #dbe7ff;
  border-radius: 9px;
  background: #f4f8ff;
  color: var(--app-primary);
  font-size: 15px;
}

.identity-avatar.user-avatar {
  border-color: transparent;
  background: linear-gradient(180deg, #2f73ff 0%, #165dff 100%);
  color: #ffffff;
  font-size: 13px;
  font-weight: 750;
}

.identity-copy {
  min-width: 0;
  display: grid;
  gap: 2px;
  line-height: 1.25;

  strong {
    min-width: 0;
    color: #1d2939;
    font-size: 13px;
    font-weight: 750;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
}

.identity-label {
  color: #98a2b3;
  font-size: 11px;
}

.identity-meta {
  min-width: 0;
  color: #667085;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.identity-meta.model {
  width: fit-content;
  max-width: 100%;
  padding: 2px 7px;
  border: 1px solid #d7e4ff;
  border-radius: 999px;
  background: #f2f6ff;
  color: #4267b2;
  font-weight: 650;
}

.identity-meta.model.missing {
  border-color: #f3d4a0;
  background: #fff7e8;
  color: #a15c07;
}

.session-meta {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
}

.session-main {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  min-width: 0;
  color: #344054;
  font-weight: 650;
  white-space: nowrap;
}

.session-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #a6afbf;
  box-shadow: 0 0 0 3px rgba(166, 175, 191, 0.14);

  &.active {
    background: #12b76a;
    box-shadow: 0 0 0 3px rgba(18, 183, 106, 0.14);
  }
}

.session-expire {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: #98a2b3;
}

.debug-toggle {
  flex: none;
  margin-left: auto;
  height: 26px;
  padding: 0 9px;
  border: 1px solid transparent;
  border-radius: 7px;
  background: transparent;
  color: #667085;
  font: inherit;
  cursor: pointer;

  &:hover,
  &.active {
    border-color: #cfe0ff;
    background: #eef5ff;
    color: var(--app-primary);
  }
}

@media (max-width: 520px) {
  .session-panel {
    padding: 10px 12px 9px;
  }

  .identity-strip {
    gap: 8px;
  }

  .identity-item {
    padding: 8px;
  }

  .identity-avatar {
    width: 30px;
    height: 30px;
    border-radius: 8px;
  }

  .identity-copy strong {
    font-size: 12px;
  }

  .identity-label {
    display: none;
  }

  .identity-meta.model {
    padding-inline: 6px;
  }

  .session-expire {
    display: none;
  }
}
</style>
