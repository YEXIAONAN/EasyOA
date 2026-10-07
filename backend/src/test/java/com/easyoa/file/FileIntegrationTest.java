package com.easyoa.file;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MvcResult;

import com.easyoa.AbstractIntegrationTest;
import com.easyoa.audit.dto.AuditLogQuery;
import com.fasterxml.jackson.databind.JsonNode;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 附件集成测试：上传（元数据 + 策略校验）、受控下载（权限链 + 敏感审计）、软删除、评论附件挂载。
 */
class FileIntegrationTest extends AbstractIntegrationTest {

    /** "hello" 的 SHA-256（用于校验摘要计算）。 */
    private static final String HELLO_SHA256 = "2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824";

    private record Fixture(Long projectId, Long taskId, Long rootId, Long memberId,
            MockHttpSession rootSession, MockHttpSession memberSession) {
    }

    private Fixture setup() throws Exception {
        initializeSystemWithRoot("root");
        MockHttpSession rootSession = login("root", DEFAULT_PASSWORD);
        Long memberId = createUserViaApi(rootSession, "member", "Member", "MEMBER", null, null);
        Long lindaId = createUserViaApi(rootSession, "linda", "Linda", "MEMBER", null, null);
        createUser("outsider", DEFAULT_PASSWORD, com.easyoa.user.domain.SystemRole.MEMBER);
        Long projectId = createProjectViaApi(rootSession, "附件项目", "ACTIVE", List.of(memberId, lindaId));
        Long rootId = userService.findByUsername("root").orElseThrow().getId();

        Map<String, Object> body = new HashMap<>();
        body.put("title", "附件任务");
        body.put("primaryAssigneeId", memberId);
        MvcResult result = mockMvc.perform(post("/api/projects/{id}/tasks", projectId)
                        .session(rootSession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(body)))
                .andExpect(status().isOk())
                .andReturn();
        Long taskId = objectMapper.readTree(result.getResponse().getContentAsString()).path("data").path("id").asLong();
        return new Fixture(projectId, taskId, rootId, memberId, rootSession, login("member", DEFAULT_PASSWORD));
    }

