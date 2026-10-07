<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowLeft } from '@element-plus/icons-vue'

import { ApiError } from '@/api/errors'
import { approvalApi } from '@/api/modules/approvals'
import { userApi } from '@/api/modules/users'
import type { ApprovalDetail, FileMeta, FormFieldDef } from '@/api/types'
import ApprovalForm from '@/components/approval/ApprovalForm.vue'
import EasyAvatar from '@/components/easy/EasyAvatar.vue'
import EasyButton from '@/components/easy/EasyButton.vue'
import EasyDialog from '@/components/easy/EasyDialog.vue'
import EasyInput from '@/components/easy/EasyInput.vue'
import EasySelect from '@/components/easy/EasySelect.vue'
import EasyStatus from '@/components/easy/EasyStatus.vue'
import { confirmAction } from '@/components/easy/easyConfirm'
import { useNotificationStore } from '@/stores/notification'
import { approvalActionLabel, approvalStatusLabel, approvalStatusTone, approverRuleLabel, approverStatusLabel, buildFormValues, nodeModeLabel } from '@/utils/approval'
import { formatDateTime, formatFileSize } from '@/utils/format'

/**
 * 审批详情：流程时间线 + 表单快照 + 审批历史 + 操作区。
 *
 * 流程展示只使用业务语言（申请人 / 节点名称 / 审批中 / 等待），不暴露 Node ID；
 * 同意、拒绝（必填原因）、退回（必填原因）都在本页完成——不提供一键批准。
 */
const route = useRoute()
const router = useRouter()
const notification = useNotificationStore()

const instanceId = computed(() => Number(route.params.id))
const detail = ref<ApprovalDetail | null>(null)
const loading = ref(false)
const loadError = ref<string | null>(null)

const members = ref<Array<{ label: string; value: number }>>([])
const editableValues = ref<Record<string, unknown>>({})
const editing = ref(false)
const savingForm = ref(false)
const submitting = ref(false)

const reviewOpen = ref(false)
const reviewAction = ref<'reject' | 'return'>('reject')
const reviewComment = ref('')

const transferOpen = ref(false)
const transferFrom = ref<number | null>(null)
const transferTo = ref<number | null>(null)
const transferComment = ref('')

async function load(): Promise<void> {
  loading.value = true
  loadError.value = null
  try {
    detail.value = await approvalApi.detail(instanceId.value)
    editableValues.value = { ...detail.value.formValues }
  } catch (error) {
    loadError.value = error instanceof ApiError ? error.message : '审批加载失败'
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  void load()
  void ensureMembers()
})

async function ensureMembers(): Promise<void> {
  if (members.value.length > 0) return
  try {
    const directory = await userApi.directory({ size: 100 })
    members.value = directory.items
      .filter((member) => member.status === 'ACTIVE')
      .map((member) => ({ label: member.displayName, value: member.id }))
  } catch {
    // 成员目录仅用于展示与转交选择，失败不阻塞详情
  }
}

const currentApprovers = computed(() =>
  (detail.value?.nodes ?? []).find((node) => node.current)?.approvers.filter((item) => item.status === 'PENDING') ?? [],
)

const transferFromOptions = computed(() =>
  currentApprovers.value.map((item) => ({ label: item.user.displayName, value: item.user.id })),
)
const transferToOptions = computed(() =>
  members.value.filter((member) => member.value !== detail.value?.applicant.id),
)

// --- 表单编辑与提交 ---------------------------------------------------------------

function startEdit(): void {
  editing.value = true
  editableValues.value = { ...(detail.value?.formValues ?? {}) }
}

async function saveForm(): Promise<void> {
  if (!detail.value) return
  savingForm.value = true
  try {
    detail.value = await approvalApi.updateForm(
      detail.value.id,
      buildFormValues(detail.value.formFields, editableValues.value),
    )
    editableValues.value = { ...detail.value.formValues }
    editing.value = false
    notification.success('表单已保存')
  } catch (error) {
    notification.error(error)
  } finally {
    savingForm.value = false
  }
}

async function submitApproval(): Promise<void> {
  if (!detail.value) return
  submitting.value = true
  try {
    detail.value = await approvalApi.submit(detail.value.id)
    editableValues.value = { ...detail.value.formValues }
    editing.value = false
    notification.success('审批已提交，等待审批人处理')
  } catch (error) {
    notification.error(error)
  } finally {
    submitting.value = false
  }
}

