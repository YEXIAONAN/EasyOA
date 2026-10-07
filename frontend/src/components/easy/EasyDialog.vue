<script setup lang="ts">
/**
 * EasyDialog — 统一对话框（克制使用：优先 Side Panel / Inline Edit）。
 * 危险操作场景外不建议使用 Modal。
 */
const open = defineModel<boolean>({ required: true })

withDefaults(
  defineProps<{
    title?: string
    width?: number | string
    closeOnClickModal?: boolean
  }>(),
  { width: 420, closeOnClickModal: false },
)
</script>

<template>
  <el-dialog
    v-model="open"
    :title="title"
    :width="width"
    :close-on-click-modal="closeOnClickModal"
    align-center
    append-to-body
  >
    <slot />
    <template v-if="$slots.footer" #footer>
      <slot name="footer" />
    </template>
  </el-dialog>
</template>