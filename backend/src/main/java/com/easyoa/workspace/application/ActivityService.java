package com.easyoa.workspace.application;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.easyoa.approval.domain.ApprovalAction;
import com.easyoa.approval.domain.ApprovalActionType;
import com.easyoa.approval.repository.ApprovalActionRepository;
import com.easyoa.audit.application.AuditService;
import com.easyoa.audit.dto.AuditLogQuery;
import com.easyoa.audit.dto.AuditLogView;
import com.easyoa.comment.domain.Comment;
import com.easyoa.comment.repository.CommentRepository;
import com.easyoa.common.audit.AuditActions;
import com.easyoa.common.security.SecurityUser;
import com.easyoa.project.repository.ProjectMemberRepository;
import com.easyoa.task.application.TaskPermissionService;
import com.easyoa.task.domain.Task;
import com.easyoa.task.repository.TaskRepository;
import com.easyoa.user.domain.User;
import com.easyoa.user.repository.UserRepository;
import com.easyoa.workspace.dto.ActivityView;

/**
 * Activity Feed：业务动态聚合（不是审计日志）。
 *
 * <p>数据来源：评论（谁评论了任务）、任务完成（谁完成了任务）、任务创建、审批动作
 * （谁发起 / 通过 / 拒绝 / 退回）与任务状态变更记录；统一映射为业务语言并附带深链。
 *
 * <p>数据范围：普通用户仅能看到自己参与项目的动态与自己的审批动态；管理员可见全部。
 */
@Service
public class ActivityService {

    private static final Long SENTINEL_PROJECT_ID = -1L;

    private final ProjectMemberRepository projectMemberRepository;
    private final TaskRepository taskRepository;
    private final CommentRepository commentRepository;
    private final ApprovalActionRepository approvalActionRepository;
    private final UserRepository userRepository;
    private final AuditService auditService;
    private final TaskPermissionService permissionService;

    public ActivityService(ProjectMemberRepository projectMemberRepository, TaskRepository taskRepository,
            CommentRepository commentRepository, ApprovalActionRepository approvalActionRepository,
            UserRepository userRepository, AuditService auditService, TaskPermissionService permissionService) {
        this.projectMemberRepository = projectMemberRepository;
        this.taskRepository = taskRepository;
        this.commentRepository = commentRepository;
        this.approvalActionRepository = approvalActionRepository;
        this.userRepository = userRepository;
        this.auditService = auditService;
        this.permissionService = permissionService;
    }

    @Transactional(readOnly = true)
    public List<ActivityView> recent(Long userId, int limit) {
        SecurityUser actor = permissionService.requireAuthenticated();
        boolean scopeAll = actor.systemRole().isAdminLike();
        List<Long> projectIds = scopeAll ? List.of() : projectMemberRepository.findProjectIdsByUserId(userId);
        if (!scopeAll && projectIds.isEmpty()) {
            // 未参与任何项目：仅保留审批动态（空集合会生成非法 SQL，用哨兵值占位）
            projectIds = List.of(SENTINEL_PROJECT_ID);
        }
        PageRequest page = PageRequest.of(0, limit);
        List<ActivityView> items = new ArrayList<>();

        for (Comment comment : commentRepository.findRecentForActivity(scopeAll, projectIds, page)) {
            items.add(new ActivityView("COMMENT_CREATED",
                    comment.getAuthor().getId(), comment.getAuthor().getDisplayName(), comment.getAuthor().getAvatarUrl(),
                    "评论了任务", comment.getTask().getTitle(), taskLink(comment.getTask()),
                    comment.getCreatedAt()));
        }

        for (Task task : taskRepository.findRecentCompleted(scopeAll, projectIds, page)) {
            items.add(new ActivityView("TASK_COMPLETED",
                    task.getPrimaryAssignee().getId(), task.getPrimaryAssignee().getDisplayName(),
                    task.getPrimaryAssignee().getAvatarUrl(),
                    "完成了任务", task.getTitle(), taskLink(task), task.getCompletedAt()));
        }

        for (Task task : taskRepository.findRecentCreated(scopeAll, projectIds, page)) {
            User creator = task.getCreatedBy() == null ? null
                    : userRepository.findById(task.getCreatedBy()).orElse(null);
            items.add(new ActivityView("TASK_CREATED",
                    creator == null ? null : creator.getId(),
                    creator == null ? "系统" : creator.getDisplayName(),
                    creator == null ? null : creator.getAvatarUrl(),
                    "创建了任务", task.getTitle(), taskLink(task), task.getCreatedAt()));
        }

        for (ApprovalAction action : approvalActionRepository.findRecentForActivity(scopeAll, userId, page)) {
            items.add(new ActivityView("APPROVAL_" + action.getAction().name(),
                    action.getActor().getId(), action.getActor().getDisplayName(), action.getActor().getAvatarUrl(),
                    approvalPhrase(action.getAction()), action.getInstance().getTitle(),
                    "/approvals/" + action.getInstance().getId(), action.getCreatedAt()));
        }

        // 任务状态变更（业务动态视角，不是审计视图；仅展示可见项目内的任务）
        for (AuditLogView view : auditService
                .query(AuditLogQuery.of(null, AuditActions.TASK_STATUS_CHANGED, "TASK", null, null, null, null, 1,
                        limit))
                .items()) {
            Task task = parseTask(view.resourceId());
            if (task == null) {
                continue;
            }
            if (!scopeAll && !projectIds.contains(task.getProject().getId())) {
                continue;
            }
            items.add(new ActivityView("TASK_STATUS_CHANGED",
                    view.actorUserId(), view.actorUsername(), null,
                    "调整了任务状态", task.getTitle(), taskLink(task), view.createdAt()));
        }

        return items.stream()
                .sorted(Comparator.comparing(ActivityView::time).reversed())
                .limit(limit)
                .toList();
    }

    private Task parseTask(String resourceId) {
        if (resourceId == null) {
            return null;
        }
        try {
            return taskRepository.findById(Long.parseLong(resourceId)).orElse(null);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private String taskLink(Task task) {
        return "/projects/" + task.getProject().getId() + "/board?task=" + task.getId();
    }

    private String approvalPhrase(ApprovalActionType action) {
        return switch (action) {
            case SUBMIT -> "发起了审批";
            case APPROVE -> "通过了审批";
            case REJECT -> "拒绝了审批";
            case RETURN -> "退回了审批";
            case WITHDRAW -> "撤回了审批";
            case TRANSFER -> "转交了审批";
            case UPDATE_FORM -> "修改了审批表单";
        };
    }
}