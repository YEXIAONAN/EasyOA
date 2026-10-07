<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'

import { ApiError } from '@/api/errors'
import { insightsApi } from '@/api/modules/insights'
import type { InsightsOverview, ProjectHealthLevel } from '@/api/types'
import EasyButton from '@/components/easy/EasyButton.vue'
import EasyEmpty from '@/components/easy/EasyEmpty.vue'
import EasyStatus from '@/components/easy/EasyStatus.vue'
import { formatDateTime, formatDate } from '@/utils/format'

/**
 * 数据中心（Phase 9）。
 *
 * 产品约束：保持克制——只呈现项目健康度、任务完成趋势、逾期任务、成员负载与审批效率；
 * 不引入图表库，趋势用原生 CSS 柱状呈现，避免为了「看起来专业」堆砌无决策价值的指标。
 */
const router = useRouter()

const data = ref<InsightsOverview | null>(null)
const loading = ref(true)
const loadError = ref<string | null>(null)

const healthLabels: Record<ProjectHealthLevel, string> = {
  HEALTHY: '正常',
  AT_RISK: '有逾期',
  BLOCKED: '已超期',
}
const healthTones: Record<ProjectHealthLevel, 'success' | 'warning' | 'danger'> = {
  HEALTHY: 'success',
  AT_RISK: 'warning',
  BLOCKED: 'danger',
}

const trendMax = computed(() => {
  const points = data.value?.taskTrend ?? []
  return Math.max(1, ...points.map((point) => Math.max(point.createdCount, point.completedCount)))
})

const workloadMax = computed(() => {
  const items = data.value?.workload ?? []
  return Math.max(1, ...items.map((item) => item.openTasks))
})

const kpis = computed(() => {
  const overview = data.value
  if (!overview) return []
  const trend = overview.taskTrend
  const recentCompleted = trend.slice(-4).reduce((sum, point) => sum + point.completedCount, 0)
  const approvals = overview.approvalEfficiency
  return [
    { label: '进行中项目', value: String(overview.projectHealth.length), unit: '个' },
    { label: '逾期任务', value: String(overview.overdueTasks.length), unit: '项', danger: overview.overdueTasks.length > 0 },
    { label: '近 4 周完成', value: String(recentCompleted), unit: '项' },
    {
      label: '平均审批时长',
      value: approvals.averageHours === null || approvals.averageHours === undefined
        ? '—'
        : String(approvals.averageHours),
      unit: approvals.averageHours === null || approvals.averageHours === undefined ? '' : '小时',
    },
  ]
})

async function load(): Promise<void> {
  loading.value = true
  loadError.value = null
  try {
    data.value = await insightsApi.overview()
  } catch (e) {
    loadError.value = e instanceof ApiError ? e.message : '数据中心加载失败'
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  void load()
})

function openProject(projectId: number): void {
  void router.push({ name: 'project-detail', params: { id: String(projectId) } })
}

function openTask(item: { projectId: number; taskId: number }): void {
  void router.push({
    name: 'project-board',
    params: { id: String(item.projectId) },
    query: { task: String(item.taskId) },
  })
}

function weekLabel(weekStart: string): string {
  const date = new Date(`${weekStart}T00:00:00Z`)
  if (Number.isNaN(date.getTime())) return weekStart
  return `${date.getMonth() + 1}/${date.getDate()}`
}
</script>

