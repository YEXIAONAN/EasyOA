<script setup lang="ts">
import { computed, ref, watch } from 'vue'

import { ApiError } from '@/api/errors'
import { securityApi } from '@/api/modules/security'
import type { SensitiveOperationPreview, SensitiveOperationResult, SensitiveOperationType } from '@/api/types'
import EasyButton from '@/components/easy/EasyButton.vue'
import EasyDialog from '@/components/easy/EasyDialog.vue'
import EasyInput from '@/components/easy/EasyInput.vue'

/**
 * ROOT 高危操作确认弹窗。
 *
 * 严格按规范顺序收集四要素：当前密码 → 动态验证码 → 操作原因 → 最终确认短语；
 * 影响范围在执行前完整展示，认证与执行全部由后端统一裁决（前端不可信）。
 */
const props = defineProps<{
  type: SensitiveOperationType
  targetId?: number | null
  payload?: Record<string, unknown>
  /** 目标名称（例如被重置 MFA 的账号），仅用于展示 */
  targetLabel?: string | null
}>()

const emit = defineEmits<{ (e: 'executed', result: SensitiveOperationResult): void }>()

const open = defineModel<boolean>({ required: true })

const preview = ref<SensitiveOperationPreview | null>(null)
const loadingPreview = ref(false)
const loadError = ref<string | null>(null)

const currentPassword = ref('')
const totpCode = ref('')
const reason = ref('')
const confirmation = ref('')
const submitting = ref(false)
const formError = ref<string | null>(null)
const result = ref<SensitiveOperationResult | null>(null)

const REASON_MIN = 8

const canSubmit = computed(() => {
  return (
    !submitting.value &&
    currentPassword.value.length > 0 &&
    /^\d{6}$/.test(totpCode.value.trim()) &&
    reason.value.trim().length >= REASON_MIN &&
    confirmation.value.trim() === (preview.value?.confirmationPhrase ?? '')
  )
})

watch(open, (value) => {
  if (value) {
    void loadPreview()
  }
})

async function loadPreview(): Promise<void> {
  loadingPreview.value = true
  loadError.value = null
  result.value = null
  formError.value = null
  currentPassword.value = ''
  totpCode.value = ''
  reason.value = ''
  confirmation.value = ''
  try {
    preview.value = await securityApi.previewOperation({
      type: props.type,
      targetId: props.targetId ?? null,
      payload: props.payload,
    })
  } catch (e) {
    loadError.value = e instanceof ApiError ? e.message : '无法获取影响范围'
  } finally {
    loadingPreview.value = false
  }
}

async function execute(): Promise<void> {
  formError.value = null
  submitting.value = true
  try {
    const executed = await securityApi.executeOperation({
      type: props.type,
      targetId: props.targetId ?? null,
      currentPassword: currentPassword.value,
      totpCode: totpCode.value.trim(),
      reason: reason.value.trim(),
      confirmation: confirmation.value.trim(),
      payload: props.payload,
    })
    result.value = executed
    emit('executed', executed)
  } catch (e) {
    formError.value = e instanceof ApiError ? e.message : '操作失败，请重试'
  } finally {
    submitting.value = false
  }
}

function resultEntries(): Array<{ key: string; value: string }> {
  const data = result.value?.result
  if (!data) return []
  return Object.entries(data)
    .filter(([key]) => key !== 'content')
    .map(([key, value]) => ({ key, value: String(value) }))
}
</script>

