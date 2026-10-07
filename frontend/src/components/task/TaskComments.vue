<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'

import { ApiError } from '@/api/errors'
import { commentApi, fileApi } from '@/api/modules/comments'
import type { CommentView, FileMeta, ProjectMemberView } from '@/api/types'
import EasyButton from '@/components/easy/EasyButton.vue'
import CommentItem from '@/components/task/CommentItem.vue'
import { useAuthStore } from '@/stores/auth'
import { useNotificationStore } from '@/stores/notification'
import { collectMentionIds } from '@/utils/comment'
import { formatFileSize } from '@/utils/format'

/**
 * 任务评论：发表 / 回复 / @成员（ElMention）/ 附件 / 编辑历史 / 撤回。
 *
 * 撤回不做物理删除（原始评论保留，界面显示撤回占位）；评论附件先上传到任务、
 * 发表时挂载（仅可引用本人上传的文件）。
 */
const props = defineProps<{
  taskId: number
  members: ProjectMemberView[]
  canViewHistory: boolean
  readOnly: boolean
}>()

const emit = defineEmits<{ (e: 'changed'): void }>()

const auth = useAuthStore()
const notification = useNotificationStore()

const items = ref<CommentView[]>([])
const total = ref(0)
const totalPages = ref(0)
const page = ref(1)
const pageSize = 20
const loading = ref(false)
const loadError = ref<string | null>(null)

const composer = ref('')
const replyTo = ref<CommentView | null>(null)
const pendingFiles = ref<FileMeta[]>([])
const uploadingFile = ref(false)
const submitting = ref(false)
const fileInputRef = ref<HTMLInputElement | null>(null)

const mentionOptions = computed(() =>
  props.members.map((member) => ({ value: member.displayName, label: member.displayName })),
)

async function load(reset = true): Promise<void> {
  loading.value = true
  loadError.value = null
  try {
    const result = await commentApi.list(props.taskId, reset ? 1 : page.value, pageSize)
    items.value = reset ? result.items : [...items.value, ...result.items]
    total.value = result.total
    totalPages.value = result.totalPages
  } catch (error) {
    loadError.value = error instanceof ApiError ? error.message : '评论加载失败'
  } finally {
    loading.value = false
  }
}

onMounted(() => load(true))
watch(
  () => props.taskId,
  () => {
    page.value = 1
    replyTo.value = null
    pendingFiles.value = []
    composer.value = ''
    void load(true)
  },
)

function loadMore(): void {
  page.value += 1
  void load(false)
}

function startReply(comment: CommentView): void {
  replyTo.value = comment
}

async function pickPendingFile(): Promise<void> {
  fileInputRef.value?.click()
}

async function onFilePicked(event: Event): Promise<void> {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file) return
  uploadingFile.value = true
  try {
    // 先上传到任务，发表评论时再挂载为评论附件
    const uploaded = await fileApi.upload(props.taskId, file)
    pendingFiles.value = [...pendingFiles.value, uploaded]
    notification.success(`已添加附件「${file.name}」`)
  } catch (error) {
    notification.error(error)
  } finally {
    uploadingFile.value = false
    input.value = ''
  }
}

function removePendingFile(file: FileMeta): void {
  pendingFiles.value = pendingFiles.value.filter((item) => item.id !== file.id)
}

async function submit(): Promise<void> {
  const content = composer.value.trim()
  if (!content) {
    notification.warning('请输入评论内容')
    return
  }
  submitting.value = true
  try {
    await commentApi.create(props.taskId, {
      content,
      parentId: replyTo.value?.id ?? null,
      mentionUserIds: collectMentionIds(content, props.members),
      attachmentFileIds: pendingFiles.value.map((file) => file.id),
    })
    composer.value = ''
    replyTo.value = null
    pendingFiles.value = []
    notification.success('评论已发表')
    await load(true)
    emit('changed')
  } catch (error) {
    notification.error(error)
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <div class="comments">
    <!-- 发表 / 回复 -->
    <div class="comments__composer">
      <p v-if="replyTo" class="comments__reply-hint">
        回复 {{ replyTo.author.displayName }}
        <button type="button" @click="replyTo = null">取消回复</button>
      </p>
      <el-mention
        v-model="composer"
        :options="mentionOptions"
        :disabled="readOnly"
        type="textarea"
        :rows="3"
        placeholder="写评论… 输入 @ 可提及项目成员"
      />
      <div v-if="pendingFiles.length > 0" class="comments__pending">
        <span v-for="file in pendingFiles" :key="file.id" class="comments__chip">
          {{ file.originalName }}（{{ formatFileSize(file.size) }}）
          <button type="button" @click="removePendingFile(file)">×</button>
        </span>
      </div>
      <div class="comments__actions">
        <input ref="fileInputRef" type="file" class="comments__file-input" @change="onFilePicked" />
        <EasyButton size="sm" :disabled="readOnly" :loading="uploadingFile" @click="pickPendingFile">
          添加附件
        </EasyButton>
        <EasyButton variant="primary" size="sm" :disabled="readOnly" :loading="submitting" @click="submit">
          {{ replyTo ? '回复' : '发表评论' }}
        </EasyButton>
        <span class="easy-text-xs easy-muted">评论支持编辑与撤回（保留完整历史）</span>
      </div>
    </div>

    <p v-if="loadError" class="comments__error">{{ loadError }}</p>
    <p v-else-if="loading && items.length === 0" class="easy-text-xs easy-muted">正在加载评论…</p>
    <p v-else-if="items.length === 0" class="easy-text-xs easy-muted">还没有评论，来发表第一条吧</p>

    <div v-else class="comments__list">
      <CommentItem
        v-for="comment in items"
        :key="comment.id"
        :comment="comment"
        :members="members"
        :current-user-id="auth.user?.id"
        :can-view-history="canViewHistory"
        :read-only="readOnly"
        @reply="startReply"
        @changed="load(true)"
      />
      <div v-if="page < totalPages" class="comments__more">
        <EasyButton size="sm" :loading="loading" @click="loadMore">加载更早的评论</EasyButton>
      </div>
      <p class="easy-text-xs easy-muted">共 {{ total }} 条评论</p>
    </div>
  </div>
</template>

<style scoped>
.comments {
  display: flex;
  flex-direction: column;
  gap: var(--easy-space-3);
}

.comments__composer {
  display: flex;
  flex-direction: column;
  gap: var(--easy-space-2);
}

.comments__reply-hint {
  font-size: var(--easy-text-xs);
  color: var(--easy-text-3);
}

.comments__reply-hint button {
  border: none;
  background: transparent;
  color: var(--easy-brand-text);
  font-size: inherit;
  cursor: pointer;
  padding: 0 4px;
}

.comments__pending {
  display: flex;
  gap: var(--easy-space-2);
  flex-wrap: wrap;
}

.comments__chip {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 2px 8px;
  border: 1px solid var(--easy-border);
  border-radius: var(--easy-radius-full);
  font-size: var(--easy-text-xs);
  color: var(--easy-text-2);
}

.comments__chip button {
  border: none;
  background: transparent;
  color: var(--easy-text-3);
  cursor: pointer;
  padding: 0;
  font-size: var(--easy-text-sm);
}

.comments__actions {
  display: flex;
  align-items: center;
  gap: var(--easy-space-2);
  flex-wrap: wrap;
}

.comments__file-input {
  display: none;
}

.comments__error {
  color: var(--easy-danger);
  font-size: var(--easy-text-sm);
}

.comments__list {
  display: flex;
  flex-direction: column;
}

.comments__more {
  display: flex;
  justify-content: center;
  padding: var(--easy-space-2) 0;
}
</style>