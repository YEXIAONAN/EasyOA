<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import EasyButton from '@/components/easy/EasyButton.vue'
import EasyEmpty from '@/components/easy/EasyEmpty.vue'

/**
 * 模块占位页。
 *
 * 用于尚未交付的模块（Phase 2~9）：明确告知用户该模块的交付阶段与内容，
 * 而不是渲染一个「假的」页面或空表格。
 */
const route = useRoute()
const router = useRouter()

const title = computed(() => (typeof route.meta.title === 'string' ? route.meta.title : '功能'))
const phase = computed(() => (typeof route.meta.phase === 'string' ? route.meta.phase : undefined))
const summary = computed(() => (typeof route.meta.summary === 'string' ? route.meta.summary : undefined))
</script>

<template>
  <div class="easy-page">
    <header class="easy-page__header">
      <div>
        <h1 class="easy-page__title">{{ title }}</h1>
        <p class="easy-page__subtitle">该模块计划在 {{ phase ?? '后续版本' }} 交付</p>
      </div>
    </header>

    <div class="easy-card">
      <EasyEmpty :title="`${title}模块尚未交付`" :phase="phase" :description="summary">
        <template #action>
          <EasyButton @click="router.push({ name: 'workspace' })">返回工作台</EasyButton>
        </template>
      </EasyEmpty>
    </div>
  </div>
</template>