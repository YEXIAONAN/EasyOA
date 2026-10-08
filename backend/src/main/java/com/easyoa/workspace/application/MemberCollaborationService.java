package com.easyoa.workspace.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.easyoa.common.exception.ApiException;
import com.easyoa.common.exception.ErrorCode;
import com.easyoa.common.requestid.RequestContext;
import com.easyoa.common.security.SecurityUser;
import com.easyoa.project.application.ProjectPermissionService;
import com.easyoa.project.application.ProjectScope;
import com.easyoa.project.application.ProjectService;
import com.easyoa.task.application.TaskService;
import com.easyoa.user.application.UserService;
import com.easyoa.workspace.dto.MemberCollaborationResponse;

/**
 * 成员协作概览：团队页面成员档案中「参与项目 / 近期任务」的数据来源。
 *
 * <p>放在 workspace 聚合层的原因：该视图需要同时读取 project 与 task 两个模块，
 * 而 user 模块是基础模块，不应反向依赖业务模块。
 *
 * <p>数据范围：先按查看者计算可见项目范围（项目成员可见，ROOT / ADMIN 可见全部），
 * 目标成员的数据再与之取交集——查看他人档案不会扩大自己的可见范围。
 */
@Service
public class MemberCollaborationService {

    private static final int RECENT_TASK_LIMIT = 6;

    private final UserService userService;
    private final ProjectService projectService;
    private final ProjectPermissionService projectPermissionService;
    private final TaskService taskService;

    public MemberCollaborationService(UserService userService, ProjectService projectService,
            ProjectPermissionService projectPermissionService, TaskService taskService) {
        this.userService = userService;
        this.projectService = projectService;
        this.projectPermissionService = projectPermissionService;
        this.taskService = taskService;
    }

    @Transactional(readOnly = true)
    public MemberCollaborationResponse of(Long targetUserId) {
        SecurityUser viewer = RequestContext.currentUser();
        if (viewer == null) {
            throw new ApiException(ErrorCode.UNAUTHENTICATED);
        }
        // 目标账号必须存在（不存在时返回 404，与其他档案接口一致）
        userService.getById(targetUserId);

        ProjectScope scope = projectPermissionService.scopeOf(viewer);
        return new MemberCollaborationResponse(
                projectService.memberProjects(targetUserId, scope),
                taskService.memberRecentTasks(targetUserId, scope, RECENT_TASK_LIMIT));
    }
}