package com.easyoa.task.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.easyoa.common.exception.ApiException;
import com.easyoa.common.exception.ErrorCode;
import com.easyoa.common.requestid.RequestContext;
import com.easyoa.common.security.SecurityUser;
import com.easyoa.project.application.ProjectPermissionService;
import com.easyoa.project.domain.ProjectRole;
import com.easyoa.task.domain.Task;
import com.easyoa.task.repository.TaskRepository;

/**
 * 任务权限：任务域的唯一授权入口。
 *
 * <p>规则（v0.1.0）：
 * <ul>
 *   <li><b>查看</b>：项目成员，或系统管理员；非项目成员一律 404（与项目接口一致，防存在性探测）；</li>
 *   <li><b>管理</b>（状态 / 进度 / 优先级 / 时间 / 协作者 / 副负责人 / 依赖 / 子任务）：
 *       主负责人、副负责人、或项目 OWNER / DEPUTY_OWNER；</li>
 *   <li><b>全量控制</b>（修改主负责人、派发审核）：
 *       主负责人、或项目 OWNER / DEPUTY_OWNER；副负责人不能修改主负责人；</li>
 *   <li>项目归档后一律只读；派发审核未通过的任务不可被操作。</li>
 * </ul>
 *
 * <p>注意：协作成员只能查看、评论（Phase 5）与完成自己负责的子任务——
 * 子任务的负责人本身就是该子任务的主负责人，由 {@link #canManage} 覆盖。
 */
@Service
public class TaskPermissionService {

    private final TaskRepository taskRepository;
    private final ProjectPermissionService projectPermissionService;

    public TaskPermissionService(TaskRepository taskRepository, ProjectPermissionService projectPermissionService) {
        this.taskRepository = taskRepository;
        this.projectPermissionService = projectPermissionService;
    }

    /** 数据范围：非项目成员访问任务返回 404。 */
    @Transactional(readOnly = true)
    public Task requireViewable(Long taskId) {
        Task task = taskRepository.findByIdWithDetails(taskId)
                .orElseThrow(() -> ApiException.notFound("任务不存在或无权访问"));
        try {
            projectPermissionService.requireViewable(task.getProject().getId());
        } catch (ApiException ex) {
            // 与「任务不存在」保持同一响应，避免通过状态码差异探测任务是否存在
            throw ApiException.notFound("任务不存在或无权访问");
        }
        return task;
    }

    /** 任务管理操作（状态 / 进度 / 优先级 / 时间 / 协作者 / 副负责人 / 依赖 / 子任务）。 */
    @Transactional(readOnly = true)
    public Task requireManage(Long taskId) {
        SecurityUser actor = requireAuthenticated();
        Task task = requireViewable(taskId);
        assertActiveAssignment(task);
        ProjectRole role = projectPermissionService.roleOf(task.getProject().getId(), actor.id());
        if (!canManage(task, actor, role)) {
            throw ApiException.forbidden("你没有管理该任务的权限（仅主负责人 / 副负责人 / 项目负责人）");
        }
        return task;
    }

    /** 全量控制操作（修改主负责人、派发审核）。 */
    @Transactional(readOnly = true)
    public Task requireFullControl(Long taskId) {
        SecurityUser actor = requireAuthenticated();
        Task task = requireViewable(taskId);
        assertActiveAssignment(task);
        ProjectRole role = projectPermissionService.roleOf(task.getProject().getId(), actor.id());
        if (!canFullControl(task, actor, role)) {
            throw ApiException.forbidden("只有主负责人或项目负责人可以执行该操作");
        }
        return task;
    }

    /** 项目内任务管理：仅 OWNER / DEPUTY_OWNER。 */
    @Transactional(readOnly = true)
    public ProjectRole requireProjectManagement(Long projectId) {
        SecurityUser actor = requireAuthenticated();
        ProjectRole role = projectPermissionService.roleOf(projectId, actor.id());
        if (role == null || !role.isManagement()) {
            throw ApiException.forbidden("只有项目负责人或副负责人可以执行该操作");
        }
        return role;
    }

    /** 项目内任务写操作：必须是项目成员（管理员仅可查看，不代替项目成员操作）。 */
    @Transactional(readOnly = true)
    public ProjectRole requireProjectMember(Long projectId) {
        SecurityUser actor = requireAuthenticated();
        ProjectRole role = projectPermissionService.roleOf(projectId, actor.id());
        if (role == null) {
            throw ApiException.forbidden("只有项目成员可以执行该操作");
        }
        return role;
    }

    public boolean canManage(Task task, SecurityUser actor, ProjectRole projectRole) {
        return isPrimaryAssignee(task, actor.id())
                || isDeputyAssignee(task, actor.id())
                || (projectRole != null && projectRole.isManagement());
    }

    public boolean canFullControl(Task task, SecurityUser actor, ProjectRole projectRole) {
        return isPrimaryAssignee(task, actor.id())
                || (projectRole != null && projectRole.isManagement());
    }

    public boolean isPrimaryAssignee(Task task, Long userId) {
        return userId != null && task.getPrimaryAssignee() != null
                && userId.equals(task.getPrimaryAssignee().getId());
    }

    public boolean isDeputyAssignee(Task task, Long userId) {
        return userId != null && task.getDeputyAssignee() != null
                && userId.equals(task.getDeputyAssignee().getId());
    }

    /** 派发审核未通过的任务不可被流转 / 编辑（审核动作本身除外）。 */
    public void assertActiveAssignment(Task task) {
        if (!task.isActiveAssignment()) {
            throw ApiException.conflict("任务派发审核尚未通过，暂不可操作");
        }
    }

    public SecurityUser requireAuthenticated() {
        SecurityUser actor = RequestContext.currentUser();
        if (actor == null) {
            throw new ApiException(ErrorCode.UNAUTHENTICATED);
        }
        return actor;
    }
}