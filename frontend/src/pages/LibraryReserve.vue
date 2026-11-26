<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { quietLevels, spaceTypes } from '../campus'
import { useRoute, useRouter } from 'vue-router'
import { saveDraft, takeDraft } from '../bookingDraft'
import { ApiError, api, dayOf, message, newRequestKey, timeRange, today, type Slot, type Viewer } from '../api'
import { reservationStatus, type Seat, type SeatReservation, type Venue } from '../library'
import SiteHeader from '../components/SiteHeader.vue'
import SeatMap from '../components/SeatMap.vue'
import GdouReference from '../components/GdouReference.vue'

const props = defineProps<{ slug: string }>()
const route = useRoute()
const router = useRouter()
const path = '/public/libraries/' + encodeURIComponent(props.slug)
const venue = ref<Venue>()
const viewer = ref<Viewer>()
const date = ref(today())
const slots = ref<Slot[]>([])
const start = ref('')
const seats = ref<Seat[]>([])
const selected = ref(0)
const area = ref('')
const power = ref(false), windowSeat = ref(false)
const displayedSeats = computed(() => seats.value.filter(item => (!area.value || item.area === area.value)
  && (!power.value || item.powerOutlet) && (!windowSeat.value || item.nearWindow)))
const receipt = ref<SeatReservation>()
const loading = ref(true)
const loadingSlots = ref(false)
const loadingSeats = ref(false)
const submitting = ref(false)
const error = ref('')
const slotsError = ref('')
const seatsError = ref('')
const actionError = ref('')
let slotVersion = 0
let seatVersion = 0
let retry: { data: string; key: string } | undefined
watch(area, () => { selected.value = 0 })
watch([power, windowSeat], () => { selected.value = 0 })

const chosenSeat = computed(() => seats.value.find(row => row.id === selected.value))
const chosenSlot = computed(() => slots.value.find(slot => slot.startsAt === start.value))
function login() {
  saveDraft({ path: route.path, date: date.value, startsAt: start.value, seatId: selected.value })
  void router.push({ path: '/reader/login', query: { next: route.path } })
}
async function load() {
  loading.value = true
  error.value = ''
  try {
    venue.value = await api<Venue>(path)
    const draft = takeDraft(route.path)
    if (draft && draft.date >= today() && draft.date <= today(6)) date.value = draft.date
    await loadSlots()
    if (draft) {
      if (slots.value.some(row => row.startsAt === draft.startsAt)) {
        start.value = draft.startsAt
        await loadSeats()
        if (seats.value.some(row => row.id === draft.seatId && row.available)) selected.value = draft.seatId!
      }
      if ((draft.startsAt && !chosenSlot.value) || (draft.seatId && !selected.value)) {
        actionError.value = '原选择已不可用，请重新选择时段或座位。'
      }
    }
  }
  catch (issue) { error.value = message(issue) }
  finally { loading.value = false }
}
async function loadSlots() {
  const version = ++slotVersion
  ++seatVersion
  start.value = ''
  slots.value = []
  seats.value = []
  selected.value = 0
  loadingSeats.value = false
  loadingSlots.value = true
  slotsError.value = ''
  seatsError.value = ''
  try {
    const result = await api<Slot[]>(path + '/slots?date=' + date.value)
    if (version === slotVersion) slots.value = result
  } catch (issue) { if (version === slotVersion) slotsError.value = message(issue) }
  finally { if (version === slotVersion) loadingSlots.value = false }
}
async function loadSeats() {
  const version = ++seatVersion
  seats.value = []
  selected.value = 0
  seatsError.value = ''
  area.value = ''
  if (!start.value) return
  loadingSeats.value = true
  try {
    const result = await api<Seat[]>(path + '/seats?startsAt=' + encodeURIComponent(start.value))
    if (version === seatVersion) seats.value = result
  } catch (issue) { if (version === seatVersion) seatsError.value = message(issue) }
  finally { if (version === seatVersion) loadingSeats.value = false }
}
async function submit() {
  if (!selected.value || submitting.value) return
  submitting.value = true
  actionError.value = ''
  const input = { seatId: selected.value, startsAt: start.value }
  const data = JSON.stringify(input)
  try {
    if (retry?.data !== data) retry = { data, key: newRequestKey() }
    receipt.value = await api<SeatReservation>('/reader/libraries/' + props.slug + '/reservations', 'POST', {
      ...input, requestKey: retry.key,
    })
  } catch (issue) {
    if (issue instanceof ApiError && issue.status === 401) { login(); return }
    if (issue instanceof ApiError && issue.code === 'SLOT_UNAVAILABLE') await loadSeats()
    actionError.value = message(issue)
  } finally { submitting.value = false }
}
void api<Viewer>('/auth/me').then(me => { viewer.value = me }).catch(() => {})
void load()
</script>