// --- 审批动作 -------------------------------------------------------------------

async function approve(): Promise<void> {
  if (!detail.value) return
  try {
    detail.value = await approvalApi.approve(detail.value.id)
    notification.success('已同意')
  } catch (error) {
    notification.error(error)
  }
}

function openReview(action: 'reject' | 'return'): void {
  reviewAction.value = action
  reviewComment.value = ''
  reviewOpen.value = true
}

async function confirmReview(): Promise<void> {
  if (!detail.value) return
  if (!reviewComment.value.trim()) {
    notification.warning(reviewAction.value === 'reject' ? '请填写拒绝原因' : '请填写退回原因')
    return
  }
  try {
    detail.value =
      reviewAction.value === 'reject'
        ? await approvalApi.reject(detail.value.id, reviewComment.value.trim())
        : await approvalApi.returnForReview(detail.value.id, reviewComment.value.trim())
    reviewOpen.value = false
    notification.success(reviewAction.value === 'reject' ? '已拒绝' : '已退回，等待申请人修改后重新提交')
  } catch (error) {
    notification.error(error)
  }
}

async function withdraw(): Promise<void> {
  if (!detail.value) return
  const confirmed = await confirmAction({
    title: '撤回申请',
    message: '撤回后申请将终止（CANCELLED），如需继续请重新发起。确定撤回吗？',
    confirmText: '撤回',
    danger: true,
  })
  if (!confirmed) return
  try {
    detail.value = await approvalApi.withdraw(detail.value.id)
    notification.success('已撤回申请')
  } catch (error) {
    notification.error(error)
  }
}

async function confirmTransfer(): Promise<void> {
  if (!detail.value || !transferFrom.value || !transferTo.value) {
    notification.warning('请选择原审批人与转交对象')
    return
  }
  try {
    detail.value = await approvalApi.transfer(
      detail.value.id,
      transferFrom.value,
      transferTo.value,
      transferComment.value.trim() || undefined,
    )
    transferOpen.value = false
    notification.success('已转交（写入审计）')
  } catch (error) {
    notification.error(error)
  }
}

// --- 表单值展示 -----------------------------------------------------------------

function fieldValue(field: FormFieldDef): string {
  const value = detail.value?.formValues?.[field.key]
  if (value === null || value === undefined || value === '') return '—'
  if (field.type === 'USER') {
    const member = members.value.find((item) => item.value === Number(value))
    return member?.label ?? `#${value}`
  }
  if (field.type === 'DATE' || field.type === 'DATETIME') return formatDateTime(String(value))
  if (field.type === 'MONEY') return `¥ ${value}`
  if (Array.isArray(value)) {
    if (field.type === 'ATTACHMENT') {
      return value
        .map((id) => attachmentName(Number(id)))
        .join('、')
    }
    return value.join('、')
  }
  return String(value)
}

function attachmentName(fileId: number): string {
  const file = (detail.value?.attachments ?? []).find((item) => item.id === fileId)
  return file?.originalName ?? `附件 #${fileId}`
}

function isAttachmentField(field: FormFieldDef): boolean {
  return field.type === 'ATTACHMENT'
}

function fieldAttachments(field: FormFieldDef): FileMeta[] {
  const ids = (detail.value?.formValues?.[field.key] as number[] | undefined) ?? []
  return (detail.value?.attachments ?? []).filter((file) => ids.includes(file.id))
}
</script>

