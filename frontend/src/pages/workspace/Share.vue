<script setup lang="ts">
import { ref } from 'vue'
import SharePanel from '../../components/SharePanel.vue'
import { api, message, type Shop } from '../../api'
const shop = ref<Shop>(), error = ref('')
async function load() {
  error.value = ''
  try { shop.value = await api<Shop>('/owner/shop') } catch (issue) { error.value = message(issue) }
}
void load()
</script>
<template><SharePanel v-if="shop" :path="'/s/' + shop.slug" :name="shop.name" :open="shop.published" />
  <p v-else-if="error" class="notice error">{{ error }} <button @click="load">重试</button></p>
</template>
