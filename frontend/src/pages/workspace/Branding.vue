<script setup lang="ts">
import { computed, ref } from 'vue'
import { api, message, type Offering, type Shop } from '../../api'
import { defaultBrand, themes, type ShopBrand } from '../../branding'
import BookingNote from '../../components/BookingNote.vue'
import StoreIdentity from '../../components/StoreIdentity.vue'

const shop = ref<Shop>()
const draft = ref<ShopBrand>({ ...defaultBrand })
const services = ref<Offering[]>([])
const pending = ref(false)
const loaded = ref(false)
const error = ref('')
const notice = ref('')
const preview = ref<HTMLElement>()
const reading = ref(0)
const link = computed(() => shop.value ? '/s/' + shop.value.slug : '')
const endpoint = '/owner/shop/branding'
const images = [
  { field: 'logo', label: '店标', limit: 200 },
  { field: 'cover', label: '封面', limit: 400 },
] as const

async function load() {
  error.value = ''
  pending.value = true
  try {
    const [profile, brand] = await Promise.all([api<Shop>('/owner/shop'), api<ShopBrand>(endpoint)])
    shop.value = profile
    draft.value = brand
    loaded.value = true
  } catch (issue) { error.value = message(issue) }
  finally { pending.value = false }
  void api<Offering[]>('/owner/services')
    .then(items => { services.value = items.filter(item => item.active) })
    .catch(() => { services.value = [] })
}

async function readImage(event: Event, field: 'logo' | 'cover', limit: number) {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  input.value = ''
  if (!file) return
  error.value = ''
  notice.value = ''
  if (!['image/jpeg', 'image/png'].includes(file.type) || file.size > limit * 1024) {
    error.value = '请选择不超过 ' + limit + ' KB 的 JPG 或 PNG 图片。'
    return
  }
  reading.value++
  try {
    const bitmap = await createImageBitmap(file)
    const sizeOk = bitmap.width <= 2400 && bitmap.height <= 2400
    bitmap.close()
    if (!sizeOk) throw new Error('图片宽度和高度均须在 2400 像素以内。')
    draft.value[field] = await new Promise<string>((resolve, reject) => {
      const reader = new FileReader()
      reader.onload = () => resolve(String(reader.result))
      reader.onerror = () => reject(new Error('图片读取失败，请重新选择。'))
      reader.readAsDataURL(file)
    })
  } catch (issue) { error.value = message(issue) }
  finally { reading.value-- }
}

async function save(publish: boolean) {
  if (pending.value || reading.value) return
  pending.value = true
  error.value = ''
  notice.value = ''
  try {
    draft.value = await api<ShopBrand>(endpoint, 'PUT', { brand: draft.value, publish })
    notice.value = publish
      ? shop.value?.published ? '外观已发布，原预约链接现在展示新外观。' : '外观已发布。开通预约页后，顾客就能看到。'
      : '草稿已保存，顾客继续看到已发布的外观。'
  } catch (issue) { error.value = message(issue) }
  finally { pending.value = false }
}

void load()
</script>

<template>
  <section class="panel">
    <div class="panel-heading">
      <h2>店铺展示</h2>
      <a v-if="shop?.published" :href="link" class="button button-outline" target="_blank" rel="noopener">查看已发布预约页 ↗</a>
    </div>
    <p v-if="error" class="notice error" role="alert">{{ error }}</p>
    <p v-if="notice" class="notice success" role="status">{{ notice }}</p>
    <p v-if="!loaded && pending" role="status">正在加载预约站设置…</p>
    <button v-else-if="!loaded" class="button button-outline" @click="load">重新加载</button>
    <div v-if="loaded && shop" class="branding-grid">
      <form class="form-stack" @submit.prevent="save(false)">
        <fieldset class="form-stack" :disabled="pending || reading > 0">
          <fieldset class="theme-picker">
            <legend>主题色</legend>
            <div class="theme-options">
              <label v-for="theme in themes" :key="theme.id" class="check store-theme" :data-theme="theme.id">
                <input v-model="draft.theme" type="radio" name="store-theme" :value="theme.id" />
                <span class="theme-swatch" aria-hidden="true"></span>{{ theme.name }}
              </label>
            </div>
          </fieldset>
          <div v-for="image in images" :key="image.field">
            <label>{{ image.label }}
              <input type="file" accept="image/jpeg,image/png" @change="readImage($event, image.field, image.limit)" />
              <small class="muted">JPG 或 PNG，最多 {{ image.limit }} KB。</small>
            </label>
            <button v-if="draft[image.field]" type="button" class="text-button" @click="draft[image.field] = ''">移除{{ image.label }}</button>
          </div>
          <label>到店地址<input v-model.trim="draft.address" maxlength="180" /></label>
          <label>营业说明<textarea v-model.trim="draft.businessNote" rows="2" maxlength="300"></textarea></label>
          <label>预约须知<textarea v-model.trim="draft.bookingNote" rows="3" maxlength="500"></textarea></label>
        </fieldset>
        <div class="actions">
          <button class="button button-outline" :disabled="pending || reading > 0">保存草稿</button>
          <button type="button" class="button button-dark" :disabled="pending || reading > 0" @click="save(true)">发布外观</button>
          <button type="button" class="button button-ghost preview-jump" @click="preview?.focus()">查看手机预览</button>
        </div>
        <RouterLink v-if="!shop.published" to="/owner?tab=shop">前往提交入驻审核</RouterLink>
      </form>
      <section ref="preview" tabindex="-1" class="brand-preview" aria-labelledby="brand-preview-title">
        <h3 id="brand-preview-title">手机预览</h3>
        <div class="brand-phone store-theme" :data-theme="draft.theme">
          <StoreIdentity :name="shop.name" :description="shop.description" :brand="draft" preview />
          <div class="panel">
            <h3>选择预约时间</h3>
            <BookingNote :note="draft.bookingNote" />
            <label>服务
              <select disabled>
                <option>请选择服务</option>
                <option v-for="service in services" :key="service.id">{{ service.name }} · {{ service.durationMinutes }} 分钟</option>
              </select>
            </label>
            <button disabled class="button button-dark">确认预约</button>
          </div>
          <footer class="store-credit">由有约提供预约服务</footer>
        </div>
      </section>
    </div>
  </section>
</template>
