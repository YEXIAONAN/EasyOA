package com.easyoa.security.domain;

import com.easyoa.securityevent.domain.SecurityEventType;

/**
 * ROOT 高危操作目录。
 *
 * <p>每一个高危动作都必须经过统一仪式：
 * 重新输入当前密码 → TOTP → 填写 Reason → 展示影响范围 → Final Confirm → 执行 → Security Event。
 * 该流程由 {@code SensitiveOperationService} 统一实现，业务模块不得自行重复认证逻辑。
 *
 * <p>{@code confirmationPhrase} 是最终确认步骤要求用户逐字输入的短语，
 * 用于阻断「误点确认」；{@code description} 用于向操作者说明影响范围。
 */
public enum SensitiveOperationType {

    AUDIT_LOG_PURGE(
            "审计日志清理",
            "按保留期限永久删除历史审计日志（安全事件与本次清理记录不受影响）。",
            "清理审计日志",
            SecurityEventType.AUDIT_DATA_PURGE),

    SENSITIVE_DATA_EXPORT(
            "敏感数据导出",
            "导出审计日志（含操作者、客户端 IP、请求 ID）为 CSV 文件，导出行为本身会被永久留痕。",
            "导出审计数据",
            SecurityEventType.SENSITIVE_DATA_EXPORT),

    MFA_RESET(
            "管理员 MFA 重置",
            "清除目标账号已绑定的动态口令并撤销其全部登录会话，该账号下次登录必须重新绑定。",
            "重置 MFA",
            SecurityEventType.MFA_RESET),

    SECURITY_POLICY_CHANGE(
            "安全策略修改",
            "调整登录保护、密码强度与管理员动态口令要求，对之后所有登录与密码变更立即生效。",
            "修改安全策略",
            SecurityEventType.SECURITY_POLICY_CHANGED),

    DATA_DESTRUCTION(
            "系统核心数据销毁",
            "永久删除全部业务数据（项目 / 任务 / 审批 / 评论 / 附件元数据 / 通知），"
                    + "保留账号、组织架构、系统设置与安全事件；操作不可撤销。",
            "销毁数据",
            SecurityEventType.DATA_DESTROYED);

    private final String displayName;
    private final String description;
    private final String confirmationPhrase;
    private final SecurityEventType securityEventType;

    SensitiveOperationType(String displayName, String description, String confirmationPhrase,
            SecurityEventType securityEventType) {
        this.displayName = displayName;
        this.description = description;
        this.confirmationPhrase = confirmationPhrase;
        this.securityEventType = securityEventType;
    }

    public String displayName() {
        return displayName;
    }

    public String description() {
        return description;
    }

    public String confirmationPhrase() {
        return confirmationPhrase;
    }

    /**
     * 操作成功后写入的安全事件类型。
     *
     * <p>所有高危操作同时还会写入 {@code PRIVILEGED_OPERATION} 语义的审计记录。
     */
    public SecurityEventType securityEventType() {
        return securityEventType;
    }
}
