<template>
  <main class="not-found-page">
    <section class="panel not-found-card">
      <span class="eyebrow">页面提示</span>
      <h1>没有找到这个页面</h1>
      <p>链接可能已失效，或地址输入有误。您可以返回工作台继续管理社区服务。</p>
      <div class="not-found-actions">
        <el-button type="primary" size="large" @click="goDashboard">返回工作台</el-button>
        <el-button size="large" @click="goBack">返回上一页</el-button>
      </div>
    </section>
  </main>
</template>

<script setup>
import { useRouter } from 'vue-router'
import { hasAdminSession } from '../../utils/admin-session.js'

const router = useRouter()

function goDashboard() {
  if (hasAdminSession(localStorage)) {
    router.replace('/dashboard')
    return
  }
  router.replace('/login')
}

function goBack() {
  // 只有站内存在上一页时才后退，避免退回站外页面（RT6）
  if (router.options.history.state?.back) {
    router.back()
    return
  }
  goDashboard()
}
</script>

<style scoped>
.not-found-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 32px 20px;
  background: var(--page-bg);
}
.not-found-card {
  width: min(560px, 100%);
  padding: 40px 36px;
  text-align: left;
}
.not-found-card h1 {
  margin: 8px 0 12px;
  font-size: 28px;
  line-height: 1.4;
  font-weight: 650;
  color: var(--ink);
}
.not-found-card p {
  color: var(--muted);
  font-size: 14px;
  line-height: 1.75;
}
.not-found-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  margin-top: 28px;
}
</style>
