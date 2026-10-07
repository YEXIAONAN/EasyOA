<script setup lang="ts">
import { computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { DataAnalysis, Folder, Stamp, Tickets } from '@element-plus/icons-vue'
import type { Component } from 'vue'

import { authApi } from '@/api/modules/auth'
import { workspaceApi } from '@/api/modules/workspace'
import type { TaskCard } from '@/api/types'
import EasyButton from '@/components/easy/EasyButton.vue'
import EasyEmpty from '@/components/easy/EasyEmpty.vue'
import EasyStatus from '@/components/easy/EasyStatus.vue'
import { useAsync } from '@/composables/useAsync'
import { useAuthStore } from '@/stores/auth'
import { formatDate, formatDateTime, formatRelative, greeting } from '@/utils/format'
import { projectRoleLabel, projectStatusLabel, projectStatusTone } from '@/utils/project'
import { taskPriorityLabel, taskPriorityTone, taskStatusTypeTone } from '@/utils/task'

/**
 * 工作台首页：回答三个问题 ——
 *   1. 我现在需要做什么（我的任务 / KPI）
 *   2. 什么事情正在等我（待我审批）
 *   3. 我的项目发生了什么（项目动态 / 项目进度）
 *
 * 任务相关区域（Phase 4 起）与项目区域（Phase 3 起）为真实数据；
 * 审批（Phase 6）与 Activity Feed（Phase 7）以明确标注交付阶段的空状态呈现，不展示假数据。
 */
const router = useRouter()
const auth = useAuthStore()

const summary = useAsync(() => workspaceApi.summary(), { immediate: false })
const sessions = useAsync(() => authApi.sessions(), { immediate: false })

onMounted(() => {
  void summary.run()
  void sessions.run()
})

const hello = computed(() => `${greeting()}，${auth.displayName || summary.data.value?.me.displayName || ''}`)
const today = computed(() =>
  new Intl.DateTimeFormat('zh-CN', { dateStyle: 'full' }).format(new Date()),
)

const openTaskCount = computed(() => summary.data.value?.kpis.myOpenTasks ?? 0)
const taskBrief = computed(() =>
  openTaskCount.value > 0 ? `今天有 ${openTaskCount.value} 项任务需要处理` : '今天没有待处理的任务',
)

const activeSessionCount = computed(() => sessions.data.value?.length ?? 0)

interface KpiCard {
  key: string
  label: string
  value: number
  hint: string
  icon: Component
  routeName: string
  query?: Record<string, string>
}

const kpis = computed<KpiCard[]>(() => [
  {
    key: 'tasks',
    label: '我的任务',
    value: openTaskCount.value,
    hint: '我是负责人 / 协作成员的未完成任务',
    icon: Tickets,
    routeName: 'my-tasks',
  },
  {
    key: 'approvals',
    label: '待我审批',
    value: summary.data.value?.kpis.pendingApprovals ?? 0,
    hint: '必须进入详情处理，不支持一键批准',
    icon: Stamp,
    routeName: 'approvals',
  },
  {
    key: 'projects',
    label: '进行中项目',
    value: summary.data.value?.kpis.activeProjects ?? 0,
    hint: '点击进入项目列表',
    icon: Folder,
    routeName: 'projects',
  },
  {
    key: 'due',
    label: '即将到期',
    value: summary.data.value?.kpis.dueSoonTasks ?? 0,
    hint: '未来 7 天到期（含已逾期）',
    icon: DataAnalysis,
    routeName: 'my-tasks',
    query: { filter: 'DUE_SOON' },
  },
])

function openTask(task: TaskCard): void {
  void router.push({
    name: 'project-board',
    params: { id: String(task.projectId) },
    query: { task: String(task.id) },
  })
}
</script>

<template>
  <div class="easy-page">
    <!-- Greeting -->
    <header class="workspace__greeting">
      <div>
        <h1 class="workspace__hello">{{ hello }}</h1>
        <p class="workspace__date">{{ today }} · {{ taskBrief }}</p>
      </div>
      <p class="workspace__status">
        EasyOA v0.1.0 · 账号、权限、组织、项目与任务已就绪，审批与通知将随版本逐步开放
      </p>
    </header>

    <!-- 加载失败 -->
    <div v-if="summary.error.value" class="easy-card workspace__error">
      <p>工作台数据加载失败：{{ summary.error.value.message }}</p>
      <EasyButton size="sm" @click="summary.run()">重试</EasyButton>
    </div>

    <!-- KPI -->
    <section class="workspace__kpis" aria-label="关键指标">
      <button
        v-for="kpi in kpis"
        :key="kpi.key"
        type="button"
        class="kpi-card"
        @click="router.push({ name: kpi.routeName, query: kpi.query })"
      >
        <div class="kpi-card__top">
          <span class="kpi-card__label">{{ kpi.label }}</span>
          <el-icon class="kpi-card__icon"><component :is="kpi.icon" /></el-icon>
        </div>
        <div class="kpi-card__value">
          <el-skeleton v-if="summary.loading.value" :rows="0" animated style="width: 48px">
            <template #template><el-skeleton-item variant="text" style="width: 40px" /></template>
          </el-skeleton>
          <template v-else>{{ kpi.value }}</template>
        </div>
        <div class="kpi-card__hint">{{ kpi.hint }}</div>
      </button>
    </section>

    <!-- 我的任务 / 待我审批 -->
    <section class="workspace__grid">
      <div class="easy-card">
        <div class="easy-card__header">
          <span class="easy-card__title">我的任务</span>
          <EasyButton
            v-if="(summary.data.value?.myTasks.length ?? 0) > 0"
            size="sm"
            @click="router.push({ name: 'my-tasks' })"
          >
            全部任务
          </EasyButton>
        </div>
        <div v-if="(summary.data.value?.myTasks.length ?? 0) > 0" class="my-task-list">
          <button
            v-for="task in summary.data.value?.myTasks ?? []"
            :key="task.id"
            type="button"
            class="my-task-item"
            @click="openTask(task)"
          >
            <div class="my-task-item__head">
              <span class="my-task-item__title">{{ task.title }}</span>
              <span v-if="task.overdue" class="my-task-item__overdue">已逾期</span>
              <span v-else-if="task.blocked" class="my-task-item__blocked">阻塞 {{ task.blockerCount }}</span>
            </div>
            <div class="my-task-item__meta">
              <span>{{ task.projectName }}</span>
              <EasyStatus :label="task.status.name" :tone="taskStatusTypeTone(task.status.systemType)" />
              <EasyStatus :label="taskPriorityLabel(task.priority)" :tone="taskPriorityTone(task.priority)" />
              <span class="my-task-item__due" :class="{ 'my-task-item__due--overdue': task.overdue }">
                {{ formatDate(task.plannedEndAt) }}
              </span>
            </div>
            <div class="my-task-item__progress">
              <div class="progress">
                <div class="progress__bar" :style="{ width: `${task.progress}%` }" />
              </div>
              <span class="progress__value">{{ task.progress }}%</span>
            </div>
          </button>
        </div>
        <EasyEmpty
          v-else
          compact
          title="还没有任务数据"
          description="任务由项目负责人在项目看板中创建并派发；指派给你的任务会在这里汇总。"
        >
          <template #action>
            <EasyButton size="sm" variant="primary" @click="router.push({ name: 'my-tasks' })">查看我的任务</EasyButton>
          </template>
        </EasyEmpty>
      </div>

      <div class="easy-card">
        <div class="easy-card__header">
          <span class="easy-card__title">待我审批</span>
          <EasyButton
            v-if="(summary.data.value?.pendingApprovals.length ?? 0) > 0"
            size="sm"
            @click="router.push({ name: 'approvals' })"
          >
            全部审批
          </EasyButton>
        </div>
        <div v-if="(summary.data.value?.pendingApprovals.length ?? 0) > 0" class="approval-mini-list">
          <button
            v-for="approval in summary.data.value?.pendingApprovals ?? []"
            :key="approval.id"
            type="button"
            class="approval-mini-item"
            @click="router.push({ name: 'approval-detail', params: { id: String(approval.id) } })"
          >
            <div class="approval-mini-item__head">
              <span class="approval-mini-item__title">{{ approval.title }}</span>
              <span class="approval-mini-item__type">{{ approval.templateName }}</span>
            </div>
            <div class="approval-mini-item__meta">
              <span>申请人：{{ approval.applicant.displayName }}</span>
              <span v-if="approval.currentNodeName">当前节点：{{ approval.currentNodeName }}</span>
              <span>{{ formatDateTime(approval.submittedAt ?? approval.createdAt) }}</span>
            </div>
          </button>
        </div>
        <EasyEmpty
          v-else
          compact
          title="没有待处理的审批"
          description="待我审批的申请会在这里汇总；敏感审批必须进入详情页处理，不支持一键批准。"
        />
      </div>
    </section>

    <!-- 项目动态 / 项目进度 -->
    <section class="workspace__grid">
      <div class="easy-card">
        <div class="easy-card__header">
          <span class="easy-card__title">项目动态</span>
        </div>
        <EasyEmpty
          compact
          title="暂无动态"
          phase="Phase 7"
          description="Activity Feed 将在 Phase 7 交付，用于展示成员完成任务、评论与状态变化。"
        />
      </div>

      <div class="easy-card">
        <div class="easy-card__header">
          <span class="easy-card__title">项目进度</span>
          <EasyButton v-if="(summary.data.value?.projectProgress.length ?? 0) > 0" size="sm" @click="router.push({ name: 'projects' })">
            全部项目
          </EasyButton>
        </div>
        <div v-if="(summary.data.value?.projectProgress.length ?? 0) > 0" class="project-progress-list">
          <button
            v-for="project in summary.data.value?.projectProgress ?? []"
            :key="project.id"
            type="button"
            class="project-progress-item"
            @click="router.push({ name: 'project-detail', params: { id: project.id } })"
          >
            <div class="project-progress-item__head">
              <span class="project-progress-item__name">{{ project.name }}</span>
              <EasyStatus :label="projectStatusLabel(project.status)" :tone="projectStatusTone(project.status)" />
            </div>
            <div class="project-progress-item__bar">
              <div class="progress">
                <div class="progress__bar" :style="{ width: `${project.progress}%` }" />
              </div>
              <span class="progress__value">{{ project.progress }}%</span>
            </div>
            <div class="project-progress-item__meta">
              <span>{{ project.owner?.displayName ?? '未设置负责人' }}</span>
              <span>{{ project.memberCount }} 位成员</span>
              <span v-if="project.myRole">{{ projectRoleLabel(project.myRole) }}</span>
            </div>
          </button>
        </div>
        <EasyEmpty
          v-else
          compact
          title="还没有参与的项目"
          description="创建或加入项目后，这里会展示项目总进度、负责人与成员规模。"
        >
          <template #action>
            <EasyButton size="sm" variant="primary" @click="router.push({ name: 'projects' })">去创建项目</EasyButton>
          </template>
        </EasyEmpty>
      </div>
    </section>

    <!-- 账号与安全（真实数据） -->
    <section class="easy-card">
      <div class="easy-card__header">
        <span class="easy-card__title">账号安全</span>
      </div>
      <div class="security-grid">
        <div class="security-item">
          <span class="security-item__label">当前账号</span>
          <span class="security-item__value">{{ auth.user?.username }}</span>
          <span class="security-item__hint">{{ auth.user?.systemRole }}</span>
        </div>
        <div class="security-item">
          <span class="security-item__label">上次登录</span>
          <span class="security-item__value">{{ formatDateTime(summary.data.value?.me.lastLoginAt) }}</span>
          <span class="security-item__hint">{{ formatRelative(summary.data.value?.me.lastLoginAt) }}</span>
        </div>
        <div class="security-item">
          <span class="security-item__label">活动会话</span>
          <span class="security-item__value">{{ activeSessionCount }}</span>
          <span class="security-item__hint">可在修改密码后自动失效其他设备</span>
        </div>
        <div class="security-item">
          <span class="security-item__label">两步验证（TOTP）</span>
          <span class="security-item__value">{{ auth.user?.totpEnabled ? '已启用' : '未启用' }}</span>
          <span class="security-item__hint">绑定入口将在 Phase 8 开放</span>
        </div>
      </div>
    </section>
  </div>
</template>

<style scoped>
.workspace__greeting {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: var(--easy-space-4);
  flex-wrap: wrap;
}

.workspace__hello {
  font-size: var(--easy-text-2xl);
  font-weight: 600;
  letter-spacing: -0.02em;
}

.workspace__date {
  margin-top: var(--easy-space-1);
  color: var(--easy-text-3);
  font-size: var(--easy-text-sm);
}

.workspace__status {
  color: var(--easy-text-3);
  font-size: var(--easy-text-xs);
  max-width: 380px;
  text-align: right;
  line-height: var(--easy-leading-relaxed);
}

.workspace__error {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--easy-space-4);
  padding: var(--easy-space-4) var(--easy-space-5);
  color: var(--easy-danger);
  font-size: var(--easy-text-sm);
}