    @Test
    @DisplayName("上传附件：元数据齐全（含 SHA-256），列表可见；下载走受控入口并对非上传者写入敏感审计")
    void uploadListDownload() throws Exception {
        Fixture f = setup();
        Long fileId = uploadTextFile(f.memberSession(), f.taskId(), "报告.txt", "hello");

        // 元数据
        mockMvc.perform(get("/api/tasks/{id}/files", f.taskId()).session(f.rootSession()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].id").value(fileId))
                .andExpect(jsonPath("$.data[0].originalName").value("报告.txt"))
                .andExpect(jsonPath("$.data[0].size").value(5))
                .andExpect(jsonPath("$.data[0].sha256").value(HELLO_SHA256))
                .andExpect(jsonPath("$.data[0].downloadUrl").value("/api/files/" + fileId));

        // 上传者本人下载：内容一致
        MvcResult own = mockMvc.perform(get("/api/files/{id}", fileId).session(f.memberSession()))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andReturn();
        assertThat(own.getResponse().getContentAsString()).isEqualTo("hello");

        // 其他项目成员下载：新开一个 session（root 是项目负责人）
        mockMvc.perform(get("/api/files/{id}", fileId).session(f.rootSession()))
                .andExpect(status().isOk());
        var sensitive = auditService.query(
                AuditLogQuery.of(null, "FILE_DOWNLOADED_SENSITIVE", null, null, null, null, null, 1, 10));
        assertThat(sensitive.items()).isNotEmpty();
        assertThat(sensitive.items().get(0).resourceId()).isEqualTo(String.valueOf(fileId));

        // 非项目成员：404（不泄露文件是否存在）
        MockHttpSession outsiderSession = login("outsider", DEFAULT_PASSWORD);
        mockMvc.perform(get("/api/files/{id}", fileId).session(outsiderSession))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("上传策略：危险扩展名 / 空文件 / 超限一律拒绝，文件名只保留路径末段")
    void uploadPolicy() throws Exception {
        Fixture f = setup();

        mockMvc.perform(multipart("/api/tasks/{id}/files", f.taskId())
                        .file(new MockMultipartFile("file", "hack.sh", "text/plain", "#!/bin/sh".getBytes()))
                        .session(f.memberSession()).with(csrf()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value("出于安全考虑，禁止上传该类型的文件"));

        mockMvc.perform(multipart("/api/tasks/{id}/files", f.taskId())
                        .file(new MockMultipartFile("file", "empty.txt", "text/plain", new byte[0]))
                        .session(f.memberSession()).with(csrf()))
                .andExpect(status().isUnprocessableEntity());

        // 测试环境限制 1MB：2MB 文件被拒绝
        mockMvc.perform(multipart("/api/tasks/{id}/files", f.taskId())
                        .file(new MockMultipartFile("file", "big.bin", "application/octet-stream",
                                new byte[2 * 1024 * 1024]))
                        .session(f.memberSession()).with(csrf()))
                .andExpect(status().isUnprocessableEntity());

        // 目录成分被清洗：../../evil.txt → evil.txt
        MvcResult result = mockMvc.perform(multipart("/api/tasks/{id}/files", f.taskId())
                        .file(new MockMultipartFile("file", "../../evil.txt", "text/plain", "x".getBytes()))
                        .session(f.memberSession()).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.originalName").value("evil.txt"))
                .andReturn();
        // 磁盘存储名必须是 UUID 形式（不含用户文件名）
        JsonNode data = objectMapper.readTree(result.getResponse().getContentAsString()).path("data");
        Long fileId = data.path("id").asLong();
        String storedName = jdbcTemplate.queryForObject("select stored_name from files where id = ?", String.class,
                fileId);
        assertThat(storedName).matches("^[0-9a-f]{32}(\\.[a-z0-9]{1,10})?$");
        assertThat(storedName).doesNotContain("evil");
    }

    @Test
    @DisplayName("删除附件：上传者可删，任务负责人可删他人附件，无关成员被拒；删除后列表与下载均不可见，审计 FILE_DELETED")
    void deleteFile() throws Exception {
        Fixture f = setup();
        Long fileId = uploadTextFile(f.rootSession(), f.taskId(), "旧版方案.txt", "v1");

        // 无关的项目成员（非上传者且非任务负责人）不能删除
        MockHttpSession lindaSession = login("linda", DEFAULT_PASSWORD);
        mockMvc.perform(delete("/api/files/{id}", fileId).session(lindaSession).with(csrf()))
                .andExpect(status().isForbidden());

        // 任务主负责人（member）可以删除他人（root）上传的附件
        mockMvc.perform(delete("/api/files/{id}", fileId).session(f.memberSession()).with(csrf()))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/tasks/{id}/files", f.taskId()).session(f.rootSession()))
                .andExpect(jsonPath("$.data.length()").value(0));
        mockMvc.perform(get("/api/files/{id}", fileId).session(f.rootSession()))
                .andExpect(status().isNotFound());

        // 软删除：记录与磁盘元数据保留
        Long remaining = jdbcTemplate.queryForObject("select count(*) from files where id = ?", Long.class, fileId);
        assertThat(remaining).isEqualTo(1L);
        var audit = auditService.query(AuditLogQuery.of(null, "FILE_DELETED", null, null, null, null, null, 1, 10));
        assertThat(audit.items()).isNotEmpty();
    }

    @Test
    @DisplayName("评论附件：先上传到任务，发布评论时挂载为评论附件（仅本人文件）")
    void commentAttachment() throws Exception {
        Fixture f = setup();
        Long fileId = uploadTextFile(f.memberSession(), f.taskId(), "截图说明.txt", "hello");

        // 引用他人上传的附件被拒绝
        mockMvc.perform(post("/api/tasks/{id}/comments", f.taskId())
                        .session(f.rootSession()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("content", "越权引用", "attachmentFileIds", List.of(fileId)))))
                .andExpect(status().isForbidden());

        // 本人发布评论并挂载附件
        mockMvc.perform(post("/api/tasks/{id}/comments", f.taskId())
                        .session(f.memberSession()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("content", "附上说明文件", "attachmentFileIds", List.of(fileId)))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.attachments.length()").value(1))
                .andExpect(jsonPath("$.data.attachments[0].id").value(fileId));

        // 任务附件列表不再包含（已归属评论）
        mockMvc.perform(get("/api/tasks/{id}/files", f.taskId()).session(f.rootSession()))
                .andExpect(jsonPath("$.data.length()").value(0));
        // 评论附件对项目成员可下载
        mockMvc.perform(get("/api/files/{id}", fileId).session(f.rootSession()))
                .andExpect(status().isOk());
    }

    // --- 辅助 -----------------------------------------------------------------

    private Long uploadTextFile(MockHttpSession session, Long taskId, String name, String content) throws Exception {
        MvcResult result = mockMvc.perform(multipart("/api/tasks/{id}/files", taskId)
                        .file(new MockMultipartFile("file", name, "text/plain",
                                content.getBytes(StandardCharsets.UTF_8)))
                        .session(session).with(csrf()))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("data").path("id").asLong();
    }
}