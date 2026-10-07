<script setup lang="ts">
import { computed, ref, watch } from 'vue'

import { ApiError } from '@/api/errors'
import { taskApi } from '@/api/modules/tasks'
import type { ProjectMemberView, TaskCard, TaskDetail, TaskStatusView } from '@/api/types'
import EasyAvatar from '@/components/easy/EasyAvatar.vue'
import EasyButton from '@/components/easy/EasyButton.vue'
import EasyDialog from '@/components/easy/EasyDialog.vue'
import EasyDrawer from '@/components/easy/EasyDrawer.vue'
import EasyEmpty from '@/components/easy/EasyEmpty.vue'
import EasyInput from '@/components/easy/EasyInput.vue'
import EasySelect from '@/components/easy/EasySelect.vue'
import EasyStatus from '@/components/easy/EasyStatus.vue'
import { useNotificationStore } from '@/stores/notification'
import { formatDate, formatDateTime, toDateInputValue, toIsoInstant } from '@/utils/format'
import { progressModeLabel, taskPriorityLabel, taskPriorityTone, taskStatusTypeTone } from '@/utils/task'

/**
 * 任务详情（右侧 Drawer，URL 由页面同步 ?task=<id>）。
 *
 * 状态 / 进度 / 负责人 / 协作成员 / 子任务 / 依赖全部走后端接口并在后端复查权限；
 * 依赖阻塞时向页面抛出事件，由页面统一弹出「忽略依赖并开始」（必须填写原因）。
 */
const props = defineProps<{
  taskId: number
  projectId: number
  members: ProjectMemberView[]
}>()

const emit = defineEmits<{
  (e: 'close'): void
  (e: 'changed'): void
  (e: 'open-task', taskId: number): void
  (e: 'blocked-transition', payload: { taskId: number; statusId: number; statusName: string }): void
}>()

const notification = useNotificationStore()
const detail = ref<TaskDetail | null>(null)
const statuses = ref<TaskStatusView[]>([])
const loading = ref(false)
const loadError = ref<string | null>(null)
const saving = ref(false)
const open = ref(true)

watch(open, (value) => {
  if (!value) emit('close')
})

async function load(): Promise<void> {
  loading.value = true
  loadError.value = null
  try {
    const [taskDetail, statusList] = await Promise.all([
      taskApi.detail(props.taskId),
      statuses.value.length > 0 ? Promise.resolve(statuses.value) : taskApi.statuses(props.projectId),
    ])
    detail.value = taskDetail
    statuses.value = statusList
    syncForms()
  } catch (error) {
    loadError.value = error instanceof ApiError ? error.message : '任务加载失败'
  } finally {
    loading.value = false
  }
}

defineExpose({ reload: load })

watch(
  () => props.taskId,
  () => {
    statuses.value = []
    void load()
  },
  { immediate: true },
)

// --- 基本信息 -----------------------------------------------------------------

const infoForm = ref({ title: '', description: '', priority: 'MEDIUM', plannedStartAt: '', plannedEndAt: '', progressMode: 'MANUAL' })
const editingTitle = ref(false)

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

const statusOptions = computed(() =>
  statuses.value.map((status) => ({ label: status.name, value: status.id })),
)

const memberOptions = computed(() =>
  props.members.map((member) => ({
    label: `${member.displayName}${member.role === 'OWNER' ? '（项目负责人）' : ''}`,
    value: member.userId,
  })),
)

function syncForms(): void {
  const task = detail.value
  if (!task) return
  infoForm.value = {
    title: task.title,
    description: task.description ?? '',
    priority: task.priority,
    plannedStartAt: toDateInputValue(task.plannedStartAt),
    plannedEndAt: toDateInputValue(task.plannedEndAt),
    progressMode: task.progressMode,
  }
  statusSelected.value = task.status.id
  progressInput.value = task.progress
  primaryId.value = task.primaryAssignee.id
  deputyId.value = task.deputyAssignee?.id ?? null
  collaboratorIds.value = task.collaborators.map((member) => member.id)
  dependencyCandidateId.value = null
  editingTitle.value = false
}

