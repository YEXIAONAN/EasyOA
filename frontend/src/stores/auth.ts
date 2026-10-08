import { computed, ref } from 'vue'
import { defineStore } from 'pinia'

import { authApi } from '@/api/modules/auth'
import type { CurrentUser, LoginPayload } from '@/api/types'

/**
 * 认证状态。
 *
 * 只保存「当前用户是谁」；权限与数据可见性永远由后端判定，
 * 前端角色判断仅用于导航展示与路由体验。
 */
export const useAuthStore = defineStore('auth', () => {
  const user = ref<CurrentUser | null>(null)
  const setupRequired = ref(false)
  const bootstrapped = ref(false)
  const bootstrapError = ref<string | null>(null)

  const isAuthenticated = computed(() => user.value !== null)
  const isAdminLike = computed(
    () => user.value?.systemRole === 'ROOT' || user.value?.systemRole === 'ADMIN',
  )
  const isRoot = computed(() => user.value?.systemRole === 'ROOT')
  const displayName = computed(() => user.value?.displayName ?? '')

  /** 应用启动时调用一次：判断是否处于初始化阶段，并尝试恢复登录态 */
  async function bootstrap(): Promise<void> {
    if (bootstrapped.value) return
    bootstrapError.value = null
    try {
      const status = await authApi.setupStatus()
      setupRequired.value = status.required
      if (!status.required) {
        try {
          // silent401：未登录是启动探测的预期结果，交给路由守卫决定是否跳转，
          // 避免全局 401 处理器抢先跳转并丢失 redirect 目标
          user.value = await authApi.me(true)
        } catch {
          user.value = null
        }
      }
    } catch (error) {
      bootstrapError.value = error instanceof Error ? error.message : '无法连接服务器'
    } finally {
      bootstrapped.value = true
    }
  }

  async function login(payload: LoginPayload): Promise<CurrentUser> {
    const currentUser = await authApi.login(payload)
    user.value = currentUser
    setupRequired.value = false
    return currentUser
  }

  async function logout(): Promise<void> {
    try {
      await authApi.logout()
    } finally {
      user.value = null
    }
  }

  /** 会话被后端判定失效时由 401 处理器调用 */
  function clearSession(): void {
    user.value = null
  }

  function markSetupCompleted(): void {
    setupRequired.value = false
  }

  return {
    user,
    setupRequired,
    bootstrapped,
    bootstrapError,
    isAuthenticated,
    isAdminLike,
    isRoot,
    displayName,
    bootstrap,
    login,
    logout,
    clearSession,
    markSetupCompleted,
  }
})