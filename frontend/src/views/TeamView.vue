<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { OfficeBuilding, Search } from '@element-plus/icons-vue'

import { ApiError } from '@/api/errors'
import { userApi } from '@/api/modules/users'
import { workspaceApi } from '@/api/modules/workspace'
import type { MemberCard, MemberCollaboration, MemberProfile, SystemRole, UserStatus } from '@/api/types'
import EasyAvatar from '@/components/easy/EasyAvatar.vue'
import EasyButton from '@/components/easy/EasyButton.vue'
import EasyDialog from '@/components/easy/EasyDialog.vue'
import EasyDrawer from '@/components/easy/EasyDrawer.vue'
import EasyEmpty from '@/components/easy/EasyEmpty.vue'
import EasyInput from '@/components/easy/EasyInput.vue'
import EasySelect from '@/components/easy/EasySelect.vue'
import EasyStatus from '@/components/easy/EasyStatus.vue'
import { orgTypeLabel, useOrgTree } from '@/composables/useOrgTree'
import { useAuthStore } from '@/stores/auth'
import { useNotificationStore } from '@/stores/notification'
import { formatDate, formatDateTime, formatRelative } from '@/utils/format'
import { systemRoleLabel } from '@/utils/permission'
import { projectRoleLabel, projectStatusLabel, projectStatusTone } from '@/utils/project'
import { taskPriorityLabel, taskPriorityTone, taskStatusTypeTone } from '@/utils/task'

/**
 * 团队页面：成员目录（卡片式，非后台表格）。
 *
 * 交互：搜索 / 组织筛选 / 状态筛选 → 点击成员卡片 → 右侧档案侧栏。
 */
const auth = useAuthStore()
const notification = useNotificationStore()
const router = useRouter()
const { flatOptions, load: loadTree } = useOrgTree()

const keyword = ref('')
const orgFilter = ref<number | null>(null)
const statusFilter = ref<UserStatus | null>(null)
const page = ref(1)
const pageSize = 12
const total = ref(0)
const members = ref<MemberCard[]>([])
const loading = ref(false)
const loadError = ref<Error | null>(null)

const statusOptions = [
  { label: '全部状态', value: 'ALL' },
  { label: '在职', value: 'ACTIVE' },
  { label: '已停用', value: 'DISABLED' },
]

async function loadMembers(): Promise<void> {
  loading.value = true
  loadError.value = null
  try {
    const result = await userApi.directory({
      keyword: keyword.value.trim() || undefined,
      orgUnitId: orgFilter.value,
      status: statusFilter.value,
      page: page.value,
      size: pageSize,
    })
    members.value = result.items
    total.value = result.total
  } catch (error) {
    loadError.value = error instanceof Error ? error : new Error('成员加载失败')
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  void loadTree()
  void loadMembers()
})

let searchTimer: ReturnType<typeof setTimeout> | undefined
watch(keyword, () => {
  clearTimeout(searchTimer)
  searchTimer = setTimeout(() => {
    page.value = 1
    void loadMembers()
  }, 300)
})

watch([orgFilter, statusFilter], () => {
  page.value = 1
  void loadMembers()
})

watch(page, () => void loadMembers())

// --- 成员档案抽屉 -----------------------------------------------------------
const drawerOpen = ref(false)
const profile = ref<MemberProfile | null>(null)
const profileLoading = ref(false)
/** 参与项目 / 近期任务：由 workspace 聚合层提供，服务端已按查看者数据范围过滤 */
const collaboration = ref<MemberCollaboration | null>(null)

async function openProfile(userId: number): Promise<void> {
  drawerOpen.value = true
  profileLoading.value = true
  profile.value = null
  collaboration.value = null
  try {
    // 两个请求并行：档案（身份 / 组织 / 联系方式）与协作概览（项目 / 任务）
    const [loadedProfile, loadedCollaboration] = await Promise.all([
      userApi.profile(userId),
      workspaceApi.memberCollaboration(userId).catch(() => null),
    ])
    profile.value = loadedProfile
    collaboration.value = loadedCollaboration
  } catch (error) {
    notification.error(error)
    drawerOpen.value = false
  } finally {
    profileLoading.value = false
  }
}

