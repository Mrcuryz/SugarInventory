<script setup>
import {computed, reactive, ref} from 'vue'
import {useRouter} from 'vue-router'
import {Lock, User} from '@element-plus/icons-vue'
import {login} from '@/api/login.js'
import {useTokenStore} from '@/stores/token'
import {useAuthStore} from '@/stores/auth'
import rememberMeStore from '@/stores/rememberMe'
import appLogo from '@/assets/logo.png'

const router = useRouter()
const tokenStore = useTokenStore()
const authStore = useAuthStore()
const rememberStore = rememberMeStore()
const loginFormRef = ref(null)
const loading = ref(false)
const loginError = ref('')
const hasTriedSubmit = ref(false)

const form = reactive({
  name: '',
  password: '',
  rememberMe: false
})

const savedInfo = rememberStore.info || {}
if (savedInfo.name) {
  form.name = savedInfo.name
  form.password = savedInfo.password || ''
  form.rememberMe = true
}

const rules = {
  name: [
    {required: true, message: '请输入用户名', trigger: 'blur'}
  ],
  password: [
    {required: true, message: '请输入密码', trigger: 'blur'},
    {min: 3, max: 18, message: '密码长度应为 3 到 18 个字符', trigger: 'blur'}
  ]
}

const businessTags = ['二维码管理', '任务流转', '仓库平面图', '单据与追溯']

const errorMessage = computed(() => {
  if (!loginError.value) return ''
  return loginError.value
})

const resolveLoginError = (error) => {
  const message = error?.msg || error?.response?.data?.msg || error?.response?.data || error?.message
  if (!message) return '网络异常，请稍后重试'
  if (String(message).includes('停用')) return '账号已停用，请联系管理员'
  if (String(message).includes('密码') || String(message).includes('用户')) return '用户名或密码错误'
  return String(message)
}

