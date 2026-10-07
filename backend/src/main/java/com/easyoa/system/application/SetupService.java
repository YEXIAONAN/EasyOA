package com.easyoa.system.application;

import java.util.Map;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.easyoa.audit.application.AuditEntry;
import com.easyoa.audit.application.AuditService;
import com.easyoa.audit.domain.RiskLevel;
import com.easyoa.auth.application.PasswordPolicy;
import com.easyoa.common.audit.AuditActions;
import com.easyoa.common.config.EasyOaProperties;
import com.easyoa.common.exception.ApiException;
import com.easyoa.common.exception.ErrorCode;
import com.easyoa.securityevent.application.SecurityEventService;
import com.easyoa.securityevent.domain.SecurityEventType;
import com.easyoa.system.dto.SetupInitializeRequest;
import com.easyoa.system.dto.SetupResultResponse;
import com.easyoa.system.dto.SetupStatusResponse;
import com.easyoa.user.application.UserService;
import com.easyoa.user.domain.SystemRole;
import com.easyoa.user.domain.User;

/**
 * 首次初始化：创建组织名称与第一个 ROOT 用户，完成后 {@code /setup} 永久关闭。
 *
 * <p>并发与重复初始化防护（三层）：
 * <ol>
 *   <li>已存在任何用户 → 拒绝；</li>
 *   <li>数据库原子开关 {@code setup.completed}: false → true，只有一个请求能成功；</li>
 *   <li>失败路径整体回滚，不会留下半初始化状态。</li>
 * </ol>
 */
@Service
public class SetupService {

    private final SystemSettingService systemSettingService;
    private final UserService userService;
    private final PasswordPolicy passwordPolicy;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;
    private final SecurityEventService securityEventService;
    private final EasyOaProperties properties;

    public SetupService(SystemSettingService systemSettingService, UserService userService,
            PasswordPolicy passwordPolicy, PasswordEncoder passwordEncoder, AuditService auditService,
            SecurityEventService securityEventService, EasyOaProperties properties) {
        this.systemSettingService = systemSettingService;
        this.userService = userService;
        this.passwordPolicy = passwordPolicy;
        this.passwordEncoder = passwordEncoder;
        this.auditService = auditService;
        this.securityEventService = securityEventService;
        this.properties = properties;
    }

    @Transactional(readOnly = true)
    public SetupStatusResponse status() {
        boolean hasUsers = userService.countUsers() > 0;
        boolean completed = systemSettingService.isSetupCompleted();
        return new SetupStatusResponse(
                !completed && !hasUsers,
                systemSettingService.organizationName(properties.getSetup().getOrganizationName()));
    }

    @Transactional
    public SetupResultResponse initialize(SetupInitializeRequest request) {
        if (userService.countUsers() > 0 || systemSettingService.isSetupCompleted()) {
            throw new ApiException(ErrorCode.SETUP_ALREADY_COMPLETED);
        }

        String username = request.username().trim();
        String organizationName = request.organizationName().trim();
        passwordPolicy.validate(username, request.password());

        User root = userService.create(username, request.displayName().trim(),
                passwordEncoder.encode(request.password()), SystemRole.ROOT);

        if (!systemSettingService.markSetupCompleted(root.getId())) {
            throw new ApiException(ErrorCode.SETUP_ALREADY_COMPLETED);
        }
        systemSettingService.setValue(SystemSettingService.KEY_ORGANIZATION_NAME, organizationName, root.getId());

        auditService.record(AuditEntry.action(AuditActions.SETUP_INITIALIZED, RiskLevel.CRITICAL)
                .actor(root.getId(), root.getUsername())
                .after(Map.of("organizationName", organizationName, "rootUsername", root.getUsername()))
                .reason("首次初始化系统并创建 ROOT 用户"));
        securityEventService.record(SecurityEventType.SETUP_COMPLETED, "INFO",
                "系统完成首次初始化，ROOT 用户：" + root.getUsername(),
                Map.of("organizationName", organizationName, "rootUsername", root.getUsername()));

        return new SetupResultResponse(organizationName, root.getUsername());
    }
}