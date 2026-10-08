<script setup lang="ts">
import { onBeforeUnmount, onMounted } from 'vue'

import EasyCommandPalette from '@/components/easy/EasyCommandPalette.vue'
import { useUiStore } from '@/stores/ui'
import AppSidebar from './AppSidebar.vue'
import AppTopbar from './AppTopbar.vue'

/**
 * 应用主框架：Sidebar + Topbar + 内容区。
 *
 * 布局说明：内容区宽度受限并可滚动，不做绝对像素定位；
 * 窄屏时侧边栏自动收起为图标栏（见 ui store 的 initViewportSync），
 * 保证小屏下内容区仍然可用。
 */
const ui = useUiStore()
let disposeViewportSync: (() => void) | null = null

onMounted(() => {
  disposeViewportSync = ui.initViewportSync()
})

onBeforeUnmount(() => {
  disposeViewportSync?.()
})
</script>

<template>
  <div class="app-shell">
    <AppSidebar />
    <div class="app-shell__main">
      <AppTopbar />
      <main class="app-shell__content">
        <router-view v-slot="{ Component }">
          <component :is="Component" class="easy-fade-in" />
        </router-view>
      </main>
    </div>
    <EasyCommandPalette />
  </div>
</template>

<style scoped>
.app-shell {
  display: flex;
  height: 100vh;
  overflow: hidden;
}

.app-shell__main {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
}

.app-shell__content {
  flex: 1;
  overflow-y: auto;
  background: var(--easy-bg);
}
</style>