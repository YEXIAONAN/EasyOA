<script setup lang="ts">
import { nextTick, ref, watch } from 'vue'
import QRCode from 'qrcode'

import { ApiError } from '@/api/errors'
import { securityApi } from '@/api/modules/security'
import type { MfaEnrollment } from '@/api/types'
import EasyButton from '@/components/easy/EasyButton.vue'
import EasyDialog from '@/components/easy/EasyDialog.vue'
import EasyInput from '@/components/easy/EasyInput.vue'
import { useNotificationStore } from '@/stores/notification'

/**
 * 动态口令绑定向导。
 *
 * 交互顺序（与后端一致）：获取 Secret → 扫码 → 输入验证码确认 → 正式启用。
 * Secret 与 otpauth URI 只在本流程中出现一次，关闭即失效。
 */
const open = defineModel<boolean>({ required: true })
const emit = defineEmits<{ (e: 'bound'): void }>()

const notification = useNotificationStore()

const state = ref<'loading' | 'scan' | 'error'>('loading')
const enrollment = ref<MfaEnrollment | null>(null)
const code = ref('')
const error = ref<string | null>(null)
const submitting = ref(false)
const confirmed = ref(false)
const canvas = ref<HTMLCanvasElement | null>(null)

async function loadEnrollment(): Promise<void> {
  state.value = 'loading'
  error.value = null
  code.value = ''
  confirmed.value = false
  try {
    enrollment.value = await securityApi.startEnrollment()
    state.value = 'scan'
    await nextTick()
    await renderQr()
  } catch (e) {
    state.value = 'error'
    error.value = e instanceof ApiError ? e.message : '无法生成绑定密钥'
  }
}

async function renderQr(): Promise<void> {
  const uri = enrollment.value?.otpauthUri
  if (!canvas.value || !uri) return
  await QRCode.toCanvas(canvas.value, uri, { width: 208, margin: 1 })
}

watch(open, (value) => {
  if (value) {
    void loadEnrollment()
    return
  }
  // 关闭时若尚未确认，主动作废待确认密钥，避免数据库里残留无效 Secret
  if (!confirmed.value && enrollment.value) {
    void securityApi.cancelEnrollment().catch(() => undefined)
  }
})

async function confirm(): Promise<void> {
  error.value = null
  if (!/^\d{6}$/.test(code.value.trim())) {
    error.value = '请输入 6 位数字验证码'
    return
  }
  submitting.value = true
  try {
    await securityApi.confirmEnrollment(code.value.trim())
    confirmed.value = true
    open.value = false
    notification.success('动态口令已启用')
    emit('bound')
  } catch (e) {
    error.value = e instanceof ApiError ? e.message : '绑定失败，请重试'
  } finally {
    submitting.value = false
  }
}

function copySecret(): void {
  const secret = enrollment.value?.secret
  if (!secret) return
  void navigator.clipboard
    .writeText(secret)
    .then(() => notification.success('密钥已复制'))
    .catch(() => notification.error('复制失败，请手动选择文本'))
}
</script>

<template>
  <EasyDialog v-model="open" title="绑定动态口令" :width="460">
    <div v-if="state === 'loading'" class="mfa__hint">正在生成绑定密钥…</div>

    <div v-else-if="state === 'error'" class="mfa__error" role="alert">{{ error }}</div>

    <div v-else class="mfa">
      <ol class="mfa__steps">
        <li>在 Google / Microsoft Authenticator、1Password 等标准 App 中扫描下方二维码</li>
        <li>无法扫码时，手动输入文本密钥</li>
        <li>输入 App 显示的 6 位验证码完成绑定</li>
      </ol>

      <div class="mfa__qr">
        <canvas ref="canvas" aria-label="动态口令绑定二维码" />
      </div>

      <div class="mfa__secret">
        <span class="mfa__secret-label">文本密钥</span>
        <code class="easy-mono">{{ enrollment?.secret }}</code>
        <EasyButton size="sm" @click="copySecret">复制</EasyButton>
      </div>

      <EasyInput
        v-model="code"
        label="6 位验证码"
        placeholder="例如 123456"
        inputmode="numeric"
        maxlength="6"
        autocomplete="one-time-code"
      />

      <p v-if="error" class="mfa__error" role="alert">{{ error }}</p>
      <p class="mfa__note">绑定成功后，登录与高危操作都需要该验证码；密钥不会再次显示。</p>
    </div>

    <template #footer>
      <div class="mfa__footer">
        <EasyButton @click="open = false">取消</EasyButton>
        <EasyButton
          variant="primary"
          :loading="submitting"
          :disabled="state !== 'scan'"
          @click="confirm"
        >
          确认绑定
        </EasyButton>
      </div>
    </template>
  </EasyDialog>
</template>

<style scoped>
.mfa {
  display: flex;
  flex-direction: column;
  gap: var(--easy-space-4);
}

.mfa__steps {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding-left: 18px;
  color: var(--easy-text-2);
  font-size: var(--easy-text-sm);
  line-height: var(--easy-leading-relaxed);
  list-style: decimal;
}

.mfa__qr {
  display: flex;
  justify-content: center;
  padding: var(--easy-space-3);
  border: 1px solid var(--easy-border);
  border-radius: var(--easy-radius-lg);
  background: var(--easy-surface);
}

.mfa__secret {
  display: flex;
  align-items: center;
  gap: var(--easy-space-2);
  padding: var(--easy-space-2) var(--easy-space-3);
  border: 1px dashed var(--easy-border-strong);
  border-radius: var(--easy-radius-md);
  background: var(--easy-surface-sunken);
  font-size: var(--easy-text-xs);
  overflow-x: auto;
}

.mfa__secret-label {
  flex: none;
  color: var(--easy-text-3);
}

.mfa__secret code {
  flex: 1;
  min-width: 0;
  letter-spacing: 0.06em;
}

.mfa__hint {
  padding: var(--easy-space-6) 0;
  text-align: center;
  color: var(--easy-text-3);
  font-size: var(--easy-text-sm);
}

.mfa__error {
  padding: var(--easy-space-2) var(--easy-space-3);
  border: 1px solid var(--easy-danger);
  border-radius: var(--easy-radius-md);
  background: var(--easy-danger-bg);
  color: var(--easy-danger);
  font-size: var(--easy-text-xs);
}

.mfa__note {
  color: var(--easy-text-3);
  font-size: var(--easy-text-xs);
}

.mfa__footer {
  display: flex;
  justify-content: flex-end;
  gap: var(--easy-space-2);
}
</style>
