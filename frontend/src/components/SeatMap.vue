<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { placeFloorSeats, type Seat } from '../library'

const props = defineProps<{ seats: Seat[]; selected: number; disabled?: boolean; purpose?: 'contact' }>()
const emit = defineEmits<{ select: [id: number] }>()
const floor = ref(0)
const zoom = ref(1)
const floors = computed(() => [...new Set(props.seats.map(seat => seat.floor ?? 0))].sort((a, b) => a - b))
watch(floors, values => {
  if (!values.includes(floor.value)) floor.value = values[0] ?? 0
}, { immediate: true })
watch(() => props.selected, id => {
  const seat = props.seats.find(item => item.id === id)
  if (seat) floor.value = seat.floor ?? 0
}, { immediate: true })
watch(floor, value => {
  const seat = props.seats.find(item => item.id === props.selected)
  if (!seat || (seat.floor ?? 0) !== value) emit('select', 0)
  zoom.value = 1
})
const visible = computed(() => props.seats.filter(seat => (seat.floor ?? 0) === floor.value))
const placed = computed(() => placeFloorSeats(visible.value))
const columns = computed(() => Math.max(4, ...placed.value.map(item => item.column)))
const rows = computed(() => Math.max(3, ...placed.value.map(item => item.row)))
const selectedSeat = computed(() => props.seats.find(seat => seat.id === props.selected))
</script>

<template>
  <section class="seat-map" aria-label="二维座位平面图">
    <div class="map-toolbar">
      <div class="map-floors" role="group" aria-label="选择楼层">
        <button v-for="level in floors" :key="level" type="button" class="button"
          :class="floor === level ? 'button-dark' : 'button-outline'" :aria-pressed="floor === level"
          :disabled="disabled" @click="floor = level">{{ level ? level + 'F' : '未标楼层' }}</button>
      </div>
      <div class="actions map-zoom"><button type="button" class="button button-outline" :disabled="zoom <= 0.8" aria-label="缩小平面图" @click="zoom = Math.max(0.8, zoom - 0.2)">−</button>
        <button type="button" class="button button-outline" @click="zoom = 1">重置</button>
        <button type="button" class="button button-outline" :disabled="zoom >= 1.8" aria-label="放大平面图" @click="zoom = Math.min(1.8, zoom + 0.2)">＋</button></div>
    </div>
    <p class="map-note">座位按管理员录入的行列定位；未录入的位置自动排列。通道、入口及窗边装饰为示意。</p>
    <div class="map-legend"><span><i class="map-key available"></i>{{ purpose === 'contact' ? '可联系预约读者' : '可预约' }}</span><span><i class="map-key selected"></i>已选择</span><span><i class="map-key occupied"></i>{{ purpose === 'contact' ? '暂不可联系' : '已预约' }}</span></div>
    <div class="map-scroll" tabindex="0" aria-label="座位图，可横向滚动">
      <div class="map-building" :style="{ width: Math.max(540, columns * 92 * zoom + 100) + 'px' }">
        <div class="map-window">阅 读 空 间 · 示 意 平 面</div>
        <div class="map-room-label">{{ floor ? floor + 'F' : '座位区' }} · {{ visible.length }} 个座位</div>
        <div class="map-seat-layout" :style="{ gridTemplateColumns: 'repeat(' + columns + ', 1fr)', gridTemplateRows: 'repeat(' + rows + ', minmax(' + 82 * zoom + 'px, auto))' }">
          <button v-for="item in placed" :key="item.seat.id" type="button" class="map-seat"
            :class="{ chosen: selected === item.seat.id, occupied: !item.seat.available }"
            :style="{ gridRow: item.row, gridColumn: item.column }" :aria-pressed="selected === item.seat.id"
            :disabled="disabled || !item.seat.available"
            :aria-label="item.seat.label + '，' + item.seat.area + (purpose === 'contact' ? item.seat.available ? '，可联系' : '，暂不可联系' : item.seat.available ? '，可预约' : '，已预约')"
            @click="emit('select', item.seat.id)">
            <span v-if="!item.seat.available" class="map-seat-state">{{ purpose === 'contact' ? '暂不可联系' : '已预约' }}</span>
            <span class="map-chair" aria-hidden="true"></span><strong>{{ item.seat.label }}</strong>
            <small>{{ item.seat.powerOutlet ? '插座' : '' }}{{ item.seat.powerOutlet && item.seat.nearWindow ? ' · ' : '' }}{{ item.seat.nearWindow ? '靠窗' : '' }}</small>
          </button>
        </div>
        <div class="map-entrance">通道 · 入口示意</div>
      </div>
    </div>
    <p v-if="selectedSeat" class="notice success" role="status">已选 {{ selectedSeat.floor ? selectedSeat.floor + 'F · ' : '' }}{{ selectedSeat.area }} · {{ selectedSeat.label }}</p>
  </section>
</template>
