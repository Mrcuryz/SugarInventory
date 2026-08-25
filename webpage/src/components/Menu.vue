<script setup>
import { computed, nextTick, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessageBox } from 'element-plus'
import PageTabs from '@/components/PageTabs.vue'
import AgentAssistant from '@/components/AgentAssistant.vue'
import { canUseAgent as hasAgentAccess } from '@/components/agent/agentAccess.mjs'
import { useTabsStore } from '@/stores/tabs'
import { filterMenuByPermissions, menuList } from '@/utils/navigation'
import { useAuthStore } from '@/stores/auth'
import { useTokenStore } from '@/stores/token'
import appLogo from '@/assets/logo.png'

const isCollapse = ref(false)
const route = useRoute()
const router = useRouter()
const tabsStore = useTabsStore()
const authStore = useAuthStore()
const tokenStore = useTokenStore()
const routerViewVisible = ref(true)
const agentAssistantRef = ref(null)

const toggleCollapse = () => {
  isCollapse.value = !isCollapse.value
}

const breadcrumbs = computed(() => route.matched
  .filter(item => item.meta?.title)
  .map(item => ({
    title: item.meta.title,
    path: item.path
  })))

const activeMenu = computed(() => route.path)
const visibleMenus = computed(() => filterMenuByPermissions(menuList, authStore.permissionCodes, authStore.roleCode))
const displayName = computed(() => authStore.name || authStore.employeeId || '当前账号')
const canUseAgent = computed(() => hasAgentAccess(authStore.roleCode, authStore.permissionCodes))

watch(
  () => route.fullPath,
  () => {
    const added = tabsStore.addTab(route)
    if (!added) {
      router.replace(tabsStore.activeTab)
    }
  },
  { immediate: true }
)

const refreshCurrentPage = async () => {
  const name = route.name
  const path = route.path
  if (name) {
    tabsStore.removeCacheName(name)
  }
  routerViewVisible.value = false
  await nextTick()
  if (name) {
    tabsStore.restoreCacheName(path, name)
  }
  routerViewVisible.value = true
}

const openAgentAssistant = () => {
  if (!canUseAgent.value) return
  agentAssistantRef.value?.open()
}

const handleLogout = async () => {
  const confirmed = await ElMessageBox.confirm('退出后将返回登录页，是否继续？', '退出登录', {
    type: 'warning',
    confirmButtonText: '退出',
    cancelButtonText: '取消'
  }).catch(() => false)
  if (!confirmed) return
  tokenStore.removeToken()
  authStore.clearAuth()
  tabsStore.closeAllTabs()
  await router.replace('/login')
}
</script>

<template>
  <div class="app-container">
    <el-aside class="el-aside" :width="isCollapse ? '64px' : '240px'">
      <div class="logo-container">
        <img :src="appLogo" alt="数字仓储平台" class="logo">
        <span v-show="!isCollapse">数字仓储平台</span>
      </div>

      <el-menu :default-active="activeMenu" :collapse="isCollapse" router>
        <template v-for="item in visibleMenus" :key="item.path">
          <el-sub-menu v-if="item.children" :index="item.path">
            <template #title>
              <el-icon>
                <component :is="item.icon" />
              </el-icon>
              <span>{{ item.title }}</span>
            </template>

            <template v-for="child in item.children" :key="child.path">
              <el-sub-menu v-if="child.children" :index="child.path">
                <template #title>
                  <el-icon v-if="child.icon">
                    <component :is="child.icon" />
                  </el-icon>
                  <span>{{ child.title }}</span>
                </template>

                <el-menu-item v-for="sub in child.children" :key="sub.path" :index="sub.path">
                  <el-icon v-if="sub.icon">
                    <component :is="sub.icon" />
                  </el-icon>
                  <span>{{ sub.title }}</span>
                </el-menu-item>
              </el-sub-menu>

              <el-menu-item v-else :index="child.path">
                <el-icon v-if="child.icon">
                  <component :is="child.icon" />
                </el-icon>
                <span>{{ child.title }}</span>
              </el-menu-item>
            </template>
          </el-sub-menu>

          <el-menu-item v-else :index="item.path">
            <el-icon>
              <component :is="item.icon" />
            </el-icon>
            <span>{{ item.title }}</span>
          </el-menu-item>
        </template>
      </el-menu>
    </el-aside>

    <div class="main-container">
      <div class="main-top">
        <el-header>
          <div class="header-left">
            <el-icon @click="toggleCollapse">
              <component :is="isCollapse ? 'Expand' : 'Fold'" />
            </el-icon>
            <el-breadcrumb separator="/">
              <el-breadcrumb-item
                v-for="(item, index) in breadcrumbs"
                :key="item.path"
                :to="index < breadcrumbs.length - 1 ? { path: item.path } : null"
              >
                {{ item.title }}
              </el-breadcrumb-item>
            </el-breadcrumb>
          </div>

          <div class="header-right">
            <el-button
              v-if="canUseAgent"
              type="primary"
              plain
              class="agent-button"
              @click="openAgentAssistant"
            >
              <el-icon><ChatDotRound /></el-icon>
              <span>AI 助手</span>
            </el-button>
            <el-dropdown trigger="click">
              <span class="user-trigger">
                <img :src="appLogo" alt="用户" class="user-logo">
                <span class="username">{{ displayName }}</span>
                <el-icon><ArrowDown /></el-icon>
              </span>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item @click="handleLogout">
                    <el-icon><SwitchButton /></el-icon>
                    退出登录
                  </el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </div>
        </el-header>

        <PageTabs @refresh-current="refreshCurrentPage" />
      </div>

      <el-main>
        <router-view v-slot="{ Component, route: currentRoute }">
          <keep-alive :include="tabsStore.cachedTabNames" :max="10">
            <component
              :is="Component"
              v-if="routerViewVisible"
              :key="currentRoute.path"
            />
          </keep-alive>
        </router-view>
      </el-main>

      <footer class="icp-footer">
        <a href="https://beian.miit.gov.cn/" target="_blank" rel="noreferrer">桂 ICP 备 2025058642 号-2</a>
      </footer>
      <AgentAssistant v-if="canUseAgent" ref="agentAssistantRef" />
    </div>
  </div>
