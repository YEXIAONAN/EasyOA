<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'

import { ApiError } from '@/api/errors'
import { auditLogApi, securityApi } from '@/api/modules/security'
import type { AuditLogItem, SecurityEventItem } from '@/api/types'
import EasyButton from '@/components/easy/EasyButton.vue'
import EasyDrawer from '@/components/easy/EasyDrawer.vue'
import EasyEmpty from '@/components/easy/EasyEmpty.vue'
import EasySelect from '@/components/easy/EasySelect.vue'
import EasyStatus from '@/components/easy/EasyStatus.vue'
import { formatDateTime } from '@/utils/format'
import {
  auditActionLabel,
  prettyJson,
  riskLevelLabel,
  riskLevelTone,
  securityEventTypeLabel,
  severityLabel,
  severityTone,
} from '@/utils/security'

/**
 * 审计与安全事件视图（只读）。
 *
 * 产品约束：审计日志与安全事件 Append Only，页面不提供任何修改 / 删除入口。
 */
type TabKey = 'audit' | 'events'

const tabs: Array<{ key: TabKey; label: string }> = [
  { key: 'audit', label: '审计日志' },
  { key: 'events', label: '安全事件' },
]

const riskOptions = [
  { label: '常规', value: 'NORMAL' },
  { label: '重要', value: 'ELEVATED' },
  { label: '高危', value: 'CRITICAL' },
]

const severityOptions = [
  { label: '提示', value: 'INFO' },
  { label: '警告', value: 'WARNING' },
  { label: '严重', value: 'CRITICAL' },
]

const eventTypeOptions = [
  { label: 'ROOT 高危操作', value: 'ROOT_SENSITIVE_OPERATION' },
  { label: '超级权限操作', value: 'PRIVILEGED_OPERATION' },
  { label: '动态口令重置', value: 'MFA_RESET' },
  { label: '审计数据清理', value: 'AUDIT_DATA_PURGE' },
  { label: '安全策略变更', value: 'SECURITY_POLICY_CHANGED' },
  { label: '敏感数据导出', value: 'SENSITIVE_DATA_EXPORT' },
  { label: '系统数据销毁', value: 'DATA_DESTROYED' },
  { label: '登录被锁定', value: 'LOGIN_BLOCKED' },
  { label: '系统初始化完成', value: 'SETUP_COMPLETED' },
]

const tab = ref<TabKey>('audit')
const page = ref(1)
const pageSize = 20
const total = ref(0)
const loading = ref(false)
const loadError = ref<string | null>(null)

const auditItems = ref<AuditLogItem[]>([])
const eventItems = ref<SecurityEventItem[]>([])

const actionFilter = ref('')
const riskFilter = ref<string | null>(null)
const severityFilter = ref<string | null>(null)
const eventTypeFilter = ref<string | null>(null)

const selectedAudit = ref<AuditLogItem | null>(null)
const selectedEvent = ref<SecurityEventItem | null>(null)
const detailOpen = ref(false)

const emptyText = computed(() =>
  tab.value === 'audit'
    ? '没有匹配的审计记录；调整筛选条件或等待新的业务操作产生审计。'
    : '没有匹配的安全事件；安全事件只在发生高危动作时写入。',
)

async function load(): Promise<void> {
  loading.value = true
  loadError.value = null
  try {
    if (tab.value === 'audit') {
      const result = await auditLogApi.list({
        action: actionFilter.value.trim() || undefined,
        riskLevel: riskFilter.value ?? undefined,
        page: page.value,
        size: pageSize,
      })
      auditItems.value = result.items
      total.value = result.total
    } else {
      const result = await securityApi.securityEvents({
        eventType: eventTypeFilter.value ?? undefined,
        severity: severityFilter.value ?? undefined,
        page: page.value,
        size: pageSize,
      })
      eventItems.value = result.items
      total.value = result.total
    }
  } catch (e) {
    loadError.value = e instanceof ApiError ? e.message : '加载失败'
    auditItems.value = []
    eventItems.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  void load()
})

watch(tab, () => {
  page.value = 1
  void load()
})

watch([actionFilter, riskFilter, severityFilter, eventTypeFilter], () => {
  page.value = 1
  void load()
})

function changePage(next: number): void {
  page.value = next
  void load()
}

function openAudit(item: AuditLogItem): void {
  selectedAudit.value = item
  selectedEvent.value = null
  detailOpen.value = true
}

function openEvent(item: SecurityEventItem): void {
  selectedEvent.value = item
  selectedAudit.value = null
  detailOpen.value = true
}

function resetFilters(): void {
  actionFilter.value = ''
  riskFilter.value = null
  severityFilter.value = null
  eventTypeFilter.value = null
}
</script>

