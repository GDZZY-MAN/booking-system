<script setup lang="ts">
import { ref } from 'vue'
import { api, message, priceLabel, type Offering } from '../../api'
const priceYuan = ref<number | string>('')

const rows = ref<Offering[]>()
const edit = ref<Offering>()
const loading = ref(false)
const saving = ref(false)
const error = ref('')
const formError = ref('')
const notice = ref('')

async function load() {
  loading.value = true
  error.value = ''
  try { rows.value = await api<Offering[]>('/owner/services') }
  catch (issue) { error.value = message(issue) }
  finally { loading.value = false }
}

function open(row?: Offering) {
  edit.value = row ? { ...row } : { id: 0, name: '', durationMinutes: 45, active: true, priceMode: 'INQUIRE', priceCents: null }
  priceYuan.value = row?.priceCents == null ? '' : row.priceCents / 100
  formError.value = ''
  notice.value = ''
}

async function save() {
  if (!edit.value) return
  saving.value = true
  formError.value = ''
  const { id, ...input } = edit.value
  input.priceCents = input.priceMode === 'FIXED' ? Math.round(Number(priceYuan.value) * 100) : null
  try {
    await api('/owner/services' + (id ? '/' + id : ''), id ? 'PUT' : 'POST', input)
    edit.value = undefined
    notice.value = '服务已保存。'
    await load()
  } catch (issue) { formError.value = message(issue) }
  finally { saving.value = false }
}

void load()
</script>
<template>
  <div class="workspace-stack">
    <section class="panel">
      <div class="panel-heading"><h2>服务项目</h2><button class="button button-dark" :disabled="saving" @click="open()">＋ 添加服务</button></div>
      <p v-if="loading" role="status">正在加载服务…</p>
      <p v-if="error" class="notice error" role="alert">{{ error }} <button class="text-button" @click="load">重试</button></p>
      <p v-if="notice" class="notice success" role="status">{{ notice }}</p>
      <p v-if="rows && !rows.length" class="muted">还没有服务。添加后，可以为员工分配服务和排班。</p>
      <ul v-if="rows?.length" class="data-list">
        <li v-for="row in rows" :key="row.id">
          <div><strong>{{ row.name }}</strong><small>{{ row.durationMinutes }} 分钟 · {{ priceLabel(row) }}</small></div>
          <span class="status" :class="{ live: row.active }">{{ row.active ? '已上架' : '已下架' }}</span>
          <button class="button button-outline" :disabled="saving" @click="open(row)">编辑</button>
        </li>
      </ul>
    </section>
    <section v-if="edit" class="panel">
      <h2>{{ edit.id ? '编辑服务' : '添加服务' }}</h2>
      <form class="form-stack" @submit.prevent="save">
        <label>服务名称<input v-model.trim="edit.name" maxlength="80" required /></label>
        <label>服务时长（分钟）<input v-model.number="edit.durationMinutes" type="number" min="1" max="480" step="1" required />
          <small class="muted">按实际服务自定，支持 1–480 分钟，例如 40、50 或 75 分钟。</small></label>
        <label>价格方式<select v-model="edit.priceMode"><option value="INQUIRE">价格咨询</option><option value="FREE">免费</option><option value="FIXED">固定价格</option></select></label>
        <label v-if="edit.priceMode === 'FIXED'">价格（元）<input v-model.number="priceYuan" type="number" required min="0.01" max="21474836.47" step="0.01" /></label>
        <label class="check"><input v-model="edit.active" type="checkbox" /> 上架后允许顾客选择</label>
        <p v-if="formError" class="notice error" role="alert">{{ formError }}</p>
        <div class="actions">
          <button class="button button-dark" :disabled="saving">保存服务</button>
          <button type="button" class="button button-ghost" :disabled="saving" @click="edit = undefined">取消</button>
        </div>
      </form>
    </section>
  </div>
</template>