.workspace__kpis {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: var(--easy-space-4);
}

@media (max-width: 1279px) {
  .workspace__kpis {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

.kpi-card {
  display: flex;
  flex-direction: column;
  gap: var(--easy-space-2);
  padding: var(--easy-space-4) var(--easy-space-5);
  border: 1px solid var(--easy-border);
  border-radius: var(--easy-radius-lg);
  background: var(--easy-surface);
  box-shadow: var(--easy-shadow-xs);
  text-align: left;
  cursor: pointer;
  transition: border-color var(--easy-transition-fast), box-shadow var(--easy-transition-fast),
    transform var(--easy-transition-fast);
}

.kpi-card:hover {
  border-color: var(--easy-brand-subtle-border);
  box-shadow: var(--easy-shadow-sm);
  transform: translateY(-1px);
}

.kpi-card__top {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.kpi-card__label {
  color: var(--easy-text-2);
  font-size: var(--easy-text-sm);
}

.kpi-card__icon {
  color: var(--easy-text-3);
  font-size: 16px;
}

.kpi-card__value {
  font-size: 26px;
  font-weight: 600;
  letter-spacing: -0.02em;
  line-height: 1.2;
}

.kpi-card__hint {
  color: var(--easy-text-3);
  font-size: var(--easy-text-xs);
}

.workspace__grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--easy-space-4);
}

@media (max-width: 1100px) {
  .workspace__grid {
    grid-template-columns: 1fr;
  }
}

.security-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: var(--easy-space-4);
  padding: var(--easy-space-5);
}

