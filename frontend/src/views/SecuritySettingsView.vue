<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'

import { ApiError } from '@/api/errors'
import { securityApi } from '@/api/modules/security'
import { userApi } from '@/api/modules/users'
import type {
  MemberCard,
  SecurityPolicy,
  SensitiveOperationResult,
  SensitiveOperationType,
} from '@/api/types'
import EasyButton from '@/components/easy/EasyButton.vue'
import EasyEmpty from '@/components/easy/EasyEmpty.vue'
import EasySelect from '@/components/easy/EasySelect.vue'
import SensitiveOperationDialog from '@/components/security/SensitiveOperationDialog.vue'
import { useAuthStore } from '@/stores/auth'
import { useNotificationStore } from '@/stores/notification'

/**
 * 系统设置（仅 ROOT / ADMIN）。
 *
 * 安全策略可读，但修改与所有高危操作都只能由 ROOT 通过统一高危操作通道执行：
 * 前端只负责收集参数与展示影响范围，认证与执行一律由后端裁决。
 */
const auth = useAuthStore()
const notification = useNotificationStore()

const policy = ref<SecurityPolicy | null>(null)
const loading = ref(true)
const loadError = ref<string | null>(null)

// 策略编辑表单（ROOT 提交时作为高危操作 payload）
const form = ref<SecurityPolicy>({
  loginMaxFailures: 5,
  loginLockMinutes: 15,
  passwordMinLength: 10,
  totpRequiredForAdmins: false,
})

// 危险区域
const retentionDays = ref(180)
const mfaTargetId = ref<number | null>(null)
const mfaTargetOptions = ref<Array<{ label: string; value: number }>>([])

const opOpen = ref(false)
const opType = ref<SensitiveOperationType>('AUDIT_LOG_PURGE')
const opPayload = ref<Record<string, unknown> | undefined>(undefined)
const opTargetId = ref<number | null>(null)
const opTargetLabel = ref<string | null>(null)

const isRoot = computed(() => auth.isRoot)
const policyDirty = computed(() => {
  const current = policy.value
  if (!current) return false
  return (
    current.loginMaxFailures !== form.value.loginMaxFailures ||
    current.loginLockMinutes !== form.value.loginLockMinutes ||
    current.passwordMinLength !== form.value.passwordMinLength ||
    current.totpRequiredForAdmins !== form.value.totpRequiredForAdmins
  )
})

async function load(): Promise<void> {
  loading.value = true
  loadError.value = null
  try {
    const current = await securityApi.policy()
    policy.value = current
    form.value = { ...current }
  } catch (e) {
    loadError.value = e instanceof ApiError ? e.message : '安全策略加载失败'
  } finally {
    loading.value = false
  }
}

async function loadTargets(): Promise<void> {
  if (!isRoot.value) return
  try {
    const result = await userApi.directory({ status: 'ACTIVE', size: 100 })
    mfaTargetOptions.value = result.items.map((member: MemberCard) => ({
      label: `${member.displayName}（${member.username}）`,
      value: member.id,
    }))
  } catch {
    mfaTargetOptions.value = []
  }
}

onMounted(() => {
  void load()
  void loadTargets()
})

function openOperation(
  type: SensitiveOperationType,
  options: { payload?: Record<string, unknown>; targetId?: number | null; targetLabel?: string | null } = {},
): void {
  opType.value = type
  opPayload.value = options.payload
  opTargetId.value = options.targetId ?? null
  opTargetLabel.value = options.targetLabel ?? null
  opOpen.value = true
}

function submitPolicy(): void {
  openOperation('SECURITY_POLICY_CHANGE', {
    payload: {
      loginMaxFailures: form.value.loginMaxFailures,
      loginLockMinutes: form.value.loginLockMinutes,
      passwordMinLength: form.value.passwordMinLength,
      totpRequiredForAdmins: form.value.totpRequiredForAdmins,
    },
  })
}

function submitPurge(): void {
  openOperation('AUDIT_LOG_PURGE', { payload: { retentionDays: retentionDays.value } })
}

function submitMfaReset(): void {
  const option = mfaTargetOptions.value.find((item) => item.value === mfaTargetId.value)
  openOperation('MFA_RESET', { targetId: mfaTargetId.value, targetLabel: option?.label ?? null })
}

