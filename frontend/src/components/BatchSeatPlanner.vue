<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { ApiError, api, message } from '../api'
import { placeFloorSeats, type Seat } from '../library'

const props = defineProps<{ root: string; seats: Seat[]; disabled: boolean }>()
const emit = defineEmits<{ updated: [rows: Seat[]]; edit: [seat: Seat]; working: [value: boolean] }>()
const floor = ref(1), area = ref(''), prefix = ref('1F-')
const rowCount = ref<number | ''>(4), colCount = ref<number | ''>(6)
const manualStart = ref<number | ''>('')
const tools = { place: '放置起点', aisle: '留作通道', window: '标记靠窗', power: '标记插座', edit: '编辑已有座位' } as const
const tool = ref<keyof typeof tools>('place')
type Cell = { row: number; col: number }
type Flags = { aisle?: boolean; window?: boolean; power?: boolean }
const startCell = ref<Cell>()
const flags = ref(new Map<string, Flags>())
const busy = ref(false), error = ref(''), notice = ref('')
const cellKey = (r: number, c: number) => r + '/' + c
const existing = computed(() => new Map(placeFloorSeats(props.seats.filter(s => s.floor === floor.value))
  .map(({ seat, row, column }) => [cellKey(row, column), seat])))
const labels = computed(() => new Set(props.seats.map(s => s.label)))
const nextNumber = computed(() => {
  let max = 0
  for (const label of labels.value) {
    if (!label.startsWith(prefix.value)) continue
    const suffix = label.slice(prefix.value.length)
    if (/^\d+$/.test(suffix)) max = Math.max(max, Number(suffix))
  }
  return max + 1
})
const first = computed(() => manualStart.value === '' ? nextNumber.value : manualStart.value)
const validCount = (n: number | '', limit: number) => typeof n === 'number' && Number.isInteger(n) && n > 0 && n <= limit
const validSize = computed(() => validCount(rowCount.value, 20) && validCount(colCount.value, 12))
const validFirst = computed(() => Number.isSafeInteger(first.value) && first.value >= 1 && Number.isSafeInteger(first.value + 239))
interface DraftCell extends Cell, Flags { label: string; conflict: string }
const preview = computed(() => {
  const cells = new Map<string, DraftCell>()
  if (!startCell.value || !validSize.value) return cells
  let number = first.value
  for (let r = 0; r < Number(rowCount.value); r++) {
    for (let c = 0; c < Number(colCount.value); c++) {
      const mark = flags.value.get(cellKey(r, c)) ?? {}
      const row = startCell.value.row + r, col = startCell.value.col + c
      const key = cellKey(row, col)
      const label = mark.aisle ? '' : prefix.value + String(number++).padStart(2, '0')
      let conflict = ''
      if (row > 20 || col > 12) conflict = '超出楼层边界'
      else if (!mark.aisle && existing.value.has(key)) conflict = '位置已用'
      else if (!mark.aisle && labels.value.has(label)) conflict = '座号重复'
      else if (label.length > 30) conflict = '座号过长'
      cells.set(key, { ...mark, row, col, label, conflict })
    }
  }
  return cells
})
const seatsToAdd = computed(() => [...preview.value.values()].filter(cell => !cell.aisle))
const conflicts = computed(() => [...preview.value.values()].filter(cell => cell.conflict))
const boardRows = computed(() => Math.max(20, ...[...preview.value.values()].map(cell => cell.row)))
const boardCols = computed(() => Math.max(12, ...[...preview.value.values()].map(cell => cell.col)))
const board = computed(() => Array.from({ length: boardRows.value * boardCols.value }, (_, i) => {
  const row = Math.floor(i / boardCols.value) + 1, col = i % boardCols.value + 1
  const key = cellKey(row, col)
  return { key, row, col, seat: existing.value.get(key), draft: preview.value.get(key) }
}))
function clearLayout() {
  startCell.value = undefined
  flags.value.clear()
  tool.value = 'place'
}
watch(floor, value => { prefix.value = value + 'F-'; clearLayout(); error.value = ''; notice.value = '' })
watch(prefix, () => { manualStart.value = '' })
function clickCell(row: number, col: number) {
  const key = cellKey(row, col)
  const saved = existing.value.get(key)
  error.value = ''; notice.value = ''
  if (tool.value === 'edit') {
    if (saved) emit('edit', saved)
    return
  }
  if (tool.value === 'place') {
    if (row > 20 || col > 12) return
    if (manualStart.value === '') manualStart.value = nextNumber.value
    startCell.value = { row, col }
    tool.value = 'aisle'
    return
  }
  if (!startCell.value || !preview.value.has(key)) return
  const rel = cellKey(row - startCell.value.row, col - startCell.value.col)
  const mark = flags.value.get(rel) ?? {}
  if (tool.value !== 'aisle' && mark.aisle) return
  flags.value.set(rel, { ...mark, [tool.value]: !mark[tool.value] })
}
const canSave = computed(() => Boolean(area.value.trim()) && validFirst.value && seatsToAdd.value.length > 0 && !conflicts.value.length)
async function save() {
  if (busy.value || !canSave.value) return
  busy.value = true; error.value = ''; notice.value = ''
  emit('working', true)
  const input = seatsToAdd.value.map(cell => ({
    label: cell.label, area: area.value.trim(), floor: floor.value, mapRow: cell.row, mapColumn: cell.col,
    nearWindow: !!cell.window, powerOutlet: !!cell.power, active: true,
  }))
  try {
    emit('updated', await api<Seat[]>(props.root + '/seats/batch', 'POST', input))
    clearLayout(); manualStart.value = ''
    notice.value = '已添加 ' + input.length + ' 个座位。可继续选择下一组的起点。'
  } catch (issue) {
    error.value = message(issue)
    if (issue instanceof ApiError && issue.status === 409) {
      try { emit('updated', await api<Seat[]>(props.root + '/seats')) }
      catch { error.value += ' 请刷新座位列表。' }
    }
  } finally { busy.value = false; emit('working', false) }
}
</script>

