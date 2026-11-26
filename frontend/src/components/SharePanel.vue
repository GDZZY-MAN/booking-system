<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import QRCode from 'qrcode'
const props = defineProps<{ path: string; name: string; open: boolean }>()
const url = computed(() => new URL(props.path, location.origin).href)
const image = ref('')
const feedback = ref('')
const local = ['127.0.0.1', 'localhost', '[::1]'].includes(location.hostname)
watch([url, () => props.open], async ([value, open], _, cleanup) => {
  let active = true
  cleanup(() => { active = false })
  image.value = ''; feedback.value = ''
  if (!open) return
  try {
    const data = await QRCode.toDataURL(value, { width: 320, margin: 4 })
    if (active) image.value = data
  } catch { if (active) feedback.value = '二维码生成失败，可复制下方链接。' }
}, { immediate: true })
async function copy() {
  try { await navigator.clipboard.writeText(url.value); feedback.value = '预约链接已复制。' }
  catch { feedback.value = '请选中链接，手动复制。' }
}
</script>
<template>
  <section class="panel form-stack"><h2>{{ name }} · 分享与二维码</h2>
    <p v-if="!open" class="muted">开放预约后，即可分享预约页。</p>
    <template v-else>
      <p>扫描二维码或打开链接可直接进入{{ name }}的预约页。</p>
      <img v-if="image" class="share-qr" :src="image" :alt="name + '的预约二维码'" />
      <label>预约页链接<input :value="url" readonly @focus="($event.target as HTMLInputElement).select()" /></label>
      <p v-if="local" class="muted">当前为本机预览地址。请在正式网站下载供其他设备扫描的二维码。</p>
      <div class="actions"><button class="button" @click="copy">复制链接</button><a v-if="image" class="button" :href="image" :download="name + '-预约二维码.png'">下载二维码</a><RouterLink class="button" :to="path">打开预约页</RouterLink></div>
    </template>
    <p v-if="feedback" role="status">{{ feedback }}</p>
  </section>
</template>
