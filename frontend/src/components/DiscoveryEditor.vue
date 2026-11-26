<script setup lang="ts">
import { ref } from 'vue'
import { api, message } from '../api'
import { spaceTypes, quietLevels, serviceCategories, type Discovery } from '../campus'
const props = defineProps<{ area: 'library' | 'service'; root?: string }>()
const root = props.root ? props.root + '/discovery' : '/owner/discovery'
const profile = ref<Discovery>()
const error = ref(''), notice = ref(''), busy = ref(false)
async function load() {
  error.value = ''
  try { profile.value = await api<Discovery>(root) } catch (issue) { error.value = message(issue) }
}
async function save() {
  busy.value = true; error.value = ''; notice.value = ''
  try { profile.value = await api<Discovery>(root, 'PUT', profile.value); notice.value = '展示资料已保存。' }
  catch (issue) { error.value = message(issue) } finally { busy.value = false }
}
void load()
</script>
<template>
  <section class="panel discovery-editor">
    <h2>{{ area === 'library' ? '空间展示信息' : '服务目录信息' }}</h2>
    <p class="muted">{{ area === 'library' ? '帮助同学在选座前了解空间。保存后展示在“找座位”入口。' : '发布服务后，这些资料会出现在“约服务”入口。' }}</p>
    <p v-if="error" class="notice error" role="alert">{{ error }} <button class="text-button" @click="load">重试</button></p>
    <p v-if="notice" class="notice success" role="status">{{ notice }}</p>
    <form v-if="profile" class="form-stack" @submit.prevent="save">
      <template v-if="area === 'library'">
        <label>空间类型<select v-model="profile.spaceType"><option v-for="(label, key) in spaceTypes" :key="key" :value="key">{{ label }}</option></select></label>
        <label>安静程度<select v-model="profile.quietLevel"><option v-for="(label, key) in quietLevels" :key="key" :value="key">{{ label }}</option></select></label>
        <label>使用规则<textarea v-model.trim="profile.rules" maxlength="500" rows="3" placeholder="例如：研讨需轻声交流；离开时带走随身物品。"></textarea></label>
      </template>
      <label v-else>服务分类<select v-model="profile.category"><option v-for="(label, key) in serviceCategories" :key="key" :value="key">{{ label }}</option></select></label>
      <label>地点<input v-model.trim="profile.address" maxlength="180" placeholder="校区、楼栋、楼层或详细地址" /></label>
      <div><button class="button button-dark" :disabled="busy">保存展示信息</button></div>
    </form>
  </section>
</template>
