package com.easyoa.common.audit;

/**
 * 审计 Action 目录。
 *
 * <p>所有写入 audit_logs 的动作名必须来自该目录，禁止在业务代码中出现魔法字符串。
 * 命名规范：{@code 领域_动作}，例如 {@code TASK_OVERRIDE_DEPENDENCY}。
 */
public final class AuditActions {

    private AuditActions() {
    }

    // --- 认证 / 会话 ---
    public static final String AUTH_LOGIN_SUCCEEDED = "AUTH_LOGIN_SUCCEEDED";
    public static final String AUTH_LOGIN_FAILED = "AUTH_LOGIN_FAILED";
    public static final String AUTH_LOGIN_BLOCKED = "AUTH_LOGIN_BLOCKED";
    public static final String AUTH_LOGOUT = "AUTH_LOGOUT";
    public static final String AUTH_PASSWORD_CHANGED = "AUTH_PASSWORD_CHANGED";
    public static final String AUTH_SESSION_REVOKED = "AUTH_SESSION_REVOKED";

    // --- 系统初始化 / 设置 ---
    public static final String SETUP_INITIALIZED = "SETUP_INITIALIZED";
    public static final String SYSTEM_SETTING_UPDATED = "SYSTEM_SETTING_UPDATED";

    // --- 用户 / 组织（Phase 2） ---
    public static final String USER_CREATED = "USER_CREATED";
    public static final String USER_ROLE_CHANGED = "USER_ROLE_CHANGED";
    public static final String USER_DISABLED = "USER_DISABLED";
    public static final String USER_ENABLED = "USER_ENABLED";
    public static final String USER_PROFILE_UPDATED = "USER_PROFILE_UPDATED";
    public static final String ORG_UNIT_CREATED = "ORG_UNIT_CREATED";
    public static final String ORG_UNIT_UPDATED = "ORG_UNIT_UPDATED";
    public static final String ORG_UNIT_ARCHIVED = "ORG_UNIT_ARCHIVED";
    public static final String ORG_UNIT_RESTORED = "ORG_UNIT_RESTORED";
    public static final String ORG_MEMBERSHIP_CHANGED = "ORG_MEMBERSHIP_CHANGED";
    public static final String ORG_PRIMARY_CHANGED = "ORG_PRIMARY_CHANGED";

    // --- 项目（Phase 3 起使用） ---
    public static final String PROJECT_CREATED = "PROJECT_CREATED";
    public static final String PROJECT_STATUS_CHANGED = "PROJECT_STATUS_CHANGED";
    public static final String PROJECT_OWNER_TRANSFERRED = "PROJECT_OWNER_TRANSFERRED";
    public static final String PROJECT_MEMBER_ADDED = "PROJECT_MEMBER_ADDED";
    public static final String PROJECT_MEMBER_REMOVED = "PROJECT_MEMBER_REMOVED";
    public static final String PROJECT_ARCHIVED = "PROJECT_ARCHIVED";

    // --- 任务（Phase 4 起使用） ---
    public static final String TASK_CREATED = "TASK_CREATED";
    public static final String TASK_ASSIGNEE_CHANGED = "TASK_ASSIGNEE_CHANGED";
    public static final String TASK_STATUS_CHANGED = "TASK_STATUS_CHANGED";
    public static final String TASK_PROGRESS_CHANGED = "TASK_PROGRESS_CHANGED";
    public static final String TASK_COMPLETED = "TASK_COMPLETED";
    public static final String TASK_OVERRIDE_DEPENDENCY = "TASK_OVERRIDE_DEPENDENCY";

    // --- 评论（Phase 5 起使用） ---
    public static final String COMMENT_EDITED = "COMMENT_EDITED";
    public static final String COMMENT_WITHDRAWN = "COMMENT_WITHDRAWN";

    // --- 文件（Phase 5 起使用） ---
    public static final String FILE_UPLOADED = "FILE_UPLOADED";
    public static final String FILE_DOWNLOADED_SENSITIVE = "FILE_DOWNLOADED_SENSITIVE";
    public static final String FILE_DELETED = "FILE_DELETED";

    // --- 审批（Phase 6 起使用） ---
    public static final String APPROVAL_TEMPLATE_CREATED = "APPROVAL_TEMPLATE_CREATED";
    public static final String APPROVAL_TEMPLATE_VERSION_CREATED = "APPROVAL_TEMPLATE_VERSION_CREATED";
    public static final String APPROVAL_SUBMITTED = "APPROVAL_SUBMITTED";
    public static final String APPROVAL_APPROVED = "APPROVAL_APPROVED";
    public static final String APPROVAL_REJECTED = "APPROVAL_REJECTED";
    public static final String APPROVAL_RETURNED = "APPROVAL_RETURNED";
    public static final String APPROVAL_TRANSFERRED = "APPROVAL_TRANSFERRED";
    public static final String APPROVAL_CANCELLED = "APPROVAL_CANCELLED";

    // --- 安全（Phase 8 起使用） ---
    public static final String SECURITY_TOTP_BOUND = "SECURITY_TOTP_BOUND";
    public static final String SECURITY_TOTP_RESET = "SECURITY_TOTP_RESET";
    public static final String SECURITY_SENSITIVE_OPERATION = "SECURITY_SENSITIVE_OPERATION";
    public static final String SECURITY_AUDIT_EXPORT = "SECURITY_AUDIT_EXPORT";
    public static final String SECURITY_DATA_DESTROYED = "SECURITY_DATA_DESTROYED";
    public static final String SECURITY_POLICY_CHANGED = "SECURITY_POLICY_CHANGED";
}