<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { getAboutInfo, type AboutInfo } from '@/api/modules/about'
import EasyButton from '@/components/easy/EasyButton.vue'

const info = ref<AboutInfo | null>(null)
const loading = ref(false)
const error = ref('')
const releaseLabel = computed(() => {
  if (info.value?.releaseStatus === 'Official' && info.value.signatureVerified) return '官方签名版本'
  return info.value?.releaseStatus === 'Development Build' ? '开发构建' : '非官方构建'
})
async function load() {
  loading.value = true
  error.value = ''
  try { info.value = await getAboutInfo() }
  catch { error.value = '暂时无法获取版本信息，请稍后重试。' }
  finally { loading.value = false }
}
onMounted(load)
</script>

<template>
  <main class="about">
    <nav class="about__nav"><router-link :to="{ name: 'workspace' }">返回 EasyOA</router-link></nav>
    <section class="easy-card about__card" aria-labelledby="about-title">
      <p class="about__eyebrow">关于</p>
      <h1 id="about-title">EasyOA Community Edition</h1>
      <p class="about__intro">轻量、自托管的团队协作与办公系统</p>
      <p v-if="loading" role="status">正在获取版本信息…</p>
      <div v-else-if="error" role="alert" class="about__error">
        <p>{{ error }}</p><EasyButton @click="load">重试</EasyButton>
      </div>
      <template v-else-if="info">
        <dl class="about__details">
          <div><dt>版本</dt><dd>v{{ info.version }}</dd></div>
          <div><dt>许可证</dt><dd>{{ info.license }}</dd></div>
          <div><dt>发布者</dt><dd>{{ info.publisher }}</dd></div>
          <div><dt>发布状态</dt><dd>{{ releaseLabel }}</dd></div>
          <div><dt>源码</dt><dd><a :href="info.sourceUrl" target="_blank" rel="noopener noreferrer">获取对应源码 ↗</a></dd></div>
        </dl>
        <p class="about__note">Community Edition 采用 AGPL-3.0-only。历史已发布的 MIT 版本继续适用原许可证。</p>
      </template>
    </section>
  </main>
</template>

<style scoped>
.about { min-height: 100vh; background: var(--easy-bg); padding: var(--easy-space-8); }
.about__nav, .about__card { width: min(720px, 100%); margin-inline: auto; }
.about__nav { margin-bottom: var(--easy-space-5); }
.about__card { padding: var(--easy-space-8); }
.about__eyebrow { color: var(--easy-text-3); margin: 0 0 var(--easy-space-2); }
h1 { font-size: 24px; line-height: 1.4; margin: 0; }
.about__intro { color: var(--easy-text-2); margin: var(--easy-space-2) 0 var(--easy-space-8); }
.about__details { margin: 0; }
.about__details div { display: grid; grid-template-columns: 104px minmax(0, 1fr); gap: var(--easy-space-4); padding: var(--easy-space-4) 0; border-bottom: 1px solid var(--easy-border); }
dt { color: var(--easy-text-3); } dd { margin: 0; overflow-wrap: anywhere; }
a { color: var(--easy-brand-text); } a:focus-visible { outline: 2px solid var(--easy-brand); outline-offset: 4px; }
.about__note { font-size: var(--easy-text-sm); color: var(--easy-text-3); margin: var(--easy-space-6) 0 0; line-height: 1.7; }
.about__error p { color: var(--easy-danger); }
@media (max-width: 600px) { .about { padding: var(--easy-space-4); } .about__card { padding: var(--easy-space-5); } }
</style>
