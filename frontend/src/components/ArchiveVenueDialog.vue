<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { api, ApiError, message } from '../api'
const props = defineProps<{ venue: { id: number; name: string; seatCount: number } }>()
const emit = defineEmits<{ close: []; archived: []; orders: []; hours: [] }>()
const dialog = ref<HTMLDialogElement>()
const count = ref<number>(), busy = ref(false), error = ref('')
const root = '/admin/venues/' + props.venue.id
async function check() {
  count.value = undefined; error.value = ''
  try { count.value = (await api<unknown[]>(root + '/reservations/unfinished')).length }
  catch (issue) { error.value = message(issue) }
}
async function remove() {
  if (busy.value || count.value !== 0) return
  busy.value = true; error.value = ''
  try { await api(root, 'DELETE'); emit('archived') }
  catch (issue) {
    if (issue instanceof ApiError && issue.code === 'VENUE_HAS_RESERVATIONS') await check()
    error.value = message(issue)
  } finally { busy.value = false }
}
onMounted(() => { dialog.value?.showModal(); void check() })
</script>
<template>
  <dialog ref="dialog" class="archive-dialog" aria-labelledby="archive-heading" @cancel.prevent="!busy && emit('close')">
    <h2 id="archive-heading">删除「{{ venue.name }}」？</h2>
    <p>{{ venue.seatCount }} 个座位及历史记录将随空间保留。移入回收站后，学生无法访问预约页。</p>
    <p v-if="error" class="notice error" role="alert">{{ error }}</p>
    <p v-if="count === undefined && !error" role="status">正在检查未结束预约…</p>
    <p v-else-if="count" class="notice">还有 {{ count }} 笔未结束预约。可以先关闭新预约，等现有预约结束，或查看并逐笔处理。</p>
    <div class="actions"><button class="button" :disabled="busy" autofocus @click="emit('close')">返回</button>
      <button v-if="count === 0" class="button button-danger" :disabled="busy" @click="remove">移入回收站</button>
      <template v-else-if="count"><button class="button button-dark" @click="emit('orders')">查看未结束预约</button>
        <button class="button" @click="emit('hours')">设置开放时间</button></template>
      <button v-else-if="error" class="button" @click="check">重新检查</button>
    </div>
  </dialog>
</template>
<style scoped>
.archive-dialog { width:min(490px,calc(100% - 32px)); max-height:85vh; overflow:auto; background:var(--white,white); color:var(--ink); padding:26px; border:1px solid var(--line); border-radius:18px; }
.archive-dialog { margin:auto; }
.archive-dialog::backdrop { background:#14293d80; }
</style>
