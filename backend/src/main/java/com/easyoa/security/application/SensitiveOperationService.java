package com.easyoa.security.application;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.easyoa.audit.application.AuditEntry;
import com.easyoa.audit.application.AuditService;
import com.easyoa.audit.domain.RiskLevel;
import com.easyoa.common.audit.AuditActions;
import com.easyoa.common.exception.ApiException;
import com.easyoa.common.exception.ErrorCode;
import com.easyoa.common.requestid.RequestContext;
import com.easyoa.common.security.SecurityUser;
import com.easyoa.security.domain.SensitiveOperationType;
import com.easyoa.security.dto.SensitiveOperationPreviewRequest;
import com.easyoa.security.dto.SensitiveOperationPreviewView;
import com.easyoa.security.dto.SensitiveOperationRequest;
import com.easyoa.security.dto.SensitiveOperationResultView;
import com.easyoa.securityevent.application.SecurityEventService;
import com.easyoa.securityevent.domain.SecurityEventType;
import com.easyoa.user.domain.SystemRole;
import com.easyoa.user.domain.User;
import com.easyoa.user.repository.UserRepository;

/**
 * ROOT 高危操作统一入口。
 *
 * <p>所有高危动作共用一套仪式，业务模块不得自行实现认证逻辑：
 * <pre>
 *   重新输入当前登录密码 → TOTP → 填写 Reason → 展示影响范围 → Final Confirm → 执行 → Security Event
 * </pre>
 *
 * <p>事务语义：本方法<b>不</b>开启外层事务。校验失败也必须留下审计与安全事件，
 * 若整个过程处于同一事务中，异常会导致这些记录被回滚，从而让攻击尝试无迹可循。
 */
@Service
public class SensitiveOperationService {

    private static final int MIN_REASON_LENGTH = 8;

    private final Map<SensitiveOperationType, SensitiveOperation> operations;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final MfaService mfaService;
    private final AuditService auditService;
    private final SecurityEventService securityEventService;

    public SensitiveOperationService(List<SensitiveOperation> operationList, UserRepository userRepository,
            PasswordEncoder passwordEncoder, MfaService mfaService, AuditService auditService,
            SecurityEventService securityEventService) {
        this.operations = operationList.stream()
                .collect(Collectors.toMap(SensitiveOperation::type, Function.identity()));
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.mfaService = mfaService;
        this.auditService = auditService;
        this.securityEventService = securityEventService;
    }

    /** 影响范围预览（只读；仅 ROOT）。 */
    public SensitiveOperationPreviewView preview(SensitiveOperationPreviewRequest request) {
        requireRoot();
        return require(request.type()).preview(request.targetId(), request.payload());
    }

    /** 执行高危操作。 */
    public SensitiveOperationResultView execute(SensitiveOperationRequest request) {
        SecurityUser actor = requireRoot();
        SensitiveOperationType type = request.type();
        SensitiveOperation operation = require(type);
        User operator = userRepository.findById(actor.id()).orElseThrow(ApiException::notFound);

        // 1. 重新输入当前登录密码
        if (!passwordEncoder.matches(request.currentPassword(), operator.getPasswordHash())) {
            deny(type, operator.getUsername(), "当前密码校验失败");
            throw new ApiException(ErrorCode.PASSWORD_MISMATCH, "当前密码校验失败");
        }
        // 2. TOTP：高危操作必须已绑定动态口令
        if (!operator.isTotpEnabled()) {
            throw new ApiException(ErrorCode.MFA_REQUIRED);
        }
        if (!mfaService.verifyCode(operator.getId(), request.totpCode())) {
            deny(type, operator.getUsername(), "动态验证码校验失败");
            throw new ApiException(ErrorCode.TOTP_INVALID);
        }
        // 3. Reason
        String reason = request.reason() == null ? "" : request.reason().trim();
        if (reason.length() < MIN_REASON_LENGTH) {
            throw new ApiException(ErrorCode.REASON_REQUIRED);
        }
        // 4. Final Confirm（逐字输入确认短语）
        if (!type.confirmationPhrase().equals(request.confirmation().trim())) {
            throw new ApiException(ErrorCode.CONFIRMATION_MISMATCH);
        }

        // 5. 执行（各实现自行开启事务）
        Map<String, Object> result = operation.execute(request.targetId(), request.payload(), operator.getId(), reason);

        // 6. 留痕：审计 + 安全事件
        auditService.record(AuditEntry.action(AuditActions.SECURITY_SENSITIVE_OPERATION, RiskLevel.CRITICAL)
                .resource("SENSITIVE_OPERATION", type.name())
                .after(result)
                .reason(reason));
        securityEventService.record(type.securityEventType(), "CRITICAL",
                "ROOT 高危操作执行成功：" + type.displayName(),
                detail(type, operator.getUsername(), request.targetId(), reason, result));

        return new SensitiveOperationResultView(type, type.displayName(), "操作已执行，并已写入安全事件",
                result, Instant.now());
    }

    /** 校验失败留痕：让「失败的高危尝试」同样可追溯。 */
    private void deny(SensitiveOperationType type, String username, String reason) {
        auditService.record(AuditEntry.action(AuditActions.SECURITY_SENSITIVE_OPERATION, RiskLevel.ELEVATED)
                .after(Map.of("type", type.name(), "outcome", "DENIED", "reason", reason))
                .reason(reason));
        securityEventService.record(SecurityEventType.PRIVILEGED_OPERATION, "WARNING",
                "ROOT 高危操作被拒绝：" + type.displayName(),
                Map.of("type", type.name(), "operator", username, "reason", reason));
    }

    private Map<String, Object> detail(SensitiveOperationType type, String operator, Long targetId, String reason,
            Map<String, Object> result) {
        Map<String, Object> detail = new HashMap<>();
        detail.put("type", type.name());
        detail.put("operator", operator);
        detail.put("targetId", targetId == null ? "none" : String.valueOf(targetId));
        detail.put("reason", reason);
        detail.put("result", result);
        return detail;
    }

    private SensitiveOperation require(SensitiveOperationType type) {
        SensitiveOperation operation = operations.get(type);
        if (operation == null) {
            throw new ApiException(ErrorCode.INVALID_REQUEST, "不支持的高危操作类型");
        }
        return operation;
    }

    private SecurityUser requireRoot() {
        SecurityUser actor = RequestContext.currentUser();
        if (actor == null) {
            throw new ApiException(ErrorCode.UNAUTHENTICATED);
        }
        if (actor.systemRole() != SystemRole.ROOT) {
            throw new ApiException(ErrorCode.SENSITIVE_OPERATION_DENIED);
        }
        return actor;
    }
}
