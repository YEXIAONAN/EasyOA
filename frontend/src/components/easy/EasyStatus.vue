<script setup lang="ts">
/**
 * EasyStatus — 状态标签（低噪音设计：以圆点 + 文本为主，不做大面积色块）。
 */
withDefaults(
  defineProps<{
    label: string
    tone?: 'neutral' | 'success' | 'warning' | 'danger' | 'brand'
    subtle?: boolean
  }>(),
  { tone: 'neutral', subtle: true },
)
</script>

<template>
  <span class="easy-status" :class="[`easy-status--${tone}`, { 'easy-status--plain': !subtle }]">
    <span class="easy-status__dot" aria-hidden="true" />
    <span class="easy-status__label">{{ label }}</span>
  </span>
</template>

<style scoped>
.easy-status {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 2px 8px;
  border-radius: var(--easy-radius-full);
  border: 1px solid var(--easy-border);
  background: var(--easy-surface);
  font-size: var(--easy-text-xs);
  color: var(--easy-text-2);
  white-space: nowrap;
}

.easy-status--plain {
  padding: 0;
  border: none;
  background: transparent;
}

.easy-status__dot {
  width: 6px;
  height: 6px;
  border-radius: var(--easy-radius-full);
  background: var(--easy-text-3);
  flex: none;
}

.easy-status--success {
  color: var(--easy-success);
}

.easy-status--success .easy-status__dot {
  background: var(--easy-success);
}

.easy-status--warning {
  color: var(--easy-warning);
}

.easy-status--warning .easy-status__dot {
  background: var(--easy-warning);
}

.easy-status--danger {
  color: var(--easy-danger);
}

.easy-status--danger .easy-status__dot {
  background: var(--easy-danger);
}

.easy-status--brand {
  color: var(--easy-brand-text);
}

.easy-status--brand .easy-status__dot {
  background: var(--easy-brand);
}
</style>