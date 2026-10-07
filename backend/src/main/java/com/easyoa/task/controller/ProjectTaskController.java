package com.easyoa.task.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.easyoa.common.response.ApiResponse;
import com.easyoa.common.response.PageResponse;
import com.easyoa.task.application.TaskQuery;
import com.easyoa.task.application.TaskService;
import com.easyoa.task.application.TaskStatusService;
import com.easyoa.task.domain.TaskPriority;
import com.easyoa.task.dto.CreateTaskRequest;
import com.easyoa.task.dto.CreateTaskStatusRequest;
import com.easyoa.task.dto.TaskBoardResponse;
import com.easyoa.task.dto.TaskCardResponse;
import com.easyoa.task.dto.TaskDetailResponse;
import com.easyoa.task.dto.TaskStatusView;

import jakarta.validation.Valid;

/**
 * 项目内任务接口。
 *
 * <p>数据范围与权限由 {@code TaskPermissionService} / {@code ProjectPermissionService} 判定：
 * 非项目成员一律 404（不泄露任务是否存在），角色不足返回 403。
 */
@RestController
@RequestMapping("/api/projects/{projectId}")
public class ProjectTaskController {

    private final TaskService taskService;
    private final TaskStatusService taskStatusService;

    public ProjectTaskController(TaskService taskService, TaskStatusService taskStatusService) {
        this.taskService = taskService;
        this.taskStatusService = taskStatusService;
    }

    /**
     * 项目看板：状态列 + 任务卡片（顶层任务全量，项目内任务天然有界）；
     * 待派发审核任务仅项目负责人可见。「任务」Tab 的列表使用分页接口。
     */
    @GetMapping("/tasks/board")
    public ApiResponse<TaskBoardResponse> board(@PathVariable Long projectId) {
        return ApiResponse.ok(taskService.board(projectId));
    }

    /** 项目任务列表（分页，仅顶层任务）。 */
    @GetMapping("/tasks")
    public ApiResponse<PageResponse<TaskCardResponse>> list(@PathVariable Long projectId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long statusId,
            @RequestParam(required = false) TaskPriority priority,
            @RequestParam(required = false, defaultValue = "1") Integer page,
            @RequestParam(required = false, defaultValue = "15") Integer size) {
        return ApiResponse.ok(taskService.listTasks(projectId, TaskQuery.of(keyword, statusId, priority, page, size)));
    }

    /** 创建任务（含派发审核判定）。 */
    @PostMapping("/tasks")
    public ApiResponse<TaskDetailResponse> create(@PathVariable Long projectId,
            @Valid @RequestBody CreateTaskRequest request) {
        return ApiResponse.ok(taskService.create(projectId, request));
    }

    /** 项目任务状态列表（默认模板 + 自定义状态）。 */
    @GetMapping("/task-statuses")
    public ApiResponse<List<TaskStatusView>> statuses(@PathVariable Long projectId) {
        return ApiResponse.ok(taskStatusService.list(projectId));
    }

    /** 新增自定义任务状态（仅项目负责人 / 副负责人；必须映射系统类型）。 */
    @PostMapping("/task-statuses")
    public ApiResponse<List<TaskStatusView>> createStatus(@PathVariable Long projectId,
            @Valid @RequestBody CreateTaskStatusRequest request) {
        return ApiResponse.ok(taskStatusService.create(projectId, request));
    }
}