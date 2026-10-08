package com.easyoa.task.application;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.easyoa.audit.application.AuditEntry;
import com.easyoa.audit.application.AuditService;
import com.easyoa.audit.domain.RiskLevel;
import com.easyoa.common.audit.AuditActions;
import com.easyoa.common.exception.ApiException;
import com.easyoa.common.exception.ErrorCode;
import com.easyoa.common.response.PageResponse;
import com.easyoa.common.security.SecurityUser;
import com.easyoa.notification.application.NotificationService;
import com.easyoa.notification.domain.NotificationType;
import com.easyoa.project.application.ProjectPermissionService;
import com.easyoa.project.application.ProjectScope;
import com.easyoa.project.domain.Project;
import com.easyoa.project.domain.ProjectRole;
import com.easyoa.project.repository.ProjectMemberRepository;
import com.easyoa.task.domain.AssignmentState;
import com.easyoa.task.domain.ProgressMode;
import com.easyoa.task.domain.Task;
import com.easyoa.task.domain.TaskCollaborator;
import com.easyoa.task.domain.TaskDependency;
import com.easyoa.task.domain.TaskPriority;
import com.easyoa.task.domain.TaskStatus;
import com.easyoa.task.domain.TaskStatusType;
import com.easyoa.task.dto.ChangeTaskStatusRequest;
import com.easyoa.task.dto.CreateSubtaskRequest;
import com.easyoa.task.dto.CreateTaskRequest;
import com.easyoa.task.dto.MemberTaskBrief;
import com.easyoa.task.dto.TaskBoardResponse;
import com.easyoa.task.dto.TaskCardResponse;
import com.easyoa.task.dto.TaskDetailResponse;
import com.easyoa.task.dto.TaskStatusView;
import com.easyoa.task.dto.TaskUserBrief;
import com.easyoa.task.dto.UpdateTaskAssigneesRequest;
import com.easyoa.task.dto.UpdateTaskCollaboratorsRequest;
import com.easyoa.task.dto.UpdateTaskRequest;
import com.easyoa.task.repository.TaskCollaboratorRepository;
import com.easyoa.task.repository.TaskDependencyRepository;
import com.easyoa.task.repository.TaskRepository;
import com.easyoa.user.application.UserService;
import com.easyoa.user.domain.User;

/**
 * 任务核心服务。
 *
 * <p>职责与规则（v0.1.0）：
 * <ul>
 *   <li><b>状态流</b>：允许向前推进（含跳步）；允许回退到 TODO / ACTIVE（打回 / 重新打开）；
 *       CLOSED 为终态；依赖阻塞只拦截「从未开始进入已开始」的流转（取消/关闭不受限）；</li>
 *   <li><b>依赖</b>：同项目内前置依赖，循环检测在添加时完成；存在未完成依赖时进入
 *       ACTIVE 及之后的状态会被拒绝，用户填写原因后可「忽略依赖并开始」（审计留痕）；</li>
 *   <li><b>进度</b>：AUTO 模式按一级子任务完成比例计算，没有子任务时回退为手工模式；</li>
 *   <li><b>时间</b>：首次进入 ACTIVE 记录 actual_start_at，首次进入 DONE 记录 completed_at；</li>
 *   <li><b>派发</b>：OWNER / DEPUTY_OWNER 派发立即生效；成员派发给他人进入
 *       PENDING_ASSIGNMENT，等待项目负责人审核，被指派成员无需再接受。</li>
 * </ul>
 */
@Service
public class TaskService {

    /** 「即将到期」窗口（天）。 */
    private static final int DUE_SOON_DAYS = 7;

    /** 视为已结束的状态类型（统计与依赖解除阻塞）。 */
    private static final EnumSet<TaskStatusType> FINISHED_TYPES = EnumSet.of(TaskStatusType.DONE,
            TaskStatusType.CLOSED);

    private final TaskRepository taskRepository;
    private final TaskCollaboratorRepository taskCollaboratorRepository;
    private final TaskDependencyRepository taskDependencyRepository;
    private final TaskStatusService taskStatusService;
    private final TaskPermissionService permissionService;
    private final ProjectPermissionService projectPermissionService;
    private final ProjectMemberRepository projectMemberRepository;
    private final UserService userService;
    private final AuditService auditService;
    private final NotificationService notificationService;

    public TaskService(TaskRepository taskRepository, TaskCollaboratorRepository taskCollaboratorRepository,
            TaskDependencyRepository taskDependencyRepository, TaskStatusService taskStatusService,
            TaskPermissionService permissionService, ProjectPermissionService projectPermissionService,
            ProjectMemberRepository projectMemberRepository, UserService userService, AuditService auditService,
            NotificationService notificationService) {
        this.taskRepository = taskRepository;
        this.taskCollaboratorRepository = taskCollaboratorRepository;
        this.taskDependencyRepository = taskDependencyRepository;
        this.taskStatusService = taskStatusService;
        this.permissionService = permissionService;
        this.projectPermissionService = projectPermissionService;
        this.projectMemberRepository = projectMemberRepository;
        this.userService = userService;
        this.auditService = auditService;
        this.notificationService = notificationService;
    }

