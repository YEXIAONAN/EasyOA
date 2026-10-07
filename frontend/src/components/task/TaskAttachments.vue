<script setup lang="ts">
import { onMounted, ref } from 'vue'

import { ApiError } from '@/api/errors'
import { fileApi } from '@/api/modules/comments'
import type { FileMeta } from '@/api/types'
import EasyButton from '@/components/easy/EasyButton.vue'
import { confirmAction } from '@/components/easy/easyConfirm'
import { useAuthStore } from '@/stores/auth'
import { useNotificationStore } from '@/stores/notification'
import { formatDateTime, formatFileSize } from '@/utils/format'

/**
 * 任务附件：上传 / 列表 / 受控下载 / 软删除。
 *
 * 附件不作为公开静态资源：下载链接指向 /api/files/{id}，由后端完成
 * 认证 → 资源权限 → 文件权限校验；非上传者下载会写入敏感审计。
 */
const props = defineProps<{
  taskId: number
  canManage: boolean
  readOnly: boolean
}>()

const emit = defineEmits<{ (e: 'changed'): void }>()

const auth = useAuthStore()
const notification = useNotificationStore()
const files = ref<FileMeta[]>([])
const loading = ref(false)
const uploading = ref(false)
const loadError = ref<string | null>(null)
const inputRef = ref<HTMLInputElement | null>(null)

async function load(): Promise<void> {
  loading.value = true
  loadError.value = null
  try {
    files.value = await fileApi.listTaskFiles(props.taskId)
  } catch (error) {
    loadError.value = error instanceof ApiError ? error.message : '附件加载失败'
  } finally {
    loading.value = false
  }
}

onMounted(load)
defineExpose({ reload: load })

function pickFile(): void {
  inputRef.value?.click()
}

async function onFilePicked(event: Event): Promise<void> {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file) return
  uploading.value = true
  try {
    await fileApi.upload(props.taskId, file)
    notification.success(`已上传「${file.name}」`)
    await load()
    emit('changed')
  } catch (error) {
    notification.error(error)
  } finally {
    uploading.value = false
    input.value = ''
  }
}

async function removeFile(file: FileMeta): Promise<void> {
  const confirmed = await confirmAction({
    title: '删除附件',
    message: `确定删除「${file.originalName}」吗？删除后其他成员将无法再下载该附件。`,
    confirmText: '删除',
    danger: true,
  })
  if (!confirmed) return
  try {
    await fileApi.remove(file.id)
    notification.success('附件已删除')
    await load()
    emit('changed')
  } catch (error) {
    notification.error(error)
  }
}

function canDelete(file: FileMeta): boolean {
  if (props.readOnly) return false
  return file.uploaderId === auth.user?.id || props.canManage
}
</script>

<template>
  <div class="attachments">
    <div class="attachments__head">
      <span class="attachments__title">附件（{{ files.length }}）</span>
      <template v-if="!readOnly">
        <input ref="inputRef" type="file" class="attachments__input" @change="onFilePicked" />
        <EasyButton size="sm" :loading="uploading" @click="pickFile">上传附件</EasyButton>
      </template>
    </div>

    <p v-if="loadError" class="attachments__error">{{ loadError }}</p>
    <p v-else-if="loading && files.length === 0" class="easy-text-xs easy-muted">正在加载附件…</p>
    <p v-else-if="files.length === 0" class="easy-text-xs easy-muted">暂无附件</p>

    <ul v-else class="attachments__list">
      <li v-for="file in files" :key="file.id" class="attachment-row">
        <a class="attachment-row__name" :href="file.downloadUrl" :title="`下载 ${file.originalName}`">
          {{ file.originalName }}
        </a>
        <span class="attachment-row__meta">{{ formatFileSize(file.size) }}</span>
        <span class="attachment-row__meta">{{ file.uploaderName }}</span>
        <span class="attachment-row__meta">{{ formatDateTime(file.createdAt) }}</span>
        <EasyButton v-if="canDelete(file)" size="sm" @click="removeFile(file)">删除</EasyButton>
      </li>
    </ul>
  </div>
</template>

<style scoped>
.attachments {
  display: flex;
  flex-direction: column;
  gap: var(--easy-space-3);
}

.attachments__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--easy-space-2);
}

.attachments__title {
  font-size: var(--easy-text-sm);
  font-weight: 600;
}

.attachments__input {
  display: none;
}

.attachments__error {
  color: var(--easy-danger);
  font-size: var(--easy-text-sm);
}

.attachments__list {
  display: flex;
  flex-direction: column;
}

.attachment-row {
  display: flex;
  align-items: center;
  gap: var(--easy-space-3);
  padding: var(--easy-space-2) 0;
  border-bottom: 1px solid var(--easy-border);
  font-size: var(--easy-text-sm);
}

.attachment-row:last-child {
  border-bottom: none;
}

.attachment-row__name {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: var(--easy-brand-text);
}

.attachment-row__meta {
  flex: none;
  font-size: var(--easy-text-xs);
  color: var(--easy-text-3);
  font-variant-numeric: tabular-nums;
}
</style>