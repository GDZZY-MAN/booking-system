<script setup lang="ts">
import { computed, onUnmounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ApiError, api, dayOf, message, newRequestKey, timeOf, workspaceFor, type Viewer } from '../api'
import { chatStatus, reminders, type Chat, type ChatMessage, type Preferences, type QuietNotice, type SentNotice } from '../social'
import SiteHeader from '../components/SiteHeader.vue'
import { syncInbox, unreadMessages as unread } from '../readerInbox'
const root = '/reader/social'
const route = useRoute()
const router = useRouter()
const chats = ref<Chat[]>([])
const notices = ref<QuietNotice[]>([])
const sentNotices = ref<SentNotice[]>([])
const messages = ref<ChatMessage[]>([])
const preferences = ref<Preferences>({ nickname: '同馆读者', chatEnabled: true })
const selected = ref(typeof route.query.chat === 'string' ? route.query.chat : '')
const messageTab = ref<'chats' | 'reminders'>('chats')
const draft = ref('')
const wechat = ref('')
const share = ref(false)
const busy = ref(false)
const loading = ref(true)
const error = ref('')
const chatError = ref('')
const notice = ref('')
const active = computed(() => chats.value.find(chat => chat.id === selected.value))
let disposed = false
let poll: ReturnType<typeof setTimeout> | undefined
let lastSend: { id: string; text: string; key: string } | undefined

async function loadMessages() {
  const id = selected.value
  if (!id || !active.value || !['ACCEPTED', 'CLOSED'].includes(active.value.status)) { messages.value = []; return }
  try {
    const result = await api<ChatMessage[]>(root + '/chats/' + id + '/messages')
    if (disposed || selected.value !== id) return
    messages.value = result
    chatError.value = ''
    if (result.length) await api(root + '/chats/' + id + '/seen', 'POST', { through: result[result.length - 1].id })
  } catch (issue) { if (selected.value === id) chatError.value = message(issue) }
}
async function refresh() {
  const results = await Promise.allSettled([
    api<QuietNotice[]>(root + '/reminders'), api<Chat[]>(root + '/chats'), api<SentNotice[]>(root + '/sent-reminders'),
  ])
  if (disposed) return
  const [alerts, threads, sent] = results
  error.value = ''
  if (alerts.status === 'fulfilled') notices.value = alerts.value
  else error.value = message(alerts.reason)
  if (threads.status === 'fulfilled') chats.value = threads.value
  else error.value = message(threads.reason)
  if (sent.status === 'fulfilled') sentNotices.value = sent.value
  else error.value = message(sent.reason)
  if (alerts.status === 'fulfilled' && threads.status === 'fulfilled') syncInbox(alerts.value, threads.value)
  await loadMessages()
}
async function start() {
  clearTimeout(poll)
  try {
    const viewer = await api<Viewer>('/auth/me')
    if (viewer.role !== 'READER') { await router.replace(workspaceFor(viewer.role)); return }
    preferences.value = await api<Preferences>(root + '/preferences')
    await refresh()
  } catch (issue) {
    if (issue instanceof ApiError && issue.status === 401) await router.replace('/reader/login')
    else error.value = message(issue)
  } finally { loading.value = false; schedule() }
}
function schedule() {
  if (disposed) return
  poll = setTimeout(async () => {
    if (!document.hidden && !busy.value) await refresh()
    schedule()
  }, 15000)
}
async function open(chat: Pick<Chat, 'id'>) {
  selected.value = chat.id
  messages.value = []
  draft.value = ''
  share.value = false
  wechat.value = ''
  chatError.value = ''
  await loadMessages()
}
watch(() => route.query.chat, async id => {
  if (typeof id === 'string') {
    messageTab.value = 'chats'
    await open({ id })
    if (!active.value || active.value.status === 'PENDING') await refresh()
  } else { selected.value = ''; messages.value = []; draft.value = ''; share.value = false; chatError.value = '' }
})
function selectChat(chat: Chat) {
  if (route.query.chat === chat.id) void open(chat)
  else void router.push({ query: { ...route.query, chat: chat.id } })
}
function returnToChats() {
  const { chat, ...query } = route.query
  void router.replace({ query })
}
async function perform(path: string, body?: unknown) {
  busy.value = true
  error.value = ''
  notice.value = ''
  try { await api(root + path, 'POST', body); await refresh(); notice.value = '操作已完成。' }
  catch (issue) { error.value = message(issue) }
  finally { busy.value = false }
}
async function savePreferences() {
  busy.value = true
  error.value = ''
  try {
    preferences.value = await api<Preferences>(root + '/preferences', 'PUT', preferences.value)
    await refresh()
    notice.value = '昵称与聊天邀请设置已保存。'
  } catch (issue) { error.value = message(issue) }
  finally { busy.value = false }
}
async function send(shareWechat = false) {
  const id = selected.value
  const text = shareWechat ? '我自愿分享的微信号是：' + wechat.value.trim() : draft.value.trim()
  if (!id || !text || busy.value || (shareWechat && !wechat.value.trim())) return
  busy.value = true
  chatError.value = ''
  try {
    if (lastSend?.id !== id || lastSend.text !== text) lastSend = { id, text, key: newRequestKey() }
    await api(root + '/chats/' + id + '/messages', 'POST', { body: text, requestKey: lastSend.key })
    lastSend = undefined
    draft.value = ''
    wechat.value = ''
    share.value = false
    await refresh()
  } catch (issue) { chatError.value = message(issue) }
  finally { busy.value = false }
}
onUnmounted(() => { disposed = true; clearTimeout(poll) })
void start()
</script>