async function saveInfo(): Promise<void> {
  if (!infoForm.value.title.trim()) {
    notification.warning('请输入任务标题')
    return
  }
  saving.value = true
  try {
    detail.value = await taskApi.update(props.taskId, {
      title: infoForm.value.title.trim(),
      description: infoForm.value.description.trim() || undefined,
      priority: infoForm.value.priority as TaskDetail['priority'],
      plannedStartAt: toIsoInstant(infoForm.value.plannedStartAt),
      plannedEndAt: toIsoInstant(infoForm.value.plannedEndAt),
      progressMode: infoForm.value.progressMode as TaskDetail['progressMode'],
    })
    syncForms()
    emit('changed')
    notification.success('任务信息已保存')
  } catch (error) {
    notification.error(error)
  } finally {
    saving.value = false
  }
}

// --- 状态 ---------------------------------------------------------------------

const statusSelected = ref<number | null>(null)

async function changeStatus(statusId: number): Promise<void> {
  const target = statuses.value.find((status) => status.id === statusId)
  try {
    detail.value = await taskApi.changeStatus(props.taskId, statusId)
    syncForms()
    emit('changed')
    notification.success(`状态已切换为「${target?.name ?? ''}」`)
  } catch (error) {
    if (error instanceof ApiError && error.code === 'TASK_BLOCKED_BY_DEPENDENCIES') {
      statusSelected.value = detail.value?.status.id ?? null
      emit('blocked-transition', { taskId: props.taskId, statusId, statusName: target?.name ?? '' })
      return
    }
    statusSelected.value = detail.value?.status.id ?? null
    notification.error(error)
  }
}

// --- 进度 ---------------------------------------------------------------------

const progressInput = ref(0)

async function saveProgress(): Promise<void> {
  try {
    detail.value = await taskApi.changeProgress(props.taskId, progressInput.value)
    syncForms()
    emit('changed')
    notification.success('进度已更新')
  } catch (error) {
    notification.error(error)
  }
}

// --- 负责人与协作成员 -----------------------------------------------------------

const primaryId = ref<number | null>(null)
const deputyId = ref<number | null>(null)
const collaboratorIds = ref<number[]>([])

async function saveAssignees(): Promise<void> {
  if (!primaryId.value) {
    notification.warning('请选择主负责人')
    return
  }
  try {
    detail.value = await taskApi.changeAssignees(props.taskId, primaryId.value, deputyId.value)
    syncForms()
    emit('changed')
    notification.success('负责人已更新')
  } catch (error) {
    notification.error(error)
  }
}

async function saveCollaborators(): Promise<void> {
  try {
    detail.value = await taskApi.changeCollaborators(props.taskId, collaboratorIds.value)
    syncForms()
    emit('changed')
    notification.success('协作成员已更新')
  } catch (error) {
    notification.error(error)
  }
}

// --- 子任务 -------------------------------------------------------------------

const subtaskOpen = ref(false)
const subtaskForm = ref({ title: '', primaryAssigneeId: null as number | null })

async function submitSubtask(): Promise<void> {
  if (!subtaskForm.value.title.trim()) {
    notification.warning('请输入子任务标题')
    return
  }
  saving.value = true
  try {
    detail.value = await taskApi.createSubtask(props.taskId, {
      title: subtaskForm.value.title.trim(),
      primaryAssigneeId: subtaskForm.value.primaryAssigneeId,
    })
    syncForms()
    subtaskOpen.value = false
    subtaskForm.value = { title: '', primaryAssigneeId: null }
    emit('changed')
    notification.success('子任务已添加')
  } catch (error) {
    notification.error(error)
  } finally {
    saving.value = false
  }
}

function openSubtask(subtask: TaskCard): void {
  emit('open-task', subtask.id)
}

// --- 依赖 ---------------------------------------------------------------------

const dependencyCandidateId = ref<number | null>(null)
const dependencyOptions = ref<Array<{ label: string; value: number }>>([])

async function ensureDependencyOptions(): Promise<void> {
  if (dependencyOptions.value.length > 0) return
  try {
    const page = await taskApi.list(props.projectId, { size: 100 })
    const excluded = new Set<number>([props.taskId, ...(detail.value?.dependencies ?? []).map((item) => item.dependsOnTaskId)])
    dependencyOptions.value = page.items
      .filter((task) => !excluded.has(task.id) && !task.parentId)
      .map((task) => ({ label: task.title, value: task.id }))
  } catch (error) {
    notification.error(error)
  }
}

async function addDependency(): Promise<void> {
  if (!dependencyCandidateId.value) {
    notification.warning('请选择前置任务')
    return
  }
  try {
    detail.value = await taskApi.addDependency(props.taskId, dependencyCandidateId.value)
    dependencyOptions.value = []
    syncForms()
    emit('changed')
    notification.success('前置依赖已添加')
  } catch (error) {
    notification.error(error)
  }
}

