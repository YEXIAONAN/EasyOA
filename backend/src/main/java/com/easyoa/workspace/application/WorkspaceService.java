package com.easyoa.workspace.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.easyoa.approval.application.ApprovalService;
import com.easyoa.project.application.ProjectService;
import com.easyoa.task.application.TaskService;
import com.easyoa.user.application.UserService;
import com.easyoa.user.dto.UserProfileResponse;
import com.easyoa.workspace.dto.WorkspaceSummaryResponse;
import com.easyoa.workspace.dto.WorkspaceSummaryResponse.KpiSummary;
import com.easyoa.workspace.dto.WorkspaceSummaryResponse.UserBrief;

/**
 * 工作台聚合服务。
 *
 * <p>全部 KPI 与区块均为真实数据：当前用户信息、活动会话（前端另行查询）、
 * 我的任务 / 即将到期、待我审批、进行中项目、项目进度。
 */
@Service
public class WorkspaceService {

    private static final int RECENT_PROJECT_LIMIT = 4;
    private static final int MY_TASK_LIMIT = 5;
    private static final int ACTIVITY_LIMIT = 6;

    private final UserService userService;
    private final ProjectService projectService;
    private final TaskService taskService;
    private final ApprovalService approvalService;
    private final ActivityService activityService;

    public WorkspaceService(UserService userService, ProjectService projectService, TaskService taskService,
            ApprovalService approvalService, ActivityService activityService) {
        this.userService = userService;
        this.projectService = projectService;
        this.taskService = taskService;
        this.approvalService = approvalService;
        this.activityService = activityService;
    }

    @Transactional(readOnly = true)
    public WorkspaceSummaryResponse summary(Long userId) {
        UserProfileResponse profile = userService.getProfile(userId);
        KpiSummary kpis = new KpiSummary(
                taskService.countMyOpenTasks(userId),
                approvalService.countPendingForApprover(userId),
                projectService.countActiveProjects(userId),
                taskService.countMyDueSoonTasks(userId));

        return new WorkspaceSummaryResponse(
                new UserBrief(profile.id(), profile.username(), profile.displayName(), profile.systemRole(),
                        profile.avatarUrl(), profile.lastLoginAt()),
                kpis,
                taskService.recentMyTasks(userId, MY_TASK_LIMIT),
                approvalService.recentPendingForApprover(userId),
                activityService.recent(userId, ACTIVITY_LIMIT),
                projectService.recentProjectsFor(userId, RECENT_PROJECT_LIMIT));
    }
}