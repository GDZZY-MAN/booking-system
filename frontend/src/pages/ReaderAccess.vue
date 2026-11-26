<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { api, message, type Viewer } from '../api'
import { setSession } from '../session'
import SiteHeader from '../components/SiteHeader.vue'

const props = defineProps<{ register: boolean }>()
const route = useRoute()
const router = useRouter()
const username = ref('')
const password = ref('')
const pending = ref(false)
const error = ref('')
const next = computed(() => typeof route.query.next === 'string' && /^\/(library|s)\/[a-z0-9-]+$/.test(route.query.next)
  ? route.query.next : '/reader')
async function submit() {
  pending.value = true
  error.value = ''
  try {
    const viewer = await api<Viewer>('/auth/reader/' + (props.register ? 'register' : 'login'), 'POST', { username: username.value, password: password.value })
    setSession(viewer)
    await router.replace(next.value)
  } catch (issue) { error.value = message(issue) }
  finally { pending.value = false }
}
</script>

<template>
  <SiteHeader show-home />
  <main id="main" class="narrow page-space panel auth-panel">
    <p class="section-label">有约 · 我的安排</p><h1>{{ register ? '注册校园账号' : '校园账号登录' }}</h1>
    <p class="muted">用同一个账号预约空间和服务，在“我的安排”查看时间表、取消预约和发起同馆交流。</p>
    <form class="form-stack" @submit.prevent="submit">
      <label>校园账号<input v-model.trim="username" required minlength="3" maxlength="80" autocomplete="username" autocapitalize="none" /></label>
      <label>密码<input v-model="password" type="password" required minlength="8" maxlength="72"
        :autocomplete="register ? 'new-password' : 'current-password'" /></label>
      <p v-if="error" class="notice error" role="alert">{{ error }}</p>
      <button class="button button-dark" :disabled="pending">{{ pending ? '正在提交…' : register ? '注册并登录' : '登录' }}</button>
    </form>
    <p class="auth-switch"><RouterLink :to="{ path: register ? '/reader/login' : '/reader/register', query: { next } }">{{ register ? '已有校园账号？去登录' : '没有校园账号？去注册' }}</RouterLink></p>
  </main>
</template>
