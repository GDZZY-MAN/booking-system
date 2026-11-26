<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { priceLabel, type Offering, type Employee } from '../../api'
import { minutesLabel } from '../../library'
const route = useRoute()
const setup = ref<{ services: Offering[]; employees: Employee[] }>()
const setupError = ref('')
import { api, dayOf, timeRange, today, message, merchantStates, type MerchantState, type Order } from '../../api'
import { serviceCategories } from '../../campus'
interface Merchant { id: number; slug: string; name: string; description: string; merchantState: MerchantState; published: boolean; reviewNote: string; ownerUsername: string; category: keyof typeof serviceCategories; address: string }
const rows = ref<Merchant[]>([]), orders = ref<Order[]>([])
const selected = ref(0), state = ref(route.query.state === 'PENDING' ? 'PENDING' : ''), search = ref(''), note = ref('')
const loading = ref(false), busy = ref(false), error = ref(''), ordersError = ref(''), notice = ref('')
const from = ref(today(-7)), through = ref(today(7))
const current = computed(() => rows.value.find(row => row.id === selected.value))
const filtered = computed(() => rows.value.filter(row => (state.value ? state.value === row.merchantState : row.merchantState !== 'DELETED')
  && (row.name + row.ownerUsername + row.slug).toLowerCase().includes(search.value.trim().toLowerCase())))