<template>
  <div class="easy-page">
    <header class="page-head">
      <div>
        <h1 class="page-head__title">数据中心</h1>
        <p class="page-head__desc">
          <template v-if="data?.scope === 'ALL'">
            你以管理员身份查看全局数据：全部项目、任务与审批。
          </template>
          <template v-else>
            数据范围仅包含你参与的项目与与你相关的审批；管理员可查看全局。
          </template>
        </p>
      </div>
      <div class="page-head__actions">
        <span v-if="data" class="page-head__stamp">生成于 {{ formatDateTime(data.generatedAt) }}</span>
        <EasyButton size="sm" @click="load">刷新</EasyButton>
      </div>
    </header>

    <div v-if="loadError" class="easy-card list-error">
      <span>{{ loadError }}</span>
      <EasyButton size="sm" @click="load">重试</EasyButton>
    </div>

    <div v-else-if="loading" class="easy-card skeleton-block">正在汇总数据…</div>

    <template v-else-if="data">
      <section class="kpi-row">
        <div v-for="kpi in kpis" :key="kpi.label" class="kpi" :class="{ 'kpi--danger': kpi.danger }">
          <div class="kpi__label">{{ kpi.label }}</div>
          <div class="kpi__value">
            {{ kpi.value }}<span v-if="kpi.unit" class="kpi__unit">{{ kpi.unit }}</span>
          </div>
        </div>
      </section>

      <div class="grid">
        <section class="easy-card panel">
          <h2 class="panel__title">项目健康度</h2>
          <p class="panel__desc">按逾期任务数与计划偏差判定，点击进入项目。</p>
          <ul v-if="data.projectHealth.length > 0" class="health-list">
            <li
              v-for="item in data.projectHealth"
              :key="item.projectId"
              class="health-row"
              @click="openProject(item.projectId)"
            >
              <div class="health-row__head">
                <span class="health-row__name">{{ item.name }}</span>
                <EasyStatus :label="healthLabels[item.health]" :tone="healthTones[item.health]" />
              </div>
              <div class="bar">
                <div class="bar__fill" :style="{ width: `${Math.min(100, Math.max(0, item.progress))}%` }" />
              </div>
              <div class="health-row__meta">
                <span>进度 {{ item.progress }}%</span>
                <span>任务 {{ item.doneTasks }}/{{ item.totalTasks }}</span>
                <span :class="{ 'is-danger': item.overdueTasks > 0 }">逾期 {{ item.overdueTasks }}</span>
                <span v-if="item.plannedEndAt">截止 {{ formatDate(item.plannedEndAt) }}</span>
              </div>
            </li>
          </ul>
          <EasyEmpty v-else compact title="暂无可见项目" description="创建项目并加入成员后，这里会显示项目健康度。" />
        </section>

        <section class="easy-card panel">
          <h2 class="panel__title">任务完成趋势</h2>
          <p class="panel__desc">近 6 周：浅色为新建，深色为完成。</p>
          <div v-if="data.taskTrend.length > 0" class="trend">
            <div v-for="point in data.taskTrend" :key="point.weekStart" class="trend__col">
              <div class="trend__bars">
                <div
                  class="trend__bar trend__bar--created"
                  :style="{ height: `${(point.createdCount / trendMax) * 100}%` }"
                  :title="`新建 ${point.createdCount}`"
                />
                <div
                  class="trend__bar trend__bar--completed"
                  :style="{ height: `${(point.completedCount / trendMax) * 100}%` }"
                  :title="`完成 ${point.completedCount}`"
                />
              </div>
              <div class="trend__label">{{ weekLabel(point.weekStart) }}</div>
            </div>
          </div>
          <EasyEmpty v-else compact title="暂无趋势数据" description="任务创建与完成后会在这里形成趋势。" />
          <div class="legend">
            <span class="legend__item"><i class="legend__dot legend__dot--created" />新建</span>
            <span class="legend__item"><i class="legend__dot legend__dot--completed" />完成</span>
          </div>
        </section>

        <section class="easy-card panel">
          <h2 class="panel__title">逾期任务</h2>
          <p class="panel__desc">按截止时间由早到晚排列，点击进入任务详情。</p>
          <ul v-if="data.overdueTasks.length > 0" class="task-list">
            <li
              v-for="item in data.overdueTasks"
              :key="item.taskId"
              class="task-row"
              @click="openTask(item)"
            >
              <div class="task-row__main">
                <div class="task-row__title">{{ item.title }}</div>
                <div class="task-row__meta">
                  <span>{{ item.projectName }}</span>
                  <span>{{ item.primaryAssignee }}</span>
                  <span>截止 {{ formatDate(item.plannedEndAt) }}</span>
                </div>
              </div>
              <div class="task-row__right">
                <span class="task-row__overdue">逾期 {{ item.overdueDays }} 天</span>
                <span class="task-row__progress">{{ item.progress }}%</span>
              </div>
            </li>
          </ul>
          <EasyEmpty v-else compact title="没有逾期任务" description="所有任务都在计划时间内，保持这个节奏。" />
        </section>

        <section class="easy-card panel">
          <h2 class="panel__title">成员任务负载</h2>
          <p class="panel__desc">按未完成任务数排序（主负责人维度）。</p>
          <ul v-if="data.workload.length > 0" class="load-list">
            <li v-for="item in data.workload" :key="item.userId" class="load-row">
              <span class="load-row__name">{{ item.displayName }}</span>
              <div class="bar bar--flex">
                <div
                  class="bar__fill"
                  :style="{ width: `${(item.openTasks / workloadMax) * 100}%` }"
                />
              </div>
              <span class="load-row__value">
                {{ item.openTasks }} 项
                <em v-if="item.overdueTasks > 0" class="load-row__overdue">逾期 {{ item.overdueTasks }}</em>
              </span>
            </li>
          </ul>
          <EasyEmpty v-else compact title="暂无负载数据" description="有未完成任务后会在这里聚合。" />
        </section>
      </div>

      <section class="easy-card panel panel--full">
        <h2 class="panel__title">审批效率</h2>
        <p class="panel__desc">
          基于已完结审批的「提交 → 完结」时长统计；通过率 = 通过 /（通过 + 拒绝）。
        </p>
        <div class="approval-stats">
          <div class="stat">
            <div class="stat__label">待处理</div>
            <div class="stat__value">{{ data.approvalEfficiency.pending }}</div>
          </div>
          <div class="stat">
            <div class="stat__label">已通过</div>
            <div class="stat__value">{{ data.approvalEfficiency.approved }}</div>
          </div>
          <div class="stat">
            <div class="stat__label">已拒绝</div>
            <div class="stat__value">{{ data.approvalEfficiency.rejected }}</div>
          </div>
          <div class="stat">
            <div class="stat__label">已退回</div>
            <div class="stat__value">{{ data.approvalEfficiency.returned }}</div>
          </div>
          <div class="stat">
            <div class="stat__label">平均时长</div>
            <div class="stat__value">
              {{ data.approvalEfficiency.averageHours ?? '—' }}
              <span class="stat__unit">{{ data.approvalEfficiency.averageHours == null ? '' : '小时' }}</span>
            </div>
          </div>
          <div class="stat">
            <div class="stat__label">通过率</div>
            <div class="stat__value">
              {{ data.approvalEfficiency.approvedRate ?? '—' }}
              <span class="stat__unit">{{ data.approvalEfficiency.approvedRate == null ? '' : '%' }}</span>
            </div>
          </div>
        </div>
        <p v-if="data.approvalEfficiency.finishedCount === 0" class="panel__note">
          还没有已完结的审批，暂无平均时长与通过率。
        </p>
      </section>
    </template>
  </div>
