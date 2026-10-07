package com.easyoa.task.dto;

import java.time.Instant;
import java.util.List;

import com.easyoa.task.domain.TaskDependency;
import com.easyoa.task.domain.TaskPriority;

/**
 * 任务详情（右侧 Drawer）。
 *
 * <p>包含负责人体系、进度与时间、子任务、依赖与当前用户可执行操作；
 * 评论 / 附件（Phase 5）不在本视图中。
 */
public record TaskDetailResponse(
        Long id,
        Long projectId,
        String projectName,
        Long parentId,
        String parentTitle,
        String title,
        String description,
        TaskPriority priority,
        TaskStatusView status,
        TaskUserBrief primaryAssignee,
        TaskUserBrief deputyAssignee,
        List<TaskUserBrief> collaborators,
        int progress,
        String progressMode,
        Instant plannedStartAt,
        Instant plannedEndAt,
        Instant actualStartAt,
        Instant completedAt,
        String assignmentState,
        boolean blocked,
        int blockerCount,
        List<TaskDependencyView> dependencies,
        List<TaskCardResponse> subtasks,
        Instant createdAt,
        Instant updatedAt,
        Permissions permissions) {

    /** 当前用户在本任务上可执行的操作（前端只做体验控制，后端独立鉴权）。 */
    public record Permissions(
            boolean canManage,
            boolean canFullControl,
            boolean canEditProgress,
            boolean canManageDependencies,
            boolean canManageCollaborators,
            boolean canReviewAssignment,
            /** 是否可查看任务下所有评论的编辑历史（作者本人、项目负责人或系统管理员）。 */
            boolean canViewCommentHistory) {
    }

    /** 前置依赖视图（用于展示与「忽略依赖并开始」弹窗）。 */
    public record TaskDependencyView(
            Long id,
            Long dependsOnTaskId,
            String title,
            String statusName,
            String statusType,
            boolean finished,
            TaskUserBrief primaryAssignee) {

        public static TaskDependencyView from(TaskDependency dependency) {
            var target = dependency.getDependsOnTask();
            return new TaskDependencyView(
                    dependency.getId(),
                    target.getId(),
                    target.getTitle(),
                    target.getStatus().getName(),
                    target.getStatus().getSystemType().name(),
                    target.getStatus().getSystemType().isFinished(),
                    TaskUserBrief.from(target.getPrimaryAssignee()));
        }
    }
}