    // --- 看板与列表 -------------------------------------------------------------

    /** 项目看板：状态列 + 任务卡片 + 待派发审核任务（仅项目负责人可见）。 */
    @Transactional
    public TaskBoardResponse board(Long projectId) {
        Project project = projectPermissionService.requireViewable(projectId);
        SecurityUser actor = permissionService.requireAuthenticated();
        ProjectRole myRole = projectPermissionService.roleOf(projectId, actor.id());

        List<TaskStatusView> statuses = taskStatusService.statuses(projectId).stream()
                .map(TaskStatusView::from)
                .toList();
        Instant now = Instant.now();
        Map<Long, List<TaskDependency>> dependencies = dependencyMap(projectId);

        List<TaskCardResponse> tasks = taskRepository.findBoardTasks(projectId, AssignmentState.ACTIVE).stream()
                .map(task -> toCard(task, now, dependencies, actor, myRole))
                .toList();

        List<TaskCardResponse> pending = myRole != null && myRole.isManagement()
                ? taskRepository.findByProjectIdAndAssignmentState(projectId, AssignmentState.PENDING_ASSIGNMENT)
                        .stream()
                        .map(task -> toCard(task, now, Map.of(), actor, myRole))
                        .toList()
                : List.of();

        return new TaskBoardResponse(project.getId(), statuses, tasks, pending);
    }

    /** 项目内任务列表（分页，仅顶层任务；含待派发审核任务，不含被驳回的任务）。 */
    @Transactional(readOnly = true)
    public PageResponse<TaskCardResponse> listTasks(Long projectId, TaskQuery query) {
        projectPermissionService.requireViewable(projectId);
        SecurityUser actor = permissionService.requireAuthenticated();
        ProjectRole myRole = projectPermissionService.roleOf(projectId, actor.id());

        Pageable pageable = PageRequest.of(Math.max(query.page(), 1) - 1, Math.max(query.size(), 1));
        Page<Task> page = taskRepository.searchProjectTasks(projectId, AssignmentState.REJECTED, query.statusId(),
                query.priority(), query.keyword(), pageable);
        if (page.isEmpty()) {
            return PageResponse.of(List.of(), query.page(), query.size(), 0);
        }
        Instant now = Instant.now();
        Map<Long, List<TaskDependency>> dependencies = dependencyMap(projectId);
        List<TaskCardResponse> cards = page.getContent().stream()
                .map(task -> toCard(task, now, dependencies, actor, myRole))
                .toList();
        return PageResponse.of(cards, query.page(), query.size(), page.getTotalElements());
    }

    /** 我的任务：主负责人 / 副负责人 / 协作成员，跨项目。 */
    @Transactional(readOnly = true)
    public PageResponse<TaskCardResponse> myTasks(Long userId, MyTaskQuery query) {
        SecurityUser actor = permissionService.requireAuthenticated();
        if (!actor.id().equals(userId)) {
            throw ApiException.forbidden("只能查看自己的任务");
        }
        Pageable pageable = PageRequest.of(Math.max(query.page(), 1) - 1, Math.max(query.size(), 1));
        boolean openOnly = query.filter() == MyTaskQuery.MyTaskFilter.OPEN
                || query.filter() == MyTaskQuery.MyTaskFilter.DUE_SOON;
        boolean doneOnly = query.filter() == MyTaskQuery.MyTaskFilter.DONE;
        // 始终传入非空阈值：避免 PostgreSQL 对可空时间参数无法推断类型（dueSoon 为 false 时该条件不生效）
        boolean dueSoon = query.filter() == MyTaskQuery.MyTaskFilter.DUE_SOON;
        Instant dueBefore = Instant.now().plus(DUE_SOON_DAYS, ChronoUnit.DAYS);

        Page<Task> page = taskRepository.searchMyTasks(userId, AssignmentState.ACTIVE, openOnly, doneOnly,
                FINISHED_TYPES, dueSoon, dueBefore, query.keyword(), pageable);
        if (page.isEmpty()) {
            return PageResponse.of(List.of(), query.page(), query.size(), 0);
        }

        // 卡片权限取决于用户在各项目中的角色：按项目缓存，避免逐条查询
        Map<Long, ProjectRole> roleCache = new HashMap<>();
        Map<Long, Map<Long, List<TaskDependency>>> dependencyCache = new HashMap<>();
        Instant now = Instant.now();
        List<TaskCardResponse> cards = page.getContent().stream().map(task -> {
            Long projectId = task.getProject().getId();
            ProjectRole role = roleCache.computeIfAbsent(projectId,
                    id -> projectPermissionService.roleOf(id, actor.id()));
            Map<Long, List<TaskDependency>> dependencies = dependencyCache.computeIfAbsent(projectId,
                    this::dependencyMap);
            return toCard(task, now, dependencies, actor, role);
        }).toList();
        return PageResponse.of(cards, query.page(), query.size(), page.getTotalElements());
    }

