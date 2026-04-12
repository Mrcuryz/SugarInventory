<template>
  <div class="page-tabs">
    <transition-group name="tab" tag="div" class="tabs-scroll">
      <button
          v-for="tab in tabsStore.visitedTabs"
          :key="tab.path"
          class="page-tab"
          :class="{ active: tab.path === tabsStore.activeTab }"
          type="button"
          :title="tab.title"
          @click="switchTab(tab)"
      >
        <el-icon class="tab-icon">
          <component :is="getMenuIconByPath(tab.path)"/>
        </el-icon>
        <span class="tab-title">{{ tab.title }}</span>
        <span
            v-if="tab.closable"
            class="tab-close"
            @click.stop="closeTab(tab)"
        >
          <el-icon><Close/></el-icon>
        </span>
      </button>
    </transition-group>
    <el-dropdown trigger="click" @command="handleCommand">
      <el-button size="small">
        页签操作
        <el-icon class="el-icon--right"><ArrowDown/></el-icon>
      </el-button>
      <template #dropdown>
        <el-dropdown-menu>
          <el-dropdown-item command="refresh">刷新当前</el-dropdown-item>
          <el-dropdown-item command="close-current" :disabled="!currentTab?.closable">关闭当前</el-dropdown-item>
          <el-dropdown-item command="close-other">关闭其他</el-dropdown-item>
          <el-dropdown-item command="close-all">关闭全部</el-dropdown-item>
        </el-dropdown-menu>
      </template>
    </el-dropdown>
  </div>
</template>

<script setup>
import {computed} from 'vue'
import {useRouter} from 'vue-router'
import {ArrowDown, Close} from '@element-plus/icons-vue'
import {useTabsStore} from '@/stores/tabs'
import {getMenuIconByPath} from '@/utils/navigation'

const emit = defineEmits(['refresh-current'])
const router = useRouter()
const tabsStore = useTabsStore()
const currentTab = computed(() => tabsStore.visitedTabs.find(tab => tab.path === tabsStore.activeTab))

const switchTab = (tab) => {
  tabsStore.setActiveTab(tab.path)
  router.push(tab.fullPath || tab.path)
}

const closeTab = (tab) => {
  const nextTab = tabsStore.closeTab(tab.path)
  if (nextTab) {
    router.push(nextTab.fullPath || nextTab.path)
  }
}

const handleCommand = (command) => {
  if (command === 'refresh') {
    emit('refresh-current')
    return
  }
  if (command === 'close-current' && currentTab.value) {
    closeTab(currentTab.value)
    return
  }
  if (command === 'close-other' && currentTab.value) {
    tabsStore.closeOtherTabs(currentTab.value.path)
    router.push(currentTab.value.fullPath || currentTab.value.path)
    return
  }
  if (command === 'close-all') {
    const homeTab = tabsStore.closeAllTabs()
    router.push(homeTab.path)
  }
}
</script>

<style scoped>
.page-tabs {
  display: flex;
  align-items: center;
  gap: 10px;
  min-height: 46px;
  padding: 8px 16px 0;
  background: linear-gradient(180deg, #fdfefe 0%, #f5f7fb 100%);
  border-bottom: 1px solid #dfe6f3;
}

.tabs-scroll {
  flex: 1;
  display: flex;
  align-items: flex-end;
  gap: 4px;
  min-width: 0;
  overflow-x: auto;
  overflow-y: hidden;
  padding: 0 2px;
}

.page-tab {
  flex: 0 0 auto;
  position: relative;
  display: inline-flex;
  align-items: center;
  gap: 7px;
  max-width: 190px;
  min-width: 0;
  height: 34px;
  margin-bottom: -1px;
  padding: 0 9px 0 12px;
  border: 1px solid transparent;
  border-bottom-color: #dfe6f3;
  border-radius: 11px 11px 0 0;
  background: rgba(255, 255, 255, 0.48);
  color: var(--app-text-tertiary);
  cursor: pointer;
  font: inherit;
  outline: none;
  transition:
      transform 0.18s ease,
      opacity 0.18s ease,
      background-color 0.18s ease,
      border-color 0.18s ease,
      box-shadow 0.18s ease,
      color 0.18s ease;
}

.page-tab::before,
.page-tab::after {
  content: "";
  position: absolute;
  bottom: -1px;
  width: 10px;
  height: 10px;
  opacity: 0;
  transition: opacity 0.18s ease;
}

.page-tab::before {
  left: -10px;
  border-right: 1px solid #dfe6f3;
  border-bottom: 1px solid #dfe6f3;
  border-bottom-right-radius: 10px;
  box-shadow: 4px 4px 0 var(--app-panel);
}

.page-tab::after {
  right: -10px;
  border-left: 1px solid #dfe6f3;
  border-bottom: 1px solid #dfe6f3;
  border-bottom-left-radius: 10px;
  box-shadow: -4px 4px 0 var(--app-panel);
}

.page-tab:hover {
  background: rgba(255, 255, 255, 0.78);
  color: var(--app-text-secondary);
  transform: translateY(-1px);
}

.page-tab.active {
  z-index: 2;
  background: var(--app-panel);
  border-color: #dfe6f3;
  border-bottom-color: var(--app-panel);
  color: var(--app-text);
  font-weight: 650;
  box-shadow: 0 -2px 12px rgba(29, 33, 41, 0.08);
  transform: translateY(-2px);
}

.page-tab.active::before,
.page-tab.active::after {
  opacity: 1;
}

.tab-icon {
  flex: 0 0 auto;
  font-size: 14px;
  color: #9aa6b8;
  transition: color 0.18s ease;
}

.page-tab.active .tab-icon {
  color: var(--app-primary);
}

.tab-title {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.tab-close {
  flex: 0 0 auto;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 17px;
  height: 17px;
  border-radius: 50%;
  color: #a6afbf;
  opacity: 0.72;
  transition:
      opacity 0.16s ease,
      background-color 0.16s ease,
      color 0.16s ease,
      transform 0.16s ease;
}

.tab-close:hover {
  background: #edf2fb;
  color: var(--app-text-secondary);
  opacity: 1;
  transform: scale(1.04);
}

.tab-move,
.tab-enter-active,
.tab-leave-active {
  transition:
      transform 0.2s ease,
      opacity 0.18s ease;
}

.tab-enter-from,
.tab-leave-to {
  opacity: 0;
  transform: translateY(6px) scale(0.96);
}

.tab-leave-active {
  position: absolute;
  pointer-events: none;
}

:deep(.el-button) {
  margin-bottom: 8px;
  border-color: var(--app-border);
  color: var(--app-text-secondary);
  background: rgba(255, 255, 255, 0.78);
}
</style>
