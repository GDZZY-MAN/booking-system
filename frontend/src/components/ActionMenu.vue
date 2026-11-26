<script setup lang="ts">
import { onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
defineProps<{ label: string }>()
const route = useRoute()
const menu = ref<HTMLDetailsElement>()
function close(focus = false) {
  if (!menu.value?.open) return
  menu.value.open = false
  if (focus) menu.value.querySelector('summary')?.focus()
}
function outside(event: PointerEvent) {
  if (!menu.value?.contains(event.target as Node)) close()
}
watch(() => route.fullPath, () => close())
onMounted(() => document.addEventListener('pointerdown', outside))
onUnmounted(() => document.removeEventListener('pointerdown', outside))
</script>
<template>
  <details ref="menu" class="action-menu" @keydown.esc.stop.prevent="close(true)">
    <summary class="button button-small"><span>{{ label }}</span> <span aria-hidden="true">⌄</span></summary>
    <div class="action-menu-content" @click="close()"><slot /></div>
  </details>
</template>
