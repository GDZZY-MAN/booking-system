<script setup lang="ts">
import { ref } from 'vue'
import { api, dayOf, message, timeRange, today, type Order, type Viewer } from '../../api'

const props = defineProps<{ role: Viewer['role'] }>()
const rows = ref<Order[]>()
const from = ref(today())
const through = ref(today(29))
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
    <h2>{{ role === 'OWNER' ? '预约订单' : '我的预约' }}</h2>
    <form class="order-filters" @submit.prevent="load">
      <label>开始日期<input v-model="from" type="date" required /></label>
      <label>结束日期<input v-model="through" type="date" :min="from" required /></label>
      <button class="button button-outline" :disabled="loading || cancelling">查询</button>
    </form>
    <p v-if="loading" role="status">正在查询预约…</p>
    <p v-if="error" class="notice error" role="alert">{{ error }} <button class="text-button" @click="load">重试</button></p>
    <p v-if="notice" class="notice success" role="status">{{ notice }}</p>
    <p v-if="!loading && !error && rows && !rows.length" class="muted">这个日期范围内还没有预约。</p>
    <ul v-if="rows?.length" class="data-list order-list">
      <li v-for="row in rows" :key="row.id">
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
