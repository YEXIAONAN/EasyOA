package com.easyoa.system.application;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

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
    private final UserService userService;
    private final SystemSettingService systemSettingService;
    private final PasswordEncoder passwordEncoder;

    public DevDataSeeder(UserRepository userRepository, OrgUnitRepository orgUnitRepository,
            OrgMembershipRepository orgMembershipRepository, ProjectRepository projectRepository,
            ProjectMemberRepository projectMemberRepository, UserService userService,
            SystemSettingService systemSettingService, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.orgUnitRepository = orgUnitRepository;
        this.orgMembershipRepository = orgMembershipRepository;
        this.projectRepository = projectRepository;
        this.projectMemberRepository = projectMemberRepository;
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

        log.info("开发环境种子数据已写入：root / admin / member / kevin / linda，"
                + "组织树：技术部（后端组 / 前端组）、产品部、Zero Lab，演示项目：EasyOA（OWNER=root）createdAt={}",
                Instant.now());
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