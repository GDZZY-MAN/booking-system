<script setup lang="ts">
import { computed, ref } from 'vue'
import { api, merchantStates, message, timeRange, type Order, type Setup, type Shop } from '../../api'
const data = ref<{ shop: Shop; setup: Setup; today: Order[] }>()
const error = ref('')
const active = computed(() => data.value?.today.filter(row => row.status === 'CONFIRMED') ?? [])
const next = computed(() => active.value.find(row => Date.parse(row.endsAt) > Date.now()))
const cancelled = computed(() => data.value?.today.filter(row => row.status === 'CANCELLED') ?? [])
async function load() {
  error.value = ''
  try { data.value = await api('/owner/overview') } catch (issue) { error.value = message(issue) }
}
void load()
</script>
<template>
  <div class="workspace-stack"><p v-if="error" class="notice error">{{ error }} <button @click="load">重试</button></p>
    <template v-if="data"><section class="panel">
      <div class="panel-heading"><h2>{{ data.shop.name }}</h2><RouterLink to="/owner?tab=shop">{{ merchantStates[data.shop.merchantState] }}{{ data.shop.merchantState === 'APPROVED' ? data.shop.published ? ' · 接单中' : ' · 已暂停接单' : '' }}</RouterLink></div>
      <div class="stat-grid"><RouterLink to="/owner?tab=orders"><strong>{{ active.length }}</strong>今日预约</RouterLink><div><strong>{{ cancelled.length }}</strong>今日时段已取消</div></div>
      <h3>下一位顾客</h3><p v-if="next">{{ next.customerName }} · {{ next.serviceName }} · {{ next.employeeName }} · {{ timeRange(next) }}</p><p v-else class="muted">今天没有待接待的顾客。</p>
      <div v-if="cancelled.length"><h3>取消记录</h3><p v-for="row in cancelled" :key="row.id">{{ timeRange(row) }} · {{ row.customerName }} · {{ row.serviceName }}</p></div>
    </section><section class="panel"><h2>入驻进度</h2>
      <p v-if="data.shop.reviewNote">{{ data.shop.reviewNote }}</p>
      <div class="actions"><RouterLink v-if="!data.setup.hasActiveService" class="button" to="/owner?tab=services">添加上架服务</RouterLink>
        <RouterLink v-if="!data.setup.hasBookableEmployee" class="button" to="/owner?tab=team">设置服务人员与排班</RouterLink>
        <RouterLink class="button" to="/owner?tab=shop">{{ ['DRAFT','REJECTED'].includes(data.shop.merchantState) ? '完善资料与提交审核' : '查看入驻与接单状态' }}</RouterLink></div>
    </section></template><p v-else-if="!error" role="status">正在加载今日安排…</p>
  </div>
</template>
