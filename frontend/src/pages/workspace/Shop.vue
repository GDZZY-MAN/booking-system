<script setup lang="ts">
import { computed, ref } from 'vue'
import { api, message, type Setup, type Shop } from '../../api'

const shop = ref<Shop>()
const setup = ref<Setup>()
const name = ref('')
const description = ref('')
const error = ref('')
const setupError = ref('')
const notice = ref('')
const pending = ref(false)
const loading = ref(false)
const link = computed(() => shop.value ? location.origin + '/s/' + shop.value.slug : '')

async function loadSetup() {
  setupError.value = ''
  try { setup.value = await api<Setup>('/owner/shop/setup') }
  catch (issue) { setupError.value = message(issue) }
}

async function load() {
  error.value = ''
  loading.value = true
  try {
    shop.value = await api<Shop>('/owner/shop')
    name.value = shop.value.name
    description.value = shop.value.description
  } catch (issue) { error.value = message(issue) }
  finally { loading.value = false }
}

async function save(publication?: boolean) {
  pending.value = true
  error.value = ''
  notice.value = ''
  try {
    if (publication === undefined) {
      shop.value = await api<Shop>('/owner/shop', 'PUT', { name: name.value, description: description.value })
      notice.value = '店铺资料已保存。'
    } else {
      shop.value = await api<Shop>('/owner/shop/publication', 'PUT', { published: publication })
      notice.value = publication ? '预约页已发布，可以分享链接。' : '预约页已取消发布。'
      await loadSetup()
    }
  } catch (issue) { error.value = message(issue) }
  finally { pending.value = false }
}

async function copy() {
  error.value = ''
  try { await navigator.clipboard.writeText(link.value); notice.value = '预约链接已复制。' }
  catch { error.value = '自动复制失败，请选中链接手动复制。' }
}

void load()
void loadSetup()
</script>

<template>
  <div class="workspace-stack">
    <section class="panel">
      <div class="panel-heading"><h2>店铺与发布</h2><span v-if="shop" class="status" :class="{ live: shop.published }">{{ shop.published ? '已发布' : '草稿' }}</span></div>
      <p v-if="loading" role="status">正在加载店铺…</p>
      <p v-if="error" class="notice error" role="alert">{{ error }}</p>
      <p v-if="notice" class="notice success" role="status">{{ notice }}</p>
      <button v-if="!shop && !loading" class="button button-outline" @click="load">重新加载</button>
      <template v-if="shop">
        <form class="form-stack" @submit.prevent="save()">
          <label>店铺名称<input v-model.trim="name" required maxlength="80" /></label>
          <label>店铺介绍<textarea v-model.trim="description" rows="3" maxlength="500" placeholder="顾客打开预约页时看到的介绍"></textarea></label>
          <div><button class="button button-outline" :disabled="pending">保存店铺资料</button></div>
        </form>
      </template>
    </section>
    <section v-if="shop" class="panel">
      <h2>开通预约</h2>
      <p><RouterLink to="/owner?tab=branding">设置预约站外观并预览 ↗</RouterLink></p>
      <p v-if="setupError" class="notice error" role="alert">{{ setupError }} <button class="text-button" @click="loadSetup">重试</button></p>
      <ol v-else-if="setup" class="setup-list">
        <li><span :class="{ done: setup.hasActiveService }">{{ setup.hasActiveService ? '✓' : '1' }}</span><RouterLink to="/owner?tab=services">配置一项上架服务</RouterLink></li>
        <li><span :class="{ done: setup.hasBookableEmployee }">{{ setup.hasBookableEmployee ? '✓' : '2' }}</span><RouterLink to="/owner?tab=team">为服务安排员工和排班</RouterLink></li>
        <li><span :class="{ done: shop.published }">{{ shop.published ? '✓' : '3' }}</span>发布并分享预约链接</li>
      </ol>
      <p v-else class="muted" role="status">正在检查开通进度…</p>
      <label>店铺预约链接<input :value="link" readonly @focus="($event.target as HTMLInputElement).select()" /></label>
      <div class="actions">
        <button v-if="!shop.published" class="button button-dark" :disabled="pending || !setup?.hasBookableEmployee" @click="save(true)">发布预约页</button>
        <template v-else>
          <button class="button button-dark" @click="copy">复制链接</button>
          <a class="button button-outline" :href="link" target="_blank" rel="noopener">打开预约页 ↗</a>
          <button class="button button-ghost" :disabled="pending" @click="save(false)">取消发布</button>
        </template>
      </div>
    </section>
  </div>
</template>
