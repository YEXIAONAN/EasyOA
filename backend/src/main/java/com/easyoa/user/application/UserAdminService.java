package com.easyoa.user.application;

import java.util.HashMap;
import java.util.Map;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.easyoa.audit.application.AuditEntry;
import com.easyoa.audit.application.AuditService;
import com.easyoa.audit.domain.RiskLevel;
import com.easyoa.auth.application.PasswordPolicy;
import com.easyoa.common.audit.AuditActions;
import com.easyoa.common.exception.ApiException;
import com.easyoa.common.requestid.RequestContext;
import com.easyoa.common.security.SecurityUser;
import com.easyoa.organization.application.OrgMembershipService;
import com.easyoa.user.domain.SystemRole;
import com.easyoa.user.domain.User;
import com.easyoa.user.domain.UserStatus;
import com.easyoa.user.domain.event.UserAccessChangedEvent;
import com.easyoa.user.dto.ChangeUserRoleRequest;
import com.easyoa.user.dto.CreateUserRequest;
import com.easyoa.user.dto.MemberProfileResponse;
import com.easyoa.user.repository.UserRepository;

/**
 * 账号管理（创建 / 启用禁用 / 角色变更）。
 *
 * <p>安全不变式：
 * <ul>
 *   <li>系统必须始终保留至少一个可用的 ROOT（防止自锁）；</li>
 *   <li>ADMIN 不能操作 ROOT / ADMIN 账号，也不能创建管理员；</li>
 *   <li>禁用账号或变更角色后立即撤销该用户全部会话（会话内角色缓存必须失效）。</li>
 * </ul>
 */
@Service
public class UserAdminService {

    private final UserRepository userRepository;
    private final UserService userService;
    private final UserDirectoryService userDirectoryService;
    private final UserPermissionService userPermissionService;
    private final OrgMembershipService orgMembershipService;
    private final PasswordPolicy passwordPolicy;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;
    private final ApplicationEventPublisher eventPublisher;

    public UserAdminService(UserRepository userRepository, UserService userService,
            UserDirectoryService userDirectoryService, UserPermissionService userPermissionService,
            OrgMembershipService orgMembershipService, PasswordPolicy passwordPolicy, PasswordEncoder passwordEncoder,
            AuditService auditService, ApplicationEventPublisher eventPublisher) {
        this.userRepository = userRepository;
        this.userService = userService;
        this.userDirectoryService = userDirectoryService;
        this.userPermissionService = userPermissionService;
        this.orgMembershipService = orgMembershipService;
        this.passwordPolicy = passwordPolicy;
        this.passwordEncoder = passwordEncoder;
        this.auditService = auditService;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public MemberProfileResponse create(CreateUserRequest request) {
        SecurityUser actor = RequestContext.currentUser();
        userPermissionService.requireCreateUser(actor, request.systemRole());

        String username = request.username().trim();
        passwordPolicy.validate(username, request.initialPassword());

        User user = userService.create(username, request.displayName().trim(),
                passwordEncoder.encode(request.initialPassword()), request.systemRole());
        user.setJobTitle(normalize(request.jobTitle()));
        user.setEmail(normalize(request.email()));
        user.setPhone(normalize(request.phone()));
        userRepository.save(user);

        if (request.orgUnitId() != null) {
            orgMembershipService.addMember(request.orgUnitId(), user.getId());
        }

        auditService.record(AuditEntry.action(AuditActions.USER_CREATED, RiskLevel.ELEVATED)
                .resource("USER", user.getId())
                .after(Map.of(
                        "username", user.getUsername(),
                        "displayName", user.getDisplayName(),
                        "systemRole", user.getSystemRole().name(),
                        "orgUnitId", String.valueOf(request.orgUnitId())))
                .reason("创建成员账号"));
        return userDirectoryService.profile(user.getId());
    }

    @Transactional
    public MemberProfileResponse changeStatus(Long userId, UserStatus status) {
        SecurityUser actor = RequestContext.currentUser();
        User target = userService.getById(userId);
        userPermissionService.requireManageAccount(actor, target);

        if (status == UserStatus.DISABLED) {
            if (target.getSystemRole() == SystemRole.ROOT && activeRootCount() <= 1) {
                throw ApiException.conflict("系统必须保留至少一个可用的 ROOT 账号");
            }
            if (!target.isActive()) {
                return userDirectoryService.profile(userId);
            }
            target.disable();
            userRepository.save(target);
            eventPublisher.publishEvent(new UserAccessChangedEvent(userId, "USER_DISABLED"));
            auditService.record(AuditEntry.action(AuditActions.USER_DISABLED, RiskLevel.ELEVATED)
                    .resource("USER", userId)
                    .before(Map.of("status", UserStatus.ACTIVE.name()))
                    .after(Map.of("status", UserStatus.DISABLED.name()))
                    .reason("禁用账号"));
        } else {
            if (target.isActive()) {
                return userDirectoryService.profile(userId);
            }
            target.enable();
            userRepository.save(target);
            auditService.record(AuditEntry.action(AuditActions.USER_ENABLED, RiskLevel.ELEVATED)
                    .resource("USER", userId)
                    .before(Map.of("status", UserStatus.DISABLED.name()))
                    .after(Map.of("status", UserStatus.ACTIVE.name()))
                    .reason("启用账号"));
        }
        return userDirectoryService.profile(userId);
    }

    @Transactional
    public MemberProfileResponse changeRole(Long userId, ChangeUserRoleRequest request) {
        userPermissionService.requireChangeRole(null);
        User target = userService.getById(userId);
        SystemRole newRole = request.systemRole();
        if (target.getSystemRole() == newRole) {
            return userDirectoryService.profile(userId);
        }
        if (target.getSystemRole() == SystemRole.ROOT && newRole != SystemRole.ROOT && activeRootCount() <= 1) {
            throw ApiException.conflict("系统必须保留至少一个 ROOT 账号");
        }

        Map<String, Object> before = new HashMap<>();
        before.put("systemRole", target.getSystemRole().name());
        target.setSystemRole(newRole);
        userRepository.save(target);

        // 会话中缓存了旧角色：撤销全部会话，强制其重新登录获取新权限
        eventPublisher.publishEvent(new UserAccessChangedEvent(userId, "USER_ROLE_CHANGED"));

        RiskLevel risk = newRole == SystemRole.ROOT || before.get("systemRole").equals(SystemRole.ROOT.name())
                ? RiskLevel.CRITICAL
                : RiskLevel.ELEVATED;
        auditService.record(AuditEntry.action(AuditActions.USER_ROLE_CHANGED, risk)
                .resource("USER", userId)
                .before(before)
                .after(Map.of("systemRole", newRole.name()))
                .reason("变更系统角色"));
        return userDirectoryService.profile(userId);
    }

    private long activeRootCount() {
        return userRepository.countBySystemRoleAndStatus(SystemRole.ROOT, UserStatus.ACTIVE);
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}