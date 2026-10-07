package com.easyoa.securityevent.domain;

/**
 * 安全事件类型：与普通审计分离，记录需要长期留痕的高危动作。
 */
public enum SecurityEventType {

    /** 系统首次初始化完成。 */
    SETUP_COMPLETED,
    /** 登录被临时锁定（连续失败）。 */
    LOGIN_BLOCKED,
    /** 疑似暴力破解（同 IP 多账号尝试）。 */
    BRUTE_FORCE_SUSPECTED,
    /** ROOT 高危操作。 */
    ROOT_SENSITIVE_OPERATION,
    /** MFA / TOTP 重置。 */
    MFA_RESET,
    /** 审计数据清理。 */
    AUDIT_DATA_PURGE,
    /** 安全策略变更。 */
    SECURITY_POLICY_CHANGED,
    /** 敏感数据导出。 */
    SENSITIVE_DATA_EXPORT,
    /** 系统数据销毁。 */
    DATA_DESTROYED,
    /** 超级权限操作。 */
    PRIVILEGED_OPERATION
}