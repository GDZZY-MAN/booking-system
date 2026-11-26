<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ApiError, api, dayOf, message, timeRange } from '../api'
import { reminders, type Chat, type ContactSeat } from '../social'
import SeatMap from '../components/SeatMap.vue'
import SiteHeader from '../components/SiteHeader.vue'
const props = defineProps<{ slug: string }>()
const router = useRouter()
const seats = ref<ContactSeat[]>([])
const view = ref<'readers' | 'map'>('readers')
const search = ref('')
const selected = ref('')
const period = ref('')
const kind = ref<keyof typeof reminders>('VOICE')
const error = ref('')
const notice = ref('')
const loading = ref(false)
const busy = ref(false)
const target = computed(() => seats.value.find(seat => seat.reservationId && seat.reservationId === selected.value))
const periodKey = (seat: ContactSeat) => seat.startsAt && seat.endsAt ? seat.startsAt + '/' + seat.endsAt : ''
const periodLabel = (seat: ContactSeat) => seat.startsAt && seat.endsAt ? dayOf(seat.startsAt) + ' · ' + timeRange({ startsAt: seat.startsAt, endsAt: seat.endsAt }) : '预约时间'
const periods = computed(() => [...new Map(seats.value.filter(seat => seat.available && seat.reservationId)
  .map(seat => [periodKey(seat), periodLabel(seat)])).entries()])
