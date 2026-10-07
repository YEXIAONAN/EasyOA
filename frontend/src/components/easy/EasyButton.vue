<script setup lang="ts">
import { computed } from 'vue'

/**
 * EasyButton — Easy 系列按钮（基于 Element Plus 的语义化封装）。
 */
const props = withDefaults(
  defineProps<{
    variant?: 'primary' | 'default' | 'danger'
    size?: 'sm' | 'md' | 'lg'
    loading?: boolean
    disabled?: boolean
    block?: boolean
    nativeType?: 'button' | 'submit' | 'reset'
  }>(),
  {
    variant: 'default',
    size: 'md',
    loading: false,
    disabled: false,
    block: false,
    nativeType: 'button',
  },
)

defineEmits<{ click: [event: MouseEvent] }>()

const elType = computed(() => {
  if (props.variant === 'primary') return 'primary'
  if (props.variant === 'danger') return 'danger'
  return 'default'
})

const elSize = computed(() => {
  if (props.size === 'sm') return 'small'
  if (props.size === 'lg') return 'large'
  return 'default'
})
</script>

<template>
  <el-button
    class="easy-button"
    :class="{ 'easy-button--block': block }"
    :type="elType"
    :size="elSize"
    :loading="loading"
    :disabled="disabled"
    :native-type="nativeType"
    @click="$emit('click', $event)"
  >
    <slot />
  </el-button>
</template>

<style scoped>
.easy-button--block {
  width: 100%;
}
</style>