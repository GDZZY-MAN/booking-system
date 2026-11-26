<script setup lang="ts">
import { computed, ref } from 'vue'
import { api, dayOf, message, timeRange, today, type Order, type Viewer } from '../../api'

const props = defineProps<{ role: Viewer['role'] }>()
const rows = ref<Order[]>()
const from = ref(today())
const through = ref(today(6))
const selectedDay = ref('')
const days = computed(() => {
  if (!from.value || !through.value) return []
  const count = Math.min(31, 1 + Math.round((Date.parse(through.value) - Date.parse(from.value)) / 86400000))
  return Array.from({ length: count }, (_, index) => new Date(Date.parse(from.value) + index * 86400000).toISOString().slice(0, 10))
})
const visible = computed(() => (rows.value ?? []).filter(row => !selectedDay.value || dayOf(row.startsAt) === selectedDay.value))
const loading = ref(false)
const error = ref('')
const actionError = ref('')
const notice = ref('')
const confirmId = ref('')
const cancelling = ref(false)
const url = props.role === 'OWNER' ? '/owner/bookings' : '/employee/bookings'

async function load() {
  loading.value = true
  error.value = ''
  try {
    const params = new URLSearchParams({ from: from.value, through: through.value })
    rows.value = await api<Order[]>(url + '?' + params)
    selectedDay.value = ''
  } catch (issue) { error.value = message(issue) }
  finally { loading.value = false }
}

async function cancel(id: string) {
  cancelling.value = true
  actionError.value = ''
  notice.value = ''
  try {
    const updated = await api<Order>(url + '/' + id + '/cancel', 'POST')
    if (rows.value) rows.value = rows.value.map(row => row.id === id ? updated : row)
    confirmId.value = ''
    notice.value = '预约已取消。'
  } catch (issue) { actionError.value = message(issue) }
  finally { cancelling.value = false }
}

void load()
</script>

<template>
  <section class="panel">
    <h2>{{ role === 'OWNER' ? '预约日历' : '我的预约' }}</h2>
    <form class="order-filters" @submit.prevent="load">
      <label>开始日期<input v-model="from" type="date" required /></label>
      <label>结束日期<input v-model="through" type="date" :min="from" required /></label>
      <button class="button button-outline" :disabled="loading || cancelling">查询</button>
    </form>
    <p v-if="loading" role="status">正在查询预约…</p>
    <p v-if="error" class="notice error" role="alert">{{ error }} <button class="text-button" @click="load">重试</button></p>
    <p v-if="notice" class="notice success" role="status">{{ notice }}</p>
    <p v-if="!loading && !error && rows && !rows.length" class="muted">这个日期范围内还没有预约。</p>
    <nav v-if="rows && !loading" class="calendar-strip" aria-label="筛选预约日期">
      <button class="button button-small" :aria-pressed="!selectedDay" @click="selectedDay = ''">全部</button>
      <button v-for="day in days" :key="day" class="button button-small" :aria-pressed="day === selectedDay" @click="selectedDay = day">{{ day.slice(5) }}<small>{{ rows.filter(r => dayOf(r.startsAt) === day && r.status === 'CONFIRMED').length }} 笔</small></button>
    </nav>
    <p v-if="selectedDay && !visible.length" class="muted">{{ selectedDay }} 没有预约。</p>
    <ul v-if="visible.length" class="data-list order-list">
      <li v-for="row in visible" :key="row.id">
        <div class="order-summary"><strong>{{ row.serviceName }}</strong>
          <small>{{ dayOf(row.startsAt) }} · {{ timeRange(row) }} · {{ row.employeeName }}</small>
          <small>顾客 {{ row.customerName }} · {{ row.contact }}</small>
          <small class="order-id">{{ row.id }}</small>
        </div>
        <span class="status" :class="{ live: row.status === 'CONFIRMED' }">{{ row.status === 'CONFIRMED' ? '已确认' : '已取消' }}</span>
        <template v-if="role === 'OWNER' && row.status === 'CONFIRMED'">
          <div v-if="confirmId === row.id" class="cancel-actions">
            <p>确认取消这笔预约？</p>
            <button class="button button-danger" :disabled="cancelling" @click="cancel(row.id)">确认取消</button>
            <button class="button button-ghost" :disabled="cancelling" @click="confirmId = ''; actionError = ''">保留预约</button>
            <p v-if="actionError" class="notice error" role="alert">{{ actionError }}</p>
          </div>
          <button v-else class="button button-outline" :disabled="cancelling" @click="confirmId = row.id; actionError = ''">取消预约</button>
        </template>
      </li>
    </ul>
  </section>
</template>
