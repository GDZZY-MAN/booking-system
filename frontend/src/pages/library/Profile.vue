<script setup lang="ts">
import { ref } from 'vue'
import { api, message, type Shop } from '../../api'
import DiscoveryEditor from '../../components/DiscoveryEditor.vue'
const props = defineProps<{ root: string }>()
const emit = defineEmits<{ saved: [] }>()
const venue = ref<Shop>()
const error = ref('')
const notice = ref('')
const busy = ref(false)
async function load() {
  error.value = ''
  try { venue.value = await api<Shop>(props.root + '/profile') }
  catch (issue) { error.value = message(issue) }
}
async function save() {
  if (!venue.value) return
  busy.value = true
  error.value = ''; notice.value = ''
  try {
    venue.value = await api<Shop>(props.root + '/profile', 'PUT', { name: venue.value.name, description: venue.value.description })
    notice.value = '空间资料已保存。'
    emit('saved')
  } catch (issue) { error.value = message(issue) }
  finally { busy.value = false }
}
void load()
</script>
<template>
  <DiscoveryEditor area="library" :root="root" />
  <section class="panel">
    <h2>空间资料</h2>
    <p v-if="error" class="notice error" role="alert">{{ error }} <button type="button" class="text-button" @click="load">重新加载</button></p>
    <p v-if="notice" class="notice success" role="status">{{ notice }}</p>
    <form v-if="venue" class="form-stack" @submit.prevent="save">
      <label>空间名称<input v-model.trim="venue.name" required maxlength="80" /></label>
      <label>场馆介绍<textarea v-model.trim="venue.description" rows="4" maxlength="500"></textarea></label>
      <div><button class="button button-dark" :disabled="busy">保存空间资料</button></div>
    </form>
  </section>
</template>
