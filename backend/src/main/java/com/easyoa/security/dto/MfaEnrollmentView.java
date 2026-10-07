package com.easyoa.security.dto;

/**
 * 动态口令绑定向导的返回值。
 *
 * <p>Secret 与 otpauth URI 只在此接口返回一次：绑定确认后任何接口都不再返回 Secret。
 */
public record MfaEnrollmentView(
        String issuer,
        String account,
        String secret,
        String otpauthUri) {
}
