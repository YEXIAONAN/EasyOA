package com.easyoa.security.application;

import java.util.Map;

import com.easyoa.security.domain.SensitiveOperationType;
import com.easyoa.security.dto.SensitiveOperationPreviewView;

/**
 * 高危操作执行器。
 *
 * <p>只负责「影响范围计算」与「实际操作」，认证仪式（密码 / TOTP / Reason / Final Confirm）
 * 一律由 {@code SensitiveOperationService} 统一完成，实现类不得重复实现认证逻辑。
 */
public interface SensitiveOperation {

    SensitiveOperationType type();

    /** 计算影响范围（只读，不产生任何副作用）。 */
    SensitiveOperationPreviewView preview(Long targetId, Map<String, Object> payload);

    /**
     * 执行操作。
     *
     * @param actorId 操作者（ROOT）用户 ID，用于审计
     * @param reason  操作原因（已通过仪式校验）
     * @return 执行结果摘要（同时写入审计与安全事件的 before/after）
     */
    Map<String, Object> execute(Long targetId, Map<String, Object> payload, Long actorId, String reason);
}