    /** 工作台「我的任务」区块：近期重要任务。 */
    @Transactional(readOnly = true)
    public List<TaskCardResponse> recentMyTasks(Long userId, int limit) {
        return myTasks(userId, MyTaskQuery.of("OPEN", null, 1, limit)).items();
    }

    /** 成员档案「近期任务」：目标成员未结束的任务，限定在查看者可见的项目范围内。 */
    @Transactional(readOnly = true)
    public List<MemberTaskBrief> memberRecentTasks(Long targetUserId, ProjectScope scope, int limit) {
        if (scope.isEmpty()) {
            return List.of();
        }
        // 不受限（管理员）时传哨兵值：query 内由 allProjects 短路，避免空 in () 语句
        List<Long> projectIds = scope.unrestricted() ? List.of(-1L) : List.copyOf(scope.projectIds());
        Instant now = Instant.now();
        return taskRepository.findMemberOpenTasks(targetUserId, scope.unrestricted(), projectIds,
                        AssignmentState.ACTIVE, FINISHED_TYPES, PageRequest.of(0, limit)).stream()
                .map(task -> new MemberTaskBrief(
                        task.getId(),
                        task.getProject().getId(),
                        task.getProject().getName(),
                        task.getTitle(),
                        task.getStatus().getName(),
                        task.getStatus().getSystemType().name(),
                        task.getPriority().name(),
                        task.getProgress(),
                        task.getPlannedEndAt(),
                        task.getPlannedEndAt() != null && task.getPlannedEndAt().isBefore(now)))
                .toList();
    }

    /** 工作台 KPI：未结束任务数。 */
    @Transactional(readOnly = true)
    public long countMyOpenTasks(Long userId) {
        return taskRepository.countMyOpenTasks(userId, AssignmentState.ACTIVE, FINISHED_TYPES);
    }

    /** 工作台 KPI：即将到期（含已逾期）任务数。 */
    @Transactional(readOnly = true)
    public long countMyDueSoonTasks(Long userId) {
        return taskRepository.countMyDueSoonTasks(userId, AssignmentState.ACTIVE, FINISHED_TYPES,
                Instant.now().plus(DUE_SOON_DAYS, ChronoUnit.DAYS));
    }

    // --- 详情 -------------------------------------------------------------------

    @Transactional(readOnly = true)
    public TaskDetailResponse detail(Long taskId) {
        return buildDetail(permissionService.requireViewable(taskId));
    }

    // --- 创建 -------------------------------------------------------------------

    /** 创建顶层任务（含派发审核判定与协作成员）。 */
    @Transactional
    public TaskDetailResponse create(Long projectId, CreateTaskRequest request) {
        SecurityUser actor = permissionService.requireAuthenticated();
        Project project = projectPermissionService.requireViewable(projectId);
        projectPermissionService.assertNotArchived(project);
        ProjectRole myRole = permissionService.requireProjectMember(projectId);

        TaskStatus status = resolveInitialStatus(project, request.statusId());
        User primary = resolveAssignee(projectId,
                request.primaryAssigneeId() == null ? actor.id() : request.primaryAssigneeId());
        User deputy = resolveDeputy(projectId, primary, request.deputyAssigneeId());

        Task task = new Task(project, null, status, request.title().trim(), primary, actor.id());
        task.updateInfo(task.getTitle(), normalize(request.description()), priorityOrDefault(request.priority()),
                request.plannedStartAt(), request.plannedEndAt());
        task.changeAssignees(primary, deputy);
        if (request.progressMode() != null) {
            task.changeProgressMode(request.progressMode());
        }
        if (status.getSystemType() == TaskStatusType.ACTIVE) {
            task.changeStatus(status, Instant.now());
        }

        boolean pending = needsAssignmentReview(myRole, primary, actor);
        if (pending) {
            task.markPendingAssignment();
        }
        taskRepository.save(task);
        applyCollaborators(task, request.collaboratorUserIds());

        auditService.record(AuditEntry.action(AuditActions.TASK_CREATED, RiskLevel.NORMAL)
                .resource("TASK", task.getId())
                .after(Map.of(
                        "projectId", projectId,
                        "title", task.getTitle(),
                        "primaryAssigneeId", primary.getId(),
                        "status", status.getName(),
                        "assignmentState", task.getAssignmentState().name()))
                .reason(pending ? "创建任务（待派发审核）" : "创建任务"));
        if (!pending) {
            notifyAssignees(task, NotificationType.TASK_ASSIGNED, "任务已分配",
                    "你被指派为「" + task.getTitle() + "」的负责人", actor.id());
        }
        return buildDetail(task);
    }

