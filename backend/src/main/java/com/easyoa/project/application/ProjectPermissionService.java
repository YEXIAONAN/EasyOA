package com.easyoa.project.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.easyoa.common.exception.ApiException;
import com.easyoa.common.requestid.RequestContext;
import com.easyoa.common.security.SecurityUser;
import com.easyoa.project.domain.Project;
import com.easyoa.project.domain.ProjectMember;
import com.easyoa.project.domain.ProjectRole;
import com.easyoa.project.repository.ProjectMemberRepository;
import com.easyoa.project.repository.ProjectRepository;

/**
 * 项目权限：项目域的唯一权限入口。
 *
 * <p>规则（v0.1.0）：
 * <ul>
 *   <li><b>查看</b>：项目成员，或系统管理员（管理 / 审计需要）；非成员一律返回
 *       {@code 404 项目不存在或无权访问}，避免泄露项目是否存在（防 IDOR 探测）；</li>
 *   <li><b>修改信息 / 状态 / 成员</b>：OWNER 或 DEPUTY_OWNER；</li>
 *   <li><b>设置副负责人 / 转让 OWNER / 归档</b>：仅 OWNER；</li>
 *   <li>项目归档后一律只读（任何角色都不能再修改）。</li>
 * </ul>
 *
 * <p>注意：系统角色与项目角色彻底分离——ADMIN 不会自动获得项目内的管理权限，
 * 只能查看（便于审计与运维），不能代替项目负责人改动内容。
 */
@Service
public class ProjectPermissionService {

    private final ProjectRepository projectRepository;
    private final ProjectMemberRepository projectMemberRepository;

    public ProjectPermissionService(ProjectRepository projectRepository,
            ProjectMemberRepository projectMemberRepository) {
        this.projectRepository = projectRepository;
        this.projectMemberRepository = projectMemberRepository;
    }

    /** 当前用户在该项目中的角色；未参与返回 null。 */
    @Transactional(readOnly = true)
    public ProjectRole roleOf(Long projectId, Long userId) {
        if (userId == null) {
            return null;
        }
        return projectMemberRepository.findByProjectIdAndUserId(projectId, userId)
                .map(ProjectMember::getRole)
                .orElse(null);
    }

    /** 数据范围：非成员不可见（管理员除外）。 */
    @Transactional(readOnly = true)
    public Project requireViewable(Long projectId) {
        SecurityUser actor = requireAuthenticated();
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> ApiException.notFound("项目不存在或无权访问"));
        if (actor.systemRole().isAdminLike()) {
            return project;
        }
        if (projectMemberRepository.existsByProjectIdAndUserId(projectId, actor.id())) {
            return project;
        }
        // 与「不存在」保持同一响应，避免通过状态码差异探测项目是否存在
        throw ApiException.notFound("项目不存在或无权访问");
    }

    /** 项目日常管理（信息 / 状态 / 成员）：OWNER 或 DEPUTY_OWNER。 */
    @Transactional(readOnly = true)
    public Project requireManagement(Long projectId) {
        SecurityUser actor = requireAuthenticated();
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> ApiException.notFound("项目不存在或无权访问"));
        assertNotArchived(project);
        ProjectRole role = roleOf(projectId, actor.id());
        if (role == null || !role.isManagement()) {
            throw ApiException.forbidden("只有项目负责人或副负责人可以执行该操作");
        }
        return project;
    }

    /** 仅 OWNER 可执行（设置副负责人 / 转让 / 归档）。 */
    @Transactional(readOnly = true)
    public Project requireOwnership(Long projectId) {
        SecurityUser actor = requireAuthenticated();
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> ApiException.notFound("项目不存在或无权访问"));
        assertNotArchived(project);
        ProjectRole role = roleOf(projectId, actor.id());
        if (role == null || !role.isOwner()) {
            throw ApiException.forbidden("只有项目负责人可以执行该操作");
        }
        return project;
    }

    /** 归档后只读校验（供各写操作复用）。 */
    public void assertNotArchived(Project project) {
        if (project.getStatus().isReadOnly()) {
            throw ApiException.conflict("项目已归档，内容为只读状态");
        }
    }

    public SecurityUser requireAuthenticated() {
        SecurityUser actor = RequestContext.currentUser();
        if (actor == null) {
            throw new ApiException(com.easyoa.common.exception.ErrorCode.UNAUTHENTICATED);
        }
        return actor;
    }

    public boolean isAdminLike(SecurityUser actor) {
        return actor != null && actor.systemRole().isAdminLike();
    }
}