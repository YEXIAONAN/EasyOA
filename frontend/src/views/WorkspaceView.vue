<script setup lang="ts">
import { computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { DataAnalysis, Folder, Stamp, Tickets } from '@element-plus/icons-vue'
import type { Component } from 'vue'

import { workspaceApi } from '@/api/modules/workspace'
import type { ActivityItem, ProjectCard, TaskCard } from '@/api/types'
import EasyButton from '@/components/easy/EasyButton.vue'
import EasyEmpty from '@/components/easy/EasyEmpty.vue'
import EasyStatus from '@/components/easy/EasyStatus.vue'
import { useAsync } from '@/composables/useAsync'
import { useAuthStore } from '@/stores/auth'
import { formatDate, formatDateTime, formatRelative, greeting } from '@/utils/format'
import { projectStatusLabel, projectStatusTone } from '@/utils/project'
import { taskPriorityLabel, taskPriorityTone, taskStatusTypeTone } from '@/utils/task'

/**
 * 工作台首页：回答两个问题 ——「我现在要做什么」「什么在等我」。
 *
 * 内容策略：只保留可立即行动的待办（我的待办 / 待我审批）与必要的 KPI；
 * 项目进度不由工作台承担（项目管理模块负责），仅在存在已超期项目时给出窄幅提示；
 * 账号与安全信息统一在「安全中心」，此处不重复。
 */
const router = useRouter()
const auth = useAuthStore()

const summary = useAsync(() => workspaceApi.summary(), { immediate: false })

onMounted(() => {
  void summary.run()
})

const hello = computed(() => `${greeting()}，${auth.displayName || summary.data.value?.me.displayName || ''}`)
const today = computed(() => new Intl.DateTimeFormat('zh-CN', { dateStyle: 'full' }).format(new Date()))

const openTaskCount = computed(() => summary.data.value?.kpis.myOpenTasks ?? 0)
const pendingApprovalCount = computed(() => summary.data.value?.kpis.pendingApprovals ?? 0)

/** 辅助说明只陈述当天待处理量（真实数据） */
const todayBrief = computed(() => {
  const parts: string[] = []
  if (openTaskCount.value > 0) parts.push(`${openTaskCount.value} 项任务需要处理`)
  if (pendingApprovalCount.value > 0) parts.push(`${pendingApprovalCount.value} 项审批等待你处理`)
  return parts.length > 0 ? `今天有 ${parts.join('，')}。` : '今天没有待处理的任务与审批。'
})

interface KpiCard {
  key: string
  label: string
  value: number
  /** 指标口径（≤6 字，帮助准确理解数字，不是操作指引） */
  caption: string
  icon: Component
  routeName: string
  query?: Record<string, string>
}

const kpis = computed<KpiCard[]>(() => [
  {
    key: 'tasks',
    label: '我的待办任务',
    value: openTaskCount.value,
    caption: '未完成',
    icon: Tickets,
    routeName: 'my-tasks',
  },
  {
    key: 'approvals',
    label: '待我审批',
    value: pendingApprovalCount.value,
    caption: '待处理',
    icon: Stamp,
    routeName: 'approvals',
  },
  {
    key: 'projects',
    label: '参与项目',
    value: summary.data.value?.kpis.activeProjects ?? 0,
    caption: '进行中',
    icon: Folder,
    routeName: 'projects',
  },
  {
    key: 'due',
    label: '即将到期',
    value: summary.data.value?.kpis.dueSoonTasks ?? 0,
    caption: '近 7 天',
    icon: DataAnalysis,
    routeName: 'my-tasks',
    query: { filter: 'DUE_SOON' },
  },
])

const ACTIVITY_LIMIT = 5
const activities = computed(() => (summary.data.value?.activity ?? []).slice(0, ACTIVITY_LIMIT))

/** 项目进度不由工作台承担：仅在存在「已超期且未完成」项目时给出窄幅提示 */
const atRiskProjects = computed<ProjectCard[]>(() =>
  (summary.data.value?.projectProgress ?? []).filter((project) => {
    if (!project.plannedEndAt || project.progress >= 100) return false
    return new Date(project.plannedEndAt).getTime() < Date.now()
  }),
)

function openTask(task: TaskCard): void {
  void router.push({
    name: 'project-board',
    params: { id: String(task.projectId) },
    query: { task: String(task.id) },
  })
}

/** Activity Feed 深链：直达任务 / 审批 / 项目，不跳回首页 */
function openActivity(item: ActivityItem): void {
  void router.push(item.link)
}
</script>

<template>
  <div class="easy-page">
    <!-- 欢迎区：问候 + 当天待处理量 + 日期 -->
    <header class="ws-head">
      <h1 class="ws-head__hello">{{ hello }}</h1>
      <p class="ws-head__brief">{{ todayBrief }}</p>
      <p class="ws-head__date">{{ today }}</p>
    </header>

    <div v-if="summary.error.value" class="easy-card ws-error">
      <span>工作台数据加载失败：{{ summary.error.value.message }}</span>
      <EasyButton size="sm" @click="summary.run()">重试</EasyButton>
    </div>

    <!-- KPI -->
    <section class="ws-kpis" aria-label="关键指标">
      <button
        v-for="kpi in kpis"
        :key="kpi.key"
        type="button"
        class="kpi"
        @click="router.push({ name: kpi.routeName, query: kpi.query })"
      >
        <div class="kpi__top">
          <span class="kpi__label">{{ kpi.label }}</span>
          <el-icon class="kpi__icon"><component :is="kpi.icon" /></el-icon>
        </div>
        <div class="kpi__value">
          <el-skeleton v-if="summary.loading.value" :rows="0" animated class="kpi__skeleton">
            <template #template><el-skeleton-item variant="text" class="kpi__skeleton-item" /></template>
          </el-skeleton>
          <template v-else>{{ kpi.value }}</template>
        </div>
        <div class="kpi__caption">{{ kpi.caption }}</div>
      </button>
    </section>

    <!-- 主区：我的待办 / 待我审批 -->
    <section class="ws-grid">
      <div class="easy-card">
        <div class="easy-card__header">
          <span class="easy-card__title">我的待办</span>
          <EasyButton
            v-if="(summary.data.value?.myTasks.length ?? 0) > 0"
            size="sm"
            @click="router.push({ name: 'my-tasks' })"
          >
            全部任务
          </EasyButton>
        </div>

        <ul v-if="(summary.data.value?.myTasks.length ?? 0) > 0" class="item-list">
          <li v-for="task in summary.data.value?.myTasks ?? []" :key="task.id">
            <button type="button" class="task-item" @click="openTask(task)">
              <div class="task-item__head">
                <span class="task-item__title">{{ task.title }}</span>
                <span v-if="task.overdue" class="tag tag--danger">已逾期</span>
                <span v-else-if="task.blocked" class="tag tag--warning">阻塞 {{ task.blockerCount }}</span>
              </div>
              <div class="task-item__meta">
                <span>{{ task.projectName }}</span>
                <EasyStatus :label="task.status.name" :tone="taskStatusTypeTone(task.status.systemType)" />
                <EasyStatus :label="taskPriorityLabel(task.priority)" :tone="taskPriorityTone(task.priority)" />
                <span :class="{ 'is-overdue': task.overdue }">{{ formatDate(task.plannedEndAt) }}</span>
              </div>
              <div class="task-item__progress">
                <div class="progress">
                  <div class="progress__bar" :style="{ width: `${task.progress}%` }" />
                </div>
                <span class="progress__value">{{ task.progress }}%</span>
              </div>
            </button>
          </li>
        </ul>
        <EasyEmpty v-else compact title="没有待办任务" description="指派给你的未完成任务会在这里汇总。">
          <template #action>
            <EasyButton size="sm" @click="router.push({ name: 'my-tasks' })">查看我的任务</EasyButton>
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

        <ul v-if="(summary.data.value?.pendingApprovals.length ?? 0) > 0" class="item-list">
          <li v-for="approval in summary.data.value?.pendingApprovals ?? []" :key="approval.id">
            <button
              type="button"
              class="approval-item"
              @click="router.push({ name: 'approval-detail', params: { id: String(approval.id) } })"
            >
              <div class="approval-item__head">
                <span class="approval-item__title">{{ approval.title }}</span>
                <span class="tag tag--brand">{{ approval.templateName }}</span>
              </div>
              <div class="approval-item__meta">
                <span>申请人：{{ approval.applicant.displayName }}</span>
                <span v-if="approval.currentNodeName">当前节点：{{ approval.currentNodeName }}</span>
                <span>{{ formatDateTime(approval.submittedAt ?? approval.createdAt) }}</span>
              </div>
            </button>
          </li>
        </ul>
        <EasyEmpty v-else compact title="没有待处理的审批" description="需要你审批的申请会在这里汇总。" />
      </div>
    </section>

    <!-- 次级区：仅在存在超期项目时给出窄幅提示 -->
    <section v-if="atRiskProjects.length > 0" class="easy-card">
      <div class="easy-card__header">
        <span class="easy-card__title">需要关注的项目</span>
        <EasyButton size="sm" @click="router.push({ name: 'projects' })">全部项目</EasyButton>
      </div>
      <ul class="item-list">
        <li v-for="project in atRiskProjects" :key="project.id">
          <button
            type="button"
            class="task-item"
            @click="router.push({ name: 'project-detail', params: { id: project.id } })"
          >
            <div class="task-item__head">
              <span class="task-item__title">{{ project.name }}</span>
              <span class="tag tag--danger">已超期</span>
            </div>
            <div class="task-item__meta">
              <EasyStatus :label="projectStatusLabel(project.status)" :tone="projectStatusTone(project.status)" />
              <span>计划结束 {{ formatDate(project.plannedEndAt) }}</span>
              <span>进度 {{ project.progress }}%</span>
            </div>
          </button>
        </li>
      </ul>
    </section>

    <!-- 近期动态 -->
    <section class="easy-card">
      <div class="easy-card__header">
        <span class="easy-card__title">近期动态</span>
      </div>
      <ul v-if="activities.length > 0" class="activity-list">
        <li v-for="(item, index) in activities" :key="`${item.type}-${index}`">
          <button type="button" class="activity-item" @click="openActivity(item)">
            <span class="activity-item__dot" aria-hidden="true" />
            <span class="activity-item__text">
              <strong>{{ item.actorName ?? '系统' }}</strong>
              {{ item.action }}
              <span class="activity-item__target">{{ item.target }}</span>
            </span>
            <span class="activity-item__time">{{ formatRelative(item.time) }}</span>
          </button>
        </li>
      </ul>
      <EasyEmpty
        v-else
        compact
        title="暂无动态"
        description="完成任务、评论、发起审批等业务动态会在这里汇总（仅显示你参与的内容）。"
      />
    </section>
  </div>
</template>

<style scoped>
/* --- 欢迎区 ---------------------------------------------------------------- */
.ws-head {
  display: flex;
  flex-direction: column;
  gap: var(--easy-space-1);
}

.ws-head__hello {
  font-size: var(--easy-text-2xl);
  font-weight: 600;
  letter-spacing: -0.02em;
}

.ws-head__brief {
  color: var(--easy-text-2);
  font-size: var(--easy-text-sm);
}

.ws-head__date {
  color: var(--easy-text-3);
  font-size: var(--easy-text-xs);
}

.ws-error {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--easy-space-4);
  padding: var(--easy-space-4) var(--easy-space-5);
  color: var(--easy-danger);
  font-size: var(--easy-text-sm);
}