    /** 添加一级子任务（返回父任务详情，便于前端直接刷新侧栏）。 */
    @Transactional
    public TaskDetailResponse createSubtask(Long parentId, CreateSubtaskRequest request) {
        SecurityUser actor = permissionService.requireAuthenticated();
        Task parent = permissionService.requireManage(parentId);
        if (parent.isSubtask()) {
            throw new ApiException(ErrorCode.UNPROCESSABLE, "v0.1.0 只支持一级子任务，子任务下不能再创建子任务");
        }
        Long projectId = parent.getProject().getId();
        TaskStatus status = taskStatusService.defaultTodoStatus(projectId);
        User primary = resolveAssignee(projectId,
                request.primaryAssigneeId() == null ? actor.id() : request.primaryAssigneeId());

        Task subtask = new Task(parent.getProject(), parent, status, request.title().trim(), primary, actor.id());
        subtask.updateInfo(subtask.getTitle(), normalize(request.description()), priorityOrDefault(request.priority()),
                request.plannedStartAt(), request.plannedEndAt());

        ProjectRole myRole = projectPermissionService.roleOf(projectId, actor.id());
        boolean pending = needsAssignmentReview(myRole, primary, actor);
        if (pending) {
            subtask.markPendingAssignment();
        }
        taskRepository.save(subtask);

        if (parent.getProgressMode() == ProgressMode.AUTO) {
            recomputeAutoProgress(parent);
        }

        auditService.record(AuditEntry.action(AuditActions.TASK_CREATED, RiskLevel.NORMAL)
                .resource("TASK", subtask.getId())
                .after(Map.of(
                        "projectId", projectId,
                        "parentTaskId", parentId,
                        "title", subtask.getTitle(),
                        "primaryAssigneeId", primary.getId(),
                        "assignmentState", subtask.getAssignmentState().name()))
                .reason("创建子任务"));
        if (!pending) {
            notifyAssignees(subtask, NotificationType.TASK_ASSIGNED, "任务已分配",
                    "你被指派为子任务「" + subtask.getTitle() + "」的负责人", actor.id());
        }
        return buildDetail(parent);
    }

    // --- 编辑 -------------------------------------------------------------------

    /** 编辑基础信息（标题 / 描述 / 优先级 / 计划时间 / 进度模式）。 */
    @Transactional
    public TaskDetailResponse updateInfo(Long taskId, UpdateTaskRequest request) {
        Task task = permissionService.requireManage(taskId);
        task.updateInfo(request.title().trim(), normalize(request.description()), priorityOrDefault(request.priority()),
                request.plannedStartAt(), request.plannedEndAt());
        if (request.progressMode() != null && request.progressMode() != task.getProgressMode()) {
            task.changeProgressMode(request.progressMode());
            if (request.progressMode() == ProgressMode.AUTO) {
                recomputeAutoProgress(task);
            }
        }
        taskRepository.save(task);
        return buildDetail(task);
    }

