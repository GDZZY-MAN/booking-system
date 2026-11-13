<script setup lang="ts">
import { ref } from 'vue'
import { api, dayOf, message, timeOf, type Employee, type Offering } from '../../api'

const weekdays = ['周一', '周二', '周三', '周四', '周五', '周六', '周日']
const minuteOptions = Array.from({ length: 97 }, (_, index) => index * 15)
const rows = ref<Employee[]>()
const services = ref<Offering[]>()
const edit = ref<Employee>()
const dayToClose = ref('')
const loading = ref(false)
const saving = ref(false)
const error = ref('')
const servicesError = ref('')
const formError = ref('')
const notice = ref('')
const inviting = ref(0)
const inviteLink = ref('')
const inviteExpires = ref('')
const inviteError = ref('')

const minuteLabel = (minute: number) =>
  String(Math.floor(minute / 60)).padStart(2, '0') + ':' + String(minute % 60).padStart(2, '0')

async function load() {
  loading.value = true
  error.value = ''
  try { rows.value = await api<Employee[]>('/owner/employees') }
  catch (issue) { error.value = message(issue) }
  finally { loading.value = false }
}

async function loadServices() {
  servicesError.value = ''
  try { services.value = await api<Offering[]>('/owner/services') }
  catch (issue) { servicesError.value = message(issue) }
}

function open(row?: Employee) {
  edit.value = row ? JSON.parse(JSON.stringify(row)) as Employee : {
    id: 0, name: '', active: true, serviceIds: [], closedDates: [],
    shifts: [1, 2, 3, 4, 5].map(weekday => ({ weekday, startMinute: 540, endMinute: 1080 })),
  }
  dayToClose.value = ''
  formError.value = ''
  notice.value = ''
}

function closeDate() {
  if (!edit.value || !dayToClose.value || edit.value.closedDates.includes(dayToClose.value)) return
  edit.value.closedDates.push(dayToClose.value)
  edit.value.closedDates.sort()
  dayToClose.value = ''
}

async function save() {
  if (!edit.value) return
  saving.value = true
  formError.value = ''
  const { id, ...input } = edit.value
  try {
    await api('/owner/employees' + (id ? '/' + id : ''), id ? 'PUT' : 'POST', input)
    edit.value = undefined
    notice.value = '员工和排班已保存。'
    await load()
  } catch (issue) { formError.value = message(issue) }
  finally { saving.value = false }
}

async function invite(row: Employee) {
  inviting.value = row.id
  inviteError.value = ''
  inviteLink.value = ''
  try {
    const result = await api<{ token: string; expiresAt: string }>('/owner/employees/' + row.id + '/invite', 'POST')
    inviteLink.value = location.origin + '/invite#' + result.token
    inviteExpires.value = dayOf(result.expiresAt) + ' ' + timeOf(result.expiresAt)
  } catch (issue) { inviteError.value = message(issue) }
  finally { inviting.value = 0 }
}

async function copy() {
  inviteError.value = ''
  try { await navigator.clipboard.writeText(inviteLink.value); notice.value = '邀请链接已复制。' }
  catch { inviteError.value = '自动复制失败，请选中链接手动复制。' }
}

