<script setup>
import {ref, computed, nextTick, watch} from 'vue'
import {useRoute, useRouter} from 'vue-router'
import PageTabs from '@/components/PageTabs.vue'
import {useTabsStore} from '@/stores/tabs'
import {filterMenuByPermissions, menuList} from '@/utils/navigation'
import {useAuthStore} from '@/stores/auth'


// 侧边栏状态
const isCollapse = ref(false)
const toggleCollapse = () => {
  isCollapse.value = !isCollapse.value
}

// 面包屑导航
const route = useRoute()
const router = useRouter()
const tabsStore = useTabsStore()
const authStore = useAuthStore()
const routerViewVisible = ref(true)
const breadcrumbs = computed(() => {
  return route.matched
      .filter(item => item.meta?.title) // 过滤有标题的路由
      .map(item => ({
        title: item.meta.title,
        path: item.path
      }))
})

// 当前激活菜单
const activeMenu = computed(() => route.path)
const visibleMenus = computed(() => filterMenuByPermissions(menuList, authStore.permissionCodes))

watch(
    () => route.fullPath,
    () => {
      const added = tabsStore.addTab(route)
      if (!added) {
        router.replace(tabsStore.activeTab)
      }
    },
    {immediate: true}
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
</script>
<template>
  <div class="app-container">
    <!-- 侧边栏 -->
    <el-aside class="el-aside" :width="isCollapse ? '64px' : '240px'">
      <div class="logo-container">
        <span v-show="!isCollapse">仓储管理系统</span>
      </div>
      <el-menu
          :default-active="activeMenu"
          :collapse="isCollapse"
          router
      >
        <template v-for="item in visibleMenus" :key="item.path">
          <el-sub-menu v-if="item.children" :index="item.path">
            <template #title>
              <el-icon>
                <component :is="item.icon"/>
              </el-icon>
              <span>{{ item.title }}</span>
            </template>
            <template v-for="child in item.children" :key="child.path">
              <el-sub-menu v-if="child.children" :index="child.path">
                <template #title>
                  <el-icon v-if="child.icon">
                    <component :is="child.icon"/>
                  </el-icon>
                  <span>{{ child.title }}</span>
                </template>
                <el-menu-item
                    v-for="sub in child.children"
                    :key="sub.path"
                    :index="sub.path"
                >
                  <el-icon v-if="sub.icon">
                    <component :is="sub.icon"/>
                  </el-icon>
                  <span>{{ sub.title }}</span>
                </el-menu-item>
              </el-sub-menu>
              <el-menu-item v-else :index="child.path">
                <el-icon v-if="child.icon">
                  <component :is="child.icon"/>
                </el-icon>
                <span>{{ child.title }}</span>
              </el-menu-item>
            </template>
          </el-sub-menu>
          <el-menu-item
              v-else
              :index="item.path"
          >
          <el-icon>
            <component :is="item.icon"/>
          </el-icon>
          <span>{{ item.title }}</span>
          </el-menu-item>
        </template>
      </el-menu>
    </el-aside>

    <div class="main-container">
      <!-- 顶部导航 -->
      <el-header>
        <div class="header-left">
          <el-icon @click="toggleCollapse">
            <component :is="isCollapse ? 'Expand' : 'Fold'"/>
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
      </el-header>

      <!-- 主内容区 -->
      <PageTabs @refresh-current="refreshCurrentPage"/>
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
        <a href="https://beian.miit.gov.cn/" target="_blank" rel="noreferrer">桂ICP备2025058642号-2</a>
      </footer>
    </div>
  </div>
</template>
<style scoped lang="scss">
.app-container {
  display: flex;
  min-height: 100vh;
  background: var(--app-bg);

  .el-aside {
    background: #ffffff;
    border-right: 1px solid var(--app-border-soft);
    transition: width 0.3s;
    box-shadow: 4px 0 18px rgba(29, 33, 41, 0.03);

    .logo-container {
      height: 60px;
      display: flex;
      align-items: center;
      padding: 0 20px;
      color: var(--app-text);
      border-bottom: 1px solid var(--app-border-soft);

      .logo {
        width: 32px;
        margin-right: 12px;
      }

      span {
        font-size: 18px;
        font-weight: 700;
      }
    }
  }

  .main-container {
    flex: 1;
    display: flex;
    flex-direction: column;

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

      .user-wrapper {
        display: flex;
        align-items: center;
        cursor: pointer;

        .username {
          margin: 0 8px;
        }
      }
    }

    .el-main {
      padding: 16px;
      background: var(--app-bg);
      flex: 1 1 auto;
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
