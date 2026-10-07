package com.easyoa.comment.application;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.easyoa.audit.application.AuditEntry;
import com.easyoa.audit.application.AuditService;
import com.easyoa.audit.domain.RiskLevel;
import com.easyoa.comment.domain.Comment;
import com.easyoa.comment.domain.CommentMention;
import com.easyoa.comment.domain.CommentVersion;
import com.easyoa.comment.dto.CommentVersionView;
import com.easyoa.comment.dto.CommentView;
import com.easyoa.comment.dto.CreateCommentRequest;
import com.easyoa.comment.dto.UpdateCommentRequest;
import com.easyoa.comment.repository.CommentMentionRepository;
import com.easyoa.comment.repository.CommentRepository;
import com.easyoa.comment.repository.CommentVersionRepository;
import com.easyoa.common.audit.AuditActions;
import com.easyoa.common.exception.ApiException;
import com.easyoa.common.exception.ErrorCode;
import com.easyoa.common.response.PageResponse;
import com.easyoa.common.security.SecurityUser;
import com.easyoa.file.application.FileService;
import com.easyoa.file.domain.FileObject;
import com.easyoa.file.dto.FileView;
import com.easyoa.project.repository.ProjectMemberRepository;
import com.easyoa.task.application.TaskPermissionService;
import com.easyoa.task.domain.Task;
import com.easyoa.task.dto.TaskUserBrief;
import com.easyoa.user.application.UserService;
import com.easyoa.user.domain.User;

/**
 * 任务评论服务。
 *
 * <p>规则（v0.1.0）：
 * <ul>
 *   <li>支持评论 / 一级回复 / @项目成员 / 附件；</li>
 *   <li>编辑保留完整历史（comment_versions 只追加）；</li>
 *   <li>撤回不做物理删除：数据库保留原始评论，界面显示「撤回了一条评论」；</li>
 *   <li>所有写操作在后端复查权限（{@link CommentPermissionService}）。</li>
 * </ul>
 */
@Service
public class CommentService {

    private final CommentRepository commentRepository;
    private final CommentVersionRepository commentVersionRepository;
    private final CommentMentionRepository commentMentionRepository;
    private final CommentPermissionService permissionService;
    private final FileService fileService;
    private final ProjectMemberRepository projectMemberRepository;
    private final UserService userService;
    private final TaskPermissionService taskPermissionService;
    private final AuditService auditService;

    public CommentService(CommentRepository commentRepository, CommentVersionRepository commentVersionRepository,
            CommentMentionRepository commentMentionRepository, CommentPermissionService permissionService,
            FileService fileService, ProjectMemberRepository projectMemberRepository, UserService userService,
            TaskPermissionService taskPermissionService, AuditService auditService) {
        this.commentRepository = commentRepository;
        this.commentVersionRepository = commentVersionRepository;
        this.commentMentionRepository = commentMentionRepository;
        this.permissionService = permissionService;
        this.fileService = fileService;
        this.projectMemberRepository = projectMemberRepository;
        this.userService = userService;
        this.taskPermissionService = taskPermissionService;
        this.auditService = auditService;
    }

    // --- 查询 -------------------------------------------------------------------

    /** 任务评论（顶层分页，最新在前；每条附带一级回复）。 */
    @Transactional(readOnly = true)
    public PageResponse<CommentView> list(Long taskId, int page, int size) {
        permissionService.requireTaskViewable(taskId);
        Page<Comment> pageResult = commentRepository.findTopLevel(taskId,
                PageRequest.of(Math.max(page, 1) - 1, Math.max(size, 1)));
        return PageResponse.of(assemble(pageResult.getContent()), page, size, pageResult.getTotalElements());
    }

    @Transactional(readOnly = true)
    public long countByTask(Long taskId) {
        permissionService.requireTaskViewable(taskId);
        return commentRepository.countByTaskId(taskId);
    }

    /** 编辑历史（作者 / 项目负责人 / 管理员）。 */
    @Transactional(readOnly = true)
    public List<CommentVersionView> versions(Long commentId) {
        permissionService.requireHistoryAccess(commentId);
        return commentVersionRepository.findByCommentId(commentId).stream()
                .map(CommentVersionView::from)
                .toList();
    }

