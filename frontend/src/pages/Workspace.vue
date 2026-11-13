<script setup lang="ts">
import { computed, defineAsyncComponent, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ApiError, api, message, type Viewer } from '../api'
import Brand from '../components/Brand.vue'

const props = defineProps<{ role: Viewer['role'] }>()
const route = useRoute()
const router = useRouter()
const sections = [
  { id: 'shop', label: '店铺与发布', symbol: '⌂' },
  { id: 'services', label: '服务项目', symbol: '✦' },
  { id: 'team', label: '员工与排班', symbol: '◷' },
  { id: 'orders', label: '预约订单', symbol: '▤' },
]
const pages = {
  shop: defineAsyncComponent(() => import('./workspace/Shop.vue')),
  services: defineAsyncComponent(() => import('./workspace/Services.vue')),
  team: defineAsyncComponent(() => import('./workspace/Team.vue')),
  orders: defineAsyncComponent(() => import('./workspace/Orders.vue')),
}
type Section = keyof typeof pages
const active = computed<Section>(() => props.role === 'EMPLOYEE' ? 'orders'
  : sections.find(section => section.id === route.query.tab)?.id as Section ?? 'shop')
const viewer = ref<Viewer>()
const pending = ref(false)
const error = ref('')
const actionError = ref('')
const loggingOut = ref(false)

async function load() {
  pending.value = true
  error.value = ''
  try {
    const me = await api<Viewer>('/auth/me')
    if (me.role !== props.role) await router.replace(me.role === 'OWNER' ? '/owner' : '/employee')
    else viewer.value = me
  } catch (issue) {
    if (issue instanceof ApiError && issue.status === 401) await router.replace('/login')
    else error.value = message(issue)
  } finally { pending.value = false }
}

async function logout() {
  loggingOut.value = true
  actionError.value = ''
  try {
    await api('/auth/logout', 'POST')
    await router.replace('/login')
  } catch (issue) { actionError.value = message(issue) }
  finally { loggingOut.value = false }
}

watch(() => props.role, load, { immediate: true })
</script>

<template>
  <header class="site-header">
    <div class="site-header-inner">
      <Brand />
      <div v-if="viewer" class="workspace-account"><span>{{ viewer.username }}</span>
        <button class="button button-ghost" :disabled="loggingOut" @click="logout">退出登录</button>
      </div>
    </div>
  </header>
  <main id="main" class="workspace content-width">
    <div class="workspace-intro">
      <p class="section-label">{{ role === 'OWNER' ? '商家' : '员工' }}</p>
      <h1>{{ role === 'OWNER' ? '店铺工作台' : '我的工作台' }}</h1>
    </div>
    <p v-if="actionError" class="notice error" role="alert">{{ actionError }}</p>
    <p v-if="pending" class="panel" role="status">正在打开工作台…</p>
    <div v-else-if="error" class="panel"><p role="alert">{{ error }}</p><button class="button button-outline" @click="load">重试</button></div>
    <div v-else-if="viewer" class="workspace-body" :class="{ 'staff-workspace': role === 'EMPLOYEE' }">
      <nav v-if="role === 'OWNER'" class="workspace-nav" aria-label="工作台功能">
        <RouterLink v-for="section in sections" :key="section.id" :to="{ path: '/owner', query: { tab: section.id } }"
          :class="{ current: active === section.id }" :aria-current="active === section.id ? 'page' : undefined">
          <span aria-hidden="true">{{ section.symbol }}</span>{{ section.label }}
        </RouterLink>
      </nav>
      <div class="workspace-content">
        <component :is="pages[active]" :key="active" v-bind="active === 'orders' ? { role } : {}" />
      </div>
    </div>
  </main>
</template>
