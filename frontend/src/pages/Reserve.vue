<script setup lang="ts">
import { computed, ref } from 'vue'
import { ApiError, api, dayOf, message, timeRange, today, type PublicShop, type Receipt, type Slot } from '../api'
import SiteHeader from '../components/SiteHeader.vue'

const props = defineProps<{ slug: string }>()
const path = '/public/' + encodeURIComponent(props.slug)
const shop = ref<PublicShop>()
const staff = ref<{ id: number; name: string }[]>([])
const slots = ref<Slot[]>([])
const serviceId = ref(0)
const employeeId = ref(0)
const date = ref(today())
const startsAt = ref('')
const name = ref('')
const contact = ref('')
const receipt = ref<Receipt>()
const pageError = ref('')
const staffError = ref('')
const slotsError = ref('')
const submitError = ref('')
const loadingPage = ref(false)
const loadingStaff = ref(false)
const loadingSlots = ref(false)
const submitting = ref(false)
const service = computed(() => shop.value?.services.find(item => item.id === serviceId.value))
let staffRequest = 0
let slotsRequest = 0
let lastRequest: { input: string; key: string } | undefined

async function loadShop() {
  loadingPage.value = true
  pageError.value = ''
  try { shop.value = await api<PublicShop>(path) }
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

async function submit() {
  if (!startsAt.value || submitting.value) return
  submitting.value = true
  submitError.value = ''
  const data = {
    serviceId: serviceId.value, employeeId: employeeId.value, startsAt: startsAt.value,
    customerName: name.value, contact: contact.value,
  }
  try {
    const input = JSON.stringify(data)
    if (lastRequest?.input !== input) lastRequest = { input, key: crypto.randomUUID() }
    receipt.value = await api<Receipt>(path + '/bookings', 'POST', { ...data, requestKey: lastRequest.key })
  } catch (issue) {
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

void loadShop()
</script>

<template>
  <SiteHeader booking />
  <main id="main" class="reserve-layout content-width">
    <aside v-if="shop" class="reserve-aside">
      <span class="reserve-symbol" aria-hidden="true">✳</span>
      <p class="section-label">在线预约</p>
      <h1>{{ shop.name }}</h1>
      <p v-if="shop.description" class="preserve-lines">{{ shop.description }}</p>
      <span class="timezone">◷ 所有时间均为北京时间</span>
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
      <button class="button button-outline" @click="bookAgain">再约一次</button>
    </section>
    <section v-else-if="shop" class="panel reserve-panel">
      <h2>选择预约时间</h2>
      <p v-if="!shop.services.length" class="notice">店铺暂时没有上架服务，可以稍后回来看看。</p>
      <form v-else class="form-stack" @submit.prevent="submit">
        <fieldset class="form-stack reserve-fields" :disabled="submitting">
          <label>服务
            <select v-model="serviceId" aria-label="服务" required @change="loadStaff">
              <option :value="0" disabled>请选择服务</option>
              <option v-for="item in shop.services" :key="item.id" :value="item.id">{{ item.name }} · {{ item.durationMinutes }} 分钟</option>
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
          <div v-if="startsAt || name || contact" class="form-stack guest-fields">
            <label>称呼<input v-model.trim="name" required maxlength="60" autocomplete="name" placeholder="怎么称呼你" /></label>
            <label>联系方式<input v-model.trim="contact" required maxlength="80" placeholder="手机号或微信号" /></label>
            <small class="muted">称呼与联系方式会提供给店主和负责本次服务的员工。</small>
          </div>
        </fieldset>
        <p v-if="submitError" class="notice error" role="alert">{{ submitError }}</p>
        <button class="button button-dark" :disabled="!startsAt || submitting">{{ submitting ? '正在确认…' : '确认预约' }}</button>
      </form>
    </section>
  </main>
</template>
