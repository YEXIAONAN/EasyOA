<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'

import { ApiError } from '@/api/errors'
import { taskApi } from '@/api/modules/tasks'
import type { TaskBoard, TaskCard, TaskStatusType, TaskStatusView } from '@/api/types'
import EasyAvatar from '@/components/easy/EasyAvatar.vue'
import EasyButton from '@/components/easy/EasyButton.vue'
import EasyDialog from '@/components/easy/EasyDialog.vue'
import EasyInput from '@/components/easy/EasyInput.vue'
import EasySelect from '@/components/easy/EasySelect.vue'
import EasyStatus from '@/components/easy/EasyStatus.vue'
import { useNotificationStore } from '@/stores/notification'
import { formatDate } from '@/utils/format'
import { taskPriorityLabel, taskPriorityTone } from '@/utils/task'

/**
 * 项目看板：按状态列展示任务卡片，支持拖拽改状态（后端校验状态流与依赖）。
 *
 * 依赖阻塞：拖拽被后端拒绝（TASK_BLOCKED_BY_DEPENDENCIES）时向页面抛出事件，
 * 由页面统一弹出「忽略依赖并开始」弹窗（必须填写原因，写入审计）。
 */
const props = defineProps<{
  projectId: number
  canCreateTask: boolean
  canManageStatuses: boolean
}>()

const emit = defineEmits<{
  (e: 'open-task', taskId: number): void
  (e: 'create-task'): void
  (e: 'blocked-transition', payload: { taskId: number; statusId: number; statusName: string }): void
}>()

const notification = useNotificationStore()
const board = ref<TaskBoard | null>(null)
const loading = ref(false)
const loadError = ref<string | null>(null)

const columns = computed(() =>
  (board.value?.statuses ?? []).map((status) => ({
    status,
    tasks: (board.value?.tasks ?? []).filter((task) => task.status.id === status.id),
  })),
)

async function load(): Promise<void> {
  loading.value = true
  loadError.value = null
  try {
    board.value = await taskApi.board(props.projectId)
  } catch (error) {
    loadError.value = error instanceof ApiError ? error.message : '看板加载失败'
  } finally {
    loading.value = false
  }
}

onMounted(load)
defineExpose({ load })

// --- 拖拽 -------------------------------------------------------------------

const draggingId = ref<number | null>(null)
const dragOverStatusId = ref<number | null>(null)

function onDragStart(event: DragEvent, task: TaskCard): void {
  // 无管理权限的卡片不可拖动（后端仍会再次校验）
  if (!task.canManage) {
    event.preventDefault()
    return
  }
  draggingId.value = task.id
  event.dataTransfer?.setData('text/plain', String(task.id))
  if (event.dataTransfer) {
    event.dataTransfer.effectAllowed = 'move'
  }
}

function onDragEnd(): void {
  draggingId.value = null
  dragOverStatusId.value = null
}

function onDragOver(status: TaskStatusView): void {
  dragOverStatusId.value = status.id
}

async function onDrop(event: DragEvent, status: TaskStatusView): Promise<void> {
  event.preventDefault()
  const raw = event.dataTransfer?.getData('text/plain')
  const taskId = raw ? Number(raw) : draggingId.value
  dragOverStatusId.value = null
  draggingId.value = null
  if (!taskId || Number.isNaN(taskId)) return
  const task = board.value?.tasks.find((item) => item.id === taskId)
  if (!task || task.status.id === status.id) return
  await moveTask(taskId, status)
}

async function moveTask(taskId: number, status: TaskStatusView): Promise<void> {
  try {
    await taskApi.changeStatus(taskId, status.id)
    notification.success(`已移动到「${status.name}」`)
  } catch (error) {
    if (error instanceof ApiError && error.code === 'TASK_BLOCKED_BY_DEPENDENCIES') {
      emit('blocked-transition', { taskId, statusId: status.id, statusName: status.name })
      return
    }
    notification.error(error)
  } finally {
    await load()
  }
}

// --- 待派发审核 ---------------------------------------------------------------

const pendingOpen = ref(false)
const rejectReason = ref('')
const reviewingId = ref<number | null>(null)

