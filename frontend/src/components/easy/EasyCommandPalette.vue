<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import type { Component } from 'vue'
import { useRouter } from 'vue-router'
import {
  CirclePlus,
  DataAnalysis,
  Document,
  Folder,
  HomeFilled,
  OfficeBuilding,
  Search,
  Setting,
  Stamp,
  SwitchButton,
  Tickets,
  User,
} from '@element-plus/icons-vue'

import { searchApi } from '@/api/modules/search'
import type { SearchHit } from '@/api/types'
import { useAuthStore } from '@/stores/auth'
import { useNotificationStore } from '@/stores/notification'
import { useUiStore } from '@/stores/ui'

/**
 * EasyCommandPalette — 全局命令面板（⌘K / Ctrl+K）与全局搜索。
 *
 * Phase 7：支持跨模块检索（项目 / 任务 / 成员 / 审批，结果按类别分组并直达内容）
 * 与快捷动作（创建任务 / 创建项目 / 发起审批）；顶部搜索框复用同一面板。
 */
interface CommandItem {
  id: string
  title: string
  group: string
  hint?: string
  icon: Component
  keywords: string[]
  run: () => void
}

const router = useRouter()
const auth = useAuthStore()
const notification = useNotificationStore()
const ui = useUiStore()

const query = ref('')
const activeIndex = ref(0)
const inputRef = ref<HTMLInputElement | null>(null)
const listRef = ref<HTMLElement | null>(null)
const searchResults = ref<SearchHit[]>([])
const searching = ref(false)

let searchTimer: number | null = null

const isMac = computed(() => /Mac|iPhone|iPad/.test(navigator.userAgent))

const commands = computed<CommandItem[]>(() => {
  const items: CommandItem[] = [
    {
      id: 'nav-workspace',
      title: '工作台',
      group: '导航',
      icon: HomeFilled,
      keywords: ['home', 'dashboard', 'gongzuotai'],
      run: () => go('workspace'),
    },
    {
      id: 'nav-projects',
      title: '项目',
      group: '导航',
      icon: Folder,
      keywords: ['projects', 'xiangmu'],
      run: () => go('projects'),
    },
    {
      id: 'nav-my-tasks',
      title: '我的任务',
      group: '导航',
      icon: Tickets,
      keywords: ['tasks', 'wodetask'],
      run: () => go('my-tasks'),
    },
    {
      id: 'nav-approvals',
      title: '审批',
      group: '导航',
      icon: Stamp,
      keywords: ['approval', 'shenpi'],
      run: () => go('approvals'),
    },
    {
      id: 'nav-team',
      title: '团队',
      group: '导航',
      icon: User,
      keywords: ['team', 'members', 'tuandui'],
      run: () => go('team'),
    },
    {
      id: 'nav-organization',
      title: '组织架构',
      group: '导航',
      icon: OfficeBuilding,
      keywords: ['organization', 'org', 'zuzhi'],
      run: () => go('organization'),
    },
    {
      id: 'nav-insights',
      title: '数据中心',
      group: '导航',
      icon: DataAnalysis,
      keywords: ['insights', 'data', 'shuju'],
      run: () => go('insights'),
    },
  ]

  if (auth.isAdminLike) {
    items.push(
      {
        id: 'nav-audit-logs',
        title: '审计日志',
        group: '导航',
        icon: Document,
        keywords: ['audit', 'shenji', 'logs'],
        run: () => go('audit-logs'),
      },
      {
        id: 'nav-settings',
        title: '系统设置',
        group: '导航',
        icon: Setting,
        keywords: ['settings', 'shezhi'],
        run: () => go('settings'),
      },
    )
  }

  items.push(
      {
        id: 'action-create-project',
        title: '创建项目',
        group: '快捷动作',
        icon: CirclePlus,
        keywords: ['create', 'project', 'chuangjianxiangmu'],
        run: () => go('projects', { create: '1' }),
      },
      {
        id: 'action-create-approval',
        title: '发起审批',
        group: '快捷动作',
        icon: Stamp,
        keywords: ['approval', 'create', 'faqishenpi'],
        run: () => go('approvals', { create: '1' }),
      },
      {
        id: 'action-create-task',
        title: '创建任务',
        group: '快捷动作',
        icon: Tickets,
        keywords: ['task', 'create', 'chuangjianrenwu'],
        run: () => go('projects', { create: 'task' }),
      },
    )

    items.push({
      id: 'action-logout',
      title: '退出登录',
      group: '操作',
      icon: SwitchButton,
      keywords: ['logout', 'exit', 'tuichu'],
      run: () => void handleLogout(),
    })

    return items
  })

  /** 搜索命中 → 可执行命令（按类别分组，点击直达内容）。 */
  const searchCommands = computed<CommandItem[]>(() => {
    const groups: Array<{ group: string; icon: Component; hits: SearchHit[] }> = [
      { group: '项目', icon: Folder, hits: searchResults.value.filter((hit) => hit.link.startsWith('/projects/') && !hit.link.includes('?')) },
      { group: '任务', icon: Tickets, hits: searchResults.value.filter((hit) => hit.link.includes('?task=')) },
      { group: '成员', icon: User, hits: searchResults.value.filter((hit) => hit.link === '/team') },
      { group: '审批', icon: Stamp, hits: searchResults.value.filter((hit) => hit.link.startsWith('/approvals/')) },
    ]
    return groups.flatMap((entry) =>
      entry.hits.map((hit) => ({
        id: `search-${entry.group}-${hit.id}`,
        title: hit.title,
        group: `搜索 · ${entry.group}`,
        hint: hit.subtitle ?? undefined,
        icon: entry.icon,
        keywords: [],
        run: () => {
          ui.closeCommandPalette()
          void router.push(hit.link)
        },
      })),
    )
  })

  const filtered = computed(() => {
    const keyword = query.value.trim().toLowerCase()
    if (!keyword) return commands.value
    const matchedCommands = commands.value.filter(
      (item) =>
        item.title.toLowerCase().includes(keyword) ||
        item.group.includes(keyword) ||
        item.keywords.some((word) => word.includes(keyword)),
    )
    return [...searchCommands.value, ...matchedCommands]
  })

  const groupedItems = computed(() => {
    const groups: Array<{ label: string; items: Array<{ item: CommandItem; index: number }> }> = []
    filtered.value.forEach((item, index) => {
      let group = groups.find((candidate) => candidate.label === item.group)
      if (!group) {
        group = { label: item.group, items: [] }
        groups.push(group)
      }
      group.items.push({ item, index })
    })
    return groups
  })

  watch(query, (value) => {
    activeIndex.value = 0
    if (searchTimer !== null) {
      window.clearTimeout(searchTimer)
    }
    const keyword = value.trim()
    if (keyword.length === 0) {
      searchResults.value = []
      searching.value = false
      return
    }
    searching.value = true
    searchTimer = window.setTimeout(async () => {
      try {
        const result = await searchApi.search(keyword)
        searchResults.value = [
          ...result.projects,
          ...result.tasks,
          ...result.users,
          ...result.approvals,
        ]
      } catch {
        searchResults.value = []
      } finally {
        searching.value = false
      }
    }, 250)
  })

