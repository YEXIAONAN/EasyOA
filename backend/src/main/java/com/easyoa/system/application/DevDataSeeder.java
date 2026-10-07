package com.easyoa.system.application;

import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.easyoa.user.application.UserService;
import com.easyoa.user.domain.SystemRole;
import com.easyoa.user.domain.User;

/**
 * 开发环境种子数据（仅 {@code dev} profile 生效）。
 *
 * <p>生产环境（prod profile）绝不会自动创建任何演示数据。
 *
 * <p>Phase 2 / 3 / 4 起会在此基础上扩展：组织树（技术部 / 产品部 / Zero Lab）、
 * 演示项目与演示任务。
 */
@Component
@Profile("dev")
@ConditionalOnProperty(prefix = "easyoa.dev-seed", name = "enabled", havingValue = "true", matchIfMissing = true)
public class DevDataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DevDataSeeder.class);

    /** 仅用于本地开发的初始密码，生产环境不存在该账号。 */
    private static final String DEV_PASSWORD = "EasyOA@2026";

    private final UserService userService;
    private final SystemSettingService systemSettingService;
    private final PasswordEncoder passwordEncoder;

    public DevDataSeeder(UserService userService, SystemSettingService systemSettingService,
            PasswordEncoder passwordEncoder) {
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

        User root = userService.create("root", "Waiting", passwordEncoder.encode(DEV_PASSWORD), SystemRole.ROOT);
        userService.create("admin", "Admin", passwordEncoder.encode(DEV_PASSWORD), SystemRole.ADMIN);
        userService.create("member", "Member", passwordEncoder.encode(DEV_PASSWORD), SystemRole.MEMBER);

        systemSettingService.setValue(SystemSettingService.KEY_ORGANIZATION_NAME, "Easy Studio", root.getId());
        systemSettingService.markSetupCompleted(root.getId());

        log.info("开发环境种子数据已写入：root / admin / member（初始密码见 README，请勿用于生产） createdAt={}",
                Instant.now());
    }
}