    /**
     * 状态流转（看板拖拽 / 侧栏切换）。
     *
     * <p>存在未完成前置依赖且本次流转会「开始任务」时：未携带原因 → 409
     * {@code TASK_BLOCKED_BY_DEPENDENCIES}；携带原因 → 记录
     * {@code TASK_OVERRIDE_DEPENDENCY} 审计后放行（权限已由 requireManage 复查）。
     */
    @Transactional
    public TaskDetailResponse changeStatus(Long taskId, ChangeTaskStatusRequest request) {
        SecurityUser actor = permissionService.requireAuthenticated();
        Task task = permissionService.requireManage(taskId);
        TaskStatus target = taskStatusService.requireStatus(task.getProject().getId(), request.statusId());
        TaskStatusType currentType = task.getStatus().getSystemType();
        TaskStatusType targetType = target.getSystemType();
        String fromStatusName = task.getStatus().getName();

        if (task.getStatus().getId().equals(target.getId())) {
            throw ApiException.conflict("任务已处于「" + target.getName() + "」状态");
        }
        validateTransition(currentType, targetType);

        boolean enteringStarted = !currentType.isStarted() && targetType.isStarted()
                && targetType != TaskStatusType.CLOSED;
        if (enteringStarted) {
            List<TaskDependency> unfinished = unfinished(taskDependencyRepository.findByTaskIdWithTarget(taskId));
            if (!unfinished.isEmpty()) {
                String reason = request.overrideReason() == null ? "" : request.overrideReason().trim();
                if (reason.isEmpty()) {
                    throw new ApiException(ErrorCode.TASK_BLOCKED_BY_DEPENDENCIES);
                }
                auditService.record(AuditEntry.action(AuditActions.TASK_OVERRIDE_DEPENDENCY, RiskLevel.ELEVATED)
                        .resource("TASK", taskId)
                        .before(Map.of("status", task.getStatus().getName()))
                        .after(Map.of("status", target.getName(),
                                "unfinishedDependencies", unfinished.size()))
                        .reason(reason));
            }
        }

        boolean firstCompletion = targetType == TaskStatusType.DONE && task.getCompletedAt() == null;
        task.changeStatus(target, Instant.now());
        if (firstCompletion && !isAutoProgress(task)) {
            // 手工模式任务完成时进度记为 100%（AUTO 模式由子任务完成比例计算）
            task.changeProgress(100);
        }
        taskRepository.save(task);

        auditService.record(AuditEntry.action(AuditActions.TASK_STATUS_CHANGED, RiskLevel.NORMAL)
                .resource("TASK", taskId)
                .before(Map.of("status", currentType.name()))
                .after(Map.of("status", targetType.name()))
                .reason("任务状态变更"));
        if (firstCompletion) {
            auditService.record(AuditEntry.action(AuditActions.TASK_COMPLETED, RiskLevel.NORMAL)
                    .resource("TASK", taskId)
                    .after(Map.of("completedAt", String.valueOf(task.getCompletedAt())))
                    .reason("任务完成"));
        }
        notifyAssignees(task, NotificationType.TASK_STATUS_CHANGED, "任务状态变更",
                "「" + task.getTitle() + "」：" + fromStatusName + " → " + target.getName(), actor.id());
        recomputeParentIfAuto(task);
        return buildDetail(task);
    }

    /** 是否处于「AUTO 且存在子任务」的自动进度模式（此时进度不可手工维护）。 */
    private boolean isAutoProgress(Task task) {
        return task.getProgressMode() == ProgressMode.AUTO && taskRepository.countByParentId(task.getId()) > 0;
    }

    /** 手工更新进度（AUTO 模式且存在子任务时拒绝）。 */
    @Transactional
    public TaskDetailResponse changeProgress(Long taskId, int progress) {
        Task task = permissionService.requireManage(taskId);
        if (isAutoProgress(task)) {
            throw ApiException.conflict("进度为自动模式（按子任务完成比例计算），不能手工更新");
        }
        int before = task.getProgress();
        task.changeProgress(progress);
        taskRepository.save(task);

        auditService.record(AuditEntry.action(AuditActions.TASK_PROGRESS_CHANGED, RiskLevel.NORMAL)
                .resource("TASK", taskId)
                .before(Map.of("progress", before))
                .after(Map.of("progress", task.getProgress()))
                .reason("更新任务进度"));
        return buildDetail(task);
    }

    /** 调整负责人（主负责人 / 副负责人；副负责人不能修改主负责人，由 full control 保证）。 */
    @Transactional
    public TaskDetailResponse changeAssignees(Long taskId, UpdateTaskAssigneesRequest request) {
        SecurityUser actor = permissionService.requireAuthenticated();
        Task task = permissionService.requireFullControl(taskId);
        Long projectId = task.getProject().getId();
        User primary = resolveAssignee(projectId, request.primaryAssigneeId());
        User deputy = resolveDeputy(projectId, primary, request.deputyAssigneeId());

        Map<String, Object> before = new HashMap<>();
        before.put("primaryAssigneeId", task.getPrimaryAssignee().getId());
        before.put("deputyAssigneeId",
                task.getDeputyAssignee() == null ? null : task.getDeputyAssignee().getId());

        task.changeAssignees(primary, deputy);
        taskRepository.save(task);

        auditService.record(AuditEntry.action(AuditActions.TASK_ASSIGNEE_CHANGED, RiskLevel.ELEVATED)
                .resource("TASK", taskId)
                .before(before)
                .after(Map.of("primaryAssigneeId", primary.getId(),
                        "deputyAssigneeId", deputy == null ? "" : deputy.getId()))
                .reason("调整任务负责人"));
        notifyAssignees(task, NotificationType.TASK_ASSIGNED, "任务已分配",
                "你被指派为「" + task.getTitle() + "」的负责人", actor.id());
        return buildDetail(task);
    }

    /** 设置协作成员（整体替换；负责人不重复计入）。 */
    @Transactional
    public TaskDetailResponse changeCollaborators(Long taskId, UpdateTaskCollaboratorsRequest request) {
        Task task = permissionService.requireManage(taskId);
        applyCollaborators(task, request.userIds());
        return buildDetail(task);
    }

    // --- 依赖 -------------------------------------------------------------------