</template>

<style scoped lang="scss">
.app-container {
  display: flex;
  height: 100vh;
  background: var(--app-bg);
  overflow: hidden;

  .el-aside {
    position: sticky;
    top: 0;
    height: 100vh;
    overflow-y: auto;
    background: #ffffff;
    border-right: 1px solid var(--app-border-soft);
    transition: width 0.3s;
    box-shadow: 4px 0 18px rgba(29, 33, 41, 0.03);

    .logo-container {
      height: 60px;
      display: flex;
      align-items: center;
      gap: 12px;
      padding: 0 18px;
      color: var(--app-text);
      border-bottom: 1px solid var(--app-border-soft);

      .logo {
        width: 30px;
        height: 30px;
        flex: 0 0 auto;
        object-fit: contain;
      }

      span {
        font-size: 18px;
        font-weight: 700;
        white-space: nowrap;
      }
    }
  }

  .main-container {
    flex: 1;
    min-width: 0;
    height: 100vh;
    display: flex;
    flex-direction: column;
    overflow: hidden;

    .main-top {
      position: sticky;
      top: 0;
      z-index: 20;
      flex: 0 0 auto;
      background: var(--app-bg);
    }

    .el-header {
      display: flex;
      justify-content: space-between;
      align-items: center;
      background: #fff;
      border-bottom: 1px solid var(--app-border-soft);
      height: 56px;

      .header-left {
        display: flex;
        align-items: center;

        .el-icon {
          margin-right: 16px;
          cursor: pointer;
        }
      }

      .header-right {
        display: flex;
        align-items: center;
        gap: 12px;
      }

      .agent-button {
        display: inline-flex;
        align-items: center;
        gap: 6px;
      }

      .user-trigger {
        display: inline-flex;
        align-items: center;
        gap: 10px;
        color: var(--app-text-secondary);
        cursor: pointer;
        user-select: none;
      }

      .user-logo {
        width: 28px;
        height: 28px;
        object-fit: contain;
      }

      .username {
        max-width: 180px;
        overflow: hidden;
        text-overflow: ellipsis;
        white-space: nowrap;
        font-weight: 600;
      }
    }

    .el-main {
      padding: 16px;
      background: var(--app-bg);
      flex: 1 1 auto;
      min-height: 0;
      overflow: auto;
    }

    .icp-footer {
      flex: 0 0 auto;
      text-align: center;
      padding: 12px 0;
      color: #8c8c8c;
      font-size: 12px;
      background: #fff;
      border-top: 1px solid var(--app-border-soft);

      :is(a) {
        color: inherit;
        text-decoration: none;

        &:hover {
          color: #1890ff;
          text-decoration: underline;
        }
      }
    }
  }
}

:deep(.el-menu) {
  border-right: none;
  padding: 8px;
}

:deep(.el-menu-item),
:deep(.el-sub-menu__title) {
  height: 42px;
  margin: 3px 0;
  border-radius: 8px;
  color: var(--app-text-secondary);
}

:deep(.el-menu-item:hover),
:deep(.el-sub-menu__title:hover) {
  background: var(--app-hover);
  color: var(--app-primary);
}

:deep(.el-menu-item.is-active) {
  background: var(--app-primary-light);
  color: var(--app-primary);
  font-weight: 650;
}

:deep(.el-menu-item.is-active::before) {
  content: "";
  width: 3px;
  height: 18px;
  margin-right: 8px;
  border-radius: 3px;
  background: var(--app-primary);
}

:deep(.el-menu--collapse .el-menu-item.is-active::before) {
  display: none;
}

:deep(.el-breadcrumb__inner) {
  color: var(--app-text-tertiary);
  font-weight: 500;
}
</style>
