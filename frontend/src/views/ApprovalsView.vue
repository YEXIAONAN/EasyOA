<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import { ApiError } from '@/api/errors'
import { approvalApi } from '@/api/modules/approvals'
import type { ApprovalCard, ApprovalScope } from '@/api/types'
import ApprovalCreateDialog from '@/components/approval/ApprovalCreateDialog.vue'
import ApprovalTemplatesDialog from '@/components/approval/ApprovalTemplatesDialog.vue'
import EasyAvatar from '@/components/easy/EasyAvatar.vue'
import EasyButton from '@/components/easy/EasyButton.vue'
import EasyEmpty from '@/components/easy/EasyEmpty.vue'
import EasyStatus from '@/components/easy/EasyStatus.vue'
import { useAuthStore } from '@/stores/auth'
import { formatDateTime } from '@/utils/format'
import { approvalStatusLabel, approvalStatusTone } from '@/utils/approval'

/**
 * 审批首页：待我审批 / 我发起的 / 已完成 + 发起申请。
 *
 * 审批卡片展示：类型（模板名）/ 申请人 / 申请时间 / 当前节点 / 状态；
 * Tab 与 URL query 同步。首页不提供一键批准（必须进入详情页处理）。
 */
const route = useRoute()
const router = useRouter()
const auth = useAuthStore()

const tabs: Array<{ key: ApprovalScope; label: string }> = [
  { key: 'PENDING', label: '待我审批' },
  { key: 'MINE', label: '我发起的' },
  { key: 'FINISHED', label: '已完成' },
]

const scope = ref<ApprovalScope>('PENDING')
const page = ref(1)
const pageSize = 15
const total = ref(0)
const items = ref<ApprovalCard[]>([])
const loading = ref(false)
const loadError = ref<string | null>(null)

const createOpen = ref(false)
const templatesOpen = ref(false)

const emptyText = computed(() => {
  if (scope.value === 'PENDING') return '当前没有需要你审批的申请'
  if (scope.value === 'MINE') return '你还没有发起过审批'
  return '暂无已完成的审批'
})

function readScopeFromQuery(): void {
  const value = route.query.scope
  const matched = tabs.find((tab) => tab.key === value)
  scope.value = matched ? matched.key : 'PENDING'
}

async function load(): Promise<void> {
  loading.value = true
  loadError.value = null
  try {
    const result = await approvalApi.list(scope.value, page.value, pageSize)
    items.value = result.items
    total.value = result.total
  } catch (error) {
    loadError.value = error instanceof ApiError ? error.message : '审批加载失败'
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  readScopeFromQuery()
  void load()
})

watch(
  () => route.query.scope,
  () => {
    readScopeFromQuery()
    page.value = 1
    void load()
  },
)

watch(scope, (value) => {
  if (route.query.scope !== value) {
    void router.replace({ query: { ...route.query, scope: value } })
  }
})

function changePage(next: number): void {
  page.value = next
  void load()
}

function openDetail(card: ApprovalCard): void {
  void router.push({ name: 'approval-detail', params: { id: String(card.id) } })
}

function onSubmitted(instanceId: number): void {
  void router.push({ name: 'approval-detail', params: { id: String(instanceId) } })
}
</script>

<template>
  <div class="easy-page">
    <header class="page-head">
      <div>
        <h1 class="page-head__title">审批</h1>
        <p class="page-head__desc">
          模板化审批与可配置审批节点；同意 / 拒绝 / 退回都需要在详情页确认，不提供一键批准。
        </p>
      </div>
      <div class="page-head__actions">
        <EasyButton v-if="auth.isAdminLike" size="sm" @click="templatesOpen = true">模板管理</EasyButton>
        <EasyButton variant="primary" @click="createOpen = true">发起申请</EasyButton>
      </div>
    </header>

    <div class="filter-tabs">
      <button
        v-for="tab in tabs"
        :key="tab.key"
        type="button"
        class="filter-tabs__item"
        :class="{ 'filter-tabs__item--active': scope === tab.key }"
        @click="scope = tab.key"
      >
        {{ tab.label }}
      </button>
    </div>

    <div v-if="loadError" class="easy-card list-error">
      <span>{{ loadError }}</span>
      <EasyButton size="sm" @click="load">重试</EasyButton>
    </div>

    <div v-else-if="loading && items.length === 0" class="easy-card skeleton-block">正在加载审批…</div>

    <div v-else-if="items.length === 0" class="easy-card">
      <EasyEmpty :title="emptyText" description="发起申请后，审批进度与流转历史会在这里汇总。">
        <template #action>
          <EasyButton v-if="scope === 'MINE'" size="sm" variant="primary" @click="createOpen = true">
            发起申请
          </EasyButton>
        </template>
      </EasyEmpty>
    </div>

    <template v-else>
      <ul class="approval-list">
        <li v-for="card in items" :key="card.id" class="approval-row" @click="openDetail(card)">
          <div class="approval-row__main">
            <div class="approval-row__title">
              {{ card.title }}
              <span class="approval-row__type">{{ card.templateName }}</span>
            </div>
            <div class="approval-row__meta">
              <span class="approval-row__applicant">
                <EasyAvatar :name="card.applicant.displayName" :src="card.applicant.avatarUrl ?? null" size="sm" />
                {{ card.applicant.displayName }}
              </span>
              <span>申请于 {{ formatDateTime(card.submittedAt ?? card.createdAt) }}</span>
              <span v-if="card.currentNodeName">当前节点：{{ card.currentNodeName }}</span>
            </div>
          </div>
          <EasyStatus :label="approvalStatusLabel(card.status)" :tone="approvalStatusTone(card.status)" />
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

    <ApprovalCreateDialog v-model="createOpen" @submitted="onSubmitted" />
    <ApprovalTemplatesDialog v-model="templatesOpen" />
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

.approval-list {
  display: flex;
  flex-direction: column;
  border: 1px solid var(--easy-border);
  border-radius: var(--easy-radius-lg);
  background: var(--easy-surface);
  overflow: hidden;
}

.approval-row {
  display: flex;
  align-items: center;
  gap: var(--easy-space-4);
  padding: var(--easy-space-4) var(--easy-space-5);
  border-bottom: 1px solid var(--easy-border);
  cursor: pointer;
  transition: background var(--easy-transition-fast);
}

.approval-row:last-child {
  border-bottom: none;
}

.approval-row:hover {
  background: var(--easy-surface-hover);
}

.approval-row__main {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.approval-row__title {
  display: flex;
  align-items: center;
  gap: var(--easy-space-2);
  font-size: var(--easy-text-sm);
  font-weight: 500;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.approval-row__type {
  flex: none;
  font-size: var(--easy-text-xs);
  color: var(--easy-brand-text);
  background: var(--easy-brand-subtle);
  border-radius: var(--easy-radius-full);
  padding: 1px 8px;
}

.approval-row__meta {
  display: flex;
  align-items: center;
  gap: var(--easy-space-4);
  font-size: var(--easy-text-xs);
  color: var(--easy-text-3);
  flex-wrap: wrap;
}

.approval-row__applicant {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  color: var(--easy-text-2);
}

.pagination {
  display: flex;
  justify-content: center;
}
</style>