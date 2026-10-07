package com.easyoa.workspace.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.easyoa.user.application.UserService;
import com.easyoa.user.dto.UserProfileResponse;
import com.easyoa.workspace.dto.WorkspaceSummaryResponse;
import com.easyoa.workspace.dto.WorkspaceSummaryResponse.KpiSummary;
import com.easyoa.workspace.dto.WorkspaceSummaryResponse.UserBrief;

/**
 * 工作台聚合服务。
 */
@Service
public class WorkspaceService {

    private final UserService userService;

    public WorkspaceService(UserService userService) {
        this.userService = userService;
    }

    @Transactional(readOnly = true)
    public WorkspaceSummaryResponse summary(Long userId) {
        UserProfileResponse profile = userService.getProfile(userId);

        // 项目 / 任务 / 审批模块尚未交付（Phase 3 起逐步接入），当前不存在任何业务数据，
        // 因此统计值恒为 0 —— 这里不做任何假数据，前端按空状态渲染。
        KpiSummary kpis = new KpiSummary(0, 0, 0, 0);

        return new WorkspaceSummaryResponse(
                new UserBrief(profile.id(), profile.username(), profile.displayName(), profile.systemRole(),
                        profile.avatarUrl(), profile.lastLoginAt()),
                kpis);
    }
}