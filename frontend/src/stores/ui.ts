import { ref } from 'vue'
import { defineStore } from 'pinia'

/**
 * 全局界面状态（侧边栏折叠、命令面板开关）。
 */
export const useUiStore = defineStore('ui', () => {
  const sidebarCollapsed = ref(false)
  const commandPaletteOpen = ref(false)

  function toggleSidebar(): void {
    sidebarCollapsed.value = !sidebarCollapsed.value
  }

  function openCommandPalette(): void {
    commandPaletteOpen.value = true
  }

  function closeCommandPalette(): void {
    commandPaletteOpen.value = false
  }

  return {
    sidebarCollapsed,
    commandPaletteOpen,
    toggleSidebar,
    openCommandPalette,
    closeCommandPalette,
  }
})