    /** 添加前置依赖（同项目、顶层任务、无循环）。 */
    @Transactional
    public TaskDetailResponse addDependency(Long taskId, Long dependsOnTaskId) {
        SecurityUser actor = permissionService.requireAuthenticated();
        Task task = permissionService.requireManage(taskId);
        if (task.isSubtask()) {
            throw new ApiException(ErrorCode.UNPROCESSABLE, "子任务不支持前置依赖，请为顶层任务配置");
        }
        if (taskId.equals(dependsOnTaskId)) {
            throw new ApiException(ErrorCode.UNPROCESSABLE, "任务不能依赖自身");
        }
        Task target = permissionService.requireViewable(dependsOnTaskId);
        if (!target.getProject().getId().equals(task.getProject().getId())) {
            throw new ApiException(ErrorCode.UNPROCESSABLE, "只支持同项目内的任务依赖");
        }
        if (target.isSubtask()) {
            throw new ApiException(ErrorCode.UNPROCESSABLE, "不能依赖子任务，请选择顶层任务");
        }
        if (taskDependencyRepository.findByTaskIdAndDependsOnTaskId(taskId, dependsOnTaskId).isPresent()) {
            throw ApiException.conflict("该依赖已存在");
        }
        assertNoCycle(task, target);
        taskDependencyRepository.save(new TaskDependency(task, target, actor.id()));
        return buildDetail(task);
    }

    /** 移除前置依赖。 */
    @Transactional
    public TaskDetailResponse removeDependency(Long taskId, Long dependsOnTaskId) {
        Task task = permissionService.requireManage(taskId);
        TaskDependency dependency = taskDependencyRepository.findByTaskIdAndDependsOnTaskId(taskId, dependsOnTaskId)
                .orElseThrow(() -> ApiException.notFound("依赖不存在"));
        taskDependencyRepository.delete(dependency);
        return buildDetail(task);
    }

    // --- 派发审核 ---------------------------------------------------------------

    /** 派发审核通过：任务正式生效（进入看板与列表）。 */
    @Transactional
    public TaskDetailResponse approveAssignment(Long taskId) {
        SecurityUser actor = permissionService.requireAuthenticated();
        Task task = permissionService.requireViewable(taskId);
        permissionService.requireProjectManagement(task.getProject().getId());
        if (!task.isPendingAssignment()) {
            throw ApiException.conflict("该任务不在待派发审核状态");
        }
        task.activateAssignment();
        taskRepository.save(task);

        auditService.record(AuditEntry.action(AuditActions.TASK_ASSIGNMENT_APPROVED, RiskLevel.NORMAL)
                .resource("TASK", taskId)
                .after(Map.of("assignmentState", AssignmentState.ACTIVE.name(),
                        "primaryAssigneeId", task.getPrimaryAssignee().getId()))
                .reason("派发审核通过"));
        notifyAssignees(task, NotificationType.TASK_ASSIGNED, "任务派发已通过",
                "「" + task.getTitle() + "」已正式生效", actor.id());
        return buildDetail(task);
    }

    /** 派发审核驳回：任务保留记录但不生效（不进入看板与列表）。 */
    @Transactional
    public TaskDetailResponse rejectAssignment(Long taskId, String reason) {
        Task task = permissionService.requireViewable(taskId);
        permissionService.requireProjectManagement(task.getProject().getId());
        if (!task.isPendingAssignment()) {
            throw ApiException.conflict("该任务不在待派发审核状态");
        }
        String normalized = reason == null || reason.isBlank() ? "派发审核驳回" : reason.trim();
        task.rejectAssignment();
        taskRepository.save(task);

        auditService.record(AuditEntry.action(AuditActions.TASK_ASSIGNMENT_REJECTED, RiskLevel.ELEVATED)
                .resource("TASK", taskId)
                .before(Map.of("assignmentState", AssignmentState.PENDING_ASSIGNMENT.name()))
                .after(Map.of("assignmentState", AssignmentState.REJECTED.name(),
                        "primaryAssigneeId", task.getPrimaryAssignee().getId()))
                .reason(normalized));
        return buildDetail(task);
    }

    // --- 内部方法 ---------------------------------------------------------------

    private TaskCardResponse toCard(Task task, Instant now, Map<Long, List<TaskDependency>> dependencies,
            SecurityUser actor, ProjectRole projectRole) {
        List<TaskDependency> deps = dependencies.getOrDefault(task.getId(), List.of());
        int blockerCount = unfinished(deps).size();
        return TaskCardResponse.from(task, now, blockerCount > 0, blockerCount,
                permissionService.canManage(task, actor, projectRole),
                permissionService.canFullControl(task, actor, projectRole));
    }

