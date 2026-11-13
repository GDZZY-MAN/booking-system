<script setup lang="ts">
import SiteHeader from '../components/SiteHeader.vue'
</script>
<template>
  <SiteHeader />
  <main id="main" class="narrow page-space panel">
    <h1>页面找不到了</h1>
    <p class="muted">请检查链接，或返回首页继续。</p>
    <RouterLink class="button button-dark" to="/">返回首页</RouterLink>
  </main>
</template>
