<script setup lang="ts">
import { useRouter } from 'vue-router'

import EasyButton from '@/components/easy/EasyButton.vue'
import EasyEmpty from '@/components/easy/EasyEmpty.vue'

const router = useRouter()
</script>

<template>
  <div class="status-page">
    <div class="easy-card status-page__card">
      <EasyEmpty
        title="页面不存在"
        description="你访问的地址可能已被移除、重命名，或者从未存在过。"
      >
        <template #action>
          <EasyButton variant="primary" @click="router.push({ name: 'workspace' })">回到工作台</EasyButton>
        </template>
      </EasyEmpty>
    </div>
  </div>
</template>

<style scoped>
.status-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: var(--easy-space-8);
  background: var(--easy-bg);
}

.status-page__card {
  width: min(520px, 100%);
}
</style>