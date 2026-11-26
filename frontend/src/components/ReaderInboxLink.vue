<script setup lang="ts">
import { computed, onUnmounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { api } from '../api'
import { session } from '../session'
import { inboxChats as chats, unreadMessages as count, refreshInbox, syncInbox } from '../readerInbox'
const route = useRoute()
const router = useRouter()
const reader = computed(() => session.value?.role === 'READER')
const busy = ref(false)
let version = 0
let stopped = false
let timer: ReturnType<typeof setTimeout> | undefined
async function refresh(token: number) {
  if (stopped || token !== version) return
  await refreshInbox()
}
function schedule(token: number) {
  if (stopped || token !== version || !reader.value) return
  timer = setTimeout(async () => {
    if (!document.hidden) await refresh(token)
    schedule(token)
  }, 15000)
}
watch([() => session.value?.readerId, () => route.path], async ([readerId], previous) => {
  const token = ++version
  clearTimeout(timer)
  if (!reader.value || readerId !== previous?.[0]) syncInbox([], [])
  if (reader.value && route.path !== '/reader/messages') { await refresh(token); schedule(token) }
}, { immediate: true })
onUnmounted(() => { stopped = true; clearTimeout(timer) })
const pending = () => chats.value.filter(chat => chat.incoming && chat.status === 'PENDING')
async function act(action: 'accept' | 'decline') {
  const chat = pending()[0]
  if (!chat || busy.value) return
  busy.value = true
  try {
    await api('/reader/social/chats/' + chat.id + '/' + action, 'POST')
    await refresh(version)
    if (action === 'accept') await router.push({ path: '/reader/messages', query: { chat: chat.id } })
  } finally { busy.value = false }
}
async function view() {
  const chat = pending()[0]
  if (chat) await router.push({ path: '/reader/messages', query: { chat: chat.id } })
}
</script>
<template>
  <aside v-if="reader && pending().length" class="reader-invitation-prompt" role="region" aria-label="新的聊天邀请" aria-live="polite">
    <p class="reader-invitation-kicker">新的聊天邀请</p>
    <strong>一位同馆读者想和你聊天</strong>
    <small>{{ pending().length > 1 ? '还有 ' + (pending().length - 1) + ' 条邀请等待处理' : '邀请会一直保留，等你决定。' }}</small>
    <div class="reader-invitation-actions">
      <button class="button button-dark" :disabled="busy" @click="act('accept')">接受</button>
      <button class="button button-outline" :disabled="busy" @click="act('decline')">拒绝</button>
      <button class="text-button" :disabled="busy" @click="view">查看消息</button>
    </div>
  </aside>
  <RouterLink v-if="reader && count && route.path !== '/reader/messages'" class="button button-small reader-inbox-link" to="/reader/messages">
    消息与聊天 <span role="status">{{ count }} 条未读</span>
  </RouterLink>
</template>