function onExecuted(result: SensitiveOperationResult): void {
  // 导出类操作：直接把后端返回的 CSV 落盘
  const fileName = result.result?.fileName
  const content = result.result?.content
  if (typeof fileName === 'string' && typeof content === 'string') {
    downloadCsv(fileName, content)
    notification.success('审计数据已导出')
  } else {
    notification.success(result.message)
  }
  void load()
  void loadTargets()
}

function downloadCsv(fileName: string, content: string): void {
  const blob = new Blob([content], { type: 'text/csv;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = fileName
  document.body.appendChild(link)
  link.click()
  document.body.removeChild(link)
  URL.revokeObjectURL(url)
}
</script>

<template>
  <div class="easy-page">
    <header class="page-head">
      <div>
        <h1 class="page-head__title">系统设置</h1>
        <p class="page-head__desc">
          安全策略与高危操作。策略修改与数据销毁等动作仅限 ROOT，并需要密码 + 动态验证码双重确认。
        </p>
      </div>
      <EasyButton size="sm" @click="load">刷新</EasyButton>
    </header>

    <div v-if="loadError" class="easy-card list-error">
      <span>{{ loadError }}</span>
      <EasyButton size="sm" @click="load">重试</EasyButton>
    </div>

    <div v-else-if="loading" class="easy-card skeleton-block">正在加载安全策略…</div>

    <template v-else>
      <section class="easy-card section">
        <div class="section__head">
          <div>
            <h2 class="section__title">安全策略</h2>
            <p class="section__desc">策略实时生效：登录保护与密码强度在每次校验时读取当前值。</p>
          </div>
        </div>

        <div class="policy-grid">
          <label class="num-field">
            <span class="num-field__label">登录失败次数上限</span>
            <el-input-number
              v-model="form.loginMaxFailures"
              :min="3"
              :max="20"
              :disabled="!isRoot"
              controls-position="right"
            />
            <span class="num-field__hint">3 ~ 20</span>
          </label>
          <label class="num-field">
            <span class="num-field__label">锁定时长（分钟）</span>
            <el-input-number
              v-model="form.loginLockMinutes"
              :min="1"
              :max="1440"
              :disabled="!isRoot"
              controls-position="right"
            />
            <span class="num-field__hint">1 ~ 1440</span>
          </label>
          <label class="num-field">
            <span class="num-field__label">密码最小长度</span>
            <el-input-number
              v-model="form.passwordMinLength"
              :min="8"
              :max="64"
              :disabled="!isRoot"
              controls-position="right"
            />
            <span class="num-field__hint">8 ~ 64</span>
          </label>
          <div class="policy-toggle">
            <label class="policy-toggle__label">管理员必须绑定动态口令</label>
            <el-switch v-model="form.totpRequiredForAdmins" :disabled="!isRoot" />
            <span class="policy-toggle__hint">开启后，未绑定动态口令的 ROOT / ADMIN 将无法登录</span>
          </div>
        </div>

        <div class="section__actions">
          <template v-if="isRoot">
            <EasyButton variant="primary" :disabled="!policyDirty" @click="submitPolicy">
              提交修改
            </EasyButton>
            <span v-if="policyDirty" class="section__dirty">存在未保存的修改</span>
          </template>
          <p v-else class="section__readonly">仅 ROOT 可修改安全策略；ADMIN 仅可查看。</p>
        </div>
      </section>

      <section v-if="isRoot" class="easy-card section danger">
        <div class="section__head">
          <div>
            <h2 class="section__title danger__title">ROOT 高危操作</h2>
            <p class="section__desc">
              每项操作都需要：重新输入当前密码 → 动态验证码 → 操作原因 → 查看影响范围 → 最终确认；
              执行结果会永久写入安全事件。
            </p>
          </div>
        </div>

        <div class="op-grid">
          <article class="op-card">
            <h3>审计日志清理</h3>
            <p>按保留期限永久删除历史审计日志（安全事件不受影响）。</p>
            <label class="num-field op-card__field">
              <span class="num-field__label">保留天数</span>
              <el-input-number
                v-model="retentionDays"
                :min="7"
                :max="3650"
                controls-position="right"
              />
              <span class="num-field__hint">默认 180 天，最少 7 天</span>
            </label>
            <EasyButton size="sm" @click="submitPurge">开始清理</EasyButton>
          </article>

          <article class="op-card">
            <h3>敏感数据导出</h3>
            <p>导出全部审计日志为 CSV（含操作者、IP、请求 ID），导出行为会被留痕。</p>
            <EasyButton size="sm" @click="openOperation('SENSITIVE_DATA_EXPORT')">导出审计数据</EasyButton>
          </article>

          <article class="op-card">
            <h3>管理员 MFA 重置</h3>
            <p>清除指定账号的动态口令并撤销其全部会话，用于成员更换设备。</p>
            <EasySelect
              v-model="mfaTargetId"
              class="op-card__field"
              label="目标账号"
              :options="mfaTargetOptions"
              placeholder="选择账号"
            />
            <EasyButton size="sm" :disabled="mfaTargetId === null" @click="submitMfaReset">
              重置 MFA
            </EasyButton>
          </article>

          <article class="op-card op-card--destructive">
            <h3>系统核心数据销毁</h3>
            <p>永久删除全部业务数据（项目 / 任务 / 审批 / 评论 / 附件元数据 / 通知），保留账号与组织架构。</p>
            <EasyButton size="sm" variant="danger" @click="openOperation('DATA_DESTRUCTION')">
              销毁业务数据
            </EasyButton>
          </article>
        </div>
      </section>

      <section v-else class="easy-card section">
        <EasyEmpty
          compact
          title="高危操作通道仅对 ROOT 开放"
          description="审计日志清理、数据销毁、MFA 重置与敏感数据导出需要 ROOT 身份与动态口令双重确认。"
        />
      </section>
    </template>

    <SensitiveOperationDialog
      v-model="opOpen"
      :type="opType"
      :target-id="opTargetId"
      :target-label="opTargetLabel"
      :payload="opPayload"
      @executed="onExecuted"
    />
  </div>
</template>

<style scoped>
.page-head {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: var(--easy-space-4);
  flex-wrap: wrap;
}

.page-head__title {
  font-size: var(--easy-text-2xl);
  font-weight: 600;
  letter-spacing: -0.02em;
}

.page-head__desc {
  margin-top: var(--easy-space-1);
  max-width: 720px;
  color: var(--easy-text-3);
  font-size: var(--easy-text-sm);
  line-height: var(--easy-leading-relaxed);
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

.section__title {
  font-size: var(--easy-text-lg);
  font-weight: 600;
}

.section__desc {
  margin-top: var(--easy-space-1);
  max-width: 760px;
  color: var(--easy-text-3);
  font-size: var(--easy-text-sm);
  line-height: var(--easy-leading-relaxed);
}

.section__actions {
  display: flex;
  align-items: center;
  gap: var(--easy-space-3);
}

.section__dirty {
  color: var(--easy-warning);
  font-size: var(--easy-text-xs);
}

.section__readonly {
  color: var(--easy-text-3);
  font-size: var(--easy-text-sm);
}

.policy-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
  gap: var(--easy-space-4);
}

.num-field {
  display: flex;
  flex-direction: column;
  gap: var(--easy-space-2);
}

.num-field__label {
  font-size: var(--easy-text-sm);
  font-weight: 500;
  color: var(--easy-text-2);
}

.num-field__hint {
  font-size: var(--easy-text-xs);
  color: var(--easy-text-3);
}

.num-field :deep(.el-input-number) {
  width: 100%;
}

.policy-toggle {
  display: flex;
  flex-direction: column;
  gap: var(--easy-space-2);
}

.policy-toggle__label {
  font-size: var(--easy-text-sm);
  font-weight: 500;
  color: var(--easy-text-2);
}

.policy-toggle__hint {
  font-size: var(--easy-text-xs);
  color: var(--easy-text-3);
}

.danger {
  border-color: var(--easy-danger);
}

.danger__title {
  color: var(--easy-danger);
}

.op-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(260px, 1fr));
  gap: var(--easy-space-4);
}

.op-card {
  display: flex;
  flex-direction: column;
  gap: var(--easy-space-3);
  align-items: flex-start;
  padding: var(--easy-space-4);
  border: 1px solid var(--easy-border);
  border-radius: var(--easy-radius-lg);
  background: var(--easy-surface);
}

.op-card--destructive {
  border-color: var(--easy-danger);
  background: var(--easy-danger-bg);
}

.op-card h3 {
  font-size: var(--easy-text-base);
  font-weight: 600;
}

.op-card p {
  color: var(--easy-text-3);
  font-size: var(--easy-text-xs);
  line-height: var(--easy-leading-relaxed);
}

.op-card__field {
  width: 100%;
}
</style>
