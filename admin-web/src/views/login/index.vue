<template>
  <main class="login-page">
    <img
      v-if="!photoFailed"
      class="login-photo"
      src="/images/login-care.jpg"
      alt=""
      aria-hidden="true"
      fetchpriority="high"
      @error="photoFailed = true"
    />
    <div class="login-shade" aria-hidden="true"></div>
    <div class="login-shell">
      <header class="login-brand">
        <img src="/brand/yilin-icon.png" alt="颐邻" width="56" height="56" />
        <div>
          <strong>
            颐邻
            <span>.</span>
          </strong>
          <small>社区居家养老服务</small>
        </div>
      </header>
      <div class="login-main">
        <section class="login-story" aria-labelledby="story-title">
          <p class="story-eyebrow">熟悉的社区，用心的照护</p>
          <h1 id="story-title">
            把照护带到家，
            <br />
            把安心留给家人。
          </h1>
          <p class="story-description">
            连接社区、家属与服务人员，
            <br />
            让每一次预约，都有妥帖的安排。
          </p>
          <div class="story-services">
            <span>预约服务</span>
            <i></i>
            <span>人员安排</span>
            <i></i>
            <span>上门照护</span>
          </div>
        </section>
        <section class="login-panel" aria-labelledby="login-title">
          <span class="panel-eyebrow">社区管理工作台</span>
          <h2 id="login-title">欢迎回来</h2>
          <p class="login-description">登录管理账号，开始今天的社区服务工作。</p>
          <el-form
            ref="loginFormRef"
            :model="loginForm"
            :rules="rules"
            label-position="top"
            @submit.prevent="handleLogin"
          >
            <el-form-item label="账号" prop="username">
              <el-input
                v-model="loginForm.username"
                placeholder="请输入管理员账号"
                size="large"
                autocomplete="username"
              />
            </el-form-item>
            <el-form-item label="密码" prop="password">
              <el-input
                v-model="loginForm.password"
                type="password"
                placeholder="请输入登录密码"
                size="large"
                show-password
                autocomplete="current-password"
              />
            </el-form-item>
            <el-button native-type="submit" class="login-submit" size="large" :loading="loading" :disabled="loading">
              {{ loading ? '正在登录…' : '登录工作台' }}
            </el-button>
          </el-form>
          <p class="login-help">账号使用遇到问题，请联系社区管理员</p>
          <details v-if="showDemo" class="demo-access">
            <summary>演示环境 · 快速进入</summary>
            <p>仅供本地演示与页面测试使用</p>
            <el-button :loading="loading" :disabled="loading" @click="mockLogin('admin')">使用管理员演示账号</el-button>
          </details>
        </section>
      </div>
      <footer class="login-footer">
        <span>颐邻 · 社区居家养老服务平台</span>
        <span>让照护，离家更近一点。</span>
      </footer>
    </div>
  </main>
</template>
<script setup>
import { ref, reactive } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import request from '../../utils/request'
import { safeRedirect, saveSession } from '../../utils/admin-session.js'
const router = useRouter()
const route = useRoute()
const photoFailed = ref(false)
const loginFormRef = ref(null),
  loading = ref(false)
