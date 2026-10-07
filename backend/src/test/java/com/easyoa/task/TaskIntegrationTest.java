package com.easyoa.task;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
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
 * 任务集成测试：状态映射与状态流、进度 AUTO、时间自动记录、依赖与循环检测、
 * 忽略依赖、负责人权限边界、派发审核、数据范围与审计。
 */
class TaskIntegrationTest extends AbstractIntegrationTest {

    private record Fixture(Long projectId, Long rootId, Long memberId, Long kevinId, Long lindaId,
            MockHttpSession rootSession, MockHttpSession memberSession) {
    }

    /** 标准场景：root（OWNER）+ member / kevin / linda（MEMBER），项目 ACTIVE。 */
    private Fixture setup() throws Exception {
        initializeSystemWithRoot("root");
        MockHttpSession rootSession = login("root", DEFAULT_PASSWORD);
        Long memberId = createUserViaApi(rootSession, "member", "Member", "MEMBER", null, null);
        Long kevinId = createUserViaApi(rootSession, "kevin", "Kevin", "MEMBER", null, null);
        Long lindaId = createUserViaApi(rootSession, "linda", "Linda", "MEMBER", null, null);
        Long projectId = createProjectViaApi(rootSession, "任务项目", "ACTIVE",
                List.of(memberId, kevinId, lindaId));
        Long rootId = userService.findByUsername("root").orElseThrow().getId();
        MockHttpSession memberSession = login("member", DEFAULT_PASSWORD);
        return new Fixture(projectId, rootId, memberId, kevinId, lindaId, rootSession, memberSession);
    }

