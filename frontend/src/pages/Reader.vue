<script setup lang="ts">
import { computed, nextTick, onUnmounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ApiError, api, dayOf, message, timeRange, today, workspaceFor, type Viewer } from '../api'
import { reservationStatus, type SeatReservation } from '../library'
import type { ServiceBooking } from '../campus'
import SiteHeader from '../components/SiteHeader.vue'
const router = useRouter()
const seats = ref<SeatReservation[]>([]), services = ref<ServiceBooking[]>([])
const profile = ref<Viewer>()
const loading = ref(true), busy = ref(false)
const error = ref(''), notice = ref('')
const period = ref('today')
const detailId = ref('')
watch(period, () => { detailId.value = '' })
const now = ref(Date.now())
const timer = setInterval(() => { now.value = Date.now() }, 30000)
onUnmounted(() => clearInterval(timer))
const reviewId = ref(''), rating = ref(5), reviewBody = ref('')
const agenda = computed(() => [
  ...seats.value.map(row => ({ id: row.id, kind: 'seat' as const, row, startsAt: row.startsAt, endsAt: row.endsAt,
    title: row.venueName, detail: row.area + ' · ' + row.seatLabel, status: reservationStatus[row.status],
    live: ['RESERVED', 'ACTIVE'].includes(row.status) && new Date(row.endsAt).getTime() > now.value })),
  ...services.value.map(row => ({ id: row.id, kind: 'service' as const, row, startsAt: row.startsAt, endsAt: row.endsAt,
    title: row.serviceName, detail: row.venueName + ' · ' + row.employeeName,
    status: row.status === 'CANCELLED' ? '已取消' : new Date(row.endsAt).getTime() <= now.value ? '已结束' : new Date(row.startsAt).getTime() <= now.value ? '服务时段内' : '已预约',
    live: row.status === 'CONFIRMED' && new Date(row.endsAt).getTime() > now.value })),
].sort((a, b) => Date.parse(a.startsAt) - Date.parse(b.startsAt)))
const monday = computed(() => {
  const day = new Date(dayOf(new Date(now.value).toISOString()) + 'T00:00:00Z')
  day.setUTCDate(day.getUTCDate() - (day.getUTCDay() + 6) % 7)
  return day.toISOString().slice(0, 10)
})
const weekEnd = computed(() => new Date(Date.parse(monday.value + 'T00:00:00Z') + 7 * 86400000).toISOString().slice(0, 10))
const visible = computed(() => agenda.value.filter(item => period.value === 'all' || (period.value === 'today'
  ? dayOf(item.startsAt) === today()
  : dayOf(item.startsAt) >= monday.value && dayOf(item.startsAt) < weekEnd.value)))
