package com.easyoa.security.application;

import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.easyoa.audit.application.AuditEntry;
import com.easyoa.audit.application.AuditService;
import com.easyoa.audit.domain.RiskLevel;
import com.easyoa.common.audit.AuditActions;
import com.easyoa.common.config.EasyOaProperties;
import com.easyoa.common.exception.ApiException;
import com.easyoa.common.exception.ErrorCode;
import com.easyoa.security.dto.SecurityPolicyView;
import com.easyoa.system.application.SystemSettingService;

/**
 * 安全策略读写（登录保护 / 密码强度 / 管理员动态口令要求）。
 *
 * <p>策略存储在 {@code system_settings}（与初始化开关同源），
 * 修改入口只有一个：{@code SensitiveOperationService} 的 SECURITY_POLICY_CHANGE 高危操作，
 * 因此本服务的 {@code update} 不会暴露给普通管理接口。
 *
 * <p>策略值实时生效：登录保护与密码强度校验每次都读取数据库当前值，
 * 避免「改了设置但要重启才生效」的假实现。
 */
@Service
public class SecuritySettingsService {

    public static final String KEY_LOGIN_MAX_FAILURES = "security.login_max_failures";
    public static final String KEY_LOGIN_LOCK_MINUTES = "security.login_lock_minutes";
    public static final String KEY_PASSWORD_MIN_LENGTH = "security.password_min_length";
    public static final String KEY_TOTP_REQUIRED_FOR_ADMINS = "security.totp_required_for_admins";

    private static final int DEFAULT_PASSWORD_MIN_LENGTH = 10;
    private static final int MIN_LOGIN_MAX_FAILURES = 3;
    private static final int MAX_LOGIN_MAX_FAILURES = 20;
    private static final int MIN_LOCK_MINUTES = 1;
    private static final int MAX_LOCK_MINUTES = 1440;
    private static final int MIN_PASSWORD_MIN_LENGTH = 8;
    private static final int MAX_PASSWORD_MIN_LENGTH = 64;

    private final SystemSettingService systemSettingService;
    private final AuditService auditService;
    private final EasyOaProperties properties;

    public SecuritySettingsService(SystemSettingService systemSettingService, AuditService auditService,
            EasyOaProperties properties) {
        this.systemSettingService = systemSettingService;
        this.auditService = auditService;
        this.properties = properties;
    }

    @Transactional(readOnly = true)
    public SecurityPolicyView current() {
        return new SecurityPolicyView(
                intValue(KEY_LOGIN_MAX_FAILURES, properties.getSecurity().getLoginMaxFailures()),
                intValue(KEY_LOGIN_LOCK_MINUTES, properties.getSecurity().getLoginLockMinutes()),
                intValue(KEY_PASSWORD_MIN_LENGTH, DEFAULT_PASSWORD_MIN_LENGTH),
                booleanValue(KEY_TOTP_REQUIRED_FOR_ADMINS, false));
    }

    /** 校验并写入策略（只允许由高危操作通道调用）。 */
    @Transactional
    public SecurityPolicyView update(SecurityPolicyView policy, Long actorId) {
        validate(policy);
        SecurityPolicyView before = current();
        systemSettingService.setValue(KEY_LOGIN_MAX_FAILURES, String.valueOf(policy.loginMaxFailures()), actorId);
        systemSettingService.setValue(KEY_LOGIN_LOCK_MINUTES, String.valueOf(policy.loginLockMinutes()), actorId);
        systemSettingService.setValue(KEY_PASSWORD_MIN_LENGTH, String.valueOf(policy.passwordMinLength()), actorId);
        systemSettingService.setValue(KEY_TOTP_REQUIRED_FOR_ADMINS, String.valueOf(policy.totpRequiredForAdmins()),
                actorId);

        auditService.record(AuditEntry.action(AuditActions.SECURITY_POLICY_CHANGED, RiskLevel.CRITICAL)
                .resource("SYSTEM_SETTING", KEY_LOGIN_MAX_FAILURES)
                .before(Map.of(
                        "loginMaxFailures", before.loginMaxFailures(),
                        "loginLockMinutes", before.loginLockMinutes(),
                        "passwordMinLength", before.passwordMinLength(),
                        "totpRequiredForAdmins", before.totpRequiredForAdmins()))
                .after(Map.of(
                        "loginMaxFailures", policy.loginMaxFailures(),
                        "loginLockMinutes", policy.loginLockMinutes(),
                        "passwordMinLength", policy.passwordMinLength(),
                        "totpRequiredForAdmins", policy.totpRequiredForAdmins()))
                .reason("ROOT 修改安全策略"));
        return current();
    }

    @Transactional(readOnly = true)
    public int loginMaxFailures() {
        return intValue(KEY_LOGIN_MAX_FAILURES, properties.getSecurity().getLoginMaxFailures());
    }

    @Transactional(readOnly = true)
    public int loginLockMinutes() {
        return intValue(KEY_LOGIN_LOCK_MINUTES, properties.getSecurity().getLoginLockMinutes());
    }

    @Transactional(readOnly = true)
    public int passwordMinLength() {
        return intValue(KEY_PASSWORD_MIN_LENGTH, DEFAULT_PASSWORD_MIN_LENGTH);
    }

    @Transactional(readOnly = true)
    public boolean totpRequiredForAdmins() {
        return booleanValue(KEY_TOTP_REQUIRED_FOR_ADMINS, false);
    }

    private void validate(SecurityPolicyView policy) {
        if (policy.loginMaxFailures() < MIN_LOGIN_MAX_FAILURES || policy.loginMaxFailures() > MAX_LOGIN_MAX_FAILURES) {
            throw new ApiException(ErrorCode.INVALID_REQUEST,
                    "登录失败次数上限必须在 " + MIN_LOGIN_MAX_FAILURES + "~" + MAX_LOGIN_MAX_FAILURES + " 之间");
        }
        if (policy.loginLockMinutes() < MIN_LOCK_MINUTES || policy.loginLockMinutes() > MAX_LOCK_MINUTES) {
            throw new ApiException(ErrorCode.INVALID_REQUEST,
                    "锁定时长必须在 " + MIN_LOCK_MINUTES + "~" + MAX_LOCK_MINUTES + " 分钟之间");
        }
        if (policy.passwordMinLength() < MIN_PASSWORD_MIN_LENGTH
                || policy.passwordMinLength() > MAX_PASSWORD_MIN_LENGTH) {
            throw new ApiException(ErrorCode.INVALID_REQUEST,
                    "密码最小长度必须在 " + MIN_PASSWORD_MIN_LENGTH + "~" + MAX_PASSWORD_MIN_LENGTH + " 之间");
        }
    }

    private int intValue(String key, int defaultValue) {
        String raw = systemSettingService.getValue(key, null);
        if (raw == null || raw.isBlank()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException ex) {
            return defaultValue;
        }
    }

    private boolean booleanValue(String key, boolean defaultValue) {
        String raw = systemSettingService.getValue(key, null);
        return raw == null || raw.isBlank() ? defaultValue : Boolean.parseBoolean(raw.trim());
    }
}
