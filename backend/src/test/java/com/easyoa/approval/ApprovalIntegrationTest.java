package com.easyoa.approval;

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
import com.easyoa.user.domain.SystemRole;
import com.fasterxml.jackson.databind.JsonNode;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 审批集成测试：模板版本化、动态审批人解析与自我审批禁止、ANY_ONE / ALL、
 * 退回后从第一个节点重新审批、拒绝 / 撤回、管理员转交、数据范围与工作台。
 */
class ApprovalIntegrationTest extends AbstractIntegrationTest {

    private record Fixture(MockHttpSession rootSession, MockHttpSession adminSession, MockHttpSession memberSession,
            MockHttpSession financeSession, Long rootId, Long adminId, Long memberId, Long financeId) {
    }

    /**
     * 场景：
     * 组织：技术部（负责人 admin）← 后端组（负责人 member，member 主部门）；
     * 成员：root(ROOT) / admin(ADMIN) / member(MEMBER) / finance(MEMBER)。
     */
    private Fixture setup() throws Exception {
        initializeSystemWithRoot("root");
        MockHttpSession rootSession = login("root", DEFAULT_PASSWORD);
        createUser("admin", DEFAULT_PASSWORD, SystemRole.ADMIN);
        createUser("member", DEFAULT_PASSWORD, SystemRole.MEMBER);
        createUser("finance", DEFAULT_PASSWORD, SystemRole.MEMBER);
        Long rootId = userService.findByUsername("root").orElseThrow().getId();
        Long adminId = userService.findByUsername("admin").orElseThrow().getId();
        Long memberId = userService.findByUsername("member").orElseThrow().getId();
        Long financeId = userService.findByUsername("finance").orElseThrow().getId();

        Long techId = insertOrgUnit("技术部", "DEPARTMENT", null, adminId);
        Long backendTeamId = insertOrgUnit("后端组", "TEAM", techId, memberId);
        jdbcTemplate.update("insert into user_org_memberships (user_id, org_unit_id, is_primary) values (?, ?, true)",
                memberId, backendTeamId);

        return new Fixture(rootSession, login("admin", DEFAULT_PASSWORD), login("member", DEFAULT_PASSWORD),
                login("finance", DEFAULT_PASSWORD), rootId, adminId, memberId, financeId);
    }

    // --- 用例 -------------------------------------------------------------------

    @Test
    @DisplayName("动态审批人：DIRECT_MANAGER 跳过申请人本人沿组织链向上解析；ANY_ONE 逐节点推进直至通过")
    void dynamicApproverAndAnyOneFlow() throws Exception {
        Fixture f = setup();
        Long templateId = createTemplate("请假申请", textField("reason", "请假事由", true), List.of(
                node("直属主管审批", "ANY_ONE", List.of(rule("DIRECT_MANAGER"))),
                node("财务备案", "ANY_ONE", List.of(fixedUser(f.financeId())))));

        Long instanceId = submit(f.memberSession(), templateId, "请假 3 天", Map.of("reason", "家中有事"));

        // 直属主管 = 技术部负责人 admin（后端组负责人是申请人本人，被跳过）
        JsonNode detail = approvalDetail(f.memberSession(), instanceId);
        assertThat(detail.path("nodes").size()).isEqualTo(2);
        assertThat(detail.path("nodes").path(0).path("approvers").path(0).path("user").path("id").asLong())
                .isEqualTo(f.adminId());
        assertThat(detail.path("nodes").path(0).path("approvers").path(0).path("ruleType").asText())
                .isEqualTo("DIRECT_MANAGER");
        assertThat(detail.path("status").asText()).isEqualTo("PENDING");
        assertThat(detail.path("nodes").path(0).path("current").asBoolean()).isTrue();

        // 非当前节点审批人（finance）不能审批第一个节点
        review(f.financeSession(), instanceId, "approve", null, 403);
        // 申请人不能审批自己的申请（自我审批禁止；同时其不是审批人）
        review(f.memberSession(), instanceId, "approve", null, 403);

        // admin 通过 → 进入财务备案节点
        review(f.adminSession(), instanceId, "approve", "同意", 200);
        JsonNode afterFirst = approvalDetail(f.adminSession(), instanceId);
        assertThat(afterFirst.path("status").asText()).isEqualTo("PENDING");
        assertThat(afterFirst.path("nodes").path(0).path("status").asText()).isEqualTo("APPROVED");
        assertThat(afterFirst.path("nodes").path(1).path("current").asBoolean()).isTrue();

        // finance 通过 → 全部节点完成 → APPROVED
        review(f.financeSession(), instanceId, "approve", "已备案", 200);
        JsonNode finished = approvalDetail(f.memberSession(), instanceId);
        assertThat(finished.path("status").asText()).isEqualTo("APPROVED");
        assertThat(finished.hasNonNull("finishedAt")).isTrue();

        var audit = auditService.query(AuditLogQuery.of(null, "APPROVAL_APPROVED", null, null, null, null, null, 1, 5));
        assertThat(audit.items()).isNotEmpty();
    }