    // --- 发表 / 回复 ---------------------------------------------------------------

    @Transactional
    public CommentView create(Long taskId, CreateCommentRequest request) {
        SecurityUser actor = taskPermissionService.requireAuthenticated();
        Task task = permissionService.requireTaskViewable(taskId);
        permissionService.requireTaskParticipant(task);

        Comment parent = resolveParent(taskId, request.parentId());
        User author = userService.getById(actor.id());
        Comment comment = new Comment(task, parent, author, request.content().trim());
        commentRepository.save(comment);
        // version_no = 1 保存原始内容，编辑历史只追加
        commentVersionRepository.save(new CommentVersion(comment, 1, comment.getContent(), author));

        List<CommentMention> mentions = syncMentions(task, comment, request.mentionUserIds());
        List<FileObject> attachments = fileService.linkToComment(task, comment, request.attachmentFileIds(), actor);
        return toView(comment, mentions, attachments.stream().map(FileView::from).toList(), List.of());
    }

    // --- 编辑 -------------------------------------------------------------------

    @Transactional
    public CommentView update(Long commentId, UpdateCommentRequest request) {
        SecurityUser actor = taskPermissionService.requireAuthenticated();
        Comment comment = permissionService.requireAuthor(commentId);
        if (comment.isWithdrawn()) {
            throw ApiException.conflict("已撤回的评论不能编辑");
        }
        int nextVersion = commentVersionRepository.findByCommentId(commentId).size() + 1;
        String content = request.content().trim();
        comment.edit(content);
        commentRepository.save(comment);
        commentVersionRepository.save(new CommentVersion(comment, nextVersion, content,
                userService.getById(actor.id())));

        List<CommentMention> mentions;
        if (request.mentionUserIds() != null) {
            mentions = syncMentions(comment.getTask(), comment, request.mentionUserIds());
        } else {
            mentions = commentMentionRepository.findByCommentIds(List.of(commentId));
        }

        auditService.record(AuditEntry.action(AuditActions.COMMENT_EDITED, RiskLevel.NORMAL)
                .resource("COMMENT", commentId)
                .before(Map.of("version", nextVersion - 1))
                .after(Map.of("version", nextVersion))
                .reason("编辑评论（保留完整历史）"));

        List<FileView> attachments = fileService.listByComments(List.of(commentId))
                .getOrDefault(commentId, List.of());
        return toView(comment, mentions, attachments, List.of());
    }

    // --- 撤回 -------------------------------------------------------------------

    /** 撤回：保留原始评论与编辑历史（数据库不删除），界面显示撤回占位。 */
    @Transactional
    public CommentView withdraw(Long commentId) {
        Comment comment = permissionService.requireAuthor(commentId);
        if (comment.isWithdrawn()) {
            throw ApiException.conflict("该评论已撤回");
        }
        comment.withdraw();
        commentRepository.save(comment);

        auditService.record(AuditEntry.action(AuditActions.COMMENT_WITHDRAWN, RiskLevel.NORMAL)
                .resource("COMMENT", commentId)
                .before(Map.of("withdrawn", false, "content", comment.getContent()))
                .after(Map.of("withdrawn", true))
                .reason("撤回评论（原始内容保留，供审计追踪）"));
        return toView(comment, List.of(), List.of(), List.of());
    }

    // --- 内部方法 ---------------------------------------------------------------

    private Comment resolveParent(Long taskId, Long parentId) {
        if (parentId == null) {
            return null;
        }
        Comment parent = commentRepository.findByIdWithAuthor(parentId)
                .orElseThrow(() -> ApiException.notFound("回复的评论不存在"));
        if (!parent.getTask().getId().equals(taskId)) {
            throw new ApiException(ErrorCode.UNPROCESSABLE, "回复的评论不属于该任务");
        }
        if (parent.isReply()) {
            throw new ApiException(ErrorCode.UNPROCESSABLE, "v0.1.0 只支持一级回复");
        }
        if (parent.isWithdrawn()) {
            throw ApiException.conflict("该评论已撤回，无法回复");
        }
        return parent;
    }

