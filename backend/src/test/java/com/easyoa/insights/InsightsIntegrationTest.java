package com.easyoa.insights;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MvcResult;

import com.easyoa.AbstractIntegrationTest;
import com.easyoa.user.domain.SystemRole;
import com.easyoa.user.domain.User;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * Phase 9 数据中心集成测试。
 *
 * <p>业务数据通过原生 SQL 精确落库（可控的创建 / 完成 / 截止时间），
 * 以便对聚合结果做确定性断言——这正是只读报表层应有的测试方式。
 */
class InsightsIntegrationTest extends AbstractIntegrationTest {

    @Test
    @DisplayName("数据中心：项目健康度 / 逾期 / 负载 / 趋势 / 审批效率")
    void overviewAggregatesBusinessData() throws Exception {
        User root = initializeSystemWithRoot("root");
        Long memberId = createUser("member", DEFAULT_PASSWORD, SystemRole.MEMBER).getId();
        MockHttpSession rootSession = login("root", DEFAULT_PASSWORD);

        Long projectId = createProjectViaApi(rootSession, "洞察项目", "ACTIVE", java.util.List.of(memberId));
        Long activeStatus = statusId(projectId, "ACTIVE");
        Long doneStatus = statusId(projectId, "DONE");

        insertTask(projectId, activeStatus, "逾期任务", memberId, 20,
                Instant.now().minus(3, ChronoUnit.DAYS), null, Instant.now().minus(20, ChronoUnit.DAYS));
        insertTask(projectId, doneStatus, "已完成任务", memberId, 100,
                Instant.now().minus(10, ChronoUnit.DAYS), Instant.now().minus(1, ChronoUnit.DAYS),
                Instant.now().minus(21, ChronoUnit.DAYS));

        Long templateId = insertTemplate(root.getId());
        insertApproval(templateId, "已通过审批", "APPROVED", root.getId(),
                Instant.now().minus(4, ChronoUnit.DAYS), Instant.now().minus(2, ChronoUnit.DAYS));
        insertApproval(templateId, "审批中", "PENDING", memberId, Instant.now().minus(1, ChronoUnit.DAYS), null);

        MvcResult result = mockMvc.perform(get("/api/insights").session(rootSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.scope").value("ALL"))
                .andReturn();
        JsonNode data = objectMapper.readTree(result.getResponse().getContentAsString()).path("data");

        JsonNode health = firstMatch(data.path("projectHealth"), "projectId", projectId);
        assertThat(health.isMissingNode()).as("项目出现在健康度列表").isFalse();
        assertThat(health.path("health").asText()).isEqualTo("AT_RISK");
        assertThat(health.path("totalTasks").asInt()).isEqualTo(2);
        assertThat(health.path("doneTasks").asInt()).isEqualTo(1);
        assertThat(health.path("overdueTasks").asInt()).isEqualTo(1);

        JsonNode overdue = firstMatch(data.path("overdueTasks"), "title", "逾期任务");
        assertThat(overdue.isMissingNode()).as("逾期任务被列出").isFalse();
        assertThat(overdue.path("overdueDays").asLong()).isGreaterThanOrEqualTo(3);
        assertThat(overdue.path("primaryAssignee").asText()).isEqualTo("member");

        JsonNode workload = firstMatch(data.path("workload"), "userId", memberId);
        assertThat(workload.path("openTasks").asInt()).isEqualTo(1);
        assertThat(workload.path("overdueTasks").asInt()).isEqualTo(1);

        JsonNode trend = data.path("taskTrend");
        assertThat(trend.size()).isEqualTo(6);
        int completedTotal = 0;
        for (JsonNode point : trend) {
            completedTotal += point.path("completedCount").asInt();
        }
        assertThat(completedTotal).isGreaterThanOrEqualTo(1);

        JsonNode approvals = data.path("approvalEfficiency");
        assertThat(approvals.path("approved").asInt()).isEqualTo(1);
        assertThat(approvals.path("pending").asInt()).isEqualTo(1);
        assertThat(approvals.path("finishedCount").asInt()).isEqualTo(1);
        assertThat(approvals.path("averageHours").asDouble()).isGreaterThan(0.0);
        assertThat(approvals.path("approvedRate").asDouble()).isEqualTo(100.0);
    }

    @Test
    @DisplayName("数据中心：普通成员只能看到自己参与的项目与相关审批")
    void memberScopeIsLimitedToOwnProjects() throws Exception {
        User root = initializeSystemWithRoot("root");
        Long memberId = createUser("member", DEFAULT_PASSWORD, SystemRole.MEMBER).getId();
        MockHttpSession rootSession = login("root", DEFAULT_PASSWORD);

        Long mineProject = createProjectViaApi(rootSession, "我的项目", "ACTIVE", java.util.List.of(memberId));
        Long otherProject = createProjectViaApi(rootSession, "他人项目", "ACTIVE", null);

        Long mineStatus = statusId(mineProject, "ACTIVE");
        Long otherStatus = statusId(otherProject, "ACTIVE");
        insertTask(mineProject, mineStatus, "我的逾期任务", memberId, 10,
                Instant.now().minus(2, ChronoUnit.DAYS), null, Instant.now().minus(5, ChronoUnit.DAYS));
        insertTask(otherProject, otherStatus, "他人逾期任务", root.getId(), 10,
                Instant.now().minus(2, ChronoUnit.DAYS), null, Instant.now().minus(5, ChronoUnit.DAYS));

        Long templateId = insertTemplate(root.getId());
        insertApproval(templateId, "与我无关的审批", "PENDING", root.getId(), Instant.now().minus(1, ChronoUnit.DAYS),
                null);

        MockHttpSession memberSession = login("member", DEFAULT_PASSWORD);
        MvcResult result = mockMvc.perform(get("/api/insights").session(memberSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.scope").value("MY_PROJECTS"))
                .andReturn();
        JsonNode data = objectMapper.readTree(result.getResponse().getContentAsString()).path("data");

        assertThat(firstMatch(data.path("projectHealth"), "projectId", otherProject).isMissingNode())
                .as("看不到非成员项目的健康度").isTrue();
        assertThat(firstMatch(data.path("projectHealth"), "projectId", mineProject).isMissingNode())
                .as("能看到自己项目的健康度").isFalse();
        assertThat(firstMatch(data.path("overdueTasks"), "title", "他人逾期任务").isMissingNode())
                .as("看不到非成员项目的逾期任务").isTrue();
        assertThat(data.path("approvalEfficiency").path("pending").asInt())
                .as("看不到与自己无关的审批").isZero();

        // 管理员视角可见全部
        MvcResult adminResult = mockMvc.perform(get("/api/insights").session(rootSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.scope").value("ALL"))
                .andReturn();
        JsonNode adminData = objectMapper.readTree(adminResult.getResponse().getContentAsString()).path("data");
        assertThat(firstMatch(adminData.path("projectHealth"), "projectId", otherProject).isMissingNode()).isFalse();
    }

    @Test
    @DisplayName("数据中心：未登录不可访问")
    void insightsRequireAuthentication() throws Exception {
        mockMvc.perform(get("/api/insights")).andExpect(status().isUnauthorized());
    }

    // --- 辅助 -----------------------------------------------------------------

    private JsonNode firstMatch(JsonNode array, String field, Object value) {
        String expected = String.valueOf(value);
        for (JsonNode node : array) {
            JsonNode candidate = node.path(field);
            if (!candidate.isMissingNode() && candidate.asText().equals(expected)) {
                return node;
            }
        }
        return com.fasterxml.jackson.databind.node.MissingNode.getInstance();
    }

    private Long statusId(Long projectId, String systemType) {
        // 项目创建时会初始化默认状态模板，这里直接复用，避免与 (project_id, name) 唯一索引冲突
        return jdbcTemplate.queryForObject(
                "select id from task_statuses where project_id = ? and system_type = ?",
                Long.class, projectId, systemType);
    }

    private void insertTask(Long projectId, Long statusId, String title, Long assigneeId, int progress,
            Instant plannedEndAt, Instant completedAt, Instant createdAt) {
        jdbcTemplate.update("""
                insert into tasks (project_id, status_id, title, primary_assignee_id, progress,
                                   planned_end_at, completed_at, created_at, updated_at)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                projectId, statusId, title, assigneeId, progress,
                timestamp(plannedEndAt), timestamp(completedAt), timestamp(createdAt), timestamp(createdAt));
    }

    private Long insertTemplate(Long createdBy) {
        Long templateId = jdbcTemplate.queryForObject("""
                insert into approval_templates (name, enabled, latest_version_no, created_by)
                values (?, true, 1, ?) returning id
                """, Long.class, "洞察测试模板", createdBy);
        jdbcTemplate.update("""
                insert into approval_template_versions (template_id, version_no, name, form_schema, node_schema, created_by)
                values (?, 1, ?, '[]', '[]', ?)
                """, templateId, "洞察测试模板", createdBy);
        return templateId;
    }

    private void insertApproval(Long templateId, String title, String status, Long applicantId, Instant submittedAt,
            Instant finishedAt) {
        Long versionId = jdbcTemplate.queryForObject(
                "select id from approval_template_versions where template_id = ?", Long.class, templateId);
        jdbcTemplate.update("""
                insert into approval_instances (title, template_id, template_version_id, template_version_no, status,
                                                applicant_id, form_snapshot, submitted_at, finished_at)
                values (?, ?, ?, 1, ?, ?, '{}', ?, ?)
                """,
                title, templateId, versionId, status, applicantId,
                timestamp(submittedAt), timestamp(finishedAt));
    }

    private Timestamp timestamp(Instant instant) {
        return instant == null ? null : Timestamp.from(instant);
    }
}