</template>

<style scoped>
.page-head {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: var(--easy-space-4);
  flex-wrap: wrap;
}

.page-head__title {
  font-size: var(--easy-text-2xl);
  font-weight: 600;
  letter-spacing: -0.02em;
}

.page-head__desc {
  margin-top: var(--easy-space-1);
  color: var(--easy-text-3);
  font-size: var(--easy-text-sm);
}

.page-head__actions {
  display: flex;
  align-items: center;
  gap: var(--easy-space-3);
}

.page-head__stamp {
  color: var(--easy-text-3);
  font-size: var(--easy-text-xs);
}

.list-error,
.skeleton-block {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--easy-space-4);
  padding: var(--easy-space-4) var(--easy-space-5);
  font-size: var(--easy-text-sm);
}

.list-error {
  color: var(--easy-danger);
}

.skeleton-block {
  color: var(--easy-text-3);
}

.kpi-row {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(160px, 1fr));
  gap: var(--easy-space-4);
}

.kpi {
  padding: var(--easy-space-4) var(--easy-space-5);
  border: 1px solid var(--easy-border);
  border-radius: var(--easy-radius-lg);
  background: var(--easy-surface);
}

.kpi--danger {
  border-color: var(--easy-danger);
}

.kpi__label {
  color: var(--easy-text-3);
  font-size: var(--easy-text-xs);
}

.kpi__value {
  margin-top: var(--easy-space-1);
  font-size: 26px;
  font-weight: 600;
  letter-spacing: -0.02em;
}

.kpi--danger .kpi__value {
  color: var(--easy-danger);
}

.kpi__unit {
  margin-left: 4px;
  color: var(--easy-text-3);
  font-size: var(--easy-text-sm);
  font-weight: 500;
}

.grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(360px, 1fr));
  gap: var(--easy-space-4);
}

.panel {
  display: flex;
  flex-direction: column;
  gap: var(--easy-space-3);
  padding: var(--easy-space-5);
}

.panel--full {
  grid-column: 1 / -1;
}

.panel__title {
  font-size: var(--easy-text-lg);
  font-weight: 600;
}

.panel__desc {
  color: var(--easy-text-3);
  font-size: var(--easy-text-xs);
}

