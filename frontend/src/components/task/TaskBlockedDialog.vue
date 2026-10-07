<script setup lang="ts">
import { ref, watch } from 'vue'

import { taskApi } from '@/api/modules/tasks'
import type { TaskDependencyView } from '@/api/types'
import EasyButton from '@/components/easy/EasyButton.vue'
import EasyDialog from '@/components/easy/EasyDialog.vue'
import EasyInput from '@/components/easy/EasyInput.vue'
import { useNotificationStore } from '@/stores/notification'

/**
 * 「忽略依赖并开始」弹窗。
 *
 * 产品规范要求：明确提示存在未完成前置任务 → 用户主动点击 → 必须填写原因 →
 * 后端重新检查权限 → 写入审计（TASK_OVERRIDE_DEPENDENCY）。
 */
const props = defineProps<{
  taskId: number | null
  statusId: number | null
  statusName: string
}>()

const open = defineModel<boolean>({ required: true })

const emit = defineEmits<{ (e: 'done'): void }>()

const notification = useNotificationStore()
const blockers = ref<TaskDependencyView[]>([])
const reason = ref('')
const submitting = ref(false)

watch(open, async (value) => {
  if (!value) return
  reason.value = ''
  blockers.value = []
  if (props.taskId) {
    try {
      const detail = await taskApi.detail(props.taskId)
      blockers.value = detail.dependencies.filter((dependency) => !dependency.finished)
    } catch {
      // 弹窗仅用于展示；后端在提交时会再次校验权限与依赖
    }
  }
})

async function confirm(): Promise<void> {
  if (!props.taskId || !props.statusId) return
  if (!reason.value.trim()) {
    notification.warning('请填写忽略依赖的原因（将写入审计）')
    return
  }
  submitting.value = true
  try {
    await taskApi.changeStatus(props.taskId, props.statusId, reason.value.trim())
    notification.success(`已忽略未完成依赖并进入「${props.statusName}」`)
    open.value = false
    emit('done')
  } catch (error) {
    notification.error(error)
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <EasyDialog v-model="open" title="当前任务仍有未完成依赖" :width="520">
    <div class="blocked-dialog">
      <p class="blocked-dialog__text">
        以下前置任务尚未完成。继续开始会被记录为「忽略依赖并开始」，原因将写入审计日志。
      </p>
      <ul v-if="blockers.length > 0" class="blocked-dialog__list">
        <li v-for="dependency in blockers" :key="dependency.id">
          <span>{{ dependency.title }}</span>
          <span class="easy-muted">{{ dependency.statusName }}</span>
        </li>
      </ul>
      <EasyInput
        v-model="reason"
        label="忽略原因（必填）"
        type="textarea"
        :rows="3"
        placeholder="例如：客户要求并行推进，已与前置任务负责人确认"
      />
      <div class="blocked-dialog__actions">
        <EasyButton size="sm" @click="open = false">取消</EasyButton>
        <EasyButton variant="primary" size="sm" :loading="submitting" @click="confirm">
          忽略依赖并开始
        </EasyButton>
      </div>
    </div>
  </EasyDialog>
</template>

<style scoped>
.blocked-dialog {
  display: flex;
  flex-direction: column;
  gap: var(--easy-space-4);
}

.blocked-dialog__text {
  font-size: var(--easy-text-sm);
  color: var(--easy-text-2);
  line-height: var(--easy-leading-relaxed);
}

.blocked-dialog__list {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: var(--easy-space-3) var(--easy-space-4);
  border: 1px solid var(--easy-warning);
  border-radius: var(--easy-radius-md);
  background: var(--easy-warning-bg);
  font-size: var(--easy-text-sm);
}

.blocked-dialog__list li {
  display: flex;
  justify-content: space-between;
  gap: var(--easy-space-3);
}

.blocked-dialog__actions {
  display: flex;
  justify-content: flex-end;
  gap: var(--easy-space-2);
}
</style>