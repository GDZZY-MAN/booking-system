<script setup lang="ts">
import SiteHeader from '../components/SiteHeader.vue'
const stages = [
  { name: '配置服务', text: '录入你的服务与时长，给每位员工安排擅长的项目。', symbol: '✦' },
  { name: '发布预约页', text: '设置好排班，把店铺的专属链接发到微信或社交平台。', symbol: '↗' },
  { name: '管理预约', text: '顾客自己选时间，店主查看全店订单，员工查看自己的预约。', symbol: '✓' },
]
</script>

<template>
  <SiteHeader />
  <main id="main">
    <section class="landing-hero content-width">
      <div class="hero-copy">
        <p class="hero-kicker"><span></span> 小店与工作室的在线预约工具</p>
        <h1>让顾客预约，<br /><em>让你专心开店。</em></h1>
        <p class="hero-description">设置服务、员工和营业时段，分享一条专属链接。顾客自己选时间，你和员工都知道下一单是谁。</p>
        <RouterLink class="button button-dark button-large" to="/register">开通我的预约页 <span aria-hidden="true">↗</span></RouterLink>
      </div>
      <div class="hero-visual" aria-hidden="true">
        <div class="visual-heading"><span>预约流程预览</span><span>有约.</span></div>
        <div class="visual-preview">
          <div class="visual-shop"><span>✳</span><div><strong>青禾工作室</strong><small>在线预约</small></div></div>
          <div class="visual-service"><span>选服务</span><strong>手部护理</strong><small>45 分钟</small></div>
          <div class="visual-service"><span>选员工</span><strong>小岚</strong><small>护理师</small></div>
          <div class="visual-slots"><span>10:00</span><span>10:45</span><span>11:30</span></div>
          <div class="visual-confirm"><span>✓</span><div><strong>预约已确认</strong><small>小岚 · 10:45–11:30</small></div></div>
        </div>
      </div>
    </section>
    <section class="landing-steps content-width" aria-labelledby="start-heading">
      <h2 id="start-heading">三步开通预约</h2>
      <div class="steps-grid">
        <article v-for="stage in stages" :key="stage.name">
          <span class="feature-icon" aria-hidden="true">{{ stage.symbol }}</span>
          <h3>{{ stage.name }}</h3>
          <p>{{ stage.text }}</p>
        </article>
      </div>
    </section>
  </main>
  <footer class="landing-footer content-width"><span>有约 · 商家自助预约平台</span><RouterLink to="/login">进入工作台 ↗</RouterLink></footer>
</template>