watch(filtered, () => {
  activeIndex.value = 0
})

watch(
  () => ui.commandPaletteOpen,
  (open) => {
    if (open) {
      query.value = ''
      activeIndex.value = 0
      void nextTick(() => inputRef.value?.focus())
    }
  },
)

watch(activeIndex, () => {
  void nextTick(() => {
    listRef.value?.querySelector('[data-active="true"]')?.scrollIntoView({ block: 'nearest' })
  })
})

function go(name: string, query?: Record<string, string>): void {
  ui.closeCommandPalette()
  void router.push({ name, query })
}

async function handleLogout(): Promise<void> {
  ui.closeCommandPalette()
  await auth.logout()
  notification.success('已退出登录')
  void router.replace({ name: 'login' })
}

function onKeydown(event: KeyboardEvent): void {
  const total = filtered.value.length
  if (event.key === 'ArrowDown') {
    event.preventDefault()
    if (total > 0) activeIndex.value = (activeIndex.value + 1) % total
  } else if (event.key === 'ArrowUp') {
    event.preventDefault()
    if (total > 0) activeIndex.value = (activeIndex.value - 1 + total) % total
  } else if (event.key === 'Enter') {
    event.preventDefault()
    filtered.value[activeIndex.value]?.run()
  } else if (event.key === 'Escape') {
    event.preventDefault()
    ui.closeCommandPalette()
  }
}

function onGlobalKeydown(event: KeyboardEvent): void {
  if ((event.metaKey || event.ctrlKey) && event.key.toLowerCase() === 'k') {
    event.preventDefault()
    if (ui.commandPaletteOpen) {
      ui.closeCommandPalette()
    } else {
      ui.openCommandPalette()
    }
  }
}

onMounted(() => window.addEventListener('keydown', onGlobalKeydown))
onBeforeUnmount(() => window.removeEventListener('keydown', onGlobalKeydown))
</script>

