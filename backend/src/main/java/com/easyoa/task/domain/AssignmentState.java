package com.easyoa.task.domain;

/**
 * 任务派发状态。
 *
 * <ul>
 *   <li>{@link #ACTIVE} — 已生效（项目负责人派发，或成员把任务派给自己）；</li>
 *   <li>{@link #PENDING_ASSIGNMENT} — 成员把任务派给他人，等待 OWNER / DEPUTY_OWNER 审核，
 *       审核通过前不进入看板、不可流转；</li>
 *   <li>{@link #REJECTED} — 派发被审核驳回，记录保留但不生效（不进入看板）。</li>
 * </ul>
 *
 * <p>注意：被指派成员无需接受任务——审核通过即正式生效。
 */
public enum AssignmentState {

    ACTIVE,
    PENDING_ASSIGNMENT,
    REJECTED
}