async function removeDependency(dependsOnTaskId: number): Promise<void> {
  try {
    detail.value = await taskApi.removeDependency(props.taskId, dependsOnTaskId)
    dependencyOptions.value = []
    syncForms()
    emit('changed')
    notification.success('前置依赖已移除')
  } catch (error) {
    notification.error(error)
  }
}

/** 未完成的前置依赖（用于「忽略依赖并开始」入口） */
const unfinishedDependencies = computed(() =>
  (detail.value?.dependencies ?? []).filter((dependency) => !dependency.finished),
)

const overrideTarget = computed(() => statuses.value.find((status) => status.systemType === 'ACTIVE') ?? null)

function requestOverride(): void {
  if (!overrideTarget.value) return
  emit('blocked-transition', {
    taskId: props.taskId,
    statusId: overrideTarget.value.id,
    statusName: overrideTarget.value.name,
  })
}

// --- 派发审核 -----------------------------------------------------------------

const rejectOpen = ref(false)
const rejectReason = ref('')

async function approveAssignment(): Promise<void> {
  try {
    detail.value = await taskApi.approveAssignment(props.taskId)
    syncForms()
    emit('changed')
    notification.success('派发审核已通过，任务正式生效')
  } catch (error) {
    notification.error(error)
  }
}

async function rejectAssignment(): Promise<void> {
  if (!rejectReason.value.trim()) {
    notification.warning('请填写驳回原因')
    return
  }
  try {
    detail.value = await taskApi.rejectAssignment(props.taskId, rejectReason.value.trim())
    syncForms()
    rejectOpen.value = false
    rejectReason.value = ''
    emit('changed')
    notification.success('已驳回该派发')
  } catch (error) {
    notification.error(error)
  }
}

const isPending = computed(() => detail.value?.assignmentState === 'PENDING_ASSIGNMENT')
const isRejected = computed(() => detail.value?.assignmentState === 'REJECTED')
</script>

