package com.easyoa.auth.application;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.easyoa.common.exception.ApiException;
import com.easyoa.security.application.SecuritySettingsService;

/**
 * 密码强度策略单元测试。
 */
class PasswordPolicyTest {

    private SecuritySettingsService securitySettingsService;
    private PasswordPolicy policy;

    @BeforeEach
    void setUp() {
        // 单元测试中策略来源以 mock 代替数据库，固定最小长度 10
        securitySettingsService = mock(SecuritySettingsService.class);
        when(securitySettingsService.passwordMinLength()).thenReturn(10);
        policy = new PasswordPolicy(securitySettingsService);
    }

    @Test
    @DisplayName("满足策略的密码通过校验")
    void acceptsStrongPassword() {
        assertThatCode(() -> policy.validate("waiting", "Strong-Pass-2026")).doesNotThrowAnyException();
        assertThatCode(() -> policy.validate("waiting", "abcd1234XY")).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(strings = { "short1", "nodigitshere", "1234567890", "password123", "abcd123456" })
    @DisplayName("弱密码被拒绝")
    void rejectsWeakPasswords(String password) {
        assertThatThrownBy(() -> policy.validate("waiting", password)).isInstanceOf(ApiException.class);
    }

    @Test
    @DisplayName("密码不得包含用户名")
    void rejectsPasswordContainingUsername() {
        assertThatThrownBy(() -> policy.validate("waiting", "waiting-2026")).isInstanceOf(ApiException.class);
    }

    @Test
    @DisplayName("密码不得包含空白字符")
    void rejectsWhitespace() {
        assertThatThrownBy(() -> policy.validate("waiting", "abcd 1234XY")).isInstanceOf(ApiException.class);
    }
}