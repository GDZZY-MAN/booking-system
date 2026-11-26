<script setup lang="ts">
import { computed, ref } from 'vue'
import { ApiError, api, dayOf, message, newRequestKey, priceLabel, timeRange, today, type PublicShop, type Receipt, type Slot, type Viewer } from '../api'
import { useRoute, useRouter } from 'vue-router'
import { saveDraft, takeDraft } from '../bookingDraft'
const route = useRoute()
const router = useRouter()
import { serviceCategories, type ServiceReview } from '../campus'
import SiteHeader from '../components/SiteHeader.vue'
import { defaultBrand, type ShopBrand } from '../branding'
import BookingNote from '../components/BookingNote.vue'

const props = defineProps<{ slug: string }>()
const path = '/public/' + encodeURIComponent(props.slug)
const shop = ref<PublicShop>()
const brand = ref<ShopBrand>(defaultBrand)
const staff = ref<{ id: number; name: string }[]>([])
const slots = ref<Slot[]>([])
const serviceId = ref(0)
const employeeId = ref(0)
const date = ref(today())
const startsAt = ref('')
const name = ref('')
const contact = ref('')
const receipt = ref<Receipt>()
const viewer = ref<Viewer>()
const reviews = ref<ServiceReview[]>([])
const reviewsError = ref('')
const pageError = ref('')
const staffError = ref('')
const slotsError = ref('')
const submitError = ref('')
const loadingPage = ref(false)
const loadingStaff = ref(false)
const loadingSlots = ref(false)
const submitting = ref(false)
const service = computed(() => shop.value?.services.find(item => item.id === serviceId.value))
const selectedSlot = computed(() => slots.value.find(slot => slot.startsAt === startsAt.value))
const selectedPerson = computed(() => staff.value.find(person => person.id === employeeId.value))
let staffRequest = 0
let slotsRequest = 0
let lastRequest: { input: string; key: string } | undefined

async function loadShop() {
  loadingPage.value = true
  pageError.value = ''
  try {
    shop.value = await api<PublicShop>(path)
    void api<ShopBrand>(path + '/branding')
      .then(value => { brand.value = value })
      .catch(() => { brand.value = defaultBrand })
  }
  catch (issue) { shop.value = undefined; pageError.value = message(issue) }
  finally { loadingPage.value = false }
}

async function loadStaff() {
  const request = ++staffRequest
  ++slotsRequest
  employeeId.value = 0
  staff.value = []
  startsAt.value = ''
  slots.value = []
  staffError.value = ''
  slotsError.value = ''
  submitError.value = ''
  loadingSlots.value = false
  loadingStaff.value = true
  try {
    const result = await api<typeof staff.value>(path + '/employees?serviceId=' + serviceId.value)
    if (request === staffRequest) staff.value = result
  } catch (issue) {
    if (request === staffRequest) staffError.value = message(issue)
  } finally {
    if (request === staffRequest) loadingStaff.value = false
  }
}

async function loadSlots() {
  const request = ++slotsRequest
  startsAt.value = ''
  slots.value = []
  slotsError.value = ''
  if (!employeeId.value || !date.value) {
    loadingSlots.value = false
    return
  }
  loadingSlots.value = true
  try {
    const params = new URLSearchParams({
      serviceId: String(serviceId.value), employeeId: String(employeeId.value), date: date.value,
    })
    const result = await api<Slot[]>(path + '/slots?' + params)
    if (request === slotsRequest) slots.value = result
  } catch (issue) {
    if (request === slotsRequest) slotsError.value = message(issue)
  } finally {
    if (request === slotsRequest) loadingSlots.value = false
  }
}

