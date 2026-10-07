<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ArrowDown, ArrowRight, Plus, Search } from '@element-plus/icons-vue'

import { ApiError } from '@/api/errors'
import { orgApi } from '@/api/modules/org'
import { userApi } from '@/api/modules/users'
import type { MemberCard, OrgMember, OrgUnitDetail, OrgUnitTreeNode } from '@/api/types'
import EasyAvatar from '@/components/easy/EasyAvatar.vue'
import EasyButton from '@/components/easy/EasyButton.vue'
import EasyDialog from '@/components/easy/EasyDialog.vue'
import EasyEmpty from '@/components/easy/EasyEmpty.vue'
import EasyInput from '@/components/easy/EasyInput.vue'
import EasySelect from '@/components/easy/EasySelect.vue'
import EasyStatus from '@/components/easy/EasyStatus.vue'
import { confirmAction } from '@/components/easy/easyConfirm'
import { orgTypeLabel, useOrgTree } from '@/composables/useOrgTree'
import { useAuthStore } from '@/stores/auth'
import { useNotificationStore } from '@/stores/notification'
import { formatDate } from '@/utils/format'

/**
 * 组织架构页面：左侧组织树（展开/折叠/搜索），右侧当前单元成员。
 *
 * 权限：前端仅按角色展示操作入口，所有变更最终由后端权限组件判定
 * （ROOT / ADMIN 调整结构；单元负责人可管理其成员）。
 */
const auth = useAuthStore()
const notification = useNotificationStore()

const { tree, loading: treeLoading, load: loadTree, includeArchived } = useOrgTree()

const keyword = ref('')
const collapsedIds = ref<Set<number>>(new Set())

const selectedId = ref<number | null>(null)
const detail = ref<OrgUnitDetail | null>(null)
const members = ref<OrgMember[]>([])
const membersLoading = ref(false)

const allMembers = ref<MemberCard[]>([])

async function loadTreeAndDetail(): Promise<void> {
  await loadTree()
  if (selectedId.value === null && tree.value.length > 0) {
    const first = tree.value[0]
    if (first) selectUnit(first.id)
  } else if (selectedId.value !== null) {
    await loadDetail(selectedId.value)
  }
}

onMounted(async () => {
  allMembers.value = (await userApi.directory({ size: 100 })).items
  await loadTreeAndDetail()
})

watch(includeArchived, async () => {
  await loadTree()
})

async function loadDetail(id: number): Promise<void> {
  membersLoading.value = true
  try {
    const [unitDetail, unitMembers] = await Promise.all([orgApi.detail(id), orgApi.members(id)])
    detail.value = unitDetail
    members.value = unitMembers
  } catch (error) {
    notification.error(error)
  } finally {
    membersLoading.value = false
  }
}

function selectUnit(id: number): void {
  selectedId.value = id
  void loadDetail(id)
}

function toggleCollapse(id: number): void {
  const next = new Set(collapsedIds.value)
  if (next.has(id)) next.delete(id)
  else next.add(id)
  collapsedIds.value = next
}

// --- 树形展示（搜索 + 折叠 + 层级缩进） ---------------------------------------
interface TreeRow {
  node: OrgUnitTreeNode
  depth: number
  hasChildren: boolean
}

const filteredTree = computed(() => filterTree(tree.value, keyword.value))

const visibleRows = computed<TreeRow[]>(() => {
  const rows: TreeRow[] = []
  const walk = (nodes: OrgUnitTreeNode[], depth: number): void => {
    for (const node of nodes) {
      rows.push({ node, depth, hasChildren: node.children.length > 0 })
      if (node.children.length > 0 && !collapsedIds.value.has(node.id)) {
        walk(node.children, depth + 1)
      }
    }
  }
  walk(filteredTree.value, 0)
  return rows
})

function filterTree(nodes: OrgUnitTreeNode[], text: string): OrgUnitTreeNode[] {
  const normalized = text.trim().toLowerCase()
  if (!normalized) return nodes
  const result: OrgUnitTreeNode[] = []
  for (const node of nodes) {
    const children = filterTree(node.children, text)
    if (node.name.toLowerCase().includes(normalized) || children.length > 0) {
      result.push({ ...node, children })
    }
  }
  return result
}

// --- 权限（仅用于展示入口，真实判定在后端） ------------------------------------
const treeIndex = computed(() => {
  const index = new Map<number, OrgUnitTreeNode>()
  const walk = (nodes: OrgUnitTreeNode[]): void => {
    for (const node of nodes) {
      index.set(node.id, node)
      walk(node.children)
    }
  }
  walk(tree.value)
  return index
})

