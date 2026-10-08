package com.easyoa.project.dto;

/**
 * 成员参与的项目（团队页面成员档案「参与项目」区块）。
 *
 * <p>只暴露展示所需字段：项目名 / 状态 / 整体进度 / 该成员在项目中的角色。
 * 不含负责人与成员列表——档案侧栏不应变成项目详情页。
 */
public record MemberProjectBrief(
        Long projectId,
        String name,
        String status,
        int progress,
        String role) {
}