<script setup lang="ts">
import { useRoute, useRouter } from 'vue-router'
const route = useRoute()
const router = useRouter()
function goBack() {
  const previous = router.options.history.state.back
  if (typeof previous === 'string' && previous.startsWith('/') && !previous.startsWith('//')) {
    router.back()
  } else {
    void router.replace('/')
  }
}
</script>
<template>
  <button v-if="route.path !== '/'" type="button" class="button button-small back-button" @click="goBack">
    <span aria-hidden="true">‹</span>返回上一页
  </button>
</template>
