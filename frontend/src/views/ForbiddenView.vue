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
        title="没有访问权限"
        description="当前账号无权访问该页面。如果你认为这是误判，请联系系统管理员确认你的角色。"
      >
        <template #action>
          <div class="status-page__actions">
            <EasyButton @click="router.back()">返回上一页</EasyButton>
            <EasyButton variant="primary" @click="router.push({ name: 'workspace' })">回到工作台</EasyButton>
          </div>
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

.status-page__actions {
  display: flex;
  gap: var(--easy-space-2);
}
</style>