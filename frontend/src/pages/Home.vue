<script setup lang="ts">
import SiteHeader from '../components/SiteHeader.vue'
</script>
<template>
  <div class="landing-page campus-home">
    <SiteHeader />
    <main id="main" class="content-width">
      <section class="campus-welcome">
        <p class="campus-eyebrow">有约 · 校园预约</p>
        <h1>把时间留给<br /><em>想做的事。</em></h1>
        <p>一处安心学习的空间，一次提前约好的服务，一天心中有数的安排。</p>
      </section>
      <section class="campus-portals" aria-label="选择预约入口">
        <RouterLink to="/libraries" class="campus-portal portal-space">
          <div class="portal-surface">
            <div class="portal-art" aria-hidden="true">
              <span class="portal-emblem"><svg viewBox="0 0 64 64"><path d="M12 33h40v17H12zM17 33V14h30v19M17 50v8m30-8v8M22 23h20" /></svg></span>
              <span class="portal-graphic graphic-space"><i v-for="seat in 9" :key="seat"></i></span>
            </div>
            <h2>找座位</h2>
            <p>按楼层、安静程度和设施，选择适合你的学习空间。</p>
            <strong class="portal-action"><span>进入空间预约</span><span class="portal-arrow" aria-hidden="true">↗</span></strong>
          </div>
        </RouterLink>
        <RouterLink to="/services" class="campus-portal portal-service">
          <div class="portal-surface">
            <div class="portal-art" aria-hidden="true">
              <span class="portal-emblem"><svg viewBox="0 0 64 64"><path d="M12 26h40v29H12zM8 26l7-15h34l7 15M25 55V39h14v16M12 26c0 9 13 9 13 0 0 9 14 9 14 0 0 9 13 9 13 0" /></svg></span>
              <span class="portal-graphic graphic-service"><i></i><i></i><i></i></span>
            </div>
            <h2>约服务</h2>
            <p>浏览校园服务，提前约好人员、时间和地点。</p>
            <strong class="portal-action"><span>浏览校园服务</span><span class="portal-arrow" aria-hidden="true">↗</span></strong>
          </div>
        </RouterLink>
        <RouterLink to="/reader" class="campus-portal portal-agenda">
          <div class="portal-surface">
            <div class="portal-art" aria-hidden="true">
              <span class="portal-emblem"><svg viewBox="0 0 64 64"><path d="M12 14h40v42H12zM22 7v14M42 7v14M12 27h40M22 39l7 7 14-13" /></svg></span>
              <span class="portal-graphic graphic-agenda"><i></i><i></i><i></i></span>
            </div>
            <h2>我的安排</h2>
            <p>查看今天与本周的预约，管理日程和同馆交流。</p>
            <strong class="portal-action"><span>查看个人安排</span><span class="portal-arrow" aria-hidden="true">↗</span></strong>
          </div>
        </RouterLink>
      </section>
      <div class="campus-guidance"><p>登录校园账号后，空间与服务预约会汇入“我的安排”。</p></div>
    </main>
    <footer class="landing-footer content-width"><span>有约 · 让校园生活有条不紊</span><RouterLink to="/admin">平台管理</RouterLink></footer>
  </div>
</template>

<style scoped>
/*
 * Visual inspiration from Uiverse's layered glass and gradient border cards.
 * https://uiverse.io/Smit-Prajapati/smart-liger-5
 * https://uiverse.io/shakil_6466/hard-hound-61
 * The artwork and responsive card treatment are tailored to the campus portals.
 */
.campus-portals {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 26px;
  padding-bottom: 12px;
}