<template>
  <div class="easy-page">
    <header class="page-head">
      <div>
        <h1 class="page-head__title">审计与安全事件</h1>
        <p class="page-head__desc">
          审计日志与安全事件均为永久追加（Append Only），系统不提供任何修改或删除入口。
        </p>
      </div>
    </header>

    <div class="toolbar">
      <div class="filter-tabs">
        <button
          v-for="item in tabs"
          :key="item.key"
          type="button"
          class="filter-tabs__item"
          :class="{ 'filter-tabs__item--active': tab === item.key }"
          @click="tab = item.key"
        >
          {{ item.label }}
        </button>
      </div>

      <div class="toolbar__filters">
        <template v-if="tab === 'audit'">
          <input
            v-model="actionFilter"
            class="text-filter"
            type="search"
            placeholder="按 Action 筛选，如 TASK_CREATED"
            aria-label="按动作筛选"
          />
          <EasySelect
            v-model="riskFilter"
            class="filter-select"
            :options="riskOptions"
            placeholder="风险级别"
          />
        </template>
        <template v-else>
          <EasySelect
            v-model="eventTypeFilter"
            class="filter-select filter-select--wide"
            :options="eventTypeOptions"
            placeholder="事件类型"
          />
          <EasySelect
            v-model="severityFilter"
            class="filter-select"
            :options="severityOptions"
            placeholder="事件级别"
          />
        </template>
        <EasyButton size="sm" @click="resetFilters">重置</EasyButton>
      </div>
    </div>

    <div v-if="loadError" class="easy-card list-error">
      <span>{{ loadError }}</span>
      <EasyButton size="sm" @click="load">重试</EasyButton>
    </div>

    <div v-else-if="loading && total === 0" class="easy-card skeleton-block">正在加载…</div>

    <div v-else-if="total === 0" class="easy-card">
      <EasyEmpty title="没有记录" :description="emptyText" />
    </div>

    <template v-else>
      <ul v-if="tab === 'audit'" class="record-list">
        <li v-for="item in auditItems" :key="item.id" class="record-row" @click="openAudit(item)">
          <span class="record-row__time easy-mono">{{ formatDateTime(item.createdAt) }}</span>
          <span class="record-row__actor">{{ item.actorUsername }}</span>
          <span class="record-row__action">
            {{ auditActionLabel(item.action) }}
            <code class="easy-mono record-row__code">{{ item.action }}</code>
          </span>
          <span class="record-row__target easy-mono">
            {{ item.resourceType ? `${item.resourceType}#${item.resourceId ?? '-'}` : '—' }}
          </span>
          <span class="record-row__reason">{{ item.reason ?? '—' }}</span>
          <EasyStatus :label="riskLevelLabel(item.riskLevel)" :tone="riskLevelTone(item.riskLevel)" />
        </li>
      </ul>

      <ul v-else class="record-list">
        <li v-for="item in eventItems" :key="item.id" class="record-row" @click="openEvent(item)">
          <span class="record-row__time easy-mono">{{ formatDateTime(item.createdAt) }}</span>
          <span class="record-row__action">{{ securityEventTypeLabel(item.eventType) }}</span>
          <span class="record-row__target">{{ item.description }}</span>
          <span class="record-row__actor">{{ item.actorUsername ?? 'system' }}</span>
          <span class="record-row__reason easy-mono">{{ item.ipAddress ?? '—' }}</span>
          <EasyStatus :label="severityLabel(item.severity)" :tone="severityTone(item.severity)" />
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

    <EasyDrawer v-model="detailOpen" :title="selectedEvent ? '安全事件详情' : '审计记录详情'" :size="520">
      <div v-if="selectedAudit" class="detail">
        <dl class="detail__grid">
          <div><dt>时间</dt><dd>{{ formatDateTime(selectedAudit.createdAt) }}</dd></div>
          <div><dt>操作者</dt><dd>{{ selectedAudit.actorUsername }}</dd></div>
          <div><dt>动作</dt><dd>{{ auditActionLabel(selectedAudit.action) }}</dd></div>
          <div><dt>风险级别</dt><dd>{{ riskLevelLabel(selectedAudit.riskLevel) }}</dd></div>
          <div><dt>资源</dt><dd class="easy-mono">{{ selectedAudit.resourceType ?? '—' }} / {{ selectedAudit.resourceId ?? '—' }}</dd></div>
          <div><dt>客户端 IP</dt><dd class="easy-mono">{{ selectedAudit.ipAddress ?? '—' }}</dd></div>
          <div><dt>请求 ID</dt><dd class="easy-mono">{{ selectedAudit.requestId ?? '—' }}</dd></div>
          <div class="detail__grid-full"><dt>原因</dt><dd>{{ selectedAudit.reason ?? '—' }}</dd></div>
        </dl>

        <section class="detail__section">
          <h3>变更前</h3>
          <pre class="easy-mono detail__json">{{ prettyJson(selectedAudit.beforeData) }}</pre>
        </section>
        <section class="detail__section">
          <h3>变更后</h3>
          <pre class="easy-mono detail__json">{{ prettyJson(selectedAudit.afterData) }}</pre>
        </section>
      </div>

      <div v-else-if="selectedEvent" class="detail">
        <dl class="detail__grid">
          <div><dt>时间</dt><dd>{{ formatDateTime(selectedEvent.createdAt) }}</dd></div>
          <div><dt>类型</dt><dd>{{ securityEventTypeLabel(selectedEvent.eventType) }}</dd></div>
          <div><dt>级别</dt><dd>{{ severityLabel(selectedEvent.severity) }}</dd></div>
          <div><dt>操作者</dt><dd>{{ selectedEvent.actorUsername ?? 'system' }}</dd></div>
          <div><dt>客户端 IP</dt><dd class="easy-mono">{{ selectedEvent.ipAddress ?? '—' }}</dd></div>
          <div><dt>请求 ID</dt><dd class="easy-mono">{{ selectedEvent.requestId ?? '—' }}</dd></div>
          <div class="detail__grid-full"><dt>说明</dt><dd>{{ selectedEvent.description }}</dd></div>
        </dl>

        <section class="detail__section">
          <h3>事件数据</h3>
          <pre class="easy-mono detail__json">{{ prettyJson(selectedEvent.detail) }}</pre>
        </section>
        <section class="detail__section">
          <h3>哈希链</h3>
          <pre class="easy-mono detail__json">previousHash: {{ selectedEvent.previousHash ?? '—' }}