function login() {
  saveDraft({ path: route.path, date: date.value, startsAt: startsAt.value, serviceId: serviceId.value, employeeId: employeeId.value })
  void router.push({ path: '/reader/login', query: { next: route.path } })
}
async function restoreSelection() {
  await loadShop()
  if (!shop.value) return
  const draft = takeDraft(route.path)
  if (!draft) return
  if (shop.value.services.some(item => item.id === draft.serviceId)) {
    serviceId.value = draft.serviceId!
    await loadStaff()
    if (draft.date >= today() && draft.date <= today(29) && staff.value.some(person => person.id === draft.employeeId)) {
      date.value = draft.date
      employeeId.value = draft.employeeId!
      await loadSlots()
      if (slots.value.some(slot => slot.startsAt === draft.startsAt)) startsAt.value = draft.startsAt
    }
  }
  if (draft.startsAt && !startsAt.value) submitError.value = '原时段已不可预约，请重新选择。'
}
async function submit() {
  if (submitting.value) return
  if (viewer.value?.role !== 'READER') { login(); return }
  if (!startsAt.value) return
  submitting.value = true
  submitError.value = ''
  const data = {
    serviceId: serviceId.value, employeeId: employeeId.value, startsAt: startsAt.value,
    customerName: name.value, contact: contact.value,
  }
  try {
    const input = JSON.stringify(data)
    if (lastRequest?.input !== input) lastRequest = { input, key: newRequestKey() }
    receipt.value = await api<Receipt>(path + '/bookings', 'POST', { ...data, requestKey: lastRequest.key })
  } catch (issue) {
    if (issue instanceof ApiError && issue.status === 401) { login(); return }
    if (issue instanceof ApiError && [404, 409].includes(issue.status)) {
      await loadSlots()
      if (slotsError.value && issue.status === 404) {
        await loadShop()
        if (shop.value?.services.some(item => item.id === serviceId.value)) await loadStaff()
        else serviceId.value = 0
      }
    }
    submitError.value = message(issue)
  } finally { submitting.value = false }
}

function bookAgain() {
  receipt.value = undefined
  lastRequest = undefined
  name.value = ''
  contact.value = ''
  void loadSlots()
}

void restoreSelection()
void api<Viewer>('/auth/me').then(me => { viewer.value = me }).catch(() => {})
void api<ServiceReview[]>(path + '/reviews').then(rows => { reviews.value = rows }).catch(issue => { reviewsError.value = message(issue) })
</script>

