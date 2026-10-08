package com.easyoa.project.application;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;

import com.easyoa.audit.application.AuditEntry;
import com.easyoa.audit.application.AuditService;
import com.easyoa.audit.domain.RiskLevel;
import com.easyoa.common.audit.AuditActions;
import com.easyoa.common.exception.ApiException;
import com.easyoa.common.exception.ErrorCode;
import com.easyoa.common.requestid.RequestContext;
import com.easyoa.common.response.PageResponse;
import com.easyoa.common.security.SecurityUser;
import com.easyoa.project.domain.Project;
import com.easyoa.project.domain.ProjectMember;
import com.easyoa.project.domain.ProjectRole;
import com.easyoa.project.domain.ProjectStatus;
import com.easyoa.project.dto.CreateProjectRequest;
import com.easyoa.project.dto.MemberProjectBrief;
import com.easyoa.project.dto.ProjectCardResponse;
import com.easyoa.project.dto.ProjectDetailResponse;
import com.easyoa.project.dto.ProjectMemberView;
import com.easyoa.project.dto.UpdateProjectRequest;
import com.easyoa.project.repository.ProjectMemberRepository;
import com.easyoa.project.repository.ProjectRepository;
import com.easyoa.task.application.TaskStatusService;
import com.easyoa.user.application.UserService;
import com.easyoa.user.domain.User;

/**
 * 项目核心服务：列表（数据范围）、概览、创建、信息更新、生命周期与归档。
 */
@Service
public class ProjectService {

    /** 成员档案「参与项目」排序：进行中优先，归档最后，同组内按项目名。 */
    private static final Comparator<ProjectMember> MEMBER_PROJECT_ORDER = Comparator
            .comparingInt((ProjectMember member) -> projectRank(member.getProject().getStatus()))
            .thenComparing(member -> member.getProject().getName(), Comparator.naturalOrder());

    private final ProjectRepository projectRepository;
    private final ProjectMemberRepository projectMemberRepository;
    private final ProjectPermissionService permissionService;
    private final UserService userService;
    private final AuditService auditService;
    private final TaskStatusService taskStatusService;

    public ProjectService(ProjectRepository projectRepository, ProjectMemberRepository projectMemberRepository,
            ProjectPermissionService permissionService, UserService userService, AuditService auditService,
            TaskStatusService taskStatusService) {
        this.projectRepository = projectRepository;
        this.projectMemberRepository = projectMemberRepository;
        this.permissionService = permissionService;
        this.userService = userService;
        this.auditService = auditService;
        this.taskStatusService = taskStatusService;
    }

    // --- 查询 -----------------------------------------------------------------

    @Transactional(readOnly = true)
    public PageResponse<ProjectCardResponse> list(ProjectQuery query) {
        SecurityUser actor = permissionService.requireAuthenticated();
        boolean scopeAll = permissionService.isAdminLike(actor);
        Pageable pageable = PageRequest.of(Math.max(query.page(), 1) - 1, Math.max(query.size(), 1));

        Page<Project> projects = projectRepository.search(scopeAll, actor.id(), query.status(), query.keyword(),
                pageable);
        if (projects.isEmpty()) {
            return PageResponse.of(List.of(), query.page(), query.size(), projects.getTotalElements());
        }

        List<Long> projectIds = projects.getContent().stream().map(Project::getId).toList();
        Map<Long, List<ProjectMember>> membersByProject = new HashMap<>();
        for (ProjectMember member : projectMemberRepository.findByProjectIdsWithUser(projectIds)) {
            membersByProject.computeIfAbsent(member.getProject().getId(), key -> new ArrayList<>()).add(member);
        }

        List<ProjectCardResponse> cards = new ArrayList<>();
        for (Project project : projects.getContent()) {
            List<ProjectMember> members = membersByProject.getOrDefault(project.getId(), List.of());
            ProjectMember owner = findRole(members, ProjectRole.OWNER);
            ProjectMember deputy = findRole(members, ProjectRole.DEPUTY_OWNER);
            ProjectRole myRole = members.stream()
                    .filter(member -> member.getUser().getId().equals(actor.id()))
                    .map(ProjectMember::getRole)
                    .findFirst()
                    .orElse(null);
            cards.add(new ProjectCardResponse(
                    project.getId(),
                    project.getName(),
                    project.getDescription(),
                    project.getStatus().name(),
                    project.getProgress(),
                    project.getPlannedStartAt(),
                    project.getPlannedEndAt(),
                    owner == null ? null : ProjectMemberView.from(owner),
                    deputy == null ? null : ProjectMemberView.from(deputy),
                    members.size(),
                    myRole == null ? null : myRole.name(),
                    project.getUpdatedAt()));
        }
        return PageResponse.of(cards, query.page(), query.size(), projects.getTotalElements());
    }