<template>
  <Teleport to="body">
    <Transition name="palette">
      <div v-if="ui.commandPaletteOpen" class="palette-mask" @mousedown.self="ui.closeCommandPalette()">
        <div class="palette" role="dialog" aria-modal="true" aria-label="命令面板">
          <div class="palette__search">
            <el-icon class="palette__search-icon"><Search /></el-icon>
            <input
              ref="inputRef"
              v-model="query"
              class="palette__input"
              placeholder="搜索页面 / 动作，或输入关键字搜索项目、任务、成员、审批…"
              autocomplete="off"
              spellcheck="false"
              @keydown="onKeydown"
            />
            <span class="palette__kbd">ESC</span>
          </div>

          <div ref="listRef" class="palette__list">
            <template v-for="group in groupedItems" :key="group.label">
              <div class="palette__group-label">{{ group.label }}</div>
              <button
                v-for="entry in group.items"
                :key="entry.item.id"
                type="button"
                class="palette__item"
                :class="{ 'is-active': entry.index === activeIndex }"
                :data-active="entry.index === activeIndex"
                @mousemove="activeIndex = entry.index"
                @click="entry.item.run()"
              >
                <el-icon class="palette__item-icon"><component :is="entry.item.icon" /></el-icon>
                <span class="palette__item-body">
                  <span class="palette__item-title">{{ entry.item.title }}</span>
                  <span v-if="entry.item.hint" class="palette__item-hint">{{ entry.item.hint }}</span>
                </span>
              </button>
            </template>

            <div v-if="filtered.length === 0" class="palette__empty">
              {{ searching ? '搜索中…' : '没有匹配的结果' }}
            </div>
          </div>

          <div class="palette__footer">
            <span class="palette__footer-key">↑↓ 选择</span>
            <span class="palette__footer-key">↵ 执行</span>
            <span class="palette__footer-note">搜索：项目 / 任务 / 成员 / 审批（点击直达内容）</span>
            <span class="palette__footer-key palette__footer-key--right">{{ isMac ? '⌘K' : 'Ctrl K' }}</span>
          </div>
        </div>
      </div>
    </Transition>
  </Teleport>
</template>

<style scoped>
.palette-mask {
  position: fixed;
  inset: 0;
  z-index: var(--easy-z-palette);
  display: flex;
  justify-content: center;
  align-items: flex-start;
  padding-top: 16vh;
  background: rgba(15, 23, 42, 0.32);
  backdrop-filter: blur(2px);
}

.palette {
  width: min(560px, calc(100vw - 48px));
  max-height: 62vh;
  display: flex;
  flex-direction: column;
  background: var(--easy-surface);
  border: 1px solid var(--easy-border);
  border-radius: var(--easy-radius-xl);
  box-shadow: var(--easy-shadow-pop);
  overflow: hidden;
}

.palette__search {
  display: flex;
  align-items: center;
  gap: var(--easy-space-3);
  padding: var(--easy-space-4) var(--easy-space-5);
  border-bottom: 1px solid var(--easy-border);
}

.palette__search-icon {
  color: var(--easy-text-3);
  font-size: 16px;
}

.palette__input {
  flex: 1;
  border: none;
  outline: none;
  background: transparent;
  font-size: var(--easy-text-md);
  color: var(--easy-text-1);
}

.palette__input::placeholder {
  color: var(--easy-text-3);
}

.palette__kbd {
  padding: 2px 6px;
  border: 1px solid var(--easy-border-strong);
  border-radius: var(--easy-radius-xs);
  font-size: 10px;
  color: var(--easy-text-3);
  letter-spacing: 0.04em;
}

.palette__list {
  flex: 1;
  overflow-y: auto;
  padding: var(--easy-space-2);
}

.palette__group-label {
  padding: var(--easy-space-3) var(--easy-space-3) var(--easy-space-1);
  font-size: 11px;
  font-weight: 600;
  letter-spacing: 0.06em;
  text-transform: uppercase;
  color: var(--easy-text-3);
}

.palette__item {
  display: flex;
  align-items: center;
  gap: var(--easy-space-3);
  width: 100%;
  padding: 9px var(--easy-space-3);
  border: none;
  border-radius: var(--easy-radius-md);
  background: transparent;
  color: var(--easy-text-1);
  font-size: var(--easy-text-base);
  text-align: left;
  cursor: pointer;
  transition: background var(--easy-transition-fast);
}

.palette__item.is-active {
  background: var(--easy-brand-subtle);
  color: var(--easy-brand-text);
}

.palette__item-icon {
  font-size: 16px;
  color: inherit;
}

.palette__item-body {
  display: flex;
  flex-direction: column;
  gap: 1px;
  min-width: 0;
}

.palette__item-hint {
  font-size: var(--easy-text-xs);
  color: var(--easy-text-3);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.palette__empty {
  padding: var(--easy-space-8) 0;
  text-align: center;
  color: var(--easy-text-3);
  font-size: var(--easy-text-sm);
}

.palette__footer {
  display: flex;
  align-items: center;
  gap: var(--easy-space-4);
  padding: var(--easy-space-3) var(--easy-space-5);
  border-top: 1px solid var(--easy-border);
  background: var(--easy-surface-sunken);
  font-size: var(--easy-text-xs);
  color: var(--easy-text-3);
}

.palette__footer-key {
  font-family: var(--easy-font-mono);
}

.palette__footer-key--right {
  margin-left: auto;
}

.palette__footer-note {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.palette-enter-active,
.palette-leave-active {
  transition: opacity var(--easy-transition-base);
}

.palette-enter-active .palette,
.palette-leave-active .palette {
  transition: transform var(--easy-transition-base), opacity var(--easy-transition-base);
}

.palette-enter-from,
.palette-leave-to {
  opacity: 0;
}

.palette-enter-from .palette,
.palette-leave-to .palette {
  transform: translateY(-8px) scale(0.99);
  opacity: 0;
}
</style>