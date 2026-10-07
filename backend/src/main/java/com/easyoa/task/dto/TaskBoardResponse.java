package com.easyoa.task.dto;

import java.util.List;

/**
 * 项目看板视图：状态列 + 任务卡片（顶层任务，含派发未生效的待审核任务）。
 *
 * <p>看板按项目维度一次返回全量（项目内任务天然有界），保证拖拽与列分组的一致性；
 * 「任务」Tab 的列表使用分页接口。
 */
public record TaskBoardResponse(
        Long projectId,
        List<TaskStatusView> statuses,
        List<TaskCardResponse> tasks,
        List<TaskCardResponse> pendingAssignments) {
}