.campus-portal {
  --portal-ink: #f5f9ff;
  --portal-copy: #d5e4f2;
  --portal-accent: #b6d9fa;
  --portal-line: #ffffff30;
  --portal-back: #6b91b5;
  --portal-glow: #4e83af;
  --portal-surface: linear-gradient(145deg, #355f86, #1b3755 75%);
  position: relative;
  isolation: isolate;
  display: flex;
  min-width: 0;
  border-radius: 26px;
  text-decoration: none;
  color: var(--portal-ink);
  -webkit-tap-highlight-color: transparent;
}

.campus-portal::before {
  content: "";
  position: absolute;
  z-index: -1;
  inset: 14px 9px -7px;
  border: 1px solid #ffffffb0;
  border-radius: inherit;
  background: var(--portal-back);
  opacity: .55;
  transform: rotate(-1.5deg);
  transition: transform .4s ease, opacity .4s ease;
  pointer-events: none;
}

.campus-portal::after {
  content: "";
  position: absolute;
  z-index: -2;
  inset: auto 28px -15px;
  height: 65px;
  background: var(--portal-glow);
  opacity: .17;
  filter: blur(21px);
  transition: opacity .4s ease;
  pointer-events: none;
}

.portal-surface {
  position: relative;
  isolation: isolate;
  display: flex;
  flex-direction: column;
  flex: 1;
  min-width: 0;
  min-height: 345px;
  padding: 28px;
  overflow: hidden;
  border: 1px solid #ffffffa0;
  border-radius: inherit;
  background: var(--portal-surface);
  box-shadow: inset 0 1px #ffffff40, 0 14px 32px #183c5b13;
  transition: transform .45s cubic-bezier(.2, .7, .2, 1), box-shadow .45s ease, border-color .45s ease;
}

.portal-surface::before {
  content: "";
  position: absolute;
  z-index: -1;
  width: 220px;
  height: 220px;
  top: -105px;
  right: -55px;
  border-radius: 50%;
  background: radial-gradient(circle, var(--portal-accent), transparent 68%);
  opacity: .24;
  pointer-events: none;
}

.portal-surface::after {
  content: "";
  position: absolute;
  z-index: 2;
  inset: 0;
  background: linear-gradient(110deg, transparent 25%, #ffffff1c 46%, #ffffff32 50%, transparent 72%);
  transform: translateX(-120%);
  pointer-events: none;
}

.portal-service {
  --portal-ink: #453a33;
  --portal-copy: #715f52;
  --portal-accent: #bd8e6c;
  --portal-line: #8f6e482b;
  --portal-back: #d4b99d;
  --portal-glow: #ba9575;
  --portal-surface: linear-gradient(140deg, #fff9f1, #eddfd1 85%);
}

.portal-agenda {
  --portal-ink: #254b42;
  --portal-copy: #526f63;
  --portal-accent: #8fc4ad;
  --portal-line: #366f542b;
  --portal-back: #a4c7b4;
  --portal-glow: #76a58a;
  --portal-surface: linear-gradient(140deg, #f1faf5, #d8eadd 85%);
}

.portal-art {
  position: relative;
  height: 112px;
  flex: none;
}

.portal-emblem {
  position: relative;
  z-index: 1;
  display: grid;
  place-items: center;
  width: 64px;
  height: 64px;
  border: 1px solid #ffffff65;
  border-radius: 18px;
  background: linear-gradient(135deg, #ffffff28, #ffffff0a);
  box-shadow: inset 0 1px #ffffff65, 0 8px 20px #17354f0d;
  backdrop-filter: blur(8px);
  -webkit-backdrop-filter: blur(8px);
  color: var(--portal-ink);
  transition: transform .45s ease, box-shadow .45s ease;
}

.portal-emblem svg {
  width: 37px;
  height: 37px;
  fill: none;
  stroke: currentColor;
  stroke-width: 2;
  stroke-linejoin: round;
  stroke-linecap: round;
}

.portal-graphic {
  position: absolute;
  right: -5px;
  top: -5px;
  width: 133px;
  height: 96px;
  pointer-events: none;
}

.graphic-space {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 8px;
  padding: 13px;
  border: 1px solid #d6ebff50;
  border-radius: 14px;
  background: linear-gradient(135deg, #d6ebff16, #d6ebff06);
  box-shadow: 8px 12px 20px #071d3924, inset 0 1px #ffffff32;
  transform: perspective(400px) rotateX(27deg) rotateZ(-17deg);
}

.graphic-space i {
  border: 1px solid #d6ebff50;
  border-top: 4px solid #d6ebff6b;
  border-radius: 5px 5px 3px 3px;
  background: #d6ebff12;
}

.graphic-space i:nth-child(5) {
  background: #c2e5ffb3;
  border-color: #e3f3ff;
  box-shadow: 0 0 16px #c2e5ff35;
}

.graphic-service {
  top: -1px;
  right: -4px;
}

.graphic-service i {
  position: absolute;
  width: 102px;
  height: 72px;
  padding: 13px;
  border: 1px solid #ffffffd9;
  border-radius: 12px;
  background: linear-gradient(135deg, #fffdf9d9, #e8d1bbd9);
  box-shadow: 0 9px 18px #8e67491a;
}

.graphic-service i:nth-child(1) { top: 4px; left: 19px; transform: rotate(14deg); opacity: .5; }
.graphic-service i:nth-child(2) { top: 15px; left: 9px; transform: rotate(-9deg); opacity: .75; }
.graphic-service i:nth-child(3) { top: 22px; left: 20px; transform: rotate(5deg); }
.graphic-service i::before {
  content: "";
  display: block;
  width: 23px;
  height: 23px;
  border: 1px solid #ac7b5659;
  border-radius: 7px;
  background: #c19a762b;
}
.graphic-service i::after {
  content: "";
  position: absolute;
  top: 15px;
  right: 16px;
  height: 43px;
  border-right: 1px dashed #ac7b5659;
}

.graphic-agenda {
  top: -4px;
  width: 126px;
  height: 99px;
  border: 1px solid #ffffffc9;
  border-radius: 14px;
  background: linear-gradient(135deg, #ffffff99, #eef7f06b);
  box-shadow: 7px 12px 20px #41735816, inset 0 1px #fff;
  transform: rotate(-10deg);
}
.graphic-agenda::before {
  content: "";
  position: absolute;
  top: 24px;
  inset-inline: 0;
  border-top: 1px solid #4a826633;
}
.graphic-agenda::after {
  content: "";
  position: absolute;
  inset: 8px 15px auto;
  height: 5px;
  border-inline: 3px solid #5d8c7759;
}
.graphic-agenda i {
  position: absolute;
  height: 12px;
  border: 1px solid #fff9;
  border-radius: 4px;
  background: #8fbaa569;
}
.graphic-agenda i:nth-child(1) { top: 36px; left: 14px; width: 57px; }
.graphic-agenda i:nth-child(2) { top: 53px; left: 43px; width: 65px; background: #52876d80; }
.graphic-agenda i:nth-child(3) { top: 70px; left: 25px; width: 42px; }

.campus-portal h2 {
  margin: 0 0 12px;
  font-size: 30px;
}
.campus-portal p {
  flex: 1;
  margin: 0;
  color: var(--portal-copy);
  font-size: 15px;
  line-height: 1.8;
}
.portal-action {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding-top: 18px;
  margin-top: 23px;
  border-top: 1px solid var(--portal-line);
  font-size: 14px;
}
.portal-arrow {
  display: grid;
  place-items: center;
  flex: none;
  width: 34px;
  height: 34px;
  border: 1px solid var(--portal-line);
  border-radius: 50%;
  background: #ffffff14;
  font-size: 19px;
  font-weight: 400;
  transition: transform .35s ease, background-color .35s ease;
}
.campus-portal:focus-visible {
  outline: 3px solid var(--focus);
  outline-offset: 6px;
}
@media (hover: hover) and (pointer: fine) and (prefers-reduced-motion: no-preference) {
  .campus-portal:is(:hover, :focus-visible) .portal-surface {
    transform: perspective(1000px) translateY(-7px) rotateX(3deg) rotateY(-2deg);
    border-color: var(--portal-accent);
    box-shadow: inset 0 1px #ffffff80, 0 22px 38px #183c5b26;
  }
  .portal-agenda:is(:hover, :focus-visible) .portal-surface {
    transform: perspective(1000px) translateY(-7px) rotateX(3deg) rotateY(2deg);
  }
  .campus-portal:is(:hover, :focus-visible)::before { transform: rotate(2deg) translateY(2px); opacity: .75; }
  .campus-portal:is(:hover, :focus-visible)::after { opacity: .27; }
  .campus-portal:is(:hover, :focus-visible) .portal-surface::after { transform: translateX(120%); transition: transform .85s ease; }
  .campus-portal:is(:hover, :focus-visible) .portal-emblem { transform: translateY(-3px) rotate(-4deg); box-shadow: inset 0 1px #ffffff80, 0 12px 22px #17354f1c; }
  .campus-portal:is(:hover, :focus-visible) .portal-arrow { transform: translate(2px, -2px); background: #ffffff35; }
}
@media (max-width: 900px) {
  .campus-portals { gap: 18px; }
  .portal-surface { padding: 22px; }
  .portal-graphic { right: -17px; transform-origin: right top; scale: .8; }
  .portal-emblem { width: 55px; height: 55px; border-radius: 15px; }
  .campus-portal h2 { font-size: 28px; }
}
@media (max-width: 650px) {
  .campus-portals { grid-template-columns: minmax(0, 1fr); gap: 25px; }
  .portal-surface { min-height: 310px; padding: 24px; }
  .portal-art { height: 102px; }
  .portal-graphic { right: 0; scale: 1; }
}
@media (prefers-reduced-motion: reduce) {
  .campus-portal::before, .campus-portal::after, .portal-surface, .portal-emblem, .portal-arrow { transition: none; }
}
</style>