const canManageStructure = computed(() => auth.isAdminLike)

const canManageMembers = computed(() => {
  if (auth.isAdminLike) return true
  const currentId = auth.user?.id
  if (!currentId || selectedId.value === null) return false
  let cursor: number | null = selectedId.value
  const visited = new Set<number>()
  while (cursor !== null && !visited.has(cursor)) {
    visited.add(cursor)
    const node = treeIndex.value.get(cursor)
    if (!node) return false
    if (node.manager?.id === currentId) return true
    cursor = node.parentId
  }
  return false
})

// --- 新建 / 编辑 / 移动 -------------------------------------------------------
const unitDialogOpen = ref(false)
const unitDialogMode = ref<'create' | 'edit'>('create')
const unitSubmitting = ref(false)
const unitError = ref<string | null>(null)
const unitForm = reactive({
  name: '',
  type: 'DEPARTMENT' as 'DEPARTMENT' | 'TEAM',
  parentId: null as number | null,
  sortOrder: 0,
  managerUserId: null as number | null,
})

const typeOptions = [
  { label: '部门', value: 'DEPARTMENT' },
  { label: '团队', value: 'TEAM' },
]

const memberOptions = computed(() =>
  allMembers.value
    .filter((member) => member.status === 'ACTIVE')
    .map((member) => ({
      label: `${member.displayName}（${member.primaryOrgUnit?.name ?? '未分配'}）`,
      value: member.id,
    })),
)

const parentOptions = computed(() => {
  const options: Array<{ label: string; value: number }> = [
    { label: '顶层（直接属于组织）', value: 0 },
  ]
  const walk = (nodes: OrgUnitTreeNode[]): void => {
    for (const node of nodes) {
      if (node.status === 'ACTIVE') {
        options.push({ label: `${'　'.repeat(node.depth)}${node.name}`, value: node.id })
      }
      walk(node.children)
    }
  }
  walk(tree.value)
  return options
})

function openCreateUnit(parentId: number | null): void {
  unitDialogMode.value = 'create'
  unitError.value = null
  Object.assign(unitForm, {
    name: '',
    type: 'DEPARTMENT',
    parentId,
    sortOrder: 0,
    managerUserId: null,
  })
  unitDialogOpen.value = true
}

function openEditUnit(): void {
  if (!detail.value) return
  unitDialogMode.value = 'edit'
  unitError.value = null
  Object.assign(unitForm, {
    name: detail.value.name,
    type: detail.value.type,
    parentId: detail.value.parentId,
    sortOrder: detail.value.sortOrder,
    managerUserId: detail.value.manager?.id ?? null,
  })
  unitDialogOpen.value = true
}

async function submitUnit(): Promise<void> {
  unitError.value = null
  if (!unitForm.name.trim()) {
    unitError.value = '请输入组织单元名称'
    return
  }
  unitSubmitting.value = true
  try {
    if (unitDialogMode.value === 'create') {
      const created = await orgApi.create({
        name: unitForm.name.trim(),
        type: unitForm.type,
        parentId: unitForm.parentId,
        sortOrder: unitForm.sortOrder,
        managerUserId: unitForm.managerUserId,
      })
      unitDialogOpen.value = false
      notification.success(`已创建「${created.name}」`)
      await loadTreeAndDetail()
      selectUnit(created.id)
    } else if (detail.value) {
      const updated = await orgApi.update(detail.value.id, {
        name: unitForm.name.trim(),
        type: unitForm.type,
        sortOrder: unitForm.sortOrder,
        managerUserId: unitForm.managerUserId,
      })
      unitDialogOpen.value = false
      notification.success(`已更新「${updated.name}」`)
      await loadTreeAndDetail()
    }
  } catch (error) {
    unitError.value = error instanceof ApiError ? error.message : '保存失败，请稍后再试'
  } finally {
    unitSubmitting.value = false
  }
}

// --- 移动 -------------------------------------------------------------------
const moveDialogOpen = ref(false)
const moveSubmitting = ref(false)
const moveError = ref<string | null>(null)
const moveTargetId = ref<number | null>(null)

