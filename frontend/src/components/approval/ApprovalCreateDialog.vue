<script setup lang="ts">
import { computed, ref, watch } from 'vue'

import { approvalApi } from '@/api/modules/approvals'
import { userApi } from '@/api/modules/users'
import type { ApprovalTemplateSummary, FormFieldDef } from '@/api/types'
import ApprovalForm from '@/components/approval/ApprovalForm.vue'
import EasyButton from '@/components/easy/EasyButton.vue'
import EasyDialog from '@/components/easy/EasyDialog.vue'
import EasyInput from '@/components/easy/EasyInput.vue'
import { useNotificationStore } from '@/stores/notification'
import { buildFormValues } from '@/utils/approval'

/**
 * 发起申请：选择模板 → 按 schema 填写表单（含附件）→ 创建草稿并提交。
 *
 * 提交时后端解析动态审批人并生成快照；若流程配置不完整会拒绝提交，
 * 此时草稿保留，可在详情页修正后重试。
 */
const open = defineModel<boolean>({ required: true })

const emit = defineEmits<{ (e: 'submitted', instanceId: number): void }>()

const notification = useNotificationStore()

const templates = ref<ApprovalTemplateSummary[]>([])
const selectedId = ref<number | null>(null)
const title = ref('')
const values = ref<Record<string, unknown>>({})
const formFields = ref<FormFieldDef[]>([])
const formRef = ref<InstanceType<typeof ApprovalForm> | null>(null)
const members = ref<Array<{ label: string; value: number }>>([])

const loadingTemplates = ref(false)
const submitting = ref(false)

const selectedTemplate = computed(() => templates.value.find((item) => item.id === selectedId.value) ?? null)

watch(open, async (value) => {
  if (!value) return
  selectedId.value = null
  title.value = ''
  values.value = {}
  formFields.value = []
  await Promise.all([loadTemplates(), ensureMembers()])
})

async function loadTemplates(): Promise<void> {
  loadingTemplates.value = true
  try {
    const result = await approvalApi.templates()
    templates.value = result.items.filter((item) => item.enabled)
  } catch (error) {
    notification.error(error)
  } finally {
    loadingTemplates.value = false
  }
}

async function ensureMembers(): Promise<void> {
  if (members.value.length > 0) return
  try {
    const directory = await userApi.directory({ size: 100 })
    members.value = directory.items
      .filter((member) => member.status === 'ACTIVE')
      .map((member) => ({ label: member.displayName, value: member.id }))
  } catch (error) {
    notification.error(error)
  }
}

async function pickTemplate(template: ApprovalTemplateSummary): Promise<void> {
  try {
    const detail = await approvalApi.templateDetail(template.id)
    selectedId.value = template.id
    title.value = template.name
    values.value = {}
    formFields.value = detail.latestVersion.formFields
  } catch (error) {
    notification.error(error)
  }
}

async function submit(): Promise<void> {
  if (!selectedId.value) {
    notification.warning('请先选择审批模板')
    return
  }
  if (!title.value.trim()) {
    notification.warning('请输入申请标题')
    return
  }
  const payloadValues = buildFormValues(formFields.value, values.value)

  submitting.value = true
  try {
    const draft = await approvalApi.create({ templateId: selectedId.value, title: title.value.trim(), values: payloadValues })
    try {
      const submittedDetail = await approvalApi.submit(draft.id)
      notification.success('审批已提交')
      open.value = false
      emit('submitted', submittedDetail.id)
    } catch (error) {
      // 提交失败（例如流程配置不完整）时草稿保留，进入详情页可修正后重试
      notification.warning(error instanceof Error ? error.message : '提交失败，草稿已保留')
      open.value = false
      emit('submitted', draft.id)
    }
  } catch (error) {
    notification.error(error)
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <EasyDialog v-model="open" title="发起申请" :width="640">
    <div class="create-approval">
      <div v-if="!selectedId" class="template-picker">
        <p class="easy-text-xs easy-muted">
          选择审批模板（模板内容版本化：提交后使用当前最新版本，历史审批不受模板更新影响）。
        </p>
        <p v-if="templates.length === 0 && !loadingTemplates" class="easy-text-sm easy-muted">
          暂无可用的审批模板，请联系管理员在「模板管理」中创建。
        </p>
        <button
          v-for="template in templates"
          :key="template.id"
          type="button"
          class="template-item"
          @click="pickTemplate(template)"
        >
          <span class="template-item__name">{{ template.name }}</span>
          <span class="template-item__desc">{{ template.description || '暂无说明' }}</span>
          <span class="template-item__version">v{{ template.latestVersionNo }}</span>
        </button>
      </div>

      <template v-else>
        <div class="create-approval__head">
          <span class="easy-text-sm">
            申请类型：<strong>{{ selectedTemplate?.name }}</strong>
            <span class="easy-text-xs easy-muted">（模板 v{{ selectedTemplate?.latestVersionNo }}）</span>
          </span>
          <EasyButton size="sm" @click="selectedId = null">重新选择</EasyButton>
        </div>
        <EasyInput v-model="title" label="申请标题" required />
        <ApprovalForm ref="formRef" v-model="values" :fields="formFields" :members="members" />
        <div class="create-approval__actions">
          <EasyButton variant="primary" :loading="submitting" @click="submit">提交审批</EasyButton>
        </div>
      </template>
    </div>
  </EasyDialog>
</template>

<style scoped>
.create-approval {
  display: flex;
  flex-direction: column;
  gap: var(--easy-space-4);
}

.template-picker {
  display: flex;
  flex-direction: column;
  gap: var(--easy-space-2);
}

.template-item {
  display: grid;
  grid-template-columns: 1fr auto;
  gap: 4px var(--easy-space-3);
  padding: var(--easy-space-3) var(--easy-space-4);
  border: 1px solid var(--easy-border);
  border-radius: var(--easy-radius-md);
  background: var(--easy-surface);
  text-align: left;
  cursor: pointer;
  transition: border-color var(--easy-transition-fast), background var(--easy-transition-fast);
}

.template-item:hover {
  border-color: var(--easy-brand-subtle-border);
  background: var(--easy-surface-hover);
}

.template-item__name {
  font-size: var(--easy-text-sm);
  font-weight: 600;
}

.template-item__desc {
  grid-column: 1 / -1;
  font-size: var(--easy-text-xs);
  color: var(--easy-text-3);
}

.template-item__version {
  font-size: var(--easy-text-xs);
  color: var(--easy-text-3);
}

.create-approval__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--easy-space-2);
}

.create-approval__actions {
  display: flex;
  justify-content: flex-end;
}
</style>