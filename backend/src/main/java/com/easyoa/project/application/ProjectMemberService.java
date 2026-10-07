package com.easyoa.project.application;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.easyoa.audit.application.AuditEntry;
import com.easyoa.audit.application.AuditService;
import com.easyoa.audit.domain.RiskLevel;
import com.easyoa.common.audit.AuditActions;
import com.easyoa.common.exception.ApiException;
import com.easyoa.common.exception.ErrorCode;
import com.easyoa.common.requestid.RequestContext;
import com.easyoa.common.security.SecurityUser;
import com.easyoa.project.domain.Project;
import com.easyoa.project.domain.ProjectMember;
import com.easyoa.project.domain.ProjectRole;
import com.easyoa.project.dto.ProjectDetailResponse;
import com.easyoa.project.dto.ProjectMemberView;
import com.easyoa.project.repository.ProjectMemberRepository;
import com.easyoa.user.application.UserService;
import com.easyoa.user.domain.User;

/**
 * 项目成员与角色管理：成员增删、副负责人设置、OWNER 转让。
 *
 * <p>角色边界（产品规范）：
 * <ul>
 *   <li>OWNER：管理项目与成员、设置副负责人、转让 OWNER、归档项目；</li>
 *   <li>DEPUTY_OWNER：管理日常信息与普通成员；<b>不能</b>修改/移除 OWNER，不能归档；</li>
 *   <li>MEMBER：查看项目、参与任务，不能管理项目配置。</li>
 * </ul>
 *
 * <p>「单一 OWNER / 单一副负责人」除了服务层校验外，还有数据库 Partial Unique Index 兜底：
 * 并发转让或并发设置副负责人时，后提交的事务会因唯一约束失败并返回 409，而不是产生脏数据。
 */
@Service
public class ProjectMemberService {

    private final ProjectMemberRepository projectMemberRepository;
    private final ProjectService projectService;
    private final ProjectPermissionService permissionService;
    private final UserService userService;
    private final AuditService auditService;

