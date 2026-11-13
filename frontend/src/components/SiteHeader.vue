<script setup lang="ts">
import Brand from './Brand.vue'
defineProps<{ showHome?: boolean }>()
</script>
<template>
  <header class="site-header" :class="{ 'site-header-with-home': showHome }">
    <div class="site-header-inner">
      <Brand />
      <nav aria-label="平台导航">
        <RouterLink v-if="showHome" class="button button-ghost" to="/">返回首页</RouterLink>
        <RouterLink class="button button-ghost" to="/login">商家 / 员工登录</RouterLink>
        <RouterLink class="button button-dark header-cta" to="/register">免费开通 <span aria-hidden="true">↗</span></RouterLink>
      </nav>
    </div>
  </header>
</template>