async function approvePending(task: TaskCard): Promise<void> {
  reviewingId.value = task.id
  try {
    await taskApi.approveAssignment(task.id)
    notification.success(`「${task.title}」派发已通过`)
    await load()
    closePendingIfEmpty()
  } catch (error) {
    notification.error(error)
  } finally {
    reviewingId.value = null
  }
}

async function rejectPending(task: TaskCard): Promise<void> {
  if (!rejectReason.value.trim()) {
    notification.warning('请填写驳回原因')
    return
  }
  reviewingId.value = task.id
  try {
    await taskApi.rejectAssignment(task.id, rejectReason.value.trim())
    notification.success(`已驳回「${task.title}」的派发`)
    rejectReason.value = ''
    await load()
    closePendingIfEmpty()
  } catch (error) {
    notification.error(error)
  } finally {
    reviewingId.value = null
  }
}

/** 审核完最后一条后自动关闭弹窗（列表为空时保留弹窗会变成空面板） */
function closePendingIfEmpty(): void {
  if ((board.value?.pendingAssignments.length ?? 0) === 0) {
    pendingOpen.value = false
  }
}

// --- 自定义状态 ---------------------------------------------------------------

const statusOpen = ref(false)
const statusName = ref('')
const statusType = ref<TaskStatusType>('TODO')
const savingStatus = ref(false)

const statusTypeOptions = [
  { label: '待处理', value: 'TODO' },
  { label: '进行中', value: 'ACTIVE' },
  { label: '待审核', value: 'REVIEW' },
  { label: '已完成', value: 'DONE' },
  { label: '已关闭（终态）', value: 'CLOSED' },
]

async function submitStatus(): Promise<void> {
  if (!statusName.value.trim()) {
    notification.warning('请输入状态名称')
    return
  }
  savingStatus.value = true
  try {
    await taskApi.createStatus(props.projectId, statusName.value.trim(), statusType.value)
    notification.success('状态已添加（统计仍按系统类型归类）')
    statusOpen.value = false
    statusName.value = ''
    statusType.value = 'TODO'
    await load()
  } catch (error) {
    notification.error(error)
  } finally {
    savingStatus.value = false
  }
}
</script>