<template>
  <SiteHeader :reader-login="login" />
  <main id="main" class="content-width page-space">
    <div class="page-breadcrumb"><RouterLink to="/libraries">所有预约场所</RouterLink></div>
    <p v-if="loading" role="status">正在加载预约场所…</p>
    <p v-else-if="error" class="notice error" role="alert">{{ error }} <button class="text-button" @click="load">重试</button></p>
    <template v-else-if="venue">
      <img v-if="venue.cover" class="venue-cover detail-cover" :src="venue.cover" alt="" />
      <div class="library-heading"><p class="section-label">座位预约</p><h1>{{ venue.name }}</h1><p class="preserve-lines muted">{{ venue.description }}</p></div>
      <div class="discovery-facts"><span>{{ spaceTypes[venue.spaceType] }}</span><span>{{ quietLevels[venue.quietLevel] }}</span><span>{{ venue.address || '地点由管理员补充' }}</span><span>{{ venue.floors ? venue.floors + ' 楼' : '楼层待补充' }}</span></div>
      <GdouReference v-if="slug === 'gdou-huguang-demo'" />
      <section v-if="receipt" class="panel narrow">
        <h2>{{ reservationStatus[receipt.status] }}</h2><p>{{ receipt.area }} · {{ receipt.seatLabel }}</p>
        <p>{{ dayOf(receipt.startsAt) }} · {{ timeRange(receipt) }}（北京时间）</p>
        <p v-if="receipt.status === 'RESERVED'">预约成功，按预约时段使用座位即可，无需签到。</p>
        <div class="actions"><RouterLink class="button button-dark" to="/reader">查看我的安排</RouterLink>
          <RouterLink v-if="receipt.status === 'RESERVED' || receipt.status === 'ACTIVE'" class="button" :to="'/library/' + slug + '/social'">联系同馆读者</RouterLink></div>
      </section>
      <div v-else class="library-layout">
        <section class="panel">
          <h2>选择时间和座位</h2>
          <fieldset class="form-stack" :disabled="submitting">
            <label>预约日期<input v-model="date" type="date" required :min="today()" :max="today(6)" @change="loadSlots" /></label>
            <p v-if="loadingSlots" role="status">正在加载时段…</p>
            <p v-else-if="slotsError" class="notice error" role="alert">{{ slotsError }} <button class="text-button" @click="loadSlots">重试</button></p>
            <p v-else-if="!slots.length" class="notice">当天可约时段已结束，请换一天。</p>
            <label v-else>预约时段（北京时间）
              <select v-model="start" @change="loadSeats"><option value="" disabled>请选择时段</option>
                <option v-for="slot in slots" :key="slot.startsAt" :value="slot.startsAt">{{ timeRange(slot) }}</option></select>
            </label>
            <p v-if="loadingSeats" role="status">正在查询座位…</p>
            <p v-else-if="seatsError" class="notice error" role="alert">{{ seatsError }} <button class="text-button" @click="loadSeats">重试</button></p>
            <template v-else-if="start && !loadingSeats">
              <p v-if="!seats.length" class="notice">暂时没有开放的座位。</p>
              <template v-else>
                <label>楼层 / 区域<select v-model="area"><option value="">全部区域</option>
                  <option v-for="zone in [...new Set(seats.map(seat => seat.area))]" :key="zone">{{ zone }}</option></select></label>
                <div class="discovery-preferences"><label class="check"><input v-model="power" type="checkbox" /> 只看有插座</label><label class="check"><input v-model="windowSeat" type="checkbox" /> 只看靠窗</label></div>
                <p v-if="!displayedSeats.length" class="notice">这个时段没有符合偏好的座位，试试取消筛选。</p>
                <SeatMap v-else :seats="displayedSeats" :selected="selected"
                  :disabled="submitting" @select="selected = $event" />
              </template>
            </template>
          </fieldset>
        </section>
        <aside class="panel">
          <h2>本次预约</h2>
          <p class="muted">北京时间 · 按预约时段使用，无需签到。开始前可取消，使用中可提前结束。</p>
          <details class="compact-details"><summary>预约规则与场所须知</summary>
            <p v-if="venue.rules" class="preserve-lines">{{ venue.rules }}</p>
            <ul class="library-rules"><li>每位读者同时保留一笔有效预约，可提前 7 天选座。</li>
              <li>时段结束后自动释放座位，请及时带走个人物品。</li></ul></details>
          <section v-if="chosenSeat && chosenSlot" class="booking-summary" aria-live="polite"><h3>确认预约信息</h3>
            <strong>{{ venue.name }} · {{ chosenSeat.label }}</strong><p>{{ chosenSeat.area }} · {{ date }} · {{ timeRange(chosenSlot) }}</p>
          </section>
          <p v-if="actionError" class="notice error" role="alert">{{ actionError }}</p>
          <template v-if="viewer?.role === 'READER'">
            <p>读者 {{ viewer.username }}</p><button class="button button-dark" :disabled="!selected || submitting" @click="submit">{{ submitting ? '正在预约…' : '确认选座' }}</button>
          </template>
          <template v-else>
            <p class="muted">登录校园账号后可以预约座位。</p>
            <button type="button" class="button button-dark" @click="login">{{ selected ? '登录后确认选座' : '校园账号登录' }}</button>
          </template>
        </aside>
      </div>
    </template>
  </main>
</template>
