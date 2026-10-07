package com.easyoa.security.application;

import java.time.Instant;
import java.util.Map;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.easyoa.audit.application.AuditEntry;
import com.easyoa.audit.application.AuditService;
import com.easyoa.audit.domain.RiskLevel;
import com.easyoa.auth.application.SessionService;
import com.easyoa.common.audit.AuditActions;
import com.easyoa.common.exception.ApiException;
import com.easyoa.common.exception.ErrorCode;
import com.easyoa.security.domain.TotpAlgorithm;
import com.easyoa.security.dto.MfaEnrollmentView;
import com.easyoa.security.dto.MfaStatusView;
import com.easyoa.securityevent.application.SecurityEventService;
import com.easyoa.securityevent.domain.SecurityEventType;
import com.easyoa.user.domain.SystemRole;
import com.easyoa.user.domain.User;
import com.easyoa.user.repository.UserRepository;

/**
 * 动态口令（TOTP）生命周期：绑定、确认、解绑、重置、校验。
 *
 * <p>安全约束：
 * <ul>
 *   <li>Secret 加密后落库，接口只在绑定阶段返回一次明文（含 otpauth URI）；</li>
 *   <li>绑定必须经「生成 → 验证码确认」两步，避免误绑导致自锁；</li>
 *   <li>解绑需要当前密码 + 动态验证码双重确认；</li>
 *   <li>ROOT 重置他人 MFA 属于高危操作，必须经 {@code SensitiveOperationService} 通道，并撤销目标会话。</li>
 * </ul>
 */
@Service
public class MfaService {

    private static final String ISSUER = "EasyOA";

    private final UserRepository userRepository;
    private final TotpAlgorithm totpAlgorithm;
    private final TotpCipher totpCipher;
    private final SecuritySettingsService securitySettingsService;
    private final PasswordEncoder passwordEncoder;
    private final SessionService sessionService;
    private final AuditService auditService;
    private final SecurityEventService securityEventService;

    public MfaService(UserRepository userRepository, TotpAlgorithm totpAlgorithm, TotpCipher totpCipher,
            SecuritySettingsService securitySettingsService, PasswordEncoder passwordEncoder,
            SessionService sessionService, AuditService auditService, SecurityEventService securityEventService) {
        this.userRepository = userRepository;
        this.totpAlgorithm = totpAlgorithm;
        this.totpCipher = totpCipher;
        this.securitySettingsService = securitySettingsService;
        this.passwordEncoder = passwordEncoder;
        this.sessionService = sessionService;
        this.auditService = auditService;
        this.securityEventService = securityEventService;
    }

    @Transactional(readOnly = true)
    public MfaStatusView status(Long userId) {
        User user = load(userId);
        boolean requiredForAdmins = securitySettingsService.totpRequiredForAdmins();
        boolean required = requiredForAdmins && isAdminRole(user.getSystemRole()) && !user.isTotpEnabled();
        return new MfaStatusView(
                user.isTotpEnabled(),
                !user.isTotpEnabled() && user.getTotpSecretEncrypted() != null,
                requiredForAdmins,
                required);
    }

    /** 生成待确认 Secret（不启用），返回二维码 URI 与文本密钥。 */
    @Transactional
    public MfaEnrollmentView startEnrollment(Long userId) {
        User user = load(userId);
        if (user.isTotpEnabled()) {
            throw new ApiException(ErrorCode.TOTP_ALREADY_ENABLED);
        }
        String secret = totpAlgorithm.generateSecret();
        user.stageTotpSecret(totpCipher.encrypt(secret));
        userRepository.save(user);
        return new MfaEnrollmentView(ISSUER, user.getUsername(), secret,
                totpAlgorithm.otpauthUri(ISSUER, user.getUsername(), secret));
    }

