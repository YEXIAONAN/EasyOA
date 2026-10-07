package com.easyoa.task.application;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.easyoa.common.exception.ApiException;
import com.easyoa.common.exception.ErrorCode;
import com.easyoa.project.application.ProjectPermissionService;
import com.easyoa.project.domain.Project;
import com.easyoa.project.repository.ProjectRepository;
import com.easyoa.task.domain.TaskStatus;
import com.easyoa.task.domain.TaskStatusType;
import com.easyoa.task.dto.CreateTaskStatusRequest;
import com.easyoa.task.dto.TaskStatusView;
import com.easyoa.task.repository.TaskStatusRepository;

/**
 * 项目任务状态：系统默认模板初始化、自定义状态与查询。
 *
 * <p>默认模板：待处理(TODO) → 进行中(ACTIVE) → 待审核(REVIEW) → 已完成(DONE)。
 * 项目可以追加自定义状态（如「开发中」→ ACTIVE），但必须映射系统统一类型；
 * 统计与流程判断只依赖系统类型。
 */
@Service
public class TaskStatusService {

    /** 默认模板（顺序即看板列顺序）。 */
    private record DefaultStatus(String name, TaskStatusType systemType) {
    }

    private static final List<DefaultStatus> DEFAULT_TEMPLATE = List.of(
            new DefaultStatus("待处理", TaskStatusType.TODO),
            new DefaultStatus("进行中", TaskStatusType.ACTIVE),
            new DefaultStatus("待审核", TaskStatusType.REVIEW),
            new DefaultStatus("已完成", TaskStatusType.DONE));

    private final TaskStatusRepository taskStatusRepository;
    private final ProjectRepository projectRepository;
    private final ProjectPermissionService projectPermissionService;
    private final TaskPermissionService permissionService;

    public TaskStatusService(TaskStatusRepository taskStatusRepository, ProjectRepository projectRepository,
            ProjectPermissionService projectPermissionService, TaskPermissionService permissionService) {
        this.taskStatusRepository = taskStatusRepository;
        this.projectRepository = projectRepository;
        this.projectPermissionService = projectPermissionService;
        this.permissionService = permissionService;
    }

    /** 为新项目初始化默认状态模板（幂等）。 */
    @Transactional
    public void ensureDefaultStatuses(Project project) {
        if (taskStatusRepository.existsByProjectId(project.getId())) {
            return;
        }
        int sortOrder = 0;
        for (DefaultStatus status : DEFAULT_TEMPLATE) {
            taskStatusRepository.save(new TaskStatus(project, status.name(), status.systemType(), sortOrder++));
        }
    }

    /**
     * 项目状态列表（按列顺序）。
     *
     * <p>正常情况下由项目创建 / 迁移初始化；这里保留惰性兜底，避免历史数据缺失导致看板不可用。
     */
    @Transactional
    public List<TaskStatus> statuses(Long projectId) {
        List<TaskStatus> statuses = taskStatusRepository.findByProjectIdOrderBySortOrderAscIdAsc(projectId);
        if (!statuses.isEmpty()) {
            return statuses;
        }
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> ApiException.notFound("项目不存在或无权访问"));
        ensureDefaultStatuses(project);
        return taskStatusRepository.findByProjectIdOrderBySortOrderAscIdAsc(projectId);
    }

    @Transactional(readOnly = true)
    public List<TaskStatusView> list(Long projectId) {
        projectPermissionService.requireViewable(projectId);
        return statuses(projectId).stream().map(TaskStatusView::from).toList();
    }

    /** 新增项目自定义状态（仅项目负责人 / 副负责人；追加到列尾）。 */
    @Transactional
    public List<TaskStatusView> create(Long projectId, CreateTaskStatusRequest request) {
        Project project = projectPermissionService.requireViewable(projectId);
        projectPermissionService.assertNotArchived(project);
        permissionService.requireProjectManagement(projectId);

        String name = request.name().trim();
        boolean duplicated = statuses(projectId).stream()
                .anyMatch(status -> status.getName().equalsIgnoreCase(name));
        if (duplicated) {
            throw ApiException.conflict("已存在同名任务状态：" + name);
        }
        int sortOrder = taskStatusRepository.maxSortOrder(projectId) + 1;
        taskStatusRepository.save(new TaskStatus(project, name, request.systemType(), sortOrder));
        return list(projectId);
    }

    /** 校验状态属于该项目（跨项目状态一律拒绝）。 */
    @Transactional(readOnly = true)
    public TaskStatus requireStatus(Long projectId, Long statusId) {
        return taskStatusRepository.findByIdAndProjectId(statusId, projectId)
                .orElseThrow(() -> new ApiException(ErrorCode.UNPROCESSABLE, "任务状态不属于该项目"));
    }

    /** 项目默认「待处理」状态（新任务 / 子任务的初始状态）。 */
    @Transactional
    public TaskStatus defaultTodoStatus(Long projectId) {
        return statuses(projectId).stream()
                .filter(status -> status.getSystemType() == TaskStatusType.TODO)
                .findFirst()
                .orElseThrow(() -> ApiException.conflict("项目任务状态未初始化，请先打开项目看板"));
    }
}