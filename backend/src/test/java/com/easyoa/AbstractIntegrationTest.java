package com.easyoa;

import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.easyoa.audit.application.AuditService;
import com.easyoa.auth.repository.LoginAttemptRepository;
import com.easyoa.auth.repository.UserSessionRepository;
import com.easyoa.system.application.SystemSettingService;
import com.easyoa.user.application.UserService;
import com.easyoa.user.domain.SystemRole;
import com.easyoa.user.domain.User;
import com.easyoa.user.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.BeforeEach;
import org.testcontainers.containers.PostgreSQLContainer;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 集成测试基类。
 *
 * <p>使用真实 PostgreSQL（Testcontainers）：Flyway 迁移、JPA 校验（ddl-auto=validate）、
 * 唯一索引与约束全部真实生效，避免 H2 造成的「测试通过、上线失败」。
 *
 * <p>每个测试方法前重置系统状态（用户 / 会话 / 登录尝试 / 初始化开关），
 * 审计与安全事件保持追加不改写——它们本身就是不可删除的。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class AbstractIntegrationTest {

    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("easyoa_test")
            .withUsername("easyoa")
            .withPassword("easyoa");

    static {
        POSTGRES.start();
    }

    /** 所有集成测试共用的强口令（满足密码策略）。 */
    protected static final String DEFAULT_PASSWORD = "EasyOA-Test-2026";

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    @Autowired
    protected UserService userService;

    @Autowired
    protected UserRepository userRepository;

    @Autowired
    protected UserSessionRepository userSessionRepository;

    @Autowired
    protected LoginAttemptRepository loginAttemptRepository;

    @Autowired
    protected SystemSettingService systemSettingService;

    @Autowired
    protected AuditService auditService;

    @Autowired
    protected PasswordEncoder passwordEncoder;

    @Autowired
    protected org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    private String cachedCsrfToken;

    @BeforeEach
    void resetSystemState() {
        // 组织 / 项目 / 任务 / 评论 / 审批数据使用原生 SQL 清理：自引用外键与级联关系无法通过实体逐行删除
        jdbcTemplate.execute("delete from approval_actions");
        jdbcTemplate.execute("delete from approval_node_approvers");
        jdbcTemplate.execute("delete from approval_nodes");
        jdbcTemplate.execute("delete from approval_instances");
        jdbcTemplate.execute("delete from approval_template_versions");
        jdbcTemplate.execute("delete from approval_templates");
        jdbcTemplate.execute("delete from files");
        jdbcTemplate.execute("delete from comment_mentions");
        jdbcTemplate.execute("delete from comment_versions");
        jdbcTemplate.execute("delete from comments");
        jdbcTemplate.execute("delete from task_dependencies");
        jdbcTemplate.execute("delete from task_collaborators");
        jdbcTemplate.execute("delete from tasks");
        jdbcTemplate.execute("delete from task_statuses");
        jdbcTemplate.execute("delete from project_members");
        jdbcTemplate.execute("delete from projects");
        jdbcTemplate.execute("delete from user_org_memberships");
        jdbcTemplate.execute("delete from org_units");
        userSessionRepository.deleteAll();
        loginAttemptRepository.deleteAll();
        userRepository.deleteAll();
        systemSettingService.setValue(SystemSettingService.KEY_SETUP_COMPLETED, "false", null);
    }

    // --- 测试辅助 -------------------------------------------------------------

    /**
     * 真实 CSRF 流程（与浏览器中的 Axios 行为一致）：
     * 先从 Cookie 拿到 XSRF-TOKEN，再通过 X-XSRF-TOKEN 请求头回传。
     *
     * <p>不使用 {@code SecurityMockMvcRequestPostProcessors.csrf()}：
     * 它与 CookieCsrfTokenRepository 的延迟加载语义不兼容，会绕过真实校验路径。
     */
    protected RequestPostProcessor csrf() throws Exception {
        String token = csrfToken();
        return request -> {
            request.setCookies(new jakarta.servlet.http.Cookie("XSRF-TOKEN", token));
            request.addHeader("X-XSRF-TOKEN", token);
            return request;
        };
    }

    private String csrfToken() throws Exception {
        if (cachedCsrfToken == null) {
            var result = mockMvc.perform(MockMvcRequestBuilders.get("/api/csrf"))
                    .andExpect(status().isOk())
                    .andReturn();
            var cookie = result.getResponse().getCookie("XSRF-TOKEN");
            if (cookie == null) {
                throw new IllegalStateException("服务端未下发 XSRF-TOKEN Cookie");
            }
            cachedCsrfToken = cookie.getValue();
        }
        return cachedCsrfToken;
    }

    protected User createUser(String username, String password, SystemRole role) {
        return userService.create(username, username, passwordEncoder.encode(password), role);
    }

    /** 直接用服务层完成初始化（非初始化流程测试的快捷路径）。 */
    protected User initializeSystemWithRoot(String username) {
        User root = createUser(username, DEFAULT_PASSWORD, SystemRole.ROOT);
        systemSettingService.markSetupCompleted(root.getId());
        return root;
    }

    /** 通过真实登录接口登录并返回会话。 */
    protected MockHttpSession login(String username, String password) throws Exception {
        MvcResult result = mockMvc
                .perform(post("/api/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("username", username, "password", password))))
                .andReturn();
        MockHttpSession session = (MockHttpSession) result.getRequest().getSession(false);
        if (session == null) {
            throw new IllegalStateException("登录失败，未创建会话：" + result.getResponse().getContentAsString());
        }
        return session;
    }

    /** 通过真实接口创建组织单元，返回单元 ID。 */
    protected Long createUnitViaApi(MockHttpSession session, String name, String type, Long parentId)
            throws Exception {
        Map<String, Object> body = new java.util.HashMap<>();
        body.put("name", name);
        body.put("type", type);
        if (parentId != null) {
            body.put("parentId", parentId);
        }
        MvcResult result = mockMvc
                .perform(post("/api/org-units")
                        .session(session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(body)))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("data").path("id").asLong();
    }

    /** 通过真实接口创建成员，返回用户 ID。 */
    protected Long createUserViaApi(MockHttpSession session, String username, String displayName,
            String systemRole, String email, String phone) throws Exception {
        Map<String, Object> body = new java.util.HashMap<>();
        body.put("username", username);
        body.put("displayName", displayName);
        body.put("systemRole", systemRole);
        body.put("initialPassword", DEFAULT_PASSWORD);
        if (email != null) {
            body.put("email", email);
        }
        if (phone != null) {
            body.put("phone", phone);
        }
        MvcResult result = mockMvc
                .perform(post("/api/users")
                        .session(session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(body)))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("data").path("id").asLong();
    }

    protected String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }

    /** 通过真实接口创建项目，返回项目 ID。 */
    protected Long createProjectViaApi(MockHttpSession session, String name, String status,
            java.util.List<Long> memberUserIds) throws Exception {
        Map<String, Object> body = new java.util.HashMap<>();
        body.put("name", name);
        body.put("description", name + " 的简介");
        if (status != null) {
            body.put("status", status);
        }
        if (memberUserIds != null) {
            body.put("memberUserIds", memberUserIds);
        }
        MvcResult result = mockMvc
                .perform(post("/api/projects")
                        .session(session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(body)))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("data").path("id").asLong();
    }
}