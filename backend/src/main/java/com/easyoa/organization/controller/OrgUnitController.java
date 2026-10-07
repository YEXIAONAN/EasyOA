package com.easyoa.organization.controller;

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
import com.easyoa.organization.application.OrgMembershipService;
import com.easyoa.organization.application.OrgUnitService;
import com.easyoa.organization.dto.AddOrgMemberRequest;
import com.easyoa.organization.dto.CreateOrgUnitRequest;
import com.easyoa.organization.dto.MoveOrgUnitRequest;
import com.easyoa.organization.dto.OrgMemberResponse;
import com.easyoa.organization.dto.OrgUnitDetailResponse;
import com.easyoa.organization.dto.OrgUnitTreeNode;
import com.easyoa.organization.dto.UpdateOrgUnitRequest;
import com.easyoa.organization.dto.UserOrgMembershipView;

import jakarta.validation.Valid;

/**
 * 组织架构与组织成员接口。
 *
 * <p>权限说明：接口层不做权限判断，全部由 {@code OrganizationPermissionService}
 * 在应用层统一执行（ROOT / ADMIN 管理体系结构；单元负责人可管理其成员）。
 */
@RestController
@RequestMapping("/api/org-units")
public class OrgUnitController {

    private final OrgUnitService orgUnitService;
    private final OrgMembershipService orgMembershipService;

    public OrgUnitController(OrgUnitService orgUnitService, OrgMembershipService orgMembershipService) {
        this.orgUnitService = orgUnitService;
        this.orgMembershipService = orgMembershipService;
    }

    /** 组织树（默认隐藏已归档单元）。 */
    @GetMapping
    public ApiResponse<List<OrgUnitTreeNode>> tree(
            @RequestParam(required = false, defaultValue = "false") boolean includeArchived) {
        return ApiResponse.ok(orgUnitService.tree(includeArchived));
    }

    @GetMapping("/{id}")
    public ApiResponse<OrgUnitDetailResponse> detail(@PathVariable Long id) {
        return ApiResponse.ok(orgUnitService.detail(id));
    }

    @PostMapping
    public ApiResponse<OrgUnitDetailResponse> create(@Valid @RequestBody CreateOrgUnitRequest request) {
        return ApiResponse.ok(orgUnitService.create(request));
    }

    @PutMapping("/{id}")
    public ApiResponse<OrgUnitDetailResponse> update(@PathVariable Long id,
            @Valid @RequestBody UpdateOrgUnitRequest request) {
        return ApiResponse.ok(orgUnitService.update(id, request));
    }

    /** 移动组织单元（禁止移动到自身或下级，避免成环）。 */
    @PostMapping("/{id}/move")
    public ApiResponse<OrgUnitDetailResponse> move(@PathVariable Long id,
            @RequestBody MoveOrgUnitRequest request) {
        return ApiResponse.ok(orgUnitService.move(id, request.newParentId()));
    }

    @PostMapping("/{id}/archive")
    public ApiResponse<OrgUnitDetailResponse> archive(@PathVariable Long id) {
        return ApiResponse.ok(orgUnitService.archive(id));
    }

    @PostMapping("/{id}/restore")
    public ApiResponse<OrgUnitDetailResponse> restore(@PathVariable Long id) {
        return ApiResponse.ok(orgUnitService.restore(id));
    }

    // --- 组织成员 -------------------------------------------------------------

    @GetMapping("/{id}/members")
    public ApiResponse<List<OrgMemberResponse>> members(@PathVariable Long id) {
        return ApiResponse.ok(orgMembershipService.membersOf(id));
    }

    @PostMapping("/{id}/members")
    public ApiResponse<List<OrgMemberResponse>> addMember(@PathVariable Long id,
            @Valid @RequestBody AddOrgMemberRequest request) {
        return ApiResponse.ok(orgMembershipService.addMember(id, request.userId()));
    }

    @DeleteMapping("/{id}/members/{userId}")
    public ApiResponse<List<OrgMemberResponse>> removeMember(@PathVariable Long id, @PathVariable Long userId) {
        return ApiResponse.ok(orgMembershipService.removeMember(id, userId));
    }

    /** 设置主部门（该成员必须已在此单元中）。 */
    @PutMapping("/{id}/members/{userId}/primary")
    public ApiResponse<List<UserOrgMembershipView>> setPrimary(@PathVariable Long id, @PathVariable Long userId) {
        return ApiResponse.ok(orgMembershipService.setPrimary(userId, id));
    }
}