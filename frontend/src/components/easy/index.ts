import type { Component } from 'vue'

import EasyAvatar from './EasyAvatar.vue'
import EasyButton from './EasyButton.vue'
import EasyDialog from './EasyDialog.vue'
import EasyEmpty from './EasyEmpty.vue'
import EasyInput from './EasyInput.vue'

/**
 * EasyOA 设计系统组件出口。
 *
 * v0.1.0 Phase 1 交付：Button / Input / Avatar / Empty / Dialog（均已有真实使用场景）。
 * EasySelect / EasyTable / EasyStatus / EasyDrawer / EasyMemberPicker / EasyConfirm
 * 将随其消费方模块（Phase 3/4/5）一起交付，避免产生无使用方的空抽象。
 */
export const easyComponents: Record<string, Component> = {
  EasyAvatar,
  EasyButton,
  EasyDialog,
  EasyEmpty,
  EasyInput,
}

export { EasyAvatar, EasyButton, EasyDialog, EasyEmpty, EasyInput }