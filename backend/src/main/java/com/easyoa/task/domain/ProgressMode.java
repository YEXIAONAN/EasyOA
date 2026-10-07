package com.easyoa.task.domain;

/**
 * 任务进度模式。
 *
 * <ul>
 *   <li>{@link #MANUAL} — 主负责人 / 副负责人手工更新 0~100%；</li>
 *   <li>{@link #AUTO} — 按一级子任务完成比例自动计算；没有子任务时回退为手工模式。</li>
 * </ul>
 */
public enum ProgressMode {

    MANUAL,
    AUTO
}