entryHash:    {{ selectedEvent.entryHash ?? '—' }}</pre>
        </section>
      </div>
    </EasyDrawer>
  </div>
</template>

<style scoped>
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

.toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--easy-space-3);
  flex-wrap: wrap;
}

.toolbar__filters {
  display: flex;
  align-items: center;
  gap: var(--easy-space-2);
  flex-wrap: wrap;
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

.text-filter {
  height: 32px;
  min-width: 240px;
  padding: 0 var(--easy-space-3);
  border: 1px solid var(--easy-border-strong);
  border-radius: var(--easy-radius-md);
  background: var(--easy-surface);
  color: var(--easy-text-1);
  font-size: var(--easy-text-sm);
}

.filter-select {
  width: 130px;
}

.filter-select--wide {
  width: 180px;
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

.record-list {
  display: flex;
  flex-direction: column;
  border: 1px solid var(--easy-border);
  border-radius: var(--easy-radius-lg);
  background: var(--easy-surface);
  overflow: hidden;
}

.record-row {
  display: grid;
  grid-template-columns: 150px 120px minmax(180px, 1fr) minmax(140px, 1fr) minmax(120px, 1fr) 88px;
  align-items: center;
  gap: var(--easy-space-3);
  padding: var(--easy-space-3) var(--easy-space-4);
  border-bottom: 1px solid var(--easy-border);
  font-size: var(--easy-text-xs);
  color: var(--easy-text-2);
  cursor: pointer;
  transition: background var(--easy-transition-fast);
}

.record-row:last-child {
  border-bottom: none;
}

.record-row:hover {
  background: var(--easy-surface-hover);
}

.record-row__time {
  color: var(--easy-text-3);
}

.record-row__actor {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.record-row__action {
  display: flex;
  align-items: center;
  gap: var(--easy-space-2);
  color: var(--easy-text-1);
  font-weight: 500;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.record-row__code {
  flex: none;
  padding: 1px 6px;
  border-radius: var(--easy-radius-xs);
  background: var(--easy-surface-sunken);
  color: var(--easy-text-3);
  font-size: 10px;
}

.record-row__target,
.record-row__reason {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.pagination {
  display: flex;
  justify-content: center;
}

.detail {
  display: flex;
  flex-direction: column;
  gap: var(--easy-space-5);
}

.detail__grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: var(--easy-space-3);
}

.detail__grid-full {
  grid-column: 1 / -1;
}

.detail__grid dt {
  color: var(--easy-text-3);
  font-size: var(--easy-text-xs);
}

.detail__grid dd {
  margin-top: 2px;
  color: var(--easy-text-1);
  font-size: var(--easy-text-sm);
  word-break: break-all;
}

.detail__section h3 {
  margin-bottom: var(--easy-space-2);
  font-size: var(--easy-text-sm);
  font-weight: 600;
  color: var(--easy-text-2);
}

.detail__json {
  max-height: 320px;
  overflow: auto;
  padding: var(--easy-space-3);
  border: 1px solid var(--easy-border);
  border-radius: var(--easy-radius-md);
  background: var(--easy-surface-sunken);
  color: var(--easy-text-2);
  font-size: var(--easy-text-xs);
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-all;
}
</style>
