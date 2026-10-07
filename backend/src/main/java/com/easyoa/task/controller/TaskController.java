package com.easyoa.task.controller;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.easyoa.common.response.ApiResponse;
import com.easyoa.common.response.PageResponse;
import com.easyoa.common.security.SecurityUser;
import com.easyoa.task.application.MyTaskQuery;
import com.easyoa.task.application.TaskPermissionService;
import com.easyoa.task.application.TaskService;
import com.easyoa.task.dto.AddTaskDependencyRequest;
import com.easyoa.task.dto.ChangeTaskProgressRequest;
import com.easyoa.task.dto.ChangeTaskStatusRequest;
import com.easyoa.task.dto.CreateSubtaskRequest;
import com.easyoa.task.dto.ReviewAssignmentRequest;
import com.easyoa.task.dto.TaskCardResponse;
import com.easyoa.task.dto.TaskDetailResponse;
import com.easyoa.task.dto.UpdateTaskAssigneesRequest;
import com.easyoa.task.dto.UpdateTaskCollaboratorsRequest;
import com.easyoa.task.dto.UpdateTaskRequest;

import jakarta.validation.Valid;

/**
 * 任务接口（任务维度）。
 *
 * <p>权限模型见 {@code TaskPermissionService}：主负责人 / 副负责人 / 项目负责人各有边界，
 * 所有写操作在后端复查权限，前端按钮仅做体验控制。
 */
@RestController
@RequestMapping("/api/tasks")
public class TaskController {

    private final TaskService taskService;
    private final TaskPermissionService permissionService;

    public TaskController(TaskService taskService, TaskPermissionService permissionService) {
        this.taskService = taskService;
        this.permissionService = permissionService;
    }

    /** 我的任务（主负责人 / 副负责人 / 协作成员，跨项目）。 */
    @GetMapping("/my")
    public ApiResponse<PageResponse<TaskCardResponse>> myTasks(
            @RequestParam(required = false) String filter,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false, defaultValue = "1") Integer page,
            @RequestParam(required = false, defaultValue = "15") Integer size) {
        SecurityUser actor = permissionService.requireAuthenticated();
        return ApiResponse.ok(taskService.myTasks(actor.id(), MyTaskQuery.of(filter, keyword, page, size)));
    }

    /** 任务详情（右侧 Drawer 数据源）。 */
    @GetMapping("/{id}")
    public ApiResponse<TaskDetailResponse> detail(@PathVariable Long id) {
        return ApiResponse.ok(taskService.detail(id));
    }

    /** 编辑基础信息（标题 / 描述 / 优先级 / 计划时间 / 进度模式）。 */
    @PutMapping("/{id}")
    public ApiResponse<TaskDetailResponse> updateInfo(@PathVariable Long id,
            @Valid @RequestBody UpdateTaskRequest request) {
        return ApiResponse.ok(taskService.updateInfo(id, request));
    }

    /** 状态流转（看板拖拽 / 侧栏切换）；依赖阻塞时前端弹「忽略依赖并开始」并填写原因。 */
    @PostMapping("/{id}/status")
    public ApiResponse<TaskDetailResponse> changeStatus(@PathVariable Long id,
            @Valid @RequestBody ChangeTaskStatusRequest request) {
        return ApiResponse.ok(taskService.changeStatus(id, request));
    }

    /** 手工更新进度（AUTO 模式且存在子任务时拒绝）。 */
    @PutMapping("/{id}/progress")
    public ApiResponse<TaskDetailResponse> changeProgress(@PathVariable Long id,
            @Valid @RequestBody ChangeTaskProgressRequest request) {
        return ApiResponse.ok(taskService.changeProgress(id, request.progress()));
    }

    /** 调整主 / 副负责人（仅主负责人或项目负责人）。 */
    @PutMapping("/{id}/assignees")
    public ApiResponse<TaskDetailResponse> changeAssignees(@PathVariable Long id,
            @Valid @RequestBody UpdateTaskAssigneesRequest request) {
        return ApiResponse.ok(taskService.changeAssignees(id, request));
    }

    /** 设置协作成员（整体替换）。 */
    @PutMapping("/{id}/collaborators")
    public ApiResponse<TaskDetailResponse> changeCollaborators(@PathVariable Long id,
            @Valid @RequestBody UpdateTaskCollaboratorsRequest request) {
        return ApiResponse.ok(taskService.changeCollaborators(id, request));
    }

    /** 添加一级子任务（返回父任务详情，便于侧栏直接刷新）。 */
    @PostMapping("/{id}/subtasks")
    public ApiResponse<TaskDetailResponse> createSubtask(@PathVariable Long id,
            @Valid @RequestBody CreateSubtaskRequest request) {
        return ApiResponse.ok(taskService.createSubtask(id, request));
    }

    /** 添加前置依赖（同项目、无循环）。 */
    @PostMapping("/{id}/dependencies")
    public ApiResponse<TaskDetailResponse> addDependency(@PathVariable Long id,
            @Valid @RequestBody AddTaskDependencyRequest request) {
        return ApiResponse.ok(taskService.addDependency(id, request.dependsOnTaskId()));
    }

    /** 移除前置依赖。 */
    @DeleteMapping("/{id}/dependencies/{dependsOnTaskId}")
    public ApiResponse<TaskDetailResponse> removeDependency(@PathVariable Long id,
            @PathVariable Long dependsOnTaskId) {
        return ApiResponse.ok(taskService.removeDependency(id, dependsOnTaskId));
    }

    /** 派发审核通过（仅项目负责人 / 副负责人）。 */
    @PostMapping("/{id}/assignment/approve")
    public ApiResponse<TaskDetailResponse> approveAssignment(@PathVariable Long id) {
        return ApiResponse.ok(taskService.approveAssignment(id));
    }

    /** 派发审核驳回（原因写入审计）。 */
    @PostMapping("/{id}/assignment/reject")
    public ApiResponse<TaskDetailResponse> rejectAssignment(@PathVariable Long id,
            @RequestBody ReviewAssignmentRequest request) {
        return ApiResponse.ok(taskService.rejectAssignment(id, request == null ? null : request.reason()));
    }
}