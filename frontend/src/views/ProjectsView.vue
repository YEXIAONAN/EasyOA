<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { Plus, Search } from '@element-plus/icons-vue'

import { ApiError } from '@/api/errors'
import { projectApi } from '@/api/modules/projects'
import { userApi } from '@/api/modules/users'
import type { MemberCard, ProjectCard, ProjectStatus } from '@/api/types'
import EasyAvatar from '@/components/easy/EasyAvatar.vue'
import EasyButton from '@/components/easy/EasyButton.vue'
import EasyDialog from '@/components/easy/EasyDialog.vue'
import EasyEmpty from '@/components/easy/EasyEmpty.vue'
import EasyInput from '@/components/easy/EasyInput.vue'
import EasySelect from '@/components/easy/EasySelect.vue'
import EasyStatus from '@/components/easy/EasyStatus.vue'
import { useNotificationStore } from '@/stores/notification'
import { formatDate, toIsoInstant } from '@/utils/format'
import { projectRoleLabel, projectRoleTone, projectStatusLabel, projectStatusTone } from '@/utils/project'

/**
 * 项目列表页。
 *
 * 数据范围由后端决定：普通成员只看到自己参与的项目，管理员可看到全部项目。
 */
const router = useRouter()
const notification = useNotificationStore()

const keyword = ref('')
const statusFilter = ref<ProjectStatus | null>(null)
const page = ref(1)
const pageSize = 12
const total = ref(0)
const projects = ref<ProjectCard[]>([])
const loading = ref(false)
const loadError = ref<Error | null>(null)

const statusOptions = [
  { label: '全部状态', value: 'ALL' },
  { label: '草稿', value: 'DRAFT' },
  { label: '进行中', value: 'ACTIVE' },
  { label: '已暂停', value: 'PAUSED' },
  { label: '已完成', value: 'COMPLETED' },
  { label: '已归档', value: 'ARCHIVED' },
]

async function loadProjects(): Promise<void> {
  loading.value = true
  loadError.value = null
  try {
    const result = await projectApi.list({
      keyword: keyword.value.trim() || undefined,
      status: statusFilter.value,
      page: page.value,
      size: pageSize,
    })
    projects.value = result.items
    total.value = result.total
  } catch (error) {
    loadError.value = error instanceof Error ? error : new Error('项目加载失败')
  } finally {
    loading.value = false
  }
}

onMounted(loadProjects)

let searchTimer: ReturnType<typeof setTimeout> | undefined
watch(keyword, () => {
  clearTimeout(searchTimer)
  searchTimer = setTimeout(() => {
    page.value = 1
    void loadProjects()
  }, 300)
})

watch([statusFilter, page], () => void loadProjects())

// --- 新建项目 ---------------------------------------------------------------
const createOpen = ref(false)
const creating = ref(false)
const createError = ref<string | null>(null)
const memberOptions = ref<Array<{ label: string; value: number }>>([])
const createForm = reactive({
  name: '',
  description: '',
  plannedStartAt: '',
  plannedEndAt: '',
  startImmediately: true,
  memberUserIds: [] as number[],
})

async function openCreate(): Promise<void> {
  createError.value = null
  Object.assign(createForm, {
    name: '',
    description: '',
    plannedStartAt: '',
    plannedEndAt: '',
    startImmediately: true,
    memberUserIds: [],
  })
  createOpen.value = true
  if (memberOptions.value.length === 0) {
    try {
      const directory = await userApi.directory({ size: 100 })
      memberOptions.value = directory.items
        .filter((member: MemberCard) => member.status === 'ACTIVE')
        .map((member: MemberCard) => ({
          label: `${member.displayName}${member.primaryOrgUnit ? `（${member.primaryOrgUnit.name}）` : ''}`,
          value: member.id,
        }))
    } catch (error) {
      notification.error(error)
    }
  }
}