    @Test
    @DisplayName("自我审批禁止：FIXED_USER 是申请人时走备用规则；无法解析合法审批人时禁止提交")
    void selfApprovalPrevention() throws Exception {
        Fixture f = setup();

        // 规则：固定审批人 = 申请人本人，备用规则 SYSTEM_ROLE:ADMIN → 解析为 admin
        Long fallbackTemplate = createTemplate("报销申请", numberField("amount", "金额", true), List.of(
                node("主管审批", "ANY_ONE", List.of(ruleWithFallback("FIXED_USER", f.memberId(),
                        List.of(rule("SYSTEM_ROLE", "ADMIN")))))));
        Long instanceId = submit(f.memberSession(), fallbackTemplate, "报销 100 元", Map.of("amount", 100));
        JsonNode detail = approvalDetail(f.memberSession(), instanceId);
        assertThat(detail.path("nodes").path(0).path("approvers").path(0).path("user").path("id").asLong())
                .isEqualTo(f.adminId());
        assertThat(detail.path("nodes").path(0).path("approvers").path(0).path("ruleType").asText())
                .isEqualTo("SYSTEM_ROLE");

        // 规则：仅 SYSTEM_ROLE:ROOT，申请人是唯一 ROOT → 默认递补链也解析不出合法审批人 → 禁止提交
        Long impossibleTemplate = createTemplate("无解审批", textField("reason", "事由", false), List.of(
                node("ROOT 审批", "ANY_ONE", List.of(rule("SYSTEM_ROLE", "ROOT")))));
        Long draftId = createDraft(f.rootSession(), impossibleTemplate, "测试", Map.of());
        mockMvc.perform(post("/api/approvals/{id}/submit", draftId).session(f.rootSession()).with(csrf()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value("审批流程配置不完整，请联系管理员"));
    }

    @Test
    @DisplayName("ALL 模式：所有审批人通过才进入下一节点")
    void allModeRequiresEveryone() throws Exception {
        Fixture f = setup();
        Long templateId = createTemplate("双人审批", textField("reason", "事由", false), List.of(
                node("主管与财务", "ALL", List.of(fixedUser(f.adminId()), fixedUser(f.financeId())))));

        Long instanceId = submit(f.memberSession(), templateId, "ALL 模式验证", Map.of());
        review(f.adminSession(), instanceId, "approve", "同意", 200);

        // admin 通过后仍在同一节点等待 finance
        JsonNode midway = approvalDetail(f.memberSession(), instanceId);
        assertThat(midway.path("status").asText()).isEqualTo("PENDING");
        assertThat(midway.path("nodes").path(0).path("status").asText()).isEqualTo("PENDING");
        assertThat(midway.path("nodes").path(0).path("current").asBoolean()).isTrue();

        review(f.financeSession(), instanceId, "approve", "同意", 200);
        assertThat(approvalDetail(f.memberSession(), instanceId).path("status").asText()).isEqualTo("APPROVED");
    }

    @Test
    @DisplayName("退回：申请人修改表单后从第一个节点重新审批（审批人快照不变）")
    void returnRestartsFromFirstNode() throws Exception {
        Fixture f = setup();
        Long templateId = createTemplate("用章申请", textField("reason", "用章事由", true), List.of(
                node("主管审批", "ANY_ONE", List.of(rule("DIRECT_MANAGER"))),
                node("财务备案", "ANY_ONE", List.of(fixedUser(f.financeId())))));

        Long instanceId = submit(f.memberSession(), templateId, "用章申请", Map.of("reason", "合同盖章"));
        // PENDING 后申请人不能修改表单
        mockMvc.perform(put("/api/approvals/{id}/form", instanceId)
                        .session(f.memberSession()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("values", Map.of("reason", "改一下")))))
                .andExpect(status().isConflict());

        review(f.adminSession(), instanceId, "return", "事由描述不完整，请补充", 200);
        JsonNode returned = approvalDetail(f.memberSession(), instanceId);
        assertThat(returned.path("status").asText()).isEqualTo("RETURNED");
        assertThat(returned.path("permissions").path("canEditForm").asBoolean()).isTrue();

        // 修改表单并重新提交 → 回到第一个节点，审批人快照保持不变且状态重置
        mockMvc.perform(put("/api/approvals/{id}/form", instanceId)
                        .session(f.memberSession()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("values", Map.of("reason", "合同盖章（补充：编号 HT-2026-01）")))))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/approvals/{id}/submit", instanceId).session(f.memberSession()).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andExpect(jsonPath("$.data.nodes[0].current").value(true))
                .andExpect(jsonPath("$.data.nodes[0].approvers[0].user.id").value(f.adminId()))
                .andExpect(jsonPath("$.data.nodes[0].approvers[0].status").value("PENDING"));

