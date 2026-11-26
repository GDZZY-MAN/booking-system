<script setup lang="ts">
import { computed, ref } from 'vue'
import { today } from '../api'

const props = defineProps<{ modelValue: string[] }>()
const emit = defineEmits<{ 'update:modelValue': [dates: string[]] }>()
const month = ref(today().slice(0, 7))
const from = ref('')
const through = ref('')
const error = ref('')
const weekdays = ['一', '二', '三', '四', '五', '六', '日']
const stamp = (value: string) => Date.parse(value + 'T00:00:00Z')
const iso = (value: number) => new Date(value).toISOString().slice(0, 10)
const selected = computed(() => new Set(props.modelValue))
const cells = computed(() => {
  const first = stamp(month.value + '-01')
  const offset = (new Date(first).getUTCDay() + 6) % 7
  return Array.from({ length: 42 }, (_, index) => iso(first + (index - offset) * 86400000))
})
function move(offset: number) {
  const date = new Date(stamp(month.value + '-01'))
  date.setUTCMonth(date.getUTCMonth() + offset)
  month.value = date.toISOString().slice(0, 7)
}
function update(dates: string[]) {
  const unique = [...new Set(dates)].sort()
  if (unique.length > 366) { error.value = '最多选择 366 个停约日期。'; return }
  error.value = ''
  emit('update:modelValue', unique)
}
function toggle(date: string) {
  update(selected.value.has(date) ? props.modelValue.filter(value => value !== date) : [...props.modelValue, date])
}
function addRange() {
  if (!from.value || !through.value || through.value < from.value) {
    error.value = '请选择完整日期范围，结束日期不能早于开始日期。'; return
  }
  const count = Math.round((stamp(through.value) - stamp(from.value)) / 86400000) + 1
  if (!Number.isFinite(count) || count > 366) { error.value = '连续停约范围最多 366 天。'; return }
  update([...props.modelValue, ...Array.from({ length: count }, (_, index) => iso(stamp(from.value) + index * 86400000))])
}
</script>

<template>
  <div class="date-picker">
    <p class="muted">点击日历可多选不连续日期；连续休假也可按日期范围添加。已确认的预约会保留。</p>
    <div class="date-calendar">
      <div class="panel-heading">
        <button type="button" class="text-button" aria-label="上个月" @click="move(-1)">←</button>
        <strong aria-live="polite">{{ month.replace('-', ' 年 ') }} 月</strong>
        <button type="button" class="text-button" aria-label="下个月" @click="move(1)">→</button>
      </div>
      <div class="calendar-grid" role="group" aria-label="多选停约日期">
        <span v-for="day in weekdays" :key="day" class="calendar-weekday">{{ day }}</span>
        <button v-for="date in cells" :key="date" type="button" class="calendar-day"
          :class="{ selected: selected.has(date), outside: !date.startsWith(month) }"
          :aria-label="date" :aria-pressed="selected.has(date)" :aria-current="date === today() ? 'date' : undefined"
          @click="toggle(date)">{{ Number(date.slice(-2)) }}</button>
      </div>
    </div>
    <div class="date-range">
      <label>停约开始日期<input v-model="from" type="date" /></label>
      <label>停约结束日期<input v-model="through" type="date" /></label>
      <button type="button" class="button button-outline" :disabled="!from || !through" @click="addRange">添加整段日期</button>
    </div>
    <p v-if="error" class="notice error" role="alert">{{ error }}</p>
    <p role="status">已选 {{ modelValue.length }} 天 <button v-if="modelValue.length" type="button" class="text-button" @click="update([])">清空日期</button></p>
    <ul v-if="modelValue.length" class="date-tags selected-dates">
      <li v-for="date in modelValue" :key="date">{{ date }}
        <button type="button" class="text-button" :aria-label="'移除 ' + date" @click="toggle(date)">×</button>
      </li>
    </ul>
  </div>
</template>
