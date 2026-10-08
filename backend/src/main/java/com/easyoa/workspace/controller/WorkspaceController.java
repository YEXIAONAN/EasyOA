package com.easyoa.workspace.controller;

import java.util.List;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.easyoa.common.response.ApiResponse;
import com.easyoa.common.security.SecurityUser;
import com.easyoa.workspace.application.ActivityService;
import com.easyoa.workspace.application.MemberCollaborationService;
import com.easyoa.workspace.application.WorkspaceService;
import com.easyoa.workspace.dto.ActivityView;
import com.easyoa.workspace.dto.MemberCollaborationResponse;
import com.easyoa.workspace.dto.WorkspaceSummaryResponse;

/**
 * 工作台（首页）接口。
 */
@RestController
@RequestMapping("/api/workspace")
public class WorkspaceController {

    private final WorkspaceService workspaceService;
    private final ActivityService activityService;
    private final MemberCollaborationService memberCollaborationService;

    public WorkspaceController(WorkspaceService workspaceService, ActivityService activityService,
            MemberCollaborationService memberCollaborationService) {
        this.workspaceService = workspaceService;
        this.activityService = activityService;
        this.memberCollaborationService = memberCollaborationService;
    }

    @GetMapping("/summary")
    public ApiResponse<WorkspaceSummaryResponse> summary(@AuthenticationPrincipal SecurityUser principal) {
        return ApiResponse.ok(workspaceService.summary(principal.id()));
    }

    /** Activity Feed（业务动态，含深链；数据范围见 ActivityService）。 */
    @GetMapping("/activity")
    public ApiResponse<List<ActivityView>> activity(@AuthenticationPrincipal SecurityUser principal,
            @RequestParam(required = false, defaultValue = "10") Integer limit) {
        return ApiResponse.ok(activityService.recent(principal.id(), Math.min(Math.max(limit, 1), 30)));
    }

    /**
     * 成员协作概览（团队页面成员档案的「参与项目 / 近期任务」）。
     *
     * <p>数据范围：非管理员只能看到自己同样可见的项目与任务。
     */
    @GetMapping("/members/{userId}/collaboration")
    public ApiResponse<MemberCollaborationResponse> memberCollaboration(@PathVariable Long userId) {
        return ApiResponse.ok(memberCollaborationService.of(userId));
    }
}