    /** 用验证码确认绑定：成功后正式启用动态口令。 */
    @Transactional
    public MfaStatusView confirmEnrollment(Long userId, String code) {
        User user = load(userId);
        if (user.isTotpEnabled()) {
            throw new ApiException(ErrorCode.TOTP_ALREADY_ENABLED);
        }
        String secret = totpCipher.decrypt(user.getTotpSecretEncrypted());
        if (secret == null) {
            throw new ApiException(ErrorCode.TOTP_NOT_ENABLED, "绑定会话已失效，请重新获取绑定密钥");
        }
        if (!totpAlgorithm.verify(secret, code, Instant.now())) {
            throw new ApiException(ErrorCode.TOTP_INVALID);
        }
        user.enableTotp();
        userRepository.save(user);
        auditService.record(AuditEntry.action(AuditActions.SECURITY_TOTP_BOUND, RiskLevel.ELEVATED)
                .resource("USER", userId)
                .reason("绑定动态口令"));
        return status(userId);
    }

    /** 取消尚未确认的绑定。 */
    @Transactional
    public void cancelEnrollment(Long userId) {
        User user = load(userId);
        if (user.isTotpEnabled()) {
            throw new ApiException(ErrorCode.TOTP_ALREADY_ENABLED);
        }
        user.clearTotp();
        userRepository.save(user);
    }

    /** 自助解绑：当前密码 + 动态验证码双重确认。 */
    @Transactional
    public MfaStatusView disable(Long userId, String currentPassword, String code) {
        User user = load(userId);
        if (!user.isTotpEnabled()) {
            throw new ApiException(ErrorCode.TOTP_NOT_ENABLED);
        }
        if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            throw new ApiException(ErrorCode.PASSWORD_MISMATCH);
        }
        String secret = totpCipher.decrypt(user.getTotpSecretEncrypted());
        if (secret == null || !totpAlgorithm.verify(secret, code, Instant.now())) {
            throw new ApiException(ErrorCode.TOTP_INVALID);
        }
        user.clearTotp();
        userRepository.save(user);
        auditService.record(AuditEntry.action(AuditActions.SECURITY_TOTP_RESET, RiskLevel.ELEVATED)
                .resource("USER", userId)
                .reason("用户自助解绑动态口令"));
        securityEventService.record(SecurityEventType.MFA_RESET, "WARNING",
                "用户自助解绑动态口令：" + user.getUsername(),
                Map.of("userId", userId, "selfService", true));
        return status(userId);
    }

    /** 登录校验：账号已启用动态口令时校验验证码。 */
    @Transactional(readOnly = true)
    public boolean verifyCode(Long userId, String code) {
        User user = load(userId);
        if (!user.isTotpEnabled()) {
            return false;
        }
        String secret = totpCipher.decrypt(user.getTotpSecretEncrypted());
        return secret != null && totpAlgorithm.verify(secret, code, Instant.now());
    }

    /**
     * ROOT 重置他人动态口令（仅允许经高危操作通道调用）。
     *
     * <p>同时撤销该账号全部会话：避免攻击者在重置后继续沿用已通过的登录态。
     *
     * <p>此处只写审计：MFA_RESET 安全事件由高危操作通道统一写入（避免同一动作重复留痕）。
     */
    @Transactional
    public Map<String, Object> resetByRoot(Long targetUserId, String reason) {
        User user = load(targetUserId);
        boolean wasEnabled = user.isTotpEnabled();
        user.clearTotp();
        userRepository.save(user);

        int revoked = sessionService.revokeAllForUser(targetUserId, "MFA_RESET", null);

        auditService.record(AuditEntry.action(AuditActions.SECURITY_TOTP_RESET, RiskLevel.CRITICAL)
                .resource("USER", targetUserId)
                .before(Map.of("totpEnabled", wasEnabled, "username", user.getUsername()))
                .after(Map.of("totpEnabled", false, "revokedSessions", revoked))
                .reason(reason));
        return Map.of("username", user.getUsername(), "wasEnabled", wasEnabled, "revokedSessions", revoked);
    }

    private boolean isAdminRole(SystemRole role) {
        return role == SystemRole.ROOT || role == SystemRole.ADMIN;
    }

    private User load(Long userId) {
        return userRepository.findById(userId).orElseThrow(ApiException::notFound);
    }
}
