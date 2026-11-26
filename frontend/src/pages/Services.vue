<script setup lang="ts">
import { computed, ref } from 'vue'
import { api, message, yuan } from '../api'
import { serviceCategories, type ServiceListing } from '../campus'
import SiteHeader from '../components/SiteHeader.vue'
const listings = ref<ServiceListing[]>([])
const category = ref('')
const query = ref('')
const loading = ref(true)
const error = ref('')
const filtered = computed(() => listings.value.filter(item => (!category.value || item.category === category.value)
  && (item.name + item.description + item.address).toLowerCase().includes(query.value.trim().toLowerCase())))
async function load() {
  loading.value = true; error.value = ''
  try { listings.value = await api<ServiceListing[]>('/public/campus/services') }
  catch (issue) { error.value = message(issue) }
  finally { loading.value = false }
}
void load()
</script>
<template>
  <SiteHeader />
  <main id="main" class="content-width page-space">
    <div class="campus-page-heading"><p class="campus-eyebrow">校园日常，提前约好</p><h1>约服务</h1><p class="muted">查看校内与校园周边服务，选择项目、服务人员和可约时间。服务结束后，可在“我的安排”留下真实评价。</p></div>
    <div class="discovery-filters panel"><label>查找服务<input v-model="query" type="search" placeholder="搜索服务商名称、介绍或地点" /></label>
      <label>服务分类<select v-model="category"><option value="">全部服务</option><option v-for="(label, key) in serviceCategories" :key="key" :value="key">{{ label }}</option></select></label></div>
    <p v-if="loading" role="status">正在查找已发布的服务…</p>
    <p v-else-if="error" class="notice error" role="alert">{{ error }} <button class="text-button" @click="load">重试</button></p>
    <div v-else-if="!filtered.length" class="panel"><h2>{{ listings.length ? '没有找到匹配的服务' : '校园服务正在入驻' }}</h2><p class="muted">{{ listings.length ? '试试其他分类或关键词。' : '服务商发布服务与排班后，会出现在这里。' }}</p><RouterLink v-if="!listings.length" to="/register">开通服务预约</RouterLink></div>
    <div v-else class="venue-grid">
      <RouterLink v-for="item in filtered" :key="item.slug" :to="'/s/' + item.slug" class="panel venue-card discovery-card">
        <img v-if="item.cover" :src="item.cover" class="venue-cover" loading="lazy" alt="" />
        <div class="store-card-heading"><img v-if="item.logo" :src="item.logo" class="directory-logo" loading="lazy" alt="" />
          <div><span class="discovery-category">{{ serviceCategories[item.category] }}</span><h2>{{ item.name }}</h2></div></div>
        <p class="price-summary">{{ [item.hasFree ? '含免费项目' : '', item.minPriceCents != null ? yuan(item.minPriceCents) + ' 起' : ''].filter(Boolean).join(' · ') || '价格咨询' }}</p>
        <p class="muted preserve-lines">{{ item.description || '进入服务页查看项目、服务人员和可约时段。' }}</p>
        <p class="discovery-location">{{ item.address || '地点由服务商补充' }}</p>
        <div class="discovery-facts"><span>{{ item.serviceCount }} 项服务</span><span>{{ item.staffCount }} 位服务人员</span><span>{{ item.rating ? item.rating.toFixed(1) + ' 分 · ' + item.reviewCount + ' 条评价' : '暂无评价' }}</span></div>
        <span class="button button-dark">查看服务与时间</span>
      </RouterLink>
    </div>
  </main>
</template>
