<script setup>
import {ref, computed} from 'vue'
import {useRoute} from 'vue-router'


// 侧边栏状态
const isCollapse = ref(false)
const toggleCollapse = () => {
  isCollapse.value = !isCollapse.value
}

import {
  House,
  Document,
  Histogram,
  Box,
  Filter,
  PieChart,
  Finished,
  Switch,
  User,
  ShoppingCart
} from "@element-plus/icons-vue";
// 菜单图标
// 菜单数据
const menuList = [
  {path: '/home', title: '首页', icon: House},
  {path: '/autoInbound', title: '自动入库', icon: Document},
  {path: '/operationlogs', title: '操作日志', icon: Document},
  {path: '/product', title: '产品管理', icon: Histogram},
  {path: '/productStock', title: '产品库存', icon: ShoppingCart},
  {path: '/warehouse', title: '库位管理', icon: Box},
  {path: '/screenMesh', title: '筛网管理', icon: Filter},
  {path: '/assay', title: '化验管理', icon: PieChart},
  {path: '/assayGroup', title: '验收标准', icon: Document},
  {path: '/standard', title: '化验标准管理', icon: Finished},
  {path: '/stock', title: '出入库管理', icon: Switch},
  {path: '/employee', title: '员工管理', icon: User}
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
      </el-header>

      <!-- 主内容区 -->
      <el-main>
        <router-view/>
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
    display: flex;
    flex-direction: column;

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
      flex: 1 1 auto;
    }

    .icp-footer {
      flex: 0 0 auto;
      text-align: center;
      padding: 12px 0;
      color: #8c8c8c;
      font-size: 12px;
      background: #fff;
      border-top: 1px solid #e6e6e6;

      a {
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
</style>
