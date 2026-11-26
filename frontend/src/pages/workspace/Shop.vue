<script setup lang="ts">
import { ref } from 'vue'
import { api, message, merchantStates, type Setup, type Shop } from '../../api'
import DiscoveryEditor from '../../components/DiscoveryEditor.vue'

const shop = ref<Shop>()
const setup = ref<Setup>()
const name = ref('')
const description = ref('')
const error = ref('')
const setupError = ref('')
const notice = ref('')
const pending = ref(false)
const loading = ref(false)

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
      notice.value = publication ? shop.value.published ? '预约页已开放。' : '入驻申请已提交，审核通过后会开放预约。' : '已停止接收新预约。'
      await loadSetup()
    }
  } catch (issue) { error.value = message(issue) }
  finally { pending.value = false }
}


void load()
void loadSetup()
</script>

<template>
  <div class="workspace-stack">
    <DiscoveryEditor area="service" />
    <section class="panel">
      <div class="panel-heading"><h2>店铺与入驻审核</h2><span v-if="shop" class="status" :class="{ live: shop.published }">{{ merchantStates[shop.merchantState] }}{{ shop.merchantState === 'APPROVED' ? shop.published ? ' · 营业中' : ' · 已暂停' : '' }}</span></div>
      <p v-if="shop?.reviewNote" class="notice" :class="{ error: ['REJECTED', 'SUSPENDED'].includes(shop.merchantState) }">平台处理说明：{{ shop.reviewNote }}</p>
      <p v-if="shop?.merchantState === 'PENDING'" class="notice">平台正在审核入驻申请。资料可继续完善，审核通过前不会公开展示或接收预约。</p>
      <p v-if="shop?.merchantState === 'SUSPENDED'" class="notice error">平台已下架本商家，不能自行重新发布。现有订单仍保留，请联系平台处理。</p>
      <p v-if="loading" role="status">正在加载店铺…</p>
      <p v-if="error" class="notice error" role="alert">{{ error }}</p>
      <p v-if="notice" class="notice success" role="status">{{ notice }}</p>
      <button v-if="!shop && !loading" class="button button-outline" @click="load">重新加载</button>
      <template v-if="shop">
        <form class="form-stack" @submit.prevent="save()">
          <label>店铺名称<input v-model.trim="name" required maxlength="80" /></label>
          <label>店铺介绍<textarea v-model.trim="description" rows="3" maxlength="500" placeholder="顾客打开预约页时看到的介绍"></textarea></label>
          <div><button class="button button-dark" :disabled="pending">保存店铺资料</button></div>
        </form>
      </template>
    </section>
    <section v-if="shop" class="panel">
      <h2>准备并提交入驻</h2>
      <p><RouterLink to="/owner?tab=branding">设置预约站外观并预览 ↗</RouterLink></p>
      <p v-if="setupError" class="notice error" role="alert">{{ setupError }} <button class="text-button" @click="loadSetup">重试</button></p>
      <ol v-else-if="setup" class="setup-list">
        <li><span :class="{ done: setup.hasActiveService }">{{ setup.hasActiveService ? '✓' : '1' }}</span><RouterLink to="/owner?tab=services">配置一项上架服务</RouterLink></li>
        <li><span :class="{ done: setup.hasBookableEmployee }">{{ setup.hasBookableEmployee ? '✓' : '2' }}</span><RouterLink to="/owner?tab=team">为服务安排员工和排班</RouterLink></li>
        <li><span :class="{ done: shop.merchantState === 'APPROVED' }">{{ shop.merchantState === 'APPROVED' ? '✓' : '3' }}</span>提交入驻，平台审核通过后开放预约</li>
      </ol>
      <p v-else class="muted" role="status">正在检查开通进度…</p>
      <div class="actions">
        <button v-if="!shop.published && !['PENDING', 'SUSPENDED'].includes(shop.merchantState)" class="button button-dark" :disabled="pending || !setup?.hasBookableEmployee" @click="save(true)">{{ shop.merchantState === 'APPROVED' ? '恢复接收预约' : shop.merchantState === 'REJECTED' ? '重新提交审核' : '提交入驻审核' }}</button>
        <button v-if="shop.merchantState === 'PENDING'" class="button button-outline" :disabled="pending" @click="save(false)">撤回申请</button>
        <button v-if="shop.published" class="button button-ghost" :disabled="pending" @click="save(false)">暂停接单</button>
      </div>
    </section>
  </div>
</template>
