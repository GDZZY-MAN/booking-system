<script setup lang="ts">
import { computed, defineAsyncComponent, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ApiError, api, message, workspaceFor, type Viewer } from '../api'
import SiteHeader from '../components/SiteHeader.vue'
import WorkspaceNav from '../components/WorkspaceNav.vue'

const props = defineProps<{ role: Viewer['role'] }>()
const route = useRoute(), router = useRouter()
const sections = [
  { id: 'overview', label: '今日待办', group: '经营' },
  { id: 'orders', label: '预约日历', group: '经营' },
  { id: 'services', label: '服务项目', group: '服务配置' },
  { id: 'team', label: '员工与排班', group: '服务配置' },
  { id: 'shop', label: '店铺与入驻', group: '店铺设置' },
  { id: 'branding', label: '店铺展示', group: '店铺设置' },
  { id: 'share', label: '分享推广', group: '店铺设置' },
]
const pages = {
  overview: defineAsyncComponent(() => import('./workspace/Overview.vue')),
  share: defineAsyncComponent(() => import('./workspace/Share.vue')),
  shop: defineAsyncComponent(() => import('./workspace/Shop.vue')),
  branding: defineAsyncComponent(() => import('./workspace/Branding.vue')),
  services: defineAsyncComponent(() => import('./workspace/Services.vue')),
  team: defineAsyncComponent(() => import('./workspace/Team.vue')),
  orders: defineAsyncComponent(() => import('./workspace/Orders.vue')),
}
type Section = keyof typeof pages
const active = computed<Section>(() => props.role === 'EMPLOYEE' ? 'orders'
  : (sections.find(section => section.id === route.query.tab)?.id ?? 'overview') as Section)
const groups = computed(() => ['经营', '服务配置', '店铺设置'].map(title => ({
  title, items: sections.filter(item => item.group === title).map(item => ({
    label: item.label, to: { path: '/owner', query: { tab: item.id } }, active: active.value === item.id,
  })),
})))
const viewer = ref<Viewer>(), pending = ref(false), error = ref('')
async function load() {
  pending.value = true; error.value = ''
  try {
    const me = await api<Viewer>('/auth/me')
    if (me.role !== props.role) await router.replace(workspaceFor(me.role))
    else viewer.value = me
  } catch (issue) {
    if (issue instanceof ApiError && issue.status === 401) await router.replace('/login')
    else error.value = message(issue)
  } finally { pending.value = false }
}
watch(() => props.role, load, { immediate: true })
watch(() => route.query.tab, tab => {
  if (tab === 'library') void router.replace('/admin?tab=venues')
}, { immediate: true })
</script>
<template>
  <SiteHeader :context="role === 'EMPLOYEE' ? 'employee' : 'merchant'" />
  <main id="main" class="workspace content-width">
    <p v-if="pending" role="status">正在打开工作台…</p>
    <div v-else-if="error" class="panel"><p role="alert">{{ error }}</p><button class="button" @click="load">重试</button></div>
    <div v-else-if="viewer" class="workspace-body" :class="{ 'staff-workspace': role === 'EMPLOYEE' }">
      <WorkspaceNav v-if="role === 'OWNER'" :groups="groups" />
      <div class="workspace-content">
        <div class="workspace-intro"><h1>{{ sections.find(item => item.id === active)?.label }}</h1></div>
        <component :is="pages[active]" :key="active" v-bind="active === 'orders' ? { role } : {}" />
      </div>
    </div>
  </main>
</template>