/* --- KPI ------------------------------------------------------------------ */
.ws-kpis {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: var(--easy-space-4);
}

.kpi {
  display: flex;
  flex-direction: column;
  gap: var(--easy-space-1);
  padding: var(--easy-space-4) var(--easy-space-5);
  border: 1px solid var(--easy-border);
  border-radius: var(--easy-radius-lg);
  background: var(--easy-surface);
  box-shadow: var(--easy-shadow-xs);
  text-align: left;
  cursor: pointer;
  transition: border-color var(--easy-transition-fast), box-shadow var(--easy-transition-fast);
}

.kpi:hover {
  border-color: var(--easy-border-strong);
  box-shadow: var(--easy-shadow-sm);
}

.kpi__top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--easy-space-2);
}

.kpi__label {
  color: var(--easy-text-2);
  font-size: var(--easy-text-sm);
}

.kpi__icon {
  color: var(--easy-text-3);
  font-size: 15px;
}

.kpi__value {
  font-size: 26px;
  font-weight: 600;
  letter-spacing: -0.02em;
  line-height: 1.2;
  font-variant-numeric: tabular-nums;
}

.kpi__skeleton {
  width: 48px;
}

.kpi__skeleton-item {
  height: 26px;
}

.kpi__caption {
  color: var(--easy-text-3);
  font-size: var(--easy-text-xs);
}

