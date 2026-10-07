package com.easyoa.approval.dto;

import java.util.Map;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 发起审批（创建草稿；values 为表单字段值，按模板 schema 校验）。
 */
public record SubmitApprovalRequest(
        @NotNull(message = "请选择审批模板")
        Long templateId,

        @NotBlank(message = "请输入申请标题")
        @Size(max = 200, message = "申请标题过长")
        String title,

        @NotNull(message = "请填写表单")
        Map<String, Object> values) {
}