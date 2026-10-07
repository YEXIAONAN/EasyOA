package com.easyoa.system.application;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.easyoa.approval.application.ApprovalSchemaCodec;
import com.easyoa.approval.domain.ApprovalTemplate;
import com.easyoa.approval.domain.ApprovalTemplateVersion;
import com.easyoa.approval.domain.ApproverRuleType;
import com.easyoa.approval.domain.NodeMode;
import com.easyoa.approval.dto.ApproverRuleView;
import com.easyoa.approval.dto.FormFieldView;
import com.easyoa.approval.dto.NodeDefinitionView;
import com.easyoa.approval.repository.ApprovalTemplateRepository;
import com.easyoa.approval.repository.ApprovalTemplateVersionRepository;
import com.easyoa.organization.domain.OrgMembership;
import com.easyoa.organization.domain.OrgUnit;
import com.easyoa.organization.domain.OrgUnitType;
import com.easyoa.organization.repository.OrgMembershipRepository;
import com.easyoa.organization.repository.OrgUnitRepository;
import com.easyoa.project.domain.Project;
import com.easyoa.project.domain.ProjectMember;
import com.easyoa.project.domain.ProjectRole;
import com.easyoa.project.domain.ProjectStatus;
import com.easyoa.project.repository.ProjectMemberRepository;
import com.easyoa.project.repository.ProjectRepository;
import com.easyoa.task.application.TaskStatusService;
import com.easyoa.task.domain.ProgressMode;
import com.easyoa.task.domain.Task;
import com.easyoa.task.domain.TaskCollaborator;
import com.easyoa.task.domain.TaskDependency;
import com.easyoa.task.domain.TaskPriority;
import com.easyoa.task.domain.TaskStatus;
import com.easyoa.task.domain.TaskStatusType;
import com.easyoa.task.repository.TaskCollaboratorRepository;
import com.easyoa.task.repository.TaskDependencyRepository;
import com.easyoa.task.repository.TaskRepository;
import com.easyoa.task.repository.TaskStatusRepository;
import com.easyoa.user.application.UserService;
import com.easyoa.user.domain.SystemRole;
import com.easyoa.user.domain.User;
import com.easyoa.user.repository.UserRepository;

/**
 * 开发环境种子数据（仅 {@code dev} profile 且 {@code easyoa.dev-seed.enabled=true} 时生效）。
 *
 * <p>生产环境（prod profile）绝不会自动创建任何演示数据。
 *
 * <p>说明：这里直接使用仓储写入演示数据——应用启动阶段不存在请求上下文，
 * 不能通过带权限校验的应用服务创建数据（演示数据仅存在于本地开发库）。
 */
