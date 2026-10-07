package com.easyoa.comment.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.easyoa.comment.domain.Comment;
import com.easyoa.comment.repository.CommentRepository;
import com.easyoa.common.exception.ApiException;
import com.easyoa.common.requestid.RequestContext;
import com.easyoa.common.security.SecurityUser;
import com.easyoa.project.application.ProjectPermissionService;
import com.easyoa.project.domain.ProjectRole;
import com.easyoa.task.application.TaskPermissionService;
import com.easyoa.task.domain.Task;

/**
 * 评论权限：评论域的唯一授权入口。
 *
 * <p>规则（v0.1.0）：
 * <ul>
 *   <li><b>查看</b>：任务可见 = 评论可见（非项目成员 404，防存在性探测）；</li>
 *   <li><b>发表 / 回复 / 上传附件</b>：项目成员（项目归档后只读）；</li>
 *   <li><b>编辑 / 撤回</b>：仅评论作者本人；</li>
 *   <li><b>编辑历史</b>：作者、项目负责人 / 副负责人，或系统管理员（审计需要）。</li>
 * </ul>
 */
@Service
public class CommentPermissionService {

    private final CommentRepository commentRepository;
    private final TaskPermissionService taskPermissionService;
    private final ProjectPermissionService projectPermissionService;

    public CommentPermissionService(CommentRepository commentRepository, TaskPermissionService taskPermissionService,
            ProjectPermissionService projectPermissionService) {
        this.commentRepository = commentRepository;
        this.taskPermissionService = taskPermissionService;
        this.projectPermissionService = projectPermissionService;
    }

    /** 任务可见（评论域与任务域共享同一条数据范围规则）。 */
    @Transactional(readOnly = true)
    public Task requireTaskViewable(Long taskId) {
        return taskPermissionService.requireViewable(taskId);
    }

    /** 评论可写：项目成员且项目未归档。 */
    @Transactional(readOnly = true)
    public void requireTaskParticipant(Task task) {
        SecurityUser actor = requireAuthenticated();
        projectPermissionService.assertNotArchived(task.getProject());
        if (projectPermissionService.roleOf(task.getProject().getId(), actor.id()) == null) {
            throw ApiException.forbidden("只有项目成员可以参与评论");
        }
    }

    /** 评论查看（非项目成员 404）。 */
    @Transactional(readOnly = true)
    public Comment requireViewable(Long commentId) {
        Comment comment = findComment(commentId);
        requireTaskViewable(comment.getTask().getId());
        return comment;
    }

    /** 仅作者本人（编辑 / 撤回）。 */
    @Transactional(readOnly = true)
    public Comment requireAuthor(Long commentId) {
        Comment comment = requireViewable(commentId);
        SecurityUser actor = requireAuthenticated();
        if (!comment.getAuthor().getId().equals(actor.id())) {
            throw ApiException.forbidden("只能操作自己的评论");
        }
        return comment;
    }

    /** 编辑历史：作者 / 项目负责人 / 系统管理员。 */
    @Transactional(readOnly = true)
    public Comment requireHistoryAccess(Long commentId) {
        Comment comment = requireViewable(commentId);
        SecurityUser actor = requireAuthenticated();
        if (comment.getAuthor().getId().equals(actor.id()) || actor.systemRole().isAdminLike()) {
            return comment;
        }
        ProjectRole role = projectPermissionService.roleOf(comment.getTask().getProject().getId(), actor.id());
        if (role != null && role.isManagement()) {
            return comment;
        }
        throw ApiException.forbidden("只有作者或项目负责人可以查看编辑历史");
    }

    private Comment findComment(Long commentId) {
        return commentRepository.findByIdWithAuthor(commentId)
                .orElseThrow(() -> ApiException.notFound("评论不存在或无权访问"));
    }

    private SecurityUser requireAuthenticated() {
        SecurityUser actor = RequestContext.currentUser();
        if (actor == null) {
            throw new ApiException(com.easyoa.common.exception.ErrorCode.UNAUTHENTICATED);
        }
        return actor;
    }
}