const moveOptions = computed(() => {
  const forbidden = new Set<number>()
  if (selectedId.value !== null) {
    const collect = (node: OrgUnitTreeNode): void => {
      forbidden.add(node.id)
      node.children.forEach(collect)
    }
    const current = treeIndex.value.get(selectedId.value)
    if (current) collect(current)
  }
  return parentOptions.value.filter((option) => option.value === 0 || !forbidden.has(option.value))
})

function openMove(): void {
  if (!detail.value) return
  moveError.value = null
  moveTargetId.value = detail.value.parentId
  moveDialogOpen.value = true
}

async function submitMove(): Promise<void> {
  if (!detail.value) return
  moveSubmitting.value = true
  moveError.value = null
  try {
    await orgApi.move(detail.value.id, moveTargetId.value && moveTargetId.value > 0 ? moveTargetId.value : null)
    moveDialogOpen.value = false
    notification.success('组织单元已移动')
    await loadTreeAndDetail()
  } catch (error) {
    moveError.value = error instanceof ApiError ? error.message : '移动失败，请稍后再试'
  } finally {
    moveSubmitting.value = false
  }
}

async function archiveUnit(): Promise<void> {
  if (!detail.value) return
  const confirmed = await confirmAction({
    title: '归档组织单元',
    message: `归档后「${detail.value.name}」及其成员归属将变为只读，且不再出现在组织树中。确定继续吗？`,
    confirmText: '归档',
    danger: true,
  })
  if (!confirmed) return
  try {
    await orgApi.archive(detail.value.id)
    notification.success('组织单元已归档')
    await loadTreeAndDetail()
  } catch (error) {
    notification.error(error)
  }
}

async function restoreUnit(): Promise<void> {
  if (!detail.value) return
  try {
    await orgApi.restore(detail.value.id)
    notification.success('组织单元已恢复')
    await loadTreeAndDetail()
  } catch (error) {
    notification.error(error)
  }
}

// --- 成员管理 ---------------------------------------------------------------
const candidateId = ref<number | null>(null)

const candidateOptions = computed(() => {
  const existing = new Set(members.value.map((member) => member.userId))
  return memberOptions.value.filter((option) => !existing.has(option.value))
})

async function addMember(): Promise<void> {
  if (!detail.value || candidateId.value === null) return
  const unitId = detail.value.id
  try {
    members.value = await orgApi.addMember(unitId, candidateId.value)
    candidateId.value = null
    notification.success('成员已加入该组织')
    await Promise.all([loadTree(), loadDetail(unitId)])
  } catch (error) {
    notification.error(error)
  }
}

async function removeMember(member: OrgMember): Promise<void> {
  if (!detail.value) return
  const unitId = detail.value.id
  const confirmed = await confirmAction({
    title: '移出组织',
    message: `确定将 ${member.displayName} 从「${detail.value.name}」移出吗？${member.primary ? '该成员的主部门将自动调整为其他归属。' : ''}`,
    confirmText: '移出',
    danger: true,
  })
  if (!confirmed) return
  try {
    members.value = await orgApi.removeMember(unitId, member.userId)
    notification.success('成员已移出')
    await Promise.all([loadTree(), loadDetail(unitId)])
  } catch (error) {
    notification.error(error)
  }
}

async function setPrimary(member: OrgMember): Promise<void> {
  if (!detail.value) return
  try {
    await orgApi.setPrimary(detail.value.id, member.userId)
    await loadDetail(detail.value.id)
    notification.success(`${member.displayName} 的主部门已设为「${detail.value.name}」`)
  } catch (error) {
    notification.error(error)
  }
}
</script>

