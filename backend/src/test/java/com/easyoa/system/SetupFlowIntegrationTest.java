package com.easyoa.system;

import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import com.easyoa.AbstractIntegrationTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 首次初始化流程与「/setup 永久关闭」语义。
 */
class SetupFlowIntegrationTest extends AbstractIntegrationTest {

    @Test
    @DisplayName("空系统：status.required = true")
    void setupIsRequiredOnEmptySystem() throws Exception {
        mockMvc.perform(get("/api/setup/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.required").value(true));
    }

    @Test
    @DisplayName("初始化创建 ROOT 并关闭 /setup，重复初始化返回 409")
    void initializeOnceAndRejectSecondAttempt() throws Exception {
        mockMvc.perform(post("/api/setup/initialize")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "organizationName", "Easy Studio",
                                "username", "root",
                                "displayName", "Waiting",
                                "password", DEFAULT_PASSWORD))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.organizationName").value("Easy Studio"))
                .andExpect(jsonPath("$.data.rootUsername").value("root"));

        mockMvc.perform(get("/api/setup/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.required").value(false));

        // 已初始化：任何再次初始化都被拒绝
        mockMvc.perform(post("/api/setup/initialize")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "organizationName", "Hacked Studio",
                                "username", "root2",
                                "displayName", "Intruder",
                                "password", DEFAULT_PASSWORD))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SETUP_ALREADY_COMPLETED"));

        // ROOT 用户可以正常登录，证明初始化结果真实落库
        login("root", DEFAULT_PASSWORD);
    }

    @Test
    @DisplayName("初始化时的密码同样受密码策略约束")
    void initializationEnforcesPasswordPolicy() throws Exception {
        mockMvc.perform(post("/api/setup/initialize")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "organizationName", "Easy Studio",
                                "username", "root",
                                "displayName", "Waiting",
                                "password", "weak"))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("PASSWORD_POLICY_VIOLATION"));
    }

    @Test
    @DisplayName("初始化写入审计日志与安全事件")
    void initializationIsAudited() throws Exception {
        mockMvc.perform(post("/api/setup/initialize")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "organizationName", "Easy Studio",
                                "username", "root",
                                "displayName", "Waiting",
                                "password", DEFAULT_PASSWORD))))
                .andExpect(status().isOk());

        var auditPage = auditService.query(com.easyoa.audit.dto.AuditLogQuery.of(
                null, "SETUP_INITIALIZED", null, null, null, null, null, 1, 20));
        assertThat(auditPage.items()).isNotEmpty();
        assertThat(auditPage.items().get(0).actorUsername()).isEqualTo("root");
        assertThat(auditPage.items().get(0).riskLevel()).isEqualTo("CRITICAL");
    }
}