const handleSubmit = async () => {
  if (loading.value) return
  hasTriedSubmit.value = true
  loginError.value = ''
  const valid = await loginFormRef.value?.validate().catch(() => false)
  if (!valid) return

  loading.value = true
  try {
    tokenStore.removeToken()
    authStore.clearAuth()
    const result = await login({
      name: form.name,
      password: form.password,
      rememberMe: form.rememberMe
    })
    const token = result?.data?.token
    if (!token) {
      throw new Error('登录响应缺少 token')
    }
    if (form.rememberMe) {
      rememberStore.setInfo({...form})
    } else {
      rememberStore.removeInfo()
    }
    tokenStore.setToken(token)
    authStore.setUserInfo(result?.data || {})
    await router.push('/home')
  } catch (error) {
    tokenStore.removeToken()
    authStore.clearAuth()
    loginError.value = resolveLoginError(error)
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <main class="login-page">
    <section class="brand-panel" aria-label="系统品牌展示">
      <div class="brand-content">
        <div class="brand-mark">
          <div class="brand-icon">
            <img :src="appLogo" alt="数字仓储平台" class="brand-logo-image">
          </div>
          <span>广西糖罐子食品有限公司</span>
        </div>

        <div class="brand-copy">
          <p class="eyebrow">Warehouse Operation Platform</p>
          <h1>智能仓储管理系统</h1>
          <p class="subtitle">面向仓储现场的数字化仓储作业平台，覆盖二维码追溯、库位管理与任务流转。</p>
        </div>

        <div class="tag-list" aria-label="业务关键词">
          <span v-for="tag in businessTags" :key="tag" class="business-tag">{{ tag }}</span>
        </div>

        <div class="warehouse-visual" aria-label="系统能力展示">
          <div class="visual-header">
            <span>现场作业闭环</span>
            <strong>QR Workflow</strong>
          </div>
          <div class="capability-grid">
            <div class="capability-item primary">
              <span class="capability-icon">QR</span>
              <div>
                <strong>仓储二维码</strong>
                <p>一板一码，循环追溯</p>
              </div>
            </div>
            <div class="capability-item">
              <span class="capability-icon">TASK</span>
              <div>
                <strong>任务流转</strong>
                <p>创建、确认、闭环记录</p>
              </div>
            </div>
            <div class="capability-item">
              <span class="capability-icon">MAP</span>
              <div>
                <strong>库位平面图</strong>
                <p>现场位置可视化</p>
              </div>
            </div>
            <div class="capability-item accent">
              <span class="capability-icon">DOC</span>
              <div>
                <strong>单据追溯</strong>
                <p>出入库全程留痕</p>
              </div>
            </div>
          </div>
        </div>
      </div>
    </section>

    <section class="login-panel" aria-label="登录表单">
      <el-card class="login-card" shadow="never">
        <div class="card-heading">
          <div class="card-logo">
            <img :src="appLogo" alt="数字仓储平台" class="card-logo-image">
          </div>
          <div>
            <h2>欢迎登录</h2>
            <p>请登录。如需注册请联系管理员</p>
          </div>
        </div>

        <el-alert
            v-if="errorMessage"
            :title="errorMessage"
            type="error"
            show-icon
            :closable="false"
            class="login-error"
        />

        <el-form
            ref="loginFormRef"
            :model="form"
            :rules="rules"
            label-width="72px"
            :show-message="hasTriedSubmit"
            :validate-on-rule-change="false"
            hide-required-asterisk
            class="login-form"
            size="large"
            @keyup.enter="handleSubmit"
        >
          <el-form-item prop="name" label="用户名">
            <el-input
                v-model.trim="form.name"
                placeholder="请输入用户名"
                :prefix-icon="User"
                clearable
                :disabled="loading"
                autocomplete="username"
            />
          </el-form-item>

          <el-form-item prop="password" label="密码">
            <el-input
                v-model="form.password"
                type="password"
                placeholder="请输入密码"
                :prefix-icon="Lock"
                show-password
                clearable
                :disabled="loading"
                autocomplete="current-password"
            />
          </el-form-item>

          <div class="form-options">
            <el-checkbox v-model="form.rememberMe" :disabled="loading">记住登录</el-checkbox>
          </div>

          <el-button
              type="primary"
              class="login-button"
              :loading="loading"
              :disabled="loading"
              @click="handleSubmit"
          >
            立即登录
          </el-button>
        </el-form>
      </el-card>
    </section>

    <footer class="icp-footer">
      <a href="https://beian.miit.gov.cn/" target="_blank" rel="noreferrer">桂ICP备2025058642号-2</a>
    </footer>
  </main>
</template>

<style scoped>
.login-page {
  position: relative;
  min-height: 100vh;
  display: grid;
  grid-template-columns: minmax(0, 55fr) minmax(450px, 45fr);
  overflow: hidden;
  background:
      radial-gradient(circle at 23% 20%, rgba(22, 93, 255, 0.12), transparent 26%),
      radial-gradient(circle at 70% 48%, rgba(255, 255, 255, 0.94), transparent 34%),
      radial-gradient(circle at 83% 82%, rgba(0, 168, 112, 0.08), transparent 23%),
      linear-gradient(135deg, #f7faff 0%, #eef4ff 44%, #f9fbff 100%);
  color: var(--app-text);
}

.login-page::before {
  content: "";
  position: absolute;
  inset: 0;
  pointer-events: none;
  background-image:
      linear-gradient(rgba(22, 93, 255, 0.038) 1px, transparent 1px),
      linear-gradient(90deg, rgba(22, 93, 255, 0.038) 1px, transparent 1px);
  background-size: 44px 44px;
  mask-image: linear-gradient(90deg, #000 0%, rgba(0, 0, 0, 0.62) 48%, transparent 88%);
}

.login-page::after {
  content: "";
  position: absolute;
  inset: 9% 7% 12% 46%;
  pointer-events: none;
  border-radius: 42px;
  background: radial-gradient(circle at 50% 48%, rgba(255, 255, 255, 0.78), transparent 62%);
  filter: blur(8px);
}

.brand-panel,
.login-panel {
  position: relative;
  z-index: 1;
}

.brand-panel {
  display: flex;
  align-items: center;
  padding: 64px clamp(44px, 5vw, 88px);
}

.brand-content {
  max-width: 650px;
}

.brand-mark {
  display: inline-flex;
  align-items: center;
  gap: 12px;
  padding: 7px 13px 7px 8px;
  border: 1px solid rgba(22, 93, 255, 0.12);
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.72);
  color: var(--app-text-secondary);
  font-size: 13px;
  box-shadow: 0 10px 28px rgba(22, 93, 255, 0.05);
  backdrop-filter: blur(10px);
}

.brand-icon {
  width: 30px;
  height: 30px;
  display: grid;
  place-items: center;
  border-radius: 10px;
  background: var(--app-primary);
  color: #fff;
}

.brand-logo-image {
  width: 18px;
  height: 18px;
  object-fit: contain;
}

.brand-copy {
  margin-top: 46px;
}

.eyebrow {
  margin: 0 0 14px;
  color: var(--app-primary);
  font-size: 13px;
  font-weight: 700;
  letter-spacing: 0.14em;
  text-transform: uppercase;
}

.brand-copy h1 {
  margin: 0;
  color: #111827;
  font-size: clamp(42px, 4.8vw, 66px);
  line-height: 1.04;
  letter-spacing: -0.045em;
}

.subtitle {
  max-width: 590px;
  margin: 20px 0 0;
  color: var(--app-text-secondary);
  font-size: 17px;
  line-height: 1.78;
}

.tag-list {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-top: 30px;
}

.business-tag {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  padding: 8px 12px;
  border: 1px solid rgba(22, 93, 255, 0.12);
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.58);
  color: var(--app-text-secondary);
  font-size: 13px;
  box-shadow: 0 8px 22px rgba(22, 93, 255, 0.045);
}

.business-tag::before {
  content: "";
  width: 6px;
  height: 6px;
  border-radius: 999px;
  background: rgba(22, 93, 255, 0.58);
}

.warehouse-visual {
  position: relative;
  width: min(600px, 90vw);
  margin-top: 42px;
  padding: 18px;
  border: 1px solid rgba(22, 93, 255, 0.10);
  border-radius: 24px;
  background:
      linear-gradient(135deg, rgba(255, 255, 255, 0.86), rgba(244, 248, 255, 0.68)),
      radial-gradient(circle at 18% 18%, rgba(22, 93, 255, 0.08), transparent 34%);
  box-shadow: 0 22px 58px rgba(22, 93, 255, 0.08);
  overflow: hidden;
}

.warehouse-visual::before {
  content: "";
  position: absolute;
  inset: 0;
  background-image:
      linear-gradient(rgba(22, 93, 255, 0.035) 1px, transparent 1px),
      linear-gradient(90deg, rgba(22, 93, 255, 0.035) 1px, transparent 1px);
  background-size: 28px 28px;
  mask-image: linear-gradient(120deg, rgba(0, 0, 0, 0.72), transparent 76%);
}

.visual-header,
.capability-grid {
  position: relative;
  z-index: 1;
}

.visual-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 14px;
  color: var(--app-text-tertiary);
  font-size: 13px;
}

.visual-header strong {
  color: var(--app-primary);
  font-size: 12px;
  letter-spacing: 0.10em;
}

.capability-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.capability-item {
  display: flex;
  align-items: center;
  gap: 12px;
  min-height: 76px;
  padding: 14px;
  border: 1px solid rgba(229, 230, 235, 0.78);
  border-radius: 18px;
  background: rgba(255, 255, 255, 0.72);
  box-shadow: 0 10px 24px rgba(29, 33, 41, 0.04);
}

.capability-item.primary {
  border-color: rgba(22, 93, 255, 0.16);
  background: rgba(238, 244, 255, 0.78);
}

.capability-item.accent {
  border-color: rgba(0, 168, 112, 0.14);
}

.capability-icon {
  flex: 0 0 auto;
  width: 42px;
  height: 42px;
  display: grid;
  place-items: center;
  border-radius: 14px;
  background: #fff;
  color: var(--app-primary);
  font-size: 11px;
  font-weight: 800;
  letter-spacing: 0.05em;
  box-shadow: inset 0 0 0 1px rgba(22, 93, 255, 0.10);
}

.capability-item strong {
  display: block;
  color: var(--app-text);
  font-size: 14px;
  line-height: 1.2;
}

.capability-item p {
  margin: 7px 0 0;
  color: var(--app-text-tertiary);
  font-size: 12px;
}

.login-panel {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 64px clamp(32px, 4.5vw, 74px);
}

.login-card {
  width: min(100%, 520px);
  border: 1px solid rgba(229, 230, 235, 0.86);
  border-radius: 26px;
  background: rgba(255, 255, 255, 0.94);
  box-shadow: 0 24px 68px rgba(29, 33, 41, 0.105);
  backdrop-filter: blur(16px);
}

.login-card :deep(.el-card__body) {
  padding: 48px 52px 46px;
}

.card-heading {
  display: flex;
  align-items: center;
  gap: 18px;
  margin-bottom: 32px;
}

.card-logo {
  width: 58px;
  height: 58px;
  display: grid;
  place-items: center;
  border-radius: 18px;
  background: linear-gradient(135deg, var(--app-primary-light), rgba(255, 255, 255, 0.92));
  border: 1px solid rgba(22, 93, 255, 0.14);
  box-shadow: 0 12px 28px rgba(22, 93, 255, 0.08);
}

.card-logo-image {
  width: 30px;
  height: 30px;
  object-fit: contain;
}

.card-heading h2 {
  margin: 0;
  color: var(--app-text);
  font-size: 28px;
  line-height: 1.16;
  letter-spacing: -0.02em;
}

.card-heading p {
  margin: 7px 0 0;
  color: var(--app-text-tertiary);
  font-size: 14px;
}

.login-error {
  margin-bottom: 20px;
  border-radius: 14px;
  background: #fff6f5;
  border-color: #ffe0dc;
}

.login-error :deep(.el-alert__title) {
  color: #b42318;
}

.login-form :deep(.el-form-item) {
  margin-bottom: 24px;
}

.login-form :deep(.el-form-item__label) {
  color: var(--app-text-secondary);
  font-weight: 600;
  padding-right: 14px;
}

.login-form :deep(.el-input__wrapper) {
  min-height: 52px;
  padding: 0 15px;
  border-radius: 15px;
  box-shadow: 0 0 0 1px var(--app-border-soft) inset;
  transition: box-shadow 0.18s ease, background 0.18s ease;
}

.login-form :deep(.el-input__wrapper.is-focus) {
  box-shadow: 0 0 0 1px var(--app-primary) inset, 0 0 0 4px rgba(22, 93, 255, 0.08);
}

.login-form :deep(.el-input__prefix) {
  color: var(--app-text-tertiary);
}

.login-form :deep(.el-input__prefix-inner) {
  align-items: center;
}

.login-form :deep(.el-input__inner::placeholder) {
  color: #a8b0bd;
}

.login-form :deep(.el-form-item__error) {
  padding-top: 6px;
  color: #d14343;
  font-size: 12px;
}

.login-form :deep(.is-error .el-input__wrapper) {
  box-shadow: 0 0 0 1px rgba(245, 63, 63, 0.55) inset;
}

.form-options {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin: -2px 0 26px 72px;
}

.login-button {
  width: 100%;
  height: 52px;
  border: 0;
  border-radius: 15px;
  font-size: 16px;
  font-weight: 700;
  background: linear-gradient(135deg, #165dff 0%, #3678ff 100%);
  box-shadow: 0 14px 30px rgba(22, 93, 255, 0.20);
  transition: transform 0.18s ease, box-shadow 0.18s ease, filter 0.18s ease;
}

.login-button:not(.is-disabled):hover {
  transform: translateY(-1px);
  filter: saturate(1.05);
  box-shadow: 0 18px 36px rgba(22, 93, 255, 0.25);
}

.login-button:not(.is-disabled):active {
  transform: translateY(0);
}

.icp-footer {
  position: absolute;
  left: 0;
  right: 0;
  bottom: 18px;
  z-index: 2;
  text-align: center;
  font-size: 12px;
  color: var(--app-text-tertiary);
}

.icp-footer a {
  color: inherit;
  text-decoration: none;
}

.icp-footer a:hover {
  color: var(--app-primary);
  text-decoration: underline;
}

@media (max-width: 980px) {
  .login-page {
    min-height: 100dvh;
    grid-template-columns: 1fr;
    overflow-y: auto;
  }

  .login-page::before {
    mask-image: linear-gradient(180deg, #000 0%, rgba(0, 0, 0, 0.55) 48%, transparent 96%);
  }

  .brand-panel {
    padding: 42px 24px 18px;
  }

  .brand-content {
    max-width: none;
  }

  .brand-copy {
    margin-top: 24px;
  }

  .brand-copy h1 {
    font-size: 36px;
  }

  .subtitle {
    margin-top: 16px;
    font-size: 15px;
    line-height: 1.7;
  }

  .tag-list {
    margin-top: 20px;
  }

  .warehouse-visual {
    width: 100%;
    margin-top: 24px;
    padding: 14px;
  }

  .capability-grid {
    grid-template-columns: repeat(4, minmax(0, 1fr));
    gap: 8px;
  }

  .capability-item {
    min-height: 58px;
    padding: 10px;
  }

  .capability-icon {
    width: 32px;
    height: 32px;
    border-radius: 11px;
    font-size: 9px;
  }

  .capability-item p {
    display: none;
  }

  .capability-item strong {
    font-size: 12px;
  }

  .login-panel {
    padding: 18px 20px 76px;
    align-items: flex-start;
  }
}

@media (max-width: 520px) {
  .brand-panel {
    padding: 28px 16px 12px;
  }

  .brand-mark {
    font-size: 12px;
  }

  .brand-copy h1 {
    font-size: 30px;
  }

  .tag-list {
    gap: 8px;
  }

  .business-tag {
    padding: 7px 10px;
  }

  .login-panel {
    padding: 12px 14px 72px;
  }

  .warehouse-visual {
    display: none;
  }

  .login-card {
    border-radius: 20px;
  }

  .login-card :deep(.el-card__body) {
    padding: 32px 24px 30px;
  }

  .card-heading {
    align-items: flex-start;
  }

  .form-options {
    margin-left: 0;
  }

  .login-form {
    --el-form-label-font-size: 13px;
  }
}
</style>
