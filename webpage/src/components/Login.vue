<script setup>
import {ref, reactive} from 'vue'
import {useRouter} from 'vue-router'
import {ElMessage} from 'element-plus'
import {login} from '@/api/login.js'
import {useTokenStore} from '@/stores/token'
import rememberMeStore from '@/stores/rememberMe'

const tokenStore = useTokenStore();
const rememberStore = rememberMeStore();

const router = useRouter()

// 表单数据
const form = reactive({
  name: '',
  password: '',
  rememberMe: false
})
// 记住我
let rememberMe = ref('')
const rememberMeData = async () => {
  form.name = rememberStore.info.name;
  form.password = rememberStore.info.password;
}
if (rememberStore.info) {
  rememberMeData();
}
// 验证规则
const rules = reactive({
  name: [
    {required: true, message: '用户名不能为空', trigger: 'blur'},
  ],
  password: [
    {required: true, message: '密码不能为空', trigger: 'blur'},
    {min: 3, max: 18, message: '长度在3到18个字符', trigger: 'blur'}
  ]
})

// 加载状态
const loading = ref(false)

// 提交处理
const handleSubmit = async () => {
  if (rememberMe) {
    rememberStore.setInfo(form);
  } else {
    rememberStore.removeInfo();
  }
  try {
    loading.value = true
    tokenStore.removeToken();
    let result = await login(form);
    ElMessage.success('登录成功')
    tokenStore.setToken(result.data.token);
    await router.push('/home')
  } catch (error) {
    rememberStore.removeInfo();
  } finally {
    loading.value = false
  }
}


</script>
<template>
  <transition name="fade-slide" mode="out-in">
    <div class="login-container">
      <div class="login-main">
        <el-card class="login-card">
          <div class="logo-container">
            <el-image
                src="/logo.svg"
                fit="contain"
                class="logo"
                :preview-src-list="['/logo.svg']"
                :initial-index="0"
                preview-teleported
            />
            <h1 class="system-title">智能仓储管理系统</h1>
          </div>
          <el-form
              ref="loginForm"
              :model="form"
              :rules="rules"
              @keyup.enter.native="handleSubmit"
          >
            <el-form-item prop="username">
              <el-input
                  style="height: 40px;margin-top: 10px;"
                  v-model="form.name"
                  placeholder="请输入用户名"
                  prefix-icon="User"
                  clearable
              />
            </el-form-item>

            <el-form-item prop="password">
              <el-input
                  style="height: 40px;margin-top: 10px;"
                  v-model="form.password"
                  type="password"
                  placeholder="请输入密码"
                  prefix-icon="Lock"
                  show-password
                  clearable
              />
            </el-form-item>

            <el-form-item>
              <el-checkbox v-model="rememberMe">记住登录</el-checkbox>
            </el-form-item>

            <el-form-item>
              <el-button
                  type="primary"
                  class="login-btn"
                  :loading="loading"
                  @click="handleSubmit"
              >
                {{ loading ? '登录中...' : '立即登录' }}
              </el-button>
            </el-form-item>
          </el-form>
        </el-card>
      </div>
      <footer class="icp-footer">
        <a href="https://beian.miit.gov.cn/" target="_blank" rel="noreferrer">桂ICP备2025058642号-2</a>
      </footer>
    </div>
  </transition>
</template>
<style scoped>
.login-container {
  display: flex;
  flex-direction: column;
  min-height: 100vh;
  min-height: 100vh;
  background: linear-gradient(135deg, #f5f7fa 0%, #c3cfe2 100%);
}

.login-main {
  flex: 1 1 auto;
  display: flex;
  justify-content: center;
  align-items: center;
  padding: 24px 12px 16px;
}

.login-card {
  width: 420px;
  border-radius: 12px;
  box-shadow: 0 6px 18px rgba(0, 0, 0, 0.1);
}

:deep(.el-card__body) {
  padding: 40px 50px;
}

.logo-container {
  text-align: center;
  margin-bottom: 30px;
}

.logo {
  width: 80px;
  height: 80px;
  margin-bottom: 15px;
}

.system-title {
  font-size: 1.5rem;
  color: var(--el-color-primary);
  margin: 0;
}

.login-btn {
  width: 100%;
  height: 45px;
  font-size: 1rem;
}

.fr {
  margin-left: 20px;
  float: right;
}

.icp-footer {
  flex: 0 0 auto;
  text-align: center;
  padding: 10px 0 16px;
  color: #666;
  font-size: 12px;

  :is(a) {
    color: inherit;
    text-decoration: none;

    &:hover {
      color: #1890ff;
      text-decoration: underline;
    }
  }
}
</style>