<template>
  <EasyDrawer v-model="open" :size="560" title="任务详情">
    <div v-if="loadError" class="drawer-error">
      <p>{{ loadError }}</p>
      <EasyButton size="sm" @click="load">重试</EasyButton>
    </div>

    <div v-else-if="loading && !detail" class="easy-muted">正在加载任务…</div>

    <div v-else-if="detail" class="task-detail">
      <!-- 派发状态提示 -->
      <div v-if="isPending" class="state-banner state-banner--pending">
        <span>该任务由成员创建并指派给他人，正在等待项目负责人审核（审核通过后正式生效）。</span>
        <div v-if="detail.permissions.canReviewAssignment" class="state-banner__actions">
          <EasyButton variant="primary" size="sm" @click="approveAssignment">审核通过</EasyButton>
          <EasyButton size="sm" @click="rejectOpen = true">驳回</EasyButton>
        </div>
      </div>
      <div v-else-if="isRejected" class="state-banner state-banner--rejected">
        该任务派发已被驳回，不生效（保留记录与审计）。如需继续推进，请调整后重新创建。
      </div>

      <!-- 标题 -->
      <header class="detail-head">
        <template v-if="editingTitle && detail.permissions.canManage">
          <EasyInput v-model="infoForm.title" label="任务标题" required />
        </template>
        <template v-else>
          <h2 class="detail-head__title">{{ detail.title }}</h2>
        </template>
        <div class="detail-head__badges">
          <EasyStatus :label="detail.status.name" :tone="taskStatusTypeTone(detail.status.systemType)" />
          <EasyStatus :label="taskPriorityLabel(detail.priority)" :tone="taskPriorityTone(detail.priority)" />
          <span v-if="detail.parentId" class="detail-head__subtask-tag">子任务</span>
        </div>
        <p v-if="detail.parentId && detail.parentTitle" class="detail-head__parent">
          上级任务：{{ detail.parentTitle }}
          <button type="button" class="link-btn" @click="emit('open-task', detail.parentId)">打开</button>
        </p>
      </header>

      <!-- 状态与进度 -->
      <section class="detail-section">
        <div class="detail-grid">
          <EasySelect
            v-model="statusSelected"
            label="状态"
            :options="statusOptions"
            :clearable="false"
            :disabled="!detail.permissions.canManage"
            @change="(value: string | number | Array<string | number> | null | undefined) => changeStatus(Number(value))"
          />
          <div class="field">
            <span class="field__label">系统类型</span>
            <span class="field__value">{{ detail.status.systemType }}</span>
            <span class="field__hint">统计与流程依据系统类型，自定义名称只影响展示</span>
          </div>
        </div>

        <div class="progress-block">
          <div class="progress-block__row">
            <span class="field__label">进度（{{ progressModeLabel(detail.progressMode) }}）</span>
            <span class="progress-block__value">{{ detail.progress }}%</span>
          </div>
          <div v-if="detail.permissions.canEditProgress" class="progress-block__controls">
            <el-slider v-model="progressInput" :min="0" :max="100" :step="5" class="progress-block__slider" />
            <EasyButton size="sm" @click="saveProgress">保存进度</EasyButton>
          </div>
          <div v-else class="progress progress--block">
            <div class="progress__bar" :style="{ width: `${detail.progress}%` }" />
          </div>
          <p v-if="detail.progressMode === 'AUTO'" class="field__hint">
            自动模式：按一级子任务完成比例计算，完成后自动刷新。
          </p>
        </div>

        <div v-if="detail.blocked" class="blocked-box">
          <div class="blocked-box__title">存在 {{ detail.blockerCount }} 个未完成的前置依赖</div>
          <ul class="blocked-box__list">
            <li v-for="dependency in unfinishedDependencies" :key="dependency.id">
              <span>{{ dependency.title }}</span>
              <span class="easy-muted">{{ dependency.statusName }}</span>
            </li>
          </ul>
          <div v-if="detail.permissions.canManage && detail.status.systemType === 'TODO' && overrideTarget" class="blocked-box__actions">
            <EasyButton size="sm" @click="requestOverride">忽略依赖并开始</EasyButton>
            <span class="field__hint">需填写原因并写入审计</span>
          </div>
        </div>
      </section>

      <!-- 基本信息 -->
      <section class="detail-section">
        <div class="detail-section__head">
          <span class="detail-section__title">基本信息</span>
          <EasyButton v-if="detail.permissions.canManage" size="sm" @click="editingTitle = !editingTitle">
            {{ editingTitle ? '收起编辑' : '编辑' }}
          </EasyButton>
        </div>

        <template v-if="editingTitle && detail.permissions.canManage">
          <EasyInput v-model="infoForm.description" label="任务描述" type="textarea" :rows="3" />
          <div class="detail-grid">
            <EasySelect v-model="infoForm.priority" label="优先级" :options="priorityOptions" :clearable="false" />
            <EasySelect
              v-model="infoForm.progressMode"
              label="进度模式"
              :options="progressModeOptions"
              :clearable="false"
            />
          </div>
          <div class="detail-grid">
            <EasyInput v-model="infoForm.plannedStartAt" label="计划开始" type="date" />
            <EasyInput v-model="infoForm.plannedEndAt" label="计划结束" type="date" />
          </div>
          <div class="detail-section__actions">
            <EasyButton variant="primary" size="sm" :loading="saving" @click="saveInfo">保存基本信息</EasyButton>
          </div>
        </template>
        <template v-else>
          <p class="detail-desc">{{ detail.description || '暂无描述' }}</p>
          <div class="detail-info">
            <div class="info-row"><span class="info-row__label">计划时间</span>
              <span>{{ formatDate(detail.plannedStartAt) }} → {{ formatDate(detail.plannedEndAt) }}</span>
            </div>
            <div class="info-row"><span class="info-row__label">实际开始</span>
              <span>{{ formatDateTime(detail.actualStartAt) }}</span>
            </div>
            <div class="info-row"><span class="info-row__label">完成时间</span>
              <span>{{ formatDateTime(detail.completedAt) }}</span>
            </div>
            <div class="info-row"><span class="info-row__label">创建时间</span>
              <span>{{ formatDateTime(detail.createdAt) }}</span>
            </div>
          </div>
        </template>
      </section>

      <!-- 负责人 -->
      <section class="detail-section">
        <div class="detail-section__head">
          <span class="detail-section__title">负责人</span>
        </div>
        <template v-if="detail.permissions.canManage">
          <div class="detail-grid">
            <EasySelect
              v-model="primaryId"
              label="主负责人"
              :options="memberOptions"
              :clearable="false"
              :disabled="!detail.permissions.canFullControl"
            />
            <EasySelect v-model="deputyId" label="副负责人" :options="memberOptions" placeholder="不设置" />
          </div>
          <div class="detail-section__actions">
            <EasyButton size="sm" :disabled="!detail.permissions.canFullControl && primaryId !== detail.primaryAssignee.id" @click="saveAssignees">
              保存负责人
            </EasyButton>
            <span v-if="!detail.permissions.canFullControl" class="field__hint">只有主负责人或项目负责人可以修改主负责人</span>
          </div>
        </template>
        <div v-else class="person-row">
          <span class="person-row__item">
            <EasyAvatar :name="detail.primaryAssignee.displayName" :src="detail.primaryAssignee.avatarUrl ?? null" size="sm" />
            {{ detail.primaryAssignee.displayName }}
            <span class="easy-muted">主负责人</span>
          </span>
          <span v-if="detail.deputyAssignee" class="person-row__item">
            <EasyAvatar :name="detail.deputyAssignee.displayName" :src="detail.deputyAssignee.avatarUrl ?? null" size="sm" />
            {{ detail.deputyAssignee.displayName }}
            <span class="easy-muted">副负责人</span>
          </span>
        </div>
      </section>

      <!-- 协作成员 -->
      <section class="detail-section">
        <div class="detail-section__head">
          <span class="detail-section__title">协作成员（{{ detail.collaborators.length }}）</span>
        </div>
        <template v-if="detail.permissions.canManageCollaborators">
          <EasySelect v-model="collaboratorIds" label="协作成员" :options="memberOptions" multiple placeholder="选择协作成员" />
          <div class="detail-section__actions">
            <EasyButton size="sm" @click="saveCollaborators">保存协作成员</EasyButton>
            <span class="field__hint">协作成员可查看任务、评论并完成自己负责的子任务</span>
          </div>
        </template>
        <div v-else-if="detail.collaborators.length > 0" class="person-row">
          <span v-for="member in detail.collaborators" :key="member.id" class="person-row__item">
            <EasyAvatar :name="member.displayName" :src="member.avatarUrl ?? null" size="sm" />
            {{ member.displayName }}
          </span>
        </div>
        <p v-else class="easy-text-xs easy-muted">暂无协作成员</p>
      </section>

      <!-- 子任务 -->
      <section v-if="!detail.parentId" class="detail-section">
        <div class="detail-section__head">
          <span class="detail-section__title">子任务（{{ detail.subtasks.length }}）</span>
          <EasyButton v-if="detail.permissions.canManage" size="sm" @click="subtaskOpen = true">
            添加子任务
          </EasyButton>
        </div>
        <p class="field__hint">v0.1.0 只支持一级子任务；进度 AUTO 模式按子任务完成比例计算。</p>
        <ul v-if="detail.subtasks.length > 0" class="subtask-list">
          <li v-for="subtask in detail.subtasks" :key="subtask.id" class="subtask-row" @click="openSubtask(subtask)">
            <span class="subtask-row__title">{{ subtask.title }}</span>
            <EasyStatus :label="subtask.status.name" :tone="taskStatusTypeTone(subtask.status.systemType)" />
            <span class="subtask-row__assignee">{{ subtask.primaryAssignee.displayName }}</span>
            <span class="subtask-row__progress">{{ subtask.progress }}%</span>
          </li>
        </ul>
        <p v-else class="easy-text-xs easy-muted">暂无子任务</p>
      </section>

      <!-- 依赖 -->
      <section v-if="!detail.parentId" class="detail-section">
        <div class="detail-section__head">
          <span class="detail-section__title">前置依赖（{{ detail.dependencies.length }}）</span>
        </div>
        <ul v-if="detail.dependencies.length > 0" class="dependency-list">
          <li v-for="dependency in detail.dependencies" :key="dependency.id" class="dependency-row">
            <span class="dependency-row__title">{{ dependency.title }}</span>
            <span class="dependency-row__status" :class="{ 'is-finished': dependency.finished }">
              {{ dependency.statusName }}
            </span>
            <span class="dependency-row__assignee">{{ dependency.primaryAssignee?.displayName ?? '—' }}</span>
            <EasyButton
              v-if="detail.permissions.canManageDependencies"
              size="sm"
              @click="removeDependency(dependency.dependsOnTaskId)"
            >
              移除
            </EasyButton>
          </li>
        </ul>
        <p v-else class="easy-text-xs easy-muted">暂无前置依赖</p>

        <template v-if="detail.permissions.canManageDependencies">
          <div class="dependency-add">
            <EasySelect
              v-model="dependencyCandidateId"
              :options="dependencyOptions"
              placeholder="选择前置任务（同项目）"
              class="dependency-add__select"
              @click="ensureDependencyOptions"
            />
            <EasyButton size="sm" :disabled="dependencyCandidateId === null" @click="addDependency">
              添加依赖
            </EasyButton>
          </div>
          <p class="field__hint">存在未完成前置依赖时无法开始任务；系统会自动检测循环依赖。</p>
        </template>
      </section>

      <!-- 评论与附件：Phase 5 -->
      <section class="detail-section">
        <div class="detail-section__head"><span class="detail-section__title">评论</span></div>
        <EasyEmpty compact title="评论将在 Phase 5 交付" phase="Phase 5" description="评论、回复、@成员与编辑历史将在 Phase 5 交付。" />
      </section>
      <section class="detail-section">
        <div class="detail-section__head"><span class="detail-section__title">附件</span></div>
        <EasyEmpty compact title="附件将在 Phase 5 交付" phase="Phase 5" description="文件上传、权限下载与附件元数据将在 Phase 5 交付。" />
      </section>

      <p class="detail-footer">最近更新：{{ formatDateTime(detail.updatedAt) }}</p>
    </div>

    <!-- 添加子任务 -->
    <EasyDialog v-model="subtaskOpen" title="添加子任务" :width="460">
      <div class="form-stack">
        <EasyInput v-model="subtaskForm.title" label="子任务标题" required />
        <EasySelect
          v-model="subtaskForm.primaryAssigneeId"
          label="子任务负责人"
          :options="memberOptions"
          placeholder="默认为创建者本人"
        />
        <div class="form-stack__actions">
          <EasyButton variant="primary" :loading="saving" @click="submitSubtask">添加</EasyButton>
        </div>
      </div>
    </EasyDialog>

    <!-- 驳回派发 -->
    <EasyDialog v-model="rejectOpen" title="驳回派发" :width="460">
      <div class="form-stack">
        <EasyInput v-model="rejectReason" label="驳回原因（必填，写入审计）" type="textarea" :rows="3" />
        <div class="form-stack__actions">
          <EasyButton variant="primary" @click="rejectAssignment">确认驳回</EasyButton>
        </div>
      </div>
    </EasyDialog>
  </EasyDrawer>