<template>
  <SiteHeader show-home />
  <main id="main" class="content-width page-space">
    <div class="panel-heading"><h1>消息与聊天 <small v-if="unread" class="status">{{ unread }} 条未读</small></h1>
      <RouterLink to="/reader" class="button button-small">返回我的安排</RouterLink></div>
    <div class="section-tabs" role="group" aria-label="消息分类">
      <button type="button" class="button button-small" :aria-pressed="messageTab === 'chats'" @click="messageTab = 'chats'">聊天</button>
      <button type="button" class="button button-small" :aria-pressed="messageTab === 'reminders'" @click="messageTab = 'reminders'">提醒 <span v-if="notices.some(item => !item.acknowledged)" class="unread-badge">{{ notices.filter(item => !item.acknowledged).length }}</span></button>
    </div>
    <p v-if="loading" role="status">正在加载消息…</p>
    <p v-if="error" class="notice error" role="alert">{{ error }} <button class="text-button" @click="start">重试</button></p>
    <p v-if="notice" class="notice success" role="status">{{ notice }}</p>
    <details class="social-settings compact-details">
      <summary>昵称与免打扰</summary>
      <form class="form-stack" @submit.prevent="savePreferences">
        <label>聊天昵称<input v-model.trim="preferences.nickname" maxlength="30" required /></label>
        <label class="check"><input v-model="preferences.chatEnabled" type="checkbox" /> 接受新的聊天邀请</label>
        <small class="muted">关闭后拒绝待处理的新邀请，已接受的聊天可继续。安静提醒仍可接收。</small>
        <button class="button button-dark" :disabled="busy">保存设置</button>
      </form>
    </details>
    <section v-if="messageTab === 'reminders'" class="panel social-notices">
      <h2>安静提醒</h2><p v-if="!notices.length" class="muted">暂时没有提醒。</p>
      <article v-for="item in notices" :key="item.id" class="social-notice">
        <p>{{ reminders[item.kind] }}</p><small class="muted">{{ dayOf(item.createdAt) }} {{ timeOf(item.createdAt) }}</small>
        <div class="actions"><button v-if="!item.acknowledged" class="button button-outline" :disabled="busy" @click="perform('/reminders/' + item.id + '/acknowledge')">收到，我会注意</button>
          <span v-else class="muted">已回应</span>
          <button class="button button-ghost" :disabled="busy" @click="perform('/reminders/' + item.id + '/block')">屏蔽该发送者</button></div>
      </article>
    </section>
    <details v-if="messageTab === 'reminders' && sentNotices.length" class="compact-details social-notices">
      <summary>我发出的提醒</summary>
      <article v-for="item in sentNotices" :key="item.id" class="social-notice">
        <p>{{ item.seatLabel }} · {{ item.acknowledged ? '对方已回应' : '已发送，等待回应' }}</p>
        <small class="muted">{{ dayOf(item.createdAt) }} {{ timeOf(item.createdAt) }}</small>
      </article>
    </details>
    <div v-if="messageTab === 'chats'" class="social-layout message-layout" :class="{ 'conversation-open': selected }">
      <section class="panel conversation-list">
        <h2>聊天邀请与会话</h2><p v-if="!chats.length" class="muted">暂时没有聊天。</p>
        <ul class="social-threads"><li v-for="chat in chats" :key="chat.id">
          <button class="social-thread" :class="{ current: selected === chat.id }" :disabled="busy" @click="selectChat(chat)">
            <strong>{{ chat.nickname }}</strong><span>{{ chatStatus(chat) }}{{ chat.unread ? ' · ' + chat.unread + ' 条未读' : '' }}</span>
          </button></li></ul>
      </section>
      <section class="panel social-conversation">
        <button v-if="selected" type="button" class="nav-link conversation-back" @click="returnToChats">‹ 返回会话列表</button>
        <p v-if="!active" class="muted">选择一个聊天邀请或会话。</p>
        <template v-else>
          <div class="panel-heading"><h2>{{ active.nickname }}</h2><span class="status">{{ chatStatus(active) }}</span></div>
          <template v-if="active.status === 'PENDING'">
            <p>{{ active.incoming ? '有位同馆读者想和你聊聊。你可以稍后再处理；在你选择前，邀请会一直保留。' : '邀请已发送，等待对方接受。' }}</p>
            <div v-if="active.incoming" class="actions">
              <button class="button button-dark" :disabled="busy" @click="perform('/chats/' + active.id + '/accept')">接受邀请</button>
              <button class="button button-outline" :disabled="busy" @click="perform('/chats/' + active.id + '/decline')">拒绝邀请</button>
            </div>
          </template>
          <div v-if="['ACCEPTED', 'CLOSED'].includes(active.status)" class="chat-history" aria-label="最近 100 条聊天消息">
            <p v-if="!messages.length" class="muted">还没有消息。</p>
            <article v-for="entry in messages" :key="entry.id" class="chat-bubble" :class="{ mine: entry.mine }">
              <small>{{ entry.mine ? '我' : active.nickname }} · {{ timeOf(entry.createdAt) }}</small><p>{{ entry.body }}</p>
            </article>
          </div>
          <p v-if="chatError" class="notice error" role="alert">{{ chatError }} <button class="text-button" @click="loadMessages">刷新会话</button></p>
          <template v-if="active.status === 'ACCEPTED'">
            <form class="chat-compose" @submit.prevent="send(false)">
              <label>聊天消息<input v-model="draft" maxlength="500" required :disabled="busy" autocomplete="off" /></label>
              <button class="button button-dark" :disabled="busy || !draft.trim()">发送</button>
            </form>
            <button class="text-button" :disabled="busy" @click="share = !share">自愿分享我的微信号</button>
            <form v-if="share" class="form-stack notice" @submit.prevent="send(true)">
              <label>我的微信号<input v-model.trim="wechat" maxlength="80" required :disabled="busy" autocomplete="off" /></label>
              <small>确认后，微信号会作为消息发送给对方。</small>
              <button class="button button-outline" :disabled="busy || !wechat">确认分享微信号</button>
            </form>
          </template>
          <div class="actions">
            <button v-if="active.status === 'PENDING' && !active.incoming" class="button button-ghost" :disabled="busy" @click="perform('/chats/' + active.id + '/withdraw')">撤回邀请</button>
            <button v-if="active.status === 'ACCEPTED'" class="button button-ghost" :disabled="busy" @click="perform('/chats/' + active.id + '/close')">结束会话</button>
            <button class="button button-ghost" :disabled="busy" @click="perform('/chats/' + active.id + '/block')">屏蔽对方</button>
          </div>
        </template>
      </section>
    </div>
  </main>
</template>