let version = 0
watch(state, () => { selected.value = 0; orders.value = []; setup.value = undefined; ++version })
async function load() { loading.value = true; error.value = ''; try { rows.value = await api<Merchant[]>('/admin/merchants') } catch (issue) { error.value = message(issue) } finally { loading.value = false } }
async function loadOrders() {
  const id = selected.value, token = ++version
  orders.value = []; ordersError.value = ''
  if (!id) return
  try { const result = await api<Order[]>('/admin/merchants/' + id + '/bookings?from=' + from.value + '&through=' + through.value); if (token === version) orders.value = result }
  catch (issue) { if (token === version) ordersError.value = message(issue) }
}
async function choose(row: Merchant) {
  selected.value = row.id; note.value = ''; notice.value = ''; setup.value = undefined; setupError.value = ''
  void loadOrders()
  try {
    const result = await api<{ services: Offering[]; employees: Employee[] }>('/admin/merchants/' + row.id + '/setup')
    if (selected.value === row.id) setup.value = result
  } catch (issue) { if (selected.value === row.id) setupError.value = message(issue) }
}
async function decide(action: string) {
  if (!current.value || busy.value) return
  busy.value = true; error.value = ''; notice.value = ''
  try { await api('/admin/merchants/' + current.value.id + '/decision', 'POST', { action, note: note.value }); await load(); note.value = ''; notice.value = '商家状态已更新。' }
  catch (issue) { error.value = message(issue) } finally { busy.value = false }
}
async function removeOrRestore() {
  const row = current.value
  if (!row || busy.value) return
  const restore = row.merchantState === 'DELETED'
  if (!window.confirm(restore ? '恢复「' + row.name + '」？恢复后须重新提交审核。' : '删除「' + row.name + '」？商家将移入回收站并停用，历史订单和账号标识保留。有未结束预约时不能删除。')) return
  busy.value = true; error.value = ''; notice.value = ''
  try {
    await api('/admin/merchants/' + row.id + (restore ? '/restore' : ''), restore ? 'POST' : 'DELETE')
    selected.value = 0; orders.value = []; setup.value = undefined; ++version
    await load()
    notice.value = restore ? '商家已恢复，需重新提交入驻申请。' : '商家已移入回收站，可在入驻状态中选择“已删除（回收站）”恢复。'
  } catch (issue) { error.value = message(issue) }
  finally { busy.value = false }
}
void load()
</script>
<template>
  <p class="muted">查看商家入驻申请、服务资料与预约记录。下架停止新预约，已有订单和历史记录仍保留。</p>
  <p v-if="error" class="notice error" role="alert">{{ error }}</p><p v-if="notice" class="notice success" role="status">{{ notice }}</p>
  <div class="discovery-filters panel"><label>查找商家<input v-model="search" type="search" placeholder="名称、账号或链接标识" /></label><label>入驻状态<select v-model="state"><option value="">全部状态</option><option v-for="(label, key) in merchantStates" :key="key" :value="key">{{ label }}</option></select></label><button class="button button-small" :disabled="loading" @click="load">刷新列表</button></div>
  <p v-if="loading" role="status">正在加载商家…</p>
  <div class="admin-master-detail"><section class="panel"><h2>商家列表</h2><p v-if="!filtered.length && !loading" class="muted">暂无符合条件的商家。</p><ul class="social-threads"><li v-for="row in filtered" :key="row.id"><button class="social-thread" :class="{ current: selected === row.id }" :disabled="busy" @click="choose(row)"><strong>{{ row.name }}</strong><span>{{ merchantStates[row.merchantState] }}{{ row.merchantState === 'APPROVED' ? row.published ? ' · 营业中' : ' · 商家已暂停' : '' }}</span><small>{{ row.ownerUsername }}</small></button></li></ul></section>
    <section class="panel"><p v-if="!current" class="muted">选择一家商家，查看资料和处理入驻申请。</p><template v-else><h2>{{ current.name }}</h2><p>{{ current.description || '暂无介绍' }}</p><div class="discovery-facts"><span>{{ serviceCategories[current.category] }}</span><span>{{ current.address || '地点待完善' }}</span><span>店主：{{ current.ownerUsername }}</span></div>
      <details><summary>查看服务项目与排班</summary>
        <p v-if="setupError" class="notice error">{{ setupError }} <button @click="choose(current)">重试</button></p>
        <template v-if="setup"><h3>服务项目</h3><p v-for="item in setup.services" :key="item.id">{{ item.name }} · {{ item.durationMinutes }} 分钟 · {{ priceLabel(item) }} · {{ item.active ? '已上架' : '已下架' }}</p><p v-if="!setup.services.length">暂无服务。</p>
          <h3>员工排班</h3><div v-for="person in setup.employees" :key="person.id"><strong>{{ person.name }} · {{ person.active ? '启用' : '停用' }}</strong>
            <p>{{ setup.services.filter(item => person.serviceIds.includes(item.id)).map(item => item.name).join('、') || '未分配服务' }}</p>
            <p>{{ person.shifts.map(s => '周' + '一二三四五六日'[s.weekday-1] + ' ' + minutesLabel(s.startMinute) + '–' + minutesLabel(s.endMinute)).join('、') || '暂无排班' }}</p>
            <p v-if="person.closedDates.length">停约日期 {{ person.closedDates.join('、') }}</p></div><p v-if="!setup.employees.length">暂无员工。</p></template>
      </details>
      <p v-if="current.reviewNote" class="notice">上次处理说明：{{ current.reviewNote }}</p>
      <label>处理说明<textarea v-model.trim="note" maxlength="500" rows="3" placeholder="驳回、下架时必须填写；通过或恢复时可补充说明。"></textarea></label>
      <div class="actions"><button class="button" :class="{ 'button-danger': current.merchantState !== 'DELETED' }" :disabled="busy" @click="removeOrRestore">{{ current.merchantState === 'DELETED' ? '恢复商家' : '删除商家' }}</button><template v-if="current.merchantState === 'PENDING'"><button class="button button-dark" :disabled="busy" @click="decide('approve')">审核通过</button><button class="button button-danger" :disabled="busy || !note" @click="decide('reject')">驳回申请</button></template>
        <button v-if="current.merchantState === 'APPROVED'" class="button button-danger" :disabled="busy || !note" @click="decide('suspend')">下架商家</button><button v-if="current.merchantState === 'SUSPENDED'" class="button" :disabled="busy" @click="decide('resume')">恢复展示与预约</button>
        <RouterLink v-if="current.published" class="button" :to="'/s/' + current.slug">查看公开服务页</RouterLink></div>
      <p v-if="['DRAFT', 'REJECTED'].includes(current.merchantState)" class="muted">等待商家完善资料并提交申请后，才能审核。</p>
      <h3 class="admin-orders-heading">预约记录</h3><form class="date-range" @submit.prevent="loadOrders"><label>开始日期<input v-model="from" type="date" required /></label><label>结束日期<input v-model="through" type="date" required :min="from" /></label><button class="button button-small">查询</button></form>
      <p v-if="ordersError" class="notice error" role="alert">{{ ordersError }}</p><p v-else-if="!orders.length" class="muted">该日期范围暂无预约。</p>
      <ul class="data-list"><li v-for="order in orders" :key="order.id"><div><strong>{{ order.serviceName }} · {{ order.employeeName }}</strong><small>{{ dayOf(order.startsAt) }} · {{ timeRange(order) }}</small><small>顾客：{{ order.customerName }} · {{ order.contact }}</small><small>{{ order.status === 'CONFIRMED' ? '已确认' : '已取消' }}</small></div></li></ul>
    </template></section></div>
</template>
