<template>
  <RouterLink class="brand" to="/" aria-label="有约首页">
    <svg class="brand-mark" viewBox="0 0 64 64" fill="none" aria-hidden="true" focusable="false">
      <rect class="brand-track" x="7" y="7" width="50" height="50" rx="16" />
      <rect class="brand-dash" x="7" y="7" width="50" height="50" rx="16" pathLength="360" />
      <circle class="brand-spin" cx="32" cy="32" r="14" pathLength="360" />
      <circle class="brand-center" cx="32" cy="32" r="3" />
    </svg>
    <span>有约<span class="brand-period">.</span></span>
  </RouterLink>
</template>

<style scoped>
/* Motion adapted from Uiverse.io by SelfMadeSystem. */
.brand-mark {
  display: block;
  flex: none;
  width: 36px;
  height: 36px;
  overflow: visible;
  stroke: var(--accent);
  stroke-width: 4;
  stroke-linecap: round;
  stroke-linejoin: round;
}
.brand-track { opacity: .12; }
.brand-dash {
  stroke-dasharray: 359 1 0 0;
  animation: brand-dash-array 2s ease-in-out infinite, brand-dash-offset 2s linear infinite;
}
.brand-spin {
  stroke: var(--accent-bright);
  stroke-dasharray: 270 90;
  transform-origin: 32px 32px;
  animation: brand-spin-array 2s ease-in-out infinite, brand-spin 8s ease-in-out infinite, brand-dash-offset 2s linear infinite;
}
.brand-center { fill: var(--accent); stroke: none; }

@keyframes brand-dash-array {
  0% { stroke-dasharray: 0 1 359 0; }
  50% { stroke-dasharray: 0 359 1 0; }
  100% { stroke-dasharray: 359 1 0 0; }
}
@keyframes brand-spin-array {
  0%, 100% { stroke-dasharray: 270 90; }
  50% { stroke-dasharray: 0 360; }
}
@keyframes brand-dash-offset {
  0% { stroke-dashoffset: 365; }
  100% { stroke-dashoffset: 5; }
}
@keyframes brand-spin {
  0% { rotate: 0deg; }
  12.5%, 25% { rotate: 270deg; }
  37.5%, 50% { rotate: 540deg; }
  62.5%, 75% { rotate: 810deg; }
  87.5%, 100% { rotate: 1080deg; }
}
@media (max-width: 650px) {
  .brand-mark { width: 30px; height: 30px; }
}
@media (prefers-reduced-motion: reduce) {
  .brand-dash, .brand-spin { animation: none; }
}
</style>