<template>
  <EasyDialog v-model="open" :title="preview?.title ?? '高危操作'" :width="540">
    <div v-if="loadingPreview" class="op__hint">正在计算影响范围…</div>

    <div v-else-if="loadError" class="op__error" role="alert">{{ loadError }}</div>

    <div v-else-if="result" class="op__done">
      <div class="op__done-badge">已执行</div>
      <p class="op__done-text">{{ result.message }}</p>
      <ul class="op__done-list">
        <li v-for="entry in resultEntries()" :key="entry.key">
          <span class="op__done-key">{{ entry.key }}</span>
          <span class="easy-mono">{{ entry.value }}</span>
        </li>
      </ul>
      <p class="op__note">本次操作已写入安全事件，可在「审计日志 → 安全事件」中查看。</p>
    </div>

    <div v-else class="op">
      <p class="op__desc">{{ preview?.description }}</p>

      <div class="op__impact">
        <div class="op__impact-title">影响范围</div>
        <ul>
          <li v-for="(item, index) in preview?.impacts ?? []" :key="index">{{ item }}</li>
        </ul>
        <div v-if="props.targetLabel ?? preview?.targetLabel" class="op__target">
          操作目标：{{ props.targetLabel ?? preview?.targetLabel }}
        </div>
      </div>

      <div class="op__fields">
        <EasyInput
          v-model="currentPassword"
          label="重新输入当前登录密码"
          type="password"
          show-password
          placeholder="用于确认是本人操作"
          autocomplete="current-password"
        />
        <EasyInput
          v-model="totpCode"
          label="动态验证码"
          placeholder="6 位数字"
          inputmode="numeric"
          maxlength="6"
          autocomplete="one-time-code"
        />
        <EasyInput
          v-model="reason"
          label="操作原因"
          :hint="`不少于 ${REASON_MIN} 个字符，将写入审计与安全事件`"
          placeholder="例如：按 180 天保留期清理历史审计日志"
        />
        <EasyInput
          v-model="confirmation"
          :label="`最终确认：请输入「${preview?.confirmationPhrase}」`"
          placeholder="逐字输入上方短语"
        />
      </div>

      <p v-if="formError" class="op__error" role="alert">{{ formError }}</p>
    </div>

    <template #footer>
      <div class="op__footer">
        <EasyButton @click="open = false">{{ result ? '关闭' : '取消' }}</EasyButton>
        <EasyButton
          v-if="!result"
          variant="danger"
          :loading="submitting"
          :disabled="!canSubmit"
          @click="execute"
        >
          执行高危操作
        </EasyButton>
      </div>
    </template>
  </EasyDialog>
</template>

<style scoped>
.op {
  display: flex;
  flex-direction: column;
  gap: var(--easy-space-4);
}

.op__desc {
  color: var(--easy-text-2);
  font-size: var(--easy-text-sm);
  line-height: var(--easy-leading-relaxed);
}

.op__impact {
  padding: var(--easy-space-3) var(--easy-space-4);
  border: 1px solid var(--easy-danger);
  border-radius: var(--easy-radius-lg);
  background: var(--easy-danger-bg);
}

.op__impact-title {
  margin-bottom: var(--easy-space-2);
  font-size: var(--easy-text-sm);
  font-weight: 600;
  color: var(--easy-danger);
}

.op__impact ul {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding-left: 18px;
  list-style: disc;
  color: var(--easy-text-1);
  font-size: var(--easy-text-sm);
  line-height: var(--easy-leading-relaxed);
}

.op__target {
  margin-top: var(--easy-space-2);
  font-size: var(--easy-text-xs);
  color: var(--easy-text-2);
}

.op__fields {
  display: flex;
  flex-direction: column;
  gap: var(--easy-space-3);
}

.op__hint {
  padding: var(--easy-space-6) 0;
  text-align: center;
  color: var(--easy-text-3);
  font-size: var(--easy-text-sm);
}

.op__error {
  padding: var(--easy-space-2) var(--easy-space-3);
  border: 1px solid var(--easy-danger);
  border-radius: var(--easy-radius-md);
  background: var(--easy-danger-bg);
  color: var(--easy-danger);
  font-size: var(--easy-text-xs);
}

.op__done {
  display: flex;
  flex-direction: column;
  gap: var(--easy-space-3);
}

.op__done-badge {
  align-self: flex-start;
  padding: 2px 10px;
  border-radius: var(--easy-radius-full);
  background: var(--easy-success-bg, var(--easy-brand-subtle));
  color: var(--easy-success, var(--easy-brand-text));
  font-size: var(--easy-text-xs);
  font-weight: 600;
}

.op__done-text {
  color: var(--easy-text-1);
  font-size: var(--easy-text-sm);
}

.op__done-list {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: var(--easy-space-3);
  border: 1px solid var(--easy-border);
  border-radius: var(--easy-radius-md);
  background: var(--easy-surface-sunken);
  font-size: var(--easy-text-xs);
}

.op__done-list li {
  display: flex;
  justify-content: space-between;
  gap: var(--easy-space-4);
}

.op__done-key {
  color: var(--easy-text-3);
}

.op__note {
  color: var(--easy-text-3);
  font-size: var(--easy-text-xs);
}

.op__footer {
  display: flex;
  justify-content: flex-end;
  gap: var(--easy-space-2);
}
</style>
