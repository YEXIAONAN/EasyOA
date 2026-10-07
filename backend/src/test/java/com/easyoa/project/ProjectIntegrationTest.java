package com.easyoa.project;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;

import com.easyoa.AbstractIntegrationTest;
import com.easyoa.project.domain.ProjectRole;
import com.easyoa.project.repository.ProjectMemberRepository;
import com.easyoa.user.domain.SystemRole;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 项目协作集成测试：生命周期流转、角色边界、OWNER 转让原子性、归档只读、数据范围与审计。
 */
class ProjectIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private ProjectMemberRepository projectMemberRepository;

    @Test
    @DisplayName("创建项目：创建者自动成为 OWNER，初始成员为 MEMBER")
    void createProjectMakesCreatorOwner() throws Exception {
        initializeSystemWithRoot("root");
        MockHttpSession rootSession = login("root", DEFAULT_PASSWORD);
        Long memberId = createUserViaApi(rootSession, "member", "Member", "MEMBER", null, null);

        Long projectId = createProjectViaApi(rootSession, "EasyOA v0.1.0", null, List.of(memberId));

        mockMvc.perform(get("/api/projects/{id}", projectId).session(rootSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.myRole").value("OWNER"))
                .andExpect(jsonPath("$.data.owner.displayName").value("root"))
                .andExpect(jsonPath("$.data.status").value("DRAFT"))
                .andExpect(jsonPath("$.data.members.length()").value(2))
                .andExpect(jsonPath("$.data.permissions.canTransferOwner").value(true))
                .andExpect(jsonPath("$.data.permissions.canArchive").value(true));
    }

    @Test
    @DisplayName("生命周期：只允许规范内的流转，归档后只读")
    void lifecycleTransitionsFollowRules() throws Exception {
        initializeSystemWithRoot("root");
        MockHttpSession rootSession = login("root", DEFAULT_PASSWORD);
        Long projectId = createProjectViaApi(rootSession, "生命周期项目", null, null);

        // DRAFT → COMPLETED 非法
        changeStatus(rootSession, projectId, "COMPLETED", 409);
        // DRAFT → ACTIVE 合法
        changeStatus(rootSession, projectId, "ACTIVE", 200);
        // ACTIVE → PAUSED / COMPLETED 合法
        changeStatus(rootSession, projectId, "PAUSED", 200);
        // PAUSED → COMPLETED 非法（规范只允许 PAUSED → ACTIVE）
        changeStatus(rootSession, projectId, "COMPLETED", 409);
        changeStatus(rootSession, projectId, "ACTIVE", 200);
        changeStatus(rootSession, projectId, "COMPLETED", 200);
        // COMPLETED → ACTIVE 重新激活
        changeStatus(rootSession, projectId, "ACTIVE", 200);

        // 归档后只读
        mockMvc.perform(post("/api/projects/{id}/archive", projectId).session(rootSession).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ARCHIVED"));

        changeStatus(rootSession, projectId, "ACTIVE", 409);
        mockMvc.perform(put("/api/projects/{id}", projectId)
                        .session(rootSession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", "改名尝试"))))
                .andExpect(status().isConflict());
        mockMvc.perform(post("/api/projects/{id}/members", projectId)
                        .session(rootSession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("userId", 1))))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("数据范围：非成员访问返回 404（不泄露项目是否存在），管理员可查看")
    void nonMemberCannotViewProject() throws Exception {
        initializeSystemWithRoot("root");
        MockHttpSession rootSession = login("root", DEFAULT_PASSWORD);
        Long outsiderId = createUserViaApi(rootSession, "kevin", "Kevin", "MEMBER", null, null);
        Long projectId = createProjectViaApi(rootSession, "私密项目", "ACTIVE", null);

        MockHttpSession outsiderSession = login("kevin", DEFAULT_PASSWORD);
        mockMvc.perform(get("/api/projects/{id}", projectId).session(outsiderSession))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));

        // 非成员无法通过列表看到该项目
        mockMvc.perform(get("/api/projects").session(outsiderSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(0));

        // 管理员可查看（管理 / 审计需要），但不是成员
        createUser("admin", DEFAULT_PASSWORD, SystemRole.ADMIN);
        MockHttpSession adminSession = login("admin", DEFAULT_PASSWORD);
        mockMvc.perform(get("/api/projects/{id}", projectId).session(adminSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.myRole").doesNotExist())
                .andExpect(jsonPath("$.data.permissions.canEditInfo").value(false));

        // 管理员列表可见全部项目
        mockMvc.perform(get("/api/projects").session(adminSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1));

        assertThat(outsiderId).isNotNull();
    }

    @Test
    @DisplayName("列表数据范围：成员只能看到自己参与的项目")
    void listScopesToMembership() throws Exception {
        initializeSystemWithRoot("root");
        MockHttpSession rootSession = login("root", DEFAULT_PASSWORD);
        createUser("member", DEFAULT_PASSWORD, SystemRole.MEMBER);
        MockHttpSession memberSession = login("member", DEFAULT_PASSWORD);
        Long memberUserId = userService.findByUsername("member").orElseThrow().getId();

        createProjectViaApi(rootSession, "项目A（含成员）", "ACTIVE", List.of(memberUserId));
        createProjectViaApi(rootSession, "项目B（不含成员）", "ACTIVE", null);

        mockMvc.perform(get("/api/projects").session(memberSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].name").value("项目A（含成员）"))
                .andExpect(jsonPath("$.data.items[0].myRole").value("MEMBER"))
                .andExpect(jsonPath("$.data.items[0].owner.displayName").value("root"));

        mockMvc.perform(get("/api/projects").session(rootSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(2));
    }

    @Test
    @DisplayName("角色边界：DEPUTY 可管理日常与普通成员，但不能设置副负责人 / 转让 / 归档 / 移除 OWNER")
    void deputyRoleBoundaries() throws Exception {
        initializeSystemWithRoot("root");
        MockHttpSession rootSession = login("root", DEFAULT_PASSWORD);
        createUser("member", DEFAULT_PASSWORD, SystemRole.MEMBER);
        createUser("kevin", DEFAULT_PASSWORD, SystemRole.MEMBER);
        MockHttpSession deputySession = login("member", DEFAULT_PASSWORD);
        Long deputyId = userService.findByUsername("member").orElseThrow().getId();
        Long kevinId = userService.findByUsername("kevin").orElseThrow().getId();
        Long rootId = userService.findByUsername("root").orElseThrow().getId();

        Long projectId = createProjectViaApi(rootSession, "角色边界项目", "ACTIVE", List.of(deputyId, kevinId));
        mockMvc.perform(put("/api/projects/{id}/deputy", projectId)
                        .session(rootSession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("userId", deputyId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.deputyOwner.userId").value(deputyId));

        // DEPUTY 允许：更新信息
        mockMvc.perform(put("/api/projects/{id}", projectId)
                        .session(deputySession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", "角色边界项目（副负责人改名）"))))
                .andExpect(status().isOk());

        // DEPUTY 允许：添加/移除普通成员
        Long extraId = createUserViaApi(rootSession, "linda", "Linda", "MEMBER", null, null);
        mockMvc.perform(post("/api/projects/{id}/members", projectId)
                        .session(deputySession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("userId", extraId))))
                .andExpect(status().isOk());
        mockMvc.perform(delete("/api/projects/{id}/members/{userId}", projectId, extraId)
                        .session(deputySession).with(csrf()))
                .andExpect(status().isOk());

        // DEPUTY 禁止：设置副负责人 / 转让 / 归档
        mockMvc.perform(put("/api/projects/{id}/deputy", projectId)
                        .session(deputySession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("userId", kevinId))))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/projects/{id}/transfer-owner", projectId)
                        .session(deputySession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("userId", kevinId))))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/projects/{id}/archive", projectId).session(deputySession).with(csrf()))
                .andExpect(status().isForbidden());

        // DEPUTY 禁止：移除 OWNER
        mockMvc.perform(delete("/api/projects/{id}/members/{userId}", projectId, rootId)
                        .session(deputySession).with(csrf()))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("普通成员不能管理项目配置")
    void memberCannotManageProject() throws Exception {
        initializeSystemWithRoot("root");
        MockHttpSession rootSession = login("root", DEFAULT_PASSWORD);
        createUser("member", DEFAULT_PASSWORD, SystemRole.MEMBER);
        MockHttpSession memberSession = login("member", DEFAULT_PASSWORD);
        Long memberId = userService.findByUsername("member").orElseThrow().getId();
        Long projectId = createProjectViaApi(rootSession, "成员权限项目", "ACTIVE", List.of(memberId));

        mockMvc.perform(put("/api/projects/{id}", projectId)
                        .session(memberSession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", "越权改名"))))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/projects/{id}/members", projectId)
                        .session(memberSession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("userId", memberId))))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/projects/{id}/status", projectId)
                        .session(memberSession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("status", "PAUSED"))))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("副负责人唯一：设置新副负责人时原副负责人自动降为普通成员")
    void setDeputyReplacesPrevious() throws Exception {
        initializeSystemWithRoot("root");
        MockHttpSession rootSession = login("root", DEFAULT_PASSWORD);
        createUser("member", DEFAULT_PASSWORD, SystemRole.MEMBER);
        createUser("kevin", DEFAULT_PASSWORD, SystemRole.MEMBER);
        Long memberId = userService.findByUsername("member").orElseThrow().getId();
        Long kevinId = userService.findByUsername("kevin").orElseThrow().getId();
        Long projectId = createProjectViaApi(rootSession, "副负责人项目", "ACTIVE", List.of(memberId, kevinId));

        setDeputy(rootSession, projectId, memberId, 200);
        setDeputy(rootSession, projectId, kevinId, 200)
                .andExpect(jsonPath("$.data.deputyOwner.userId").value(kevinId));

        // 数据层确认：只有一个副负责人，原副负责人降级为 MEMBER
        assertThat(projectMemberRepository.findByProjectId(projectId).stream()
                .filter(member -> member.getRole() == ProjectRole.DEPUTY_OWNER)
                .count()).isEqualTo(1);
        assertThat(projectMemberRepository.findByProjectIdAndUserId(projectId, memberId).orElseThrow().getRole())
                .isEqualTo(ProjectRole.MEMBER);

        // 取消副负责人
        mockMvc.perform(put("/api/projects/{id}/deputy", projectId)
                        .session(rootSession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.deputyOwner").doesNotExist());
    }

    @Test
    @DisplayName("OWNER 转让：原子且唯一，原 OWNER 降为成员；只能转让给项目成员")
    void transferOwnerIsAtomic() throws Exception {
        initializeSystemWithRoot("root");
        MockHttpSession rootSession = login("root", DEFAULT_PASSWORD);
        createUser("member", DEFAULT_PASSWORD, SystemRole.MEMBER);
        Long memberId = userService.findByUsername("member").orElseThrow().getId();
        Long rootId = userService.findByUsername("root").orElseThrow().getId();
        Long projectId = createProjectViaApi(rootSession, "转让项目", "ACTIVE", List.of(memberId));

        // 转让给非成员 → 422
        Long outsiderId = createUserViaApi(rootSession, "linda", "Linda", "MEMBER", null, null);
        mockMvc.perform(post("/api/projects/{id}/transfer-owner", projectId)
                        .session(rootSession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("userId", outsiderId))))
                .andExpect(status().isUnprocessableEntity());

        // 正常转让
        mockMvc.perform(post("/api/projects/{id}/transfer-owner", projectId)
                        .session(rootSession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("userId", memberId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.owner.userId").value(memberId))
                .andExpect(jsonPath("$.data.myRole").value("MEMBER"))
                .andExpect(jsonPath("$.data.permissions.canTransferOwner").value(false));

        assertThat(projectMemberRepository.findByProjectId(projectId).stream()
                .filter(member -> member.getRole() == ProjectRole.OWNER)
                .count()).isEqualTo(1);

        // 原 OWNER 已失去管理权
        mockMvc.perform(put("/api/projects/{id}", projectId)
                        .session(rootSession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", "原 OWNER 改名尝试"))))
                .andExpect(status().isForbidden());

        // 新 OWNER 具备完整权限
        MockHttpSession newOwnerSession = login("member", DEFAULT_PASSWORD);
        mockMvc.perform(put("/api/projects/{id}", projectId)
                        .session(newOwnerSession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", "新负责人改名"))))
                .andExpect(status().isOk());
        assertThat(rootId).isNotNull();
    }

    @Test
    @DisplayName("项目负责人不能被移除，也不能移除自己（须先转让）")
    void ownerCannotBeRemoved() throws Exception {
        initializeSystemWithRoot("root");
        MockHttpSession rootSession = login("root", DEFAULT_PASSWORD);
        Long rootId = userService.findByUsername("root").orElseThrow().getId();
        Long projectId = createProjectViaApi(rootSession, "唯一负责人项目", "ACTIVE", null);

        mockMvc.perform(delete("/api/projects/{id}/members/{userId}", projectId, rootId)
                        .session(rootSession).with(csrf()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"));
    }

    @Test
    @DisplayName("项目校验：同名项目拒绝、进度范围校验、重复成员拒绝")
    void projectValidations() throws Exception {
        initializeSystemWithRoot("root");
        MockHttpSession rootSession = login("root", DEFAULT_PASSWORD);
        Long memberId = createUserViaApi(rootSession, "member", "Member", "MEMBER", null, null);
        Long projectId = createProjectViaApi(rootSession, "唯一项目", "ACTIVE", List.of(memberId));

        mockMvc.perform(post("/api/projects")
                        .session(rootSession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", "唯一项目"))))
                .andExpect(status().isConflict());

        mockMvc.perform(put("/api/projects/{id}/progress", projectId)
                        .session(rootSession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("progress", 130))))
                .andExpect(status().isBadRequest());

        mockMvc.perform(put("/api/projects/{id}/progress", projectId)
                        .session(rootSession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("progress", 66))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.progress").value(66));

        mockMvc.perform(post("/api/projects/{id}/members", projectId)
                        .session(rootSession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("userId", memberId))))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("项目变更写入审计日志，且工作台 KPI 反映真实项目数")
    void auditsAndWorkspaceIntegration() throws Exception {
        initializeSystemWithRoot("root");
        MockHttpSession rootSession = login("root", DEFAULT_PASSWORD);
        createUser("member", DEFAULT_PASSWORD, SystemRole.MEMBER);
        Long memberId = userService.findByUsername("member").orElseThrow().getId();
        Long projectId = createProjectViaApi(rootSession, "审计项目", "ACTIVE", List.of(memberId));

        mockMvc.perform(post("/api/projects/{id}/transfer-owner", projectId)
                        .session(rootSession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("userId", memberId))))
                .andExpect(status().isOk());

        var created = auditService.query(com.easyoa.audit.dto.AuditLogQuery.of(
                null, "PROJECT_CREATED", null, null, null, null, null, 1, 10));
        assertThat(created.items()).isNotEmpty();
        assertThat(created.items().get(0).resourceId()).isEqualTo(String.valueOf(projectId));

        var transferred = auditService.query(com.easyoa.audit.dto.AuditLogQuery.of(
                null, "PROJECT_OWNER_TRANSFERRED", null, null, null, null, null, 1, 10));
        assertThat(transferred.items()).isNotEmpty();
        assertThat(transferred.items().get(0).riskLevel()).isEqualTo("CRITICAL");

        // 工作台：成员视角 KPI 与项目进度列表（由新 OWNER 查看）
        MockHttpSession newOwnerSession = login("member", DEFAULT_PASSWORD);
        mockMvc.perform(get("/api/workspace/summary").session(newOwnerSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.kpis.activeProjects").value(1))
                .andExpect(jsonPath("$.data.projectProgress.length()").value(1))
                .andExpect(jsonPath("$.data.projectProgress[0].name").value("审计项目"))
                .andExpect(jsonPath("$.data.projectProgress[0].myRole").value("OWNER"));
    }

    // --- 辅助 -----------------------------------------------------------------

    private org.springframework.test.web.servlet.ResultActions changeStatus(MockHttpSession session, Long projectId,
            String status, int expectedStatus) throws Exception {
        return mockMvc.perform(post("/api/projects/{id}/status", projectId)
                        .session(session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("status", status))))
                .andExpect(status().is(expectedStatus));
    }

    private org.springframework.test.web.servlet.ResultActions setDeputy(MockHttpSession session, Long projectId,
            Long userId, int expectedStatus) throws Exception {
        return mockMvc.perform(put("/api/projects/{id}/deputy", projectId)
                        .session(session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("userId", userId))))
                .andExpect(status().is(expectedStatus));
    }
}