<template>
  <div class="board-wrap">
    <div class="board-toolbar">
      <div class="board-toolbar__info">
        <span v-if="loading" class="easy-text-xs easy-muted">加载中…</span>
        <span v-else class="easy-text-xs easy-muted">
          共 {{ board?.tasks.length ?? 0 }} 个任务 · 拖拽卡片改变状态（自动校验状态流与依赖）
        </span>
      </div>
      <div class="board-toolbar__actions">
        <EasyButton v-if="canManageStatuses" size="sm" @click="statusOpen = true">添加状态</EasyButton>
        <EasyButton v-if="canCreateTask" variant="primary" size="sm" @click="emit('create-task')">
          新建任务
        </EasyButton>
      </div>
    </div>

    <div v-if="loadError" class="easy-card board-error">
      <span>{{ loadError }}</span>
      <EasyButton size="sm" @click="load">重试</EasyButton>
    </div>

    <template v-else>
      <div
        v-if="(board?.pendingAssignments.length ?? 0) > 0"
        class="pending-banner"
        role="button"
        @click="pendingOpen = true"
      >
        <span>
          有 {{ board?.pendingAssignments.length }} 个任务等待派发审核（成员创建并指派给他人，需项目负责人确认）
        </span>
        <EasyButton size="sm" @click.stop="pendingOpen = true">去审核</EasyButton>
      </div>

      <div class="board" :class="{ 'board--loading': loading && !board }">
        <section
          v-for="column in columns"
          :key="column.status.id"
          class="board-column"
          :class="{ 'board-column--over': dragOverStatusId === column.status.id }"
          @dragover.prevent="onDragOver(column.status)"
          @dragleave="dragOverStatusId = null"
          @drop.prevent="onDrop($event, column.status)"
        >
          <header class="board-column__head">
            <span class="board-column__name">{{ column.status.name }}</span>
            <span class="board-column__count">{{ column.tasks.length }}</span>
          </header>
          <div class="board-column__body">
            <article
              v-for="task in column.tasks"
              :key="task.id"
              class="task-card"
              :class="{
                'task-card--dragging': draggingId === task.id,
                'task-card--blocked': task.blocked,
                'task-card--readonly': !task.canManage,
              }"
              :draggable="task.canManage"
              @dragstart="onDragStart($event, task)"
              @dragend="onDragEnd"
              @click="emit('open-task', task.id)"
            >
              <div class="task-card__title">{{ task.title }}</div>
              <div class="task-card__badges">
                <EasyStatus :label="taskPriorityLabel(task.priority)" :tone="taskPriorityTone(task.priority)" />
                <span v-if="task.blocked" class="task-card__blocked" title="存在未完成的前置依赖">
                  阻塞 {{ task.blockerCount }}
                </span>
              </div>
              <div class="task-card__progress">
                <div class="progress">
                  <div class="progress__bar" :style="{ width: `${task.progress}%` }" />
                </div>
                <span class="progress__value">{{ task.progress }}%</span>
              </div>
              <div class="task-card__foot">
                <span class="task-card__person">
                  <EasyAvatar
                    :name="task.primaryAssignee.displayName"
                    :src="task.primaryAssignee.avatarUrl ?? null"
                    size="sm"
                  />
                  <span class="task-card__person-name">{{ task.primaryAssignee.displayName }}</span>
                </span>
                <span class="task-card__due" :class="{ 'task-card__due--overdue': task.overdue }">
                  {{ formatDate(task.plannedEndAt) }}
                </span>
              </div>
            </article>

            <p v-if="column.tasks.length === 0" class="board-column__empty">暂无任务</p>
          </div>
        </section>
      </div>
    </template>

    <!-- 派发审核 -->
    <EasyDialog v-model="pendingOpen" title="待派发审核" :width="620">
      <div class="pending-list">
        <p class="easy-text-xs easy-muted">
          普通成员创建并指派给他人的任务需项目负责人审核通过后才正式生效（被指派成员无需再次接受）。
        </p>
        <div v-for="task in board?.pendingAssignments ?? []" :key="task.id" class="pending-row">
          <div class="pending-row__main">
            <span class="pending-row__title">{{ task.title }}</span>
            <span class="easy-text-xs easy-muted">
              指派给 {{ task.primaryAssignee.displayName }} · 截止 {{ formatDate(task.plannedEndAt) }}
            </span>
          </div>
          <div class="pending-row__actions">
            <EasyButton
              variant="primary"
              size="sm"
              :loading="reviewingId === task.id"
              @click="approvePending(task)"
            >
              通过
            </EasyButton>
            <EasyButton size="sm" :loading="reviewingId === task.id" @click="rejectPending(task)">
              驳回
            </EasyButton>
          </div>
        </div>
        <EasyInput v-model="rejectReason" label="驳回原因（驳回时必填，写入审计）" placeholder="例如：优先级不匹配" />
      </div>
    </EasyDialog>

    <!-- 添加自定义状态 -->
    <EasyDialog v-model="statusOpen" title="添加任务状态" :width="460">
      <div class="form-stack">
        <EasyInput v-model="statusName" label="状态名称" required placeholder="例如：Code Review" />
        <EasySelect
          v-model="statusType"
          label="系统类型（统计依据）"
          :options="statusTypeOptions"
          :clearable="false"
        />
        <p class="easy-text-xs easy-muted">
          自定义状态只影响展示名称，所有统计与流程判断都映射到系统统一类型。
        </p>
        <div class="form-stack__actions">
          <EasyButton variant="primary" :loading="savingStatus" @click="submitStatus">添加状态</EasyButton>
        </div>
      </div>
    </EasyDialog>
  </div>
</template>

<style scoped>
.board-wrap {
  display: flex;
  flex-direction: column;
  gap: var(--easy-space-4);
}

.board-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--easy-space-3);
  flex-wrap: wrap;
}

.board-toolbar__actions {
  display: flex;
  gap: var(--easy-space-2);
}

.board-error {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--easy-space-4);
  padding: var(--easy-space-4) var(--easy-space-5);
  color: var(--easy-danger);
  font-size: var(--easy-text-sm);
}

.pending-banner {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--easy-space-3);
  padding: var(--easy-space-3) var(--easy-space-4);
  border: 1px solid var(--easy-warning);
  border-radius: var(--easy-radius-md);
  background: var(--easy-warning-bg);
  color: var(--easy-warning);
  font-size: var(--easy-text-sm);
  cursor: pointer;
}

.board {
  display: flex;
  gap: var(--easy-space-4);
  align-items: flex-start;
  overflow-x: auto;
  padding-bottom: var(--easy-space-2);
}

