package com.easyoa.approval.dto;

import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

/**
 * 创建审批模板（同时发布 v1 版本）。
 */
public record CreateTemplateRequest(
        @NotBlank(message = "请输入模板名称")
        @Size(max = 120, message = "模板名称过长")
        String name,

        @Size(max = 1000, message = "模板说明过长")
        String description,

        @NotEmpty(message = "请至少配置一个表单字段")
        List<FormFieldView> formFields,

        @NotEmpty(message = "请至少配置一个审批节点")
        List<NodeDefinitionView> nodes) {
}