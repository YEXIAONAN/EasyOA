<script setup lang="ts">
import { onMounted, ref } from 'vue'

import { ApiError } from '@/api/errors'
import { authApi } from '@/api/modules/auth'
import { securityApi } from '@/api/modules/security'
import type { MfaStatus, SessionSummary } from '@/api/types'
import EasyButton from '@/components/easy/EasyButton.vue'
import EasyDialog from '@/components/easy/EasyDialog.vue'
import EasyEmpty from '@/components/easy/EasyEmpty.vue'
import EasyInput from '@/components/easy/EasyInput.vue'
import EasyStatus from '@/components/easy/EasyStatus.vue'
import MfaEnrollmentDialog from '@/components/security/MfaEnrollmentDialog.vue'
import { useAuthStore } from '@/stores/auth'
import { useNotificationStore } from '@/stores/notification'
import { formatDateTime, formatRelative } from '@/utils/format'

/**
 * 安全中心（所有登录用户）：动态口令自助管理 + 登录设备管理。
 *
 * 系统设置（安全策略 / ROOT 高危操作）在「系统设置」中，仅管理员可见。
 */
const auth = useAuthStore()
const notification = useNotificationStore()

const mfa = ref<MfaStatus | null>(null)
const sessions = ref<SessionSummary[]>([])
const loading = ref(true)
const loadError = ref<string | null>(null)

const enrollOpen = ref(false)
const disableOpen = ref(false)
const disablePassword = ref('')
const disableCode = ref('')
const disableError = ref<string | null>(null)
const submitting = ref(false)

async function load(): Promise<void> {
  loading.value = true
  loadError.value = null
  try {
    const [status, activeSessions] = await Promise.all([
      securityApi.mfaStatus(),
      authApi.sessions().catch(() => [] as SessionSummary[]),
    ])
    mfa.value = status
    sessions.value = activeSessions
  } catch (e) {
    loadError.value = e instanceof ApiError ? e.message : '安全信息加载失败'
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  void load()
})

async function submitDisable(): Promise<void> {
  disableError.value = null
  if (!disablePassword.value) {
    disableError.value = '请输入当前密码'
    return
  }
  if (!/^\d{6}$/.test(disableCode.value.trim())) {
    disableError.value = '请输入 6 位动态验证码'
    return
  }
  submitting.value = true
  try {
    mfa.value = await securityApi.disableMfa({
      currentPassword: disablePassword.value,
      code: disableCode.value.trim(),
    })
    disableOpen.value = false
    disablePassword.value = ''
    disableCode.value = ''
    notification.success('动态口令已解绑')
  } catch (e) {
    disableError.value = e instanceof ApiError ? e.message : '解绑失败，请重试'
  } finally {
    submitting.value = false
  }
}

async function revoke(session: SessionSummary): Promise<void> {
  try {
    await authApi.revokeSession(session.id)
    sessions.value = sessions.value.filter((item) => item.id !== session.id)
    notification.success('该设备已下线')
  } catch (e) {
    notification.error(e)
  }
}
</script>

<template>
  <div class="easy-page">
    <header class="page-head">
      <div>
        <h1 class="page-head__title">安全中心</h1>
        <p class="page-head__desc">管理你的动态口令与登录设备；所有安全动作都会写入审计日志。</p>
      </div>
    </header>

    <div v-if="loadError" class="easy-card list-error">
      <span>{{ loadError }}</span>
      <EasyButton size="sm" @click="load">重试</EasyButton>
    </div>

    <div v-else-if="loading" class="easy-card skeleton-block">正在加载安全信息…</div>

    <template v-else>
      <section class="easy-card section">
        <div class="section__head">
          <div>
            <h2 class="section__title">动态口令（TOTP）</h2>
            <p class="section__desc">
              标准 TOTP，兼容 Google / Microsoft Authenticator、1Password 等应用；启用后登录需额外输入验证码。
            </p>
          </div>
          <EasyStatus
            :label="mfa?.enabled ? '已启用' : mfa?.pendingEnrollment ? '待确认' : '未启用'"
            :tone="mfa?.enabled ? 'success' : mfa?.pendingEnrollment ? 'warning' : 'neutral'"
          />
        </div>

        <p v-if="mfa?.required" class="section__alert" role="alert">
          当前系统要求管理员必须绑定动态口令，请尽快完成绑定。
        </p>

        <div class="section__actions">
          <template v-if="mfa?.enabled">
            <EasyButton @click="disableOpen = true">解绑动态口令</EasyButton>
          </template>
          <template v-else>
            <EasyButton variant="primary" @click="enrollOpen = true">绑定动态口令</EasyButton>
          </template>
        </div>
      </section>

      <section class="easy-card section">
        <div class="section__head">
          <div>
            <h2 class="section__title">登录设备</h2>
            <p class="section__desc">当前账号的活跃会话；非本人设备请立即下线并修改密码。</p>
          </div>
          <div class="section__meta">
            <span>上次登录 {{ formatDateTime(auth.user?.lastLoginAt) }}</span>
            <span>{{ formatRelative(auth.user?.lastLoginAt) }}</span>
          </div>
        </div>

        <ul v-if="sessions.length > 0" class="session-list">
          <li v-for="session in sessions" :key="session.id" class="session-row">
            <div class="session-row__main">
              <div class="session-row__title">
                {{ session.current ? '当前设备' : '其他设备' }}
                <EasyStatus v-if="session.current" label="本次登录" tone="brand" />
              </div>
              <div class="session-row__meta">
                <span class="easy-mono">{{ session.ipAddress ?? '未知 IP' }}</span>
                <span class="session-row__ua">{{ session.userAgent ?? '未知客户端' }}</span>
              </div>
              <div class="session-row__meta">
                <span>最近活动 {{ formatRelative(session.lastSeenAt) }}</span>
                <span>过期于 {{ formatDateTime(session.expiresAt) }}</span>
              </div>
            </div>
            <EasyButton v-if="!session.current" size="sm" @click="revoke(session)">下线</EasyButton>
          </li>
        </ul>
        <EasyEmpty v-else compact title="没有其他活跃设备" description="当你从其他浏览器登录时，会在这里显示。" />
      </section>
    </template>

    <MfaEnrollmentDialog v-model="enrollOpen" @bound="load" />

    <EasyDialog v-model="disableOpen" title="解绑动态口令" :width="420">
      <div class="disable">
        <p class="disable__hint">解绑后将降低账号安全等级：登录只需密码。需要当前密码与动态验证码双重确认。</p>
        <EasyInput
          v-model="disablePassword"
          label="当前密码"
          type="password"
          show-password
          autocomplete="current-password"
        />
        <EasyInput v-model="disableCode" label="动态验证码" placeholder="6 位数字" maxlength="6" />
        <p v-if="disableError" class="disable__error" role="alert">{{ disableError }}</p>
      </div>
      <template #footer>
        <div class="dialog-footer">
          <EasyButton @click="disableOpen = false">取消</EasyButton>
          <EasyButton variant="danger" :loading="submitting" @click="submitDisable">确认解绑</EasyButton>
        </div>
      </template>
    </EasyDialog>
  </div>
