<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowLeft, Search } from '@element-plus/icons-vue'

import { ApiError } from '@/api/errors'
import type { PageResult } from '@/api/client'
import { projectApi } from '@/api/modules/projects'
import { taskApi } from '@/api/modules/tasks'
import { userApi } from '@/api/modules/users'
import type {
  ProjectDetail,
  ProjectMemberView,
  ProjectStatus,
  TaskCard,
  TaskPriority,
  TaskStatusType,
  TaskStatusView,
} from '@/api/types'
import EasyAvatar from '@/components/easy/EasyAvatar.vue'
import EasyButton from '@/components/easy/EasyButton.vue'
import EasyEmpty from '@/components/easy/EasyEmpty.vue'
import EasyInput from '@/components/easy/EasyInput.vue'
import EasySelect from '@/components/easy/EasySelect.vue'
import EasyStatus from '@/components/easy/EasyStatus.vue'
import TaskBlockedDialog from '@/components/task/TaskBlockedDialog.vue'
import TaskBoard from '@/components/task/TaskBoard.vue'
import TaskCreateDialog from '@/components/task/TaskCreateDialog.vue'
import TaskDetailDrawer from '@/components/task/TaskDetailDrawer.vue'
import { confirmAction } from '@/components/easy/easyConfirm'
import { useAuthStore } from '@/stores/auth'
import { useNotificationStore } from '@/stores/notification'
import { formatDate, formatDateTime, toDateInputValue, toIsoInstant } from '@/utils/format'
import { projectRoleLabel, projectRoleTone, projectStatusLabel, projectStatusTone } from '@/utils/project'
import { taskPriorityLabel, taskPriorityTone, taskStatusTypeTone } from '@/utils/task'

/**
 * 项目详情页（工作区）。
 *
 * Tab：概览 / 看板 / 任务 / 成员 / 设置
 *
 * URL 同步：
 * - Tab：看板使用 /projects/:id/board，其余 Tab 使用 ?tab=xxx；
 * - 任务详情：?task=<taskId>（刷新后仍打开对应任务）。
 */
const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const notification = useNotificationStore()

const projectId = computed(() => Number(route.params.id))
const detail = ref<ProjectDetail | null>(null)
const loading = ref(false)
const loadError = ref<string | null>(null)

const tabs = ['overview', 'board', 'tasks', 'members', 'settings'] as const
type TabName = (typeof tabs)[number]
const activeTab = ref<TabName>('overview')
const BOARD_ROUTE = 'project-board'

/** 前端镜像的生命周期流转表（仅用于展示按钮，后端会再次校验） */
const TRANSITIONS: Record<ProjectStatus, ProjectStatus[]> = {
  DRAFT: ['ACTIVE', 'ARCHIVED'],
  ACTIVE: ['PAUSED', 'COMPLETED', 'ARCHIVED'],
  PAUSED: ['ACTIVE', 'ARCHIVED'],
  COMPLETED: ['ACTIVE', 'ARCHIVED'],
  ARCHIVED: [],
}

const transitionOptions = computed(() =>
  detail.value ? TRANSITIONS[detail.value.status].filter((status) => status !== 'ARCHIVED') : [],
)

async function load(): Promise<void> {
  loading.value = true
  loadError.value = null
  try {
    detail.value = await projectApi.detail(projectId.value)
    syncInfoForm()
    void loadTaskOverview()
    // 命令面板「创建任务」直达：进入看板后自动打开新建任务弹窗
    if (route.query.create === '1') {
      createOpen.value = true
      void router.replace({ query: { ...route.query, create: undefined } })
    }
  } catch (error) {
    loadError.value = error instanceof ApiError ? error.message : '项目加载失败'
  } finally {
    loading.value = false
  }
}

function readTabFromRoute(): void {
  if (route.name === BOARD_ROUTE) {
    activeTab.value = 'board'
    return
  }
  const tab = route.query.tab
  activeTab.value =
    typeof tab === 'string' && (tabs as readonly string[]).includes(tab) ? (tab as TabName) : 'overview'
}

/** Tab → URL（看板使用 /projects/:id/board，其余使用 ?tab=xxx，保留 task 深链参数） */
function syncRoute(tab: TabName): void {
  if (!detail.value) return
  const id = String(projectId.value)
  if (tab === 'board') {
    if (route.name !== BOARD_ROUTE) {
      const query = { ...route.query }
      delete query.tab
      void router.replace({ name: BOARD_ROUTE, params: { id }, query })
    }
    return
  }
  if (route.name === BOARD_ROUTE || route.query.tab !== tab) {
    void router.replace({ name: 'project-detail', params: { id }, query: { ...route.query, tab } })
  }
}

onMounted(() => {
  readTabFromRoute()
  void load()
})

watch(
  () => [route.name, route.query.tab],
  () => readTabFromRoute(),
)

watch(activeTab, (tab) => syncRoute(tab))

// --- 概览：进度 -------------------------------------------------------------
const progressInput = ref<number>(0)
const savingProgress = ref(false)

async function saveProgress(): Promise<void> {
  if (!detail.value) return
  savingProgress.value = true
  try {
    detail.value = await projectApi.changeProgress(detail.value.id, progressInput.value)
    notification.success('项目进度已更新')
  } catch (error) {
    notification.error(error)
  } finally {
    savingProgress.value = false
  }
}

