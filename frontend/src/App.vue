<script setup lang="ts">
import { useRoute } from 'vue-router'
const route = useRoute()
</script>

<template>
  <a class="skip-link" href="#main">跳到主要内容</a>
  <RouterView :key="route.path" />
</template>
