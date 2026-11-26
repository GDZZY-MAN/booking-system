<script setup lang="ts">
import { computed, ref } from 'vue'
import { api, message } from '../api'
import type { Venue } from '../library'
import SiteHeader from '../components/SiteHeader.vue'
import { spaceTypes, quietLevels } from '../campus'
import { minutesLabel } from '../library'
const venues = ref<Venue[]>([])
const loading = ref(true)
const error = ref('')
const kind = ref(''), query = ref('')
const power = ref(false), windowSeat = ref(false)
const filtered = computed(() => venues.value.filter(venue => (!kind.value || venue.spaceType === kind.value)
  && (!power.value || venue.powerCount > 0) && (!windowSeat.value || venue.windowCount > 0)
  && (venue.name + venue.description + venue.address).toLowerCase().includes(query.value.trim().toLowerCase())))
async function load() {
  loading.value = true
  error.value = ''
  try { venues.value = await api<Venue[]>('/public/libraries') }
  catch (issue) { error.value = message(issue) }
  finally { loading.value = false }
}
void load()
</script>
<template>
  <SiteHeader show-home />
  <main id="main" class="content-width page-space">
    <div class="campus-page-heading"><p class="campus-eyebrow">给专注一个合适的位置</p><h1>找座位</h1><p class="muted">图书馆、自习室、研讨间。先了解空间和使用规则，再选时段、楼层与座位。</p></div>
    <div class="discovery-filters panel"><label>查找空间<input v-model="query" type="search" placeholder="搜索空间名称、介绍或地点" /></label>
      <label>空间类型<select v-model="kind"><option value="">全部空间</option><option v-for="(label, key) in spaceTypes" :key="key" :value="key">{{ label }}</option></select></label>
      <div class="discovery-preferences"><label class="check"><input v-model="power" type="checkbox" /> 场馆有插座</label><label class="check"><input v-model="windowSeat" type="checkbox" /> 场馆有靠窗座位</label></div></div>
    <p v-if="!loading && !error && venues.length" class="muted" role="status">找到 {{ filtered.length }} / {{ venues.length }} 处空间。这里筛选场馆设施，未勾选表示不限。进入场馆选择时段后，可筛选具体座位。</p>
    <p v-if="loading" role="status">正在查找开放的场所…</p>
    <p v-else-if="error" class="notice error" role="alert">{{ error }} <button class="text-button" @click="load">重试</button></p>
    <div v-else-if="!venues.length" class="panel"><h2>暂时没有开放的座位预约站</h2>
      <p class="muted">平台管理员可以在校园空间管理中添加并发布场馆。</p><RouterLink to="/admin/login">平台管理员登录</RouterLink></div>
    <div v-else-if="!filtered.length" class="panel"><h2>暂未找到符合偏好的空间</h2><p class="muted">试试其他空间类型，或取消部分筛选条件。</p></div>
    <div v-else class="venue-grid">
      <RouterLink v-for="venue in filtered" :key="venue.slug" :to="'/library/' + venue.slug" class="panel venue-card discovery-card">
        <img :src="venue.cover || '/space-default.svg'" class="venue-cover" loading="lazy" alt="" />
        <span class="discovery-category">{{ spaceTypes[venue.spaceType] }} · 开放预约</span><h2>{{ venue.name }}</h2><p class="muted preserve-lines">{{ venue.description }}</p>
        <p class="discovery-location">{{ venue.address || '地点由管理员补充' }}</p>
        <div class="discovery-facts"><span>{{ quietLevels[venue.quietLevel] }}</span><span>{{ venue.floors ? venue.floors + ' 楼' : '楼层待补充' }}</span><span>{{ venue.seatCount }} 个开放座位</span><span>插座 {{ venue.powerCount }} · 靠窗 {{ venue.windowCount }}</span></div>
        <p>{{ minutesLabel(venue.openMinute) }}–{{ minutesLabel(venue.closeMinute) }} · 每时段 {{ venue.slotMinutes }} 分钟</p>
        <span class="button button-dark">查看空间与座位</span>
      </RouterLink>
    </div>
  </main>
</template>