watch(periods, values => {
  if (!values.some(([key]) => key === period.value)) period.value = values[0]?.[0] ?? ''
})
const mapSeats = computed(() => {
  const physical = new Map<number, ContactSeat>()
  for (const seat of seats.value) {
    if (!physical.has(seat.id)) physical.set(seat.id, { ...seat, available: false, reservationId: null, nickname: null, chatEnabled: false, remindersEnabled: false })
    if (seat.available && periodKey(seat) === period.value) physical.set(seat.id, seat)
  }
  return [...physical.values()]
})
const selectedSeat = computed(() => mapSeats.value.find(seat => seat.reservationId && seat.reservationId === selected.value)?.id ?? 0)
function choose(seat: ContactSeat) { period.value = periodKey(seat); selected.value = seat.reservationId ?? '' }
function chooseMapSeat(id: number) {
  selected.value = mapSeats.value.find(seat => seat.id === id && seat.available)?.reservationId ?? ''
}
const contacts = computed(() => seats.value.filter(seat => seat.available && seat.reservationId && seat.chatEnabled))
const filtered = computed(() => {
  const query = search.value.trim().toLocaleLowerCase()
  return contacts.value.filter(seat => [seat.nickname || '同馆读者', seat.label, seat.area, seat.floor ? seat.floor + 'F' : '']
    .join(' ').toLocaleLowerCase().includes(query))
})
const path = '/reader/social/venues/' + encodeURIComponent(props.slug)
function onTabKey(event: KeyboardEvent) {
  if (busy.value || !['ArrowLeft', 'ArrowRight', 'Home', 'End'].includes(event.key)) return
  event.preventDefault()
  view.value = event.key === 'Home' ? 'readers' : event.key === 'End' ? 'map' : view.value === 'readers' ? 'map' : 'readers'
  document.getElementById(view.value === 'readers' ? 'contact-readers-tab' : 'contact-map-tab')?.focus()
}
async function load() {
  loading.value = true
  error.value = ''
  selected.value = ''
  try { seats.value = await api<typeof seats.value>(path + '/seats') }
  catch (issue) {
    seats.value = []
    if (issue instanceof ApiError && issue.status === 401) {
      error.value = '请先使用读者账号登录，并预约本场所座位。'
    } else error.value = message(issue)
  } finally { loading.value = false }
}
async function send(invite: boolean, recipient = target.value) {
  if (!recipient?.available || !recipient.reservationId || busy.value || (invite && !recipient.chatEnabled) || (!invite && !recipient.remindersEnabled)) return
  choose(recipient)
  busy.value = true
  error.value = ''
  notice.value = ''
  try {
    if (invite) {
      const chat = await api<Chat>(path + '/invitations', 'POST', { seatId: recipient.id, reservationId: recipient.reservationId })
      await router.push({ path: '/reader/messages', query: { chat: chat.id } })
    } else {
      const result = await api<{ message: string }>(path + '/reminders', 'POST', { seatId: recipient.id, kind: kind.value, reservationId: recipient.reservationId })
      notice.value = result.message
    }
  } catch (issue) { error.value = message(issue) }
  finally { busy.value = false }
}
void load()
</script>
<template>
  <SiteHeader show-home />
  <main id="main" class="content-width page-space">
    <div class="panel-heading library-heading"><h1>馆内交流</h1><button class="text-button" :disabled="busy || loading" @click="load">刷新预约座位</button></div>
    <p class="muted">预约成功即可联系同馆预约读者，不用等预约开始。对方接受邀请后即可私聊。</p>
    <p v-if="error" class="notice error" role="alert">{{ error }} <RouterLink to="/reader">查看预约状态</RouterLink></p>
    <p v-if="notice" class="notice success" role="status">{{ notice }}</p>
    <p v-if="loading" role="status">正在查询当前有效预约座位…</p>
    <div v-else-if="seats.length" class="library-layout">
      <section class="panel contact-browser">
        <div class="contact-tabs" role="tablist" aria-label="选择聊天对象的方式" @keydown="onTabKey">
          <button id="contact-readers-tab" class="button button-small" type="button" role="tab" :aria-selected="view === 'readers'" aria-controls="contact-readers"
            :tabindex="view === 'readers' ? 0 : -1" :disabled="busy" @click="view = 'readers'">读者列表 <span>{{ contacts.length }}</span></button>
          <button id="contact-map-tab" class="button button-small" type="button" role="tab" :aria-selected="view === 'map'" aria-controls="contact-map"
            :tabindex="view === 'map' ? 0 : -1" :disabled="busy" @click="view = 'map'">座位图选人</button>
        </div>
        <div v-if="view === 'readers'" id="contact-readers" role="tabpanel" aria-labelledby="contact-readers-tab" tabindex="0">
          <h2>可联系读者</h2>
          <p class="muted">显示同馆已预约、允许聊天的其他读者，包含尚未开始的预约。可在「消息与聊天」中设置昵称与邀请偏好。</p>
          <label v-if="contacts.length">查找读者<input v-model="search" type="search" placeholder="搜索昵称、座位或区域" /></label>
          <p v-if="!contacts.length" class="notice">当前没有可联系的读者，稍后可以刷新看看。</p>
          <p v-else-if="!filtered.length" class="notice">没有找到匹配的读者，请试试其他昵称或座位。</p>
          <ul v-else class="contact-list">
            <li v-for="seat in filtered" :key="seat.reservationId || seat.id" class="contact-card" :class="{ selected: selected === seat.reservationId }">
              <button type="button" class="contact-person" :aria-pressed="selected === seat.reservationId" :disabled="busy"
                :aria-label="'选择 ' + (seat.nickname || '同馆读者') + '，' + seat.label" @click="choose(seat)">
                <span class="contact-avatar" aria-hidden="true">{{ Array.from(seat.nickname || '同馆读者')[0] }}</span>
                <span class="contact-identity"><strong>{{ seat.nickname || '同馆读者' }}</strong>
                  <small>{{ seat.floor ? seat.floor + 'F · ' : '' }}{{ seat.area }} · {{ seat.label }}</small>
                  <small>{{ periodLabel(seat) }}</small></span>
              </button>
              <button type="button" class="button button-outline" :disabled="busy"
                :aria-label="'邀请 ' + (seat.nickname || '同馆读者') + '（' + seat.label + '）聊天'" @click="send(true, seat)">邀请聊天</button>
            </li>
          </ul>
        </div>
        <div v-else id="contact-map" role="tabpanel" aria-labelledby="contact-map-tab" tabindex="0">
          <h2>座位图选人</h2>
          <label v-if="periods.length">查看预约时段<select v-model="period" :disabled="busy" @change="selected = ''">
            <option v-for="[key, label] in periods" :key="key" :value="key">{{ label }}</option>
          </select></label>
          <SeatMap purpose="contact" :seats="mapSeats" :selected="selectedSeat" :disabled="busy" @select="chooseMapSeat" />
        </div>
      </section>
      <aside class="panel form-stack">
        <h2>{{ target ? target.nickname || '同馆读者' : '先选择聊天对象' }}</h2>
        <template v-if="target">
          <p class="notice success" role="status">已选择 {{ target.floor ? target.floor + 'F · ' : '' }}{{ target.area }} · {{ target.label }}</p>
          <p class="muted">{{ periodLabel(target) }}</p>
          <p class="muted">对方可以接受、拒绝或忽略邀请。微信号由双方在聊天中自愿分享。</p>
          <button class="button button-dark" :disabled="busy || !target.chatEnabled" @click="send(true)">{{ target.chatEnabled ? '发送聊天邀请' : '暂不接收聊天邀请' }}</button>
          <details class="compact-details"><summary>发送礼貌提醒</summary>
          <label>礼貌提醒<select v-model="kind" :disabled="busy || !target.remindersEnabled"><option value="VOICE">请降低交谈声音</option><option value="HEADPHONES">请佩戴耳机</option><option value="ITEMS">请轻放物品</option></select></label>
          <p class="notice">{{ reminders[kind] }}</p>
          <p v-if="!target.remindersEnabled" class="muted">双方进入预约时段后可发送安静提醒；聊天邀请现在就能发送。</p>
          <button class="button button-outline" :disabled="busy || !target.remindersEnabled" @click="send(false)">确认发送匿名提醒</button>
          </details>
        </template>
        <p v-else class="muted">点击列表中的读者，或切换座位图选择座位。首次聊天需要对方接受邀请。</p>
      </aside>
    </div>
    <section v-else-if="!loading && !error" class="panel"><h2>当前没有可联系的读者</h2><p class="muted">稍后可以刷新查看同馆预约情况。</p></section>
  </main>
</template>