<template>
  <SiteHeader :reader-login="login" />
  <div class="content-width page-breadcrumb"><RouterLink to="/services">所有校园服务</RouterLink></div>
  <main id="main" class="reserve-layout content-width store-theme" :data-theme="brand.theme">
    <aside v-if="shop" class="panel service-introduction">
      <img v-if="brand.cover" :src="brand.cover" class="service-cover" alt="" />
      <div class="service-name"><img v-if="brand.logo" :src="brand.logo" class="directory-logo" alt="" />
        <div><p v-if="shop.discovery" class="section-label">{{ serviceCategories[shop.discovery.category] }}</p><h1>{{ shop.name }}</h1></div></div>
      <p v-if="shop.description" class="preserve-lines muted">{{ shop.description }}</p>
      <p class="service-address">{{ shop.discovery?.address || brand.address || '地点由服务商补充，请预约前确认。' }}</p>
      <small class="muted">所有时间均为北京时间</small>
      <details v-if="brand.businessNote" class="compact-details"><summary>营业说明</summary><p class="preserve-lines">{{ brand.businessNote }}</p></details>
    </aside>
    <section v-if="loadingPage || pageError" class="panel reserve-panel">
      <p v-if="loadingPage" role="status">正在加载店铺…</p>
      <template v-else><h1>预约页面暂不可用</h1><p class="muted" role="alert">{{ pageError }}</p>
        <button class="button button-outline" @click="loadShop">重新加载</button></template>
    </section>
    <section v-else-if="receipt" class="panel reserve-panel receipt" aria-live="polite">
      <span class="receipt-icon" aria-hidden="true">{{ receipt.status === 'CONFIRMED' ? '✓' : '–' }}</span>
      <h2>{{ receipt.status === 'CONFIRMED' ? '预约已确认' : '预约已取消' }}</h2>
      <dl class="receipt-details">
        <div><dt>预约服务</dt><dd>{{ receipt.serviceName }}</dd></div>
        <div><dt>服务员工</dt><dd>{{ receipt.employeeName }}</dd></div>
        <div><dt>预约时间</dt><dd>{{ dayOf(receipt.startsAt) }} · {{ timeRange(receipt) }}</dd></div>
        <div><dt>预约编号</dt><dd class="order-id">{{ receipt.id }}</dd></div>
      </dl>
      <p class="notice success">这次服务已加入“我的安排”。</p>
      <RouterLink class="button button-dark" to="/reader">查看我的安排</RouterLink>
      <button class="button button-outline" @click="bookAgain">返回店铺</button>
    </section>
    <section v-else-if="shop" class="panel reserve-panel">
      <h2>选择预约时间</h2>
      <p v-if="viewer?.role === 'READER'" class="notice success">本次预约将保存到 {{ viewer.username }} 的“我的安排”。</p>
      <p v-else class="muted">可以先登录校园账号，再选择预约时段。</p>
      <BookingNote :note="brand.bookingNote" />
      <p v-if="!shop.services.length" class="notice">店铺暂时没有上架服务，可以稍后回来看看。</p>
      <form v-else class="form-stack" @submit.prevent="submit">
        <fieldset class="form-stack reserve-fields" :disabled="submitting">
          <label>服务
            <select v-model="serviceId" aria-label="服务" required @change="loadStaff">
              <option :value="0" disabled>请选择服务</option>
              <option v-for="item in shop.services" :key="item.id" :value="item.id">{{ item.name }} · {{ item.durationMinutes }} 分钟 · {{ priceLabel(item) }}</option>
            </select>
          </label>
          <template v-if="service">
            <p v-if="loadingStaff" class="muted" role="status">正在查找可预约员工…</p>
            <div v-else-if="staffError" class="notice error" role="alert">{{ staffError }} <button type="button" class="text-button" @click="loadStaff">重试</button></div>
            <p v-else-if="!staff.length" class="notice">这项服务暂时没有可预约员工。</p>
            <label v-else>员工
              <select v-model="employeeId" aria-label="员工" required @change="loadSlots">
                <option :value="0" disabled>请选择员工</option>
                <option v-for="person in staff" :key="person.id" :value="person.id">{{ person.name }}</option>
              </select>
            </label>
          </template>
          <template v-if="employeeId">
            <label>日期<input v-model="date" type="date" required :min="today()" :max="today(29)" @change="loadSlots" /></label>
            <p v-if="loadingSlots" class="muted" role="status">正在查询空档…</p>
            <div v-else-if="slotsError" class="notice error" role="alert">{{ slotsError }} <button type="button" class="text-button" @click="loadSlots">重试</button></div>
            <p v-else-if="!slots.length" class="notice">当天没有可预约时段，试试其他日期或员工。</p>
            <fieldset v-else class="slot-field">
              <legend>可约时段</legend>
              <div class="slots">
                <label v-for="slot in slots" :key="slot.startsAt" class="slot" :class="{ selected: startsAt === slot.startsAt }">
                  <input v-model="startsAt" type="radio" name="slot" :value="slot.startsAt" /> {{ timeRange(slot) }}
                </label>
              </div>
            </fieldset>
          </template>
          <div v-if="startsAt && viewer?.role === 'READER'" class="form-stack guest-fields">
            <label>称呼<input v-model.trim="name" required maxlength="60" autocomplete="name" placeholder="怎么称呼你" /></label>
            <label>联系方式<input v-model.trim="contact" required maxlength="80" placeholder="手机号或微信号" /></label>
            <small class="muted">称呼与联系方式会提供给店主和负责本次服务的员工。</small>
          </div>
        </fieldset>
        <p v-if="submitError" class="notice error" role="alert">{{ submitError }}</p>
        <p v-if="service && selectedSlot && selectedPerson" class="booking-summary">{{ service.name }} · {{ priceLabel(service) }} · {{ selectedPerson.name }}<br />{{ date }} · {{ timeRange(selectedSlot) }}</p>
        <button v-if="viewer?.role === 'READER'" type="submit" class="button button-dark" :disabled="!startsAt || submitting">{{ submitting ? '正在确认…' : '确认预约' }}</button>
      </form>
      <button v-if="viewer?.role !== 'READER'" type="button" class="button button-dark" @click="login">{{ startsAt ? '登录后继续预约' : '校园账号登录' }}</button>
    </section>
  </main>
  <section v-if="shop" class="content-width service-reviews">
    <p v-if="reviewsError" class="notice error" role="alert">评价暂时无法加载：{{ reviewsError }}</p>
    <p v-else-if="!reviews.length" class="muted">暂无评价 · 完成服务后可在“我的安排”评价。</p>
    <details v-else class="compact-details"><summary>使用者评价（{{ reviews.length }}）</summary>
    <p class="muted">来自已完成服务的预约用户。</p>
    <article v-for="(review, index) in reviews" :key="index" class="social-notice"><strong>{{ review.rating }} 星 · {{ review.serviceName }}</strong><p class="preserve-lines">{{ review.body }}</p><small class="muted">已预约用户 · {{ dayOf(review.createdAt) }}</small></article>
    </details>
  </section>
  <footer class="store-credit">由 <RouterLink to="/">有约</RouterLink> 提供预约服务</footer>
</template>