void load()
void loadServices()
</script>
<template>
  <div class="workspace-stack">
    <section class="panel">
      <div class="panel-heading"><h2>员工与排班</h2><button class="button button-dark" :disabled="saving" @click="open()">＋ 添加员工</button></div>
      <p v-if="loading" role="status">正在加载员工…</p>
      <p v-if="error" class="notice error" role="alert">{{ error }} <button class="text-button" @click="load">重试</button></p>
      <p v-if="notice" class="notice success" role="status">{{ notice }}</p>
      <p v-if="rows && !rows.length" class="muted">还没有员工。添加一位员工，选择服务并设置可预约时间。</p>
      <ul v-if="rows?.length" class="data-list">
        <li v-for="row in rows" :key="row.id">
          <div><strong>{{ row.name }}</strong><small>{{ row.serviceIds.length }} 项服务 · 每周 {{ row.shifts.length }} 段排班</small></div>
          <span class="status" :class="{ live: row.active }">{{ row.active ? '接受预约' : '已停约' }}</span>
          <button class="button button-outline" :disabled="saving" @click="open(row)">编辑</button>
          <button class="button button-outline" :disabled="inviting !== 0" @click="invite(row)">{{ inviting === row.id ? '生成中…' : '邀请登录' }}</button>
        </li>
      </ul>
      <p v-if="inviteError" class="notice error" role="alert">{{ inviteError }}</p>
      <div v-if="inviteLink" class="invite-box">
        <label>员工邀请链接<input :value="inviteLink" readonly @focus="($event.target as HTMLInputElement).select()" /></label>
        <p class="muted">仅可使用一次，{{ inviteExpires }} 到期。重新生成会使旧链接失效。</p>
        <button class="button button-outline" @click="copy">复制邀请</button>
      </div>
    </section>
    <section v-if="edit" class="panel">
      <h2>{{ edit.id ? '编辑员工与排班' : '添加员工' }}</h2>
      <form class="form-stack" @submit.prevent="save">
        <label>员工姓名<input v-model.trim="edit.name" maxlength="60" required /></label>
        <label class="check"><input v-model="edit.active" type="checkbox" /> 接受新预约</label>
        <fieldset class="schedule-group">
          <legend>可提供的服务</legend>
          <p v-if="servicesError" class="notice error" role="alert">{{ servicesError }} <button type="button" class="text-button" @click="loadServices">重试</button></p>
          <p v-else-if="!services" class="muted">正在加载服务…</p>
          <p v-else-if="!services.length" class="muted">先到 <RouterLink to="/owner?tab=services">服务项目</RouterLink> 添加服务。</p>
          <div v-else class="checks">
            <label v-for="service in services" :key="service.id" class="check">
              <input v-model="edit.serviceIds" type="checkbox" :value="service.id" /> {{ service.name }}{{ service.active ? '' : '（已下架）' }}
            </label>
          </div>
        </fieldset>
        <fieldset class="schedule-group">
          <legend>每周排班（北京时间）</legend>
          <div v-for="(shift, index) in edit.shifts" :key="index" class="shift-row">
            <select v-model="shift.weekday" :aria-label="'第 ' + (index + 1) + ' 段的星期'">
              <option v-for="(weekday, day) in weekdays" :key="day" :value="day + 1">{{ weekday }}</option>
            </select>
            <select v-model="shift.startMinute" :aria-label="'第 ' + (index + 1) + ' 段的开始时间'">
              <option v-for="minute in minuteOptions.slice(0, -1)" :key="minute" :value="minute">{{ minuteLabel(minute) }}</option>
            </select>
            <select v-model="shift.endMinute" :aria-label="'第 ' + (index + 1) + ' 段的结束时间'">
              <option v-for="minute in minuteOptions.slice(1)" :key="minute" :value="minute">{{ minuteLabel(minute) }}</option>
            </select>
            <button type="button" class="text-button" :aria-label="'移除第 ' + (index + 1) + ' 段排班'" @click="edit.shifts.splice(index, 1)">移除</button>
          </div>
          <button type="button" class="button button-outline" :disabled="edit.shifts.length >= 56"
            @click="edit.shifts.push({ weekday: 6, startMinute: 540, endMinute: 1080 })">＋ 添加时段</button>
        </fieldset>
        <fieldset class="schedule-group">
          <legend>指定日期停约</legend>
          <p class="muted">停约只影响新订单，已经确认的预约会保留。</p>
          <div class="actions"><input v-model="dayToClose" type="date" aria-label="停约日期" />
            <button type="button" class="button button-outline" :disabled="!dayToClose || edit.closedDates.includes(dayToClose)" @click="closeDate">加入停约日期</button>
          </div>
          <ul v-if="edit.closedDates.length" class="date-tags">
            <li v-for="(date, index) in edit.closedDates" :key="date">{{ date }}
              <button type="button" class="text-button" :aria-label="'移除 ' + date" @click="edit.closedDates.splice(index, 1)">×</button>
            </li>
          </ul>
        </fieldset>
        <p v-if="formError" class="notice error" role="alert">{{ formError }}</p>
        <div class="actions"><button class="button button-dark" :disabled="saving || !services">保存员工与排班</button>
          <button type="button" class="button button-ghost" :disabled="saving" @click="edit = undefined">取消</button></div>
      </form>
    </section>
  </div>
</template>
