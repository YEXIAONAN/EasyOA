import { ref } from 'vue'
import { defineStore } from 'pinia'

/** 窄屏断点：低于此宽度时侧边栏自动收起为图标栏，把空间让给内容 */
const NARROW_VIEWPORT_QUERY = '(max-width: 1023px)'

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

  /**
   * 视口宽度联动侧边栏。
   *
   * 只在**跨越断点时**改变折叠状态：进入窄屏自动收起、回到宽屏自动展开，
   * 从而既保证窄屏内容区可用，又不会在用户手动折叠后被反复覆盖。
   */
  function initViewportSync(): () => void {
    const media = window.matchMedia(NARROW_VIEWPORT_QUERY)
    sidebarCollapsed.value = media.matches

    const onChange = (event: MediaQueryListEvent): void => {
      sidebarCollapsed.value = event.matches
    }
    media.addEventListener('change', onChange)
    return () => media.removeEventListener('change', onChange)
  }

  return {
    sidebarCollapsed,
    commandPaletteOpen,
    toggleSidebar,
    openCommandPalette,
    closeCommandPalette,
    initViewportSync,
  }
})