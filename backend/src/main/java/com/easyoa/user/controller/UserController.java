package com.easyoa.user.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.easyoa.common.response.ApiResponse;
import com.easyoa.common.response.PageResponse;
import com.easyoa.user.application.UserAdminService;
import com.easyoa.user.application.UserDirectoryQuery;
import com.easyoa.user.application.UserDirectoryService;
import com.easyoa.user.domain.UserStatus;
import com.easyoa.user.dto.ChangeUserRoleRequest;
import com.easyoa.user.dto.CreateUserRequest;
import com.easyoa.user.dto.MemberCardResponse;
import com.easyoa.user.dto.MemberProfileResponse;
import com.easyoa.user.dto.UpdateMyProfileRequest;
import com.easyoa.user.dto.UpdateUserStatusRequest;

import jakarta.validation.Valid;

/**
 * 成员目录、成员档案与账号管理接口。
 *
 * <p>权限全部由应用层的 {@code UserPermissionService} / {@code UserAdminService} 判定：
 * 即使绕过前端直接调用，未授权操作同样会被拒绝。
 */
@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserDirectoryService userDirectoryService;
    private final UserAdminService userAdminService;

    public UserController(UserDirectoryService userDirectoryService, UserAdminService userAdminService) {
        this.userDirectoryService = userDirectoryService;
        this.userAdminService = userAdminService;
    }

    /** 成员目录（团队页面）。 */
    @GetMapping("/directory")
    public ApiResponse<PageResponse<MemberCardResponse>> directory(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long orgUnitId,
            @RequestParam(required = false) UserStatus status,
            @RequestParam(required = false, defaultValue = "1") Integer page,
            @RequestParam(required = false, defaultValue = "24") Integer size) {
        return ApiResponse.ok(userDirectoryService.directory(UserDirectoryQuery.of(keyword, orgUnitId, status, page, size)));
    }

    /** 成员档案（联系方式按权限过滤）。 */
    @GetMapping("/{id}")
    public ApiResponse<MemberProfileResponse> profile(@PathVariable Long id) {
        return ApiResponse.ok(userDirectoryService.profile(id));
    }

    /** 更新本人档案。 */
    @PatchMapping("/me")
    public ApiResponse<MemberProfileResponse> updateOwnProfile(@Valid @RequestBody UpdateMyProfileRequest request) {
        return ApiResponse.ok(userDirectoryService.updateOwnProfile(request));
    }

    /** 创建成员（ADMIN 只能创建 MEMBER；创建管理员需要 ROOT）。 */
    @PostMapping
    public ApiResponse<MemberProfileResponse> create(@Valid @RequestBody CreateUserRequest request) {
        return ApiResponse.ok(userAdminService.create(request));
    }

    /** 启用 / 禁用账号（禁用会立即撤销其全部会话）。 */
    @PostMapping("/{id}/status")
    public ApiResponse<MemberProfileResponse> changeStatus(@PathVariable Long id,
            @Valid @RequestBody UpdateUserStatusRequest request) {
        return ApiResponse.ok(userAdminService.changeStatus(id, request.status()));
    }

    /** 变更系统角色（仅 ROOT；变更后强制重新登录）。 */
    @PostMapping("/{id}/role")
    public ApiResponse<MemberProfileResponse> changeRole(@PathVariable Long id,
            @Valid @RequestBody ChangeUserRoleRequest request) {
        return ApiResponse.ok(userAdminService.changeRole(id, request));
    }
}