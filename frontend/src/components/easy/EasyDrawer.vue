<script setup lang="ts">
/**
 * EasyDrawer — 右侧滑出面板。
 *
 * 交互原则：优先 Side Panel 而不是 Modal；
 * 任务详情、成员档案等「伴随上下文」的内容都应使用 Drawer。
 */
const open = defineModel<boolean>({ required: true })

withDefaults(
  defineProps<{
    title?: string
    size?: number | string
    showHeader?: boolean
  }>(),
  { size: 420, showHeader: true },
)
</script>

<template>
  <el-drawer
    v-model="open"
    :size="size"
    :with-header="showHeader && !!title"
    :title="title"
    append-to-body
    destroy-on-close
    class="easy-drawer"
  >
    <slot />
  </el-drawer>
</template>

<style>
.easy-drawer .el-drawer__header {
  margin-bottom: 0;
  padding: var(--easy-space-5) var(--easy-space-6) var(--easy-space-3);
  border-bottom: 1px solid var(--easy-border);
  color: var(--easy-text-1);
  font-weight: 600;
}

.easy-drawer .el-drawer__body {
  padding: var(--easy-space-5) var(--easy-space-6);
  overflow-y: auto;
}
</style>