<template>
  <form class="batch-planner form-stack" @submit.prevent="save">
    <fieldset class="form-stack" :disabled="busy || props.disabled">
      <div class="batch-inputs">
        <label>楼层<select v-model="floor" aria-label="楼层"><option v-for="n in 20" :key="n" :value="n">{{ n }} 楼</option></select></label>
        <label>区域名称<input v-model.trim="area" maxlength="50" required placeholder="例如 东区" /></label>
        <label>排数<input v-model.number="rowCount" type="number" min="1" max="20" required /></label>
        <label>每排座数<input v-model.number="colCount" type="number" min="1" max="12" required /></label>
        <label>座号前缀<input v-model.trim="prefix" maxlength="28" /></label>
        <label>起始编号<input v-model.number="manualStart" type="number" min="1" :placeholder="String(nextNumber)" /></label>
      </div>
      <p class="muted">点击楼层图上的起点放置座位，再点格子留出通道或标记设施。再次点击可撤销标记。</p>
      <div class="actions planner-tools" role="group" aria-label="平面图工具">
        <button v-for="(label, key) in tools" :key="key" type="button" class="button button-small" :aria-pressed="tool === key"
          :disabled="!startCell && key !== 'place' && key !== 'edit'" @click="tool = key">{{ label }}</button>
      </div>
      <div class="planner-scroll" tabindex="0" :aria-label="floor + ' 楼排座图，可上下左右滚动'">
        <div class="planner-grid" :style="{gridTemplateColumns: 'repeat(' + boardCols + ', 74px)'}">
          <button v-for="cell in board" :key="cell.key" type="button" class="planner-cell"
            :class="{ saved: cell.seat, planned: cell.draft && !cell.draft.aisle, aisle: cell.draft?.aisle, conflict: cell.draft?.conflict }"
            :aria-label="cell.row + ' 排 ' + cell.col + ' 列 ' + [cell.seat?.label, cell.draft?.label, cell.draft?.conflict].filter(Boolean).join(' ')"
            @click="clickCell(cell.row, cell.col)">
            <small>{{ cell.row }} · {{ cell.col }}</small>
            <strong>{{ cell.draft?.label || cell.seat?.label || (cell.draft?.aisle ? '通道' : '＋') }}</strong>
            <span v-if="cell.draft?.conflict">{{ cell.draft.conflict }}</span>
            <span v-else-if="cell.seat">已有座位</span>
            <span v-else-if="cell.draft && !cell.draft.aisle">{{ [cell.draft.window && '靠窗', cell.draft.power && '插座'].filter(Boolean).join(' · ') }}</span>
          </button>
        </div>
      </div>
      <p v-if="!validFirst || !validSize" class="notice error">请检查起始编号和排座尺寸（最多 20 排、每排 12 座）。</p>
      <p v-if="conflicts.length" class="notice error" role="alert">有 {{ conflicts.length }} 个格子需要调整。第 {{ conflicts[0].row }} 排第 {{ conflicts[0].col }} 列：{{ conflicts[0].conflict }}。</p>
      <div class="actions"><button class="button button-dark" :disabled="!canSave">保存这 {{ seatsToAdd.length }} 个座位</button>
        <button v-if="startCell" type="button" class="button button-small" @click="clearLayout">清空预览</button></div>
    </fieldset>
    <p v-if="error" class="notice error" role="alert">{{ error }}</p>
    <p v-if="notice" class="notice success" role="status">{{ notice }}</p>
  </form>
</template>

<style scoped>
.batch-planner { margin-block: 20px; min-width: 0; }
.batch-inputs { display:grid; grid-template-columns:repeat(auto-fit,minmax(min(135px,100%),1fr)); gap:12px; }
.batch-planner fieldset { min-width:0; }
.planner-tools { margin:0; }
.planner-tools [aria-pressed="true"] { color:white; background:var(--accent); }
.planner-scroll { overflow:auto; max-width:100%; max-height:390px; border:1px solid var(--line); border-radius:12px; }
.planner-grid { display:grid; width:max-content; gap:4px; padding:8px; }
.planner-cell { display:grid; align-content:center; gap:1px; height:64px; border:1px dashed var(--line); border-radius:6px; background:white; color:var(--muted); cursor:pointer; }
.planner-cell strong { overflow:hidden; text-overflow:ellipsis; white-space:nowrap; }
.planner-cell small,.planner-cell span { font-size:10px; }
.planner-cell.saved { border-style:solid; background:#e6ebf1; color:#3a4655; }
.planner-cell.planned { border:1px solid #37836a; background:#e0f5ec; color:#1d563f; }
.planner-cell.aisle { background:#f3ede1; color:#695638; }
.planner-cell.conflict { background:#ffe9e6; color:#8a281c; border:2px solid #ad4333; }
</style>
