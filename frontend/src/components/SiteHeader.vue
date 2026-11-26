<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import Brand from './Brand.vue'
import BackButton from './BackButton.vue'
import ActionMenu from './ActionMenu.vue'
import { message, workspaceFor } from '../api'
import { session, signOut } from '../session'
import { unreadMessages } from '../readerInbox'
const props = defineProps<{ showHome?: boolean; context?: 'admin' | 'merchant' | 'employee'; readerLogin?: () => void }>()
const route = useRoute(), router = useRouter()
const busy = ref(false), error = ref('')
const workspaceNames = { OWNER: '商家工作台', EMPLOYEE: '员工工作台', ADMIN: '平台管理', READER: '我的安排' }
const active = computed(() => route.path.startsWith('/library') ? '/libraries'
  : route.path.startsWith('/s/') ? '/services' : route.path.startsWith('/reader') ? '/reader' : route.path)
const loginTarget = computed(() => ({ path: '/reader/login', query: { next: /^\/(library|s)\/[a-z0-9-]+$/.test(route.path) ? route.path : '/reader' } }))
async function logout() {
  busy.value = true; error.value = ''
  try {
    await signOut()
    await router.push(props.context === 'admin' ? '/admin/login' : props.context ? '/login' : '/reader/login')
  } catch (issue) { error.value = message(issue) }
  finally { busy.value = false }
}
</script>
<template>
  <header class="site-header">
    <div class="site-header-inner clean-header">
      <div class="header-brand"><Brand /><span v-if="context" class="workspace-label">{{ context === 'admin' ? '平台管理' : context === 'employee' ? '员工工作台' : '商家工作台' }}</span></div>
      <nav v-if="!context" class="campus-nav" aria-label="平台导航">
        <RouterLink v-for="item in [{ to: '/libraries', label: '找座位' }, { to: '/services', label: '约服务' }, { to: '/reader', label: '我的安排' }]"
          :key="item.to" class="button button-small" :to="item.to" :aria-current="active === item.to ? 'page' : undefined">{{ item.label }}</RouterLink>
      </nav>
      <div class="header-account">
        <template v-if="session">
          <RouterLink v-if="session.role === 'READER'" class="button button-small" to="/reader/messages">消息 <span v-if="unreadMessages" class="unread-badge">{{ unreadMessages }}</span></RouterLink>
          <RouterLink v-else-if="!context" class="button button-small workspace-jump" :to="workspaceFor(session.role)">{{ workspaceNames[session.role] }}</RouterLink>
          <ActionMenu :label="session.username">
            <RouterLink :to="workspaceFor(session.role)">{{ workspaceNames[session.role] }}</RouterLink>
            <template v-if="session.role !== 'READER'">
              <button v-if="readerLogin" type="button" @click="readerLogin">切换校园账号</button>
              <RouterLink v-else :to="loginTarget">切换校园账号</RouterLink>
            </template>
            <button type="button" :disabled="busy" @click="logout">退出登录</button>
          </ActionMenu>
        </template>
        <template v-else-if="!context">
          <button v-if="readerLogin" class="button button-small" type="button" @click="readerLogin">校园账号登录</button>
          <RouterLink v-else class="button button-small" :to="loginTarget">校园账号登录</RouterLink>
        </template>
        <ActionMenu v-if="!context" label="商家入口">
          <RouterLink to="/login">商家 / 员工登录</RouterLink>
          <RouterLink to="/register">商家注册入驻</RouterLink>
        </ActionMenu>
      </div>
    </div>
    <div v-if="route.path !== '/'" class="return-navigation content-width" aria-label="返回导航">
      <BackButton /><RouterLink class="button button-small" to="/">返回首页</RouterLink>
    </div>
    <p v-if="error" class="notice error content-width" role="alert">{{ error }}</p>
  </header>
</template>