/* --- 两栏内容区 ------------------------------------------------------------ */
.ws-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--easy-space-4);
}

.item-list {
  display: flex;
  flex-direction: column;
  padding: var(--easy-space-2);
}

.task-item,
.approval-item {
  display: flex;
  flex-direction: column;
  gap: var(--easy-space-2);
  width: 100%;
  padding: var(--easy-space-3);
  border: none;
  border-radius: var(--easy-radius-md);
  background: transparent;
  text-align: left;
  cursor: pointer;
  transition: background var(--easy-transition-fast);
}

.task-item:hover,
.approval-item:hover {
  background: var(--easy-surface-hover);
}

.task-item__head,
.approval-item__head {
  display: flex;
  align-items: center;
  gap: var(--easy-space-2);
  min-width: 0;
}

.task-item__title,
.approval-item__title {
  font-size: var(--easy-text-sm);
  font-weight: 600;
  color: var(--easy-text-1);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.task-item__meta,
.approval-item__meta {
  display: flex;
  align-items: center;
  gap: var(--easy-space-3);
  font-size: var(--easy-text-xs);
  color: var(--easy-text-3);
  flex-wrap: wrap;
}

.is-overdue {
  color: var(--easy-danger);
  font-weight: 600;
}

.task-item__progress {
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

/* --- 轻量标签（逾期 / 阻塞 / 审批类型） ------------------------------------ */
.tag {
  flex: none;
  padding: 1px 8px;
  border-radius: var(--easy-radius-full);
  font-size: var(--easy-text-xs);
  line-height: 1.6;
}

.tag--danger {
  color: var(--easy-danger);
  border: 1px solid var(--easy-danger);
}

.tag--warning {
  color: var(--easy-warning);
  border: 1px solid var(--easy-warning);
}

.tag--brand {
  color: var(--easy-brand-text);
  background: var(--easy-brand-subtle);
}

/* --- 近期动态 -------------------------------------------------------------- */
.activity-list {
  display: flex;
  flex-direction: column;
  padding: var(--easy-space-2);
}

.activity-item {
  display: flex;
  align-items: center;
  gap: var(--easy-space-3);
  width: 100%;
  padding: var(--easy-space-2) var(--easy-space-3);
  border: none;
  border-radius: var(--easy-radius-md);
  background: transparent;
  text-align: left;
  cursor: pointer;
  transition: background var(--easy-transition-fast);
}

.activity-item:hover {
  background: var(--easy-surface-hover);
}

.activity-item__dot {
  width: 6px;
  height: 6px;
  flex: none;
  border-radius: 50%;
  background: var(--easy-brand);
}

.activity-item__text {
  flex: 1;
  min-width: 0;
  font-size: var(--easy-text-sm);
  color: var(--easy-text-2);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.activity-item__text strong {
  color: var(--easy-text-1);
  font-weight: 600;
}

.activity-item__target {
  color: var(--easy-text-1);
}

.activity-item__time {
  flex: none;
  font-size: var(--easy-text-xs);
  color: var(--easy-text-3);
}

/* --- 响应式 --------------------------------------------------------------- */
@media (max-width: 1279px) {
  .ws-kpis {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 1100px) {
  .ws-grid {
    grid-template-columns: 1fr;
  }
}
</style>