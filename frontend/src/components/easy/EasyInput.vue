<script setup lang="ts">
import { useId } from 'vue'

/**
 * EasyInput — 带标签 / 提示 / 错误态的输入框。
 * 支持 v-model 与透传 Element Plus 输入属性（如 show-password、placeholder、autocomplete）。
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
</script>

<template>
  <div class="easy-field" :class="{ 'easy-field--invalid': !!error }">
    <label v-if="label" class="easy-field__label" :for="inputId">
      {{ label }}
      <em v-if="required" aria-hidden="true">*</em>
    </label>
    <el-input :id="inputId" v-model="model" v-bind="$attrs" />
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