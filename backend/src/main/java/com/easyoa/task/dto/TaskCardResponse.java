package com.easyoa.task.dto;

import java.time.Instant;

import com.easyoa.task.domain.Task;
import com.easyoa.task.domain.TaskPriority;
import com.easyoa.task.domain.TaskStatusType;

/**
 * 任务卡片视图：看板、任务列表、我的任务与子任务列表共用。
 *
 * <p>看板卡片只展示：标题、状态、优先级、主负责人、截止时间、进度、Blocked 标志
 * （对应前端渲染子集，卡片数据保持克制）。
 */
public record TaskCardResponse(
        Long id,
        Long projectId,
        String projectName,
        Long parentId,
        String title,
        TaskPriority priority,
        TaskStatusView status,
        TaskUserBrief primaryAssignee,
        TaskUserBrief deputyAssignee,
        int progress,
        String progressMode,
        Instant plannedStartAt,
        Instant plannedEndAt,
        Instant completedAt,
        boolean blocked,
        int blockerCount,
        boolean overdue,
        boolean canManage,
        boolean canFullControl,
        Instant updatedAt) {

    public static TaskCardResponse from(Task task, Instant now, boolean blocked, int blockerCount,
            boolean canManage, boolean canFullControl) {
        TaskStatusType systemType = task.getStatus().getSystemType();
        boolean overdue = task.getPlannedEndAt() != null && !systemType.isFinished()
                && task.getPlannedEndAt().isBefore(now);
        return new TaskCardResponse(
                task.getId(),
                task.getProject().getId(),
                task.getProject().getName(),
                task.getParent() == null ? null : task.getParent().getId(),
                task.getTitle(),
                task.getPriority(),
                TaskStatusView.from(task.getStatus()),
                TaskUserBrief.from(task.getPrimaryAssignee()),
                TaskUserBrief.from(task.getDeputyAssignee()),
                task.getProgress(),
                task.getProgressMode().name(),
                task.getPlannedStartAt(),
                task.getPlannedEndAt(),
                task.getCompletedAt(),
                blocked,
                blockerCount,
                overdue,
                canManage,
                canFullControl,
                task.getUpdatedAt());
    }
}