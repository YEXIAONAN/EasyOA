<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { Bell } from '@element-plus/icons-vue'

import { notificationApi } from '@/api/modules/notifications'
import type { NotificationItem } from '@/api/types'
import EasyButton from '@/components/easy/EasyButton.vue'
import { useNotificationStore } from '@/stores/notification'
import { formatRelative } from '@/utils/format'

/**
 * 通知铃铛：未读数 + 最近通知列表（Deep Link 直达任务 / 审批 / 项目）。
 *
 * 每 60 秒轮询未读数；点击通知标记已读并跳转到对应内容，不跳回首页。
 *
 * 注意：这里不使用 el-popover 的受控 `visible`（受控模式会与 trigger="click"
 * 的内部状态冲突，导致浮层无法展开），改用 @show / @hide 事件驱动数据加载。
 */
const POLL_INTERVAL_MS = 60_000
const PREVIEW_SIZE = 8

const router = useRouter()
const notification = useNotificationStore()

const items = ref<NotificationItem[]>([])
const loading = ref(false)

let timer: number | null = null

async function refresh(): Promise<void> {
  await notification.refreshUnread()
  try {
    const result = await notificationApi.list({ size: PREVIEW_SIZE })
    items.value = result.items
  } catch {
    // 列表加载失败不阻塞铃铛（未读数独立展示）
  }
}

async function onOpen(): Promise<void> {
  loading.value = true
  await refresh()
  loading.value = false
}

async function openItem(item: NotificationItem): Promise<void> {
  try {
    if (!item.read) {
      await notificationApi.markRead(item.id)
      item.read = true
      await notification.refreshUnread()
    }
  } catch {
    // 已读失败不阻塞跳转
  }
  void router.push(item.link)
}

async function markAll(): Promise<void> {
  await notificationApi.markAllRead()
  items.value = items.value.map((item) => ({ ...item, read: true }))
  await notification.refreshUnread()
}

onMounted(async () => {
  await refresh()
  timer = window.setInterval(() => void refresh(), POLL_INTERVAL_MS)
})

onBeforeUnmount(() => {
  if (timer !== null) {
    window.clearInterval(timer)
  }
})
</script>

<template>
  <el-popover
    placement="bottom-end"
    :width="340"
    trigger="click"
    popper-class="easy-popover"
    @show="onOpen"
  >
    <template #reference>
      <button type="button" class="topbar__icon-button" aria-label="通知中心">
        <el-icon><Bell /></el-icon>
        <span v-if="notification.unreadCount > 0" class="topbar__badge">
          {{ notification.unreadCount > 99 ? '99+' : notification.unreadCount }}
        </span>
      </button>
    </template>

    <div class="notification-panel">
      <div class="notification-panel__head">
        <span class="notification-panel__title">通知</span>
        <EasyButton v-if="items.some((item) => !item.read)" size="sm" @click="markAll">全部已读</EasyButton>
      </div>

      <p v-if="loading && items.length === 0" class="notification-panel__empty">加载中…</p>
      <p v-else-if="items.length === 0" class="notification-panel__empty">
        暂无通知<br />
        <span class="easy-text-xs">任务分配、审批待办、评论 @ 等通知会在这里汇总，点击可直达对应内容。</span>
      </p>
      <ul v-else class="notification-panel__list">
        <li
          v-for="item in items"
          :key="item.id"
          class="notification-item"
          :class="{ 'notification-item--unread': !item.read }"
          @click="openItem(item)"
        >
          <div class="notification-item__head">
            <span class="notification-item__title">{{ item.title }}</span>
            <span class="notification-item__time">{{ formatRelative(item.createdAt) }}</span>
          </div>
          <p v-if="item.body" class="notification-item__body">{{ item.body }}</p>
        </li>
      </ul>
    </div>
  </el-popover>
</template>

<style scoped>
.topbar__icon-button {
  position: relative;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 34px;
  height: 34px;
  border: none;
  border-radius: var(--easy-radius-md);
  background: transparent;
  color: var(--easy-text-2);
  font-size: 17px;
  cursor: pointer;
}

.topbar__icon-button:hover {
  background: var(--easy-surface-hover);
  color: var(--easy-text-1);
}

.topbar__badge {
  position: absolute;
  top: 3px;
  right: 3px;
  min-width: 15px;
  height: 15px;
  padding: 0 4px;
  border-radius: var(--easy-radius-full);
  background: var(--easy-danger);
  color: var(--easy-text-inverse);
  font-size: 10px;
  line-height: 15px;
  text-align: center;
}

.notification-panel {
  display: flex;
  flex-direction: column;
  gap: var(--easy-space-2);
}

.notification-panel__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding-bottom: var(--easy-space-2);
  border-bottom: 1px solid var(--easy-border);
}

.notification-panel__title {
  font-size: var(--easy-text-sm);
  font-weight: 600;
}

.notification-panel__empty {
  padding: var(--easy-space-4) 0;
  text-align: center;
  font-size: var(--easy-text-sm);
  color: var(--easy-text-3);
  line-height: var(--easy-leading-relaxed);
}

.notification-panel__list {
  display: flex;
  flex-direction: column;
  max-height: 380px;
  overflow-y: auto;
}

.notification-item {
  display: flex;
  flex-direction: column;
  gap: 2px;
  padding: var(--easy-space-2) var(--easy-space-2);
  border-radius: var(--easy-radius-md);
  cursor: pointer;
  transition: background var(--easy-transition-fast);
}

.notification-item:hover {
  background: var(--easy-surface-hover);
}

.notification-item__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--easy-space-2);
}

.notification-item__title {
  font-size: var(--easy-text-sm);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.notification-item--unread .notification-item__title {
  font-weight: 600;
}

.notification-item--unread .notification-item__title::before {
  content: '';
  display: inline-block;
  width: 6px;
  height: 6px;
  margin-right: 6px;
  border-radius: 50%;
  background: var(--easy-brand);
  vertical-align: middle;
}

.notification-item__time {
  flex: none;
  font-size: var(--easy-text-xs);
  color: var(--easy-text-3);
}

.notification-item__body {
  font-size: var(--easy-text-xs);
  color: var(--easy-text-3);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
</style>