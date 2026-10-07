<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowDown, Bell, Search } from '@element-plus/icons-vue'

import { authApi } from '@/api/modules/auth'
import { ApiError } from '@/api/errors'
import EasyAvatar from '@/components/easy/EasyAvatar.vue'
import EasyButton from '@/components/easy/EasyButton.vue'
import EasyDialog from '@/components/easy/EasyDialog.vue'
import EasyInput from '@/components/easy/EasyInput.vue'
import { useAuthStore } from '@/stores/auth'
import { useNotificationStore } from '@/stores/notification'
import { useUiStore } from '@/stores/ui'
import { systemRoleLabel } from '@/utils/permission'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const notification = useNotificationStore()
const ui = useUiStore()

const pageTitle = computed(() => (typeof route.meta.title === 'string' ? route.meta.title : ''))
const isMac = computed(() => /Mac|iPhone|iPad/.test(navigator.userAgent))

// --- 修改密码 ---------------------------------------------------------------
const passwordDialogOpen = ref(false)
const currentPassword = ref('')
const newPassword = ref('')
const confirmPassword = ref('')
const submitting = ref(false)
const formError = ref<string | null>(null)

function openPasswordDialog(): void {
  currentPassword.value = ''
  newPassword.value = ''
  confirmPassword.value = ''
  formError.value = null
  passwordDialogOpen.value = true
}

async function submitPasswordChange(): Promise<void> {
  formError.value = null
  if (!currentPassword.value) {
    formError.value = '请输入当前密码'
    return
  }
  if (newPassword.value.length < 10) {
    formError.value = '新密码长度至少 10 位'
    return
  }
  if (newPassword.value !== confirmPassword.value) {
    formError.value = '两次输入的新密码不一致'
    return
  }
  submitting.value = true
  try {
    await authApi.changePassword({
      currentPassword: currentPassword.value,
      newPassword: newPassword.value,
    })
    passwordDialogOpen.value = false
    notification.success('密码已更新，其他设备的登录状态已失效')
  } catch (error) {
    // 密码类错误在表单内就地提示，其余统一走全局提示
    if (error instanceof ApiError && (error.code === 'PASSWORD_MISMATCH' || error.code === 'PASSWORD_POLICY_VIOLATION')) {
      formError.value = error.message
    } else {
      notification.error(error)
    }
  } finally {
    submitting.value = false
  }
}

// --- 用户菜单 ---------------------------------------------------------------
async function onUserCommand(command: string | number | object): Promise<void> {
  if (command === 'password') {
    openPasswordDialog()
    return
  }
  if (command === 'logout') {
    await auth.logout()
    notification.success('已退出登录')
    void router.replace({ name: 'login' })
  }
}
</script>

<template>
  <header class="topbar">
    <div class="topbar__left">
      <h1 class="topbar__title">{{ pageTitle }}</h1>
    </div>

    <div class="topbar__right">
      <button type="button" class="topbar__search" @click="ui.openCommandPalette()">
        <el-icon class="topbar__search-icon"><Search /></el-icon>
        <span class="topbar__search-label">搜索</span>
        <span class="topbar__kbd">{{ isMac ? '⌘K' : 'Ctrl K' }}</span>
      </button>

      <el-popover placement="bottom-end" :width="320" trigger="click" popper-class="easy-popover">
        <template #reference>
          <button type="button" class="topbar__icon-button" aria-label="通知中心">
            <el-icon><Bell /></el-icon>
            <span v-if="notification.unreadCount > 0" class="topbar__badge">{{ notification.unreadCount }}</span>
          </button>
        </template>
        <div class="popover-title">通知</div>
        <div class="popover-empty">
          <p class="popover-empty__title">暂无通知</p>
          <p class="popover-empty__hint">
            任务分配、审批待办、评论 @ 等通知将在 Phase 7 接入，并支持点击直达对应内容。
          </p>
        </div>
      </el-popover>

      <el-dropdown trigger="click" @command="onUserCommand">
        <button type="button" class="topbar__user">
          <EasyAvatar :name="auth.displayName || '?'" :src="auth.user?.avatarUrl ?? null" />
          <span class="topbar__user-name">{{ auth.displayName }}</span>
          <el-icon class="topbar__user-caret"><ArrowDown /></el-icon>
        </button>
        <template #dropdown>
          <el-dropdown-menu>
            <el-dropdown-item disabled>
              {{ auth.user?.username }} · {{ systemRoleLabel(auth.user?.systemRole) }}
            </el-dropdown-item>
            <el-dropdown-item divided command="password">修改密码</el-dropdown-item>
            <el-dropdown-item command="logout">退出登录</el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>
    </div>

    <EasyDialog v-model="passwordDialogOpen" title="修改密码" :width="440">
      <div class="password-form">
        <EasyInput
          v-model="currentPassword"
          label="当前密码"
          type="password"
          show-password
          placeholder="请输入当前登录密码"
          autocomplete="current-password"
        />
        <EasyInput
          v-model="newPassword"
          label="新密码"
          type="password"
          show-password
          placeholder="至少 10 位，需同时包含字母与数字"
          autocomplete="new-password"
        />
        <EasyInput
          v-model="confirmPassword"
          label="确认新密码"
          type="password"
          show-password
          placeholder="再次输入新密码"
          autocomplete="new-password"
        />
        <p v-if="formError" class="password-form__error" role="alert">{{ formError }}</p>
        <p class="password-form__note">修改成功后，其他设备上的登录会话将立即失效。</p>
      </div>
      <template #footer>
        <div class="dialog-footer">
          <EasyButton @click="passwordDialogOpen = false">取消</EasyButton>
          <EasyButton variant="primary" :loading="submitting" @click="submitPasswordChange">
            确认修改
          </EasyButton>
        </div>
      </template>
    </EasyDialog>
  </header>