const groups = computed(() => [...new Set(visible.value.map(item => dayOf(item.startsAt)))])
const next = computed(() => agenda.value.find(item => item.live))
async function showNext() {
  const item = next.value
  if (!item) return
  if (!visible.value.includes(item)) period.value = 'all'
  await nextTick()
  document.getElementById('arrangement-' + item.kind + item.id)?.focus()
}
async function load() {
  loading.value = true; error.value = ''; now.value = Date.now()
  try {
    const me = await api<Viewer>('/auth/me')
    if (me.role !== 'READER') { await router.replace(workspaceFor(me.role)); return }
    profile.value = me
    const [space, service] = await Promise.allSettled([
      api<SeatReservation[]>('/reader/reservations'), api<ServiceBooking[]>('/reader/service-bookings'),
    ])
    if (space.status === 'fulfilled') seats.value = space.value
    else { seats.value = []; error.value = '座位安排未能加载：' + message(space.reason) }
    if (service.status === 'fulfilled') services.value = service.value
    else { services.value = []; error.value += ' 服务安排未能加载：' + message(service.reason) }
    if (!services.value.some(row => row.id === detailId.value)) detailId.value = ''
  } catch (issue) {
    if (issue instanceof ApiError && issue.status === 401) await router.replace('/reader/login')
    else error.value = message(issue)
  } finally { loading.value = false }
}
async function perform(path: string, body?: unknown) {
  if (busy.value) return
  busy.value = true; error.value = ''; notice.value = ''
  try {
    await api(path, 'POST', body); await load(); reviewId.value = ''
    notice.value = path.endsWith('/review') ? '评价已发布，感谢分享使用体验。' : path.endsWith('/finish') ? '座位已释放。' : '预约已取消。'
  } catch (issue) { error.value = message(issue) }
  finally { busy.value = false }
}
void load()
</script>
<template>
  <SiteHeader />
  <main id="main" class="content-width page-space agenda-page">
    <div class="panel-heading"><h1>我的安排</h1>
      <button class="text-button" :disabled="busy || loading" @click="load">刷新安排</button></div>
    <p v-if="profile" class="muted">所有时间均为北京时间。</p>
    <p v-if="loading" role="status">正在整理你的安排…</p>
    <p v-if="error" class="notice error" role="alert">{{ error }}</p>
    <p v-if="notice" class="notice success" role="status">{{ notice }}</p>
    <section v-if="next" class="agenda-next" aria-label="下一项安排">
      <div><span>{{ Date.parse(next.startsAt) <= now ? '正在进行' : '接下来' }} · {{ next.kind === 'seat' ? '空间预约' : '服务预约' }}</span>
        <h2>{{ next.title }}</h2><p>{{ next.detail }}</p><strong>{{ dayOf(next.startsAt) }} · {{ timeRange(next) }}</strong></div>
      <div><p>{{ next.kind === 'seat' ? '按预约时间使用，到馆前即可联系同馆读者。' : '请按约定时间到达服务地点。' }}</p>
        <button class="button button-small" @click="showNext">查看这项安排</button></div>
    </section>
    <div class="agenda-periods" role="group" aria-label="日程范围"><button v-for="tab in [{ id: 'today', label: '今天' }, { id: 'week', label: '本周' }, { id: 'all', label: '全部记录' }]" :key="tab.id" class="button button-small" :aria-pressed="period === tab.id" @click="period = tab.id">{{ tab.label }}</button></div>
    <p v-if="period === 'week'" class="muted">本周从周一开始，按预约开始时间排列。</p>
    <section v-if="!visible.length && !loading" class="panel agenda-empty"><h2>{{ period === 'all' ? '从一个小安排开始' : '这段时间还没有安排' }}</h2><p class="muted">找一处学习空间，或把日常服务提前约好。</p><div class="actions"><RouterLink class="button button-dark" to="/libraries">找座位</RouterLink><RouterLink class="button button-outline" to="/services">约服务</RouterLink></div></section>
    <section v-for="day in groups" :key="day" class="agenda-day" :aria-label="day + ' 的安排'"><h2>{{ day }} <small v-if="day === today()">今天</small></h2>
      <article v-for="item in visible.filter(row => dayOf(row.startsAt) === day)" :key="item.kind + item.id" :id="'arrangement-' + item.kind + item.id" tabindex="-1" class="panel agenda-item" :class="{ 'agenda-live': item.live }">
        <div class="agenda-time"><strong>{{ timeRange(item) }}</strong><span>{{ item.kind === 'seat' ? '空间预约' : '服务预约' }}</span></div>
        <div class="agenda-description"><h3>{{ item.title }}</h3><p>{{ item.detail }}</p><span class="status" :class="{ live: item.live }">{{ item.status }}</span>
          <p v-if="item.kind === 'service' && item.row.address" class="muted">{{ item.row.address }}</p>
          <p v-if="item.kind === 'service' && item.row.rating" class="muted">已评价 · {{ item.row.rating }} 星</p>
        </div>
        <div class="actions agenda-actions">
          <template v-if="item.kind === 'seat'">
            <button v-if="item.row.status === 'RESERVED' && Date.parse(item.startsAt) > now" class="button button-outline" :disabled="busy" @click="perform('/reader/reservations/' + item.id + '/cancel')">取消预约</button>
            <button v-if="item.row.status === 'ACTIVE'" class="button button-outline" :disabled="busy" @click="perform('/reader/reservations/' + item.id + '/finish')">结束使用</button>
            <RouterLink v-if="item.live" class="button button-dark" :to="'/library/' + item.row.slug + '/social'">馆内交流</RouterLink>
          </template>
          <template v-else>
            <button v-if="item.live && Date.parse(item.startsAt) > now" class="button button-outline" :disabled="busy" @click="perform('/reader/service-bookings/' + item.id + '/cancel')">取消预约</button>
            <button v-if="item.row.status === 'CONFIRMED' && Date.parse(item.endsAt) <= now && !item.row.rating" class="button button-outline" @click="reviewId = item.id; reviewBody = ''; rating = 5">评价服务</button>
            <button type="button" class="button button-ghost" :id="'booking-details-toggle-' + item.id"
              :aria-expanded="detailId === item.id" :aria-controls="'booking-details-' + item.id"
              @click="detailId = detailId === item.id ? '' : item.id">{{ detailId === item.id ? '收起详情' : '预约详情' }}</button>
          </template>
        </div>
        <section v-if="item.kind === 'service'" v-show="detailId === item.id" :id="'booking-details-' + item.id"
          class="agenda-booking-details" :aria-labelledby="'booking-details-toggle-' + item.id">
          <h4>本次预约</h4>
          <dl class="receipt-details">
            <div><dt>预约服务</dt><dd>{{ item.row.serviceName }}</dd></div>
            <div><dt>服务店铺</dt><dd>{{ item.row.venueName }}</dd></div>
            <div><dt>服务员工</dt><dd>{{ item.row.employeeName }}</dd></div>
            <div><dt>预约时间</dt><dd>{{ dayOf(item.startsAt) }} · {{ timeRange(item) }}（北京时间）</dd></div>
            <div><dt>服务地点</dt><dd>{{ item.row.address || '地点由商家补充' }}</dd></div>
            <div><dt>预约状态</dt><dd>{{ item.status }}</dd></div>
            <div><dt>预约编号</dt><dd class="order-id">{{ item.id }}</dd></div>
          </dl>
          <RouterLink class="button button-small" :to="'/s/' + item.row.slug">前往店铺</RouterLink>
        </section>
        <form v-if="reviewId === item.id && item.kind === 'service'" class="form-stack agenda-review" @submit.prevent="perform('/reader/service-bookings/' + item.id + '/review', { rating, body: reviewBody })">
          <label>评分<select v-model.number="rating"><option v-for="star in [5, 4, 3, 2, 1]" :key="star" :value="star">{{ star }} 星</option></select></label>
          <label>评价内容<textarea v-model.trim="reviewBody" required maxlength="500" rows="3" placeholder="分享你的真实使用体验。评价会公开展示，请勿填写手机号等个人信息。"></textarea></label>
          <div class="actions"><button class="button button-dark" :disabled="busy">发布评价</button><button type="button" class="button button-ghost" @click="reviewId = ''">暂不评价</button></div>
        </form>
      </article>
    </section>
  </main>
</template>

<style scoped>
.agenda-booking-details { grid-column: 1 / -1; border-top: 1px solid var(--line); padding-top: 18px; font-size: 14px; }
.agenda-booking-details h4 { margin: 0 0 12px; }
.agenda-booking-details dl { margin: 0 0 16px; }
</style>