</template>

<style scoped>
.task-detail {
  display: flex;
  flex-direction: column;
  gap: var(--easy-space-5);
}

.drawer-error {
  display: flex;
  flex-direction: column;
  gap: var(--easy-space-3);
  color: var(--easy-danger);
}

.state-banner {
  display: flex;
  flex-direction: column;
  gap: var(--easy-space-3);
  padding: var(--easy-space-3) var(--easy-space-4);
  border-radius: var(--easy-radius-md);
  font-size: var(--easy-text-sm);
}

.state-banner--pending {
  border: 1px solid var(--easy-warning);
  background: var(--easy-warning-bg);
  color: var(--easy-warning);
}

.state-banner--rejected {
  border: 1px solid var(--easy-danger);
  background: var(--easy-danger-bg);
  color: var(--easy-danger);
}

.state-banner__actions {
  display: flex;
  gap: var(--easy-space-2);
}

.detail-head {
  display: flex;
  flex-direction: column;
  gap: var(--easy-space-2);
}

.detail-head__title {
  font-size: var(--easy-text-lg);
  font-weight: 600;
  line-height: var(--easy-leading-normal);
}

.detail-head__badges {
  display: flex;
  align-items: center;
  gap: var(--easy-space-2);
  flex-wrap: wrap;
}

