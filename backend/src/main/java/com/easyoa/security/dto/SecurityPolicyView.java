package com.easyoa.security.dto;

/**
 * 安全策略视图（系统设置页展示；修改必须走 ROOT 高危操作通道）。
 */
public record SecurityPolicyView(
        int loginMaxFailures,
        int loginLockMinutes,
        int passwordMinLength,
        boolean totpRequiredForAdmins) {
}
