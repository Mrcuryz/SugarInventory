<script setup>
import {ref, computed} from 'vue'
import {useRoute} from 'vue-router'
import {
  Expand,
  Fold,
  ArrowDown,
  House,
  Box,
  Histogram,
  Setting,
  Document
} from '@element-plus/icons-vue'


// 侧边栏状态
const isCollapse = ref(false)
const toggleCollapse = () => {
  isCollapse.value = !isCollapse.value
}

<svg t="1742219055596" className="icon" viewBox="0 0 1024 1024" version="1.1" xmlns="http://www.w3.org/2000/svg"
     p-id="1785" width="200" height="200">
  <path
      d="M625.566 589.1c31.214 0 56.52 25.304 56.52 56.52 0 31.214-25.306 56.52-56.52 56.52H399.485c-31.214 0-56.518-25.306-56.518-56.52 0-31.216 25.304-56.52 56.518-56.52h226.081z m301.439 282.599c0 31.214-25.306 56.52-56.52 56.52h-471c-31.214 0-56.518-25.306-56.518-56.52 0-31.216 25.304-56.52 56.518-56.52h414.48V502.075L512.319 232.402 211.085 501.366v370.333c0 31.214-25.304 56.52-56.52 56.52-31.214 0-56.518-25.306-56.518-56.52V476.742c-0.396-15.064 5.043-30.249 16.56-41.746l357.495-319.203c22.267-22.231 58.374-22.231 80.642 0 0.273 0.271 0.45 0.599 0.716 0.874l356.672 318.866c4.103 4.101 7.189 8.774 9.778 13.642 4.378 8.028 7.093 17.095 7.093 26.885v395.639z"
      fill="#5E9DF3" p-id="1786"></path>
  <path
      d="M512.546 99.114c-31.216 0-56.522 25.304-56.522 56.52 0 31.214 25.306 56.52 56.522 56.52 31.214 0 56.52-25.306 56.52-56.52 0-31.216-25.306-56.52-56.52-56.52z m357.959 715.918c-31.216 0-56.52 25.304-56.52 56.52 0 31.214 25.304 56.52 56.52 56.52 31.214 0 56.52-25.306 56.52-56.52-0.001-31.216-25.306-56.52-56.52-56.52z m-471-226.079c-31.214 0-56.518 25.304-56.518 56.52 0 31.214 25.304 56.52 56.518 56.52 31.216 0 56.52-25.306 56.52-56.52-0.001-31.217-25.305-56.52-56.52-56.52z"
      fill="#3080EE" p-id="1787"></path>
</svg>
// 菜单数据
const menuList = [
  {path: '/home', title: '首页', icon: House},
  {path: '/operationlogs', title: '操作日志', icon: Document},
  {path: '/product', title: '产品管理', icon: Histogram},
  {path: '/doc', title: '文档中心', icon: Box},
  {path: '/setting', title: '系统设置', icon: Setting}
]

// 面包屑导航
const route = useRoute()
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
</script>
<template>
  <div class="app-container">
    <!-- 侧边栏 -->
    <el-aside class="el-aside" :width="isCollapse ? '64px' : '240px'">
      <div class="logo-container">
        <img src="@/assets/logo.png" class="logo">
        <span v-show="!isCollapse">仓储管理系统</span>
      </div>
      <el-menu
          :default-active="activeMenu"
          :collapse="isCollapse"
          router
          background-color="#2d3b54"
          text-color="#b0bac9"
          active-text-color="#fff"
      >
        <el-menu-item
            v-for="item in menuList"
            :key="item.path"
            :index="item.path"
        >
          <el-icon>
            <component :is="item.icon"/>
          </el-icon>
          <span>{{ item.title }}</span>
        </el-menu-item>
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

        <div class="user-info">
          <el-dropdown>
            <div class="user-wrapper">
              <el-avatar :size="32" src="@/assets/user.png" />
              <span class="username">管理员</span>
              <el-icon><arrow-down /></el-icon>
            </div>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item>个人中心</el-dropdown-item>
                <el-dropdown-item divided @click="logout">退出登录</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </el-header>

      <!-- 主内容区 -->
      <el-main>
        <router-view />
      </el-main>
    </div>
  </div>
</template>
<style scoped>
.app-container {
  display: flex;
  min-height: 100vh;
  background: #f0f2f5;

  .el-aside {
    background: #2d3b54;
    transition: width 0.3s;

    .logo-container {
      height: 60px;
      display: flex;
      align-items: center;
      padding: 0 20px;
      color: #fff;

      .logo {
        width: 32px;
        margin-right: 12px;
      }

      span {
        font-size: 18px;
        font-weight: bold;
      }
    }
  }

  .main-container {
    flex: 1;

    .el-header {
      display: flex;
      justify-content: space-between;
      align-items: center;
      background: #fff;
      border-bottom: 1px solid #e6e6e6;

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
      padding: 20px;
      background: #f0f2f5;
    }
  }
}
</style>