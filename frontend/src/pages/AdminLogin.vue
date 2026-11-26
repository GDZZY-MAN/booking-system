<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { api, message, type Viewer } from '../api'
import { setSession } from '../session'
import SiteHeader from '../components/SiteHeader.vue'
const router = useRouter()
const username = ref(''), password = ref(''), error = ref(''), busy = ref(false)
async function login() {
  busy.value = true; error.value = ''
  try {
    const viewer = await api<Viewer>('/auth/admin/login', 'POST', { username: username.value, password: password.value })
    setSession(viewer)
    await router.replace('/admin')
  }
  catch (issue) { error.value = message(issue) } finally { busy.value = false }
}
</script>
<template>
  <SiteHeader context="admin" />
  <main id="main" class="narrow panel page-space auth-panel"><p class="section-label">有约 · 平台管理</p><h1>平台管理员登录</h1>
    <p class="muted">统一审核入驻商家，管理校园内的图书馆、自习室和研讨间。管理员账号由平台负责人配置，不开放自主注册。</p>
    <form class="form-stack" @submit.prevent="login"><label>管理员账号<input v-model.trim="username" required autocomplete="username" /></label>
      <label>密码<input v-model="password" type="password" required autocomplete="current-password" /></label>
      <p v-if="error" class="notice error" role="alert">{{ error }}</p><button class="button button-dark" :disabled="busy">{{ busy ? '登录中…' : '登录平台后台' }}</button></form>
    <p class="auth-switch">商家请使用 <RouterLink to="/login">商家 / 员工登录</RouterLink>。</p>
  </main>
</template>
