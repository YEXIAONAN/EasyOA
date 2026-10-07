<script setup lang="ts">
import { computed, useAttrs } from 'vue'

/**
 * EasySelect — 带标签的下拉选择（用于筛选与表单）。
 *
 * 注意：class / style 保留在组件根元素上（不转发给内部 el-select），
 * 这样调用方的 scoped 样式（例如设置宽度）才能生效；
 * 其余属性（placeholder / teleported 等）继续透传给 el-select。
 */
export interface EasySelectOption {
  label: string
  value: string | number
}

withDefaults(
  defineProps<{
    label?: string
    options: EasySelectOption[]
    placeholder?: string
    clearable?: boolean
    hint?: string
    block?: boolean
  }>(),
  { placeholder: '请选择', clearable: true, block: false },
)

defineOptions({ inheritAttrs: false })

const model = defineModel<string | number | Array<string | number> | null | undefined>()

const attrs = useAttrs()
const rootClass = computed(() => attrs.class ?? null)
const rootStyle = computed(() => attrs.style ?? null)

/** 除 class / style 之外的属性透传给内部 el-select */
const selectAttrs = computed(() => {
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
  <div class="easy-field" :class="[rootClass, { 'easy-field--block': block }]" :style="rootStyle">
    <label v-if="label" class="easy-field__label">{{ label }}</label>
    <el-select v-model="model" v-bind="selectAttrs" :placeholder="placeholder" :clearable="clearable">
      <el-option v-for="option in options" :key="option.value" :label="option.label" :value="option.value" />
    </el-select>
    <span v-if="hint" class="easy-field__hint">{{ hint }}</span>
  </div>
</template>

<style scoped>
.easy-field {
  display: flex;
  flex-direction: column;
  gap: var(--easy-space-2);
}

.easy-field--block {
  width: 100%;
}

.easy-field__label {
  font-size: var(--easy-text-sm);
  font-weight: 500;
  color: var(--easy-text-2);
}

.easy-field__hint {
  font-size: var(--easy-text-xs);
  color: var(--easy-text-3);
}
</style>