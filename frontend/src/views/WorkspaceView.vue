<script setup lang="ts">
import { computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { DataAnalysis, Folder, Stamp, Tickets } from '@element-plus/icons-vue'
import type { Component } from 'vue'

import { authApi } from '@/api/modules/auth'
import { workspaceApi } from '@/api/modules/workspace'
import EasyButton from '@/components/easy/EasyButton.vue'
import EasyEmpty from '@/components/easy/EasyEmpty.vue'
import EasyStatus from '@/components/easy/EasyStatus.vue'
import { useAsync } from '@/composables/useAsync'
import { useAuthStore } from '@/stores/auth'
import { formatDateTime, formatRelative, greeting } from '@/utils/format'
import { projectRoleLabel, projectStatusLabel, projectStatusTone } from '@/utils/project'

/**
 * 工作台首页：回答三个问题 ——
 *   1. 我现在需要做什么（我的任务 / KPI）
 *   2. 什么事情正在等我（待我审批）
 *   3. 我的项目发生了什么（项目动态 / 项目进度）
 *
 * Phase 1 说明：项目 / 任务 / 审批模块尚未交付，对应区块以空状态呈现真实进度，
 * 不展示任何假数据；账号与安全信息为真实数据。
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

const activeSessionCount = computed(() => sessions.data.value?.length ?? 0)

interface KpiCard {
  key: string
  label: string
  value: number
  hint: string
  icon: Component
  routeName: string
}

const kpis = computed<KpiCard[]>(() => [
  {
    key: 'tasks',
    label: '我的任务',
    value: summary.data.value?.kpis.myOpenTasks ?? 0,
    hint: 'Phase 4 起接入',
    icon: Tickets,
    routeName: 'my-tasks',
  },
  {
    key: 'approvals',
    label: '待我审批',
    value: summary.data.value?.kpis.pendingApprovals ?? 0,
    hint: 'Phase 6 起接入',
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
    hint: 'Phase 4 起接入',
    icon: DataAnalysis,
    routeName: 'my-tasks',
  },
])
</script>

<template>
  <div class="easy-page">
    <!-- Greeting -->
    <header class="workspace__greeting">
      <div>
        <h1 class="workspace__hello">{{ hello }}</h1>
        <p class="workspace__date">{{ today }}</p>
      </div>
      <p class="workspace__status">
        EasyOA v0.1.0 · 账号、权限与安全审计已就绪，协作模块将随版本逐步开放
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
        @click="router.push({ name: kpi.routeName })"
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
        </div>
        <EasyEmpty
          compact
          title="还没有任务数据"
          phase="Phase 4"
          description="任务工作流、子任务、依赖与看板将在 Phase 4 交付，届时这里会展示你的近期任务。"
        />
      </div>

      <div class="easy-card">
        <div class="easy-card__header">
          <span class="easy-card__title">待我审批</span>
        </div>
        <EasyEmpty
          compact
          title="没有待处理的审批"
          phase="Phase 6"
          description="审批模块交付后，待办审批会在这里汇总；敏感审批必须进入详情页处理，不支持一键批准。"
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
</style>