    @Test
    @DisplayName("创建任务：默认「待处理」状态，项目自动初始化默认状态模板，看板返回状态列与卡片")
    void createTaskAndBoard() throws Exception {
        Fixture f = setup();

        Long taskId = createTask(f.rootSession(), f.projectId(), "实现登录页", f.memberId(), Map.of());
        JsonNode detail = taskDetail(f.rootSession(), taskId);
        assertThat(detail.path("status").path("systemType").asText()).isEqualTo("TODO");
        assertThat(detail.path("priority").asText()).isEqualTo("MEDIUM");
        assertThat(detail.path("assignmentState").asText()).isEqualTo("ACTIVE");
        assertThat(detail.path("permissions").path("canManage").asBoolean()).isTrue();

        mockMvc.perform(get("/api/projects/{id}/task-statuses", f.projectId()).session(f.memberSession()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(4))
                .andExpect(jsonPath("$.data[0].name").value("待处理"))
                .andExpect(jsonPath("$.data[0].systemType").value("TODO"))
                .andExpect(jsonPath("$.data[3].systemType").value("DONE"));

        mockMvc.perform(get("/api/projects/{id}/tasks/board", f.projectId()).session(f.memberSession()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.statuses.length()").value(4))
                .andExpect(jsonPath("$.data.tasks.length()").value(1))
                .andExpect(jsonPath("$.data.tasks[0].title").value("实现登录页"))
                .andExpect(jsonPath("$.data.tasks[0].primaryAssignee.displayName").value("Member"))
                .andExpect(jsonPath("$.data.tasks[0].blocked").value(false));

        // 任务列表（分页；无筛选参数时同样可用）
        mockMvc.perform(get("/api/projects/{id}/tasks", f.projectId()).session(f.memberSession()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].title").value("实现登录页"))
                .andExpect(jsonPath("$.data.items[0].status.name").value("待处理"));
    }

    @Test
    @DisplayName("状态流：允许前跳与重新打开，DONE → REVIEW 回退被拒；首次进入 ACTIVE / DONE 自动记录时间")
    void statusFlowAndTimeAutoRecord() throws Exception {
        Fixture f = setup();
        Long taskId = createTask(f.rootSession(), f.projectId(), "状态流任务", f.memberId(), Map.of());
        Long activeStatus = statusId(f.rootSession(), f.projectId(), "ACTIVE");
        Long reviewStatus = statusId(f.rootSession(), f.projectId(), "REVIEW");
        Long doneStatus = statusId(f.rootSession(), f.projectId(), "DONE");

        // TODO → DONE（前跳）允许：记录 completed_at，但未经历开始，actual_start_at 为空
        changeStatus(f.memberSession(), taskId, doneStatus, null, 200);
        JsonNode completed = taskDetail(f.memberSession(), taskId);
        assertThat(completed.hasNonNull("completedAt")).isTrue();
        assertThat(completed.hasNonNull("actualStartAt")).isFalse();
        // 手工模式：完成时进度记为 100%
        assertThat(completed.path("progress").asInt()).isEqualTo(100);
        String firstCompletedAt = completed.path("completedAt").asText();

        // DONE → REVIEW 回退被拒绝（只能回到 待处理 / 进行中）
        changeStatus(f.memberSession(), taskId, reviewStatus, null, 409);
        // DONE → ACTIVE 重新打开：记录 actual_start_at，完成时间不覆盖
        changeStatus(f.memberSession(), taskId, activeStatus, null, 200);
        JsonNode reopened = taskDetail(f.memberSession(), taskId);
        assertThat(reopened.hasNonNull("actualStartAt")).isTrue();
        assertThat(reopened.path("completedAt").asText()).isEqualTo(firstCompletedAt);

        // 同状态流转拒绝
        changeStatus(f.memberSession(), taskId, activeStatus, null, 409);
    }

    @Test
    @DisplayName("进度 AUTO：按一级子任务完成比例自动计算，存在子任务时禁止手工更新；无子任务回退手工")
    void autoProgressFollowsSubtasks() throws Exception {
        Fixture f = setup();
        Long parentId = createTask(f.rootSession(), f.projectId(), "AUTO 任务", f.memberId(),
                Map.of("progressMode", "AUTO"));
        Long doneStatus = statusId(f.rootSession(), f.projectId(), "DONE");

        // 无子任务时可手工更新（规范：没有子任务时默认使用 MANUAL）
        changeProgress(f.memberSession(), parentId, 40, 200);

        Long s1 = createSubtask(f.rootSession(), parentId, "子任务一", f.memberId());
        Long s2 = createSubtask(f.rootSession(), parentId, "子任务二", f.memberId());
        Long s3 = createSubtask(f.rootSession(), parentId, "子任务三", f.memberId());
        changeStatus(f.rootSession(), s1, doneStatus, null, 200);

        JsonNode parent = taskDetail(f.rootSession(), parentId);
        assertThat(parent.path("subtasks").size()).isEqualTo(3);
        assertThat(parent.path("progress").asInt()).isEqualTo(33);

        // AUTO + 存在子任务：手工更新被拒绝
        changeProgress(f.memberSession(), parentId, 50, 409);

        changeStatus(f.rootSession(), s2, doneStatus, null, 200);
        assertThat(taskDetail(f.rootSession(), parentId).path("progress").asInt()).isEqualTo(67);

        // 子任务不能有子任务（v0.1.0 只支持一级）
        mockMvc.perform(post("/api/tasks/{id}/subtasks", s1)
                        .session(f.rootSession()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("title", "二级子任务"))))
                .andExpect(status().isUnprocessableEntity());
        assertThat(s3).isNotNull();
    }

    @Test
    @DisplayName("依赖：阻塞开始、忽略依赖并开始（填写原因 + 审计）、循环依赖检测、前置完成后自动解除")
    void dependenciesBlockAndOverride() throws Exception {
        Fixture f = setup();
        Long a = createTask(f.rootSession(), f.projectId(), "前置任务 A", f.memberId(), Map.of());
        Long b = createTask(f.rootSession(), f.projectId(), "后续任务 B", f.kevinId(), Map.of());
        Long activeStatus = statusId(f.rootSession(), f.projectId(), "ACTIVE");
        Long doneStatus = statusId(f.rootSession(), f.projectId(), "DONE");

        // B 依赖 A
        mockMvc.perform(post("/api/tasks/{id}/dependencies", b)
                        .session(f.rootSession()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("dependsOnTaskId", a))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.blocked").value(true))
                .andExpect(jsonPath("$.data.blockerCount").value(1))
                .andExpect(jsonPath("$.data.dependencies[0].dependsOnTaskId").value(a));

        // A 未完成：B 进入 ACTIVE 被阻塞（结构化错误码供前端弹「忽略依赖并开始」）
        changeStatus(f.rootSession(), b, activeStatus, null, 409)
                .andExpect(jsonPath("$.code").value("TASK_BLOCKED_BY_DEPENDENCIES"));

        // 忽略依赖并开始：必须填写原因
        changeStatus(f.rootSession(), b, activeStatus, "  ", 409);
        changeStatus(f.rootSession(), b, activeStatus, "客户要求并行推进，已与负责人确认", 200);

        var audit = auditService.query(AuditLogQuery.of(null, "TASK_OVERRIDE_DEPENDENCY", null, null, null, null,
                null, 1, 10));
        assertThat(audit.items()).isNotEmpty();
        assertThat(audit.items().get(0).resourceId()).isEqualTo(String.valueOf(b));
        assertThat(audit.items().get(0).riskLevel()).isEqualTo("ELEVATED");

        // 循环依赖：A 依赖 B → 拒绝；自依赖 → 拒绝
        mockMvc.perform(post("/api/tasks/{id}/dependencies", a)
                        .session(f.rootSession()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("dependsOnTaskId", b))))
                .andExpect(status().isConflict());
        mockMvc.perform(post("/api/tasks/{id}/dependencies", a)
                        .session(f.rootSession()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("dependsOnTaskId", a))))
                .andExpect(status().isUnprocessableEntity());

        // 前置完成后自动解除阻塞：C 依赖 A，A 完成后 C 可直接开始（无需忽略）
        Long c = createTask(f.rootSession(), f.projectId(), "后续任务 C", f.kevinId(), Map.of());
        mockMvc.perform(post("/api/tasks/{id}/dependencies", c)
                        .session(f.rootSession()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("dependsOnTaskId", a))))
                .andExpect(status().isOk());
        changeStatus(f.rootSession(), a, doneStatus, null, 200);
        changeStatus(f.rootSession(), c, activeStatus, null, 200);

        // 子任务不支持依赖
        Long subtask = createSubtask(f.rootSession(), c, "子任务", f.memberId());
        mockMvc.perform(post("/api/tasks/{id}/dependencies", subtask)
                        .session(f.rootSession()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("dependsOnTaskId", a))))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    @DisplayName("负责人权限：副负责人不能改主负责人；协作成员只读；非任务角色不能流转；非成员 404")
    void assigneePermissionBoundaries() throws Exception {
        Fixture f = setup();
        Long taskId = createTask(f.rootSession(), f.projectId(), "权限任务", f.memberId(),
                Map.of("deputyAssigneeId", f.kevinId(), "collaboratorUserIds", List.of(f.lindaId())));
        Long activeStatus = statusId(f.rootSession(), f.projectId(), "ACTIVE");

        // 副负责人可以改状态 / 进度
        MockHttpSession kevinSession = login("kevin", DEFAULT_PASSWORD);
        changeStatus(kevinSession, taskId, activeStatus, null, 200);
        changeProgress(kevinSession, taskId, 50, 200);

        // 副负责人不能修改主负责人
        mockMvc.perform(put("/api/tasks/{id}/assignees", taskId)
                        .session(kevinSession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("primaryAssigneeId", f.kevinId()))))
                .andExpect(status().isForbidden());

        // 协作成员（非负责人）不能流转任务
        MockHttpSession lindaSession = login("linda", DEFAULT_PASSWORD);
        mockMvc.perform(post("/api/tasks/{id}/status", taskId)
                        .session(lindaSession).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("statusId", activeStatus))))
                .andExpect(status().isForbidden());

        // 主负责人可以修改主负责人
        mockMvc.perform(put("/api/tasks/{id}/assignees", taskId)
                        .session(f.memberSession()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("primaryAssigneeId", f.lindaId()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.primaryAssignee.id").value(f.lindaId()));

        // 非项目成员：任务 404（防存在性探测）
        createUser("outsider", DEFAULT_PASSWORD, com.easyoa.user.domain.SystemRole.MEMBER);
        MockHttpSession outsiderSession = login("outsider", DEFAULT_PASSWORD);
        mockMvc.perform(get("/api/tasks/{id}", taskId).session(outsiderSession))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));

        // 副负责人不能与主负责人相同
        mockMvc.perform(put("/api/tasks/{id}/assignees", taskId)
                        .session(f.rootSession()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("primaryAssigneeId", f.lindaId(), "deputyAssigneeId", f.lindaId()))))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    @DisplayName("派发审核：成员派给他人进入 PENDING_ASSIGNMENT，项目负责人审核通过后才进入看板")
    void pendingAssignmentReview() throws Exception {
        Fixture f = setup();

        // 成员派给自己：立即生效
        Long selfTask = createTask(f.memberSession(), f.projectId(), "自己的任务", f.memberId(), Map.of());
        assertThat(taskDetail(f.memberSession(), selfTask).path("assignmentState").asText()).isEqualTo("ACTIVE");

        // 成员派给他人：待审核，不进入看板
        Long pendingTask = createTask(f.memberSession(), f.projectId(), "派发任务", f.kevinId(), Map.of());
        assertThat(taskDetail(f.memberSession(), pendingTask).path("assignmentState").asText())
                .isEqualTo("PENDING_ASSIGNMENT");

        mockMvc.perform(get("/api/projects/{id}/tasks/board", f.projectId()).session(f.memberSession()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.tasks.length()").value(1))
                .andExpect(jsonPath("$.data.pendingAssignments.length()").value(0));

        // 待审核任务对项目负责人可见（审核入口）
        mockMvc.perform(get("/api/projects/{id}/tasks/board", f.projectId()).session(f.rootSession()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.pendingAssignments.length()").value(1))
                .andExpect(jsonPath("$.data.pendingAssignments[0].title").value("派发任务"));

        // 普通成员不能审核自己的派发
        mockMvc.perform(post("/api/tasks/{id}/assignment/approve", pendingTask)
                        .session(f.memberSession()).with(csrf()))
                .andExpect(status().isForbidden());

        // 项目负责人审核通过：正式生效
        mockMvc.perform(post("/api/tasks/{id}/assignment/approve", pendingTask)
                        .session(f.rootSession()).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.assignmentState").value("ACTIVE"));
        mockMvc.perform(get("/api/projects/{id}/tasks/board", f.projectId()).session(f.rootSession()))
                .andExpect(jsonPath("$.data.tasks.length()").value(2));

        // 驳回：记录保留但不生效（不进入看板），写入审计
        Long rejectedTask = createTask(f.memberSession(), f.projectId(), "将被驳回的任务", f.kevinId(), Map.of());
        mockMvc.perform(post("/api/tasks/{id}/assignment/reject", rejectedTask)
                        .session(f.rootSession()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("reason", "优先级不匹配，请调整后再提交"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.assignmentState").value("REJECTED"));
        mockMvc.perform(get("/api/projects/{id}/tasks/board", f.projectId()).session(f.rootSession()))
                .andExpect(jsonPath("$.data.tasks.length()").value(2));

        var audit = auditService.query(AuditLogQuery.of(null, "TASK_ASSIGNMENT_REJECTED", null, null, null, null,
                null, 1, 10));
        assertThat(audit.items()).isNotEmpty();
        assertThat(audit.items().get(0).reason()).contains("优先级不匹配");
    }

    @Test
    @DisplayName("自定义状态：必须映射系统类型，CLOSED 为终态且同样解除依赖阻塞")
    void customStatusAndClosedTerminal() throws Exception {
        Fixture f = setup();

        // 普通成员不能新增状态
        mockMvc.perform(post("/api/projects/{id}/task-statuses", f.projectId())
                        .session(f.memberSession()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", "开发中", "systemType", "ACTIVE"))))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/projects/{id}/task-statuses", f.projectId())
                        .session(f.rootSession()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", "开发中", "systemType", "ACTIVE"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(5))
                .andExpect(jsonPath("$.data[4].name").value("开发中"))
                .andExpect(jsonPath("$.data[4].systemType").value("ACTIVE"));

        // 同名状态拒绝
        mockMvc.perform(post("/api/projects/{id}/task-statuses", f.projectId())
                        .session(f.rootSession()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", "开发中", "systemType", "ACTIVE"))))
                .andExpect(status().isConflict());

        Long devStatus = statusIdByName(f.rootSession(), f.projectId(), "开发中");
        Long taskId = createTask(f.rootSession(), f.projectId(), "自定义状态任务", f.memberId(), Map.of());
        changeStatus(f.memberSession(), taskId, devStatus, null, 200);
        assertThat(taskDetail(f.memberSession(), taskId).path("status").path("systemType").asText())
                .isEqualTo("ACTIVE");

        // CLOSED 自定状态：终态 + 解除依赖阻塞
        mockMvc.perform(post("/api/projects/{id}/task-statuses", f.projectId())
                        .session(f.rootSession()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", "已取消", "systemType", "CLOSED"))))
                .andExpect(status().isOk());
        Long closedStatus = statusIdByName(f.rootSession(), f.projectId(), "已取消");
        Long activeStatus = statusId(f.rootSession(), f.projectId(), "ACTIVE");

        Long cancelled = createTask(f.rootSession(), f.projectId(), "取消的任务", f.kevinId(), Map.of());
        changeStatus(f.rootSession(), cancelled, closedStatus, null, 200);
        // CLOSED 为终态
        changeStatus(f.rootSession(), cancelled, activeStatus, null, 409);

        Long dependent = createTask(f.rootSession(), f.projectId(), "依赖已取消任务的任务", f.kevinId(), Map.of());
        mockMvc.perform(post("/api/tasks/{id}/dependencies", dependent)
                        .session(f.rootSession()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("dependsOnTaskId", cancelled))))
                .andExpect(status().isOk());
        changeStatus(f.rootSession(), dependent, activeStatus, null, 200);
    }

    @Test
    @DisplayName("协作成员与子任务：子任务负责人可完成自己的子任务；我的任务与工作台反映真实数据")
    void subtaskAssigneeAndMyTasks() throws Exception {
        Fixture f = setup();
        Long parentId = createTask(f.rootSession(), f.projectId(), "父任务", f.memberId(), Map.of());
        Long subtaskId = createSubtask(f.rootSession(), parentId, "由 Linda 负责的子任务", f.lindaId());
        Long doneStatus = statusId(f.rootSession(), f.projectId(), "DONE");

        // 子任务负责人（并非父任务角色）可以完成自己的子任务
        MockHttpSession lindaSession = login("linda", DEFAULT_PASSWORD);
        changeStatus(lindaSession, subtaskId, doneStatus, null, 200);
        // 但不能流转父任务
        changeStatus(lindaSession, parentId, doneStatus, null, 403);

        // 我的任务：linda 看到自己负责的子任务（含已完成）
        mockMvc.perform(get("/api/tasks/my").param("filter", "ALL").session(lindaSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].title").value("由 Linda 负责的子任务"))
                .andExpect(jsonPath("$.data.items[0].projectName").value("任务项目"));

        // 即将到期过滤
        Long dueTask = createTask(f.rootSession(), f.projectId(), "快到期的任务", f.memberId(),
                Map.of("plannedEndAt", Instant.now().plus(2, ChronoUnit.DAYS).toString()));
        mockMvc.perform(get("/api/tasks/my").param("filter", "DUE_SOON").session(f.memberSession()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].id").value(dueTask))
                .andExpect(jsonPath("$.data.items[0].overdue").value(false));

        // 工作台：KPI 与「我的任务」区块
        mockMvc.perform(get("/api/workspace/summary").session(f.memberSession()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.kpis.myOpenTasks").value(2))
                .andExpect(jsonPath("$.data.kpis.dueSoonTasks").value(1))
                .andExpect(jsonPath("$.data.myTasks.length()").value(2))
                .andExpect(jsonPath("$.data.myTasks[0].title").value("快到期的任务"));

        // 归档项目只读：不能创建任务
        mockMvc.perform(post("/api/projects/{id}/archive", f.projectId()).session(f.rootSession()).with(csrf()))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/projects/{id}/tasks", f.projectId())
                        .session(f.rootSession()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("title", "归档后创建", "primaryAssigneeId", f.memberId()))))
                .andExpect(status().isConflict());
    }

    // --- 辅助 -----------------------------------------------------------------

    private Long createTask(MockHttpSession session, Long projectId, String title, Long primaryAssigneeId,
            Map<String, Object> overrides) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("title", title);
        body.put("primaryAssigneeId", primaryAssigneeId);
        body.putAll(overrides);
        MvcResult result = mockMvc.perform(post("/api/projects/{id}/tasks", projectId)
                        .session(session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(body)))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("data").path("id").asLong();
    }

    private Long createSubtask(MockHttpSession session, Long parentId, String title, Long assigneeId)
            throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("title", title);
        body.put("primaryAssigneeId", assigneeId);
        MvcResult result = mockMvc.perform(post("/api/tasks/{id}/subtasks", parentId)
                        .session(session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(body)))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode subtasks = objectMapper.readTree(result.getResponse().getContentAsString())
                .path("data").path("subtasks");
        for (JsonNode node : subtasks) {
            if (title.equals(node.path("title").asText())) {
                return node.path("id").asLong();
            }
        }
        throw new IllegalStateException("子任务未创建成功：" + title);
    }

    private org.springframework.test.web.servlet.ResultActions changeStatus(MockHttpSession session, Long taskId,
            Long statusId, String overrideReason, int expectedStatus) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("statusId", statusId);
        if (overrideReason != null) {
            body.put("overrideReason", overrideReason);
        }
        return mockMvc.perform(post("/api/tasks/{id}/status", taskId)
                        .session(session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(body)))
                .andExpect(status().is(expectedStatus));
    }

    private void changeProgress(MockHttpSession session, Long taskId, int progress, int expectedStatus)
            throws Exception {
        mockMvc.perform(put("/api/tasks/{id}/progress", taskId)
                        .session(session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("progress", progress))))
                .andExpect(status().is(expectedStatus));
    }

    private JsonNode taskDetail(MockHttpSession session, Long taskId) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/tasks/{id}", taskId).session(session))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("data");
    }

    private Long statusId(MockHttpSession session, Long projectId, String systemType) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/projects/{id}/task-statuses", projectId).session(session))
                .andExpect(status().isOk())
                .andReturn();
        for (JsonNode node : objectMapper.readTree(result.getResponse().getContentAsString()).path("data")) {
            if (systemType.equals(node.path("systemType").asText())) {
                return node.path("id").asLong();
            }
        }
        throw new IllegalStateException("缺少状态类型：" + systemType);
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
}