package com.easyoa.workspace;

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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Phase 7 工作台集成测试：通知中心（含深链 / 未读 / 标记已读）、全局搜索分组与数据范围、
 * Activity Feed（业务动态，不是审计日志）。
 */
class WorkbenchIntegrationTest extends AbstractIntegrationTest {

    private record Fixture(Long projectId, Long taskId, Long rootId, Long memberId, Long kevinId, Long outsiderId,
            MockHttpSession rootSession, MockHttpSession memberSession) {
    }

    private Fixture setup() throws Exception {
        initializeSystemWithRoot("root");
        MockHttpSession rootSession = login("root", DEFAULT_PASSWORD);
        Long memberId = createUserViaApi(rootSession, "member", "Member", "MEMBER", null, null);
        Long kevinId = createUserViaApi(rootSession, "kevin", "Kevin", "MEMBER", null, null);
        createUser("outsider", DEFAULT_PASSWORD, com.easyoa.user.domain.SystemRole.MEMBER);
        Long outsiderId = userService.findByUsername("outsider").orElseThrow().getId();
        Long projectId = createProjectViaApi(rootSession, "工作台项目", "ACTIVE", List.of(memberId, kevinId));
        Long rootId = userService.findByUsername("root").orElseThrow().getId();

        Map<String, Object> body = new HashMap<>();
        body.put("title", "工作台任务");
        body.put("primaryAssigneeId", memberId);
        MvcResult result = mockMvc.perform(post("/api/projects/{id}/tasks", projectId)
                        .session(rootSession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(body)))
                .andExpect(status().isOk())
                .andReturn();
        Long taskId = objectMapper.readTree(result.getResponse().getContentAsString()).path("data").path("id").asLong();
        return new Fixture(projectId, taskId, rootId, memberId, kevinId, outsiderId, rootSession,
                login("member", DEFAULT_PASSWORD));
    }

    @Test
    @DisplayName("通知中心：任务分配 / @ 提及 / 回复 触发通知，含深链、未读数与标记已读")
    void notificationsWithDeepLink() throws Exception {
        Fixture f = setup();

        // 任务分配 → 主负责人收到 TASK_ASSIGNED，深链直达看板任务
        mockMvc.perform(get("/api/notifications/unread-count").session(f.memberSession()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.count").value(1));
        MvcResult listed = mockMvc.perform(get("/api/notifications").session(f.memberSession()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].type").value("TASK_ASSIGNED"))
                .andExpect(jsonPath("$.data.items[0].actorName").value("root"))
                .andReturn();
        JsonNode notification = objectMapper.readTree(listed.getResponse().getContentAsString())
                .path("data").path("items").path(0);
        assertThat(notification.path("link").asText())
                .isEqualTo("/projects/" + f.projectId() + "/board?task=" + f.taskId());
        Long notificationId = notification.path("id").asLong();

        // @ 提及：root 评论并 @ member（member 已有 1 条未读，这里 member 是触发者？不是：root 触发）
        mockMvc.perform(post("/api/tasks/{id}/comments", f.taskId())
                        .session(f.rootSession()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("content", "@Member 请确认排期", "mentionUserIds", List.of(f.memberId())))))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/notifications").session(f.memberSession()).param("unreadOnly", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(2))
                .andExpect(jsonPath("$.data.items[0].type").value("MENTION"));

        // member 回复 root 的评论 → root 收到 COMMENT_REPLY
        Long rootCommentId = latestCommentId(f.taskId(), f.memberSession());
        mockMvc.perform(post("/api/tasks/{id}/comments", f.taskId())
                        .session(f.memberSession()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("content", "排期没问题", "parentId", rootCommentId))))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/notifications").session(f.rootSession()).param("unreadOnly", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].type").value("COMMENT_REPLY"));

        // 标记已读（未读数归零）；越权读取他人通知返回 404
        mockMvc.perform(post("/api/notifications/{id}/read", notificationId).session(f.memberSession()).with(csrf()))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/notifications/unread-count").session(f.memberSession()))
                .andExpect(jsonPath("$.data.count").value(1));
        mockMvc.perform(post("/api/notifications/{id}/read", notificationId).session(f.rootSession()).with(csrf()))
                .andExpect(status().isNotFound());

