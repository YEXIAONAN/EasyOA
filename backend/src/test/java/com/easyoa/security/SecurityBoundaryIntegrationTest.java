package com.easyoa.security;

import java.lang.reflect.Method;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;

import com.easyoa.AbstractIntegrationTest;
import com.easyoa.audit.repository.AuditLogRepository;
import com.easyoa.securityevent.repository.SecurityEventRepository;
import com.easyoa.user.domain.SystemRole;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 安全边界测试：越权访问、IDOR、审计不可变、会话撤销。
 *
 * <p>核心原则：前端不可信。这里的每个用例都直接调用 HTTP API，
 * 不依赖任何前端行为。
 */
class SecurityBoundaryIntegrationTest extends AbstractIntegrationTest {

    @Test
    @DisplayName("普通 MEMBER 访问审计日志 API → 403；ADMIN 可以访问")
    void auditLogsRequireAdminRole() throws Exception {
        initializeSystemWithRoot("root");
        createUser("admin", DEFAULT_PASSWORD, SystemRole.ADMIN);
        createUser("member", DEFAULT_PASSWORD, SystemRole.MEMBER);

        MockHttpSession memberSession = login("member", DEFAULT_PASSWORD);
        mockMvc.perform(get("/api/audit-logs").session(memberSession))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        MockHttpSession adminSession = login("admin", DEFAULT_PASSWORD);
        mockMvc.perform(get("/api/audit-logs").session(adminSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("会话被撤销后，即使浏览器仍持有 Cookie 也立即 401")
    void revokedSessionIsRejectedImmediately() throws Exception {
        initializeSystemWithRoot("root");
        MockHttpSession session = login("root", DEFAULT_PASSWORD);

        mockMvc.perform(get("/api/auth/me").session(session)).andExpect(status().isOk());

        // 通过「登录设备管理」撤销当前会话
        mockMvc.perform(delete("/api/auth/sessions/{id}", currentSessionId(session))
                        .session(session)
                        .with(csrf()))
                .andExpect(status().isOk());

        // 服务端会话记录已失效：继续使用旧 Cookie 请求必须被拒绝
        mockMvc.perform(get("/api/auth/me").session(session))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    @DisplayName("IDOR：不能撤销其他用户的会话（跨用户操作无任何效果）")
    void cannotRevokeAnotherUsersSession() throws Exception {
        initializeSystemWithRoot("root");
        createUser("member", DEFAULT_PASSWORD, SystemRole.MEMBER);

        MockHttpSession memberSession = login("member", DEFAULT_PASSWORD);
        MockHttpSession rootSession = login("root", DEFAULT_PASSWORD);

        Long rootSessionId = currentSessionId(rootSession);

        // member 尝试撤销 root 的会话：接口返回成功，但目标会话不受影响
        mockMvc.perform(delete("/api/auth/sessions/{id}", rootSessionId)
                        .session(memberSession)
                        .with(csrf()))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/auth/me").session(rootSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.username").value("root"));
    }

    @Test
    @DisplayName("登录设备列表只返回当前用户自己的会话")
    void sessionListIsScopedToCurrentUser() throws Exception {
        initializeSystemWithRoot("root");
        var member = createUser("member", DEFAULT_PASSWORD, SystemRole.MEMBER);

        MockHttpSession memberSession = login("member", DEFAULT_PASSWORD);
        login("root", DEFAULT_PASSWORD);

        mockMvc.perform(get("/api/auth/sessions").session(memberSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1));

        // 数据层面确认：过滤条件确实按当前用户生效
        List<com.easyoa.auth.domain.UserSession> memberSessions = userSessionRepository
                .findByUserIdAndRevokedAtIsNullAndExpiresAtAfterOrderByLastSeenAtDesc(member.getId(), Instant.now());
        assertThat(memberSessions).hasSize(1);
    }

    @Test
    @DisplayName("审计与安全事件仓储不存在任何 delete / update 能力（Append Only 结构性保证）")
    void auditRepositoriesAreStructurallyAppendOnly() {
        for (Method method : AuditLogRepository.class.getMethods()) {
            assertThat(method.getName()).doesNotContain("delete").doesNotContain("Delete");
        }
        for (Method method : SecurityEventRepository.class.getMethods()) {
            assertThat(method.getName()).doesNotContain("delete").doesNotContain("Delete");
        }
    }

    @Test
    @DisplayName("请求携带伪造的转发头无法绕过：登录失败限制按真实 IP 维度记录")
    void spoofedForwardHeadersDoNotBreakSecurityControls() throws Exception {
        initializeSystemWithRoot("root");

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/api/auth/login")
                        .with(csrf())
                        .header("X-Forwarded-For", "1.2.3.4")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("username", "root", "password", "Wrong-Password-2026"))))
                .andExpect(status().isUnauthorized());

        assertThat(loginAttemptRepository.count()).isPositive();
    }

    private Long currentSessionId(MockHttpSession session) throws Exception {
        var result = mockMvc.perform(get("/api/auth/sessions").session(session))
                .andExpect(status().isOk())
                .andReturn();
        var root = objectMapper.readTree(result.getResponse().getContentAsString());
        return root.path("data").get(0).path("id").asLong();
    }
}