package com.easyoa.task.domain;

/**
 * 任务状态的系统统一类型。
 *
 * <p>项目可以自定义任务状态名称（如「开发中」「Code Review」「已上线」），
 * 但每个自定义状态必须映射到这里的系统类型；所有统计与流程判断只依赖系统类型，
 * 绝不依赖自定义状态名称。
 *
 * <pre>
 * TODO ──► ACTIVE ──► REVIEW ──► DONE ──► CLOSED
 * </pre>
 *
 * <p>状态流规则（v0.1.0）：允许向前推进（任意跳步）；允许回退到 TODO / ACTIVE
 * （打回或重新打开）；CLOSED 为终态，不可再流转。
 */
public enum TaskStatusType {

    /** 待处理：尚未开始。 */
    TODO,

    /** 进行中：第一次进入该类型时自动记录 actual_start_at。 */
    ACTIVE,

    /** 待审核。 */
    REVIEW,

    /** 已完成：第一次进入该类型时自动记录 completed_at。 */
    DONE,

    /** 已关闭（取消 / 归档）：终态。 */
    CLOSED;

    /** 系统类型顺序，用于状态流与统计。 */
    public int rank() {
        return ordinal();
    }

    /** 终态：不会再流转（DONE 允许重新打开，因此只有 CLOSED 是严格终态）。 */
    public boolean isTerminal() {
        return this == CLOSED;
    }

    /** 是否已结束（视为完成）：DONE / CLOSED 都用于解除依赖阻塞与「未完成」统计。 */
    public boolean isFinished() {
        return this == DONE || this == CLOSED;
    }

    /** 是否已经营（进入 ACTIVE 及之后的状态）：依赖阻塞只拦截这一类流转。 */
    public boolean isStarted() {
        return rank() >= ACTIVE.rank();
    }
}