    @Transactional(readOnly = true)
    public ProjectDetailResponse detail(Long projectId) {
        Project project = permissionService.requireViewable(projectId);
        return toDetail(project, permissionService.requireAuthenticated());
    }

    // --- 变更 -----------------------------------------------------------------

    @Transactional
    public ProjectDetailResponse create(CreateProjectRequest request) {
        SecurityUser actor = permissionService.requireAuthenticated();
        String name = request.name().trim();
        projectRepository.findFirstByNameIgnoreCase(name).ifPresent(existing -> {
            throw ApiException.conflict("已存在同名项目：" + name);
        });

        Project project = new Project(name, normalize(request.description()), actor.id());
        project.changeProgress(request.progress() == null ? 0 : request.progress());
        project.updateInfo(name, normalize(request.description()), request.plannedStartAt(), request.plannedEndAt());

        ProjectStatus initialStatus = resolveInitialStatus(request);
        if (initialStatus != ProjectStatus.DRAFT) {
            if (!ProjectStatus.DRAFT.canTransitionTo(initialStatus)) {
                throw new ApiException(ErrorCode.UNPROCESSABLE, "创建项目时不支持的状态：" + initialStatus);
            }
            project.changeStatus(initialStatus);
        }
        projectRepository.save(project);

        // 任务模块：为新项目初始化默认任务状态模板（待处理 → 进行中 → 待审核 → 已完成）
        taskStatusService.ensureDefaultStatuses(project);

        // 创建者自动成为 OWNER
        User actorUser = userService.getById(actor.id());
        projectMemberRepository.save(new ProjectMember(project, actorUser, ProjectRole.OWNER));

        // 初始成员
        Set<Long> addedIds = new HashSet<>();
        if (request.memberUserIds() != null) {
            for (Long userId : request.memberUserIds()) {
                if (userId == null || userId.equals(actor.id()) || !addedIds.add(userId)) {
                    continue;
                }
                User member = userService.getById(userId);
                if (member.isActive()) {
                    projectMemberRepository.save(new ProjectMember(project, member, ProjectRole.MEMBER));
                }
            }
        }

        auditService.record(AuditEntry.action(AuditActions.PROJECT_CREATED, RiskLevel.NORMAL)
                .resource("PROJECT", project.getId())
                .after(Map.of(
                        "name", project.getName(),
                        "status", project.getStatus().name(),
                        "memberCount", addedIds.size() + 1))
                .reason("创建项目"));
        return detail(project.getId());
    }

    @Transactional
    public ProjectDetailResponse updateInfo(Long projectId, UpdateProjectRequest request) {
        Project project = permissionService.requireManagement(projectId);
        Map<String, Object> before = new HashMap<>();
        before.put("name", project.getName());
        before.put("plannedStartAt", String.valueOf(project.getPlannedStartAt()));
        before.put("plannedEndAt", String.valueOf(project.getPlannedEndAt()));

        String name = request.name().trim();
        projectRepository.findFirstByNameIgnoreCase(name)
                .filter(existing -> !existing.getId().equals(projectId))
                .ifPresent(existing -> {
                    throw ApiException.conflict("已存在同名项目：" + name);
                });

        project.updateInfo(name, normalize(request.description()), request.plannedStartAt(),
                request.plannedEndAt());
        projectRepository.save(project);

        auditService.record(AuditEntry.action(AuditActions.PROJECT_STATUS_CHANGED, RiskLevel.NORMAL)
                .resource("PROJECT", projectId)
                .before(before)
                .after(Map.of("name", name))
                .reason("更新项目信息"));
        return detail(projectId);
    }

