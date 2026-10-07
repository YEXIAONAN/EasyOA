import type { ProjectMemberView } from '@/api/types'

/**
 * 从评论内容中解析 @成员。
 *
 * 通过「内容包含 @姓名」反推成员 id：即使编辑后手动删除 @ 文案，
 * 也不会把 stale 的提及 id 发给后端（后端仍会校验必须是项目成员）。
 */
export function collectMentionIds(content: string, members: ProjectMemberView[]): number[] {
  const ids = new Set<number>()
  for (const member of members) {
    if (content.includes(`@${member.displayName}`)) {
      ids.add(member.userId)
    }
  }
  return [...ids]
}