// --- 概览：任务统计 ------------------------------------------------------------
const taskStats = ref<{ total: number; byType: Record<TaskStatusType, number>; overdue: number; blocked: number } | null>(null)

async function loadTaskOverview(): Promise<void> {
  try {
    const page = await taskApi.list(projectId.value, { size: 100 })
    const byType: Record<TaskStatusType, number> = { TODO: 0, ACTIVE: 0, REVIEW: 0, DONE: 0, CLOSED: 0 }
    let overdue = 0
    let blocked = 0
    for (const task of page.items) {
      byType[task.status.systemType] += 1
      if (task.overdue) overdue += 1
      if (task.blocked) blocked += 1
    }
    taskStats.value = { total: page.total, byType, overdue, blocked }
  } catch {
    // 概览统计失败不影响主流程（看板与任务 Tab 会给出明确错误）
    taskStats.value = null
  }
}

const doneRatio = computed(() => {
  if (!taskStats.value || taskStats.value.total === 0) return 0
  const finished = taskStats.value.byType.DONE + taskStats.value.byType.CLOSED
  return Math.round((finished * 100) / taskStats.value.total)
})

// --- 设置：基本信息 ---------------------------------------------------------
const infoForm = reactive({ name: '', description: '', plannedStartAt: '', plannedEndAt: '' })
const savingInfo = ref(false)

function syncInfoForm(): void {
  if (!detail.value) return
  infoForm.name = detail.value.name
  infoForm.description = detail.value.description ?? ''
  infoForm.plannedStartAt = toDateInputValue(detail.value.plannedStartAt)
  infoForm.plannedEndAt = toDateInputValue(detail.value.plannedEndAt)
  progressInput.value = detail.value.progress
}

async function submitInfo(): Promise<void> {
  if (!detail.value) return
  if (!infoForm.name.trim()) {
    notification.warning('请输入项目名称')
    return
  }
  savingInfo.value = true
  try {
    detail.value = await projectApi.updateInfo(detail.value.id, {
      name: infoForm.name.trim(),
      description: infoForm.description.trim() || undefined,
      plannedStartAt: toIsoInstant(infoForm.plannedStartAt),
      plannedEndAt: toIsoInstant(infoForm.plannedEndAt),
    })
    notification.success('项目信息已保存')
  } catch (error) {
    notification.error(error)
  } finally {
    savingInfo.value = false
  }
}

async function changeStatus(target: ProjectStatus): Promise<void> {
  if (!detail.value) return
  try {
    detail.value = await projectApi.changeStatus(detail.value.id, target)
    notification.success(`项目状态已切换为「${projectStatusLabel(target)}」`)
  } catch (error) {
    notification.error(error)
  }
}

async function archiveProject(): Promise<void> {
  if (!detail.value) return
  const confirmed = await confirmAction({
    title: '归档项目',
    message: `归档后「${detail.value.name}」将变为只读：项目信息、成员、任务与状态都不能再修改；已归档项目仍会保留在列表中（带「已归档」标记），历史记录与审计保持完整。确定归档吗？`,
    confirmText: '归档',
    danger: true,
  })
  if (!confirmed) return
  try {
    detail.value = await projectApi.archive(detail.value.id)
    notification.success('项目已归档')
  } catch (error) {
    notification.error(error)
  }
}

// --- 成员管理 ---------------------------------------------------------------
const memberOptions = ref<Array<{ label: string; value: number }>>([])
const candidateId = ref<number | null>(null)

const candidateOptions = computed(() => {
  const existing = new Set(detail.value?.members.map((member) => member.userId) ?? [])
  return memberOptions.value.filter((option) => !existing.has(option.value))
})

const manageableMembers = computed(() =>
  (detail.value?.members ?? []).filter((member) => member.role !== 'OWNER'),
)

async function ensureMemberOptions(): Promise<void> {
  if (memberOptions.value.length > 0) return
  try {
    const directory = await userApi.directory({ size: 100 })
    memberOptions.value = directory.items
      .filter((member) => member.status === 'ACTIVE')
      .map((member) => ({
        label: `${member.displayName}${member.primaryOrgUnit ? `（${member.primaryOrgUnit.name}）` : ''}`,
        value: member.id,
      }))
  } catch (error) {
    notification.error(error)
  }
}

async function addMember(): Promise<void> {
  if (!detail.value || candidateId.value === null) return
  try {
    await projectApi.addMember(detail.value.id, candidateId.value)
    candidateId.value = null
    detail.value = await projectApi.detail(projectId.value)
    notification.success('成员已加入项目')
  } catch (error) {
    notification.error(error)
  }
}

async function removeMember(member: ProjectMemberView): Promise<void> {
  if (!detail.value) return
  const confirmed = await confirmAction({
    title: '移出项目',
    message: `确定将 ${member.displayName} 从「${detail.value.name}」移出吗？`,
    confirmText: '移出',
    danger: true,
  })
  if (!confirmed) return
  try {
    await projectApi.removeMember(detail.value.id, member.userId)
    detail.value = await projectApi.detail(projectId.value)
    notification.success('成员已移出项目')
  } catch (error) {
    notification.error(error)
  }
}

