package com.easyoa.approval.domain;

/**
 * 审批历史动作（approval_actions，只追加）。
 */
public enum ApprovalActionType {

    SUBMIT,
    APPROVE,
    REJECT,
    RETURN,
    WITHDRAW,
    TRANSFER,
    UPDATE_FORM
}