<script setup lang="ts">
import { computed } from 'vue'

/**
 * EasyAvatar — 头像：优先展示图片，否则展示姓名首字母（颜色由姓名稳定派生）。
 * 不依赖任何外部图片资源，私有化部署环境同样可用。
 */
const props = withDefaults(
  defineProps<{
    name: string
    src?: string | null
    size?: 'sm' | 'md' | 'lg'
  }>(),
  { src: null, size: 'md' },
)

const initial = computed(() => {
  const value = props.name.trim()
  if (!value) return '?'
  const words = value.split(/\s+/).filter(Boolean)
  const first = words[0] ?? value
  const isLatin = /^[a-zA-Z0-9]/.test(first)
  if (words.length > 1 && words[1]) {
    return `${first.charAt(0)}${words[1].charAt(0)}`.toUpperCase()
  }
  return isLatin ? first.slice(0, 2).toUpperCase() : first.slice(0, 1)
})

/** 稳定派生色相（低饱和，保持克制的中性/绿色系观感） */
const hue = computed(() => {
  let hash = 0
  for (const char of props.name) {
    hash = (hash * 31 + char.codePointAt(0)!) % 360
  }
  return hash
})
</script>

<template>
  <span class="easy-avatar" :class="`easy-avatar--${size}`">
    <img v-if="src" :src="src" :alt="name" class="easy-avatar__image" />
    <span
      v-else
      class="easy-avatar__initial"
      :style="{ background: `hsl(${hue} 36% 94%)`, color: `hsl(${hue} 42% 32%)` }"
      :aria-label="name"
    >
      {{ initial }}
    </span>
  </span>
</template>

<style scoped>
.easy-avatar {
  display: inline-flex;
  flex: none;
  border-radius: var(--easy-radius-full);
  overflow: hidden;
}

.easy-avatar--sm {
  width: 24px;
  height: 24px;
  font-size: 11px;
}

.easy-avatar--md {
  width: 32px;
  height: 32px;
  font-size: 13px;
}

.easy-avatar--lg {
  width: 44px;
  height: 44px;
  font-size: 16px;
}

.easy-avatar__image,
.easy-avatar__initial {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.easy-avatar__initial {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-weight: 600;
  letter-spacing: 0.02em;
  user-select: none;
}
</style>