    /** 项目总体进度（OWNER / DEPUTY_OWNER 手工维护）。 */
    @Transactional
    public ProjectDetailResponse changeProgress(Long projectId, int progress) {
        Project project = permissionService.requireManagement(projectId);
        int before = project.getProgress();
        project.changeProgress(progress);
        projectRepository.save(project);
        auditService.record(AuditEntry.action(AuditActions.PROJECT_STATUS_CHANGED, RiskLevel.NORMAL)
                .resource("PROJECT", projectId)
                .before(Map.of("progress", before))
                .after(Map.of("progress", project.getProgress()))
                .reason("更新项目进度"));
        return detail(projectId);
    }

    /** 生命周期流转（含归档）；非法流转一律 409。 */
    @Transactional
    public ProjectDetailResponse changeStatus(Long projectId, ProjectStatus target) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> ApiException.notFound("项目不存在或无权访问"));
        permissionService.assertNotArchived(project);

        boolean archiving = target == ProjectStatus.ARCHIVED;
        // 归档属于高风险操作：仅 OWNER；其余状态流转 OWNER / DEPUTY_OWNER 均可
        if (archiving) {
            permissionService.requireOwnership(projectId);
        } else {
            permissionService.requireManagement(projectId);
        }

        ProjectStatus current = project.getStatus();
        if (current == target) {
            throw ApiException.conflict("项目已处于「" + target + "」状态");
        }
        if (!current.canTransitionTo(target)) {
            throw ApiException.conflict("不允许的状态流转：" + current + " → " + target);
        }

        project.changeStatus(target);
        projectRepository.save(project);

        auditService.record(AuditEntry.action(
                archiving ? AuditActions.PROJECT_ARCHIVED : AuditActions.PROJECT_STATUS_CHANGED,
                RiskLevel.ELEVATED)
                .resource("PROJECT", projectId)
                .before(Map.of("status", current.name()))
                .after(Map.of("status", target.name()))
                .reason("变更项目状态"));
        return detail(projectId);
    }

    // --- 内部方法 -------------------------------------------------------------

    /** 供其它服务复用：把项目转换为详情视图（含成员与当前用户可执行操作）。 */
    @Transactional(propagation = Propagation.REQUIRED, readOnly = true)
    public ProjectDetailResponse toDetail(Project project, SecurityUser actor) {
        List<ProjectMember> members = projectMemberRepository.findByProjectIdWithUser(project.getId());
        List<ProjectMemberView> views = members.stream().map(ProjectMemberView::from).toList();
        ProjectMemberView owner = views.stream()
                .filter(view -> ProjectRole.OWNER.name().equals(view.role()))
                .findFirst()
                .orElse(null);
        ProjectMemberView deputy = views.stream()
                .filter(view -> ProjectRole.DEPUTY_OWNER.name().equals(view.role()))
                .findFirst()
                .orElse(null);
        ProjectRole myRole = members.stream()
                .filter(member -> member.getUser().getId().equals(actor.id()))
                .map(ProjectMember::getRole)
                .findFirst()
                .orElse(null);

        boolean readOnly = project.getStatus().isReadOnly();
        boolean management = !readOnly && myRole != null && myRole.isManagement();
        boolean ownership = !readOnly && myRole != null && myRole.isOwner();

        return new ProjectDetailResponse(
                project.getId(),
                project.getName(),
                project.getDescription(),
                project.getStatus().name(),
                project.getProgress(),
                project.getPlannedStartAt(),
                project.getPlannedEndAt(),
                project.getArchivedAt(),
                project.getCreatedAt(),
                project.getUpdatedAt(),
                owner,
                deputy,
                views,
                myRole == null ? null : myRole.name(),
                new ProjectDetailResponse.Permissions(management, management, management, ownership, ownership,
                        ownership));
    }

    private ProjectStatus resolveInitialStatus(CreateProjectRequest request) {
        if (request.status() != null) {
            return request.status();
        }
        return Boolean.TRUE.equals(request.activateImmediately()) ? ProjectStatus.ACTIVE : ProjectStatus.DRAFT;
    }

    private ProjectMember findRole(List<ProjectMember> members, ProjectRole role) {
        return members.stream().filter(member -> member.getRole() == role).findFirst().orElse(null);
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    /** 供工作台使用：用户参与的项目（含成员视图）。 */
    @Transactional(readOnly = true)
    public List<ProjectCardResponse> recentProjectsFor(Long userId, int limit) {
        List<Project> projects = projectRepository.findRecentForUser(userId, ProjectStatus.ARCHIVED,
                PageRequest.of(0, limit));
        if (projects.isEmpty()) {
            return List.of();
        }
        List<Long> projectIds = projects.stream().map(Project::getId).toList();
        Map<Long, List<ProjectMember>> membersByProject = new HashMap<>();
        for (ProjectMember member : projectMemberRepository.findByProjectIdsWithUser(projectIds)) {
            membersByProject.computeIfAbsent(member.getProject().getId(), key -> new ArrayList<>()).add(member);
        }
        List<ProjectCardResponse> cards = new ArrayList<>();
        for (Project project : projects) {
            List<ProjectMember> members = membersByProject.getOrDefault(project.getId(), List.of());
            ProjectMember owner = findRole(members, ProjectRole.OWNER);
            ProjectMember deputy = findRole(members, ProjectRole.DEPUTY_OWNER);
            ProjectMember mine = members.stream()
                    .filter(member -> member.getUser().getId().equals(userId))
                    .findFirst()
                    .orElse(null);
            cards.add(new ProjectCardResponse(
                    project.getId(),
                    project.getName(),
                    project.getDescription(),
                    project.getStatus().name(),
                    project.getProgress(),
                    project.getPlannedStartAt(),
                    project.getPlannedEndAt(),
                    owner == null ? null : ProjectMemberView.from(owner),
                    deputy == null ? null : ProjectMemberView.from(deputy),
                    members.size(),
                    mine == null ? null : mine.getRole().name(),
                    project.getUpdatedAt()));
        }
        return cards;
    }

    /**
     * 成员档案「参与项目」：目标成员参与的项目，并按查看者的数据范围过滤。
     *
     * <p>返回顺序：进行中的项目优先，已归档的排最后（保留历史可见性，
     * 但不让归档项目占据档案侧栏的主要位置）。
     */
    @Transactional(readOnly = true)
    public List<MemberProjectBrief> memberProjects(Long targetUserId, ProjectScope scope) {
        return projectMemberRepository.findByUserIdWithProject(targetUserId).stream()
                .filter(member -> scope.covers(member.getProject().getId()))
                .sorted(MEMBER_PROJECT_ORDER)
                .map(member -> new MemberProjectBrief(
                        member.getProject().getId(),
                        member.getProject().getName(),
                        member.getProject().getStatus().name(),
                        member.getProject().getProgress(),
                        member.getRole().name()))
                .toList();
    }

    /** 活跃项目数量（工作台 KPI）。 */
    @Transactional(readOnly = true)
    public long countActiveProjects(Long userId) {
        return projectRepository.countByUserAndStatus(userId, ProjectStatus.ACTIVE);
    }

    private static int projectRank(ProjectStatus status) {
        return switch (status) {
            case ARCHIVED -> 2;
            case COMPLETED -> 1;
            default -> 0;
        };
    }
}