.board-column {
  flex: none;
  width: 272px;
  display: flex;
  flex-direction: column;
  gap: var(--easy-space-3);
  padding: var(--easy-space-3);
  border: 1px solid var(--easy-border);
  border-radius: var(--easy-radius-lg);
  background: var(--easy-surface-sunken);
  transition: border-color var(--easy-transition-fast), background var(--easy-transition-fast);
}

.board-column--over {
  border-color: var(--easy-brand);
  background: var(--easy-brand-subtle);
}

.board-column__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 var(--easy-space-1);
}

.board-column__name {
  font-size: var(--easy-text-sm);
  font-weight: 600;
}

.board-column__count {
  font-size: var(--easy-text-xs);
  color: var(--easy-text-3);
  font-variant-numeric: tabular-nums;
}

.board-column__body {
  display: flex;
  flex-direction: column;
  gap: var(--easy-space-2);
  min-height: 48px;
}

.board-column__empty {
  padding: var(--easy-space-3) 0;
  text-align: center;
  color: var(--easy-text-3);
  font-size: var(--easy-text-xs);
}

.task-card {
  display: flex;
  flex-direction: column;
  gap: var(--easy-space-2);
  padding: var(--easy-space-3);
  border: 1px solid var(--easy-border);
  border-radius: var(--easy-radius-md);
  background: var(--easy-surface);
  box-shadow: var(--easy-shadow-xs);
  cursor: pointer;
  transition: border-color var(--easy-transition-fast), box-shadow var(--easy-transition-fast);
}

.task-card:hover {
  border-color: var(--easy-brand-subtle-border);
  box-shadow: var(--easy-shadow-sm);
}

.task-card--dragging {
  opacity: 0.5;
}

.task-card--blocked {
  border-left: 3px solid var(--easy-warning);
}

.task-card--readonly {
  cursor: default;
}

.task-card__title {
  font-size: var(--easy-text-sm);
  font-weight: 500;
  line-height: var(--easy-leading-normal);
  color: var(--easy-text-1);
}

.task-card__badges {
  display: flex;
  align-items: center;
  gap: var(--easy-space-2);
  flex-wrap: wrap;
}

.task-card__blocked {
  display: inline-flex;
  align-items: center;
  padding: 1px 6px;
  border-radius: var(--easy-radius-full);
  border: 1px solid var(--easy-warning);
  color: var(--easy-warning);
  font-size: var(--easy-text-xs);
}

.task-card__progress {
  display: flex;
  align-items: center;
  gap: var(--easy-space-2);
}

.progress {
  flex: 1;
  height: 4px;
  border-radius: var(--easy-radius-full);
  background: var(--easy-surface-sunken);
  overflow: hidden;
}

.progress__bar {
  height: 100%;
  border-radius: var(--easy-radius-full);
  background: var(--easy-brand);
  transition: width var(--easy-transition-base);
}

.progress__value {
  font-size: var(--easy-text-xs);
  color: var(--easy-text-3);
  font-variant-numeric: tabular-nums;
}

.task-card__foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--easy-space-2);
}

.task-card__person {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  min-width: 0;
}

.task-card__person-name {
  font-size: var(--easy-text-xs);
  color: var(--easy-text-2);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.task-card__due {
  font-size: var(--easy-text-xs);
  color: var(--easy-text-3);
  font-variant-numeric: tabular-nums;
  flex: none;
}

.task-card__due--overdue {
  color: var(--easy-danger);
  font-weight: 600;
}

.pending-list {
  display: flex;
  flex-direction: column;
  gap: var(--easy-space-3);
}

.pending-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--easy-space-3);
  padding: var(--easy-space-3);
  border: 1px solid var(--easy-border);
  border-radius: var(--easy-radius-md);
}

.pending-row__main {
  display: flex;
  flex-direction: column;
  gap: 4px;
  min-width: 0;
}

.pending-row__title {
  font-size: var(--easy-text-sm);
  font-weight: 500;
}

.pending-row__actions {
  display: flex;
  gap: var(--easy-space-2);
  flex: none;
}

.form-stack {
  display: flex;
  flex-direction: column;
  gap: var(--easy-space-4);
}

.form-stack__actions {
  display: flex;
  justify-content: flex-start;
}
</style>