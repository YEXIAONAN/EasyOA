package com.easyoa.search.application;

import java.util.List;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.easyoa.approval.repository.ApprovalInstanceRepository;
import com.easyoa.common.security.SecurityUser;
import com.easyoa.project.repository.ProjectRepository;
import com.easyoa.search.dto.SearchHit;
import com.easyoa.search.dto.SearchResponse;
import com.easyoa.task.application.TaskPermissionService;
import com.easyoa.task.repository.TaskRepository;
import com.easyoa.user.domain.User;
import com.easyoa.user.domain.UserStatus;
import com.easyoa.user.repository.UserRepository;

/**
 * 全局搜索：项目 / 任务 / 成员 / 审批，按类别分组返回。
 *
 * <p>数据范围与各模块一致：管理员可见全部，普通用户仅能看到自己参与的
 * 项目 / 任务与自己的审批；成员检索对所有登录用户开放（团队目录本身可见）。
 */
@Service
public class SearchService {

    private static final int GROUP_LIMIT = 5;

    private final ProjectRepository projectRepository;
    private final TaskRepository taskRepository;
    private final UserRepository userRepository;
    private final ApprovalInstanceRepository approvalInstanceRepository;
    private final TaskPermissionService permissionService;

    public SearchService(ProjectRepository projectRepository, TaskRepository taskRepository,
            UserRepository userRepository, ApprovalInstanceRepository approvalInstanceRepository,
            TaskPermissionService permissionService) {
        this.projectRepository = projectRepository;
        this.taskRepository = taskRepository;
        this.userRepository = userRepository;
        this.approvalInstanceRepository = approvalInstanceRepository;
        this.permissionService = permissionService;
    }

    @Transactional(readOnly = true)
    public SearchResponse search(String rawQuery) {
        SecurityUser actor = permissionService.requireAuthenticated();
        String query = rawQuery == null ? "" : rawQuery.trim();
        if (query.isEmpty()) {
            return new SearchResponse(query, List.of(), List.of(), List.of(), List.of());
        }
        boolean scopeAll = actor.systemRole().isAdminLike();
        String keyword = "%" + query.toLowerCase() + "%";
        PageRequest limit = PageRequest.of(0, GROUP_LIMIT);

        List<SearchHit> projects = projectRepository.search(scopeAll, actor.id(), null, keyword, limit).getContent()
                .stream()
                .map(project -> new SearchHit(project.getId(), project.getName(), project.getDescription(),
                        "/projects/" + project.getId()))
                .toList();

        List<SearchHit> tasks = taskRepository.searchForUser(scopeAll, actor.id(), keyword, limit).stream()
                .map(task -> new SearchHit(task.getId(), task.getTitle(),
                        task.getProject().getName() + " · " + task.getStatus().getName(),
                        "/projects/" + task.getProject().getId() + "/board?task=" + task.getId()))
                .toList();

        List<SearchHit> users = userRepository.searchByKeyword(keyword, UserStatus.ACTIVE, limit).stream()
                .map(user -> new SearchHit(user.getId(), user.getDisplayName(), userSubtitle(user), "/team"))
                .toList();

        List<SearchHit> approvals = approvalInstanceRepository.searchForUser(scopeAll, actor.id(), keyword, limit)
                .stream()
                .map(instance -> new SearchHit(instance.getId(), instance.getTitle(),
                        instance.getTemplate().getName() + " · " + instance.getStatus().name(),
                        "/approvals/" + instance.getId()))
                .toList();

        return new SearchResponse(query, projects, tasks, users, approvals);
    }

    private String userSubtitle(User user) {
        String jobTitle = user.getJobTitle() == null || user.getJobTitle().isBlank() ? "" : user.getJobTitle() + " · ";
        return jobTitle + user.getUsername();
    }
}