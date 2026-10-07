<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Search } from '@element-plus/icons-vue'

import { ApiError } from '@/api/errors'
import { taskApi } from '@/api/modules/tasks'
import type { TaskCard } from '@/api/types'
import EasyButton from '@/components/easy/EasyButton.vue'
import EasyEmpty from '@/components/easy/EasyEmpty.vue'
import EasyInput from '@/components/easy/EasyInput.vue'
import EasyStatus from '@/components/easy/EasyStatus.vue'
import { formatDate } from '@/utils/format'
import { taskPriorityLabel, taskPriorityTone, taskStatusTypeTone } from '@/utils/task'

/**
 * 我的任务：主负责人 / 副负责人 / 协作成员的任务（跨项目）。
 *
 * 点击任务直接深链到项目看板并打开任务侧栏（/projects/:id/board?task=:taskId），
 * 不跳回首页。筛选状态与 URL 同步，便于分享与刷新。
 */
const route = useRoute()
const router = useRouter()

type TaskFilter = 'OPEN' | 'DUE_SOON' | 'DONE' | 'ALL'

const filters: Array<{ key: TaskFilter; label: string }> = [
  { key: 'OPEN', label: '未完成' },
  { key: 'DUE_SOON', label: '即将到期' },
  { key: 'DONE', label: '已完成' },
  { key: 'ALL', label: '全部' },
]

const activeFilter = ref<TaskFilter>('OPEN')
const keyword = ref('')
const page = ref(1)
const pageSize = 15
const total = ref(0)
const items = ref<TaskCard[]>([])
const loading = ref(false)
const loadError = ref<string | null>(null)

function readFilterFromQuery(): void {
  const filter = route.query.filter
  const matched = filters.find((item) => item.key === filter)
  activeFilter.value = matched ? matched.key : 'OPEN'
}

async function load(): Promise<void> {
  loading.value = true
  loadError.value = null
  try {
    const result = await taskApi.my({
      filter: activeFilter.value,
      keyword: keyword.value.trim() || undefined,
      page: page.value,
      size: pageSize,
    })
    items.value = result.items
    total.value = result.total
  } catch (error) {
    loadError.value = error instanceof ApiError ? error.message : '任务加载失败'
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  readFilterFromQuery()
  void load()
})

watch(
  () => route.query.filter,
  () => {
    readFilterFromQuery()
    page.value = 1
    void load()
  },
)

watch(activeFilter, (filter) => {
  if (route.query.filter !== filter) {
    void router.replace({ query: { ...route.query, filter } })
  }
})

function search(): void {
  page.value = 1
  void load()
}

function changePage(next: number): void {
  page.value = next
  void load()
}

/** 深链：打开项目看板并直接展开该任务（刷新后仍保持） */
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
    <header class="page-head">
      <div>
        <h1 class="page-head__title">我的任务</h1>
        <p class="page-head__desc">
          我是主负责人 / 副负责人 / 协作成员的任务；点击任务直接打开项目看板中的任务侧栏。
        </p>
      </div>
      <div class="page-head__search">
        <EasyInput v-model="keyword" placeholder="搜索任务标题 / 项目名称" @keyup.enter="search">
          <template #prefix><el-icon><Search /></el-icon></template>
        </EasyInput>
        <EasyButton size="sm" @click="search">搜索</EasyButton>
      </div>
    </header>

    <div class="filter-tabs">
      <button
        v-for="filter in filters"
        :key="filter.key"
        type="button"
        class="filter-tabs__item"
        :class="{ 'filter-tabs__item--active': activeFilter === filter.key }"
        @click="activeFilter = filter.key"
      >
        {{ filter.label }}
      </button>
    </div>

    <div v-if="loadError" class="easy-card list-error">
      <span>{{ loadError }}</span>
      <EasyButton size="sm" @click="load">重试</EasyButton>
    </div>

    <div v-else-if="loading && items.length === 0" class="easy-card skeleton-block">正在加载任务…</div>

    <div v-else-if="items.length === 0" class="easy-card">
      <EasyEmpty
        title="没有符合条件的任务"
        description="任务由项目负责人在项目看板中创建并派发；你也可以在项目内创建任务并指派给他人。"
      />
    </div>

    <template v-else>
      <ul class="task-list">
        <li v-for="task in items" :key="task.id" class="task-row" @click="openTask(task)">
          <div class="task-row__main">
            <div class="task-row__title">
              {{ task.title }}
              <span v-if="task.parentId" class="task-row__tag">子任务</span>
            </div>
            <div class="task-row__meta">
              <span>{{ task.projectName }}</span>
              <span>主负责人：{{ task.primaryAssignee.displayName }}</span>
              <span v-if="task.blocked" class="task-row__blocked">阻塞 {{ task.blockerCount }}</span>
            </div>
          </div>
          <div class="task-row__progress">
            <div class="progress">
              <div class="progress__bar" :style="{ width: `${task.progress}%` }" />
            </div>
            <span class="progress__value">{{ task.progress }}%</span>
          </div>
          <EasyStatus :label="task.status.name" :tone="taskStatusTypeTone(task.status.systemType)" />
          <EasyStatus :label="taskPriorityLabel(task.priority)" :tone="taskPriorityTone(task.priority)" />
          <span class="task-row__due" :class="{ 'task-row__due--overdue': task.overdue }">
            {{ formatDate(task.plannedEndAt) }}
          </span>
        </li>
      </ul>

      <div v-if="total > pageSize" class="pagination">
        <el-pagination
          layout="prev, pager, next"
          :total="total"
          :page-size="pageSize"
          :current-page="page"
          background
          @current-change="changePage"
        />
      </div>
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

