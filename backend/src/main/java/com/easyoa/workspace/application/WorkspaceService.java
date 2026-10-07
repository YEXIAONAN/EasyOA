package com.easyoa.workspace.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.easyoa.project.application.ProjectService;
import com.easyoa.user.application.UserService;
import com.easyoa.user.dto.UserProfileResponse;
import com.easyoa.workspace.dto.WorkspaceSummaryResponse;
import com.easyoa.workspace.dto.WorkspaceSummaryResponse.KpiSummary;
import com.easyoa.workspace.dto.WorkspaceSummaryResponse.UserBrief;

/**
 * 工作台聚合服务。
 *
 * <p>已接入真实数据的部分：当前用户信息、活动会话（前端另行查询）、进行中项目 KPI、项目进度列表。
 * 任务与审批相关统计待 Phase 4 / Phase 6 交付后接入，当前返回 0（前端按空状态渲染，不做假数据）。
 */
@Service
public class WorkspaceService {

    private static final int RECENT_PROJECT_LIMIT = 4;

    private final UserService userService;
    private final ProjectService projectService;

    public WorkspaceService(UserService userService, ProjectService projectService) {
        this.userService = userService;
        this.projectService = projectService;
    }

    @Transactional(readOnly = true)
    public WorkspaceSummaryResponse summary(Long userId) {
        UserProfileResponse profile = userService.getProfile(userId);
        KpiSummary kpis = new KpiSummary(0, 0, projectService.countActiveProjects(userId), 0);

        return new WorkspaceSummaryResponse(
                new UserBrief(profile.id(), profile.username(), profile.displayName(), profile.systemRole(),
                        profile.avatarUrl(), profile.lastLoginAt()),
                kpis,
                projectService.recentProjectsFor(userId, RECENT_PROJECT_LIMIT));
    }
}