    public ProjectMemberService(ProjectMemberRepository projectMemberRepository, ProjectService projectService,
            ProjectPermissionService permissionService, UserService userService, AuditService auditService) {
        this.projectMemberRepository = projectMemberRepository;
        this.projectService = projectService;
        this.permissionService = permissionService;
        this.userService = userService;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public List<ProjectMemberView> members(Long projectId) {
        permissionService.requireViewable(projectId);
        return projectMemberRepository.findByProjectIdWithUser(projectId).stream()
                .map(ProjectMemberView::from)
                .toList();
    }

    @Transactional
    public List<ProjectMemberView> addMember(Long projectId, Long userId) {
        Project project = permissionService.requireManagement(projectId);
        User user = userService.getById(userId);
        if (!user.isActive()) {
            throw new ApiException(ErrorCode.UNPROCESSABLE, "该账号已被禁用，无法加入项目");
        }
        if (projectMemberRepository.existsByProjectIdAndUserId(projectId, userId)) {
            throw ApiException.conflict("该成员已在项目中");
        }
        projectMemberRepository.save(new ProjectMember(project, user, ProjectRole.MEMBER));

        auditService.record(AuditEntry.action(AuditActions.PROJECT_MEMBER_ADDED, RiskLevel.NORMAL)
                .resource("PROJECT", projectId)
                .after(Map.of("userId", userId, "role", ProjectRole.MEMBER.name()))
                .reason("添加项目成员"));
        return members(projectId);
    }

    @Transactional
    public List<ProjectMemberView> removeMember(Long projectId, Long userId) {
        permissionService.requireManagement(projectId);
        ProjectMember target = projectMemberRepository.findByProjectIdAndUserId(projectId, userId)
                .orElseThrow(() -> ApiException.notFound("该成员不在项目中"));

        SecurityUser actor = permissionService.requireAuthenticated();
        if (target.getRole().isOwner()) {
            throw ApiException.conflict("不能移除项目负责人，请先转让 OWNER");
        }
        if (actor.id().equals(userId)) {
            throw ApiException.conflict("不能移除自己，请先转让 OWNER");
        }
        if (target.getRole() == ProjectRole.DEPUTY_OWNER && !isOwner(projectId, actor.id())) {
            throw ApiException.forbidden("只有项目负责人可以移除副负责人");
        }

        projectMemberRepository.delete(target);
        auditService.record(AuditEntry.action(AuditActions.PROJECT_MEMBER_REMOVED, RiskLevel.ELEVATED)
                .resource("PROJECT", projectId)
                .before(Map.of("userId", userId, "role", target.getRole().name()))
                .reason("移除项目成员"));
        return members(projectId);
    }

    /**
     * 设置副负责人（仅 OWNER）。传入 null 表示取消当前副负责人。
     */
    @Transactional
    public ProjectDetailResponse setDeputy(Long projectId, Long userId) {
        Project project = permissionService.requireOwnership(projectId);
        SecurityUser actor = permissionService.requireAuthenticated();

        ProjectMember currentDeputy = projectMemberRepository
                .findFirstByProjectIdAndRole(projectId, ProjectRole.DEPUTY_OWNER)
                .orElse(null);

        if (userId == null) {
            if (currentDeputy != null) {
                currentDeputy.changeRole(ProjectRole.MEMBER);
                projectMemberRepository.save(currentDeputy);
                auditService.record(AuditEntry.action(AuditActions.PROJECT_MEMBER_ADDED, RiskLevel.NORMAL)
                        .resource("PROJECT", projectId)
                        .before(Map.of("deputyUserId", currentDeputy.getUser().getId()))
                        .reason("取消副负责人"));
            }
            return projectService.toDetail(project, actor);
        }

        if (userId.equals(actor.id())) {
            throw ApiException.conflict("OWNER 本人无需设置为副负责人");
        }
        ProjectMember target = projectMemberRepository.findByProjectIdAndUserId(projectId, userId)
                .orElseThrow(() -> new ApiException(ErrorCode.UNPROCESSABLE, "该成员尚未加入项目，无法设为副负责人"));
        if (target.getRole().isOwner()) {
            throw ApiException.conflict("项目负责人不能同时作为副负责人");
        }
        if (currentDeputy != null && currentDeputy.getId().equals(target.getId())) {
            return projectService.toDetail(project, actor);
        }

        // 先清空原副负责人并 flush，避免同一事务内出现两个 DEPUTY_OWNER 触发唯一约束
        if (currentDeputy != null) {
            currentDeputy.changeRole(ProjectRole.MEMBER);
            projectMemberRepository.save(currentDeputy);
            projectMemberRepository.flush();
        }
        target.changeRole(ProjectRole.DEPUTY_OWNER);
        projectMemberRepository.save(target);

        auditService.record(AuditEntry.action(AuditActions.PROJECT_OWNER_TRANSFERRED, RiskLevel.ELEVATED)
                .resource("PROJECT", projectId)
                .after(Map.of("deputyOwnerUserId", userId))
                .reason("设置副负责人"));
        return projectService.toDetail(project, actor);
    }

    /**
     * 转让 OWNER（仅当前 OWNER）。转让后原 OWNER 变为普通成员，目标成员成为新的 OWNER。
     */
    @Transactional
    public ProjectDetailResponse transferOwner(Long projectId, Long targetUserId) {
        Project project = permissionService.requireOwnership(projectId);
        SecurityUser actor = permissionService.requireAuthenticated();

        if (actor.id().equals(targetUserId)) {
            throw ApiException.conflict("目标成员已经是项目负责人");
        }
        ProjectMember target = projectMemberRepository.findByProjectIdAndUserId(projectId, targetUserId)
                .orElseThrow(() -> new ApiException(ErrorCode.UNPROCESSABLE, "只能转让给项目内的成员"));
        ProjectMember currentOwner = projectMemberRepository.findByProjectIdAndUserId(projectId, actor.id())
                .orElseThrow(ApiException::forbidden);
        User targetUser = userService.getById(targetUserId);
        if (!targetUser.isActive()) {
            throw new ApiException(ErrorCode.UNPROCESSABLE, "目标成员账号已被禁用，无法接收项目");
        }

        // 顺序至关重要：先降级原 OWNER 并 flush，再提升新 OWNER（避免唯一索引冲突）
        currentOwner.changeRole(ProjectRole.MEMBER);
        projectMemberRepository.save(currentOwner);
        projectMemberRepository.flush();

        target.changeRole(ProjectRole.OWNER);
        projectMemberRepository.save(target);
        projectMemberRepository.flush();

        auditService.record(AuditEntry.action(AuditActions.PROJECT_OWNER_TRANSFERRED, RiskLevel.CRITICAL)
                .resource("PROJECT", projectId)
                .before(Map.of("ownerUserId", actor.id()))
                .after(Map.of("ownerUserId", targetUserId))
                .reason("转让项目负责人"));
        return projectService.toDetail(project, actor);
    }

    private boolean isOwner(Long projectId, Long userId) {
        return projectMemberRepository.findByProjectIdAndUserId(projectId, userId)
                .map(member -> member.getRole().isOwner())
                .orElse(false);
    }

    /** 当前用户在各项目中的角色（供前端在列表中展示操作入口）。 */
    @Transactional(readOnly = true)
    public ProjectRole myRole(Long projectId) {
        SecurityUser actor = RequestContext.currentUser();
        return permissionService.roleOf(projectId, actor == null ? null : actor.id());
    }
}