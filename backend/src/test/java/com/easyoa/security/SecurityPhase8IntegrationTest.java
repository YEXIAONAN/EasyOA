package com.easyoa.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MvcResult;

import com.easyoa.AbstractIntegrationTest;
import com.easyoa.security.domain.TotpAlgorithm;
import com.easyoa.user.domain.SystemRole;
import com.easyoa.user.domain.User;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * Phase 8 安全能力集成测试。
 *
 * <p>覆盖：TOTP 完整生命周期、登录第二步校验、ROOT 高危操作仪式、
 * 安全策略实时生效、安全事件检索、审计清理与导出。
 */
class SecurityPhase8IntegrationTest extends AbstractIntegrationTest {

    private static final String SENSITIVE_EXECUTE = "/api/security/sensitive-operations/execute";
    private static final String SENSITIVE_PREVIEW = "/api/security/sensitive-operations/preview";

    @Autowired
    private TotpAlgorithm totpAlgorithm;

    /**
     * 安全事件在生产环境永久追加，但测试断言需要确定性计数，
     * 因此在本用例类内每次执行前清空（仅测试环境行为）。
     */
    @BeforeEach
    void cleanSecurityEvents() {
        jdbcTemplate.execute("delete from security_events");
    }

    // --- TOTP 生命周期 ---------------------------------------------------------

