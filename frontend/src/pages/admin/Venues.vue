<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRoute } from 'vue-router'
import SharePanel from '../../components/SharePanel.vue'
import ArchiveVenueDialog from '../../components/ArchiveVenueDialog.vue'
import VenueCover from '../../components/VenueCover.vue'
import ActionMenu from '../../components/ActionMenu.vue'
import { api, message, type Shop } from '../../api'
import { spaceTypes } from '../../campus'
import Seats from '../library/Seats.vue'
import Profile from '../library/Profile.vue'
const route = useRoute()
const tabs = { overview: '空间概览', profile: '资料与封面', seats: '座位和区域', hours: '开放时间', orders: '预约记录', share: '分享与二维码' } as const
interface Venue { id: number; name: string; slug: string; published: boolean; spaceType: keyof typeof spaceTypes; seatCount: number }
const rows = ref<Venue[]>([]), selected = ref(Number(route.query.venue) || 0), editor = ref<keyof typeof tabs>('overview')
const creating = ref(false), busy = ref(false), loading = ref(false), error = ref('')
const name = ref(''), slug = ref('')
const current = computed(() => rows.value.find(row => row.id === selected.value))
const trash = ref(false), unfinished = ref(false), notice = ref('')
const deleting = ref<Venue>()
let request = 0
async function load() {
  const token = ++request
  loading.value = true; error.value = ''
  try {
    const result = await api<Venue[]>('/admin/venues' + (trash.value ? '?archived=true' : ''))
    if (token === request) rows.value = result
  } catch (issue) { if (token === request) error.value = message(issue) }
  finally { if (token === request) loading.value = false }
}
function switchList() {
  trash.value = !trash.value
  selected.value = 0; creating.value = false; rows.value = []; notice.value = ''
  void load()
}
function openFromDialog(section: 'hours' | 'orders') {
  if (!deleting.value) return
  selected.value = deleting.value.id
  editor.value = section; unfinished.value = section === 'orders'; deleting.value = undefined
}
async function archived() {
  notice.value = '「' + deleting.value?.name + '」已移入回收站。'
  rows.value = rows.value.filter(row => row.id !== deleting.value?.id)
  deleting.value = undefined; selected.value = 0
  await load()
}
async function restore(row: Venue) {
  busy.value = true; error.value = ''; notice.value = ''
  try {
    await api('/admin/venues/' + row.id + '/restore', 'POST')
    rows.value = rows.value.filter(item => item.id !== row.id)
    notice.value = '「' + row.name + '」已恢复。请在空间列表中检查并重新开放预约。'
    await load()
  } catch (issue) { error.value = message(issue) }
  finally { busy.value = false }
}
async function create() {
  busy.value = true; error.value = ''
  try { const venue = await api<Shop>('/admin/venues', 'POST', { name: name.value, slug: slug.value }); await load(); selected.value = venue.id; editor.value = 'profile'; creating.value = false; name.value = ''; slug.value = '' }
  catch (issue) { error.value = message(issue) } finally { busy.value = false }
}
void load()
</script>
<template>
  <div class="panel-heading"><h2>{{ trash ? '空间回收站' : '校园空间' }}</h2><div class="actions">
    <button class="text-button" :disabled="loading || busy" @click="load">刷新空间列表</button>
    <button class="text-button" :disabled="busy" @click="switchList">{{ trash ? '返回校园空间' : '回收站' }}</button>
    <button v-if="!trash" class="button button-dark" :disabled="busy" @click="creating = !creating">添加校园空间</button></div></div>
  <p v-if="notice" class="notice success" role="status">{{ notice }}</p>
  <p v-if="error" class="notice error" role="alert">{{ error }}</p>
  <form v-if="creating" class="panel form-stack" @submit.prevent="create"><h2>新建空间</h2><label>空间名称<input v-model.trim="name" required maxlength="80" placeholder="例如：东区图书馆" /></label><label>页面链接标识<input v-model.trim="slug" required minlength="3" maxlength="60" pattern="[a-z0-9][a-z0-9\-]{1,58}[a-z0-9]" placeholder="east-library" /><small class="muted">这是同一网站内的空间地址，不会创建新网站。创建后可继续配置座位和开放时间。</small></label><div class="actions"><button class="button" :disabled="busy">创建空间</button><button type="button" class="button" @click="creating = false">取消</button></div></form>
  <p v-if="loading" role="status">正在加载校园空间…</p>
  <div v-if="!current" class="venue-grid admin-venues"><article v-for="row in rows" :key="row.id" class="panel">
    <div class="panel-heading"><h3>{{ row.name }}</h3><span class="status" :class="{ live: row.published }">{{ trash ? '已删除' : row.published ? '已开放' : '未开放' }}</span></div>
    <p>{{ spaceTypes[row.spaceType] }} · {{ row.seatCount }} 个座位</p>
    <button v-if="trash" class="button button-small" :disabled="busy" @click="restore(row)">恢复空间</button>
    <div v-else class="actions"><button class="button button-small" @click="selected = row.id; editor = 'overview'; notice = ''">管理此空间</button>
      <ActionMenu label="更多"><button class="danger-link" @click="deleting = row">删除空间</button></ActionMenu></div>
  </article></div>
  <p v-if="!rows.length && !loading && !error" class="panel muted">{{ trash ? '回收站为空。' : '暂无校园空间，可添加第一个场馆。' }}</p>
  <ArchiveVenueDialog v-if="deleting" :key="deleting.id" :venue="deleting" @close="deleting = undefined" @archived="archived" @hours="openFromDialog('hours')" @orders="openFromDialog('orders')" />
  <section v-if="current" class="admin-venue-editor"><div class="panel-heading"><h2>{{ current.name }}</h2><button class="button button-small" @click="selected = 0">返回空间列表</button></div>
    <nav class="section-tabs" aria-label="场馆管理选项"><button v-for="(label,key) in tabs" :key="key" class="button button-small" :aria-pressed="editor === key" @click="editor = key; unfinished = false">{{ label }}</button></nav>
    <section v-if="editor === 'overview'" class="panel"><h3>{{ spaceTypes[current.spaceType] }}</h3>
      <p>{{ current.seatCount }} 个座位 · {{ current.published ? '已开放读者预约' : '尚未开放预约' }}</p>
      <div class="actions"><RouterLink v-if="current.published" class="button" :to="'/library/' + current.slug">查看预约页</RouterLink>
        <button class="button button-danger" @click="deleting = current">删除空间</button></div>
    </section>
    <template v-else-if="editor === 'profile'"><Profile :key="selected" :root="'/admin/venues/' + selected" @saved="load" /><VenueCover :key="'cover-' + selected" :root="'/admin/venues/' + selected" /></template>
    <SharePanel v-else-if="editor === 'share'" :name="current.name" :path="'/library/' + current.slug" :open="current.published" />
    <Seats v-else :key="selected + '-' + editor + '-' + unfinished" :root="'/admin/venues/' + selected" :section="editor" :unfinished="unfinished" @saved="load" />
  </section>
</template>
