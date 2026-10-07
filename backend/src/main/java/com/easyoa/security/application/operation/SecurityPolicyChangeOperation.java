package com.easyoa.security.application.operation;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.easyoa.security.application.AbstractSensitiveOperation;
import com.easyoa.security.application.SecuritySettingsService;
import com.easyoa.security.domain.SensitiveOperationType;
import com.easyoa.security.dto.SecurityPolicyView;

/**
 * 安全策略修改：调整登录保护、密码强度与管理员动态口令要求。
 *
 * <p>影响范围以「字段级差异」呈现，操作者可以清楚看到每一项的变化。
 */
@Component
public class SecurityPolicyChangeOperation extends AbstractSensitiveOperation {

    private final SecuritySettingsService securitySettingsService;

    public SecurityPolicyChangeOperation(SecuritySettingsService securitySettingsService) {
        this.securitySettingsService = securitySettingsService;
    }

    @Override
    public SensitiveOperationType type() {
        return SensitiveOperationType.SECURITY_POLICY_CHANGE;
    }

    @Override
    protected List<String> impacts(Long targetId, Map<String, Object> payload) {
        SecurityPolicyView current = securitySettingsService.current();
        SecurityPolicyView target = resolve(current, payload);
        List<String> impacts = new ArrayList<>();
        addIfChanged(impacts, "登录失败次数上限", current.loginMaxFailures(), target.loginMaxFailures());
        addIfChanged(impacts, "账号锁定时长（分钟）", current.loginLockMinutes(), target.loginLockMinutes());
        addIfChanged(impacts, "密码最小长度", current.passwordMinLength(), target.passwordMinLength());
        addIfChanged(impacts, "管理员必须绑定动态口令", current.totpRequiredForAdmins(),
                target.totpRequiredForAdmins());
        if (impacts.isEmpty()) {
            impacts.add("策略与当前值一致，执行后不会产生变化");
        }
        impacts.add("策略立即生效（登录保护与密码强度在每次校验时读取当前值）");
        return impacts;
    }

    @Override
    public Map<String, Object> execute(Long targetId, Map<String, Object> payload, Long actorId, String reason) {
        SecurityPolicyView current = securitySettingsService.current();
        SecurityPolicyView target = resolve(current, payload);
        SecurityPolicyView updated = securitySettingsService.update(target, actorId);
        return Map.of(
                "loginMaxFailures", updated.loginMaxFailures(),
                "loginLockMinutes", updated.loginLockMinutes(),
                "passwordMinLength", updated.passwordMinLength(),
                "totpRequiredForAdmins", updated.totpRequiredForAdmins());
    }

    private SecurityPolicyView resolve(SecurityPolicyView current, Map<String, Object> payload) {
        return new SecurityPolicyView(
                intPayload(payload, "loginMaxFailures", current.loginMaxFailures()),
                intPayload(payload, "loginLockMinutes", current.loginLockMinutes()),
                intPayload(payload, "passwordMinLength", current.passwordMinLength()),
                boolPayload(payload, "totpRequiredForAdmins", current.totpRequiredForAdmins()));
    }

    private void addIfChanged(List<String> impacts, String label, Object before, Object after) {
        if (!String.valueOf(before).equals(String.valueOf(after))) {
            impacts.add(label + "：" + before + " → " + after);
        }
    }
}
