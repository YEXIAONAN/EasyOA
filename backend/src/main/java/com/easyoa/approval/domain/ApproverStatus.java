package com.easyoa.approval.domain;

/**
 * 审批人快照状态。
 *
 * <p>{@link #RETURNED} 表示该审批人执行了退回操作；实例重新提交后重置为 {@link #PENDING}。
 * {@link #TRANSFERRED_OUT} 表示管理员转交后该审批人退出当前节点。
 */
public enum ApproverStatus {

    PENDING,
    APPROVED,
    REJECTED,
    RETURNED,
    TRANSFERRED_OUT
}