        mockMvc.perform(post("/api/notifications/read-all").session(f.memberSession()).with(csrf()))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/notifications/unread-count").session(f.memberSession()))
                .andExpect(jsonPath("$.data.count").value(0));
    }

    @Test
    @DisplayName("审批通知：提交提醒审批人、通过提醒申请人")
    void approvalNotifications() throws Exception {
        Fixture f = setup();
        // 模板：单节点固定审批人 admin（用 root 建模板）
        createUser("admin", DEFAULT_PASSWORD, com.easyoa.user.domain.SystemRole.ADMIN);
        Long adminId = userService.findByUsername("admin").orElseThrow().getId();
        Long templateId = createTemplate(f.rootSession(), "通知验证审批", adminId);

        MvcResult draft = mockMvc.perform(post("/api/approvals")
                        .session(f.memberSession()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("templateId", templateId, "title", "通知验证申请",
                                "values", Map.of("reason", "测试通知")))))
                .andExpect(status().isOk())
                .andReturn();
        Long instanceId = objectMapper.readTree(draft.getResponse().getContentAsString()).path("data").path("id")
                .asLong();
        mockMvc.perform(post("/api/approvals/{id}/submit", instanceId).session(f.memberSession()).with(csrf()))
                .andExpect(status().isOk());

        MockHttpSession adminSession = login("admin", DEFAULT_PASSWORD);
        mockMvc.perform(get("/api/notifications").session(adminSession).param("unreadOnly", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].type").value("APPROVAL_PENDING"))
                .andExpect(jsonPath("$.data.items[0].link").value("/approvals/" + instanceId));

        mockMvc.perform(post("/api/approvals/{id}/approve", instanceId)
                        .session(adminSession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("comment", "同意"))))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/notifications").session(f.memberSession()).param("unreadOnly", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].type").value("APPROVAL_APPROVED"));
    }

    @Test
    @DisplayName("全局搜索：按类别分组返回，普通用户仅能搜到自己的范围")
    void globalSearch() throws Exception {
        Fixture f = setup();

        mockMvc.perform(get("/api/search").param("q", "工作台").session(f.memberSession()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.projects.length()").value(1))
                .andExpect(jsonPath("$.data.projects[0].title").value("工作台项目"))
                .andExpect(jsonPath("$.data.projects[0].link").value("/projects/" + f.projectId()))
                .andExpect(jsonPath("$.data.tasks.length()").value(1))
                .andExpect(jsonPath("$.data.tasks[0].link")
                        .value("/projects/" + f.projectId() + "/board?task=" + f.taskId()));

        // 成员检索对所有登录用户开放
        mockMvc.perform(get("/api/search").param("q", "Member").session(f.memberSession()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.users.length()").value(1))
                .andExpect(jsonPath("$.data.users[0].title").value("Member"));

        // 数据范围：非项目成员搜不到任务与项目；管理员全量可见
        MockHttpSession outsiderSession = login("outsider", DEFAULT_PASSWORD);
        mockMvc.perform(get("/api/search").param("q", "工作台").session(outsiderSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.projects.length()").value(0))
                .andExpect(jsonPath("$.data.tasks.length()").value(0));
        mockMvc.perform(get("/api/search").param("q", "工作台").session(f.rootSession()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.projects.length()").value(1));
    }

    @Test
    @DisplayName("Activity Feed：任务创建 / 评论 / 完成 / 状态调整均进入业务动态，且受数据范围限制")
    void activityFeed() throws Exception {
        Fixture f = setup();

        // member 评论
        mockMvc.perform(post("/api/tasks/{id}/comments", f.taskId())
                        .session(f.memberSession()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("content", "开始处理"))))
                .andExpect(status().isOk());
        // root 把任务拖到「已完成」
        Long doneStatusId = statusIdByName(f.rootSession(), f.projectId(), "已完成");
        mockMvc.perform(post("/api/tasks/{id}/status", f.taskId())
                        .session(f.rootSession()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("statusId", doneStatusId))))
                .andExpect(status().isOk());

        MvcResult result = mockMvc.perform(get("/api/workspace/activity").session(f.memberSession()).param("limit", "10"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode items = objectMapper.readTree(result.getResponse().getContentAsString()).path("data");
        java.util.Set<String> types = new java.util.HashSet<>();
        items.forEach(item -> types.add(item.path("type").asText()));
        assertThat(types).contains("TASK_CREATED", "COMMENT_CREATED", "TASK_COMPLETED", "TASK_STATUS_CHANGED");
        assertThat(items.get(0).path("link").asText()).contains("/projects/" + f.projectId());

        // 工作台摘要内联 Activity（项目动态区块）
        mockMvc.perform(get("/api/workspace/summary").session(f.memberSession()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.activity.length()").value(org.hamcrest.Matchers.greaterThan(0)));

        // 数据范围：非项目成员（且非管理员）看不到该项目的动态
        MockHttpSession outsiderSession = login("outsider", DEFAULT_PASSWORD);
        MvcResult outsiderResult = mockMvc.perform(get("/api/workspace/activity").session(outsiderSession))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode outsiderItems = objectMapper.readTree(outsiderResult.getResponse().getContentAsString())
                .path("data");
        assertThat(outsiderItems.size()).isZero();
    }

    // --- 辅助 -------------------------------------------------------------------

    private Long latestCommentId(Long taskId, MockHttpSession session) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/tasks/{id}/comments", taskId).session(session))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .path("data").path("items").path(0).path("id").asLong();
    }

    private Long statusIdByName(MockHttpSession session, Long projectId, String name) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/projects/{id}/task-statuses", projectId).session(session))
                .andExpect(status().isOk())
                .andReturn();
        for (JsonNode node : objectMapper.readTree(result.getResponse().getContentAsString()).path("data")) {
            if (name.equals(node.path("name").asText())) {
                return node.path("id").asLong();
            }
        }
        throw new IllegalStateException("缺少状态：" + name);
    }

    private Long createTemplate(MockHttpSession session, String name, Long approverId) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/approval-templates")
                        .session(session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "name", name,
                                "description", name,
                                "formFields", List.of(Map.of("key", "reason", "label", "事由", "type", "TEXT",
                                        "required", false)),
                                "nodes", List.of(Map.of(
                                        "name", "审批节点",
                                        "mode", "ANY_ONE",
                                        "approvers", List.of(Map.of("type", "FIXED_USER", "userId", approverId))))))))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("data").path("id").asLong();
    }
}