async function setDeputy(member: ProjectMemberView | null): Promise<void> {
  if (!detail.value) return
  try {
    detail.value = await projectApi.setDeputy(detail.value.id, member ? member.userId : null)
    notification.success(member ? `${member.displayName} 已设为副负责人` : '已取消副负责人')
  } catch (error) {
    notification.error(error)
  }
}

async function transferOwner(member: ProjectMemberView): Promise<void> {
  if (!detail.value) return
  const confirmed = await confirmAction({
    title: '转让项目负责人',
    message: `转让后 ${member.displayName} 将成为项目负责人（OWNER），你将变为普通成员，且无法再管理项目配置。确定继续吗？`,
    confirmText: '转让',
    danger: true,
  })
  if (!confirmed) return
  try {
    detail.value = await projectApi.transferOwner(detail.value.id, member.userId)
    await ensureMemberOptions()
    notification.success(`项目负责人已转让给 ${member.displayName}`)
  } catch (error) {
    notification.error(error)
  }
}

// --- 看板 -------------------------------------------------------------------
const boardRef = ref<InstanceType<typeof TaskBoard> | null>(null)
const drawerRef = ref<InstanceType<typeof TaskDetailDrawer> | null>(null)
const createOpen = ref(false)

const isArchived = computed(() => detail.value?.status === 'ARCHIVED')
const myUserId = computed(() => auth.user?.id)
const canCreateTask = computed(() => detail.value?.myRole != null && !isArchived.value)
const canManageStatuses = computed(() => {
  const role = detail.value?.myRole
  return (role === 'OWNER' || role === 'DEPUTY_OWNER') && !isArchived.value
})

/** 任务详情深链：?task=<id>（刷新后仍然打开） */
const openTaskId = computed(() => {
  const raw = route.query.task
  const value = Array.isArray(raw) ? raw[0] : raw
  const parsed = value ? Number(value) : null
  return parsed && !Number.isNaN(parsed) ? parsed : null
})

function openTask(taskId: number): void {
  if (openTaskId.value === taskId) return
  void router.push({ query: { ...route.query, task: String(taskId) } })
}

function closeTask(): void {
  const query = { ...route.query }
  delete query.task
  void router.replace({ query })
}

function onTaskChanged(): void {
  boardRef.value?.load()
  void loadTaskOverview()
  if (taskPage.value) void loadTaskList()
}

// 依赖阻塞：看板拖拽或侧栏切换被拒后，统一弹出「忽略依赖并开始」
const blockedDialog = reactive({ open: false, taskId: null as number | null, statusId: null as number | null, statusName: '' })

function handleBlockedTransition(payload: { taskId: number; statusId: number; statusName: string }): void {
  blockedDialog.taskId = payload.taskId
  blockedDialog.statusId = payload.statusId
  blockedDialog.statusName = payload.statusName
  blockedDialog.open = true
}

function onOverrideDone(): void {
  boardRef.value?.load()
  drawerRef.value?.reload()
  void loadTaskOverview()
  if (taskPage.value) void loadTaskList()
}

function onTaskCreated(): void {
  boardRef.value?.load()
  void loadTaskOverview()
  if (activeTab.value === 'tasks') void loadTaskList()
}

// --- 任务列表 ----------------------------------------------------------------
const taskStatuses = ref<TaskStatusView[]>([])
const taskPage = ref<PageResult<TaskCard> | null>(null)
const taskListLoading = ref(false)
const taskListError = ref<string | null>(null)
const taskFilters = reactive({
  keyword: '',
  statusId: null as number | null,
  priority: null as TaskPriority | null,
  page: 1,
  size: 15,
})

const taskStatusOptions = computed(() => taskStatuses.value.map((status) => ({ label: status.name, value: status.id })))
const taskPriorityOptions = [
  { label: '低', value: 'LOW' },
  { label: '中', value: 'MEDIUM' },
  { label: '高', value: 'HIGH' },
  { label: '紧急', value: 'URGENT' },
]

async function loadTaskList(): Promise<void> {
  taskListLoading.value = true
  taskListError.value = null
  try {
    if (taskStatuses.value.length === 0) {
      taskStatuses.value = await taskApi.statuses(projectId.value)
    }
    taskPage.value = await taskApi.list(projectId.value, {
      keyword: taskFilters.keyword.trim() || undefined,
      statusId: taskFilters.statusId,
      priority: taskFilters.priority,
      page: taskFilters.page,
      size: taskFilters.size,
    })
  } catch (error) {
    taskListError.value = error instanceof ApiError ? error.message : '任务加载失败'
  } finally {
    taskListLoading.value = false
  }
}

function searchTasks(): void {
  taskFilters.page = 1
  void loadTaskList()
}

function changeTaskPage(next: number): void {
  taskFilters.page = next
  void loadTaskList()
}

watch(activeTab, (tab) => {
  if (tab === 'tasks') void loadTaskList()
})
</script>

