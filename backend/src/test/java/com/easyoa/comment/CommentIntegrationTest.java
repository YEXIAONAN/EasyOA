package com.easyoa.comment;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MvcResult;

import com.easyoa.AbstractIntegrationTest;
import com.easyoa.audit.dto.AuditLogQuery;
import com.fasterxml.jackson.databind.JsonNode;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 评论集成测试：发表 / 回复 / @成员 / 编辑历史 / 撤回（保留原始内容）/ 数据范围。
 */
class CommentIntegrationTest extends AbstractIntegrationTest {

    private record Fixture(Long projectId, Long taskId, Long rootId, Long memberId, Long kevinId,
            MockHttpSession rootSession, MockHttpSession memberSession) {
    }

    private Fixture setup() throws Exception {
        initializeSystemWithRoot("root");
        MockHttpSession rootSession = login("root", DEFAULT_PASSWORD);
        Long memberId = createUserViaApi(rootSession, "member", "Member", "MEMBER", null, null);
        Long kevinId = createUserViaApi(rootSession, "kevin", "Kevin", "MEMBER", null, null);
        createUser("outsider", DEFAULT_PASSWORD, com.easyoa.user.domain.SystemRole.MEMBER);
        Long projectId = createProjectViaApi(rootSession, "评论项目", "ACTIVE", List.of(memberId, kevinId));
        Long rootId = userService.findByUsername("root").orElseThrow().getId();

        Map<String, Object> body = new HashMap<>();
        body.put("title", "评论任务");
        body.put("primaryAssigneeId", memberId);
        MvcResult result = mockMvc.perform(post("/api/projects/{id}/tasks", projectId)
                        .session(rootSession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(body)))
                .andExpect(status().isOk())
                .andReturn();
        Long taskId = objectMapper.readTree(result.getResponse().getContentAsString()).path("data").path("id").asLong();
        return new Fixture(projectId, taskId, rootId, memberId, kevinId, rootSession,
                login("member", DEFAULT_PASSWORD));
    }

    @Test
    @DisplayName("评论与回复：支持一级回复，拒绝回复的回复与跨任务回复")
    void createCommentAndReply() throws Exception {
        Fixture f = setup();

        Long commentId = createComment(f.memberSession(), f.taskId(), "第一条评论", null);
        // 回复顶层评论
        Long replyId = createComment(kevinSession(), f.taskId(), "收到，我来处理", commentId);
        // 回复的回复（二级）被拒绝
        mockMvc.perform(post("/api/tasks/{id}/comments", f.taskId())
                        .session(kevinSession()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("content", "二级回复", "parentId", replyId))))
                .andExpect(status().isUnprocessableEntity());

        mockMvc.perform(get("/api/tasks/{id}/comments", f.taskId()).session(f.memberSession()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].id").value(commentId))
                .andExpect(jsonPath("$.data.items[0].content").value("第一条评论"))
                .andExpect(jsonPath("$.data.items[0].replies.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].replies[0].content").value("收到，我来处理"))
                .andExpect(jsonPath("$.data.items[0].replies[0].parentId").value(commentId));
    }

