<script setup lang="ts">
/**
 * EasyEmpty — 统一空状态。
 *
 * 产品要求：空状态必须说明「为什么是空的 / 下一步是什么」，
 * 而不是一句苍白的「暂无数据」。
 */
withDefaults(
  defineProps<{
    title: string
    description?: string
    compact?: boolean
  }>(),
  { compact: false },
)
</script>

<template>
  <div class="easy-empty" :class="{ 'easy-empty--compact': compact }">
    <svg class="easy-empty__art" viewBox="0 0 96 72" fill="none" aria-hidden="true">
      <rect x="12" y="16" width="72" height="44" rx="8" fill="var(--easy-surface-sunken)" />
      <rect x="22" y="28" width="40" height="4" rx="2" fill="var(--easy-border-strong)" />
      <rect x="22" y="38" width="52" height="4" rx="2" fill="var(--easy-border)" />
      <rect x="22" y="48" width="30" height="4" rx="2" fill="var(--easy-border)" />
      <circle cx="74" cy="24" r="9" fill="var(--easy-brand-subtle)" stroke="var(--easy-brand-subtle-border)" />
      <path
        d="M70.5 24.2l2.4 2.4 4.6-4.8"
        stroke="var(--easy-brand)"
        stroke-width="1.8"
        stroke-linecap="round"
        stroke-linejoin="round"
      />
    </svg>
    <div class="easy-empty__text">
      <div class="easy-empty__title">{{ title }}</div>
      <p v-if="description" class="easy-empty__description">{{ description }}</p>
    </div>
    <div v-if="$slots.action" class="easy-empty__action">
      <slot name="action" />
    </div>
  </div>
</template>

<style scoped>
.easy-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--easy-space-3);
  padding: var(--easy-space-10) var(--easy-space-6);
  text-align: center;
}

.easy-empty--compact {
  padding: var(--easy-space-6) var(--easy-space-4);
}

.easy-empty__art {
  width: 96px;
  height: 72px;
}

.easy-empty__title {
  font-size: var(--easy-text-base);
  font-weight: 600;
  color: var(--easy-text-1);
}

.easy-empty__description {
  max-width: 420px;
  margin-top: var(--easy-space-1);
  color: var(--easy-text-3);
  font-size: var(--easy-text-sm);
  line-height: var(--easy-leading-relaxed);
}

.easy-empty__action {
  margin-top: var(--easy-space-2);
}
</style>