<script setup lang="ts">
import { useRoute } from 'vue-router'
import { computed, watch } from 'vue'
import { refreshSession } from './session'
import ReaderInboxLink from './components/ReaderInboxLink.vue'
import BackgroundShell from './components/BackgroundShell.vue'
const route = useRoute()
const background = computed(() => route.meta.background ?? 'detail')
watch(() => route.path, () => { void refreshSession() }, { immediate: true })
</script>

<template>
  <BackgroundShell :mode="background" />
  <a class="skip-link" href="#main">跳到主要内容</a>
  <RouterView :key="route.path" />
  <ReaderInboxLink />
</template>
