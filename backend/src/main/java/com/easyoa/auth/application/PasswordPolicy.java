package com.easyoa.auth.application;

import java.util.Set;

import org.springframework.stereotype.Component;

import com.easyoa.common.exception.ApiException;
import com.easyoa.common.exception.ErrorCode;
import com.easyoa.security.application.SecuritySettingsService;

/**
 * 密码强度策略。
 *
 * <p>规则（v0.1.0）：
 * <ul>
 *   <li>长度下限由安全策略配置（默认 10），上限 128；</li>
 *   <li>至少包含字母与数字；</li>
 *   <li>不得与用户名相同或包含用户名；</li>
 *   <li>不得使用常见弱密码；</li>
 *   <li>不得包含空白字符。</li>
 * </ul>
 *
 * <p>长度下限实时读取安全策略，使 ROOT 的「安全策略修改」真正生效。
 */
@Component
public class PasswordPolicy {

    private static final int MAX_LENGTH = 128;
    private static final int MIN_ALLOWED_LENGTH = 8;

    private static final Set<String> COMMON_WEAK_PASSWORDS = Set.of(
            "password123", "1234567890", "qwerty12345", "admin12345", "easyoa1234",
            "password1234", "abcd123456", "1111111111", "admin@12345");

    private final SecuritySettingsService securitySettingsService;

    public PasswordPolicy(SecuritySettingsService securitySettingsService) {
        this.securitySettingsService = securitySettingsService;
    }

    public void validate(String username, String password) {
        int minLength = Math.max(MIN_ALLOWED_LENGTH, securitySettingsService.passwordMinLength());
        if (password == null || password.length() < minLength) {
            throw violation("密码长度至少 " + minLength + " 位");
        }
        if (password.length() > MAX_LENGTH) {
            throw violation("密码长度不得超过 " + MAX_LENGTH + " 位");
        }
        if (password.chars().anyMatch(Character::isWhitespace)) {
            throw violation("密码不得包含空白字符");
        }
        boolean hasLetter = password.chars().anyMatch(Character::isLetter);
        boolean hasDigit = password.chars().anyMatch(Character::isDigit);
        if (!hasLetter || !hasDigit) {
            throw violation("密码必须同时包含字母与数字");
        }
        String lower = password.toLowerCase();
        if (upper(username) != null && lower.contains(username.toLowerCase())) {
            throw violation("密码不得包含用户名");
        }
        if (COMMON_WEAK_PASSWORDS.contains(lower)) {
            throw violation("密码过于常见，请更换更复杂的密码");
        }
    }

    private String upper(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private ApiException violation(String reason) {
        return new ApiException(ErrorCode.PASSWORD_POLICY_VIOLATION, reason);
    }
}