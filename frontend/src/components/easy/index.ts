import type { Component } from 'vue'

import EasyAvatar from './EasyAvatar.vue'
import EasyButton from './EasyButton.vue'
import EasyDialog from './EasyDialog.vue'
import EasyDrawer from './EasyDrawer.vue'
import EasyEmpty from './EasyEmpty.vue'
import EasyInput from './EasyInput.vue'
import EasySelect from './EasySelect.vue'
import EasyStatus from './EasyStatus.vue'
import { confirmAction } from './easyConfirm'

/**
 * EasyOA 设计系统组件出口。
 *
 * Phase 1：Button / Input / Avatar / Empty / Dialog
 * Phase 2：Drawer（成员档案） / Select（筛选与表单） / Status（状态标签） / Confirm（危险操作）
 *
 * EasyTable / EasyMemberPicker 将随其消费方模块（Phase 3/4）交付。
 */
export const easyComponents: Record<string, Component> = {
  EasyAvatar,
  EasyButton,
  EasyDialog,
  EasyDrawer,
  EasyEmpty,
  EasyInput,
  EasySelect,
  EasyStatus,
}

export { confirmAction, EasyAvatar, EasyButton, EasyDialog, EasyDrawer, EasyEmpty, EasyInput, EasySelect, EasyStatus }
export type { ConfirmOptions } from './easyConfirm'