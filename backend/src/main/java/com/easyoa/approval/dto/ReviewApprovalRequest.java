package com.easyoa.approval.dto;

import jakarta.validation.constraints.Size;

/**
 * 审批操作（同意 / 拒绝 / 退回）。
 *
 * <p>拒绝与退回必须填写意见（服务层校验，写入审批历史与审计）。
 */
public record ReviewApprovalRequest(
        @Size(max = 1000, message = "审批意见过长")
        String comment) {
}