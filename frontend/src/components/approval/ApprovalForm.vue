<script setup lang="ts">
import { computed, ref } from 'vue'

import { approvalApi } from '@/api/modules/approvals'
import type { FileMeta, FormFieldDef } from '@/api/types'
import EasyButton from '@/components/easy/EasyButton.vue'
import EasyInput from '@/components/easy/EasyInput.vue'
import EasySelect from '@/components/easy/EasySelect.vue'
import { useNotificationStore } from '@/stores/notification'
import { formatFileSize } from '@/utils/format'
import { toFormInputValue } from '@/utils/approval'

/**
 * 审批动态表单渲染器（模板 schema 驱动）。
 *
 * 支持 TEXT / TEXTAREA / NUMBER / MONEY / DATE / DATETIME / SELECT / MULTI_SELECT /
 * USER / ATTACHMENT；附件先上传（草稿附件），提交时由后端挂载到实例。
 */
withDefaults(
  defineProps<{
    fields: FormFieldDef[]
    members: Array<{ label: string; value: number }>
    readonly?: boolean
  }>(),
  { readonly: false },
)

const values = defineModel<Record<string, unknown>>({ required: true })

const notification = useNotificationStore()
const uploaded = ref<Record<string, FileMeta[]>>({})
const uploadingKey = ref<string | null>(null)
const fileInputRef = ref<HTMLInputElement | null>(null)
const activeFieldKey = ref<string | null>(null)

const selectOptions = computed(() => (field: FormFieldDef) =>
  (field.options ?? []).map((option) => ({ label: option, value: option })),
)

function inputValue(field: FormFieldDef): unknown {
  return values.value[field.key] ?? (field.type === 'MULTI_SELECT' || field.type === 'ATTACHMENT' ? [] : '')
}

function setValue(field: FormFieldDef, value: unknown): void {
  values.value = { ...values.value, [field.key]: value }
}

function pickFile(fieldKey: string): void {
  activeFieldKey.value = fieldKey
  fileInputRef.value?.click()
}

async function onFilePicked(event: Event): Promise<void> {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  const fieldKey = activeFieldKey.value
  input.value = ''
  if (!file || !fieldKey) return
  uploadingKey.value = fieldKey
  try {
    const meta = await approvalApi.uploadAttachment(file)
    const list = [...(uploaded.value[fieldKey] ?? []), meta]
    uploaded.value = { ...uploaded.value, [fieldKey]: list }
    setValue({ key: fieldKey } as FormFieldDef, list.map((item) => item.id))
    notification.success(`已添加附件「${file.name}」`)
  } catch (error) {
    notification.error(error)
  } finally {
    uploadingKey.value = null
    activeFieldKey.value = null
  }
}

function removeAttachment(field: FormFieldDef, fileId: number): void {
  const list = (uploaded.value[field.key] ?? []).filter((item) => item.id !== fileId)
  uploaded.value = { ...uploaded.value, [field.key]: list }
  setValue(field, list.map((item) => item.id))
}

const attachmentNames = computed(() => {
  const map: Record<number, FileMeta> = {}
  for (const list of Object.values(uploaded.value)) {
    for (const file of list) map[file.id] = file
  }
  return map
})
</script>

