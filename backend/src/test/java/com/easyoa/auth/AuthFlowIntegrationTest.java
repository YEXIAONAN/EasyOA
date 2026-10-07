package com.easyoa.auth;

import java.time.Instant;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;

import com.easyoa.AbstractIntegrationTest;
import com.easyoa.user.domain.SystemRole;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 登录 / 会话 / 密码 完整闭环，以及对应的安全边界。
 */
class AuthFlowIntegrationTest extends AbstractIntegrationTest {

    @Test
    @DisplayName("登录成功后可获取当前用户，登出后会话被撤销")
    void loginAndLogoutLifecycle() throws Exception {
        var user = initializeSystemWithRoot("root");

        MockHttpSession session = login("root", DEFAULT_PASSWORD);

        mockMvc.perform(get("/api/auth/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.username").value("root"))
                .andExpect(jsonPath("$.data.systemRole").value("ROOT"))
                // 敏感字段绝不外泄
                .andExpect(jsonPath("$.data.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.data.password").doesNotExist());

        mockMvc.perform(post("/api/auth/logout").session(session).with(csrf()))
                .andExpect(status().isOk());

        // 数据库中的会话记录已被撤销
        assertThat(userSessionRepository
                .findByUserIdAndRevokedAtIsNullAndExpiresAtAfterOrderByLastSeenAtDesc(user.getId(), Instant.now()))
                .isEmpty();
    }

    @Test
    @DisplayName("密码错误返回 401，且不泄露用户是否存在")
    void wrongPasswordIsRejected() throws Exception {
        initializeSystemWithRoot("root");

        mockMvc.perform(post("/api/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("username", "root", "password", "Wrong-Password-2026"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));

        // 不存在的用户返回同一错误码（避免用户名枚举）
        mockMvc.perform(post("/api/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("username", "ghost-user", "password", "Wrong-Password-2026"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
    }

    @Test
    @DisplayName("连续失败达到阈值后账号被临时锁定（429）")
    void accountIsTemporarilyLockedAfterRepeatedFailures() throws Exception {
        initializeSystemWithRoot("root");

        for (int attempt = 0; attempt < 5; attempt++) {
            mockMvc.perform(post("/api/auth/login")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of("username", "root", "password", "Wrong-Password-2026"))))
                    .andExpect(status().isUnauthorized());
        }

        // 第 6 次即使密码正确也被拒绝
        mockMvc.perform(post("/api/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("username", "root", "password", DEFAULT_PASSWORD))))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("LOGIN_BLOCKED"));
    }

    @Test
    @DisplayName("未登录访问受保护接口返回 401，而不是重定向或 HTML")
    void unauthenticatedRequestsAreRejected() throws Exception {
        initializeSystemWithRoot("root");

        mockMvc.perform(get("/api/workspace/summary"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
                .andExpect(jsonPath("$.requestId").exists());
    }

    @Test
    @DisplayName("缺少 CSRF Token 的写请求被拒绝（403）")
    void writeRequestsWithoutCsrfAreRejected() throws Exception {
        initializeSystemWithRoot("root");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("username", "root", "password", DEFAULT_PASSWORD))))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("修改密码后：旧密码失效、其他设备会话立即失效、当前会话保持有效")
    void changePasswordRevokesOtherSessions() throws Exception {
        initializeSystemWithRoot("root");

        MockHttpSession sessionA = login("root", DEFAULT_PASSWORD);
        MockHttpSession sessionB = login("root", DEFAULT_PASSWORD);

        mockMvc.perform(get("/api/auth/me").session(sessionB)).andExpect(status().isOk());

        mockMvc.perform(post("/api/auth/password")
                        .session(sessionA)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "currentPassword", DEFAULT_PASSWORD,
                                "newPassword", "Brand-New-Password-2026"))))
                .andExpect(status().isOk());

        // 其他设备会话立即失效
        mockMvc.perform(get("/api/auth/me").session(sessionB))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));

        // 当前会话仍然有效
        mockMvc.perform(get("/api/auth/me").session(sessionA)).andExpect(status().isOk());

        // 新密码可用、旧密码失效
        mockMvc.perform(post("/api/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("username", "root", "password", DEFAULT_PASSWORD))))
                .andExpect(status().isUnauthorized());

        login("root", "Brand-New-Password-2026");
    }

    @Test
    @DisplayName("修改密码时当前密码错误 → 422，且不修改任何数据")
    void changePasswordRequiresCurrentPassword() throws Exception {
        initializeSystemWithRoot("root");
        MockHttpSession session = login("root", DEFAULT_PASSWORD);

        mockMvc.perform(post("/api/auth/password")
                        .session(session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "currentPassword", "Not-The-Password-2026",
                                "newPassword", "Brand-New-Password-2026"))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("PASSWORD_MISMATCH"));

        // 原密码仍然有效
        login("root", DEFAULT_PASSWORD);
    }

    @Test
    @DisplayName("修改密码同时受密码策略约束")
    void changePasswordEnforcesPolicy() throws Exception {
        initializeSystemWithRoot("root");
        MockHttpSession session = login("root", DEFAULT_PASSWORD);

        mockMvc.perform(post("/api/auth/password")
                        .session(session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "currentPassword", DEFAULT_PASSWORD,
                                "newPassword", "short1"))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("PASSWORD_POLICY_VIOLATION"));

        mockMvc.perform(post("/api/auth/password")
                        .session(session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "currentPassword", DEFAULT_PASSWORD,
                                "newPassword", "rootrootroot1"))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("PASSWORD_POLICY_VIOLATION"));
    }

    @Test
    @DisplayName("禁用账号无法登录（403）")
    void disabledUserCannotLogin() throws Exception {
        var user = initializeSystemWithRoot("root");
        createUser("member", DEFAULT_PASSWORD, SystemRole.MEMBER);

        // 通过领域方法禁用账号（Phase 2 起提供管理接口）
        userRepository.findById(user.getId()).ifPresent(entity -> {
            entity.disable();
            userRepository.save(entity);
        });

        mockMvc.perform(post("/api/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("username", "root", "password", DEFAULT_PASSWORD))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCOUNT_DISABLED"));
    }
}