/** 深链到项目详情（新标签式跳转保持在应用内，档案侧栏随之关闭） */
function openProject(projectId: number): void {
  drawerOpen.value = false
  void router.push({ name: 'project-detail', params: { id: String(projectId) } })
}

/** 深链到看板中的任务详情侧栏（URL 形如 /projects/1/board?task=11） */
function openTask(projectId: number, taskId: number): void {
  drawerOpen.value = false
  void router.push({
    name: 'project-board',
    params: { id: String(projectId) },
    query: { task: String(taskId) },
  })
}

// --- 新建成员（管理员） ------------------------------------------------------
const createOpen = ref(false)
const creating = ref(false)
const createError = ref<string | null>(null)
const createForm = reactive({
  username: '',
  displayName: '',
  jobTitle: '',
  email: '',
  phone: '',
  systemRole: 'MEMBER' as SystemRole,
  initialPassword: '',
  orgUnitId: null as number | null,
})

const roleOptions = computed(() =>
  auth.isRoot
    ? [
        { label: '成员', value: 'MEMBER' },
        { label: '管理员', value: 'ADMIN' },
        { label: '系统所有者（ROOT）', value: 'ROOT' },
      ]
    : [{ label: '成员', value: 'MEMBER' }],
)

function openCreate(): void {
  createError.value = null
  Object.assign(createForm, {
    username: '',
    displayName: '',
    jobTitle: '',
    email: '',
    phone: '',
    systemRole: 'MEMBER',
    initialPassword: '',
    orgUnitId: orgFilter.value,
  })
  createOpen.value = true
}

async function submitCreate(): Promise<void> {
  createError.value = null
  if (!createForm.username.trim() || !createForm.displayName.trim()) {
    createError.value = '请填写用户名与显示名称'
    return
  }
  if (createForm.initialPassword.length < 10) {
    createError.value = '初始密码长度至少 10 位'
    return
  }
  creating.value = true
  try {
    const created = await userApi.create({
      username: createForm.username.trim(),
      displayName: createForm.displayName.trim(),
      jobTitle: createForm.jobTitle.trim() || undefined,
      email: createForm.email.trim() || undefined,
      phone: createForm.phone.trim() || undefined,
      systemRole: createForm.systemRole,
      initialPassword: createForm.initialPassword,
      orgUnitId: createForm.orgUnitId,
    })
    createOpen.value = false
    notification.success(`成员 ${created.displayName} 已创建`)
    await Promise.all([loadMembers(), loadTree()])
  } catch (error) {
    if (error instanceof ApiError && error.fields?.length && error.fields[0]) {
      createError.value = error.fields[0].message
    } else {
      createError.value = error instanceof ApiError ? error.message : '创建失败，请稍后再试'
    }
  } finally {
    creating.value = false
  }
}

const roleTone = (role: SystemRole): 'brand' | 'warning' | 'neutral' => {
  if (role === 'ROOT') return 'brand'
  if (role === 'ADMIN') return 'warning'
  return 'neutral'
}
</script>

