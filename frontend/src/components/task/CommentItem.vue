<script setup lang="ts">
import { computed, ref } from 'vue'

import { commentApi } from '@/api/modules/comments'
import type { CommentVersionView, CommentView, ProjectMemberView } from '@/api/types'
import EasyAvatar from '@/components/easy/EasyAvatar.vue'
import EasyButton from '@/components/easy/EasyButton.vue'
import EasyDialog from '@/components/easy/EasyDialog.vue'
import { confirmAction } from '@/components/easy/easyConfirm'
import { useNotificationStore } from '@/stores/notification'
import { collectMentionIds } from '@/utils/comment'
import { formatDateTime, formatFileSize, formatRelative } from '@/utils/format'

/**
 * 单条评论（含一级回复）。
 *
 * 撤回显示「某某 撤回了一条评论」；编辑保留完整历史（可查看历史版本）；
 * 编辑 / 撤回仅作者本人。
 */
const props = defineProps<{
  comment: CommentView
  members: ProjectMemberView[]
  currentUserId?: number
  canViewHistory: boolean
  readOnly: boolean
  compact?: boolean
}>()

const emit = defineEmits<{
  (e: 'reply', comment: CommentView): void
  (e: 'changed'): void
}>()

const notification = useNotificationStore()
const editing = ref(false)
const editContent = ref('')
const saving = ref(false)

const versionsOpen = ref(false)
const versions = ref<CommentVersionView[]>([])
const loadingVersions = ref(false)

const mentionOptions = computed(() =>
  props.members.map((member) => ({ value: member.displayName, label: member.displayName })),
)
const isMine = computed(() => props.comment.author.id === props.currentUserId)
const showHistory = computed(() => props.comment.edited && (isMine.value || props.canViewHistory))

function startEdit(): void {
  editing.value = true
  editContent.value = props.comment.content ?? ''
}

async function submitEdit(): Promise<void> {
  if (!editContent.value.trim()) {
    notification.warning('评论内容不能为空')
    return
  }
  saving.value = true
  try {
    await commentApi.update(props.comment.id, {
      content: editContent.value.trim(),
      mentionUserIds: collectMentionIds(editContent.value, props.members),
    })
    editing.value = false
    notification.success('评论已更新（完整历史已保留）')
    emit('changed')
  } catch (error) {
    notification.error(error)
  } finally {
    saving.value = false
  }
}

async function withdraw(): Promise<void> {
  const confirmed = await confirmAction({
    title: '撤回评论',
    message: '撤回后界面显示「撤回了一条评论」，原始内容仍保留在数据库中供审计追踪。确定撤回吗？',
    confirmText: '撤回',
    danger: true,
  })
  if (!confirmed) return
  try {
    await commentApi.withdraw(props.comment.id)
    notification.success('评论已撤回')
    emit('changed')
  } catch (error) {
    notification.error(error)
  }
}

async function openVersions(): Promise<void> {
  versionsOpen.value = true
  loadingVersions.value = true
  try {
    versions.value = await commentApi.versions(props.comment.id)
  } catch (error) {
    notification.error(error)
  } finally {
    loadingVersions.value = false
  }
}
</script>

