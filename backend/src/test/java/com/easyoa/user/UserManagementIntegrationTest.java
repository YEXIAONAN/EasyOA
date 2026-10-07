package com.easyoa.user;

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
 * 账号管理集成测试：创建权限、密码策略、禁用与角色变更的会话撤销、最后 ROOT 保护。
 */
class UserManagementIntegrationTest extends AbstractIntegrationTest {

    @Test
    @DisplayName("ADMIN 只能创建 MEMBER；创建管理员需要 ROOT")
    void createUserRoleBoundary() throws Exception {
        initializeSystemWithRoot("root");
        createUser("admin", DEFAULT_PASSWORD, SystemRole.ADMIN);
        MockHttpSession adminSession = login("admin", DEFAULT_PASSWORD);
        MockHttpSession rootSession = login("root", DEFAULT_PASSWORD);

        // ADMIN 创建 MEMBER → 允许
        mockMvc.perform(post("/api/users")
                        .session(adminSession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("username", "newbie", "displayName", "Newbie",
                                "systemRole", "MEMBER", "initialPassword", DEFAULT_PASSWORD))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.systemRole").value("MEMBER"));

        // ADMIN 创建 ADMIN → 拒绝
        mockMvc.perform(post("/api/users")
                        .session(adminSession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("username", "admin2", "displayName", "Admin2",
                                "systemRole", "ADMIN", "initialPassword", DEFAULT_PASSWORD))))
                .andExpect(status().isForbidden());

        // ROOT 创建 ADMIN → 允许
        mockMvc.perform(post("/api/users")
                        .session(rootSession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("username", "admin2", "displayName", "Admin2",
                                "systemRole", "ADMIN", "initialPassword", DEFAULT_PASSWORD))))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("创建成员时同样执行密码策略与用户名唯一校验")
    void createUserValidations() throws Exception {
        initializeSystemWithRoot("root");
        MockHttpSession rootSession = login("root", DEFAULT_PASSWORD);

        mockMvc.perform(post("/api/users")
                        .session(rootSession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("username", "weakpass", "displayName", "Weak",
                                "systemRole", "MEMBER", "initialPassword", "123456"))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("PASSWORD_POLICY_VIOLATION"));

        createUser("member", DEFAULT_PASSWORD, SystemRole.MEMBER);
        mockMvc.perform(post("/api/users")
                        .session(rootSession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("username", "member", "displayName", "Duplicate",
                                "systemRole", "MEMBER", "initialPassword", DEFAULT_PASSWORD))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("USERNAME_TAKEN"));
    }

    @Test
    @DisplayName("禁用账号后其全部会话立即失效（会话内角色与状态必须失效）")
    void disablingUserRevokesSessions() throws Exception {
        initializeSystemWithRoot("root");
        Long memberId = createUser("member", DEFAULT_PASSWORD, SystemRole.MEMBER).getId();
        MockHttpSession memberSession = login("member", DEFAULT_PASSWORD);
        MockHttpSession rootSession = login("root", DEFAULT_PASSWORD);

        mockMvc.perform(get("/api/auth/me").session(memberSession)).andExpect(status().isOk());

        mockMvc.perform(post("/api/users/{id}/status", memberId)
                        .session(rootSession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("status", "DISABLED"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DISABLED"));

        mockMvc.perform(get("/api/auth/me").session(memberSession))
                .andExpect(status().isUnauthorized());

        // 被禁用后无法再次登录
        mockMvc.perform(post("/api/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("username", "member", "password", DEFAULT_PASSWORD))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCOUNT_DISABLED"));
    }

    @Test
    @DisplayName("ADMIN 不能操作 ROOT / ADMIN 账号，也不能修改自己")
    void adminCannotOperateAdmins() throws Exception {
        initializeSystemWithRoot("root");
        Long adminId = createUser("admin", DEFAULT_PASSWORD, SystemRole.ADMIN).getId();
        Long rootId = userService.findByUsername("root").orElseThrow().getId();
        MockHttpSession adminSession = login("admin", DEFAULT_PASSWORD);

        mockMvc.perform(post("/api/users/{id}/status", rootId)
                        .session(adminSession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("status", "DISABLED"))))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/users/{id}/status", adminId)
                        .session(adminSession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("status", "DISABLED"))))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("角色变更仅 ROOT 可执行，且不允许移除最后一个 ROOT")
    void roleChangeRules() throws Exception {
        initializeSystemWithRoot("root");
        Long adminId = createUser("admin", DEFAULT_PASSWORD, SystemRole.ADMIN).getId();
        Long memberId = createUser("member", DEFAULT_PASSWORD, SystemRole.MEMBER).getId();
        Long rootId = userService.findByUsername("root").orElseThrow().getId();
        MockHttpSession adminSession = login("admin", DEFAULT_PASSWORD);
        MockHttpSession rootSession = login("root", DEFAULT_PASSWORD);

        // ADMIN 无权变更角色
        mockMvc.perform(post("/api/users/{id}/role", memberId)
                        .session(adminSession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("systemRole", "ADMIN"))))
                .andExpect(status().isForbidden());

        // 最后一个 ROOT 不能被降级（防止系统失去管理入口）
        mockMvc.perform(post("/api/users/{id}/role", rootId)
                        .session(rootSession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("systemRole", "MEMBER"))))
                .andExpect(status().isConflict());