<template>
  <div class="easy-page">
    <header class="easy-page__header">
      <div>
        <h1 class="easy-page__title">团队</h1>
        <p class="easy-page__subtitle">
          共 {{ total }} 位成员 · 点击成员卡片查看完整档案
        </p>
      </div>
      <EasyButton v-if="auth.isAdminLike" variant="primary" @click="openCreate">添加成员</EasyButton>
    </header>

    <!-- 工具栏 -->
    <div class="toolbar">
      <div class="toolbar__search">
        <el-icon class="toolbar__search-icon"><Search /></el-icon>
        <input v-model="keyword" class="toolbar__input" placeholder="搜索姓名 / 用户名 / 职位" />
      </div>
      <EasySelect
        v-model="orgFilter"
        :options="flatOptions"
        placeholder="全部组织"
        :teleported="true"
        class="toolbar__select"
      />
      <EasySelect
        :model-value="statusFilter ?? 'ALL'"
        :options="statusOptions"
        placeholder="全部状态"
        :clearable="false"
        class="toolbar__select"
        @update:model-value="statusFilter = $event === 'ALL' ? null : ($event as UserStatus)"
      />
    </div>

    <!-- 加载失败 -->
    <div v-if="loadError" class="easy-card error-bar">
      <span>成员加载失败：{{ loadError.message }}</span>
      <EasyButton size="sm" @click="loadMembers">重试</EasyButton>
    </div>

    <!-- 成员卡片 -->
    <div v-if="loading" class="member-grid">
      <div v-for="index in 6" :key="index" class="easy-card member-card member-card--skeleton" />
    </div>

    <div v-else-if="members.length > 0" class="member-grid">
      <button
        v-for="member in members"
        :key="member.id"
        type="button"
        class="easy-card member-card"
        @click="openProfile(member.id)"
      >
        <div class="member-card__top">
          <EasyAvatar :name="member.displayName" :src="member.avatarUrl ?? null" size="lg" />
          <div class="member-card__identity">
            <span class="member-card__name">{{ member.displayName }}</span>
            <span class="member-card__job">{{ member.jobTitle || '职位待完善' }}</span>
          </div>
          <EasyStatus v-if="member.status === 'DISABLED'" label="已停用" tone="danger" />
        </div>

        <div class="member-card__meta">
          <span class="member-card__dept">
            <el-icon><OfficeBuilding /></el-icon>
            {{ member.primaryOrgUnit?.name ?? '未分配部门' }}
          </span>
          <span v-if="member.orgUnitCount > 1" class="member-card__extra">
            等 {{ member.orgUnitCount }} 个组织
          </span>
        </div>

        <div class="member-card__footer">
          <EasyStatus :label="systemRoleLabel(member.systemRole)" :tone="roleTone(member.systemRole)" />
          <span class="member-card__joined">加入于 {{ formatDate(member.joinedAt) }}</span>
        </div>
      </button>
    </div>

    <div v-else class="easy-card">
      <EasyEmpty
        title="没有匹配的成员"
        description="调整搜索关键字或组织筛选条件后再试；也可以直接添加新成员。"
      >
        <template v-if="auth.isAdminLike" #action>
          <EasyButton variant="primary" @click="openCreate">添加成员</EasyButton>
        </template>
      </EasyEmpty>
    </div>

    <div v-if="total > pageSize" class="pagination">
      <el-pagination
        v-model:current-page="page"
        layout="prev, pager, next"
        :total="total"
        :page-size="pageSize"
        background
      />
    </div>

    <!-- 成员档案侧栏 -->
    <EasyDrawer v-model="drawerOpen" :size="440" :show-header="false">
      <div v-if="profileLoading" class="profile-skeleton">正在加载档案…</div>

      <div v-else-if="profile" class="profile">
        <header class="profile__header">
          <EasyAvatar :name="profile.displayName" :src="profile.avatarUrl ?? null" size="lg" />
          <div class="profile__identity">
            <h2 class="profile__name">{{ profile.displayName }}</h2>
            <span class="profile__handle">@{{ profile.username }}</span>
          </div>
        </header>

        <div class="profile__badges">
          <EasyStatus :label="systemRoleLabel(profile.systemRole)" :tone="roleTone(profile.systemRole)" />
          <EasyStatus
            :label="profile.status === 'ACTIVE' ? '在职' : '已停用'"
            :tone="profile.status === 'ACTIVE' ? 'success' : 'danger'"
          />
          <EasyStatus v-if="profile.jobTitle" :label="profile.jobTitle" />
        </div>

        <p v-if="profile.bio" class="profile__bio">{{ profile.bio }}</p>

        <section class="profile__section">
          <h3 class="profile__section-title">组织归属</h3>
          <div class="profile__org">
            <div v-for="item in profile.orgUnits" :key="item.orgUnit.id" class="profile__org-item">
              <span class="profile__org-name">{{ item.orgUnit.name }}</span>
              <span class="profile__org-type">{{ orgTypeLabel(item.orgUnit.type) }}</span>
              <EasyStatus v-if="item.primary" label="主部门" tone="brand" />
              <EasyStatus v-else-if="item.orgUnit.status === 'ARCHIVED'" label="已归档" />
            </div>
            <p v-if="profile.orgUnits.length === 0" class="profile__empty">尚未加入任何组织</p>
          </div>
        </section>

        <section class="profile__section">
          <h3 class="profile__section-title">联系方式</h3>
          <div v-if="profile.contactVisible" class="profile__contact">
            <span>邮箱：{{ profile.contact?.email || '未填写' }}</span>
            <span>手机：{{ profile.contact?.phone || '未填写' }}</span>
          </div>
          <p v-else class="profile__empty">联系方式仅本人、系统管理员与其所在组织负责人可见</p>
        </section>

        <section class="profile__section">
          <h3 class="profile__section-title">
            参与项目
            <span v-if="collaboration" class="profile__section-count">{{ collaboration.projects.length }}</span>
          </h3>
          <div v-if="collaboration && collaboration.projects.length > 0" class="profile__projects">
            <button
              v-for="item in collaboration.projects"
              :key="item.projectId"
              type="button"
              class="profile__project"
              @click="openProject(item.projectId)"
            >
              <div class="profile__project-head">
                <span class="profile__project-name">{{ item.name }}</span>
                <EasyStatus :label="projectStatusLabel(item.status)" :tone="projectStatusTone(item.status)" />
              </div>
              <div class="profile__project-meta">
                <span>{{ projectRoleLabel(item.role) }}</span>
                <span>进度 {{ item.progress }}%</span>
              </div>
            </button>
          </div>
          <p v-else-if="collaboration" class="profile__empty">
            该成员尚未参与任何项目（或你无权查看其所在项目）。
          </p>
          <p v-else class="profile__empty">协作数据暂时无法加载，请稍后重试。</p>
        </section>

        <section class="profile__section">
          <h3 class="profile__section-title">
            近期任务
            <span v-if="collaboration" class="profile__section-count">{{ collaboration.recentTasks.length }}</span>
          </h3>
          <div v-if="collaboration && collaboration.recentTasks.length > 0" class="profile__tasks">
            <button
              v-for="item in collaboration.recentTasks"
              :key="item.taskId"
              type="button"
              class="profile__task"
              @click="openTask(item.projectId, item.taskId)"
            >
              <div class="profile__task-head">
                <span class="profile__task-title">{{ item.title }}</span>
                <span class="profile__task-status" :class="`is-${taskStatusTypeTone(item.statusType)}`">
                  {{ item.statusName }}
                </span>
              </div>
              <div class="profile__task-meta">
                <span>{{ item.projectName }}</span>
                <EasyStatus
                  :label="taskPriorityLabel(item.priority)"
                  :tone="taskPriorityTone(item.priority)"
                  subtle
                />
                <span v-if="item.plannedEndAt" :class="{ 'is-danger': item.overdue }">
                  {{ item.overdue ? '已逾期' : '截止' }} {{ formatDate(item.plannedEndAt) }}
                </span>
                <span>进度 {{ item.progress }}%</span>
              </div>
            </button>
          </div>
          <p v-else-if="collaboration" class="profile__empty">
            该成员当前没有进行中的任务。
          </p>
          <p v-else class="profile__empty">协作数据暂时无法加载，请稍后重试。</p>
          <p v-if="collaboration" class="profile__hint">
            仅展示未结束的任务；非管理员只能看到与自己同项目的内容。
          </p>
        </section>

        <section class="profile__section">
          <h3 class="profile__section-title">账号信息</h3>
          <dl class="profile__facts">
            <div><dt>加入时间</dt><dd>{{ formatDateTime(profile.createdAt) }}</dd></div>
            <div><dt>最近登录</dt><dd>{{ formatRelative(profile.lastLoginAt) }}</dd></div>
          </dl>
        </section>
      </div>
    </EasyDrawer>

    <!-- 新建成员 -->
    <EasyDialog v-model="createOpen" title="添加成员" :width="480">
      <div class="create-form">
        <EasyInput v-model="createForm.username" label="用户名" required placeholder="字母开头，3~32 位"
          hint="登录账号，创建后不可修改" />
        <EasyInput v-model="createForm.displayName" label="显示名称" required placeholder="例如：Waiting" />
        <EasyInput v-model="createForm.jobTitle" label="职位" placeholder="例如：后端工程师" />
        <div class="create-form__row">
          <EasyInput v-model="createForm.email" label="邮箱" placeholder="选填" />
          <EasyInput v-model="createForm.phone" label="手机" placeholder="选填" />
        </div>
        <div class="create-form__row">
          <EasySelect
            :model-value="createForm.systemRole"
            label="系统角色"
            :options="roleOptions"
            :clearable="false"
            block
            @update:model-value="createForm.systemRole = $event as SystemRole"
          />
          <EasySelect
            v-model="createForm.orgUnitId"
            label="组织单元"
            :options="flatOptions"
            placeholder="暂不分配"
            block
          />
        </div>
        <EasyInput
          v-model="createForm.initialPassword"
          label="初始密码"
          required
          type="password"
          show-password
          placeholder="至少 10 位，含字母与数字"
          hint="请通过安全渠道告知成员，并要求其首次登录后修改"
        />
        <p v-if="createError" class="create-form__error" role="alert">{{ createError }}</p>
      </div>
      <template #footer>
        <div class="dialog-footer">
          <EasyButton @click="createOpen = false">取消</EasyButton>
          <EasyButton variant="primary" :loading="creating" @click="submitCreate">创建成员</EasyButton>
        </div>
      </template>
    </EasyDialog>
  </div>