<template>
  <div class="comment" :class="{ 'comment--compact': compact }">
    <div class="comment__head">
      <EasyAvatar :name="comment.author.displayName" :src="comment.author.avatarUrl ?? null" size="sm" />
      <span class="comment__author">{{ comment.author.displayName }}</span>
      <span class="comment__time" :title="formatDateTime(comment.createdAt)">
        {{ formatRelative(comment.createdAt) }}
      </span>
      <span v-if="comment.edited" class="comment__edited">已编辑</span>
    </div>

    <p v-if="comment.withdrawn" class="comment__withdrawn">
      {{ comment.author.displayName }} 撤回了一条评论
    </p>

    <template v-else>
      <div v-if="editing" class="comment__edit">
        <el-mention
          v-model="editContent"
          :options="mentionOptions"
          type="textarea"
          :rows="3"
          placeholder="修改评论…"
        />
        <div class="comment__edit-actions">
          <EasyButton size="sm" @click="editing = false">取消</EasyButton>
          <EasyButton variant="primary" size="sm" :loading="saving" @click="submitEdit">保存修改</EasyButton>
        </div>
      </div>
      <template v-else>
        <p class="comment__content">{{ comment.content }}</p>
        <div v-if="comment.mentions.length > 0" class="comment__mentions">
          <span v-for="mention in comment.mentions" :key="mention.id" class="comment__mention">
            @{{ mention.displayName }}
          </span>
        </div>
        <div v-if="comment.attachments.length > 0" class="comment__attachments">
          <a
            v-for="file in comment.attachments"
            :key="file.id"
            class="comment__attachment"
            :href="file.downloadUrl"
          >
            {{ file.originalName }}（{{ formatFileSize(file.size) }}）
          </a>
        </div>
      </template>
    </template>

    <div v-if="!comment.withdrawn || showHistory" class="comment__actions">
      <button v-if="!compact && !readOnly && !comment.withdrawn" type="button" @click="emit('reply', comment)">
        回复
      </button>
      <template v-if="isMine && !readOnly && !comment.withdrawn && !editing">
        <button type="button" @click="startEdit">编辑</button>
        <button type="button" @click="withdraw">撤回</button>
      </template>
      <button v-if="showHistory" type="button" @click="openVersions">历史</button>
    </div>

    <div v-if="comment.replies.length > 0" class="comment__replies">
      <CommentItem
        v-for="reply in comment.replies"
        :key="reply.id"
        :comment="reply"
        :members="members"
        :current-user-id="currentUserId"
        :can-view-history="canViewHistory"
        :read-only="readOnly"
        compact
        @changed="emit('changed')"
      />
    </div>

    <!-- 编辑历史 -->
    <EasyDialog v-model="versionsOpen" title="评论编辑历史" :width="520">
      <div class="versions">
        <p class="easy-text-xs easy-muted">历史版本只追加、不删除：撤回后仍可由管理员审计追踪。</p>
        <div v-for="version in versions" :key="version.versionNo" class="versions__item">
          <div class="versions__head">
            <span>v{{ version.versionNo }}</span>
            <span class="easy-text-xs easy-muted">
              {{ version.editor?.displayName ?? '—' }} · {{ formatDateTime(version.createdAt) }}
            </span>
          </div>
          <p class="versions__content">{{ version.content }}</p>
        </div>
        <p v-if="loadingVersions" class="easy-text-xs easy-muted">加载中…</p>
      </div>
    </EasyDialog>
  </div>
</template>

<style scoped>
.comment {
  display: flex;
  flex-direction: column;
  gap: var(--easy-space-2);
  padding: var(--easy-space-3) 0;
  border-bottom: 1px solid var(--easy-border);
}

.comment:last-child {
  border-bottom: none;
}

.comment--compact {
  border-bottom: none;
  padding: var(--easy-space-2) 0;
}

.comment__head {
  display: flex;
  align-items: center;
  gap: var(--easy-space-2);
  flex-wrap: wrap;
}

.comment__author {
  font-size: var(--easy-text-sm);
  font-weight: 600;
}

.comment__time {
  font-size: var(--easy-text-xs);
  color: var(--easy-text-3);
}

.comment__edited {
  font-size: var(--easy-text-xs);
  color: var(--easy-text-3);
  border: 1px solid var(--easy-border);
  border-radius: var(--easy-radius-full);
  padding: 0 6px;
}

.comment__content {
  font-size: var(--easy-text-sm);
  color: var(--easy-text-1);
  line-height: var(--easy-leading-relaxed);
  white-space: pre-wrap;
  word-break: break-word;
}

.comment__withdrawn {
  font-size: var(--easy-text-sm);
  color: var(--easy-text-3);
  font-style: italic;
}

.comment__mentions {
  display: flex;
  gap: var(--easy-space-2);
  flex-wrap: wrap;
}

.comment__mention {
  font-size: var(--easy-text-xs);
  color: var(--easy-brand-text);
  background: var(--easy-brand-subtle);
  border-radius: var(--easy-radius-full);
  padding: 1px 8px;
}

.comment__attachments {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.comment__attachment {
  font-size: var(--easy-text-xs);
  color: var(--easy-brand-text);
}

.comment__actions {
  display: flex;
  gap: var(--easy-space-3);
}

.comment__actions button {
  border: none;
  background: transparent;
  color: var(--easy-text-3);
  font-size: var(--easy-text-xs);
  cursor: pointer;
  padding: 0;
}

.comment__actions button:hover {
  color: var(--easy-brand-text);
}

.comment__edit {
  display: flex;
  flex-direction: column;
  gap: var(--easy-space-2);
}

.comment__edit-actions {
  display: flex;
  gap: var(--easy-space-2);
  justify-content: flex-end;
}

.comment__replies {
  display: flex;
  flex-direction: column;
  margin-top: var(--easy-space-1);
  padding-left: var(--easy-space-4);
  border-left: 2px solid var(--easy-border);
}

.versions {
  display: flex;
  flex-direction: column;
  gap: var(--easy-space-3);
}

.versions__item {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: var(--easy-space-2) var(--easy-space-3);
  border: 1px solid var(--easy-border);
  border-radius: var(--easy-radius-md);
}

.versions__head {
  display: flex;
  justify-content: space-between;
  gap: var(--easy-space-2);
  font-size: var(--easy-text-xs);
  font-weight: 600;
}

.versions__content {
  font-size: var(--easy-text-sm);
  color: var(--easy-text-2);
  white-space: pre-wrap;
  word-break: break-word;
}
</style>