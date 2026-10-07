import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'

import AppLayout from '@/layouts/AppLayout.vue'
import { useAuthStore } from '@/stores/auth'

/**
 * 路由表。
 *
 * meta 约定：
 *   - requiresAuth：需要登录（默认全部业务路由需要）
 *   - adminOnly：仅 ROOT / ADMIN 可见（前端只做体验控制，真实权限在后端）
 *   - title / phase / summary：占位页展示的模块信息
 */
const routes: RouteRecordRaw[] = [
  {
    path: '/login',
    name: 'login',
    component: () => import('@/views/LoginView.vue'),
    meta: { public: true, title: '登录' },
  },
  {
    path: '/setup',
    name: 'setup',
    component: () => import('@/views/SetupView.vue'),
    meta: { public: true, title: '初始化 EasyOA' },
  },
  {
    path: '/',
    component: AppLayout,
    children: [
      {
        path: '',
        name: 'workspace',
        component: () => import('@/views/WorkspaceView.vue'),
        meta: { title: '工作台' },
      },
      {
        path: 'projects',
        name: 'projects',
        component: () => import('@/views/ProjectsView.vue'),
        meta: { title: '项目' },
      },
      {
        path: 'projects/:id(\\d+)',
        name: 'project-detail',
        component: () => import('@/views/ProjectDetailView.vue'),
        meta: { title: '项目详情' },
      },
      {
        // 看板使用独立路径（/projects/12/board?task=86），支持任务深链与刷新保持
        path: 'projects/:id(\\d+)/board',
        name: 'project-board',
        component: () => import('@/views/ProjectDetailView.vue'),
        meta: { title: '项目看板' },
      },
      {
        path: 'my-tasks',
        name: 'my-tasks',
        component: () => import('@/views/MyTasksView.vue'),
        meta: { title: '我的任务' },
      },
      {
        path: 'approvals',
        name: 'approvals',
        component: () => import('@/views/ApprovalsView.vue'),
        meta: { title: '审批' },
      },
      {
        path: 'approvals/:id(\\d+)',
        name: 'approval-detail',
        component: () => import('@/views/ApprovalDetailView.vue'),
        meta: { title: '审批详情' },
      },
      {
        path: 'team',
        name: 'team',
        component: () => import('@/views/TeamView.vue'),
        meta: { title: '团队' },
      },
      {
        path: 'organization',
        name: 'organization',
        component: () => import('@/views/OrganizationView.vue'),
        meta: { title: '组织架构' },
      },
      {
        path: 'insights',
        name: 'insights',
        component: () => import('@/views/ModulePlaceholderView.vue'),
        meta: {
          title: '数据中心',
          phase: 'Phase 9',
          summary: '项目健康度、任务趋势、逾期与负载分析将在 Phase 9 交付。',
        },
      },
      {
        path: 'audit-logs',
        name: 'audit-logs',
        component: () => import('@/views/AuditLogsView.vue'),
        meta: { title: '审计日志', adminOnly: true },
      },
      {
        path: 'settings',
        name: 'settings',
        component: () => import('@/views/SecuritySettingsView.vue'),
        meta: { title: '系统设置', adminOnly: true },
      },
      {
        path: 'security',
        name: 'security',
        component: () => import('@/views/SecurityCenterView.vue'),
        meta: { title: '安全中心' },
      },
    ],
  },
  {
    path: '/403',
    name: 'forbidden',
    component: () => import('@/views/ForbiddenView.vue'),
    meta: { public: true, title: '无访问权限' },
  },
  {
    path: '/:pathMatch(.*)*',
    name: 'not-found',
    component: () => import('@/views/NotFoundView.vue'),
    meta: { public: true, title: '页面不存在' },
  },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
  scrollBehavior: () => ({ top: 0 }),
})

router.beforeEach(async (to) => {
  const auth = useAuthStore()
  await auth.bootstrap()

  const isPublic = to.meta.public === true

  // 系统未初始化：强制进入初始化向导
  if (auth.setupRequired) {
    return to.name === 'setup' ? true : { name: 'setup' }
  }
  // 已初始化：/setup 永久关闭
  if (to.name === 'setup') {
    return { name: auth.isAuthenticated ? 'workspace' : 'login' }
  }
  // 未登录访问受保护页面
  if (!isPublic && !auth.isAuthenticated) {
    return { name: 'login', query: { redirect: to.fullPath } }
  }
  // 已登录访问登录页
  if (to.name === 'login' && auth.isAuthenticated) {
    return { name: 'workspace' }
  }
  // 管理页面：仅体验拦截，后端仍会独立鉴权
  if (to.meta.adminOnly === true && !auth.isAdminLike) {
    return { name: 'forbidden' }
  }
  return true
})

router.afterEach((to) => {
  const title = typeof to.meta.title === 'string' ? to.meta.title : undefined
  document.title = title ? `${title} · EasyOA` : 'EasyOA'
})

export default router