        // 从第一个节点重新审批：admin 再次通过 → 进入财务节点 → 完成
        review(f.adminSession(), instanceId, "approve", "这次可以", 200);
        review(f.financeSession(), instanceId, "approve", "备案完成", 200);
        assertThat(approvalDetail(f.memberSession(), instanceId).path("status").asText()).isEqualTo("APPROVED");

        var audit = auditService.query(
                AuditLogQuery.of(null, "APPROVAL_RETURNED", null, null, null, null, null, 1, 5));
        assertThat(audit.items()).isNotEmpty();
    }

    @Test
    @DisplayName("拒绝与撤回：拒绝必须填写原因；撤回仅申请人且仅审批中")
    void rejectAndWithdraw() throws Exception {
        Fixture f = setup();
        Long templateId = createTemplate("预算申请", textField("reason", "事由", false), List.of(
                node("主管审批", "ANY_ONE", List.of(rule("DIRECT_MANAGER")))));

        // 拒绝必须填写原因
        Long first = submit(f.memberSession(), templateId, "预算 1", Map.of());
        review(f.adminSession(), first, "reject", null, 422);
        review(f.adminSession(), first, "reject", "预算超限，暂不批准", 200);
        assertThat(approvalDetail(f.memberSession(), first).path("status").asText()).isEqualTo("REJECTED");
        var rejected = auditService.query(
                AuditLogQuery.of(null, "APPROVAL_REJECTED", null, null, null, null, null, 1, 5));
        assertThat(rejected.items()).isNotEmpty();

        // 撤回：非申请人 403；申请人撤回后 CANCELLED
        Long second = submit(f.memberSession(), templateId, "预算 2", Map.of());
        mockMvc.perform(post("/api/approvals/{id}/withdraw", second).session(f.adminSession()).with(csrf()))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/approvals/{id}/withdraw", second).session(f.memberSession()).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CANCELLED"));
        // 撤回后不能再次审批
        review(f.adminSession(), second, "approve", null, 409);
        var cancelled = auditService.query(
                AuditLogQuery.of(null, "APPROVAL_CANCELLED", null, null, null, null, null, 1, 5));
        assertThat(cancelled.items()).isNotEmpty();
    }

    @Test
    @DisplayName("管理员转交：原审批人退出，新审批人接替；不能转交给申请人")
    void adminTransfer() throws Exception {
        Fixture f = setup();
        Long templateId = createTemplate("转交验证", textField("reason", "事由", false), List.of(
                node("主管审批", "ANY_ONE", List.of(rule("DIRECT_MANAGER")))));

        Long instanceId = submit(f.memberSession(), templateId, "转交测试", Map.of());
        // 不能转交给申请人本人
        mockMvc.perform(post("/api/approvals/{id}/transfer", instanceId)
                        .session(f.rootSession()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("fromUserId", f.adminId(), "toUserId", f.memberId()))))
                .andExpect(status().isUnprocessableEntity());

        // 转交给 finance
        mockMvc.perform(post("/api/approvals/{id}/transfer", instanceId)
                        .session(f.rootSession()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("fromUserId", f.adminId(), "toUserId", f.financeId(),
                                "comment", "原审批人出差，转交处理"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.nodes[0].approvers[1].user.id").value(f.financeId()))
                .andExpect(jsonPath("$.data.nodes[0].approvers[0].status").value("TRANSFERRED_OUT"))
                .andExpect(jsonPath("$.data.nodes[0].approvers[1].transferredIn").value(true));

        // 原审批人已退出：不能再审批；新审批人可以
        review(f.adminSession(), instanceId, "approve", null, 403);
        review(f.financeSession(), instanceId, "approve", "已接替处理", 200);
        assertThat(approvalDetail(f.memberSession(), instanceId).path("status").asText()).isEqualTo("APPROVED");

        var audit = auditService.query(
                AuditLogQuery.of(null, "APPROVAL_TRANSFERRED", null, null, null, null, null, 1, 5));
        assertThat(audit.items()).isNotEmpty();
        assertThat(audit.items().get(0).riskLevel()).isEqualTo("CRITICAL");
    }

    @Test
    @DisplayName("模板版本化：已运行实例继续使用旧版本；新申请使用新版本；数据范围与工作台联动")
    void templateVersioningAndDataScope() throws Exception {
        Fixture f = setup();
        Long templateId = createTemplate("版本化模板 v1", textField("reason", "事由", false), List.of(
                node("主管审批", "ANY_ONE", List.of(rule("DIRECT_MANAGER")))));

        Long instanceV1 = submit(f.memberSession(), templateId, "v1 申请", Map.of());

        // 发布 v2（两个节点）
        mockMvc.perform(post("/api/approval-templates/{id}/versions", templateId)
                        .session(f.rootSession()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "formFields", List.of(textField("reason", "事由", false)),
                                "nodes", List.of(
                                        node("主管审批", "ANY_ONE", List.of(rule("DIRECT_MANAGER"))),
                                        node("财务备案", "ANY_ONE", List.of(fixedUser(f.financeId()))))))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.latestVersionNo").value(2));

        // 已运行实例仍是 v1（一个节点）
        JsonNode oldInstance = approvalDetail(f.memberSession(), instanceV1);
        assertThat(oldInstance.path("templateVersionNo").asInt()).isEqualTo(1);
        assertThat(oldInstance.path("nodes").size()).isEqualTo(1);

        // 新申请使用 v2（两个节点）
        Long instanceV2 = submit(f.memberSession(), templateId, "v2 申请", Map.of());
        JsonNode newInstance = approvalDetail(f.memberSession(), instanceV2);
        assertThat(newInstance.path("templateVersionNo").asInt()).isEqualTo(2);
        assertThat(newInstance.path("nodes").size()).isEqualTo(2);

        // 数据范围：finance 未参与 v1 实例 → 404；admin 是 v1 审批人 → 可见
        mockMvc.perform(get("/api/approvals/{id}", instanceV1).session(f.financeSession()))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/approvals/{id}", instanceV1).session(f.adminSession()))
                .andExpect(status().isOk());

        // 工作台：admin 的待我审批 KPI 与区块（v1 / v2 两个实例的当前节点都指向 admin）
        mockMvc.perform(get("/api/workspace/summary").session(f.adminSession()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.kpis.pendingApprovals").value(2))
                .andExpect(jsonPath("$.data.pendingApprovals.length()").value(2))
                // 最近更新的在前：v2 申请为 index 0，v1 申请为 index 1
                .andExpect(jsonPath("$.data.pendingApprovals[0].title").value("v2 申请"))
                .andExpect(jsonPath("$.data.pendingApprovals[1].title").value("v1 申请"))
                .andExpect(jsonPath("$.data.pendingApprovals[1].currentNodeName").value("主管审批"));
    }

    // --- 辅助 -------------------------------------------------------------------

    private Long insertOrgUnit(String name, String type, Long parentId, Long managerId) {
        jdbcTemplate.update("insert into org_units (parent_id, name, type, sort_order, status, manager_user_id) "
                + "values (?, ?, ?, 0, 'ACTIVE', ?)", parentId, name, type, managerId);
        return jdbcTemplate.queryForObject("select id from org_units where name = ?", Long.class, name);
    }

    private Map<String, Object> textField(String key, String label, boolean required) {
        return Map.of("key", key, "label", label, "type", "TEXT", "required", required);
    }

    private Map<String, Object> numberField(String key, String label, boolean required) {
        return Map.of("key", key, "label", label, "type", "NUMBER", "required", required);
    }

    private Map<String, Object> node(String name, String mode, List<Map<String, Object>> approvers) {
        return Map.of("name", name, "mode", mode, "approvers", approvers);
    }

    private Map<String, Object> rule(String type) {
        return Map.of("type", type);
    }

    private Map<String, Object> rule(String type, String systemRole) {
        return Map.of("type", type, "systemRole", systemRole);
    }

    private Map<String, Object> fixedUser(Long userId) {
        return Map.of("type", "FIXED_USER", "userId", userId);
    }

    private Map<String, Object> ruleWithFallback(String type, Long userId, List<Map<String, Object>> fallback) {
        return Map.of("type", type, "userId", userId, "fallback", fallback);
    }

    private Long createTemplate(String name, Map<String, Object> field, List<Map<String, Object>> nodes)
            throws Exception {
        // 使用独立的 root session（root 为 ROOT，可管理模板）
        MockHttpSession rootSession = login("root", DEFAULT_PASSWORD);
        MvcResult result = mockMvc.perform(post("/api/approval-templates")
                        .session(rootSession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", name, "description", name + " 的说明",
                                "formFields", List.of(field), "nodes", nodes))))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("data").path("id").asLong();
    }

    private Long createDraft(MockHttpSession session, Long templateId, String title, Map<String, Object> values)
            throws Exception {
        MvcResult result = mockMvc.perform(post("/api/approvals")
                        .session(session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("templateId", templateId, "title", title, "values", values))))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("data").path("id").asLong();
    }

    private Long submit(MockHttpSession session, Long templateId, String title, Map<String, Object> values)
            throws Exception {
        Long draftId = createDraft(session, templateId, title, values);
        MvcResult result = mockMvc.perform(post("/api/approvals/{id}/submit", draftId)
                        .session(session).with(csrf()))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("data").path("id").asLong();
    }

    private org.springframework.test.web.servlet.ResultActions review(MockHttpSession session, Long instanceId,
            String action, String comment, int expectedStatus) throws Exception {
        Map<String, Object> body = new HashMap<>();
        if (comment != null) {
            body.put("comment", comment);
        }
        return mockMvc.perform(post("/api/approvals/{id}/" + action, instanceId)
                        .session(session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(body)))
                .andExpect(status().is(expectedStatus));
    }

    private JsonNode approvalDetail(MockHttpSession session, Long instanceId) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/approvals/{id}", instanceId).session(session))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("data");
    }
}