</template>

<style scoped>
.toolbar {
  display: flex;
  align-items: center;
  gap: var(--easy-space-3);
  flex-wrap: wrap;
}

.toolbar__search {
  display: flex;
  align-items: center;
  gap: var(--easy-space-2);
  height: 34px;
  padding: 0 var(--easy-space-3);
  border: 1px solid var(--easy-border-strong);
  border-radius: var(--easy-radius-md);
  background: var(--easy-surface);
  min-width: 280px;
  flex: 1;
  max-width: 420px;
}

.toolbar__search-icon {
  color: var(--easy-text-3);
  font-size: 15px;
}

.toolbar__input {
  flex: 1;
  border: none;
  outline: none;
  background: transparent;
  font-size: var(--easy-text-sm);
  color: var(--easy-text-1);
}

.toolbar__select {
  width: 200px;
}

.error-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--easy-space-4);
  padding: var(--easy-space-4) var(--easy-space-5);
  color: var(--easy-danger);
  font-size: var(--easy-text-sm);
}

.member-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(260px, 1fr));
  gap: var(--easy-space-4);
}

.member-card {
  display: flex;
  flex-direction: column;
  gap: var(--easy-space-3);
  padding: var(--easy-space-4);
  text-align: left;
  cursor: pointer;
  transition: border-color var(--easy-transition-fast), box-shadow var(--easy-transition-fast),
    transform var(--easy-transition-fast);
}

