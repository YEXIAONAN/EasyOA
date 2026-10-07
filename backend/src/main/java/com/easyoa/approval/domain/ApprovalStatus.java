package com.easyoa.approval.domain;

/**
 * 审批实例状态。
 *
 * <pre>
 * DRAFT ──提交──► PENDING ──全部节点通过──► APPROVED
 *                    │  ├──任一节点拒绝────► REJECTED
 *                    │  ├──审批人退回──────► RETURNED ──修改后重新提交──► PENDING（从第一个节点重新审批）
 *                    │  └──申请人撤回──────► CANCELLED
 * </pre>
 */
public enum ApprovalStatus {

    DRAFT,
    PENDING,
    APPROVED,
    REJECTED,
    RETURNED,
    CANCELLED;

    /** 终态：通过 / 拒绝 / 撤回。 */
    public boolean isFinished() {
        return this == APPROVED || this == REJECTED || this == CANCELLED;
    }

    /** 申请人可编辑表单的状态（进入 PENDING 后不得修改，只能撤回或等待退回）。 */
    public boolean isFormEditable() {
        return this == DRAFT || this == RETURNED;
    }
}