    private TaskDetailResponse buildDetail(Task task) {
        SecurityUser actor = permissionService.requireAuthenticated();
        Long projectId = task.getProject().getId();
        ProjectRole myRole = projectPermissionService.roleOf(projectId, actor.id());

        List<TaskDependency> dependencies = taskDependencyRepository.findByTaskIdWithTarget(task.getId());
        int blockerCount = unfinished(dependencies).size();
        List<TaskUserBrief> collaborators = taskCollaboratorRepository.findByTaskIdWithUser(task.getId()).stream()
                .map(collaborator -> TaskUserBrief.from(collaborator.getUser()))
                .toList();
        List<TaskCardResponse> subtasks = task.isSubtask() ? List.of()
                : taskRepository.findByParentIdWithStatus(task.getId()).stream()
                        .map(subtask -> toCard(subtask, Instant.now(), Map.of(), actor, myRole))
                        .toList();

        boolean active = task.isActiveAssignment();
        boolean canManage = active && permissionService.canManage(task, actor, myRole);
        boolean canFullControl = active && permissionService.canFullControl(task, actor, myRole);
        boolean canReview = task.isPendingAssignment() && myRole != null && myRole.isManagement();
        boolean canEditProgress = canManage
                && (task.getProgressMode() == ProgressMode.MANUAL || subtasks.isEmpty());
        boolean canViewCommentHistory = actor.systemRole().isAdminLike()
                || (myRole != null && myRole.isManagement());

        return new TaskDetailResponse(
                task.getId(),
                projectId,
                task.getProject().getName(),
                task.getParent() == null ? null : task.getParent().getId(),
                task.getParent() == null ? null : parentTitle(task),
                task.getTitle(),
                task.getDescription(),
                task.getPriority(),
                TaskStatusView.from(task.getStatus()),
                TaskUserBrief.from(task.getPrimaryAssignee()),
                TaskUserBrief.from(task.getDeputyAssignee()),
                collaborators,
                task.getProgress(),
                task.getProgressMode().name(),
                task.getPlannedStartAt(),
                task.getPlannedEndAt(),
                task.getActualStartAt(),
                task.getCompletedAt(),
                task.getAssignmentState().name(),
                blockerCount > 0,
                blockerCount,
                dependencies.stream().map(TaskDetailResponse.TaskDependencyView::from).toList(),
                subtasks,
                task.getCreatedAt(),
                task.getUpdatedAt(),
                new TaskDetailResponse.Permissions(canManage, canFullControl, canEditProgress, canManage, canManage,
                        canReview, canViewCommentHistory));
    }

    private String parentTitle(Task task) {
        return task.getParent() == null ? null : task.getParent().getTitle();
    }

    /** 任务通知（收件人：主负责人 + 副负责人；触发者本人自动跳过）。 */
    private void notifyAssignees(Task task, NotificationType type, String title, String body, Long actorId) {
        List<Long> recipients = new ArrayList<>();
        recipients.add(task.getPrimaryAssignee().getId());
        if (task.getDeputyAssignee() != null) {
            recipients.add(task.getDeputyAssignee().getId());
        }
        notificationService.notifyAll(recipients, type, title, body, taskLink(task), "TASK", task.getId(), actorId);
    }

    private String taskLink(Task task) {
        return "/projects/" + task.getProject().getId() + "/board?task=" + task.getId();
    }

    /**
     * 状态流校验：向前推进（任意跳步）或回退到 TODO / ACTIVE（打回 / 重新打开）；
     * CLOSED 为终态。
     */
    private void validateTransition(TaskStatusType current, TaskStatusType target) {
        if (current.isTerminal()) {
            throw ApiException.conflict("已关闭的任务不能再变更状态");
        }
        if (target.rank() >= current.rank()) {
            return;
        }
        if (target == TaskStatusType.TODO || target == TaskStatusType.ACTIVE) {
            return;
        }
        throw ApiException.conflict("不允许的状态流转：" + current + " → " + target);
    }

    /** 循环检测：B 依赖 A，若从 A 出发（递归）能回到 B，则形成环。 */
    private void assertNoCycle(Task task, Task target) {
        Map<Long, List<Long>> edges = new HashMap<>();
        for (TaskDependency dependency : taskDependencyRepository
                .findByProjectIdWithTarget(task.getProject().getId())) {
            edges.computeIfAbsent(dependency.getTask().getId(), key -> new ArrayList<>())
                    .add(dependency.getDependsOnTask().getId());
        }
        Deque<Long> stack = new ArrayDeque<>(edges.getOrDefault(target.getId(), List.of()));
        Set<Long> visited = new HashSet<>();
        while (!stack.isEmpty()) {
            Long current = stack.pop();
            if (current.equals(task.getId())) {
                throw ApiException.conflict("检测到循环依赖：该依赖会形成依赖环，已阻止");
            }
            if (visited.add(current)) {
                stack.addAll(edges.getOrDefault(current, List.of()));
            }
        }
    }

