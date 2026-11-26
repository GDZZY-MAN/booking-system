<script setup lang="ts">
import { ref } from 'vue'
import { api, message } from '../api'
const props = defineProps<{ root: string }>()
const cover = ref<string>(), busy = ref(false), error = ref(''), notice = ref('')
async function load() {
  error.value = ''
  try { cover.value = (await api<{ cover: string }>(props.root + '/cover')).cover }
  catch (issue) { error.value = message(issue) }
}
async function choose(event: Event) {
  const file = (event.target as HTMLInputElement).files?.[0]
  if (!file) return
  notice.value = ''; error.value = ''
  if (file.size > 400 * 1024 || !['image/png','image/jpeg'].includes(file.type)) {
    error.value = '请选择 400 KB 以内的 JPG 或 PNG 图片。'; return
  }
  busy.value = true
  const reader = new FileReader()
  reader.onload = () => { cover.value = String(reader.result); busy.value = false }
  reader.onerror = () => { error.value = '图片读取失败。'; busy.value = false }
  reader.readAsDataURL(file)
}
async function save() {
  busy.value = true; error.value = ''; notice.value = ''
  try { await api(props.root + '/cover', 'PUT', { cover: cover.value }); notice.value = '封面已保存。' }
  catch (issue) { error.value = message(issue) }
  finally { busy.value = false }
}
void load()
</script>
<template>
  <section class="panel form-stack"><h2>场馆封面</h2>
    <template v-if="cover !== undefined"><img class="venue-cover" :src="cover || '/space-default.svg'" alt="当前封面预览" />
      <label>选择封面（JPG 或 PNG，最多 400 KB）<input type="file" accept="image/jpeg,image/png" :disabled="busy" @change="choose" /></label>
      <div class="actions"><button class="button button-dark" :disabled="busy" @click="save">保存封面</button><button v-if="cover" class="button" :disabled="busy" @click="cover = ''; notice = ''">使用默认插画</button></div>
    </template>
    <p v-if="error" class="notice error" role="alert">{{ error }} <button v-if="cover === undefined" class="text-button" @click="load">重试</button></p>
    <p v-if="notice" role="status">{{ notice }}</p>
  </section>
</template>
