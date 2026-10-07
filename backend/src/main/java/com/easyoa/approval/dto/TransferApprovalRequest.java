package com.easyoa.approval.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 审批转交（仅管理员；完整审计）。
 *
 * <p>转交后原审批人退出当前节点（TRANSFERRED_OUT），新审批人快照标记来源；
 * 目标用户不能是申请人（自我审批禁止）。
 */
public record TransferApprovalRequest(
        @NotNull(message = "请选择原审批人")
        Long fromUserId,

        @NotNull(message = "请选择转交对象")
        Long toUserId,

        @Size(max = 1000, message = "转交说明过长")
        String comment) {
}