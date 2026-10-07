package com.easyoa.security.application.operation;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.easyoa.security.application.AbstractSensitiveOperation;
import com.easyoa.security.application.MfaService;
import com.easyoa.security.domain.SensitiveOperationType;
import com.easyoa.user.domain.User;
import com.easyoa.user.repository.UserRepository;

/**
 * 管理员 MFA 重置：清除目标账号动态口令并撤销其全部会话。
 *
 * <p>典型场景：成员更换手机且丢失恢复码，由 ROOT 重置后由本人重新绑定。
 */
@Component
public class MfaResetOperation extends AbstractSensitiveOperation {

    private final UserRepository userRepository;
    private final MfaService mfaService;

    public MfaResetOperation(UserRepository userRepository, MfaService mfaService) {
        this.userRepository = userRepository;
        this.mfaService = mfaService;
    }

    @Override
    public SensitiveOperationType type() {
        return SensitiveOperationType.MFA_RESET;
    }

    @Override
    protected boolean requiresTarget() {
        return true;
    }

    @Override
    protected String targetLabel(Long targetId, Map<String, Object> payload) {
        return userRepository.findById(requireTarget(targetId))
                .map(user -> user.getDisplayName() + "（" + user.getUsername() + "）")
                .orElse("未知账号");
    }

    @Override
    protected List<String> impacts(Long targetId, Map<String, Object> payload) {
        User user = userRepository.findById(requireTarget(targetId)).orElse(null);
        if (user == null) {
            return List.of("目标账号不存在");
        }
        return List.of(
                "将清除账号「" + user.getDisplayName() + "」已绑定的动态口令",
                "将立即撤销该账号全部登录会话（所有设备强制下线）",
                "该账号下次登录需重新绑定动态口令");
    }

    @Override
    public Map<String, Object> execute(Long targetId, Map<String, Object> payload, Long actorId, String reason) {
        return mfaService.resetByRoot(requireTarget(targetId), reason);
    }

    private Long requireTarget(Long targetId) {
        if (targetId == null) {
            throw com.easyoa.common.exception.ApiException.invalidRequest("请选择要重置 MFA 的账号");
        }
        return targetId;
    }
}
