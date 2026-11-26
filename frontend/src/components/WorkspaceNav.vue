<script setup lang="ts">
import { onMounted, onUnmounted, ref, useId, watch } from 'vue'
import { useRoute, type RouteLocationRaw } from 'vue-router'
defineProps<{ groups: { title: string; items: { label: string; to: RouteLocationRaw; active: boolean }[] }[] }>()
const route = useRoute()
const id = useId()
const expanded = ref(false)
const container = ref<HTMLElement>()
const toggle = ref<HTMLButtonElement>()
function close(focus = false) {
  expanded.value = false
  if (focus) toggle.value?.focus()
}
function outside(event: PointerEvent) {
  if (!container.value?.contains(event.target as Node)) close()
}
watch(() => route.fullPath, () => close())
onMounted(() => document.addEventListener('pointerdown', outside))
onUnmounted(() => document.removeEventListener('pointerdown', outside))
</script>
<template>
  <aside ref="container" class="workspace-navigation" @keydown.esc.stop.prevent="close(true)">
    <button ref="toggle" type="button" class="button workspace-menu-toggle" :aria-expanded="expanded" :aria-controls="id"
      @click="expanded = !expanded">管理菜单 <span aria-hidden="true">{{ expanded ? '⌃' : '⌄' }}</span></button>
    <nav :id="id" class="workspace-nav" :class="{ expanded }" aria-label="工作台功能">
      <section v-for="group in groups" :key="group.title" class="workspace-nav-group">
        <h2>{{ group.title }}</h2>
        <RouterLink v-for="item in group.items" :key="item.label" :to="item.to" :class="{ current: item.active }"
          :aria-current="item.active ? 'page' : undefined" @click="close()">{{ item.label }}</RouterLink>
      </section>
    </nav>
  </aside>
</template>