</template>

<style scoped>
.page-head__title {
  font-size: var(--easy-text-2xl);
  font-weight: 600;
  letter-spacing: -0.02em;
}

.page-head__desc {
  margin-top: var(--easy-space-1);
  color: var(--easy-text-3);
  font-size: var(--easy-text-sm);
}

.list-error,
.skeleton-block {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--easy-space-4);
  padding: var(--easy-space-4) var(--easy-space-5);
  font-size: var(--easy-text-sm);
}

.list-error {
  color: var(--easy-danger);
}

.skeleton-block {
  color: var(--easy-text-3);
}

.section {
  display: flex;
  flex-direction: column;
  gap: var(--easy-space-4);
  padding: var(--easy-space-5);
}

.section__head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--easy-space-4);
}

.section__title {
  font-size: var(--easy-text-lg);
  font-weight: 600;
}

.section__desc {
  margin-top: var(--easy-space-1);
  max-width: 640px;
  color: var(--easy-text-3);
  font-size: var(--easy-text-sm);
  line-height: var(--easy-leading-relaxed);
}

.section__alert {
  padding: var(--easy-space-2) var(--easy-space-3);
  border: 1px solid var(--easy-warning);
  border-radius: var(--easy-radius-md);
  background: var(--easy-warning-bg, var(--easy-surface-sunken));
  color: var(--easy-warning);
  font-size: var(--easy-text-xs);
}

.section__meta {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 2px;
  flex: none;
  color: var(--easy-text-3);
  font-size: var(--easy-text-xs);
}

.section__actions {
  display: flex;
  gap: var(--easy-space-2);
}

.session-list {
  display: flex;
  flex-direction: column;
  border: 1px solid var(--easy-border);
  border-radius: var(--easy-radius-lg);
  overflow: hidden;
}

.session-row {
  display: flex;
  align-items: center;
  gap: var(--easy-space-4);
  padding: var(--easy-space-3) var(--easy-space-4);
  border-bottom: 1px solid var(--easy-border);
}

.session-row:last-child {
  border-bottom: none;
}

.session-row__main {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.session-row__title {
  display: flex;
  align-items: center;
  gap: var(--easy-space-2);
  font-size: var(--easy-text-sm);
  font-weight: 500;
}

.session-row__meta {
  display: flex;
  align-items: center;
  gap: var(--easy-space-4);
  color: var(--easy-text-3);
  font-size: var(--easy-text-xs);
  flex-wrap: wrap;
}

.session-row__ua {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  max-width: 420px;
}

.disable {
  display: flex;
  flex-direction: column;
  gap: var(--easy-space-3);
}

.disable__hint {
  color: var(--easy-text-2);
  font-size: var(--easy-text-sm);
  line-height: var(--easy-leading-relaxed);
}

.disable__error {
  padding: var(--easy-space-2) var(--easy-space-3);
  border: 1px solid var(--easy-danger);
  border-radius: var(--easy-radius-md);
  background: var(--easy-danger-bg);
  color: var(--easy-danger);
  font-size: var(--easy-text-xs);
}

.dialog-footer {
  display: flex;
  justify-content: flex-end;
  gap: var(--easy-space-2);
}
</style>
