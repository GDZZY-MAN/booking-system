<script setup lang="ts">
import { ref } from 'vue'
import { api, message } from '../../api'
interface Overview { pending: number; serviceToday: number; seatsToday: number; openVenues: number; closedVenues: number; venueTasks: { id: number; name: string }[] }
const data = ref<Overview>(), error = ref('')
async function load() {
  error.value = ''
  try { data.value = await api('/admin/overview') } catch (issue) { error.value = message(issue) }
}
void load()
</script>
<template>
  <section class="panel"><h2>今日运营</h2>
    <p v-if="error" class="notice error">{{ error }} <button @click="load">重试</button></p>
    <template v-if="data"><div class="stat-grid">
      <RouterLink to="/admin?tab=merchants&state=PENDING"><strong>{{ data.pending }}</strong>商家待审核 →</RouterLink>
      <div><strong>{{ data.serviceToday }}</strong>今日服务预约</div><div><strong>{{ data.seatsToday }}</strong>今日座位预约</div>
      <RouterLink to="/admin?tab=venues"><strong>{{ data.openVenues }} / {{ data.closedVenues }}</strong>空间已开放 / 未开放 →</RouterLink>
    </div>
    <h3>待完善的空间</h3><p v-if="data.venueTasks.length" class="muted">请检查地点、使用规则和可用座位。</p>
    <div class="actions"><RouterLink v-for="row in data.venueTasks" :key="row.id" class="button button-small" :to="{path:'/admin',query:{tab:'venues',venue:row.id}}">{{ row.name }} ↗</RouterLink></div>
    <p v-if="!data.venueTasks.length" class="muted">当前空间资料与可用座位已齐备。</p></template>
    <p v-else-if="!error" role="status">正在汇总预约…</p>
  </section>
</template>