<template>
  <div class="easy-page">
    <header class="easy-page__header">
      <div>
        <h1 class="easy-page__title">组织架构</h1>
        <p class="easy-page__subtitle">部门与团队层级、负责人设置与成员归属</p>
      </div>
      <div class="header-actions">
        <label class="archived-toggle">
          <el-switch v-model="includeArchived" size="small" />
          <span>显示已归档</span>
        </label>
        <EasyButton v-if="canManageStructure" variant="primary" @click="openCreateUnit(null)">
          <el-icon style="margin-right: 4px"><Plus /></el-icon>
          新建部门 / 团队
        </EasyButton>
      </div>
    </header>

    <div class="org-layout">
      <!-- 组织树 -->
      <aside class="easy-card org-tree">
        <div class="org-tree__search">
          <el-icon class="org-tree__search-icon"><Search /></el-icon>
          <input v-model="keyword" class="org-tree__input" placeholder="搜索组织单元" />
        </div>

        <div v-if="treeLoading" class="org-tree__hint">正在加载组织树…</div>

        <div v-else-if="visibleRows.length === 0" class="org-tree__hint">
          暂无组织单元{{ canManageStructure ? '，点击右上角「新建部门 / 团队」开始搭建' : '' }}
        </div>

        <ul v-else class="org-tree__list">
          <li v-for="row in visibleRows" :key="row.node.id">
            <div
              class="org-tree__row"
              :class="{ 'is-selected': row.node.id === selectedId, 'is-archived': row.node.status === 'ARCHIVED' }"
              :style="{ paddingLeft: `${12 + row.depth * 16}px` }"
              @click="selectUnit(row.node.id)"
            >
              <button
                v-if="row.hasChildren"
                type="button"
                class="org-tree__toggle"
                @click.stop="toggleCollapse(row.node.id)"
              >
                <el-icon>
                  <ArrowDown v-if="!collapsedIds.has(row.node.id)" />
                  <ArrowRight v-else />
                </el-icon>
              </button>
              <span v-else class="org-tree__toggle org-tree__toggle--empty" />

              <span class="org-tree__name">{{ row.node.name }}</span>
              <EasyStatus v-if="row.node.status === 'ARCHIVED'" label="已归档" />
              <span class="org-tree__type">{{ orgTypeLabel(row.node.type) }}</span>
              <span class="org-tree__count">{{ row.node.memberCount }}</span>
            </div>
          </li>
        </ul>
      </aside>

      <!-- 单元详情 -->
      <section class="org-detail">
        <div v-if="!detail" class="easy-card">
          <EasyEmpty title="请选择组织单元" description="从左侧选择部门或团队，查看其成员与负责人。" />
        </div>

        <template v-else>
          <div class="easy-card">
            <div class="easy-card__header">
              <div class="org-detail__title">
                <span class="easy-card__title">{{ detail.name }}</span>
                <EasyStatus :label="orgTypeLabel(detail.type)" />
                <EasyStatus v-if="detail.status === 'ARCHIVED'" label="已归档" tone="warning" />
              </div>
              <div v-if="canManageStructure" class="org-detail__actions">
                <EasyButton size="sm" @click="openCreateUnit(detail.id)">新建下级</EasyButton>
                <EasyButton size="sm" @click="openEditUnit">编辑</EasyButton>
                <EasyButton size="sm" @click="openMove">移动</EasyButton>
                <EasyButton v-if="detail.status === 'ACTIVE'" size="sm" @click="archiveUnit">归档</EasyButton>
                <EasyButton v-else size="sm" variant="primary" @click="restoreUnit">恢复</EasyButton>
              </div>
            </div>

            <div class="org-detail__meta">
              <div class="org-detail__meta-item">
                <span class="org-detail__meta-label">负责人</span>
                <span v-if="detail.manager" class="org-detail__manager">
                  <EasyAvatar :name="detail.manager.displayName" :src="detail.manager.avatarUrl ?? null" size="sm" />
                  {{ detail.manager.displayName }}
                </span>
                <span v-else class="easy-muted">未设置</span>
              </div>
              <div class="org-detail__meta-item">
                <span class="org-detail__meta-label">上级单元</span>
                <span>{{ detail.parentName ?? '顶层' }}</span>
              </div>
              <div class="org-detail__meta-item">
                <span class="org-detail__meta-label">成员数量</span>
                <span>{{ detail.memberCount }}</span>
              </div>
            </div>
          </div>

          <div class="easy-card">
            <div class="easy-card__header">
              <span class="easy-card__title">成员</span>
              <div v-if="canManageMembers && detail.status === 'ACTIVE'" class="org-detail__add">
                <EasySelect
                  v-model="candidateId"
                  :options="candidateOptions"
                  placeholder="选择成员加入"
                  class="org-detail__candidate"
                />
                <EasyButton
                  variant="primary"
                  size="sm"
                  :disabled="candidateId === null"
                  @click="addMember"
                >
                  加入组织
                </EasyButton>
              </div>
            </div>

            <div v-if="membersLoading" class="org-detail__hint">正在加载成员…</div>

            <ul v-else-if="members.length > 0" class="member-list">
              <li v-for="member in members" :key="member.userId" class="member-row">
                <EasyAvatar :name="member.displayName" :src="member.avatarUrl ?? null" />
                <div class="member-row__identity">
                  <span class="member-row__name">{{ member.displayName }}</span>
                  <span class="member-row__job">{{ member.jobTitle || member.username }}</span>
                </div>
                <EasyStatus v-if="member.primary" label="主部门" tone="brand" />
                <EasyStatus v-if="member.status === 'DISABLED'" label="已停用" tone="danger" />
                <span class="member-row__joined">{{ formatDate(member.joinedAt) }} 加入</span>
                <div v-if="canManageMembers" class="member-row__actions">
                  <EasyButton v-if="!member.primary" size="sm" @click="setPrimary(member)">设为主部门</EasyButton>
                  <EasyButton size="sm" @click="removeMember(member)">移出</EasyButton>
                </div>
              </li>
            </ul>

            <EasyEmpty
              v-else
              compact
              title="该组织还没有成员"
              :description="
                canManageMembers
                  ? '使用右上角的选择框把成员加入本组织；首个归属会自动成为其主部门。'
                  : '当前组织尚未分配成员。'
              "
            />
          </div>
        </template>
      </section>
    </div>

    <!-- 新建 / 编辑单元 -->
    <EasyDialog v-model="unitDialogOpen" :title="unitDialogMode === 'create' ? '新建组织单元' : '编辑组织单元'" :width="460">
      <div class="form-stack">
        <EasyInput v-model="unitForm.name" label="名称" required placeholder="例如：技术部 / 后端组" />
        <div class="form-stack__row">
          <EasySelect
            :model-value="unitForm.type"
            label="类型"
            :options="typeOptions"
            :clearable="false"
            block
            @update:model-value="unitForm.type = $event as 'DEPARTMENT' | 'TEAM'"
          />
          <EasySelect
            v-if="unitDialogMode === 'create'"
            :model-value="unitForm.parentId ?? 0"
            label="上级单元"
            :options="parentOptions"
            :clearable="false"
            block
            @update:model-value="unitForm.parentId = $event === 0 ? null : Number($event)"
          />
          <EasyInput v-else :model-value="String(unitForm.sortOrder)" label="排序" placeholder="数字越小越靠前"
            @update:model-value="unitForm.sortOrder = Number($event) || 0" />
        </div>
        <EasySelect
          v-model="unitForm.managerUserId"
          label="负责人"
          :options="memberOptions"
          placeholder="暂不设置"
          hint="负责人可以在其单元（含下级）范围内管理成员；同时也是审批流中的组织负责人来源"
        />
        <p v-if="unitError" class="form-stack__error" role="alert">{{ unitError }}</p>
      </div>
      <template #footer>
        <div class="dialog-footer">
          <EasyButton @click="unitDialogOpen = false">取消</EasyButton>
          <EasyButton variant="primary" :loading="unitSubmitting" @click="submitUnit">保存</EasyButton>
        </div>
      </template>
    </EasyDialog>

    <!-- 移动单元 -->
    <EasyDialog v-model="moveDialogOpen" title="移动组织单元" :width="440">
      <div class="form-stack">
        <EasySelect
          :model-value="moveTargetId ?? 0"
          label="移动到"
          :options="moveOptions"
          :clearable="false"
          block
          hint="不能移动到自身或自己的下级（会造成组织结构循环）"
          @update:model-value="moveTargetId = $event === 0 ? null : Number($event)"
        />
        <p v-if="moveError" class="form-stack__error" role="alert">{{ moveError }}</p>
      </div>
      <template #footer>
        <div class="dialog-footer">
          <EasyButton @click="moveDialogOpen = false">取消</EasyButton>
          <EasyButton variant="primary" :loading="moveSubmitting" @click="submitMove">确认移动</EasyButton>
        </div>
      </template>
    </EasyDialog>
  </div>