    @Test
    @DisplayName("@成员：只能 @ 项目内成员，结果随评论返回")
    void mentionMembers() throws Exception {
        Fixture f = setup();

        mockMvc.perform(post("/api/tasks/{id}/comments", f.taskId())
                        .session(f.memberSession()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("content", "@Kevin 帮忙确认", "mentionUserIds", List.of(f.kevinId())))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.mentions.length()").value(1))
                .andExpect(jsonPath("$.data.mentions[0].id").value(f.kevinId()));

        // @非项目成员被拒绝
        Long outsiderId = userService.findByUsername("outsider").orElseThrow().getId();
        mockMvc.perform(post("/api/tasks/{id}/comments", f.taskId())
                        .session(f.memberSession()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("content", "@外部人", "mentionUserIds", List.of(outsiderId)))))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    @DisplayName("编辑评论：仅作者可编辑，历史保留原始与每次修改，审计 COMMENT_EDITED")
    void editKeepsHistory() throws Exception {
        Fixture f = setup();
        Long commentId = createComment(f.memberSession(), f.taskId(), "原始内容", null);

        // 非作者不能编辑
        mockMvc.perform(put("/api/comments/{id}", commentId)
                        .session(kevinSession()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("content", "篡改"))))
                .andExpect(status().isForbidden());

        mockMvc.perform(put("/api/comments/{id}", commentId)
                        .session(f.memberSession()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("content", "第一次修改"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").value("第一次修改"))
                .andExpect(jsonPath("$.data.edited").value(true));

        // 历史：v1 原始内容 + v2 修改后内容
        mockMvc.perform(get("/api/comments/{id}/versions", commentId).session(f.memberSession()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].versionNo").value(1))
                .andExpect(jsonPath("$.data[0].content").value("原始内容"))
                .andExpect(jsonPath("$.data[1].versionNo").value(2))
                .andExpect(jsonPath("$.data[1].content").value("第一次修改"));

        // 项目负责人也可查看历史（审计需要）
        mockMvc.perform(get("/api/comments/{id}/versions", commentId).session(f.rootSession()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2));

        // 普通成员（非作者、非项目负责人）不能查看历史
        mockMvc.perform(get("/api/comments/{id}/versions", commentId).session(kevinSession()))
                .andExpect(status().isForbidden());

        var audit = auditService.query(AuditLogQuery.of(null, "COMMENT_EDITED", null, null, null, null, null, 1, 10));
        assertThat(audit.items()).isNotEmpty();
        assertThat(audit.items().get(0).resourceId()).isEqualTo(String.valueOf(commentId));
    }

    @Test
    @DisplayName("撤回评论：仅作者可撤回，界面不返回内容但数据库保留原始评论，审计 COMMENT_WITHDRAWN")
    void withdrawKeepsOriginal() throws Exception {
        Fixture f = setup();
        Long commentId = createComment(f.memberSession(), f.taskId(), "会被撤回的评论", null);

        // 非作者不能撤回
        mockMvc.perform(post("/api/comments/{id}/withdraw", commentId).session(kevinSession()).with(csrf()))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/comments/{id}/withdraw", commentId).session(f.memberSession()).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.withdrawn").value(true))
                .andExpect(jsonPath("$.data.content").doesNotExist());

        // 列表：撤回占位（withdrawn=true，无内容）
        mockMvc.perform(get("/api/tasks/{id}/comments", f.taskId()).session(kevinSession()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].withdrawn").value(true))
                .andExpect(jsonPath("$.data.items[0].content").doesNotExist());

        // 重复撤回冲突
        mockMvc.perform(post("/api/comments/{id}/withdraw", commentId).session(f.memberSession()).with(csrf()))
                .andExpect(status().isConflict());

        // 数据库保留原始评论（不物理删除）
        String content = jdbcTemplate.queryForObject("select content from comments where id = ?", String.class,
                commentId);
        assertThat(content).isEqualTo("会被撤回的评论");
        Long versionCount = jdbcTemplate.queryForObject(
                "select count(*) from comment_versions where comment_id = ?", Long.class, commentId);
        assertThat(versionCount).isEqualTo(1L);

        var audit = auditService.query(
                AuditLogQuery.of(null, "COMMENT_WITHDRAWN", null, null, null, null, null, 1, 10));
        assertThat(audit.items()).isNotEmpty();
    }

    @Test
    @DisplayName("编辑带 @提及 的评论：保留原提及不冲突，可增删提及（回归：先删后插触发唯一约束）")
    void editCommentWithMentions() throws Exception {
        Fixture f = setup();
        Long lindaId = createUserViaApi(f.rootSession(), "linda", "Linda", "MEMBER", null, null);
        // 把 Linda 加入项目（创建项目时只有 member / kevin）
        mockMvc.perform(post("/api/projects/{id}/members", f.projectId())
                        .session(f.rootSession()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("userId", lindaId))))
                .andExpect(status().isOk());

        MvcResult created = mockMvc.perform(post("/api/tasks/{id}/comments", f.taskId())
                        .session(f.memberSession()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("content", "@Kevin 请确认", "mentionUserIds", List.of(f.kevinId())))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.mentions.length()").value(1))
                .andReturn();
        Long commentId = objectMapper.readTree(created.getResponse().getContentAsString())
                .path("data").path("id").asLong();

        // 保留原 @ + 新增 @Linda：不应触发唯一约束冲突
        mockMvc.perform(put("/api/comments/{id}", commentId)
                        .session(f.memberSession()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("content", "@Kevin @Linda 请确认", "mentionUserIds",
                                List.of(f.kevinId(), lindaId)))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.mentions.length()").value(2))
                .andExpect(jsonPath("$.data.edited").value(true));

        // 移除 @Kevin：仅保留 Linda
        mockMvc.perform(put("/api/comments/{id}", commentId)
                        .session(f.memberSession()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("content", "@Linda 请确认", "mentionUserIds", List.of(lindaId)))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.mentions.length()").value(1))
                .andExpect(jsonPath("$.data.mentions[0].id").value(lindaId));
    }

    @Test
    @DisplayName("数据范围：非项目成员访问任务评论一律 404")
    void outsiderCannotAccessComments() throws Exception {
        Fixture f = setup();
        MockHttpSession outsiderSession = login("outsider", DEFAULT_PASSWORD);

        mockMvc.perform(get("/api/tasks/{id}/comments", f.taskId()).session(outsiderSession))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
        mockMvc.perform(post("/api/tasks/{id}/comments", f.taskId())
                        .session(outsiderSession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("content", "越权评论"))))
                .andExpect(status().isNotFound());
    }

    // --- 辅助 -----------------------------------------------------------------

    private Long createComment(MockHttpSession session, Long taskId, String content, Long parentId)
            throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("content", content);
        if (parentId != null) {
            body.put("parentId", parentId);
        }
        MvcResult result = mockMvc.perform(post("/api/tasks/{id}/comments", taskId)
                        .session(session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(body)))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("data").path("id").asLong();
    }

    private MockHttpSession kevinSession() throws Exception {
        return login("kevin", DEFAULT_PASSWORD);
    }
}