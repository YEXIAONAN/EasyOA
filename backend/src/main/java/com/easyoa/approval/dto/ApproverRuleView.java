package com.easyoa.approval.dto;

import java.util.List;

import com.easyoa.approval.domain.ApproverRuleType;

/**
 * 审批人规则（动态审批人解析的输入）。
 *
 * <p>解析顺序：规则本身 → 规则自带的 {@link #fallback} 递补链 → 系统默认递补链
 * （主部门负责人 → SYSTEM_ROLE:ADMIN → SYSTEM_ROLE:ROOT）；解析结果过滤申请人本人
 * （自我审批禁止）；仍无法解析出合法审批人则禁止提交。
 */
public record ApproverRuleView(
        ApproverRuleType type,

        /** FIXED_USER 时的固定成员。 */
        Long userId,

        /** SYSTEM_ROLE 时的系统角色（ADMIN / ROOT）。 */
        String systemRole,

        /** PROJECT_OWNER / PROJECT_DEPUTY 时指向表单中「项目 id」字段的 key。 */
        String projectField,

        /** 模板覆盖的备用审批规则（为空时使用系统默认递补链）。 */
        List<ApproverRuleView> fallback) {
}