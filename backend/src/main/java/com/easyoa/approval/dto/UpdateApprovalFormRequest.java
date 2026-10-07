package com.easyoa.approval.dto;

import java.util.Map;

import jakarta.validation.constraints.NotNull;

/**
 * 修改申请表单（仅 DRAFT / RETURNED 状态可由申请人修改）。
 */
public record UpdateApprovalFormRequest(
        @NotNull(message = "请填写表单")
        Map<String, Object> values) {
}