const showDemo = import.meta.env.DEV || import.meta.env.VITE_SHOW_DEMO === 'true'
const loginForm = reactive({ username: '', password: '' })
const rules = {
  username: [{ required: true, message: '请输入管理员账号', trigger: 'blur' }],
  password: [{ required: true, message: '请输入登录密码', trigger: 'blur' }]
}
async function handleLogin() {
  if (loading.value) return
  const valid = await loginFormRef.value.validate().catch(() => false)
  if (!valid) return
  loading.value = true
  try {
    saveLogin(await request.post('/auth/login', loginForm))
  } catch {
  } finally {
    loading.value = false
  }
}
async function mockLogin(username) {
  if (loading.value) return
  loading.value = true
  try {
    saveLogin(await request.post('/auth/mock-login?username=' + encodeURIComponent(username)))
  } catch {
  } finally {
    loading.value = false
  }
}
function saveLogin(result) {
  if (result.userInfo?.role !== 'ADMIN') {
    ElMessage.warning('请使用管理员账号登录社区管理后台')
    return
  }
  saveSession(result)
  ElMessage.success('欢迎回来，' + (result.userInfo.realName || result.userInfo.username))
  router.replace(safeRedirect(route.query.redirect) || '/dashboard')
}
</script>
<style scoped>
.login-page {
  position: relative;
  isolation: isolate;
  min-height: 100vh;
  min-height: 100svh;
  background: #243f32;
  color: #fff;
}
.login-photo,
.login-shade {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  z-index: -1;
}
.login-photo {
  object-fit: cover;
  object-position: center 42%;
}
.login-shade {
  background:
    linear-gradient(90deg, rgba(12, 31, 22, 0.67), rgba(12, 31, 22, 0.18) 48%, rgba(12, 31, 22, 0.71)),
    linear-gradient(0deg, rgba(12, 31, 22, 0.58), transparent 70%);
}
.login-shell {
  min-height: 100vh;
  min-height: 100svh;
  display: flex;
  flex-direction: column;
  max-width: 1600px;
  margin: 0 auto;
  padding: 42px clamp(28px, 6vw, 96px) 26px;
}
.login-brand {
  display: flex;
  align-items: center;
  gap: 14px;
}
.login-brand > img {
  width: 56px;
  height: 56px;
  border-radius: 14px;
}
.login-brand strong {
  display: block;
  font-size: 30px;
  line-height: 1.15;
  letter-spacing: 3px;
  font-weight: 650;
}
.login-brand strong span {
  color: #edc087;
}
.login-brand small {
  display: block;
  margin-top: 7px;
  color: #f0eee3;
  font-size: 12px;
  letter-spacing: 2px;
}
.login-main {
  flex: 1;
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(350px, 430px);
  align-items: center;
  gap: clamp(35px, 7vw, 120px);
  padding: 50px 0;
}
.story-eyebrow {
  font-size: 14px;
  color: #f3eedf;
  letter-spacing: 3px;
}
.login-story h1 {
  margin: 24px 0;
  font-size: clamp(34px, 3.9vw, 58px);
  line-height: 1.6;
  font-weight: 600;
  letter-spacing: 1px;
}
.story-description {
  color: #f2eee3;
  font-size: 16px;
  line-height: 2;
}
.story-services {
  display: flex;
  align-items: center;
  gap: 22px;
  margin-top: 40px;
  color: #f4f0e5;
  font-size: 13px;
}
.story-services i {
  width: 25px;
  height: 1px;
  background: #c3cfc2;
}
.login-panel {
  padding: 36px;
  border: 1px solid rgba(255, 255, 255, 0.4);
  border-radius: 24px;
  background: rgba(19, 43, 31, 0.78);
  box-shadow: 0 24px 70px rgba(9, 24, 17, 0.2);
}
.panel-eyebrow {
  color: #e0ebdf;
  font-size: 12px;
  letter-spacing: 2px;
}
.login-panel h2 {
  margin: 12px 0;
  font-size: 34px;
  font-weight: 600;
  line-height: 1.4;
}
.login-description {
  margin-bottom: 28px;
  color: #e2e9df;
  font-size: 13px;
  line-height: 1.8;
}
.login-panel :deep(.el-form-item) {
  margin-bottom: 26px;
}
.login-panel :deep(.el-form-item__label) {
  color: #fff;
  font-size: 14px;
}
.login-panel :deep(.el-input__wrapper) {
  min-height: 48px;
  border-radius: 10px;
  background: rgba(251, 252, 247, 0.95);
  box-shadow: 0 0 0 1px rgba(255, 255, 255, 0.6) inset;
}
.login-panel :deep(.el-input__inner) {
  color: #243b30;
}
.login-panel :deep(.el-input__inner::placeholder) {
  color: #617366;
}
.login-panel :deep(.el-input__wrapper.is-focus) {
  box-shadow:
    0 0 0 2px #b4d7ba inset,
    0 0 0 3px rgba(180, 215, 186, 0.15);
}
.login-panel :deep(.el-form-item__error) {
  color: #ffdbca;
}
.login-panel .login-submit {
  width: 100%;
  min-height: 50px;
  margin-top: 6px;
  background: rgba(255, 255, 255, 0.21);
  border: 1px solid rgba(255, 255, 255, 0.65);
  border-radius: 12px;
  color: #fff;
  font-size: 16px;
  font-weight: 550;
  transition:
    transform 180ms ease,
    background-color 180ms ease;
}
.login-panel .login-submit:hover,
.login-panel .login-submit:focus-visible {
  background: rgba(255, 255, 255, 0.32);
}
.login-panel .login-submit:active {
  transform: scale(0.98);
}
.login-panel .login-submit.is-disabled {
  background: rgba(255, 255, 255, 0.15);
  color: #fff;
  opacity: 0.75;
}
.login-help {
  margin-top: 20px;
  color: #e3e9df;
  font-size: 12px;
  line-height: 1.8;
  text-align: center;
}
.demo-access {
  margin-top: 26px;
  padding-top: 20px;
  border-top: 1px solid rgba(255, 255, 255, 0.22);
  color: #e0e9db;
}
.demo-access summary {
  font-size: 12px;
  cursor: pointer;
  width: fit-content;
}
.demo-access p {
  margin: 12px 0;
  font-size: 12px;
}
.login-footer {
  display: flex;
  justify-content: space-between;
  gap: 20px;
  font-size: 12px;
  line-height: 1.6;
  color: #e0e6da;
}
@media (max-width: 1000px) {
  .login-shell {
    padding-inline: 32px;
  }
  .login-main {
    gap: 32px;
    grid-template-columns: minmax(0, 1fr) minmax(320px, 390px);
  }
  .login-story h1 {
    font-size: 34px;
  }
  .story-services {
    gap: 12px;
  }
  .login-panel {
    padding: 30px;
  }
}
@media (max-width: 720px) {
  .login-shell {
    padding: 26px 22px;
  }
  .login-photo {
    object-position: 45% center;
  }
  .login-main {
    display: flex;
    flex-direction: column;
    align-items: stretch;
    gap: 30px;
    padding: 36px 0;
  }
  .login-story h1 {
    font-size: 32px;
    margin: 14px 0;
    line-height: 1.5;
  }
  .story-description,
  .story-services {
    display: none;
  }
  .login-panel {
    width: 100%;
    max-width: 460px;
    align-self: center;
    padding: 28px;
  }
  .login-footer {
    flex-wrap: wrap;
  }
}
@media (prefers-reduced-motion: reduce) {
  .login-panel .login-submit {
    transition: none;
  }
}
</style>
