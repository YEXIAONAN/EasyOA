package com.easyoa.task.dto;

import jakarta.validation.constraints.Size;

/**
 * 派发审核（驳回时建议填写原因，写入审计）。
 */
public record ReviewAssignmentRequest(
        @Size(max = 500, message = "原因过长")
        String reason) {
}