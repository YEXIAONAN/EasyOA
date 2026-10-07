package com.easyoa.approval.dto;

import java.util.List;

import jakarta.validation.constraints.NotEmpty;

/**
 * 发布模板新版本（表单与节点定义整体替换；已运行实例继续使用旧版本）。
 */
public record PublishTemplateVersionRequest(
        @NotEmpty(message = "请至少配置一个表单字段")
        List<FormFieldView> formFields,

        @NotEmpty(message = "请至少配置一个审批节点")
        List<NodeDefinitionView> nodes) {
}