.member-card:hover {
  border-color: var(--easy-brand-subtle-border);
  box-shadow: var(--easy-shadow-sm);
  transform: translateY(-1px);
}

.member-card--skeleton {
  height: 132px;
  cursor: default;
  background: linear-gradient(90deg, var(--easy-surface-sunken), var(--easy-surface));
}

.member-card__top {
  display: flex;
  align-items: center;
  gap: var(--easy-space-3);
}

.member-card__identity {
  display: flex;
  flex-direction: column;
  min-width: 0;
  flex: 1;
}

.member-card__name {
  font-size: var(--easy-text-md);
  font-weight: 600;
  color: var(--easy-text-1);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.member-card__job {
  font-size: var(--easy-text-xs);
  color: var(--easy-text-3);
}

.member-card__meta {
  display: flex;
  align-items: center;
  gap: var(--easy-space-2);
  font-size: var(--easy-text-sm);
  color: var(--easy-text-2);
}

.member-card__dept {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.member-card__extra {
  flex: none;
  font-size: var(--easy-text-xs);
  color: var(--easy-text-3);
}

.member-card__footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--easy-space-2);
  padding-top: var(--easy-space-3);
  border-top: 1px solid var(--easy-border);
}

.member-card__joined {
  font-size: var(--easy-text-xs);
  color: var(--easy-text-3);
}

.pagination {
  display: flex;
  justify-content: center;
}

/* --- 档案侧栏 --------------------------------------------------------------- */
.profile {
  display: flex;
  flex-direction: column;
  gap: var(--easy-space-5);
}

.profile-skeleton {
  color: var(--easy-text-3);
  font-size: var(--easy-text-sm);
}

.profile__header {
  display: flex;
  align-items: center;
  gap: var(--easy-space-4);
}