<template>
  <div class="approval-form">
    <input ref="fileInputRef" type="file" class="approval-form__file" @change="onFilePicked" />

    <template v-for="field in fields" :key="field.key">
      <EasyInput
        v-if="field.type === 'TEXT'"
        :model-value="String(inputValue(field) ?? '')"
        :label="field.label"
        :required="field.required"
        :disabled="readonly"
        :placeholder="field.placeholder ?? undefined"
        @update:model-value="(value: string) => setValue(field, value)"
      />
      <EasyInput
        v-else-if="field.type === 'TEXTAREA'"
        :model-value="String(inputValue(field) ?? '')"
        :label="field.label"
        :required="field.required"
        :disabled="readonly"
        type="textarea"
        :rows="3"
        @update:model-value="(value: string) => setValue(field, value)"
      />
      <EasyInput
        v-else-if="field.type === 'NUMBER' || field.type === 'MONEY'"
        :model-value="String(inputValue(field) ?? '')"
        :label="field.type === 'MONEY' ? `${field.label}（元）` : field.label"
        :required="field.required"
        :disabled="readonly"
        type="number"
        @update:model-value="(value: string) => setValue(field, value)"
      />
      <EasyInput
        v-else-if="field.type === 'DATE'"
        :model-value="String(toFormInputValue('DATE', inputValue(field)) ?? '')"
        :label="field.label"
        :required="field.required"
        :disabled="readonly"
        type="date"
        @update:model-value="(value: string) => setValue(field, value)"
      />
      <EasyInput
        v-else-if="field.type === 'DATETIME'"
        :model-value="String(toFormInputValue('DATETIME', inputValue(field)) ?? '')"
        :label="field.label"
        :required="field.required"
        :disabled="readonly"
        type="datetime-local"
        @update:model-value="(value: string) => setValue(field, value)"
      />
      <EasySelect
        v-else-if="field.type === 'SELECT'"
        :model-value="(inputValue(field) as string | null)"
        :label="field.label"
        :options="selectOptions(field)"
        :clearable="!field.required"
        :disabled="readonly"
        @update:model-value="(value: unknown) => setValue(field, value)"
      />
      <EasySelect
        v-else-if="field.type === 'MULTI_SELECT'"
        :model-value="(inputValue(field) as Array<string | number>)"
        multiple
        :label="field.label"
        :options="selectOptions(field)"
        :disabled="readonly"
        @update:model-value="(value: unknown) => setValue(field, value)"
      />
      <EasySelect
        v-else-if="field.type === 'USER'"
        :model-value="inputValue(field) ? Number(inputValue(field)) : null"
        :label="field.label"
        :options="members"
        :clearable="!field.required"
        :disabled="readonly"
        @update:model-value="(value: unknown) => setValue(field, value)"
      />
      <div v-else-if="field.type === 'ATTACHMENT'" class="approval-form__attachment">
        <span class="approval-form__label">{{ field.label }}{{ field.required ? ' *' : '' }}</span>
        <div class="approval-form__files">
          <span v-for="file in uploaded[field.key] ?? []" :key="file.id" class="approval-form__chip">
            {{ attachmentNames[file.id]?.originalName ?? `附件 #${file.id}` }}（{{ formatFileSize(file.size) }}）
            <button v-if="!readonly" type="button" @click="removeAttachment(field, file.id)">×</button>
          </span>
          <span v-if="(uploaded[field.key] ?? []).length === 0" class="easy-text-xs easy-muted">暂无附件</span>
        </div>
        <EasyButton
          v-if="!readonly"
          size="sm"
          :loading="uploadingKey === field.key"
          @click="pickFile(field.key)"
        >
          上传附件
        </EasyButton>
      </div>
    </template>
  </div>
</template>

<style scoped>
.approval-form {
  display: flex;
  flex-direction: column;
  gap: var(--easy-space-4);
}

.approval-form__file {
  display: none;
}

.approval-form__attachment {
  display: flex;
  flex-direction: column;
  gap: var(--easy-space-2);
}

.approval-form__label {
  font-size: var(--easy-text-sm);
  font-weight: 500;
  color: var(--easy-text-2);
}

.approval-form__files {
  display: flex;
  gap: var(--easy-space-2);
  flex-wrap: wrap;
}

.approval-form__chip {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 2px 8px;
  border: 1px solid var(--easy-border);
  border-radius: var(--easy-radius-full);
  font-size: var(--easy-text-xs);
  color: var(--easy-text-2);
}

.approval-form__chip button {
  border: none;
  background: transparent;
  color: var(--easy-text-3);
  cursor: pointer;
  padding: 0;
}
</style>