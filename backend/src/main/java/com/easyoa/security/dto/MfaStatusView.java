package com.easyoa.security.dto;

/**
 * 当前账号的动态口令状态（不含任何 Secret 信息）。
 */
public record MfaStatusView(
        boolean enabled,
        /** 存在待确认的绑定（已生成 Secret 但尚未验证）。 */
        boolean pendingEnrollment,
        /** 系统策略是否要求管理员必须绑定动态口令。 */
        boolean requiredForAdmins,
        /** 当前账号是否被系统策略要求绑定（ROOT / ADMIN 且策略开启）。 */
        boolean required) {
}
