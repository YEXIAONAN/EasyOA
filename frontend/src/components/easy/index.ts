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
 * 基础组件：Avatar / Button / Input / Select / Status / Empty
 * 容器组件：Dialog（Modal）/ Drawer（Side Panel）
 * 交互能力：Confirm（危险操作确认）、CommandPalette（命令面板，见 components/easy/EasyCommandPalette.vue）
 *
 * 设计原则：组件只承载样式与交互，不承载业务规则；Element Plus 仅作底层实现。
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