.page-head__search {
  display: flex;
  align-items: center;
  gap: var(--easy-space-2);
}

.filter-tabs {
  display: flex;
  gap: var(--easy-space-1);
  padding: 3px;
  border: 1px solid var(--easy-border);
  border-radius: var(--easy-radius-md);
  background: var(--easy-surface-sunken);
  width: fit-content;
}

.filter-tabs__item {
  padding: 5px 14px;
  border: none;
  border-radius: var(--easy-radius-sm);
  background: transparent;
  color: var(--easy-text-2);
  font-size: var(--easy-text-sm);
  cursor: pointer;
  transition: background var(--easy-transition-fast), color var(--easy-transition-fast);
}

.filter-tabs__item--active {
  background: var(--easy-surface);
  color: var(--easy-text-1);
  font-weight: 500;
  box-shadow: var(--easy-shadow-xs);
}

.list-error,
.skeleton-block {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--easy-space-4);
  padding: var(--easy-space-4) var(--easy-space-5);
  color: var(--easy-danger);
  font-size: var(--easy-text-sm);
}

.skeleton-block {
  color: var(--easy-text-3);
}

.task-list {
  display: flex;
  flex-direction: column;
  border: 1px solid var(--easy-border);
  border-radius: var(--easy-radius-lg);
  background: var(--easy-surface);
  overflow: hidden;
}

.task-row {
  display: flex;
  align-items: center;
  gap: var(--easy-space-4);
  padding: var(--easy-space-3) var(--easy-space-5);
  border-bottom: 1px solid var(--easy-border);
  cursor: pointer;
  transition: background var(--easy-transition-fast);
}

.task-row:last-child {
  border-bottom: none;
}

.task-row:hover {
  background: var(--easy-surface-hover);
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
  display: flex;
  align-items: center;
  gap: var(--easy-space-2);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.task-row__tag {
  font-size: var(--easy-text-xs);
  color: var(--easy-text-3);
  border: 1px dashed var(--easy-border);
  border-radius: var(--easy-radius-full);
  padding: 0 6px;
  flex: none;
}

.task-row__meta {
  display: flex;
  gap: var(--easy-space-4);
  font-size: var(--easy-text-xs);
  color: var(--easy-text-3);
}

.task-row__blocked {
  color: var(--easy-warning);
}

.task-row__progress {
  display: flex;
  align-items: center;
  gap: var(--easy-space-2);
  width: 120px;
  flex: none;
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
}

.progress__value {
  font-size: var(--easy-text-xs);
  color: var(--easy-text-3);
  font-variant-numeric: tabular-nums;
  width: 34px;
  text-align: right;
}

.task-row__due {
  font-size: var(--easy-text-xs);
  color: var(--easy-text-3);
  flex: none;
  width: 84px;
  text-align: right;
  font-variant-numeric: tabular-nums;
}

.task-row__due--overdue {
  color: var(--easy-danger);
  font-weight: 600;
}

.pagination {
  display: flex;
  justify-content: center;
}
</style>