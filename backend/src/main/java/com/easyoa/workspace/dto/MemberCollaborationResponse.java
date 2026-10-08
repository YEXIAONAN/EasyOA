package com.easyoa.workspace.dto;

import java.util.List;

import com.easyoa.project.dto.MemberProjectBrief;
import com.easyoa.task.dto.MemberTaskBrief;

/**
 * 成员协作概览（团队页面成员档案的「参与项目」与「近期任务」区块）。
 *
 * <p>之所以独立于 {@code MemberProfileResponse}：成员档案属于 user 模块，
 * 而这里需要跨 project / task 聚合；若塞进 user 模块会让基础模块反向依赖业务模块。
 * 因此由 workspace 聚合层提供，且**所有列表都已按查看者的数据范围过滤**——
 * 非管理员只能看到自己同样可见的项目与任务，不会因为查看他人档案而越权。
 */
public record MemberCollaborationResponse(
        List<MemberProjectBrief> projects,
        List<MemberTaskBrief> recentTasks) {
}