<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { api, message, type Viewer } from '../api'
import SiteHeader from '../components/SiteHeader.vue'

const props = defineProps<{ mode: 'login' | 'register' | 'invite' }>()
const route = useRoute()
const router = useRouter()
const username = ref('')
const password = ref('')
const shopName = ref('')
const slug = ref('')
const pending = ref(false)
const error = ref('')
const inviteToken = computed(() => route.hash.slice(1))
const title = computed(() => ({ login: '登录工作台', register: '开通你的预约页', invite: '开通员工账号' })[props.mode])

async function submit() {
  pending.value = true
  error.value = ''
  try {
    const credentials = { username: username.value, password: password.value }
    const viewer = props.mode === 'register'
      ? await api<Viewer>('/auth/register', 'POST', { ...credentials, shopName: shopName.value, slug: slug.value })
      : props.mode === 'invite'
        ? await api<Viewer>('/auth/accept', 'POST', { ...credentials, token: inviteToken.value })
        : await api<Viewer>('/auth/login', 'POST', credentials)
    await router.replace(viewer.role === 'OWNER' ? '/owner' : '/employee')
  } catch (issue) {
    error.value = message(issue)
  } finally {
    pending.value = false
  }
}
</script>

<template>
  <SiteHeader />
  <main id="main" class="auth-layout content-width">
    <aside class="auth-aside">
      <span class="auth-symbol" aria-hidden="true">✳</span>
      <h2>给你的店，<br />留一张预约名片。</h2>
      <p>服务、排班和订单，都在一个工作台。</p>
    </aside>
    <section class="panel auth-panel">
      <h1>{{ title }}</h1>
      <p v-if="mode === 'login'" class="muted">店主和员工使用各自的账号登录。</p>
      <p v-if="mode === 'invite'" class="muted">完成注册后即可查看分配给你的预约。</p>
      <p v-if="mode === 'invite' && !inviteToken" class="notice error" role="alert">邀请链接不完整，请联系店主获取新链接。</p>
      <form v-else class="form-stack" @submit.prevent="submit">
        <template v-if="mode === 'register'">
          <label>店铺名称<input v-model.trim="shopName" maxlength="80" required placeholder="例如 青禾工作室" /></label>
          <label>预约链接标识
            <input v-model.trim="slug" required minlength="3" maxlength="60"
              pattern="[a-z0-9][a-z0-9\-]{1,58}[a-z0-9]" placeholder="qinghe-studio" autocapitalize="none" />
            <small class="muted">公开地址为 /s/{{ slug || '你的店铺标识' }}，发布后可分享。</small>
          </label>
        </template>
        <label>账号<input v-model.trim="username" minlength="3" maxlength="80" autocomplete="username" required autocapitalize="none" /></label>
        <label>密码<input v-model="password" type="password" minlength="8" maxlength="72"
          :autocomplete="mode === 'login' ? 'current-password' : 'new-password'" required /></label>
        <p v-if="error" class="notice error" role="alert">{{ error }}</p>
        <button class="button button-dark" :disabled="pending">{{ pending ? '正在提交…' : title }}</button>
      </form>
      <p v-if="mode === 'login'" class="auth-switch">新店主？ <RouterLink to="/register">开通店铺 ↗</RouterLink></p>
      <p v-if="mode === 'register'" class="auth-switch">已有账号？ <RouterLink to="/login">登录 ↗</RouterLink></p>
    </section>
  </main>
</template>