.detail-head__subtask-tag {
  font-size: var(--easy-text-xs);
  color: var(--easy-text-3);
  border: 1px dashed var(--easy-border);
  border-radius: var(--easy-radius-full);
  padding: 1px 8px;
}

.detail-head__parent {
  font-size: var(--easy-text-xs);
  color: var(--easy-text-3);
}

.link-btn {
  border: none;
  background: transparent;
  color: var(--easy-brand-text);
  font-size: inherit;
  cursor: pointer;
  padding: 0 2px;
}

.detail-section {
  display: flex;
  flex-direction: column;
  gap: var(--easy-space-3);
  padding-top: var(--easy-space-4);
  border-top: 1px solid var(--easy-border);
}

.detail-section__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--easy-space-2);
}

.detail-section__title {
  font-size: var(--easy-text-sm);
  font-weight: 600;
}

.detail-section__actions {
  display: flex;
  align-items: center;
  gap: var(--easy-space-2);
  flex-wrap: wrap;
}

.detail-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: var(--easy-space-3);
}

.field {
  display: flex;
  flex-direction: column;
  gap: var(--easy-space-2);
}

.field__label {
  font-size: var(--easy-text-sm);
  font-weight: 500;
  color: var(--easy-text-2);
}

.field__value {
  font-size: var(--easy-text-sm);
}