</template>

<style scoped>
.header-actions {
  display: flex;
  align-items: center;
  gap: var(--easy-space-4);
}

.archived-toggle {
  display: inline-flex;
  align-items: center;
  gap: var(--easy-space-2);
  font-size: var(--easy-text-xs);
  color: var(--easy-text-3);
}

.org-layout {
  display: grid;
  grid-template-columns: 320px minmax(0, 1fr);
  gap: var(--easy-space-4);
  align-items: start;
}

@media (max-width: 1100px) {
  .org-layout {
    grid-template-columns: 260px minmax(0, 1fr);
  }
}

/* 窄屏（未正式支持，但避免布局被挤压到不可用） */
@media (max-width: 900px) {
  .org-layout {
    grid-template-columns: minmax(0, 1fr);
  }

  .org-tree__list {
    max-height: 240px;
  }
}

/* --- 组织树 --------------------------------------------------------------- */
.org-tree {
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.org-tree__search {
  display: flex;
  align-items: center;
  gap: var(--easy-space-2);
  padding: var(--easy-space-3) var(--easy-space-4);
  border-bottom: 1px solid var(--easy-border);
}

.org-tree__search-icon {
  color: var(--easy-text-3);
  font-size: 15px;
}

.org-tree__input {
  flex: 1;
  border: none;
  outline: none;
  background: transparent;
  font-size: var(--easy-text-sm);
}

.org-tree__list {
  padding: var(--easy-space-2);
  display: flex;
  flex-direction: column;
  gap: 2px;
  max-height: 62vh;
  overflow-y: auto;
}

.org-tree__row {
  display: flex;
  align-items: center;
  gap: var(--easy-space-2);
  height: 34px;
  padding-right: var(--easy-space-3);
  border-radius: var(--easy-radius-md);
  cursor: pointer;
  transition: background var(--easy-transition-fast);
}

.org-tree__row:hover {
  background: var(--easy-surface-hover);
}

.org-tree__row.is-selected {
  background: var(--easy-brand-subtle);
}

.org-tree__row.is-selected .org-tree__name {
  color: var(--easy-brand-text);
  font-weight: 600;
}

.org-tree__row.is-archived .org-tree__name {
  color: var(--easy-text-3);
}

.org-tree__toggle {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 18px;
  height: 18px;
  border: none;
  background: transparent;
  color: var(--easy-text-3);
  cursor: pointer;
  font-size: 12px;
  flex: none;
}

.org-tree__toggle--empty {
  cursor: default;
}

.org-tree__name {
  flex: 1;
  font-size: var(--easy-text-sm);
  color: var(--easy-text-1);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.org-tree__type {
  font-size: var(--easy-text-xs);
  color: var(--easy-text-3);
  flex: none;
}

.org-tree__count {
  min-width: 20px;
  text-align: right;
  font-size: var(--easy-text-xs);
  color: var(--easy-text-3);
  flex: none;
}

.org-tree__hint {
  padding: var(--easy-space-5);
  color: var(--easy-text-3);
  font-size: var(--easy-text-sm);
}

/* --- 单元详情 -------------------------------------------------------------- */
.org-detail {
  display: flex;
  flex-direction: column;
  gap: var(--easy-space-4);
  min-width: 0;
}

.org-detail__title {
  display: flex;
  align-items: center;
  gap: var(--easy-space-2);
}

.org-detail__actions {
  display: flex;
  gap: var(--easy-space-2);
  flex-wrap: wrap;
}

.org-detail__meta {
  display: flex;
  flex-wrap: wrap;
  gap: var(--easy-space-8);
  padding: var(--easy-space-4) var(--easy-space-5);
}

.org-detail__meta-item {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.org-detail__meta-label {
  font-size: var(--easy-text-xs);
  color: var(--easy-text-3);
}

.org-detail__manager {
  display: inline-flex;
  align-items: center;
  gap: var(--easy-space-2);
  font-size: var(--easy-text-sm);
}

.org-detail__add {
  display: flex;
  align-items: center;
  gap: var(--easy-space-2);
}

.org-detail__candidate {
  width: 220px;
}

.org-detail__hint {
  padding: var(--easy-space-5);
  color: var(--easy-text-3);
  font-size: var(--easy-text-sm);
}

.member-list {
  display: flex;
  flex-direction: column;
}

.member-row {
  display: flex;
  align-items: center;
  gap: var(--easy-space-3);
  padding: var(--easy-space-3) var(--easy-space-5);
  border-bottom: 1px solid var(--easy-border);
  flex-wrap: wrap;
}

.member-row:last-child {
  border-bottom: none;
}

.member-row__identity {
  display: flex;
  flex-direction: column;
  min-width: 0;
  flex: 1;
}

.member-row__name {
  font-size: var(--easy-text-sm);
  font-weight: 500;
  color: var(--easy-text-1);
}

.member-row__job {
  font-size: var(--easy-text-xs);
  color: var(--easy-text-3);
}

.member-row__joined {
  font-size: var(--easy-text-xs);
  color: var(--easy-text-3);
  flex: none;
}

.member-row__actions {
  display: flex;
  gap: var(--easy-space-2);
  flex: none;
}

/* --- 表单 ----------------------------------------------------------------- */
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