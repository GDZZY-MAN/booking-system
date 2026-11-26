<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'

const props = defineProps<{ mode: 'home' | 'public' | 'detail' | 'workspace' }>()
const canvas = ref<HTMLCanvasElement>()
const animationAllowed = ref(false), ready = ref(false)
const animated = computed(() => animationAllowed.value && (props.mode === 'home' || props.mode === 'public'))
let stop: (() => void) | undefined
let generation = 0
let detachPreferences: (() => void) | undefined

function contextLost() {
  ready.value = false
  stop?.()
  stop = undefined
}

watch([canvas, () => props.mode, animated], async ([target, mode, enabled]) => {
  const token = ++generation
  stop?.()
  stop = undefined
  ready.value = false
  if (!target || !enabled || (mode !== 'home' && mode !== 'public')) return
  try {
    const { startCloudRenderer } = await import('../lib/cloudRenderer')
    if (token !== generation) return
    stop = startCloudRenderer(target, mode)
    ready.value = Boolean(stop)
  } catch {
    // Static clouds remain visible when the optional renderer cannot be loaded.
  }
}, { flush: 'post' })

onMounted(() => {
  const reduceMotion = window.matchMedia('(prefers-reduced-motion: reduce)')
  const compact = window.matchMedia('(max-width: 760px), (pointer: coarse)')
  const connection = (navigator as Navigator & { connection?: { saveData?: boolean } }).connection
  const update = () => {
    animationAllowed.value = !document.hidden && !reduceMotion.matches && !compact.matches && !connection?.saveData
  }
  reduceMotion.addEventListener('change', update)
  compact.addEventListener('change', update)
  document.addEventListener('visibilitychange', update)
  detachPreferences = () => {
    reduceMotion.removeEventListener('change', update)
    compact.removeEventListener('change', update)
    document.removeEventListener('visibilitychange', update)
  }
  update()
})
onUnmounted(() => { generation++; stop?.(); detachPreferences?.() })
</script>

<template>
  <div class="app-background" :class="'background-' + mode" aria-hidden="true">
    <div class="background-sky">
      <canvas v-if="animated" ref="canvas" :class="{ ready }" @webglcontextlost="contextLost"></canvas>
    </div>
    <div class="background-shade"></div>
  </div>
</template>

<style scoped>
.app-background {
  position: fixed;
  inset: 0 0 auto;
  height: 100vh;
  height: 100lvh;
  z-index: -1;
  overflow: hidden;
  pointer-events: none;
  background: var(--paper);
}
.background-sky, .background-shade { position: absolute; inset: 0; }
.background-sky {
  background:
    radial-gradient(ellipse 30% 13% at 82% 28%, #fcfdfff0 28%, transparent 100%),
    radial-gradient(ellipse 22% 12% at 97% 21%, #fcfdffcc 25%, transparent 100%),
    radial-gradient(ellipse 35% 17% at 90% 78%, #fcfdffeb 30%, transparent 100%),
    radial-gradient(ellipse 32% 13% at 16% 90%, #fcfdffbf 20%, transparent 100%),
    linear-gradient(#528dca, #b2d5f0);
}
canvas { display: block; width: 100%; height: 100%; opacity: 0; }
canvas.ready { opacity: 1; }
.background-shade { background: linear-gradient(90deg, #edf3faf5, #edf3fad9 40%, #edf3fa40); }
.background-public .background-sky { opacity: .32; }
.background-public .background-shade {
  background: linear-gradient(90deg, #edf3facc, #edf3fa99 40%, #edf3fa22);
}
.background-detail .background-sky { opacity: .24; }
.background-detail .background-shade {
  background: radial-gradient(ellipse at 50% 40%, #edf3fa 18%, #edf3fa80 70%, transparent);
}
.background-workspace {
  background: radial-gradient(ellipse at 100% 0%, #cddff380, transparent 55%),
    radial-gradient(ellipse at 0% 100%, #dcebf880, transparent 50%), var(--paper);
}
.background-workspace .background-sky, .background-workspace .background-shade { display: none; }
@media (max-width: 760px) {
  .background-shade { background: linear-gradient(180deg, #edf3faf5, #edf3fadb 45%, #edf3fa66); }
  .background-public .background-sky { opacity: .28; }
}
</style>