.profile__identity {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.profile__name {
  font-size: var(--easy-text-lg);
  font-weight: 600;
}

.profile__handle {
  font-size: var(--easy-text-xs);
  color: var(--easy-text-3);
}

.profile__badges {
  display: flex;
  flex-wrap: wrap;
  gap: var(--easy-space-2);
}

.profile__bio {
  color: var(--easy-text-2);
  font-size: var(--easy-text-sm);
  line-height: var(--easy-leading-relaxed);
}

.profile__section {
  display: flex;
  flex-direction: column;
  gap: var(--easy-space-2);
  padding-top: var(--easy-space-4);
  border-top: 1px solid var(--easy-border);
}

.profile__section-title {
  display: flex;
  align-items: center;
  gap: var(--easy-space-2);
  font-size: var(--easy-text-sm);
  font-weight: 600;
  color: var(--easy-text-1);
}

.profile__section-count {
  padding: 0 6px;
  border-radius: var(--easy-radius-full);
  background: var(--easy-surface-sunken);
  color: var(--easy-text-3);
  font-size: 10px;
  font-weight: 500;
}

.profile__hint {
  margin-top: var(--easy-space-2);
  color: var(--easy-text-3);
  font-size: 10px;
  line-height: var(--easy-leading-relaxed);
}

.profile__projects,
.profile__tasks {
  display: flex;
  flex-direction: column;
  gap: var(--easy-space-2);
}

.profile__project,
.profile__task {
  display: flex;
  flex-direction: column;
  gap: 6px;
  width: 100%;
  padding: var(--easy-space-3);
  border: 1px solid var(--easy-border);
  border-radius: var(--easy-radius-md);
  background: var(--easy-surface);
  text-align: left;
  cursor: pointer;
  transition: background var(--easy-transition-fast), border-color var(--easy-transition-fast);
}

.profile__project:hover,
.profile__task:hover {
  background: var(--easy-surface-hover);
  border-color: var(--easy-border-strong);
}

.profile__project-head,
.profile__task-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--easy-space-2);
}

.profile__project-name,
.profile__task-title {
  font-size: var(--easy-text-sm);
  font-weight: 500;
  color: var(--easy-text-1);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.profile__task-status {
  flex: none;
  font-size: var(--easy-text-xs);
  color: var(--easy-text-2);
}

.profile__task-status.is-brand {
  color: var(--easy-brand-text);
}

.profile__task-status.is-warning {
  color: var(--easy-warning);
}

.profile__task-status.is-success {
  color: var(--easy-success);
}

.profile__project-meta,
.profile__task-meta {
  display: flex;
  align-items: center;
  gap: var(--easy-space-3);
  color: var(--easy-text-3);
  font-size: var(--easy-text-xs);
  flex-wrap: wrap;
}

.profile__task-meta .is-danger {
  color: var(--easy-danger);
}

.profile__org {
  display: flex;
  flex-direction: column;
  gap: var(--easy-space-2);
}

.profile__org-item {
  display: flex;
  align-items: center;
  gap: var(--easy-space-2);
  font-size: var(--easy-text-sm);
}

.profile__org-name {
  color: var(--easy-text-1);
}

.profile__org-type {
  font-size: var(--easy-text-xs);
  color: var(--easy-text-3);
}

.profile__contact {
  display: flex;
  flex-direction: column;
  gap: 4px;
  font-size: var(--easy-text-sm);
  color: var(--easy-text-2);
}

.profile__empty {
  color: var(--easy-text-3);
  font-size: var(--easy-text-xs);
  line-height: var(--easy-leading-relaxed);
}

.profile__facts {
  display: flex;
  flex-direction: column;
  gap: 6px;
  margin: 0;
  font-size: var(--easy-text-sm);
}

.profile__facts div {
  display: flex;
  justify-content: space-between;
  gap: var(--easy-space-4);
}

.profile__facts dt {
  color: var(--easy-text-3);
}

.profile__facts dd {
  margin: 0;
  color: var(--easy-text-1);
}

/* --- 新建成员表单 ----------------------------------------------------------- */
.create-form {
  display: flex;
  flex-direction: column;
  gap: var(--easy-space-4);
  padding-top: var(--easy-space-1);
}

.create-form__row {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: var(--easy-space-3);
}

.create-form__error {
  padding: var(--easy-space-2) var(--easy-space-3);
  border: 1px solid var(--easy-danger);
  border-radius: var(--easy-radius-md);
  background: var(--easy-danger-bg);
  color: var(--easy-danger);
  font-size: var(--easy-text-xs);
}

.dialog-footer {
  display: flex;
  justify-content: flex-end;
  gap: var(--easy-space-2);
}
</style>