<template>
  <div class="easy-page">
    <div v-if="loadError" class="easy-card error-bar">
      <span>{{ loadError }}</span>
      <div class="error-bar__actions">
        <EasyButton size="sm" @click="router.push({ name: 'approvals' })">返回审批列表</EasyButton>
        <EasyButton size="sm" variant="primary" @click="load">重试</EasyButton>
      </div>
    </div>

    <div v-else-if="loading && !detail" class="easy-card skeleton-block">正在加载审批…</div>

    <template v-else-if="detail">
      <header class="approval-header">
        <div class="approval-header__left">
          <button type="button" class="back-link" @click="router.push({ name: 'approvals' })">
            <el-icon><ArrowLeft /></el-icon>
            审批列表
          </button>
          <div class="approval-header__title">
            <h1 class="approval-header__name">{{ detail.title }}</h1>
            <EasyStatus :label="approvalStatusLabel(detail.status)" :tone="approvalStatusTone(detail.status)" />
          </div>
          <p class="approval-header__meta">
            {{ detail.templateName }}（v{{ detail.templateVersionNo }}）· 申请人
            {{ detail.applicant.displayName }} · 提交于
            {{ formatDateTime(detail.submittedAt ?? detail.createdAt) }}
          </p>
        </div>

        <div class="approval-header__actions">
          <template v-if="detail.permissions.canApprove">
            <EasyButton variant="primary" @click="approve">同意</EasyButton>
          </template>
          <template v-if="detail.permissions.canReject">
            <EasyButton @click="openReview('reject')">拒绝</EasyButton>
          </template>
          <template v-if="detail.permissions.canReturn">
            <EasyButton @click="openReview('return')">退回修改</EasyButton>
          </template>
          <template v-if="detail.permissions.canEditForm">
            <EasyButton v-if="!editing" @click="startEdit">编辑表单</EasyButton>
            <template v-else>
              <EasyButton :loading="savingForm" @click="saveForm">保存表单</EasyButton>
              <EasyButton @click="editing = false">取消编辑</EasyButton>
            </template>
          </template>
          <template v-if="detail.permissions.canSubmit">
            <EasyButton variant="primary" :loading="submitting" @click="submitApproval">
              {{ detail.status === 'RETURNED' ? '重新提交审批' : '提交审批' }}
            </EasyButton>
          </template>
          <template v-if="detail.permissions.canWithdraw">
            <EasyButton @click="withdraw">撤回</EasyButton>
          </template>
          <template v-if="detail.permissions.canTransfer">
            <EasyButton
              @click="
                transferOpen = true;
                transferFrom = currentApprovers[0]?.user.id ?? null;
                transferTo = null;
                transferComment = ''
              "
            >
              转交
            </EasyButton>
          </template>
        </div>
      </header>

      <div v-if="detail.status === 'RETURNED'" class="return-banner">
        申请已被退回：修改表单后重新提交，将<strong>从第一个审批节点重新审批</strong>（不会从退回节点继续）。
      </div>

      <div class="approval-body">
        <!-- 流程 -->
        <section class="easy-card">
          <div class="easy-card__header">
            <span class="easy-card__title">审批流程</span>
            <span class="easy-text-xs easy-muted">
              {{ detail.nodes.length > 0 ? `共 ${detail.nodes.length} 个审批节点` : '尚未提交' }}
            </span>
          </div>
          <div class="easy-card__body flow">
            <div class="flow-node flow-node--done">
              <span class="flow-node__dot">✓</span>
              <div class="flow-node__body">
                <span class="flow-node__name">申请人 · {{ detail.applicant.displayName }}</span>
                <span class="flow-node__hint">{{ formatDateTime(detail.submittedAt ?? detail.createdAt) }} 提交</span>
              </div>
            </div>
            <div
              v-for="node in detail.nodes"
              :key="node.index"
              class="flow-node"
              :class="{
                'flow-node--current': node.current,
                'flow-node--done': node.status === 'APPROVED',
                'flow-node--rejected': node.status === 'REJECTED',
              }"
            >
              <span class="flow-node__dot">
                {{ node.status === 'APPROVED' ? '✓' : node.status === 'REJECTED' ? '✕' : node.current ? '●' : '○' }}
              </span>
              <div class="flow-node__body">
                <span class="flow-node__name">
                  {{ node.name }}
                  <span class="easy-text-xs easy-muted">（{{ nodeModeLabel(node.mode) }}）</span>
                </span>
                <div class="flow-node__approvers">
                  <span v-for="approver in node.approvers" :key="approver.user.id" class="flow-approver">
                    <EasyAvatar :name="approver.user.displayName" :src="approver.user.avatarUrl ?? null" size="sm" />
                    {{ approver.user.displayName }}
                    <span class="flow-approver__status">{{ approverStatusLabel(approver.status) }}</span>
                    <span class="flow-approver__rule">{{ approverRuleLabel(approver.ruleType) }}</span>
                    <span v-if="approver.transferredIn" class="flow-approver__rule">转交</span>
                    <span v-if="approver.comment" class="flow-approver__comment">「{{ approver.comment }}」</span>
                  </span>
                </div>
                <span v-if="node.current" class="flow-node__hint">审批中</span>
              </div>
            </div>
          </div>
        </section>

        <!-- 表单快照 -->
        <section class="easy-card">
          <div class="easy-card__header">
            <span class="easy-card__title">申请表单（提交时的版本快照）</span>
          </div>
          <div class="easy-card__body">
            <ApprovalForm
              v-if="editing && detail.permissions.canEditForm"
              v-model="editableValues"
              :fields="detail.formFields"
              :members="members"
            />
            <div v-else class="form-readonly">
              <div v-for="field in detail.formFields" :key="field.key" class="form-row">
                <span class="form-row__label">{{ field.label }}</span>
                <template v-if="isAttachmentField(field)">
                  <span class="form-row__value">
                    <span v-if="fieldAttachments(field).length === 0">—</span>
                    <a
                      v-for="file in fieldAttachments(field)"
                      :key="file.id"
                      class="form-row__file"
                      :href="file.downloadUrl"
                    >
                      {{ file.originalName }}（{{ formatFileSize(file.size) }}）
                    </a>
                  </span>
                </template>
                <span v-else class="form-row__value">{{ fieldValue(field) }}</span>
              </div>
            </div>
          </div>
        </section>

        <!-- 审批历史 -->
        <section class="easy-card">
          <div class="easy-card__header"><span class="easy-card__title">审批历史</span></div>
          <div class="easy-card__body history">
            <div v-if="detail.actions.length === 0" class="easy-text-xs easy-muted">暂无记录</div>
            <div v-for="(action, index) in detail.actions" :key="index" class="history-row">
              <span class="history-row__time">{{ formatDateTime(action.createdAt) }}</span>
              <span class="history-row__actor">{{ action.actor.displayName }}</span>
              <span class="history-row__action">{{ approvalActionLabel(action.action) }}</span>
              <span v-if="action.comment" class="history-row__comment">「{{ action.comment }}」</span>
            </div>
          </div>
        </section>
      </div>

      <!-- 拒绝 / 退回 -->
      <EasyDialog
        v-model="reviewOpen"
        :title="reviewAction === 'reject' ? '拒绝申请' : '退回修改'"
        :width="480"
      >
        <div class="form-stack">
          <p v-if="reviewAction === 'return'" class="easy-text-xs easy-muted">
            退回后申请人修改表单重新提交，将从第一个审批节点重新审批。
          </p>
          <EasyInput
            v-model="reviewComment"
            :label="reviewAction === 'reject' ? '拒绝原因（必填）' : '退回原因（必填）'"
            type="textarea"
            :rows="3"
          />
          <div class="form-stack__actions">
            <EasyButton variant="primary" @click="confirmReview">
              {{ reviewAction === 'reject' ? '确认拒绝' : '确认退回' }}
            </EasyButton>
          </div>
        </div>
      </EasyDialog>

      <!-- 转交 -->
      <EasyDialog v-model="transferOpen" title="转交审批" :width="480">
        <div class="form-stack">
          <EasySelect v-model="transferFrom" label="原审批人（当前节点待审批人）" :options="transferFromOptions" :clearable="false" />
          <EasySelect v-model="transferTo" label="转交给" :options="transferToOptions" :clearable="false" />
          <EasyInput v-model="transferComment" label="转交说明（写入审计）" type="textarea" :rows="2" />
          <div class="form-stack__actions">
            <EasyButton variant="primary" @click="confirmTransfer">确认转交</EasyButton>
          </div>
        </div>
      </EasyDialog>
    </template>
  </div>
