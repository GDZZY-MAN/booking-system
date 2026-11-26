<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ApiError, api, message, type Viewer } from '../api'
import SiteHeader from '../components/SiteHeader.vue'
import WorkspaceNav from '../components/WorkspaceNav.vue'
import Merchants from './admin/Merchants.vue'
import Overview from './admin/Overview.vue'
import Venues from './admin/Venues.vue'
const route = useRoute(), router = useRouter()
const viewer = ref<Viewer>(), error = ref('')
const sections = [
  { id: 'overview', label: '运营总览', to: '/admin' },
  { id: 'merchants', label: '商家审核与管理', to: '/admin?tab=merchants' },
  { id: 'venues', label: '校园空间管理', to: '/admin?tab=venues' },
]
const tab = computed(() => ['merchants', 'venues'].includes(String(route.query.tab)) ? String(route.query.tab) : 'overview')
const groups = computed(() => [{ title: '平台运营', items: sections.map(item => ({ ...item, active: tab.value === item.id })) }])
async function load() {
  error.value = ''
  try {
    const me = await api<Viewer>('/auth/me')
    if (me.role !== 'ADMIN') error.value = '请使用平台管理员账号登录。'
    else viewer.value = me
  } catch (issue) {
    if (issue instanceof ApiError && issue.status === 401) await router.replace('/admin/login')
    else error.value = message(issue)
  }
}
void load()
</script>
<template>
  <SiteHeader context="admin" />
  <main id="main" class="workspace content-width">
    <p v-if="error" class="notice error" role="alert">{{ error }} <RouterLink to="/admin/login">管理员登录</RouterLink></p>
    <div v-if="viewer" class="workspace-body">
      <WorkspaceNav :groups="groups" />
      <div class="workspace-content">
        <div class="workspace-intro"><h1>{{ sections.find(item => item.id === tab)?.label }}</h1></div>
        <Merchants v-if="tab === 'merchants'" /><Venues v-else-if="tab === 'venues'" /><Overview v-else />
      </div>
    </div>
    <p v-else-if="!error" role="status">正在打开平台管理…</p>
  </main>
</template>
