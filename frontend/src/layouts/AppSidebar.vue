<script setup lang="ts">
import { computed } from 'vue'
import type { Component } from 'vue'
import { useRouter } from 'vue-router'
import {
  DataAnalysis,
  Document,
  Folder,
  HomeFilled,
  Lock,
  OfficeBuilding,
  Setting,
  Stamp,
  Tickets,
  User,
} from '@element-plus/icons-vue'

import { useAuthStore } from '@/stores/auth'
import { useUiStore } from '@/stores/ui'

/**
 * 左侧导航。
 *
 * 设计约束（产品规范）：简洁、圆角、active 使用浅绿色背景、图标克制、
 * 不做巨大侧栏、不出现几十个二级菜单。
 *
 * 权限来源：菜单项只声明路由 name，可见性统一读取路由 meta（title / adminOnly），
 * 避免在导航里重复定义路径、标题与权限映射（单一事实来源 = router）。
 */
interface NavItem {
  name: string
  label: string
  icon: Component
}

interface NavGroup {
  label: string
  items: NavItem[]
}

const auth = useAuthStore()
const ui = useUiStore()
const router = useRouter()

const groups: NavGroup[] = [
  {
    label: '协作',
    items: [
      { name: 'workspace', label: '工作台', icon: HomeFilled },
      { name: 'projects', label: '项目', icon: Folder },
      { name: 'my-tasks', label: '我的任务', icon: Tickets },
      { name: 'approvals', label: '审批', icon: Stamp },
    ],
  },
  {
    label: '组织',
    items: [
      { name: 'team', label: '团队', icon: User },
      { name: 'organization', label: '组织架构', icon: OfficeBuilding },
    ],
  },
  {
    label: '洞察',
    items: [{ name: 'insights', label: '数据中心', icon: DataAnalysis }],
  },
  {
    label: '系统',
    items: [
      { name: 'security', label: '安全中心', icon: Lock },
      { name: 'audit-logs', label: '审计日志', icon: Document },
      { name: 'settings', label: '系统设置', icon: Setting },
    ],
  },
]

/** 路由 meta 声明的管理员专属页面 */
function requiresAdmin(name: string): boolean {
  return router.resolve({ name }).meta.adminOnly === true
}

/**
 * 无权限的导航项不渲染（仅体验层面，真实权限由后端判定；
 * 路由守卫亦会拦截直接访问）。
 */
const visibleGroups = computed(() =>
  groups
    .map((group) => ({
      ...group,
      items: group.items.filter((item) => !requiresAdmin(item.name) || auth.isAdminLike),
    }))
    .filter((group) => group.items.length > 0),
)
</script>

<template>
  <aside class="sidebar" :class="{ 'sidebar--collapsed': ui.sidebarCollapsed }">
    <div class="sidebar__brand">
      <svg class="sidebar__logo" viewBox="0 0 32 32" fill="none" aria-hidden="true">
        <rect width="32" height="32" rx="9" fill="var(--easy-brand)" />
        <path
          d="M9.5 16.6l4.2 4.2 8.8-9.4"
          stroke="#fff"
          stroke-width="2.6"
          stroke-linecap="round"
          stroke-linejoin="round"
        />
      </svg>
      <Transition name="brand">
        <div v-if="!ui.sidebarCollapsed" class="sidebar__brand-text">
          <span class="sidebar__name">EasyOA</span>
          <span class="sidebar__tagline">企业协同办公平台</span>
        </div>
      </Transition>
    </div>

    <nav class="sidebar__nav">
      <div v-for="group in visibleGroups" :key="group.label" class="sidebar__group">
        <div v-if="!ui.sidebarCollapsed" class="sidebar__group-label">{{ group.label }}</div>
        <router-link
          v-for="item in group.items"
          :key="item.name"
          class="sidebar__item"
          :class="{ 'is-active': $route.name === item.name }"
          :to="{ name: item.name }"
          :title="ui.sidebarCollapsed ? item.label : undefined"
        >
          <el-icon class="sidebar__item-icon"><component :is="item.icon" /></el-icon>
          <Transition name="brand">
            <span v-if="!ui.sidebarCollapsed" class="sidebar__item-label">{{ item.label }}</span>
          </Transition>
        </router-link>
      </div>
    </nav>

    <div class="sidebar__footer">
      <button
        type="button"
        class="sidebar__collapse"
        :title="ui.sidebarCollapsed ? '展开侧边栏' : '折叠侧边栏'"
        @click="ui.toggleSidebar()"
      >
        <svg viewBox="0 0 16 16" fill="none" aria-hidden="true" class="sidebar__collapse-icon">
          <path
            :d="ui.sidebarCollapsed ? 'M6 3l5 5-5 5' : 'M10 3L5 8l5 5'"
            stroke="currentColor"
            stroke-width="1.6"
            stroke-linecap="round"
            stroke-linejoin="round"
          />
        </svg>
        <span v-if="!ui.sidebarCollapsed" class="sidebar__collapse-label">折叠</span>
      </button>
    </div>
  </aside>