@Component
@Profile("dev")
@ConditionalOnProperty(prefix = "easyoa.dev-seed", name = "enabled", havingValue = "true", matchIfMissing = true)
public class DevDataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DevDataSeeder.class);

    /** 仅用于本地开发的初始密码，生产环境不存在这些账号。 */
    private static final String DEV_PASSWORD = "EasyOA@2026";

    private final UserRepository userRepository;
    private final OrgUnitRepository orgUnitRepository;
    private final OrgMembershipRepository orgMembershipRepository;
    private final ProjectRepository projectRepository;
    private final ProjectMemberRepository projectMemberRepository;
    private final TaskStatusRepository taskStatusRepository;
    private final TaskRepository taskRepository;
    private final TaskDependencyRepository taskDependencyRepository;
    private final TaskCollaboratorRepository taskCollaboratorRepository;
    private final TaskStatusService taskStatusService;
    private final ApprovalTemplateRepository approvalTemplateRepository;
    private final ApprovalTemplateVersionRepository approvalTemplateVersionRepository;
    private final ApprovalSchemaCodec approvalSchemaCodec;
    private final UserService userService;
    private final SystemSettingService systemSettingService;
    private final PasswordEncoder passwordEncoder;

    public DevDataSeeder(UserRepository userRepository, OrgUnitRepository orgUnitRepository,
            OrgMembershipRepository orgMembershipRepository, ProjectRepository projectRepository,
            ProjectMemberRepository projectMemberRepository, TaskStatusRepository taskStatusRepository,
            TaskRepository taskRepository, TaskDependencyRepository taskDependencyRepository,
            TaskCollaboratorRepository taskCollaboratorRepository, TaskStatusService taskStatusService,
            ApprovalTemplateRepository approvalTemplateRepository,
            ApprovalTemplateVersionRepository approvalTemplateVersionRepository,
            ApprovalSchemaCodec approvalSchemaCodec,
            UserService userService, SystemSettingService systemSettingService,
            PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.orgUnitRepository = orgUnitRepository;
        this.orgMembershipRepository = orgMembershipRepository;
        this.projectRepository = projectRepository;
        this.projectMemberRepository = projectMemberRepository;
        this.taskStatusRepository = taskStatusRepository;
        this.taskRepository = taskRepository;
        this.taskDependencyRepository = taskDependencyRepository;
        this.taskCollaboratorRepository = taskCollaboratorRepository;
        this.taskStatusService = taskStatusService;
        this.approvalTemplateRepository = approvalTemplateRepository;
        this.approvalTemplateVersionRepository = approvalTemplateVersionRepository;
        this.approvalSchemaCodec = approvalSchemaCodec;
        this.userService = userService;
        this.systemSettingService = systemSettingService;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (systemSettingService.isSetupCompleted() || userService.countUsers() > 0) {
            log.info("系统已初始化，跳过开发环境种子数据");
            return;
        }

        // --- 成员 -------------------------------------------------------------
        User root = createUser("root", "Waiting", "系统负责人", SystemRole.ROOT);
        User admin = createUser("admin", "Admin", "运维管理员", SystemRole.ADMIN);
        User backend = createUser("member", "Member", "后端工程师", SystemRole.MEMBER);
        User frontend = createUser("kevin", "Kevin", "前端工程师", SystemRole.MEMBER);
        User product = createUser("linda", "Linda", "产品经理", SystemRole.MEMBER);

        // --- 组织树（示范：Easy Studio → 技术部 / 产品部 / Zero Lab）--------------
        OrgUnit tech = createUnit(null, "技术部", OrgUnitType.DEPARTMENT, 0, admin);
        OrgUnit backendTeam = createUnit(tech, "后端组", OrgUnitType.TEAM, 0, backend);
        OrgUnit frontendTeam = createUnit(tech, "前端组", OrgUnitType.TEAM, 1, frontend);
        OrgUnit productDept = createUnit(null, "产品部", OrgUnitType.DEPARTMENT, 1, product);
        OrgUnit zeroLab = createUnit(null, "Zero Lab", OrgUnitType.TEAM, 2, root);

        // --- 组织归属（首个归属自动成为主部门） -----------------------------------
        join(root, tech);
        join(root, zeroLab);
        join(admin, tech);
        join(backend, backendTeam);
        join(backend, tech);
        join(frontend, frontendTeam);
        join(frontend, tech);
        join(product, productDept);

        systemSettingService.setValue(SystemSettingService.KEY_ORGANIZATION_NAME, "Easy Studio", root.getId());
        systemSettingService.markSetupCompleted(root.getId());

        // --- 演示项目（Phase 3） --------------------------------------------------
        Project demo = new Project("EasyOA", "EasyOA v0.1.0 交付：账号与权限、组织架构、项目协作", root.getId());
        demo.changeProgress(35);
        demo.changeStatus(ProjectStatus.ACTIVE);
        demo.updateInfo("EasyOA", "EasyOA v0.1.0 交付：账号与权限、组织架构、项目协作",
                Instant.now().minus(20, ChronoUnit.DAYS), Instant.now().plus(40, ChronoUnit.DAYS));
        projectRepository.save(demo);
        projectMemberRepository.save(new ProjectMember(demo, root, ProjectRole.OWNER));
        projectMemberRepository.save(new ProjectMember(demo, admin, ProjectRole.DEPUTY_OWNER));
        projectMemberRepository.save(new ProjectMember(demo, backend, ProjectRole.MEMBER));
        projectMemberRepository.save(new ProjectMember(demo, frontend, ProjectRole.MEMBER));
        projectMemberRepository.save(new ProjectMember(demo, product, ProjectRole.MEMBER));

        // --- 演示任务（Phase 4：状态流 / 子任务 / 依赖 / 看板） ---------------------
        seedTasks(demo, root, admin, backend, frontend, product);

        // --- 演示审批模板（Phase 6：动态审批人 / 自我审批禁止 / 多节点流转） ---------
        seedApprovalTemplates(root);

        log.info("开发环境种子数据已写入：root / admin / member / kevin / linda，"
                + "组织树：技术部（后端组 / 前端组）、产品部、Zero Lab，"
                + "演示项目：EasyOA（OWNER=root，含演示任务与依赖）createdAt={}",
                Instant.now());
    }

    /** 演示任务：覆盖已完成 / 进行中 / 待审核 / 待处理、AUTO 进度子任务与依赖阻塞。 */
    private void seedTasks(Project project, User root, User admin, User backend, User frontend, User product) {
        taskStatusService.ensureDefaultStatuses(project);
        List<TaskStatus> statuses = taskStatusRepository.findByProjectIdOrderBySortOrderAscIdAsc(project.getId());
        TaskStatus todo = statusOf(statuses, TaskStatusType.TODO);
        TaskStatus active = statusOf(statuses, TaskStatusType.ACTIVE);
        TaskStatus review = statusOf(statuses, TaskStatusType.REVIEW);
        TaskStatus done = statusOf(statuses, TaskStatusType.DONE);

        Instant now = Instant.now();
        seedTask(project, done, "账号、权限与安全审计", "Session 认证、CSRF、首次初始化与审计留痕。",
                TaskPriority.HIGH, root, null, ProgressMode.MANUAL, 100,
                now.minus(20, ChronoUnit.DAYS), now.minus(4, ChronoUnit.DAYS), root);
        seedTask(project, done, "组织架构与成员目录", "组织树、多组织归属与成员档案。",
                TaskPriority.MEDIUM, backend, admin, ProgressMode.MANUAL, 100,
                now.minus(18, ChronoUnit.DAYS), now.minus(6, ChronoUnit.DAYS), root);
        seedTask(project, active, "项目协作与工作台", "项目生命周期、角色权限与工作台聚合。",
                TaskPriority.HIGH, admin, root, ProgressMode.MANUAL, 70,
                now.minus(12, ChronoUnit.DAYS), now.plus(5, ChronoUnit.DAYS), root);

        Task taskModule = seedTask(project, active, "任务执行模块（看板与依赖）",
                "任务状态流、子任务、依赖阻塞与看板拖拽。",
                TaskPriority.URGENT, backend, null, ProgressMode.AUTO, 33,
                now.minus(3, ChronoUnit.DAYS), now.plus(12, ChronoUnit.DAYS), root);
        // AUTO 模式子任务（1/3 完成 → 进度 33%）
        seedSubtask(project, taskModule, done, "V4 数据模型与迁移", backend, root);
        seedSubtask(project, taskModule, active, "状态流与依赖阻塞", frontend, root);
        seedSubtask(project, taskModule, todo, "看板拖拽与任务侧栏", frontend, root);

        Task dataCenter = seedTask(project, review, "数据中心与统计看板", "项目健康度、任务趋势与成员负载。",
                TaskPriority.MEDIUM, frontend, null, ProgressMode.MANUAL, 90,
                now.minus(5, ChronoUnit.DAYS), now.plus(2, ChronoUnit.DAYS), root);
        // 协作成员示例：root 以协作成员身份参与（「我的任务」跨角色聚合）
        taskCollaboratorRepository.save(new TaskCollaborator(dataCenter, root));
        Task approval = seedTask(project, todo, "审批与公文流转", "审批模板、节点流转与历史。",
                TaskPriority.HIGH, product, null, ProgressMode.MANUAL, 0,
                now.plus(10, ChronoUnit.DAYS), now.plus(30, ChronoUnit.DAYS), root);
        Task mobile = seedTask(project, todo, "移动端适配调研", "响应式布局与移动端交互调研。",
                TaskPriority.LOW, product, null, ProgressMode.MANUAL, 0,
                now.plus(14, ChronoUnit.DAYS), now.plus(21, ChronoUnit.DAYS), root);

        // 前置依赖（未完成 → 看板卡片显示 BLOCKED）
        taskDependencyRepository.save(new TaskDependency(approval, taskModule, root.getId()));
        taskDependencyRepository.save(new TaskDependency(mobile, dataCenter, root.getId()));
    }

    /** 演示审批模板：请假申请（直属主管 → 管理员备案）与采购申请（直属主管 → 财务/管理员）。 */
    private void seedApprovalTemplates(User root) {
        if (approvalTemplateRepository.count() > 0) {
            return;
        }
        // 请假申请
        ApprovalTemplate leave = new ApprovalTemplate("请假申请",
                "员工请假：直属主管审批 → 管理员备案（动态审批人 DIRECT_MANAGER）", root.getId());
        approvalTemplateRepository.save(leave);
        String leaveForm = approvalSchemaCodec.writeFormSchema(List.of(
                new FormFieldView("reason", "请假事由", "TEXTAREA", true, null, null),
                new FormFieldView("leaveType", "请假类型", "SELECT", true, List.of("事假", "病假", "年假"), null),
                new FormFieldView("startDate", "开始日期", "DATE", true, null, null),
                new FormFieldView("endDate", "结束日期", "DATE", true, null, null)));
        String leaveNodes = approvalSchemaCodec.writeNodeSchema(List.of(
                new NodeDefinitionView("直属主管审批", NodeMode.ANY_ONE,
                        List.of(new ApproverRuleView(ApproverRuleType.DIRECT_MANAGER, null, null, null, null))),
                new NodeDefinitionView("管理员备案", NodeMode.ANY_ONE,
                        List.of(new ApproverRuleView(ApproverRuleType.SYSTEM_ROLE, null, "ADMIN", null, null)))));
        publishSeededTemplate(leave, leaveForm, leaveNodes, root);

        // 采购申请
        ApprovalTemplate purchase = new ApprovalTemplate("采购申请",
                "设备 / 物料采购：直属主管审批 → 管理员审批（含金额与用途）", root.getId());
        approvalTemplateRepository.save(purchase);
        String purchaseForm = approvalSchemaCodec.writeFormSchema(List.of(
                new FormFieldView("item", "采购物品", "TEXT", true, null, null),
                new FormFieldView("amount", "采购金额", "MONEY", true, null, null),
                new FormFieldView("usage", "用途说明", "TEXTAREA", true, null, null)));
        String purchaseNodes = approvalSchemaCodec.writeNodeSchema(List.of(
                new NodeDefinitionView("直属主管审批", NodeMode.ANY_ONE,
                        List.of(new ApproverRuleView(ApproverRuleType.DIRECT_MANAGER, null, null, null, null))),
                new NodeDefinitionView("管理员审批", NodeMode.ANY_ONE,
                        List.of(new ApproverRuleView(ApproverRuleType.SYSTEM_ROLE, null, "ADMIN", null, null)))));
        publishSeededTemplate(purchase, purchaseForm, purchaseNodes, root);
    }

    private void publishSeededTemplate(ApprovalTemplate template, String formSchema, String nodeSchema, User actor) {
        ApprovalTemplateVersion version = approvalTemplateVersionRepository.save(new ApprovalTemplateVersion(
                template, 1, template.getName(), template.getDescription(), formSchema, nodeSchema, actor.getId()));
        template.publishVersion(version.getVersionNo());
        approvalTemplateRepository.save(template);
    }

    private TaskStatus statusOf(List<TaskStatus> statuses, TaskStatusType type) {
        return statuses.stream()
                .filter(status -> status.getSystemType() == type)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("缺少默认任务状态：" + type));
    }

    private Task seedTask(Project project, TaskStatus status, String title, String description,
            TaskPriority priority, User primary, User deputy, ProgressMode mode, int progress,
            Instant plannedStart, Instant plannedEnd, User actor) {
        Task task = new Task(project, null, status, title, primary, actor.getId());
        task.updateInfo(title, description, priority, plannedStart, plannedEnd);
        task.changeProgressMode(mode);
        task.changeProgress(progress);
        if (deputy != null) {
            task.changeAssignees(primary, deputy);
        }
        if (status.getSystemType() == TaskStatusType.ACTIVE || status.getSystemType() == TaskStatusType.DONE) {
            // 自动记录 actual_start_at / completed_at
            task.changeStatus(status, Instant.now());
        }
        return taskRepository.save(task);
    }

    private void seedSubtask(Project project, Task parent, TaskStatus status, String title, User primary, User actor) {
        Task subtask = new Task(project, parent, status, title, primary, actor.getId());
        subtask.updateInfo(title, null, TaskPriority.MEDIUM, null, parent.getPlannedEndAt());
        if (status.getSystemType() == TaskStatusType.ACTIVE || status.getSystemType() == TaskStatusType.DONE) {
            subtask.changeStatus(status, Instant.now());
        }
        taskRepository.save(subtask);
    }

    private User createUser(String username, String displayName, String jobTitle, SystemRole role) {
        User user = userService.create(username, displayName, passwordEncoder.encode(DEV_PASSWORD), role);
        user.setJobTitle(jobTitle);
        return userRepository.save(user);
    }

    private OrgUnit createUnit(OrgUnit parent, String name, OrgUnitType type, int sortOrder, User manager) {
        OrgUnit unit = new OrgUnit(parent == null ? null : parent.getId(), name, type, sortOrder, manager.getId());
        return orgUnitRepository.save(unit);
    }

    private void join(User user, OrgUnit unit) {
        boolean hasPrimary = orgMembershipRepository.findFirstByUserIdAndPrimaryTrue(user.getId()).isPresent();
        orgMembershipRepository.save(new OrgMembership(user, unit, !hasPrimary));
    }
}