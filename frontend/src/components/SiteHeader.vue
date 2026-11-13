<script setup lang="ts">
import Brand from './Brand.vue'
defineProps<{ booking?: boolean }>()
</script>
<template>
  <header class="site-header">
    <div class="site-header-inner">
      <Brand />
      <nav v-if="!booking" aria-label="账号">
        <RouterLink class="button button-ghost" to="/login">商家 / 员工登录</RouterLink>
        <RouterLink class="button button-dark header-cta" to="/register">免费开通 <span aria-hidden="true">↗</span></RouterLink>
      </nav>
    </div>
  </header>
</template>