</template>

<style scoped>
.sidebar {
  display: flex;
  flex-direction: column;
  width: var(--easy-sidebar-width);
  flex: none;
  background: var(--easy-surface);
  border-right: 1px solid var(--easy-border);
  transition: width var(--easy-transition-base);
}

.sidebar--collapsed {
  width: var(--easy-sidebar-width-collapsed);
}

.sidebar__brand {
  display: flex;
  align-items: center;
  gap: var(--easy-space-3);
  height: var(--easy-topbar-height);
  padding: 0 var(--easy-space-4);
  border-bottom: 1px solid var(--easy-border);
  overflow: hidden;
}

.sidebar__logo {
  width: 28px;
  height: 28px;
  flex: none;
}

.sidebar__brand-text {
  display: flex;
  flex-direction: column;
  line-height: 1.2;
  white-space: nowrap;
}

.sidebar__name {
  font-size: var(--easy-text-md);
  font-weight: 700;
  letter-spacing: -0.01em;
}

.sidebar__tagline {
  font-size: 11px;
  color: var(--easy-text-3);
}

.sidebar__nav {
  flex: 1;
  /* 必须显式 min-height: 0：否则 flex 项默认 min-height:auto，
     内容超高时不会收缩，会把导航区撑到页脚下方导致末项被遮挡 */
  min-height: 0;
  overflow-y: auto;
  padding: var(--easy-space-4) var(--easy-space-3);
  display: flex;
  flex-direction: column;
  gap: var(--easy-space-5);
}

.sidebar__group {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.sidebar__group-label {
  padding: 0 var(--easy-space-3) var(--easy-space-2);
  font-size: 11px;
  font-weight: 600;
  letter-spacing: 0.06em;
  text-transform: uppercase;
  color: var(--easy-text-3);
}

.sidebar__item {
  display: flex;
  align-items: center;
  gap: var(--easy-space-3);
  height: 36px;
  padding: 0 var(--easy-space-3);
  border-radius: var(--easy-radius-md);
  color: var(--easy-text-2);
  font-size: var(--easy-text-base);
  white-space: nowrap;
  transition: background var(--easy-transition-fast), color var(--easy-transition-fast);
}

.sidebar__item:hover {
  background: var(--easy-surface-hover);
  color: var(--easy-text-1);
}

.sidebar__item.is-active {
  background: var(--easy-brand-subtle);
  color: var(--easy-brand-text);
  font-weight: 600;
}

.sidebar__item-icon {
  font-size: 17px;
  flex: none;
}

.sidebar__footer {
  padding: var(--easy-space-3);
  border-top: 1px solid var(--easy-border);
}

.sidebar__collapse {
  display: flex;
  align-items: center;
  gap: var(--easy-space-2);
  width: 100%;
  height: 32px;
  padding: 0 var(--easy-space-3);
  border: none;
  border-radius: var(--easy-radius-md);
  background: transparent;
  color: var(--easy-text-3);
  font-size: var(--easy-text-sm);
  cursor: pointer;
  transition: background var(--easy-transition-fast), color var(--easy-transition-fast);
}

.sidebar__collapse:hover {
  background: var(--easy-surface-hover);
  color: var(--easy-text-1);
}

.sidebar__collapse-icon {
  width: 16px;
  height: 16px;
  flex: none;
}

.brand-enter-active,
.brand-leave-active {
  transition: opacity var(--easy-transition-fast);
}

.brand-enter-from,
.brand-leave-to {
  opacity: 0;
}
</style>