.panel__note {
  color: var(--easy-text-3);
  font-size: var(--easy-text-xs);
}

.health-list,
.task-list,
.load-list {
  display: flex;
  flex-direction: column;
  gap: var(--easy-space-2);
}

.health-row {
  display: flex;
  flex-direction: column;
  gap: var(--easy-space-2);
  padding: var(--easy-space-3);
  border: 1px solid var(--easy-border);
  border-radius: var(--easy-radius-md);
  cursor: pointer;
  transition: background var(--easy-transition-fast);
}

.health-row:hover,
.task-row:hover {
  background: var(--easy-surface-hover);
}

.health-row__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--easy-space-3);
}

.health-row__name {
  font-size: var(--easy-text-sm);
  font-weight: 500;
}

.health-row__meta,
.task-row__meta {
  display: flex;
  align-items: center;
  gap: var(--easy-space-3);
  color: var(--easy-text-3);
  font-size: var(--easy-text-xs);
  flex-wrap: wrap;
}

.is-danger {
  color: var(--easy-danger);
}

.bar {
  height: 6px;
  border-radius: var(--easy-radius-full);
  background: var(--easy-surface-sunken);
  overflow: hidden;
}

.bar--flex {
  flex: 1;
  min-width: 60px;
}

.bar__fill {
  height: 100%;
  border-radius: var(--easy-radius-full);
  background: var(--easy-brand);
}

.trend {
  display: flex;
  align-items: flex-end;
  gap: var(--easy-space-3);
  height: 140px;
  padding-top: var(--easy-space-2);
}

.trend__col {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--easy-space-2);
  height: 100%;
}

.trend__bars {
  flex: 1;
  width: 100%;
  display: flex;
  align-items: flex-end;
  justify-content: center;
  gap: 4px;
}

.trend__bar {
  width: 12px;
  min-height: 2px;
  border-radius: var(--easy-radius-xs) var(--easy-radius-xs) 0 0;
}

.trend__bar--created {
  background: var(--easy-brand-subtle-border);
}

.trend__bar--completed {
  background: var(--easy-brand);
}

.trend__label {
  color: var(--easy-text-3);
  font-size: 10px;
}

.legend {
  display: flex;
  gap: var(--easy-space-4);
  color: var(--easy-text-3);
  font-size: var(--easy-text-xs);
}

.legend__item {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.legend__dot {
  width: 8px;
  height: 8px;
  border-radius: var(--easy-radius-xs);
}

.legend__dot--created {
  background: var(--easy-brand-subtle-border);
}

.legend__dot--completed {
  background: var(--easy-brand);
}

.task-row {
  display: flex;
  align-items: center;
  gap: var(--easy-space-3);
  padding: var(--easy-space-3);
  border: 1px solid var(--easy-border);
  border-radius: var(--easy-radius-md);
  cursor: pointer;
  transition: background var(--easy-transition-fast);
}

.task-row__main {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.task-row__title {
  font-size: var(--easy-text-sm);
  font-weight: 500;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.task-row__right {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 2px;
  flex: none;
}

.task-row__overdue {
  color: var(--easy-danger);
  font-size: var(--easy-text-xs);
  font-weight: 500;
}

.task-row__progress {
  color: var(--easy-text-3);
  font-size: var(--easy-text-xs);
}

.load-row {
  display: flex;
  align-items: center;
  gap: var(--easy-space-3);
  font-size: var(--easy-text-sm);
}

.load-row__name {
  width: 96px;
  flex: none;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.load-row__value {
  flex: none;
  color: var(--easy-text-2);
  font-size: var(--easy-text-xs);
}

.load-row__overdue {
  margin-left: 6px;
  color: var(--easy-danger);
  font-style: normal;
}

.approval-stats {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(120px, 1fr));
  gap: var(--easy-space-3);
}

.stat {
  padding: var(--easy-space-3);
  border: 1px solid var(--easy-border);
  border-radius: var(--easy-radius-md);
  background: var(--easy-surface-sunken);
}

.stat__label {
  color: var(--easy-text-3);
  font-size: var(--easy-text-xs);
}

.stat__value {
  margin-top: 2px;
  font-size: var(--easy-text-xl);
  font-weight: 600;
}

.stat__unit {
  margin-left: 3px;
  color: var(--easy-text-3);
  font-size: var(--easy-text-xs);
  font-weight: 500;
}
</style>
