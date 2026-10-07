package com.easyoa.project.controller;

import java.util.List;

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
import com.easyoa.project.application.ProjectMemberService;
import com.easyoa.project.application.ProjectQuery;
import com.easyoa.project.application.ProjectService;
import com.easyoa.project.domain.ProjectStatus;
import com.easyoa.project.dto.AddProjectMemberRequest;
import com.easyoa.project.dto.ChangeProjectProgressRequest;
import com.easyoa.project.dto.ChangeProjectStatusRequest;
import com.easyoa.project.dto.CreateProjectRequest;
import com.easyoa.project.dto.ProjectCardResponse;
import com.easyoa.project.dto.ProjectDetailResponse;
import com.easyoa.project.dto.ProjectMemberView;
import com.easyoa.project.dto.SetDeputyOwnerRequest;
import com.easyoa.project.dto.TransferOwnerRequest;
import com.easyoa.project.dto.UpdateProjectRequest;

import jakarta.validation.Valid;

/**
 * 项目接口。
 *
 * <p>数据范围与角色权限全部由 {@code ProjectPermissionService} 在应用层判定：
 * 非项目成员访问一律 404（不泄露项目是否存在），角色不足返回 403。
 */
@RestController
@RequestMapping("/api/projects")
public class ProjectController {

    private final ProjectService projectService;
    private final ProjectMemberService projectMemberService;

    public ProjectController(ProjectService projectService, ProjectMemberService projectMemberService) {
        this.projectService = projectService;
        this.projectMemberService = projectMemberService;
    }

    /** 项目列表（普通用户仅可见自己参与的项目）。 */
    @GetMapping
    public ApiResponse<PageResponse<ProjectCardResponse>> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) ProjectStatus status,
            @RequestParam(required = false, defaultValue = "1") Integer page,
            @RequestParam(required = false, defaultValue = "12") Integer size) {
        return ApiResponse.ok(projectService.list(ProjectQuery.of(keyword, status, page, size)));
    }

    @PostMapping
    public ApiResponse<ProjectDetailResponse> create(@Valid @RequestBody CreateProjectRequest request) {
        return ApiResponse.ok(projectService.create(request));
    }

    /** 项目概览（含成员、当前用户角色与可执行操作）。 */
    @GetMapping("/{id}")
    public ApiResponse<ProjectDetailResponse> detail(@PathVariable Long id) {
        return ApiResponse.ok(projectService.detail(id));
    }

    @PutMapping("/{id}")
    public ApiResponse<ProjectDetailResponse> updateInfo(@PathVariable Long id,
            @Valid @RequestBody UpdateProjectRequest request) {
        return ApiResponse.ok(projectService.updateInfo(id, request));
    }

    @PutMapping("/{id}/progress")
    public ApiResponse<ProjectDetailResponse> changeProgress(@PathVariable Long id,
            @Valid @RequestBody ChangeProjectProgressRequest request) {
        return ApiResponse.ok(projectService.changeProgress(id, request.progress()));
    }

    @PostMapping("/{id}/status")
    public ApiResponse<ProjectDetailResponse> changeStatus(@PathVariable Long id,
            @Valid @RequestBody ChangeProjectStatusRequest request) {
        return ApiResponse.ok(projectService.changeStatus(id, request.status()));
    }

    /** 归档项目（仅 OWNER；归档后项目只读）。 */
    @PostMapping("/{id}/archive")
    public ApiResponse<ProjectDetailResponse> archive(@PathVariable Long id) {
        return ApiResponse.ok(projectService.changeStatus(id, ProjectStatus.ARCHIVED));
    }

    // --- 成员与角色 -----------------------------------------------------------

    @GetMapping("/{id}/members")
    public ApiResponse<List<ProjectMemberView>> members(@PathVariable Long id) {
        return ApiResponse.ok(projectMemberService.members(id));
    }

    @PostMapping("/{id}/members")
    public ApiResponse<List<ProjectMemberView>> addMember(@PathVariable Long id,
            @Valid @RequestBody AddProjectMemberRequest request) {
        return ApiResponse.ok(projectMemberService.addMember(id, request.userId()));
    }

    @DeleteMapping("/{id}/members/{userId}")
    public ApiResponse<List<ProjectMemberView>> removeMember(@PathVariable Long id, @PathVariable Long userId) {
        return ApiResponse.ok(projectMemberService.removeMember(id, userId));
    }

    /** 设置或取消副负责人（仅 OWNER）。 */
    @PutMapping("/{id}/deputy")
    public ApiResponse<ProjectDetailResponse> setDeputy(@PathVariable Long id,
            @RequestBody SetDeputyOwnerRequest request) {
        return ApiResponse.ok(projectMemberService.setDeputy(id, request.userId()));
    }

    /** 转让项目负责人（仅 OWNER）。 */
    @PostMapping("/{id}/transfer-owner")
    public ApiResponse<ProjectDetailResponse> transferOwner(@PathVariable Long id,
            @Valid @RequestBody TransferOwnerRequest request) {
        return ApiResponse.ok(projectMemberService.transferOwner(id, request.userId()));
    }
}