        // 正常晋升：ROOT 把 member 提升为 ADMIN
        mockMvc.perform(post("/api/users/{id}/role", memberId)
                        .session(rootSession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("systemRole", "ADMIN"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.systemRole").value("ADMIN"));

        // 已有两个管理员后，可降级原 ADMIN
        mockMvc.perform(post("/api/users/{id}/role", adminId)
                        .session(rootSession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("systemRole", "MEMBER"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.systemRole").value("MEMBER"));
    }

    @Test
    @DisplayName("角色变更后强制重新登录：旧会话中的角色缓存立即失效")
    void roleChangeRevokesSessions() throws Exception {
        initializeSystemWithRoot("root");
        Long memberId = createUser("member", DEFAULT_PASSWORD, SystemRole.MEMBER).getId();
        MockHttpSession memberSession = login("member", DEFAULT_PASSWORD);
        MockHttpSession rootSession = login("root", DEFAULT_PASSWORD);

        mockMvc.perform(get("/api/auth/me").session(memberSession)).andExpect(status().isOk());

        mockMvc.perform(post("/api/users/{id}/role", memberId)
                        .session(rootSession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("systemRole", "ADMIN"))))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/auth/me").session(memberSession))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("本人可以维护自己的档案，但不能修改职位与系统角色")
    void memberCanUpdateOwnProfile() throws Exception {
        initializeSystemWithRoot("root");
        Long memberId = createUser("member", DEFAULT_PASSWORD, SystemRole.MEMBER).getId();
        MockHttpSession memberSession = login("member", DEFAULT_PASSWORD);
        MockHttpSession rootSession = login("root", DEFAULT_PASSWORD);

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .patch("/api/users/me")
                        .session(memberSession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "displayName", "Member Updated",
                                "email", "me@easyoa.dev",
                                "phone", "13900000000",
                                "bio", "专注后端与安全"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.displayName").value("Member Updated"))
                .andExpect(jsonPath("$.data.contactVisible").value(true));

        // 防 Mass Assignment：DTO 之外的字段（如职位、系统角色）直接拒绝
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .patch("/api/users/me")
                        .session(memberSession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "displayName", "伪装的成员",
                                "jobTitle", "CTO",
                                "systemRole", "ROOT"))))
                .andExpect(status().isBadRequest());

        // 职位与角色未被篡改
        var updated = userService.getById(memberId);
        assertThat(updated.getJobTitle()).isNull();
        assertThat(updated.getDisplayName()).isEqualTo("Member Updated");

        // 角色未被提升
        mockMvc.perform(get("/api/users/{id}", memberId).session(rootSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.systemRole").value("MEMBER"));
    }

    @Test
    @DisplayName("组织与账号变更写入审计日志")
    void changesAreAudited() throws Exception {
        initializeSystemWithRoot("root");
        MockHttpSession rootSession = login("root", DEFAULT_PASSWORD);
        createUnitViaApi(rootSession, "技术部", "DEPARTMENT", null);
        createUserViaApi(rootSession, "member", "Member", "MEMBER", null, null);

        var unitAudits = auditService.query(com.easyoa.audit.dto.AuditLogQuery.of(
                null, "ORG_UNIT_CREATED", null, null, null, null, null, 1, 10));
        assertThat(unitAudits.items()).isNotEmpty();
        assertThat(unitAudits.items().get(0).actorUsername()).isEqualTo("root");
        assertThat(unitAudits.items().get(0).riskLevel()).isEqualTo("ELEVATED");

        var userAudits = auditService.query(com.easyoa.audit.dto.AuditLogQuery.of(
                null, "USER_CREATED", null, null, null, null, null, 1, 10));
        assertThat(userAudits.items()).isNotEmpty();
        assertThat(userAudits.items().get(0).afterData()).contains("member");
    }
}