@media (max-width: 1279px) {
  .security-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

.security-item {
  display: flex;
  flex-direction: column;
  gap: var(--easy-space-1);
}

.security-item__label {
  color: var(--easy-text-3);
  font-size: var(--easy-text-xs);
}

.security-item__value {
  font-size: var(--easy-text-base);
  font-weight: 600;
}

.security-item__hint {
  color: var(--easy-text-3);
  font-size: var(--easy-text-xs);
}

/* --- 项目进度区块（Phase 3 起为真实数据） ------------------------------------ */
.project-progress-list {
  display: flex;
  flex-direction: column;
  padding: var(--easy-space-2);
}

.project-progress-item {
  display: flex;
  flex-direction: column;
  gap: var(--easy-space-2);
  padding: var(--easy-space-3);
  border: none;
  border-radius: var(--easy-radius-md);
  background: transparent;
  text-align: left;
  cursor: pointer;
  transition: background var(--easy-transition-fast);
}

.project-progress-item:hover {
  background: var(--easy-surface-hover);
}

.project-progress-item__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--easy-space-2);
}

.project-progress-item__name {
  font-size: var(--easy-text-sm);
  font-weight: 600;
  color: var(--easy-text-1);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.project-progress-item__bar {
  display: flex;
  align-items: center;
  gap: var(--easy-space-3);
}

.progress {
  flex: 1;
  height: 6px;
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
  color: var(--easy-text-2);
  font-variant-numeric: tabular-nums;
}

.project-progress-item__meta {
  display: flex;
  gap: var(--easy-space-4);
  font-size: var(--easy-text-xs);
  color: var(--easy-text-3);
}

/* --- 我的任务区块（Phase 4 起为真实数据） ------------------------------------- */
.my-task-list {
  display: flex;
  flex-direction: column;
  padding: var(--easy-space-2);
}

.my-task-item {
  display: flex;
  flex-direction: column;
  gap: var(--easy-space-2);
  padding: var(--easy-space-3);
  border: none;
  border-radius: var(--easy-radius-md);
  background: transparent;
  text-align: left;
  cursor: pointer;
  transition: background var(--easy-transition-fast);
}

.my-task-item:hover {
  background: var(--easy-surface-hover);
}

.my-task-item__head {
  display: flex;
  align-items: center;
  gap: var(--easy-space-2);
  min-width: 0;
}

.my-task-item__title {
  font-size: var(--easy-text-sm);
  font-weight: 600;
  color: var(--easy-text-1);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.my-task-item__overdue,
.my-task-item__blocked {
  flex: none;
  font-size: var(--easy-text-xs);
  border-radius: var(--easy-radius-full);
  padding: 1px 8px;
}

.my-task-item__overdue {
  color: var(--easy-danger);
  border: 1px solid var(--easy-danger);
}

.my-task-item__blocked {
  color: var(--easy-warning);
  border: 1px solid var(--easy-warning);
}

.my-task-item__meta {
  display: flex;
  align-items: center;
  gap: var(--easy-space-3);
  font-size: var(--easy-text-xs);
  color: var(--easy-text-3);
  flex-wrap: wrap;
}

.my-task-item__due {
  font-variant-numeric: tabular-nums;
}

.my-task-item__due--overdue {
  color: var(--easy-danger);
  font-weight: 600;
}

.my-task-item__progress {
  display: flex;
  align-items: center;
  gap: var(--easy-space-3);
}

/* --- 待我审批区块（Phase 6 起为真实数据） ------------------------------------- */
.approval-mini-list {
  display: flex;
  flex-direction: column;
  padding: var(--easy-space-2);
}

.approval-mini-item {
  display: flex;
  flex-direction: column;
  gap: var(--easy-space-2);
  padding: var(--easy-space-3);
  border: none;
  border-radius: var(--easy-radius-md);
  background: transparent;
  text-align: left;
  cursor: pointer;
  transition: background var(--easy-transition-fast);
}

.approval-mini-item:hover {
  background: var(--easy-surface-hover);
}

.approval-mini-item__head {
  display: flex;
  align-items: center;
  gap: var(--easy-space-2);
  min-width: 0;
}

.approval-mini-item__title {
  font-size: var(--easy-text-sm);
  font-weight: 600;
  color: var(--easy-text-1);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.approval-mini-item__type {
  flex: none;
  font-size: var(--easy-text-xs);
  color: var(--easy-brand-text);
  background: var(--easy-brand-subtle);
  border-radius: var(--easy-radius-full);
  padding: 1px 8px;
}

.approval-mini-item__meta {
  display: flex;
  align-items: center;
  gap: var(--easy-space-3);
  font-size: var(--easy-text-xs);
  color: var(--easy-text-3);
  flex-wrap: wrap;
}
</style>