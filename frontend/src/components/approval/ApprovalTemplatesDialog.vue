<script setup lang="ts">
import { ref, watch } from 'vue'

import { approvalApi } from '@/api/modules/approvals'
import { userApi } from '@/api/modules/users'
import type {
  ApprovalTemplateDetail,
  ApprovalTemplateSummary,
  ApproverRuleType,
  FormFieldDef,
  NodeDefinition,
  TemplatePayload,
} from '@/api/types'
import EasyButton from '@/components/easy/EasyButton.vue'
import EasyDialog from '@/components/easy/EasyDialog.vue'
import EasyInput from '@/components/easy/EasyInput.vue'
import EasySelect from '@/components/easy/EasySelect.vue'
import { useNotificationStore } from '@/stores/notification'

/**
 * 审批模板管理（仅系统管理员）。
 *
 * 模板内容版本化：编辑表单 / 节点后「保存」会发布新版本（v+1），
 * 已运行实例继续使用发起时的版本。
 */
const open = defineModel<boolean>({ required: true })

const notification = useNotificationStore()
const templates = ref<ApprovalTemplateSummary[]>([])
const loading = ref(false)
const saving = ref(false)

const members = ref<Array<{ label: string; value: number }>>([])

// 编辑器状态
const editing = ref(false)
const editingId = ref<number | null>(null)
const form = ref({
  name: '',
  description: '',
  enabled: true,
  formFields: [] as FormFieldDef[],
  nodes: [] as NodeDefinition[],
})

const fieldTypeOptions = [
  { label: '单行文本', value: 'TEXT' },
  { label: '多行文本', value: 'TEXTAREA' },
  { label: '数字', value: 'NUMBER' },
  { label: '金额', value: 'MONEY' },
  { label: '日期', value: 'DATE' },
  { label: '日期时间', value: 'DATETIME' },
  { label: '单选', value: 'SELECT' },
  { label: '多选', value: 'MULTI_SELECT' },
  { label: '成员', value: 'USER' },
  { label: '附件', value: 'ATTACHMENT' },
]

const ruleTypeOptions = [
  { label: '指定成员', value: 'FIXED_USER' },
  { label: '直属主管（主部门向上）', value: 'DIRECT_MANAGER' },
  { label: '主部门负责人', value: 'PRIMARY_DEPT_MANAGER' },
  { label: '组织负责人', value: 'ORG_UNIT_MANAGER' },
  { label: '项目负责人', value: 'PROJECT_OWNER' },
  { label: '项目副负责人', value: 'PROJECT_DEPUTY' },
  { label: '系统角色', value: 'SYSTEM_ROLE' },
]

const modeOptions = [
  { label: '任一通过（ANY_ONE）', value: 'ANY_ONE' },
  { label: '全部通过（ALL）', value: 'ALL' },
]

const fallbackOptions = [
  { label: '系统默认递补链', value: '' },
  { label: '备用：管理员（ADMIN）', value: 'ADMIN' },
  { label: '备用：系统负责人（ROOT）', value: 'ROOT' },
]

watch(open, async (value) => {
  if (!value) return
  editing.value = false
  editingId.value = null
  await Promise.all([load(), ensureMembers()])
})