<template>
  <div class="easy-page">
    <div v-if="loadError" class="easy-card error-bar">
      <span>{{ loadError }}</span>
      <div class="error-bar__actions">
        <EasyButton size="sm" @click="router.push({ name: 'projects' })">返回项目列表</EasyButton>
        <EasyButton size="sm" variant="primary" @click="load">重试</EasyButton>
      </div>
    </div>

    <div v-else-if="loading && !detail" class="easy-card skeleton-block">正在加载项目…</div>

    <template v-else-if="detail">
      <!-- 顶部 -->
      <header class="project-header">
        <div class="project-header__left">
          <button type="button" class="back-link" @click="router.push({ name: 'projects' })">
            <el-icon><ArrowLeft /></el-icon>
            项目列表
          </button>
          <div class="project-header__title">
            <h1 class="project-header__name">{{ detail.name }}</h1>
            <EasyStatus :label="projectStatusLabel(detail.status)" :tone="projectStatusTone(detail.status)" />
            <EasyStatus
              v-if="detail.myRole"
              :label="projectRoleLabel(detail.myRole)"
              :tone="projectRoleTone(detail.myRole)"
            />
          </div>
          <p class="project-header__desc">{{ detail.description || '暂无项目简介' }}</p>
        </div>

        <div class="project-header__meta">
          <div class="meta-item">
            <span class="meta-item__label">总体进度</span>
            <div class="meta-item__progress">
              <div class="progress">
                <div class="progress__bar" :style="{ width: `${detail.progress}%` }" />
              </div>
              <span class="progress__value">{{ detail.progress }}%</span>
            </div>
          </div>
          <div class="meta-item">
            <span class="meta-item__label">负责人</span>
            <span v-if="detail.owner" class="meta-item__person">
              <EasyAvatar :name="detail.owner.displayName" :src="detail.owner.avatarUrl ?? null" size="sm" />
              {{ detail.owner.displayName }}
            </span>
            <span v-else class="easy-muted">未设置</span>
          </div>
          <div class="meta-item">
            <span class="meta-item__label">副负责人</span>
            <span v-if="detail.deputyOwner" class="meta-item__person">
              <EasyAvatar
                :name="detail.deputyOwner.displayName"
                :src="detail.deputyOwner.avatarUrl ?? null"
                size="sm"
              />
              {{ detail.deputyOwner.displayName }}
            </span>
            <span v-else class="easy-muted">未设置</span>
          </div>
          <div class="meta-item">
            <span class="meta-item__label">项目周期</span>
            <span class="meta-item__value">
              {{ formatDate(detail.plannedStartAt) }} → {{ formatDate(detail.plannedEndAt) }}
            </span>
          </div>
        </div>
      </header>

      <div v-if="isArchived" class="archived-banner">
        该项目已归档，所有内容为只读状态。如需继续协作，请由管理员在数据库中恢复（v0.1.0 不提供取消归档入口）。
      </div>

      <el-tabs v-model="activeTab" class="project-tabs">
        <!-- 概览 -->
        <el-tab-pane label="概览" name="overview">
          <div class="tab-body">
            <div class="overview-grid">
              <div class="easy-card">
                <div class="easy-card__header"><span class="easy-card__title">项目信息</span></div>
                <div class="easy-card__body info-list">
                  <div class="info-row">
                    <span class="info-row__label">项目简介</span>
                    <span>{{ detail.description || '暂无' }}</span>
                  </div>
                  <div class="info-row">
                    <span class="info-row__label">创建时间</span>
                    <span>{{ formatDateTime(detail.createdAt) }}</span>
                  </div>
                  <div class="info-row">
                    <span class="info-row__label">最近更新</span>
                    <span>{{ formatDateTime(detail.updatedAt) }}</span>
                  </div>
                  <div class="info-row">
                    <span class="info-row__label">成员数量</span>
                    <span>{{ detail.members.length }}</span>
                  </div>
                </div>
              </div>

              <div class="easy-card">
                <div class="easy-card__header">
                  <span class="easy-card__title">总体进度</span>
                  <span v-if="detail.permissions.canEditInfo" class="easy-text-xs easy-muted">
                    负责人 / 副负责人可维护
                  </span>
                </div>
                <div class="easy-card__body progress-editor">
                  <div class="progress progress--lg">
                    <div class="progress__bar" :style="{ width: `${detail.progress}%` }" />
                  </div>
                  <div v-if="detail.permissions.canEditInfo" class="progress-editor__controls">
                    <el-slider v-model="progressInput" :min="0" :max="100" :step="5" class="progress-editor__slider" />
                    <EasyButton size="sm" :loading="savingProgress" @click="saveProgress">保存进度</EasyButton>
                  </div>
                  <p v-else class="easy-text-xs easy-muted">
                    进度由项目负责人 / 副负责人维护。
                  </p>
                </div>
              </div>
            </div>

            <div class="easy-card">
              <div class="easy-card__header">
                <span class="easy-card__title">任务概览</span>
                <EasyButton size="sm" @click="activeTab = 'board'">打开看板</EasyButton>
              </div>
              <div v-if="taskStats && taskStats.total > 0" class="easy-card__body task-stats">
                <div class="task-stats__kpis">
                  <div class="stat-item">
                    <span class="stat-item__label">任务总数</span>
                    <span class="stat-item__value">{{ taskStats.total }}</span>
                  </div>
                  <div class="stat-item">
                    <span class="stat-item__label">已完成</span>
                    <span class="stat-item__value stat-item__value--success">{{ taskStats.byType.DONE }}</span>
                  </div>
                  <div class="stat-item">
                    <span class="stat-item__label">进行中</span>
                    <span class="stat-item__value stat-item__value--brand">{{ taskStats.byType.ACTIVE }}</span>
                  </div>
                  <div class="stat-item">
                    <span class="stat-item__label">阻塞</span>
                    <span class="stat-item__value stat-item__value--warning">{{ taskStats.blocked }}</span>
                  </div>
                  <div class="stat-item">
                    <span class="stat-item__label">已逾期</span>
                    <span class="stat-item__value stat-item__value--danger">{{ taskStats.overdue }}</span>
                  </div>
                </div>
                <div class="task-stats__completion">
                  <div class="progress progress--lg">
                    <div class="progress__bar" :style="{ width: `${doneRatio}%` }" />
                  </div>
                  <span class="progress__value">{{ doneRatio }}%（已完成 / 已关闭占比，按系统状态类型统计）</span>
                </div>
              </div>
              <EasyEmpty
                v-else
                compact
                title="还没有任务"
                description="在看板中创建第一个任务：支持主/副负责人、协作成员、子任务、依赖与进度自动计算。"
              >
                <template #action>
                  <EasyButton size="sm" variant="primary" @click="activeTab = 'board'">去创建任务</EasyButton>
                </template>
              </EasyEmpty>
            </div>

            <div class="easy-card">
              <div class="easy-card__header">
                <span class="easy-card__title">项目成员</span>
                <EasyButton size="sm" @click="activeTab = 'members'">管理成员</EasyButton>
              </div>
              <div class="easy-card__body member-chips">
                <span v-for="member in detail.members" :key="member.userId" class="member-chip">
                  <EasyAvatar :name="member.displayName" :src="member.avatarUrl ?? null" size="sm" />
                  {{ member.displayName }}
                  <span class="member-chip__role">{{ projectRoleLabel(member.role) }}</span>
                </span>
              </div>
            </div>
          </div>
        </el-tab-pane>

        <!-- 看板 -->
        <el-tab-pane label="看板" name="board">
          <TaskBoard
            ref="boardRef"
            :project-id="projectId"
            :can-create-task="canCreateTask"
            :can-manage-statuses="canManageStatuses"
            @open-task="openTask"
            @create-task="createOpen = true"
            @blocked-transition="handleBlockedTransition"
          />
        </el-tab-pane>

        <!-- 任务列表 -->
        <el-tab-pane label="任务" name="tasks">
          <div class="easy-card task-panel">
            <div class="task-panel__filters">
              <EasyInput v-model="taskFilters.keyword" placeholder="搜索任务标题 / 描述" @keyup.enter="searchTasks">
                <template #prefix><el-icon><Search /></el-icon></template>
              </EasyInput>
              <EasySelect
                v-model="taskFilters.statusId"
                :options="taskStatusOptions"
                placeholder="全部状态"
                class="task-panel__filter"
              />
              <EasySelect
                v-model="taskFilters.priority"
                :options="taskPriorityOptions"
                placeholder="全部优先级"
                class="task-panel__filter"
              />
              <EasyButton size="sm" @click="searchTasks">筛选</EasyButton>
              <EasyButton v-if="canCreateTask" variant="primary" size="sm" @click="createOpen = true">
                新建任务
              </EasyButton>
            </div>

            <div v-if="taskListError" class="task-panel__error">
              <span>{{ taskListError }}</span>
              <EasyButton size="sm" @click="loadTaskList">重试</EasyButton>
            </div>

            <div v-else-if="taskListLoading && !taskPage" class="task-panel__hint">正在加载任务…</div>

            <template v-else-if="taskPage && taskPage.items.length > 0">
              <ul class="task-table">
                <li
                  v-for="task in taskPage.items"
                  :key="task.id"
                  class="task-table__row"
                  @click="openTask(task.id)"
                >
                  <div class="task-table__main">
                    <span class="task-table__title">{{ task.title }}</span>
                    <span class="task-table__sub">
                      主负责人：{{ task.primaryAssignee.displayName }}
                      <span v-if="task.deputyAssignee">· 副负责人：{{ task.deputyAssignee.displayName }}</span>
                    </span>
                  </div>
                  <span v-if="task.blocked" class="task-table__blocked">阻塞 {{ task.blockerCount }}</span>
                  <div class="task-table__progress">
                    <div class="progress">
                      <div class="progress__bar" :style="{ width: `${task.progress}%` }" />
                    </div>
                    <span class="progress__value">{{ task.progress }}%</span>
                  </div>
                  <EasyStatus :label="task.status.name" :tone="taskStatusTypeTone(task.status.systemType)" />
                  <EasyStatus :label="taskPriorityLabel(task.priority)" :tone="taskPriorityTone(task.priority)" />
                  <span class="task-table__due" :class="{ 'task-table__due--overdue': task.overdue }">
                    {{ formatDate(task.plannedEndAt) }}
                  </span>
                </li>
              </ul>

              <div v-if="taskPage.total > taskFilters.size" class="pagination">
                <el-pagination
                  layout="prev, pager, next"
                  :total="taskPage.total"
                  :page-size="taskFilters.size"
                  :current-page="taskFilters.page"
                  background
                  @current-change="changeTaskPage"
                />
              </div>
            </template>

            <EasyEmpty
              v-else
              compact
              title="没有符合条件的任务"
              description="尝试调整筛选条件，或在看板中创建新任务。"
            />
          </div>
        </el-tab-pane>

        <!-- 成员 -->
        <el-tab-pane label="成员" name="members">
          <div class="easy-card">
            <div class="easy-card__header">
              <span class="easy-card__title">项目成员（{{ detail.members.length }}）</span>
              <div v-if="detail.permissions.canManageMembers" class="add-member">
                <EasySelect
                  v-model="candidateId"
                  :options="candidateOptions"
                  placeholder="选择成员加入"
                  class="add-member__select"
                  @click="ensureMemberOptions"
                />
                <EasyButton
                  variant="primary"
                  size="sm"
                  :disabled="candidateId === null"
                  @click="addMember"
                >
                  加入项目
                </EasyButton>
              </div>
            </div>

            <ul class="member-table">
              <li v-for="member in detail.members" :key="member.userId" class="member-row">
                <EasyAvatar :name="member.displayName" :src="member.avatarUrl ?? null" />
                <div class="member-row__identity">
                  <span class="member-row__name">
                    {{ member.displayName }}
                    <span v-if="member.userId === myUserId" class="member-row__self">（我）</span>
                  </span>
                  <span class="member-row__job">{{ member.jobTitle || member.username }}</span>
                </div>
                <EasyStatus :label="projectRoleLabel(member.role)" :tone="projectRoleTone(member.role)" />
                <span class="member-row__joined">{{ formatDate(member.joinedAt) }} 加入</span>
                <div v-if="detail.permissions.canManageMembers" class="member-row__actions">
                  <EasyButton
                    v-if="detail.permissions.canSetDeputy && member.role === 'MEMBER'"
                    size="sm"
                    @click="setDeputy(member)"
                  >
                    设为副负责人
                  </EasyButton>
                  <EasyButton
                    v-if="detail.permissions.canSetDeputy && member.role === 'DEPUTY_OWNER'"
                    size="sm"
                    @click="setDeputy(null)"
                  >
                    取消副负责人
                  </EasyButton>
                  <EasyButton
                    v-if="detail.permissions.canTransferOwner && member.role !== 'OWNER'"
                    size="sm"
                    @click="transferOwner(member)"
                  >
                    转让负责人
                  </EasyButton>
                  <EasyButton
                    v-if="member.role !== 'OWNER' && member.userId !== myUserId"
                    size="sm"
                    @click="removeMember(member)"
                  >
                    移出
                  </EasyButton>
                </div>
              </li>
            </ul>

            <p v-if="!detail.permissions.canManageMembers" class="member-hint">
              只有项目负责人 / 副负责人可以管理项目成员。
            </p>
            <p v-else-if="manageableMembers.length === 0" class="member-hint">
              当前项目中只有负责人本人，可在上方选择框添加成员。
            </p>
          </div>
        </el-tab-pane>

        <!-- 设置 -->
        <el-tab-pane label="设置" name="settings">
          <div class="tab-body">
            <div class="easy-card">
              <div class="easy-card__header"><span class="easy-card__title">基本信息</span></div>
              <div class="easy-card__body">
                <div v-if="detail.permissions.canEditInfo" class="form-stack">
                  <EasyInput v-model="infoForm.name" label="项目名称" required />
                  <EasyInput v-model="infoForm.description" label="项目简介" type="textarea" :rows="3" />
                  <div class="form-stack__row">
                    <EasyInput v-model="infoForm.plannedStartAt" label="计划开始" type="date" />
                    <EasyInput v-model="infoForm.plannedEndAt" label="计划结束" type="date" />
                  </div>
                  <div class="form-stack__actions">
                    <EasyButton variant="primary" :loading="savingInfo" @click="submitInfo">
                      保存修改
                    </EasyButton>
                  </div>
                </div>
                <p v-else class="easy-text-sm easy-muted">
                  你没有修改项目信息的权限（仅项目负责人 / 副负责人可修改）。
                </p>
              </div>
            </div>

            <div class="easy-card">
              <div class="easy-card__header"><span class="easy-card__title">生命周期</span></div>
              <div class="easy-card__body lifecycle">
                <div class="lifecycle__current">
                  当前状态：
                  <EasyStatus :label="projectStatusLabel(detail.status)" :tone="projectStatusTone(detail.status)" />
                </div>
                <div v-if="detail.permissions.canChangeStatus && transitionOptions.length > 0" class="lifecycle__actions">
                  <EasyButton
                    v-for="target in transitionOptions"
                    :key="target"
                    size="sm"
                    @click="changeStatus(target)"
                  >
                    切换为「{{ projectStatusLabel(target) }}」
                  </EasyButton>
                </div>
                <p v-else class="easy-text-xs easy-muted">
                  {{ isArchived ? '已归档项目为只读终态。' : '你没有变更项目状态的权限。' }}
                </p>
              </div>
            </div>

            <div class="easy-card danger-card">
              <div class="easy-card__header"><span class="easy-card__title">归档项目</span></div>
              <div class="easy-card__body">
                <p class="easy-text-sm easy-muted">
                  项目结束请使用归档（不提供删除）。归档后项目只读，历史数据与审计保持完整。
                </p>
                <div class="form-stack__actions">
                  <EasyButton
                    variant="danger"
                    :disabled="!detail.permissions.canArchive"
                    @click="archiveProject"
                  >
                    归档项目
                  </EasyButton>
                </div>
                <p v-if="!detail.permissions.canArchive && !isArchived" class="easy-text-xs easy-muted">
                  只有项目负责人可以归档项目。
                </p>
              </div>
            </div>
          </div>
        </el-tab-pane>
      </el-tabs>

      <!-- 新建任务 -->
      <TaskCreateDialog
        v-model="createOpen"
        :project-id="projectId"
        :members="detail.members"
        @created="onTaskCreated"
      />

      <!-- 任务详情（右侧 Drawer，URL 同步 ?task=<id>） -->
      <TaskDetailDrawer
        v-if="openTaskId"
        ref="drawerRef"
        :key="openTaskId"
        :task-id="openTaskId"
        :project-id="projectId"
        :members="detail.members"
        :archived="isArchived"
        @close="closeTask"
        @changed="onTaskChanged"
        @open-task="openTask"
        @blocked-transition="handleBlockedTransition"
      />

      <!-- 忽略依赖并开始 -->
      <TaskBlockedDialog
        v-model="blockedDialog.open"
        :task-id="blockedDialog.taskId"
        :status-id="blockedDialog.statusId"
        :status-name="blockedDialog.statusName"
        @done="onOverrideDone"
      />
    </template>
  </div>