</template>

<style scoped>
.error-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--easy-space-4);
  padding: var(--easy-space-4) var(--easy-space-5);
  color: var(--easy-danger);
  font-size: var(--easy-text-sm);
}

.error-bar__actions {
  display: flex;
  gap: var(--easy-space-2);
}

.skeleton-block {
  padding: var(--easy-space-6);
  color: var(--easy-text-3);
  font-size: var(--easy-text-sm);
}

.approval-header {
  display: flex;
  justify-content: space-between;
  gap: var(--easy-space-6);
  flex-wrap: wrap;
}

.back-link {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  margin-bottom: var(--easy-space-2);
  border: none;
  background: transparent;
  color: var(--easy-text-3);
  font-size: var(--easy-text-sm);
  cursor: pointer;
  padding: 0;
}

.back-link:hover {
  color: var(--easy-text-1);
}

.approval-header__title {
  display: flex;
  align-items: center;
  gap: var(--easy-space-3);
  flex-wrap: wrap;
}

.approval-header__name {
  font-size: var(--easy-text-2xl);
  font-weight: 600;
  letter-spacing: -0.02em;
}

.approval-header__meta {
  margin-top: var(--easy-space-2);
  color: var(--easy-text-3);
  font-size: var(--easy-text-sm);
}

.approval-header__actions {
  display: flex;
  gap: var(--easy-space-2);
  flex-wrap: wrap;
  align-content: flex-start;
}