async function load(): Promise<void> {
  loading.value = true
  try {
    const result = await approvalApi.templates('', 100)
    templates.value = result.items
  } catch (error) {
    notification.error(error)
  } finally {
    loading.value = false
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

function startCreate(): void {
  editing.value = true
  editingId.value = null
  form.value = {
    name: '',
    description: '',
    enabled: true,
    formFields: [{ key: 'reason', label: '申请事由', type: 'TEXTAREA', required: true, options: [] }],
    nodes: [
      {
        name: '直属主管审批',
        mode: 'ANY_ONE',
        approvers: [{ type: 'DIRECT_MANAGER' }],
      },
    ],
  }
}

async function startEdit(template: ApprovalTemplateSummary): Promise<void> {
  try {
    const detail: ApprovalTemplateDetail = await approvalApi.templateDetail(template.id)
    editing.value = true
    editingId.value = template.id
    form.value = {
      name: detail.name,
      description: detail.description ?? '',
      enabled: detail.enabled,
      formFields: JSON.parse(JSON.stringify(detail.latestVersion.formFields)),
      nodes: JSON.parse(JSON.stringify(detail.latestVersion.nodes)),
    }
  } catch (error) {
    notification.error(error)
  }
}

function addField(): void {
  form.value.formFields.push({ key: '', label: '', type: 'TEXT', required: false, options: [] })
}

function removeField(index: number): void {
  form.value.formFields.splice(index, 1)
}

function addNode(): void {
  form.value.nodes.push({ name: '', mode: 'ANY_ONE', approvers: [{ type: 'DIRECT_MANAGER' }] })
}

function removeNode(index: number): void {
  form.value.nodes.splice(index, 1)
}

function addApprover(nodeIndex: number): void {
  form.value.nodes[nodeIndex]?.approvers.push({ type: 'FIXED_USER' })
}

function removeApprover(nodeIndex: number, approverIndex: number): void {
  form.value.nodes[nodeIndex]?.approvers.splice(approverIndex, 1)
}

function optionsText(field: FormFieldDef): string {
  return (field.options ?? []).join('、')
}

function setOptions(field: FormFieldDef, text: string): void {
  field.options = text
    .split(/[、,，]/)
    .map((item) => item.trim())
    .filter(Boolean)
}

/** 备用规则（简化编辑：无 / ADMIN / ROOT） */
function fallbackRole(rule: { fallback?: { type: ApproverRuleType; systemRole?: string | null }[] | null }): string {
  const first = rule.fallback?.[0]
  return first?.systemRole ?? ''
}

function setFallbackRole(
  rule: { fallback?: { type: ApproverRuleType; systemRole?: string | null }[] | null },
  role: string,
): void {
  rule.fallback = role ? [{ type: 'SYSTEM_ROLE', systemRole: role }] : null
}

function buildPayload(): TemplatePayload {
  return {
    name: form.value.name.trim(),
    description: form.value.description.trim() || undefined,
    formFields: form.value.formFields.map((field) => ({
      key: field.key.trim(),
      label: field.label.trim(),
      type: field.type,
      required: field.required,
      options: field.options && field.options.length > 0 ? field.options : undefined,
    })),
    nodes: form.value.nodes.map((node) => ({
      name: node.name.trim(),
      mode: node.mode,
      approvers: node.approvers.map((rule) => ({
        type: rule.type,
        userId: rule.type === 'FIXED_USER' ? rule.userId : undefined,
        systemRole: rule.type === 'SYSTEM_ROLE' ? rule.systemRole : undefined,
        projectField: rule.type === 'PROJECT_OWNER' || rule.type === 'PROJECT_DEPUTY' ? rule.projectField : undefined,
        fallback: rule.fallback && rule.fallback.length > 0 ? rule.fallback : undefined,
      })),
    })),
  }
}

async function save(): Promise<void> {
  if (!form.value.name.trim()) {
    notification.warning('请输入模板名称')
    return
  }
  const payload = buildPayload()
  saving.value = true
  try {
    if (editingId.value === null) {
      await approvalApi.createTemplate(payload)
      notification.success('模板已创建（v1）')
    } else {
      await approvalApi.updateTemplate(editingId.value, {
        name: payload.name,
        description: payload.description,
        enabled: form.value.enabled,
      })
      await approvalApi.publishVersion(editingId.value, {
        formFields: payload.formFields,
        nodes: payload.nodes,
      })
      notification.success('已发布新版本（已运行实例继续使用旧版本）')
    }
    editing.value = false
    editingId.value = null
    await load()
  } catch (error) {
    notification.error(error)
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <EasyDialog v-model="open" title="审批模板管理" :width="760">
    <div v-if="!editing" class="template-manager">
      <div class="template-manager__head">
        <span class="easy-text-xs easy-muted">
          模板内容版本化：修改后发布新版本，已运行的审批实例始终使用发起时的版本。
        </span>
        <EasyButton variant="primary" size="sm" @click="startCreate">新建模板</EasyButton>
      </div>

      <p v-if="loading" class="easy-text-xs easy-muted">加载中…</p>
      <ul v-else class="template-list">
        <li v-for="template in templates" :key="template.id" class="template-row">
          <div class="template-row__main">
            <span class="template-row__name">
              {{ template.name }}
              <span class="easy-text-xs easy-muted">v{{ template.latestVersionNo }}</span>
              <span v-if="!template.enabled" class="template-row__disabled">已停用</span>
            </span>
            <span class="easy-text-xs easy-muted">{{ template.description || '暂无说明' }}</span>
          </div>
          <EasyButton size="sm" @click="startEdit(template)">编辑 / 发布新版本</EasyButton>
        </li>
        <li v-if="templates.length === 0" class="easy-text-sm easy-muted">暂无模板，点击「新建模板」创建第一个。</li>
      </ul>
    </div>

    <div v-else class="template-editor">
      <EasyInput v-model="form.name" label="模板名称" required />
      <EasyInput v-model="form.description" label="模板说明" />
      <label v-if="editingId !== null" class="template-editor__enabled">
        <input v-model="form.enabled" type="checkbox" />
        启用（停用后成员无法发起新申请，已运行实例不受影响）
      </label>

      <!-- 表单字段 -->
      <section class="editor-section">
        <div class="editor-section__head">
          <span class="editor-section__title">表单字段</span>
          <EasyButton size="sm" @click="addField">添加字段</EasyButton>
        </div>
        <div v-for="(field, index) in form.formFields" :key="index" class="editor-row">
          <EasyInput v-model="field.label" placeholder="字段名称" />
          <EasyInput v-model="field.key" placeholder="字段标识（英文）" />
          <EasySelect v-model="field.type" :options="fieldTypeOptions" :clearable="false" />
          <label class="editor-row__check">
            <input v-model="field.required" type="checkbox" />
            必填
          </label>
          <EasyInput
            v-if="field.type === 'SELECT' || field.type === 'MULTI_SELECT'"
            :model-value="optionsText(field)"
            placeholder="选项（顿号分隔）"
            @update:model-value="(value: string) => setOptions(field, value)"
          />
          <EasyButton size="sm" @click="removeField(index)">删除</EasyButton>
        </div>
      </section>

      <!-- 审批节点 -->
      <section class="editor-section">
        <div class="editor-section__head">
          <span class="editor-section__title">审批节点（按顺序流转）</span>
          <EasyButton size="sm" @click="addNode">添加节点</EasyButton>
        </div>
        <div v-for="(node, nodeIndex) in form.nodes" :key="nodeIndex" class="node-card">
          <div class="editor-row">
            <EasyInput v-model="node.name" placeholder="节点名称（如：直属主管审批）" />
            <EasySelect v-model="node.mode" :options="modeOptions" :clearable="false" />
            <EasyButton size="sm" @click="removeNode(nodeIndex)">删除节点</EasyButton>
          </div>
          <div v-for="(rule, ruleIndex) in node.approvers" :key="ruleIndex" class="editor-row">
            <EasySelect v-model="rule.type" :options="ruleTypeOptions" :clearable="false" />
            <EasySelect
              v-if="rule.type === 'FIXED_USER'"
              v-model="rule.userId"
              :options="members"
              placeholder="选择成员"
              :clearable="false"
            />
            <EasySelect
              v-if="rule.type === 'SYSTEM_ROLE'"
              v-model="rule.systemRole"
              :options="[
                { label: '管理员（ADMIN）', value: 'ADMIN' },
                { label: '系统负责人（ROOT）', value: 'ROOT' },
              ]"
              :clearable="false"
            />
            <EasyInput
              v-if="rule.type === 'PROJECT_OWNER' || rule.type === 'PROJECT_DEPUTY'"
              :model-value="rule.projectField ?? ''"
              placeholder="表单中的项目字段 key"
              @update:model-value="(value: string) => (rule.projectField = value)"
            />
            <EasySelect
              :model-value="fallbackRole(rule)"
              :options="fallbackOptions"
              :clearable="false"
              @update:model-value="(value: unknown) => setFallbackRole(rule, String(value ?? ''))"
            />
            <EasyButton size="sm" @click="removeApprover(nodeIndex, ruleIndex)">删除</EasyButton>
          </div>
          <EasyButton size="sm" @click="addApprover(nodeIndex)">添加审批人规则</EasyButton>
          <p class="easy-text-xs easy-muted">
            自我审批禁止：解析出的审批人等于申请人时自动使用备用规则；仍无法解析将拒绝提交。
          </p>
        </div>
      </section>

      <div class="template-editor__actions">
        <EasyButton @click="editing = false">取消</EasyButton>
        <EasyButton variant="primary" :loading="saving" @click="save">
          {{ editingId === null ? '创建模板' : '保存并发布新版本' }}
        </EasyButton>
      </div>
    </div>
  </EasyDialog>
</template>

<style scoped>
.template-manager {
  display: flex;
  flex-direction: column;
  gap: var(--easy-space-4);
}

.template-manager__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--easy-space-3);
}

.template-list {
  display: flex;
  flex-direction: column;
  gap: var(--easy-space-2);
}

.template-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--easy-space-3);
  padding: var(--easy-space-3);
  border: 1px solid var(--easy-border);
  border-radius: var(--easy-radius-md);
}