.field__hint {
  font-size: var(--easy-text-xs);
  color: var(--easy-text-3);
}

.progress-block {
  display: flex;
  flex-direction: column;
  gap: var(--easy-space-2);
}

.progress-block__row {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.progress-block__value {
  font-size: var(--easy-text-sm);
  font-variant-numeric: tabular-nums;
}

.progress-block__controls {
  display: flex;
  align-items: center;
  gap: var(--easy-space-3);
}

.progress-block__slider {
  flex: 1;
}

.progress {
  height: 6px;
  border-radius: var(--easy-radius-full);
  background: var(--easy-surface-sunken);
  overflow: hidden;
}

.progress--block {
  width: 100%;
}

.progress__bar {
  height: 100%;
  border-radius: var(--easy-radius-full);
  background: var(--easy-brand);
  transition: width var(--easy-transition-base);
}

.blocked-box {
  display: flex;
  flex-direction: column;
  gap: var(--easy-space-2);
  padding: var(--easy-space-3) var(--easy-space-4);
  border: 1px solid var(--easy-warning);
  border-radius: var(--easy-radius-md);
  background: var(--easy-warning-bg);
}

.blocked-box__title {
  font-size: var(--easy-text-sm);
  font-weight: 600;
  color: var(--easy-warning);
}

.blocked-box__list {
  display: flex;
  flex-direction: column;
  gap: 4px;
  font-size: var(--easy-text-xs);
  color: var(--easy-text-2);
}

.blocked-box__list li {
  display: flex;
  justify-content: space-between;
  gap: var(--easy-space-2);
}

.blocked-box__actions {
  display: flex;
  align-items: center;
  gap: var(--easy-space-2);
}

.detail-desc {
  font-size: var(--easy-text-sm);
  color: var(--easy-text-2);
  line-height: var(--easy-leading-relaxed);
  white-space: pre-wrap;
}

.detail-info {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.info-row {
  display: flex;
  gap: var(--easy-space-3);
  font-size: var(--easy-text-sm);
}

.info-row__label {
  width: 80px;
  flex: none;
  color: var(--easy-text-3);
}

.person-row {
  display: flex;
  gap: var(--easy-space-4);
  flex-wrap: wrap;
}

.person-row__item {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: var(--easy-text-sm);
}

.subtask-list,
.dependency-list {
  display: flex;
  flex-direction: column;
}

.subtask-row,
.dependency-row {
  display: flex;
  align-items: center;
  gap: var(--easy-space-3);
  padding: var(--easy-space-2) 0;
  border-bottom: 1px solid var(--easy-border);
  font-size: var(--easy-text-sm);
}

.subtask-row:last-child,
.dependency-row:last-child {
  border-bottom: none;
}

.subtask-row {
  cursor: pointer;
}

.subtask-row:hover .subtask-row__title {
  color: var(--easy-brand-text);
}

.subtask-row__title,
.dependency-row__title {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.subtask-row__assignee,
.dependency-row__assignee {
  font-size: var(--easy-text-xs);
  color: var(--easy-text-3);
  flex: none;
}

.subtask-row__progress {
  font-size: var(--easy-text-xs);
  color: var(--easy-text-3);
  font-variant-numeric: tabular-nums;
  flex: none;
}

.dependency-row__status {
  font-size: var(--easy-text-xs);
  color: var(--easy-warning);
  flex: none;
}

.dependency-row__status.is-finished {
  color: var(--easy-success);
}

.dependency-add {
  display: flex;
  align-items: center;
  gap: var(--easy-space-2);
}

.dependency-add__select {
  flex: 1;
}

.detail-footer {
  font-size: var(--easy-text-xs);
  color: var(--easy-text-3);
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