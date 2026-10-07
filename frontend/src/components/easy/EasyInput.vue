<script setup lang="ts">
import { computed, useAttrs, useId } from 'vue'

/**
 * EasyInput — 带标签 / 提示 / 错误态的输入框。
 *
 * 注意：class / style 保留在组件根元素上，保证调用方的 scoped 样式（宽度等）生效；
 * 其余属性（placeholder / show-password / autocomplete 等）透传给 el-input。
 */
withDefaults(
  defineProps<{
    label?: string
    hint?: string
    error?: string
    required?: boolean
  }>(),
  { required: false },
)

defineOptions({ inheritAttrs: false })

const model = defineModel<string>({ default: '' })
const inputId = useId()

const attrs = useAttrs()
const rootClass = computed(() => attrs.class ?? null)
const rootStyle = computed(() => attrs.style ?? null)

const inputAttrs = computed(() => {
  const rest: Record<string, unknown> = {}
  for (const [key, value] of Object.entries(attrs)) {
    if (key !== 'class' && key !== 'style') {
      rest[key] = value
    }
  }
  return rest
})
</script>

<template>
  <div class="easy-field" :class="[rootClass, { 'easy-field--invalid': !!error }]" :style="rootStyle">
    <label v-if="label" class="easy-field__label" :for="inputId">
      {{ label }}
      <em v-if="required" aria-hidden="true">*</em>
    </label>
    <el-input :id="inputId" v-model="model" v-bind="inputAttrs" />
    <span v-if="error" class="easy-field__error" role="alert">{{ error }}</span>
    <span v-else-if="hint" class="easy-field__hint">{{ hint }}</span>
  </div>
</template>

<style scoped>
.easy-field {
  display: flex;
  flex-direction: column;
  gap: var(--easy-space-2);
}

.easy-field__label {
  font-size: var(--easy-text-sm);
  font-weight: 500;
  color: var(--easy-text-2);
}

.easy-field__label em {
  margin-left: 2px;
  font-style: normal;
  color: var(--easy-danger);
}

.easy-field__hint {
  font-size: var(--easy-text-xs);
  color: var(--easy-text-3);
}

.easy-field__error {
  font-size: var(--easy-text-xs);
  color: var(--easy-danger);
  animation: easy-fade-in var(--easy-transition-fast);
}

.easy-field--invalid :deep(.el-input__wrapper) {
  box-shadow: 0 0 0 1px var(--easy-danger) inset;
}
</style>