.template-row__main {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}

.template-row__name {
  font-size: var(--easy-text-sm);
  font-weight: 600;
}

.template-row__disabled {
  margin-left: var(--easy-space-2);
  font-size: var(--easy-text-xs);
  color: var(--easy-danger);
}

.template-editor {
  display: flex;
  flex-direction: column;
  gap: var(--easy-space-4);
  max-height: 62vh;
  overflow-y: auto;
  padding-right: 4px;
}

.template-editor__enabled {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: var(--easy-text-sm);
  color: var(--easy-text-2);
}

.editor-section {
  display: flex;
  flex-direction: column;
  gap: var(--easy-space-3);
  padding-top: var(--easy-space-3);
  border-top: 1px solid var(--easy-border);
}

.editor-section__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.editor-section__title {
  font-size: var(--easy-text-sm);
  font-weight: 600;
}

.editor-row {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(140px, 1fr));
  gap: var(--easy-space-2);
  align-items: center;
}

.editor-row__check {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: var(--easy-text-xs);
  color: var(--easy-text-2);
}

.node-card {
  display: flex;
  flex-direction: column;
  gap: var(--easy-space-2);
  padding: var(--easy-space-3);
  border: 1px solid var(--easy-border);
  border-radius: var(--easy-radius-md);
  background: var(--easy-surface-sunken);
}

.template-editor__actions {
  display: flex;
  justify-content: flex-end;
  gap: var(--easy-space-2);
}
</style>