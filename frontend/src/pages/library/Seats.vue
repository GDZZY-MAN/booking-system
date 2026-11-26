<script setup lang="ts">
import { nextTick, ref } from 'vue'
import BatchSeatPlanner from '../../components/BatchSeatPlanner.vue'
import { api, dayOf, message, timeRange, today } from '../../api'
import { minutesLabel, reservationStatus, type LibrarySettings, type Seat, type SeatReservation } from '../../library'

const settings = ref<LibrarySettings>({ published: false, openMinute: 480, closeMinute: 1320, slotMinutes: 120 })
const seats = ref<Seat[]>([])
const orders = ref<SeatReservation[]>([])
const edit = ref<Seat>()
const date = ref(today())
const busy = ref(false)
const loading = ref(false)
const settingsLoaded = ref(false)
const error = ref('')
const ordersError = ref('')
const notice = ref('')
const times = Array.from({ length: 49 }, (_, index) => index * 30)
const props = defineProps<{ root: string; section: 'hours' | 'seats' | 'orders'; unfinished?: boolean }>()
const unfinished = ref(!!props.unfinished)
const planning = ref(false)
const seatForm = ref<HTMLFormElement>()
let orderRequest = 0
const emit = defineEmits<{ saved: [] }>()
const root = props.root

async function load() {
  error.value = ''
  loading.value = true
  try {
    if (props.section === 'hours') settings.value = await api<LibrarySettings>(root)
    if (props.section === 'seats') seats.value = await api<Seat[]>(root + '/seats')
    settingsLoaded.value = true
  } catch (issue) { error.value = message(issue) }
  finally { loading.value = false }
}
async function loadOrders() {
  ordersError.value = ''
  const current = ++orderRequest
  orders.value = []
  try {
    const result = await api<SeatReservation[]>(root + (unfinished.value ? '/reservations/unfinished' : '/reservations?date=' + date.value))
    if (current === orderRequest) orders.value = result
  } catch (issue) { if (current === orderRequest) ordersError.value = message(issue) }
}
async function saveSettings() {
  busy.value = true
  error.value = ''
  notice.value = ''
  try {
    settings.value = await api<LibrarySettings>(root, 'PUT', settings.value)
    notice.value = '开放时间与预约状态已保存。'
    emit('saved')
  } catch (issue) { error.value = message(issue) }
  finally { busy.value = false }
}
async function editSeat(seat?: Seat) {
  edit.value = seat ? { ...seat } : { id: 0, label: '', area: '', nearWindow: false, powerOutlet: false, active: true,
    floor: null, mapRow: null, mapColumn: null }
  error.value = ''
  await nextTick()
  seatForm.value?.querySelector('input')?.focus()
}
function updateBatch(rows: Seat[]) {
  seats.value = rows
  emit('saved')
}
async function saveSeat() {
  if (!edit.value) return
  busy.value = true
  error.value = ''
  notice.value = ''
  try {
    const { id, ...input } = edit.value
    input.floor = input.floor || null
    input.mapRow = input.mapRow || null
    input.mapColumn = input.mapColumn || null
    await api(root + '/seats' + (id ? '/' + id : ''), id ? 'PUT' : 'POST', input)
    seats.value = await api<Seat[]>(root + '/seats')
    edit.value = undefined
    notice.value = '座位已保存。'
    emit('saved')
  } catch (issue) { error.value = message(issue) }
  finally { busy.value = false }
}
async function action(row: SeatReservation, operation: 'cancel') {
  busy.value = true
  ordersError.value = ''
  notice.value = ''
  try {
    await api(root + '/reservations/' + row.id + '/' + operation, 'POST')
    await loadOrders()
    notice.value = '座位预约已取消。'
  } catch (issue) { await loadOrders(); ordersError.value = message(issue) }
  finally { busy.value = false }
}
if (props.section === 'orders') void loadOrders()
else void load()
</script>