</template>

<style scoped>
.topbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--easy-space-4);
  height: var(--easy-topbar-height);
  padding: 0 var(--easy-content-padding-x);
  background: var(--easy-surface);
  border-bottom: 1px solid var(--easy-border);
}

.topbar__title {
  font-size: var(--easy-text-lg);
  font-weight: 600;
  letter-spacing: -0.01em;
}

.topbar__right {
  display: flex;
  align-items: center;
  gap: var(--easy-space-2);
}

.topbar__search {
  display: flex;
  align-items: center;
  gap: var(--easy-space-2);
  height: 34px;
  padding: 0 var(--easy-space-3);
  border: 1px solid var(--easy-border-strong);
  border-radius: var(--easy-radius-md);
  background: var(--easy-bg);
  color: var(--easy-text-3);
  font-size: var(--easy-text-sm);
  cursor: pointer;
  transition: border-color var(--easy-transition-fast), background var(--easy-transition-fast);
}

.topbar__search:hover {
  border-color: var(--easy-text-3);
  background: var(--easy-surface);
}

.topbar__search-icon {
  font-size: 15px;
}

.topbar__search-label {
  min-width: 88px;
  text-align: left;
}

.topbar__kbd {
  padding: 1px 5px;
  border: 1px solid var(--easy-border-strong);
  border-radius: var(--easy-radius-xs);
  background: var(--easy-surface);
  font-size: 10px;
  font-family: var(--easy-font-mono);
}

.topbar__icon-button {
  position: relative;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 34px;
  height: 34px;
  border: none;
  border-radius: var(--easy-radius-md);
  background: transparent;
  color: var(--easy-text-2);
  font-size: 17px;
  cursor: pointer;
  transition: background var(--easy-transition-fast), color var(--easy-transition-fast);
}

.topbar__icon-button:hover {
  background: var(--easy-surface-hover);
  color: var(--easy-text-1);
}

.topbar__badge {
  position: absolute;
  top: 3px;
  right: 3px;
  min-width: 15px;
  height: 15px;
  padding: 0 4px;
  border-radius: var(--easy-radius-full);
  background: var(--easy-danger);
  color: #fff;
  font-size: 10px;
  line-height: 15px;
  text-align: center;
}

.topbar__user {
  display: flex;
  align-items: center;
  gap: var(--easy-space-2);
  height: 38px;
  padding: 0 var(--easy-space-2);
  border: none;
  border-radius: var(--easy-radius-md);
  background: transparent;
  color: var(--easy-text-1);
  font-size: var(--easy-text-sm);
  cursor: pointer;
  transition: background var(--easy-transition-fast);
}

.topbar__user:hover {
  background: var(--easy-surface-hover);
}

.topbar__user-name {
  max-width: 140px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.topbar__user-caret {
  font-size: 12px;
  color: var(--easy-text-3);
}

.popover-title {
  font-size: var(--easy-text-sm);
  font-weight: 600;
  padding-bottom: var(--easy-space-2);
  border-bottom: 1px solid var(--easy-border);
}

.popover-empty {
  padding: var(--easy-space-4) 0 var(--easy-space-2);
  text-align: center;
}

.popover-empty__title {
  font-size: var(--easy-text-sm);
  color: var(--easy-text-2);
}

.popover-empty__hint {
  margin-top: var(--easy-space-2);
  font-size: var(--easy-text-xs);
  color: var(--easy-text-3);
  line-height: var(--easy-leading-relaxed);
}

.password-form {
  display: flex;
  flex-direction: column;
  gap: var(--easy-space-4);
  padding-top: var(--easy-space-1);
}

.password-form__error {
  padding: var(--easy-space-2) var(--easy-space-3);
  border: 1px solid var(--easy-danger);
  border-radius: var(--easy-radius-md);
  background: var(--easy-danger-bg);
  color: var(--easy-danger);
  font-size: var(--easy-text-xs);
}

.password-form__note {
  color: var(--easy-text-3);
  font-size: var(--easy-text-xs);
}

.dialog-footer {
  display: flex;
  justify-content: flex-end;
  gap: var(--easy-space-2);
}
</style>