</template>

<style scoped>
.error-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--easy-space-4);
  padding: var(--easy-space-4) var(--easy-space-5);
  color: var(--easy-danger);
  font-size: var(--easy-text-sm);
}

.error-bar__actions {
  display: flex;
  gap: var(--easy-space-2);
}

.skeleton-block {
  padding: var(--easy-space-6);
  color: var(--easy-text-3);
  font-size: var(--easy-text-sm);
}

.project-header {
  display: flex;
  justify-content: space-between;
  gap: var(--easy-space-6);
  flex-wrap: wrap;
}

.project-header__left {
  min-width: 0;
  flex: 1;
}

.back-link {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  margin-bottom: var(--easy-space-2);
  border: none;
  background: transparent;
  color: var(--easy-text-3);
  font-size: var(--easy-text-sm);
  cursor: pointer;
  padding: 0;
}

.back-link:hover {
  color: var(--easy-text-1);
}

.project-header__title {
  display: flex;
  align-items: center;
  gap: var(--easy-space-3);
  flex-wrap: wrap;
}

.project-header__name {
  font-size: var(--easy-text-2xl);
  font-weight: 600;
  letter-spacing: -0.02em;
}

.project-header__desc {
  margin-top: var(--easy-space-2);
  color: var(--easy-text-2);
  font-size: var(--easy-text-sm);
  line-height: var(--easy-leading-relaxed);
  max-width: 720px;
}

