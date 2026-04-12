import {computed, ref} from 'vue'
import {defineStore} from 'pinia'
import {ElMessage} from 'element-plus'

export const MAX_TABS = 10
export const HOME_TAB = {
  title: '首页',
  path: '/home',
  name: 'Home',
  fullPath: '/home',
  closable: false
}

export const KEEP_ALIVE_TAB_NAMES = [
  'Home',
  'AutoInbound',
  'Product',
  'ProductStock',
  'Warehouse',
  'WarehouseMap',
  'PalletCodeList',
  'PalletTaskOverview',
  'SemiInTaskPage',
  'SemiOutTaskPage',
  'FinishInTaskPage',
  'FinishOutTaskPage',
  'TransferTaskPage',
  'Assay',
  'AssayGroup',
  'Standard',
  'Stock',
  'OperationLogs'
]

export const useTabsStore = defineStore('tabs', () => {
  const visitedTabs = ref([{...HOME_TAB}])
  const activeTab = ref(HOME_TAB.path)
  const cachedTabNames = computed(() => {
    return visitedTabs.value
        .map(tab => tab.name)
        .filter(name => KEEP_ALIVE_TAB_NAMES.includes(name))
  })

  const normalizeTab = (route) => ({
    title: route.meta?.title || route.name || route.path,
    path: route.path,
    name: route.name,
    fullPath: route.fullPath,
    closable: route.path !== HOME_TAB.path
  })

  const addTab = (route) => {
    if (!route?.name || route.meta?.hiddenTab || route.path === '/login') {
      return true
    }
    const exists = visitedTabs.value.some(tab => tab.path === route.path)
    if (!exists) {
      if (visitedTabs.value.length >= MAX_TABS) {
        ElMessage.warning(`最多同时打开 ${MAX_TABS} 个页签`)
        return false
      }
      visitedTabs.value.push(normalizeTab(route))
    }
    activeTab.value = route.path
    return true
  }

  const setActiveTab = (path) => {
    activeTab.value = path
  }

  const closeTab = (path) => {
    const index = visitedTabs.value.findIndex(tab => tab.path === path)
    if (index < 0 || !visitedTabs.value[index].closable) {
      return null
    }
    const isActive = activeTab.value === path
    visitedTabs.value.splice(index, 1)
    if (!isActive) {
      return null
    }
    const nextTab = visitedTabs.value[index] || visitedTabs.value[index - 1] || visitedTabs.value[0] || HOME_TAB
    activeTab.value = nextTab.path
    return nextTab
  }

  const closeOtherTabs = (path) => {
    visitedTabs.value = visitedTabs.value.filter(tab => !tab.closable || tab.path === path)
    activeTab.value = path
  }

  const closeAllTabs = () => {
    visitedTabs.value = visitedTabs.value.filter(tab => !tab.closable)
    if (!visitedTabs.value.length) {
      visitedTabs.value = [{...HOME_TAB}]
    }
    activeTab.value = HOME_TAB.path
    return HOME_TAB
  }

  const removeCacheName = (name) => {
    const target = visitedTabs.value.find(tab => tab.name === name)
    if (target) {
      target.name = `${name}__refreshing`
    }
  }

  const restoreCacheName = (path, name) => {
    const target = visitedTabs.value.find(tab => tab.path === path)
    if (target) {
      target.name = name
    }
  }

  return {
    visitedTabs,
    activeTab,
    cachedTabNames,
    addTab,
    setActiveTab,
    closeTab,
    closeOtherTabs,
    closeAllTabs,
    removeCacheName,
    restoreCacheName
  }
})
