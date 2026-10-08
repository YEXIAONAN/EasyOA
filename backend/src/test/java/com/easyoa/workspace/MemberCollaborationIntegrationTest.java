package com.easyoa.workspace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MvcResult;

import com.easyoa.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * 成员协作概览（团队页面成员档案的「参与项目 / 近期任务」）集成测试。
 *
 * <p>重点验证数据范围：查看他人档案不得扩大自己的可见范围——
 * 非管理员只能看到「双方共有」的项目与任务。
 */
class MemberCollaborationIntegrationTest extends AbstractIntegrationTest {

    @Test
    @DisplayName("管理员查看成员档案：可见其参与项目与该成员的未结束任务")
    void adminSeesMemberProjectsAndTasks() throws Exception {
        initializeSystemWithRoot("root");
        MockHttpSession rootSession = login("root", DEFAULT_PASSWORD);
        Long memberId = createUserViaApi(rootSession, "member", "Member", "MEMBER", null, null);
        Long projectId = createProjectViaApi(rootSession, "协作项目", "ACTIVE", List.of(memberId));
        createTask(rootSession, projectId, "成员进行中的任务", memberId);
        createTask(rootSession, projectId, "负责人自己的任务", null);

        MvcResult result = mockMvc
                .perform(get("/api/workspace/members/{id}/collaboration", memberId).session(rootSession))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode data = objectMapper.readTree(result.getResponse().getContentAsString()).path("data");

        JsonNode project = data.path("projects").path(0);
        assertThat(project.path("projectId").asLong()).isEqualTo(projectId);
        assertThat(project.path("name").asText()).isEqualTo("协作项目");
        assertThat(project.path("role").asText()).isEqualTo("MEMBER");
        assertThat(project.path("status").asText()).isEqualTo("ACTIVE");

        assertThat(data.path("recentTasks")).hasSize(1);
        JsonNode task = data.path("recentTasks").path(0);
        assertThat(task.path("title").asText()).isEqualTo("成员进行中的任务");
        assertThat(task.path("projectName").asText()).isEqualTo("协作项目");
        assertThat(task.path("statusType").asText()).isEqualTo("TODO");
        assertThat(task.path("overdue").asBoolean()).isFalse();
    }

    @Test
    @DisplayName("数据范围：成员只能看到与自己共有的项目与任务，看不到他人的私有项目")
    void memberScopeLimitedToSharedProjects() throws Exception {
        UserFixture fixture = setupTwoProjects();

        // 目标成员 kevin 同时在「共有项目」与「私有项目」中
        MvcResult result = mockMvc
                .perform(get("/api/workspace/members/{id}/collaboration", fixture.kevinId())
                        .session(fixture.memberSession()))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode data = objectMapper.readTree(result.getResponse().getContentAsString()).path("data");

        // 只能看到共有项目
        assertThat(data.path("projects")).hasSize(1);
        assertThat(data.path("projects").path(0).path("name").asText()).isEqualTo("共有项目");
        // 只能看到共有项目下的任务
        assertThat(data.path("recentTasks")).hasSize(1);
        assertThat(data.path("recentTasks").path(0).path("title").asText()).isEqualTo("共有项目任务");
    }

    @Test
    @DisplayName("数据范围：没有任何共同项目的成员之间返回空列表（不泄露项目是否存在）")
    void unrelatedMemberSeesNothing() throws Exception {
        UserFixture fixture = setupTwoProjects();
        Long outsiderId = createUserViaApi(fixture.rootSession(), "outsider", "Outsider", "MEMBER", null, null);
        MockHttpSession outsiderSession = login("outsider", DEFAULT_PASSWORD);

        mockMvc.perform(get("/api/workspace/members/{id}/collaboration", fixture.kevinId()).session(outsiderSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.projects.length()").value(0))
                .andExpect(jsonPath("$.data.recentTasks.length()").value(0));
    }

    @Test
    @DisplayName("管理员查看他人档案不受共有项目限制；目标不存在返回 404；未登录返回 401")
    void boundaryCases() throws Exception {
        UserFixture fixture = setupTwoProjects();

        // 管理员可见目标成员的全部项目（共有 + 私有）
        MvcResult result = mockMvc
                .perform(get("/api/workspace/members/{id}/collaboration", fixture.kevinId())
                        .session(fixture.rootSession()))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode data = objectMapper.readTree(result.getResponse().getContentAsString()).path("data");
        assertThat(data.path("projects")).hasSize(2);

        mockMvc.perform(get("/api/workspace/members/{id}/collaboration", 999999).session(fixture.rootSession()))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/workspace/members/{id}/collaboration", fixture.kevinId()))
                .andExpect(status().isUnauthorized());
    }

    // --- 辅助 -----------------------------------------------------------------

    private record UserFixture(Long projectId, Long privateProjectId, Long kevinId, MockHttpSession rootSession,
            MockHttpSession memberSession) {
    }

    /**
     * 场景：root（管理员）+ member + kevin。
     * 「共有项目」含 member 与 kevin；「私有项目」只含 kevin（member 不可见）。
     */
    private UserFixture setupTwoProjects() throws Exception {
        initializeSystemWithRoot("root");
        MockHttpSession rootSession = login("root", DEFAULT_PASSWORD);
        Long memberId = createUserViaApi(rootSession, "member", "Member", "MEMBER", null, null);
        Long kevinId = createUserViaApi(rootSession, "kevin", "Kevin", "MEMBER", null, null);

        Long sharedProject = createProjectViaApi(rootSession, "共有项目", "ACTIVE", List.of(memberId, kevinId));
        Long privateProject = createProjectViaApi(rootSession, "私有项目", "ACTIVE", List.of(kevinId));

        createTask(rootSession, sharedProject, "共有项目任务", kevinId);
        createTask(rootSession, privateProject, "私有项目任务", kevinId);

        return new UserFixture(sharedProject, privateProject, kevinId, rootSession, login("member", DEFAULT_PASSWORD));
    }

    private Long createTask(MockHttpSession session, Long projectId, String title, Long primaryAssigneeId)
            throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("title", title);
        if (primaryAssigneeId != null) {
            body.put("primaryAssigneeId", primaryAssigneeId);
        }
        MvcResult result = mockMvc.perform(post("/api/projects/{id}/tasks", projectId)
                        .session(session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(body)))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("data").path("id").asLong();
    }
}