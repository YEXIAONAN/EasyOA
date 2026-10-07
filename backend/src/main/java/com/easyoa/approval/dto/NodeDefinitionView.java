package com.easyoa.approval.dto;

import java.util.List;

import com.easyoa.approval.domain.NodeMode;

/**
 * 审批节点定义（模板 schema 与实例快照共用）。
 */
public record NodeDefinitionView(
        String name,
        NodeMode mode,
        List<ApproverRuleView> approvers) {
}