async function submitCreate(): Promise<void> {
  createError.value = null
  if (!createForm.name.trim()) {
    createError.value = '请输入项目名称'
    return
  }
  creating.value = true
  try {
    const created = await projectApi.create({
      name: createForm.name.trim(),
      description: createForm.description.trim() || undefined,
      plannedStartAt: toIsoInstant(createForm.plannedStartAt),
      plannedEndAt: toIsoInstant(createForm.plannedEndAt),
      memberUserIds: createForm.memberUserIds.length > 0 ? createForm.memberUserIds : undefined,
      status: createForm.startImmediately ? 'ACTIVE' : 'DRAFT',
    })
    createOpen.value = false
    notification.success(`项目「${created.name}」已创建，你是项目负责人`)
    await router.push({ name: 'project-detail', params: { id: created.id } })
  } catch (error) {
    createError.value = error instanceof ApiError ? error.message : '创建失败，请稍后再试'
  } finally {
    creating.value = false
  }
}

const activeCount = computed(
  () => projects.value.filter((project) => project.status === 'ACTIVE').length,
)
</script>

<template>
  <div class="easy-page">
    <header class="easy-page__header">
      <div>
        <h1 class="easy-page__title">项目</h1>
        <p class="easy-page__subtitle">
          共 {{ total }} 个项目 · 当前页 {{ activeCount }} 个进行中 · 点击卡片进入项目工作区
        </p>
      </div>
      <EasyButton variant="primary" @click="openCreate">
        <el-icon style="margin-right: 4px"><Plus /></el-icon>
        新建项目
      </EasyButton>
    </header>

    <div class="toolbar">
      <div class="toolbar__search">
        <el-icon class="toolbar__search-icon"><Search /></el-icon>
        <input v-model="keyword" class="toolbar__input" placeholder="搜索项目名称或简介" />
      </div>
      <EasySelect
        :model-value="statusFilter ?? 'ALL'"
        :options="statusOptions"
        :clearable="false"
        class="toolbar__select"
        @update:model-value="statusFilter = $event === 'ALL' ? null : ($event as ProjectStatus)"
      />
    </div>

    <div v-if="loadError" class="easy-card error-bar">
      <span>项目加载失败：{{ loadError.message }}</span>
      <EasyButton size="sm" @click="loadProjects">重试</EasyButton>
    </div>

    <div v-if="loading" class="project-grid">
      <div v-for="index in 6" :key="index" class="easy-card project-card project-card--skeleton" />
    </div>

    <div v-else-if="projects.length > 0" class="project-grid">
      <button
        v-for="project in projects"
        :key="project.id"
        type="button"
        class="easy-card project-card"
        @click="router.push({ name: 'project-detail', params: { id: project.id } })"
      >
        <div class="project-card__head">
          <span class="project-card__name">{{ project.name }}</span>
          <EasyStatus
            :label="projectStatusLabel(project.status)"
            :tone="projectStatusTone(project.status)"
          />
        </div>

        <p class="project-card__desc">{{ project.description || '暂无项目简介' }}</p>

        <div class="project-card__progress">
          <div class="progress">
            <div class="progress__bar" :style="{ width: `${project.progress}%` }" />
          </div>
          <span class="progress__value">{{ project.progress }}%</span>
        </div>

        <div class="project-card__meta">
          <span class="project-card__owner">
            <EasyAvatar
              v-if="project.owner"
              :name="project.owner.displayName"
              :src="project.owner.avatarUrl ?? null"
              size="sm"
            />
            <span>{{ project.owner?.displayName ?? '未设置负责人' }}</span>
            <span class="project-card__owner-role">负责人</span>
          </span>
          <span class="project-card__members">{{ project.memberCount }} 位成员</span>
        </div>

        <div class="project-card__footer">
          <EasyStatus
            v-if="project.myRole"
            :label="projectRoleLabel(project.myRole)"
            :tone="projectRoleTone(project.myRole)"
          />
          <span v-else class="project-card__visitor">仅可见</span>
          <span class="project-card__dates">
            {{ formatDate(project.plannedStartAt) }} → {{ formatDate(project.plannedEndAt) }}
          </span>
        </div>
      </button>
    </div>

    <div v-else class="easy-card">
      <EasyEmpty
        title="还没有项目"
        description="创建第一个项目后，你可以管理成员、跟踪进度，并在 Phase 4 交付后使用看板与任务管理。"
      >
        <template #action>
          <EasyButton variant="primary" @click="openCreate">新建项目</EasyButton>
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

    <!-- 新建项目 -->
    <EasyDialog v-model="createOpen" title="新建项目" :width="520">
      <div class="form-stack">
        <EasyInput v-model="createForm.name" label="项目名称" required placeholder="例如：EasyOA v0.1.0" />
        <EasyInput
          v-model="createForm.description"
          label="项目简介"
          type="textarea"
          :rows="3"
          placeholder="一句话说明项目目标与范围"
        />
        <div class="form-stack__row">
          <EasyInput v-model="createForm.plannedStartAt" label="计划开始" type="date" />
          <EasyInput v-model="createForm.plannedEndAt" label="计划结束" type="date" />
        </div>
        <EasySelect
          v-model="createForm.memberUserIds"
          label="初始成员"
          :options="memberOptions"
          multiple
          placeholder="可稍后在项目成员中添加"
          block
        />
        <label class="form-stack__switch">
          <el-switch v-model="createForm.startImmediately" size="small" />
          <span>创建后立即进入「进行中」（否则为草稿）</span>
        </label>
        <p class="form-stack__note">创建者自动成为项目负责人（OWNER），可设置副负责人或转让。</p>
        <p v-if="createError" class="form-stack__error" role="alert">{{ createError }}</p>
      </div>
      <template #footer>
        <div class="dialog-footer">
          <EasyButton @click="createOpen = false">取消</EasyButton>
          <EasyButton variant="primary" :loading="creating" @click="submitCreate">创建项目</EasyButton>
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
}