    @Test
    @DisplayName("TOTP：绑定需要正确验证码，绑定后接口不再返回 Secret")
    void enrollmentRequiresValidCode() throws Exception {
        initializeSystemWithRoot("root");
        MockHttpSession session = login("root", DEFAULT_PASSWORD);

        String secret = startEnrollment(session);
        assertThat(secret).isNotBlank().hasSizeGreaterThanOrEqualTo(32);

        // 错误的验证码不能完成绑定
        mockMvc.perform(post("/api/security/mfa/enrollment/confirm")
                        .session(session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("code", "000000"))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("TOTP_INVALID"));

        confirmEnrollment(session, secret);

        // 绑定后：状态为已启用，且再次调用状态接口不返回任何 Secret 字段
        MvcResult status = mockMvc.perform(get("/api/security/mfa").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.enabled").value(true))
                .andExpect(jsonPath("$.data.pendingEnrollment").value(false))
                .andReturn();
        assertThat(status.getResponse().getContentAsString()).doesNotContain(secret);

        assertThat(userRepository.findByUsernameIgnoreCase("root").orElseThrow().isTotpEnabled()).isTrue();
    }

    @Test
    @DisplayName("TOTP：启用后登录必须携带动态验证码")
    void loginRequiresTotpWhenEnabled() throws Exception {
        initializeSystemWithRoot("root");
        MockHttpSession session = login("root", DEFAULT_PASSWORD);
        String secret = startEnrollment(session);
        confirmEnrollment(session, secret);

        // 1) 不带验证码 → TOTP_REQUIRED 且不建立会话
        mockMvc.perform(post("/api/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("username", "root", "password", DEFAULT_PASSWORD))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("TOTP_REQUIRED"));

        // 2) 错误验证码 → TOTP_INVALID
        mockMvc.perform(post("/api/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("username", "root", "password", DEFAULT_PASSWORD, "totpCode", "000000"))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("TOTP_INVALID"));

        // 3) 正确验证码 → 登录成功
        mockMvc.perform(post("/api/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("username", "root", "password", DEFAULT_PASSWORD,
                                "totpCode", currentCode(secret)))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totpEnabled").value(true));
    }

    @Test
    @DisplayName("TOTP：解绑需要当前密码 + 验证码双重确认")
    void disableRequiresPasswordAndCode() throws Exception {
        initializeSystemWithRoot("root");
        MockHttpSession session = login("root", DEFAULT_PASSWORD);
        String secret = startEnrollment(session);
        confirmEnrollment(session, secret);

        mockMvc.perform(post("/api/security/mfa/disable")
                        .session(session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("currentPassword", "Wrong-Password-1", "code", currentCode(secret)))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("PASSWORD_MISMATCH"));

        mockMvc.perform(post("/api/security/mfa/disable")
                        .session(session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("currentPassword", DEFAULT_PASSWORD, "code", currentCode(secret)))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.enabled").value(false));
    }

    // --- ROOT 高危操作 ---------------------------------------------------------

    @Test
    @DisplayName("高危操作：未绑定 TOTP 的 ROOT 被拒绝")
    void sensitiveOperationRequiresMfa() throws Exception {
        initializeSystemWithRoot("root");
        MockHttpSession session = login("root", DEFAULT_PASSWORD);

        mockMvc.perform(post(SENSITIVE_EXECUTE)
                        .session(session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(operationBody("AUDIT_LOG_PURGE", DEFAULT_PASSWORD, "123456",
                                "清理历史审计日志数据", "清理审计日志", Map.of("retentionDays", 180)))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("MFA_REQUIRED"));
    }

    @Test
    @DisplayName("高危操作：密码 / 验证码 / 原因 / 最终确认逐步校验")
    void sensitiveOperationCeremonyStepByStep() throws Exception {
        User root = initializeSystemWithRoot("root");
        MockHttpSession session = login("root", DEFAULT_PASSWORD);
        String secret = bindTotp(session);

        // 1) 密码错误
        mockMvc.perform(post(SENSITIVE_EXECUTE)
                        .session(session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(operationBody("AUDIT_LOG_PURGE", "Wrong-Password-1", currentCode(secret),
                                "清理历史审计日志数据", "清理审计日志", Map.of("retentionDays", 180)))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("PASSWORD_MISMATCH"));

        // 2) 动态验证码错误
        mockMvc.perform(post(SENSITIVE_EXECUTE)
                        .session(session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(operationBody("AUDIT_LOG_PURGE", DEFAULT_PASSWORD, "000000",
                                "清理历史审计日志数据", "清理审计日志", Map.of("retentionDays", 180)))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("TOTP_INVALID"));

        // 3) 原因过短
        mockMvc.perform(post(SENSITIVE_EXECUTE)
                        .session(session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(operationBody("AUDIT_LOG_PURGE", DEFAULT_PASSWORD, currentCode(secret),
                                "太短", "清理审计日志", Map.of("retentionDays", 180)))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("REASON_REQUIRED"));

        // 4) 最终确认口令不匹配
        mockMvc.perform(post(SENSITIVE_EXECUTE)
                        .session(session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(operationBody("AUDIT_LOG_PURGE", DEFAULT_PASSWORD, currentCode(secret),
                                "清理历史审计日志数据", "确认清理", Map.of("retentionDays", 180)))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("CONFIRMATION_MISMATCH"));

        // 失败的尝试同样必须留痕（安全事件）
        assertThat(securityEventCount("PRIVILEGED_OPERATION")).isGreaterThanOrEqualTo(2);

        // 5) 全部通过 → 执行成功并写入 AUDIT_DATA_PURGE 安全事件
        mockMvc.perform(post(SENSITIVE_EXECUTE)
                        .session(session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(operationBody("AUDIT_LOG_PURGE", DEFAULT_PASSWORD, currentCode(secret),
                                "清理历史审计日志数据", "清理审计日志", Map.of("retentionDays", 180)))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.type").value("AUDIT_LOG_PURGE"));

        assertThat(securityEventCount("AUDIT_DATA_PURGE")).isEqualTo(1);
        assertThat(userRepository.findById(root.getId()).orElseThrow().isTotpEnabled()).isTrue();
    }

    @Test
    @DisplayName("高危操作：非 ROOT 一律拒绝，预览接口同样受限")
    void sensitiveOperationRootOnly() throws Exception {
        initializeSystemWithRoot("root");
        User admin = createUser("admin", DEFAULT_PASSWORD, SystemRole.ADMIN);
        MockHttpSession adminSession = login("admin", DEFAULT_PASSWORD);

        mockMvc.perform(post(SENSITIVE_PREVIEW)
                        .session(adminSession)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("type", "AUDIT_LOG_PURGE"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("SENSITIVE_OPERATION_DENIED"));

        mockMvc.perform(post(SENSITIVE_EXECUTE)
                        .session(adminSession)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(operationBody("AUDIT_LOG_PURGE", DEFAULT_PASSWORD, "123456",
                                "尝试越权执行高危操作", "清理审计日志", Map.of("retentionDays", 180)))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("SENSITIVE_OPERATION_DENIED"));

        assertThat(userRepository.findById(admin.getId()).orElseThrow().getSystemRole())
                .isEqualTo(SystemRole.ADMIN);
    }

    @Test
    @DisplayName("高危操作：预览展示影响范围与确认短语")
    void previewReturnsImpactScope() throws Exception {
        initializeSystemWithRoot("root");
        MockHttpSession session = login("root", DEFAULT_PASSWORD);

        mockMvc.perform(post(SENSITIVE_PREVIEW)
                        .session(session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("type", "DATA_DESTRUCTION"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("系统核心数据销毁"))
                .andExpect(jsonPath("$.data.confirmationPhrase").value("销毁数据"))
                .andExpect(jsonPath("$.data.impacts[0]").exists());
    }

    @Test
    @DisplayName("高危操作：数据销毁按外键依赖顺序执行且保留账号")
    void dataDestructionIsForeignKeySafe() throws Exception {
        User root = initializeSystemWithRoot("root");
        MockHttpSession session = login("root", DEFAULT_PASSWORD);
        String secret = bindTotp(session);
        Long projectId = createProjectViaApi(session, "将被销毁的项目", "ACTIVE", null);
        assertThat(projectId).isNotNull();

        mockMvc.perform(post(SENSITIVE_EXECUTE)
                        .session(session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(operationBody("DATA_DESTRUCTION", DEFAULT_PASSWORD, currentCode(secret),
                                "项目组解散，销毁全部业务数据", "销毁数据", null))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.result.totalDeletedRows").exists());

        assertThat(jdbcTemplate.queryForObject("select count(*) from projects", Long.class)).isZero();
        // 账号与组织数据保留
        assertThat(jdbcTemplate.queryForObject("select count(*) from users", Long.class)).isGreaterThan(0);
        assertThat(userRepository.findById(root.getId())).isPresent();
        assertThat(securityEventCount("DATA_DESTROYED")).isEqualTo(1);
    }

    // --- 安全策略实时生效 -------------------------------------------------------

    @Test
    @DisplayName("安全策略：修改后登录保护阈值立即生效")
    void securityPolicyTakesEffectImmediately() throws Exception {
        initializeSystemWithRoot("root");
        MockHttpSession session = login("root", DEFAULT_PASSWORD);
        String secret = bindTotp(session);

        Map<String, Object> policy = Map.of(
                "loginMaxFailures", 3,
                "loginLockMinutes", 5,
                "passwordMinLength", 12,
                "totpRequiredForAdmins", false);
        mockMvc.perform(post(SENSITIVE_EXECUTE)
                        .session(session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(operationBody("SECURITY_POLICY_CHANGE", DEFAULT_PASSWORD, currentCode(secret),
                                "收紧登录保护与密码强度策略", "修改安全策略", policy))))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/security/settings").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.loginMaxFailures").value(3))
                .andExpect(jsonPath("$.data.passwordMinLength").value(12));

        createUser("victim", DEFAULT_PASSWORD, SystemRole.MEMBER);
        for (int attempt = 0; attempt < 3; attempt++) {
            mockMvc.perform(post("/api/auth/login")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json(Map.of("username", "victim", "password", "Wrong-Password-1"))))
                    .andExpect(status().isUnauthorized());
        }
        // 第 4 次：达到新阈值（3）后被临时锁定
        mockMvc.perform(post("/api/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("username", "victim", "password", "Wrong-Password-1"))))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("LOGIN_BLOCKED"));

        assertThat(securityEventCount("SECURITY_POLICY_CHANGED")).isEqualTo(1);
    }

    @Test
    @DisplayName("安全策略：管理员强制 TOTP 策略生效后未绑定管理员无法登录")
    void totpRequiredForAdminsBlocksLogin() throws Exception {
        initializeSystemWithRoot("root");
        MockHttpSession session = login("root", DEFAULT_PASSWORD);
        String secret = bindTotp(session);

        mockMvc.perform(post(SENSITIVE_EXECUTE)
                        .session(session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(operationBody("SECURITY_POLICY_CHANGE", DEFAULT_PASSWORD, currentCode(secret),
                                "要求管理员必须绑定动态口令", "修改安全策略",
                                Map.of("totpRequiredForAdmins", true)))))
                .andExpect(status().isOk());

        createUser("admin2", DEFAULT_PASSWORD, SystemRole.ADMIN);
        mockMvc.perform(post("/api/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("username", "admin2", "password", DEFAULT_PASSWORD))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("MFA_SETUP_REQUIRED"));
    }

    // --- MFA 重置 / 审计清理 / 导出 --------------------------------------------

    @Test
    @DisplayName("MFA 重置：ROOT 重置目标账号并撤销其会话")
    void mfaResetRevokesTargetSessions() throws Exception {
        initializeSystemWithRoot("root");
        MockHttpSession rootSession = login("root", DEFAULT_PASSWORD);
        String rootSecret = bindTotp(rootSession);

        User member = createUser("target", DEFAULT_PASSWORD, SystemRole.MEMBER);
        MockHttpSession memberSession = login("target", DEFAULT_PASSWORD);
        String memberSecret = startEnrollment(memberSession);
        confirmEnrollment(memberSession, memberSecret);

        mockMvc.perform(post(SENSITIVE_EXECUTE)
                        .session(rootSession)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(operationBody("MFA_RESET", DEFAULT_PASSWORD, currentCode(rootSecret),
                                "成员更换手机，需要重新绑定动态口令", "重置 MFA",
                                null, member.getId()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.type").value("MFA_RESET"));

        assertThat(userRepository.findById(member.getId()).orElseThrow().isTotpEnabled()).isFalse();
        // 目标账号会话被撤销
        mockMvc.perform(get("/api/auth/me").session(memberSession))
                .andExpect(status().isUnauthorized());
        assertThat(securityEventCount("MFA_RESET")).isEqualTo(1);
    }

    @Test
    @DisplayName("审计清理：按保留期限删除历史日志并留痕")
    void auditLogPurgeDeletesOldEntries() throws Exception {
        initializeSystemWithRoot("root");
        MockHttpSession session = login("root", DEFAULT_PASSWORD);
        String secret = bindTotp(session);

        jdbcTemplate.update("insert into audit_logs (actor_username, action, risk_level, created_at) "
                + "values (?, ?, ?, ?)", "legacy", "LEGACY_ACTION", "NORMAL",
                Timestamp.from(Instant.now().minus(400, ChronoUnit.DAYS)));

        mockMvc.perform(post(SENSITIVE_EXECUTE)
                        .session(session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(operationBody("AUDIT_LOG_PURGE", DEFAULT_PASSWORD, currentCode(secret),
                                "按 180 天保留期清理历史审计日志", "清理审计日志", Map.of("retentionDays", 180)))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.result.deletedCount").value(1));

        Long remaining = jdbcTemplate.queryForObject(
                "select count(*) from audit_logs where action = 'LEGACY_ACTION'", Long.class);
        assertThat(remaining).isZero();
    }

    @Test
    @DisplayName("敏感数据导出：返回 CSV 内容与行数")
    void sensitiveDataExportReturnsCsv() throws Exception {
        initializeSystemWithRoot("root");
        MockHttpSession session = login("root", DEFAULT_PASSWORD);
        String secret = bindTotp(session);

        MvcResult result = mockMvc.perform(post(SENSITIVE_EXECUTE)
                        .session(session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(operationBody("SENSITIVE_DATA_EXPORT", DEFAULT_PASSWORD, currentCode(secret),
                                "合规审计需要导出全部审计日志", "导出审计数据", null))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.result.fileName").exists())
                .andReturn();

        JsonNode data = objectMapper.readTree(result.getResponse().getContentAsString()).path("data").path("result");
        assertThat(data.path("fileName").asText()).endsWith(".csv");
        assertThat(data.path("content").asText()).contains("actor_username").contains("request_id");
        assertThat(data.path("rowCount").asInt()).isGreaterThan(0);
        assertThat(securityEventCount("SENSITIVE_DATA_EXPORT")).isEqualTo(1);
    }

    // --- 安全事件只读视图 -------------------------------------------------------

    @Test
    @DisplayName("安全事件：ROOT/ADMIN 可检索，普通成员被拒绝")
    void securityEventsReadableByAdminsOnly() throws Exception {
        initializeSystemWithRoot("root");
        MockHttpSession rootSession = login("root", DEFAULT_PASSWORD);
        String secret = bindTotp(rootSession);
        mockMvc.perform(post(SENSITIVE_EXECUTE)
                        .session(rootSession)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(operationBody("SENSITIVE_DATA_EXPORT", DEFAULT_PASSWORD, currentCode(secret),
                                "导出审计日志用于合规检查", "导出审计数据", null))))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/security-events").session(rootSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].eventType").value("SENSITIVE_DATA_EXPORT"))
                .andExpect(jsonPath("$.data.items[0].entryHash").isNotEmpty());

        createUser("plain", DEFAULT_PASSWORD, SystemRole.MEMBER);
        MockHttpSession memberSession = login("plain", DEFAULT_PASSWORD);
        mockMvc.perform(get("/api/security-events").session(memberSession))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/audit-logs").session(memberSession))
                .andExpect(status().isForbidden());
    }

    // --- 辅助 -----------------------------------------------------------------

    private String startEnrollment(MockHttpSession session) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/security/mfa/enrollment")
                        .session(session)
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.otpauthUri").exists())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .path("data").path("secret").asText();
    }

    private void confirmEnrollment(MockHttpSession session, String secret) throws Exception {
        mockMvc.perform(post("/api/security/mfa/enrollment/confirm")
                        .session(session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("code", currentCode(secret)))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.enabled").value(true));
    }

    /** 绑定动态口令并返回 Secret（供后续生成验证码）。 */
    private String bindTotp(MockHttpSession session) throws Exception {
        String secret = startEnrollment(session);
        confirmEnrollment(session, secret);
        return secret;
    }

    private String currentCode(String secret) {
        return totpAlgorithm.generateCode(secret, Instant.now());
    }

    private Map<String, Object> operationBody(String type, String password, String totpCode, String reason,
            String confirmation, Map<String, Object> payload) {
        return operationBody(type, password, totpCode, reason, confirmation, payload, null);
    }

    private Map<String, Object> operationBody(String type, String password, String totpCode, String reason,
            String confirmation, Map<String, Object> payload, Long targetId) {
        Map<String, Object> body = new HashMap<>();
        body.put("type", type);
        body.put("currentPassword", password);
        body.put("totpCode", totpCode);
        body.put("reason", reason);
        body.put("confirmation", confirmation);
        if (payload != null) {
            body.put("payload", payload);
        }
        if (targetId != null) {
            body.put("targetId", targetId);
        }
        return body;
    }

    private long securityEventCount(String eventType) {
        Long count = jdbcTemplate.queryForObject(
                "select count(*) from security_events where event_type = ?", Long.class, eventType);
        return count == null ? 0L : count;
    }
}
