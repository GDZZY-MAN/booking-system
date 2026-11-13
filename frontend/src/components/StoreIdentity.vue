<script setup lang="ts">
import { ref, watch } from 'vue'
import type { ShopBrand } from '../branding'

const props = defineProps<{ name: string; description: string; brand: ShopBrand; preview?: boolean }>()
const brokenLogo = ref(false)
const brokenCover = ref(false)
watch(() => props.brand.logo, () => { brokenLogo.value = false })
watch(() => props.brand.cover, () => { brokenCover.value = false })
</script>

<template>
  <div class="store-identity">
    <img v-if="brand.cover && !brokenCover" class="store-cover" :src="brand.cover" alt="" @error="brokenCover = true" />
    <div class="store-identity-body">
      <img v-if="brand.logo && !brokenLogo" class="store-logo" :src="brand.logo" alt="" @error="brokenLogo = true" />
      <span v-else class="reserve-symbol" aria-hidden="true">✳</span>
      <p class="section-label">在线预约</p>
      <component :is="preview ? 'h3' : 'h1'" class="store-name">{{ name }}</component>
      <p v-if="description" class="preserve-lines">{{ description }}</p>
      <dl v-if="brand.address || brand.businessNote" class="store-details">
        <div v-if="brand.address"><dt>到店地址</dt><dd>{{ brand.address }}</dd></div>
        <div v-if="brand.businessNote"><dt>营业说明</dt><dd>{{ brand.businessNote }}</dd></div>
      </dl>
      <span class="timezone">◷ 所有时间均为北京时间</span>
    </div>
  </div>
</template>