.toolbar__select {
  width: 180px;
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

.project-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(320px, 1fr));
  gap: var(--easy-space-4);
}

.project-card {
  display: flex;
  flex-direction: column;
  gap: var(--easy-space-3);
  padding: var(--easy-space-5);
  text-align: left;
  cursor: pointer;
  transition: border-color var(--easy-transition-fast), box-shadow var(--easy-transition-fast),
    transform var(--easy-transition-fast);
}

.project-card:hover {
  border-color: var(--easy-brand-subtle-border);
  box-shadow: var(--easy-shadow-sm);
  transform: translateY(-1px);
}

.project-card--skeleton {
  height: 210px;
  cursor: default;
  background: linear-gradient(90deg, var(--easy-surface-sunken), var(--easy-surface));
}

.project-card__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--easy-space-2);
}

.project-card__name {
  font-size: var(--easy-text-md);
  font-weight: 600;
  color: var(--easy-text-1);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.project-card__desc {
  min-height: 34px;
  color: var(--easy-text-3);
  font-size: var(--easy-text-sm);
  line-height: var(--easy-leading-relaxed);
  display: -webkit-box;
  -webkit-line-clamp: 2;
  line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.project-card__progress {
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

.project-card__meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--easy-space-2);
  font-size: var(--easy-text-sm);
  color: var(--easy-text-2);
}

.project-card__owner {
  display: inline-flex;
  align-items: center;
  gap: var(--easy-space-2);
  min-width: 0;
}

.project-card__owner-role {
  font-size: var(--easy-text-xs);
  color: var(--easy-text-3);
}

.project-card__members {
  flex: none;
  font-size: var(--easy-text-xs);
  color: var(--easy-text-3);
}

.project-card__footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--easy-space-2);
  padding-top: var(--easy-space-3);
  border-top: 1px solid var(--easy-border);
}

.project-card__visitor {
  font-size: var(--easy-text-xs);
  color: var(--easy-text-3);
}

.project-card__dates {
  font-size: var(--easy-text-xs);
  color: var(--easy-text-3);
}

.pagination {
  display: flex;
  justify-content: center;
}

.form-stack {
  display: flex;
  flex-direction: column;
  gap: var(--easy-space-4);
  padding-top: var(--easy-space-1);
}

.form-stack__row {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: var(--easy-space-3);
}

.form-stack__switch {
  display: inline-flex;
  align-items: center;
  gap: var(--easy-space-2);
  font-size: var(--easy-text-sm);
  color: var(--easy-text-2);
}

.form-stack__note {
  font-size: var(--easy-text-xs);
  color: var(--easy-text-3);
}

.form-stack__error {
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