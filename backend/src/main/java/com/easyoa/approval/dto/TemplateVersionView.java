package com.easyoa.approval.dto;

import java.time.Instant;
import java.util.List;

/**
 * 模板版本详情（表单字段定义 + 审批节点定义）。
 */
public record TemplateVersionView(
        int versionNo,
        String name,
        String description,
        List<FormFieldView> formFields,
        List<NodeDefinitionView> nodes,
        Instant createdAt) {
}