.return-banner {
  padding: var(--easy-space-3) var(--easy-space-4);
  border: 1px solid var(--easy-warning);
  border-radius: var(--easy-radius-md);
  background: var(--easy-warning-bg);
  color: var(--easy-warning);
  font-size: var(--easy-text-sm);
}

.approval-body {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 1fr);
  gap: var(--easy-space-4);
  align-items: start;
}

@media (max-width: 1100px) {
  .approval-body {
    grid-template-columns: minmax(0, 1fr);
  }
}

/* --- 流程 --- */
.flow {
  display: flex;
  flex-direction: column;
  gap: var(--easy-space-1);
}

.flow-node {
  display: flex;
  gap: var(--easy-space-3);
  padding: var(--easy-space-2) 0;
}

.flow-node__dot {
  width: 22px;
  height: 22px;
  flex: none;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: var(--easy-radius-full);
  border: 1px solid var(--easy-border);
  color: var(--easy-text-3);
  font-size: 12px;
}

.flow-node--done .flow-node__dot {
  border-color: var(--easy-success);
  color: var(--easy-success);
}

.flow-node--current .flow-node__dot {
  border-color: var(--easy-brand);
  color: var(--easy-brand-text);
}

.flow-node--rejected .flow-node__dot {
  border-color: var(--easy-danger);
  color: var(--easy-danger);
}

.flow-node__body {
  display: flex;
  flex-direction: column;
  gap: 4px;
  min-width: 0;
}

.flow-node__name {
  font-size: var(--easy-text-sm);
  font-weight: 500;
}

.flow-node__hint {
  font-size: var(--easy-text-xs);
  color: var(--easy-text-3);
}

.flow-node__approvers {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.flow-approver {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: var(--easy-text-xs);
  color: var(--easy-text-2);
  flex-wrap: wrap;
}

.flow-approver__status {
  color: var(--easy-text-3);
}

.flow-approver__rule {
  color: var(--easy-text-3);
  border: 1px solid var(--easy-border);
  border-radius: var(--easy-radius-full);
  padding: 0 6px;
}

.flow-approver__comment {
  color: var(--easy-text-3);
}

/* --- 表单 --- */
.form-readonly {
  display: flex;
  flex-direction: column;
  gap: var(--easy-space-3);
}

.form-row {
  display: flex;
  gap: var(--easy-space-4);
  font-size: var(--easy-text-sm);
}

.form-row__label {
  width: 96px;
  flex: none;
  color: var(--easy-text-3);
}

.form-row__value {
  min-width: 0;
  white-space: pre-wrap;
  word-break: break-word;
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.form-row__file {
  color: var(--easy-brand-text);
  font-size: var(--easy-text-xs);
}

/* --- 历史 --- */
.history {
  display: flex;
  flex-direction: column;
  gap: var(--easy-space-2);
}

.history-row {
  display: flex;
  align-items: center;
  gap: var(--easy-space-3);
  font-size: var(--easy-text-sm);
  flex-wrap: wrap;
}

.history-row__time {
  color: var(--easy-text-3);
  font-size: var(--easy-text-xs);
  font-variant-numeric: tabular-nums;
}

.history-row__actor {
  font-weight: 500;
}

.history-row__action {
  color: var(--easy-brand-text);
}

.history-row__comment {
  color: var(--easy-text-3);
  font-size: var(--easy-text-xs);
}

.form-stack {
  display: flex;
  flex-direction: column;
  gap: var(--easy-space-4);
}

.form-stack__actions {
  display: flex;
  justify-content: flex-end;
}
</style>