    private List<TaskDependency> unfinished(List<TaskDependency> dependencies) {
        return dependencies.stream()
                .filter(dependency -> !dependency.getDependsOnTask().getStatus().getSystemType().isFinished())
                .toList();
    }

    /** 项目内依赖边：taskId → 该任务的前置依赖列表。 */
    private Map<Long, List<TaskDependency>> dependencyMap(Long projectId) {
        Map<Long, List<TaskDependency>> map = new HashMap<>();
        for (TaskDependency dependency : taskDependencyRepository.findByProjectIdWithTarget(projectId)) {
            map.computeIfAbsent(dependency.getTask().getId(), key -> new ArrayList<>()).add(dependency);
        }
        return map;
    }

    /** 子任务完成后重算父任务进度（仅 AUTO 模式且存在子任务）。 */
    private void recomputeParentIfAuto(Task task) {
        if (!task.isSubtask()) {
            return;
        }
        Task parent = task.getParent();
        if (parent.getProgressMode() == ProgressMode.AUTO && taskRepository.countByParentId(parent.getId()) > 0) {
            long done = taskRepository.countByParentIdAndStatusSystemType(parent.getId(), TaskStatusType.DONE);
            parent.changeProgress((int) Math.round(done * 100.0 / taskRepository.countByParentId(parent.getId())));
            taskRepository.save(parent);
        }
    }

    private void recomputeAutoProgress(Task parent) {
        long total = taskRepository.countByParentId(parent.getId());
        if (total == 0) {
            // 没有子任务时按规范回退为手工模式：保留当前进度
            return;
        }
        long done = taskRepository.countByParentIdAndStatusSystemType(parent.getId(), TaskStatusType.DONE);
        parent.changeProgress((int) Math.round(done * 100.0 / total));
        taskRepository.save(parent);
    }

    /** 协作成员整体替换（不重复计入负责人）。 */
    private void applyCollaborators(Task task, List<Long> userIds) {
        Long projectId = task.getProject().getId();
        Set<Long> target = new LinkedHashSet<>();
        if (userIds != null) {
            for (Long userId : userIds) {
                if (userId != null) {
                    target.add(userId);
                }
            }
        }
        target.remove(task.getPrimaryAssignee().getId());
        if (task.getDeputyAssignee() != null) {
            target.remove(task.getDeputyAssignee().getId());
        }

        List<TaskCollaborator> existing = taskCollaboratorRepository.findByTaskId(task.getId());
        Set<Long> existingIds = new HashSet<>();
        for (TaskCollaborator collaborator : existing) {
            existingIds.add(collaborator.getUser().getId());
            if (!target.contains(collaborator.getUser().getId())) {
                taskCollaboratorRepository.delete(collaborator);
            }
        }
        for (Long userId : target) {
            if (!existingIds.contains(userId)) {
                taskCollaboratorRepository.save(new TaskCollaborator(task, resolveAssignee(projectId, userId)));
            }
        }
    }

    private TaskStatus resolveInitialStatus(Project project, Long statusId) {
        TaskStatus status = statusId == null
                ? taskStatusService.defaultTodoStatus(project.getId())
                : taskStatusService.requireStatus(project.getId(), statusId);
        TaskStatusType type = status.getSystemType();
        if (type != TaskStatusType.TODO && type != TaskStatusType.ACTIVE) {
            throw new ApiException(ErrorCode.UNPROCESSABLE, "创建任务时只能选择「待处理」或「进行中」状态");
        }
        return status;
    }

    /** 负责人必须是项目内有效成员。 */
    private User resolveAssignee(Long projectId, Long userId) {
        if (userId == null || !projectMemberRepository.existsByProjectIdAndUserId(projectId, userId)) {
            throw new ApiException(ErrorCode.UNPROCESSABLE, "负责人必须是项目成员");
        }
        User user = userService.getById(userId);
        if (!user.isActive()) {
            throw new ApiException(ErrorCode.UNPROCESSABLE, "账号已被禁用，无法作为负责人");
        }
        return user;
    }

    private User resolveDeputy(Long projectId, User primary, Long deputyId) {
        if (deputyId == null) {
            return null;
        }
        if (deputyId.equals(primary.getId())) {
            throw new ApiException(ErrorCode.UNPROCESSABLE, "副负责人不能与主负责人相同");
        }
        return resolveAssignee(projectId, deputyId);
    }

    /** 派发规则：项目负责人派发立即生效；成员派给他人进入待审核。 */
    private boolean needsAssignmentReview(ProjectRole myRole, User primary, SecurityUser actor) {
        return (myRole == null || !myRole.isManagement()) && !primary.getId().equals(actor.id());
    }

    private TaskPriority priorityOrDefault(TaskPriority priority) {
        return priority == null ? TaskPriority.MEDIUM : priority;
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}