<template>
  <div class="workspace-stack">
      <p v-if="loading" role="status">正在加载场所配置…</p>
      <p v-if="error" class="notice error" role="alert">{{ error }}</p>
      <p v-if="notice" class="notice success" role="status">{{ notice }}</p>
      <button v-if="section !== 'orders' && !settingsLoaded && !loading" class="button button-outline" @click="load">重新加载</button>
    <section v-if="section === 'hours'" class="panel"><h2>开放时间</h2>
      <form v-if="settingsLoaded" class="form-stack" @submit.prevent="saveSettings">
        <fieldset class="form-stack" :disabled="busy">
          <div class="order-filters">
            <label>每日开始时间<select v-model="settings.openMinute"><option v-for="time in times.slice(0, -1)" :key="time" :value="time">{{ minutesLabel(time) }}</option></select></label>
            <label>每日结束时间<select v-model="settings.closeMinute"><option v-for="time in times.slice(1)" :key="time" :value="time">{{ minutesLabel(time) }}</option></select></label>
          </div>
          <label>每个预约时段（分钟）<input v-model.number="settings.slotMinutes" type="number" min="30" max="240" step="30" required /></label>
          <label class="check"><input v-model="settings.published" type="checkbox" /> 开放读者预约</label>
          <small class="muted">至少添加一个可用座位后即可开放。关闭后停止新预约，已有预约继续有效。</small>
          <div><button class="button button-dark">保存开放设置</button></div>
        </fieldset>
      </form>
    </section>
    <section v-if="section === 'seats' && settingsLoaded" class="panel">
      <div class="panel-heading"><h2>座位和区域</h2><div class="actions">
        <button class="button button-dark" :disabled="busy" :aria-expanded="planning" @click="planning = !planning; edit = undefined">{{ planning ? '收起批量排座' : '批量排座' }}</button>
        <button class="button" :disabled="busy" @click="editSeat()">添加单座</button></div></div>
      <BatchSeatPlanner v-if="planning" :root="root" :seats="seats" :disabled="busy" @working="busy = $event" @updated="updateBatch" @edit="editSeat" />
      <p v-if="!seats.length && !planning" class="muted">用「批量排座」在楼层图上添加一组座位。</p>
      <details v-if="seats.length" :open="!planning"><summary>已有座位（{{ seats.length }}）</summary>
      <ul class="data-list"><li v-for="seat in seats" :key="seat.id">
        <div><strong>{{ seat.area }} · {{ seat.label }}</strong><small>{{ seat.nearWindow ? '靠窗 · ' : '' }}{{ seat.powerOutlet ? '有插座 · ' : '' }}{{ seat.active ? '开放' : '停用' }}</small></div>
        <button class="button button-outline" :disabled="busy" @click="editSeat(seat)">编辑</button>
      </li></ul></details>
      <form v-if="edit" ref="seatForm" class="form-stack seat-edit" @submit.prevent="saveSeat">
        <fieldset class="form-stack" :disabled="busy">
          <label>座位编号<input v-model.trim="edit.label" maxlength="30" required placeholder="例如 A-01" /></label>
          <label>楼层 / 区域<input v-model.trim="edit.area" maxlength="50" required placeholder="例如 二楼靠窗区" /></label>
          <div class="seat-position-fields">
            <label>楼层<input v-model.number="edit.floor" type="number" min="1" max="20" placeholder="可留空" /></label>
            <label>平面图行<input v-model.number="edit.mapRow" type="number" min="1" max="20" placeholder="1–20" /></label>
            <label>平面图列<input v-model.number="edit.mapColumn" type="number" min="1" max="12" placeholder="1–12" /></label>
          </div>
          <small class="muted">行、列同时留空时自动排布；填写后固定在对应位置，同楼层位置不可重复。</small>
          <div class="checks"><label class="check"><input v-model="edit.nearWindow" type="checkbox" /> 靠窗</label>
            <label class="check"><input v-model="edit.powerOutlet" type="checkbox" /> 有插座</label>
            <label class="check"><input v-model="edit.active" type="checkbox" /> 开放预约</label></div>
          <div class="actions"><button class="button button-dark">保存座位</button>
            <button type="button" class="button button-ghost" @click="edit = undefined">关闭编辑</button></div>
        </fieldset>
      </form>
    </section>
    <section v-if="section === 'orders'" class="panel">
      <h2>座位预约记录</h2>
      <p class="muted">预约按时段自动生效与结束，读者无需签到。管理员可查看记录或取消有效预约。</p>
      <form class="order-filters" @submit.prevent="loadOrders">
        <label>记录范围<select v-model="unfinished" @change="loadOrders"><option :value="false">按日期</option><option :value="true">所有未结束预约</option></select></label>
        <label v-if="!unfinished">查看日期<input v-model="date" type="date" required /></label>
        <button class="button button-outline" :disabled="busy">刷新预约</button></form>
      <p v-if="ordersError" class="notice error" role="alert">{{ ordersError }}</p>
      <p v-if="!orders.length && !ordersError" class="muted">{{ unfinished ? '当前没有未结束的预约。' : '当天暂无座位预约。' }}</p>
      <ul class="data-list order-list"><li v-for="row in orders" :key="row.id">
        <div><strong>{{ row.area }} · {{ row.seatLabel }}</strong><small>{{ dayOf(row.startsAt) }} · {{ timeRange(row) }} · 读者 {{ row.username }}</small></div>
        <span class="status">{{ reservationStatus[row.status] }}</span>
        <button v-if="['RESERVED', 'ACTIVE'].includes(row.status)" class="button button-outline" :disabled="busy" @click="action(row, 'cancel')">取消座位预约</button>
      </li></ul>
    </section>
  </div>
</template>
