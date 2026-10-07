package com.easyoa.user.application;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.easyoa.common.exception.ApiException;
import com.easyoa.common.requestid.RequestContext;
import com.easyoa.common.response.PageResponse;
import com.easyoa.common.security.SecurityUser;
import com.easyoa.organization.application.OrgMembershipService;
import com.easyoa.organization.application.OrgUnitService;
import com.easyoa.organization.dto.OrgUnitBrief;
import com.easyoa.organization.dto.UserOrgMembershipView;
import com.easyoa.user.domain.User;
import com.easyoa.user.dto.MemberCardResponse;
import com.easyoa.user.dto.MemberProfileResponse;
import com.easyoa.user.repository.UserRepository;

/**
 * 成员目录与成员档案（团队页面数据来源）。
 */
@Service
public class UserDirectoryService {

    private final UserRepository userRepository;
    private final UserService userService;
    private final OrgMembershipService orgMembershipService;
    private final OrgUnitService orgUnitService;
    private final UserPermissionService userPermissionService;

    public UserDirectoryService(UserRepository userRepository, UserService userService,
            OrgMembershipService orgMembershipService, OrgUnitService orgUnitService,
            UserPermissionService userPermissionService) {
        this.userRepository = userRepository;
        this.userService = userService;
        this.orgMembershipService = orgMembershipService;
        this.orgUnitService = orgUnitService;
        this.userPermissionService = userPermissionService;
    }

    @Transactional(readOnly = true)
    public PageResponse<MemberCardResponse> directory(UserDirectoryQuery query) {
        Pageable pageable = PageRequest.of(Math.max(query.page(), 1) - 1, Math.max(query.size(), 1));

        boolean filterByOrg = query.orgUnitId() != null;
        Set<Long> scopedUserIds = Set.of();
        if (filterByOrg) {
            // 组织筛选自动包含下级单元
            List<Long> unitIds = orgUnitService.subtreeIds(query.orgUnitId());
            scopedUserIds = orgMembershipService.userIdsInUnits(unitIds);
            if (scopedUserIds.isEmpty()) {
                return PageResponse.of(List.of(), query.page(), query.size(), 0);
            }
        }

        Page<User> users = userRepository.searchDirectory(query.keyword(), query.status(), filterByOrg, scopedUserIds,
                pageable);
        Map<Long, List<UserOrgMembershipView>> memberships = orgMembershipService
                .membershipsOfUsers(users.getContent().stream().map(User::getId).toList());

        List<MemberCardResponse> cards = new ArrayList<>();
        for (User user : users.getContent()) {
            List<UserOrgMembershipView> userMemberships = memberships.getOrDefault(user.getId(), List.of());
            OrgUnitBrief primary = userMemberships.stream()
                    .filter(UserOrgMembershipView::primary)
                    .map(UserOrgMembershipView::orgUnit)
                    .findFirst()
                    .orElse(null);
            cards.add(new MemberCardResponse(
                    user.getId(),
                    user.getUsername(),
                    user.getDisplayName(),
                    user.getAvatarUrl(),
                    user.getJobTitle(),
                    user.getSystemRole().name(),
                    user.getStatus().name(),
                    primary,
                    userMemberships.size(),
                    user.getCreatedAt()));
        }
        return PageResponse.of(cards, query.page(), query.size(), users.getTotalElements());
    }

    /** 成员档案：基础信息对所有登录用户可见，联系方式按权限过滤。 */
    @Transactional(readOnly = true)
    public MemberProfileResponse profile(Long targetUserId) {
        SecurityUser viewer = RequestContext.currentUser();
        User user = userService.getById(targetUserId);
        List<UserOrgMembershipView> memberships = orgMembershipService.membershipsOf(targetUserId);
        OrgUnitBrief primary = memberships.stream()
                .filter(UserOrgMembershipView::primary)
                .map(UserOrgMembershipView::orgUnit)
                .findFirst()
                .orElse(null);

        boolean contactVisible = userPermissionService.canViewContact(viewer, targetUserId);
        MemberProfileResponse.ContactInfo contact = contactVisible
                ? new MemberProfileResponse.ContactInfo(user.getEmail(), user.getPhone())
                : null;

        return new MemberProfileResponse(
                user.getId(),
                user.getUsername(),
                user.getDisplayName(),
                user.getAvatarUrl(),
                user.getJobTitle(),
                user.getSystemRole().name(),
                user.getStatus().name(),
                user.getBio(),
                primary,
                memberships,
                contactVisible,
                contact,
                user.getLastLoginAt(),
                user.getCreatedAt());
    }

    /** 更新本人档案（显示名称 / 邮箱 / 手机 / 简介 / 头像）。 */
    @Transactional
    public MemberProfileResponse updateOwnProfile(com.easyoa.user.dto.UpdateMyProfileRequest request) {
        SecurityUser actor = RequestContext.currentUser();
        if (actor == null) {
            throw new ApiException(com.easyoa.common.exception.ErrorCode.UNAUTHENTICATED);
        }
        User user = userService.getById(actor.id());
        user.setDisplayName(request.displayName().trim());
        user.setEmail(normalize(request.email()));
        user.setPhone(normalize(request.phone()));
        user.setBio(normalize(request.bio()));
        user.setAvatarUrl(normalize(request.avatarUrl()));
        userRepository.save(user);
        return profile(actor.id());
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    /** 供创建成员时校验：用户是否存在于给定组织（避免重复添加）。 */
    @Transactional(readOnly = true)
    public Set<Long> existingUserIds(List<Long> candidateIds) {
        Set<Long> found = new HashSet<>();
        userRepository.findAllById(candidateIds).forEach(user -> found.add(user.getId()));
        return found;
    }
}