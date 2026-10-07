import { computed, ref } from 'vue'

import { orgApi } from '@/api/modules/org'
import type { OrgUnitStatus, OrgUnitTreeNode, OrgUnitType } from '@/api/types'

export interface OrgTreeOption {
  label: string
  value: number
  depth: number
  type: OrgUnitType
  status: OrgUnitStatus
}

/**
 * 组织树加载与扁平化（团队页面筛选、组织架构页面共用）。
 *
 * {@code includeArchived} 由组合式函数持有并返回，页面可直接绑定开关，
 * 切换后调用 {@code load()} 即可刷新（避免「开关打开但请求参数未变」这类缺陷）。
 */
export function useOrgTree(options: { includeArchived?: boolean } = {}) {
  const tree = ref<OrgUnitTreeNode[]>([])
  const loading = ref(false)
  const error = ref<Error | null>(null)
  const includeArchived = ref(options.includeArchived ?? false)

  async function load(): Promise<void> {
    loading.value = true
    error.value = null
    try {
      tree.value = await orgApi.tree(includeArchived.value)
    } catch (caught) {
      error.value = caught instanceof Error ? caught : new Error('组织树加载失败')
    } finally {
      loading.value = false
    }
  }

  const flatOptions = computed<OrgTreeOption[]>(() => flatten(tree.value))

  return { tree, loading, error, includeArchived, load, flatOptions }
}

function flatten(nodes: OrgUnitTreeNode[]): OrgTreeOption[] {
  const result: OrgTreeOption[] = []
  const walk = (items: OrgUnitTreeNode[]): void => {
    for (const node of items) {
      const indent = '　'.repeat(node.depth)
      const suffix = node.status === 'ARCHIVED' ? '（已归档）' : ''
      result.push({
        label: `${indent}${node.name}${suffix}`,
        value: node.id,
        depth: node.depth,
        type: node.type,
        status: node.status,
      })
      if (node.children.length > 0) walk(node.children)
    }
  }
  walk(nodes)
  return result
}

/** 组织类型的中文标签 */
export function orgTypeLabel(type: OrgUnitType): string {
  return type === 'DEPARTMENT' ? '部门' : '团队'
}