.project-header__meta {
  display: grid;
  grid-template-columns: repeat(2, minmax(140px, auto));
  gap: var(--easy-space-4) var(--easy-space-8);
  align-content: start;
}

.meta-item {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.meta-item__label {
  font-size: var(--easy-text-xs);
  color: var(--easy-text-3);
}

.meta-item__person {
  display: inline-flex;
  align-items: center;
  gap: var(--easy-space-2);
  font-size: var(--easy-text-sm);
}

.meta-item__value {
  font-size: var(--easy-text-sm);
  font-variant-numeric: tabular-nums;
}

.meta-item__progress {
  display: flex;
  align-items: center;
  gap: var(--easy-space-2);
}

.progress {
  width: 120px;
  height: 6px;
  border-radius: var(--easy-radius-full);
  background: var(--easy-surface-sunken);
  overflow: hidden;
}

.progress--lg {
  width: 100%;
  height: 8px;
}

.progress__bar {
  height: 100%;
  border-radius: var(--easy-radius-full);
  background: var(--easy-brand);
  transition: width var(--easy-transition-base);
}

.progress__value {
  font-size: var(--easy-text-xs);
  color: var(--easy-text-2);
  font-variant-numeric: tabular-nums;
}

.archived-banner {
  padding: var(--easy-space-3) var(--easy-space-4);
  border: 1px solid var(--easy-warning);
  border-radius: var(--easy-radius-md);
  background: var(--easy-warning-bg);
  color: var(--easy-warning);
  font-size: var(--easy-text-sm);
}

.project-tabs :deep(.el-tabs__header) {
  margin-bottom: var(--easy-space-4);
}

.tab-body {
  display: flex;
  flex-direction: column;
  gap: var(--easy-space-4);
}

.overview-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--easy-space-4);
}