    /**
     * @成员差异同步（创建与编辑共用）。
     *
     * <p>必须使用差异同步而不是「全删再插」：同一事务内 Hibernate 的写队列先插后删，
     * 保留原有 @ 时插入会与尚在队列中的删除撞上唯一索引 (comment_id, user_id)，
     * 表现为 409 数据冲突。
     */
    private List<CommentMention> syncMentions(Task task, Comment comment, List<Long> userIds) {
        Set<Long> target = resolveMentionTargets(task, userIds);
        List<CommentMention> existing = commentMentionRepository.findByCommentIds(List.of(comment.getId()));
        Set<Long> existingIds = new HashSet<>();
        List<CommentMention> kept = new ArrayList<>();
        for (CommentMention mention : existing) {
            Long userId = mention.getUser().getId();
            existingIds.add(userId);
            if (target.contains(userId)) {
                kept.add(mention);
            } else {
                commentMentionRepository.delete(mention);
            }
        }
        for (Long userId : target) {
            if (!existingIds.contains(userId)) {
                kept.add(commentMentionRepository.save(new CommentMention(comment, userService.getById(userId))));
            }
        }
        return kept;
    }

    /** 校验 @ 目标：必须是项目内有效成员。 */
    private Set<Long> resolveMentionTargets(Task task, List<Long> userIds) {
        Set<Long> target = new LinkedHashSet<>();
        if (userIds == null) {
            return target;
        }
        for (Long userId : userIds) {
            if (userId == null) {
                continue;
            }
            if (!projectMemberRepository.existsByProjectIdAndUserId(task.getProject().getId(), userId)) {
                throw new ApiException(ErrorCode.UNPROCESSABLE, "只能 @ 项目内的成员");
            }
            User user = userService.getById(userId);
            if (!user.isActive()) {
                throw new ApiException(ErrorCode.UNPROCESSABLE, "无法 @ 已禁用的账号");
            }
            target.add(userId);
        }
        return target;
    }

    private List<CommentView> assemble(List<Comment> topLevel) {
        if (topLevel.isEmpty()) {
            return List.of();
        }
        List<Long> topIds = topLevel.stream().map(Comment::getId).toList();
        List<Comment> replies = commentRepository.findReplies(topIds);

        Set<Long> allIds = new LinkedHashSet<>(topIds);
        replies.forEach(reply -> allIds.add(reply.getId()));

        Map<Long, List<CommentMention>> mentions = new HashMap<>();
        for (CommentMention mention : commentMentionRepository.findByCommentIds(allIds)) {
            mentions.computeIfAbsent(mention.getComment().getId(), key -> new ArrayList<>()).add(mention);
        }
        Map<Long, List<FileView>> attachments = fileService.listByComments(allIds);

        Map<Long, List<Comment>> repliesByParent = new HashMap<>();
        for (Comment reply : replies) {
            repliesByParent.computeIfAbsent(reply.getParent().getId(), key -> new ArrayList<>()).add(reply);
        }

        List<CommentView> views = new ArrayList<>();
        for (Comment comment : topLevel) {
            List<CommentView> replyViews = repliesByParent.getOrDefault(comment.getId(), List.of()).stream()
                    .map(reply -> toView(reply,
                            mentions.getOrDefault(reply.getId(), List.of()),
                            attachments.getOrDefault(reply.getId(), List.of()),
                            List.of()))
                    .toList();
            views.add(toView(comment,
                    mentions.getOrDefault(comment.getId(), List.of()),
                    attachments.getOrDefault(comment.getId(), List.of()),
                    replyViews));
        }
        return views;
    }

    private CommentView toView(Comment comment, List<CommentMention> mentions, List<FileView> attachments,
            List<CommentView> replies) {
        boolean withdrawn = comment.isWithdrawn();
        return new CommentView(
                comment.getId(),
                comment.getTask().getId(),
                comment.getParent() == null ? null : comment.getParent().getId(),
                TaskUserBrief.from(comment.getAuthor()),
                withdrawn ? null : comment.getContent(),
                withdrawn,
                comment.isEdited(),
                withdrawn ? List.of()
                        : mentions.stream().map(mention -> TaskUserBrief.from(mention.getUser())).toList(),
                withdrawn ? List.of() : attachments,
                replies,
                comment.getCreatedAt(),
                comment.getUpdatedAt());
    }
}