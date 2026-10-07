<script setup lang="ts">
import { ref, watch } from 'vue'

import { taskApi } from '@/api/modules/tasks'
import type { ProgressMode, ProjectMemberView, TaskPriority } from '@/api/types'
import EasyButton from '@/components/easy/EasyButton.vue'
import EasyDialog from '@/components/easy/EasyDialog.vue'
import EasyInput from '@/components/easy/EasyInput.vue'
import EasySelect from '@/components/easy/EasySelect.vue'
import { useAuthStore } from '@/stores/auth'
import { useNotificationStore } from '@/stores/notification'
import { toIsoInstant } from '@/utils/format'

/**
 * 新建任务。
 *
 * 派发规则由后端判定：项目负责人创建立即生效；普通成员指派给他人进入待审核（PENDING_ASSIGNMENT），
 * 指派给自己立即生效（被指派成员无需再次接受）。
 */
const props = defineProps<{
  projectId: number
  members: ProjectMemberView[]
}>()

const open = defineModel<boolean>({ required: true })

const emit = defineEmits<{ (e: 'created'): void }>()

const auth = useAuthStore()
const notification = useNotificationStore()
const saving = ref(false)

const form = ref({
  title: '',
  description: '',
  primaryAssigneeId: null as number | null,
  deputyAssigneeId: null as number | null,
  priority: 'MEDIUM' as TaskPriority,
  plannedStartAt: '',
  plannedEndAt: '',
  progressMode: 'MANUAL' as ProgressMode,
})

const priorityOptions = [
  { label: '低', value: 'LOW' },
  { label: '中', value: 'MEDIUM' },
  { label: '高', value: 'HIGH' },
  { label: '紧急', value: 'URGENT' },
]

const progressModeOptions = [
  { label: '手工更新', value: 'MANUAL' },
  { label: '自动（按子任务完成比例）', value: 'AUTO' },
]

function memberOptions(exclude?: number | null): Array<{ label: string; value: number }> {
  return props.members
    .filter((member) => member.userId !== exclude)
    .map((member) => ({
      label: `${member.displayName}${member.role === 'OWNER' ? '（项目负责人）' : ''}`,
      value: member.userId,
    }))
}

watch(open, (value) => {
  if (!value) return
  const isMember = props.members.some((member) => member.userId === auth.user?.id)
  form.value = {
    title: '',
    description: '',
    primaryAssigneeId: isMember ? (auth.user?.id ?? null) : null,
    deputyAssigneeId: null,
    priority: 'MEDIUM',
    plannedStartAt: '',
    plannedEndAt: '',
    progressMode: 'MANUAL',
  }
})

async function submit(): Promise<void> {
  if (!form.value.title.trim()) {
    notification.warning('请输入任务标题')
    return
  }
  if (!form.value.primaryAssigneeId) {
    notification.warning('请选择主负责人')
    return
  }
  saving.value = true
  try {
    await taskApi.create(props.projectId, {
      title: form.value.title.trim(),
      description: form.value.description.trim() || undefined,
      primaryAssigneeId: form.value.primaryAssigneeId,
      deputyAssigneeId: form.value.deputyAssigneeId,
      priority: form.value.priority,
      plannedStartAt: toIsoInstant(form.value.plannedStartAt),
      plannedEndAt: toIsoInstant(form.value.plannedEndAt),
      progressMode: form.value.progressMode,
    })
    notification.success('任务已创建')
    open.value = false
    emit('created')
  } catch (error) {
    notification.error(error)
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <EasyDialog v-model="open" title="新建任务" :width="560">
    <div class="form-stack">
      <EasyInput v-model="form.title" label="任务标题" required placeholder="例如：实现登录页" />
      <EasyInput v-model="form.description" label="任务描述" type="textarea" :rows="3" />
      <div class="form-stack__row">
        <EasySelect
          v-model="form.primaryAssigneeId"
          label="主负责人"
          :options="memberOptions()"
          :clearable="false"
        />
        <EasySelect
          v-model="form.deputyAssigneeId"
          label="副负责人"
          :options="memberOptions(form.primaryAssigneeId)"
          placeholder="不设置"
        />
      </div>
      <div class="form-stack__row">
        <EasySelect v-model="form.priority" label="优先级" :options="priorityOptions" :clearable="false" />
        <EasySelect
          v-model="form.progressMode"
          label="进度模式"
          :options="progressModeOptions"
          :clearable="false"
        />
      </div>
      <div class="form-stack__row">
        <EasyInput v-model="form.plannedStartAt" label="计划开始" type="date" />
        <EasyInput v-model="form.plannedEndAt" label="计划结束" type="date" />
      </div>
      <p class="easy-text-xs easy-muted">
        提示：普通成员把任务指派给他人时，需要项目负责人审核通过后才正式生效。
      </p>
      <div class="form-stack__actions">
        <EasyButton variant="primary" :loading="saving" @click="submit">创建任务</EasyButton>
      </div>
    </div>
  </EasyDialog>
</template>

<style scoped>
.form-stack {
  display: flex;
  flex-direction: column;
  gap: var(--easy-space-4);
}

.form-stack__row {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: var(--easy-space-3);
}

.form-stack__actions {
  display: flex;
  justify-content: flex-start;
}
</style>