@media (max-width: 1100px) {
  .overview-grid {
    grid-template-columns: minmax(0, 1fr);
  }
}

.info-list {
  display: flex;
  flex-direction: column;
  gap: var(--easy-space-3);
}

.info-row {
  display: flex;
  gap: var(--easy-space-4);
  font-size: var(--easy-text-sm);
}

.info-row__label {
  width: 88px;
  flex: none;
  color: var(--easy-text-3);
}

.progress-editor {
  display: flex;
  flex-direction: column;
  gap: var(--easy-space-4);
}

.progress-editor__controls {
  display: flex;
  align-items: center;
  gap: var(--easy-space-4);
}

.progress-editor__slider {
  flex: 1;
  max-width: 320px;
}

.member-chips {
  display: flex;
  flex-wrap: wrap;
  gap: var(--easy-space-3);
}

.member-chip {
  display: inline-flex;
  align-items: center;
  gap: var(--easy-space-2);
  padding: 4px 10px 4px 4px;
  border: 1px solid var(--easy-border);
  border-radius: var(--easy-radius-full);
  background: var(--easy-surface);
  font-size: var(--easy-text-sm);
}

.member-chip__role {
  font-size: var(--easy-text-xs);
  color: var(--easy-text-3);
}

.add-member {
  display: flex;
  align-items: center;
  gap: var(--easy-space-2);
}

.add-member__select {
  width: 220px;
}

.member-table {
  display: flex;
  flex-direction: column;
}

.member-row {
  display: flex;
  align-items: center;
  gap: var(--easy-space-3);
  padding: var(--easy-space-3) var(--easy-space-5);
  border-bottom: 1px solid var(--easy-border);
  flex-wrap: wrap;
}

.member-row:last-child {
  border-bottom: none;
}

.member-row__identity {
  display: flex;
  flex-direction: column;
  min-width: 0;
  flex: 1;
}

.member-row__name {
  font-size: var(--easy-text-sm);
  font-weight: 500;
}

.member-row__self {
  color: var(--easy-text-3);
  font-weight: 400;
}

.member-row__job {
  font-size: var(--easy-text-xs);
  color: var(--easy-text-3);
}

.member-row__joined {
  font-size: var(--easy-text-xs);
  color: var(--easy-text-3);
  flex: none;
}

.member-row__actions {
  display: flex;
  gap: var(--easy-space-2);
  flex: none;
  flex-wrap: wrap;
}

.member-hint {
  padding: var(--easy-space-4) var(--easy-space-5);
  color: var(--easy-text-3);
  font-size: var(--easy-text-xs);
}

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

.lifecycle {
  display: flex;
  flex-direction: column;
  gap: var(--easy-space-3);
}

.lifecycle__current {
  display: flex;
  align-items: center;
  gap: var(--easy-space-2);
  font-size: var(--easy-text-sm);
  color: var(--easy-text-2);
}

.lifecycle__actions {
  display: flex;
  gap: var(--easy-space-2);
  flex-wrap: wrap;
}

.danger-card {
  border-color: var(--easy-danger);
}

.danger-card .easy-card__body {
  display: flex;
  flex-direction: column;
  gap: var(--easy-space-3);
}

/* --- 任务概览 ---------------------------------------------------------------- */
.task-stats {
  display: flex;
  flex-direction: column;
  gap: var(--easy-space-4);
}

.task-stats__kpis {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: var(--easy-space-3);
}

@media (max-width: 900px) {
  .task-stats__kpis {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
}

.stat-item {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.stat-item__label {
  font-size: var(--easy-text-xs);
  color: var(--easy-text-3);
}

.stat-item__value {
  font-size: var(--easy-text-xl);
  font-weight: 600;
  font-variant-numeric: tabular-nums;
}

.stat-item__value--success {
  color: var(--easy-success);
}

.stat-item__value--brand {
  color: var(--easy-brand-text);
}

.stat-item__value--warning {
  color: var(--easy-warning);
}

.stat-item__value--danger {
  color: var(--easy-danger);
}

.task-stats__completion {
  display: flex;
  align-items: center;
  gap: var(--easy-space-3);
}

/* --- 任务列表 ---------------------------------------------------------------- */
.task-panel {
  display: flex;
  flex-direction: column;
  gap: var(--easy-space-4);
  padding: var(--easy-space-4) var(--easy-space-5);
}

.task-panel__filters {
  display: flex;
  align-items: center;
  gap: var(--easy-space-3);
  flex-wrap: wrap;
}

.task-panel__filters > :first-child {
  width: 260px;
}

.task-panel__filter {
  width: 160px;
}

.task-panel__error,
.task-panel__hint {
  color: var(--easy-danger);
  font-size: var(--easy-text-sm);
  padding: var(--easy-space-2) 0;
}

.task-panel__hint {
  color: var(--easy-text-3);
}

.task-table {
  display: flex;
  flex-direction: column;
}

.task-table__row {
  display: flex;
  align-items: center;
  gap: var(--easy-space-4);
  padding: var(--easy-space-3) 0;
  border-bottom: 1px solid var(--easy-border);
  cursor: pointer;
}

.task-table__row:last-child {
  border-bottom: none;
}

.task-table__row:hover .task-table__title {
  color: var(--easy-brand-text);
}

.task-table__main {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.task-table__title {
  font-size: var(--easy-text-sm);
  font-weight: 500;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.task-table__sub {
  font-size: var(--easy-text-xs);
  color: var(--easy-text-3);
}

.task-table__blocked {
  font-size: var(--easy-text-xs);
  color: var(--easy-warning);
  flex: none;
}

.task-table__progress {
  display: flex;
  align-items: center;
  gap: var(--easy-space-2);
  width: 120px;
  flex: none;
}

.task-table__progress .progress {
  flex: 1;
  width: auto;
  height: 4px;
}

.task-table__due {
  font-size: var(--easy-text-xs);
  color: var(--easy-text-3);
  width: 84px;
  text-align: right;
  flex: none;
  font-variant-numeric: tabular-nums;
}

.task-table__due--overdue {
  color: var(--easy-danger);
  font-weight: 600;
}

.pagination {
  display: flex;
  justify-content: center;
}
</style>